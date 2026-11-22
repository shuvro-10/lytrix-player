package com.musicplayer.lytrix.ui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import coil.compose.AsyncImage
import com.musicplayer.lytrix.MainActivity
import com.musicplayer.lytrix.R
import kotlinx.coroutines.flow.MutableStateFlow

// 🔥 Media3 Imports
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.request.videoFrameMillis
import com.google.common.util.concurrent.ListenableFuture

// Lottie Imports
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition

object HoverState {
    val isHovering = MutableStateFlow(false)
    val isVisible = MutableStateFlow(false)
}

object WidgetMediaState {
    val title = MutableStateFlow<String>("No Song")
    val artist = MutableStateFlow<String>("Unknown Artist")
    val artworkUri = MutableStateFlow<String?>(null)
    val isPlaying = MutableStateFlow<Boolean>(false)
}

fun sendMediaCommandToService(context: Context, keyCode: Int) {
    try {
        val componentName = ComponentName(context, com.musicplayer.lytrix.service.PlaybackService::class.java)
        val downIntent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
            component = componentName
            putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        }
        val upIntent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
            component = componentName
            putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_UP, keyCode))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(downIntent)
            context.startForegroundService(upIntent)
        } else {
            context.startService(downIntent)
            context.startService(upIntent)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private class ComposeLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    init {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun destroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }
}

class FloatingWidgetService : Service() {

    private lateinit var windowManager: WindowManager
    private var composeView: ComposeView? = null
    private val composeLifecycleOwner = ComposeLifecycleOwner()

    private var deleteZoneView: ComposeView? = null
    private lateinit var deleteParams: WindowManager.LayoutParams

