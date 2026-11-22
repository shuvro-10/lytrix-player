package com.musicplayer.lytrix.ui.component

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PlayerCoverWithGestures(
    gesturesEnabled: Boolean,
    isGuest: Boolean = false,
    isPlaying: Boolean = false,
    currentPosition: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit = {},
    onSkipPrevious: () -> Unit = {},
    onLongPress: (() -> Unit)? = null,
    onTap: (() -> Unit)? = null,
    swipeEnabled: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val currentPosState = rememberUpdatedState(currentPosition)
    val durationState = rememberUpdatedState(duration)
    val isPlayingState = rememberUpdatedState(isPlaying)
    val isGuestState = rememberUpdatedState(isGuest)
    val gesturesEnabledState = rememberUpdatedState(gesturesEnabled)
    val onSeekState = rememberUpdatedState(onSeek)
    val onPlayPauseState = rememberUpdatedState(onPlayPause)
    val onSkipNextState = rememberUpdatedState(onSkipNext)
    val onSkipPreviousState = rememberUpdatedState(onSkipPrevious)
    val onLongPressState = rememberUpdatedState(onLongPress)
    val onTapState = rememberUpdatedState(onTap)

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

    val density = LocalDensity.current
    val swipeThresholdPx = remember(density) { with(density) { 64.dp.toPx() } }

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

    // Auto-dismiss seek effect overlay after 850ms
    LaunchedEffect(seekTrigger) {
        if (seekTrigger > 0) {
            showSeekOverlay = true
            delay(850L)
            showSeekOverlay = false
        }
    }

    // Auto-dismiss center play/pause pop after 600ms
    LaunchedEffect(centerPopTrigger) {
        if (centerPopTrigger > 0) {
            showCenterPop = true
            delay(600L)
            showCenterPop = false
        }
    }

    val tapModifier = if (gesturesEnabled) {
        Modifier.pointerInput(gesturesEnabled) {
            detectTapGestures(
                onPress = {
                    if (!isGuestState.value) {
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
                onTap = if (onTap != null) { { onTapState.value?.invoke() } } else null,
                onLongPress = {
                    onLongPressState.value?.invoke()
                },
                onDoubleTap = { offset ->
                    if (!isGuestState.value) {
                        val w = size.width
                        val leftBoundary = w * 0.35f
                        val rightBoundary = w * 0.65f

                        when {
                            // Left 35%: Seek Backward (-5s, -10s, etc.)
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

                            // Right 35%: Seek Forward (+5s, +10s, etc.)
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
            )
        }
    } else {
        if (onLongPress != null || onTap != null) {
            Modifier.pointerInput(Unit) {
                detectTapGestures(
                    onTap = if (onTap != null) { { onTapState.value?.invoke() } } else null,
                    onLongPress = { onLongPressState.value?.invoke() }
                )
            }
        } else {
            Modifier
        }
    }

    val dragModifier = if (gesturesEnabled && swipeEnabled) {
        Modifier.pointerInput(gesturesEnabled, swipeEnabled) {
            var dragAccumulator = 0f
            detectHorizontalDragGestures(
                onDragStart = { dragAccumulator = 0f },
                onHorizontalDrag = { change, dragAmount ->
                    change.consume()
                    dragAccumulator += dragAmount
                },
                onDragEnd = {
                    if (!isGuestState.value) {
                        if (dragAccumulator > swipeThresholdPx) {
                            onSkipPreviousState.value()
                        } else if (dragAccumulator < -swipeThresholdPx) {
                            onSkipNextState.value()
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
        modifier = modifier.then(tapModifier).then(dragModifier),
        contentAlignment = Alignment.Center
    ) {
        // Child content (Album art / video / discs)
        content()

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
                    .background(Color.Black.copy(alpha = 0.75f))
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
                    .background(Color.Black.copy(alpha = 0.75f))
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
                    .background(Color.Black.copy(alpha = 0.72f)),
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
