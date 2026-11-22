package com.musicplayer.lytrix.ui

import kotlin.math.pow

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.LocalIndication
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 🌟 Lytrix Fluid Tactile Bounce Engine
 * SimpMusic ও Meld এর মতো যেকোনো বাটন, কার্ড বা আইটেম টাচ করলে
 * এটি ০.৯৫x স্কেল ডাউন হয় এবং আঙুল ছাড়লে স্প্রিং বাউন্স করে ফিরে আসে।
 */

/**
 * ১. সাধারণ ক্লিকেবল আইটেমে বাউন্স যোগ করার জন্য
 */
fun Modifier.bounceClick(
    pressedScale: Float = 0.95f,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = 0.55f, // Tactile bouncy feel
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "bounceClickScale"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            onClick = onClick
        )
}

/**
 * ২. বিদ্যমান যেকোনো কার্ড, বাটন বা রো-এর ওপর সরাসরি বাউন্স রেসপন্স দেওয়ার জন্য
 * (এটি কোনো ক্লিক ইভেন্ট কনজিউম করে না, তাই আগের লজিক ১০০% অক্ষত থাকে)
 */
fun Modifier.bouncePress(
    pressedScale: Float = 0.95f
): Modifier = composed {
    var isPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = 0.55f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "bouncePressScale"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    waitForUpOrCancellation()
                    isPressed = false
                }
            }
        }
}

/**
 * 🎵 Lytrix Live Equalizer Waves (Dancing Bars)
 * সক্রিয় (Currently Playing) গানের ওপর ছন্দময় নাচানাচি করা ৩টি বার।
 * গান পজ থাকলে স্থির হয়ে থাকবে, গান প্লে হলে নাচবে।
 */
