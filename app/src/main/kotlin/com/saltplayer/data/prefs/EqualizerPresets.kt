// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Corripo
package com.saltplayer.data.prefs

/**
 * User preset bookkeeping for the equalizer.
 *
 * Kept as pure functions over an immutable map so saving, overwriting and deleting can be tested
 * without touching the settings file. Band values are clamped to the same ±12 dB range the audio
 * backend accepts, and the number of presets is capped to keep `settings.json` small.
 */
object EqualizerPresets {

    const val MAX_PRESETS = 12
    const val MAX_NAME_LENGTH = 24

    data class StoreResult(val presets: Map<String, List<Float>>, val accepted: Boolean, val reason: String? = null)

    fun normaliseBands(bands: List<Float>): List<Float> =
        List(EqualizerSettings.BAND_FREQUENCIES.size) { index ->
            (bands.getOrNull(index) ?: 0f).coerceIn(-12f, 12f)
        }

    fun save(
        presets: Map<String, List<Float>>,
        name: String,
        bands: List<Float>,
    ): StoreResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return StoreResult(presets, false, "empty name")
        val key = trimmed.take(MAX_NAME_LENGTH)
        val isOverwrite = presets.containsKey(key)
        if (!isOverwrite && presets.size >= MAX_PRESETS) {
            return StoreResult(presets, false, "limit reached")
        }
        val updated = LinkedHashMap(presets)
        updated[key] = normaliseBands(bands)
        return StoreResult(updated, true)
    }

    fun remove(presets: Map<String, List<Float>>, name: String): Map<String, List<Float>> =
        presets.filterKeys { it != name }

    /** Resolves a preset name against built-ins first, then user presets. */
    fun resolve(presets: Map<String, List<Float>>, name: String): List<Float>? =
        EqualizerSettings.PRESETS[name] ?: presets[name]

    /** True when [bands] matches a built-in or saved preset, so the UI can highlight it. */
    fun nameFor(presets: Map<String, List<Float>>, bands: List<Float>): String? {
        val normalised = normaliseBands(bands)
        EqualizerSettings.PRESETS.entries.firstOrNull { it.value == normalised }?.let { return it.key }
        presets.entries.firstOrNull { it.value == normalised }?.let { return it.key }
        return null
    }
}
