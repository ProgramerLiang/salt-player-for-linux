// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Corripo
package com.saltplayer.data.db

import com.saltplayer.core.Log
import com.saltplayer.domain.model.Playlist
import com.saltplayer.domain.model.Track
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

/**
 * SQLite-backed media library.
 *
 * The database is the single source of truth for the library, favourites, playlists and play
 * history; it is loaded into memory once at start-up so the UI never waits for a scan.
 */
class LibraryDatabase(private val path: Path) : AutoCloseable {

    private val connection: Connection

    init {
        Files.createDirectories(path.toAbsolutePath().parent)
        Class.forName("org.sqlite.JDBC")
        connection = DriverManager.getConnection("jdbc:sqlite:$path")
        connection.autoCommit = true
        connection.createStatement().use { statement ->
            statement.execute("PRAGMA journal_mode=WAL")
            statement.execute("PRAGMA synchronous=NORMAL")
            statement.execute("PRAGMA foreign_keys=ON")
        }
        createSchema()
    }

    private fun createSchema() {
        connection.createStatement().use { st ->
            st.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS tracks (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    path TEXT NOT NULL UNIQUE,
                    title TEXT NOT NULL DEFAULT '',
                    artist TEXT NOT NULL DEFAULT '',
                    album_artist TEXT NOT NULL DEFAULT '',
                    album TEXT NOT NULL DEFAULT '',
                    track_no INTEGER NOT NULL DEFAULT 0,
                    disc_no INTEGER NOT NULL DEFAULT 0,
                    year INTEGER NOT NULL DEFAULT 0,
                    genre TEXT NOT NULL DEFAULT '',
                    duration_ms INTEGER NOT NULL DEFAULT 0,
                    bitrate_kbps INTEGER NOT NULL DEFAULT 0,
                    sample_rate_hz INTEGER NOT NULL DEFAULT 0,
                    channels INTEGER NOT NULL DEFAULT 0,
                    format TEXT NOT NULL DEFAULT '',
                    size_bytes INTEGER NOT NULL DEFAULT 0,
                    mtime_ms INTEGER NOT NULL DEFAULT 0,
                    added_at_ms INTEGER NOT NULL DEFAULT 0,
                    has_cover INTEGER NOT NULL DEFAULT 0,
                    favorite INTEGER NOT NULL DEFAULT 0,
                    rg_track REAL,
                    rg_album REAL,
                    has_lyrics INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_tracks_album ON tracks(album_artist, album)")
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_tracks_artist ON tracks(artist)")
            st.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS playlists (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    created_at_ms INTEGER NOT NULL
                )
                """.trimIndent()
            )
            st.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS playlist_tracks (
                    playlist_id INTEGER NOT NULL REFERENCES playlists(id) ON DELETE CASCADE,
                    track_id INTEGER NOT NULL REFERENCES tracks(id) ON DELETE CASCADE,
                    position INTEGER NOT NULL
                )
                """.trimIndent()
            )
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_playlist_tracks ON playlist_tracks(playlist_id, position)")
            st.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS plays (
                    track_id INTEGER NOT NULL REFERENCES tracks(id) ON DELETE CASCADE,
                    played_at_ms INTEGER NOT NULL
                )
                """.trimIndent()
            )
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_plays_at ON plays(played_at_ms DESC)")
        }
    }

    // --- tracks ---------------------------------------------------------

    fun trackCount(): Int = connection.prepareStatement("SELECT COUNT(*) FROM tracks").use { st ->
        st.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
    }

    fun loadAllTracks(): List<Track> = connection.prepareStatement("SELECT $TRACK_COLUMNS FROM tracks").use { st ->
        st.executeQuery().use { rs ->
            buildList {
                while (rs.next()) add(rs.toTrack())
            }
        }
    }

    /** Path → (size, mtime) of every row, used to compute an incremental scan. */
    fun fileSignatures(): Map<String, FileSignature> =
        connection.prepareStatement("SELECT path, size_bytes, mtime_ms FROM tracks").use { st ->
            st.executeQuery().use { rs ->
                buildMap {
                    while (rs.next()) {
                        put(rs.getString(1), FileSignature(rs.getLong(2), rs.getLong(3)))
                    }
                }
            }
        }

    data class FileSignature(val sizeBytes: Long, val mtimeMs: Long)

    /**
     * Inserts or updates [tracks] keyed by path, preserving favourite flags and ids.
     * Returns a map of path → id.
     */
    fun upsertTracks(tracks: List<Track>): Map<String, Long> {
        if (tracks.isEmpty()) return emptyMap()
        val result = HashMap<String, Long>(tracks.size)
        connection.autoCommit = false
        try {
            connection.prepareStatement(
                """
                INSERT INTO tracks (path, title, artist, album_artist, album, track_no, disc_no, year, genre,
                    duration_ms, bitrate_kbps, sample_rate_hz, channels, format, size_bytes, mtime_ms,
                    added_at_ms, has_cover, favorite, rg_track, rg_album, has_lyrics)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(path) DO UPDATE SET
                    title=excluded.title, artist=excluded.artist, album_artist=excluded.album_artist,
                    album=excluded.album, track_no=excluded.track_no, disc_no=excluded.disc_no,
                    year=excluded.year, genre=excluded.genre, duration_ms=excluded.duration_ms,
                    bitrate_kbps=excluded.bitrate_kbps, sample_rate_hz=excluded.sample_rate_hz,
                    channels=excluded.channels, format=excluded.format, size_bytes=excluded.size_bytes,
                    mtime_ms=excluded.mtime_ms, has_cover=excluded.has_cover, rg_track=excluded.rg_track,
                    rg_album=excluded.rg_album, has_lyrics=excluded.has_lyrics
                RETURNING id
                """.trimIndent()
            ).use { st ->
                for (track in tracks) {
                    st.setString(1, track.path)
                    st.setString(2, track.title)
                    st.setString(3, track.artist)
                    st.setString(4, track.albumArtist)
                    st.setString(5, track.album)
                    st.setInt(6, track.trackNo)
                    st.setInt(7, track.discNo)
                    st.setInt(8, track.year)
                    st.setString(9, track.genre)
                    st.setLong(10, track.durationMs)
                    st.setInt(11, track.bitrateKbps)
                    st.setInt(12, track.sampleRateHz)
                    st.setInt(13, track.channels)
                    st.setString(14, track.format)
                    st.setLong(15, track.sizeBytes)
                    st.setLong(16, track.mtimeMs)
                    st.setLong(17, if (track.addedAtMs > 0) track.addedAtMs else System.currentTimeMillis())
                    st.setInt(18, if (track.hasCover) 1 else 0)
                    st.setInt(19, if (track.favorite) 1 else 0)
                    track.replayGainTrackDb?.let { st.setDouble(20, it) } ?: st.setNull(20, java.sql.Types.REAL)
                    track.replayGainAlbumDb?.let { st.setDouble(21, it) } ?: st.setNull(21, java.sql.Types.REAL)
                    st.setInt(22, if (track.hasEmbeddedLyrics) 1 else 0)
                    st.executeQuery().use { rs ->
                        if (rs.next()) result[track.path] = rs.getLong(1)
                    }
                }
            }
            connection.commit()
        } catch (error: Exception) {
            runCatching { connection.rollback() }
            Log.error("LibraryDatabase", "upsertTracks failed", error)
            throw error
        } finally {
            connection.autoCommit = true
        }
        return result
    }

    /** Deletes tracks whose file vanished; playlists/history cascade. */
    fun deleteTracks(paths: Collection<String>): Int {
        if (paths.isEmpty()) return 0
        connection.autoCommit = false
        var deleted = 0
        try {
            connection.prepareStatement("DELETE FROM tracks WHERE path = ?").use { st ->
                for (path in paths) {
                    st.setString(1, path)
                    deleted += st.executeUpdate()
                }
            }
            connection.commit()
        } catch (error: Exception) {
            runCatching { connection.rollback() }
            Log.error("LibraryDatabase", "deleteTracks failed", error)
        } finally {
            connection.autoCommit = true
        }
        return deleted
    }

    /** Stores a duration measured by the audio backend (formats jaudiotagger cannot parse). */
    fun setDuration(trackId: Long, durationMs: Long) {
        connection.prepareStatement("UPDATE tracks SET duration_ms = ? WHERE id = ? AND duration_ms <= 0").use { st ->
            st.setLong(1, durationMs)
            st.setLong(2, trackId)
            st.executeUpdate()
        }
    }

    fun setFavorite(trackId: Long, favorite: Boolean) {
        connection.prepareStatement("UPDATE tracks SET favorite = ? WHERE id = ?").use { st ->
            st.setInt(1, if (favorite) 1 else 0)
            st.setInt(2, trackId.toInt())
            st.executeUpdate()
        }
    }

    fun recordPlay(trackId: Long, atMs: Long = System.currentTimeMillis()) {
        connection.prepareStatement("INSERT INTO plays(track_id, played_at_ms) VALUES (?, ?)").use { st ->
            st.setLong(1, trackId)
            st.setLong(2, atMs)
            st.executeUpdate()
        }
        // Keep the history bounded.
        connection.createStatement().use { st ->
            st.executeUpdate("DELETE FROM plays WHERE rowid NOT IN (SELECT rowid FROM plays ORDER BY played_at_ms DESC LIMIT 5000)")
        }
    }

    /** Track ids ordered by most recent play, one entry per track. */
    fun recentlyPlayedTrackIds(limit: Int = 200): List<Long> =
        connection.prepareStatement(
            "SELECT track_id, MAX(played_at_ms) AS last FROM plays GROUP BY track_id ORDER BY last DESC LIMIT ?"
        ).use { st ->
            st.setInt(1, limit)
            st.executeQuery().use { rs ->
                buildList { while (rs.next()) add(rs.getLong(1)) }
            }
        }

    // --- playlists ------------------------------------------------------

    fun playlists(): List<Playlist> = connection.prepareStatement(
        """
        SELECT p.id, p.name, p.created_at_ms, COUNT(pt.track_id)
        FROM playlists p LEFT JOIN playlist_tracks pt ON pt.playlist_id = p.id
        GROUP BY p.id ORDER BY p.name COLLATE NOCASE
        """.trimIndent()
    ).use { st ->
        st.executeQuery().use { rs ->
            buildList {
                while (rs.next()) {
                    add(Playlist(rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getInt(4)))
                }
            }
        }
    }

    fun createPlaylist(name: String, nowMs: Long = System.currentTimeMillis()): Long {
        connection.prepareStatement("INSERT INTO playlists(name, created_at_ms) VALUES (?, ?)").use { st ->
            st.setString(1, name)
            st.setLong(2, nowMs)
            st.executeUpdate()
        }
        return connection.prepareStatement("SELECT last_insert_rowid()").use { st ->
            st.executeQuery().use { rs -> if (rs.next()) rs.getLong(1) else -1L }
        }
    }

    fun renamePlaylist(playlistId: Long, name: String) {
        connection.prepareStatement("UPDATE playlists SET name = ? WHERE id = ?").use { st ->
            st.setString(1, name)
            st.setLong(2, playlistId)
            st.executeUpdate()
        }
    }

    fun deletePlaylist(playlistId: Long) {
        connection.prepareStatement("DELETE FROM playlists WHERE id = ?").use { st ->
            st.setLong(1, playlistId)
            st.executeUpdate()
        }
    }

    fun playlistTrackIds(playlistId: Long): List<Long> =
        connection.prepareStatement(
            "SELECT track_id FROM playlist_tracks WHERE playlist_id = ? ORDER BY position"
        ).use { st ->
            st.setLong(1, playlistId)
            st.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.getLong(1)) } }
        }

    fun playlistTracks(playlistId: Long): List<Track> =
        connection.prepareStatement(
            """
            SELECT ${TRACK_COLUMNS_PREFIXED} FROM playlist_tracks pt
            JOIN tracks t ON t.id = pt.track_id
            WHERE pt.playlist_id = ? ORDER BY pt.position
            """.trimIndent()
        ).use { st ->
            st.setLong(1, playlistId)
            st.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toTrack()) } }
        }

    fun addToPlaylist(playlistId: Long, trackIds: List<Long>) {
        if (trackIds.isEmpty()) return
        connection.autoCommit = false
        try {
            val nextPosition = connection.prepareStatement(
                "SELECT COALESCE(MAX(position) + 1, 0) FROM playlist_tracks WHERE playlist_id = ?"
            ).use { st ->
                st.setLong(1, playlistId)
                st.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
            }
            connection.prepareStatement(
                "INSERT INTO playlist_tracks(playlist_id, track_id, position) VALUES (?, ?, ?)"
            ).use { st ->
                var position = nextPosition
                for (trackId in trackIds) {
                    st.setLong(1, playlistId)
                    st.setLong(2, trackId)
                    st.setInt(3, position++)
                    st.addBatch()
                }
                st.executeBatch()
            }
            connection.commit()
        } catch (error: Exception) {
            runCatching { connection.rollback() }
            Log.error("LibraryDatabase", "addToPlaylist failed", error)
        } finally {
            connection.autoCommit = true
        }
    }

    fun removeFromPlaylist(playlistId: Long, trackId: Long) {
        connection.autoCommit = false
        try {
            connection.prepareStatement(
                "DELETE FROM playlist_tracks WHERE playlist_id = ? AND track_id = ?"
            ).use { st ->
                st.setLong(1, playlistId)
                st.setLong(2, trackId)
                st.executeUpdate()
            }
            resequencePlaylist(playlistId)
            connection.commit()
        } catch (error: Exception) {
            runCatching { connection.rollback() }
            Log.error("LibraryDatabase", "removeFromPlaylist failed", error)
        } finally {
            connection.autoCommit = true
        }
    }

    fun reorderPlaylist(playlistId: Long, orderedTrackIds: List<Long>) {
        connection.autoCommit = false
        try {
            connection.prepareStatement(
                "UPDATE playlist_tracks SET position = ? WHERE playlist_id = ? AND track_id = ?"
            ).use { st ->
                orderedTrackIds.forEachIndexed { index, trackId ->
                    st.setInt(1, index)
                    st.setLong(2, playlistId)
                    st.setLong(3, trackId)
                    st.addBatch()
                }
                st.executeBatch()
            }
            connection.commit()
        } catch (error: Exception) {
            runCatching { connection.rollback() }
            Log.error("LibraryDatabase", "reorderPlaylist failed", error)
        } finally {
            connection.autoCommit = true
        }
    }

    private fun resequencePlaylist(playlistId: Long) {
        val ids = playlistTrackIds(playlistId)
        connection.prepareStatement(
            "UPDATE playlist_tracks SET position = ? WHERE playlist_id = ? AND track_id = ?"
        ).use { st ->
            ids.forEachIndexed { index, trackId ->
                st.setInt(1, index)
                st.setLong(2, playlistId)
                st.setLong(3, trackId)
                st.addBatch()
            }
            st.executeBatch()
        }
    }

    override fun close() {
        runCatching { connection.close() }
    }

    private companion object {
        const val TRACK_COLUMNS =
            "id, path, title, artist, album_artist, album, track_no, disc_no, year, genre, duration_ms, " +
                "bitrate_kbps, sample_rate_hz, channels, format, size_bytes, mtime_ms, added_at_ms, has_cover, " +
                "favorite, rg_track, rg_album, has_lyrics"

        const val TRACK_COLUMNS_PREFIXED =
            "t.id, t.path, t.title, t.artist, t.album_artist, t.album, t.track_no, t.disc_no, t.year, t.genre, " +
                "t.duration_ms, t.bitrate_kbps, t.sample_rate_hz, t.channels, t.format, t.size_bytes, t.mtime_ms, " +
                "t.added_at_ms, t.has_cover, t.favorite, t.rg_track, t.rg_album, t.has_lyrics"
    }
}

internal fun java.sql.ResultSet.toTrack(): Track = Track(
    id = getLong(1),
    path = getString(2),
    title = getString(3) ?: "",
    artist = getString(4) ?: "",
    albumArtist = getString(5) ?: "",
    album = getString(6) ?: "",
    trackNo = getInt(7),
    discNo = getInt(8),
    year = getInt(9),
    genre = getString(10) ?: "",
    durationMs = getLong(11),
    bitrateKbps = getInt(12),
    sampleRateHz = getInt(13),
    channels = getInt(14),
    format = getString(15) ?: "",
    sizeBytes = getLong(16),
    mtimeMs = getLong(17),
    addedAtMs = getLong(18),
    hasCover = getInt(19) != 0,
    favorite = getInt(20) != 0,
    replayGainTrackDb = getObject(21)?.let { (it as Number).toDouble() },
    replayGainAlbumDb = getObject(22)?.let { (it as Number).toDouble() },
    hasEmbeddedLyrics = getInt(23) != 0,
)
