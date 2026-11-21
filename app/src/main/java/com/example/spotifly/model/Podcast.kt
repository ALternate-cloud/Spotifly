package com.example.spotifly.model

import kotlinx.serialization.Serializable

/**
 * A podcast show, discovered through the public iTunes search index and played from
 * its own RSS feed. Nothing here depends on Spotify's catalogue.
 */
@Serializable
data class PodcastShow(
    val id: String,
    val title: String,
    val publisher: String = "",
    val artworkUrl: String? = null,
    val feedUrl: String = "",
    val description: String = "",
    val genre: String = "",
    val episodeCount: Int = 0
)

/** A single episode inside a show's feed. */
@Serializable
data class PodcastEpisode(
    val id: String,
    val showId: String,
    val showTitle: String = "",
    val title: String,
    val description: String = "",
    val audioUrl: String = "",
    val artworkUrl: String? = null,
    val durationMs: Long = 0L,
    val publishedAt: Long = 0L
) {
    val displayDuration: String
        get() {
            if (durationMs <= 0) return ""
            val totalSeconds = durationMs / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            return if (hours > 0) "%dh %02dm".format(hours, minutes) else "%dm".format(minutes)
        }
}
