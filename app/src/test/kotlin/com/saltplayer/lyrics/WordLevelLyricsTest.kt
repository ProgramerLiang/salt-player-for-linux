// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Corripo
package com.saltplayer.lyrics

import com.saltplayer.domain.lyrics.LrcParser
import com.saltplayer.domain.lyrics.TtmlParser
import com.saltplayer.domain.lyrics.YrcParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Fixtures mirror the structure found in a real library but are written by hand here; no user
 * lyric file is committed.
 */
class WordLevelLyricsTest {

    private val ttml = """
        <?xml version="1.0" encoding="utf-8"?>
        <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata"
            xmlns:amll="http://www.example.com/ns/amll">
          <head>
            <metadata>
              <amll:meta key="musicName" value="心太软"/>
              <amll:meta key="artists" value="任贤齐"/>
            </metadata>
          </head>
          <body><div>
            <p begin="00:00:24.910" end="00:00:30.350" ttm:agent="v1">
              <span begin="00:00:24.910" end="00:00:25.270">你</span>
              <span begin="00:00:25.270" end="00:00:25.520">总</span>
              <span begin="00:00:25.520">是</span>
            </p>
            <p begin="00:00:24.910" end="00:00:30.350" ttm:agent="v2">
              <span begin="00:00:24.910" end="00:00:30.350">You are always</span>
            </p>
          </div></body>
        </tt>
    """.trimIndent()

    @Test
    fun `ttml keeps per-syllable timings and derives missing ends`() {
        val lyrics = TtmlParser.parse(ttml)
        assertTrue(lyrics.synced)
        assertEquals("TTML", lyrics.format)
        assertEquals("心太软", lyrics.title)
        assertEquals("任贤齐", lyrics.artist)

        val line = lyrics.lines.first()
        assertEquals(24_910L, line.timeMs)
        assertEquals(listOf("你", "总", "是"), line.words.map { it.text })
        assertEquals(24_910L, line.words[0].timeMs)
        // third span has no end attribute; it must inherit the line end
        assertEquals(30_350L, line.words.last().endMs)
        assertTrue(line.words[2].timeMs > line.words[1].timeMs)
    }

    @Test
    fun `ttml agent v2 lines become translations, not separate lines`() {
        val lyrics = TtmlParser.parse(ttml)
        assertEquals(1, lyrics.lines.size)
        assertEquals("你总是", lyrics.lines[0].text)
        assertEquals("You are always", lyrics.lines[0].translation)
    }

    @Test
    fun `ttml time formats`() {
        assertEquals(1_500L, TtmlParser.parseTime("00:01.500"))
        assertEquals(3_724_500L, TtmlParser.parseTime("01:02:04.500"))
        assertEquals(2_500L, TtmlParser.parseTime("2.5s"))
        assertEquals(900L, TtmlParser.parseTime("900ms"))
        assertEquals(null, TtmlParser.parseTime(""))
    }

    @Test
    fun `karaoke progress follows the sung syllables`() {
        val line = TtmlParser.parse(ttml).lines.first()
        assertEquals(0f, line.progress(24_000))
        val half = line.progress(25_400)
        assertTrue(half > 0.05f && half < 0.5f, "expected a partial fill, was $half")
        assertEquals(1f, line.progress(40_000))
    }

    private val yrcTimed = """
        心太软
        任贤齐
        小虫
        你总是心太软
        [24000,3000](24000,300,0)(24300,300,0)(24600,400,0)(25000,400,0)(25400,400,0)(25800,1200,0)
        You are always too soft-hearted
        [24000,3000](24000,300,0)(24300,300,0)(24600,400,0)(25000,400,0)(25400,400,0)(25800,1200,0)
    """.trimIndent()

    @Test
    fun `yrc timed variant produces words and pairs the translation`() {
        val lyrics = YrcParser.parse(yrcTimed)
        assertTrue(lyrics.synced)
        assertEquals("YRC", lyrics.format)
        assertEquals(1, lyrics.lines.size, "the second line shares the timestamp and is a translation")
        val line = lyrics.lines.first()
        assertEquals("你总是心太软", line.text)
        assertEquals("You are always too soft-hearted", line.translation)
        assertEquals(6, line.words.size)
        assertEquals(24_000L, line.words.first().timeMs)
        assertEquals(24_300L, line.words.first().endMs)
    }

    private val yrcTextOnly = """
        Love Yourself 
        Justin Bieber
        三畿道 X Gluneko
        For all the times that you rain on my parade
        一直以来 你就是淋湿我天堂的雨水
        And all the clubs you get in using my name
        你习惯打着我的名号混进每个夜店
    """.trimIndent()

    @Test
    fun `yrc text-only variant keeps translation pairs and stays unsynced`() {
        val lyrics = YrcParser.parse(yrcTextOnly)
        assertFalse(lyrics.synced)
        assertEquals("Justin Bieber", lyrics.artist)
        assertEquals(2, lyrics.lines.size)
        assertEquals("For all the times that you rain on my parade", lyrics.lines[0].text)
        assertEquals("一直以来 你就是淋湿我天堂的雨水", lyrics.lines[0].translation)
        assertFalse(lyrics.wordSynced)
    }

    @Test
    fun `translation detection needs a script change`() {
        assertTrue(YrcParser.looksLikeTranslation("Hello", "你好"))
        assertFalse(YrcParser.looksLikeTranslation("Hello", "Hello again"))
        assertFalse(YrcParser.looksLikeTranslation("你好", "再见"))
    }

    @Test
    fun `lrc ignores NetEase json credits and keeps synced lines`() {
        val raw = """
            {"t":0,"c":[{"tx":"作词: "},{"tx":"小虫"}]}
            {"t":750,"c":[{"tx":"作曲: "}]}
            [00:25.991]你总是心太软 心太软
            [00:31.917]独自一个人流泪到天亮
        """.trimIndent()
        val lyrics = LrcParser.parse(raw)
        assertTrue(lyrics.synced)
        assertEquals(2, lyrics.lines.size, "the JSON credit lines must not become lyric lines")
        assertEquals("你总是心太软 心太软", lyrics.lines[0].text)
        assertEquals(25_991L, lyrics.lines[0].timeMs)
    }

    @Test
    fun `enhanced lrc inline markers become words`() {
        val raw = "[00:10.000]<00:10.000>Hel<00:10.400>lo <00:10.800>world"
        val lyrics = LrcParser.parse(raw)
        val line = lyrics.lines.single()
        assertEquals(3, line.words.size)
        assertEquals(listOf("Hel", "lo ", "world"), line.words.map { it.text })
        assertEquals(10_000L, line.words[0].timeMs)
        assertEquals(10_400L, line.words[0].endMs)
        assertTrue(lyrics.wordSynced)
    }

    @Test
    fun `plain lrc still yields line-level lyrics without words`() {
        val lyrics = LrcParser.parse("[00:01.00]first\n[00:02.00]second")
        assertEquals(2, lyrics.lines.size)
        assertFalse(lyrics.wordSynced)
        assertEquals(-1, lyrics.indexAt(500))
        assertEquals(0, lyrics.indexAt(1_200))
        assertEquals(1, lyrics.indexAt(2_000))
    }
}
