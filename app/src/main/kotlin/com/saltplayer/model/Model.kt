package com.saltplayer.model

import kotlinx.serialization.Serializable

@Serializable
data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Double,
    val filePath: String,
    val coverArt: CoverArt? = null
)

@Serializable
data class CoverArt(
    val mimeType: String,
    val data: String  // base64-encoded image bytes
)

@Serializable
enum class PlayMode {
    SEQUENTIAL,
    SHUFFLE,
    REPEAT_ONE,
    REPEAT_ALL;

    companion object {
        fun fromString(s: String): PlayMode? = entries.find { it.name.equals(s, ignoreCase = true) }
    }
}

@Serializable
data class PlaybackState(
    val songId: String? = null,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val coverArt: CoverArt? = null,
    val duration: Double = 0.0,
    val position: Double = 0.0,
    val isPlaying: Boolean = false
)

@Serializable
data class AppConfig(
    val musicDirectory: String = "",
    val volume: Float = 0.8f,
    val playMode: PlayMode = PlayMode.SEQUENTIAL,
    val theme: String = "auto"  // "light" | "dark" | "auto"
)
