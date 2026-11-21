@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.spotifly.service

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.spotifly.MainActivity
import com.example.spotifly.player.AudioEffectsManager
import com.example.spotifly.player.MonoRenderersFactory
import com.example.spotifly.player.PlaybackTuning

class MusicPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        PlaybackTuning.init(this)
        AudioEffectsManager.getInstance(this)

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .apply {
                if (PlaybackTuning.monoAudio.value) {
                    setRenderersFactory(MonoRenderersFactory(this@MusicPlaybackService))
                }
            }
            .build()

        // Silence skipping is a decoder-level feature, so it is set on the player
        // instance and re-applied whenever the preference changes on a new session.
        runCatching { player.skipSilenceEnabled = PlaybackTuning.skipSilence.value }

        // The audio session id only becomes usable once output starts. Attach the
        // equalizer on the first real playback and keep it for the session.
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    runCatching {
                        AudioEffectsManager.getInstance(this@MusicPlaybackService)
                            .attach(player.audioSessionId)
                    }
                }
            }
        })

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
