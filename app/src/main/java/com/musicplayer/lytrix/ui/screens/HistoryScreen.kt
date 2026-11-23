package com.musicplayer.lytrix.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.musicplayer.lytrix.R
import com.musicplayer.lytrix.db.OnlineHistoryEntity
import com.musicplayer.lytrix.innertube.YouTube
import com.musicplayer.lytrix.innertube.models.SongItem
import com.musicplayer.lytrix.model.JioSaavnSong
import com.musicplayer.lytrix.ui.PlayerViewModel
import com.musicplayer.lytrix.ui.playOnlineSongWithAd
import com.musicplayer.lytrix.ui.playLytrixPlaylistWithAd
import com.musicplayer.lytrix.ui.theme.LocalAccentColor
import compose.icons.TablerIcons
import compose.icons.tablericons.*
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

enum class HistoryTab {
    LOCAL, REMOTE
}

sealed class DateAgo {
    data object Today : DateAgo()
    data object Yesterday : DateAgo()
    data object ThisWeek : DateAgo()
    data object LastWeek : DateAgo()
    class Other(val date: LocalDate) : DateAgo() {
        override fun equals(other: Any?): Boolean {
            if (other is Other) return date == other.date
            return super.equals(other)
        }
        override fun hashCode(): Int = date.hashCode()
    }
}

private fun formatRelativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    if (diff < 60_000) return "Just now"
    val minutes = diff / 60_000
    if (minutes < 60) return "${minutes}m ago"
    val hours = minutes / 60
    if (hours < 24) return "${hours}h ago"
    val days = hours / 24
    if (days < 7) return "${days}d ago"
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
    return date.format(DateTimeFormatter.ofPattern("MMM d"))
}

private fun dateAgoToString(dateAgo: DateAgo): String = when (dateAgo) {
    DateAgo.Today -> "Today"
    DateAgo.Yesterday -> "Yesterday"
    DateAgo.ThisWeek -> "This week"
    DateAgo.LastWeek -> "Last week"
    is DateAgo.Other -> dateAgo.date.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
}

