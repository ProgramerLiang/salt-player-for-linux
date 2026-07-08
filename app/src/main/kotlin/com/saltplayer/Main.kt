package com.saltplayer

import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.application
import com.moriafly.salt.ui.UnstableSaltUiApi
import com.moriafly.salt.ui.window.CaptionBarHitTest
import com.moriafly.salt.ui.window.SaltWindow
import com.moriafly.salt.ui.window.SaltWindowProperties
import com.saltplayer.ui.theme.AppTheme
import kotlinx.coroutines.delay

@OptIn(ExperimentalComposeUiApi::class, UnstableSaltUiApi::class)
fun main() = application {
    val appState = remember { AppState() }

    // Determine initial theme
    val systemIsDark = java.awt.Toolkit.getDefaultToolkit()
        .let { false } // TODO: detect system dark mode on Linux
    val initialDark = when (appState.config.theme) {
        "dark" -> true
        "light" -> false
        else -> systemIsDark
    }
    appState.isDarkTheme = initialDark

    // Poll playback position every 500ms when playing
    LaunchedEffect(appState.playbackState.value.isPlaying) {
        while (true) {
            appState.updatePlaybackPosition()
            delay(500)
        }
    }

    AppTheme(isDarkTheme = appState.isDarkTheme) {
        SaltWindow(
            onCloseRequest = {
                appState.destroy()
                exitApplication()
            },
            title = "Salt Player",
            resizable = true,
            properties = SaltWindowProperties.default(
                minSize = DpSize(800.dp, 600.dp)
            )
        ) {
            CaptionBarHitTest()
            AppLayout(appState)
        }
    }
}
