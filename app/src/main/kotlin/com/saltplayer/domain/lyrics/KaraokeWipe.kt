// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Corripo
package com.saltplayer.domain.lyrics

/**
 * Time → horizontal-progress mapping for the karaoke wipe.
 *
 * The renderer measures each word's pixel span and asks this object *which* word is being sung and
 * *how far into it* the playhead is. Keeping the maths here (and not in the composable) makes the
 * wipe monotonic, boundary-correct and testable without a text layout.
 */
object KaraokeWipe {

    /** Which word is active and the 0..1 progress inside it. */
    data class Slice(val index: Int, val fraction: Float)

    /**
     * Progress inside the active word. Returns `null` when [positionMs] is before the line starts;
     * afterwards the fraction is clamped so a late or early clock can never rewind the wipe.
     */
    fun sliceAt(words: List<LyricWord>, positionMs: Long): Slice? {
        if (words.isEmpty()) return null
        val first = words.first()
        if (positionMs < first.timeMs) return null
        for (index in words.indices) {
            val word = words[index]
            val end = wordEnd(words, index)
            if (positionMs < end) {
                val span = (end - word.timeMs).coerceAtLeast(1L)
                val fraction = ((positionMs - word.timeMs).toFloat() / span).coerceIn(0f, 1f)
                return Slice(index, fraction)
            }
        }
        return Slice(words.lastIndex, 1f)
    }

    /**
     * Word end time: the explicit end, or the next word's start so a source file that only carries
     * start times still produces continuous progress.
     */
    fun wordEnd(words: List<LyricWord>, index: Int): Long {
        val word = words[index]
        if (word.endMs > word.timeMs) return word.endMs
        val next = words.getOrNull(index + 1)?.timeMs
        return next ?: (word.timeMs + 200)
    }

    /**
     * Progress across the whole line, weighted by character count *and* by the time each word
     * occupies. Used for the line-level fallback (plain LRC) and for asserting monotonicity.
     */
    fun fractionOfLine(words: List<LyricWord>, positionMs: Long): Float {
        if (words.isEmpty()) return 0f
        if (positionMs <= words.first().timeMs) return 0f
        var travelled = 0f
        var total = 0f
        for (index in words.indices) {
            val word = words[index]
            val end = wordEnd(words, index)
            val span = (end - word.timeMs).coerceAtLeast(1L).toFloat()
            total += span
            travelled += when {
                positionMs >= end -> span
                positionMs <= word.timeMs -> 0f
                else -> (positionMs - word.timeMs).toFloat()
            }
        }
        return if (total <= 0f) 0f else (travelled / total).coerceIn(0f, 1f)
    }

    /** Line-level fallback: linear wipe over the line's own duration. */
    fun fractionOfLine(line: LyricLine, positionMs: Long): Float {
        if (line.hasWords) return fractionOfLine(line.words, positionMs)
        val start = line.timeMs
        val end = if (line.endMs > start) line.endMs else start + 1
        return ((positionMs - start).toFloat() / (end - start)).coerceIn(0f, 1f)
    }
}