private fun playLocalHistoryItem(
    context: Context,
    viewModel: PlayerViewModel,
    item: OnlineHistoryEntity,
    allItems: List<OnlineHistoryEntity>
) {
    if (item.source == "YOUTUBE") {
        val ytTracks = allItems.filter { it.source == "YOUTUBE" }.map {
            PlayerViewModel.YtMusicTrack(
                id = it.songId,
                title = it.title,
                artist = it.artist,
                thumbnailUrl = it.imageUrl
            )
        }
        val idx = ytTracks.indexOfFirst { it.id == item.songId }
        viewModel.playLytrixPlaylistWithAd(
            list = ytTracks,
            index = if (idx >= 0) idx else 0,
            context = context
        )
    } else {
        val jioSong = JioSaavnSong(
            id = item.songId,
            name = item.title,
            artist = item.artist,
            imageUrl = item.imageUrl,
            downloadUrl = item.uriString
        )
        val jioList = allItems.filter { it.source == "JIOSAAVN" }.map {
            JioSaavnSong(
                id = it.songId,
                name = it.title,
                artist = it.artist,
                imageUrl = it.imageUrl,
                downloadUrl = it.uriString
            )
        }
        viewModel.playOnlineSongWithAd(context, jioSong, jioList)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    viewModel: PlayerViewModel,
    onNavigateBack: () -> Unit
) {
    BackHandler { onNavigateBack() }
    val context = LocalContext.current
    val accentColor = LocalAccentColor.current

    val onlineHistory by viewModel.onlineHistory.collectAsState()
    val remoteHistoryPage by viewModel.remoteHistoryPage.collectAsState()
    val isRemoteHistoryLoading by viewModel.isRemoteHistoryLoading.collectAsState()

    val onlineLikedPrefs = remember { context.getSharedPreferences("online_liked_songs", Context.MODE_PRIVATE) }
    var likedSongIds by remember {
        mutableStateOf(onlineLikedPrefs.getStringSet("liked_ids", emptySet())?.toSet() ?: emptySet())
    }

    var selectedTab by rememberSaveable { mutableStateOf(HistoryTab.LOCAL) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var showNoticeBanner by rememberSaveable { mutableStateOf(true) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    var selectedItemForMenu by remember { mutableStateOf<OnlineHistoryEntity?>(null) }

    val isYouTubeLoggedIn = remember {
        YouTube.hasLoginCookie() || !YouTube.cookie.isNullOrBlank()
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == HistoryTab.REMOTE && isYouTubeLoggedIn && remoteHistoryPage == null) {
            viewModel.fetchRemoteHistory()
        }
    }

    val filteredLocalHistory = remember(onlineHistory, searchQuery) {
        val deduplicated = onlineHistory.distinctBy { it.songId }
        if (searchQuery.isBlank()) {
            deduplicated
        } else {
            deduplicated.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                    it.artist.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val zoneId = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zoneId) }
    val thisMonday = remember { today.with(DayOfWeek.MONDAY) }
    val lastMonday = remember { thisMonday.minusDays(7) }

    val groupedLocalHistory = remember(filteredLocalHistory) {
        filteredLocalHistory.groupBy { item ->
            val date = Instant.ofEpochMilli(item.timestamp).atZone(zoneId).toLocalDate()
            val daysAgo = ChronoUnit.DAYS.between(date, today).toInt()
            when {
                daysAgo == 0 -> DateAgo.Today
                daysAgo == 1 -> DateAgo.Yesterday
                date >= thisMonday -> DateAgo.ThisWeek
                date >= lastMonday -> DateAgo.LastWeek
                else -> DateAgo.Other(date.withDayOfMonth(1))
            }
        }.toSortedMap(
            compareBy { dateAgo ->
                when (dateAgo) {
                    DateAgo.Today -> 0L
                    DateAgo.Yesterday -> 1L
                    DateAgo.ThisWeek -> 2L
                    DateAgo.LastWeek -> 3L
                    is DateAgo.Other -> ChronoUnit.DAYS.between(dateAgo.date, today)
                }
            }
        )
    }

    val filteredRemoteSections = remember(remoteHistoryPage, searchQuery) {
        val sections = remoteHistoryPage?.sections ?: emptyList()
        if (searchQuery.isBlank()) {
            sections
        } else {
            sections.mapNotNull { section ->
                val songs = section.songs.filter { song ->
                    song.title.contains(searchQuery, ignoreCase = true) ||
                        song.artists.any { it.name.contains(searchQuery, ignoreCase = true) }
                }
                if (songs.isNotEmpty()) section.copy(songs = songs) else null
            }
        }
    }

    var isFabExpanded by remember { mutableStateOf(true) }
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -15) isFabExpanded = false
                if (available.y > 15) isFabExpanded = true
                return Offset.Zero
            }
        }
    }

    val lazyListState = rememberLazyListState()

    val hasSongsToShuffle = remember(selectedTab, filteredLocalHistory, filteredRemoteSections) {
        if (selectedTab == HistoryTab.LOCAL) {
            filteredLocalHistory.isNotEmpty()
        } else {
            filteredRemoteSections.any { it.songs.isNotEmpty() }
        }
    }

    // ── Clear All History Dialog ──────────────────────────────────────────────
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = {
                Text(
                    text = "Clear online history",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.clear_online_listen_history_confirm),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearOnlineListenHistory(context)
                        Toast.makeText(context, "Online history cleared", Toast.LENGTH_SHORT).show()
                        showClearConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // ── Song Item Options Bottom Sheet ────────────────────────────────────────
    selectedItemForMenu?.let { item ->
        val isItemLiked = remember(item, likedSongIds) {
            likedSongIds.contains(item.songId)
        }

        ModalBottomSheet(
            onDismissRequest = { selectedItemForMenu = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
            ) {
                // Header preview
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    ) {
                        AsyncImage(
                            model = item.imageUrl,
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = item.artist,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), modifier = Modifier.padding(bottom = 8.dp))

                // Play
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            selectedItemForMenu = null
                            playLocalHistoryItem(context, viewModel, item, filteredLocalHistory)
                        }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(TablerIcons.PlayerPlay, contentDescription = null, tint = accentColor, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Play", color = MaterialTheme.colorScheme.onBackground, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }

                // Add to Likes / Liked
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            val jioSong = JioSaavnSong(
                                id = item.songId,
                                name = item.title,
                                artist = item.artist,
                                imageUrl = item.imageUrl,
                                downloadUrl = item.uriString
                            )
                            viewModel.toggleOnlineFavorite(context, jioSong)
                            val updated = onlineLikedPrefs.getStringSet("liked_ids", emptySet())?.toSet() ?: emptySet()
                            likedSongIds = updated
                            selectedItemForMenu = null
                        }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isItemLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = null,
                        tint = if (isItemLiked) Color(0xFFE53935) else accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = if (isItemLiked) "Liked" else "Like song",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Delete from history
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            selectedItemForMenu = null
                            viewModel.deleteOnlineHistoryBySongId(item.songId)
                            Toast.makeText(context, "Removed from history", Toast.LENGTH_SHORT).show()
                        }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(TablerIcons.Trash, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Remove from history", color = MaterialTheme.colorScheme.error, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search history...", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                                unfocusedTextColor = MaterialTheme.colorScheme.onBackground
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.history),
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isSearchActive) {
                                isSearchActive = false
                                searchQuery = ""
                            } else {
                                onNavigateBack()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            isSearchActive = !isSearchActive
                            if (!isSearchActive) searchQuery = ""
                        }
                    ) {
                        Icon(
                            imageVector = if (isSearchActive) Icons.Rounded.Close else Icons.Rounded.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    if (selectedTab == HistoryTab.LOCAL && onlineHistory.isNotEmpty()) {
                        IconButton(onClick = { showClearConfirmDialog = true }) {
                            Icon(
                                imageVector = TablerIcons.Trash,
                                contentDescription = "Clear History",
                                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .nestedScroll(nestedScrollConnection)
        ) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 140.dp)
            ) {
                // ── Top ChipsRow (Local / Remote) ─────────────────────────────
                // Only show ChipsRow if user is logged into YouTube (Meld style)
                if (isYouTubeLoggedIn) {
                    item(key = "tab_chips") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            FilterChip(
                                selected = selectedTab == HistoryTab.LOCAL,
                                onClick = { selectedTab = HistoryTab.LOCAL },
                                label = { Text("App History", fontWeight = FontWeight.SemiBold) },
                                shape = RoundedCornerShape(16.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f),
                                    selectedContainerColor = accentColor,
                                    labelColor = MaterialTheme.colorScheme.onBackground,
                                    selectedLabelColor = Color.Black
                                ),
                                border = null
                            )

                            Spacer(modifier = Modifier.width(10.dp))

                            FilterChip(
                                selected = selectedTab == HistoryTab.REMOTE,
                                onClick = { selectedTab = HistoryTab.REMOTE },
                                label = { Text("YouTube Music", fontWeight = FontWeight.SemiBold) },
                                shape = RoundedCornerShape(16.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f),
                                    selectedContainerColor = accentColor,
                                    labelColor = MaterialTheme.colorScheme.onBackground,
                                    selectedLabelColor = Color.Black
                                ),
                                border = null
                            )
                        }
                    }
                }

                // ── Warning Banner in English (Requested by user) ─────────────
                if (selectedTab == HistoryTab.LOCAL && showNoticeBanner) {
                    item(key = "notice_banner") {
                        val isDarkHistory = com.musicplayer.lytrix.ui.theme.LocalIsDarkTheme.current
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = if (isDarkHistory) Color(0xFF1E1712) else MaterialTheme.colorScheme.surfaceContainerHigh),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFA726))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Info,
                                    contentDescription = null,
                                    tint = Color(0xFFFFA726),
                                    modifier = Modifier
                                        .size(22.dp)
                                        .padding(top = 2.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Online history is not backed up",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDarkHistory) Color(0xFFF7EBE5) else MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Listening history for online songs is stored locally on this device and not backed up to the cloud. Save your favorite songs by liking them or adding to a playlist.",
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp,
                                        color = if (isDarkHistory) Color(0xFFF7EBE5).copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = { showNoticeBanner = false },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Dismiss",
                                        tint = if (isDarkHistory) Color(0xFFF7EBE5).copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // ── TAB 1: LOCAL / APP HISTORY (YouTube + JioSaavn) ───────────
                if (selectedTab == HistoryTab.LOCAL) {
                    if (groupedLocalHistory.isEmpty()) {
                        item(key = "empty_local") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 80.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = TablerIcons.History,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = if (searchQuery.isNotBlank()) "No matching songs found" else "No listening history yet",
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    } else {
                        groupedLocalHistory.forEach { (dateAgo, itemsForDate) ->
                            stickyHeader(key = "header_${dateAgo.hashCode()}") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.background)
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = dateAgoToString(dateAgo),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = accentColor
                                    )
                                }
                            }

                            items(
                                items = itemsForDate,
                                key = { it.id }
                            ) { item ->
                                val isLiked = likedSongIds.contains(item.songId)
                                OnlineHistorySongRow(
                                    item = item,
                                    isLiked = isLiked,
                                    onPlay = {
                                        playLocalHistoryItem(context, viewModel, item, filteredLocalHistory)
                                    },
                                    onMoreClick = {
                                        selectedItemForMenu = item
                                    }
                                )
                            }
                        }
                    }
                }

                // ── TAB 2: REMOTE YOUTUBE MUSIC HISTORY ───────────────────────
                if (selectedTab == HistoryTab.REMOTE) {
                    if (!isYouTubeLoggedIn) {
                        item(key = "youtube_not_logged_in") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 60.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.04f))
                                        .padding(24.dp)
                                ) {
                                    Icon(
                                        imageVector = TablerIcons.BrandYoutube,
                                        contentDescription = null,
                                        tint = Color(0xFFFF0000),
                                        modifier = Modifier.size(56.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "YouTube Music Cloud History",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Sign in to your YouTube account from Online Settings to access your cloud listening history.",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                        lineHeight = 18.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else if (isRemoteHistoryLoading) {
                        item(key = "remote_loading") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 100.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = accentColor)
                            }
                        }
                    } else if (filteredRemoteSections.isEmpty()) {
                        item(key = "remote_empty") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 80.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = TablerIcons.History,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = if (searchQuery.isNotBlank()) "No matching YouTube songs found" else "No YouTube Music history found",
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    } else {
                        filteredRemoteSections.forEach { section ->
                            stickyHeader(key = "remote_header_${section.title}") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.background)
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = section.title,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = accentColor
                                    )
                                }
                            }

                            items(
                                items = section.songs,
                                key = { "${section.title}_${it.id}" }
                            ) { song ->
                                RemoteYouTubeSongRow(
                                    song = song,
                                    onPlay = {
                                        val tracks = section.songs.map { s ->
                                            PlayerViewModel.YtMusicTrack(
                                                id = s.id,
                                                title = s.title,
                                                artist = s.artists.joinToString(", ") { it.name },
                                                thumbnailUrl = s.thumbnail
                                            )
                                        }
                                        val idx = tracks.indexOfFirst { it.id == song.id }
                                        viewModel.playLytrixPlaylistWithAd(
                                            list = tracks,
                                            index = if (idx >= 0) idx else 0,
                                            context = context
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // ── Shuffle Floating Action Button (Meld + Lytrix Music Recognizer style) ──
            AnimatedVisibility(
                visible = isFabExpanded && hasSongsToShuffle,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 100.dp, end = 20.dp)
            ) {
                FloatingActionButton(
                    onClick = {
                        if (selectedTab == HistoryTab.LOCAL) {
                            val shuffled = filteredLocalHistory.shuffled()
                            if (shuffled.isNotEmpty()) {
                                playLocalHistoryItem(context, viewModel, shuffled.first(), shuffled)
                            }
                        } else {
                            val allRemoteSongs = filteredRemoteSections.flatMap { it.songs }.shuffled()
                            if (allRemoteSongs.isNotEmpty()) {
                                val ytTracks = allRemoteSongs.map { s ->
                                    PlayerViewModel.YtMusicTrack(
                                        id = s.id,
                                        title = s.title,
                                        artist = s.artists.joinToString(", ") { it.name },
                                        thumbnailUrl = s.thumbnail
                                    )
                                }
                                viewModel.playLytrixPlaylistWithAd(ytTracks, 0, context)
                            }
                        }
                    },
                    containerColor = accentColor,
                    contentColor = Color.Black,
                    shape = CircleShape,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shuffle,
                        contentDescription = "Shuffle Play",
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun OnlineHistorySongRow(
    item: OnlineHistoryEntity,
    isLiked: Boolean,
    onPlay: () -> Unit,
    onMoreClick: () -> Unit
) {
    val isYt = item.source == "YOUTUBE"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF222222))
        ) {
            AsyncImage(
                model = item.imageUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (isYt) Color(0x33FF0000) else Color(0x3300BFA5)
                ) {
                    Text(
                        text = if (isYt) "YouTube" else "JioSaavn",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isYt) Color(0xFFFF5252) else Color(0xFF00E676),
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = item.artist,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
        }

        if (isLiked) {
            Icon(
                imageVector = Icons.Rounded.Favorite,
                contentDescription = "Liked",
                tint = Color(0xFFE53935),
                modifier = Modifier
                    .size(16.dp)
                    .padding(end = 4.dp)
            )
        }

        Text(
            text = formatRelativeTime(item.timestamp),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
            modifier = Modifier.padding(horizontal = 6.dp)
        )

        IconButton(
            onClick = onMoreClick,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = "Options",
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun RemoteYouTubeSongRow(
    song: SongItem,
    onPlay: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF222222))
        ) {
            AsyncImage(
                model = song.thumbnail,
                contentDescription = song.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0x33FF0000)
                ) {
                    Text(
                        text = "YouTube",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF5252),
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = song.artists.joinToString(", ") { it.name },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
