package com.example.spotifly.data.prefs

import androidx.core.content.edit
import android.content.Context
import android.content.SharedPreferences
import com.example.spotifly.model.Album
import com.example.spotifly.model.Artist
import com.example.spotifly.model.LibraryLayout
import com.example.spotifly.model.RepeatMode
import com.example.spotifly.model.Song
import com.example.spotifly.model.StreamQuality
import com.example.spotifly.model.ThemeMode
import com.example.spotifly.theme.AccentTheme
import com.example.spotifly.theme.AccentThemes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Single source of truth for everything the user expects to survive a restart:
 * liked songs, listening history, the last playback session, and appearance settings.
 */
class UserPreferencesManager private constructor(context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val favoritesFile = File(context.filesDir, "favorites.json")
    private val recentlyPlayedFile = File(context.filesDir, "recently_played.json")
    private val savedAlbumsFile = File(context.filesDir, "saved_albums.json")
    private val followedArtistsFile = File(context.filesDir, "followed_artists.json")
    private val sessionFile = File(context.filesDir, "last_session.json")
    private val fileMutex = Mutex()

    // ---- Simple settings ----

    private val _accentName = MutableStateFlow(prefs.getString(KEY_ACCENT, AccentThemes.first().name) ?: AccentThemes.first().name)
    val accentName: StateFlow<String> = _accentName.asStateFlow()

    val accent: AccentTheme
        get() = AccentThemes.find { it.name == _accentName.value } ?: AccentThemes.first()

    private val _autoplayEnabled = MutableStateFlow(prefs.getBoolean(KEY_AUTOPLAY, true))
    val autoplayEnabled: StateFlow<Boolean> = _autoplayEnabled.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(prefs.getFloat(KEY_SPEED, 1.0f))
    val playbackSpeedPref: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _shuffleEnabled = MutableStateFlow(prefs.getBoolean(KEY_SHUFFLE, false))
    val shuffleEnabledPref: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    private val _repeatModePref = MutableStateFlow(
        runCatching { RepeatMode.valueOf(prefs.getString(KEY_REPEAT, RepeatMode.OFF.name) ?: RepeatMode.OFF.name) }
            .getOrDefault(RepeatMode.OFF)
    )
    val repeatModePref: StateFlow<RepeatMode> = _repeatModePref.asStateFlow()

    fun setAccent(name: String) {
        _accentName.value = name
        prefs.edit { putString(KEY_ACCENT, name) }
    }

    fun setAutoplay(enabled: Boolean) {
        _autoplayEnabled.value = enabled
        prefs.edit { putBoolean(KEY_AUTOPLAY, enabled) }
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        prefs.edit { putFloat(KEY_SPEED, speed) }
    }

    fun setShuffleEnabled(enabled: Boolean) {
        _shuffleEnabled.value = enabled
        prefs.edit { putBoolean(KEY_SHUFFLE, enabled) }
    }

    fun setRepeatMode(mode: RepeatMode) {
        _repeatModePref.value = mode
        prefs.edit { putString(KEY_REPEAT, mode.name) }
    }

    // ---- Library & app settings ----

    private val _libraryLayout = MutableStateFlow(
        enumValue(prefs.getString(KEY_LIBRARY_LAYOUT, null), LibraryLayout.LIST)
    )
    val libraryLayout: StateFlow<LibraryLayout> = _libraryLayout.asStateFlow()

    private val _themeMode = MutableStateFlow(
        enumValue(prefs.getString(KEY_THEME_MODE, null), ThemeMode.DARK)
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _hideExplicit = MutableStateFlow(prefs.getBoolean(KEY_HIDE_EXPLICIT, false))
    val hideExplicit: StateFlow<Boolean> = _hideExplicit.asStateFlow()

    private val _streamQuality = MutableStateFlow(
        enumValue(prefs.getString(KEY_STREAM_QUALITY, null), StreamQuality.AUTO)
    )
    val streamQuality: StateFlow<StreamQuality> = _streamQuality.asStateFlow()

    private val _dataSaver = MutableStateFlow(prefs.getBoolean(KEY_DATA_SAVER, false))
    val dataSaver: StateFlow<Boolean> = _dataSaver.asStateFlow()

    private val _offlineMode = MutableStateFlow(prefs.getBoolean(KEY_OFFLINE_MODE, false))
    val offlineMode: StateFlow<Boolean> = _offlineMode.asStateFlow()

    /** Playlists the user pinned to the top of the library, in pin order. */
    private val _pinnedPlaylistIds = MutableStateFlow(readStringList(KEY_PINNED_PLAYLISTS))
    val pinnedPlaylistIds: StateFlow<List<String>> = _pinnedPlaylistIds.asStateFlow()

    fun setLibraryLayout(layout: LibraryLayout) {
        _libraryLayout.value = layout
        prefs.edit { putString(KEY_LIBRARY_LAYOUT, layout.name) }
    }

    fun toggleLibraryLayout() = setLibraryLayout(_libraryLayout.value.toggled())

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit { putString(KEY_THEME_MODE, mode.name) }
    }

    fun setHideExplicit(hidden: Boolean) {
        _hideExplicit.value = hidden
        prefs.edit { putBoolean(KEY_HIDE_EXPLICIT, hidden) }
    }

    fun setStreamQuality(quality: StreamQuality) {
        _streamQuality.value = quality
        prefs.edit { putString(KEY_STREAM_QUALITY, quality.name) }
    }

    fun setDataSaver(enabled: Boolean) {
        _dataSaver.value = enabled
        prefs.edit { putBoolean(KEY_DATA_SAVER, enabled) }
    }

    fun setOfflineMode(enabled: Boolean) {
        _offlineMode.value = enabled
        prefs.edit { putBoolean(KEY_OFFLINE_MODE, enabled) }
    }

    fun togglePinnedPlaylist(playlistId: String) {
        val updated = _pinnedPlaylistIds.value.toMutableList()
        if (!updated.remove(playlistId)) updated.add(playlistId)
        _pinnedPlaylistIds.value = updated
        prefs.edit { putString(KEY_PINNED_PLAYLISTS, json.encodeToString(updated)) }
    }

    fun isPlaylistPinned(playlistId: String): Boolean =
        _pinnedPlaylistIds.value.contains(playlistId)

    // ---- Favorites (persisted as full songs so liked tracks survive restarts) ----

    private val _favoriteSongs = MutableStateFlow<List<Song>>(emptyList())
    val favoriteSongs: StateFlow<List<Song>> = _favoriteSongs.asStateFlow()

    val favoriteIds: Set<String>
        get() = _favoriteSongs.value.map { it.id }.toSet()

    fun isFavorite(songId: String): Boolean = _favoriteSongs.value.any { it.id == songId }

    fun toggleFavorite(song: Song) {
        _favoriteSongs.update { current ->
            if (current.any { it.id == song.id }) {
                current.filterNot { it.id == song.id }
            } else {
                listOf(song) + current
            }
        }
        persistSongs(favoritesFile) { _favoriteSongs.value }
    }

    // ---- Saved albums / followed artists ----
    // Stored as full objects, like favorites, so a saved album still has a name even
    // when the catalogue is unreachable and the library scan has not run yet.

    private val _savedAlbums = MutableStateFlow<List<Album>>(emptyList())
    val savedAlbums: StateFlow<List<Album>> = _savedAlbums.asStateFlow()

    val savedAlbumIds: Set<String>
        get() = _savedAlbums.value.map { it.id }.toSet()

    fun isAlbumSaved(albumId: String): Boolean = _savedAlbums.value.any { it.id == albumId }

    fun toggleSavedAlbum(album: Album) {
        _savedAlbums.update { current ->
            if (current.any { it.id == album.id }) {
                current.filterNot { it.id == album.id }
            } else {
                listOf(album) + current
            }
        }
        persistList(savedAlbumsFile) { _savedAlbums.value }
    }

    private val _followedArtists = MutableStateFlow<List<Artist>>(emptyList())
    val followedArtists: StateFlow<List<Artist>> = _followedArtists.asStateFlow()

    val followedArtistIds: Set<String>
        get() = _followedArtists.value.map { it.id }.toSet()

    fun isArtistFollowed(artistId: String): Boolean =
        _followedArtists.value.any { it.id == artistId }

    fun toggleFollowedArtist(artist: Artist) {
        _followedArtists.update { current ->
            if (current.any { it.id == artist.id }) {
                current.filterNot { it.id == artist.id }
            } else {
                listOf(artist) + current
            }
        }
        persistList(followedArtistsFile) { _followedArtists.value }
    }

    // ---- Recently played ----

    private val _recentlyPlayed = MutableStateFlow<List<Song>>(emptyList())
    val recentlyPlayed: StateFlow<List<Song>> = _recentlyPlayed.asStateFlow()

    init {
        // Reading + decoding JSON on the main thread stalls cold start, so hydrate
        // on IO and merge by id so a toggle made meanwhile is never lost.
        scope.launch {
            val storedFavorites = loadSongs(favoritesFile)
            _favoriteSongs.update { current -> mergeById(current, storedFavorites) }
            val storedRecent = loadSongs(recentlyPlayedFile)
            _recentlyPlayed.update { current -> mergeById(current, storedRecent) }
            val storedAlbums = loadList<Album>(savedAlbumsFile)
            _savedAlbums.update { current -> mergeByKey(current, storedAlbums) { it.id } }
            val storedArtists = loadList<Artist>(followedArtistsFile)
            _followedArtists.update { current -> mergeByKey(current, storedArtists) { it.id } }
        }
    }

    private fun mergeById(preferred: List<Song>, stored: List<Song>): List<Song> {
        if (stored.isEmpty()) return preferred
        return preferred + stored.filterNot { song -> preferred.any { it.id == song.id } }
    }

    fun recordPlayed(song: Song) {
        _recentlyPlayed.update { current ->
            (listOf(song) + current.filterNot { it.id == song.id }).take(MAX_RECENT)
        }
        persistSongs(recentlyPlayedFile) { _recentlyPlayed.value }
    }

    fun clearRecentlyPlayed() {
        _recentlyPlayed.value = emptyList()
        persistSongs(recentlyPlayedFile) { _recentlyPlayed.value }
    }

    // ---- Last playback session ----

    @Serializable
    data class SavedSession(
        val songs: List<Song> = emptyList(),
        val index: Int = 0,
        val positionMs: Long = 0L,
        val savedAt: Long = 0L
    )

    fun saveSession(songs: List<Song>, index: Int, positionMs: Long) {
        if (songs.isEmpty()) return
        val session = SavedSession(songs, index.coerceIn(0, songs.lastIndex), positionMs, System.currentTimeMillis())
        scope.launch {
            fileMutex.withLock {
                runCatching { sessionFile.writeText(json.encodeToString(session)) }
            }
        }
    }

    suspend fun loadSession(): SavedSession? = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            runCatching {
                if (!sessionFile.exists()) return@runCatching null
                val content = sessionFile.readText()
                if (content.isBlank()) return@runCatching null
                json.decodeFromString<SavedSession>(content)
            }.getOrNull()
        }
    }

    fun clearSession() {
        scope.launch {
            fileMutex.withLock {
                runCatching { sessionFile.delete() }
            }
        }
    }

    // ---- Helpers ----

    private fun loadSongs(file: File): List<Song> = loadList(file)

    private inline fun <reified T> loadList(file: File): List<T> {
        return runCatching {
            if (!file.exists()) return@runCatching emptyList()
            val content = file.readText()
            if (content.isBlank()) emptyList() else json.decodeFromString<List<T>>(content)
        }.getOrDefault(emptyList())
    }

    // The list is read at write time, so a queued persist always saves the newest state
    private fun persistSongs(file: File, snapshot: () -> List<Song>) = persistList(file, snapshot)

    private inline fun <reified T> persistList(file: File, crossinline snapshot: () -> List<T>) {
        scope.launch {
            fileMutex.withLock {
                runCatching { file.writeText(json.encodeToString(snapshot())) }
            }
        }
    }

    private fun <T> mergeByKey(
        preferred: List<T>,
        stored: List<T>,
        key: (T) -> String
    ): List<T> {
        if (stored.isEmpty()) return preferred
        return preferred + stored.filterNot { item -> preferred.any { key(it) == key(item) } }
    }

    private inline fun <reified T : Enum<T>> enumValue(raw: String?, fallback: T): T =
        runCatching { enumValueOf<T>(raw ?: "") }.getOrDefault(fallback)

    private fun readStringList(key: String): List<String> = runCatching {
        val raw = prefs.getString(key, null) ?: return@runCatching emptyList()
        if (raw.isBlank()) emptyList() else json.decodeFromString<List<String>>(raw)
    }.getOrDefault(emptyList())

    companion object {
        private const val PREFS_NAME = "spotifly_user_prefs"
        private const val KEY_ACCENT = "accent_name"
        private const val KEY_AUTOPLAY = "autoplay_enabled"
        private const val KEY_SPEED = "playback_speed"
        private const val KEY_SHUFFLE = "shuffle_enabled"
        private const val KEY_REPEAT = "repeat_mode"
        private const val KEY_LIBRARY_LAYOUT = "library_layout"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_HIDE_EXPLICIT = "hide_explicit"
        private const val KEY_STREAM_QUALITY = "stream_quality"
        private const val KEY_DATA_SAVER = "data_saver"
        private const val KEY_OFFLINE_MODE = "offline_mode"
        private const val KEY_PINNED_PLAYLISTS = "pinned_playlists"
        private const val MAX_RECENT = 50

        @Volatile
        private var instance: UserPreferencesManager? = null

        fun getInstance(context: Context): UserPreferencesManager {
            return instance ?: synchronized(this) {
                instance ?: UserPreferencesManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
