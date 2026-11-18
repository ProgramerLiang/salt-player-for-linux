// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Corripo
package com.saltplayer.playback

import com.saltplayer.core.Log
import com.saltplayer.data.prefs.BackendPreference
import com.saltplayer.playback.gst.GstAudioEngine
import com.saltplayer.playback.mpv.MpvAudioEngine
import com.saltplayer.playback.sound.JavaSoundAudioEngine

/**
 * Probes every backend once, in documented priority order, and hands out a prepared engine.
 *
 * Probing is a real availability check: GStreamer is initialised, libmpv is dlopen'ed and Java
 * Sound is asked for its mixers. A backend that fails to prepare is reported with the reason
 * and an install hint instead of being silently replaced.
 */
class EngineRegistry {

    data class Probe(val descriptor: EngineDescriptor, val factory: () -> AudioEngine)

    private val probes = listOf(
        Probe(
            EngineDescriptor(
                id = GstAudioEngine.ID,
                displayName = "GStreamer",
                available = false,
                capabilities = GstAudioEngine().capabilities,
                detail = "",
                installHint = "Debian/Ubuntu: sudo apt install gstreamer1.0-plugins-base gstreamer1.0-plugins-good " +
                    "gstreamer1.0-plugins-bad gstreamer1.0-plugins-ugly gstreamer1.0-libav · " +
                    "Fedora: sudo dnf install gstreamer1-plugins-{base,good,bad-free,ugly-free} gstreamer1-libav · " +
                    "Arch: sudo pacman -S gst-plugins-base gst-plugins-good gst-plugins-bad gst-plugins-ugly gst-libav",
            ),
            ::GstAudioEngine,
        ),
        Probe(
            EngineDescriptor(
                id = MpvAudioEngine.ID,
                displayName = "libmpv",
                available = false,
                capabilities = MpvAudioEngine().capabilities,
                detail = "",
                installHint = "Debian/Ubuntu: sudo apt install libmpv2 · Fedora: sudo dnf install mpv-libs · " +
                    "Arch: sudo pacman -S mpv",
            ),
            ::MpvAudioEngine,
        ),
        Probe(
            EngineDescriptor(
                id = JavaSoundAudioEngine.ID,
                displayName = "Java Sound (fallback)",
                available = true,
                capabilities = JavaSoundAudioEngine().capabilities,
                detail = "Built into the JVM; WAV/AIFF/AU only. Gapless, EQ and rate control are unavailable.",
            ),
            ::JavaSoundAudioEngine,
        ),
    )

    @Volatile
    private var cached: List<EngineDescriptor>? = null

    /** Runs the probes (once) and returns every backend with its availability. */
    fun probeAll(): List<EngineDescriptor> {
        cached?.let { return it }
        val result = probes.map { probe ->
            val engine = probe.factory()
            val descriptor = try {
                engine.prepare()
                probe.descriptor.copy(available = true, detail = describeAvailable(engine))
            } catch (error: Throwable) {
                probe.descriptor.copy(
                    available = false,
                    detail = error.message ?: error::class.java.simpleName,
                )
            } finally {
                runCatching { engine.close() }
            }
            descriptor
        }
        cached = result
        return result
    }

    private fun describeAvailable(engine: AudioEngine): String = when (engine.id) {
        GstAudioEngine.ID -> "Native GStreamer libraries loaded; gapless, EQ, rate and device selection enabled."
        MpvAudioEngine.ID -> "libmpv loaded in-process; gapless playlist transitions and af equalizer enabled."
        else -> "JVM built-in audio."
    }

    /**
     * Creates and prepares the engine to use: the pinned backend when it works, otherwise the
     * first available backend in priority order. Returns null only when nothing works at all.
     */
    fun create(preference: BackendPreference, lastWorking: String? = null): PreparedEngine? {
        val order = when (preference) {
            BackendPreference.GStreamer -> listOf(GstAudioEngine.ID)
            BackendPreference.LibMpv -> listOf(MpvAudioEngine.ID)
            BackendPreference.JavaSound -> listOf(JavaSoundAudioEngine.ID)
            BackendPreference.Auto -> buildList {
                lastWorking?.let { add(it) }
                add(GstAudioEngine.ID)
                add(MpvAudioEngine.ID)
                add(JavaSoundAudioEngine.ID)
            }.distinct()
        }
        for (id in order) {
            val probe = probes.firstOrNull { it.descriptor.id == id } ?: continue
            val engine = probe.factory()
            try {
                engine.prepare()
                Log.info("Engine", "using backend '$id'")
                return PreparedEngine(engine)
            } catch (error: Throwable) {
                Log.warn("Engine", "backend '$id' unavailable: ${error.message}")
                runCatching { engine.close() }
            }
        }
        return null
    }

    data class PreparedEngine(val engine: AudioEngine)
}
