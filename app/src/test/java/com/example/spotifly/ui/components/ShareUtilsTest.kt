package com.example.spotifly.ui.components

import com.example.spotifly.data.stats.ArtistStat
import com.example.spotifly.data.stats.ListeningStats
import com.example.spotifly.data.stats.TrackStat
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class ShareUtilsTest {

    private fun statsWithTopItems(): ListeningStats = ListeningStats(
        totalPlays = 42,
        totalMs = TimeUnit.MINUTES.toMillis(90),
        uniqueTracks = 12,
        uniqueArtists = 6,
        playsLast7Days = 7,
        msLast7Days = TimeUnit.MINUTES.toMillis(20),
        topTracks = listOf(
            TrackStat(
                songId = "1",
                title = "Midnight City",
                artist = "M83",
                artworkUrl = null,
                plays = 9,
                listenedMs = 30_000
            )
        ),
        topArtists = listOf(
            ArtistStat(name = "M83", artworkUrl = null, plays = 9, listenedMs = 30_000)
        )
    )

    @Test
    fun `wrapped text reports recorded numbers`() {
        val text = ShareUtils.wrappedText(statsWithTopItems())

        assertTrue(text.contains("90 minutes"))
        assertTrue(text.contains("42 plays"))
        assertTrue(text.contains("6 artists"))
        assertTrue(text.contains("Top artist: M83 (9 plays)"))
        assertTrue(text.contains("Top track: Midnight City — M83"))
        assertTrue(text.contains("Last 7 days: 20 minutes"))
        assertTrue(text.contains("Shared from Spotifly"))
    }

    @Test
    fun `wrapped text skips missing leaders`() {
        val text = ShareUtils.wrappedText(ListeningStats())

        assertFalse(text.contains("Top artist"))
        assertFalse(text.contains("Top track"))
        assertTrue(text.contains("0 minutes"))
    }
}
