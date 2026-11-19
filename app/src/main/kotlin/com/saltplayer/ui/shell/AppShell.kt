// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Corripo
package com.saltplayer.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moriafly.salt.ui.Icon
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import com.saltplayer.domain.model.LibraryView
import com.saltplayer.ui.AppViewModel
import com.saltplayer.ui.Screen
import com.saltplayer.ui.component.CoverImage
import com.saltplayer.ui.component.RoundIconButton
import com.saltplayer.ui.component.SaltSlider
import com.saltplayer.ui.component.Tag
import com.saltplayer.ui.formatDuration
import com.saltplayer.ui.i18n.Str
import com.saltplayer.ui.screens.DetailScreen
import com.saltplayer.ui.screens.LibraryScreen
import com.saltplayer.ui.screens.NowPlayingScreen
import com.saltplayer.ui.screens.SearchScreen
import com.saltplayer.ui.screens.SettingsScreen

private data class NavItem(val view: LibraryView, val icon: ImageVector, val label: Str)

private val NAV_ITEMS = listOf(
    NavItem(LibraryView.Songs, Icons.Rounded.Audiotrack, Str.Songs),
    NavItem(LibraryView.Albums, Icons.Rounded.Album, Str.Albums),
    NavItem(LibraryView.Artists, Icons.Rounded.Person, Str.Artists),
    NavItem(LibraryView.Folders, Icons.Rounded.Folder, Str.Folders),
    NavItem(LibraryView.Playlists, Icons.Rounded.QueueMusic, Str.Playlists),
    NavItem(LibraryView.Recent, Icons.Rounded.History, Str.Recent),
    NavItem(LibraryView.Favorites, Icons.Rounded.Favorite, Str.Favorites),
)

const val TABLET_MIN_WIDTH_DP = 840

/**
 * The tablet shell: a persistent navigation rail on the left, a title/搜索 bar on top, the
 * screen content in the middle and the mini player pinned to the bottom. Below
 * [TABLET_MIN_WIDTH_DP] the rail collapses into a drawer so the app stays usable in a narrow
 * window.
 */
