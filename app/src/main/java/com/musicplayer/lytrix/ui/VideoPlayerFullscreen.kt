@file:OptIn(androidx.media3.common.util.UnstableApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.musicplayer.lytrix.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.audiofx.Equalizer
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.musicplayer.lytrix.model.Video
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first

// 🔥 Tabler Icons ইম্পোর্ট করা হলো
import compose.icons.TablerIcons
import compose.icons.tablericons.*

@Composable
fun VideoPlayerFullscreen(
    video: Video,
    onClose: () -> Unit,
    onPlayAsAudio: (Video) -> Unit,
    videosViewModel: VideosViewModel = viewModel(),
    playerViewModel: PlayerViewModel
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current

    val appQueue by playerViewModel.currentQueueList.collectAsState()
    val baseVideosList by videosViewModel.videosList.collectAsState()

    val allVideos by remember(appQueue, baseVideosList, video.id) {
        derivedStateOf {
            if (video.id < 0 || !baseVideosList.any { it.id == video.id }) {
                appQueue.filter { it.isVideo }.map { s ->
                    com.musicplayer.lytrix.model.Video(
                        id = if (s.id < 0) s.id else -s.id,
                        title = s.title, durationText = "", sizeText = "",
                        uriString = s.uriString, path = s.filePath ?: "", dateModified = 0L
                    )
                }
            } else {
                baseVideosList
            }
        }
    }

    val currentAllVideos by rememberUpdatedState(allVideos)

    var currentPlayingVideo by remember { mutableStateOf(video) }
    val playModeState = remember { mutableStateOf(ExoPlayer.REPEAT_MODE_ALL) }

    var isInPiPMode by remember { mutableStateOf(false) }

    var subTextColor by remember { mutableIntStateOf(android.graphics.Color.WHITE) }
    var subBgColor by remember { mutableIntStateOf(android.graphics.Color.TRANSPARENT) }
    var subTextSize by remember { mutableFloatStateOf(0.0533f) }
    var showSubtitleStyleSheet by remember { mutableStateOf(false) }

    val videoPrefs = context.getSharedPreferences("video_progress", Context.MODE_PRIVATE)
    var showResumePrompt by remember { mutableStateOf(false) }

    var abPointA by remember { mutableStateOf<Long?>(null) }
    var abPointB by remember { mutableStateOf<Long?>(null) }
    var showABRepeatSheet by remember { mutableStateOf(false) }

    var showEqualizerSheet by remember { mutableStateOf(false) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = ExoPlayer.REPEAT_MODE_ALL
            playModeState.value = ExoPlayer.REPEAT_MODE_ALL

            addListener(object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    if (mediaItem != null) {
                        val foundVideo = currentAllVideos.find { it.id.toString() == mediaItem.mediaId }
                        if (foundVideo != null) {
                            currentPlayingVideo = foundVideo
                            abPointA = null
                            abPointB = null
                        }
                    }
                }
            })
        }
    }

    var equalizer by remember { mutableStateOf<Equalizer?>(null) }
    val isEqEnabled by playerViewModel.isEqEnabled.collectAsState()
    val bandValues by playerViewModel.eqBandValues.collectAsState()

    DisposableEffect(exoPlayer.audioSessionId) {
        if (exoPlayer.audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
            try { equalizer = Equalizer(0, exoPlayer.audioSessionId) } catch (e: Exception) { e.printStackTrace() }
        }
        onDispose { equalizer?.release() }
    }

    LaunchedEffect(isEqEnabled, bandValues, equalizer) {
        equalizer?.let { eq ->
            try {
                eq.enabled = isEqEnabled
                if (isEqEnabled) {
                    val maxLevel = eq.bandLevelRange[1]
                    for (i in 0 until eq.numberOfBands) {
                        if (i < bandValues.size) {
                            val realLevel = (bandValues[i] * (maxLevel / 15f)).toInt()
                            eq.setBandLevel(i.toShort(), realLevel.toShort())
                        }
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    var resizeModeState by remember { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val act = context as? Activity
            val isPip = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N && act?.isInPictureInPictureMode == true
            when (event) {
                Lifecycle.Event.ON_PAUSE -> { if (!isPip) exoPlayer.pause() else isInPiPMode = true }
                Lifecycle.Event.ON_RESUME -> { isInPiPMode = false; exoPlayer.play() }
                Lifecycle.Event.ON_DESTROY -> exoPlayer.release()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        val window = (context as? Activity)?.window
        window?.let { win ->
            WindowCompat.getInsetsController(win, win.decorView).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
            window?.let { win -> WindowCompat.getInsetsController(win, win.decorView).show(WindowInsetsCompat.Type.systemBars()) }
            val act = context as? Activity
            act?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    var isLocked by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showPlayModeSheet by remember { mutableStateOf(false) }
    var showAudioTrackSheet by remember { mutableStateOf(false) }
    var showSpeedSheet by remember { mutableStateOf(false) }
    var showTrashConfirmForVideo by remember { mutableStateOf(false) }
    var showSubtitleSheet by remember { mutableStateOf(false) }

    var isControlsVisible by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(exoPlayer.isPlaying) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }

    val accentColor = com.musicplayer.lytrix.ui.theme.LocalAccentColor.current

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(playbackState: Int) { duration = exoPlayer.duration.coerceAtLeast(0) }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    LaunchedEffect(Unit) {
        val playtimePrefs = context.getSharedPreferences("detailed_playtime", Context.MODE_PRIVATE)

        while (true) {
            currentPosition = exoPlayer.currentPosition
            duration = exoPlayer.duration.coerceAtLeast(0)

            // 🔥 ম্যাজিক: ফুলস্ক্রিন ভিডিও প্লেয়ারের রিয়েল-টাইম DataStore ট্র্যাকার
            if (isPlaying && currentPosition > 0) {
                try {
                    val currentVideoMs = try { playtimePrefs.getLong("video_ms", 0L) } catch(e: Exception) { 0L }
                    val currentWeeklyMs = try { playtimePrefs.getLong("weekly_ms", 0L) } catch(e: Exception) { 0L }

                    val todayDate = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault()).format(java.util.Date())

                    context.dataStore.edit { prefs ->
                        val lastTrackedDate = prefs[DataStoreManager.TASK_TRACKED_DATE] ?: ""
                        if (lastTrackedDate != todayDate) {
                            prefs[DataStoreManager.TASK_TRACKED_DATE] = todayDate
                            prefs[DataStoreManager.DAILY_TASK_AUDIO_MS] = 0L
                            prefs[DataStoreManager.DAILY_TASK_VIDEO_MS] = 0L
                        }

                        val currentDailyVideoMs = prefs[DataStoreManager.DAILY_TASK_VIDEO_MS] ?: 0L
                        prefs[DataStoreManager.DAILY_TASK_VIDEO_MS] = currentDailyVideoMs + 200L
                    }

                    // 🎬 ভিডিও প্লেয়ারের জন্য রিয়েল-টাইম সেভ (200ms)
                    playtimePrefs.edit()
                        .remove("video_ms").putLong("video_ms", currentVideoMs + 200L)
                        .remove("weekly_ms").putLong("weekly_ms", currentWeeklyMs + 200L)
                        .apply()
                } catch (e: Exception) {}
            }

            delay(200)
        }
    }

    LaunchedEffect(currentPosition, abPointA, abPointB) {
        if (isPlaying && abPointA != null && abPointB != null) {
            if (currentPosition >= abPointB!!) {
                exoPlayer.seekTo(abPointA!!)
            }
        }
    }

    LaunchedEffect(isPlaying) {
        while(true) {
            delay(5000)
            if (isPlaying && currentPosition > 5000L && currentPosition < duration - 1000L) {
                videoPrefs.edit().putLong("vid_${currentPlayingVideo.id}", currentPosition).apply()
            }
        }
    }

    LaunchedEffect(currentPlayingVideo.id) {
        val savedPos = videoPrefs.getLong("vid_${currentPlayingVideo.id}", 0L)
        if (savedPos > 5000L) {
            delay(300)
            exoPlayer.seekTo(savedPos)
            showResumePrompt = true
            delay(6000)
            showResumePrompt = false
        } else { showResumePrompt = false }
    }

    LaunchedEffect(Unit) {
        if (videosViewModel.videosList.value.isEmpty()) videosViewModel.loadVideos(context)
    }

    LaunchedEffect(currentAllVideos) {
        val list = currentAllVideos
        if (list.isEmpty()) {
            exoPlayer.setMediaItem(MediaItem.Builder().setUri(video.uriString).setMediaId(video.id.toString()).build())
            exoPlayer.prepare()
            exoPlayer.play()
        } else {
            val mediaItems = list.map { MediaItem.Builder().setUri(it.uriString).setMediaId(it.id.toString()).build() }.toMutableList()
            var startIndex = list.indexOfFirst { it.id == video.id }
            if (startIndex == -1) {
                mediaItems.add(0, MediaItem.Builder().setUri(video.uriString).setMediaId(video.id.toString()).build())
                startIndex = 0
            }
            val currentPos = exoPlayer.currentPosition
            exoPlayer.setMediaItems(mediaItems, startIndex, if (currentPos > 0) currentPos else C.TIME_UNSET)
            exoPlayer.prepare()
            exoPlayer.play()
        }
    }

    LaunchedEffect(isControlsVisible, isPlaying, isLocked) {
        if (isControlsVisible && isPlaying && !isLocked) {
            delay(4000)
            isControlsVisible = false
        }
    }

    val pickSubtitleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                exoPlayer.currentMediaItem?.let { originalMediaItem ->
                    val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(uri).setMimeType(MimeTypes.APPLICATION_SUBRIP).setLanguage("en").setLabel("Loaded Subtitle").build()
                    val newMediaItemBuilder = originalMediaItem.buildUpon().setSubtitleConfigurations(listOf(subtitleConfig)).build()
                    val pos = exoPlayer.currentPosition
                    exoPlayer.setMediaItem(newMediaItemBuilder, pos)
                }
            }
        }
    }

    BackHandler { onClose() }

    fun formatTimeStr(ms: Long): String {
        val totalSecs = ms / 1000
        val h = totalSecs / 3600
        val m = (totalSecs % 3600) / 60
        val s = totalSecs % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = resizeModeState
                    layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)

                    var initialY = 0f
                    subtitleView?.isClickable = true
                    subtitleView?.setOnTouchListener { v, event ->
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> { initialY = event.rawY - v.translationY; v.parent.requestDisallowInterceptTouchEvent(true); true }
                            MotionEvent.ACTION_MOVE -> { v.translationY = event.rawY - initialY; true }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { v.parent.requestDisallowInterceptTouchEvent(false); true }
                            else -> false
                        }
                    }
                }
            },
            update = { view ->
                view.player = exoPlayer
                view.resizeMode = resizeModeState
                view.subtitleView?.apply {
                    setApplyEmbeddedStyles(false)
                    setApplyEmbeddedFontSizes(false)
                    val style = CaptionStyleCompat(subTextColor, subBgColor, android.graphics.Color.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW, android.graphics.Color.BLACK, null)
                    setStyle(style)
                    setFractionalTextSize(subTextSize)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (!isInPiPMode) {
            var isFastForwarding by remember { mutableStateOf(false) }

            VideoGestureOverlay(
                exoPlayer = exoPlayer,
                modifier = Modifier.fillMaxSize(),
                isLocked = isLocked,
                onSingleTap = { isControlsVisible = !isControlsVisible },
                onDoubleTapLeft = { if (!isLocked) exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0)) },
                onDoubleTapRight = { if (!isLocked) exoPlayer.seekTo((exoPlayer.currentPosition + 10000).coerceAtMost(duration)) },
                onDoubleTapCenter = { if (!isLocked) { if (isPlaying) exoPlayer.pause() else exoPlayer.play() } },
                onLongPressStart = { if (!isLocked && isPlaying) { isFastForwarding = true; exoPlayer.playbackParameters = PlaybackParameters(2.0f) } },
                onLongPressEnd = { if (isFastForwarding) { isFastForwarding = false; exoPlayer.playbackParameters = PlaybackParameters(1.0f) } }
            )

            AnimatedVisibility(
                visible = isFastForwarding, enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 90.dp)
            ) {
                Box(modifier = Modifier.background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(text = "Playing at 2x speed", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (!isInPiPMode) {
            AnimatedVisibility(
                visible = isControlsVisible || isLocked, enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
            ) {
                Box(modifier = Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent))).padding(horizontal = 16.dp, vertical = 24.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().statusBarsPadding(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        if (!isLocked) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                IconButton(onClick = onClose) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Close", tint = Color.White) }
                                Text(text = currentPlayingVideo.title, color = Color.White, maxLines = 2, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 18.sp), modifier = Modifier.padding(horizontal = 8.dp))
                            }
                        } else { Spacer(modifier = Modifier.weight(1f)) }
                        IconButton(onClick = { isLocked = !isLocked }) { Icon(if (isLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen, contentDescription = "Lock", tint = Color.White, modifier = Modifier.size(28.dp)) }
                    }
                }
            }

            AnimatedVisibility(
                visible = isControlsVisible && !isLocked, enter = slideInVertically(initialOffsetY = { it }) + fadeIn(), exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.95f)))).padding(bottom = 32.dp, start = 24.dp, end = 24.dp, top = 64.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Text(formatTimeStr(currentPosition), color = accentColor, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("  |  ", color = Color.White.copy(alpha = 0.3f), fontWeight = FontWeight.Light, fontSize = 14.sp)
                        Text(formatTimeStr(duration), color = Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    }

                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            if (activity?.requestedOrientation == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) { activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
                            else { activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE }
                        }) { Icon(Icons.Rounded.ScreenRotation, contentDescription = "Rotate", tint = Color.White) }

                        IconButton(onClick = {
                            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                            exoPlayer.pause()
                            onPlayAsAudio(currentPlayingVideo)
                            onClose()
                        }) { Icon(Icons.Rounded.Headphones, contentDescription = "Play as audio", tint = Color.White) }

                        IconButton(onClick = { showSettingsSheet = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "Settings", tint = Color.White) }
                    }

                    var isDragging by remember { mutableStateOf(false) }
                    var sliderValue by remember { mutableFloatStateOf(0f) }
                    Slider(
                        value = if (isDragging) sliderValue else if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
                        onValueChange = { isDragging = true; sliderValue = it },
                        onValueChangeFinished = { isDragging = false; exoPlayer.seekTo((sliderValue * duration).toLong()) },
                        modifier = Modifier.fillMaxWidth().height(24.dp),
                        colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor, inactiveTrackColor = Color.White.copy(alpha = 0.2f))
                    )

                    Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            exoPlayer.seekToPreviousMediaItem()
                        }) { Icon(Icons.Rounded.SkipPrevious, contentDescription = "Prev", tint = Color.White, modifier = Modifier.size(32.dp)) }

                        IconButton(onClick = { exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0)) }) { Icon(Icons.Rounded.FastRewind, contentDescription = "Rewind", tint = Color.White, modifier = Modifier.size(32.dp)) }

                        IconButton(onClick = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() }, modifier = Modifier.size(64.dp).background(accentColor, CircleShape)) {
                            Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = "Play/Pause", tint = Color.Black, modifier = Modifier.size(40.dp))
                        }

                        IconButton(onClick = { exoPlayer.seekTo((exoPlayer.currentPosition + 10000).coerceAtMost(duration)) }) { Icon(Icons.Rounded.FastForward, contentDescription = "Fast Forward", tint = Color.White, modifier = Modifier.size(32.dp)) }

                        IconButton(onClick = {
                            exoPlayer.seekToNextMediaItem()
                        }) { Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(32.dp)) }
                    }
                }
            }

            AnimatedVisibility(
                visible = showResumePrompt && isControlsVisible && !isLocked, enter = slideInVertically(initialOffsetY = { it }) + fadeIn(), exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 140.dp, start = 24.dp, end = 24.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "Continue playing from where you stopped.", color = Color.White, fontSize = 13.sp, lineHeight = 16.sp, modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = "Start Over", color = accentColor, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, modifier = Modifier.clickable { exoPlayer.seekTo(0); showResumePrompt = false; videoPrefs.edit().putLong("vid_${currentPlayingVideo.id}", 0L).apply() })
                    Spacer(modifier = Modifier.width(12.dp))
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(20.dp).clickable { showResumePrompt = false })
                }
            }
        }
    }

    if (showSettingsSheet) {
        val sheetStateForFix = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showSettingsSheet = false }, sheetState = sheetStateForFix, contentWindowInsets = { WindowInsets(0, 0, 0, 0) }) {
            Column(modifier = Modifier.padding(bottom = 32.dp).verticalScroll(rememberScrollState())) {
                Text("Video Settings", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
                ListItem(headlineContent = { Text("Equalizer") }, leadingContent = { Icon(TablerIcons.Adjustments, contentDescription = null) }, modifier = Modifier.clickable { showEqualizerSheet = true; showSettingsSheet = false })
                ListItem(headlineContent = { Text("A-B Repeat") }, leadingContent = { Icon(TablerIcons.Repeat, contentDescription = null) }, trailingContent = { if(abPointA != null || abPointB != null) Text("Active", color=accentColor) }, modifier = Modifier.clickable { showABRepeatSheet = true; showSettingsSheet = false })
                val shareText = "Share"
                ListItem(headlineContent = { Text(shareText) }, leadingContent = { Icon(TablerIcons.Share, contentDescription = null) }, modifier = Modifier.clickable { val shareIntent = Intent(Intent.ACTION_SEND).apply { type = "video/*"; putExtra(Intent.EXTRA_STREAM, android.net.Uri.parse(currentPlayingVideo.uriString)) }; context.startActivity(Intent.createChooser(shareIntent, shareText)); showSettingsSheet = false })
                ListItem(headlineContent = { Text("Floating window") }, leadingContent = { Icon(TablerIcons.PictureInPicture, contentDescription = null) }, modifier = Modifier.clickable { showSettingsSheet = false; isControlsVisible = false; isInPiPMode = true; android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ val act = context as? Activity; if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) { act?.enterPictureInPictureMode(android.app.PictureInPictureParams.Builder().build()) } }, 600) })
                ListItem(headlineContent = { Text("Play mode") }, leadingContent = { Icon(TablerIcons.Repeat, contentDescription = null) }, modifier = Modifier.clickable { showPlayModeSheet = true; showSettingsSheet = false })
                ListItem(headlineContent = { Text("Scale adjust") }, leadingContent = { Icon(TablerIcons.AspectRatio, contentDescription = null) }, modifier = Modifier.clickable { resizeModeState = if (resizeModeState == AspectRatioFrameLayout.RESIZE_MODE_FIT) { AspectRatioFrameLayout.RESIZE_MODE_ZOOM } else if (resizeModeState == AspectRatioFrameLayout.RESIZE_MODE_ZOOM) { AspectRatioFrameLayout.RESIZE_MODE_FILL } else { AspectRatioFrameLayout.RESIZE_MODE_FIT }; showSettingsSheet = false })
                ListItem(headlineContent = { Text("Speed") }, leadingContent = { Icon(TablerIcons.Dashboard, contentDescription = null) }, modifier = Modifier.clickable { showSpeedSheet = true; showSettingsSheet = false })
                ListItem(headlineContent = { Text("Audio track") }, leadingContent = { Icon(TablerIcons.Music, contentDescription = null) }, modifier = Modifier.clickable { showAudioTrackSheet = true; showSettingsSheet = false })
                ListItem(headlineContent = { Text("Subtitle") }, leadingContent = { Icon(TablerIcons.Message, contentDescription = null) }, modifier = Modifier.clickable { showSubtitleSheet = true; showSettingsSheet = false })
                ListItem(headlineContent = { Text("Delete") }, leadingContent = { Icon(TablerIcons.Trash, contentDescription = null) }, modifier = Modifier.clickable { showSettingsSheet = false; showTrashConfirmForVideo = true })
            }
        }
    }

    if (showABRepeatSheet) {
        val sheetStateForFix = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showABRepeatSheet = false }, sheetState = sheetStateForFix, contentWindowInsets = { WindowInsets(0, 0, 0, 0) }) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                Text("A-B Repeat", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
                Text("Select points to loop a specific part of the video.", color = Color.Gray, modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp))

                ListItem(
                    headlineContent = { Text("Set Point A", fontWeight = FontWeight.SemiBold) },
                    leadingContent = { Icon(TablerIcons.MapPin, contentDescription = null, tint = if(abPointA != null) accentColor else Color.Gray) },
                    trailingContent = { if(abPointA != null) Text(formatTimeStr(abPointA!!), color = accentColor) },
                    modifier = Modifier.clickable {
                        abPointA = currentPosition
                        if (abPointB != null && currentPosition >= abPointB!!) abPointB = null
                        Toast.makeText(context, "Point A Set", Toast.LENGTH_SHORT).show()
                    }
                )

                ListItem(
                    headlineContent = { Text("Set Point B", fontWeight = FontWeight.SemiBold) },
                    leadingContent = { Icon(TablerIcons.MapPin, contentDescription = null, tint = if(abPointB != null) accentColor else Color.Gray) },
                    trailingContent = { if(abPointB != null) Text(formatTimeStr(abPointB!!), color = accentColor) },
                    modifier = Modifier.clickable {
                        if (abPointA != null) {
                            if (currentPosition > abPointA!!) {
                                abPointB = currentPosition
                                Toast.makeText(context, "Point B Set! Looping...", Toast.LENGTH_SHORT).show()
                            } else { Toast.makeText(context, "Point B must be after Point A", Toast.LENGTH_SHORT).show() }
                        } else { Toast.makeText(context, "Set Point A first", Toast.LENGTH_SHORT).show() }
                    }
                )

                if (abPointA != null || abPointB != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { abPointA = null; abPointB = null; Toast.makeText(context, "A-B Repeat Cleared", Toast.LENGTH_SHORT).show() },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f))
                    ) { Text("Clear A-B Repeat", color = Color.White) }
                }
            }
        }
    }

    if (showEqualizerSheet) {
        EqualizerBottomSheet(
            viewModel = playerViewModel,
            onDismiss = { showEqualizerSheet = false }
        )
    }

    if (showSubtitleSheet) {
        val sheetStateForFix = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showSubtitleSheet = false }, sheetState = sheetStateForFix, contentWindowInsets = { WindowInsets(0, 0, 0, 0) }) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                Text("Subtitle", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
                ListItem(headlineContent = { Text("Subtitle Style") }, leadingContent = { Icon(TablerIcons.Palette, contentDescription = null) }, modifier = Modifier.clickable { showSubtitleStyleSheet = true; showSubtitleSheet = false })
                ListItem(headlineContent = { Text("Load subtitle from storage") }, leadingContent = { Icon(TablerIcons.Folder, contentDescription = null) }, modifier = Modifier.clickable { val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "*/*"; addCategory(Intent.CATEGORY_OPENABLE) }; pickSubtitleLauncher.launch(intent); showSubtitleSheet = false })
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.White.copy(alpha = 0.1f))
                val isNoneSelected = exoPlayer.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
                ListItem(headlineContent = { Text("None", color = if (isNoneSelected) accentColor else Color.Unspecified) }, leadingContent = { Icon(TablerIcons.CircleOff, contentDescription = null, tint = if (isNoneSelected) accentColor else Color.Unspecified) }, modifier = Modifier.clickable { exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build(); showSubtitleSheet = false })
                val textGroups = exoPlayer.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
                if (textGroups.isNotEmpty()) {
                    textGroups.forEachIndexed { _, group ->
                        for (trackIndex in 0 until group.length) {
                            val format = group.getTrackFormat(trackIndex)
                            val langName = if (format.language != null) java.util.Locale(format.language!!).displayLanguage else null
                            val trackName = langName ?: format.label ?: "Subtitle ${trackIndex + 1}"
                            val isSelected = !isNoneSelected && group.isTrackSelected(trackIndex)
                            ListItem(headlineContent = { Text(trackName, color = if (isSelected) accentColor else Color.Unspecified) }, leadingContent = { Icon(TablerIcons.Message, contentDescription = null, tint = if (isSelected) accentColor else Color.Unspecified) }, modifier = Modifier.clickable { val trackSelectionOverride = TrackSelectionOverride(group.mediaTrackGroup, trackIndex); exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).setOverrideForType(trackSelectionOverride).build(); showSubtitleSheet = false })
                        }
                    }
                }
            }
        }
    }

    if (showSubtitleStyleSheet) {
        val sheetStateForFix = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showSubtitleStyleSheet = false }, sheetState = sheetStateForFix, contentWindowInsets = { WindowInsets(0, 0, 0, 0) }) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp).padding(bottom = 32.dp)) {
                Text("Subtitle Style", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(24.dp))
                Text("Text Color", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    listOf(android.graphics.Color.WHITE, android.graphics.Color.YELLOW, android.graphics.Color.GREEN, android.graphics.Color.CYAN, android.graphics.Color.parseColor("#FF5252"), android.graphics.Color.parseColor("#FF9800"), android.graphics.Color.parseColor("#E0E0E0")).forEach { colorInt ->
                        Box(modifier = Modifier.size(40.dp).background(Color(colorInt), CircleShape).clickable { subTextColor = colorInt }, contentAlignment = Alignment.Center) { if (subTextColor == colorInt) Icon(TablerIcons.Check, contentDescription = null, tint = if(colorInt == android.graphics.Color.WHITE || colorInt == android.graphics.Color.YELLOW) Color.Black else Color.White) }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text("Background Color", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    listOf(android.graphics.Color.TRANSPARENT, android.graphics.Color.parseColor("#80000000"), android.graphics.Color.BLACK, android.graphics.Color.parseColor("#800000FF"), android.graphics.Color.parseColor("#80FF0000")).forEach { colorInt ->
                        Box(modifier = Modifier.size(40.dp).background(Color(colorInt), CircleShape).clickable { subBgColor = colorInt }, contentAlignment = Alignment.Center) { if (colorInt == android.graphics.Color.TRANSPARENT) Icon(TablerIcons.Ban, contentDescription = null, tint = Color.White); if (subBgColor == colorInt) Icon(TablerIcons.Check, contentDescription = null, tint = Color.White) }
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
                Text("Text Size", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                Slider(value = subTextSize, onValueChange = { subTextSize = it }, valueRange = 0.02f..0.12f, colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        }
    }

    if (showPlayModeSheet) {
        val sheetStateForFix = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showPlayModeSheet = false }, sheetState = sheetStateForFix, contentWindowInsets = { WindowInsets(0, 0, 0, 0) }) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                Text("Play mode", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
                val currentMode = playModeState.value
                val isPauseAfterPlay = exoPlayer.pauseAtEndOfMediaItems
                ListItem(headlineContent = { Text("Loop one", color = if (!isPauseAfterPlay && currentMode == ExoPlayer.REPEAT_MODE_ONE) accentColor else Color.Unspecified) }, leadingContent = { Icon(TablerIcons.RepeatOnce, contentDescription = null, tint = if (!isPauseAfterPlay && currentMode == ExoPlayer.REPEAT_MODE_ONE) accentColor else Color.Unspecified) }, trailingContent = { if (!isPauseAfterPlay && currentMode == ExoPlayer.REPEAT_MODE_ONE) Icon(TablerIcons.Check, contentDescription = null, tint = accentColor) }, modifier = Modifier.clickable { exoPlayer.pauseAtEndOfMediaItems = false; exoPlayer.repeatMode = ExoPlayer.REPEAT_MODE_ONE; playModeState.value = ExoPlayer.REPEAT_MODE_ONE; showPlayModeSheet = false })
                ListItem(headlineContent = { Text("Loop all", color = if (!isPauseAfterPlay && currentMode == ExoPlayer.REPEAT_MODE_ALL) accentColor else Color.Unspecified) }, leadingContent = { Icon(TablerIcons.Repeat, contentDescription = null, tint = if (!isPauseAfterPlay && currentMode == ExoPlayer.REPEAT_MODE_ALL) accentColor else Color.Unspecified) }, trailingContent = { if (!isPauseAfterPlay && currentMode == ExoPlayer.REPEAT_MODE_ALL) Icon(TablerIcons.Check, contentDescription = null, tint = accentColor) }, modifier = Modifier.clickable { exoPlayer.pauseAtEndOfMediaItems = false; exoPlayer.repeatMode = ExoPlayer.REPEAT_MODE_ALL; playModeState.value = ExoPlayer.REPEAT_MODE_ALL; showPlayModeSheet = false })
                ListItem(headlineContent = { Text("Pause after play", color = if (isPauseAfterPlay) accentColor else Color.Unspecified) }, leadingContent = { Icon(TablerIcons.PlayerPause, contentDescription = null, tint = if (isPauseAfterPlay) accentColor else Color.Unspecified) }, trailingContent = { if (isPauseAfterPlay) Icon(TablerIcons.Check, contentDescription = null, tint = accentColor) }, modifier = Modifier.clickable { exoPlayer.pauseAtEndOfMediaItems = true; exoPlayer.repeatMode = ExoPlayer.REPEAT_MODE_OFF; playModeState.value = ExoPlayer.REPEAT_MODE_OFF; showPlayModeSheet = false })
            }
        }
    }

    if (showAudioTrackSheet) {
        val sheetStateForFix = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showAudioTrackSheet = false }, sheetState = sheetStateForFix, contentWindowInsets = { WindowInsets(0, 0, 0, 0) }) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                Text("Audio Tracks", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
                val audioGroups = exoPlayer.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
                if (audioGroups.isEmpty()) {
                    Text("No alternative audio tracks available.", modifier = Modifier.padding(16.dp), color = Color.Gray)
                } else {
                    audioGroups.forEachIndexed { _, group ->
                        for (trackIndex in 0 until group.length) {
                            val format = group.getTrackFormat(trackIndex)
                            val langName = if (format.language != null) java.util.Locale(format.language!!).displayLanguage else null
                            val trackName = langName ?: format.label ?: "Track ${trackIndex + 1}"
                            val isSelected = group.isTrackSelected(trackIndex)
                            ListItem(headlineContent = { Text(trackName, color = if (isSelected) accentColor else Color.Unspecified) }, leadingContent = { Icon(TablerIcons.Music, contentDescription = null, tint = if (isSelected) accentColor else Color.Unspecified) }, modifier = Modifier.clickable { val trackSelectionOverride = TrackSelectionOverride(group.mediaTrackGroup, trackIndex); exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon().setOverrideForType(trackSelectionOverride).build(); showAudioTrackSheet = false })
                        }
                    }
                }
            }
        }
    }

    if (showSpeedSheet) {
        val sheetStateForFix = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showSpeedSheet = false }, sheetState = sheetStateForFix, contentWindowInsets = { WindowInsets(0, 0, 0, 0) }) {
            SpeedSelectionSheet(onSetSpeed = { speed -> exoPlayer.playbackParameters = PlaybackParameters(speed); showSpeedSheet = false }, accentColor = accentColor, currentSpeed = exoPlayer.playbackParameters.speed)
        }
    }

    if (showTrashConfirmForVideo) {
        val uri = android.net.Uri.parse(currentPlayingVideo.uriString)
        PremiumTrashConfirmDialog(
            uris = listOf(uri),
            onConfirm = { exoPlayer.pause(); videosViewModel.deleteVideo(context, currentPlayingVideo); showTrashConfirmForVideo = false; onClose() },
            onDismiss = { showTrashConfirmForVideo = false }
        )
    }
}

