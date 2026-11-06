package com.zelsigns.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class ThemeMode(val label: String) {
    DARK("Dark"),
    LIGHT("Light"),
    CUSTOM("Custom")
}

object Terminal {
    val bg = Color(0xFF05080A)
    val text = Color(0xFF9EF7B4)
    val dim = Color(0xFF6B8F77)
    val error = Color(0xFFFF6B6B)
    val ai = Color(0xFF5CE1FF)
    val ok = Color(0xFF4ADE80)
    val warn = Color(0xFFFFD166)
}

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7C9CFF),
    onPrimary = Color(0xFF0A1030),
    secondary = Color(0xFF00E5FF),
    background = Color(0xFF0B0F1A),
    onBackground = Color(0xFFE6E9F5),
    surface = Color(0xFF121829),
    onSurface = Color(0xFFE6E9F5),
    surfaceVariant = Color(0xFF1B2338),
    onSurfaceVariant = Color(0xFFC3C9DD),
    error = Color(0xFFFF6B6B)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF3F51D9),
    onPrimary = Color.White,
    secondary = Color(0xFF00838F),
    background = Color(0xFFF7F8FC),
    onBackground = Color(0xFF14172B),
    surface = Color.White,
    onSurface = Color(0xFF14172B),
    surfaceVariant = Color(0xFFE9ECF7),
    onSurfaceVariant = Color(0xFF3A3F58),
    error = Color(0xFFB3261E)
)

// "Custom" = neon synthwave palette
private val CustomColors = darkColorScheme(
    primary = Color(0xFFFF2E93),
    onPrimary = Color.White,
    secondary = Color(0xFF00E5FF),
    onSecondary = Color(0xFF00191C),
    background = Color(0xFF150826),
    onBackground = Color(0xFFFCE8FF),
    surface = Color(0xFF220F3D),
    onSurface = Color(0xFFFCE8FF),
    surfaceVariant = Color(0xFF30175A),
    onSurfaceVariant = Color(0xFFE2C8F5),
    error = Color(0xFFFF8A80)
)

@Composable
fun SignZelTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val colors = when (mode) {
        ThemeMode.DARK -> DarkColors
        ThemeMode.LIGHT -> LightColors
        ThemeMode.CUSTOM -> CustomColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
fun systemPrefersDark(): Boolean = isSystemInDarkTheme()
