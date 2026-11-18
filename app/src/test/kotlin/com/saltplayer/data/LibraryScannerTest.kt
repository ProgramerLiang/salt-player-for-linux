// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Corripo
package com.saltplayer.data

import com.saltplayer.data.db.LibraryDatabase
import com.saltplayer.data.media.CoverStore
import com.saltplayer.data.scan.LibraryScanner
import com.saltplayer.data.scan.RawTags
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LibraryScannerTest {

    private val root = Files.createTempDirectory("salt-scan")
    private val db = LibraryDatabase(root.resolve("library.db"))
    private val coverStore = CoverStore(root.resolve("covers"))

    @AfterTest
    fun tearDown() = db.close()

    private fun speckleFile(relative: String) {
        val file = root.resolve(relative)
        Files.createDirectories(file.parent)
        Files.write(file, ByteArray(16) { 1 })
    }

    private fun scanner(tags: (java.nio.file.Path) -> RawTags = { path ->
        RawTags(
            title = path.fileName.toString().substringBeforeLast('.'),
            artist = "Artist",
            album = "Album",
            durationMs = 1000,
            format = "MP3",
        )
    }) = LibraryScanner(db, coverStore, tags)

    @Test
    fun `first scan adds files, second scan is incremental`() = runBlocking {
        speckleFile("album/one.mp3")
        speckleFile("album/two.flac")
        speckleFile("notes.txt")
        val scanner = scanner()
        val progress = mutableListOf<Int>()

        val first = scanner.scan(listOf(root.toString()), onProgress = { progress.add(it.scanned) })
        assertEquals(2, first.added)
        assertEquals(2, db.trackCount())
        assertEquals(listOf(1, 2), progress)

        val readCount = java.util.concurrent.atomic.AtomicInteger()
        val second = scanner(
            tags = { path ->
                readCount.incrementAndGet()
                RawTags(title = path.fileName.toString(), format = "MP3", durationMs = 1000)
            }
        ).scan(listOf(root.toString()))
        assertEquals(0, second.added)
        assertEquals(0, second.updated)
        assertEquals(0, readCount.get(), "an unchanged library must not be re-read")
    }

    @Test
    fun `changed files are re-read and vanished files are removed`() = runBlocking {
        speckleFile("a.mp3")
        speckleFile("b.mp3")
        val scanner = scanner()
        scanner.scan(listOf(root.toString()))
        assertEquals(2, db.trackCount())

        Files.write(root.resolve("a.mp3"), ByteArray(32) { 2 })
        Files.delete(root.resolve("b.mp3"))
        val result = scanner.scan(listOf(root.toString()))
        assertEquals(1, result.updated)
        assertEquals(1, result.removed)
        assertEquals(1, db.trackCount())
    }

    @Test
    fun `full rescan re-reads everything`() = runBlocking {
        speckleFile("a.mp3")
        val scanner = scanner()
        scanner.scan(listOf(root.toString()))
        val reads = java.util.concurrent.atomic.AtomicInteger()
        scanner(tags = { reads.incrementAndGet(); RawTags(title = "x", durationMs = 1) })
            .scan(listOf(root.toString()), fullRescan = true)
        assertTrue(reads.get() >= 1)
    }

    @Test
    fun `missing roots are ignored instead of failing`() = runBlocking {
        val result = scanner().scan(listOf("/definitely/not/here"))
        assertEquals(0, result.total)
    }

    @Test
    fun `folder walk skips hidden directories`() = runBlocking {
        speckleFile("visible/a.mp3")
        speckleFile(".hidden/b.mp3")
        scanner().scan(listOf(root.toString()))
        assertEquals(1, db.trackCount())
    }
}
