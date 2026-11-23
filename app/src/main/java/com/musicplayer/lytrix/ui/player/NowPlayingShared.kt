@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.musicplayer.lytrix.ui.player

import com.musicplayer.lytrix.ui.component.NowPlayingVideoOverlayControls

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.drawable.BitmapDrawable
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Brush
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.musicplayer.lytrix.ui.component.findActivity
import com.musicplayer.lytrix.ui.component.safeEnterPip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.musicplayer.lytrix.MainActivity
import com.musicplayer.lytrix.R
import com.musicplayer.lytrix.model.Song
import com.musicplayer.lytrix.artwork.ArtworkPreferencesManager
import com.musicplayer.lytrix.model.Video
import com.musicplayer.lytrix.service.PlaybackService
import com.musicplayer.lytrix.spotify.SpotifyPreferencesManager
import com.musicplayer.lytrix.spotify.canvas.CanvasVideoPlayerManager
import com.musicplayer.lytrix.spotify.canvas.VisualMediaManager
import com.musicplayer.lytrix.spotify.canvas.VisualMediaResult
import com.musicplayer.lytrix.ui.AdManager
import com.musicplayer.lytrix.ui.EqualizerBottomSheet
import com.musicplayer.lytrix.ui.LyricsFullScreenOverlay
import com.musicplayer.lytrix.ui.LytrixExpressiveSpinner
import com.musicplayer.lytrix.ui.PlayerViewModel
import com.musicplayer.lytrix.ui.UpNextQueueSheet
import com.musicplayer.lytrix.ui.cleanArtistDisplayName
import com.musicplayer.lytrix.ui.component.LoopingCoverVideoPlayer
import com.musicplayer.lytrix.ui.component.LytrixFluidBottomSheet
import com.musicplayer.lytrix.ui.component.PlayerCoverWithGestures
import com.musicplayer.lytrix.ui.rememberEmbeddedArt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs

data class ArtworkPaletteState(
    val primaryBg: Color,
    val secondaryBg: Color,
    val effectiveAccent: Color,
    val rawVibrant: Color,
    val contentOnAccent: Color,
)

@Composable
fun rememberArtworkPaletteState(
    song: Song,
    accentColor: Color,
    artData: Any?
): ArtworkPaletteState {
    val context = LocalContext.current
    var paletteColors by remember(song.id) { mutableStateOf<List<Color>>(emptyList()) }
    var extractedVibrant by remember(song.id) { mutableStateOf<Color?>(null) }

    LaunchedEffect(song.id, artData) {
        withContext(Dispatchers.IO) {
            try {
                val req = ImageRequest.Builder(context)
                    .data(artData ?: song.albumArtUriString ?: song.uriString)
                    .apply {
                        if ((artData == null || artData == song.uriString) && song.isVideo) {
                            videoFrameMillis(1000)
                        }
                    }
                    .allowHardware(false)
                    .build()
                val result = context.imageLoader.execute(req)
                val bitmap = (result.drawable as? BitmapDrawable)?.bitmap ?: return@withContext
                val palette = Palette.from(bitmap).maximumColorCount(16).generate()
                val bgSwatches = listOfNotNull(
                    palette.darkMutedSwatch,
                    palette.darkVibrantSwatch,
                    palette.dominantSwatch,
                    palette.mutedSwatch
                )
                if (bgSwatches.isNotEmpty()) {
                    paletteColors = bgSwatches.map { Color(it.rgb) }
                }
                val vibrant = palette.vibrantSwatch
                    ?: palette.lightVibrantSwatch
                    ?: palette.dominantSwatch
                    ?: palette.mutedSwatch
                if (vibrant != null) {
                    extractedVibrant = Color(vibrant.rgb)
                }
            } catch (_: Exception) {
            }
        }
    }

    val rawVibrant = extractedVibrant
        ?: accentColor.takeIf { it != Color.Unspecified && it != Color.Transparent }
        ?: Color(0xFF4C8DFF)

    val effectiveAccent = remember(rawVibrant) {
        val lum = rawVibrant.luminance()
        if (lum < 0.48f) {
            lerp(rawVibrant, Color.White, ((0.48f - lum) / 0.48f).coerceIn(0.32f, 0.78f))
        } else {
            rawVibrant
        }
    }

    return ArtworkPaletteState(
        primaryBg = paletteColors.getOrNull(0) ?: Color(0xFF181B22),
        secondaryBg = paletteColors.getOrNull(1) ?: Color(0xFF0E1015),
        effectiveAccent = effectiveAccent,
        rawVibrant = rawVibrant,
        contentOnAccent = if (effectiveAccent.luminance() > 0.55f) Color.Black else Color.White,
    )
}

