package com.example.spotifly.data.catalog

import com.example.spotifly.model.Song

/**
 * Builds the autocomplete list shown under the search field. Everything here is
 * local knowledge: past searches plus artists and titles already in the library.
 * Online results are deliberately excluded — those arrive after the debounce and
 * would make the dropdown jump while typing.
 */
object SearchSuggestions {

    fun build(
        query: String,
        localSongs: List<Song>,
        recentSearches: List<String>,
        limit: Int = 6
    ): List<String> {
        val typed = query.trim()
        if (typed.isEmpty()) return emptyList()

        val seen = LinkedHashMap<String, String>()

        fun add(candidate: String) {
            val value = candidate.trim()
            if (value.isEmpty() || value.equals(typed, ignoreCase = true)) return
            if (!value.contains(typed, ignoreCase = true)) return
            seen.putIfAbsent(value.lowercase(), value)
        }

        recentSearches.forEach(::add)
        localSongs.asSequence()
            .map { it.artist }
            .filter { it.isNotBlank() }
            .distinct()
            .take(300)
            .forEach(::add)
        localSongs.asSequence()
            .map { it.title }
            .filter { it.isNotBlank() }
            .take(300)
            .forEach(::add)

        return seen.values.take(limit)
    }
}
