package com.example.spotifly.data.online

import com.example.spotifly.data.catalog.TrackMatching
import com.example.spotifly.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryServiceTest {

    @Test
    fun parseItunesRssChart_parsesArrayAndUpscalesArtwork() {
        val sampleJson = """
            {
              "feed": {
                "entry": [
                  {
                    "im:name": {"label": "Starboy &amp; Friends"},
                    "im:artist": {"label": "The Weeknd"},
                    "im:collection": {"im:name": {"label": "Starboy"}},
                    "im:image": [
                      {"label": "https://is1-ssl.mzstatic.com/image/55x55bb.png"},
                      {"label": "https://is1-ssl.mzstatic.com/image/170x170bb.png"}
                    ],
                    "id": {"attributes": {"im:id": "1440871397"}},
                    "category": {"attributes": {"label": "R&amp;B/Soul"}},
                    "im:releaseDate": {"label": "2016-11-25T00:00:00-07:00"}
                  }
                ]
              }
            }
        """.trimIndent()

        val songs = DiscoveryService.parseItunesRssChart(sampleJson)
        assertEquals(1, songs.size)
        val song = songs.first()
        assertEquals("itunes_1440871397", song.id)
        assertEquals("Starboy & Friends", song.title)
        assertEquals("The Weeknd", song.artist)
        assertEquals("Starboy", song.album)
        assertEquals("https://is1-ssl.mzstatic.com/image/500x500bb.png", song.artworkUrl)
        assertEquals(2016, song.year)
        assertEquals("1440871397", song.sourceIds.itunes)
    }

    @Test
    fun parseAppleMarketingChart_parsesResultsCleanly() {
        val json = """
            {
              "feed": {
                "results": [
                  {
                    "id": "1844932150",
                    "name": "Espresso",
                    "artistName": "Sabrina Carpenter",
                    "releaseDate": "2024-04-12",
                    "artistId": "390647681",
                    "artworkUrl100": "https://is1-ssl.mzstatic.com/image/100x100bb.jpg",
                    "genres": [{"name": "Pop"}]
                  }
                ]
              }
            }
        """.trimIndent()

        val songs = DiscoveryService.parseAppleMarketingChart(json)
        assertEquals(1, songs.size)
        assertEquals("Espresso", songs[0].title)
        assertEquals("Sabrina Carpenter", songs[0].artist)
        assertEquals("Pop", songs[0].genre)
        assertEquals("https://is1-ssl.mzstatic.com/image/500x500bb.jpg", songs[0].artworkUrl)
    }

    @Test
    fun parseSaavnChartDescriptors_skipsNullStubEntries() {
        val json = """
            [
              {"id": "1134543272", "title": "Hindi: India Superhits Top 50", "type": "playlist", "image": "https://c.saavncdn.com/editorial/150x150.jpg"},
              {"id": null, "title": null, "type": null}
            ]
        """.trimIndent()

        val descriptors = DiscoveryService.parseSaavnChartDescriptors(json)
        assertEquals(1, descriptors.size)
        assertEquals("1134543272", descriptors[0].id)
        assertEquals("https://c.saavncdn.com/editorial/500x500.jpg", descriptors[0].artworkUrl)
    }

    @Test
    fun pickMatchingDeezerArtistId_prefersExactNormalizedMatch() {
        val json = """
            {
              "data": [
                {"id": 999, "name": "The Weeknd Tribute Band"},
                {"id": 4050205, "name": "The Weeknd"}
              ]
            }
        """.trimIndent()
        assertEquals("4050205", DiscoveryService.pickMatchingDeezerArtistId("the weeknd", json))
    }

    @Test
    fun parseDeezerRelatedArtists_excludesTargetAndMapsIds() {
        val json = """
            {
              "data": [
                {"id": 4050205, "name": "The Weeknd", "picture_big": "https://cdn-images.dzcdn.net/wk.jpg"},
                {"id": 564, "name": "Rihanna", "picture_big": "https://cdn-images.dzcdn.net/rih.jpg"},
                {"id": 8706544, "name": "Dua Lipa", "picture_xl": "https://cdn-images.dzcdn.net/dl.jpg"}
              ]
            }
        """.trimIndent()

        val related = DiscoveryService.parseDeezerRelatedArtists(json, excludeName = "The Weeknd")
        assertEquals(2, related.size)
        assertEquals("deezer_artist_564", related[0].id)
        assertEquals("Rihanna", related[0].name)
        assertEquals("https://cdn-images.dzcdn.net/rih.jpg", related[0].artworkUrl)
        assertEquals("Dua Lipa", related[1].name)
    }

    @Test
    fun deriveLibraryRelatedArtists_ranksCollaboratorsFirst() {
        val library = listOf(
            Song(id = "1", title = "Starboy", artist = "The Weeknd, Daft Punk", genre = "Pop"),
            Song(id = "2", title = "I Feel It Coming", artist = "The Weeknd, Daft Punk", genre = "Pop"),
            Song(id = "3", title = "Levitating", artist = "Dua Lipa", genre = "Pop"),
            Song(id = "4", title = "Hum", artist = "Anuv Jain", genre = "Indie")
        )

        val related = DiscoveryService.deriveLibraryRelatedArtists("The Weeknd", library)
        assertTrue(related.isNotEmpty())
        assertEquals("Daft Punk", related.first().name)
        assertTrue(related.any { it.name == "Dua Lipa" })
    }

    @Test
    fun extractMatchingLyricSnippet_matchesExactSingleAndMultiLinePhrases() {
        val lyrics = """
            Now he's thinkin' 'bout me every night, oh
            Is it that sweet? I guess so
            Say you can't sleep, baby, I know
            That's that me espresso
            I'm working late 'cause I'm a singer
            Oh, he looks so cute wrapped 'round my finger
        """.trimIndent()

        val exactLine = DiscoveryService.extractMatchingLyricSnippet(lyrics, "im working late cause im a singer")
        assertEquals("I'm working late 'cause I'm a singer", exactLine)

        val couplet = DiscoveryService.extractMatchingLyricSnippet(lyrics, "say you cant sleep baby i know thats that me espresso")
        assertNotNull(couplet)
        assertTrue(couplet!!.contains("Say you can't sleep"))
        assertTrue(couplet.contains("espresso"))

        val unrelated = DiscoveryService.extractMatchingLyricSnippet(lyrics, "completely unrelated dragon ballad words")
        assertNull(unrelated)
    }

    @Test
    fun parseLrclibSearchResults_extractsMatchedSnippetFromSyncedLyrics() {
        val json = """
            [
              {
                "id": 42,
                "trackName": "Starboy",
                "artistName": "The Weeknd",
                "albumName": "Starboy",
                "duration": 230.0,
                "syncedLyrics": "[00:12.00] I'm tryna put you in the worst mood, ah\n[00:16.00] P1 cleaner than your church shoes, ah"
              }
            ]
        """.trimIndent()

        val matches = DiscoveryService.parseLrclibSearchResults(json, "worst mood")
        assertEquals(1, matches.size)
        assertEquals("Starboy", matches[0].first.title)
        assertEquals("I'm tryna put you in the worst mood, ah", matches[0].second)
    }

    @Test
    fun parseItunesRssChart_leavesUnknownDurationAsZeroAndMatchesRealStreamDuration() {
        val sampleJson = """
            {
              "feed": {
                "entry": [
                  {
                    "im:name": {"label": "Flower In A Hurricane"},
                    "im:artist": {"label": "Justice, SZA, King Gizzard &amp; The Lizard Wizard &amp; Tame Impala"},
                    "im:image": [{"label": "https://is1-ssl.mzstatic.com/image/170x170bb.png"}],
                    "id": {"attributes": {"im:id": "999999"}}
                  }
                ]
              }
            }
        """.trimIndent()

        val chartSong = DiscoveryService.parseItunesRssChart(sampleJson).single()
        assertEquals(0L, chartSong.durationSec)
        assertEquals(0L, chartSong.durationMs)

        // Previously durationSec=200L rejected real 221s tracks because |200 - 221| > 15s.
        val score = TrackMatching.scoreCandidate(
            requested = chartSong,
            candidateTitle = "Flower In A Hurricane (feat. Tame Impala, SZA, King Gizzard & The Lizard Wizard)",
            candidateArtist = "Justice",
            candidateDurationSec = 221L
        )
        assertTrue("Expected chart track to match 221s stream candidate (score=$score)", score >= 65)
    }
}
