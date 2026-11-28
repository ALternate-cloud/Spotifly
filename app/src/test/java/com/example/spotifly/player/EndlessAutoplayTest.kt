package com.example.spotifly.player

import com.example.spotifly.model.PlaybackState
import com.example.spotifly.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EndlessAutoplayTest {

    private fun song(
        id: String,
        title: String = "Track $id",
        artist: String = "Artist $id",
        internalId: String = id,
        isPodcast: Boolean = false
    ) = Song(
        id = id,
        title = title,
        artist = artist,
        internalId = internalId,
        isPodcast = isPodcast
    )

    @Test
    fun remainingUpcomingTracks_computesSliceAfterCurrentIndex() {
        val songs = (0..4).map { song("s$it") }
        assertEquals(listOf(songs[2], songs[3], songs[4]), remainingUpcomingTracks(songs, 1))
        assertEquals(emptyList<Song>(), remainingUpcomingTracks(songs, 4))
        assertEquals(songs, remainingUpcomingTracks(songs, -1))
        assertEquals(emptyList<Song>(), remainingUpcomingTracks(emptyList(), 0))
    }

    @Test
    fun shouldRefillAutoplay_ignoresStaleAutoplayQueueSizeAndChecksActualRemaining() {
        val songs = (0..11).map { song("s$it") }
        // Stale autoplayQueue claims 12 items remain, but currentIndex is already at 7 -> only 4 remain (< 8).
        val staleState = PlaybackState(
            currentSong = songs[7],
            currentIndex = 7,
            queue = songs,
            autoplayQueue = songs,
            isAutoplay = true,
            isShuffle = false
        )
        assertEquals(4, remainingUpcomingNonManualCount(staleState))
        assertTrue(shouldRefillAutoplay(staleState))

        // With 10 tracks remaining after currentIndex, no refill is needed yet.
        val fullLookaheadState = staleState.copy(currentSong = songs[1], currentIndex = 1)
        assertEquals(10, remainingUpcomingNonManualCount(fullLookaheadState))
        assertFalse(shouldRefillAutoplay(fullLookaheadState))
    }

    @Test
    fun remainingUpcomingNonManualCount_excludesManualPrefixFromLookahead() {
        val current = song("cur")
        val manual = (1..6).map { song("m$it") }
        val auto = (1..3).map { song("a$it") }
        val state = PlaybackState(
            currentSong = current,
            currentIndex = 0,
            queue = listOf(current) + manual + auto,
            manualQueue = manual,
            autoplayQueue = auto,
            isAutoplay = true
        )

        assertEquals(3, remainingUpcomingNonManualCount(state))
        assertTrue(shouldRefillAutoplay(state))
    }

    @Test
    fun shouldRefillAutoplay_respectsDisabledAutoplayShuffleAndForcePlay() {
        val current = song("cur")
        val base = PlaybackState(
            currentSong = current,
            currentIndex = 0,
            queue = (0..14).map { song("s$it") },
            isAutoplay = true,
            isShuffle = false
        )

        assertFalse(shouldRefillAutoplay(base, forcePlay = false))
        assertTrue(shouldRefillAutoplay(base, forcePlay = true))
        assertFalse(shouldRefillAutoplay(base.copy(isAutoplay = false), forcePlay = true))
        assertFalse(shouldRefillAutoplay(base.copy(isShuffle = true), forcePlay = true))
    }

    @Test
    fun selectAutoplaySeedCandidates_prefersTailThenCurrentThenRecentNonPodcast() {
        val current = song("cur")
        val tailAuto = song("auto_tail")
        val firstAuto = song("auto_1")
        val podcastRecent = song("pod_1", isPodcast = true)
        val recent1 = song("rec_1")
        val recent2 = song("rec_2")

        val state = PlaybackState(
            currentSong = current,
            currentIndex = 0,
            queue = listOf(current, firstAuto, tailAuto),
            autoplayQueue = listOf(firstAuto, tailAuto)
        )

        val seeds = selectAutoplaySeedCandidates(
            state = state,
            fallbackSeed = current,
            recentPlayed = listOf(podcastRecent, recent1, current, recent2)
        )

        assertEquals(listOf(tailAuto, current, recent1, recent2), seeds)
    }

    @Test
    fun selectAutoplaySeedCandidates_fallsBackToQueueTailWhenAutoplayQueueEmpty() {
        val current = song("cur")
        val queueMid = song("q_mid")
        val queueTail = song("q_tail")
        val state = PlaybackState(
            currentSong = current,
            currentIndex = 0,
            queue = listOf(current, queueMid, queueTail),
            autoplayQueue = emptyList()
        )

        val seeds = selectAutoplaySeedCandidates(state = state, fallbackSeed = current)
        assertEquals(listOf(queueTail, current), seeds)
    }

    @Test
    fun mergeAutoplayBatch_deduplicatesByIdInternalIdRecordingAndTitleFamily() {
        val current = song("cur", title = "Tum Hi Ho", artist = "Arijit Singh")
        val queued = song("q1", title = "Kesariya", artist = "Arijit Singh", internalId = "int_q1")
        val state = PlaybackState(
            currentSong = current,
            currentIndex = 0,
            queue = listOf(current, queued),
            autoplayQueue = listOf(queued)
        )

        val dupId = song("q1", title = "Completely Different", artist = "Other")
        val dupInternalId = song("other_provider", title = "Other Title", artist = "Other", internalId = "int_q1")
        val dupTitleRemix = song("remix_1", title = "Tum Hi Ho (Lo-Fi Remix)", artist = "DJ Test")
        val dupSameRecording = song("yt_kesariya", title = "Kesariya", artist = "Arijit Singh")
        val podcast = song("pod", title = "Podcast Ep 1", artist = "Host", isPodcast = true)
        val fresh1 = song("fresh_1", title = "Channa Mereya", artist = "Arijit Singh")
        val fresh1DupInBatch = song("fresh_1_dup", title = "Channa Mereya", artist = "Arijit Singh")
        val fresh2 = song("fresh_2", title = "Raabta", artist = "Pritam")

        val merged = mergeAutoplayBatch(
            state,
            listOf(dupId, dupInternalId, dupTitleRemix, dupSameRecording, podcast, fresh1, fresh1DupInBatch, fresh2)
        )

        assertEquals(listOf(fresh1, fresh2), merged.appended)
        assertEquals(listOf(current, queued, fresh1, fresh2), merged.updatedState.queue)
        assertEquals(listOf(queued, fresh1, fresh2), merged.updatedState.autoplayQueue)
    }

    @Test
    fun syncSubQueuesOnTransition_advancesAcrossMultiStepSkips() {
        val m1 = song("m1")
        val m2 = song("m2")
        val a1 = song("a1")
        val a2 = song("a2")
        val a3 = song("a3")

        // Jump from index 0 straight to index 3 (a1 is now playing)
        val (remainingManual, remainingAuto) = syncSubQueuesOnTransition(
            previousIndex = 0,
            newIndex = 3,
            newSong = a1,
            manualQueue = listOf(m1, m2),
            autoplayQueue = listOf(a1, a2, a3)
        )

        assertEquals(emptyList<Song>(), remainingManual)
        assertEquals(listOf(a2, a3), remainingAuto)
    }

    @Test
    fun songsToPreResolve_boundsToAtMostFourUpcomingTracks() {
        val queue = (0..15).map { song("s$it") }
        val state = PlaybackState(currentSong = queue[2], currentIndex = 2, queue = queue)

        val toResolve = songsToPreResolve(state)
        assertEquals(AUTOPLAY_PRERESOLVE_COUNT, toResolve.size)
        assertEquals(listOf(queue[3], queue[4], queue[5], queue[6]), toResolve)
    }

    @Test
    fun retryCooldown_blocksImmediateTightLoopAndClearsAfterCooldown() {
        val failedAt = 10_000L
        assertFalse(isAutoplayRetryInCooldown(0L, failedAt + 100L))
        assertTrue(isAutoplayRetryInCooldown(failedAt, failedAt + 1_000L))
        assertTrue(isAutoplayRetryInCooldown(failedAt, failedAt + AUTOPLAY_RETRY_COOLDOWN_MS - 1))
        assertFalse(isAutoplayRetryInCooldown(failedAt, failedAt + AUTOPLAY_RETRY_COOLDOWN_MS))
    }

    @Test
    fun buildAutoplayExcludeIds_includesAllQueueManualAutoplayAndCurrentIds() {
        val current = song("c", internalId = "ic")
        val manual = song("m", internalId = "im")
        val auto = song("a", internalId = "ia")
        val state = PlaybackState(
            currentSong = current,
            queue = listOf(current, manual, auto),
            manualQueue = listOf(manual),
            autoplayQueue = listOf(auto)
        )

        assertEquals(setOf("c", "ic", "m", "im", "a", "ia"), buildAutoplayExcludeIds(state))
    }
}
