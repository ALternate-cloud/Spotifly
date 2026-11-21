package com.example.spotifly.data.catalog

import com.example.spotifly.model.Song
import com.example.spotifly.model.SourceIds
import kotlin.math.abs

object TrackMatching {

    private val PARENTHETICAL_REGEX = Regex("""\s*[\(\[\{].*?[\)\]\}]""")
    private val FEAT_REGEX = Regex("""\b(feat|ft|featuring)\.?\s+.*""", RegexOption.IGNORE_CASE)
    private val REMASTER_REGEX = Regex("""\b(remastered|remaster|deluxe|bonus\s+track|expanded|anniversary)\b""", RegexOption.IGNORE_CASE)
    private val PUNCTUATION_REGEX = Regex("""[^\p{L}\p{N}\s]""")
    private val WHITESPACE_REGEX = Regex("""\s+""")

    /**
     * Normalise title for matching across Deezer, iTunes, and JioSaavn.
     * Lowercase, strip "(Remastered)", "[feat. ...]", "feat./ft.", brackets, punctuation and extra spaces.
     */
    fun normalizeTitle(title: String): String {
        if (title.isBlank()) return ""
        var cleaned = title.lowercase()
        // Remove remaster tags inside parentheses or standalone
        cleaned = REMASTER_REGEX.replace(cleaned, "")
        // Remove feat tags
        cleaned = FEAT_REGEX.replace(cleaned, "")
        // Remove bracketed expressions (feat, remaster, audio info, etc.)
        cleaned = PARENTHETICAL_REGEX.replace(cleaned, "")
        // Remove punctuation
        cleaned = PUNCTUATION_REGEX.replace(cleaned, " ")
        // Normalize whitespace
        return WHITESPACE_REGEX.replace(cleaned, " ").trim()
    }

    /**
     * Normalise artist for matching. Returns primary artist name in lowercase without punctuation.
     */
    fun normalizeArtist(artist: String): String {
        if (artist.isBlank()) return ""
        val primary = primaryArtist(artist)
        val cleaned = PUNCTUATION_REGEX.replace(primary.lowercase(), " ")
        return WHITESPACE_REGEX.replace(cleaned, " ").trim()
    }

    fun primaryArtist(artist: String): String {
        if (artist.isBlank()) return ""
        val parts = artist.split(Regex("[,&/]|\\bft\\.?\\b|\\bfeat\\.?\\b|\\bfeaturing\\b", RegexOption.IGNORE_CASE))
        return parts.firstOrNull()?.trim().orEmpty()
    }

