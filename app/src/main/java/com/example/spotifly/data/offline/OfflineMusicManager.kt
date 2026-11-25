package com.example.spotifly.data.offline

import android.content.Context
import com.example.spotifly.model.DownloadStatus
import com.example.spotifly.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class OfflineMusicManager private constructor(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val offlineDir = File(context.filesDir, "offline_music").apply { if (!exists()) mkdirs() }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val _downloadedSongs = MutableStateFlow<List<Song>>(emptyList())
    val downloadedSongs: StateFlow<List<Song>> = _downloadedSongs.asStateFlow()

    private val _downloadStatus = MutableStateFlow<Map<String, DownloadStatus>>(emptyMap())
    val downloadStatus: StateFlow<Map<String, DownloadStatus>> = _downloadStatus.asStateFlow()

    // Percent (0-100) per song id for in-flight downloads
    private val _downloadProgress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Int>> = _downloadProgress.asStateFlow()

    // Active downloads keyed by song id, so a second tap can't start a parallel copy
    private val activeJobs = ConcurrentHashMap<String, Job>()

    // OkHttp calls must be cancelled too, otherwise a blocking socket read ignores Job.cancel()
    private val activeCalls = ConcurrentHashMap<String, Call>()

    init {
        loadDownloadedSongs()
    }

    fun loadDownloadedSongs() {
        scope.launch {
            val songs = mutableListOf<Song>()
            val statusMap = mutableMapOf<String, DownloadStatus>()

            val metaFiles = offlineDir.listFiles { _, name -> name.endsWith(".json") } ?: emptyArray()
            for (file in metaFiles) {
                try {
                    val content = file.readText()
                    val song = json.decodeFromString<Song>(content)
                    val audioFile = File(offlineDir, "${cleanFileName(song.id)}.m4a")
                    if (audioFile.exists() && audioFile.length() > 0) {
                        val offlineSong = song.copy(
                            isDownloaded = true,
                            localFilePath = audioFile.absolutePath,
                            contentUriString = audioFile.toURI().toString()
                        )
                        songs.add(offlineSong)
                        statusMap[song.id] = DownloadStatus.DOWNLOADED
                    } else {
                        file.delete()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            _downloadedSongs.value = songs
            _downloadStatus.value = statusMap
        }
    }

    fun isSongDownloaded(songId: String): Boolean {
        return _downloadedSongs.value.any { it.id == songId }
    }

    fun getDownloadedSong(songId: String): Song? {
        return _downloadedSongs.value.find { it.id == songId }
    }

    fun downloadSong(song: Song) {
        val streamUrl = song.streamUrl
        if (streamUrl.isNullOrBlank()) return
        if (isSongDownloaded(song.id)) return
        if (activeJobs.containsKey(song.id)) return // already downloading

        _downloadStatus.update { it + (song.id to DownloadStatus.DOWNLOADING) }
        _downloadProgress.update { it + (song.id to 0) }

        activeJobs[song.id] = scope.launch {
            try {
                val cleanId = cleanFileName(song.id)
                val targetAudioFile = File(offlineDir, "$cleanId.m4a")
                val targetMetaFile = File(offlineDir, "$cleanId.json")
                // Partial file from a cancelled attempt must not be resumed accidentally
                if (targetAudioFile.exists()) targetAudioFile.delete()

                val request = Request.Builder()
                    .url(streamUrl)
                    .header("User-Agent", "Mozilla/5.0")
                    .build()

                val call = httpClient.newCall(request)
                activeCalls[song.id] = call
                val response = try {
                    call.execute()
                } finally {
                    activeCalls.remove(song.id)
                }
                if (!response.isSuccessful || response.body == null) {
                    _downloadStatus.update { it + (song.id to DownloadStatus.FAILED) }
                    _downloadProgress.update { it - song.id }
                    return@launch
                }

                val source = response.body!!.byteStream()
                val totalBytes = response.body!!.contentLength()
                var copiedBytes = 0L
                var lastReportedPercent = -1

                FileOutputStream(targetAudioFile).use { output ->
                    source.use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            // A cancelled job must stop copying instead of finishing the file
                            if (!isActive) throw CancellationException("download cancelled")
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            copiedBytes += read
                            if (totalBytes > 0) {
                                val percent = ((copiedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                                if (percent > lastReportedPercent) {
                                    lastReportedPercent = percent
                                    _downloadProgress.update { it + (song.id to percent) }
                                }
                            }
                        }
                    }
                }

                val downloadedSong = song.copy(
                    isDownloaded = true,
                    localFilePath = targetAudioFile.absolutePath,
                    contentUriString = targetAudioFile.toURI().toString()
                )

                targetMetaFile.writeText(json.encodeToString(downloadedSong))

                _downloadedSongs.update { list -> list.filterNot { it.id == song.id } + downloadedSong }
                _downloadStatus.update { it + (song.id to DownloadStatus.DOWNLOADED) }
                _downloadProgress.update { it - song.id }
            } catch (e: CancellationException) {
                File(offlineDir, "${cleanFileName(song.id)}.m4a").delete()
                File(offlineDir, "${cleanFileName(song.id)}.json").delete()
                _downloadStatus.update { it - song.id }
                _downloadProgress.update { it - song.id }
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                val cleanId = cleanFileName(song.id)
                File(offlineDir, "$cleanId.m4a").delete() // remove partial file
                File(offlineDir, "$cleanId.json").delete()
                if (isActive) {
                    _downloadStatus.update { it + (song.id to DownloadStatus.FAILED) }
                } else {
                    // Cancelled mid-read: OkHttp surfaces call.cancel() as an IOException
                    _downloadStatus.update { it - song.id }
                }
                _downloadProgress.update { it - song.id }
            } finally {
                activeJobs.remove(song.id)
            }
        }
    }

    /** Cancels an in-flight download (no-op if it already finished). */
    fun cancelDownload(songId: String) {
        // Cancel the Job first so the copy loop sees isActive=false before the socket aborts
        activeJobs[songId]?.cancel()
        activeCalls.remove(songId)?.cancel()
        _downloadStatus.update { it - songId }
        _downloadProgress.update { it - songId }
    }

    fun removeDownload(songId: String) {
        cancelDownload(songId)
        scope.launch {
            val cleanId = cleanFileName(songId)
            val audioFile = File(offlineDir, "$cleanId.m4a")
            val metaFile = File(offlineDir, "$cleanId.json")

            if (audioFile.exists()) audioFile.delete()
            if (metaFile.exists()) metaFile.delete()

            _downloadedSongs.update { list -> list.filterNot { it.id == songId } }
            _downloadStatus.update { it - songId }
            _downloadProgress.update { it - songId }
        }
    }

    fun clearAllDownloads() {
        val currentIds = _downloadedSongs.value.map { it.id } + activeJobs.keys.toList()
        currentIds.distinct().forEach { cancelDownload(it) }
        scope.launch {
            offlineDir.listFiles()?.forEach { file ->
                runCatching { file.delete() }
            }
            _downloadedSongs.value = emptyList()
            _downloadStatus.value = emptyMap()
            _downloadProgress.value = emptyMap()
        }
    }

    private fun cleanFileName(id: String): String {
        return id.replace(Regex("[^a-zA-Z0-9_]"), "_")
    }

    companion object {
        // Only applicationContext is retained by this process-wide singleton.
        @android.annotation.SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: OfflineMusicManager? = null

        fun getInstance(context: Context): OfflineMusicManager {
            return instance ?: synchronized(this) {
                instance ?: OfflineMusicManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
