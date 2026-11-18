// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Corripo
package com.saltplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.moriafly.salt.ui.dialog.YesNoDialog
import com.saltplayer.domain.model.Track
import com.saltplayer.ui.AppViewModel
import com.saltplayer.ui.Screen
import com.saltplayer.ui.component.CoverImage
import com.saltplayer.ui.component.EmptyState
import com.saltplayer.ui.component.RoundIconButton
import com.saltplayer.ui.component.TrackRow
import com.saltplayer.ui.i18n.Str

@Composable
fun DetailScreen(vm: AppViewModel, screen: Screen) {
    when (screen) {
        is Screen.AlbumDetail -> {
            val album = vm.albumByKey(screen.key)
            if (album == null) {
                BackHeader(vm, "")
                EmptyState(title = vm.strings[Str.NoResults])
            } else {
                DetailLayout(
                    vm = vm,
                    title = album.title,
                    subtitle = "${album.artist} · ${album.trackCount} · ${vm.strings.format(Str.TrackCount, album.trackCount)}",
                    coverTrackId = album.coverTrackId,
                    tracks = album.tracks,
                )
            }
        }

        is Screen.ArtistDetail -> {
            val artist = vm.artistByName(screen.name)
            if (artist == null) {
                BackHeader(vm, "")
                EmptyState(title = vm.strings[Str.NoResults])
            } else {
                DetailLayout(
                    vm = vm,
                    title = artist.name,
                    subtitle = "${vm.strings.format(Str.AlbumCount, artist.albumCount)} · ${vm.strings.format(Str.TrackCount, artist.trackCount)}",
                    coverTrackId = artist.tracks.firstOrNull { it.hasCover }?.id,
                    tracks = artist.tracks,
                )
            }
        }

        is Screen.PlaylistDetail -> {
            val playlist = vm.playlistById(screen.id)
            var tracks by remember(screen.id) { mutableStateOf<List<Track>>(emptyList()) }
            LaunchedEffect(screen.id) { tracks = vm.playlistTracks(screen.id) }
            var confirmDelete by remember { mutableStateOf(false) }
            DetailLayout(
                vm = vm,
                title = playlist?.name ?: vm.strings[Str.Playlists],
                subtitle = vm.strings.format(Str.TrackCount, tracks.size),
                coverTrackId = tracks.firstOrNull { it.hasCover }?.id,
                tracks = tracks,
                extraAction = {
                    RoundIconButton(Icons.Rounded.Delete, vm.strings[Str.Delete], { confirmDelete = true }, size = 34.dp)
                },
                onRemove = { track -> vm.removeFromPlaylist(screen.id, track.id) },
            )
            if (confirmDelete && playlist != null) {
                YesNoDialog(
                    onDismissRequest = { confirmDelete = false },
                    onConfirm = {
                        vm.deletePlaylist(playlist.id)
                        confirmDelete = false
                    },
                    title = vm.strings[Str.Delete],
                    content = vm.strings.format(Str.DeletePlaylistConfirm, playlist.name),
                )
            }
        }

        is Screen.FolderDetail -> {
            val node = vm.folderByPath(screen.path)
            if (node == null) {
                BackHeader(vm, screen.path)
                EmptyState(title = vm.strings[Str.NoResults])
            } else {
                DetailLayout(
                    vm = vm,
                    title = node.name,
                    subtitle = vm.strings.format(Str.TrackCount, node.trackCount),
                    coverTrackId = node.tracks.firstOrNull()?.id,
                    tracks = node.tracks,
                )
            }
        }

        else -> Unit
    }
}

@Composable
private fun DetailLayout(
    vm: AppViewModel,
    title: String,
    subtitle: String,
    coverTrackId: Long?,
    tracks: List<Track>,
    extraAction: @Composable (() -> Unit)? = null,
    onRemove: ((Track) -> Unit)? = null,
) {
    val s = vm.strings
    Column(modifier = Modifier.fillMaxSize()) {
        BackHeader(vm, title)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CoverImage(store = vm.coverStore, trackId = coverTrackId, size = 132.dp, corner = 14.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = SaltTheme.colors.text,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(text = subtitle, color = SaltTheme.colors.subText, fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { vm.playTracks(tracks, 0) },
                        text = s[Str.PlayAll],
                        enabled = tracks.isNotEmpty(),
                    )
                    Text(
                        text = s[Str.ShuffleAll],
                        color = SaltTheme.colors.highlight,
                        fontSize = 13.sp,
                        modifier = Modifier.clickable { vm.playTracks(tracks.shuffled(), 0) },
                    )
                    extraAction?.invoke()
                }
            }
        }
        if (tracks.isEmpty()) {
            EmptyState(title = s[Str.EmptyPlaylist])
            return@Column
        }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            itemsIndexed(tracks, key = { index, track -> "${track.id}-$index" }) { index, track ->
                TrackRow(
                    track = track,
                    index = index + 1,
                    playing = vm.playerState.track?.id == track.id && vm.playerState.playing,
                    showCover = true,
                    store = vm.coverStore,
                    onClick = { vm.playTracks(tracks, index) },
                    onSecondary = {
                        if (onRemove != null) onRemove(track) else vm.trackInfoTrack = track
                    },
                )
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable
private fun BackHeader(vm: AppViewModel, title: String) {
    Row(
        modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        RoundIconButton(Icons.Rounded.ArrowBack, vm.strings[Str.Back], { vm.navigateBack() }, size = 36.dp)
        Text(
            text = title,
            color = SaltTheme.colors.text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
