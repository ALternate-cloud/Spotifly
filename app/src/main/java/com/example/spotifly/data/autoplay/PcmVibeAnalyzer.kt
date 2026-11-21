package com.example.spotifly.data.autoplay

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Measures energy, tempo, brightness, tonal colour and pulse strength from
 * decoded mono PCM.
 *
 * This is deliberately small and dependency-free: a radix-2 FFT, spectral
 * summary statistics and onset autocorrelation. It is not a replacement for a
 * trained audio model, but it turns the actual recording into features instead
 * of guessing from a title, and it runs on-device with no network.
 *
 * Keep this file free of Android imports so the maths is unit-testable on the JVM.
 */
object PcmVibeAnalyzer {

    const val TARGET_SAMPLE_RATE = 8_000

    private const val FFT_SIZE = 1024
    private const val HOP = 256
    private const val MAX_ANALYSIS_SECONDS = 20
    private const val MIN_BPM = 60.0
    private const val MAX_BPM = 180.0
    private const val TYPICAL_BPM = 110.0

    private val MAJOR_PROFILE = doubleArrayOf(
        6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88
    )
    private val MINOR_PROFILE = doubleArrayOf(
        6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17
    )

    fun analyze(samples: ShortArray, sampleRate: Int): VibeVector {
        if (samples.isEmpty() || sampleRate <= 0) return VibeVector()
        val usable = min(samples.size, sampleRate * MAX_ANALYSIS_SECONDS)
        if (usable < FFT_SIZE) {
            return VibeVector(energy = rmsEnergy(samples, usable))
        }

        val window = DoubleArray(FFT_SIZE) { index ->
            0.5 - 0.5 * cos(2.0 * PI * index / (FFT_SIZE - 1))
        }
        val re = DoubleArray(FFT_SIZE)
        val im = DoubleArray(FFT_SIZE)
        val previousMagnitude = DoubleArray(FFT_SIZE / 2)

        val onsets = ArrayList<Double>(usable / HOP)
        val chroma = DoubleArray(12)
        var centroidSum = 0.0
        var flatnessSum = 0.0
        var frames = 0
        var position = 0

        while (position + FFT_SIZE <= usable) {
            for (i in 0 until FFT_SIZE) {
                re[i] = (samples[position + i] / 32768.0) * window[i]
                im[i] = 0.0
            }
            fft(re, im)

            var magnitudeSum = 0.0
            var weightedFrequency = 0.0
            var logSum = 0.0
            var flux = 0.0
            val binCount = FFT_SIZE / 2

            for (bin in 1 until binCount) {
                val magnitude = sqrt(re[bin] * re[bin] + im[bin] * im[bin])
                magnitudeSum += magnitude
                logSum += ln(magnitude + 1e-9)
                val frequency = bin.toDouble() * sampleRate / FFT_SIZE
                weightedFrequency += frequency * magnitude

                if (frequency in 65.0..2_000.0) {
                    chroma[pitchClass(frequency)] += magnitude
                }
                val delta = magnitude - previousMagnitude[bin]
                if (delta > 0) flux += delta
                previousMagnitude[bin] = magnitude
            }

            if (magnitudeSum > 1e-9) {
                val centroid = weightedFrequency / magnitudeSum
                centroidSum += ((centroid - 500.0) / 3_500.0).coerceIn(0.0, 1.0)
                val flatness = exp(logSum / binCount) / (magnitudeSum / binCount + 1e-9)
                flatnessSum += flatness.coerceIn(0.0, 1.0)
            }

            onsets.add(flux)
            frames++
            position += HOP
        }

        if (frames == 0) return VibeVector(energy = rmsEnergy(samples, usable))

        val energy = rmsEnergy(samples, usable)
        val brightness = (centroidSum / frames).coerceIn(0.0, 1.0)
        val flatness = (flatnessSum / frames).coerceIn(0.0, 1.0)
        val pulse = pulseStrength(onsets, sampleRate)
        val tempo = tempoFromOnsets(onsets, sampleRate)
        val valence = valenceFromChroma(chroma)
        val acousticness = (0.60 * (1.0 - flatness) + 0.40 * (1.0 - brightness)).coerceIn(0.02, 0.98)
        val danceability = (0.15 + 0.75 * pulse.value).coerceIn(0.05, 0.95)

        return VibeVector(
            energy = energy,
            valence = valence,
            tempo = tempo,
            acousticness = acousticness,
            danceability = danceability
        ).clamped()
    }

    private fun rmsEnergy(samples: ShortArray, size: Int): Double {
        if (size <= 0) return 0.02
        var sum = 0.0
        for (i in 0 until size) {
            val value = samples[i] / 32768.0
            sum += value * value
        }
        val rms = sqrt(sum / size).coerceAtLeast(1e-7)
        val dbfs = 20.0 * log10(rms)
        return ((dbfs + 50.0) / 45.0).coerceIn(0.02, 0.98)
    }

    private data class Pulse(val value: Double, val bpm: Double)

