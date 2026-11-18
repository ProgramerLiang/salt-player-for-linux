// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Corripo
package com.saltplayer.playback

import com.saltplayer.core.Log
import com.saltplayer.data.db.LibraryDatabase
import com.saltplayer.data.prefs.EqualizerSettings
import com.saltplayer.data.prefs.ReplayGainMode
import com.saltplayer.data.prefs.Settings
import com.saltplayer.data.session.SessionState
import com.saltplayer.data.session.SessionStore
import com.saltplayer.domain.model.Track
import com.saltplayer.domain.queue.PlayMode
import com.saltplayer.domain.queue.PlayQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Immutable snapshot the UI renders. */
data class PlayerState(
    val track: Track? = null,
    val queue: List<Long> = emptyList(),
    val index: Int = -1,
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val mode: PlayMode = PlayMode.Sequential,
    val volume: Float = 0.8f,
    val rate: Float = 1.0f,
    val backendName: String = "not initialised",
    val backendId: String = "",
    val backendFeatures: List<String> = emptyList(),
    val error: String? = null,
)

/**
 * Playback coordinator.
 *
 * Every command and every backend event is funnelled through a single coroutine (an actor), so
 * queue state, engine calls and session persistence are serialised without locks. The UI only
 * observes [state] and sends commands.
 */
