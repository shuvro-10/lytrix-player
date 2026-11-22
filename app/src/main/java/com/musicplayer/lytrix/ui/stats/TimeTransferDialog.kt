package com.musicplayer.lytrix.ui.stats

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.musicplayer.lytrix.db.AppDatabase
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowRight
import compose.icons.tablericons.ArrowsRightLeft
import compose.icons.tablericons.X
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class TimeTransferCandidate(
    val key: String,
    val isOnline: Boolean,
    val localSongId: Long,
    val onlineSongId: String,
    val title: String,
    val artist: String,
    val imageUrl: String,
    val durationMs: Long,
    val recordedPlays: Int
)

/**
 * Meld-inspired Time Transfer dialog allowing users to merge/move play counts and listening
 * history from an old/duplicate entry to a target track.
 */
@Composable
fun TimeTransferDialog(
    accentColor: Color,
    onDismiss: () -> Unit,
    onTransferComplete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var candidates by remember { mutableStateOf<List<TimeTransferCandidate>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isTransferring by remember { mutableStateOf(false) }

    var sourceSearch by remember { mutableStateOf("") }
    var targetSearch by remember { mutableStateOf("") }
    var selectedSource by remember { mutableStateOf<TimeTransferCandidate?>(null) }
    var selectedTarget by remember { mutableStateOf<TimeTransferCandidate?>(null) }

    LaunchedEffect(Unit) {
        candidates = loadTimeTransferCandidates(context)
        isLoading = false
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
                        Icon(TablerIcons.ArrowsRightLeft, contentDescription = null, tint = accentColor)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Time & Stats Transfer",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Move play counts & history from an old entry into another track",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(TablerIcons.X, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = accentColor)
                    }
                } else if (candidates.size < 2) {
                    Text(
                        text = "Listen to at least two distinct songs first before transferring stats.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                } else {
                    Text(
                        text = if (selectedSource == null) {
                            "1. Select Source Song (stats to move FROM)"
                        } else {
                            "Source: ${selectedSource!!.title} (${selectedSource!!.recordedPlays} plays)"
                        },
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accentColor
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = sourceSearch,
                        onValueChange = { sourceSearch = it },
                        placeholder = { Text("Filter source song...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    val sourceFiltered = remember(candidates, sourceSearch) {
                        candidates.filter {
                            it.recordedPlays > 0 && (
                                sourceSearch.isBlank() ||
                                    it.title.contains(sourceSearch, ignoreCase = true) ||
                                    it.artist.contains(sourceSearch, ignoreCase = true)
                                )
                        }
                    }
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 125.dp)
                            .padding(vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(sourceFiltered, key = { "src_${it.key}" }) { item ->
                            CandidateSelectRow(
                                item = item,
                                selected = selectedSource?.key == item.key,
                                accentColor = accentColor,
                                onClick = {
                                    selectedSource = item
                                    if (selectedTarget?.key == item.key) selectedTarget = null
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (selectedTarget == null) {
                            "2. Select Target Song (stats to merge INTO)"
                        } else {
                            "Target: ${selectedTarget!!.title} (${selectedTarget!!.recordedPlays} plays)"
                        },
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accentColor
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = targetSearch,
                        onValueChange = { targetSearch = it },
                        placeholder = { Text("Filter target song...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    val targetFiltered = remember(candidates, targetSearch, selectedSource) {
                        candidates.filter {
                            it.key != selectedSource?.key &&
                                (targetSearch.isBlank() ||
                                    it.title.contains(targetSearch, ignoreCase = true) ||
                                    it.artist.contains(targetSearch, ignoreCase = true))
                        }
                    }
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 125.dp)
                            .padding(vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(targetFiltered, key = { "dst_${it.key}" }) { item ->
                            CandidateSelectRow(
                                item = item,
                                selected = selectedTarget?.key == item.key,
                                accentColor = accentColor,
                                onClick = { selectedTarget = item }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        enabled = !isTransferring && selectedSource != null && selectedTarget != null && selectedSource?.key != selectedTarget?.key,
                        onClick = {
                            val src = selectedSource ?: return@Button
                            val dst = selectedTarget ?: return@Button
                            isTransferring = true
                            scope.launch {
                                val movedCount = executeTimeTransfer(context, src, dst)
                                isTransferring = false
                                Toast.makeText(
                                    context,
                                    "Transferred $movedCount history/play entries to ${dst.title}",
                                    Toast.LENGTH_SHORT
                                ).show()
                                onTransferComplete()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                    ) {
                        Icon(TablerIcons.ArrowRight, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isTransferring) "Moving..." else "Transfer Stats")
                    }
                }
            }
        }
    }
}

@Composable
private fun CandidateSelectRow(
    item: TimeTransferCandidate,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = if (selected) accentColor.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerLowest
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${item.artist} • ${if (item.isOnline) "Online" else "Local"}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "${item.recordedPlays} plays",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
        }
    }
}

private suspend fun loadTimeTransferCandidates(context: Context): List<TimeTransferCandidate> =
    withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val audioHistory = db.audioHistoryDao().getAllAudioHistory()
        val metadataCounts = db.songMetadataDao().getAllPlayCounts().first().associateBy { it.songId }
        val onlineHistory = db.onlineHistoryDao().getAllOnlineHistory().first()

        val resultMap = LinkedHashMap<String, TimeTransferCandidate>()

        audioHistory.groupBy { it.songId }.forEach { (songId, entries) ->
            val sample = entries.first()
            val count = maxOf(entries.size, metadataCounts[songId]?.playCount ?: 0)
            val dur = entries.firstOrNull { it.durationMs > 0L }?.durationMs ?: 0L
            resultMap["L_$songId"] = TimeTransferCandidate(
                key = "L_$songId",
                isOnline = false,
                localSongId = songId,
                onlineSongId = "",
                title = sample.title,
                artist = sample.artist,
                imageUrl = "",
                durationMs = dur,
                recordedPlays = count
            )
        }

        onlineHistory.groupBy { it.songId }.forEach { (songId, entries) ->
            val sample = entries.first()
            val dur = entries.firstOrNull { it.duration > 0L }?.duration ?: 0L
            resultMap["O_$songId"] = TimeTransferCandidate(
                key = "O_$songId",
                isOnline = true,
                localSongId = -1L,
                onlineSongId = songId,
                title = sample.title,
                artist = sample.artist,
                imageUrl = sample.imageUrl,
                durationMs = dur,
                recordedPlays = entries.size
            )
        }

        // Also include unplayed / library local songs & liked online tracks so any track can be a target.
        runCatching {
            val proj = arrayOf(
                android.provider.MediaStore.Audio.Media._ID,
                android.provider.MediaStore.Audio.Media.TITLE,
                android.provider.MediaStore.Audio.Media.ARTIST,
                android.provider.MediaStore.Audio.Media.DURATION
            )
            context.contentResolver.query(
                android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                proj,
                "${android.provider.MediaStore.Audio.Media.IS_MUSIC} != 0",
                null,
                null
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media._ID)
                val titleCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.TITLE)
                val artistCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.ARTIST)
                val durCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DURATION)
                while (c.moveToNext()) {
                    val sid = c.getLong(idCol)
                    val k = "L_$sid"
                    val durMs = c.getLong(durCol).coerceAtLeast(0L)
                    val existing = resultMap[k]
                    if (existing != null) {
                        if (existing.durationMs <= 0L && durMs > 0L) {
                            resultMap[k] = existing.copy(durationMs = durMs)
                        }
                    } else {
                        val t = c.getString(titleCol).orEmpty()
                        if (t.isNotBlank()) {
                            resultMap[k] = TimeTransferCandidate(
                                key = k,
                                isOnline = false,
                                localSongId = sid,
                                onlineSongId = "",
                                title = t,
                                artist = c.getString(artistCol).orEmpty().ifBlank { "Unknown Artist" },
                                imageUrl = "",
                                durationMs = durMs,
                                recordedPlays = metadataCounts[sid]?.playCount ?: 0
                            )
                        }
                    }
                }
            }
        }

        runCatching {
            val likedPrefs = context.getSharedPreferences("online_liked_songs", Context.MODE_PRIVATE)
            val likedIds = likedPrefs.getStringSet("liked_ids", emptySet()).orEmpty()
            likedIds.forEach { oid ->
                val raw = likedPrefs.getString("song_data_$oid", null) ?: return@forEach
                val obj = runCatching { org.json.JSONObject(raw) }.getOrNull() ?: return@forEach
                val title = obj.optString("title", obj.optString("name", ""))
                if (title.isNotBlank() && !resultMap.containsKey("O_$oid")) {
                    resultMap["O_$oid"] = TimeTransferCandidate(
                        key = "O_$oid",
                        isOnline = true,
                        localSongId = -1L,
                        onlineSongId = oid,
                        title = title,
                        artist = obj.optString("artist", obj.optString("subtitle", "Unknown Artist")),
                        imageUrl = obj.optString("imageUrl", obj.optString("image", "")),
                        durationMs = 0L,
                        recordedPlays = 0
                    )
                }
            }
        }

        resultMap.values.sortedWith(
            compareByDescending<TimeTransferCandidate> { it.recordedPlays }.thenBy { it.title.lowercase() }
        )
    }

