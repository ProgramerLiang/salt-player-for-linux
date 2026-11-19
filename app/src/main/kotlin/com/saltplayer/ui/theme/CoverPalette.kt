// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Corripo
package com.saltplayer.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Dominant-colour extraction for the flowing player background.
 *
 * Pure integer maths so it is testable without a graphics context: callers pass the ARGB pixels
 * of a small thumbnail. Pixels are quantised to 5 bits per channel, near-black and near-white
 * entries are dropped (they carry no hue), and the most frequent bucket wins. A small saturation
 * and lightness boost keeps the result usable as a background tint.
 */
object CoverPalette {

    data class Rgb(val red: Int, val green: Int, val blue: Int)

    fun dominant(pixels: IntArray): Rgb? {
        if (pixels.isEmpty()) return null
        val buckets = HashMap<Int, IntArray>() // key -> [count, sumR, sumG, sumB]
        for (argb in pixels) {
            val a = (argb ushr 24) and 0xFF
            if (a < 128) continue // skip transparent padding around cover art
            val r = (argb ushr 16) and 0xFF
            val g = (argb ushr 8) and 0xFF
            val b = argb and 0xFF
            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            val value = max / 255f
            if (value < 0.12f || value > 0.95f) continue // near black / near white
            if ((max - min) < 12) continue // greys carry no identity
            val key = ((r shr 3) shl 10) or ((g shr 3) shl 5) or (b shr 3)
            val entry = buckets.getOrPut(key) { IntArray(4) }
            entry[0]++
            entry[1] += r
            entry[2] += g
            entry[3] += b
        }
        val best = buckets.values.maxByOrNull { it[0] } ?: return null
        val count = best[0].coerceAtLeast(1)
        return boost(Rgb(best[1] / count, best[2] / count, best[3] / count))
    }

    /** Nudges saturation/lightness so the colour survives as a dark, readable backdrop. */
    fun boost(rgb: Rgb, saturation: Float = 1.25f, lightness: Float = 0.42f): Rgb {
        val r = rgb.red / 255f
        val g = rgb.green / 255f
        val b = rgb.blue / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val l = (max + min) / 2f
        val d = max - min
        val s = if (d == 0f) 0f else d / (1f - kotlin.math.abs(2f * l - 1f))
        val newS = (s * saturation).coerceIn(0f, 1f)
        return hslToRgb(hue(r, g, b, max, d), newS, lightness)
    }

    private fun hue(r: Float, g: Float, b: Float, max: Float, d: Float): Float {
        if (d == 0f) return 0f
        val h = when (max) {
            r -> ((g - b) / d) % 6f
            g -> (b - r) / d + 2f
            else -> (r - g) / d + 4f
        }
        return (h * 60f + 360f) % 360f
    }

    private fun hslToRgb(hue: Float, s: Float, l: Float): Rgb {
        val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
        val x = c * (1f - kotlin.math.abs((hue / 60f) % 2f - 1f))
        val m = l - c / 2f
        val (r1, g1, b1) = when {
            hue < 60f -> Triple(c, x, 0f)
            hue < 120f -> Triple(x, c, 0f)
            hue < 180f -> Triple(0f, c, x)
            hue < 240f -> Triple(0f, x, c)
            hue < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return Rgb(
            ((r1 + m) * 255f).toInt().coerceIn(0, 255),
            ((g1 + m) * 255f).toInt().coerceIn(0, 255),
            ((b1 + m) * 255f).toInt().coerceIn(0, 255),
        )
    }

    fun toColor(rgb: Rgb): Color = Color(rgb.red, rgb.green, rgb.blue)

    /** Rotates the hue of [color] by [degrees], used by the slow background drift. */
    fun shiftHue(color: Color, degrees: Float): Color {
        val r = color.red
        val g = color.green
        val b = color.blue
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val d = max - min
        val l = (max + min) / 2f
        val s = if (d == 0f) 0f else d / (1f - kotlin.math.abs(2f * l - 1f))
        val h = (hue(r, g, b, max, d) + degrees + 360f) % 360f
        val rgb = hslToRgb(h, s, l)
        return toColor(rgb)
    }
}
