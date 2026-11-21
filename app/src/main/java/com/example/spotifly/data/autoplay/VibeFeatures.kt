package com.example.spotifly.data.autoplay

import com.example.spotifly.model.Song

/**
 * Everything the autoplay ranker needs to know about one track.
 *
 * [vector] is the best guess available: measured audio features when they have
 * been analysed, otherwise a metadata profile derived from genre, language,
 * title/album words, duration and era.
 */
data class VibeProfile(
    val vector: VibeVector,
    val language: String?,
    val genre: String?,
    val artistKey: String,
    val year: Int
)

/**
 * Turns whatever metadata the catalogue exposes into a vibe profile.
 *
 * Streaming catalogues usually expose no acoustic features at all, so this is
 * the universal fallback: genre words, title/album mood words, language,
 * duration and era. It is intentionally coarse — the ranker only needs enough
 * signal to separate a slow Urdu ballad from a dance track.
 *
 * When [measured] is supplied (decoded from a local or downloaded file), it
 * dominates the estimate because it describes the actual recording.
 */
object VibeFeatures {

    private val CALM_WORDS = listOf(
        "sad", "sadness", "alone", "lonely", "broken", "breakup", "break up",
        "tears", "cry", "crying", "miss you", "missing you", "memories", "memory",
        "yaad", "yaadein", "yaade", "judaai", "judai", "tanha", "tanhai", "dard",
        "gham", "gam", "ansu", "ashq", "bewafa", "intezaar", "raat", "night",
        "slow", "slowed", "reverb", "lofi", "lo-fi", "acoustic", "unplugged",
        "instrumental", "piano", "sleep", "lullaby", "soft", "peace", "dua",
        "sufi", "ghazal", "melancholy", "heartbreak", "lost", "empty"
    )

    private val HIGH_ENERGY_WORDS = listOf(
        "remix", "club", "party", "dance", "beat", "drop", "bass", "edm",
        "house", "techno", "trap", "rap", "hip hop", "hip-hop", "workout",
        "gym", "run", "energetic", "hype", "banger", "sped up", "nightcore",
        "rock", "metal", "punk", "dhol", "bhangra", "festival", "dhamaka",
        "dj", "mashup", "power"
    )

    private val ACOUSTIC_WORDS = listOf(
        "acoustic", "unplugged", "instrumental", "piano", "guitar", "lofi",
        "lo-fi", "classical", "ghazal", "sufi", "qawwali", "flute", "violin",
        "orchestra", "sitar", "sarod", "sarangi", "tabla", "live"
    )

    private val ROMANTIC_WORDS = listOf(
        "love", "pyaar", "pyar", "ishq", "mohabbat", "mohabbat", "dil", "jaan",
        "romantic", "romance", "forever", "together", "sweetheart", "humsafar",
        "saathiya", "sanam", "jaana", "jaaneman", "wedding", "shaadi"
    )

    private val POSITIVE_WORDS = listOf(
        "happy", "sunshine", "smile", "joy", "celebration", "birthday",
        "holiday", "summer", "feel good", "disco", "celebration"
    )

    /** Genre buckets. Values are the base profile for that genre. */
    private data class GenreBucket(val name: String, val vector: VibeVector)

    private val GENRE_BUCKETS = listOf(
        GenreBucket(
            "classical",
            VibeVector(energy = 0.28, valence = 0.45, tempo = 0.28, acousticness = 0.85, danceability = 0.2)
        ),
        GenreBucket(
            "acoustic",
            VibeVector(energy = 0.34, valence = 0.42, tempo = 0.34, acousticness = 0.8, danceability = 0.28)
        ),
        GenreBucket(
            "chill",
            VibeVector(energy = 0.32, valence = 0.5, tempo = 0.3, acousticness = 0.62, danceability = 0.38)
        ),
        GenreBucket(
            "metal",
            VibeVector(energy = 0.92, valence = 0.32, tempo = 0.82, acousticness = 0.08, danceability = 0.5)
        ),
        GenreBucket(
            "electronic",
            VibeVector(energy = 0.82, valence = 0.65, tempo = 0.78, acousticness = 0.05, danceability = 0.88)
        ),
        GenreBucket(
            "hiphop",
            VibeVector(energy = 0.7, valence = 0.5, tempo = 0.55, acousticness = 0.1, danceability = 0.78)
        ),
        GenreBucket(
            "rock",
            VibeVector(energy = 0.72, valence = 0.5, tempo = 0.6, acousticness = 0.2, danceability = 0.55)
        ),
        GenreBucket(
            "pop",
            VibeVector(energy = 0.6, valence = 0.66, tempo = 0.56, acousticness = 0.22, danceability = 0.68)
        ),
        GenreBucket(
            "jazz",
            VibeVector(energy = 0.4, valence = 0.56, tempo = 0.4, acousticness = 0.62, danceability = 0.45)
        ),
        GenreBucket(
            "soundtrack",
            VibeVector(energy = 0.42, valence = 0.42, tempo = 0.4, acousticness = 0.5, danceability = 0.32)
        )
    )

