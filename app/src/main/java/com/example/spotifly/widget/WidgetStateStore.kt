package com.example.spotifly.widget

import androidx.core.content.edit
import android.content.Context
import android.graphics.Bitmap
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.spotifly.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * Small persisted snapshot of what the player is doing, written by [com.example.spotifly.player.MusicController]
 * and read by the home-screen widget. The widget runs in a different process from the
 * media session, so it cannot query the player directly.
 */
object WidgetStateStore {

    private const val PREFS = "spotifly_widget"
    private const val KEY_TITLE = "title"
    private const val KEY_ARTIST = "artist"
    private const val KEY_ARTWORK = "artwork"
    private const val KEY_PLAYING = "playing"
    private const val KEY_HAS_SONG = "has_song"
    private const val ART_FILE = "widget_art.png"

    private val scope = CoroutineScope(Dispatchers.IO)
    private val artworkToken = AtomicInteger(0)

    fun update(context: Context, song: Song?, isPlaying: Boolean) {
        val appContext = context.applicationContext
        val token = artworkToken.incrementAndGet()
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_HAS_SONG, song != null)
            putString(KEY_TITLE, song?.title ?: "")
            putString(KEY_ARTIST, song?.artist ?: "")
            putString(KEY_ARTWORK, song?.artworkUrl ?: "")
            putBoolean(KEY_PLAYING, isPlaying)
        }

        val artworkUrl = song?.artworkUrl
        scope.launch {
            // Fast track changes can finish out of order; only the newest update
            // is allowed to replace the artwork file.
            if (token != artworkToken.get()) return@launch
            val artFile = artFile(appContext)
            if (artworkUrl.isNullOrBlank()) {
                artFile.delete()
            } else {
                runCatching {
                    val request = ImageRequest.Builder(appContext)
                        .data(artworkUrl)
                        .allowHardware(false)
                        .size(512)
                        .build()
                    val result = Coil.imageLoader(appContext).execute(request)
                    val drawable = (result as? SuccessResult)?.drawable
                    if (drawable != null) {
                        val bitmap = androidx.core.graphics.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
                        val canvas = android.graphics.Canvas(bitmap)
                        drawable.setBounds(0, 0, canvas.width, canvas.height)
                        drawable.draw(canvas)
                        if (token == artworkToken.get()) {
                            artFile.outputStream().use {
                                bitmap.compress(Bitmap.CompressFormat.PNG, 90, it)
                            }
                        }
                    }
                }
            }
            if (token == artworkToken.get()) {
                NowPlayingWidgetProvider.updateAll(appContext)
            }
        }
    }

    fun updatePlaying(context: Context, isPlaying: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit { putBoolean(KEY_PLAYING, isPlaying) }
        NowPlayingWidgetProvider.updateAll(context)
    }

    data class Snapshot(
        val hasSong: Boolean,
        val title: String,
        val artist: String,
        val isPlaying: Boolean,
        val artworkFile: File?
    )

    fun read(context: Context): Snapshot {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val art = artFile(context.applicationContext)
        return Snapshot(
            hasSong = prefs.getBoolean(KEY_HAS_SONG, false),
            title = prefs.getString(KEY_TITLE, "").orEmpty(),
            artist = prefs.getString(KEY_ARTIST, "").orEmpty(),
            isPlaying = prefs.getBoolean(KEY_PLAYING, false),
            artworkFile = art.takeIf { it.exists() && it.length() > 0L }
        )
    }

    private fun artFile(context: Context) = File(context.cacheDir, ART_FILE)
}
