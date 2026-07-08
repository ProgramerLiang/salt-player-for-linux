package com.saltplayer.ui.theme

import androidx.compose.runtime.Composable
import com.moriafly.salt.ui.SaltConfigs
import com.moriafly.salt.ui.SaltTheme

@Composable
fun AppTheme(
    isDarkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    SaltTheme(
        configs = SaltConfigs.default(isDarkTheme = isDarkTheme),
        content = content
    )
}
