package com.example.spotifly.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.spotifly.model.Song
import com.example.spotifly.theme.SpotifyCard
import com.example.spotifly.theme.SpotifyElevated
import com.example.spotifly.theme.SpotifyGreen
import com.example.spotifly.theme.SpotifyTextSecondary
import com.example.spotifly.theme.SpotifyWhite

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackOptionsBottomSheet(
    song: Song,
    isFavorite: Boolean,
    isDownloaded: Boolean,
    isDownloading: Boolean = false,
    downloadProgressPercent: Int? = null,
    onAddToPlaylistClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onCancelDownloadClick: () -> Unit = {},
    onAddToQueueClick: () -> Unit,
    onToggleFavoriteClick: () -> Unit,
    onDismiss: () -> Unit,
    onPlayNextClick: () -> Unit = {},
    onSongRadioClick: () -> Unit = {},
    onShareClick: () -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SpotifyElevated,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(SpotifyCard)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header: Track preview
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(SpotifyCard),
                    contentAlignment = Alignment.Center
                ) {
                    if (!song.artworkUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = song.artworkUrl,
                            contentDescription = song.title,
                            modifier = Modifier.fillMaxWidth(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = SpotifyTextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = song.title,
                        color = SpotifyWhite,
                        fontSize = 16.sp,
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

            // Options List
            OptionRow(
                icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                title = "Add to playlist",
                onClick = {
                    onDismiss()
                    onAddToPlaylistClick()
                }
            )

            if (!song.isLocal) {
                OptionRow(
                    icon = if (isDownloaded) Icons.Default.Delete else Icons.Default.Download,
                    title = when {
                        isDownloaded -> "Remove download"
                        isDownloading && downloadProgressPercent != null -> "Downloading… $downloadProgressPercent% (tap to cancel)"
                        isDownloading -> "Downloading… (tap to cancel)"
                        else -> "Download for offline play"
                    },
                    iconTint = if (isDownloaded) SpotifyGreen else SpotifyWhite,
                    onClick = {
                        onDismiss()
                        if (isDownloading) onCancelDownloadClick() else onDownloadClick()
                    }
                )
            }

            OptionRow(
                icon = Icons.AutoMirrored.Filled.QueueMusic,
                title = "Add to queue",
                onClick = {
                    onDismiss()
                    onAddToQueueClick()
                }
            )

            OptionRow(
                icon = Icons.Default.SkipNext,
                title = "Play next",
                onClick = {
                    onDismiss()
                    onPlayNextClick()
                }
            )

            if (!song.isPodcast) {
                OptionRow(
                    icon = Icons.Default.Radio,
                    title = "Start Radio",
                    onClick = {
                        onDismiss()
                        onSongRadioClick()
                    }
                )
            }

            OptionRow(
                icon = Icons.Default.Share,
                title = "Share",
                onClick = {
                    onDismiss()
                    onShareClick()
                }
            )

            OptionRow(
                icon = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                title = if (isFavorite) "Remove from Liked Songs" else "Like this song",
                iconTint = if (isFavorite) SpotifyGreen else SpotifyWhite,
                onClick = {
                    onToggleFavoriteClick()
                    onDismiss()
                }
            )
        }
    }
}

@Composable
private fun OptionRow(
    icon: ImageVector,
    title: String,
    iconTint: androidx.compose.ui.graphics.Color = SpotifyWhite,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            color = SpotifyWhite,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
