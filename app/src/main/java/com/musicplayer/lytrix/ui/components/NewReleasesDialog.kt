package com.musicplayer.lytrix.ui.components

import android.widget.Toast
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.musicplayer.lytrix.sync.DiscoveredReleaseItem
import com.musicplayer.lytrix.sync.NewReleaseChecker
import com.musicplayer.lytrix.ui.PlayerViewModel
import compose.icons.TablerIcons
import compose.icons.tablericons.Bell
import compose.icons.tablericons.Refresh
import compose.icons.tablericons.X
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * In-app viewer for SimpMusic-style subscribed artist new releases & upload feed.
 */
@Composable
fun NewReleasesDialog(
    accentColor: Color,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf(NewReleaseChecker.loadFeed(context)) }
    var lastCheckMs by remember { mutableLongStateOf(NewReleaseChecker.getLastCheckMs(context)) }
    var isChecking by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF1A1514)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(TablerIcons.Bell, contentDescription = null, tint = accentColor)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Artist New Releases",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        val subtitle = if (lastCheckMs > 0L) {
                            "Checked ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(lastCheckMs))}"
                        } else {
                            "Tracks & albums from subscribed artists"
                        }
                        Text(subtitle, fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                    }
                    IconButton(
                        enabled = !isChecking,
                        onClick = {
                            isChecking = true
                            scope.launch {
                                val res = NewReleaseChecker.checkForNewReleases(context, pushNotification = false)
                                items = res.items
                                lastCheckMs = NewReleaseChecker.getLastCheckMs(context)
                                isChecking = false
                                val msg = when {
                                    res.checkedArtistsCount == 0 -> "Subscribe to artists in Online Music first"
                                    res.newlyFoundCount > 0 -> "Found ${res.newlyFoundCount} new releases!"
                                    else -> "Checked ${res.checkedArtistsCount} artists — up to date"
                                }
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        if (isChecking) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = accentColor, strokeWidth = 2.dp)
                        } else {
                            Icon(TablerIcons.Refresh, contentDescription = "Refresh", tint = accentColor)
                        }
                    }
                    IconButton(onClick = {
                        NewReleaseChecker.markAllSeenInUi(context)
                        onDismiss()
                    }) {
                        Icon(TablerIcons.X, contentDescription = "Close", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (items.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 26.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No artist releases synced yet",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Subscribe to artists in Online Music, then tap Check Now.",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            enabled = !isChecking,
                            onClick = {
                                isChecking = true
                                scope.launch {
                                    val res = NewReleaseChecker.checkForNewReleases(context, pushNotification = false)
                                    items = res.items
                                    lastCheckMs = NewReleaseChecker.getLastCheckMs(context)
                                    isChecking = false
                                    if (res.checkedArtistsCount == 0) {
                                        Toast.makeText(context, "Subscribe to an artist first!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                        ) {
                            Text("Check Now")
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(items, key = { "${it.artistId}_${it.id}" }) { rel ->
                            ReleaseItemRow(
                                item = rel,
                                accentColor = accentColor,
                                onClick = {
                                    playDiscoveredRelease(context, rel)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReleaseItemRow(
    item: DiscoveredReleaseItem,
    accentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        color = Color.White.copy(alpha = 0.06f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = item.thumbnailUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${item.artistName} • ${item.releaseType}${item.year?.let { " • $it" } ?: ""}",
                    fontSize = 11.5.sp,
                    color = Color.White.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (item.isNew) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = accentColor
                ) {
                    Text(
                        text = "NEW",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

private fun playDiscoveredRelease(context: android.content.Context, item: DiscoveredReleaseItem) {
    val vm = PlayerViewModel.activeInstance
    if (vm != null && (item.releaseType == "SONG" || item.releaseType == "VIDEO")) {
        val track = PlayerViewModel.YtMusicTrack(
            id = item.id,
            title = item.title,
            artist = item.artistName,
            thumbnailUrl = item.thumbnailUrl
        )
        vm.playLytrixPlaylist(listOf(track), 0, context)
        Toast.makeText(context, "Playing ${item.title}", Toast.LENGTH_SHORT).show()
    } else {
        val url = if (item.releaseType == "ALBUM" || item.releaseType == "SINGLE" || item.releaseType == "EP") {
            "https://music.youtube.com/browse/${item.browseId}"
        } else {
            "https://music.youtube.com/watch?v=${item.id}"
        }
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }
}
