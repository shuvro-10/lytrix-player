package com.musicplayer.lytrix.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import coil.request.CachePolicy
import com.musicplayer.lytrix.model.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun VideosScreen(
    videosViewModel: com.musicplayer.lytrix.ui.VideosViewModel = viewModel(),
    playerViewModel: com.musicplayer.lytrix.ui.PlayerViewModel,
    searchQuery: String = "",
    sortOption: com.musicplayer.lytrix.ui.PlayerViewModel.SortOption = com.musicplayer.lytrix.ui.PlayerViewModel.SortOption.NEWEST,
    onNavigateToSettings: () -> Unit,
    onPlayVideo: (com.musicplayer.lytrix.model.Video) -> Unit = {},
    currentTheme: String = "Solid Default",
    themeFont: androidx.compose.ui.text.font.FontFamily = androidx.compose.ui.text.font.FontFamily.Default
) {
    val context = LocalContext.current
    val allVideos by videosViewModel.videosList.collectAsState()
    val hiddenIds by playerViewModel.hiddenSongIds.collectAsState()

    val videos = remember(allVideos, searchQuery, sortOption) {
        val filtered = if (searchQuery.isBlank()) allVideos else {
            allVideos.filter { it.title.contains(searchQuery, ignoreCase = true) }
        }
        when (sortOption) {
            com.musicplayer.lytrix.ui.PlayerViewModel.SortOption.NEWEST -> filtered.sortedByDescending { it.dateModified }
            com.musicplayer.lytrix.ui.PlayerViewModel.SortOption.A_TO_Z -> filtered.sortedBy { it.title }
            com.musicplayer.lytrix.ui.PlayerViewModel.SortOption.LONGEST_DURATION -> filtered.sortedByDescending { it.durationText }
            else -> filtered
        }
    }

    val accentColor = com.musicplayer.lytrix.ui.theme.LocalAccentColor.current
    val gridViewSize = com.musicplayer.lytrix.ui.theme.LocalGridViewSize.current

    var selectedVideos by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val isSelectionMode = selectedVideos.isNotEmpty()

    var showRenameDialogFor by remember { mutableStateOf<Video?>(null) }
    var renameText by remember { mutableStateOf("") }
    var showTrashConfirmForVideos by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    var pendingRenameVideo by remember { mutableStateOf<Video?>(null) }
    var pendingRenameName by remember { mutableStateOf("") }

    val renameLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            pendingRenameVideo?.let { video -> scope.launch { videosViewModel.renameVideo(context, video, pendingRenameName) } }
        }
    }

    val db = remember { com.musicplayer.lytrix.db.AppDatabase.getDatabase(context) }
    val prefs = remember { context.getSharedPreferences("app_preferences", android.content.Context.MODE_PRIVATE) }

    var showVideoHistoryPref by remember { mutableStateOf(prefs.getBoolean("show_video_history", true)) }
    var videoHistory by remember { mutableStateOf<List<com.musicplayer.lytrix.db.VideoHistoryEntity>>(emptyList()) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }

    LaunchedEffect(hiddenIds, allVideos) {
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            val showPref = prefs.getBoolean("show_video_history", true)
            val history = if (showPref) db.videoHistoryDao().getRecentVideos() else emptyList()
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                showVideoHistoryPref = showPref
                videoHistory = history
            }
        }
        videosViewModel.loadVideos(context, hiddenIds)
    }

    val handlePlayVideo: (Video) -> Unit = { video ->
        if (showVideoHistoryPref) {
            scope.launch(Dispatchers.IO) {
                try {
                    db.videoHistoryDao().insertVideoHistory(
                        com.musicplayer.lytrix.db.VideoHistoryEntity(
                            uriString = video.uriString,
                            title = video.title,
                            duration = 0L,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                    videoHistory = db.videoHistoryDao().getRecentVideos()
                } catch (e: Exception) { e.printStackTrace() }
            }
        }

        onPlayVideo(video)

        scope.launch {
            kotlinx.coroutines.delay(500)
            prefs.edit().putBoolean("is_video_playing", true).apply()
        }
    }

    val isAdFreeLive by com.musicplayer.lytrix.ui.AdManager.isAdFreeFlow.collectAsState()
    val showAd = !isAdFreeLive && !com.musicplayer.lytrix.ui.AdManager.isAdFree(context)
    val adPosition = 3

    // 🔥 ফাস্ট স্ক্রলারের জন্য স্টেট
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()

    Box(modifier = Modifier.fillMaxSize()) {
            if (videos.isEmpty() && videoHistory.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No videos found", fontFamily = themeFont, color = MaterialTheme.colorScheme.onBackground.copy(alpha=0.5f))
                }
            } else {
                if (gridViewSize <= 1) {
                    // =========================
                    // LIST VIEW (ভিডিও লিস্ট)
                    // =========================
                    com.musicplayer.lytrix.ui.PremiumFastScroller(
                        listState = listState,
                        accentColor = accentColor,
                        currentTheme = currentTheme
                    ) {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(bottom = 120.dp, top = if (isSelectionMode) 72.dp else 8.dp)
                        ) {
                            if (showVideoHistoryPref && videoHistory.isNotEmpty() && !isSelectionMode && searchQuery.isBlank()) {
                                item {
                                    VideoHistorySection(
                                        historyList = videoHistory,
                                        allVideos = allVideos,
                                        themeFont = themeFont,
                                        onClearClick = { showClearHistoryDialog = true },
                                        onHistoryItemClick = { uri ->
                                            val videoToPlay = allVideos.find { it.uriString == uri }
                                            if (videoToPlay != null) handlePlayVideo(videoToPlay)
                                            else android.widget.Toast.makeText(
                                                context,
                                                "Video not found",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }

                            itemsIndexed(items = videos, key = { _, it -> it.id }) { index, video ->
                                val isSelected = selectedVideos.contains(video.id)
                                VideoListItem(
                                    video = video,
                                    isSelectionMode = isSelectionMode,
                                    isSelected = isSelected,
                                    accentColor = accentColor,
                                    currentTheme = currentTheme,
                                    themeFont = themeFont,
                                    onClick = {
                                        if (isSelectionMode) selectedVideos =
                                            if (isSelected) selectedVideos - video.id else selectedVideos + video.id else handlePlayVideo(
                                            video
                                        )
                                    },
                                    onLongClick = {
                                        selectedVideos =
                                            if (isSelected) selectedVideos - video.id else selectedVideos + video.id
                                    },
                                    onEditClick = {
                                        renameText = video.title; showRenameDialogFor = video
                                    }
                                )

                                if (index == adPosition - 1 && showAd) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth()
                                            .padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        com.musicplayer.lytrix.ui.BannerAdView()
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // =========================
                    // GRID VIEW (ভিডিও গ্রিড)
                    // =========================
                    // 🔥 গ্রিডের জন্য ফাস্ট স্ক্রলার অ্যাড করা হলো না কারণ LazyVerticalGrid এ listState কাজ করে না।
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(gridViewSize),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = if (isSelectionMode) 72.dp else 8.dp, bottom = 120.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {

                        if (showVideoHistoryPref && videoHistory.isNotEmpty() && !isSelectionMode && searchQuery.isBlank()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                VideoHistorySection(
                                    historyList = videoHistory,
                                    allVideos = allVideos,
                                    themeFont = themeFont,
                                    onClearClick = { showClearHistoryDialog = true },
                                    onHistoryItemClick = { uri ->
                                        val videoToPlay = allVideos.find { it.uriString == uri }
                                        if (videoToPlay != null) handlePlayVideo(videoToPlay)
                                        else android.widget.Toast.makeText(context, "Video not found", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }

                        val beforeAdCount = minOf(adPosition, videos.size)

                        items(count = beforeAdCount, key = { index -> videos[index].id }) { index ->
                            val video = videos[index]
                            val isSelected = selectedVideos.contains(video.id)
                            VideoGridItem(
                                video = video, isSelectionMode = isSelectionMode, isSelected = isSelected, accentColor = accentColor, currentTheme = currentTheme, themeFont = themeFont,
                                onClick = { if (isSelectionMode) selectedVideos = if (isSelected) selectedVideos - video.id else selectedVideos + video.id else handlePlayVideo(video) },
                                onLongClick = { selectedVideos = if (isSelected) selectedVideos - video.id else selectedVideos + video.id },
                                onEditClick = { renameText = video.title; showRenameDialogFor = video }
                            )
                        }

                        if (showAd && videos.size >= adPosition) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                                    com.musicplayer.lytrix.ui.BannerAdView()
                                }
                            }
                        }

                        val afterAdCount = maxOf(0, videos.size - adPosition)
                        if (afterAdCount > 0) {
                            items(count = afterAdCount, key = { index -> videos[index + adPosition].id }) { index ->
                                val video = videos[index + adPosition]
                                val isSelected = selectedVideos.contains(video.id)
                                VideoGridItem(
                                    video = video, isSelectionMode = isSelectionMode, isSelected = isSelected, accentColor = accentColor, currentTheme = currentTheme, themeFont = themeFont,
                                    onClick = { if (isSelectionMode) selectedVideos = if (isSelected) selectedVideos - video.id else selectedVideos + video.id else handlePlayVideo(video) },
                                    onLongClick = { selectedVideos = if (isSelected) selectedVideos - video.id else selectedVideos + video.id },
                                    onEditClick = { renameText = video.title; showRenameDialogFor = video }
                                )
                            }
                        }
                    }
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = isSelectionMode,
                enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200)) + androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(250)) { -it },
                exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(180)) + androidx.compose.animation.slideOutVertically(androidx.compose.animation.core.tween(200)) { -it },
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                com.musicplayer.lytrix.ui.component.FloatingSelectionBar(
                    selectedCount = selectedVideos.size,
                    onCloseClick = { selectedVideos = emptySet() },
                    onRenameClick = if (selectedVideos.size == 1) {
                        {
                            val vid = videos.find { it.id == selectedVideos.first() }
                            if (vid != null) {
                                renameText = vid.title
                                showRenameDialogFor = vid
                            }
                        }
                    } else null,
                    onHideClick = {
                        selectedVideos.forEach { videoId ->
                            val vid = videos.find { it.id == videoId }
                            if (vid != null) {
                                videosViewModel.hideVideo(vid)
                                playerViewModel.hideSong(-videoId)
                            }
                        }
                        selectedVideos = emptySet()
                    },
                    onDeleteClick = { showTrashConfirmForVideos = true },
                    onShareClick = {
                        val uriList = videos.filter { selectedVideos.contains(it.id) }.map { android.net.Uri.parse(it.uriString) }
                        if (uriList.isNotEmpty()) {
                            val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                type = "video/*"
                                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uriList))
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Videos"))
                        }
                        selectedVideos = emptySet()
                    }
                )
            }
        }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = { Text("Delete", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground) },
            text = { Text("Clear all the history", color = MaterialTheme.colorScheme.onBackground.copy(alpha=0.8f)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch(Dispatchers.IO) {
                        db.videoHistoryDao().clearHistory()
                        videoHistory = emptyList()
                    }
                    showClearHistoryDialog = false
                }) { Text("Ok", color = accentColor, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) { Text("Cancel", color = MaterialTheme.colorScheme.onBackground.copy(alpha=0.6f)) }
            }
        )
    }

    if (showRenameDialogFor != null) {
        val v = showRenameDialogFor!!
        AlertDialog(
            onDismissRequest = { showRenameDialogFor = null },
            title = { Text("Video Info & Rename") },
            text = {
                Column {
                    OutlinedTextField(value = renameText, onValueChange = { renameText = it }, label = { Text("File Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Duration: ${v.durationText}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha=0.6f))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Size: ${v.sizeText}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha=0.6f))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Path: ${v.path}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha=0.6f))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (renameText.isNotBlank() && renameText != v.title) {
                        val newName = renameText.trim()
                        pendingRenameVideo = v
                        pendingRenameName = newName
                        scope.launch {
                            val intentSender = videosViewModel.renameVideo(context, v, newName)
                            if (intentSender != null) {
                                val request = IntentSenderRequest.Builder(intentSender as android.content.IntentSender).build()
                                renameLauncher.launch(request)
                            }
                        }
                    }
                    showRenameDialogFor = null
                }) { Text("Save", color = accentColor) }
            },
            dismissButton = { TextButton(onClick = { showRenameDialogFor = null }) { Text("Cancel") } }
        )
    }

    if (showTrashConfirmForVideos) {
        val uris = videos.filter { selectedVideos.contains(it.id) }.map { android.net.Uri.parse(it.uriString) }
        com.musicplayer.lytrix.ui.PremiumTrashConfirmDialog(
            uris = uris,
            onConfirm = {
                val toDelete = videos.filter { selectedVideos.contains(it.id) }
                toDelete.forEach { video -> videosViewModel.deleteVideo(context, video) }
                selectedVideos = emptySet()
            },
            onDismiss = { showTrashConfirmForVideos = false }
        )
    }
}

