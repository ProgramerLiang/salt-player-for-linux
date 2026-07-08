package com.saltplayer.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.UnstableSaltUiApi
import com.saltplayer.AppState
import com.saltplayer.model.Song

@OptIn(UnstableSaltUiApi::class)
@Composable
fun LibraryScreen(
    appState: AppState,
    modifier: Modifier = Modifier
) {
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
                text = "音乐库",
                style = MaterialTheme.typography.headlineMedium,
                color = SaltTheme.colors.text
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { appState.scanLibrary() }) {
                Text(
                    if (appState.isScanning) "扫描中…" else "重新扫描",
                    color = SaltTheme.colors.subText
                )
            }
        }

        if (appState.songs.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "暂无歌曲\n请先在设置中配置音乐目录并扫描",
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
                items(appState.songs) { song ->
                    SongRow(
                        song = song,
                        isActive = appState.playbackState.value.songId == song.id,
                        onClick = { appState.playSong(song.id) }
                    )
                }
            }
        }
    }
}

@OptIn(UnstableSaltUiApi::class)
@Composable
private fun SongRow(
    song: Song,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val bg = if (isActive) SaltTheme.colors.highlight else SaltTheme.colors.background
    val fg = if (isActive) SaltTheme.colors.text else SaltTheme.colors.subText

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Cover
        if (song.coverArt != null) {
            AsyncImage(
                model = "data:${song.coverArt.mimeType};base64,${song.coverArt.data}",
                contentDescription = song.title,
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(SaltTheme.colors.highlight),
                contentAlignment = Alignment.Center
            ) {
                Text("♪", color = SaltTheme.colors.subText)
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = fg,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row {
                if (song.artist.isNotBlank()) {
                    Text(
                        text = song.artist,
                        color = SaltTheme.colors.subText,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (song.artist.isNotBlank() && song.album.isNotBlank()) {
                    Text(
                        text = " — ",
                        color = SaltTheme.colors.subText,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (song.album.isNotBlank()) {
                    Text(
                        text = song.album,
                        color = SaltTheme.colors.subText,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(Modifier.width(8.dp))

        // Duration
        Text(
            text = formatDuration(song.duration),
            color = SaltTheme.colors.subText,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun formatDuration(seconds: Double): String {
    val m = (seconds / 60).toInt()
    val s = (seconds % 60).toInt()
    return "%d:%02d".format(m, s)
}
