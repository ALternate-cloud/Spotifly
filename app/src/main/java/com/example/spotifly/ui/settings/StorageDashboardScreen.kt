package com.example.spotifly.ui.settings

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.spotifly.data.offline.StorageBreakdown
import com.example.spotifly.data.offline.StorageInspector
import com.example.spotifly.data.offline.StoredTrackUsage
import com.example.spotifly.theme.Glassmorphism
import com.example.spotifly.theme.SpotifyBackground
import com.example.spotifly.theme.SpotifyError
import com.example.spotifly.theme.SpotifyGreen
import com.example.spotifly.theme.SpotifyGreenBright
import com.example.spotifly.theme.SpotifyTextSecondary
import com.example.spotifly.theme.SpotifyWhite

/**
 * Granular Storage & Cache Dashboard:
 * - Visual breakdown across downloaded songs, podcast episodes, artwork cache, and temp/export cache
 * - Device free space indicator via StatFs
 * - Single-tap track removal and category cache cleaners
 */
@Composable
fun StorageDashboardScreen(
    breakdown: StorageBreakdown,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onClearImageCache: () -> Unit,
    onClearTempCache: () -> Unit,
    onDeleteDownloadedTrack: (String) -> Unit,
    onClearAllDownloads: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var confirmClearAll by remember { mutableStateOf(false) }
    val accent = SpotifyGreen

    val musicColor = SpotifyGreenBright
    val podcastColor = Color(0xFF38BDF8)
    val imageColor = Color(0xFFF59E0B)
    val tempColor = Color(0xFFA78BFA)

    LaunchedEffect(Unit) {
        onRefresh()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SpotifyBackground)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = SpotifyWhite
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Storage & Cache",
                    color = SpotifyWhite,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Manage offline tracks & disk allocations",
                    color = SpotifyTextSecondary,
                    fontSize = 12.sp
                )
            }
            if (isLoading) {
                CircularProgressIndicator(
                    color = SpotifyGreenBright,
                    strokeWidth = 2.dp,
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .size(20.dp)
                )
            } else {
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh storage",
                        tint = SpotifyWhite
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Overview Card + Segmented Allocation Bar
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(16.dp, RoundedCornerShape(20.dp), ambientColor = Color.Black)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Glassmorphism.darkGlassBrush)
                        .border(1.2.dp, Glassmorphism.specularBorderBrush, RoundedCornerShape(20.dp))
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Glassmorphism.accentGlassBrush(accent, 0.22f))
                                    .border(1.dp, Glassmorphism.accentGlassBorderBrush(accent), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Storage,
                                    contentDescription = null,
                                    tint = SpotifyGreenBright,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = StorageInspector.formatBytes(breakdown.totalManagedBytes),
                                    color = SpotifyWhite,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "Total Spotifly media & cache",
                                    color = SpotifyTextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        if (breakdown.deviceFreeBytes > 0L) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Glassmorphism.subtleGlassFill)
                                    .border(0.8.dp, Glassmorphism.specularBorderBrush, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = "${StorageInspector.formatBytes(breakdown.deviceFreeBytes)} free",
                                    color = SpotifyWhite,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Segmented Bar
                    val denom = breakdown.totalManagedBytes.coerceAtLeast(1L).toFloat()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(CircleShape)
                            .background(Color(0x22FFFFFF)),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        val segments = listOf(
                            breakdown.musicBytes to musicColor,
                            breakdown.podcastBytes to podcastColor,
                            breakdown.imageCacheBytes to imageColor,
                            breakdown.tempCacheBytes to tempColor
                        ).filter { it.first > 0L }

                        if (segments.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize())
                        } else {
                            segments.forEach { (bytes, color) ->
                                val weight = (bytes / denom).coerceAtLeast(0.03f)
                                Box(
                                    modifier = Modifier
                                        .weight(weight)
                                        .fillMaxHeight()
                                        .clip(CircleShape)
                                        .background(color)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Legend grid
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LegendRow(
                            color = musicColor,
                            label = "Downloaded Music (${breakdown.downloadedMusicTracks.size})",
                            sizeText = StorageInspector.formatBytes(breakdown.musicBytes)
                        )
                        LegendRow(
                            color = podcastColor,
                            label = "Podcast Episodes (${breakdown.downloadedPodcastEpisodes.size})",
                            sizeText = StorageInspector.formatBytes(breakdown.podcastBytes)
                        )
                        LegendRow(
                            color = imageColor,
                            label = "Artwork Image Cache",
                            sizeText = StorageInspector.formatBytes(breakdown.imageCacheBytes)
                        )
                        LegendRow(
                            color = tempColor,
                            label = "Shared & Temp Buffers",
                            sizeText = StorageInspector.formatBytes(breakdown.tempCacheBytes)
                        )
                    }
                }
            }

            // Quick Cache Cleaners
            item {
                Text(
                    text = "Quick Cleanup",
                    color = SpotifyWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CleanupActionRow(
                        icon = Icons.Default.Image,
                        iconTint = imageColor,
                        title = "Clear Artwork Cache",
                        subtitle = "${StorageInspector.formatBytes(breakdown.imageCacheBytes)} cached thumbnails & covers",
                        actionLabel = "Clear",
                        enabled = breakdown.imageCacheBytes > 0L,
                        onClick = onClearImageCache
                    )
                    CleanupActionRow(
                        icon = Icons.Default.CleaningServices,
                        iconTint = tempColor,
                        title = "Clear Shared Cards & Temp Cache",
                        subtitle = "${StorageInspector.formatBytes(breakdown.tempCacheBytes)} story cards & playlist exports",
                        actionLabel = "Clear",
                        enabled = breakdown.tempCacheBytes > 0L,
                        onClick = onClearTempCache
                    )
                    CleanupActionRow(
                        icon = Icons.Default.DeleteSweep,
                        iconTint = SpotifyError,
                        title = "Remove All Offline Downloads",
                        subtitle = "${breakdown.downloadedMusicTracks.size + breakdown.downloadedPodcastEpisodes.size} files (${StorageInspector.formatBytes(breakdown.musicBytes + breakdown.podcastBytes)})",
                        actionLabel = "Clear All",
                        enabled = (breakdown.downloadedMusicTracks.isNotEmpty() || breakdown.downloadedPodcastEpisodes.isNotEmpty()),
                        isDestructive = true,
                        onClick = { confirmClearAll = true }
                    )
                }
            }

            // Per-Track Downloaded Songs
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Downloaded Songs",
                        color = SpotifyWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = StorageInspector.formatBytes(breakdown.musicBytes),
                        color = SpotifyGreenBright,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (breakdown.downloadedMusicTracks.isEmpty()) {
                item {
                    EmptyAllocationCard(
                        icon = Icons.Default.DownloadDone,
                        message = "No offline music tracks stored on device"
                    )
                }
            } else {
                items(
                    breakdown.downloadedMusicTracks,
                    key = { "dl_music_${it.song.id}" }
                ) { usage ->
                    StoredTrackRow(
                        usage = usage,
                        isPodcast = false,
                        onDelete = { onDeleteDownloadedTrack(usage.song.id) }
                    )
                }
            }

            if (breakdown.downloadedPodcastEpisodes.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Downloaded Podcast Episodes",
                            color = SpotifyWhite,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = StorageInspector.formatBytes(breakdown.podcastBytes),
                            color = podcastColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                items(
                    breakdown.downloadedPodcastEpisodes,
                    key = { "dl_pod_${it.song.id}" }
                ) { usage ->
                    StoredTrackRow(
                        usage = usage,
                        isPodcast = true,
                        onDelete = { onDeleteDownloadedTrack(usage.song.id) }
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Glassmorphism.subtleGlassFill)
                        .border(0.8.dp, Glassmorphism.specularBorderBrush, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderZip,
                        contentDescription = null,
                        tint = SpotifyTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Library metadata, playlists & vibe cache: ${StorageInspector.formatBytes(breakdown.appMetadataBytes)}",
                        color = SpotifyTextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            containerColor = Color(0xFF161622),
            title = { Text("Remove all offline downloads?", color = SpotifyWhite) },
            text = {
                Text(
                    "This will delete ${breakdown.downloadedMusicTracks.size + breakdown.downloadedPodcastEpisodes.size} downloaded audio files and free ${StorageInspector.formatBytes(breakdown.musicBytes + breakdown.podcastBytes)}.",
                    color = SpotifyTextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearAll = false
                    onClearAllDownloads()
                }) {
                    Text("Delete All", color = SpotifyError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAll = false }) {
                    Text("Cancel", color = SpotifyGreenBright)
                }
            }
        )
    }
}

