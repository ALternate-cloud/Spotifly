package com.example.spotifly.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Pure-Compose animated equalizer bars. A permission-free visual cue that audio
 * is actively playing (android.media.audiofx.Visualizer would need RECORD_AUDIO).
 */
@Composable
fun EqualizerBars(
    color: Color,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barWidth: Dp = 3.dp,
    maxHeight: Dp = 16.dp
) {
    val transition = rememberInfiniteTransition(label = "eqBars")
    val phases = listOf(0, 90, 40, 130)
    val heights = phases.mapIndexed { index, phase ->
        transition.animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 420 + index * 110, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "eqBar$index"
        )
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom
    ) {
        heights.forEachIndexed { index, animatable ->
            val fraction by animatable
            val scale = if (isPlaying) fraction else 0.25f
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .width(barWidth)
                    .height(maxHeight * scale)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
            if (index != heights.lastIndex) {
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(2.dp))
            }
        }
    }
}
