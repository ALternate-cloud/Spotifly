package com.example.spotifly.data.podcast

import com.example.spotifly.model.PodcastEpisode
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Tolerant RSS episode parser.
 *
 * Real podcast feeds are not clean XML: they declare HTML entities in an inline DTD
 * (`&rsquo;`, `&nbsp;`), wrap titles in CDATA, embed HTML in descriptions and vary the
 * namespace prefixes. Android's streaming XML parser gives up on the first unresolved
 * entity, and one bad character would cost the whole show, so this parser works on
 * `<item>` blocks and decodes entities itself.
 */
internal object PodcastFeedParser {

    private val itemRegex = Regex(
        "<item\\b.*?</item>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val channelArtworkRegex = Regex(
        "<itunes:image[^>]*href\\s*=\\s*[\"']([^\"']+)[\"']",
        RegexOption.IGNORE_CASE
    )
    private val enclosureRegex = Regex(
        "<enclosure\\b[^>]*url\\s*=\\s*[\"']([^\"']+)[\"']",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val entityRegex = Regex("&(#x?[0-9a-fA-F]+|[a-zA-Z][a-zA-Z0-9]*);")
    private val htmlTagRegex = Regex("</?[a-zA-Z][^>]*>")
    private val whitespaceRegex = Regex("\\s+")
    private val punctuationSpaceRegex = Regex("\\s+([,.;:!?])")

    fun parse(
        xml: String,
        showId: String,
        showTitle: String,
        fallbackArtwork: String? = null,
        limit: Int = 300
    ): List<PodcastEpisode> {
        if (xml.isBlank()) return emptyList()
        val channelArtwork = channelArtworkRegex.find(xml)?.groupValues?.get(1)
            ?.takeIf { it.isNotBlank() }
            ?: fallbackArtwork

        return itemRegex.findAll(xml)
            .take(limit)
            .mapNotNull { match ->
                parseItem(match.value, showId, showTitle, channelArtwork)
            }
            .toList()
    }

    private fun parseItem(
        block: String,
        showId: String,
        showTitle: String,
        artwork: String?
    ): PodcastEpisode? {
        val audioUrl = enclosureRegex.find(block)?.groupValues?.get(1)?.trim()
        if (audioUrl.isNullOrBlank()) return null
        val title = tagText(block, "title")?.takeIf { it.isNotBlank() } ?: return null

        val description = tagText(block, "content:encoded")
            ?.takeIf { it.isNotBlank() }
            ?: tagText(block, "description").orEmpty()

        val durationMs = parseDuration(
            tagText(block, "itunes:duration") ?: tagText(block, "duration").orEmpty()
        )
        val publishedAt = parseDate(tagText(block, "pubDate").orEmpty())
        val guid = tagText(block, "guid").orEmpty().ifBlank { audioUrl }

        return PodcastEpisode(
            id = hashId(guid),
            showId = showId,
            showTitle = showTitle,
            title = title,
            description = description,
            audioUrl = audioUrl,
            artworkUrl = artwork,
            durationMs = durationMs,
            publishedAt = publishedAt
        )
    }

    /** Reads a tag's text, unwrapping CDATA and stripping inline HTML. */
    fun tagText(block: String, tag: String): String? {
        val pattern = Regex(
            "<$tag\\b[^>]*>(.*?)</$tag>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val raw = pattern.find(block)?.groupValues?.get(1) ?: return null
        return cleanText(raw)
    }

    fun cleanText(raw: String): String {
        var text = raw.trim()
        text = text.removePrefix("<![CDATA[").removeSuffix("]]>")
        // Strip literal HTML, decode entities, then strip any tags that were entity-encoded.
        // The tag pattern needs a letter after "<" so prose like "5 < 6 > 4" survives.
        text = htmlTagRegex.replace(text, " ")
        text = decodeEntities(text)
        text = htmlTagRegex.replace(text, " ")
        text = whitespaceRegex.replace(text, " ").trim()
        // Tag removal leaves a space before punctuation ("really ."); close it up.
        text = punctuationSpaceRegex.replace(text, "$1")
        return if (text.length > 600) text.take(600) + "…" else text
    }

    /** Decodes the named/numeric entities that podcast feeds actually use. */
    fun decodeEntities(raw: String): String = entityRegex.replace(raw) { match ->
        val token = match.groupValues[1]
        when {
            token.startsWith("#x", ignoreCase = true) ->
                token.drop(2).toIntOrNull(16)?.let(::codePointToString) ?: ""

            token.startsWith("#") ->
                token.drop(1).toIntOrNull()?.let(::codePointToString) ?: ""

            else -> namedEntities[token.lowercase()] ?: ""
        }
    }

    private fun codePointToString(codePoint: Int): String =
        if (codePoint in 1..0x10FFFF) String(Character.toChars(codePoint)) else ""

    fun parseDuration(raw: String): Long {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return 0L
        trimmed.toLongOrNull()?.let { return it * 1000 }
        val parts = trimmed.split(":").mapNotNull { it.trim().toLongOrNull() }
        return when (parts.size) {
            3 -> (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000
            2 -> (parts[0] * 60 + parts[1]) * 1000
            1 -> parts[0] * 1000
            else -> 0L
        }
    }

    fun parseDate(raw: String): Long {
        if (raw.isBlank()) return 0L
        val patterns = listOf(
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            "EEE, d MMM yyyy HH:mm:ss Z",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd HH:mm:ss"
        )
        for (pattern in patterns) {
            runCatching {
                val format = SimpleDateFormat(pattern, Locale.US)
                format.parse(raw)?.let { return it.time }
            }
        }
        return 0L
    }

    private fun hashId(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.take(12).joinToString("") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }
    }

    private val namedEntities = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
        "nbsp" to " ", "ensp" to " ", "emsp" to " ", "thinsp" to " ",
        "rsquo" to "’", "lsquo" to "‘", "rdquo" to "”", "ldquo" to "“",
        "sbquo" to "‚", "bdquo" to "„", "hellip" to "…", "middot" to "·",
        "mdash" to "—", "ndash" to "–", "hyphen" to "‐", "minus" to "−",
        "copy" to "©", "reg" to "®", "trade" to "™", "deg" to "°",
        "euro" to "€", "pound" to "£", "yen" to "¥", "cent" to "¢",
        "eacute" to "é", "egrave" to "è", "agrave" to "à", "ccedil" to "ç",
        "uuml" to "ü", "ouml" to "ö", "auml" to "ä", "szlig" to "ß",
        "ntilde" to "ñ", "oacute" to "ó", "iacute" to "í", "uacute" to "ú",
        "times" to "×", "divide" to "÷", "plusmn" to "±", "frac12" to "½",
        "laquo" to "«", "raquo" to "»", "bull" to "•", "dagger" to "†",
        "larr" to "←", "rarr" to "→", "harr" to "↔", "infin" to "∞"
    )
}
