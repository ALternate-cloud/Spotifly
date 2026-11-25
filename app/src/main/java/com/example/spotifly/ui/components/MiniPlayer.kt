package com.example.spotifly.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.spotifly.model.PlaybackState
import com.example.spotifly.theme.Glassmorphism
import com.example.spotifly.theme.SpotifyCard
import com.example.spotifly.theme.SpotifyElevated
import com.example.spotifly.theme.SpotifyGreen
import com.example.spotifly.theme.SpotifyTextSecondary
import com.example.spotifly.theme.SpotifyWhite

@Composable
fun MiniPlayer(
    playbackState: PlaybackState,
    isFavorite: Boolean,
    onPlayPauseClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val song = playbackState.currentSong ?: return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .shadow(24.dp, RoundedCornerShape(16.dp), ambientColor = Color.Black)
            .clip(RoundedCornerShape(16.dp))
            .background(Glassmorphism.darkGlassBrush)
            .border(1.2.dp, Glassmorphism.specularBorderBrush, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Artwork with subtle glassy bezel
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF14141E))
                    .border(1.dp, Color(0x35FFFFFF), RoundedCornerShape(10.dp)),
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
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Title & Artist
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = song.title,
                    color = SpotifyWhite,
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

            // Live equalizer bars while playing — a permission-free "now sounding" cue
            if (playbackState.isPlaying && !playbackState.isBuffering) {
                EqualizerBars(
                    color = SpotifyGreen,
                    isPlaying = true,
                    maxHeight = 16.dp,
                    modifier = Modifier.padding(end = 6.dp)
                )
            }

            // Favorite Button
            IconButton(
                onClick = onFavoriteClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (isFavorite) SpotifyGreen else SpotifyWhite,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Play / Pause Button or Loading Spinner
            IconButton(
                onClick = onPlayPauseClick,
                modifier = Modifier.size(38.dp)
            ) {
                if (playbackState.isBuffering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = SpotifyGreen,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = if (playbackState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                        tint = SpotifyWhite,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        // Progress bar at the bottom
        LinearProgressIndicator(
            progress = { playbackState.progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(2.5.dp),
            color = SpotifyGreen,
            trackColor = Color(0x18FFFFFF),
            strokeCap = StrokeCap.Round
        )
    }
}
