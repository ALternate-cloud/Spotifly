package com.example.spotifly.data.autoplay

import androidx.core.net.toUri
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.example.spotifly.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Decodes real audio to mono PCM, downsamples it to 8 kHz and hands it to
 * [PcmVibeAnalyzer].
 *
 * Sources come in priority order:
 * 1. local/downloaded file (offline-proof, cheapest)
 * 2. the song's progressive streaming URL — the first ~seconds of audio are
 *    fetched and decoded so catalogue tracks get measured vectors instead of
 *    the metadata fallback. Callers gate remote decoding on data-saver and
 *    offline preferences because it costs bandwidth.
 *
 * Only the first ~24 seconds are captured: that is enough for a stable energy,
 * timbre, tempo and key estimate, and it keeps analysis to a couple of seconds
 * per file instead of decoding an entire album track.
 *
 * This is the "real audio features" path: title/keyword guesses are only used
 * when a track cannot be decoded at all (DRM, codec unavailable, no network).
 * Every failure returns null so autoplay silently falls back to metadata
 * instead of breaking playback.
 */
object AudioVibeAnalyzer {

    private const val MAX_CAPTURE_SECONDS = 24
    /** Local files decode fast; network streams rebuffer, so a smaller cap. */
    const val DECODE_TIMEOUT_MS = 20_000L
    const val STREAM_TIMEOUT_MS = 11_000L

    /**
     * True when the song points at a source this process can attempt to decode
     * (a local file, or a progressive HTTP(S) stream).
     */
    fun isAnalyzable(context: Context, song: Song): Boolean =
        hasLocalSource(context, song) || streamUrlOf(song) != null

    /** True when the song points at a file this process can read offline. */
    fun hasLocalSource(context: Context, song: Song): Boolean {
        if (song.isPodcast) return false
        return resolveUri(context, song) != null
    }

    /** Progressive http(s) URL of the recording, when the catalogue exposed one. */
    fun streamUrlOf(song: Song): String? =
        song.streamUrl?.trim()?.takeIf {
            it.startsWith("http://") || it.startsWith("https://")
        }

    suspend fun analyze(
        context: Context,
        song: Song,
        timeoutMs: Long = DECODE_TIMEOUT_MS
    ): VibeVector? = withContext(Dispatchers.IO) {
        val local = resolveUri(context, song) ?: return@withContext streamAnalysis(song, timeoutMs)
        runCatching { decodeUri(context, local, timeoutMs) }
            .getOrNull()
            ?.let { pcm -> PcmVibeAnalyzer.analyze(pcm.samples, pcm.sampleRate) }
            ?: streamAnalysis(song, timeoutMs)
    }

    /** Fallback: bounded decode of the front of the streaming URL. */
    private fun streamAnalysis(song: Song, timeoutMs: Long): VibeVector? {
        val url = streamUrlOf(song) ?: return null
        return runCatching {
            when (val pcm = decodeSource(opener = { it.setDataSource(url) }, timeoutMs = timeoutMs)) {
                null -> null
                else -> PcmVibeAnalyzer.analyze(pcm.samples, pcm.sampleRate)
            }
        }.getOrNull()
    }

    private data class Pcm(val samples: ShortArray, val sampleRate: Int)

    private fun resolveUri(context: Context, song: Song): Uri? {
        val path = song.localFilePath
        if (!path.isNullOrBlank()) {
            val file = File(path.removePrefix("file://"))
            if (file.exists()) return Uri.fromFile(file)
        }
        val content = song.contentUriString
        if (!content.isNullOrBlank()) {
            val uri = (content).toUri()
            if (uri.scheme != "file" || uri.path?.let { File(it).exists() } == true) {
                return uri
            }
        }
        // A downloaded copy may exist even when the queued Song is the original
        // streaming object; the player resolves the same file by id.
        val cleanId = song.id.replace(Regex("[^a-zA-Z0-9_]"), "_")
        val offlineFile = File(context.filesDir, "offline_music/$cleanId.m4a")
        if (offlineFile.exists() && offlineFile.length() > 0) {
            return Uri.fromFile(offlineFile)
        }
        return null
    }

