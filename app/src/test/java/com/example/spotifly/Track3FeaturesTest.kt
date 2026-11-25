package com.example.spotifly

import com.example.spotifly.data.offline.StorageInspector
import com.example.spotifly.data.playlist.PlaylistBackupManager
import com.example.spotifly.model.Playlist
import com.example.spotifly.model.Song
import com.example.spotifly.model.SourceIds
import com.example.spotifly.player.SLEEP_FADE_WINDOW_MS
import com.example.spotifly.player.computeSleepFadeVolume
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class Track3FeaturesTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun damagedJsonIsRejected() {
        assertTrue(PlaylistBackupManager.parseImportPayload("{").isEmpty())
    }

    @Test
    fun restoringLocalSongsChecksMetadataAcrossDeviceIds() {
        val original = Song("local_42", "Night", "Luna", durationMs = 180000L, isLocal = true)
        val backup = PlaylistBackupManager.exportJson(listOf(Playlist("p", "Mix", songs = listOf(original))))
        val collision = original.copy(title = "Day")
        val actual = original.copy(id = "local_7", internalId = "local_7")
        val restored = PlaylistBackupManager.parseImportPayload(backup, knownLibrary = listOf(collision, actual))
        assertEquals(actual.id, restored.single().songs.single().id)
        val missing = PlaylistBackupManager.parseImportPayload(backup, knownLibrary = listOf(collision))
            .single().songs.single()
        assertEquals(original.title, missing.title)
        assertTrue(missing.id != collision.id)
    }

    @Test
    fun computeSleepFadeVolume_tapersSmoothlyAcrossFinal30Seconds() {
        assertEquals(1f, computeSleepFadeVolume(60_000L), 0.0001f)
        assertEquals(1f, computeSleepFadeVolume(SLEEP_FADE_WINDOW_MS), 0.0001f)
        assertEquals(0.25f, computeSleepFadeVolume(15_000L), 0.0001f)
        assertEquals(0.01f, computeSleepFadeVolume(3_000L), 0.001f)
        assertEquals(0f, computeSleepFadeVolume(0L), 0.0001f)
        assertEquals(0f, computeSleepFadeVolume(-500L), 0.0001f)
    }

    @Test
    fun playlistBackup_m3uRoundTripsAndResolvesKnownSongs() {
        val knownStarboy = Song(
            id = "saavn_starboy",
            title = "Starboy",
            artist = "The Weeknd",
            durationMs = 230_000L,
            sourceIds = SourceIds(saavn = "starboy")
        )
        val playlist = Playlist(
            id = "pl_1",
            name = "Late Night Drive",
            songs = listOf(knownStarboy)
        )

        val exported = PlaylistBackupManager.exportM3u(playlist)
        assertTrue(exported.startsWith("#EXTM3U"))
        assertTrue(exported.contains("#PLAYLIST:Late Night Drive"))
        assertTrue(exported.contains("spotifly://track/saavn_starboy"))

        val imported = PlaylistBackupManager.parseImportPayload(
            rawText = exported,
            fallbackName = "Fallback",
            knownLibrary = listOf(knownStarboy)
        )
        assertEquals(1, imported.size)
        assertEquals("Late Night Drive", imported.first().name)
        assertEquals("saavn_starboy", imported.first().songs.first().id)
    }

    @Test
    fun playlistBackup_parsesExternalM3uAndJsonBundleCleanly() {
        val m3u = """
            #EXTM3U
            #PLAYLIST:Roadtrip Mix
            #EXTINF:175,Sabrina Carpenter - Espresso
            https://example.com/espresso.m4a
            #EXTINF:200,Dua Lipa - Levitating
            Dua Lipa - Levitating
        """.trimIndent()

        val parsedM3u = PlaylistBackupManager.parseImportPayload(m3u, fallbackName = "Fallback")
        assertEquals(1, parsedM3u.size)
        assertEquals("Roadtrip Mix", parsedM3u[0].name)
        assertEquals(2, parsedM3u[0].songs.size)
        assertEquals("Espresso", parsedM3u[0].songs[0].title)
        assertEquals("Sabrina Carpenter", parsedM3u[0].songs[0].artist)
        assertTrue(parsedM3u[0].songs[0].isPlayable)

        val jsonBackup = PlaylistBackupManager.exportJson(parsedM3u)
        val parsedJson = PlaylistBackupManager.parseImportPayload(jsonBackup)
        assertEquals(1, parsedJson.size)
        assertEquals("Roadtrip Mix", parsedJson[0].name)
        assertEquals(2, parsedJson[0].songs.size)
        assertEquals("Levitating", parsedJson[0].songs[1].title)
    }

    @Test
    fun storageInspector_calculatesDirectorySizesAndFormatsBytes() {
        assertEquals("0 KB", StorageInspector.formatBytes(0L))
        assertEquals("512 KB", StorageInspector.formatBytes(512 * 1024L))
        assertEquals("2.5 MB", StorageInspector.formatBytes((2.5 * 1024 * 1024).toLong()))
        assertEquals("1.50 GB", StorageInspector.formatBytes((1.5 * 1024 * 1024 * 1024).toLong()))

        val root = tempFolder.newFolder("cache_test")
        val imageCache = File(root, "image_cache").apply { mkdirs() }
        File(imageCache, "img.bin").writeBytes(ByteArray(100))
        val shared = File(root, "shared_images").apply { mkdirs() }
        File(shared, "card.png").writeBytes(ByteArray(250))

        assertEquals(350L, StorageInspector.directoryBytes(root))
        assertEquals(250L, StorageInspector.directoryBytesExcluding(root, "image_cache"))
    }
}