    private val GENRE_KEYWORDS = mapOf(
        "classical" to listOf("classical", "ghazal", "sufi", "devotional", "qawwali", "carnatic", "hindustani"),
        "acoustic" to listOf("acoustic", "folk", "singer-songwriter", "singer songwriter", "ballad", "unplugged"),
        "chill" to listOf("lo-fi", "lofi", "ambient", "chill", "downtempo", "sleep"),
        "metal" to listOf("metal", "punk", "hardcore", "heavy"),
        "electronic" to listOf("edm", "electronic", "house", "techno", "trance", "dubstep", "dance"),
        "hiphop" to listOf("hip-hop", "hip hop", "rap", "trap", "drill", "grime"),
        "rock" to listOf("rock", "indie rock", "alternative", "grunge"),
        "pop" to listOf("pop", "bollywood", "filmi", "soundtrack", "ost", "romantic"),
        "jazz" to listOf("jazz", "blues", "soul", "swing", "bossa"),
        "soundtrack" to listOf("soundtrack", "score", "theme", "ost")
    )

    /**
     * Languages that share a listening culture. The ranker uses this to allow
     * Hindi -> Urdu (the example that motivated the feature) without treating
     * Hindi -> English as the same neighbourhood.
     */
    private val SOUTH_ASIAN_LANGUAGES = setOf(
        "hindi", "urdu", "punjabi", "bengali", "tamil", "telugu", "malayalam",
        "kannada", "marathi", "gujarati", "bhojpuri", "assamese", "odia",
        "oriya", "rajasthani", "haryanvi", "nepali", "sanskrit", "sinhala",
        "konkani", "kashmiri", "sindhi"
    )

    private val HINDUSTANI_LANGUAGES = setOf("hindi", "urdu", "punjabi")

    fun profileFor(song: Song, measured: VibeVector? = null): VibeProfile {
        val language = normaliseLanguage(song.language)
        val genre = song.genre?.trim()?.takeIf { it.isNotEmpty() }
        val text = buildString {
            append(song.title.lowercase())
            append(' ')
            append(song.album.lowercase())
            if (!genre.isNullOrBlank()) {
                append(' ')
                append(genre.lowercase())
            }
        }

        val tokens = tokenize(text)
        val bucket = genreBucket(genre, text, tokens)
        var energy = 0.5
        var valence = 0.5
        var tempo = 0.5
        var acousticness = 0.44
        var danceability = 0.45

        if (bucket != null) {
            energy = bucket.vector.energy
            valence = bucket.vector.valence
            tempo = bucket.vector.tempo
            acousticness = bucket.vector.acousticness
            danceability = bucket.vector.danceability
        }

        val calm = containsAny(text, tokens, CALM_WORDS)
        val high = containsAny(text, tokens, HIGH_ENERGY_WORDS)
        val acoustic = containsAny(text, tokens, ACOUSTIC_WORDS)
        val romantic = containsAny(text, tokens, ROMANTIC_WORDS)
        val positive = containsAny(text, tokens, POSITIVE_WORDS)

        if (calm) {
            energy -= 0.22
            valence -= 0.10
            tempo -= 0.18
            acousticness += 0.15
            danceability -= 0.15
        }
        if (high) {
            energy += 0.25
            tempo += 0.20
            danceability += 0.20
            acousticness -= 0.15
        }
        if (acoustic) {
            acousticness += 0.25
            energy -= 0.06
        }
        if (romantic) valence += 0.12
        if (positive) valence += 0.18

        // Long-form pieces (classical, ghazal, prog) are rarely dance tracks.
        if (song.durationMs >= 8 * 60_000L) {
            energy -= 0.12
            tempo -= 0.12
            acousticness += 0.08
        } else if (song.durationMs in 1..75_000L) {
            // Short cues/interludes tend to sit outside the normal energy range.
            energy -= 0.04
            danceability -= 0.04
        }

        if (song.year in 1..1985) {
            acousticness += 0.06
            tempo -= 0.04
        } else if (song.year >= 2015) {
            danceability += 0.03
        }

        val metadata = VibeVector(
            energy = energy,
            valence = valence,
            tempo = tempo,
            acousticness = acousticness,
            danceability = danceability
        ).clamped()

        val vector = if (measured != null) {
            // The user's rule: ranking is pure audio similarity. A measured
            // vector describes the actual recording, so it is used untouched;
            // metadata is only ever the fallback when nothing can be decoded.
            measured.clamped()
        } else {
            metadata
        }

        return VibeProfile(
            vector = vector,
            language = language,
            genre = genre?.lowercase(),
            artistKey = primaryArtistKey(song.artist),
            year = song.year
        )
    }

