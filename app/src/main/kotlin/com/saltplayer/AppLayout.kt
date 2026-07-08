package com.saltplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.UnstableSaltUiApi
import com.saltplayer.ui.component.PlayerBar
import com.saltplayer.ui.component.Sidebar
import com.saltplayer.ui.screen.*

@OptIn(UnstableSaltUiApi::class)
@Composable
fun AppLayout(
    appState: AppState
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SaltTheme.colors.background)
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // Sidebar
            Sidebar(
                currentScreen = appState.currentScreen,
                onNavigate = { appState.currentScreen = it }
            )

            // Content area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(SaltTheme.colors.background)
            ) {
                when (appState.currentScreen) {
                    Screen.LIBRARY -> LibraryScreen(appState)
                    Screen.PLAYLIST -> PlaylistScreen(appState)
                    Screen.NOW_PLAYING -> NowPlayingScreen(appState)
                    Screen.SETTINGS -> SettingsScreen(appState)
                }
            }
        }

        // Bottom player bar (always visible)
        PlayerBar(
            state = appState.playbackState.value,
            playMode = appState.config.playMode,
            volume = appState.config.volume,
            onPlayPause = { appState.togglePlayPause() },
            onNext = { appState.nextSong() },
            onPrev = { appState.prevSong() },
            onToggleMode = {
                val modes = com.saltplayer.model.PlayMode.entries
                val next = modes[(modes.indexOf(appState.config.playMode) + 1) % modes.size]
                appState.setPlayMode(next)
            },
            onVolumeChange = { appState.setVolume(it) },
            onSeek = {}
        )
    }
}
