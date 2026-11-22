package com.musicplayer.lytrix.ui.component

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.musicplayer.lytrix.R
import com.musicplayer.lytrix.spotify.canvas.VisualMediaResult
import com.musicplayer.lytrix.spotify.canvas.VisualMediaType
import com.musicplayer.lytrix.spotify.canvas.CanvasVideoPlayerManager
import com.musicplayer.lytrix.model.Song
import com.musicplayer.lytrix.ui.rememberEmbeddedArt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private const val SCALLOP_STEPS = 384
private const val NATURAL_WINDOW_MS = 15_000L
private const val TAKEOVER_MS = 3_000f
private const val DISC_SCALE = 0.58f
private const val DECK_OFFSET = 0.24f
private val DEG_TO_RAD = (PI / 180.0).toFloat()
private val SquircleShape = RoundedCornerShape(20.dp)

/**
 * Squircle (rounded rect, exact same geometry as RoundedCornerShape) that morphs into a
 * 16-lobed flower. Only the lobes rotate - the image inside never rotates, so no
 * counter-rotation layers are needed.
 *
 * At morphProgress = 0 it returns a real RoundedCornerShape outline, so the final
 * landing frame is pixel-identical to the normal static album art.
 */
class MorphingScallopShape(
    private val morphProgress: Float,
    private val cornerRadiusPx: Float = 56f,
    private val numLobes: Int = 16,
    private val amplitude: Float = 0.05f,
    private val rotationRad: Float = 0f
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val progress = morphProgress.coerceIn(0f, 1f)
        if (progress <= 0.001f) {
            return Outline.Rounded(
                RoundRect(
                    rect = Rect(0f, 0f, size.width, size.height),
                    cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
                )
            )
        }

        val hx = size.width / 2f
        val hy = size.height / 2f
        val maxR = min(hx, hy)
        val cr = min(cornerRadiusPx, maxR)
        val ccx = hx - cr
        val ccy = hy - cr
        val cornerConst = ccx * ccx + ccy * ccy - cr * cr
        val step = (2.0 * PI).toFloat() / SCALLOP_STEPS

        val path = Path()
        for (i in 0 until SCALLOP_STEPS) {
            val theta = i * step
            val c = cos(theta)
            val s = sin(theta)
            val ac = abs(c)
            val asn = abs(s)

            // Distance from center to the rounded-rect boundary along this direction
            val tx = if (ac > 1e-6f) hx / ac else Float.MAX_VALUE
            val ty = if (asn > 1e-6f) hy / asn else Float.MAX_VALUE
            val tEdge = min(tx, ty)
            val rBox = if (tEdge * ac > ccx && tEdge * asn > ccy) {
                val b = ac * ccx + asn * ccy
                b + sqrt(max(b * b - cornerConst, 0f))
            } else {
                tEdge
            }

            // Flower radius, never larger than maxR so petals are not cut off
            val wave = cos(numLobes * (theta - rotationRad))
            val rFlower = maxR * (1f - amplitude * (1f - wave))

            val r = (1f - progress) * rBox + progress * rFlower
            val x = hx + r * c
            val y = hy + r * s
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

private fun buildArtRequest(
    context: Context,
    song: Song,
    effectiveArtData: Any?
): ImageRequest {
    val dataToLoad = effectiveArtData ?: song.albumArtUriString ?: song.uriString
    return ImageRequest.Builder(context)
        .data(dataToLoad)
        .apply {
            if ((effectiveArtData == null || dataToLoad is String) && song.isVideo && dataToLoad == song.uriString) {
                videoFrameMillis(1000)
            }
        }
        .crossfade(false)
        .build()
}

/**
 * Album art disc.
 * Background = ambient blurred copy of the cover with ContentScale.Crop, blur(25.dp), alpha(0.65f)
 * (fills to the very edge, perfectly matching Meld Now Playing without black gaps),
 * foreground = sharp cover with ContentScale.Fit (never zoomed / cropped).
 */
@OptIn(UnstableApi::class)
@Composable
fun ArtworkDisc(
    song: Song,
    artData: Any?,
    modifier: Modifier = Modifier,
    layerTransform: (GraphicsLayerScope.() -> Unit)? = null,
    shapeProvider: () -> Shape = { SquircleShape },
    shadowAlpha: () -> Float = { 1f },
    onLongPress: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onDoubleTap: ((Offset, Float) -> Unit)? = null,
    onSwipeLeft: (() -> Unit)? = null,
    onSwipeRight: (() -> Unit)? = null,
    gesturesEnabled: Boolean = false,
    visualMedia: VisualMediaResult? = null,
    isPlaying: Boolean = false
) {
    val context = LocalContext.current
    val effectiveArtData = artData ?: rememberEmbeddedArt(context, song)
    val imageRequest = remember(effectiveArtData, song.uriString, song.albumArtUriString) {
        buildArtRequest(context, song, effectiveArtData)
    }
    val latestOnClick by rememberUpdatedState(onClick)
    val latestOnLongPress by rememberUpdatedState(onLongPress)
    val latestOnDoubleTap by rememberUpdatedState(onDoubleTap)
    val latestOnSwipeLeft by rememberUpdatedState(onSwipeLeft)
    val latestOnSwipeRight by rememberUpdatedState(onSwipeRight)
    val latestGesturesEnabled by rememberUpdatedState(gesturesEnabled)

    val density = LocalDensity.current
    val swipeThresholdPx = remember(density) { with(density) { 56.dp.toPx() } }

    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    var isHoldingForSpeed by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            if (isHoldingForSpeed) {
                isHoldingForSpeed = false
                com.musicplayer.lytrix.service.PlaybackService.setSpeedBoost(false)
            }
        }
    }

    val tapModifier = if (onClick != null || onLongPress != null || onDoubleTap != null || gesturesEnabled) {
        Modifier.pointerInput(song.id, gesturesEnabled) {
            detectTapGestures(
                onPress = {
                    if (latestGesturesEnabled) {
                        val speedJob = coroutineScope.launch {
                            delay(350L)
                            isHoldingForSpeed = true
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            com.musicplayer.lytrix.service.PlaybackService.setSpeedBoost(true)
                        }
                        try {
                            tryAwaitRelease()
                        } finally {
                            speedJob.cancel()
                            if (isHoldingForSpeed) {
                                isHoldingForSpeed = false
                                com.musicplayer.lytrix.service.PlaybackService.setSpeedBoost(false)
                            }
                        }
                    } else {
                        tryAwaitRelease()
                    }
                },
                onLongPress = { latestOnLongPress?.invoke() },
                onDoubleTap = if (latestGesturesEnabled && latestOnDoubleTap != null) {
                    { offset -> latestOnDoubleTap?.invoke(offset, size.width.toFloat()) }
                } else null,
                onTap = if (latestOnClick != null) {
                    { latestOnClick?.invoke() }
                } else null
            )
        }
    } else Modifier

    val dragModifier = if (gesturesEnabled && (onSwipeLeft != null || onSwipeRight != null)) {
        Modifier.pointerInput(song.id, gesturesEnabled) {
            var dragAccumulator = 0f
            detectHorizontalDragGestures(
                onDragStart = { dragAccumulator = 0f },
                onHorizontalDrag = { change, dragAmount ->
                    if (latestGesturesEnabled) {
                        change.consume()
                        dragAccumulator += dragAmount
                    }
                },
                onDragEnd = {
                    if (latestGesturesEnabled) {
                        if (dragAccumulator < -swipeThresholdPx) {
                            latestOnSwipeLeft?.invoke()
                        } else if (dragAccumulator > swipeThresholdPx) {
                            latestOnSwipeRight?.invoke()
                        }
                    }
                    dragAccumulator = 0f
                },
                onDragCancel = {
                    dragAccumulator = 0f
                }
            )
        }
    } else Modifier

    Box(
        modifier = modifier
            .graphicsLayer {
                layerTransform?.invoke(this)
                val a = shadowAlpha()
                if (a > 0.01f) {
                    shadowElevation = 16.dp.toPx()
                    shape = SquircleShape
                    clip = false
                    ambientShadowColor = Color.Black.copy(alpha = a)
                    spotShadowColor = Color.Black.copy(alpha = a)
                }
            }
            .then(tapModifier)
            .then(dragModifier),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    shape = shapeProvider()
                    clip = true
                }
                .background(Color(0xFF1E1E24))
        ) {
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(25.dp)
                    .alpha(0.65f)
            )
            AsyncImage(
                model = imageRequest,
                contentDescription = "Cover Art",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.15f))
            )

            if (visualMedia != null) {
                val exoPlayer = remember(visualMedia.url, song.id, visualMedia.clips) {
                    CanvasVideoPlayerManager.getOrCreatePlayer(context, visualMedia.url, song.id, isPlaying, visualMedia.clips)
                }

                var isVideoReady by remember(visualMedia.url, song.id) {
                    mutableStateOf(exoPlayer.playbackState == Player.STATE_READY)
                }

                DisposableEffect(exoPlayer) {
                    val listener = object : Player.Listener {
                        override fun onRenderedFirstFrame() {
                            isVideoReady = true
                        }
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_READY) {
                                isVideoReady = true
                            }
                        }
                        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                            isVideoReady = false
                        }
                    }
                    exoPlayer.addListener(listener)
                    onDispose {
                        exoPlayer.removeListener(listener)
                    }
                }

                LaunchedEffect(isPlaying) {
                    exoPlayer.playWhenReady = isPlaying
                }

                val videoAlpha by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (isVideoReady) 1f else 0f,
                    animationSpec = tween(if (isVideoReady) 0 else 300),
                    label = "videoAlpha"
                )

                AndroidView(
                    factory = { ctx ->
                        (LayoutInflater.from(ctx).inflate(R.layout.apple_music_texture_player_view, null) as PlayerView).apply {
                            player = exoPlayer
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            subtitleView?.visibility = View.GONE
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    update = { playerView ->
                        if (playerView.player != exoPlayer) {
                            playerView.player = exoPlayer
                        }
                        playerView.subtitleView?.visibility = View.GONE
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(videoAlpha)
                )
            }
        }

        // ⏩ YouTube-style 2X Speed Pill Overlay
        AnimatedVisibility(
            visible = isHoldingForSpeed,
            enter = fadeIn(tween(140)) + scaleIn(initialScale = 0.85f),
            exit = fadeOut(tween(180)) + scaleOut(targetScale = 0.85f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.82f))
                    .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.FastForward,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "2X Speed",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

/** Songs frozen when a transition starts, so the animation never changes under our feet. */
private class DeckSession(
    val current: Song,
    val next: Song,
    val nextArt: Any?,
    val isManual: Boolean
)

/**
 * Dual-deck track transition.
 *
 * Two animatables drive everything:
 *  - split    0 -> 1 : current disc morphs to flower + shrinks to bottom-left, next disc appears top-right
 *  - takeover 0 -> 1 : next disc glides to center, grows to 1.0 and melts back into the squircle
 *
 * The audio track only switches AFTER takeover has landed (manual) or exactly when the song ends (natural).
 * When the real song changes, the landed disc stays on top until the real one underneath is ready,
 * so there is no snap, jump-cut or flicker.
 *
 * manualSkipTrigger: parent increments it when the Next button is pressed. This composable plays the
 * animation and then calls onSkipNext() itself.
 */
@Composable
fun NextSongTransitionArt(
    currentSong: Song,
    nextSong: Song?,
    currentPositionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    isTransitionEnabled: Boolean,
    artData: Any?,
    nextArtData: Any?,
    manualSkipTrigger: Int,
    onLongPress: (() -> Unit)? = null,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit = {},
    onSeek: (Long) -> Unit = {},
    onPlayPause: () -> Unit = {},
    gesturesEnabled: Boolean = true,
    isGuest: Boolean = false,
    visualMedia: VisualMediaResult? = null,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val cornerRadiusPx = remember(density) { with(density) { 20.dp.toPx() } }

    val scope = rememberCoroutineScope()

    LaunchedEffect(currentSong.id) {
        CanvasVideoPlayerManager.releaseIfDifferentSong(currentSong.id)
    }
    val currentPosState = rememberUpdatedState(currentPositionMs)
    val durationState = rememberUpdatedState(durationMs)
    val isPlayingState = rememberUpdatedState(isPlaying)
    val isGuestState = rememberUpdatedState(isGuest)
    val gesturesEnabledState = rememberUpdatedState(gesturesEnabled)
    val onSeekState = rememberUpdatedState(onSeek)
    val onPlayPauseState = rememberUpdatedState(onPlayPause)
    val onSkipPreviousState = rememberUpdatedState(onSkipPrevious)

    var showSeekOverlay by remember { mutableStateOf(false) }
    var seekText by remember { mutableStateOf("") }
    var seekIsForward by remember { mutableStateOf(false) }
    var seekTrigger by remember { mutableIntStateOf(0) }

    var showCenterPop by remember { mutableStateOf(false) }
    var centerPopIsPlaying by remember { mutableStateOf(false) }
    var centerPopTrigger by remember { mutableIntStateOf(0) }

    var lastTapTime by remember { mutableLongStateOf(0L) }
    var lastTapDirection by remember { mutableStateOf("") }
    var seekMultiplier by remember { mutableIntStateOf(1) }
    var seekBasePosition by remember { mutableLongStateOf(0L) }

    LaunchedEffect(seekTrigger) {
        if (seekTrigger > 0) {
            showSeekOverlay = true
            delay(850L)
            showSeekOverlay = false
        }
    }

    LaunchedEffect(centerPopTrigger) {
        if (centerPopTrigger > 0) {
            showCenterPop = true
            delay(600L)
            showCenterPop = false
        }
    }
    val split = remember { Animatable(0f) }
    val take = remember { Animatable(0f) }

    var session by remember { mutableStateOf<DeckSession?>(null) }
    var manualRunning by remember { mutableStateOf(false) }
    var transitionJob by remember { mutableStateOf<Job?>(null) }
    val lastHandledTrigger = remember { mutableIntStateOf(manualSkipTrigger) }

    val latestPos by rememberUpdatedState(currentPositionMs)
    val latestDuration by rememberUpdatedState(durationMs)
    val latestPlaying by rememberUpdatedState(isPlaying)
    val latestSong by rememberUpdatedState(currentSong)
    val latestArt by rememberUpdatedState(artData)
    val latestNextSong by rememberUpdatedState(nextSong)
    val latestNextArt by rememberUpdatedState(nextArtData)
    val latestOnSkip by rememberUpdatedState(onSkipNext)

    val sess = session
    val songSwitched = sess != null && currentSong.id != sess.current.id
    val nearLanded by remember { derivedStateOf { take.value >= 0.9f } }
    val landed by remember { derivedStateOf { take.value >= 0.999f } }
    val hold = sess != null && songSwitched && nearLanded
    val abort = sess != null && songSwitched && !nearLanded
    val activeSess = if (abort) null else sess

    val remainingMs = (durationMs - currentPositionMs).coerceAtLeast(0L)
    val naturalWindow = isTransitionEnabled && nextSong != null && durationMs > 20_000L &&
            remainingMs in 1L..NATURAL_WINDOW_MS

    val endSession: suspend () -> Unit = {
        transitionJob?.cancel()
        transitionJob = null
        split.snapTo(0f)
        take.snapTo(0f)
        session = null
    }

    // Natural transition: split over 2.5s, wait, then takeover locked to the playback clock
    val runNatural: suspend () -> Unit = {
        if (session == null) {
            val n = latestNextSong
            if (n != null) {
                split.snapTo(0f)
                take.snapTo(0f)
                session = DeckSession(latestSong, n, latestNextArt, false)
            }
        }
        if (session != null) {
            split.animateTo(1f, tween(2500, easing = FastOutSlowInEasing))
            while ((latestDuration - latestPos).toFloat() > TAKEOVER_MS) delay(100)

            var clock = latestPos.toFloat()
            var lastPos = latestPos
            var lastFrame = withFrameNanos { it }
            while (take.value < 1f) {
                val now = withFrameNanos { it }
                val dt = (now - lastFrame) / 1_000_000f
                lastFrame = now
                if (latestPlaying) clock += dt
                val p = latestPos
                if (p != lastPos) {
                    lastPos = p
                    val drift = p.toFloat() - clock
                    clock = if (abs(drift) > 400f) p.toFloat() else clock + drift * 0.5f
                }
                val remaining = latestDuration.toFloat() - clock
                val t = ((TAKEOVER_MS - remaining) / TAKEOVER_MS).coerceIn(0f, 1f)
                val eased = FastOutSlowInEasing.transform(t)
                if (eased > take.value) take.snapTo(eased) // never goes backwards
            }
        }
    }

    // Manual transition (Next button or tap on the next disc)
    val startManual: () -> Unit = start@{
        val n = latestNextSong
        if (n == null) {
            latestOnSkip()
            return@start
        }
        if (manualRunning || songSwitched || landed) return@start
        manualRunning = true
        transitionJob?.cancel()
        transitionJob = scope.launch {
            try {
                if (session == null) {
                    split.snapTo(0f)
                    take.snapTo(0f)
                    session = DeckSession(latestSong, n, latestNextArt, true)
                }
                split.animateTo(1f, tween(350, easing = FastOutSlowInEasing))
                take.animateTo(1f, tween(750, easing = FastOutSlowInEasing))
                latestOnSkip() // audio switches only now, after the disc has fully landed
            } finally {
                manualRunning = false
            }
        }
    }

    LaunchedEffect(naturalWindow) {
        if (manualRunning) return@LaunchedEffect
        val existing = session
        if (naturalWindow) {
            if (existing == null || !existing.isManual) {
                transitionJob?.cancel()
                transitionJob = scope.launch { runNatural() }
            }
        } else if (existing != null && !existing.isManual) {
            transitionJob?.cancel()
            transitionJob = scope.launch {
                delay(500) // the song may just be changing right now - give it a moment
                val switched = latestSong.id != existing.current.id
                if (session === existing && !switched && take.value < 0.999f) {
                    // user seeked back / disabled the feature: play the animation in reverse
                    split.animateTo(0f, tween(300))
                    take.animateTo(0f, tween(300))
                    session = null
                }
            }
        }
    }

    LaunchedEffect(manualSkipTrigger) {
        if (manualSkipTrigger != lastHandledTrigger.intValue) {
            lastHandledTrigger.intValue = manualSkipTrigger
            startManual()
        }
    }

    // Release: wait until the real (new) song art is ready under the landed disc, then drop the overlay
    LaunchedEffect(sess, songSwitched, nearLanded, landed) {
        if (sess == null) return@LaunchedEffect
        if (songSwitched) {
            if (nearLanded) {
                var waited = 0
                while (latestArt == null && sess.nextArt != null && waited < 700) {
                    delay(50)
                    waited += 50
                }
                delay(150)
            }
            endSession()
        } else if (landed) {
            delay(2500) // audio never switched (e.g. repeat-one) - give up and show current song again
            endSession()
        }
    }

    // Motion clocks exist only while a transition is on screen (no idle frame wakeups)
    val infinite = if (activeSess != null) rememberInfiniteTransition(label = "deck_motion") else null
    val rotationState: State<Float>? = if (infinite != null) {
        infinite.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(18000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "deck_rotation"
        )
    } else null
    val bobState: State<Float>? = if (infinite != null) {
        infinite.animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "deck_bob"
        )
    } else null

    val curS: () -> Float = { if (activeSess != null && !hold) split.value else 0f }
    val curT: () -> Float = { if (activeSess != null && !hold) take.value else 0f }
    val nextS: () -> Float = { if (hold) 1f else split.value }
    val nextT: () -> Float = { if (hold) 1f else take.value }
    val bob: () -> Float = { bobState?.value ?: 0f }
    val rotation: () -> Float = { (rotationState?.value ?: 0f) * DEG_TO_RAD }

    var boxWidthPx by remember { mutableFloatStateOf(0f) }

    val handleDoubleTap: (Offset, Float) -> Unit = { offset, discWidth ->
        if (!isGuestState.value && gesturesEnabledState.value) {
        val w = if (discWidth > 0f) discWidth else if (boxWidthPx > 0f) boxWidthPx else 1000f
        val leftBoundary = w * 0.35f
        val rightBoundary = w * 0.65f

        when {
            // Left 35%: Seek Backward (-5s, -10s...)
            offset.x < leftBoundary -> {
                val now = System.currentTimeMillis()
                if (now - lastTapTime < 800L && lastTapDirection == "backward") {
                    seekMultiplier++
                } else {
                    seekMultiplier = 1
                    seekBasePosition = currentPosState.value
                }
                lastTapTime = now
                lastTapDirection = "backward"

                val skipSec = 5 * seekMultiplier
                val targetPos = (seekBasePosition - (skipSec * 1000L)).coerceAtLeast(0L)
                onSeekState.value(targetPos)

                seekText = "-${skipSec}s"
                seekIsForward = false
                seekTrigger++
            }

            // Right 35%: Seek Forward (+5s, +10s...)
            offset.x > rightBoundary -> {
                val now = System.currentTimeMillis()
                if (now - lastTapTime < 800L && lastTapDirection == "forward") {
                    seekMultiplier++
                } else {
                    seekMultiplier = 1
                    seekBasePosition = currentPosState.value
                }
                lastTapTime = now
                lastTapDirection = "forward"

                val skipSec = 5 * seekMultiplier
                val maxDur = if (durationState.value > 0L) durationState.value else Long.MAX_VALUE
                val targetPos = (seekBasePosition + (skipSec * 1000L)).coerceAtMost(maxDur)
                onSeekState.value(targetPos)

                seekText = "+${skipSec}s"
                seekIsForward = true
                seekTrigger++
            }

            // Center 30%: Toggle Play / Pause
            else -> {
                centerPopIsPlaying = !isPlayingState.value
                centerPopTrigger++
                onPlayPauseState.value()
            }
        }
        }
    }

    Box(
        modifier = modifier.graphicsLayer { boxWidthPx = size.width },
        contentAlignment = Alignment.Center
    ) {
        // Disc 1: the current song. Identity transform + squircle when no transition is running.
        ArtworkDisc(
            song = currentSong,
            artData = artData,
            modifier = Modifier.fillMaxSize(),
            visualMedia = visualMedia,
            isPlaying = isPlaying,
            layerTransform = {
                val s = curS()
                val t = curT()
                val k = 1f - 0.42f * s - 0.18f * t
                val spread = s * (1f + 0.4f * t)
                scaleX = k
                scaleY = k
                alpha = 1f - t
                translationX = -size.width * DECK_OFFSET * spread
                translationY = size.height * DECK_OFFSET * spread + bob() * 3.dp.toPx() * s * (1f - t)
            },
            shapeProvider = {
                MorphingScallopShape(
                    morphProgress = curS(),
                    cornerRadiusPx = cornerRadiusPx,
                    rotationRad = rotation()
                )
            },
            shadowAlpha = { 1f - curS() },
            onLongPress = onLongPress,
            onDoubleTap = handleDoubleTap,
            onSwipeLeft = {
                if (isTransitionEnabled && latestNextSong != null) {
                    startManual()
                } else {
                    latestOnSkip()
                }
            },
            onSwipeRight = {
                onSkipPreviousState.value()
            },
            gesturesEnabled = gesturesEnabledState.value
        )

        // Disc 2: the next song (drawn on top)
        if (activeSess != null) {
            ArtworkDisc(
                song = activeSess.next,
                artData = activeSess.nextArt,
                modifier = Modifier.fillMaxSize(),
                layerTransform = {
                    val s = nextS()
                    val t = nextT()
                    val k = DISC_SCALE * s + 0.42f * t
                    val rest = 1f - t
                    val floatPx = bob() * 3.dp.toPx() * s * rest
                    scaleX = k
                    scaleY = k
                    alpha = s
                    translationX = size.width * DECK_OFFSET * rest
                    translationY = -size.height * DECK_OFFSET * rest - floatPx
                },
                shapeProvider = {
                    MorphingScallopShape(
                        morphProgress = 1f - nextT(),
                        cornerRadiusPx = cornerRadiusPx,
                        rotationRad = -rotation()
                    )
                },
                shadowAlpha = { if (hold) 0f else nextT() },
                onClick = startManual
            )
        }

        // ⏪ Left Seek Overlay Badge (Backward)
        AnimatedVisibility(
            visible = showSeekOverlay && !seekIsForward,
            enter = fadeIn(tween(140)) + scaleIn(initialScale = 0.8f),
            exit = fadeOut(tween(220)) + scaleOut(targetScale = 0.8f),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 24.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black.copy(alpha = 0.78f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.FastRewind,
                        contentDescription = "Seek backward",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = seekText,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // ⏩ Right Seek Overlay Badge (Forward)
        AnimatedVisibility(
            visible = showSeekOverlay && seekIsForward,
            enter = fadeIn(tween(140)) + scaleIn(initialScale = 0.8f),
            exit = fadeOut(tween(220)) + scaleOut(targetScale = 0.8f),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 24.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black.copy(alpha = 0.78f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = seekText,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Rounded.FastForward,
                        contentDescription = "Seek forward",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // ⏯ Center Play / Pause Pop Feedback
        AnimatedVisibility(
            visible = showCenterPop,
            enter = fadeIn(tween(100)) + scaleIn(initialScale = 0.6f),
            exit = fadeOut(tween(200)) + scaleOut(targetScale = 1.15f),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (centerPopIsPlaying) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(38.dp)
                )
            }
        }
    }
}