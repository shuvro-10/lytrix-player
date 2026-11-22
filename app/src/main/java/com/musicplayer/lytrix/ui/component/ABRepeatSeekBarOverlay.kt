package com.musicplayer.lytrix.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicplayer.lytrix.ui.icons.MetrolistIcons

private fun formatABShortTime(ms: Long): String {
    val totalSec = (ms.coerceAtLeast(0L) / 1000L)
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    return "%d:%02d".format(mins, secs)
}

/**
 * Symfonium / Material 3 Expressive minimal A-B Repeat seekbar overlay:
 * - Soft glowing A -> B loop highlight strip directly on the track
 * - Clean 2.5dp x 14dp rounded vertical capsule tick bars at Point A & Point B (no clunky balls)
 * - Never intercepts touch/scrub gestures on the underlying slider.
 */
@Composable
fun ABRepeatSeekBarBox(
    pointA: Long?,
    pointB: Long?,
    durationMs: Long,
    markerColor: Color,
    horizontalInset: Dp = 10.dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        content()
        if (durationMs > 0L && (pointA != null || pointB != null)) {
            Canvas(
                modifier = Modifier.matchParentSize(),
            ) {
                val insetPx = horizontalInset.toPx()
                val trackStart = insetPx
                val trackWidth = (size.width - insetPx * 2f).coerceAtLeast(1f)
                val centerY = size.height / 2f

                val fracA = pointA?.let { (it.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) }
                val fracB = pointB?.let { (it.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) }

                // Soft glowing loop region highlight along the track
                if (fracA != null && fracB != null && fracB > fracA) {
                    val xA = trackStart + trackWidth * fracA
                    val xB = trackStart + trackWidth * fracB
                    val glowH = 8.dp.toPx()
                    val coreH = 4.dp.toPx()
                    drawRoundRect(
                        color = markerColor.copy(alpha = 0.24f),
                        topLeft = Offset(xA, centerY - glowH / 2f),
                        size = Size((xB - xA).coerceAtLeast(2f), glowH),
                        cornerRadius = CornerRadius(glowH / 2f, glowH / 2f),
                    )
                    drawRoundRect(
                        color = markerColor.copy(alpha = 0.72f),
                        topLeft = Offset(xA, centerY - coreH / 2f),
                        size = Size((xB - xA).coerceAtLeast(2f), coreH),
                        cornerRadius = CornerRadius(coreH / 2f, coreH / 2f),
                    )
                }

                // Minimal vertical capsule tick markers (2.5dp x 14dp) with subtle dark outline for contrast
                val tickW = 2.6.dp.toPx()
                val tickH = 14.dp.toPx()
                val outlineW = 4.4.dp.toPx()
                val outlineH = 16.dp.toPx()

                fun drawCapsuleTick(frac: Float) {
                    val x = (trackStart + trackWidth * frac).coerceIn(outlineW, size.width - outlineW)
                    drawRoundRect(
                        color = Color.Black.copy(alpha = 0.45f),
                        topLeft = Offset(x - outlineW / 2f, centerY - outlineH / 2f),
                        size = Size(outlineW, outlineH),
                        cornerRadius = CornerRadius(outlineW / 2f, outlineW / 2f),
                    )
                    drawRoundRect(
                        color = markerColor,
                        topLeft = Offset(x - tickW / 2f, centerY - tickH / 2f),
                        size = Size(tickW, tickH),
                        cornerRadius = CornerRadius(tickW / 2f, tickW / 2f),
                    )
                }

                if (fracA != null) drawCapsuleTick(fracA)
                if (fracB != null) drawCapsuleTick(fracB)
            }
        }
    }
}

/**
 * Slim Symfonium / M3 Expressive contextual floating pill that smoothly slides in
 * directly below the seekbar ONLY when Point A or Point B is active:
 *   [ ↻  A 01:12  →  B 01:45   ✕ ]
 * One-tap to update A, set/update B, or clear without reopening the 3-dot menu.
 */
@Composable
fun ABRepeatControlPill(
    pointA: Long?,
    pointB: Long?,
    accentColor: Color,
    onSetPointA: () -> Unit,
    onSetPointB: () -> Unit,
    onClearAB: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visible = pointA != null || pointB != null
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.34f),
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.42f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = MetrolistIcons.Rounded.Repeat,
                        contentDescription = "A-B Repeat Active",
                        tint = accentColor,
                        modifier = Modifier.size(14.dp),
                    )
                    // Point A chip (tap to re-mark A at current position)
                    Surface(
                        shape = CircleShape,
                        color = accentColor.copy(alpha = 0.20f),
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(onClick = onSetPointA),
                    ) {
                        Text(
                            text = if (pointA != null) "A ${formatABShortTime(pointA)}" else "Set A",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                    Text(
                        text = "→",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 11.sp,
                    )
                    // Point B chip (tap to set/update B at current position)
                    Surface(
                        shape = CircleShape,
                        color = if (pointB != null) accentColor.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.14f),
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(enabled = pointA != null, onClick = onSetPointB),
                    ) {
                        Text(
                            text = if (pointB != null) "B ${formatABShortTime(pointB)}" else "Set B",
                            color = if (pointB != null) Color.White else accentColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(2.dp))
                    // 1-tap Clear button
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onClearAB),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = MetrolistIcons.Outlined.Close,
                            contentDescription = "Clear A-B Repeat",
                            tint = Color.White.copy(alpha = 0.78f),
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
    }
}
