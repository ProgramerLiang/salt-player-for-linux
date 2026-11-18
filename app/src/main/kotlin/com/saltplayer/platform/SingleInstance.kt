// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Corripo
package com.saltplayer.platform

import com.saltplayer.core.Log
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.Files
import java.nio.file.Path

/**
 * Single-instance guard plus a tiny activation channel.
 *
 * A lock file in the XDG data directory decides who owns the application; the second instance
 * writes a byte to a FIFO-like marker file and exits, the primary instance watches that file and
 * raises its window.
 */
class SingleInstance(private val lockFile: Path) : AutoCloseable {

    private var channel: FileChannel? = null
    private var lock: FileLock? = null

    /** True when this process owns the instance. */
    fun acquire(): Boolean {
        runCatching { Files.createDirectories(lockFile.parent) }
        return runCatching {
            val raf = RandomAccessFile(lockFile.toFile(), "rw")
            val ch = raf.channel
            val fileLock = ch.tryLock()
            if (fileLock == null) {
                ch.close()
                false
            } else {
                channel = ch
                lock = fileLock
                true
            }
        }.getOrElse {
            Log.warn("SingleInstance", "lock failed, continuing without single-instance guard", it)
            true
        }
    }

    /** Asks the running instance to raise its window, then the caller can exit. */
    fun signalActivate(): Boolean = runCatching {
        Files.writeString(activationFile(), System.currentTimeMillis().toString())
    }.isSuccess

    fun activationFile(): Path = lockFile.resolveSibling(lockFile.fileName.toString() + ".activate")

    fun consumeActivation(): Boolean {
        val file = activationFile()
        if (!Files.exists(file)) return false
        return runCatching {
            Files.delete(file)
            true
        }.getOrDefault(false)
    }

    override fun close() {
        runCatching { lock?.release() }
        runCatching { channel?.close() }
    }
}
