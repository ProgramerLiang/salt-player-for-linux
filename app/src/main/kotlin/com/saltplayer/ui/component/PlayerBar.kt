package com.saltplayer.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.UnstableSaltUiApi
import com.saltplayer.model.PlaybackState
import com.saltplayer.model.PlayMode

@OptIn(UnstableSaltUiApi::class)
@Composable
fun PlayerBar(
    state: PlaybackState,
    playMode: PlayMode,
    volume: Float,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onToggleMode: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onSeek: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    // Always show the bar, even when no song is playing
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(SaltTheme.colors.subBackground)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cover art thumbnail
            CoverThumb(state)

            Spacer(Modifier.width(12.dp))

            // Song info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = state.title.ifBlank { "Salt Player" },
                    color = SaltTheme.colors.text,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (state.artist.isNotBlank()) {
                    Text(
                        text = state.artist,
                        color = SaltTheme.colors.subText,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            // Playback controls
            PlaybackControls(
                isPlaying = state.isPlaying,
                onPrev = onPrev,
                onPlayPause = onPlayPause,
                onNext = onNext
            )
        }
    }
}

@Composable
private fun CoverThumb(state: PlaybackState) {
    if (state.coverArt != null) {
        AsyncImage(
            model = "data:${state.coverArt.mimeType};base64,${state.coverArt.data}",
            contentDescription = state.title,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(SaltTheme.colors.highlight),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "♪",
                color = SaltTheme.colors.subText,
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}

@Composable
private fun PlaybackControls(
    isPlaying: Boolean,
    onPrev: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Previous
        IconButton(onClick = onPrev, modifier = Modifier.size(36.dp)) {
            Text("⏮", style = MaterialTheme.typography.titleMedium)
        }
        // Play/Pause
        IconButton(onClick = onPlayPause, modifier = Modifier.size(40.dp)) {
            Text(
                if (isPlaying) "⏸" else "▶️",
                style = MaterialTheme.typography.titleLarge
            )
        }
        // Next
        IconButton(onClick = onNext, modifier = Modifier.size(36.dp)) {
            Text("⏭", style = MaterialTheme.typography.titleMedium)
        }
    }
}
