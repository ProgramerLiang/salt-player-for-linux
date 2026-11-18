// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Corripo
package com.saltplayer.data.scan

import com.saltplayer.core.Log
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.TagField
import java.nio.file.Files
import java.nio.file.Path

/** Everything a scan needs to know about one audio file. */
data class RawTags(
    val title: String = "",
    val artist: String = "",
    val albumArtist: String = "",
    val album: String = "",
    val trackNo: Int = 0,
    val discNo: Int = 0,
    val year: Int = 0,
    val genre: String = "",
    val durationMs: Long = 0,
    val bitrateKbps: Int = 0,
    val sampleRateHz: Int = 0,
    val channels: Int = 0,
    val format: String = "",
    val coverBytes: ByteArray? = null,
    val coverMime: String? = null,
    val replayGainTrackDb: Double? = null,
    val replayGainAlbumDb: Double? = null,
    val lyrics: String? = null,
) {
    // ByteArray defeats the generated equals/hashCode; identity semantics are what we want here.
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}

/**
 * Reads embedded tags through jaudiotagger, which covers MP3, FLAC, OGG/Vorbis, MP4/M4A, WAV
 * and AIFF — the formats a local music library actually contains.
 *
 * Files jaudiotagger cannot parse are still added to the library using filename metadata; the
 * player backfills the duration from the audio backend the first time such a file is played.
 */
object TagReader {

    private val replayGainTrackKeys = listOf("REPLAYGAIN_TRACK_GAIN", "replaygain_track_gain")
    private val replayGainAlbumKeys = listOf("REPLAYGAIN_ALBUM_GAIN", "replaygain_album_gain")

    fun read(file: Path): RawTags {
        val sizeBytes = runCatching { Files.size(file) }.getOrDefault(0L)
        if (sizeBytes <= 0L) {
            Log.warn("TagReader", "empty or unreadable file: $file")
            return RawTags().withFileFallback(file, 0L)
        }
        val scaledBitrate = if (sizeBytes > 0) 0 else 0
        return runCatching { readWithTagger(file, sizeBytes) }
            .getOrElse {
                Log.warn("TagReader", "cannot read tags of $file: ${it.message}")
                RawTags(bitrateKbps = scaledBitrate).withFileFallback(file, sizeBytes)
            }
    }

    private fun readWithTagger(file: Path, sizeBytes: Long): RawTags {
        val audioFile = AudioFileIO.read(file.toFile())
        val header = audioFile.audioHeader
        val tag: Tag? = audioFile.tag
        val durationMs = runCatching { (header.preciseTrackLength * 1000).toLong() }
            .getOrElse { header.trackLength.toLong() * 1000 }
        val bitrate = runCatching { header.bitRateAsNumber.toInt() }.getOrDefault(0)
        val sampleRate = runCatching { header.sampleRateAsNumber }.getOrDefault(0)
        val channels = runCatching { header.channels.toInt() }.getOrDefault(0)
        val artwork = runCatching { tag?.firstArtwork }.getOrNull()
        val coverBytes = runCatching { artwork?.binaryData }.getOrNull()?.takeIf { it.isNotEmpty() }
        val coverMime = runCatching { artwork?.mimeType }.getOrNull()

        fun field(key: FieldKey): String = runCatching { tag?.getFirst(key) ?: "" }.getOrDefault("")

        return RawTags(
            title = field(FieldKey.TITLE),
            artist = field(FieldKey.ARTIST),
            albumArtist = field(FieldKey.ALBUM_ARTIST),
            album = field(FieldKey.ALBUM),
            trackNo = parseTrackNumber(field(FieldKey.TRACK)),
            discNo = parseTrackNumber(field(FieldKey.DISC_NO)),
            year = parseYear(field(FieldKey.YEAR)),
            genre = field(FieldKey.GENRE),
            durationMs = durationMs,
            bitrateKbps = bitrate,
            sampleRateHz = sampleRate,
            channels = channels,
            format = runCatching { header.format }.getOrDefault(""),
            coverBytes = coverBytes,
            coverMime = coverMime,
            replayGainTrackDb = readGain(tag, replayGainTrackKeys),
            replayGainAlbumDb = readGain(tag, replayGainAlbumKeys),
            lyrics = field(FieldKey.LYRICS).ifBlank { null },
        ).withFileFallback(file, sizeBytes)
    }

    private fun readGain(tag: Tag?, keys: List<String>): Double? {
        if (tag == null) return null
        for (key in keys) {
            parseGain(runCatching { tag.getFirst(key) }.getOrNull())?.let { return it }
        }
        // Vorbis comments / TXXX frames are not normalised by jaudiotagger, so scan raw fields.
        val fields: Iterator<TagField> = runCatching { tag.fields }.getOrNull() ?: return null
        while (fields.hasNext()) {
            val field = fields.next()
            val id = runCatching { field.id }.getOrNull() ?: continue
            if (keys.any { id.equals(it, ignoreCase = true) }) {
                parseGain(runCatching { field.toString() }.getOrNull())?.let { return it }
            }
        }
        return null
    }

    /** Parses `"-7.50 dB"`-style ReplayGain values (also accepts a comma decimal separator). */
    internal fun parseGain(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        val match = Regex("""[-+]?\d+(?:[.,]\d+)?""").find(raw) ?: return null
        return match.value.replace(',', '.').toDoubleOrNull()
    }

    /** Parses `"3/12"`, `"03"` or `"3"` into 3. */
    internal fun parseTrackNumber(raw: String?): Int {
        if (raw.isNullOrBlank()) return 0
        return Regex("""\d+""").find(raw)?.value?.toIntOrNull() ?: 0
    }

    /** Parses `"1997"`, `"1997-04-01"` into 1997. */
    internal fun parseYear(raw: String?): Int {
        if (raw.isNullOrBlank()) return 0
        val match = Regex("""(19|20)\d{2}""").find(raw) ?: return 0
        return match.value.toIntOrNull() ?: 0
    }

    private fun RawTags.withFileFallback(file: Path, sizeBytes: Long): RawTags {
        val name = file.fileName.toString()
        val extension = name.substringAfterLast('.', "").uppercase()
        val estimatedBitrate = if (bitrateKbps > 0 || durationMs <= 0 || sizeBytes <= 0) {
            bitrateKbps
        } else {
            (sizeBytes * 8 / durationMs).toInt()
        }
        return copy(
            title = title.ifBlank { name.substringBeforeLast('.') }.trim(),
            artist = artist.trim(),
            album = album.trim(),
            albumArtist = albumArtist.trim(),
            genre = genre.trim(),
            format = format.ifBlank { extension.ifBlank { "unknown" } },
            bitrateKbps = estimatedBitrate,
        )
    }
}
