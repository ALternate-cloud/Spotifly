package com.example.spotifly.data.history

import androidx.core.content.edit
import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

class SearchHistoryManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _recentSearches = MutableStateFlow<List<String>>(emptyList())
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

    init {
        _recentSearches.value = loadRecentSearches()
    }

    private fun loadRecentSearches(): List<String> {
        val json = prefs.getString(KEY_SEARCHES, null) ?: return emptyList()
        val rawList = mutableListOf<String>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val item = array.optString(i, "").trim()
                if (item.length >= 2 && !rawList.any { it.equals(item, ignoreCase = true) }) {
                    rawList.add(item)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Clean out any partial prefix items (e.g. if 'song' exists, remove 'so', 'son')
        val cleaned = rawList.filterNot { candidate ->
            rawList.any { longer ->
                longer.length > candidate.length && longer.startsWith(candidate, ignoreCase = true)
            }
        }

        // Save sanitized list if we pruned garbage
        if (cleaned.size != rawList.size) {
            persist(cleaned)
        }

        return cleaned
    }

    private fun persist(searches: List<String>) {
        val array = JSONArray()
        searches.forEach { array.put(it) }
        prefs.edit { putString(KEY_SEARCHES, array.toString()) }
    }

    private fun saveSearches(searches: List<String>) {
        persist(searches)
        _recentSearches.value = searches
    }

    fun addSearch(query: String) {
        val clean = query.trim()
        if (clean.length < 2) return

        val current = _recentSearches.value.toMutableList()
        // Remove exact matches and any partial prefixes of this search term
        current.removeAll { existing ->
            existing.equals(clean, ignoreCase = true) ||
            (clean.length > existing.length && clean.startsWith(existing, ignoreCase = true))
        }
        current.add(0, clean) // Put at the top
        val trimmed = if (current.size > MAX_SEARCHES) current.take(MAX_SEARCHES) else current
        saveSearches(trimmed)
    }

    fun removeSearch(query: String) {
        val current = _recentSearches.value.toMutableList()
        current.removeAll { it.equals(query.trim(), ignoreCase = true) }
        saveSearches(current)
    }

    fun clearAll() {
        saveSearches(emptyList())
    }

    companion object {
        private const val PREFS_NAME = "spotifly_search_history"
        private const val KEY_SEARCHES = "recent_queries"
        private const val MAX_SEARCHES = 15

        @Volatile
        private var instance: SearchHistoryManager? = null

        fun getInstance(context: Context): SearchHistoryManager {
            return instance ?: synchronized(this) {
                instance ?: SearchHistoryManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
