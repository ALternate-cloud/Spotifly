package com.example.spotifly.data.online

import com.example.spotifly.data.catalog.TrackMatching
import com.example.spotifly.model.Artist
import com.example.spotifly.model.Lyrics
import com.example.spotifly.model.Song
import com.example.spotifly.model.SourceIds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

data class ChartFeed(
    val id: String,
    val title: String,
    val subtitle: String,
    val regionBadge: String,
    val artworkUrl: String?,
    val songs: List<Song>
)

data class LyricSearchMatch(
    val song: Song,
    val matchedSnippet: String? = null
)

internal data class SaavnChartDescriptor(
    val id: String,
    val title: String,
    val artworkUrl: String?
)

/**
 * Discovery & Content Intelligence service powering:
 * 1. Global & Regional Top Charts (iTunes RSS, Apple Music Most Played, JioSaavn Charts)
 * 2. "Fans Also Like" Related Artists graph (Deezer Related Artists + library collaborator fallback)
 * 3. Search by Lyrics (full-text lyric search across iTunes lyric index, LRCLIB & JioSaavn,
 *    with exact lyric line snippet verification)
 */
object DiscoveryService {

    private const val CACHE_TTL_MS = 10 * 60 * 1000L

    private data class ChartsCacheEntry(val feeds: List<ChartFeed>, val expiresAt: Long)
    private data class RelatedCacheEntry(val artists: List<Artist>, val expiresAt: Long)

    @Volatile
    private var chartsCache: ChartsCacheEntry? = null
    private val relatedCache = LinkedHashMap<String, RelatedCacheEntry>(32, 0.75f, true)
    private val cacheLock = Any()

    internal fun clearCachesForTesting() {
        synchronized(cacheLock) {
            chartsCache = null
            relatedCache.clear()
        }
    }

    // =========================================================================
    // 1. Global & Regional Top Charts
    // =========================================================================

