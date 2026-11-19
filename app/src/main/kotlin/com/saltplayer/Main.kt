// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Corripo
package com.saltplayer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.window.WindowPlacement
import com.saltplayer.core.AppPaths
import com.saltplayer.core.Log
import com.saltplayer.data.db.LibraryDatabase
import com.saltplayer.data.lyrics.LyricsRepository
import com.saltplayer.data.media.CoverStore
import com.saltplayer.data.prefs.CloseBehavior
import com.saltplayer.data.prefs.WindowMode
import com.saltplayer.data.prefs.SettingsStore
import com.saltplayer.data.scan.LibraryScanner
import com.saltplayer.data.session.SessionStore
import com.saltplayer.platform.SingleInstance
import com.saltplayer.platform.mpris.MprisServer
import com.saltplayer.playback.EngineRegistry
import com.saltplayer.playback.PlayerController
import com.saltplayer.domain.queue.PlayMode
import com.saltplayer.ui.AppViewModel
import com.saltplayer.ui.i18n.Str
import com.saltplayer.ui.shell.AppShell
import com.saltplayer.ui.theme.AppTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.nio.file.Path

fun main() {
    System.setProperty("java.awt.headless", "false")
    val paths = AppPaths()
    paths.ensureDirectories()
    Log.attach(paths.logFile)

    val singleInstance = SingleInstance(paths.singleInstanceSocket)
    if (!singleInstance.acquire()) {
        singleInstance.signalActivate()
        println("Salt Player is already running — asked the existing window to front.")
        return
    }
    BuildInfo.singleInstanceOwner = "primary (pid ${ProcessHandle.current().pid()})"

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database = LibraryDatabase(paths.libraryDbFile)
    val settingsStore = SettingsStore(paths.settingsFile)
    val coverStore = CoverStore(paths.coverDir)
    val lyricsRepository = LyricsRepository()
    val scanner = LibraryScanner(database, coverStore)
    val registry = EngineRegistry()
    val sessionStore = SessionStore(paths.sessionFile)

    lateinit var viewModel: AppViewModel
    val player = PlayerController(
        registry = registry,
        db = database,
        sessionStore = sessionStore,
        settingsProvider = { viewModel.settings },
        scope = scope,
        resolveTrack = { id -> viewModel.trackById(id) },
    )
    viewModel = AppViewModel(
        paths = paths,
        db = database,
        settingsStore = settingsStore,
        coverStore = coverStore,
        lyricsRepository = lyricsRepository,
        scanner = scanner,
        player = player,
        registry = registry,
        scope = scope,
    )

    val mpris = MprisServer(controls = MprisControls(viewModel))
    val mprisStarted = mpris.start()
    if (mprisStarted) {
        scope.launch {
            player.state.collect { state -> mpris.update(state, state.track) }
        }
    }

    scope.launch { viewModel.start() }

    application {
        // No hard-coded window size: niri (and other tiling compositors) own the surface
        // size. Forcing 1180x800 dp left the Compose canvas smaller than the window and
        // produced an unpainted dark band on the right/bottom edge.
        // Tiling compositors own the surface size. On niri a windowed surface ends up larger
        // than the size the toolkit reports, which shows as black bands or clipped rounded
        // corners, so the default is fullscreen and the mode is user-selectable in Settings.
        val windowState = rememberWindowState(
            placement = when (viewModel.settings.windowMode) {
                WindowMode.Fullscreen -> WindowPlacement.Fullscreen
                WindowMode.Maximized -> WindowPlacement.Maximized
                WindowMode.Windowed -> WindowPlacement.Floating
            },
        )
        var windowVisible by remember { mutableStateOf(true) }
        val trayState = rememberTrayState()
        val uiScope = rememberCoroutineScope()
        val strings = viewModel.strings

        LaunchedEffect(Unit) {
            while (true) {
                delay(700)
                viewModel.refreshSystemTheme()
                if (singleInstance.consumeActivation()) {
                    windowVisible = true
                    windowState.isMinimized = false
                }
            }
        }

        if (!windowVisible) {
            // Keep the process alive while living in the tray.
            trayState
        }

        Window(
            onCloseRequest = {
                if (viewModel.settings.closeBehavior == CloseBehavior.MinimizeToTray && viewModel.settings.showTray) {
                    windowVisible = false
                } else {
                    shutdown(scope, player, mpris, database, singleInstance)
                    exitApplication()
                }
            },
            state = windowState,
            visible = windowVisible,
            // In fullscreen the toolkit frame insets would otherwise leave an unpainted
            // strip along the right and bottom edges.
            undecorated = viewModel.settings.windowMode == WindowMode.Fullscreen,
            title = System.getProperty("salt.player.windowTitle") ?: strings[Str.AppName],
        ) {

            AppTheme(
                settings = viewModel.settings,
                systemDark = viewModel.systemDark,
                systemAccentName = viewModel.systemAccentName,
            ) {
                androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
                    AppShell(viewModel)
                }
            }
        }

        if (viewModel.settings.showTray) {
            Tray(
                icon = rememberVectorPainter(Icons.Rounded.MusicNote),
                state = trayState,
                tooltip = strings[Str.AppName],
                onAction = { windowVisible = true },
                menu = {
                    Item(strings[Str.ShowWindow], onClick = { windowVisible = true })
                    Item(strings[if (viewModel.playerState.playing) Str.Pause else Str.Play]) {
                        uiScope.launch { viewModel.player.togglePlayPause() }
                    }
                    Item(strings[Str.Next]) { uiScope.launch { viewModel.player.next() } }
                    Item(strings[Str.Exit], onClick = {
                        shutdown(scope, player, mpris, database, singleInstance)
                        exitApplication()
                    })
                },
            )
        }
    }
}

private fun shutdown(
    scope: CoroutineScope,
    player: PlayerController,
    mpris: MprisServer,
    database: LibraryDatabase,
    singleInstance: SingleInstance,
) {
    runCatching { player.saveSession() }
    runCatching { mpris.close() }
    runCatching { player.close() }
    runCatching { scope.coroutineContext[kotlinx.coroutines.Job]?.cancel() }
    runCatching { database.close() }
    runCatching { singleInstance.close() }
}

private fun Int.dp() = androidx.compose.ui.unit.Dp(this.toFloat())

/** MPRIS adaptor: translates D-Bus calls into playback-controller commands. */
private class MprisControls(private val vm: AppViewModel) : MprisServer.Controls {
    override fun state() = vm.playerState
    override fun play() = vm.player.play()
    override fun pause() = vm.player.pause()
    override fun playPause() = vm.player.togglePlayPause()
    override fun next() = vm.player.next()
    override fun previous() = vm.player.previous()
    override fun stop() {
        vm.player.pause()
        vm.player.seekTo(0)
    }

    override fun seekTo(positionMs: Long) = vm.player.seekTo(positionMs)
    override fun seekBy(deltaMs: Long) = vm.player.seekTo((vm.playerState.positionMs + deltaMs).coerceAtLeast(0))
    override fun setVolume(volume: Double) = vm.player.setVolume(volume.toFloat())
    override fun setPlayMode(mode: PlayMode) = vm.player.setMode(mode)
    override fun artworkUri(track: com.saltplayer.domain.model.Track?): String? {
        val id = track?.id ?: return null
        val file: Path = vm.coverStore.fullCover(id) ?: return null
        return file.toUri().toString()
    }
}
