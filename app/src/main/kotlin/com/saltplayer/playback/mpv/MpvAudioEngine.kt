// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Corripo
package com.saltplayer.playback.mpv

import com.saltplayer.core.Log
import com.saltplayer.data.prefs.EqualizerSettings
import com.saltplayer.playback.AudioEngine
import com.saltplayer.playback.EngineCapabilities
import com.saltplayer.playback.EngineListener
import com.saltplayer.playback.EngineMath
import com.saltplayer.playback.EngineUnavailableException
import com.saltplayer.playback.PlayRequest
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import java.io.File
import kotlin.concurrent.thread

/**
 * Secondary backend: libmpv driven in-process through JNA.
 *
 * mpv is only used when GStreamer cannot be initialised. It offers excellent format coverage,
 * hardware-independent audio output, `--gapless-audio` playlist transitions and an `af`
 * equalizer chain. All property access is guarded so a crashing mpv instance cannot take the
 * UI thread down.
 */
class MpvAudioEngine : AudioEngine {

    override val id: String = ID
    override val displayName: String = "libmpv"

    override val capabilities: EngineCapabilities = EngineCapabilities(
        gapless = true,
        preciseSeek = true,
        playbackRate = true,
        equalizer = true,
        replayGain = true,
        deviceSelection = true,
        wideFormatSupport = true,
    )

    private var lib: MpvBindings? = null
    private var handle: Pointer? = null
    private var events: Thread? = null

    @Volatile private var listener: EngineListener? = null
    @Volatile private var playing = false
    @Volatile private var volume = 0.8f
    @Volatile private var replayGainDb: Double? = null
    @Volatile private var rate = 1.0
    @Volatile private var durationMs = 0L
    @Volatile private var preloadedPath: String? = null
    @Volatile private var currentPath: String? = null
    @Volatile private var running = false
    @Volatile private var endHandled = false

    private val playlistEntries = mutableListOf<String>()

    @Synchronized
    override fun prepare() {
        if (handle != null) return
        val library = loadLibrary() ?: throw EngineUnavailableException(
            "libmpv could not be loaded. Install it with 'sudo pacman -S mpv' " +
                "(Arch) / 'sudo apt install libmpv2' (Debian)."
        )
        val ctx = library.mpv_create() ?: throw EngineUnavailableException(
            "mpv_create() returned NULL (libmpv loaded; see docs for the locale/JNA cause)"
        )
        library.mpv_set_option_string(ctx, "terminal", "no")
        library.mpv_set_option_string(ctx, "really-quiet", "yes")
        library.mpv_set_option_string(ctx, "no-video", "yes")
        library.mpv_set_option_string(ctx, "audio-display", "no")
        library.mpv_set_option_string(ctx, "gapless-audio", "yes")
        library.mpv_set_option_string(ctx, "keep-open", "no")
        library.mpv_set_option_string(ctx, "idle", "yes")
        library.mpv_set_option_string(ctx, "replaygain", "no")
        val initResult = library.mpv_initialize(ctx)
        if (initResult < 0) {
            library.mpv_terminate_destroy(ctx)
            throw EngineUnavailableException("mpv_initialize() failed: ${library.mpv_error_string(initResult)}")
        }
        library.mpv_observe_property(ctx, 1, "pause", FORMAT_FLAG)
        library.mpv_observe_property(ctx, 2, "duration", FORMAT_DOUBLE)
        lib = library
        handle = ctx
        running = true
        events = thread(name = "mpv-events", isDaemon = true) { eventLoop(ctx) }
        Log.info("Mpv", "libmpv ready")
    }

    private fun jnaError(): String = com.sun.jna.Native.getLastError().toString()

