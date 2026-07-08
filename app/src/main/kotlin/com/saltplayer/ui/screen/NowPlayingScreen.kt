package com.saltplayer.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.UnstableSaltUiApi
import com.saltplayer.AppState
import com.saltplayer.model.PlayMode

@OptIn(UnstableSaltUiApi::class)
@Composable
fun NowPlayingScreen(
    appState: AppState,
    modifier: Modifier = Modifier
) {
    val state = appState.playbackState.value
    val config = appState.config

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Title
        Text(
            text = "正在播放",
            style = MaterialTheme.typography.headlineMedium,
            color = SaltTheme.colors.text,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        Spacer(Modifier.weight(0.3f))

        // Cover art
        if (state.coverArt != null) {
            AsyncImage(
                model = "data:${state.coverArt.mimeType};base64,${state.coverArt.data}",
                contentDescription = state.title,
                modifier = Modifier
                    .size(280.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SaltTheme.colors.highlight),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (state.title.isEmpty()) "♪" else state.title,
                    color = SaltTheme.colors.subText,
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        // Song info
        Text(
            text = state.title.ifBlank { "暂无歌曲" },
            style = MaterialTheme.typography.headlineSmall,
            color = SaltTheme.colors.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (state.artist.isNotBlank()) {
            Text(
                text = state.artist,
                style = MaterialTheme.typography.bodyLarge,
                color = SaltTheme.colors.subText
            )
        }
        if (state.album.isNotBlank()) {
            Text(
                text = state.album,
                style = MaterialTheme.typography.bodyMedium,
                color = SaltTheme.colors.subText
            )
        }

        Spacer(Modifier.height(24.dp))

        // Progress bar
        if (state.duration > 0) {
            Column(Modifier.fillMaxWidth()) {
                Slider(
                    value = (state.position / state.duration).toFloat().coerceIn(0f, 1f),
                    onValueChange = {}, // TODO: seeking
                    colors = SliderDefaults.colors(
                        thumbColor = SaltTheme.colors.text,
                        activeTrackColor = SaltTheme.colors.text
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(state.position),
                        color = SaltTheme.colors.subText,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = formatTime(state.duration),
                        color = SaltTheme.colors.subText,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Controls row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Play mode
            IconButton(onClick = {
                val modes = PlayMode.entries
                val next = modes[(modes.indexOf(config.playMode) + 1) % modes.size]
                appState.setPlayMode(next)
            }) {
                Text(
                    text = modeIcon(config.playMode),
                    style = MaterialTheme.typography.titleLarge
                )
            }

            IconButton(onClick = { appState.prevSong() }) {
                Text("⏮", style = MaterialTheme.typography.titleLarge)
            }

            IconButton(
                onClick = { appState.togglePlayPause() },
                modifier = Modifier.size(56.dp)
            ) {
                Text(
                    text = if (state.isPlaying) "⏸" else "▶️",
                    style = MaterialTheme.typography.headlineLarge
                )
            }

            IconButton(onClick = { appState.nextSong() }) {
                Text("⏭", style = MaterialTheme.typography.titleLarge)
            }

            // Volume placeholder
            Spacer(Modifier.width(8.dp))
        }

        Spacer(Modifier.weight(0.3f))
    }
}

private fun formatTime(seconds: Double): String {
    val m = (seconds / 60).toInt()
    val s = (seconds % 60).toInt()
    return "%d:%02d".format(m, s)
}

private fun modeIcon(mode: PlayMode): String = when (mode) {
    PlayMode.SEQUENTIAL -> "🔁"
    PlayMode.SHUFFLE -> "🔀"
    PlayMode.REPEAT_ONE -> "🔂"
    PlayMode.REPEAT_ALL -> "🔄"
}
