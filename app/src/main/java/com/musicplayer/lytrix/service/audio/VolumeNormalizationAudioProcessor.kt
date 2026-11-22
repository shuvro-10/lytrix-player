package com.musicplayer.lytrix.service.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.tanh

/**
 * Hybrid PCM loudness normalizer supporting:
 * 1. Exact static metadata gain (YouTube loudnessDb / local ReplayGain / Opus R128 tags)
 * 2. Real-time adaptive RMS + soft peak limiter (for JioSaavn & untagged local files)
 */
@UnstableApi
class VolumeNormalizationAudioProcessor : BaseAudioProcessor() {
    private var encoding = C.ENCODING_INVALID
    private var bytesPerSample = 0
    private var sampleRate = 44_100
    private var channelCount = 2

    @Volatile
    var enabled = false
        set(value) {
            if (field != value) {
                field = value
                Timber.tag(TAG).d("Normalization processor enabled: $value")
            }
        }

    private data class GainState(val targetGainMb: Int, val linearGain: Double)

    @Volatile
    private var currentGain = GainState(0, 1.0)

    @Volatile
    private var adaptiveMode = false

    @Volatile
    private var adaptiveTargetLufs = -14.0

    @Volatile
    private var emaMeanSq = 0.0

    @Volatile
    private var analyzedDurationSec = 0.0

    @Volatile
    private var adaptiveLinearGain = 1.0

    @Volatile
    private var targetAdaptiveGain = 1.0

    @Synchronized
    fun setTargetGain(gainMb: Int) {
        adaptiveMode = false
        if (currentGain.targetGainMb != gainMb) {
            val linearGain = 10.0.pow(gainMb / 2000.0)
            currentGain = GainState(gainMb, linearGain)
            Timber.tag(TAG).d("Static target gain set to $gainMb mB (multiplier: $linearGain)")
        }
    }

    @Synchronized
    fun setAdaptiveTargetLufs(targetLufs: Float) {
        adaptiveTargetLufs = targetLufs.toDouble().coerceIn(-24.0, -5.0)
        if (!adaptiveMode) {
            adaptiveMode = true
            resetAdaptiveState()
        }
        Timber.tag(TAG).d("Adaptive RMS normalization enabled -> target $adaptiveTargetLufs LUFS")
    }

    @Synchronized
    fun resetAdaptiveState() {
        emaMeanSq = 0.0
        analyzedDurationSec = 0.0
        adaptiveLinearGain = 1.0
        targetAdaptiveGain = 1.0
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        encoding = inputAudioFormat.encoding
        sampleRate = inputAudioFormat.sampleRate.takeIf { it > 0 } ?: 44_100
        channelCount = inputAudioFormat.channelCount.takeIf { it > 0 } ?: 2
        bytesPerSample = when (encoding) {
            C.ENCODING_PCM_16BIT -> 2
            C.ENCODING_PCM_24BIT -> 3
            C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 4
            else -> {
                encoding = C.ENCODING_INVALID
                bytesPerSample = 0
                return AudioProcessor.AudioFormat.NOT_SET
            }
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return

        val output = replaceOutputBuffer(inputBuffer.remaining())
        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        output.order(ByteOrder.LITTLE_ENDIAN)

        if (!enabled) {
            output.put(inputBuffer)
            output.flip()
            return
        }

        val totalSamples = inputBuffer.remaining() / bytesPerSample
        if (totalSamples <= 0) {
            output.put(inputBuffer)
            output.flip()
            return
        }

        if (!adaptiveMode) {
            val gain = currentGain
            if (gain.targetGainMb == 0) {
                output.put(inputBuffer)
                output.flip()
                return
            }
            processStaticGain(inputBuffer, output, totalSamples, gain.linearGain)
            output.flip()
            return
        }

        val (startGain, endGain) = updateAdaptiveGainForBuffer(inputBuffer.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN), totalSamples)
        processRampedGain(inputBuffer, output, totalSamples, startGain, endGain)
        output.flip()
    }

    private fun updateAdaptiveGainForBuffer(readView: ByteBuffer, totalSamples: Int): Pair<Double, Double> {
        val prevGain = adaptiveLinearGain
        val stride = (totalSamples / 512).coerceAtLeast(1)
        var sumSq = 0.0
        var count = 0
        val startPos = readView.position()

        var idx = 0
        while (idx < totalSamples) {
            val norm = readNormalizedSampleAt(readView, startPos + idx * bytesPerSample)
            sumSq += norm * norm
            count++
            idx += stride
        }

        val frameCount = (totalSamples / channelCount).coerceAtLeast(1)
        val bufDurSec = frameCount.toDouble() / sampleRate.toDouble()
        val meanSq = if (count > 0) sumSq / count else 0.0

        // Ignore silent gaps/fades below -46 dBFS so background noise isn't boosted.
        if (meanSq >= SILENCE_THRESHOLD_MEAN_SQ) {
            analyzedDurationSec += bufDurSec
            val tauSec = if (analyzedDurationSec < 1.5) 0.45 else 2.2
            val alpha = (bufDurSec / (tauSec + bufDurSec)).coerceIn(0.001, 0.5)
            emaMeanSq = if (emaMeanSq <= 0.0) {
                meanSq
            } else {
                emaMeanSq + alpha * (meanSq - emaMeanSq)
            }

            val estimatedDbfs = 10.0 * log10(emaMeanSq.coerceAtLeast(1e-12))
            val desiredGainDb = (adaptiveTargetLufs - estimatedDbfs).coerceIn(-12.0, 6.0)
            targetAdaptiveGain = 10.0.pow(desiredGainDb / 20.0)
        }

        val smoothTau = if (targetAdaptiveGain < adaptiveLinearGain) 0.12 else 0.45
        val smoothAlpha = (bufDurSec / (smoothTau + bufDurSec)).coerceIn(0.01, 0.5)
        val nextGain = adaptiveLinearGain + smoothAlpha * (targetAdaptiveGain - adaptiveLinearGain)
        adaptiveLinearGain = nextGain
        return prevGain to nextGain
    }

