package com.example.spotifly.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.spotifly.data.history.SearchHistoryManager
import com.example.spotifly.data.catalog.LibraryGrouping
import com.example.spotifly.data.local.LocalMusicRepository
import com.example.spotifly.data.offline.OfflineMusicManager
import com.example.spotifly.data.online.OnlineMusicService
import com.example.spotifly.data.playlist.PlaylistRepository
import com.example.spotifly.data.prefs.UserPreferencesManager
import com.example.spotifly.data.stats.ListeningStatsManager
import android.net.Uri
import coil.Coil
import com.example.spotifly.model.Album
import com.example.spotifly.model.Artist
import com.example.spotifly.model.DownloadStatus
import com.example.spotifly.model.LibraryLayout
import com.example.spotifly.model.PlaybackState
import com.example.spotifly.model.Playlist
import com.example.spotifly.model.Song
import com.example.spotifly.model.StreamQuality
import com.example.spotifly.model.ThemeMode
import com.example.spotifly.player.MusicController
import com.example.spotifly.theme.AccentTheme
import com.example.spotifly.theme.AccentThemes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Song radio stays finite: a queue long enough to feel endless, short enough to finish. */
private const val RADIO_MAX_TRACKS = 40

class MainScreenViewModel(application: Application) : AndroidViewModel(application) {

    private val musicController = MusicController.getInstance(application)
    private val localMusicRepository = LocalMusicRepository(application)
    private val searchHistoryManager = SearchHistoryManager.getInstance(application)
    private val playlistRepository = PlaylistRepository.getInstance(application)
    private val offlineMusicManager = OfflineMusicManager.getInstance(application)
    private val userPreferences = UserPreferencesManager.getInstance(application)
    private val statsManager = ListeningStatsManager.getInstance(application)

    val playbackState: StateFlow<PlaybackState> = musicController.playbackState
    val favorites: StateFlow<Set<String>> = musicController.favorites
    val sleepTimerEndMs: StateFlow<Long?> = musicController.sleepTimerEndMs
    val sleepAtEndOfTrack: StateFlow<Boolean> = musicController.sleepAtEndOfTrack

    val recentSearches: StateFlow<List<String>> = searchHistoryManager.recentSearches
    val playlists: StateFlow<List<Playlist>> = playlistRepository.playlists
    val downloadedSongs: StateFlow<List<Song>> = offlineMusicManager.downloadedSongs
    val downloadStatus: StateFlow<Map<String, DownloadStatus>> = offlineMusicManager.downloadStatus
    val downloadProgress: StateFlow<Map<String, Int>> = offlineMusicManager.downloadProgress

    val recentlyPlayed: StateFlow<List<Song>> = userPreferences.recentlyPlayed
    val favoriteSongs: StateFlow<List<Song>> = userPreferences.favoriteSongs
    val stats: StateFlow<com.example.spotifly.data.stats.ListeningStats> = statsManager.stats

    val savedAlbums: StateFlow<List<Album>> = userPreferences.savedAlbums
    val followedArtists: StateFlow<List<Artist>> = userPreferences.followedArtists
    val pinnedPlaylistIds: StateFlow<List<String>> = userPreferences.pinnedPlaylistIds
    val libraryLayout: StateFlow<LibraryLayout> = userPreferences.libraryLayout
    val themeMode: StateFlow<ThemeMode> = userPreferences.themeMode
    val hideExplicit: StateFlow<Boolean> = userPreferences.hideExplicit
    val streamQuality: StateFlow<StreamQuality> = userPreferences.streamQuality
    val dataSaver: StateFlow<Boolean> = userPreferences.dataSaver
    val offlineMode: StateFlow<Boolean> = userPreferences.offlineMode

    private val _openAlbum = MutableStateFlow<Album?>(null)
    val openAlbum: StateFlow<Album?> = _openAlbum.asStateFlow()

    private val _openArtist = MutableStateFlow<Artist?>(null)
    val openArtist: StateFlow<Artist?> = _openArtist.asStateFlow()

