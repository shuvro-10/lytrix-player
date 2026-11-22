package com.musicplayer.lytrix.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.activity.compose.BackHandler
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.musicplayer.lytrix.model.Song

@Composable
fun ArrangeSongRow(
    song: Song,
    isSelected: Boolean,
    isDragged: Boolean,
    onToggleSelection: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (change: androidx.compose.ui.input.pointer.PointerInputChange, dragAmount: Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val elevation by animateDpAsState(if (isDragged) 8.dp else 0.dp)
    val cardBgColor = if (isDragged) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent

    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentOnDragCancel by rememberUpdatedState(onDragCancel)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(if (isDragged) 1f else 0f)
            .shadow(elevation)
            .background(cardBgColor)
            .clickable { onToggleSelection() }
            .padding(start = 10.dp, top = 4.dp, end = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
                .border(1.dp, if (isSelected) Color.Transparent else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(13.dp))
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Box(modifier = Modifier.weight(1f)) {
            SongCardItem(
                song = song,
                isActive = false,
                isPlaying = false,
                activeColor = MaterialTheme.colorScheme.primary,
                playCountText = null,
                showDragHandle = false,
                onClick = { onToggleSelection() }
            )
        }
        Box(
            modifier = Modifier
                .padding(8.dp)
                .pointerInput(song.id) {
                    detectVerticalDragGestures(
                        onDragStart = { currentOnDragStart() },
                        onVerticalDrag = { change, dragAmount -> currentOnDrag(change, dragAmount) },
                        onDragEnd = { currentOnDragEnd() },
                        onDragCancel = { currentOnDragCancel() }
                    )
                }
        ) {
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = "Drag to reorder",
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ManualArrangeScreen(
    type: String,
    idString: String,
    viewModel: PlayerViewModel,
    initialSongs: List<Song>,
    onNavigateBack: () -> Unit
) {
    BackHandler { onNavigateBack() }
    val currentSong by viewModel.currentSong.collectAsState()
    var songs by remember { mutableStateOf(initialSongs.toList()) }
    var selectedIndices by remember { mutableStateOf(setOf<Int>()) }
    val playlistId = idString.toLongOrNull() ?: 0L

    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    val listState = rememberLazyListState()

    fun moveSelectedToTop() {
        if (selectedIndices.isEmpty()) return
        val selectedSongs = selectedIndices.sorted().map { songs[it] }
        val remainingSongs = songs.filterIndexed { index, _ -> !selectedIndices.contains(index) }
        songs = selectedSongs + remainingSongs
        selectedIndices = emptySet()
    }

    fun moveSelectedToBottom() {
        if (selectedIndices.isEmpty()) return
        val selectedSongs = selectedIndices.sorted().map { songs[it] }
        val remainingSongs = songs.filterIndexed { index, _ -> !selectedIndices.contains(index) }
        songs = remainingSongs + selectedSongs
        selectedIndices = emptySet()
    }

    fun moveItem(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        val newSongs = songs.toMutableList()
        val item = newSongs.removeAt(fromIndex)
        newSongs.add(toIndex, item)
        songs = newSongs
        
        val newSelected = mutableSetOf<Int>()
        for (i in selectedIndices) {
            when {
                i == fromIndex -> newSelected.add(toIndex)
                i in (fromIndex + 1)..toIndex -> newSelected.add(i - 1)
                i in toIndex until fromIndex -> newSelected.add(i + 1)
                else -> newSelected.add(i)
            }
        }
        selectedIndices = newSelected
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(getString("manual_arrange")) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (type == "liked") {
                            viewModel.reorderFavorites(songs.map { it.id })
                        } else if (type == "custom") {
                            viewModel.reorderPlaylist(playlistId, songs.map { it.id })
                        }
                        onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            if (selectedIndices.isNotEmpty()) {
                BottomAppBar(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(bottom = if (currentSong != null) 74.dp else 0.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        TextButton(onClick = { moveSelectedToTop() }) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = "Move to top")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(getString("move_to_top"))
                        }
                        TextButton(onClick = { moveSelectedToBottom() }) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = "Move to bottom")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(getString("move_to_bottom"))
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = if (currentSong != null) 140.dp else 70.dp)
        ) {
            itemsIndexed(items = songs, key = { _, item -> item.id }) { index, song ->
                var totalDragOffset by remember { mutableStateOf(0f) }
                
                val isDragged = draggedIndex == index
                val isSelected = selectedIndices.contains(index)
                
                ArrangeSongRow(
                    song = song,
                    isSelected = isSelected,
                    isDragged = isDragged,
                    onToggleSelection = {
                        selectedIndices = if (isSelected) {
                            selectedIndices - index
                        } else {
                            selectedIndices + index
                        }
                    },
                    modifier = Modifier.animateItem(),
                    onDragStart = { 
                        draggedIndex = index 
                        totalDragOffset = 0f
                    },
                    onDrag = { change, dragAmount -> 
                        change.consume()
                        totalDragOffset += dragAmount
                        val layoutInfo = listState.layoutInfo
                        val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == draggedIndex }
                        itemInfo?.let {
                            val y = it.offset + (it.size / 2) + totalDragOffset
                            val targetItem = layoutInfo.visibleItemsInfo.firstOrNull { info ->
                                y >= info.offset && y <= info.offset + info.size
                            }
                            if (targetItem != null && targetItem.index != draggedIndex) {
                                val fromIndex = draggedIndex!!
                                val toIndex = targetItem.index
                                val offsetShift = targetItem.offset - it.offset
                                totalDragOffset -= offsetShift
                                moveItem(fromIndex, toIndex)
                                draggedIndex = toIndex
                            }
                        }
                    },
                    onDragEnd = { 
                        draggedIndex = null
                        totalDragOffset = 0f
                    },
                    onDragCancel = { 
                        draggedIndex = null
                        totalDragOffset = 0f
                    }
                )
            }
        }
    }
}