enum class DragDirection { NONE, HORIZONTAL, VERTICAL_LEFT, VERTICAL_RIGHT }

@Composable
fun VideoGestureOverlay(
    exoPlayer: ExoPlayer,
    modifier: Modifier = Modifier,
    isLocked: Boolean,
    onSingleTap: () -> Unit,
    onDoubleTapLeft: () -> Unit,
    onDoubleTapRight: () -> Unit,
    onDoubleTapCenter: () -> Unit,
    onLongPressStart: () -> Unit = {},
    onLongPressEnd: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()

    val scope = rememberCoroutineScope()
    val accentColor = com.musicplayer.lytrix.ui.theme.LocalAccentColor.current

    var dragDirection by remember { mutableStateOf(DragDirection.NONE) }
    var accumulatedX by remember { mutableFloatStateOf(0f) }
    var accumulatedY by remember { mutableFloatStateOf(0f) }

    var initialVolume by remember { mutableFloatStateOf(0f) }
    var initialBrightness by remember { mutableFloatStateOf(0f) }
    var initialSeek by remember { mutableLongStateOf(0L) }

    var showVolumeUI by remember { mutableStateOf(false) }
    var showBrightnessUI by remember { mutableStateOf(false) }
    var showSeekUI by remember { mutableStateOf(false) }
    var displayPercentage by remember { mutableIntStateOf(0) }
    var seekTargetDisplay by remember { mutableLongStateOf(0L) }

    var showLeftAnimation by remember { mutableStateOf(false) }
    var leftTapTrigger by remember { mutableIntStateOf(0) }

    var showRightAnimation by remember { mutableStateOf(false) }
    var rightTapTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(leftTapTrigger) {
        if (leftTapTrigger > 0) {
            showLeftAnimation = true
            delay(600)
            showLeftAnimation = false
        }
    }

    LaunchedEffect(rightTapTrigger) {
        if (rightTapTrigger > 0) {
            showRightAnimation = true
            delay(600)
            showRightAnimation = false
        }
    }

    fun formatTime(ms: Long): String {
        val totalSecs = ms / 1000
        val h = totalSecs / 3600
        val m = (totalSecs % 3600) / 60
        val s = totalSecs % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { offset ->
                        var isLongPress = false
                        val job = scope.launch { kotlinx.coroutines.delay(500); isLongPress = true; onLongPressStart() }
                        tryAwaitRelease()
                        job.cancel()
                        if (isLongPress) { onLongPressEnd() }
                    },
                    onTap = { onSingleTap() },
                    onDoubleTap = { offset ->
                        val screenWidth = size.width
                        when {
                            offset.x < screenWidth * 0.3f -> {
                                leftTapTrigger++
                                onDoubleTapLeft()
                            }
                            offset.x > screenWidth * 0.7f -> {
                                rightTapTrigger++
                                onDoubleTapRight()
                            }
                            else -> onDoubleTapCenter()
                        }
                    }
                )
            }
            .pointerInput(isLocked) {
                if (!isLocked) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            dragDirection = DragDirection.NONE
                            accumulatedX = 0f
                            accumulatedY = 0f
                            initialVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                            initialBrightness = activity?.window?.attributes?.screenBrightness ?: 0.5f
                            if (initialBrightness < 0.01f) initialBrightness = 0.5f

                            initialSeek = exoPlayer.currentPosition.coerceAtLeast(0L)
                        },
                        onDragEnd = {
                            if (dragDirection == DragDirection.HORIZONTAL) {
                                exoPlayer.seekTo(seekTargetDisplay)
                            }
                            dragDirection = DragDirection.NONE
                            showVolumeUI = false
                            showBrightnessUI = false
                            showSeekUI = false
                        },
                        onDragCancel = {
                            dragDirection = DragDirection.NONE
                            showVolumeUI = false
                            showBrightnessUI = false
                            showSeekUI = false
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            accumulatedX += dragAmount.x
                            accumulatedY += dragAmount.y

                            if (dragDirection == DragDirection.NONE) {
                                if (abs(accumulatedX) > 40f) {
                                    dragDirection = DragDirection.HORIZONTAL
                                } else if (abs(accumulatedY) > 40f) {
                                    dragDirection = if (change.position.x < size.width / 2) DragDirection.VERTICAL_LEFT else DragDirection.VERTICAL_RIGHT
                                }
                            }

                            when (dragDirection) {
                                DragDirection.HORIZONTAL -> {
                                    showSeekUI = true
                                    val seekDelta = (accumulatedX * 100).toLong()
                                    val currentDuration = exoPlayer.duration.coerceAtLeast(0L)
                                    seekTargetDisplay = (initialSeek + seekDelta).coerceIn(0L, currentDuration)
                                }
                                DragDirection.VERTICAL_LEFT -> {
                                    showBrightnessUI = true
                                    val deltaRatio = -accumulatedY / size.height
                                    val newBrightness = (initialBrightness + deltaRatio).coerceIn(0.01f, 1.0f)
                                    activity?.let {
                                        val layoutParams = it.window.attributes
                                        layoutParams.screenBrightness = newBrightness
                                        it.window.attributes = layoutParams
                                    }
                                    displayPercentage = (newBrightness * 100).toInt()
                                }
                                DragDirection.VERTICAL_RIGHT -> {
                                    showVolumeUI = true
                                    val deltaRatio = -accumulatedY / size.height
                                    val newVolume = (initialVolume + (deltaRatio * maxVolume)).coerceIn(0f, maxVolume)
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume.roundToInt(), 0)
                                    displayPercentage = ((newVolume / maxVolume) * 100).toInt()
                                }
                                else -> {}
                            }
                        }
                    )
                }
            }
    ) {
        if (showVolumeUI || showBrightnessUI) {
            Box(
                modifier = Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp)).padding(horizontal = 24.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = if (showVolumeUI) Icons.Default.VolumeUp else Icons.Default.BrightnessMedium, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "$displayPercentage%", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (showSeekUI) {
            val seekDeltaSec = (seekTargetDisplay - initialSeek) / 1000
            val sign = if (seekDeltaSec > 0) "+" else if (seekDeltaSec < 0) "-" else ""
            val absDeltaStr = formatTime(abs(seekDeltaSec) * 1000)
            val currentDuration = exoPlayer.duration.coerceAtLeast(0L)

            Box(
                modifier = Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(16.dp)).padding(horizontal = 24.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "$sign$absDeltaStr", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "[ ${formatTime(seekTargetDisplay)}", color = accentColor, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(text = " / ${formatTime(currentDuration)} ]", color = Color.White.copy(alpha = 0.8f), fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = showLeftAnimation,
            enter = fadeIn(androidx.compose.animation.core.tween(200)),
            exit = fadeOut(androidx.compose.animation.core.tween(400)),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.35f)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color.White.copy(alpha = 0.15f), Color.Transparent)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = Icons.Rounded.FastRewind, contentDescription = null, tint = Color.White, modifier = Modifier.size(42.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "- 10 Seconds", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = showRightAnimation,
            enter = fadeIn(androidx.compose.animation.core.tween(200)),
            exit = fadeOut(androidx.compose.animation.core.tween(400)),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.35f)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.15f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = Icons.Rounded.FastForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(42.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "+ 10 Seconds", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}