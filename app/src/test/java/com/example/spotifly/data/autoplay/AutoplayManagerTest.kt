package com.example.spotifly.data.autoplay

import com.example.spotifly.model.Song
import com.example.spotifly.model.SourceIds
import org.junit.Assert.*
import org.junit.Test

class AutoplayManagerTest {
    private fun song(id: String, artist: String = id) = Song(
        id = id, title = id, artist = artist, language = "hindi",
        durationMs = 220_000, sourceIds = SourceIds(saavn = id)
    )
    private val mellow = VibeVector(0.3, 0.4, 0.3, 0.7, 0.3)
    private val loud = VibeVector(0.95, 0.8, 0.9, 0.05, 0.95)

    @Test fun measuredVibeRejectsPartyTrackDespiteSameLanguage() {
        val seed = song("seed")
        val party = song("party")
        val calm = song("calm")
        val result = AutoplayManager.rankCandidates(seed, emptyList(),
            listOf(AutoplayCandidate(party), AutoplayCandidate(calm)),
            measured = { if (it.id == "party") loud else mellow })
        assertEquals(listOf(calm), result)
    }

    @Test fun excludesRecentRecordingsSkipsAndQueuedIdentities() {
        val recent = song("recent")
        val duplicate = recent.copy(id = "other-provider", internalId = "other-provider")
        val queued = song("queued").copy(internalId = "queued-identity")
        val skipped = song("skipped")
        val good = song("good")
        val result = AutoplayManager.rankCandidates(song("seed"), listOf(recent),
            listOf(duplicate, queued, skipped, good).map { AutoplayCandidate(it) },
            excludeIds = setOf("queued-identity"), userSkippedFastIds = setOf("skipped"),
            measured = { mellow })
        assertEquals(listOf(good), result)
    }

    @Test fun limitsBatchAndAlternatesArtists() {
        val candidates = (1..15).map { AutoplayCandidate(song("track$it", "artist${it % 3}")) }
        val result = AutoplayManager.rankCandidates(song("seed"), emptyList(), candidates, measured = { mellow })
        assertEquals(6, result.size)
        assertTrue(result.zipWithNext().all { (a, b) -> a.artist != b.artist })
    }
}
