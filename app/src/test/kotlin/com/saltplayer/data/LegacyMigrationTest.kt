// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Corripo
package com.saltplayer.data

import com.saltplayer.core.LegacyMigration
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The project was renamed; a scanned library must survive that. */
class LegacyMigrationTest {

    private fun seed(dir: Path, files: Map<String, String>) {
        for ((name, content) in files) {
            val path = dir.resolve(name)
            Files.createDirectories(path.parent)
            Files.writeString(path, content)
        }
    }

    @Test
    fun `copies settings and library from the old directory once`() {
        val root = Files.createTempDirectory("migrate")
        val oldConfig = root.resolve("config/salt-player")
        val oldData = root.resolve("data/salt-player")
        val newConfig = root.resolve("config/salt-shaker")
        val newData = root.resolve("data/salt-shaker")
        seed(oldConfig, mapOf("settings.json" to """{"volume":0.4}"""))
        seed(oldData, mapOf("library.db" to "fake-db", "instance.sock" to "stale"))

        val result = LegacyMigration.migrateIfNeeded(newConfig, oldConfig, newData, oldData)

        assertTrue(result.configCopied)
        assertTrue(result.dataCopied)
        assertEquals("""{"volume":0.4}""", Files.readString(newConfig.resolve("settings.json")))
        assertEquals("fake-db", Files.readString(newData.resolve("library.db")))
        assertFalse(Files.exists(newData.resolve("instance.sock")), "stale socket must not be copied")
        // Originals stay in place so a rollback is possible.
        assertTrue(Files.exists(oldConfig.resolve("settings.json")))
    }

    @Test
    fun `does nothing when the new directories already exist`() {
        val root = Files.createTempDirectory("migrate2")
        val oldConfig = root.resolve("config/salt-player")
        val newConfig = root.resolve("config/salt-shaker")
        seed(oldConfig, mapOf("settings.json" to "old"))
        Files.createDirectories(newConfig)
        Files.writeString(newConfig.resolve("settings.json"), "new")

        val result = LegacyMigration.migrateIfNeeded(newConfig, oldConfig, root.resolve("data/new"), root.resolve("data/old"))

        assertFalse(result.didSomething)
        assertEquals("new", Files.readString(newConfig.resolve("settings.json")))
    }

    @Test
    fun `reports nothing to do on a fresh install`() {
        val root = Files.createTempDirectory("migrate3")
        val result = LegacyMigration.migrateIfNeeded(
            root.resolve("config/new"), root.resolve("config/old"),
            root.resolve("data/new"), root.resolve("data/old"),
        )
        assertFalse(result.didSomething)
        assertEquals("no previous installation found", result.skippedReason)
    }
}
