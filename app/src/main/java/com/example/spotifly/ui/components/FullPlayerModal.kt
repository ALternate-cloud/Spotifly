package com.example.spotifly.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.spotifly.model.PlaybackState
import com.example.spotifly.model.RepeatMode
import com.example.spotifly.theme.DarkPalette
import com.example.spotifly.theme.Glassmorphism
import com.example.spotifly.theme.LocalSpotiflyPalette
import com.example.spotifly.theme.SpotifyCard
import com.example.spotifly.theme.SpotifyDarkGray
import com.example.spotifly.theme.SpotifyGreen
import com.example.spotifly.theme.SpotifyGreenBright
import com.example.spotifly.theme.SpotifyBlack
import com.example.spotifly.theme.SpotifyTextSecondary
import com.example.spotifly.theme.SpotifyWhite

enum class NowPlayingMode {
    CURVED,
    IMMERSIVE
}

private val sleepTimerOptions = listOf(5, 10, 15, 30, 45, 60)

/**
 * Priority 1: Curved Music Player from Reference Screenshot
 *
 * Distinctive Now Playing interface:
 * - Large curved progress arc above playback controls
 * - Heart / favourite indicator integrated into the arc
 * - Glowing neon circular Play/Pause button in the centre with white center and dark pause bars
 * - Previous and Next buttons on either side
 * - Shuffle and Repeat controls
 * - Dark black/burgundy background with red/pink neon lighting
 * - Track title and artist above the curved progress section
 * - Interactive and smooth progress synchronized with playback
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerModal(
    playbackState: PlaybackState,
    isFavorite: Boolean,
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
    modifier: Modifier = Modifier
) {
    val song = playbackState.currentSong ?: return
    val context = LocalContext.current

    var showLyrics by remember { mutableStateOf(false) }
    var showMoreSheet by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showShareModal by remember { mutableStateOf(false) }
    var playerMode by remember { mutableStateOf(NowPlayingMode.CURVED) }

    // Ambient artwork color extraction
    var ambientColor by remember { mutableStateOf<Color?>(null) }
    LaunchedEffect(song.artworkUrl, song.id) {
        ambientColor = extractAmbientColor(context, song.artworkUrl)
    }
    val animatedAmbient by animateColorAsState(
        targetValue = ambientColor ?: Color(0xFF2E0711),
        animationSpec = tween(durationMillis = 600),
        label = "ambientColor"
    )

    val accent = SpotifyGreen

    // 100% Opaque Root Container with Dark Black / Burgundy Palette
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF08080B))
    ) {
        // Atmospheric blurred album backdrop
        if (!song.artworkUrl.isNullOrEmpty()) {
            AsyncImage(
                model = song.artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(55.dp)
                    .alpha(0.26f)
            )
        }

        // Atmospheric Theme Accent Lighting Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            accent.copy(alpha = 0.40f),
                            Color(0x181014),
                            Color.Transparent
                        ),
                        center = Offset(220f, 320f),
                        radius = 850f
                    )
                )
        )

        // Dark Vignette Gradients
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xD908080B),
                            Color(0x7008080B),
                            Color(0xFA08080B)
                        )
                    )
                )
        )

        CompositionLocalProvider(LocalSpotiflyPalette provides DarkPalette) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(modifier = Modifier.height(28.dp))

                // Top Header Bar: [ ✕ ] -------- [ Curved | Immersive ] -------- [ ⋮ ]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Circular frosted glass dismiss button (matching reference photo)
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .shadow(8.dp, CircleShape, ambientColor = Color.Black)
                            .clip(CircleShape)
                            .background(Glassmorphism.darkGlassBrush)
                            .border(1.dp, Glassmorphism.specularBorderBrush, CircleShape)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Collapse",
                            tint = SpotifyWhite,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Player Mode Switcher: Frosted Glass Capsule [ Curved | Immersive ]
                    Box(
                        modifier = Modifier
                            .shadow(12.dp, RoundedCornerShape(20.dp), ambientColor = Color.Black)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Glassmorphism.darkGlassBrush)
                            .border(1.dp, Glassmorphism.specularBorderBrush, RoundedCornerShape(20.dp))
                            .padding(3.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val isCurvedActive = playerMode == NowPlayingMode.CURVED
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isCurvedActive) Glassmorphism.accentGlassBrush(accent) else Glassmorphism.transparentBrush)
                                    .border(
                                        1.dp,
                                        if (isCurvedActive) Glassmorphism.accentGlassBorderBrush(accent) else Glassmorphism.transparentBrush,
                                        RoundedCornerShape(16.dp)
                                    )
                                    .clickable { playerMode = NowPlayingMode.CURVED }
                                    .padding(horizontal = 12.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = "Curved",
                                    color = if (isCurvedActive) SpotifyGreenBright else Color(0xBBFFFFFF),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            val isImmersiveActive = playerMode == NowPlayingMode.IMMERSIVE
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isImmersiveActive) Glassmorphism.accentGlassBrush(accent) else Glassmorphism.transparentBrush)
                                    .border(
                                        1.dp,
                                        if (isImmersiveActive) Glassmorphism.accentGlassBorderBrush(accent) else Glassmorphism.transparentBrush,
                                        RoundedCornerShape(16.dp)
                                    )
                                    .clickable { playerMode = NowPlayingMode.IMMERSIVE }
                                    .padding(horizontal = 12.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = "Immersive",
                                    color = if (isImmersiveActive) SpotifyGreenBright else Color(0xBBFFFFFF),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Circular frosted glass more options button (matching reference photo)
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .shadow(8.dp, CircleShape, ambientColor = Color.Black)
                            .clip(CircleShape)
                            .background(Glassmorphism.darkGlassBrush)
                            .border(1.dp, Glassmorphism.specularBorderBrush, CircleShape)
                            .clickable { showMoreSheet = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More options",
                            tint = SpotifyWhite,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (playerMode == NowPlayingMode.IMMERSIVE) {
                    ImmersiveNowPlayingView(
                        song = song,
                        progress = playbackState.progress,
                        durationMs = playbackState.durationMs,
                        currentPositionMs = playbackState.currentPositionMs,
                        isPlaying = playbackState.isPlaying,
                        isBuffering = playbackState.isBuffering,
                        isFavorite = isFavorite,
                        isShuffle = playbackState.isShuffle,
                        repeatMode = playbackState.repeatMode,
                        lyrics = playbackState.lyrics,
                        isLyricsLoading = playbackState.isLyricsLoading,
                        showLyrics = showLyrics,
                        onToggleLyrics = { showLyrics = !showLyrics },
                        onSeekTo = onSeekTo,
                        onPlayPauseClick = onPlayPauseClick,
                        onSkipNext = onSkipNext,
                        onSkipPrevious = onSkipPrevious,
                        onToggleShuffle = onToggleShuffle,
                        onToggleRepeat = onToggleRepeat,
                        onFavoriteClick = onFavoriteClick,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                } else {
                    // Upper Section: Artwork / Dynamic Lyrics Slide Stage + Dual Badges + Track Information
                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        val metadataReservedHeight = 112.dp
                        val coverStageHeight = 180.dp
                            .coerceAtMost((maxHeight - metadataReservedHeight).coerceAtLeast(136.dp))
                        val lyricsStageHeight = (maxHeight - metadataReservedHeight)
                            .coerceIn(coverStageHeight, 290.dp)
                        val animatedStageHeight by animateDpAsState(
                            targetValue = if (showLyrics) lyricsStageHeight else coverStageHeight,
                            animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
                            label = "curvedLyricsStageHeight"
                        )

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            ArtworkLyricsSlideStage(
                                showLyrics = showLyrics,
                                lyrics = playbackState.lyrics,
                                isLyricsLoading = playbackState.isLyricsLoading,
                                currentPositionMs = playbackState.currentPositionMs,
                                onSeekTo = onSeekTo,
                                onCloseLyrics = { showLyrics = false },
                                accentColor = accent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(animatedStageHeight)
                            ) {
                                // Tappable Album Artwork Card with frosted glass cut-bevel rim and specular sheen
                                Box(
                                    modifier = Modifier
                                        .size(175.dp)
                                        .shadow(28.dp, RoundedCornerShape(24.dp), ambientColor = accent, spotColor = accent)
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(Color(0xFF14141E))
                                        .border(1.5.dp, Glassmorphism.specularBorderBrush, RoundedCornerShape(24.dp))
                                        .border(6.dp, Color(0x18FFFFFF), RoundedCornerShape(24.dp))
                                        .clickable { showLyrics = true },
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
                                            tint = SpotifyTextSecondary,
                                            modifier = Modifier.size(48.dp)
                                        )
                                    }

                                    // Glass diagonal sheen overlay
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.linearGradient(
                                                    colors = listOf(
                                                        Color(0x35FFFFFF),
                                                        Color(0x08FFFFFF),
                                                        Color.Transparent
                                                    ),
                                                    start = Offset(0f, 0f),
                                                    end = Offset(240f, 240f)
                                                )
                                            )
                                    )

                                    // Subtle frosted lyrics badge indicating tap-to-slide affordance
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(10.dp)
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(Glassmorphism.darkGlassBrush)
                                            .border(1.dp, Glassmorphism.specularBorderBrush, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Lyrics,
                                            contentDescription = null,
                                            tint = SpotifyGreenBright,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Dual Frosted Glass Badges (matching reference photo above title)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Lossless Audio Glass Pill
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Glassmorphism.accentGlassBrush(accent, 0.22f))
                                        .border(1.dp, Glassmorphism.accentGlassBorderBrush(accent), RoundedCornerShape(12.dp))
                                        .padding(horizontal = 9.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "LOSSLESS AUDIO",
                                        color = SpotifyGreenBright,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.2.sp
                                    )
                                }

                                // Quality Bitrate Glass Pill
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0x28FFFFFF))
                                        .border(1.dp, Glassmorphism.specularBorderBrush, RoundedCornerShape(12.dp))
                                        .padding(horizontal = 9.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "320 KBPS",
                                        color = Color(0xEEFFFFFF),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Track Title (bold, centered, matching "New love Song")
                            Text(
                                text = song.title,
                                color = SpotifyWhite,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            // Artist Name (centered, matching "Albert Mansour")
                            Text(
                                text = song.artist,
                                color = Color(0xFFD6D6E2),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                    }

                    // Lower Section: Distinctive Glassmorphic Curved Music Player Console
                    CurvedPlayerConsole(
                        progress = playbackState.progress,
                        durationMs = playbackState.durationMs,
                        currentPositionMs = playbackState.currentPositionMs,
                        isPlaying = playbackState.isPlaying,
                        isBuffering = playbackState.isBuffering,
                        isFavorite = isFavorite,
                        isShuffle = playbackState.isShuffle,
                        repeatMode = playbackState.repeatMode,
                        onSeekTo = onSeekTo,
                        onPlayPauseClick = onPlayPauseClick,
                        onSkipNext = onSkipNext,
                        onSkipPrevious = onSkipPrevious,
                        onToggleShuffle = onToggleShuffle,
                        onToggleRepeat = onToggleRepeat,
                        onFavoriteClick = onFavoriteClick,
                        accentColor = accent,
                        accentBright = SpotifyGreenBright
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Overflow More Options Bottom Sheet (Preserves all Spotifly capabilities)
    if (showMoreSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showMoreSheet = false },
            sheetState = sheetState,
            containerColor = Color(0xFF14050A),
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0x40FFFFFF)) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            ) {
                // Header track summary in sheet
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            color = SpotifyWhite,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = song.artist,
                            color = SpotifyTextSecondary,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Action: Lyrics
                MoreOptionItem(
                    icon = Icons.Default.Lyrics,
                    title = if (showLyrics) "Hide Lyrics" else "Show Lyrics",
                    tint = if (showLyrics) accent else SpotifyWhite,
                    onClick = {
                        showLyrics = !showLyrics
                        showMoreSheet = false
                    }
                )

                // Action: Queue
                MoreOptionItem(
                    icon = Icons.AutoMirrored.Filled.QueueMusic,
                    title = "Open Queue",
                    onClick = {
                        showMoreSheet = false
                        onOpenQueue()
                    }
                )

                // Action: Playback Speed
                MoreOptionItem(
                    icon = Icons.Default.Speed,
                    title = "Playback Speed (${if (playbackState.playbackSpeed == 1.0f) "1.0x" else "${playbackState.playbackSpeed}x"})",
                    onClick = {
                        showMoreSheet = false
                        showSpeedDialog = true
                    }
                )

                // Action: Sleep Timer
                MoreOptionItem(
                    icon = Icons.Default.Bedtime,
                    title = if (sleepTimerEndMs != null || sleepAtEndOfTrack) "Sleep Timer Active" else "Sleep Timer",
                    tint = if (sleepTimerEndMs != null || sleepAtEndOfTrack) accent else SpotifyWhite,
                    onClick = {
                        showMoreSheet = false
                        showSleepTimerDialog = true
                    }
                )

                // Action: Add to Playlist
                MoreOptionItem(
                    icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                    title = "Add to Playlist",
                    onClick = {
                        showMoreSheet = false
                        onAddToPlaylistClick()
                    }
                )

                // Action: Download
                if (!song.isLocal) {
                    MoreOptionItem(
                        icon = if (isDownloaded) Icons.Default.DownloadDone else Icons.Default.Download,
                        title = when {
                            isDownloading -> "Downloading… ${downloadProgressPercent ?: 0}%"
                            isDownloaded -> "Downloaded"
                            else -> "Download Track"
                        },
                        tint = if (isDownloaded) accent else SpotifyWhite,
                        onClick = {
                            if (isDownloading) onCancelDownloadClick() else onDownloadClick()
                            showMoreSheet = false
                        }
                    )
                }

                // Action: Autoplay
                MoreOptionItem(
                    icon = Icons.Default.AllInclusive,
                    title = "Autoplay Similar Songs",
                    tint = if (playbackState.isAutoplay) accent else SpotifyWhite,
                    onClick = {
                        onToggleAutoplay()
                    }
                )

                // Action: Share Story & Card
                MoreOptionItem(
                    icon = Icons.Default.Share,
                    title = "Share Music Story / Card",
                    onClick = {
                        showMoreSheet = false
                        showShareModal = true
                    }
                )

                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }

    // Shareable Story & Card Generator Modal
    if (showShareModal) {
        ShareCardModal(
            song = song,
            lyrics = playbackState.lyrics,
            onDismiss = { showShareModal = false }
        )
    }

    // Playback Speed Dialog
    if (showSpeedDialog) {
        val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            containerColor = Color(0xFF16060B),
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
                                .background(if (isSelected) accent.copy(alpha = 0.15f) else Color.Transparent)
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
                                color = if (isSelected) accent else SpotifyWhite,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 15.sp
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = accent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSpeedDialog = false }) {
                    Text("Close", color = accent)
                }
            }
        )
    }

    // Sleep Timer Dialog
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

@Composable
private fun MoreOptionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    tint: Color = SpotifyWhite,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            color = tint,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
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
    val accent = SpotifyGreen
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF16060B),
        icon = {
            Icon(
                imageVector = Icons.Default.Bedtime,
                contentDescription = null,
                tint = accent,
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
                val remainingMs = sleepTimerEndMs?.let { (it - System.currentTimeMillis()).coerceAtLeast(0L) }
                val infoText = when {
                    remainingMs != null && remainingMs <= 30_000L ->
                        "Fading volume gently… pausing in ${(remainingMs / 1000L).coerceAtLeast(1L)}s."
                    remainingMs != null ->
                        "Pausing in ${(remainingMs / 60_000L).coerceAtLeast(1L)} min with a gentle 30s fade-out."
                    else ->
                        "Music tapers volume gently over the final 30 seconds before pausing."
                }
                Text(
                    text = infoText,
                    color = if (remainingMs != null && remainingMs <= 30_000L) accent else SpotifyTextSecondary,
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
                    color = if (sleepAtEndOfTrack) accent.copy(alpha = 0.2f) else SpotifyDarkGray,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectEndOfTrack() }
                ) {
                    Text(
                        text = "End of track",
                        color = if (sleepAtEndOfTrack) accent else SpotifyWhite,
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
                    Text("Cancel timer", color = accent)
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("Close", color = accent)
                }
            }
        }
    )
}
