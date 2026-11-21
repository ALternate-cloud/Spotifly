package com.example.spotifly.data.catalog

import com.example.spotifly.model.Album
import com.example.spotifly.model.Artist
import com.example.spotifly.model.Song

/**
 * Groups a flat track list into the albums and artists Spotify's navigation model
 * exposes. Everything here works from data the app already has — the device scan,
 * downloads, likes, playlists and search results — so album and artist screens work
 * with no extra backend.
 */
object LibraryGrouping {

    fun albums(songs: List<Song>): List<Album> =
        songs
            .filter { it.album.isNotBlank() }
            .groupBy { it.albumKey }
            .map { (key, tracks) ->
                val ordered = tracks.sortedWith(
                    compareBy<Song> { it.discNumber }.thenBy { it.trackNumber }.thenBy { it.title }
                )
                val lead = ordered.first()
                Album(
                    id = "album_$key",
                    title = lead.album,
                    artist = lead.artist,
                    artworkUrl = ordered.firstNotNullOfOrNull { it.artworkUrl },
                    year = lead.year,
                    songs = ordered.distinctBy { it.id },
                    isLocal = ordered.any { it.isLocal }
                )
            }
            .sortedWith(compareByDescending<Album> { it.year }.thenBy { it.title.lowercase() })

    fun artists(songs: List<Song>): List<Artist> =
        songs
            .filter { it.artist.isNotBlank() }
            .groupBy { it.artistKey }
            .map { (key, tracks) ->
                val ordered = tracks.sortedBy { it.title.lowercase() }
                Artist(
                    id = "artist_$key",
                    name = ordered.first().artist.split(",").first().trim(),
                    artworkUrl = ordered.firstNotNullOfOrNull { it.artworkUrl },
                    songs = ordered.distinctBy { it.id },
                    albums = albums(ordered),
                    isLocal = ordered.any { it.isLocal }
                )
            }
            .sortedBy { it.name.lowercase() }

    fun albumFor(song: Song, library: List<Song>): Album =
        albums((library + song).distinctBy { it.id }).firstOrNull { it.id == "album_${song.albumKey}" }
            ?: Album(
                id = "album_${song.albumKey}",
                title = song.album.ifBlank { song.title },
                artist = song.artist,
                artworkUrl = song.artworkUrl,
                year = song.year,
                songs = listOf(song),
                isLocal = song.isLocal
            )

    fun artistFor(song: Song, library: List<Song>): Artist =
        artists((library + song).distinctBy { it.id }).firstOrNull { it.id == "artist_${song.artistKey}" }
            ?: Artist(
                id = "artist_${song.artistKey}",
                name = song.artist.split(",").first().trim(),
                artworkUrl = song.artworkUrl,
                songs = listOf(song),
                albums = emptyList(),
                isLocal = song.isLocal
            )

    /** Case-insensitive track search used by library and playlist filtering. */
    fun matches(song: Song, query: String): Boolean {
        if (query.isBlank()) return true
        val q = query.trim()
        return song.title.contains(q, ignoreCase = true) ||
            song.artist.contains(q, ignoreCase = true) ||
            song.album.contains(q, ignoreCase = true)
    }
}
