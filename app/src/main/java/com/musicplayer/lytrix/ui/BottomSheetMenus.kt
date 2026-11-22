package com.musicplayer.lytrix.ui

import android.content.Context
import android.media.AudioManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.musicplayer.lytrix.db.PlaylistEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.ServiceList
import androidx.lifecycle.viewmodel.compose.viewModel
// 🔥 Tabler Icons সঠিক ইম্পোর্ট
import compose.icons.TablerIcons
import compose.icons.tablericons.*
import androidx.compose.material.icons.filled.PushPin
import kotlinx.coroutines.delay
import androidx.compose.animation.core.RepeatMode
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder

enum class BottomSheetType {
    MAIN, PLAYLIST, SLEEP_TIMER, SPEED, AB_SELECT, VIDEO_QUALITY, DETAILS, COMMENTS, LISTEN_TOGETHER
}
val MeldBgColor = Color(0xFF1A181C)
val MeldCardColor = Color(0xFF2D2A2E)
val MeldTextColor = Color.White

@JvmOverloads
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerBottomSheetController(
    viewModel: PlayerViewModel,
    sheetType: BottomSheetType,
    onDismiss: () -> Unit,
    onNavigate: (BottomSheetType) -> Unit,
    playlists: List<PlaylistEntity>,
    onAddToPlaylist: (Long) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onSetSleepTimer: (Int, Boolean) -> Unit,
    onSetSpeed: (Float) -> Unit,
    onDeleteSong: () -> Unit = {},
    accentColor: Color,
    currentSpeed: Float = 1.0f,
    currentSleepTimerMinutes: Int = 0,
    playLastSongToEnd: Boolean = false,
    onHideSong: () -> Unit,
    onPowerSavingMode: () -> Unit = {},
    onShareSong: () -> Unit = {},
    onSetPointA: () -> Unit = {},
    onSetPointB: () -> Unit = {},
    onClearAB: () -> Unit = {},
    isPointASet: Boolean = false,
    isPointBSet: Boolean = false,
    song: com.musicplayer.lytrix.model.Song? = null,
    currentVideoQuality: String = "Auto",
    onSetVideoQuality: (String) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {},
    onViewAlbum: (() -> Unit)? = null,
    onPlayOnline: () -> Unit = {},
    onNavigateToListenTogether: () -> Unit = {}
) {
    val context = LocalContext.current
    val pinnedIds by viewModel.pinnedIds.collectAsState()
    val currentPitch by viewModel.currentPlaybackPitch.collectAsState()
    val sleepTimerState by viewModel.sleepTimerState.collectAsState()
    val resolvedSongAlbum by viewModel.currentSongAlbumInfo.collectAsState()
    val pointAMs by viewModel.pointA.collectAsState()
    val pointBMs by viewModel.pointB.collectAsState()
    var showCustomCoverDialog by remember { mutableStateOf(false) }
    var showYouTubeMatchDialog by remember { mutableStateOf(false) }

    val currentSongKey = song?.originalId?.takeIf { it.isNotBlank() } ?: song?.id?.toString()
    val resolvedAlbumName = resolvedSongAlbum
        ?.takeIf { it.songKey == currentSongKey }
        ?.albumName
    val sleepTimerSubtitle = if (sleepTimerState.isActive) {
        if (sleepTimerState.stopAfterCurrentSong) {
            "End of song (${com.musicplayer.lytrix.playback.formatSleepTimerRemaining(sleepTimerState.remainingMs)})"
        } else {
            com.musicplayer.lytrix.playback.formatSleepTimerRemaining(sleepTimerState.remainingMs)
        }
    } else null

    // Lytrix Sleep Timer Dialog (rendered directly as a dialog, not inside a bottom sheet)
    if (sheetType == BottomSheetType.SLEEP_TIMER) {
        com.musicplayer.lytrix.ui.component.LytrixSleepTimerDialog(
            state = sleepTimerState,
            onDismiss = onDismiss,
            onStart = { durationMs, fadeOut, finishCurrentSong ->
                viewModel.startSleepTimer(durationMs, fadeOut, finishCurrentSong)
            },
            onStopAfterCurrentSong = { fadeOut ->
                viewModel.stopSleepTimerAfterCurrentSong(fadeOut)
            },
            onCancelTimer = {
                viewModel.cancelSleepTimer()
            },
            allowEndOfSong = song?.filePath != "ONLINE_RADIO",
        )
        return
    }

    val isTwoStage = sheetType == BottomSheetType.MAIN ||
        sheetType == BottomSheetType.DETAILS ||
        sheetType == BottomSheetType.COMMENTS

    com.musicplayer.lytrix.ui.component.LytrixFluidBottomSheet(
        onDismissRequest = onDismiss,
        sheetKey = sheetType,
        enableTwoStage = isTwoStage,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        when (sheetType) {
            BottomSheetType.MAIN -> MainMenuSheet(
                context = context, song = song, onNavigate = onNavigate, onDismiss = onDismiss,
                onHideSong = onHideSong, onDeleteSong = onDeleteSong, onPowerSavingMode = onPowerSavingMode,
                onShareSong = onShareSong, onNavigateToArtist = onNavigateToArtist, onPlayOnline = onPlayOnline,
                onNavigateToListenTogether = onNavigateToListenTogether,
                isPinnedToSpeedDial = pinnedIds.contains(song?.originalId ?: ""),
                resolvedAlbumName = resolvedAlbumName,
                sleepTimerSubtitle = sleepTimerSubtitle,
                onPinToSpeedDial = {
                    song?.let {
                        val isPinned = pinnedIds.contains(it.originalId ?: "")
                        viewModel.toggleSpeedDial(com.musicplayer.lytrix.ui.PlayerViewModel.YtMusicTrack(
                            id = it.originalId ?: return@let,
                            title = it.title,
                            artist = it.artist,
                            thumbnailUrl = it.albumArtUriString ?: ""
                        ))
                        val msg = if (isPinned) "Unpinned from Speed Dial" else "Pinned to Speed Dial"
                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                onNotInterested = {
                    val s = song
                    onDismiss()
                    s?.let {
                        val id = it.originalId ?: it.id.toString()
                        viewModel.markSongNotInterested(id, it.title, it.artist, it.albumArtUriString ?: "")
                        val isOnline = it.filePath in listOf("ONLINE", "ONLINE_LYTRIX", "ONLINE_PODCAST", "OFFLINE")
                        if (isOnline) {
                            viewModel.skipNext()
                        }
                        viewModel.triggerUndoSnackbar(id)
                    }
                },
                onOpenComments = { onNavigate(BottomSheetType.COMMENTS) },
                onChangeCoverArt = { showCustomCoverDialog = true },
                onChangeYouTubeMatch = {
                    if (viewModel.isInListenTogetherRoom()) {
                        android.widget.Toast.makeText(context, "Changing YouTube match is disabled in Listen Together", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        showYouTubeMatchDialog = true
                    }
                },
                onViewAlbum = {
                    if (onViewAlbum != null) {
                        onViewAlbum()
                    } else if (song != null) {
                        viewModel.openAlbumForSong(context, song)
                    }
                }
            )
            BottomSheetType.PLAYLIST -> PlaylistSelectionSheet(playlists = playlists, onAddToPlaylist = { onAddToPlaylist(it); onDismiss() }, onCreatePlaylist = onCreatePlaylist)
            BottomSheetType.SLEEP_TIMER -> Unit
            BottomSheetType.SPEED -> com.musicplayer.lytrix.ui.component.LytrixTempoPitchControls(
                speed = currentSpeed,
                pitch = currentPitch,
                onSpeedChange = { viewModel.setPlaybackSpeed(it, immediate = false) },
                onPitchChange = { viewModel.setPlaybackPitch(it, immediate = false) },
                onCommit = { viewModel.commitPlaybackParametersNow() },
                onDone = onDismiss,
            )
            BottomSheetType.AB_SELECT -> ABSelectSheet(
                onSetPointA = { onSetPointA(); onDismiss() },
                onSetPointB = { onSetPointB(); onDismiss() },
                onClearAB = { onClearAB(); onDismiss() },
                isPointASet = isPointASet,
                isPointBSet = isPointBSet,
                pointAMs = pointAMs,
                pointBMs = pointBMs,
                accentColor = accentColor,
            )
            BottomSheetType.VIDEO_QUALITY -> VideoQualitySheet(currentQuality = currentVideoQuality, onSetQuality = { onSetVideoQuality(it); onDismiss() }, accentColor = accentColor)
            BottomSheetType.DETAILS -> {
                if (song != null) {
                    MeldDetailsScreen(
                        song = song,
                        accentColor = accentColor,
                        onOpenComments = { onNavigate(BottomSheetType.COMMENTS) },
                        onDismiss = { onNavigate(BottomSheetType.MAIN) }
                    )
                }
            }
            BottomSheetType.COMMENTS -> {
                if (song != null) {
                    val targetVid = viewModel.getEffectiveYouTubeVideoId(context, song)
                        ?: song.originalId
                        ?: song.id.toString()
                    com.musicplayer.lytrix.ui.component.YouTubeCommentsSheet(
                        videoId = targetVid,
                        songTitle = song.title,
                        accentColor = accentColor,
                        onDismiss = { onNavigate(BottomSheetType.MAIN) }
                    )
                }
            }
            BottomSheetType.LISTEN_TOGETHER -> {
                // ফাঁকা থাকবে, কারণ এটা এখন ফুল-স্ক্রিন স্ক্রিনে চলে গেছে
            }
        }
    }

    if (showCustomCoverDialog && song != null) {
        com.musicplayer.lytrix.ui.component.CustomCoverEditDialog(
            song = song,
            hasCustomCover = viewModel.hasCustomAlbumArt(context, song),
            accentColor = accentColor,
            onPickImageUri = { uri -> viewModel.saveCustomAlbumArt(context, song, uri) },
            onApplyImageUrl = { url -> viewModel.saveCustomAlbumArtUrl(context, song, url) },
            onRemoveCustomCover = { viewModel.removeCustomAlbumArt(context, song) },
            onDismiss = { showCustomCoverDialog = false }
        )
    }

    if (showYouTubeMatchDialog && song != null) {
        val activeYtId = viewModel.getEffectiveYouTubeVideoId(context, song)
        com.musicplayer.lytrix.ui.component.YouTubeMatchOverrideDialog(
            song = song,
            currentYouTubeId = activeYtId,
            hasOverride = viewModel.hasYouTubeMatchOverride(context, song),
            accentColor = accentColor,
            onConfirm = { selection -> viewModel.overrideYouTubeMatch(context, song, selection) },
            onResetOverride = { viewModel.resetYouTubeMatchOverride(context, song) },
            onDismiss = { showYouTubeMatchDialog = false }
        )
    }
}

@Composable
fun MeldVolumeSlider(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var volume by remember { mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }
    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
    val activeColor = MaterialTheme.colorScheme.primary

    Box(modifier = modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(volume.coerceAtLeast(0.1f))
                    .fillMaxHeight()
                    .background(activeColor)
                    .padding(start = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Icon(
                    imageVector = TablerIcons.Volume,
                    contentDescription = "Volume",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Box(
                modifier = Modifier
                    .weight((maxVolume - volume).coerceAtLeast(0.01f))
                    .fillMaxHeight()
                    .background(Color.Transparent),
                contentAlignment = Alignment.CenterEnd
            ) {
                Box(
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                )
            }
        }
        Slider(
            value = volume,
            onValueChange = {
                volume = it
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, it.toInt(), 0)
            },
            valueRange = 0f..maxVolume,
            colors = SliderDefaults.colors(
                thumbColor = Color.Transparent,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent
            ),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        )
    }
}

@Composable
fun MeldTopActionChip(modifier: Modifier = Modifier, icon: ImageVector, text: String, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .aspectRatio(1.1f)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = text, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun MeldMenuGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        content()
    }
}

@Composable
fun MeldMenuItem(icon: ImageVector, title: String, subtitle: String? = null, onClick: () -> Unit = {}) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun MainMenuSheet(
    context: android.content.Context, song: com.musicplayer.lytrix.model.Song? = null, onNavigate: (BottomSheetType) -> Unit,
    onDismiss: () -> Unit, onHideSong: () -> Unit, onDeleteSong: () -> Unit, onPowerSavingMode: () -> Unit = {}, onShareSong: () -> Unit = {}, onNavigateToArtist: (String) -> Unit = {}, onPlayOnline: () -> Unit = {}, onNavigateToListenTogether: () -> Unit = {},
    isPinnedToSpeedDial: Boolean = false,
    onPinToSpeedDial: () -> Unit = {},
    onNotInterested: () -> Unit = {},
    onOpenComments: () -> Unit = {},
    onChangeCoverArt: () -> Unit = {},
    onChangeYouTubeMatch: () -> Unit = {},
    onViewAlbum: () -> Unit = {},
    resolvedAlbumName: String? = null,
    sleepTimerSubtitle: String? = null,
) {
    val isCloudSong = song?.filePath in listOf("ONLINE", "ONLINE_LYTRIX", "ONLINE_PODCAST", "ONLINE_RADIO", "OFFLINE", "OFFLINE_PODCAST", "ONLINE_AUDIOBOOK", "OFFLINE_AUDIOBOOK")

    if (isCloudSong) {
        OnlineSongMenu(
            context, song, onNavigate, onDismiss, onPowerSavingMode, onNavigateToArtist, onPlayOnline, onNavigateToListenTogether,
            isPinnedToSpeedDial, onPinToSpeedDial, onNotInterested, onOpenComments, onChangeCoverArt, onChangeYouTubeMatch,
            onViewAlbum, resolvedAlbumName, sleepTimerSubtitle
        )
    } else {
        LocalSongMenu(context, song, onNavigate, onDismiss, onHideSong, onDeleteSong, onPowerSavingMode, onShareSong, onChangeCoverArt, sleepTimerSubtitle)
    }
}

@Composable
fun OnlineSongMenu(
    context: android.content.Context, 
    song: com.musicplayer.lytrix.model.Song?, 
    onNavigate: (BottomSheetType) -> Unit, 
    onDismiss: () -> Unit, 
    onPowerSavingMode: () -> Unit, 
    onNavigateToArtist: (String) -> Unit, 
    onPlayOnline: () -> Unit, 
    onNavigateToListenTogether: () -> Unit,
    isPinnedToSpeedDial: Boolean = false,
    onPinToSpeedDial: () -> Unit = {},
    onNotInterested: () -> Unit = {},
    onOpenComments: () -> Unit = {},
    onChangeCoverArt: () -> Unit = {},
    onChangeYouTubeMatch: () -> Unit = {},
    onViewAlbum: () -> Unit = {},
    resolvedAlbumName: String? = null,
    sleepTimerSubtitle: String? = null,
) {
    val isRadioStream = song?.filePath == "ONLINE_RADIO"
    val isPodcast = song?.filePath in listOf("ONLINE_PODCAST", "OFFLINE_PODCAST")
    val isAudiobook = song?.mediaType == "AUDIOBOOK" || song?.filePath == "ONLINE_AUDIOBOOK"
    val isPodcastOrAudiobook = isPodcast || isAudiobook || song?.mediaType in listOf("PODCAST", "AUDIOBOOK")
    val isJioSaavn = song?.albumArtUriString?.contains("saavn", ignoreCase = true) == true ||
        song?.uriString?.contains("saavn", ignoreCase = true) == true ||
        song?.album?.startsWith("saavn", ignoreCase = true) == true ||
        song?.originalId?.startsWith("saavn:", ignoreCase = true) == true
    val coroutineScope = rememberCoroutineScope()
    var isDownloadedLocally by remember(song) { mutableStateOf(false) }
    val safeName = song?.title?.replace(Regex("[^a-zA-Z0-9 ]"), "_")?.take(50)?.trim()
    val file = java.io.File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC), "$safeName.mp3")

    val isAb = isAudiobook || song?.mediaType == "AUDIOBOOK"
    val laterPrefix = if (isAb) "online_ab_" else "online_podcast_"
    val laterName = if (isAb) "Books for later" else "Episodes for later"
    val laterPrefs = remember(song?.id, isAb) { context.getSharedPreferences("$laterPrefix$laterName", Context.MODE_PRIVATE) }
    val sId = song?.originalId ?: song?.id?.toString() ?: ""
    var isSavedForLater by remember(sId, laterPrefs) {
        mutableStateOf(sId.isNotBlank() && laterPrefs.contains(sId))
    }

    LaunchedEffect(song) { isDownloadedLocally = file.exists() }
    var showArtistSelection by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
            MeldVolumeSlider()
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MeldTopActionChip(
                    modifier = Modifier.weight(1f),
                    icon = if (isPodcastOrAudiobook) (if (isSavedForLater) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder) else com.musicplayer.lytrix.ui.icons.LytrixIcons.AutoMirrored.Filled.PlaylistAdd,
                    text = if (isPodcastOrAudiobook) (if (isSavedForLater) "Saved" else "Save Later") else "Add to playlist"
                ) {
                    if (isPodcastOrAudiobook) {
                        if (sId.isNotBlank()) {
                            if (isSavedForLater) {
                                laterPrefs.edit().remove(sId).commit()
                                isSavedForLater = false
                                android.widget.Toast.makeText(context, "Removed from $laterName", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                val json = org.json.JSONObject().apply {
                                    put("id", sId)
                                    put("name", song?.title ?: "Episode")
                                    put("title", song?.title ?: "Episode")
                                    put("artist", song?.artist ?: "")
                                    put("publisher", song?.artist ?: "")
                                    put("imageUrl", song?.albumArtUriString ?: "")
                                    put("downloadUrl", song?.uriString ?: "")
                                    put("albumName", song?.album ?: laterName)
                                    song?.channelId?.let { put("channelId", it) }
                                    put("timestamp", System.currentTimeMillis())
                                }
                                laterPrefs.edit().putString(sId, json.toString()).commit()
                                isSavedForLater = true
                                android.widget.Toast.makeText(context, "Saved to $laterName", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        onNavigate(BottomSheetType.PLAYLIST)
                    }
                }
                MeldTopActionChip(modifier = Modifier.weight(1f), icon = com.musicplayer.lytrix.ui.icons.LytrixIcons.Outlined.Share, text = "Share") {
                    onDismiss()
                    shareOnlineSong(context, song, isPodcast, isJioSaavn)
                }
                MeldTopActionChip(modifier = Modifier.weight(1f), icon = com.musicplayer.lytrix.ui.icons.LytrixIcons.Outlined.Info, text = "Details") { onNavigate(BottomSheetType.DETAILS) }
            }
        }

        item {
            MeldMenuGroup {
                val channelOrAuthorTitle = when {
                    isAudiobook -> "View author"
                    isPodcast -> "View channel"
                    isPodcastOrAudiobook -> "View channel"
                    else -> "View artist"
                }
                val channelOrAuthorSubtitle = song?.artist ?: when {
                    isAudiobook -> "Author"
                    isPodcast || isPodcastOrAudiobook -> "Channel"
                    else -> "Unknown Artist"
                }
                MeldMenuItem(
                    icon = com.musicplayer.lytrix.ui.icons.LytrixIcons.Outlined.Person,
                    title = channelOrAuthorTitle,
                    subtitle = channelOrAuthorSubtitle
                ) {
                    onDismiss()
                    if (isAudiobook) {
                        onNavigateToArtist("AUDIOBOOK_${song?.artist ?: ""}")
                    } else if (isPodcast || isPodcastOrAudiobook) {
                        if (!song?.channelId.isNullOrBlank() && song!!.channelId!!.startsWith("UC")) {
                            onNavigateToArtist(song!!.channelId!!)
                        } else {
                            val targetShow = song?.album?.takeIf { it.isNotBlank() && it != "Unknown Album" } ?: song?.artist ?: ""
                            onNavigateToArtist("PODCAST_$targetShow")
                        }
                    } else {
                        val artists = song?.artist?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
                        if (artists.size > 1) {
                            showArtistSelection = true
                        } else if (artists.isNotEmpty()) {
                            onNavigateToArtist(artists.first())
                        }
                    }
                }

                if (!isRadioStream && !isPodcastOrAudiobook && !isJioSaavn) {
                    val genericAlbums = setOf(
                        "Online Stream",
                        "YouTube Music",
                        "YouTube Playlist",
                        "Lytrix Original",
                        "Offline Downloaded",
                        "Unknown Album",
                    )
                    val albumSubtitle = resolvedAlbumName?.takeIf { it.isNotBlank() }
                        ?: song?.album?.takeIf {
                            it.isNotBlank() &&
                                it !in genericAlbums &&
                                !it.startsWith("MPREb_") &&
                                !it.startsWith("FEmusic_library_album_")
                        }
                        ?: "Album details"
                    MeldMenuItem(
                        icon = com.musicplayer.lytrix.ui.icons.LytrixIcons.Outlined.Album,
                        title = "View album",
                        subtitle = albumSubtitle
                    ) {
                        onDismiss()
                        onViewAlbum()
                    }
                }

                if (!isRadioStream) {
                    if (isDownloadedLocally) {
                        MeldMenuItem(icon = com.musicplayer.lytrix.ui.icons.LytrixIcons.Outlined.Delete, title = "Remove from device", subtitle = "Delete downloaded file") {
                            onDismiss()
                            if (file.exists()) file.delete()
                            com.musicplayer.lytrix.playback.OkHttpDownloader.deleteSongFiles(context, song?.title ?: "")
                            context.getSharedPreferences("downloaded_meta", Context.MODE_PRIVATE).edit().remove("song_${song?.originalId ?: song?.id}").apply()
                            isDownloadedLocally = false
                            android.widget.Toast.makeText(context, "Removed from device", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        MeldMenuItem(icon = com.musicplayer.lytrix.ui.icons.LytrixIcons.Outlined.CloudUpload, title = "Play Online", subtitle = "Stream with lyrics & video") {
                            onPlayOnline()
                        }
                        MeldMenuItem(icon = com.musicplayer.lytrix.ui.icons.LytrixIcons.Outlined.Download, title = "Save to Storage 👑", subtitle = "Export to public Music folder") {
                            onDismiss()
                            if (!isUserPremium(context)) {
                                android.widget.Toast.makeText(context, "👑 Save to Storage is exclusive to Lytrix Premium users!", android.widget.Toast.LENGTH_LONG).show()
                                return@MeldMenuItem
                            }
                            android.widget.Toast.makeText(context, "Saving...", android.widget.Toast.LENGTH_SHORT).show()
                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                val sId = song?.originalId ?: song?.id?.toString() ?: ""
                                val sTitle = song?.title ?: ""
                                val sArtist = song?.artist ?: ""
                                val sAlbum = song?.album ?: ""
                                val success = com.musicplayer.lytrix.playback.OkHttpDownloader.exportSongToStorage(
                                    context = context,
                                    songId = sId,
                                    title = sTitle,
                                    artist = sArtist,
                                    album = sAlbum
                                )
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    android.widget.Toast.makeText(
                                        context,
                                        if (success) "Saved to Music/Lytrix 🎵" else "Export failed",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    } else {
                        val downloadSubtitle = when {
                            isAudiobook -> "Download Audiobook"
                            isPodcast && (song?.uriString?.contains("itunes") == true || song?.uriString?.contains("apple") == true) -> "Download from Apple Podcasts"
                            isPodcast && (song?.uriString?.contains("youtu") == true || song?.filePath == "ONLINE_LYTRIX" || song?.originalId?.startsWith("yt:") == true) -> "Download from YouTube"
                            isPodcast -> "Download Podcast Episode"
                            isJioSaavn -> "Download from JioSaavn"
                            else -> "Download from YouTube"
                        }
                        MeldMenuItem(icon = TablerIcons.Download, title = "Download", subtitle = downloadSubtitle) {
                            onDismiss()
                            song?.let { onlineSong ->
                                requestDownloadWithAd(context, onlineSong.title, isPlaylist = false) {
                                    downloadOnlineSongFromPlayer(context, onlineSong)
                                    android.widget.Toast.makeText(context, "Check notification panel", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
                if (!isJioSaavn && !isPodcast && !isRadioStream && (song?.filePath == "ONLINE_LYTRIX" || song?.filePath == "ONLINE")) {
                    if (song?.filePath == "ONLINE_LYTRIX") {
                        MeldMenuItem(
                            icon = if (isPinnedToSpeedDial) androidx.compose.material.icons.Icons.Default.Close else androidx.compose.material.icons.Icons.Default.PushPin, 
                            title = if (isPinnedToSpeedDial) "Unpin from Speed Dial" else "Pin to Speed Dial", 
                            subtitle = if (isPinnedToSpeedDial) "Remove from speed dial" else "Pin song to speed dial"
                        ) {
                            onPinToSpeedDial()
                            onDismiss()
                        }
                    }
                    MeldMenuItem(
                        icon = TablerIcons.EyeOff,
                        title = "Not interested",
                        subtitle = "Remove from recommendations"
                    ) {
                        onNotInterested()
                        onDismiss()
                    }
                }
            }
        }

        item {
            MeldMenuGroup { MeldMenuItem(icon = androidx.compose.material.icons.Icons.Rounded.PlayArrow, title = "Floating Player") { onDismiss(); startFloatingWidget(context) } }
        }

        if (!isRadioStream) {
            item {
                MeldMenuGroup {
                    MeldMenuItem(icon = TablerIcons.Users, title = "Listen Together") {
                        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                        if (currentUser != null) {
                            onDismiss()
                            onNavigateToListenTogether()
                        } else {
                            onDismiss()
                            android.widget.Toast.makeText(context, "You need to sign in first", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }

        if (!isDownloadedLocally && !isRadioStream && !isPodcast && !isJioSaavn) {
            item {
                MeldMenuGroup {
                    MeldMenuItem(icon = TablerIcons.Message, title = "Comments", subtitle = "Read YouTube comments & replies") { onOpenComments() }
                    MeldMenuItem(icon = TablerIcons.ArrowsRightLeft, title = "Change YouTube Match", subtitle = "Switch audio or video version") { onChangeYouTubeMatch() }
                    MeldMenuItem(icon = TablerIcons.Photo, title = "Custom Cover Art", subtitle = "Set cover from gallery or URL") { onChangeCoverArt() }
                    MeldMenuItem(icon = TablerIcons.Settings, title = "Video Quality") { onNavigate(BottomSheetType.VIDEO_QUALITY) }
                }
            }
        } else if (!isRadioStream) {
            item {
                MeldMenuGroup {
                    if (!isJioSaavn && !isPodcast) {
                        MeldMenuItem(icon = TablerIcons.Message, title = "Comments", subtitle = "Read YouTube comments & replies") { onOpenComments() }
                    }
                    MeldMenuItem(icon = TablerIcons.Photo, title = "Custom Cover Art", subtitle = "Set cover from gallery or URL") { onChangeCoverArt() }
                }
            }
        }

        item {
            MeldMenuGroup {
                MeldMenuItem(icon = TablerIcons.Dashboard, title = "Tempo & Pitch") { onNavigate(BottomSheetType.SPEED) }
                MeldMenuItem(icon = com.musicplayer.lytrix.ui.icons.LytrixIcons.Outlined.Bedtime, title = "Sleep timer", subtitle = sleepTimerSubtitle) { onNavigate(BottomSheetType.SLEEP_TIMER) }
                MeldMenuItem(icon = TablerIcons.Repeat, title = "A-B Repeat") { onNavigate(BottomSheetType.AB_SELECT) }
                MeldMenuItem(icon = TablerIcons.BatteryAutomotive, title = "Always-On Display", subtitle = "AMOLED clock & battery saver") { onPowerSavingMode(); onDismiss() }
                MeldMenuItem(icon = TablerIcons.Lock, title = "Pocket Touch Lock", subtitle = "Prevent accidental pocket touches") {
                    onDismiss()
                    com.musicplayer.lytrix.aod.AodLaunchTrigger.launch(initialLocked = true)
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showArtistSelection && song != null) {
        val artists = song.artist.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        Dialog(onDismissRequest = { showArtistSelection = false }) {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MeldCardColor)) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Select Artist", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MeldTextColor, modifier = Modifier.padding(bottom = 12.dp))
                    artists.forEach { artistName ->
                        Text(text = artistName, fontSize = 16.sp, color = MeldTextColor, modifier = Modifier.fillMaxWidth().clickable {
                            showArtistSelection = false
                            onNavigateToArtist(artistName)
                        }.padding(vertical = 12.dp))
                    }
                }
            }
        }
    }
}
}

@Composable
fun LocalSongMenu(
    context: android.content.Context,
    song: com.musicplayer.lytrix.model.Song?,
    onNavigate: (BottomSheetType) -> Unit,
    onDismiss: () -> Unit,
    onHideSong: () -> Unit,
    onDeleteSong: () -> Unit,
    onPowerSavingMode: () -> Unit,
    onShareSong: () -> Unit,
    onChangeCoverArt: () -> Unit = {},
    sleepTimerSubtitle: String? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
            MeldVolumeSlider()
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MeldTopActionChip(modifier = Modifier.weight(1f), icon = TablerIcons.Playlist, text = "Add to playlist") { onNavigate(BottomSheetType.PLAYLIST) }
                MeldTopActionChip(modifier = Modifier.weight(1f), icon = TablerIcons.Share, text = "Share") { onDismiss(); onShareSong() }
                MeldTopActionChip(modifier = Modifier.weight(1f), icon = TablerIcons.InfoCircle, text = "Details") { onNavigate(BottomSheetType.DETAILS) }
            }
        }

        item {
            MeldMenuGroup { MeldMenuItem(icon = androidx.compose.material.icons.Icons.Rounded.PlayArrow, title = "Floating Player") { onDismiss(); startFloatingWidget(context) } }
        }

        item {
            MeldMenuGroup {
                MeldMenuItem(icon = TablerIcons.Dashboard, title = "Tempo & Pitch") { onNavigate(BottomSheetType.SPEED) }
                MeldMenuItem(icon = com.musicplayer.lytrix.ui.icons.LytrixIcons.Outlined.Bedtime, title = "Sleep timer", subtitle = sleepTimerSubtitle) { onNavigate(BottomSheetType.SLEEP_TIMER) }
                MeldMenuItem(icon = TablerIcons.Repeat, title = "A-B Repeat") { onNavigate(BottomSheetType.AB_SELECT) }
                MeldMenuItem(icon = TablerIcons.Photo, title = "Custom Cover Art") { onChangeCoverArt() }
                MeldMenuItem(icon = TablerIcons.BatteryAutomotive, title = "Always-On Display", subtitle = "AMOLED clock & battery saver") { onPowerSavingMode(); onDismiss() }
                MeldMenuItem(icon = TablerIcons.Lock, title = "Pocket Touch Lock", subtitle = "Prevent accidental pocket touches") {
                    onDismiss()
                    com.musicplayer.lytrix.aod.AodLaunchTrigger.launch(initialLocked = true)
                }
            }
        }

        item {
            MeldMenuGroup {
                MeldMenuItem(icon = TablerIcons.EyeOff, title = "Hide song") { onHideSong(); onDismiss() }
                MeldMenuItem(icon = TablerIcons.Trash, title = "Delete song", subtitle = "Remove permanently") { onDeleteSong(); onDismiss() }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
    }
}

private fun formatCountShort(count: Long): String = when {
    count >= 1_000_000_000 -> String.format("%.1fB", count / 1_000_000_000.0)
    count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
    count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
    else -> count.toString()
}

@Composable
fun MeldDetailsScreen(
    song: com.musicplayer.lytrix.model.Song,
    accentColor: Color,
    onOpenComments: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val isOnline = song.filePath in listOf("ONLINE", "ONLINE_LYTRIX", "ONLINE_PODCAST", "OFFLINE_PODCAST", "ONLINE_AUDIOBOOK")
    val isRadioStream = song.filePath == "ONLINE_RADIO"
    val isPodcast = song.mediaType == "PODCAST" || song.filePath in listOf("ONLINE_PODCAST", "OFFLINE_PODCAST")
    val isAudiobook = song.mediaType == "AUDIOBOOK" || song.filePath == "ONLINE_AUDIOBOOK"
    val isPodOrAb = isPodcast || isAudiobook
    val isJioSaavn = song.albumArtUriString?.contains("saavn", ignoreCase = true) == true
    val isLibriVox = song.uriString?.contains("archive.org", ignoreCase = true) == true ||
                     song.uriString?.contains("librivox", ignoreCase = true) == true ||
                     song.album?.contains("librivox", ignoreCase = true) == true
    val isApple = song.albumArtUriString?.contains("mzstatic", ignoreCase = true) == true ||
                  song.album?.contains("apple", ignoreCase = true) == true
    val isYtSong = (isOnline && !isJioSaavn && !isLibriVox && !isApple) || (song.originalId?.length == 11)

    val sourcePlatformName = when {
        isRadioStream -> "Live Radio"
        isJioSaavn -> "JioSaavn"
        isLibriVox -> "LibriVox Classics"
        isApple -> "Apple Podcasts"
        isYtSong && isPodcast -> "YouTube Podcasts"
        isYtSong && isAudiobook -> "YouTube Audiobooks"
        isYtSong -> "YouTube Music"
        else -> if (isOnline) "Online Stream" else "Local Storage"
    }

    var viewsText by remember { mutableStateOf("Loading...") }
    var likesText by remember { mutableStateOf("Loading...") }
    var uploadDateText by remember { mutableStateOf("Loading...") }
    var descriptionText by remember { mutableStateOf("Loading description...") }
    var fileSizeText by remember { mutableStateOf("Unknown") }
    var durationText by remember { mutableStateOf("Loading...") }

    fun copyToClipboard(text: String?) {
        if (!text.isNullOrBlank() && text != "Loading..." && text != "N/A" && text != "Unavailable") {
            clipboardManager.setText(AnnotatedString(text))
            android.widget.Toast.makeText(context, "Copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(song) {
        val safeName = song.title.replace(Regex("[^a-zA-Z0-9 ]"), "_").take(50).trim()
        val file = java.io.File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC), "$safeName.mp3")

        if (file.exists()) {
            val sizeInMb = file.length().toDouble() / (1024 * 1024)
            fileSizeText = String.format("%.2f MB", sizeInMb)
        } else if (!isOnline) {
            fileSizeText = "File not found"
        }

        if (isRadioStream) {
            durationText = "Live Radio Stream"
        } else if (song.duration > 0) {
            durationText = String.format("%02d:%02d", (song.duration / 1000) / 60, (song.duration / 1000) % 60)
        } else {
            durationText = "Unknown"
        }

        if (isYtSong && !isRadioStream) {
            withContext(Dispatchers.IO) {
                try {
                    val id = song.originalId ?: song.id.toString()
                    val extractor = ServiceList.YouTube.getStreamExtractor("https://www.youtube.com/watch?v=$id")
                    extractor.fetchPage()

                    viewsText = try { extractor.viewCount.takeIf { it > 0 }?.let { formatCountShort(it) } ?: "N/A" } catch (e: Exception) { "N/A" }
                    likesText = try { extractor.likeCount.takeIf { it > 0 }?.let { formatCountShort(it) } ?: "N/A" } catch (e: Exception) { "N/A" }

                    val rawDate = try { extractor.uploadDate?.toString() ?: "" } catch(e:Exception) { "" }
                    uploadDateText = if (rawDate.contains("instant=")) {
                        rawDate.substringAfter("instant=").substringBefore("T")
                    } else {
                        val dateMatch = Regex("\\d{4}-\\d{2}-\\d{2}").find(rawDate)
                        dateMatch?.value ?: "Unknown"
                    }

                    val rawDesc = try { extractor.description?.content ?: "" } catch(e:Exception){""}
                    descriptionText = if (rawDesc.isNotBlank()) {
                        @Suppress("DEPRECATION")
                        android.text.Html.fromHtml(rawDesc).toString().trim()
                    } else {
                        "No description available."
                    }

                    if (song.duration <= 0L) {
                        val len = extractor.length
                        if (len > 0) durationText = String.format("%02d:%02d", len / 60, len % 60)
                    }

                } catch (e: Exception) {
                    viewsText = "Unavailable"; likesText = "Unavailable"; uploadDateText = "Unavailable"; descriptionText = "Error loading description."
                }
            }
        } else if (isJioSaavn) {
            withContext(Dispatchers.IO) {
                try {
                    val id = song.originalId ?: song.id.toString()

                    val client = okhttp3.OkHttpClient.Builder()
                        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                        .build()

                    val request = okhttp3.Request.Builder()
                        .url("https://jiosaavn-api.shuvrodaspranto20170014.workers.dev/api/songs?ids=$id")
                        .header(
                            "User-Agent",
                            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
                        )
                        .header(
                            com.musicplayer.lytrix.constants.LytrixSecurityConstants.SAAVN_SECRET_HEADER,
                            com.musicplayer.lytrix.constants.LytrixSecurityConstants.SAAVN_SECRET_TOKEN
                        )
                        .header("Accept", "application/json")
                        .build()

                    client.newCall(request).execute().use { response ->
                        val bodyStr = response.body?.string()
                        android.util.Log.d("LytrixDetails", "code=${response.code} body=${bodyStr?.take(300)}")

                        if (response.isSuccessful && !bodyStr.isNullOrBlank()) {
                            val root = org.json.JSONObject(bodyStr)
                            val jsonArray = root.optJSONArray("data")

                            if (jsonArray != null && jsonArray.length() > 0) {
                                val songData = jsonArray.getJSONObject(0)

                                val playCount = songData.optLong("playCount", 0L)
                                viewsText = if (playCount > 0) formatCountShort(playCount) else "N/A"
                                likesText = "N/A"

                                uploadDateText = songData.optString("year", "Unknown").ifBlank { "Unknown" }

                                val label = songData.optString("label", "")
                                val copyright = songData.optString("copyright", "")
                                descriptionText = if (copyright.isNotEmpty()) "$label\n$copyright".trim()
                                else label.ifEmpty { "No description available." }

                                if (song.duration <= 0L) {
                                    val durationSec = songData.optInt("duration", 0)
                                    if (durationSec > 0) {
                                        durationText = String.format("%02d:%02d", durationSec / 60, durationSec % 60)
                                    }
                                }
                            } else {
                                viewsText = "N/A"; uploadDateText = "Unknown"; descriptionText = "No description available."
                            }
                        } else {
                            android.util.Log.e("LytrixDetails", "HTTP ${response.code} failed for id=$id")
                            viewsText = "Unavailable"; uploadDateText = "Unavailable"; descriptionText = "Error loading description."
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("LytrixDetails", "Exception", e)
                    viewsText = "Unavailable"; uploadDateText = "Unavailable"; descriptionText = "Error loading description."
                }
            }
        }
    }

    val parsedCredits = remember(descriptionText) {
        com.musicplayer.lytrix.ui.component.SongCreditsParser.parseFromDescription(descriptionText)
    }

    Column(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).clickable { onDismiss() }) {
            Icon(TablerIcons.ArrowLeft, contentDescription = "Back", tint = MeldTextColor)
            Spacer(modifier = Modifier.width(16.dp))
            Text("Details", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MeldTextColor)
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Text("General", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = accentColor, modifier = Modifier.padding(bottom = 8.dp, start = 4.dp))
                MeldMenuGroup {
                    MeldMenuItem(
                        icon = TablerIcons.World,
                        title = "Source platform",
                        subtitle = sourcePlatformName
                    ) { copyToClipboard(sourcePlatformName) }

                    val titleLabel = if (isPodOrAb) (if (isAudiobook) "Chapter / Book title" else "Episode title") else "Song title"
                    MeldMenuItem(icon = TablerIcons.Music, title = titleLabel, subtitle = song.title) { copyToClipboard(song.title) }

                    val artistLabel = if (isPodOrAb) (if (isAudiobook) "Author / Reader" else "Show / Creator") else "Song artists"
                    MeldMenuItem(icon = TablerIcons.User, title = artistLabel, subtitle = song.artist) { copyToClipboard(song.artist) }

                    if (!song.album.isNullOrBlank() && song.album != "Unknown Album") {
                        val albumLabel = if (isAudiobook) "Audiobook" else if (isPodcast) "Podcast show" else "Album"
                        MeldMenuItem(icon = com.musicplayer.lytrix.ui.icons.LytrixIcons.Outlined.Album, title = albumLabel, subtitle = song.album) { copyToClipboard(song.album) }
                    }

                    MeldMenuItem(icon = TablerIcons.Bookmark, title = "Media id", subtitle = song.originalId ?: song.id.toString()) { copyToClipboard(song.originalId ?: song.id.toString()) }
                }
            }

            item {
                Text("Information", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = accentColor, modifier = Modifier.padding(bottom = 8.dp, start = 4.dp))
                MeldMenuGroup {
                    MeldMenuItem(icon = TablerIcons.Clock, title = "Duration", subtitle = durationText) { copyToClipboard(durationText) }
                    MeldMenuItem(icon = TablerIcons.File, title = "File Path / URL", subtitle = song.filePath ?: song.uriString) { copyToClipboard(song.filePath ?: song.uriString) }

                    if ((isYtSong || isJioSaavn) && !isRadioStream) {
                        if (viewsText != "N/A" && viewsText != "Unavailable") {
                            MeldMenuItem(icon = TablerIcons.Eye, title = "Views / Plays", subtitle = viewsText) { copyToClipboard(viewsText) }
                        }
                        if (isYtSong && likesText != "N/A" && likesText != "Unavailable") {
                            MeldMenuItem(icon = TablerIcons.ThumbUp, title = "Likes", subtitle = likesText) { copyToClipboard(likesText) }
                        }
                        if (uploadDateText != "Unknown" && uploadDateText != "Unavailable") {
                            MeldMenuItem(icon = TablerIcons.Calendar, title = if (isJioSaavn) "Release Year" else "Upload / Release Date", subtitle = uploadDateText) { copyToClipboard(uploadDateText) }
                        }
                    }

                    MeldMenuItem(icon = TablerIcons.File, title = "MIME type", subtitle = if(song.isVideo) "video/mp4" else "audio/mpeg") { copyToClipboard(if(song.isVideo) "video/mp4" else "audio/mpeg") }

                    if (fileSizeText != "Unknown" && fileSizeText != "File not found") {
                        MeldMenuItem(icon = TablerIcons.Database, title = "File size", subtitle = fileSizeText) { copyToClipboard(fileSizeText) }
                    }
                }
            }

            if (!isRadioStream) {
                if (!isPodOrAb && parsedCredits.isNotEmpty()) {
                    item {
                        Text("Song Credits", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = accentColor, modifier = Modifier.padding(bottom = 8.dp, start = 4.dp))
                        MeldMenuGroup {
                            parsedCredits.forEach { credit ->
                                MeldMenuItem(icon = TablerIcons.Music, title = credit.role, subtitle = credit.value) {
                                    copyToClipboard("${credit.role}: ${credit.value}")
                                }
                            }
                        }
                    }
                }
                if (isYtSong && !isPodOrAb) {
                    item {
                        MeldMenuGroup {
                            MeldMenuItem(
                                icon = TablerIcons.Message,
                                title = "YouTube Comments",
                                subtitle = "View community comments & replies"
                            ) {
                                onOpenComments()
                            }
                        }
                    }
                }
                if (descriptionText.isNotBlank() && descriptionText != "No description available." && descriptionText != "Error loading description.") {
                    item {
                        val descLabel = if (isPodOrAb) "Episode Show Notes / Description" else "Description"
                        Text(descLabel, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = accentColor, modifier = Modifier.padding(bottom = 8.dp, start = 4.dp))
                        MeldMenuGroup {
                            Text(text = descriptionText, color = MeldTextColor.copy(alpha=0.7f), fontSize = 14.sp, modifier = Modifier.fillMaxWidth().clickable { copyToClipboard(descriptionText) }.padding(16.dp))
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

// ========================================================
// 🔥 Extra Required Functions
// ========================================================
fun downloadOnlineSongFromPlayer(context: android.content.Context, song: com.musicplayer.lytrix.model.Song) {
    val songId = song.originalId ?: song.id.toString()
    val isYtSong = !(song.albumArtUriString?.contains("saavn", ignoreCase = true) ?: false)

    com.musicplayer.lytrix.playback.OkHttpDownloader.startDownload(
        context = context,
        songId = songId,
        title = song.title,
        artist = song.artist,
        imageUrl = song.albumArtUriString ?: "",
        // JioSaavn হলে সরাসরি লিংক, YouTube হলে ফাঁকা (ডাউনলোডার নিজে নতুন লিংক বের করবে)
        providedUrl = if (isYtSong) "" else song.uriString
    )
}
fun shareOnlineSong(context: android.content.Context, song: com.musicplayer.lytrix.model.Song?, isPodcast: Boolean, isJioSaavn: Boolean) {
    if (song == null) return

    // 🔥 composition থেকে আলাদা scope — মেনু বন্ধ হয়ে গেলেও ক্যানসেল হবে না
    CoroutineScope(Dispatchers.IO).launch {
        var fetchedUrl = ""
        try {
            val realId = song.originalId ?: song.id.toString()
            if (isPodcast) {
                val url = java.net.URL("https://itunes.apple.com/lookup?id=$realId")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.requestMethod = "GET"
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val results = org.json.JSONObject(response).optJSONArray("results")
                if (results != null && results.length() > 0) {
                    fetchedUrl = results.getJSONObject(0).optString("trackViewUrl", "")
                }
            } else if (isJioSaavn) {
                val url = java.net.URL("https://jiosaavn-api.shuvrodaspranto20170014.workers.dev/api/songs?ids=$realId")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.requestMethod = "GET"
                connection.setRequestProperty(
                    com.musicplayer.lytrix.constants.LytrixSecurityConstants.SAAVN_SECRET_HEADER,
                    com.musicplayer.lytrix.constants.LytrixSecurityConstants.SAAVN_SECRET_TOKEN
                )
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val dataArray = org.json.JSONObject(response).optJSONArray("data")
                if (dataArray != null && dataArray.length() > 0) {
                    // 🔥 এখান থেকে আসল jiosaavn.com এর লিংকটা বের হয়ে আসবে
                    fetchedUrl = dataArray.getJSONObject(0).optString("url", "")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val shareText = when {
            fetchedUrl.isNotBlank() -> "🎧 Listen to ${song.title} by ${song.artist}:\n$fetchedUrl"
            isPodcast -> "🎧 Listen to the podcast: ${song.title} by ${song.artist}"
            isJioSaavn -> "🎵 Listen to ${song.title} by ${song.artist}!"
            else -> "https://music.youtube.com/watch?v=${song.originalId ?: song.id}"
        }

        withContext(Dispatchers.Main) {
            try {
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(android.content.Intent.createChooser(intent, "Share Music"))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

fun startFloatingWidget(context: android.content.Context) {
    if (android.provider.Settings.canDrawOverlays(context)) {
        val intent = android.content.Intent(context, FloatingWidgetService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    } else {
        android.widget.Toast.makeText(context, "Please allow 'Display over other apps'", android.widget.Toast.LENGTH_LONG).show()
        val intent = android.content.Intent(
            android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            android.net.Uri.parse("package:${context.packageName}")
        )
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

private fun formatABTimestamp(ms: Long): String {
    val totalSec = (ms.coerceAtLeast(0L) / 1000L)
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    return "%d:%02d".format(mins, secs)
}

@Composable
fun ABSelectSheet(
    onSetPointA: () -> Unit,
    onSetPointB: () -> Unit,
    onClearAB: () -> Unit,
    isPointASet: Boolean,
    isPointBSet: Boolean,
    pointAMs: Long? = null,
    pointBMs: Long? = null,
    accentColor: Color,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp, top = 8.dp)) {
        Text(text = "A-B Repeat", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MeldTextColor, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        Text(text = "Select points to loop a specific part of the song.", fontSize = 14.sp, color = MeldTextColor.copy(alpha = 0.6f), modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp))
        Row(modifier = Modifier.fillMaxWidth().clickable { onSetPointA() }.padding(vertical = 16.dp, horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = TablerIcons.MapPin, contentDescription = null, tint = if (isPointASet) accentColor else MeldTextColor.copy(alpha = 0.7f), modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(20.dp))
                val labelA = if (isPointASet && pointAMs != null) "Point A · ${formatABTimestamp(pointAMs)}" else if (isPointASet) "Point A (Set)" else "Set Point A"
                Text(text = labelA, fontSize = 16.sp, color = if (isPointASet) accentColor else MeldTextColor)
            }
            if (isPointASet) Icon(imageVector = TablerIcons.Check, contentDescription = "Active", tint = accentColor)
        }
        Row(modifier = Modifier.fillMaxWidth().clickable(enabled = isPointASet) { if (isPointASet) onSetPointB() }.padding(vertical = 16.dp, horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = TablerIcons.MapPin, contentDescription = null, tint = if (!isPointASet) MeldTextColor.copy(alpha = 0.3f) else if (isPointBSet) accentColor else MeldTextColor.copy(alpha = 0.7f), modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(20.dp))
                val labelB = if (isPointBSet && pointBMs != null) "Point B · ${formatABTimestamp(pointBMs)}" else if (isPointBSet) "Point B (Set)" else "Set Point B"
                Text(text = labelB, fontSize = 16.sp, color = if (!isPointASet) MeldTextColor.copy(alpha = 0.3f) else if (isPointBSet) accentColor else MeldTextColor)
            }
            if (isPointBSet) Icon(imageVector = TablerIcons.Check, contentDescription = "Active", tint = accentColor)
        }
        if (isPointASet || isPointBSet) {
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp).height(1.dp).background(MeldTextColor.copy(alpha = 0.1f)))
            Row(modifier = Modifier.fillMaxWidth().clickable { onClearAB() }.padding(vertical = 16.dp, horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = TablerIcons.X, contentDescription = null, tint = Color.Red.copy(alpha = 0.8f), modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(20.dp))
                Text(text = "Clear Selection", fontSize = 16.sp, color = Color.Red.copy(alpha = 0.8f), fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun MenuRowItem(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 16.dp, horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = null, tint = MeldTextColor.copy(alpha = 0.7f), modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(20.dp))
        Text(text = text, fontSize = 16.sp, color = MeldTextColor)
    }
}

@Composable
fun PlaylistSelectionSheet(playlists: List<PlaylistEntity>, onAddToPlaylist: (Long) -> Unit, onCreatePlaylist: (String) -> Unit) {
    var showCreateDialog by remember { mutableStateOf(false) }
    if (showCreateDialog) {
        var playlistName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New Playlist", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    placeholder = { Text("Playlist name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (playlistName.isNotBlank()) onCreatePlaylist(playlistName)
                    showCreateDialog = false
                }) { Text("Create", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") } }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 28.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Add to playlist",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MeldTextColor
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MeldCardColor)
                .clickable { showCreateDialog = true }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = TablerIcons.Plus,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = "New Playlist",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MeldTextColor
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (playlists.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No custom playlists found",
                    fontSize = 14.sp,
                    color = MeldTextColor.copy(alpha = 0.5f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(playlists) { playlist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MeldCardColor)
                            .clickable { onAddToPlaylist(playlist.id) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = TablerIcons.Playlist,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = playlist.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MeldTextColor)
                        }
                        Text(text = "${playlist.songCount} songs", fontSize = 13.sp, color = MeldTextColor.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PremiumWheelPicker(items: List<Int>, initialValue: Int, onItemSelected: (Int) -> Unit, accentColor: Color) {
    val initialIndex = items.indexOf(initialValue).takeIf { it >= 0 } ?: 0
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val flingBehavior = androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(lazyListState = listState)
    val itemHeight = 56.dp
    LaunchedEffect(listState) { snapshotFlow { listState.firstVisibleItemIndex }.collect { index -> if (index in items.indices) { onItemSelected(items[index]) } } }
    Box(modifier = Modifier.height(itemHeight * 3).width(80.dp), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.fillMaxWidth().height(itemHeight).background(accentColor.copy(alpha = 0.15f), shape = RoundedCornerShape(12.dp)))
        LazyColumn(state = listState, flingBehavior = flingBehavior, modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            item { Spacer(modifier = Modifier.height(itemHeight)) }
            items(items.size) { index ->
                val isSelected by remember { derivedStateOf { listState.firstVisibleItemIndex == index } }
                Box(modifier = Modifier.height(itemHeight), contentAlignment = Alignment.Center) {
                    Text(text = items[index].toString().padStart(2, '0'), fontSize = if (isSelected) 32.sp else 20.sp, fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium, color = if (isSelected) accentColor else MeldTextColor.copy(alpha = 0.3f))
                }
            }
            item { Spacer(modifier = Modifier.height(itemHeight)) }
        }
    }
}

@Composable
fun SleepTimerSheet(onSetTimer: (Int, Boolean) -> Unit, accentColor: Color, currentSleepTimerMinutes: Int, playLastSongToEnd: Boolean) {
    var showCustomPicker by remember { mutableStateOf(false) }
    var selectedTimer by remember { mutableStateOf(currentSleepTimerMinutes) }
    var playLastOneToEnd by remember { mutableStateOf(playLastSongToEnd) }
    val timerOptions = listOf(0 to "Turn Off", 10 to "10 Minutes", 20 to "20 Minutes", 30 to "30 Minutes", 60 to "60 Minutes")
    var customHour by remember { mutableStateOf(if (selectedTimer >= 60) selectedTimer / 60 else 0) }
    var customMinute by remember { mutableStateOf(if (selectedTimer > 0) selectedTimer % 60 else 0) }

    if (showCustomPicker) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 8.dp, end = 16.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { showCustomPicker = false }) { Icon(TablerIcons.ArrowLeft, contentDescription = "Back", tint = MeldTextColor) }
                Text("Custom time", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MeldTextColor)
            }
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PremiumWheelPicker((0..23).toList(), customHour, { customHour = it }, accentColor)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Hour", color = MeldTextColor.copy(alpha = 0.6f), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(modifier = Modifier.width(40.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PremiumWheelPicker((0..59).toList(), customMinute, { customMinute = it }, accentColor)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Min", color = MeldTextColor.copy(alpha = 0.6f), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
            Button(onClick = { val totalMinutes = (customHour * 60) + customMinute; selectedTimer = totalMinutes; onSetTimer(totalMinutes, playLastOneToEnd) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp).height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = accentColor), shape = RoundedCornerShape(27.dp)) {
                Text("Set timer", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
            Text(text = "Sleep timer", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MeldTextColor, modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 8.dp))
            timerOptions.forEach { (minutes, label) ->
                TimerRowItem(label, selectedTimer == minutes, accentColor) { selectedTimer = minutes; onSetTimer(minutes, playLastOneToEnd) }
            }
            val isCustomSelected = selectedTimer !in timerOptions.map { it.first } && selectedTimer > 0
            Row(modifier = Modifier.fillMaxWidth().clickable { showCustomPicker = true }.padding(vertical = 16.dp, horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "Custom", fontSize = 16.sp, color = if (isCustomSelected) accentColor else MeldTextColor)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isCustomSelected) {
                        val h = selectedTimer / 60; val m = selectedTimer % 60
                        val timeStr = if (h > 0) "${h}h ${m}m" else "${m}m"
                        Text(timeStr, color = accentColor, fontSize = 14.sp, modifier = Modifier.padding(end = 8.dp), fontWeight = FontWeight.Medium)
                    } else { Text("Set", color = MeldTextColor.copy(alpha = 0.5f), fontSize = 14.sp, modifier = Modifier.padding(end = 8.dp)) }
                    Icon(imageVector = TablerIcons.ChevronRight, contentDescription = "Custom", tint = MeldTextColor.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                }
            }
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp).height(1.dp).background(MeldTextColor.copy(alpha = 0.1f)))
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "Play last song to end", fontSize = 16.sp, color = MeldTextColor)
                Switch(checked = playLastOneToEnd, onCheckedChange = { playLastOneToEnd = it; onSetTimer(selectedTimer, it) }, colors = SwitchDefaults.colors(checkedThumbColor = accentColor, checkedTrackColor = accentColor.copy(alpha = 0.5f)))
            }
        }
    }
}

@Composable
fun TimerRowItem(text: String, isSelected: Boolean, accentColor: Color, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 16.dp, horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = text, fontSize = 16.sp, color = if (isSelected) accentColor else MeldTextColor)
        if (isSelected) Icon(imageVector = TablerIcons.Check, contentDescription = "Active", tint = accentColor)
    }
}

@Composable
fun SpeedSelectionSheet(
    onSetSpeed: (Float) -> Unit,
    accentColor: Color,
    currentSpeed: Float,
    currentPitchSemitones: Int = 0,
    onSetPitch: ((Int) -> Unit)? = null,
    onResetTempoPitch: (() -> Unit)? = null
) {
    var selectedSpeed by remember(currentSpeed) { mutableFloatStateOf(currentSpeed) }
    var selectedPitch by remember(currentPitchSemitones) { mutableIntStateOf(currentPitchSemitones) }

    if (onSetPitch == null) {
        val speedOptions = listOf(0.5f to "0.5X", 0.75f to "0.75X", 1.0f to "1.0X (Default)", 1.25f to "1.25X", 1.5f to "1.5X", 2.0f to "2.0X")
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
            speedOptions.forEach { (speed, label) -> TimerRowItem(label, selectedSpeed == speed, accentColor) { selectedSpeed = speed; onSetSpeed(speed) } }
        }
        return
    }

    val isModified = kotlin.math.abs(selectedSpeed - 1.0f) > 0.01f || selectedPitch != 0
    val speedPresets = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    val pitchPresets = listOf(-4, -2, 0, 2, 4)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Tempo & Pitch",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MeldTextColor
            )
            if (isModified) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(accentColor.copy(alpha = 0.16f))
                        .clickable {
                            selectedSpeed = 1.0f
                            selectedPitch = 0
                            onResetTempoPitch?.invoke() ?: run {
                                onSetSpeed(1.0f)
                                onSetPitch(0)
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.RestartAlt,
                        contentDescription = "Reset",
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Reset",
                        color = accentColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Tempo (Speed) section - Meld-style stepper + presets
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MeldCardColor)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(
                        imageVector = TablerIcons.Dashboard,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Text("Speed (Tempo)", color = MeldTextColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.10f))
                            .clickable {
                                val next = (kotlin.math.round(((selectedSpeed - 0.05f).coerceIn(0.25f, 2.0f)) * 100f) / 100f)
                                selectedSpeed = next
                                onSetSpeed(next)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Remove, contentDescription = "Decrease speed", tint = MeldTextColor, modifier = Modifier.size(18.dp))
                    }
                    Text(
                        text = "${String.format(java.util.Locale.US, "%.2f", selectedSpeed)}x",
                        color = MeldTextColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.widthIn(min = 52.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.10f))
                            .clickable {
                                val next = (kotlin.math.round(((selectedSpeed + 0.05f).coerceIn(0.25f, 2.0f)) * 100f) / 100f)
                                selectedSpeed = next
                                onSetSpeed(next)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "Increase speed", tint = MeldTextColor, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                speedPresets.forEach { preset ->
                    val active = kotlin.math.abs(selectedSpeed - preset) < 0.01f
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (active) accentColor else Color.White.copy(alpha = 0.08f))
                            .clickable {
                                selectedSpeed = preset
                                onSetSpeed(preset)
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${preset}x",
                            color = if (active) Color.Black else MeldTextColor,
                            fontSize = 12.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Pitch (Semitones) section - Meld-style transpose (-12..+12)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MeldCardColor)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(
                        imageVector = TablerIcons.Music,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Text("Pitch (Semitones)", color = MeldTextColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.10f))
                            .clickable {
                                val next = (selectedPitch - 1).coerceIn(-12, 12)
                                selectedPitch = next
                                onSetPitch(next)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Remove, contentDescription = "Decrease pitch", tint = MeldTextColor, modifier = Modifier.size(18.dp))
                    }
                    Text(
                        text = "${if (selectedPitch > 0) "+" else ""}$selectedPitch st",
                        color = MeldTextColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.widthIn(min = 52.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.10f))
                            .clickable {
                                val next = (selectedPitch + 1).coerceIn(-12, 12)
                                selectedPitch = next
                                onSetPitch(next)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "Increase pitch", tint = MeldTextColor, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                pitchPresets.forEach { st ->
                    val active = selectedPitch == st
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (active) accentColor else Color.White.copy(alpha = 0.08f))
                            .clickable {
                                selectedPitch = st
                                onSetPitch(st)
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${if (st > 0) "+" else ""}$st",
                            color = if (active) Color.Black else MeldTextColor,
                            fontSize = 12.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VideoQualitySheet(currentQuality: String, onSetQuality: (String) -> Unit, accentColor: Color) {
    val options = listOf("Auto", "1080p", "720p", "480p", "360p", "144p")
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
        Text(text = "Video Quality", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 8.dp), color = MeldTextColor)
        options.forEach { quality ->
            val label = if (quality == "Auto") "Auto (Recommended)" else quality
            TimerRowItem(text = label, isSelected = currentQuality == quality, accentColor = accentColor) { onSetQuality(quality) }
        }
    }
}
fun shareLocalSong(context: android.content.Context, song: com.musicplayer.lytrix.model.Song) {
    try {
        val raw = song.uriString
        val shareUri: android.net.Uri = when {
            raw.startsWith("content://") -> android.net.Uri.parse(raw)
            song.isVideo -> android.content.ContentUris.withAppendedId(
                android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                kotlin.math.abs(song.id)
            )
            else -> android.content.ContentUris.withAppendedId(
                android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                kotlin.math.abs(song.id)
            )
        }

        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = if (song.isVideo) "video/*" else "audio/*"
            putExtra(android.content.Intent.EXTRA_STREAM, shareUri)
            putExtra(android.content.Intent.EXTRA_TEXT, "🎵 ${song.title} - ${song.artist}")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val chooser = android.content.Intent.createChooser(intent, "Share Song")
        chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    } catch (e: Exception) {
        e.printStackTrace()
        android.widget.Toast.makeText(context, "Share failed!", android.widget.Toast.LENGTH_SHORT).show()
    }
}