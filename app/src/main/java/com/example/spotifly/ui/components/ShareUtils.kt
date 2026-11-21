package com.example.spotifly.ui.components

import android.content.Context
import android.content.Intent
import com.example.spotifly.data.stats.ListeningStats
import com.example.spotifly.model.Playlist
import com.example.spotifly.model.Song

/**
 * Android's share sheet, matching Spotify's "Share" action. Only text is shared —
 * the clone has no hosted link service, so it shares what it actually knows:
 * the track identity and where it came from.
 */
object ShareUtils {

    fun shareSong(context: Context, song: Song) {
        val body = buildString {
            append("${song.title} — ${song.artist}")
            if (song.album.isNotBlank()) append("\nAlbum: ${song.album}")
            append("\n\nShared from Spotifly")
        }
        share(context, song.title, body)
    }

    fun sharePlaylist(context: Context, playlist: Playlist) {
        val body = buildString {
            append("${playlist.name}\n")
            if (playlist.description.isNotBlank()) append("${playlist.description}\n")
            append("${playlist.songs.size} tracks\n\n")
            playlist.songs.take(10).forEachIndexed { index, song ->
                append("${index + 1}. ${song.title} — ${song.artist}\n")
            }
            if (playlist.songs.size > 10) append("…and ${playlist.songs.size - 10} more\n")
            append("\nShared from Spotifly")
        }
        share(context, playlist.name, body)
    }

    /** Shares one lyric line as a quote card; lyrics are text, so this stays honest. */
    fun shareLyric(context: Context, song: Song, line: String) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return
        val body = buildString {
            append("\u201C$trimmed\u201D\n")
            append("${song.artist} — ${song.title}\n\n")
            append("Shared from Spotifly")
        }
        share(context, "${song.title} — ${song.artist}", body)
    }

    /** The Wrapped card as text: numbers this device actually recorded. */
    fun shareWrapped(context: Context, stats: ListeningStats) {
        share(context, "My Spotifly listening", wrappedText(stats))
    }

    internal fun wrappedText(stats: ListeningStats): String = buildString {
        append("My Spotifly listening\n")
        append("${stats.totalMinutes} minutes · ${stats.totalPlays} plays · ${stats.uniqueArtists} artists\n")
        stats.topArtists.firstOrNull()?.let {
            append("Top artist: ${it.name} (${it.plays} plays)\n")
        }
        stats.topTracks.firstOrNull()?.let {
            append("Top track: ${it.title} — ${it.artist}\n")
        }
        append("Last 7 days: ${stats.minutesLast7Days} minutes\n")
        append("\nShared from Spotifly")
    }

    private fun share(context: Context, subject: String, body: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        val chooser = Intent.createChooser(send, "Share").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(chooser) }
    }
}
