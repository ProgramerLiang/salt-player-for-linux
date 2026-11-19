// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Corripo
package com.saltplayer.domain.lyrics

/**
 * Parser for YRC (NetEase "yrc") lyrics.
 *
 * Two variants exist in the wild and both are supported:
 *
 * 1. **Timed**: a text line followed by `[start,duration](wStart,wDur,0)(wStart,wDur,0)…`, where
 *    every bracket group is one syllable. This yields per-word timings.
 * 2. **Text only** (what the user's five local files contain): three metadata lines
 *    (title / artist / author) followed by alternating original and translated lines, with no
 *    timestamps at all. Those become unsynchronised lines that keep their translation.
 */
object YrcParser {

    private val timingLine = Regex("""^\[(\d+),(\d+)]""")
    private val wordGroup = Regex("""\((\d+),(\d+)(?:,\d+)?\)""")

    fun parse(raw: String, source: Lyrics.Source = Lyrics.Source.Sidecar): Lyrics {
        val lines = raw.removePrefix("\uFEFF").lines().map { it.trimEnd() }
            .filter { it.isNotBlank() }
        if (lines.isEmpty()) return Lyrics.Empty

        val hasTiming = lines.any { timingLine.containsMatchIn(it) }
        return if (hasTiming) parseTimed(lines, source) else parseTextOnly(lines, source)
    }

    private fun parseTimed(lines: List<String>, source: Lyrics.Source): Lyrics {
        val parsed = mutableListOf<LyricLine>()
        var pendingText: String? = null
        for (line in lines) {
            val match = timingLine.find(line)
            if (match == null) {
                // Metadata lines before the first timed pair are dropped unless they look like text.
                pendingText = line
                continue
            }
            val text = pendingText ?: line.substring(match.range.last + 1).trim()
            pendingText = null
            val start = match.groupValues[1].toLongOrNull() ?: continue
            val duration = match.groupValues[2].toLongOrNull() ?: 0L
            val words = wordGroup.findAll(line).map { group ->
                val wordStart = group.groupValues[1].toLongOrNull() ?: start
                val wordDuration = group.groupValues[2].toLongOrNull() ?: 0L
                LyricWord(wordStart, wordStart + wordDuration.coerceAtLeast(1), "")
            }.toList()

            val withText = if (words.isEmpty()) {
                emptyList()
            } else {
                // Distribute the characters over the syllable slots.
                val characters = text.toList()
                val perSlot = if (words.size == characters.size) 1 else 0
                if (perSlot == 1) {
                    words.mapIndexed { index, word -> word.copy(text = characters[index].toString()) }
                } else {
                    words.mapIndexed { index, word ->
                        if (index == 0) word.copy(text = text) else word.copy(text = "")
                    }
                }
            }
            parsed += LyricLine(
                timeMs = start,
                text = text,
                endMs = start + duration,
                words = withText,
            )
        }

        // Pair consecutive lines that share a timestamp window: the second is the translation.
        val merged = mutableListOf<LyricLine>()
        for (line in parsed) {
            val last = merged.lastOrNull()
            if (last != null && last.translation == null && line.timeMs == last.timeMs && line.text != last.text) {
                merged[merged.lastIndex] = last.copy(translation = line.text)
            } else {
                merged += line
            }
        }
        return Lyrics(
            lines = merged.sortedBy { it.timeMs },
            synced = merged.isNotEmpty(),
            source = source,
            format = "YRC",
        )
    }

    private fun parseTextOnly(lines: List<String>, source: Lyrics.Source): Lyrics {
        val body = lines.drop(3) // title / artist / author
        val result = mutableListOf<LyricLine>()
        var index = 0
        while (index < body.size) {
            val original = body[index]
            val next = body.getOrNull(index + 1)
            val paired = next != null && looksLikeTranslation(original, next)
            result += LyricLine(
                timeMs = 0L,
                text = original,
                translation = if (paired) next else null,
            )
            index += if (paired) 2 else 1
        }
        return Lyrics(
            lines = result,
            synced = false,
            title = lines.getOrNull(0),
            artist = lines.getOrNull(1),
            by = lines.getOrNull(2),
            source = source,
            format = "YRC",
        )
    }

    /**
     * Decides whether [candidate] is the translation of [original].
     *
     * The two lines must differ, and their scripts must differ as well: a CJK line following a
     * latin line (or the reverse) is the layout NetEase produces for bilingual lyrics. Lines that
     * share a script are treated as separate lyric lines.
     */
    internal fun looksLikeTranslation(original: String, candidate: String): Boolean {
        if (original.isBlank() || candidate.isBlank()) return false
        if (original.equals(candidate, ignoreCase = true)) return false
        return containsCjk(original) != containsCjk(candidate)
    }

    private fun containsCjk(text: String): Boolean = text.any { it.code in 0x3400..0x9FFF }
}
