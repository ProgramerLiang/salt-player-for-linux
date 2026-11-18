// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Corripo
package com.saltplayer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import com.saltplayer.domain.library.LibraryIndex
import com.saltplayer.ui.AppViewModel
import com.saltplayer.ui.Screen
import com.saltplayer.ui.component.EmptyState
import com.saltplayer.ui.component.SectionTitle
import com.saltplayer.ui.component.TrackRow
import com.saltplayer.ui.i18n.Str

/** Instant search across songs, albums, artists and playlists. */
@Composable
fun SearchScreen(vm: AppViewModel) {
    val s = vm.strings
    if (vm.searchQuery.isBlank()) {
        EmptyState(title = s[Str.SearchEmpty], hint = s[Str.SearchHint])
        return
    }
    val result = LibraryIndex.search(
        query = vm.searchQuery,
        tracks = vm.tracks,
        albums = vm.albums,
        artists = vm.artists,
        playlists = vm.playlists,
    )
    if (result.isEmpty) {
        EmptyState(title = s[Str.NoResults], hint = vm.searchQuery)
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        if (result.tracks.isNotEmpty()) {
            item { SectionTitle(text = "${s[Str.Songs]} · ${result.tracks.size}") }
            itemsIndexed(result.tracks, key = { _, track -> "t${track.id}" }) { index, track ->
                TrackRow(
                    track = track,
                    index = index + 1,
                    playing = vm.playerState.track?.id == track.id && vm.playerState.playing,
                    showCover = true,
                    store = vm.coverStore,
                    onClick = { vm.playTracks(result.tracks, index) },
                    onSecondary = { vm.trackInfoTrack = track },
                )
            }
        }
        if (result.albums.isNotEmpty()) {
            item { SectionTitle(text = "${s[Str.Albums]} · ${result.albums.size}") }
            items(result.albums, key = { "a${it.key}" }) { album ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .height(46.dp)
                        .clickable { vm.openScreen(Screen.AlbumDetail(album.key)) }
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${album.title} · ${album.artist}",
                        color = SaltTheme.colors.text,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (result.artists.isNotEmpty()) {
            item { SectionTitle(text = "${s[Str.Artists]} · ${result.artists.size}") }
            items(result.artists, key = { "r${it.name}" }) { artist ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .height(46.dp)
                        .clickable { vm.openScreen(Screen.ArtistDetail(artist.name)) }
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = artist.name, color = SaltTheme.colors.text, fontSize = 14.sp, maxLines = 1)
                }
            }
        }
        if (result.playlists.isNotEmpty()) {
            item { SectionTitle(text = "${s[Str.Playlists]} · ${result.playlists.size}") }
            items(result.playlists, key = { "p${it.id}" }) { playlist ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .height(46.dp)
                        .clickable { vm.openScreen(Screen.PlaylistDetail(playlist.id)) }
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = playlist.name, color = SaltTheme.colors.text, fontSize = 14.sp, maxLines = 1)
                }
            }
        }
    }
}
