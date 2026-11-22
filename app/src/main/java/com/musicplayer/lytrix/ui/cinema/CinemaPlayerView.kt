@file:OptIn(UnstableApi::class)

package com.musicplayer.lytrix.ui.cinema

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.widget.Toast
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.util.Locale
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// Small types
// ---------------------------------------------------------------------------

private enum class AspectMode(val label: String) {
    FIT("Fit"),
    RATIO_16_9("16:9"),
    ZOOM("Zoom");

    fun next(): AspectMode = entries[(ordinal + 1) % entries.size]
}

private enum class QualityOption(val label: String, val maxHeight: Int?) {
    AUTO("Auto", null),
    P1080("1080p", 1080),
    P720("720p", 720),
    P480("480p", 480),
    P360("360p", 360);
}

private sealed interface StreamState {
    data object Loading : StreamState
    data object Failed : StreamState
    data class ExoReady(val url: String) : StreamState
    data class WebReady(val embedUrl: String) : StreamState
    data class TorrentReady(val magnetUri: String, val streams: List<TorrentStream>) : StreamState
}

private data class SeekFlash(val forward: Boolean, val tick: Int)
private data class LevelHud(val isBrightness: Boolean, val value: Float)
private data class TrackOption(val label: String, val selected: Boolean, val onSelect: () -> Unit)

// ---------------------------------------------------------------------------
// Player
// ---------------------------------------------------------------------------

/**
 * Immersive landscape player with Hybrid Streaming Engine:
 * - Native ExoPlayer for direct HLS/MP4 streams with gesture HUD, audio & subtitles
 * - Ad-Free In-App Web Engine for multi-server embed gateways (VidLink, SuperEmbed, etc.)
 * with zero popups and full 1080p playback.
 */
