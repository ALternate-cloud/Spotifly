package com.example.spotifly.ui.artist

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.spotifly.model.Album
import com.example.spotifly.model.Artist
import com.example.spotifly.model.DownloadStatus
import com.example.spotifly.model.PlaybackState
import com.example.spotifly.model.Song
import com.example.spotifly.theme.Glassmorphism
import com.example.spotifly.theme.SpotifyBackground
import com.example.spotifly.theme.SpotifyCard
import com.example.spotifly.theme.SpotifyGreen
import com.example.spotifly.theme.SpotifyGreenBright
import com.example.spotifly.theme.SpotifyTextSecondary
import com.example.spotifly.theme.SpotifyWhite
import com.example.spotifly.ui.components.DetailHeader
import com.example.spotifly.ui.components.TrackItem

@Composable
fun ArtistDetailScreen(
    artist: Artist,
    playbackState: PlaybackState,
    favorites: Set<String>,
    modifier: Modifier = Modifier,
    downloadStatus: Map<String, DownloadStatus> = emptyMap(),
    relatedArtists: List<Artist> = emptyList(),
    isLoadingRelatedArtists: Boolean = false,
    onBack: () -> Unit,
    onPlayQueue: (List<Song>, Int) -> Unit,
    onFavoriteClick: (Song) -> Unit,
    onTrackMoreClick: (Song) -> Unit = {},
    onOpenAlbum: (Album) -> Unit = {},
    onOpenRelatedArtist: (Artist) -> Unit = {},
    onStartRadio: () -> Unit = {},
    isFollowed: Boolean = false,
    onToggleFollowed: () -> Unit = {},
) {
    val popular = artist.songs.take(5)
    val meta = buildString {
        append("${artist.trackCount} ${if (artist.trackCount == 1) "song" else "songs"}")
        if (artist.albumCount > 0) append(" • ${artist.albumCount} albums")
        if (artist.isLocal) append(" • local")
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(SpotifyBackground),
        contentPadding = PaddingValues(bottom = 140.dp)
    ) {
        item {
            DetailHeader(
                eyebrow = "Artist",
                title = artist.name,
                meta = meta,
                artworkUrl = artist.artworkUrl,
                fallbackIcon = Icons.Default.Person,
                isArtist = true,
                onBack = onBack,
                onPlay = {
                    val index = artist.songs.indexOfFirst { it.id == playbackState.currentSong?.id }
                    onPlayQueue(artist.songs, if (index >= 0) index else 0)
                },
                onShuffle = {
                    if (artist.songs.isNotEmpty()) onPlayQueue(artist.songs.shuffled(), 0)
                },
                actions = {
                    IconButton(onClick = onToggleFollowed) {
                        Icon(
                            imageVector = if (isFollowed) Icons.Default.CheckCircle else Icons.Default.PersonAdd,
                            contentDescription = if (isFollowed) "Unfollow artist" else "Follow artist",
                            tint = if (isFollowed) SpotifyGreen else SpotifyWhite
                        )
                    }
                }
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Glassmorphism.darkGlassBrush)
                        .border(1.dp, Glassmorphism.specularBorderBrush, RoundedCornerShape(20.dp))
                        .clickable(onClick = onStartRadio)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Radio,
                        contentDescription = null,
                        tint = SpotifyGreenBright,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Artist Radio",
                        color = SpotifyWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (popular.isNotEmpty()) {
            item {
                SectionTitle("Popular")
            }
            itemsIndexed(popular, key = { _, song -> "popular_${song.id}" }) { _, song ->
                TrackItem(
                    song = song,
                    isPlaying = playbackState.currentSong?.id == song.id,
                    isFavorite = favorites.contains(song.id),
                    isDownloaded = downloadStatus[song.id] == DownloadStatus.DOWNLOADED || song.isDownloaded,
                    isDownloading = downloadStatus[song.id] == DownloadStatus.DOWNLOADING,
                    onClick = {
                        val idx = artist.songs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                        onPlayQueue(artist.songs, idx)
                    },
                    onFavoriteClick = { onFavoriteClick(song) },
                    onMoreClick = { onTrackMoreClick(song) }
                )
            }
        }

        if (artist.albums.isNotEmpty()) {
            item {
                SectionTitle("Albums")
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(artist.albums, key = { it.id }) { album ->
                        AlbumTile(album = album, onClick = { onOpenAlbum(album) })
                    }
                }
            }
        }

        // Fans Also Like / Related Artists carousel
        if (isLoadingRelatedArtists || relatedArtists.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(10.dp))
                SectionTitle("Fans Also Like")
            }
            item {
                if (isLoadingRelatedArtists && relatedArtists.isEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            color = SpotifyGreenBright,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Finding similar artists…",
                            color = SpotifyTextSecondary,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(relatedArtists, key = { "rel_${it.id}_${it.name}" }) { relArtist ->
                            RelatedArtistTile(
                                artist = relArtist,
                                onClick = { onOpenRelatedArtist(relArtist) }
                            )
                        }
                    }
                }
            }
        }

        if (artist.songs.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                SectionTitle("All songs")
            }
            itemsIndexed(artist.songs, key = { _, song -> "all_${song.id}" }) { index, song ->
                TrackItem(
                    song = song,
                    isPlaying = playbackState.currentSong?.id == song.id,
                    isFavorite = favorites.contains(song.id),
                    isDownloaded = downloadStatus[song.id] == DownloadStatus.DOWNLOADED || song.isDownloaded,
                    isDownloading = downloadStatus[song.id] == DownloadStatus.DOWNLOADING,
                    onClick = { onPlayQueue(artist.songs, index) },
                    onFavoriteClick = { onFavoriteClick(song) },
                    onMoreClick = { onTrackMoreClick(song) }
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = SpotifyWhite,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun AlbumTile(album: Album, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(130.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(130.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(SpotifyCard)
                .border(0.8.dp, Glassmorphism.specularBorderBrush, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (!album.artworkUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = album.artworkUrl,
                    contentDescription = album.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = SpotifyTextSecondary,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = album.title,
            color = SpotifyWhite,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = if (album.year > 0) "${album.year}" else "Album",
            color = SpotifyTextSecondary,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun RelatedArtistTile(artist: Artist, onClick: () -> Unit) {
    val accent = SpotifyGreen
    Column(
        modifier = Modifier
            .width(114.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Glassmorphism.darkGlassBrush)
            .border(1.dp, Glassmorphism.specularBorderBrush, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .shadow(12.dp, CircleShape, ambientColor = accent)
                .clip(CircleShape)
                .background(Color(0xFF1A1A26))
                .border(1.2.dp, Glassmorphism.accentGlassBorderBrush(accent), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (!artist.artworkUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = artist.artworkUrl,
                    contentDescription = artist.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = SpotifyTextSecondary,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = artist.name,
            color = SpotifyWhite,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Similar Artist",
            color = SpotifyTextSecondary,
            fontSize = 10.sp
        )
    }
}
