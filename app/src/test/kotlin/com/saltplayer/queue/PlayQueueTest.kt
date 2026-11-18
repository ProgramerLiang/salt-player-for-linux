// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Corripo
package com.saltplayer.queue

import com.saltplayer.domain.queue.PlayMode
import com.saltplayer.domain.queue.PlayQueue
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayQueueTest {

    private fun queue(
        size: Int = 5,
        mode: PlayMode = PlayMode.Sequential,
        start: Int = 0,
        seed: Int = 42,
    ) = PlayQueue((1L..size).toList(), start, mode, Random(seed))

    @Test
    fun `sequential advance stops at the end when the track finished`() {
        val q = queue(mode = PlayMode.Sequential)
        assertEquals(2L, q.advance().trackId)
        assertEquals(3L, q.advance().trackId)
        assertEquals(4L, q.advance().trackId)
        assertEquals(5L, q.advance().trackId)
        val last = q.advance(autoAdvance = true)
        assertTrue(last.stopped)
        assertEquals(5L, q.currentTrackId)
    }

    @Test
    fun `manual next wraps around in sequential mode`() {
        val q = queue(size = 2, mode = PlayMode.Sequential, start = 1)
        val move = q.advance(autoAdvance = false)
        assertFalse(move.stopped)
        assertEquals(1L, move.trackId)
        assertTrue(move.wrapped)
    }

    @Test
    fun `repeat all always wraps and never stops`() {
        val q = queue(size = 3, mode = PlayMode.RepeatAll, start = 2)
        val move = q.advance(autoAdvance = true)
        assertFalse(move.stopped)
        assertEquals(1L, move.trackId)
        assertTrue(move.wrapped)
    }

    @Test
    fun `repeat one keeps the same track on natural end but next skips`() {
        val q = queue(mode = PlayMode.RepeatOne, start = 1)
        val auto = q.advance(autoAdvance = true)
        assertEquals(2L, auto.trackId)
        val manual = q.advance(autoAdvance = false)
        assertEquals(3L, manual.trackId)
    }

    @Test
    fun `shuffle never returns the current track twice in a row`() {
        val q = queue(size = 6, mode = PlayMode.Shuffle)
        repeat(50) {
            val before = q.currentTrackId
            val move = q.advance()
            assertTrue(move.trackId != before)
        }
    }

    @Test
    fun `shuffle without repeat plays every track once before repeating`() {
        val q = queue(size = 5, mode = PlayMode.ShuffleNoRepeat)
        val played = mutableListOf(q.currentTrackId!!)
        repeat(4) {
            val move = q.advance(autoAdvance = true)
            assertFalse(move.stopped)
            played.add(move.trackId!!)
        }
        assertEquals(5, played.toSet().size, "all tracks should be visited exactly once: $played")
        val afterEnd = q.advance(autoAdvance = true)
        assertTrue(afterEnd.stopped, "after a full shuffled round, natural advance stops")
    }

    @Test
    fun `rewind wraps to the last track from the first`() {
        val q = queue(size = 4, start = 0)
        val move = q.rewind()
        assertEquals(4L, move.trackId)
    }

    @Test
    fun `play next inserts right after the current track`() {
        val q = queue(size = 3, start = 0)
        q.insertNext(99L)
        assertEquals(listOf(1L, 99L, 2L, 3L), q.items)
        assertEquals(99L, q.advance().trackId)
    }

    @Test
    fun `removing an earlier entry keeps the current track playing`() {
        val q = queue(size = 4, start = 2)
        assertEquals(3L, q.currentTrackId)
        q.removeAt(0)
        assertEquals(3L, q.currentTrackId)
        assertEquals(1, q.currentIndex, "removing an earlier item shifts the index down by one")
    }

    @Test
    fun `removing the current entry moves to the following one`() {
        val q = queue(size = 4, start = 1)
        q.removeAt(1)
        assertEquals(3L, q.currentTrackId)
    }

    @Test
    fun `moving entries keeps the current track selected`() {
        val q = queue(size = 4, start = 2)
        val current = q.currentTrackId
        q.move(0, 3)
        assertEquals(current, q.currentTrackId)
        assertEquals(listOf(2L, 3L, 4L, 1L), q.items)
    }

    @Test
    fun `advanceTo commits a gapless transition without reshuffling`() {
        val q = queue(size = 4, mode = PlayMode.ShuffleNoRepeat)
        val move = q.advanceTo(3L)
        assertEquals(3L, move?.trackId)
        assertEquals(3L, q.currentTrackId)
        assertEquals(2, q.currentIndex)
    }

    @Test
    fun `empty queue reports stopped moves`() {
        val q = PlayQueue()
        assertTrue(q.advance().stopped)
        assertTrue(q.rewind().stopped)
        assertNull(q.currentTrackId)
    }

    @Test
    fun `clear empties the queue and resets the index`() {
        val q = queue(size = 3)
        q.clear()
        assertEquals(0, q.size)
        assertEquals(-1, q.currentIndex)
    }
}
