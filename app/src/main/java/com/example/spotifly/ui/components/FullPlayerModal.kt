package com.example.spotifly.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.spotifly.model.PlaybackState
import com.example.spotifly.model.RepeatMode
import com.example.spotifly.theme.DarkPalette
import com.example.spotifly.theme.LocalSpotiflyPalette
import com.example.spotifly.theme.SpotifyBlack
import com.example.spotifly.theme.SpotifyCard
import com.example.spotifly.theme.SpotifyDarkGray
import com.example.spotifly.theme.SpotifyGreen
import com.example.spotifly.theme.SpotifyTextSecondary
import com.example.spotifly.theme.SpotifyWhite

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import kotlinx.coroutines.delay

private val sleepTimerOptions = listOf(5, 10, 15, 30, 45, 60)

@Composable
fun FullPlayerModal(
    playbackState: PlaybackState,
    isFavorite: Boolean,
    modifier: Modifier = Modifier,
    isDownloaded: Boolean = false,
    isDownloading: Boolean = false,
    downloadProgressPercent: Int? = null,
    sleepTimerEndMs: Long? = null,
    sleepAtEndOfTrack: Boolean = false,
    onDismiss: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onFavoriteClick: () -> Unit,
    onDownloadClick: () -> Unit = {},
    onCancelDownloadClick: () -> Unit = {},
    onAddToPlaylistClick: () -> Unit = {},
    onToggleAutoplay: () -> Unit = {},
    onSetPlaybackSpeed: (Float) -> Unit = {},
    onStartSleepTimer: (Int) -> Unit = {},
    onStartSleepTimerAtEndOfTrack: () -> Unit = {},
    onCancelSleepTimer: () -> Unit = {},
    onOpenQueue: () -> Unit,
) {
    val song = playbackState.currentSong ?: return
    var showLyrics by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }

    var isDraggingSlider by remember { mutableStateOf(false) }
    var dragSliderValue by remember { mutableFloatStateOf(0f) }

    val currentSliderValue = if (isDraggingSlider) {
        dragSliderValue
    } else {
        playbackState.progress
    }

    // ---- Ambient artwork color: the player background follows the album art ----
    val context = LocalContext.current
    var ambientColor by remember { mutableStateOf<Color?>(null) }
    LaunchedEffect(song.artworkUrl, song.id) {
        ambientColor = extractAmbientColor(context, song.artworkUrl)
    }
    val animatedAmbient by animateColorAsState(
        targetValue = ambientColor ?: SpotifyCard,
        animationSpec = tween(durationMillis = 600),
        label = "ambientColor"
    )
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            animatedAmbient.copy(alpha = 0.85f),
            animatedAmbient.darken(0.55f),
            SpotifyBlack
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        // The full player always sits on artwork, so it keeps the dark palette
        // even when the rest of the app is in light mode. Without this the
        // "white" labels would turn black on a dark gradient.
        CompositionLocalProvider(LocalSpotiflyPalette provides DarkPalette) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(28.dp))

            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Collapse",
                        tint = SpotifyWhite,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (song.isLocal) "PLAYING FROM DEVICE" else "PLAYING FROM SPOTIFLY",
                        color = SpotifyWhite.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = song.album.ifEmpty { song.title },
                        color = SpotifyWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (showLyrics) {
                    val activeLine = playbackState.lyrics?.let { lyrics ->
                        val index = lyrics.getActiveLineIndex(playbackState.currentPositionMs)
                        val synced = lyrics.lines.getOrNull(index)?.text
                        synced?.takeIf { it.isNotBlank() }
                            ?: lyrics.plainLyrics?.lineSequence()?.firstOrNull { it.isNotBlank() }
                            ?: lyrics.lines.firstOrNull { it.text.isNotBlank() }?.text
                    }
                    if (activeLine != null) {
                        IconButton(onClick = { ShareUtils.shareLyric(context, song, activeLine) }) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share lyric line",
                                tint = SpotifyWhite,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                IconButton(onClick = onOpenQueue) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = "Queue",
                        tint = SpotifyWhite,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (showLyrics) {
                // Lyrics View
                Box(modifier = Modifier.weight(1f)) {
                    LyricsView(
                        lyrics = playbackState.lyrics,
                        isLoading = playbackState.isLyricsLoading,
                        currentPositionMs = playbackState.currentPositionMs,
                        onSeekTo = onSeekTo
                    )
                }
            } else {
                // Big Album Artwork with a glow tinted by the ambient color
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .aspectRatio(1f)
                            .shadow(32.dp, RoundedCornerShape(14.dp), ambientColor = animatedAmbient)
                            .clip(RoundedCornerShape(14.dp))
                            .background(SpotifyCard),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!song.artworkUrl.isNullOrEmpty()) {
                            AsyncImage(
                                model = song.artworkUrl,
                                contentDescription = song.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = SpotifyTextSecondary,
                                modifier = Modifier.size(96.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Song Info & Favorite Heart
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        color = SpotifyWhite,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = song.artist,
                        color = SpotifyWhite.copy(alpha = 0.75f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Add to Playlist
                IconButton(
                    onClick = onAddToPlaylistClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                        contentDescription = "Add to playlist",
                        tint = SpotifyWhite,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Download Button (for online tracks): tap again to cancel
                if (!song.isLocal) {
                    IconButton(
                        onClick = {
                            if (isDownloading) onCancelDownloadClick() else onDownloadClick()
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        when {
                            isDownloading && downloadProgressPercent != null -> {
                                CircularProgressIndicator(
                                    progress = { downloadProgressPercent / 100f },
                                    color = SpotifyGreen,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            isDownloading -> {
                                CircularProgressIndicator(
                                    color = SpotifyGreen,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            else -> {
                                Icon(
                                    imageVector = if (isDownloaded) Icons.Default.DownloadDone else Icons.Default.Download,
                                    contentDescription = if (isDownloaded) "Downloaded" else "Download",
                                    tint = if (isDownloaded) SpotifyGreen else SpotifyWhite,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }

                // Favorite Button
                IconButton(
                    onClick = onFavoriteClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) SpotifyGreen else SpotifyWhite,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Seekbar
            Slider(
                value = currentSliderValue,
                onValueChange = {
                    isDraggingSlider = true
                    dragSliderValue = it
                },
                onValueChangeFinished = {
                    isDraggingSlider = false
                    val targetMs = (dragSliderValue * playbackState.durationMs).toLong()
                    onSeekTo(targetMs)
                },
                colors = SliderDefaults.colors(
                    thumbColor = SpotifyWhite,
                    activeTrackColor = SpotifyGreen,
                    inactiveTrackColor = SpotifyWhite.copy(alpha = 0.25f)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Timestamps & Playback Speed
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val currentSec = ((if (isDraggingSlider) (dragSliderValue * playbackState.durationMs).toLong() else playbackState.currentPositionMs) / 1000).coerceAtLeast(0)
                val totalSec = (playbackState.durationMs / 1000).coerceAtLeast(0)

                Text(
                    text = "%d:%02d".format(currentSec / 60, currentSec % 60),
                    color = SpotifyWhite.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )

                // Playback Speed Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (playbackState.playbackSpeed != 1.0f) SpotifyGreen.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f),
                    border = BorderStroke(
                        1.dp,
                        if (playbackState.playbackSpeed != 1.0f) SpotifyGreen else Color.White.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.clickable { showSpeedDialog = true }
                ) {
                    Text(
                        text = if (playbackState.playbackSpeed == 1.0f) "1.0x" else "${playbackState.playbackSpeed}x",
                        color = if (playbackState.playbackSpeed != 1.0f) SpotifyGreen else SpotifyWhite.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }

                Text(
                    text = "%d:%02d".format(totalSec / 60, totalSec % 60),
                    color = SpotifyWhite.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Spoken word needs longer transport jumps than music: 15s back to re-hear
            // a sentence, 30s forward to clear an ad read.
            if (song.isPodcast) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PodcastSkipChip(
                        label = "15",
                        contentDescription = "Back 15 seconds",
                        icon = Icons.Default.Replay
                    ) {
                        onSeekTo((playbackState.currentPositionMs - 15_000L).coerceAtLeast(0L))
                    }
                    Spacer(modifier = Modifier.width(20.dp))
                    PodcastSkipChip(
                        label = "30",
                        contentDescription = "Forward 30 seconds",
                        icon = Icons.Default.Forward30
                    ) {
                        onSeekTo(playbackState.currentPositionMs + 30_000L)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Playback Controls Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle
                IconButton(onClick = onToggleShuffle) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (playbackState.isShuffle) SpotifyGreen else SpotifyWhite.copy(alpha = 0.7f),
                        modifier = Modifier.size(26.dp)
                    )
                }

                // Previous
                IconButton(onClick = onSkipPrevious) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = SpotifyWhite,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Big Play / Pause Button
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(SpotifyWhite),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = onPlayPauseClick,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (playbackState.isBuffering) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                color = SpotifyBlack,
                                strokeWidth = 3.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (playbackState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                                tint = SpotifyBlack,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }

                // Next
                IconButton(onClick = onSkipNext) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = SpotifyWhite,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Repeat
                IconButton(onClick = onToggleRepeat) {
                    val repeatIcon = if (playbackState.repeatMode == RepeatMode.ONE) {
                        Icons.Default.RepeatOne
                    } else {
                        Icons.Default.Repeat
                    }
                    val tint = if (playbackState.repeatMode != RepeatMode.OFF) SpotifyGreen else SpotifyWhite.copy(alpha = 0.7f)
                    Icon(
                        imageVector = repeatIcon,
                        contentDescription = "Repeat",
                        tint = tint,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bottom Actions: source badge, sleep timer, autoplay, lyrics
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Source badge
                Text(
                    text = when {
                        song.isPodcast -> "PODCAST"
                        song.isLocal -> "DEVICE AUDIO"
                        else -> "HIGH QUALITY STREAM"
                    },
                    color = SpotifyGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Sleep Timer Button (shows live countdown while active)
                    SleepTimerBadge(
                        sleepTimerEndMs = sleepTimerEndMs,
                        sleepAtEndOfTrack = sleepAtEndOfTrack,
                        onClick = { showSleepTimerDialog = true }
                    )

                    // Autoplay Toggle Button
                    IconButton(onClick = onToggleAutoplay) {
                        Icon(
                            imageVector = Icons.Default.AllInclusive,
                            contentDescription = "Autoplay Similar Songs",
                            tint = if (playbackState.isAutoplay) SpotifyGreen else SpotifyWhite.copy(alpha = 0.7f),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Toggle Lyrics Button
                    IconButton(onClick = { showLyrics = !showLyrics }) {
                        Icon(
                            imageVector = Icons.Default.Lyrics,
                            contentDescription = "Lyrics",
                            tint = if (showLyrics) SpotifyGreen else SpotifyWhite,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }

    if (showSpeedDialog) {
        val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            containerColor = SpotifyCard,
            title = {
                Text(
                    text = "Playback Speed",
                    color = SpotifyWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    speeds.forEach { speed ->
                        val isSelected = playbackState.playbackSpeed == speed
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) SpotifyGreen.copy(alpha = 0.15f) else Color.Transparent)
                                .clickable {
                                    onSetPlaybackSpeed(speed)
                                    showSpeedDialog = false
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (speed == 1.0f) "1.0x (Normal)" else "${speed}x",
                                color = if (isSelected) SpotifyGreen else SpotifyWhite,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 15.sp
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = SpotifyGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSpeedDialog = false }) {
                    Text("Close", color = SpotifyGreen)
                }
            }
        )
        }
    }

    if (showSleepTimerDialog) {
        SleepTimerDialog(
            sleepTimerEndMs = sleepTimerEndMs,
            sleepAtEndOfTrack = sleepAtEndOfTrack,
            onSelect = { minutes ->
                onStartSleepTimer(minutes)
                showSleepTimerDialog = false
            },
            onSelectEndOfTrack = {
                onStartSleepTimerAtEndOfTrack()
                showSleepTimerDialog = false
            },
            onCancel = {
                onCancelSleepTimer()
                showSleepTimerDialog = false
            },
            onDismiss = { showSleepTimerDialog = false }
        )
    }
}

/** Moon icon that morphs into a live remaining-time countdown while a timer runs. */
@Composable
private fun SleepTimerBadge(
    sleepTimerEndMs: Long?,
    sleepAtEndOfTrack: Boolean,
    onClick: () -> Unit
) {
    var remainingMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(sleepTimerEndMs) {
        while (true) {
            remainingMs = ((sleepTimerEndMs ?: 0L) - System.currentTimeMillis()).coerceAtLeast(0L)
            if (sleepTimerEndMs == null) break
            delay(1_000)
        }
    }

    val active = (sleepTimerEndMs != null && remainingMs > 0) || sleepAtEndOfTrack
    Box(
        modifier = Modifier.clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (active) {
            val totalSeconds = remainingMs / 1000
            val label = if (sleepAtEndOfTrack) {
                "End of track"
            } else if (totalSeconds >= 60) {
                "${totalSeconds / 60}:${"%02d".format(totalSeconds % 60)}"
            } else {
                "${totalSeconds}s"
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(SpotifyGreen.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Bedtime,
                    contentDescription = "Sleep timer",
                    tint = SpotifyGreen,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = label,
                    color = SpotifyGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            IconButton(onClick = onClick) {
                Icon(
                    imageVector = Icons.Default.Bedtime,
                    contentDescription = "Sleep timer",
                    tint = SpotifyWhite.copy(alpha = 0.7f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
fun SleepTimerDialog(
    sleepTimerEndMs: Long?,
    sleepAtEndOfTrack: Boolean = false,
    onSelect: (Int) -> Unit,
    onSelectEndOfTrack: () -> Unit = {},
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SpotifyCard,
        icon = {
            Icon(
                imageVector = Icons.Default.Bedtime,
                contentDescription = null,
                tint = SpotifyGreen,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = "Sleep Timer",
                color = SpotifyWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Music will pause automatically when the timer ends.",
                    color = SpotifyTextSecondary,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(14.dp))
                for (row in sleepTimerOptions.chunked(3)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        for (minutes in row) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = SpotifyDarkGray,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onSelect(minutes) }
                            ) {
                                Text(
                                    text = "$minutes min",
                                    color = SpotifyWhite,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .padding(vertical = 12.dp)
                                        .wrapContentWidth(Alignment.CenterHorizontally)
                                )
                            }
                        }
                        if (row.size < 3) {
                            repeat(3 - row.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (sleepAtEndOfTrack) SpotifyGreen.copy(alpha = 0.2f) else SpotifyDarkGray,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectEndOfTrack() }
                ) {
                    Text(
                        text = "End of track",
                        color = if (sleepAtEndOfTrack) SpotifyGreen else SpotifyWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .padding(vertical = 12.dp)
                            .wrapContentWidth(Alignment.CenterHorizontally)
                    )
                }
            }
        },
        confirmButton = {
            if (sleepTimerEndMs != null || sleepAtEndOfTrack) {
                TextButton(onClick = onCancel) {
                    Text("Cancel timer", color = SpotifyGreen)
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("Close", color = SpotifyGreen)
                }
            }
        }
    )
}

/** A pill button for spoken-word skip amounts, Spotify-style. */
@Composable
private fun PodcastSkipChip(
    label: String,
    contentDescription: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = SpotifyWhite.copy(alpha = 0.12f),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = SpotifyWhite,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "${label}s",
                color = SpotifyWhite,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/** Darkens a color while keeping it fully opaque, for gradient middles. */
private fun Color.darken(fraction: Float): Color = Color(
    red = red * (1f - fraction),
    green = green * (1f - fraction),
    blue = blue * (1f - fraction),
    alpha = 1f
)
