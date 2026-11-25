package com.example.spotifly.data.playlist

import com.example.spotifly.data.catalog.TrackMatching
import com.example.spotifly.data.deeplink.DeepLinkParser
import com.example.spotifly.data.deeplink.DeepLinkTarget
import com.example.spotifly.model.Playlist
import com.example.spotifly.model.Song
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class PlaylistBackupBundle(
    val version: Int = 1,
    val exportedAtMs: Long = System.currentTimeMillis(),
    val playlists: List<Playlist>
)

/**
 * Pure, deterministic serializer/parser for Spotifly playlist backup bundles (`.json`)
 * and standard extended playlists (`.m3u` / `.m3u8` / plain text lines).
 */
object PlaylistBackupManager {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    fun exportJson(playlists: List<Playlist>): String {
        return json.encodeToString(PlaylistBackupBundle(playlists = playlists))
    }

    fun exportM3u(playlist: Playlist): String = buildString {
        appendLine("#EXTM3U")
        appendLine("#PLAYLIST:${playlist.name}")
        for (song in playlist.songs) {
            val seconds = ((song.durationMs / 1000L).takeIf { it > 0L } ?: song.durationSec).coerceAtLeast(0L)
            val durationVal = if (seconds > 0L) seconds else -1L
            val label = listOf(song.artist.trim(), song.title.trim())
                .filter { it.isNotBlank() }
                .joinToString(" - ")
                .ifBlank { song.title }
            appendLine("#EXTINF:$durationVal,$label")
            val location = when {
                !song.localFilePath.isNullOrBlank() -> song.localFilePath
                !song.streamUrl.isNullOrBlank() && song.streamUrl.startsWith("http", ignoreCase = true) -> song.streamUrl
                else -> "spotifly://track/${song.id}"
            }
            appendLine(location)
        }
    }

