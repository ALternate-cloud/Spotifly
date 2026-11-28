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

    @Test fun limitsBatchToDefaultSizeAndAlternatesArtists() {
        val candidates = (1..20).map { AutoplayCandidate(song("track$it", "artist${it % 3}")) }
        val result = AutoplayManager.rankCandidates(song("seed"), emptyList(), candidates, measured = { mellow })
        assertEquals(AutoplayManager.DEFAULT_BATCH_SIZE, result.size)
        assertTrue(result.zipWithNext().all { (a, b) -> a.artist != b.artist })
    }

    @Test fun respectsCustomTargetSize() {
        val candidates = (1..20).map { AutoplayCandidate(song("track$it", "artist${it % 3}")) }
        val result = AutoplayManager.rankCandidates(
            seed = song("seed"),
            recentPlayed = emptyList(),
            candidates = candidates,
            targetSize = 6,
            measured = { mellow }
        )
        assertEquals(6, result.size)
        assertTrue(result.zipWithNext().all { (a, b) -> a.artist != b.artist })
    }

    @Test fun usesRelaxedFallbackTierWhenPrimaryPoolExhaustedWhileRespectingFilters() {
        val primary = song("primary", "artist1")
        val midEnergy = song("mid", "artist2")
        val skippedMid = song("skipped_mid", "artist3")
        val excludedMid = song("excluded_mid", "artist4")
        val podcastMid = song("podcast_mid", "artist5").copy(isPodcast = true)
        val extremeLoud = song("party", "artist6")

        val midVector = VibeVector(energy = 0.78, valence = 0.55, tempo = 0.55, acousticness = 0.35, danceability = 0.60)

        val result = AutoplayManager.rankCandidates(
            seed = song("seed"),
            recentPlayed = emptyList(),
            candidates = listOf(
                AutoplayCandidate(primary),
                AutoplayCandidate(midEnergy),
                AutoplayCandidate(skippedMid),
                AutoplayCandidate(excludedMid),
                AutoplayCandidate(podcastMid),
                AutoplayCandidate(extremeLoud)
            ),
            excludeIds = setOf("excluded_mid"),
            userSkippedFastIds = setOf("skipped_mid"),
            targetSize = 5,
            measured = {
                when (it.id) {
                    "party" -> loud
                    "mid", "skipped_mid", "excluded_mid", "podcast_mid" -> midVector
                    else -> mellow
                }
            }
        )

        assertEquals(listOf(primary, midEnergy), result)
    }

    @Test fun skipsRelaxedTierWhenPrimaryPoolMeetsTargetSize() {
        val primarySongs = (1..4).map { AutoplayCandidate(song("p$it", "artist${it % 3}")) }
        val midEnergy = AutoplayCandidate(song("mid", "artist4"))
        val midVector = VibeVector(energy = 0.78, valence = 0.55, tempo = 0.55, acousticness = 0.35, danceability = 0.60)

        val result = AutoplayManager.rankCandidates(
            seed = song("seed"),
            recentPlayed = emptyList(),
            candidates = primarySongs + midEnergy,
            targetSize = 4,
            measured = { if (it.id == "mid") midVector else mellow }
        )

        assertEquals(4, result.size)
        assertFalse(result.any { it.id == "mid" })
    }
}
