package com.example.spotifly.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure JVM tests for LRC parsing and synced-line tracking. */
class LyricsTest {

    @Test
    fun parseLrc_readsCentisecondAndMillisecondTimestamps() {
        val lines = Lyrics.parseLrc(
            """
            [00:12.25] first line
            [01:05.123] three digit fraction
            not a lyric line
            """.trimIndent()
        )

        assertEquals(2, lines.size)
        assertEquals(12_250L, lines[0].timeMs)
        assertEquals("first line", lines[0].text)
        assertEquals(65_123L, lines[1].timeMs)
        assertEquals("three digit fraction", lines[1].text)
    }

    @Test
    fun parseLrc_sortsOutOfOrderEntries() {
        val lines = Lyrics.parseLrc("[00:30.00] later\n[00:04.00] earlier")

        assertEquals(listOf("earlier", "later"), lines.map { it.text })
    }

    @Test
    fun getActiveLineIndex_followsPlaybackPosition() {
        val synced = Lyrics(
            trackName = "Track",
            artistName = "Artist",
            isSynced = true,
            lines = listOf(
                LyricLine(0L, "one"),
                LyricLine(10_000L, "two"),
                LyricLine(20_000L, "three")
            )
        )

        assertEquals(-1, synced.getActiveLineIndex(-1_000L))
        assertEquals(0, synced.getActiveLineIndex(5_000L))
        assertEquals(1, synced.getActiveLineIndex(10_000L))
        assertEquals(2, synced.getActiveLineIndex(90_000L))
    }

    @Test
    fun getActiveLineIndex_returnsMinusOneWhenUnsynced() {
        val plain = Lyrics(
            trackName = "Track",
            artistName = "Artist",
            plainLyrics = "just words"
        )

        assertEquals(-1, plain.getActiveLineIndex(5_000L))
    }
}
