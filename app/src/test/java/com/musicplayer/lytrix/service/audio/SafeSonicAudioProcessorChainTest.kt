package com.musicplayer.lytrix.service.audio

import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.audio.AudioProcessingPipeline
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import com.google.common.collect.ImmutableList
import com.musicplayer.lytrix.eq.audio.CustomEqualizerAudioProcessor
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow

class SafeSonicAudioProcessorChainTest {

    @Test
    fun drainingAcrossPitchSemitonesTerminatesQuicklyWithoutEosLoop() {
        val norm = VolumeNormalizationAudioProcessor()
        val eq = CustomEqualizerAudioProcessor()
        val silence = SilenceDetectorAudioProcessor(onLongSilence = {})
        val skipSilence = SilenceSkippingAudioProcessor(2_000_000L, 20_000L, 256.toShort())
        val sonic = SonicAudioProcessor()
        val chain = SafeSonicAudioProcessorChain(
            arrayOf(norm, eq, silence),
            skipSilence,
            sonic
        )
        val pipeline = AudioProcessingPipeline(ImmutableList.copyOf(chain.audioProcessors))
        val format = AudioProcessor.AudioFormat(44_100, 2, C.ENCODING_PCM_16BIT)

        for (semitones in listOf(0, 4, 12, -2, -3, -4, -5, -6, -12)) {
            val pitch = 2.0.pow(semitones / 12.0).toFloat()
            chain.applyPlaybackParameters(PlaybackParameters(1.0f, pitch))
            pipeline.configure(format)
            pipeline.flush()

            val pcm = ByteBuffer.allocateDirect(4096).order(ByteOrder.nativeOrder())
            repeat(1024) { idx ->
                val sample = ((idx % 128) * 120).toShort()
                pcm.putShort(sample)
                pcm.putShort(sample)
            }
            pcm.flip()

            var safety = 0
            while (pcm.hasRemaining() && safety++ < 200) {
                pipeline.queueInput(pcm)
                while (pipeline.getOutput().hasRemaining() && safety++ < 200) {
                    // consume
                }
            }

            pipeline.queueEndOfStream()
            var drainSteps = 0
            while (!pipeline.isEnded && drainSteps < 200) {
                val out = pipeline.getOutput()
                out.position(out.limit())
                drainSteps++
            }
            assertTrue(
                "Pipeline did not reach EOS cleanly for semitones=$semitones (drainSteps=$drainSteps)",
                pipeline.isEnded
            )
        }
        pipeline.reset()
    }
}
