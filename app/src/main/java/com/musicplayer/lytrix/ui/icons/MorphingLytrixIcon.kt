package com.musicplayer.lytrix.ui.icons

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.delay

/**
 * Material 3 Expressive Outlined -> Filled morphing icon (MetroList / Lytrix / SimpMusic style).
 * Smoothly crossfades + pops with spring physics when toggled into active/filled state.
 */
@Composable
fun MorphingMetroIcon(
    selected: Boolean,
    outlinedIcon: ImageVector,
    filledIcon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    var pulseTrigger by remember { mutableStateOf(false) }

    LaunchedEffect(selected) {
        if (selected) {
            pulseTrigger = true
            delay(160)
            pulseTrigger = false
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (pulseTrigger) 1.18f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "metroIconBounce",
    )

    AnimatedContent(
        targetState = selected,
        transitionSpec = {
            (fadeIn(tween(180)) + scaleIn(initialScale = 0.82f, animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)))
                .togetherWith(fadeOut(tween(130)) + scaleOut(targetScale = 0.88f))
        },
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        label = "metroIconMorph",
    ) { isSelected ->
        Icon(
            imageVector = if (isSelected) filledIcon else outlinedIcon,
            contentDescription = contentDescription,
            tint = tint,
        )
    }
}
