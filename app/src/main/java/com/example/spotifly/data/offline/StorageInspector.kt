package com.example.spotifly.data.offline

import android.content.Context
import android.os.StatFs
import coil.Coil
import coil.annotation.ExperimentalCoilApi
import com.example.spotifly.model.Song
import java.io.File
import java.util.Locale

data class StoredTrackUsage(
    val song: Song,
    val bytes: Long,
    val fileExists: Boolean
)

data class StorageBreakdown(
    val musicBytes: Long = 0L,
    val podcastBytes: Long = 0L,
    val imageCacheBytes: Long = 0L,
    val tempCacheBytes: Long = 0L,
    val appMetadataBytes: Long = 0L,
    val deviceTotalBytes: Long = 0L,
    val deviceFreeBytes: Long = 0L,
    val downloadedMusicTracks: List<StoredTrackUsage> = emptyList(),
    val downloadedPodcastEpisodes: List<StoredTrackUsage> = emptyList()
) {
    val totalManagedBytes: Long
        get() = musicBytes + podcastBytes + imageCacheBytes + tempCacheBytes
}

/**
 * Inspects exact on-device byte allocations across Spotifly's offline tracks,
 * podcast downloads, Coil image cache, shared exports/cards, and device free space.
 */
object StorageInspector {

    @OptIn(ExperimentalCoilApi::class)
    fun inspect(context: Context, downloadedSongs: List<Song>): StorageBreakdown {
        val trackUsages = downloadedSongs.map { song ->
            val audioFile = song.localFilePath?.let { File(it) }
            val metaFile = audioFile?.let { File(it.parentFile, it.nameWithoutExtension + ".json") }
            val bytes = (audioFile?.takeIf { it.exists() }?.length() ?: 0L) +
                (metaFile?.takeIf { it.exists() }?.length() ?: 0L)
            StoredTrackUsage(
                song = song,
                bytes = bytes,
                fileExists = audioFile?.exists() == true
            )
        }.sortedByDescending { it.bytes }

        val musicTracks = trackUsages.filterNot { it.song.isPodcast }
        val podcastTracks = trackUsages.filter { it.song.isPodcast }

        val musicBytes = musicTracks.sumOf { it.bytes }
        val podcastBytes = podcastTracks.sumOf { it.bytes }

        val imageCacheDir = File(context.cacheDir, "image_cache")
        val coilLoader = Coil.imageLoader(context)
        val imageCacheBytes = coilLoader.diskCache?.size ?: directoryBytes(imageCacheDir)

        val tempCacheBytes = directoryBytesExcluding(context.cacheDir, excludedTopLevelName = "image_cache")
        val appMetadataBytes = directoryBytesExcluding(context.filesDir, excludedTopLevelName = "offline_music")

        val (totalDevice, freeDevice) = runCatching {
            val stat = StatFs(context.filesDir.absolutePath)
            stat.totalBytes to stat.availableBytes
        }.getOrDefault(0L to 0L)

        return StorageBreakdown(
            musicBytes = musicBytes,
            podcastBytes = podcastBytes,
            imageCacheBytes = imageCacheBytes,
            tempCacheBytes = tempCacheBytes,
            appMetadataBytes = appMetadataBytes,
            deviceTotalBytes = totalDevice,
            deviceFreeBytes = freeDevice,
            downloadedMusicTracks = musicTracks,
            downloadedPodcastEpisodes = podcastTracks
        )
    }

    fun clearTempCache(context: Context): Long {
        var freed = 0L
        val children = context.cacheDir.listFiles() ?: return 0L
        for (child in children) {
            if (child.name == "image_cache") continue
            freed += deleteRecursivelyCounted(child)
        }
        return freed
    }

    internal fun directoryBytes(root: File?): Long {
        if (root == null || !root.exists()) return 0L
        if (root.isFile) return root.length()
        return root.listFiles()?.sumOf { directoryBytes(it) } ?: 0L
    }

    internal fun directoryBytesExcluding(root: File?, excludedTopLevelName: String): Long {
        if (root == null || !root.exists() || !root.isDirectory) return 0L
        return root.listFiles()
            ?.filterNot { it.name == excludedTopLevelName }
            ?.sumOf { directoryBytes(it) }
            ?: 0L
    }

    private fun deleteRecursivelyCounted(target: File): Long {
        if (!target.exists()) return 0L
        var freed = 0L
        if (target.isDirectory) {
            target.listFiles()?.forEach { child -> freed += deleteRecursivelyCounted(child) }
            target.delete()
        } else {
            val len = target.length()
            if (target.delete()) freed += len
        }
        return freed
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 KB"
        val kb = bytes / 1024.0
        if (kb < 1024.0) {
            return String.format(Locale.US, "%.0f KB", kb.coerceAtLeast(1.0))
        }
        val mb = kb / 1024.0
        return if (mb >= 1024.0) {
            String.format(Locale.US, "%.2f GB", mb / 1024.0)
        } else {
            String.format(Locale.US, "%.1f MB", mb)
        }
    }
}