    suspend fun getTopCharts(forceRefresh: Boolean = false): List<ChartFeed> = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            chartsCache?.takeIf { it.expiresAt > System.currentTimeMillis() }?.let {
                return@withContext it.feeds
            }
        }

        val feeds = coroutineScope {
            val globalDeferred = async { fetchItunesChartFeed("us", "chart_global", "Global Top 25", "Worldwide trending hits", "GLOBAL") }
            val indiaDeferred = async { fetchIndiaSuperhitsChartFeed() }
            val ukDeferred = async { fetchItunesChartFeed("gb", "chart_uk", "UK Top 25", "United Kingdom chart toppers", "UK") }
            val newReleasesDeferred = async { fetchRegionalAppleMarketingFeed("in", "chart_india_hot", "India Hot 25", "Rising tracks across India", "TRENDING") }

            listOfNotNull(
                globalDeferred.await(),
                indiaDeferred.await(),
                ukDeferred.await(),
                newReleasesDeferred.await()
            )
        }

        if (feeds.isNotEmpty()) {
            chartsCache = ChartsCacheEntry(feeds, System.currentTimeMillis() + CACHE_TTL_MS)
        }
        feeds
    }

    private suspend fun fetchItunesChartFeed(
        country: String,
        feedId: String,
        title: String,
        subtitle: String,
        badge: String
    ): ChartFeed? {
        val rssBody = OnlineMusicService.executeGet(
            "https://itunes.apple.com/$country/rss/topsongs/limit=25/json"
        )
        val rssSongs = rssBody?.let(::parseItunesRssChart).orEmpty()
        val songs = if (rssSongs.isNotEmpty()) {
            rssSongs
        } else {
            val marketingBody = OnlineMusicService.executeGet(
                "https://rss.marketingtools.apple.com/api/v2/$country/music/most-played/25/songs.json"
            )
            marketingBody?.let(::parseAppleMarketingChart).orEmpty()
        }
        if (songs.isEmpty()) return null
        return ChartFeed(
            id = feedId,
            title = title,
            subtitle = subtitle,
            regionBadge = badge,
            artworkUrl = songs.firstNotNullOfOrNull { it.artworkUrl },
            songs = songs
        )
    }

    private suspend fun fetchRegionalAppleMarketingFeed(
        country: String,
        feedId: String,
        title: String,
        subtitle: String,
        badge: String
    ): ChartFeed? {
        val marketingBody = OnlineMusicService.executeGet(
            "https://rss.marketingtools.apple.com/api/v2/$country/music/most-played/25/songs.json"
        )
        val songs = marketingBody?.let(::parseAppleMarketingChart).orEmpty()
            .ifEmpty {
                OnlineMusicService.executeGet("https://itunes.apple.com/$country/rss/topsongs/limit=25/json")
                    ?.let(::parseItunesRssChart)
                    .orEmpty()
            }
        if (songs.isEmpty()) return null
        return ChartFeed(
            id = feedId,
            title = title,
            subtitle = subtitle,
            regionBadge = badge,
            artworkUrl = songs.firstNotNullOfOrNull { it.artworkUrl },
            songs = songs
        )
    }

    private suspend fun fetchIndiaSuperhitsChartFeed(): ChartFeed? {
        val chartsBody = OnlineMusicService.executeGet(
            "https://www.jiosaavn.com/api.php?__call=content.getCharts&_format=json",
            saavnHeaders = true
        )
        val descriptors = chartsBody?.let(::parseSaavnChartDescriptors).orEmpty()
        val primary = descriptors.firstOrNull()
        if (primary != null) {
            val playlistBody = OnlineMusicService.executeGet(
                "https://www.jiosaavn.com/api.php?__call=playlist.getDetails&_format=json&listid=${URLEncoder.encode(primary.id, "UTF-8")}",
                saavnHeaders = true
            )
            if (!playlistBody.isNullOrBlank()) {
                val songs = runCatching {
                    val json = JSONObject(playlistBody)
                    val arr = json.optJSONArray("songs") ?: JSONArray()
                    buildList {
                        for (i in 0 until arr.length()) {
                            OnlineMusicService.parseSaavnSong(arr.getJSONObject(i), i)?.let(::add)
                        }
                    }
                }.getOrDefault(emptyList())

                if (songs.isNotEmpty()) {
                    return ChartFeed(
                        id = "chart_india_saavn_${primary.id}",
                        title = primary.title.removePrefix("Hindi: ").ifBlank { "India Superhits Top 50" },
                        subtitle = "Top streaming tracks in India",
                        regionBadge = "INDIA",
                        artworkUrl = primary.artworkUrl ?: songs.firstNotNullOfOrNull { it.artworkUrl },
                        songs = songs
                    )
                }
            }
        }
        return fetchItunesChartFeed("in", "chart_india", "India Top 25", "Top tracks across India", "INDIA")
    }

    internal fun parseItunesRssChart(jsonText: String): List<Song> = runCatching {
        val feed = JSONObject(jsonText).optJSONObject("feed") ?: return emptyList()
        val entries = mutableListOf<JSONObject>()
        when (val raw = feed.opt("entry")) {
            is JSONArray -> for (i in 0 until raw.length()) {
                raw.optJSONObject(i)?.let(entries::add)
            }
            is JSONObject -> entries.add(raw)
        }
        entries.mapIndexedNotNull { idx, entry ->
            val title = decodeHtmlEntities(entry.optJSONObject("im:name")?.optString("label").orEmpty())
            val artist = decodeHtmlEntities(entry.optJSONObject("im:artist")?.optString("label").orEmpty())
            if (title.isBlank() || artist.isBlank()) return@mapIndexedNotNull null

            val trackId = entry.optJSONObject("id")?.optJSONObject("attributes")?.optString("im:id")
                ?.takeIf { it.isNotBlank() } ?: "rss_$idx"
            val album = decodeHtmlEntities(
                entry.optJSONObject("im:collection")?.optJSONObject("im:name")?.optString("label").orEmpty()
            )
            val images = entry.optJSONArray("im:image")
            val rawImg = if (images != null && images.length() > 0) {
                images.optJSONObject(images.length() - 1)?.optString("label").orEmpty()
            } else ""
            val artwork = rawImg
                .replace("55x55bb", "500x500bb")
                .replace("60x60bb", "500x500bb")
                .replace("170x170bb", "500x500bb")
                .ifBlank { null }
            val genre = entry.optJSONObject("category")?.optJSONObject("attributes")?.optString("label")
                ?.takeIf { it.isNotBlank() }
            val year = entry.optJSONObject("im:releaseDate")?.optString("label")?.take(4)?.toIntOrNull() ?: 0

            Song(
                id = "itunes_$trackId",
                internalId = "itunes_$trackId",
                title = title,
                artist = artist,
                artists = TrackMatching.splitArtists(artist),
                album = album,
                durationMs = 0L,
                durationSec = 0L,
                artworkUrl = artwork,
                isLocal = false,
                year = year,
                genre = genre,
                sourceIds = SourceIds(itunes = trackId),
                isPlayable = false
            )
        }
    }.getOrDefault(emptyList())

    internal fun parseAppleMarketingChart(jsonText: String): List<Song> = runCatching {
        val results = JSONObject(jsonText).optJSONObject("feed")?.optJSONArray("results") ?: return emptyList()
        buildList {
            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                val title = decodeHtmlEntities(item.optString("name", ""))
                val artist = decodeHtmlEntities(item.optString("artistName", ""))
                val id = item.optString("id", "")
                if (title.isBlank() || artist.isBlank() || id.isBlank()) continue
                val rawArt = item.optString("artworkUrl100", "")
                val artwork = rawArt
                    .replace("100x100bb", "500x500bb")
                    .ifBlank { null }
                val year = item.optString("releaseDate", "").take(4).toIntOrNull() ?: 0
                val genre = item.optJSONArray("genres")?.optJSONObject(0)?.optString("name")?.ifBlank { null }

                add(
                    Song(
                        id = "itunes_$id",
                        internalId = "itunes_$id",
                        title = title,
                        artist = artist,
                        artists = TrackMatching.splitArtists(artist),
                        album = "",
                        durationMs = 0L,
                        durationSec = 0L,
                        artworkUrl = artwork,
                        isLocal = false,
                        year = year,
                        genre = genre,
                        artistId = item.optString("artistId").ifBlank { null },
                        sourceIds = SourceIds(itunes = id),
                        isPlayable = false
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    internal fun parseSaavnChartDescriptors(jsonText: String): List<SaavnChartDescriptor> = runCatching {
        val arr = JSONArray(jsonText)
        buildList {
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val id = item.optString("id", "").takeIf { it.isNotBlank() && it != "null" } ?: continue
                val title = decodeHtmlEntities(item.optString("title", "")).takeIf { it.isNotBlank() && it != "null" } ?: continue
                val type = item.optString("type", "")
                if (type.isNotBlank() && type != "playlist") continue
                val img = item.optString("image", "").replace("150x150", "500x500").replace("\\/", "/").ifBlank { null }
                add(SaavnChartDescriptor(id = id, title = title, artworkUrl = img))
            }
        }
    }.getOrDefault(emptyList())

    // =========================================================================
    // 2. "Fans Also Like" / Related Artists
    // =========================================================================

    suspend fun getRelatedArtists(
        artistName: String,
        libraryFallbackSongs: List<Song> = emptyList()
    ): List<Artist> = withContext(Dispatchers.IO) {
        val cleanName = artistName.trim()
        if (cleanName.isBlank()) return@withContext emptyList()
        val cacheKey = TrackMatching.normalizeArtist(cleanName).ifBlank { cleanName.lowercase() }

        synchronized(cacheLock) {
            relatedCache[cacheKey]?.takeIf { it.expiresAt > System.currentTimeMillis() }?.let {
                return@withContext it.artists
            }
        }

        val remote = runCatching {
            val encoded = URLEncoder.encode(cleanName, "UTF-8")
            val searchBody = OnlineMusicService.executeGet("https://api.deezer.com/search/artist?q=$encoded&limit=5")
            val deezerId = searchBody?.let { pickMatchingDeezerArtistId(cleanName, it) }
            if (deezerId != null) {
                val relatedBody = OnlineMusicService.executeGet("https://api.deezer.com/artist/$deezerId/related?limit=15")
                relatedBody?.let { parseDeezerRelatedArtists(it, excludeName = cleanName) }.orEmpty()
            } else {
                emptyList()
            }
        }.getOrDefault(emptyList())

        val combined = if (remote.isNotEmpty()) {
            remote
        } else {
            deriveLibraryRelatedArtists(cleanName, libraryFallbackSongs)
        }

        if (combined.isNotEmpty()) {
            synchronized(cacheLock) {
                if (relatedCache.size > 40) {
                    relatedCache.keys.firstOrNull()?.let { relatedCache.remove(it) }
                }
                relatedCache[cacheKey] = RelatedCacheEntry(combined, System.currentTimeMillis() + CACHE_TTL_MS)
            }
        }
        combined
    }

    suspend fun getDeezerArtistTopTracks(deezerArtistId: String): List<Song> = withContext(Dispatchers.IO) {
        val cleanId = deezerArtistId.removePrefix("deezer_artist_").trim()
        if (cleanId.isBlank()) return@withContext emptyList()
        val body = OnlineMusicService.executeGet("https://api.deezer.com/artist/$cleanId/top?limit=25")
            ?: return@withContext emptyList()
        parseDeezerTrackArray(body)
    }

    internal fun pickMatchingDeezerArtistId(targetName: String, searchJson: String): String? = runCatching {
        val arr = JSONObject(searchJson).optJSONArray("data") ?: return null
        val normTarget = TrackMatching.normalizeArtist(targetName)
        var partialMatchId: String? = null
        var fallbackId: String? = null
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val id = item.optLong("id", 0L).takeIf { it > 0L }?.toString() ?: continue
            val name = decodeHtmlEntities(item.optString("name", ""))
            if (fallbackId == null) fallbackId = id
            val normCandidate = TrackMatching.normalizeArtist(name)
            if (normCandidate == normTarget) return id
            if (partialMatchId == null && normTarget.isNotBlank() &&
                (normCandidate.contains(normTarget) || normTarget.contains(normCandidate))
            ) {
                partialMatchId = id
            }
        }
        partialMatchId ?: fallbackId
    }.getOrNull()

    internal fun parseDeezerRelatedArtists(jsonText: String, excludeName: String = ""): List<Artist> = runCatching {
        val arr = JSONObject(jsonText).optJSONArray("data") ?: return emptyList()
        val excludeNorm = TrackMatching.normalizeArtist(excludeName)
        buildList {
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val id = item.optLong("id", 0L).takeIf { it > 0L } ?: continue
                val name = decodeHtmlEntities(item.optString("name", ""))
                if (name.isBlank()) continue
                if (excludeNorm.isNotBlank() && TrackMatching.normalizeArtist(name) == excludeNorm) continue
                val artwork = listOf(
                    item.optString("picture_big", ""),
                    item.optString("picture_xl", ""),
                    item.optString("picture_medium", "")
                ).firstOrNull { it.isNotBlank() && !it.endsWith("/artist//500x500-000000-80-0-0.jpg") }
                add(
                    Artist(
                        id = "deezer_artist_$id",
                        name = name,
                        artworkUrl = artwork
                    )
                )
            }
        }.distinctBy { TrackMatching.normalizeArtist(it.name) }
    }.getOrDefault(emptyList())

    internal fun parseDeezerTrackArray(jsonText: String): List<Song> = runCatching {
        val arr = JSONObject(jsonText).optJSONArray("data") ?: return emptyList()
        buildList {
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val id = item.optLong("id", 0L).takeIf { it > 0L }?.toString() ?: continue
                val title = decodeHtmlEntities(item.optString("title", item.optString("title_short", "")))
                if (title.isBlank()) continue
                val artistName = decodeHtmlEntities(
                    item.optJSONObject("artist")?.optString("name", "Unknown Artist").orEmpty()
                ).ifBlank { "Unknown Artist" }
                val albumTitle = decodeHtmlEntities(item.optJSONObject("album")?.optString("title", "").orEmpty())
                val albumObj = item.optJSONObject("album")
                val artwork = albumObj?.let { a ->
                    listOf(
                        a.optString("cover_xl", ""),
                        a.optString("cover_big", ""),
                        a.optString("cover_medium", "")
                    ).firstOrNull { it.isNotBlank() }
                }
                val durationSec = item.optLong("duration", 0L)
                val isrc = item.optString("isrc", "").ifBlank { null }
                add(
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
                        isrc = isrc,
                        sourceIds = SourceIds(deezer = id),
                        streamUrl = null,
                        isPlayable = false
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    internal fun deriveLibraryRelatedArtists(artistName: String, library: List<Song>): List<Artist> {
        val targetNorm = TrackMatching.normalizeArtist(artistName)
        if (targetNorm.isBlank() || library.isEmpty()) return emptyList()

        // Find collaborators who share songs or genre/language with the target artist
        val artistSongs = library.filter { song ->
            TrackMatching.splitArtists(song.artist).any { TrackMatching.normalizeArtist(it) == targetNorm }
        }
        val targetGenres = artistSongs.mapNotNull { it.genre?.lowercase() }.toSet()
        val targetLanguages = artistSongs.mapNotNull { it.language?.lowercase() }.toSet()

        val collaboratorCounts = HashMap<String, Int>()
        val artistTracks = HashMap<String, MutableList<Song>>()

        for (song in library) {
            val songArtists = (song.artists.ifEmpty { TrackMatching.splitArtists(song.artist) })
                .map { it.trim() }
                .filter { it.isNotBlank() }
            val hasTarget = songArtists.any { TrackMatching.normalizeArtist(it) == targetNorm }
            val sameVibe = (song.genre?.lowercase() in targetGenres) || (song.language?.lowercase() in targetLanguages)

            for (candidate in songArtists) {
                val candNorm = TrackMatching.normalizeArtist(candidate)
                if (candNorm.isBlank() || candNorm == targetNorm) continue
                artistTracks.getOrPut(candidate) { mutableListOf() }.add(song)
                if (hasTarget) {
                    collaboratorCounts[candidate] = (collaboratorCounts[candidate] ?: 0) + 5
                } else if (sameVibe) {
                    collaboratorCounts[candidate] = (collaboratorCounts[candidate] ?: 0) + 1
                }
            }
        }

        return collaboratorCounts.entries
            .sortedByDescending { it.value }
            .take(10)
            .map { (name, _) ->
                val songs = artistTracks[name].orEmpty().distinctBy { it.id }
                Artist(
                    id = "artist_${TrackMatching.normalizeArtist(name)}",
                    name = name,
                    artworkUrl = songs.firstNotNullOfOrNull { it.artworkUrl },
                    songs = songs
                )
            }
    }

    // =========================================================================
    // 3. Search by Lyrics
    // =========================================================================

    suspend fun searchByLyrics(phrase: String): List<LyricSearchMatch> = withContext(Dispatchers.IO) {
        val cleanPhrase = phrase.trim()
        if (cleanPhrase.length < 3) return@withContext emptyList()

        val wantsCoverOrRemix = TrackMatching.isCover(cleanPhrase, cleanPhrase) ||
            TrackMatching.isRemix(cleanPhrase) ||
            TrackMatching.isKaraoke(cleanPhrase)

        val (itunesIn, itunesUs, lrclibMatches, saavnCandidates) = coroutineScope {
            val dIn = async { runCatching { OnlineMusicService.searchITunes(cleanPhrase, country = "IN", limit = 20) }.getOrDefault(emptyList()) }
            val dUs = async { runCatching { OnlineMusicService.searchITunes(cleanPhrase, country = "US", limit = 20) }.getOrDefault(emptyList()) }
            val dLrclib = async { fetchLrclibDirectMatches(cleanPhrase) }
            val dSaavn = async { runCatching { OnlineMusicService.searchJioSaavnEnhanced(cleanPhrase) }.getOrDefault(emptyList()) }
            Quadruple(dIn.await(), dUs.await(), dLrclib.await(), dSaavn.await())
        }

        val snippetByRecordingKey = HashMap<String, String>()
        lrclibMatches.forEach { (song, snippet) ->
            if (!snippet.isNullOrBlank()) {
                snippetByRecordingKey[recordingKey(song)] = snippet
            }
        }

        val mergedSongs = mutableListOf<Song>()
        fun addOrMerge(track: Song) {
            if (!wantsCoverOrRemix && isLowQualityLyricSubstitute(track)) return
            val idx = mergedSongs.indexOfFirst { TrackMatching.isSameRecording(it, track) }
            if (idx >= 0) {
                mergedSongs[idx] = TrackMatching.mergeTracks(mergedSongs[idx], track)
            } else {
                mergedSongs.add(track)
            }
        }

        // Interleave top results from US iTunes (global lyric index), IN iTunes (regional lyric index),
        // LRCLIB direct matches, and JioSaavn so partial title matches on one provider cannot starve
        // full-text lyric hits from another provider.
        val maxLen = maxOf(itunesUs.size, itunesIn.size, saavnCandidates.size, lrclibMatches.size)
        for (i in 0 until maxLen) {
            itunesUs.getOrNull(i)?.let(::addOrMerge)
            itunesIn.getOrNull(i)?.let(::addOrMerge)
            lrclibMatches.getOrNull(i)?.first?.let(::addOrMerge)
            saavnCandidates.getOrNull(i)?.let(::addOrMerge)
        }

        // Verify & extract matching lyric snippets from LRCLIB concurrently for top 12 candidates
        val topCandidates = mergedSongs.take(12)
        val verifiedSnippets = coroutineScope {
            topCandidates.map { song ->
                async {
                    val key = recordingKey(song)
                    val existing = snippetByRecordingKey[key]
                    if (existing != null) return@async key to existing

                    val lyrics = withTimeoutOrNull(2_500L) {
                        OnlineMusicService.fetchLyrics(song)
                    }
                    val snippet = lyrics?.let { lyr ->
                        val fullText = lyr.lines.joinToString("\n") { it.text }
                            .ifBlank { lyr.plainLyrics.orEmpty() }
                        extractMatchingLyricSnippet(fullText, cleanPhrase)
                    }
                    key to snippet
                }
            }.awaitAll()
        }.toMap()

        val initialOrder = mergedSongs.mapIndexed { idx, song -> song to idx }.toMap()
        mergedSongs
            .map { song ->
                val snippet = snippetByRecordingKey[recordingKey(song)]
                    ?: verifiedSnippets[recordingKey(song)]
                LyricSearchMatch(song = song, matchedSnippet = snippet)
            }
            .sortedByDescending { match ->
                var score = 0
                if (!match.matchedSnippet.isNullOrBlank()) score += 200
                if (match.song.isPlayable || match.song.sourceIds.saavn != null) score += 25
                if (!isLowQualityLyricSubstitute(match.song)) score += 20
                score * 100 - (initialOrder[match.song] ?: 0)
            }
    }

    private suspend fun fetchLrclibDirectMatches(phrase: String): List<Pair<Song, String?>> {
        val url = "https://lrclib.net/api/search?q=${URLEncoder.encode(phrase, "UTF-8")}"
        val body = OnlineMusicService.executeGet(url) ?: return emptyList()
        return parseLrclibSearchResults(body, phrase)
    }

    internal fun parseLrclibSearchResults(jsonText: String, queryPhrase: String): List<Pair<Song, String?>> = runCatching {
        val arr = JSONArray(jsonText)
        buildList {
            for (i in 0 until minOf(arr.length(), 15)) {
                val item = arr.optJSONObject(i) ?: continue
                val id = item.optLong("id", i.toLong()).toString()
                val title = item.optString("trackName", "").trim()
                val artist = item.optString("artistName", "").trim()
                if (title.isBlank() || artist.isBlank()) continue
                val album = item.optString("albumName", "").trim()
                val durationSec = item.optDouble("duration", 180.0).toLong().coerceAtLeast(1L)
                val synced = item.optString("syncedLyrics", "")
                val plain = item.optString("plainLyrics", "")
                val textBody = if (synced.isNotBlank()) {
                    Lyrics.parseLrc(synced).joinToString("\n") { it.text }.ifBlank { plain }
                } else {
                    plain
                }
                val snippet = extractMatchingLyricSnippet(textBody, queryPhrase)
                val song = Song(
                    id = "lrclib_$id",
                    internalId = "lrclib_${TrackMatching.normalizeTitle(title)}_${TrackMatching.normalizeArtist(artist)}",
                    title = title,
                    artist = artist,
                    artists = TrackMatching.splitArtists(artist),
                    album = album,
                    durationSec = durationSec,
                    durationMs = durationSec * 1000L,
                    isPlayable = false
                )
                add(song to snippet)
            }
        }
    }.getOrDefault(emptyList())

    /**
     * Finds the line (or 2-line couplet) inside [lyricsText] that best matches [queryPhrase].
     * Returns null when the lyrics do not match at least half of the non-trivial search words.
     */
    internal fun extractMatchingLyricSnippet(lyricsText: String, queryPhrase: String): String? {
        if (lyricsText.isBlank() || queryPhrase.isBlank()) return null
        val normQuery = normalizeForLyricMatch(queryPhrase)
        val queryTokens = normQuery.split(" ").filter { it.length >= 2 }
        if (normQuery.isBlank() || queryTokens.isEmpty()) return null

        val lines = lyricsText.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (lines.isEmpty()) return null

        // Pass 1: exact normalized phrase match on a single line
        for (line in lines) {
            if (normalizeForLyricMatch(line).contains(normQuery)) {
                return line
            }
        }

        // Pass 2: exact normalized phrase match across two consecutive lines
        for (i in 0 until lines.lastIndex) {
            val pairNorm = normalizeForLyricMatch("${lines[i]} ${lines[i + 1]}")
            if (pairNorm.contains(normQuery)) {
                return "${lines[i]} / ${lines[i + 1]}"
            }
        }

        // Pass 3: keyword overlap fallback, preferring single lines before pairs on ties
        var bestScore = 0
        var bestLine: String? = null
        val minRequiredHits = if (queryTokens.size <= 2) queryTokens.size else maxOf(2, (queryTokens.size + 1) / 2)

        for (line in lines) {
            val singleNormTokens = normalizeForLyricMatch(line).split(" ").toSet()
            val singleHits = queryTokens.count { it in singleNormTokens }
            if (singleHits >= minRequiredHits && singleHits > bestScore) {
                bestScore = singleHits
                bestLine = line
            }
        }

        for (i in 0 until lines.lastIndex) {
            val combinedNormTokens = normalizeForLyricMatch("${lines[i]} ${lines[i + 1]}").split(" ").toSet()
            val pairHits = queryTokens.count { it in combinedNormTokens }
            if (pairHits >= minRequiredHits && pairHits > bestScore) {
                bestScore = pairHits
                bestLine = "${lines[i]} / ${lines[i + 1]}"
            }
        }
        return bestLine
    }

    private fun normalizeForLyricMatch(text: String): String =
        text.lowercase()
            .replace("'", "")
            .replace("’", "")
            .replace(Regex("""[^\p{L}\p{N}\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun isLowQualityLyricSubstitute(song: Song): Boolean {
        val lowerArtist = song.artist.lowercase()
        val lowerTitle = song.title.lowercase()
        return TrackMatching.isCover(song.title, song.artist) ||
            TrackMatching.isKaraoke(song.title) ||
            lowerArtist.contains("kidz bop") ||
            lowerArtist.contains("lullaby") ||
            lowerTitle.contains("8-bit") ||
            lowerTitle.contains("ringtone")
    }

    private fun recordingKey(song: Song): String =
        "${TrackMatching.normalizeTitle(song.title)}|${TrackMatching.normalizeArtist(song.artist)}"

    private fun decodeHtmlEntities(text: String): String =
        text.replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&#039;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .trim()

    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
