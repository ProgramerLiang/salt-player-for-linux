// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Corripo
package com.saltplayer.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.saltplayer.ui.theme.AccentPalette
import kotlin.test.Test
import kotlin.test.assertEquals

/** Regression: settings persist ARGB Int widened to Long; Color must read the low 32 bits. */
class ThemeColorTest {
    @Test
    fun `stored accent round trips through signed long widening`() {
        for ((_, expected) in AccentPalette.entries) {
            val stored = expected.toArgb().toLong()
            val restored = Color(stored.toInt())
            assertEquals(expected.toArgb(), restored.toArgb())
        }
    }

    @Test
    fun `legacy packed value is rejected by the settings sanitiser`() {
        // -6295550177574912 is what the old build persisted (Compose packed ULong, not ARGB).
        // Its low 32 bits are not an opaque colour, so sanitising must drop it instead of
        // rendering an invisible/grey accent.
        val legacy = -6295550177574912L
        val sanitised = com.saltplayer.data.prefs.Settings(accentColor = legacy).sanitised()
        assertEquals(null, sanitised.accentColor)
    }

    @Test
    fun `opaque argb accent survives the sanitiser`() {
        val orange = 0xFFE8593CL
        val sanitised = com.saltplayer.data.prefs.Settings(accentColor = orange).sanitised()
        assertEquals(orange, sanitised.accentColor)
    }
}
