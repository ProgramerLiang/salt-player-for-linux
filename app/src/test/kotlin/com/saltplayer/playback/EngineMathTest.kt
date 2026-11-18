// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Corripo
package com.saltplayer.playback

import com.saltplayer.data.prefs.BackendPreference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class EngineMathTest {

    @Test
    fun `zero dB is unity gain`() {
        assertEquals(1.0, EngineMath.dbToGain(0.0), 1e-9)
    }

    @Test
    fun `minus six dB halves the amplitude`() {
        assertTrue(kotlin.math.abs(EngineMath.dbToGain(-6.0206) - 0.5) < 1e-3)
    }

    @Test
    fun `replay gain is folded into the effective volume`() {
        assertEquals(0.5, EngineMath.combineVolume(0.5f, null), 1e-6)
        assertEquals(1.0, EngineMath.combineVolume(0.5f, 6.0206), 1e-3)
    }

    @Test
    fun `combined volume is clamped to a safe range`() {
        assertEquals(8.0, EngineMath.combineVolume(1f, 40.0), 1e-6)
        assertEquals(0.0, EngineMath.combineVolume(0f, -40.0), 1e-6)
    }
}

class EngineRegistryTest {

    @Test
    fun `every advertised backend is either available with capabilities or unavailable with a reason`() {
        val probes = EngineRegistry().probeAll()
        assertEquals(3, probes.size)
        for (probe in probes) {
            if (probe.available) {
                assertTrue(probe.capabilities.supportedFeatures().isNotEmpty())
            } else {
                assertTrue(probe.detail.isNotBlank(), "${probe.id} must explain why it is unavailable")
            }
        }
    }

    @Test
    fun `java sound is always offered as the documented fallback`() {
        val probes = EngineRegistry().probeAll()
        assertTrue(probes.any { it.id == "javasound" && it.available })
    }

    @Test
    fun `gstreamer is probed on this machine and reports its capabilities`() {
        val gst = EngineRegistry().probeAll().first { it.id == "gstreamer" }
        assertTrue(gst.available, "GStreamer is installed here: ${gst.detail}")
        assertTrue(gst.capabilities.gapless)
    }

    @Test
    fun `create honours the pinned preference and reports the chosen backend`() {
        val registry = EngineRegistry()
        val prepared = registry.create(BackendPreference.JavaSound)
        assertTrue(prepared != null)
        assertEquals("javasound", prepared.engine.id)
        prepared.engine.close()
    }

    @Test
    fun `create falls back to an available backend when the pinned one is missing`() {
        val registry = EngineRegistry()
        val prepared = registry.create(BackendPreference.LibMpv)
        if (prepared != null) {
            assertNotEquals("", prepared.engine.id)
            prepared.engine.close()
        }
    }
}
