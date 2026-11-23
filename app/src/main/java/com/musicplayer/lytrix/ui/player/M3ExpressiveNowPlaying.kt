@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.musicplayer.lytrix.ui.player

import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import kotlin.math.abs
import androidx.media3.common.PlaybackParameters

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.rounded.Aod
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import com.musicplayer.lytrix.ui.component.LoopingCoverVideoPlayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicColorScheme
import com.musicplayer.lytrix.aod.AodLaunchTrigger
import com.musicplayer.lytrix.model.Song
import com.musicplayer.lytrix.model.Video
import com.musicplayer.lytrix.ui.DataStoreManager
import com.musicplayer.lytrix.ui.LytrixExpressiveSpinner
import com.musicplayer.lytrix.ui.MeldNowPlayingBelowTheFold
import com.musicplayer.lytrix.ui.NowPlayingLiveLyricStrip
import com.musicplayer.lytrix.ui.PlayerViewModel
import com.musicplayer.lytrix.ui.component.NextSongTransitionArt
import com.musicplayer.lytrix.ui.component.PlayerCoverWithGestures
import com.musicplayer.lytrix.ui.rememberEmbeddedArt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Material 3 Expressive / V9 Morphing Player with Wavy Seekbar.
 * Combines SimpMusic's MaterialKolor Expressive tonal palette and Lytrix's V9
 * spring-physics expanding transport controls with a travelling sine-wave progress bar.
 */
