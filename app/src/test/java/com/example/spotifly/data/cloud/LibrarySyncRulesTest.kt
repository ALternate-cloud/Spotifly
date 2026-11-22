package com.example.spotifly.data.cloud

import com.example.spotifly.model.Song
import org.junit.Assert.*
import org.junit.Test

class LibrarySyncRulesTest {
    @Test fun staleEditCannotResurrectDeletedRecord() {
        val tombstone = LibraryRecord("p:a", revision = 8, deleted = true)
        assertFalse(LibrarySyncRules.accepts(tombstone, LibraryOperation("edit", "p:a", 7, payload = "{}")))
        assertTrue(LibrarySyncRules.accepts(tombstone, LibraryOperation("recreate", "p:a", 8, payload = "{}")))
    }
    @Test fun duplicateDeliveryIsIdempotent() {
        val record = LibraryRecord("f:a", revision = 4, operationId = "same")
        assertTrue(LibrarySyncRules.alreadyApplied(record, LibraryOperation("same", "f:a", 3)))
    }
    @Test fun unrelatedLiveChangesCanCommitDespiteOlderObservedRevision() {
        assertTrue(LibrarySyncRules.accepts(LibraryRecord("p:a", revision = 12), LibraryOperation("new", "p:a", 2)))
    }
    @Test fun trackIdentityUsesProviderAndNeverDisplayText() {
        assertNotEquals(LibrarySyncRules.songKey(Song("one", "Title", "Artist")), LibrarySyncRules.songKey(Song("two", "Title", "Artist")))
        assertEquals(LibrarySyncRules.songKey(Song("saavn_123", "Old")), LibrarySyncRules.songKey(Song("different", "New", sourceIds = com.example.spotifly.model.SourceIds(saavn = "123"))))
    }
    @Test fun cloudTrackDoesNotContainDeviceOrStreamLocations() {
        val song = LibrarySyncRules.cloudSong(Song("local_1", "Track", isLocal = true, streamUrl = "https://expired", contentUriString = "content://private", localFilePath = "/secret", artworkUrl = "/private", isDownloaded = true))
        assertNull(song.streamUrl); assertNull(song.contentUriString); assertNull(song.localFilePath)
        assertNull(song.artworkUrl); assertFalse(song.isPlayable); assertFalse(song.isDownloaded)
    }
    @Test fun failedPendingOperationSurvivesRoundTrip() {
        val profile = LibraryProfile(pending = listOf(LibraryOperation("retry", "p:a", 3, payload = "data")))
        val encoded = LibrarySyncRules.json.encodeToString(LibraryProfile.serializer(), profile)
        assertEquals(profile, LibrarySyncRules.json.decodeFromString(LibraryProfile.serializer(), encoded))
    }
    @Test fun accountFilesCannotCollideOrEscapeDirectory() {
        assertNotEquals(LibrarySyncRules.profileName("a"), LibrarySyncRules.profileName("b"))
        assertFalse(LibrarySyncRules.profileName("../../account").contains('/'))
        assertNotEquals(LibrarySyncRules.profileName(null), LibrarySyncRules.profileName("guest"))
    }
    @Test fun rejectedEditDoesNotRebaseFollowingEditOntoRemoteTombstone() {
        val first = LibraryOperation("first", "p:a", 2, payload = "old")
        val second = LibraryOperation("second", "p:a", 2, payload = "new", predecessor = "first")
        val profile = LibraryProfile(records = mapOf("p:a" to LibraryRecord("p:a", 2, payload = "new")), pending = listOf(first, second))
        val remote = LibraryRecord("p:a", 8, deleted = true)
        val result = LibrarySyncRules.acknowledge(profile, first, remote, null)
        assertEquals(2L, result.pending.single().observedRevision)
        assertFalse(LibrarySyncRules.accepts(remote, result.pending.single()))
        assertEquals("new", result.records.getValue("p:a").payload)
    }
    @Test fun acceptedEditRebasesSuccessorToItsCommittedRevisionOnly() {
        val first = LibraryOperation("first", "p:a", 2, payload = "old")
        val second = LibraryOperation("second", "p:a", 2, payload = "new", predecessor = "first")
        val profile = LibraryProfile(records = mapOf("p:a" to LibraryRecord("p:a", 2, payload = "new")), pending = listOf(first, second))
        val laterDeletion = LibraryRecord("p:a", 9, deleted = true)
        val result = LibrarySyncRules.acknowledge(profile, first, laterDeletion, 7)
        assertEquals(7L, result.pending.single().observedRevision)
        assertFalse(LibrarySyncRules.accepts(laterDeletion, result.pending.single()))
    }
    @Test fun serverReadKeepsEditsCreatedDuringRestoration() {
        val operation = LibraryOperation("pending", "p:a", payload = "local")
        val local = LibraryRecord("p:a", payload = "local")
        val profile = LibraryProfile(mapOf("p:a" to local), listOf(operation))
        val result = LibrarySyncRules.merge(profile, listOf(LibraryRecord("p:a", 4, payload = "remote"), LibraryRecord("p:b", 1, payload = "different")))
        assertEquals("local", result.records.getValue("p:a").payload)
        assertEquals("different", result.records.getValue("p:b").payload)
        assertEquals(listOf(operation), result.pending)
    }
    @Test fun guestImportMarkerCannotBeAcknowledgedWhileAnEditIsPending() {
        val profile = LibraryProfile(pending = listOf(LibraryOperation("pending", "p:a")), guestImport = "A", guestImportRevision = "snapshot1")
        assertTrue(runCatching { LibrarySyncRules.acknowledgeGuest(profile) }.isFailure)
        assertFalse(profile.guestImportAcknowledged)
    }
    @Test fun acknowledgedGuestSnapshotRetainsItsExactRevisionAndTargetAccount() {
        val profile = LibraryProfile(guestImport = "A", guestImportRevision = "snapshot1")
        val acknowledged = LibrarySyncRules.acknowledgeGuest(profile)
        assertTrue(acknowledged.guestImportAcknowledged)
        assertEquals("snapshot1", acknowledged.guestImportRevision)
        assertEquals("A", acknowledged.guestImport)
    }
    @Test fun localPlaceholdersAreDeviceScopedAndSanitizationIsIdempotent() {
        val local = Song("local_123", "Local", isLocal = true)
        val a = LibrarySyncRules.cloudSong(local, "phone-A")
        val b = LibrarySyncRules.cloudSong(local, "phone-B")
        assertNotEquals(a.id, b.id)
        assertEquals(a, LibrarySyncRules.cloudSong(a, "phone-B"))
    }
    @Test fun boundedRevisionChunksPreserveUnicodeAtBoundary() {
        val payload = "a".repeat(39_999) + "\uD83D\uDE00" + "b".repeat(50_000)
        val chunks = LibrarySyncRules.chunks(payload)
        assertEquals(payload, chunks.joinToString(""))
        chunks.forEach { assertTrue(it.toByteArray().size <= 160_000); assertEquals(it, it.toByteArray().toString(Charsets.UTF_8)) }
    }
    @Test fun guestEditsAfterSuccessfulImportStageOnlyChangedRecordsIncludingRemovals() {
        val original = LibraryRecord("p:old", payload = "unchanged")
        val liked = LibraryRecord("f:liked", payload = "song")
        val baseline = mapOf(original.key to original, liked.key to liked)
        val account = LibraryProfile(guestImport = "A", guestImportAcknowledged = true, guestImportRevision = "old", guestSnapshot = baseline)
        val changed = baseline + ("p:new" to LibraryRecord("p:new", payload = "new")) + ("f:liked" to liked.copy(deleted = true, payload = ""))
        val staged = LibrarySyncRules.stageGuest(account, LibraryProfile(records = changed, guestImport = "A"), "A") { java.util.UUID.randomUUID().toString() }
        assertEquals(setOf("p:new", "f:liked"), staged.pending.map { it.key }.toSet())
        assertTrue(staged.pending.single { it.key == "f:liked" }.deleted)
        assertFalse(staged.guestImportAcknowledged)
        assertEquals(staged, LibrarySyncRules.stageGuest(staged, LibraryProfile(records = changed, guestImport = "A"), "A") { error("must not replay") })
    }
    @Test fun acknowledgedGuestDataDoesNotCrossToAnotherAccount() {
        val accountB = LibraryProfile()
        val guestA = LibraryProfile(mapOf("p:a" to LibraryRecord("p:a")), guestImport = "A")
        assertEquals(accountB, LibrarySyncRules.stageGuest(accountB, guestA, "B") { error("must not import") })
    }
    @Test fun guestPlaylistMergeRetainsAdditionsDuringServerReadAndUnrelatedCloudSongs() {
        val a = Song("a", "A"); val b = Song("b", "B"); val c = Song("c", "C")
        val base = com.example.spotifly.model.Playlist("p", "P", songs = listOf(a))
        val result = LibrarySyncRules.mergeGuestPlaylist(base, base.copy(songs = listOf(a, c)), base.copy(songs = listOf(b)), "device")
        assertEquals(listOf("b", "a", "c"), result.songs.map { it.id })
    }
    @Test fun guestPlaylistMergeRetainsRemovalsDuringServerRead() {
        val a = Song("a", "A"); val b = Song("b", "B")
        val base = com.example.spotifly.model.Playlist("p", "P", songs = listOf(a))
        val result = LibrarySyncRules.mergeGuestPlaylist(base, base.copy(songs = emptyList()), base.copy(songs = listOf(b, a)), "device")
        assertEquals(listOf("b"), result.songs.map { it.id })
    }
    @Test fun guestLikeUnlikeRelikeUsesLastObservedAccountTombstoneRevision() {
        val liked = LibraryRecord("f:x", payload = "song")
        var guest = LibraryProfile(records = mapOf(liked.key to liked))
        var account = LibrarySyncRules.stageGuest(LibraryProfile(), guest, "A") { "like" }
        var operation = account.pending.single()
        val firstServer = liked.copy(revision = 1, operationId = operation.id)
        account = LibrarySyncRules.acknowledgeGuest(LibrarySyncRules.acknowledge(account, operation, firstServer, 1))
        guest = guest.copy(records = mapOf(liked.key to liked.copy(deleted = true, payload = "")), guestImport = "A")
        account = LibrarySyncRules.stageGuest(account, guest, "A") { "unlike" }
        operation = account.pending.single()
        assertEquals(1L, operation.observedRevision)
        val tombstone = LibraryRecord(liked.key, revision = 2, deleted = true, operationId = operation.id)
        account = LibrarySyncRules.acknowledgeGuest(LibrarySyncRules.acknowledge(account, operation, tombstone, 2))
        guest = guest.copy(records = mapOf(liked.key to liked))
        val relike = LibrarySyncRules.stageGuest(account, guest, "A") { "relike" }.pending.single()
        assertEquals(2L, relike.observedRevision)
        assertTrue(LibrarySyncRules.accepts(tombstone, relike))
    }
    @Test fun unchangedGuestSnapshotDoesNotResurrectAnAccountTombstone() {
        val original = LibraryRecord("f:x", payload = "song")
        val account = LibraryProfile(records = mapOf("f:x" to original.copy(revision = 3, deleted = true)), guestImport = "A", guestSnapshot = mapOf("f:x" to original))
        val staged = LibrarySyncRules.stageGuest(account, LibraryProfile(records = mapOf("f:x" to original), guestImport = "A"), "A") { error("unchanged data must not replay") }
        assertTrue(staged.pending.isEmpty())
        assertTrue(staged.records.getValue("f:x").deleted)
    }
    @Test fun guestMergeKeepsSameDeviceLocalTrackPlayable() {
        val local = Song("local_123", "Local", isLocal = true, contentUriString = "content://media/123", localFilePath = "/device/audio.mp3", isDownloaded = true, isPlayable = true)
        val playlist = com.example.spotifly.model.Playlist("p", "P", songs = listOf(local))
        val remote = playlist.copy(songs = listOf(LibrarySyncRules.cloudSong(local, "same-phone")))
        val restored = LibrarySyncRules.mergeGuestPlaylist(playlist, playlist, remote, "same-phone").songs.single()
        assertEquals(local.id, restored.id)
        assertEquals(local.contentUriString, restored.contentUriString)
        assertEquals(local.localFilePath, restored.localFilePath)
        assertTrue(restored.isPlayable)
        assertTrue(restored.isDownloaded)
    }
    @Test fun guestMergeNeverAttachesOtherPhoneLocalFilesToPlaceholder() {
        val local = Song("local_123", "Local", isLocal = true, contentUriString = "content://media/123")
        val playlist = com.example.spotifly.model.Playlist("p", "P", songs = listOf(local))
        val foreign = LibrarySyncRules.cloudSong(local, "different-phone")
        val result = LibrarySyncRules.mergeGuestPlaylist(playlist, playlist, playlist.copy(songs = listOf(foreign)), "this-phone")
        assertEquals(2, result.songs.size)
        assertNull(result.songs.first().contentUriString)
        assertEquals("content://media/123", result.songs.last().contentUriString)
    }
}
