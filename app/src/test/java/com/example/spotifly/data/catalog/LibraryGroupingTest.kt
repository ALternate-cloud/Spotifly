package com.example.spotifly.data.catalog

import com.example.spotifly.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM tests for album/artist grouping over a flat track list. */
class LibraryGroupingTest {

    private fun song(
        id: String,
        title: String,
        artist: String = "Artist",
        album: String = "Album",
        albumId: String? = null,
        artistId: String? = null,
        trackNumber: Int = 0,
        discNumber: Int = 1,
        year: Int = 0,
        artworkUrl: String? = null,
        isLocal: Boolean = false
    ) = Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        albumId = albumId,
        artistId = artistId,
        trackNumber = trackNumber,
        discNumber = discNumber,
        year = year,
        artworkUrl = artworkUrl,
        isLocal = isLocal
    )

    @Test
    fun albums_groupsByNormalisedArtistAndAlbum() {
        val albums = LibraryGrouping.albums(
            listOf(
                song("1", "One", artist = "Band", album = "Record"),
                song("2", "Two", artist = " band ", album = "record"),
                song("3", "Other", artist = "Band", album = "Second")
            )
        )

        assertEquals(2, albums.size)
        val record = albums.first { it.title.equals("Record", ignoreCase = true) }
        assertEquals(2, record.trackCount)
    }

    @Test
    fun albums_ordersTracksByDiscThenTrackNumber() {
        val album = LibraryGrouping.albums(
            listOf(
                song("late", "Late", trackNumber = 3),
                song("second", "Second", trackNumber = 2),
                song("disc2", "Bonus", trackNumber = 1, discNumber = 2)
            )
        ).single()

        assertEquals(listOf("Second", "Late", "Bonus"), album.songs.map { it.title })
    }

    @Test
    fun albums_skipsTracksWithoutAlbumName() {
        val albums = LibraryGrouping.albums(
            listOf(
                song("1", "Single", album = ""),
                song("2", "Album track", album = "Record")
            )
        )

        assertEquals(1, albums.size)
        assertEquals("Record", albums.single().title)
    }

    @Test
    fun albums_prefersExplicitAlbumIdOverNameMatching() {
        val albums = LibraryGrouping.albums(
            listOf(
                song("1", "One", album = "Same name", albumId = "aaa"),
                song("2", "Two", album = "Same name", albumId = "bbb")
            )
        )

        assertEquals(2, albums.size)
    }

    @Test
    fun albums_usesFirstArtworkFoundAndLocalFlag() {
        val album = LibraryGrouping.albums(
            listOf(
                song("1", "One", artworkUrl = null),
                song("2", "Two", artworkUrl = "https://art/2.jpg", isLocal = true)
            )
        ).single()

        assertEquals("https://art/2.jpg", album.artworkUrl)
        assertTrue(album.isLocal)
    }

    @Test
    fun artists_splitsFeaturedCreditsAndGroupsCaseInsensitively() {
        val artists = LibraryGrouping.artists(
            listOf(
                song("1", "One", artist = "Main, Featured"),
                song("2", "Two", artist = "MAIN")
            )
        )

        assertEquals(1, artists.size)
        assertEquals("Main", artists.single().name)
        assertEquals(2, artists.single().trackCount)
    }

    @Test
    fun artists_collectTheOwnAlbums() {
        val artists = LibraryGrouping.artists(
            listOf(
                song("1", "One", album = "First"),
                song("2", "Two", album = "Second")
            )
        )

        assertEquals(1, artists.size)
        assertEquals(2, artists.single().albumCount)
    }

    @Test
    fun albumFor_fallsBackToSingleTrackAlbum() {
        val track = song("1", "Lonely", album = "Unknown record")
        val album = LibraryGrouping.albumFor(track, emptyList())

        assertEquals("Unknown record", album.title)
        assertEquals(listOf(track.id), album.songs.map { it.id })
    }

    @Test
    fun albumFor_findsAlbumInLibrary() {
        val library = listOf(
            song("1", "One", album = "Record"),
            song("2", "Two", album = "Record")
        )
        val album = LibraryGrouping.albumFor(library.first(), library)

        assertEquals(2, album.trackCount)
    }

    @Test
    fun artistFor_usesLibraryWhenAvailable() {
        val library = listOf(
            song("1", "One", artist = "Band"),
            song("2", "Two", artist = "Band", album = "Other")
        )
        val artist = LibraryGrouping.artistFor(library.first(), library)

        assertEquals("Band", artist.name)
        assertEquals(2, artist.trackCount)
    }

    @Test
    fun matches_checksTitleArtistAndAlbumCaseInsensitively() {
        val track = song("1", "Midnight City", artist = "M83", album = "Hurry Up")

        assertTrue(LibraryGrouping.matches(track, "midnight"))
        assertTrue(LibraryGrouping.matches(track, "M83"))
        assertTrue(LibraryGrouping.matches(track, "hurry"))
        assertTrue(LibraryGrouping.matches(track, "   "))
        assertFalse(LibraryGrouping.matches(track, "sunrise"))
    }
}
