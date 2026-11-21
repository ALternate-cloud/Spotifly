package com.example.spotifly.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.spotifly.data.online.OnlineMusicService
import com.example.spotifly.data.stats.ArtistStat
import com.example.spotifly.model.Album
import com.example.spotifly.model.Artist
import com.example.spotifly.model.Playlist
import com.example.spotifly.model.PlaybackState
import com.example.spotifly.model.Song
import com.example.spotifly.theme.SpotifyBackground
import com.example.spotifly.theme.SpotifyCard
import com.example.spotifly.theme.SpotifyElevated
import com.example.spotifly.theme.SpotifyGreen
import com.example.spotifly.theme.SpotifyTextSecondary
import com.example.spotifly.theme.SpotifyWhite
import com.example.spotifly.ui.components.TrackItem
import java.util.Calendar
import com.example.spotifly.theme.SpotifyBlack

/** A heuristic mix derived from what the user actually played, not a model. */
private data class Mix(
    val title: String,
    val subtitle: String,
    val artworkUrl: String?,
    val songs: List<Song>
)

@Composable
fun HomeScreen(
    playbackState: PlaybackState,
    favorites: Set<String>,
    modifier: Modifier = Modifier,
    recentlyPlayed: List<Song> = emptyList(),
    topArtists: List<ArtistStat> = emptyList(),
    playlists: List<Playlist> = emptyList(),
    albums: List<Album> = emptyList(),
    onSongClick: (Song) -> Unit,
    onPlayQueue: (List<Song>, Int) -> Unit = { _, _ -> },
    onFavoriteClick: (Song) -> Unit,
    onShuffleAll: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenAlbum: (Album) -> Unit = {},
    onOpenArtist: (Artist) -> Unit = {},
    onOpenStats: () -> Unit = {},
    onOpenAudioSettings: () -> Unit = {},
) {
    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 5..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    val trendingHits = OnlineMusicService.trendingHits

    // "Made for you" mixes: one per artist the user keeps coming back to, built from
    // their own history. This is the honest version of a Daily Mix — no model, just
    // the user's own listening, grouped and labelled.
    val mixes = remember(recentlyPlayed) {
        recentlyPlayed
            .groupBy { it.artist.split(",").first().trim() }
            .filter { (_, songs) -> songs.size >= 2 }
            .map { (artistName, songs) ->
                Mix(
                    title = "$artistName Mix",
                    subtitle = "${songs.size} songs you played",
                    artworkUrl = songs.firstNotNullOfOrNull { it.artworkUrl },
                    songs = songs.distinctBy { it.id }
                )
            }
            .take(6)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(SpotifyBackground),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        // Top Header with stats + settings
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = greeting,
                    color = SpotifyWhite,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onOpenStats) {
                        Icon(
                            imageVector = Icons.Default.BarChart,
                            contentDescription = "Your listening",
                            tint = SpotifyTextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = SpotifyTextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        // Jump back in: what the user actually played last
        if (recentlyPlayed.isNotEmpty()) {
            item {
                SectionTitle("Jump back in")
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(recentlyPlayed.take(10), key = { "recent_${it.id}" }) { song ->
                        RecentCard(
                            song = song,
                            isPlaying = playbackState.currentSong?.id == song.id && playbackState.isPlaying,
                            onClick = { onSongClick(song) }
                        )
                    }
                }
            }
        }

        // Quick Jump 2x3 Grid
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val gridItems = trendingHits.take(6).chunked(2)
                for (row in gridItems) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (song in row) {
                            val songIndex = trendingHits.indexOfFirst { it.id == song.id }
                            QuickPlayCard(
                                song = song,
                                isPlaying = playbackState.currentSong?.id == song.id && playbackState.isPlaying,
                                onClick = {
                                    if (songIndex != -1) onPlayQueue(trendingHits, songIndex) else onSongClick(song)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (row.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // Shuffle everything action
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(SpotifyGreen)
                        .clickable { onShuffleAll() }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = null,
                        tint = SpotifyBlack,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Shuffle all my music",
                        color = SpotifyBlack,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Made for you — heuristic mixes from the user's own history
        if (mixes.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                SectionTitle("Made for you")
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(mixes, key = { "mix_${it.title}" }) { mix ->
                        MixCard(
                            mix = mix,
                            onClick = {
                                if (mix.songs.isNotEmpty()) onPlayQueue(mix.songs, 0)
                            }
                        )
                    }
                }
            }
        }

        // Top artists, from the local play log
        if (topArtists.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(28.dp))
                SectionTitle("Your top artists")
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(topArtists, key = { "topartist_${it.name}" }) { artistStat ->
                        TopArtistCard(
                            stat = artistStat,
                            onClick = {
                                val songs = recentlyPlayed.filter {
                                    it.artist.contains(artistStat.name, ignoreCase = true)
                                }
                                val artist = Artist(
                                    id = "stats_artist_${artistStat.name.lowercase()}",
                                    name = artistStat.name,
                                    artworkUrl = artistStat.artworkUrl,
                                    songs = songs,
                                    albums = emptyList()
                                )
                                onOpenArtist(artist)
                            }
                        )
                    }
                }
            }
        }

        // Albums the library knows about
        if (albums.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(28.dp))
                SectionTitle("Albums for you")
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(albums.take(12), key = { "album_${it.id}" }) { album ->
                        AlbumTile(album = album, onClick = { onOpenAlbum(album) })
                    }
                }
            }
        }

        // The user's own playlists
        if (playlists.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(28.dp))
                SectionTitle("Your playlists")
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(playlists, key = { "playlist_${it.id}" }) { playlist ->
                        PlaylistCard(
                            playlist = playlist,
                            onClick = {
                                if (playlist.songs.isNotEmpty()) onPlayQueue(playlist.songs, 0)
                            }
                        )
                    }
                }
            }
        }

        // Section Title: Trending Hits
        item {
            Spacer(modifier = Modifier.height(28.dp))
            SectionTitle("Trending Hits")
        }

        // Horizontal Carousel of Trending Albums
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                itemsIndexed(trendingHits) { index, song ->
                    AlbumCard(
                        song = song,
                        onClick = { onPlayQueue(trendingHits, index) }
                    )
                }
            }
        }

        // Section Title: Recommended Tracks
        item {
            Spacer(modifier = Modifier.height(28.dp))
            SectionTitle("Featured for You")
        }

        // List of Tracks
        itemsIndexed(trendingHits) { index, song ->
            TrackItem(
                song = song,
                isPlaying = playbackState.currentSong?.id == song.id,
                isFavorite = favorites.contains(song.id),
                onClick = { onPlayQueue(trendingHits, index) },
                onFavoriteClick = { onFavoriteClick(song) }
            )
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
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun MixCard(mix: Mix, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SpotifyElevated),
            contentAlignment = Alignment.Center
        ) {
            if (!mix.artworkUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = mix.artworkUrl,
                    contentDescription = mix.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = SpotifyTextSecondary,
                    modifier = Modifier.size(44.dp)
                )
            }
            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(SpotifyGreen)
                    .align(Alignment.BottomEnd),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play mix",
                    tint = SpotifyBlack,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = mix.title,
            color = SpotifyWhite,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = mix.subtitle,
            color = SpotifyTextSecondary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TopArtistCard(stat: ArtistStat, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(120.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(SpotifyCard),
            contentAlignment = Alignment.Center
        ) {
            if (!stat.artworkUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = stat.artworkUrl,
                    contentDescription = stat.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
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
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stat.name,
            color = SpotifyWhite,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${stat.plays} plays",
            color = SpotifyTextSecondary,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun AlbumTile(album: Album, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SpotifyCard),
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
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = SpotifyTextSecondary,
                    modifier = Modifier.size(44.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = album.title,
            color = SpotifyWhite,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = album.displayArtist,
            color = SpotifyTextSecondary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PlaylistCard(playlist: Playlist, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SpotifyCard),
            contentAlignment = Alignment.Center
        ) {
            val artwork = playlist.songs.firstOrNull()?.artworkUrl
            if (!artwork.isNullOrEmpty()) {
                AsyncImage(
                    model = artwork,
                    contentDescription = playlist.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = SpotifyTextSecondary,
                    modifier = Modifier.size(44.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = playlist.name,
            color = SpotifyWhite,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${playlist.songs.size} songs",
            color = SpotifyTextSecondary,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun RecentCard(
    song: Song,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(120.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(8.dp))
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
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = SpotifyTextSecondary,
                    modifier = Modifier.size(36.dp)
                )
            }

            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .padding(6.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(SpotifyGreen)
                        .align(Alignment.BottomEnd),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = SpotifyBlack,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = song.title,
            color = if (isPlaying) SpotifyGreen else SpotifyWhite,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = song.artist,
            color = SpotifyTextSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun QuickPlayCard(
    song: Song,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(SpotifyElevated)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
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
                    tint = SpotifyTextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = song.title,
            color = if (isPlaying) SpotifyGreen else SpotifyWhite,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        )
    }
}

@Composable
private fun AlbumCard(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(140.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(8.dp))
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
                    modifier = Modifier.size(48.dp)
                )
            }

            // Green play button overlay badge
            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(SpotifyGreen)
                    .align(Alignment.BottomEnd),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = SpotifyBlack,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

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
}
