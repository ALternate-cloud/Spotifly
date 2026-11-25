package com.example.spotifly.ui.library

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.spotifly.data.catalog.LibraryGrouping
import com.example.spotifly.data.catalog.LibrarySort
import com.example.spotifly.data.catalog.LibrarySorting
import com.example.spotifly.data.catalog.PlaylistListSort
import com.example.spotifly.model.Album
import com.example.spotifly.model.Artist
import com.example.spotifly.model.DownloadStatus
import com.example.spotifly.model.LibraryLayout
import com.example.spotifly.model.PlaybackState
import com.example.spotifly.model.Playlist
import com.example.spotifly.model.Song
import com.example.spotifly.theme.SpotifyBackground
import com.example.spotifly.theme.SpotifyCard
import com.example.spotifly.theme.SpotifyElevated
import com.example.spotifly.theme.SpotifyGreen
import com.example.spotifly.theme.SpotifyTextSecondary
import com.example.spotifly.theme.SpotifyWhite
import com.example.spotifly.theme.SpotifyGreenBright
import com.example.spotifly.theme.Glassmorphism
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.shadow
import com.example.spotifly.ui.components.TrackItem
import java.io.File
import com.example.spotifly.theme.SpotifyBlack
import com.example.spotifly.theme.SpotifyScrim

private val libraryTabs = listOf(
    "Playlists",
    "Liked Songs",
    "Albums",
    "Artists",
    "Downloaded",
    "Device"
)

