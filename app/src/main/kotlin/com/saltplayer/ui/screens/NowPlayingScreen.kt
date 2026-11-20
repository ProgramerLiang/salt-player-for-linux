// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Corripo
package com.saltplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import com.saltplayer.domain.queue.PlayMode
import com.saltplayer.ui.AppViewModel
import com.saltplayer.ui.component.CoverColorCache
import com.saltplayer.ui.component.CoverImage
import com.saltplayer.ui.component.SaltSlider
import com.saltplayer.ui.component.FlowBackground
import com.saltplayer.ui.component.FlowPaletteLayers
import com.saltplayer.ui.component.RoundIconButton
import com.saltplayer.ui.formatDuration
import com.saltplayer.ui.i18n.Str
import com.saltplayer.ui.theme.Contrast

/**
 * Full-screen now-playing view with synced lyrics: the current line is highlighted, clicking a
 * line seeks to it, and the offset can be nudged for badly timed `.lrc` files.
 *
 * Every foreground colour on this screen comes from one contrast-checked palette. The flowing
 * background's brightest/darkest reachable pixel is bounded by [FlowPaletteLayers], so the
 * palette is guaranteed to keep body text at >= 4.5:1 and icons/large text at >= 3:1 across
 * dark/light schemes, animated/static/off flow and every cover hue.
 */
