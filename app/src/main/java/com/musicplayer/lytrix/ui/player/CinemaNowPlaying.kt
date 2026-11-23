@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.musicplayer.lytrix.ui.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ripple
import com.musicplayer.lytrix.ui.component.tactileSpringPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Aod
import androidx.compose.material.icons.rounded.Bedtime
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.musicplayer.lytrix.aod.AodLaunchTrigger
import com.musicplayer.lytrix.model.Song
import com.musicplayer.lytrix.model.Video
import com.musicplayer.lytrix.ui.DataStoreManager
import com.musicplayer.lytrix.ui.LytrixExpressiveSpinner
import com.musicplayer.lytrix.ui.MeldNowPlayingBelowTheFold
import com.musicplayer.lytrix.ui.NowPlayingLiveLyricStrip
import com.musicplayer.lytrix.ui.PlayerViewModel
import com.musicplayer.lytrix.ui.rememberEmbeddedArt
import kotlinx.coroutines.launch

/**
 * Immersive Cinema Player inspired by Lytrix's ImmersivePlayerScreen.
 * Uses an edge-to-edge Ken Burns / Canvas / Zoomed Video stage that dissolves seamlessly into
 * a deep blurred palette backdrop via a vertical alpha fade mask, paired with a tap-to-toggle
 * Theatre Focus HUD and translucent floating transport controls.
 */