enum class PlayerOutputKind { SPEAKER, BLUETOOTH, WIRED, USB, OTHER }

data class PlayerAudioOutputEntry(
    val device: AudioDeviceInfo,
    val id: Int,
    val name: String,
    val kind: PlayerOutputKind,
    val isActive: Boolean
)

@Composable
fun rememberAudioOutputEntries(): Pair<List<PlayerAudioOutputEntry>, (PlayerAudioOutputEntry) -> Unit> {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var outputs by remember { mutableStateOf<List<PlayerAudioOutputEntry>>(emptyList()) }

    fun refresh() {
        outputs = queryPlayerAudioOutputs(audioManager, PlaybackService.preferredAudioDevice?.id)
    }

    DisposableEffect(audioManager) {
        val cb = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = refresh()
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                val picked = PlaybackService.preferredAudioDevice?.id
                if (picked != null && removedDevices?.any { it.id == picked } == true) {
                    PlaybackService.selectPreferredAudioDevice(null)
                }
                refresh()
            }
        }
        audioManager.registerAudioDeviceCallback(cb, Handler(Looper.getMainLooper()))
        refresh()
        onDispose { audioManager.unregisterAudioDeviceCallback(cb) }
    }

    val select: (PlayerAudioOutputEntry) -> Unit = { entry ->
        PlaybackService.selectPreferredAudioDevice(entry.device)
        outputs = queryPlayerAudioOutputs(audioManager, entry.id)
    }
    return outputs to select
}

private fun queryPlayerAudioOutputs(audioManager: AudioManager, preferredId: Int?): List<PlayerAudioOutputEntry> {
    val devices = try {
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
    } catch (_: Exception) {
        emptyArray()
    }

    fun kindOf(type: Int): PlayerOutputKind? = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> PlayerOutputKind.SPEAKER
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER,
        AudioDeviceInfo.TYPE_HEARING_AID -> PlayerOutputKind.BLUETOOTH
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_LINE_ANALOG,
        AudioDeviceInfo.TYPE_LINE_DIGITAL,
        AudioDeviceInfo.TYPE_AUX_LINE -> PlayerOutputKind.WIRED
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_ACCESSORY -> PlayerOutputKind.USB
        else -> null
    }

    fun nameOf(dev: AudioDeviceInfo, kind: PlayerOutputKind): String = when (kind) {
        PlayerOutputKind.SPEAKER -> "Phone Speaker"
        PlayerOutputKind.BLUETOOTH -> dev.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Bluetooth Audio"
        PlayerOutputKind.WIRED -> dev.productName?.toString()?.takeIf { it.isNotBlank() && it != Build.MODEL } ?: "Wired Headphones"
        PlayerOutputKind.USB -> dev.productName?.toString()?.takeIf { it.isNotBlank() && it != Build.MODEL } ?: "USB Audio"
        PlayerOutputKind.OTHER -> dev.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Audio Output"
    }

    val usable = devices
        .mapNotNull { d -> kindOf(d.type)?.let { k -> Triple(d, k, nameOf(d, k)) } }
        .distinctBy { (_, k, n) -> "$k|$n" }

    val activeId = run {
        if (preferredId != null && usable.any { it.first.id == preferredId }) return@run preferredId
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
                val routed = audioManager.getAudioDevicesForAttributes(attrs)
                val match = routed.firstNotNullOfOrNull { r -> usable.firstOrNull { it.first.id == r.id }?.first?.id }
                if (match != null) return@run match
            } catch (_: Exception) {
            }
        }
        usable.maxByOrNull { (_, k, _) ->
            when (k) {
                PlayerOutputKind.USB -> 4
                PlayerOutputKind.BLUETOOTH -> 3
                PlayerOutputKind.WIRED -> 2
                PlayerOutputKind.OTHER -> 1
                PlayerOutputKind.SPEAKER -> 0
            }
        }?.first?.id
    }

    return usable.map { (d, k, n) ->
        PlayerAudioOutputEntry(d, d.id, n, k, d.id == activeId)
    }
}

