package com.musicplayer.lytrix.eq.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.musicplayer.lytrix.eq.data.ParametricEQ
import com.musicplayer.lytrix.eq.data.ParametricEQBand
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.tanh

/**
 * Applies Parametric & AutoEq profiles via cascaded biquad filters inside ExoPlayer's audio pipeline.
 */
@UnstableApi
class CustomEqualizerAudioProcessor : BaseAudioProcessor() {

    private class FilterState(
        val enabled: Boolean,
        val preampGain: Double,
        val filters: Array<BiquadFilter>
    )

    private var sampleRate = 0
    private var channelCount = 0
    private var pendingProfile: ParametricEQ? = null
    private var activeParametricEq: ParametricEQ? = null

    @Volatile
    private var state = FilterState(enabled = false, preampGain = 1.0, filters = emptyArray())

    @Synchronized
    fun applyProfile(parametricEQ: ParametricEQ) {
        activeParametricEq = parametricEQ
        if (sampleRate == 0) {
            pendingProfile = parametricEQ
            return
        }
        val filters = buildFilters(parametricEQ.bands, sampleRate)
        val preampGain = 10.0.pow(parametricEQ.preamp / 20.0)
        state = FilterState(enabled = true, preampGain = preampGain, filters = filters)
        pendingProfile = null
        Timber.tag(TAG).d("Applied EQ profile: ${filters.size} filters, ${parametricEQ.preamp} dB preamp")
    }

    @Synchronized
    fun disable() {
        pendingProfile = null
        activeParametricEq = null
        state = FilterState(enabled = false, preampGain = 1.0, filters = emptyArray())
        Timber.tag(TAG).d("Equalizer disabled")
    }

    fun isEnabled(): Boolean = state.enabled

    private fun buildFilters(bands: List<ParametricEQBand>, rate: Int): Array<BiquadFilter> {
        return bands
            .asSequence()
            .filter { it.enabled && it.frequency > 0.0 && it.frequency < rate / 2.0 && abs(it.gain) > 0.001 }
            .map { band ->
                BiquadFilter(
                    sampleRate = rate,
                    frequency = band.frequency,
                    gain = band.gain,
                    q = band.q,
                    filterType = band.filterType
                )
            }
            .toList()
            .toTypedArray()
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        // Bypass cleanly rather than throwing on unsupported encodings or multi-channel layouts
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount !in 1..2) {
            sampleRate = 0
            channelCount = 0
            return AudioProcessor.AudioFormat.NOT_SET
        }

        val prevRate = sampleRate
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount

        val currentPending = pendingProfile
        if (currentPending != null) {
            applyProfile(currentPending)
        } else if (state.enabled && activeParametricEq != null && prevRate != sampleRate) {
            applyProfile(activeParametricEq!!)
        } else if (state.enabled) {
            state.filters.forEach { it.reset() }
        }

        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return

        val snapshot = state
        val output = replaceOutputBuffer(inputBuffer.remaining())
        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        output.order(ByteOrder.LITTLE_ENDIAN)

        if (!snapshot.enabled || snapshot.filters.isEmpty()) {
            output.put(inputBuffer)
        } else {
            processAudioBuffer(inputBuffer, output, snapshot)
        }
        output.flip()
    }

    private fun processAudioBuffer(input: ByteBuffer, output: ByteBuffer, snapshot: FilterState) {
        val filters = snapshot.filters
        val preampGain = snapshot.preampGain
        val frameCount = input.remaining() / (2 * channelCount)

        when (channelCount) {
            1 -> {
                repeat(frameCount) {
                    var sample = input.getShort().toDouble() / 32768.0
                    for (filter in filters) {
                        sample = filter.processMono(sample)
                    }
                    output.putShort(softLimitToPcm16(sample * preampGain))
                }
            }
            2 -> {
                repeat(frameCount) {
                    var left = input.getShort().toDouble() / 32768.0
                    var right = input.getShort().toDouble() / 32768.0
                    for (filter in filters) {
                        left = filter.processLeft(left)
                        right = filter.processRight(right)
                    }
                    output.putShort(softLimitToPcm16(left * preampGain))
                    output.putShort(softLimitToPcm16(right * preampGain))
                }
            }
        }
    }

    private fun softLimitToPcm16(normalizedSample: Double): Short {
        val limited = if (abs(normalizedSample) > 0.92) {
            val sign = if (normalizedSample >= 0.0) 1.0 else -1.0
            val excess = (abs(normalizedSample) - 0.92) / 0.08
            sign * (0.92 + 0.08 * tanh(excess))
        } else {
            normalizedSample
        }
        return (limited * 32768.0)
            .coerceIn(-32768.0, 32767.0)
            .toInt()
            .toShort()
    }

    override fun onFlush() {
        state.filters.forEach { it.reset() }
    }

    override fun onReset() {
        sampleRate = 0
        channelCount = 0
        state.filters.forEach { it.reset() }
    }

    private companion object {
        const val TAG = "CustomEqualizerAudioProcessor"
    }
}
