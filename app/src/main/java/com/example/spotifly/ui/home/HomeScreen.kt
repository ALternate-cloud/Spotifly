package com.example.spotifly.ui.home

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.spotifly.data.online.ChartFeed
import com.example.spotifly.data.online.OnlineMusicService
import com.example.spotifly.data.stats.ArtistStat
import com.example.spotifly.model.Album
import com.example.spotifly.model.Artist
import com.example.spotifly.model.Playlist
import com.example.spotifly.model.PlaybackState
import com.example.spotifly.model.Song
import com.example.spotifly.theme.Glassmorphism
import com.example.spotifly.theme.SpotifyBackground
import com.example.spotifly.theme.SpotifyBlack
import com.example.spotifly.theme.SpotifyCard
import com.example.spotifly.theme.SpotifyElevated
import com.example.spotifly.theme.SpotifyGreen
import com.example.spotifly.theme.SpotifyGreenBright
import com.example.spotifly.theme.SpotifyTextSecondary
import com.example.spotifly.theme.SpotifyWhite
import com.example.spotifly.ui.components.CurvedNeonPlayButton
import com.example.spotifly.ui.components.EqualizerBars
import com.example.spotifly.ui.components.TrackItem
import java.util.Calendar

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
    chartFeeds: List<ChartFeed> = emptyList(),
    isLoadingCharts: Boolean = false,
    offlineMode: Boolean = false,
    onSongClick: (Song) -> Unit,
    onPlayQueue: (List<Song>, Int) -> Unit = { _, _ -> },
    onFavoriteClick: (Song) -> Unit,
    onTrackMoreClick: (Song) -> Unit = {},
    onShuffleAll: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenAlbum: (Album) -> Unit = {},
    onOpenArtist: (Artist) -> Unit = {},
    onOpenChart: (ChartFeed) -> Unit = {},
    onRefreshCharts: () -> Unit = {},
    onOpenStats: () -> Unit = {},
    onOpenAudioSettings: () -> Unit = {},
    onOpenDiscover: () -> Unit = {},
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
    val categories = remember { listOf("All", "Popular", "Trending", "New Releases") }
    var selectedCategory by remember { mutableStateOf("All") }
    var selectedChartId by remember(chartFeeds) {
        mutableStateOf(chartFeeds.firstOrNull()?.id ?: "curated")
    }

    // "Made for you" mixes: one per artist the user keeps coming back to, built from
    // their own history.
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

    val accent = SpotifyGreen

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(SpotifyBackground),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        // 1. Top Greeting & Profile Area (Screen B)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // User Profile & Greeting
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .shadow(12.dp, CircleShape, ambientColor = accent)
                            .clip(CircleShape)
                            .background(Color(0xFF161622))
                            .border(2.dp, accent.copy(alpha = 0.8f), CircleShape)
                            .border(4.dp, Color(0xFF101016), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = "Profile",
                            tint = accent,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Welcome back,",
                            color = SpotifyTextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = greeting,
                            color = SpotifyWhite,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Right Action Buttons: Frosted Glass Capsule Buttons (Screen A Discover, Stats, Settings)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Glass Discover Button
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0x24FFFFFF))
                            .border(1.dp, Glassmorphism.specularBorderBrush, CircleShape)
                            .clickable(onClick = onOpenDiscover),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Discover",
                            tint = accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Glass Stats Button
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0x24FFFFFF))
                            .border(1.dp, Glassmorphism.specularBorderBrush, CircleShape)
                            .clickable(onClick = onOpenStats),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.BarChart,
                            contentDescription = "Your listening",
                            tint = Color(0xDDFFFFFF),
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    // Glass Settings Button
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0x24FFFFFF))
                            .border(1.dp, Glassmorphism.specularBorderBrush, CircleShape)
                            .clickable(onClick = onOpenSettings),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color(0xDDFFFFFF),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // 2. Category Tabs: Frosted Glass Capsule Chips (All, Popular, Trending, New Releases)
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                items(categories) { category ->
                    val isSelected = category == selectedCategory
                    Box(
                        modifier = Modifier
                            .shadow(if (isSelected) 8.dp else 2.dp, RoundedCornerShape(20.dp), ambientColor = if (isSelected) accent else Color.Transparent)
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) Glassmorphism.accentGlassBrush(accent) else Glassmorphism.subtleGlassFill)
                            .border(
                                1.2.dp,
                                if (isSelected) Glassmorphism.accentGlassBorderBrush(accent) else Glassmorphism.specularBorderBrush,
                                RoundedCornerShape(20.dp)
                            )
                            .clickable { selectedCategory = category }
                            .padding(horizontal = 18.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = category,
                            color = if (isSelected) SpotifyGreenBright else SpotifyWhite,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        // 3. Featured Music Hero Card (Screen B)
        if (trendingHits.isNotEmpty() && (selectedCategory == "All" || selectedCategory == "Trending")) {
            item {
                val activeHeroSong = chartFeeds.firstOrNull()?.songs?.firstOrNull() ?: trendingHits.first()
                val activeHeroQueue = chartFeeds.firstOrNull()?.songs?.takeIf { it.isNotEmpty() } ?: trendingHits
                val isFeaturedPlaying = playbackState.currentSong?.id == activeHeroSong.id && playbackState.isPlaying
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    FeaturedMusicHeroCard(
                        song = activeHeroSong,
                        isPlaying = isFeaturedPlaying,
                        onClick = { onPlayQueue(activeHeroQueue, 0) }
                    )
                }
            }
        }

        // 4. Global & Regional Top Charts Shelf (Track 2 Discovery)
        if (!offlineMode && (chartFeeds.isNotEmpty() || isLoadingCharts)) {
            item {
                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = null,
                            tint = SpotifyGreenBright,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Global & Regional Charts",
                            color = SpotifyWhite,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (isLoadingCharts) {
                        CircularProgressIndicator(
                            color = SpotifyGreenBright,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        IconButton(
                            onClick = onRefreshCharts,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh charts",
                                tint = SpotifyTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            if (chartFeeds.isNotEmpty()) {
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(chartFeeds, key = { "chart_card_${it.id}" }) { chart ->
                            TopChartCard(
                                chart = chart,
                                onCardClick = { onOpenChart(chart) },
                                onPlayClick = {
                                    if (chart.songs.isNotEmpty()) onPlayQueue(chart.songs, 0)
                                }
                            )
                        }
                    }
                }
            }
        }

        // 5. Ranked Charts / Trending Section with Region Filter Pills
        val activeChartFeed = chartFeeds.firstOrNull { it.id == selectedChartId } ?: chartFeeds.firstOrNull()
        val rankedQueue = if (!offlineMode && activeChartFeed != null && selectedChartId != "curated") {
            activeChartFeed.songs
        } else {
            trendingHits
        }
        val rankedHits = rankedQueue.take(5)

        if (selectedCategory == "All" || selectedCategory == "Trending" || selectedCategory == "New Releases") {
            item {
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedCategory == "New Releases") "Rising Chart Hits" else "Trending Now",
                        color = SpotifyWhite,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "See all",
                        color = accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable {
                            if (activeChartFeed != null) {
                                onOpenChart(activeChartFeed)
                            } else {
                                selectedCategory = "Trending"
                            }
                        }
                    )
                }

                if (!offlineMode && chartFeeds.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        items(chartFeeds, key = { "region_${it.id}" }) { feed ->
                            val pillSelected = selectedChartId == feed.id
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (pillSelected) Glassmorphism.accentGlassBrush(accent, 0.24f) else Glassmorphism.subtleGlassFill)
                                    .border(
                                        1.dp,
                                        if (pillSelected) Glassmorphism.accentGlassBorderBrush(accent) else Glassmorphism.specularBorderBrush,
                                        RoundedCornerShape(14.dp)
                                    )
                                    .clickable { selectedChartId = feed.id }
                                    .padding(horizontal = 12.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = feed.title,
                                    color = if (pillSelected) SpotifyGreenBright else SpotifyTextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = if (pillSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            itemsIndexed(rankedHits, key = { idx, s -> "ranked_${selectedChartId}_${s.id}_$idx" }) { index, song ->
                TrendingRankedTrackRow(
                    rank = index + 1,
                    song = song,
                    isPlaying = playbackState.currentSong?.id == song.id && playbackState.isPlaying,
                    isFavorite = favorites.contains(song.id),
                    onClick = { onPlayQueue(rankedQueue, index) },
                    onFavoriteClick = { onFavoriteClick(song) }
                )
            }
        }

        // 6. Curated / Top Playlists with Artwork (Screen B)
        if (selectedCategory == "All" || selectedCategory == "Popular") {
            item {
                Spacer(modifier = Modifier.height(24.dp))
                SectionTitle("Top Playlists")
            }

            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (playlists.isNotEmpty()) {
                        items(playlists, key = { "pl_${it.id}" }) { playlist ->
                            ModernPlaylistCard(
                                playlist = playlist,
                                onClick = {
                                    if (playlist.songs.isNotEmpty()) onPlayQueue(playlist.songs, 0)
                                }
                            )
                        }
                    } else {
                        val showcasePlaylists = listOf(
                            Playlist(id = "curated_hits", name = "Today's Top Hits", songs = trendingHits.take(8)),
                            Playlist(id = "curated_chill", name = "Chill Vibes", songs = trendingHits.reversed().take(6)),
                            Playlist(id = "curated_workout", name = "Workout Energy", songs = trendingHits.shuffled().take(6))
                        )
                        items(showcasePlaylists) { playlist ->
                            ModernPlaylistCard(
                                playlist = playlist,
                                onClick = {
                                    if (playlist.songs.isNotEmpty()) onPlayQueue(playlist.songs, 0)
                                }
                            )
                        }
                    }
                }
            }
        }

        // 7. Your Top Artists (opens Artist Profile & "Fans Also Like")
        if (topArtists.isNotEmpty() && (selectedCategory == "All" || selectedCategory == "Popular")) {
            item {
                Spacer(modifier = Modifier.height(24.dp))
                SectionTitle("Your Top Artists")
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(topArtists.take(10), key = { "top_art_${it.name}" }) { stat ->
                        TopArtistCard(
                            stat = stat,
                            onClick = {
                                onOpenArtist(
                                    Artist(
                                        id = "artist_${stat.name.lowercase().trim()}",
                                        name = stat.name,
                                        artworkUrl = stat.artworkUrl
                                    )
                                )
                            }
                        )
                    }
                }
            }
        }

        // 8. Jump back in: what the user actually played last
        if (recentlyPlayed.isNotEmpty() && (selectedCategory == "All" || selectedCategory == "Popular")) {
            item {
                Spacer(modifier = Modifier.height(24.dp))
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

        // 9. Made for you mixes
        if (mixes.isNotEmpty() && selectedCategory == "All") {
            item {
                Spacer(modifier = Modifier.height(24.dp))
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

        // 10. Albums for you
        if (albums.isNotEmpty() && (selectedCategory == "All" || selectedCategory == "New Releases")) {
            item {
                Spacer(modifier = Modifier.height(24.dp))
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

        // 11. All Recommended Tracks
        item {
            Spacer(modifier = Modifier.height(24.dp))
            SectionTitle("Featured for You")
        }

        itemsIndexed(trendingHits, key = { idx, s -> "feat_${s.id}_$idx" }) { index, song ->
            TrackItem(
                song = song,
                isPlaying = playbackState.currentSong?.id == song.id,
                isFavorite = favorites.contains(song.id),
                onClick = { onPlayQueue(trendingHits, index) },
                onFavoriteClick = { onFavoriteClick(song) },
                onMoreClick = { onTrackMoreClick(song) }
            )
        }
    }
}

@Composable
private fun TopChartCard(
    chart: ChartFeed,
    onCardClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    val accent = SpotifyGreen
    Column(
        modifier = Modifier
            .width(184.dp)
            .shadow(16.dp, RoundedCornerShape(18.dp), ambientColor = Color.Black)
            .clip(RoundedCornerShape(18.dp))
            .background(Glassmorphism.darkGlassBrush)
            .border(1.2.dp, Glassmorphism.specularBorderBrush, RoundedCornerShape(18.dp))
            .clickable(onClick = onCardClick)
            .padding(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF1A1A26))
                .border(1.dp, Glassmorphism.specularBorderBrush, RoundedCornerShape(14.dp))
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
                    modifier = Modifier
                        .size(40.dp)
                        .align(Alignment.Center)
                )
            }

            // Dark scrim for readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0x55000000), Color.Transparent, Color(0xBB08080E))
                        )
                    )
            )

            // Region badge top-left
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x990A0A12))
                    .border(0.8.dp, Glassmorphism.accentGlassBorderBrush(accent), RoundedCornerShape(10.dp))
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(
                    text = chart.regionBadge,
                    color = SpotifyGreenBright,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
            }

            // Play button bottom-right
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(34.dp)
                    .shadow(8.dp, CircleShape, ambientColor = accent)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(colors = listOf(SpotifyGreenBright, accent)))
                    .border(0.8.dp, Color(0x70FFFFFF), CircleShape)
                    .clickable(onClick = onPlayClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play chart",
                    tint = SpotifyBlack,
                    modifier = Modifier.size(19.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = chart.title,
            color = SpotifyWhite,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        val previewArtists = chart.songs.take(2).joinToString(" • ") { it.title }
        Text(
            text = previewArtists.ifBlank { "${chart.songs.size} tracks" },
            color = SpotifyTextSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun FeaturedMusicHeroCard(
    song: Song,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = SpotifyGreen
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(208.dp)
            .shadow(24.dp, RoundedCornerShape(22.dp), ambientColor = Color.Black)
            .clip(RoundedCornerShape(22.dp))
            .background(Glassmorphism.deepObsidianGlassBrush)
            .border(1.5.dp, Glassmorphism.specularBorderBrush, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
    ) {
        if (!song.artworkUrl.isNullOrEmpty()) {
            AsyncImage(
                model = song.artworkUrl,
                contentDescription = song.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Dark gradient scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0x20000000),
                            Color(0x8808080E),
                            Color(0xF208080E)
                        )
                    )
                )
        )

        // Glassy specular light sheen across top edge
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0x35FFFFFF), Color.Transparent)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Glassy Top Trending badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x550A1A12))
                        .border(1.dp, Glassmorphism.accentGlassBorderBrush(accent), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "★ TOP TRENDING",
                        color = accent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                // Frosted Lossless badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x35FFFFFF))
                        .border(1.dp, Glassmorphism.specularBorderBrush, RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "LOSSLESS",
                        color = SpotifyWhite,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp)
                ) {
                    Text(
                        text = song.title,
                        color = SpotifyWhite,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = song.artist,
                        color = Color(0xFFD0D0DC),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Over 12M+ monthly listeners",
                        color = SpotifyTextSecondary,
                        fontSize = 11.sp
                    )
                }

                CurvedNeonPlayButton(
                    isPlaying = isPlaying,
                    isBuffering = false,
                    onClick = onClick,
                    size = 52.dp
                )
            }
        }
    }
}

