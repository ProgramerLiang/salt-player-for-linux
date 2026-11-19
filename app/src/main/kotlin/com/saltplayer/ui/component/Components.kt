// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Corripo
package com.saltplayer.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moriafly.salt.ui.Icon
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import com.saltplayer.data.media.CoverStore
import com.saltplayer.domain.model.Track
import com.saltplayer.ui.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap

/**
 * Cached cover art loader: decoded once per track and kept in memory.
 *
 * Tracks without artwork are remembered in [missing] instead of storing `null` in the map —
 * `ConcurrentHashMap` rejects null values, which previously threw an NPE on the AWT event
 * thread and surfaced as an exception dialog whenever a cover-less track scrolled into view.
 */
object CoverCache {
    private val cache = ConcurrentHashMap<Long, ImageBitmap>()
    private val missing: MutableSet<Long> = ConcurrentHashMap.newKeySet()

    suspend fun load(store: CoverStore, trackId: Long): ImageBitmap? {
        cache[trackId]?.let { return it }
        if (trackId in missing) return null
        val bitmap = withContext(Dispatchers.IO) {
            val file = store.thumbnail(trackId) ?: return@withContext null
            runCatching {
                val bytes = Files.readAllBytes(file)
                Image.makeFromEncoded(bytes).toComposeImageBitmap()
            }.getOrNull()
        }
        if (bitmap == null) {
            missing.add(trackId)
        } else {
            cache[trackId] = bitmap
            missing.remove(trackId)
        }
        return bitmap
    }

    fun invalidate(trackId: Long) {
        cache.remove(trackId)
        missing.remove(trackId)
    }
}

@Composable
fun CoverImage(
    store: CoverStore,
    trackId: Long?,
    size: Dp,
    modifier: Modifier = Modifier,
    corner: Dp = 8.dp,
) {
    var bitmap by remember(trackId) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(trackId) {
        bitmap = trackId?.let { CoverCache.load(store, it) }
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(SaltTheme.colors.subBackground),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            androidx.compose.foundation.Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
            )
        } else {
            CoverPlaceholder(size)
        }
    }
}

@Composable
private fun CoverPlaceholder(size: Dp) {
    Box(
        modifier = Modifier.size(size).background(SaltTheme.colors.subBackground),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "♪", color = SaltTheme.colors.subText, fontSize = (size.value * 0.42f).sp)
    }
}

/**
 * Salt-style slider. SaltUI 3.0.0 keeps its slider `internal`, so this is a small local
 * implementation that follows the same visual language (thick rounded track, round thumb).
 */
@Composable
fun SaltSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    var widthPx by remember { mutableStateOf(1f) }
    val highlight = SaltTheme.colors.highlight
    val trackColor = SaltTheme.colors.subText.copy(alpha = 0.25f)
    var dragging by remember { mutableStateOf(false) }

    fun update(x: Float) {
        if (!enabled || widthPx <= 1f) return
        onValueChange((x / widthPx).coerceIn(0f, 1f))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(enabled) {
                detectTapGestures { offset -> update(offset.x) }
            }
            .pointerInput(enabled) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        update(offset.x)
                    },
                    onDragEnd = {
                        dragging = false
                        onValueChangeFinished?.invoke()
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        update(change.position.x)
                    },
                )
            },
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(28.dp)) {
            widthPx = size.width
            drawSlider(value.coerceIn(0f, 1f), highlight, trackColor, dragging)
        }
    }
}

private fun DrawScope.drawSlider(value: Float, highlight: Color, trackColor: Color, dragging: Boolean) {
    val trackHeight = 5.dp.toPx()
    val centerY = size.height / 2f
    val radius = (size.height / 2f) - 4.dp.toPx()
    val thumbRadius = if (dragging) 8.dp.toPx() else 6.5f * density
    drawRoundRect(
        color = trackColor,
        topLeft = Offset(0f, centerY - trackHeight / 2),
        size = Size(size.width, trackHeight),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2),
    )
    if (value > 0f) {
        drawRoundRect(
            color = highlight,
            topLeft = Offset(0f, centerY - trackHeight / 2),
            size = Size(size.width * value, trackHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2),
        )
    }
    val x = (size.width * value).coerceIn(thumbRadius, size.width - thumbRadius)
    drawCircle(color = highlight, radius = thumbRadius, center = Offset(x, centerY))
}

/** A row in any track list. */
@Composable
fun TrackRow(
    track: Track,
    index: Int?,
    playing: Boolean,
    showCover: Boolean,
    store: CoverStore,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onSecondary: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (index != null) {
            Box(modifier = Modifier.width(28.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = if (playing) "▶" else index.toString(),
                    color = if (playing) SaltTheme.colors.highlight else SaltTheme.colors.subText,
                    fontSize = 13.sp,
                )
            }
        }
        if (showCover) {
            CoverImage(store = store, trackId = track.id, size = 42.dp, corner = 6.dp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.displayTitle,
                color = if (playing) SaltTheme.colors.highlight else SaltTheme.colors.text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${track.displayArtist} · ${track.album.ifBlank { "-" }}",
                color = SaltTheme.colors.subText,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (track.favorite) {
            Text(text = "♥", color = SaltTheme.colors.error, fontSize = 13.sp)
        }
        Text(
            text = formatDuration(track.durationMs),
            color = SaltTheme.colors.subText,
            fontSize = 12.sp,
        )
        if (onSecondary != null) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onSecondary),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "⋯", color = SaltTheme.colors.subText, fontSize = 18.sp)
            }
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = SaltTheme.colors.text,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

