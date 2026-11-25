package com.example.spotifly.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.spotifly.model.Lyrics
import com.example.spotifly.model.RepeatMode as AppRepeatMode
import com.example.spotifly.model.Song
import com.example.spotifly.theme.Glassmorphism
import com.example.spotifly.theme.SpotifyBlack
import com.example.spotifly.theme.SpotifyGreen
import com.example.spotifly.theme.SpotifyGreenBright
import com.example.spotifly.theme.SpotifyWhite
import kotlin.math.cos
import kotlin.math.sin

/**
 * Screen C from Reference Concept: Immersive Now Playing Screen
 *
 * Visual Highlights:
 * - Large circular album artwork disc with concentric orbital rings and neon aura
 * - Circular playback decoration tracing the artwork
 * - Real-time audio-reactive visualizer with dancing waveform frequency spectrum bars
 * - Track information with lossless audio tag and favorite control
 * - Smooth interactive seek progress bar
 * - Symmetrical controls with glowing neon Play/Pause button
 */
@Composable
fun ImmersiveNowPlayingView(
    song: Song,
    progress: Float,
    durationMs: Long,
    currentPositionMs: Long,
    isPlaying: Boolean,
    isBuffering: Boolean,
    isFavorite: Boolean,
    isShuffle: Boolean,
    repeatMode: AppRepeatMode,
    lyrics: Lyrics? = null,
    isLyricsLoading: Boolean = false,
    showLyrics: Boolean = false,
    onToggleLyrics: () -> Unit = {},
    onSeekTo: (Long) -> Unit,
    onPlayPauseClick: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onFavoriteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = SpotifyGreen
    val brightAccent = SpotifyGreenBright

    // Continuous rotation for outer circular playback decoration
    val infiniteTransition = rememberInfiniteTransition(label = "immersiveRotation")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbitRotation"
    )

    // Dynamic wave phase for the audio-reactive visualizer
    val visualizerPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "visualizerPhase"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        // Center Area: Slide between Circular Orbital Artwork and Inline Dynamic Lyrics
        ArtworkLyricsSlideStage(
            showLyrics = showLyrics,
            lyrics = lyrics,
            isLyricsLoading = isLyricsLoading,
            currentPositionMs = currentPositionMs,
            onSeekTo = onSeekTo,
            onCloseLyrics = onToggleLyrics,
            accentColor = accent,
            modifier = Modifier
                .fillMaxWidth()
                .height(310.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // 1. Concentric Ambient Neon Glow Rings
                Box(
                    modifier = Modifier
                        .size(290.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    accent.copy(alpha = if (isPlaying) 0.35f else 0.15f),
                                    accent.copy(alpha = 0.08f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // 2. Circular Playback Decoration: Outer Orbital Ring with Progress Arc
                Canvas(
                    modifier = Modifier
                        .size(270.dp)
                        .rotate(if (isPlaying) rotationAngle else 0f)
                ) {
                    val canvasSize = size.minDimension
                    val radius = canvasSize / 2f - 4.dp.toPx()
                    val center = Offset(size.width / 2f, size.height / 2f)

                    // Faint orbital circular track
                    drawCircle(
                        color = Color(0x25FFFFFF),
                        radius = radius,
                        center = center,
                        style = Stroke(width = 1.5.dp.toPx())
                    )

                    // Neon circular playback arc accent
                    drawArc(
                        color = accent,
                        startAngle = 0f,
                        sweepAngle = 75f,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f),
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Secondary subtle arc
                    drawArc(
                        color = Color(0x60FFFFFF),
                        startAngle = 180f,
                        sweepAngle = 45f,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f),
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Orbital indicator satellite bead
                    val beadAngleRad = Math.toRadians(75.0)
                    val beadX = center.x + radius * cos(beadAngleRad).toFloat()
                    val beadY = center.y + radius * sin(beadAngleRad).toFloat()
                    drawCircle(
                        color = Color.White,
                        radius = 4.dp.toPx(),
                        center = Offset(beadX, beadY)
                    )
                }

                // 3. Tappable Central Circular Album Artwork Disc with cut-glass specular rim
                Box(
                    modifier = Modifier
                        .size(210.dp)
                        .shadow(32.dp, CircleShape, ambientColor = accent, spotColor = accent)
                        .clip(CircleShape)
                        .background(Color(0xFF101016))
                        .border(1.5.dp, Glassmorphism.specularBorderBrush, CircleShape)
                        .border(4.dp, accent.copy(alpha = 0.65f), CircleShape)
                        .border(8.dp, Color(0xFF14141E), CircleShape)
                        .clickable(onClick = onToggleLyrics),
                    contentAlignment = Alignment.Center
                ) {
                    if (!song.artworkUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = song.artworkUrl,
                            contentDescription = "${song.title} album cover, tap for lyrics",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = "${song.title} album cover, tap for lyrics",
                            tint = accent,
                            modifier = Modifier.size(56.dp)
                        )
                    }

                    // Inner radial vignette scrim for vinyl depth
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(Color.Transparent, Color(0x70000000)),
                                    radius = 220f
                                )
                            )
                    )

                    // Subtle bottom lyrics hint inside circular disc
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 14.dp)
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Glassmorphism.darkGlassBrush)
                            .border(1.dp, Glassmorphism.specularBorderBrush, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lyrics,
                            contentDescription = null,
                            tint = brightAccent,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }

        // 4. Audio-Reactive Visualizer: 28 Frequency Spectrum Waveform Bars
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val barCount = 28
                for (i in 0 until barCount) {
                    val normalizedDistFromCenter = kotlin.math.abs(i - barCount / 2f) / (barCount / 2f)
                    val baseFactor = (1f - normalizedDistFromCenter * 0.45f).coerceIn(0.2f, 1f)

                    val dynamicHeight = if (isPlaying) {
                        val wave1 = sin(visualizerPhase + i * 0.45f).toFloat()
                        val wave2 = cos(visualizerPhase * 1.5f + i * 0.3f).toFloat()
                        val combined = ((wave1 + wave2 + 2f) / 4f) * baseFactor
                        (8.dp + 32.dp * combined).coerceIn(6.dp, 40.dp)
                    } else {
                        (6.dp + 4.dp * baseFactor)
                    }

                    Box(
                        modifier = Modifier
                            .width(3.2.dp)
                            .height(dynamicHeight)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        brightAccent,
                                        accent,
                                        accent.copy(alpha = 0.5f)
                                    )
                                )
                            )
                    )
                }
            }
        }

        // 5. Track Information Section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Frosted Glass Lossless Audio Tag
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Glassmorphism.accentGlassBrush(accent, 0.22f))
                    .border(1.dp, Glassmorphism.accentGlassBorderBrush(accent), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "LOSSLESS AUDIO • IMMERSIVE",
                    color = SpotifyGreenBright,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Track Title
            Text(
                text = song.title,
                color = SpotifyWhite,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Artist Name
            Text(
                text = song.artist,
                color = Color(0xFFD6D6E0),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }

        // 6. Interactive Seek Bar / Progress
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        ) {
            var isDragging by remember { mutableStateOf(false) }
            var dragProgress by remember { mutableFloatStateOf(0f) }
            val activeProgress = if (isDragging) dragProgress else progress.coerceIn(0f, 1f)

            // Seek track
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .pointerInput(durationMs) {
                        detectTapGestures { offset ->
                            val newFraction = (offset.x / size.width).coerceIn(0f, 1f)
                            if (durationMs > 0) {
                                onSeekTo((newFraction * durationMs).toLong())
                            }
                        }
                    }
                    .pointerInput(durationMs) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                isDragging = true
                                dragProgress = (offset.x / size.width).coerceIn(0f, 1f)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                dragProgress = (change.position.x / size.width).coerceIn(0f, 1f)
                            },
                            onDragEnd = {
                                isDragging = false
                                if (durationMs > 0) {
                                    onSeekTo((dragProgress * durationMs).toLong())
                                }
                            },
                            onDragCancel = { isDragging = false }
                        )
                    },
                contentAlignment = Alignment.CenterStart
            ) {
                // Frosted Glass Background Track
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0x35FFFFFF))
                        .border(0.5.dp, Glassmorphism.specularBorderBrush, RoundedCornerShape(2.dp))
                )

                // Filled Progress Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth(activeProgress)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(brightAccent, accent)
                            )
                        )
                )

                // Glowing Glass Scrubber Thumb
                Box(
                    modifier = Modifier
                        .fillMaxWidth(activeProgress)
                        .offset(x = (-6).dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Box(
                        modifier = Modifier
                            .size(15.dp)
                            .shadow(10.dp, CircleShape, ambientColor = accent, spotColor = accent)
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(2.dp, accent, CircleShape)
                    )
                }
            }

            // Timestamps: Current / Duration
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTime(currentPositionMs),
                    color = Color(0x99FFFFFF),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = formatTime(durationMs),
                    color = Color(0x99FFFFFF),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // 7. Frosted Glass Control Capsule with Concentric Glowing Play/Pause
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(20.dp, RoundedCornerShape(40.dp), ambientColor = Color.Black)
                .clip(RoundedCornerShape(40.dp))
                .background(Glassmorphism.glassPodBrush)
                .border(1.2.dp, Glassmorphism.specularBorderBrush, RoundedCornerShape(40.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Shuffle Button in glass well
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(if (isShuffle) Glassmorphism.accentGlassBrush(accent) else Glassmorphism.subtleGlassFill)
                        .border(
                            1.dp,
                            if (isShuffle) Glassmorphism.accentGlassBorderBrush(accent) else Glassmorphism.specularBorderBrush,
                            CircleShape
                        )
                        .clickable(onClick = onToggleShuffle),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (isShuffle) SpotifyGreenBright else Color(0x99FFFFFF),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Previous Button
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onSkipPrevious),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = SpotifyWhite,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Central Glowing Neon Play / Pause Button
                CurvedNeonPlayButton(
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    onClick = onPlayPauseClick,
                    size = 76.dp,
                    accentColor = accent
                )

                // Next Button
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onSkipNext),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = SpotifyWhite,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Favorite Button in glass well
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(if (isFavorite) Glassmorphism.accentGlassBrush(accent) else Glassmorphism.subtleGlassFill)
                        .border(
                            1.dp,
                            if (isFavorite) Glassmorphism.accentGlassBorderBrush(accent) else Glassmorphism.specularBorderBrush,
                            CircleShape
                        )
                        .clickable(onClick = onFavoriteClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) accent else Color(0x99FFFFFF),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
