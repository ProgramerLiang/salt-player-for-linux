package com.saltplayer.config

import com.saltplayer.model.AppConfig
import com.saltplayer.model.PlayMode
import kotlinx.serialization.json.Json
import java.io.File

object ConfigManager {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private val configDir: File by lazy {
        val dir = File(System.getProperty("user.home"), ".config/salt-player")
        dir.mkdirs()
        dir
    }

    private val configFile: File get() = File(configDir, "config.json")

    fun load(): AppConfig {
        return try {
            if (configFile.exists()) {
                json.decodeFromString<AppConfig>(configFile.readText())
            } else {
                AppConfig(
                    musicDirectory = defaultMusicDir()
                )
            }
        } catch (_: Exception) {
            AppConfig(musicDirectory = defaultMusicDir())
        }
    }

    fun save(config: AppConfig) {
        try {
            configFile.writeText(json.encodeToString(AppConfig.serializer(), config))
        } catch (_: Exception) {
            System.err.println("salt-player: failed to save config")
        }
    }

    fun cacheDir(): File {
        val dir = File(configDir, "cache")
        dir.mkdirs()
        return dir
    }

    fun saveSongsCache(songsJson: String) {
        File(cacheDir(), "songs.json").writeText(songsJson)
    }

    fun loadSongsCache(): String? {
        val file = File(cacheDir(), "songs.json")
        return if (file.exists()) file.readText() else null
    }

    private fun defaultMusicDir(): String {
        val home = System.getProperty("user.home")
        return File(home, "Music").takeIf { it.isDirectory }?.absolutePath ?: home
    }
}
