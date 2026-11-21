package com.example.spotifly.player

import androidx.core.content.edit
import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Audio behaviour that belongs to the app rather than to a single playback session:
 * the settings Spotify exposes under Playback and Equalizer. Stored in its own
 * preferences file so the playback service can read it before the UI exists.
 */
object PlaybackTuning {

    private const val PREFS = "spotifly_audio"
    private const val KEY_CROSSFADE = "crossfade_ms"
    private const val KEY_SKIP_SILENCE = "skip_silence"
    private const val KEY_MONO_AUDIO = "mono_audio"
    private const val KEY_NORMALIZE = "normalize_volume"
    private const val KEY_EQ_ENABLED = "eq_enabled"
    private const val KEY_EQ_PRESET = "eq_preset"
    private const val KEY_EQ_LEVELS = "eq_levels"
    private const val KEY_BASS = "bass_boost"
    private const val KEY_PODCAST_SPEED = "podcast_speed"

    const val MAX_CROSSFADE_MS = 12_000
    /** Android's equalizer exposes roughly +/-15 dB; we store millibels. */
    const val MAX_BAND_MILLIBELS = 1_500

    private var prefs: SharedPreferences? = null

    private val _crossfadeMs = MutableStateFlow(0)
    val crossfadeMs: StateFlow<Int> = _crossfadeMs.asStateFlow()

    private val _skipSilence = MutableStateFlow(false)
    val skipSilence: StateFlow<Boolean> = _skipSilence.asStateFlow()

    private val _normalizeVolume = MutableStateFlow(false)
    val normalizeVolume: StateFlow<Boolean> = _normalizeVolume.asStateFlow()

    /** Downmix stereo to one channel, applied when the playback service builds its player. */
    private val _monoAudio = MutableStateFlow(false)
    val monoAudio: StateFlow<Boolean> = _monoAudio.asStateFlow()

    private val _eqEnabled = MutableStateFlow(false)
    val eqEnabled: StateFlow<Boolean> = _eqEnabled.asStateFlow()

    private val _eqPreset = MutableStateFlow(0)
    val eqPreset: StateFlow<Int> = _eqPreset.asStateFlow()

    private val _bassBoost = MutableStateFlow(0)
    val bassBoost: StateFlow<Int> = _bassBoost.asStateFlow()

    /**
     * Spoken word gets its own remembered speed. Spotify keeps a speed per show; the
     * honest local equivalent is one speed for podcast playback that never leaks into
     * music, because nobody wants their albums at 1.5x.
     */
    private val _podcastSpeed = MutableStateFlow(1f)
    val podcastSpeed: StateFlow<Float> = _podcastSpeed.asStateFlow()

    /** Per-band gain in millibels, aligned with the device equalizer's band order. */
    private val _bandLevels = MutableStateFlow<IntArray>(IntArray(0))
    val bandLevels: StateFlow<IntArray> = _bandLevels.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _crossfadeMs.value = p.getInt(KEY_CROSSFADE, 0).coerceIn(0, MAX_CROSSFADE_MS)
        _skipSilence.value = p.getBoolean(KEY_SKIP_SILENCE, false)
        _normalizeVolume.value = p.getBoolean(KEY_NORMALIZE, false)
        _monoAudio.value = p.getBoolean(KEY_MONO_AUDIO, false)
        _eqEnabled.value = p.getBoolean(KEY_EQ_ENABLED, false)
        _eqPreset.value = p.getInt(KEY_EQ_PRESET, 0)
        _bassBoost.value = p.getInt(KEY_BASS, 0)
        _podcastSpeed.value = p.getFloat(KEY_PODCAST_SPEED, 1f).coerceIn(0.5f, 3f)
        _bandLevels.value = decodeLevels(p.getString(KEY_EQ_LEVELS, null))
    }

    val isReady: Boolean get() = prefs != null

    fun setCrossfadeMs(value: Int) {
        val clamped = value.coerceIn(0, MAX_CROSSFADE_MS)
        _crossfadeMs.value = clamped
        prefs?.edit { putInt(KEY_CROSSFADE, clamped) }
    }

    fun setSkipSilence(enabled: Boolean) {
        _skipSilence.value = enabled
        prefs?.edit { putBoolean(KEY_SKIP_SILENCE, enabled) }
    }

    fun setNormalizeVolume(enabled: Boolean) {
        _normalizeVolume.value = enabled
        prefs?.edit { putBoolean(KEY_NORMALIZE, enabled) }
    }

    fun setMonoAudio(enabled: Boolean) {
        _monoAudio.value = enabled
        prefs?.edit { putBoolean(KEY_MONO_AUDIO, enabled) }
    }

    fun setEqEnabled(enabled: Boolean) {
        _eqEnabled.value = enabled
        prefs?.edit { putBoolean(KEY_EQ_ENABLED, enabled) }
    }

    fun setEqPreset(preset: Int) {
        _eqPreset.value = preset
        prefs?.edit { putInt(KEY_EQ_PRESET, preset) }
    }

    fun setBassBoost(strength: Int) {
        val clamped = strength.coerceIn(0, 1000)
        _bassBoost.value = clamped
        prefs?.edit { putInt(KEY_BASS, clamped) }
    }

    fun setBandLevels(levels: IntArray) {
        _bandLevels.value = levels
        prefs?.edit { putString(KEY_EQ_LEVELS, levels.joinToString(",")) }
    }

    fun setPodcastSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 3f)
        _podcastSpeed.value = clamped
        prefs?.edit { putFloat(KEY_PODCAST_SPEED, clamped) }
    }

    private fun decodeLevels(raw: String?): IntArray {
        if (raw.isNullOrBlank()) return IntArray(0)
        return raw.split(",").mapNotNull { it.trim().toIntOrNull() }.toIntArray()
    }
}
