// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Corripo
package com.saltplayer.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.saltplayer.domain.lyrics.KaraokeWipe
import com.saltplayer.domain.lyrics.LyricLine

/**
 * Karaoke lyric line with a horizontal wipe instead of per-word colour flips.
 *
 * The text is measured once per (line, style) to get every word's pixel span; the playhead is then
 * mapped to an x offset inside the active word, so highlight crosses letters *within* a word and
 * keeps moving inside a single CJK glyph. Two identical text layers are stacked and the upper
 * (highlight) layer is clipped to that x offset.
 */
@Composable
fun KaraokeLine(
    line: LyricLine,
    positionMs: Long,
    style: TextStyle,
    dimColor: Color,
    highlightColor: Color,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val full = remember(line.text, style) { measurer.measure(line.text, style) }
    val wordSpans = remember(line.text, style) {
        val spans = ArrayList<ClosedFloatingPointRange<Float>>(line.words.size)
        var prefix = ""
        var previous = 0f
        for (word in line.words) {
            prefix += word.text
            val width = if (prefix.isEmpty()) 0f else measurer.measure(prefix, style).size.width.toFloat()
            spans += previous..width
            previous = width
        }
        spans
    }
    val totalWidth = full.size.width.toFloat().coerceAtLeast(1f)
    val wipeX = remember(positionMs, line, wordSpans) {
        val slice = KaraokeWipe.sliceAt(line.words, positionMs)
        when {
            slice == null -> 0f
            wordSpans.isEmpty() -> KaraokeWipe.fractionOfLine(line, positionMs) * totalWidth
            else -> {
                val span = wordSpans[slice.index.coerceIn(wordSpans.indices)]
                span.start + (span.endInclusive - span.start) * slice.fraction
            }
        }
    }

    Box(modifier = modifier) {
        BasicText(
            text = line.text,
            style = style.copy(color = dimColor),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier = Modifier
                .clipToBounds()
                .width(with(androidx.compose.ui.platform.LocalDensity.current) { wipeX.toDp() }),
        ) {
            BasicText(
                text = line.text,
                style = style.copy(color = highlightColor),
                maxLines = 2,
                overflow = TextOverflow.Visible,
                modifier = Modifier.width(with(androidx.compose.ui.platform.LocalDensity.current) { totalWidth.toDp() }),
            )
        }
    }
}
