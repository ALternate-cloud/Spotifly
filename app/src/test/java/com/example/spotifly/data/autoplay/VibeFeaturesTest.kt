package com.example.spotifly.data.autoplay

import com.example.spotifly.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM tests for metadata-derived vibe profiles and affinity rules. */
class VibeFeaturesTest {

    private fun song(
        title: String,
        artist: String = "Artist",
        album: String = "",
        language: String? = null,
        genre: String? = null,
        durationMs: Long = 210_000,
        year: Int = 0
    ) = Song(
        id = "id_${title.hashCode()}",
        title = title,
        artist = artist,
        album = album,
        language = language,
        genre = genre,
        durationMs = durationMs,
        year = year,
        streamUrl = "https://example.test/stream"
    )

    @Test
    fun languageAffinity_treatsHindiAndUrduAsNeighbours() {
        assertEquals(1.0, VibeFeatures.languageAffinity("hindi", "hindi"), 0.0001)
        assertEquals(0.92, VibeFeatures.languageAffinity("hindi", "urdu"), 0.0001)
        assertEquals(0.78, VibeFeatures.languageAffinity("hindi", "punjabi"), 0.0001)
        assertEquals(0.6, VibeFeatures.languageAffinity("hindi", "tamil"), 0.0001)
        assertEquals(0.15, VibeFeatures.languageAffinity("hindi", "english"), 0.0001)
        assertEquals(0.4, VibeFeatures.languageAffinity("hindi", null), 0.0001)
        assertEquals(0.55, VibeFeatures.languageAffinity("hindi", "instrumental"), 0.0001)
    }

    @Test
    fun calmWords_lowerEnergyAndTempo() {
        val plain = VibeFeatures.profileFor(song("Aasmani Rang Ho")).vector
        val sad = VibeFeatures.profileFor(
            song("Sad Lonely Acoustic Piano Ballad", album = "Broken Heart")
        ).vector

        assertTrue("sad song should be calmer", sad.energy < plain.energy)
        assertTrue("sad song should be slower", sad.tempo < plain.tempo)
        assertTrue("sad song should be more acoustic", sad.acousticness > plain.acousticness)
        assertTrue("sad song should be less danceable", sad.danceability < plain.danceability)
    }

    @Test
    fun energyWords_raiseTempoAndDanceability() {
        val plain = VibeFeatures.profileFor(song("Aasmani Rang Ho")).vector
        val dance = VibeFeatures.profileFor(song("Party Bass Drop Remix")).vector

        assertTrue(dance.energy > plain.energy)
        assertTrue(dance.tempo > plain.tempo)
        assertTrue(dance.danceability > plain.danceability)
        assertTrue(dance.acousticness < plain.acousticness)
    }

    @Test
    fun wordsMatchWholeTokens_soBeatlesIsNotABeat() {
        val beatles = VibeFeatures.profileFor(song("The Beatles", artist = "The Beatles")).vector
        val dance = VibeFeatures.profileFor(song("Beat Drop")).vector

        assertTrue(dance.energy > beatles.energy)
    }

    @Test
    fun genreTags_overrideNeutralDefaults() {
        val classical = VibeFeatures.profileFor(song("Raag", genre = "Hindustani Classical")).vector
        val metal = VibeFeatures.profileFor(song("Noise", genre = "Heavy Metal")).vector

        assertTrue(classical.acousticness > 0.7)
        assertTrue(metal.energy > 0.8)
        assertEquals(0.7, VibeFeatures.genreAffinity("Hindustani Classical", "classical"), 0.0001)
        assertEquals(0.0, VibeFeatures.genreAffinity("classical", "hip hop"), 0.0001)
    }

    @Test
    fun unknownGenres_doNotMatchEachOther() {
        assertEquals(0.0, VibeFeatures.genreAffinity("reggae", "country"), 0.0001)
        assertEquals(0.0, VibeFeatures.genreAffinity("reggae", null), 0.0001)
    }

    @Test
    fun measuredVector_isUsedUntouched() {
        val measured = VibeVector(
            energy = 0.1,
            valence = 0.2,
            tempo = 0.25,
            acousticness = 0.9,
            danceability = 0.15
        )
        val profile = VibeFeatures.profileFor(song("Party Remix"), measured = measured)

        // Scoring is pure audio similarity: a measured vector must reach the
        // profile unchanged, never blended back toward keyword guesses.
        assertEquals(measured.clamped(), profile.vector)
    }

    @Test
    fun titleKey_groupsEveryVersionOfOneSongName() {
        val original = song("Mere Nishan", artist = "Mohammed")
        val remix = song("Mere Nishan (Stormzy Remix)", artist = "Someone Else")
        val duet = song("Mere Nishan - Duet Version", artist = "Mohammed")
        val plus = song("Mere Nishaan", artist = "Kailash Kher")
        val unrelated = song("Mere Pyar Ki Nishani", artist = "Ramveer Gurjar")

        assertEquals(VibeFeatures.titleKey(original), VibeFeatures.titleKey(remix))
        assertEquals(VibeFeatures.titleKey(original), VibeFeatures.titleKey(duet))
        assertEquals(VibeFeatures.titleKey(original), VibeFeatures.titleKey(plus))
        assertNotEquals(VibeFeatures.titleKey(original), VibeFeatures.titleKey(unrelated))
    }

    @Test
    fun titleKey_isArtistAgnostic() {
        val sheet = song("Shape of You", artist = "Ed Sheeran")
        val cover = song("Shape of You", artist = "Galantis")

        assertEquals(VibeFeatures.titleKey(sheet), VibeFeatures.titleKey(cover))
    }

    @Test
    fun dedupeKey_ignoresVersionSuffixesAndFeaturedArtists() {
        val original = song("Mere Nishan", artist = "Mohammed")
        val duet = song("Mere Nishan (duet version)", artist = "Mohammed")
        val featured = song("Ruposh", artist = "Wajhi Farooki feat. Tarun Sharma")
        val plain = song("Ruposh", artist = "Wajhi Farooki")

        assertEquals(VibeFeatures.dedupeKey(original), VibeFeatures.dedupeKey(duet))
        assertEquals(VibeFeatures.dedupeKey(plain), VibeFeatures.dedupeKey(featured))
        assertEquals("wajhi farooki", VibeFeatures.primaryArtistKey(featured.artist))
    }
}
