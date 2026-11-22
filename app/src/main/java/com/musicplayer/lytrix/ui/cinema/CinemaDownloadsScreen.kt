@file:OptIn(UnstableApi::class)

package com.musicplayer.lytrix.ui.cinema

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FileDownloadDone
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File

@Composable
fun CinemaDownloadsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        TorrentDownloadManager.init(context)
    }

    val tasks by TorrentDownloadManager.tasks.collectAsState()
    val activeTasks = remember(tasks) { tasks.filter { it.status != DownloadStatus.COMPLETED } }
    val completedTasks = remember(tasks) { tasks.filter { it.status == DownloadStatus.COMPLETED } }

    var selectedTab by remember { mutableIntStateOf(if (activeTasks.isNotEmpty()) 0 else 1) }
    var playingOfflineTask by remember { mutableStateOf<CinemaDownloadTask?>(null) }
    var taskToDelete by remember { mutableStateOf<CinemaDownloadTask?>(null) }

    val accent = cinemaAccent()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    BackHandler {
        if (playingOfflineTask != null) {
            playingOfflineTask = null
        } else {
            onBack()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CinemaPalette.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Text(
                    text = "Downloads & Offline",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                )
            }

            // Tabs Row
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = CinemaPalette.surface,
                contentColor = accent,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = accent
                    )
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            text = "Active (${activeTasks.size})",
                            color = if (selectedTab == 0) accent else Color.White.copy(alpha = 0.6f),
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            text = "My Downloads (${completedTasks.size})",
                            color = if (selectedTab == 1) accent else Color.White.copy(alpha = 0.6f),
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }

            // Content List
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (selectedTab == 0) {
                    // Active Downloads
                    if (activeTasks.isEmpty()) {
                        EmptyStateView(
                            icon = Icons.Rounded.Download,
                            title = "No Active Downloads",
                            subtitle = "Downloads you start from movie and TV torrent streams will appear here."
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, navBottom + 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(activeTasks, key = { it.id }) { task ->
                                ActiveDownloadCard(
                                    task = task,
                                    accent = accent,
                                    onPause = { TorrentDownloadManager.pauseDownload(task.id) },
                                    onResume = { TorrentDownloadManager.resumeDownload(context, task.id) },
                                    onCancel = { TorrentDownloadManager.cancelDownload(context, task.id) }
                                )
                            }
                        }
                    }
                } else {
                    // Completed Downloads
                    if (completedTasks.isEmpty()) {
                        EmptyStateView(
                            icon = Icons.Rounded.FileDownloadDone,
                            title = "No Downloaded Videos Yet",
                            subtitle = "Completed downloads will be available here for offline playback anytime without internet."
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, navBottom + 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(completedTasks, key = { it.id }) { task ->
                                CompletedDownloadCard(
                                    task = task,
                                    accent = accent,
                                    onPlay = { playingOfflineTask = task },
                                    onOpenExternal = {
                                        openDownloadedFile(context, task.filePath, task.title)
                                    },
                                    onDelete = { taskToDelete = task }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Offline Video Player Overlay
        playingOfflineTask?.let { task ->
            CinemaOfflinePlayerView(
                filePath = task.filePath,
                title = task.title,
                onClose = { playingOfflineTask = null }
            )
        }

        // Delete Confirmation Dialog
        taskToDelete?.let { task ->
            AlertDialog(
                onDismissRequest = { taskToDelete = null },
                containerColor = CinemaPalette.surface,
                title = { Text("Delete Download?", color = Color.White) },
                text = {
                    Text(
                        "Are you sure you want to remove “${task.title}” from your device? This will free up storage.",
                        color = Color.White.copy(alpha = 0.8f)
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            TorrentDownloadManager.deleteCompleted(context, task.id)
                            taskToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                    ) {
                        Text("Delete", color = Color.White)
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { taskToDelete = null }) {
                        Text("Cancel", color = Color.White)
                    }
                }
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Download Cards
// ---------------------------------------------------------------------------

@Composable
private fun ActiveDownloadCard(
    task: CinemaDownloadTask,
    accent: Color,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CinemaPalette.surface)
            .border(1.dp, CinemaPalette.border, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Poster / Icon
                Box(
                    modifier = Modifier
                        .size(52.dp, 74.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF202026)),
                    contentAlignment = Alignment.Center
                ) {
                    if (task.posterUrl.isNotBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(task.posterUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = task.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Videocam,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (task.quality.isNotBlank()) {
                            Text(
                                text = task.quality,
                                color = accent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (task.sizeStr.isNotBlank()) {
                            Text(
                                text = "•  ${task.sizeStr}",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 12.sp
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    // Speed and status
                    Text(
                        text = when (task.status) {
                            DownloadStatus.DOWNLOADING -> "${TorrentDownloadManager.formatSpeed(task.speedBytesPerSec)}  •  ${task.peersCount} peers"
                            DownloadStatus.PAUSED -> "Paused"
                            DownloadStatus.QUEUED -> "Connecting to peers..."
                            DownloadStatus.ERROR -> task.errorMessage ?: "Failed"
                            DownloadStatus.COMPLETED -> "Completed"
                        },
                        color = if (task.status == DownloadStatus.ERROR) Color(0xFFEF5350) else Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }

                // Action button (Pause / Resume)
                IconButton(
                    onClick = {
                        if (task.status == DownloadStatus.DOWNLOADING) onPause() else onResume()
                    }
                ) {
                    Icon(
                        imageVector = if (task.status == DownloadStatus.DOWNLOADING) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = "Control",
                        tint = accent
                    )
                }

                // Cancel button
                IconButton(onClick = onCancel) {
                    Icon(
                        imageVector = Icons.Rounded.Stop,
                        contentDescription = "Cancel",
                        tint = Color.White.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Progress bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LinearProgressIndicator(
                    progress = { task.progressPercent / 100f },
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(CircleShape),
                    color = accent,
                    trackColor = Color.White.copy(alpha = 0.1f)
                )
                Text(
                    text = "${task.progressPercent}%",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun CompletedDownloadCard(
    task: CinemaDownloadTask,
    accent: Color,
    onPlay: () -> Unit,
    onOpenExternal: () -> Unit,
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CinemaPalette.surface)
            .border(1.dp, CinemaPalette.border, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Poster
                Box(
                    modifier = Modifier
                        .size(52.dp, 74.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF202026)),
                    contentAlignment = Alignment.Center
                ) {
                    if (task.posterUrl.isNotBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(task.posterUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = task.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Videocam,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (task.quality.isNotBlank()) {
                            Text(
                                text = task.quality,
                                color = accent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        val formattedSize = if (task.totalBytes > 0) {
                            TorrentDownloadManager.formatSize(task.totalBytes)
                        } else task.sizeStr
                        if (formattedSize.isNotBlank()) {
                            Text(
                                text = "•  $formattedSize",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 12.sp
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Ready for offline playback ✅",
                        color = Color(0xFF81C784),
                        fontSize = 12.sp
                    )
                }

                // Delete button
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete",
                        tint = Color.White.copy(alpha = 0.5f)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Actions Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onPlay,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accent),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Play Offline", fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = onOpenExternal,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Play with VLC ↗", fontSize = 13.sp)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Fullscreen In-App Offline Player (ExoPlayer)
// ---------------------------------------------------------------------------

@Composable
private fun CinemaOfflinePlayerView(
    filePath: String,
    title: String,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val view = LocalView.current

    // Keep screen on & immersive landscape
    DisposableEffect(Unit) {
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val origOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (origOrientation != null) {
                activity.requestedOrientation = origOrientation
            }
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val file = File(filePath)
            val uri = Uri.fromFile(file)
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    BackHandler(onBack = onClose)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Close Overlay Button
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(16.dp)
                .size(40.dp)
                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Close",
                tint = Color.White
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun openDownloadedFile(context: Context, filePath: String, title: String) {
    try {
        val file = File(filePath)
        if (!file.exists()) {
            Toast.makeText(context, "File does not exist on disk", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = try {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (_: Throwable) {
            Uri.fromFile(file)
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/*")
            putExtra("title", title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(
                Intent(intent).setPackage("org.videolan.vlc").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            context.startActivity(Intent.createChooser(intent, "Play with...").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to open video: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun EmptyStateView(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(CinemaPalette.surface, CircleShape)
                .border(1.dp, CinemaPalette.border, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.4f),
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.6f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
