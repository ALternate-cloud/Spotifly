package com.example.spotifly.data.autoplay

import com.example.spotifly.data.catalog.TrackMatching
import com.example.spotifly.data.online.OnlineMusicService
import com.example.spotifly.data.online.PlaybackResolver
import com.example.spotifly.model.Song
import com.example.spotifly.model.SourceIds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object AutoplayManager {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private const val TARGET_QUEUE_SIZE = 6

    /** Small batches keep recommendations anchored to what is actually playing. */
    suspend fun buildAutoplayQueue(
        seed: Song,
        recentPlayed: List<Song>,
        excludeIds: Set<String>,
        userLikedIds: Set<String> = emptySet(),
        userSkippedFastIds: Set<String> = emptySet(),
        measured: (Song) -> VibeVector? = { null },
        prepareCandidates: suspend (List<AutoplayCandidate>) -> Unit = {}
    ): List<Song> = withContext(Dispatchers.IO) {
        val candidates = fetchAllSimilarCandidates(seed)
        prepareCandidates(candidates.filterNot { it.song.id in excludeIds })
        rankCandidates(seed, recentPlayed, candidates, excludeIds,
            userLikedIds, userSkippedFastIds, measured)
    }

    internal fun rankCandidates(
        seed: Song,
        recentPlayed: List<Song>,
        candidates: List<AutoplayCandidate>,
        excludeIds: Set<String> = emptySet(),
        userLikedIds: Set<String> = emptySet(),
        userSkippedFastIds: Set<String> = emptySet(),
        measured: (Song) -> VibeVector? = { null }
    ): List<Song> {
        val target = VibeFeatures.profileFor(seed, measured(seed))
        val pool = candidates.filter {
            val profile = VibeFeatures.profileFor(it.song, measured(it.song))
            val similarEnergy = kotlin.math.abs(target.vector.energy - profile.vector.energy) <= 0.4
            similarEnergy &&
                it.song.internalId !in excludeIds &&
                it.song.id !in userSkippedFastIds &&
                it.song.internalId !in userSkippedFastIds &&
                TrackMatching.isVersionCompatible(seed, it.song.title, it.song.artist)
        }.sortedByDescending { it.song.id in userLikedIds || it.song.internalId in userLikedIds }
        val result = mutableListOf<Song>()
        repeat(TARGET_QUEUE_SIZE) {
            val previousArtist = TrackMatching.normalizeArtist((result.lastOrNull() ?: seed).primaryArtist)
            val available = pool.filter { candidate ->
                result.none { TrackMatching.isSameRecording(it, candidate.song) }
            }
            if (available.isEmpty()) return result
            val varied = available.filter {
                TrackMatching.normalizeArtist(it.song.primaryArtist) != previousArtist
            }
            val pick = VibeRanker.pick(
                currentSong = seed,
                history = recentPlayed,
                candidates = varied.ifEmpty { available },
                excludeIds = excludeIds + result.map { it.id },
                excludeTitles = result.map { VibeFeatures.titleKey(it) }.toSet(),
                measured = measured
            ) ?: return result
            result.add(pick.song)
        }
        return result
    }

    /**
     * Gathers candidate songs across 4 similarity sources in priority order:
     * 1. YouTube Music radio / watch playlist ("related / up next")
     * 2. JioSaavn song suggestions, same album, same artist top songs
     * 3. Deezer related artists -> top tracks
     * 4. Same genre + same language + similar era search
     */
    private suspend fun fetchAllSimilarCandidates(seed: Song): List<AutoplayCandidate> = coroutineScope {
        val ytRadioDef = async { runCatching { fetchYouTubeMusicRadio(seed) }.getOrDefault(emptyList()) }
        val saavnSimDef = async { runCatching { fetchSaavnSimilar(seed) }.getOrDefault(emptyList()) }
        val deezerSimDef = async { runCatching { fetchDeezerSimilar(seed) }.getOrDefault(emptyList()) }
        val genreEraDef = async { runCatching { fetchGenreEraSimilar(seed) }.getOrDefault(emptyList()) }

        val ytRadio = ytRadioDef.await()
        val saavnSim = saavnSimDef.await()
        val deezerSim = deezerSimDef.await()
        val genreEra = genreEraDef.await()

        val all = mutableListOf<AutoplayCandidate>()
        fun addSongs(songs: List<Song>, source: CandidateSource) {
            songs.forEachIndexed { index, song ->
                val existing = all.indexOfFirst { TrackMatching.isSameRecording(it.song, song) }
                if (existing < 0) {
                    all.add(AutoplayCandidate(song.ensureMigrated(), source, index))
                } else {
                    all[existing] = all[existing].copy(
                        song = TrackMatching.mergeTracks(all[existing].song, song)
                    )
                }
            }
        }
        addSongs(ytRadio, CandidateSource.RECOMMENDED)
        addSongs(saavnSim, CandidateSource.RECOMMENDED)
        addSongs(deezerSim, CandidateSource.ARTIST)
        addSongs(genreEra, CandidateSource.VIBE)

        all
    }

    // =========================================================================
    // Source 1: YouTube Music Radio
    // =========================================================================

    private suspend fun fetchYouTubeMusicRadio(seed: Song): List<Song> {
        val videoId = if (!seed.sourceIds.ytmusic.isNullOrBlank()) {
            seed.sourceIds.ytmusic
        } else {
            val query = "${seed.title} ${seed.primaryArtist}".trim()
            PlaybackResolver.searchYouTubeMusicSongCandidates(query)
                .filter { TrackMatching.scoreCandidate(seed, it.title, it.artist, it.durationSec) >= 60 }
                .maxByOrNull { TrackMatching.scoreCandidate(seed, it.title, it.artist, it.durationSec) }?.videoId
        } ?: return emptyList()

        val songs = mutableListOf<Song>()
        runCatching {
            val url = "https://music.youtube.com/youtubei/v1/next"
            val payload = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", "1.20240101.01.00")
                        put("hl", "en")
                        put("gl", "US")
                    })
                })
                put("videoId", videoId)
                put("playlistId", "RDAMVM$videoId")
            }

            val requestBody = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "https://music.youtube.com/")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return@use
                val json = JSONObject(body)

                parseYouTubeRadioItems(json, songs)
            }
        }
        return songs
    }

    private fun parseYouTubeRadioItems(json: JSONObject, outSongs: MutableList<Song>) {
        val renderers = mutableListOf<JSONObject>()
        fun findPanelRenderers(obj: Any) {
            when (obj) {
                is JSONObject -> {
                    if (obj.has("playlistPanelVideoRenderer")) {
                        renderers.add(obj.getJSONObject("playlistPanelVideoRenderer"))
                    }
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        findPanelRenderers(obj.get(keys.next()))
                    }
                }
                is JSONArray -> {
                    for (i in 0 until obj.length()) {
                        findPanelRenderers(obj.get(i))
                    }
                }
            }
        }
        findPanelRenderers(json)

        for (item in renderers) {
            val vid = item.optString("videoId", "")
            if (vid.isBlank()) continue

            val titleRuns = item.optJSONObject("title")?.optJSONArray("runs") ?: JSONArray()
            val title = titleRuns.optJSONObject(0)?.optString("text", "Unknown Track").orEmpty()

            val bylineRuns = item.optJSONObject("longBylineText")?.optJSONArray("runs")
                ?: item.optJSONObject("shortBylineText")?.optJSONArray("runs")
                ?: JSONArray()
            val bylineParts = mutableListOf<String>()
            for (i in 0 until bylineRuns.length()) {
                val t = bylineRuns.getJSONObject(i).optString("text", "")
                if (t != " • " && t.isNotBlank()) bylineParts.add(t)
            }
            val artistName = bylineParts.firstOrNull() ?: "Unknown Artist"
            val albumName = if (bylineParts.size > 1) bylineParts[1] else ""
            val year = bylineParts.lastOrNull()?.toIntOrNull() ?: 0

            val durStr = item.optJSONObject("lengthText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text", "") ?: ""
            var durationSec = 0L
            if (durStr.contains(":")) {
                val p = durStr.split(":")
                if (p.size == 2) {
                    durationSec = (p[0].toLongOrNull() ?: 0L) * 60L + (p[1].toLongOrNull() ?: 0L)
                }
            }

            val thumbs = item.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
            val artwork = thumbs?.optJSONObject(thumbs.length() - 1)?.optString("url")?.ifBlank { null }

            outSongs.add(
                Song(
                    id = "yt_$vid",
                    internalId = "yt_$vid",
                    title = title,
                    artist = artistName,
                    artists = TrackMatching.splitArtists(artistName),
                    album = albumName,
                    durationSec = durationSec,
                    durationMs = durationSec * 1000L,
                    artworkUrl = artwork,
                    year = year,
                    sourceIds = SourceIds(ytmusic = vid),
                    isPlayable = true
                )
            )
        }
    }

    // =========================================================================
    // Source 2: JioSaavn Suggestions, Album & Artist Top Songs
    // =========================================================================

    private suspend fun fetchSaavnSimilar(seed: Song): List<Song> {
        val songs = mutableListOf<Song>()
        val saavnId = seed.sourceIds.saavn
            ?: if (seed.id.startsWith("saavn_")) seed.id.removePrefix("saavn_") else null

        // 1. JioSaavn track recommendations endpoint
        if (!saavnId.isNullOrBlank()) {
            runCatching {
                val url = "https://www.jiosaavn.com/api.php?__call=reco.getreco&_format=json&pid=$saavnId"
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Referer", "https://www.jiosaavn.com/")
                    .build()

                httpClient.newCall(req).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string().orEmpty()
                        if (body.isNotBlank()) {
                            val array = JSONObject(body).optJSONArray(saavnId) ?: JSONArray()
                            for (i in 0 until array.length()) {
                                OnlineMusicService.parseSaavnSong(array.getJSONObject(i), i)?.let(songs::add)
                            }
                        }
                    }
                }
            }
        }

        // 2. Artist top tracks
        val artistId = seed.artistId
        val artistName = seed.primaryArtist
        if (artistName.isNotBlank()) {
            val topSongs = OnlineMusicService.getArtistSongs(artistId ?: "", artistName)
            songs.addAll(topSongs.take(10))
        }

        // 3. Album tracks
        val albumId = seed.albumId
        if (!albumId.isNullOrBlank()) {
            val albumSongs = OnlineMusicService.getAlbumSongs(albumId, seed.album)
            songs.addAll(albumSongs.take(6))
        }

        return songs
    }

    // =========================================================================
    // Source 3: Deezer Related Artists & Top Songs
    // =========================================================================

    private suspend fun fetchDeezerSimilar(seed: Song): List<Song> {
        val songs = mutableListOf<Song>()
        val deezerId = seed.sourceIds.deezer

        runCatching {
            var artistId: String? = null
            if (!deezerId.isNullOrBlank()) {
                val trackUrl = "https://api.deezer.com/track/$deezerId"
                val req = Request.Builder().url(trackUrl).header("User-Agent", "Mozilla/5.0").build()
                httpClient.newCall(req).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string().orEmpty()
                        artistId = JSONObject(body).optJSONObject("artist")?.optLong("id", 0L)?.toString()
                    }
                }
            }

            if (artistId != null && artistId != "0") {
                // Related artists
                val relUrl = "https://api.deezer.com/artist/$artistId/related"
                val req = Request.Builder().url(relUrl).header("User-Agent", "Mozilla/5.0").build()
                httpClient.newCall(req).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string().orEmpty()
                        val data = JSONObject(body).optJSONArray("data") ?: JSONArray()
                        for (i in 0 until data.length().coerceAtMost(3)) {
                            val relArtist = data.getJSONObject(i)
                            val relArtistId = relArtist.optLong("id", 0L).toString()
                            if (relArtistId != "0") {
                                val topTracks = fetchDeezerArtistTop(relArtistId)
                                songs.addAll(topTracks.take(3))
                            }
                        }
                    }
                }
            }
        }
        return songs
    }

    private fun fetchDeezerArtistTop(artistId: String): List<Song> {
        val songs = mutableListOf<Song>()
        runCatching {
            val url = "https://api.deezer.com/artist/$artistId/top?limit=10"
            val req = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
            httpClient.newCall(req).execute().use { response ->
                if (!response.isSuccessful) return@use
                val body = response.body?.string().orEmpty()
                val array = JSONObject(body).optJSONArray("data") ?: JSONArray()
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val id = item.optLong("id", 0L).toString()
                    val title = item.optString("title", "")
                    val artistName = item.optJSONObject("artist")?.optString("name", "Unknown Artist") ?: "Unknown Artist"
                    val albumTitle = item.optJSONObject("album")?.optString("title", "") ?: ""
                    val durationSec = item.optLong("duration", 0L)
                    val artwork = item.optJSONObject("album")?.optString("cover_big")?.ifBlank { null }

                    songs.add(
                        Song(
                            id = "deezer_$id",
                            internalId = "deezer_$id",
                            title = title,
                            artist = artistName,
                            artists = TrackMatching.splitArtists(artistName),
                            album = albumTitle,
                            durationSec = durationSec,
                            durationMs = durationSec * 1000L,
                            artworkUrl = artwork,
                            sourceIds = SourceIds(deezer = id),
                            streamUrl = null,
                            isPlayable = false
                        )
                    )
                }
            }
        }
        return songs
    }

    // =========================================================================
    // Source 4: Genre + Language + Era Search
    // =========================================================================

    private suspend fun fetchGenreEraSimilar(seed: Song): List<Song> {
        val queries = AutoplayQueries.vibeQueries(VibeFeatures.profileFor(seed))
        return coroutineScope {
            queries.map { query ->
                async { runCatching { OnlineMusicService.searchSongs(query).take(15) }.getOrDefault(emptyList()) }
            }.flatMap { it.await() }
        }
    }
}
