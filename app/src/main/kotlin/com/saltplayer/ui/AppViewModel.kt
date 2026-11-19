// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Corripo
package com.saltplayer.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.saltplayer.core.AppPaths
import com.saltplayer.core.Log
import com.saltplayer.data.db.LibraryDatabase
import com.saltplayer.data.lyrics.LyricsRepository
import com.saltplayer.data.media.CoverStore
import com.saltplayer.data.prefs.Settings
import com.saltplayer.data.prefs.SettingsStore
import com.saltplayer.data.scan.LibraryScanner
import com.saltplayer.data.scan.TagReader
import com.saltplayer.domain.library.LibraryIndex
import com.saltplayer.domain.library.SongSort
import com.saltplayer.domain.lyrics.Lyrics
import com.saltplayer.domain.model.Album
import com.saltplayer.domain.model.Artist
import com.saltplayer.domain.model.FolderNode
import com.saltplayer.domain.model.LibraryView
import com.saltplayer.domain.model.Playlist
import com.saltplayer.domain.model.Track
import com.saltplayer.domain.queue.PlayMode
import com.saltplayer.playback.EngineDescriptor
import com.saltplayer.playback.EngineRegistry
import com.saltplayer.playback.PlayerController
import com.saltplayer.playback.PlayerState
import com.saltplayer.platform.SystemTheme
import com.saltplayer.ui.i18n.Strings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path

/** Screens the shell can show on the right-hand side, mirroring the Salt Player tablet layout. */
sealed interface Screen {
    data class Library(val view: LibraryView) : Screen
    data class AlbumDetail(val key: String) : Screen
    data class ArtistDetail(val name: String) : Screen
    data class PlaylistDetail(val id: Long) : Screen
    data class FolderDetail(val path: String) : Screen
    data object Search : Screen
    data object Settings : Screen
}

data class ScanUiState(
    val running: Boolean = false,
    val scanned: Int = 0,
    val total: Int = 0,
    val currentFile: String = "",
    val lastSummary: String? = null,
)

/**
 * Single state holder for the whole UI. Compose reads its `mutableStateOf` properties; every
 * expensive operation (scanning, tag reads, lyrics) runs on [Dispatchers.IO].
 */
