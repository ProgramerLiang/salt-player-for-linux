// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Corripo
package com.saltplayer.domain.lyrics

/** One timed lyric line, optionally carrying its translation (bilingual lyrics). */
data class LyricLine(
    val timeMs: Long,
    val text: String,
    val translation: String? = null,
)

/**
 * Parsed lyrics. [synced] is false for plain-text lyrics where [LyricLine.timeMs] is 0 for
 * every line and the UI shows them as a static block instead of a synced, scrolling view.
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
) {
    enum class Source { Embedded, Sidecar, None }

    val isEmpty: Boolean get() = lines.isEmpty()

    /** Index of the line that is active at [positionMs]; -1 when the first line is still ahead. */
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

    /** Returns a copy with a shifted offset (used by the manual lyric calibration UI). */
    fun withOffset(offsetMs: Long): Lyrics = copy(offsetMs = offsetMs)

    companion object {
        val Empty = Lyrics(emptyList(), synced = false)
    }
}
