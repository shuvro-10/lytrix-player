@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.musicplayer.lytrix.ui.component

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.musicplayer.lytrix.playback.SleepTimerDefaults
import com.musicplayer.lytrix.playback.SleepTimerPrefsStore
import com.musicplayer.lytrix.playback.SleepTimerState
import com.musicplayer.lytrix.playback.formatSleepTimerRemaining
import com.musicplayer.lytrix.playback.sleepTimerDurationMs
import com.musicplayer.lytrix.ui.icons.LytrixIcons

private fun lytrixSegmentedGroupShape(
    first: Boolean,
    last: Boolean,
    pressed: Boolean,
): Shape {
    val outer = if (pressed) 12.dp else 20.dp
    val inner = if (pressed) 8.dp else 4.dp
    val top = if (first) outer else inner
    val bottom = if (last) outer else inner
    return RoundedCornerShape(
        topStart = top,
        topEnd = top,
        bottomStart = bottom,
        bottomEnd = bottom,
    )
}

/**
 * Verbatim Material 3 Expressive port of Lytrix's SleepTimerDialog.kt.
 */
@Composable
fun LytrixSleepTimerDialog(
    state: SleepTimerState,
    onDismiss: () -> Unit,
    onStart: (durationMs: Long, fadeOut: Boolean, finishCurrentSong: Boolean) -> Unit,
    onStopAfterCurrentSong: (fadeOut: Boolean) -> Unit,
    onCancelTimer: () -> Unit,
    allowEndOfSong: Boolean = true,
) {
    val context = LocalContext.current
    val defaults = remember { SleepTimerPrefsStore.load(context) }
    var hours by rememberSaveable { mutableStateOf((defaults.durationMinutes / 60).toString().padStart(2, '0')) }
    var minutes by rememberSaveable { mutableStateOf((defaults.durationMinutes % 60).toString().padStart(2, '0')) }
    var finishCurrentSong by rememberSaveable { mutableStateOf(defaults.finishCurrentSong) }
    var fadeOut by rememberSaveable { mutableStateOf(defaults.fadeOut) }
    var expanded by rememberSaveable { mutableStateOf(true) }
    val durationMs = sleepTimerDurationMs(hours, minutes)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(onDismiss) { detectTapGestures { onDismiss() } }
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .pointerInput(Unit) { detectTapGestures { /* consume taps inside card */ } },
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = AlertDialogDefaults.shape,
                    color = AlertDialogDefaults.containerColor,
                    tonalElevation = AlertDialogDefaults.TonalElevation,
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        Icon(
                            LytrixIcons.Outlined.Bedtime,
                            contentDescription = null,
                            tint = AlertDialogDefaults.iconContentColor,
                        )
                        Text(
                            "Sleep timer",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            if (state.isActive) {
                                Text(
                                    if (state.stopAfterCurrentSong) {
                                        "Stops at end of current song (${formatSleepTimerRemaining(state.remainingMs)})"
                                    } else {
                                        "Time remaining: ${formatSleepTimerRemaining(state.remainingMs)}"
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    textAlign = TextAlign.Center,
                                )
                                if (state.finishCurrentSong) {
                                    Text(
                                        "Finish current song",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (state.fadeOut) {
                                    Text(
                                        "Fade out enabled",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    LytrixDurationField(
                                        value = hours,
                                        onValueChange = { hours = it },
                                        label = "Hour",
                                        maximum = 23,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        ":",
                                        modifier = Modifier.padding(top = 12.dp),
                                        style = MaterialTheme.typography.displayMedium,
                                    )
                                    LytrixDurationField(
                                        value = minutes,
                                        onValueChange = { minutes = it },
                                        label = "Minute",
                                        maximum = 59,
                                        modifier = Modifier.weight(1f),
                                        last = true,
                                    )
                                }
                                if (allowEndOfSong) {
                                    FilledTonalButton(
                                        onClick = {
                                            onStopAfterCurrentSong(fadeOut)
                                            onDismiss()
                                        },
                                        shapes = ButtonDefaults.shapes(),
                                    ) {
                                        Text("End of song")
                                    }
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Surface(
                                        shape = lytrixSegmentedGroupShape(first = true, last = !expanded, pressed = false),
                                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                "Advanced options",
                                                modifier = Modifier.weight(1f),
                                                style = MaterialTheme.typography.titleMedium,
                                            )
                                            val rotation by animateFloatAsState(
                                                targetValue = if (expanded) 180f else 0f,
                                                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                                                label = "sleepAdvancedArrow",
                                            )
                                            FilledTonalIconButton(
                                                onClick = { expanded = !expanded },
                                                shapes = IconButtonDefaults.shapes(),
                                            ) {
                                                Icon(
                                                    LytrixIcons.Outlined.KeyboardArrowDown,
                                                    contentDescription = if (expanded) "Collapse" else "Expand",
                                                    modifier = Modifier.rotate(rotation),
                                                )
                                            }
                                        }
                                    }
                                    AnimatedVisibility(
                                        visible = expanded,
                                        enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                                            fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
                                        exit = shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                                            fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            LytrixTimerCheckboxRow(
                                                text = "Finish current song",
                                                checked = finishCurrentSong,
                                                enabled = allowEndOfSong,
                                                last = false,
                                                onCheckedChange = { finishCurrentSong = it },
                                            )
                                            LytrixTimerCheckboxRow(
                                                text = "Fade out",
                                                checked = fadeOut,
                                                last = true,
                                                onCheckedChange = { fadeOut = it },
                                            )
                                        }
                                    }
                                }
                                Surface(
                                    onClick = {
                                        durationMs?.let {
                                            SleepTimerPrefsStore.save(
                                                context,
                                                SleepTimerDefaults((it / 60_000L).toInt(), finishCurrentSong, fadeOut),
                                            )
                                            Toast.makeText(context, "Saved as default sleep timer", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    enabled = durationMs != null,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.extraLarge,
                                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ) {
                                    Text(
                                        "Set as default",
                                        modifier = Modifier.padding(16.dp),
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                            }
                        }
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (!state.isActive) {
                                OutlinedButton(
                                    onClick = {
                                        val saved = SleepTimerPrefsStore.load(context)
                                        hours = (saved.durationMinutes / 60).toString().padStart(2, '0')
                                        minutes = (saved.durationMinutes % 60).toString().padStart(2, '0')
                                        finishCurrentSong = saved.finishCurrentSong
                                        fadeOut = saved.fadeOut
                                    },
                                    shapes = ButtonDefaults.shapes(),
                                ) {
                                    Text("Reset")
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            OutlinedButton(
                                onClick = onDismiss,
                                shapes = ButtonDefaults.shapes(),
                            ) {
                                Text(if (state.isActive) "Close" else "Cancel")
                            }
                            Button(
                                onClick = {
                                    if (state.isActive) {
                                        onCancelTimer()
                                    } else {
                                        durationMs?.let {
                                            onStart(
                                                it,
                                                fadeOut,
                                                finishCurrentSong && allowEndOfSong,
                                            )
                                        }
                                    }
                                    onDismiss()
                                },
                                enabled = state.isActive || durationMs != null,
                                shapes = ButtonDefaults.shapes(),
                            ) {
                                Text(if (state.isActive) "Cancel timer" else "OK")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LytrixDurationField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    maximum: Int,
    modifier: Modifier,
    last: Boolean = false,
) {
    val focusManager = LocalFocusManager.current
    var input by remember { mutableStateOf(TextFieldValue(value)) }
    LaunchedEffect(value) {
        if (input.text != value) input = TextFieldValue(value, TextRange(0, value.length))
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = input,
            onValueChange = {
                if (it.text.length <= 2 && it.text.all { char -> char in '0'..'9' }) {
                    input = it
                    onValueChange(it.text)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged {
                    if (it.isFocused) input = input.copy(selection = TextRange(0, input.text.length))
                }
                .semantics { contentDescription = label },
            textStyle = MaterialTheme.typography.displayMedium.copy(textAlign = TextAlign.Center),
            singleLine = true,
            isError = value.toIntOrNull()?.let { it > maximum } == true,
            shape = MaterialTheme.shapes.small,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                focusedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Transparent,
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = if (last) ImeAction.Done else ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Next) },
                onDone = { focusManager.clearFocus() },
            ),
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LytrixTimerCheckboxRow(
    text: String,
    checked: Boolean,
    last: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Surface(
        shape = lytrixSegmentedGroupShape(first = false, last = last, pressed = pressed),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = checked,
                    enabled = enabled,
                    role = Role.Checkbox,
                    interactionSource = interactionSource,
                    indication = ripple(),
                    onValueChange = onCheckedChange,
                )
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
            Text(text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