    val accentTheme: StateFlow<AccentTheme> = userPreferences.accentName
        .map { name -> AccentThemes.find { it.name == name } ?: AccentThemes.first() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, userPreferences.accent)

    private val _localSongs = MutableStateFlow<List<Song>>(emptyList())
    val localSongs: StateFlow<List<Song>> = _localSongs.asStateFlow()

    // Storage readout for settings; measured off the main thread because it stats files
    private val _downloadedBytes = MutableStateFlow(0L)
    val downloadedBytes: StateFlow<Long> = _downloadedBytes.asStateFlow()

    private val _cacheBytes = MutableStateFlow(0L)
    val cacheBytes: StateFlow<Long> = _cacheBytes.asStateFlow()

    // Aggregates all known songs for searching and library views. Offline mode drops
    // the streaming catalogue so the library only offers things that can actually play.
    val allSongs: StateFlow<List<Song>> = combine(
        _localSongs,
        downloadedSongs,
        playbackState,
        hideExplicit,
        offlineMode
    ) { local, downloaded, state, hideExplicitTracks, offline ->
        val catalogue = if (offline) emptyList() else OnlineMusicService.trendingHits
        (local + downloaded + catalogue + state.queue)
            .distinctBy { it.id }
            .filterNot { hideExplicitTracks && it.isExplicit }
    }.stateIn(viewModelScope, SharingStarted.Lazily, OnlineMusicService.trendingHits)

