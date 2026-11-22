package com.example.spotifly.data.cloud
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
class LibraryProfileFilesTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun corruptHydrationFailsAndPreservesOriginalBytes() {
        val file = File(temporary.root, LibrarySyncRules.profileName(null)).apply { writeText("broken-original") }
        assertTrue(runCatching { LibraryProfileFiles(temporary.root).read(null) }.isFailure)
        assertEquals("broken-original", file.readText())
    }
    @Test fun switchingAccountsKeepsPendingOperationsSeparateAfterReopen() {
        val files = LibraryProfileFiles(temporary.root)
        val a = LibraryProfile(pending = listOf(LibraryOperation("a", "p:a")))
        val b = LibraryProfile(pending = listOf(LibraryOperation("b", "p:b")))
        files.write("A", a); files.write("B", b)
        val reopened = LibraryProfileFiles(temporary.root)
        assertEquals(a, reopened.read("A")); assertEquals(b, reopened.read("B")); assertNull(reopened.read(null))
    }
    @Test fun interruptedWriteRecoversCompleteJournalAndPendingEdits() {
        val files = LibraryProfileFiles(temporary.root)
        val prior = LibraryProfile(pending = listOf(LibraryOperation("durable", "p:a")))
        files.write("A", prior)
        val file = File(temporary.root, LibrarySyncRules.profileName("A"))
        assertTrue(file.renameTo(File(file.path + ".bak")))
        file.writeText("partial-write")
        assertEquals(prior, LibraryProfileFiles(temporary.root).read("A"))
    }
}
