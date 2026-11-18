// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Corripo
package com.saltplayer.domain.queue

import kotlin.random.Random

/**
 * Play modes mirroring Salt Player's Android UI.
 *
 * [Sequential] stops when the queue ends, [RepeatOne] keeps the current track,
 * [RepeatAll] wraps around, [Shuffle] picks a random track every time (may repeat),
 * and [ShuffleNoRepeat] walks a shuffled permutation so nothing repeats until the whole
 * queue has been played.
 */
enum class PlayMode {
    Sequential,
    RepeatOne,
    RepeatAll,
    Shuffle,
    ShuffleNoRepeat;

    val isShuffle: Boolean get() = this == Shuffle || this == ShuffleNoRepeat

    fun next(): PlayMode = entries[(ordinal + 1) % entries.size]
}

/** Result of an [PlayQueue.advance] / [PlayQueue.rewind] call. */
data class QueueMove(
    val index: Int,
    val trackId: Long?,
    /** True when playback should stop because the queue ran off its end. */
    val stopped: Boolean = false,
    /** True when the queue wrapped around from the last item to the first. */
    val wrapped: Boolean = false,
)

/**
 * The playback queue. Deliberately free of Compose, coroutines and audio concerns so the
 * ordering rules (including shuffling) can be unit-tested deterministically.
 *
 * Track ids are opaque here; the controller resolves them against the library.
 */
