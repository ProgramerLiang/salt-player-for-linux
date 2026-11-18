// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Corripo
package com.saltplayer.data.prefs

import com.saltplayer.core.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Reads and writes `settings.json` (XDG config dir).
 *
 * The on-disk format is versioned: [migrate] upgrades older documents before they are decoded,
 * and decoding ignores unknown keys so a newer file written by a future version still loads.
 */
class SettingsStore(private val file: Path) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    fun load(): Settings {
        if (!Files.exists(file)) return Settings().sanitised()
        return runCatching {
            val text = Files.readString(file)
            SettingsCodec.parse(text, json)
        }.getOrElse { error ->
            Log.warn("Settings", "failed to read $file, falling back to defaults", error)
            Settings().sanitised()
        }
    }

    fun save(settings: Settings) {
        runCatching {
            Files.createDirectories(file.parent)
            val temp = file.resolveSibling(file.fileName.toString() + ".tmp")
            Files.writeString(temp, json.encodeToString(Settings.serializer(), settings.sanitised()))
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
        }.onFailure { Log.warn("Settings", "failed to save $file", it) }
    }
}

/** Pure part of settings persistence, split out so migration is unit-testable. */
object SettingsCodec {

    fun parse(text: String, json: Json = defaultJson): Settings {
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: return Settings().sanitised()
        val migrated = migrate(root)
        return runCatching { json.decodeFromJsonElement(Settings.serializer(), migrated).sanitised() }
            .getOrElse { Settings().sanitised() }
    }

    /**
     * Version upgrades:
     * * v1 (single `musicDir` string, `theme` enum) → v2 (`musicDirs` list, `themeMode`).
     */
    fun migrate(root: JsonObject): JsonObject {
        var result = root
        val version = result["version"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1
        if (version < 2) {
            result = buildJsonObject {
                result.forEach { (key, value) ->
                    when (key) {
                        "musicDir" -> {
                            val dir = value.jsonPrimitive.content
                            if (dir.isNotBlank()) {
                                put("musicDirs", buildJsonArray { add(JsonPrimitive(dir)) })
                            }
                        }

                        "theme" -> {
                            val mode = value.jsonPrimitive.content.lowercase()
                            put(
                                "themeMode",
                                JsonPrimitive(
                                    when (mode) {
                                        "dark" -> ThemeMode.Dark.name
                                        "light" -> ThemeMode.Light.name
                                        else -> ThemeMode.System.name
                                    }
                                )
                            )
                        }

                        else -> put(key, value)
                    }
                }
                put("version", JsonPrimitive(Settings.CURRENT_VERSION))
            }
        }
        return result
    }

    val defaultJson: Json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }
}
