// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Corripo
package com.saltplayer.domain.lyrics

/**
 * Parser for `.lrc` lyrics (also used for embedded lyrics payloads, which follow the same
 * format).
 *
 * Supported syntax:
 * * `[mm:ss]`, `[mm:ss.xx]`, `[mm:ss.xxx]` timestamps, several per line;
 * * `[offset:±ms]` global shift;
 * * id tags `[ti:]`, `[ar:]`, `[al:]`, `[by:]`;
 * * bilingual lyrics: a second line carrying the *same* timestamp becomes the translation
 *   of the first one instead of a duplicate line;
 * * untimed text is returned as unsynchronised (static) lyrics.
 */
object LrcParser {

    private val timestamp = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val metadata = Regex("""^\[(ti|ar|al|by|offset|length|re|ve):\s*(.*?)]$""", RegexOption.IGNORE_CASE)
    private val bom = '\uFEFF'

    fun parse(raw: String, source: Lyrics.Source = Lyrics.Source.Sidecar): Lyrics {
        val text = raw.removePrefix(bom.toString())
        var offsetMs = 0L
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var by: String? = null
        val timed = mutableListOf<LyricLine>()
        val plain = mutableListOf<String>()

        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) {
                if (plain.isNotEmpty()) plain.add("")
                continue
            }
            val meta = metadata.matchEntire(line)
            if (meta != null) {
                val key = meta.groupValues[1].lowercase()
                val value = meta.groupValues[2].trim()
                when (key) {
                    "ti" -> if (value.isNotEmpty()) title = value
                    "ar" -> if (value.isNotEmpty()) artist = value
                    "al" -> if (value.isNotEmpty()) album = value
                    "by" -> if (value.isNotEmpty()) by = value
                    "offset" -> value.trimStart('+').toLongOrNull()?.let { offsetMs = it }
                }
                continue
            }

            val stamps = timestamp.findAll(line).toList()
            if (stamps.isEmpty()) {
                plain.add(line)
                continue
            }
            val content = line.substring(stamps.last().range.last + 1).trim()
            if (content.isEmpty()) continue
            for (stamp in stamps) {
                val minutes = stamp.groupValues[1].toLongOrNull() ?: continue
                val seconds = stamp.groupValues[2].toLongOrNull() ?: continue
                val fractionRaw = stamp.groupValues[3]
                val fractionMs = when (fractionRaw.length) {
                    0 -> 0L
                    1 -> fractionRaw.toLong() * 100
                    2 -> fractionRaw.toLong() * 10
                    else -> fractionRaw.take(3).toLong()
                }
                timed.add(LyricLine(minutes * 60_000 + seconds * 1_000 + fractionMs, content))
            }
        }

        if (timed.isEmpty()) {
            // No timestamps: static lyrics, keep paragraph breaks but drop trailing blanks.
            val lines = plain.map { it.trim() }.dropWhile { it.isEmpty() }.dropLastWhile { it.isEmpty() }
            return Lyrics(
                lines = lines.map { LyricLine(0, it) },
                synced = false,
                offsetMs = offsetMs,
                title = title,
                artist = artist,
                album = album,
                by = by,
                source = source,
            )
        }

        val merged = ArrayList<LyricLine>(timed.size)
        for (line in timed) {
            val existing = merged.indexOfFirst { it.timeMs == line.timeMs }
            if (existing >= 0 && merged[existing].text != line.text) {
                merged[existing] = merged[existing].copy(translation = line.text)
            } else if (existing < 0) {
                merged.add(line)
            }
        }
        merged.sortBy { it.timeMs }

        return Lyrics(
            lines = merged,
            synced = true,
            offsetMs = offsetMs,
            title = title,
            artist = artist,
            album = album,
            by = by,
            source = source,
        )
    }

    /** Serialises lyrics back to LRC, used for the "export lyrics" action. */
    fun write(lyrics: Lyrics): String = buildString {
        lyrics.title?.let { append("[ti:").append(it).append("]\n") }
        lyrics.artist?.let { append("[ar:").append(it).append("]\n") }
        lyrics.album?.let { append("[al:").append(it).append("]\n") }
        lyrics.by?.let { append("[by:").append(it).append("]\n") }
        if (lyrics.offsetMs != 0L) append("[offset:").append(lyrics.offsetMs).append("]\n")
        for (line in lyrics.lines) {
            if (lyrics.synced) {
                val minutes = line.timeMs / 60_000
                val seconds = (line.timeMs % 60_000) / 1_000
                val millis = line.timeMs % 1_000
                append('[').append(minutes.toString().padStart(2, '0'))
                append(':').append(seconds.toString().padStart(2, '0'))
                append('.').append(millis.toString().padStart(3, '0'))
                append(']').append(line.text).append('\n')
                line.translation?.let { append('[').append(minutes.toString().padStart(2, '0')).append(':') }
                if (line.translation != null) {
                    val t = line.translation!!
                    val m = line.timeMs / 60_000
                    val s = (line.timeMs % 60_000) / 1_000
                    val ms = line.timeMs % 1_000
                    append('[').append(m.toString().padStart(2, '0')).append(':')
                        .append(s.toString().padStart(2, '0')).append('.')
                        .append(ms.toString().padStart(3, '0')).append(']').append(t).append('\n')
                }
            } else {
                append(line.text).append('\n')
            }
        }
    }
}
