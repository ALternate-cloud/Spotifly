package com.example.spotifly.data.stats

import android.content.Context
import com.example.spotifly.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.TimeUnit

@Serializable
data class PlayEvent(
    val songId: String,
    val title: String,
    val artist: String,
    val artworkUrl: String? = null,
    val playedAt: Long = 0L,
    val durationMs: Long = 0L
)

data class TrackStat(
    val songId: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val plays: Int,
    val listenedMs: Long
)

data class ArtistStat(
    val name: String,
    val artworkUrl: String?,
    val plays: Int,
    val listenedMs: Long
)

data class ListeningStats(
    val totalPlays: Int = 0,
    val totalMs: Long = 0L,
    val uniqueTracks: Int = 0,
    val uniqueArtists: Int = 0,
    val playsLast7Days: Int = 0,
    val msLast7Days: Long = 0L,
    val playsLast30Days: Int = 0,
    val msLast30Days: Long = 0L,
    val topTracks: List<TrackStat> = emptyList(),
    val topArtists: List<ArtistStat> = emptyList()
) {
    val totalMinutes: Long get() = TimeUnit.MILLISECONDS.toMinutes(totalMs)
    val minutesLast7Days: Long get() = TimeUnit.MILLISECONDS.toMinutes(msLast7Days)
    val isEmpty: Boolean get() = totalPlays == 0
}

/**
 * Append-only play log plus the aggregations Spotify surfaces in Wrapped and in the
 * profile stats. It records what the player actually started, with the track's
 * duration as the listening estimate — honest, since no per-second telemetry is kept.
 */
class ListeningStatsManager private constructor(context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val eventsFile = File(context.filesDir, "play_events.json")

    private val _events = MutableStateFlow<List<PlayEvent>>(emptyList())
    private val _stats = MutableStateFlow(ListeningStats())
    val stats: StateFlow<ListeningStats> = _stats.asStateFlow()

    init {
        scope.launch {
            val loaded = mutex.withLock {
                runCatching {
                    if (!eventsFile.exists()) emptyList()
                    else json.decodeFromString<List<PlayEvent>>(eventsFile.readText())
                }.getOrDefault(emptyList())
            }
            _events.value = loaded
            _stats.value = compute(loaded)
        }
    }

    fun recordPlay(song: Song) {
        val event = PlayEvent(
            songId = song.id,
            title = song.title,
            artist = song.artist,
            artworkUrl = song.artworkUrl,
            playedAt = System.currentTimeMillis(),
            durationMs = song.durationMs.coerceAtLeast(0L)
        )
        val updated = (_events.value + event).takeLast(MAX_EVENTS)
        _events.value = updated
        _stats.value = compute(updated)
        scope.launch {
            mutex.withLock {
                runCatching { eventsFile.writeText(json.encodeToString(updated)) }
            }
        }
    }

    fun clear() {
        _events.value = emptyList()
        _stats.value = ListeningStats()
        scope.launch { mutex.withLock { runCatching { eventsFile.delete() } } }
    }

    private fun compute(events: List<PlayEvent>): ListeningStats = aggregateListeningStats(events)

    companion object {
        private const val MAX_EVENTS = 5000

        @Volatile
        private var instance: ListeningStatsManager? = null

        fun getInstance(context: Context): ListeningStatsManager =
            instance ?: synchronized(this) {
                instance ?: ListeningStatsManager(context.applicationContext).also { instance = it }
            }
    }
}

/**
 * Pure aggregation over the play log, kept out of the Android-owning class so it can be
 * tested without a Context. "Listened" is estimated from each track's duration, which is
 * the honest number when no per-second telemetry is kept.
 */
internal fun aggregateListeningStats(events: List<PlayEvent>): ListeningStats {
    if (events.isEmpty()) return ListeningStats()
    val now = System.currentTimeMillis()
    val day = TimeUnit.DAYS.toMillis(1)
    val week = events.filter { now - it.playedAt <= 7 * day }
    val month = events.filter { now - it.playedAt <= 30 * day }

    val topTracks = events
        .groupBy { it.songId }
        .map { (songId, plays) ->
            val lead = plays.maxByOrNull { it.playedAt }!!
            TrackStat(
                songId = songId,
                title = lead.title,
                artist = lead.artist,
                artworkUrl = lead.artworkUrl,
                plays = plays.size,
                listenedMs = plays.sumOf { it.durationMs }
            )
        }
        .sortedByDescending { it.plays }
        .take(10)

    val topArtists = events
        .groupBy { it.artist.split(",").first().trim().lowercase() }
        .map { (_, plays) ->
            val lead = plays.maxByOrNull { it.playedAt }!!
            ArtistStat(
                name = lead.artist.split(",").first().trim(),
                artworkUrl = plays.firstNotNullOfOrNull { it.artworkUrl },
                plays = plays.size,
                listenedMs = plays.sumOf { it.durationMs }
            )
        }
        .sortedByDescending { it.plays }
        .take(10)

    return ListeningStats(
        totalPlays = events.size,
        totalMs = events.sumOf { it.durationMs },
        uniqueTracks = events.distinctBy { it.songId }.size,
        uniqueArtists = events.distinctBy { it.artist.split(",").first().trim().lowercase() }.size,
        playsLast7Days = week.size,
        msLast7Days = week.sumOf { it.durationMs },
        playsLast30Days = month.size,
        msLast30Days = month.sumOf { it.durationMs },
        topTracks = topTracks,
        topArtists = topArtists
    )
}
