// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Corripo
package com.saltplayer.platform

import com.saltplayer.core.Log
import com.saltplayer.core.UiScale
import java.util.concurrent.TimeUnit

/**
 * Reads the desktop's dark-mode preference and accent colour.
 *
 * Order of preference:
 * 1. the freedesktop appearance portal (`org.freedesktop.appearance color-scheme`) over D-Bus,
 * 2. the GNOME/GTK settings exposed through `gsettings`,
 * 3. the `GTK_THEME` environment variable.
 *
 * Every probe is best-effort: an unknown desktop simply reports "no preference" and the app
 * falls back to the light theme.
 */
object SystemTheme {

    data class Snapshot(val dark: Boolean, val accentName: String?)

    fun read(): Snapshot = Snapshot(dark = isDark(), accentName = accentName())

    fun isDark(): Boolean {
        portalColorScheme()?.let { return it }
        gsettingsColorScheme()?.let { return it }
        val gtkTheme = System.getenv("GTK_THEME") ?: return false
        return gtkTheme.contains("dark", ignoreCase = true)
    }

    /**
     * Display scale the toolkit failed to report.
     *
     * Under XWayland the compositor may resize the surface instead of advertising a scale, so
     * `GraphicsConfiguration.getDefaultTransform()` stays identity. `Xft.dpi` is the next best
     * source of truth, then GDK's environment variables, then AWT, then 1.0.
     */
    fun detectScale(): Float {
        UiScale.parseXftDpi(runCommand(listOf("xrdb", "-query")))?.let { return it }
        UiScale.parseEnvScale(System.getenv("GDK_SCALE"))?.let { return it }
        runCatching {
            val transform = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                .defaultScreenDevice.defaultConfiguration.defaultTransform
            val scale = transform.scaleX.toFloat()
            if (scale > 1.01f) return scale
        }
        return 1f
    }

    fun accentName(): String? {
        val value = runCommand(listOf("gsettings", "get", "org.gnome.desktop.interface", "accent-color"))
            ?: return null
        return value.trim().trim('\'').takeIf { it.isNotBlank() && it != "default" }
    }

    /** Colour scheme through the XDG desktop portal, if a portal is running. */
    private fun portalColorScheme(): Boolean? = runCatching {
        val output = runCommand(
            listOf(
                "gdbus", "call", "--session",
                "--dest", "org.freedesktop.portal.Desktop",
                "--object-path", "/org/freedesktop/portal/desktop",
                "--method", "org.freedesktop.portal.Settings.Read",
                "org.freedesktop.appearance", "color-scheme",
            )
        ) ?: return null
        // Output looks like: (<<uint32 1>>,) — 1 = prefer dark, 0 = no preference, 2 = prefer light.
        val value = Regex("""uint32\s+(\d+)""").find(output)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        when (value) {
            1 -> true
            2 -> false
            else -> null
        }
    }.getOrNull()

    private fun gsettingsColorScheme(): Boolean? = runCatching {
        val output = runCommand(
            listOf("gsettings", "get", "org.gnome.desktop.interface", "color-scheme")
        ) ?: return null
        when {
            output.contains("prefer-dark") -> true
            output.contains("prefer-light") -> false
            else -> null
        }
    }.getOrNull()

    private fun runCommand(command: List<String>): String? = runCatching {
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
        val finished = process.waitFor(2, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            return null
        }
        process.inputStream.bufferedReader().readText().trim().ifEmpty { null }
    }.onFailure { Log.warn("SystemTheme", "probe failed: ${command.joinToString(" ")}", it) }.getOrNull()
}
