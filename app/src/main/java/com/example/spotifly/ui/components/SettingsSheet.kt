package com.example.spotifly.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DataSaverOn
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spotifly.theme.AccentTheme
import com.example.spotifly.theme.AccentThemes
import com.example.spotifly.theme.SpotifyCard
import com.example.spotifly.theme.SpotifyDarkGray
import com.example.spotifly.theme.SpotifyGreen
import com.example.spotifly.theme.SpotifyOnAccent
import com.example.spotifly.theme.SpotifyTextSecondary
import com.example.spotifly.theme.SpotifyWhite
import com.example.spotifly.model.StreamQuality
import com.example.spotifly.model.ThemeMode
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    currentAccent: AccentTheme,
    autoplayEnabled: Boolean,
    downloadedCount: Int,
    downloadedBytes: Long,
    onAccentSelected: (AccentTheme) -> Unit,
    onAutoplayChanged: (Boolean) -> Unit,
    onClearSearchHistory: () -> Unit,
    onClearRecentlyPlayed: () -> Unit,
    onDismiss: () -> Unit,
    playbackSpeed: Float = 1f,
    onSpeedSelected: (Float) -> Unit = {},
    onOpenAudioSettings: () -> Unit = {},
    onOpenStats: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    themeMode: ThemeMode = ThemeMode.DARK,
    onThemeModeSelected: (ThemeMode) -> Unit = {},
    hideExplicit: Boolean = false,
    onHideExplicitChanged: (Boolean) -> Unit = {},
    streamQuality: StreamQuality = StreamQuality.AUTO,
    onStreamQualitySelected: (StreamQuality) -> Unit = {},
    dataSaver: Boolean = false,
    onDataSaverChanged: (Boolean) -> Unit = {},
    offlineMode: Boolean = false,
    onOfflineModeChanged: (Boolean) -> Unit = {},
    cacheBytes: Long = 0L,
    onClearCache: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp)
    ) {
        // Drag handle
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(SpotifyCard)
                .align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Settings",
            color = SpotifyWhite,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Accent picker
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Palette,
                contentDescription = null,
                tint = SpotifyGreen,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Accent color",
                    color = SpotifyWhite,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = currentAccent.name,
                    color = SpotifyTextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(start = 34.dp)
        ) {
            AccentThemes.forEach { accent ->
                val isSelected = accent.name == currentAccent.name
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(accent.primary)
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) SpotifyWhite else SpotifyCard,
                            shape = CircleShape
                        )
                        .clickable { onAccentSelected(accent) },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Theme mode
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.DarkMode,
                contentDescription = null,
                tint = SpotifyGreen,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Theme",
                    color = SpotifyWhite,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = themeMode.label,
                    color = SpotifyTextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 34.dp)
        ) {
            ThemeMode.entries.forEach { mode ->
                OptionPill(
                    label = mode.label,
                    selected = mode == themeMode,
                    onClick = { onThemeModeSelected(mode) }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Autoplay toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.AllInclusive,
                contentDescription = null,
                tint = SpotifyGreen,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Autoplay similar songs",
                    color = SpotifyWhite,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Matches mood, energy and language, and queues the next track before this one ends",
                    color = SpotifyTextSecondary,
                    fontSize = 12.sp
                )
            }
            Switch(
                checked = autoplayEnabled,
                onCheckedChange = onAutoplayChanged,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Black,
                    checkedTrackColor = SpotifyGreen,
                    uncheckedThumbColor = SpotifyTextSecondary,
                    uncheckedTrackColor = SpotifyCard
                )
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        SettingsToggleRow(
            icon = Icons.Default.VisibilityOff,
            title = "Hide explicit content",
            subtitle = "Filter explicit tracks out of the library and search",
            checked = hideExplicit,
            onCheckedChange = onHideExplicitChanged
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Playback speed
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Speed,
                contentDescription = null,
                tint = SpotifyGreen,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Playback speed",
                color = SpotifyWhite,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 34.dp)
        ) {
            SPEED_OPTIONS.forEach { (speed, label) ->
                val isSelected = kotlin.math.abs(speed - playbackSpeed) < 0.01f
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (isSelected) SpotifyGreen else SpotifyCard)
                        .clickable { onSpeedSelected(speed) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) SpotifyOnAccent else SpotifyTextSecondary,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "Audio & listening",
            color = SpotifyTextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        SettingsRow(
            icon = Icons.Default.GraphicEq,
            title = "Playback & equalizer",
            subtitle = "Crossfade, skip silence, volume normalisation, 5-band EQ"
        ) {
            onOpenAudioSettings()
        }

        SettingsRow(
            icon = Icons.Default.BarChart,
            title = "Your listening",
            subtitle = "Top tracks, top artists and minutes played on this device"
        ) {
            onOpenStats()
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "Data",
            color = SpotifyTextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        SettingsToggleRow(
            icon = Icons.Default.WifiOff,
            title = "Offline mode",
            subtitle = "Use only downloaded music and skip network requests",
            checked = offlineMode,
            onCheckedChange = onOfflineModeChanged
        )

        SettingsToggleRow(
            icon = Icons.Default.DataSaverOn,
            title = "Data saver",
            subtitle = "Prefer smaller artwork and lower-quality streams",
            checked = dataSaver,
            onCheckedChange = onDataSaverChanged
        )

        SettingsValueRow(
            icon = Icons.Default.HighQuality,
            title = "Streaming quality",
            subtitle = streamQuality.description,
            value = streamQuality.label,
            onClick = { onStreamQualitySelected(streamQuality.next()) }
        )

        Spacer(modifier = Modifier.height(8.dp))

        SettingsRow(
            icon = Icons.Default.DeleteSweep,
            title = "Clear search history",
            subtitle = "Remove all recent searches from this device"
        ) {
            onClearSearchHistory()
            onDismiss()
        }

        SettingsRow(
            icon = Icons.Default.History,
            title = "Clear recently played",
            subtitle = "Reset your listening history"
        ) {
            onClearRecentlyPlayed()
            onDismiss()
        }

        // Storage readout
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(SpotifyDarkGray)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.DownloadDone,
                contentDescription = null,
                tint = SpotifyGreen,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Offline storage",
                    color = SpotifyWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "$downloadedCount downloaded track${if (downloadedCount == 1) "" else "s"} • ${formatBytes(downloadedBytes)}",
                    color = SpotifyTextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        SettingsRow(
            icon = Icons.Default.CleaningServices,
            title = "Clear image cache",
            subtitle = "${formatBytes(cacheBytes)} used by artwork thumbnails"
        ) {
            onClearCache()
        }

        Spacer(modifier = Modifier.height(18.dp))

        // About
        SettingsRow(
            icon = Icons.Default.Info,
            title = "About & legal",
            subtitle = "Audio sources, local data, clean-room notes"
        ) {
            onOpenAbout()
        }
    }
}

@Composable
private fun OptionPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) SpotifyGreen else SpotifyCard)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) SpotifyOnAccent else SpotifyTextSecondary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = SpotifyGreen,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = SpotifyWhite,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                color = SpotifyTextSecondary,
                fontSize = 12.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = SpotifyOnAccent,
                checkedTrackColor = SpotifyGreen,
                uncheckedThumbColor = SpotifyTextSecondary,
                uncheckedTrackColor = SpotifyCard
            )
        )
    }
}

@Composable
private fun SettingsValueRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = SpotifyGreen,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = SpotifyWhite,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                color = SpotifyTextSecondary,
                fontSize = 12.sp
            )
        }
        Text(
            text = value,
            color = SpotifyGreen,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = SpotifyGreen,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = SpotifyWhite,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                color = SpotifyTextSecondary,
                fontSize = 12.sp
            )
        }
    }
}

private val SPEED_OPTIONS = listOf(
    0.75f to "0.75x",
    1f to "1x",
    1.25f to "1.25x",
    1.5f to "1.5x",
    2f to "2x"
)

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) {
        String.format(Locale.US, "%.1f GB", mb / 1024)
    } else {
        String.format(Locale.US, "%.0f MB", mb)
    }
}