@Composable
fun CinemaPlayerView(
    request: PlaybackRequest,
    servers: List<ServerSource>,
    resolver: CinemaStreamResolver,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val accent = cinemaAccent()

    // --- state -------------------------------------------------------------
    var server by remember { mutableStateOf(request.server) }
    var streamState by remember { mutableStateOf<StreamState>(StreamState.Loading) }
    var retryTick by remember { mutableIntStateOf(0) }
    var playerError by remember { mutableStateOf<String?>(null) }

    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }
    var tracks by remember { mutableStateOf(Tracks.EMPTY) }

    var hudVisible by remember { mutableStateOf(true) }
    var interactionTick by remember { mutableIntStateOf(0) }
    var serverMenu by remember { mutableStateOf(false) }
    var qualityMenu by remember { mutableStateOf(false) }
    var speedMenu by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var sheetOpen by remember { mutableStateOf(false) }
    var aspect by remember { mutableStateOf(AspectMode.FIT) }
    var quality by remember { mutableStateOf(QualityOption.AUTO) }

    var seekFlash by remember { mutableStateOf<SeekFlash?>(null) }
    var levelHud by remember { mutableStateOf<LevelHud?>(null) }

    // --- system services ---------------------------------------------------
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var volume by remember {
        mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume)
    }
    var brightness by remember { mutableFloatStateOf(initialBrightness(activity)) }

    // Request Audio Focus so background music pauses during video playback
    DisposableEffect(Unit) {
        val audioFocusRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                .build()
        } else null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
            audioManager.requestAudioFocus(audioFocusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }

        onDispose {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
                audioManager.abandonAudioFocusRequest(audioFocusRequest)
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        }
    }

    // --- ExoPlayer ---------------------------------------------------------
    val exoPlayer = remember {
        val httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)

        val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context)
            .setDataSourceFactory(httpDataSourceFactory)

        val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setEnableDecoderFallback(true)

        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setPreferredAudioLanguage("hi")
            )
        }

        ExoPlayer.Builder(context, renderersFactory)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, true)
            .setSeekBackIncrementMs(10_000L)
            .setSeekForwardIncrementMs(10_000L)
            .build()
            .apply { playWhenReady = true }
    }
    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
            TorrentStreamManager.stopStream()
        }
    }

    val torrentPlaybackState by TorrentStreamManager.playbackState.collectAsState()

    var torrentFile by remember { mutableStateOf<String?>(null) }
    var torrentRetryCount by remember { mutableIntStateOf(0) }
    var torrentRetryTick by remember { mutableIntStateOf(0) }
    var lastTorrentState by remember { mutableStateOf<StreamState.TorrentReady?>(null) }
    var codecFailUrl by remember { mutableStateOf<String?>(null) }

    fun playTorrentUrl(url: String, startPositionMs: Long = 0L) {
        val http = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(120_000)
        val source = ProgressiveMediaSource.Factory(http)
            .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(10))
            .createMediaSource(MediaItem.fromUri(url))
        exoPlayer.setMediaSource(source, startPositionMs)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    LaunchedEffect(torrentPlaybackState) {
        when (val st = torrentPlaybackState) {
            is TorrentPlaybackState.Ready -> {
                if (st.external) {
                    openInExternalPlayer(context, st.streamUrl, st.streamTitle)
                } else {
                    torrentFile = st.streamUrl
                    torrentRetryCount = 0
                    playTorrentUrl(st.streamUrl)
                    streamState = StreamState.ExoReady(st.streamUrl)
                }
            }
            is TorrentPlaybackState.Error -> {
                Toast.makeText(context, "Torrent stream error: ${st.message}", Toast.LENGTH_LONG).show()
            }
            else -> {}
        }
    }

    // Retry torrent playback after an error (gives the downloader time to fetch more data).
    LaunchedEffect(torrentRetryTick) {
        val url = torrentFile
        if (torrentRetryTick > 0 && url != null) {
            val resumeAt = exoPlayer.currentPosition.coerceAtLeast(0L)
            delay(2_500)
            playTorrentUrl(url, resumeAt)
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                isPlaying = isPlayingNow
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED) {
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
                    playerError = null
                }
            }

            override fun onTracksChanged(newTracks: Tracks) {
                tracks = newTracks
            }

            override fun onPlayerError(error: PlaybackException) {
                android.util.Log.e("CinemaPlayer", "code=${error.errorCodeName} cause=${error.cause}", error)
                var diagInfo = ""
                exoPlayer.currentTracks.groups.forEach { g ->
                    for (i in 0 until g.length) {
                        val f = g.getTrackFormat(i)
                        val logLine = "type=${g.type} mime=${f.sampleMimeType} codecs=${f.codecs} supported=${g.isTrackSupported(i)} selected=${g.isTrackSelected(i)}"
                        android.util.Log.e("CinemaPlayer", logLine)
                        if (g.isTrackSelected(i) || !g.isTrackSupported(i)) {
                            val kind = if (g.type == C.TRACK_TYPE_VIDEO) "Vid" else "Aud"
                            diagInfo += "$kind: ${f.sampleMimeType?.substringAfter('/') ?: f.codecs ?: "?"}(sup=${g.isTrackSupported(i)}) "
                        }
                    }
                }

                val codecFail = error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED ||
                    error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                    error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES
                val failedUrl = torrentFile
                if (failedUrl != null && codecFail) {
                    // Do not retry: decoder will fail again at the exact same spot. Offer VLC dialog.
                    exoPlayer.stop()
                    codecFailUrl = failedUrl
                    return
                }

                // Torrent playback: never jump to the web player automatically. Retry instead.
                if (torrentFile != null) {
                    if (torrentRetryCount < 3) {
                        torrentRetryCount++
                        torrentRetryTick++
                    } else {
                        torrentFile = null
                        TorrentStreamManager.stopStream()
                        exoPlayer.stop()
                        exoPlayer.clearMediaItems()
                        val msg = if (diagInfo.isNotBlank()) {
                            "Decoder error (${error.errorCodeName}):\n$diagInfo\n👉 Please use 'External Player ↗' (VLC/MX)."
                        } else {
                            "Torrent can't be played (${error.errorCodeName}). Try another stream or External Player."
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        streamState = lastTorrentState ?: StreamState.Failed
                    }
                    return
                }

                // If ExoPlayer encounters error on a stream, fallback gracefully to the web embed player
                val tmdbId = request.detail.item.id
                val isTv = request.detail.item.kind == CinemaKind.TV || request.detail.item.kind == CinemaKind.ANIME || request.seasonNumber != null
                val s = request.seasonNumber ?: 1
                val e = request.episode?.episodeNumber ?: 1
                val fallbackEmbed = if (isTv) {
                    "${CinemaRemoteConfig.getVidLinkBase()}/tv/$tmdbId/$s/$e?autoplay=true"
                } else {
                    "${CinemaRemoteConfig.getVidLinkBase()}/movie/$tmdbId?autoplay=true"
                }
                streamState = StreamState.WebReady(fallbackEmbed)
                playerError = null
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    // Pause when the app goes to the background.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) exoPlayer.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Resolve stream: if direct media, play with ExoPlayer; if embed, play with AdFree Web Player
    LaunchedEffect(server, retryTick) {
        torrentFile = null
        TorrentStreamManager.stopStream()
        streamState = StreamState.Loading
        playerError = null
        val resolvedUrl = cinemaCatching {
            resolver.resolve(request.detail, server, request.seasonNumber, request.episode)
        }.getOrNull()

        if (resolvedUrl.isNullOrBlank()) {
            streamState = StreamState.Failed
        } else if (resolvedUrl.startsWith("magnet:")) {
            exoPlayer.pause()
            val isTv = request.detail.item.kind == CinemaKind.TV || request.detail.item.kind == CinemaKind.ANIME || request.seasonNumber != null
            val season = request.seasonNumber ?: 1
            val ep = request.episode?.episodeNumber ?: 1
            val streams = if (!request.detail.imdbId.isNullOrBlank()) {
                TorrentioRepository.fetchStreams(request.detail.imdbId, isTv, season, ep)
            } else emptyList()
            val tr = StreamState.TorrentReady(resolvedUrl, streams)
            lastTorrentState = tr
            streamState = tr
        } else {
            val isDirect = resolvedUrl.substringBefore('?').lowercase().run {
                endsWith(".m3u8") || endsWith(".mp4") || endsWith(".mpd") || endsWith(".mkv")
            }
            if (isDirect) {
                val resumeAt = if (exoPlayer.mediaItemCount > 0) exoPlayer.currentPosition else 0L
                exoPlayer.setMediaItem(MediaItem.fromUri(resolvedUrl), resumeAt)
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
                streamState = StreamState.ExoReady(resolvedUrl)
            } else {
                exoPlayer.pause()
                streamState = StreamState.WebReady(resolvedUrl)
            }
        }
    }

    // Position polling for ExoPlayer
    LaunchedEffect(exoPlayer) {
        while (true) {
            if (!isScrubbing) {
                positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                val d = exoPlayer.duration
                if (d > 0L) durationMs = d
            }
            delay(500)
        }
    }

    // Portrait for loading / torrent list, landscape + immersive only while video is playing.
    val isVideoMode = streamState is StreamState.ExoReady || streamState is StreamState.WebReady

    // Restore everything when leaving the player.
    DisposableEffect(activity) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val previousOrientation = activity?.requestedOrientation
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            if (activity != null && previousOrientation != null) activity.requestedOrientation = previousOrientation
            window?.let { w ->
                w.attributes = w.attributes.apply {
                    screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
            }
        }
    }

    LaunchedEffect(activity, isVideoMode) {
        val act = activity ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(act.window, act.window.decorView)
        if (isVideoMode) {
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Auto-hide the HUD.
    LaunchedEffect(hudVisible, isPlaying, interactionTick, sheetOpen, isScrubbing, serverMenu, qualityMenu, speedMenu, streamState) {
        if (hudVisible && !sheetOpen && !isScrubbing && !serverMenu && !qualityMenu && !speedMenu) {
            delay(3_800)
            hudVisible = false
        }
    }
    LaunchedEffect(seekFlash) {
        if (seekFlash != null) {
            delay(700)
            seekFlash = null
        }
    }
    LaunchedEffect(levelHud) {
        if (levelHud != null) {
            delay(900)
            levelHud = null
        }
    }

    BackHandler(onBack = onClose)
    BackHandler(enabled = sheetOpen) { sheetOpen = false }

    val hasError = streamState is StreamState.Failed || playerError != null

    fun togglePlay() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            if (exoPlayer.playbackState == Player.STATE_ENDED) exoPlayer.seekTo(0L)
            exoPlayer.play()
        }
        interactionTick++
    }

    fun applyQuality(option: QualityOption) {
        quality = option
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon().apply {
            if (option.maxHeight == null) clearVideoSizeConstraints() else setMaxVideoSize(Int.MAX_VALUE, option.maxHeight)
        }.build()
    }

    // --- UI ----------------------------------------------------------------
    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when (val state = streamState) {
            is StreamState.WebReady -> {
                // Ad-free In-App Web Engine for guaranteed playback
                CinemaAdFreeWebPlayer(
                    embedUrl = state.embedUrl,
                    modifier = Modifier.fillMaxSize()
                )

                // Top Floating Bar for Navigation and Server Switching
                AnimatedVisibility(
                    visible = hudVisible,
                    enter = fadeIn(tween(180)),
                    exit = fadeOut(tween(220)),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)))
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            HudIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Close", onClick = onClose)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    request.detail.item.title,
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val episode = request.episode
                                val subtitle = if (episode != null) {
                                    "S${request.seasonNumber ?: 1} • E${episode.episodeNumber.toString().padStart(2, '0')} • ${episode.title}"
                                } else {
                                    listOfNotNull(request.detail.item.year.takeIf { it.isNotBlank() }, request.detail.item.duration)
                                        .joinToString(" • ")
                                }
                                if (subtitle.isNotEmpty()) {
                                    Text(
                                        subtitle,
                                        color = CinemaPalette.textSecondary,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }

                            if (servers.isNotEmpty()) {
                                Box {
                                    HudPill(icon = Icons.Rounded.Dns, label = server.name, trailing = Icons.Rounded.ExpandMore) {
                                        serverMenu = true
                                    }
                                    DropdownMenu(
                                        expanded = serverMenu,
                                        onDismissRequest = { serverMenu = false },
                                        modifier = Modifier.background(CinemaPalette.surface),
                                    ) {
                                        servers.forEach { s ->
                                            DropdownMenuItem(
                                                text = { Text("${s.name}  ·  ${s.badge}", color = Color.White) },
                                                onClick = {
                                                    server = s
                                                    serverMenu = false
                                                },
                                                trailingIcon = {
                                                    if (s.id == server.id) {
                                                        Icon(Icons.Rounded.Check, contentDescription = null, tint = accent)
                                                    }
                                                },
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.width(8.dp))
                            HudIconButton(Icons.Rounded.Refresh, "Reload") {
                                retryTick++
                            }
                        }
                    }
                }

                // Small floating toggle button when HUD auto-hides so user can easily bring it back
                if (!hudVisible) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .padding(top = 10.dp, end = 14.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.65f))
                            .clickable { hudVisible = true }
                            .padding(8.dp)
                    ) {
                        Icon(
                            Icons.Rounded.ExpandMore,
                            contentDescription = "Show Menu",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            is StreamState.TorrentReady -> {
                TorrentStreamPanel(
                    detail = request.detail,
                    seasonNumber = request.seasonNumber,
                    episode = request.episode,
                    streams = state.streams,
                    currentMagnet = state.magnetUri,
                    onExit = onClose,
                    onSwitchToWeb = {
                        val fallback = if (request.detail.item.kind == CinemaKind.TV || request.detail.item.kind == CinemaKind.ANIME || request.seasonNumber != null) {
                            "${CinemaRemoteConfig.getVidLinkBase()}/tv/${request.detail.item.id}/${request.seasonNumber ?: 1}/${request.episode?.episodeNumber ?: 1}?autoplay=true"
                        } else {
                            "${CinemaRemoteConfig.getVidLinkBase()}/movie/${request.detail.item.id}?autoplay=true"
                        }
                        server = MultiServerStreamResolver.SERVER_VIDLINK
                        streamState = StreamState.WebReady(fallback)
                    },
                    accent = accent
                )
            }

            is StreamState.ExoReady -> {
                // Native ExoPlayer Surface with Gesture HUD
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    hudVisible = !hudVisible
                                    interactionTick++
                                },
                                onDoubleTap = { offset ->
                                    val forward = offset.x > size.width / 2f
                                    if (forward) exoPlayer.seekForward() else exoPlayer.seekBack()
                                    seekFlash = SeekFlash(forward, (seekFlash?.tick ?: 0) + 1)
                                },
                            )
                        }
                        .pointerInput(Unit) {
                            var leftSide = true
                            detectVerticalDragGestures(
                                onDragStart = { start -> leftSide = start.x < size.width / 2f },
                                onDragEnd = {},
                                onDragCancel = {},
                            ) { change, dragAmount ->
                                change.consume()
                                val delta = -dragAmount / size.height * 1.4f
                                if (leftSide) {
                                    brightness = (brightness + delta).coerceIn(0.02f, 1f)
                                    activity?.window?.let { w ->
                                        w.attributes = w.attributes.apply { screenBrightness = brightness }
                                    }
                                    levelHud = LevelHud(isBrightness = true, value = brightness)
                                } else {
                                    volume = (volume + delta).coerceIn(0f, 1f)
                                    audioManager.setStreamVolume(
                                        AudioManager.STREAM_MUSIC,
                                        (volume * maxVolume).roundToInt(),
                                        0,
                                    )
                                    levelHud = LevelHud(isBrightness = false, value = volume)
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                useController = false
                                player = exoPlayer
                                setShutterBackgroundColor(Color.Black.toArgb())
                            }
                        },
                        update = { view ->
                            view.resizeMode = when (aspect) {
                                AspectMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                AspectMode.RATIO_16_9 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                AspectMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            }
                        },
                        modifier = if (aspect == AspectMode.RATIO_16_9) {
                            Modifier.aspectRatio(16f / 9f)
                        } else {
                            Modifier.fillMaxSize()
                        },
                    )

                    // Double-tap ripple
                    seekFlash?.let { flash ->
                        SeekRipple(
                            flash = flash,
                            modifier = Modifier
                                .align(if (flash.forward) Alignment.CenterEnd else Alignment.CenterStart)
                                .fillMaxHeight()
                                .fillMaxWidth(0.34f),
                        )
                    }

                    // Brightness / volume indicator
                    levelHud?.let { hud ->
                        LevelIndicator(
                            hud = hud,
                            accent = accent,
                            modifier = Modifier
                                .align(if (hud.isBrightness) Alignment.CenterStart else Alignment.CenterEnd)
                                .windowInsetsPadding(WindowInsets.safeDrawing)
                                .padding(horizontal = 28.dp),
                        )
                    }

                    // ExoPlayer HUD
                    AnimatedVisibility(
                        visible = hudVisible,
                        enter = fadeIn(tween(180)),
                        exit = fadeOut(tween(220)),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Box(Modifier.fillMaxSize()) {
                            Box(
                                Modifier
                                    .align(Alignment.TopCenter)
                                    .fillMaxWidth()
                                    .height(110.dp)
                                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent))),
                            )
                            Box(
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .height(150.dp)
                                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)))),
                            )

                            // Top bar
                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .fillMaxWidth()
                                    .windowInsetsPadding(WindowInsets.safeDrawing)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                HudIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Close", onClick = onClose)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        request.detail.item.title,
                                        color = Color.White,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    val episode = request.episode
                                    val subtitle = if (episode != null) {
                                        "S${request.seasonNumber ?: 1} • E${episode.episodeNumber.toString().padStart(2, '0')} • ${episode.title}"
                                    } else {
                                        listOfNotNull(request.detail.item.year.takeIf { it.isNotBlank() }, request.detail.item.duration)
                                            .joinToString(" • ")
                                    }
                                    if (subtitle.isNotEmpty()) {
                                        Text(
                                            subtitle,
                                            color = CinemaPalette.textSecondary,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                if (servers.isNotEmpty()) {
                                    Box {
                                        HudPill(icon = Icons.Rounded.Dns, label = server.name, trailing = Icons.Rounded.ExpandMore) {
                                            serverMenu = true
                                            interactionTick++
                                        }
                                        DropdownMenu(
                                            expanded = serverMenu,
                                            onDismissRequest = { serverMenu = false },
                                            modifier = Modifier.background(CinemaPalette.surface),
                                        ) {
                                            servers.forEach { s ->
                                                DropdownMenuItem(
                                                    text = { Text("${s.name}  ·  ${s.badge}", color = Color.White) },
                                                    onClick = {
                                                        server = s
                                                        serverMenu = false
                                                    },
                                                    trailingIcon = {
                                                        if (s.id == server.id) {
                                                            Icon(Icons.Rounded.Check, contentDescription = null, tint = accent)
                                                        }
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Center controls
                            Row(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalArrangement = Arrangement.spacedBy(36.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                HudIconButton(Icons.Rounded.Replay10, "Rewind 10 seconds", size = 52.dp) {
                                    exoPlayer.seekBack()
                                    interactionTick++
                                }
                                Box(
                                    Modifier
                                        .size(76.dp)
                                        .clip(CircleShape)
                                        .background(accent.copy(alpha = 0.92f))
                                        .clickable { togglePlay() },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (isBuffering) {
                                        CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(34.dp))
                                    } else {
                                        Icon(
                                            if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                            contentDescription = if (isPlaying) "Pause" else "Play",
                                            tint = Color.White,
                                            modifier = Modifier.size(42.dp),
                                        )
                                    }
                                }
                                HudIconButton(Icons.Rounded.Forward10, "Forward 10 seconds", size = 52.dp) {
                                    exoPlayer.seekForward()
                                    interactionTick++
                                }
                            }

                            // Bottom bar
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .windowInsetsPadding(WindowInsets.safeDrawing)
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            ) {
                                val fraction = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
                                Slider(
                                    value = if (isScrubbing) scrubFraction else fraction,
                                    onValueChange = {
                                        isScrubbing = true
                                        scrubFraction = it
                                        interactionTick++
                                    },
                                    onValueChangeFinished = {
                                        val target = (scrubFraction * durationMs).toLong()
                                        exoPlayer.seekTo(target)
                                        positionMs = target
                                        isScrubbing = false
                                    },
                                    enabled = durationMs > 0L,
                                    colors = SliderDefaults.colors(
                                        thumbColor = accent,
                                        activeTrackColor = accent,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                                        disabledThumbColor = Color.White.copy(alpha = 0.3f),
                                        disabledActiveTrackColor = Color.White.copy(alpha = 0.3f),
                                        disabledInactiveTrackColor = Color.White.copy(alpha = 0.15f),
                                    ),
                                    modifier = Modifier.height(28.dp),
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val shown = if (isScrubbing) (scrubFraction * durationMs).toLong() else positionMs
                                    Text(
                                        "${formatTime(shown)} / ${formatTime(durationMs)}",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Spacer(Modifier.weight(1f))

                                    // Quality menu: only for web/HLS streams with dynamic transcoding.
                                    // Hidden during torrent playback since raw MKV is a fixed file.
                                    if (torrentFile == null) {
                                        Box {
                                            HudPill(icon = Icons.Rounded.HighQuality, label = quality.label) {
                                                qualityMenu = true
                                                interactionTick++
                                            }
                                            DropdownMenu(
                                                expanded = qualityMenu,
                                                onDismissRequest = { qualityMenu = false },
                                                modifier = Modifier.background(CinemaPalette.surface),
                                            ) {
                                                QualityOption.entries.forEach { option ->
                                                    DropdownMenuItem(
                                                        text = { Text(option.label, color = Color.White) },
                                                        onClick = {
                                                            applyQuality(option)
                                                            qualityMenu = false
                                                        },
                                                        trailingIcon = {
                                                            if (option == quality) {
                                                                Icon(Icons.Rounded.Check, contentDescription = null, tint = accent)
                                                            }
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(Modifier.width(8.dp))
                                    }

                                    // Playback speed control
                                    Box {
                                        HudPill(icon = Icons.Rounded.Speed, label = "${if (playbackSpeed == 1f) "1.0" else playbackSpeed}x") {
                                            speedMenu = true
                                            interactionTick++
                                        }
                                        DropdownMenu(
                                            expanded = speedMenu,
                                            onDismissRequest = { speedMenu = false },
                                            modifier = Modifier.background(CinemaPalette.surface),
                                        ) {
                                            listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { s ->
                                                DropdownMenuItem(
                                                    text = { Text("${s}x", color = Color.White) },
                                                    onClick = {
                                                        playbackSpeed = s
                                                        exoPlayer.setPlaybackSpeed(s)
                                                        speedMenu = false
                                                        interactionTick++
                                                    },
                                                    trailingIcon = {
                                                        if (playbackSpeed == s) {
                                                            Icon(Icons.Rounded.Check, contentDescription = null, tint = accent)
                                                        }
                                                    },
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.width(8.dp))

                                    HudPill(icon = Icons.Rounded.Subtitles, label = "Audio & Subs") {
                                        sheetOpen = true
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    HudPill(icon = Icons.Rounded.AspectRatio, label = aspect.label) {
                                        aspect = aspect.next()
                                        interactionTick++
                                    }
                                }
                            }
                        }
                    }
                }
            }

            is StreamState.Loading -> {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = accent)
                    Spacer(Modifier.height(12.dp))
                    Text("Connecting to secure stream…", color = CinemaPalette.textSecondary, fontSize = 13.sp)
                }
            }

            is StreamState.Failed -> {
                ErrorPanel(
                    message = if (request.detail.isUnreleased) {
                        "This title has not been released or uploaded to streaming servers yet."
                    } else {
                        playerError ?: "This server stream is currently unavailable."
                    },
                    isUnreleased = request.detail.isUnreleased,
                    canSwitchServer = servers.size > 1,
                    trailerKey = request.detail.trailerYoutubeKey,
                    onWatchTrailer = { key ->
                        try {
                            val appIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("vnd.youtube:$key")).apply {
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(appIntent)
                        } catch (_: Exception) {
                            val webIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com/watch?v=$key")).apply {
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(webIntent)
                        }
                    },
                    onRetry = { retryTick++ },
                    onNextServer = {
                        val index = servers.indexOfFirst { it.id == server.id }
                        server = servers[(index + 1).mod(servers.size)]
                    },
                    onClose = onClose,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }

        // Subtitles / audio sheet for ExoPlayer
        if (sheetOpen) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { sheetOpen = false },
            )
        }
        AnimatedVisibility(
            visible = sheetOpen,
            enter = slideInVertically(tween(240)) { it } + fadeIn(),
            exit = slideOutVertically(tween(200)) { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            val textGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT && it.isSupported }
            val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO && it.isSupported }

            val subtitleOptions = buildList {
                add(
                    TrackOption("Off", textGroups.none { it.isSelected }) {
                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                            .build()
                    },
                )
                textGroups.forEachIndexed { index, group ->
                    add(
                        TrackOption(trackLabel(group, index), group.isSelected) {
                            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
                                .build()
                        },
                    )
                }
            }
            val audioOptions = audioGroups.mapIndexed { index, group ->
                TrackOption(trackLabel(group, index), group.isSelected) {
                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                        .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
                        .build()
                }
            }

            Column(
                Modifier
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(CinemaPalette.surface)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { /* swallow taps */ }
                    .padding(16.dp),
            ) {
                Text(
                    "Audio & Subtitles",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.heightIn(max = 240.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TrackColumn("Subtitles", subtitleOptions, accent, Modifier.weight(1f))
                    TrackColumn("Audio", audioOptions, accent, Modifier.weight(1f))
                }
            }
        }

        codecFailUrl?.let { url ->
            AlertDialog(
                onDismissRequest = { },
                title = { Text("Can't decode this video in-app") },
                text = {
                    Text(
                        "This file uses a video format (most likely 10-bit HEVC) that your phone's " +
                            "hardware decoder doesn't support. VLC can play it with its software decoder, " +
                            "though heavy files may stutter on mid-range chips."
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        openInExternalPlayer(context, url, request.detail.item.title)
                        codecFailUrl = null
                        torrentFile = null
                        // Keep torrent server running so VLC can stream from it; return to stream list
                        streamState = lastTorrentState ?: StreamState.Failed
                    }) { Text("Play with VLC ↗") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        codecFailUrl = null
                        torrentFile = null
                        TorrentStreamManager.stopStream()
                        exoPlayer.clearMediaItems()
                        streamState = lastTorrentState ?: StreamState.Failed
                    }) { Text("Cancel") }
                },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Ad-Free In-App Web Player Component
// ---------------------------------------------------------------------------

private val ALLOWED_EMBED_DOMAINS = listOf(
    "vidlink.pro",
    "vidsrc.sh",
    "vidsrc.to",
    "vidsrc.me",
    "vidsrc.in",
    "vidsrc.pm",
    "vidsrc.net",
    "vidsrc.xyz",
    "vidsrc.stream",
    "vidsrc.buzz",
    "vidsrc.vip",
    "vidsrc.cc",
    "2embed.cc",
    "2embed.org",
    "2embed.skin",
    "streamsrcs.2embed.cc",
    "streamsrc.2embed.cc",
    "cloudnest.cc",
    "cloudnest.org",
    "cloudnest.top",
    "rcp.vidsrc.vip",
    "streamwish.to",
    "filemoon.sx",
    "vidmoly.me",
    "autoembed.cc",
    "autoembed.to",
    "megacloud.tv",
    "rabbitstream.net",
    "embed.su",
    "multiembed.mov",
    "jwplayer.com",
    "cloudflare.com",
    "googleapis.com",
    "gstatic.com",
    "jsdelivr.net",
    "wsrv.nl"
)

private val BLOCKED_AD_DOMAINS = listOf(
    "adsterra",
    "popads",
    "propellerads",
    "monetag",
    "clickadu",
    "exoclick",
    "trafficjunky",
    "doubleclick",
    "googleads",
    "adservice",
    "bet365",
    "1xbet",
    "parimatch",
    "mostbet",
    "melbet",
    "histats",
    "statcounter",
    "popunder",
    "deliverads",
    "adx"
)

private fun isAdRequest(url: String, host: String): Boolean {
    val lowerHost = host.lowercase()
    if (BLOCKED_AD_DOMAINS.any { lowerHost.contains(it) }) return true
    val lower = url.lowercase()
    return lower.contains("/popunder") || lower.contains("/adsterra")
}

@Composable
fun CinemaAdFreeWebPlayer(
    embedUrl: String,
    modifier: Modifier = Modifier,
    onProgress: (Int) -> Unit = {}
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isUnmuted by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> webViewInstance?.onPause()
                Lifecycle.Event.ON_RESUME -> webViewInstance?.onResume()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            webViewInstance?.apply {
                stopLoading()
                loadUrl("about:blank")
                destroy()
            }
            webViewInstance = null
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(android.graphics.Color.BLACK)

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        allowFileAccess = false
                        allowContentAccess = false
                        loadWithOverviewMode = false
                        useWideViewPort = false
                        setSupportZoom(false)
                        builtInZoomControls = false
                        displayZoomControls = false
                        setSupportMultipleWindows(false)
                        javaScriptCanOpenWindowsAutomatically = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
                        cacheMode = WebSettings.LOAD_DEFAULT
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onCreateWindow(
                            view: WebView?,
                            isDialog: Boolean,
                            isUserGesture: Boolean,
                            resultMsg: android.os.Message?
                        ): Boolean {
                            // Strict popup blocker: NEVER open popups or new windows!
                            return false
                        }

                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            super.onProgressChanged(view, newProgress)
                            onProgress(newProgress)
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val url = request?.url ?: return true
                            val urlStr = url.toString()
                            val lower = urlStr.lowercase()
                            val host = url.host?.lowercase().orEmpty()

                            // 1. Strict Protocol Guard: block tel, sms, intent, market, etc.
                            if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
                                return true
                            }

                            // 2. Block known ad tracker patterns
                            if (isAdRequest(urlStr, host)) {
                                return true
                            }

                            // 3. Main-Frame Navigation Guard:
                            // Prevent malicious popunders from redirecting top-level window to external phishing/billing sites
                            if (request?.isForMainFrame == true) {
                                val isAllowed = ALLOWED_EMBED_DOMAINS.any { allowed ->
                                    host == allowed || host.endsWith(".$allowed")
                                }
                                if (!isAllowed) {
                                    return true
                                }
                            }

                            // Sub-frames and player CDN resources are allowed to load
                            return false
                        }

                        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                            val urlStr = request?.url?.toString() ?: return null
                            val host = request?.url?.host?.lowercase().orEmpty()
                            if (isAdRequest(urlStr, host)) {
                                return WebResourceResponse(
                                    "text/plain",
                                    "UTF-8",
                                    ByteArrayInputStream(ByteArray(0))
                                )
                            }
                            return super.shouldInterceptRequest(view, request)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            val js = """
                                (function() {
                                    var style = document.createElement('style');
                                    style.type = 'text/css';
                                    style.innerHTML = 'html, body { width: 100vw !important; height: 100vh !important; margin: 0 !important; padding: 0 !important; overflow: hidden !important; background: #000000 !important; } iframe { width: 100% !important; height: 100% !important; border: none !important; } .popunder, a[target="_blank"] { display: none !important; }';
                                    document.head.appendChild(style);
                                    window.open = function() { return null; };
                                    window.alert = function() { return null; };
                                    window.confirm = function() { return true; };

                                    function forceUnmute() {
                                        try {
                                            if (window.jwplayer && typeof window.jwplayer === 'function') {
                                                var p = window.jwplayer();
                                                if (p && typeof p.setMute === 'function') {
                                                    p.setMute(false);
                                                    p.setVolume(100);
                                                }
                                            }
                                        } catch(e) {}
                                        try {
                                            var vids = document.querySelectorAll('video');
                                            for (var i = 0; i < vids.length; i++) {
                                                vids[i].muted = false;
                                                vids[i].volume = 1.0;
                                            }
                                        } catch(e) {}
                                        try {
                                            var muteBtns = document.querySelectorAll('.jw-icon-volume, .jw-icon-volume-off, .art-control-volume, button[aria-label*="mute"], button[aria-label*="Mute"], button[aria-label*="unmute"], button[aria-label*="Unmute"]');
                                            for (var j = 0; j < muteBtns.length; j++) {
                                                muteBtns[j].click();
                                            }
                                        } catch(e) {}
                                    }
                                    forceUnmute();
                                    setTimeout(forceUnmute, 600);
                                    setTimeout(forceUnmute, 1500);
                                    setTimeout(forceUnmute, 3000);
                                    window.addEventListener('click', forceUnmute);
                                    window.addEventListener('touchstart', forceUnmute);
                                })();
                            """.trimIndent()
                            view?.evaluateJavascript(js, null)
                        }
                    }

                    webViewInstance = this
                    loadUrl(embedUrl)
                }
            },
            update = { webView ->
                if (webView.url != embedUrl) {
                    webView.loadUrl(embedUrl)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Floating Unmute Pill: If mobile browser muted autoplay audio, user can tap once to unmute!
        AnimatedVisibility(
            visible = !isUnmuted,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(bottom = 20.dp, end = 20.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, Color(0xFF64B5F6).copy(alpha = 0.6f)),
                shadowElevation = 8.dp,
                modifier = Modifier.clickable {
                    isUnmuted = true
                    webViewInstance?.evaluateJavascript(
                        """
                        (function() {
                            try {
                                if (window.jwplayer && typeof window.jwplayer === 'function') {
                                    var p = window.jwplayer();
                                    if (p && typeof p.setMute === 'function') {
                                        p.setMute(false);
                                        p.setVolume(100);
                                    }
                                }
                            } catch(e) {}
                            try {
                                var vids = document.querySelectorAll('video');
                                for (var i = 0; i < vids.length; i++) {
                                    vids[i].muted = false;
                                    vids[i].volume = 1.0;
                                }
                            } catch(e) {}
                            try {
                                var muteBtns = document.querySelectorAll('.jw-icon-volume, .jw-icon-volume-off, .art-control-volume, button[aria-label*="mute"], button[aria-label*="Mute"], button[aria-label*="unmute"], button[aria-label*="Unmute"]');
                                for (var j = 0; j < muteBtns.length; j++) {
                                    muteBtns[j].click();
                                }
                            } catch(e) {}
                        })();
                        """.trimIndent(),
                        null
                    )
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = "Unmute",
                        tint = Color(0xFF64B5F6),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Tap to Unmute 🔊",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// HUD pieces
// ---------------------------------------------------------------------------

@Composable
private fun HudIconButton(
    icon: ImageVector,
    description: String,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 42.dp,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.1f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(size * 0.55f))
    }
}

@Composable
private fun HudPill(
    icon: ImageVector,
    label: String,
    trailing: ImageVector? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(Color.White.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        if (trailing != null) {
            Spacer(Modifier.width(2.dp))
            Icon(trailing, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun SeekRipple(flash: SeekFlash, modifier: Modifier = Modifier) {
    val scale = remember(flash.tick) { Animatable(0.6f) }
    val alpha = remember(flash.tick) { Animatable(1f) }
    LaunchedEffect(flash.tick) {
        launch { scale.animateTo(1.15f, tween(420)) }
        alpha.animateTo(0f, tween(600, delayMillis = 100))
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(150.dp)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    this.alpha = alpha.value
                }
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    if (flash.forward) Icons.Rounded.FastForward else Icons.Rounded.FastRewind,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(36.dp),
                )
                Text("10 s", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun LevelIndicator(hud: LevelHud, accent: Color, modifier: Modifier = Modifier) {
    val icon = when {
        hud.isBrightness -> Icons.Rounded.BrightnessMedium
        hud.value <= 0.01f -> Icons.AutoMirrored.Rounded.VolumeOff
        else -> Icons.AutoMirrored.Rounded.VolumeUp
    }
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 14.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        Box(
            Modifier
                .height(110.dp)
                .width(5.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.25f)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(hud.value.coerceIn(0f, 1f))
                    .background(accent),
            )
        }
        Text("${(hud.value * 100).roundToInt()}%", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ErrorPanel(
    message: String,
    isUnreleased: Boolean = false,
    canSwitchServer: Boolean,
    trailerKey: String? = null,
    onWatchTrailer: ((String) -> Unit)? = null,
    onRetry: () -> Unit,
    onNextServer: () -> Unit,
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val accent = cinemaAccent()
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.82f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (isUnreleased) "Stream Not Available Yet" else "Can't play this stream",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            color = CinemaPalette.textSecondary,
            fontSize = 12.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!trailerKey.isNullOrBlank() && onWatchTrailer != null) {
                Button(
                    onClick = { onWatchTrailer(trailerKey) },
                    shape = RoundedCornerShape(percent = 50),
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White),
                ) {
                    Icon(Icons.Rounded.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Watch Trailer")
                }
            } else {
                Button(
                    onClick = onRetry,
                    shape = RoundedCornerShape(percent = 50),
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White),
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Retry")
                }
            }
            if (canSwitchServer) {
                OutlinedButton(
                    onClick = onNextServer,
                    shape = RoundedCornerShape(percent = 50),
                ) {
                    Text("Try next server", color = Color.White)
                }
            }
            HudIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Close", size = 38.dp, onClick = onClose)
        }
    }
}

@Composable
private fun TrackColumn(
    title: String,
    options: List<TrackOption>,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(title, color = CinemaPalette.textSecondary, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(6.dp))
        if (options.isEmpty()) {
            Text("Not available", color = CinemaPalette.textSecondary, fontSize = 13.sp)
        } else {
            LazyColumn {
                items(options) { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = option.onSelect)
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            option.label,
                            color = if (option.selected) accent else Color.White,
                            fontWeight = if (option.selected) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (option.selected) {
                            Icon(Icons.Rounded.Check, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun trackLabel(group: Tracks.Group, index: Int): String {
    val format = group.getTrackFormat(0)
    val language = format.language
        ?.takeIf { it != "und" }
        ?.let { Locale.forLanguageTag(it).getDisplayLanguage(Locale.getDefault()) }
        ?.replaceFirstChar { it.uppercase() }
    return format.label ?: language ?: "Track ${index + 1}"
}

private fun initialBrightness(activity: Activity?): Float {
    if (activity == null) return 0.5f
    val windowValue = activity.window.attributes.screenBrightness
    if (windowValue >= 0f) return windowValue
    val system = Settings.System.getInt(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
    return (system / 255f).coerceIn(0.02f, 1f)
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    fun two(v: Long) = v.toString().padStart(2, '0')
    return if (hours > 0) "$hours:${two(minutes)}:${two(seconds)}" else "${two(minutes)}:${two(seconds)}"
}

// ---------------------------------------------------------------------------
// Torrentio Streams Panel
// ---------------------------------------------------------------------------

@Composable
private fun TorrentStreamPanel(
    detail: CinemaDetail,
    seasonNumber: Int?,
    episode: Episode?,
    streams: List<TorrentStream>,
    currentMagnet: String,
    onExit: () -> Unit,
    onSwitchToWeb: () -> Unit,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var isStrictCountry by remember { mutableStateOf(GeoSafety.isStrictCountry(context)) }
    var detectedCountry by remember { mutableStateOf(GeoSafety.getDetectedCountry(context)) }

    LaunchedEffect(Unit) {
        GeoSafety.refreshIpCountry(context)
        isStrictCountry = GeoSafety.isStrictCountry(context)
        detectedCountry = GeoSafety.getDetectedCountry(context)
    }

    var pendingCellularStream by remember { mutableStateOf<TorrentStream?>(null) }
    var pendingExternal by remember { mutableStateOf(false) }

    Box(
        modifier
            .fillMaxSize()
            .background(Color(0xFF0A0C12))
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Header Row
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.1f))
                            .clickable(onClick = onExit),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        val titleText = if (episode != null) {
                            "${detail.item.title} • S$seasonNumber E${episode.episodeNumber}"
                        } else {
                            detail.item.title
                        }
                        Text(
                            titleText,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "⚡ Torrentio Engine",
                                color = accent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "• ${streams.size} streams found",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.width(8.dp))

                Button(
                    onClick = onSwitchToWeb,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.12f),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Web Player", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(Modifier.height(14.dp))

            // Quick In-App Play Banner
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = accent.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onSwitchToWeb)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.PlayCircle,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Watch directly In-App without external apps",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            "Instant cloud stream on VidLink (Fast 1080p)",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                    }
                    Button(
                        onClick = onSwitchToWeb,
                        colors = ButtonDefaults.buttonColors(containerColor = accent),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Play In-App", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (isStrictCountry) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF2E1515),
                    border = BorderStroke(1.dp, Color(0xFFEF5350)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Security,
                                contentDescription = null,
                                tint = Color(0xFFEF5350),
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Auto-Safety Protection Active",
                                color = Color(0xFFEF5350),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "You are connecting from ${GeoSafety.getCountryName(detectedCountry)} ($detectedCountry). " +
                                "To protect you from strict ISP copyright tracking and legal fines in this region, " +
                                "P2P torrent streaming is disabled. Please enjoy this title using the secure Instant Web Player.",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = onSwitchToWeb,
                            colors = ButtonDefaults.buttonColors(containerColor = accent),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Text("Switch to Instant Web Player", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                val torrentPlaybackState by TorrentStreamManager.playbackState.collectAsState()

                val buf = torrentPlaybackState as? TorrentPlaybackState.Buffering
                if (buf != null) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF161C2C),
                        border = BorderStroke(1.dp, accent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    progress = { (buf.bufferPercent / 100f).coerceIn(0f, 1f) },
                                    color = accent,
                                    modifier = Modifier.size(26.dp),
                                    strokeWidth = 3.dp
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Buffering P2P Stream in Lytrix (${buf.bufferPercent}%)",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        buf.streamTitle,
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = { TorrentStreamManager.stopStream() }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Cancel", tint = Color.White)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("👤 ${buf.seeds} peers connected", color = Color(0xFF66BB6A), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                val speedKb = buf.speedBytesPerSec / 1024
                                val speedStr = if (speedKb > 1024) "${String.format("%.1f", speedKb / 1024f)} MB/s" else "$speedKb KB/s"
                                Text("🚀 Speed: $speedStr", color = accent, fontSize = 11.sp)
                            }
                        }
                    }
                }

                val ready = torrentPlaybackState as? TorrentPlaybackState.Ready
                if (ready != null) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF161C2C),
                        border = BorderStroke(1.dp, accent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Row(
                            Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Streaming to external player",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    ready.streamTitle,
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Button(
                                onClick = { TorrentStreamManager.stopStream() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Stop", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                if (streams.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "No direct P2P streams found for this title.",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 14.sp
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = onSwitchToWeb,
                                colors = ButtonDefaults.buttonColors(containerColor = accent),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Text("Switch to Instant Web Player", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    val sortedStreams = remember(streams) { streams.sortedByDescending { it.compat().rank } }
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(sortedStreams) { stream ->
                            TorrentStreamCard(
                                stream = stream,
                                accent = accent,
                                onPlay = {
                                    if (NetworkUtils.isCellular(context)) {
                                        pendingCellularStream = stream
                                        pendingExternal = false
                                    } else {
                                        TorrentStreamManager.startStream(context, stream.magnetUri, stream.filename, stream.fileIdx)
                                    }
                                },
                                onExternalPlay = {
                                    if (NetworkUtils.isCellular(context)) {
                                        pendingCellularStream = stream
                                        pendingExternal = true
                                    } else {
                                        Toast.makeText(context, "Preparing stream for external player...", Toast.LENGTH_SHORT).show()
                                        TorrentStreamManager.startStream(context, stream.magnetUri, stream.filename, stream.fileIdx, external = true)
                                    }
                                },
                                onDownload = {
                                    val downloadTitle = if (episode != null) {
                                        "${detail.item.title} S${seasonNumber ?: 1}E${episode.episodeNumber}"
                                    } else {
                                        detail.item.title
                                    }
                                    TorrentDownloadManager.startDownload(
                                        context = context,
                                        magnetUri = stream.magnetUri,
                                        title = downloadTitle,
                                        quality = stream.quality,
                                        sizeStr = stream.sizeStr,
                                        posterUrl = detail.item.posterUrl,
                                        fileIdx = stream.fileIdx
                                    )
                                    Toast.makeText(context, "Download started! See Downloads tab", Toast.LENGTH_SHORT).show()
                                },
                                onCopy = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Magnet Link", stream.magnetUri))
                                    Toast.makeText(context, "Magnet link copied to clipboard!", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }
        }

        pendingCellularStream?.let { stream ->
            AlertDialog(
                onDismissRequest = { pendingCellularStream = null },
                title = { Text("⚠️ Mobile Data Warning") },
                text = {
                    val sizeText = stream.sizeStr.ifBlank { "approx. 2.3 GB" }
                    Text(
                        "You are currently connected to Mobile Data (SIM). Streaming this high-definition movie ($sizeText) may consume a significant amount of your data pack.\n\nDo you want to proceed?"
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val s = stream
                            val ext = pendingExternal
                            pendingCellularStream = null
                            if (ext) {
                                Toast.makeText(context, "Preparing stream for external player...", Toast.LENGTH_SHORT).show()
                                TorrentStreamManager.startStream(context, s.magnetUri, s.filename, s.fileIdx, external = true)
                            } else {
                                TorrentStreamManager.startStream(context, s.magnetUri, s.filename, s.fileIdx)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = accent)
                    ) {
                        Text("Proceed Anyway", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingCellularStream = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun TorrentStreamCard(
    stream: TorrentStream,
    accent: Color,
    onPlay: () -> Unit,
    onExternalPlay: () -> Unit,
    onDownload: () -> Unit,
    onCopy: () -> Unit,
) {
    val compat = remember(stream) { stream.compat() }

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF141822))
            .border(
                1.dp,
                if (stream.isHindiOrDual) accent.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(14.dp)
            )
            .padding(14.dp)
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Quality badge
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(accent.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(stream.quality, color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    // Dynamic Language Badge
                    val badgeColor = when {
                        stream.languageLabel.contains("Hindi") -> Color(0xFF2E7D32)
                        stream.languageLabel.contains("Bengali") -> Color(0xFF00796B)
                        stream.languageLabel.contains("Multi") -> Color(0xFF1565C0)
                        else -> Color(0xFF455A64)
                    }
                    val textColor = when {
                        stream.languageLabel.contains("Hindi") -> Color(0xFFA5D6A7)
                        stream.languageLabel.contains("Bengali") -> Color(0xFF80CBC4)
                        stream.languageLabel.contains("Multi") -> Color(0xFF90CAF9)
                        else -> Color(0xFFCFD8DC)
                    }

                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(badgeColor.copy(alpha = 0.5f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(stream.languageLabel, color = textColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    if (stream.sizeStr.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stream.sizeStr,
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                    }
                }

                // Seeders
                Text(
                    "👤 ${stream.seeders} seeders",
                    color = if (stream.seeders > 50) Color(0xFF66BB6A) else Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                stream.filename,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            compat.badge?.let { label ->
                Spacer(Modifier.height(8.dp))
                val (bg, fg) = when (compat.playableInApp) {
                    true -> Color(0xFF2E7D32) to Color(0xFFA5D6A7)
                    false -> Color(0xFFB71C1C) to Color(0xFFEF9A9A)
                    null -> Color(0xFFE65100) to Color(0xFFFFCC80)
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(bg.copy(alpha = 0.5f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(label, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(12.dp))

            // Action Buttons Row 1: Stream in-app & External Player
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onPlay,
                    colors = ButtonDefaults.buttonColors(containerColor = accent),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 9.dp)
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Stream In-App", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onExternalPlay,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(vertical = 9.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("External Player ↗", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(6.dp))

            // Action Buttons Row 2: Download & Copy Magnet
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDownload,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(vertical = 7.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White.copy(alpha = 0.85f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Open in Torrent App ⬇", fontSize = 11.sp)
                }

                OutlinedButton(
                    onClick = onCopy,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(vertical = 7.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White.copy(alpha = 0.85f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Copy Magnet 📋", fontSize = 11.sp)
                }
            }
        }
    }
}