    /** 1.0 = same language, 0.0 = unrelated listening culture. */
    fun languageAffinity(first: String?, second: String?): Double {
        val a = normaliseLanguage(first)
        val b = normaliseLanguage(second)
        if (a == null || b == null) return 0.4
        if (a == b) return 1.0
        if (a == "instrumental" || b == "instrumental") return 0.55
        val pair = setOf(a, b)
        if (pair == setOf("hindi", "urdu")) return 0.92
        if (pair.all { it in HINDUSTANI_LANGUAGES }) return 0.78
        if (pair.all { it in SOUTH_ASIAN_LANGUAGES }) return 0.6
        return 0.15
    }

    /** 1.0 = same genre, partial for neighbouring genres, 0.0 = unrelated. */
    fun genreAffinity(first: String?, second: String?): Double {
        val a = normaliseGenre(first) ?: return 0.0
        val b = normaliseGenre(second) ?: return 0.0
        if (a == b) return 1.0
        // Unknown genres must not match each other just because both buckets are null.
        val aBucket = bucketName(a) ?: return 0.0
        val bBucket = bucketName(b) ?: return 0.0
        return if (aBucket == bBucket) 0.7 else 0.0
    }

    fun normaliseLanguage(raw: String?): String? {
        val value = raw?.trim()?.lowercase() ?: return null
        if (value.isEmpty() || value == "unknown") return null
        return value.substringBefore('(').substringBefore('-').trim().ifEmpty { null }
    }

    fun primaryArtistKey(raw: String): String {
        if (raw.isBlank()) return ""
        val primary = raw
            .split(",", "&", "/", ";")
            .firstOrNull()
            ?.split(Regex("\\s+(?:ft\\.?|feat\\.?|featuring|with|x)\\s+"), limit = 2)
            ?.firstOrNull()
            ?: raw
        return primary.trim().lowercase().ifEmpty { raw.trim().lowercase() }
    }

    /** Title+artist key used to spot the same recording under a different id. */
    fun dedupeKey(song: Song): String =
        "${normaliseTitle(song.title)}|${primaryArtistKey(song.artist)}"

    /**
     * Title-only identity for the "no other version of this song name" rule.
     *
     * Strips version markers ("(Stormzy Remix)", " - Reprise", "[Live]"), then
     * collapses doubled letters so "Mere Nishaan" and "Mere Nishan" share a key
     * even though catalogues spell the same song both ways. Artist-agnostic by
     * design: a cover of the same name must still never follow the original.
     */
    fun titleKey(song: Song): String {
        val key = normaliseTitle(song.title).replace(REPEATED_LETTER, "$1")
        if (key.isNotEmpty()) return key
        return song.title.trim().lowercase().replace(Regex("[^a-z0-9\\p{L}]"), "")
            .ifEmpty { "unknown" }
    }

    private val REPEATED_LETTER = Regex("([\\p{L}])\\1+")

    fun normaliseTitle(raw: String): String = raw.lowercase()
        .replace(Regex("\\(.*?\\)"), "")
        .replace(Regex("\\[.*?]"), "")
        .replace(Regex("\\s+-\\s+.*$"), "")
        .replace(Regex("[^a-z0-9\\p{L}]"), "")
        .trim()

    private fun genreBucket(genre: String?, text: String, tokens: Set<String>): GenreBucket? {
        val normalised = genre?.lowercase()
        if (normalised != null) {
            GENRE_KEYWORDS.entries.forEach { (bucket, keywords) ->
                if (keywords.any { normalised.contains(it) }) {
                    return GENRE_BUCKETS.firstOrNull { it.name == bucket }
                }
            }
        }
        // Without a genre tag, fall back to a title/album word that names one.
        GENRE_KEYWORDS.entries.forEach { (bucket, keywords) ->
            if (containsAny(text, tokens, keywords)) {
                return GENRE_BUCKETS.firstOrNull { it.name == bucket }
            }
        }
        return null
    }

    private fun normaliseGenre(raw: String?): String? =
        raw?.lowercase()?.trim()?.takeIf { it.isNotEmpty() }

    private fun bucketName(genre: String): String? {
        GENRE_KEYWORDS.entries.forEach { (bucket, keywords) ->
            if (keywords.any { genre.contains(it) }) return bucket
        }
        return null
    }

    private fun tokenize(text: String): Set<String> =
        text.split(Regex("[^a-z0-9\\p{L}]+")).filter { it.isNotEmpty() }.toSet()

    /**
     * Single words match whole tokens so "Beatles" is not classified by "beat"
     * and "Midnight" is not a "night" song. Hyphenated and multi-word cues use
     * a substring match because they survive tokenisation poorly.
     */
    private fun containsAny(text: String, tokens: Set<String>, words: List<String>): Boolean =
        words.any { word ->
            if (word.contains(' ') || word.contains('-')) text.contains(word) else word in tokens
        }
}
