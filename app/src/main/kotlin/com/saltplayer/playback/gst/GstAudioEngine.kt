// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Corripo
package com.saltplayer.playback.gst

import com.saltplayer.core.Log
import com.saltplayer.data.prefs.EqualizerSettings
import com.saltplayer.playback.AudioEngine
import com.saltplayer.playback.EngineCapabilities
import com.saltplayer.playback.EngineListener
import com.saltplayer.playback.EngineMath
import com.saltplayer.playback.EngineUnavailableException
import com.saltplayer.playback.PlayRequest
import org.freedesktop.gstreamer.Bus
import org.freedesktop.gstreamer.Element
import org.freedesktop.gstreamer.ElementFactory
import org.freedesktop.gstreamer.Format
import org.freedesktop.gstreamer.Gst
import org.freedesktop.gstreamer.GstObject
import org.freedesktop.gstreamer.State
import org.freedesktop.gstreamer.elements.PlayBin
import org.freedesktop.gstreamer.elements.PlayFlags
import org.freedesktop.gstreamer.event.SeekFlags
import org.freedesktop.gstreamer.event.SeekType
import org.freedesktop.gstreamer.message.Message
import org.freedesktop.gstreamer.message.MessageType
import java.io.File
import java.util.EnumSet
import java.util.concurrent.TimeUnit

/**
 * Primary backend: GStreamer through `gst1-java-core`.
 *
 * `playbin` gives format coverage (whatever plugins the distribution ships), ALSA/PulseAudio
 * output selection, exact seeking with rate changes, an `equalizer-10bands` audio-filter and
 * real gapless playback through the `about-to-finish` signal.
 */
class GstAudioEngine : AudioEngine {

    override val id: String = ID
    override val displayName: String = "GStreamer"

    override val capabilities: EngineCapabilities = EngineCapabilities(
        gapless = true,
        preciseSeek = true,
        playbackRate = true,
        equalizer = true,
        replayGain = true,
        deviceSelection = true,
        wideFormatSupport = true,
    )

    @Volatile
    private var listener: EngineListener? = null

    private var playbin: PlayBin? = null
    private var equalizer: Element? = null
    private var audioSink: Element? = null

    @Volatile private var prepared = false
    @Volatile private var playing = false
    @Volatile private var volume = 0.8f
    @Volatile private var replayGainDb: Double? = null
    @Volatile private var preampGain = 1.0
    @Volatile private var rate = 1.0
    @Volatile private var deviceId: String = ""
    @Volatile private var pendingNextPath: String? = null
    @Volatile private var transitionPending = false
    @Volatile private var lastDurationMs = 0L
    private val lock = Any()

    override fun setListener(listener: EngineListener?) {
        this.listener = listener
    }

    @Synchronized
    override fun prepare() {
        if (prepared) return
        try {
            if (!Gst.isInitialized()) {
                Gst.init("SaltPlayer", *emptyArray())
            }
            val bin = PlayBin("salt-player")
            bin.setFlags(EnumSet.of(PlayFlags.AUDIO))
            bin.setVolume(EngineMath.combineVolume(volume, replayGainDb))

            equalizer = runCatching {
                ElementFactory.make("equalizer-10bands", "salt-eq")
            }.getOrNull().also { eq ->
                if (eq == null) {
                    Log.warn("Gst", "equalizer-10bands unavailable (gst-plugins-good not installed?)")
                } else {
                    bin.set("audio-filter", eq)
                }
            }

            applyAudioSink(bin, deviceId)
            connectBus(bin)
            bin.connect(object : PlayBin.ABOUT_TO_FINISH {
                override fun aboutToFinish(element: PlayBin) {
                    val next = pendingNextPath ?: return
                    if (!File(next).isFile) return
                    // Handing the URI to playbin here is what makes the transition gapless.
                    runCatching {
                        element.setURI(File(next).toURI())
                        transitionPending = true
                    }.onFailure { Log.warn("Gst", "gapless hand-off failed", it) }
                }
            })
            playbin = bin
            prepared = true
            Log.info("Gst", "GStreamer ${Gst.getVersionString()} ready")
        } catch (error: Throwable) {
            prepared = false
            throw EngineUnavailableException(
                "GStreamer could not be initialised: ${error.message ?: error::class.java.simpleName}",
                error,
            )
        }
    }

    private fun applyAudioSink(bin: PlayBin, device: String) {
        runCatching {
            audioSink?.stop()
        }
        val sink = runCatching {
            when {
                // "fakesink"/"nullsink" are real GStreamer sinks used for headless runs and tests.
                device == "fakesink" || device == "nullsink" -> ElementFactory.make(device, "salt-sink")
                    .also { if (device == "fakesink") runCatching { it.set("sync", true) } }
                else -> ElementFactory.make("autoaudiosink", "salt-sink")
            }
        }.getOrNull()
        if (sink == null) {
            Log.warn("Gst", "could not create audio sink for device '$device', using playbin default")
            audioSink = null
        } else {
            bin.setAudioSink(sink)
            audioSink = sink
        }
    }

