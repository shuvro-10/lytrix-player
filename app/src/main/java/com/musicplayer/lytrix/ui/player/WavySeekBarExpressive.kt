package com.musicplayer.lytrix.ui.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Custom Canvas-based Material 3 Expressive Wavy Seekbar.
 * Draws an animated travelling sine wave across the played segment while [isPlaying] and not
 * scrubbing, flattens cleanly on pause/touch, and morphs the thumb between a circle and pill bar.
 */
@Composable
fun WavySeekBarExpressive(
    progressFraction: Float,
    isPlaying: Boolean,
    activeColor: Color,
    trackColor: Color,
    thumbColor: Color,
    onProgressChange: (Float) -> Unit,
    onProgressChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 4.5.dp,
    wavelength: Dp = 28.dp,
    maxAmplitude: Dp = 4.dp,
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val strokePx = with(density) { strokeWidth.toPx() }
    val waveLenPx = with(density) { wavelength.toPx() }.coerceAtLeast(24f)
    val maxAmpPx = with(density) { maxAmplitude.toPx() }
    val thumbGapPx = with(density) { 10.dp.toPx() }

    var isInteracting by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(progressFraction.coerceIn(0f, 1f)) }
    var widthPx by remember { mutableIntStateOf(0) }
    var phaseRad by remember { mutableFloatStateOf(0f) }

    val latestOnChange by rememberUpdatedState(onProgressChange)
    val latestOnFinish by rememberUpdatedState(onProgressChangeFinished)
    val displayedFraction = (if (isInteracting) dragFraction else progressFraction).coerceIn(0f, 1f)

    val amplitudeFactor by animateFloatAsState(
        targetValue = if (isPlaying && !isInteracting) 1f else 0f,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "wavyAmplitude"
    )
    val thumbMorph by animateFloatAsState(
        targetValue = if (isInteracting) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "wavyThumbMorph"
    )

    // Continuous traveling wave phase while active
    LaunchedEffect(isPlaying, isInteracting) {
        if (!isPlaying || isInteracting) return@LaunchedEffect
        var lastNs = 0L
        while (isActive) {
            withInfiniteAnimationFrameNanos { ns ->
                if (lastNs != 0L) {
                    val dt = ((ns - lastNs).coerceIn(0L, 64_000_000L)) / 1_000_000_000f
                    phaseRad = (phaseRad + dt * 5.2f) % (2f * PI.toFloat())
                }
                lastNs = ns
            }
        }
    }

    fun fractionAt(x: Float): Float = if (widthPx <= 0) 0f else (x / widthPx).coerceIn(0f, 1f)

    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .onSizeChanged { widthPx = it.width }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val f = fractionAt(offset.x)
                    dragFraction = f
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    latestOnChange(f)
                    latestOnFinish()
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isInteracting = true
                        val f = fractionAt(offset.x)
                        dragFraction = f
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        latestOnChange(f)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val f = fractionAt(change.position.x)
                        dragFraction = f
                        latestOnChange(f)
                    },
                    onDragEnd = {
                        isInteracting = false
                        latestOnFinish()
                    },
                    onDragCancel = {
                        isInteracting = false
                        latestOnFinish()
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerY = size.height / 2f
            val totalW = size.width
            if (totalW <= 0f) return@Canvas

            val thumbX = displayedFraction * totalW
            val activeEnd = (thumbX - thumbGapPx).coerceAtLeast(0f)
            val inactiveStart = (thumbX + thumbGapPx).coerceAtMost(totalW)

            // Inactive flat segment
            if (inactiveStart < totalW) {
                drawLine(
                    color = trackColor,
                    start = Offset(inactiveStart, centerY),
                    end = Offset(totalW, centerY),
                    strokeWidth = strokePx,
                    cap = StrokeCap.Round
                )
            }

            // Active wavy segment
            if (activeEnd > 2f) {
                val amp = maxAmpPx * amplitudeFactor
                if (amp < 0.25f) {
                    drawLine(
                        color = activeColor,
                        start = Offset(0f, centerY),
                        end = Offset(activeEnd, centerY),
                        strokeWidth = strokePx,
                        cap = StrokeCap.Round
                    )
                } else {
                    val path = Path()
                    val stepPx = 4f
                    val twoPiOverLen = (2f * PI.toFloat()) / waveLenPx
                    var x = 0f
                    while (x <= activeEnd) {
                        val edgeTaper = ((activeEnd - x) / 14f).coerceIn(0f, 1f)
                        val y = centerY + sin(x * twoPiOverLen - phaseRad) * amp * edgeTaper
                        if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
                        x += stepPx
                    }
                    path.lineTo(activeEnd, centerY)
                    drawPath(
                        path = path,
                        color = activeColor,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
            }
        }

        // Morphing Thumb: circle at rest -> tall rounded bar while dragging
        val thumbW = (14f + (6f - 14f) * thumbMorph).dp
        val thumbH = (14f + (24f - 14f) * thumbMorph).dp
        val thumbXPx = (displayedFraction * widthPx - with(density) { thumbW.toPx() / 2f }).roundToInt()
        Box(
            modifier = Modifier
                .offset { IntOffset(thumbXPx, 0) }
                .size(width = thumbW, height = thumbH)
                .clip(RoundedCornerShape(50))
                .background(thumbColor)
        )
    }
}

private enum class V9ControlKey { PREV, PLAY_PAUSE, NEXT }

/**
 * Lytrix V9-style spring-physics morphing playback button bar.
 * Pressing a button expands its weight while compressing neighbors, and the central Play/Pause
 * container morphs between a full pill circle and a wide expressive squircle.
 */
