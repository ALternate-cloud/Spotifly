package com.example.spotifly.ui.stats

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.spotifly.data.stats.ArtistStat
import com.example.spotifly.data.stats.ListeningStats
import com.example.spotifly.data.stats.TrackStat
import com.example.spotifly.theme.SpotifyBackground
import com.example.spotifly.theme.SpotifyCard
import com.example.spotifly.theme.SpotifyGreen
import com.example.spotifly.theme.SpotifyTextSecondary
import com.example.spotifly.theme.SpotifyWhite
import com.example.spotifly.ui.components.ShareUtils

/**
 * The Wrapped-shaped view: what this device has actually played. Numbers come from
 * the local play log, so nothing is sent anywhere and nothing is invented.
 */
@Composable
fun StatsScreen(
    stats: ListeningStats,
    onBack: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    onTrackClick: (TrackStat) -> Unit = {},
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(SpotifyBackground),
        contentPadding = PaddingValues(bottom = 140.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = SpotifyWhite
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { ShareUtils.shareWrapped(context, stats) }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share your listening",
                            tint = SpotifyWhite,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    TextButton(onClick = onClear) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = SpotifyTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reset", color = SpotifyTextSecondary, fontSize = 13.sp)
                    }
                }
            }
        }

        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = "Your listening",
                    color = SpotifyWhite,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Built from the tracks this device has played. Nothing leaves the phone.",
                    color = SpotifyTextSecondary,
                    fontSize = 13.sp
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(18.dp))
            WrappedCard(stats = stats, onShare = { ShareUtils.shareWrapped(context, stats) })
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatTile("Minutes", stats.totalMinutes.toString(), Modifier.weight(1f))
                StatTile("Plays", stats.totalPlays.toString(), Modifier.weight(1f))
            }
        }

        item {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatTile("Artists", stats.uniqueArtists.toString(), Modifier.weight(1f))
                StatTile("This week", "${stats.minutesLast7Days} min", Modifier.weight(1f))
            }
        }

        if (stats.isEmpty) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Play something and your stats start here.",
                        color = SpotifyTextSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        }

        if (stats.topTracks.isNotEmpty()) {
            item { SectionTitle("Top tracks") }
            items(stats.topTracks, key = { "track_${it.songId}" }) { track ->
                TopTrackRow(track = track, onClick = { onTrackClick(track) })
            }
        }

        if (stats.topArtists.isNotEmpty()) {
            item { SectionTitle("Top artists") }
            items(stats.topArtists, key = { "artist_${it.name}" }) { artist ->
                TopArtistRow(artist)
            }
        }
    }
}

@Composable
private fun WrappedCard(stats: ListeningStats, onShare: () -> Unit) {
    val topArtist = stats.topArtists.firstOrNull()
    val topTrack = stats.topTracks.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF145C3A), Color(0xFF08251A))
                )
            )
            .padding(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "YOUR LISTENING, WRAPPED",
                color = SpotifyWhite.copy(alpha = 0.75f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            IconButton(onClick = onShare, modifier = Modifier.size(30.dp)) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share your wrapped card",
                    tint = SpotifyWhite,
                    modifier = Modifier.size(17.dp)
                )
            }
        }

        Text(
            text = stats.totalMinutes.toString(),
            color = SpotifyGreen,
            fontSize = 44.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "minutes listened",
            color = SpotifyWhite,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "${stats.totalPlays} plays · ${stats.uniqueArtists} artists · ${stats.minutesLast7Days} min this week",
            color = SpotifyWhite.copy(alpha = 0.85f),
            fontSize = 12.sp
        )

        topArtist?.let {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Top artist  ${it.name}",
                color = SpotifyWhite,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        topTrack?.let {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Top track  ${it.title} — ${it.artist}",
                color = SpotifyWhite.copy(alpha = 0.85f),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(SpotifyCard)
            .padding(14.dp)
    ) {
        Text(
            text = value,
            color = SpotifyGreen,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
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
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
    )
}

@Composable
private fun TopTrackRow(track: TrackStat, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Artwork(track.artworkUrl)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = SpotifyWhite,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                color = SpotifyTextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = "${track.plays}x",
            color = SpotifyGreen,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun TopArtistRow(artist: ArtistStat) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(SpotifyCard),
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
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = SpotifyTextSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = artist.name,
            color = SpotifyWhite,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${artist.plays}x",
            color = SpotifyGreen,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun Artwork(url: String?) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(SpotifyCard),
        contentAlignment = Alignment.Center
    ) {
        if (!url.isNullOrEmpty()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = SpotifyTextSecondary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
