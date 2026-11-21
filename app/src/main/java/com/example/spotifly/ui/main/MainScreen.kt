package com.example.spotifly.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.spotifly.data.deeplink.DeepLinkTarget
import com.example.spotifly.model.DownloadStatus
import com.example.spotifly.model.Song
import com.example.spotifly.theme.SpotifyBackground
import com.example.spotifly.theme.SpotifyDarkGray
import com.example.spotifly.theme.SpotifyTextSecondary
import com.example.spotifly.theme.SpotifyWhite
import com.example.spotifly.ui.album.AlbumDetailScreen
import com.example.spotifly.ui.artist.ArtistDetailScreen
import com.example.spotifly.ui.components.AddToPlaylistModal
import com.example.spotifly.ui.components.FullPlayerModal
import com.example.spotifly.ui.components.MiniPlayer
import com.example.spotifly.ui.components.QueueBottomSheet
import com.example.spotifly.ui.components.SettingsSheet
import com.example.spotifly.ui.components.ShareUtils
import com.example.spotifly.ui.components.TrackOptionsBottomSheet
import com.example.spotifly.ui.home.HomeScreen
import com.example.spotifly.ui.library.LibraryScreen
import com.example.spotifly.ui.podcast.PodcastsScreen
import com.example.spotifly.ui.search.SearchScreen
import com.example.spotifly.ui.settings.AboutScreen
import com.example.spotifly.ui.settings.AudioSettingsScreen
import com.example.spotifly.ui.stats.StatsScreen
import kotlinx.coroutines.launch

enum class MainTab {
    HOME,
    SEARCH,
    LIBRARY
}

