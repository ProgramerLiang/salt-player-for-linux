package com.saltplayer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.saltplayer.config.ConfigManager
import com.saltplayer.model.AppConfig
import com.saltplayer.model.PlaybackState
import com.saltplayer.model.PlayMode
import com.saltplayer.model.Song
import com.saltplayer.player.AudioPlayer
import com.saltplayer.queue.PlayQueue
import com.saltplayer.scanner.LibraryScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.sound.sampled.AudioSystem

class AppState {

    val audioPlayer = AudioPlayer()
    val playQueue = PlayQueue()
    val json = Json { ignoreUnknownKeys = true }

    var config by mutableStateOf(ConfigManager.load())
    var songs by mutableStateOf<List<Song>>(emptyList())
    var currentScreen by mutableStateOf(Screen.LIBRARY)
    var isDarkTheme by mutableStateOf(false)
    var isScanning by mutableStateOf(false)

    // Position tracking via polling timer
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var _playbackState = MutableStateFlow(PlaybackState())
    val playbackState = _playbackState.asStateFlow()

    init {
        // Restore cached songs
        val cached = ConfigManager.loadSongsCache()
        if (cached != null) {
            try {
                songs = json.decodeFromString<List<Song>>(cached)
            } catch (_: Exception) {}
        }
    }

    fun scanLibrary() {
        val dir = File(config.musicDirectory)
        if (!dir.isDirectory) return
        isScanning = true
        songs = LibraryScanner.scan(dir)
        // Cache songs
        try {
            ConfigManager.saveSongsCache(json.encodeToString(songs))
        } catch (_: Exception) {}
        isScanning = false
    }

    fun playSong(songId: String) {
        val idx = songs.indexOfFirst { it.id == songId }
        if (idx < 0) return
        playQueue.setQueue(songs, idx)
        val song = songs[idx]
        audioPlayer.loadAndPlay(
            songId = song.id,
            title = song.title,
            artist = song.artist,
            album = song.album,
            coverArtBase64 = song.coverArt?.data,
            coverMime = song.coverArt?.mimeType,
            filePath = song.filePath,
            duration = song.duration
        )
    }

    fun togglePlayPause() {
        if (audioPlayer.isPlaying()) {
            audioPlayer.pause()
        } else {
            val current = playQueue.current()
            if (current != null && audioPlayer.state.value.songId != null) {
                audioPlayer.play()
            } else if (current != null) {
                playSong(current.id)
            }
        }
    }

    fun nextSong() {
        playQueue.next()?.let { playSong(it.id) }
    }

    fun prevSong() {
        playQueue.prev()?.let { playSong(it.id) }
    }

    fun setPlayMode(mode: PlayMode) {
        playQueue.setMode(mode)
        config = config.copy(playMode = mode)
        saveConfig()
    }

    fun setVolume(vol: Float) {
        audioPlayer.setVolume(vol)
        config = config.copy(volume = vol)
        saveConfig()
    }

    fun setMusicDirectory(dir: String) {
        config = config.copy(musicDirectory = dir)
        saveConfig()
        scanLibrary()
    }

    private fun saveConfig() {
        ConfigManager.save(config)
    }

    fun updatePlaybackPosition() {
        val pos = audioPlayer.getPosition()
        val currentState = audioPlayer.state.value
        _playbackState.value = currentState.copy(position = pos)
    }

    fun destroy() {
        audioPlayer.destroy()
    }
}

enum class Screen {
    LIBRARY,
    PLAYLIST,
    NOW_PLAYING,
    SETTINGS
}
