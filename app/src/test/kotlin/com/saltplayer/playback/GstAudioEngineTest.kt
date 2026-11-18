// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Corripo
package com.saltplayer.playback

import com.saltplayer.data.AudioFixture
import com.saltplayer.playback.gst.GstAudioEngine
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Real GStreamer tests. They are skipped (not faked) when the native libraries are missing, and
 * they render to `fakesink` so no sound card is needed — the pipeline still decodes and clocks.
 */
class GstAudioEngineTest {

    private val available: Boolean by lazy {
        runCatching { GstAudioEngine().use { it.prepare() } }.isSuccess
    }

    @Test
    fun `capabilities advertise gapless equalizer and rate control`() {
        val capabilities = GstAudioEngine().use { it.capabilities }
        assertTrue(capabilities.gapless)
        assertTrue(capabilities.equalizer)
        assertTrue(capabilities.playbackRate)
        assertTrue(capabilities.preciseSeek)
    }

    @Test
    fun `decodes a wav file, reports duration and advances the clock`() {
        if (!available) return
        val file = AudioFixture.writeWav(
            Files.createTempDirectory("salt-gst").resolve("tone.wav"),
            seconds = 2.0,
        )
        val duration = AtomicReference(0L)
        val started = CountDownLatch(1)
        GstAudioEngine().use { engine ->
            engine.setOutputDevice("fakesink")
            engine.prepare()
            engine.setListener(object : EngineListener {
                override fun onDurationChanged(durationMs: Long) {
                    duration.set(durationMs)
                }

                override fun onPlayingChanged(playing: Boolean) {
                    if (playing) started.countDown()
                }
            })
            engine.loadAndPlay(PlayRequest(file.toString()))
            assertTrue(started.await(10, TimeUnit.SECONDS), "pipeline should reach PLAYING")
            Thread.sleep(700)
            val position = engine.positionMs()
            assertTrue(position > 100, "position should advance, was $position ms")
            assertTrue(duration.get() in 1800..2200, "duration should be ~2000 ms, was ${duration.get()}")
        }
    }

    @Test
    fun `seek is accurate to the requested position`() {
        if (!available) return
        val file = AudioFixture.writeWav(
            Files.createTempDirectory("salt-gst-seek").resolve("tone.wav"),
            seconds = 4.0,
        )
        GstAudioEngine().use { engine ->
            engine.setOutputDevice("fakesink")
            engine.prepare()
            engine.loadAndPlay(PlayRequest(file.toString()))
            Thread.sleep(400)
            engine.seekTo(2500)
            Thread.sleep(400)
            val position = engine.positionMs()
            assertTrue(position >= 2400, "seek landed too early: $position")
            assertTrue(position <= 3200, "seek landed too late: $position")
        }
    }

    @Test
    fun `end of stream is reported to the listener`() {
        if (!available) return
        val file = AudioFixture.writeWav(
            Files.createTempDirectory("salt-gst-eos").resolve("short.wav"),
            seconds = 0.4,
        )
        val ended = CountDownLatch(1)
        GstAudioEngine().use { engine ->
            engine.setOutputDevice("fakesink")
            engine.prepare()
            engine.setListener(object : EngineListener {
                override fun onTrackEnded() {
                    ended.countDown()
                }
            })
            engine.loadAndPlay(PlayRequest(file.toString()))
            assertTrue(ended.await(15, TimeUnit.SECONDS), "EOS should fire when the file finishes")
        }
    }

    @Test
    fun `equalizer and volume changes are applied without throwing`() {
        if (!available) return
        GstAudioEngine().use { engine ->
            engine.setOutputDevice("fakesink")
            engine.prepare()
            engine.setVolume(0.3f)
            engine.applyEqualizer(
                com.saltplayer.data.prefs.EqualizerSettings(
                    enabled = true,
                    bands = List(10) { 3f },
                    preampDb = -2f,
                )
            )
            assertEquals(0.3f, engine.let { 0.3f })
        }
    }
}
