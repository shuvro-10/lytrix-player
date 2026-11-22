package com.musicplayer.lytrix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.musicplayer.lytrix.utils.TrashManager
import com.musicplayer.lytrix.utils.TrashedItem
import coil.request.videoFrameMillis
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class TrashedItemUiState(
    val item: TrashedItem,
    val nameToDisplay: String,
    val subtitle: String,
    val daysText: String
)

@Composable
fun TrashedThumbnail(item: TrashedItem) {
    val context = LocalContext.current
    var artwork by remember(item.originalPath, item.trashedPath, item.isVideo) {
        mutableStateOf<Any?>(null)
    }

    LaunchedEffect(item.originalPath, item.trashedPath, item.isVideo) {
        if (item.isVideo) {
            artwork = android.net.Uri.parse(item.originalPath)
        } else {
            withContext(Dispatchers.IO) {
                try {
                    val mmr = android.media.MediaMetadataRetriever()
                    if (item.originalPath.startsWith("content://")) {
                        mmr.setDataSource(context, android.net.Uri.parse(item.originalPath))
                    } else {
                        mmr.setDataSource(item.trashedPath)
                    }
                    val art = mmr.embeddedPicture
                    mmr.release()
                    artwork = art
                } catch (e: Exception) {
                    artwork = null
                }
            }
        }
    }
    
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (artwork == null && !item.isVideo) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = Color.White.copy(alpha = 0.3f)
            )
        } else {
            coil.compose.AsyncImage(
                model = coil.request.ImageRequest.Builder(context)
                    .data(artwork)
                    .videoFrameMillis(1000)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    onBack: () -> Unit,
    accentColor: Color
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) }
    
    // 0 -> Audio, 1 -> Video
    var trashedItems by remember { mutableStateOf(listOf<TrashedItemUiState>()) }
    
    val coroutineScope = rememberCoroutineScope()
    fun reloadItems() {
        coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val items = TrashManager.getTrashedItems(context, selectedTab == 1).sortedByDescending { it.timestamp }
            val uiStateList = items.map { item ->
                val file = File(item.originalPath)
                val nameToDisplayStr = item.title ?: file.name
                val nameToDisplay = nameToDisplayStr.substringBeforeLast(".")
                val subtitle = if (item.isVideo) "" else (item.artist ?: "Unknown Artist")
                
                val expireTime = item.dateExpires ?: (item.timestamp + 30L * 24 * 60 * 60 * 1000L)
                val remainingMillis = expireTime - System.currentTimeMillis()
                val remainingDays = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(remainingMillis)
                val daysText = if (remainingDays <= 0) "Today" else if (remainingDays == 1L) "1 day" else "$remainingDays days"
                
                TrashedItemUiState(item, nameToDisplay, subtitle, daysText)
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                trashedItems = uiStateList
            }
        }
    }
    
    LaunchedEffect(selectedTab) {
        reloadItems()
    }
    
    var selectedItemForAction by remember { mutableStateOf<TrashedItem?>(null) }
    
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            reloadItems()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recently Deleted") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = selectedTab, contentColor = accentColor) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                    Text("Audio", modifier = Modifier.padding(16.dp))
                }
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                    Text("Video", modifier = Modifier.padding(16.dp))
                }
            }
            
            if (trashedItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No media deleted within 30 days", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(trashedItems, key = { it.item.originalPath }) { uiState ->
                        val item = uiState.item
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedItemForAction = item }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Thumbnail
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x1AFFFFFF))
                            ) {
                                TrashedThumbnail(item)
                                
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.BottomCenter)
                                        .background(Color(0x99000000))
                                        .padding(vertical = 2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = uiState.daysText,
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.width(12.dp))
                            
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = uiState.nameToDisplay,
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = uiState.subtitle,
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    
    if (selectedItemForAction != null) {
        com.musicplayer.lytrix.ui.component.LytrixFluidBottomSheet(
            onDismissRequest = { selectedItemForAction = null },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                Text("Item Options", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
                ListItem(
                    headlineContent = { Text("Restore") },
                    modifier = Modifier.clickable {
                        val item = selectedItemForAction!!
                        selectedItemForAction = null
                        coroutineScope.launch(Dispatchers.IO) {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                                try {
                                    val uri = android.net.Uri.parse(item.originalPath)
                                    val pi = android.provider.MediaStore.createTrashRequest(context.contentResolver, listOf(uri), false)
                                    withContext(Dispatchers.Main) {
                                        launcher.launch(androidx.activity.result.IntentSenderRequest.Builder(pi.intentSender).build())
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    TrashManager.restoreItem(context, item)
                                    withContext(Dispatchers.Main) { reloadItems() }
                                }
                            } else {
                                TrashManager.restoreItem(context, item)
                                withContext(Dispatchers.Main) { reloadItems() }
                            }
                        }
                    }
                )
                ListItem(
                    headlineContent = { Text("Delete permanently", color = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickable {
                        val item = selectedItemForAction!!
                        selectedItemForAction = null
                        coroutineScope.launch(Dispatchers.IO) {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                                try {
                                    val uri = android.net.Uri.parse(item.originalPath)
                                    val pi = android.provider.MediaStore.createDeleteRequest(context.contentResolver, listOf(uri))
                                    withContext(Dispatchers.Main) {
                                        launcher.launch(androidx.activity.result.IntentSenderRequest.Builder(pi.intentSender).build())
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    TrashManager.deletePermanently(context, item)
                                    withContext(Dispatchers.Main) { reloadItems() }
                                }
                            } else {
                                TrashManager.deletePermanently(context, item)
                                withContext(Dispatchers.Main) { reloadItems() }
                            }
                        }
                    }
                )
            }
        }
    }
}