/** Full-screen destinations pushed above the tab content, Spotify-style. */
private enum class DetailRoute {
    NONE,
    STATS,
    AUDIO_SETTINGS,
    PODCASTS,
    ABOUT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    deepLink: DeepLinkTarget? = null,
    onDeepLinkConsumed: () -> Unit = {},
    viewModel: MainScreenViewModel = viewModel()
) {
    var currentTab by remember { mutableStateOf(MainTab.HOME) }
    var isFullPlayerVisible by remember { mutableStateOf(false) }
    var isQueueVisible by remember { mutableStateOf(false) }
    var isSettingsVisible by remember { mutableStateOf(false) }
    var detailRoute by remember { mutableStateOf(DetailRoute.NONE) }

    var selectedTrackForOptions by remember { mutableStateOf<Song?>(null) }
    var selectedTrackForPlaylist by remember { mutableStateOf<Song?>(null) }
    var queueToSave by remember { mutableStateOf<List<Song>?>(null) }
    var queuePlaylistName by remember { mutableStateOf("") }
    // Deep links that need a screen which owns its own selection state.
    var pendingPlaylistDeepLink by remember { mutableStateOf<String?>(null) }
    var pendingSearchDeepLink by remember { mutableStateOf<String?>(null) }

    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val localSongs by viewModel.localSongs.collectAsStateWithLifecycle()
    val allSongs by viewModel.allSongs.collectAsStateWithLifecycle()
    val favoriteSongs by viewModel.favoriteSongs.collectAsStateWithLifecycle()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsStateWithLifecycle()

    val recentSearches by viewModel.recentSearches.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val downloadedSongs by viewModel.downloadedSongs.collectAsStateWithLifecycle()
    val downloadStatus by viewModel.downloadStatus.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val downloadedBytes by viewModel.downloadedBytes.collectAsStateWithLifecycle()
    val sleepTimerEndMs by viewModel.sleepTimerEndMs.collectAsStateWithLifecycle()
    val sleepAtEndOfTrack by viewModel.sleepAtEndOfTrack.collectAsStateWithLifecycle()
    val accentTheme by viewModel.accentTheme.collectAsStateWithLifecycle()

    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val savedAlbums by viewModel.savedAlbums.collectAsStateWithLifecycle()
    val followedArtists by viewModel.followedArtists.collectAsStateWithLifecycle()
    val pinnedPlaylistIds by viewModel.pinnedPlaylistIds.collectAsStateWithLifecycle()
    val libraryLayout by viewModel.libraryLayout.collectAsStateWithLifecycle()
    val hideExplicit by viewModel.hideExplicit.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val streamQuality by viewModel.streamQuality.collectAsStateWithLifecycle()
    val dataSaver by viewModel.dataSaver.collectAsStateWithLifecycle()
    val offlineMode by viewModel.offlineMode.collectAsStateWithLifecycle()
    val cacheBytes by viewModel.cacheBytes.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val openAlbum by viewModel.openAlbum.collectAsStateWithLifecycle()
    val openArtist by viewModel.openArtist.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val queueSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val settingsSheetState = rememberModalBottomSheetState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Surface stream failures as a snackbar instead of failing silently
    LaunchedEffect(playbackState.playbackError) {
        val error = playbackState.playbackError
        if (error != null) {
            snackbarHostState.showSnackbar(error)
            // Reset so the very same failure can be reported again
            viewModel.clearPlaybackError()
        }
    }

    // spotifly:// links resolve against what the library actually holds. A link
    // is consumed once so rotation does not replay it.
    LaunchedEffect(deepLink) {
        val target = deepLink ?: return@LaunchedEffect
        onDeepLinkConsumed()
        detailRoute = DetailRoute.NONE
        when (target) {
            is DeepLinkTarget.Track -> {
                val song = (allSongs + downloadedSongs).firstOrNull { it.id == target.id }
                if (song != null) {
                    viewModel.playSong(song)
                    isFullPlayerVisible = true
                } else {
                    snackbarHostState.showSnackbar("That track isn't in your library")
                }
            }

            is DeepLinkTarget.Album -> {
                val album = albums.firstOrNull { it.id == target.id }
                    ?: savedAlbums.firstOrNull { it.id == target.id }
                when {
                    album != null -> viewModel.openAlbum(album)
                    offlineMode -> snackbarHostState.showSnackbar("Offline mode: album not available")
                    else -> viewModel.openAlbumById(target.id)
                }
            }

            is DeepLinkTarget.Artist -> {
                val artist = artists.firstOrNull { it.id == target.id }
                    ?: followedArtists.firstOrNull { it.id == target.id }
                when {
                    artist != null -> viewModel.openArtist(artist)
                    offlineMode -> snackbarHostState.showSnackbar("Offline mode: artist not available")
                    else -> viewModel.openArtistById(target.id)
                }
            }

            is DeepLinkTarget.Playlist -> {
                if (playlists.any { it.id == target.id }) {
                    currentTab = MainTab.LIBRARY
                    pendingPlaylistDeepLink = target.id
                } else {
                    snackbarHostState.showSnackbar("That playlist no longer exists")
                }
            }

            is DeepLinkTarget.Search -> {
                currentTab = MainTab.SEARCH
                pendingSearchDeepLink = target.query
            }

            DeepLinkTarget.Home -> currentTab = MainTab.HOME
        }
    }

    // Android back closes the topmost detail layer, then the player, then the sheet.
    BackHandler(
        enabled = openAlbum != null || openArtist != null ||
            detailRoute != DetailRoute.NONE || isFullPlayerVisible
    ) {
        when {
            openAlbum != null -> viewModel.closeAlbum()
            openArtist != null -> viewModel.closeArtist()
            detailRoute != DetailRoute.NONE -> detailRoute = DetailRoute.NONE
            isFullPlayerVisible -> isFullPlayerVisible = false
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            NavigationBar(
                containerColor = SpotifyBackground.copy(alpha = 0.98f),
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    selected = currentTab == MainTab.HOME && detailRoute == DetailRoute.NONE,
                    onClick = {
                        currentTab = MainTab.HOME
                        detailRoute = DetailRoute.NONE
                    },
                    icon = {
                        Icon(
                            imageVector = if (currentTab == MainTab.HOME) Icons.Filled.Home else Icons.Outlined.Home,
                            contentDescription = "Home"
                        )
                    },
                    label = {
                        Text(
                            text = "Home",
                            fontSize = 11.sp,
                            fontWeight = if (currentTab == MainTab.HOME) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = SpotifyWhite,
                        unselectedIconColor = SpotifyTextSecondary,
                        selectedTextColor = SpotifyWhite,
                        unselectedTextColor = SpotifyTextSecondary,
                        indicatorColor = Color.Transparent
                    )
                )

                NavigationBarItem(
                    selected = currentTab == MainTab.SEARCH && detailRoute == DetailRoute.NONE,
                    onClick = {
                        currentTab = MainTab.SEARCH
                        detailRoute = DetailRoute.NONE
                    },
                    icon = {
                        Icon(
                            imageVector = if (currentTab == MainTab.SEARCH) Icons.Filled.Search else Icons.Outlined.Search,
                            contentDescription = "Search"
                        )
                    },
                    label = {
                        Text(
                            text = "Search",
                            fontSize = 11.sp,
                            fontWeight = if (currentTab == MainTab.SEARCH) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = SpotifyWhite,
                        unselectedIconColor = SpotifyTextSecondary,
                        selectedTextColor = SpotifyWhite,
                        unselectedTextColor = SpotifyTextSecondary,
                        indicatorColor = Color.Transparent
                    )
                )

                NavigationBarItem(
                    selected = currentTab == MainTab.LIBRARY && detailRoute == DetailRoute.NONE,
                    onClick = {
                        currentTab = MainTab.LIBRARY
                        detailRoute = DetailRoute.NONE
                    },
                    icon = {
                        Icon(
                            imageVector = if (currentTab == MainTab.LIBRARY) Icons.Filled.LibraryMusic else Icons.Outlined.LibraryMusic,
                            contentDescription = "Your Library"
                        )
                    },
                    label = {
                        Text(
                            text = "Your Library",
                            fontSize = 11.sp,
                            fontWeight = if (currentTab == MainTab.LIBRARY) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = SpotifyWhite,
                        unselectedIconColor = SpotifyTextSecondary,
                        selectedTextColor = SpotifyWhite,
                        unselectedTextColor = SpotifyTextSecondary,
                        indicatorColor = Color.Transparent
                    )
                )
            }
        },
        containerColor = SpotifyBackground
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Album and artist screens stack above the tabs, so closing one returns
            // the user exactly where they were.
            val album = openAlbum
            val artist = openArtist

            when {
                album != null -> {
                    AlbumDetailScreen(
                        album = album,
                        playbackState = playbackState,
                        favorites = favorites,
                        downloadStatus = downloadStatus,
                        onBack = { viewModel.closeAlbum() },
                        onPlayQueue = { songs, index -> viewModel.playQueue(songs, index) },
                        onFavoriteClick = { viewModel.toggleFavorite(it) },
                        onTrackMoreClick = { selectedTrackForOptions = it },
                        onOpenArtist = { song ->
                            viewModel.openArtist(
                                com.example.spotifly.data.catalog.LibraryGrouping.artistFor(song, allSongs)
                            )
                        },
                        isSaved = savedAlbums.any { it.id == album.id },
                        onToggleSaved = { viewModel.toggleSavedAlbum(album) },
                        onDownloadAll = { viewModel.downloadSongs(album.songs) }
                    )
                }

                artist != null -> {
                    ArtistDetailScreen(
                        artist = artist,
                        playbackState = playbackState,
                        favorites = favorites,
                        downloadStatus = downloadStatus,
                        onBack = { viewModel.closeArtist() },
                        onPlayQueue = { songs, index -> viewModel.playQueue(songs, index) },
                        onFavoriteClick = { viewModel.toggleFavorite(it) },
                        onTrackMoreClick = { selectedTrackForOptions = it },
                        onOpenAlbum = { viewModel.openAlbum(it) },
                        onStartRadio = { viewModel.startArtistRadio(artist) },
                        isFollowed = followedArtists.any { it.id == artist.id },
                        onToggleFollowed = { viewModel.toggleFollowedArtist(artist) }
                    )
                }

                detailRoute == DetailRoute.STATS -> {
                    StatsScreen(
                        stats = stats,
                        onBack = { detailRoute = DetailRoute.NONE },
                        onClear = { viewModel.clearStats() }
                    )
                }

                detailRoute == DetailRoute.AUDIO_SETTINGS -> {
                    AudioSettingsScreen(onBack = { detailRoute = DetailRoute.NONE })
                }

                detailRoute == DetailRoute.PODCASTS -> {
                    PodcastsScreen(
                        playbackState = playbackState,
                        onPlayEpisodeQueue = { songs, index ->
                            viewModel.playQueue(songs, index)
                        },
                        onBack = { detailRoute = DetailRoute.NONE }
                    )
                }

                detailRoute == DetailRoute.ABOUT -> {
                    AboutScreen(onBack = { detailRoute = DetailRoute.NONE })
                }

                else -> {
                    when (currentTab) {
                        MainTab.HOME -> {
                            HomeScreen(
                                playbackState = playbackState,
                                favorites = favorites,
                                recentlyPlayed = recentlyPlayed,
                                topArtists = stats.topArtists,
                                playlists = playlists,
                                albums = albums,
                                onSongClick = { viewModel.playSong(it) },
                                onPlayQueue = { songs, index -> viewModel.playQueue(songs, index) },
                                onFavoriteClick = { viewModel.toggleFavorite(it) },
                                onShuffleAll = { viewModel.shuffleAll() },
                                onOpenSettings = {
                                    currentTab = MainTab.HOME
                                    isSettingsVisible = true
                                },
                                onOpenAlbum = { viewModel.openAlbum(it) },
                                onOpenArtist = { viewModel.openArtist(it) },
                                onOpenStats = { detailRoute = DetailRoute.STATS },
                                onOpenAudioSettings = { detailRoute = DetailRoute.AUDIO_SETTINGS }
                            )
                        }

                        MainTab.SEARCH -> {
                            SearchScreen(
                                playbackState = playbackState,
                                favorites = favorites,
                                localSongs = localSongs,
                                recentSearches = recentSearches,
                                downloadStatus = downloadStatus,
                                playlists = playlists,
                                onSongClick = { viewModel.playSong(it) },
                                onPlayQueue = { songs, index -> viewModel.playQueue(songs, index) },
                                onFavoriteClick = { viewModel.toggleFavorite(it) },
                                onTrackMoreClick = { selectedTrackForOptions = it },
                                onOpenAlbum = { viewModel.openAlbum(it) },
                                onOpenArtist = { viewModel.openArtist(it) },
                                onOpenPodcasts = { detailRoute = DetailRoute.PODCASTS },
                                onAddSearchQuery = { viewModel.addSearchQuery(it) },
                                onRemoveSearchQuery = { viewModel.removeSearchQuery(it) },
                                onClearSearchHistory = { viewModel.clearSearchHistory() },
                                offlineMode = offlineMode,
                                initialQuery = pendingSearchDeepLink,
                                onInitialQueryConsumed = { pendingSearchDeepLink = null }
                            )
                        }

                        MainTab.LIBRARY -> {
                            LibraryScreen(
                                playbackState = playbackState,
                                favorites = favorites,
                                allSongs = allSongs,
                                localSongs = localSongs,
                                favoriteSongs = favoriteSongs,
                                downloadedSongs = downloadedSongs,
                                downloadStatus = downloadStatus,
                                playlists = playlists,
                                albums = albums,
                                artists = artists,
                                savedAlbums = savedAlbums,
                                followedArtists = followedArtists,
                                pinnedPlaylistIds = pinnedPlaylistIds,
                                libraryLayout = libraryLayout,
                                hideExplicit = hideExplicit,
                                onScanLocalSongs = { viewModel.scanLocalSongs() },
                                onSongClick = { viewModel.playSong(it) },
                                onPlayQueue = { songs, index -> viewModel.playQueue(songs, index) },
                                onFavoriteClick = { viewModel.toggleFavorite(it) },
                                onTrackMoreClick = { selectedTrackForOptions = it },
                                onCreatePlaylist = { viewModel.createPlaylist(it) },
                                onDeletePlaylist = { viewModel.deletePlaylist(it) },
                                onRemoveSongFromPlaylist = { pId, sId ->
                                    viewModel.removeSongFromPlaylist(pId, sId)
                                },
                                onRenamePlaylist = { pId, newName ->
                                    viewModel.renamePlaylist(pId, newName)
                                },
                                onMoveSong = { pId, from, to ->
                                    viewModel.moveSongInPlaylist(pId, from, to)
                                },
                                onUpdateDescription = { pId, description ->
                                    viewModel.updatePlaylistDescription(pId, description)
                                },
                                onOpenAlbum = { viewModel.openAlbum(it) },
                                onOpenArtist = { viewModel.openArtist(it) },
                                onSharePlaylist = { ShareUtils.sharePlaylist(context, it) },
                                onToggleSavedAlbum = { viewModel.toggleSavedAlbum(it) },
                                onToggleFollowedArtist = { viewModel.toggleFollowedArtist(it) },
                                onTogglePinPlaylist = { viewModel.togglePlaylistPinned(it) },
                                onToggleLayout = { viewModel.toggleLibraryLayout() },
                                onSetPlaylistCover = { playlistId, uri ->
                                    viewModel.setPlaylistCover(playlistId, uri)
                                },
                                onRemovePlaylistCover = { viewModel.removePlaylistCover(it) },
                                onDownloadPlaylist = { viewModel.downloadSongs(it.songs) },
                                openPlaylistId = pendingPlaylistDeepLink,
                                onPlaylistDeepLinkConsumed = { pendingPlaylistDeepLink = null }
                            )
                        }
                    }
                }
            }

            // Floating MiniPlayer above Navigation Bar
            if (playbackState.currentSong != null) {
                MiniPlayer(
                    playbackState = playbackState,
                    isFavorite = favorites.contains(playbackState.currentSong?.id),
                    onPlayPauseClick = { viewModel.togglePlayPause() },
                    onFavoriteClick = { playbackState.currentSong?.let { viewModel.toggleFavorite(it) } },
                    onClick = { isFullPlayerVisible = true },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }

    // Full Screen Player Modal
    AnimatedVisibility(
        visible = isFullPlayerVisible,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it })
    ) {
        val currentSong = playbackState.currentSong
        val isCurrentDownloaded = currentSong?.let {
            downloadStatus[it.id] == DownloadStatus.DOWNLOADED || it.isDownloaded
        } ?: false
        val isCurrentDownloading = currentSong?.let {
            downloadStatus[it.id] == DownloadStatus.DOWNLOADING
        } ?: false
        val currentDownloadProgress = currentSong?.let { downloadProgress[it.id] }

        FullPlayerModal(
            playbackState = playbackState,
            isFavorite = favorites.contains(currentSong?.id),
            isDownloaded = isCurrentDownloaded,
            isDownloading = isCurrentDownloading,
            downloadProgressPercent = currentDownloadProgress,
            sleepTimerEndMs = sleepTimerEndMs,
            sleepAtEndOfTrack = sleepAtEndOfTrack,
            onDismiss = { isFullPlayerVisible = false },
            onPlayPauseClick = { viewModel.togglePlayPause() },
            onSeekTo = { viewModel.seekTo(it) },
            onSkipNext = { viewModel.skipNext() },
            onSkipPrevious = { viewModel.skipPrevious() },
            onToggleShuffle = { viewModel.toggleShuffle() },
            onToggleRepeat = { viewModel.toggleRepeat() },
            onFavoriteClick = { currentSong?.let { viewModel.toggleFavorite(it) } },
            onDownloadClick = {
                currentSong?.let { song ->
                    if (isCurrentDownloaded) {
                        viewModel.removeDownload(song.id)
                    } else {
                        viewModel.downloadSong(song)
                    }
                }
            },
            onCancelDownloadClick = { currentSong?.let { viewModel.cancelDownload(it.id) } },
            onAddToPlaylistClick = { selectedTrackForPlaylist = currentSong },
            onToggleAutoplay = { viewModel.toggleAutoplay() },
            onSetPlaybackSpeed = { viewModel.setPlaybackSpeed(it) },
            onStartSleepTimer = { viewModel.startSleepTimer(it) },
            onStartSleepTimerAtEndOfTrack = { viewModel.startSleepTimerAtEndOfTrack() },
            onCancelSleepTimer = { viewModel.cancelSleepTimer() },
            onOpenQueue = { isQueueVisible = true }
        )
    }

    // Queue Bottom Sheet
    if (isQueueVisible) {
        ModalBottomSheet(
            onDismissRequest = { isQueueVisible = false },
            sheetState = queueSheetState,
            containerColor = SpotifyDarkGray
        ) {
            QueueBottomSheet(
                queue = playbackState.queue,
                currentIndex = playbackState.currentIndex,
                manualQueue = playbackState.manualQueue,
                autoplayQueue = playbackState.autoplayQueue,
                onSongClick = { index ->
                    viewModel.playQueue(playbackState.queue, index)
                    coroutineScope.launch { queueSheetState.hide() }
                    isQueueVisible = false
                },
                onMoveQueueItem = { from, to -> viewModel.moveQueueItem(from, to) },
                onRemoveQueueItem = { index -> viewModel.removeFromQueue(index) },
                autoplayNextId = playbackState.autoplayNextId,
                onSaveAsPlaylist = {
                    queueToSave = playbackState.queue.toList()
                    queuePlaylistName = playbackState.queue.firstOrNull()?.let { "${it.title} Mix" }.orEmpty()
                    isQueueVisible = false
                },
                onClose = {
                    coroutineScope.launch { queueSheetState.hide() }
                    isQueueVisible = false
                }
            )
        }
    }

    queueToSave?.let { songs ->
        AlertDialog(
            onDismissRequest = { queueToSave = null },
            title = { Text("Save as Playlist") },
            text = {
                OutlinedTextField(
                    value = queuePlaylistName,
                    onValueChange = { queuePlaylistName = it },
                    label = { Text("Playlist name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    enabled = queuePlaylistName.isNotBlank(),
                    onClick = {
                        val playlist = viewModel.saveQueueAsPlaylist(queuePlaylistName, songs)
                        queueToSave = null
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Saved ${playlist.name} to Your Library")
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { queueToSave = null }) { Text("Cancel") }
            }
        )
    }

    // Settings Bottom Sheet
    if (isSettingsVisible) {
        ModalBottomSheet(
            onDismissRequest = { isSettingsVisible = false },
            sheetState = settingsSheetState,
            containerColor = SpotifyDarkGray
        ) {
            SettingsSheet(
                currentAccent = accentTheme,
                autoplayEnabled = playbackState.isAutoplay,
                downloadedCount = downloadedSongs.size,
                downloadedBytes = downloadedBytes,
                onAccentSelected = { viewModel.setAccent(it.name) },
                onAutoplayChanged = { enabled ->
                    if (enabled != playbackState.isAutoplay) viewModel.toggleAutoplay()
                },
                onClearSearchHistory = { viewModel.clearSearchHistory() },
                onClearRecentlyPlayed = { viewModel.clearRecentlyPlayed() },
                playbackSpeed = playbackState.playbackSpeed,
                onSpeedSelected = { viewModel.setPlaybackSpeed(it) },
                onOpenAudioSettings = {
                    coroutineScope.launch { settingsSheetState.hide() }
                    isSettingsVisible = false
                    detailRoute = DetailRoute.AUDIO_SETTINGS
                },
                onOpenStats = {
                    coroutineScope.launch { settingsSheetState.hide() }
                    isSettingsVisible = false
                    detailRoute = DetailRoute.STATS
                },
                onOpenAbout = {
                    coroutineScope.launch { settingsSheetState.hide() }
                    isSettingsVisible = false
                    detailRoute = DetailRoute.ABOUT
                },
                themeMode = themeMode,
                onThemeModeSelected = { viewModel.setThemeMode(it) },
                hideExplicit = hideExplicit,
                onHideExplicitChanged = { viewModel.setHideExplicit(it) },
                streamQuality = streamQuality,
                onStreamQualitySelected = { viewModel.setStreamQuality(it) },
                dataSaver = dataSaver,
                onDataSaverChanged = { viewModel.setDataSaver(it) },
                offlineMode = offlineMode,
                onOfflineModeChanged = { viewModel.setOfflineMode(it) },
                cacheBytes = cacheBytes,
                onClearCache = { viewModel.clearImageCache() },
                onDismiss = {
                    coroutineScope.launch { settingsSheetState.hide() }
                    isSettingsVisible = false
                }
            )
        }
    }

    // Track Options Bottom Sheet
    selectedTrackForOptions?.let { track ->
        val isFav = favorites.contains(track.id)
        val isDown = downloadStatus[track.id] == DownloadStatus.DOWNLOADED || track.isDownloaded
        val isDownloading = downloadStatus[track.id] == DownloadStatus.DOWNLOADING
        val progressPercent = downloadProgress[track.id]
        TrackOptionsBottomSheet(
            song = track,
            isFavorite = isFav,
            isDownloaded = isDown,
            isDownloading = isDownloading,
            downloadProgressPercent = progressPercent,
            onAddToPlaylistClick = { selectedTrackForPlaylist = track },
            onDownloadClick = {
                if (isDown) viewModel.removeDownload(track.id) else viewModel.downloadSong(track)
            },
            onCancelDownloadClick = { viewModel.cancelDownload(track.id) },
            onAddToQueueClick = { viewModel.addToQueue(track) },
            onToggleFavoriteClick = { viewModel.toggleFavorite(track) },
            onPlayNextClick = { viewModel.playNext(track) },
            onSongRadioClick = { viewModel.startSongRadio(track) },
            onShareClick = { ShareUtils.shareSong(context, track) },
            onDismiss = { selectedTrackForOptions = null }
        )
    }

    // Add To Playlist Modal
    selectedTrackForPlaylist?.let { track ->
        AddToPlaylistModal(
            song = track,
            playlists = playlists,
            onAddToPlaylist = { playlistId, song -> viewModel.addSongToPlaylist(playlistId, song) },
            onCreatePlaylist = { name ->
                val p = viewModel.createPlaylist(name)
                viewModel.addSongToPlaylist(p.id, track)
            },
            onDismiss = { selectedTrackForPlaylist = null }
        )
    }
}
