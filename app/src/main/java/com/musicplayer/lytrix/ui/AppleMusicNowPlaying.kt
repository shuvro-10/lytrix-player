@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.musicplayer.lytrix.ui

import com.musicplayer.lytrix.ui.component.NowPlayingVideoOverlayControls
import com.musicplayer.lytrix.ui.player.NowPlayingLandscapeVideoFullScreen
import com.musicplayer.lytrix.ui.player.NowPlayingMediaStage

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.material3.ripple
import com.musicplayer.lytrix.ui.component.tactileSpringPress
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import com.musicplayer.lytrix.ui.component.LytrixFluidBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.musicplayer.lytrix.R
import com.musicplayer.lytrix.model.Song
import com.musicplayer.lytrix.model.Video
import com.musicplayer.lytrix.service.PlaybackService
import com.musicplayer.lytrix.spotify.SpotifyPreferencesManager
import com.musicplayer.lytrix.spotify.canvas.CanvasVideoPlayerManager
import com.musicplayer.lytrix.spotify.canvas.VisualMediaManager
import com.musicplayer.lytrix.spotify.canvas.VisualMediaResult
import com.musicplayer.lytrix.ui.component.PlayerCoverWithGestures
import compose.icons.TablerIcons
import compose.icons.tablericons.PlayerSkipBack
import compose.icons.tablericons.PlayerSkipForward
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private enum class AmOutputKind { SPEAKER, BLUETOOTH, WIRED, USB, OTHER }

private data class AmOutputDeviceEntry(
    val device: AudioDeviceInfo,
    val id: Int,
    val name: String,
    val kind: AmOutputKind,
    val isActive: Boolean
)

/**
 * Queries usable media audio output devices and determines which is currently routed.
 */
private fun queryAudioOutputs(audioManager: AudioManager, preferredDeviceId: Int?): List<AmOutputDeviceEntry> {
    val allDevices = try {
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
    } catch (_: Exception) {
        emptyArray()
    }

    fun kindOf(type: Int): AmOutputKind? = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> AmOutputKind.SPEAKER
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER,
        AudioDeviceInfo.TYPE_HEARING_AID -> AmOutputKind.BLUETOOTH
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_LINE_ANALOG,
        AudioDeviceInfo.TYPE_LINE_DIGITAL,
        AudioDeviceInfo.TYPE_AUX_LINE -> AmOutputKind.WIRED
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_ACCESSORY -> AmOutputKind.USB
        else -> null
    }

    fun nameOf(device: AudioDeviceInfo, kind: AmOutputKind): String = when (kind) {
        AmOutputKind.SPEAKER -> "Phone Speaker"
        AmOutputKind.BLUETOOTH -> device.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Bluetooth Earbuds"
        AmOutputKind.WIRED -> device.productName?.toString()?.takeIf { it.isNotBlank() && it != Build.MODEL } ?: "Wired Headphones"
        AmOutputKind.USB -> device.productName?.toString()?.takeIf { it.isNotBlank() && it != Build.MODEL } ?: "USB Audio"
        AmOutputKind.OTHER -> device.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Audio Output"
    }

    val usable = allDevices
        .mapNotNull { dev -> kindOf(dev.type)?.let { kind -> Triple(dev, kind, nameOf(dev, kind)) } }
        .distinctBy { (_, kind, name) -> "$kind|$name" }

    val activeId: Int? = run {
        if (preferredDeviceId != null && usable.any { it.first.id == preferredDeviceId }) {
            return@run preferredDeviceId
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
                val routed = audioManager.getAudioDevicesForAttributes(attrs)
                val match = routed.firstNotNullOfOrNull { r -> usable.firstOrNull { it.first.id == r.id }?.first?.id }
                if (match != null) return@run match
            } catch (_: Exception) {}
        }
        usable.maxByOrNull { (_, kind, _) ->
            when (kind) {
                AmOutputKind.BLUETOOTH -> 5
                AmOutputKind.WIRED -> 4
                AmOutputKind.USB -> 3
                AmOutputKind.OTHER -> 2
                AmOutputKind.SPEAKER -> 1
            }
        }?.first?.id
    }

    return usable
        .map { (dev, kind, name) ->
            AmOutputDeviceEntry(
                device = dev,
                id = dev.id,
                name = name,
                kind = kind,
                isActive = dev.id == activeId
            )
        }
        .sortedBy { if (it.kind == AmOutputKind.SPEAKER) 0 else 1 }
}

/**
 * 🍎 Apple Music Inspired Modern Now Playing Screen
 * - Full-bleed frosted blurred artwork backdrop spanning the full screen cleanly
 * - TextureView-backed Apple Music Motion artwork / Canvas seamlessly alpha-dissolving into background
 * - Swipe down from top to close via NestedScrollConnection matching MeldNowPlaying
 * - Compact icon-only Meld Audio / Video switcher & RESIZE_MODE_FIT video mode
 * - Clickable live Audio Output Switcher sheet (switch Phone Speaker / Bluetooth Earbuds live)
 * - Clean empty lyric strip when a song has no lyrics (no placeholder dots)
 * - Up Next Queue button in bottom-right, Equalizer icon in bottom-left
 */
