package com.furyform.knata.sample.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF0066CC),
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = androidx.compose.ui.graphics.Color(0xFFD7E9FF),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF001C3D),
    secondary = androidx.compose.ui.graphics.Color(0xFF006D3A),
    onSecondary = androidx.compose.ui.graphics.Color.White,
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFFD6F7E5),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFF00210F),
    background = androidx.compose.ui.graphics.Color(0xFFF9FBFF),
    surface = androidx.compose.ui.graphics.Color.White,
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFFE6EEF9),
    onSurface = androidx.compose.ui.graphics.Color(0xFF101213),
    error = androidx.compose.ui.graphics.Color(0xFFBA1A1A),
    onError = androidx.compose.ui.graphics.Color.White,
)

private val DarkColors = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF9CC7FF),
    onPrimary = androidx.compose.ui.graphics.Color(0xFF003258),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF00497A),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFD7E9FF),
    secondary = androidx.compose.ui.graphics.Color(0xFF7FDAA5),
    onSecondary = androidx.compose.ui.graphics.Color(0xFF00391B),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFF00522B),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFFB6F2C9),
    background = androidx.compose.ui.graphics.Color(0xFF001F11),
    surface = androidx.compose.ui.graphics.Color(0xFF001F11),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF11323C),
    onSurface = androidx.compose.ui.graphics.Color(0xFFF3F5F7),
    error = androidx.compose.ui.graphics.Color(0xFFFFB4AB),
    onError = androidx.compose.ui.graphics.Color(0xFF410001),
)

@Composable
fun KnataTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colors,
        typography = androidx.compose.material3.Typography(),
        content = content,
    )
}
