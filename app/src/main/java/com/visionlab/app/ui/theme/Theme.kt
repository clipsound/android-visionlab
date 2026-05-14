package com.visionlab.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF2563EB),
    onPrimary = Color.White,
    secondary = Color(0xFF0F172A),
    onSecondary = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF60A5FA),
    onPrimary = Color(0xFF0B1220),
    secondary = Color(0xFFE2E8F0),
    onSecondary = Color(0xFF0B1220),
)

@Composable
fun VisionLabTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
