package com.musicplayer.lytrix.ui

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import com.musicplayer.lytrix.ui.getString
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.request.videoFrameMillis
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.musicplayer.lytrix.model.Song
import androidx.compose.animation.core.*
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import android.content.Intent
import androidx.compose.animation.animateColorAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.lazy.grid.GridItemSpan
import kotlinx.coroutines.launch
// 🔥 আপনার বানানো ফাস্ট স্ক্রলার ইম্পোর্টের জন্য
import androidx.compose.foundation.lazy.rememberLazyListState

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SongsTabContent(
    filteredSongs: List<com.musicplayer.lytrix.model.Song>,
    currentSong: com.musicplayer.lytrix.model.Song?,
    isPlaying: Boolean,
    accentColor: Color,
    isLoading: Boolean = false,
    onSongClick: (com.musicplayer.lytrix.model.Song) -> Unit,
    onRenameClick: (Long) -> Unit = {},
    onHideClick: (Set<Long>) -> Unit = {},
    onDeleteClick: (Set<Long>) -> Unit = {},
    onAddToPlaylistClick: (Set<Long>) -> Unit = {},
    currentTheme: String = "Solid Default",
    themeFont: androidx.compose.ui.text.font.FontFamily = androidx.compose.ui.text.font.FontFamily.Default,
    onRefresh: ((onComplete: () -> Unit) -> Unit) = { it() }
) {
    // 🔥 ফায়ারবেস অ্যানালিটিক্স - সংস ট্যাব ট্র্যাকিং
    androidx.compose.runtime.LaunchedEffect(Unit) {
        AnalyticsManager.logScreenView("Songs_Tab")
    }

    var selectedSongs by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val isSelectionMode by remember { derivedStateOf { selectedSongs.isNotEmpty() } }
    var showTrashConfirmForSongs by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val isAdFreeLive by AdManager.isAdFreeFlow.collectAsState()
    val showAd = !isAdFreeLive && !AdManager.isAdFree(context)
    val adPosition = 3

    // 🔥 Music Journey - Database & Scope
    val db = remember { com.musicplayer.lytrix.db.AppDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()

    // গান প্লে করার সাথে সাথে অডিও হিস্ট্রিতে সেভ করার কমন ফাংশন
    val handlePlayAudio: (com.musicplayer.lytrix.model.Song) -> Unit = { song ->

        val activity = context as? android.app.Activity
        if (activity != null) {
            onSongClick(song)
        } else {
            onSongClick(song)
        }
    }

    // 🔥 লিস্ট স্টেট অ্যাড করা হলো ফাস্ট স্ক্রলারের জন্য
    val listState = rememberLazyListState()
    var isRefreshing by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                if (!isSelectionMode) {
                    isRefreshing = true
                    onRefresh {
                        isRefreshing = false
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        ) {
            if (isLoading) {
            LytrixSongListShimmer()
        } else if (filteredSongs.isEmpty()) {
            EmptyStateView()
        } else {
            val gridViewSize = com.musicplayer.lytrix.ui.theme.LocalGridViewSize.current
            if (gridViewSize <= 1) {

                // 🔥 ম্যাজিক: আপনার LazyColumn-কে PremiumFastScroller এর ভেতরে ঢুকিয়ে দেওয়া হয়েছে
                PremiumFastScroller(
                    listState = listState,
                    accentColor = accentColor,
                    currentTheme = currentTheme
                ) {
                    LazyColumn(
                        state = listState, // 🔥 স্টেট পাস করা হলো
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 120.dp, top = if (isSelectionMode) 72.dp else 8.dp)
                    ) {
                        itemsIndexed(items = filteredSongs, key = { _, it -> it.id }) { index, song ->
                            val isCurrent = song.id == currentSong?.id
                            val isSelected = selectedSongs.contains(song.id)
                            SongCardItem(
                                song = song, isActive = isCurrent, isPlaying = isCurrent && isPlaying, activeColor = accentColor,
                                isSelectionMode = isSelectionMode, isSelected = isSelected, currentTheme = currentTheme, themeFont = themeFont,
                                onLongClick = { selectedSongs = if (isSelected) selectedSongs - song.id else selectedSongs + song.id },
                                onClick = {
                                    if (isSelectionMode) {
                                        selectedSongs = if (isSelected) selectedSongs - song.id else selectedSongs + song.id
                                    } else {
                                        handlePlayAudio(song)
                                    }
                                }
                            )

                            if (index == adPosition - 1 && showAd) {
                                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                                    BannerAdView()
                                }
                            }
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(gridViewSize),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = if (isSelectionMode) 72.dp else 8.dp, bottom = 120.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    val beforeAdCount = minOf(adPosition, filteredSongs.size)
                    items(count = beforeAdCount, key = { index -> filteredSongs[index].id }) { index ->
                        val song = filteredSongs[index]
                        val isCurrent = song.id == currentSong?.id
                        val isSelected = selectedSongs.contains(song.id)
                        SongGridCardItem(
                            song = song, isActive = isCurrent, isPlaying = isCurrent && isPlaying, activeColor = accentColor,
                            isSelectionMode = isSelectionMode, isSelected = isSelected, currentTheme = currentTheme, themeFont = themeFont,
                            onLongClick = { selectedSongs = if (isSelected) selectedSongs - song.id else selectedSongs + song.id },
                            onClick = {
                                if (isSelectionMode) {
                                    selectedSongs = if (isSelected) selectedSongs - song.id else selectedSongs + song.id
                                } else {
                                    handlePlayAudio(song)
                                }
                            }
                        )
                    }

                    if (showAd && filteredSongs.size >= adPosition) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                                BannerAdView()
                            }
                        }
                    }

                    val afterAdCount = maxOf(0, filteredSongs.size - adPosition)
                    if (afterAdCount > 0) {
                        items(count = afterAdCount, key = { index -> filteredSongs[index + adPosition].id }) { index ->
                            val song = filteredSongs[index + adPosition]
                            val isCurrent = song.id == currentSong?.id
                            val isSelected = selectedSongs.contains(song.id)
                            SongGridCardItem(
                                song = song, isActive = isCurrent, isPlaying = isCurrent && isPlaying, activeColor = accentColor,
                                isSelectionMode = isSelectionMode, isSelected = isSelected, currentTheme = currentTheme, themeFont = themeFont,
                                onLongClick = { selectedSongs = if (isSelected) selectedSongs - song.id else selectedSongs + song.id },
                                onClick = {
                                    if (isSelectionMode) {
                                        selectedSongs = if (isSelected) selectedSongs - song.id else selectedSongs + song.id
                                    } else {
                                        handlePlayAudio(song)
                                    }
                                }
                            )
                        }
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
                selectedCount = selectedSongs.size,
                onCloseClick = { selectedSongs = emptySet() },
                onRenameClick = if (selectedSongs.size == 1) {
                    { onRenameClick(selectedSongs.first()); selectedSongs = emptySet() }
                } else null,
                onHideClick = { onHideClick(selectedSongs); selectedSongs = emptySet() },
                onDeleteClick = { showTrashConfirmForSongs = true },
                onShareClick = {
                    val uriList = filteredSongs.filter { selectedSongs.contains(it.id) }.map { android.net.Uri.parse(it.uriString) }
                    if (uriList.isNotEmpty()) {
                        val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                            type = "audio/*"
                            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uriList))
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Songs"))
                    }
                    selectedSongs = emptySet()
                },
                onAddToPlaylistClick = { onAddToPlaylistClick(selectedSongs); selectedSongs = emptySet() }
            )
        }
    }

    if (showTrashConfirmForSongs) {
        val uris = remember(selectedSongs, filteredSongs) {
            filteredSongs.filter { selectedSongs.contains(it.id) }.map { android.net.Uri.parse(it.uriString) }
        }
        PremiumTrashConfirmDialog(
            uris = uris,
            onConfirm = { onDeleteClick(selectedSongs); selectedSongs = emptySet() },
            onDismiss = { showTrashConfirmForSongs = false }
        )
    }
}
@Composable
fun EmptyStateView() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Outlined.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f),
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "No Tracks Found",
            fontWeight = FontWeight.Light,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SongCardItem(
    song: Song,
    isActive: Boolean,
    isPlaying: Boolean,
    activeColor: Color,
    playCountText: String? = null,
    showDragHandle: Boolean = false,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
    currentTheme: String = "Solid Default",
    themeFont: androidx.compose.ui.text.font.FontFamily = androidx.compose.ui.text.font.FontFamily.Default,
    onRefresh: ((onComplete: () -> Unit) -> Unit) = { it() }
) {
    val infiniteTransition = rememberInfiniteTransition(label = "glow_anim_card")

    val themeColors = when(currentTheme) {
        "Cyber City" -> listOf(Color(0xFF9D4EDD), Color(0xFF00F2FE))
        "Campside Rain" -> listOf(Color(0xFF10B981), Color(0xFFA5D6A7))
        "Cat Rain" -> listOf(Color(0xFF3B82F6), Color(0xFF87CEEB))
        "Cartoon Walk" -> listOf(Color(0xFFF59E0B), Color(0xFFFFD700))
        "Summer Home" -> listOf(Color(0xFF00E5FF), Color(0xFF84FFFF))
        "Summer Village" -> listOf(Color(0xFF00E676), Color(0xFF69F0AE))
        "Snowy Home" -> listOf(Color(0xFF00BFFF), Color(0xFFE0FFFF))
        else -> listOf(activeColor, activeColor.copy(alpha = 0.5f))
    }

    val gradientOffset by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart),
        label = "gradient_offset_card"
    )

    val borderModifier = when {
        isSelected -> Modifier.border(1.5.dp, activeColor, RoundedCornerShape(16.dp))
        isActive && currentTheme != "Solid Default" -> {
            val borderBrush = Brush.linearGradient(
                colors = themeColors,
                start = androidx.compose.ui.geometry.Offset(gradientOffset, 0f),
                end = androidx.compose.ui.geometry.Offset(gradientOffset + 500f, 500f)
            )
            Modifier.border(2.dp, borderBrush, RoundedCornerShape(16.dp))
        }
        isActive -> Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
        else -> Modifier
    }

    val isVideoThemeActive = currentTheme != "Solid Default"

    val defaultPanelColor = Color.Transparent
    val activePanelColor = if (isVideoThemeActive) themeColors[0].copy(alpha = 0.25f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    val selectedPanelColor = activeColor.copy(alpha = 0.12f)

    val cardBgColor by animateColorAsState(
        targetValue = when {
            isSelected -> selectedPanelColor
            isActive -> activePanelColor
            else -> defaultPanelColor
        },
        animationSpec = tween(300)
    )

    val defaultTextColor = if (isVideoThemeActive) Color.White else MaterialTheme.colorScheme.onSurface
    val animatedText by animateColorAsState(
        targetValue = when {
            isSelected -> activeColor
            isActive -> if (isVideoThemeActive) themeColors[0] else MaterialTheme.colorScheme.primary
            else -> defaultTextColor
        },
        animationSpec = tween(300)
    )

    val context = LocalContext.current

    val artData = rememberLibraryCoverArt(context, song)
    val imageRequest = remember(artData, song.uriString) {
        val dataToLoad = artData ?: song.uriString
        val reqBuilder = ImageRequest.Builder(context)
            .data(dataToLoad)
            .crossfade(true)
            .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
            .diskCachePolicy(coil.request.CachePolicy.ENABLED)

        if ((artData == null || dataToLoad is String) && song.isVideo) {
            reqBuilder.videoFrameMillis(1000)
        }
        reqBuilder.build()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(cardBgColor)
            .then(borderModifier)
            .bouncePress()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
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
                                .background(activeColor)
                        } else {
                            Modifier.border(2.dp, Color(0xFF6B7280).copy(alpha = 0.45f), CircleShape)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    val isLightAccent = (activeColor.red * 0.299f + activeColor.green * 0.587f + activeColor.blue * 0.114f) > 0.5f
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

        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .border(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
        ) {
            AsyncImage(
                model = imageRequest,
                contentDescription = "Cover",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(54.dp)
            )
            if (isActive) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
            .verticalScroll(rememberScrollState())
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    LytrixEqualizerWaves(isPlaying = isPlaying, barColor = if (isVideoThemeActive) themeColors[0] else activeColor, maxHeight = 20.dp, barWidth = 3.5.dp, spacing = 2.5.dp)
                }
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                fontFamily = themeFont,
                fontSize = 16.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                color = animatedText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (song.hasLyrics) {
                    Box(modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isVideoThemeActive) themeColors[0].copy(alpha = 0.2f) else activeColor.copy(alpha = 0.2f))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text("LYRICS", fontFamily = themeFont, fontSize = 8.sp, color = if (isVideoThemeActive) themeColors[0] else activeColor, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }
                if (song.artist == "Music Video" || song.album == "Video") {
                    Box(modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isVideoThemeActive) themeColors[0].copy(alpha = 0.2f) else activeColor.copy(alpha = 0.2f))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text("MV", fontFamily = themeFont, fontSize = 8.sp, color = if (isVideoThemeActive) themeColors[0] else activeColor, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = playCountText ?: song.artist,
                    fontFamily = themeFont,
                    fontSize = 14.sp,
                    color = if (isActive) (if(isVideoThemeActive) themeColors[0].copy(alpha = 0.8f) else activeColor.copy(alpha = 0.8f)) else defaultTextColor.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (showDragHandle) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = "Drag to reorder",
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SongGridCardItem(
    song: Song,
    isActive: Boolean,
    isPlaying: Boolean,
    activeColor: Color,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
    currentTheme: String = "Solid Default",
    themeFont: androidx.compose.ui.text.font.FontFamily = androidx.compose.ui.text.font.FontFamily.Default,
    onRefresh: ((onComplete: () -> Unit) -> Unit) = { it() }
) {
    val infiniteTransition = rememberInfiniteTransition(label = "glow_anim_grid")

    val themeColors = when(currentTheme) {
        "Cyber City" -> listOf(Color(0xFF9D4EDD), Color(0xFF00F2FE))
        "Campside Rain" -> listOf(Color(0xFF10B981), Color(0xFFA5D6A7))
        "Cat Rain" -> listOf(Color(0xFF3B82F6), Color(0xFF87CEEB))
        "Cartoon Walk" -> listOf(Color(0xFFF59E0B), Color(0xFFFFD700))
        "Summer Home" -> listOf(Color(0xFF00E5FF), Color(0xFF84FFFF))
        "Summer Village" -> listOf(Color(0xFF00E676), Color(0xFF69F0AE))
        "Snowy Home" -> listOf(Color(0xFF00BFFF), Color(0xFFE0FFFF))
        else -> listOf(activeColor, activeColor.copy(alpha = 0.5f))
    }

    val gradientOffset by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart),
        label = "gradient_offset_grid"
    )

    val borderModifier = when {
        isSelected -> Modifier.border(1.5.dp, activeColor, RoundedCornerShape(20.dp))
        isActive && currentTheme != "Solid Default" -> {
            val borderBrush = Brush.linearGradient(
                colors = themeColors,
                start = androidx.compose.ui.geometry.Offset(gradientOffset, 0f),
                end = androidx.compose.ui.geometry.Offset(gradientOffset + 500f, 500f)
            )
            Modifier.border(2.dp, borderBrush, RoundedCornerShape(20.dp))
        }
        isActive -> Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp))
        else -> Modifier
    }

    val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val isVideoThemeActive = currentTheme != "Solid Default"

    val defaultPanelColor = Color.Transparent
    val activePanelColor = if (isVideoThemeActive) themeColors[0].copy(alpha = 0.25f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    val selectedPanelColor = activeColor.copy(alpha = 0.12f)

    val cardBgColor by animateColorAsState(
        targetValue = when {
            isSelected -> selectedPanelColor
            isActive -> activePanelColor
            else -> defaultPanelColor
        },
        animationSpec = tween(300)
    )

    val defaultTextColor = if (isVideoThemeActive) Color.White else MaterialTheme.colorScheme.onSurface
    val animatedText by animateColorAsState(
        targetValue = when {
            isSelected -> activeColor
            isActive -> if (isVideoThemeActive) themeColors[0] else MaterialTheme.colorScheme.primary
            else -> defaultTextColor
        },
        animationSpec = tween(300)
    )

    val context = LocalContext.current

    val artData = rememberLibraryCoverArt(context, song)
    val imageRequest = remember(artData, song.uriString) {
        val dataToLoad = artData ?: song.uriString
        val reqBuilder = ImageRequest.Builder(context)
            .data(dataToLoad)
            .crossfade(true)
            .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
            .diskCachePolicy(coil.request.CachePolicy.ENABLED)

        if ((artData == null || dataToLoad is String) && song.isVideo) {
            reqBuilder.videoFrameMillis(1000)
        }
        reqBuilder.build()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBgColor)
            .then(borderModifier)
            .bouncePress()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
        ) {
            AsyncImage(
                model = imageRequest,
                contentDescription = "Cover",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (isSelectionMode) {
                val isLightAccent = (activeColor.red * 0.299f + activeColor.green * 0.587f + activeColor.blue * 0.114f) > 0.5f
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .size(24.dp)
                        .align(Alignment.TopStart)
                        .clip(CircleShape)
                        .background(if (isSelected) activeColor else Color.Black.copy(alpha = 0.35f))
                        .border(2.dp, if (isSelected) activeColor else Color.White.copy(alpha = 0.7f), CircleShape),
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
            if (isActive) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
            .verticalScroll(rememberScrollState())
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    LytrixEqualizerWaves(isPlaying = isPlaying, barColor = if (isVideoThemeActive) themeColors[0] else activeColor, maxHeight = 20.dp, barWidth = 3.5.dp, spacing = 2.5.dp)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = song.title,
            fontFamily = themeFont,
            fontSize = 14.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
            color = animatedText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (song.artist == "Music Video" || song.album == "Video") {
                Box(modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isVideoThemeActive) themeColors[0].copy(alpha = 0.2f) else activeColor.copy(alpha = 0.2f))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text("MV", fontFamily = themeFont, fontSize = 8.sp, color = if (isVideoThemeActive) themeColors[0] else activeColor, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = song.artist,
                fontFamily = themeFont,
                fontSize = 12.sp,
                color = if (isActive) (if(isVideoThemeActive) themeColors[0].copy(alpha = 0.8f) else activeColor.copy(alpha=0.8f)) else defaultTextColor.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun EqVisualizer(accentColor: Color) {
    Row(
        modifier = Modifier.height(20.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "eq")
        repeat(3) { index ->
            androidx.compose.runtime.key(index) {
                val heightMultiplier by infiniteTransition.animateFloat(
                    initialValue = 0.2f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(
                            durationMillis = 400 + (index * 150),
                            easing = FastOutSlowInEasing
                        ),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "eq_bar_$index"
                )
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .graphicsLayer {
                            scaleY = heightMultiplier
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                        }
                        .clip(CircleShape)
                        .background(accentColor)
                )
            }
        }
    }
}

@Composable
fun PlaylistsTabContent(
    favoriteCount: Int,
    recentCount: Int,
    mostPlayedCount: Int,
    playlists: List<com.musicplayer.lytrix.db.PlaylistEntity>,
    accentColor: Color,
    likedVisible: Boolean = true,
    recentVisible: Boolean = true,
    mostPlayedVisible: Boolean = true,
    onLikedSongsClick: () -> Unit,
    onRecentSongsClick: () -> Unit,
    onMostPlayedClick: () -> Unit,
    onCustomPlaylistClick: (Long) -> Unit,
    onDeleteCustomPlaylist: (Long) -> Unit,
    onAddNewClick: () -> Unit
) {
    LaunchedEffect(Unit) { AnalyticsManager.logScreenView("Playlists_Tab") }
    var playlistToDelete by remember { mutableStateOf<Long?>(null) }

    if (playlistToDelete != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            title = { Text(getString("delete_playlist_title")) },
            text = { Text(getString("delete_playlist_text")) },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        onDeleteCustomPlaylist(playlistToDelete!!)
                        playlistToDelete = null
                    }
                ) {
                    Text(getString("delete"), color = Color.Red)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { playlistToDelete = null }) {
                    Text(getString("cancel"))
                }
            }
        )
    }

    val gridViewSize = com.musicplayer.lytrix.ui.theme.LocalGridViewSize.current

    if (gridViewSize <= 1) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Button(
                    onClick = onAddNewClick,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(getString("add_new"))
                }
            }

            if (likedVisible) {
                item {
                    PlaylistListCard(
                        title = getString("liked_songs"),
                        subtitle = "$favoriteCount ${getString("tracks")}",
                        gradient = Brush.linearGradient(listOf(Color(0xFFFF5252), Color(0xFFC51162))),
                        icon = Icons.Filled.Favorite,
                        onClick = onLikedSongsClick
                    )
                }
            }
            if (recentVisible) {
                item {
                    PlaylistListCard(
                        title = getString("recently_played"),
                        subtitle = "$recentCount ${getString("tracks")}",
                        gradient = Brush.linearGradient(listOf(Color(0xFF2196F3), Color(0xFF3F51B5))),
                        icon = Icons.Filled.History,
                        onClick = onRecentSongsClick
                    )
                }
            }
            if (mostPlayedVisible) {
                item {
                    PlaylistListCard(
                        title = getString("most_played"),
                        // 🔥 আবার আগের মতো tracks করে দেওয়া হলো
                        subtitle = "$mostPlayedCount ${getString("tracks")}",
                        gradient = Brush.linearGradient(listOf(Color(0xFF4CAF50), Color(0xFF009688))),
                        icon = Icons.Filled.Leaderboard,
                        onClick = onMostPlayedClick
                    )
                }
            }
            items(
                count = playlists.size,
                key = { index -> playlists[index].id }
            ) { index ->
                val pl = playlists[index]
                PlaylistListCard(
                    title = pl.name,
                    subtitle = "${pl.songCount} ${getString("tracks")}",
                    gradient = Brush.linearGradient(listOf(Color.DarkGray, Color.Black)),
                    icon = Icons.Filled.QueueMusic,
                    onClick = { onCustomPlaylistClick(pl.id) },
                    onLongClick = { playlistToDelete = pl.id }
                )
            }
        }
    } else {
        val cols = gridViewSize
        LazyVerticalGrid(
            columns = GridCells.Fixed(cols),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(cols) }) {
                Button(
                    onClick = onAddNewClick,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(getString("add_new"))
                }
            }

            if (likedVisible) {
                item {
                    PlaylistCard(
                        title = getString("liked_songs"),
                        subtitle = "$favoriteCount ${getString("tracks")}",
                        gradient = Brush.linearGradient(listOf(Color(0xFFFF5252), Color(0xFFC51162))),
                        icon = Icons.Filled.Favorite,
                        onClick = onLikedSongsClick
                    )
                }
            }
            if (recentVisible) {
                item {
                    PlaylistCard(
                        title = getString("recently_played"),
                        subtitle = "$recentCount ${getString("tracks")}",
                        gradient = Brush.linearGradient(listOf(Color(0xFF2196F3), Color(0xFF3F51B5))),
                        icon = Icons.Filled.History,
                        onClick = onRecentSongsClick
                    )
                }
            }
            if (mostPlayedVisible) {
                item {
                    PlaylistCard(
                        title = getString("most_played"),
                        // 🔥 আবার আগের মতো tracks করে দেওয়া হলো
                        subtitle = "$mostPlayedCount ${getString("tracks")}",
                        gradient = Brush.linearGradient(listOf(Color(0xFF4CAF50), Color(0xFF009688))),
                        icon = Icons.Filled.Leaderboard,
                        onClick = onMostPlayedClick
                    )
                }
            }
            items(
                count = playlists.size,
                key = { index -> playlists[index].id }
            ) { index ->
                val pl = playlists[index]
                PlaylistCard(
                    title = pl.name,
                    subtitle = "${pl.songCount} ${getString("tracks")}",
                    gradient = Brush.linearGradient(listOf(Color.DarkGray, Color.Black)),
                    icon = Icons.Filled.QueueMusic,
                    onClick = { onCustomPlaylistClick(pl.id) },
                    onLongClick = { playlistToDelete = pl.id }
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PlaylistListCard(
    title: String,
    subtitle: String,
    gradient: Brush,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(gradient)
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
            .bouncePress()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    subtitle,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PlaylistCard(
    title: String,
    subtitle: String,
    gradient: Brush,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(24.dp))
            .background(gradient)
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
            .bouncePress()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Bottom
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .size(32.dp)
                    .padding(bottom = 8.dp)
            )
            Text(
                title,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Text(
                subtitle,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun AlbumsTabContent(
    albums: List<Pair<String, List<com.musicplayer.lytrix.model.Song>>>,
    onAlbumClick: (String) -> Unit = {},
    currentTheme: String = "Solid Default",
    themeFont: androidx.compose.ui.text.font.FontFamily = androidx.compose.ui.text.font.FontFamily.Default,
    onRefresh: ((onComplete: () -> Unit) -> Unit) = { it() }
) {
    LaunchedEffect(Unit) { AnalyticsManager.logScreenView("Albums_Tab") }
    val context = LocalContext.current
    val isAdFreeLive by AdManager.isAdFreeFlow.collectAsState()
    val showAd = !isAdFreeLive && !AdManager.isAdFree(context)
    val adPosition = 3

    // 🔥 লিস্ট স্টেট অ্যাড করা হলো
    val listState = rememberLazyListState()
    var isRefreshing by remember { mutableStateOf(false) }

    if (albums.isEmpty()) {
        EmptyStateView()
    } else {
        val gridViewSize = com.musicplayer.lytrix.ui.theme.LocalGridViewSize.current
        if (gridViewSize <= 1) {

            // 🔥 PremiumFastScroller অ্যাড করা হলো
            PremiumFastScroller(
                listState = listState,
                accentColor = com.musicplayer.lytrix.ui.theme.LocalAccentColor.current,
                currentTheme = currentTheme
            ) {
                LazyColumn(
                    state = listState, // 🔥 স্টেট পাস করা হলো
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 120.dp, top = 8.dp)
                ) {
                    itemsIndexed(items = albums, key = { _, it -> it.first }) { index, (albumName, songs) ->
                        GroupCardItem(title = albumName, songs = songs, countText = "${songs.size} tracks", onClick = { onAlbumClick(albumName) }, currentTheme = currentTheme, themeFont = themeFont)
                        if (index == adPosition - 1 && showAd) {
                            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) { BannerAdView() }
                        }
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(gridViewSize), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()
            ) {
                val beforeAdCount = minOf(adPosition, albums.size)
                items(count = beforeAdCount, key = { index -> albums[index].first }) { index ->
                    val (albumName, songs) = albums[index]
                    GroupGridCardItem(title = albumName, songs = songs, countText = "${songs.size} tracks", onClick = { onAlbumClick(albumName) })
                }
                if (showAd && albums.size >= adPosition) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) { BannerAdView() }
                    }
                }
                val afterAdCount = maxOf(0, albums.size - adPosition)
                if (afterAdCount > 0) {
                    items(count = afterAdCount, key = { index -> albums[index + adPosition].first }) { index ->
                        val (albumName, songs) = albums[index + adPosition]
                        GroupGridCardItem(title = albumName, songs = songs, countText = "${songs.size} tracks", onClick = { onAlbumClick(albumName) })
                    }
                }
            }
        }
    }
}

@Composable
fun ArtistsTabContent(
    artists: List<Pair<String, List<com.musicplayer.lytrix.model.Song>>>,
    onArtistClick: (String) -> Unit = {},
    currentTheme: String = "Solid Default",
    themeFont: androidx.compose.ui.text.font.FontFamily = androidx.compose.ui.text.font.FontFamily.Default,
    onRefresh: ((onComplete: () -> Unit) -> Unit) = { it() }
) {
    LaunchedEffect(Unit) { AnalyticsManager.logScreenView("Artists_Tab") }
    val context = LocalContext.current
    val isAdFreeLive by AdManager.isAdFreeFlow.collectAsState()
    val showAd = !isAdFreeLive && !AdManager.isAdFree(context)
    val adPosition = 3

    // 🔥 লিস্ট স্টেট অ্যাড করা হলো
    val listState = rememberLazyListState()
    var isRefreshing by remember { mutableStateOf(false) }

    if (artists.isEmpty()) {
        EmptyStateView()
    } else {
        val gridViewSize = com.musicplayer.lytrix.ui.theme.LocalGridViewSize.current
        if (gridViewSize <= 1) {

            // 🔥 PremiumFastScroller অ্যাড করা হলো
            PremiumFastScroller(
                listState = listState,
                accentColor = com.musicplayer.lytrix.ui.theme.LocalAccentColor.current,
                currentTheme = currentTheme
            ) {
                LazyColumn(
                    state = listState, // 🔥 স্টেট পাস করা হলো
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 120.dp, top = 8.dp)
                ) {
                    itemsIndexed(items = artists, key = { _, it -> it.first }) { index, (artistName, songs) ->
                        GroupCardItem(title = artistName, songs = songs, countText = "${songs.size} tracks", onClick = { onArtistClick(artistName) }, currentTheme = currentTheme, themeFont = themeFont)
                        if (index == adPosition - 1 && showAd) {
                            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) { BannerAdView() }
                        }
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(gridViewSize), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()
            ) {
                val beforeAdCount = minOf(adPosition, artists.size)
                items(count = beforeAdCount, key = { index -> artists[index].first }) { index ->
                    val (artistName, songs) = artists[index]
                    GroupGridCardItem(title = artistName, songs = songs, countText = "${songs.size} tracks", onClick = { onArtistClick(artistName) })
                }
                if (showAd && artists.size >= adPosition) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) { BannerAdView() }
                    }
                }
                val afterAdCount = maxOf(0, artists.size - adPosition)
                if (afterAdCount > 0) {
                    items(count = afterAdCount, key = { index -> artists[index + adPosition].first }) { index ->
                        val (artistName, songs) = artists[index + adPosition]
                        GroupGridCardItem(title = artistName, songs = songs, countText = "${songs.size} tracks", onClick = { onArtistClick(artistName) })
                    }
                }
            }
        }
    }
}

