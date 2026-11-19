// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Corripo
package com.saltplayer.data.prefs

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class ThemeMode { System, Light, Dark }

enum class AppLanguage { System, Chinese, English }

/** Which audio backend the user pinned; [Auto] probes GStreamer → libmpv → Java Sound. */
enum class BackendPreference { Auto, GStreamer, LibMpv, JavaSound }

enum class CloseBehavior { Quit, MinimizeToTray }

/**
 * How the window asks the compositor to place it.
 *
 * Tiling compositors such as niri decide the surface size themselves; on those setups a
 * windowed instance can end up with a surface larger than the sizes the toolkit reports
 * (black bands / clipped corners). Fullscreen makes the surface match the screen exactly.
 */
enum class WindowMode { Fullscreen, Maximized, Windowed }

enum class ReplayGainMode { Off, Track, Album }

@Serializable
enum class SongSortKey { Title, Artist, Album, Duration, Year, AddedAt }

/**
 * 10-band equalizer state. Band 0 is the lowest frequency.
 *
 * Frequencies follow GStreamer's `equalizer-10bands`: 30, 60, 120, 240, 480, 960, 1920,
 * 3840, 7680, 15360 Hz.
 */
@Serializable
data class EqualizerSettings(
    val enabled: Boolean = false,
    val preset: String = "Flat",
    /** Per-band gain in dB, clamped to [-12, +12]. */
    val bands: List<Float> = List(BAND_FREQUENCIES.size) { 0f },
    /** Pre-amplification in dB, clamped to [-12, +12]. */
    val preampDb: Float = 0f,
) {
    fun clamped(): EqualizerSettings = copy(
        bands = bands.let { current ->
            List(BAND_FREQUENCIES.size) { index -> (current.getOrNull(index) ?: 0f).coerceIn(-12f, 12f) }
        },
        preampDb = preampDb.coerceIn(-12f, 12f),
    )

    companion object {
        val BAND_FREQUENCIES = listOf(30, 60, 120, 240, 480, 960, 1920, 3840, 7680, 15360)

        val PRESETS: Map<String, List<Float>> = linkedMapOf(
            "Flat" to List(10) { 0f },
            "Pop" to listOf(-1.5f, -1f, 0f, 2f, 4f, 4f, 2f, 0f, -1f, -1.5f),
            "Rock" to listOf(4.5f, 3.5f, 2f, 0f, -1f, -0.5f, 1.5f, 3f, 4f, 4.5f),
            "Jazz" to listOf(3f, 2f, 1f, 2f, -1.5f, -1.5f, 0f, 1f, 2f, 3f),
            "Classical" to listOf(4f, 3f, 2f, 0f, -1f, -1f, 0f, 2f, 3f, 4f),
            "Vocal" to listOf(-2f, -1f, 0f, 2f, 4f, 4f, 3f, 1f, 0f, -1f),
            "Bass Boost" to listOf(6f, 5f, 4f, 2f, 0f, 0f, 0f, 0f, 0f, 0f),
            "Treble Boost" to listOf(0f, 0f, 0f, 0f, 0f, 1f, 3f, 4f, 5f, 6f),
            "Electronic" to listOf(4f, 3f, 0f, -2f, -1f, 1f, 0f, 2f, 4f, 5f),
        )
    }
}

/**
 * All persisted user preferences. Every new field needs a default so that old settings files
 * keep loading; structural changes are handled by [SettingsStore.migrate].
 */
@Serializable
data class Settings(
    val version: Int = CURRENT_VERSION,
    val themeMode: ThemeMode = ThemeMode.System,
    /** Custom accent colour as ARGB; `null` means "use SaltUI's default highlight". */
    val accentColor: Long? = null,
    val useSystemAccent: Boolean = true,
    val fontScale: Float = 1f,
    val language: AppLanguage = AppLanguage.System,
    val musicDirs: List<String> = emptyList(),
    val backendPreference: BackendPreference = BackendPreference.Auto,
    /** Identifier of the backend that actually worked last time, e.g. `gstreamer`. */
    val lastWorkingBackend: String? = null,
    /** GStreamer/ALSA device id; empty means "system default". */
    val outputDevice: String = "",
    val closeBehavior: CloseBehavior = CloseBehavior.Quit,
    val windowMode: WindowMode = WindowMode.Fullscreen,
    val showTray: Boolean = true,
    val replayGain: ReplayGainMode = ReplayGainMode.Off,
    val equalizer: EqualizerSettings = EqualizerSettings(),
    val volume: Float = 0.8f,
    val playbackSpeed: Float = 1f,
    val songSort: SongSortKey = SongSortKey.Title,
    val songSortDescending: Boolean = false,
    val scanOnStartup: Boolean = true,
    val scanFollowSymlinks: Boolean = false,
    val lyricOffsetMs: Long = 0,
    val showLyricTranslation: Boolean = true,
    @SerialName("sleepTimerMinutes") val sleepTimerMinutes: Int = 0,
) {
    /** Defensive normalisation applied after load so a hand-edited file cannot break the UI. */
    fun sanitised(): Settings = copy(
        fontScale = fontScale.coerceIn(0.8f, 1.6f),
        volume = volume.coerceIn(0f, 1f),
        playbackSpeed = playbackSpeed.coerceIn(0.5f, 2f),
        musicDirs = musicDirs.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
        equalizer = equalizer.clamped(),
        sleepTimerMinutes = sleepTimerMinutes.coerceIn(0, 24 * 60),
        lyricOffsetMs = lyricOffsetMs.coerceIn(-30_000, 30_000),
        // Older builds persisted ARGB Int widened to Long. Accept any value whose low
        // 32 bits form an opaque colour; reset everything else so the UI cannot grey out.
        accentColor = accentColor?.takeIf { (it.toInt().ushr(24) and 0xFF) >= 0x80 },
    )

    companion object {
        const val CURRENT_VERSION = 2
    }
}
