// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Corripo
package com.saltplayer.data

import com.saltplayer.data.scan.TagReader
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TagReaderTest {

    @Test
    fun `parses replay gain values`() {
        assertEquals(-7.5, TagReader.parseGain("-7.50 dB"))
        assertEquals(3.25, TagReader.parseGain("+3,25 dB"))
        assertEquals(1.0, TagReader.parseGain("1"))
        assertNull(TagReader.parseGain(""))
        assertNull(TagReader.parseGain("no gain here"))
    }

    @Test
    fun `parses track numbers written as n slash total`() {
        assertEquals(3, TagReader.parseTrackNumber("3/12"))
        assertEquals(7, TagReader.parseTrackNumber("07"))
        assertEquals(0, TagReader.parseTrackNumber(null))
        assertEquals(0, TagReader.parseTrackNumber("A"))
    }

    @Test
    fun `parses years from id3 style strings`() {
        assertEquals(1997, TagReader.parseYear("1997"))
        assertEquals(2011, TagReader.parseYear("2011-05-01"))
        assertEquals(0, TagReader.parseYear("unknown"))
    }

    @Test
    fun `reads duration and format from a real wav file`() {
        val file = AudioFixture.writeWav(Files.createTempDirectory("salt-tag").resolve("tone.wav"), seconds = 1.5)
        val tags = TagReader.read(file)
        assertNotNull(tags)
        assertEquals("WAV", tags.format.uppercase().take(3))
        assertTrue(tags.durationMs in 1400..1600, "duration should be ~1500 ms but was ${tags.durationMs}")
        assertTrue(tags.sampleRateHz >= 44_000)
        assertEquals("tone", tags.title)
    }

    @Test
    fun `falls back to filename metadata for unreadable files`() {
        val file = Files.createTempDirectory("salt-broken").resolve("broken.flac")
        Files.write(file, ByteArray(64) { 0x5A })
        val tags = TagReader.read(file)
        assertEquals("broken", tags.title)
        assertEquals("FLAC", tags.format)
    }
}
