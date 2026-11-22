package com.example.spotifly.data.playlist

import android.content.Context
import android.net.Uri
import com.example.spotifly.model.Playlist
import com.example.spotifly.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class PlaylistRepository private constructor(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val store = com.example.spotifly.data.cloud.LibraryProfileStore.getInstance(context)
    val playlists: StateFlow<List<Playlist>> = store.playlists
    val hydration = store.hydration

    fun createPlaylist(name: String, songs: List<Song> = emptyList()): Playlist {
        val trimmed = name.trim().ifEmpty { "My Playlist #${playlists.value.size + 1}" }
        val newPlaylist = Playlist(
            id = "playlist_${UUID.randomUUID()}",
            name = trimmed,
            createdAt = System.currentTimeMillis(),
            songs = songs.toList()
        )
        store.createPlaylist(newPlaylist)
        return newPlaylist
    }

    fun deletePlaylist(playlistId: String) {
        store.deletePlaylist(playlistId)
    }

    /**
     * Copies a cover the user picked into app storage, then records its path.
     * Copying instead of keeping the content URI means the cover survives
     * permission loss, reboots, and the source image being deleted.
     */
    fun setCoverFromUri(playlistId: String, uri: Uri) {
        val token = store.sessionToken()
        scope.launch {
            val copied = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(context.filesDir, "playlist_covers").apply { mkdirs() }
                    val target = File(dir, "cover_${UUID.randomUUID()}.jpg")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                    target.takeIf { it.length() > 0L }
                }.getOrNull()
            } ?: return@launch
            store.updatePlaylist(playlistId, token) { it.copy(coverPath = copied.absolutePath, updatedAt = System.currentTimeMillis()) }
        }
    }

    fun removeCover(playlistId: String) {
        updatePlaylist(playlistId) { it.copy(coverPath = null) }
    }

    private fun updatePlaylist(playlistId: String, transform: (Playlist) -> Playlist) {
        store.updatePlaylist(playlistId) { transform(it).copy(updatedAt = System.currentTimeMillis()) }
    }
    fun renamePlaylist(playlistId: String, newName: String) {
        if (newName.trim().isNotEmpty()) updatePlaylist(playlistId) { it.copy(name = newName.trim()) }
    }
    fun updateDescription(playlistId: String, description: String) = updatePlaylist(playlistId) { it.copy(description = description.trim()) }
    fun moveSong(playlistId: String, from: Int, to: Int) = updatePlaylist(playlistId) { playlist ->
        val songs = playlist.songs.toMutableList()
        if (from in songs.indices && to in songs.indices) songs.add(to, songs.removeAt(from))
        playlist.copy(songs = songs)
    }
    fun addSongToPlaylist(playlistId: String, song: Song) = updatePlaylist(playlistId) {
        if (it.songs.none { current -> current.id == song.id }) it.copy(songs = it.songs + song) else it
    }
    fun removeSongFromPlaylist(playlistId: String, songId: String) = updatePlaylist(playlistId) { it.copy(songs = it.songs.filterNot { song -> song.id == songId }) }

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
