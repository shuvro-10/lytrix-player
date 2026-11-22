package com.musicplayer.lytrix.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
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
import coil.ImageLoader
import coil.compose.rememberAsyncImagePainter
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.musicplayer.lytrix.model.Song

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiddenFilesScreen(
    viewModel: PlayerViewModel,
    onNavigateBack: () -> Unit
) {
    val hiddenSongs by viewModel.hiddenSongsDetailed.collectAsState()

    // 🔥 ওয়ার্নিং ফিক্স: mutableIntStateOf ব্যবহার করা হলো
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        viewModel.loadHiddenSongsDetailed()
    }

    val audioFiles = hiddenSongs.filter { !it.isVideo }
    val videoFiles = hiddenSongs.filter { it.isVideo }
    val displayList = if (selectedTabIndex == 0) audioFiles else videoFiles

    val accentColor = com.musicplayer.lytrix.ui.theme.LocalAccentColor.current
    var showMenuForSong by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Hidden files", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )

                // Audio & Video Tabs
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = accentColor,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                            color = accentColor,
                            height = 3.dp
                        )
                    }
                ) {
                    Tab(
                        selected = selectedTabIndex == 0,
                        onClick = { selectedTabIndex = 0 },
                        text = { Text("Audio", fontWeight = if (selectedTabIndex == 0) FontWeight.Bold else FontWeight.Normal) },
                        selectedContentColor = accentColor,
                        unselectedContentColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                    Tab(
                        selected = selectedTabIndex == 1,
                        onClick = { selectedTabIndex = 1 },
                        text = { Text("Video", fontWeight = if (selectedTabIndex == 1) FontWeight.Bold else FontWeight.Normal) },
                        selectedContentColor = accentColor,
                        unselectedContentColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (displayList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    text = if (selectedTabIndex == 0) "No hidden audio files" else "No hidden video files",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
            ) {
                items(displayList, key = { it.id }) { item ->
                    HiddenMediaItem(
                        song = item,
                        isMenuExpanded = showMenuForSong == item.id,
                        onMenuClick = { showMenuForSong = item.id },
                        onMenuDismiss = { showMenuForSong = null },
                        onUnhide = {
                            // 🔥 ম্যাজিক ফিক্স: এখন শুধু item.id পাঠানো হচ্ছে, তাই কোনো এরর আসবে না!
                            viewModel.unhideSong(item.id)
                            showMenuForSong = null
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun HiddenMediaItem(
    song: Song,
    isMenuExpanded: Boolean,
    onMenuClick: () -> Unit,
    onMenuDismiss: () -> Unit,
    onUnhide: () -> Unit
) {
    val context = LocalContext.current

    // ভিডিও থাম্বনেইল সাপোর্ট
    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Transparent)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Cover Art / Thumbnail
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            if (song.albumArtUriString != null && song.albumArtUriString.isNotBlank()) {
                val painter = rememberAsyncImagePainter(
                    model = ImageRequest.Builder(context)
                        .data(song.albumArtUriString)
                        .crossfade(true)
                        .build(),
                    imageLoader = imageLoader
                )
                Image(
                    painter = painter,
                    contentDescription = "Cover",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (song.isVideo) {
                    Icon(Icons.Default.PlayCircle, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(24.dp))
                }
            } else {
                Icon(
                    imageVector = if (song.isVideo) Icons.Default.PlayCircle else Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (song.isVideo) "Video File" else song.artist,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box {
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            }

            DropdownMenu(
                expanded = isMenuExpanded,
                onDismissRequest = onMenuDismiss,
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                DropdownMenuItem(
                    text = { Text("Unhide", fontWeight = FontWeight.SemiBold) },
                    onClick = onUnhide
                )
            }
        }
    }
}