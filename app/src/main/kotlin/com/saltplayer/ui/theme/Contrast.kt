// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Corripo
package com.saltplayer.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Contrast helpers for text drawn on top of the flowing background.
 *
 * Colours are converted with the WCAG 2.x formulas: sRGB channels are linearised, luminance is
 * `0.2126R + 0.7152G + 0.0722B`, and the contrast ratio is `(L1 + 0.05) / (L2 + 0.05)`. Every
 * decision made here is a pure function so the guaranteed ratios can be unit-tested.
 */
object Contrast {

    /** WCAG minimum for body text. */
    const val BODY_MIN = 4.5f

    /** Stricter target for the highlighted lyric line. */
    const val HIGHLIGHT_MIN = 7.0f

    enum class Polarity { LightText, DarkText }

    fun linearise(channel: Float): Float {
        val c = channel.coerceIn(0f, 1f)
        return if (c <= 0.03928f) c / 12.92f else Math.pow(((c + 0.055) / 1.055).toDouble(), 2.4).toFloat()
    }

    fun relativeLuminance(color: Color): Float =
        0.2126f * linearise(color.red) +
            0.7152f * linearise(color.green) +
            0.0722f * linearise(color.blue)

    fun contrastRatio(a: Color, b: Color): Float {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        val lighter = maxOf(la, lb)
        val darker = minOf(la, lb)
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    /** Picks the polarity that gives the better ratio against [background]. */
    fun polarityFor(background: Color): Polarity =
        if (contrastRatio(Color.White, background) >= contrastRatio(Color(0xFF101014), background)) {
            Polarity.LightText
        } else {
            Polarity.DarkText
        }

    fun textColor(polarity: Polarity): Color = when (polarity) {
        Polarity.LightText -> Color(0xFFF7F8FA)
        Polarity.DarkText -> Color(0xFF101014)
    }

    /**
     * Flattens [base] into the requested colour scheme.
     *
     * "Dark scheme" pulls the colour down into 0.16..0.30 relative luminance, "light scheme" lifts
     * it to 0.72..0.88. Working in luminance (instead of hsl lightness) means a cover colour can
     * never leave the text below [BODY_MIN] contrast: the worst case in each band is checked by
     * the unit tests.
     */
    /**
     * Blends [base] toward white or black until it lands in the scheme's luminance band.
     *
     * Blending (rather than scaling) is required: scaling a dark navy can never reach 0.80
     * luminance, so a "light scheme" would silently stay dark on dark covers.
     */
    fun flattenToScheme(base: Color, lightScheme: Boolean): Color {
        val target = if (lightScheme) 0.80f else 0.22f
        val current = relativeLuminance(base)
        if (kotlin.math.abs(current - target) < 0.02f) return base
        val mixTo = if (lightScheme) Color.White else Color.Black
        var low = 0f
        var high = 1f
        var best = base
        repeat(16) {
            val t = (low + high) / 2f
            val candidate = blend(base, mixTo, t)
            best = candidate
            val luminance = relativeLuminance(candidate)
            val tooLight = luminance > target
            if (lightScheme) {
                if (tooLight) high = t else low = t
            } else {
                if (tooLight) low = t else high = t
            }
        }
        return best
    }

    fun blend(from: Color, to: Color, amount: Float): Color {
        val t = amount.coerceIn(0f, 1f)
        return Color(
            red = from.red + (to.red - from.red) * t,
            green = from.green + (to.green - from.green) * t,
            blue = from.blue + (to.blue - from.blue) * t,
        )
    }

    /**
     * Text colours for a background, guaranteed to meet [BODY_MIN] and aiming for
     * [HIGHLIGHT_MIN] on the highlighted line. When the preferred polarity cannot reach the
     * target the background is compensated instead of shipping unreadable text.
     */
    data class LyricColors(
        val background: Color,
        val body: Color,
        val highlight: Color,
        val translation: Color,
        val bodyRatio: Float,
        val highlightRatio: Float,
    )

    fun lyricColors(
        base: Color,
        lightScheme: Boolean,
        minimumBodyRatio: Float = BODY_MIN,
    ): LyricColors {
        var background = flattenToScheme(base, lightScheme)
        var polarity = if (lightScheme) Polarity.DarkText else Polarity.LightText
        var body = textColor(polarity)
        // Safety net: if the scheme band still leaves the text short of the minimum, walk the
        // background away from the text until the ratio is met.
        var guard = 0
        while (contrastRatio(body, background) < minimumBodyRatio && guard++ < 12) {
            background = if (polarity == Polarity.LightText) {
                blend(background, Color.Black, 0.12f)
            } else {
                blend(background, Color.White, 0.12f)
            }
        }
        if (contrastRatio(body, background) < contrastRatio(textColor(opposite(polarity)), background)) {
            polarity = opposite(polarity)
            body = textColor(polarity)
        }
        val highlightRatio = contrastRatio(body, background)
        return LyricColors(
            background = background,
            body = body.copy(alpha = 0.92f),
            highlight = body,
            translation = body.copy(alpha = 0.70f),
            bodyRatio = contrastRatio(body.copy(alpha = 0.92f), background),
            highlightRatio = highlightRatio,
        )
    }

    private fun opposite(polarity: Polarity): Polarity = when (polarity) {
        Polarity.LightText -> Polarity.DarkText
        Polarity.DarkText -> Polarity.LightText
    }
}