@Composable
fun LibraryScreen(
    playbackState: PlaybackState,
    favorites: Set<String>,
    allSongs: List<Song>,
    localSongs: List<Song>,
    modifier: Modifier = Modifier,
    favoriteSongs: List<Song> = emptyList(),
    downloadedSongs: List<Song> = emptyList(),
    downloadStatus: Map<String, DownloadStatus> = emptyMap(),
    playlists: List<Playlist> = emptyList(),
    albums: List<Album> = emptyList(),
    artists: List<Artist> = emptyList(),
    savedAlbums: List<Album> = emptyList(),
    followedArtists: List<Artist> = emptyList(),
    pinnedPlaylistIds: List<String> = emptyList(),
    libraryLayout: LibraryLayout = LibraryLayout.LIST,
    hideExplicit: Boolean = false,
    onScanLocalSongs: () -> Unit,
    onSongClick: (Song) -> Unit,
    onPlayQueue: (List<Song>, Int) -> Unit = { _, _ -> },
    onFavoriteClick: (Song) -> Unit,
    onTrackMoreClick: ((Song) -> Unit)? = null,
    onCreatePlaylist: (String) -> Unit = {},
    onDeletePlaylist: (String) -> Unit = {},
    onRemoveSongFromPlaylist: (playlistId: String, songId: String) -> Unit = { _, _ -> },
    onRenamePlaylist: (playlistId: String, newName: String) -> Unit = { _, _ -> },
    onMoveSong: (playlistId: String, from: Int, to: Int) -> Unit = { _, _, _ -> },
    onUpdateDescription: (playlistId: String, description: String) -> Unit = { _, _ -> },
    onOpenAlbum: (Album) -> Unit = {},
    onOpenArtist: (Artist) -> Unit = {},
    onSharePlaylist: (Playlist) -> Unit = {},
    onToggleSavedAlbum: (Album) -> Unit = {},
    onToggleFollowedArtist: (Artist) -> Unit = {},
    onTogglePinPlaylist: (String) -> Unit = {},
    onToggleLayout: () -> Unit = {},
    onSetPlaylistCover: (playlistId: String, uri: Uri) -> Unit = { _, _ -> },
    onRemovePlaylistCover: (String) -> Unit = {},
    onDownloadPlaylist: (Playlist) -> Unit = {},
    onOpenStorageDashboard: () -> Unit = {},
    onImportPlaylistsUri: (Uri, (String) -> Unit) -> Unit = { _, _ -> },
    onImportPlaylistsText: (String, String, (String) -> Unit) -> Unit = { _, _, _ -> },
    onBackupAllPlaylistsUri: (Uri, (Boolean) -> Unit) -> Unit = { _, _ -> },
    onSavePlaylistExportUri: (Uri, String, (Boolean) -> Unit) -> Unit = { _, _, _ -> },
    onSharePlaylistExportFile: (filename: String, mimeType: String, content: String) -> Unit = { _, _, _ -> },
    openPlaylistId: String? = null,
    onPlaylistDeepLinkConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf("Playlists") }
    var selectedPlaylistId by remember { mutableStateOf<String?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showImportPlaylistDialog by remember { mutableStateOf(false) }
    var importNameInput by remember { mutableStateOf("") }
    var importTextInput by remember { mutableStateOf("") }
    var newPlaylistName by remember { mutableStateOf("") }
    var importBannerMessage by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var librarySort by remember { mutableStateOf(LibrarySort.RECENT) }
    var playlistSort by remember { mutableStateOf(PlaylistListSort.RECENT) }

    val importFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportPlaylistsUri(uri) { msg -> importBannerMessage = msg }
        }
    }

    val backupAllPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            onBackupAllPlaylistsUri(uri) { ok ->
                importBannerMessage = if (ok) "Saved library playlists backup (.json)" else "Couldn't write backup"
            }
        }
    }

    // A spotifly://playlist link selects the playlist once, then releases the
    // request so backing out of the detail does not reopen it.
    LaunchedEffect(openPlaylistId) {
        val id = openPlaylistId ?: return@LaunchedEffect
        if (playlists.any { it.id == id }) {
            selectedPlaylistId = id
        }
        onPlaylistDeepLinkConsumed()
    }

    val activePlaylist = playlists.find { it.id == selectedPlaylistId }

    if (activePlaylist != null) {
        PlaylistDetailView(
            playlist = activePlaylist,
            playbackState = playbackState,
            favorites = favorites,
            hideExplicit = hideExplicit,
            onBackClick = { selectedPlaylistId = null },
            onPlayQueue = onPlayQueue,
            onFavoriteClick = onFavoriteClick,
            onRemoveSong = { songId -> onRemoveSongFromPlaylist(activePlaylist.id, songId) },
            onRenamePlaylist = { newName -> onRenamePlaylist(activePlaylist.id, newName) },
            onDeletePlaylist = {
                onDeletePlaylist(activePlaylist.id)
                selectedPlaylistId = null
            },
            onMoveSong = { from, to -> onMoveSong(activePlaylist.id, from, to) },
            onUpdateDescription = { description ->
                onUpdateDescription(activePlaylist.id, description)
            },
            onSharePlaylist = { onSharePlaylist(activePlaylist) },
            onSetCover = { uri -> onSetPlaylistCover(activePlaylist.id, uri) },
            onRemoveCover = { onRemovePlaylistCover(activePlaylist.id) },
            onDownloadAll = { onDownloadPlaylist(activePlaylist) },
            onSaveExportUri = onSavePlaylistExportUri,
            onShareExportFile = onSharePlaylistExportFile,
            modifier = modifier
        )
        return
    }

    val permissionToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, permissionToRequest) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (isGranted) {
            onScanLocalSongs()
        }
    }

    fun visibleSongs(songs: List<Song>): List<Song> = songs
        .filterNot { hideExplicit && it.isExplicit }
        .filter { LibraryGrouping.matches(it, searchQuery) }

    val visiblePlaylists = remember(playlists, searchQuery, playlistSort, pinnedPlaylistIds) {
        val filtered = playlists.filter { LibrarySorting.matchesPlaylist(it, searchQuery) }
        LibrarySorting.playlists(filtered, playlistSort)
    }

    val visibleAlbums = remember(albums, searchQuery, librarySort) {
        val filtered = albums.filter {
            searchQuery.isBlank() ||
                it.title.contains(searchQuery.trim(), ignoreCase = true) ||
                it.artist.contains(searchQuery.trim(), ignoreCase = true)
        }
        LibrarySorting.albums(filtered, librarySort)
    }

    val visibleArtists = remember(artists, searchQuery, librarySort) {
        val filtered = artists.filter {
            searchQuery.isBlank() || it.name.contains(searchQuery.trim(), ignoreCase = true)
        }
        LibrarySorting.artists(filtered, librarySort)
    }

    // Saved/followed items are looked up in the live catalogue first so new tracks
    // show up, then fall back to the stored copy when the catalogue is offline.
    val savedAlbumRows = remember(savedAlbums, visibleAlbums) {
        val byId = visibleAlbums.associateBy { it.id }
        savedAlbums.map { byId[it.id] ?: it }
            .filter { searchQuery.isBlank() || it.title.contains(searchQuery.trim(), true) }
    }
    val followedArtistRows = remember(followedArtists, visibleArtists) {
        val byId = visibleArtists.associateBy { it.id }
        followedArtists.map { byId[it.id] ?: it }
            .filter { searchQuery.isBlank() || it.name.contains(searchQuery.trim(), true) }
    }
    val savedAlbumIds = savedAlbums.map { it.id }.toSet()
    val followedArtistIds = followedArtists.map { it.id }.toSet()
    val otherAlbums = visibleAlbums.filterNot { it.id in savedAlbumIds }
    val otherArtists = visibleArtists.filterNot { it.id in followedArtistIds }

    val pinnedPlaylists = pinnedPlaylistIds.mapNotNull { id -> visiblePlaylists.find { it.id == id } }
    val unpinnedPlaylists = visiblePlaylists.filterNot { it.id in pinnedPlaylistIds }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SpotifyBackground)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Your Library",
                color = SpotifyWhite,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Frosted Glass Layout Toggle Button
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0x24FFFFFF))
                        .border(1.dp, Glassmorphism.specularBorderBrush, CircleShape)
                        .clickable(onClick = onToggleLayout),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (libraryLayout == LibraryLayout.GRID) {
                            Icons.AutoMirrored.Filled.ViewList
                        } else {
                            Icons.Default.GridView
                        },
                        contentDescription = if (libraryLayout == LibraryLayout.GRID) {
                            "Show as list"
                        } else {
                            "Show as grid"
                        },
                        tint = SpotifyWhite,
                        modifier = Modifier.size(19.dp)
                    )
                }
                when (selectedTab) {
                    "Device" -> Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0x24FFFFFF))
                            .border(1.dp, Glassmorphism.specularBorderBrush, CircleShape)
                            .clickable {
                                if (hasPermission) onScanLocalSongs()
                                else permissionLauncher.launch(permissionToRequest)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Scan", tint = SpotifyWhite, modifier = Modifier.size(19.dp))
                    }
                    "Playlists" -> Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0x24FFFFFF))
                            .border(1.dp, Glassmorphism.specularBorderBrush, CircleShape)
                            .clickable { showCreatePlaylistDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "New Playlist", tint = SpotifyWhite, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Frosted Glass Library Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            libraryTabs.forEach { tab ->
                val isSelected = selectedTab == tab
                Box(
                    modifier = Modifier
                        .shadow(if (isSelected) 8.dp else 2.dp, RoundedCornerShape(20.dp), ambientColor = if (isSelected) SpotifyGreen else Color.Transparent)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSelected) Glassmorphism.accentGlassBrush(SpotifyGreen) else Glassmorphism.subtleGlassFill)
                        .border(
                            1.2.dp,
                            if (isSelected) Glassmorphism.accentGlassBorderBrush(SpotifyGreen) else Glassmorphism.specularBorderBrush,
                            RoundedCornerShape(20.dp)
                        )
                        .clickable { selectedTab = tab }
                        .padding(horizontal = 16.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tab,
                        color = if (isSelected) SpotifyGreenBright else SpotifyWhite,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Frosted Glass Search in Library Field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        text = "Search in Your Library",
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
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0x28FFFFFF),
                    unfocusedContainerColor = Color(0x18FFFFFF),
                    focusedBorderColor = SpotifyGreen,
                    unfocusedBorderColor = Color(0x20FFFFFF),
                    focusedTextColor = SpotifyWhite,
                    unfocusedTextColor = SpotifyWhite
                ),
                modifier = Modifier
                    .weight(1f)
                    .shadow(8.dp, RoundedCornerShape(14.dp), ambientColor = Color.Black)
                    .border(1.dp, Glassmorphism.specularBorderBrush, RoundedCornerShape(14.dp))
            )

            val sortLabel = if (selectedTab == "Playlists") playlistSort.label else librarySort.label
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x18FFFFFF))
                    .border(1.dp, Glassmorphism.specularBorderBrush, RoundedCornerShape(14.dp))
                    .clickable {
                        if (selectedTab == "Playlists") {
                            playlistSort = playlistSort.next()
                        } else {
                            librarySort = librarySort.next()
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Sort,
                    contentDescription = "Sort",
                    tint = SpotifyWhite,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = sortLabel,
                    color = SpotifyWhite,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (selectedTab) {
            "Playlists" -> PlaylistsTab(
                pinned = pinnedPlaylists,
                rest = unpinnedPlaylists,
                isEmpty = visiblePlaylists.isEmpty(),
                searchQuery = searchQuery,
                layout = libraryLayout,
                bannerMessage = importBannerMessage,
                onDismissBanner = { importBannerMessage = null },
                onOpenPlaylist = { selectedPlaylistId = it.id },
                onSharePlaylist = onSharePlaylist,
                onTogglePin = { onTogglePinPlaylist(it.id) },
                onCreatePlaylist = { showCreatePlaylistDialog = true },
                onImportPlaylist = { showImportPlaylistDialog = true },
                onBackupAllPlaylists = {
                    backupAllPicker.launch("Spotifly_Playlists_Backup.json")
                }
            )

            "Liked Songs" -> {
                val likedSongs = LibrarySorting.songs(
                    visibleSongs(
                        if (favoriteSongs.isNotEmpty()) favoriteSongs
                        else allSongs.filter { favorites.contains(it.id) }
                    ),
                    librarySort
                )
                if (likedSongs.isEmpty()) {
                    EmptyLibraryState(
                        icon = {
                            Icon(Icons.Default.Favorite, null, tint = SpotifyGreen, modifier = Modifier.size(48.dp))
                        },
                        title = if (searchQuery.isBlank()) "No liked songs yet" else "Nothing matches",
                        subtitle = if (searchQuery.isBlank()) {
                            "Tap the heart icon on any track to save it here"
                        } else {
                            "Try a different word"
                        }
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 120.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item { TrackCountLabel("${likedSongs.size} favorite songs") }
                        itemsIndexed(likedSongs, key = { _, song -> song.id }) { index, song ->
                            TrackItem(
                                song = song,
                                isPlaying = playbackState.currentSong?.id == song.id,
                                isFavorite = true,
                                isDownloaded = downloadStatus[song.id] == DownloadStatus.DOWNLOADED || song.isDownloaded,
                                isDownloading = downloadStatus[song.id] == DownloadStatus.DOWNLOADING,
                                onClick = { onPlayQueue(likedSongs, index) },
                                onFavoriteClick = { onFavoriteClick(song) },
                                onMoreClick = { onTrackMoreClick?.invoke(song) }
                            )
                        }
                    }
                }
            }

            "Albums" -> AlbumsTab(
                saved = savedAlbumRows,
                other = otherAlbums,
                isEmpty = savedAlbumRows.isEmpty() && otherAlbums.isEmpty(),
                searchQuery = searchQuery,
                layout = libraryLayout,
                savedIds = savedAlbumIds,
                onOpenAlbum = onOpenAlbum,
                onToggleSaved = onToggleSavedAlbum
            )

            "Artists" -> ArtistsTab(
                followed = followedArtistRows,
                other = otherArtists,
                isEmpty = followedArtistRows.isEmpty() && otherArtists.isEmpty(),
                searchQuery = searchQuery,
                layout = libraryLayout,
                followedIds = followedArtistIds,
                onOpenArtist = onOpenArtist,
                onToggleFollowed = onToggleFollowedArtist
            )

            "Downloaded" -> {
                val downloads = LibrarySorting.songs(visibleSongs(downloadedSongs), librarySort)
                if (downloads.isEmpty()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        StorageDashboardShortcutBanner(
                            subtitle = "Inspect artwork & temp cache allocations",
                            onClick = onOpenStorageDashboard
                        )
                        EmptyLibraryState(
                            icon = {
                                Icon(
                                    Icons.Default.DownloadDone,
                                    null,
                                    tint = SpotifyGreen,
                                    modifier = Modifier.size(52.dp)
                                )
                            },
                            title = if (searchQuery.isBlank()) "No downloaded tracks yet" else "Nothing matches",
                            subtitle = "Download any online song to listen completely offline without internet."
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 120.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item {
                            StorageDashboardShortcutBanner(
                                subtitle = "${downloads.size} offline tracks • Manage disk usage & caches",
                                onClick = onOpenStorageDashboard
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                        item { TrackCountLabel("${downloads.size} tracks saved for offline play") }
                        itemsIndexed(downloads, key = { _, song -> song.id }) { index, song ->
                            TrackItem(
                                song = song,
                                isPlaying = playbackState.currentSong?.id == song.id,
                                isFavorite = favorites.contains(song.id),
                                isDownloaded = true,
                                onClick = { onPlayQueue(downloads, index) },
                                onFavoriteClick = { onFavoriteClick(song) },
                                onMoreClick = { onTrackMoreClick?.invoke(song) }
                            )
                        }
                    }
                }
            }

            "Device" -> {
                if (!hasPermission) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = SpotifyGreen,
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Access Device Storage",
                                color = SpotifyWhite,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Allow Spotifly to scan and play audio files saved on your phone.",
                                color = SpotifyTextSecondary,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = { permissionLauncher.launch(permissionToRequest) },
                                colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Text(
                                    text = "Grant Storage Permission",
                                    color = SpotifyBlack,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                } else if (localSongs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Storage,
                                contentDescription = null,
                                tint = SpotifyTextSecondary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No local audio files found",
                                color = SpotifyWhite,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Add MP3, M4A or FLAC files to your device storage",
                                color = SpotifyTextSecondary,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = onScanLocalSongs,
                                colors = ButtonDefaults.buttonColors(containerColor = SpotifyCard)
                            ) {
                                Text(text = "Rescan Storage", color = SpotifyWhite)
                            }
                        }
                    }
                } else {
                    val deviceSongs = LibrarySorting.songs(visibleSongs(localSongs), librarySort)
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 120.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item { TrackCountLabel("${deviceSongs.size} tracks on device") }
                        itemsIndexed(deviceSongs, key = { _, song -> song.id }) { index, song ->
                            TrackItem(
                                song = song,
                                isPlaying = playbackState.currentSong?.id == song.id,
                                isFavorite = favorites.contains(song.id),
                                onClick = { onPlayQueue(deviceSongs, index) },
                                onFavoriteClick = { onFavoriteClick(song) },
                                onMoreClick = { onTrackMoreClick?.invoke(song) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text(text = "New Playlist", color = SpotifyWhite) },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    placeholder = { Text(text = "My Awesome Playlist", color = SpotifyTextSecondary) },
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
                        val name = newPlaylistName.trim().ifEmpty { "My Playlist" }
                        onCreatePlaylist(name)
                        showCreatePlaylistDialog = false
                        newPlaylistName = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen)
                ) {
                    Text(text = "Create", color = SpotifyBlack, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) {
                    Text(text = "Cancel", color = SpotifyTextSecondary)
                }
            },
            containerColor = SpotifyElevated
        )
    }

    if (showImportPlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showImportPlaylistDialog = false },
            containerColor = SpotifyElevated,
            title = { Text(text = "Import Playlist", color = SpotifyWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Pick an .m3u, .m3u8, or Spotifly .json backup file, or paste track lines below:",
                        color = SpotifyTextSecondary,
                        fontSize = 12.sp
                    )
                    Button(
                        onClick = {
                            showImportPlaylistDialog = false
                            importFilePicker.launch(arrayOf("*/*"))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Choose .m3u / .json File", color = SpotifyBlack, fontWeight = FontWeight.Bold)
                    }
                    OutlinedTextField(
                        value = importNameInput,
                        onValueChange = { importNameInput = it },
                        label = { Text("Playlist name (optional)", color = SpotifyTextSecondary, fontSize = 12.sp) },
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
                    OutlinedTextField(
                        value = importTextInput,
                        onValueChange = { importTextInput = it },
                        label = { Text("Paste M3U, JSON, or 'Artist - Song' lines", color = SpotifyTextSecondary, fontSize = 12.sp) },
                        minLines = 3,
                        maxLines = 5,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = SpotifyWhite,
                            unfocusedTextColor = SpotifyWhite,
                            focusedBorderColor = SpotifyGreen,
                            unfocusedBorderColor = SpotifyCard,
                            focusedContainerColor = SpotifyCard,
                            unfocusedContainerColor = SpotifyCard
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = importTextInput.isNotBlank(),
                    onClick = {
                        val fallback = importNameInput.trim().ifEmpty { "Imported Mix" }
                        onImportPlaylistsText(importTextInput, fallback) { msg ->
                            importBannerMessage = msg
                        }
                        importNameInput = ""
                        importTextInput = ""
                        showImportPlaylistDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen)
                ) {
                    Text("Import Text", color = SpotifyBlack, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportPlaylistDialog = false }) {
                    Text(text = "Cancel", color = SpotifyTextSecondary)
                }
            }
        )
    }
}

@Composable
private fun PlaylistsTab(
    pinned: List<Playlist>,
    rest: List<Playlist>,
    isEmpty: Boolean,
    searchQuery: String,
    layout: LibraryLayout,
    bannerMessage: String? = null,
    onDismissBanner: () -> Unit = {},
    onOpenPlaylist: (Playlist) -> Unit,
    onSharePlaylist: (Playlist) -> Unit,
    onTogglePin: (Playlist) -> Unit,
    onCreatePlaylist: () -> Unit,
    onImportPlaylist: () -> Unit = {},
    onBackupAllPlaylists: () -> Unit = {}
) {
    if (isEmpty) {
        Column(modifier = Modifier.fillMaxSize()) {
            PlaylistManagementActionsRow(
                bannerMessage = bannerMessage,
                onDismissBanner = onDismissBanner,
                onCreatePlaylist = onCreatePlaylist,
                onImportPlaylist = onImportPlaylist,
                onBackupAllPlaylists = onBackupAllPlaylists
            )
            EmptyLibraryMessage(
                text = if (searchQuery.isBlank()) "No playlists created yet"
                else "No playlists match \"$searchQuery\""
            )
        }
        return
    }

    if (layout == LibraryLayout.GRID) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(bottom = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                PlaylistManagementActionsRow(
                    bannerMessage = bannerMessage,
                    onDismissBanner = onDismissBanner,
                    onCreatePlaylist = onCreatePlaylist,
                    onImportPlaylist = onImportPlaylist,
                    onBackupAllPlaylists = onBackupAllPlaylists
                )
            }
            if (pinned.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel("Pinned") }
                gridItems(pinned, key = { "pinned_${it.id}" }) { playlist ->
                    PlaylistGridCard(playlist, onOpenPlaylist, onTogglePin)
                }
                item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel("All playlists") }
            }
            gridItems(rest, key = { it.id }) { playlist ->
                PlaylistGridCard(playlist, onOpenPlaylist, onTogglePin)
            }
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 120.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            PlaylistManagementActionsRow(
                bannerMessage = bannerMessage,
                onDismissBanner = onDismissBanner,
                onCreatePlaylist = onCreatePlaylist,
                onImportPlaylist = onImportPlaylist,
                onBackupAllPlaylists = onBackupAllPlaylists
            )
        }
        if (pinned.isNotEmpty()) {
            item { SectionLabel("Pinned") }
            items(pinned, key = { "pinned_${it.id}" }) { playlist ->
                PlaylistRow(playlist, onOpenPlaylist, onSharePlaylist, onTogglePin, isPinned = true)
            }
        }
        if (pinned.isNotEmpty() && rest.isNotEmpty()) {
            item { SectionLabel("All playlists") }
        }
        items(rest, key = { it.id }) { playlist ->
            PlaylistRow(playlist, onOpenPlaylist, onSharePlaylist, onTogglePin, isPinned = false)
        }
    }
}

