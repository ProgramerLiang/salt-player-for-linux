// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Corripo
package com.saltplayer.data.lyrics

import com.saltplayer.core.Log
import com.saltplayer.domain.lyrics.LrcParser
import com.saltplayer.domain.lyrics.Lyrics
import com.saltplayer.domain.model.Track
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves lyrics for a track, in this order:
 * 1. a `.lrc` file next to the audio file (`song.lrc`, then `Artist - Title.lrc`);
 * 2. lyrics embedded in the file's tags (ID3 USLT / Vorbis LYRICS);
 * 3. nothing — the UI shows an explicit placeholder.
 *
 * Parsed results are cached per track id; the cache is small and bounded by the library size.
 */
class LyricsRepository {

    private val cache = ConcurrentHashMap<Long, Lyrics>()

    fun load(track: Track, force: Boolean = false): Lyrics {
        if (!force) {
            cache[track.id]?.let { return it }
        }
        val lyrics = readSidecar(track) ?: readEmbedded(track) ?: Lyrics.Empty
        cache[track.id] = lyrics
        return lyrics
    }

    fun invalidate(trackId: Long) {
        cache.remove(trackId)
    }

    private fun readSidecar(track: Track): Lyrics? {
        val audio = runCatching { Path.of(track.path) }.getOrNull() ?: return null
        val parent = audio.parent ?: return null
        val baseName = audio.fileName.toString().substringBeforeLast('.')
        val candidates = buildList {
            add(parent.resolve("$baseName.lrc"))
            add(parent.resolve("$baseName.LRC"))
            if (track.displayArtist.isNotBlank() && track.title.isNotBlank()) {
                add(parent.resolve("${track.displayArtist} - ${track.title}.lrc"))
            }
        }
        for (candidate in candidates) {
            if (!Files.isRegularFile(candidate)) continue
            val text = runCatching { Files.readString(candidate) }.getOrNull() ?: continue
            val parsed = LrcParser.parse(text, Lyrics.Source.Sidecar)
            if (!parsed.isEmpty) return parsed
        }
        return null
    }

    private fun readEmbedded(track: Track): Lyrics? {
        if (!track.hasEmbeddedLyrics) return null
        val text = runCatching {
            val audioFile = AudioFileIO.read(Path.of(track.path).toFile())
            audioFile.tag?.getFirst(FieldKey.LYRICS)
        }.getOrNull()
        if (text.isNullOrBlank()) return null
        val parsed = LrcParser.parse(text, Lyrics.Source.Embedded)
        return parsed.takeIf { !it.isEmpty }
    }

    /** Plain text of the lyrics, for the "copy lyrics" action and the About screen. */
    fun plainText(lyrics: Lyrics): String {
        if (lyrics.isEmpty) return ""
        return buildString {
            lyrics.lines.forEach { line ->
                if (lyrics.synced) {
                    val minutes = line.timeMs / 60_000
                    val seconds = (line.timeMs % 60_000) / 1_000
                    append("[%02d:%02d]".format(minutes, seconds))
                }
                append(line.text)
                line.translation?.let { append(" / ").append(it) }
                append('\n')
            }
        }
    }

    fun logMissing(track: Track) {
        Log.info("Lyrics", "no lyrics for ${track.displayTitle}")
    }
}
