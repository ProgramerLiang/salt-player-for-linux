// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Corripo
package com.saltplayer.data.media

import com.saltplayer.core.Log
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import javax.imageio.ImageIO

/**
 * Stores embedded cover art on disk (cache dir) and produces a thumbnail for grid/list views.
 *
 * Covers are keyed by track id, so a rescan of the same file reuses the existing image and an
 * updated file replaces it.
 */
class CoverStore(private val coverDir: Path) {

    init {
        runCatching { Files.createDirectories(coverDir) }
    }

    data class StoredCover(val fullPath: Path, val thumbnailPath: Path?)

    fun store(trackId: Long, bytes: ByteArray, mimeType: String?): StoredCover? {
        if (bytes.isEmpty()) return null
        val extension = extensionFor(mimeType, bytes)
        val name = "cover-$trackId"
        val full = coverDir.resolve("$name.$extension")
        return runCatching {
            Files.write(full, bytes)
            val thumb = runCatching { writeThumbnail(full, name) }.getOrNull()
            StoredCover(full, thumb)
        }.onFailure { Log.warn("CoverStore", "failed to store cover for track $trackId", it) }.getOrNull()
    }

    fun fullCover(trackId: Long): Path? = findExisting("cover-$trackId")

    fun thumbnail(trackId: Long): Path? =
        findExisting("cover-$trackId-thumb") ?: fullCover(trackId)

    /** Looks up a sidecar image (`cover.jpg`, `folder.png`, …) next to the audio file. */
    fun sidecarFor(trackPath: String): Path? {
        val parent = runCatching { Path.of(trackPath).parent }.getOrNull() ?: return null
        for (candidate in SIDECAR_NAMES) {
            val file = parent.resolve(candidate)
            if (Files.isRegularFile(file)) return file
        }
        return null
    }

    fun delete(trackId: Long) {
        findExisting("cover-$trackId")?.let { runCatching { Files.deleteIfExists(it) } }
        findExisting("cover-$trackId-thumb")?.let { runCatching { Files.deleteIfExists(it) } }
    }

    private fun findExisting(baseName: String): Path? {
        if (!Files.isDirectory(coverDir)) return null
        return runCatching {
            Files.list(coverDir).use { stream ->
                stream.filter { it.fileName.toString().substringBeforeLast('.').substringBeforeLast('-') ==
                    baseName.substringBeforeLast('-') || it.fileName.toString().startsWith("$baseName.") }
                    .findFirst().orElse(null)
            }
        }.getOrNull()
    }

    private fun writeThumbnail(source: Path, name: String): Path? {
        val image = ImageIO.read(source.toFile()) ?: return null
        val scale = minOf(THUMB_SIZE.toDouble() / image.width, THUMB_SIZE.toDouble() / image.height)
        val width = maxOf((image.width * scale).toInt(), 1)
        val height = maxOf((image.height * scale).toInt(), 1)
        val scaled = image.getScaledInstance(width, height, java.awt.Image.SCALE_SMOOTH)
        val buffered = java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB)
        val graphics = buffered.createGraphics()
        graphics.drawImage(scaled, 0, 0, null)
        graphics.dispose()
        val target = coverDir.resolve("$name-thumb.jpg")
        ImageIO.write(buffered, "jpg", target.toFile())
        return target
    }

    private fun extensionFor(mimeType: String?, bytes: ByteArray): String = when {
        mimeType?.contains("png", true) == true -> "png"
        mimeType?.contains("webp", true) == true -> "webp"
        bytes.size > 4 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() -> "png"
        else -> "jpg"
    }

    companion object {
        private const val THUMB_SIZE = 256
        private val SIDECAR_NAMES = listOf("cover.jpg", "cover.png", "folder.jpg", "folder.png", "front.jpg")
    }
}
