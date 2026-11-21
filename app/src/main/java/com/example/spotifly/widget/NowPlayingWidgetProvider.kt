package com.example.spotifly.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.spotifly.MainActivity
import com.example.spotifly.R
import com.example.spotifly.service.MusicPlaybackService

/**
 * Home-screen "now playing" card. It reads the snapshot the player writes and
 * sends transport commands back through a short-lived [MediaController], which
 * is the supported way for an app widget to talk to a MediaSessionService.
 */
class NowPlayingWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { id ->
            appWidgetManager.updateAppWidget(id, buildViews(context))
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        if (action != ACTION_TOGGLE && action != ACTION_NEXT && action != ACTION_PREV) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val token = SessionToken(
            appContext,
            ComponentName(appContext, MusicPlaybackService::class.java)
        )
        val future = MediaController.Builder(appContext, token).buildAsync()
        future.addListener({
            try {
                val controller = future.get()
                when (action) {
                    ACTION_TOGGLE -> if (controller.isPlaying) controller.pause() else {
                        controller.prepare()
                        controller.play()
                    }
                    ACTION_NEXT -> controller.seekToNextMediaItem()
                    ACTION_PREV -> controller.seekToPreviousMediaItem()
                }
                controller.release()
            } catch (_: Exception) {
                // Session not running yet: tapping the card opens the app instead.
            } finally {
                pendingResult.finish()
            }
        }, ContextCompat.getMainExecutor(appContext))
    }

    companion object {
        const val ACTION_TOGGLE = "com.example.spotifly.widget.TOGGLE"
        const val ACTION_NEXT = "com.example.spotifly.widget.NEXT"
        const val ACTION_PREV = "com.example.spotifly.widget.PREV"

        fun updateAll(context: Context) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val ids = manager.getAppWidgetIds(
                ComponentName(appContext, NowPlayingWidgetProvider::class.java)
            )
            if (ids.isEmpty()) return
            val views = buildViews(appContext)
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private fun buildViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_now_playing)
            val state = WidgetStateStore.read(context)

            views.setTextViewText(
                R.id.widget_title,
                state.title.ifBlank { "Nothing playing" }
            )
            views.setTextViewText(
                R.id.widget_artist,
                state.artist.ifBlank { "Open Spotifly to pick a track" }
            )
            views.setImageViewResource(
                R.id.widget_play_pause,
                if (state.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
            )

            val art = state.artworkFile?.let { BitmapFactory.decodeFile(it.absolutePath) }
            if (art != null) {
                views.setImageViewBitmap(R.id.widget_art, art)
            } else {
                views.setImageViewResource(R.id.widget_art, R.drawable.ic_widget_music_note)
            }

            views.setOnClickPendingIntent(R.id.widget_prev, broadcast(context, ACTION_PREV, 1))
            views.setOnClickPendingIntent(R.id.widget_play_pause, broadcast(context, ACTION_TOGGLE, 2))
            views.setOnClickPendingIntent(R.id.widget_next, broadcast(context, ACTION_NEXT, 3))
            views.setOnClickPendingIntent(R.id.widget_root, openApp(context))
            return views
        }

        private fun broadcast(context: Context, action: String, requestCode: Int): PendingIntent {
            val intent = Intent(context, NowPlayingWidgetProvider::class.java).setAction(action)
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun openApp(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
