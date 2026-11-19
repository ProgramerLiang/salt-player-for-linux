// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Corripo
package com.saltplayer.data

import com.saltplayer.core.UiScale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UiScaleTest {

    @Test
    fun `percent is clamped to the supported range`() {
        assertEquals(80, UiScale.clampPercent(10))
        assertEquals(150, UiScale.clampPercent(400))
        assertEquals(120, UiScale.clampPercent(120))
        assertEquals(0, UiScale.clampPercent(0), "0 means follow system")
    }

    @Test
    fun `slider values snap to five percent steps`() {
        assertEquals(80, UiScale.snapPercent(80))
        assertEquals(85, UiScale.snapPercent(83))
        assertEquals(150, UiScale.snapPercent(149))
        assertEquals(0, UiScale.snapPercent(0))
    }

    @Test
    fun `follow system uses the detected display scale`() {
        assertEquals(1.25f, UiScale.factor(0, 1.25f))
        assertEquals(1f, UiScale.factor(0, 0f), "invalid detection falls back to 1.0")
    }

    @Test
    fun `explicit percent overrides the detected scale`() {
        assertEquals(1.2f, UiScale.factor(120, 1.25f))
    }

    @Test
    fun `negative and non finite scales are rejected`() {
        assertEquals(1f, UiScale.resolve(1f, -2f, 1f).density)
        assertEquals(1f, UiScale.resolve(Float.NaN, 1f, 1f).density)
    }

    @Test
    fun `ui factor scales density while font scale stacks on top`() {
        val resolved = UiScale.resolve(baseDensity = 1.25f, uiFactor = 1.2f, fontScale = 1.1f)
        assertEquals(1.5f, resolved.density, 1e-6f)
        assertEquals(1.1f, resolved.fontScale, 1e-6f)
    }

    @Test
    fun `xft dpi is converted to a scale factor`() {
        assertEquals(1.25f, UiScale.parseXftDpi("Xft.dpi:\t120\n"))
        assertEquals(1f, UiScale.parseXftDpi("Xft.dpi: 96"))
        assertNull(UiScale.parseXftDpi("no dpi here"))
        assertNull(UiScale.parseXftDpi(null))
    }

    @Test
    fun `environment scale is validated`() {
        assertEquals(2f, UiScale.parseEnvScale("2"))
        assertNull(UiScale.parseEnvScale("0"))
        assertNull(UiScale.parseEnvScale("nonsense"))
    }
}