@Composable
fun LytrixEqualizerWaves(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barColor: Color = Color.White,
    maxHeight: Dp = 18.dp,
    barWidth: Dp = 3.dp,
    spacing: Dp = 2.5.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "eq_waves")

    val bar1Height by if (isPlaying) {
        infiniteTransition.animateFloat(
            initialValue = 0.25f,
            targetValue = 0.95f,
            animationSpec = infiniteRepeatable(
                animation = tween(420, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar1"
        )
    } else {
        remember { mutableStateOf(0.35f) }
    }

    val bar2Height by if (isPlaying) {
        infiniteTransition.animateFloat(
            initialValue = 0.85f,
            targetValue = 0.20f,
            animationSpec = infiniteRepeatable(
                animation = tween(330, easing = LinearOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar2"
        )
    } else {
        remember { mutableStateOf(0.6f) }
    }

    val bar3Height by if (isPlaying) {
        infiniteTransition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(490, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar3"
        )
    } else {
        remember { mutableStateOf(0.4f) }
    }

    Row(
        modifier = modifier.height(maxHeight),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            modifier = Modifier
                .width(barWidth)
                .fillMaxHeight(bar1Height)
                .clip(RoundedCornerShape(1.5.dp))
                .background(barColor)
        )
        Box(
            modifier = Modifier
                .width(barWidth)
                .fillMaxHeight(bar2Height)
                .clip(RoundedCornerShape(1.5.dp))
                .background(barColor)
        )
        Box(
            modifier = Modifier
                .width(barWidth)
                .fillMaxHeight(bar3Height)
                .clip(RoundedCornerShape(1.5.dp))
                .background(barColor)
        )
    }
}

/**
 * 🌟 Google Material 3 Expressive Morphing Loading Indicator
 * যেমনটা Google Play Store, Google Photos এবং Android 15-এ দেখা যায়।
 * এটি ৫টি ক্যানোনিকাল Material 3 শেপের ভেতর মসৃণভাবে রূপ নেয় (Shape Morphing):
 * 1. Cookie4Sided (৪টি পাপড়ির ক্লোভার)
 * 2. SoftBurst (১০টি সূক্ষ্ম পয়েন্টেড স্টার)
 * 3. Cookie9Sided (৯টি গোলাকার পাপড়ির কুকি)
 * 4. Pill / Oval (মসৃণ ওভাল)
 * 5. Sunny (৮টি পয়েন্টেড স্টার)
 * এবং প্রতিটি শেপ পরিবর্তনের সাথে সাথে অর্গানিক স্প্রিং রোটেশন ও স্থিতিস্থাপক জেলি পালস দেয়।
 */
@Composable
fun LytrixExpressiveSpinner(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    size: Dp = 32.dp,
    strokeWidth: Dp = 3.dp,
    trackColor: Color = Color.Transparent
) {
    val infiniteTransition = rememberInfiniteTransition(label = "m3_expressive_morph")

    // Morphs through 5 distinct Material 3 Expressive shapes: 0..4
    val morphCycle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "morphCycle"
    )

    androidx.compose.foundation.Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val baseR = (minOf(w, h) / 2f) * 0.76f
        val cx = w / 2f
        val cy = h / 2f

        val currentStage = morphCycle.toInt() % 5
        val nextStage = (currentStage + 1) % 5
        val rawFrac = (morphCycle - morphCycle.toInt()).coerceIn(0f, 1f)

        // Expressive cubic-bezier easing for fluid morphing
        val t = androidx.compose.animation.core.FastOutSlowInEasing.transform(rawFrac)

        // Dynamic rotation: rotates by 45 degrees on each shape transition
        val baseRotDeg = currentStage * 45f + t * 45f
        val rotRad = Math.toRadians(baseRotDeg.toDouble()).toFloat()

        // Organic elastic jelly breath/scale during transition
        val scale = 1.0f + 0.08f * kotlin.math.sin(t * Math.PI.toFloat())

        val numPoints = 120
        val path = androidx.compose.ui.graphics.Path()

        for (i in 0 until numPoints) {
            val theta = (i.toFloat() / numPoints.toFloat()) * (2f * Math.PI.toFloat())

            // Radius for shape at currentStage
            val rCurrent = when (currentStage) {
                0 -> 1.0f + 0.24f * kotlin.math.cos(4f * theta) - 0.03f * kotlin.math.cos(8f * theta)
                1 -> {
                    val c = kotlin.math.cos(10f * theta)
                    1.0f + 0.18f * (kotlin.math.sign(c) * Math.pow(kotlin.math.abs(c).toDouble(), 0.72)).toFloat()
                }
                2 -> 1.0f + 0.12f * kotlin.math.cos(9f * theta)
                3 -> 1.0f + 0.18f * kotlin.math.cos(2f * theta)
                else -> {
                    val c = kotlin.math.cos(8f * theta)
                    1.0f + 0.20f * (kotlin.math.sign(c) * Math.pow(kotlin.math.abs(c).toDouble(), 0.75)).toFloat()
                }
            }

            // Radius for shape at nextStage
            val rNext = when (nextStage) {
                0 -> 1.0f + 0.24f * kotlin.math.cos(4f * theta) - 0.03f * kotlin.math.cos(8f * theta)
                1 -> {
                    val c = kotlin.math.cos(10f * theta)
                    1.0f + 0.18f * (kotlin.math.sign(c) * Math.pow(kotlin.math.abs(c).toDouble(), 0.72)).toFloat()
                }
                2 -> 1.0f + 0.12f * kotlin.math.cos(9f * theta)
                3 -> 1.0f + 0.18f * kotlin.math.cos(2f * theta)
                else -> {
                    val c = kotlin.math.cos(8f * theta)
                    1.0f + 0.20f * (kotlin.math.sign(c) * Math.pow(kotlin.math.abs(c).toDouble(), 0.75)).toFloat()
                }
            }

            val morphedR = ((1f - t) * rCurrent + t * rNext) * baseR * scale
            val angle = theta + rotRad
            val px = cx + morphedR * kotlin.math.cos(angle)
            val py = cy + morphedR * kotlin.math.sin(angle)

            if (i == 0) {
                path.moveTo(px, py)
            } else {
                path.lineTo(px, py)
            }
        }
        path.close()

        drawPath(path = path, color = color)
    }
}

