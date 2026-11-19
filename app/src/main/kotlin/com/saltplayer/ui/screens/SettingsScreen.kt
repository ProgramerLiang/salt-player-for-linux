// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Corripo
package com.saltplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moriafly.salt.ui.Button
import com.moriafly.salt.ui.RoundedColumn
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import com.moriafly.salt.ui.dialog.InputDialog
import com.moriafly.salt.ui.dialog.YesNoDialog
import com.saltplayer.BuildInfo
import com.saltplayer.data.prefs.AppLanguage
import com.saltplayer.data.prefs.BackendPreference
import com.saltplayer.data.prefs.CloseBehavior
import com.saltplayer.data.prefs.EqualizerSettings
import com.saltplayer.data.prefs.ReplayGainMode
import com.saltplayer.data.prefs.Settings
import com.saltplayer.data.prefs.ThemeMode
import com.saltplayer.data.prefs.WindowMode
import com.saltplayer.core.UiScale
import com.saltplayer.platform.AppControl
import com.saltplayer.platform.FolderPicker
import com.saltplayer.platform.SystemTheme
import com.saltplayer.ui.AppViewModel
import com.saltplayer.ui.component.ContentMetrics
import com.saltplayer.ui.component.SaltSlider
import com.saltplayer.ui.component.SettingsPanel
import com.saltplayer.ui.component.SectionTitle
import com.saltplayer.ui.component.Tag
import com.saltplayer.ui.i18n.Str
import com.saltplayer.ui.theme.AccentPalette

