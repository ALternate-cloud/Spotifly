package com.example.spotifly.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class AccentTheme(
    val name: String,
    val primary: Color,
    val bright: Color
)

val AccentThemes = listOf(
    AccentTheme("Spotify Green", Color(0xFF1DB954), Color(0xFF1ED760)),
    AccentTheme("Neon Cyan", Color(0xFF00E5CC), Color(0xFF63FFEC)),
    AccentTheme("Ultra Violet", Color(0xFF9D6BFF), Color(0xFFBD9EFF)),
    AccentTheme("Electric Blue", Color(0xFF3D9BFF), Color(0xFF6FB6FF)),
    AccentTheme("Sunset Amber", Color(0xFFFF9F1C), Color(0xFFFFBE5C)),
    AccentTheme("Rose Punch", Color(0xFFFF4D6D), Color(0xFFFF758F))
)

val LocalAccentTheme = staticCompositionLocalOf { AccentThemes.first() }

/**
 * Every surface colour the app draws. The names below are historic (Spotify-*)
 * but are read through this palette, so one theme switch repaints the whole app.
 */
@Immutable
data class SpotiflyPalette(
    val background: Color,
    val surface: Color,
    val card: Color,
    val elevated: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val divider: Color,
    val error: Color,
    val onAccent: Color,
    val scrim: Color,
    val isDark: Boolean
)

val DarkPalette = SpotiflyPalette(
    background = Color(0xFF121212),
    surface = Color(0xFF181818),
    card = Color(0xFF242424),
    elevated = Color(0xFF282828),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFB3B3B3),
    divider = Color(0xFF2A2A2A),
    error = Color(0xFFE91429),
    onAccent = Color(0xFF121212),
    scrim = Color(0xFF000000),
    isDark = true
)

val LightPalette = SpotiflyPalette(
    background = Color(0xFFF7F7F7),
    surface = Color(0xFFFFFFFF),
    card = Color(0xFFEDEDED),
    elevated = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF121212),
    textSecondary = Color(0xFF5C5C5C),
    divider = Color(0xFFD9D9D9),
    error = Color(0xFFB3261E),
    onAccent = Color(0xFF121212),
    scrim = Color(0xFF000000),
    isDark = false
)

val LocalSpotiflyPalette = staticCompositionLocalOf { DarkPalette }

// Semantic accessors. Composables resolve these against the active palette,
// which is why existing screens did not need a per-screen refactor.
/** Fixed near-black: text/icons that sit on the bright accent, and image scrims. */
val SpotifyBlack = Color(0xFF121212)

/** The themed page background. Use this instead of SpotifyBlack behind content. */
val SpotifyBackground: Color @androidx.compose.runtime.Composable get() = LocalSpotiflyPalette.current.background
val SpotifyDarkGray: Color @androidx.compose.runtime.Composable get() = LocalSpotiflyPalette.current.surface
val SpotifyCard: Color @androidx.compose.runtime.Composable get() = LocalSpotiflyPalette.current.card
val SpotifyElevated: Color @androidx.compose.runtime.Composable get() = LocalSpotiflyPalette.current.elevated
val SpotifyWhite: Color @androidx.compose.runtime.Composable get() = LocalSpotiflyPalette.current.textPrimary
val SpotifyTextSecondary: Color @androidx.compose.runtime.Composable get() = LocalSpotiflyPalette.current.textSecondary
val SpotifyDivider: Color @androidx.compose.runtime.Composable get() = LocalSpotiflyPalette.current.divider
val SpotifyError: Color @androidx.compose.runtime.Composable get() = LocalSpotiflyPalette.current.error

/** Text and icons that sit on the bright accent fill, in both themes. */
val SpotifyOnAccent: Color @androidx.compose.runtime.Composable get() = LocalSpotiflyPalette.current.onAccent

/** Always-dark scrim for text over artwork, independent of the theme. */
val SpotifyScrim: Color @androidx.compose.runtime.Composable get() = LocalSpotiflyPalette.current.scrim

/**
 * Resolves to the user-selected accent color. Composable getter so every
 * existing call site switches theme automatically.
 */
val SpotifyGreen: Color @androidx.compose.runtime.Composable get() = LocalAccentTheme.current.primary
val SpotifyGreenBright: Color @androidx.compose.runtime.Composable get() = LocalAccentTheme.current.bright
