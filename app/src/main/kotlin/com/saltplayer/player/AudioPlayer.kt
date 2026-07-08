package com.saltplayer.player

import com.saltplayer.model.CoverArt
import com.saltplayer.model.PlaybackState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.sound.sampled.*

class AudioPlayer {

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var line: SourceDataLine? = null
    private var playbackJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile private var paused = false
    @Volatile private var stopped = false
    private var currentVolume: Float = 0.8f
    private var currentSongPath: String? = null

    private val lock = Any()

    fun loadAndPlay(
        songId: String,
        title: String,
        artist: String,
        album: String,
        coverArtBase64: String?,
        coverMime: String?,
        filePath: String,
        duration: Double
    ) {
        stop()
        synchronized(lock) {
            stopped = false
            paused = false
            currentSongPath = filePath

            _state.value = PlaybackState(
                songId = songId,
                title = title,
                artist = artist,
                album = album,
                coverArt = if (coverArtBase64 != null && coverMime != null)
                    CoverArt(coverMime, coverArtBase64) else null,
                duration = duration,
                position = 0.0,
                isPlaying = true
            )
        }

        playbackJob = scope.launch {
            try {
                playAudioFile(filePath)
            } catch (e: Exception) {
                System.err.println("salt-player: playback error: ${e.message}")
                synchronized(lock) {
                    _state.value = _state.value.copy(isPlaying = false)
                }
            }
        }
    }

    private suspend fun playAudioFile(path: String) {
        var audioStream: AudioInputStream? = null
        try {
            audioStream = AudioSystem.getAudioInputStream(File(path))
            val baseFormat = audioStream.format
            val decodedFormat = AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                baseFormat.sampleRate,
                16,
                baseFormat.channels,
                baseFormat.channels * 2,
                baseFormat.sampleRate,
                false
            )

            val decodedStream = AudioSystem.getAudioInputStream(decodedFormat, audioStream)
            val info = DataLine.Info(SourceDataLine::class.java, decodedFormat)

            if (!AudioSystem.isLineSupported(info))
                throw IllegalStateException("Audio line not supported")

            val dataLine = AudioSystem.getLine(info) as SourceDataLine
            synchronized(lock) { line = dataLine }
            dataLine.open(decodedFormat)
            applyLineVolume(dataLine, currentVolume)
            dataLine.start()

            val buffer = ByteArray(4096)
            var bytesRead: Int
            while (decodedStream.read(buffer).also { bytesRead = it } != -1) {
                if (stopped) break
                if (paused) {
                    while (paused && !stopped) delay(100)
                    if (stopped) break
                }
                dataLine.write(buffer, 0, bytesRead)
            }

            dataLine.drain()
            dataLine.stop()
            dataLine.close()
            synchronized(lock) { line = null }

            if (!stopped && !paused) {
                synchronized(lock) {
                    _state.value = _state.value.copy(isPlaying = false, position = 0.0)
                }
            }
        } catch (e: Exception) {
            synchronized(lock) {
                _state.value = _state.value.copy(isPlaying = false)
                line?.close()
                line = null
            }
            throw e
        } finally {
            try { audioStream?.close() } catch (_: Exception) {}
        }
    }

    fun play() {
        synchronized(lock) {
            paused = false
            _state.value = _state.value.copy(isPlaying = true)
        }
    }

    fun pause() {
        synchronized(lock) {
            paused = true
            _state.value = _state.value.copy(isPlaying = false)
        }
    }

    fun stop() {
        synchronized(lock) {
            stopped = true
            paused = false
            line?.close()
            line = null
            currentSongPath = null
            _state.value = PlaybackState()
        }
        playbackJob?.cancel()
    }

    fun setVolume(vol: Float) {
        currentVolume = vol.coerceIn(0f, 1f)
        synchronized(lock) {
            val l = line ?: return@synchronized
            applyLineVolume(l, currentVolume)
        }
    }

    private fun applyLineVolume(l: SourceDataLine, vol: Float) {
        try {
            if (l.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                val control = l.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
                val db = if (vol <= 0f) control.minimum
                else (20.0 * Math.log10(vol.toDouble())).toFloat()
                control.value = db.coerceIn(control.minimum, control.maximum)
            }
        } catch (_: Exception) {}
    }

    fun getPosition(): Double {
        val l = synchronized(lock) { line } ?: return _state.value.position
        return try { l.microsecondPosition / 1_000_000.0 }
            catch (_: Exception) { _state.value.position }
    }

    fun isPlaying(): Boolean = !paused && !stopped && line != null && line!!.isOpen

    fun destroy() {
        stop()
        scope.cancel()
    }
}
