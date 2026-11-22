package com.musicplayer.lytrix.ui.library

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.musicplayer.lytrix.PodcastEpisode
import com.musicplayer.lytrix.PodcastInfo
import com.musicplayer.lytrix.model.JioSaavnSong
import com.musicplayer.lytrix.model.Song
import com.musicplayer.lytrix.ui.BottomSheetType
import com.musicplayer.lytrix.ui.OnlineSongMenu
import com.musicplayer.lytrix.ui.PlayerViewModel
import com.musicplayer.lytrix.ui.playUnifiedMediaEpisode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowsDetailRoute(
    viewModel: PlayerViewModel,
    title: String,
    isAudiobook: Boolean,
    onBackClick: () -> Unit,
    onNavigateToArtist: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val curSong by viewModel.currentSong.collectAsState()

    val dbType = if (isAudiobook) "Audiobook" else "Podcast"
    val dbName = if (isAudiobook) "online_audiobooks_meta" else "online_podcasts_meta"
    val prefix = if (isAudiobook) "online_ab_" else "online_podcast_"

    val plMetaPrefs = remember(dbName) { context.getSharedPreferences(dbName, Context.MODE_PRIVATE) }
    val plSongsPrefs = remember(prefix, title) { context.getSharedPreferences("$prefix$title", Context.MODE_PRIVATE) }
    val laterName = if (isAudiobook) "Books for later" else "Episodes for later"
    val laterPrefix = if (isAudiobook) "online_ab_" else "online_podcast_"
    val laterPrefs = remember(laterPrefix, laterName) { context.getSharedPreferences("$laterPrefix$laterName", Context.MODE_PRIVATE) }
    val likedPrefName = if (isAudiobook) "online_liked_audiobooks" else "online_liked_podcasts"
    val likedEpisodePrefs = remember(likedPrefName) { context.getSharedPreferences(likedPrefName, Context.MODE_PRIVATE) }

    var isSaved by remember(title) {
        mutableStateOf(plMetaPrefs.getStringSet("names", emptySet())?.contains(title) == true)
    }
    var episodes by remember(title) { mutableStateOf<List<JioSaavnSong>>(emptyList()) }
    var author by remember(title) { mutableStateOf(curSong?.artist?.takeIf { it.isNotBlank() } ?: "") }
    var coverUrl by remember(title) { mutableStateOf(curSong?.albumArtUriString ?: "") }
    var description by remember(title) { mutableStateOf("") }
    var isLoading by remember(title) { mutableStateOf(true) }

    var selectedEpisodeForMenu by remember { mutableStateOf<JioSaavnSong?>(null) }
    var showMenuSheet by remember { mutableStateOf(false) }

    // 1. Instant local load
    LaunchedEffect(title) {
        val localList = plSongsPrefs.all.values.mapNotNull { value ->
            try {
                val jsonStr = value.toString()
                if (jsonStr.startsWith("{")) {
                    val obj = JSONObject(jsonStr)
                    JioSaavnSong(
                        id = obj.optString("id", ""),
                        name = obj.optString("name", obj.optString("title", "Episode")),
                        artist = obj.optString("artist", obj.optString("publisher", "")),
                        imageUrl = obj.optString("imageUrl", obj.optString("image", "")),
                        downloadUrl = obj.optString("downloadUrl", obj.optString("mediaUrl", obj.optString("audio", ""))),
                        albumName = obj.optString("album", title),
                    )
                } else null
            } catch (_: Exception) { null }
        }
        if (localList.isNotEmpty()) {
            episodes = localList
            if (author.isBlank()) author = localList.firstOrNull()?.artist.orEmpty()
            if (coverUrl.isBlank()) coverUrl = localList.firstOrNull()?.imageUrl.orEmpty()
            isLoading = false
        }
    }

    // 2. Fetch live episodes from Apple Podcasts / iTunes API
    LaunchedEffect(title, isAudiobook) {
        withContext(Dispatchers.IO) {
            try {
                val searchQuery = if (isAudiobook) "$title audiobook" else title
                val encoded = URLEncoder.encode(searchQuery, "UTF-8")
                val urlString = "https://itunes.apple.com/search?term=$encoded&media=podcast&entity=podcastEpisode&limit=40"
                val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    setRequestProperty("User-Agent", "Mozilla/5.0")
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val results = JSONObject(body).optJSONArray("results")
                if (results != null && results.length() > 0) {
                    val remoteList = mutableListOf<JioSaavnSong>()
                    var foundAuthor = ""
                    var foundCover = ""
                    var foundDesc = ""
                    for (i in 0 until results.length()) {
                        val obj = results.optJSONObject(i) ?: continue
                        val tName = obj.optString("trackName").trim()
                        val cName = obj.optString("collectionName").trim()
                        val aName = obj.optString("artistName").trim()
                        val audio = obj.optString("episodeUrl").trim()
                        val img = obj.optString("artworkUrl600").ifBlank { obj.optString("artworkUrl100") }
                        val tId = obj.optString("trackId").ifBlank { "${obj.optString("collectionId")}_$i" }
                        val durMs = obj.optLong("trackTimeMillis", 0L)
                        val durStr = if (durMs > 0) {
                            val totalSec = durMs / 1000
                            val m = totalSec / 60
                            val s = totalSec % 60
                            "$m:${s.toString().padStart(2, '0')}"
                        } else ""

                        if (tName.isBlank() || audio.isBlank()) continue
                        if (foundAuthor.isBlank()) foundAuthor = aName
                        if (foundCover.isBlank()) foundCover = img
                        if (foundDesc.isBlank()) foundDesc = obj.optString("description").take(400)

                        remoteList.add(
                            JioSaavnSong(
                                id = tId,
                                name = tName,
                                artist = aName.ifBlank { title },
                                imageUrl = img,
                                downloadUrl = audio,
                                albumName = cName.ifBlank { title }
                            )
                        )
                    }

                    if (remoteList.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            episodes = remoteList
                            if (author.isBlank() && foundAuthor.isNotBlank()) author = foundAuthor
                            if (coverUrl.isBlank() && foundCover.isNotBlank()) coverUrl = foundCover
                            if (description.isBlank() && foundDesc.isNotBlank()) description = foundDesc
                            isLoading = false
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                withContext(Dispatchers.Main) {
                    isLoading = false
                }
            }
        }
    }

    // Fallback if no episodes were found online and current song matches
    if (episodes.isEmpty() && curSong != null) {
        val s = curSong!!
        val isMatch = s.title.contains(title, ignoreCase = true) ||
                s.album.contains(title, ignoreCase = true) ||
                s.artist.contains(title, ignoreCase = true)
        if (isMatch) {
            episodes = listOf(
                JioSaavnSong(
                    id = s.originalId ?: s.id.toString(),
                    name = s.title,
                    artist = s.artist,
                    imageUrl = s.albumArtUriString ?: "",
                    downloadUrl = s.uriString ?: "",
                    albumName = s.album.ifBlank { title }
                )
            )
        }
    }

    val savedForLaterIds = remember(laterPrefs) { laterPrefs.all.keys.toSet() }
    val likedEpisodeIds = remember(likedEpisodePrefs) { likedEpisodePrefs.getStringSet("liked_ids", emptySet())?.toSet() ?: emptySet() }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        MetroPodcastShowDetailScreen(
            title = title,
            author = author.ifBlank { if (isAudiobook) "Audiobook Author" else "Podcast Show" },
            thumbnailUrl = coverUrl.ifBlank { curSong?.albumArtUriString ?: "" },
            description = description.takeIf { it.isNotBlank() },
            episodes = episodes,
            isSaved = isSaved,
            onToggleSave = {
                val currentNames = plMetaPrefs.getStringSet("names", emptySet())?.toMutableSet() ?: mutableSetOf()
                if (currentNames.contains(title)) {
                    currentNames.remove(title)
                    plSongsPrefs.edit().clear().apply()
                    Toast.makeText(context, "Removed from Library", Toast.LENGTH_SHORT).show()
                } else {
                    currentNames.add(title)
                    val editor = plSongsPrefs.edit()
                    episodes.forEach { ep ->
                        val obj = JSONObject().apply {
                            put("id", ep.id)
                            put("name", ep.name)
                            put("title", ep.name)
                            put("artist", ep.artist)
                            put("imageUrl", ep.imageUrl)
                            put("downloadUrl", ep.downloadUrl)
                            put("album", title)
                        }
                        editor.putString(ep.id, obj.toString())
                    }
                    editor.apply()
                    Toast.makeText(context, "Saved to Library", Toast.LENGTH_SHORT).show()
                }
                plMetaPrefs.edit().putStringSet("names", currentNames).apply()
                isSaved = !isSaved
            },
            onPlayEpisode = { clickedEp, episodeList ->
                val eps = episodeList.map { s ->
                    PodcastEpisode(
                        id = s.id,
                        title_original = s.name,
                        audio = s.downloadUrl.ifBlank { "yt:${s.id}" },
                        image = s.imageUrl,
                        audio_length_sec = 0,
                        podcast = PodcastInfo(author.ifBlank { s.artist }),
                        channelId = null,
                    )
                }
                val target = eps.find { it.id == clickedEp.id } ?: eps.first()
                playUnifiedMediaEpisode(
                    context = context,
                    viewModel = viewModel,
                    episode = target,
                    queue = eps,
                    mediaCategory = if (isAudiobook) "AUDIOBOOK" else "PODCAST"
                )
            },
            onToggleLikeEpisode = { ep ->
                val set = likedEpisodePrefs.getStringSet("liked_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
                if (set.contains(ep.id)) {
                    set.remove(ep.id)
                    likedEpisodePrefs.edit().remove("song_data_${ep.id}").putStringSet("liked_ids", set).apply()
                    Toast.makeText(context, "Removed from Liked", Toast.LENGTH_SHORT).show()
                } else {
                    set.add(ep.id)
                    val jsonObj = JSONObject().apply {
                        put("id", ep.id); put("title", ep.name); put("name", ep.name)
                        put("subtitle", ep.artist); put("artist", ep.artist); put("image", ep.imageUrl)
                        put("imageUrl", ep.imageUrl); put("url", ep.downloadUrl); put("downloadUrl", ep.downloadUrl)
                    }
                    likedEpisodePrefs.edit().putString("song_data_${ep.id}", jsonObj.toString()).putStringSet("liked_ids", set).apply()
                    Toast.makeText(context, "Saved to Liked", Toast.LENGTH_SHORT).show()
                }
            },
            likedEpisodeIds = likedEpisodeIds,
            onSaveForLater = { ep ->
                if (laterPrefs.contains(ep.id)) {
                    laterPrefs.edit().remove(ep.id).apply()
                    Toast.makeText(context, "Removed from $laterName", Toast.LENGTH_SHORT).show()
                } else {
                    val json = JSONObject().apply {
                        put("id", ep.id)
                        put("name", ep.name)
                        put("artist", ep.artist)
                        put("imageUrl", ep.imageUrl)
                        put("downloadUrl", ep.downloadUrl)
                        put("albumName", title)
                    }
                    laterPrefs.edit().putString(ep.id, json.toString()).apply()
                    Toast.makeText(context, "Saved to $laterName", Toast.LENGTH_SHORT).show()
                }
            },
            savedForLaterIds = savedForLaterIds,
            onMoreClick = { ep ->
                selectedEpisodeForMenu = ep
                showMenuSheet = true
            },
            onChannelClick = { onNavigateToArtist(it) },
            onBackClick = onBackClick
        )

        if (isLoading && episodes.isEmpty()) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }

    if (showMenuSheet && selectedEpisodeForMenu != null) {
        val ep = selectedEpisodeForMenu!!
        val fakeSong = remember(ep) {
            Song(
                id = ep.id.hashCode().toLong(),
                title = ep.name,
                artist = ep.artist,
                album = if (isAudiobook) "Audiobook" else "Podcast",
                duration = 0L,
                uriString = ep.downloadUrl,
                filePath = if (isAudiobook) "ONLINE_AUDIOBOOK" else "ONLINE_PODCAST",
                albumArtUriString = ep.imageUrl,
                isVideo = false,
                hasLyrics = false,
                originalId = ep.id,
                channelId = null,
                mediaType = if (isAudiobook) "AUDIOBOOK" else "PODCAST"
            )
        }
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
        ModalBottomSheet(
            onDismissRequest = {
                showMenuSheet = false
                selectedEpisodeForMenu = null
            },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            OnlineSongMenu(
                context = context,
                song = fakeSong,
                onNavigate = { _ -> },
                onDismiss = {
                    showMenuSheet = false
                    selectedEpisodeForMenu = null
                },
                onPowerSavingMode = { },
                onNavigateToArtist = onNavigateToArtist,
                onPlayOnline = {
                    showMenuSheet = false
                    selectedEpisodeForMenu = null
                    val eps = episodes.map { s ->
                        PodcastEpisode(
                            id = s.id,
                            title_original = s.name,
                            audio = s.downloadUrl.ifBlank { "yt:${s.id}" },
                            image = s.imageUrl,
                            audio_length_sec = 0,
                            podcast = PodcastInfo(author.ifBlank { s.artist }),
                            channelId = null,
                        )
                    }
                    val target = eps.find { it.id == ep.id } ?: eps.first()
                    playUnifiedMediaEpisode(
                        context = context,
                        viewModel = viewModel,
                        episode = target,
                        queue = eps,
                        mediaCategory = if (isAudiobook) "AUDIOBOOK" else "PODCAST"
                    )
                },
                onNavigateToListenTogether = { }
            )
        }
    }
}
