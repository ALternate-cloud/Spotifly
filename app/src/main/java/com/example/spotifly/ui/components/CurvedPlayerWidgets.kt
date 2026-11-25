package com.example.spotifly.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spotifly.theme.Glassmorphism
import com.example.spotifly.theme.SpotifyGreen
import com.example.spotifly.theme.SpotifyGreenBright
import com.example.spotifly.model.RepeatMode as AppRepeatMode
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Distinctive Curved Music Player Console:
 * - Sweeping convex upward progress arc spanning across controls
 * - Heart / Favorite toggle integrated directly below the arc apex (slightly above)
 * - Live timestamp frosted glass pill ("2:46 / 3:15") positioned slightly above
 * - Small curved accent line gracefully hovering slightly above the play button
 * - Clean, vibrant, open transport controls without dulling container outlines
 * - Concentric neon glowing Play/Pause button with vivid energy ring and solid white core
 * - Symmetrical Previous/Next and Shuffle/Repeat buttons
 */
@Composable
fun CurvedPlayerConsole(
    progress: Float,
    durationMs: Long,
    currentPositionMs: Long,
    isPlaying: Boolean,
    isBuffering: Boolean,
    isFavorite: Boolean,
    isShuffle: Boolean,
    repeatMode: AppRepeatMode,
    onSeekTo: (Long) -> Unit,
    onPlayPauseClick: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onFavoriteClick: () -> Unit,
    accentColor: Color = SpotifyGreen,
    accentBright: Color = SpotifyGreenBright,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    val effectiveProgress = if (isDragging) dragProgress else progress.coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(310.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        // 1. Curved Progress Arc Canvas with interactive tap and drag scrubbing
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(durationMs) {
                    val arcStart = 205f
                    val arcSweep = 130f
                    val arcRadius = size.width * 0.44f
                    val arcCenterY = 18.dp.toPx() + arcRadius
                    val arcCenterX = size.width / 2f

                    fun updateFraction(touchX: Float, touchY: Float) {
                        val dx = (touchX - arcCenterX).toDouble()
                        val dy = (touchY - arcCenterY).toDouble()
                        var deg = Math.toDegrees(atan2(dy, dx)).toFloat()
                        if (deg < 0) deg += 360f
                        val fraction = ((deg - arcStart) / arcSweep).coerceIn(0f, 1f)
                        dragProgress = fraction
                        onSeekTo((fraction * durationMs).toLong())
                    }

                    detectTapGestures { offset ->
                        updateFraction(offset.x, offset.y)
                    }
                }
                .pointerInput(durationMs) {
                    val arcStart = 205f
                    val arcSweep = 130f
                    val arcRadius = size.width * 0.44f
                    val arcCenterY = 18.dp.toPx() + arcRadius
                    val arcCenterX = size.width / 2f

                    fun updateFraction(touchX: Float, touchY: Float) {
                        val dx = (touchX - arcCenterX).toDouble()
                        val dy = (touchY - arcCenterY).toDouble()
                        var deg = Math.toDegrees(atan2(dy, dx)).toFloat()
                        if (deg < 0) deg += 360f
                        val fraction = ((deg - arcStart) / arcSweep).coerceIn(0f, 1f)
                        dragProgress = fraction
                    }

                    detectDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            updateFraction(offset.x, offset.y)
                        },
                        onDragEnd = {
                            isDragging = false
                            onSeekTo((dragProgress * durationMs).toLong())
                        },
                        onDragCancel = {
                            isDragging = false
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            updateFraction(change.position.x, change.position.y)
                        }
                    )
                }
        ) {
            val arcStart = 205f
            val arcSweep = 130f
            val radius = size.width * 0.44f
            val centerX = size.width / 2f
            val centerY = 18.dp.toPx() + radius

            val arcTopLeft = Offset(centerX - radius, centerY - radius)
            val arcSize = Size(radius * 2f, radius * 2f)

            // Dark Smoky Glass Dome Dish Background
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.16f),
                        Color(0xE014141E),
                        Color(0xF00A0A10),
                        Color.Transparent
                    ),
                    center = Offset(centerX, centerY),
                    radius = radius * 1.05f
                ),
                radius = radius,
                center = Offset(centerX, centerY)
            )

            // Top-edge subtle cut-glass specular sheen arc
            drawArc(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0x00FFFFFF),
                        Color(0x44FFFFFF),
                        Color(0x88FFFFFF),
                        Color(0x44FFFFFF),
                        Color(0x00FFFFFF)
                    )
                ),
                startAngle = arcStart,
                sweepAngle = arcSweep,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = 1.2.dp.toPx())
            )

            // Unplayed Background Arc Track
            drawArc(
                color = Color(0x35FFFFFF),
                startAngle = arcStart,
                sweepAngle = arcSweep,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )

            // Played Glowing Neon Progress Arc
            val playedSweep = arcSweep * effectiveProgress
            if (playedSweep > 0.5f) {
                drawArc(
                    brush = Brush.horizontalGradient(
                        colors = listOf(accentBright, accentColor)
                    ),
                    startAngle = arcStart,
                    sweepAngle = playedSweep,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // Glowing Scrubber Handle Bead at Current Progress
            val currentAngleDeg = arcStart + playedSweep
            val currentAngleRad = Math.toRadians(currentAngleDeg.toDouble())
            val handleX = (centerX + radius * cos(currentAngleRad)).toFloat()
            val handleY = (centerY + radius * sin(currentAngleRad)).toFloat()

            // Outer Neon Glow Aura
            drawCircle(
                color = accentColor.copy(alpha = 0.45f),
                radius = 10.dp.toPx(),
                center = Offset(handleX, handleY)
            )
            // Mid glow
            drawCircle(
                color = accentBright.copy(alpha = 0.85f),
                radius = 5.5.dp.toPx(),
                center = Offset(handleX, handleY)
            )
            // Inner Bright Bead
            drawCircle(
                color = Color.White,
                radius = 3.dp.toPx(),
                center = Offset(handleX, handleY)
            )

            // Small curved accent line gracefully hovering slightly above the pod and play button (no collision with border)
            val playCenterY = 196.dp.toPx()
            val secondaryRadius = 66.dp.toPx()

            // Soft luminous neon glow aura behind the curved accent segment
            drawArc(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.15f),
                        accentBright.copy(alpha = 0.35f),
                        accentColor.copy(alpha = 0.15f)
                    )
                ),
                startAngle = 232f,
                sweepAngle = 76f,
                useCenter = false,
                topLeft = Offset(centerX - secondaryRadius, playCenterY - secondaryRadius),
                size = Size(secondaryRadius * 2f, secondaryRadius * 2f),
                style = Stroke(width = 13.dp.toPx(), cap = StrokeCap.Round)
            )

            // Bold thick glowing arc segment matching the reference photo
            drawArc(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.70f),
                        accentBright,
                        accentColor.copy(alpha = 0.70f)
                    )
                ),
                startAngle = 232f,
                sweepAngle = 76f,
                useCenter = false,
                topLeft = Offset(centerX - secondaryRadius, playCenterY - secondaryRadius),
                size = Size(secondaryRadius * 2f, secondaryRadius * 2f),
                style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        // 2. Elements nestled inside the arc: Heart, Timestamps, and Playback Controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Heart / Favourite Indicator (positioned slightly above, right under arc apex)
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onFavoriteClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = "Favorite",
                    tint = if (isFavorite) accentColor else accentColor.copy(alpha = 0.65f),
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Live Timestamps Frosted Glass Pill (positioned slightly above)
            val displayMs = if (isDragging) (dragProgress * durationMs).toLong() else currentPositionMs
            val currentSec = (displayMs / 1000).coerceAtLeast(0)
            val totalSec = (durationMs / 1000).coerceAtLeast(0)
            val timeString = "%d:%02d / %d:%02d".format(
                currentSec / 60, currentSec % 60,
                totalSec / 60, totalSec % 60
            )

            Box(
                modifier = Modifier
                    .shadow(10.dp, RoundedCornerShape(14.dp), ambientColor = Color.Black)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Glassmorphism.darkGlassBrush)
                    .border(1.dp, Glassmorphism.specularBorderBrush, RoundedCornerShape(14.dp))
                    .padding(horizontal = 11.dp, vertical = 3.dp)
            ) {
                Text(
                    text = timeString,
                    color = Color(0xEEFFFFFF),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Upper Shoulder Row: Shuffle and Repeat flanking the small curved line
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 38.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle Glass Button
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .shadow(8.dp, CircleShape, ambientColor = if (isShuffle) accentColor else Color.Black)
                        .clip(CircleShape)
                        .background(if (isShuffle) Glassmorphism.accentGlassBrush(accentColor) else Glassmorphism.darkGlassBrush)
                        .border(
                            1.dp,
                            if (isShuffle) Glassmorphism.accentGlassBorderBrush(accentColor) else Glassmorphism.specularBorderBrush,
                            CircleShape
                        )
                        .clickable(onClick = onToggleShuffle),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (isShuffle) SpotifyGreenBright else Color(0xAAFFFFFF),
                        modifier = Modifier.size(19.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Repeat Glass Button
                val isRepeatActive = repeatMode != AppRepeatMode.OFF
                val repeatIcon = if (repeatMode == AppRepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat

                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .shadow(8.dp, CircleShape, ambientColor = if (isRepeatActive) accentColor else Color.Black)
                        .clip(CircleShape)
                        .background(if (isRepeatActive) Glassmorphism.accentGlassBrush(accentColor) else Glassmorphism.darkGlassBrush)
                        .border(
                            1.dp,
                            if (isRepeatActive) Glassmorphism.accentGlassBorderBrush(accentColor) else Glassmorphism.specularBorderBrush,
                            CircleShape
                        )
                        .clickable(onClick = onToggleRepeat),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = repeatIcon,
                        contentDescription = "Repeat",
                        tint = if (isRepeatActive) SpotifyGreenBright else Color(0xAAFFFFFF),
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Frosted Glass Control Pod (Previous, Concentric Neon Play/Pause, Next) with Specular Border
            Box(
                modifier = Modifier
                    .shadow(24.dp, RoundedCornerShape(44.dp), ambientColor = Color.Black)
                    .clip(RoundedCornerShape(44.dp))
                    .background(Glassmorphism.glassPodBrush)
                    .border(1.2.dp, Glassmorphism.specularBorderBrush, RoundedCornerShape(44.dp))
                    .padding(horizontal = 24.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
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
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Concentric Glowing Neon Play/Pause Button
                    CurvedNeonPlayButton(
                        isPlaying = isPlaying,
                        isBuffering = isBuffering,
                        onClick = onPlayPauseClick,
                        accentColor = accentColor,
                        size = 76.dp
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
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Concentric Neon Glowing Play/Pause Button:
 * - Outer glowing neon plasma ring matching active accent color
 * - Deep dark intermediate circular groove
 * - Clean solid white circular button with dark pause/play icon in the center
 * - No dulling borders: high contrast, radiant glow
 */
@Composable
fun CurvedNeonPlayButton(
    isPlaying: Boolean,
    isBuffering: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 78.dp,
    accentColor: Color = SpotifyGreen
) {
    val infiniteTransition = rememberInfiniteTransition(label = "curvedPlayPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val outerRingSize = size * (76f / 78f)
    val grooveSize = size * (62f / 78f)
    val innerCircleSize = size * (48f / 78f)
    val iconSize = size * (26f / 78f)

    Box(
        modifier = modifier
            .size(size)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // Outer Glowing Neon Ring using theme accent
        Box(
            modifier = Modifier
                .size(outerRingSize)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.22f))
                .border(
                    width = 2.dp,
                    color = accentColor.copy(alpha = if (isPlaying) pulseAlpha else 0.50f),
                    shape = CircleShape
                )
        )

        // Middle Dark Groove Ring
        Box(
            modifier = Modifier
                .size(grooveSize)
                .clip(CircleShape)
                .background(Color(0xFF141418))
                .border(1.dp, Color(0x33FFFFFF), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (isBuffering) {
                CircularProgressIndicator(
                    modifier = Modifier.size(iconSize),
                    color = accentColor,
                    strokeWidth = 2.5.dp
                )
            } else {
                // Inner Solid White Circle with Dark Icon (high contrast, vivid)
                Box(
                    modifier = Modifier
                        .size(innerCircleSize)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color(0xFF0F0F14),
                        modifier = Modifier.size(iconSize)
                    )
                }
            }
        }
    }
}