@Composable
private fun LegendRow(color: Color, label: String, sizeText: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = label, color = SpotifyTextSecondary, fontSize = 12.sp)
        }
        Text(text = sizeText, color = SpotifyWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CleanupActionRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    actionLabel: String,
    enabled: Boolean,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Glassmorphism.darkGlassBrush)
            .border(1.dp, Glassmorphism.specularBorderBrush, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = SpotifyWhite, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, color = SpotifyTextSecondary, fontSize = 11.sp)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(
                    when {
                        !enabled -> SolidColor(Color(0x14FFFFFF))
                        isDestructive -> SolidColor(SpotifyError.copy(alpha = 0.22f))
                        else -> Glassmorphism.accentGlassBrush(SpotifyGreen, 0.22f)
                    }
                )
                .border(
                    0.8.dp,
                    when {
                        !enabled -> SolidColor(Color(0x20FFFFFF))
                        isDestructive -> SolidColor(SpotifyError.copy(alpha = 0.6f))
                        else -> Glassmorphism.accentGlassBorderBrush(SpotifyGreen)
                    },
                    RoundedCornerShape(14.dp)
                )
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = actionLabel,
                color = when {
                    !enabled -> SpotifyTextSecondary
                    isDestructive -> SpotifyError
                    else -> SpotifyGreenBright
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun EmptyAllocationCard(icon: ImageVector, message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Glassmorphism.subtleGlassFill)
            .border(0.8.dp, Glassmorphism.specularBorderBrush, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = SpotifyTextSecondary, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = message, color = SpotifyTextSecondary, fontSize = 13.sp)
    }
}

@Composable
private fun StoredTrackRow(
    usage: StoredTrackUsage,
    isPodcast: Boolean,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Glassmorphism.darkGlassBrush)
            .border(0.8.dp, Glassmorphism.specularBorderBrush, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1A1A26)),
            contentAlignment = Alignment.Center
        ) {
            if (!usage.song.artworkUrl.isNullOrBlank()) {
                AsyncImage(
                    model = usage.song.artworkUrl,
                    contentDescription = usage.song.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = if (isPodcast) Icons.Default.Podcasts else Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = SpotifyTextSecondary
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = usage.song.title,
                color = SpotifyWhite,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${usage.song.artist} • ${StorageInspector.formatBytes(usage.bytes)}",
                color = SpotifyTextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete offline file",
                tint = SpotifyTextSecondary,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}
