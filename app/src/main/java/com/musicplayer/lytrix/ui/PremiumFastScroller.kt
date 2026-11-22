package com.musicplayer.lytrix.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import compose.icons.TablerIcons
import compose.icons.tablericons.ChevronDown
import compose.icons.tablericons.ChevronUp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun PremiumFastScroller(
    listState: LazyListState,
    accentColor: Color,
    currentTheme: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var isDragging by remember { mutableStateOf(false) }
    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    val isVisible = listState.isScrollInProgress || isDragging
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 300), label = "scrollbar_alpha"
    )

    val layoutInfo = listState.layoutInfo
    val totalItems = layoutInfo.totalItemsCount
    val visibleItems = layoutInfo.visibleItemsInfo.size
    val maxIndex = (totalItems - visibleItems).coerceAtLeast(0)
    val firstIndex = listState.firstVisibleItemIndex

    val scrollProportion = if (maxIndex > 0) firstIndex.toFloat() / maxIndex.toFloat() else 0f

    val isNeon = currentTheme == "Neon Gradient"

    val thumbBg by animateColorAsState(
        targetValue = if (isDragging) (if (isNeon) Color(0xFF00F2FE) else accentColor) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
        animationSpec = tween(200), label = "thumb_bg"
    )
    val iconTint by animateColorAsState(
        targetValue = if (isDragging) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(200), label = "icon_tint"
    )

    Box(modifier = modifier.fillMaxSize()) {
        content() // মেইন লিস্ট

        if (totalItems > 15) {
            BoxWithConstraints(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .fillMaxHeight()
                    .width(32.dp)
            ) {
                val thumbHeightDp = 64.dp
                val maxOffsetPx = with(LocalDensity.current) { (maxHeight - thumbHeightDp).toPx() }
                val currentOffsetY = (maxOffsetPx * scrollProportion).coerceIn(0f, maxOffsetPx)

                // 🔥 জাদুকরী লাইন: যেকোনো পজিশন থেকে কোনো জাম্প ছাড়া বর্তমান লাইভ স্ক্রল রিড করার জন্য
                val currentOffsetYState by rememberUpdatedState(currentOffsetY)
                val maxOffsetPxState by rememberUpdatedState(maxOffsetPx)
                val maxIndexState by rememberUpdatedState(maxIndex)

                Box(
                    modifier = Modifier
                        .offset(y = with(LocalDensity.current) { currentOffsetY.toDp() })
                        .align(Alignment.TopEnd)
                        .padding(end = 4.dp)
                        .alpha(alpha)
                        .width(28.dp)
                        .height(thumbHeightDp)
                        .shadow(if (isDragging) 8.dp else 4.dp, RoundedCornerShape(14.dp))
                        .clip(RoundedCornerShape(14.dp))
                        .background(thumbBg)
                        .border(1.dp, if (isDragging) Color.Transparent else MaterialTheme.colorScheme.onBackground.copy(alpha=0.1f), RoundedCornerShape(14.dp))
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragStart = {
                                    isDragging = true
                                    // 🔥 ঠিক যেই জায়গায় হাত দিবেন সেখান থেকেই ড্র্যাগ শুরু হবে!
                                    dragAccumulator = currentOffsetYState
                                },
                                onDragEnd = { isDragging = false },
                                onDragCancel = { isDragging = false }
                            ) { change, dragAmount ->
                                change.consume()
                                val maxPx = maxOffsetPxState
                                val maxIdx = maxIndexState
                                if (maxPx > 0f && maxIdx > 0) {
                                    dragAccumulator = (dragAccumulator + dragAmount * 0.85f).coerceIn(0f, maxPx)
                                    val proportion = dragAccumulator / maxPx
                                    val targetIndex = (proportion * maxIdx).roundToInt().coerceIn(0, maxIdx)
                                    coroutineScope.launch { listState.scrollToItem(targetIndex) }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.fillMaxHeight().padding(vertical = 6.dp)
                    ) {
                        Icon(TablerIcons.ChevronUp, contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp))
                        Icon(TablerIcons.ChevronDown, contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}