class AppViewModel(
    val paths: AppPaths,
    private val db: LibraryDatabase,
    private val settingsStore: SettingsStore,
    val coverStore: CoverStore,
    private val lyricsRepository: LyricsRepository,
    private val scanner: LibraryScanner,
    val player: PlayerController,
    private val registry: EngineRegistry,
    private val scope: CoroutineScope,
) {
    var settings by mutableStateOf(settingsStore.load())
        private set

    val strings: Strings get() = Strings(settings.language)

    var tracks by mutableStateOf<List<Track>>(emptyList())
        private set
    var albums by mutableStateOf<List<Album>>(emptyList())
        private set
    var artists by mutableStateOf<List<Artist>>(emptyList())
        private set
    var playlists by mutableStateOf<List<Playlist>>(emptyList())
        private set
    var recentTracks by mutableStateOf<List<Track>>(emptyList())
        private set
    var favoriteTracks by mutableStateOf<List<Track>>(emptyList())
        private set
    var folderTree by mutableStateOf<List<FolderNode>>(emptyList())
        private set

    var screen by mutableStateOf<Screen>(
        // Debug/automation hook: screenshots and UI tests need to open a specific page
        // without synthesising clicks, which are unreliable under some compositors.
        if (System.getProperty("salt.player.startScreen") == "settings") Screen.Settings
        else Screen.Library(LibraryView.Songs)
    )
    var playerState by mutableStateOf(PlayerState())
        private set
    var scanState by mutableStateOf(ScanUiState())
        private set
    var searchQuery by mutableStateOf("")
    var searchActive by mutableStateOf(false)
    var nowPlayingOpen by mutableStateOf(false)
    var queueOpen by mutableStateOf(false)
    var equalizerOpen by mutableStateOf(false)
    var trackInfoTrack by mutableStateOf<Track?>(null)
    var currentLyrics by mutableStateOf(Lyrics.Empty)
        private set
    var toast by mutableStateOf<String?>(null)
        private set
    var systemDark by mutableStateOf(SystemTheme.isDark())
        private set
    var systemAccentName by mutableStateOf(SystemTheme.accentName())
        private set
    var backends by mutableStateOf<List<EngineDescriptor>>(emptyList())
        private set
    var songSort by mutableStateOf(settings.songSort.toIndexSort())
        private set
    var sortDescending by mutableStateOf(settings.songSortDescending)
        private set

    private var scanJob: Job? = null
    private var sleepJob: Job? = null
    private var byId: Map<Long, Track> = emptyMap()
    private var lastLyricsTrackId: Long? = null

    // --- lifecycle ------------------------------------------------------

    fun start() {
        scope.launch {
            player.prepareEngine()
            reloadLibrary()
            player.restoreSession()
            player.setVolume(settings.volume)
            player.setEqualizer(settings.equalizer)
            player.setOutputDevice(settings.outputDevice)
            player.setRate(settings.playbackSpeed)
            // The player state stream never completes; collecting it inline would block backend
            // probing, the start-up scan and session restore forever.
            scope.launch { collectPlayer() }
            // Screenshot/automation hook: open the now-playing page on the first library track
            // with a synthetic position so the flowing background and karaoke line can be captured
            // without a real audio device.
            if (System.getProperty("salt.player.startScreen") == "nowplaying" && tracks.isNotEmpty()) {
                // Let the player-state collector settle first, otherwise its first emission
                // overwrites the synthetic demo state immediately.
                delay(1_200)
                val demo = tracks.firstOrNull { track ->
                    val stem = track.path.substringBeforeLast('.')
                    java.nio.file.Files.isRegularFile(java.nio.file.Path.of("$stem.ttml")) ||
                        java.nio.file.Files.isRegularFile(java.nio.file.Path.of("$stem.lrc"))
                } ?: tracks.firstOrNull()
                if (demo != null) {
                    playerState = playerState.copy(
                        track = demo,
                        durationMs = demo.durationMs.coerceAtLeast(200_000),
                        positionMs = 26_000,
                    )
                    loadLyrics(demo)
                    nowPlayingOpen = true
                }
            }
            probeBackends()
            if (settings.scanOnStartup || tracks.isEmpty()) {
                rescan(full = tracks.isEmpty())
            }
        }
    }

    private suspend fun collectPlayer() {
        player.state.collect { state ->
            val previousTrack = playerState.track
            playerState = state
            if (state.track?.id != previousTrack?.id || (state.track != null && lastLyricsTrackId != state.track?.id)) {
                state.track?.let { loadLyrics(it) }
            }
        }
    }

    fun refreshSystemTheme() {
        systemDark = SystemTheme.isDark()
        systemAccentName = SystemTheme.accentName()
    }

    suspend fun probeBackends() {
        backends = withContext(Dispatchers.IO) { registry.probeAll() }
    }

    // --- library --------------------------------------------------------

    suspend fun reloadLibrary() = withContext(Dispatchers.IO) {
        val loaded = db.loadAllTracks()
        val playlistRows = db.playlists()
        val recentIds = db.recentlyPlayedTrackIds()
        withContext(Dispatchers.Main) {
            byId = loaded.associateBy { it.id }
            tracks = loaded
            playlists = playlistRows
            recentTracks = recentIds.mapNotNull { id -> loaded.firstOrNull { it.id == id } }
            favoriteTracks = loaded.filter { it.favorite }
            rebuildGroups()
        }
    }

    private fun rebuildGroups() {
        val sorted = LibraryIndex.sort(tracks, songSort, sortDescending)
        albums = LibraryIndex.albums(tracks)
        artists = LibraryIndex.artists(tracks)
        folderTree = LibraryIndex.folderTree(tracks, settings.musicDirs)
        tracks = sorted
    }

    fun changeSort(sort: SongSort) {
        songSort = sort
        persistSettings { it.copy(songSort = sort.toKey(), songSortDescending = sortDescending) }
        rebuildGroups()
    }

    fun toggleSortDirection() {
        sortDescending = !sortDescending
        persistSettings { it.copy(songSortDescending = sortDescending) }
        rebuildGroups()
    }

    fun trackById(id: Long): Track? = byId[id]

    fun playTracks(list: List<Track>, startIndex: Int, shuffle: Boolean = false) {
        if (list.isEmpty()) return
        if (shuffle) player.setMode(PlayMode.ShuffleNoRepeat)
        player.playQueue(list.map { it.id }, startIndex.coerceIn(0, list.lastIndex))
    }

    fun toggleFavorite(track: Track) {
        val next = !track.favorite
        scope.launch(Dispatchers.IO) {
            db.setFavorite(track.id, next)
            withContext(Dispatchers.Main) {
                tracks = tracks.map { if (it.id == track.id) it.copy(favorite = next) else it }
                favoriteTracks = tracks.filter { it.favorite }
                byId = tracks.associateBy { it.id }
                if (playerState.track?.id == track.id) {
                    playerState = playerState.copy(track = playerState.track?.copy(favorite = next))
                }
            }
        }
    }

    fun scan(full: Boolean) {
        if (scanState.running) return
        if (settings.musicDirs.isEmpty()) {
            toast = strings[com.saltplayer.ui.i18n.Str.EmptyLibraryHint]
            return
        }
        scanJob = scope.launch {
            scanState = ScanUiState(running = true)
            try {
                val result = scanner.scan(
                    roots = settings.musicDirs,
                    fullRescan = full,
                    followSymlinks = settings.scanFollowSymlinks,
                    onProgress = { progress ->
                        scope.launch {
                            scanState = scanState.copy(
                                scanned = progress.scanned,
                                total = progress.total,
                                currentFile = progress.currentFile,
                            )
                        }
                    },
                )
                reloadLibrary()
                scanState = ScanUiState(
                    running = false,
                    lastSummary = strings.format(
                        com.saltplayer.ui.i18n.Str.ScanSummary,
                        result.added, result.updated, result.removed, result.total,
                    ),
                )
            } catch (error: kotlinx.coroutines.CancellationException) {
                scanState = scanState.copy(running = false, lastSummary = strings[com.saltplayer.ui.i18n.Str.ScanCancelled])
            } catch (error: Throwable) {
                Log.error("AppViewModel", "scan failed", error)
                scanState = scanState.copy(running = false)
                toast = "${strings[com.saltplayer.ui.i18n.Str.ErrorScanFailed]}: ${error.message}"
            }
        }
    }

    fun rescan(full: Boolean) = scan(full)

    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
        scanState = scanState.copy(running = false)
    }

    // --- settings -------------------------------------------------------

    fun updateSettings(transform: (Settings) -> Settings) {
        val previous = settings
        val updated = transform(previous).sanitised()
        settings = updated
        settingsStore.save(updated)
        if (updated.volume != previous.volume) player.setVolume(updated.volume)
        if (updated.equalizer != previous.equalizer) player.setEqualizer(updated.equalizer)
        if (updated.playbackSpeed != previous.playbackSpeed) player.setRate(updated.playbackSpeed)
        if (updated.outputDevice != previous.outputDevice) player.setOutputDevice(updated.outputDevice)
        if (updated.musicDirs != previous.musicDirs) rebuildGroups()
    }

    private fun persistSettings(transform: (Settings) -> Settings) {
        val updated = transform(settings).sanitised()
        settings = updated
        settingsStore.save(updated)
    }

    /** Re-probes the audio backends and refreshes the settings list. */
    /** Switches audio backend: persists the choice, rebuilds the engine and reports the outcome. */
    fun selectBackend(preference: com.saltplayer.data.prefs.BackendPreference, label: String) {
        updateSettings { it.copy(backendPreference = preference) }
        player.reloadEngine()
        showToast("后端：$label")
    }

    fun rescanBackendsAsync() {
        scope.launch {
            probeBackends()
            showToast("backends re-probed")
        }
    }

    /** Applies an equalizer change and immediately hands it to the running backend. */
    fun updateSettingsAutoEq(transform: (com.saltplayer.data.prefs.EqualizerSettings) -> com.saltplayer.data.prefs.EqualizerSettings) {
        updateSettings { it.copy(equalizer = transform(it.equalizer)) }
    }

    fun addMusicDir(dir: String) {
        if (dir.isBlank()) return
        if (!Files.isDirectory(Path.of(dir))) {
            toast = "$dir: not a directory"
            return
        }
        updateSettings { it.copy(musicDirs = (it.musicDirs + dir).distinct()) }
        scan(full = false)
    }

    fun removeMusicDir(dir: String) {
        updateSettings { it.copy(musicDirs = it.musicDirs - dir) }
        scan(full = true)
    }

    fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        updateSettings { it.copy(sleepTimerMinutes = minutes) }
        if (minutes <= 0) return
        sleepJob = scope.launch {
            delay(minutes * 60_000L)
            player.pause()
            toast = strings[com.saltplayer.ui.i18n.Str.Pause]
        }
    }

    // --- playlists ------------------------------------------------------

    suspend fun createPlaylist(name: String): Long? = withContext(Dispatchers.IO) {
        val id = db.createPlaylist(name)
        withContext(Dispatchers.Main) {
            playlists = db.playlists()
            toast = strings.format(com.saltplayer.ui.i18n.Str.CreatedPlaylist, name)
        }
        id
    }

    fun createPlaylistAsync(name: String) {
        scope.launch { createPlaylist(name) }
    }

    fun deletePlaylist(id: Long) {
        scope.launch(Dispatchers.IO) {
            db.deletePlaylist(id)
            withContext(Dispatchers.Main) {
                playlists = db.playlists()
                if (screen is Screen.PlaylistDetail) screen = Screen.Library(LibraryView.Playlists)
            }
        }
    }

    fun renamePlaylist(id: Long, name: String) {
        scope.launch(Dispatchers.IO) {
            db.renamePlaylist(id, name)
            withContext(Dispatchers.Main) { playlists = db.playlists() }
        }
    }

    fun addToPlaylist(playlistId: Long, trackIds: List<Long>) {
        if (trackIds.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            db.addToPlaylist(playlistId, trackIds)
            withContext(Dispatchers.Main) {
                playlists = db.playlists()
                toast = strings[com.saltplayer.ui.i18n.Str.AddedToPlaylist]
            }
        }
    }

    fun removeFromPlaylist(playlistId: Long, trackId: Long) {
        scope.launch(Dispatchers.IO) {
            db.removeFromPlaylist(playlistId, trackId)
            withContext(Dispatchers.Main) { playlists = db.playlists() }
        }
    }

    suspend fun playlistTracks(id: Long): List<Track> = withContext(Dispatchers.IO) { db.playlistTracks(id) }

    fun playlistById(id: Long): Playlist? = playlists.firstOrNull { it.id == id }

    fun albumByKey(key: String): Album? = albums.firstOrNull { it.key == key }

    fun artistByName(name: String): Artist? = artists.firstOrNull { it.name == name }

    fun folderByPath(path: String): FolderNode? {
        fun find(nodes: List<FolderNode>): FolderNode? {
            for (node in nodes) {
                if (node.path == path) return node
                find(node.children)?.let { return it }
            }
            return null
        }
        return find(folderTree)
    }

    // --- lyrics ---------------------------------------------------------

    private suspend fun loadLyrics(track: Track) {
        lastLyricsTrackId = track.id
        val lyrics = withContext(Dispatchers.IO) { lyricsRepository.load(track) }
        currentLyrics = lyrics.withOffset(settings.lyricOffsetMs)
    }

    fun setLyricOffset(offsetMs: Long) {
        updateSettings { it.copy(lyricOffsetMs = offsetMs) }
        currentLyrics = currentLyrics.withOffset(offsetMs)
    }

    fun reloadLyrics() {
        val track = playerState.track ?: return
        scope.launch { loadLyrics(track) }
    }

    fun showToast(message: String) {
        toast = message
        scope.launch {
            delay(2500)
            if (toast == message) toast = null
        }
    }

    fun openScreen(target: Screen) {
        screen = target
        searchActive = false
    }

    fun navigateBack() {
        screen = when (val current = screen) {
            is Screen.Library -> Screen.Library(LibraryView.Songs)
            else -> Screen.Library(LibraryView.Songs)
        }
        searchActive = false
    }

    /** Track metadata for the info sheet, including the resolved file size. */
    fun trackDetails(track: Track): List<Pair<String, String>> {
        val s = strings
        return buildList {
            add(s[com.saltplayer.ui.i18n.Str.Path] to track.path)
            add(s[com.saltplayer.ui.i18n.Str.Format] to track.format.ifBlank { "-" })
            if (track.bitrateKbps > 0) add(s[com.saltplayer.ui.i18n.Str.Bitrate] to "${track.bitrateKbps} kbps")
            if (track.sampleRateHz > 0) add(s[com.saltplayer.ui.i18n.Str.SampleRate] to "${track.sampleRateHz} Hz")
            if (track.channels > 0) add(s[com.saltplayer.ui.i18n.Str.Channels] to track.channels.toString())
            add(s[com.saltplayer.ui.i18n.Str.Duration] to formatDuration(track.durationMs))
            if (track.year > 0) add(s[com.saltplayer.ui.i18n.Str.Year] to track.year.toString())
            if (track.genre.isNotBlank()) add(s[com.saltplayer.ui.i18n.Str.Genre] to track.genre)
        }
    }
}

