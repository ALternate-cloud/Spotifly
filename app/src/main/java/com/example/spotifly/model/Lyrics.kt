package com.example.spotifly.model

import kotlinx.serialization.Serializable
import java.util.regex.Pattern

@Serializable
data class LyricLine(
    val timeMs: Long,
    val text: String
)

@Serializable
data class Lyrics(
    val trackName: String,
    val artistName: String,
    val lines: List<LyricLine> = emptyList(),
    val plainLyrics: String? = null,
    val isSynced: Boolean = false
) {
    fun getActiveLineIndex(currentPositionMs: Long): Int {
        if (!isSynced || lines.isEmpty()) return -1
        var activeIndex = -1
        for (i in lines.indices) {
            if (lines[i].timeMs <= currentPositionMs) {
                activeIndex = i
            } else {
                break
            }
        }
        return activeIndex
    }

    companion object {
        private val lrcPattern = Pattern.compile("""\[(\d{2}):(\d{2})\.(\d{2,3})\](.*)""")

        /** Parses standard LRC content ([mm:ss.xx] lyric text) into sorted timed lines. */
        fun parseLrc(lrcContent: String): List<LyricLine> {
            val result = mutableListOf<LyricLine>()
            lrcContent.lineSequence().forEach { line ->
                val matcher = lrcPattern.matcher(line.trim())
                if (matcher.matches()) {
                    val min = matcher.group(1)?.toLongOrNull() ?: 0L
                    val sec = matcher.group(2)?.toLongOrNull() ?: 0L
                    val fracStr = matcher.group(3) ?: "0"
                    val ms = if (fracStr.length == 2) {
                        (fracStr.toLongOrNull() ?: 0L) * 10
                    } else {
                        fracStr.toLongOrNull() ?: 0L
                    }
                    val totalMs = min * 60_000L + sec * 1_000L + ms
                    val text = matcher.group(4)?.trim().orEmpty()
                    result.add(LyricLine(timeMs = totalMs, text = text))
                }
            }
            return result.sortedBy { it.timeMs }
        }
    }
}
