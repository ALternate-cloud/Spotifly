package com.example.spotifly.ui.onboarding

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.spotifly.data.online.OnlineMusicService
import com.example.spotifly.theme.Glassmorphism
import com.example.spotifly.theme.SpotifyBlack
import com.example.spotifly.theme.SpotifyCard
import com.example.spotifly.theme.SpotifyGreen
import com.example.spotifly.theme.SpotifyGreenBright
import com.example.spotifly.theme.SpotifyTextSecondary
import com.example.spotifly.theme.SpotifyWhite
import kotlin.math.cos
import kotlin.math.sin

/**
 * Screen A from Reference Concept: Discover / Onboarding Screen
 *
 * Visual Highlights:
 * - Radial album artwork collage arranged in a curved fan/arc
 * - Central discovery badge with waveform icon, "Discover Music Made For You", and subtitle
 * - Flowing curved lyric typography along the outer fan perimeter
 * - Bottom floating pill dock with attractive "Start" button and glowing neon orb
 */
@Composable
fun DiscoverOnboardingScreen(
    onStartClick: () -> Unit,
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = onStartClick
) {
    var animationStarted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        animationStarted = true
    }

    val fanTransition by animateFloatAsState(
        targetValue = if (animationStarted) 1f else 0f,
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label = "fanTransition"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "onboardingPulse")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    // Curated showcase tracks for the radial collage
    val showcaseSongs = remember {
        OnlineMusicService.trendingHits.take(5)
    }

    val accent = SpotifyGreen

    val cardGradients = remember {
        listOf(
            Brush.linearGradient(listOf(Color(0xFF1E3A8A), Color(0xFF0F172A))),
            Brush.linearGradient(listOf(Color(0xFF065F46), Color(0xFF022C22))),
            Brush.linearGradient(listOf(Color(0xFF831843), Color(0xFF500724))),
            Brush.linearGradient(listOf(Color(0xFF78350F), Color(0xFF451A03))),
            Brush.linearGradient(listOf(Color(0xFF581C87), Color(0xFF3B0764)))
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF08080B))
    ) {
        // Atmospheric radial stage lighting
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            accent.copy(alpha = 0.30f),
                            Color(0x181014),
                            Color.Transparent
                        ),
                        center = Offset(240f, 450f),
                        radius = 800f
                    )
                )
        )

        // Dark Vignette Scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xE608080B),
                            Color(0x6008080B),
                            Color(0xF508080B)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(36.dp))

            // Center-Left Radial Composition: Discover Disc + Arc Fan Collage
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(460.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                // 1. Curved Outer Perimeter with Flowing Musical Lyrics
                Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val center = Offset(size.width * 0.28f, size.height * 0.50f)
                    val outerRadius = size.width * 0.68f

                    // Subtle outer arc track
                    drawArc(
                        color = Color(0x22FFFFFF),
                        startAngle = 280f,
                        sweepAngle = 145f * fanTransition,
                        useCenter = false,
                        topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
                        size = androidx.compose.ui.geometry.Size(outerRadius * 2f, outerRadius * 2f),
                        style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Secondary neon arc highlight
                    drawArc(
                        color = accent.copy(alpha = 0.50f),
                        startAngle = 310f,
                        sweepAngle = 65f * fanTransition,
                        useCenter = false,
                        topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
                        size = androidx.compose.ui.geometry.Size(outerRadius * 2f, outerRadius * 2f),
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // 2. Radial Collage Tiles: Fan of 5 Artist / Album Artwork Cards hugging the arc
                showcaseSongs.forEachIndexed { index, song ->
                    // Angles sweeping around the right side of the disc (from -60° to +60°)
                    val targetAngleDeg = -55f + index * 28f
                    val currentAngleDeg = targetAngleDeg * fanTransition
                    val angleRad = Math.toRadians(currentAngleDeg.toDouble())

                    val orbitRadius = 160.dp
                    val offsetX = (orbitRadius.value * cos(angleRad)).dp + 40.dp
                    val offsetY = (orbitRadius.value * sin(angleRad)).dp

                    Box(
                        modifier = Modifier
                            .offset(x = offsetX, y = offsetY)
                            .size(76.dp)
                            .shadow(16.dp, RoundedCornerShape(16.dp), ambientColor = accent)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Glassmorphism.darkGlassBrush)
                            .border(1.5.dp, Glassmorphism.specularBorderBrush, RoundedCornerShape(16.dp))
                            .rotate(currentAngleDeg * 0.35f)
                    ) {
                        if (!song.artworkUrl.isNullOrEmpty()) {
                            AsyncImage(
                                model = song.artworkUrl,
                                contentDescription = song.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(cardGradients[index % cardGradients.size]),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = song.title.take(1),
                                    color = Color.White,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Glassy specular reflection sheen over the artwork
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(Color(0x38FFFFFF), Color(0x06FFFFFF), Color(0x60000000)),
                                        start = Offset(0f, 0f),
                                        end = Offset(180f, 180f)
                                    )
                                )
                        )
                    }
                }

                // 3. Central Discovery Circular Disc: Frosted Dark Obsidian Glass
                Box(
                    modifier = Modifier
                        .size(246.dp)
                        .shadow(32.dp, CircleShape, ambientColor = accent, spotColor = accent)
                        .clip(CircleShape)
                        .background(Glassmorphism.deepObsidianGlassBrush)
                        .border(1.5.dp, Glassmorphism.specularBorderBrush, CircleShape)
                        .border(6.dp, Color(0x20FFFFFF), CircleShape)
                        .border(10.dp, Color(0x14141E), CircleShape)
                        .padding(22.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Top-edge glass gloss sheen
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color(0x22FFFFFF), Color.Transparent),
                                    startY = 0f,
                                    endY = 160f
                                )
                            )
                    )
                    Column(
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Animated Waveform Icon Glyphs
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalAlignment = Alignment.Bottom,
                            modifier = Modifier.height(20.dp)
                        ) {
                            for (bar in 0 until 4) {
                                val barHeight = 6.dp + (12.dp * (0.5f + 0.5f * sin(wavePhase + bar * 0.9f).toFloat()))
                                Box(
                                    modifier = Modifier
                                        .width(3.5.dp)
                                        .height(barHeight)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(accent)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Title Typography from reference: "Discover Music Made For You"
                        Text(
                            text = "Discover",
                            color = SpotifyWhite,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 26.sp
                        )
                        Text(
                            text = "Music Made",
                            color = accent,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 26.sp
                        )
                        Text(
                            text = "For You",
                            color = SpotifyWhite,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 26.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Subtitle
                        Text(
                            text = "Enjoy personalized music every moment.",
                            color = SpotifyTextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Bottom Floating Pill Navigation Dock with "Start" Button: Frosted Glass Capsule
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 36.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .shadow(24.dp, RoundedCornerShape(50.dp), ambientColor = Color.Black)
                        .clip(RoundedCornerShape(50.dp))
                        .background(Glassmorphism.darkGlassBrush)
                        .border(1.2.dp, Glassmorphism.specularBorderBrush, RoundedCornerShape(50.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Left circular glass back button
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color(0x28FFFFFF))
                                .border(1.dp, Glassmorphism.specularBorderBrush, CircleShape)
                                .clickable(onClick = onBackClick),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color(0xDDFFFFFF),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Attractive Glowing Glass "Start" Button from Screen A
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color(0x38FFFFFF),
                                            Color(0x18FFFFFF)
                                        )
                                    )
                                )
                                .border(1.2.dp, Glassmorphism.accentGlassBorderBrush(accent), RoundedCornerShape(50.dp))
                                .clickable(onClick = onStartClick)
                                .padding(start = 28.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                            Text(
                                text = "Start",
                                color = SpotifyWhite,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )

                            // Glowing circular neon glass orb with play arrow
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(SpotifyGreenBright, accent)
                                        )
                                    )
                                    .border(1.dp, Color(0x60FFFFFF), CircleShape)
                                    .shadow(14.dp, CircleShape, ambientColor = accent),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = "Start",
                                    tint = SpotifyBlack,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
