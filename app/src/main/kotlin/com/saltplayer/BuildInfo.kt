// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Corripo
package com.saltplayer

/** Build-time and runtime facts shown in Settings › About. */
object BuildInfo {
    const val VERSION = "0.2.1"
    const val LICENSE = "LGPL-3.0-or-later"

    /** Set by the MPRIS service once the D-Bus name has been acquired. */
    @Volatile
    var mprisActive: Boolean = false

    /** Human readable description of single-instance ownership, shown in Settings. */
    @Volatile
    var singleInstanceOwner: String = "primary"
}
