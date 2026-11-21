package com.example.spotifly.data.autoplay

import kotlinx.serialization.Serializable
import kotlin.math.min
import kotlin.math.sqrt

/**
 * A coarse, unit-interval description of how a track feels.
 *
 * Every axis is normalised to 0..1 so measured audio features and (rare)
 * metadata fallbacks can share the same space:
 *
 * - [energy]      quiet/intimate (0) .. loud/driving (1)
 * - [valence]     melancholic (0) .. bright/positive (1)
 * - [tempo]       60 BPM (0) .. 180 BPM (1)
 * - [acousticness] produced/electronic (0) .. acoustic/organic (1)
 * - [danceability] free-tempo (0) .. strong steady pulse (1)
 */
@Serializable
data class VibeVector(
    val energy: Double = 0.5,
    val valence: Double = 0.5,
    val tempo: Double = 0.5,
    val acousticness: Double = 0.45,
    val danceability: Double = 0.45
) {

    fun clamped(): VibeVector = VibeVector(
        energy = energy.coerceIn(0.02, 0.98),
        valence = valence.coerceIn(0.02, 0.98),
        tempo = tempo.coerceIn(0.02, 0.98),
        acousticness = acousticness.coerceIn(0.02, 0.98),
        danceability = danceability.coerceIn(0.02, 0.98)
    )

    /** Weighted mean; [otherWeight] is the share (0..1) given to [other]. */
    fun blendedWith(other: VibeVector, otherWeight: Double): VibeVector {
        val w = otherWeight.coerceIn(0.0, 1.0)
        val own = 1.0 - w
        return VibeVector(
            energy = energy * own + other.energy * w,
            valence = valence * own + other.valence * w,
            tempo = tempo * own + other.tempo * w,
            acousticness = acousticness * own + other.acousticness * w,
            danceability = danceability * own + other.danceability * w
        )
    }

    /**
     * Weighted, mean-centred **cosine similarity** between feature vectors.
     *
     * Each axis deviation from [CENTER] is scaled by its weight, so energy and
     * mood — the axes a listener actually notices — dominate the direction of
     * the vector. The cosine is mapped from [-1, 1] onto [0, 1].
     *
     * A vector close to [CENTER] carries almost no information (a track with no
     * measured features and neutral metadata). Cosine amplifies noise for tiny
     * vectors, so the result is shrunk toward the "no signal" midpoint by the
     * confidence of the weaker side instead of being trusted outright.
     */
    fun similarityTo(other: VibeVector): Double {
        val a = weightedDeviations()
        val b = other.weightedDeviations()

        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val lenA = sqrt(normA)
        val lenB = sqrt(normB)
        val confidence = (min(lenA, lenB) / CONFIDENCE_NORM).coerceIn(0.0, 1.0)
        if (confidence <= 0.0 || lenA * lenB <= 0.0) return NO_SIGNAL_SIMILARITY

        val cos = (dot / (lenA * lenB)).coerceIn(-1.0, 1.0)
        val cosineSim = ((cos + 1.0) / 2.0).coerceIn(0.0, 1.0)
        return (NO_SIGNAL_SIMILARITY * (1.0 - confidence) + cosineSim * confidence)
            .coerceIn(0.0, 1.0)
    }

    private fun weightedDeviations(): DoubleArray = doubleArrayOf(
        (energy - CENTER_ENERGY) * ENERGY_WEIGHT,
        (valence - CENTER_VALENCE) * VALENCE_WEIGHT,
        tempo - CENTER_TEMPO,
        acousticness - CENTER_ACOUSTICNESS,
        danceability - CENTER_DANCEABILITY
    )

    companion object {
        /** Axis weights for the cosine. Energy and mood lead; the rest support. */
        const val ENERGY_WEIGHT = 1.40
        const val VALENCE_WEIGHT = 1.20
        const val TEMPO_WEIGHT = 1.00
        const val ACOUSTIC_WEIGHT = 1.00
        const val DANCE_WEIGHT = 1.00

        /** 0..1 midpoint of each axis; centring removes baseline offsets. */
        const val CENTER_ENERGY = 0.45
        const val CENTER_VALENCE = 0.50
        const val CENTER_TEMPO = 0.47
        const val CENTER_ACOUSTICNESS = 0.45
        const val CENTER_DANCEABILITY = 0.40

        /**
         * Vectors shorter than this (after centring) are treated as carrying no
         * usable signal rather than being matched directionally.
         */
        const val CONFIDENCE_NORM = 0.40

        /** "Knowing nothing" similarity: dead centre. */
        const val NO_SIGNAL_SIMILARITY = 0.5

        val NEUTRAL = VibeVector()
    }
}
