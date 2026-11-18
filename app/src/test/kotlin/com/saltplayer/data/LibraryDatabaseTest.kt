// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Corripo
package com.saltplayer.data

import com.saltplayer.data.db.LibraryDatabase
import com.saltplayer.domain.model.Track
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LibraryDatabaseTest {

    private val dir = Files.createTempDirectory("salt-db")
    private val db = LibraryDatabase(dir.resolve("library.db"))

    @AfterTest
    fun tearDown() = db.close()

    private fun track(id: Long, path: String, favorite: Boolean = false) = Track(
        id = id,
        path = path,
        title = "title-$id",
        artist = "artist",
        albumArtist = "artist",
        album = "album",
        trackNo = id.toInt(),
        discNo = 1,
        year = 2024,
        genre = "Pop",
        durationMs = 1234,
        bitrateKbps = 320,
        sampleRateHz = 44_100,
        channels = 2,
        format = "MP3",
        sizeBytes = 100,
        mtimeMs = 1,
        addedAtMs = 1,
        hasCover = false,
        favorite = favorite,
    )

    @Test
    fun `upsert inserts then updates and keeps ids stable`() {
        val first = db.upsertTracks(listOf(track(0, "/m/1.mp3"), track(0, "/m/2.mp3")))
        assertEquals(2, db.trackCount())
        val idOfFirst = first["/m/1.mp3"]
        val again = db.upsertTracks(listOf(track(0, "/m/1.mp3").copy(title = "renamed")))
        assertEquals(idOfFirst, again["/m/1.mp3"])
        assertEquals(2, db.trackCount())
        assertEquals("renamed", db.loadAllTracks().first { it.path == "/m/1.mp3" }.title)
    }

    @Test
    fun `favorites survive an update and can be toggled`() {
        val ids = db.upsertTracks(listOf(track(0, "/m/1.mp3")))
        val id = ids.getValue("/m/1.mp3")
        db.setFavorite(id, true)
        assertEquals(true, db.loadAllTracks().single().favorite)
        db.upsertTracks(listOf(track(0, "/m/1.mp3").copy(title = "changed")))
        assertEquals(true, db.loadAllTracks().single().favorite, "favourite must survive a rescan")
        db.setFavorite(id, false)
        assertEquals(false, db.loadAllTracks().single().favorite)
    }

    @Test
    fun `recently played is ordered by most recent play`() {
        val ids = db.upsertTracks(listOf(track(0, "/m/1.mp3"), track(0, "/m/2.mp3")))
        val first = ids.getValue("/m/1.mp3")
        val second = ids.getValue("/m/2.mp3")
        db.recordPlay(first, 1_000)
        db.recordPlay(second, 2_000)
        db.recordPlay(first, 3_000)
        assertEquals(listOf(first, second), db.recentlyPlayedTrackIds())
    }

    @Test
    fun `deleting a path removes the track and its history`() {
        val ids = db.upsertTracks(listOf(track(0, "/m/1.mp3")))
        db.recordPlay(ids.getValue("/m/1.mp3"), 1)
        assertEquals(1, db.deleteTracks(listOf("/m/1.mp3")))
        assertEquals(0, db.trackCount())
        assertTrue(db.recentlyPlayedTrackIds().isEmpty())
    }

    @Test
    fun `playlists keep insertion order and support reordering and removal`() {
        val ids = db.upsertTracks(listOf(track(0, "/m/1.mp3"), track(0, "/m/2.mp3"), track(0, "/m/3.mp3")))
        val playlistId = db.createPlaylist("Mix", 1_000)
        db.addToPlaylist(playlistId, listOf(ids.getValue("/m/3.mp3"), ids.getValue("/m/1.mp3")))
        assertEquals(listOf(ids.getValue("/m/3.mp3"), ids.getValue("/m/1.mp3")), db.playlistTrackIds(playlistId))
        db.reorderPlaylist(playlistId, listOf(ids.getValue("/m/1.mp3"), ids.getValue("/m/3.mp3")))
        assertEquals(listOf(ids.getValue("/m/1.mp3"), ids.getValue("/m/3.mp3")), db.playlistTrackIds(playlistId))
        db.removeFromPlaylist(playlistId, ids.getValue("/m/1.mp3"))
        assertEquals(listOf(ids.getValue("/m/3.mp3")), db.playlistTrackIds(playlistId))
        assertEquals(1, db.playlists().single().trackCount)
        db.renamePlaylist(playlistId, "Renamed")
        assertEquals("Renamed", db.playlists().single().name)
        db.deletePlaylist(playlistId)
        assertTrue(db.playlists().isEmpty())
    }

    @Test
    fun `duration backfill only touches tracks without a duration`() {
        val ids = db.upsertTracks(listOf(track(0, "/m/1.mp3").copy(durationMs = 0)))
        val id = ids.getValue("/m/1.mp3")
        db.setDuration(id, 9_000)
        assertEquals(9_000, db.loadAllTracks().single().durationMs)
        db.setDuration(id, 12_000)
        assertEquals(9_000, db.loadAllTracks().single().durationMs)
    }
}
