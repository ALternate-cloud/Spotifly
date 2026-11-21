package com.example.spotifly.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.spotifly.model.Playlist
import com.example.spotifly.theme.SpotifyCard
import com.example.spotifly.theme.SpotifyGreen
import com.example.spotifly.theme.SpotifyTextSecondary
import com.example.spotifly.theme.SpotifyWhite
import java.io.File

/** Cover first, then the first track's artwork, then the generic playlist glyph. */
@Composable
fun PlaylistArtwork(
    playlist: Playlist,
    size: Dp = 54.dp,
    corner: Dp = 6.dp
) {
    val model: Any? = playlist.coverPath?.let { File(it) }
        ?: playlist.songs.firstOrNull()?.artworkUrl
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(SpotifyCard),
        contentAlignment = Alignment.Center
    ) {
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = playlist.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                contentDescription = null,
                tint = SpotifyGreen,
                modifier = Modifier.size(size * 0.5f)
            )
        }
    }
}

@Composable
fun LibraryEntityRow(
    title: String,
    subtitle: String,
    artworkUrl: Any?,
    isCircle: Boolean,
    fallback: @Composable (Color) -> Unit,
    onClick: () -> Unit,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ArtworkBubble(
            artworkUrl = artworkUrl,
            title = title,
            isCircle = isCircle,
            size = 54.dp,
            modifier = Modifier
        ) {
            fallback(SpotifyTextSecondary)
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = SpotifyWhite,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = SpotifyTextSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        trailing()
    }
}

@Composable
fun LibraryEntityCard(
    title: String,
    subtitle: String,
    artworkUrl: Any?,
    isCircle: Boolean,
    fallback: @Composable (Color) -> Unit,
    onClick: () -> Unit,
    overlay: @Composable () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Box {
            ArtworkBubble(
                artworkUrl = artworkUrl,
                title = title,
                isCircle = isCircle,
                size = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            ) {
                fallback(SpotifyTextSecondary)
            }
            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                overlay()
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            color = SpotifyWhite,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            color = SpotifyTextSecondary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ArtworkBubble(
    artworkUrl: Any?,
    title: String,
    isCircle: Boolean,
    size: Dp,
    modifier: Modifier,
    fallback: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .then(if (size > 0.dp) Modifier.size(size) else Modifier)
            .clip(if (isCircle) CircleShape else RoundedCornerShape(6.dp))
            .background(SpotifyCard),
        contentAlignment = Alignment.Center
    ) {
        if (artworkUrl != null && artworkUrl.toString().isNotEmpty()) {
            AsyncImage(
                model = artworkUrl,
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            fallback()
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text = text,
        color = SpotifyWhite,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 10.dp, bottom = 8.dp)
    )
}

@Composable
fun TrackCountLabel(text: String) {
    Text(
        text = text,
        color = SpotifyTextSecondary,
        fontSize = 13.sp,
        modifier = Modifier.padding(bottom = 10.dp)
    )
}

@Composable
fun EmptyLibraryMessage(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = SpotifyTextSecondary, fontSize = 14.sp)
    }
}

@Composable
fun EmptyLibraryState(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 120.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            icon()
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = title,
                color = SpotifyWhite,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                color = SpotifyTextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
    }
}
