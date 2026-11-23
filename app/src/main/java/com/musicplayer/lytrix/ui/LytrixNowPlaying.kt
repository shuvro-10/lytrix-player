@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.musicplayer.lytrix.ui

import com.musicplayer.lytrix.ui.component.NowPlayingVideoOverlayControls

import android.content.Context
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import com.musicplayer.lytrix.ui.component.tactileSpringPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.musicplayer.lytrix.artwork.ArtworkPreferencesManager
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.musicplayer.lytrix.model.Song
import com.musicplayer.lytrix.model.Video
import com.musicplayer.lytrix.ui.component.NextSongTransitionArt
import com.musicplayer.lytrix.ui.component.PlayerCoverWithGestures
import com.musicplayer.lytrix.ui.component.LoopingCoverVideoPlayer
import com.musicplayer.lytrix.ui.component.ArtistMotionVideoPlayer
import com.musicplayer.lytrix.spotify.SpotifyPreferencesManager
import com.musicplayer.lytrix.spotify.canvas.AppleMusicAnimatedArtwork
import android.content.res.Configuration
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * 🌟 Meld-Style Modern Now Playing Screen
 * হুবহু মেল্ডের নিউ প্লেয়ার ডিজাইন:
 * - অ্যালবামের আলো-ছায়াযুক্ত রিচ ডার্ক ব্যাকগ্রাউন্ড
 * - ২০ডিপি স্কুইরকল আর্টওয়ার্ক
 * - শেয়ার এবং লাইক/হার্ট বাটন ক্লাস্টার
 * - সলিড ওয়াইড স্লাইডার ও টাইমিং
 * - আইকনিক ৬৮ডিপি পিল প্লেয়ার কন্ট্রোলস (স্প্রিং ফিজিক্স ও লাইভ "Play"/"Pause" টেক্সট)
 * - নিচে ৫টি স্কয়ার বর্ডারড অপশন বাটন ও থ্রি-ডট মোর বাটন
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("UNUSED_PARAMETER")
@Composable
fun MeldNowPlayingFullScreen(
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
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp

    val dataStoreManager = remember { DataStoreManager(context) }
    val isTransitionEnabled by dataStoreManager.nextSongTransitionAnimationFlow.collectAsState(initial = true)
    val playerGesturesEnabled by dataStoreManager.playerGesturesEnabledFlow.collectAsState(initial = true)
    val myRoomStatus by viewModel.myRoomStatus.collectAsState()
    val metroRoomRole by viewModel.metroManager.roomRole.collectAsState()
    val isGuest = myRoomStatus == "approved" || myRoomStatus == "pending" || metroRoomRole == com.musicplayer.lytrix.listentogether.metro.MetroRoomRole.GUEST
    val currentQueue by viewModel.currentQueueList.collectAsState()
    val previousSong = remember(currentQueue, song.id, repeatMode) {
        val currentIndex = currentQueue.indexOfFirst { it.id == song.id }
        if (currentIndex > 0) {
            currentQueue[currentIndex - 1]
        } else if (currentIndex == 0 && repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF && currentQueue.size > 1) {
            currentQueue.lastOrNull()
        } else {
            null
        }
    }
    val nextSong = remember(currentQueue, song.id, repeatMode) {
        val currentIndex = currentQueue.indexOfFirst { it.id == song.id }
        if (currentIndex in 0 until currentQueue.lastIndex) {
            currentQueue[currentIndex + 1]
        } else if (currentIndex == currentQueue.lastIndex && repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF && currentQueue.size > 1) {
            currentQueue.firstOrNull()
        } else {
            null
        }
    }
    val nextArtData = rememberEmbeddedArt(context, nextSong)

    val isVideoModeActive by viewModel.isVideoModeActive.collectAsState()
    val isVideoHandoffPending by viewModel.isVideoHandoffPending.collectAsState()
    val sleepTimerState by viewModel.sleepTimerState.collectAsState()
    val sleepTimerDisplay = if (sleepTimerState.isActive) {
        com.musicplayer.lytrix.playback.formatSleepTimerRemaining(sleepTimerState.remainingMs)
    } else null
    val pointA by viewModel.pointA.collectAsState()
    val pointB by viewModel.pointB.collectAsState()
    var manualSkipTrigger by remember { mutableIntStateOf(0) }

    // অ্যানিমেশন শেষ হলে NextSongTransitionArt নিজেই এটা কল করবে
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
    var isLyricsExpanded by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showEqualizer by remember { mutableStateOf(false) }
    var showArtOptionsDialog by remember { mutableStateOf(false) }
    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderDragPosition by remember { mutableFloatStateOf(0f) }
    var dragAmountAccumulator by remember { mutableFloatStateOf(0f) }

    var draggingPosition by remember { mutableStateOf<Long?>(null) }
    var isPressingRewind by remember { mutableStateOf(false) }
    var isPressingForward by remember { mutableStateOf(false) }

    LaunchedEffect(isPressingForward) {
        if (isPressingForward) {
            var tempPos = draggingPosition ?: currentPosition
            while (true) {
                delay(50)
                tempPos = minOf(duration, tempPos + 1000)
                draggingPosition = tempPos
            }
        } else if (draggingPosition != null && !isPressingRewind) {
            onSeek(draggingPosition!!)
            draggingPosition = null
        }
    }

    LaunchedEffect(isPressingRewind) {
        if (isPressingRewind) {
            var tempPos = draggingPosition ?: currentPosition
            while (true) {
                delay(50)
                tempPos = maxOf(0, tempPos - 1000)
                draggingPosition = tempPos
            }
        } else if (draggingPosition != null && !isPressingForward) {
            onSeek(draggingPosition!!)
            draggingPosition = null
        }
    }

    val isLocalSong = remember(song.filePath, song.uriString) {
        song.filePath !in listOf("ONLINE_LYTRIX", "ONLINE") && !song.uriString.startsWith("http")
    }
    val isYtSong = remember(song.filePath, song.albumArtUriString, song.uriString) {
        com.musicplayer.lytrix.ui.player.isYouTubeSong(song)
    }

    val albumArtPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.saveCustomAlbumArt(context, song.id, uri)
        }
    }

    val scrollState = rememberScrollState()
    var pullDownAccumulator by remember { mutableFloatStateOf(0f) }

    // Reset scroll and recommendation request on new song
    var hasRequestedBelowTheFold by remember(song.id, song.originalId) { mutableStateOf(false) }
    LaunchedEffect(song.id) {
        if (scrollState.value != 0) {
            scrollState.scrollTo(0)
        }
    }

    // 🌟 Lazy load: Only triggers when user scrolls down! (Shared between Portrait & Landscape)
    LaunchedEffect(scrollState.value, song.id, song.originalId) {
        if (scrollState.value > 40 && !hasRequestedBelowTheFold && isYtSong) {
            hasRequestedBelowTheFold = true
            viewModel.loadNowPlayingRecommendations(song)
        }
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (scrollState.value == 0 && available.y > 0 && source == NestedScrollSource.Drag) {
                    pullDownAccumulator += available.y
                    if (pullDownAccumulator > 300f) {
                        onClose()
                        pullDownAccumulator = 0f
                        return available
                    }
                } else if (scrollState.value > 0) {
                    pullDownAccumulator = 0f
                }
                return Offset.Zero
            }
        }
    }

    BackHandler(enabled = true) {
        if (isLyricsExpanded) {
            isLyricsExpanded = false
        } else if (showQueue) {
            showQueue = false
        } else if (showEqualizer) {
            showEqualizer = false
        } else if (isVideoModeActive) {
            viewModel.switchToAudioMode(song)
        } else if (scrollState.value > 0) {
            coroutineScope.launch {
                scrollState.animateScrollTo(0)
            }
        } else {
            onClose()
        }
    }

    val mainActivity = context as? com.musicplayer.lytrix.MainActivity
    DisposableEffect(isLyricsExpanded, showQueue, showEqualizer, isVideoModeActive) {
        if (mainActivity != null) {
            mainActivity.nowPlayingBackHandler = {
                if (isLyricsExpanded) {
                    isLyricsExpanded = false
                    true
                } else if (showQueue) {
                    showQueue = false
                    true
                } else if (showEqualizer) {
                    showEqualizer = false
                    true
                } else if (isVideoModeActive) {
                    viewModel.switchToAudioMode(song)
                    true
                } else {
                    false
                }
            }
        }
        onDispose {
            if (mainActivity != null) {
                mainActivity.nowPlayingBackHandler = null
            }
        }
    }

    // 🔥 লাইক বাগ ফিক্স (অনলাইন ও অফলাইন সব গানের জন্য লাইক স্ট্যাটাস সিঙ্ক)
    val isAppOnlineSong = viewModel.isSongOnline(song)

    val onlineLikedIds by viewModel.onlineLikedIds.collectAsState()
    val displayIsFavorite = remember(song.id, song.originalId, song.filePath, song.uriString, onlineLikedIds, isFavorite) {
        if (viewModel.isSongOnline(song)) {
            onlineLikedIds.contains(viewModel.onlineLikeKey(song))
        } else {
            isFavorite
        }
    }

    // Palette extraction from album art
    val artData = rememberEmbeddedArt(context, song)
    var extractedPalette by remember { mutableStateOf<List<Color>>(emptyList()) }
    var artAccentColor by remember { mutableStateOf<Color?>(null) }

    LaunchedEffect(song.id, artData) {
        withContext(Dispatchers.IO) {
            try {
                val req = ImageRequest.Builder(context)
                    .data(artData ?: song.uriString)
                    .apply {
                        if ((artData == null || artData == song.uriString) && song.isVideo) {
                            videoFrameMillis(1000)
                        }
                    }
                    .allowHardware(false)
                    .build()
                val result = context.imageLoader.execute(req)
                val bitmap = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                if (bitmap != null) {
                    val palette = Palette.from(bitmap).maximumColorCount(16).generate()
                    val swatches = listOfNotNull(
                        palette.darkMutedSwatch,
                        palette.darkVibrantSwatch,
                        palette.dominantSwatch,
                        palette.mutedSwatch
                    )
                    if (swatches.isNotEmpty()) {
                        extractedPalette = swatches.map { Color(it.rgb) }
                    }
                    val vibrant = palette.vibrantSwatch
                        ?: palette.lightVibrantSwatch
                        ?: palette.dominantSwatch
                        ?: palette.mutedSwatch
                    if (vibrant != null) {
                        artAccentColor = Color(vibrant.rgb)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    // 🎨 Safe dynamic accent color: never too dark or black!
    val effectiveAccent = remember(accentColor, artAccentColor) {
        var base = artAccentColor ?: accentColor
        val lum = base.luminance()
        if (lum < 0.55f) {
            val blendFactor = ((0.55f - lum) / 0.55f).coerceIn(0.40f, 0.85f)
            base = lerp(base, Color.White, blendFactor)
        }
        base
    }
    val contentColorOnAccent = if (effectiveAccent.luminance() > 0.55f) Color.Black else Color.White

    val primaryBgColor = extractedPalette.getOrNull(0) ?: Color(0xFF1C1917)
    val secondaryBgColor = extractedPalette.getOrNull(1) ?: Color(0xFF0F0E0D)

    val backgroundBrush = remember(primaryBgColor, secondaryBgColor) {
        Brush.verticalGradient(
            colors = listOf(
                primaryBgColor.copy(alpha = 0.70f),
                secondaryBgColor.copy(alpha = 0.95f),
                Color(0xFF0A0908)
            )
        )
    }
    val artworkPrefs = remember { ArtworkPreferencesManager.getInstance(context) }
    val isCanvasEnabled by artworkPrefs.canvasEnabled.collectAsState()
    val isAppleMotionEnabled by artworkPrefs.animatedCoversEnabled.collectAsState()
    val contentOrder by artworkPrefs.canvasContentOrder.collectAsState()
    val enabledSources by artworkPrefs.canvasEnabledSources.collectAsState()
    val youtubeCanvasQuality by artworkPrefs.youtubeCanvasQuality.collectAsState()
    val youtubeCanvasMode by artworkPrefs.youtubeCanvasMode.collectAsState()
    val animatedCoverResolution by artworkPrefs.animatedCoverResolution.collectAsState()

    // 🎬 Visual Media (Apple Music Animated Artwork, YouTube Video Canvas or Spotify Canvas)
    var visualMedia by remember(song.id) { mutableStateOf<com.musicplayer.lytrix.spotify.canvas.VisualMediaResult?>(null) }
    LaunchedEffect(
        song.id, isAppleMotionEnabled, isCanvasEnabled, contentOrder, enabledSources,
        youtubeCanvasQuality, youtubeCanvasMode, animatedCoverResolution
    ) {
        visualMedia = null
        com.musicplayer.lytrix.spotify.canvas.CanvasVideoPlayerManager.releaseIfDifferentSong(song.id)
        if (!isAppleMotionEnabled && !isCanvasEnabled) {
            return@LaunchedEffect
        }
        visualMedia = com.musicplayer.lytrix.spotify.canvas.VisualMediaManager.getVisualMedia(context, song)
    }

    // 🎨 Live Artist Annotations
    val annotatedArtistString = remember(song.artist) {
        buildAnnotatedString {
            val artists = com.musicplayer.lytrix.utils.ArtistSeparatorManager.splitArtists(context, song.artist)
            artists.forEachIndexed { index, artistName ->
                val trimmedName = artistName
                if (trimmedName.isNotBlank()) {
                    pushStringAnnotation(tag = "ARTIST", annotation = trimmedName)
                    withStyle(
                        style = SpanStyle(
                            color = Color.White.copy(alpha = 0.68f),
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp
                        )
                    ) {
                        append(trimmedName)
                    }
                    pop()
                    if (index < artists.size - 1) {
                        withStyle(
                            style = SpanStyle(
                                color = Color.White.copy(alpha = 0.68f),
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp
                            )
                        ) {
                            append(", ")
                        }
                    }
                }
            }
        }
    }

    // 🎵 Live Lyrics handled cleanly by NowPlayingLiveLyricStrip

    // 🔄 Orientation & Window Insets Management
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val activityContext = context as? android.app.Activity
    val currentResizeMode by viewModel.videoResizeMode.collectAsState()
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

    if (isLandscape && isVideoModeActive) {
        // 🔥 Landscape Fullscreen Video Player (Edge-to-edge with hidden system bars & gestures)
        BackHandler(enabled = isLandscape && isVideoModeActive) {
            activityContext?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        val isVideoPlayerReady by viewModel.isVideoPlayerReady.collectAsState()
        val exoPlayer = viewModel.getVideoPlayer()
        var showControls by remember { mutableStateOf(true) }

        LaunchedEffect(showControls, isPlaying) {
            if (showControls && isPlaying) {
                delay(3500)
                showControls = false
            }
        }

        PlayerCoverWithGestures(
            gesturesEnabled = playerGesturesEnabled,
            isGuest = isGuest,
            isPlaying = isPlaying,
            currentPosition = currentPosition,
            duration = duration,
            onSeek = { seekPos ->
                onSeek(seekPos)
                val mappedSeek = viewModel.mapAudioToVideoPosition(song.id.toString(), seekPos)
                exoPlayer?.seekTo(mappedSeek)
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
            if (!isVideoPlayerReady || exoPlayer == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    LytrixExpressiveSpinner(color = effectiveAccent, size = 42.dp)
                }
            } else {
                androidx.compose.ui.viewinterop.AndroidView(
                    factory = { ctx ->
                        (android.view.LayoutInflater.from(ctx).inflate(
                            com.musicplayer.lytrix.R.layout.apple_music_texture_player_view,
                            null
                        ) as androidx.media3.ui.PlayerView).apply {
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
                        if (playerView.player != exoPlayer) {
                            playerView.player = exoPlayer
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
                                .background(effectiveAccent)
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Video", color = contentColorOnAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                                    if (currentResizeMode == androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM)
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
                    val positionToShow = if (isDraggingSlider) sliderDragPosition.toLong() else (draggingPosition ?: currentPosition)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(formatMillisToTime(positionToShow), fontSize = 12.sp, color = Color.White)
                        Slider(
                            value = (if (duration > 0) positionToShow.toFloat() / duration else 0f).coerceIn(0f, 1f),
                            onValueChange = { if (!isGuest) draggingPosition = (it * duration).toLong() },
                            onValueChangeFinished = {
                                if (!isGuest) draggingPosition?.let {
                                    onSeek(it)
                                    exoPlayer?.seekTo(it)
                                    viewModel.forceSyncToFirebase()
                                    draggingPosition = null
                                }
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = effectiveAccent,
                                activeTrackColor = effectiveAccent,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 16.dp)
                        )
                        Text(formatMillisToTime(duration), fontSize = 12.sp, color = Color.White)
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
                                .pointerInput(Unit) {
                                    detectTapGestures(onPress = {
                                        if (!isGuest) {
                                            val job = coroutineScope.launch { delay(400); isPressingRewind = true }
                                            tryAwaitRelease()
                                            job.cancel()
                                            if (!isPressingRewind) {
                                                onSkipPrevious()
                                                viewModel.forceSyncToFirebase()
                                            }
                                            isPressingRewind = false
                                        }
                                    })
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.SkipPrevious, contentDescription = "Skip Previous", tint = Color.White, modifier = Modifier.size(34.dp))
                        }
                        Spacer(modifier = Modifier.width(32.dp))
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(effectiveAccent)
                                .clickable {
                                    if (!isGuest) {
                                        onPlayPause()
                                        viewModel.forceSyncToFirebase()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isBuffering) {
                                LytrixExpressiveSpinner(color = contentColorOnAccent, strokeWidth = 3.dp, size = 28.dp)
                            } else {
                                Icon(
                                    if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = contentColorOnAccent,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(32.dp))
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .pointerInput(Unit) {
                                    detectTapGestures(onPress = {
                                        if (!isGuest) {
                                            val job = coroutineScope.launch { delay(400); isPressingForward = true }
                                            tryAwaitRelease()
                                            job.cancel()
                                            if (!isPressingForward) {
                                                handleSkipNext()
                                            }
                                            isPressingForward = false
                                        }
                                    })
                                },
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
                .nestedScroll(nestedScrollConnection)
                .background(Color.Black)
                .background(backgroundBrush)
        ) {
            val totalViewportHeight = maxHeight
            val totalViewportWidth = maxWidth
            val isCanvasBackground = visualMedia != null && visualMedia!!.type != com.musicplayer.lytrix.spotify.canvas.VisualMediaType.APPLE_MUSIC_MOTION && !isVideoModeActive

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                // Viewport 1: Full-Screen Landscape Player (MetroList Canvas Style + Scrollable Below-the-Fold)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(totalViewportHeight)
                        .clipToBounds()
                ) {
                    if (isCanvasBackground) {
                        // MetroList Landscape Fullscreen Canvas: 100% edge-to-edge video (no blurry half cutoff!)
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
                            gesturesEnabled = playerGesturesEnabled,
                            isGuest = isGuest,
                            clips = visualMedia!!.clips,
                            clipShape = RectangleShape,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Subtle horizontal readability scrim behind right-side controls (matches MetroList Image 1)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        0.00f to Color.Black.copy(alpha = 0.08f),
                                        0.38f to Color.Black.copy(alpha = 0.14f),
                                        0.62f to Color.Black.copy(alpha = 0.42f),
                                        1.00f to Color.Black.copy(alpha = 0.62f)
                                    )
                                )
                        )

                        // Top status-bar readability gradient
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
                    // MetroList Landscape Canvas: Keep left canvas area clean & unobstructed (no square box on top!)
                    Spacer(
                        modifier = Modifier
                            .weight(0.46f)
                            .fillMaxHeight()
                    )
                } else {
                // Left Column: NextSongTransitionArt + 5 Toolbar Buttons (for static cover & Apple Music 1:1)
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
                            gesturesEnabled = playerGesturesEnabled,
                            isGuest = isGuest,
                            visualMedia = visualMedia.takeIf { it?.type == com.musicplayer.lytrix.spotify.canvas.VisualMediaType.APPLE_MUSIC_MOTION },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 5 Segmented Buttons: [ Queue | Sleep Timer | Shuffle | Equalizer | Repeat ]
                    val queueShape = RoundedCornerShape(topStart = 50.dp, bottomStart = 50.dp, topEnd = 3.dp, bottomEnd = 3.dp)
                    val middleShape = RoundedCornerShape(3.dp)
                    val repeatShape = RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp, topEnd = 50.dp, bottomEnd = 50.dp)
                    val buttonSize = 40.dp

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(buttonSize)
                                    .clip(queueShape)
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .border(1.dp, Color.White.copy(alpha = 0.28f), queueShape)
                                    .clickable { showQueue = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = "Up Next Queue", tint = Color.White, modifier = Modifier.size(20.dp))
                            }

                            Box(
                                modifier = Modifier
                                    .then(
                                        if (sleepTimerDisplay != null) Modifier.height(buttonSize).widthIn(min = buttonSize)
                                        else Modifier.size(buttonSize)
                                    )
                                    .clip(middleShape)
                                    .background(if (sleepTimerState.isActive) effectiveAccent else Color.White.copy(alpha = 0.08f))
                                    .border(1.dp, if (sleepTimerState.isActive) effectiveAccent else Color.White.copy(alpha = 0.28f), middleShape)
                                    .clickable { onOpenSleepTimer() }
                                    .padding(horizontal = if (sleepTimerDisplay != null) 8.dp else 0.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (sleepTimerDisplay != null) {
                                    Text(
                                        text = sleepTimerDisplay,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = contentColorOnAccent,
                                        maxLines = 1,
                                    )
                                } else {
                                    Icon(Icons.Rounded.Bedtime, contentDescription = "Sleep Timer", tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                            }

                            val isShuffleActive = shuffleEnabled
                            Box(
                                modifier = Modifier
                                    .size(buttonSize)
                                    .clip(middleShape)
                                    .then(
                                        if (isShuffleActive) Modifier.background(effectiveAccent)
                                        else Modifier
                                            .background(Color.White.copy(alpha = 0.08f))
                                            .border(1.dp, Color.White.copy(alpha = 0.28f), middleShape)
                                    )
                                    .clickable { onToggleShuffle() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.Shuffle, contentDescription = "Shuffle", tint = if (isShuffleActive) contentColorOnAccent else Color.White, modifier = Modifier.size(20.dp))
                            }

                            Box(
                                modifier = Modifier
                                    .size(buttonSize)
                                    .clip(middleShape)
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .border(1.dp, Color.White.copy(alpha = 0.28f), middleShape)
                                    .clickable { showEqualizer = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.Equalizer, contentDescription = "Equalizer", tint = Color.White, modifier = Modifier.size(20.dp))
                            }

                            val isRepeatActive = repeatMode != 0
                            Box(
                                modifier = Modifier
                                    .size(buttonSize)
                                    .clip(repeatShape)
                                    .then(
                                        if (isRepeatActive) Modifier.background(effectiveAccent)
                                        else Modifier
                                            .background(Color.White.copy(alpha = 0.08f))
                                            .border(1.dp, Color.White.copy(alpha = 0.28f), repeatShape)
                                    )
                                    .clickable { onToggleRepeat() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (repeatMode == 1) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                                    contentDescription = "Repeat",
                                    tint = if (isRepeatActive) contentColorOnAccent else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
                }

                Spacer(modifier = Modifier.width(if (isCanvasBackground) 16.dp else 28.dp))

                // Right Column: Title/Artist + Capsule, Progress & Controls
                Column(
                    modifier = Modifier
                        .weight(0.58f)
                        .fillMaxHeight()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header: Collapse + Title/Artist + (Video pill if YT) + [ Share | Fav ]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
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
                                    .background(Color.White.copy(alpha = 0.12f))
                                    .clickable { onClose() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Collapse", tint = Color.White, modifier = Modifier.size(24.dp))
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.basicMarquee()
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                ClickableText(
                                    text = annotatedArtistString,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    onClick = { offset ->
                                        if (isAppOnlineSong) {
                                            val clickedArtist = annotatedArtistString.getStringAnnotations(tag = "ARTIST", start = offset, end = offset).firstOrNull()?.item ?: cleanArtistDisplayName(song.artist)
                                            onClose()
                                            val isAb = song.mediaType == "AUDIOBOOK" || song.filePath in listOf("ONLINE_AUDIOBOOK", "OFFLINE_AUDIOBOOK")
                                            val isPod = song.mediaType == "PODCAST" || song.filePath in listOf("ONLINE_PODCAST", "OFFLINE_PODCAST")
                                            val target = when {
                                                isAb -> "AUDIOBOOK_$clickedArtist"
                                                isPod && (song.channelId.isNullOrBlank() || !song.channelId!!.startsWith("UC")) -> "PODCAST_${song.album.takeIf { !it.isNullOrBlank() } ?: clickedArtist}"
                                                !song.channelId.isNullOrBlank() -> song.channelId!!
                                                else -> clickedArtist
                                            }
                                            onNavigateToArtist(target)
                                        }
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isYtSong) {
                                Box(
                                    modifier = Modifier
                                        .height(38.dp)
                                        .clip(RoundedCornerShape(19.dp))
                                        .background(Color.White.copy(alpha = 0.12f))
                                        .clickable {
                                            if (viewModel.isInListenTogetherRoom() && !viewModel.isSong16by9(song)) {
                                                android.widget.Toast.makeText(context, "Video mode for album tracks is disabled in Listen Together", android.widget.Toast.LENGTH_SHORT).show()
                                                return@clickable
                                            }
                                            val activity = context as? android.app.Activity
                                            if (activity != null) {
                                                AdManager.showAdForVideo(activity, song.id.toString()) {
                                                    viewModel.switchToVideoMode(song)
                                                }
                                            } else {
                                                viewModel.switchToVideoMode(song)
                                            }
                                        }
                                        .padding(horizontal = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (isVideoHandoffPending) {
                                            LytrixExpressiveSpinner(color = Color.White, size = 16.dp)
                                        } else {
                                            Icon(Icons.Rounded.SmartDisplay, contentDescription = "Video Mode", tint = Color.White, modifier = Modifier.size(18.dp))
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(if (isVideoHandoffPending) "Loading..." else "Video", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            val shareShape = RoundedCornerShape(topStart = 50.dp, bottomStart = 50.dp, topEnd = 3.dp, bottomEnd = 3.dp)
                            val favShape = RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp, topEnd = 50.dp, bottomEnd = 50.dp)

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(shareShape)
                                        .background(effectiveAccent)
                                        .clickable {
                                            if (isAppOnlineSong) {
                                                val isPodcast = song.filePath == "ONLINE_PODCAST" || song.filePath == "OFFLINE_PODCAST"
                                                val isJioSaavn = song.albumArtUriString?.contains("saavn", ignoreCase = true) == true
                                                shareOnlineSong(context, song, isPodcast, isJioSaavn)
                                            } else {
                                                shareLocalSong(context, song)
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Rounded.Share, contentDescription = "Share", tint = contentColorOnAccent, modifier = Modifier.size(18.dp))
                                }

                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(favShape)
                                        .background(effectiveAccent)
                                        .clickable { onToggleFavorite() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    com.musicplayer.lytrix.ui.icons.MorphingMetroIcon(
                                        selected = displayIsFavorite,
                                        outlinedIcon = com.musicplayer.lytrix.ui.icons.LytrixIcons.Outlined.FavoriteBorder,
                                        filledIcon = com.musicplayer.lytrix.ui.icons.LytrixIcons.Filled.Favorite,
                                        contentDescription = "Favorite",
                                        tint = if (displayIsFavorite) Color(0xFFE53935) else contentColorOnAccent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Live Lyrics Preview (Karaoke + Standard)
                    NowPlayingLiveLyricStrip(
                        song = song,
                        currentLyrics = currentLyrics,
                        currentPosition = viewModel.getEffectiveLyricsPosition(song, currentPosition),
                        isPlaying = isPlaying,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        contentAlignment = Alignment.Center,
                        loadingPlaceholderText = "Loading lyrics...",
                        onClick = { isLyricsExpanded = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                    )

                    // Progress Slider & Timestamps
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        val effectivePosition = if (isDraggingSlider) sliderDragPosition.toLong() else (draggingPosition ?: currentPosition)
                        val progressFraction = if (duration > 0) (effectivePosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

                        com.musicplayer.lytrix.ui.component.ABRepeatSeekBarBox(
                            pointA = pointA,
                            pointB = pointB,
                            durationMs = duration,
                            markerColor = effectiveAccent,
                        ) {
                            Slider(
                                value = progressFraction,
                                onValueChange = { frac ->
                                    if (!isGuest) {
                                        isDraggingSlider = true
                                        sliderDragPosition = frac * duration
                                    }
                                },
                                onValueChangeFinished = {
                                    if (!isGuest) {
                                        isDraggingSlider = false
                                        onSeek(sliderDragPosition.toLong())
                                    }
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = effectiveAccent,
                                    activeTrackColor = effectiveAccent,
                                    inactiveTrackColor = effectiveAccent.copy(alpha = 0.25f)
                                ),
                                modifier = Modifier.fillMaxWidth().height(26.dp)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = formatMillisToTime(effectivePosition), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.65f))
                            Text(text = formatMillisToTime(duration), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.65f))
                        }

                        com.musicplayer.lytrix.ui.component.ABRepeatControlPill(
                            pointA = pointA,
                            pointB = pointB,
                            accentColor = effectiveAccent,
                            onSetPointA = { viewModel.setPointA() },
                            onSetPointB = { viewModel.setPointB() },
                            onClearAB = { viewModel.clearAB() },
                        )
                    }

                    // Controls Row (Previous, Play/Pause with text, Next, More options circle)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        val backInteractionSource = remember { MutableInteractionSource() }
                        val nextInteractionSource = remember { MutableInteractionSource() }
                        val playPauseInteractionSource = remember { MutableInteractionSource() }
                        val landscapePlayCorner by animateDpAsState(
                            targetValue = if (isPlaying) 28.dp else 18.dp,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                            label = "landscapePlayCorner"
                        )
                        val landscapeHaptic = androidx.compose.ui.platform.LocalHapticFeedback.current

                        // ⏮ Previous Circle (Expressive Spring Press + Ripple)
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .tactileSpringPress(backInteractionSource, 0.88f)
                                .clip(CircleShape)
                                .background(effectiveAccent.copy(alpha = 0.16f))
                                .indication(backInteractionSource, ripple())
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onPress = { offset ->
                                            val press = PressInteraction.Press(offset)
                                            coroutineScope.launch { backInteractionSource.emit(press) }
                                            val job = coroutineScope.launch { delay(400); isPressingRewind = true }
                                            try {
                                                tryAwaitRelease()
                                            } finally {
                                                job.cancel()
                                                coroutineScope.launch { backInteractionSource.emit(PressInteraction.Release(press)) }
                                                if (!isPressingRewind && !isGuest) {
                                                    landscapeHaptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                                    onSkipPrevious()
                                                    viewModel.forceSyncToFirebase()
                                                }
                                                isPressingRewind = false
                                            }
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(com.musicplayer.lytrix.ui.icons.LytrixIcons.Rounded.SkipPrevious, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(28.dp))
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // ⏯ Main Play / Pause Capsule (Expressive Corner Morph + Spring Press + Ripple + Morphing Icon)
                        Box(
                            modifier = Modifier
                                .height(56.dp)
                                .widthIn(min = 140.dp)
                                .tactileSpringPress(playPauseInteractionSource, 0.93f)
                                .clip(RoundedCornerShape(landscapePlayCorner))
                                .background(effectiveAccent)
                                .clickable(interactionSource = playPauseInteractionSource, indication = ripple()) {
                                    if (!isGuest) {
                                        landscapeHaptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                        onPlayPause()
                                    }
                                }
                                .padding(horizontal = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isBuffering) {
                                LytrixExpressiveSpinner(color = contentColorOnAccent, size = 26.dp)
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    com.musicplayer.lytrix.ui.component.MorphingPlayPauseIcon(
                                        isPlaying = isPlaying,
                                        playIcon = com.musicplayer.lytrix.ui.icons.LytrixIcons.Rounded.PlayArrow,
                                        pauseIcon = com.musicplayer.lytrix.ui.icons.LytrixIcons.Rounded.Pause,
                                        tint = contentColorOnAccent,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isPlaying) "Pause" else "Play",
                                        color = contentColorOnAccent,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // ⏭ Next Circle (Expressive Spring Press + Ripple)
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .tactileSpringPress(nextInteractionSource, 0.88f)
                                .clip(CircleShape)
                                .background(effectiveAccent.copy(alpha = 0.16f))
                                .indication(nextInteractionSource, ripple())
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onPress = { offset ->
                                            val press = PressInteraction.Press(offset)
                                            coroutineScope.launch { nextInteractionSource.emit(press) }
                                            val job = coroutineScope.launch { delay(400); isPressingForward = true }
                                            try {
                                                tryAwaitRelease()
                                            } finally {
                                                job.cancel()
                                                coroutineScope.launch { nextInteractionSource.emit(PressInteraction.Release(press)) }
                                                if (!isPressingForward && !isGuest) {
                                                    landscapeHaptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                                    handleSkipNext()
                                                }
                                                isPressingForward = false
                                            }
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(com.musicplayer.lytrix.ui.icons.LytrixIcons.Rounded.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(28.dp))
                        }

                        if (!isCanvasBackground) {
                            Spacer(modifier = Modifier.width(16.dp))

                            // 6. More Options (Material 3 Expressive Morphing IconButton)
                            com.musicplayer.lytrix.ui.component.MetroMorphingIconButton(
                                onClick = onOpenMenu,
                                containerColor = effectiveAccent,
                                contentColor = contentColorOnAccent,
                                size = 46.dp
                            ) {
                                Icon(
                                    imageVector = com.musicplayer.lytrix.ui.icons.LytrixIcons.Default.MoreVert,
                                    contentDescription = "More",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    if (isCanvasBackground) {
                        Spacer(modifier = Modifier.height(12.dp))
                        // Authentic MetroList Material 3 Expressive Connected ButtonGroup Toolbar + Morphing 3-dots
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            com.musicplayer.lytrix.ui.component.MetroConnectedPlayerToolbar(
                                actions = listOf(
                                    com.musicplayer.lytrix.ui.component.MetroToolbarAction(
                                        outlinedIcon = com.musicplayer.lytrix.ui.icons.LytrixIcons.AutoMirrored.Rounded.QueueMusic,
                                        contentDescription = "Queue",
                                        checked = showQueue,
                                        onClick = { showQueue = true }
                                    ),
                                    com.musicplayer.lytrix.ui.component.MetroToolbarAction(
                                        outlinedIcon = com.musicplayer.lytrix.ui.icons.LytrixIcons.Outlined.Bedtime,
                                        filledIcon = com.musicplayer.lytrix.ui.icons.LytrixIcons.Filled.Bedtime,
                                        contentDescription = "Sleep Timer",
                                        checked = sleepTimerState.isActive,
                                        text = sleepTimerDisplay,
                                        onClick = onOpenSleepTimer
                                    ),
                                    com.musicplayer.lytrix.ui.component.MetroToolbarAction(
                                        outlinedIcon = com.musicplayer.lytrix.ui.icons.LytrixIcons.Rounded.Shuffle,
                                        contentDescription = "Shuffle",
                                        checked = shuffleEnabled,
                                        onClick = onToggleShuffle
                                    ),
                                    com.musicplayer.lytrix.ui.component.MetroToolbarAction(
                                        outlinedIcon = com.musicplayer.lytrix.ui.icons.LytrixIcons.Outlined.Equalizer,
                                        contentDescription = "Equalizer",
                                        checked = showEqualizer,
                                        onClick = { showEqualizer = true }
                                    ),
                                    com.musicplayer.lytrix.ui.component.MetroToolbarAction(
                                        outlinedIcon = if (repeatMode == 1) com.musicplayer.lytrix.ui.icons.LytrixIcons.Rounded.RepeatOne else com.musicplayer.lytrix.ui.icons.LytrixIcons.Rounded.Repeat,
                                        contentDescription = "Repeat",
                                        checked = repeatMode != 0,
                                        onClick = onToggleRepeat
                                    ),
                                ),
                                activeContainerColor = effectiveAccent,
                                activeContentColor = contentColorOnAccent,
                                inactiveContainerColor = Color.Black.copy(alpha = 0.38f),
                                inactiveContentColor = Color.White,
                                buttonHeight = 38.dp,
                                buttonWidth = 40.dp,
                                iconSize = 18.dp
                            )
                            com.musicplayer.lytrix.ui.component.MetroMorphingIconButton(
                                onClick = onOpenMenu,
                                containerColor = effectiveAccent,
                                contentColor = contentColorOnAccent,
                                size = 40.dp
                            ) {
                                Icon(
                                    imageVector = com.musicplayer.lytrix.ui.icons.LytrixIcons.Default.MoreVert,
                                    contentDescription = "More",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Viewport 2: Below-the-fold content (Meet the Artist, More from Artist, Similar Tracks)
        if (isYtSong) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Box(modifier = Modifier.widthIn(max = 860.dp).fillMaxWidth()) {
                    MeldNowPlayingBelowTheFold(
                        viewModel = viewModel,
                        song = song,
                        effectiveAccent = effectiveAccent,
                        contentColorOnAccent = contentColorOnAccent,
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

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
            .nestedScroll(nestedScrollConnection)
            .background(Color.Black)
            .background(backgroundBrush)
    ) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val statusBarTop = with(density) { WindowInsets.statusBars.getTop(density).toDp() }
        val totalViewportHeight = maxHeight
        val screenWidth = maxWidth

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            // Viewport 1: Full-Screen Player Screen (Main Controls)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(totalViewportHeight)
                    .clipToBounds()
            ) {
                if (visualMedia != null && visualMedia!!.type != com.musicplayer.lytrix.spotify.canvas.VisualMediaType.APPLE_MUSIC_MOTION && !isVideoModeActive) {
                    // MetroList YouTube Music Video / Spotify Canvas: Fullscreen (fillMaxSize) with 6-stop dark gradient overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .align(Alignment.Center)
                    ) {
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
                            gesturesEnabled = playerGesturesEnabled,
                            isGuest = isGuest,
                            clips = visualMedia!!.clips,
                            clipShape = RectangleShape,
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
            // ==========================================
            // 1. TOP HEADER (Collapse, Audio/Video Switcher & Cast)
            // ==========================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Circular Collapse Button
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Collapse",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Center Content: Audio/Video Switcher (or Back to Video or Now Playing)
                if (song.isVideo) {
                    // 🌟 Premium Meld-Style "Back to Video" Capsule Button
                    Box(
                        modifier = Modifier
                            .height(42.dp)
                            .clip(RoundedCornerShape(21.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        effectiveAccent.copy(alpha = 0.22f),
                                        effectiveAccent.copy(alpha = 0.10f)
                                    )
                                )
                            )
                            .border(
                                width = 1.dp,
                                color = effectiveAccent.copy(alpha = 0.40f),
                                shape = RoundedCornerShape(21.dp)
                            )
                            .clickable {
                                onClose()
                                if (isPlaying) onPlayPause()
                                val v = Video(
                                    id = -song.id,
                                    title = song.title,
                                    durationText = "",
                                    sizeText = "",
                                    uriString = song.uriString,
                                    path = song.filePath ?: "",
                                    dateModified = 0L
                                )
                                onPlayVideo(v)
                            }
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Videocam,
                                contentDescription = "Back to Video",
                                tint = effectiveAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Back to Video",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = null,
                                tint = effectiveAccent.copy(alpha = 0.85f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                } else if (isYtSong) {
                    // Iconic Segmented Audio / Video Pill Switcher
                    val isAudioSelected = !isVideoModeActive
                    val isVideoSelected = isVideoModeActive

                    val audioBgColor by animateColorAsState(
                        targetValue = if (isAudioSelected) effectiveAccent else Color.Transparent,
                        animationSpec = tween(250),
                        label = "audioBg"
                    )
                    val audioIconTint by animateColorAsState(
                        targetValue = if (isAudioSelected) contentColorOnAccent else Color.White.copy(alpha = 0.65f),
                        animationSpec = tween(250),
                        label = "audioTint"
                    )
                    val videoBgColor by animateColorAsState(
                        targetValue = if (isVideoSelected) effectiveAccent else Color.Transparent,
                        animationSpec = tween(250),
                        label = "videoBg"
                    )
                    val videoIconTint by animateColorAsState(
                        targetValue = if (isVideoSelected) contentColorOnAccent else Color.White.copy(alpha = 0.65f),
                        animationSpec = tween(250),
                        label = "videoTint"
                    )

                    Box(
                        modifier = Modifier
                            .height(42.dp)
                            .clip(RoundedCornerShape(21.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .padding(3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Audio Option
                            Box(
                                modifier = Modifier
                                    .height(36.dp)
                                    .width(50.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(audioBgColor)
                                    .clickable {
                                        viewModel.switchToAudioMode(song)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Headphones,
                                    contentDescription = "Audio Mode",
                                    tint = audioIconTint,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Video Option
                            Box(
                                modifier = Modifier
                                    .height(36.dp)
                                    .width(50.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(videoBgColor)
                                    .clickable {
                                        if (!isVideoModeActive) {
                                            if (viewModel.isInListenTogetherRoom() && !viewModel.isSong16by9(song)) {
                                                android.widget.Toast.makeText(context, "Video mode for album tracks is disabled in Listen Together", android.widget.Toast.LENGTH_SHORT).show()
                                                return@clickable
                                            }
                                            val activity = context as? android.app.Activity
                                            if (activity != null) {
                                                AdManager.showAdForVideo(activity, song.id.toString()) {
                                                    viewModel.switchToVideoMode(song)
                                                }
                                            } else {
                                                viewModel.switchToVideoMode(song)
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isVideoHandoffPending) {
                                    LytrixExpressiveSpinner(color = videoIconTint, size = 18.dp)
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.SmartDisplay,
                                        contentDescription = "Video Mode",
                                        tint = videoIconTint,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Now Playing",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }

                // Circular Cast Button (Matching top-bar design in screenshot)
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .clickable {
                            android.widget.Toast.makeText(context, "No cast devices found", android.widget.Toast.LENGTH_SHORT).show()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Cast,
                        contentDescription = "Cast",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // ==========================================
            // 2. ALBUM ARTWORK OR VIDEO PLAYER (Contained with flexible weight)
            // ==========================================
            Box(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (isVideoModeActive) {
                    val isVideoPlayerReady by viewModel.isVideoPlayerReady.collectAsState()
                    val exoPlayer = viewModel.getVideoPlayer()
                    val baseSpeed by viewModel.currentPlaybackSpeed.collectAsState()

                    // ভিডিওর আসল aspect ratio (16:9 নাকি 1:1)
                    var videoAspect by remember(song.id) { mutableFloatStateOf(16f / 9f) }
                    DisposableEffect(exoPlayer) {
                        fun update(size: androidx.media3.common.VideoSize) {
                            if (size.width > 0 && size.height > 0) {
                                videoAspect = (size.width * size.pixelWidthHeightRatio / size.height)
                                    .coerceIn(0.5f, 2.4f)
                            }
                        }
                        val listener = object : androidx.media3.common.Player.Listener {
                            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                                update(videoSize)
                            }
                        }
                        exoPlayer?.let {
                            update(it.videoSize)
                            it.addListener(listener)
                        }
                        onDispose { exoPlayer?.removeListener(listener) }
                    }

                    BoxWithConstraints(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        val idealHeight = screenWidth / videoAspect
                        val boxHeight = minOf(idealHeight, maxHeight)   // কখনো ফাঁকা জায়গার বেশি হবে না
                        val isHeightLimited = boxHeight < idealHeight - 1.dp

                        PlayerCoverWithGestures(
                            gesturesEnabled = playerGesturesEnabled,
                            isGuest = isGuest,
                            isPlaying = isPlaying,
                            currentPosition = currentPosition,
                            duration = duration,
                            onSeek = { seekPos ->
                                onSeek(seekPos)
                                exoPlayer?.seekTo(seekPos)
                                viewModel.forceSyncToFirebase()
                            },
                            onPlayPause = {
                                onPlayPause()
                                viewModel.forceSyncToFirebase()
                            },
                            swipeEnabled = false,
                            modifier = Modifier
                                .requiredSize(screenWidth, boxHeight)
                                .background(Color.Black)
                        ) {
                            if (isHeightLimited) {
                                AsyncImage(
                                    model = artData ?: song.albumArtUriString ?: song.uriString,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .blur(25.dp)
                                        .alpha(0.7f)
                                )
                            }

                            if (!isVideoPlayerReady || exoPlayer == null) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    LytrixExpressiveSpinner(
                                        color = effectiveAccent,
                                        size = 42.dp
                                    )
                                }
                            } else {
                                androidx.compose.ui.viewinterop.AndroidView(
                                    factory = { ctx ->
                                        (android.view.LayoutInflater.from(ctx).inflate(
                                            com.musicplayer.lytrix.R.layout.apple_music_texture_player_view,
                                            null
                                        ) as androidx.media3.ui.PlayerView).apply {
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
                                        if (playerView.player != exoPlayer) {
                                            playerView.player = exoPlayer
                                        }
                                        playerView.resizeMode = currentResizeMode
                                        playerView.subtitleView?.visibility = android.view.View.GONE
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                                com.musicplayer.lytrix.ui.component.NowPlayingVideoOverlayControls(viewModel = viewModel)
                            }
                        }
                    }
                } else if (visualMedia != null && visualMedia!!.type != com.musicplayer.lytrix.spotify.canvas.VisualMediaType.APPLE_MUSIC_MOTION) {
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
                        gesturesEnabled = playerGesturesEnabled,
                        isGuest = isGuest,
                        visualMedia = visualMedia.takeIf { it?.type == com.musicplayer.lytrix.spotify.canvas.VisualMediaType.APPLE_MUSIC_MOTION },
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .padding(vertical = 8.dp)
                            .aspectRatio(1f)
                    )
                }
            }

            // ==========================================
            // 2.5 LIVE LYRICS PREVIEW (Word-by-Word Karaoke + Standard)
            // ==========================================
            NowPlayingLiveLyricStrip(
                song = song,
                currentLyrics = currentLyrics,
                currentPosition = viewModel.getEffectiveLyricsPosition(song, currentPosition),
                isPlaying = isPlaying,
                fontSize = 17.sp,
                textAlign = TextAlign.Center,
                contentAlignment = Alignment.Center,
                loadingPlaceholderText = "Loading lyrics...",
                onClick = { isLyricsExpanded = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 2.dp)
            )

            // ==========================================
            // 3. TITLE, ARTIST & ACTION CLUSTER ROW
            // ==========================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (visualMedia != null && visualMedia!!.type != com.musicplayer.lytrix.spotify.canvas.VisualMediaType.APPLE_MUSIC_MOTION && !isVideoModeActive) {
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
                // Song Title & Artist
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp)
                ) {
                    Text(
                        text = song.title,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee()
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    // Uses hoisted annotatedArtistString
                    ClickableText(
                        text = annotatedArtistString,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        onClick = { offset ->
                            if (isAppOnlineSong) {
                                val clickedArtist = annotatedArtistString.getStringAnnotations(tag = "ARTIST", start = offset, end = offset)
                                    .firstOrNull()?.item ?: cleanArtistDisplayName(song.artist)
                                onClose()
                                val isAb = song.mediaType == "AUDIOBOOK" || song.filePath in listOf("ONLINE_AUDIOBOOK", "OFFLINE_AUDIOBOOK")
                                val isPod = song.mediaType == "PODCAST" || song.filePath in listOf("ONLINE_PODCAST", "OFFLINE_PODCAST")
                                val target = when {
                                    isAb -> "AUDIOBOOK_$clickedArtist"
                                    isPod && (song.channelId.isNullOrBlank() || !song.channelId!!.startsWith("UC")) -> "PODCAST_${song.album.takeIf { !it.isNullOrBlank() } ?: clickedArtist}"
                                    !song.channelId.isNullOrBlank() -> song.channelId!!
                                    else -> clickedArtist
                                }
                                onNavigateToArtist(target)
                            }
                        }
                    )
                }

                // Quick Action Buttons (Share & Favorite Segmented Pills with Dynamic Tint)
                val shareShape = RoundedCornerShape(topStart = 50.dp, bottomStart = 50.dp, topEnd = 3.dp, bottomEnd = 3.dp)
                val favShape = RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp, topEnd = 50.dp, bottomEnd = 50.dp)

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Share Button (Segmented Left Pill)
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(shareShape)
                            .background(effectiveAccent)
                            .clickable {
                                if (isAppOnlineSong) {
                                    val isPodcast = song.filePath == "ONLINE_PODCAST" || song.filePath == "OFFLINE_PODCAST"
                                    val isJioSaavn = song.albumArtUriString?.contains("saavn", ignoreCase = true) == true
                                    shareOnlineSong(context, song, isPodcast, isJioSaavn)
                                } else {
                                    shareLocalSong(context, song)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = "Share",
                            tint = contentColorOnAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Favorite / Heart Button (Segmented Right Pill)
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(favShape)
                            .background(effectiveAccent)
                            .clickable { onToggleFavorite() },
                        contentAlignment = Alignment.Center
                    ) {
                        com.musicplayer.lytrix.ui.icons.MorphingMetroIcon(
                            selected = displayIsFavorite,
                            outlinedIcon = com.musicplayer.lytrix.ui.icons.LytrixIcons.Outlined.FavoriteBorder,
                            filledIcon = com.musicplayer.lytrix.ui.icons.LytrixIcons.Filled.Favorite,
                            contentDescription = "Favorite",
                            tint = if (displayIsFavorite) Color(0xFFE53935) else contentColorOnAccent,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // ==========================================
            // 4. PROGRESS BAR & TIMESTAMPS
            // ==========================================
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                val effectivePosition = if (isDraggingSlider) sliderDragPosition.toLong() else (draggingPosition ?: currentPosition)
                val progressFraction = if (duration > 0) (effectivePosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

                com.musicplayer.lytrix.ui.component.ABRepeatSeekBarBox(
                    pointA = pointA,
                    pointB = pointB,
                    durationMs = duration,
                    markerColor = effectiveAccent,
                ) {
                    Slider(
                        value = progressFraction,
                        onValueChange = { frac ->
                            if (!isGuest) {
                                isDraggingSlider = true
                                sliderDragPosition = frac * duration
                            }
                        },
                        onValueChangeFinished = {
                            if (!isGuest) {
                                isDraggingSlider = false
                                onSeek(sliderDragPosition.toLong())
                            }
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = effectiveAccent,
                            activeTrackColor = effectiveAccent,
                            inactiveTrackColor = effectiveAccent.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier.fillMaxWidth().height(28.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatMillisToTime(effectivePosition),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.65f)
                    )
                    Text(
                        text = formatMillisToTime(duration),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.65f)
                    )
                }

                com.musicplayer.lytrix.ui.component.ABRepeatControlPill(
                    pointA = pointA,
                    pointB = pointB,
                    accentColor = effectiveAccent,
                    onSetPointA = { viewModel.setPointA() },
                    onSetPointB = { viewModel.setPointB() },
                    onClearAB = { viewModel.clearAB() },
                )
            }

            // ==========================================
            // 5. ICONIC MELD SPRING PILL BUTTONS (68dp)
            // ==========================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                val backInteractionSource = remember { MutableInteractionSource() }
                val nextInteractionSource = remember { MutableInteractionSource() }
                val playPauseInteractionSource = remember { MutableInteractionSource() }

                val isPlayPausePressed by playPauseInteractionSource.collectIsPressedAsState()
                val isBackPressed by backInteractionSource.collectIsPressedAsState()
                val isNextPressed by nextInteractionSource.collectIsPressedAsState()

                // Dynamic Spring Physics Weights (Meld exact physics)
                val playPauseWeight by animateFloatAsState(
                    targetValue = when {
                        isPlayPausePressed -> 1.85f
                        isBackPressed || isNextPressed -> 1.05f
                        else -> 1.35f
                    },
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
                    label = "playPauseWeight"
                )

                val backButtonWeight by animateFloatAsState(
                    targetValue = when {
                        isBackPressed -> 0.65f
                        isPlayPausePressed -> 0.35f
                        else -> 0.45f
                    },
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
                    label = "backButtonWeight"
                )

                val nextButtonWeight by animateFloatAsState(
                    targetValue = when {
                        isNextPressed -> 0.65f
                        isPlayPausePressed -> 0.35f
                        else -> 0.45f
                    },
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
                    label = "nextButtonWeight"
                )

                val portraitHaptic = androidx.compose.ui.platform.LocalHapticFeedback.current
                val portraitPlayCorner by animateDpAsState(
                    targetValue = if (isPlaying) 34.dp else 20.dp,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "portraitPlayCorner"
                )

                // ⏮ Previous Track Pill (Spring Weight + Ripple + Haptic)
                Box(
                    modifier = Modifier
                        .height(68.dp)
                        .weight(backButtonWeight)
                        .clip(RoundedCornerShape(50))
                        .background(effectiveAccent.copy(alpha = 0.16f))
                        .indication(backInteractionSource, ripple())
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = { offset ->
                                    val press = PressInteraction.Press(offset)
                                    coroutineScope.launch { backInteractionSource.emit(press) }
                                    val job = coroutineScope.launch {
                                        delay(400)
                                        isPressingRewind = true
                                    }
                                    try {
                                        tryAwaitRelease()
                                    } finally {
                                        job.cancel()
                                        coroutineScope.launch { backInteractionSource.emit(PressInteraction.Release(press)) }
                                        if (!isPressingRewind && !isGuest) {
                                            portraitHaptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                            onSkipPrevious()
                                            viewModel.forceSyncToFirebase()
                                        }
                                        isPressingRewind = false
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = com.musicplayer.lytrix.ui.icons.LytrixIcons.Rounded.SkipPrevious,
                        contentDescription = "Previous",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // ⏯ Main Play / Pause Pill (Expressive Corner Morph + Spring Weight + Ripple + Morphing Icon)
                Box(
                    modifier = Modifier
                        .height(68.dp)
                        .weight(playPauseWeight)
                        .clip(RoundedCornerShape(portraitPlayCorner))
                        .background(effectiveAccent)
                        .clickable(interactionSource = playPauseInteractionSource, indication = ripple()) {
                            if (!isGuest) {
                                portraitHaptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                onPlayPause()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isBuffering) {
                        LytrixExpressiveSpinner(color = contentColorOnAccent, size = 26.dp)
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            com.musicplayer.lytrix.ui.component.MorphingPlayPauseIcon(
                                isPlaying = isPlaying,
                                playIcon = com.musicplayer.lytrix.ui.icons.LytrixIcons.Rounded.PlayArrow,
                                pauseIcon = com.musicplayer.lytrix.ui.icons.LytrixIcons.Rounded.Pause,
                                tint = contentColorOnAccent,
                                modifier = Modifier.size(30.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isPlaying) "Pause" else "Play",
                                color = contentColorOnAccent,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // ⏭ Next Track Pill (Spring Weight + Ripple + Haptic)
                Box(
                    modifier = Modifier
                        .height(68.dp)
                        .weight(nextButtonWeight)
                        .clip(RoundedCornerShape(50))
                        .background(effectiveAccent.copy(alpha = 0.16f))
                        .indication(nextInteractionSource, ripple())
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = { offset ->
                                    val press = PressInteraction.Press(offset)
                                    coroutineScope.launch { nextInteractionSource.emit(press) }
                                    val job = coroutineScope.launch {
                                        delay(400)
                                        isPressingForward = true
                                    }
                                    try {
                                        tryAwaitRelease()
                                    } finally {
                                        job.cancel()
                                        coroutineScope.launch { nextInteractionSource.emit(PressInteraction.Release(press)) }
                                        if (!isPressingForward && !isGuest) {
                                            portraitHaptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                            handleSkipNext()
                                        }
                                        isPressingForward = false
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = com.musicplayer.lytrix.ui.icons.LytrixIcons.Rounded.SkipNext,
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            // ==========================================
            // 6. BOTTOM TOOLBAR (MetroList Expressive Connected ButtonGroup + Morphing More Circle)
            // ==========================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                com.musicplayer.lytrix.ui.component.MetroConnectedPlayerToolbar(
                    actions = listOf(
                        com.musicplayer.lytrix.ui.component.MetroToolbarAction(
                            outlinedIcon = com.musicplayer.lytrix.ui.icons.LytrixIcons.AutoMirrored.Rounded.QueueMusic,
                            contentDescription = "Up Next Queue",
                            checked = showQueue,
                            onClick = { showQueue = true }
                        ),
                        com.musicplayer.lytrix.ui.component.MetroToolbarAction(
                            outlinedIcon = com.musicplayer.lytrix.ui.icons.LytrixIcons.Outlined.Bedtime,
                            filledIcon = com.musicplayer.lytrix.ui.icons.LytrixIcons.Filled.Bedtime,
                            contentDescription = "Sleep Timer",
                            checked = sleepTimerState.isActive,
                            text = sleepTimerDisplay,
                            onClick = onOpenSleepTimer
                        ),
                        com.musicplayer.lytrix.ui.component.MetroToolbarAction(
                            outlinedIcon = com.musicplayer.lytrix.ui.icons.LytrixIcons.Rounded.Shuffle,
                            contentDescription = "Shuffle",
                            checked = shuffleEnabled,
                            onClick = onToggleShuffle
                        ),
                        com.musicplayer.lytrix.ui.component.MetroToolbarAction(
                            outlinedIcon = com.musicplayer.lytrix.ui.icons.LytrixIcons.Outlined.Equalizer,
                            contentDescription = "Equalizer",
                            checked = showEqualizer,
                            onClick = { showEqualizer = true }
                        ),
                        com.musicplayer.lytrix.ui.component.MetroToolbarAction(
                            outlinedIcon = if (repeatMode == 1) com.musicplayer.lytrix.ui.icons.LytrixIcons.Rounded.RepeatOne else com.musicplayer.lytrix.ui.icons.LytrixIcons.Rounded.Repeat,
                            contentDescription = "Repeat",
                            checked = repeatMode != 0,
                            onClick = onToggleRepeat
                        ),
                    ),
                    activeContainerColor = effectiveAccent,
                    activeContentColor = contentColorOnAccent,
                    inactiveContainerColor = Color.White.copy(alpha = 0.10f),
                    inactiveContentColor = Color.White,
                    buttonHeight = 42.dp,
                    buttonWidth = 44.dp,
                    iconSize = 20.dp
                )

                // 6. Right More Button (Material 3 Expressive Morphing IconButton)
                com.musicplayer.lytrix.ui.component.MetroMorphingIconButton(
                    onClick = onOpenMenu,
                    containerColor = effectiveAccent,
                    contentColor = contentColorOnAccent,
                    size = 42.dp
                ) {
                    Icon(
                        imageVector = com.musicplayer.lytrix.ui.icons.LytrixIcons.Default.MoreVert,
                        contentDescription = "More",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        }

        // Viewport 2: Below-the-fold content (Meet the Artist, More from Artist, Similar Tracks)
        if (isYtSong) {
            MeldNowPlayingBelowTheFold(
                viewModel = viewModel,
                song = song,
                effectiveAccent = effectiveAccent,
                contentColorOnAccent = contentColorOnAccent,
                onNavigateToArtist = { artistName ->
                    onClose()
                    onNavigateToArtist(artistName)
                }
            )
        }
    }
    }

    }

    if (showArtOptionsDialog && isLocalSong) {
        val prefs = context.getSharedPreferences("custom_album_arts", Context.MODE_PRIVATE)
        val hasCustomArt = prefs.contains(song.id.toString())

        Dialog(onDismissRequest = { showArtOptionsDialog = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Album Art Options",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(effectiveAccent.copy(alpha = 0.16f))
                            .clickable {
                                showArtOptionsDialog = false
                                albumArtPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                            .padding(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Image,
                            contentDescription = "Change",
                            tint = effectiveAccent
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Change Image",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = effectiveAccent
                        )
                    }

                    if (hasCustomArt) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.Red.copy(alpha = 0.12f))
                                .clickable {
                                    showArtOptionsDialog = false
                                    viewModel.removeCustomAlbumArt(context, song.id)
                                }
                                .padding(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = "Remove",
                                tint = Color.Red
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = "Remove Custom Image",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.Red
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    TextButton(
                        onClick = { showArtOptionsDialog = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(
                            text = "Cancel",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    val currentLyricsProviderName by viewModel.currentLyricsProviderName.collectAsState()
    AnimatedVisibility(
        visible = isLyricsExpanded,
        enter = fadeIn() + slideInVertically { it },
        exit = fadeOut() + slideOutVertically { it }
    ) {
        LyricsFullScreenOverlay(
            song = song,
            lyrics = currentLyrics,
            currentPosition = viewModel.getEffectiveLyricsPosition(song, currentPosition),
            isPlaying = isPlaying,
            onPlayPause = onPlayPause,
            onClose = { isLyricsExpanded = false },
            onSeek = { audioPos -> viewModel.seekFromLyrics(song, audioPos) },
            onChangeSource = { viewModel.changeLyricsSource(context) },
            onPrevious = onSkipPrevious,
            onNext = onSkipNext,
            durationMs = duration,
            accentColor = effectiveAccent,
            providerName = currentLyricsProviderName
        )
    }

    if (showQueue) {
        UpNextQueueSheet(
            viewModel = viewModel,
            accentColor = effectiveAccent,
            onDismiss = { showQueue = false }
        )
    }

    if (showEqualizer) {
        EqualizerBottomSheet(
            viewModel = viewModel,
            onDismiss = { showEqualizer = false }
        )
    }
}

/**
 * Helper to format milliseconds to mm:ss
 */
private fun formatMillisToTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}


/**
 * 🌟 Meld-Style & Spotify-Style Below-The-Fold Continuous Scroll Content
 * - Meet the Artist card (High-res photo, bio/subscribers, View Profile)
 * - More from [Artist Name] (Horizontal carousel)
 * - Similar to [Song Name] (Smart recommendations)
 * - Fluid Shimmer skeletons during lazy loading
 */
@Composable
fun MeldNowPlayingBelowTheFold(
    viewModel: PlayerViewModel,
    song: Song,
    effectiveAccent: Color,
    contentColorOnAccent: Color,
    onNavigateToArtist: (String) -> Unit
) {
    val context = LocalContext.current
    val rawRelatedData by viewModel.nowPlayingRelatedData.collectAsState()
    val relatedData = rawRelatedData?.takeIf { it.songKey.isBlank() || it.songKey == song.id.toString() }
    val isLoading by viewModel.isNowPlayingRelatedLoading.collectAsState()

    val isHeroVideoEnabled by remember {
        SpotifyPreferencesManager.getInstance(context).artistHeroVideoEnabled
    }.collectAsState(initial = false)

    val targetArtistName = relatedData?.artistName?.ifBlank { song.artist } ?: song.artist
    var artistMotionUrl by remember(targetArtistName) { mutableStateOf<String?>(null) }

    LaunchedEffect(targetArtistName, isHeroVideoEnabled) {
        if (isHeroVideoEnabled && targetArtistName.isNotBlank()) {
            artistMotionUrl = withContext(Dispatchers.IO) {
                AppleMusicAnimatedArtwork.getArtistMotionUrl(context, targetArtistName)
            }
        } else {
            artistMotionUrl = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        if (isLoading) {
            // ==========================================
            // 🌟 1. SHIMMER SKELETON LOADING STATE
            // ==========================================
            // Artist Card Shimmer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .shimmer()
            )

            Spacer(modifier = Modifier.height(28.dp))

            // More from Artist Shimmer
            Box(
                modifier = Modifier
                    .width(180.dp)
                    .height(20.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .shimmer()
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .width(135.dp)
                            .height(175.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .shimmer()
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Similar Tracks Shimmer
            Box(
                modifier = Modifier
                    .width(200.dp)
                    .height(20.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .shimmer()
            )
            Spacer(modifier = Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(3) {
                    LytrixSongItemShimmer()
                }
            }

            Spacer(modifier = Modifier.height(60.dp))
        } else if (relatedData != null) {
            val data = relatedData!!
            val rawArtistName = data.artistName.ifBlank { song.artist }
            val artistName = rawArtistName
            val isAb = song.mediaType == "AUDIOBOOK" || song.filePath in listOf("ONLINE_AUDIOBOOK", "OFFLINE_AUDIOBOOK")
            val isPod = song.mediaType == "PODCAST" || song.filePath in listOf("ONLINE_PODCAST", "OFFLINE_PODCAST")
            val targetArtist = when {
                isAb -> "AUDIOBOOK_$rawArtistName"
                isPod && (song.channelId.isNullOrBlank() || !song.channelId!!.startsWith("UC")) -> "PODCAST_${song.album.takeIf { !it.isNullOrBlank() } ?: rawArtistName}"
                !song.channelId.isNullOrBlank() -> song.channelId!!
                else -> rawArtistName
            }

            // ==========================================
            // 🌟 1. MEET THE ARTIST CARD (Spotify / SimpMusic Style)
            // ==========================================
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF161616)
                ),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.08f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToArtist(targetArtist) }
            ) {
                if (!artistMotionUrl.isNullOrBlank()) {
                    // 🎬 FULL-BLEED ARTIST VIDEO: Video fills entire card without separate black row
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                    ) {
                        ArtistMotionVideoPlayer(
                            videoUrl = artistMotionUrl!!,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Top-left About the artist pill
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(14.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black.copy(alpha = 0.60f))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "About the artist",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                ),
                                color = Color.White
                            )
                        }

                        // Bottom Scrim for text readability over video
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(95.dp)
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.80f))
                                    )
                                )
                        )

                        // Lower Card Surface: Artist details + View button overlaid directly on video
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 12.dp)
                            ) {
                                Text(
                                    text = artistName,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                val rawSub = data.artistSubscribers?.takeIf { it.isNotBlank() } ?: "Artist"
                                val subText = when {
                                    rawSub.equals("Artist", ignoreCase = true) -> "Artist"
                                    rawSub.contains("subscriber", ignoreCase = true) ||
                                    rawSub.contains("listener", ignoreCase = true) ||
                                    rawSub.contains("monthly", ignoreCase = true) -> rawSub
                                    else -> "$rawSub subscribers"
                                }
                                Text(
                                    text = subText,
                                    fontSize = 13.sp,
                                    color = Color.White.copy(alpha = 0.85f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // View Profile Pill
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color.Black.copy(alpha = 0.45f))
                                    .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                                    .clickable { onNavigateToArtist(targetArtist) }
                                    .padding(horizontal = 14.dp, vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "View",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // 🖼️ STATIC IMAGE: Keep 220dp photo box + lower dark row for maximum contrast
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Artwork with top tag and smooth bottom scrim
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                        ) {
                            val imageModel = data.artistThumbnail ?: song.albumArtUriString
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(imageModel)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = artistName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )

                            // Top Scrim for badge contrast
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(60.dp)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent)
                                        )
                                    )
                            )

                            // Top-left About the artist pill
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(14.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black.copy(alpha = 0.60f))
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = "About the artist",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    ),
                                    color = Color.White
                                )
                            }

                            // Bottom Scrim to blend with card surface
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(70.dp)
                                    .align(Alignment.BottomCenter)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color.Transparent, Color(0xFF161616))
                                        )
                                    )
                            )
                        }

                        // Lower Card Surface: Artist details + View button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 12.dp)
                            ) {
                                Text(
                                    text = artistName,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                val rawSub = data.artistSubscribers?.takeIf { it.isNotBlank() } ?: "Artist"
                                val subText = when {
                                    rawSub.equals("Artist", ignoreCase = true) -> "Artist"
                                    rawSub.contains("subscriber", ignoreCase = true) ||
                                    rawSub.contains("listener", ignoreCase = true) ||
                                    rawSub.contains("monthly", ignoreCase = true) -> rawSub
                                    else -> "$rawSub subscribers"
                                }
                                Text(
                                    text = subText,
                                    fontSize = 13.sp,
                                    color = Color.White.copy(alpha = 0.70f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // View Profile Pill
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(effectiveAccent.copy(alpha = 0.14f))
                                    .border(1.dp, effectiveAccent.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                                    .clickable { onNavigateToArtist(targetArtist) }
                                    .padding(horizontal = 14.dp, vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "View",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = effectiveAccent
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = effectiveAccent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ==========================================
            // 🌟 2. MORE FROM [ARTIST] (Horizontal Carousel)
            // ==========================================
            if (data.moreFromArtist.isNotEmpty()) {
                Spacer(modifier = Modifier.height(28.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "More from $artistName",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(data.moreFromArtist) { index, track ->
                        Column(
                            modifier = Modifier
                                .width(135.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    viewModel.playLytrixPlaylistWithAd(data.moreFromArtist, index, context)
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(135.dp)
                                    .clip(RoundedCornerShape(14.dp))
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(track.thumbnailUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = track.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = track.title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = track.artist,
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.65f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // ==========================================
            // 🌟 3. SIMILAR TRACKS (Smart Recommendations)
            // ==========================================
            if (data.similarTracks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    text = "Similar to ${song.title}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(14.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    data.similarTracks.take(12).forEachIndexed { index, track ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.04f))
                                .clickable {
                                    viewModel.playLytrixPlaylistWithAd(data.similarTracks, index, context)
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(track.thumbnailUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = track.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 14.dp)
                            ) {
                                Text(
                                    text = track.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = track.artist,
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.65f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Minimal Play Button
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.08f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (data.moreFromArtist.isEmpty() && data.similarTracks.isEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                RecommendationRetryButton(
                    accentColor = effectiveAccent,
                    onClick = { viewModel.loadNowPlayingRecommendations(song, forceRefresh = true) }
                )
            }

            Spacer(modifier = Modifier.height(60.dp))
        } else {
            val isAb = song.mediaType == "AUDIOBOOK" || song.filePath in listOf("ONLINE_AUDIOBOOK", "OFFLINE_AUDIOBOOK")
            val isPod = song.mediaType == "PODCAST" || song.filePath in listOf("ONLINE_PODCAST", "OFFLINE_PODCAST")
            val fallbackArtist = cleanArtistDisplayName(song.artist)
            val targetArtist = when {
                isAb -> "AUDIOBOOK_$fallbackArtist"
                isPod && (song.channelId.isNullOrBlank() || !song.channelId!!.startsWith("UC")) -> "PODCAST_${song.album.takeIf { !it.isNullOrBlank() } ?: fallbackArtist}"
                !song.channelId.isNullOrBlank() -> song.channelId!!
                else -> fallbackArtist
            }
            // 🌟 Fallback Card: Always render so viewport height is maintained!
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF161616)
                ),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.08f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToArtist(targetArtist) }
            ) {
                if (!artistMotionUrl.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                    ) {
                        ArtistMotionVideoPlayer(
                            videoUrl = artistMotionUrl!!,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(14.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black.copy(alpha = 0.60f))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "About the artist",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                ),
                                color = Color.White
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(95.dp)
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.80f))
                                    )
                                )
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 12.dp)
                            ) {
                                Text(
                                    text = song.artist,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "Artist",
                                    fontSize = 13.sp,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color.Black.copy(alpha = 0.45f))
                                    .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                                    .clickable { onNavigateToArtist(targetArtist) }
                                    .padding(horizontal = 14.dp, vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "View",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(song.albumArtUriString)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = song.artist,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(14.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black.copy(alpha = 0.60f))
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = "About the artist",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    ),
                                    color = Color.White
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(70.dp)
                                    .align(Alignment.BottomCenter)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color.Transparent, Color(0xFF161616))
                                        )
                                    )
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 12.dp)
                            ) {
                                Text(
                                    text = song.artist,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "Artist",
                                    fontSize = 13.sp,
                                    color = Color.White.copy(alpha = 0.70f)
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(effectiveAccent.copy(alpha = 0.14f))
                                    .border(1.dp, effectiveAccent.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                                    .clickable { onNavigateToArtist(targetArtist) }
                                    .padding(horizontal = 14.dp, vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "View",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = effectiveAccent
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = effectiveAccent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            RecommendationRetryButton(
                accentColor = effectiveAccent,
                onClick = { viewModel.loadNowPlayingRecommendations(song, forceRefresh = true) }
            )

            Spacer(modifier = Modifier.height(60.dp))
        }
    }
}

@Composable
private fun RecommendationRetryButton(
    accentColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = "Retry",
                tint = accentColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Tap to load recommendations",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }
    }
}
