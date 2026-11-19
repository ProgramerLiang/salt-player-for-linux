// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Corripo
package com.saltplayer.core

import java.nio.file.Path
import java.nio.file.Paths

/**
 * XDG base-directory aware paths for Salt Player.
 *
 * Nothing is ever written into the user's home directory root: config, data and cache
 * live in the usual XDG locations. [env] is injectable so the resolution rules can be
 * unit-tested without touching the real environment.
 */
class AppPaths(private val env: Map<String, String> = System.getenv()) {

    private val home: Path = Paths.get(env["HOME"] ?: System.getProperty("user.home"))

    private fun xdg(name: String, fallback: String): Path {
        val raw = env[name]
        return if (!raw.isNullOrBlank()) {
            Paths.get(raw)
        } else {
            home.resolve(fallback)
        }
    }

    /** `~/.config/salt-shaker` */
    val configDir: Path = xdg("XDG_CONFIG_HOME", ".config").resolve(APP_DIR)

    /** `~/.local/share/salt-shaker` */
    val dataDir: Path = xdg("XDG_DATA_HOME", ".local/share").resolve(APP_DIR)

    /** `~/.cache/salt-shaker` */
    val cacheDir: Path = xdg("XDG_CACHE_HOME", ".cache").resolve(APP_DIR)

    /** Directories written by the previous name of the project, used only for migration. */
    val legacyConfigDir: Path = xdg("XDG_CONFIG_HOME", ".config").resolve(LEGACY_APP_DIR)
    val legacyDataDir: Path = xdg("XDG_DATA_HOME", ".local/share").resolve(LEGACY_APP_DIR)

    val settingsFile: Path get() = configDir.resolve("settings.json")
    val sessionFile: Path get() = configDir.resolve("session.json")
    val libraryDbFile: Path get() = dataDir.resolve("library.db")
    val coverDir: Path get() = cacheDir.resolve("covers")
    val logFile: Path get() = cacheDir.resolve("salt-shaker.log")
    val singleInstanceSocket: Path get() = dataDir.resolve("instance.sock")

    companion object {
        const val APP_DIR = "salt-shaker"
        const val LEGACY_APP_DIR = "salt-player"
    }

    fun ensureDirectories() {
        for (dir in listOf(configDir, dataDir, cacheDir, coverDir)) {
            java.nio.file.Files.createDirectories(dir)
        }
    }

    /** Default music directories used on first launch, limited to directories that exist. */
    fun defaultMusicDirs(): List<String> {
        val candidates = buildList<String> {
            val xdgMusic = env["XDG_MUSIC_DIR"]
            if (!xdgMusic.isNullOrBlank()) add(xdgMusic)
            add(home.resolve("Music").toString())
            add(home.resolve("音乐").toString())
        }
        return candidates.distinct().filter { java.nio.file.Files.isDirectory(Paths.get(it)) }
    }
}