    fun parseImportPayload(
        rawText: String,
        fallbackName: String = "Imported Playlist",
        knownLibrary: List<Song> = emptyList()
    ): List<Playlist> {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) return emptyList()

        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return parseJsonPlaylists(trimmed, knownLibrary).orEmpty()
        }
        return listOfNotNull(parseM3u(trimmed, fallbackName, knownLibrary))
    }

    internal fun parseJsonPlaylists(rawJson: String, knownLibrary: List<Song>): List<Playlist>? {
        val bundle = runCatching { json.decodeFromString<PlaylistBackupBundle>(rawJson) }.getOrNull()
        val parsedList = when {
            bundle != null -> bundle.playlists
            else -> runCatching { json.decodeFromString<List<Playlist>>(rawJson) }.getOrNull()
                ?: runCatching { listOf(json.decodeFromString<Playlist>(rawJson)) }.getOrNull()
        } ?: return null

        val libraryById = knownLibrary.associateBy { it.id }
        val localByMetadata = knownLibrary.filter { it.isLocal }.groupBy { localTrackKey(it) }
        return parsedList.mapNotNull { playlist ->
            val cleanName = playlist.name.trim().ifBlank { return@mapNotNull null }
            val resolvedSongs = playlist.songs.map { song ->
                if (song.isLocal || song.id.startsWith("local_")) {
                    val candidates = localByMetadata[localTrackKey(song)].orEmpty()
                    val match = candidates.singleOrNull { it.id == song.id } ?: candidates.singleOrNull()
                    if (match != null) {
                        match.ensureMigrated()
                    } else {
                        // MediaStore IDs and content URIs are scoped to the source device.
                        val id = "imported_${UUID.randomUUID()}"
                        song.copy(
                            id = id, internalId = id, isLocal = false, isDownloaded = false,
                            localFilePath = null, contentUriString = null,
                            artworkUrl = song.artworkUrl?.takeUnless { it.startsWith("content:") },
                            streamUrl = song.streamUrl?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
                        ).ensureMigrated()
                    }
                } else {
                    (libraryById[song.id] ?: song).ensureMigrated()
                }
            }
            playlist.copy(
                id = "playlist_${UUID.randomUUID()}",
                name = cleanName,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                songs = resolvedSongs
            )
        }
    }

    private fun localTrackKey(song: Song) = Triple(
        song.title.trim().lowercase(),
        song.artist.trim().lowercase(),
        song.durationMs.takeIf { it > 0L } ?: song.durationSec * 1000L
    )

    internal fun parseM3u(
        rawText: String,
        fallbackName: String,
        knownLibrary: List<Song>
    ): Playlist? {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return null

        var playlistName = fallbackName.trim().ifBlank { "Imported Playlist" }
        var pendingExtDurationSec = 0L
        var pendingExtArtist: String? = null
        var pendingExtTitle: String? = null
        val songs = mutableListOf<Song>()

        for (line in lines) {
            when {
                line.startsWith("#EXTM3U", ignoreCase = true) -> Unit
                line.startsWith("#PLAYLIST:", ignoreCase = true) -> {
                    val candidate = line.substringAfter(':', "").trim()
                    if (candidate.isNotBlank()) playlistName = candidate
                }
                line.startsWith("#EXTINF:", ignoreCase = true) -> {
                    val info = line.substringAfter(':', "")
                    val durationPart = info.substringBefore(',', "").trim().toLongOrNull() ?: -1L
                    val metaPart = info.substringAfter(',', "").trim()
                    pendingExtDurationSec = if (durationPart > 0L) durationPart else 0L
                    if (metaPart.contains(" - ")) {
                        pendingExtArtist = metaPart.substringBefore(" - ").trim()
                        pendingExtTitle = metaPart.substringAfter(" - ").trim()
                    } else if (metaPart.isNotBlank()) {
                        pendingExtTitle = metaPart
                        pendingExtArtist = null
                    }
                }
                line.startsWith("#") -> Unit
                else -> {
                    val resolved = resolveTrackEntry(
                        location = line,
                        extTitle = pendingExtTitle,
                        extArtist = pendingExtArtist,
                        extDurationSec = pendingExtDurationSec,
                        knownLibrary = knownLibrary
                    )
                    if (resolved != null) songs.add(resolved)
                    pendingExtDurationSec = 0L
                    pendingExtArtist = null
                    pendingExtTitle = null
                }
            }
        }

        // Handle edge case where M3U had only #EXTINF lines without URI lines
        if (songs.isEmpty() && !pendingExtTitle.isNullOrBlank()) {
            resolveTrackEntry(
                location = "",
                extTitle = pendingExtTitle,
                extArtist = pendingExtArtist,
                extDurationSec = pendingExtDurationSec,
                knownLibrary = knownLibrary
            )?.let(songs::add)
        }

        if (songs.isEmpty()) return null
        return Playlist(
            id = "playlist_${UUID.randomUUID()}",
            name = playlistName,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            songs = songs
        )
    }

    private fun resolveTrackEntry(
        location: String,
        extTitle: String?,
        extArtist: String?,
        extDurationSec: Long,
        knownLibrary: List<Song>
    ): Song? {
        val deepLinkId = (DeepLinkParser.parse(location) as? DeepLinkTarget.Track)?.id
        if (deepLinkId != null) {
            knownLibrary.firstOrNull { it.id == deepLinkId }?.let { return it }
        }

        if (location.isNotBlank()) {
            knownLibrary.firstOrNull {
                it.localFilePath == location || it.contentUriString == location || it.streamUrl == location
            }?.let { return it }
        }

        // Plain line fallback like "Artist - Title" without #EXTINF
        var title = extTitle?.takeIf { it.isNotBlank() }
        var artist = extArtist?.takeIf { it.isNotBlank() }
        val isHttpUrl = location.startsWith("http://", ignoreCase = true) ||
            location.startsWith("https://", ignoreCase = true)

        if (title == null) {
            if (isHttpUrl || location.startsWith("spotifly://", ignoreCase = true)) {
                title = deepLinkId?.removePrefix("saavn_")?.removePrefix("itunes_")
                    ?: location.substringAfterLast('/').substringBefore('?').ifBlank { "Imported Track" }
            } else if (location.contains(" - ")) {
                artist = location.substringBefore(" - ").trim()
                title = location.substringAfter(" - ").trim()
            } else if (location.isNotBlank()) {
                title = location.substringAfterLast('/').substringAfterLast('\\')
                    .substringBeforeLast('.')
                    .ifBlank { location }
            }
        }

        val cleanTitle = title?.takeIf { it.isNotBlank() } ?: return null
        val cleanArtist = artist.orEmpty()

        // Try fuzzy match in known library by normalized title + artist
        val normTitle = TrackMatching.normalizeTitle(cleanTitle)
        val normArtist = TrackMatching.normalizeArtist(cleanArtist)
        knownLibrary.firstOrNull { lib ->
            TrackMatching.normalizeTitle(lib.title) == normTitle &&
                (normArtist.isBlank() || TrackMatching.normalizeArtist(lib.artist).contains(normArtist))
        }?.let { return it }

        val songId = deepLinkId ?: "imported_${UUID.randomUUID().toString().take(8)}"
        return Song(
            id = songId,
            internalId = songId,
            title = cleanTitle,
            artist = cleanArtist.ifBlank { "Unknown Artist" },
            artists = TrackMatching.splitArtists(cleanArtist),
            durationSec = extDurationSec,
            durationMs = extDurationSec * 1000L,
            streamUrl = if (isHttpUrl) location else null,
            isPlayable = isHttpUrl || songId.startsWith("saavn_") || songId.startsWith("yt_")
        )
    }
}
