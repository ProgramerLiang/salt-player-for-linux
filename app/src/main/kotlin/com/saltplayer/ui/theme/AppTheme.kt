// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Corripo
package com.saltplayer.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import com.moriafly.salt.ui.SaltColors
import com.moriafly.salt.ui.SaltConfigs
import com.moriafly.salt.ui.SaltDynamicColors
import com.moriafly.salt.ui.SaltTextStyles
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.saltTextStyles
import com.saltplayer.data.prefs.Settings
import com.saltplayer.data.prefs.ThemeMode

/** Accent colours offered in Settings › Appearance. */
object AccentPalette {
    val entries: List<Pair<String, Color>> = listOf(
        "Salt" to Color(0xFF0470E6),
        "Coral" to Color(0xFFE8593C),
        "Mint" to Color(0xFF16A085),
        "Violet" to Color(0xFF7A5AF8),
        "Amber" to Color(0xFFE9A23B),
        "Rose" to Color(0xFFE14C82),
        "Teal" to Color(0xFF118AB2),
        "Forest" to Color(0xFF3A7D44),
    )

    /** Maps a freedesktop accent name (`blue`, `teal`, …) to a colour. */
    fun fromSystemName(name: String?): Color? = when (name?.lowercase()) {
        "blue" -> Color(0xFF0470E6)
        "teal" -> Color(0xFF118AB2)
        "green" -> Color(0xFF3A7D44)
        "yellow" -> Color(0xFFE9A23B)
        "orange" -> Color(0xFFE8593C)
        "red" -> Color(0xFFD33D3D)
        "pink" -> Color(0xFFE14C82)
        "purple" -> Color(0xFF7A5AF8)
        "slate" -> Color(0xFF64748B)
        else -> null
    }
}

/**
 * Applies the SaltUI theme with the user's dark-mode, accent and font-scale preferences.
 *
 * SaltUI is the theme engine here: this composable only decides *which* colours to hand it.
 */
@Composable
fun AppTheme(
    settings: Settings,
    systemDark: Boolean,
    systemAccentName: String?,
    content: @Composable () -> Unit,
) {
    val dark = when (settings.themeMode) {
        ThemeMode.System -> systemDark
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val accent = settings.accentColor?.let { Color(it.toULong().toLong() shl 0) }
        ?: settings.accentColor?.let { Color(it) }
        ?: if (settings.useSystemAccent) AccentPalette.fromSystemName(systemAccentName) else null

    val lightBase = SaltColors.defaultLight()
    val darkBase = SaltColors.defaultDark()
    val colors = SaltDynamicColors(
        light = if (accent != null) lightBase.copy(highlight = accent, onHighlight = onHighlightFor(accent)) else lightBase,
        dark = if (accent != null) darkBase.copy(highlight = accent, onHighlight = onHighlightFor(accent)) else darkBase,
    )

    SaltTheme(
        configs = SaltConfigs.default(isDarkTheme = dark),
        dynamicColors = colors,
        textStyles = scaledTextStyles(settings.fontScale),
        content = content,
    )
}

private fun scaledTextStyles(scale: Float): SaltTextStyles {
    if (scale == 1f) return saltTextStyles()
    val base = saltTextStyles()
    return base.copy(
        main = base.main.scaled(scale),
        sub = base.sub.scaled(scale),
        paragraph = base.paragraph.scaled(scale),
        largeTitle = base.largeTitle.scaled(scale),
    )
}

private fun TextStyle.scaled(scale: Float): TextStyle {
    val size: TextUnit = fontSize
    return if (size.isSp) copy(fontSize = size * scale) else this
}

/** Chooses black or white text for a coloured surface, by luminance. */
fun onHighlightFor(color: Color): Color {
    val luminance = 0.299f * color.red + 0.587f * color.green + 0.114f * color.blue
    return if (luminance > 0.62f) Color(0xFF10131A) else Color.White
}
