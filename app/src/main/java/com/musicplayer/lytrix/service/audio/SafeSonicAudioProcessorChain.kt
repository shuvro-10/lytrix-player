package com.musicplayer.lytrix.service.audio

import androidx.media3.common.PlaybackParameters
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessorChain
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import java.nio.ByteBuffer

/**
 * Custom [AudioProcessorChain] that protects [SonicAudioProcessor] from Media3's repeated
 * `queueEndOfStream()` drain loop AND places always-active custom processors downstream of Sonic.
 *
 * Even in Media3 1.11.1, `AudioProcessingPipeline.processData()` invokes `queueEndOfStream()` on
 * downstream processors on every drain pass once an upstream processor has ended, while
 * `SonicAudioProcessor.queueEndOfStream()` is not idempotent (`Sonic.queueEndOfStream()` resets
 * `accumulatedSpeedAdjustmentError` and emits another silent frame per pass), which deadlocks
 * `ExoPlayer:Playback` inside `DefaultAudioSink.processBuffers()`.
 *
 * Keeping `SilenceSkippingAudioProcessor` immediately before the idempotency-guarded
 * `SonicAudioProcessor` preserves Media3's exact skipped-frame duration accounting, while placing
 * always-active custom processors (normalization, EQ, silence detector) downstream ensures Sonic
 * has no always-active upstream processors at all.
 */
@UnstableApi
class SafeSonicAudioProcessorChain(
    downstreamProcessors: Array<AudioProcessor>,
    private val silenceSkippingAudioProcessor: SilenceSkippingAudioProcessor,
    private val sonicAudioProcessor: SonicAudioProcessor
) : AudioProcessorChain {

    private val processors: Array<AudioProcessor> = Array(downstreamProcessors.size + 2) { index ->
        when (index) {
            0 -> silenceSkippingAudioProcessor
            1 -> IdempotentEndOfStreamAudioProcessor(sonicAudioProcessor)
            else -> downstreamProcessors[index - 2]
        }
    }

    override fun getAudioProcessors(): Array<AudioProcessor> = processors

    override fun applyPlaybackParameters(playbackParameters: PlaybackParameters): PlaybackParameters {
        sonicAudioProcessor.setSpeed(playbackParameters.speed)
        sonicAudioProcessor.setPitch(playbackParameters.pitch)
        return playbackParameters
    }

    override fun applySkipSilenceEnabled(skipSilenceEnabled: Boolean): Boolean {
        silenceSkippingAudioProcessor.setEnabled(skipSilenceEnabled)
        return skipSilenceEnabled
    }

    override fun getMediaDuration(playoutDuration: Long): Long {
        return if (sonicAudioProcessor.isActive) {
            sonicAudioProcessor.getMediaDuration(playoutDuration)
        } else {
            playoutDuration
        }
    }

    override fun getSkippedOutputFrameCount(): Long = silenceSkippingAudioProcessor.skippedFrames

    private class IdempotentEndOfStreamAudioProcessor(
        private val delegate: AudioProcessor
    ) : AudioProcessor {
        private var endOfStreamQueued = false

        override fun getDurationAfterProcessorApplied(durationUs: Long): Long =
            delegate.getDurationAfterProcessorApplied(durationUs)

        override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat =
            delegate.configure(inputAudioFormat)

        override fun isActive(): Boolean = delegate.isActive()

        override fun queueInput(inputBuffer: ByteBuffer) {
            delegate.queueInput(inputBuffer)
        }

        override fun queueEndOfStream() {
            if (endOfStreamQueued) return
            endOfStreamQueued = true
            delegate.queueEndOfStream()
        }

        override fun getOutput(): ByteBuffer = delegate.getOutput()

        override fun isEnded(): Boolean = delegate.isEnded()

        @Deprecated("Deprecated in Media3; kept for binary compatibility")
        override fun flush() {
            endOfStreamQueued = false
            @Suppress("DEPRECATION")
            delegate.flush()
        }

        override fun flush(streamMetadata: AudioProcessor.StreamMetadata) {
            endOfStreamQueued = false
            delegate.flush(streamMetadata)
        }

        override fun reset() {
            endOfStreamQueued = false
            delegate.reset()
        }
    }
}
