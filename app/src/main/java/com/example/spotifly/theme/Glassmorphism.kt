package com.example.spotifly.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Reusable frosted glass and glassmorphism styling utilities for Spotifly:
 * - Frosted smoky dark acrylic fills with ambient light transmission
 * - Directional specular highlight borders (beveled cut-glass refraction)
 * - Accent-infused luminous neon glass surfaces
 */
object Glassmorphism {

    /** Default frosted smoky dark glass gradient */
    val darkGlassBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xD0181824),
            Color(0xB812121A),
            Color(0xC80C0C12)
        )
    )

    /** Subtle ultra-translucent frosted glass layer for cards & chips */
    val frostedPillBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0x35FFFFFF),
            Color(0x18FFFFFF),
            Color(0x0CFFFFFF)
        )
    )

    /** Deep obsidian glass container brush */
    val deepObsidianGlassBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xE014141E),
            Color(0xCC0E0E16),
            Color(0xE608080E)
        )
    )

    /** Glass pod brush for control capsules */
    val glassPodBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xF0181824),
            Color(0xE6101018),
            Color(0xF50A0A10)
        )
    )

    /** Specular cut-glass border highlight with top-left specular illumination */
    val specularBorderBrush = Brush.linearGradient(
        colors = listOf(
            Color(0x66FFFFFF),
            Color(0x28FFFFFF),
            Color(0x0AFFFFFF),
            Color(0x1AFFFFFF)
        ),
        start = Offset(0f, 0f),
        end = Offset(400f, 600f)
    )

    /** Subtle glassy border stroke */
    val subtleGlassBorder = BorderStroke(
        width = 1.dp,
        brush = specularBorderBrush
    )

    /** Transparent brush placeholder */
    val transparentBrush = Brush.linearGradient(
        colors = listOf(Color.Transparent, Color.Transparent)
    )

    /** Subtle glass fill brush for inactive tabs and wells */
    val subtleGlassFill = Brush.verticalGradient(
        colors = listOf(
            Color(0x28FFFFFF),
            Color(0x14FFFFFF)
        )
    )

    /** Accent-tinted luminous glass brush */
    fun accentGlassBrush(accent: Color, alpha: Float = 0.28f): Brush =
        Brush.verticalGradient(
            colors = listOf(
                accent.copy(alpha = (alpha * 1.35f).coerceAtMost(1f)),
                accent.copy(alpha = alpha),
                accent.copy(alpha = (alpha * 0.7f).coerceAtMost(1f))
            )
        )

    /** Accent-tinted glowing glass border brush */
    fun accentGlassBorderBrush(accent: Color): Brush =
        Brush.linearGradient(
            colors = listOf(
                accent.copy(alpha = 0.90f),
                accent.copy(alpha = 0.40f),
                Color(0x25FFFFFF),
                accent.copy(alpha = 0.60f)
            ),
            start = Offset(0f, 0f),
            end = Offset(300f, 400f)
        )

    /** Accent-tinted glowing glass border stroke */
    fun accentGlassBorder(accent: Color, width: Dp = 1.dp): BorderStroke =
        BorderStroke(
            width = width,
            brush = accentGlassBorderBrush(accent)
        )
}

/**
 * Modifier extension to apply a high-fidelity glassmorphic frosted surface
 */
fun Modifier.glassmorphic(
    shape: Shape,
    backgroundBrush: Brush = Glassmorphism.darkGlassBrush,
    borderStroke: BorderStroke = Glassmorphism.subtleGlassBorder,
    shadowElevation: Dp = 12.dp,
    spotColor: Color = Color(0x33000000)
): Modifier = this
    .shadow(elevation = shadowElevation, shape = shape, spotColor = spotColor, ambientColor = spotColor)
    .clip(shape)
    .background(backgroundBrush)
    .border(borderStroke, shape)
