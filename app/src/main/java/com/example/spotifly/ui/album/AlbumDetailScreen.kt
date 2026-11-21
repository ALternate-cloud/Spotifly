package com.example.spotifly.ui.album

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spotifly.model.Album
import com.example.spotifly.model.DownloadStatus
import com.example.spotifly.model.PlaybackState
import com.example.spotifly.model.Song
import com.example.spotifly.theme.SpotifyBackground
import com.example.spotifly.theme.SpotifyGreen
import com.example.spotifly.theme.SpotifyTextSecondary
import com.example.spotifly.theme.SpotifyWhite
import com.example.spotifly.ui.components.DetailHeader
import com.example.spotifly.ui.components.TrackItem

@Composable
fun AlbumDetailScreen(
    album: Album,
    playbackState: PlaybackState,
    favorites: Set<String>,
    modifier: Modifier = Modifier,
    downloadStatus: Map<String, DownloadStatus> = emptyMap(),
    onBack: () -> Unit,
    onPlayQueue: (List<Song>, Int) -> Unit,
    onFavoriteClick: (Song) -> Unit,
    onTrackMoreClick: (Song) -> Unit = {},
    onOpenArtist: (Song) -> Unit = {},
    isSaved: Boolean = false,
    onToggleSaved: () -> Unit = {},
    onDownloadAll: () -> Unit = {},
) {
    val meta = buildString {
        append(album.displayArtist)
        if (album.year > 0) append(" • ${album.year}")
        append(" • ${album.trackCount} ${if (album.trackCount == 1) "song" else "songs"}")
        val minutes = album.totalDurationMs / 60_000
        if (minutes > 0) append(" • $minutes min")
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(SpotifyBackground),
        contentPadding = PaddingValues(bottom = 140.dp)
    ) {
        item {
            DetailHeader(
                eyebrow = "Album",
                title = album.title,
                meta = meta,
                artworkUrl = album.artworkUrl,
                fallbackIcon = Icons.Default.MusicNote,
                onBack = onBack,
                onPlay = {
                    val index = album.songs.indexOfFirst { it.id == playbackState.currentSong?.id }
                    onPlayQueue(album.songs, if (index >= 0) index else 0)
                },
                onShuffle = {
                    if (album.songs.isNotEmpty()) onPlayQueue(album.songs.shuffled(), 0)
                },
                actions = {
                    if (album.songs.isNotEmpty()) {
                        IconButton(onClick = onDownloadAll) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Download album",
                                tint = SpotifyWhite
                            )
                        }
                    }
                    IconButton(onClick = onToggleSaved) {
                        Icon(
                            imageVector = if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = if (isSaved) "Remove saved album" else "Save album",
                            tint = if (isSaved) SpotifyGreen else SpotifyWhite
                        )
                    }
                }
            )
        }

        item {
            TextButton(
                onClick = { album.songs.firstOrNull()?.let(onOpenArtist) },
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                Text(
                    text = album.displayArtist,
                    color = SpotifyWhite,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (album.songs.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No tracks loaded for this album yet.",
                        color = SpotifyTextSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            itemsIndexed(album.songs, key = { _, song -> song.id }) { index, song ->
                TrackItem(
                    song = song,
                    isPlaying = playbackState.currentSong?.id == song.id,
                    isFavorite = favorites.contains(song.id),
                    isDownloaded = downloadStatus[song.id] == DownloadStatus.DOWNLOADED || song.isDownloaded,
                    isDownloading = downloadStatus[song.id] == DownloadStatus.DOWNLOADING,
                    onClick = { onPlayQueue(album.songs, index) },
                    onFavoriteClick = { onFavoriteClick(song) },
                    onMoreClick = { onTrackMoreClick(song) }
                )
            }
        }
    }
}