    /**
     * Comb-filter the onset envelope over 60..180 BPM. Checking the first three
     * beat multiples avoids half/double-tempo errors, and a gentle log-normal
     * prior keeps a steady 120 BPM click from being reported as 60.
     */
    private fun tempoFromOnsets(onsets: List<Double>, sampleRate: Int): Double =
        pulseStrength(onsets, sampleRate).let { pulse ->
            if (pulse.value <= 0.02) 0.5
            else ((pulse.bpm - MIN_BPM) / (MAX_BPM - MIN_BPM)).coerceIn(0.0, 1.0)
        }

    private fun pulseStrength(onsets: List<Double>, sampleRate: Int): Pulse {
        if (onsets.size < 8) return Pulse(0.0, 0.0)

        val fps = sampleRate.toDouble() / HOP
        val maxLag = ((fps * 60.0 / MIN_BPM) * 3.0).roundToInt() + 2
        val mean = onsets.average()
        val centered = DoubleArray(onsets.size) { onsets[it] - mean }
        val acf = DoubleArray(maxLag + 1)
        for (lag in 0..maxLag) {
            var sum = 0.0
            for (i in lag until centered.size) sum += centered[i] * centered[i - lag]
            acf[lag] = sum / (centered.size - lag).coerceAtLeast(1)
        }
        if (acf[0] <= 1e-12) return Pulse(0.0, 0.0)

        var bestBpm = 0.0
        var bestScore = 0.0
        var bpm = MIN_BPM
        while (bpm <= MAX_BPM) {
            val lag = fps * 60.0 / bpm
            val first = acf[sampleAt(acf, lag)]
            val second = acf[sampleAt(acf, lag * 2)]
            val third = acf[sampleAt(acf, lag * 3)]
            val pulse = (first + 0.5 * second + 0.25 * third) / 1.75
            val prior = exp(-0.5 * (ln(bpm / TYPICAL_BPM) / 0.35) * (ln(bpm / TYPICAL_BPM) / 0.35))
            val score = pulse * (0.6 + 0.4 * prior)
            if (score > bestScore) {
                bestScore = score
                bestBpm = bpm
            }
            bpm += 1.0
        }

        val normalisedPulse = (bestScore / acf[0]).coerceIn(0.0, 1.0)
        return Pulse(normalisedPulse, bestBpm)
    }

    private fun sampleAt(values: DoubleArray, index: Double): Int =
        index.roundToInt().coerceIn(0, values.lastIndex)

    /**
     * Krumhansl-Schmuckler style key estimate. A major third/fifth pattern lifts
     * valence, a minor pattern lowers it; the correlation margin sets confidence.
     */
    private fun valenceFromChroma(chroma: DoubleArray): Double {
        val total = chroma.sum()
        if (total <= 1e-9) return 0.5

        val mean = chroma.average()
        val centred = DoubleArray(12) { chroma[it] - mean }
        val norm = sqrt(centred.sumOf { it * it })
        if (norm <= 1e-9) return 0.5

        var bestMajor = -1.0
        var bestMinor = -1.0
        for (tonic in 0 until 12) {
            bestMajor = maxOf(bestMajor, correlation(centred, norm, rotate(MAJOR_PROFILE, tonic)))
            bestMinor = maxOf(bestMinor, correlation(centred, norm, rotate(MINOR_PROFILE, tonic)))
        }

        val margin = (bestMajor - bestMinor).coerceIn(-1.0, 1.0)
        return (0.5 + 0.5 * margin).coerceIn(0.05, 0.95)
    }

    private fun rotate(profile: DoubleArray, tonic: Int): DoubleArray {
        val mean = profile.average()
        val rotated = DoubleArray(12) { index -> profile[(index - tonic + 12) % 12] - mean }
        val norm = sqrt(rotated.sumOf { it * it }).coerceAtLeast(1e-9)
        return DoubleArray(12) { rotated[it] / norm }
    }

    private fun correlation(centred: DoubleArray, norm: Double, unitProfile: DoubleArray): Double {
        var sum = 0.0
        for (i in 0 until 12) sum += centred[i] * unitProfile[i]
        return (sum / norm).coerceIn(-1.0, 1.0)
    }

    private fun pitchClass(frequency: Double): Int {
        val midi = 69.0 + 12.0 * (ln(frequency / 440.0) / ln(2.0))
        return ((midi.roundToInt() % 12) + 12) % 12
    }

    /** In-place iterative radix-2 Cooley-Tukey FFT. */
    private fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }

        var length = 2
        while (length <= n) {
            val angle = -2.0 * PI / length
            val wRe = cos(angle)
            val wIm = sin(angle)
            var start = 0
            while (start < n) {
                var curRe = 1.0
                var curIm = 0.0
                for (k in 0 until length / 2) {
                    val evenRe = re[start + k]
                    val evenIm = im[start + k]
                    val oddRe = re[start + k + length / 2] * curRe - im[start + k + length / 2] * curIm
                    val oddIm = re[start + k + length / 2] * curIm + im[start + k + length / 2] * curRe
                    re[start + k] = evenRe + oddRe
                    im[start + k] = evenIm + oddIm
                    re[start + k + length / 2] = evenRe - oddRe
                    im[start + k + length / 2] = evenIm - oddIm
                    val nextRe = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe
                    curRe = nextRe
                }
                start += length
            }
            length = length shl 1
        }
    }
}
