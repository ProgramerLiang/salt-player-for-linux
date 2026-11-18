// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Corripo
package com.saltplayer.lyrics

import com.saltplayer.domain.lyrics.LrcParser
import com.saltplayer.domain.lyrics.Lyrics
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LrcParserTest {

    @Test
    fun `parses timestamps with centiseconds and milliseconds`() {
        val lyrics = LrcParser.parse(
            """
            [00:01.50]first
            [00:12.345]second
            [01:00]third
            """.trimIndent()
        )
        assertTrue(lyrics.synced)
        assertEquals(listOf(1500L, 12345L, 60_000L), lyrics.lines.map { it.timeMs })
        assertEquals(listOf("first", "second", "third"), lyrics.lines.map { it.text })
    }

    @Test
    fun `expands repeated timestamps into separate lines`() {
        val lyrics = LrcParser.parse("[00:01.00][00:31.00]chorus")
        assertEquals(2, lyrics.lines.size)
        assertEquals(1000L, lyrics.lines[0].timeMs)
        assertEquals(31_000L, lyrics.lines[1].timeMs)
    }

    @Test
    fun `duplicate timestamps become translations`() {
        val lyrics = LrcParser.parse(
            """
            [00:01.00]Hello
            [00:01.00]你好
            """.trimIndent()
        )
        assertEquals(1, lyrics.lines.size)
        assertEquals("Hello", lyrics.lines[0].text)
        assertEquals("你好", lyrics.lines[0].translation)
    }

    @Test
    fun `reads metadata tags and offset`() {
        val lyrics = LrcParser.parse(
            """
            [ti:Sample]
            [ar:Artist]
            [al:Album]
            [by:tester]
            [offset:+500]
            [00:02.00]line
            """.trimIndent()
        )
        assertEquals("Sample", lyrics.title)
        assertEquals("Artist", lyrics.artist)
        assertEquals("Album", lyrics.album)
        assertEquals("tester", lyrics.by)
        assertEquals(500L, lyrics.offsetMs)
        // Positive offset makes lines appear earlier.
        assertEquals(0, lyrics.indexAt(1500))
        assertEquals(-1, lyrics.indexAt(1400))
    }

    @Test
    fun `untimed text is returned as unsynchronised lyrics`() {
        val lyrics = LrcParser.parse("just some words\n\nand more")
        assertFalse(lyrics.synced)
        assertEquals(3, lyrics.lines.size)
        assertEquals("just some words", lyrics.lines[0].text)
    }

    @Test
    fun `indexAt finds the active line with binary search`() {
        val lyrics = LrcParser.parse("[00:00.00]a\n[00:05.00]b\n[00:10.00]c")
        assertEquals(-1, lyrics.indexAt(0) - 1) // sanity: 0 is the first line, so index 0
        assertEquals(0, lyrics.indexAt(0))
        assertEquals(0, lyrics.indexAt(4999))
        assertEquals(1, lyrics.indexAt(5000))
        assertEquals(2, lyrics.indexAt(999_999))
    }

    @Test
    fun `empty input yields empty lyrics`() {
        val lyrics = LrcParser.parse("")
        assertTrue(lyrics.isEmpty)
        assertNull(lyrics.lineAt(0))
    }

    @Test
    fun `write produces a parseable document`() {
        val original = LrcParser.parse("[ti:T]\n[00:01.000]one\n[00:01.000]translation\n[00:02.500]two")
        val text = LrcParser.write(original)
        val reparsed = LrcParser.parse(text)
        assertEquals(original.lines.map { it.timeMs }, reparsed.lines.map { it.timeMs })
        assertEquals("translation", reparsed.lines[0].translation)
        assertEquals(Lyrics.Source.Sidecar, reparsed.source)
    }
}