    private fun loadLibrary(): MpvBindings? {
        val attempted = mutableListOf<String>()
        for (name in listOf("mpv", "libmpv.so.2", "libmpv.so.1", "libmpv.2.dylib")) {
            val loaded = runCatching { Native.load(name, MpvBindings::class.java) }
                .onFailure { attempted += "$name: ${it.message}" }
                .getOrNull()
            if (loaded != null) {
                Log.info("Mpv", "loaded native library '$name'")
                return loaded
            }
        }
        if (attempted.isNotEmpty()) Log.warn("Mpv", "libmpv load attempts: ${attempted.joinToString("; ")}")
        return null
    }

    override fun setListener(listener: EngineListener?) {
        this.listener = listener
    }

    override fun loadAndPlay(request: PlayRequest) {
        val library = lib ?: throw EngineUnavailableException("libmpv backend is not prepared")
        val ctx = handle ?: throw EngineUnavailableException("libmpv backend is not prepared")
        if (!File(request.path).isFile) {
            listener?.onError("File not found", request.path)
            return
        }
        synchronized(playlistEntries) {
            playlistEntries.clear()
            playlistEntries.add(request.path)
        }
        preloadedPath = null
        currentPath = request.path
        durationMs = 0
        endHandled = false
        replayGainDb = request.replayGainDb
        library.mpv_command_string(ctx, "loadfile ${quote(request.path)} replace")
        if (request.startPositionMs > 0) {
            library.mpv_command_string(ctx, "seek ${request.startPositionMs / 1000.0} absolute+exact")
        }
        library.mpv_set_property_string(ctx, "pause", "no")
        applyVolume()
        library.mpv_set_property_string(ctx, "speed", rate.toString())
    }

    override fun pause() {
        lib?.let { if (handle != null) it.mpv_set_property_string(handle!!, "pause", "yes") }
    }

    override fun resume() {
        lib?.let { if (handle != null) it.mpv_set_property_string(handle!!, "pause", "no") }
    }

    override fun stop() {
        lib?.let { if (handle != null) it.mpv_command_string(handle!!, "stop") }
        playing = false
        listener?.onPlayingChanged(false)
    }

    override fun seekTo(positionMs: Long) {
        lib?.let { if (handle != null) it.mpv_command_string(handle!!, "seek ${positionMs / 1000.0} absolute+exact") }
    }

