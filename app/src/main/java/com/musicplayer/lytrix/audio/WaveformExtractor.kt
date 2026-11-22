package com.musicplayer.lytrix.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import com.musicplayer.lytrix.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Produces normalized 0..1 amplitude bars (`BARS_PER_SECOND` per second) for SoundCloud waveform rendering.
 * Decodes and caches real RMS envelopes for local/downloaded audio files and provides an instant
 * track-seeded harmonic envelope for un-cached remote streams.
 */
object WaveformExtractor {
    const val BARS_PER_SECOND = 4f
    private const val SAMPLE_WINDOW_COUNT = 160
    private val unsafeChars = Regex("[^a-zA-Z0-9._-]")

    suspend fun getOrCreateWaveform(
        context: Context,
        song: Song,
        durationMs: Long
    ): FloatArray = withContext(Dispatchers.IO) {
        val safeDuration = durationMs.coerceAtLeast(15_000L)
        val barCount = ((safeDuration / 1000f) * BARS_PER_SECOND).toInt().coerceIn(80, 1800)
        val cacheKey = buildCacheKey(song)
        val dir = File(context.cacheDir, "waveforms").apply { mkdirs() }
        val cacheFile = File(dir, "$cacheKey.wf")

        readCached(cacheFile, barCount)?.let { return@withContext it }

        val localUri = resolvePlayableLocalUri(song)
        if (localUri != null) {
            val decoded = runCatching { decodeLocalWaveform(context, localUri, barCount) }.getOrNull()
            if (decoded != null && decoded.size >= 32) {
                writeCached(cacheFile, decoded)
                return@withContext decoded
            }
        }

        synthesizeTrackEnvelope(song, barCount)
    }

    fun synthesizeInstantPreview(song: Song, durationMs: Long): FloatArray {
        val safeDuration = durationMs.coerceAtLeast(15_000L)
        val barCount = ((safeDuration / 1000f) * BARS_PER_SECOND).toInt().coerceIn(80, 1800)
        return synthesizeTrackEnvelope(song, barCount)
    }

    private fun buildCacheKey(song: Song): String {
        val raw = (song.originalId ?: "${song.id}_${song.title}_${song.duration}")
        return raw.replace(unsafeChars, "_").take(80)
    }

    private fun resolvePlayableLocalUri(song: Song): Uri? {
        val path = song.filePath
        if (!path.isNullOrBlank() && path.startsWith("/")) {
            val file = File(path)
            if (file.isFile && file.length() > 0L) return Uri.fromFile(file)
        }
        val uriStr = song.uriString
        if (uriStr.startsWith("content://") || uriStr.startsWith("file://")) {
            return Uri.parse(uriStr)
        }
        return null
    }

    private fun readCached(file: File, targetCount: Int): FloatArray? {
        if (!file.isFile || file.length() < 32L) return null
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
        val raw = FloatArray(bytes.size) { (bytes[it].toInt() and 0xFF) / 255f }
        return resample(raw, targetCount)
    }

    private fun writeCached(file: File, bars: FloatArray) {
        runCatching {
            val bytes = ByteArray(bars.size) { i ->
                (bars[i].coerceIn(0.08f, 1f) * 255f).toInt().coerceIn(0, 255).toByte()
            }
            file.writeBytes(bytes)
        }
    }

    private suspend fun decodeLocalWaveform(
        context: Context,
        uri: Uri,
        barCount: Int
    ): FloatArray? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            if (uri.scheme == "file" && uri.path != null) {
                extractor.setDataSource(uri.path!!)
            } else {
                extractor.setDataSource(context, uri, null)
            }

