package com.example.spotifly.data.autoplay

import com.example.spotifly.model.Song
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow

/** Where a candidate came from; catalogue recommendations get a small prior. */
enum class CandidateSource { RECOMMENDED, VIBE, ARTIST, DOWNLOADED, LIBRARY }

data class AutoplayCandidate(
    val song: Song,
    val source: CandidateSource = CandidateSource.VIBE,
    val sourceRank: Int = -1
)

/** Why the ranker chose a track, shown in diagnostics and tests. */
enum class PickReason {
    /** Strong match on the audio feature vectors with a different artist. */
    VIBE,

    /** Acceptable audio match inside the same language or genre scene. */
    SAME_SCENE,

    /** Nothing from the neighbourhood was left; stay with the same artist. */
    SAME_ARTIST,

    /** Only non-recent tracks remained. */
    LAST_RESORT
}

data class AutoplayPick(
    val song: Song,
    val reason: PickReason,
    val score: Double,
    val similarity: Double
)

/**
 * Ranks candidate tracks against the current song plus a short rolling history.
 *
 * Rules, in priority order:
 * 1. never repeat a recently played id, the same recording under another id,
 *    or any other version of a song name already played or queued
 * 2. prefer a different artist (no back-to-back artist repeats)
 * 3. prefer a strong audio-similarity match (cosine on feature vectors), then
 *    language/genre neighbours
 * 4. fall back rather than stopping: same artist, then any non-recent track
 */
object VibeRanker {

    private const val RECENT_HISTORY = 10
    private const val HISTORY_BLEND_COUNT = 5

    /** Current song dominates, recent history keeps the set from drifting. */
    private const val CURRENT_WEIGHT = 0.62
    private const val HISTORY_WEIGHT = 0.30
    private const val HISTORY_DECAY = 0.55

    /**
     * Tier thresholds on the cosine scale (0.5 = no signal, ~1 = same feel).
     * They sit high because measured vectors separate cleanly: same-vibe pairs
     * score roughly 0.80+ while unrelated pairs land near 0.5.
     */
    private const val STRONG_SIMILARITY = 0.78
    private const val SCENE_SIMILARITY = 0.60
    private const val STRONG_LANGUAGE = 0.45
    private const val SCENE_LANGUAGE = 0.75
    private const val SCENE_GENRE = 0.70
    private const val MIN_PLAYABLE_MS = 30_000L

    fun pick(
        currentSong: Song,
        history: List<Song>,
        candidates: List<AutoplayCandidate>,
        excludeIds: Set<String> = emptySet(),
        excludeTitles: Set<String> = emptySet(),
        measured: (Song) -> VibeVector? = { null }
    ): AutoplayPick? {
        val currentProfile = VibeFeatures.profileFor(currentSong, measured(currentSong))
        val recentSongs = history
            .filter { it.id != currentSong.id }
            .distinctBy { it.id }
            .take(RECENT_HISTORY)
        val recentProfiles = recentSongs.map { VibeFeatures.profileFor(it, measured(it)) }
        val target = targetProfile(currentProfile, recentProfiles.take(HISTORY_BLEND_COUNT))

        val blockedIds = excludeIds + currentSong.id + recentSongs.map { it.id }
        val blockedKeys = (listOf(currentSong) + recentSongs)
            .map { VibeFeatures.dedupeKey(it) }
            .toSet()
        // The user-facing rule: never queue another version of a song name that
        // the listener has around them now (seed, history, and staged queue).
        val blockedTitles = excludeTitles +
            (listOf(currentSong) + recentSongs).map { VibeFeatures.titleKey(it) }

        val scored = candidates.asSequence()
            .filter { it.song.id.isNotBlank() && it.song.id !in blockedIds }
            .filter { VibeFeatures.dedupeKey(it.song) !in blockedKeys }
            .filter { VibeFeatures.titleKey(it.song) !in blockedTitles }
            .filter { isPlayable(it.song) }
            .distinctBy { VibeFeatures.dedupeKey(it.song) }
            .map { candidate ->
                val profile = VibeFeatures.profileFor(candidate.song, measured(candidate.song))
                Scored(
                    candidate = candidate,
                    profile = profile,
                    similarity = target.vector.similarityTo(profile.vector),
                    languageAffinity = VibeFeatures.languageAffinity(target.language, profile.language),
                    genreAffinity = VibeFeatures.genreAffinity(target.genre, profile.genre),
                    score = score(
                        target = target,
                        candidate = candidate,
                        profile = profile,
                        recentProfiles = recentProfiles
                    )
                )
            }
            .sortedByDescending { it.score }
            .toList()

        if (scored.isEmpty()) return null

        val strong = scored.firstOrNull {
            it.isDifferentArtist(currentProfile) &&
                it.similarity >= STRONG_SIMILARITY &&
                it.languageAffinity >= STRONG_LANGUAGE
        }
        if (strong != null) return strong.toPick(PickReason.VIBE)

        val scene = scored.firstOrNull {
            it.isDifferentArtist(currentProfile) &&
                it.similarity >= SCENE_SIMILARITY &&
                (it.languageAffinity >= SCENE_LANGUAGE || it.genreAffinity >= SCENE_GENRE)
        }
        if (scene != null) return scene.toPick(PickReason.SAME_SCENE)

        val sameArtist = scored.firstOrNull { !it.isDifferentArtist(currentProfile) }
        if (sameArtist != null) return sameArtist.toPick(PickReason.SAME_ARTIST)

        return scored.first().toPick(PickReason.LAST_RESORT)
    }

