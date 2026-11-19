// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Corripo
package com.saltplayer.core

/**
 * UI scale maths, kept free of Compose so the rules can be unit-tested.
 *
 * The toolkit reports `density = 1.0` under XWayland here (the compositor resizes the surface
 * instead of telling the client the display scale), so the application has to apply the factor
 * itself. [percent] is the user's setting; `0` means "follow the system".
 */
object UiScale {

    const val FOLLOW_SYSTEM = 0
    const val MIN_PERCENT = 80
    const val MAX_PERCENT = 150
    const val STEP_PERCENT = 5
    const val DEFAULT_PERCENT = 100

    fun clampPercent(percent: Int): Int =
        if (percent == FOLLOW_SYSTEM) FOLLOW_SYSTEM else percent.coerceIn(MIN_PERCENT, MAX_PERCENT)

    /** Rounded to the slider step so persisted values always land on a tick. */
    fun snapPercent(percent: Int): Int {
        if (percent == FOLLOW_SYSTEM) return FOLLOW_SYSTEM
        val clamped = clampPercent(percent)
        val steps = Math.round((clamped - MIN_PERCENT).toFloat() / STEP_PERCENT)
        return (MIN_PERCENT + steps * STEP_PERCENT).coerceIn(MIN_PERCENT, MAX_PERCENT)
    }

    /** Effective multiplier; falls back to [systemScale] when the user follows the system. */
    fun factor(percent: Int, systemScale: Float): Float {
        val safeSystem = if (systemScale.isFinite() && systemScale > 0.1f) systemScale else 1f
        return if (percent == FOLLOW_SYSTEM) safeSystem else clampPercent(percent) / 100f
    }

    /**
     * Density and font scale handed to `LocalDensity`.
     *
     * The UI factor multiplies both, so text, controls, icons and spacing grow together; the
     * user's font scale is applied *on top* of it, which keeps the two settings independent.
     */
    fun resolve(baseDensity: Float, uiFactor: Float, fontScale: Float): Resolved {
        val safeBase = if (baseDensity.isFinite() && baseDensity > 0.1f) baseDensity else 1f
        val safeUi = if (uiFactor.isFinite() && uiFactor > 0.1f) uiFactor else 1f
        val safeFont = if (fontScale.isFinite() && fontScale > 0.1f) fontScale else 1f
        return Resolved(density = safeBase * safeUi, fontScale = safeFont)
    }

    data class Resolved(val density: Float, val fontScale: Float)

    /** Parses `Xft.dpi: 120` from `xrdb -query` output into a scale factor (120 dpi = 1.25). */
    fun parseXftDpi(output: String?): Float? {
        if (output.isNullOrBlank()) return null
        val match = Regex("""Xft\.dpi:\s*(\d+(?:\.\d+)?)""").find(output) ?: return null
        val dpi = match.groupValues[1].toFloatOrNull() ?: return null
        if (dpi <= 0f) return null
        return dpi / 96f
    }

    /** Parses `GDK_SCALE`/`GDK_DPI_SCALE` style integers. */
    fun parseEnvScale(value: String?): Float? {
        val parsed = value?.trim()?.toFloatOrNull() ?: return null
        return if (parsed in 0.5f..4f) parsed else null
    }
}