    private fun connectBus(bin: PlayBin) {
        val bus = bin.bus
        bus.connect(object : Bus.EOS {
            override fun endOfStream(source: GstObject) {
                if (transitionPending) return
                playing = false
                listener?.onTrackEnded()
            }
        })
        bus.connect(object : Bus.ERROR {
            override fun errorMessage(source: GstObject, code: Int, message: String) {
                playing = false
                Log.warn("Gst", "playback error ($code): $message")
                listener?.onError(message, null)
            }
        })
        bus.connect(object : Bus.STATE_CHANGED {
            override fun stateChanged(source: GstObject, old: State, current: State, pending: State) {
                val nowPlaying = current == State.PLAYING
                if (nowPlaying != playing) {
                    playing = nowPlaying
                    listener?.onPlayingChanged(nowPlaying)
                }
            }
        })
        bus.connect(object : Bus.DURATION_CHANGED {
            override fun durationChanged(source: GstObject) {
                val duration = queryDurationMs()
                if (duration > 0) {
                    lastDurationMs = duration
                    listener?.onDurationChanged(duration)
                }
            }
        })
        bus.connect(object : Bus.MESSAGE {
            override fun busMessage(bus: Bus, message: Message) {
                if (message.type != MessageType.STREAM_START) return
                if (transitionPending) {
                    transitionPending = false
                    val next = pendingNextPath
                    pendingNextPath = null
                    lastDurationMs = 0
                    if (next != null) listener?.onTrackTransition(next)
                }
            }
        })
    }

    override fun loadAndPlay(request: PlayRequest) {
        val bin = playbin ?: throw EngineUnavailableException("GStreamer backend is not prepared")
        if (!File(request.path).isFile) {
            listener?.onError("File not found", request.path)
            return
        }
        synchronized(lock) {
            transitionPending = false
            replayGainDb = request.replayGainDb
            lastDurationMs = 0
            bin.stop()
            bin.setURI(File(request.path).toURI())
            bin.setVolume(EngineMath.combineVolume(volume, replayGainDb) * preampGain)
            // Preroll in PAUSED so the seek lands before the first sample is rendered.
            bin.pause()
            runCatching { bin.getState(3, TimeUnit.SECONDS) }
            val duration = queryDurationMs()
            if (duration > 0) {
                lastDurationMs = duration
                listener?.onDurationChanged(duration)
            }
            if (request.startPositionMs > 0) {
                runCatching {
                    bin.seek(rate, Format.TIME, SEEK_FLAGS, SeekType.SET, request.startPositionMs * 1_000_000, SeekType.NONE, -1)
                }.onFailure { Log.warn("Gst", "initial seek failed", it) }
            } else if (rate != 1.0) {
                runCatching {
                    bin.seek(rate, Format.TIME, SEEK_FLAGS, SeekType.SET, 0, SeekType.NONE, -1)
                }.onFailure { Log.warn("Gst", "rate restore failed", it) }
            }
            bin.play()
        }
    }

    override fun pause() {
        playbin?.pause()
        playing = false
        listener?.onPlayingChanged(false)
    }

    override fun resume() {
        playbin?.play()
    }

    override fun stop() {
        playbin?.stop()
        playing = false
        transitionPending = false
        listener?.onPlayingChanged(false)
    }

    override fun seekTo(positionMs: Long) {
        val bin = playbin ?: return
        runCatching {
            bin.seek(rate, Format.TIME, SEEK_FLAGS, SeekType.SET, positionMs * 1_000_000, SeekType.NONE, -1)
        }.onFailure { Log.warn("Gst", "seek to $positionMs ms failed", it) }
    }

    override fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0f, 1f)
        playbin?.setVolume(EngineMath.combineVolume(this.volume, replayGainDb) * preampGain)
    }

    override fun setPlaybackRate(rate: Double) {
        this.rate = rate.coerceIn(0.25, 4.0)
        val position = positionMs()
        val bin = playbin ?: return
        runCatching {
            bin.seek(this.rate, Format.TIME, SEEK_FLAGS, SeekType.SET, position * 1_000_000, SeekType.NONE, -1)
        }.onFailure { Log.warn("Gst", "rate change failed", it) }
    }

    override fun applyEqualizer(settings: EqualizerSettings) {
        val eq = equalizer ?: return
        val bands = if (settings.enabled) settings.bands else List(EqualizerSettings.BAND_FREQUENCIES.size) { 0f }
        for (index in 0 until EqualizerSettings.BAND_FREQUENCIES.size) {
            runCatching { eq.set("band$index", bands.getOrElse(index) { 0f }.toDouble()) }
        }
        preampGain = if (settings.enabled) EngineMath.dbToGain(settings.preampDb.toDouble()) else 1.0
        playbin?.setVolume(EngineMath.combineVolume(volume, replayGainDb) * preampGain)
    }

    override fun setOutputDevice(deviceId: String) {
        this.deviceId = deviceId
        val bin = playbin ?: return
        val wasPlaying = playing
        runCatching {
            bin.stop()
            applyAudioSink(bin, deviceId)
            if (wasPlaying) bin.play()
        }.onFailure { Log.warn("Gst", "switching output device failed", it) }
    }

    override fun setNextTrack(path: String?) {
        pendingNextPath = path
    }

    override fun positionMs(): Long = runCatching {
        playbin?.queryPosition(TimeUnit.MILLISECONDS)?.coerceAtLeast(0) ?: 0
    }.getOrDefault(0)

    override fun durationMs(): Long {
        if (lastDurationMs > 0) return lastDurationMs
        val queried = queryDurationMs()
        if (queried > 0) lastDurationMs = queried
        return queried.coerceAtLeast(0)
    }

    private fun queryDurationMs(): Long = runCatching {
        playbin?.queryDuration(TimeUnit.MILLISECONDS)?.coerceAtLeast(0) ?: 0
    }.getOrDefault(0)

    override fun isPlaying(): Boolean = playing

    override fun close() {
        runCatching { playbin?.stop() }
        runCatching { playbin?.dispose() }
        playbin = null
        equalizer = null
        audioSink = null
        prepared = false
    }

    companion object {
        const val ID = "gstreamer"

        private val SEEK_FLAGS: EnumSet<SeekFlags> =
            EnumSet.of(SeekFlags.FLUSH, SeekFlags.ACCURATE, SeekFlags.KEY_UNIT)
    }
}
