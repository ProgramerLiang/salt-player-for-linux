// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Corripo
package com.saltplayer.domain.lyrics

/**
 * One timed syllable/word inside a line.
 *
 * [endMs] is derived from the next word (or the line end) when the source file omits it, so the
 * UI can always compute a karaoke fill without extra branches.
 */
data class LyricWord(
    val timeMs: Long,
    val endMs: Long,
    val text: String,
)

/** One timed lyric line, optionally carrying its translation and per-word timings. */
data class LyricLine(
    val timeMs: Long,
    val text: String,
    val translation: String? = null,
    val endMs: Long = 0L,
    /** Empty for line-level lyrics; populated for TTML / YRC / enhanced LRC. */
    val words: List<LyricWord> = emptyList(),
) {
    val hasWords: Boolean get() = words.isNotEmpty()

    /** Fraction (0..1) of this line that has already been "sung" at [positionMs]. */
    fun progress(positionMs: Long): Float {
        if (!hasWords) {
            val end = if (endMs > timeMs) endMs else timeMs + 1
            return ((positionMs - timeMs).toFloat() / (end - timeMs)).coerceIn(0f, 1f)
        }
        val total = words.sumOf { (it.endMs - it.timeMs).coerceAtLeast(1L) }
        if (total <= 0) return 0f
        var elapsed = 0L
        for (word in words) {
            when {
                positionMs >= word.endMs -> elapsed += (word.endMs - word.timeMs).coerceAtLeast(1L)
                positionMs <= word.timeMs -> break
                else -> {
                    elapsed += (positionMs - word.timeMs)
                    break
                }
            }
        }
        return (elapsed.toFloat() / total).coerceIn(0f, 1f)
    }
}

/**
 * Parsed lyrics. [synced] is false for plain-text lyrics; [wordSynced] additionally says whether
 * per-word timings exist, which drives the karaoke highlight.
 */
data class Lyrics(
    val lines: List<LyricLine>,
    val synced: Boolean,
    val offsetMs: Long = 0,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val by: String? = null,
    val source: Source = Source.None,
    val format: String = "",
) {
    enum class Source { Embedded, Sidecar, None }

    val isEmpty: Boolean get() = lines.isEmpty()

    val wordSynced: Boolean get() = lines.any { it.hasWords }

    /** Index of the line active at [positionMs]; -1 before the first line. */
    fun indexAt(positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        val target = positionMs + offsetMs
        var low = 0
        var high = lines.lastIndex
        var result = -1
        while (low <= high) {
            val mid = (low + high) / 2
            if (lines[mid].timeMs <= target) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }

    fun lineAt(positionMs: Long): LyricLine? = lines.getOrNull(indexAt(positionMs))

    fun withOffset(offsetMs: Long): Lyrics = copy(offsetMs = offsetMs)

    companion object {
        val Empty = Lyrics(emptyList(), synced = false)
    }
}
