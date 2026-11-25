package com.example.spotifly.service

import androidx.media3.common.Player
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicPlaybackServiceTest {

    @Test
    fun shouldKeepServiceOnTaskRemoved_onlyWhenActivelyPlayingWithItems() {
        assertTrue(
            MusicPlaybackService.shouldKeepServiceOnTaskRemoved(
                playWhenReady = true,
                playbackState = Player.STATE_READY,
                mediaItemCount = 1
            )
        )
        assertTrue(
            MusicPlaybackService.shouldKeepServiceOnTaskRemoved(
                playWhenReady = true,
                playbackState = Player.STATE_BUFFERING,
                mediaItemCount = 3
            )
        )
        // Paused: must tear down notification & service on task removal
        assertFalse(
            MusicPlaybackService.shouldKeepServiceOnTaskRemoved(
                playWhenReady = false,
                playbackState = Player.STATE_READY,
                mediaItemCount = 2
            )
        )
        // Ended / idle / empty queue: must tear down on task removal
        assertFalse(
            MusicPlaybackService.shouldKeepServiceOnTaskRemoved(
                playWhenReady = true,
                playbackState = Player.STATE_ENDED,
                mediaItemCount = 1
            )
        )
        assertFalse(
            MusicPlaybackService.shouldKeepServiceOnTaskRemoved(
                playWhenReady = true,
                playbackState = Player.STATE_IDLE,
                mediaItemCount = 1
            )
        )
        assertFalse(
            MusicPlaybackService.shouldKeepServiceOnTaskRemoved(
                playWhenReady = true,
                playbackState = Player.STATE_READY,
                mediaItemCount = 0
            )
        )
    }
}
