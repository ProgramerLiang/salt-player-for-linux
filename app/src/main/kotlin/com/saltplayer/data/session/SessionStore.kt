// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Corripo
package com.saltplayer.data.session

import com.saltplayer.core.Log
import com.saltplayer.domain.queue.PlayMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** Playback state persisted on exit so the next launch continues where the user left off. */
@Serializable
data class SessionState(
    val queue: List<Long> = emptyList(),
    val index: Int = -1,
    val positionMs: Long = 0,
    val mode: PlayMode = PlayMode.Sequential,
    val volume: Float = 0.8f,
    val updatedAtMs: Long = 0,
)

class SessionStore(private val file: Path) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    fun load(): SessionState? {
        if (!Files.exists(file)) return null
        return runCatching { json.decodeFromString(SessionState.serializer(), Files.readString(file)) }
            .onFailure { Log.warn("Session", "cannot read $file", it) }
            .getOrNull()
    }

    fun save(state: SessionState) {
        runCatching {
            Files.createDirectories(file.parent)
            val temp = file.resolveSibling(file.fileName.toString() + ".tmp")
            Files.writeString(temp, json.encodeToString(SessionState.serializer(), state.copy(updatedAtMs = System.currentTimeMillis())))
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
        }.onFailure { Log.warn("Session", "cannot write $file", it) }
    }

    fun clear() {
        runCatching { Files.deleteIfExists(file) }
    }
}
