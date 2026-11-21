package com.example.spotifly.data.online

import kotlin.io.encoding.Base64
import com.example.spotifly.data.catalog.TrackMatching
import com.example.spotifly.model.Song
import com.example.spotifly.model.SourceIds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object PlaybackResolver {

    enum class SourceType {
        SAAVN,
        YTMUSIC
    }

    data class CachedResolution(
        val sourceType: SourceType,
        val sourceId: String,
        val streamUrl: String? = null,
        val resolvedAtNanos: Long = System.nanoTime()
    )

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val desKeySpec by lazy {
        SecretKeySpec("38346591".toByteArray(Charsets.UTF_8), "DES")
    }

    // Bound reuse of verified URLs. Player errors bypass this window and refresh the source.
    private val streamReuseNanos = TimeUnit.SECONDS.toNanos(60)
    private val resolutionCache = ConcurrentHashMap<String, CachedResolution>()

    /** Invidious instances used for YouTube audio stream extraction */
    private val INVIDIOUS_INSTANCES = listOf(
        "https://yt.chocolatemoo53.com",
        "https://inv.nadeko.net",
        "https://invidious.nerdvpn.de"
    )

    /**
     * Resolves a track to a playable stream URL using the target architecture:
     * Step 1: JioSaavn first (best quality, 320 kbps if available)
     * Step 2: YouTube Music fallback
     * Step 3: Neither resolves -> return not playable state (never substitute wrong versions)
     */
    suspend fun resolve(track: Song): Song? = resolve(track, reuseStream = true)

    private suspend fun resolve(track: Song, reuseStream: Boolean): Song? = withContext(Dispatchers.IO) {
        if (track.isLocal || track.isPodcast) {
            return@withContext track
        }

        val cacheKey = trackCacheKey(track)
        val cached = resolutionCache[cacheKey]

        if (cached != null) {
            if (reuseStream && cached.streamUrl != null &&
                System.nanoTime() - cached.resolvedAtNanos < streamReuseNanos
            ) {
                return@withContext track.withResolvedStream(cached, cached.streamUrl)
            }
            val refreshed = refreshFromCached(track, cached)
            if (refreshed != null && refreshed.streamUrl != null) {
                resolutionCache[cacheKey] = CachedResolution(cached.sourceType, cached.sourceId, refreshed.streamUrl)
                return@withContext refreshed
            }
        }

        // Step 1: Try JioSaavn first (320kbps if present)
        val saavnTrack = resolveViaJioSaavn(track)
        if (saavnTrack != null && saavnTrack.streamUrl != null) {
            val saavnId = saavnTrack.sourceIds.saavn ?: saavnTrack.id.removePrefix("saavn_")
            resolutionCache[cacheKey] = CachedResolution(SourceType.SAAVN, saavnId, saavnTrack.streamUrl)
            return@withContext saavnTrack
        }

        // Step 2: Fallback to YouTube Music
        val ytTrack = resolveViaYouTubeMusic(track)
        if (ytTrack != null && ytTrack.streamUrl != null) {
            val videoId = ytTrack.sourceIds.ytmusic ?: ""
            if (videoId.isNotBlank()) {
                resolutionCache[cacheKey] = CachedResolution(SourceType.YTMUSIC, videoId, ytTrack.streamUrl)
            }
            return@withContext ytTrack
        }

        // Step 3: Neither source resolves; return clear not-playable state
        track.copy(isPlayable = false, streamUrl = null)
    }

    /**
     * Re-resolve stream URL on 403/410 playback error.
     */
    suspend fun reResolve(track: Song): Song? = resolve(track, reuseStream = false)

    private fun Song.withResolvedStream(cached: CachedResolution, url: String): Song {
        val ids = when (cached.sourceType) {
            SourceType.SAAVN -> sourceIds.copy(saavn = cached.sourceId)
            SourceType.YTMUSIC -> sourceIds.copy(ytmusic = cached.sourceId)
        }
        return copy(streamUrl = url, isPlayable = true, sourceIds = ids)
    }

    private fun trackCacheKey(track: Song): String {
        return if (track.internalId.isNotBlank()) {
            track.internalId
        } else {
            "${TrackMatching.normalizeTitle(track.title)}|${TrackMatching.normalizeArtist(track.artist)}"
        }
    }

    private suspend fun refreshFromCached(track: Song, cached: CachedResolution): Song? {
        return when (cached.sourceType) {
            SourceType.SAAVN -> {
                val streamUrl = fetchSaavnStreamUrlById(cached.sourceId)
                if (streamUrl != null) {
                    track.copy(
                        streamUrl = streamUrl,
                        isPlayable = true,
                        sourceIds = track.sourceIds.copy(saavn = cached.sourceId)
                    )
                } else null
            }
            SourceType.YTMUSIC -> {
                val streamUrl = extractYouTubeAudioStream(cached.sourceId)
                if (streamUrl != null) {
                    track.copy(
                        streamUrl = streamUrl,
                        isPlayable = true,
                        sourceIds = track.sourceIds.copy(ytmusic = cached.sourceId)
                    )
                } else null
            }
        }
    }

    // =========================================================================
    // STEP 1: JioSaavn Resolution
    // =========================================================================

    private suspend fun resolveViaJioSaavn(requested: Song): Song? {
        // If we already have a Saavn ID, query it directly
        val directSaavnId = requested.sourceIds.saavn
            ?: if (requested.id.startsWith("saavn_")) requested.id.removePrefix("saavn_") else null

        if (!directSaavnId.isNullOrBlank()) {
            val streamUrl = fetchSaavnStreamUrlById(directSaavnId)
            if (streamUrl != null) {
                return requested.copy(
                    streamUrl = streamUrl,
                    isPlayable = true,
                    sourceIds = requested.sourceIds.copy(saavn = directSaavnId)
                )
            }
        }

        // Otherwise search JioSaavn candidates and score them
        val query = "${requested.title} ${requested.primaryArtist}".trim()
        val candidates = searchSaavnRawCandidates(query)

        var bestCandidate: JSONObject? = null
        var bestScore = 0

        for (cand in candidates) {
            val title = unescapeHtml(cand.optString("song", ""))
            val artist = unescapeHtml(cand.optString("primary_artists", cand.optString("singers", "")))
            val durationSec = cand.optLong("duration", 0L)

            val score = TrackMatching.scoreCandidate(requested, title, artist, durationSec)
            if (score > bestScore && score >= 65) {
                bestScore = score
                bestCandidate = cand
            }
        }

        if (bestCandidate != null) {
            val saavnId = bestCandidate.optString("id", "")
            val encryptedUrl = bestCandidate.optString("encrypted_media_url", "")
            val streamUrl = getHighestQualitySaavnStream(encryptedUrl)

            if (!streamUrl.isNullOrBlank() && saavnId.isNotBlank()) {
                val durationSec = bestCandidate.optLong("duration", 0L)
                return requested.copy(
                    streamUrl = streamUrl,
                    isPlayable = true,
                    durationSec = if (durationSec > 0) durationSec else requested.durationSec,
                    durationMs = if (durationSec > 0) durationSec * 1000L else requested.durationMs,
                    sourceIds = requested.sourceIds.copy(saavn = saavnId)
                )
            }
        }

        return null
    }

    private suspend fun fetchSaavnStreamUrlById(saavnId: String): String? {
        return runCatching {
            val url = "https://www.jiosaavn.com/api.php?__call=song.getDetails&_format=json&pids=$saavnId"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "https://www.jiosaavn.com/")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return@use null
                val json = JSONObject(body)
                val songObj = json.optJSONObject(saavnId) ?: return@use null
                val encUrl = songObj.optString("encrypted_media_url", "")
                getHighestQualitySaavnStream(encUrl)
            }
        }.getOrNull()
    }

    private suspend fun searchSaavnRawCandidates(query: String): List<JSONObject> {
        return runCatching {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://www.jiosaavn.com/api.php?__call=search.getResults&_format=json&n=40&p=1&q=$encoded"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "https://www.jiosaavn.com/")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList()
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return@use emptyList()
                val resultsArray = JSONObject(body).optJSONArray("results") ?: JSONArray()
                val list = mutableListOf<JSONObject>()
                for (i in 0 until resultsArray.length()) {
                    list.add(resultsArray.getJSONObject(i))
                }
                list
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Decrypts Saavn CDN URL and attempts highest quality bitrate (320 kbps first, fallback to 160 kbps).
     */
    // JioSaavn supplies DES/ECB encrypted URLs. This only decrypts its wire format.
    @android.annotation.SuppressLint("GetInstance")
    private fun getHighestQualitySaavnStream(encryptedMediaUrl: String): String? {
        if (encryptedMediaUrl.isBlank()) return null
        return try {
            val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, desKeySpec)
            val cleanEnc = encryptedMediaUrl.trim().replace("\\/", "/")
            val decodedBytes = Base64.decode(
                cleanEnc.filterNot { it.isWhitespace() }.replace('-', '+').replace('_', '/')
            )
            val decryptedBytes = cipher.doFinal(decodedBytes)
            val rawUrl = String(decryptedBytes, Charsets.UTF_8).trim()
            if (rawUrl.startsWith("http")) {
                // Use 320 kbps if present (standard JioSaavn CDN replaces _96 with _320)
                val url320 = rawUrl.replace("_96.mp4", "_320.mp4")
                if (probeUrlAvailable(url320)) {
                    url320
                } else {
                    rawUrl.replace("_96.mp4", "_160.mp4")
                }
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun probeUrlAvailable(url: String): Boolean {
        return try {
            val req = Request.Builder().url(url).head().build()
            httpClient.newCall(req).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }

    // =========================================================================
    // STEP 2: YouTube Music Resolution
    // =========================================================================

    private suspend fun resolveViaYouTubeMusic(requested: Song): Song? {
        var targetVideoId = requested.sourceIds.ytmusic

        if (targetVideoId.isNullOrBlank()) {
            val query = "${requested.title} ${requested.primaryArtist}".trim()
            val candidates = searchYouTubeMusicSongCandidates(query)

            var bestScore = 0
            var bestVideoId: String? = null

            for (cand in candidates) {
                val score = TrackMatching.scoreCandidate(
                    requested,
                    cand.title,
                    cand.artist,
                    cand.durationSec
                )
                if (score > bestScore && score >= 65) {
                    bestScore = score
                    bestVideoId = cand.videoId
                }
            }
            targetVideoId = bestVideoId
        }

        if (!targetVideoId.isNullOrBlank()) {
            val audioUrl = extractYouTubeAudioStream(targetVideoId)
            if (!audioUrl.isNullOrBlank()) {
                return requested.copy(
                    streamUrl = audioUrl,
                    isPlayable = true,
                    sourceIds = requested.sourceIds.copy(ytmusic = targetVideoId)
                )
            }
        }

        return null
    }

    data class YouTubeMusicCandidate(
        val videoId: String,
        val title: String,
        val artist: String,
        val durationSec: Long,
        val artworkUrl: String?
    )

    suspend fun searchYouTubeMusicSongCandidates(query: String): List<YouTubeMusicCandidate> = withContext(Dispatchers.IO) {
        val candidates = mutableListOf<YouTubeMusicCandidate>()
        runCatching {
            val url = "https://music.youtube.com/youtubei/v1/search"
            val payload = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", "1.20240101.01.00")
                        put("hl", "en")
                        put("gl", "US")
                    })
                })
                put("query", query)
                // Filter specifically for Songs on YouTube Music
                put("params", "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D")
            }

            val requestBody = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Referer", "https://music.youtube.com/")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return@use
                val json = JSONObject(body)

                parseYouTubeMusicSearchRenderers(json, candidates)
            }
        }
        candidates
    }

    private fun parseYouTubeMusicSearchRenderers(json: JSONObject, outCandidates: MutableList<YouTubeMusicCandidate>) {
        val renderers = mutableListOf<JSONObject>()
        fun findRenderers(obj: Any) {
            when (obj) {
                is JSONObject -> {
                    if (obj.has("musicResponsiveListItemRenderer")) {
                        renderers.add(obj.getJSONObject("musicResponsiveListItemRenderer"))
                    }
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        findRenderers(obj.get(keys.next()))
                    }
                }
                is JSONArray -> {
                    for (i in 0 until obj.length()) {
                        findRenderers(obj.get(i))
                    }
                }
            }
        }
        findRenderers(json)

        val durationPattern = Regex("""^\d+:\d+$""")
        for (renderer in renderers) {
            val flexCols = renderer.optJSONArray("flexColumns") ?: continue
            if (flexCols.length() == 0) continue

            // 1. Title
            val col0 = flexCols.getJSONObject(0).optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            val runs0 = col0?.optJSONObject("text")?.optJSONArray("runs") ?: continue
            val title = runs0.optJSONObject(0)?.optString("text", "").orEmpty()
            if (title.isBlank()) continue

            // 2. VideoId
            var videoId: String? = null
            val nav = renderer.optJSONObject("navigationEndpoint")
            val playNav = renderer.optJSONObject("overlay")
                ?.optJSONObject("musicItemThumbnailOverlayRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("musicPlayButtonRenderer")
                ?.optJSONObject("playNavigationEndpoint")

            if (nav?.has("watchEndpoint") == true) {
                videoId = nav.getJSONObject("watchEndpoint").optString("videoId")?.ifBlank { null }
            } else if (playNav?.has("watchEndpoint") == true) {
                videoId = playNav.getJSONObject("watchEndpoint").optString("videoId")?.ifBlank { null }
            }

            // 3. Artists & Duration
            var artist = ""
            var durationSec = 0L
            if (flexCols.length() > 1) {
                val col1 = flexCols.getJSONObject(1).optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                val runs1 = col1?.optJSONObject("text")?.optJSONArray("runs") ?: JSONArray()
                val artistList = mutableListOf<String>()

                for (i in 0 until runs1.length()) {
                    val run = runs1.getJSONObject(i)
                    val text = run.optString("text", "")
                    if (videoId == null && run.has("navigationEndpoint")) {
                        val watchEp = run.getJSONObject("navigationEndpoint").optJSONObject("watchEndpoint")
                        videoId = watchEp?.optString("videoId")?.ifBlank { null }
                    }

                    if (durationPattern.matches(text)) {
                        val parts = text.split(":")
                        if (parts.size == 2) {
                            val mins = parts[0].toLongOrNull() ?: 0L
                            val secs = parts[1].toLongOrNull() ?: 0L
                            durationSec = mins * 60L + secs
                        }
                    } else if (text != " • " && text != "Song" && text != "Video" && text != "&" && text != ", ") {
                        if (artistList.isEmpty() || artistList.size < 3) {
                            artistList.add(text)
                        }
                    }
                }
                artist = artistList.joinToString(", ")
            }

            val thumbs = renderer.optJSONObject("thumbnail")
                ?.optJSONObject("musicThumbnailRenderer")
                ?.optJSONObject("thumbnail")
                ?.optJSONArray("thumbnails")
            val artworkUrl = if (thumbs != null && thumbs.length() > 0) {
                thumbs.getJSONObject(thumbs.length() - 1).optString("url")?.ifBlank { null }
            } else null

            if (!videoId.isNullOrBlank()) {
                outCandidates.add(
                    YouTubeMusicCandidate(
                        videoId = videoId,
                        title = title,
                        artist = artist,
                        durationSec = durationSec,
                        artworkUrl = artworkUrl
                    )
                )
            }
        }
    }

    /**
     * Extracts direct audio stream from YouTube videoId using Invidious instance failover.
     */
    suspend fun extractYouTubeAudioStream(videoId: String): String? = withContext(Dispatchers.IO) {
        if (videoId.isBlank()) return@withContext null

        for (instance in INVIDIOUS_INSTANCES) {
            val streamUrl = runCatching {
                val url = "$instance/api/v1/videos/$videoId?local=true"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = response.body?.string().orEmpty()
                    if (body.isBlank()) return@use null
                    val json = JSONObject(body)

                    val adaptiveFormats = json.optJSONArray("adaptiveFormats")
                        ?: json.optJSONArray("audioStreams")
                        ?: JSONArray()

                    var bestAudioUrl: String? = null
                    var maxBitrate = 0L

                    for (i in 0 until adaptiveFormats.length()) {
                        val format = adaptiveFormats.getJSONObject(i)
                        val type = format.optString("type", format.optString("mimeType", ""))
                        val rawUrl = format.optString("url", "")
                        if (type.contains("audio") && rawUrl.isNotBlank()) {
                            val bitrate = format.optLong("bitrate", 0L)
                            if (bitrate >= maxBitrate) {
                                maxBitrate = bitrate
                                bestAudioUrl = java.net.URI(instance).resolve(rawUrl).toString()
                            }
                        }
                    }
                    bestAudioUrl
                }
            }.getOrNull()

            if (!streamUrl.isNullOrBlank() && probeAudioStream(streamUrl)) {
                return@withContext streamUrl
            }
        }
        null
    }

    private fun probeAudioStream(url: String): Boolean = try {
        val request = Request.Builder().url(url).header("Range", "bytes=0-0").build()
        httpClient.newCall(request).execute().use { response ->
            val type = response.header("Content-Type").orEmpty()
            response.isSuccessful &&
                (type.startsWith("audio/") || type.startsWith("application/octet-stream")) &&
                (response.body?.byteStream()?.read() ?: -1) >= 0
        }
    } catch (_: Exception) {
        false
    }

    private fun unescapeHtml(text: String): String {
        return text.replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&#039;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .trim()
    }
}
