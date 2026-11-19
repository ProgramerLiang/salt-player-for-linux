// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Corripo
package com.saltplayer.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.saltplayer.data.media.CoverStore
import com.saltplayer.ui.theme.CoverPalette
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import kotlin.math.cos
import kotlin.math.sin

/**
 * Palette cache for the flowing background.
 *
 * Decoding happens once per track: the dominant colour is derived from the already-small
 * thumbnail in the cover cache, so switching tracks costs one small JPEG decode, and every frame
 * afterwards only interpolates two brushes.
 */
object CoverColorCache {
    private val cache = ConcurrentHashMap<Long, Color>()

    fun get(trackId: Long, store: CoverStore): Color? {
        cache[trackId]?.let { return it }
        val file: File? = store.thumbnail(trackId)?.toFile()
            ?: store.fullCover(trackId)?.toFile()
            ?: return null
        val color = runCatching {
            val image = ImageIO.read(file) ?: return@runCatching null
            val width = image.width.coerceAtMost(64)
            val height = image.height.coerceAtMost(64)
            val scaled = image.getScaledInstance(width, height, java.awt.Image.SCALE_FAST)
            val buffered = java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB)
            buffered.createGraphics().apply {
                drawImage(scaled, 0, 0, null)
                dispose()
            }
            val pixels = IntArray(width * height)
            buffered.getRGB(0, 0, width, height, pixels, 0, width)
            CoverPalette.dominant(pixels)?.let { CoverPalette.toColor(it) }
        }.getOrNull()
        if (color != null) cache[trackId] = color
        return color
    }

    fun clear() = cache.clear()
}

/**
 * Slowly flowing gradient built from the current track's cover colour.
 *
 * Two radial gradients drift on independent 26 s and 18 s cycles, and their hues breathe by a few
 * degrees. On a track change the base colour cross-fades over 600 ms instead of cutting, and the
 * whole thing is drawn on the canvas each frame: no bitmap copies, no blur passes.
 */
@Composable
fun FlowBackground(
    baseColor: Color,
    modifier: Modifier = Modifier,
) {
    // Track changes cross-fade the palette instead of cutting.
    val current by animateColorAsState(
        targetValue = baseColor,
        animationSpec = tween(600),
        label = "palette",
    )
    val transition = rememberInfiniteTransition(label = "flow")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(26_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "drift",
    )
    val breathe by transition.animateFloat(
        initialValue = -20f,
        targetValue = 20f,
        animationSpec = infiniteRepeatable(
            animation = tween(18_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathe",
    )

    val warm = remember(current, breathe.toInt()) { CoverPalette.shiftHue(current, breathe) }
    val cool = remember(current) { CoverPalette.shiftHue(current, -28f) }

    Canvas(modifier = modifier.fillMaxSize()) {
        drawBackdrop(current)
        val radius = size.maxDimension * 0.78f
        val center1 = Offset(
            x = size.width * (0.32f + 0.10f * cos(drift)),
            y = size.height * (0.30f + 0.08f * sin(drift * 0.8f)),
        )
        val center2 = Offset(
            x = size.width * (0.70f + 0.09f * cos(drift * 0.7f + 1.4f)),
            y = size.height * (0.72f + 0.07f * sin(drift * 0.6f)),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(warm.copy(alpha = 0.85f), Color.Transparent),
                center = center1,
                radius = radius,
            ),
            radius = radius,
            center = center1,
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(cool.copy(alpha = 0.75f), Color.Transparent),
                center = center2,
                radius = radius * 0.92f,
            ),
            radius = radius * 0.92f,
            center = center2,
        )
    }
}

private fun DrawScope.drawBackdrop(base: Color) {
    // Fully opaque bed: a translucent one let the page underneath show through the lyrics.
    drawRect(color = Color(0xFF101014), size = Size(size.width, size.height))
    drawRect(
        brush = Brush.verticalGradient(
            listOf(
                base.copy(alpha = 0.62f),
                base.copy(alpha = 0.30f),
                Color.Transparent,
            )
        ),
        size = Size(size.width, size.height),
    )
}
