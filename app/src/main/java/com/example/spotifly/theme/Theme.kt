package com.example.spotifly.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun spotiflyColorScheme(accent: AccentTheme, palette: SpotiflyPalette) =
    if (palette.isDark) {
        darkColorScheme(
            primary = accent.primary,
            onPrimary = palette.onAccent,
            primaryContainer = palette.card,
            onPrimaryContainer = palette.textPrimary,
            secondary = accent.bright,
            onSecondary = palette.onAccent,
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.surface,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.card,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.divider,
            error = palette.error,
            onError = palette.textPrimary
        )
    } else {
        lightColorScheme(
            primary = accent.primary,
            onPrimary = palette.onAccent,
            primaryContainer = palette.card,
            onPrimaryContainer = palette.textPrimary,
            secondary = accent.bright,
            onSecondary = palette.onAccent,
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.surface,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.card,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.divider,
            error = palette.error,
            onError = palette.textPrimary
        )
    }

@Composable
fun SpotiflyTheme(
    accent: AccentTheme = AccentThemes.first(),
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val palette = if (darkTheme) DarkPalette else LightPalette
    CompositionLocalProvider(
        LocalAccentTheme provides accent,
        LocalSpotiflyPalette provides palette
    ) {
        MaterialTheme(
            colorScheme = spotiflyColorScheme(accent, palette),
            typography = Typography,
            content = content
        )
    }
}