    override fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0f, 1f)
        applyVolume()
    }

    private fun applyVolume() {
        val library = lib ?: return
        val ctx = handle ?: return
        val percent = (EngineMath.combineVolume(volume, replayGainDb) * 100.0).coerceIn(0.0, 130.0)
        library.mpv_set_property_string(ctx, "volume", "%.2f".format(percent))
    }

    override fun setPlaybackRate(rate: Double) {
        this.rate = rate.coerceIn(0.25, 4.0)
        lib?.let { if (handle != null) it.mpv_set_property_string(handle!!, "speed", this.rate.toString()) }
    }

    override fun applyEqualizer(settings: EqualizerSettings) {
        val library = lib ?: return
        val ctx = handle ?: return
        val filter = if (!settings.enabled) {
            ""
        } else {
            EqualizerSettings.BAND_FREQUENCIES.mapIndexed { index, frequency ->
                val gain = settings.bands.getOrElse(index) { 0f }
                "equalizer=f=%d:width_type=o:width=1:g=%.2f".format(frequency, gain)
            }.joinToString(",")
        }
        library.mpv_command_string(ctx, "af set ${quote(filter)}")
    }

    override fun setOutputDevice(deviceId: String) {
        val library = lib ?: return
        val ctx = handle ?: return
        runCatching {
            library.mpv_set_property_string(ctx, "audio-device", if (deviceId.isBlank()) "auto" else deviceId)
        }.onFailure { Log.warn("Mpv", "audio-device switch failed", it) }
    }

    override fun setNextTrack(path: String?) {
        val library = lib ?: return
        val ctx = handle ?: return
        preloadedPath = path
        if (path == null) return
        synchronized(playlistEntries) { playlistEntries.add(path) }
        library.mpv_command_string(ctx, "loadfile ${quote(path)} append")
    }

    override fun positionMs(): Long {
        val library = lib ?: return 0
        val ctx = handle ?: return 0
        val buffer = Memory(8)
        val result = runCatching { library.mpv_get_property(ctx, "time-pos", FORMAT_DOUBLE, buffer) }.getOrNull() ?: return 0
        if (result < 0) return 0
        return (buffer.getDouble(0) * 1000).toLong().coerceAtLeast(0)
    }

    override fun durationMs(): Long = durationMs.coerceAtLeast(0)

    override fun isPlaying(): Boolean = playing

    override fun close() {
        running = false
        events?.interrupt()
        val library = lib
        val ctx = handle
        handle = null
        lib = null
        if (library != null && ctx != null) {
            runCatching { library.mpv_terminate_destroy(ctx) }
        }
        events = null
    }

    private fun eventLoop(ctx: Pointer) {
        val library = lib ?: return
        while (running) {
            val event = runCatching { library.mpv_wait_event(ctx, 0.1) }.getOrNull() ?: break
            if (event == Pointer.NULL) continue
            when (event.getInt(0)) {
                EVENT_NONE -> continue
                EVENT_SHUTDOWN -> break
                EVENT_END_FILE -> handleEndFile(library, event.getPointer(OFFSET_DATA))
                EVENT_PROPERTY_CHANGE -> handlePropertyChange(event.getPointer(OFFSET_DATA))
            }
        }
    }

    private fun handleEndFile(library: MpvBindings, data: Pointer?) {
        val reason = data?.getInt(0) ?: -1
        when (reason) {
            END_REASON_EOF -> {
                if (endHandled) return
                endHandled = true
                val next = preloadedPath
                if (next != null) {
                    preloadedPath = null
                    currentPath = next
                    synchronized(playlistEntries) {
                        if (playlistEntries.isNotEmpty()) playlistEntries.removeAt(0)
                    }
                    // Drop the finished entry so mpv's playlist cannot grow without bound.
                    library.mpv_command_string(handle ?: return, "playlist-remove 0")
                    durationMs = 0
                    endHandled = false
                    listener?.onTrackTransition(next)
                } else {
                    playing = false
                    listener?.onPlayingChanged(false)
                    listener?.onTrackEnded()
                }
            }

            END_REASON_ERROR -> {
                playing = false
                listener?.onError("mpv could not play this file", null)
            }

            END_REASON_STOP -> {
                playing = false
                listener?.onPlayingChanged(false)
            }
        }
    }

    private fun handlePropertyChange(data: Pointer?) {
        if (data == null) return
        val name = runCatching { data.getPointer(0)?.getString(0) }.getOrNull() ?: return
        val format = data.getInt(8)
        val value = data.getPointer(16) ?: return
        when (name) {
            "pause" -> {
                if (format != FORMAT_FLAG) return
                val paused = value.getInt(0) != 0
                val nowPlaying = !paused
                if (nowPlaying != playing) {
                    playing = nowPlaying
                    listener?.onPlayingChanged(nowPlaying)
                }
            }

            "duration" -> {
                if (format != FORMAT_DOUBLE) return
                val seconds = value.getDouble(0)
                if (seconds > 0) {
                    durationMs = (seconds * 1000).toLong()
                    listener?.onDurationChanged(durationMs)
                }
            }
        }
    }

    private fun quote(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    companion object {
        const val ID = "libmpv"

        private const val FORMAT_FLAG = 3
        private const val FORMAT_DOUBLE = 5

        private const val EVENT_NONE = 0
        private const val EVENT_SHUTDOWN = 1
        private const val EVENT_END_FILE = 7
        private const val EVENT_PROPERTY_CHANGE = 22

        private const val END_REASON_EOF = 0
        private const val END_REASON_STOP = 2
        private const val END_REASON_ERROR = 4

        /** mpv_event.data offset in the 64-bit mpv_event struct. */
        private const val OFFSET_DATA = 16L
    }
}
