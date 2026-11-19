// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Corripo
package com.saltplayer.domain.lyrics

import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Parser for AMLL-style TTML lyrics.
 *
 * Structure seen in real files: `<p begin end ttm:agent="v1">` containing one `<span begin end>`
 * per syllable, plus `<amll:meta key=... value=...>` entries in the head. When several agents
 * exist, `v1` carries the original text and any further agent at the same timestamp is treated
 * as the translation of that line.
 */
object TtmlParser {

    fun parse(raw: String, source: Lyrics.Source = Lyrics.Source.Sidecar): Lyrics {
        val document = runCatching {
            DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                // Lyrics are local files; never resolve external entities.
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            }.newDocumentBuilder().parse(ByteArrayInputStream(raw.toByteArray(Charsets.UTF_8)))
        }.getOrNull() ?: return Lyrics.Empty

        val meta = mutableMapOf<String, String>()
        val paragraphs = document.getElementsByTagNameNS("*", "p")
        val lines = mutableListOf<LyricLine>()

        for (index in 0 until paragraphs.length) {
            val element = paragraphs.item(index) as? Element ?: continue
            val beginMs = parseTime(element.getAttribute("begin")) ?: continue
            val endMs = parseTime(element.getAttribute("end")) ?: 0L
            val agent = element.getAttribute("ttm:agent").ifBlank {
                element.getAttributeNS("http://www.w3.org/ns/ttml#metadata", "agent")
            }
            val words = readWords(element, beginMs, endMs)
            val text = words.joinToString("") { it.text }.ifBlank {
                element.textContent.orEmpty().trim()
            }
            if (text.isEmpty()) continue
            lines += LyricLine(
                timeMs = beginMs,
                text = text,
                translation = null,
                endMs = if (endMs > beginMs) endMs else beginMs + words.sumOf { it.endMs - it.timeMs },
                words = words,
                // remember the agent for the merge step below
            ).let { if (agent.isBlank() || agent == "v1") it else it.copy(text = "", translation = text) }
        }

        val metas = document.getElementsByTagNameNS("*", "meta")
        for (index in 0 until metas.length) {
            val element = metas.item(index) as? Element ?: continue
            val key = element.getAttribute("key")
            val value = element.getAttribute("value")
            if (key.isNotBlank() && value.isNotBlank()) meta[key] = value
        }

        val merged = mergeTranslations(lines)
        return Lyrics(
            lines = merged,
            synced = merged.isNotEmpty(),
            title = meta["musicName"],
            artist = meta["artists"],
            album = meta["album"],
            by = meta["ncmMusicId"]?.let { "amll #$it" },
            source = source,
            format = "TTML",
        )
    }

    /** Reads `<span>` children as words; a paragraph without spans becomes one line-level word. */
    private fun readWords(paragraph: Element, lineStart: Long, lineEnd: Long): List<LyricWord> {
        val spans = paragraph.getElementsByTagNameNS("*", "span")
        val raw = mutableListOf<LyricWord>()
        for (index in 0 until spans.length) {
            val span = spans.item(index) as? Element ?: continue
            val text = span.textContent.orEmpty()
            if (text.isEmpty()) continue
            val start = parseTime(span.getAttribute("begin")) ?: lineStart
            val end = parseTime(span.getAttribute("end"))
            raw += LyricWord(start, end ?: start, text)
        }
        if (raw.isEmpty()) return emptyList()
        // Fill missing ends from the next word, then from the line end.
        return raw.mapIndexed { index, word ->
            val next = raw.getOrNull(index + 1)?.timeMs
            val resolvedEnd = when {
                word.endMs > word.timeMs -> word.endMs
                next != null && next > word.timeMs -> next
                lineEnd > word.timeMs -> lineEnd
                else -> word.timeMs + 120
            }
            word.copy(endMs = resolvedEnd)
        }
    }

    /**
     * Lines whose text was left empty hold the translation of the closest earlier line that
     * starts at (about) the same time.
     */
    private fun mergeTranslations(lines: List<LyricLine>): List<LyricLine> {
        val originals = lines.filter { it.text.isNotEmpty() }.toMutableList()
        val translations = lines.filter { it.text.isEmpty() && it.translation != null }
        for (translation in translations) {
            val target = originals.indexOfFirst { kotlin.math.abs(it.timeMs - translation.timeMs) <= 60 }
            if (target >= 0) {
                originals[target] = originals[target].copy(translation = translation.translation)
            }
        }
        return originals.sortedBy { it.timeMs }
    }

    /** Accepts `hh:mm:ss.mmm`, `mm:ss.mmm`, `12.5s`, `900ms`, `12.5` (seconds) and raw millis. */
    fun parseTime(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        val text = value.trim()
        if (text.endsWith("ms")) return text.dropLast(2).toDoubleOrNull()?.toLong()
        if (text.endsWith("s")) return text.dropLast(1).toDoubleOrNull()?.let { (it * 1000).toLong() }
        if (text.contains(":")) {
            val parts = text.split(":")
            val seconds = parts.last().toDoubleOrNull() ?: return null
            var total = (seconds * 1000).toLong()
            val minutes = parts.getOrNull(parts.size - 2)?.toLongOrNull() ?: 0L
            total += minutes * 60_000
            if (parts.size >= 3) {
                total += (parts[0].toLongOrNull() ?: 0L) * 3_600_000
            }
            return total
        }
        return text.toDoubleOrNull()?.let { (it * 1000).toLong() }
    }
}