@Composable
private fun PlaylistManagementActionsRow(
    bannerMessage: String?,
    onDismissBanner: () -> Unit,
    onCreatePlaylist: () -> Unit,
    onImportPlaylist: () -> Unit,
    onBackupAllPlaylists: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CreatePlaylistCard(onCreatePlaylist)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Glassmorphism.darkGlassBrush)
                    .border(0.8.dp, Glassmorphism.specularBorderBrush, RoundedCornerShape(12.dp))
                    .clickable(onClick = onImportPlaylist)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FileUpload,
                    contentDescription = null,
                    tint = SpotifyGreenBright,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Import Playlist",
                    color = SpotifyWhite,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Glassmorphism.darkGlassBrush)
                    .border(0.8.dp, Glassmorphism.specularBorderBrush, RoundedCornerShape(12.dp))
                    .clickable(onClick = onBackupAllPlaylists)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FileDownload,
                    contentDescription = null,
                    tint = SpotifyGreenBright,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Backup All",
                    color = SpotifyWhite,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
        if (!bannerMessage.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Glassmorphism.accentGlassBrush(SpotifyGreen, 0.18f))
                    .border(0.8.dp, Glassmorphism.accentGlassBorderBrush(SpotifyGreen), RoundedCornerShape(12.dp))
                    .clickable(onClick = onDismissBanner)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = bannerMessage,
                    color = SpotifyGreenBright,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(text = "OK", color = SpotifyWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun StorageDashboardShortcutBanner(
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Glassmorphism.darkGlassBrush)
            .border(1.dp, Glassmorphism.specularBorderBrush, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Storage,
            contentDescription = null,
            tint = SpotifyGreenBright,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Storage & Cache Dashboard",
                color = SpotifyWhite,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                color = SpotifyTextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = "Manage",
            color = SpotifyGreenBright,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun CreatePlaylistCard(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SpotifyCard)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(SpotifyGreen.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "New",
                tint = SpotifyGreen,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(
                text = "Create Playlist",
                color = SpotifyWhite,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Build your personal music collection",
                color = SpotifyTextSecondary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun PlaylistRow(
    playlist: Playlist,
    onOpen: (Playlist) -> Unit,
    onShare: (Playlist) -> Unit,
    onTogglePin: (Playlist) -> Unit,
    isPinned: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onOpen(playlist) }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlaylistArtwork(playlist)
        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                color = SpotifyWhite,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Playlist • ${playlist.songs.size} tracks",
                color = SpotifyTextSecondary,
                fontSize = 13.sp
            )
            if (playlist.description.isNotBlank()) {
                Text(
                    text = playlist.description,
                    color = SpotifyTextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        IconButton(onClick = { onTogglePin(playlist) }) {
            Icon(
                imageVector = Icons.Default.PushPin,
                contentDescription = if (isPinned) "Unpin playlist" else "Pin playlist",
                tint = if (isPinned) SpotifyGreen else SpotifyTextSecondary,
                modifier = Modifier.size(19.dp)
            )
        }
        IconButton(onClick = { onShare(playlist) }) {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = "Share playlist",
                tint = SpotifyTextSecondary,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

@Composable
private fun PlaylistGridCard(
    playlist: Playlist,
    onOpen: (Playlist) -> Unit,
    onTogglePin: (Playlist) -> Unit
) {
    LibraryEntityCard(
        title = playlist.name,
        subtitle = "${playlist.songs.size} tracks",
        artworkUrl = playlist.coverPath?.let { File(it) } ?: playlist.songs.firstOrNull()?.artworkUrl,
        isCircle = false,
        fallback = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null, tint = it) },
        onClick = { onOpen(playlist) },
        overlay = {
            IconButton(
                onClick = { onTogglePin(playlist) },
                modifier = Modifier
                    .size(32.dp)
                    .background(SpotifyScrim.copy(alpha = 0.55f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.PushPin,
                    contentDescription = "Pin playlist",
                    tint = SpotifyWhite,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    )
}

@Composable
private fun AlbumsTab(
    saved: List<Album>,
    other: List<Album>,
    isEmpty: Boolean,
    searchQuery: String,
    layout: LibraryLayout,
    savedIds: Set<String>,
    onOpenAlbum: (Album) -> Unit,
    onToggleSaved: (Album) -> Unit
) {
    if (isEmpty) {
        EmptyLibraryState(
            icon = { Icon(Icons.Default.MusicNote, null, tint = SpotifyGreen, modifier = Modifier.size(48.dp)) },
            title = if (searchQuery.isBlank()) "No albums yet" else "Nothing matches",
            subtitle = "Save an album, or let your library grow as you play"
        )
        return
    }

    val sections = buildList {
        if (saved.isNotEmpty()) add("Saved albums" to saved)
        if (other.isNotEmpty()) add("All albums" to other)
    }

    if (layout == LibraryLayout.GRID) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(bottom = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            sections.forEach { (label, items) ->
                item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel(label) }
                gridItems(items, key = { "${label}_${it.id}" }) { album ->
                    LibraryEntityCard(
                        title = album.title,
                        subtitle = album.displayArtist,
                        artworkUrl = album.artworkUrl,
                        isCircle = false,
                        fallback = { Icon(Icons.Default.MusicNote, null, tint = it) },
                        onClick = { onOpenAlbum(album) },
                        overlay = {
                            SavedAlbumButton(album, album.id in savedIds, onToggleSaved)
                        }
                    )
                }
            }
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 120.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        sections.forEach { (label, items) ->
            item { SectionLabel(label) }
            items(items, key = { "${label}_${it.id}" }) { album ->
                LibraryEntityRow(
                    title = album.title,
                    subtitle = buildString {
                        append(album.displayArtist)
                        if (album.year > 0) append(" • ${album.year}")
                        append(" • ${album.trackCount} tracks")
                    },
                    artworkUrl = album.artworkUrl,
                    isCircle = false,
                    fallback = { Icon(Icons.Default.MusicNote, null, tint = it) },
                    onClick = { onOpenAlbum(album) },
                    trailing = {
                        SavedAlbumButton(album, album.id in savedIds, onToggleSaved)
                    }
                )
            }
        }
    }
}

@Composable
private fun SavedAlbumButton(album: Album, isSaved: Boolean, onToggle: (Album) -> Unit) {
    IconButton(onClick = { onToggle(album) }) {
        Icon(
            imageVector = if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
            contentDescription = if (isSaved) "Remove saved album" else "Save album",
            tint = if (isSaved) SpotifyGreen else SpotifyTextSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun ArtistsTab(
    followed: List<Artist>,
    other: List<Artist>,
    isEmpty: Boolean,
    searchQuery: String,
    layout: LibraryLayout,
    followedIds: Set<String>,
    onOpenArtist: (Artist) -> Unit,
    onToggleFollowed: (Artist) -> Unit
) {
    if (isEmpty) {
        EmptyLibraryState(
            icon = { Icon(Icons.Default.Person, null, tint = SpotifyGreen, modifier = Modifier.size(48.dp)) },
            title = if (searchQuery.isBlank()) "No artists yet" else "Nothing matches",
            subtitle = "Follow an artist, or let your library grow as you play"
        )
        return
    }

    val sections = buildList {
        if (followed.isNotEmpty()) add("Following" to followed)
        if (other.isNotEmpty()) add("Artists in your library" to other)
    }

    if (layout == LibraryLayout.GRID) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(bottom = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            sections.forEach { (label, items) ->
                item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel(label) }
                gridItems(items, key = { "${label}_${it.id}" }) { artist ->
                    LibraryEntityCard(
                        title = artist.name,
                        subtitle = "${artist.trackCount} songs",
                        artworkUrl = artist.artworkUrl,
                        isCircle = true,
                        fallback = { Icon(Icons.Default.Person, null, tint = it) },
                        onClick = { onOpenArtist(artist) },
                        overlay = { FollowedArtistButton(artist, artist.id in followedIds, onToggleFollowed) }
                    )
                }
            }
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 120.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        sections.forEach { (label, items) ->
            item { SectionLabel(label) }
            items(items, key = { "${label}_${it.id}" }) { artist ->
                LibraryEntityRow(
                    title = artist.name,
                    subtitle = "${artist.trackCount} songs • ${artist.albumCount} albums",
                    artworkUrl = artist.artworkUrl,
                    isCircle = true,
                    fallback = { Icon(Icons.Default.Person, null, tint = it) },
                    onClick = { onOpenArtist(artist) },
                    trailing = {
                        FollowedArtistButton(artist, artist.id in followedIds, onToggleFollowed)
                    }
                )
            }
        }
    }
}

@Composable
private fun FollowedArtistButton(artist: Artist, isFollowed: Boolean, onToggle: (Artist) -> Unit) {
    IconButton(onClick = { onToggle(artist) }) {
        Icon(
            imageVector = if (isFollowed) Icons.Default.CheckCircle else Icons.Default.PersonAdd,
            contentDescription = if (isFollowed) "Unfollow artist" else "Follow artist",
            tint = if (isFollowed) SpotifyGreen else SpotifyTextSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}
