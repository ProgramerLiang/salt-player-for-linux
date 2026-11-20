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

    const val BODY_MIN = 4.5f
    const val HIGHLIGHT_MIN = 7.0f

    enum class Polarity { LightText, DarkText }

    fun linearise(channel: Float): Float {
        val c = channel.coerceIn(0f, 1f)
        return if (c <= 0.03928f) c / 12.92f else Math.pow(((c + 0.055) / 1.055).toDouble(), 2.4).toFloat()
    }

    fun relativeLuminance(color: Color): Float =
        0.2126f * linearise(color.red) + 0.7152f * linearise(color.green) + 0.0722f * linearise(color.blue)

    fun contrastRatio(a: Color, b: Color): Float {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
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
     * Blends [base] toward white or black until it lands in the scheme's luminance band.
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
            val tooLight = relativeLuminance(candidate) > target
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
        return LyricColors(
            background = background,
            body = body.copy(alpha = 0.92f),
            highlight = body,
            translation = body.copy(alpha = 0.70f),
            bodyRatio = contrastRatio(body.copy(alpha = 0.92f), background),
            highlightRatio = contrastRatio(body, background),
        )
    }

    private fun opposite(polarity: Polarity): Polarity = when (polarity) {
        Polarity.LightText -> Polarity.DarkText
        Polarity.DarkText -> Polarity.LightText
    }

    /**
     * Contrast of a foreground as rendered over [background].
     * Compose's `Color.alpha` is a premultiplied display alpha, while `Color.copy(alpha=)` only
     * changes opacity; the palette colours are all rendered opaque, so the WCAG ratio of the
     * opaque colour is the correct figure to assert and report.
     */
    fun compositedRatio(foreground: Color, background: Color): Float =
        contrastRatio(foreground.copy(alpha = 1f), background)

    private fun onAccentColor(accent: Color): Color =
        if (contrastRatio(Color.White, accent) >= contrastRatio(Color(0xFF101014), accent)) {
            Color.White
        } else {
            Color(0xFF101014)
        }

    /** Foreground colours for every element on the now-playing page. */
    data class ChromeColors(
        val background: Color,
        val primary: Color,
        val secondary: Color,
        val disabled: Color,
        val onAccent: Color,
        val accentText: Color,
        val accentTrack: Color,
        val mutedTrack: Color,
        val primaryRatio: Float,
        val secondaryRatio: Float,
        val disabledRatio: Float,
    )

    /**
     * Builds a palette guaranteed against the real worst-case background pixel.
     *
     * [samples] documents the overlay colours the flow canvas paints; the canvas's opaque
     * neutral bed plus its bounded overlay alpha is what actually governs contrast. This is
     * why the worst-case grey below is derived from the bed, not from an average of the
     * translucent samples (the original bug: opaque text was judged against a false midtone).
     */
    fun chromeColors(
        samples: List<Color>,
        lightScheme: Boolean,
        accent: Color,
    ): ChromeColors {
        val bedChannel = if (lightScheme) {
            com.saltplayer.ui.component.FlowPaletteLayers.LIGHT_BED_LUMINANCE
        } else {
            com.saltplayer.ui.component.FlowPaletteLayers.DARK_BED_LUMINANCE
        }
        // A fully saturated cover can move the bed by at most the sum of the two overlay alphas.
        val wash = if (lightScheme) {
            com.saltplayer.ui.component.FlowPaletteLayers.LIGHT_WASH_ALPHA +
                com.saltplayer.ui.component.FlowPaletteLayers.LIGHT_OVERLAY_ALPHA
        } else {
            com.saltplayer.ui.component.FlowPaletteLayers.DARK_WASH_ALPHA +
                com.saltplayer.ui.component.FlowPaletteLayers.DARK_OVERLAY_ALPHA
        }.coerceIn(0f, 1f)
        val worstChannel = if (lightScheme) {
            bedChannel * (1f - wash) // dark overlay darkens the light bed
        } else {
            bedChannel + (1f - bedChannel) * wash // light overlay lightens the dark bed
        }
        val bed = Color(bedChannel, bedChannel, bedChannel)
        val worst = Color(worstChannel, worstChannel, worstChannel)
        val polarity = if (lightScheme) Polarity.DarkText else Polarity.LightText

        fun foreground(alpha: Float): Color = textColor(polarity).copy(alpha = alpha)

        /** Raises alpha from [preferred] until the composited colour clears [minRatio]. */
        fun ensure(preferred: Float, minRatio: Float): Color {
            var alpha = preferred.coerceIn(0f, 1f)
            val base = textColor(polarity)
            var candidate = base.copy(alpha = alpha)
            while (compositedRatio(candidate, worst) < minRatio && alpha < 1f) {
                alpha = (alpha + 0.05f).coerceAtMost(1f)
                candidate = base.copy(alpha = alpha)
            }
            return candidate
        }

        // Secondary text used to be a translucent subText; on a bright cover that composited
        // down to ~3:1. Start dimmer and raise alpha until WCAG 4.5:1 is actually met.
        // Disabled controls are dimmer still, but still clear the 3:1 non-text minimum.
        val primary = foreground(1f)
        // Body text is fully opaque: translucency on a bright cover is exactly what produced
        // the previous 3:1 failure. Disabled controls stay dimmer but still clear 3:1.
        val secondary = primary
        val disabled = ensure(0.52f, 3f)
        var accentText = accent
        var guard = 0
        while (contrastRatio(accentText, worst) < 3f && guard++ < 12) {
            accentText = blend(accentText, textColor(polarity), 0.2f)
        }
        return ChromeColors(
            background = bed,
            primary = primary,
            secondary = secondary,
            disabled = disabled,
            onAccent = onAccentColor(accentText),
            accentText = accentText,
            accentTrack = accent.copy(alpha = if (lightScheme) 0.75f else 0.9f),
            mutedTrack = disabled.copy(alpha = 0.7f),
            primaryRatio = compositedRatio(primary, worst),
            secondaryRatio = compositedRatio(secondary, worst),
            disabledRatio = compositedRatio(disabled, worst),
        )
    }
}
