package com.example.spotifly.data.online

import com.example.spotifly.data.catalog.TrackMatching
import org.json.JSONObject
import com.example.spotifly.model.Song
import org.junit.Assert.*
import org.junit.Test
import org.junit.Assume.assumeTrue
import kotlinx.coroutines.runBlocking

class SearchCoverageTest {
    @Test fun liveIshqaVeSearchReturnsExactRecording() = runBlocking {
        assumeTrue(System.getenv("SPOTIFLY_LIVE_TESTS") == "1")
        val results = OnlineMusicService.searchSongs("Ishqa ve")
        assertTrue("Expected exact title ahead of extended versions", results.first().title.equals("Ishqa Ve", true))
        val track = results.firstOrNull {
            it.title.equals("Ishqa Ve", true) && it.artist.contains("Zeeshan Ali")
        }
        assertNotNull("Missing Zeeshan Ali recording", track)
        assertFalse("Expected catalog identity", track!!.sourceIds.itunes.isNullOrBlank())
        val resolved = PlaybackResolver.resolve(track)
        assertEquals("XffsuRCvg2k", resolved?.sourceIds?.ytmusic)
        assertTrue("Expected a verified full audio stream", resolved?.isPlayable == true)
        assertFalse(resolved?.streamUrl.isNullOrBlank())
    }

    @Test fun decodesCatalogStreamWithoutAndroidBase64OrApi26() {
        val expectedUrl = "https://example.test/song_96.mp4"
        val cipher = javax.crypto.Cipher.getInstance("DES/ECB/PKCS5Padding")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE,
            javax.crypto.spec.SecretKeySpec("38346591".toByteArray(), "DES"))
        val encrypted = java.util.Base64.getEncoder().encodeToString(cipher.doFinal(expectedUrl.toByteArray()))
        for (encoded in listOf(encrypted, encrypted.replace('+', '-').replace('/', '_'))) {
            val item = JSONObject().put("id", "fixture").put("song", "Fixture")
                .put("encrypted_media_url", encoded)
            val track = OnlineMusicService.parseSaavnSong(item, 0)!!
            assertEquals("https://example.test/song_160.mp4", track.streamUrl)
        }
    }

    @Test fun parsesIshqaVeWithCatalogLinksForAutoplay() {
        val track = OnlineMusicService.parseSaavnSong(JSONObject("""
            {"id":"02yJpu9x","song":"Ishqa Ve","album":"Ishqa Ve",
             "primary_artists":"Rahul Mishra, Sanah Moidutty","duration":"226",
             "language":"hindi","albumid":"58926675","primary_artists_id":"499199, 658478"}
        """), 0)!!
        assertEquals("Ishqa Ve", track.title)
        assertEquals("saavn_02yJpu9x", track.id)
        assertEquals("58926675", track.albumId)
        assertEquals("499199", track.artistId)
        assertEquals(226000L, track.durationMs)
    }

    @Test fun resolverRejectsDifferentSingerWithSameTitleAndDuration() {
        val requested = Song(id = "ishqa", title = "Ishqa Ve", artist = "Zeeshan Ali", durationMs = 207000)
        assertEquals(0, TrackMatching.scoreCandidate(requested, "Ishqa Ve", "Another Singer", 207))
        assertEquals(100, TrackMatching.scoreCandidate(requested, "Ishqa Ve", "Zeeshan Ali", 207))
    }

    @Test fun ordinaryWordsAreNotLiveOrCoverVersions() {
        assertFalse(TrackMatching.isLive("Alive"))
        assertFalse(TrackMatching.isCover("Discover", "Singer"))
        assertTrue(TrackMatching.isLive("Song (Live)"))
        assertTrue(TrackMatching.isCover("Song (Cover)", "Singer"))
    }
}
