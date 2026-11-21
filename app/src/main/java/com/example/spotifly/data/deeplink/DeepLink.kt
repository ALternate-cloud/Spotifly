package com.example.spotifly.data.deeplink

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * What an inbound `spotifly://` link is asking the app to show. Links are the
 * clone's own scheme — no Spotify URLs or catalogue identifiers are involved.
 */
sealed interface DeepLinkTarget {
    data class Track(val id: String) : DeepLinkTarget
    data class Album(val id: String) : DeepLinkTarget
    data class Artist(val id: String) : DeepLinkTarget
    data class Playlist(val id: String) : DeepLinkTarget
    data class Search(val query: String) : DeepLinkTarget
    data object Home : DeepLinkTarget
}

/**
 * Parses `spotifly://<type>/<id>` and `spotifly://search?q=...`.
 *
 * Kept free of `android.net.Uri` so it can be unit-tested on the JVM and so a
 * malformed intent can never crash the activity at startup.
 */
object DeepLinkParser {

    const val SCHEME = "spotifly"
    private const val PREFIX = "$SCHEME://"

    fun parse(raw: String?): DeepLinkTarget? {
        val value = raw?.trim().orEmpty()
        if (value.length <= PREFIX.length) return null
        if (!value.startsWith(PREFIX, ignoreCase = true)) return null

        val remainder = value.substring(PREFIX.length)
        val path = remainder.substringBefore('?')
        val query = remainder.substringAfter('?', "")
        val segments = path.split('/').filter { it.isNotBlank() }
        val type = segments.firstOrNull()?.lowercase() ?: return null

        fun idAt(index: Int): String? =
            segments.getOrNull(index)?.let(::decode)?.takeIf { it.isNotBlank() }

        return when (type) {
            "track", "song" -> idAt(1)?.let(DeepLinkTarget::Track)
            "album" -> idAt(1)?.let(DeepLinkTarget::Album)
            "artist" -> idAt(1)?.let(DeepLinkTarget::Artist)
            "playlist" -> idAt(1)?.let(DeepLinkTarget::Playlist)
            "search" -> {
                val fromQuery = queryParameters(query)["q"]
                val fromPath = idAt(1)
                (fromQuery ?: fromPath)?.takeIf { it.isNotBlank() }
                    ?.let(DeepLinkTarget::Search)
            }
            "home" -> DeepLinkTarget.Home
            else -> null
        }
    }

    private fun queryParameters(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        return raw.split('&')
            .mapNotNull { pair ->
                if (pair.isBlank()) return@mapNotNull null
                val key = pair.substringBefore('=', "").trim()
                val value = pair.substringAfter('=', "").trim()
                if (key.isEmpty()) null else key.lowercase() to decode(value)
            }
            .toMap()
    }

    private fun decode(value: String): String =
        runCatching { URLDecoder.decode(value, StandardCharsets.UTF_8.name()) }
            .getOrDefault(value)
}