@Composable
private fun TrendingRankedTrackRow(
    rank: Int,
    song: Song,
    isPlaying: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = SpotifyGreen
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .shadow(if (isPlaying) 8.dp else 2.dp, RoundedCornerShape(16.dp), ambientColor = if (isPlaying) accent.copy(alpha = 0.25f) else Color.Black)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isPlaying) Color(0x28FFFFFF) else Color(0x14FFFFFF))
            .border(
                1.dp,
                if (isPlaying) Glassmorphism.accentGlassBorderBrush(accent) else Glassmorphism.specularBorderBrush,
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = String.format("%02d", rank),
            color = if (isPlaying) accent else SpotifyTextSecondary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(26.dp)
        )

        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF14141E))
                .border(1.dp, Glassmorphism.specularBorderBrush, RoundedCornerShape(12.dp))
        ) {
            if (!song.artworkUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = song.artworkUrl,
                    contentDescription = song.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = SpotifyTextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = if (isPlaying) accent else SpotifyWhite,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = song.artist,
                color = SpotifyTextSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (isPlaying) {
            EqualizerBars(color = accent, isPlaying = true)
        }

        IconButton(
            onClick = onFavoriteClick,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = "Favorite",
                tint = if (isFavorite) accent else Color(0x60FFFFFF),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun ModernPlaylistCard(
    playlist: Playlist,
    onClick: () -> Unit
) {
    val accent = SpotifyGreen
    Column(
        modifier = Modifier
            .width(148.dp)
            .shadow(16.dp, RoundedCornerShape(18.dp), ambientColor = Color.Black)
            .clip(RoundedCornerShape(18.dp))
            .background(Glassmorphism.darkGlassBrush)
            .border(1.2.dp, Glassmorphism.specularBorderBrush, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(124.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF1E1E2A))
                .border(1.dp, Glassmorphism.specularBorderBrush, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            val coverUrl = playlist.coverPath ?: playlist.songs.firstOrNull { it.artworkUrl != null }?.artworkUrl
            if (!coverUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = playlist.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(40.dp)
                )
            }

            // Glass orb play button
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(32.dp)
                    .shadow(8.dp, CircleShape, ambientColor = accent)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(SpotifyGreenBright, accent)
                        )
                    )
                    .border(0.8.dp, Color(0x60FFFFFF), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = SpotifyBlack,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = playlist.name,
            color = SpotifyWhite,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = "${playlist.songs.size} tracks",
            color = SpotifyTextSecondary,
            fontSize = 12.sp
        )
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
