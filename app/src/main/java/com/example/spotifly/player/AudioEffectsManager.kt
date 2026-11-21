package com.example.spotifly.player

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.util.Log

/**
 * Wraps Android's audio effects on ExoPlayer's audio session. This is what makes the
 * clone behave like a real player rather than a stream viewer: a five-band equalizer,
 * bass boost, and loudness compensation.
 *
 * Effects are attached lazily, when the player actually starts producing audio, because
 * the audio session id is only valid from that moment. Every call is defensive: some
 * devices refuse an effect, some expose fewer bands, and none of that should break
 * playback.
 */
class AudioEffectsManager private constructor(context: Context) {

    private val appContext = context.applicationContext

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var loudness: LoudnessEnhancer? = null
    private var attachedSession = 0

    @Synchronized
    fun attach(audioSessionId: Int) {
        if (audioSessionId <= 0) return
        if (attachedSession == audioSessionId && equalizer != null) {
            applyAll()
            return
        }
        release()
        attachedSession = audioSessionId

        equalizer = runCatching { Equalizer(0, audioSessionId) }.getOrNull()
        bassBoost = runCatching { BassBoost(0, audioSessionId) }.getOrNull()
        loudness = runCatching { LoudnessEnhancer(audioSessionId) }.getOrNull()
        applyAll()
    }

    /** Re-reads [PlaybackTuning] and pushes it into the live effects. */
    @Synchronized
    fun applyAll() {
        PlaybackTuning.init(appContext)

        equalizer?.let { eq ->
            runCatching {
                eq.enabled = PlaybackTuning.eqEnabled.value
                if (PlaybackTuning.eqEnabled.value) {
                    val preset = PlaybackTuning.eqPreset.value
                    val bandCount = eq.numberOfBands.toInt()
                    val stored = PlaybackTuning.bandLevels.value
                    if (stored.size == bandCount) {
                        for (band in 0 until bandCount) {
                            eq.setBandLevel(band.toShort(), stored[band].toShort())
                        }
                    } else if (preset > 0 || preset == 0) {
                        val maxPreset = eq.numberOfPresets.toInt() - 1
                        if (preset in 0..maxPreset) {
                            eq.usePreset(preset.toShort())
                        }
                    }
                }
            }.onFailure { Log.d(TAG, "Equalizer unavailable: ${it.message}") }
        }

        bassBoost?.let { bb ->
            runCatching {
                val strength = PlaybackTuning.bassBoost.value
                bb.enabled = strength > 0
                if (strength > 0) bb.setStrength(strength.toShort())
            }.onFailure { Log.d(TAG, "BassBoost unavailable: ${it.message}") }
        }

        loudness?.let { le ->
            runCatching {
                val enabled = PlaybackTuning.normalizeVolume.value
                le.enabled = enabled
                if (enabled) le.setTargetGain(NORMALIZE_GAIN_MB)
            }.onFailure { Log.d(TAG, "LoudnessEnhancer unavailable: ${it.message}") }
        }
    }

    /** Band centre frequencies in Hz, for labelling the sliders. */
    @Synchronized
    fun bandFrequencies(): IntArray {
        val eq = equalizer ?: return IntArray(0)
        return runCatching {
            IntArray(eq.numberOfBands.toInt()) { band -> eq.getCenterFreq(band.toShort()) / 1000 }
        }.getOrDefault(IntArray(0))
    }

    @Synchronized
    fun bandLevelRange(): Pair<Int, Int> {
        val eq = equalizer ?: return -PlaybackTuning.MAX_BAND_MILLIBELS to PlaybackTuning.MAX_BAND_MILLIBELS
        return runCatching {
            val range = eq.bandLevelRange
            range[0].toInt() to range[1].toInt()
        }.getOrDefault(-PlaybackTuning.MAX_BAND_MILLIBELS to PlaybackTuning.MAX_BAND_MILLIBELS)
    }

    @Synchronized
    fun presetNames(): List<String> {
        val eq = equalizer ?: return emptyList()
        return runCatching {
            (0 until eq.numberOfPresets.toInt()).map { index ->
                runCatching { eq.getPresetName(index.toShort()) }.getOrDefault("Preset $index")
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun isEqualizerAvailable(): Boolean = equalizer != null

    @Synchronized
    fun release() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        runCatching { loudness?.release() }
        equalizer = null
        bassBoost = null
        loudness = null
        attachedSession = 0
    }

    companion object {
        private const val TAG = "AudioEffects"
        private const val NORMALIZE_GAIN_MB = 600

        @Volatile
        private var instance: AudioEffectsManager? = null

        fun getInstance(context: Context): AudioEffectsManager =
            instance ?: synchronized(this) {
                instance ?: AudioEffectsManager(context).also { instance = it }
            }
    }
}
