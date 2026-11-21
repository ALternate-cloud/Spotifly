package com.example.spotifly.data.catalog

import com.example.spotifly.model.Song
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchSuggestionsTest {

    private fun song(id: String, title: String, artist: String) = Song(
        id = id,
        title = title,
        artist = artist,
        album = "Album",
        durationMs = 200_000L,
        streamUrl = "",
        artworkUrl = null
    )

    private val library = listOf(
        song("1", "Midnight City", "M83"),
        song("2", "Midnight Sun", "M83"),
        song("3", "City Lights", "Daft Punk"),
        song("4", "Digital Love", "Daft Punk")
    )

    @Test
    fun `returns nothing for blank query`() {
        assertEquals(emptyList<String>(), SearchSuggestions.build("  ", library, listOf("Midnight")))
    }

    @Test
    fun `prioritises recent searches`() {
        val result = SearchSuggestions.build("mid", library, listOf("Midnight Songs"))
        assertEquals("Midnight Songs", result.first())
    }

    @Test
    fun `falls back to artists then track titles`() {
        val result = SearchSuggestions.build("daf", library, emptyList())
        assertEquals(listOf("Daft Punk"), result)
    }

    @Test
    fun `matches titles as well as artists`() {
        val result = SearchSuggestions.build("city", library, emptyList())
        assertEquals(listOf("Midnight City", "City Lights"), result)
    }

    @Test
    fun `deduplicates case-insensitively and skips the exact query`() {
        val result = SearchSuggestions.build(
            query = "m83",
            localSongs = library,
            recentSearches = listOf("M83", "m83 radio")
        )
        assertEquals(listOf("m83 radio"), result)
    }

    @Test
    fun `respects the limit`() {
        val result = SearchSuggestions.build(
            query = "mid",
            localSongs = library,
            recentSearches = listOf("mid 1", "mid 2", "mid 3"),
            limit = 2
        )
        assertEquals(2, result.size)
    }
}
