package com.saltplayer.scanner

import com.saltplayer.model.CoverArt
import com.saltplayer.model.Song
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.*
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile

object LibraryScanner {

    private val SUPPORTED_EXTENSIONS = setOf(
        "mp3", "flac", "ogg", "wav", "m4a", "aac",
        "wma", "aiff", "aif", "opus", "ape", "wv"
    )

    fun scan(directory: File): List<Song> {
        if (!directory.isDirectory) return emptyList()
        val songs = mutableListOf<Song>()
        Files.walk(directory.toPath())
            .filter { it.isRegularFile() }
            .filter { it.extension.lowercase(Locale.ROOT) in SUPPORTED_EXTENSIONS }
            .forEach { path -> extractMetadata(path)?.let { songs.add(it) } }
        songs.sortBy { it.title.lowercase() }
        return songs
    }

    private fun extractMetadata(path: Path): Song? {
        return try {
            val file = path.toFile()
            val id = hashPath(path.toString())
            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tagOrCreateDefault
            val header = audioFile.audioHeader

            val title = tag.getFirst(FieldKey.TITLE)
                .takeIf { it.isNotBlank() } ?: file.nameWithoutExtension
            val artist = tag.getFirst(FieldKey.ARTIST)
            val album = tag.getFirst(FieldKey.ALBUM)
            val duration = header.trackLength.toDouble()

            // Extract embedded cover art
            val coverArt = try {
                val artwork = tag.firstArtwork
                if (artwork != null) {
                    CoverArt(
                        mimeType = artwork.mimeType,
                        data = Base64.getEncoder().encodeToString(artwork.binaryData)
                    )
                } else null
            } catch (_: Exception) { null }

            Song(
                id = id,
                title = title,
                artist = artist,
                album = album,
                duration = duration,
                filePath = file.absolutePath,
                coverArt = coverArt
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun hashPath(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        return md.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
