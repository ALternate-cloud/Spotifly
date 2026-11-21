package com.example.spotifly.data.autoplay

import android.content.Context
import com.example.spotifly.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Persists vibe vectors measured from decoded audio.
 *
 * Analysis costs a few seconds of CPU per track, so results survive restarts
 * and are keyed by song id. A stored entry also records the duration it was
 * measured with: if the same id later points at a different recording the
 * stale vector is ignored instead of silently mis-ranking it.
 */
class VibeProfileStore private constructor(context: Context) {

    @Serializable
    private data class Entry(
        val vector: VibeVector,
        val durationMs: Long,
        val updatedAt: Long
    )

    private val file = File(context.filesDir, "vibe_profiles.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val cache = HashMap<String, Entry>()

    @Volatile
    private var loaded = false

    fun get(song: Song): VibeVector? {
        val entry = synchronized(this) {
            ensureLoaded()
            cache[song.id]
        } ?: return null
        // A large duration change means the id now describes a different edit.
        if (entry.durationMs > 0 && song.durationMs > 0 &&
            kotlin.math.abs(entry.durationMs - song.durationMs) > 15_000L
        ) {
            return null
        }
        return entry.vector
    }

    fun put(song: Song, vector: VibeVector) {
        synchronized(this) {
            ensureLoaded()
            cache[song.id] = Entry(vector, song.durationMs, System.currentTimeMillis())
        }
        schedulePersist()
    }

    fun clear() {
        synchronized(this) {
            cache.clear()
            loaded = true
        }
        schedulePersist()
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        runCatching {
            if (!file.exists()) return
            val stored = json.decodeFromString<Map<String, Entry>>(file.readText())
            cache.putAll(stored)
        }
    }

    private fun schedulePersist() {
        scope.launch {
            val snapshot = synchronized(this@VibeProfileStore) { HashMap(cache) }
            runCatching {
                val temp = File(file.parentFile, "${file.name}.tmp")
                temp.writeText(json.encodeToString(snapshot))
                if (!temp.renameTo(file)) {
                    file.writeText(temp.readText())
                    temp.delete()
                }
            }
        }
    }

    companion object {
        @Volatile
        private var instance: VibeProfileStore? = null

        fun getInstance(context: Context): VibeProfileStore =
            instance ?: synchronized(this) {
                instance ?: VibeProfileStore(context.applicationContext).also { instance = it }
            }
    }
}
