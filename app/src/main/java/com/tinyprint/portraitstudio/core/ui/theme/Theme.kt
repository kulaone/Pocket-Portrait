package com.tinyprint.portraitstudio.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = NeonViolet,
    secondary = NeonCyan,
    background = Slate900,
    surface = Slate800,
    onPrimary = Slate100,
    onSecondary = Slate900,
    onBackground = Slate100,
    onSurface = Slate100,
    error = CrimsonError
)

@Composable
fun PortraitStudioTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
