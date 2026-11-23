package com.musicplayer.lytrix.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.musicplayer.lytrix.playback.DownloadSource
import com.musicplayer.lytrix.playback.DownloadStatus
import com.musicplayer.lytrix.playback.DownloadTask
import com.musicplayer.lytrix.playback.OkHttpDownloader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onNavigateBack: () -> Unit,
    onPlaySong: (DownloadTask) -> Unit = {}
) {
    val context = LocalContext.current
    val accentColor = com.musicplayer.lytrix.ui.theme.LocalAccentColor.current
    val downloadTasksMap by OkHttpDownloader.downloadTasks.collectAsState()
    val allTasks = remember(downloadTasksMap) { downloadTasksMap.values.toList().reversed() }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        DownloadSource.YOUTUBE to "YouTube Music",
        DownloadSource.JIOSAAVN to "JioSaavn",
        DownloadSource.OTHERS to "Others"
    )

    val currentSource = tabs[selectedTabIndex].first
    val currentTasks = allTasks.filter { it.source == currentSource }
    val activeCount = allTasks.count { it.status == DownloadStatus.DOWNLOADING }
    val isDark = com.musicplayer.lytrix.ui.theme.LocalIsDarkTheme.current

    Scaffold(
        containerColor = if (isDark) Color(0xFF0F0F0F) else MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Downloads",
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = if (isDark) Color.White else MaterialTheme.colorScheme.onBackground
                        )
                        if (activeCount > 0) {
                            Spacer(Modifier.width(10.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(accentColor.copy(alpha = 0.2f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "$activeCount active",
                                    color = accentColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = if (isDark) Color.White else MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = if (isDark) Color(0xFF0F0F0F) else MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 🔥 ডানে-বামে স্মুথ স্ক্রোলযোগ্য প্রিমিয়াম পিল-স্টাইল ট্যাব
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                tabs.forEachIndexed { index, (source, label) ->
                    val isSelected = selectedTabIndex == index
                    val count = allTasks.count { it.source == source }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) accentColor else if (isDark) Color(0xFF1E1E1E) else MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable { selectedTabIndex = index }
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = label,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) (if (accentColor.luminance() > 0.5f) Color.Black else Color.White) else if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                            if (count > 0) {
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(if (isSelected) Color.Black.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.12f))
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = count.toString(),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.Black else Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // 🔥 ডাউনলোড গানের তালিকা
            if (currentTasks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.CloudDownload,
                            contentDescription = null,
                            tint = Color.DarkGray,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "No downloads in ${tabs[selectedTabIndex].second}",
                            color = Color.Gray,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Songs downloaded from here will appear live",
                            color = Color.DarkGray,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 140.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(currentTasks, key = { it.id }) { task ->
                        DownloadItemCard(
                            task = task,
                            accentColor = accentColor,
                            onCancel = { OkHttpDownloader.cancelDownload(task.id, context) },
                            onRetry = {
                                OkHttpDownloader.startDownload(
                                    context = context,
                                    songId = task.id,
                                    title = task.title,
                                    artist = task.artist,
                                    imageUrl = task.imageUrl,
                                    providedUrl = task.providedUrl,
                                    source = task.source
                                )
                            },
                            onPlay = { onPlaySong(task) }
                        )
                    }
                }
            }
        }
    }
}

// 🔥 প্রতিটি গানের জন্য প্রিমিয়াম ডার্ক কার্ড
@Composable
private fun DownloadItemCard(
    task: DownloadTask,
    accentColor: Color,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onPlay: () -> Unit
) {
    val context = LocalContext.current
    val isDark = com.musicplayer.lytrix.ui.theme.LocalIsDarkTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isDark) Color(0xFF181818) else MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, if (isDark) Color.White.copy(alpha = 0.05f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // কভার ছবি
        AsyncImage(
            model = task.imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isDark) Color(0xFF242424) else MaterialTheme.colorScheme.surfaceContainerHighest)
        )

        Spacer(Modifier.width(12.dp))

        // গান ও শিল্পীর নাম এবং প্রোগ্রেস
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title,
                color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = task.artist,
                color = Color.Gray,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(6.dp))

            when (task.status) {
                DownloadStatus.DOWNLOADING -> {
                    Column {
                        LinearProgressIndicator(
                            progress = { task.progress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = accentColor,
                            trackColor = Color(0xFF2C2C2C)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${task.downloadedMB} MB / ${task.totalMB} MB • ${task.progress}%",
                            color = accentColor.copy(alpha = 0.9f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                DownloadStatus.COMPLETED -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF00E676),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        val displaySize = if (task.totalMB != "0.00") task.totalMB else task.downloadedMB
                        Text(
                            text = "Download Complete • $displaySize MB",
                            color = Color(0xFF00E676),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                DownloadStatus.FAILED -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.ErrorOutline,
                            contentDescription = null,
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = task.errorMessage ?: "Download Failed",
                            color = Color(0xFFFF5252),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Spacer(Modifier.width(10.dp))

        // বাটন (ক্যান্সেল, রিট্রাই, অথবা প্লে)
        when (task.status) {
            DownloadStatus.DOWNLOADING -> {
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Red.copy(alpha = 0.15f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel",
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            DownloadStatus.COMPLETED -> {
                IconButton(
                    onClick = onPlay,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            DownloadStatus.FAILED -> {
                IconButton(
                    onClick = onRetry,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Retry",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}