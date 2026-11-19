// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Corripo
package com.saltplayer.ui

import com.saltplayer.data.media.CoverStore
import com.saltplayer.ui.component.CoverCache
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertNull

/**
 * Regression: a track without artwork used to store `null` in a ConcurrentHashMap, which threw
 * an NPE on the AWT event thread (surfacing as an exception dialog while scrolling the library).
 */
class CoverCacheTest {

    @Test
    fun `loading a track without artwork returns null instead of throwing`() = runBlocking {
        val store = CoverStore(Files.createTempDirectory("covers"))
        assertNull(CoverCache.load(store, 987_654_321L))
        // Second call hits the negative cache and must stay null-safe.
        assertNull(CoverCache.load(store, 987_654_321L))
    }
}
