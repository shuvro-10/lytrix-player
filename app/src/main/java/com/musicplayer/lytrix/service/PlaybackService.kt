package com.musicplayer.lytrix.service

import android.content.Context
import android.content.SharedPreferences
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.musicplayer.lytrix.eq.LytrixEqualizerService
import com.musicplayer.lytrix.eq.audio.CustomEqualizerAudioProcessor
import com.musicplayer.lytrix.innertube.models.response.PlayerResponse
import com.musicplayer.lytrix.service.audio.LoudnessLevel
import com.musicplayer.lytrix.service.audio.SilenceDetectorAudioProcessor
import com.musicplayer.lytrix.service.audio.VolumeNormalizationAudioProcessor
import com.musicplayer.lytrix.service.audio.normalizationGainMb
import com.musicplayer.lytrix.service.audio.r128TrackGainToGainMb
import com.musicplayer.lytrix.service.audio.replayGainToGainMb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackService : MediaSessionService() {

    companion object {
        val streamQualityMap = java.util.concurrent.ConcurrentHashMap<String, String>()

        @Volatile
        var liveAudioLevel: Float = 0f
            private set

        @Volatile
        var activeInstance: PlaybackService? = null
            private set

        @Volatile
        var preferredAudioDevice: android.media.AudioDeviceInfo? = null
            private set

        @Volatile
        var currentPlaybackParameters: androidx.media3.common.PlaybackParameters =
            androidx.media3.common.PlaybackParameters.DEFAULT
            private set

        @Volatile
        var activeVideoPlayerRef: androidx.media3.exoplayer.ExoPlayer? = null

        @Volatile
        var isSpeedBoostActive: Boolean = false
            private set

        @Volatile
        private var preBoostParameters: androidx.media3.common.PlaybackParameters? = null

        fun selectPreferredAudioDevice(device: android.media.AudioDeviceInfo?) {
            preferredAudioDevice = device
            activeInstance?.applyPreferredAudioDevice(device)
        }

        fun setSpeedBoost(enabled: Boolean) {
            if (enabled) {
                if (!isSpeedBoostActive) {
                    preBoostParameters = currentPlaybackParameters
                }
                isSpeedBoostActive = true
                val basePitch = preBoostParameters?.pitch ?: currentPlaybackParameters.pitch
                setTempoAndPitch(2.0f, basePitch)
            } else if (isSpeedBoostActive) {
                isSpeedBoostActive = false
                val restore = preBoostParameters ?: androidx.media3.common.PlaybackParameters.DEFAULT
                preBoostParameters = null
                setTempoAndPitch(restore.speed, restore.pitch)
            }
        }

        fun setTempoAndPitch(speed: Float, pitch: Float) {
            val safeSpeed = speed.takeIf { it.isFinite() }?.coerceIn(0.25f, 2.0f) ?: 1.0f
            val safePitch = pitch.takeIf { it.isFinite() }?.coerceIn(0.25f, 2.0f) ?: 1.0f
            val params = androidx.media3.common.PlaybackParameters(safeSpeed, safePitch)
            currentPlaybackParameters = params
            val applyAll = {
                activeInstance?.applyPlaybackParametersToPlayers(params)
                activeVideoPlayerRef?.let { vp ->
                    try {
                        val prev = vp.playbackParameters
                        val pitchChanged = kotlin.math.abs(prev.pitch - params.pitch) > 0.0001f
                        val speedChanged = kotlin.math.abs(prev.speed - params.speed) > 0.0001f
                        if (pitchChanged || speedChanged) {
                            vp.playbackParameters = params
                            if (pitchChanged &&
                                vp.mediaItemCount > 0 &&
                                (vp.playbackState == Player.STATE_READY || vp.playbackState == Player.STATE_BUFFERING)
                            ) {
                                vp.seekTo(vp.currentPosition.coerceAtLeast(0L))
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
            val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                applyAll()
            } else {
                mainHandler.post { applyAll() }
            }
        }

        /**
         * Returns a concise stream/file quality label for player chrome (e.g. "256 kbps · 48 kHz" or "FLAC Lossless").
         */
        fun formatBadgeForSong(song: com.musicplayer.lytrix.model.Song): String? {
            val ytId = song.originalId?.takeIf { it.isNotBlank() }
            val isJioSaavn = song.albumArtUriString?.contains("saavn", ignoreCase = true) == true ||
                song.uriString.contains("saavn", ignoreCase = true) ||
                song.album.startsWith("saavn", ignoreCase = true) ||
                song.originalId?.startsWith("saavn:", ignoreCase = true) == true

            if (!isJioSaavn && ytId != null) {
                streamQualityMap[ytId]?.let { return it }
            }
            if (isJioSaavn) {
                val urlKbps = Regex("""_(\d{2,3})\.mp4""", RegexOption.IGNORE_CASE)
                    .find(song.uriString)
                    ?.groupValues?.getOrNull(1)?.toIntOrNull()
                if (urlKbps != null && urlKbps > 0) {
                    return "$urlKbps kbps · 44.1 kHz"
                }
                return null
            }
            val isOnline = song.filePath in setOf("ONLINE", "ONLINE_LYTRIX", "ONLINE_PODCAST", "ONLINE_RADIO") ||
                song.uriString.startsWith("http")
            if (!isOnline) {
                val ext = (song.filePath ?: song.uriString).substringAfterLast('.', "").lowercase()
                if (ext in setOf("flac", "alac", "wav", "aiff")) {
                    return "${ext.uppercase()} · Lossless"
                }
            }
            return null
        }

        fun isCurrentlyPlaying(): Boolean = activeInstance?.mainPlayer?.isPlaying == true

        @Volatile
        var sleepTimerGain: Float = 1f
            private set

        fun pauseIfPlaying() {
            val run = {
                activeInstance?.mainPlayer?.let { player ->
                    if (player.isPlaying || player.playWhenReady) player.pause()
                }
                activeVideoPlayerRef?.let { vp ->
                    try {
                        if (vp.isPlaying || vp.playWhenReady) vp.pause()
                    } catch (_: Exception) {}
                }
            }
            val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                run()
            } else {
                mainHandler.post { run() }
            }
        }

        fun setPlaybackVolume(volume: Float) {
            val clamped = volume.coerceIn(0f, 1f)
            val run = {
                activeInstance?.mainPlayer?.volume = clamped
            }
            val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                run()
            } else {
                mainHandler.post { run() }
            }
        }

        fun setSleepTimerGain(gain: Float) {
            val clamped = gain.coerceIn(0f, 1f)
            sleepTimerGain = clamped
            val run = {
                activeInstance?.mainPlayer?.volume = clamped
                activeVideoPlayerRef?.let { vp ->
                    try {
                        // Only scale video player when it is the active audio output (1:1 video mode)
                        if (vp.volume > 0f || (clamped == 1f && vp.playWhenReady)) {
                            if (vp.volume > 0f) {
                                vp.volume = clamped
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
            val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                run()
            } else {
                mainHandler.post { run() }
            }
        }

        fun setPauseAtEndOfMediaItems(enabled: Boolean) {
            val run = {
                activeInstance?.mainPlayer?.pauseAtEndOfMediaItems = enabled
            }
            val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                run()
            } else {
                mainHandler.post { run() }
            }
        }

    }

    data class DreamPlaybackSnapshot(
        val title: String,
        val artist: String,
        val album: String,
        val artworkUri: String?,
        val positionMs: Long,
        val durationMs: Long,
        val isPlaying: Boolean,
        val canSkipPrev: Boolean,
        val canSkipNext: Boolean,
        val mediaId: String? = null
    )

    @Volatile
    private var lastDreamSnapshot: DreamPlaybackSnapshot? = null

    fun currentDreamSnapshot(): DreamPlaybackSnapshot? {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            return lastDreamSnapshot
        }
        val player = mainPlayer ?: return lastDreamSnapshot
        val item = player.currentMediaItem ?: return null
        val meta = item.mediaMetadata
        return DreamPlaybackSnapshot(
            title = meta.title?.toString().orEmpty().ifBlank { "Not Playing" },
            artist = meta.artist?.toString().orEmpty(),
            album = meta.albumTitle?.toString().orEmpty(),
            artworkUri = meta.artworkUri?.toString(),
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = player.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0L) ?: 0L,
            isPlaying = player.isPlaying,
            canSkipPrev = player.hasPreviousMediaItem() || player.currentPosition > 3000L,
            canSkipNext = player.hasNextMediaItem(),
            mediaId = item.mediaId.takeIf { it.isNotBlank() }
        ).also { lastDreamSnapshot = it }
    }

    /** Disables video tracks on background audio players so local MV files do not spin up HW video decoders. */
    private fun createAudioOnlyTrackSelector(): androidx.media3.exoplayer.trackselection.DefaultTrackSelector {
        return androidx.media3.exoplayer.trackselection.DefaultTrackSelector(this).apply {
            parameters = buildUponParameters()
                .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, true)
                .build()
        }
    }

    fun togglePlayPause() {
        mainPlayer?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun skipToNextTrack() {
        mainPlayer?.let { if (it.hasNextMediaItem()) it.seekToNextMediaItem() }
    }

    fun skipToPreviousTrack() {
        mainPlayer?.seekToPrevious()
    }

    fun seekToPosition(positionMs: Long) {
        mainPlayer?.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun applyPreferredAudioDevice(device: android.media.AudioDeviceInfo?) {
        mainPlayer?.setPreferredAudioDevice(device)
        overlapPlayer?.setPreferredAudioDevice(device)
    }

    private data class StreamLoudness(
        val loudnessDb: Double?,
        val perceptualLoudnessDb: Double?
    )

    private var mainPlayer: ExoPlayer? = null
    private var overlapPlayer: ExoPlayer? =
        null // 🔥 লার্ক প্লেয়ারের মতো ওভারল্যাপ করার অদৃশ্য প্লেয়ার
    private val streamUrlCache =
        java.util.concurrent.ConcurrentHashMap<String, Pair<String, Long>>()
    private data class ReplayGainTag(val db: Double?, val r128Raw: Int?)

    private val streamLoudnessMap =
        java.util.concurrent.ConcurrentHashMap<String, StreamLoudness>()
    private val replayGainMap =
        java.util.concurrent.ConcurrentHashMap<String, ReplayGainTag>()
    private val CACHE_EXPIRY_MS = 1000 * 60 * 60 * 2L // 2 hours

    private val innerTubeXBlacklist = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val newPipeBlacklist = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val streamEngineMap = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val inFlightResolutions = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.CompletableDeferred<String?>>()
    private val BLACKLIST_EXPIRY_MS = 1000 * 60 * 60 * 2L // 2 hours

    private var mediaSession: MediaSession? = null

    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var monitorJob: Job? = null
    private var preloadJob: Job? = null
    private var silenceSkipJob: Job? = null
    private var sponsorBlockJob: Job? = null
    private lateinit var prefs: SharedPreferences
    private lateinit var audioAttributes: AudioAttributes
    private var isCrossfading = false
    private var lastAudioRecoveryMs = 0L

    @Volatile
    private var activeMainVideoId: String? = null
    @Volatile
    private var activeSponsorSegments: List<SponsorBlockManager.Segment> = emptyList()
    @Volatile
    private var sponsorSegmentsVideoId: String? = null
    private val skippedSponsorKeys = HashSet<String>()
    private val mainNormalizationProcessor = VolumeNormalizationAudioProcessor()
    private val mainEqProcessor = CustomEqualizerAudioProcessor()
    private val mainSilenceProcessor = SilenceDetectorAudioProcessor(
        onLongSilence = { handleLongSilenceDetected() },
        onAudioLevel = { liveAudioLevel = it }
    )
    private var overlapEqProcessor: CustomEqualizerAudioProcessor? = null

    private fun extractYouTubeVideoId(uri: android.net.Uri?): String? {
        if (uri == null) return null
        val isYouTube = uri.host?.contains("youtube.com") == true || uri.host?.contains("youtu.be") == true
        if (!isYouTube) return null
        return (uri.getQueryParameter("v") ?: uri.lastPathSegment)?.takeIf { it.isNotBlank() }
    }

    private fun recordFormatAndLoudnessMetadata(
        videoId: String,
        audioConfig: PlayerResponse.PlayerConfig.AudioConfig?,
        fmt: PlayerResponse.StreamingData.Format
    ) {
        val rawBitrate = fmt.bitrate.takeIf { it > 0 } ?: fmt.averageBitrate ?: 0
        val kbps = if (rawBitrate > 10_000) Math.round(rawBitrate / 1000.0).toInt() else rawBitrate
        val sRate = fmt.audioSampleRate ?: 0
        val sLabel = if (sRate > 0) {
            val tenths = (sRate + 50) / 100
            if (tenths % 10 == 0) "${tenths / 10} kHz" else "${tenths / 10}.${tenths % 10} kHz"
        } else "48 kHz"
        streamQualityMap[videoId] = if (kbps > 0) "$kbps kbps · $sLabel" else "128 kbps · $sLabel"

        val loudnessDb = audioConfig?.loudnessDb ?: fmt.loudnessDb
        val perceptualDb = audioConfig?.perceptualLoudnessDb
        if (loudnessDb != null || perceptualDb != null) {
            streamLoudnessMap[videoId] = StreamLoudness(loudnessDb, perceptualDb)
        }
    }

    private fun captureReplayGainMetadata(mediaKey: String?, metadata: androidx.media3.common.Metadata?) {
        if (mediaKey.isNullOrBlank() || metadata == null) return
        val dbRegex = Regex("""([+-]?\d+(?:\.\d+)?)""")
        for (i in 0 until metadata.length()) {
            val entry = metadata[i]
            val desc: String
            val valStr: String
            when (entry) {
                is androidx.media3.extractor.metadata.id3.TextInformationFrame -> {
                    desc = entry.description.orEmpty().uppercase()
                    valStr = entry.values.firstOrNull().orEmpty()
                }
                is androidx.media3.extractor.metadata.vorbis.VorbisComment -> {
                    desc = entry.key.uppercase()
                    valStr = entry.value
                }
                else -> continue
            }
            if (desc == "REPLAYGAIN_TRACK_GAIN") {
                val parsed = dbRegex.find(valStr)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
                if (parsed != null && parsed.isFinite()) {
                    replayGainMap[mediaKey] = ReplayGainTag(db = parsed, r128Raw = null)
                    return
                }
            } else if (desc == "R128_TRACK_GAIN") {
                val parsed = valStr.trim().toIntOrNull()
                if (parsed != null) {
                    replayGainMap[mediaKey] = ReplayGainTag(db = null, r128Raw = parsed)
                    return
                }
            }
        }
    }

    private fun applyNormalizationToProcessor(
        processor: VolumeNormalizationAudioProcessor,
        videoId: String?,
        mediaKey: String? = null
    ) {
        if (!::prefs.isInitialized || !prefs.getBoolean("audio_normalization", false)) {
            processor.setTargetGain(0)
            processor.enabled = false
            return
        }
        val level = LoudnessLevel.fromStored(prefs.getString("loudness_level", LoudnessLevel.BALANCED.name))

        // 1. Exact YouTube loudness metadata
        if (videoId != null) {
            val loudness = streamLoudnessMap[videoId]
            val gainMb = normalizationGainMb(loudness?.loudnessDb, loudness?.perceptualLoudnessDb, level.targetLufs)
            if (gainMb != null) {
                processor.setTargetGain(gainMb)
                processor.enabled = true
                return
            }
        }

        // 2. Exact local ReplayGain / Opus R128 tag if present
        val rg = mediaKey?.let { replayGainMap[it] }
        if (rg != null) {
            val rgGainMb = when {
                rg.r128Raw != null -> r128TrackGainToGainMb(rg.r128Raw, level.targetLufs)
                rg.db != null -> replayGainToGainMb(rg.db, level.targetLufs)
                else -> null
            }
            if (rgGainMb != null) {
                processor.setTargetGain(rgGainMb)
                processor.enabled = true
                return
            }
        }

        // 3. Untagged local files, JioSaavn streams, or YouTube without loudness metadata -> Real-time Adaptive RMS + Peak Limiter
        processor.setAdaptiveTargetLufs(level.targetLufs)
        processor.enabled = true
    }

    private fun updateMainNormalizationForCurrentItem() {
        val currentItem = mainPlayer?.currentMediaItem
        val uri = currentItem?.localConfiguration?.uri
        val videoId = uri?.let { extractYouTubeVideoId(it) }
        val mediaKey = uri?.toString() ?: currentItem?.mediaId
        activeMainVideoId = videoId
        applyNormalizationToProcessor(mainNormalizationProcessor, videoId, mediaKey)
    }

    private fun updateSilenceSkipSettings() {
        if (!::prefs.isInitialized) return
        val skipSilence = prefs.getBoolean("skip_silence", false)
        val instantSkip = skipSilence && prefs.getBoolean("skip_silence_instant", false)
        mainPlayer?.skipSilenceEnabled = skipSilence
        overlapPlayer?.skipSilenceEnabled = skipSilence
        mainSilenceProcessor.instantModeEnabled = instantSkip
        if (!instantSkip) {
            silenceSkipJob?.cancel()
            mainSilenceProcessor.resetTracking()
        }
    }

    private fun handleLongSilenceDetected() {
        val player = mainPlayer ?: return
        if (!::prefs.isInitialized ||
            !prefs.getBoolean("skip_silence", false) ||
            !prefs.getBoolean("skip_silence_instant", false)
        ) {
            return
        }
        if (silenceSkipJob?.isActive == true) return

        silenceSkipJob = serviceScope.launch {
            delay(200L)
            val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: return@launch
            if (duration <= 15_000L) return@launch
            var hops = 0
            while (
                isActive &&
                prefs.getBoolean("skip_silence", false) &&
                prefs.getBoolean("skip_silence_instant", false) &&
                mainSilenceProcessor.isCurrentlySilent()
            ) {
                val current = player.currentPosition
                val target = (current + 15_000L).coerceAtMost(duration - 500L)
                if (target <= current) break
                mainSilenceProcessor.resetTracking()
                player.seekTo(target)
                hops++
                if (hops >= 80 || target >= duration - 500L) break
                delay(350L)
            }
        }
    }

    private fun applyPlaybackParametersToPlayers(params: androidx.media3.common.PlaybackParameters) {
        mainPlayer?.let { player ->
            runCatching {
                val previous = player.playbackParameters
                val pitchChanged = kotlin.math.abs(previous.pitch - params.pitch) > 0.0001f
                val speedChanged = kotlin.math.abs(previous.speed - params.speed) > 0.0001f
                if (pitchChanged || speedChanged) {
                    player.playbackParameters = params
                    // Bypass DefaultAudioSink's drainToEndOfStream() checkpoint-desync bug on pitch
                    // transitions by issuing an instant clean flush at the current playback position.
                    if (pitchChanged &&
                        player.mediaItemCount > 0 &&
                        (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_BUFFERING)
                    ) {
                        val pos = player.currentPosition.coerceAtLeast(0L)
                        player.seekTo(pos)
                    }
                }
                if (player.playbackState == Player.STATE_IDLE && player.mediaItemCount > 0) {
                    player.prepare()
                }
            }.onFailure { err ->
                android.util.Log.e("PlaybackService", "Failed to apply playback parameters $params", err)
            }
        }
        overlapPlayer?.let { overlap ->
            runCatching {
                if (overlap.playbackParameters != params) {
                    overlap.playbackParameters = params
                }
            }
        }
    }

    private fun createRenderersFactory(
        normalizationProcessor: VolumeNormalizationAudioProcessor,
        eqProcessor: CustomEqualizerAudioProcessor,
        silenceProcessor: SilenceDetectorAudioProcessor
    ) = object : androidx.media3.exoplayer.DefaultRenderersFactory(this) {
        override fun buildAudioRenderers(
            context: Context,
            extensionRendererMode: Int,
            mediaCodecSelector: androidx.media3.exoplayer.mediacodec.MediaCodecSelector,
            enableDecoderFallback: Boolean,
            audioSink: androidx.media3.exoplayer.audio.AudioSink,
            eventHandler: android.os.Handler,
            eventListener: androidx.media3.exoplayer.audio.AudioRendererEventListener,
            out: java.util.ArrayList<androidx.media3.exoplayer.Renderer>
        ) {
            super.buildAudioRenderers(
                context,
                extensionRendererMode,
                mediaCodecSelector,
                enableDecoderFallback,
                audioSink,
                eventHandler,
                eventListener,
                out
            )
            val coreIdx = out.indexOfFirst { it is androidx.media3.exoplayer.audio.MediaCodecAudioRenderer }
            if (coreIdx == -1) return
            out[coreIdx] = object : androidx.media3.exoplayer.audio.MediaCodecAudioRenderer(
                context,
                mediaCodecSelector,
                enableDecoderFallback,
                eventHandler,
                eventListener,
                audioSink
            ) {
                override fun getCodecOperatingRateV23(
                    targetPlaybackSpeed: Float,
                    format: androidx.media3.common.Format,
                    streamFormats: Array<androidx.media3.common.Format>
                ): Float {
                    return super.getCodecOperatingRateV23(
                        if (targetPlaybackSpeed in 0.95f..1.05f) 1f else targetPlaybackSpeed,
                        format,
                        streamFormats
                    )
                }
            }
        }

        override fun buildAudioSink(
            context: Context,
            enableFloatOutput: Boolean,
            enableAudioTrackPlaybackParams: Boolean
        ): androidx.media3.exoplayer.audio.AudioSink {
            // Pitch-safe software Sonic sink: disable float output & hardware AudioTrack
            // playback params, guard SonicAudioProcessor against Media3's repeated
            // queueEndOfStream() drain loop, and place always-active processors downstream.
            return androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(this@PlaybackService)
                .setEnableFloatOutput(false)
                .setEnableAudioTrackPlaybackParams(false)
                .setAudioProcessorChain(
                    com.musicplayer.lytrix.service.audio.SafeSonicAudioProcessorChain(
                        downstreamProcessors = arrayOf(
                            normalizationProcessor,
                            eqProcessor,
                            silenceProcessor
                        ),
                        silenceSkippingAudioProcessor = androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor(
                            2_000_000L,
                            20_000L,
                            256.toShort()
                        ),
                        sonicAudioProcessor = androidx.media3.common.audio.SonicAudioProcessor()
                    )
                )
                .build()
        }
    }

    private fun resolveYouTubeStreamUrl(videoId: String): String? {
        val now = System.currentTimeMillis()
        val cached = streamUrlCache[videoId]
        if (cached != null && !cached.first.isNullOrBlank() && cached.first.startsWith("http") && (now - cached.second) < CACHE_EXPIRY_MS) {
            android.util.Log.d("PlaybackService", "Using CACHED URL for $videoId (Engine: ${streamEngineMap[videoId] ?: "UNKNOWN"})")
            if (videoId == activeMainVideoId) {
                applyNormalizationToProcessor(mainNormalizationProcessor, videoId)
            }
            return cached.first
        }

        val deferred = kotlinx.coroutines.CompletableDeferred<String?>()
        val existing = inFlightResolutions.putIfAbsent(videoId, deferred)
        if (existing != null) {
            android.util.Log.d("PlaybackService", "Awaiting in-flight resolution for $videoId")
            return try {
                val url = kotlinx.coroutines.runBlocking(Dispatchers.IO) { existing.await() }
                if (url != null && videoId == activeMainVideoId) {
                    applyNormalizationToProcessor(mainNormalizationProcessor, videoId)
                }
                url
            } catch (_: Throwable) {
                null
            }
        }

        var resolvedUrl: String? = null
        try {
            resolvedUrl = resolveYouTubeStreamUrlInternal(videoId, now)
            if (resolvedUrl != null && videoId == activeMainVideoId) {
                applyNormalizationToProcessor(mainNormalizationProcessor, videoId)
            }
            return resolvedUrl
        } finally {
            inFlightResolutions.remove(videoId, deferred)
            deferred.complete(resolvedUrl)
        }
    }

    private fun resolveYouTubeStreamUrlInternal(videoId: String, now: Long): String? {
        // 1. InnerTubeX (Primary Engine)
        val isInnerTubeXBlacklisted = innerTubeXBlacklist[videoId]?.let { (now - it) < BLACKLIST_EXPIRY_MS } == true

        if (!isInnerTubeXBlacklisted) {
            android.util.Log.d(
                "PlaybackService",
                "Trying InnerTubeX logic for videoId: $videoId"
            )
            try {
                com.musicplayer.lytrix.utils.InnerTubeXPlayer.initialize(this@PlaybackService.applicationContext)
                val finalUrl =
                    kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                        val connectivityManager =
                            getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                        val result =
                            com.musicplayer.lytrix.utils.InnerTubeXPlayer.playerResponseForPlayback(
                                videoId = videoId,
                                playlistId = null,
                                audioQuality = com.musicplayer.lytrix.constants.AudioQuality.HIGH,
                                connectivityManager = connectivityManager
                            )
                        val playbackData = result.getOrNull()
                        if (playbackData != null) {
                            recordFormatAndLoudnessMetadata(videoId, playbackData.audioConfig, playbackData.format)
                        }
                        if (result.isFailure) {
                            android.util.Log.e(
                                "PlaybackService",
                                "InnerTubeX Result Failure: ${result.exceptionOrNull()?.message}",
                                result.exceptionOrNull()
                            )
                        }
                        return@runBlocking result.getOrNull()?.streamUrl
                    }

                if (!finalUrl.isNullOrBlank() && finalUrl.startsWith("http")) {
                    android.util.Log.d(
                        "PlaybackService",
                        "InnerTubeX Success! URL: $finalUrl"
                    )
                    streamUrlCache[videoId] = Pair(finalUrl, now)
                    streamEngineMap[videoId] = "INNERTUBEX"
                    return finalUrl
                } else {
                    android.util.Log.e("PlaybackService", "InnerTubeX returned a null URL.")
                }
            } catch (e: Exception) {
                android.util.Log.e("PlaybackService", "InnerTubeX Failed: ${e.message}", e)
            }
        } else {
            android.util.Log.w("PlaybackService", "Skipping InnerTubeX for ytVideoId=$videoId (Blacklisted due to previous 403)")
        }

        // 2. NewPipe (Fallback Engine)
        val isNewPipeBlacklisted = newPipeBlacklist[videoId]?.let { (now - it) < BLACKLIST_EXPIRY_MS } == true

        if (!isNewPipeBlacklisted) {
            try {
                val youtubeUrl = "https://www.youtube.com/watch?v=$videoId"
                val streamExtractor =
                    org.schabi.newpipe.extractor.ServiceList.YouTube.getStreamExtractor(
                        youtubeUrl
                    )
                streamExtractor.fetchPage()

                val bestAudioStream =
                    streamExtractor.audioStreams.maxByOrNull { it.bitrate }
                val bestAudio = bestAudioStream?.content

                if (!bestAudio.isNullOrBlank() && bestAudio.startsWith("http") && !bestAudio.contains("dur=1.0")) {
                    val rawBitrate = bestAudioStream?.bitrate ?: 0
                    val kbps = if (rawBitrate > 10_000) Math.round(rawBitrate / 1000.0).toInt() else rawBitrate
                    streamQualityMap[videoId] = if (kbps > 0) "$kbps kbps · 48 kHz" else "128 kbps · 48 kHz"
                    android.util.Log.d(
                        "PlaybackService",
                        "NewPipe Success! Playing: $bestAudio"
                    )
                    streamUrlCache[videoId] = Pair(bestAudio, now)
                    streamEngineMap[videoId] = "NEWPIPE"
                    return bestAudio
                }
            } catch (e: Throwable) {
                android.util.Log.e("PlaybackService", "NewPipe Failed: ${e.message}")
            }
        } else {
            android.util.Log.w("PlaybackService", "Skipping NewPipe for ytVideoId=$videoId (Blacklisted due to previous 403)")
        }

        // 3. YTPlayerUtils (Hybrid fallback)
        android.util.Log.d(
            "PlaybackService",
            "Falling back to Hybrid YTPlayerUtils logic for videoId: $videoId"
        )
        try {
            try {
                com.musicplayer.lytrix.utils.cipher.CipherDeobfuscator.appContext
            } catch (e: UninitializedPropertyAccessException) {
                com.musicplayer.lytrix.utils.cipher.CipherDeobfuscator.initialize(this@PlaybackService.applicationContext)
            }

            val finalUrl =
                kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                    val connectivityManager =
                        getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager

                    val result =
                        com.musicplayer.lytrix.utils.YTPlayerUtils.playerResponseForPlayback(
                            videoId = videoId,
                            playlistId = null,
                            audioQuality = com.musicplayer.lytrix.constants.AudioQuality.HIGH,
                            connectivityManager = connectivityManager
                        )
                    val playbackData = result.getOrNull()
                    if (playbackData != null) {
                        recordFormatAndLoudnessMetadata(videoId, playbackData.audioConfig, playbackData.format)
                    }

                    return@runBlocking result.getOrNull()?.streamUrl
                }

            if (!finalUrl.isNullOrBlank() && finalUrl.startsWith("http")) {
                android.util.Log.d(
                    "PlaybackService",
                    "Meld YTPlayerUtils Success! URL: $finalUrl"
                )
                streamUrlCache[videoId] = Pair(finalUrl, now)
                streamEngineMap[videoId] = "YTPLAYERUTILS"
                return finalUrl
            }
        } catch (e: Exception) {
            android.util.Log.e(
                "PlaybackService",
                "Hybrid Fallback completely failed: ${e.message}"
            )
        }
        return null
    }

    private fun scheduleNextTrackPreload() {
        val player = mainPlayer ?: return
        if (!::prefs.isInitialized || !prefs.getBoolean("preload_next_stream", true)) {
            preloadJob?.cancel()
            return
        }
        val nextIndex = player.nextMediaItemIndex
        if (nextIndex == C.INDEX_UNSET || nextIndex < 0 || nextIndex >= player.mediaItemCount) {
            preloadJob?.cancel()
            return
        }
        val videoId = extractYouTubeVideoId(player.getMediaItemAt(nextIndex).localConfiguration?.uri) ?: return
        val now = System.currentTimeMillis()
        val cached = streamUrlCache[videoId]
        if (cached != null && !cached.first.isNullOrBlank() && cached.first.startsWith("http") && (now - cached.second) < CACHE_EXPIRY_MS) {
            return
        }

        preloadJob?.cancel()
        preloadJob = serviceScope.launch(Dispatchers.IO) {
            delay(600L)
            if (!isActive) return@launch
            val url = resolveYouTubeStreamUrl(videoId)
            android.util.Log.d(
                "PlaybackService",
                if (url != null) "Next stream preloaded for $videoId" else "Next stream preload returned null for $videoId"
            )
        }
    }

    private val mediaSourceFactory by lazy {
        // 🔥 ১. ExoPlayer-কে আসল ব্রাউজারের ছদ্মবেশ (User-Agent) পরানো হলো
        val httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")

        // ২. সেই ছদ্মবেশটা upstreamFactory-তে দেওয়া হলো
        val upstreamFactory =
            androidx.media3.datasource.DefaultDataSource.Factory(this, httpDataSourceFactory)

        val resolver = object : androidx.media3.datasource.ResolvingDataSource.Resolver {
            override fun resolveDataSpec(dataSpec: androidx.media3.datasource.DataSpec): androidx.media3.datasource.DataSpec {
                val videoId = extractYouTubeVideoId(dataSpec.uri)
                if (videoId != null) {
                    val resolvedUrl = resolveYouTubeStreamUrl(videoId)
                    if (!resolvedUrl.isNullOrBlank() && resolvedUrl.startsWith("http")) {
                        return dataSpec.withUri(android.net.Uri.parse(resolvedUrl))
                    }
                }
                return dataSpec
            }
        }

        val resolvingFactory =
            androidx.media3.datasource.ResolvingDataSource.Factory(upstreamFactory, resolver)
        androidx.media3.exoplayer.source.DefaultMediaSourceFactory(resolvingFactory)
    }
    private val prefsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key == "play_together") {
                val playTogether = sharedPreferences.getBoolean("play_together", false)
                mainPlayer?.setAudioAttributes(audioAttributes, !playTogether)
            }
            if (key == "gapless_playback") {
                val gapless = sharedPreferences.getBoolean("gapless_playback", false)
                if (gapless) {
                    mainPlayer?.setSeekParameters(androidx.media3.exoplayer.SeekParameters.EXACT)
                } else {
                    mainPlayer?.setSeekParameters(androidx.media3.exoplayer.SeekParameters.DEFAULT)
                }
            }
            if (key == "preload_next_stream") {
                if (sharedPreferences.getBoolean("preload_next_stream", true)) {
                    scheduleNextTrackPreload()
                } else {
                    preloadJob?.cancel()
                }
            }
            if (key == "audio_normalization" || key == "loudness_level") {
                updateMainNormalizationForCurrentItem()
            }
            if (key == "skip_silence" || key == "skip_silence_instant") {
                updateSilenceSkipSettings()
            }
            if (key == "sponsorblock_enabled" || key == "sponsorblock_categories") {
                loadSponsorBlockForCurrentTrack()
            }
        }

    private fun loadSponsorBlockForCurrentTrack() {
        sponsorBlockJob?.cancel()
        activeSponsorSegments = emptyList()
        skippedSponsorKeys.clear()
        if (!::prefs.isInitialized || !prefs.getBoolean("sponsorblock_enabled", false)) {
            sponsorSegmentsVideoId = null
            return
        }
        val videoId = activeMainVideoId
            ?: mainPlayer?.currentMediaItem?.localConfiguration?.uri?.let(::extractYouTubeVideoId)
            ?: return
        sponsorSegmentsVideoId = videoId
        val categories = SponsorBlockManager.parseCategories(
            prefs.getString("sponsorblock_categories", SponsorBlockManager.DEFAULT_CATEGORIES)
        )
        sponsorBlockJob = serviceScope.launch {
            val segments = SponsorBlockManager.fetchSegments(videoId, categories)
            if (isActive && sponsorSegmentsVideoId == videoId) {
                activeSponsorSegments = segments
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        prefs = getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)

        val playTogether = prefs.getBoolean("play_together", false)
        val gapless = prefs.getBoolean("gapless_playback", false)

        audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val database = com.musicplayer.lytrix.db.AppDatabase.getDatabase(this)
        val tracker = PlaybackTracker(database.playtimeDao(), this)

        LytrixEqualizerService.restoreSavedState(this)
        LytrixEqualizerService.addAudioProcessor(mainEqProcessor)

        val exoPlayer = ExoPlayer.Builder(this)
            .setTrackSelector(createAudioOnlyTrackSelector())
            .setMediaSourceFactory(mediaSourceFactory) // 🔥 মেইন প্লেয়ারে ব্রাউজার সোর্স এড করা হলো!
            .setRenderersFactory(
                createRenderersFactory(
                    mainNormalizationProcessor,
                    mainEqProcessor,
                    mainSilenceProcessor
                )
            )
            .setAudioAttributes(audioAttributes, !playTogether)
            .setHandleAudioBecomingNoisy(true)
            .build()

        exoPlayer.skipSilenceEnabled = prefs.getBoolean("skip_silence", false)
        mainSilenceProcessor.instantModeEnabled =
            exoPlayer.skipSilenceEnabled && prefs.getBoolean("skip_silence_instant", false)

        if (gapless) {
            exoPlayer.setSeekParameters(androidx.media3.exoplayer.SeekParameters.EXACT)
        }
        if (currentPlaybackParameters != androidx.media3.common.PlaybackParameters.DEFAULT) {
            exoPlayer.playbackParameters = currentPlaybackParameters
        }

        exoPlayer.addAnalyticsListener(tracker)
        val initialSessionId = exoPlayer.audioSessionId
        if (initialSessionId != 0 && initialSessionId != androidx.media3.common.C.AUDIO_SESSION_ID_UNSET) {
            AudioEffectManager.init(initialSessionId)
        }
        exoPlayer.addAnalyticsListener(object :
            androidx.media3.exoplayer.analytics.AnalyticsListener {
            override fun onAudioSessionIdChanged(
                eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                audioSessionId: Int
            ) {
                AudioEffectManager.init(audioSessionId)
            }
        })

        exoPlayer.addListener(object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                // Use the YouTube video ID from the URI (not mediaId which is the DB row ID)
                val uri = mainPlayer?.currentMediaItem?.localConfiguration?.uri
                val ytVideoId = (uri?.getQueryParameter("v") ?: uri?.lastPathSegment)?.takeIf { it.isNotBlank() }
                if (ytVideoId != null) {
                    streamUrlCache.remove(ytVideoId)
                    val failedEngine = streamEngineMap.remove(ytVideoId)
                    android.util.Log.d(
                        "PlaybackService",
                        "Removed broken URL from cache for ytVideoId=$ytVideoId (engine: $failedEngine, error: ${error.message})"
                    )

                    val cause = error.cause
                    if (cause is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException && cause.responseCode == 403) {
                        if (failedEngine == "INNERTUBEX") {
                            innerTubeXBlacklist[ytVideoId] = System.currentTimeMillis()
                            android.util.Log.d("PlaybackService", "Blacklisted InnerTubeX for ytVideoId=$ytVideoId due to 403. Auto-retrying with NewPipe...")
                            mainPlayer?.let { player ->
                                player.prepare()
                                player.play()
                            }
                        } else if (failedEngine == "NEWPIPE") {
                            newPipeBlacklist[ytVideoId] = System.currentTimeMillis()
                            android.util.Log.d("PlaybackService", "Blacklisted NewPipe for ytVideoId=$ytVideoId due to 403. Auto-retrying with YTPlayerUtils...")
                            mainPlayer?.let { player ->
                                player.prepare()
                                player.play()
                            }
                        } else {
                            android.util.Log.e("PlaybackService", "403 occurred on engine $failedEngine. Stopping retry loop.")
                        }
                    }
                } else {
                    android.util.Log.w("PlaybackService", "onPlayerError: could not get ytVideoId from URI=$uri (${error.errorCodeName})")
                    val isAudioSinkError =
                        error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED ||
                            error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED
                    val now = System.currentTimeMillis()
                    if (isAudioSinkError && now - lastAudioRecoveryMs > 2500L) {
                        lastAudioRecoveryMs = now
                        mainPlayer?.let { player ->
                            val resumePos = player.currentPosition.coerceAtLeast(0L)
                            if (currentPlaybackParameters != androidx.media3.common.PlaybackParameters.DEFAULT) {
                                currentPlaybackParameters = androidx.media3.common.PlaybackParameters.DEFAULT
                                player.playbackParameters = currentPlaybackParameters
                            }
                            if (resumePos > 0L) player.seekTo(resumePos)
                            player.prepare()
                            if (player.playWhenReady) player.play()
                        }
                    }
                }
            }

            override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
                currentPlaybackParameters = playbackParameters
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    overlapPlayer?.play()
                } else {
                    overlapPlayer?.pause()
                }
                currentDreamSnapshot()
                com.musicplayer.lytrix.widget.LytrixWidgetUpdater.scheduleUpdate(this@PlaybackService)
            }

            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                silenceSkipJob?.cancel()
                mainSilenceProcessor.resetTracking()
                mainNormalizationProcessor.resetAdaptiveState()
                updateMainNormalizationForCurrentItem()
                loadSponsorBlockForCurrentTrack()
                scheduleNextTrackPreload()
                currentDreamSnapshot()
                com.musicplayer.lytrix.widget.LytrixWidgetUpdater.scheduleUpdate(this@PlaybackService)
            }

            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                val item = mainPlayer?.currentMediaItem
                val mediaKey = item?.localConfiguration?.uri?.toString() ?: item?.mediaId
                for (group in tracks.groups) {
                    if (group.type == C.TRACK_TYPE_AUDIO && group.isSelected) {
                        for (i in 0 until group.length) {
                            if (group.isTrackSelected(i)) {
                                captureReplayGainMetadata(mediaKey, group.getTrackFormat(i).metadata)
                            }
                        }
                    }
                }
                updateMainNormalizationForCurrentItem()
            }

            override fun onMetadata(metadata: androidx.media3.common.Metadata) {
                val item = mainPlayer?.currentMediaItem
                val mediaKey = item?.localConfiguration?.uri?.toString() ?: item?.mediaId
                captureReplayGainMetadata(mediaKey, metadata)
                updateMainNormalizationForCurrentItem()
            }

            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                scheduleNextTrackPreload()
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                scheduleNextTrackPreload()
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                scheduleNextTrackPreload()
            }
        })

        mainPlayer = exoPlayer
        activeInstance = this
        preferredAudioDevice?.let { exoPlayer.setPreferredAudioDevice(it) }
        mediaSession = MediaSession.Builder(this, exoPlayer).build()

        startPlaybackMonitor()
        scheduleNextTrackPreload()
        com.musicplayer.lytrix.widget.LytrixWidgetUpdater.scheduleUpdate(this, immediate = true)
    }

    private fun startPlaybackMonitor() {
        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            var widgetSyncElapsedMs = 0L
            while (isActive) {
                val player = mainPlayer ?: break

                if (player.isPlaying) {
                    widgetSyncElapsedMs += 200L
                    if (widgetSyncElapsedMs >= 15_000L) {
                        widgetSyncElapsedMs = 0L
                        com.musicplayer.lytrix.widget.LytrixWidgetUpdater.scheduleUpdate(this@PlaybackService)
                    }
                } else {
                    widgetSyncElapsedMs = 0L
                }

                if (prefs.getBoolean("sponsorblock_enabled", false) && player.isPlaying) {
                    val currentVid = sponsorSegmentsVideoId
                    val segments = activeSponsorSegments
                    if (currentVid != null && currentVid == activeMainVideoId && segments.isNotEmpty()) {
                        val pos = player.currentPosition
                        val dur = player.duration
                        for (seg in segments) {
                            val key = "$currentVid:${seg.category}:${seg.startMs}:${seg.endMs}"
                            if (key !in skippedSponsorKeys && pos >= seg.startMs && pos < seg.endMs - 400L) {
                                skippedSponsorKeys.add(key)
                                val targetPos = if (dur > 0L) {
                                    seg.endMs.coerceAtMost((dur - 250L).coerceAtLeast(0L))
                                } else {
                                    seg.endMs
                                }
                                player.seekTo(targetPos)
                                if (prefs.getBoolean("sponsorblock_show_toast", false)) {
                                    android.widget.Toast.makeText(
                                        this@PlaybackService,
                                        "Skipped ${SponsorBlockManager.categoryDisplayName(seg.category)}",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                                break
                            }
                        }
                    }
                }

                val crossfadeEnabled = prefs.getBoolean("crossfade", false)

                if (crossfadeEnabled && player.isPlaying && !isCrossfading && player.hasNextMediaItem()) {
                    val durationSec = prefs.getFloat("crossfade_duration", 4f)
                    val crossfadeMs = (durationSec * 1000).toLong()
                    val remainingTime = player.duration - player.currentPosition

                    if (remainingTime > 100 && remainingTime <= crossfadeMs) {
                        triggerOverlappingCrossfade(remainingTime)
                    }
                }
                delay(200)
            }
        }
    }

    private fun triggerOverlappingCrossfade(remainingTimeMs: Long) {
        val player = mainPlayer ?: return
        isCrossfading = true

        val currentItem = player.currentMediaItem
        val currentPos = player.currentPosition

        if (currentItem != null) {
            val overlapNorm = VolumeNormalizationAudioProcessor()
            val overlapEq = CustomEqualizerAudioProcessor()
            val overlapSilence = SilenceDetectorAudioProcessor(onLongSilence = {})
            overlapEqProcessor?.let { LytrixEqualizerService.removeAudioProcessor(it) }
            overlapEqProcessor = overlapEq
            LytrixEqualizerService.addAudioProcessor(overlapEq)
            applyNormalizationToProcessor(
                overlapNorm,
                extractYouTubeVideoId(currentItem.localConfiguration?.uri),
                currentItem.localConfiguration?.uri?.toString() ?: currentItem.mediaId
            )

            overlapPlayer = ExoPlayer.Builder(this@PlaybackService)
                .setTrackSelector(createAudioOnlyTrackSelector())
                .setMediaSourceFactory(mediaSourceFactory) // 🔥 ওভারল্যাপ প্লেয়ারেও ব্রাউজার সোর্স এড করা হলো!
                .setRenderersFactory(createRenderersFactory(overlapNorm, overlapEq, overlapSilence))
                .setAudioAttributes(audioAttributes, false)
                .build().apply {
                    skipSilenceEnabled = prefs.getBoolean("skip_silence", false)
                    setMediaItem(currentItem)
                    seekTo(currentPos)
                    volume = 1f
                    preferredAudioDevice?.let { setPreferredAudioDevice(it) }
                    prepare()
                    playWhenReady = true
                }
        }

        player.volume = 1f
        player.seekToNext()

        serviceScope.launch {
            delay(remainingTimeMs)
            overlapEqProcessor?.let {
                LytrixEqualizerService.removeAudioProcessor(it)
                overlapEqProcessor = null
            }
            overlapPlayer?.stop()
            overlapPlayer?.release()
            overlapPlayer = null

            delay(1000)
            isCrossfading = false
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        try {
            // 🔒 5-Minute Grace with Instant Abandonment Cleanup:
            // সোয়াইপ করলে এই মেম্বার সাথে সাথে online = false (Away 💤) হয়ে যাবে
            // যদি রুমে অন্য অন্তত ১ জনও অনলাইনে থাকে → রুম ডিলিট হবে না
            // যদি রুমের সবাই অ্যাপ কিল করে দেয় (০ জন অনলাইন) → সাথে সাথে রুম মুছে যাবে
            val sessionPrefs = getSharedPreferences("listen_together_session", Context.MODE_PRIVATE)
            val currentRoomCode = sessionPrefs.getString("current_room_code", null)
            val deviceId = sessionPrefs.getString("device_id", null)
            val dbUrl = "https://lytrix-player-default-rtdb.asia-southeast1.firebasedatabase.app"
            if (!currentRoomCode.isNullOrBlank() && !deviceId.isNullOrBlank()) {
                val db = com.google.firebase.database.FirebaseDatabase.getInstance(dbUrl)
                val roomRef = db.getReference("ListenTogetherRooms").child(currentRoomCode)
                val membersRef = roomRef.child("members")

                // ১. নিজেকে অফলাইন (Away 💤) হিসেবে মার্ক করো
                membersRef.child(deviceId).child("online").setValue(false)

                // ২. চেক করো অন্য কোনো মেম্বার এখনো অনলাইনে আছে কি না
                val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
                val latch = java.util.concurrent.CountDownLatch(1)
                membersRef.get().addOnCompleteListener(executor) { snapTask ->
                    try {
                        if (snapTask.isSuccessful) {
                            val snapshot = snapTask.result
                            var hasOtherOnlineMember = false
                            if (snapshot != null && snapshot.exists()) {
                                for (child in snapshot.children) {
                                    val mId = child.key ?: continue
                                    if (mId != deviceId) {
                                        val isOnline = child.child("online").getValue(Boolean::class.java) ?: false
                                        if (isOnline) {
                                            hasOtherOnlineMember = true
                                            break
                                        }
                                    }
                                }
                            }
                            if (!hasOtherOnlineMember) {
                                // ❌ রুমের সবাই অফলাইন/অ্যাপ কিল করেছে → সাথে সাথেই রুম ক্লোজ
                                roomRef.removeValue()
                                db.getReference("ListenTogetherVoices").child(currentRoomCode).removeValue()
                                sessionPrefs.edit().clear().apply()
                            }
                        }
                    } catch (_: Throwable) {}
                    latch.countDown()
                }
                try {
                    latch.await(800, java.util.concurrent.TimeUnit.MILLISECONDS)
                } catch (_: Throwable) {}
                executor.shutdown()
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        monitorJob?.cancel()
        preloadJob?.cancel()
        silenceSkipJob?.cancel()
        sponsorBlockJob?.cancel()
        LytrixEqualizerService.removeAudioProcessor(mainEqProcessor)
        overlapEqProcessor?.let {
            LytrixEqualizerService.removeAudioProcessor(it)
            overlapEqProcessor = null
        }
        overlapPlayer?.release()
        mediaSession?.let { session ->
            session.player.release()
            session.release()
            mediaSession = null
        }
        if (activeInstance === this) {
            activeInstance = null
            liveAudioLevel = 0f
        }
        mainPlayer = null
        super.onDestroy()
    }
}