package com.example.spotifly.data.autoplay

import com.example.spotifly.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ranking rules: audio cosine first, then scene, artist, and last-resort
 * fallbacks. Measured vectors stand in for decoded audio so the tests exercise
 * the audio-similarity contract, not keyword guesses.
 */
class VibeRankerTest {

    private fun song(
        id: String,
        title: String,
        artist: String,
        language: String? = null,
        genre: String? = null,
        durationMs: Long = 210_000,
        year: Int = 2022
    ) = Song(
        id = id,
        title = title,
        artist = artist,
        album = title,
        language = language,
        genre = genre,
        durationMs = durationMs,
        year = year,
        streamUrl = "https://example.test/$id"
    )

    /** A mellow, slow romantic ballad — the measured feel of the seed track. */
    private val softBalladVector = VibeVector(
        energy = 0.35,
        valence = 0.40,
        tempo = 0.33,
        acousticness = 0.55,
        danceability = 0.40
    )

    /** A loud, fast club record — what a bass-drop remix measures as. */
    private val clubBangerVector = VibeVector(
        energy = 0.90,
        valence = 0.70,
        tempo = 0.85,
        acousticness = 0.05,
        danceability = 0.90
    )

    private val mereNishan = song(
        id = "saavn_nishan",
        title = "Mere Nishan",
        artist = "Mohammed",
        language = "hindi",
        durationMs = 264_000
    )

    private val ruposh = song(
        id = "saavn_ruposh",
        title = "Ruposh",
        artist = "Wajhi Farooki",
        language = "urdu",
        durationMs = 233_000
    )

    private val partyRemix = song(
        id = "saavn_party",
        title = "Party Bass Drop Remix",
        artist = "DJ Test",
        language = "hindi",
        durationMs = 200_000
    )

    private val sameArtistSad = song(
        id = "saavn_same_artist",
        title = "Tum Meri Zindagi Ho",
        artist = "Mohammed",
        language = "hindi",
        durationMs = 148_000,
        year = 2021
    )

    /** Steps in for (song, vector) pairs so candidates get measured audio. */
    private inline fun measured(vararg pairs: Pair<Song, VibeVector>): (Song) -> VibeVector? {
        val map = pairs.associate { (song, vector) -> song.id to vector }
        return { song -> map[song.id] }
    }

    @Test
    fun similarMeasuredAudio_beatsUpbeatDanceTrack() {
        val pick = VibeRanker.pick(
            currentSong = mereNishan,
            history = emptyList(),
            candidates = listOf(
                AutoplayCandidate(partyRemix),
                AutoplayCandidate(ruposh),
                AutoplayCandidate(sameArtistSad)
            ),
            measured = measured(
                mereNishan to softBalladVector,
                ruposh to VibeVector(energy = 0.30, valence = 0.38, tempo = 0.35, acousticness = 0.60, danceability = 0.45),
                partyRemix to clubBangerVector
            )
        )

        assertNotNull(pick)
        assertEquals(ruposh.id, pick?.song?.id)
        assertEquals(PickReason.VIBE, pick?.reason)
        assertTrue("audio similarity should be high", pick!!.similarity >= 0.78)
    }

    @Test
    fun measuredCandidate_beatsStrongerMetadataPrior() {
        // A different-artist banger that measured audio says is the same feel
        // must beat a modest metadata-only candidate from the catalogue.
        val measuredBanger = song(
            id = "saavn_measured",
            title = "Neon Nights",
            artist = "Synth Duo",
            language = "hindi"
        )
        val metadataOnly = song(
            id = "saavn_meta",
            title = "Some Ordinary Words",
            artist = "Ordinary Band",
            language = "hindi"
        )

        val pick = VibeRanker.pick(
            currentSong = mereNishan,
            history = emptyList(),
            candidates = listOf(
                AutoplayCandidate(metadataOnly, CandidateSource.RECOMMENDED, 0),
                AutoplayCandidate(measuredBanger)
            ),
            measured = measured(
                mereNishan to clubBangerVector,
                measuredBanger to VibeVector(energy = 0.88, valence = 0.72, tempo = 0.83, acousticness = 0.08, danceability = 0.88)
            )
        )

        assertEquals(measuredBanger.id, pick?.song?.id)
    }

