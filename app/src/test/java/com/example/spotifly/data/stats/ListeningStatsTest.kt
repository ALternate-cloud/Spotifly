package com.example.spotifly.data.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Pure JVM tests for the play-log aggregation behind stats and Wrapped-style totals. */
class ListeningStatsTest {

    private val now = System.currentTimeMillis()

    private fun event(
        songId: String,
        artist: String,
        playedAt: Long = now,
        durationMs: Long = 180_000L,
        title: String = "Track $songId",
        artworkUrl: String? = null
    ) = PlayEvent(
        songId = songId,
        title = title,
        artist = artist,
        artworkUrl = artworkUrl,
        playedAt = playedAt,
        durationMs = durationMs
    )

    @Test
    fun emptyLogProducesEmptyStats() {
        val stats = aggregateListeningStats(emptyList())

        assertTrue(stats.isEmpty)
        assertEquals(0, stats.totalPlays)
        assertTrue(stats.topTracks.isEmpty())
    }

    @Test
    fun totalsCountPlaysAndEstimatedListeningTime() {
        val stats = aggregateListeningStats(
            listOf(
                event("1", "Band", durationMs = 60_000L),
                event("2", "Band", durationMs = 120_000L)
            )
        )

        assertEquals(2, stats.totalPlays)
        assertEquals(180_000L, stats.totalMs)
        assertEquals(3L, stats.totalMinutes)
        assertEquals(2, stats.uniqueTracks)
        assertEquals(1, stats.uniqueArtists)
    }

    @Test
    fun topTracksRankByPlayCountAndKeepLatestMetadata() {
        val stats = aggregateListeningStats(
            listOf(
                event("a", "Band", playedAt = now - 5_000, title = "Old title"),
                event("b", "Band"),
                event("a", "Band", playedAt = now, title = "New title", artworkUrl = "https://art/a.jpg")
            )
        )

        assertEquals("a", stats.topTracks.first().songId)
        assertEquals(2, stats.topTracks.first().plays)
        assertEquals("New title", stats.topTracks.first().title)
        assertEquals("https://art/a.jpg", stats.topTracks.first().artworkUrl)
        assertEquals(2L, stats.topTracks.first().listenedMs / 180_000L)
    }

    @Test
    fun featuredCreditsCountTowardsTheLeadArtist() {
        val stats = aggregateListeningStats(
            listOf(
                event("1", "Main, Featured"),
                event("2", "main")
            )
        )

        assertEquals(1, stats.uniqueArtists)
        assertEquals("Main", stats.topArtists.single().name)
        assertEquals(2, stats.topArtists.single().plays)
    }

    @Test
    fun timeWindowsSeparateWeekMonthAndOlderPlays() {
        val day = TimeUnit.DAYS.toMillis(1)
        val stats = aggregateListeningStats(
            listOf(
                event("recent", "Band", playedAt = now - 2 * day),
                event("month", "Band", playedAt = now - 20 * day),
                event("old", "Band", playedAt = now - 90 * day)
            )
        )

        assertEquals(3, stats.totalPlays)
        assertEquals(1, stats.playsLast7Days)
        assertEquals(2, stats.playsLast30Days)
    }

    @Test
    fun topTracksAreCappedAtTen() {
        val events = (1..14).map { index -> event("song$index", "Band") }
        val stats = aggregateListeningStats(events)

        assertEquals(10, stats.topTracks.size)
        assertEquals(14, stats.totalPlays)
    }
}
