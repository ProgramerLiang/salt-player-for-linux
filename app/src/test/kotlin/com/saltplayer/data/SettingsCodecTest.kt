// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Corripo
package com.saltplayer.data

import com.saltplayer.data.prefs.AppLanguage
import com.saltplayer.data.prefs.EqualizerSettings
import com.saltplayer.data.prefs.Settings
import com.saltplayer.data.prefs.SettingsCodec
import com.saltplayer.data.prefs.SettingsStore
import com.saltplayer.data.prefs.ThemeMode
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsCodecTest {

    @Test
    fun `round trips through the store`() {
        val dir = Files.createTempDirectory("salt-settings")
        val store = SettingsStore(dir.resolve("settings.json"))
        val settings = Settings(
            themeMode = ThemeMode.Dark,
            language = AppLanguage.English,
            musicDirs = listOf("/music/a", "/music/b"),
            volume = 0.42f,
            equalizer = EqualizerSettings(enabled = true, preset = "Rock", bands = List(10) { 1.5f }),
        )
        store.save(settings)
        val loaded = store.load()
        assertEquals(ThemeMode.Dark, loaded.themeMode)
        assertEquals(AppLanguage.English, loaded.language)
        assertEquals(listOf("/music/a", "/music/b"), loaded.musicDirs)
        assertEquals(0.42f, loaded.volume)
        assertTrue(loaded.equalizer.enabled)
        assertEquals(1.5f, loaded.equalizer.bands[3])
    }

    @Test
    fun `migrates a v1 document with a single musicDir and theme`() {
        val legacy = """
            {
              "version": 1,
              "musicDir": "/home/user/Music",
              "theme": "dark",
              "volume": 0.5
            }
        """.trimIndent()
        val migrated = SettingsCodec.parse(legacy)
        assertEquals(listOf("/home/user/Music"), migrated.musicDirs)
        assertEquals(ThemeMode.Dark, migrated.themeMode)
        assertEquals(0.5f, migrated.volume)
        assertEquals(Settings.CURRENT_VERSION, migrated.version)
    }

    @Test
    fun `unknown keys from a future version are ignored`() {
        val future = """
            {
              "version": 99,
              "volume": 0.7,
              "unknownFeature": {"nested": true},
              "musicDirs": ["/music"]
            }
        """.trimIndent()
        val parsed = SettingsCodec.parse(future)
        assertEquals(0.7f, parsed.volume)
        assertEquals(listOf("/music"), parsed.musicDirs)
    }

    @Test
    fun `invalid json falls back to defaults`() {
        val parsed = SettingsCodec.parse("{ this is not json")
        assertEquals(Settings().volume, parsed.volume)
        assertEquals(emptyList(), parsed.musicDirs)
    }

    @Test
    fun `out of range values are clamped`() {
        val parsed = SettingsCodec.parse(
            """{"volume": 5.0, "playbackSpeed": 9.0, "fontScale": 4.0, "sleepTimerMinutes": -20}"""
        )
        assertEquals(1f, parsed.volume)
        assertEquals(2f, parsed.playbackSpeed)
        assertEquals(1.6f, parsed.fontScale)
        assertEquals(0, parsed.sleepTimerMinutes)
    }

    @Test
    fun `equalizer bands are normalised to ten entries`() {
        val parsed = SettingsCodec.parse("""{"equalizer": {"enabled": true, "bands": [1.0, 2.0]}}""")
        assertEquals(10, parsed.equalizer.bands.size)
        assertEquals(1f, parsed.equalizer.bands[0])
        assertEquals(0f, parsed.equalizer.bands[9])
    }
}