    /**
     * Blends the current track with the last few plays. Recent songs weigh more
     * than older ones, so a single outlier cannot drag the whole session away.
     */
    fun targetProfile(current: VibeProfile, recent: List<VibeProfile>): VibeProfile {
        val considered = recent.take(HISTORY_BLEND_COUNT)
        // CURRENT_WEIGHT and HISTORY_WEIGHT are shares of a 0.92 total (the small
        // remainder is deliberate slack), so normalize the decayed history to
        // exactly HISTORY_WEIGHT before blending.
        val decayTotal = considered.indices.sumOf { HISTORY_DECAY.pow(it.toDouble()) }
        val historyScale = if (decayTotal > 0.0) HISTORY_WEIGHT / decayTotal else 0.0

        var vector = current.vector
        var totalWeight = CURRENT_WEIGHT
        considered.forEachIndexed { index, profile ->
            val weight = historyScale * HISTORY_DECAY.pow(index.toDouble())
            if (weight <= 0.0) return@forEachIndexed
            vector = vector.blendedWith(profile.vector, weight / (totalWeight + weight))
            totalWeight += weight
        }

        return current.copy(vector = vector)
    }

    /**
     * Audio cosine is the foundation of the score; language, genre, catalogue
     * order, era and artist variety can only nudge it. Title/artist text never
     * lifts a candidate onto electronics it does not have.
     */
    private fun score(
        target: VibeProfile,
        candidate: AutoplayCandidate,
        profile: VibeProfile,
        recentProfiles: List<VibeProfile>
    ): Double {
        val similarity = target.vector.similarityTo(profile.vector)
        val language = VibeFeatures.languageAffinity(target.language, profile.language)
        val genre = VibeFeatures.genreAffinity(target.genre, profile.genre)
        val recoPrior = when {
            candidate.source != CandidateSource.RECOMMENDED || candidate.sourceRank < 0 -> 0.0
            else -> (1.0 - candidate.sourceRank / 25.0).coerceIn(0.0, 1.0)
        }
        val era = eraAffinity(target.year, profile.year)
        val artistRepeats = recentProfiles.count { it.isSameArtistAs(profile) }

        return similarity +
            language * 0.35 +
            genre * 0.15 +
            recoPrior * 0.15 +
            era * 0.05 -
            artistRepeats * 0.15
    }

    private fun eraAffinity(first: Int, second: Int): Double {
        if (first <= 0 || second <= 0) return 0.0
        return (1.0 - min(abs(first - second) / 30.0, 1.0)).coerceIn(0.0, 1.0)
    }

    private fun isPlayable(song: Song): Boolean {
        if (song.isPodcast) return false
        if (song.durationMs in 1 until MIN_PLAYABLE_MS) return false
        return !song.streamUrl.isNullOrBlank() ||
            !song.localFilePath.isNullOrBlank() ||
            !song.contentUriString.isNullOrBlank() ||
            !song.sourceIds.saavn.isNullOrBlank() ||
            !song.sourceIds.ytmusic.isNullOrBlank() ||
            !song.sourceIds.deezer.isNullOrBlank() ||
            !song.sourceIds.itunes.isNullOrBlank()
    }

    private fun VibeProfile.isSameArtistAs(other: VibeProfile): Boolean =
        artistKey.isNotEmpty() && other.artistKey.isNotEmpty() && artistKey == other.artistKey

    private data class Scored(
        val candidate: AutoplayCandidate,
        val profile: VibeProfile,
        val similarity: Double,
        val languageAffinity: Double,
        val genreAffinity: Double,
        val score: Double
    ) {
        fun isDifferentArtist(current: VibeProfile): Boolean =
            !(profile.artistKey.isNotEmpty() &&
                current.artistKey.isNotEmpty() &&
                profile.artistKey == current.artistKey)

        fun toPick(reason: PickReason) = AutoplayPick(
            song = candidate.song,
            reason = reason,
            score = score,
            similarity = similarity
        )
    }
}