@Composable
fun AppShell(vm: AppViewModel) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(SaltTheme.colors.background)) {
        val wide = maxWidth.value >= TABLET_MIN_WIDTH_DP
        var drawerOpen by remember { mutableStateOf(false) }

        // Nav width scales between 208 and 264 dp so a narrow tile does not squeeze the
        // content area, while a wide tile does not leave an oversized empty rail.
        val navWidth = (maxWidth.value * 0.16f).coerceIn(208f, 264f).dp
        // Compositors that tile with rounded corners (niri) clip the outermost pixels of the
        // surface. The background is painted edge-to-edge so nothing looks black, while all
        // interactive content is inset by a safe margin so it is never cut off.
        val safeInset = 12.dp
        Row(modifier = Modifier.fillMaxSize().padding(safeInset)) {
            if (wide) {
                NavigationRail(vm = vm, modifier = Modifier.width(navWidth).fillMaxHeight())
            }
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                TopBar(vm = vm, showMenuButton = !wide, onMenuClick = { drawerOpen = true })
                if (vm.scanState.running) {
                    ScanIndicator(vm)
                }
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    // Tablet content is centred and capped so lists stay readable on ultrawide
                    // windows instead of stretching a single column across the whole screen.
                    Box(modifier = Modifier.fillMaxSize().widthIn(max = 1280.dp)) {
                        ContentHost(vm)
                    }
                }
                MiniPlayerBar(vm)
            }
        }

        if (!wide && drawerOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable { drawerOpen = false },
            )
            NavigationRail(
                vm = vm,
                modifier = Modifier.width(248.dp).fillMaxHeight(),
                onNavigate = { drawerOpen = false },
            )
        }

        if (vm.nowPlayingOpen) {
            NowPlayingScreen(vm)
        }
        if (vm.queueOpen) {
            QueueSheet(vm)
        }
        vm.toast?.let { message ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Box(
                    modifier = Modifier
                        .padding(bottom = 104.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(SaltTheme.colors.popup)
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                ) {
                    Text(text = message, color = SaltTheme.colors.text, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun NavigationRail(vm: AppViewModel, modifier: Modifier = Modifier, onNavigate: () -> Unit = {}) {
    val s = vm.strings
    Column(
        modifier = modifier
            .background(SaltTheme.colors.subBackground.copy(alpha = 0.55f))
            .padding(horizontal = 12.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(SaltTheme.colors.highlight),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "♫", color = SaltTheme.colors.onHighlight, fontSize = 15.sp)
            }
            Text(text = s[Str.AppName], color = SaltTheme.colors.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(6.dp))

        for (item in NAV_ITEMS) {
            val selected = (vm.screen as? Screen.Library)?.view == item.view
            NavRow(
                icon = item.icon,
                label = s[item.label],
                selected = selected,
                onClick = {
                    vm.openScreen(Screen.Library(item.view))
                    onNavigate()
                },
            )
        }
        Spacer(Modifier.weight(1f))
        NavRow(
            icon = Icons.Rounded.Settings,
            label = s[Str.Settings],
            selected = vm.screen is Screen.Settings,
            onClick = {
                vm.openScreen(Screen.Settings)
                onNavigate()
            },
        )
        Spacer(Modifier.height(4.dp))
        val backendLabel = vm.playerState.backendName.ifBlank {
            vm.backends.firstOrNull { it.available }?.displayName ?: vm.strings[com.saltplayer.ui.i18n.Str.Loading]
        }
        Text(
            text = "v${com.saltplayer.BuildInfo.VERSION} · $backendLabel",
            color = SaltTheme.colors.subText,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun NavRow(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) SaltTheme.colors.highlight.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) SaltTheme.colors.highlight else SaltTheme.colors.subText,
            modifier = Modifier.size(19.dp),
        )
        Text(
            text = label,
            color = if (selected) SaltTheme.colors.highlight else SaltTheme.colors.text,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

@Composable
private fun TopBar(vm: AppViewModel, showMenuButton: Boolean, onMenuClick: () -> Unit) {
    val s = vm.strings
    val focus = remember { FocusRequester() }
    val title = when (val screen = vm.screen) {
        is Screen.Library -> s[
            when (screen.view) {
                LibraryView.Songs -> Str.Songs
                LibraryView.Albums -> Str.Albums
                LibraryView.Artists -> Str.Artists
                LibraryView.Folders -> Str.Folders
                LibraryView.Playlists -> Str.Playlists
                LibraryView.Recent -> Str.Recent
                LibraryView.Favorites -> Str.Favorites
            }
        ]

        is Screen.Settings -> s[Str.Settings]
        is Screen.Search -> s[Str.Search]
        is Screen.AlbumDetail, is Screen.ArtistDetail, is Screen.PlaylistDetail, is Screen.FolderDetail -> ""
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(SaltTheme.colors.background)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (showMenuButton) {
            RoundIconButton(Icons.Rounded.Audiotrack, "menu", onMenuClick, size = 36.dp)
        }
        if (vm.searchActive) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SaltTheme.colors.subBackground)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Rounded.Search, null, tint = SaltTheme.colors.subText, modifier = Modifier.size(17.dp))
                BasicTextField(
                    value = vm.searchQuery,
                    onValueChange = {
                        vm.searchQuery = it
                        vm.screen = Screen.Search
                    },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = SaltTheme.colors.text,
                        fontSize = 14.sp,
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(SaltTheme.colors.highlight),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.weight(1f).focusRequester(focus),
                    decorationBox = { inner ->
                        if (vm.searchQuery.isEmpty()) {
                            Text(text = s[Str.SearchHint], color = SaltTheme.colors.subText, fontSize = 14.sp)
                        }
                        inner()
                    },
                )
                RoundIconButton(Icons.Rounded.Close, s[Str.Close], {
                    vm.searchActive = false
                    vm.searchQuery = ""
                    vm.openScreen(Screen.Library(LibraryView.Songs))
                }, size = 26.dp, tint = SaltTheme.colors.subText)
            }
            LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        } else {
            Text(
                text = title,
                color = SaltTheme.colors.text,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (vm.scanState.running) {
                Tag(text = s[Str.Scanning])
            }
            RoundIconButton(Icons.Rounded.Search, s[Str.Search], {
                vm.searchActive = true
                vm.screen = Screen.Search
            }, size = 36.dp, tint = SaltTheme.colors.subText)
            RoundIconButton(Icons.Rounded.PlaylistPlay, s[Str.Queue], {
                vm.queueOpen = true
            }, size = 36.dp, tint = SaltTheme.colors.subText)
            RoundIconButton(Icons.Rounded.MoreVert, s[Str.More], {
                vm.openScreen(Screen.Settings)
            }, size = 36.dp, tint = SaltTheme.colors.subText)
        }
    }
}

@Composable
private fun ScanIndicator(vm: AppViewModel) {
    val s = vm.strings
    val progress = if (vm.scanState.total > 0) {
        vm.scanState.scanned.toFloat() / vm.scanState.total
    } else {
        0f
    }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${s[Str.Scanning]} ${vm.scanState.scanned}/${vm.scanState.total}",
                color = SaltTheme.colors.subText,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = s[Str.ScanCancel],
                color = SaltTheme.colors.highlight,
                fontSize = 12.sp,
                modifier = Modifier.clickable { vm.cancelScan() },
            )
        }
        SaltSlider(value = progress, onValueChange = {}, enabled = false)
    }
}