class PlayQueue(
    items: List<Long> = emptyList(),
    startIndex: Int = 0,
    mode: PlayMode = PlayMode.Sequential,
    private val random: Random = Random.Default,
) {
    private val entries = items.toMutableList()

    var mode: PlayMode = mode
        set(value) {
            field = value
            rebuildShuffleOrder()
        }

    /** Shuffled permutation of [entries] indices; only meaningful for shuffle modes. */
    private var shuffleOrder: MutableList<Int> = mutableListOf()
    private var shufflePos: Int = -1

    var currentIndex: Int = when {
        entries.isEmpty() -> -1
        startIndex in entries.indices -> startIndex
        else -> 0
    }
        private set

    val items: List<Long> get() = entries
    val size: Int get() = entries.size
    val isEmpty: Boolean get() = entries.isEmpty()
    val currentTrackId: Long? get() = entries.getOrNull(currentIndex)

    init {
        rebuildShuffleOrder()
    }

    /** Replaces the whole queue and starts at [startIndex]. */
    fun setItems(newItems: List<Long>, startIndex: Int = 0) {
        entries.clear()
        entries.addAll(newItems)
        currentIndex = when {
            entries.isEmpty() -> -1
            startIndex in entries.indices -> startIndex
            else -> 0
        }
        rebuildShuffleOrder()
    }

    fun jumpTo(index: Int): QueueMove? {
        if (index !in entries.indices) return null
        currentIndex = index
        syncShufflePosToCurrent()
        return QueueMove(index, entries[index])
    }

    /**
     * Commits an already-decided transition (used by gapless playback, where the backend has
     * started the preloaded track and the shuffle walk must not pick another one).
     */
    fun advanceTo(trackId: Long): QueueMove? {
        val index = entries.indexOf(trackId)
        if (index < 0) return null
        currentIndex = index
        syncShufflePosToCurrent()
        return QueueMove(index, trackId)
    }

    fun jumpToTrack(trackId: Long): QueueMove? {
        val index = entries.indexOf(trackId)
        return if (index >= 0) jumpTo(index) else null
    }

    /** Track that will play next if [advance] is called, honouring the play mode. */
    fun peekNext(): Long? = nextIndex(autoAdvance = false, peekOnly = true)?.let { entries[it] }

    /**
     * Moves to the next track. [autoAdvance] is true when the current track finished on its
     * own; in [PlayMode.Sequential] and at the end of the queue that stops playback.
     */
    fun advance(autoAdvance: Boolean = false): QueueMove {
        if (entries.isEmpty()) return QueueMove(-1, null, stopped = true)
        if (mode == PlayMode.RepeatOne && autoAdvance) {
            return QueueMove(currentIndex, entries[currentIndex])
        }
        val target = nextIndex(autoAdvance = autoAdvance, peekOnly = false)
            ?: return QueueMove(currentIndex, entries[currentIndex], stopped = true)
        val wrapped = target <= currentIndex && !mode.isShuffle || (mode.isShuffle && shufflePos == 0)
        currentIndex = target
        return QueueMove(target, entries[target], wrapped = wrapped)
    }

    /** Moves to the previous track; wraps to the end when already at the start. */
    fun rewind(): QueueMove {
        if (entries.isEmpty()) return QueueMove(-1, null, stopped = true)
        val target = when {
            mode.isShuffle && shuffleOrder.isNotEmpty() -> {
                if (shufflePos > 0) {
                    shufflePos--
                    shuffleOrder[shufflePos]
                } else {
                    shufflePos = shuffleOrder.lastIndex
                    shuffleOrder[shufflePos]
                }
            }

            currentIndex > 0 -> currentIndex - 1
            else -> entries.lastIndex
        }
        currentIndex = target
        syncShufflePosToCurrent()
        return QueueMove(target, entries[target], wrapped = target == entries.lastIndex)
    }

    /** Inserts [trackId] to play right after the current track ("play next"). */
    fun insertNext(trackId: Long): Int {
        if (entries.isEmpty()) {
            entries.add(trackId)
            currentIndex = 0
            rebuildShuffleOrder()
            return 1
        }
        val at = (currentIndex + 1).coerceIn(0, entries.size)
        entries.add(at, trackId)
        // Keep the shuffle walk in sync: the new track must be the next shuffle step.
        val previousOrder = shuffleOrder.toList()
        rebuildShuffleOrder()
        if (mode.isShuffle) {
            val pos = shuffleOrder.indexOf(at)
            if (pos >= 0) {
                shuffleOrder.removeAt(pos)
                val insertAt = (shufflePos + 1).coerceIn(0, shuffleOrder.size)
                shuffleOrder.add(insertAt, at)
            }
            if (previousOrder.isEmpty()) shufflePos = 0
        }
        return entries.size
    }

    fun append(trackId: Long): Int {
        entries.add(trackId)
        if (currentIndex < 0) currentIndex = 0
        rebuildShuffleOrder()
        return entries.size
    }

    fun appendAll(trackIds: List<Long>): Int {
        if (trackIds.isEmpty()) return entries.size
        entries.addAll(trackIds)
        if (currentIndex < 0) currentIndex = 0
        rebuildShuffleOrder()
        return entries.size
    }

    /**
     * Removes the entry at [index]. When the removed entry is the current one the queue keeps
     * pointing at the same slot (which is now the following track) so playback continues.
     */
    fun removeAt(index: Int): Boolean {
        if (index !in entries.indices) return false
        entries.removeAt(index)
        if (entries.isEmpty()) {
            currentIndex = -1
        } else if (index < currentIndex) {
            currentIndex--
        } else if (index == currentIndex) {
            currentIndex = currentIndex.coerceAtMost(entries.lastIndex)
        }
        rebuildShuffleOrder()
        return true
    }

    fun move(from: Int, to: Int): Boolean {
        if (from !in entries.indices || to !in entries.indices || from == to) return false
        val current = entries.getOrNull(currentIndex)
        val item = entries.removeAt(from)
        entries.add(to, item)
        if (current != null) currentIndex = entries.indexOf(current)
        rebuildShuffleOrder()
        return true
    }

    fun clear() {
        entries.clear()
        currentIndex = -1
        shuffleOrder.clear()
        shufflePos = -1
    }

    // --- internals -------------------------------------------------------

    private fun nextIndex(autoAdvance: Boolean, peekOnly: Boolean): Int? {
        return when (mode) {
            PlayMode.Shuffle -> {
                if (entries.size == 1) return currentIndex
                var candidate = currentIndex
                var guard = 0
                while (candidate == currentIndex && guard++ < 32) {
                    candidate = random.nextInt(entries.size)
                }
                candidate
            }

            PlayMode.ShuffleNoRepeat -> {
                if (shuffleOrder.size <= 1) return currentIndex
                val nextPos = shufflePos + 1
                if (nextPos > shuffleOrder.lastIndex) {
                    if (!autoAdvance) {
                        // Manual next: start a fresh shuffled round.
                        if (!peekOnly) {
                            reshuffleKeepingCurrentFirst()
                            shufflePos = 1
                        }
                        shuffleOrder.getOrNull(1) ?: shuffleOrder.firstOrNull()
                    } else {
                        null
                    }
                } else {
                    if (!peekOnly) shufflePos = nextPos
                    shuffleOrder[nextPos]
                }
            }

            PlayMode.RepeatOne, PlayMode.RepeatAll -> {
                if (currentIndex >= entries.lastIndex) 0 else currentIndex + 1
            }

            PlayMode.Sequential -> {
                if (currentIndex >= entries.lastIndex) {
                    if (autoAdvance) null else 0
                } else {
                    currentIndex + 1
                }
            }
        }
    }

    private fun rebuildShuffleOrder() {
        val previousCurrent = currentIndex
        shuffleOrder = entries.indices.toMutableList()
        shuffleOrder.shuffle(random)
        if (previousCurrent in shuffleOrder) {
            shuffleOrder.remove(previousCurrent)
            shuffleOrder.add(0, previousCurrent)
            shufflePos = 0
        } else {
            shufflePos = if (shuffleOrder.isEmpty()) -1 else 0
        }
    }

    private fun reshuffleKeepingCurrentFirst() {
        val current = currentIndex
        shuffleOrder = entries.indices.toMutableList()
        shuffleOrder.shuffle(random)
        if (current in shuffleOrder) {
            shuffleOrder.remove(current)
            shuffleOrder.add(0, current)
        }
        shufflePos = 0
    }

    private fun syncShufflePosToCurrent() {
        if (!mode.isShuffle) return
        val pos = shuffleOrder.indexOf(currentIndex)
        if (pos >= 0) shufflePos = pos
    }
}