@Composable
fun VideoHistorySection(
    historyList: List<com.musicplayer.lytrix.db.VideoHistoryEntity>,
    allVideos: List<com.musicplayer.lytrix.model.Video>,
    themeFont: androidx.compose.ui.text.font.FontFamily,
    onClearClick: () -> Unit,
    onHistoryItemClick: (String) -> Unit
) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("History", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onBackground, fontFamily = themeFont)
            IconButton(onClick = onClearClick, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Clear History", tint = MaterialTheme.colorScheme.onBackground.copy(alpha=0.6f))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(historyList) { historyItem ->
                val linkedVideo = allVideos.find { it.uriString == historyItem.uriString }
                val durationStr = linkedVideo?.durationText ?: ""
                val artData = if (linkedVideo != null) rememberVideoCoverArt(context, linkedVideo) else historyItem.uriString
                Column(
                    modifier = Modifier
                        .width(150.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onHistoryItemClick(historyItem.uriString) }
                ) {
                    Box(modifier = Modifier.width(150.dp).height(85.dp).clip(RoundedCornerShape(12.dp)).background(Color.DarkGray)) {

                        // 🔥 ম্যাজিক: History Caching Policy অ্যাড করা হলো
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(artData)
                                .videoFrameMillis(1000)
                                .crossfade(true)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .build(),
                            contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                        )

                        if (durationStr.isNotEmpty()) {
                            Box(modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp)) {
                                Text("▶ $durationStr", color = Color.White, fontSize = 10.sp, fontFamily = themeFont)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(historyItem.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha=0.9f), fontFamily = themeFont, lineHeight = 14.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoListItem(
    video: com.musicplayer.lytrix.model.Video, isSelectionMode: Boolean, isSelected: Boolean, accentColor: Color, onClick: () -> Unit, onLongClick: () -> Unit, onEditClick: () -> Unit,
    currentTheme: String = "Solid Default", themeFont: androidx.compose.ui.text.font.FontFamily = androidx.compose.ui.text.font.FontFamily.Default
) {
    val context = LocalContext.current
    val isVideoThemeActive = currentTheme != "Solid Default"
    val themeColor = when(currentTheme) {
        "Cyber City" -> Color(0xFF9D4EDD)
        "Campside Rain" -> Color(0xFF10B981)
        "Cat Rain" -> Color(0xFF3B82F6)
        "Cartoon Walk" -> Color(0xFFF59E0B)
        "Summer Home" -> Color(0xFF00E5FF)
        "Summer Village" -> Color(0xFF00E676)
        "Snowy Home" -> Color(0xFF00BFFF)
        else -> accentColor
    }
    val defaultTextColor = if (isVideoThemeActive) Color.White else MaterialTheme.colorScheme.onSurface
    val activeUiColor = if (isVideoThemeActive) themeColor else accentColor
    val isLightAccent = (activeUiColor.red * 0.299f + activeUiColor.green * 0.587f + activeUiColor.blue * 0.114f) > 0.5f
    val panelBg = if (isSelected) (if (isVideoThemeActive) themeColor.copy(alpha = 0.25f) else activeUiColor.copy(alpha = 0.12f)) else Color.Transparent
    val titleTextColor = if (isSelected) activeUiColor else defaultTextColor

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(panelBg)
            .then(if (isSelected) Modifier.border(1.5.dp, activeUiColor, RoundedCornerShape(16.dp)) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelectionMode) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .then(
                        if (isSelected) {
                            Modifier
                                .clip(CircleShape)
                                .background(activeUiColor)
                        } else {
                            Modifier.border(2.dp, Color(0xFF6B7280).copy(alpha = 0.45f), CircleShape)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = if (isLightAccent) Color.Black else Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
        }

        Box(modifier = Modifier.size(80.dp, 50.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            val artData = rememberVideoCoverArt(context, video)

            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(artData)
                    .videoFrameMillis(1000)
                    .crossfade(true)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build(),
                contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(80.dp, 50.dp)
            )

            Box(modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp)) {
                Text(video.durationText, color = Color.White, fontSize = 10.sp, fontFamily = themeFont)
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(video.title, fontFamily = themeFont, color = titleTextColor, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
            Text(video.sizeText, fontFamily = themeFont, fontSize = 12.sp, color = defaultTextColor.copy(alpha = 0.7f))
        }
        if (isSelectionMode) {
            IconButton(onClick = onEditClick) {
                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = defaultTextColor.copy(alpha = 0.7f))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoGridItem(
    video: com.musicplayer.lytrix.model.Video, isSelectionMode: Boolean, isSelected: Boolean, accentColor: Color, onClick: () -> Unit, onLongClick: () -> Unit, onEditClick: () -> Unit,
    currentTheme: String = "Solid Default", themeFont: androidx.compose.ui.text.font.FontFamily = androidx.compose.ui.text.font.FontFamily.Default
) {
    val context = LocalContext.current
    val isVideoThemeActive = currentTheme != "Solid Default"
    val themeColor = when(currentTheme) {
        "Cyber City" -> Color(0xFF9D4EDD)
        "Campside Rain" -> Color(0xFF10B981)
        "Cat Rain" -> Color(0xFF3B82F6)
        "Cartoon Walk" -> Color(0xFFF59E0B)
        "Summer Home" -> Color(0xFF00E5FF)
        "Summer Village" -> Color(0xFF00E676)
        "Snowy Home" -> Color(0xFF00BFFF)
        else -> accentColor
    }
    val panelColor = if (isVideoThemeActive) Color.Black.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceContainer
    val activeUiColor = if (isVideoThemeActive) themeColor else accentColor

    Card(
        modifier = Modifier.fillMaxWidth().aspectRatio(1f).combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (isSelected) activeUiColor.copy(alpha = 0.2f) else (if (isVideoThemeActive) Color.Black.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceContainerHigh)),
        border = if (isSelected) BorderStroke(2.dp, activeUiColor) else null
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            // 🔥 নতুন লাইন: কভার আর্ট বের করা
            val artData = rememberVideoCoverArt(context, video)

            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(artData) // 🔥 আগে video.uriString ছিল, এখন artData হবে
                    .videoFrameMillis(1000)
                    .crossfade(true)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build(),
                contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
            )

            Box(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha=0.8f)))).padding(8.dp)) {
                Column {
                    Text(video.title, fontFamily = themeFont, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(video.durationText, fontFamily = themeFont, color = Color.White.copy(alpha=0.8f), fontSize = 10.sp)
                        if (isSelectionMode) { Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.White, modifier = Modifier.size(16.dp).clickable { onEditClick() }) }
                    }
                }
            }
            if (isSelectionMode) {
                val isLightAccent = (activeUiColor.red * 0.299f + activeUiColor.green * 0.587f + activeUiColor.blue * 0.114f) > 0.5f
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) activeUiColor else Color.Black.copy(alpha = 0.35f))
                        .border(2.dp, if (isSelected) activeUiColor else Color.White.copy(alpha = 0.7f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = if (isLightAccent) Color.Black else Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}
private val noEmbeddedArtVideoCache = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

@Composable
fun rememberVideoCoverArt(context: android.content.Context, video: com.musicplayer.lytrix.model.Video): Any {
    var artData by remember(video.id, video.uriString) { mutableStateOf<Any>(video.uriString) }

    LaunchedEffect(video.id, video.uriString) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val safeName = video.uriString.hashCode().toString()
            val cacheFile = java.io.File(context.cacheDir, "lytrix_vid_$safeName.jpg")

            if (cacheFile.exists() && cacheFile.length() > 0) {
                artData = cacheFile.absolutePath
                return@withContext
            }

            if (noEmbeddedArtVideoCache.contains(video.uriString)) {
                return@withContext
            }

            if (!video.uriString.startsWith("http")) {
                var retriever: android.media.MediaMetadataRetriever? = null
                try {
                    retriever = android.media.MediaMetadataRetriever()
                    retriever.setDataSource(context, android.net.Uri.parse(video.uriString))

                    val pic = retriever.embeddedPicture
                    if (pic != null && pic.isNotEmpty()) {
                        java.io.FileOutputStream(cacheFile).use { it.write(pic) }
                        artData = cacheFile.absolutePath
                    } else {
                        noEmbeddedArtVideoCache.add(video.uriString)
                    }
                } catch (e: Exception) {
                    noEmbeddedArtVideoCache.add(video.uriString)
                } finally {
                    try {
                        retriever?.release()
                    } catch (e: Exception) {}
                }
            }
        }
    }
    return artData
}
