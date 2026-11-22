package com.musicplayer.lytrix.ui

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.musicplayer.lytrix.model.JioSaavnSong
import java.io.File
import java.net.URLEncoder
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedSongBottomSheet(
    context: Context,
    song: JioSaavnSong,
    viewModel: PlayerViewModel,
    onDismiss: () -> Unit,
    onNavigateToArtist: (String) -> Unit
) {
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val isDownloadedLocally = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "${song.name.replace(Regex("[^a-zA-Z0-9 ]"), "_").take(50).trim()}.mp3").exists()
    
    val isAudiobookItem = song.albumName == "Audiobook" || viewModel.currentMediaCategory.value == "AUDIOBOOK" || song.id.startsWith("ab:")
    val isPodcastItem = song.albumName == "Podcast" || (!isAudiobookItem && viewModel.currentMediaCategory.value == "PODCAST") || song.id.startsWith("pod:")
    val isShowItem = isAudiobookItem || isPodcastItem

    val likedPrefKey = if (isAudiobookItem) "online_liked_audiobooks" else if (isPodcastItem) "online_liked_podcasts" else "online_liked_songs"
    val onlineLikedPrefs = context.getSharedPreferences(likedPrefKey, Context.MODE_PRIVATE)
    var isOnlineLiked by remember(song, likedPrefKey) {
        mutableStateOf(onlineLikedPrefs.getStringSet("liked_ids", emptySet())?.contains(song.id) == true)
    }

    val laterPrefix = if (isAudiobookItem) "online_ab_" else "online_podcast_"
    val laterName = if (isAudiobookItem) "Books for later" else "Episodes for later"
    val laterPrefs = remember(song.id, isAudiobookItem) { context.getSharedPreferences("$laterPrefix$laterName", Context.MODE_PRIVATE) }
    var isSavedForLater by remember(song.id, laterPrefs) {
        mutableStateOf(laterPrefs.contains(song.id))
    }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistNameInput by remember { mutableStateOf("") }
    var showDetailsDialog by remember { mutableStateOf(false) }

    val isMetroGuest = viewModel.metroManager.roomRole.collectAsState().value == com.musicplayer.lytrix.listentogether.metro.MetroRoomRole.GUEST
    val myFbStatus = viewModel.myRoomStatus.collectAsState().value
    val isRoomGuest = isMetroGuest || (myFbStatus == "approved" || myFbStatus == "co_host")

    if (showAddToPlaylistDialog) {
        val metaPref = if (isShowItem) "online_shows_playlists_meta" else "online_playlists_meta"
        val plPrefix = if (isShowItem) "online_show_pl_" else "online_pl_"
        val plPrefs = context.getSharedPreferences(metaPref, Context.MODE_PRIVATE)
        val playlists = plPrefs.getStringSet("names", emptySet())?.toList() ?: emptyList()
        AlertDialog(
            onDismissRequest = { showAddToPlaylistDialog = false },
            title = { Text(if (isShowItem) "Add to Shows Playlist" else "Add to Playlist", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    if (playlists.isNotEmpty()) {
                        Text("Existing Playlists:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                        LazyColumn(modifier = Modifier.heightIn(max = 160.dp).padding(vertical = 8.dp)) {
                            items(playlists) { plName ->
                                Text(
                                    text = plName,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        val targetPrefs = context.getSharedPreferences("$plPrefix$plName", Context.MODE_PRIVATE)
                                        val obj = JSONObject().apply {
                                            put("id", song.id)
                                            put("name", song.name)
                                            put("title", song.name)
                                            put("artist", song.artist)
                                            put("imageUrl", song.imageUrl)
                                            put("downloadUrl", song.downloadUrl ?: "")
                                            put("albumName", if (isShowItem) (if (isAudiobookItem) "Audiobook" else "Podcast") else plName)
                                            put("timestamp", System.currentTimeMillis())
                                        }
                                        targetPrefs.edit().putString(song.id, obj.toString()).apply()
                                        Toast.makeText(context, "Added to $plName", Toast.LENGTH_SHORT).show()
                                        showAddToPlaylistDialog = false
                                        onDismiss()
                                    }.padding(vertical = 10.dp, horizontal = 4.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newPlaylistNameInput,
                        onValueChange = { newPlaylistNameInput = it },
                        placeholder = { Text("Or create new playlist...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newPlaylistNameInput.isNotBlank()) {
                        val existing = plPrefs.getStringSet("names", emptySet())?.toMutableSet() ?: mutableSetOf()
                        existing.add(newPlaylistNameInput)
                        plPrefs.edit().putStringSet("names", existing).apply()
                        val targetPrefs = context.getSharedPreferences("$plPrefix$newPlaylistNameInput", Context.MODE_PRIVATE)
                        val obj = JSONObject().apply {
                            put("id", song.id)
                            put("name", song.name)
                            put("title", song.name)
                            put("artist", song.artist)
                            put("imageUrl", song.imageUrl)
                            put("downloadUrl", song.downloadUrl ?: "")
                            put("albumName", if (isShowItem) (if (isAudiobookItem) "Audiobook" else "Podcast") else newPlaylistNameInput)
                            put("timestamp", System.currentTimeMillis())
                        }
                        targetPrefs.edit().putString(song.id, obj.toString()).apply()
                        Toast.makeText(context, "Created & Added", Toast.LENGTH_SHORT).show()
                        newPlaylistNameInput = ""
                        showAddToPlaylistDialog = false
                        onDismiss()
                    }
                }) { Text("Create & Add") }
            },
            dismissButton = { TextButton(onClick = { showAddToPlaylistDialog = false }) { Text("Cancel") } }
        )
    }

    if (showDetailsDialog) {
        AlertDialog(
            onDismissRequest = { showDetailsDialog = false },
            title = { Text(song.name, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, maxLines = 2) },
            text = {
                Column {
                    Text(text = "Author / Channel: ${cleanArtistDisplayName(song.artist)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = "Media Type: ${if (isAudiobookItem) "Audiobook" else if (isPodcastItem) "Podcast" else "Music"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = { showDetailsDialog = false }) { Text("Close") } }
        )
    }

    com.musicplayer.lytrix.ui.component.LytrixFluidBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(model = song.imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = song.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(text = cleanArtistDisplayName(song.artist), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, maxLines = 1)
                    }

                    IconButton(onClick = {
                        val currentLikedIds = onlineLikedPrefs.getStringSet("liked_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
                        val songIdStr = song.id

                        if (isOnlineLiked) {
                            currentLikedIds.remove(songIdStr)
                            onlineLikedPrefs.edit().remove("song_data_$songIdStr").putStringSet("liked_ids", currentLikedIds).apply()
                            isOnlineLiked = false
                            Toast.makeText(context, "Removed from Cloud Likes", Toast.LENGTH_SHORT).show()
                        } else {
                            currentLikedIds.add(songIdStr)
                            val jsonObj = JSONObject()
                            jsonObj.put("id", songIdStr)
                            jsonObj.put("originalId", songIdStr)
                            jsonObj.put("title", song.name)
                            jsonObj.put("name", song.name)
                            jsonObj.put("subtitle", song.artist)
                            jsonObj.put("artist", song.artist)
                            jsonObj.put("image", song.imageUrl)
                            jsonObj.put("imageUrl", song.imageUrl)
                            jsonObj.put("url", song.downloadUrl ?: "")
                            jsonObj.put("downloadUrl", song.downloadUrl ?: "")
                            onlineLikedPrefs.edit().putString("song_data_$songIdStr", jsonObj.toString()).putStringSet("liked_ids", currentLikedIds).apply()
                            isOnlineLiked = true
                            Toast.makeText(context, "Saved to Cloud Likes", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(if (isOnlineLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = "Like", tint = if (isOnlineLiked) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (isShowItem) {
                        SharedPremiumMenuSquareButton(
                            icon = if (isSavedForLater) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            text = if (isSavedForLater) "Saved" else "Save Later",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (isSavedForLater) {
                                    laterPrefs.edit().remove(song.id).apply()
                                    isSavedForLater = false
                                    Toast.makeText(context, "Removed from $laterName", Toast.LENGTH_SHORT).show()
                                } else {
                                    val obj = JSONObject().apply {
                                        put("id", song.id)
                                        put("name", song.name)
                                        put("title", song.name)
                                        put("artist", song.artist)
                                        put("imageUrl", song.imageUrl)
                                        put("downloadUrl", song.downloadUrl ?: "")
                                        put("albumName", if (isAudiobookItem) "Audiobook" else "Podcast")
                                        put("timestamp", System.currentTimeMillis())
                                    }
                                    laterPrefs.edit().putString(song.id, obj.toString()).apply()
                                    isSavedForLater = true
                                    Toast.makeText(context, "Saved to $laterName", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                    SharedPremiumMenuSquareButton(icon = Icons.Default.PlaylistAdd, text = "Playlist", modifier = Modifier.weight(1f), onClick = { showAddToPlaylistDialog = true })
                    SharedPremiumMenuSquareButton(icon = Icons.Default.Share, text = "Share", modifier = Modifier.weight(1f), onClick = {
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            val searchLink = if (isShowItem) "https://www.youtube.com/watch?v=${song.id}" else "https://music.youtube.com/search?q=${java.net.URLEncoder.encode("${song.name} ${song.artist}", "UTF-8")}"
                            putExtra(android.content.Intent.EXTRA_TEXT, searchLink)
                        }
                        context.startActivity(android.content.Intent.createChooser(intent, if (isShowItem) "Share Episode" else "Share Song"))
                        onDismiss()
                    })
                    SharedPremiumMenuSquareButton(icon = Icons.Default.Info, text = "Details", modifier = Modifier.weight(1f), onClick = { showDetailsDialog = true })
                }
            }

            if (isRoomGuest) {
                item {
                    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        Row(modifier = Modifier.fillMaxWidth().clickable {
                            val isSaavn = song.downloadUrl.isNotBlank() || song.imageUrl.contains("saavn", ignoreCase = true)
                            val trackId = if (isSaavn) "saavn:${song.id}" else song.id
                            val trackAlbum = if (isSaavn && song.downloadUrl.isNotBlank()) "saavn_url:${song.downloadUrl}" else "JioSaavn"
                            viewModel.suggestTrackToActiveRoom(
                                id = trackId,
                                title = song.name,
                                artist = song.artist,
                                album = trackAlbum,
                                thumbnail = song.imageUrl,
                                streamUrl = song.downloadUrl ?: "",
                                context = context
                            )
                            onDismiss()
                        }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("Suggest to host", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            item {
                Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Row(modifier = Modifier.fillMaxWidth().clickable {
                        viewModel.playNextOnline(context, song)
                        onDismiss()
                    }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PlaylistPlay, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Play next", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
                    }
                    
                    val dlTitle = if (isAudiobookItem) "Download Audiobook" else if (isPodcastItem) "Download Episode" else (if (isDownloadedLocally) "Remove from device" else "Download")
                    Row(modifier = Modifier.fillMaxWidth().clickable {
                        if (isDownloadedLocally && !isShowItem) {
                            val file = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "${song.name.replace(Regex("[^a-zA-Z0-9 ]"), "_").take(50).trim()}.mp3")
                            if (file.exists()) file.delete()
                            Toast.makeText(context, "Removed from device", Toast.LENGTH_SHORT).show()
                        } else {
                            requestDownloadWithAd(context, song.name, isPlaylist = false) {
                                val dummyOnlineSong = com.musicplayer.lytrix.model.Song(
                                    id = song.id.hashCode().toLong(),
                                    title = song.name,
                                    artist = song.artist,
                                    album = if (isAudiobookItem) "Audiobook" else if (isPodcastItem) "Podcast" else "Unknown",
                                    duration = 0L,
                                    uriString = song.downloadUrl ?: "",
                                    filePath = if (isAudiobookItem) "ONLINE_AUDIOBOOK" else if (isPodcastItem) "ONLINE_PODCAST" else "ONLINE",
                                    albumArtUriString = song.imageUrl,
                                    isVideo = false,
                                    hasLyrics = false,
                                    originalId = song.id,
                                    mediaType = if (isAudiobookItem) "AUDIOBOOK" else if (isPodcastItem) "PODCAST" else "SONG"
                                )
                                downloadOnlineSongFromPlayer(context, dummyOnlineSong)
                                Toast.makeText(context, "Check notification panel", Toast.LENGTH_SHORT).show()
                            }
                        }
                        onDismiss()
                    }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (isDownloadedLocally && !isShowItem) Icons.Default.Delete else Icons.Default.Download, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(dlTitle, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
                    }
                }
            }

            if (isShowItem) {
                item {
                    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        Row(modifier = Modifier.fillMaxWidth().clickable {
                            val target = cleanArtistDisplayName(song.artist)
                            if (target.isNotBlank()) {
                                onNavigateToArtist(target)
                            }
                            onDismiss()
                        }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(if (isAudiobookItem) "Go to Author" else "Go to Channel / Show", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
                        }
                    }
                }
            } else {
                item {
                    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        Row(modifier = Modifier.fillMaxWidth().clickable {
                            onDismiss()
                        }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Radio, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("Start radio", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
                        }
                        
                        Row(modifier = Modifier.fillMaxWidth().clickable {
                            val parsedArtists = com.musicplayer.lytrix.utils.ArtistSeparatorManager.splitArtists(context, song.artist)
                            if (parsedArtists.isNotEmpty()) {
                                onNavigateToArtist(parsedArtists.first())
                            }
                            onDismiss()
                        }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("Go to Artist", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
                        }

                        val isJioSaavnItem = song.downloadUrl.isNotBlank() || song.imageUrl.contains("saavn", ignoreCase = true)
                        if (!isJioSaavnItem) {
                            Row(modifier = Modifier.fillMaxWidth().clickable {
                                onDismiss()
                                viewModel.openAlbumForOnlineSong(context, song)
                            }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(com.musicplayer.lytrix.ui.icons.LytrixIcons.Outlined.Album, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(16.dp))
                                Text("View album", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
                            }
                        }
                    }
                }
            }

            item {
                Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Row(modifier = Modifier.fillMaxWidth().clickable {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            val searchLink = "https://music.youtube.com/search?q=${URLEncoder.encode("${song.name} ${song.artist}", "UTF-8")}"
                            putExtra(Intent.EXTRA_TEXT, searchLink)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share Song"))
                        onDismiss()
                    }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Share", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedYtPlaylistBottomSheet(
    context: Context,
    playlistId: String,
    playlistTitle: String,
    playlistArtist: String,
    playlistThumbnail: String,
    playlistType: String,
    viewModel: PlayerViewModel,
    onDismiss: () -> Unit
) {
    val ytPlaylistBottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val dbName = if (playlistType == "Album") "online_albums_meta" else if (playlistType == "Podcast") "online_podcasts_meta" else "online_playlists_meta"
    val prefix = if (playlistType == "Album") "online_album_" else if (playlistType == "Podcast") "online_podcast_" else "online_pl_"
    val plPrefs = context.getSharedPreferences(dbName, Context.MODE_PRIVATE)
    var isSaved by remember(playlistTitle) { mutableStateOf(plPrefs.getStringSet("names", emptySet())?.contains(playlistTitle) == true) }
    
    com.musicplayer.lytrix.ui.component.LytrixFluidBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = playlistThumbnail,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(60.dp).clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = playlistTitle, color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(text = playlistArtist, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, maxLines = 1)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item {
                // Top 3 action row
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable { 
                        viewModel.loadYtPlaylistDetails(playlistId, playlistType, playlistTitle)
                        onDismiss()
                    }.padding(horizontal = 20.dp, vertical = 12.dp)) {
                        Icon(Icons.Default.PlaylistPlay, contentDescription = "Play next", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Play next", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
                    }
                    
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable {
                        val targetPrefs = context.getSharedPreferences(dbName, Context.MODE_PRIVATE)
                        val coverPrefs = context.getSharedPreferences("playlist_covers", Context.MODE_PRIVATE)
                        val timePrefs = context.getSharedPreferences("playlist_time", Context.MODE_PRIVATE)
                        val idPrefs = context.getSharedPreferences("playlist_ids", Context.MODE_PRIVATE)
                        val set = targetPrefs.getStringSet("names", emptySet())?.toMutableSet() ?: mutableSetOf()

                        if (isSaved) {
                            set.remove(playlistTitle)
                            targetPrefs.edit().putStringSet("names", set).apply()
                            context.getSharedPreferences("$prefix$playlistTitle", Context.MODE_PRIVATE).edit().clear().apply()
                            coverPrefs.edit().remove(playlistTitle).apply()
                            timePrefs.edit().remove(playlistTitle).apply()
                            idPrefs.edit().remove(playlistTitle).apply()
                            isSaved = false
                        } else {
                            isSaved = true
                            Toast.makeText(context, "Saving $playlistType in background...", Toast.LENGTH_SHORT).show()
                            viewModel.saveYtPlaylistSilently(
                                id = playlistId,
                                type = playlistType,
                                title = playlistTitle,
                                thumbnailUrl = playlistThumbnail,
                                context = context
                            )
                        }
                    }.padding(horizontal = 20.dp, vertical = 12.dp)) {
                        Icon(if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, contentDescription = "Save", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(if (isSaved) "Saved" else "Save", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
                    }
                    
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            val searchLink = "https://music.youtube.com/search?q=${URLEncoder.encode(playlistTitle, "UTF-8")}"
                            putExtra(Intent.EXTRA_TEXT, searchLink)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share"))
                        onDismiss()
                    }.padding(horizontal = 20.dp, vertical = 12.dp)) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Share", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
                    }
                }
            }

            item {
                Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Row(modifier = Modifier.fillMaxWidth().clickable { 
                        viewModel.loadYtPlaylistDetails(playlistId, playlistType, playlistTitle)
                        onDismiss() 
                    }.padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Shuffle, contentDescription = "Shuffle", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Shuffle play", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
                    }

                    Row(modifier = Modifier.fillMaxWidth().clickable {
                        onDismiss()
                        requestDownloadWithAd(context, playlistTitle, isPlaylist = true) {
                            viewModel.downloadYtPlaylistSilently(
                                id = playlistId,
                                type = playlistType,
                                title = playlistTitle,
                                context = context
                            )
                        }
                    }.padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Download, contentDescription = "Download", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Download", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun SharedPremiumMenuSquareButton(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .aspectRatio(1.2f)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(28.dp))
        Spacer(modifier = Modifier.height(8.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
