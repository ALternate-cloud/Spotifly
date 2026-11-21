package com.example.spotifly.data.catalog

import com.example.spotifly.model.Album
import com.example.spotifly.model.Artist
import com.example.spotifly.model.Playlist
import com.example.spotifly.model.Song

/**
 * Sort modes are pure list transforms so the ordering rules can be unit tested
 * without Compose. Each enum owns its label and its cycle, which is what the
 * single sort button in the library header shows.
 */
enum class LibrarySort(val label: String) {
    RECENT("Recents"),
    ALPHABETICAL("A-Z"),
    BY_ARTIST("Artist");

    fun next(): LibrarySort = entries[(ordinal + 1) % entries.size]
}

enum class PlaylistListSort(val label: String) {
    RECENT("Recents"),
    ALPHABETICAL("A-Z"),
    MOST_TRACKS("Most tracks");

    fun next(): PlaylistListSort = entries[(ordinal + 1) % entries.size]
}

/** Track order inside a playlist. CUSTOM keeps the user's drag-and-drop order. */
enum class PlaylistTrackSort(val label: String) {
    CUSTOM("Custom order"),
    TITLE("Title"),
    ARTIST("Artist"),
    ALBUM("Album"),
    DURATION("Duration");

    fun next(): PlaylistTrackSort = entries[(ordinal + 1) % entries.size]
}

object LibrarySorting {

    fun songs(songs: List<Song>, sort: LibrarySort): List<Song> = when (sort) {
        LibrarySort.RECENT -> songs
        LibrarySort.ALPHABETICAL -> songs.sortedBy { it.title.lowercase() }
        LibrarySort.BY_ARTIST -> songs.sortedWith(
            compareBy({ it.artist.lowercase() }, { it.title.lowercase() })
        )
    }

    fun albums(albums: List<Album>, sort: LibrarySort): List<Album> = when (sort) {
        LibrarySort.RECENT -> albums.sortedWith(
            compareByDescending<Album> { it.year }.thenBy { it.title.lowercase() }
        )
        LibrarySort.ALPHABETICAL -> albums.sortedBy { it.title.lowercase() }
        LibrarySort.BY_ARTIST -> albums.sortedWith(
            compareBy({ it.artist.lowercase() }, { it.title.lowercase() })
        )
    }

    fun artists(artists: List<Artist>, sort: LibrarySort): List<Artist> = when (sort) {
        // "Recent" for artists means the ones with the deepest catalogue first;
        // there is no per-artist timestamp in a local library.
        LibrarySort.RECENT -> artists.sortedByDescending { it.trackCount }
        LibrarySort.ALPHABETICAL, LibrarySort.BY_ARTIST -> artists.sortedBy { it.name.lowercase() }
    }

    fun playlists(playlists: List<Playlist>, sort: PlaylistListSort): List<Playlist> = when (sort) {
        PlaylistListSort.RECENT -> playlists.sortedByDescending {
            if (it.updatedAt > 0L) it.updatedAt else it.createdAt
        }
        PlaylistListSort.ALPHABETICAL -> playlists.sortedBy { it.name.lowercase() }
        PlaylistListSort.MOST_TRACKS -> playlists.sortedByDescending { it.songs.size }
    }

    fun playlistTracks(songs: List<Song>, sort: PlaylistTrackSort): List<Song> = when (sort) {
        PlaylistTrackSort.CUSTOM -> songs
        PlaylistTrackSort.TITLE -> songs.sortedBy { it.title.lowercase() }
        PlaylistTrackSort.ARTIST -> songs.sortedWith(
            compareBy({ it.artist.lowercase() }, { it.album.lowercase() }, { it.title.lowercase() })
        )
        PlaylistTrackSort.ALBUM -> songs.sortedWith(
            compareBy(
                { it.album.lowercase() },
                { it.discNumber },
                { it.trackNumber },
                { it.title.lowercase() }
            )
        )
        PlaylistTrackSort.DURATION -> songs.sortedByDescending { it.durationMs }
    }

    /** Pinned items keep their own order so re-pinning does not reshuffle the list. */
    fun <T> pinnedFirst(items: List<T>, id: (T) -> String, pinnedIds: List<String>): List<T> {
        if (pinnedIds.isEmpty()) return items
        val pinned = pinnedIds.mapNotNull { idToFind -> items.find { id(it) == idToFind } }
        val pinnedSet = pinned.map { id(it) }.toSet()
        return pinned + items.filterNot { id(it) in pinnedSet }
    }

    fun matchesPlaylist(playlist: Playlist, query: String): Boolean {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return true
        return playlist.name.contains(trimmed, ignoreCase = true) ||
            playlist.description.contains(trimmed, ignoreCase = true)
    }

    fun filterPlaylistTracks(songs: List<Song>, query: String): List<Song> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return songs
        return songs.filter {
            it.title.contains(trimmed, ignoreCase = true) ||
                it.artist.contains(trimmed, ignoreCase = true) ||
                it.album.contains(trimmed, ignoreCase = true)
        }
    }
}