@Composable
fun AppleMusicNowPlayingFullScreen(
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
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp

    val dataStoreManager = remember { DataStoreManager(context) }
    val playerGesturesEnabled by dataStoreManager.playerGesturesEnabledFlow.collectAsState(initial = true)
    val myRoomStatus by viewModel.myRoomStatus.collectAsState()
    val metroRoomRole by viewModel.metroManager.roomRole.collectAsState()
    val isGuest = myRoomStatus == "approved" || myRoomStatus == "pending" ||
            metroRoomRole == com.musicplayer.lytrix.listentogether.metro.MetroRoomRole.GUEST

    val isYtSong = remember(song.filePath, song.albumArtUriString, song.uriString) {
        com.musicplayer.lytrix.ui.player.isYouTubeSong(song)
    }

    val isVideoModeActive by viewModel.isVideoModeActive.collectAsState()
    val isVideoHandoffPending by viewModel.isVideoHandoffPending.collectAsState()
    val pointA by viewModel.pointA.collectAsState()
    val pointB by viewModel.pointB.collectAsState()
    val videoStageActive = isVideoModeActive
    var showLyricsOverlay by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showEqualizer by remember { mutableStateOf(false) }
    var showOutputSheet by remember { mutableStateOf(false) }

    // 🎵 Visual Media (MetroList Architecture: Apple Music motion, YouTube canvas, Spotify canvas)
    val artworkPrefs = remember { com.musicplayer.lytrix.artwork.ArtworkPreferencesManager.getInstance(context) }
    val isAppleMotionEnabled by artworkPrefs.animatedCoversEnabled.collectAsState()
    val isCanvasEnabled by artworkPrefs.canvasEnabled.collectAsState()
    val contentOrder by artworkPrefs.canvasContentOrder.collectAsState()
    val enabledSources by artworkPrefs.canvasEnabledSources.collectAsState()

    var visualMedia by remember(song.id) { mutableStateOf<VisualMediaResult?>(null) }
    LaunchedEffect(song.id, isAppleMotionEnabled, isCanvasEnabled, contentOrder, enabledSources) {
        visualMedia = null
        CanvasVideoPlayerManager.releaseIfDifferentSong(song.id)
        if (!isAppleMotionEnabled && !isCanvasEnabled) return@LaunchedEffect
        visualMedia = VisualMediaManager.getVisualMedia(context, song)
    }

    // 🎧 Live Audio Device Detection & Hot-Swap Output List
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var audioOutputs by remember { mutableStateOf<List<AmOutputDeviceEntry>>(emptyList()) }

    DisposableEffect(audioManager) {
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                audioOutputs = queryAudioOutputs(audioManager, PlaybackService.preferredAudioDevice?.id)
            }
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                val pickedId = PlaybackService.preferredAudioDevice?.id
                if (pickedId != null && removedDevices?.any { it.id == pickedId } == true) {
                    PlaybackService.selectPreferredAudioDevice(null)
                }
                audioOutputs = queryAudioOutputs(audioManager, PlaybackService.preferredAudioDevice?.id)
            }
        }
        audioManager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        audioOutputs = queryAudioOutputs(audioManager, PlaybackService.preferredAudioDevice?.id)
        onDispose {
            audioManager.unregisterAudioDeviceCallback(callback)
        }
    }

    val activeOutput = remember(audioOutputs) {
        audioOutputs.firstOrNull { it.isActive } ?: audioOutputs.firstOrNull()
    }
    val connectedDeviceName = activeOutput?.name ?: "Phone Speaker"

    // 🔊 Device Volume Control
    val maxVol = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var currentVol by remember { mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVol) }

    // 🔥 Centralized Online & Offline Like/Favorite Status (Single Source of Truth)
    val isAppOnlineSong = viewModel.isSongOnline(song)
    val onlineLikedIds by viewModel.onlineLikedIds.collectAsState()
    val displayIsFavorite = remember(song.id, song.originalId, song.filePath, song.uriString, onlineLikedIds, isFavorite, isAppOnlineSong) {
        if (isAppOnlineSong) {
            onlineLikedIds.contains(viewModel.onlineLikeKey(song))
        } else {
            isFavorite
        }
    }

    // 🎨 Dynamic Palette Extraction from Cover Art
    val artData: Any? = rememberEmbeddedArt(context, song)
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

    val primaryBgColor = extractedPalette.getOrNull(0) ?: Color(0xFF1E222B)
    val secondaryBgColor = extractedPalette.getOrNull(1) ?: Color(0xFF13151D)

    val effectiveAccent = remember(accentColor, artAccentColor) {
        var base = artAccentColor ?: (if (accentColor != Color.Unspecified && accentColor != Color.Transparent) accentColor else Color(0xFF2E6BFF))
        val lum = base.luminance()
        if (lum < 0.55f) {
            val blendFactor = ((0.55f - lum) / 0.55f).coerceIn(0.40f, 0.85f)
            base = lerp(base, Color.White, blendFactor)
        }
        base
    }
    val contentColorOnAccent = if (effectiveAccent.luminance() > 0.55f) Color.Black else Color.White

    // ⏩ Continuous Long-press Scrubbing (Fast Forward & Rewind)
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

    // 📜 Vertical Scroll Management & Pull-Down-to-Close (MeldNowPlaying exact behavior)
    val scrollState = rememberScrollState()
    var hasRequestedBelowTheFold by remember(song.id, song.originalId) { mutableStateOf(false) }
    var pullDownAccumulator by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(song.id) {
        if (scrollState.value != 0) {
            scrollState.scrollTo(0)
        }
    }

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
                    if (pullDownAccumulator > 280f) {
                        onClose()
                        pullDownAccumulator = 0f
                        return available
                    }
                } else if (scrollState.value > 0 || available.y < 0) {
                    pullDownAccumulator = 0f
                }
                return Offset.Zero
            }
        }
    }

    // 🔙 Back Handler Navigation Logic
    BackHandler(enabled = true) {
        if (showLyricsOverlay) {
            showLyricsOverlay = false
        } else if (showOutputSheet) {
            showOutputSheet = false
        } else if (showQueue) {
            showQueue = false
        } else if (showEqualizer) {
            showEqualizer = false
        } else if (isVideoModeActive) {
            viewModel.switchToAudioMode(song)
        } else if (scrollState.value > 0) {
            coroutineScope.launch { scrollState.animateScrollTo(0) }
        } else {
            onClose()
        }
    }

    val mainActivity = context as? com.musicplayer.lytrix.MainActivity
    DisposableEffect(showLyricsOverlay, showOutputSheet, showQueue, showEqualizer, isVideoModeActive) {
        if (mainActivity != null) {
            mainActivity.nowPlayingBackHandler = {
                if (showLyricsOverlay) {
                    showLyricsOverlay = false
                    true
                } else if (showOutputSheet) {
                    showOutputSheet = false
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

    // 🖼️ Image Requests
    val imageRequest = remember(artData, song.uriString, song.albumArtUriString) {
        val dataToLoad: Any = artData ?: (song.albumArtUriString ?: song.uriString)
        ImageRequest.Builder(context)
            .data(dataToLoad)
            .crossfade(true)
            .build()
    }

    val backdropRequest = remember(artData, song.uriString, song.albumArtUriString) {
        val dataToLoad: Any = artData ?: (song.albumArtUriString ?: song.uriString)
        ImageRequest.Builder(context)
            .data(dataToLoad)
            .apply {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    size(48)
                }
            }
            .crossfade(450)
            .build()
    }

    // 🏷️ Live Audio Quality Label
    val liveAudioQuality by viewModel.audioQualityLabel.collectAsState()
    val displayQualityText = remember(liveAudioQuality, song.filePath, song.uriString) {
        if (!liveAudioQuality.isNullOrBlank()) {
            liveAudioQuality!!
        } else {
            val path = song.filePath?.lowercase(Locale.ROOT) ?: ""
            when {
                path.endsWith(".flac") || path.endsWith(".wav") -> "Lossless · 48 kHz"
                path.endsWith(".opus") -> "160 kbps · 48 kHz"
                path.endsWith(".m4a") || path.endsWith(".aac") -> "256 kbps · 44.1 kHz"
                path.endsWith(".mp3") -> "320 kbps · 44.1 kHz"
                song.uriString.startsWith("http") -> "128 kbps · 48 kHz"
                else -> "HD Audio · 48 kHz"
            }
        }
    }

    // 🎤 Clickable Artist Name
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
                            fontSize = 16.sp
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
                                fontSize = 16.sp
                            )
                        ) {
                            append(", ")
                        }
                    }
                }
            }
        }
    }

    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val currentResizeMode by viewModel.videoResizeMode.collectAsState()
    val isLandscapeVideoFullscreen by viewModel.isLandscapeVideoFullscreen.collectAsState()
    val activityContext = context as? Activity
    DisposableEffect(Unit) {
        onDispose {
            if (activityContext?.requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
                activityContext?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    if (isLandscape && isVideoModeActive && isLandscapeVideoFullscreen) {
        NowPlayingLandscapeVideoFullScreen(
            viewModel = viewModel,
            song = song,
            artData = artData,
            isPlaying = isPlaying,
            isBuffering = isBuffering,
            currentPosition = currentPosition,
            duration = duration,
            accentColor = effectiveAccent,
            contentColorOnAccent = contentColorOnAccent,
            gesturesEnabled = playerGesturesEnabled,
            isGuest = isGuest,
            onPlayPause = onPlayPause,
            onSkipNext = onSkipNext,
            onSkipPrevious = onSkipPrevious,
            onSeek = onSeek
        )
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
            .background(Color(0xFF0A0B0E))
    ) {
        val totalViewportHeight = maxHeight
        val totalViewportWidth = maxWidth
        val statusBarHeight = with(density) { WindowInsets.statusBars.getTop(density).toDp() }

        // ==========================================
        // LAYER 0: CONTINUOUS FROSTED BLURRED ARTWORK BACKDROP
        // ==========================================
        AsyncImage(
            model = backdropRequest,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Modifier.blur(85.dp, BlurredEdgeTreatment.Unbounded)
                    } else {
                        Modifier
                    }
                )
        )

        // Atmospheric translucent wash over frosted art so typography & controls stay crisp without hard edges
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.00f to Color.Black.copy(alpha = 0.16f),
                        0.45f to primaryBgColor.copy(alpha = 0.42f),
                        0.75f to secondaryBgColor.copy(alpha = 0.56f),
                        1.00f to Color.Black.copy(alpha = 0.78f)
                    )
                )
        )

        // Shared sub-composables reused identically across Portrait & Landscape
        val collapseButton: @Composable (androidx.compose.ui.unit.Dp) -> Unit = { btnSize ->
            Box(
                modifier = Modifier
                    .size(btnSize)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f))
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "Collapse",
                    tint = Color.White,
                    modifier = Modifier.size(btnSize * 0.66f)
                )
            }
        }

        val modeSwitcherSlot: @Composable () -> Unit = {
            if (song.isVideo) {
                Box(
                    modifier = Modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(effectiveAccent.copy(alpha = 0.20f))
                        .border(1.dp, effectiveAccent.copy(alpha = 0.40f), RoundedCornerShape(20.dp))
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Videocam, contentDescription = null, tint = effectiveAccent, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Back to Video", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = effectiveAccent, modifier = Modifier.size(16.dp))
                    }
                }
            } else if (isYtSong) {
                val isAudioSelected = !videoStageActive
                val isVideoSelected = videoStageActive

                val audioBgColor by animateColorAsState(
                    targetValue = if (isAudioSelected) effectiveAccent else Color.Transparent,
                    animationSpec = tween(250),
                    label = "audioBg"
                )
                val audioIconTint by animateColorAsState(
                    targetValue = if (isAudioSelected) contentColorOnAccent else Color.White.copy(alpha = 0.70f),
                    animationSpec = tween(250),
                    label = "audioTint"
                )
                val videoBgColor by animateColorAsState(
                    targetValue = if (isVideoSelected) effectiveAccent else Color.Transparent,
                    animationSpec = tween(250),
                    label = "videoBg"
                )
                val videoIconTint by animateColorAsState(
                    targetValue = if (isVideoSelected) contentColorOnAccent else Color.White.copy(alpha = 0.70f),
                    animationSpec = tween(250),
                    label = "videoTint"
                )

                Box(
                    modifier = Modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .padding(3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .height(34.dp)
                                .width(48.dp)
                                .clip(RoundedCornerShape(17.dp))
                                .background(audioBgColor)
                                .clickable { viewModel.switchToAudioMode(song) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Headphones,
                                contentDescription = "Audio Mode",
                                tint = audioIconTint,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .height(34.dp)
                                .width(48.dp)
                                .clip(RoundedCornerShape(17.dp))
                                .background(videoBgColor)
                                .clickable {
                                    if (viewModel.notifyIfVideoUnavailable(song)) return@clickable
                                    if (viewModel.isInListenTogetherRoom() && !viewModel.isSong16by9(song)) {
                                        android.widget.Toast.makeText(context, "Video mode for album tracks is disabled in Listen Together", android.widget.Toast.LENGTH_SHORT).show()
                                        return@clickable
                                    }
                                    if (!videoStageActive && !isVideoHandoffPending) {
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
                                LytrixExpressiveSpinner(color = effectiveAccent, size = 18.dp)
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.SmartDisplay,
                                    contentDescription = "Video Mode",
                                    tint = videoIconTint,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = "Now Playing",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.90f)
                )
            }
        }

        val castButton: @Composable (androidx.compose.ui.unit.Dp) -> Unit = { btnSize ->
            Box(
                modifier = Modifier
                    .size(btnSize)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f))
                    .clickable {
                        android.widget.Toast.makeText(context, "No cast devices found", android.widget.Toast.LENGTH_SHORT).show()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Cast,
                    contentDescription = "Cast",
                    tint = Color.White,
                    modifier = Modifier.size(btnSize * 0.48f)
                )
            }
        }

        val stageBlock: @Composable (Modifier, Boolean) -> Unit = { stageModifier, roundedCardMode ->
            Box(modifier = stageModifier) {
if (videoStageActive) {
                        // YouTube Video Mode (Exact MeldNowPlaying.kt RESIZE_MODE_FIT Logic)
                        val isVideoPlayerReady by viewModel.isVideoPlayerReady.collectAsState()
                        val exoPlayer = viewModel.getVideoPlayer()
                        val baseSpeed by viewModel.currentPlaybackSpeed.collectAsState()

                        var videoAspect by remember(song.id) { mutableFloatStateOf(16f / 9f) }
                        DisposableEffect(exoPlayer) {
                            fun update(size: androidx.media3.common.VideoSize) {
                                if (size.width > 0 && size.height > 0) {
                                    videoAspect = (size.width * size.pixelWidthHeightRatio / size.height)
                                        .coerceIn(0.5f, 2.4f)
                                }
                            }
                            val listener = object : Player.Listener {
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
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = statusBarHeight + 56.dp, bottom = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val containerWidth = maxWidth
                            val idealHeight = containerWidth / videoAspect
                            val boxHeight = minOf(idealHeight, maxHeight)
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
                                    .requiredSize(containerWidth, boxHeight)
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
                                        LytrixExpressiveSpinner(color = effectiveAccent, size = 42.dp)
                                    }
                                } else {
                                    val is16by9 = viewModel.isSong16by9(song)
                                    if (is16by9) {
                                        LaunchedEffect(Unit) {
                                            exoPlayer.volume = 0f
                                            exoPlayer.seekTo(currentPosition)
                                            exoPlayer.playWhenReady = isPlaying
                                        }
                                        LaunchedEffect(isPlaying) {
                                            exoPlayer.volume = 0f
                                            exoPlayer.playWhenReady = isPlaying
                                        }
                                        LaunchedEffect(currentPosition) {
                                            if (exoPlayer.playbackState == Player.STATE_READY) {
                                                val activeSpeed = if (com.musicplayer.lytrix.service.PlaybackService.isSpeedBoostActive) 2.0f else baseSpeed
                                                val videoPos = exoPlayer.currentPosition
                                                val diff = videoPos - currentPosition
                                                val drift = kotlin.math.abs(diff)
                                                when {
                                                    drift > 1500 -> exoPlayer.seekTo(currentPosition)
                                                    diff < -150 -> {
                                                        val catchUpSpeed = (activeSpeed * 1.05f).coerceAtMost(2.15f)
                                                        if (exoPlayer.playbackParameters.speed != catchUpSpeed) {
                                                            exoPlayer.playbackParameters = androidx.media3.common.PlaybackParameters(catchUpSpeed)
                                                        }
                                                    }
                                                    diff > 150 -> {
                                                        val slowDownSpeed = (activeSpeed * 0.95f).coerceAtLeast(0.25f)
                                                        if (exoPlayer.playbackParameters.speed != slowDownSpeed) {
                                                            exoPlayer.playbackParameters = androidx.media3.common.PlaybackParameters(slowDownSpeed)
                                                        }
                                                    }
                                                    else -> if (exoPlayer.playbackParameters.speed != activeSpeed) {
                                                        exoPlayer.playbackParameters = androidx.media3.common.PlaybackParameters(activeSpeed)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    AndroidView(
                                        factory = { ctx ->
                                            (LayoutInflater.from(ctx).inflate(R.layout.apple_music_texture_player_view, null) as PlayerView).apply {
                                                resizeMode = currentResizeMode
                                                isClickable = false
                                                isFocusable = false
                                                setOnTouchListener { _, _ -> false }
                                                subtitleView?.visibility = android.view.View.GONE
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
                    } else {
                        // Artwork + TextureView Motion Video dissolved via Offscreen DstIn Alpha Mask (No hard horizontal seam!)
                                            PlayerCoverWithGestures(
                        gesturesEnabled = playerGesturesEnabled,
                        isGuest = isGuest,
                        isPlaying = isPlaying,
                        currentPosition = currentPosition,
                        duration = duration,
                        onSeek = onSeek,
                        onPlayPause = onPlayPause,
                        onSkipNext = onSkipNext,
                        onSkipPrevious = onSkipPrevious,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val contentModifier = if (roundedCardMode) {
                            // Immersive Landscape Stage: Edge-to-edge left 48% with smooth horizontal fade mask (matching CinemaNowPlaying)
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                                .drawWithContent {
                                    drawContent()
                                    drawRect(
                                        brush = Brush.horizontalGradient(
                                            0.00f to Color.Black,
                                            0.72f to Color.Black,
                                            0.88f to Color.Black.copy(alpha = 0.42f),
                                            1.00f to Color.Transparent
                                        ),
                                        blendMode = BlendMode.DstIn
                                    )
                                }
                        } else {
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                                .drawWithContent {
                                    drawContent()
                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            0.00f to Color.Black.copy(alpha = 0.85f),
                                            0.08f to Color.Black,
                                            0.58f to Color.Black,
                                            0.88f to Color.Black.copy(alpha = 0.25f),
                                            1.00f to Color.Transparent
                                        ),
                                        blendMode = BlendMode.DstIn
                                    )
                                }
                        }

                        Box(modifier = contentModifier) {
                            AsyncImage(
                                model = imageRequest,
                                contentDescription = song.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )

                            if (visualMedia != null) {
                                val exoPlayer = remember(visualMedia!!.url, song.id, visualMedia!!.clips) {
                                    CanvasVideoPlayerManager.getOrCreatePlayer(context, visualMedia!!.url, song.id, isPlaying, visualMedia!!.clips)
                                }
                                var isVideoReady by remember(visualMedia!!.url, song.id) {
                                    mutableStateOf(false)
                                }
                                DisposableEffect(exoPlayer) {
                                    val listener = object : Player.Listener {
                                        override fun onRenderedFirstFrame() {
                                            isVideoReady = true
                                        }
                                        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                                            isVideoReady = false
                                        }
                                    }
                                    exoPlayer.addListener(listener)
                                    onDispose { exoPlayer.removeListener(listener) }
                                }
                                LaunchedEffect(isPlaying) {
                                    exoPlayer.playWhenReady = isPlaying
                                }
                                val videoAlpha by animateFloatAsState(
                                    targetValue = if (isVideoReady) 1f else 0f,
                                    animationSpec = tween(350),
                                    label = "amVideoAlpha"
                                )
                                AndroidView(
                                    factory = { ctx ->
                                        (LayoutInflater.from(ctx).inflate(R.layout.apple_music_texture_player_view, null) as PlayerView).apply {
                                            player = exoPlayer
                                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                            layoutParams = android.view.ViewGroup.LayoutParams(
                                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                                android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                            )
                                        }
                                    },
                                    update = { pv -> if (pv.player != exoPlayer) pv.player = exoPlayer },
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .alpha(videoAlpha)
                                )
                            }
                        }
                    }
                }
            }
        }

        val trackInfoRow: @Composable (androidx.compose.ui.unit.TextUnit, Boolean) -> Unit = { titleSize, includeCast ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        fontSize = titleSize,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, animationMode = MarqueeAnimationMode.Immediately)
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    if (isAppOnlineSong) {
                        ClickableText(
                            text = annotatedArtistString,
                            onClick = { offset ->
                                val clickedArtist = annotatedArtistString
                                    .getStringAnnotations(tag = "ARTIST", start = offset, end = offset)
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
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Text(
                            text = annotatedArtistString,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                if (includeCast) {
                    castButton(36.dp)
                    Spacer(modifier = Modifier.width(2.dp))
                }

                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(if (includeCast) 38.dp else 42.dp)
                ) {
                    Icon(
                        imageVector = if (displayIsFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = "Favorite",
                        tint = if (displayIsFavorite) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(if (includeCast) 24.dp else 28.dp)
                    )
                }

                IconButton(
                    onClick = onOpenMenu,
                    modifier = Modifier.size(if (includeCast) 38.dp else 42.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "More",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(if (includeCast) 22.dp else 26.dp)
                    )
                }
            }
        }

        val sliderAndQualitySection: @Composable () -> Unit = {
            var sliderPosition by remember { mutableFloatStateOf(0f) }
            var isUserSeeking by remember { mutableStateOf(false) }
            val currentFrac = if (duration > 0) (currentPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f
            val activeFrac = if (isUserSeeking) sliderPosition else (if (draggingPosition != null && duration > 0) (draggingPosition!!.toFloat() / duration).coerceIn(0f, 1f) else currentFrac)

            com.musicplayer.lytrix.ui.component.ABRepeatSeekBarBox(
                pointA = pointA,
                pointB = pointB,
                durationMs = duration,
                markerColor = Color.White,
            ) {
                Slider(
                    value = activeFrac,
                    onValueChange = {
                        isUserSeeking = true
                        sliderPosition = it
                    },
                    onValueChangeFinished = {
                        isUserSeeking = false
                        val targetMs = (sliderPosition * duration).toLong()
                        onSeek(targetMs)
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White.copy(alpha = 0.90f),
                        inactiveTrackColor = Color.White.copy(alpha = 0.22f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                )
            }

            val posMs = draggingPosition ?: (if (isUserSeeking) (sliderPosition * duration).toLong() else currentPosition)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatMillisToTime(posMs),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.65f)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Headphones,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.60f),
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = displayQualityText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color.White.copy(alpha = 0.65f)
                    )
                }

                val remainingMs = (duration - posMs).coerceAtLeast(0L)
                Text(
                    text = "-${formatMillisToTime(remainingMs)}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.65f)
                )
            }

            com.musicplayer.lytrix.ui.component.ABRepeatControlPill(
                pointA = pointA,
                pointB = pointB,
                accentColor = Color.White,
                onSetPointA = { viewModel.setPointA() },
                onSetPointB = { viewModel.setPointB() },
                onClearAB = { viewModel.clearAB() },
            )
        }

        val playbackControlsRow: @Composable (androidx.compose.ui.unit.Dp) -> Unit = { playCircleSize ->
            val appleHaptic = androidx.compose.ui.platform.LocalHapticFeedback.current
            val prevInteraction = remember { MutableInteractionSource() }
            val playInteraction = remember { MutableInteractionSource() }
            val nextInteraction = remember { MutableInteractionSource() }
            val shuffleInteraction = remember { MutableInteractionSource() }
            val repeatInteraction = remember { MutableInteractionSource() }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (isLandscape) 4.dp else 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .tactileSpringPress(shuffleInteraction, 0.84f)
                        .clip(CircleShape)
                        .clickable(interactionSource = shuffleInteraction, indication = ripple(bounded = true, color = Color.White)) {
                            appleHaptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            onToggleShuffle()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val shuffleTint by animateColorAsState(
                        targetValue = if (shuffleEnabled) effectiveAccent else Color.White.copy(alpha = 0.45f),
                        animationSpec = tween(220),
                        label = "appleShuffleTint"
                    )
                    Icon(
                        imageVector = com.musicplayer.lytrix.ui.icons.LytrixIcons.Rounded.Shuffle,
                        contentDescription = "Shuffle",
                        tint = shuffleTint,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .tactileSpringPress(prevInteraction, 0.82f)
                        .clip(CircleShape)
                        .indication(prevInteraction, ripple(bounded = true, color = Color.White))
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = { offset ->
                                    val press = androidx.compose.foundation.interaction.PressInteraction.Press(offset)
                                    coroutineScope.launch { prevInteraction.emit(press) }
                                    val job = coroutineScope.launch { delay(400); isPressingRewind = true }
                                    try {
                                        tryAwaitRelease()
                                    } finally {
                                        job.cancel()
                                        coroutineScope.launch { prevInteraction.emit(androidx.compose.foundation.interaction.PressInteraction.Release(press)) }
                                        if (!isPressingRewind && !isGuest) {
                                            appleHaptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                            onSkipPrevious()
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
                        modifier = Modifier.size(34.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(playCircleSize)
                        .tactileSpringPress(playInteraction, 0.86f)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable(interactionSource = playInteraction, indication = ripple(bounded = true, color = Color.Black)) {
                            if (!isGuest) {
                                appleHaptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                onPlayPause()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isBuffering) {
                        LytrixExpressiveSpinner(color = Color.Black, size = playCircleSize * 0.42f)
                    } else {
                        com.musicplayer.lytrix.ui.component.MorphingPlayPauseIcon(
                            isPlaying = isPlaying,
                            playIcon = com.musicplayer.lytrix.ui.icons.LytrixIcons.Rounded.PlayArrow,
                            pauseIcon = com.musicplayer.lytrix.ui.icons.LytrixIcons.Rounded.Pause,
                            tint = Color.Black,
                            modifier = Modifier.size(playCircleSize * 0.56f)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .tactileSpringPress(nextInteraction, 0.82f)
                        .clip(CircleShape)
                        .indication(nextInteraction, ripple(bounded = true, color = Color.White))
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = { offset ->
                                    val press = androidx.compose.foundation.interaction.PressInteraction.Press(offset)
                                    coroutineScope.launch { nextInteraction.emit(press) }
                                    val job = coroutineScope.launch { delay(400); isPressingForward = true }
                                    try {
                                        tryAwaitRelease()
                                    } finally {
                                        job.cancel()
                                        coroutineScope.launch { nextInteraction.emit(androidx.compose.foundation.interaction.PressInteraction.Release(press)) }
                                        if (!isPressingForward && !isGuest) {
                                            appleHaptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                            onSkipNext()
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
                        modifier = Modifier.size(34.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .tactileSpringPress(repeatInteraction, 0.84f)
                        .clip(CircleShape)
                        .clickable(interactionSource = repeatInteraction, indication = ripple(bounded = true, color = Color.White)) {
                            appleHaptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            onToggleRepeat()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val repeatIcon = when (repeatMode) {
                        Player.REPEAT_MODE_ONE -> com.musicplayer.lytrix.ui.icons.LytrixIcons.Rounded.RepeatOne
                        else -> com.musicplayer.lytrix.ui.icons.LytrixIcons.Rounded.Repeat
                    }
                    val repeatTint by animateColorAsState(
                        targetValue = if (repeatMode == Player.REPEAT_MODE_OFF) Color.White.copy(alpha = 0.45f) else effectiveAccent,
                        animationSpec = tween(220),
                        label = "appleRepeatTint"
                    )
                    Icon(
                        imageVector = repeatIcon,
                        contentDescription = "Repeat",
                        tint = repeatTint,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        val volumeSliderRow: @Composable () -> Unit = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.VolumeDown,
                    contentDescription = "Vol Down",
                    tint = Color.White.copy(alpha = 0.50f),
                    modifier = Modifier.size(18.dp)
                )

                Slider(
                    value = currentVol,
                    onValueChange = { newVol ->
                        currentVol = newVol
                        val targetVol = (newVol * maxVol).toInt().coerceIn(0, maxVol)
                        try {
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                        } catch (_: Exception) {}
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White.copy(alpha = 0.75f),
                        inactiveTrackColor = Color.White.copy(alpha = 0.20f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(20.dp)
                        .padding(horizontal = 10.dp)
                )

                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                    contentDescription = "Vol Up",
                    tint = Color.White.copy(alpha = 0.50f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        val bottomActionDock: @Composable (androidx.compose.ui.unit.Dp) -> Unit = { pillHeight ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { showEqualizer = true },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Equalizer,
                        contentDescription = "Equalizer",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(22.dp)
                    )
                }

                val deviceIcon = when (activeOutput?.kind) {
                    AmOutputKind.BLUETOOTH, AmOutputKind.WIRED -> Icons.Rounded.Headphones
                    AmOutputKind.USB -> Icons.Rounded.Usb
                    AmOutputKind.SPEAKER -> Icons.Rounded.Smartphone
                    else -> Icons.Rounded.Speaker
                }
                Box(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(horizontal = 8.dp)
                        .height(pillHeight)
                        .clip(RoundedCornerShape(pillHeight / 2))
                        .background(Color.White.copy(alpha = 0.14f))
                        .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(pillHeight / 2))
                        .clickable {
                            audioOutputs = queryAudioOutputs(audioManager, PlaybackService.preferredAudioDevice?.id)
                            showOutputSheet = true
                        }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = deviceIcon,
                            contentDescription = "Select Audio Output",
                            tint = Color.White.copy(alpha = 0.90f),
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = connectedDeviceName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.90f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = { showQueue = true },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                        contentDescription = "Up Next Queue",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // ==========================================
        // LAYER 1: SCROLLABLE FOREGROUND PLAYER + BELOW THE FOLD
        // ==========================================
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            if (isLandscape) {
                // Immersive-style Apple Music Landscape: Edge-to-edge Left 48% with 16:9 Crisp Video + Atmospheric Fog OR Artwork with Horizontal Fade + Right 52% Controls
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(totalViewportHeight)
                        .clip(androidx.compose.ui.graphics.RectangleShape)
                ) {
                    val artWidth = totalViewportWidth * 0.48f

                    if (videoStageActive) {
                        // Left 48% Crisp 16:9 Video Box + Deep Atmospheric Cloud Mist underneath
                        Column(
                            modifier = Modifier
                                .size(width = artWidth, height = totalViewportHeight)
                                .align(Alignment.TopStart)
                                .systemBarsPadding()
                                .padding(start = 18.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Top Row: Collapse Button + Audio/Video Switcher
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                collapseButton(38.dp)
                                modeSwitcherSlot()
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // 16:9 Crisp Video Card (100% crisp without any fades/cuts)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(18.dp))
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
                                    accentColor = effectiveAccent,
                                    gesturesEnabled = playerGesturesEnabled,
                                    isGuest = isGuest,
                                    shape = RoundedCornerShape(18.dp),
                                    onSeek = onSeek,
                                    onPlayPause = onPlayPause,
                                    onSkipNext = onSkipNext,
                                    onSkipPrevious = onSkipPrevious,
                                    videoResizeMode = currentResizeMode,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            // Lower Stage Area: Atmospheric Cloud / Fog Gradient spreading across the bottom
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(top = 8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                            Brush.verticalGradient(
                                                0.0f to Color.Transparent,
                                                0.35f to effectiveAccent.copy(alpha = 0.14f),
                                                0.70f to secondaryBgColor.copy(alpha = 0.38f),
                                                1.0f to primaryBgColor.copy(alpha = 0.58f)
                                            )
                                        )
                                )
                            }
                        }
                    } else {
                        // Left 48% Edge-to-Edge Stage (Full Height + Smooth Horizontal Fade for Artwork)
                        Box(
                            modifier = Modifier
                                .size(width = artWidth, height = totalViewportHeight)
                                .align(Alignment.TopStart)
                        ) {
                            stageBlock(Modifier.fillMaxSize(), true)
                        }

                        // Top-left floating Collapse + Audio/Video Switcher over Left Stage
                        Row(
                            modifier = Modifier
                                .width(artWidth)
                                .align(Alignment.TopStart)
                                .systemBarsPadding()
                                .padding(start = 18.dp, end = 16.dp, top = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            collapseButton(38.dp)
                            modeSwitcherSlot()
                        }
                    }

                    // Right 52% Controls Column
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.52f)
                            .align(Alignment.CenterEnd)
                            .systemBarsPadding()
                            .padding(start = 12.dp, end = 24.dp, top = 10.dp, bottom = 10.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        trackInfoRow(20.sp, true)

                        NowPlayingLiveLyricStrip(
                            song = song,
                            currentLyrics = currentLyrics,
                            currentPosition = viewModel.getEffectiveLyricsPosition(song, currentPosition),
                            isPlaying = isPlaying,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Start,
                            contentAlignment = Alignment.CenterStart,
                            loadingPlaceholderText = "",
                            onClick = { showLyricsOverlay = true },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Column(modifier = Modifier.fillMaxWidth()) {
                            sliderAndQualitySection()
                        }

                        playbackControlsRow(52.dp)
                        volumeSliderRow()
                        bottomActionDock(34.dp)
                    }
                }
            } else {
                // VIEWPORT 1: FULL SCREEN PORTRAIT APPLE MUSIC PLAYER
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(totalViewportHeight)
                ) {
                    // 1. TOP HALF: ARTWORK / MOTION / VIDEO PLAYER
                    stageBlock(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.55f)
                            .align(Alignment.TopCenter),
                        false
                    )

                    // Subtle Top Scrim for Status Bar & Header Buttons legibility only
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.22f)
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    0f to Color.Black.copy(alpha = 0.45f),
                                    1f to Color.Transparent
                                )
                            )
                    )

                    // 2. TOP HEADER ROW (Collapse, Audio/Video Switcher, Cast)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = statusBarHeight + 8.dp, start = 20.dp, end = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        collapseButton(42.dp)
                        modeSwitcherSlot()
                        castButton(42.dp)
                    }

                    // 3. BOTTOM HALF: CONTROLS & DOCK
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 12.dp)
                    ) {
                        trackInfoRow(22.sp, false)

                        Spacer(modifier = Modifier.height(14.dp))

                        NowPlayingLiveLyricStrip(
                            song = song,
                            currentLyrics = currentLyrics,
                            currentPosition = viewModel.getEffectiveLyricsPosition(song, currentPosition),
                            isPlaying = isPlaying,
                            fontSize = 16.sp,
                            textAlign = TextAlign.Start,
                            contentAlignment = Alignment.CenterStart,
                            loadingPlaceholderText = "",
                            onClick = { showLyricsOverlay = true },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(4.dp))
                        sliderAndQualitySection()
                        Spacer(modifier = Modifier.height(16.dp))
                        playbackControlsRow(68.dp)
                        Spacer(modifier = Modifier.height(16.dp))
                        volumeSliderRow()
                        Spacer(modifier = Modifier.height(16.dp))
                        bottomActionDock(40.dp)
                    }
                }
            }

            // ==========================================
            // VIEWPORT 2: BELOW-THE-FOLD CONTENT
            // ==========================================
            if (isYtSong) {
                if (isLandscape) {
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
                } else {
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

    // ==========================================
    // AUDIO OUTPUT PICKER SHEET (Earbuds / Speaker / Wired)
    // ==========================================
    if (showOutputSheet) {
        AppleMusicAudioOutputSheet(
            outputs = audioOutputs,
            accentColor = effectiveAccent,
            onSelectOutput = { entry ->
                PlaybackService.selectPreferredAudioDevice(entry.device)
                audioOutputs = queryAudioOutputs(audioManager, entry.id)
                showOutputSheet = false
            },
            onDismiss = { showOutputSheet = false }
        )
    }

    // ==========================================
    // FULLSCREEN SYNCHRONIZED LYRICS OVERLAY
    // ==========================================
    val currentLyricsProviderName by viewModel.currentLyricsProviderName.collectAsState()
    AnimatedVisibility(
        visible = showLyricsOverlay,
        enter = fadeIn() + slideInVertically { it },
        exit = fadeOut() + slideOutVertically { it }
    ) {
        LyricsFullScreenOverlay(
            song = song,
            lyrics = currentLyrics,
            currentPosition = viewModel.getEffectiveLyricsPosition(song, currentPosition),
            isPlaying = isPlaying,
            onPlayPause = onPlayPause,
            onClose = { showLyricsOverlay = false },
            onSeek = { audioPos -> viewModel.seekFromLyrics(song, audioPos) },
            onChangeSource = { viewModel.changeLyricsSource(context) },
            onPrevious = onSkipPrevious,
            onNext = onSkipNext,
            durationMs = duration,
            accentColor = effectiveAccent,
            providerName = currentLyricsProviderName
        )
    }

    // ==========================================
    // UP NEXT QUEUE SHEET
    // ==========================================
    if (showQueue) {
        UpNextQueueSheet(
            viewModel = viewModel,
            accentColor = effectiveAccent,
            onDismiss = { showQueue = false }
        )
    }

    // ==========================================
    // EQUALIZER BOTTOM SHEET
    // ==========================================
    if (showEqualizer) {
        EqualizerBottomSheet(
            viewModel = viewModel,
            onDismiss = { showEqualizer = false }
        )
    }
}

@Composable
private fun AppleMusicAudioOutputSheet(
    outputs: List<AmOutputDeviceEntry>,
    accentColor: Color,
    onSelectOutput: (AmOutputDeviceEntry) -> Unit,
    onDismiss: () -> Unit
) {
    // Use LytrixFluidBottomSheet with allowContentDrag = false (same non-bouncy architecture as Equalizer/Sleep Timer)
    LytrixFluidBottomSheet(
        onDismissRequest = onDismiss,
        enableTwoStage = false,
        allowContentDrag = false,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = Color(0xFF181A20),
        contentColor = Color.White,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.25f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Audio Output",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Choose where playback audio should route",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.60f)
            )
            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            Spacer(modifier = Modifier.height(8.dp))

            outputs.forEach { entry ->
                val icon = when (entry.kind) {
                    AmOutputKind.BLUETOOTH, AmOutputKind.WIRED -> Icons.Rounded.Headphones
                    AmOutputKind.USB -> Icons.Rounded.Usb
                    AmOutputKind.SPEAKER -> Icons.Rounded.Smartphone
                    else -> Icons.Rounded.Speaker
                }
                val subtitle = when (entry.kind) {
                    AmOutputKind.BLUETOOTH -> "Bluetooth"
                    AmOutputKind.WIRED -> "Wired Audio"
                    AmOutputKind.USB -> "USB Device"
                    AmOutputKind.SPEAKER -> "This Device"
                    else -> "Output Device"
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (entry.isActive) accentColor.copy(alpha = 0.16f) else Color.Transparent)
                        .clickable { onSelectOutput(entry) }
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (entry.isActive) accentColor else Color.White.copy(alpha = 0.80f),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.name,
                            fontSize = 15.sp,
                            fontWeight = if (entry.isActive) FontWeight.Bold else FontWeight.Medium,
                            color = if (entry.isActive) Color.White else Color.White.copy(alpha = 0.88f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = subtitle,
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.55f)
                        )
                    }
                    if (entry.isActive) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Active",
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatMillisToTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}