    private fun readNormalizedSampleAt(buf: ByteBuffer, byteOffset: Int): Double {
        return when (encoding) {
            C.ENCODING_PCM_16BIT -> buf.getShort(byteOffset).toDouble() / 32768.0
            C.ENCODING_PCM_24BIT -> {
                val b0 = buf.get(byteOffset).toInt() and 0xFF
                val b1 = buf.get(byteOffset + 1).toInt() and 0xFF
                val b2 = buf.get(byteOffset + 2).toInt()
                val raw = (b2 shl 16) or (b1 shl 8) or b0
                raw.toDouble() / 8388608.0
            }
            C.ENCODING_PCM_32BIT -> buf.getInt(byteOffset).toDouble() / 2147483648.0
            C.ENCODING_PCM_FLOAT -> buf.getFloat(byteOffset).toDouble().coerceIn(-2.0, 2.0)
            else -> 0.0
        }
    }

    private fun processStaticGain(
        input: ByteBuffer,
        output: ByteBuffer,
        totalSamples: Int,
        linearGain: Double
    ) {
        processRampedGain(input, output, totalSamples, linearGain, linearGain)
    }

    private fun processRampedGain(
        input: ByteBuffer,
        output: ByteBuffer,
        totalSamples: Int,
        startGain: Double,
        endGain: Double
    ) {
        val delta = if (totalSamples > 1) (endGain - startGain) / (totalSamples - 1) else 0.0
        when (encoding) {
            C.ENCODING_PCM_16BIT -> {
                var i = 0
                while (i < totalSamples) {
                    val g = startGain + delta * i
                    val normalized = (input.getShort().toDouble() / 32768.0) * g
                    val limited = softLimit(normalized)
                    output.putShort((limited * 32767.0).coerceIn(-32768.0, 32767.0).toInt().toShort())
                    i++
                }
            }
            C.ENCODING_PCM_24BIT -> {
                var i = 0
                while (i < totalSamples) {
                    val g = startGain + delta * i
                    val b0 = input.get().toInt() and 0xFF
                    val b1 = input.get().toInt() and 0xFF
                    val b2 = input.get().toInt()
                    val sample = (b2 shl 16) or (b1 shl 8) or b0
                    val normalized = (sample.toDouble() / 8388608.0) * g
                    val limited = softLimit(normalized)
                    val processed = (limited * 8388607.0).coerceIn(-8388608.0, 8388607.0).toInt()
                    output.put((processed and 0xFF).toByte())
                    output.put(((processed shr 8) and 0xFF).toByte())
                    output.put(((processed shr 16) and 0xFF).toByte())
                    i++
                }
            }
            C.ENCODING_PCM_32BIT -> {
                var i = 0
                while (i < totalSamples) {
                    val g = startGain + delta * i
                    val normalized = (input.getInt().toDouble() / 2147483648.0) * g
                    val limited = softLimit(normalized)
                    output.putInt((limited * 2147483647.0).coerceIn(-2147483648.0, 2147483647.0).toLong().toInt())
                    i++
                }
            }
            C.ENCODING_PCM_FLOAT -> {
                var i = 0
                while (i < totalSamples) {
                    val g = startGain + delta * i
                    val limited = softLimit(input.getFloat().toDouble() * g)
                    output.putFloat(limited.toFloat().coerceIn(-1.0f, 1.0f))
                    i++
                }
            }
        }
    }

    /**
     * Soft-knee brickwall limiter above -1 dBFS (0.891) to prevent harsh clipping when boosting quiet tracks.
     */
    private fun softLimit(x: Double): Double {
        val ax = abs(x)
        if (ax <= LIMITER_THRESHOLD) return x
        val excess = ax - LIMITER_THRESHOLD
        val compressed = LIMITER_THRESHOLD + LIMITER_HEADROOM * tanh(excess / LIMITER_HEADROOM)
        return if (x >= 0.0) compressed else -compressed
    }

    override fun onReset() {
        encoding = C.ENCODING_INVALID
        bytesPerSample = 0
        resetAdaptiveState()
    }

    private companion object {
        const val TAG = "VolumeNormalizationProcessor"
        const val SILENCE_THRESHOLD_MEAN_SQ = 2.5118864e-5 // ~ -46 dBFS
        const val LIMITER_THRESHOLD = 0.891 // ~ -1 dBFS
        const val LIMITER_HEADROOM = 1.0 - LIMITER_THRESHOLD
    }
}