@Composable
fun V9ExpressiveTransportRow(
    isPlaying: Boolean,
    isBusy: Boolean,
    isGuest: Boolean,
    playContainerColor: Color,
    playIconColor: Color,
    sideContainerColor: Color,
    sideIconColor: Color,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 78.dp,
    onRewindHoldChanged: (Boolean) -> Unit = {},
    onForwardHoldChanged: (Boolean) -> Unit = {},
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var pressedKey by remember { mutableStateOf<V9ControlKey?>(null) }
    var holdingKey by remember { mutableStateOf<V9ControlKey?>(null) }
    var pressCounter by remember { mutableIntStateOf(0) }
    val onPreviousState = rememberUpdatedState(onPrevious)
    val onNextState = rememberUpdatedState(onNext)
    val onRewindHoldState = rememberUpdatedState(onRewindHoldChanged)
    val onForwardHoldState = rememberUpdatedState(onForwardHoldChanged)

    LaunchedEffect(pressedKey, holdingKey, pressCounter) {
        if (pressedKey != null && holdingKey == null) {
            delay(240L)
            if (holdingKey == null) {
                pressedKey = null
            }
        }
    }

    val prevInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val nextInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }

    fun Modifier.transportPressHold(
        key: V9ControlKey,
        interactionSource: androidx.compose.foundation.interaction.MutableInteractionSource,
        onTap: () -> Unit,
        onHoldChanged: (Boolean) -> Unit,
    ): Modifier = this
        .indication(interactionSource, androidx.compose.material3.ripple())
        .pointerInput(isGuest, key) {
            if (isGuest) return@pointerInput
            detectTapGestures(
                onPress = { offset ->
                    val press = androidx.compose.foundation.interaction.PressInteraction.Press(offset)
                    scope.launch { interactionSource.emit(press) }
                    pressedKey = key
                    pressCounter++
                    var isHolding = false
                    val holdJob = scope.launch {
                        delay(400L)
                        isHolding = true
                        holdingKey = key
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onHoldChanged(true)
                    }
                    try {
                        tryAwaitRelease()
                    } finally {
                        holdJob.cancel()
                        scope.launch { interactionSource.emit(androidx.compose.foundation.interaction.PressInteraction.Release(press)) }
                        if (isHolding) {
                            holdingKey = null
                            pressedKey = null
                            onHoldChanged(false)
                        } else {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onTap()
                        }
                    }
                }
            )
        }

    fun weightFor(key: V9ControlKey, baseWeight: Float): Float = when {
        pressedKey == null -> baseWeight
        pressedKey == key -> baseWeight * 1.24f
        else -> baseWeight * 0.84f
    }

    val weightSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
    val shapeSpec = spring<Dp>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    val prevWeight by animateFloatAsState(weightFor(V9ControlKey.PREV, 0.92f), weightSpec, label = "v9PrevW")
    val playWeight by animateFloatAsState(weightFor(V9ControlKey.PLAY_PAUSE, 1.38f), weightSpec, label = "v9PlayW")
    val nextWeight by animateFloatAsState(weightFor(V9ControlKey.NEXT, 0.92f), weightSpec, label = "v9NextW")

    val playCorner by animateDpAsState(
        targetValue = if (isPlaying) 22.dp else height / 2,
        animationSpec = shapeSpec,
        label = "v9PlayCorner"
    )
    val sideCorner by animateDpAsState(
        targetValue = if (pressedKey == V9ControlKey.PREV || pressedKey == V9ControlKey.NEXT) 22.dp else height / 2,
        animationSpec = shapeSpec,
        label = "v9SideCorner"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(prevWeight)
                .fillMaxHeight()
                .clip(RoundedCornerShape(sideCorner))
                .background(sideContainerColor)
                .transportPressHold(
                    key = V9ControlKey.PREV,
                    interactionSource = prevInteraction,
                    onTap = { onPreviousState.value() },
                    onHoldChanged = { onRewindHoldState.value(it) }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipPrevious,
                contentDescription = "Previous",
                tint = sideIconColor,
                modifier = Modifier.size(30.dp)
            )
        }

        Box(
            modifier = Modifier
                .weight(playWeight)
                .fillMaxHeight()
                .graphicsLayer {
                    clip = true
                    shape = RoundedCornerShape(playCorner)
                }
                .background(playContainerColor)
                .clickable(enabled = !isGuest) {
                    pressedKey = V9ControlKey.PLAY_PAUSE
                    pressCounter++
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onPlayPause()
                },
            contentAlignment = Alignment.Center
        ) {
            if (isBusy) {
                com.musicplayer.lytrix.ui.LytrixExpressiveSpinner(
                    color = playIconColor,
                    strokeWidth = 3.dp,
                    size = 28.dp
                )
            } else {
                Crossfade(
                    targetState = isPlaying,
                    animationSpec = tween(200),
                    label = "v9PlayIcon"
                ) { playing ->
                    Icon(
                        imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (playing) "Pause" else "Play",
                        tint = playIconColor,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(nextWeight)
                .fillMaxHeight()
                .clip(RoundedCornerShape(sideCorner))
                .background(sideContainerColor)
                .transportPressHold(
                    key = V9ControlKey.NEXT,
                    interactionSource = nextInteraction,
                    onTap = { onNextState.value() },
                    onHoldChanged = { onForwardHoldState.value(it) }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipNext,
                contentDescription = "Next",
                tint = sideIconColor,
                modifier = Modifier.size(30.dp)
            )
        }
    }
}
