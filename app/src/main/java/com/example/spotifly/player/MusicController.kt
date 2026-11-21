package com.example.spotifly.player

import androidx.core.net.toUri
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.spotifly.data.autoplay.AudioVibeAnalyzer
import com.example.spotifly.data.autoplay.AutoplayCandidate
import com.example.spotifly.data.autoplay.AutoplayManager
import com.example.spotifly.data.autoplay.AutoplayPick
import com.example.spotifly.data.autoplay.AutoplayQueries
import com.example.spotifly.data.autoplay.CandidateSource
import com.example.spotifly.data.autoplay.VibeFeatures
import com.example.spotifly.data.autoplay.VibeProfileStore
import com.example.spotifly.data.autoplay.VibeRanker
import com.example.spotifly.data.autoplay.VibeVector
import com.example.spotifly.data.local.LocalMusicRepository
import com.example.spotifly.data.offline.OfflineMusicManager
import com.example.spotifly.data.online.OnlineMusicService
import com.example.spotifly.data.online.PlaybackResolver
import com.example.spotifly.data.podcast.PodcastRepository
import com.example.spotifly.data.prefs.UserPreferencesManager
import com.example.spotifly.data.stats.ListeningStatsManager
import com.example.spotifly.model.PlaybackState
import com.example.spotifly.model.RepeatMode
import com.example.spotifly.model.Song
import com.example.spotifly.service.MusicPlaybackService
import com.example.spotifly.widget.WidgetStateStore
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class MusicController private constructor(private val context: Context) {

    private val prefs = UserPreferencesManager.getInstance(context)
    private val stats = ListeningStatsManager.getInstance(context)
    private val podcasts = PodcastRepository.getInstance(context)
    private val offline = OfflineMusicManager.getInstance(context)
    private val vibeStore = VibeProfileStore.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var progressJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var sessionSaveJob: Job? = null
    private var fadeJob: Job? = null
    private var autoplayJob: Job? = null
    private var preResolveJob: Job? = null
    private var refillJob: Job? = null
    private var analysisJob: Job? = null
    private var analyzingSongId: String? = null
    private val fastSkippedIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private var trackStartTimeMs: Long = 0L

    /**
     * Incremented whenever the queue is edited or the current track changes.
     * A background autoplay fetch compares its token before mutating the queue,
     * so a slow network response cannot override a manual action.
     */
    private var autoplayToken = 0
    private var lastAutoplayAttemptSongId: String? = null
    private var lastAutoplayAttemptAt = 0L
    private var isFadingOut = false

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _favorites = MutableStateFlow<Set<String>>(emptySet())
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    private val _sleepTimerEndMs = MutableStateFlow<Long?>(null)
    val sleepTimerEndMs: StateFlow<Long?> = _sleepTimerEndMs.asStateFlow()

    /** Sleep timer set to "end of track": pause when the current song finishes. */
    private val _sleepAtEndOfTrack = MutableStateFlow(false)
    val sleepAtEndOfTrack: StateFlow<Boolean> = _sleepAtEndOfTrack.asStateFlow()

    private var lyricsRequestId = 0
    private var isRestoringSession = false
    private var isControllerReady = false
    private var pendingAction: ((MediaController) -> Unit)? = null
    private var erroredSongId: String? = null
    private var erroredSongRetries = 0

    /** Set while restoring a stored speed, so it is not written back as a new preference. */
    @Volatile
    private var suppressSpeedPersist = false

    init {
        PlaybackTuning.init(context)
        scope.launch {
            // Favorites hydrate off the main thread, so mirror the flow instead of a snapshot
            prefs.favoriteSongs.collect { songs ->
                _favorites.value = songs.map { it.id }.toSet()
            }
        }
        initializeController()
        startPositionTicker()
    }

    private fun initializeController() {
        val sessionToken = SessionToken(context, ComponentName(context, MusicPlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            // The future can complete on a binder thread; Player calls are main-thread only.
            scope.launch {
                try {
                    val controller = controllerFuture?.get() ?: return@launch
                    mediaController = controller
                    setupPlayerListener(controller)
                    applyRestoredSettings(controller)
                    isControllerReady = true
                    restoreLastSession()
                    val pending = pendingAction
                    pendingAction = null
                    pending?.invoke(controller)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }, MoreExecutors.directExecutor())
    }

    /**
     * Runs [action] against the bound controller, or keeps the latest action and runs it
     * as soon as binding finishes — otherwise the first play tap on a cold start is dropped.
     */
    private fun withController(action: (MediaController) -> Unit) {
        val controller = mediaController
        if (controller != null && isControllerReady) {
            action(controller)
        } else {
            pendingAction = action
        }
    }

    private fun applyRestoredSettings(controller: MediaController) {
        controller.shuffleModeEnabled = prefs.shuffleEnabledPref.value
        controller.repeatMode = when (prefs.repeatModePref.value) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }
        if (kotlin.math.abs(prefs.playbackSpeedPref.value - 1.0f) > 0.01f) {
            controller.setPlaybackSpeed(prefs.playbackSpeedPref.value)
        }
        _playbackState.update {
            it.copy(
                isShuffle = prefs.shuffleEnabledPref.value,
                repeatMode = prefs.repeatModePref.value,
                isAutoplay = prefs.autoplayEnabled.value,
                playbackSpeed = prefs.playbackSpeedPref.value
            )
        }
    }

    private fun setupPlayerListener(player: Player) {
        player.addListener(object : Player.Listener {
            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                _playbackState.update { it.copy(playbackSpeed = playbackParameters.speed) }
                if (!suppressSpeedPersist) {
                    prefs.setPlaybackSpeed(playbackParameters.speed)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playbackState.update { it.copy(isPlaying = isPlaying) }
                WidgetStateStore.updatePlaying(context, isPlaying)
                if (!isPlaying) {
                    // Leaving a fade half-applied would make the next resume silent
                    fadeJob?.cancel()
                    isFadingOut = false
                    mediaController?.volume = 1f
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val isBuffering = playbackState == Player.STATE_BUFFERING
                val duration = player.duration.coerceAtLeast(0L)
                _playbackState.update {
                    it.copy(
                        isBuffering = isBuffering,
                        durationMs = if (duration > 0) duration else it.durationMs
                    )
                }

                if (playbackState == Player.STATE_ENDED) {
                    val finished = _playbackState.value.currentSong
                    if (finished != null && finished.isPodcast) {
                        // Finished episodes lose their resume point and get a played mark
                        podcasts.markCompleted(podcasts.episodeIdOf(finished))
                    } else if (_sleepAtEndOfTrack.value) {
                        // The user asked playback to stop after this track.
                        stopAtEndOfTrackIfRequested()
                    } else if (_playbackState.value.isAutoplay && finished != null) {
                        // Refill autoplay queue and continue playback
                        checkAndRefillAutoplayQueue(finished, forcePlay = true)
                    }
                }
                if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
                    saveSession()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                // Dead/broken stream or 403/410: attempt re-resolution once, then skip silently
                val failedSong = _playbackState.value.currentSong
                val failedId = failedSong?.id
                val retries = if (failedId == erroredSongId) erroredSongRetries + 1 else 1
                erroredSongId = failedId
                erroredSongRetries = retries

                if (retries <= 1 && failedSong != null && !failedSong.isLocal && !failedSong.isPodcast) {
                    scope.launch {
                        val refreshed = runCatching { PlaybackResolver.reResolve(failedSong) }.getOrNull()
                        if (refreshed != null && !refreshed.streamUrl.isNullOrEmpty()) {
                            updateResolvedSong(refreshed)
                            withController { ctrl ->
                                ctrl.prepare()
                                ctrl.play()
                            }
                            return@launch
                        }
                        skipNextSilently()
                    }
                    return
                }

                // If still failing or second attempt: skip silently so queue never stalls
                skipNextSilently()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    // The timer may be set to stop when the song ends; pause as soon
                    // as the next item is queued instead of letting it play on.
                    stopAtEndOfTrackIfRequested()
                }
                erroredSongId = null
                erroredSongRetries = 0
                trackStartTimeMs = System.currentTimeMillis()
                val mediaId = mediaItem?.mediaId
                val currentQueue = _playbackState.value.queue
                val newIndex = resolveQueueIndex(currentQueue, mediaId, player.currentMediaItemIndex)
                val newSong = if (newIndex != -1) currentQueue[newIndex] else null

                // Consume item from manualQueue or autoplayQueue if applicable
                val currentManual = _playbackState.value.manualQueue
                val currentAuto = _playbackState.value.autoplayQueue
                val updatedManual = if (currentManual.isNotEmpty() && newSong?.id == currentManual.first().id) {
                    currentManual.drop(1)
                } else {
                    currentManual
                }
                val updatedAuto = if (currentAuto.isNotEmpty() && newSong?.id == currentAuto.first().id) {
                    currentAuto.drop(1)
                } else {
                    currentAuto
                }

                _playbackState.update {
                    it.copy(
                        currentSong = newSong,
                        currentIndex = newIndex,
                        currentPositionMs = 0L,
                        durationMs = newSong?.durationMs ?: 0L,
                        lyrics = null,
                        playbackError = null,
                        manualQueue = updatedManual,
                        autoplayQueue = updatedAuto
                    )
                }
                WidgetStateStore.update(context, newSong, _playbackState.value.isPlaying)

                if (newSong != null) {
                    if (!isRestoringSession) {
                        prefs.recordPlayed(newSong)
                        stats.recordPlay(newSong)
                    }
                    if (newSong.isPodcast) {
                        // Spoken audio resumes where it stopped instead of restarting
                        val resumeAt = podcasts.progressFor(newSong.id)
                        if (resumeAt > 0) {
                            mediaController?.seekTo(resumeAt)
                            _playbackState.update { it.copy(currentPositionMs = resumeAt) }
                        }
                    } else {
                        loadLyrics(newSong)
                    }
                    // Each content type remembers its own speed; applying one must not
                    // overwrite the other's preference.
                    val rememberedSpeed = if (newSong.isPodcast) {
                        PlaybackTuning.podcastSpeed.value
                    } else {
                        prefs.playbackSpeedPref.value
                    }
                    suppressSpeedPersist = true
                    mediaController?.setPlaybackSpeed(rememberedSpeed)
                    suppressSpeedPersist = false
                    _playbackState.update { it.copy(playbackSpeed = rememberedSpeed) }
                    analyzeInBackground(newSong)

                    // Ensure the current song has a resolved streamUrl
                    if (!newSong.isLocal && !newSong.isPodcast && newSong.streamUrl.isNullOrEmpty()) {
                        scope.launch {
                            val resolved = runCatching { PlaybackResolver.resolve(newSong) }.getOrNull()
                            if (resolved != null && !resolved.streamUrl.isNullOrEmpty()) {
                                updateResolvedSong(resolved)
                            } else {
                                skipNextSilently()
                            }
                        }
                    }

                    // Build / refill autoplay queue if low on tracks
                    if (_playbackState.value.isAutoplay && !newSong.isPodcast) {
                        checkAndRefillAutoplayQueue(newSong)
                    }

                    preResolveUpcoming()
                    startFadeIn()
                }
                saveSession()
            }
        })
    }

    private fun startPositionTicker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            var sessionSaveCounter = 0
            while (isActive) {
                mediaController?.let { controller ->
                    if (controller.isPlaying) {
                        val pos = controller.currentPosition.coerceAtLeast(0L)
                        val dur = controller.duration.coerceAtLeast(0L)
                        _playbackState.update {
                            it.copy(
                                currentPositionMs = pos,
                                durationMs = if (dur > 0) dur else it.durationMs
                            )
                        }
                        maybeStartFadeOut(pos, dur)
                        maybePrefetchAutoplay(pos, dur)
                        if (_sleepAtEndOfTrack.value && dur > 0 && pos >= dur - 350) {
                            stopAtEndOfTrackIfRequested()
                        }
                        // Podcast resume points are written more often than the session
                        _playbackState.value.currentSong?.takeIf { it.isPodcast }?.let { episode ->
                            podcasts.saveProgress(podcasts.episodeIdOf(episode), pos)
                        }
                        // Persist the session roughly every 5 seconds of playback
                        sessionSaveCounter++
                        if (sessionSaveCounter >= 25) {
                            sessionSaveCounter = 0
                            saveSession()
                        }
                        delay(200)
                    } else {
                        // Idle: slow the ticker down so we don't burn battery
                        delay(1000)
                    }
                } ?: delay(1000)
            }
        }
    }

    // ---- Crossfade ----
    //
    // A single ExoPlayer cannot overlap two tracks, so this is a fade-through:
    // the tail of one track ramps down and the head of the next ramps up. It is the
    // behaviour users expect from a crossfade control, without a second decoder.

    private fun startFadeIn() {
        val crossfadeMs = PlaybackTuning.crossfadeMs.value
        val controller = mediaController ?: return
        fadeJob?.cancel()
        isFadingOut = false

        if (crossfadeMs <= 0) {
            controller.volume = 1f
            return
        }

        controller.volume = 0f
        fadeJob = scope.launch {
            val steps = 20
            val stepMs = (crossfadeMs / 2 / steps).coerceAtLeast(20).toLong()
            for (step in 1..steps) {
                if (!isActive) return@launch
                controller.volume = step.toFloat() / steps
                delay(stepMs)
            }
            controller.volume = 1f
        }
    }

    private fun maybeStartFadeOut(positionMs: Long, durationMs: Long) {
        val crossfadeMs = PlaybackTuning.crossfadeMs.value.toLong()
        if (crossfadeMs <= 0 || durationMs <= 0 || positionMs <= 0) return

        val fadeWindow = (crossfadeMs / 2).coerceAtLeast(400L)
        val remaining = durationMs - positionMs

        // Seeking backwards out of the fade window must restore full volume
        if (remaining > fadeWindow) {
            if (isFadingOut) {
                fadeJob?.cancel()
                isFadingOut = false
                mediaController?.volume = 1f
            }
            return
        }
        if (isFadingOut || remaining <= 0) return

        val controller = mediaController ?: return
        isFadingOut = true
        fadeJob?.cancel()
        fadeJob = scope.launch {
            val steps = 15
            val startVolume = controller.volume.coerceAtLeast(0f)
            val stepMs = (remaining / steps).coerceAtLeast(40L)
            for (step in 1..steps) {
                if (!isActive) return@launch
                controller.volume = (startVolume * (1f - step.toFloat() / steps)).coerceIn(0f, 1f)
                delay(stepMs)
            }
            controller.volume = 0f
        }
    }

    // ---- Session restore ----

    private fun restoreLastSession() {
        isRestoringSession = true
        scope.launch {
            try {
                val session = prefs.loadSession() ?: return@launch
                // Ignore sessions older than 30 days
                if (System.currentTimeMillis() - session.savedAt > 30L * 24 * 60 * 60 * 1000) return@launch
                if (session.songs.isEmpty()) return@launch

                val controller = mediaController ?: return@launch
                // A play tap that arrived while the session was loading wins over the restore
                if (_playbackState.value.currentSong != null) return@launch
                val index = session.index.coerceIn(0, session.songs.lastIndex)
                _playbackState.update {
                    it.copy(
                        queue = session.songs,
                        currentIndex = index,
                        currentSong = session.songs[index],
                        currentPositionMs = session.positionMs,
                        durationMs = session.songs[index].durationMs
                    )
                }
                controller.setMediaItems(session.songs.map { it.toMediaItem() }, index, session.positionMs)
                controller.prepare()
                loadLyrics(session.songs[index])
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isRestoringSession = false
            }
        }
    }

    private fun saveSession() {
        val state = _playbackState.value
        val song = state.currentSong ?: return
        sessionSaveJob?.cancel()
        sessionSaveJob = scope.launch {
            prefs.saveSession(state.queue, state.currentIndex, state.currentPositionMs)
        }
    }

    // ---- Public controls ----

    fun playSong(song: Song, startRadio: Boolean = false) {
        cancelAutoplayFetch()
        preResolveJob?.cancel()
        refillJob?.cancel()
        trackStartTimeMs = System.currentTimeMillis()

        _playbackState.update {
            it.copy(
                isAutoplay = startRadio || prefs.autoplayEnabled.value,
                manualQueue = emptyList(),
                autoplayQueue = emptyList(),
                queue = listOf(song),
                currentIndex = 0,
                currentSong = song,
                durationMs = song.durationMs,
                currentPositionMs = 0L,
                lyrics = null,
                playbackError = null,
                isBuffering = true
            )
        }

        scope.launch {
            val resolved = if (song.isLocal || !song.streamUrl.isNullOrEmpty()) {
                song
            } else {
                PlaybackResolver.resolve(song)
            }

            if (resolved == null || (!resolved.isLocal && resolved.streamUrl.isNullOrEmpty())) {
                _playbackState.update {
                    it.copy(
                        isBuffering = false,
                        playbackError = "Couldn't play '${song.title}' — stream unavailable"
                    )
                }
                return@launch
            }

            _playbackState.update {
                it.copy(
                    queue = listOf(resolved),
                    currentSong = resolved,
                    isBuffering = false
                )
            }
            loadLyrics(resolved)

            withController { controller ->
                controller.clearMediaItems()
                controller.setMediaItem(resolved.toMediaItem())
                controller.prepare()
                controller.play()
            }

            if (_playbackState.value.isAutoplay) {
                triggerAutoplayQueueBuild(resolved)
            }
        }
    }

    fun playQueue(songs: List<Song>, startIndex: Int) {
        if (songs.isEmpty()) return
        cancelAutoplayFetch()
        preResolveJob?.cancel()
        refillJob?.cancel()
        trackStartTimeMs = System.currentTimeMillis()

        val requestedIndex = startIndex.coerceIn(0, songs.lastIndex)
        val requestedSong = songs[requestedIndex]
        val previous = _playbackState.value
        val wasSameSong = previous.currentSong?.id == requestedSong.id &&
            previous.queue == songs

        if (wasSameSong) {
            withController { controller ->
                controller.seekTo(0)
                controller.play()
            }
            return
        }

        _playbackState.update {
            it.copy(
                queue = songs,
                isAutoplay = prefs.autoplayEnabled.value,
                currentIndex = requestedIndex,
                currentSong = requestedSong,
                durationMs = requestedSong.durationMs,
                currentPositionMs = 0L,
                lyrics = null,
                playbackError = null,
                manualQueue = emptyList(),
                autoplayQueue = emptyList(),
                isBuffering = true
            )
        }

        scope.launch {
            val resolved = if (requestedSong.isLocal || !requestedSong.streamUrl.isNullOrEmpty()) {
                requestedSong
            } else {
                PlaybackResolver.resolve(requestedSong) ?: requestedSong
            }

            val updatedSongs = songs.toMutableList()
            updatedSongs[requestedIndex] = resolved

            _playbackState.update {
                it.copy(
                    queue = updatedSongs,
                    currentSong = resolved,
                    isBuffering = false
                )
            }
            loadLyrics(resolved)

            withController { controller ->
                controller.clearMediaItems()
                val mediaItems = updatedSongs.map { it.toMediaItem() }
                controller.setMediaItems(mediaItems, requestedIndex, 0L)
                controller.prepare()
                controller.play()
            }

            preResolveUpcoming()
        }
    }

    fun togglePlayPause() {
        withController { controller ->
            if (controller.isPlaying) {
                controller.pause()
            } else {
                if (controller.playbackState == Player.STATE_IDLE || controller.playbackState == Player.STATE_ENDED) {
                    controller.prepare()
                }
                controller.play()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
        _playbackState.update { it.copy(currentPositionMs = positionMs) }
        saveSession()
    }

    fun skipNext() {
        val current = _playbackState.value.currentSong
        val now = System.currentTimeMillis()
        if (current != null && trackStartTimeMs > 0 && now - trackStartTimeMs < 10_000L) {
            fastSkippedIds.add(current.id)
            if (current.internalId.isNotBlank()) fastSkippedIds.add(current.internalId)
        }
        mediaController?.let {
            if (it.hasNextMediaItem()) {
                it.seekToNextMediaItem()
            } else if (_playbackState.value.repeatMode == RepeatMode.ALL && _playbackState.value.queue.isNotEmpty()) {
                playQueue(_playbackState.value.queue, 0)
            }
        }
    }

    fun skipPrevious() {
        mediaController?.let {
            if (it.currentPosition > 3000) {
                it.seekTo(0)
            } else if (it.hasPreviousMediaItem()) {
                it.seekToPreviousMediaItem()
            }
        }
    }

    fun toggleShuffle() {
        val newShuffle = !_playbackState.value.isShuffle
        mediaController?.shuffleModeEnabled = newShuffle
        _playbackState.update { it.copy(isShuffle = newShuffle) }
        prefs.setShuffleEnabled(newShuffle)
        if (newShuffle) {
            dropAutoplayTail()
        }
    }

    fun toggleRepeatMode() {
        val nextMode = when (_playbackState.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }

        mediaController?.repeatMode = when (nextMode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }

        _playbackState.update { it.copy(repeatMode = nextMode) }
        prefs.setRepeatMode(nextMode)
    }

    fun toggleFavorite(song: Song) {
        prefs.toggleFavorite(song)
    }

    fun toggleAutoplay() {
        val newAutoplay = !_playbackState.value.isAutoplay
        prefs.setAutoplay(newAutoplay)

        if (!newAutoplay) {
            cancelAutoplayFetch()
            val autoCount = _playbackState.value.autoplayQueue.size
            if (autoCount > 0) {
                val totalCount = _playbackState.value.queue.size
                withController { controller ->
                    for (i in (totalCount - 1) downTo (totalCount - autoCount)) {
                        if (i >= 0 && i < controller.mediaItemCount) {
                            controller.removeMediaItem(i)
                        }
                    }
                }
                _playbackState.update {
                    it.copy(
                        isAutoplay = false,
                        autoplayQueue = emptyList(),
                        queue = it.queue.dropLast(autoCount)
                    )
                }
            } else {
                _playbackState.update { it.copy(isAutoplay = false) }
            }
        } else {
            _playbackState.update { it.copy(isAutoplay = true) }
            val current = _playbackState.value.currentSong
            if (current != null) {
                checkAndRefillAutoplayQueue(current)
            }
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.25f, 3.0f)
        mediaController?.setPlaybackSpeed(clamped)
        _playbackState.update { it.copy(playbackSpeed = clamped) }
        if (_playbackState.value.currentSong?.isPodcast == true) {
            PlaybackTuning.setPodcastSpeed(clamped)
        } else {
            prefs.setPlaybackSpeed(clamped)
        }
    }

    // ---- Sleep timer ----

    fun startSleepTimer(minutes: Int) {
        cancelSleepTimer()
        if (minutes <= 0) return
        _sleepTimerEndMs.value = System.currentTimeMillis() + minutes * 60_000L
        sleepTimerJob = scope.launch {
            while (isActive) {
                val end = _sleepTimerEndMs.value ?: break
                if (System.currentTimeMillis() >= end) {
                    mediaController?.pause()
                    _sleepTimerEndMs.value = null
                    break
                }
                delay(1_000)
            }
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimerEndMs.value = null
        _sleepAtEndOfTrack.value = false
    }

    /**
     * Spotify's "end of track" sleep option: let the current song finish, then stop.
     * Checked both by the ticker (before the transition) and on the automatic
     * transition itself, so a slow tick cannot let the next song run away.
     */
    fun startSleepTimerAtEndOfTrack() {
        cancelSleepTimer()
        _sleepAtEndOfTrack.value = true
    }

    private fun stopAtEndOfTrackIfRequested() {
        if (!_sleepAtEndOfTrack.value) return
        _sleepAtEndOfTrack.value = false
        mediaController?.pause()
    }

    /** Clears a transient failure message once the UI has consumed it. */
    fun clearPlaybackError() {
        _playbackState.update { it.copy(playbackError = null) }
    }

    // ---- Autoplay ("play similar") ----
    //
    // The staged continuation is a real Media3 playlist item so the transition to
    // it is gapless. It is tagged in PlaybackState.autoplayNextId; any manual
    // queue edit drops it so user picks always play before autoplay picks.

    /**
     * Safety net for long tracks: if remaining duration is low, stage continuation before song ends.
     */
    private fun maybePrefetchAutoplay(positionMs: Long, durationMs: Long) {
        val state = _playbackState.value
        if (!state.isAutoplay || state.isShuffle) return
        val current = state.currentSong ?: return
        if (current.isPodcast) return
        if (durationMs <= 0L) return
        val remaining = durationMs - positionMs
        if (remaining < 0 || remaining > AUTOPLAY_PREFETCH_WINDOW_MS) return
        if (state.autoplayQueue.size < 3) {
            checkAndRefillAutoplayQueue(current)
        }
    }

    private fun triggerAutoplayQueueBuild(seed: Song) {
        cancelAutoplayFetch()
        val token = autoplayToken
        autoplayJob = scope.launch(Dispatchers.IO) {
            val similar = runCatching {
                if (prefs.offlineMode.value) listOfNotNull(pickAutoplayCandidate(seed)?.song)
                else AutoplayManager.buildAutoplayQueue(
                    seed = seed,
                    recentPlayed = prefs.recentlyPlayed.value,
                    excludeIds = setOf(seed.id, seed.internalId),
                    userLikedIds = _favorites.value,
                    userSkippedFastIds = fastSkippedIds.toSet(),
                    measured = { vibeStore.get(it) },
                    prepareCandidates = { measureCandidates(it) }
                )
            }.getOrDefault(emptyList())

            if (token != autoplayToken) return@launch
            if (similar.isEmpty()) return@launch

            val latest = _playbackState.value
            if (latest.currentSong?.id != seed.id || !latest.isAutoplay) return@launch

            withContext(Dispatchers.Main) {
                if (token != autoplayToken || !_playbackState.value.isAutoplay ||
                    _playbackState.value.currentSong?.id != seed.id) return@withContext
                val currentManual = _playbackState.value.manualQueue
                val fresh = similar.filter { candidate -> currentManual.none { it.id == candidate.id || it.internalId == candidate.internalId } }
                val updatedQueue = _playbackState.value.queue + fresh
                _playbackState.update {
                    it.copy(
                        autoplayQueue = fresh,
                        queue = updatedQueue
                    )
                }

                withController { controller ->
                    val nextIndex = controller.mediaItemCount
                    for (s in fresh) {
                        controller.addMediaItem(s.toMediaItem())
                    }
                    if (fresh.isNotEmpty() && controller.playbackState == Player.STATE_ENDED) {
                        controller.seekTo(nextIndex, 0L)
                        controller.prepare()
                        controller.play()
                    }
                }

                preResolveUpcoming()
            }
        }
    }

    private fun checkAndRefillAutoplayQueue(seed: Song, forcePlay: Boolean = false) {
        val state = _playbackState.value
        if (!state.isAutoplay) return
        if (state.autoplayQueue.size >= 3 && !forcePlay) return
        if (refillJob?.isActive == true || autoplayJob?.isActive == true) return
        val token = autoplayToken

        refillJob = scope.launch(Dispatchers.IO) {
            val effectiveSeed = seed
            val exclude = state.queue.map { it.id }.toSet() + state.queue.map { it.internalId }.toSet()
            val more = runCatching {
                if (prefs.offlineMode.value) listOfNotNull(pickAutoplayCandidate(effectiveSeed)?.song)
                else AutoplayManager.buildAutoplayQueue(
                    seed = effectiveSeed,
                    recentPlayed = prefs.recentlyPlayed.value,
                    excludeIds = exclude,
                    userLikedIds = _favorites.value,
                    userSkippedFastIds = fastSkippedIds.toSet(),
                    measured = { vibeStore.get(it) },
                    prepareCandidates = { measureCandidates(it) }
                )
            }.getOrDefault(emptyList())

            if (more.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    val curr = _playbackState.value
                    if (token != autoplayToken || !curr.isAutoplay || curr.currentSong?.id != seed.id) return@withContext
                    val fresh = more.filter { candidate -> curr.queue.none { it.id == candidate.id || it.internalId == candidate.internalId } }
                    if (fresh.isEmpty()) return@withContext
                    val newAuto = curr.autoplayQueue + fresh
                    val newQueue = curr.queue + fresh
                    _playbackState.update { it.copy(autoplayQueue = newAuto, queue = newQueue) }
                    withController { ctrl ->
                        for (s in fresh) {
                            ctrl.addMediaItem(s.toMediaItem())
                        }
                        if (ctrl.playbackState == Player.STATE_ENDED) {
                            ctrl.seekTo(curr.queue.size, 0L)
                            ctrl.prepare()
                            ctrl.play()
                        }
                    }
                    preResolveUpcoming()
                }
            }
        }
    }

    private fun updateResolvedSong(resolved: Song) {
        val state = _playbackState.value
        val idx = state.queue.indexOfFirst {
            it.id == resolved.id || (it.internalId.isNotBlank() && it.internalId == resolved.internalId)
        }
        if (idx != -1) {
            val newQueue = state.queue.toMutableList()
            newQueue[idx] = resolved
            val newManual = state.manualQueue.map {
                if (it.id == resolved.id || it.internalId == resolved.internalId) resolved else it
            }
            val newAuto = state.autoplayQueue.map {
                if (it.id == resolved.id || it.internalId == resolved.internalId) resolved else it
            }
            _playbackState.update {
                it.copy(
                    queue = newQueue,
                    manualQueue = newManual,
                    autoplayQueue = newAuto,
                    currentSong = if (state.currentIndex == idx) resolved else state.currentSong
                )
            }

            withController { controller ->
                if (idx >= 0 && idx < controller.mediaItemCount) {
                    controller.replaceMediaItem(idx, resolved.toMediaItem())
                }
            }
        }
    }

    private fun preResolveUpcoming() {
        preResolveJob?.cancel()
        preResolveJob = scope.launch(Dispatchers.IO) {
            val state = _playbackState.value
            val upcoming = state.queue.drop((state.currentIndex + 1).coerceAtLeast(0)).take(3)
            for (song in upcoming) {
                if (!song.isLocal && !song.isPodcast && song.streamUrl.isNullOrEmpty()) {
                    val resolved = runCatching { PlaybackResolver.resolve(song) }.getOrNull()
                    if (resolved != null && !resolved.streamUrl.isNullOrEmpty()) {
                        withContext(Dispatchers.Main) {
                            updateResolvedSong(resolved)
                        }
                    }
                }
            }
        }
    }

    private fun skipNextSilently() {
        scope.launch {
            delay(300)
            withController { controller ->
                if (controller.hasNextMediaItem()) {
                    controller.seekToNextMediaItem()
                    controller.prepare()
                    controller.play()
                } else {
                    _playbackState.update { it.copy(isBuffering = false) }
                }
            }
        }
    }

    private fun cancelAutoplayFetch() {
        autoplayToken++
        autoplayJob?.cancel()
        autoplayJob = null
        refillJob?.cancel()
        refillJob = null
        lastAutoplayAttemptSongId = null
    }

    private fun dropAutoplayTail() {
        cancelAutoplayFetch()
    }

    private suspend fun pickAutoplayCandidate(current: Song): AutoplayPick? {
        // If the seed was never measured (e.g. data saver was on when it started
        // and is now off), give real audio one bounded chance before metadata.
        if (vibeStore.get(current) == null && analyzingSongId != current.id) {
            analyzeInBackground(current)
        }
        // A freshly started decode has the best description of the current track;
        // wait briefly, but never stall the pick on a slow file.
        if (analyzingSongId == current.id) {
            withTimeoutOrNull(ANALYSIS_WAIT_MS) { analysisJob?.join() }
        }

        val measured: (Song) -> VibeVector? = { vibeStore.get(it) }
        val recent = prefs.recentlyPlayed.value
            .filter { it.id != current.id }
            .take(HISTORY_FOR_AUTOPLAY)
        val currentProfile = VibeFeatures.profileFor(current, measured(current))
        val recentProfiles = recent.take(5).map { VibeFeatures.profileFor(it, measured(it)) }
        val target = VibeRanker.targetProfile(currentProfile, recentProfiles)

        // The extra mood search is the first thing to skip on a metered connection.
        val vibeQueries = if (prefs.dataSaver.value) {
            emptyList()
        } else {
            AutoplayQueries.vibeQueries(target)
        }
        val online = if (prefs.offlineMode.value) {
            emptyList()
        } else {
            runCatching { OnlineMusicService.autoplayCandidates(current, vibeQueries) }
                .getOrDefault(emptyList())
        }

        val pool = buildList {
            addAll(online)
            addAll(
                offline.downloadedSongs.value
                    .filterNot { it.isPodcast }
                    .map { AutoplayCandidate(it, CandidateSource.DOWNLOADED) }
            )
            if (online.size < 5) {
                // Local files are the offline fallback, and they carry genre tags
                // that the streaming catalogue does not expose.
                val local = runCatching { LocalMusicRepository(context).getLocalSongs() }
                    .getOrDefault(emptyList())
                addAll(local.map { AutoplayCandidate(it, CandidateSource.LIBRARY) })
            }
            // Older history only becomes eligible after the ranker's 10-song window,
            // so it can rescue a pick without replaying something recent. Podcasts
            // share this history but must never become a music continuation.
            addAll(
                prefs.recentlyPlayed.value
                    .drop(HISTORY_FOR_AUTOPLAY)
                    .filterNot { it.isPodcast }
                    .map { AutoplayCandidate(it, CandidateSource.LIBRARY) }
            )
        }

        // Never spend decode budget on tracks the ranker would refuse anyway.
        val queue = _playbackState.value.queue
        val blockedIds = queue.map { it.id }.toSet()
        val blockedTitles = queue.map { VibeFeatures.titleKey(it) }.toSet()
        val measurable = pool.filterNot { it.song.id in blockedIds }
            .filterNot { VibeFeatures.titleKey(it.song) in blockedTitles }
        withContext(Dispatchers.IO) {
            measureCandidates(measurable)
        }

        return VibeRanker.pick(
            currentSong = current,
            history = recent,
            candidates = pool,
            excludeIds = blockedIds,
            excludeTitles = blockedTitles,
            measured = measured
        )
    }

    /**
     * Measure candidates whose vibe has not been decoded yet so the ranker
     * scores with real audio (the user's requirement) instead of metadata.
     *
     * Sequential on purpose: parallel decodes fight each other for the codec
     * and for bandwidth. Remote URLs are bounded by [CANDIDATE_BUDGET_MS] as a
     * whole and [CANDIDATE_ANALYSIS_TIMEOUT_MS] per track, and are skipped under
     * data saver / offline preferences; local files always decode offline.
     */
    private suspend fun measureCandidates(candidates: List<AutoplayCandidate>) {
        val dataSaver = prefs.dataSaver.value
        val offline = prefs.offlineMode.value
        val deadline = System.currentTimeMillis() + CANDIDATE_BUDGET_MS

        var measuredCount = 0
        for (candidate in candidates.take(MAX_CANDIDATE_ANALYSES * 3)) {
            if (measuredCount >= MAX_CANDIDATE_ANALYSES) return
            if (System.currentTimeMillis() > deadline) return

            val song = candidate.song
            if (song.isPodcast) continue
            if (vibeStore.get(song) != null) continue

            val local = AudioVibeAnalyzer.hasLocalSource(context, song)
            if (!local && (dataSaver || offline)) continue
            if (!local && AudioVibeAnalyzer.streamUrlOf(song) == null) continue

            measuredCount++
            val timeout = if (local) AudioVibeAnalyzer.DECODE_TIMEOUT_MS else AudioVibeAnalyzer.STREAM_TIMEOUT_MS
            val vector = runCatching { AudioVibeAnalyzer.analyze(context, song, timeout) }.getOrNull()
            if (vector != null) vibeStore.put(song, vector)
        }
    }

    /**
     * Decode a track once and remember its measured vibe.
     *
     * Local/downloaded files are always analysable. Streaming tracks are
     * measured too — the user's rule is similarity from real audio — unless a
     * data-saver or offline setting makes paying the bandwidth inappropriate.
     */
    private fun analyzeInBackground(song: Song) {
        if (song.isPodcast) return
        if (vibeStore.get(song) != null) return
        if (analyzingSongId == song.id && analysisJob?.isActive == true) return

        val local = AudioVibeAnalyzer.hasLocalSource(context, song)
        val streamUrl = AudioVibeAnalyzer.streamUrlOf(song)
        if (!local && (streamUrl == null || prefs.offlineMode.value || prefs.dataSaver.value)) return

        analyzingSongId = song.id
        analysisJob = scope.launch(Dispatchers.IO) {
            val timeout = if (local) AudioVibeAnalyzer.DECODE_TIMEOUT_MS else AudioVibeAnalyzer.STREAM_TIMEOUT_MS
            val vector = runCatching { AudioVibeAnalyzer.analyze(context, song, timeout) }.getOrNull()
            if (vector != null) vibeStore.put(song, vector)
        }
    }

    fun addToQueue(song: Song) {
        val state = _playbackState.value
        val currentManual = state.manualQueue
        val updatedManual = currentManual + song
        val currentAuto = state.autoplayQueue
        val currentIndex = state.currentIndex
        val prefix = state.queue.take(currentIndex + 1)
        val newQueue = prefix + updatedManual + currentAuto

        _playbackState.update { it.copy(manualQueue = updatedManual, queue = newQueue) }
        withController { controller ->
            val insertIndex = (currentIndex + 1 + currentManual.size).coerceIn(0, controller.mediaItemCount)
            controller.addMediaItem(insertIndex, song.toMediaItem())
        }
        preResolveUpcoming()
    }

    /** Inserts directly after the current track, like Spotify's "Play next". */
    fun playNext(song: Song) {
        val state = _playbackState.value
        val currentManual = state.manualQueue
        val updatedManual = listOf(song) + currentManual
        val currentAuto = state.autoplayQueue
        val currentIndex = state.currentIndex
        val prefix = state.queue.take(currentIndex + 1)
        val newQueue = prefix + updatedManual + currentAuto

        _playbackState.update { it.copy(manualQueue = updatedManual, queue = newQueue) }
        withController { controller ->
            val insertIndex = (currentIndex + 1).coerceIn(0, controller.mediaItemCount)
            controller.addMediaItem(insertIndex, song.toMediaItem())
        }
        preResolveUpcoming()
    }

    /** Reorders the queue and keeps the player's playlist in sync. */
    fun moveQueueItem(from: Int, to: Int) {
        val queue = _playbackState.value.queue.toMutableList()
        if (from !in queue.indices || to !in queue.indices || from == to) return
        val moved = queue.removeAt(from)
        queue.add(to, moved)

        val current = _playbackState.value.currentIndex
        val newCurrent = when {
            current == from -> to
            current in minOf(from, to)..maxOf(from, to) -> if (from < to) current - 1 else current + 1
            else -> current
        }

        val upcoming = if (newCurrent < queue.lastIndex) queue.subList(newCurrent + 1, queue.size) else emptyList()
        val manualSet = _playbackState.value.manualQueue.map { it.id }.toSet()
        val newManual = upcoming.filter { it.id in manualSet }
        val newAuto = upcoming.filterNot { it.id in manualSet }

        _playbackState.update {
            it.copy(
                queue = queue.toList(),
                manualQueue = newManual,
                autoplayQueue = newAuto,
                currentIndex = newCurrent,
                currentSong = queue.getOrNull(newCurrent)
            )
        }
        mediaController?.moveMediaItem(from, to)
    }

    /** Removes a track from the queue (skipping it if currently playing). */
    fun removeFromQueue(index: Int) {
        val state = _playbackState.value
        val queue = state.queue.toMutableList()
        if (index !in queue.indices) return

        val current = state.currentIndex
        if (index == current) {
            skipNext()
            return
        }

        val removedSong = queue.removeAt(index)
        val controller = mediaController
        controller?.removeMediaItem(index)

        val newManual = state.manualQueue.filterNot { it.id == removedSong.id }
        val newAuto = state.autoplayQueue.filterNot { it.id == removedSong.id }
        val newCurrent = when {
            controller != null -> controller.currentMediaItemIndex
            index < current -> current - 1
            else -> current.coerceAtMost(queue.lastIndex)
        }

        _playbackState.update {
            it.copy(
                queue = queue.toList(),
                manualQueue = newManual,
                autoplayQueue = newAuto,
                currentIndex = newCurrent,
                currentSong = queue.getOrNull(newCurrent)
            )
        }
        saveSession()
    }

    private fun loadLyrics(song: Song) {
        // Only the most recent request wins; stale fetches (playQueue followed by
        // onMediaItemTransition) are discarded, and the service caches repeats.
        val requestId = ++lyricsRequestId
        scope.launch {
            _playbackState.update { it.copy(isLyricsLoading = true) }
            val lyrics = OnlineMusicService.fetchLyrics(song)
            if (_playbackState.value.currentSong?.id == song.id && lyricsRequestId == requestId) {
                _playbackState.update {
                    it.copy(lyrics = lyrics, isLyricsLoading = false)
                }
            }
        }
    }

    private fun Song.toMediaItem(): MediaItem {
        val uri = when {
            isLocal && !contentUriString.isNullOrEmpty() -> (contentUriString).toUri()
            !localFilePath.isNullOrEmpty() && java.io.File(localFilePath).exists() -> {
                Uri.fromFile(java.io.File(localFilePath))
            }
            else -> {
                val cleanId = id.replace(Regex("[^a-zA-Z0-9_]"), "_")
                val offlineFile = java.io.File(context.filesDir, "offline_music/$cleanId.m4a")
                if (offlineFile.exists() && offlineFile.length() > 0) {
                    Uri.fromFile(offlineFile)
                } else {
                    (streamUrl ?: "").toUri()
                }
            }
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(artworkUrl?.let { (it).toUri() })
            .build()

        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(uri)
            .setMediaMetadata(metadata)
            .build()
    }

    companion object {
        private const val MAX_SONG_RETRIES = 3
        private const val AUTOPLAY_PREFETCH_WINDOW_MS = 30_000L
        private const val AUTOPLAY_RETRY_MS = 15_000L
        private const val ANALYSIS_WAIT_MS = 8_000L

        /** Whole candidate-measurement stage; keeps the pick on time. */
        private const val CANDIDATE_BUDGET_MS = 25_000L

        /** Cap on newly decoded tracks per pick; results persist for later picks. */
        private const val MAX_CANDIDATE_ANALYSES = 8

        private const val HISTORY_FOR_AUTOPLAY = 12

        // Only applicationContext is retained by this process-wide singleton.
        @android.annotation.SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: MusicController? = null

        fun getInstance(context: Context): MusicController {
            return instance ?: synchronized(this) {
                instance ?: MusicController(context.applicationContext).also { instance = it }
            }
        }
    }
}

/**
 * Resolve the state-queue index of the item the player just transitioned to.
 *
 * The controller's own index is authoritative when it agrees with the media id:
 * queues may legitimately contain the same song more than once, and resolving
 * by id alone would point at the first (already played) occurrence.
 */
internal fun resolveQueueIndex(
    queue: List<Song>,
    mediaId: String?,
    controllerIndex: Int
): Int {
    if (mediaId == null) return -1
    if (controllerIndex in queue.indices && queue[controllerIndex].id == mediaId) {
        return controllerIndex
    }
    return queue.indexOfFirst { it.id == mediaId }
}