class PlayerController(
    private val registry: EngineRegistry,
    private val db: LibraryDatabase,
    private val sessionStore: SessionStore,
    private val settingsProvider: () -> Settings,
    private val scope: CoroutineScope,
    private val resolveTrack: (Long) -> Track?,
) : AutoCloseable {

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val queue = PlayQueue()
    private val commands = Channel<Command>(Channel.UNLIMITED)
    private var engine: AudioEngine? = null
    private var plannedNextId: Long? = null
    private var lastSessionSaveMs = 0L
    private var tickerJob: kotlinx.coroutines.Job? = null

    private sealed interface Command {
        data class PlayQueueCommand(val ids: List<Long>, val startIndex: Int) : Command
        data object Toggle : Command
        data object Play : Command
        data object Pause : Command
        data object Next : Command
        data object Previous : Command
        data class Seek(val positionMs: Long) : Command
        data class SetVolume(val volume: Float) : Command
        data class SetMode(val mode: PlayMode) : Command
        data class SetRate(val rate: Float) : Command
        data class SetEqualizer(val settings: EqualizerSettings) : Command
        data class SetOutputDevice(val deviceId: String) : Command
        data class AddNext(val trackId: Long) : Command
        data class Append(val trackIds: List<Long>) : Command
        data class RemoveAt(val index: Int) : Command
        data class Move(val from: Int, val to: Int) : Command
        data object ClearQueue : Command
        data class JumpTo(val index: Int) : Command
        data object RestoreSession : Command
        data object SaveSession : Command
        data object Shutdown : Command
    }

    init {
        scope.launch { processCommands() }
        scope.launch { startTicker() }
    }

    // --- public API -----------------------------------------------------

    fun playQueue(trackIds: List<Long>, startIndex: Int = 0) {
        commands.trySend(Command.PlayQueueCommand(trackIds, startIndex))
    }

    fun togglePlayPause() = commands.trySend(Command.Toggle).let { }
    fun play() = commands.trySend(Command.Play).let { }
    fun pause() = commands.trySend(Command.Pause).let { }
    fun next() = commands.trySend(Command.Next).let { }
    fun previous() = commands.trySend(Command.Previous).let { }
    fun seekTo(positionMs: Long) = commands.trySend(Command.Seek(positionMs)).let { }

    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        _state.update { it.copy(volume = clamped) }
        commands.trySend(Command.SetVolume(clamped))
    }

    fun setMode(mode: PlayMode) {
        _state.update { it.copy(mode = mode) }
        commands.trySend(Command.SetMode(mode))
    }

    fun cycleMode() = setMode(state.value.mode.next())

    fun setRate(rate: Float) {
        val clamped = rate.coerceIn(0.5f, 2f)
        _state.update { it.copy(rate = clamped) }
        commands.trySend(Command.SetRate(clamped))
    }

    fun setEqualizer(settings: EqualizerSettings) = commands.trySend(Command.SetEqualizer(settings)).let { }

    fun setOutputDevice(deviceId: String) = commands.trySend(Command.SetOutputDevice(deviceId)).let { }

    fun playNext(trackId: Long) = commands.trySend(Command.AddNext(trackId)).let { }

    fun appendToQueue(trackIds: List<Long>) = commands.trySend(Command.Append(trackIds)).let { }

    fun removeFromQueue(index: Int) = commands.trySend(Command.RemoveAt(index)).let { }

    fun moveInQueue(from: Int, to: Int) = commands.trySend(Command.Move(from, to)).let { }

    fun clearQueue() = commands.trySend(Command.ClearQueue).let { }

    fun jumpTo(index: Int) = commands.trySend(Command.JumpTo(index)).let { }

    fun restoreSession() {
        commands.trySend(Command.RestoreSession)
    }

    fun saveSession() {
        commands.trySend(Command.SaveSession)
    }

    /** Now-playing queue resolved to tracks, for the queue sheet. */
    fun queueTracks(): List<Track> = state.value.queue.mapNotNull(resolveTrack)

    // --- actor ----------------------------------------------------------

    private suspend fun processCommands() {
        for (command in commands) {
            try {
                when (command) {
                    is Command.PlayQueueCommand -> startQueue(command.ids, command.startIndex)
                    Command.Toggle -> if (_state.value.playing) doPause() else doResume()
                    Command.Play -> doResume()
                    Command.Pause -> doPause()
                    Command.Next -> doNext()
                    Command.Previous -> doPrevious()
                    is Command.Seek -> doSeek(command.positionMs)
                    is Command.SetVolume -> engine?.setVolume(command.volume)
                    is Command.SetMode -> {
                        queue.mode = command.mode
                        _state.update { it.copy(mode = command.mode) }
                        schedulePreload()
                    }

                    is Command.SetRate -> engine?.setPlaybackRate(command.rate.toDouble())
                    is Command.SetEqualizer -> engine?.applyEqualizer(command.settings)
                    is Command.SetOutputDevice -> engine?.setOutputDevice(command.deviceId)
                    is Command.AddNext -> {
                        queue.insertNext(command.trackId)
                        publishQueue()
                        schedulePreload()
                    }

                    is Command.Append -> {
                        queue.appendAll(command.trackIds)
                        publishQueue()
                        schedulePreload()
                    }

                    is Command.RemoveAt -> {
                        val wasCurrent = command.index == queue.currentIndex
                        queue.removeAt(command.index)
                        publishQueue()
                        if (queue.isEmpty) {
                            engine?.stop()
                            _state.update { it.copy(track = null, playing = false, positionMs = 0) }
                        } else if (wasCurrent) {
                            val id = queue.currentTrackId
                            val track = id?.let(resolveTrack)
                            if (track != null) load(track, 0)
                        }
                        schedulePreload()
                    }

                    is Command.Move -> {
                        queue.move(command.from, command.to)
                        publishQueue()
                    }

                    Command.ClearQueue -> {
                        queue.clear()
                        engine?.stop()
                        plannedNextId = null
                        engine?.setNextTrack(null)
                        _state.update { it.copy(queue = emptyList(), index = -1, track = null, playing = false, positionMs = 0) }
                        sessionStore.clear()
                    }

                    is Command.JumpTo -> {
                        val move = queue.jumpTo(command.index) ?: return
                        publishQueue()
                        move.trackId?.let(resolveTrack)?.let { load(it, 0) }
                    }

                    Command.RestoreSession -> restoreSessionInternal()
                    Command.SaveSession -> persistSession(force = true)
                    Command.Shutdown -> {
                        persistSession(force = true)
                        engine?.close()
                        engine = null
                    }
                }
            } catch (error: Throwable) {
                Log.error("Player", "command failed: $command", error)
                _state.update { it.copy(error = error.message ?: "playback error") }
            }
        }
    }

    private suspend fun ensureEngine(): AudioEngine? {
        engine?.let { return it }
        val settings = settingsProvider()
        val prepared = registry.create(settings.backendPreference, settings.lastWorkingBackend)
        if (prepared == null) {
            _state.update {
                it.copy(backendName = "no backend", backendFeatures = emptyList(), error = "No audio backend available")
            }
            return null
        }
        val created = prepared.engine
        created.setListener(object : EngineListener {
            override fun onPlayingChanged(playing: Boolean) {
                _state.update { it.copy(playing = playing) }
            }

            override fun onTrackTransition(path: String) = onEngineTransition(path)

            override fun onTrackEnded() = onEngineEnded()

            override fun onDurationChanged(durationMs: Long) {
                if (durationMs > 0) _state.update { it.copy(durationMs = durationMs) }
                val track = _state.value.track
                if (track != null && track.durationMs <= 0 && durationMs > 0) {
                    // Backfill durations for formats jaudiotagger could not measure.
                    runCatching { db.setDuration(track.id, durationMs) }
                }
            }

            override fun onError(message: String, detail: String?) {
                Log.warn("Player", "engine error: $message ${detail ?: ""}")
                _state.update { it.copy(error = message, playing = false) }
            }
        })
        created.setVolume(_state.value.volume)
        created.setPlaybackRate(_state.value.rate.toDouble())
        created.applyEqualizer(settingsProvider().equalizer)
        engine = created
        _state.update {
            it.copy(
                backendName = created.displayName,
                backendId = created.id,
                backendFeatures = created.capabilities.supportedFeatures(),
                error = null,
            )
        }
        return created
    }

    private suspend fun startQueue(ids: List<Long>, startIndex: Int) {
        if (ids.isEmpty()) return
        queue.mode = _state.value.mode
        queue.setItems(ids, startIndex.coerceIn(0, ids.lastIndex))
        publishQueue()
        val track = queue.currentTrackId?.let(resolveTrack)
        if (track == null) {
            _state.update { it.copy(error = "Track not in library") }
            return
        }
        load(track, 0)
    }

    private suspend fun load(track: Track, startPositionMs: Long) {
        val activeEngine = ensureEngine()
        if (activeEngine == null) {
            _state.update { it.copy(error = "No audio backend available", playing = false) }
            return
        }
        _state.update {
            it.copy(
                track = track,
                positionMs = startPositionMs,
                durationMs = track.durationMs,
                playing = true,
                error = null,
                index = queue.currentIndex,
            )
        }
        runCatching { db.recordPlay(track.id) }
        activeEngine.loadAndPlay(
            PlayRequest(
                path = track.path,
                startPositionMs = startPositionMs,
                replayGainDb = replayGainFor(track),
            )
        )
        schedulePreload()
        persistSession(force = true)
    }

    private fun replayGainFor(track: Track): Double? = when (settingsProvider().replayGain) {
        ReplayGainMode.Off -> null
        ReplayGainMode.Track -> track.replayGainTrackDb
        ReplayGainMode.Album -> track.replayGainAlbumDb ?: track.replayGainTrackDb
    }

    private fun schedulePreload() {
        val activeEngine = engine ?: return
        if (queue.isEmpty || _state.value.mode == PlayMode.Sequential && queue.currentIndex >= queue.size - 1) {
            plannedNextId = null
            activeEngine.setNextTrack(null)
            return
        }
        val nextId = queue.peekNext()
        plannedNextId = nextId
        val nextPath = nextId?.let(resolveTrack)?.path
        activeEngine.setNextTrack(nextPath)
    }

    private suspend fun doResume() {
        val activeEngine = ensureEngine() ?: return
        if (activeEngine.isPlaying()) return
        if (_state.value.track == null) {
            val first = resolveTrack(queue.currentTrackId ?: return) ?: return
            load(first, _state.value.positionMs)
            return
        }
        activeEngine.resume()
        _state.update { it.copy(playing = true) }
    }

    private fun doPause() {
        engine?.pause()
        _state.update { it.copy(playing = false) }
        persistSession(force = true)
    }

    private suspend fun doNext() {
        if (queue.isEmpty) return
        val move = queue.advance(autoAdvance = false)
        publishQueue()
        if (move.stopped) return
        move.trackId?.let(resolveTrack)?.let { load(it, 0) }
    }

    private suspend fun doPrevious() {
        if (queue.isEmpty) return
        // Restart the current track when the user is deep into it, like every other player.
        if (_state.value.positionMs > 3_000) {
            doSeek(0)
            return
        }
        val move = queue.rewind()
        publishQueue()
        move.trackId?.let(resolveTrack)?.let { load(it, 0) }
    }

    private fun doSeek(positionMs: Long) {
        val duration = _state.value.durationMs
        val clamped = positionMs.coerceIn(0, if (duration > 0) duration else positionMs)
        _state.update { it.copy(positionMs = clamped) }
        engine?.seekTo(clamped)
    }

    private fun onEngineTransition(path: String) {
        val planned = plannedNextId
        val move = planned?.let { queue.advanceTo(it) } ?: queue.advance(autoAdvance = true)
        val track = move.trackId?.let(resolveTrack)
        if (track == null) {
            _state.update { it.copy(error = "Track not in library: $path") }
            return
        }
        _state.update {
            it.copy(
                track = track,
                index = queue.currentIndex,
                positionMs = 0,
                durationMs = track.durationMs,
                playing = true,
            )
        }
        runCatching { db.recordPlay(track.id) }
        publishQueue()
        schedulePreload()
        persistSession(force = true)
    }

    private fun onEngineEnded() {
        val move = queue.advance(autoAdvance = true)
        if (move.stopped) {
            _state.update { it.copy(playing = false) }
            engine?.setNextTrack(null)
            persistSession(force = true)
            return
        }
        val track = move.trackId?.let(resolveTrack)
        if (track == null) {
            _state.update { it.copy(playing = false) }
            return
        }
        scope.launch { load(track, 0) }
    }

    private fun publishQueue() {
        _state.update { it.copy(queue = queue.items, index = queue.currentIndex) }
    }

    private fun restoreSessionInternal() {
        val session = sessionStore.load() ?: return
        if (session.queue.isEmpty()) return
        val valid = session.queue.filter { resolveTrack(it) != null }
        if (valid.isEmpty()) {
            sessionStore.clear()
            return
        }
        queue.mode = session.mode
        queue.setItems(valid, session.index.coerceIn(0, valid.lastIndex))
        val track = queue.currentTrackId?.let(resolveTrack)
        _state.update {
            it.copy(
                queue = queue.items,
                index = queue.currentIndex,
                mode = session.mode,
                volume = session.volume,
                track = track,
                positionMs = session.positionMs,
                durationMs = track?.durationMs ?: 0,
                playing = false,
            )
        }
    }

    private fun persistSession(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastSessionSaveMs < SESSION_SAVE_INTERVAL_MS) return
        lastSessionSaveMs = now
        val state = _state.value
        if (state.queue.isEmpty()) {
            sessionStore.clear()
            return
        }
        sessionStore.save(
            SessionState(
                queue = state.queue,
                index = state.index,
                positionMs = state.positionMs,
                mode = state.mode,
                volume = state.volume,
            )
        )
    }

    private suspend fun startTicker() {
        while (scope.isActive) {
            delay(TICK_MS)
            val activeEngine = engine ?: continue
            if (!_state.value.playing) continue
            val position = activeEngine.positionMs()
            val duration = activeEngine.durationMs()
            _state.update {
                it.copy(
                    positionMs = if (position > 0) position else it.positionMs,
                    durationMs = if (duration > 0) duration else it.durationMs,
                )
            }
            persistSession(force = false)
        }
    }

    override fun close() {
        commands.trySend(Command.Shutdown)
        commands.close()
    }

    private companion object {
        const val TICK_MS = 400L
        const val SESSION_SAVE_INTERVAL_MS = 10_000L
    }
}