    private var mediaController: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        setupMediaController()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 200
        }

        deleteParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            300,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(101, createNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(101, createNotification())
        }

        deleteZoneView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(composeLifecycleOwner)
            setViewTreeSavedStateRegistryOwner(composeLifecycleOwner)
            setViewTreeViewModelStoreOwner(composeLifecycleOwner)
            setContent {
                val isHovering by HoverState.isHovering.collectAsState()
                val isVisible by HoverState.isVisible.collectAsState()

                val scale by animateFloatAsState(if (isHovering) 1.6f else 1f, label = "scale")
                val alpha by animateFloatAsState(if (isVisible) 1f else 0f, label = "alpha")

                Box(
                    modifier = Modifier.fillMaxSize().alpha(alpha),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .padding(bottom = 60.dp)
                            .size(60.dp)
                            .scale(scale)
                            .clip(CircleShape)
                            .background(if (isHovering) Color.Red else Color.Black.copy(alpha = 0.8f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                }
            }
        }

        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(composeLifecycleOwner)
            setViewTreeSavedStateRegistryOwner(composeLifecycleOwner)
            setViewTreeViewModelStoreOwner(composeLifecycleOwner)
            setContent {
                var offsetX by remember { mutableStateOf(params.x.toFloat()) }
                var offsetY by remember { mutableStateOf(params.y.toFloat()) }
                val context = LocalContext.current

                val viewModel = MainActivity.instance?.playerViewModel
                val vmSong by viewModel?.currentSong?.collectAsState() ?: remember { mutableStateOf(null) }
                val vmIsPlaying by viewModel?.isPlaying?.collectAsState() ?: remember { mutableStateOf(false) }

                val mediaTitle by WidgetMediaState.title.collectAsState()
                val mediaArtist by WidgetMediaState.artist.collectAsState()
                val mediaArtUri by WidgetMediaState.artworkUri.collectAsState()
                val mediaIsPlaying by WidgetMediaState.isPlaying.collectAsState()

                val finalTitle = if (mediaTitle != "No Song") mediaTitle else (vmSong?.title ?: "No Song")
                val finalArtist = if (mediaArtist != "Unknown Artist") mediaArtist else (vmSong?.artist ?: "Unknown Artist")
// 🔥 FIX: মেইন প্লেয়ারের মতো অরিজিনাল কভার আর্ট ফেচ করা হচ্ছে
                val artData = rememberEmbeddedArt(context, vmSong)
                val finalArtData = artData ?: mediaArtUri ?: vmSong?.albumArtUriString ?: vmSong?.uriString
                val finalIsPlaying = if (mediaController != null) mediaIsPlaying else vmIsPlaying

                FloatingPlayerUI(
                    title = finalTitle,
                    artist = finalArtist,
                    artworkData = finalArtData, // নাম চেঞ্জ করা হয়েছে
                    isVideo = vmSong?.isVideo == true, // ভিডিও কি না সেটা চেক করা হচ্ছে
                    isPlaying = finalIsPlaying,
                    onPlayPause = {
                        val controller = mediaController
                        if (controller != null && controller.isConnected) {
                            if (controller.isPlaying) controller.pause() else controller.play()
                        } else {
                            sendMediaCommandToService(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                            try { viewModel?.playPause() } catch (e: Exception) {}
                        }
                    },
                    onSkipNext = {
                        val controller = mediaController
                        if (controller != null && controller.isConnected) {
                            controller.seekToNext()
                        } else {
                            sendMediaCommandToService(context, KeyEvent.KEYCODE_MEDIA_NEXT)
                            try { viewModel?.skipNext() } catch (e: Exception) {}
                        }
                    },
                    onSkipPrevious = {
                        val controller = mediaController
                        if (controller != null && controller.isConnected) {
                            controller.seekToPrevious()
                        } else {
                            sendMediaCommandToService(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                            try { viewModel?.skipPrevious() } catch (e: Exception) {}
                        }
                    },
                    onDrag = { dx, dy ->
                        offsetX += dx
                        offsetY += dy
                        params.x = offsetX.toInt()
                        params.y = offsetY.toInt()
                        windowManager.updateViewLayout(this, params)

                        val metrics = context.resources.displayMetrics
                        val sHeight = metrics.heightPixels
                        val sWidth = metrics.widthPixels

                        val isNearBottom = offsetY > (sHeight * 0.65f)
                        val isNearCenter = offsetX > (sWidth * 0.05f) && offsetX < (sWidth * 0.95f)
                        val isHovering = offsetY > (sHeight * 0.75f) && isNearCenter

                        HoverState.isHovering.value = isHovering
                        HoverState.isVisible.value = isNearBottom || isHovering
                    },
                    onDragEnd = {
                        if (HoverState.isHovering.value) {
                            stopSelf()
                        }
                        HoverState.isVisible.value = false
                        HoverState.isHovering.value = false
                    }
                )
            }
        }

        windowManager.addView(deleteZoneView, deleteParams)
        windowManager.addView(composeView, params)
    }

    private fun setupMediaController() {
        try {
            val sessionToken = SessionToken(
                this,
                ComponentName(this, com.musicplayer.lytrix.service.PlaybackService::class.java)
            )
            controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
            controllerFuture?.addListener({
                try {
                    val controller = controllerFuture?.get()
                    mediaController = controller

                    controller?.addListener(object : Player.Listener {
                        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                            updateMediaState(controller)
                        }
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            WidgetMediaState.isPlaying.value = isPlaying
                        }
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            updateMediaState(controller)
                        }
                    })
                    if (controller != null) updateMediaState(controller)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(this))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateMediaState(controller: MediaController) {
        val item = controller.currentMediaItem
        val meta = item?.mediaMetadata

        if (meta != null) {
            val titleStr = meta.title?.toString()
            val artistStr = meta.artist?.toString()
            val artUri = meta.artworkUri?.toString()

            if (!titleStr.isNullOrBlank()) WidgetMediaState.title.value = titleStr
            if (!artistStr.isNullOrBlank()) WidgetMediaState.artist.value = artistStr
            if (artUri != null) {
                WidgetMediaState.artworkUri.value = artUri
            } else if (item?.localConfiguration?.uri != null) {
                WidgetMediaState.artworkUri.value = item.localConfiguration?.uri.toString()
            }
        }
        WidgetMediaState.isPlaying.value = controller.isPlaying
    }

    private fun createNotification(): Notification {
        val channelId = "floating_widget"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Floating Widget", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Lytrix Floating Player")
            .setContentText("Widget is active")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        composeLifecycleOwner.destroy()
        composeView?.let { windowManager.removeView(it) }
        deleteZoneView?.let { windowManager.removeView(it) }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

@Composable
fun FloatingPlayerUI(
    title: String,
    artist: String,
    artworkData: Any?,
    isVideo: Boolean,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit
) {
    val accentColor = Color(0xFF00E676)
    var isExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    var localIsPlaying by remember(isPlaying) { mutableStateOf(isPlaying) }

    val sharedPrefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
    var currentTheme by remember { mutableStateOf(sharedPrefs.getString("selected_theme", "Cartoon") ?: "Cartoon") }

    DisposableEffect(sharedPrefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == "selected_theme") {
                currentTheme = prefs.getString(key, "Cartoon") ?: "Cartoon"
            }
        }
        sharedPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { sharedPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val containerWidth by animateDpAsState(
        targetValue = if (isExpanded) 280.dp else 60.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow),
        label = "width"
    )

    val imageSize by animateDpAsState(
        targetValue = if (isExpanded) 40.dp else 52.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow),
        label = "imageSize"
    )

    val containerPadding by animateDpAsState(
        targetValue = if (isExpanded) 10.dp else 4.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow),
        label = "padding"
    )

    val lottieRes = when (currentTheme) {
        "Cartoon" -> R.raw.theme_cartoon
        else -> null
    }

    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(lottieRes ?: R.raw.theme_cartoon)
    )
    val progress by animateLottieCompositionAsState(
        composition,
        iterations = LottieConstants.IterateForever,
        isPlaying = localIsPlaying
    )

    Box(
        modifier = Modifier
            .width(containerWidth)
            .height(60.dp)
            .clip(RoundedCornerShape(30.dp))
            // 🔥 FIX 1: Neon Gradient থিমের কালার যেন ব্ল্যাক ব্যাকগ্রাউন্ডে ঢাকা না পড়ে
            .background(if (currentTheme != "None (Classic)") Color.Black else Color.Black.copy(alpha = 0.85f))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = { onDragEnd() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                )
            }
    ) {
        // 🔥 থিম অনুযায়ী ব্যাকগ্রাউন্ড ম্যাজিক (Neon Gradient Added Here)
        if (isExpanded) {
            when (currentTheme) {
                "Ocean Wave" -> WidgetOceanWaveBg(isPlaying = localIsPlaying)
                "Neon Gradient" -> WidgetNeonGradientBg(isPlaying = localIsPlaying)
                "Cartoon" -> {
                    if (lottieRes != null) {
                        LottieAnimation(
                            composition = composition,
                            progress = { progress },
                            modifier = Modifier.matchParentSize().alpha(0.6f),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(containerPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
// 🔥 FIX: ভিডিও হলে হুবহু মেইন প্লেয়ারের মতো 1000ms এর ফ্রেম কাটবে
            val imageRequest = remember(artworkData, isVideo) {
                val reqBuilder = coil.request.ImageRequest.Builder(context)
                    .data(artworkData)
                    .crossfade(true)
                if (isVideo && artworkData is String) {
                    reqBuilder.videoFrameMillis(1000)
                }
                reqBuilder.build()
            }

            AsyncImage(
                model = imageRequest,
                contentDescription = "Cover",
                modifier = Modifier
                    .size(imageSize)
                    .clip(CircleShape)
                    .background(Color.DarkGray)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        isExpanded = !isExpanded
                    },
                contentScale = ContentScale.Crop
            )

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(tween(400, delayMillis = 100)) + expandHorizontally(tween(400)),
                exit = fadeOut(tween(250)) + shrinkHorizontally(tween(250))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = artist,
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(onClick = onSkipPrevious, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Prev", tint = Color.White)
                    }

                    IconButton(onClick = {
                        localIsPlaying = !localIsPlaying
                        onPlayPause()
                    }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = if (localIsPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = accentColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    IconButton(onClick = onSkipNext, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White)
                    }
                }
            }
        }
    }
}

// 🔥 Ocean Wave & Neon Gradient Background Logics (Renamed to prevent conflict)
@Composable
fun WidgetOceanWaveBg(isPlaying: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI.toFloat()),
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    val currentPhase = if (isPlaying) phase else 0f

    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, height)
            for (x in 0..width.toInt() step 5) {
                val y = kotlin.math.sin((x / width * 2 * Math.PI) + currentPhase) * 20f + (height * 0.4f)
                lineTo(x.toFloat(), y.toFloat())
            }
            lineTo(width, height)
            close()
        }
        drawPath(path, Color(0xFF0083B0).copy(alpha = 0.5f))
    }
}

@Composable
fun WidgetNeonGradientBg(isPlaying: Boolean) {
    val transition = rememberInfiniteTransition(label = "neon")
    val offset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "offset"
    )
    val currentOffset = if (isPlaying) offset else 500f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF240046).copy(alpha = 0.8f),
                        Color(0xFFFF007F).copy(alpha = 0.4f),
                        Color(0xFF00F2FE).copy(alpha = 0.5f)
                    ),
                    start = androidx.compose.ui.geometry.Offset(0f, currentOffset),
                    end = androidx.compose.ui.geometry.Offset(currentOffset, 1000f)
                )
            )
    )
}