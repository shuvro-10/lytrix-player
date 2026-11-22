package com.musicplayer.lytrix.ui.component

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.launch

/**
 * 🌊 LytrixFluidBottomSheet — Lytrix / ViMusic / Meld আর্কিটেকচারে তৈরি ১০০% ফ্লুইড বটমশিট।
 * 
 * ১. কোন প্রকার লাফালাফি (Jitter/Bouncing) নেই: গুগলের ত্রুটিপূর্ণ ড্র্যাগ অ্যাঙ্কর বাদ দিয়ে
 *    সরাসরি টাচ অফসেট (1:1 Physics Translation) ট্র্যাক করে।
 * ২. নিচে কোন ফাঁকা অংশ (Empty Void) নেই: ছোট শিটে কনটেন্ট যতটুকু (যেমন স্লিপ টাইমার ২৫০ ডিপি),
 *    শিট ঠিক ততটুকুই লম্বা হবে।
 * ৩. Two-Stage (Half-Screen & Full-Screen) সাপোর্ট:
 *    [enableTwoStage] = true দিলে শিটটি প্রথমে হাফ স্ক্রিনে (~৫৫%) খুলবে এবং আঙুল দিয়ে
 *    টেনে ফুল স্ক্রিনে (~৯০%) বড় করা যাবে।
 */
