package com.example.spotifly.data.online

import android.text.Html
import kotlin.io.encoding.Base64
import com.example.spotifly.data.autoplay.AutoplayCandidate
import com.example.spotifly.data.autoplay.CandidateSource
import com.example.spotifly.data.catalog.TrackMatching
import com.example.spotifly.model.Album
import com.example.spotifly.model.Artist
import com.example.spotifly.model.LyricLine
import com.example.spotifly.model.Lyrics
import com.example.spotifly.model.Song
import com.example.spotifly.model.SourceIds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object OnlineMusicService {

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    // DES Secret Key for JioSaavn CDN URL decryption
    private val desKeySpec by lazy {
        SecretKeySpec("38346591".toByteArray(Charsets.UTF_8), "DES")
    }

    // Search cache: small in-memory LRU cache so repeated searches are instantaneous
    private data class SearchCacheEntry(val songs: List<Song>, val expiresAt: Long)
    private val searchCache = object : LinkedHashMap<String, SearchCacheEntry>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, SearchCacheEntry>): Boolean = size > 50
    }
    private val searchCacheLock = Any()

    // Lyrics cache: avoids refetching lyrics every time a song replays or the queue rebuilds
    private val lyricsCache = object : LinkedHashMap<String, Lyrics?>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Lyrics?>): Boolean = size > 100
    }
    private val lyricsCacheLock = Any()

    // Curated trending hits with direct full-length 160kbps audio streams
    val trendingHits: List<Song> = listOf(
        Song(
            id = "saavn_starboy",
            title = "Starboy",
            artist = "The Weeknd ft. Daft Punk",
            album = "Starboy",
            durationMs = 230000L,
            artworkUrl = "https://c.saavncdn.com/372/Starboy-English-2016-500x500.jpg",
            streamUrl = "https://aac.saavncdn.com/372/38de816bee7a6df4607f1f0e6822c5bc_160.mp4",
            isLocal = false
        ),
        Song(
            id = "saavn_blinding_lights",
            title = "Blinding Lights",
            artist = "The Weeknd",
            album = "After Hours",
            durationMs = 200000L,
            artworkUrl = "https://c.saavncdn.com/077/After-Hours-English-2020-20260804045014-500x500.jpg",
            streamUrl = "https://aac.saavncdn.com/077/0b02a92687d1ae3369b6859f44872e52_160.mp4",
            isLocal = false
        ),
        Song(
            id = "saavn_espresso",
            title = "Espresso",
            artist = "Sabrina Carpenter",
            album = "Espresso",
            durationMs = 175000L,
            artworkUrl = "https://c.saavncdn.com/111/Espresso-English-2024-20240412064803-500x500.jpg",
            streamUrl = "https://aac.saavncdn.com/111/cf81f6eb5b6768fe2e1ddf9335124a5f_160.mp4",
            isLocal = false
        ),
        Song(
            id = "saavn_die_with_a_smile",
            title = "Die With A Smile",
            artist = "Lady Gaga, Bruno Mars",
            album = "Die With A Smile",
            durationMs = 250000L,
            artworkUrl = "https://c.saavncdn.com/060/Die-With-A-Smile-English-2024-20240816103634-500x500.jpg",
            streamUrl = "https://aac.saavncdn.com/060/05bb6ae7a01edcbd8e0d859d2fa1d83d_160.mp4",
            isLocal = false
        ),
        Song(
            id = "saavn_birds_of_a_feather",
            title = "BIRDS OF A FEATHER",
            artist = "Billie Eilish",
            album = "HIT ME HARD AND SOFT",
            durationMs = 210000L,
            artworkUrl = "https://c.saavncdn.com/707/HIT-ME-HARD-AND-SOFT-English-2024-20240517063536-500x500.jpg",
            streamUrl = "https://aac.saavncdn.com/707/761fa325ce0600e1463336f0431d82b3_160.mp4",
            isLocal = false
        ),
        Song(
            id = "saavn_levitating",
            title = "Levitating",
            artist = "Dua Lipa",
            album = "Future Nostalgia",
            durationMs = 203000L,
            artworkUrl = "https://c.saavncdn.com/207/Dua-Lipa-Live-From-Mexico-English-2026-20260526161014-500x500.jpg",
            streamUrl = "https://aac.saavncdn.com/207/35add090eadc52dbd0205f0f4500176b_160.mp4",
            isLocal = false
        ),
        Song(
            id = "saavn_flowers",
            title = "Flowers",
            artist = "Miley Cyrus",
            album = "Endless Summer Vacation",
            durationMs = 200000L,
            artworkUrl = "https://c.saavncdn.com/361/Endless-Summer-Vacation-English-2023-20230830062637-500x500.jpg",
            streamUrl = "https://aac.saavncdn.com/361/76f4573834ee4db705e0a80818878dca_160.mp4",
            isLocal = false
        ),
        Song(
            id = "saavn_believer",
            title = "Believer",
            artist = "Imagine Dragons",
            album = "Evolve",
            durationMs = 204000L,
            artworkUrl = "https://c.saavncdn.com/248/Evolve-English-2018-20260605220036-500x500.jpg",
            streamUrl = "https://aac.saavncdn.com/248/46944eb7b4b31f5b0abf5eb2e1be2d2a_160.mp4",
            isLocal = false
        )
    )

    /**
     * Await an OkHttp call as a coroutine. Cancelling the coroutine cancels the
     * underlying network call instead of letting it run to completion in the background.
     */
    private suspend fun Request.await(): Response =
        suspendCancellableCoroutine { cont ->
            val call = httpClient.newCall(this)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    if (cont.isActive) cont.resume(response)
                }

                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resumeWithException(e)
                }
            })
        }

    internal suspend fun executeGet(url: String, saavnHeaders: Boolean = false): String? = withContext(Dispatchers.IO) {
        val request = if (saavnHeaders) {
            saavnRequest(url)
        } else {
            Request.Builder()
                .url(url)
                .header("User-Agent", "Spotifly-Android/2.0")
                .build()
        }
        runCatching {
            request.await().use { response ->
                if (!response.isSuccessful) null else response.body?.string()?.takeIf { it.isNotBlank() }
            }
        }.getOrNull()
    }

    /**
     * Search songs online using unified catalogue layer:
     * Searches Deezer, iTunes, and JioSaavn in parallel.
     * Dedupes and merges candidates across sources, normalising metadata.
     * Ranks exact title+artist matches first.
     * Marks tracks without direct streams as unplayable (greyed out) instead of hiding them.
     */
    suspend fun searchSongs(
        query: String,
        onPartialResults: (suspend (List<Song>) -> Unit)? = null
    ): List<Song> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return@withContext emptyList()

        synchronized(searchCacheLock) {
            searchCache[cleanQuery]?.takeIf { it.expiresAt > System.currentTimeMillis() }
                ?.let { return@withContext it.songs }
        }

        val results = coroutineScope {
            val providers: List<suspend () -> List<Song>> = listOf(
                { searchJioSaavnEnhanced(cleanQuery) },
                { searchITunes(cleanQuery) },
                { searchDeezer(cleanQuery) }
            )
            val completed = Channel<Pair<Int, List<Song>>>(providers.size)
            providers.forEachIndexed { index, fetch ->
                launch { completed.send(index to searchSource(fetch)) }
            }
            val byProvider = MutableList(providers.size) { emptyList<Song>() }
            repeat(providers.size) {
                val (index, songs) = completed.receive()
                byProvider[index] = songs
                if (songs.isNotEmpty()) onPartialResults?.invoke(rankSearchResults(cleanQuery, byProvider.flatten()))
            }
            rankSearchResults(cleanQuery, byProvider.flatten())
        }

        val finalResults = if (results.isNotEmpty()) {
            results
        } else {
            trendingHits.filter {
                it.title.contains(cleanQuery, ignoreCase = true) ||
                it.artist.contains(cleanQuery, ignoreCase = true) ||
                it.album.contains(cleanQuery, ignoreCase = true)
            }
        }

        synchronized(searchCacheLock) {
            if (finalResults.isNotEmpty()) {
                searchCache[cleanQuery] = SearchCacheEntry(finalResults, System.currentTimeMillis() + 120_000L)
            }
        }
        finalResults
    }

    private fun rankSearchResults(cleanQuery: String, tracks: List<Song>): List<Song> {
        val merged = mutableListOf<Song>()
        fun addOrMerge(track: Song) {
            val matchIndex = merged.indexOfFirst { TrackMatching.isSameRecording(it, track) }
            if (matchIndex >= 0) {
                merged[matchIndex] = TrackMatching.mergeTracks(merged[matchIndex], track)
            } else {
                merged.add(track)
            }
        }

        tracks.forEach(::addOrMerge)

        // Rank exact matches first
        val normQ = TrackMatching.normalizeTitle(cleanQuery)
        val normQArtist = TrackMatching.normalizeArtist(cleanQuery)
        return merged.sortedWith(
            compareByDescending<Song> { song ->
                val normTitle = TrackMatching.normalizeTitle(song.title)
                val normArt = TrackMatching.normalizeArtist(song.artist)
                var score = 0
                if (song.title.trim().equals(cleanQuery, ignoreCase = true)) score += 50
                if (normTitle == normQ) score += 100
                else if (normTitle.startsWith(normQ)) score += 50
                else if (normTitle.contains(normQ)) score += 25

                if (normArt == normQArtist && normArt.isNotEmpty()) score += 80
                else if (normArt.isNotEmpty() && (normArt.contains(normQArtist) || normQ.contains(normArt))) score += 30

                if (song.isPlayable || song.sourceIds.saavn != null) score += 10
                score
            }.thenByDescending { it.sourceIds.saavn != null }
        )
    }

    private suspend fun searchSource(block: suspend () -> List<Song>): List<Song> =
        withTimeoutOrNull(8_000L) {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyList()
            }
        }.orEmpty()

    /**
     * Legacy JioSaavn search (before changes):
     * - Small limit (10), page 0
     * - Only song search, no album or artist expansion
     * - No query cleaning fallback
     */
    suspend fun searchJioSaavnLegacyForTesting(query: String): List<Song> = withContext(Dispatchers.IO) {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        searchJioSaavnPage(encodedQuery, page = 0, limit = 10)
    }

    /**
     * Enhanced JioSaavn search:
     * - Limit raised to 40-50 and pagination support
     * - Queries songs, albums AND artists in parallel; expands album tracklists and artist top tracks into songs
     * - Runs cleaned query fallback if raw query returns < 3 results
     */
    suspend fun searchJioSaavnEnhanced(query: String): List<Song> = withContext(Dispatchers.IO) {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val songs = searchJioSaavnPage(encodedQuery, page = 1, limit = 50)
        if (songs.isNotEmpty()) return@withContext songs
        val cleaned = TrackMatching.normalizeTitle(query)
        if (cleaned.isNotBlank() && cleaned != query.lowercase().trim()) {
            return@withContext searchJioSaavnPage(URLEncoder.encode(cleaned, "UTF-8"), page = 1, limit = 50)
        }
        emptyList()
    }

    private suspend fun searchJioSaavnPage(encodedQuery: String, page: Int, limit: Int): List<Song> {
        val results = mutableListOf<Song>()
        runCatching {
            val url = "https://www.jiosaavn.com/api.php?__call=search.getResults&_format=json&n=$limit&p=$page&q=$encodedQuery"
            val response = saavnRequest(url).await()
            response.use {
                if (!it.isSuccessful) return emptyList()
                val jsonString = it.body?.string().orEmpty()
                if (jsonString.isBlank()) return emptyList()
                val jsonObject = JSONObject(jsonString)
                val resultsArray = jsonObject.optJSONArray("results") ?: JSONArray()
                for (i in 0 until resultsArray.length()) {
                    val item = resultsArray.getJSONObject(i)
                    parseSaavnSong(item, i)?.let { results.add(it) }
                }
            }
        }
        return results
    }

    suspend fun searchDeezer(query: String): List<Song> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Song>()
        runCatching {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://api.deezer.com/search?q=$encoded&limit=50"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val response = request.await()
            response.use {
                if (!it.isSuccessful) return@use
                val body = it.body?.string().orEmpty()
                if (body.isBlank()) return@use
                val json = JSONObject(body)
                val array = json.optJSONArray("data") ?: JSONArray()
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val id = item.optLong("id", 0L).toString()
                    val title = unescapeHtml(item.optString("title", item.optString("title_short", "Unknown Track")))
                    val artistObj = item.optJSONObject("artist")
                    val artistName = unescapeHtml(artistObj?.optString("name", "Unknown Artist") ?: "Unknown Artist")
                    val albumObj = item.optJSONObject("album")
                    val albumTitle = unescapeHtml(albumObj?.optString("title", "") ?: "")
                    val artwork = albumObj?.optString("cover_xl", albumObj.optString("cover_big", albumObj.optString("cover_medium", "")))?.ifBlank { null }
                    val durationSec = item.optLong("duration", 0L)
                    val isrc = item.optString("isrc", "").ifBlank { null }
                    val splitArtists = TrackMatching.splitArtists(artistName)

                    results.add(
                        Song(
                            id = "deezer_$id",
                            internalId = "deezer_$id",
                            title = title,
                            artist = artistName,
                            artists = splitArtists,
                            album = albumTitle,
                            durationSec = durationSec,
                            durationMs = durationSec * 1000L,
                            artworkUrl = artwork,
                            isrc = isrc,
                            sourceIds = SourceIds(deezer = id),
                            streamUrl = null, // Never use Deezer preview URL as full playback!
                            isPlayable = false
                        )
                    )
                }
            }
        }
        results
    }

    /**
     * Fetch a broad candidate pool for autoplay.
     *
     * Three sources run in parallel and are tagged so the ranker can give
     * catalogue recommendations a small prior:
     * 1. JioSaavn's "recommended" endpoint for the seed recording
     * 2. a mood/language search ("hindi sad songs")
     * 3. the seed artist's catalogue
     *
     * Anything unreachable degrades to an empty list rather than failing the
     * whole pick; the local/downloaded pool is merged by the caller.
     */
    suspend fun autoplayCandidates(seed: Song, vibeQueries: List<String>): List<AutoplayCandidate> =
        withContext(Dispatchers.IO) {
            coroutineScope {
                val recommendations = async { fetchSaavnRecommendations(seed) }
                val vibeSearches = vibeQueries
                    .filter { it.isNotBlank() }
                    .distinct()
                    .map { query ->
                        async {
                            runCatching { searchJioSaavn(query) }.getOrDefault(emptyList())
                        }
                    }
                val artist = async {
                    val primary = seed.artist.split(",", "&").firstOrNull()?.trim().orEmpty()
                    if (primary.isBlank()) emptyList()
                    else runCatching { searchJioSaavn(primary) }.getOrDefault(emptyList())
                }

                buildList {
                    recommendations.await().forEachIndexed { index, song ->
                        add(AutoplayCandidate(song, CandidateSource.RECOMMENDED, index))
                    }
                    vibeSearches.forEach { search ->
                        search.await().forEach { add(AutoplayCandidate(it, CandidateSource.VIBE)) }
                    }
                    artist.await().forEach { add(AutoplayCandidate(it, CandidateSource.ARTIST)) }
                }.distinctBy { it.song.id }
            }
        }

    /** JioSaavn's per-track recommendation list; empty for non-Saavn seeds. */
    private suspend fun fetchSaavnRecommendations(song: Song): List<Song> {
        if (!song.id.startsWith("saavn_")) return emptyList()
        val pid = song.id.removePrefix("saavn_")
        if (pid.isBlank()) return emptyList()

        return runCatching {
            val url = "https://www.jiosaavn.com/api.php?__call=reco.getreco&_format=json&pid=$pid"
            val response = saavnRequest(url).await()
            response.use {
                if (!it.isSuccessful) return@use emptyList()
                val body = it.body?.string().orEmpty()
                if (body.isBlank()) return@use emptyList()
                val array = JSONObject(body).optJSONArray(pid) ?: return@use emptyList()
                buildList {
                    for (i in 0 until array.length()) {
                        parseSaavnSong(array.getJSONObject(i), i)?.let(::add)
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    private suspend fun searchJioSaavn(query: String): List<Song> {
        val results = mutableListOf<Song>()
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "https://www.jiosaavn.com/api.php?__call=search.getResults&_format=json&n=30&p=1&q=$encodedQuery"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("Referer", "https://www.jiosaavn.com/")
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()

        val response = request.await()
        response.use {
            if (!it.isSuccessful) return emptyList()

            val jsonString = it.body?.string().orEmpty()
            if (jsonString.isBlank()) return emptyList()

            val jsonObject = JSONObject(jsonString)
            val resultsArray = jsonObject.optJSONArray("results") ?: JSONArray()

            for (i in 0 until resultsArray.length()) {
                val item = resultsArray.getJSONObject(i)
                parseSaavnSong(item, i)?.let { results.add(it) }
            }
        }
        return results
    }

    fun parseSaavnSong(item: JSONObject, index: Int): Song? {
        return try {
            val id = item.optString("id", (System.currentTimeMillis() + index).toString())
            val songName = unescapeHtml(item.optString("song", "Unknown Track"))
            val artistName = unescapeHtml(
                item.optString("primary_artists", item.optString("singers", "Unknown Artist"))
            ).ifEmpty { "Unknown Artist" }
            val albumName = unescapeHtml(item.optString("album", ""))
            val durationSec = item.optLong("duration", 0L)
            val durationMs = if (durationSec > 0) durationSec * 1000L else 180000L
            val language = unescapeHtml(item.optString("language", "")).ifBlank { null }
            val year = item.optString("year", "").toIntOrNull() ?: 0

            val rawImage = item.optString("image", "")
            val artworkUrl = rawImage
                .replace("150x150", "500x500")
                .replace("50x50", "500x500")
                .replace("\\/", "/")
                .ifEmpty { null }

            val encryptedUrl = item.optString("encrypted_media_url", "")
            val decryptedStream = decryptMediaUrl(encryptedUrl)
            val fallbackPreview = item.optString("media_preview_url", "").replace("\\/", "/")

            val finalStreamUrl = decryptedStream ?: fallbackPreview.ifEmpty { null }

            Song(
                id = "saavn_$id",
                internalId = "saavn_$id",
                title = songName,
                artist = artistName,
                artists = TrackMatching.splitArtists(artistName),
                album = albumName,
                durationMs = durationMs,
                durationSec = durationSec,
                artworkUrl = artworkUrl,
                streamUrl = finalStreamUrl,
                isLocal = false,
                year = year,
                language = language,
                albumId = item.optString("albumid").ifBlank { null },
                artistId = item.optString("primary_artists_id").split(",").firstOrNull()?.trim()?.ifBlank { null },
                sourceIds = SourceIds(saavn = id),
                isPlayable = !finalStreamUrl.isNullOrBlank()
            )
        } catch (e: Exception) {
            null
        }
    }

    internal suspend fun searchITunes(query: String, country: String = "IN", limit: Int = 50): List<Song> {
        val results = mutableListOf<Song>()
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "https://itunes.apple.com/search?term=$encodedQuery&country=$country&media=music&entity=song&limit=$limit"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Spotifly-Android/2.0")
            .build()

        val response = request.await()
        response.use {
            if (!it.isSuccessful) return emptyList()

            val jsonString = it.body?.string().orEmpty()
            if (jsonString.isBlank()) return emptyList()
            val jsonObject = JSONObject(jsonString)
            val resultsArray = jsonObject.optJSONArray("results") ?: JSONArray()

            for (i in 0 until resultsArray.length()) {
                val item = resultsArray.getJSONObject(i)
                val trackId = item.optLong("trackId", System.currentTimeMillis() + i).toString()
                val trackName = unescapeHtml(item.optString("trackName", "Unknown Track"))
                val artistName = unescapeHtml(item.optString("artistName", "Unknown Artist"))
                val collectionName = unescapeHtml(item.optString("collectionName", ""))
                val durationMs = item.optLong("trackTimeMillis", 0L)
                val durationSec = durationMs / 1000L
                val rawArtwork = item.optString("artworkUrl100", "")
                val artworkUrl = rawArtwork.replace("100x100bb.jpg", "600x600bb.jpg").ifEmpty { null }
                val genre = item.optString("primaryGenreName", "").ifBlank { null }
                val releaseYear = item.optString("releaseDate", "").take(4).toIntOrNull() ?: 0

                results.add(
                    Song(
                        id = "itunes_$trackId",
                        internalId = "itunes_$trackId",
                        title = trackName,
                        artist = artistName,
                        artists = TrackMatching.splitArtists(artistName),
                        album = collectionName,
                        durationMs = durationMs,
                        durationSec = durationSec,
                        artworkUrl = artworkUrl,
                        streamUrl = null, // IMPORTANT: Never use iTunes previewUrl as full-song playback!
                        isLocal = false,
                        year = releaseYear,
                        genre = genre,
                        sourceIds = SourceIds(itunes = trackId),
                        isPlayable = false
                    )
                )
            }
        }
        return results
    }

    // ---------------------------------------------------------------------
    // Catalogue: albums and artists
    //
    // Spotify's navigation model is artist -> album -> track. These calls give the
    // clone the same three levels using the public JioSaavn catalogue the app already
    // streams from. Every call degrades to a track search when the richer endpoint
    // is unavailable, so the UI never dead-ends.
    // ---------------------------------------------------------------------

    internal fun saavnRequest(url: String): Request = Request.Builder()
        .url(url)
        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        .header("Referer", "https://www.jiosaavn.com/")
        .header("Accept-Language", "en-US,en;q=0.9")
        .build()

    suspend fun searchAlbums(query: String): List<Album> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val encoded = URLEncoder.encode(query, "UTF-8")
        val albums = mutableListOf<Album>()
        runCatching {
            val request = saavnRequest(
                "https://www.jiosaavn.com/api.php?__call=search.getAlbumResults&_format=json&n=20&p=1&q=$encoded"
            )
            request.await().use { response ->
                if (!response.isSuccessful) return@use
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return@use
                val results = JSONObject(body).optJSONArray("results") ?: JSONArray()
                for (i in 0 until results.length()) {
                    val item = results.getJSONObject(i)
                    val id = item.optString("albumid", "").ifBlank { item.optString("id", "") }
                    val title = unescapeHtml(item.optString("title", item.optString("name", "")))
                    if (id.isBlank() || title.isBlank()) continue
                    val image = item.optString("image", "").replace("150x150", "500x500").replace("\\/", "/")
                    val artist = unescapeHtml(item.optString("music", item.optString("artist", "")))
                    val year = item.optJSONObject("more_info")?.optString("year", "")?.toIntOrNull() ?: 0
                    albums.add(
                        Album(
                            id = "saavn_album_$id",
                            title = title,
                            artist = artist,
                            artworkUrl = image.ifBlank { null },
                            year = year
                        )
                    )
                }
            }
        }
        if (albums.isNotEmpty()) return@withContext albums.distinctBy { it.id }

        // Fallback: derive albums from track results
        runCatching { searchJioSaavn(query) }
            .getOrDefault(emptyList())
            .filter { it.album.isNotBlank() }
            .groupBy { it.albumKey }
            .map { (key, tracks) ->
                val lead = tracks.first()
                Album(
                    id = "album_$key",
                    title = lead.album,
                    artist = lead.artist,
                    artworkUrl = tracks.firstNotNullOfOrNull { it.artworkUrl },
                    songs = tracks,
                    isLocal = false
                )
            }
    }

    suspend fun searchArtists(query: String): List<Artist> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val encoded = URLEncoder.encode(query, "UTF-8")
        val artists = mutableListOf<Artist>()
        runCatching {
            val request = saavnRequest(
                "https://www.jiosaavn.com/api.php?__call=search.getArtistResults&_format=json&n=20&p=1&q=$encoded"
            )
            request.await().use { response ->
                if (!response.isSuccessful) return@use
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return@use
                val results = JSONObject(body).optJSONArray("results") ?: JSONArray()
                for (i in 0 until results.length()) {
                    val item = results.getJSONObject(i)
                    val id = item.optString("artistid", "").ifBlank { item.optString("id", "") }
                    val name = unescapeHtml(item.optString("name", item.optString("title", "")))
                    if (id.isBlank() || name.isBlank()) continue
                    val image = item.optString("image", "").replace("150x150", "500x500").replace("\\/", "/")
                    artists.add(
                        Artist(
                            id = "saavn_artist_$id",
                            name = name,
                            artworkUrl = image.ifBlank { null }
                        )
                    )
                }
            }
        }
        if (artists.isNotEmpty()) return@withContext artists.distinctBy { it.id }

        runCatching { searchJioSaavn(query) }
            .getOrDefault(emptyList())
            .groupBy { it.artistKey }
            .map { (key, tracks) ->
                val lead = tracks.first()
                Artist(
                    id = "artist_$key",
                    name = lead.artist.split(",").first().trim(),
                    artworkUrl = tracks.firstNotNullOfOrNull { it.artworkUrl },
                    songs = tracks
                )
            }
    }

    suspend fun getAlbumSongs(albumId: String, fallbackTitle: String = ""): List<Song> =
        withContext(Dispatchers.IO) {
            val numericId = albumId.removePrefix("saavn_album_")
            if (albumId.startsWith("saavn_album_")) {
                runCatching {
                    val request = saavnRequest(
                        "https://www.jiosaavn.com/api.php?__call=content.getAlbumDetails&_format=json&albumid=$numericId"
                    )
                    request.await().use { response ->
                        if (!response.isSuccessful) return@use emptyList<Song>()
                        val body = response.body?.string().orEmpty()
                        if (body.isBlank()) return@use emptyList<Song>()
                        val array = JSONObject(body).optJSONArray("songs") ?: JSONArray()
                        val songs = mutableListOf<Song>()
                        for (i in 0 until array.length()) {
                            parseSaavnSong(array.getJSONObject(i), i)?.let { songs.add(it) }
                        }
                        songs
                    }
                }.getOrDefault(emptyList()).let { if (it.isNotEmpty()) return@withContext it }
            }

            val query = fallbackTitle.ifBlank { albumId.substringAfterLast('_') }
            runCatching { searchJioSaavn(query) }.getOrDefault(emptyList())
        }

    suspend fun getArtistSongs(artistId: String, fallbackName: String = ""): List<Song> =
        withContext(Dispatchers.IO) {
            if (artistId.startsWith("deezer_artist_")) {
                val deezerId = artistId.removePrefix("deezer_artist_")
                val targetName = fallbackName.ifBlank { deezerId }
                return@withContext coroutineScope {
                    val deezerTop = async { DiscoveryService.getDeezerArtistTopTracks(deezerId) }
                    val saavnHits = async { runCatching { searchJioSaavnEnhanced(targetName) }.getOrDefault(emptyList()) }
                    val merged = mutableListOf<Song>()
                    fun addOrMerge(track: Song) {
                        val idx = merged.indexOfFirst { TrackMatching.isSameRecording(it, track) }
                        if (idx >= 0) merged[idx] = TrackMatching.mergeTracks(merged[idx], track)
                        else merged.add(track)
                    }
                    saavnHits.await()
                        .filter { song ->
                            val normArtist = TrackMatching.normalizeArtist(targetName)
                            normArtist.isBlank() || TrackMatching.normalizeArtist(song.artist).contains(normArtist) ||
                                song.artists.any { TrackMatching.normalizeArtist(it) == normArtist }
                        }
                        .forEach(::addOrMerge)
                    deezerTop.await().forEach(::addOrMerge)
                    merged.ifEmpty { deezerTop.await() }
                }
            }

            val numericId = artistId.removePrefix("saavn_artist_")
            if (artistId.startsWith("saavn_artist_")) {
                runCatching {
                    val request = saavnRequest(
                        "https://www.jiosaavn.com/api.php?__call=artist.getArtistPageDetails&_format=json&artistId=$numericId"
                    )
                    request.await().use { response ->
                        if (!response.isSuccessful) return@use emptyList<Song>()
                        val body = response.body?.string().orEmpty()
                        if (body.isBlank()) return@use emptyList<Song>()
                        val json = JSONObject(body)
                        val array = json.optJSONObject("topSongs")?.optJSONArray("songs")
                            ?: json.optJSONArray("topSongs")
                            ?: json.optJSONArray("songs")
                            ?: JSONArray()
                        val songs = mutableListOf<Song>()
                        for (i in 0 until array.length()) {
                            parseSaavnSong(array.getJSONObject(i), i)?.let { songs.add(it) }
                        }
                        songs
                    }
                }.getOrDefault(emptyList()).let { if (it.isNotEmpty()) return@withContext it }
            }

            val query = fallbackName.ifBlank { artistId.substringAfterLast('_') }
            runCatching { searchJioSaavn(query) }.getOrDefault(emptyList())
        }

    // JioSaavn supplies DES/ECB encrypted URLs. This only decrypts its wire format.
    @android.annotation.SuppressLint("GetInstance")
    private fun decryptMediaUrl(encryptedMediaUrl: String): String? {
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
                rawUrl.replace("_96.mp4", "_160.mp4")
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun unescapeHtml(text: String): String {
        if (text.isEmpty()) return text
        return try {
            Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY).toString().trim()
        } catch (e: Exception) {
            text.replace("&quot;", "\"")
                .replace("&amp;", "&")
                .replace("&#039;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&nbsp;", " ")
        }
    }

    /**
     * Fetch synchronized or plain lyrics from LRCLIB, with an in-memory cache.
     */
    suspend fun fetchLyrics(song: Song): Lyrics? = withContext(Dispatchers.IO) {
        val cacheKey = "${song.id}|${song.title}|${song.artist}"
        synchronized(lyricsCacheLock) { lyricsCache[cacheKey] }?.let { cached -> return@withContext cached }

        val result = try {
            val cleanTitle = song.title.replace(Regex("""\(.*?\)"""), "").trim()
            val primaryArtist = song.artist.split(",")[0].replace(Regex("""ft\..*""", RegexOption.IGNORE_CASE), "").trim()
            val encodedTitle = URLEncoder.encode(cleanTitle.ifEmpty { song.title }, "UTF-8")
            val encodedArtist = URLEncoder.encode(primaryArtist.ifEmpty { song.artist }, "UTF-8")
            val durationSec = (song.durationMs / 1000).coerceAtLeast(1)

            val url = "https://lrclib.net/api/get?track_name=$encodedTitle&artist_name=$encodedArtist&duration=$durationSec"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Spotifly-Android/2.0 (https://github.com/example/spotifly)")
                .build()

            val response = request.await()
            var fetched: Lyrics? = null
            response.use {
                if (it.isSuccessful) {
                    val body = it.body?.string().orEmpty()
                    if (body.isNotBlank()) {
                        fetched = parseLrclibRecord(song, JSONObject(body))
                    }
                }
            }
            if (fetched == null) {
                val searchUrl = "https://lrclib.net/api/search?track_name=$encodedTitle&artist_name=$encodedArtist"
                val searchReq = Request.Builder()
                    .url(searchUrl)
                    .header("User-Agent", "Spotifly-Android/2.0 (https://github.com/example/spotifly)")
                    .build()
                searchReq.await().use { res ->
                    if (res.isSuccessful) {
                        val body = res.body?.string().orEmpty()
                        if (body.isNotBlank()) {
                            val arr = JSONArray(body)
                            for (i in 0 until arr.length()) {
                                val candidate = parseLrclibRecord(song, arr.getJSONObject(i)) ?: continue
                                if (candidate.isSynced) {
                                    fetched = candidate
                                    break
                                } else if (fetched == null) {
                                    fetched = candidate
                                }
                            }
                        }
                    }
                }
            }
            fetched
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            e.printStackTrace()
            null
        }

        synchronized(lyricsCacheLock) { lyricsCache[cacheKey] = result }
        result
    }

    private fun parseLrclibRecord(song: Song, json: JSONObject): Lyrics? {
        val syncedLrc = json.optString("syncedLyrics", "").takeIf { it.isNotBlank() && it != "null" }.orEmpty()
        val plainText = json.optString("plainLyrics", "").takeIf { it.isNotBlank() && it != "null" }.orEmpty()
        if (syncedLrc.isNotBlank()) {
            val lines = Lyrics.parseLrc(syncedLrc)
            return Lyrics(
                trackName = song.title,
                artistName = song.artist,
                lines = lines,
                plainLyrics = plainText.ifEmpty { null },
                isSynced = lines.isNotEmpty()
            )
        } else if (plainText.isNotBlank()) {
            return Lyrics(
                trackName = song.title,
                artistName = song.artist,
                lines = emptyList(),
                plainLyrics = plainText,
                isSynced = false
            )
        }
        return null
    }
}
