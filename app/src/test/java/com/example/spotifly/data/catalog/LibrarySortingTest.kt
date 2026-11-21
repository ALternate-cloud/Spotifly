package com.example.spotifly.data.catalog

import com.example.spotifly.model.Album
import com.example.spotifly.model.Artist
import com.example.spotifly.model.Playlist
import com.example.spotifly.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM tests for the sort/filter rules behind the library controls. */
class LibrarySortingTest {

    private fun song(
        id: String,
        title: String,
        artist: String = "Artist",
        album: String = "Album",
        durationMs: Long = 0L,
        trackNumber: Int = 0,
        discNumber: Int = 1
    ) = Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        trackNumber = trackNumber,
        discNumber = discNumber
    )

    private fun playlist(id: String, name: String, tracks: Int = 0, updatedAt: Long = 0L) =
        Playlist(
            id = id,
            name = name,
            songs = (0 until tracks).map { song("$id-$it", "Track $it") },
            updatedAt = updatedAt
        )

    @Test
    fun songs_alphabeticalIgnoresCase() {
        val sorted = LibrarySorting.songs(
            listOf(song("1", "beta"), song("2", "Alpha"), song("3", "Gamma")),
            LibrarySort.ALPHABETICAL
        )
        assertEquals(listOf("Alpha", "beta", "Gamma"), sorted.map { it.title })
    }

    @Test
    fun songs_byArtistThenTitle() {
        val sorted = LibrarySorting.songs(
            listOf(
                song("1", "Zed", artist = "Beta"),
                song("2", "Alpha", artist = "Beta"),
                song("3", "Mid", artist = "Alpha")
            ),
            LibrarySort.BY_ARTIST
        )
        assertEquals(listOf("Mid", "Alpha", "Zed"), sorted.map { it.title })
    }

    @Test
    fun songs_recentKeepsOriginalOrder() {
        val input = listOf(song("1", "B"), song("2", "A"))
        assertEquals(input, LibrarySorting.songs(input, LibrarySort.RECENT))
    }

    @Test
    fun albums_byArtistThenTitle() {
        val albums = listOf(
            Album(id = "1", title = "Second", artist = "Beta"),
            Album(id = "2", title = "First", artist = "Alpha"),
            Album(id = "3", title = "Another", artist = "Beta")
        )
        val sorted = LibrarySorting.albums(albums, LibrarySort.BY_ARTIST)
        assertEquals(listOf("First", "Another", "Second"), sorted.map { it.title })
    }

    @Test
    fun albums_recentOrdersByYearDescending() {
        val albums = listOf(
            Album(id = "1", title = "Old", artist = "A", year = 1999),
            Album(id = "2", title = "New", artist = "A", year = 2024)
        )
        val sorted = LibrarySorting.albums(albums, LibrarySort.RECENT)
        assertEquals(listOf("New", "Old"), sorted.map { it.title })
    }

    @Test
    fun artists_recentOrdersByCatalogueDepth() {
        val artists = listOf(
            Artist(id = "1", name = "Small", songs = listOf(song("s1", "One"))),
            Artist(
                id = "2",
                name = "Big",
                songs = listOf(song("b1", "One"), song("b2", "Two"))
            )
        )
        assertEquals(listOf("Big", "Small"), LibrarySorting.artists(artists, LibrarySort.RECENT).map { it.name })
    }

    @Test
    fun playlists_recentPrefersUpdatedAtAndFallsBackToCreatedAt() {
        val older = Playlist(id = "1", name = "Older", createdAt = 100L, updatedAt = 0L)
        val newer = Playlist(id = "2", name = "Newer", createdAt = 50L, updatedAt = 300L)
        val sorted = LibrarySorting.playlists(listOf(older, newer), PlaylistListSort.RECENT)
        assertEquals(listOf("Newer", "Older"), sorted.map { it.name })
    }

    @Test
    fun playlistTracks_alphabeticalAndDuration() {
        val songs = listOf(
            song("1", "Zebra", durationMs = 100L),
            song("2", "Apple", durationMs = 300L)
        )
        assertEquals(
            listOf("Apple", "Zebra"),
            LibrarySorting.playlistTracks(songs, PlaylistTrackSort.TITLE).map { it.title }
        )
        assertEquals(
            listOf("Apple", "Zebra"),
            LibrarySorting.playlistTracks(songs, PlaylistTrackSort.DURATION).map { it.title }
        )
    }

    @Test
    fun playlistTracks_customKeepsDragOrder() {
        val songs = listOf(song("1", "Second"), song("2", "First"))
        assertEquals(songs, LibrarySorting.playlistTracks(songs, PlaylistTrackSort.CUSTOM))
    }

    @Test
    fun pinnedFirst_keepsPinnedOrderAndDoesNotDuplicate() {
        val playlists = listOf(
            playlist("a", "A"),
            playlist("b", "B"),
            playlist("c", "C")
        )
        val ordered = LibrarySorting.pinnedFirst(playlists, { it.id }, listOf("c", "a"))
        assertEquals(listOf("c", "a", "b"), ordered.map { it.id })
    }

    @Test
    fun pinnedFirst_ignoresIdsThatNoLongerExist() {
        val playlists = listOf(playlist("a", "A"))
        val ordered = LibrarySorting.pinnedFirst(playlists, { it.id }, listOf("deleted", "a"))
        assertEquals(listOf("a"), ordered.map { it.id })
    }

    @Test
    fun matchesPlaylist_searchesNameAndDescription() {
        val item = Playlist(id = "1", name = "Late Night", description = "Slow jazz")
        assertTrue(LibrarySorting.matchesPlaylist(item, "night"))
        assertTrue(LibrarySorting.matchesPlaylist(item, "JAZZ"))
        assertFalse(LibrarySorting.matchesPlaylist(item, "metal"))
        assertTrue(LibrarySorting.matchesPlaylist(item, "   "))
    }

    @Test
    fun filterPlaylistTracks_searchesTitleArtistAndAlbum() {
        val songs = listOf(
            song("1", "Nightcall", artist = "Kavinsky", album = "Outrun"),
            song("2", "Daylight", artist = "Other", album = "Morning")
        )
        assertEquals(listOf("1"), LibrarySorting.filterPlaylistTracks(songs, "kavinsky").map { it.id })
        assertEquals(listOf("2"), LibrarySorting.filterPlaylistTracks(songs, "morning").map { it.id })
        assertEquals(2, LibrarySorting.filterPlaylistTracks(songs, "").size)
    }

    @Test
    fun sortCyclesWrapAround() {
        assertEquals(LibrarySort.ALPHABETICAL, LibrarySort.RECENT.next())
        assertEquals(LibrarySort.RECENT, LibrarySort.BY_ARTIST.next())
        assertEquals(PlaylistTrackSort.TITLE, PlaylistTrackSort.CUSTOM.next())
        assertEquals(PlaylistTrackSort.CUSTOM, PlaylistTrackSort.DURATION.next())
        assertEquals(PlaylistListSort.ALPHABETICAL, PlaylistListSort.RECENT.next())
    }
}
