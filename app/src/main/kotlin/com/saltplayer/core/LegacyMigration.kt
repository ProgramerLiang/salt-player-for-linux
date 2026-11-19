// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Corripo
package com.saltplayer.core

import java.nio.file.Files
import java.nio.file.Path

/**
 * Carries a previous installation's data over to the renamed application directory.
 *
 * The application used to store everything under `salt-player`; it is now `salt-shaker`.
 * Users who already scanned a library must not lose it, so on first start the old settings and
 * database are *copied* (never moved) when the new directory does not exist yet. The old files
 * are left untouched, which keeps a downgrade and a manual rollback possible.
 */
object LegacyMigration {

    data class Result(
        val configCopied: Boolean = false,
        val dataCopied: Boolean = false,
        val bytesCopied: Long = 0,
        val skippedReason: String? = null,
    ) {
        val didSomething: Boolean get() = configCopied || dataCopied
    }

    /**
     * Copies [legacyConfigDir] to [configDir] and [legacyDataDir] to [dataDir] when the target
     * is absent. Files are copied, not linked, so both installations stay independent.
     */
    fun migrateIfNeeded(
        configDir: Path,
        legacyConfigDir: Path,
        dataDir: Path,
        legacyDataDir: Path,
    ): Result {
        if (Files.exists(configDir) || Files.exists(dataDir)) {
            return Result(skippedReason = "target directory already exists")
        }
        if (!Files.exists(legacyConfigDir) && !Files.exists(legacyDataDir)) {
            return Result(skippedReason = "no previous installation found")
        }
        var configCopied = false
        var dataCopied = false
        var bytes = 0L
        runCatching {
            if (Files.isDirectory(legacyConfigDir)) {
                bytes += copyTree(legacyConfigDir, configDir)
                configCopied = true
            }
            // The library database is what users would hate to lose, so it is migrated too.
            if (Files.isDirectory(legacyDataDir)) {
                bytes += copyTree(legacyDataDir, dataDir, skipNames = setOf("instance.sock"))
                dataCopied = true
            }
        }.onFailure { Log.warn("Migration", "legacy data migration failed", it) }
        if (configCopied || dataCopied) {
            Log.info("Migration", "migrated salt-player -> salt-shaker ($bytes bytes)")
        }
        return Result(configCopied, dataCopied, bytes, null)
    }

    private fun copyTree(source: Path, target: Path, skipNames: Set<String> = emptySet()): Long {
        var copied = 0L
        Files.walk(source).use { stream ->
            stream.forEach { path ->
                val relative = source.relativize(path)
                if (relative.toString() in skipNames) return@forEach
                val destination = target.resolve(relative.toString())
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination)
                } else {
                    Files.createDirectories(destination.parent)
                    Files.copy(path, destination)
                    copied += runCatching { Files.size(destination) }.getOrDefault(0L)
                }
            }
        }
        return copied
    }
}
