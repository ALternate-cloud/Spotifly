package com.example.spotifly.data.autoplay

/**
 * Builds the catalogue searches that widen the candidate pool beyond the
 * per-track recommendation endpoint.
 *
 * The seed's language plus a coarse mood word is enough to surface the right
 * neighbourhood ("hindi soft songs"). Hindi and Urdu share a listening culture,
 * so a calm Hindi seed also searches the Urdu side — that is how a track like
 * "Mere Nishan" can reach a soft Urdu ballad instead of staying inside one
 * catalogue label.
 */
object AutoplayQueries {

    fun vibeQuery(target: VibeProfile): String =
        listOfNotNull(target.language?.takeIf { it.isNotBlank() && it != "unknown" }, moodWord(target), "songs")
            .joinToString(" ")

    /** Primary mood search plus a sibling-language search when one exists. */
    fun vibeQueries(target: VibeProfile): List<String> {
        val primary = vibeQuery(target)
        val neighbour = when (target.language) {
            "hindi" -> "urdu"
            "urdu" -> "hindi"
            else -> null
        } ?: return listOf(primary)

        return listOf(
            primary,
            "$neighbour ${moodWord(target)} songs"
        )
    }

    private fun moodWord(target: VibeProfile): String = when {
        target.vector.energy <= 0.42 && target.vector.valence <= 0.48 -> "sad"
        target.vector.energy <= 0.52 -> "soft"
        target.vector.energy >= 0.74 -> "party"
        target.vector.valence >= 0.62 -> "romantic"
        else -> "melody"
    }
}
