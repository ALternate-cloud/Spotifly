package com.example.spotifly.data.autoplay

import org.junit.Assert.assertEquals
import org.junit.Test

class AutoplayQueriesTest {

    private fun profile(energy: Double, valence: Double, language: String?) = VibeProfile(
        vector = VibeVector(energy = energy, valence = valence),
        language = language,
        genre = null,
        artistKey = "artist",
        year = 2020
    )

    @Test
    fun sadSlowProfile_searchesSoftSongsInTheSeedLanguage() {
        assertEquals(
            "hindi sad songs",
            AutoplayQueries.vibeQuery(profile(energy = 0.3, valence = 0.3, language = "hindi"))
        )
    }

    @Test
    fun calmProfile_usesSoft() {
        assertEquals(
            "urdu soft songs",
            AutoplayQueries.vibeQuery(profile(energy = 0.5, valence = 0.6, language = "urdu"))
        )
    }

    @Test
    fun highEnergyProfile_usesParty() {
        assertEquals(
            "hindi party songs",
            AutoplayQueries.vibeQuery(profile(energy = 0.85, valence = 0.5, language = "hindi"))
        )
    }

    @Test
    fun brightProfile_usesRomantic() {
        assertEquals(
            "hindi romantic songs",
            AutoplayQueries.vibeQuery(profile(energy = 0.65, valence = 0.8, language = "hindi"))
        )
    }

    @Test
    fun missingLanguage_dropsItRatherThanSearchingUnknown() {
        assertEquals(
            "soft songs",
            AutoplayQueries.vibeQuery(profile(energy = 0.5, valence = 0.5, language = null))
        )
    }

    @Test
    fun hindiSeed_alsoSearchesUrduNeighbourhood() {
        val queries = AutoplayQueries.vibeQueries(
            profile(energy = 0.4, valence = 0.4, language = "hindi")
        )

        assertEquals(listOf("hindi sad songs", "urdu sad songs"), queries)
    }

    @Test
    fun unrelatedLanguage_doesNotAddANeighbourSearch() {
        assertEquals(
            listOf("english sad songs"),
            AutoplayQueries.vibeQueries(profile(energy = 0.4, valence = 0.4, language = "english"))
        )
    }
}
