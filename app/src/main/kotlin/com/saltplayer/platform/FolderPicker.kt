// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Corripo
package com.saltplayer.platform

import com.saltplayer.core.Log
import javax.swing.JFileChooser
import javax.swing.UIManager

/**
 * Native directory chooser. Compose Desktop has no file-picker API, so the AWT/Swing chooser is
 * used; failures are reported instead of being swallowed, and the settings screen falls back to
 * typing a path manually.
 */
object FolderPicker {

    fun pick(startDirectory: String? = null): String? = runCatching {
        runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
        val chooser = JFileChooser()
        chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        chooser.dialogTitle = "Choose a music folder"
        startDirectory?.let { chooser.currentDirectory = java.io.File(it) }
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile?.absolutePath
        } else {
            null
        }
    }.onFailure { Log.warn("FolderPicker", "directory chooser failed", it) }.getOrNull()
}
