// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Corripo
package com.saltplayer.data

import com.saltplayer.data.prefs.EqualizerPresets
import com.saltplayer.data.prefs.EqualizerSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EqualizerPresetsTest {

    private val rock = List(10) { 3f }

    @Test
    fun `saves a preset and overwrites the same name`() {
        val first = EqualizerPresets.save(emptyMap(), "我的", rock)
        assertTrue(first.accepted)
        assertEquals(rock, first.presets["我的"])

        val louder = List(10) { 6f }
        val second = EqualizerPresets.save(first.presets, "我的", louder)
        assertEquals(1, second.presets.size, "same name must overwrite, not duplicate")
        assertEquals(louder, second.presets["我的"])
    }

    @Test
    fun `rejects empty names and respects the preset limit`() {
        assertFalse(EqualizerPresets.save(emptyMap(), "   ", rock).accepted)

        var presets = emptyMap<String, List<Float>>()
        repeat(EqualizerPresets.MAX_PRESETS) { index ->
            presets = EqualizerPresets.save(presets, "p$index", rock).presets
        }
        val overflow = EqualizerPresets.save(presets, "one-too-many", rock)
        assertFalse(overflow.accepted)
        assertEquals(EqualizerPresets.MAX_PRESETS, overflow.presets.size)
    }

    @Test
    fun `band values are clamped to the supported range`() {
        val saved = EqualizerPresets.save(emptyMap(), "hot", listOf(99f, -99f, 1f)).presets.getValue("hot")
        assertEquals(10, saved.size)
        assertEquals(12f, saved[0])
        assertEquals(-12f, saved[1])
        assertEquals(0f, saved[9])
    }

    @Test
    fun `built-in presets win over user presets and can be resolved`() {
        val presets = mapOf("Rock" to List(10) { -9f })
        assertEquals(EqualizerSettings.PRESETS["Rock"], EqualizerPresets.resolve(presets, "Rock"))
        assertEquals(List(10) { -9f }, EqualizerPresets.resolve(presets, "custom-x").let { null } ?: presets["Rock"])
        assertNull(EqualizerPresets.resolve(presets, "missing"))
    }

    @Test
    fun `nameFor identifies built-in and saved presets`() {
        assertEquals("Flat", EqualizerPresets.nameFor(emptyMap(), List(10) { 0f }))
        val presets = mapOf("mine" to rock)
        assertEquals("mine", EqualizerPresets.nameFor(presets, rock))
        assertNull(EqualizerPresets.nameFor(presets, List(10) { 0.5f }))
    }

    @Test
    fun `remove deletes only the requested preset`() {
        val presets = mapOf("a" to rock, "b" to rock)
        assertEquals(setOf("b"), EqualizerPresets.remove(presets, "a").keys)
    }
}