/**
 * 🎵 SimpMusic-Style Wavy Circular Progress Ring
 * কভার আর্টের চারপাশের রিং:
 * - গান প্লে হলে সুন্দরভাবে তরঙ্গায়িত (Wavy Arc) হয়ে চলে।
 * - গান পজ থাকলে মসৃণ শান্ত বৃত্তাকার রিং হিসেবে স্থির থাকে।
 * - trackColor = Color.Transparent হওয়ায় কোনো বাড়তি অনাকাঙ্ক্ষিত গোল দাগ দেখায় না।
 */
@Composable
fun LytrixWavyCircularProgress(
    progress: Float,
    isBuffering: Boolean = false,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    trackColor: Color = Color.Transparent,
    strokeWidth: Dp = 2.6.dp,
    numWaves: Int = 8
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wavy_progress_anim")

    // Smooth wave phase shift along the arc when playing
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    // Indeterminate rotation for song loading / buffering state
    val bufferRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1350, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "buffer_rotation"
    )

    // Indeterminate sweep angle expanding & contracting while loading
    val bufferSweep by infiniteTransition.animateFloat(
        initialValue = 70f,
        targetValue = 210f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "buffer_sweep"
    )

    // Amplitude: wavy while playing/buffering, flat (0) when paused
    val targetAmplitude = when {
        isBuffering -> 1.0f
        isPlaying && progress > 0.005f -> 1.0f
        else -> 0f
    }
    val animatedAmplitude by animateFloatAsState(
        targetValue = targetAmplitude,
        animationSpec = tween(350, easing = FastOutSlowInEasing),
        label = "wave_amplitude"
    )

    androidx.compose.foundation.Canvas(modifier = modifier) {
        val strokePx = strokeWidth.toPx()
        val diameter = minOf(size.width, size.height) - strokePx - 2.dp.toPx()
        val radius = diameter / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        // 1. Draw Background Track Ring ONLY if trackColor is explicitly provided
        if (trackColor != Color.Transparent && trackColor.alpha > 0.02f) {
            drawCircle(
                color = trackColor,
                radius = radius,
                center = center,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
        }

        // 2. Determine sweep angle and starting angle
        val currentSweep = if (isBuffering) bufferSweep else (progress * 360f).coerceIn(0f, 360f)
        val startAngle = if (isBuffering) bufferRotation else -90f

        if (currentSweep > 1f) {
            if (animatedAmplitude > 0.08f) {
                // Wavy Arc while playing or buffering (SimpMusic CircularWavyProgressIndicator style)
                val path = androidx.compose.ui.graphics.Path()
                val stepCount = (currentSweep * 1.5f).toInt().coerceAtLeast(24)
                val angleStep = currentSweep / stepCount
                val waveAmpPx = strokePx * 0.48f * animatedAmplitude

                for (i in 0..stepCount) {
                    val angleDeg = startAngle + i * angleStep
                    val angleRad = Math.toRadians(angleDeg.toDouble())
                    val waveOffset = (waveAmpPx * kotlin.math.sin(numWaves * angleRad - wavePhase)).toFloat()
                    val r = radius + waveOffset
                    val x = center.x + r * kotlin.math.cos(angleRad).toFloat()
                    val y = center.y + r * kotlin.math.sin(angleRad).toFloat()

                    if (i == 0) {
                        path.moveTo(x, y)
                    } else {
                        path.lineTo(x, y)
                    }
                }

                drawPath(
                    path = path,
                    color = color,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            } else {
                // Flat Clean Arc when paused
                drawArc(
                    color = color,
                    startAngle = startAngle,
                    sweepAngle = currentSweep,
                    useCenter = false,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round),
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
                )
            }
        }
    }
}