    private fun decodeUri(context: Context, uri: Uri, timeoutMs: Long): Pcm? =
        decodeSource(
            timeoutMs = timeoutMs,
            opener = { extractor ->
                if (uri.scheme == "content") {
                    extractor.setDataSource(context, uri, null)
                } else {
                    val path = uri.path ?: error("no path for $uri")
                    extractor.setDataSource(path)
                }
            }
        )

    private fun decodeSource(opener: (MediaExtractor) -> Unit, timeoutMs: Long): Pcm? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            opener(extractor)

            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: return null
            extractor.selectTrack(trackIndex)

            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            var sampleRate = format.optInt(MediaFormat.KEY_SAMPLE_RATE, 44_100)
                .takeIf { it > 0 } ?: 44_100
            var channels = format.optInt(MediaFormat.KEY_CHANNEL_COUNT, 2)
                .coerceIn(1, 8)

            codec = MediaCodec.createDecoderByType(mime).apply {
                configure(format, null, null, 0)
                start()
            }

            val maxFrames = sampleRate * MAX_CAPTURE_SECONDS
            var mono = FloatArray(maxFrames)
            var monoCount = 0
            val info = MediaCodec.BufferInfo()
            val deadline = System.currentTimeMillis() + timeoutMs
            var inputDone = false
            var outputDone = false

            while (!outputDone && System.currentTimeMillis() < deadline) {
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(5_000)
                    if (inputIndex >= 0) {
                        val input = codec.getInputBuffer(inputIndex)
                        val size = if (input != null) extractor.readSampleData(input, 0) else -1
                        if (size < 0) {
                            codec.queueInputBuffer(
                                inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(info, 5_000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val output = codec.outputFormat
                        sampleRate = output.optInt(MediaFormat.KEY_SAMPLE_RATE, sampleRate)
                            .takeIf { it > 0 } ?: sampleRate
                        channels = output.optInt(MediaFormat.KEY_CHANNEL_COUNT, channels)
                            .coerceIn(1, 8)
                    }

                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit

                    else -> {
                        if (outputIndex >= 0) {
                            val buffer = codec.getOutputBuffer(outputIndex)
                            if (info.size > 0 && buffer != null) {
                                buffer.position(info.offset)
                                buffer.limit(info.offset + info.size)
                                val shorts = buffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                                while (shorts.hasRemaining() && monoCount < maxFrames) {
                                    var sum = 0.0
                                    var taken = 0
                                    while (taken < channels && shorts.hasRemaining()) {
                                        sum += shorts.get() / 32768.0
                                        taken++
                                    }
                                    if (taken == 0) break
                                    mono[monoCount++] = (sum / taken).toFloat()
                                }
                            }
                            codec.releaseOutputBuffer(outputIndex, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                outputDone = true
                            }
                        }
                    }
                }

                if (monoCount >= maxFrames) outputDone = true
            }

            if (monoCount < sampleRate / 2) return null
            return downsample(mono, monoCount, sampleRate)
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    /**
     * Box-filter down to [PcmVibeAnalyzer.TARGET_SAMPLE_RATE]. Averaging (rather
     * than picking every Nth sample) avoids aliasing high-frequency energy into
     * the band the spectral features care about.
     */
    private fun downsample(mono: FloatArray, count: Int, sampleRate: Int): Pcm {
        val target = PcmVibeAnalyzer.TARGET_SAMPLE_RATE
        if (sampleRate <= target) {
            val samples = ShortArray(count) {
                (mono[it] * 32767.0).roundToInt().coerceIn(-32768, 32767).toShort()
            }
            return Pcm(samples, sampleRate)
        }

        val step = sampleRate.toDouble() / target
        val outSize = (count / step).toInt().coerceAtLeast(1)
        val samples = ShortArray(outSize)
        for (i in 0 until outSize) {
            val start = (i * step).toInt()
            val end = max(start + 1, ((i + 1) * step).toInt()).coerceAtMost(count)
            var sum = 0.0
            for (j in start until end) sum += mono[j]
            samples[i] = (sum / (end - start) * 32767.0)
                .roundToInt()
                .coerceIn(-32768, 32767)
                .toShort()
        }
        return Pcm(samples, target)
    }

    private fun MediaFormat.optInt(key: String, fallback: Int): Int =
        if (containsKey(key)) runCatching { getInteger(key) }.getOrDefault(fallback) else fallback
}
