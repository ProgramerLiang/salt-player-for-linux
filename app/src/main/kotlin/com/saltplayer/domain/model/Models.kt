// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Corripo
package com.saltplayer.domain.model

/**
 * One audio file in the local library. [id] is the database primary key and is stable
 * across scans (it is keyed by [path]); [mtimeMs] and [sizeBytes] drive incremental rescans.
 */
data class Track(
    val id: Long,
    val path: String,
    val title: String,
    val artist: String,
    val albumArtist: String,
    val album: String,
    val trackNo: Int,
    val discNo: Int,
    val year: Int,
    val genre: String,
    val durationMs: Long,
    val bitrateKbps: Int,
    val sampleRateHz: Int,
    val channels: Int,
    val format: String,
    val sizeBytes: Long,
    val mtimeMs: Long,
    val addedAtMs: Long,
    val hasCover: Boolean,
    val favorite: Boolean = false,
    val replayGainTrackDb: Double? = null,
    val replayGainAlbumDb: Double? = null,
    val hasEmbeddedLyrics: Boolean = false,
) {
    val fileName: String get() = path.substringAfterLast('/')

    val displayTitle: String get() = title.ifBlank { fileName.substringBeforeLast('.') }

    val displayArtist: String get() = artist.ifBlank { unknownArtistLabel }

    /** Stable grouping key for albums: albums by different artists must not merge. */
    val albumKey: String get() = "${albumArtist.ifBlank { artist }}\u0000$album"

    companion object {
        const val unknownArtistLabel = "Unknown Artist"
        const val unknownAlbumLabel = "Unknown Album"
    }
}

data class Album(
    val key: String,
    val title: String,
    val artist: String,
    val year: Int,
    val trackCount: Int,
    val durationMs: Long,
    val coverTrackId: Long?,
    val tracks: List<Track> = emptyList(),
)

data class Artist(
    val name: String,
    val albumCount: Int,
    val trackCount: Int,
    val tracks: List<Track> = emptyList(),
)

data class Playlist(
    val id: Long,
    val name: String,
    val createdAtMs: Long,
    val trackCount: Int,
    val tracks: List<Track> = emptyList(),
)

/** A node in the folder tree view. */
data class FolderNode(
    val path: String,
    val name: String,
    val trackCount: Int,
    val children: List<FolderNode> = emptyList(),
    val tracks: List<Track> = emptyList(),
)

enum class LibraryView {
    Songs,
    Albums,
    Artists,
    Folders,
    Playlists,
    Recent,
    Favorites,
}

const val RECENT_TRACKS_LIMIT = 200
