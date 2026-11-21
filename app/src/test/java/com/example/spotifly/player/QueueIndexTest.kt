package com.example.spotifly.player

import com.example.spotifly.model.Song
import org.junit.Assert.assertEquals
import org.junit.Test

/** The queue may contain the same song twice; the player's index must win. */
class QueueIndexTest {

    private fun song(id: String) = Song(id = id, title = id, artist = "Artist")

    @Test
    fun duplicateIds_resolveToThePlayingOccurrence() {
        val queue = listOf(song("a"), song("b"), song("a"))

        assertEquals(2, resolveQueueIndex(queue, "a", 2))
        assertEquals(0, resolveQueueIndex(queue, "a", 0))
    }

    @Test
    fun controllerIndexDisagrees_fallsBackToMediaId() {
        val queue = listOf(song("a"), song("b"))

        // State and controller can briefly disagree; the id is still better than
        // returning a negative index and losing the current song.
        assertEquals(1, resolveQueueIndex(queue, "b", 0))
    }

    @Test
    fun missingMediaId_returnsMinusOne() {
        assertEquals(-1, resolveQueueIndex(listOf(song("a")), null, 0))
        assertEquals(-1, resolveQueueIndex(emptyList(), "a", 0))
    }
}
