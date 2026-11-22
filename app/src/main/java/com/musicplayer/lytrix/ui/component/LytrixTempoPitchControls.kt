@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package com.musicplayer.lytrix.ui.component

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.musicplayer.lytrix.ui.icons.MetrolistIcons
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.decodeFromJsonElement
import java.text.DecimalFormatSymbols
import java.util.Locale
import java.util.UUID
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Verbatim Material 3 Expressive port of MetroList's TempoPitchControls.kt,
 * TempoPitchPreset.kt, TempoPitchInput.kt, and FormatUtils.kt.
 */
@Serializable
data class MetroTempoPitchPreset(
    val id: String,
    val name: String,
    val speed: Float,
    val pitch: Float,
) {
    internal val isValid: Boolean
        get() = id.isNotBlank() && name.isNotBlank() && speed in 0.5f..2f && pitch in 0.5f..2f

    fun canApply(
        speedEnabled: Boolean,
        varispeed: Boolean,
        pitchEnabled: Boolean = true,
    ): Boolean = isValid && speedEnabled && (!varispeed || speed == pitch) && (pitchEnabled || pitch == 1f)

    fun apply(
        speedEnabled: Boolean,
        varispeed: Boolean,
        onSpeedChange: (Float) -> Unit,
        onPitchChange: ((Float) -> Unit)?,
    ) {
        if (!canApply(speedEnabled, varispeed, onPitchChange != null)) return
        onSpeedChange(speed)
        onPitchChange?.invoke(pitch)
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        internal fun decode(value: String): List<MetroTempoPitchPreset> {
            val entries = runCatching { json.parseToJsonElement(value) as? JsonArray }.getOrNull() ?: return emptyList()
            return entries
                .mapNotNull { entry ->
                    runCatching { json.decodeFromJsonElement<MetroTempoPitchPreset>(entry) }.getOrNull()?.takeIf { it.isValid }
                }.distinctBy { it.id }
        }

        internal fun encode(presets: List<MetroTempoPitchPreset>): String =
            runCatching { json.encodeToString(presets) }.getOrDefault("[]")
    }
}

object MetroTempoPitchPresetStore {
    private const val PREFS_NAME = "metro_tempo_pitch_prefs"
    private const val KEY_PRESETS = "presets_json"

    fun load(context: Context): List<MetroTempoPitchPreset> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_PRESETS, "[]") ?: "[]"
        return MetroTempoPitchPreset.decode(raw)
    }

    fun savePreset(context: Context, name: String, speed: Float, pitch: Float): List<MetroTempoPitchPreset> {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return load(context)
        val current = load(context).toMutableList()
        current.add(
            MetroTempoPitchPreset(
                id = UUID.randomUUID().toString(),
                name = trimmed,
                speed = speed.coerceIn(0.5f, 2f),
                pitch = pitch.coerceIn(0.5f, 2f),
            ),
        )
        persist(context, current)
        return current
    }

    fun deletePreset(context: Context, id: String): List<MetroTempoPitchPreset> {
        val updated = load(context).filterNot { it.id == id }
        persist(context, updated)
        return updated
    }

    private fun persist(context: Context, presets: List<MetroTempoPitchPreset>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PRESETS, MetroTempoPitchPreset.encode(presets))
            .apply()
    }
}

fun formatMetroPlaybackSpeed(speed: Float): String = String.format(Locale.getDefault(), "%.2f", speed)

fun formatMetroPlaybackPitch(semitones: Float): String {
    val rounded = (semitones * 100).roundToInt() / 100f
    val value = String.format(Locale.getDefault(), "%.2f", rounded)
    return if (rounded > 0f) "+$value" else value
}

fun metroPlaybackPitchToSemitones(pitch: Float): Float =
    (12f * log2(pitch.coerceIn(0.5f, 2f))).coerceIn(-12f, 12f)

fun metroSemitonesToPlaybackPitch(semitones: Float): Float =
    2f.pow(semitones.coerceIn(-12f, 12f) / 12f)

private fun parseMetroTempoPitchInput(
    input: String,
    range: ClosedFloatingPointRange<Float>,
): Float? {
    val decimalSeparator = DecimalFormatSymbols.getInstance(Locale.getDefault()).decimalSeparator
    val normalized = buildString {
        for (character in input.trim()) {
            append(
                when {
                    character == decimalSeparator || character == '.' -> '.'
                    character == '+' || character == '-' -> character
                    character.digitToIntOrNull() != null -> character.digitToInt()
                    else -> return null
                },
            )
        }
    }
    val value = normalized.toDoubleOrNull() ?: return null
    if (!value.isFinite() || value < range.start.toDouble() || value > range.endInclusive.toDouble()) return null
    return value.toFloat()
}