@Composable
fun CinemaNowPlayingFullScreen(
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
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val coroutineScope = rememberCoroutineScope()
    val dataStore = remember { DataStoreManager(context) }
    val gesturesEnabled by dataStore.playerGesturesEnabledFlow.collectAsState(initial = true)
    val isGuest = rememberIsListenTogetherGuest(viewModel)
    val isAppOnlineSong = viewModel.isSongOnline(song)
    val isYtSong = remember(song.filePath, song.albumArtUriString, song.uriString) { isYouTubeSong(song) }
    val displayIsFavorite = rememberResolvedIsFavorite(viewModel, song, isFavorite)
    val artData = rememberEmbeddedArt(context, song)
    val palette = rememberArtworkPaletteState(song, accentColor, artData)
    val visualMedia = rememberVisualMediaForSong(song)
    val qualityLabel = rememberPlayerAudioQualityLabel(viewModel, song)
    val (audioOutputs, selectOutput) = rememberAudioOutputEntries()
    val activeOutputName = remember(audioOutputs) {
        audioOutputs.firstOrNull { it.isActive }?.name ?: "Phone Speaker"
    }

    val isVideoModeActive by viewModel.isVideoModeActive.collectAsState()
    val isVideoHandoffPending by viewModel.isVideoHandoffPending.collectAsState()
    val sleepTimerState by viewModel.sleepTimerState.collectAsState()
    val sleepTimerDisplay = if (sleepTimerState.isActive) {
        com.musicplayer.lytrix.playback.formatSleepTimerRemaining(sleepTimerState.remainingMs)
    } else null
    val pointA by viewModel.pointA.collectAsState()
    val pointB by viewModel.pointB.collectAsState()
    val exoPlayer = if (isVideoModeActive) viewModel.getVideoPlayer() else null
    val videoAspect = rememberExoVideoAspectRatio(exoPlayer)
    var theatreFocusMode by remember { mutableStateOf(false) }
    var showLyricsOverlay by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showEqualizer by remember { mutableStateOf(false) }
    var showOutputSheet by remember { mutableStateOf(false) }
    var sliderDragMs by remember { mutableStateOf<Long?>(null) }
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

    // Slow Ken Burns gentle drift when static artwork is on stage and playing
    val kenBurnsTransition = rememberInfiniteTransition(label = "cinemaKenBurns")
    val breathScale by kenBurnsTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12_000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "kenBurnsScale"
    )
    val stageScale = if (isPlaying && visualMedia == null && !isVideoModeActive) breathScale else 1.0f

    val stageFadeMask = remember {
        Brush.verticalGradient(
            0.0f to Color.White,
            0.54f to Color.White.copy(alpha = 0.96f),
            0.78f to Color.White.copy(alpha = 0.48f),
            1.0f to Color.Transparent
        )
    }

    val horizontalStageFadeMask = remember {
        Brush.horizontalGradient(
            0.0f to Color.White,
            0.66f to Color.White.copy(alpha = 0.96f),
            0.88f to Color.White.copy(alpha = 0.42f),
            1.0f to Color.Transparent
        )
    }

    val artistAnnotated = remember(song.artist) {
        buildClickableArtistAnnotated(song.artist, Color.White.copy(alpha = 0.72f), fontSize = 15.sp)
    }

    val activityContext = context as? Activity
    val isLandscapeVideoFullscreen by viewModel.isLandscapeVideoFullscreen.collectAsState()
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
            accentColor = palette.effectiveAccent,
            contentColorOnAccent = palette.contentOnAccent,
            gesturesEnabled = gesturesEnabled,
            isGuest = isGuest,
            onPlayPause = onPlayPause,
            onSkipNext = onSkipNext,
            onSkipPrevious = onSkipPrevious,
            onSeek = onSeek
        )
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .nestedScroll(nestedScroll)
        ) {
            val viewportWidth = maxWidth
            val viewportHeight = maxHeight
            val statusBarTop = with(density) { WindowInsets.statusBars.getTop(density).toDp() }
            val artModel = artData ?: (song.albumArtUriString ?: song.uriString)
            val safeDuration = duration.coerceAtLeast(1L)
            val shownPos = sliderDragMs
                ?: holdScrubState.draggingPosition
                ?: currentPosition.coerceIn(0L, safeDuration)

            val metadataBlock: @Composable (Boolean) -> Unit = { showFav ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            color = Color.White,
                            fontSize = if (isLandscape) 20.sp else 23.sp,
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

                    if (showFav) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(if (isLandscape) 38.dp else 42.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.12f))
                                .clickable(onClick = onToggleFavorite),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (displayIsFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                contentDescription = "Favorite",
                                tint = if (displayIsFavorite) Color(0xFFFFD54F) else Color.White,
                                modifier = Modifier.size(if (isLandscape) 21.dp else 23.dp)
                            )
                        }
                    }
                }
            }

            val timelineBlock: @Composable () -> Unit = {
                com.musicplayer.lytrix.ui.component.ABRepeatSeekBarBox(
                    pointA = pointA,
                    pointB = pointB,
                    durationMs = safeDuration,
                    markerColor = palette.effectiveAccent,
                ) {
                    Slider(
                        value = shownPos.toFloat(),
                        onValueChange = { if (!isGuest) sliderDragMs = it.toLong() },
                        onValueChangeFinished = {
                            if (!isGuest) sliderDragMs?.let { onSeek(it) }
                            sliderDragMs = null
                        },
                        valueRange = 0f..safeDuration.toFloat(),
                        enabled = !isGuest && duration > 0L,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White.copy(alpha = 0.92f),
                            inactiveTrackColor = Color.White.copy(alpha = 0.24f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatTrackTime(shownPos), color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp)
                    Text("-${formatTrackTime((safeDuration - shownPos).coerceAtLeast(0L))}", color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp)
                }

                com.musicplayer.lytrix.ui.component.ABRepeatControlPill(
                    pointA = pointA,
                    pointB = pointB,
                    accentColor = palette.effectiveAccent,
                    onSetPointA = { viewModel.setPointA() },
                    onSetPointB = { viewModel.setPointB() },
                    onClearAB = { viewModel.clearAB() },
                )
            }

            val transportAndDockBlock: @Composable (androidx.compose.ui.unit.Dp) -> Unit = { playSize ->
                val cinemaHaptic = androidx.compose.ui.platform.LocalHapticFeedback.current
                val playInteraction = remember { MutableInteractionSource() }
                val shuffleInteraction = remember { MutableInteractionSource() }
                val repeatInteraction = remember { MutableInteractionSource() }
                val playCorner by androidx.compose.animation.core.animateDpAsState(
                    targetValue = if (isPlaying) playSize * 0.34f else playSize / 2,
                    animationSpec = androidx.compose.animation.core.spring(
                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                        stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                    ),
                    label = "cinemaPlayCorner"
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .tactileSpringPress(shuffleInteraction, 0.85f)
                                .clip(CircleShape)
                                .background(if (shuffleEnabled) palette.effectiveAccent.copy(alpha = 0.22f) else Color.Transparent)
                                .clickable(enabled = !isGuest, interactionSource = shuffleInteraction, indication = ripple(color = Color.White)) {
                                    cinemaHaptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                    onToggleShuffle()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = com.musicplayer.lytrix.ui.icons.LytrixIcons.Rounded.Shuffle,
                                contentDescription = "Shuffle",
                                tint = if (shuffleEnabled) palette.effectiveAccent else Color.White.copy(alpha = 0.58f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Icon(
                            imageVector = com.musicplayer.lytrix.ui.icons.LytrixIcons.Rounded.SkipPrevious,
                            contentDescription = "Previous",
                            tint = Color.White,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .holdToScrubTransport(
                                    enabled = !isGuest,
                                    scope = coroutineScope,
                                    onTap = {
                                        cinemaHaptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                        onSkipPrevious()
                                    },
                                    onHoldChanged = { holdScrubState.setRewind(it) }
                                )
                                .padding(9.dp)
                        )

                        Box(
                            modifier = Modifier
                                .size(playSize)
                                .tactileSpringPress(playInteraction, 0.88f)
                                .clip(RoundedCornerShape(playCorner))
                                .background(Color.White.copy(alpha = 0.22f))
                                .border(1.2.dp, Color.White.copy(alpha = 0.36f), RoundedCornerShape(playCorner))
                                .clickable(enabled = !isGuest, interactionSource = playInteraction, indication = ripple(color = Color.White)) {
                                    cinemaHaptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                    onPlayPause()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isBuffering) {
                                LytrixExpressiveSpinner(color = Color.White, strokeWidth = 2.5.dp, size = playSize * 0.44f)
                            } else {
                                com.musicplayer.lytrix.ui.component.MorphingPlayPauseIcon(
                                    isPlaying = isPlaying,
                                    playIcon = com.musicplayer.lytrix.ui.icons.LytrixIcons.Rounded.PlayArrow,
                                    pauseIcon = com.musicplayer.lytrix.ui.icons.LytrixIcons.Rounded.Pause,
                                    tint = Color.White,
                                    modifier = Modifier.size(playSize * 0.54f)
                                )
                            }
                        }

                        Icon(
                            imageVector = com.musicplayer.lytrix.ui.icons.LytrixIcons.Rounded.SkipNext,
                            contentDescription = "Next",
                            tint = Color.White,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .holdToScrubTransport(
                                    enabled = !isGuest,
                                    scope = coroutineScope,
                                    onTap = {
                                        cinemaHaptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                        onSkipNext()
                                    },
                                    onHoldChanged = { holdScrubState.setForward(it) }
                                )
                                .padding(9.dp)
                        )

                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .tactileSpringPress(repeatInteraction, 0.85f)
                                .clip(CircleShape)
                                .background(if (repeatMode != Player.REPEAT_MODE_OFF) palette.effectiveAccent.copy(alpha = 0.22f) else Color.Transparent)
                                .clickable(enabled = !isGuest, interactionSource = repeatInteraction, indication = ripple(color = Color.White)) {
                                    cinemaHaptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                    onToggleRepeat()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (repeatMode == Player.REPEAT_MODE_ONE) com.musicplayer.lytrix.ui.icons.LytrixIcons.Rounded.RepeatOne else com.musicplayer.lytrix.ui.icons.LytrixIcons.Rounded.Repeat,
                                contentDescription = "Repeat",
                                tint = if (repeatMode != Player.REPEAT_MODE_OFF) palette.effectiveAccent else Color.White.copy(alpha = 0.58f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(if (isLandscape) 10.dp else 14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.12f))
                                .clickable { showOutputSheet = true }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Headphones, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = activeOutputName,
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            CinemaChipIcon(Icons.Rounded.MusicNote, onClick = { showLyricsOverlay = true })
                            CinemaSleepTimerChip(
                                isActive = sleepTimerState.isActive,
                                countdownText = sleepTimerDisplay,
                                activeContainerColor = palette.effectiveAccent,
                                activeContentColor = palette.contentOnAccent,
                                onClick = onOpenSleepTimer,
                            )
                            CinemaChipIcon(Icons.Rounded.Equalizer, onClick = { showEqualizer = true })
                            CinemaChipIcon(Icons.AutoMirrored.Rounded.QueueMusic, onClick = { showQueue = true })
                        }
                    }
                }
            }

            val formatSeamBlock: @Composable () -> Unit = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.weight(1f).height(0.5.dp).background(Color.White.copy(alpha = 0.22f)))
                    Text(
                        text = qualityLabel,
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    )
                    Box(modifier = Modifier.weight(1f).height(0.5.dp).background(Color.White.copy(alpha = 0.22f)))
                }
            }

            val aodAndMenuButtons: @Composable (androidx.compose.ui.unit.Dp) -> Unit = { btnSize ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(btnSize)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.42f))
                            .combinedClickable(
                                onClick = { AodLaunchTrigger.launch(initialLocked = false) },
                                onLongClick = { AodLaunchTrigger.launch(initialLocked = true) }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Aod, "Always-On Display (Hold for Pocket Lock)", tint = Color.White, modifier = Modifier.size(btnSize * 0.48f))
                    }
                    Box(
                        modifier = Modifier
                            .size(btnSize)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.42f))
                            .clickable(onClick = onOpenMenu),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.MoreVert, "Menu", tint = Color.White, modifier = Modifier.size(btnSize * 0.50f))
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(viewportHeight)
                ) {
                    // Full-height ambient blurred backdrop
                    val backdropReq = remember(artModel) {
                        ImageRequest.Builder(context)
                            .data(artModel)
                            .apply { if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) size(48) }
                            .crossfade(400)
                            .build()
                    }
                    AsyncImage(
                        model = backdropReq,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(64.dp) else Modifier)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0.0f to Color.Black.copy(alpha = 0.25f),
                                    0.55f to palette.primaryBg.copy(alpha = 0.65f),
                                    1.0f to Color.Black.copy(alpha = 0.94f)
                                )
                            )
                    )

                    if (isLandscape) {
                        // Lytrix ImmersiveLandscape port: Left 48% Stage with soft horizontal fade, Right 52% Controls
                        val artWidth = viewportWidth * 0.48f

                        Box(
                            modifier = Modifier
                                .size(width = artWidth, height = viewportHeight)
                                .align(Alignment.TopStart)
                                .then(
                                    if (isVideoModeActive) {
                                        Modifier.background(Color.Black.copy(alpha = 0.35f))
                                    } else {
                                        Modifier
                                            .graphicsLayer {
                                                compositingStrategy = CompositingStrategy.Offscreen
                                                scaleX = stageScale
                                                scaleY = stageScale
                                            }
                                            .drawWithContent {
                                                drawContent()
                                                drawRect(brush = horizontalStageFadeMask, blendMode = BlendMode.DstIn)
                                            }
                                    }
                                )
                        ) {
                            NowPlayingMediaStage(
                                viewModel = viewModel,
                                song = song,
                                artData = artData,
                                visualMedia = visualMedia,
                                isVideoModeActive = isVideoModeActive,
                                isPlaying = isPlaying,
                                currentPosition = currentPosition,
                                duration = duration,
                                accentColor = palette.effectiveAccent,
                                gesturesEnabled = gesturesEnabled,
                                isGuest = isGuest,
                                shape = RectangleShape,
                                onSeek = onSeek,
                                onPlayPause = onPlayPause,
                                onSkipNext = onSkipNext,
                                onSkipPrevious = onSkipPrevious,
                                onTap = { theatreFocusMode = !theatreFocusMode },
                                videoResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Top-left Collapse over stage (respects cutout/systemBars in landscape)
                        androidx.compose.animation.AnimatedVisibility(
                            visible = !theatreFocusMode,
                            enter = fadeIn(tween(240)),
                            exit = fadeOut(tween(240)),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .systemBarsPadding()
                                .padding(start = 18.dp, top = 12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.42f))
                                    .clickable(onClick = onClose),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.KeyboardArrowDown, "Collapse", tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                        }

                        // Right 52% Controls Column
                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(0.52f)
                                .align(Alignment.CenterEnd)
                                .systemBarsPadding()
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            AnimatedVisibility(visible = !theatreFocusMode, enter = fadeIn(tween(220)), exit = fadeOut(tween(220))) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AudioVideoModePill(
                                            song = song,
                                            isYtSong = isYtSong,
                                            isVideoModeActive = isVideoModeActive,
                                            isPlaying = isPlaying,
                                            accentColor = palette.effectiveAccent,
                                            contentColorOnAccent = palette.contentOnAccent,
                                            onSetVideoMode = { if (it) viewModel.switchToVideoMode(song) else viewModel.switchToAudioMode(song) },
                                            onPlayPause = onPlayPause,
                                            onClose = onClose,
                                            onPlayVideo = onPlayVideo,
                                            fallbackTitle = "Cinema Mode",
                                            isVideoHandoffPending = isVideoHandoffPending
                                        )
                                        Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                                            aodAndMenuButtons(36.dp)
                                        }
                                    }
                                    formatSeamBlock()
                                }
                            }

                            NowPlayingLiveLyricStrip(
                                song = song,
                                currentLyrics = currentLyrics,
                                currentPosition = viewModel.getEffectiveLyricsPosition(song, currentPosition),
                                isPlaying = isPlaying,
                                fontSize = if (theatreFocusMode) 17.sp else 14.sp,
                                textAlign = TextAlign.Center,
                                contentAlignment = Alignment.Center,
                                onClick = { showLyricsOverlay = true }
                            )

                            Column(modifier = Modifier.fillMaxWidth()) {
                                metadataBlock(!theatreFocusMode)
                                Spacer(modifier = Modifier.height(6.dp))
                                timelineBlock()
                            }

                            AnimatedVisibility(visible = !theatreFocusMode, enter = fadeIn(tween(220)), exit = fadeOut(tween(220))) {
                                transportAndDockBlock(58.dp)
                            }
                        }
                    } else {
                        // ImmersivePortrait layout: Top Cinema Visual Stage (~65% viewport height)
                        if (isVideoModeActive) {
                            val videoHeight = (screenWidth / videoAspect).coerceIn(200.dp, viewportHeight * 0.56f)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = statusBarTop + 62.dp)
                                    .height(videoHeight)
                                    .align(Alignment.TopCenter)
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
                                    accentColor = palette.effectiveAccent,
                                    gesturesEnabled = gesturesEnabled,
                                    isGuest = isGuest,
                                    shape = RectangleShape,
                                    onSeek = onSeek,
                                    onPlayPause = onPlayPause,
                                    onSkipNext = onSkipNext,
                                    onSkipPrevious = onSkipPrevious,
                                    onTap = { theatreFocusMode = !theatreFocusMode },
                                    videoResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(0.65f)
                                    .align(Alignment.TopCenter)
                                    .graphicsLayer {
                                        compositingStrategy = CompositingStrategy.Offscreen
                                        scaleX = stageScale
                                        scaleY = stageScale
                                    }
                                    .drawWithContent {
                                        drawContent()
                                        drawRect(brush = stageFadeMask, blendMode = BlendMode.DstIn)
                                    }
                            ) {
                                NowPlayingMediaStage(
                                    viewModel = viewModel,
                                    song = song,
                                    artData = artData,
                                    visualMedia = visualMedia,
                                    isVideoModeActive = false,
                                    isPlaying = isPlaying,
                                    currentPosition = currentPosition,
                                    duration = duration,
                                    accentColor = palette.effectiveAccent,
                                    gesturesEnabled = gesturesEnabled,
                                    isGuest = isGuest,
                                    shape = RectangleShape,
                                    onSeek = onSeek,
                                    onPlayPause = onPlayPause,
                                    onSkipNext = onSkipNext,
                                    onSkipPrevious = onSkipPrevious,
                                    onTap = { theatreFocusMode = !theatreFocusMode },
                                    videoResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        // Top Cinema Header (auto-hides cleanly in Theatre Focus Mode)
                        androidx.compose.animation.AnimatedVisibility(
                            visible = !theatreFocusMode,
                            enter = fadeIn(tween(240)),
                            exit = fadeOut(tween(240)),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .padding(top = statusBarTop + 12.dp, start = 18.dp, end = 18.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.42f))
                                        .clickable(onClick = onClose),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Rounded.KeyboardArrowDown, "Collapse", tint = Color.White, modifier = Modifier.size(26.dp))
                                }

                                AudioVideoModePill(
                                    song = song,
                                    isYtSong = isYtSong,
                                    isVideoModeActive = isVideoModeActive,
                                    isPlaying = isPlaying,
                                    accentColor = palette.effectiveAccent,
                                    contentColorOnAccent = palette.contentOnAccent,
                                    onSetVideoMode = { if (it) viewModel.switchToVideoMode(song) else viewModel.switchToAudioMode(song) },
                                    onPlayPause = onPlayPause,
                                    onClose = onClose,
                                    onPlayVideo = onPlayVideo,
                                    fallbackTitle = "Cinema Mode",
                                    isVideoHandoffPending = isVideoHandoffPending
                                )

                                aodAndMenuButtons(40.dp)
                            }
                        }

                        // Bottom Cinema Translucent Controls Deck
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 24.dp, vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            AnimatedVisibility(visible = !theatreFocusMode, enter = fadeIn(), exit = fadeOut()) {
                                Box(modifier = Modifier.padding(bottom = 12.dp)) {
                                    formatSeamBlock()
                                }
                            }

                            NowPlayingLiveLyricStrip(
                                song = song,
                                currentLyrics = currentLyrics,
                                currentPosition = viewModel.getEffectiveLyricsPosition(song, currentPosition),
                                isPlaying = isPlaying,
                                fontSize = if (theatreFocusMode) 17.sp else 15.sp,
                                textAlign = TextAlign.Center,
                                contentAlignment = Alignment.Center,
                                onClick = { showLyricsOverlay = true }
                            )

                            Spacer(modifier = Modifier.height(10.dp))
                            metadataBlock(!theatreFocusMode)
                            Spacer(modifier = Modifier.height(8.dp))
                            timelineBlock()

                            AnimatedVisibility(visible = !theatreFocusMode, enter = fadeIn(tween(220)), exit = fadeOut(tween(220))) {
                                Column {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    transportAndDockBlock(66.dp)
                                }
                            }
                        }
                    }
                }

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
                                    effectiveAccent = palette.effectiveAccent,
                                    contentColorOnAccent = palette.contentOnAccent,
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
                            effectiveAccent = palette.effectiveAccent,
                            contentColorOnAccent = palette.contentOnAccent,
                            onNavigateToArtist = { artistName ->
                                onClose()
                                onNavigateToArtist(artistName)
                            }
                        )
                    }
                }
            }
        }

        // Mounted AFTER BoxWithConstraints so LyricsFullScreenOverlay draws on top of the black backdrop.
        NowPlayingCommonSheets(
            viewModel = viewModel,
            song = song,
            currentLyrics = currentLyrics,
            currentPosition = currentPosition,
            duration = duration,
            isPlaying = isPlaying,
            accentColor = palette.effectiveAccent,
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
            onSkipNext = onSkipNext,
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
}

@Composable
private fun CinemaChipIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    com.musicplayer.lytrix.ui.component.MetroMorphingIconButton(
        onClick = onClick,
        containerColor = Color.White.copy(alpha = 0.14f),
        contentColor = Color.White.copy(alpha = 0.90f),
        size = 36.dp
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun CinemaSleepTimerChip(
    isActive: Boolean,
    countdownText: String?,
    activeContainerColor: Color,
    activeContentColor: Color,
    onClick: () -> Unit,
) {
    if (isActive && countdownText != null) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = activeContainerColor,
            contentColor = activeContentColor,
            modifier = Modifier.height(36.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = com.musicplayer.lytrix.ui.icons.LytrixIcons.Filled.Bedtime,
                    contentDescription = "Sleep Timer",
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = countdownText,
                    style = MaterialTheme.typography.labelSmall,
                    color = activeContentColor,
                    maxLines = 1,
                )
            }
        }
    } else {
        CinemaChipIcon(
            icon = com.musicplayer.lytrix.ui.icons.LytrixIcons.Outlined.Bedtime,
            onClick = onClick,
        )
    }
}
