package com.example.spotifly.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.spotifly.data.online.ChartFeed
import com.example.spotifly.model.PlaybackState
import com.example.spotifly.model.Song
import com.example.spotifly.theme.Glassmorphism
import com.example.spotifly.theme.SpotifyGreen
import com.example.spotifly.theme.SpotifyGreenBright
import com.example.spotifly.theme.SpotifyTextSecondary
import com.example.spotifly.theme.SpotifyWhite

/**
 * Full-height Dark Cinematic Modal Sheet displaying a complete ranked Global or Regional chart.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartDetailSheet(
    chart: ChartFeed,
    playbackState: PlaybackState,
    favorites: Set<String>,
    onPlayQueue: (List<Song>, Int) -> Unit,
    onFavoriteClick: (Song) -> Unit,
    onTrackMoreClick: (Song) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val accent = SpotifyGreen

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xF50C0C14),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.90f)
                .padding(horizontal = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .shadow(14.dp, RoundedCornerShape(14.dp), ambientColor = accent)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF1A1A26))
                        .border(1.dp, Glassmorphism.specularBorderBrush, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!chart.artworkUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = chart.artworkUrl,
                            contentDescription = chart.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = null,
                            tint = SpotifyGreenBright,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Glassmorphism.accentGlassBrush(accent, 0.22f))
                            .border(0.8.dp, Glassmorphism.accentGlassBorderBrush(accent), RoundedCornerShape(10.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = chart.regionBadge,
                            color = SpotifyGreenBright,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = chart.title,
                        color = SpotifyWhite,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${chart.songs.size} tracks • ${chart.subtitle}",
                        color = SpotifyTextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Glassmorphism.darkGlassBrush)
                        .border(1.dp, Glassmorphism.specularBorderBrush, CircleShape)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = SpotifyWhite,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Play All / Shuffle Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .shadow(12.dp, RoundedCornerShape(21.dp), ambientColor = accent)
                        .clip(RoundedCornerShape(21.dp))
                        .background(Glassmorphism.accentGlassBrush(accent))
                        .border(1.dp, Glassmorphism.accentGlassBorderBrush(accent), RoundedCornerShape(21.dp))
                        .clickable {
                            if (chart.songs.isNotEmpty()) onPlayQueue(chart.songs, 0)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Play Chart",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(21.dp))
                        .background(Glassmorphism.darkGlassBrush)
                        .border(1.dp, Glassmorphism.specularBorderBrush, RoundedCornerShape(21.dp))
                        .clickable {
                            if (chart.songs.isNotEmpty()) onPlayQueue(chart.songs.shuffled(), 0)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = null,
                            tint = SpotifyGreenBright,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Shuffle",
                            color = SpotifyWhite,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(
                    chart.songs,
                    key = { idx, s -> "chart_${chart.id}_${s.id}_$idx" }
                ) { index, song ->
                    val isPlaying = playbackState.currentSong?.id == song.id
                    val isFav = favorites.contains(song.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isPlaying) Glassmorphism.accentGlassBrush(accent, 0.16f) else Glassmorphism.darkGlassBrush)
                            .border(
                                1.dp,
                                if (isPlaying) Glassmorphism.accentGlassBorderBrush(accent) else Glassmorphism.specularBorderBrush,
                                RoundedCornerShape(14.dp)
                            )
                            .clickable { onPlayQueue(chart.songs, index) }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "%02d".format(index + 1),
                            color = if (index < 3 || isPlaying) SpotifyGreenBright else SpotifyTextSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(28.dp)
                        )

                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF171722)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!song.artworkUrl.isNullOrBlank()) {
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
                                    tint = SpotifyTextSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                color = if (isPlaying) SpotifyGreenBright else SpotifyWhite,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = song.artist,
                                color = SpotifyTextSecondary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (isPlaying && playbackState.isPlaying) {
                            EqualizerBars(
                                color = SpotifyGreenBright,
                                isPlaying = true,
                                modifier = Modifier.padding(end = 6.dp)
                            )
                        }

                        IconButton(
                            onClick = { onFavoriteClick(song) },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = if (isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (isFav) SpotifyGreenBright else SpotifyTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = { onTrackMoreClick(song) },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Options",
                                tint = SpotifyTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
