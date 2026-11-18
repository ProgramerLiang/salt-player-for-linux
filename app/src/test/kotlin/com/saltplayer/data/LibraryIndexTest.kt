// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Corripo
package com.saltplayer.data

import com.saltplayer.domain.library.LibraryIndex
import com.saltplayer.domain.library.SongSort
import com.saltplayer.domain.model.Playlist
import com.saltplayer.domain.model.Track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LibraryIndexTest {

    private fun track(
        id: Long,
        title: String,
        artist: String = "Artist",
        album: String = "Album",
        albumArtist: String = "",
        trackNo: Int = 1,
        discNo: Int = 1,
        year: Int = 2020,
        durationMs: Long = 60_000,
        path: String = "/music/Artist/Album/$title.mp3",
    ) = Track(
        id = id,
        path = path,
        title = title,
        artist = artist,
        albumArtist = albumArtist,
        album = album,
        trackNo = trackNo,
        discNo = discNo,
        year = year,
        genre = "",
        durationMs = durationMs,
        bitrateKbps = 320,
        sampleRateHz = 44_100,
        channels = 2,
        format = "MP3",
        sizeBytes = 1_000,
        mtimeMs = 0,
        addedAtMs = id,
        hasCover = false,
    )

    @Test
    fun `albums group by album artist and album name`() {
        val tracks = listOf(
            track(1, "a", album = "X", albumArtist = "A"),
            track(2, "b", album = "X", albumArtist = "A", trackNo = 2),
            track(3, "c", album = "X", albumArtist = "B"),
        )
        val albums = LibraryIndex.albums(tracks)
        assertEquals(2, albums.size)
        val first = albums.first { it.artist == "A" }
        assertEquals(2, first.trackCount)
        assertEquals(listOf(1L, 2L), first.tracks.map { it.id })
    }

    @Test
    fun `artists count distinct albums`() {
        val artists = LibraryIndex.artists(
            listOf(
                track(1, "a", artist = "Solo", album = "One"),
                track(2, "b", artist = "Solo", album = "Two"),
                track(3, "c", artist = "Other", album = "One"),
            )
        )
        val solo = artists.first { it.name == "Solo" }
        assertEquals(2, solo.albumCount)
        assertEquals(2, solo.trackCount)
    }

    @Test
    fun `folder tree nests directories under the configured root`() {
        val tracks = listOf(
            track(1, "a", path = "/music/rock/a.mp3"),
            track(2, "b", path = "/music/rock/sub/b.mp3"),
            track(3, "c", path = "/music/jazz/c.mp3"),
        )
        val tree = LibraryIndex.folderTree(tracks, listOf("/music"))
        assertEquals(1, tree.size)
        val root = tree.first()
        assertEquals("/music", root.path)
        assertEquals(3, root.trackCount)
        assertEquals(2, root.children.size)
        val rock = root.children.first { it.name == "rock" }
        assertEquals(2, rock.trackCount)
        assertEquals(1, rock.children.size)
    }

    @Test
    fun `search matches title artist album and playlist`() {
        val tracks = listOf(track(1, "Moonlight", artist = "Debussy"), track(2, "Sunrise", artist = "Other"))
        val albums = LibraryIndex.albums(tracks)
        val artists = LibraryIndex.artists(tracks)
        val playlists = listOf(Playlist(1, "Moon mix", 0, 0))
        val result = LibraryIndex.search("moon", tracks, albums, artists, playlists)
        assertEquals(listOf(1L), result.tracks.map { it.id })
        assertEquals(1, result.playlists.size)
        assertTrue(LibraryIndex.search("", tracks, albums, artists, playlists).isEmpty)
    }

    @Test
    fun `sorting respects direction and is stable`() {
        val tracks = listOf(track(1, "b", durationMs = 20_000), track(2, "a", durationMs = 30_000))
        assertEquals(listOf("a", "b"), LibraryIndex.sort(tracks, SongSort.Title).map { it.title })
        assertEquals(listOf("b", "a"), LibraryIndex.sort(tracks, SongSort.Title, descending = true).map { it.title })
        assertEquals(listOf(1L, 2L), LibraryIndex.sort(tracks, SongSort.Duration).map { it.id })
    }
}
