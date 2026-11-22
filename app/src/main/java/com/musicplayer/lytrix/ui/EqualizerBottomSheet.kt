package com.musicplayer.lytrix.ui

import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicplayer.lytrix.eq.data.EqMode
import com.musicplayer.lytrix.eq.data.FilterType
import com.musicplayer.lytrix.eq.data.ParametricEQ
import com.musicplayer.lytrix.eq.data.ParametricEQBand
import com.musicplayer.lytrix.eq.data.SavedEQProfile
import com.musicplayer.lytrix.eq.ui.EqAutoEqWizardDialog
import com.musicplayer.lytrix.eq.ui.EqFrequencyResponseGraph
import com.musicplayer.lytrix.ui.theme.LocalAccentColor

data class EqPreset(val name: String, val icon: ImageVector, val levels: List<Float>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerBottomSheet(
    viewModel: PlayerViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val accentColor = LocalAccentColor.current

    val isEqEnabled by viewModel.isEqEnabled.collectAsState()
    val eqMode by viewModel.eqMode.collectAsState()
    val bandValues by viewModel.eqBandValues.collectAsState()
    val selectedPresetName by viewModel.selectedPreset.collectAsState()
    val savedProfiles by viewModel.eqProfiles.collectAsState()
    val activeProfile by viewModel.activeEqProfile.collectAsState()

    var selectedTab by remember(eqMode, activeProfile?.id) {
        mutableStateOf(
            if (eqMode == EqMode.PROFILE && activeProfile != null) EqMode.PROFILE else EqMode.GRAPHIC
        )
    }
    var showAutoEqWizard by remember { mutableStateOf(false) }
    var importErrorMessage by remember { mutableStateOf<String?>(null) }
    var profileToDelete by remember { mutableStateOf<SavedEQProfile?>(null) }

    val apoFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            var fileName = "custom_eq.txt"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        val name = cursor.getString(idx)
                        if (!name.isNullOrBlank()) fileName = name
                    }
                }
            }
            val stream = context.contentResolver.openInputStream(uri)
            if (stream != null) {
                viewModel.importCustomEqProfile(
                    fileName = fileName,
                    inputStream = stream,
                    onSuccess = { profile ->
                        selectedTab = EqMode.PROFILE
                        Toast.makeText(context, "Imported ${profile.deviceModel}", Toast.LENGTH_SHORT).show()
                    },
                    onError = { err -> importErrorMessage = err }
                )
            } else {
                importErrorMessage = "Unable to open selected file."
            }
        } catch (e: Exception) {
            importErrorMessage = e.message ?: "Failed to read file"
        }
    }

    val frequencies = listOf("31", "63", "125", "250", "500", "1K", "2K", "4K", "8K", "16K")
    val presets = listOf(
        EqPreset("Custom", Icons.Rounded.GraphicEq, listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
        EqPreset("Normal", Icons.Rounded.Face, listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
        EqPreset("Pop", Icons.Rounded.MusicNote, listOf(-1f, -1f, 0f, 2f, 4f, 4f, 2f, 0f, -1f, -1f)),
        EqPreset("R&B", Icons.Rounded.Album, listOf(3f, 5f, 4f, 1f, -1f, -1f, 1f, 3f, 4f, 5f)),
        EqPreset("Folk", Icons.Rounded.Spa, listOf(2f, 1f, 0f, -1f, -2f, -1f, 0f, 1f, 2f, 3f)),
        EqPreset("Dance", Icons.Rounded.Nightlife, listOf(5f, 4f, 2f, 0f, -2f, -4f, -2f, 0f, 2f, 4f)),
        EqPreset("Jazz", Icons.Rounded.MusicVideo, listOf(3f, 2f, 1f, -1f, -2f, -2f, -1f, 1f, 2f, 3f)),
        EqPreset("Bass booster", Icons.Rounded.Speaker, listOf(6f, 5f, 4f, 2f, 0f, 0f, 0f, 0f, 0f, 0f)),
        EqPreset("Treble booster", Icons.Rounded.MicExternalOn, listOf(0f, 0f, 0f, 0f, 0f, 2f, 4f, 5f, 6f, 7f)),
        EqPreset("Vocal booster", Icons.Rounded.Mic, listOf(-2f, -1f, 0f, 2f, 4f, 4f, 3f, 1f, 0f, -1f)),
        EqPreset("HipHop", Icons.Rounded.LibraryMusic, listOf(5f, 4f, 1f, -1f, -2f, -1f, 1f, 2f, 3f, 4f)),
        EqPreset("Rock", Icons.Rounded.ElectricMeter, listOf(5f, 3f, 0f, -1f, -2f, -1f, 1f, 3f, 4f, 5f)),
        EqPreset("Classical", Icons.Rounded.Piano, listOf(4f, 3f, 1f, 0f, -1f, -1f, 0f, 2f, 3f, 4f))
    )

    val graphicBandsForGraph = remember(bandValues) {
        ParametricEQ.GRAPHIC_BAND_FREQUENCIES.mapIndexed { idx, freq ->
            ParametricEQBand(
                frequency = freq,
                gain = (bandValues.getOrNull(idx) ?: 0f).toDouble(),
                q = 1.41,
                filterType = FilterType.PK,
                enabled = true
            )
        }
    }

    val showingProfileCurve = (selectedTab == EqMode.PROFILE && activeProfile != null) ||
        (eqMode == EqMode.PROFILE && activeProfile != null && selectedTab == EqMode.PROFILE)
    val graphBands = if (showingProfileCurve) activeProfile?.bands.orEmpty() else graphicBandsForGraph
    val graphPreamp = if (showingProfileCurve) activeProfile?.preamp ?: 0.0 else 0.0

    val statusText = when {
        !isEqEnabled -> "Disabled · Flat response"
        eqMode == EqMode.PROFILE && activeProfile != null ->
            "AutoEq: ${activeProfile!!.deviceModel} (${String.format("%.1f", activeProfile!!.preamp)} dB preamp)"
        else -> "10-Band Parametric DSP · $selectedPresetName"
    }

    com.musicplayer.lytrix.ui.component.LytrixFluidBottomSheet(
        onDismissRequest = onDismiss,
        allowContentDrag = false,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "Equalizer",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = statusText,
                            fontSize = 12.sp,
                            color = if (isEqEnabled) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Switch(
                        checked = isEqEnabled,
                        onCheckedChange = { viewModel.toggleEqualizer(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    )
                }
            }

            item {
                EqFrequencyResponseGraph(
                    bands = graphBands,
                    preamp = graphPreamp,
                    accentColor = accentColor,
                    isEnabled = isEqEnabled
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilterChip(
                        selected = selectedTab == EqMode.GRAPHIC,
                        onClick = {
                            selectedTab = EqMode.GRAPHIC
                            viewModel.setEqMode(EqMode.GRAPHIC)
                        },
                        label = { Text("10-Band EQ") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedTab == EqMode.PROFILE,
                        onClick = {
                            selectedTab = EqMode.PROFILE
                            if (activeProfile != null) {
                                viewModel.setEqMode(EqMode.PROFILE)
                            }
                        },
                        label = {
                            Text(
                                text = if (savedProfiles.isEmpty()) "AutoEq Profiles" else "AutoEq (${savedProfiles.size})",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Headphones,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (selectedTab == EqMode.GRAPHIC) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Graphic Bands",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = {
                                if (!isEqEnabled) viewModel.toggleEqualizer(true)
                                viewModel.applyPreset("Normal", List(10) { 0f })
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.RestartAlt,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset Flat", fontSize = 12.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        frequencies.forEachIndexed { index, freqLabel ->
                            PremiumVerticalSlider(
                                value = bandValues.getOrElse(index) { 0f },
                                freqLabel = freqLabel,
                                isEnabled = isEqEnabled,
                                accentColor = accentColor,
                                onValueChange = { newValue ->
                                    if (isEqEnabled) {
                                        viewModel.updateEqBand(index, newValue)
                                    }
                                }
                            )
                        }
                    }
                }

                item {
                    Text(
                        text = "Presets",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                val presetPairs = presets.chunked(2)
                items(presetPairs.size) { rowIndex ->
                    val pair = presetPairs[rowIndex]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        pair.forEach { preset ->
                            val isSelected = eqMode == EqMode.GRAPHIC && preset.name == selectedPresetName

                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceContainerHigh
                                    )
                                    .border(
                                        width = if (isSelected) 1.5.dp else 0.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .clickable {
                                        if (!isEqEnabled) viewModel.toggleEqualizer(true)
                                        viewModel.applyPreset(preset.name, preset.levels)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = preset.icon,
                                    contentDescription = preset.name,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = preset.name,
                                    fontSize = 13.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (pair.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            } else {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = accentColor.copy(alpha = 0.12f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { showAutoEqWizard = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Headphones,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Download AutoEq Headphone Profiles",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Search 4,000+ earphone/IEM curves or import ParametricEQ .txt",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.Rounded.CloudDownload,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { showAutoEqWizard = true },
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("AutoEq Search", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        FilledTonalButton(
                            onClick = { apoFilePicker.launch(arrayOf("text/plain", "text/*")) },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import .txt", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }

                if (activeProfile != null) {
                    val profile = activeProfile!!
                    item {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Fine-Tune ${profile.deviceModel}",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Preamp ${String.format("%.1f", profile.preamp)} dB · ${profile.bands.size} Parametric Bands",
                                            fontSize = 11.5.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (!isEqEnabled) {
                                        TextButton(
                                            onClick = { viewModel.toggleEqualizer(true) },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text("Enable", fontSize = 12.sp, color = accentColor)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Preamp",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Slider(
                                        value = profile.preamp.toFloat().coerceIn(-15f, 15f),
                                        onValueChange = { newPreamp ->
                                            if (!isEqEnabled) viewModel.toggleEqualizer(true)
                                            viewModel.updateProfilePreamp(newPreamp)
                                        },
                                        valueRange = -15f..15f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = accentColor,
                                            activeTrackColor = accentColor
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${String.format("%+.1f", profile.preamp)} dB",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = accentColor
                                    )
                                }

                                if (profile.bands.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    val bandRowModifier = if (profile.bands.size > 10) {
                                        Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState())
                                    } else {
                                        Modifier.fillMaxWidth()
                                    }
                                    Row(
                                        modifier = bandRowModifier,
                                        horizontalArrangement = if (profile.bands.size > 10) {
                                            Arrangement.spacedBy(8.dp)
                                        } else {
                                            Arrangement.SpaceBetween
                                        }
                                    ) {
                                        profile.bands.forEachIndexed { bandIdx, band ->
                                            val freqText = when {
                                                band.frequency >= 1000.0 -> {
                                                    val k = band.frequency / 1000.0
                                                    if (k % 1.0 == 0.0) "${k.toInt()}K" else String.format("%.1fK", k)
                                                }
                                                else -> band.frequency.toInt().toString()
                                            }
                                            PremiumVerticalSlider(
                                                value = band.gain.toFloat().coerceIn(-15f, 15f),
                                                freqLabel = freqText,
                                                isEnabled = isEqEnabled && band.enabled,
                                                accentColor = accentColor,
                                                onValueChange = { newGain ->
                                                    if (!isEqEnabled) viewModel.toggleEqualizer(true)
                                                    viewModel.updateProfileBand(bandIdx, newGain)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (savedProfiles.isEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Headphones,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "No AutoEq Profiles Yet",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Use AutoEq Search to match your exact headphone/earbud model or import an APO ParametricEQ.txt file.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(savedProfiles.size) { index ->
                        val profile = savedProfiles[index]
                        val isSelected = isEqEnabled && eqMode == EqMode.PROFILE && activeProfile?.id == profile.id
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) accentColor.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = if (isSelected) 1.5.dp else 0.dp,
                                    color = if (isSelected) accentColor else Color.Transparent,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable {
                                    if (isSelected) {
                                        viewModel.selectEqProfile(null)
                                    } else {
                                        viewModel.selectEqProfile(profile.id)
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { viewModel.selectEqProfile(profile.id) }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = profile.deviceModel,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${profile.bands.size} bands · ${String.format("%.1f", profile.preamp)} dB preamp · ${profile.source}",
                                        fontSize = 11.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = { profileToDelete = profile }) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = "Delete profile",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAutoEqWizard) {
        EqAutoEqWizardDialog(
            accentColor = accentColor,
            onProfilesReady = { fetchedProfiles ->
                viewModel.saveAutoEqProfiles(fetchedProfiles, activateFirst = true)
                selectedTab = EqMode.PROFILE
                Toast.makeText(
                    context,
                    "Applied ${fetchedProfiles.first().deviceModel}",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onDismiss = { showAutoEqWizard = false }
        )
    }

    if (importErrorMessage != null) {
        AlertDialog(
            onDismissRequest = { importErrorMessage = null },
            title = { Text("Import Error") },
            text = { Text(importErrorMessage ?: "") },
            confirmButton = {
                TextButton(onClick = { importErrorMessage = null }) { Text("OK") }
            }
        )
    }

    if (profileToDelete != null) {
        val target = profileToDelete!!
        AlertDialog(
            onDismissRequest = { profileToDelete = null },
            title = { Text("Delete Profile") },
            text = { Text("Remove \"${target.deviceModel}\" from saved EQ profiles?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteEqProfile(target.id)
                        profileToDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { profileToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun PremiumVerticalSlider(
    value: Float,
    freqLabel: String,
    isEnabled: Boolean,
    accentColor: Color,
    onValueChange: (Float) -> Unit
) {
    val maxDb = 15f
    var isDragging by remember { mutableStateOf(false) }

    val animatedValue = if (isDragging) {
        value
    } else {
        animateFloatAsState(
            targetValue = value,
            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
            label = "EqBand"
        ).value
    }

    val displayValue = value.toInt()
    val valueString = if (displayValue > 0) "+$displayValue" else "$displayValue"

    val trackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)
    val activeTrackColor = if (isEnabled) accentColor else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
    val thumbColor = if (isEnabled) Color.White else Color.Gray

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = valueString,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = if (displayValue != 0 && isEnabled) accentColor else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            modifier = Modifier.height(16.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .width(28.dp)
                .height(160.dp)
                .pointerInput(isEnabled) {
                    if (!isEnabled) return@pointerInput
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull()
                            if (change != null) {
                                if (change.pressed) {
                                    isDragging = true
                                    change.consume()
                                    val y = change.position.y.coerceIn(0f, size.height.toFloat())
                                    val newValue = maxDb - (y / size.height) * (maxDb * 2)
                                    onValueChange(newValue.coerceIn(-maxDb, maxDb))
                                } else {
                                    isDragging = false
                                }
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val trackWidth = 6.dp.toPx()
                val centerX = canvasWidth / 2f
                val centerY = canvasHeight / 2f

                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(centerX - trackWidth / 2f, 0f),
                    size = Size(trackWidth, canvasHeight),
                    cornerRadius = CornerRadius(trackWidth / 2f, trackWidth / 2f)
                )

                val mappedY = centerY - (animatedValue / maxDb) * centerY
                val topY = minOf(centerY, mappedY)
                val bottomY = maxOf(centerY, mappedY)

                drawRoundRect(
                    color = activeTrackColor,
                    topLeft = Offset(centerX - trackWidth / 2f, topY),
                    size = Size(trackWidth, bottomY - topY),
                    cornerRadius = CornerRadius(trackWidth / 2f, trackWidth / 2f)
                )

                drawCircle(
                    color = thumbColor,
                    radius = 8.dp.toPx(),
                    center = Offset(centerX, mappedY)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = freqLabel,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
        )
    }
}
