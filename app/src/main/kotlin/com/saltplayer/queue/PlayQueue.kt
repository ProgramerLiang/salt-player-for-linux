package com.saltplayer.queue

import com.saltplayer.model.PlayMode
import com.saltplayer.model.Song

class PlayQueue {
    private var songs: List<Song> = emptyList()
    private var currentIndex: Int = -1
    private var mode: PlayMode = PlayMode.SEQUENTIAL
    private val historyIndices: MutableList<Int> = mutableListOf()

    fun setQueue(list: List<Song>, startIndex: Int) {
        songs = list
        currentIndex = startIndex.coerceIn(0, (songs.size - 1).coerceAtLeast(0))
        historyIndices.clear()
    }

    fun current(): Song? = songs.getOrNull(currentIndex)

    fun next(): Song? {
        if (songs.isEmpty()) return null
        historyIndices.add(currentIndex)
        currentIndex = when (mode) {
            PlayMode.SEQUENTIAL -> (currentIndex + 1) % songs.size
            PlayMode.SHUFFLE -> {
                val pool = songs.indices.filter { it != currentIndex }
                if (pool.isEmpty()) 0 else pool.random()
            }
            PlayMode.REPEAT_ONE -> currentIndex
            PlayMode.REPEAT_ALL -> (currentIndex + 1) % songs.size
        }
        return current()
    }

    fun prev(): Song? {
        if (songs.isEmpty()) return null
        currentIndex = if (historyIndices.isNotEmpty()) {
            historyIndices.removeLast()
        } else {
            (currentIndex - 1 + songs.size) % songs.size
        }
        return current()
    }

    fun shuffleRemaining() {
        // TODO: Fisher-Yates on remaining
    }

    fun setMode(m: PlayMode) { mode = m }
    fun getMode(): PlayMode = mode

    fun isEmpty(): Boolean = songs.isEmpty()
    fun size(): Int = songs.size
    fun getSongs(): List<Song> = songs
    fun getCurrentIndex(): Int = currentIndex
}
