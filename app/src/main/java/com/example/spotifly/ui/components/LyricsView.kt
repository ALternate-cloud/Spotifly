package com.example.spotifly.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spotifly.model.Lyrics
import com.example.spotifly.theme.Glassmorphism
import com.example.spotifly.theme.SpotifyGreen
import com.example.spotifly.theme.SpotifyGreenBright
import com.example.spotifly.theme.SpotifyTextSecondary
import com.example.spotifly.theme.SpotifyWhite
import kotlin.math.abs

/**
 * Reusable stage container that smoothly slides album artwork out horizontally
 * and brings dynamic lyrics in its place (and reverses smoothly back to cover).
 */
@Composable
fun ArtworkLyricsSlideStage(
    showLyrics: Boolean,
    lyrics: Lyrics?,
    isLyricsLoading: Boolean,
    currentPositionMs: Long,
    onSeekTo: (Long) -> Unit,
    onCloseLyrics: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = SpotifyGreen,
    artworkContent: @Composable () -> Unit
) {
    Box(
        modifier = modifier.clipToBounds(),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = showLyrics,
            transitionSpec = {
                val slideSpec = tween<androidx.compose.ui.unit.IntOffset>(
                    durationMillis = 380,
                    easing = FastOutSlowInEasing
                )
                if (targetState) {
                    // Cover slides out left; Lyrics slides in from right
                    (slideInHorizontally(animationSpec = slideSpec) { it } +
                        fadeIn(tween(280, delayMillis = 50))) togetherWith
                        (slideOutHorizontally(animationSpec = slideSpec) { -it } +
                            fadeOut(tween(220)))
                } else {
                    // Lyrics slides out right; Cover slides in from left
                    (slideInHorizontally(animationSpec = slideSpec) { -it } +
                        fadeIn(tween(280, delayMillis = 50))) togetherWith
                        (slideOutHorizontally(animationSpec = slideSpec) { it } +
                            fadeOut(tween(220)))
                }.using(SizeTransform(clip = true))
            },
            label = "artworkToLyricsSlide",
            modifier = Modifier.fillMaxSize()
        ) { showingLyrics ->
            if (showingLyrics) {
                LyricsView(
                    lyrics = lyrics,
                    isLoading = isLyricsLoading,
                    currentPositionMs = currentPositionMs,
                    onSeekTo = onSeekTo,
                    onClose = onCloseLyrics,
                    accentColor = accentColor,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    artworkContent()
                }
            }
        }
    }
}

@Composable
fun LyricsView(
    lyrics: Lyrics?,
    isLoading: Boolean,
    currentPositionMs: Long,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
    accentColor: Color = SpotifyGreen
) {
    val listState = rememberLazyListState()
    val cardShape = RoundedCornerShape(24.dp)

    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(cardShape)
            .background(Glassmorphism.deepObsidianGlassBrush)
            .background(Glassmorphism.accentGlassBrush(accentColor, 0.08f))
            .border(1.dp, Glassmorphism.specularBorderBrush, cardShape)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Compact frosted header inside the inline lyrics stage
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val isSynced = lyrics?.isSynced == true && lyrics.lines.isNotEmpty()
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (isSynced) SpotifyGreenBright else SpotifyTextSecondary)
                )
                Text(
                    text = if (isSynced) "DYNAMIC LYRICS" else "LYRICS",
                    color = if (isSynced) SpotifyGreenBright else SpotifyTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
            }

            if (onClose != null) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Glassmorphism.frostedPillBrush)
                        .border(1.dp, Glassmorphism.specularBorderBrush, RoundedCornerShape(14.dp))
                        .clickable(onClick = onClose)
                        .semantics { contentDescription = "Back to cover" }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = SpotifyWhite,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "Cover",
                        color = SpotifyWhite,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = if (onClose != null) {
                            Modifier
                                .fillMaxSize()
                                .clickable(onClick = onClose)
                        } else {
                            Modifier
                        },
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = accentColor,
                            modifier = Modifier.size(30.dp),
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Finding dynamic lyrics...",
                            color = SpotifyTextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }

                // Also covers a lyrics object that arrived with neither timed nor plain text,
                // which would otherwise render a blank panel.
                lyrics == null || (lyrics.lines.isEmpty() && lyrics.plainLyrics.isNullOrEmpty()) -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = if (onClose != null) {
                            Modifier
                                .fillMaxSize()
                                .clickable(onClick = onClose)
                        } else {
                            Modifier
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lyrics,
                            contentDescription = null,
                            tint = SpotifyTextSecondary,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No lyrics found for this song",
                            color = SpotifyWhite,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (onClose != null) "Tap to return to album cover" else "Try searching for another track",
                            color = SpotifyTextSecondary,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                lyrics.isSynced && lyrics.lines.isNotEmpty() -> {
                    val activeIndex = lyrics.getActiveLineIndex(currentPositionMs)

                    LaunchedEffect(activeIndex) {
                        if (activeIndex >= 0) {
                            listState.animateScrollToItem(
                                (activeIndex - 1).coerceAtLeast(0)
                            )
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(top = 6.dp, bottom = 36.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(lyrics.lines) { index, line ->
                                val isActive = index == activeIndex
                                val distance = if (activeIndex >= 0) abs(index - activeIndex) else Int.MAX_VALUE
                                val targetColor = when {
                                    isActive -> SpotifyWhite
                                    index < activeIndex -> Color(0x55FFFFFF)
                                    distance == 1 -> Color(0xAAFFFFFF)
                                    else -> Color(0x52FFFFFF)
                                }
                                val animatedColor by animateColorAsState(
                                    targetValue = targetColor,
                                    animationSpec = tween(durationMillis = 280),
                                    label = "lyricLineColor"
                                )
                                val animatedScale by animateFloatAsState(
                                    targetValue = if (isActive) 1.0f else 0.91f,
                                    animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                                    label = "lyricLineScale"
                                )
                                val highlightAlpha by animateFloatAsState(
                                    targetValue = if (isActive) 1f else 0f,
                                    animationSpec = tween(durationMillis = 260),
                                    label = "lyricHighlightAlpha"
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            accentColor.copy(alpha = 0.14f * highlightAlpha)
                                        )
                                        .clickable { onSeekTo(line.timeMs) }
                                        .padding(horizontal = 10.dp, vertical = 7.dp)
                                ) {
                                    Text(
                                        text = line.text.ifBlank { "♪" },
                                        color = animatedColor,
                                        fontSize = 18.sp,
                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                        lineHeight = 24.sp,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .graphicsLayer {
                                                scaleX = animatedScale
                                                scaleY = animatedScale
                                                transformOrigin = TransformOrigin(0f, 0.5f)
                                            }
                                    )
                                }
                            }
                        }

                        // Soft bottom fade mask so scrolling lines dissolve cleanly
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(26.dp)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color(0xD80A0A12))
                                    )
                                )
                        )
                    }
                }

                !lyrics.plainLyrics.isNullOrEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            item {
                                Text(
                                    text = lyrics.plainLyrics,
                                    color = SpotifyWhite.copy(alpha = 0.92f),
                                    fontSize = 15.sp,
                                    lineHeight = 23.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(24.dp)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color(0xD80A0A12))
                                    )
                                )
                        )
                    }
                }
            }
        }
    }
}
