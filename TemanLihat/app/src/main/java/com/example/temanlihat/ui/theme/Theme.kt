package com.example.temanlihat.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TemanLihatColors = lightColorScheme(
    primary = Color(0xFF0089DF),
    onPrimary = Color.White,
    background = Color.White,
    onBackground = Color(0xFF080808),
    surface = Color.White,
    onSurface = Color(0xFF080808),
    surfaceVariant = Color(0xFFF3F2F8),
    onSurfaceVariant = Color(0xFF76767B)
)

@Composable
fun TemanLihatTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TemanLihatColors,
        typography = Typography,
        content = content
    )
}
