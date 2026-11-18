// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Corripo
package com.saltplayer.platform

/** Marker used by the settings screen while a backend probe is running. */
object BackendProbeRunner {
    @Volatile
    var running: Boolean = false
}
