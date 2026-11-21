package com.example.spotifly.model

import kotlinx.serialization.Serializable

@Serializable
data class SourceIds(
    val deezer: String? = null,
    val itunes: String? = null,
    val saavn: String? = null,
    val ytmusic: String? = null
)

@Serializable
data class Song(
    val id: String,
    val title: String,
    val artist: String = "",
    val album: String = "",
    val durationMs: Long = 0L,
    val artworkUrl: String? = null,
    val streamUrl: String? = null,
    val isLocal: Boolean = false,
    val contentUriString: String? = null,
    val isFavorite: Boolean = false,
    val isDownloaded: Boolean = false,
    val localFilePath: String? = null,
    // Catalog identity. Populated when the source exposes it; grouping falls back
    // to the normalised artist/album name when these are blank.
    val albumId: String? = null,
    val artistId: String? = null,
    val trackNumber: Int = 0,
    val discNumber: Int = 1,
    val year: Int = 0,
    val isExplicit: Boolean = false,
    val genre: String? = null,
    /** Catalogue language ("hindi", "urdu", "english", ...); used for vibe matching. */
    val language: String? = null,
    /** True for podcast episodes, which need resume and 15/30s skips. */
    val isPodcast: Boolean = false,
    // Unified Track Model fields
    val internalId: String = id,
    val artists: List<String> = emptyList(),
    val durationSec: Long = if (durationMs > 0) durationMs / 1000L else 0L,
    val isrc: String? = null,
    val sourceIds: SourceIds = SourceIds(),
    val isPlayable: Boolean = true
) {
    val displayArtist: String
        get() = if (artists.isNotEmpty()) artists.joinToString(", ") else artist.ifBlank { "Unknown Artist" }

    val primaryArtist: String
        get() = artists.firstOrNull()?.trim() ?: artist.split(Regex("[,&/]|\\bft\\.?\\b|\\bfeat\\.?\\b", RegexOption.IGNORE_CASE)).firstOrNull()?.trim() ?: artist

    val displayDuration: String
        get() {
            val totalSeconds = if (durationSec > 0) durationSec else (durationMs / 1000).coerceAtLeast(0)
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }

    /** Stable key for grouping tracks into an album when the source has no album id. */
    val albumKey: String
        get() = albumId?.takeIf { it.isNotBlank() }
            ?: "${artist.lowercase().trim()}|${album.ifBlank { title }.lowercase().trim()}"

    /** Stable key for grouping tracks into an artist when the source has no artist id. */
    val artistKey: String
        get() = artistId?.takeIf { it.isNotBlank() }
            ?: artist.split(",").first().lowercase().trim()

    /** Ensures existing stored songs are backwards-compatible and migrated into unified model */
    fun ensureMigrated(): Song {
        val saavnId = sourceIds.saavn ?: if (id.startsWith("saavn_")) id.removePrefix("saavn_") else null
        val itunesId = sourceIds.itunes ?: if (id.startsWith("itunes_")) id.removePrefix("itunes_") else null
        val ytId = sourceIds.ytmusic ?: if (id.startsWith("yt_")) id.removePrefix("yt_") else null
        val resolvedArtists = if (artists.isNotEmpty()) artists else {
            if (artist.isNotBlank()) {
                artist.split(Regex("[,&/]|\\bft\\.?\\b|\\bfeat\\.?\\b", RegexOption.IGNORE_CASE))
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
            } else emptyList()
        }
        val sec = if (durationSec > 0) durationSec else (durationMs / 1000L).coerceAtLeast(0L)
        val ms = if (durationMs > 0) durationMs else sec * 1000L
        val cleanInternalId = if (internalId.isNotBlank()) internalId else id
        return copy(
            internalId = cleanInternalId,
            artists = resolvedArtists,
            durationSec = sec,
            durationMs = ms,
            sourceIds = sourceIds.copy(saavn = saavnId, itunes = itunesId, ytmusic = ytId),
            isPlayable = isPlayable || streamUrl != null || saavnId != null || ytId != null
        )
    }
}

/** Unified Track model alias */
typealias Track = Song

enum class RepeatMode {
    OFF,
    ALL,
    ONE
}

enum class DownloadStatus {
    IDLE,
    DOWNLOADING,
    DOWNLOADED,
    FAILED
}

@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val songs: List<Song> = emptyList(),
    val description: String = "",
    val updatedAt: Long = 0L,
    /** Absolute path to a user-picked cover image in app storage. */
    val coverPath: String? = null
)

/** A playable album, either from the catalogue or assembled from the user's library. */
@Serializable
data class Album(
    val id: String,
    val title: String,
    val artist: String,
    val artworkUrl: String? = null,
    val year: Int = 0,
    val songs: List<Song> = emptyList(),
    val isLocal: Boolean = false
) {
    val displayArtist: String get() = artist.ifBlank { "Unknown artist" }
    val trackCount: Int get() = songs.size
    val totalDurationMs: Long get() = songs.sumOf { it.durationMs }
}

/** An artist grouped from tracks. */
@Serializable
data class Artist(
    val id: String,
    val name: String,
    val artworkUrl: String? = null,
    val songs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val isLocal: Boolean = false
) {
    val trackCount: Int get() = songs.size
    val albumCount: Int get() = albums.size
}

data class PlaybackState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isBuffering: Boolean = false,
    val isShuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val isAutoplay: Boolean = true,
    val playbackSpeed: Float = 1.0f,
    val queue: List<Song> = emptyList(),
    val currentIndex: Int = -1,
    /** Tracks explicitly added by user ("Add to Queue" / "Play next") */
    val manualQueue: List<Song> = emptyList(),
    /** Upcoming similar tracks generated automatically */
    val autoplayQueue: List<Song> = emptyList(),
    /**
     * Id of the prefetched autoplay continuation sitting at the end of [queue].
     * Null when no continuation is staged or autoplay is off.
     */
    val autoplayNextId: String? = null,
    val lyrics: Lyrics? = null,
    val isLyricsLoading: Boolean = false,
    val playbackError: String? = null
) {
    val progress: Float
        get() = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
}
