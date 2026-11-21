package com.example.spotifly.data.podcast

import android.content.Context
import com.example.spotifly.model.PodcastShow
import com.example.spotifly.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
private data class PodcastState(
    val followedShows: List<PodcastShow> = emptyList(),
    val episodeProgressMs: Map<String, Long> = emptyMap(),
    val completedEpisodes: List<String> = emptyList()
)

/**
 * Followed shows and per-episode resume points. Resume is the one thing spoken audio
 * cannot live without: a 90-minute episode must continue where it stopped, even after
 * the process dies.
 */
class PodcastRepository private constructor(context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val stateFile = File(context.filesDir, "podcasts.json")

    private val _followedShows = MutableStateFlow<List<PodcastShow>>(emptyList())
    val followedShows: StateFlow<List<PodcastShow>> = _followedShows.asStateFlow()

    private val _episodeProgress = MutableStateFlow<Map<String, Long>>(emptyMap())
    val episodeProgress: StateFlow<Map<String, Long>> = _episodeProgress.asStateFlow()

    private val _completedEpisodes = MutableStateFlow<Set<String>>(emptySet())
    val completedEpisodes: StateFlow<Set<String>> = _completedEpisodes.asStateFlow()

    @Volatile
    private var hydrated = false

    init {
        scope.launch { hydrate() }
    }

    private suspend fun hydrate() {
        val loaded = mutex.withLock {
            runCatching {
                if (!stateFile.exists()) PodcastState()
                else json.decodeFromString<PodcastState>(stateFile.readText())
            }.getOrDefault(PodcastState())
        }
        _followedShows.value = loaded.followedShows
        _episodeProgress.value = loaded.episodeProgressMs
        _completedEpisodes.value = loaded.completedEpisodes.toSet()
        hydrated = true
    }

    fun isFollowing(showId: String): Boolean = _followedShows.value.any { it.id == showId }

    fun toggleFollow(show: PodcastShow) {
        _followedShows.update { current ->
            if (current.any { it.id == show.id }) current.filterNot { it.id == show.id }
            else current + show
        }
        persist()
    }

    fun progressFor(songIdOrEpisodeId: String): Long {
        val episodeId = songIdOrEpisodeId.removePrefix("podcast_")
        return _episodeProgress.value[episodeId] ?: 0L
    }

    fun saveProgress(episodeId: String, positionMs: Long) {
        if (positionMs <= 0) return
        _episodeProgress.update { it + (episodeId to positionMs) }
        persistThrottled()
    }

    fun markCompleted(episodeId: String) {
        _episodeProgress.update { it - episodeId }
        _completedEpisodes.update { it + episodeId }
        persist()
    }

    fun clearProgress(episodeId: String) {
        _episodeProgress.update { it - episodeId }
        persist()
    }

    fun episodeIdOf(song: Song): String = song.id.removePrefix("podcast_")

    private var lastPersistAt = 0L

    /** Resume points change every few seconds; writing each one would thrash the disk. */
    private fun persistThrottled() {
        val now = System.currentTimeMillis()
        if (now - lastPersistAt < 5_000) return
        lastPersistAt = now
        persist()
    }

    private fun persist() {
        val snapshot = PodcastState(
            followedShows = _followedShows.value,
            episodeProgressMs = _episodeProgress.value,
            completedEpisodes = _completedEpisodes.value.toList()
        )
        scope.launch {
            mutex.withLock {
                runCatching { stateFile.writeText(json.encodeToString(snapshot)) }
            }
        }
    }

    companion object {
        @Volatile
        private var instance: PodcastRepository? = null

        fun getInstance(context: Context): PodcastRepository =
            instance ?: synchronized(this) {
                instance ?: PodcastRepository(context.applicationContext).also { instance = it }
            }
    }
}
