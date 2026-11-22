package com.example.spotifly.data.cloud
import org.junit.Assert.*
import org.junit.Test
class LibrarySessionGenerationTest {
    @Test fun lateCallbacksCannotCrossAccountSwitchEvenAfterSwitchingBack() {
        val session = LibrarySessionGeneration()
        val firstA = session.advance("A")
        assertTrue(session.isCurrent("A", firstA))
        session.advance("B")
        assertFalse(session.isCurrent("A", firstA))
        val secondA = session.advance("A")
        assertFalse(session.isCurrent("A", firstA))
        assertTrue(session.isCurrent("A", secondA))
    }
    @Test fun signingOutInvalidatesAuthenticatedWork() {
        val session = LibrarySessionGeneration()
        val token = session.advance("A")
        session.advance(null)
        assertFalse(session.isCurrent("A", token))
    }
}
