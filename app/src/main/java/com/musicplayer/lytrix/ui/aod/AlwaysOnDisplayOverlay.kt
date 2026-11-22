package com.musicplayer.lytrix.ui.aod

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.BitmapDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.os.BatteryManager
import android.service.dreams.DreamService
import android.text.format.DateFormat
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.musicplayer.lytrix.aod.AodPreferences
import com.musicplayer.lytrix.ui.LytrixLyricLine
import com.musicplayer.lytrix.ui.parseLytrixLrcStrict
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Lytrix-inspired pure AMOLED Always-On Display with palette artwork ambient glow,
 * Pixel-Stacked / Digital clock widget, synced lyrics ticker, tactile Slide-to-Lock, and
 * dedicated Pocket Touch Lock unlock behavior.
 */
@Composable
fun AlwaysOnDisplayOverlay(
    title: String,
    artist: String,
    album: String,
    artworkData: Any?,
    lyricsText: String?,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    canSkipPrev: Boolean = true,
    canSkipNext: Boolean = true,
    accentColor: Color,
    initialLocked: Boolean = false,
    onPlayPause: () -> Unit,
    onSkipPrev: () -> Unit,
    onSkipNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    var config by remember { mutableStateOf(AodPreferences.read(context)) }

    // Remember whether we entered specifically as Pocket Touch Lock so unlocking returns directly to player.
    val startedAsTouchLock = remember(initialLocked) { initialLocked }
    var isLocked by remember(initialLocked) { mutableStateOf(initialLocked) }
    var isPocketCovered by remember { mutableStateOf(false) }
    var isDimmed by remember { mutableStateOf(false) }
    var lastInteractionMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var sliderDragMs by remember { mutableStateOf<Long?>(null) }

    fun resetInteraction() {
        lastInteractionMs = System.currentTimeMillis()
        if (isDimmed) isDimmed = false
    }

    BackHandler(enabled = true) {
        if (isLocked) {
            resetInteraction()
        } else {
            onExit()
        }
    }

    // Subtle AMOLED burn-in pixel shift (Lytrix shifts every 60s smoothly)
    var targetPixelShift by remember { mutableStateOf(IntOffset.Zero) }
    val pixelShiftOffset by animateIntOffsetAsState(
        targetValue = targetPixelShift,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "aodPixelShift"
    )
    LaunchedEffect(config.pixelShiftEnabled) {
        if (!config.pixelShiftEnabled) {
            targetPixelShift = IntOffset.Zero
            return@LaunchedEffect
        }
        val shifts = listOf(
            IntOffset(0, 0), IntOffset(8, 4), IntOffset(-8, -4),
            IntOffset(4, -8), IntOffset(-6, 6), IntOffset(6, -6)
        )
        var index = 0
        while (true) {
            delay(60_000L)
            index = (index + 1) % shifts.size
            targetPixelShift = shifts[index]
        }
    }

    // Gentle inactivity ambient dim ONLY (never forces Touch Lock automatically on AOD!)
    LaunchedEffect(lastInteractionMs, isDimmed) {
        if (isDimmed) return@LaunchedEffect
        delay(10_000L)
        isDimmed = true
    }

    // Proximity pocket blackout: darkens screen while inside pocket without overriding unlocked AOD state.
    DisposableEffect(config.proximityPocketLock) {
        if (!config.proximityPocketLock) return@DisposableEffect onDispose {}
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val proximity = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        if (sensorManager == null || proximity == null) return@DisposableEffect onDispose {}

        val threshold = (proximity.maximumRange * 0.15f).coerceAtLeast(1.0f)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val dist = event.values.firstOrNull() ?: return
                isPocketCovered = proximity.maximumRange > 0f && dist <= threshold
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensorManager.registerListener(listener, proximity, SensorManager.SENSOR_DELAY_NORMAL)
        onDispose { sensorManager.unregisterListener(listener) }
    }

    // Low AMOLED brightness control while active
    DisposableEffect(isDimmed, isPocketCovered) {
        val window = (context as? Activity)?.window ?: (context as? DreamService)?.window
        window?.let { w ->
            val lp = w.attributes
            lp.screenBrightness = when {
                isPocketCovered && config.proximityPocketLock -> 0.001f
                isDimmed -> 0.08f
                else -> 0.22f
            }
            w.attributes = lp
        }
        onDispose {
            val w = (context as? Activity)?.window ?: (context as? DreamService)?.window
            w?.let {
                val lp = it.attributes
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                it.attributes = lp
            }
        }
    }

    // Dynamic Palette artwork color extraction
    var extractedArtworkColor by remember { mutableStateOf<Color?>(null) }
    LaunchedEffect(artworkData) {
        if (artworkData == null) {
            extractedArtworkColor = null
            return@LaunchedEffect
        }
        val color = withContext(Dispatchers.IO) {
            runCatching {
                val req = ImageRequest.Builder(context)
                    .data(artworkData)
                    .size(160, 160)
                    .allowHardware(false)
                    .build()
                val res = context.imageLoader.execute(req)
                val bmp = ((res as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap ?: return@runCatching null
                val pal = Palette.from(bmp).maximumColorCount(12).generate()
                val rgb = pal.vibrantSwatch?.rgb
                    ?: pal.lightVibrantSwatch?.rgb
                    ?: pal.dominantSwatch?.rgb
                rgb?.let { Color(it) }
            }.getOrNull()
        }
        extractedArtworkColor = color
    }

    val effectiveAccent by animateColorAsState(
        targetValue = extractedArtworkColor ?: accentColor,
        animationSpec = tween(800),
        label = "aodAccent"
    )

    val contentAlpha by animateFloatAsState(
        targetValue = if (isDimmed) 0.42f else 1.0f,
        animationSpec = tween(500),
        label = "aodDimAlpha"
    )

    val lyricsPrefs = remember { context.getSharedPreferences("lyrics_prefs", Context.MODE_PRIVATE) }
    val parsedLines = remember(lyricsText) {
        if (lyricsText.isNullOrBlank()) emptyList() else parseLytrixLrcStrict(lyricsText)
    }
    val offsetMs = lyricsPrefs.getLong("${title}_${artist}_offset_ms", 0L)
    val activeLyricLine = remember(parsedLines, positionMs, offsetMs) {
        findActiveAodLyricLine(parsedLines, positionMs - offsetMs)
    }

    val bgBrush = remember(effectiveAccent, config.ambientGlowEnabled) {
        if (config.ambientGlowEnabled) {
            Brush.radialGradient(
                colors = listOf(
                    effectiveAccent.copy(alpha = 0.20f),
                    effectiveAccent.copy(alpha = 0.05f),
                    Color.Black
                )
            )
        } else {
            Brush.verticalGradient(listOf(Color.Black, Color.Black))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .background(bgBrush)
            .pointerInput(isLocked) {
                detectTapGestures(
                    onTap = { resetInteraction() },
                    onDoubleTap = {
                        resetInteraction()
                        if (!isLocked) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onPlayPause()
                        }
                    }
                )
            }
            .pointerInput(isLocked) {
                if (!isLocked) {
                    var accumVertical = 0f
                    detectVerticalDragGestures(
                        onDragStart = {
                            resetInteraction()
                            accumVertical = 0f
                        },
                        onVerticalDrag = { _, dragAmount ->
                            resetInteraction()
                            accumVertical += dragAmount
                            if (abs(accumVertical) > 40f) {
                                val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                                val dir = if (accumVertical < 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
                                am?.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI)
                                accumVertical = 0f
                            }
                        },
                        onDragEnd = { resetInteraction() }
                    )
                }
            }
    ) {
        // Minimal top-right close icon button (Lytrix style)
        AnimatedVisibility(
            visible = !isLocked,
            enter = fadeIn(tween(250)),
            exit = fadeOut(tween(250)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .safeDrawingPadding()
                .padding(8.dp)
        ) {
            IconButton(
                onClick = {
                    resetInteraction()
                    onExit()
                }
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Exit Always-On Display",
                    tint = Color.White.copy(alpha = 0.72f)
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            modifier = Modifier
                .fillMaxSize()
                .offset { pixelShiftOffset }
                .alpha(contentAlpha)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 32.dp, vertical = 24.dp)
        ) {
            // Tap clock to cycle Pixel Stacked -> Bold Digital -> Minimal -> Elegant Thin
            AodClockWidget(
                clockStyle = config.clockStyle,
                accentColor = effectiveAccent,
                modifier = Modifier.clickable(enabled = !isLocked) {
                    resetInteraction()
                    val order = listOf(
                        AodPreferences.CLOCK_PIXEL_STACKED,
                        AodPreferences.CLOCK_BOLD_DIGITAL,
                        AodPreferences.CLOCK_MINIMAL,
                        AodPreferences.CLOCK_ELEGANT_THIN
                    )
                    val next = order[(order.indexOf(config.clockStyle) + 1) % order.size]
                    AodPreferences.setClockStyle(context, next)
                    config = AodPreferences.read(context)
                }
            )

            // Artwork with Lytrix ambient shadow glow
            val artworkSize = 216.dp
            val artworkShape = RoundedCornerShape(28.dp)
            Box(
                modifier = Modifier
                    .size(artworkSize)
                    .shadow(
                        elevation = 24.dp,
                        shape = artworkShape,
                        clip = false,
                        ambientColor = effectiveAccent,
                        spotColor = effectiveAccent
                    )
                    .clip(artworkShape)
                    .background(Color.White.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center
            ) {
                if (artworkData != null) {
                    AsyncImage(
                        model = artworkData,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.45f),
                        modifier = Modifier.size(64.dp)
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title.ifBlank { "No Track Playing" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .basicMarquee()
                )
                if (artist.isNotBlank()) {
                    Text(
                        text = if (album.isNotBlank()) "$artist · $album" else artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (config.showLyricsTicker && !activeLyricLine.isNullOrBlank()) {
                    Text(
                        text = activeLyricLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = effectiveAccent.copy(alpha = 0.90f),
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .basicMarquee()
                    )
                }
            }

            AnimatedVisibility(
                visible = !isLocked && !isDimmed,
                enter = fadeIn(tween(250)),
                exit = fadeOut(tween(250))
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val safeDuration = durationMs.coerceAtLeast(1L)
                    val shownPos = sliderDragMs ?: positionMs.coerceIn(0L, safeDuration)
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Slider(
                            value = shownPos.toFloat(),
                            onValueChange = {
                                resetInteraction()
                                sliderDragMs = it.toLong()
                            },
                            onValueChangeFinished = {
                                resetInteraction()
                                sliderDragMs?.let { onSeek(it) }
                                sliderDragMs = null
                            },
                            valueRange = 0f..safeDuration.toFloat(),
                            enabled = durationMs > 0L,
                            colors = SliderDefaults.colors(
                                thumbColor = effectiveAccent,
                                activeTrackColor = effectiveAccent,
                                inactiveTrackColor = Color.White.copy(alpha = 0.28f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp)
                        ) {
                            Text(formatAodTime(shownPos), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.65f))
                            Text(formatAodTime(durationMs), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.65f))
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(
                            onClick = {
                                resetInteraction()
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSkipPrev()
                            },
                            enabled = canSkipPrev,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SkipPrevious,
                                contentDescription = "Previous",
                                tint = if (canSkipPrev) Color.White else Color.White.copy(alpha = 0.35f),
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        val onPlayTint = if (effectiveAccent.luminance() > 0.55f) Color.Black else Color.White
                        FilledIconButton(
                            onClick = {
                                resetInteraction()
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onPlayPause()
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = effectiveAccent,
                                contentColor = onPlayTint
                            ),
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                resetInteraction()
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSkipNext()
                            },
                            enabled = canSkipNext,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SkipNext,
                                contentDescription = "Next",
                                tint = if (canSkipNext) Color.White else Color.White.copy(alpha = 0.35f),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    // Lytrix tactile Slide-to-Lock pill
                    AodSlideToLockButton(
                        accentColor = effectiveAccent,
                        onLock = {
                            resetInteraction()
                            isLocked = true
                        },
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }

        if (isPocketCovered && config.proximityPocketLock) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .zIndex(999f)
                    .pointerInput(Unit) { detectTapGestures { } }
            )
        }

        AodTouchLockOverlay(
            isLocked = isLocked,
            unlockMethod = config.unlockMethod,
            accentColor = effectiveAccent,
            isPocketLockMode = startedAsTouchLock,
            onUnlock = {
                if (startedAsTouchLock) {
                    onExit()
                } else {
                    isLocked = false
                    resetInteraction()
                }
            }
        )
    }
}

@Composable
private fun AodClockWidget(
    clockStyle: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var formattedTime by remember { mutableStateOf("") }
    var formattedDate by remember { mutableStateOf("") }
    var formattedHours by remember { mutableStateOf("") }
    var formattedMinutes by remember { mutableStateOf("") }
    var batteryLevel by remember { mutableIntStateOf(-1) }
    var isCharging by remember { mutableStateOf(false) }

    fun updateClock() {
        val now = Date()
        val is24Hour = DateFormat.is24HourFormat(context)
        val timePattern = if (clockStyle == AodPreferences.CLOCK_PIXEL_STACKED) {
            if (is24Hour) "HH:mm" else "h:mm"
        } else {
            if (is24Hour) "HH:mm" else "h:mm a"
        }
        formattedTime = SimpleDateFormat(timePattern, Locale.getDefault()).format(now)
        formattedDate = SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(now)
        formattedHours = SimpleDateFormat(if (is24Hour) "HH" else "hh", Locale.getDefault()).format(now)
        formattedMinutes = SimpleDateFormat("mm", Locale.getDefault()).format(now)
    }

    LaunchedEffect(clockStyle) {
        while (true) {
            updateClock()
            delay(1000L)
        }
    }

    DisposableEffect(context, clockStyle) {
        updateClock()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_TIME_TICK,
                    Intent.ACTION_TIME_CHANGED,
                    Intent.ACTION_TIMEZONE_CHANGED -> updateClock()
                    Intent.ACTION_BATTERY_CHANGED -> {
                        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                        if (level >= 0 && scale > 0) batteryLevel = (level * 100) / scale
                        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                        isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == BatteryManager.BATTERY_STATUS_FULL
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        val initialIntent = ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        if (initialIntent != null) {
            val level = initialIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = initialIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level >= 0 && scale > 0) batteryLevel = (level * 100) / scale
            val status = initialIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        }
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.padding(vertical = 4.dp)
    ) {
        if (clockStyle == AodPreferences.CLOCK_PIXEL_STACKED) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formattedHours,
                    fontSize = 58.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 54.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = formattedMinutes,
                    fontSize = 58.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 54.sp,
                    color = accentColor.copy(alpha = 0.88f),
                    textAlign = TextAlign.Center
                )
            }
        } else if (formattedTime.isNotBlank()) {
            val (fontSize, fontWeight, fontFamily) = when (clockStyle) {
                AodPreferences.CLOCK_BOLD_DIGITAL -> Triple(44.sp, FontWeight.Black, FontFamily.Monospace)
                AodPreferences.CLOCK_ELEGANT_THIN -> Triple(48.sp, FontWeight.ExtraLight, FontFamily.SansSerif)
                else -> Triple(38.sp, FontWeight.Medium, FontFamily.Default)
            }
            Text(
                text = formattedTime,
                fontSize = fontSize,
                fontWeight = fontWeight,
                fontFamily = fontFamily,
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }

        if (formattedDate.isNotBlank()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.65f)
                )
                if (batteryLevel >= 0) {
                    Icon(
                        imageVector = when {
                            isCharging -> Icons.Default.BatteryChargingFull
                            batteryLevel >= 90 -> Icons.Default.BatteryFull
                            else -> Icons.Default.BatteryStd
                        },
                        contentDescription = null,
                        tint = if (isCharging) accentColor else Color.White.copy(alpha = 0.65f),
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "$batteryLevel%",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isCharging) accentColor else Color.White.copy(alpha = 0.65f)
                    )
                }
            }
        }
    }
}

@Composable
private fun AodSlideToLockButton(
    accentColor: Color,
    onLock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val maxSlidePx = remember(density) { with(density) { 210.dp.toPx() } }
    var slideOffsetPx by remember { mutableFloatStateOf(0f) }

    val animatedSlideOffset by animateFloatAsState(
        targetValue = slideOffsetPx,
        animationSpec = tween(durationMillis = if (slideOffsetPx == 0f) 200 else 0),
        label = "lockSlideOffset"
    )
    val knobIconTint = if (accentColor.luminance() > 0.55f) Color.Black else Color.White

    Box(
        modifier = modifier
            .width(260.dp)
            .height(50.dp)
            .clip(RoundedCornerShape(25.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .pointerInput(maxSlidePx) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (slideOffsetPx >= maxSlidePx * 0.70f) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLock()
                        }
                        slideOffsetPx = 0f
                    },
                    onDragCancel = { slideOffsetPx = 0f },
                    onHorizontalDrag = { _, dragAmount ->
                        slideOffsetPx = (slideOffsetPx + dragAmount).coerceIn(0f, maxSlidePx)
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = "Slide to Lock",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White.copy(alpha = 0.65f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Box(
            modifier = Modifier
                .offset { IntOffset(animatedSlideOffset.roundToInt(), 0) }
                .padding(3.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(accentColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = knobIconTint,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun AodTouchLockOverlay(
    isLocked: Boolean,
    unlockMethod: String,
    accentColor: Color,
    isPocketLockMode: Boolean,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!isLocked) return

    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val maxSlidePx = remember(density) { with(density) { 204.dp.toPx() } }
    var slideOffsetPx by remember { mutableFloatStateOf(0f) }
    var holdProgress by remember { mutableFloatStateOf(0f) }
    var isHolding by remember { mutableStateOf(false) }

    val animatedSlideOffset by animateFloatAsState(
        targetValue = slideOffsetPx,
        animationSpec = if (slideOffsetPx == 0f) {
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
        } else {
            snap()
        },
        label = "slideUnlockOffset"
    )
    val knobIconTint = if (accentColor.luminance() > 0.55f) Color.Black else Color.White

    LaunchedEffect(isHolding) {
        if (isHolding) {
            val startTime = System.currentTimeMillis()
            val holdDurationMs = 1500L
            while (isHolding && holdProgress < 1f) {
                val elapsed = System.currentTimeMillis() - startTime
                holdProgress = (elapsed.toFloat() / holdDurationMs).coerceIn(0f, 1f)
                if (holdProgress >= 1f) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onUnlock()
                    break
                }
                delay(16L)
            }
        } else {
            holdProgress = 0f
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(50f)
            .background(Color.Black.copy(alpha = 0.72f))
            .pointerInput(Unit) { detectTapGestures { } }
            .pointerInput(Unit) { detectHorizontalDragGestures { _, _ -> } }
            .pointerInput(Unit) { detectVerticalDragGestures { _, _ -> } }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 56.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = if (isPocketLockMode) "Pocket Touch Lock" else "Screen Locked",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.65f),
                modifier = Modifier.padding(bottom = 16.dp)
            )
            when (unlockMethod) {
                AodPreferences.UNLOCK_HOLD -> {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.14f))
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        isHolding = true
                                        tryAwaitRelease()
                                        isHolding = false
                                    }
                                )
                            }
                    ) {
                        if (holdProgress > 0f) {
                            CircularProgressIndicator(
                                progress = { holdProgress },
                                modifier = Modifier.size(72.dp),
                                color = accentColor,
                                strokeWidth = 4.dp
                            )
                        }
                        Icon(
                            imageVector = if (holdProgress >= 1f) Icons.Default.LockOpen else Icons.Default.Lock,
                            contentDescription = "Hold to unlock",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (isPocketLockMode) "Hold 1.5s to return to player" else "Hold 1.5s to unlock",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                }

                else -> {
                    Box(
                        modifier = Modifier
                            .width(260.dp)
                            .height(56.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(Color.White.copy(alpha = 0.18f))
                            .pointerInput(maxSlidePx) {
                                detectHorizontalDragGestures(
                                    onDragEnd = {
                                        if (slideOffsetPx >= maxSlidePx * 0.70f) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onUnlock()
                                        }
                                        slideOffsetPx = 0f
                                    },
                                    onDragCancel = { slideOffsetPx = 0f },
                                    onHorizontalDrag = { _, dragAmount ->
                                        slideOffsetPx = (slideOffsetPx + dragAmount).coerceIn(0f, maxSlidePx)
                                    }
                                )
                            },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = if (isPocketLockMode) "Slide to return" else "Slide to unlock",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White.copy(alpha = 0.78f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Box(
                            modifier = Modifier
                                .offset { IntOffset(animatedSlideOffset.roundToInt(), 0) }
                                .padding(4.dp)
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(accentColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LockOpen,
                                contentDescription = "Unlock",
                                tint = knobIconTint,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun findActiveAodLyricLine(lines: List<LytrixLyricLine>, positionMs: Long): String? {
    if (lines.isEmpty()) return null
    val timed = lines.filter { it.startTimeMs >= 0L }
    if (timed.isEmpty()) return lines.firstOrNull()?.text
    val idx = timed.indexOfLast { it.startTimeMs <= positionMs }
    return if (idx >= 0) timed[idx].text else timed.firstOrNull()?.text
}

private fun formatAodTime(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return String.format(Locale.getDefault(), "%02d:%02d", min, sec)
}