@Composable
fun LytrixAudioOutputSheet(
    outputs: List<PlayerAudioOutputEntry>,
    accentColor: Color,
    onSelectOutput: (PlayerAudioOutputEntry) -> Unit,
    onDismiss: () -> Unit
) {
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
                color = Color.White.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            Spacer(modifier = Modifier.height(8.dp))

            outputs.forEach { entry ->
                val icon = when (entry.kind) {
                    PlayerOutputKind.BLUETOOTH, PlayerOutputKind.WIRED -> Icons.Rounded.Headphones
                    PlayerOutputKind.USB -> Icons.Rounded.Usb
                    PlayerOutputKind.SPEAKER -> Icons.Rounded.Smartphone
                    else -> Icons.Rounded.Speaker
                }
                val subtitle = when (entry.kind) {
                    PlayerOutputKind.BLUETOOTH -> "Bluetooth"
                    PlayerOutputKind.WIRED -> "Wired Audio"
                    PlayerOutputKind.USB -> "USB Device"
                    PlayerOutputKind.SPEAKER -> "This Device"
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
                        tint = if (entry.isActive) accentColor else Color.White.copy(alpha = 0.8f),
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
                        Text(subtitle, fontSize = 12.sp, color = Color.White.copy(alpha = 0.55f))
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

@Composable
fun rememberVisualMediaForSong(song: Song): VisualMediaResult? {
    val context = LocalContext.current
    val artworkPrefs = remember { ArtworkPreferencesManager.getInstance(context) }
    val isAppleMotion by artworkPrefs.animatedCoversEnabled.collectAsState()
    val isCanvas by artworkPrefs.canvasEnabled.collectAsState()
    val contentOrder by artworkPrefs.canvasContentOrder.collectAsState()
    val enabledSources by artworkPrefs.canvasEnabledSources.collectAsState()
    val youtubeCanvasQuality by artworkPrefs.youtubeCanvasQuality.collectAsState()
    val youtubeCanvasMode by artworkPrefs.youtubeCanvasMode.collectAsState()
    val animatedCoverResolution by artworkPrefs.animatedCoverResolution.collectAsState()
    var visualMedia by remember(song.id) { mutableStateOf<VisualMediaResult?>(null) }

    LaunchedEffect(
        song.id, isAppleMotion, isCanvas, contentOrder, enabledSources,
        youtubeCanvasQuality, youtubeCanvasMode, animatedCoverResolution
    ) {
        visualMedia = null
        CanvasVideoPlayerManager.releaseIfDifferentSong(song.id)
        if (!isAppleMotion && !isCanvas) return@LaunchedEffect
        visualMedia = VisualMediaManager.getVisualMedia(context, song)
    }
    return visualMedia
}

@Composable
fun rememberPlayerAudioQualityLabel(viewModel: PlayerViewModel, song: Song): String {
    val liveLabel by viewModel.audioQualityLabel.collectAsState()
    return remember(liveLabel, song.filePath, song.uriString, song.originalId, song.albumArtUriString) {
        val isJioSaavn = song.albumArtUriString?.contains("saavn", ignoreCase = true) == true ||
            song.uriString.contains("saavn", ignoreCase = true) ||
            song.album.startsWith("saavn", ignoreCase = true) ||
            song.originalId?.startsWith("saavn:", ignoreCase = true) == true
        val isPodcast = song.filePath in listOf("ONLINE_PODCAST", "OFFLINE_PODCAST")
        val isRadio = song.filePath == "ONLINE_RADIO"

        // Prefer real decoded ExoPlayer track label for JioSaavn / local / podcasts,
        // and cached YouTube stream format for YouTube tracks.
        val preferred = if (isJioSaavn) {
            liveLabel?.takeIf { it.isNotBlank() } ?: PlaybackService.formatBadgeForSong(song)
        } else {
            PlaybackService.formatBadgeForSong(song) ?: liveLabel?.takeIf { it.isNotBlank() }
        }
        preferred ?: run {
            val path = song.filePath?.lowercase(Locale.ROOT).orEmpty()
            when {
                isJioSaavn -> "320 kbps · 44.1 kHz"
                isPodcast -> "Podcast · 44.1 kHz"
                isRadio -> "Live Radio"
                path.endsWith(".flac") || path.endsWith(".wav") -> "Lossless · 48 kHz"
                path.endsWith(".opus") -> "160 kbps · 48 kHz"
                path.endsWith(".m4a") || path.endsWith(".aac") -> "256 kbps · 44.1 kHz"
                path.endsWith(".mp3") -> "320 kbps · 44.1 kHz"
                song.uriString.startsWith("http") -> "128 kbps · 48 kHz"
                else -> "HD Audio · 44.1 kHz"
            }
        }
    }
}

@Composable
fun rememberResolvedIsFavorite(viewModel: PlayerViewModel, song: Song, localFavorite: Boolean): Boolean {
    val isOnline = viewModel.isSongOnline(song)
    val onlineLikedIds by viewModel.onlineLikedIds.collectAsState()
    return remember(song.id, song.originalId, song.filePath, song.uriString, onlineLikedIds, localFavorite, isOnline) {
        if (isOnline) onlineLikedIds.contains(viewModel.onlineLikeKey(song)) else localFavorite
    }
}

@Composable
fun rememberIsListenTogetherGuest(viewModel: PlayerViewModel): Boolean {
    val myRoomStatus by viewModel.myRoomStatus.collectAsState()
    val metroRoomRole by viewModel.metroManager.roomRole.collectAsState()
    return myRoomStatus == "approved" || myRoomStatus == "pending" ||
        metroRoomRole == com.musicplayer.lytrix.listentogether.metro.MetroRoomRole.GUEST
}

fun isYouTubeSong(song: Song): Boolean {
    val isSaavn = (song.albumArtUriString?.contains("saavn", ignoreCase = true) == true) ||
        song.uriString.contains("saavncdn.com", ignoreCase = true)
    if (isSaavn) return false
    if (song.filePath in listOf("ONLINE_LYTRIX", "ONLINE")) return true
    if (song.filePath == "ONLINE_PODCAST") {
        val uri = song.uriString
        if (uri.contains("youtube.com", ignoreCase = true) || uri.contains("youtu.be", ignoreCase = true)) return true
        val orig = song.originalId
        if (!orig.isNullOrBlank() && !orig.startsWith("http", ignoreCase = true) && !orig.startsWith("saavn", ignoreCase = true) && !orig.all(Char::isDigit)) return true
    }
    return false
}

/**
 * Shared artwork, canvas motion, and YouTube Video Mode renderer with swipe/tap gesture support.
 */
@Composable
fun NowPlayingMediaStage(
    viewModel: PlayerViewModel,
    song: Song,
    artData: Any?,
    visualMedia: VisualMediaResult?,
    isVideoModeActive: Boolean,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    accentColor: Color,
    gesturesEnabled: Boolean,
    isGuest: Boolean,
    shape: Shape,
    onSeek: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onTap: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    videoResizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val artModel = artData ?: (song.albumArtUriString ?: song.uriString)

    if (isVideoModeActive) {
        val isVideoReady by viewModel.isVideoPlayerReady.collectAsState()
        val exoPlayer = viewModel.getVideoPlayer()
        val baseSpeed by viewModel.currentPlaybackSpeed.collectAsState()

        val currentResizeMode by viewModel.videoResizeMode.collectAsState()
        val effectiveResizeMode = currentResizeMode

        var hasRenderedFirstFrame by remember(exoPlayer, song.id) { mutableStateOf(false) }
        DisposableEffect(exoPlayer, song.id) {
            val listener = object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    hasRenderedFirstFrame = true
                }
            }
            exoPlayer?.addListener(listener)
            onDispose { exoPlayer?.removeListener(listener) }
        }
        val videoAlpha by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (isVideoReady && hasRenderedFirstFrame) 1f else 0f,
            animationSpec = androidx.compose.animation.core.tween(durationMillis = 200),
            label = "musicVideoFade"
        )

        PlayerCoverWithGestures(
            gesturesEnabled = gesturesEnabled,
            isGuest = isGuest,
            isPlaying = isPlaying,
            currentPosition = currentPosition,
            duration = duration,
            onSeek = { pos ->
                onSeek(pos)
                val mappedSeek = viewModel.mapAudioToVideoPosition(song.id.toString(), pos)
                exoPlayer?.seekTo(mappedSeek)
                viewModel.forceSyncToFirebase()
            },
            onPlayPause = {
                onPlayPause()
                viewModel.forceSyncToFirebase()
            },
            onTap = onTap,
            swipeEnabled = false,
            modifier = modifier.clip(shape).background(Color.Black)
        ) {
            // High-quality artwork underneath: visible only until video has rendered first frame (prevents leaking behind letterbox)
            if (!isVideoReady || !hasRenderedFirstFrame) {
                AsyncImage(
                    model = artModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    LytrixExpressiveSpinner(color = accentColor, size = 42.dp)
                }
            }

            if (exoPlayer != null) {
                val is16by9 = viewModel.isSong16by9(song)
                if (is16by9) {
                    LaunchedEffect(Unit) {
                        exoPlayer.volume = 0f
                        val mappedPos = viewModel.mapAudioToVideoPosition(song.id.toString(), currentPosition)
                        exoPlayer.seekTo(mappedPos)
                        exoPlayer.playWhenReady = isPlaying
                    }
                    LaunchedEffect(isPlaying) {
                        exoPlayer.volume = 0f
                        exoPlayer.playWhenReady = isPlaying
                    }
                    LaunchedEffect(currentPosition) {
                        if (exoPlayer.playbackState == Player.STATE_READY) {
                            val activeSpeed = if (com.musicplayer.lytrix.service.PlaybackService.isSpeedBoostActive) 2.0f else baseSpeed
                            val mappedExpectedPos = viewModel.mapAudioToVideoPosition(song.id.toString(), currentPosition)
                            val diff = exoPlayer.currentPosition - mappedExpectedPos
                            when {
                                kotlin.math.abs(diff) > 1500 -> exoPlayer.seekTo(mappedExpectedPos)
                                diff < -150 -> exoPlayer.playbackParameters = PlaybackParameters((activeSpeed * 1.05f).coerceAtMost(2.15f))
                                diff > 150 -> exoPlayer.playbackParameters = PlaybackParameters((activeSpeed * 0.95f).coerceAtLeast(0.25f))
                                else -> if (exoPlayer.playbackParameters.speed != activeSpeed) {
                                    exoPlayer.playbackParameters = PlaybackParameters(activeSpeed)
                                }
                            }
                        }
                    }
                }
                AndroidView(
                    factory = { ctx ->
                        (LayoutInflater.from(ctx).inflate(R.layout.apple_music_texture_player_view, null) as PlayerView).apply {
                            resizeMode = effectiveResizeMode
                            isClickable = false
                            isFocusable = false
                            setOnTouchListener { _, _ -> false }
                            subtitleView?.visibility = android.view.View.GONE
                        }
                    },
                    update = { pv ->
                        if (pv.player != exoPlayer) pv.player = exoPlayer
                        pv.resizeMode = effectiveResizeMode
                        pv.subtitleView?.visibility = android.view.View.GONE
                    },
                    modifier = Modifier.fillMaxSize().alpha(videoAlpha)
                )

                com.musicplayer.lytrix.ui.component.NowPlayingVideoOverlayControls(viewModel = viewModel)
            }
        }
    } else if (visualMedia != null) {
        LoopingCoverVideoPlayer(
            videoUrl = visualMedia.url,
            mediaType = visualMedia.type,
            song = song,
            artData = artData,
            isPlaying = isPlaying,
            currentPositionMs = currentPosition,
            durationMs = duration,
            onSeek = onSeek,
            onPlayPause = onPlayPause,
            onSkipNext = onSkipNext,
            onSkipPrevious = onSkipPrevious,
            onLongPress = onLongPress,
            gesturesEnabled = gesturesEnabled,
            isGuest = isGuest,
            clips = visualMedia.clips,
            modifier = modifier.clip(shape)
        )
    } else {
        PlayerCoverWithGestures(
            gesturesEnabled = gesturesEnabled,
            isGuest = isGuest,
            isPlaying = isPlaying,
            currentPosition = currentPosition,
            duration = duration,
            onSeek = onSeek,
            onPlayPause = onPlayPause,
            onSkipNext = onSkipNext,
            onSkipPrevious = onSkipPrevious,
            onLongPress = onLongPress,
            onTap = onTap,
            modifier = modifier.clip(shape)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(artModel).crossfade(true).build(),
                contentDescription = song.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun AudioVideoModePill(
    song: Song,
    isYtSong: Boolean,
    isVideoModeActive: Boolean,
    isPlaying: Boolean,
    accentColor: Color,
    contentColorOnAccent: Color,
    onSetVideoMode: (Boolean) -> Unit,
    onPlayPause: () -> Unit,
    onClose: () -> Unit,
    onPlayVideo: (Video) -> Unit,
    fallbackTitle: String = "Now Playing",
    isVideoHandoffPending: Boolean = false,
) {
    val context = LocalContext.current
    if (song.isVideo) {
        Row(
            modifier = Modifier
                .height(38.dp)
                .clip(RoundedCornerShape(19.dp))
                .background(accentColor.copy(alpha = 0.22f))
                .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(19.dp))
                .clickable {
                    onClose()
                    if (isPlaying) onPlayPause()
                    onPlayVideo(
                        Video(
                            id = -song.id,
                            title = song.title,
                            durationText = "",
                            sizeText = "",
                            uriString = song.uriString,
                            path = song.filePath ?: "",
                            dateModified = 0L
                        )
                    )
                }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Videocam, null, tint = accentColor, modifier = Modifier.size(17.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Back to Video", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color.White)
            Spacer(modifier = Modifier.width(2.dp))
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = accentColor, modifier = Modifier.size(16.dp))
        }
    } else if (isYtSong) {
        val audioBg by animateColorAsState(if (!isVideoModeActive) accentColor else Color.Transparent, tween(220), label = "pillA")
        val audioTint by animateColorAsState(if (!isVideoModeActive) contentColorOnAccent else Color.White.copy(alpha = 0.72f), tween(220), label = "pillAT")
        val videoBg by animateColorAsState(if (isVideoModeActive) accentColor else Color.Transparent, tween(220), label = "pillV")
        val videoTint by animateColorAsState(if (isVideoModeActive) contentColorOnAccent else Color.White.copy(alpha = 0.72f), tween(220), label = "pillVT")

        Row(
            modifier = Modifier
                .height(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White.copy(alpha = 0.12f))
                .padding(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .height(34.dp)
                    .width(46.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(audioBg)
                    .clickable { onSetVideoMode(false) },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Headphones, contentDescription = "Audio Mode", tint = audioTint, modifier = Modifier.size(19.dp))
            }
            Box(
                modifier = Modifier
                    .height(34.dp)
                    .width(46.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(videoBg)
                    .clickable {
                        if (!isVideoModeActive) {
                            val activeVm = com.musicplayer.lytrix.ui.PlayerViewModel.activeInstance
                            if (activeVm?.isInListenTogetherRoom() == true && !activeVm.isSong16by9(song)) {
                                android.widget.Toast.makeText(context, "Video mode for album tracks is disabled in Listen Together", android.widget.Toast.LENGTH_SHORT).show()
                                return@clickable
                            }
                            val activity = context as? Activity
                            if (activity != null) {
                                AdManager.showAdForVideo(activity, song.id.toString()) {
                                    onSetVideoMode(true)
                                }
                            } else {
                                onSetVideoMode(true)
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isVideoHandoffPending) {
                    com.musicplayer.lytrix.ui.LytrixExpressiveSpinner(
                        color = videoTint,
                        size = 18.dp
                    )
                } else {
                    Icon(Icons.Rounded.SmartDisplay, contentDescription = "Video Mode", tint = videoTint, modifier = Modifier.size(19.dp))
                }
            }
        }
    } else {
        Text(
            text = fallbackTitle,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.86f)
        )
    }
}

fun buildClickableArtistAnnotated(
    rawArtist: String,
    textColor: Color,
    fontSize: androidx.compose.ui.unit.TextUnit = 15.sp
): AnnotatedString = buildAnnotatedString {
    val parts = com.musicplayer.lytrix.utils.ArtistSeparatorManager.splitArtists(null, rawArtist)
    parts.forEachIndexed { idx, trimmed ->
        pushStringAnnotation(tag = "ARTIST", annotation = trimmed)
        withStyle(SpanStyle(color = textColor, fontWeight = FontWeight.Medium, fontSize = fontSize)) {
            append(trimmed)
        }
        pop()
        if (idx < parts.lastIndex) {
            withStyle(SpanStyle(color = textColor, fontWeight = FontWeight.Medium, fontSize = fontSize)) {
                append(", ")
            }
        }
    }
}

/**
 * Wires standard BackHandler + MainActivity back interception and mounts the shared overlays
 * (Lyrics, Queue, Equalizer, and Audio Output Picker) for all Now Playing styles.
 */
@Composable
fun NowPlayingCommonSheets(
    viewModel: PlayerViewModel,
    song: Song,
    currentLyrics: String?,
    currentPosition: Long,
    duration: Long,
    isPlaying: Boolean,
    accentColor: Color,
    showLyricsOverlay: Boolean,
    showQueue: Boolean,
    showEqualizer: Boolean,
    showOutputSheet: Boolean,
    isVideoModeActive: Boolean,
    outputs: List<PlayerAudioOutputEntry>,
    onSelectOutput: (PlayerAudioOutputEntry) -> Unit,
    onDismissLyrics: () -> Unit,
    onDismissQueue: () -> Unit,
    onDismissEqualizer: () -> Unit,
    onDismissOutput: () -> Unit,
    onDisableVideoMode: () -> Unit,
    onSeek: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onScrollTopOrClose: () -> Unit,
) {
    val context = LocalContext.current
    val providerName by viewModel.currentLyricsProviderName.collectAsState()

    fun handleBack(): Boolean = when {
        showLyricsOverlay -> { onDismissLyrics(); true }
        showOutputSheet -> { onDismissOutput(); true }
        showQueue -> { onDismissQueue(); true }
        showEqualizer -> { onDismissEqualizer(); true }
        isVideoModeActive -> { onDisableVideoMode(); true }
        else -> false
    }

    BackHandler(enabled = true) {
        if (!handleBack()) {
            onScrollTopOrClose()
        }
    }

    val mainActivity = context as? MainActivity
    DisposableEffect(showLyricsOverlay, showOutputSheet, showQueue, showEqualizer, isVideoModeActive) {
        if (mainActivity != null) {
            mainActivity.nowPlayingBackHandler = { handleBack() }
        }
        onDispose {
            if (mainActivity != null) {
                mainActivity.nowPlayingBackHandler = null
            }
        }
    }

    val effectiveLyricsPosition = viewModel.getEffectiveLyricsPosition(song, currentPosition)
    AnimatedVisibility(
        visible = showLyricsOverlay,
        enter = fadeIn() + slideInVertically { it },
        exit = fadeOut() + slideOutVertically { it }
    ) {
        LyricsFullScreenOverlay(
            song = song,
            lyrics = currentLyrics,
            currentPosition = effectiveLyricsPosition,
            isPlaying = isPlaying,
            onPlayPause = onPlayPause,
            onClose = onDismissLyrics,
            onSeek = { audioTargetMs -> viewModel.seekFromLyrics(song, audioTargetMs) },
            onChangeSource = { viewModel.changeLyricsSource(context) },
            onPrevious = onSkipPrevious,
            onNext = onSkipNext,
            durationMs = duration,
            accentColor = accentColor,
            providerName = providerName
        )
    }

    if (showQueue) {
        UpNextQueueSheet(viewModel = viewModel, accentColor = accentColor, onDismiss = onDismissQueue)
    }
    if (showEqualizer) {
        EqualizerBottomSheet(viewModel = viewModel, onDismiss = onDismissEqualizer)
    }
    if (showOutputSheet) {
        LytrixAudioOutputSheet(
            outputs = outputs,
            accentColor = accentColor,
            onSelectOutput = { entry ->
                onSelectOutput(entry)
                onDismissOutput()
            },
            onDismiss = onDismissOutput
        )
    }
}

@Composable
fun rememberExoVideoAspectRatio(exoPlayer: Player?): Float {
    var videoAspect by remember(exoPlayer) { mutableFloatStateOf(16f / 9f) }
    DisposableEffect(exoPlayer) {
        fun update(vs: VideoSize) {
            if (vs.width > 0 && vs.height > 0) {
                videoAspect = (vs.width.toFloat() / vs.height.toFloat()).coerceIn(0.6f, 2.4f)
            }
        }
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                update(videoSize)
            }
        }
        exoPlayer?.let {
            update(it.videoSize)
            it.addListener(listener)
        }
        onDispose { exoPlayer?.removeListener(listener) }
    }
    return videoAspect
}

class HoldToScrubState(
    initialPosition: Long? = null,
    onSetRewind: (Boolean) -> Unit,
    onSetForward: (Boolean) -> Unit
) {
    private val setRewindCallback = onSetRewind
    private val setForwardCallback = onSetForward
    var draggingPosition by mutableStateOf(initialPosition)
        internal set

    fun setRewind(active: Boolean) = setRewindCallback(active)
    fun setForward(active: Boolean) = setForwardCallback(active)
}

@Composable
fun rememberHoldToScrubState(
    currentPosition: Long,
    duration: Long,
    onSeek: (Long) -> Unit
): HoldToScrubState {
    val currentPosState = rememberUpdatedState(currentPosition)
    val durationState = rememberUpdatedState(duration)
    val onSeekState = rememberUpdatedState(onSeek)
    var isPressingRewind by remember { mutableStateOf(false) }
    var isPressingForward by remember { mutableStateOf(false) }
    val state = remember {
        HoldToScrubState(
            onSetRewind = { isPressingRewind = it },
            onSetForward = { isPressingForward = it }
        )
    }

    LaunchedEffect(isPressingForward) {
        if (isPressingForward) {
            var tempPos = state.draggingPosition ?: currentPosState.value
            while (true) {
                delay(50)
                tempPos = minOf(durationState.value.coerceAtLeast(0L), tempPos + 1000L)
                state.draggingPosition = tempPos
            }
        } else if (state.draggingPosition != null && !isPressingRewind) {
            onSeekState.value(state.draggingPosition!!)
            state.draggingPosition = null
        }
    }

    LaunchedEffect(isPressingRewind) {
        if (isPressingRewind) {
            var tempPos = state.draggingPosition ?: currentPosState.value
            while (true) {
                delay(50)
                tempPos = maxOf(0L, tempPos - 1000L)
                state.draggingPosition = tempPos
            }
        } else if (state.draggingPosition != null && !isPressingForward) {
            onSeekState.value(state.draggingPosition!!)
            state.draggingPosition = null
        }
    }

    return state
}

/**
 * Distinguishes a quick tap (skip track) from a 400ms+ hold (continuous scrub via [onHoldChanged]).
 */
fun Modifier.holdToScrubTransport(
    enabled: Boolean,
    scope: kotlinx.coroutines.CoroutineScope,
    onTap: () -> Unit,
    onHoldChanged: (Boolean) -> Unit
): Modifier = pointerInput(enabled) {
    if (!enabled) return@pointerInput
    detectTapGestures(
        onPress = {
            var holding = false
            val job = scope.launch {
                delay(400L)
                holding = true
                onHoldChanged(true)
            }
            try {
                tryAwaitRelease()
            } finally {
                job.cancel()
                if (holding) {
                    onHoldChanged(false)
                } else {
                    onTap()
                }
            }
        }
    )
}

fun formatTrackTime(millis: Long): String {
    val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

/**
 * Universal Landscape Edge-to-Edge Fullscreen Video Player.
 * Used across all NowPlaying styles (M3, Expressive, Apple Music, Cinema, Classic).
 */
@Composable
fun NowPlayingLandscapeVideoFullScreen(
    viewModel: PlayerViewModel,
    song: Song,
    artData: Any? = null,
    isPlaying: Boolean,
    isBuffering: Boolean,
    currentPosition: Long,
    duration: Long,
    accentColor: Color,
    contentColorOnAccent: Color,
    gesturesEnabled: Boolean = true,
    isGuest: Boolean = false,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activityContext = context.findActivity()

    // 1. Hardware / Gesture Back Handling - Resets orientation to UNSPECIFIED (never hard locks portrait)
    BackHandler(enabled = true) {
        viewModel.isLandscapeVideoFullscreen.value = false
        activityContext?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    // 2. Immersive System Bars Hiding while in Fullscreen
    val window = activityContext?.window
    DisposableEffect(Unit) {
        window?.let { win ->
            val insetsController = WindowCompat.getInsetsController(win, win.decorView)
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            window?.let { win ->
                WindowCompat.getInsetsController(win, win.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val isVideoPlayerReady by viewModel.isVideoPlayerReady.collectAsState()
    val exoPlayer = viewModel.getVideoPlayer()
    val currentResizeMode by viewModel.videoResizeMode.collectAsState()
    var showControls by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()
    val holdScrubState = rememberHoldToScrubState(currentPosition, duration, onSeek)

    var scrubbingFraction by remember { mutableStateOf<Float?>(null) }
    val safeDuration = duration.coerceAtLeast(1L)
    val shownPosMs = holdScrubState.draggingPosition
        ?: scrubbingFraction?.let { (it * safeDuration).toLong() }
        ?: currentPosition.coerceIn(0L, safeDuration)
    val shownFraction = (shownPosMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)

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
            exoPlayer?.seekTo(mapped)
            viewModel.forceSyncToFirebase()
        },
        onPlayPause = {
            onPlayPause()
            viewModel.forceSyncToFirebase()
        },
        onTap = { showControls = !showControls },
        swipeEnabled = false,
        modifier = modifier
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

        if (exoPlayer != null) {
            AndroidView(
                factory = { ctx ->
                    (LayoutInflater.from(ctx).inflate(
                        R.layout.apple_music_texture_player_view,
                        null
                    ) as PlayerView).apply {
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
                update = { pv ->
                    if (pv.player != exoPlayer) {
                        pv.player = exoPlayer
                    }
                    pv.resizeMode = currentResizeMode
                    pv.subtitleView?.visibility = android.view.View.GONE
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (!isVideoPlayerReady || exoPlayer == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                LytrixExpressiveSpinner(color = accentColor, size = 42.dp)
            }
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
                        viewModel.isLandscapeVideoFullscreen.value = false
                        activityContext?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
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
                                viewModel.isLandscapeVideoFullscreen.value = false
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
                            .background(accentColor)
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
                            safeEnterPip(activityContext, viewModel)
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(formatTrackTime(shownPosMs), fontSize = 12.sp, color = Color.White)
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
                                    val mapped = viewModel.mapAudioToVideoPosition(song.id.toString(), target)
                                    exoPlayer?.seekTo(mapped)
                                    viewModel.forceSyncToFirebase()
                                }
                            }
                            scrubbingFraction = null
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = accentColor,
                            activeTrackColor = accentColor,
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
                            .background(accentColor)
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
                            .clip(CircleShape)
                            .holdToScrubTransport(
                                enabled = !isGuest,
                                scope = coroutineScope,
                                onTap = onSkipNext,
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
}