    @Test
    fun otherVersionsOfTheSameSongName_areNeverRecommended() {
        val original = song("alpha", "Shape of You", "Ed Sheeran", language = "english")
        val stormzyRemix = song("beta", "Shape of You (Stormzy Remix)", "Ed Sheeran", language = "english")
        val galantisRemix = song("gamma", "Shape of You (Galantis Remix)", "Galantis", language = "english")
        val freshTrack = song("delta", "Something New", "Other Artist", language = "english")

        val pick = VibeRanker.pick(
            currentSong = original,
            history = emptyList(),
            // Both remixes measured IDENTICAL to the seed — closer in audio
            // than the fresh track. The title rule, not similarity, decides.
            candidates = listOf(
                AutoplayCandidate(stormzyRemix),
                AutoplayCandidate(galantisRemix),
                AutoplayCandidate(freshTrack)
            ),
            measured = measured(
                original to VibeVector(energy = 0.75, valence = 0.65, tempo = 0.60, acousticness = 0.25, danceability = 0.75),
                stormzyRemix to VibeVector(energy = 0.75, valence = 0.65, tempo = 0.60, acousticness = 0.25, danceability = 0.75),
                galantisRemix to VibeVector(energy = 0.75, valence = 0.65, tempo = 0.60, acousticness = 0.25, danceability = 0.75),
                freshTrack to VibeVector(energy = 0.70, valence = 0.60, tempo = 0.55, acousticness = 0.30, danceability = 0.70)
            )
        )

        assertEquals(freshTrack.id, pick?.song?.id)
    }

    @Test
    fun queuedSongsExcludeTheirTitleFamily() {
        val queued = song("queue_1", "Pixel", "Queue Artist", language = "hindi")
        val sameTitleDifferentId = song("queue_2", "Pixel", "Different Artist", language = "hindi")
        val fresh = song("fresh_1", "Morning Dew", "Dew Artist", language = "hindi")

        val pick = VibeRanker.pick(
            currentSong = mereNishan,
            history = emptyList(),
            candidates = listOf(AutoplayCandidate(sameTitleDifferentId), AutoplayCandidate(fresh)),
            excludeIds = setOf(queued.id),
            excludeTitles = setOf(VibeFeatures.titleKey(queued))
        )

        assertEquals(fresh.id, pick?.song?.id)
    }

    @Test
    fun historyTitleFamily_isNotRepeated() {
        val played = song("played_1", "Ruposh", "Someone Else", language = "urdu")
        val sameNameDifferentArtist = song("played_2", "Ruposh (Cover)", "Yet Another", language = "urdu")
        val fresh = song("fresh_1", "Wajah", "New Artist", language = "hindi")

        val pick = VibeRanker.pick(
            currentSong = mereNishan,
            history = listOf(played),
            candidates = listOf(AutoplayCandidate(sameNameDifferentArtist), AutoplayCandidate(fresh))
        )

        assertEquals(fresh.id, pick?.song?.id)
    }

    @Test
    fun recentlyPlayedSongs_areNeverRepeated() {
        val pick = VibeRanker.pick(
            currentSong = mereNishan,
            history = listOf(ruposh, sameArtistSad),
            candidates = listOf(AutoplayCandidate(ruposh), AutoplayCandidate(partyRemix))
        )

        assertEquals(partyRemix.id, pick?.song?.id)
    }

    @Test
    fun sameTitleUnderAnotherId_isTreatedAsTheSameRecording() {
        val duplicate = ruposh.copy(id = "itunes_999", artist = "Wajhi Farooki")
        val pick = VibeRanker.pick(
            currentSong = mereNishan,
            history = listOf(ruposh),
            candidates = listOf(AutoplayCandidate(duplicate), AutoplayCandidate(partyRemix))
        )

        assertNotEquals(duplicate.id, pick?.song?.id)
        assertEquals(partyRemix.id, pick?.song?.id)
    }

