package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.data.model.AccentPalette
import com.example.data.model.ThemeMode

private fun buildLightScheme(accent: AccentPalette): ColorScheme = lightColorScheme(
    primary = accent.primaryLight,
    onPrimary = Color.White,
    primaryContainer = accent.primaryLight.copy(alpha = 0.14f),
    onPrimaryContainer = accent.primaryLight,
    secondary = accent.secondaryLight,
    onSecondary = Color.White,
    secondaryContainer = LightSurfaceVariant,
    onSecondaryContainer = LightOnSurface,
    tertiary = SecretAmber,
    onTertiary = Color.Black,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFF1F5F9),
    surfaceContainerHighest = Color(0xFFE2E8F0),
    outline = LightOutline,
    outlineVariant = LightOutlineVariant
)

private fun buildDarkScheme(accent: AccentPalette): ColorScheme = darkColorScheme(
    primary = accent.primaryDark,
    onPrimary = Color(0xFF00211C),
    primaryContainer = accent.primaryDark.copy(alpha = 0.18f),
    onPrimaryContainer = accent.primaryDark,
    secondary = accent.secondaryDark,
    onSecondary = Color(0xFF142420),
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = DarkOnSurface,
    tertiary = SecretAmber,
    onTertiary = Color.Black,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainerLowest = DarkBackground,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurfaceVariant,
    surfaceContainerHighest = Color(0xFF233734),
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant
)

@Composable
fun ChatGauravTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accentPalette: AccentPalette = AccentPalette.EMERALD,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = if (isDark) buildDarkScheme(accentPalette) else buildLightScheme(accentPalette)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
