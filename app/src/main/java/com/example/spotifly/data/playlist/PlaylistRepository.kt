package com.example.spotifly.data.playlist

import android.content.Context
import android.net.Uri
import com.example.spotifly.model.Playlist
import com.example.spotifly.model.Song
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class PlaylistRepository private constructor(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val writeMutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val playlistFile = File(context.filesDir, "playlists.json")

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    init {
        loadPlaylists()
    }

    private fun loadPlaylists() {
        scope.launch {
            try {
                if (playlistFile.exists()) {
                    val content = playlistFile.readText()
                    if (content.isNotBlank()) {
                        val list = json.decodeFromString<List<Playlist>>(content)
                        // Keep anything the user created while this read was in flight
                        _playlists.update { current ->
                            if (current.isEmpty()) list
                            else current + list.filterNot { loaded -> current.any { it.id == loaded.id } }
                        }
                        return@launch
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            // Default initial playlist if none exists
            val initial = listOf(
                Playlist(
                    id = "favorites_playlist",
                    name = "My Favorites",
                    createdAt = System.currentTimeMillis(),
                    songs = emptyList()
                )
            )
            _playlists.update { current -> if (current.isEmpty()) initial else current }
            persistCurrentPlaylists()
        }
    }

    // State is updated synchronously on the caller (main) thread; persistence always
    // writes the *latest* value so rapid mutations can never overwrite each other
    // with stale snapshots. The mutex keeps two coroutines from writing the file at once.
    private fun persistCurrentPlaylists() {
        scope.launch {
            withContext(Dispatchers.IO) {
                writeMutex.withLock {
                    try {
                        playlistFile.writeText(json.encodeToString(_playlists.value))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    fun createPlaylist(name: String, songs: List<Song> = emptyList()): Playlist {
        val trimmed = name.trim().ifEmpty { "My Playlist #${_playlists.value.size + 1}" }
        val newPlaylist = Playlist(
            id = "playlist_${UUID.randomUUID()}",
            name = trimmed,
            createdAt = System.currentTimeMillis(),
            songs = songs.toList()
        )
        _playlists.update { it + newPlaylist }
        persistCurrentPlaylists()
        return newPlaylist
    }

    fun deletePlaylist(playlistId: String) {
        val cover = _playlists.value.find { it.id == playlistId }?.coverPath
        _playlists.update { list -> list.filterNot { it.id == playlistId } }
        persistCurrentPlaylists()
        cover?.let { scope.launch { withContext(Dispatchers.IO) { runCatching { File(it).delete() } } } }
    }

    /**
     * Copies a cover the user picked into app storage, then records its path.
     * Copying instead of keeping the content URI means the cover survives
     * permission loss, reboots, and the source image being deleted.
     */
    fun setCoverFromUri(playlistId: String, uri: Uri) {
        scope.launch {
            val copied = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(context.filesDir, "playlist_covers").apply { mkdirs() }
                    val target = File(dir, "cover_${playlistId.hashCode()}.jpg")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                    target.takeIf { it.length() > 0L }
                }.getOrNull()
            } ?: return@launch
            updatePlaylist(playlistId) { it.copy(coverPath = copied.absolutePath) }
        }
    }

    fun removeCover(playlistId: String) {
        val previous = _playlists.value.find { it.id == playlistId }?.coverPath
        updatePlaylist(playlistId) { it.copy(coverPath = null) }
        previous?.let { scope.launch { withContext(Dispatchers.IO) { runCatching { File(it).delete() } } } }
    }

    private fun updatePlaylist(playlistId: String, transform: (Playlist) -> Playlist) {
        _playlists.update { list ->
            list.map {
                if (it.id == playlistId) {
                    transform(it).copy(updatedAt = System.currentTimeMillis())
                } else {
                    it
                }
            }
        }
        persistCurrentPlaylists()
    }

    fun renamePlaylist(playlistId: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        _playlists.update { list ->
            list.map {
                if (it.id == playlistId) it.copy(name = trimmed, updatedAt = System.currentTimeMillis()) else it
            }
        }
        persistCurrentPlaylists()
    }

    fun updateDescription(playlistId: String, description: String) {
        _playlists.update { list ->
            list.map {
                if (it.id == playlistId) {
                    it.copy(description = description.trim(), updatedAt = System.currentTimeMillis())
                } else {
                    it
                }
            }
        }
        persistCurrentPlaylists()
    }

    /** Reorders a track inside a playlist, matching the drag handle in Spotify. */
    fun moveSong(playlistId: String, from: Int, to: Int) {
        if (from == to) return
        _playlists.update { list ->
            list.map { playlist ->
                if (playlist.id != playlistId) return@map playlist
                val songs = playlist.songs.toMutableList()
                if (from !in songs.indices || to !in songs.indices) return@map playlist
                val moved = songs.removeAt(from)
                songs.add(to, moved)
                playlist.copy(songs = songs, updatedAt = System.currentTimeMillis())
            }
        }
        persistCurrentPlaylists()
    }

    fun addSongToPlaylist(playlistId: String, song: Song) {
        _playlists.update { list ->
            list.map { playlist ->
                if (playlist.id == playlistId && playlist.songs.none { it.id == song.id }) {
                    playlist.copy(songs = playlist.songs + song, updatedAt = System.currentTimeMillis())
                } else {
                    playlist
                }
            }
        }
        persistCurrentPlaylists()
    }

    fun removeSongFromPlaylist(playlistId: String, songId: String) {
        _playlists.update { list ->
            list.map { playlist ->
                if (playlist.id == playlistId) {
                    playlist.copy(
                        songs = playlist.songs.filterNot { it.id == songId },
                        updatedAt = System.currentTimeMillis()
                    )
                } else {
                    playlist
                }
            }
        }
        persistCurrentPlaylists()
    }

    companion object {
        // Only applicationContext is retained by this process-wide singleton.
        @android.annotation.SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: PlaylistRepository? = null

        fun getInstance(context: Context): PlaylistRepository {
            return instance ?: synchronized(this) {
                instance ?: PlaylistRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
