// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Corripo
package com.saltplayer.data

import java.io.DataOutputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.sin

/** Writes real (playable) PCM WAV files so tag reading and playback can be tested end to end. */
object AudioFixture {

    fun writeWav(
        file: Path,
        seconds: Double = 1.0,
        sampleRate: Int = 44_100,
        frequencyHz: Double = 440.0,
    ): Path {
        Files.createDirectories(file.toAbsolutePath().parent)
        val frames = (seconds * sampleRate).toInt()
        val dataSize = frames * 2
        DataOutputStream(Files.newOutputStream(file)).use { out ->
            out.writeBytes("RIFF")
            out.writeInt(Integer.reverseBytes(36 + dataSize))
            out.writeBytes("WAVE")
            out.writeBytes("fmt ")
            out.writeInt(Integer.reverseBytes(16))
            out.writeShort(java.lang.Short.reverseBytes(1.toShort()).toInt())
            out.writeShort(java.lang.Short.reverseBytes(1.toShort()).toInt())
            out.writeInt(Integer.reverseBytes(sampleRate))
            out.writeInt(Integer.reverseBytes(sampleRate * 2))
            out.writeShort(java.lang.Short.reverseBytes(2.toShort()).toInt())
            out.writeShort(java.lang.Short.reverseBytes(16.toShort()).toInt())
            out.writeBytes("data")
            out.writeInt(Integer.reverseBytes(dataSize))
            for (frame in 0 until frames) {
                val value = (sin(2.0 * Math.PI * frequencyHz * frame / sampleRate) * Short.MAX_VALUE * 0.2).toInt()
                out.writeShort(java.lang.Short.reverseBytes(value.toShort()).toInt())
            }
        }
        return file
    }
}
