// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Corripo
package com.saltplayer.playback.mpv

import com.sun.jna.Library
import com.sun.jna.Pointer

/**
 * Minimal libmpv surface used by [MpvAudioEngine].
 *
 * Must be public: JNA creates a dynamic proxy for the interface, and a private nested type makes
 * that proxy fail with an access error, which surfaced as an unexplained `mpv_create() == NULL`.
 */
interface MpvBindings : Library {
    fun mpv_create(): Pointer?
    fun mpv_initialize(ctx: Pointer): Int
    fun mpv_terminate_destroy(ctx: Pointer)
    fun mpv_set_option_string(ctx: Pointer, name: String, value: String): Int
    fun mpv_set_property_string(ctx: Pointer, name: String, value: String): Int
    fun mpv_get_property(ctx: Pointer, name: String, format: Int, data: Pointer): Int
    fun mpv_command_string(ctx: Pointer, command: String): Int
    fun mpv_observe_property(ctx: Pointer, replyUserData: Long, name: String, format: Int): Int
    fun mpv_wait_event(ctx: Pointer, timeoutSeconds: Double): Pointer?
    fun mpv_error_string(code: Int): String
}
