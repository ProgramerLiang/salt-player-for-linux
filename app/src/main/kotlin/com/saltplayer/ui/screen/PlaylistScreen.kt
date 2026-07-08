package com.saltplayer.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.UnstableSaltUiApi
import com.saltplayer.AppState

@OptIn(UnstableSaltUiApi::class)
@Composable
fun PlaylistScreen(
    appState: AppState,
    modifier: Modifier = Modifier
) {
    val queue = appState.playQueue
    val songs = queue.getSongs()
    val current = queue.getCurrentIndex()
    val state = appState.playbackState.value

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "播放队列",
                style = MaterialTheme.typography.headlineMedium,
                color = SaltTheme.colors.text
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${songs.size} 首",
                color = SaltTheme.colors.subText
            )
        }

        if (songs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "播放队列为空\n从音乐库中选择歌曲开始播放",
                    color = SaltTheme.colors.subText,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                itemsIndexed(songs) { index, song ->
                    val isActive = index == current &&
                            state.songId == song.id &&
                            state.isPlaying

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isActive) SaltTheme.colors.highlight
                                else SaltTheme.colors.background
                            )
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { appState.playSong(song.id) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${index + 1}",
                            color = SaltTheme.colors.subText,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.width(28.dp)
                        )

                        Column(Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                color = if (isActive) SaltTheme.colors.text
                                else SaltTheme.colors.subText,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (song.artist.isNotBlank()) {
                                Text(
                                    text = song.artist,
                                    color = SaltTheme.colors.subText,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (isActive) {
                            Text(
                                text = "▶",
                                color = SaltTheme.colors.text,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}