    @Test
    fun differentArtistIsPreferred_whenOnlySameArtistMatchesTheVibe() {
        val pick = VibeRanker.pick(
            currentSong = mereNishan,
            history = emptyList(),
            candidates = listOf(AutoplayCandidate(sameArtistSad))
        )

        // Nothing else exists, so the same-artist fallback wins over stopping.
        assertEquals(sameArtistSad.id, pick?.song?.id)
        assertEquals(PickReason.SAME_ARTIST, pick?.reason)
    }

    @Test
    fun unrelatedTrack_stillPlaysAsLastResort() {
        val english = song(
            id = "saavn_english",
            title = "Ordinary Days",
            artist = "Someone Else",
            language = "english"
        )
        val pick = VibeRanker.pick(
            currentSong = mereNishan,
            history = emptyList(),
            candidates = listOf(AutoplayCandidate(english))
        )

        assertEquals(english.id, pick?.song?.id)
        assertEquals(PickReason.LAST_RESORT, pick?.reason)
    }

    @Test
    fun noCandidates_returnsNull() {
        assertEquals(
            null,
            VibeRanker.pick(mereNishan, emptyList(), emptyList())
        )
    }

    @Test
    fun shortClipsAndUnplayableTracks_areSkipped() {
        val clip = song("clip", "Interlude", "Somebody", durationMs = 8_000)
        val noUrl = ruposh.copy(id = "broken", streamUrl = null, contentUriString = null)

        assertEquals(
            null,
            VibeRanker.pick(
                mereNishan,
                emptyList(),
                listOf(AutoplayCandidate(clip), AutoplayCandidate(noUrl))
            )
        )
    }

    @Test
    fun podcastEpisodes_areNeverAutoplayCandidates() {
        val episode = song(
            id = "podcast_episode",
            title = "Episode 12",
            artist = "Some Show",
            durationMs = 2_400_000
        ).copy(isPodcast = true)

        val pick = VibeRanker.pick(
            currentSong = mereNishan,
            history = emptyList(),
            candidates = listOf(AutoplayCandidate(episode))
        )

        assertEquals(null, pick)
    }

    @Test
    fun targetBlend_keepsCurrentTrackDominant() {
        val current = VibeProfile(
            vector = VibeVector(energy = 0.0, valence = 0.0, tempo = 0.0, acousticness = 0.0, danceability = 0.0),
            language = "hindi",
            genre = null,
            artistKey = "artist",
            year = 2020
        )
        val history = List(5) {
            current.copy(vector = VibeVector(energy = 1.0, valence = 1.0, tempo = 1.0, acousticness = 1.0, danceability = 1.0))
        }

        val target = VibeRanker.targetProfile(current, history)

        // Current weight 0.62 vs. history total 0.30 of the 0.92 normalised total.
        assertEquals(0.30 / 0.92, target.vector.energy, 0.001)
        assertTrue("current track must stay the strongest single input", target.vector.energy < 0.5)
    }

    @Test
    fun targetBlend_decaysOlderHistory() {
        val current = VibeProfile(
            vector = VibeVector(energy = 0.0),
            language = "hindi",
            genre = null,
            artistKey = "artist",
            year = 2020
        )
        val high = current.copy(vector = VibeVector(energy = 1.0))
        val low = current.copy(vector = VibeVector(energy = 0.0))

        val recent = VibeRanker.targetProfile(current, listOf(high, low, low))
        val older = VibeRanker.targetProfile(current, listOf(low, low, high))

        assertTrue(
            "the most recent play should influence the target more than an older one",
            recent.vector.energy > older.vector.energy
        )
    }

    @Test
    fun languageNeighbourPreferred_overBiggerCataloguePrior() {
        val punjabi = song("saavn_punjabi", "Soft Dhun", "Another Artist", language = "punjabi")
        val english = song("saavn_english", "Soft Song", "Indie Act", language = "english")

        val pick = VibeRanker.pick(
            currentSong = mereNishan,
            history = emptyList(),
            candidates = listOf(
                AutoplayCandidate(english, CandidateSource.RECOMMENDED, 0),
                AutoplayCandidate(punjabi, CandidateSource.VIBE)
            )
        )

        assertEquals(punjabi.id, pick?.song?.id)
    }
}
