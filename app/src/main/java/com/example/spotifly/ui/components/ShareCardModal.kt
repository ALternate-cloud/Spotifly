package com.example.spotifly.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.spotifly.model.Lyrics
import com.example.spotifly.model.Song
import com.example.spotifly.theme.Glassmorphism
import com.example.spotifly.theme.SpotifyGreen
import com.example.spotifly.theme.SpotifyGreenBright
import com.example.spotifly.theme.SpotifyTextSecondary
import com.example.spotifly.theme.SpotifyWhite
import kotlinx.coroutines.launch

/**
 * Visual Shareable Story & Card Generator Modal:
 * - Preview & export high-res visual cards for Instagram Stories / WhatsApp Status
 * - Select between 9:16 vertical Story and 1:1 Square Card
 * - Optional quote card for synchronized lyrics
 * - Direct 1-tap "Share to Stories / Apps" via FileProvider (image/png)
 * - Save directly to local gallery (Pictures/Spotifly)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareCardModal(
    song: Song,
    lyrics: Lyrics? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var format by remember { mutableStateOf(ShareCardFormat.STORY_9_16) }
    var includeLyrics by remember { mutableStateOf(!lyrics?.lines.isNullOrEmpty()) }
    var isSharing by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    val activeAccent = SpotifyGreen
    val accentInt = activeAccent.toArgb()

    val topLyricLine = remember(lyrics) {
        lyrics?.lines?.firstOrNull { it.text.isNotBlank() }?.text
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xF50D0D16),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Glassmorphism.accentGlassBrush(activeAccent, 0.20f))
                            .border(1.dp, Glassmorphism.accentGlassBorderBrush(activeAccent), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            tint = SpotifyGreenBright,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Share Music Story",
                            color = SpotifyWhite,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Studio visual card for social stories",
                            color = SpotifyTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = SpotifyWhite
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Format Switcher Pills: [ 9:16 Story ] | [ 1:1 Square ]
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Glassmorphism.darkGlassBrush)
                    .border(1.dp, Glassmorphism.specularBorderBrush, RoundedCornerShape(20.dp))
                    .padding(4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val isStory = format == ShareCardFormat.STORY_9_16
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isStory) Glassmorphism.accentGlassBrush(activeAccent) else Glassmorphism.transparentBrush)
                            .border(
                                1.dp,
                                if (isStory) Glassmorphism.accentGlassBorderBrush(activeAccent) else Glassmorphism.transparentBrush,
                                RoundedCornerShape(16.dp)
                            )
                            .clickable { format = ShareCardFormat.STORY_9_16 }
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "9:16 Story",
                            color = if (isStory) SpotifyGreenBright else Color(0xBBFFFFFF),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    val isSquare = format == ShareCardFormat.SQUARE_1_1
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSquare) Glassmorphism.accentGlassBrush(activeAccent) else Glassmorphism.transparentBrush)
                            .border(
                                1.dp,
                                if (isSquare) Glassmorphism.accentGlassBorderBrush(activeAccent) else Glassmorphism.transparentBrush,
                                RoundedCornerShape(16.dp)
                            )
                            .clickable { format = ShareCardFormat.SQUARE_1_1 }
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "1:1 Square",
                            color = if (isSquare) SpotifyGreenBright else Color(0xBBFFFFFF),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Live Interactive Card Preview Container
            val previewAspectRatio = if (format == ShareCardFormat.STORY_9_16) 9f / 16f else 1f
            val previewHeight = if (format == ShareCardFormat.STORY_9_16) 360.dp else 260.dp

            Box(
                modifier = Modifier
                    .height(previewHeight)
                    .aspectRatio(previewAspectRatio)
                    .shadow(28.dp, RoundedCornerShape(20.dp), ambientColor = activeAccent, spotColor = activeAccent)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF14141E), Color(0xFF09090F), Color(0xFF040407))
                        )
                    )
                    .border(1.2.dp, Glassmorphism.specularBorderBrush, RoundedCornerShape(20.dp))
                    .padding(14.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Card Header Badge
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "SPOTIFLY",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = "LOSSLESS AUDIO",
                            color = SpotifyGreenBright,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                    }

                    // Artwork
                    val artPreviewSize = if (format == ShareCardFormat.STORY_9_16) 130.dp else 110.dp
                    Box(
                        modifier = Modifier
                            .size(artPreviewSize)
                            .shadow(16.dp, RoundedCornerShape(16.dp), ambientColor = Color.Black)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF1B1B26))
                            .border(1.dp, Glassmorphism.specularBorderBrush, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!song.artworkUrl.isNullOrEmpty()) {
                            AsyncImage(
                                model = song.artworkUrl,
                                contentDescription = song.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = SpotifyTextSecondary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    // Metadata
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = song.title,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = song.artist,
                            color = Color(0xCCFFFFFF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Stylized Equalizer Soundwave Preview
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val barHeights = listOf(6, 12, 18, 14, 22, 10, 16, 24, 18, 12, 20, 8, 14, 18, 10, 6)
                            barHeights.forEach { h ->
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(h.dp)
                                        .clip(CircleShape)
                                        .background(activeAccent)
                                )
                            }
                        }

                        // Optional Lyrics Preview
                        if (includeLyrics && !topLyricLine.isNullOrBlank() && format == ShareCardFormat.STORY_9_16) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.92f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x35FFFFFF))
                                    .border(0.8.dp, Color(0x45FFFFFF), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "“${topLyricLine.take(45)}”",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Card Footer
                    Text(
                        text = "PLAY ON SPOTIFLY",
                        color = Color(0x88FFFFFF),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Lyrics Toggle Chip (if lyrics available)
            if (!topLyricLine.isNullOrBlank() && format == ShareCardFormat.STORY_9_16) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (includeLyrics) Glassmorphism.accentGlassBrush(activeAccent, 0.20f) else Glassmorphism.darkGlassBrush)
                        .border(
                            1.dp,
                            if (includeLyrics) Glassmorphism.accentGlassBorderBrush(activeAccent) else Glassmorphism.specularBorderBrush,
                            RoundedCornerShape(14.dp)
                        )
                        .clickable { includeLyrics = !includeLyrics }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.FormatQuote,
                            contentDescription = null,
                            tint = if (includeLyrics) SpotifyGreenBright else Color(0xAAFFFFFF),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (includeLyrics) "Lyrics Quote Included" else "Add Lyric Quote",
                            color = if (includeLyrics) SpotifyGreenBright else Color(0xAAFFFFFF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Primary Action: Share to Stories / Apps
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .shadow(16.dp, RoundedCornerShape(25.dp), ambientColor = activeAccent, spotColor = activeAccent)
                    .clip(RoundedCornerShape(25.dp))
                    .background(Glassmorphism.accentGlassBrush(activeAccent))
                    .border(1.dp, Glassmorphism.accentGlassBorderBrush(activeAccent), RoundedCornerShape(25.dp))
                    .clickable(enabled = !isSharing) {
                        isSharing = true
                        coroutineScope.launch {
                            try {
                                ShareUtils.shareStoryCard(
                                    context = context,
                                    song = song,
                                    format = format,
                                    lyricsExcerpt = if (includeLyrics) topLyricLine else null,
                                    accentColorInt = accentInt
                                )
                            } finally {
                                isSharing = false
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isSharing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.IosShare,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = if (isSharing) "Rendering Card…" else "Share to Stories & Apps",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Secondary Actions: Save Image to Device & Copy Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Save to Gallery
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Glassmorphism.darkGlassBrush)
                        .border(1.dp, Glassmorphism.specularBorderBrush, RoundedCornerShape(22.dp))
                        .clickable(enabled = !isSaving) {
                            isSaving = true
                            coroutineScope.launch {
                                try {
                                    ShareUtils.saveCardToGallery(
                                        context = context,
                                        song = song,
                                        format = format,
                                        lyricsExcerpt = if (includeLyrics) topLyricLine else null,
                                        accentColorInt = accentInt
                                    )
                                } finally {
                                    isSaving = false
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = SpotifyWhite,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = SpotifyWhite,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = if (isSaving) "Saving…" else "Save Image",
                            color = SpotifyWhite,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Copy Text Info
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Glassmorphism.darkGlassBrush)
                        .border(1.dp, Glassmorphism.specularBorderBrush, RoundedCornerShape(22.dp))
                        .clickable {
                            ShareUtils.shareSong(context, song)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            tint = Color(0xCCFFFFFF),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Copy Info",
                            color = Color(0xCCFFFFFF),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
        }
    }
}
