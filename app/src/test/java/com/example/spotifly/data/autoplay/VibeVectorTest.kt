package com.example.spotifly.data.autoplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cosine-similarity contract for the autoplay engine: identical feel = 1,
 * opposite feel = 0, no-signal centre = 0.5, and small-norm noise never
 * pretends to be strong signal.
 */
class VibeVectorTest {

    @Test
    fun identicalVectors_areFullySimilar() {
        val v = VibeVector(energy = 0.9, valence = 0.7, tempo = 0.8, acousticness = 0.1, danceability = 0.85)
        assertEquals(1.0, v.similarityTo(v.copy()), 0.0001)
    }

    @Test
    fun oppositeVectors_areNotSimilar() {
        // Deviations chosen as exact reflections around the centre so the
        // cosine is -1 and the mapped similarity is exactly 0.
        val loud = VibeVector(energy = 0.75, valence = 0.70, tempo = 0.62, acousticness = 0.15, danceability = 0.60)
        val quiet = VibeVector(energy = 0.15, valence = 0.30, tempo = 0.32, acousticness = 0.75, danceability = 0.20)
        assertEquals(0.0, loud.similarityTo(quiet), 0.0001)
    }

    @Test
    fun vectorsExactlyAtTheCentre_carryNoSignal() {
        val centre = VibeVector(
            energy = 0.45,
            valence = 0.50,
            tempo = 0.47,
            acousticness = 0.45,
            danceability = 0.40
        )
        assertEquals(0.5, centre.similarityTo(centre.copy()), 0.0001)
    }

    @Test
    fun sameFeelMeasurements_stayHigh() {
        val one = VibeVector(energy = 0.80, valence = 0.60, tempo = 0.70, acousticness = 0.30, danceability = 0.65)
        val two = VibeVector(energy = 0.76, valence = 0.58, tempo = 0.66, acousticness = 0.34, danceability = 0.62)
        assertTrue("same-feel tracks should rank as the same vibe", one.similarityTo(two) >= 0.9)
    }

    @Test
    fun oppositeFeelMeasurements_stayLow() {
        val club = VibeVector(energy = 0.9, valence = 0.7, tempo = 0.85, acousticness = 0.05, danceability = 0.9)
        val ghazal = VibeVector(energy = 0.15, valence = 0.2, tempo = 0.25, acousticness = 0.9, danceability = 0.2)
        assertTrue("different-feel tracks must not be called similar", club.similarityTo(ghazal) <= 0.2)
    }

    @Test
    fun weakNearNeutralVector_isShrunkTowardNoSignal() {
        // A real measurement vs. a barely-off-centre metadata vector: the tiny
        // vector contributes little information, so similarity stays modest.
        val measured = VibeVector(energy = 0.85, valence = 0.70, tempo = 0.75, acousticness = 0.15, danceability = 0.80)
        val almostNeutral = VibeVector(energy = 0.52, valence = 0.52, tempo = 0.49, acousticness = 0.44, danceability = 0.43)

        val similarity = measured.similarityTo(almostNeutral)
        assertTrue("no real vector should read as identical (1.0) to a tiny vector", similarity < 0.75)
        assertTrue("shrinkage keeps some directional information", similarity > 0.5)
    }

    @Test
    fun energyDominatesDirection() {
        // Same-mood pair differing mainly on tempo should stay closer than a
        // pair flipped on energy, because energy has the highest weight.
        val a = VibeVector(energy = 0.80, valence = 0.60, tempo = 0.40, danceability = 0.55)
        val b = VibeVector(energy = 0.74, valence = 0.66, tempo = 0.30, danceability = 0.50)
        val flippedEnergy = VibeVector(energy = 0.10, valence = 0.60, tempo = 0.40, danceability = 0.55)

        assertTrue(a.similarityTo(b) > a.similarityTo(flippedEnergy))
    }
}
