@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.musicplayer.lytrix.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.musicplayer.lytrix.ui.icons.MorphingMetroIcon

/**
 * Tactile spring press scale (Apple Music / SimpMusic / Expressive bounce feel).
 */
fun Modifier.tactileSpringPress(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.86f,
): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "tactileSpringScale",
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

data class MetroToolbarAction(
    val outlinedIcon: ImageVector,
    val filledIcon: ImageVector = outlinedIcon,
    val contentDescription: String,
    val checked: Boolean = false,
    val text: String? = null,
    val onClick: () -> Unit,
)

/**
 * Verbatim Material 3 Expressive port of MetroList's PlayerConnectedButtonGroup.kt:
 * Connected ButtonGroup of ToggleButtons that widen + morph corners with spring physics on tap,
 * paired with Outlined -> Filled MorphingMetroIcon and haptic feedback.
 * When `action.text != null` (e.g. active Sleep Timer countdown), displays the live text
 * inside the highlighted ToggleButton just like MetroList.
 */
@Composable
fun MetroConnectedPlayerToolbar(
    actions: List<MetroToolbarAction>,
    activeContainerColor: Color,
    activeContentColor: Color,
    inactiveContainerColor: Color = Color.White.copy(alpha = 0.12f),
    inactiveContentColor: Color = Color.White,
    buttonHeight: Dp = 42.dp,
    buttonWidth: Dp = 44.dp,
    iconSize: Dp = 20.dp,
    modifier: Modifier = Modifier,
) {
    if (actions.isEmpty()) return
    val haptic = LocalHapticFeedback.current

    ButtonGroup(
        overflowIndicator = { menuState -> ButtonGroupDefaults.OverflowIndicator(menuState) },
        expandedRatio = 0.14f,
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.height(buttonHeight),
    ) {
        actions.forEachIndexed { index, action ->
            customItem(
                buttonGroupContent = {
                    val interactionSource = remember { MutableInteractionSource() }
                    val shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        actions.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    }
                    ToggleButton(
                        checked = action.checked,
                        interactionSource = interactionSource,
                        onCheckedChange = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            action.onClick()
                        },
                        shapes = shapes,
                        contentPadding = if (action.text != null) {
                            PaddingValues(horizontal = 8.dp)
                        } else {
                            PaddingValues(0.dp)
                        },
                        colors = ToggleButtonDefaults.colors(
                            containerColor = inactiveContainerColor,
                            contentColor = inactiveContentColor,
                            checkedContainerColor = activeContainerColor,
                            checkedContentColor = activeContentColor,
                        ),
                        modifier = if (action.text != null) {
                            Modifier
                                .animateWidth(interactionSource)
                                .widthIn(min = buttonWidth)
                                .height(buttonHeight)
                        } else {
                            Modifier
                                .animateWidth(interactionSource)
                                .width(buttonWidth)
                                .height(buttonHeight)
                        },
                    ) {
                        if (action.text != null) {
                            Text(
                                text = action.text,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                            )
                        } else {
                            MorphingMetroIcon(
                                selected = action.checked,
                                outlinedIcon = action.outlinedIcon,
                                filledIcon = action.filledIcon,
                                contentDescription = action.contentDescription,
                                modifier = Modifier.size(iconSize),
                            )
                        }
                    }
                },
                menuContent = { menuState ->
                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(
                                imageVector = if (action.checked) action.filledIcon else action.outlinedIcon,
                                contentDescription = null,
                                tint = if (action.checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        text = {
                            Text(
                                if (action.text != null) "${action.contentDescription} (${action.text})" else action.contentDescription
                            )
                        },
                        onClick = {
                            menuState.dismiss()
                            action.onClick()
                        },
                    )
                },
            )
        }
    }
}

/**
 * Material 3 Expressive morphing FilledIconButton (circle -> squircle on press + haptic).
 * Matches MetroList's FilledIconButton(shapes = IconButtonDefaults.shapes()).
 */
@Composable
fun MetroMorphingIconButton(
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    FilledIconButton(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        enabled = enabled,
        shapes = IconButtonDefaults.shapes(),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        modifier = modifier.size(size),
        content = content,
    )
}

/**
 * Animated Play/Pause icon transition used across MetroList / SimpMusic / Lytrix players.
 */
@Composable
fun MorphingPlayPauseIcon(
    isPlaying: Boolean,
    playIcon: ImageVector,
    pauseIcon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = isPlaying,
        transitionSpec = {
            (fadeIn(tween(150)) + scaleIn(initialScale = 0.78f, animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)))
                .togetherWith(fadeOut(tween(110)) + scaleOut(targetScale = 0.78f))
        },
        label = "playPauseIconMorph",
    ) { playing ->
        Icon(
            imageVector = if (playing) pauseIcon else playIcon,
            contentDescription = if (playing) "Pause" else "Play",
            tint = tint,
            modifier = modifier,
        )
    }
}
