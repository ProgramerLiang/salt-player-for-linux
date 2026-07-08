package com.saltplayer.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.UnstableSaltUiApi
import com.saltplayer.AppState
import com.saltplayer.model.PlayMode

@OptIn(UnstableSaltUiApi::class)
@Composable
fun SettingsScreen(
    appState: AppState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        Text(
            text = "设置",
            style = MaterialTheme.typography.headlineMedium,
            color = SaltTheme.colors.text,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        Spacer(Modifier.height(8.dp))

        // Music directory
        SettingsGroup(title = "音乐目录") {
            SettingsRow(
                label = appState.config.musicDirectory.ifBlank { "未设置" },
                subtitle = "选择音乐文件所在目录",
                onClick = {
                    // Open JFileChooser
                    val chooser = javax.swing.JFileChooser()
                    chooser.fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
                    chooser.dialogTitle = "选择音乐目录"
                    if (appState.config.musicDirectory.isNotBlank()) {
                        chooser.currentDirectory = java.io.File(appState.config.musicDirectory)
                    } else {
                        chooser.currentDirectory = java.io.File(System.getProperty("user.home"))
                    }
                    val result = chooser.showOpenDialog(null)
                    if (result == javax.swing.JFileChooser.APPROVE_OPTION) {
                        val dir = chooser.selectedFile.absolutePath
                        appState.setMusicDirectory(dir)
                    }
                }
            )
        }

        Spacer(Modifier.height(16.dp))

        // Play mode
        SettingsGroup(title = "播放模式") {
            PlayMode.entries.forEach { mode ->
                val isSelected = appState.config.playMode == mode
                SettingsRow(
                    label = modeLabel(mode),
                    subtitle = modeDescription(mode),
                    selected = isSelected,
                    onClick = { appState.setPlayMode(mode) }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Volume
        SettingsGroup(title = "音量") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SaltTheme.colors.subBackground)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${(appState.config.volume * 100).toInt()}%",
                    color = SaltTheme.colors.text,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.width(48.dp)
                )
                Slider(
                    value = appState.config.volume,
                    onValueChange = { appState.setVolume(it) },
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = SaltTheme.colors.text,
                        activeTrackColor = SaltTheme.colors.text
                    )
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Theme
        SettingsGroup(title = "主题") {
            listOf("auto" to "跟随系统", "light" to "浅色", "dark" to "深色").forEach { (value, label) ->
                val isSelected = appState.config.theme == value
                SettingsRow(
                    label = label,
                    selected = isSelected,
                    onClick = {
                        appState.config = appState.config.copy(theme = value)
                    }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // About
        SettingsGroup(title = "关于") {
            SettingsRow(
                label = "Salt Player for Linux",
                subtitle = "版本 0.1.0-alpha",
                onClick = {}
            )
            SettingsRow(
                label = "许可证",
                subtitle = "MIT License",
                onClick = {}
            )
        }
    }
}

@OptIn(UnstableSaltUiApi::class)
@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            color = SaltTheme.colors.subText,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SaltTheme.colors.subBackground),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            content()
        }
    }
}

@OptIn(UnstableSaltUiApi::class)
@Composable
private fun SettingsRow(
    label: String,
    subtitle: String? = null,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                color = SaltTheme.colors.text,
                style = MaterialTheme.typography.bodyLarge
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = SaltTheme.colors.subText,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        if (selected) {
            Text(
                text = "✓",
                color = SaltTheme.colors.text,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

private fun modeLabel(mode: PlayMode): String = when (mode) {
    PlayMode.SEQUENTIAL -> "顺序播放"
    PlayMode.SHUFFLE -> "随机播放"
    PlayMode.REPEAT_ONE -> "单曲循环"
    PlayMode.REPEAT_ALL -> "列表循环"
}

private fun modeDescription(mode: PlayMode): String = when (mode) {
    PlayMode.SEQUENTIAL -> "按顺序播放队列中的歌曲"
    PlayMode.SHUFFLE -> "随机打乱队列"
    PlayMode.REPEAT_ONE -> "重复播放当前歌曲"
    PlayMode.REPEAT_ALL -> "队列播放完毕后从头开始"
}
