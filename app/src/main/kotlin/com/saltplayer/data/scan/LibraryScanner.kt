// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Corripo
package com.saltplayer.data.scan

import com.saltplayer.core.Log
import com.saltplayer.data.db.LibraryDatabase
import com.saltplayer.data.media.CoverStore
import com.saltplayer.domain.model.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/**
 * Walks the configured music folders, reads tags and keeps the SQLite library in sync.
 *
 * Scanning is incremental by default: files whose (size, mtime) is unchanged are skipped, so
 * start-up is fast while a manual "rescan everything" stays available.
 */
class LibraryScanner(
    private val db: LibraryDatabase,
    private val coverStore: CoverStore,
    private val tagReader: (Path) -> RawTags = { TagReader.read(it) },
) {

    data class Progress(
        val scanned: Int,
        val total: Int,
        val currentFile: String,
        val added: Int,
        val updated: Int,
        val removed: Int,
    )

    data class Result(
        val added: Int,
        val updated: Int,
        val removed: Int,
        val total: Int,
        val cancelled: Boolean,
    )

    /** Collects every supported audio file under [roots]. */
    fun collectFiles(roots: List<String>, followSymlinks: Boolean = false): List<Path> {
        val result = mutableListOf<Path>()
        for (root in roots.distinct()) {
            val rootPath = runCatching { Path.of(root) }.getOrNull() ?: continue
            if (!Files.isDirectory(rootPath)) continue
            val visitor = object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    val extension = file.fileName.toString().substringAfterLast('.', "").lowercase()
                    if (extension in SUPPORTED_EXTENSIONS) result.add(file)
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, exc: java.io.IOException): FileVisitResult {
                    Log.warn("Scanner", "cannot access $file: ${exc.message}")
                    return FileVisitResult.CONTINUE
                }

                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (!followSymlinks && Files.isSymbolicLink(dir)) return FileVisitResult.SKIP_SUBTREE
                    val name = dir.fileName?.toString().orEmpty()
                    if (name.startsWith(".") && dir != rootPath) return FileVisitResult.SKIP_SUBTREE
                    return FileVisitResult.CONTINUE
                }
            }
            runCatching { Files.walkFileTree(rootPath, visitor) }
                .onFailure { Log.warn("Scanner", "walking $root failed", it) }
        }
        return result.sorted()
    }

    /**
     * Scans [roots]. When [fullRescan] is false only new/changed files are re-read.
     * [onProgress] is called from the scanning thread; cancellation is honoured between files.
     */
    suspend fun scan(
        roots: List<String>,
        fullRescan: Boolean = false,
        followSymlinks: Boolean = false,
        onProgress: (Progress) -> Unit = {},
    ): Result {
        val files = collectFiles(roots, followSymlinks)
        val known = db.fileSignatures()
        val scannedPaths = files.map { it.toString() }.toSet()
        val stalePaths = known.keys.filter { it !in scannedPaths }
        val toRead = if (fullRescan) {
            files
        } else {
            files.filter { file ->
                val signature = known[file.toString()]
                signature == null || signature.sizeBytes != sizeOf(file) || signature.mtimeMs != mtimeOf(file)
            }
        }
        Log.info("Scanner", "found ${files.size} audio files, re-reading ${toRead.size}, dropping ${stalePaths.size}")

        var added = 0
        var updated = 0
        var removed = 0
        val batch = mutableListOf<Pair<Track, RawTags>>()

        suspend fun flush() {
            if (batch.isEmpty()) return
            val ids = db.upsertTracks(batch.map { it.first })
            for ((track, raw) in batch) {
                val id = ids[track.path] ?: continue
                val cover = raw.coverBytes
                if (cover != null) coverStore.store(id, cover, raw.coverMime)
            }
            Log.info("Scanner", "persisted ${batch.size} tracks")
            batch.clear()
        }

        for ((index, file) in toRead.withIndex()) {
            currentCoroutineContext().ensureActive()
            onProgress(Progress(index + 1, toRead.size, file.toString(), added, updated, removed))
            val raw = try {
                tagReader(file)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                Log.warn("Scanner", "tag read failed for $file", error)
                RawTags()
            }
            val path = file.toString()
            if (known.containsKey(path)) updated++ else added++
            batch.add(
                Track(
                    id = 0,
                    path = path,
                    title = raw.title,
                    artist = raw.artist,
                    albumArtist = raw.albumArtist,
                    album = raw.album,
                    trackNo = raw.trackNo,
                    discNo = raw.discNo,
                    year = raw.year,
                    genre = raw.genre,
                    durationMs = raw.durationMs,
                    bitrateKbps = raw.bitrateKbps,
                    sampleRateHz = raw.sampleRateHz,
                    channels = raw.channels,
                    format = raw.format,
                    sizeBytes = sizeOf(file),
                    mtimeMs = mtimeOf(file),
                    addedAtMs = 0,
                    hasCover = raw.coverBytes != null || coverStore.sidecarFor(path) != null,
                    replayGainTrackDb = raw.replayGainTrackDb,
                    replayGainAlbumDb = raw.replayGainAlbumDb,
                    hasEmbeddedLyrics = !raw.lyrics.isNullOrBlank(),
                ) to raw
            )
            if (batch.size >= BATCH_SIZE) flush()
        }
        flush()
        if (stalePaths.isNotEmpty()) removed = db.deleteTracks(stalePaths)

        return Result(
            added = added,
            updated = updated,
            removed = removed,
            total = db.trackCount(),
            cancelled = false,
        )
    }

    private fun sizeOf(file: Path): Long = runCatching { Files.size(file) }.getOrDefault(0L)

    private fun mtimeOf(file: Path): Long =
        runCatching { Files.getLastModifiedTime(file).toMillis() }.getOrDefault(0L)

    companion object {
        private const val BATCH_SIZE = 64

        val SUPPORTED_EXTENSIONS = setOf(
            "mp3", "flac", "ogg", "oga", "opus", "m4a", "aac", "wav", "aiff", "aif",
            "wma", "ape", "mpc", "wv", "mka", "dsf", "dff",
        )
    }
}
