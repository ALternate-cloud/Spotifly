package com.example.spotifly.data.podcast

import android.util.Log
import com.example.spotifly.model.PodcastEpisode
import com.example.spotifly.model.PodcastShow
import com.example.spotifly.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Podcast discovery and episode loading through public channels only: the iTunes
 * search index for discovery, each show's own RSS feed for episodes. No Spotify
 * catalogue, no keys, no scraping.
 */
object PodcastService {

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun searchShows(query: String): List<PodcastShow> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val encoded = URLEncoder.encode(query, "UTF-8")
        val request = Request.Builder()
            .url("https://itunes.apple.com/search?media=podcast&entity=podcast&limit=25&term=$encoded")
            .header("User-Agent", USER_AGENT)
            .build()

        runCatching {
            request.await().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Podcast search HTTP ${response.code} for \"$query\"")
                    return@use emptyList<PodcastShow>()
                }
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) {
                    Log.w(TAG, "Podcast search returned an empty body for \"$query\"")
                    return@use emptyList<PodcastShow>()
                }

                val results = JSONObject(body).optJSONArray("results")
                if (results == null) {
                    Log.w(TAG, "Podcast search response had no results array")
                    return@use emptyList<PodcastShow>()
                }

                val shows = mutableListOf<PodcastShow>()
                for (i in 0 until results.length()) {
                    val item = results.optJSONObject(i) ?: continue
                    val feedUrl = item.optString("feedUrl", "")
                    val title = item.optString("collectionName", item.optString("trackName", ""))
                    if (feedUrl.isBlank() || title.isBlank()) continue
                    shows.add(
                        PodcastShow(
                            id = "itunes_${item.optLong("collectionId", title.hashCode().toLong())}",
                            title = title,
                            publisher = item.optString("artistName", ""),
                            artworkUrl = item.optString("artworkUrl600", "")
                                .ifBlank { item.optString("artworkUrl100", "") },
                            feedUrl = feedUrl,
                            genre = item.optString("primaryGenreName", ""),
                            episodeCount = item.optInt("trackCount", 0)
                        )
                    )
                }
                shows.distinctBy { it.feedUrl }
            }
        }.onFailure { error ->
            Log.w(TAG, "Podcast search failed for \"$query\"", error)
        }.getOrDefault(emptyList())
    }

    /** Fetches and parses a show's RSS feed into episodes, newest first as published. */
    suspend fun loadEpisodes(show: PodcastShow): List<PodcastEpisode> = withContext(Dispatchers.IO) {
        if (show.feedUrl.isBlank()) return@withContext emptyList()
        val request = Request.Builder()
            .url(show.feedUrl)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/rss+xml, application/xml, text/xml, */*")
            .build()

        runCatching {
            request.await().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Feed HTTP ${response.code} for ${show.title}")
                    return@use emptyList<PodcastEpisode>()
                }
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) {
                    Log.w(TAG, "Empty feed body for ${show.title}")
                    return@use emptyList<PodcastEpisode>()
                }
                val parsed = PodcastFeedParser.parse(
                    xml = body,
                    showId = show.id,
                    showTitle = show.title,
                    fallbackArtwork = show.artworkUrl
                )
                if (parsed.isEmpty()) {
                    Log.w(TAG, "Feed for ${show.title} had ${body.length} chars but no playable episodes")
                } else {
                    Log.d(TAG, "Parsed ${parsed.size} episodes for ${show.title}")
                }
                parsed
            }
        }.onFailure { error ->
            Log.w(TAG, "Feed load failed for ${show.title}", error)
        }.getOrDefault(emptyList())
    }

    /** A podcast episode is just another playable item, so it rides the music queue. */
    fun episodeAsSong(episode: PodcastEpisode): Song = Song(
        id = "podcast_${episode.id}",
        title = episode.title,
        artist = episode.showTitle.ifBlank { "Podcast" },
        album = "Podcast",
        durationMs = episode.durationMs,
        artworkUrl = episode.artworkUrl,
        streamUrl = episode.audioUrl,
        isPodcast = true
    )

    private suspend fun Request.await(): Response =
        suspendCancellableCoroutine { continuation ->
            val call = httpClient.newCall(this)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    if (continuation.isActive) continuation.resume(response) else response.close()
                }
            })
        }

    private const val TAG = "PodcastService"

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
}
