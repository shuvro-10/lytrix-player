package com.musicplayer.lytrix.ui.components

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.musicplayer.lytrix.playlist.ExportFormat
import com.musicplayer.lytrix.playlist.ExportablePlaylistOption
import com.musicplayer.lytrix.playlist.PlaylistPortabilityManager
import compose.icons.TablerIcons
import compose.icons.tablericons.FileExport
import compose.icons.tablericons.FileImport
import compose.icons.tablericons.X
import kotlinx.coroutines.launch

/**
 * Meld-inspired dialog for exporting any Lytrix playlist to CSV/M3U/M3U8 or importing
 * external `.csv`, `.m3u`, and `.m3u8` playlists into Lytrix local/online library.
 */
@Composable
fun PlaylistPortabilityDialog(
    accentColor: Color,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var playlists by remember { mutableStateOf<List<ExportablePlaylistOption>>(emptyList()) }
    var selectedFormat by remember { mutableStateOf(ExportFormat.M3U8) }
    var pendingExportOption by remember { mutableStateOf<ExportablePlaylistOption?>(null) }
    var isBusy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        playlists = PlaylistPortabilityManager.listExportablePlaylists(context)
    }

    val createExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val option = pendingExportOption
        pendingExportOption = null
        if (uri != null && option != null) {
            isBusy = true
            scope.launch {
                runCatching {
                    val tracks = PlaylistPortabilityManager.loadTracksForOption(context, option)
                    PlaylistPortabilityManager.exportPlaylistToUri(context, uri, tracks, selectedFormat)
                }.onSuccess { count ->
                    Toast.makeText(context, "Exported $count tracks (${selectedFormat.extension.uppercase()})", Toast.LENGTH_SHORT).show()
                }.onFailure { err ->
                    Toast.makeText(context, "Export failed: ${err.localizedMessage}", Toast.LENGTH_LONG).show()
                }
                isBusy = false
            }
        }
    }

    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            isBusy = true
            scope.launch {
                runCatching {
                    PlaylistPortabilityManager.importPlaylistFromUri(context, uri)
                }.onSuccess { summary ->
                    playlists = PlaylistPortabilityManager.listExportablePlaylists(context)
                    Toast.makeText(
                        context,
                        "Imported \"${summary.playlistName}\": ${summary.matchedLocalCount} local + ${summary.importedOnlineCount} online tracks",
                        Toast.LENGTH_LONG
                    ).show()
                }.onFailure { err ->
                    Toast.makeText(context, "Import failed: ${err.localizedMessage}", Toast.LENGTH_LONG).show()
                }
                isBusy = false
            }
        }
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
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(TablerIcons.FileExport, contentDescription = null, tint = accentColor)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Playlist CSV & M3U",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Import or export portable playlist files",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(TablerIcons.X, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    enabled = !isBusy,
                    onClick = {
                        importFileLauncher.launch(
                            arrayOf(
                                "text/*",
                                "audio/x-mpegurl",
                                "application/vnd.apple.mpegurl",
                                "application/octet-stream"
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(TablerIcons.FileImport, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Import .CSV / .M3U / .M3U8 File", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Export Format", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExportFormat.entries.forEach { fmt ->
                        FilterChip(
                            selected = selectedFormat == fmt,
                            onClick = { selectedFormat = fmt },
                            label = { Text(fmt.extension.uppercase(), fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text("Tap a playlist to export:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(6.dp))

                if (playlists.isEmpty()) {
                    Text(
                        text = "No playlists with tracks found yet.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 18.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(playlists, key = { "${it.kind}_${it.id}" }) { pl ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable(enabled = !isBusy) {
                                        pendingExportOption = pl
                                        val safeName = pl.name.replace(Regex("[^A-Za-z0-9_\\- ]"), "").trim().ifBlank { "Playlist" }
                                        createExportLauncher.launch("$safeName.${selectedFormat.extension}")
                                    },
                                color = MaterialTheme.colorScheme.surfaceContainerLowest
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = pl.name,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${pl.trackCount} tracks",
                                            fontSize = 11.5.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(
                                        TablerIcons.FileExport,
                                        contentDescription = "Export",
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
    }
}
