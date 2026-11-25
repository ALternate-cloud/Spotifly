package com.example.spotifly.ui.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.spotifly.data.catalog.LibrarySorting
import com.example.spotifly.data.catalog.PlaylistTrackSort
import com.example.spotifly.data.playlist.PlaylistBackupManager
import com.example.spotifly.model.PlaybackState
import com.example.spotifly.model.Playlist
import com.example.spotifly.model.Song
import com.example.spotifly.theme.SpotifyBackground
import com.example.spotifly.theme.SpotifyCard
import com.example.spotifly.theme.SpotifyElevated
import com.example.spotifly.theme.SpotifyGreen
import com.example.spotifly.theme.SpotifyTextSecondary
import com.example.spotifly.theme.SpotifyWhite
import com.example.spotifly.ui.components.TrackItem
import java.io.File
import com.example.spotifly.theme.SpotifyBlack
import com.example.spotifly.theme.SpotifyScrim

@Composable
fun PlaylistDetailView(
    playlist: Playlist,
    playbackState: PlaybackState,
    favorites: Set<String>,
    onBackClick: () -> Unit,
    onPlayQueue: (List<Song>, Int) -> Unit,
    onFavoriteClick: (Song) -> Unit,
    onRemoveSong: (songId: String) -> Unit,
    onDeletePlaylist: () -> Unit,
    modifier: Modifier = Modifier,
    onRenamePlaylist: (newName: String) -> Unit = {},
    onMoveSong: (from: Int, to: Int) -> Unit = { _, _ -> },
    onUpdateDescription: (String) -> Unit = {},
    onSharePlaylist: () -> Unit = {},
    onSetCover: (Uri) -> Unit = {},
    onRemoveCover: () -> Unit = {},
    onDownloadAll: () -> Unit = {},
    onSaveExportUri: (Uri, String, (Boolean) -> Unit) -> Unit = { _, _, _ -> },
    onShareExportFile: (filename: String, mimeType: String, content: String) -> Unit = { _, _, _ -> },
    hideExplicit: Boolean = false,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDescriptionDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var draftName by remember { mutableStateOf(playlist.name) }
    var draftDescription by remember { mutableStateOf(playlist.description) }
    var songToRemove by remember { mutableStateOf<Song?>(null) }
    var songMenuIndex by remember { mutableStateOf<Int?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var trackSort by remember { mutableStateOf(PlaylistTrackSort.CUSTOM) }
    var pendingExportText by remember { mutableStateOf<String?>(null) }
    var exportStatusMessage by remember { mutableStateOf<String?>(null) }

    val coverPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) onSetCover(uri) }

    val saveM3uLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("audio/x-mpegurl")
    ) { uri ->
        val content = pendingExportText
        pendingExportText = null
        if (uri != null && content != null) {
            onSaveExportUri(uri, content) { ok ->
                exportStatusMessage = if (ok) "Saved .m3u8 playlist" else "Couldn't save file"
            }
        }
    }

    val saveJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val content = pendingExportText
        pendingExportText = null
        if (uri != null && content != null) {
            onSaveExportUri(uri, content) { ok ->
                exportStatusMessage = if (ok) "Saved JSON backup" else "Couldn't save backup"
            }
        }
    }

    val visibleSongs = remember(playlist.songs, searchQuery, trackSort, hideExplicit) {
        LibrarySorting.playlistTracks(
            LibrarySorting.filterPlaylistTracks(
                playlist.songs.filterNot { hideExplicit && it.isExplicit },
                searchQuery
            ),
            trackSort
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SpotifyBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = SpotifyWhite
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { exportStatusMessage = null; showExportDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = "Export playlist",
                        tint = SpotifyTextSecondary
                    )
                }

                IconButton(onClick = onSharePlaylist) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share playlist",
                        tint = SpotifyTextSecondary
                    )
                }

                IconButton(onClick = { draftName = playlist.name; showRenameDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Rename Playlist",
                        tint = SpotifyTextSecondary
                    )
                }

                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Playlist",
                        tint = SpotifyTextSecondary
                    )
                }
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                ) {
                    Box {
                        Box(
                            modifier = Modifier
                                .size(160.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SpotifyCard),
                            contentAlignment = Alignment.Center
                        ) {
                            val cover = playlist.coverPath?.let { File(it) }
                            val fallbackArt = playlist.songs.firstOrNull()?.artworkUrl
                            when {
                                cover != null -> AsyncImage(
                                    model = cover,
                                    contentDescription = playlist.name,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                !fallbackArt.isNullOrEmpty() -> AsyncImage(
                                    model = fallbackArt,
                                    contentDescription = playlist.name,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                else -> Icon(
                                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                    contentDescription = null,
                                    tint = SpotifyGreen,
                                    modifier = Modifier.size(64.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = { coverPicker.launch("image/*") },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp)
                                .size(38.dp)
                                .background(SpotifyScrim.copy(alpha = 0.7f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = "Change cover",
                                tint = SpotifyWhite,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }

                    if (playlist.coverPath != null) {
                        TextButton(onClick = onRemoveCover) {
                            Text(text = "Remove cover", color = SpotifyTextSecondary, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = playlist.name,
                        color = SpotifyWhite,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "${playlist.songs.size} tracks",
                        color = SpotifyTextSecondary,
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                draftDescription = playlist.description
                                showDescriptionDialog = true
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = playlist.description.ifBlank { "Add a description" },
                            color = if (playlist.description.isBlank()) SpotifyGreen else SpotifyTextSecondary,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit description",
                            tint = if (playlist.description.isBlank()) SpotifyGreen else SpotifyTextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (playlist.songs.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = { onPlayQueue(visibleSongs, 0) },
                                colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                                shape = CircleShape,
                                modifier = Modifier.height(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play All",
                                    tint = SpotifyBlack,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Play All",
                                    color = SpotifyBlack,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            IconButton(
                                onClick = onDownloadAll,
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(SpotifyCard, CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Download playlist",
                                    tint = SpotifyWhite
                                )
                            }
                        }
                    }
                }
            }

            if (playlist.songs.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    text = "Search in playlist",
                                    color = SpotifyTextSecondary,
                                    fontSize = 13.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = SpotifyTextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = SpotifyCard,
                                unfocusedContainerColor = SpotifyCard,
                                focusedBorderColor = SpotifyGreen,
                                unfocusedBorderColor = Color.Transparent,
                                focusedTextColor = SpotifyWhite,
                                unfocusedTextColor = SpotifyWhite
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { trackSort = trackSort.next() }
                                .padding(horizontal = 6.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = "Sort tracks",
                                tint = SpotifyTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = trackSort.label,
                                color = SpotifyWhite,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            if (playlist.songs.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "This playlist is empty",
                                color = SpotifyWhite,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Search or browse songs and tap 'Add to Playlist'",
                                color = SpotifyTextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            } else if (visibleSongs.isEmpty()) {
                item {
                    EmptyLibraryMessage(text = "No tracks match \"$searchQuery\"")
                }
            } else {
                itemsIndexed(visibleSongs, key = { _, song -> song.id }) { index, song ->
                    TrackItem(
                        song = song,
                        isPlaying = playbackState.currentSong?.id == song.id,
                        isFavorite = favorites.contains(song.id),
                        onClick = { onPlayQueue(visibleSongs, index) },
                        onFavoriteClick = { onFavoriteClick(song) },
                        onMoreClick = { songMenuIndex = index }
                    )
                }
            }
        }
    }

    // Per-track menu: reorder or remove. Reordering is only meaningful in the
    // user's own order, so the arrows hide once the list is sorted.
    songMenuIndex?.let { index ->
        val song = visibleSongs.getOrNull(index)
        val originalIndex = song?.let { item ->
            playlist.songs.indexOfFirst { it.id == item.id }
        } ?: -1
        val canReorder = trackSort == PlaylistTrackSort.CUSTOM && originalIndex >= 0
        AlertDialog(
            onDismissRequest = { songMenuIndex = null },
            title = { Text(text = song?.title ?: "Track", color = SpotifyWhite) },
            text = {
                Column {
                    if (canReorder) {
                        TextButton(
                            onClick = {
                                onMoveSong(originalIndex, originalIndex - 1)
                                songMenuIndex = null
                            },
                            enabled = originalIndex > 0,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = null,
                                tint = if (originalIndex > 0) SpotifyWhite else SpotifyTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Move up",
                                color = if (originalIndex > 0) SpotifyWhite else SpotifyTextSecondary
                            )
                        }
                        TextButton(
                            onClick = {
                                onMoveSong(originalIndex, originalIndex + 1)
                                songMenuIndex = null
                            },
                            enabled = originalIndex < playlist.songs.lastIndex,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowDownward,
                                contentDescription = null,
                                tint = if (originalIndex < playlist.songs.lastIndex) SpotifyWhite else SpotifyTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Move down",
                                color = if (originalIndex < playlist.songs.lastIndex) SpotifyWhite else SpotifyTextSecondary
                            )
                        }
                    }
                    TextButton(
                        onClick = {
                            songToRemove = song
                            songMenuIndex = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = Color(0xFFE53935),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(text = "Remove from playlist", color = Color(0xFFE53935))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { songMenuIndex = null }) {
                    Text(text = "Close", color = SpotifyTextSecondary)
                }
            },
            containerColor = SpotifyElevated
        )
    }

    songToRemove?.let { song ->
        AlertDialog(
            onDismissRequest = { songToRemove = null },
            title = { Text(text = "Remove from playlist?", color = SpotifyWhite) },
            text = {
                Text(
                    text = "'${song.title}' will be removed from '${playlist.name}'.",
                    color = SpotifyTextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRemoveSong(song.id)
                        songToRemove = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen)
                ) {
                    Text(text = "Remove", color = SpotifyBlack, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { songToRemove = null }) {
                    Text(text = "Cancel", color = SpotifyTextSecondary)
                }
            },
            containerColor = SpotifyElevated
        )
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text(text = "Rename Playlist", color = SpotifyWhite) },
            text = {
                OutlinedTextField(
                    value = draftName,
                    onValueChange = { draftName = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = SpotifyWhite,
                        unfocusedTextColor = SpotifyWhite,
                        focusedBorderColor = SpotifyGreen,
                        unfocusedBorderColor = SpotifyCard,
                        focusedContainerColor = SpotifyCard,
                        unfocusedContainerColor = SpotifyCard
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = draftName.trim()
                        if (name.isNotEmpty()) onRenamePlaylist(name)
                        showRenameDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen)
                ) {
                    Text(text = "Save", color = SpotifyBlack, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text(text = "Cancel", color = SpotifyTextSecondary)
                }
            },
            containerColor = SpotifyElevated
        )
    }

    if (showDescriptionDialog) {
        AlertDialog(
            onDismissRequest = { showDescriptionDialog = false },
            title = { Text(text = "Playlist description", color = SpotifyWhite) },
            text = {
                OutlinedTextField(
                    value = draftDescription,
                    onValueChange = { draftDescription = it },
                    placeholder = { Text("What is this playlist about?", color = SpotifyTextSecondary) },
                    minLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = SpotifyWhite,
                        unfocusedTextColor = SpotifyWhite,
                        focusedBorderColor = SpotifyGreen,
                        unfocusedBorderColor = SpotifyCard,
                        focusedContainerColor = SpotifyCard,
                        unfocusedContainerColor = SpotifyCard
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateDescription(draftDescription)
                        showDescriptionDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen)
                ) {
                    Text(text = "Save", color = SpotifyBlack, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDescriptionDialog = false }) {
                    Text(text = "Cancel", color = SpotifyTextSecondary)
                }
            },
            containerColor = SpotifyElevated
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(text = "Delete Playlist?", color = SpotifyWhite) },
            text = {
                Text(
                    text = "Are you sure you want to delete '${playlist.name}'? This action cannot be undone.",
                    color = SpotifyTextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeletePlaylist()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                ) {
                    Text(text = "Delete", color = SpotifyWhite, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(text = "Cancel", color = SpotifyTextSecondary)
                }
            },
            containerColor = SpotifyElevated
        )
    }

    if (showExportDialog) {
        val safeBase = playlist.name.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "playlist" }
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            containerColor = SpotifyElevated,
            title = { Text(text = "Export '${playlist.name}'", color = SpotifyWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Choose a portable format (.m3u8 for external players, .json for full lossless Spotifly backup).",
                        color = SpotifyTextSecondary,
                        fontSize = 13.sp
                    )
                    if (exportStatusMessage != null) {
                        Text(text = exportStatusMessage!!, color = SpotifyGreen, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    TextButton(onClick = {
                        pendingExportText = PlaylistBackupManager.exportM3u(playlist)
                        saveM3uLauncher.launch("$safeBase.m3u8")
                    }) {
                        Text("Save as .m3u8 Playlist", color = SpotifyWhite)
                    }
                    TextButton(onClick = {
                        pendingExportText = PlaylistBackupManager.exportJson(listOf(playlist))
                        saveJsonLauncher.launch("$safeBase.json")
                    }) {
                        Text("Save as .json Backup", color = SpotifyWhite)
                    }
                    TextButton(onClick = {
                        showExportDialog = false
                        onShareExportFile("$safeBase.m3u8", "audio/x-mpegurl", PlaylistBackupManager.exportM3u(playlist))
                    }) {
                        Text("Share .m3u8 File…", color = SpotifyGreen)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Done", color = SpotifyGreen)
                }
            }
        )
    }
}