@Composable
fun GroupCardItem(
    title: String,
    songs: List<Song>,
    countText: String,
    onClick: () -> Unit = {},
    currentTheme: String = "Solid Default",
    themeFont: androidx.compose.ui.text.font.FontFamily = androidx.compose.ui.text.font.FontFamily.Default,
    onRefresh: ((onComplete: () -> Unit) -> Unit) = { it() }
) {
    val isVideoThemeActive = currentTheme != "Solid Default"
    val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()

    val defaultTextColor = if (isVideoThemeActive) Color.White else MaterialTheme.colorScheme.onSurface
    val panelBg = if (isVideoThemeActive) Color.Black.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceContainer

    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(panelBg)
            .then(if (!isVideoThemeActive) Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f), RoundedCornerShape(20.dp)) else Modifier)
            .bouncePress()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            val firstSong = remember(songs) { songs.firstOrNull() }
            if (firstSong != null) {
                val artData = rememberLibraryCoverArt(context, firstSong)
                val imageRequest = remember(artData, firstSong.uriString) {
                    val dataToLoad = artData ?: firstSong.uriString
                    val reqBuilder = ImageRequest.Builder(context)
                        .data(dataToLoad)
                        .crossfade(true)
                        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                        .diskCachePolicy(coil.request.CachePolicy.ENABLED)

                    if ((artData == null || dataToLoad is String) && firstSong.isVideo) {
                        reqBuilder.videoFrameMillis(1000)
                    }
                    reqBuilder.build()
                }
                AsyncImage(
                    model = imageRequest,
                    contentDescription = "Cover",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(64.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = themeFont,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = defaultTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))

            val previewSongs = remember(songs) { songs.take(2) }
            for (song in previewSongs) {
                key(song.id) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.MusicNote,
                            contentDescription = null,
                            tint = defaultTextColor.copy(alpha = 0.5f),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = song.title,
                            fontFamily = themeFont,
                            fontSize = 12.sp,
                            color = defaultTextColor.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = countText,
            fontFamily = themeFont,
            color = defaultTextColor.copy(alpha = 0.4f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun GroupGridCardItem(
    title: String,
    songs: List<Song>,
    countText: String,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.9f)
            .bouncePress()
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Box(
                    modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    val firstSong = remember(songs) { songs.firstOrNull() }
                    if (firstSong != null) {
                        val context = LocalContext.current
                        val artData = rememberLibraryCoverArt(context, firstSong)
                        val imageRequest = remember(artData, firstSong.uriString) {
                            val dataToLoad = artData ?: firstSong.uriString
                            val reqBuilder = ImageRequest.Builder(context)
                                .data(dataToLoad)
                                .crossfade(true)
                                .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                                .diskCachePolicy(coil.request.CachePolicy.ENABLED)

                            if ((artData == null || dataToLoad is String) && firstSong.isVideo) {
                                reqBuilder.videoFrameMillis(1000)
                            }
                            reqBuilder.build()
                        }
                        AsyncImage(
                            model = imageRequest,
                            contentDescription = "Cover",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(Icons.Outlined.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(countText, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(10.dp))
                Spacer(modifier = Modifier.width(4.dp))
                val firstPreview = remember(songs) { songs.firstOrNull()?.title ?: "Unknown" }
                Text(firstPreview, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}
private val noEmbeddedArtLibraryCache = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()

@androidx.compose.runtime.Composable
private fun rememberLibraryCoverArt(context: android.content.Context, song: com.musicplayer.lytrix.model.Song?): Any? {
    if (song == null) return null
    val customArt = song.albumArtUriString?.takeIf { it.isNotBlank() && it != "null" }
    // গ্যালারির কাস্টম কভার বা সরাসরি অনলাইন ইউআরএল হলে আগে রিটার্ন
    if (customArt != null && (customArt.startsWith("file://") || customArt.startsWith("http://") || customArt.startsWith("https://"))) {
        return customArt
    }
    if (song.uriString.startsWith("http")) {
        return customArt ?: song.uriString
    }

    var artData by androidx.compose.runtime.remember(song.id, song.uriString) {
        val safeName = song.uriString.hashCode().toString()
        val cacheFile = java.io.File(context.cacheDir, "lytrix_lib_$safeName.jpg")
        val initial = if (cacheFile.exists() && cacheFile.length() > 0) cacheFile.absolutePath else (customArt ?: song.uriString)
        androidx.compose.runtime.mutableStateOf<Any?>(initial)
    }

    if (noEmbeddedArtLibraryCache.contains(song.id)) return artData

    androidx.compose.runtime.LaunchedEffect(song.id, song.uriString) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            var retriever: android.media.MediaMetadataRetriever? = null
            try {
                val safeName = song.uriString.hashCode().toString()
                val cacheFile = java.io.File(context.cacheDir, "lytrix_lib_$safeName.jpg")

                if (cacheFile.exists() && cacheFile.length() > 0) {
                    artData = cacheFile.absolutePath
                    return@withContext
                }

                if (noEmbeddedArtLibraryCache.contains(song.id)) return@withContext

                if (!song.uriString.startsWith("http")) {
                    retriever = android.media.MediaMetadataRetriever()
                    retriever.setDataSource(context, android.net.Uri.parse(song.uriString))

                    // 🔥 শুধু রেডিমেড ছবি খুঁজবে (VidMate/SnapTube কভার), ভারী ফ্রেম কাটবে না।
                    val pic = retriever.embeddedPicture

                    if (pic != null && pic.isNotEmpty()) {
                        java.io.FileOutputStream(cacheFile).use { it.write(pic) }
                        artData = cacheFile.absolutePath
                    } else {
                        noEmbeddedArtLibraryCache.add(song.id)
                    }
                }
            } catch (e: Exception) {
                noEmbeddedArtLibraryCache.add(song.id)
            } finally {
                try { retriever?.release() } catch (e: Exception) {}
            }
        }
    }
    return artData
}