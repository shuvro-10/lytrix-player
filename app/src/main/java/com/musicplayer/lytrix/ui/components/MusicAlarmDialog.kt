package com.musicplayer.lytrix.ui.components

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.musicplayer.lytrix.alarm.AlarmSourceType
import com.musicplayer.lytrix.alarm.MusicAlarmEntry
import com.musicplayer.lytrix.alarm.MusicAlarmScheduler
import com.musicplayer.lytrix.alarm.MusicAlarmStore
import com.musicplayer.lytrix.db.AppDatabase
import com.musicplayer.lytrix.ui.PlayerViewModel
import compose.icons.TablerIcons
import compose.icons.tablericons.Alarm
import compose.icons.tablericons.Plus
import compose.icons.tablericons.Trash
import compose.icons.tablericons.X
import kotlinx.coroutines.flow.first
import java.util.Calendar

/**
 * Full-featured Meld-style Music Alarm Clock dialog accessible from Preferences.
 */
@Composable
fun MusicAlarmDialog(
    accentColor: Color,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var alarms by remember { mutableStateOf(MusicAlarmStore.loadAll(context)) }
    var editingAlarm by remember { mutableStateOf<MusicAlarmEntry?>(null) }

    val canScheduleExact = remember {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || (am?.canScheduleExactAlarms() == true)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(TablerIcons.Alarm, contentDescription = null, tint = accentColor)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Music Alarm Clock",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Wake up to any playlist, liked track, or library",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(TablerIcons.X, contentDescription = "Close")
                    }
                }

                if (!canScheduleExact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = accentColor.copy(alpha = 0.12f)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                runCatching {
                                    context.startActivity(
                                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                    )
                                }
                            }
                    ) {
                        Text(
                            text = "Tap to allow exact alarms so wake-up playback triggers right on the minute.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (alarms.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                TablerIcons.Alarm,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No music alarms set",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Tap New Alarm below to schedule a musical wake-up",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 340.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(alarms, key = { it.id }) { alarm ->
                            AlarmEntryCard(
                                alarm = alarm,
                                accentColor = accentColor,
                                onToggle = { checked ->
                                    alarms = MusicAlarmStore.setEnabled(context, alarm.id, checked)
                                    val updated = alarms.firstOrNull { it.id == alarm.id }
                                    if (updated != null) {
                                        if (updated.enabled) {
                                            MusicAlarmScheduler.schedule(context, updated)
                                        } else {
                                            MusicAlarmScheduler.cancel(context, updated.id)
                                        }
                                    }
                                },
                                onEdit = { editingAlarm = alarm },
                                onDelete = {
                                    MusicAlarmScheduler.cancel(context, alarm.id)
                                    alarms = MusicAlarmStore.delete(context, alarm.id)
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            val quickTest = MusicAlarmEntry(
                                hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
                                minute = Calendar.getInstance().get(Calendar.MINUTE),
                                label = "5s Test Alarm",
                                sourceType = AlarmSourceType.ALL_LOCAL,
                                fadeInSeconds = 8,
                                randomTrack = true
                            )
                            MusicAlarmScheduler.scheduleTestInSeconds(context, quickTest, 5)
                            alarms = MusicAlarmStore.loadAll(context)
                            Toast.makeText(
                                context,
                                "Test alarm armed — will trigger in 5 seconds!",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    ) {
                        Text("Test in 5s", color = accentColor, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = {
                            val now = Calendar.getInstance()
                            editingAlarm = MusicAlarmEntry(
                                hour = now.get(Calendar.HOUR_OF_DAY),
                                minute = (now.get(Calendar.MINUTE) + 1) % 60
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(TablerIcons.Plus, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("New Alarm", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    editingAlarm?.let { target ->
        AlarmEditorDialog(
            initial = target,
            accentColor = accentColor,
            onDismiss = { editingAlarm = null },
            onSave = { saved ->
                val enabledSaved = saved.copy(enabled = true)
                alarms = MusicAlarmStore.upsert(context, enabledSaved)
                MusicAlarmScheduler.schedule(context, enabledSaved)
                editingAlarm = null
                val hhmm = String.format("%02d:%02d", enabledSaved.hour, enabledSaved.minute)
                Toast.makeText(context, "Alarm scheduled for $hhmm", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
private fun AlarmEntryCard(
    alarm: MusicAlarmEntry,
    accentColor: Color,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onEdit),
        color = MaterialTheme.colorScheme.surfaceContainerLowest
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = String.format("%02d:%02d", alarm.hour, alarm.minute),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (alarm.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
                val sourceSummary = if (alarm.sourceType == AlarmSourceType.SPECIFIC_SONG && alarm.songTitle.isNotBlank()) {
                    alarm.songTitle
                } else {
                    alarm.playlistTitle.ifBlank { alarm.sourceType.displayName }
                }
                Text(
                    text = "${alarm.label.ifBlank { "Alarm" }} • $sourceSummary",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatRepeatSummary(alarm),
                    fontSize = 11.sp,
                    color = accentColor
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    TablerIcons.Trash,
                    contentDescription = "Delete alarm",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp)
                )
            }
            Switch(
                checked = alarm.enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(checkedTrackColor = accentColor)
            )
        }
    }
}

@Composable
private fun AlarmEditorDialog(
    initial: MusicAlarmEntry,
    accentColor: Color,
    onDismiss: () -> Unit,
    onSave: (MusicAlarmEntry) -> Unit
) {
    val context = LocalContext.current
    var hour by remember(initial) { mutableIntStateOf(initial.hour) }
    var minute by remember(initial) { mutableIntStateOf(initial.minute) }
    var label by remember(initial) { mutableStateOf(initial.label) }
    var repeatDays by remember(initial) { mutableStateOf(initial.repeatDays) }
    var sourceType by remember(initial) { mutableStateOf(initial.sourceType) }
    var playlistId by remember(initial) { mutableStateOf(initial.playlistId) }
    var playlistTitle by remember(initial) { mutableStateOf(initial.playlistTitle) }
    var songId by remember(initial) { mutableStateOf(initial.songId) }
    var songTitle by remember(initial) { mutableStateOf(initial.songTitle) }
    var songArtist by remember(initial) { mutableStateOf(initial.songArtist) }
    var songUri by remember(initial) { mutableStateOf(initial.songUri) }
    var songArtUrl by remember(initial) { mutableStateOf(initial.songArtUrl) }
    var songIsOnline by remember(initial) { mutableStateOf(initial.songIsOnline) }
    var randomTrack by remember(initial) { mutableStateOf(initial.randomTrack) }
    var fadeInSeconds by remember(initial) { mutableIntStateOf(initial.fadeInSeconds) }

    var showSongPicker by remember { mutableStateOf(false) }
    var localPlaylists by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var onlinePlaylists by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        localPlaylists = runCatching {
            AppDatabase.getDatabase(context).playlistDao().getAllPlaylists().first()
                .map { it.id.toString() to it.name }
        }.getOrDefault(emptyList())

        val meta = context.getSharedPreferences("online_playlists_meta", Context.MODE_PRIVATE)
        onlinePlaylists = (meta.getStringSet("names", emptySet()) ?: emptySet()).sorted()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configure Music Alarm", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NumberStepperBox("Hour", hour, 24) { hour = it }
                    Text(
                        ":",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    )
                    NumberStepperBox("Min", minute, 60) { minute = it }
                }

                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Repeat Days", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                val days = listOf(
                    Calendar.SUNDAY to "S",
                    Calendar.MONDAY to "M",
                    Calendar.TUESDAY to "T",
                    Calendar.WEDNESDAY to "W",
                    Calendar.THURSDAY to "T",
                    Calendar.FRIDAY to "F",
                    Calendar.SATURDAY to "S"
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    days.forEach { (dayConst, short) ->
                        val selected = dayConst in repeatDays
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selected) accentColor else MaterialTheme.colorScheme.surfaceContainerLowest
                                )
                                .border(
                                    1.dp,
                                    if (selected) accentColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                    CircleShape
                                )
                                .clickable {
                                    repeatDays = if (selected) repeatDays - dayConst else repeatDays + dayConst
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = short,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Text("Wake-Up Source", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AlarmSourceType.entries.forEach { option ->
                        FilterChip(
                            selected = sourceType == option,
                            onClick = {
                                sourceType = option
                                when (option) {
                                    AlarmSourceType.SPECIFIC_SONG -> {
                                        playlistId = ""
                                        playlistTitle = songTitle.ifBlank { "Pick a Song" }
                                        showSongPicker = true
                                    }
                                    AlarmSourceType.LOCAL_PLAYLIST -> {
                                        val first = localPlaylists.firstOrNull()
                                        playlistId = first?.first.orEmpty()
                                        playlistTitle = first?.second.orEmpty()
                                    }
                                    AlarmSourceType.ONLINE_PLAYLIST -> {
                                        val first = onlinePlaylists.firstOrNull().orEmpty()
                                        playlistId = first
                                        playlistTitle = first
                                    }
                                    else -> {
                                        playlistId = ""
                                        playlistTitle = option.displayName
                                    }
                                }
                            },
                            label = { Text(option.displayName, fontSize = 11.sp) }
                        )
                    }
                }

                if (sourceType == AlarmSourceType.SPECIFIC_SONG) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = accentColor.copy(alpha = 0.14f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { showSongPicker = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MusicNote,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (songTitle.isNotBlank()) songTitle else "Select Wake-Up Song",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (songArtist.isNotBlank()) {
                                        "$songArtist • Tap to change"
                                    } else {
                                        "Tap to choose any local or recent online song"
                                    },
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                } else if (sourceType == AlarmSourceType.LOCAL_PLAYLIST && localPlaylists.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        localPlaylists.forEach { (id, name) ->
                            FilterChip(
                                selected = playlistId == id,
                                onClick = {
                                    playlistId = id
                                    playlistTitle = name
                                },
                                label = { Text(name, fontSize = 11.sp) }
                            )
                        }
                    }
                } else if (sourceType == AlarmSourceType.ONLINE_PLAYLIST && onlinePlaylists.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        onlinePlaylists.forEach { name ->
                            FilterChip(
                                selected = playlistId == name,
                                onClick = {
                                    playlistId = name
                                    playlistTitle = name
                                },
                                label = { Text(name, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                if (sourceType != AlarmSourceType.SPECIFIC_SONG) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Shuffle Track", fontSize = 13.sp)
                        Switch(checked = randomTrack, onCheckedChange = { randomTrack = it })
                    }
                }

                Text("Gentle Volume Fade-In: ${fadeInSeconds}s", fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 10, 20, 30).forEach { sec ->
                        FilterChip(
                            selected = fadeInSeconds == sec,
                            onClick = { fadeInSeconds = sec },
                            label = { Text("${sec}s", fontSize = 11.sp) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val effectiveSource = if (sourceType == AlarmSourceType.SPECIFIC_SONG && songId.isBlank() && songUri.isBlank()) {
                        AlarmSourceType.ALL_LOCAL
                    } else {
                        sourceType
                    }
                    onSave(
                        initial.copy(
                            hour = hour,
                            minute = minute,
                            label = label.trim().ifBlank { "Morning Music" },
                            repeatDays = repeatDays,
                            sourceType = effectiveSource,
                            playlistId = playlistId,
                            playlistTitle = if (effectiveSource == AlarmSourceType.SPECIFIC_SONG) songTitle else playlistTitle,
                            songId = songId,
                            songTitle = songTitle,
                            songArtist = songArtist,
                            songUri = songUri,
                            songArtUrl = songArtUrl,
                            songIsOnline = songIsOnline,
                            randomTrack = randomTrack,
                            fadeInSeconds = fadeInSeconds
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
            ) {
                Text("Save Alarm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showSongPicker) {
        AlarmSongPickerDialog(
            selectedSongId = songId,
            accentColor = accentColor,
            onDismiss = { showSongPicker = false },
            onSelectSong = { picked ->
                songId = picked.songId
                songTitle = picked.title
                songArtist = picked.artist
                songUri = picked.uri
                songArtUrl = picked.artUrl
                songIsOnline = picked.isOnline
                playlistTitle = picked.title
                showSongPicker = false
            }
        )
    }
}

private data class AlarmPickableTrack(
    val songId: String,
    val title: String,
    val artist: String,
    val uri: String,
    val artUrl: String,
    val isOnline: Boolean
)

@Composable
private fun AlarmSongPickerDialog(
    selectedSongId: String,
    accentColor: Color,
    onDismiss: () -> Unit,
    onSelectSong: (AlarmPickableTrack) -> Unit
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var tracks by remember { mutableStateOf<List<AlarmPickableTrack>>(emptyList()) }

    LaunchedEffect(Unit) {
        val collected = LinkedHashMap<String, AlarmPickableTrack>()
        PlayerViewModel.activeInstance?.songsList?.value?.forEach { local ->
            val key = "L_${local.id}"
            collected[key] = AlarmPickableTrack(
                songId = local.id.toString(),
                title = local.title,
                artist = local.artist,
                uri = local.uriString,
                artUrl = local.albumArtUriString.orEmpty(),
                isOnline = false
            )
        }
        runCatching {
            AppDatabase.getDatabase(context).onlineHistoryDao().getAllOnlineHistory().first().forEach { hist ->
                val key = "O_${hist.songId}"
                if (!collected.containsKey(key)) {
                    collected[key] = AlarmPickableTrack(
                        songId = hist.songId,
                        title = hist.title,
                        artist = hist.artist,
                        uri = hist.uriString.ifBlank { "https://www.youtube.com/watch?v=${hist.songId}" },
                        artUrl = hist.imageUrl,
                        isOnline = true
                    )
                }
            }
        }
        tracks = collected.values.toList()
    }

    val filtered = remember(tracks, query) {
        val q = query.trim()
        if (q.isEmpty()) tracks.take(80)
        else tracks.filter {
            it.title.contains(q, ignoreCase = true) || it.artist.contains(q, ignoreCase = true)
        }.take(80)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Wake-Up Song", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 260.dp, max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search songs or artists...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                if (filtered.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (tracks.isEmpty()) "No songs available on device/history yet" else "No matching songs",
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filtered, key = { "${it.isOnline}_${it.songId}_${it.uri}" }) { item ->
                            val isSelected = item.songId == selectedSongId && selectedSongId.isNotBlank()
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) accentColor.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerLowest,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onSelectSong(item) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.title,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${item.artist} • ${if (item.isOnline) "Online" else "Local"}",
                                            fontSize = 11.sp,
                                            color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = "Selected",
                                            tint = accentColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun NumberStepperBox(
    caption: String,
    value: Int,
    modulo: Int,
    onChange: (Int) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        TextButton(onClick = { onChange((value + 1) % modulo) }) {
            Text("▲", fontSize = 16.sp)
        }
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest
        ) {
            Text(
                text = String.format("%02d", value),
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
            )
        }
        TextButton(onClick = { onChange((value - 1 + modulo) % modulo) }) {
            Text("▼", fontSize = 16.sp)
        }
        Text(caption, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatRepeatSummary(alarm: MusicAlarmEntry): String {
    val prefix = if (alarm.fadeInSeconds > 0) "Fade ${alarm.fadeInSeconds}s" else "Instant volume"
    if (alarm.repeatDays.isEmpty()) return "One-time • $prefix"
    if (alarm.repeatDays.size == 7) return "Every day • $prefix"
    val names = mapOf(
        Calendar.SUNDAY to "Sun",
        Calendar.MONDAY to "Mon",
        Calendar.TUESDAY to "Tue",
        Calendar.WEDNESDAY to "Wed",
        Calendar.THURSDAY to "Thu",
        Calendar.FRIDAY to "Fri",
        Calendar.SATURDAY to "Sat"
    )
    return alarm.repeatDays.sorted().mapNotNull { names[it] }.joinToString(", ") + " • $prefix"
}
