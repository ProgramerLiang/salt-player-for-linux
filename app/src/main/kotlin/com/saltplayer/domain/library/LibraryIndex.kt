// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Corripo
package com.saltplayer.domain.library

import com.saltplayer.domain.model.Album
import com.saltplayer.domain.model.Artist
import com.saltplayer.domain.model.FolderNode
import com.saltplayer.domain.model.Playlist
import com.saltplayer.domain.model.Track
import java.text.Collator
import java.util.Locale

/** Sort keys for the songs list. */
enum class SongSort(val id: String) {
    Title("title"),
    Artist("artist"),
    Album("album"),
    Duration("duration"),
    Year("year"),
    AddedAt("added"),
    ;

    fun next(): SongSort = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromId(id: String?): SongSort = entries.firstOrNull { it.id == id } ?: Title
    }
}

/**
 * Pure in-memory projections of the media library: albums, artists, folder tree, search and
 * sorting. Kept free of SQLite/Compose so it is unit-testable.
 */
object LibraryIndex {

    private val collator: Collator = Collator.getInstance(Locale.SIMPLIFIED_CHINESE).apply {
        strength = Collator.SECONDARY
        decomposition = Collator.CANONICAL_DECOMPOSITION
    }

    /** Locale-aware, case-insensitive comparison (Chinese collates by pinyin in the JDK). */
    fun compareText(a: String, b: String): Int = collator.compare(a, b)

    fun sort(tracks: List<Track>, sort: SongSort, descending: Boolean = false): List<Track> {
        val comparator: Comparator<Track> = when (sort) {
            SongSort.Title -> compareBy<Track> { it.displayTitle.lowercase() }
            SongSort.Artist -> compareBy<Track> { it.displayArtist.lowercase() }
                .thenBy { it.album.lowercase() }
                .thenBy { it.discNo }
                .thenBy { it.trackNo }
            SongSort.Album -> compareBy<Track> { it.album.lowercase() }
                .thenBy { it.discNo }
                .thenBy { it.trackNo }
            SongSort.Duration -> compareBy<Track> { it.durationMs }
            SongSort.Year -> compareBy<Track> { it.year }.thenBy { it.album.lowercase() }
            SongSort.AddedAt -> compareBy<Track> { it.addedAtMs }
        }
        val sorted = tracks.sortedWith(comparator)
        return if (descending) sorted.reversed() else sorted
    }

    fun albums(tracks: List<Track>): List<Album> =
        tracks.groupBy { it.albumKey }
            .map { (key, albumTracks) ->
                val ordered = albumTracks.sortedWith(
                    compareBy<Track> { it.discNo }.thenBy { it.trackNo }.thenBy { it.displayTitle.lowercase() }
                )
                val first = ordered.first()
                Album(
                    key = key,
                    title = first.album.ifBlank { Track.unknownAlbumLabel },
                    artist = first.albumArtist.ifBlank { first.displayArtist },
                    year = ordered.map { it.year }.firstOrNull { it > 0 } ?: 0,
                    trackCount = ordered.size,
                    durationMs = ordered.sumOf { it.durationMs },
                    coverTrackId = ordered.firstOrNull { it.hasCover }?.id,
                    tracks = ordered,
                )
            }
            .sortedWith { a, b ->
                compareText(a.title, b.title).takeIf { it != 0 }
                    ?: compareText(a.artist, b.artist)
            }

    fun artists(tracks: List<Track>): List<Artist> =
        tracks.groupBy { it.displayArtist }
            .map { (name, artistTracks) ->
                Artist(
                    name = name,
                    albumCount = artistTracks.map { it.albumKey }.distinct().size,
                    trackCount = artistTracks.size,
                    tracks = artistTracks,
                )
            }
            .sortedWith { a, b -> compareText(a.name, b.name) }

    /**
     * Builds a folder tree from track paths. Folders outside every configured root are grouped
     * under the "Other" node so nothing silently disappears from the view.
     */
    fun folderTree(tracks: List<Track>, roots: List<String>): List<FolderNode> {
        val nodes = linkedMapOf<String, MutableList<Track>>()
        for (track in tracks) {
            val parent = track.path.substringBeforeLast('/', missingDelimiterValue = "")
            nodes.getOrPut(parent) { mutableListOf() }.add(track)
        }
        fun nodeOf(path: String): FolderNode {
            val direct = nodes[path].orEmpty()
            val children = nodes.keys
                .filter { it != path && it.startsWith("$path/") && !it.removePrefix("$path/").contains('/') }
                .sorted()
                .map { nodeOf(it) }
            val total = direct.size + children.sumOf { it.trackCount }
            return FolderNode(
                path = path,
                name = path.substringAfterLast('/').ifBlank { path },
                trackCount = total,
                children = children,
                tracks = direct.sortedWith(compareBy { it.fileName.lowercase() }),
            )
        }
        return roots.distinct().map { root ->
            val normalized = root.trimEnd('/')
            nodeOf(normalized)
        }.filter { it.trackCount > 0 || it.children.isNotEmpty() }
    }

    data class SearchResult(
        val tracks: List<Track>,
        val albums: List<Album>,
        val artists: List<Artist>,
        val playlists: List<Playlist>,
    ) {
        val isEmpty: Boolean
            get() = tracks.isEmpty() && albums.isEmpty() && artists.isEmpty() && playlists.isEmpty()
    }

    /** Case-insensitive substring search across track, album, artist and playlist names. */
    fun search(
        query: String,
        tracks: List<Track>,
        albums: List<Album>,
        artists: List<Artist>,
        playlists: List<Playlist>,
        limitPerSection: Int = 50,
    ): SearchResult {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return SearchResult(emptyList(), emptyList(), emptyList(), emptyList())
        val matchedTracks = tracks.filter {
            it.displayTitle.lowercase().contains(q) ||
                it.displayArtist.lowercase().contains(q) ||
                it.album.lowercase().contains(q) ||
                it.fileName.lowercase().contains(q)
        }.take(limitPerSection)
        val matchedAlbums = albums.filter {
            it.title.lowercase().contains(q) || it.artist.lowercase().contains(q)
        }.take(limitPerSection)
        val matchedArtists = artists.filter { it.name.lowercase().contains(q) }.take(limitPerSection)
        val matchedPlaylists = playlists.filter { it.name.lowercase().contains(q) }.take(limitPerSection)
        return SearchResult(matchedTracks, matchedAlbums, matchedArtists, matchedPlaylists)
    }
}