fun formatDuration(ms: Long): String {
    if (ms <= 0) return "--:--"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

fun SongSort.toKey(): com.saltplayer.data.prefs.SongSortKey = when (this) {
    SongSort.Title -> com.saltplayer.data.prefs.SongSortKey.Title
    SongSort.Artist -> com.saltplayer.data.prefs.SongSortKey.Artist
    SongSort.Album -> com.saltplayer.data.prefs.SongSortKey.Album
    SongSort.Duration -> com.saltplayer.data.prefs.SongSortKey.Duration
    SongSort.Year -> com.saltplayer.data.prefs.SongSortKey.Year
    SongSort.AddedAt -> com.saltplayer.data.prefs.SongSortKey.AddedAt
}

fun com.saltplayer.data.prefs.SongSortKey.toIndexSort(): SongSort = when (this) {
    com.saltplayer.data.prefs.SongSortKey.Title -> SongSort.Title
    com.saltplayer.data.prefs.SongSortKey.Artist -> SongSort.Artist
    com.saltplayer.data.prefs.SongSortKey.Album -> SongSort.Album
    com.saltplayer.data.prefs.SongSortKey.Duration -> SongSort.Duration
    com.saltplayer.data.prefs.SongSortKey.Year -> SongSort.Year
    com.saltplayer.data.prefs.SongSortKey.AddedAt -> SongSort.AddedAt
}