    // Albums and artists are grouped from everything the app knows about, which is
    // how Spotify's library surfaces them without a separate index.
    val albums: StateFlow<List<Album>> = allSongs
        .map { LibraryGrouping.albums(it) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val artists: StateFlow<List<Artist>> = allSongs
        .map { LibraryGrouping.artists(it) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        scanLocalSongs()
        observeDownloadedBytes()
        refreshCacheBytes()
    }

    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    private fun refreshCacheBytes() {
        viewModelScope.launch(Dispatchers.IO) {
            val loader = Coil.imageLoader(getApplication())
            val disk = loader.diskCache?.size ?: 0L
            val memory = loader.memoryCache?.size?.toLong() ?: 0L
            _cacheBytes.value = disk + memory
        }
    }

    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    fun clearImageCache() {
        viewModelScope.launch(Dispatchers.IO) {
            val loader = Coil.imageLoader(getApplication())
            runCatching { loader.diskCache?.clear() }
            loader.memoryCache?.clear()
            _cacheBytes.value = loader.diskCache?.size ?: 0L
        }
    }

    private fun observeDownloadedBytes() {
        viewModelScope.launch {
            offlineMusicManager.downloadedSongs.collect { songs ->
                _downloadedBytes.value = withContext(Dispatchers.IO) {
                    songs.sumOf { song -> song.localFilePath?.let { File(it).length() } ?: 0L }
                }
            }
        }
    }

    fun scanLocalSongs() {
        viewModelScope.launch {
            val songs = localMusicRepository.getLocalSongs()
            _localSongs.value = songs
        }
    }

    fun playSong(song: Song) {
        musicController.playSong(song)
    }

    fun playQueue(songs: List<Song>, startIndex: Int) {
        musicController.playQueue(songs, startIndex)
    }

    /**
     * Starts an endless Spotify-style radio queue based on the seed song.
     */
    fun startSongRadio(song: Song) {
        if (!song.isPodcast) musicController.playSong(song, startRadio = true)
    }

    /** Artist radio: starts playback of the artist's top song and generates an endless queue. */
    fun startArtistRadio(artist: Artist) {
        viewModelScope.launch {
            val songs = if (artist.songs.isNotEmpty() || offlineMode.value) {
                artist.songs
            } else {
                withContext(Dispatchers.IO) {
                    OnlineMusicService.getArtistSongs(artist.id, artist.name)
                }
            }
            val first = songs.firstOrNull { !it.isPodcast }
            if (first != null) {
                startSongRadio(first)
            }
        }
    }

    /** Shuffle-plays the user's entire known library (device + downloads + streaming catalog). */
    fun shuffleAll() {
        val everything = allSongs.value.shuffled()
        if (everything.isNotEmpty()) {
            musicController.playQueue(everything, 0)
        }
    }

    fun togglePlayPause() {
        musicController.togglePlayPause()
    }

    fun seekTo(positionMs: Long) {
        musicController.seekTo(positionMs)
    }

    fun skipNext() {
        musicController.skipNext()
    }

    fun skipPrevious() {
        musicController.skipPrevious()
    }

    fun toggleShuffle() {
        musicController.toggleShuffle()
    }

    fun toggleRepeat() {
        musicController.toggleRepeatMode()
    }

    fun toggleAutoplay() {
        musicController.toggleAutoplay()
    }

    fun setPlaybackSpeed(speed: Float) {
        musicController.setPlaybackSpeed(speed)
    }

    fun toggleFavorite(song: Song) {
        musicController.toggleFavorite(song)
    }

    fun addToQueue(song: Song) {
        musicController.addToQueue(song)
    }

    fun playNext(song: Song) {
        musicController.playNext(song)
    }

    // ---- Album / artist detail ----

    /** Opens an album; if the catalogue gave us metadata only, its tracks load async. */
    fun openAlbum(album: Album) {
        _openAlbum.value = album
        if (album.songs.size <= 1 && !offlineMode.value) {
            viewModelScope.launch {
                val songs = withContext(Dispatchers.IO) {
                    OnlineMusicService.getAlbumSongs(album.id, album.title)
                }
                if (songs.isEmpty()) return@launch
                _openAlbum.value?.let { current ->
                    if (current.id == album.id) _openAlbum.value = current.copy(songs = songs)
                }
            }
        }
    }

    fun closeAlbum() {
        _openAlbum.value = null
    }

    fun openArtist(artist: Artist) {
        _openArtist.value = artist
        if (artist.songs.isEmpty() && !offlineMode.value) {
            viewModelScope.launch {
                val songs = withContext(Dispatchers.IO) {
                    OnlineMusicService.getArtistSongs(artist.id, artist.name)
                }
                if (songs.isEmpty()) return@launch
                _openArtist.value?.let { current ->
                    if (current.id == artist.id) {
                        _openArtist.value = current.copy(
                            songs = songs,
                            albums = LibraryGrouping.albums(songs)
                        )
                    }
                }
            }
        }
    }

    fun closeArtist() {
        _openArtist.value = null
    }

    /**
     * Deep-link entry: the linked album may not be part of any current grouping
     * yet (for example a saved album whose tracks are not in the library).
     */
    fun openAlbumById(albumId: String) {
        val existing = albums.value.firstOrNull { it.id == albumId }
            ?: savedAlbums.value.firstOrNull { it.id == albumId }
        openAlbum(existing ?: Album(id = albumId, title = "Album", artist = ""))
    }

    fun openArtistById(artistId: String) {
        val existing = artists.value.firstOrNull { it.id == artistId }
            ?: followedArtists.value.firstOrNull { it.id == artistId }
        openArtist(existing ?: Artist(id = artistId, name = "Artist"))
    }

    fun moveQueueItem(from: Int, to: Int) {
        musicController.moveQueueItem(from, to)
    }

    fun removeFromQueue(index: Int) {
        musicController.removeFromQueue(index)
    }

    /** Lets an identical error message raise a new snackbar instead of being swallowed. */
    fun clearPlaybackError() {
        musicController.clearPlaybackError()
    }

    // Sleep timer
    fun startSleepTimer(minutes: Int) {
        musicController.startSleepTimer(minutes)
    }

    fun startSleepTimerAtEndOfTrack() {
        musicController.startSleepTimerAtEndOfTrack()
    }

    fun cancelSleepTimer() {
        musicController.cancelSleepTimer()
    }

    // Search History actions
    fun addSearchQuery(query: String) {
        searchHistoryManager.addSearch(query)
    }

    fun removeSearchQuery(query: String) {
        searchHistoryManager.removeSearch(query)
    }

    fun clearSearchHistory() {
        searchHistoryManager.clearAll()
    }

    // Playlist actions
    fun saveQueueAsPlaylist(name: String, songs: List<Song>): Playlist {
        val playlistSongs = songs.map { song ->
            // Resolved YouTube audio URLs expire. Keep the catalog ID so playback can resolve a fresh URL.
            if (!song.isLocal && song.sourceIds.ytmusic != null) song.copy(streamUrl = null) else song
        }
        return playlistRepository.createPlaylist(name, playlistSongs)
    }

    fun createPlaylist(name: String): Playlist {
        return playlistRepository.createPlaylist(name)
    }

    fun deletePlaylist(playlistId: String) {
        playlistRepository.deletePlaylist(playlistId)
    }

    fun renamePlaylist(playlistId: String, newName: String) {
        playlistRepository.renamePlaylist(playlistId, newName)
    }

    fun addSongToPlaylist(playlistId: String, song: Song) {
        playlistRepository.addSongToPlaylist(playlistId, song)
    }

    fun removeSongFromPlaylist(playlistId: String, songId: String) {
        playlistRepository.removeSongFromPlaylist(playlistId, songId)
    }

    fun moveSongInPlaylist(playlistId: String, from: Int, to: Int) {
        playlistRepository.moveSong(playlistId, from, to)
    }

    fun updatePlaylistDescription(playlistId: String, description: String) {
        playlistRepository.updateDescription(playlistId, description)
    }

    fun setPlaylistCover(playlistId: String, uri: Uri) {
        playlistRepository.setCoverFromUri(playlistId, uri)
    }

    fun removePlaylistCover(playlistId: String) {
        playlistRepository.removeCover(playlistId)
    }

    fun togglePlaylistPinned(playlistId: String) {
        userPreferences.togglePinnedPlaylist(playlistId)
    }

    // ---- Saved albums, followed artists, library layout ----

    fun toggleSavedAlbum(album: Album) {
        userPreferences.toggleSavedAlbum(album)
    }

    fun toggleFollowedArtist(artist: Artist) {
        userPreferences.toggleFollowedArtist(artist)
    }

    fun toggleLibraryLayout() {
        userPreferences.toggleLibraryLayout()
    }

    // ---- App settings ----

    fun setThemeMode(mode: ThemeMode) {
        userPreferences.setThemeMode(mode)
    }

    fun setHideExplicit(hidden: Boolean) {
        userPreferences.setHideExplicit(hidden)
    }

    fun setStreamQuality(quality: StreamQuality) {
        userPreferences.setStreamQuality(quality)
    }

    fun setDataSaver(enabled: Boolean) {
        userPreferences.setDataSaver(enabled)
    }

    fun setOfflineMode(enabled: Boolean) {
        userPreferences.setOfflineMode(enabled)
    }

    // ---- Stats ----

    fun clearStats() {
        statsManager.clear()
    }

    // Offline download actions
    fun downloadSong(song: Song) {
        offlineMusicManager.downloadSong(song)
    }

    /** Album/playlist "download all": queue every downloadable track at once. */
    fun downloadSongs(songs: List<Song>) {
        songs.filter { !it.isLocal && !it.streamUrl.isNullOrBlank() }
            .forEach { offlineMusicManager.downloadSong(it) }
    }

    fun cancelDownload(songId: String) {
        offlineMusicManager.cancelDownload(songId)
    }

    fun removeDownload(songId: String) {
        offlineMusicManager.removeDownload(songId)
    }

    fun isSongDownloaded(songId: String): Boolean {
        return offlineMusicManager.isSongDownloaded(songId)
    }

    // Appearance & data management
    fun setAccent(name: String) {
        userPreferences.setAccent(name)
    }

    fun clearRecentlyPlayed() {
        userPreferences.clearRecentlyPlayed()
    }
}