private fun stepMetroTempoPitchValue(
    value: Float,
    delta: Float,
    range: ClosedFloatingPointRange<Float>,
): Float = ((value + delta) * 100).roundToInt().div(100f).coerceIn(range)

private class MetroSliderDetents(
    private val range: ClosedFloatingPointRange<Float>,
    private val steps: Int,
    initialValue: Float,
) {
    private var lastBucket = bucket(initialValue)

    private fun bucket(value: Float): Int {
        val span = (range.endInclusive - range.start).takeIf { it > 0f } ?: return 0
        val count = (steps + 1).coerceAtLeast(1)
        return (((value.coerceIn(range) - range.start) / span) * count).roundToInt()
    }

    fun reset(value: Float) {
        lastBucket = bucket(value)
    }

    fun moved(value: Float): Boolean {
        val next = bucket(value)
        if (next == lastBucket) return false
        lastBucket = next
        return true
    }
}

@Composable
fun MetroTempoPitchControls(
    speed: Float,
    pitch: Float,
    onSpeedChange: (Float) -> Unit,
    onPitchChange: ((Float) -> Unit)?,
    onCommit: () -> Unit = {},
    speedEnabled: Boolean = true,
    varispeed: Boolean = false,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val speedLabel = "Tempo"
    val pitchLabel = "Pitch"
    val semitones = metroPlaybackPitchToSemitones(pitch)
    val linkPitchToSpeed = varispeed && speedEnabled && onPitchChange != null
    val applySpeed: (Float) -> Unit = { value ->
        onSpeedChange(value)
        if (linkPitchToSpeed) onPitchChange(value)
    }
    val currentSpeed by rememberUpdatedState(speed)
    val latestCommit by rememberUpdatedState(onCommit)
    LaunchedEffect(linkPitchToSpeed) {
        if (linkPitchToSpeed) {
            onPitchChange(currentSpeed)
            latestCommit()
        }
    }
    DisposableEffect(Unit) {
        onDispose { latestCommit() }
    }

    var presets by remember { mutableStateOf(MetroTempoPitchPresetStore.load(context)) }
    var savingPreset by rememberSaveable { mutableStateOf(false) }
    var presetName by rememberSaveable { mutableStateOf("") }
    var deletingPresetId by rememberSaveable { mutableStateOf<String?>(null) }

    if (savingPreset) {
        AlertDialog(
            onDismissRequest = { savingPreset = false },
            title = { Text("Save preset") },
            text = {
                OutlinedTextField(
                    value = presetName,
                    onValueChange = { presetName = it },
                    label = { Text("Preset name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = presetName.isNotBlank(),
                    onClick = {
                        presets = MetroTempoPitchPresetStore.savePreset(context, presetName, speed, pitch)
                        savingPreset = false
                    },
                    shapes = ButtonDefaults.shapes(),
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(
                    onClick = { savingPreset = false },
                    shapes = ButtonDefaults.shapes(),
                ) { Text("Cancel") }
            },
        )
    }

    presets.firstOrNull { it.id == deletingPresetId }?.let { preset ->
        AlertDialog(
            onDismissRequest = { deletingPresetId = null },
            text = { Text("Delete preset \"${preset.name}\"?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        presets = MetroTempoPitchPresetStore.deletePreset(context, preset.id)
                        deletingPresetId = null
                    },
                    shapes = ButtonDefaults.shapes(),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(
                    onClick = { deletingPresetId = null },
                    shapes = ButtonDefaults.shapes(),
                ) { Text("Cancel") }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Text(
            text = "Tempo & Pitch",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        MetroTempoPitchSlider(
            label = speedLabel,
            value = speed,
            defaultValue = 1f,
            valueText = "${formatMetroPlaybackSpeed(speed)}x",
            range = 0.5f..2f,
            steps = 29,
            enabled = speedEnabled,
            onValueChange = applySpeed,
            onValueChangeFinished = onCommit,
        )
        if (!varispeed && onPitchChange != null) {
            Spacer(Modifier.height(16.dp))
            MetroTempoPitchSlider(
                label = pitchLabel,
                value = semitones,
                defaultValue = 0f,
                valueText = "${formatMetroPlaybackPitch(semitones)} st",
                range = -12f..12f,
                steps = 479,
                showTicks = false,
                onValueChange = { onPitchChange(metroSemitonesToPlaybackPitch(it)) },
                onValueChangeFinished = onCommit,
            )
        }
        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = {
                presetName = ""
                savingPreset = true
            },
            shapes = ButtonDefaults.shapes(),
        ) {
            Text("Save preset")
        }
        if (presets.isNotEmpty()) {
            Text(
                "Presets",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
            )
            presets.forEach { preset ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = {
                            preset.apply(speedEnabled, varispeed, onSpeedChange, onPitchChange)
                            onCommit()
                        },
                        enabled = preset.canApply(speedEnabled, varispeed, onPitchChange != null),
                        modifier = Modifier.weight(1f),
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(preset.name)
                    }
                    TextButton(
                        onClick = { deletingPresetId = preset.id },
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text("Delete")
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = {
                    if (speedEnabled) applySpeed(1f)
                    if (!varispeed) onPitchChange?.invoke(1f)
                    onCommit()
                },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text("Reset")
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    onCommit()
                    onDone()
                },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text("Done")
            }
        }
    }
}

@Composable
private fun MetroTempoPitchSlider(
    label: String,
    value: Float,
    defaultValue: Float,
    valueText: String,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    showTicks: Boolean = true,
    enabled: Boolean = true,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit = {},
) {
    val haptic = LocalHapticFeedback.current
    val detents = remember(range, steps) { MetroSliderDetents(range, steps, value) }
    detents.reset(value)
    var editing by rememberSaveable { mutableStateOf(false) }

    Text(label, style = MaterialTheme.typography.titleMedium)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(
            onClick = { editing = true },
            enabled = enabled,
            shapes = ButtonDefaults.shapes(),
        ) {
            Text(valueText)
        }
        Spacer(Modifier.weight(1f))
        TextButton(
            onClick = {
                onValueChange(defaultValue)
                onValueChangeFinished()
            },
            enabled = enabled && value != defaultValue,
            shapes = ButtonDefaults.shapes(),
        ) {
            Text("Reset")
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onValueChange(stepMetroTempoPitchValue(value, -0.05f, range))
                onValueChangeFinished()
            },
            enabled = enabled && value > range.start,
            shapes = IconButtonDefaults.shapes(),
        ) {
            Icon(MetrolistIcons.Filled.Remove, contentDescription = "Decrease $label")
        }
        val sliderState = rememberSliderState(
            value = value.coerceIn(range),
            trackRange = range,
            steps = steps,
        )
        sliderState.shouldAutoSnap = false
        sliderState.value = value.coerceIn(range)
        Slider(
            state = sliderState,
            onValueChange = { raw ->
                val snapped = (raw * 20).roundToInt() / 20f
                if (detents.moved(snapped)) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                onValueChange(snapped)
            },
            onValueChangeFinished = onValueChangeFinished,
            enabled = enabled,
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = label },
            colors = if (showTicks) {
                SliderDefaults.colors()
            } else {
                SliderDefaults.colors(
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent,
                )
            },
        )
        IconButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onValueChange(stepMetroTempoPitchValue(value, 0.05f, range))
                onValueChangeFinished()
            },
            enabled = enabled && value < range.endInclusive,
            shapes = IconButtonDefaults.shapes(),
        ) {
            Icon(MetrolistIcons.Filled.Add, contentDescription = "Increase $label")
        }
    }

    if (editing && enabled) {
        var input by rememberSaveable(stateSaver = TextFieldValue.Saver) {
            val text = formatMetroPlaybackSpeed(value)
            mutableStateOf(TextFieldValue(text, TextRange(0, text.length)))
        }
        val parsed = parseMetroTempoPitchInput(input.text, range)
        val focusRequester = remember { FocusRequester() }
        val confirm = {
            if (parsed != null) {
                onValueChange(parsed)
                onValueChangeFinished()
                editing = false
            }
        }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(label) },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text(label) },
                    singleLine = true,
                    isError = parsed == null,
                    supportingText = {
                        if (parsed == null) {
                            Text("Enter a value between ${formatMetroPlaybackSpeed(range.start)} and ${formatMetroPlaybackSpeed(range.endInclusive)}")
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (range.start < 0) KeyboardType.Text else KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { confirm() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
            },
            confirmButton = {
                TextButton(
                    onClick = confirm,
                    enabled = parsed != null,
                    shapes = ButtonDefaults.shapes(),
                ) { Text("Done") }
            },
            dismissButton = {
                TextButton(
                    onClick = { editing = false },
                    shapes = ButtonDefaults.shapes(),
                ) { Text("Cancel") }
            },
        )
    }
}
