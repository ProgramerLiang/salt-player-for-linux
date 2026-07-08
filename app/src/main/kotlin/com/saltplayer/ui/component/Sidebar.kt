package com.saltplayer.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.UnstableSaltUiApi
import com.saltplayer.Screen

@OptIn(UnstableSaltUiApi::class)
@Composable
fun Sidebar(
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(220.dp)
            .fillMaxHeight()
            .background(SaltTheme.colors.subBackground)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Spacer(Modifier.height(24.dp))

        SidebarItem("音乐库", currentScreen == Screen.LIBRARY) {
            onNavigate(Screen.LIBRARY)
        }
        SidebarItem("播放队列", currentScreen == Screen.PLAYLIST) {
            onNavigate(Screen.PLAYLIST)
        }
        SidebarItem("正在播放", currentScreen == Screen.NOW_PLAYING) {
            onNavigate(Screen.NOW_PLAYING)
        }

        Spacer(Modifier.weight(1f))

        SidebarItem("设置", currentScreen == Screen.SETTINGS) {
            onNavigate(Screen.SETTINGS)
        }
    }
}

@OptIn(UnstableSaltUiApi::class)
@Composable
private fun SidebarItem(
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    val bg = if (active) SaltTheme.colors.highlight else SaltTheme.colors.subBackground
    val fg = if (active) SaltTheme.colors.text else SaltTheme.colors.subText

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            color = fg,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
