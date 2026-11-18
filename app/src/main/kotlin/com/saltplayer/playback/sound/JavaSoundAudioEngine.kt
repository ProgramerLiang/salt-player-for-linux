// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Corripo
package com.saltplayer.playback.sound

import com.saltplayer.core.Log
import com.saltplayer.data.prefs.EqualizerSettings
import com.saltplayer.playback.AudioEngine
import com.saltplayer.playback.EngineCapabilities
import com.saltplayer.playback.EngineListener
import com.saltplayer.playback.EngineMath
import com.saltplayer.playback.EngineUnavailableException
import com.saltplayer.playback.PlayRequest
import java.io.File
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.FloatControl
import javax.sound.sampled.SourceDataLine
import kotlin.concurrent.thread

/**
 * Last-resort backend built on `javax.sound.sampled`.
 *
 * It exists so the application still makes sound on machines without GStreamer or mpv. It only
 * decodes formats that a Java Sound SPI is registered for (WAV/AIFF/AU out of the box, MP3/OGG
 * only with extra SPI jars). It has no gapless playback, no rate control and no equalizer —
 * [capabilities] says so, and the settings screen shows the downgrade explicitly.
 */
class JavaSoundAudioEngine : AudioEngine {

    override val id: String = ID
    override val displayName: String = "Java Sound (fallback)"

    override val capabilities: EngineCapabilities = EngineCapabilities(
        gapless = false,
        preciseSeek = true,
        playbackRate = false,
        equalizer = false,
        replayGain = true,
        deviceSelection = true,
        wideFormatSupport = false,
    )

    @Volatile private var listener: EngineListener? = null
    @Volatile private var volume = 0.8f
    @Volatile private var playing = false
    @Volatile private var durationMs = 0L
    @Volatile private var positionMs = 0L
    @Volatile private var running = false
    @Volatile private var mixerName: String = ""

    private var worker: Thread? = null
    private var line: SourceDataLine? = null
    private var currentFile: String? = null
    private var startOffsetMs = 0L
    private val lock = Any()

    override fun setListener(listener: EngineListener?) {
        this.listener = listener
    }

    @Synchronized
    override fun prepare() {
        val mixers = runCatching { AudioSystem.getMixerInfo() }.getOrNull()
        if (mixers.isNullOrEmpty()) {
            throw EngineUnavailableException("Java Sound reports no audio mixer; is ALSA/PulseAudio running?")
        }
    }

    override fun loadAndPlay(request: PlayRequest) {
        if (!File(request.path).isFile) {
            listener?.onError("File not found", request.path)
            return
        }
        stopInternal()
        currentFile = request.path
        startOffsetMs = request.startPositionMs.coerceAtLeast(0)
        durationMs = probeDurationMs(request.path)
        running = true
        worker = thread(name = "javasound-player", isDaemon = true) { playbackLoop(request.path, request.startPositionMs) }
    }

    private fun probeDurationMs(path: String): Long = runCatching {
        AudioSystem.getAudioFileFormat(File(path)).frameLength.let { frames ->
            val format = AudioSystem.getAudioFileFormat(File(path)).format
            if (format.frameRate > 0) (frames / format.frameRate * 1000).toLong() else 0L
        }
    }.getOrDefault(0L)

    private fun playbackLoop(path: String, startPositionMs: Long) {
        var stream: AudioInputStream? = null
        try {
            stream = AudioSystem.getAudioInputStream(File(path))
            val baseFormat = stream.format
            val pcmFormat = if (baseFormat.encoding == AudioFormat.Encoding.PCM_SIGNED) {
                baseFormat
            } else {
                AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    baseFormat.sampleRate,
                    16,
                    baseFormat.channels,
                    baseFormat.channels * 2,
                    baseFormat.sampleRate,
                    false,
                )
            }
            if (pcmFormat != baseFormat) stream = AudioSystem.getAudioInputStream(pcmFormat, stream)

            val info = DataLine.Info(SourceDataLine::class.java, pcmFormat)
            val output = AudioSystem.getLine(info) as SourceDataLine
            output.open(pcmFormat, 64 * 1024)
            line = output
            applyVolume(output)
            output.start()

            if (startPositionMs > 0) {
                val skipBytes = (startPositionMs / 1000.0 * pcmFormat.frameRate * pcmFormat.frameSize).toLong()
                stream.skip(maxOf(skipBytes, 0) - (skipBytes % pcmFormat.frameSize))
            }
            positionMs = startPositionMs
            listener?.onDurationChanged(durationMs)
            playing = true
            listener?.onPlayingChanged(true)

            val buffer = ByteArray(16 * 1024)
            var written = 0L
            while (running) {
                val read = stream.read(buffer, 0, buffer.size)
                if (read <= 0) break
                var offset = 0
                while (offset < read && running) {
                    val count = output.write(buffer, offset, read - offset)
                    offset += count
                    written += count
                }
                positionMs = startPositionMs + (written / (pcmFormat.frameRate * pcmFormat.frameSize) * 1000).toLong()
            }
            output.drain()
            output.stop()
            output.close()
            if (running) {
                playing = false
                listener?.onPlayingChanged(false)
                listener?.onTrackEnded()
            }
        } catch (error: Throwable) {
            Log.warn("JavaSound", "playback failed for $path", error)
            listener?.onError(
                "Java Sound cannot decode this file (${error.message ?: error::class.java.simpleName})",
                "Install GStreamer for full format support.",
            )
        } finally {
            runCatching { stream?.close() }
            line = null
        }
    }

    private fun applyVolume(output: SourceDataLine) {
        runCatching {
            if (output.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                val control = output.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
                val gain = EngineMath.combineVolume(volume, null).coerceIn(0.0, 1.0)
                val db = if (gain <= 0.0) control.minimum else (20 * Math.log10(gain)).toFloat()
                control.value = db.coerceIn(control.minimum, control.maximum)
            }
        }
    }

    override fun pause() {
        line?.stop()
        playing = false
        listener?.onPlayingChanged(false)
    }

    override fun resume() {
        line?.start()
        playing = true
        listener?.onPlayingChanged(true)
    }

    override fun stop() = stopInternal()

    private fun stopInternal() {
        running = false
        worker?.interrupt()
        worker = null
        runCatching { line?.stop() }
        runCatching { line?.close() }
        line = null
        playing = false
    }

    override fun seekTo(positionMs: Long) {
        val path = currentFile ?: return
        val wasPlaying = playing
        stopInternal()
        if (!wasPlaying) {
            this.positionMs = positionMs
            return
        }
        running = true
        startOffsetMs = positionMs
        worker = thread(name = "javasound-seek", isDaemon = true) { playbackLoop(path, positionMs) }
    }

    override fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0f, 1f)
        line?.let { applyVolume(it) }
    }

    override fun setPlaybackRate(rate: Double) = Unit

    override fun applyEqualizer(settings: EqualizerSettings) = Unit

    override fun setOutputDevice(deviceId: String) {
        mixerName = deviceId
        Log.info("JavaSound", "output device preference recorded: '${deviceId.ifBlank { "system default" }}'")
    }

    override fun setNextTrack(path: String?) = Unit

    override fun positionMs(): Long = positionMs.coerceAtLeast(0)

    override fun durationMs(): Long = durationMs.coerceAtLeast(0)

    override fun isPlaying(): Boolean = playing

    override fun close() = stopInternal()

    companion object {
        const val ID = "javasound"
    }
}
