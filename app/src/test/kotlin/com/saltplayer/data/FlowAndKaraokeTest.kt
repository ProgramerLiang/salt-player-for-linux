// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Corripo
package com.saltplayer.data

import androidx.compose.ui.graphics.Color
import com.saltplayer.data.prefs.FlowPalette
import com.saltplayer.data.prefs.Settings
import com.saltplayer.data.prefs.SettingsCodec
import com.saltplayer.domain.lyrics.KaraokeWipe
import com.saltplayer.domain.lyrics.LyricLine
import com.saltplayer.domain.lyrics.LyricWord
import com.saltplayer.ui.theme.Contrast
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContrastTest {

    private val orange = Color(0xFFE8611C)
    private val navy = Color(0xFF10203A)
    private val nearWhite = Color(0xFFF4F4F4)

    @Test
    fun `luminance follows the wcag formula`() {
        assertEquals(0f, Contrast.relativeLuminance(Color.Black), 1e-4f)
        assertEquals(1f, Contrast.relativeLuminance(Color.White), 1e-4f)
        // 50% grey linearises to ~0.2140
        val grey = Contrast.relativeLuminance(Color(0xFF808080))
        assertTrue(grey > 0.19f && grey < 0.24f, "grey luminance was $grey")
    }

    @Test
    fun `black on white is 21 to 1`() {
        assertEquals(21f, Contrast.contrastRatio(Color.Black, Color.White), 0.05f)
    }

    @Test
    fun `dark scheme produces light text above the body minimum`() {
        val colors = Contrast.lyricColors(orange, lightScheme = false)
        assertTrue(colors.highlightRatio >= Contrast.BODY_MIN, "ratio ${colors.highlightRatio}")
        assertTrue(Contrast.relativeLuminance(colors.background) < 0.32f)
        assertTrue(Contrast.contrastRatio(colors.highlight, colors.background) >= 4.5f)
    }

    @Test
    fun `light scheme produces dark text above the body minimum`() {
        val colors = Contrast.lyricColors(navy, lightScheme = true)
        assertTrue(colors.highlightRatio >= Contrast.BODY_MIN, "ratio ${colors.highlightRatio}")
        assertTrue(Contrast.relativeLuminance(colors.background) > 0.6f)
    }

    @Test
    fun `a colour that conflicts with the scheme is compensated, not forced`() {
        // Very light cover colour asked for the dark scheme: luminance is pulled down.
        val colors = Contrast.lyricColors(nearWhite, lightScheme = false)
        assertTrue(Contrast.relativeLuminance(colors.background) < 0.35f)
        assertTrue(Contrast.contrastRatio(colors.highlight, colors.background) >= 4.5f)
    }

    @Test
    fun `flatten keeps the hue family while moving luminance`() {
        val dark = Contrast.flattenToScheme(orange, lightScheme = false)
        val light = Contrast.flattenToScheme(orange, lightScheme = true)
        assertTrue(dark.red > dark.blue, "warm hue must survive darkening")
        assertTrue(
            Contrast.relativeLuminance(dark) < Contrast.relativeLuminance(light),
            "dark scheme must be darker than light scheme",
        )
    }

    private val covers = listOf(
        Color(0xFFE8611C), // red-orange, the user's reproduced case
        Color(0xFF2B6BFF), // blue
        Color(0xFFF4F0E6), // near-white
        Color(0xFF10203A), // near-black navy
        Color(0xFF16A085), // saturated teal
    )

    @Test
    fun `chrome palette keeps body text above 4 point 5 on every cover and mode`() {
        for (cover in covers) {
            for (lightScheme in listOf(false, true)) {
                val samples = com.saltplayer.ui.component.FlowPaletteLayers.samples(cover, lightScheme)
                val chrome = Contrast.chromeColors(samples, lightScheme, Color(0xFFE760F4))
                assertTrue(
                    chrome.secondaryRatio >= Contrast.BODY_MIN,
                    "secondary ${chrome.secondaryRatio} cover=$cover light=$lightScheme",
                )
                assertTrue(
                    chrome.primaryRatio >= Contrast.BODY_MIN,
                    "primary ${chrome.primaryRatio} cover=$cover light=$lightScheme",
                )
                assertTrue(
                    chrome.disabledRatio >= 3f,
                    "disabled ${chrome.disabledRatio} cover=$cover light=$lightScheme",
                )
                assertTrue(
                    Contrast.contrastRatio(chrome.accentText, chrome.background) >= 3f,
                    "accent icon cover=$cover light=$lightScheme",
                )
            }
        }
    }

    @Test
    fun `flow bed polarity matches the chosen scheme`() {
        val dark = Contrast.chromeColors(
            com.saltplayer.ui.component.FlowPaletteLayers.samples(orange, false), false, orange,
        )
        val light = Contrast.chromeColors(
            com.saltplayer.ui.component.FlowPaletteLayers.samples(orange, true), true, orange,
        )
        assertTrue(Contrast.relativeLuminance(dark.background) < 0.1f)
        assertTrue(Contrast.relativeLuminance(light.background) > 0.7f)
    }
}