@Composable
fun SettingsScreen(vm: AppViewModel, forceEqualizer: Boolean = false) {
    val s = vm.strings
    var addFolderDialog by remember { mutableStateOf(false) }
    var newFolder by remember { mutableStateOf("") }
    var deletePlaylistId by remember { mutableStateOf<Long?>(null) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val panelMargin = if (maxWidth < 720.dp) ContentMetrics.PanelOuterMarginNarrow
        else ContentMetrics.PanelOuterMargin
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(ContentMetrics.SectionSpacing),
        ) {
        SettingsPanel(outerMargin = panelMargin, title = s[Str.Library]) {
            for (dir in vm.settings.musicDirs) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = dir, color = SaltTheme.colors.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text(
                        text = s[Str.Delete],
                        color = SaltTheme.colors.error,
                        fontSize = 12.sp,
                        modifier = Modifier.clickable { vm.removeMusicDir(dir) },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().height(40.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "+ ${s[Str.AddFolder]}",
                    color = SaltTheme.colors.highlight,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable {
                        val picked = FolderPicker.pick()
                        if (picked != null) vm.addMusicDir(picked) else addFolderDialog = true
                    },
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (vm.scanState.running) s[Str.Scanning] else s[Str.Scan],
                    color = SaltTheme.colors.highlight,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable { vm.rescan(full = false) },
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = s[Str.ScanAll],
                    color = SaltTheme.colors.highlight,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable { vm.rescan(full = true) },
                )
            }
            vm.scanState.lastSummary?.let {
                Text(text = it, color = SaltTheme.colors.subText, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
            }
            SwitchRow(
                label = s[Str.ScanOnStartup],
                value = vm.settings.scanOnStartup,
                onChange = { value -> vm.updateSettings { it.copy(scanOnStartup = value) } },
            )
            SwitchRow(
                label = s[Str.FollowSymlinks],
                value = vm.settings.scanFollowSymlinks,
                onChange = { value -> vm.updateSettings { it.copy(scanFollowSymlinks = value) } },
            )
        }

        SettingsPanel(outerMargin = panelMargin, title = s[Str.Audio]) {
            for (backend in vm.backends) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = backend.displayName,
                                color = if (backend.available) SaltTheme.colors.text else SaltTheme.colors.subText,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(Modifier.width(8.dp))
                            if (vm.playerState.backendId == backend.id) Tag(text = "active")
                            if (!backend.available) Tag(text = s[Str.BackendUnavailable], color = SaltTheme.colors.error)
                        }
                        Text(text = backend.detail, color = SaltTheme.colors.subText, fontSize = 11.sp)
                        if (backend.available) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                for (feature in backend.capabilities.supportedFeatures()) {
                                    Tag(text = feature, color = SaltTheme.colors.subText)
                                }
                            }
                        } else {
                            backend.installHint?.let {
                                Text(
                                    text = "${s[Str.InstallHint]}: $it",
                                    color = SaltTheme.colors.warning,
                                    fontSize = 10.sp,
                                )
                            }
                        }
                    }
                    Button(
                        onClick = { vm.updateSettings { it.copy(backendPreference = backendPreferenceFor(backend.id)) } },
                        text = if (vm.settings.backendPreference == backendPreferenceFor(backend.id)) s[Str.BackendPinned] else s[Str.BackendAuto],
                        enabled = backend.available,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().height(38.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = s[Str.RescanBackends],
                    color = SaltTheme.colors.highlight,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable { vm.rescanBackendsAsync() },
                )
                Spacer(Modifier.weight(1f))
                Text(text = "${s[Str.Backend]}: ${vm.playerState.backendName}", color = SaltTheme.colors.subText, fontSize = 12.sp)
            }
            PickerRow(
                label = s[Str.ReplayGain],
                options = listOf(
                    ReplayGainMode.Off to s[Str.ReplayGainOff],
                    ReplayGainMode.Track to s[Str.ReplayGainTrack],
                    ReplayGainMode.Album to s[Str.ReplayGainAlbum],
                ),
                selected = vm.settings.replayGain,
                onSelect = { mode -> vm.updateSettings { it.copy(replayGain = mode) } },
            )
            Text(
                text = "${s[Str.OutputDevice]}: ${vm.settings.outputDevice.ifBlank { s[Str.SystemDefault] }}",
                color = SaltTheme.colors.subText,
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        }

        EqualizerSection(vm, panelMargin)

        SettingsPanel(outerMargin = panelMargin, title = s[Str.Appearance]) {
            PickerRow(
                label = s[Str.Theme],
                options = listOf(
                    ThemeMode.System to s[Str.ThemeSystem],
                    ThemeMode.Light to s[Str.ThemeLight],
                    ThemeMode.Dark to s[Str.ThemeDark],
                ),
                selected = vm.settings.themeMode,
                onSelect = { mode -> vm.updateSettings { it.copy(themeMode = mode) } },
            )
            SwitchRow(
                label = s[Str.UseSystemAccent],
                value = vm.settings.useSystemAccent,
                onChange = { value -> vm.updateSettings { it.copy(useSystemAccent = value) } },
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = s[Str.AccentColor], color = SaltTheme.colors.text, fontSize = 14.sp, modifier = Modifier.width(110.dp))
                Tag(
                    text = s[Str.UseSystemAccent],
                    color = if (vm.settings.useSystemAccent) SaltTheme.colors.highlight else SaltTheme.colors.subText,
                    modifier = Modifier.clickable { vm.updateSettings { it.copy(useSystemAccent = true, accentColor = null) } },
                )
                for ((_, color) in AccentPalette.entries) {
                    val selected = vm.settings.accentColor == color.toArgb().toLong()
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(color)
                            .clickable { vm.updateSettings { it.copy(accentColor = color.toArgb().toLong(), useSystemAccent = false) } },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) Text(text = "✓", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
            // Interface scale: multiplies dp and sp, so everything grows together. Live preview
            // comes for free because the value lives in the observed settings state.
            val followSystem = vm.settings.uiScalePercent == UiScale.FOLLOW_SYSTEM
            val systemScale = remember { SystemTheme.detectScale() }
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = s[Str.UiScale],
                        color = SaltTheme.colors.text,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Tag(
                        text = s[Str.UiScaleFollowSystem],
                        color = if (followSystem) SaltTheme.colors.highlight else SaltTheme.colors.subText,
                        modifier = Modifier.clickable {
                            vm.updateSettings { it.copy(uiScalePercent = UiScale.FOLLOW_SYSTEM) }
                        },
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f)) {
                        val factor = UiScale.factor(vm.settings.uiScalePercent, systemScale)
                        SaltSlider(
                            value = ((factor * 100f) - UiScale.MIN_PERCENT) /
                                (UiScale.MAX_PERCENT - UiScale.MIN_PERCENT),
                            onValueChange = { fraction ->
                                val percent = (UiScale.MIN_PERCENT +
                                    fraction * (UiScale.MAX_PERCENT - UiScale.MIN_PERCENT)).toInt()
                                vm.updateSettings { it.copy(uiScalePercent = UiScale.snapPercent(percent)) }
                            },
                        )
                    }
                    Text(
                        text = if (followSystem) "跟随系统 ${(systemScale * 100).toInt()}%" else
                            "${vm.settings.uiScalePercent}%",
                        color = SaltTheme.colors.subText,
                        fontSize = 11.sp,
                        modifier = Modifier.width(96.dp),
                    )
                }
                Text(text = s[Str.UiScaleNote], color = SaltTheme.colors.subText, fontSize = 11.sp)
            }
            SliderRow(
                label = s[Str.FontScale],
                value = (vm.settings.fontScale - 0.8f) / 0.8f,
                valueLabel = "%.2fx".format(vm.settings.fontScale),
                onChange = { fraction -> vm.updateSettings { it.copy(fontScale = 0.8f + fraction * 0.8f) } },
            )
            PickerRow(
                label = s[Str.Language],
                options = listOf(
                    AppLanguage.System to s[Str.LanguageSystem],
                    AppLanguage.Chinese to s[Str.LanguageChinese],
                    AppLanguage.English to s[Str.LanguageEnglish],
                ),
                selected = vm.settings.language,
                onSelect = { language -> vm.updateSettings { it.copy(language = language) } },
            )
        }

        SettingsPanel(outerMargin = panelMargin, title = s[Str.Behaviour]) {
            PickerRow(
                label = s[Str.WindowModeLabel],
                options = listOf(
                    WindowMode.Fullscreen to s[Str.WindowModeFullscreen],
                    WindowMode.Maximized to s[Str.WindowModeMaximized],
                    WindowMode.Windowed to s[Str.WindowModeWindowed],
                ),
                selected = vm.settings.windowMode,
                onSelect = { mode ->
                    if (mode != vm.settings.windowMode) {
                        vm.updateSettings { it.copy(windowMode = mode) }
                        // Compose Desktop cannot re-decorate a live native window without a
                        // restart (doing so crashes under XWayland), so say it explicitly.
                        vm.showToast(s[Str.WindowModeNeedsRestart])
                    }
                },
            )
            if (AppControl.restart != null) {
                Text(
                    text = s[Str.RestartNow],
                    color = SaltTheme.colors.highlight,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .clickable { AppControl.restart?.invoke() },
                )
            }
            PickerRow(
                label = s[Str.CloseBehaviour],
                options = listOf(
                    CloseBehavior.Quit to s[Str.CloseQuit],
                    CloseBehavior.MinimizeToTray to s[Str.CloseMinimize],
                ),
                selected = vm.settings.closeBehavior,
                onSelect = { behavior -> vm.updateSettings { it.copy(closeBehavior = behavior) } },
            )
            SwitchRow(
                label = s[Str.ShowTray],
                value = vm.settings.showTray && AppControl.traySupported,
                onChange = { value ->
                    if (AppControl.traySupported) {
                        vm.updateSettings { it.copy(showTray = value) }
                    } else {
                        vm.showToast(s[Str.TrayUnavailable])
                    }
                },
            )
            if (!AppControl.traySupported) {
                Text(
                    text = s[Str.TrayUnavailable],
                    color = SaltTheme.colors.warning,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
            PickerRow(
                label = s[Str.SleepTimer],
                options = listOf(0, 15, 30, 45, 60, 90).map { minutes ->
                    minutes to if (minutes == 0) s[Str.SleepTimerOff] else s.format(Str.SleepTimerMinutes, minutes)
                },
                selected = vm.settings.sleepTimerMinutes,
                onSelect = { minutes -> vm.setSleepTimer(minutes) },
            )
        }

        SettingsPanel(outerMargin = panelMargin, title = s[Str.DesktopIntegration]) {
            InfoLine(s[Str.MprisEnabled], if (BuildInfo.mprisActive) "org.mpris.MediaPlayer2.saltplayer" else s[Str.BackendUnavailable])
            InfoLine(s[Str.SingleInstance], BuildInfo.singleInstanceOwner)
        }

        SettingsPanel(outerMargin = panelMargin, title = s[Str.About]) {
            InfoLine(s[Str.Version], BuildInfo.VERSION)
            InfoLine(s[Str.License], "LGPL-3.0-or-later")
            InfoLine(s[Str.AudioBackend], vm.playerState.backendName)
            InfoLine(s[Str.ThirdParty], "SaltUI (Apache-2.0) · GStreamer · SQLite · D-Bus")
            Text(
                text = "Salt Player for Linux · © 2021-2026 Moriafly · Linux port © 2026 Corripo",
                color = SaltTheme.colors.subText,
                fontSize = 11.sp,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        }
    }

    if (addFolderDialog) {
        InputDialog(
            onDismissRequest = { addFolderDialog = false },
            onConfirm = {
                vm.addMusicDir(newFolder.trim())
                addFolderDialog = false
                newFolder = ""
            },
            onChange = { newFolder = it },
            title = s[Str.AddFolder],
            text = newFolder,
            hint = "/home/user/Music",
        )
    }
}

private fun backendPreferenceFor(id: String): BackendPreference = when (id) {
    "gstreamer" -> BackendPreference.GStreamer
    "libmpv" -> BackendPreference.LibMpv
    "javasound" -> BackendPreference.JavaSound
    else -> BackendPreference.Auto
}

@Composable
private fun EqualizerSection(vm: AppViewModel, panelMargin: androidx.compose.ui.unit.Dp) {
    val s = vm.strings
    val eq = vm.settings.equalizer
    SettingsPanel(outerMargin = panelMargin, title = s[Str.Equalizer]) {
        SwitchRow(
            label = s[Str.EqualizerEnabled],
            value = eq.enabled,
            onChange = { enabled -> vm.updateSettingsAutoEq { it.copy(enabled = enabled) } },
            enabled = vm.playerState.backendFeatures.contains("equalizer"),
        )
        if (!vm.playerState.backendFeatures.contains("equalizer")) {
            Text(
                text = "current backend has no equalizer: ${vm.playerState.backendName}",
                color = SaltTheme.colors.warning,
                fontSize = 11.sp,
            )
        }
        var saveDialog by remember { mutableStateOf(false) }
        var presetName by remember { mutableStateOf("") }
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for ((name, bands) in EqualizerSettings.PRESETS) {
                Tag(
                    text = name,
                    color = if (eq.preset == name) SaltTheme.colors.highlight else SaltTheme.colors.subText,
                    modifier = Modifier.clickable {
                        vm.updateSettingsAutoEq { it.copy(preset = name, bands = bands) }
                    },
                )
            }
        }
        // User presets: saved from the current band values and kept in settings.json.
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "自定义预设",
                color = SaltTheme.colors.subText,
                fontSize = 11.sp,
            )
            for ((name, bands) in vm.settings.customPresets) {
                Tag(
                    text = if (eq.preset == name) "$name ✓" else name,
                    color = if (eq.preset == name) SaltTheme.colors.highlight else SaltTheme.colors.subText,
                    modifier = Modifier.clickable {
                        vm.updateSettingsAutoEq { it.copy(preset = name, bands = bands) }
                    },
                )
            }
            Text(
                text = "+ 保存当前",
                color = SaltTheme.colors.highlight,
                fontSize = 11.sp,
                modifier = Modifier.clickable { saveDialog = true },
            )
        }
        if (saveDialog) {
            InputDialog(
                onDismissRequest = { saveDialog = false },
                onConfirm = {
                    val result = com.saltplayer.data.prefs.EqualizerPresets.save(
                        vm.settings.customPresets, presetName, eq.bands,
                    )
                    if (result.accepted) {
                        vm.updateSettings {
                            it.copy(customPresets = result.presets, equalizer = it.equalizer.copy(preset = presetName.trim()))
                        }
                    } else {
                        vm.showToast(result.reason ?: "保存失败")
                    }
                    saveDialog = false
                    presetName = ""
                },
                onChange = { presetName = it },
                title = "保存均衡器预设",
                text = presetName,
                hint = "预设名称",
            )
        }
        if (vm.settings.customPresets.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(text = "删除：", color = SaltTheme.colors.subText, fontSize = 11.sp)
                for (name in vm.settings.customPresets.keys) {
                    Text(
                        text = "$name ✕",
                        color = SaltTheme.colors.error,
                        fontSize = 11.sp,
                        modifier = Modifier.clickable {
                            vm.updateSettings {
                                it.copy(customPresets = com.saltplayer.data.prefs.EqualizerPresets.remove(it.customPresets, name))
                            }
                        },
                    )
                }
            }
        }
        for (index in EqualizerSettings.BAND_FREQUENCIES.indices) {
            SliderRow(
                label = "${EqualizerSettings.BAND_FREQUENCIES[index]} Hz",
                value = (eq.bands.getOrElse(index) { 0f } + 12f) / 24f,
                valueLabel = "%.1f dB".format(eq.bands.getOrElse(index) { 0f }),
                onChange = { fraction ->
                    val gain = fraction * 24f - 12f
                    vm.updateSettingsAutoEq { settings ->
                        val bands = settings.bands.toMutableList()
                        while (bands.size <= index) bands.add(0f)
                        bands[index] = gain
                        settings.copy(bands = bands, preset = "Custom")
                    }
                },
            )
        }
        SliderRow(
            label = s[Str.Preamp],
            value = (eq.preampDb + 12f) / 24f,
            valueLabel = "%.1f dB".format(eq.preampDb),
            onChange = { fraction -> vm.updateSettingsAutoEq { it.copy(preampDb = fraction * 24f - 12f) } },
        )
    }
}

@Composable
private fun SwitchRow(label: String, value: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(
        modifier = Modifier.fillMaxWidth().height(40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = SaltTheme.colors.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
        com.moriafly.salt.ui.Switcher(
            state = value,
            onChange = { if (enabled) onChange(it) },
        )
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    valueLabel: String,
    onChange: (Float) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = SaltTheme.colors.text, fontSize = 12.sp, modifier = Modifier.width(76.dp))
        Box(modifier = Modifier.weight(1f)) {
            SaltSlider(value = value, onValueChange = onChange)
        }
        Text(text = valueLabel, color = SaltTheme.colors.subText, fontSize = 11.sp, modifier = Modifier.width(72.dp))
    }
}

@Composable
private fun <T> PickerRow(label: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(text = label, color = SaltTheme.colors.text, fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
            for ((value, text) in options) {
                Tag(
                    text = text,
                    color = if (value == selected) SaltTheme.colors.highlight else SaltTheme.colors.subText,
                    modifier = Modifier.clickable { onSelect(value) },
                )
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().height(34.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = SaltTheme.colors.subText, fontSize = 13.sp, modifier = Modifier.width(150.dp))
        Text(text = value, color = SaltTheme.colors.text, fontSize = 13.sp)
    }
}
