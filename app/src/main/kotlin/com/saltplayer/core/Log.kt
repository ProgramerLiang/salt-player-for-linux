// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Corripo
package com.saltplayer.core

import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Minimal application logger: stdout plus a rotating-by-session append log file. */
object Log {
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    private var logFile: java.nio.file.Path? = null

    fun attach(file: java.nio.file.Path) {
        logFile = file
        runCatching {
            java.nio.file.Files.createDirectories(file.parent)
            if (java.nio.file.Files.exists(file) && java.nio.file.Files.size(file) > 2L * 1024 * 1024) {
                java.nio.file.Files.delete(file)
            }
        }
    }

    fun info(tag: String, message: String) = write("I", tag, message, null)

    fun warn(tag: String, message: String, error: Throwable? = null) = write("W", tag, message, error)

    fun error(tag: String, message: String, error: Throwable? = null) = write("E", tag, message, error)

    private fun write(level: String, tag: String, message: String, error: Throwable?) {
        val line = buildString {
            append(LocalTime.now().format(timeFormat))
            append(' ').append(level).append('/').append(tag).append(": ").append(message)
            if (error != null) append("\n  ").append(error.stackTraceToString().lines().take(6).joinToString("\n  "))
        }
        println(line)
        val file = logFile ?: return
        runCatching { java.nio.file.Files.writeString(file, line + "\n", java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND) }
    }
}