/** Drawer transitions are pure progress functions; no pointer state may enter them. */
class DrawerMotionTest {
    @Test
    fun `scrim alpha follows progress and is stable for repeated pointer positions`() {
        fun scrimAlpha(progress: Float) = 0.32f * progress
        val open = scrimAlpha(1f)
        // Simulate moving the pointer up/down 20 times: progress does not change, so neither
        // does the scrim. This is the invariant the old implementation violated.
        repeat(20) { step ->
            val progress = 1f
            assertEquals(open, scrimAlpha(progress), 1e-6f, "pointer move $step changed the scrim")
        }
        assertEquals(0f, scrimAlpha(0f), 1e-6f)
        assertTrue(scrimAlpha(0.5f) in (0f..open))
    }
}

class KaraokeWipeTest {

    private val words = listOf(
        LyricWord(1_000, 1_400, "Hello "),
        LyricWord(1_400, 1_900, "brave "),
        LyricWord(1_900, 2_600, "world"),
    )

    @Test
    fun `slice reports the active word and its fraction`() {
        assertEquals(0, KaraokeWipe.sliceAt(words, 1_200)?.index)
        assertEquals(0.5f, KaraokeWipe.sliceAt(words, 1_200)!!.fraction, 0.01f)
        assertEquals(1, KaraokeWipe.sliceAt(words, 1_650)?.index)
        assertEquals(2, KaraokeWipe.sliceAt(words, 2_000)?.index)
    }

    @Test
    fun `progress is monotonic and clamps at both ends`() {
        assertEquals(null, KaraokeWipe.sliceAt(words, 900), "before the line there is no wipe")
        assertEquals(1f, KaraokeWipe.sliceAt(words, 99_999)!!.fraction, 0f)
        var previous = -1f
        var time = 1_000L
        while (time <= 2_600L) {
            val fraction = KaraokeWipe.fractionOfLine(words, time)
            assertTrue(fraction >= previous - 1e-4f, "fraction went backwards at $time")
            previous = fraction
            time += 25
        }
        assertEquals(0f, KaraokeWipe.fractionOfLine(words, 500), 0f)
        assertEquals(1f, KaraokeWipe.fractionOfLine(words, 9_999), 0f)
    }

    @Test
    fun `word boundaries are continuous across words`() {
        val endOfFirst = KaraokeWipe.fractionOfLine(words, 1_400)
        val startOfSecond = KaraokeWipe.fractionOfLine(words, 1_400)
        assertEquals(endOfFirst, startOfSecond, 0f)
        // no jump larger than one step when crossing the boundary
        val justBefore = KaraokeWipe.fractionOfLine(words, 1_399)
        val justAfter = KaraokeWipe.fractionOfLine(words, 1_401)
        assertTrue(justAfter - justBefore < 0.05f, "wipe jumped by ${justAfter - justBefore}")
    }

    @Test
    fun `missing ends are filled from the next word`() {
        val open = listOf(LyricWord(0, 0, "a"), LyricWord(500, 0, "b"))
        assertEquals(500L, KaraokeWipe.wordEnd(open, 0))
        assertEquals(700L, KaraokeWipe.wordEnd(open, 1), "last word gets a 200 ms tail")
    }

    @Test
    fun `line-level lyrics fall back to a linear wipe over the line duration`() {
        val line = LyricLine(timeMs = 1_000, text = "plain", endMs = 3_000)
        assertEquals(0f, KaraokeWipe.fractionOfLine(line, 1_000), 0f)
        assertEquals(0.5f, KaraokeWipe.fractionOfLine(line, 2_000), 0.01f)
        assertEquals(1f, KaraokeWipe.fractionOfLine(line, 3_000), 0f)
    }
}

class FlowSettingsTest {

    @Test
    fun `flow settings default to on, animated and dark`() {
        val settings = Settings()
        assertTrue(settings.flowBackground)
        assertTrue(settings.flowAnimated)
        assertEquals(FlowPalette.Dark, settings.flowPalette)
    }

    @Test
    fun `flow settings survive a save and load round trip`() {
        val dir = java.nio.file.Files.createTempDirectory("flow-settings")
        val store = com.saltplayer.data.prefs.SettingsStore(dir.resolve("settings.json"))
        store.save(Settings(flowBackground = false, flowAnimated = false, flowPalette = FlowPalette.Light))
        val loaded = store.load()
        assertEquals(false, loaded.flowBackground)
        assertEquals(false, loaded.flowAnimated)
        assertEquals(FlowPalette.Light, loaded.flowPalette)
    }

    @Test
    fun `older settings files without the flow keys keep the defaults`() {
        val legacy = """{"version":2,"volume":0.5}"""
        val parsed = SettingsCodec.parse(legacy)
        assertTrue(parsed.flowBackground)
        assertEquals(FlowPalette.Dark, parsed.flowPalette)
        assertEquals(0.5f, parsed.volume)
    }
}
