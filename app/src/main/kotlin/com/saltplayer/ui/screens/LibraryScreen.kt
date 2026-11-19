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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moriafly.salt.ui.Button
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import com.moriafly.salt.ui.dialog.InputDialog
import com.moriafly.salt.ui.popup.PopupMenu
import com.moriafly.salt.ui.popup.PopupMenuItem
import com.saltplayer.domain.library.SongSort
import com.saltplayer.domain.model.FolderNode
import com.saltplayer.domain.model.LibraryView
import com.saltplayer.domain.model.Track
import com.saltplayer.ui.AppViewModel
import com.saltplayer.ui.Screen
import com.saltplayer.ui.component.CoverImage
import com.saltplayer.ui.component.EmptyState
import com.saltplayer.ui.component.SectionTitle
import com.saltplayer.ui.component.TrackRow
import com.saltplayer.ui.formatDuration
import com.saltplayer.ui.i18n.Str

@Composable
fun LibraryScreen(vm: AppViewModel, view: LibraryView) {
    when (view) {
        LibraryView.Songs -> SongsScreen(vm)
        LibraryView.Albums -> AlbumsScreen(vm)
        LibraryView.Artists -> ArtistsScreen(vm)
        LibraryView.Folders -> FoldersScreen(vm)
        LibraryView.Playlists -> PlaylistsScreen(vm)
        LibraryView.Recent -> TrackCollectionScreen(vm, Str.Recent, Str.EmptyRecent, vm.recentTracks)
        LibraryView.Favorites -> TrackCollectionScreen(vm, Str.Favorites, Str.EmptyFavorites, vm.favoriteTracks)
    }
}

