package com.musicplayer.lytrix.service.audio

import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Pass-through PCM detector that triggers [onLongSilence] once when a silent passage lasts longer than [minSilenceDurationUs],
 * and emits throttled normalized RMS levels via [onAudioLevel] for live UI waveform feedback.
 */
@UnstableApi
class SilenceDetectorAudioProcessor(
    private val minSilenceDurationUs: Long = 2_000_000L,
    private val silenceThreshold: Int = 256,
    private val onLongSilence: () -> Unit,
    private val onAudioLevel: ((Float) -> Unit)? = null,
) : BaseAudioProcessor() {

    private var sampleRate = 0
    private var channelCount = 0

    @Volatile
    var instantModeEnabled = false

    @Volatile
    private var consecutiveSilentFrames = 0L

    @Volatile
    private var inSilence = false

    private var notifiedThisSilence = false
    private var lastLevelEmitMs = 0L
    private var smoothedLevel = 0f

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount <= 0) {
            sampleRate = 0
            channelCount = 0
            return AudioProcessor.AudioFormat.NOT_SET
        }
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return

        if (onAudioLevel != null) {
            sampleLiveAudioLevel(inputBuffer)
        }

        if (instantModeEnabled && sampleRate > 0 && channelCount > 0) {
            detectSilence(inputBuffer)
        } else {
            clearSilenceState()
        }

        replaceOutputBuffer(inputBuffer.remaining()).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            put(inputBuffer)
            flip()
        }
    }

    private fun sampleLiveAudioLevel(inputBuffer: ByteBuffer) {
        val now = SystemClock.uptimeMillis()
        if (now - lastLevelEmitMs < 33L) return
        lastLevelEmitMs = now

        val remainingBytes = inputBuffer.remaining()
        if (remainingBytes < 16) return
        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        val basePos = inputBuffer.position()
        val sampleCount = remainingBytes / 2
        val stride = (sampleCount / 32).coerceAtLeast(1)
        var sumSq = 0.0
        var count = 0
        var idx = 0
        while (idx < sampleCount) {
            val s = inputBuffer.getShort(basePos + idx * 2).toDouble() / 32768.0
            sumSq += s * s
            count++
            idx += stride
        }
        val rms = if (count > 0) sqrt(sumSq / count).toFloat() else 0f
        val normalized = (rms * 3.2f).coerceIn(0f, 1f)
        smoothedLevel = if (normalized > smoothedLevel) {
            smoothedLevel * 0.45f + normalized * 0.55f
        } else {
            smoothedLevel * 0.78f + normalized * 0.22f
        }
        onAudioLevel?.invoke(smoothedLevel)
    }

    private fun detectSilence(inputBuffer: ByteBuffer) {
        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        val frameCount = inputBuffer.remaining() / (2 * channelCount)
        val basePosition = inputBuffer.position()

        repeat(frameCount) { frameIndex ->
            var framePeak = 0
            repeat(channelCount) { channelIndex ->
                val sampleOffset = basePosition + (frameIndex * channelCount + channelIndex) * 2
                framePeak = maxOf(framePeak, abs(inputBuffer.getShort(sampleOffset).toInt()))
            }

            if (framePeak < silenceThreshold) {
                consecutiveSilentFrames++
                if (consecutiveSilentFrames * 1_000_000L / sampleRate >= minSilenceDurationUs) {
                    inSilence = true
                    if (!notifiedThisSilence) {
                        notifiedThisSilence = true
                        onLongSilence()
                    }
                }
            } else {
                clearSilenceState()
            }
        }
    }

    private fun clearSilenceState() {
        consecutiveSilentFrames = 0L
        inSilence = false
        notifiedThisSilence = false
    }

    fun resetTracking() = clearSilenceState()

    fun isCurrentlySilent(): Boolean = inSilence

    override fun onFlush() = clearSilenceState()

    override fun onReset() {
        sampleRate = 0
        channelCount = 0
        clearSilenceState()
    }
}