private suspend fun executeTimeTransfer(
    context: Context,
    source: TimeTransferCandidate,
    target: TimeTransferCandidate
): Int = withContext(Dispatchers.IO) {
    val db = AppDatabase.getDatabase(context)
    if (!source.isOnline && !target.isOnline) {
        val movedRows = db.audioHistoryDao().reassignSongHistory(
            fromSongId = source.localSongId,
            toSongId = target.localSongId,
            toTitle = target.title,
            toArtist = target.artist
        )
        val movedStats = db.songMetadataDao().transferPlayStats(source.localSongId, target.localSongId)
        return@withContext maxOf(movedRows, movedStats)
    }
    if (source.isOnline && target.isOnline) {
        return@withContext db.onlineHistoryDao().reassignOnlineHistory(
            fromSongId = source.onlineSongId,
            toSongId = target.onlineSongId,
            toTitle = target.title,
            toArtist = target.artist,
            toImageUrl = target.imageUrl
        )
    }
    if (!source.isOnline && target.isOnline) {
        val srcRows = db.audioHistoryDao().getHistoryForSong(source.localSongId)
        val srcMeta = db.songMetadataDao().getMetadata(source.localSongId)
        val totalPlays = maxOf(srcRows.size, srcMeta?.playCount ?: 0)
        val onlineDao = db.onlineHistoryDao()
        val resolvedDur = target.durationMs.takeIf { it > 0L } ?: source.durationMs
        srcRows.forEach { row ->
            onlineDao.insertOnlineHistory(
                com.musicplayer.lytrix.db.OnlineHistoryEntity(
                    songId = target.onlineSongId,
                    title = target.title,
                    artist = target.artist,
                    imageUrl = target.imageUrl,
                    source = if (target.imageUrl.contains("saavn", ignoreCase = true)) "JIOSAAVN" else "YOUTUBE",
                    timestamp = row.timestamp,
                    duration = if (resolvedDur > 0L) resolvedDur else row.durationMs
                )
            )
        }
        val extraPlays = (totalPlays - srcRows.size).coerceAtLeast(0)
        val baseTs = srcMeta?.lastPlayedTimestamp?.takeIf { it > 0L } ?: System.currentTimeMillis()
        repeat(extraPlays) { idx ->
            onlineDao.insertOnlineHistory(
                com.musicplayer.lytrix.db.OnlineHistoryEntity(
                    songId = target.onlineSongId,
                    title = target.title,
                    artist = target.artist,
                    imageUrl = target.imageUrl,
                    source = if (target.imageUrl.contains("saavn", ignoreCase = true)) "JIOSAAVN" else "YOUTUBE",
                    timestamp = baseTs - idx * 60_000L,
                    duration = resolvedDur
                )
            )
        }
        db.audioHistoryDao().deleteHistoryForSong(source.localSongId)
        db.audioHistoryDao().resetPlayStatsForSong(source.localSongId)
        return@withContext totalPlays
    }
    // Online -> Local
    val onlineRows = db.onlineHistoryDao().getHistoryBySongId(source.onlineSongId)
    val resolvedDur = target.durationMs.takeIf { it > 0L } ?: source.durationMs
    onlineRows.forEach { row ->
        db.audioHistoryDao().insertAudioHistory(
            com.musicplayer.lytrix.db.AudioHistoryEntity(
                songId = target.localSongId,
                title = target.title,
                artist = target.artist,
                timestamp = row.timestamp,
                durationMs = if (resolvedDur > 0L) resolvedDur else row.duration
            )
        )
    }
    val metaDao = db.songMetadataDao()
    val existingTarget = metaDao.getMetadata(target.localSongId)
    val maxTs = onlineRows.maxOfOrNull { it.timestamp } ?: System.currentTimeMillis()
    if (existingTarget == null) {
        metaDao.insertOrUpdate(
            com.musicplayer.lytrix.db.SongMetadataEntity(
                songId = target.localSongId,
                playCount = onlineRows.size,
                lastPlayedTimestamp = maxTs
            )
        )
    } else {
        metaDao.insertOrUpdate(
            existingTarget.copy(
                playCount = existingTarget.playCount + onlineRows.size,
                lastPlayedTimestamp = maxOf(existingTarget.lastPlayedTimestamp, maxTs)
            )
        )
    }
    db.onlineHistoryDao().deleteHistoryBySongId(source.onlineSongId)
    onlineRows.size
}