@Composable
fun M3ExpressiveNowPlayingFullScreen(
    viewModel: PlayerViewModel,
    song: Song,
    isPlaying: Boolean,
    isBuffering: Boolean,
    currentPosition: Long,
    duration: Long,
    repeatMode: Int,
    shuffleEnabled: Boolean,
    accentColor: Color,
    currentLyrics: String?,
    isFavorite: Boolean,
    onClose: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenMenu: () -> Unit,
    onOpenSleepTimer: () -> Unit = {},
    onNavigateToArtist: (String) -> Unit,
    onPlayVideo: (Video) -> Unit = {}
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val coroutineScope = rememberCoroutineScope()
    val dataStore = remember { DataStoreManager(context) }
    val gesturesEnabled by dataStore.playerGesturesEnabledFlow.collectAsState(initial = true)
    val isTransitionEnabled by dataStore.nextSongTransitionAnimationFlow.collectAsState(initial = true)
    val isGuest = rememberIsListenTogetherGuest(viewModel)
    val isAppOnlineSong = viewModel.isSongOnline(song)
    val isYtSong = remember(song.filePath, song.albumArtUriString, song.uriString) { isYouTubeSong(song) }
    val displayIsFavorite = rememberResolvedIsFavorite(viewModel, song, isFavorite)
    val artData = rememberEmbeddedArt(context, song)
    val currentQueue by viewModel.currentQueueList.collectAsState()
    val nextSong = remember(currentQueue, song.id, repeatMode) {
        val currentIndex = currentQueue.indexOfFirst { it.id == song.id }
        if (currentIndex in 0 until currentQueue.lastIndex) {
            currentQueue[currentIndex + 1]
        } else if (currentIndex == currentQueue.lastIndex && repeatMode != Player.REPEAT_MODE_OFF && currentQueue.size > 1) {
            currentQueue.firstOrNull()
        } else {
            null
        }
    }
    val nextArtData = rememberEmbeddedArt(context, nextSong)
    val palette = rememberArtworkPaletteState(song, accentColor, artData)
    val visualMedia = rememberVisualMediaForSong(song)
    val qualityLabel = rememberPlayerAudioQualityLabel(viewModel, song)
    val (audioOutputs, selectOutput) = rememberAudioOutputEntries()
    val activeOutputName = remember(audioOutputs) {
        audioOutputs.firstOrNull { it.isActive }?.name ?: "Speaker"
    }

    val expressiveScheme = rememberDynamicColorScheme(
        seedColor = palette.rawVibrant,
        isDark = true,
        isAmoled = false,
        style = PaletteStyle.Expressive
    )

    val isVideoModeActive by viewModel.isVideoModeActive.collectAsState()
    val isVideoHandoffPending by viewModel.isVideoHandoffPending.collectAsState()
    val sleepTimerState by viewModel.sleepTimerState.collectAsState()
    val sleepTimerDisplay = if (sleepTimerState.isActive) {
        com.musicplayer.lytrix.playback.formatSleepTimerRemaining(sleepTimerState.remainingMs)
    } else null
    val pointA by viewModel.pointA.collectAsState()
    val pointB by viewModel.pointB.collectAsState()
    var manualSkipTrigger by remember { mutableIntStateOf(0) }
    val exoPlayer = if (isVideoModeActive) viewModel.getVideoPlayer() else null
    val videoAspect = rememberExoVideoAspectRatio(exoPlayer)
    val performSkipNext: () -> Unit = {
        onSkipNext()
        viewModel.forceSyncToFirebase()
    }
    val handleSkipNext: () -> Unit = {
        if (isTransitionEnabled && nextSong != null && !isVideoModeActive) {
            manualSkipTrigger++
        } else {
            performSkipNext()
        }
    }
    var showLyricsOverlay by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showEqualizer by remember { mutableStateOf(false) }
    var showOutputSheet by remember { mutableStateOf(false) }
    var scrubbingFraction by remember { mutableStateOf<Float?>(null) }
    val holdScrubState = rememberHoldToScrubState(
        currentPosition = currentPosition,
        duration = duration,
        onSeek = onSeek
    )

    val scrollState = rememberScrollState()
    var hasRequestedBelowFold by remember(song.id, song.originalId) { mutableStateOf(false) }
    LaunchedEffect(scrollState.value, song.id, song.originalId) {
        if (scrollState.value > 40 && !hasRequestedBelowFold && isYtSong) {
            hasRequestedBelowFold = true
            viewModel.loadNowPlayingRecommendations(song)
        }
    }

    var pullDownAcc by remember { mutableFloatStateOf(0f) }
    val nestedScroll = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (scrollState.value == 0 && available.y > 0 && source == NestedScrollSource.Drag) {
                    pullDownAcc += available.y
                    if (pullDownAcc > 280f) {
                        onClose()
                        pullDownAcc = 0f
                        return available
                    }
                } else if (scrollState.value > 0 || available.y < 0) {
                    pullDownAcc = 0f
                }
                return Offset.Zero
            }
        }
    }

    val safeDuration = duration.coerceAtLeast(1L)
    val shownPosMs = scrubbingFraction?.let { (it * safeDuration).toLong() }
        ?: holdScrubState.draggingPosition
        ?: currentPosition.coerceIn(0L, safeDuration)
    val shownFraction = scrubbingFraction
        ?: (shownPosMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)

    // Expressive artwork morph: soft pillowed corners & gentle scale when paused vs playing
    val artCorner by animateDpAsState(
        targetValue = if (isPlaying) 28.dp else 18.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "m3ExpArtCorner"
    )
    val artScale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.94f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "m3ExpArtScale"
    )

    val artistAnnotated = remember(song.artist, expressiveScheme.onSurfaceVariant) {
        buildClickableArtistAnnotated(song.artist, expressiveScheme.onSurfaceVariant, fontSize = 15.sp)
    }
    val hasDisplayableLyrics = !currentLyrics.isNullOrBlank() && currentLyrics != "LOADING_LYRICS_STATE"

    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val activityContext = context as? Activity
    DisposableEffect(isLandscape, isVideoModeActive) {
        activityContext?.window?.let { window ->
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            if (isLandscape && isVideoModeActive) {
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            activityContext?.window?.let { win ->
                WindowCompat.getInsetsController(win, win.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (activityContext?.requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
                activityContext?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLandscape && isVideoModeActive) {
            // 🔥 Landscape Fullscreen Video Player (Edge-to-edge with hidden system bars & gestures)
            BackHandler(enabled = isLandscape && isVideoModeActive) {
                activityContext?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }

            val isVideoPlayerReady by viewModel.isVideoPlayerReady.collectAsState()
            val videoExoPlayer = viewModel.getVideoPlayer()
            val currentResizeMode by viewModel.videoResizeMode.collectAsState()
            var showControls by remember { mutableStateOf(true) }

            LaunchedEffect(showControls, isPlaying) {
                if (showControls && isPlaying) {
                    delay(3500)
                    showControls = false
                }
            }

            PlayerCoverWithGestures(
                gesturesEnabled = gesturesEnabled,
                isGuest = isGuest,
                isPlaying = isPlaying,
                currentPosition = currentPosition,
                duration = duration,
                onSeek = { seekPos ->
                    onSeek(seekPos)
                    val mapped = viewModel.mapAudioToVideoPosition(song.id.toString(), seekPos)
                    videoExoPlayer?.seekTo(mapped)
                    viewModel.forceSyncToFirebase()
                },
                onPlayPause = {
                    onPlayPause()
                    viewModel.forceSyncToFirebase()
                },
                onTap = { showControls = !showControls },
                swipeEnabled = false,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                // High-res Album Art Underneath (shown until video loads)
                AsyncImage(
                    model = artData ?: (song.albumArtUriString ?: song.uriString),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )

                if (videoExoPlayer != null) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                useController = false
                                controllerAutoShow = false
                                isClickable = false
                                isFocusable = false
                                setOnTouchListener { _, _ -> false }
                                resizeMode = currentResizeMode
                                subtitleView?.visibility = android.view.View.GONE
                                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                hideController()
                            }
                        },
                        update = { playerView ->
                            if (playerView.player != videoExoPlayer) {
                                playerView.player = videoExoPlayer
                            }
                            playerView.resizeMode = currentResizeMode
                            playerView.subtitleView?.visibility = android.view.View.GONE
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Top Navigation & Control Bar in Landscape Fullscreen
                AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { -50 }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { -50 }),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .statusBarsPadding()
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        // Left: Back / Exit Fullscreen Button
                        IconButton(
                            onClick = {
                                activityContext?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            },
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .size(40.dp)
                                .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Exit Fullscreen",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Center: Song | Video Capsule
                        Row(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .height(38.dp)
                                .clip(RoundedCornerShape(19.dp))
                                .background(Color.Black.copy(alpha = 0.65f))
                                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(19.dp))
                                .padding(3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        activityContext?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                        viewModel.switchToAudioMode(song)
                                    }
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Song", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(expressiveScheme.primary)
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Video", color = expressiveScheme.onPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Right: Scale (Fit/Zoom) & PiP Controls
                        Row(
                            modifier = Modifier.align(Alignment.CenterEnd),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Scale / Zoom Toggle
                            IconButton(
                                onClick = { viewModel.toggleVideoResizeMode() },
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        if (currentResizeMode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM)
                                            Color(0xFF2196F3).copy(alpha = 0.85f)
                                        else
                                            Color.Black.copy(alpha = 0.65f),
                                        CircleShape
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AspectRatio,
                                    contentDescription = "Aspect Ratio",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // PiP Button
                            IconButton(
                                onClick = {
                                    com.musicplayer.lytrix.ui.component.safeEnterPip(activityContext, viewModel)
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PictureInPictureAlt,
                                    contentDescription = "PiP",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // Bottom Controls Overlay
                AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { 50 }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { 50 }),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                        .navigationBarsPadding()
                        .padding(horizontal = 32.dp, vertical = 16.dp)
                ) {
                    Column {
                        val positionToShow = shownPosMs
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(formatTrackTime(positionToShow), fontSize = 12.sp, color = Color.White)
                            Slider(
                                value = shownFraction,
                                onValueChange = { frac ->
                                    if (!isGuest) scrubbingFraction = frac
                                },
                                onValueChangeFinished = {
                                    if (!isGuest) {
                                        scrubbingFraction?.let { frac ->
                                            val target = (frac * safeDuration).toLong()
                                            onSeek(target)
                                            viewModel.forceSyncToFirebase()
                                        }
                                    }
                                    scrubbingFraction = null
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = expressiveScheme.primary,
                                    activeTrackColor = expressiveScheme.primary,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 16.dp)
                            )
                            Text(formatTrackTime(duration), fontSize = 12.sp, color = Color.White)
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .holdToScrubTransport(
                                        enabled = !isGuest,
                                        scope = coroutineScope,
                                        onTap = onSkipPrevious,
                                        onHoldChanged = { holdScrubState.setRewind(it) }
                                    )
                                    .padding(9.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.SkipPrevious, contentDescription = "Skip Previous", tint = Color.White, modifier = Modifier.size(34.dp))
                            }
                            Spacer(modifier = Modifier.width(32.dp))
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(expressiveScheme.primary)
                                    .clickable {
                                        if (!isGuest) {
                                            onPlayPause()
                                            viewModel.forceSyncToFirebase()
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isBuffering) {
                                    LytrixExpressiveSpinner(color = expressiveScheme.onPrimary, strokeWidth = 3.dp, size = 28.dp)
                                } else {
                                    Icon(
                                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        contentDescription = "Play/Pause",
                                        tint = expressiveScheme.onPrimary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(32.dp))
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .holdToScrubTransport(
                                        enabled = !isGuest,
                                        scope = coroutineScope,
                                        onTap = handleSkipNext,
                                        onHoldChanged = { holdScrubState.setForward(it) }
                                    )
                                    .padding(9.dp),
                            contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.SkipNext, contentDescription = "Skip Next", tint = Color.White, modifier = Modifier.size(34.dp))
                            }
                        }
                    }
                }
            }
        } else if (isLandscape) {
            // 🔥 Landscape Audio Player (Continuous Scroll with Below-the-fold)
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .nestedScroll(nestedScroll)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                expressiveScheme.surfaceContainerHigh,
                                expressiveScheme.surfaceContainerLowest,
                                Color.Black
                            )
                        )
                    )
            ) {
                val totalViewportHeight = maxHeight
                val isCanvasBackground = visualMedia != null &&
                    visualMedia!!.type != com.musicplayer.lytrix.spotify.canvas.VisualMediaType.APPLE_MUSIC_MOTION &&
                    !isVideoModeActive

                val dockComposable: @Composable (Modifier, androidx.compose.ui.unit.Dp) -> Unit = { dockModifier, actionSize ->
                    Row(
                        modifier = dockModifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(expressiveScheme.surfaceContainer)
                            .border(1.dp, expressiveScheme.outlineVariant.copy(alpha = 0.28f), RoundedCornerShape(22.dp))
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ExpressiveDockAction(
                            icon = Icons.Rounded.Shuffle,
                            active = shuffleEnabled,
                            activeColor = expressiveScheme.primary,
                            inactiveColor = expressiveScheme.onSurfaceVariant,
                            size = actionSize,
                            onClick = { if (!isGuest) onToggleShuffle() }
                        )
                        ExpressiveDockAction(
                            icon = Icons.AutoMirrored.Rounded.QueueMusic,
                            active = showQueue,
                            activeColor = expressiveScheme.primary,
                            inactiveColor = expressiveScheme.onSurfaceVariant,
                            size = actionSize,
                            onClick = { showQueue = true }
                        )
                        ExpressiveDockAction(
                            icon = Icons.Rounded.MusicNote,
                            active = !currentLyrics.isNullOrBlank(),
                            activeColor = expressiveScheme.primary,
                            inactiveColor = expressiveScheme.onSurfaceVariant.copy(alpha = 0.45f),
                            size = actionSize,
                            onClick = { showLyricsOverlay = true }
                        )
                        ExpressiveDockAction(
                            icon = com.musicplayer.lytrix.ui.icons.LytrixIcons.Outlined.Bedtime,
                            active = sleepTimerState.isActive,
                            activeColor = expressiveScheme.primary,
                            inactiveColor = expressiveScheme.onSurfaceVariant,
                            size = actionSize,
                            badgeText = sleepTimerDisplay,
                            onClick = onOpenSleepTimer
                        )
                        ExpressiveDockAction(
                            icon = Icons.Rounded.Equalizer,
                            active = showEqualizer,
                            activeColor = expressiveScheme.primary,
                            inactiveColor = expressiveScheme.onSurfaceVariant,
                            size = actionSize,
                            onClick = { showEqualizer = true }
                        )
                        ExpressiveDockAction(
                            icon = Icons.Rounded.Headphones,
                            active = false,
                            activeColor = expressiveScheme.primary,
                            inactiveColor = expressiveScheme.onSurfaceVariant,
                            size = actionSize,
                            onClick = { showOutputSheet = true }
                        )
                        ExpressiveDockAction(
                            icon = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                            active = repeatMode != Player.REPEAT_MODE_OFF,
                            activeColor = expressiveScheme.primary,
                            inactiveColor = expressiveScheme.onSurfaceVariant,
                            size = actionSize,
                            onClick = { if (!isGuest) onToggleRepeat() }
                        )
                    }
                }

                val timelineComposable: @Composable () -> Unit = {
                    com.musicplayer.lytrix.ui.component.ABRepeatSeekBarBox(
                        pointA = pointA,
                        pointB = pointB,
                        durationMs = safeDuration,
                        markerColor = expressiveScheme.primary,
                    ) {
                        WavySeekBarExpressive(
                            progressFraction = shownFraction,
                            isPlaying = isPlaying && !isBuffering,
                            activeColor = expressiveScheme.primary,
                            trackColor = expressiveScheme.surfaceContainerHighest,
                            thumbColor = expressiveScheme.primary,
                            onProgressChange = { frac ->
                                if (!isGuest) scrubbingFraction = frac
                            },
                            onProgressChangeFinished = {
                                if (!isGuest) {
                                    scrubbingFraction?.let { frac -> onSeek((frac * safeDuration).toLong()) }
                                }
                                scrubbingFraction = null
                            }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatTrackTime(shownPosMs),
                            color = expressiveScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = qualityLabel,
                            color = expressiveScheme.primary.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = formatTrackTime(duration),
                            color = expressiveScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    com.musicplayer.lytrix.ui.component.ABRepeatControlPill(
                        pointA = pointA,
                        pointB = pointB,
                        accentColor = expressiveScheme.primary,
                        onSetPointA = { viewModel.setPointA() },
                        onSetPointB = { viewModel.setPointB() },
                        onClearAB = { viewModel.clearAB() },
                    )
                }

                val aodButton: @Composable (androidx.compose.ui.unit.Dp) -> Unit = { btnSize ->
                    Box(
                        modifier = Modifier
                            .size(btnSize)
                            .clip(CircleShape)
                            .background(expressiveScheme.surfaceContainerHighest)
                            .combinedClickable(
                                onClick = { AodLaunchTrigger.launch(initialLocked = false) },
                                onLongClick = { AodLaunchTrigger.launch(initialLocked = true) }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Aod,
                            contentDescription = "Always-On Display (Hold for Pocket Lock)",
                            tint = expressiveScheme.onSurface,
                            modifier = Modifier.size(btnSize * 0.48f)
                        )
                    }
                }

                val menuButton: @Composable (androidx.compose.ui.unit.Dp) -> Unit = { btnSize ->
                    Box(
                        modifier = Modifier
                            .size(btnSize)
                            .clip(CircleShape)
                            .background(expressiveScheme.surfaceContainerHighest)
                            .clickable(onClick = onOpenMenu),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "More Options",
                            tint = expressiveScheme.onSurface,
                            modifier = Modifier.size(btnSize * 0.50f)
                        )
                    }
                }

                val favoriteButton: @Composable (androidx.compose.ui.unit.Dp) -> Unit = { btnSize ->
                    Box(
                        modifier = Modifier
                            .size(btnSize)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (displayIsFavorite) expressiveScheme.tertiaryContainer
                                else expressiveScheme.surfaceContainerHigh
                            )
                            .clickable(onClick = onToggleFavorite),
                        contentAlignment = Alignment.Center
                    ) {
                        com.musicplayer.lytrix.ui.icons.MorphingMetroIcon(
                            selected = displayIsFavorite,
                            outlinedIcon = com.musicplayer.lytrix.ui.icons.LytrixIcons.Outlined.Star,
                            filledIcon = com.musicplayer.lytrix.ui.icons.LytrixIcons.Filled.Star,
                            contentDescription = "Favorite",
                            tint = if (displayIsFavorite) expressiveScheme.onTertiaryContainer else expressiveScheme.onSurfaceVariant,
                            modifier = Modifier.size(btnSize * 0.54f)
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                ) {
                    // Viewport 1: Landscape Audio Player (100% Edge-to-Edge Fullscreen Canvas when active)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(totalViewportHeight)
                            .clip(androidx.compose.ui.graphics.RectangleShape)
                    ) {
                        if (isCanvasBackground) {
                            LoopingCoverVideoPlayer(
                                videoUrl = visualMedia!!.url,
                                mediaType = visualMedia!!.type,
                                song = song,
                                artData = artData,
                                isPlaying = isPlaying,
                                currentPositionMs = currentPosition,
                                durationMs = duration,
                                onSeek = onSeek,
                                onPlayPause = onPlayPause,
                                onSkipNext = performSkipNext,
                                onSkipPrevious = onSkipPrevious,
                                gesturesEnabled = gesturesEnabled,
                                isGuest = isGuest,
                                clips = visualMedia!!.clips,
                                clipShape = androidx.compose.ui.graphics.RectangleShape,
                                modifier = Modifier.fillMaxSize()
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.horizontalGradient(
                                            0.00f to Color.Black.copy(alpha = 0.08f),
                                            0.38f to Color.Black.copy(alpha = 0.14f),
                                            0.62f to Color.Black.copy(alpha = 0.44f),
                                            1.00f to Color.Black.copy(alpha = 0.64f)
                                        )
                                    )
                            )

                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .background(
                                        Brush.verticalGradient(
                                            0.0f to Color.Black.copy(alpha = 0.48f),
                                            0.6f to Color.Black.copy(alpha = 0.16f),
                                            1.0f to Color.Transparent
                                        )
                                    )
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .systemBarsPadding()
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                        if (isCanvasBackground) {
                            // Unobstructed left fullscreen canvas stage
                            Spacer(
                                modifier = Modifier
                                    .weight(0.44f)
                                    .fillMaxHeight()
                            )
                        } else {
                        // Left Column: Square Artwork + Tonal Dock (Static Cover / Apple Music 1:1)
                        Column(
                            modifier = Modifier
                                .weight(0.42f)
                                .fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .aspectRatio(1f)
                                    .padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                NextSongTransitionArt(
                                    currentSong = song,
                                    nextSong = nextSong,
                                    currentPositionMs = currentPosition,
                                    durationMs = duration,
                                    isPlaying = isPlaying,
                                    isTransitionEnabled = isTransitionEnabled,
                                    artData = artData,
                                    nextArtData = nextArtData,
                                    manualSkipTrigger = manualSkipTrigger,
                                    onLongPress = null,
                                    onSkipNext = performSkipNext,
                                    onSkipPrevious = {
                                        onSkipPrevious()
                                        viewModel.forceSyncToFirebase()
                                    },
                                    onSeek = { seekPos ->
                                        onSeek(seekPos)
                                        viewModel.forceSyncToFirebase()
                                    },
                                    onPlayPause = {
                                        onPlayPause()
                                        viewModel.forceSyncToFirebase()
                                    },
                                    gesturesEnabled = gesturesEnabled,
                                    isGuest = isGuest,
                                    visualMedia = visualMedia.takeIf { it?.type == com.musicplayer.lytrix.spotify.canvas.VisualMediaType.APPLE_MUSIC_MOTION },
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            scaleX = artScale
                                            scaleY = artScale
                                            clip = false
                                        }
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            dockComposable(Modifier, 36.dp)
                        }
                        }

                        Spacer(modifier = Modifier.width(if (isCanvasBackground) 16.dp else 26.dp))

                        // Right Column: Top Header + Title/Artist + Live Lyrics + Wavy Seekbar + V9 Controls
                        Column(
                            modifier = Modifier
                                .weight(0.58f)
                                .fillMaxHeight()
                                .padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(expressiveScheme.surfaceContainerHighest)
                                            .clickable(onClick = onClose),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.KeyboardArrowDown,
                                            contentDescription = "Collapse",
                                            tint = expressiveScheme.onSurface,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = song.title,
                                            color = expressiveScheme.onSurface,
                                            fontSize = 19.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            modifier = Modifier.basicMarquee()
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        @Suppress("DEPRECATION")
                                        ClickableText(
                                            text = artistAnnotated,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            onClick = { offset ->
                                                if (isAppOnlineSong) {
                                                    val clicked = artistAnnotated
                                                        .getStringAnnotations("ARTIST", offset, offset)
                                                        .firstOrNull()?.item ?: return@ClickableText
                                                    val isAb = song.mediaType == "AUDIOBOOK" || song.filePath in listOf("ONLINE_AUDIOBOOK", "OFFLINE_AUDIOBOOK")
                                                    val isPod = song.mediaType == "PODCAST" || song.filePath in listOf("ONLINE_PODCAST", "OFFLINE_PODCAST")
                                                    val targetArtist = when {
                                                        isAb -> "AUDIOBOOK_$clicked"
                                                        isPod && (song.channelId.isNullOrBlank() || !song.channelId!!.startsWith("UC")) -> "PODCAST_${song.album.takeIf { !it.isNullOrBlank() } ?: clicked}"
                                                        !song.channelId.isNullOrBlank() -> song.channelId!!
                                                        else -> clicked
                                                    }
                                                    onClose()
                                                    onNavigateToArtist(targetArtist)
                                                }
                                            }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (isYtSong || song.isVideo) {
                                        AudioVideoModePill(
                                            song = song,
                                            isYtSong = isYtSong,
                                            isVideoModeActive = isVideoModeActive,
                                            isPlaying = isPlaying,
                                            accentColor = expressiveScheme.primary,
                                            contentColorOnAccent = expressiveScheme.onPrimary,
                                            onSetVideoMode = { if (it) viewModel.switchToVideoMode(song) else viewModel.switchToAudioMode(song) },
                                            onPlayPause = onPlayPause,
                                            onClose = onClose,
                                            onPlayVideo = onPlayVideo,
                                            isVideoHandoffPending = isVideoHandoffPending
                                        )
                                    }
                                    favoriteButton(38.dp)
                                    aodButton(38.dp)
                                    menuButton(38.dp)
                                }
                            }

                            AnimatedVisibility(visible = hasDisplayableLyrics) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(expressiveScheme.surfaceContainer)
                                        .padding(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    NowPlayingLiveLyricStrip(
                                        song = song,
                                        currentLyrics = currentLyrics,
                                        currentPosition = viewModel.getEffectiveLyricsPosition(song, currentPosition),
                                        isPlaying = isPlaying,
                                        fontSize = 14.sp,
                                        textAlign = TextAlign.Center,
                                        contentAlignment = Alignment.Center,
                                        onClick = { showLyricsOverlay = true }
                                    )
                                }
                            }

                            Column(modifier = Modifier.fillMaxWidth()) {
                                timelineComposable()
                            }

                            V9ExpressiveTransportRow(
                                isPlaying = isPlaying,
                                isBusy = isBuffering,
                                isGuest = isGuest,
                                playContainerColor = expressiveScheme.primary,
                                playIconColor = expressiveScheme.onPrimary,
                                sideContainerColor = expressiveScheme.secondaryContainer,
                                sideIconColor = expressiveScheme.onSecondaryContainer,
                                onPrevious = onSkipPrevious,
                                onPlayPause = onPlayPause,
                                onNext = handleSkipNext,
                                onRewindHoldChanged = { holdScrubState.setRewind(it) },
                                onForwardHoldChanged = { holdScrubState.setForward(it) },
                                height = if (isCanvasBackground) 56.dp else 64.dp
                            )

                            if (isCanvasBackground) {
                                dockComposable(Modifier, 34.dp)
                            }
                        }
                    }
                    }

                    if (isYtSong) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black)
                                .padding(horizontal = 32.dp),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Box(modifier = Modifier.widthIn(max = 860.dp).fillMaxWidth()) {
                                MeldNowPlayingBelowTheFold(
                                    viewModel = viewModel,
                                    song = song,
                                    effectiveAccent = expressiveScheme.primary,
                                    contentColorOnAccent = expressiveScheme.onPrimary,
                                    onNavigateToArtist = { artistName ->
                                        onClose()
                                        onNavigateToArtist(artistName)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // 🔥 Crash-proof Portrait Player (Flexible center stage sizing with weight(1f, fill = false))
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .nestedScroll(nestedScroll)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                expressiveScheme.surfaceContainerHigh,
                                expressiveScheme.surfaceContainerLowest,
                                Color.Black
                            )
                        )
                    )
            ) {
                val totalViewportHeight = maxHeight
                val statusBarTop = with(density) { WindowInsets.statusBars.getTop(density).toDp() }
                val artShape = RoundedCornerShape(artCorner)

                val dockComposable: @Composable (Modifier, androidx.compose.ui.unit.Dp) -> Unit = { dockModifier, actionSize ->
                    Row(
                        modifier = dockModifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(expressiveScheme.surfaceContainer)
                            .border(1.dp, expressiveScheme.outlineVariant.copy(alpha = 0.28f), RoundedCornerShape(22.dp))
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ExpressiveDockAction(
                            icon = Icons.Rounded.Shuffle,
                            active = shuffleEnabled,
                            activeColor = expressiveScheme.primary,
                            inactiveColor = expressiveScheme.onSurfaceVariant,
                            size = actionSize,
                            onClick = { if (!isGuest) onToggleShuffle() }
                        )
                        ExpressiveDockAction(
                            icon = Icons.AutoMirrored.Rounded.QueueMusic,
                            active = showQueue,
                            activeColor = expressiveScheme.primary,
                            inactiveColor = expressiveScheme.onSurfaceVariant,
                            size = actionSize,
                            onClick = { showQueue = true }
                        )
                        ExpressiveDockAction(
                            icon = Icons.Rounded.MusicNote,
                            active = !currentLyrics.isNullOrBlank(),
                            activeColor = expressiveScheme.primary,
                            inactiveColor = expressiveScheme.onSurfaceVariant.copy(alpha = 0.45f),
                            size = actionSize,
                            onClick = { showLyricsOverlay = true }
                        )
                        ExpressiveDockAction(
                            icon = com.musicplayer.lytrix.ui.icons.LytrixIcons.Outlined.Bedtime,
                            active = sleepTimerState.isActive,
                            activeColor = expressiveScheme.primary,
                            inactiveColor = expressiveScheme.onSurfaceVariant,
                            size = actionSize,
                            badgeText = sleepTimerDisplay,
                            onClick = onOpenSleepTimer
                        )
                        ExpressiveDockAction(
                            icon = Icons.Rounded.Equalizer,
                            active = showEqualizer,
                            activeColor = expressiveScheme.primary,
                            inactiveColor = expressiveScheme.onSurfaceVariant,
                            size = actionSize,
                            onClick = { showEqualizer = true }
                        )
                        ExpressiveDockAction(
                            icon = Icons.Rounded.Headphones,
                            active = false,
                            activeColor = expressiveScheme.primary,
                            inactiveColor = expressiveScheme.onSurfaceVariant,
                            size = actionSize,
                            onClick = { showOutputSheet = true }
                        )
                        ExpressiveDockAction(
                            icon = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                            active = repeatMode != Player.REPEAT_MODE_OFF,
                            activeColor = expressiveScheme.primary,
                            inactiveColor = expressiveScheme.onSurfaceVariant,
                            size = actionSize,
                            onClick = { if (!isGuest) onToggleRepeat() }
                        )
                    }
                }

                val timelineComposable: @Composable () -> Unit = {
                    com.musicplayer.lytrix.ui.component.ABRepeatSeekBarBox(
                        pointA = pointA,
                        pointB = pointB,
                        durationMs = safeDuration,
                        markerColor = expressiveScheme.primary,
                    ) {
                        WavySeekBarExpressive(
                            progressFraction = shownFraction,
                            isPlaying = isPlaying && !isBuffering,
                            activeColor = expressiveScheme.primary,
                            trackColor = expressiveScheme.surfaceContainerHighest,
                            thumbColor = expressiveScheme.primary,
                            onProgressChange = { frac ->
                                if (!isGuest) scrubbingFraction = frac
                            },
                            onProgressChangeFinished = {
                                if (!isGuest) {
                                    scrubbingFraction?.let { frac -> onSeek((frac * safeDuration).toLong()) }
                                }
                                scrubbingFraction = null
                            }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatTrackTime(shownPosMs),
                            color = expressiveScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = qualityLabel,
                            color = expressiveScheme.primary.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = formatTrackTime(duration),
                            color = expressiveScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    com.musicplayer.lytrix.ui.component.ABRepeatControlPill(
                        pointA = pointA,
                        pointB = pointB,
                        accentColor = expressiveScheme.primary,
                        onSetPointA = { viewModel.setPointA() },
                        onSetPointB = { viewModel.setPointB() },
                        onClearAB = { viewModel.clearAB() },
                    )
                }

                val aodButton: @Composable (androidx.compose.ui.unit.Dp) -> Unit = { btnSize ->
                    Box(
                        modifier = Modifier
                            .size(btnSize)
                            .clip(CircleShape)
                            .background(expressiveScheme.surfaceContainerHighest)
                            .combinedClickable(
                                onClick = { AodLaunchTrigger.launch(initialLocked = false) },
                                onLongClick = { AodLaunchTrigger.launch(initialLocked = true) }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Aod,
                            contentDescription = "Always-On Display (Hold for Pocket Lock)",
                            tint = expressiveScheme.onSurface,
                            modifier = Modifier.size(btnSize * 0.48f)
                        )
                    }
                }

                val menuButton: @Composable (androidx.compose.ui.unit.Dp) -> Unit = { btnSize ->
                    Box(
                        modifier = Modifier
                            .size(btnSize)
                            .clip(CircleShape)
                            .background(expressiveScheme.surfaceContainerHighest)
                            .clickable(onClick = onOpenMenu),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "More Options",
                            tint = expressiveScheme.onSurface,
                            modifier = Modifier.size(btnSize * 0.50f)
                        )
                    }
                }

                val favoriteButton: @Composable (androidx.compose.ui.unit.Dp) -> Unit = { btnSize ->
                    Box(
                        modifier = Modifier
                            .size(btnSize)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (displayIsFavorite) expressiveScheme.tertiaryContainer
                                else expressiveScheme.surfaceContainerHigh
                            )
                            .clickable(onClick = onToggleFavorite),
                        contentAlignment = Alignment.Center
                    ) {
                        com.musicplayer.lytrix.ui.icons.MorphingMetroIcon(
                            selected = displayIsFavorite,
                            outlinedIcon = com.musicplayer.lytrix.ui.icons.LytrixIcons.Outlined.Star,
                            filledIcon = com.musicplayer.lytrix.ui.icons.LytrixIcons.Filled.Star,
                            contentDescription = "Favorite",
                            tint = if (displayIsFavorite) expressiveScheme.onTertiaryContainer else expressiveScheme.onSurfaceVariant,
                            modifier = Modifier.size(btnSize * 0.54f)
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                ) {
                    val isCanvasBackground = visualMedia != null &&
                        visualMedia.type != com.musicplayer.lytrix.spotify.canvas.VisualMediaType.APPLE_MUSIC_MOTION &&
                        !isVideoModeActive

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(totalViewportHeight)
                            .clipToBounds()
                    ) {
                        if (isCanvasBackground) {
                            // MetroList YouTube Music Video / Spotify Canvas: Fullscreen (fillMaxSize) with 6-stop dark gradient overlay
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .align(Alignment.Center)
                            ) {
                                LoopingCoverVideoPlayer(
                                    videoUrl = visualMedia!!.url,
                                    mediaType = visualMedia.type,
                                    song = song,
                                    artData = artData,
                                    isPlaying = isPlaying,
                                    currentPositionMs = currentPosition,
                                    durationMs = duration,
                                    onSeek = onSeek,
                                    onPlayPause = onPlayPause,
                                    onSkipNext = performSkipNext,
                                    onSkipPrevious = onSkipPrevious,
                                    gesturesEnabled = gesturesEnabled,
                                    isGuest = isGuest,
                                    clips = visualMedia.clips,
                                    clipShape = androidx.compose.ui.graphics.RectangleShape,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                0f to Color.Black.copy(alpha = 0.65f),
                                                0.12f to Color.Black.copy(alpha = 0.45f),
                                                0.3f to Color.Black.copy(alpha = 0.2f),
                                                0.48f to Color.Black.copy(alpha = 0.2f),
                                                0.64f to Color.Black.copy(alpha = 0.65f),
                                                1f to Color.Black.copy(alpha = 0.8f)
                                            )
                                        )
                                )
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .navigationBarsPadding()
                                .padding(top = statusBarTop + 8.dp, start = 20.dp, end = 20.dp, bottom = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                        // Expressive Tonal Top Bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(expressiveScheme.surfaceContainerHighest)
                                    .clickable(onClick = onClose),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = "Collapse",
                                    tint = expressiveScheme.onSurface,
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            if (isYtSong || song.isVideo) {
                                AudioVideoModePill(
                                    song = song,
                                    isYtSong = isYtSong,
                                    isVideoModeActive = isVideoModeActive,
                                    isPlaying = isPlaying,
                                    accentColor = expressiveScheme.primary,
                                    contentColorOnAccent = expressiveScheme.onPrimary,
                                    onSetVideoMode = { if (it) viewModel.switchToVideoMode(song) else viewModel.switchToAudioMode(song) },
                                    onPlayPause = onPlayPause,
                                    onClose = onClose,
                                    onPlayVideo = onPlayVideo,
                                    isVideoHandoffPending = isVideoHandoffPending
                                )
                            } else {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(expressiveScheme.secondaryContainer)
                                        .clickable { showOutputSheet = true }
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Headphones,
                                        contentDescription = null,
                                        tint = expressiveScheme.onSecondaryContainer,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = activeOutputName,
                                        color = expressiveScheme.onSecondaryContainer,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                aodButton(42.dp)
                                menuButton(42.dp)
                            }
                        }

                        // Center Stage (Crash-proof: weight(1f, fill = false))
                        Box(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isVideoModeActive) {
                                val idealHeight = screenWidth / videoAspect
                                val boxHeight = minOf(idealHeight, totalViewportHeight * 0.44f)
                                val videoFrameShape = RoundedCornerShape(8.dp)
                                Box(
                                    modifier = Modifier
                                        .requiredSize(screenWidth, boxHeight)
                                        .clip(videoFrameShape)
                                        .background(Color.Black)
                                ) {
                                    NowPlayingMediaStage(
                                        viewModel = viewModel,
                                        song = song,
                                        artData = artData,
                                        visualMedia = visualMedia,
                                        isVideoModeActive = true,
                                        isPlaying = isPlaying,
                                        currentPosition = currentPosition,
                                        duration = duration,
                                        accentColor = expressiveScheme.primary,
                                        gesturesEnabled = gesturesEnabled,
                                        isGuest = isGuest,
                                        shape = videoFrameShape,
                                        onSeek = onSeek,
                                        onPlayPause = onPlayPause,
                                        onSkipNext = performSkipNext,
                                        onSkipPrevious = onSkipPrevious,
                                        videoResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            } else if (isCanvasBackground) {
                                // Full-bleed tall canvas active in background: leave center space clear
                                Spacer(modifier = Modifier.height(totalViewportHeight * 0.38f))
                            } else {
                                NextSongTransitionArt(
                                    currentSong = song,
                                    nextSong = nextSong,
                                    currentPositionMs = currentPosition,
                                    durationMs = duration,
                                    isPlaying = isPlaying,
                                    isTransitionEnabled = isTransitionEnabled,
                                    artData = artData,
                                    nextArtData = nextArtData,
                                    manualSkipTrigger = manualSkipTrigger,
                                    onLongPress = {},
                                    onSkipNext = performSkipNext,
                                    onSkipPrevious = {
                                        onSkipPrevious()
                                        viewModel.forceSyncToFirebase()
                                    },
                                    onSeek = { seekPos ->
                                        onSeek(seekPos)
                                        viewModel.forceSyncToFirebase()
                                    },
                                    onPlayPause = {
                                        onPlayPause()
                                        viewModel.forceSyncToFirebase()
                                    },
                                    gesturesEnabled = gesturesEnabled,
                                    isGuest = isGuest,
                                    visualMedia = visualMedia.takeIf { it?.type == com.musicplayer.lytrix.spotify.canvas.VisualMediaType.APPLE_MUSIC_MOTION },
                                    modifier = Modifier
                                        .fillMaxWidth(0.92f)
                                        .padding(vertical = 8.dp)
                                        .aspectRatio(1f)
                                        .graphicsLayer {
                                            scaleX = artScale
                                            scaleY = artScale
                                            clip = false
                                        }
                                )
                            }
                        }

                        // Bottom Controls
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Title, Clickable Artist & Favorite Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isCanvasBackground) {
                                    coil.compose.AsyncImage(
                                        model = coil.request.ImageRequest.Builder(context)
                                            .data(artData ?: song.albumArtUriString ?: song.uriString)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = song.title,
                                        color = expressiveScheme.onSurface,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        modifier = Modifier.basicMarquee()
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    @Suppress("DEPRECATION")
                                    ClickableText(
                                        text = artistAnnotated,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        onClick = { offset ->
                                            if (isAppOnlineSong) {
                                                val clicked = artistAnnotated
                                                    .getStringAnnotations("ARTIST", offset, offset)
                                                    .firstOrNull()?.item ?: return@ClickableText
                                                val isAb = song.mediaType == "AUDIOBOOK" || song.filePath in listOf("ONLINE_AUDIOBOOK", "OFFLINE_AUDIOBOOK")
                                                val isPod = song.mediaType == "PODCAST" || song.filePath in listOf("ONLINE_PODCAST", "OFFLINE_PODCAST")
                                                val targetArtist = when {
                                                    isAb -> "AUDIOBOOK_$clicked"
                                                    isPod && (song.channelId.isNullOrBlank() || !song.channelId!!.startsWith("UC")) -> "PODCAST_${song.album.takeIf { !it.isNullOrBlank() } ?: clicked}"
                                                    !song.channelId.isNullOrBlank() -> song.channelId!!
                                                    else -> clicked
                                                }
                                                onClose()
                                                onNavigateToArtist(targetArtist)
                                            }
                                        }
                                    )
                                }

                                Spacer(modifier = Modifier.width(10.dp))
                                favoriteButton(44.dp)
                            }

                            AnimatedVisibility(visible = hasDisplayableLyrics) {
                                Column {
                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Integrated Tonal Live Lyric Capsule (hidden when no lyrics exist)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(expressiveScheme.surfaceContainer)
                                            .padding(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        NowPlayingLiveLyricStrip(
                                            song = song,
                                            currentLyrics = currentLyrics,
                                            currentPosition = viewModel.getEffectiveLyricsPosition(song, currentPosition),
                                            isPlaying = isPlaying,
                                            fontSize = 14.sp,
                                            textAlign = TextAlign.Start,
                                            contentAlignment = Alignment.CenterStart,
                                            onClick = { showLyricsOverlay = true }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            timelineComposable()
                            Spacer(modifier = Modifier.height(12.dp))

                            // V9 Spring-Physics Morphing Transport Bar
                            V9ExpressiveTransportRow(
                                isPlaying = isPlaying,
                                isBusy = isBuffering,
                                isGuest = isGuest,
                                playContainerColor = expressiveScheme.primary,
                                playIconColor = expressiveScheme.onPrimary,
                                sideContainerColor = expressiveScheme.secondaryContainer,
                                sideIconColor = expressiveScheme.onSecondaryContainer,
                                onPrevious = onSkipPrevious,
                                onPlayPause = onPlayPause,
                                onNext = handleSkipNext,
                                onRewindHoldChanged = { holdScrubState.setRewind(it) },
                                onForwardHoldChanged = { holdScrubState.setForward(it) },
                                height = 74.dp
                            )

                            Spacer(modifier = Modifier.height(12.dp))
                            dockComposable(Modifier, 42.dp)
                        }
                    }
                    }

                    if (isYtSong) {
                        MeldNowPlayingBelowTheFold(
                            viewModel = viewModel,
                            song = song,
                            effectiveAccent = expressiveScheme.primary,
                            contentColorOnAccent = expressiveScheme.onPrimary,
                            onNavigateToArtist = { artistName ->
                                onClose()
                                onNavigateToArtist(artistName)
                            }
                        )
                    }
                }
            }
        }

        // Mounted AFTER BoxWithConstraints so LyricsFullScreenOverlay draws on top of the player.
        NowPlayingCommonSheets(
            viewModel = viewModel,
            song = song,
            currentLyrics = currentLyrics,
            currentPosition = currentPosition,
            duration = duration,
            isPlaying = isPlaying,
            accentColor = expressiveScheme.primary,
            showLyricsOverlay = showLyricsOverlay,
            showQueue = showQueue,
            showEqualizer = showEqualizer,
            showOutputSheet = showOutputSheet,
            isVideoModeActive = isVideoModeActive,
            outputs = audioOutputs,
            onSelectOutput = selectOutput,
            onDismissLyrics = { showLyricsOverlay = false },
            onDismissQueue = { showQueue = false },
            onDismissEqualizer = { showEqualizer = false },
            onDismissOutput = { showOutputSheet = false },
            onDisableVideoMode = { viewModel.switchToAudioMode(song) },
            onSeek = onSeek,
            onPlayPause = onPlayPause,
            onSkipPrevious = onSkipPrevious,
            onSkipNext = performSkipNext,
            onScrollTopOrClose = {
                if (scrollState.value > 0) {
                    coroutineScope.launch { scrollState.animateScrollTo(0) }
                } else {
                    onClose()
                }
            }
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpressiveDockAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    size: androidx.compose.ui.unit.Dp = 42.dp,
    badgeText: String? = null,
    onClick: () -> Unit
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    androidx.compose.material3.ToggleButton(
        checked = active,
        onCheckedChange = {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
            onClick()
        },
        shapes = androidx.compose.material3.ToggleButtonDefaults.shapesFor(size),
        contentPadding = if (badgeText != null) {
            androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)
        } else {
            androidx.compose.foundation.layout.PaddingValues(0.dp)
        },
        colors = androidx.compose.material3.ToggleButtonDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = inactiveColor,
            checkedContainerColor = activeColor.copy(alpha = 0.22f),
            checkedContentColor = activeColor,
        ),
        modifier = if (badgeText != null) {
            Modifier.height(size).widthIn(min = size)
        } else {
            Modifier.size(size)
        },
    ) {
        if (badgeText != null) {
            Text(
                text = badgeText,
                style = MaterialTheme.typography.labelSmall,
                color = activeColor,
                maxLines = 1,
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(size * 0.50f)
            )
        }
    }
}