            var audioTrackIndex = -1
            var trackFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    trackFormat = fmt
                    break
                }
            }
            if (audioTrackIndex < 0 || trackFormat == null) return null

            val durationUs = if (trackFormat.containsKey(MediaFormat.KEY_DURATION)) {
                trackFormat.getLong(MediaFormat.KEY_DURATION)
            } else 0L
            if (durationUs <= 0L) return null

            extractor.selectTrack(audioTrackIndex)
            val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: return null
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(trackFormat, null, null, 0)
            codec.start()

            val windowPeaks = FloatArray(SAMPLE_WINDOW_COUNT)
            val info = MediaCodec.BufferInfo()
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT

            for (w in 0 until SAMPLE_WINDOW_COUNT) {
                coroutineContext.ensureActive()
                val seekUs = (durationUs * w) / SAMPLE_WINDOW_COUNT
                extractor.seekTo(seekUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                codec.flush()

                var maxWindowRms = 0f
                var decodedFrames = 0
                var sawInputEos = false
                var attempts = 0
                while (decodedFrames < 2 && attempts < 14) {
                    attempts++
                    if (!sawInputEos) {
                        val inIdx = codec.dequeueInputBuffer(4_000L)
                        if (inIdx >= 0) {
                            val inBuf = codec.getInputBuffer(inIdx)
                            val sampleSize = if (inBuf != null) extractor.readSampleData(inBuf, 0) else -1
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                sawInputEos = true
                            } else {
                                val pts = extractor.sampleTime.coerceAtLeast(0L)
                                codec.queueInputBuffer(inIdx, 0, sampleSize, pts, 0)
                                extractor.advance()
                            }
                        }
                    }

                    val outIdx = codec.dequeueOutputBuffer(info, 4_000L)
                    when {
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val outFmt = codec.outputFormat
                            pcmEncoding = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                                outFmt.containsKey(MediaFormat.KEY_PCM_ENCODING)
                            ) {
                                outFmt.getInteger(MediaFormat.KEY_PCM_ENCODING)
                            } else {
                                AudioFormat.ENCODING_PCM_16BIT
                            }
                        }
                        outIdx >= 0 -> {
                            val outBuf = codec.getOutputBuffer(outIdx)
                            if (outBuf != null && info.size > 0) {
                                outBuf.position(info.offset)
                                outBuf.limit(info.offset + info.size)
                                val rms = computeBufferRms(outBuf, info.size, pcmEncoding)
                                if (rms > maxWindowRms) maxWindowRms = rms
                                decodedFrames++
                            }
                            codec.releaseOutputBuffer(outIdx, false)
                        }
                    }
                }
                windowPeaks[w] = maxWindowRms
            }

            val peakMax = (windowPeaks.maxOrNull() ?: 0f).coerceAtLeast(0.02f)
            val normalized = FloatArray(windowPeaks.size) { i ->
                (0.14f + 0.86f * (windowPeaks[i] / peakMax)).coerceIn(0.12f, 1f)
            }
            return resample(normalized, barCount)
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun computeBufferRms(buf: java.nio.ByteBuffer, sizeBytes: Int, encoding: Int): Float {
        buf.order(ByteOrder.LITTLE_ENDIAN)
        var sumSq = 0.0
        var count = 0
        when (encoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> {
                val floats = sizeBytes / 4
                val stride = (floats / 64).coerceAtLeast(1)
                var i = 0
                while (i < floats) {
                    val v = buf.getFloat(buf.position() + i * 4).toDouble()
                    sumSq += v * v
                    count++
                    i += stride
                }
            }
            else -> {
                val shorts = sizeBytes / 2
                val stride = (shorts / 64).coerceAtLeast(1)
                var i = 0
                while (i < shorts) {
                    val v = buf.getShort(buf.position() + i * 2).toDouble() / 32768.0
                    sumSq += v * v
                    count++
                    i += stride
                }
            }
        }
        return if (count > 0) sqrt(sumSq / count).toFloat() else 0f
    }

    private fun resample(source: FloatArray, targetCount: Int): FloatArray {
        if (source.isEmpty()) return FloatArray(targetCount) { 0.35f }
        if (source.size == targetCount) return source
        return FloatArray(targetCount) { i ->
            val pos = i * (source.size - 1).toFloat() / (targetCount - 1).coerceAtLeast(1)
            val lo = pos.toInt().coerceIn(0, source.lastIndex)
            val hi = (lo + 1).coerceAtMost(source.lastIndex)
            val frac = pos - lo
            (source[lo] * (1f - frac) + source[hi] * frac).coerceIn(0.10f, 1f)
        }
    }

    private fun synthesizeTrackEnvelope(song: Song, barCount: Int): FloatArray {
        var seed = ((song.originalId ?: song.title).hashCode().toLong() shl 32) xor
            song.artist.hashCode().toLong() xor song.id
        fun nextRand(): Float {
            seed = seed * 6364136223846793005L + 1442695040888963407L
            return (((seed ushr 33) and 0xFFFFFFL).toFloat() / 0xFFFFFFL.toFloat())
        }

        val p1 = nextRand() * 6.28f
        val p2 = nextRand() * 6.28f
        return FloatArray(barCount) { idx ->
            val progress = idx.toFloat() / barCount.coerceAtLeast(1)
            // Realistic song structure macro-dynamics: intro ramp -> verses/choruses -> outro fade
            val macro = when {
                progress < 0.08f -> 0.25f + (progress / 0.08f) * 0.45f
                progress > 0.90f -> 0.72f - ((progress - 0.90f) / 0.10f) * 0.50f
                else -> 0.62f + 0.24f * sin(progress * 11f + p1) + 0.14f * cos(progress * 23f + p2)
            }
            val beatAccent = 0.78f + 0.22f * abs(sin(idx * 0.48f + p2))
            val micro = 0.76f + 0.48f * nextRand()
            (macro * beatAccent * micro).coerceIn(0.14f, 0.98f)
        }
    }
}