@Composable
private fun ContentHost(vm: AppViewModel) {
    when (val screen = vm.screen) {
        is Screen.Library -> LibraryScreen(vm, screen.view)
        is Screen.AlbumDetail, is Screen.ArtistDetail, is Screen.PlaylistDetail, is Screen.FolderDetail ->
            DetailScreen(vm, screen)

        Screen.Search -> SearchScreen(vm)
        Screen.Settings -> SettingsScreen(vm)
    }
}

@Composable
private fun MiniPlayerBar(vm: AppViewModel) {
    val state = vm.playerState
    val track = state.track
    val s = vm.strings
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SaltTheme.colors.subBackground)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        if (track != null) {
            SaltSlider(
                value = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f,
                onValueChange = { fraction ->
                    vm.player.seekTo((fraction * state.durationMs).toLong())
                },
                onValueChangeFinished = { vm.player.saveSession() },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(62.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (track == null) {
                Text(text = s[Str.NoResults], color = SaltTheme.colors.subText, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                return@Column
            }
            Row(
                modifier = Modifier.weight(1f).clickable { vm.nowPlayingOpen = true },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CoverImage(store = vm.coverStore, trackId = track.id, size = 46.dp, corner = 8.dp)
                Column {
                    Text(
                        text = track.displayTitle,
                        color = SaltTheme.colors.text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${track.displayArtist} · ${formatDuration(state.positionMs)} / ${formatDuration(state.durationMs)}",
                        color = SaltTheme.colors.subText,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            RoundIconButton(Icons.Rounded.Favorite, s[Str.Favorite], {
                vm.toggleFavorite(track)
            }, size = 34.dp, tint = if (track.favorite) SaltTheme.colors.error else SaltTheme.colors.subText)
            RoundIconButton(Icons.Rounded.SkipPrevious, s[Str.Previous], { vm.player.previous() }, size = 38.dp)
            RoundIconButton(
                icon = if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (state.playing) s[Str.Pause] else s[Str.Play],
                onClick = { vm.player.togglePlayPause() },
                size = 44.dp,
                background = SaltTheme.colors.highlight,
                tint = SaltTheme.colors.onHighlight,
            )
            RoundIconButton(Icons.Rounded.SkipNext, s[Str.Next], { vm.player.next() }, size = 38.dp)
            RoundIconButton(Icons.Rounded.QueueMusic, s[Str.Queue], { vm.queueOpen = true }, size = 34.dp, tint = SaltTheme.colors.subText)
            RoundIconButton(Icons.Rounded.KeyboardArrowUp, s[Str.NowPlaying], { vm.nowPlayingOpen = true }, size = 34.dp, tint = SaltTheme.colors.subText)
        }
    }
}

/** Queue sheet: reorder, jump, remove, clear. */
@Composable
private fun QueueSheet(vm: AppViewModel) {
    val s = vm.strings
    val queueIds = vm.playerState.queue
    val queueTracks = remember(queueIds) { queueIds.mapNotNull { vm.trackById(it) } }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable { vm.queueOpen = false },
        contentAlignment = Alignment.CenterEnd,
    ) {
        Column(
            modifier = Modifier
                .width(420.dp)
                .fillMaxHeight()
                .background(SaltTheme.colors.background)
                .clickable(enabled = false) {}
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${s[Str.Queue]} · ${queueTracks.size}",
                    color = SaltTheme.colors.text,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = s[Str.ClearQueue],
                    color = SaltTheme.colors.error,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable { vm.player.clearQueue() },
                )
                Spacer(Modifier.width(12.dp))
                RoundIconButton(Icons.Rounded.Close, s[Str.Close], { vm.queueOpen = false }, size = 30.dp)
            }
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.weight(1f)) {
                items(queueTracks.size) { index ->
                    val track = queueTracks[index]
                    val current = index == vm.playerState.index
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (current) SaltTheme.colors.highlight.copy(alpha = 0.1f) else Color.Transparent)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = track.displayTitle,
                            color = if (current) SaltTheme.colors.highlight else SaltTheme.colors.text,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).clickable { vm.player.jumpTo(index) },
                        )
                        Text(
                            text = "↑",
                            color = SaltTheme.colors.subText,
                            fontSize = 13.sp,
                            modifier = Modifier.clickable { if (index > 0) vm.player.moveInQueue(index, index - 1) },
                        )
                        Text(
                            text = "↓",
                            color = SaltTheme.colors.subText,
                            fontSize = 13.sp,
                            modifier = Modifier.clickable {
                                if (index < queueTracks.lastIndex) vm.player.moveInQueue(index, index + 1)
                            },
                        )
                        Text(
                            text = "✕",
                            color = SaltTheme.colors.subText,
                            fontSize = 13.sp,
                            modifier = Modifier.clickable { vm.player.removeFromQueue(index) },
                        )
                    }
                }
            }
        }
    }
}
