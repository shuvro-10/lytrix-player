package com.musicplayer.lytrix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicplayer.lytrix.model.Song

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    viewModel: PlayerViewModel,
    type: String,
    idString: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val playlistId = idString.toLongOrNull() ?: 0L
    val songsList by viewModel.songsList.collectAsState()
    val favIds by viewModel.favoriteSongIds.collectAsState()
    val recentIds by viewModel.recentSongIds.collectAsState()
    val mostPlayedIds by viewModel.mostPlayedSongIds.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    var playlistCustomSongs by remember { mutableStateOf<List<Long>>(emptyList()) }

    // Sort states
    var sortBy by remember { mutableStateOf("Newest") }

    // Bottom Sheet states
    var showMenuSheet by remember { mutableStateOf(false) }
    var showSortSheet by remember { mutableStateOf(false) }
    var showAddSongsSheet by remember { mutableStateOf(false) }
    var showManageSheet by remember { mutableStateOf(false) }
    var isManualArrangeMode by remember { mutableStateOf(false) }

    val playCounts by viewModel.playCounts.collectAsState()

    // 🔥 প্রিমিয়াম থিম স্টেট (অ্যাড ও ফন্ট চেঞ্জের জন্য)
    val currentPremiumTheme by viewModel.playerTheme.collectAsState()
    val isAdFreeLive by AdManager.isAdFreeFlow.collectAsState()
    val showAd = !isAdFreeLive && !AdManager.isAdFree(context)
    val adPosition = 3

    val poppinsFont = remember { androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.font.Font(com.musicplayer.lytrix.R.font.font_poppins)) }
    val quanticoFont = remember { androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.font.Font(com.musicplayer.lytrix.R.font.font_quantico)) }
    val montserratFont = remember { androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.font.Font(com.musicplayer.lytrix.R.font.font_montserrat)) }

    val themeFont = when (currentPremiumTheme) {
        "Cyber City" -> quanticoFont
        "Campside Rain", "Cat Rain", "Snowy Home" -> poppinsFont
        "Cartoon Walk", "Summer Home", "Summer Village" -> montserratFont
        else -> androidx.compose.ui.text.font.FontFamily.Default
    }

    LaunchedEffect(type, playlistId) {
        if (type == "custom") {
            viewModel.getPlaylistSongsFlow(playlistId).collect { ids ->
                playlistCustomSongs = ids
            }
        }
    }

    val displaySongs = remember(type, songsList, favIds, recentIds, mostPlayedIds, playlistCustomSongs, sortBy, idString) {
        val baseList = when (type) {
            "liked" -> favIds.mapNotNull { id -> songsList.find { it.id == id } }
            "recent" -> recentIds.mapNotNull { id -> songsList.find { it.id == id } }
            "most_played" -> mostPlayedIds.mapNotNull { id -> songsList.find { it.id == id } }
            "custom" -> playlistCustomSongs.mapNotNull { id -> songsList.find { it.id == id } }
            "artist" -> songsList.filter { if (idString == "Unknown Artist" || idString.isBlank()) (it.artist.isBlank() || it.artist.equals("Unknown Artist", ignoreCase = true) || it.artist == "<unknown>") else (it.artist.equals(idString, ignoreCase = true) || (idString == "<unknown>" && it.artist.isBlank())) }
            "album" -> songsList.filter { if (idString == "Unknown Album" || idString.isBlank()) (it.album.isBlank() || it.album.equals("Unknown Album", ignoreCase = true) || it.album == "<unknown>") else (it.album.equals(idString, ignoreCase = true) || (idString == "<unknown>" && it.album.isBlank())) }
            else -> emptyList()
        }

        when (sortBy) {
            "A to Z" -> baseList.sortedBy { it.title }
            "Most played" -> baseList.sortedByDescending { song -> mostPlayedIds.indexOf(song.id).let { if(it == -1) 9999 else it } }
            else -> baseList
        }
    }
    var pendingPlaylistExportFormat by remember { mutableStateOf<com.musicplayer.lytrix.playlist.ExportFormat?>(null) }
    val exportPlaylistLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.CreateDocument("*/*")
    ) { destUri ->
        val fmt = pendingPlaylistExportFormat
        pendingPlaylistExportFormat = null
        if (destUri != null && fmt != null) {
            val content = com.musicplayer.lytrix.playlist.PlaylistPortabilityManager.formatSongs(displaySongs, fmt)
            runCatching {
                context.contentResolver.openOutputStream(destUri)?.use { out ->
                    out.write(content.toByteArray(Charsets.UTF_8))
                    out.flush()
                }
            }.onSuccess {
                android.widget.Toast.makeText(context, "Exported ${displaySongs.size} songs (${fmt.extension.uppercase()})", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (isManualArrangeMode) {
        ManualArrangeScreen(
            type = type,
            idString = idString,
            viewModel = viewModel,
            initialSongs = displaySongs,
            onNavigateBack = { isManualArrangeMode = false }
        )
        return
    }

    val title = when (type) {
        "liked" -> "Liked Songs"
        "recent" -> "Recently Played"
        "most_played" -> "Most Played"
        "artist" -> idString
        "album" -> idString
        else -> viewModel.playlists.value.find { it.id == playlistId }?.name ?: "Playlist"
    }

    val accentColor = com.musicplayer.lytrix.ui.theme.LocalAccentColor.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        com.musicplayer.lytrix.ui.component.AmbientThemeGlow(
            tint = accentColor,
            intensity = 0.22f
        )

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(title, fontFamily = themeFont) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (type != "recent" && type != "most_played") {
                            IconButton(onClick = { showMenuSheet = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            containerColor = Color.Transparent
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = {
                        if (displaySongs.isNotEmpty()) {
                            viewModel.playSong(displaySongs.first(), displaySongs)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Play All", fontFamily = themeFont)
                }

                Button(
                    onClick = {
                        if (displaySongs.isNotEmpty()) {
                            viewModel.playSong(displaySongs.random(), displaySongs)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.Shuffle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Shuffle", fontFamily = themeFont)
                }
            }

            Text(
                text = "${displaySongs.size} tracks",
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = themeFont
            )

            LazyColumn(
                contentPadding = PaddingValues(bottom = if (currentSong != null) 120.dp else 40.dp)
            ) {
                // 🔥 ম্যাজিক: itemsIndexed ব্যবহার করে ৩ নাম্বার গানের পর ব্যানার অ্যাড
                itemsIndexed(items = displaySongs, key = { _, it -> it.id }) { index, song ->
                    val playCountText = if (type == "most_played") {
                        "${playCounts[song.id] ?: 0} ${getString("times").lowercase()}"
                    } else null

                    SongCardItem(
                        song = song,
                        isActive = currentSong?.id == song.id,
                        isPlaying = currentSong?.id == song.id && isPlaying,
                        activeColor = MaterialTheme.colorScheme.primary,
                        playCountText = playCountText,
                        currentTheme = currentPremiumTheme,
                        themeFont = themeFont,
                        onClick = {
                            viewModel.playSong(song, displaySongs)
                        }
                    )

                    // 🔥 ৩ নাম্বার গানের পর ব্যানার অ্যাড
                    if (index == adPosition - 1 && showAd) {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                            BannerAdView()
                        }
                    }
                }
            }
        }
        }
    }

    if (showMenuSheet) {
        com.musicplayer.lytrix.ui.component.LytrixFluidBottomSheet(
            onDismissRequest = { showMenuSheet = false },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                Text(getString("playlist_options"), modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold, fontFamily = themeFont)
                ListItem(
                    headlineContent = { Text(getString("songs_sort"), fontFamily = themeFont) },
                    modifier = Modifier.clickable { showMenuSheet = false; showSortSheet = true }
                )
                if (type == "custom") {
                    ListItem(
                        headlineContent = { Text(getString("add_songs"), fontFamily = themeFont) },
                        modifier = Modifier.clickable { showMenuSheet = false; showAddSongsSheet = true }
                    )
                    ListItem(
                        headlineContent = { Text(getString("manage_remove"), fontFamily = themeFont) },
                        modifier = Modifier.clickable { showMenuSheet = false; showManageSheet = true }
                    )
                }
                ListItem(
                    headlineContent = { Text(getString("manual_arrange"), fontFamily = themeFont) },
                    modifier = Modifier.clickable { showMenuSheet = false; isManualArrangeMode = true }
                )
                ListItem(
                    headlineContent = { Text("Export as M3U8", fontFamily = themeFont) },
                    modifier = Modifier.clickable {
                        showMenuSheet = false
                        pendingPlaylistExportFormat = com.musicplayer.lytrix.playlist.ExportFormat.M3U8
                        exportPlaylistLauncher.launch("Lytrix_${type}_${idString}.m3u8")
                    }
                )
                ListItem(
                    headlineContent = { Text("Export as CSV", fontFamily = themeFont) },
                    modifier = Modifier.clickable {
                        showMenuSheet = false
                        pendingPlaylistExportFormat = com.musicplayer.lytrix.playlist.ExportFormat.CSV
                        exportPlaylistLauncher.launch("Lytrix_${type}_${idString}.csv")
                    }
                )
            }
        }
    }

    if (showSortSheet) {
        com.musicplayer.lytrix.ui.component.LytrixFluidBottomSheet(
            onDismissRequest = { showSortSheet = false },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                Text(getString("sort_by"), modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold, fontFamily = themeFont)
                val sortOptions = listOf("Newest" to getString("newest"), "Most played" to getString("most_played"), "A to Z" to getString("a_to_z"))
                sortOptions.forEach { (optionKey, optionLabel) ->
                    ListItem(
                        headlineContent = { Text(optionLabel, fontFamily = themeFont) },
                        modifier = Modifier.clickable {
                            sortBy = optionKey
                            showSortSheet = false
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = if (sortBy == optionKey) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        )
                    )
                }
            }
        }
    }

    if (showAddSongsSheet) {
        MultiSelectSongsSheet(
            title = getString("add_songs"),
            allSongs = songsList,
            preSelectedIds = playlistCustomSongs,
            onDismiss = { showAddSongsSheet = false },
            onSave = { selectedIds ->
                val newIds = selectedIds.filter { !playlistCustomSongs.contains(it) }
                viewModel.addSongsToPlaylist(playlistId, newIds)
                showAddSongsSheet = false
            },
            saveButtonText = getString("save"),
            themeFont = themeFont
        )
    }

    if (showManageSheet) {
        MultiSelectSongsSheet(
            title = getString("manage_remove"),
            allSongs = displaySongs,
            preSelectedIds = emptyList(),
            onDismiss = { showManageSheet = false },
            onSave = { selectedIds ->
                viewModel.removeSongsFromPlaylist(playlistId, selectedIds)
                showManageSheet = false
            },
            saveButtonText = getString("remove_selected"),
            themeFont = themeFont
        )
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiSelectSongsSheet(
    title: String,
    allSongs: List<Song>,
    preSelectedIds: List<Long>,
    onDismiss: () -> Unit,
    onSave: (List<Long>) -> Unit,
    saveButtonText: String = "Save",
    themeFont: androidx.compose.ui.text.font.FontFamily = androidx.compose.ui.text.font.FontFamily.Default
) {
    var selectedIds by remember { mutableStateOf(preSelectedIds.toSet()) }
    val isRemove = title.contains("remove", ignoreCase = true) || saveButtonText.contains("remove", ignoreCase = true)

    com.musicplayer.lytrix.ui.component.LytrixFluidBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    fontFamily = themeFont,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.width(12.dp))

                val buttonText = if (selectedIds.isNotEmpty()) {
                    if (isRemove) "Remove (${selectedIds.size})" else "Save (${selectedIds.size})"
                } else {
                    saveButtonText
                }

                val hasSelection = selectedIds.isNotEmpty()
                val btnBgColor = if (isRemove) {
                    if (hasSelection) Color(0xFFEF4444) else MaterialTheme.colorScheme.surfaceContainerHighest
                } else {
                    if (hasSelection) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest
                }
                val btnContentColor = if (hasSelection) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)

                Button(
                    onClick = {
                        if (hasSelection || !isRemove) {
                            onSave(selectedIds.toList())
                        }
                    },
                    enabled = hasSelection || !isRemove,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = btnBgColor,
                        contentColor = btnContentColor,
                        disabledContainerColor = btnBgColor,
                        disabledContentColor = btnContentColor
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(
                        text = buttonText,
                        fontFamily = themeFont,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 48.dp)
            ) {
                items(items = allSongs, key = { it.id }) { song ->
                    val isSelected = selectedIds.contains(song.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                selectedIds = if (isSelected) selectedIds - song.id else selectedIds + song.id
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = null,
                            colors = CheckboxDefaults.colors(
                                checkedColor = if (isRemove) Color(0xFFEF4444) else MaterialTheme.colorScheme.primary
                            )
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = song.title,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontFamily = themeFont,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = song.artist,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = themeFont
                            )
                        }
                    }
                }
            }
        }
    }
}