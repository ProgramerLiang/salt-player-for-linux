// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Corripo
package com.saltplayer.platform

/**
 * Small bridge between the settings UI and the application process.
 *
 * Restart and tray availability are facts only the entry point knows, so it publishes them here
 * instead of the UI guessing. [restart] is intentionally nullable: if a headless/test build
 * never sets it, the UI hides the restart action rather than offering a dead button.
 */
object AppControl {

    /** Restarts the application process, preserving the current working directory. */
    @Volatile
    var restart: (() -> Unit)? = null

    /**
     * True when the desktop provides a system tray. Compose Desktop uses AWT's tray, which
     * reports false under plain Wayland and under XWayland sessions without a StatusNotifier
     * host; the settings screen shows the real value instead of pretending it works.
     */
    @Volatile
    var traySupported: Boolean = false

    /** Human readable reason shown next to an unavailable tray toggle. */
    @Volatile
    var trayUnavailableReason: String? = null
}
