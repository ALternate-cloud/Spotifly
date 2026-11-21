@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.spotifly.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Averages every channel of 16-bit PCM into a single mono channel. Used for the
 * "Mono audio" accessibility setting, so people listening on one earbud hear both
 * sides of a stereo mix instead of half of it.
 */
class MonoDownmixAudioProcessor : BaseAudioProcessor() {

    private var downmix = false
    private var inputChannels = 1

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        inputChannels = inputAudioFormat.channelCount.coerceAtLeast(1)
        downmix = inputChannels > 1 && inputAudioFormat.encoding == C.ENCODING_PCM_16BIT
        return if (downmix) {
            AudioProcessor.AudioFormat(inputAudioFormat.sampleRate, 1, C.ENCODING_PCM_16BIT)
        } else {
            // Already mono, or a PCM encoding this processor does not rewrite.
            inputAudioFormat
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!downmix) {
            val passthrough = replaceOutputBuffer(inputBuffer.remaining())
            passthrough.put(inputBuffer)
            passthrough.flip()
            return
        }

        val frameCount = inputBuffer.remaining() / (2 * inputChannels)
        val output = replaceOutputBuffer(frameCount * 2)
        val input = inputBuffer.order(ByteOrder.nativeOrder()).asShortBuffer()
        val mono = output.asShortBuffer()
        while (input.remaining() >= inputChannels) {
            var sum = 0
            repeat(inputChannels) { sum += input.get().toInt() }
            mono.put((sum / inputChannels).toShort())
        }
        inputBuffer.position(inputBuffer.limit())
        output.flip()
    }

    override fun onReset() {
        downmix = false
        inputChannels = 1
    }
}

/**
 * Swaps in a mono audio sink at player build time. Channel layout is fixed when
 * the service creates its player, so toggling the setting takes effect on the
 * next playback service start (the same contract as skip-silence).
 */
class MonoRenderersFactory(context: Context) : DefaultRenderersFactory(context) {

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean
    ): AudioSink {
        return DefaultAudioSink.Builder(context)
            .setAudioProcessors(arrayOf(MonoDownmixAudioProcessor()))
            // The downmix works on 16-bit PCM; forcing integer output keeps the
            // processor in the pipeline on devices that would otherwise use float.
            .setEnableFloatOutput(false)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .build()
    }
}