@Composable
private fun SongsScreen(vm: AppViewModel) {
    val s = vm.strings
    var sortMenu by remember { mutableStateOf(false) }
    if (vm.tracks.isEmpty()) {
        EmptyState(
            title = s[Str.EmptyLibrary],
            hint = s[Str.EmptyLibraryHint],
            action = {
                Button(onClick = { vm.openScreen(Screen.Settings) }, text = s[Str.Settings])
            },
        )
        return
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .widthIn(max = com.saltplayer.ui.component.ContentMetrics.MaxWidth)
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = com.saltplayer.ui.component.ContentMetrics.SidePadding, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = s.format(Str.SongCount, vm.tracks.size),
                color = SaltTheme.colors.subText,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            Box {
                Text(
                    text = sortLabel(vm, vm.songSort) + if (vm.sortDescending) " ↓" else " ↑",
                    color = SaltTheme.colors.highlight,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { sortMenu = true }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
                PopupMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    for (sort in SongSort.entries) {
                        PopupMenuItem(
                            onClick = {
                                vm.changeSort(sort)
                                sortMenu = false
                            },
                            text = sortLabel(vm, sort),
                            selected = vm.songSort == sort,
                        )
                    }
                    PopupMenuItem(
                        onClick = {
                            vm.toggleSortDirection()
                            sortMenu = false
                        },
                        text = if (vm.sortDescending) "↑" else "↓",
                    )
                }
            }
        }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = com.saltplayer.ui.component.ContentMetrics.MaxWidth)
                    .fillMaxWidth()
                    .padding(horizontal = com.saltplayer.ui.component.ContentMetrics.SidePadding),
            ) {
            itemsIndexed(vm.tracks, key = { _, track -> track.id }) { index, track ->
                TrackRow(
                    track = track,
                    index = index + 1,
                    playing = vm.playerState.track?.id == track.id && vm.playerState.playing,
                    showCover = true,
                    store = vm.coverStore,
                    onClick = { vm.playTracks(vm.tracks, index) },
                    onSecondary = { vm.trackInfoTrack = track },
                )
            }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

private fun sortLabel(vm: AppViewModel, sort: SongSort): String = vm.strings[
    when (sort) {
        SongSort.Title -> Str.Songs
        SongSort.Artist -> Str.Artists
        SongSort.Album -> Str.Albums
        SongSort.Duration -> Str.Duration
        SongSort.Year -> Str.Year
        SongSort.AddedAt -> Str.Recent
    }
]

@Composable
private fun AlbumsScreen(vm: AppViewModel) {
    val s = vm.strings
    if (vm.albums.isEmpty()) {
        EmptyState(title = s[Str.EmptyLibrary], hint = s[Str.EmptyLibraryHint])
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 168.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        items(vm.albums, key = { it.key }) { album ->
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { vm.openScreen(Screen.AlbumDetail(album.key)) },
            ) {
                CoverImage(
                    store = vm.coverStore,
                    trackId = album.coverTrackId,
                    size = 168.dp,
                    corner = 12.dp,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = album.title,
                    color = SaltTheme.colors.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${album.artist} · ${s.format(Str.TrackCount, album.trackCount)}",
                    color = SaltTheme.colors.subText,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ArtistsScreen(vm: AppViewModel) {
    val s = vm.strings
    if (vm.artists.isEmpty()) {
        EmptyState(title = s[Str.EmptyLibrary], hint = s[Str.EmptyLibraryHint])
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        items(vm.artists, key = { it.name }) { artist ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { vm.openScreen(Screen.ArtistDetail(artist.name)) }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(21.dp))
                        .background(SaltTheme.colors.subBackground),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = artist.name.take(1).uppercase(), color = SaltTheme.colors.highlight, fontSize = 16.sp)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = artist.name, color = SaltTheme.colors.text, fontSize = 15.sp, maxLines = 1)
                    Text(
                        text = "${s.format(Str.AlbumCount, artist.albumCount)} · ${s.format(Str.TrackCount, artist.trackCount)}",
                        color = SaltTheme.colors.subText,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun FoldersScreen(vm: AppViewModel) {
    val s = vm.strings
    if (vm.folderTree.isEmpty()) {
        EmptyState(title = s[Str.EmptyFolders], hint = s[Str.EmptyLibraryHint])
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        for (root in vm.folderTree) {
            folderItems(vm, root, depth = 0)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.folderItems(
    vm: AppViewModel,
    node: FolderNode,
    depth: Int,
) {
    val s = vm.strings
    item(key = node.path) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable { vm.openScreen(Screen.FolderDetail(node.path)) }
                .padding(start = (10 + depth * 16).dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(text = "📁", fontSize = 15.sp)
            Text(
                text = node.name,
                color = SaltTheme.colors.text,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(text = s.format(Str.TrackCount, node.trackCount), color = SaltTheme.colors.subText, fontSize = 12.sp)
        }
    }
    for (child in node.children) {
        folderItems(vm, child, depth + 1)
    }
}

@Composable
private fun PlaylistsScreen(vm: AppViewModel) {
    val s = vm.strings
    var showCreate by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        SectionTitle(
            text = s[Str.Playlists],
            modifier = Modifier.padding(horizontal = 16.dp),
            trailing = {
                Text(
                    text = "+ " + s[Str.Create],
                    color = SaltTheme.colors.highlight,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable { showCreate = true },
                )
            },
        )
        if (vm.playlists.isEmpty()) {
            EmptyState(title = s[Str.EmptyPlaylist], hint = s[Str.PlaylistName])
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                items(vm.playlists, key = { it.id }) { playlist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { vm.openScreen(Screen.PlaylistDetail(playlist.id)) }
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(SaltTheme.colors.subBackground),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = "☰", color = SaltTheme.colors.highlight, fontSize = 16.sp)
                        }
                        Text(
                            text = playlist.name,
                            color = SaltTheme.colors.text,
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = s.format(Str.TrackCount, playlist.trackCount),
                            color = SaltTheme.colors.subText,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }

    if (showCreate) {
        InputDialog(
            onDismissRequest = { showCreate = false },
            onConfirm = {
                if (newName.isNotBlank()) {
                    vm.createPlaylistAsync(newName.trim())
                }
                showCreate = false
                newName = ""
            },
            onChange = { newName = it },
            title = s[Str.Create],
            text = newName,
            hint = s[Str.PlaylistName],
        )
    }
}

@Composable
private fun TrackCollectionScreen(vm: AppViewModel, title: Str, empty: Str, tracks: List<Track>) {
    val s = vm.strings
    Column(modifier = Modifier.fillMaxSize()) {
        if (tracks.isEmpty()) {
            EmptyState(title = s[empty])
            return@Column
        }
        SectionTitle(
            text = s[title],
            modifier = Modifier.padding(horizontal = 16.dp),
            trailing = {
                Text(
                    text = s[Str.PlayAll],
                    color = SaltTheme.colors.highlight,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable { vm.playTracks(tracks, 0) },
                )
            },
        )
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                TrackRow(
                    track = track,
                    index = index + 1,
                    playing = vm.playerState.track?.id == track.id && vm.playerState.playing,
                    showCover = true,
                    store = vm.coverStore,
                    onClick = { vm.playTracks(tracks, index) },
                    onSecondary = { vm.trackInfoTrack = track },
                )
            }
        }
    }
}
