package com.example.spotifly.data.autoplay

import java.util.Random
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM tests for the dependency-free PCM feature extractor. */
class PcmVibeAnalyzerTest {

    private val sampleRate = 8_000

    private fun sine(
        frequency: Double,
        seconds: Int = 10,
        amplitude: Double = 0.4
    ): ShortArray {
        val size = sampleRate * seconds
        return ShortArray(size) { index ->
            (amplitude * sin(2.0 * PI * frequency * index / sampleRate) * 32_767.0)
                .toInt()
                .toShort()
        }
    }

    private fun noise(seconds: Int = 10): ShortArray {
        val random = Random(42)
        return ShortArray(sampleRate * seconds) {
            ((random.nextDouble() * 2.0 - 1.0) * 0.5 * 32_767.0).toInt().toShort()
        }
    }

    /** 120 BPM: a short decaying 1 kHz burst every 0.5 s. */
    private fun clickTrack(bpm: Int = 120, seconds: Int = 12): ShortArray {
        val samples = ShortArray(sampleRate * seconds)
        val period = sampleRate * 60 / bpm
        var beat = 0
        while (beat * period < samples.size) {
            val start = beat * period
            for (i in 0 until 240) {
                val index = start + i
                if (index >= samples.size) break
                val envelope = exp(-i / 45.0)
                samples[index] = (
                    sin(2.0 * PI * 1_000.0 * i / sampleRate) * envelope * 0.8 * 32_767.0
                    ).toInt().toShort()
            }
            beat++
        }
        return samples
    }

    @Test
    fun silence_hasLowEnergyAndNoPulse() {
        val vector = PcmVibeAnalyzer.analyze(ShortArray(sampleRate * 5), sampleRate)

        assertTrue("silence energy should be near the floor", vector.energy < 0.1)
        assertTrue("silence has no dance pulse", vector.danceability < 0.3)
    }

    @Test
    fun tonalAudioReadsMoreAcousticThanNoise() {
        val tone = PcmVibeAnalyzer.analyze(sine(500.0), sampleRate)
        val noisy = PcmVibeAnalyzer.analyze(noise(), sampleRate)

        assertTrue(
            "a pure tone should be more acoustic than white noise " +
                "(tone=${tone.acousticness}, noise=${noisy.acousticness})",
            tone.acousticness > noisy.acousticness + 0.2
        )
    }

    @Test
    fun steadyClicks_areDetectedNearTheirTempo() {
        val vector = PcmVibeAnalyzer.analyze(clickTrack(bpm = 120), sampleRate)

        // tempo is normalised over 60..180 BPM, so 120 BPM maps to ~0.5.
        assertTrue("expected ~120 BPM, got ${vector.tempo}", vector.tempo in 0.37..0.63)
        assertTrue("a click track should have a dance pulse", vector.danceability > 0.3)
    }

    @Test
    fun slowClicks_areDetectedNearTheirTempo() {
        val vector = PcmVibeAnalyzer.analyze(clickTrack(bpm = 80), sampleRate)

        // 80 BPM maps to (80 - 60) / 120 = 0.1667.
        assertTrue("expected ~80 BPM, got ${vector.tempo}", vector.tempo in 0.05..0.32)
    }

    @Test
    fun shortBuffer_doesNotCrash() {
        val vector = PcmVibeAnalyzer.analyze(ShortArray(64), sampleRate)

        assertTrue(vector.energy in 0.0..1.0)
        assertTrue(vector.tempo in 0.0..1.0)
    }

    @Test
    fun emptyAndInvalidInput_returnNeutralVector() {
        val empty = PcmVibeAnalyzer.analyze(ShortArray(0), sampleRate)
        val invalid = PcmVibeAnalyzer.analyze(ShortArray(1_000), 0)

        assertTrue(empty.energy in 0.0..1.0)
        assertTrue(invalid.energy in 0.0..1.0)
    }
}