@Composable
fun LytrixFluidBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetKey: Any? = null,
    enableTwoStage: Boolean = false,
    allowContentDrag: Boolean = true,
    halfFraction: Float = 0.55f,
    expandedFraction: Float = 0.90f,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    shape: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    dragHandle: @Composable (() -> Unit)? = {
        Box(
            modifier = Modifier
                .padding(vertical = 12.dp)
                .size(width = 40.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        )
    },
    content: @Composable ColumnScope.() -> Unit
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    
    val screenHeightDp = configuration.screenHeightDp.dp
    val screenHeightPx = with(density) { screenHeightDp.toPx() }
    val maxHeightDp = screenHeightDp * expandedFraction
    val maxHeightPx = screenHeightPx * expandedFraction
    
    // Two-stage এর জন্য হাফ-স্ক্রিন ও এক্সপ্যান্ডেড ক্যালকুলেশন
    val halfOffsetY = if (enableTwoStage) {
        val halfHeightPx = screenHeightPx * halfFraction
        (maxHeightPx - halfHeightPx).coerceAtLeast(0f)
    } else {
        0f
    }

    // প্রারম্ভিক অফসেট (নিচে থেকে উপরে ওঠার জন্য)
    var sheetHeightPx by remember { mutableFloatStateOf(maxHeightPx) }
    val initialOffset = if (enableTwoStage) maxHeightPx else maxHeightPx
    val offsetY = remember { Animatable(initialOffset) }
    var activeAnimJob by remember { androidx.compose.runtime.mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var dragStartOffset by remember { androidx.compose.runtime.mutableStateOf<Float?>(null) }

    fun dismissSmoothly() {
        activeAnimJob?.cancel()
        activeAnimJob = coroutineScope.launch {
            val target = if (enableTwoStage) maxHeightPx else sheetHeightPx.coerceAtLeast(maxHeightPx)
            offsetY.animateTo(
                targetValue = target,
                animationSpec = tween(durationMillis = 220, easing = FastOutLinearInEasing)
            )
            onDismissRequest()
        }
    }

    fun settlePosition(velocityY: Float) {
        activeAnimJob?.cancel()
        val startOffset = dragStartOffset
        dragStartOffset = null
        activeAnimJob = coroutineScope.launch {
            if (enableTwoStage) {
                val currentOffset = offsetY.value
                val dragThresholdPx = with(density) { 24.dp.toPx() }
                val dismissThresholdPx = with(density) { 36.dp.toPx() }

                val startedNearFull = startOffset != null && startOffset <= halfOffsetY * 0.25f
                val startedNearHalf = startOffset != null && startOffset >= halfOffsetY * 0.75f

                when {
                    // ১. ফ্লিক ভেলোসিটি হ্যান্ডলিং
                    velocityY < -180f -> {
                        // যেকোনো স্থান থেকে হালকা উপরে ফ্লিক -> ফুল স্ক্রিন
                        offsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.85f))
                    }
                    velocityY > 200f -> {
                        // নিচে ফ্লিক
                        if (startedNearFull || currentOffset < halfOffsetY * 0.70f) {
                            // ফুল স্ক্রিনে থাকা অবস্থা থেকে নিচে ফ্লিক -> হাফ স্ক্রিন
                            offsetY.animateTo(halfOffsetY, spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.85f))
                        } else {
                            // হাফ স্ক্রিন বা নিচে থাকলে ডিসমিস
                            dismissSmoothly()
                        }
                    }
                    // ২. ফুল স্ক্রিন থেকে ধীর গতির ড্র্যাগ
                    startedNearFull -> {
                        if (currentOffset > dragThresholdPx) {
                            // সামান্য নিচে নামালেই হাফ স্ক্রিনে নামবে
                            offsetY.animateTo(halfOffsetY, spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.85f))
                        } else {
                            offsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.85f))
                        }
                    }
                    // ৩. হাফ স্ক্রিন থেকে ধীর গতির ড্র্যাগ
                    startedNearHalf -> {
                        if (currentOffset < halfOffsetY - dragThresholdPx) {
                            // সামান্য উপরে টানলেই ফুল স্ক্রিনে যাবে
                            offsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.85f))
                        } else if (currentOffset > halfOffsetY + dismissThresholdPx) {
                            // নিচে টানলে ডিসমিস হবে
                            dismissSmoothly()
                        } else {
                            offsetY.animateTo(halfOffsetY, spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.85f))
                        }
                    }
                    // ৪. সাধারণ মিডপয়েন্ট ব্যালান্স ফলব্যাক
                    else -> {
                        if (currentOffset <= halfOffsetY * 0.50f) {
                            offsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.85f))
                        } else if (currentOffset <= halfOffsetY + dismissThresholdPx) {
                            offsetY.animateTo(halfOffsetY, spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.85f))
                        } else {
                            dismissSmoothly()
                        }
                    }
                }
            } else {
                // ১-স্টেজ র‍্যাপ কনটেন্ট মোড (Equalizer বা সাধারণ ১-স্টেজ শিট)
                // দুর্ঘটনাবশত সামান্য স্ক্রলে বা স্লাইডার নাড়ালে যাতে ক্লোজ না হয়:
                val dismissDistanceThreshold = with(density) { 120.dp.toPx() }
                val dismissFractionThreshold = if (sheetHeightPx > 0f) sheetHeightPx * 0.35f else dismissDistanceThreshold
                val minDragForFlingDismiss = with(density) { 36.dp.toPx() }

                // কেবল শিটটি অন্তত ৩৬dp নিচে টানা থাকলে এবং নিচের দিকে তীব্র ফ্লিক করলে,
                // অথবা ১২০dp/৩৫% পার করলে তবেই ক্লোজ হবে।
                val shouldDismiss = (offsetY.value > minDragForFlingDismiss && velocityY > 800f) ||
                                    offsetY.value > dismissDistanceThreshold ||
                                    offsetY.value > dismissFractionThreshold

                if (shouldDismiss) {
                    dismissSmoothly()
                } else {
                    offsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.85f))
                }
            }
        }
    }

    // প্রথমবার ওপেন হওয়া ও শিট টাইপ পরিবর্তনের স্মুথ এন্ট্রান্স অ্যানিমেশন
    LaunchedEffect(sheetKey, enableTwoStage, halfOffsetY) {
        val target = if (enableTwoStage) halfOffsetY else 0f
        offsetY.animateTo(
            targetValue = target,
            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
        )
    }

    Dialog(
        onDismissRequest = { dismissSmoothly() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        // ফুল স্ক্রিন ডেকোর ও ট্রান্সপারেন্ট স্ট্যাটাস/ন্যাভ বার সাপোর্ট
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            dialogWindow?.let { win ->
                win.setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT
                )
                win.setFlags(
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                )
                win.statusBarColor = android.graphics.Color.TRANSPARENT
                win.navigationBarColor = android.graphics.Color.TRANSPARENT
                win.setDimAmount(0f) // কাস্টম ফ্লুইড স্ক্রিম নিচে ড্র করা হবে
            }
        }

        BackHandler {
            if (enableTwoStage && offsetY.value < halfOffsetY - 50f) {
                coroutineScope.launch {
                    offsetY.animateTo(halfOffsetY, spring(stiffness = Spring.StiffnessMediumLow))
                }
            } else {
                dismissSmoothly()
            }
        }

        val scrimAlpha by remember {
            derivedStateOf {
                if (enableTwoStage) {
                    ((1f - (offsetY.value / maxHeightPx)).coerceIn(0f, 1f) * 0.55f)
                } else {
                    if (sheetHeightPx <= 0f) 0.55f
                    else ((1f - (offsetY.value / sheetHeightPx)).coerceIn(0f, 1f) * 0.55f)
                }
            }
        }

        // নেস্টেড স্ক্রোল হ্যান্ডলার (লিস্ট স্ক্রোল ও শিট ড্র্যাগের মধ্যে নিখুঁত সমন্বয়)
        val nestedScrollConnection = remember(enableTwoStage, halfOffsetY, sheetHeightPx) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    // স্ক্রিনের শিট যদি ফুল স্ক্রিন না থাকে (offsetY > 0) এবং ইউজার উপরে সোয়াইপ করছে (available.y < 0)
                    if (available.y < 0f && offsetY.value > 0f) {
                        activeAnimJob?.cancel()
                        if (dragStartOffset == null) dragStartOffset = offsetY.value
                        val newOffset = (offsetY.value + available.y).coerceAtLeast(0f)
                        val consumedY = newOffset - offsetY.value
                        coroutineScope.launch { offsetY.snapTo(newOffset) }
                        return Offset(0f, consumedY)
                    }
                    return Offset.Zero
                }

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource
                ): Offset {
                    // লিস্ট টপে থাকার সময় ইউজার যদি নিচে টানে (available.y > 0)
                    if (available.y > 0f && source == NestedScrollSource.UserInput) {
                        activeAnimJob?.cancel()
                        if (dragStartOffset == null) dragStartOffset = offsetY.value
                        val newOffset = (offsetY.value + available.y).coerceAtLeast(0f)
                        val consumedY = newOffset - offsetY.value
                        coroutineScope.launch { offsetY.snapTo(newOffset) }
                        return Offset(0f, consumedY)
                    }
                    return Offset.Zero
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (offsetY.value > 0f) {
                        settlePosition(available.y)
                        return available
                    }
                    return Velocity.Zero
                }

                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                    if (offsetY.value > 0f) {
                        settlePosition(available.y)
                        return available
                    }
                    return Velocity.Zero
                }
            }
        }

        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // ১. ব্যাকড্রপ স্ক্রিম (বাইরে ট্যাপ করলে বন্ধ হবে)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { dismissSmoothly() }
                    )
            )

            // ২. ফ্লুইড বটমশিট কন্টেইনার
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(nestedScrollConnection),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = modifier
                        .fillMaxWidth()
                        .then(
                            if (enableTwoStage) {
                                Modifier.height(maxHeightDp)
                            } else {
                                Modifier.heightIn(max = maxHeightDp)
                            }
                        )
                        .onGloballyPositioned { layoutCoordinates ->
                            val height = layoutCoordinates.size.height.toFloat()
                            if (height > 0f && sheetHeightPx != height) {
                                sheetHeightPx = height
                            }
                        }
                        .graphicsLayer {
                            translationY = offsetY.value
                        }
                        .clip(shape)
                        .background(containerColor)
                        .then(
                            if (allowContentDrag) {
                                Modifier.pointerInput(Unit) {
                                    val velocityTracker = VelocityTracker()
                                    detectVerticalDragGestures(
                                        onDragStart = {
                                            activeAnimJob?.cancel()
                                            dragStartOffset = offsetY.value
                                            velocityTracker.resetTracking()
                                        },
                                        onDragEnd = {
                                            val velocity = velocityTracker.calculateVelocity().y
                                            settlePosition(velocity)
                                        },
                                        onDragCancel = {
                                            settlePosition(0f)
                                        },
                                        onVerticalDrag = { change, dragAmount ->
                                            activeAnimJob?.cancel()
                                            velocityTracker.addPointerInputChange(change)
                                            val newOffset = (offsetY.value + dragAmount).coerceAtLeast(0f)
                                            coroutineScope.launch {
                                                offsetY.snapTo(newOffset)
                                            }
                                            if (dragAmount > 0f || offsetY.value > 0f) {
                                                change.consume()
                                            }
                                        }
                                    )
                                }
                            } else Modifier
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (enableTwoStage) Modifier.fillMaxHeight() else Modifier)
                            .windowInsetsPadding(WindowInsets.navigationBars),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // ড্র্যাগ হ্যান্ডেল এরিয়া (সর্বদা হ্যান্ডেলে টান দিয়ে শিট সরানো যাবে)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pointerInput(Unit) {
                                    val velocityTracker = VelocityTracker()
                                    detectVerticalDragGestures(
                                        onDragStart = {
                                            activeAnimJob?.cancel()
                                            dragStartOffset = offsetY.value
                                            velocityTracker.resetTracking()
                                        },
                                        onDragEnd = {
                                            val velocity = velocityTracker.calculateVelocity().y
                                            settlePosition(velocity)
                                        },
                                        onDragCancel = {
                                            settlePosition(0f)
                                        },
                                        onVerticalDrag = { change, dragAmount ->
                                            activeAnimJob?.cancel()
                                            velocityTracker.addPointerInputChange(change)
                                            val newOffset = (offsetY.value + dragAmount).coerceAtLeast(0f)
                                            coroutineScope.launch {
                                                offsetY.snapTo(newOffset)
                                            }
                                            if (dragAmount > 0f || offsetY.value > 0f) {
                                                change.consume()
                                            }
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            dragHandle?.invoke()
                        }
                        content()
                    }
                }
            }
        }
    }
}