@Composable
fun NowPlayingScreen(vm: AppViewModel) {
    val s = vm.strings
    val state = vm.playerState
    val track = state.track ?: return
    val lyrics = vm.currentLyrics
    val listState = rememberLazyListState()
    val currentLine = if (lyrics.synced) lyrics.indexAt(state.positionMs) else -1
    var lyricsExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(currentLine) {
        if (lyrics.synced && currentLine >= 0) {
            runCatching { listState.animateScrollToItem(currentLine) }
        }
    }

    val themeAccent = SaltTheme.colors.highlight
    val palette = remember(track.id, themeAccent) {
        CoverColorCache.get(track.id, vm.coverStore) ?: themeAccent
    }
    val lightScheme = vm.settings.flowPalette == com.saltplayer.data.prefs.FlowPalette.Light
    val samples = remember(palette, lightScheme) { FlowPaletteLayers.samples(palette, lightScheme) }
    val chrome = remember(samples, lightScheme, themeAccent) {
        Contrast.chromeColors(samples, lightScheme, themeAccent)
    }
    val lyricColors = remember(chrome.background, lightScheme) {
        Contrast.lyricColors(chrome.background, lightScheme)
    }
    // Frame-driven clock: the player only publishes every ~400 ms, which would make the wipe step.
    val smoothPosition by rememberSmoothPosition(state.positionMs, state.playing, state.durationMs)
    Box(modifier = Modifier.fillMaxSize()) {
        if (vm.settings.flowBackground) {
            FlowBackground(
                baseColor = palette,
                lightScheme = lightScheme,
                animated = vm.settings.flowAnimated,
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().background(chrome.background))
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundIconButton(Icons.Rounded.KeyboardArrowDown, s[Str.Close], { vm.nowPlayingOpen = false },
                    size = 38.dp, tint = chrome.primary)
                Spacer(Modifier.weight(1f))
                Text(text = s[Str.NowPlaying], color = chrome.secondary, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                RoundIconButton(Icons.Rounded.Favorite, s[Str.Favorite], { vm.toggleFavorite(track) }, size = 36.dp,
                    tint = if (track.favorite) chrome.accentText else chrome.secondary)
            }

            Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(36.dp)) {
                Column(
                    modifier = Modifier.width(340.dp).padding(top = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CoverImage(store = vm.coverStore, trackId = track.id, size = 320.dp, corner = 18.dp)
                    Spacer(Modifier.height(22.dp))
                    Text(
                        text = track.displayTitle,
                        color = chrome.primary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        textAlign = TextAlign.Center,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${track.displayArtist} · ${track.album.ifBlank { "-" }}",
                        color = chrome.secondary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(18.dp))
                    SaltSlider(
                        value = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f,
                        onValueChange = { vm.player.seekTo((it * state.durationMs).toLong()) },
                        onValueChangeFinished = { vm.player.saveSession() },
                        activeColor = chrome.accentTrack,
                        inactiveColor = chrome.mutedTrack,
                    )
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(formatDuration(state.positionMs), color = chrome.secondary, fontSize = 11.sp)
                        Spacer(Modifier.weight(1f))
                        Text(formatDuration(state.durationMs), color = chrome.secondary, fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        RoundIconButton(
                            icon = modeIcon(state.mode),
                            contentDescription = s[Str.PlayMode],
                            onClick = { vm.player.cycleMode() },
                            size = 36.dp,
                            tint = if (state.mode == PlayMode.Sequential) chrome.secondary else chrome.accentText,
                        )
                        RoundIconButton(Icons.Rounded.SkipPrevious, s[Str.Previous], { vm.player.previous() },
                            size = 42.dp, tint = chrome.primary)
                        RoundIconButton(
                            icon = if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (state.playing) s[Str.Pause] else s[Str.Play],
                            onClick = { vm.player.togglePlayPause() },
                            size = 58.dp,
                            background = chrome.accentTrack,
                            tint = chrome.onAccent,
                        )
                        RoundIconButton(Icons.Rounded.SkipNext, s[Str.Next], { vm.player.next() },
                            size = 42.dp, tint = chrome.primary)
                        RoundIconButton(Icons.Rounded.VolumeUp, s[Str.Volume], { vm.nowPlayingOpen = false },
                            size = 36.dp, tint = chrome.secondary)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(text = s[Str.Volume], color = chrome.secondary, fontSize = 11.sp)
                        Box(modifier = Modifier.weight(1f)) {
                            SaltSlider(value = state.volume,
                                onValueChange = { vm.player.setVolume(it) },
                                activeColor = chrome.accentTrack,
                                inactiveColor = chrome.mutedTrack)
                        }
                        Text(text = "${(state.volume * 100).toInt()}%", color = chrome.secondary, fontSize = 11.sp)
                    }
                }

                Column(modifier = Modifier.weight(1f).fillMaxSize()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = s[Str.Lyrics],
                            color = chrome.primary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        if (lyrics.synced) {
                            TaggedAction(s[Str.LyricsShiftEarlier], chrome) { vm.setLyricOffset(lyrics.offsetMs - 500) }
                            Spacer(Modifier.width(6.dp))
                            TaggedAction(s[Str.LyricsShiftLater], chrome) { vm.setLyricOffset(lyrics.offsetMs + 500) }
                            Spacer(Modifier.width(6.dp))
                            TaggedAction(s[Str.LyricsReset], chrome) { vm.setLyricOffset(0) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    when {
                        lyrics.isEmpty -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(text = s[Str.NoLyrics], color = chrome.secondary, fontSize = 13.sp)
                        }

                        !lyrics.synced -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(lyrics.lines.size) { index ->
                                Text(
                                    text = lyrics.lines[index].text,
                                    color = chrome.secondary,
                                    fontSize = 14.sp,
                                    lineHeight = 22.sp,
                                    modifier = Modifier.padding(vertical = 3.dp),
                                )
                            }
                        }

                        else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                            items(lyrics.lines.size) { index ->
                                val line = lyrics.lines[index]
                                val active = index == currentLine
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { vm.player.seekTo(line.timeMs - lyrics.offsetMs) }
                                        .padding(vertical = 5.dp),
                                ) {
                                    if (active) {
                                        com.saltplayer.ui.component.KaraokeLine(
                                            line = line,
                                            positionMs = smoothPosition,
                                            style = androidx.compose.ui.text.TextStyle(
                                                fontSize = 17.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                lineHeight = 24.sp,
                                            ),
                                            dimColor = lyricColors.body.copy(alpha = 0.35f),
                                            highlightColor = lyricColors.highlight,
                                        )
                                    } else {
                                        val distance = if (currentLine < 0) 2 else kotlin.math.abs(index - currentLine)
                                        val alpha = when {
                                            active -> 1f
                                            distance == 1 -> 0.55f
                                            distance == 2 -> 0.32f
                                            else -> 0.18f
                                        }
                                        Text(
                                            text = line.text,
                                            color = lyricColors.body.copy(alpha = alpha),
                                            fontSize = if (active) 17.sp else 15.sp,
                                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                            lineHeight = 24.sp,
                                        )
                                    }
                                    if (vm.settings.showLyricTranslation && line.translation != null) {
                                        Text(
                                            text = line.translation!!,
                                            color = if (active) chrome.accentText else chrome.secondary,
                                            fontSize = 13.sp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaggedAction(text: String, chrome: Contrast.ChromeColors, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(chrome.mutedTrack, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        Text(text = text, color = chrome.secondary, fontSize = 10.sp)
    }
}

private fun modeIcon(mode: PlayMode) = when (mode) {
    PlayMode.Sequential, PlayMode.RepeatAll -> Icons.Rounded.Repeat
    PlayMode.RepeatOne -> Icons.Rounded.RepeatOne
    PlayMode.Shuffle, PlayMode.ShuffleNoRepeat -> Icons.Rounded.Shuffle
}

private fun androidx.compose.foundation.lazy.LazyListScope.items(count: Int, content: @Composable (Int) -> Unit) {
    items(count = count, itemContent = { index -> content(index) })
}

/**
 * Interpolates the playhead between the player's coarse position updates.
 *
 * `withFrameNanos` drives one state write per frame while playing, so the karaoke wipe advances
 * smoothly instead of stepping every 400 ms. When paused the value simply follows the state.
 */
@Composable
private fun rememberSmoothPosition(
    positionMs: Long,
    playing: Boolean,
    durationMs: Long,
): androidx.compose.runtime.State<Long> {
    val state = androidx.compose.runtime.remember { androidx.compose.runtime.mutableLongStateOf(positionMs) }
    androidx.compose.runtime.LaunchedEffect(positionMs, playing) {
        var lastFrame = 0L
        var base = positionMs
        state.longValue = positionMs
        if (!playing) return@LaunchedEffect
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
                if (lastFrame != 0L) {
                    val delta = (now - lastFrame) / 1_000_000
                    val next = state.longValue + delta
                    state.longValue = if (durationMs > 0) next.coerceAtMost(durationMs) else next
                }
                lastFrame = now
                if (state.longValue < base) state.longValue = base
            }
        }
    }
    return state
}
