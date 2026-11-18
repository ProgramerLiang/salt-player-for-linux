// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Corripo
package com.saltplayer.data

import com.saltplayer.core.AppPaths
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppPathsTest {

    @Test
    fun `honours XDG environment variables`() {
        val paths = AppPaths(mapOf("HOME" to "/home/tester", "XDG_CONFIG_HOME" to "/cfg", "XDG_DATA_HOME" to "/data", "XDG_CACHE_HOME" to "/cache"))
        assertEquals(Paths.get("/cfg/salt-player"), paths.configDir)
        assertEquals(Paths.get("/data/salt-player"), paths.dataDir)
        assertEquals(Paths.get("/cache/salt-player"), paths.cacheDir)
        assertEquals(Paths.get("/cfg/salt-player/settings.json"), paths.settingsFile)
        assertEquals(Paths.get("/data/salt-player/library.db"), paths.libraryDbFile)
    }

    @Test
    fun `falls back to home dot directories and never writes to the home root`() {
        val paths = AppPaths(mapOf("HOME" to "/home/tester"))
        assertEquals(Paths.get("/home/tester/.config/salt-player"), paths.configDir)
        assertEquals(Paths.get("/home/tester/.local/share/salt-player"), paths.dataDir)
        assertEquals(Paths.get("/home/tester/.cache/salt-player"), paths.cacheDir)
        assertTrue(paths.logFile.startsWith(Paths.get("/home/tester/.cache/salt-player")))
    }

    @Test
    fun `default music directories only include existing paths`() {
        val paths = AppPaths(mapOf("HOME" to "/home/tester"))
        val dirs = paths.defaultMusicDirs()
        assertTrue(dirs.all { java.nio.file.Files.isDirectory(Paths.get(it)) })
    }
}
