// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Corripo
package com.saltplayer.playback

import com.saltplayer.data.prefs.EqualizerSettings

/** Feature flags a backend may or may not provide; surfaced in the settings UI. */
data class EngineCapabilities(
    val gapless: Boolean = false,
    val preciseSeek: Boolean = false,
    val playbackRate: Boolean = false,
    val equalizer: Boolean = false,
    val replayGain: Boolean = false,
    val deviceSelection: Boolean = false,
    val wideFormatSupport: Boolean = false,
) {
    fun supportedFeatures(): List<String> = buildList {
        if (gapless) add("gapless")
        if (preciseSeek) add("precise-seek")
        if (playbackRate) add("playback-rate")
        if (equalizer) add("equalizer")
        if (replayGain) add("replaygain")
        if (deviceSelection) add("device-selection")
        if (wideFormatSupport) add("wide-format-support")
    }
}

/** A backend that can (or cannot) be used on this machine, with the reason. */
data class EngineDescriptor(
    val id: String,
    val displayName: String,
    val available: Boolean,
    val capabilities: EngineCapabilities,
    /** Human readable detail, e.g. the detected GStreamer version or the missing library. */
    val detail: String,
    /** Copy-pasteable command that installs the missing runtime, if any. */
    val installHint: String? = null,
)

class EngineUnavailableException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class PlayRequest(
    val path: String,
    val startPositionMs: Long = 0L,
    /** ReplayGain correction in dB, already resolved by the controller. */
    val replayGainDb: Double? = null,
)

/** Callbacks fired on backend threads; the controller marshals them onto the UI dispatcher. */
interface EngineListener {
    fun onPlayingChanged(playing: Boolean) = Unit
    /** The backend gaplessly moved to the preloaded track; [path] is the track now playing. */
    fun onTrackTransition(path: String) = Unit
    /** The current track ended and nothing was preloaded. */
    fun onTrackEnded() = Unit
    fun onDurationChanged(durationMs: Long) = Unit
    fun onError(message: String, detail: String? = null) = Unit
}

/**
 * Pluggable audio backend.
 *
 * Implementations must be thread safe: control methods may be called from the UI thread while
 * the backend reports events from its own thread.
 */
interface AudioEngine : AutoCloseable {
    val id: String
    val displayName: String
    val capabilities: EngineCapabilities

    /** Verifies native dependencies; throws [EngineUnavailableException] when unusable. */
    @Throws(EngineUnavailableException::class)
    fun prepare()

    fun setListener(listener: EngineListener?)

    fun loadAndPlay(request: PlayRequest)
    fun pause()
    fun resume()
    fun stop()

    fun seekTo(positionMs: Long)
    /** Linear volume in 0..1, already including ReplayGain. */
    fun setVolume(volume: Float)
    fun setPlaybackRate(rate: Double)
    fun applyEqualizer(settings: EqualizerSettings)
    fun setOutputDevice(deviceId: String)

    /** Track to start immediately after the current one without an audible gap. */
    fun setNextTrack(path: String?)

    fun positionMs(): Long
    fun durationMs(): Long
    fun isPlaying(): Boolean

    override fun close()
}

/** Shared helpers for engines. */
internal object EngineMath {
    /** dB → linear gain factor. */
    fun dbToGain(db: Double): Double = Math.pow(10.0, db / 20.0)

    fun combineVolume(volume: Float, replayGainDb: Double?): Double {
        val base = volume.coerceIn(0f, 1f).toDouble()
        val gain = replayGainDb?.let { dbToGain(it) } ?: 1.0
        return (base * gain).coerceIn(0.0, 8.0)
    }
}