@Composable
fun EmptyState(title: String, hint: String? = null, modifier: Modifier = Modifier, action: @Composable (() -> Unit)? = null) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = "♫", color = SaltTheme.colors.subText.copy(alpha = 0.5f), fontSize = 34.sp)
        Text(text = title, color = SaltTheme.colors.text, fontSize = 15.sp)
        if (hint != null) {
            Text(text = hint, color = SaltTheme.colors.subText, fontSize = 12.sp)
        }
        if (action != null) {
            Spacer(modifier = Modifier.height(8.dp))
            action()
        }
    }
}

@Composable
fun InfoRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = label, color = SaltTheme.colors.subText, fontSize = 13.sp, modifier = Modifier.width(96.dp))
        Text(text = value, color = SaltTheme.colors.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
    }
}

/** Round icon button used for transport controls. */
@Composable
fun RoundIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    background: Color = Color.Transparent,
    tint: Color = SaltTheme.colors.text,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size / 2))
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}

/** A small pill used for counts and capability tags. */
@Composable
fun Tag(text: String, modifier: Modifier = Modifier, color: Color = SaltTheme.colors.highlight) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .border(0.5.dp, color.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text = text, color = color, fontSize = 11.sp)
    }
}

/**
 * Centred, width-capped container for long-form screens.
 *
 * Card-heavy content stretched across a 1920 px window reads as "no rhythm": the eye has to
 * travel the full width for every row. Capping the column and centring it keeps a comfortable
 * measure while still leaving the backdrop full-bleed.
 */
object ContentMetrics {
    /** Width cap for text-heavy columns that sit directly on the backdrop (song lists). */
    val MaxWidth = 820.dp

    /** Width cap for the panel itself; the content fills the panel minus its inner padding. */
    val PanelMaxWidth = 1400.dp
    val PanelContentMaxWidth = 1040.dp

    /** Distance between the panel edge and the window edge. */
    val PanelOuterMargin = 24.dp
    val PanelOuterMarginNarrow = 12.dp

    /** Inner padding so text and controls never touch the panel border. */
    /**
     * Opaque panel colour.
     *
     * SaltUI's `subBackground` is a 50 % white wash (`0x80FFFFFF`). Painting a translucent panel
     * on top of a translucent bed makes the panel slightly brighter than the margin around it,
     * which reads as a colour band. Flattening the wash to an opaque colour lets the bed and the
     * panels use the very same value.
     */
    @Composable
    fun panelColor(): androidx.compose.ui.graphics.Color =
        SaltTheme.colors.subBackground.compositeOver(SaltTheme.colors.background)

    val PanelInnerHorizontal = 32.dp
    val PanelInnerVertical = 20.dp
    val SidePadding = 28.dp
    val CardHorizontalPadding = 20.dp
    val CardVerticalPadding = 6.dp
    val SectionSpacing = 18.dp
}

@Composable
fun ContentColumn(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                // widthIn must run before fillMaxWidth: filling first pins the minimum width to
                // the parent and the cap is then ignored.
                .widthIn(max = ContentMetrics.MaxWidth)
                .fillMaxWidth()
                .padding(horizontal = ContentMetrics.SidePadding),
            content = content,
        )
    }
}

/**
 * A white panel that is wider than the content it carries.
 *
 * SaltUI's [com.moriafly.salt.ui.RoundedColumn] applies `paddingValues` *before* clipping and
 * painting, so padding there shrinks the visible surface instead of insetting its content. The
 * panel therefore takes no padding of its own: the surface spans the available width minus
 * [ContentMetrics.PanelOuterMargin], and an inner column adds the padding and the width cap.
 */
@Composable
fun SettingsPanel(
    outerMargin: Dp,
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    // The panel is the carrier: it spans the available width (capped for ultrawide windows) and
    // only its *content* is inset, so text and controls never touch the paint edge.
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
    Column(
        modifier = Modifier
            .widthIn(max = ContentMetrics.PanelMaxWidth)
            .fillMaxWidth()
            .padding(horizontal = outerMargin),
    ) {
        com.moriafly.salt.ui.RoundedColumn(
            paddingValues = androidx.compose.foundation.layout.PaddingValues(0.dp),
            color = ContentMetrics.panelColor(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = ContentMetrics.PanelInnerHorizontal,
                        vertical = ContentMetrics.PanelInnerVertical,
                    ),
            ) {
                if (title != null) {
                    Text(
                        text = title,
                        color = SaltTheme.colors.text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(10.dp))
                }
                content()
            }
        }
    }
    }
}

/**
 * In-app logo: the same artwork as the launcher icon, loaded from resources.
 *
 * The sidebar used to render a `♫` glyph inside a coloured square, which looked like the old
 * branding. The bitmap is packaged at 256 px so it stays sharp when the UI scale enlarges it.
 */
@Composable
fun AppLogo(size: Dp, modifier: Modifier = Modifier, corner: Dp = 8.dp) {
    val bitmap = remember {
        runCatching {
            val stream = Thread.currentThread().contextClassLoader
                ?.getResourceAsStream("icons/salt-shaker.png")
                ?: return@runCatching null
            Image.makeFromEncoded(stream.use { it.readBytes() }).toComposeImageBitmap()
        }.getOrNull()
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(SaltTheme.colors.subBackground),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            androidx.compose.foundation.Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(size),
            )
        } else {
            Text(text = "♫", color = SaltTheme.colors.onHighlight, fontSize = 15.sp)
        }
    }
}