    fun splitArtists(artist: String): List<String> {
        if (artist.isBlank()) return emptyList()
        return artist.split(Regex("[,&/]|\\bft\\.?\\b|\\bfeat\\.?\\b|\\bfeaturing\\b", RegexOption.IGNORE_CASE))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    /**
     * Version flags: detect if a title/artist indicates remix, live, sped-up, karaoke, or cover.
     */
    fun isRemix(title: String): Boolean =
        title.contains(Regex("""\b(remix|mix|club mix|dance mix|extended mix|dub)\b""", RegexOption.IGNORE_CASE))

    fun isLive(title: String): Boolean =
        Regex("""\blive\b""", RegexOption.IGNORE_CASE).containsMatchIn(title) || title.contains("in concert", ignoreCase = true)

    fun isSpedUp(title: String): Boolean =
        title.contains("sped up", ignoreCase = true) ||
            title.contains("speed up", ignoreCase = true) ||
            title.contains("nightcore", ignoreCase = true)

    fun isKaraoke(title: String): Boolean =
        title.contains("karaoke", ignoreCase = true) ||
            title.contains("instrumental", ignoreCase = true) ||
            title.contains("backing track", ignoreCase = true)

    fun isCover(title: String, artist: String): Boolean =
        Regex("""\bcover\b""", RegexOption.IGNORE_CASE).containsMatchIn(title) ||
            title.contains("tribute", ignoreCase = true) ||
            artist.contains("tribute", ignoreCase = true)

    /**
     * Checks if candidate version attributes match the requested song's version expectations.
     * Reject covers, live, remix, sped-up or karaoke versions unless the requested track itself is one of those.
     */
    fun isVersionCompatible(requested: Song, candidateTitle: String, candidateArtist: String): Boolean {
        val reqRemix = isRemix(requested.title)
        val candRemix = isRemix(candidateTitle)
        if (candRemix != reqRemix) return false

        val reqLive = isLive(requested.title)
        val candLive = isLive(candidateTitle)
        if (candLive != reqLive) return false

        val reqSpedUp = isSpedUp(requested.title)
        val candSpedUp = isSpedUp(candidateTitle)
        if (candSpedUp != reqSpedUp) return false

        val reqKaraoke = isKaraoke(requested.title)
        val candKaraoke = isKaraoke(candidateTitle)
        if (candKaraoke != reqKaraoke) return false

        val reqCover = isCover(requested.title, requested.artist)
        val candCover = isCover(candidateTitle, candidateArtist)
        if (candCover && !reqCover) return false

        return true
    }

    /**
     * Deduplication predicate:
     * Prefer ISRC match when available on both; otherwise title + primary artist + duration within ±3 seconds.
     */
    fun isSameRecording(t1: Song, t2: Song): Boolean {
        if (!t1.isrc.isNullOrBlank() && !t2.isrc.isNullOrBlank()) {
            if (t1.isrc.equals(t2.isrc, ignoreCase = true)) return true
        }

        val normTitle1 = normalizeTitle(t1.title)
        val normTitle2 = normalizeTitle(t2.title)
        if (normTitle1.isEmpty() || normTitle2.isEmpty()) return false
        if (normTitle1 != normTitle2) return false

        val normArtist1 = normalizeArtist(t1.artist)
        val normArtist2 = normalizeArtist(t2.artist)
        if (normArtist1.isNotEmpty() && normArtist2.isNotEmpty()) {
            val artistMatch = normArtist1 == normArtist2 ||
                normArtist1.contains(normArtist2) ||
                normArtist2.contains(normArtist1)
            if (!artistMatch) return false
        }

        val d1 = if (t1.durationSec > 0) t1.durationSec else t1.durationMs / 1000L
        val d2 = if (t2.durationSec > 0) t2.durationSec else t2.durationMs / 1000L
        if (d1 > 0 && d2 > 0) {
            val diff = abs(d1 - d2)
            if (diff > 3) return false
        }

        // Must have compatible version types (e.g. don't merge a live recording with the studio version)
        return isVersionCompatible(t1, t2.title, t2.artist)
    }

    /**
     * Merge metadata from two matching records into one unified Track model.
     */
    fun mergeTracks(existing: Song, incoming: Song): Song {
        val mergedSourceIds = SourceIds(
            deezer = existing.sourceIds.deezer ?: incoming.sourceIds.deezer,
            itunes = existing.sourceIds.itunes ?: incoming.sourceIds.itunes,
            saavn = existing.sourceIds.saavn ?: incoming.sourceIds.saavn,
            ytmusic = existing.sourceIds.ytmusic ?: incoming.sourceIds.ytmusic
        )

        val mergedArtists = if (existing.artists.isNotEmpty()) {
            existing.artists
        } else if (incoming.artists.isNotEmpty()) {
            incoming.artists
        } else {
            splitArtists(existing.artist.ifBlank { incoming.artist })
        }

        val bestArtwork = existing.artworkUrl?.takeIf { it.isNotBlank() }
            ?: incoming.artworkUrl?.takeIf { it.isNotBlank() }

        val bestDurationMs = if (existing.durationMs > 0) existing.durationMs else incoming.durationMs
        val bestDurationSec = if (existing.durationSec > 0) existing.durationSec else incoming.durationSec

        val streamUrl = existing.streamUrl ?: incoming.streamUrl
        val isPlayable = existing.isPlayable || incoming.isPlayable ||
            streamUrl != null || mergedSourceIds.saavn != null || mergedSourceIds.ytmusic != null

        return existing.copy(
            artists = mergedArtists,
            artworkUrl = bestArtwork,
            durationMs = bestDurationMs,
            durationSec = bestDurationSec,
            isrc = existing.isrc ?: incoming.isrc,
            album = existing.album.ifBlank { incoming.album },
            year = if (existing.year > 0) existing.year else incoming.year,
            genre = existing.genre ?: incoming.genre,
            language = existing.language ?: incoming.language,
            sourceIds = mergedSourceIds,
            streamUrl = streamUrl,
            isPlayable = isPlayable
        )
    }

    /**
     * Scores a candidate against a requested track:
     * - Returns 0 if incompatible version (cover/live/remix mismatch)
     * - Duration difference: <=3s = 35 points, <=5s = 20 points, >15s = 0 points
     * - Title similarity: up to 35 points
     * - Artist match: up to 30 points
     */
    fun scoreCandidate(
        requested: Song,
        candidateTitle: String,
        candidateArtist: String,
        candidateDurationSec: Long
    ): Int {
        if (!isVersionCompatible(requested, candidateTitle, candidateArtist)) {
            return 0
        }

        var score = 0

        // 1. Duration check
        val reqDuration = if (requested.durationSec > 0) requested.durationSec else requested.durationMs / 1000L
        if (reqDuration > 0 && candidateDurationSec > 0) {
            val diff = abs(reqDuration - candidateDurationSec)
            when {
                diff <= 3 -> score += 35
                diff <= 5 -> score += 20
                diff <= 10 -> score += 10
                diff > 15 -> return 0 // Too far off
            }
        } else {
            score += 15 // Unknown duration
        }

        // 2. Title match
        val normReqTitle = normalizeTitle(requested.title)
        val normCandTitle = normalizeTitle(candidateTitle)
        if (normReqTitle == normCandTitle) {
            score += 35
        } else if (normReqTitle.contains(normCandTitle) || normCandTitle.contains(normReqTitle)) {
            score += 25
        } else {
            val sim = tokenSimilarity(normReqTitle, normCandTitle)
            if (sim < 0.5f) return 0
            score += (sim * 35).toInt()
        }

        // 3. Artist match
        val normReqArtist = normalizeArtist(requested.artist)
        val normCandArtist = normalizeArtist(candidateArtist)
        if (normReqArtist.isNotEmpty() && normCandArtist.isNotEmpty()) {
            if (normReqArtist == normCandArtist) {
                score += 30
            } else if (normReqArtist.contains(normCandArtist) || normCandArtist.contains(normReqArtist)) {
                score += 22
            } else {
                val sim = tokenSimilarity(normReqArtist, normCandArtist)
                if (sim < 0.5f) return 0
                score += (sim * 30).toInt()
            }
        } else {
            score += 10
        }

        return score
    }

    private fun tokenSimilarity(s1: String, s2: String): Float {
        val tokens1 = s1.split(" ").filter { it.length > 1 }.toSet()
        val tokens2 = s2.split(" ").filter { it.length > 1 }.toSet()
        if (tokens1.isEmpty() || tokens2.isEmpty()) return 0f
        val intersection = tokens1.intersect(tokens2).size
        val union = tokens1.union(tokens2).size
        return if (union > 0) intersection.toFloat() / union.toFloat() else 0f
    }
}
