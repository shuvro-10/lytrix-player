@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.musicplayer.lytrix.ui

import android.content.ComponentName
import android.content.Context
import android.widget.Toast
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.imageLoader
import com.musicplayer.lytrix.data.SongScanner
import com.musicplayer.lytrix.model.Song
import com.musicplayer.lytrix.service.PlaybackService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Deferred
import kotlin.random.Random

// 🔥 DataStore Imports
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import com.musicplayer.lytrix.LytrixSongItem
import com.musicplayer.lytrix.RetrofitInstance
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.ServiceList
import com.musicplayer.lytrix.innertube.YouTube
import com.musicplayer.lytrix.innertube.MusicVideoCounterpart
import com.musicplayer.lytrix.innertube.CounterpartPositionMap
import com.musicplayer.lytrix.innertube.CounterpartSegment
import com.musicplayer.lytrix.utils.InnerTubeXPlayer
import com.musicplayer.lytrix.innertube.pages.HomePage
import com.musicplayer.lytrix.innertube.models.SongItem
import com.musicplayer.lytrix.innertube.utils.completed
import org.schabi.newpipe.extractor.timeago.patterns.it
import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.musicplayer.lytrix.shazam.Shazam
import com.musicplayer.lytrix.shazam.ShazamSignatureGenerator
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.musicplayer.lytrix.model.ListenRoom
import com.musicplayer.lytrix.model.RoomMember
import com.musicplayer.lytrix.model.SyncState
import com.musicplayer.lytrix.listentogether.metro.MetroManager
import com.musicplayer.lytrix.listentogether.metro.MetroRoomRole
import com.musicplayer.lytrix.listentogether.metro.PlaybackActions
import com.musicplayer.lytrix.listentogether.metro.TrackInfo
import kotlinx.coroutines.tasks.await

class PlayerViewModel : ViewModel() {

    companion object {
        var activeInstance: PlayerViewModel? = null
    }

    private fun loadOnlineQueue(context: Context) {
        try {
            val jsonString = context.getSharedPreferences("playback_prefs", Context.MODE_PRIVATE)
                .getString("saved_queue", "[]") ?: "[]"
            if (jsonString.length <= 2) return
            val arr = org.json.JSONArray(jsonString)
            val list = mutableListOf<Song>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(Song(
                    id = obj.getLong("id"),
                    title = obj.getString("title"),
                    artist = obj.getString("artist"),
                    album = obj.getString("album"),
                    duration = obj.getLong("duration"),
                    uriString = obj.getString("uriString"),
                    albumArtUriString = if (obj.has("albumArtUriString")) obj.getString("albumArtUriString") else null,
                    filePath = if (obj.has("filePath")) obj.getString("filePath") else null,
                    hasLyrics = obj.optBoolean("hasLyrics", false),
                    isVideo = obj.optBoolean("isVideo", false),
                    originalId = if (obj.has("originalId")) obj.getString("originalId") else null
                ))
            }

            if (list.isNotEmpty()) {
                _currentQueueList.value = list
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveOnlineQueue(context: Context, list: List<Song>) {
        try {
            val arr = org.json.JSONArray()
            list.take(500).forEach { song ->
                val obj = org.json.JSONObject()
                obj.put("id", song.id)
                obj.put("title", song.title)
                obj.put("artist", song.artist)
                obj.put("album", song.album)
                obj.put("duration", song.duration)
                obj.put("uriString", song.uriString)
                song.albumArtUriString?.let { obj.put("albumArtUriString", it) }
                song.filePath?.let { obj.put("filePath", it) }
                obj.put("hasLyrics", song.hasLyrics)
                obj.put("isVideo", song.isVideo)
                song.originalId?.let { obj.put("originalId", it) }
                arr.put(obj)
            }

            context.getSharedPreferences("playback_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("saved_queue", arr.toString())
                .apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val _songsList = MutableStateFlow<List<Song>>(emptyList())
    val songsList: StateFlow<List<Song>> = _songsList.asStateFlow()
    private val _isCinemaActive = MutableStateFlow(false)
    val isCinemaActive: StateFlow<Boolean> = _isCinemaActive.asStateFlow()

    fun setCinemaActive(active: Boolean) {
        _isCinemaActive.value = active
    }
    private val _accountPlaylists = MutableStateFlow<List<YtMusicTrack>?>(null)
    val accountPlaylists = _accountPlaylists.asStateFlow()
    private val _speedDialItems = MutableStateFlow<List<YtMusicTrack>>(emptyList())
    val speedDialItems = _speedDialItems.asStateFlow()
    private val _pinnedIds = MutableStateFlow<Set<String>>(emptySet())
    val pinnedIds = _pinnedIds.asStateFlow()
    // 🔥 হোমের মুড চিপসের জন্য আলাদা লিস্ট
    private val _homeYtMusicSections = MutableStateFlow<List<YtMusicSection>>(emptyList())
    val homeYtMusicSections: StateFlow<List<YtMusicSection>> = _homeYtMusicSections
    // 🔥 এক্সপ্লোর ট্যাবের জন্য আলাদা লিস্ট
    private val _exploreYtMusicSections = MutableStateFlow<List<YtMusicSection>>(emptyList())
    val exploreYtMusicSections: StateFlow<List<YtMusicSection>> = _exploreYtMusicSections
    private val speedDialPrefs: android.content.SharedPreferences?
        get() = (appContext ?: runCatching { com.musicplayer.lytrix.LytrixApplication.instance }.getOrNull())
            ?.getSharedPreferences("speed_dial_db", android.content.Context.MODE_PRIVATE)

    fun loadAccountPlaylists() {
        if (com.musicplayer.lytrix.innertube.YouTube.cookie == null) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val response = com.musicplayer.lytrix.innertube.YouTube.library("FEmusic_liked_playlists").getOrNull()
            if (response != null) {
                val list = mutableListOf<YtMusicTrack>()
                list.addAll(response.items.mapNotNull { it.toYtMusicTrack() })
                _accountPlaylists.value = list
            }
        }
    }

    fun loadSpeedDial() {
        // Meld-style: pinned + keepListening (history) + quickPicks + home sections combined
        val pinnedJson = speedDialPrefs?.getString("speed_dial_list", "[]") ?: "[]"
        try {
            // 1. Load pinned (user explicitly pinned items)
            val pinnedJsonArray = org.json.JSONArray(pinnedJson)
            val pinned = mutableListOf<YtMusicTrack>()
            for (i in 0 until pinnedJsonArray.length()) {
                val obj = pinnedJsonArray.getJSONObject(i)
                pinned.add(YtMusicTrack(
                    id = obj.optString("id", ""),
                    title = obj.optString("title", ""),
                    artist = obj.optString("artist", ""),
                    thumbnailUrl = obj.optString("thumbnailUrl", ""),
                    playCount = obj.optString("playCount", "")
                ))
            }

            _pinnedIds.value = pinned.map { it.id }.toSet()
            // 2. Load keepListening (recent history) from yt_history_db (filter out Shows/Audiobooks/Podcasts)
            val resolvedCtx = appContext ?: runCatching { com.musicplayer.lytrix.LytrixApplication.instance }.getOrNull()
            val historyPrefs = resolvedCtx?.getSharedPreferences("yt_history_db", android.content.Context.MODE_PRIVATE)
            val historyJson = historyPrefs?.getString("history_list", "[]") ?: "[]"
            val historyArray = org.json.JSONArray(historyJson)
            val keepListening = mutableListOf<YtMusicTrack>()
            var historyChanged = false
            for (i in 0 until historyArray.length()) {
                val obj = historyArray.getJSONObject(i)
                val id = obj.optString("id", "")
                val title = obj.optString("title", "")
                val artist = obj.optString("artist", "")
                val isShow = id.startsWith("pod_") || id.startsWith("ab_") ||
                    title.contains("audiobook", ignoreCase = true) ||
                    title.contains("podcast", ignoreCase = true) ||
                    artist.contains("audiobook", ignoreCase = true) ||
                    artist.contains("podcast", ignoreCase = true) ||
                    artist.contains("Peaks Audiobooks", ignoreCase = true)
                if (isShow) {
                    historyChanged = true
                    continue
                }
                keepListening.add(YtMusicTrack(
                    id = id,
                    title = title,
                    artist = artist,
                    thumbnailUrl = obj.optString("thumbnailUrl", "")
                ))
            }
            if (historyChanged && historyPrefs != null) {
                val newArr = org.json.JSONArray()
                keepListening.forEach { t ->
                    newArr.put(org.json.JSONObject().apply {
                        put("id", t.id)
                        put("title", t.title)
                        put("artist", t.artist)
                        put("thumbnailUrl", t.thumbnailUrl)
                    })
                }
                historyPrefs.edit().putString("history_list", newArr.toString()).apply()
            }

            // 3. Quick picks = shuffled history
            val quickPicks = keepListening.shuffled().take(10)
            // 4. Home sections (from both explore and home) - Filter out Shows/Audiobooks/Podcasts
            val homeSections = (_exploreYtMusicSections.value + _homeYtMusicSections.value).flatMap { it.tracks }
                .filterNot {
                    it.id.startsWith("pod_") || it.id.startsWith("ab_") ||
                    it.title.contains("audiobook", ignoreCase = true) ||
                    it.title.contains("podcast", ignoreCase = true) ||
                    it.artist.contains("audiobook", ignoreCase = true) ||
                    it.artist.contains("podcast", ignoreCase = true) ||
                    it.artist.contains("Peaks Audiobooks", ignoreCase = true)
                }
                .take(60)
            val cleanPinned = pinned.filterNot {
                it.id.startsWith("pod_") || it.id.startsWith("ab_") ||
                it.title.contains("audiobook", ignoreCase = true) ||
                it.title.contains("podcast", ignoreCase = true) ||
                it.artist.contains("audiobook", ignoreCase = true) ||
                it.artist.contains("podcast", ignoreCase = true) ||
                it.artist.contains("Peaks Audiobooks", ignoreCase = true)
            }
            // Combine exactly like Meld's buildSpeedDialItems
            val combined = (cleanPinned + keepListening + quickPicks + homeSections)
                .distinctBy { it.id }
                .filter { it.id.isNotBlank() && it.thumbnailUrl.isNotBlank() && !_notInterestedSongIds.value.contains(it.id) }
                .take(27)
            _speedDialItems.value = combined
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Pin/unpin a track permanently to speed dial (saved to SharedPreferences)

    fun toggleSpeedDial(track: YtMusicTrack) {
        val pinnedJson = speedDialPrefs?.getString("speed_dial_list", "[]") ?: "[]"
        try {
            val pinnedArray = org.json.JSONArray(pinnedJson)
            val pinned = mutableListOf<YtMusicTrack>()
            for (i in 0 until pinnedArray.length()) {
                val obj = pinnedArray.getJSONObject(i)
                pinned.add(YtMusicTrack(
                    id = obj.optString("id", ""),
                    title = obj.optString("title", ""),
                    artist = obj.optString("artist", ""),
                    thumbnailUrl = obj.optString("thumbnailUrl", ""),
                    playCount = obj.optString("playCount", "")
                ))
            }

            val idx = pinned.indexOfFirst { it.id == track.id }
            if (idx >= 0) pinned.removeAt(idx) else pinned.add(0, track)
            saveSpeedDial(pinned)
            _pinnedIds.value = pinned.map { it.id }.toSet()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        loadSpeedDial()
    }

    private fun saveSpeedDial(list: List<YtMusicTrack>) {
        val jsonArray = org.json.JSONArray()
        list.forEach { track ->
            val obj = org.json.JSONObject()
            obj.put("id", track.id)
            obj.put("title", track.title)
            obj.put("artist", track.artist)
            obj.put("thumbnailUrl", track.thumbnailUrl)
            obj.put("playCount", track.playCount)
            jsonArray.put(obj)
        }

        speedDialPrefs?.edit()?.putString("speed_dial_list", jsonArray.toString())?.apply()
    }

    data class NotInterestedSong(
        val id: String,
        val title: String,
        val artist: String,
        val thumbnailUrl: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    data class UndoSnackbarEvent(
        val message: String = "Song removed from recommendations",
        val songId: String
    )
    private val _snackbarEvent = kotlinx.coroutines.flow.MutableSharedFlow<UndoSnackbarEvent>(extraBufferCapacity = 1)
    val snackbarEvent: SharedFlow<UndoSnackbarEvent> = _snackbarEvent.asSharedFlow()

    fun triggerUndoSnackbar(songId: String, message: String = "Song removed from recommendations") {
        _snackbarEvent.tryEmit(UndoSnackbarEvent(message, songId))
    }

    private val notInterestedPrefs: android.content.SharedPreferences?
        get() = (appContext ?: runCatching { com.musicplayer.lytrix.LytrixApplication.instance }.getOrNull())
            ?.getSharedPreferences("not_interested_prefs", android.content.Context.MODE_PRIVATE)
    private val _notInterestedSongIds = kotlinx.coroutines.flow.MutableStateFlow<Set<String>>(emptySet())
    val notInterestedSongIds: kotlinx.coroutines.flow.StateFlow<Set<String>> = _notInterestedSongIds.asStateFlow()
    private val _notInterestedSongs = kotlinx.coroutines.flow.MutableStateFlow<List<NotInterestedSong>>(emptyList())
    val notInterestedSongs: kotlinx.coroutines.flow.StateFlow<List<NotInterestedSong>> = _notInterestedSongs.asStateFlow()

    data class RemovedSectionPosition(
        val sectionTitle: String,
        val sectionIndex: Int,
        val trackIndex: Int,
        val isHome: Boolean
    )

    data class RemovedFeaturedCardPosition(
        val cardId: String,
        val trackIndex: Int
    )
    private data class RemovedTrackHistory(
        val track: YtMusicTrack,
        val positions: List<RemovedSectionPosition>,
        val featuredCardPositions: List<RemovedFeaturedCardPosition> = emptyList(),
        val speedDialIndex: Int,
        val playlistTrackIndex: Int
    )
    private var lastRemovedHistory: RemovedTrackHistory? = null

    fun loadNotInterestedSongs() {
        val prefs = notInterestedPrefs ?: return
        try {
            val ids = prefs.getStringSet("not_interested_ids", emptySet()) ?: emptySet()
            _notInterestedSongIds.value = ids
            val list = ids.mapNotNull { id ->
                val json = prefs.getString("song_meta_$id", null) ?: return@mapNotNull null
                try {
                    val obj = org.json.JSONObject(json)
                    NotInterestedSong(
                        id = obj.optString("id", id),
                        title = obj.optString("title", ""),
                        artist = obj.optString("artist", ""),
                        thumbnailUrl = obj.optString("thumbnailUrl", ""),
                        timestamp = obj.optLong("timestamp", 0L)
                    )
                } catch (e: Exception) {
                    null
                }

            }.sortedByDescending { it.timestamp }
            _notInterestedSongs.value = list
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun markSongNotInterested(id: String, title: String, artist: String, thumbnailUrl: String, itemType: String = "") {
        val prefs = notInterestedPrefs ?: return
        try {
            val currentIds = prefs.getStringSet("not_interested_ids", emptySet()) ?: emptySet()
            val newIds = HashSet(currentIds).apply { add(id) }
            val obj = org.json.JSONObject().apply {
                put("id", id)
                put("title", title)
                put("artist", artist)
                put("thumbnailUrl", thumbnailUrl)
                put("timestamp", System.currentTimeMillis())
            }

            prefs.edit()
                .putStringSet("not_interested_ids", newIds)
                .putString("song_meta_$id", obj.toString())
                .apply()
            _notInterestedSongIds.value = newIds
            val currentList = _notInterestedSongs.value.toMutableList()
            currentList.removeAll { it.id == id }
            currentList.add(0, NotInterestedSong(id, title, artist, thumbnailUrl, System.currentTimeMillis()))
            _notInterestedSongs.value = currentList
            // Capture removal position in ALL matching sections so Undo restores everywhere
            val removedTrack = YtMusicTrack(id = id, title = title, artist = artist, thumbnailUrl = thumbnailUrl, itemType = itemType)
            val positions = mutableListOf<RemovedSectionPosition>()
            // 1. Check in Explore feed (all sections that contain this track)
            val currentExplore = _exploreYtMusicSections.value
            for (sIdx in currentExplore.indices) {
                val sec = currentExplore[sIdx]
                val tIdx = sec.tracks.indexOfFirst { it.id == id }
                if (tIdx != -1) {
                    positions.add(RemovedSectionPosition(sec.title, sIdx, tIdx, isHome = false))
                }
            }

            // 2. Check in Home feed (all sections that contain this track)
            val currentHome = _homeYtMusicSections.value
            for (sIdx in currentHome.indices) {
                val sec = currentHome[sIdx]
                val tIdx = sec.tracks.indexOfFirst { it.id == id }
                if (tIdx != -1) {
                    positions.add(RemovedSectionPosition(sec.title, sIdx, tIdx, isHome = true))
                }
            }

            // 3. Check in Community Featured Cards (previewSongs of each playlist card)
            val featuredPositions = mutableListOf<RemovedFeaturedCardPosition>()
            for (card in _featuredCards.value) {
                val tIdx = card.previewSongs.indexOfFirst { it.id == id }
                if (tIdx != -1) {
                    featuredPositions.add(RemovedFeaturedCardPosition(card.id, tIdx))
                }
            }

            val speedDialIdx = _speedDialItems.value.indexOfFirst { it.id == id }
            val playlistIdx = _ytPlaylistDetails.value?.indexOfFirst { it.id == id } ?: -1
            lastRemovedHistory = RemovedTrackHistory(
                track = removedTrack,
                positions = positions,
                featuredCardPositions = featuredPositions,
                speedDialIndex = speedDialIdx,
                playlistTrackIndex = playlistIdx
            )
            // Instant UI removal from active feeds, community cards, and playlist view
            _exploreYtMusicSections.value = _exploreYtMusicSections.value.map { section ->
                section.copy(tracks = section.tracks.filterNot { it.id == id })
            }.filter { it.tracks.isNotEmpty() }
            _homeYtMusicSections.value = _homeYtMusicSections.value.map { section ->
                section.copy(tracks = section.tracks.filterNot { it.id == id })
            }.filter { it.tracks.isNotEmpty() }
            _speedDialItems.value = _speedDialItems.value.filterNot { it.id == id }
            _featuredCards.value = _featuredCards.value.map { card ->
                card.copy(previewSongs = card.previewSongs.filterNot { it.id == id })
            }

            _ytPlaylistDetails.value = _ytPlaylistDetails.value?.filterNot { it.id == id }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun markSongNotInterested(track: YtMusicTrack) {
        markSongNotInterested(track.id, track.title, track.artist, track.thumbnailUrl ?: "", track.itemType)
    }

    fun undoNotInterested(songId: String) {
        val prefs = notInterestedPrefs ?: return
        try {
            val currentIds = prefs.getStringSet("not_interested_ids", emptySet()) ?: emptySet()
            val newIds = HashSet(currentIds).apply { remove(songId) }
            prefs.edit()
                .putStringSet("not_interested_ids", newIds)
                .remove("song_meta_$songId")
                .apply()
            _notInterestedSongIds.value = newIds
            _notInterestedSongs.value = _notInterestedSongs.value.filterNot { it.id == songId }
            // Restore back into active feeds at previous position
            val history = lastRemovedHistory
            if (history != null && history.track.id == songId) {
                val restoreTrack = history.track
                // Restore back into ALL sections where it was removed from
                for (pos in history.positions) {
                    if (!pos.isHome) {
                        val currentExplore = _exploreYtMusicSections.value.toMutableList()
                        var targetIdx = -1
                        if (pos.sectionIndex in currentExplore.indices &&
                            currentExplore[pos.sectionIndex].title.equals(pos.sectionTitle, ignoreCase = true)) {
                            targetIdx = pos.sectionIndex
                        } else {
                            targetIdx = currentExplore.indexOfFirst { it.title.equals(pos.sectionTitle, ignoreCase = true) }
                        }

                        if (targetIdx != -1) {
                            val targetSec = currentExplore[targetIdx]
                            val updatedTracks = targetSec.tracks.toMutableList()
                            val insertIdx = pos.trackIndex.coerceIn(0, updatedTracks.size)
                            updatedTracks.add(insertIdx, restoreTrack)
                            currentExplore[targetIdx] = targetSec.copy(tracks = updatedTracks)
                            _exploreYtMusicSections.value = currentExplore
                        }

                    } else {
                        val currentHome = _homeYtMusicSections.value.toMutableList()
                        var targetIdx = -1
                        if (pos.sectionIndex in currentHome.indices &&
                            currentHome[pos.sectionIndex].title.equals(pos.sectionTitle, ignoreCase = true)) {
                            targetIdx = pos.sectionIndex
                        } else {
                            targetIdx = currentHome.indexOfFirst { it.title.equals(pos.sectionTitle, ignoreCase = true) }
                        }

                        if (targetIdx != -1) {
                            val targetSec = currentHome[targetIdx]
                            val updatedTracks = targetSec.tracks.toMutableList()
                            val insertIdx = pos.trackIndex.coerceIn(0, updatedTracks.size)
                            updatedTracks.add(insertIdx, restoreTrack)
                            currentHome[targetIdx] = targetSec.copy(tracks = updatedTracks)
                            _homeYtMusicSections.value = currentHome
                        }
                    }
                }

                // Restore into Community Featured Playlist Cards
                for (fPos in history.featuredCardPositions) {
                    val currentCards = _featuredCards.value.toMutableList()
                    val cardIdx = currentCards.indexOfFirst { it.id == fPos.cardId }
                    if (cardIdx != -1) {
                        val targetCard = currentCards[cardIdx]
                        val updatedSongs = targetCard.previewSongs.toMutableList()
                        val insertIdx = fPos.trackIndex.coerceIn(0, updatedSongs.size)
                        updatedSongs.add(insertIdx, restoreTrack)
                        currentCards[cardIdx] = targetCard.copy(previewSongs = updatedSongs)
                        _featuredCards.value = currentCards
                    }
                }

                // Restore into Speed Dial
                if (history.speedDialIndex >= 0) {
                    val currentSd = _speedDialItems.value.toMutableList()
                    val insertIdx = history.speedDialIndex.coerceIn(0, currentSd.size)
                    currentSd.add(insertIdx, restoreTrack)
                    _speedDialItems.value = currentSd
                }

                // Restore into Playlist Details view
                if (history.playlistTrackIndex >= 0 && _ytPlaylistDetails.value != null) {
                    val currentPl = _ytPlaylistDetails.value!!.toMutableList()
                    val insertIdx = history.playlistTrackIndex.coerceIn(0, currentPl.size)
                    currentPl.add(insertIdx, restoreTrack)
                    _ytPlaylistDetails.value = currentPl
                }

                lastRemovedHistory = null
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun clearAllNotInterested() {
        try {
            notInterestedPrefs?.edit()?.clear()?.apply()
            _notInterestedSongIds.value = emptySet()
            _notInterestedSongs.value = emptyList()
            lastRemovedHistory = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun refreshExploreYtMusic(onComplete: () -> Unit = {}) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                loadArchiveToonData(chip = null)
                kotlinx.coroutines.delay(800)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onComplete()
                }
            }
        }
    }

    @Volatile private var isLocalRefreshing = false

    fun refreshLocalSongs(context: Context, onComplete: () -> Unit = {}) {
        if (isLocalRefreshing) {
            onComplete()
            return
        }
        isLocalRefreshing = true
        val targetContext = context.applicationContext
        appContext = targetContext
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // মিডিয়াস্টোরের ইনডেক্সিং নিশ্চিত করতে ছোট বিলম্ব
                kotlinx.coroutines.delay(350)
                val db = com.musicplayer.lytrix.db.AppDatabase.getDatabase(targetContext)
                val whitelistEntities = db.whitelistSongDao().getAllWhitelistSongs()
                val whitelistIds = whitelistEntities.map { it.songId }
                val scanner = SongScanner(targetContext)
                val scanned = scanner.scanSongs(whitelistIds)
                val prefs = targetContext.getSharedPreferences("custom_album_arts", Context.MODE_PRIVATE)
                val updatedSongs = scanned.map { song ->
                    val customArt = try {
                        prefs.getString(song.id.toString(), null)
                    } catch (e: Exception) {
                        null
                    }

                    if (customArt != null) {
                        song.copy(albumArtUriString = customArt)
                    } else {
                        song
                    }
                }

                _songsList.value = updatedSongs
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    initMediaController(targetContext)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLocalRefreshing = false
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onComplete()
                }
            }
        }
    }

    init {
        activeInstance = this
        loadSpeedDial()
        loadAccountPlaylists()
        loadNotInterestedSongs()
    }

    // 🔥 visitorData ইনিশিয়ালাইজ করার ফাংশন।
    // লগিন থাকলে (cookie সেট আছে) authState-কে ছোঁয়া হয় না।
    // লগিন না থাকলে anonymous visitorData ফেচ করে সেট করা হয়।
    private var visitorDataReady = false

    suspend fun initVisitorData(context: Context) {
        if (visitorDataReady) return
        // যদি ইউজার আগে থেকেই লগিন করা থাকে তাহলে authState-এ cookie আছে।
        // সেক্ষেত্রে নতুন anonymous visitorData আনলে লগিন সেশন নষ্ট হয়ে যাবে।
        // তাই লগিন থাকলে এই ফাংশন কিছুই করবে না।
        val existingCookie = com.musicplayer.lytrix.innertube.YouTube.authState.cookie
        if (!existingCookie.isNullOrBlank() && existingCookie.contains("SAPISID")) {
            visitorDataReady = true
            return
        }

        val prefs = context.getSharedPreferences("innertube_session", Context.MODE_PRIVATE)
        var cached = prefs.getString("visitor_data", null)?.takeIf { it.isNotBlank() && it != "null" }
        if (cached == null) {
            cached = withContext(Dispatchers.IO) {
                com.musicplayer.lytrix.innertube.YouTube.visitorData().getOrNull()
            }

            if (cached != null) {
                prefs.edit().putString("visitor_data", cached).apply()
            }
        }

        // শুধু visitorData আপডেট করা হচ্ছে, বাকি authState অপরিবর্তিত থাকছে
        if (!cached.isNullOrBlank()) {
            com.musicplayer.lytrix.innertube.YouTube.authState =
                com.musicplayer.lytrix.innertube.YouTube.authState.copy(visitorData = cached)
        }

        visitorDataReady = true
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    private val _hiddenSongIds = MutableStateFlow<List<Long>>(emptyList())
    val hiddenSongIds: StateFlow<List<Long>> = _hiddenSongIds.asStateFlow()
    private var lastCountedSongId: Long? = null
    private var lastOnlineHistoryRowId: Long? = null
    private var lastOnlineHistorySongId: String? = null

    enum class SortOption { NEWEST, MOST_PLAYED, A_TO_Z, ARTIST, LONGEST_DURATION }
    private val _sortOption = MutableStateFlow(SortOption.NEWEST)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    fun setSortOption(option: SortOption) {
        _sortOption.value = option
        viewModelScope.launch {
            delay(100)
            val currentList = filteredSongs.value
            val controller = mediaController ?: return@launch
            val currentMediaId = controller.currentMediaItem?.mediaId ?: return@launch
            val isPlaying = controller.isPlaying
            val pos = controller.currentPosition
            val mediaItems = currentList.map { s ->
                androidx.media3.common.MediaItem.Builder()
                    .setMediaId(s.id.toString())
                    .setUri(s.uriString)
                    .setMediaMetadata(buildQueueMediaMetadata(s))
                    .build()
            }

            val newIndex = currentList.indexOfFirst { it.id.toString() == currentMediaId }
            if (newIndex >= 0) {
                controller.setMediaItems(mediaItems, newIndex, pos)
                controller.prepare()
                if (isPlaying) controller.play() else controller.pause()
            } else {
                controller.setMediaItems(mediaItems)
                controller.prepare()
            }
        }
    }

    private val _mostPlayedSongIdsForSort = MutableStateFlow<List<Long>>(emptyList())
    val filteredSongs: StateFlow<List<Song>> = combine(
        _songsList,
        _searchQuery,
        _sortOption,
        _mostPlayedSongIdsForSort,
        _hiddenSongIds
    ) { songs, query, sortOpt, mostPlayed, hiddenIds ->
        val visibleSongs = songs.filter { !hiddenIds.contains(it.id) }
        val filtered = if (query.isBlank()) {
            visibleSongs
        } else {
            visibleSongs.filter { song ->
                song.title.contains(query, ignoreCase = true) ||
                        song.artist.contains(query, ignoreCase = true) ||
                        song.album.contains(query, ignoreCase = true)
            }
        }

        when (sortOpt) {
            SortOption.NEWEST -> filtered.sortedByDescending { it.id }
            SortOption.MOST_PLAYED -> filtered.sortedByDescending {
                if (mostPlayed.contains(it.id)) (mostPlayed.size - mostPlayed.indexOf(
                    it.id
                )) else -1
            }

            SortOption.A_TO_Z -> filtered.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title.trim() })
            SortOption.ARTIST -> filtered.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.artist })
            SortOption.LONGEST_DURATION -> filtered.sortedByDescending { it.duration }
        }

    }.flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val groupedAlbums: StateFlow<List<Pair<String, List<Song>>>> =
        combine(_songsList, _hiddenSongIds, _searchQuery) { songs, hiddenIds, query ->
            songs.filter { !hiddenIds.contains(it.id) }
                .groupBy { it.album.takeIf { a -> a.isNotBlank() } ?: "Unknown Album" }
                .entries.filter {
                    query.isBlank() ||
                            it.key.contains(query, ignoreCase = true) ||
                            it.value.any { s -> s.title.contains(query, ignoreCase = true) }
                }
                .map { it.key to it.value }
                .sortedBy { it.first }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val groupedArtists: StateFlow<List<Pair<String, List<Song>>>> =
        combine(_songsList, _hiddenSongIds, _searchQuery) { songs, hiddenIds, query ->
            songs.filter { !hiddenIds.contains(it.id) }
                .groupBy { it.artist.takeIf { a -> a.isNotBlank() } ?: "Unknown Artist" }
                .entries.filter {
                    query.isBlank() ||
                            it.key.contains(query, ignoreCase = true) ||
                            it.value.any { s -> s.title.contains(query, ignoreCase = true) }
                }
                .map { it.key to it.value }
                .sortedBy { it.first }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val groupedFolders: StateFlow<List<Pair<String, List<Song>>>> =
        combine(_songsList, _hiddenSongIds, _searchQuery) { songs, hiddenIds, query ->
            songs.filter { !hiddenIds.contains(it.id) }
                .groupBy { song ->
                    val path = song.filePath ?: ""
                    if (path.isBlank() || path == "ONLINE" || path == "ONLINE_PODCAST") {
                        "Unknown Folder"
                    } else {
                        java.io.File(path).parentFile?.name ?: "Unknown Folder"
                    }
                }
                .entries.filter {
                    query.isBlank() ||
                            it.key.contains(query, ignoreCase = true) ||
                            it.value.any { s -> s.title.contains(query, ignoreCase = true) }
                }
                .map { it.key to it.value }
                .sortedBy { it.first }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()
    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()
    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()
    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()
    private val _shuffleModeEnabled = MutableStateFlow(false)
    val shuffleModeEnabled: StateFlow<Boolean> = _shuffleModeEnabled.asStateFlow()
    private val _isServiceConnected = MutableStateFlow(false)
    val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()
    private val _audioQualityLabel = MutableStateFlow<String?>(null)
    val audioQualityLabel: StateFlow<String?> = _audioQualityLabel.asStateFlow()
    private val _currentLyrics = MutableStateFlow<String?>(null)
    val currentLyrics: StateFlow<String?> = _currentLyrics.asStateFlow()
    // লিরিক্স প্রোভাইডারের ইনডেক্স ধরে রাখার জন্য
    private val _currentLyricsProviderIndex = MutableStateFlow(-1)
    val currentLyricsProviderIndex: StateFlow<Int> = _currentLyricsProviderIndex.asStateFlow()
    private val _currentLyricsProviderName = MutableStateFlow<String?>(null)
    val currentLyricsProviderName: StateFlow<String?> = _currentLyricsProviderName.asStateFlow()
    private val _favoriteSongIds = MutableStateFlow<List<Long>>(emptyList())
    val favoriteSongIds: StateFlow<List<Long>> = _favoriteSongIds.asStateFlow()
    private val _onlineLikedIds = MutableStateFlow<Set<String>>(emptySet())
    val onlineLikedIds: StateFlow<Set<String>> = _onlineLikedIds.asStateFlow()
    private var onlineLikedPrefsListener: android.content.SharedPreferences.OnSharedPreferenceChangeListener? = null
    private val _paletteAccentColor = MutableStateFlow(Color(0xFF80DEEA))
    val paletteAccentColor: StateFlow<Color> = _paletteAccentColor.asStateFlow()
    private val _playerTheme = MutableStateFlow("Solid Default")
    val playerTheme: StateFlow<String> = _playerTheme.asStateFlow()
    private var mediaController: MediaController? = null
    private var controllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? =
        null
    private var progressJob: Job? = null
    private var isScanning = false
    private var isDbInitialized = false
    private var appContext: Context? = null
    // 🔥 মিউজিক ভিডিও (VidMate / SnapTube) নোটিফিকেশন আর্ট নিশ্চিত করার ফাংশন

    private fun ensureVideoArtwork(song: Song): android.net.Uri? {
        if (!song.isVideo) return null
        val context = appContext ?: return null
        val safeName = song.title.replace(Regex("[^a-zA-Z0-9 ]"), "_").take(50).trim()
        val thumbFile = java.io.File(context.cacheDir, "vthumb_${song.id}_$safeName.jpg")
        if (thumbFile.exists() && thumbFile.length() > 0) {
            return toShareableFileUri(context, thumbFile)
        }

        val libCache = java.io.File(context.cacheDir, "lytrix_lib_${song.uriString.hashCode()}.jpg")
        if (libCache.exists() && libCache.length() > 0) {
            return toShareableFileUri(context, libCache)
        }

        val vidCache = java.io.File(context.cacheDir, "lytrix_vid_${song.uriString.hashCode()}.jpg")
        if (vidCache.exists() && vidCache.length() > 0) {
            return toShareableFileUri(context, vidCache)
        }

        val retriever = android.media.MediaMetadataRetriever()
        try {
            if (song.filePath != null && java.io.File(song.filePath).exists()) {
                retriever.setDataSource(song.filePath)
            } else {
                retriever.setDataSource(context, android.net.Uri.parse(song.uriString))
            }

            // VidMate/SnapTube আসল ইউটিউব কভার ছবি
            val pic = retriever.embeddedPicture
            val bmp = if (pic != null) {
                android.graphics.BitmapFactory.decodeByteArray(pic, 0, pic.size)
            } else {
                retriever.getFrameAtTime(1000000)
            }

            if (bmp != null) {
                val maxDim = maxOf(bmp.width, bmp.height)
                val scaledBmp = if (maxDim > 512) {
                    val ratio = 512f / maxDim.toFloat()
                    android.graphics.Bitmap.createScaledBitmap(
                        bmp,
                        (bmp.width * ratio).toInt().coerceAtLeast(1),
                        (bmp.height * ratio).toInt().coerceAtLeast(1),
                        true
                    )
                } else {
                    bmp
                }
                java.io.FileOutputStream(thumbFile).use { out ->
                    scaledBmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
                }
                if (scaledBmp !== bmp) {
                    scaledBmp.recycle()
                }
                bmp.recycle()

                return toShareableFileUri(context, thumbFile)
            }

        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            runCatching { retriever.release() }
        }

        return null
    }

    // 🔥 অফলাইন গান, কাস্টম কভার আর্ট এবং VidMate/SnapTube গানের নোটিফিকেশন আর্ট

    private fun resolveExistingLocalArtworkFile(context: Context, song: Song): java.io.File? {
        val customArt = song.albumArtUriString
        if (customArt?.startsWith("file://") == true) {
            val f = android.net.Uri.parse(customArt).path?.let { java.io.File(it) }
            if (f != null && f.exists() && f.length() > 0L) return f
        }
        if (song.filePath == "OFFLINE") {
            val safeName = song.title.replace(Regex("[^a-zA-Z0-9 ]"), "_").take(50).trim()
            val coverFile = java.io.File(
                context.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC),
                "${safeName}_cover.jpg"
            )
            if (coverFile.exists() && coverFile.length() > 0L) return coverFile
        }
        val hash = song.uriString.hashCode()
        if (song.isVideo) {
            val safeName = song.title.replace(Regex("[^a-zA-Z0-9 ]"), "_").take(50).trim()
            val vthumb = java.io.File(context.cacheDir, "vthumb_${song.id}_$safeName.jpg")
            if (vthumb.exists() && vthumb.length() > 0L) return vthumb
            val libCache = java.io.File(context.cacheDir, "lytrix_lib_$hash.jpg")
            if (libCache.exists() && libCache.length() > 0L) return libCache
            val vidCache = java.io.File(context.cacheDir, "lytrix_vid_$hash.jpg")
            if (vidCache.exists() && vidCache.length() > 0L) return vidCache
            return null
        }
        val libCache = java.io.File(context.cacheDir, "lytrix_lib_$hash.jpg")
        if (libCache.exists() && libCache.length() > 0L) return libCache
        return null
    }

    private fun toShareableFileUri(context: Context, file: java.io.File): android.net.Uri? {
        if (!file.exists() || file.length() <= 0L) return null
        val contentUri = runCatching {
            androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.FileProvider",
                file
            )
        }.getOrNull()
        if (contentUri != null) {
            runCatching {
                context.grantUriPermission(
                    "com.android.systemui",
                    contentUri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            return contentUri
        }
        return null
    }

    private fun readBoundedArtworkBytes(file: java.io.File): ByteArray? = runCatching {
        if (!file.exists() || file.length() <= 0L) return@runCatching null
        if (file.length() <= 64 * 1024L) {
            file.readBytes()
        } else {
            val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(file.absolutePath, opts)
            var sample = 1
            while (opts.outWidth / sample > 320 || opts.outHeight / sample > 320) {
                sample *= 2
            }
            val decodeOpts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = android.graphics.BitmapFactory.decodeFile(file.absolutePath, decodeOpts) ?: return@runCatching null
            val out = java.io.ByteArrayOutputStream()
            bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
            bmp.recycle()
            out.toByteArray()
        }
    }.getOrNull()

    private fun getSafeArtworkUri(song: Song): android.net.Uri? {
        val context = appContext ?: return song.albumArtUriString?.takeUnless { it.startsWith("file://") }?.let { android.net.Uri.parse(it) }
        val localFile = resolveExistingLocalArtworkFile(context, song)
        if (localFile != null) {
            toShareableFileUri(context, localFile)?.let { return it }
        }
        val raw = song.albumArtUriString ?: return null
        if (raw.startsWith("file://")) {
            return null
        }
        return android.net.Uri.parse(raw)
    }

    /** Lightweight metadata for queue lists so large playlists do not decode or retain artwork bytes in MediaItem timelines. */
    private fun buildQueueMediaMetadata(song: Song): androidx.media3.common.MediaMetadata {
        val builder = androidx.media3.common.MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setAlbumTitle(song.album)
        val raw = song.albumArtUriString
        if (!raw.isNullOrBlank() && !raw.startsWith("file://")) {
            builder.setArtworkUri(android.net.Uri.parse(raw))
        }
        return builder.build()
    }

    private fun buildSafeMediaMetadata(
        song: Song,
        includeArtworkData: Boolean = false
    ): androidx.media3.common.MediaMetadata {
        val context = appContext
        val builder = androidx.media3.common.MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setAlbumTitle(song.album)
        val safeUri = getSafeArtworkUri(song)
        if (safeUri != null) {
            builder.setArtworkUri(safeUri)
        }
        if (includeArtworkData && context != null) {
            val localFile = resolveExistingLocalArtworkFile(context, song)
            val artBytes = localFile?.let { readBoundedArtworkBytes(it) }
            if (artBytes != null && artBytes.isNotEmpty()) {
                builder.setArtworkData(artBytes, androidx.media3.common.MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            }
        }
        return builder.build()
    }

    private val _pointA = MutableStateFlow<Long?>(null)
    val pointA: StateFlow<Long?> = _pointA.asStateFlow()
    private val _pointB = MutableStateFlow<Long?>(null)
    val pointB: StateFlow<Long?> = _pointB.asStateFlow()
    private var abLoopJob: Job? = null

    private fun is1to1VideoActiveNow(): Boolean {
        val current = _currentSong.value ?: return false
        return _isVideoModeActive.value && !isSong16by9(current)
    }

    private fun livePosition(): Long =
        if (is1to1VideoActiveNow()) {
            videoPlayer?.currentPosition ?: _currentPosition.value
        } else {
            mediaController?.currentPosition ?: _currentPosition.value
        }

    fun setPointA() {
        abLoopJob?.cancel()
        abLoopJob = null
        _pointB.value = null
        _pointA.value = livePosition().coerceAtLeast(0L)
    }

    fun setPointB() {
        val a = _pointA.value ?: return
        val current = livePosition().coerceAtLeast(0L)
        if (current > a + 500) {
            _pointB.value = current
            startABLoop()
        }
    }

    fun clearAB() {
        abLoopJob?.cancel()
        abLoopJob = null
        _pointA.value = null
        _pointB.value = null
    }

    private fun remapABForModeSwitch(
        song: Song,
        toVideo: Boolean,
        audioDurationMs: Long?,
        videoDurationMs: Long?,
    ) {
        val a = _pointA.value
        val b = _pointB.value
        if (a == null && b == null) return
        if (isSong16by9(song)) return // Same timeline

        val key = song.id.toString()
        val cp = counterpartForSongId(key)
        val hasPositionMap = cp?.positionMap != null
        val largeMismatch = !hasPositionMap &&
            audioDurationMs != null && videoDurationMs != null &&
            audioDurationMs > 0L && videoDurationMs > 0L &&
            kotlin.math.abs(audioDurationMs - videoDurationMs) > 40_000L

        if (largeMismatch) {
            clearAB()
            appContext?.let {
                Toast.makeText(it, "A-B Repeat cleared (different video length)", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val targetDur = if (toVideo) videoDurationMs else audioDurationMs
        val newA = a?.let { pos ->
            if (toVideo) mapAudioToVideoPosition(key, pos, videoDurationMs)
            else mapVideoToAudioPosition(key, pos, videoDurationMs)
        }?.let { mapped ->
            if (targetDur != null && targetDur > 0L) mapped.coerceIn(0L, (targetDur - 1L).coerceAtLeast(0L)) else mapped
        }
        val newB = b?.let { pos ->
            if (toVideo) mapAudioToVideoPosition(key, pos, videoDurationMs)
            else mapVideoToAudioPosition(key, pos, videoDurationMs)
        }?.let { mapped ->
            if (targetDur != null && targetDur > 0L) mapped.coerceIn(0L, (targetDur - 1L).coerceAtLeast(0L)) else mapped
        }

        if (newA != null && newB != null && newB <= newA + 500L) {
            clearAB()
            return
        }
        _pointA.value = newA
        _pointB.value = newB
        if (newA != null && newB != null) {
            startABLoop()
        }
    }

    private fun startABLoop() {
        abLoopJob?.cancel()
        abLoopJob = viewModelScope.launch {
            while (isActive) {
                val a = _pointA.value
                val b = _pointB.value
                if (a == null || b == null) break
                if (is1to1VideoActiveNow()) {
                    val vp = videoPlayer
                    if (vp != null && vp.currentPosition >= b) {
                        vp.seekTo(a)
                    }
                } else {
                    val controller = mediaController
                    if (controller != null && controller.currentPosition >= b) {
                        controller.seekTo(a)
                        val cur = _currentSong.value
                        if (_isVideoModeActive.value && cur != null && isSong16by9(cur)) {
                            videoPlayer?.seekTo(a)
                        }
                    }
                }

                delay(150)
            }
        }
    }

    // 🔥 ডেটা ডিলিট না করে ভেতর থেকে রিপেয়ার করবে এবং ফায়ারবেস সিঙ্ক করবে

    fun repairAndSyncData(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            var isModified = false
            // ১. Liked Songs রিপেয়ার
            val likedPrefs = context.getSharedPreferences("online_liked_songs", Context.MODE_PRIVATE)
            val likedIds = likedPrefs.getStringSet("liked_ids", emptySet())?.toSet() ?: emptySet()
            val likedEditor = likedPrefs.edit()
            for (key in likedIds) {
                val json = likedPrefs.getString("song_data_$key", null)
                if (json != null) {
                    try {
                        val obj = org.json.JSONObject(json)
                        val internalId = obj.optString("id", "")
                        // 🔥 যদি ভেতরের আইডি আর বাইরের আসল আইডি না মেলে, তবেই ফিক্স করবে
                        if (internalId != key || !obj.has("originalId")) {
                            obj.put("id", key)
                            obj.put("originalId", key)
                            likedEditor.putString("song_data_$key", obj.toString())
                            isModified = true
                        }

                    } catch (e: Exception) {}
                }
            }

            if (isModified) likedEditor.apply()
            // ২. Downloaded Songs রিপেয়ার
            val downloadPrefs = context.getSharedPreferences("downloaded_meta", Context.MODE_PRIVATE)
            val allDownloads = downloadPrefs.all
            var isDownloadModified = false
            val downloadEditor = downloadPrefs.edit()
            for ((mapKey, value) in allDownloads) {
                if (value is String && mapKey.startsWith("song_")) {
                    val realKey = mapKey.removePrefix("song_")
                    try {
                        val obj = org.json.JSONObject(value)
                        val internalId = obj.optString("id", "")
                        if (internalId != realKey || !obj.has("originalId")) {
                            obj.put("id", realKey)
                            obj.put("originalId", realKey)
                            downloadEditor.putString(mapKey, obj.toString())
                            isDownloadModified = true
                        }

                    } catch (e: Exception) {}
                }
            }

            if (isDownloadModified) downloadEditor.apply()
            // 🔥 ৩. ফায়ারবেস অটো-আপডেট: রিপেয়ার হলে সাথে সাথে ক্লাউডে আসল ডেটা পাঠিয়ে দেবে
            if (isModified || isDownloadModified) {
                try {
                    performSilentSync(context)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun loadAndScanSongs(context: Context) {
        if (isScanning) return
        isScanning = true
        _isLoading.value = true
        appContext = context.applicationContext
        initOnlineLikedObserver(context)
        repairAndSyncData(context)
        loadVideoQualityPref(context)
        loadSpeedDial()
        loadOnlineQueue(context)
        // শুধুমাত্র ১ম বার ডাটাবেস লিসেনারগুলো চালু করবে, বারবার ডুপ্লিকেট করবে না
        if (!isDbInitialized) {
            isDbInitialized = true
            viewModelScope.launch(Dispatchers.IO) {
                _currentQueueList.collect { list ->
                    if (list.isNotEmpty()) {
                        saveOnlineQueue(context, list)
                    }
                }
            }

            viewModelScope.launch(Dispatchers.IO) {
                val db = com.musicplayer.lytrix.db.AppDatabase.getDatabase(context)
                launch {
                    db.songMetadataDao().getAllFavoriteSongIds().collect { _favoriteSongIds.value = it }
                }

                launch {
                    db.songMetadataDao().getRecentlyPlayedSongIds()
                        .collect { _recentSongIds.value = it }
                }

                launch {
                    db.songMetadataDao().getMostPlayedSongIds()
                        .collect { _mostPlayedSongIds.value = it; _mostPlayedSongIdsForSort.value = it }
                }

                launch {
                    db.songMetadataDao().getAllPlayCounts().collect { entities ->
                        _playCounts.value = entities.associate { it.songId to it.playCount }
                    }
                }

                launch { db.playlistDao().getAllPlaylists().collect { _playlists.value = it } }
                launch {
                    db.hiddenSongDao().getAllHiddenSongs()
                        .collect { list -> _hiddenSongIds.value = list.map { it.songId } }
                }

                launch {
                    com.musicplayer.lytrix.db.ThemePreferencesManager(context).playerTheme.collect {
                        _playerTheme.value = it
                    }
                }

                launch {
                    db.onlineHistoryDao().getAllOnlineHistory().collect { list ->
                        _onlineHistory.value = list
                    }
                }
            }
        }

        // পুরো স্ক্যানিং এবং প্রসেসিং ব্যাকগ্রাউন্ড Dispatchers.IO-তে চলবে
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val db = com.musicplayer.lytrix.db.AppDatabase.getDatabase(context)
                val whitelistEntities = db.whitelistSongDao().getAllWhitelistSongs()
                val whitelistIds = whitelistEntities.map { it.songId }
                val scanner = SongScanner(context)
                val scanned = scanner.scanSongs(whitelistIds)
                val prefs = context.getSharedPreferences("custom_album_arts", Context.MODE_PRIVATE)
                val updatedSongs = scanned.map { song ->
                    val customArt = try {
                        prefs.getString(song.id.toString(), null)
                    } catch (e: Exception) {
                        null
                    }

                    if (customArt != null) {
                        song.copy(albumArtUriString = customArt)
                    } else {
                        song
                    }
                }

                _songsList.value = updatedSongs
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    initMediaController(context)
                }

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isScanning = false
                _isLoading.value = false
            }
        }
    }

    private fun checkAndIncrementPlayCount(controller: MediaController) {
        val song = _currentSong.value ?: return
        if (song.filePath == "ONLINE_PODCAST") return

        val isOnlineTrack = song.filePath == "ONLINE" ||
            song.filePath == "ONLINE_LYTRIX" ||
            !song.originalId.isNullOrBlank() ||
            song.uriString.startsWith("http")

        val resolvedDurationMs = when {
            song.duration > 0L -> song.duration
            controller.duration > 0L && controller.duration != androidx.media3.common.C.TIME_UNSET -> controller.duration
            else -> 0L
        }

        if (isOnlineTrack) {
            val context = appContext ?: return
            if (com.musicplayer.lytrix.ui.PrivacyPreferencesManager.isPauseListenHistory(context)) return
            if (song.mediaType in listOf("PODCAST", "AUDIOBOOK") ||
                song.filePath in listOf("ONLINE_PODCAST", "OFFLINE_PODCAST", "ONLINE_AUDIOBOOK", "OFFLINE_AUDIOBOOK") ||
                currentMediaCategory.value in listOf("PODCAST", "AUDIOBOOK")) {
                return // Shows must NEVER pollute YouTube Music history or online history!
            }
            if (controller.isPlaying && lastCountedSongId != song.id) {
                lastCountedSongId = song.id
                val isYt = song.filePath == "ONLINE_LYTRIX" ||
                    song.uriString.contains("youtube.com", ignoreCase = true) ||
                    song.uriString.contains("youtu.be", ignoreCase = true) ||
                    !song.albumArtUriString.orEmpty().contains("saavn", ignoreCase = true)
                trackOnlineHistory(
                    songId = song.originalId?.takeIf { it.isNotBlank() } ?: song.id.toString(),
                    title = song.title,
                    artist = song.artist,
                    imageUrl = song.albumArtUriString.orEmpty(),
                    source = if (isYt) "YOUTUBE" else "JIOSAAVN",
                    duration = resolvedDurationMs,
                    uriString = song.uriString
                )
            }
            return
        }

        val context = appContext ?: return
        if (com.musicplayer.lytrix.ui.PrivacyPreferencesManager.isPauseLocalListenHistory(context)) return
        if (controller.isPlaying && lastCountedSongId != song.id) {
            lastCountedSongId = song.id
            viewModelScope.launch(Dispatchers.IO) {
                val db = com.musicplayer.lytrix.db.AppDatabase.getDatabase(context)
                val metadataDao = db.songMetadataDao()
                val existing = metadataDao.getMetadata(song.id)
                if (existing != null) {
                    metadataDao.incrementPlayCount(song.id, System.currentTimeMillis())
                } else {
                    metadataDao.insertOrUpdate(
                        com.musicplayer.lytrix.db.SongMetadataEntity(
                            songId = song.id,
                            playCount = 1,
                            lastPlayedTimestamp = System.currentTimeMillis()
                        )
                    )
                }

                db.audioHistoryDao().insertAudioHistory(
                    com.musicplayer.lytrix.db.AudioHistoryEntity(
                        songId = song.id,
                        title = song.title,
                        artist = song.artist,
                        timestamp = System.currentTimeMillis(),
                        durationMs = resolvedDurationMs
                    )
                )
            }
        }
    }

    private fun backfillCurrentOnlineDuration(durationMs: Long) {
        if (durationMs <= 0L) return
        val context = appContext ?: return
        val song = _currentSong.value ?: return
        val isOnlineTrack = song.filePath == "ONLINE" ||
            song.filePath == "ONLINE_LYTRIX" ||
            !song.originalId.isNullOrBlank() ||
            song.uriString.startsWith("http")
        if (!isOnlineTrack) return
        val resolvedId = song.originalId?.takeIf { it.isNotBlank() } ?: song.id.toString()
        val rowId = lastOnlineHistoryRowId
        val rowSongId = lastOnlineHistorySongId
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dao = com.musicplayer.lytrix.db.AppDatabase.getDatabase(context).onlineHistoryDao()
                if (rowId != null && rowSongId == resolvedId) {
                    dao.updateDurationById(rowId, durationMs)
                }
                dao.backfillMissingDuration(resolvedId, durationMs)
            } catch (_: Exception) {}
        }
    }

    private fun initMediaController(context: Context) {
        val applicationContext = context.applicationContext
        initOnlineLikedObserver(applicationContext)
        if (mediaController != null || controllerFuture != null) return
        val sessionToken = SessionToken(
            applicationContext,
            ComponentName(applicationContext, PlaybackService::class.java)
        )
        controllerFuture = MediaController.Builder(applicationContext, sessionToken).buildAsync()
        val future = controllerFuture ?: return
        future.addListener({
            try {
                val controller = future.get()
                mediaController = controller
                _isServiceConnected.value = true
                _isPlaying.value = controller.isPlaying
                _repeatMode.value = controller.repeatMode
                _shuffleModeEnabled.value = controller.shuffleModeEnabled
                _duration.value = controller.duration.coerceAtLeast(0)
                val activeParams = controller.playbackParameters
                currentPlaybackSpeed.value = kotlin.math.round(activeParams.speed.coerceIn(0.25f, 2.0f) * 100f) / 100f
                currentPlaybackPitchSemitones.value = kotlin.math.round(12.0 * kotlin.math.log2(activeParams.pitch.toDouble())).toInt().coerceIn(-12, 12)
                if (controller.isPlaying) {
                    startProgressPoll()
                }

                controller.addListener(object : Player.Listener {

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        if (_isVideoModeActive.value) {
                            val current = _currentSong.value
                            val is16x9 = current != null && isSong16by9(current)
                            if (!is16x9) {
                                // শুধুমাত্র ১:১ ভিডিও মোডে যেখানে ভিডিও প্লেয়ার সাউন্ড প্লে করছে
                                if (isPlaying) {
                                    controller.pause()
                                    setVideoPlayIntent(true)
                                }
                                return
                            }
                        }
                        _isPlaying.value = isPlaying
                        if (isPlaying) {
                            startProgressPoll()
                            checkAndIncrementPlayCount(controller)
                        } else {
                            stopProgressPoll()
                        }
                        if (!isRemoteApplyActive() && metroManager.roomRole.value == MetroRoomRole.HOST) {
                            forceSyncToMetro(if (isPlaying) PlaybackActions.PLAY else PlaybackActions.PAUSE)
                        }
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && metroSleepTimer.isStopAfterCurrentSong) {
                            metroSleepTimer.onEndOfMediaItem()
                        }
                        lastCountedSongId = null
                        _isVideoModeActive.value = false
                        _isVideoHandoffPending.value = false
                        cancelVideoHandoff()
                        releaseVideoPlayer()
                        syncCurrentMediaItem(controller)
                        if (!isRemoteApplyActive()) {
                            forceSyncToMetro(PlaybackActions.CHANGE_TRACK)
                        }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED && metroSleepTimer.isStopAfterCurrentSong) {
                            metroSleepTimer.onEndOfMediaItem()
                        }
                        val current = _currentSong.value
                        val is16x9 = current != null && isSong16by9(current)
                        if (_isVideoModeActive.value && !is16x9) return
                        _isBuffering.value = playbackState == Player.STATE_BUFFERING
                        val realDur = controller.duration.takeIf { it != androidx.media3.common.C.TIME_UNSET && it > 0L } ?: 0L
                        _duration.value = realDur

                        if (playbackState == Player.STATE_READY) {
                            updateAudioQualityFromController(controller)
                            if (realDur > 0L) {
                                backfillCurrentOnlineDuration(realDur)
                            }
                            if (metroManager.roomRole.value == MetroRoomRole.GUEST) {
                                val roomState = metroManager.roomState.value
                                val livePos = if (roomState != null && roomState.position > 0) {
                                    metroManager.client.metroClock.positionAt(roomState.position, roomState.lastUpdate, roomState.isPlaying)
                                } else {
                                    pendingMetroSeekPosition ?: 0L
                                }
                                pendingMetroSeekPosition = null

                                if (livePos > 0 && Math.abs(controller.currentPosition - livePos) > 300L) {
                                    markRemoteApply(2500L)
                                    controller.seekTo(livePos)
                                }

                                if (metroManager.bufferingTrackId != null) {
                                    val currentTrackId = (_currentSong.value?.originalId ?: _currentSong.value?.id.toString())
                                    val rev = roomState?.revision ?: 0L
                                    metroManager.reportBufferReady(currentTrackId, rev)
                                }
                            } else {
                                pendingMetroSeekPosition?.let { seekPos ->
                                    pendingMetroSeekPosition = null
                                    if (Math.abs(controller.currentPosition - seekPos) > 300L) {
                                        markRemoteApply(2500L)
                                        controller.seekTo(seekPos)
                                    }
                                }
                            }
                        }

                        if (!isRemoteApplyActive()) {
                            if (metroManager.roomRole.value == MetroRoomRole.HOST) {
                                if (playbackState == Player.STATE_READY && controller.playWhenReady) {
                                    forceSyncToMetro(PlaybackActions.PLAY)
                                }
                            }
                        }
                    }

                    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                        if (!isRemoteApplyActive() && reason == Player.DISCONTINUITY_REASON_SEEK) {
                            forceSyncToMetro(PlaybackActions.SEEK)
                        }
                    }

                    override fun onRepeatModeChanged(repeatMode: Int) {
                        _repeatMode.value = repeatMode
                    }

                    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                        _shuffleModeEnabled.value = shuffleModeEnabled
                    }

                    override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                        updateAudioQualityFromController(controller)
                    }

                    override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
                        currentPlaybackSpeed.value = kotlin.math.round(playbackParameters.speed.coerceIn(0.25f, 2.0f) * 100f) / 100f
                        currentPlaybackPitchSemitones.value = kotlin.math.round(
                            12.0 * kotlin.math.log2(playbackParameters.pitch.toDouble())
                        ).toInt().coerceIn(-12, 12)
                    }

                })
                if (controller.mediaItemCount == 0) {
                    if (_currentQueueList.value.isNotEmpty()) {
                        setPlaylist(_currentQueueList.value)
                    } else if (_songsList.value.isNotEmpty()) {
                        setPlaylist(_songsList.value)
                    }

                } else {
                    syncCurrentMediaItem(controller)
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }

        }, androidx.core.content.ContextCompat.getMainExecutor(applicationContext))
    }

    private fun setPlaylist(songs: List<Song>) {
        _currentQueueList.value = songs
        val controller = mediaController ?: return
        val mediaItems = songs.map { song ->
            androidx.media3.common.MediaItem.Builder()
                .setMediaId(song.id.toString())
                .setUri(song.uriString)
                .setMediaMetadata(buildQueueMediaMetadata(song))
                .build()
        }

        val context = appContext
        var startIndex = 0
        var startPositionMs = 0L
        if (context != null) {
            val prefs = context.getSharedPreferences("playback_prefs", Context.MODE_PRIVATE)
            // 🔥 CRASH FIX: Safe Try-Catch
            val lastId = try {
                prefs.getString("last_song_id", null)
            } catch (e: Exception) {
                null
            }

            val lastPosition = try {
                prefs.getLong("last_song_position", 0L)
            } catch (e: Exception) {
                0L
            }

            if (lastId != null) {
                val index = mediaItems.indexOfFirst { it.mediaId == lastId }
                if (index != -1) {
                    startIndex = index
                    startPositionMs = 0L
                }
            }
        }

        controller.setMediaItems(mediaItems, startIndex, startPositionMs)
        controller.prepare()
        syncCurrentMediaItem(controller)
    }

    fun updateAudioQualityFromController(controller: MediaController? = mediaController) {
        val ctrl = controller ?: return
        try {
            val tracks = ctrl.currentTracks
            var detectedSampleRate: String? = null
            var detectedBitrateKbps: Int? = null

            for (group in tracks.groups) {
                if (group.type == androidx.media3.common.C.TRACK_TYPE_AUDIO && group.isSelected) {
                    for (i in 0 until group.length) {
                        if (group.isTrackSelected(i)) {
                            val format = group.getTrackFormat(i)
                            val bitrateVal = if (format.bitrate > 0) format.bitrate else format.averageBitrate
                            if (bitrateVal > 0) {
                                detectedBitrateKbps = Math.round(bitrateVal / 1000.0).toInt()
                            }
                            if (format.sampleRate > 0) {
                                val tenths = (format.sampleRate + 50) / 100
                                detectedSampleRate = if (tenths % 10 == 0) "${tenths / 10} kHz" else "${tenths / 10}.${tenths % 10} kHz"
                            }
                            break
                        }
                    }
                    if (detectedSampleRate != null || detectedBitrateKbps != null) break
                }
            }

            val current = _currentSong.value
            val isJioSaavn = current != null && (
                current.albumArtUriString?.contains("saavn", ignoreCase = true) == true ||
                    current.uriString.contains("saavn", ignoreCase = true) ||
                    current.album.startsWith("saavn", ignoreCase = true) ||
                    current.originalId?.startsWith("saavn:", ignoreCase = true) == true
                )
            val isOnline = current != null && (current.filePath in listOf("ONLINE", "ONLINE_LYTRIX", "ONLINE_PODCAST") || current.uriString.startsWith("http"))
            val videoId = current?.originalId?.ifBlank { current.id.toString() } ?: current?.id?.toString() ?: ""

            val cachedQuality = if (isOnline && !isJioSaavn && videoId.isNotBlank()) {
                com.musicplayer.lytrix.service.PlaybackService.streamQualityMap[videoId]
            } else null

            val saavnUrlKbps = if (isJioSaavn && current != null) {
                Regex("""_(\d{2,3})\.mp4""", RegexOption.IGNORE_CASE)
                    .find(current.uriString)
                    ?.groupValues?.getOrNull(1)?.toIntOrNull()
            } else null

            val defaultRate = if (isJioSaavn) "44.1 kHz" else "48 kHz"

            val label: String? = when {
                !cachedQuality.isNullOrBlank() -> cachedQuality
                isJioSaavn -> {
                    val kbps = detectedBitrateKbps ?: saavnUrlKbps ?: 320
                    val rate = detectedSampleRate ?: "44.1 kHz"
                    "$kbps kbps · $rate"
                }
                detectedBitrateKbps != null && detectedSampleRate != null -> "$detectedBitrateKbps kbps · $detectedSampleRate"
                detectedBitrateKbps != null -> "$detectedBitrateKbps kbps · $defaultRate"
                isOnline && detectedSampleRate != null -> "128 kbps · $detectedSampleRate"
                isOnline -> "128 kbps · $defaultRate"
                else -> null
            }

            if (label == null && current != null && !isOnline) {
                val context = appContext
                if (context != null) {
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            val retriever = android.media.MediaMetadataRetriever()
                            val path = current.filePath
                            if (!path.isNullOrBlank() && java.io.File(path).exists()) {
                                retriever.setDataSource(path)
                            } else {
                                retriever.setDataSource(context, android.net.Uri.parse(current.uriString))
                            }
                            val bStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_BITRATE)
                            val bInt = bStr?.toIntOrNull() ?: 0
                            val kbps = if (bInt > 0) Math.round(bInt / 1000.0).toInt() else null
                            val mime = current.filePath?.lowercase(java.util.Locale.ROOT) ?: ""
                            val isLossless = mime.endsWith(".flac") || mime.endsWith(".wav") || (kbps != null && kbps >= 900)

                            val offLabel = when {
                                isLossless -> "Lossless · ${detectedSampleRate ?: "48 kHz"}"
                                kbps != null -> "$kbps kbps · ${detectedSampleRate ?: "44.1 kHz"}"
                                else -> "HD Audio · ${detectedSampleRate ?: "44.1 kHz"}"
                            }
                            _audioQualityLabel.value = offLabel
                            retriever.release()
                        } catch (_: Exception) {
                            val mime = current.filePath?.lowercase(java.util.Locale.ROOT) ?: ""
                            val fallback = when {
                                mime.endsWith(".flac") || mime.endsWith(".wav") -> "Lossless · 48 kHz"
                                mime.endsWith(".mp3") -> "320 kbps · 44.1 kHz"
                                mime.endsWith(".m4a") || mime.endsWith(".aac") -> "256 kbps · 44.1 kHz"
                                else -> "HD Audio · 44.1 kHz"
                            }
                            _audioQualityLabel.value = fallback
                        }
                    }
                }
            } else {
                _audioQualityLabel.value = label
            }
        } catch (_: Exception) {}
    }

    private fun syncCurrentMediaItem(controller: MediaController) {
        if (_currentQueueList.value.isEmpty() && controller.mediaItemCount > 0) {
            val q = mutableListOf<Song>()
            for (i in 0 until controller.mediaItemCount) {
                val mediaId = controller.getMediaItemAt(i).mediaId
                _songsList.value.find { it.id.toString() == mediaId }?.let { q.add(it) }
            }

            if (q.isNotEmpty()) _currentQueueList.value = q
        }

        val currentMediaId = controller.currentMediaItem?.mediaId
        val previousSongId = _currentSong.value?.id
        val song = _currentQueueList.value.find { it.id.toString() == currentMediaId }
            ?: _songsList.value.find { it.id.toString() == currentMediaId }
        if (song?.id != previousSongId) {
            clearAB()
            lastAudioDurationBeforeVideoMs = 0L
            clearNowPlayingRecommendations()
            if (_isVideoModeActive.value) {
                _isVideoModeActive.value = false
            }
            _isVideoPlayerReady.value = false
            preWarmedSongId = null
            preWarmedQuality = null
            pendingVideoSeekMs = null
            pendingVideoPlayWhenReady = null
            preWarmJob?.cancel()
            videoPlayer?.stop()
            videoPlayer?.clearMediaItems()
            updateCurrentSongAlbumState(song)
        }
        _currentSong.value = song
        updateAudioQualityFromController(controller)
        _duration.value = controller.duration.coerceAtLeast(0)
        val context = appContext ?: return
        currentMediaId?.let { mediaId ->
            val prefs = context.getSharedPreferences("playback_prefs", Context.MODE_PRIVATE)
            // 🔥 CRASH FIX: Safe Try-Catch
            try {
                prefs.edit().putString("last_song_id", mediaId).apply()
            } catch (e: Exception) {
            }
        }

        song?.let { songItem ->
            extractColorsAndAccent(context, songItem)
            checkAndIncrementPlayCount(controller)
            preWarmVideoIfEligible(context, songItem) // 🔥 নতুন লাইন
            if (songItem.isVideo || songItem.filePath == "OFFLINE" || songItem.albumArtUriString?.startsWith("file://") == true) {
                viewModelScope.launch(Dispatchers.IO) {
                    if (songItem.isVideo) {
                        ensureVideoArtwork(songItem)
                    }
                    val richMetadata = buildSafeMediaMetadata(songItem, includeArtworkData = true)
                    if (richMetadata.artworkUri != null || richMetadata.artworkData != null) {
                        kotlinx.coroutines.withContext(Dispatchers.Main) {
                            val currentIdx = controller.currentMediaItemIndex
                            if (currentIdx >= 0 && currentIdx < controller.mediaItemCount) {
                                val currentItem = controller.getMediaItemAt(currentIdx)
                                val currentMeta = currentItem.mediaMetadata
                                val uriChanged = richMetadata.artworkUri != null && currentMeta.artworkUri != richMetadata.artworkUri
                                val bytesMissing = currentMeta.artworkData == null && richMetadata.artworkData != null
                                if (currentItem.mediaId == songItem.id.toString() && (uriChanged || bytesMissing)) {
                                    val updatedItem = currentItem.buildUpon()
                                        .setMediaMetadata(richMetadata)
                                        .build()
                                    controller.replaceMediaItem(currentIdx, updatedItem)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (song != null && context != null) {
            viewModelScope.launch {
                _currentLyricsProviderIndex.value = -1
                _currentLyrics.value = "LOADING_LYRICS_STATE"
                val path = song.filePath
                val isLocalFile = path != null && !path.startsWith("ONLINE") && !path.startsWith("OFFLINE") && !path.startsWith("http")
                if (isLocalFile) {
                    val localLyrics = com.musicplayer.lytrix.utils.LyricsFetcher.readLocalLrcFile(song)
                    _currentLyrics.value = localLyrics
                    if (!localLyrics.isNullOrBlank()) {
                        _currentLyricsProviderName.value = "Local File"
                    }

                } else {
                    try {
                        val result = com.musicplayer.lytrix.utils.LyricsFetcher.fetchLyricsWithResult(context, song, -1)
                        if (result != null) {
                            val prefs = context.getSharedPreferences("lyrics_prefs", android.content.Context.MODE_PRIVATE)
                            val lyricsKey = "${song.title}_${song.artist}_lyrics"
                            val providerKey = "${song.title}_${song.artist}_provider"
                            prefs.edit()
                                .putString(lyricsKey, result.lyrics)
                                .putString(providerKey, result.providerName)
                                .apply()
                            _currentLyrics.value = result.lyrics
                            _currentLyricsProviderName.value = result.providerName
                            _currentLyricsProviderIndex.value = result.providerIndex
                        } else {
                            _currentLyrics.value = null
                            _currentLyricsProviderName.value = null
                        }

                    } catch (e: Exception) {
                        _currentLyrics.value = null
                        _currentLyricsProviderName.value = null
                    }
                }
            }

        } else {
            _currentLyrics.value = null
        }
    }

    private fun startProgressPoll() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive) {
                if (_isVideoModeActive.value) {
                    val current = _currentSong.value
                    val is16x9 = current != null && isSong16by9(current)
                    if (!is16x9) {
                        val vp = videoPlayer
                        if (vp != null && _isVideoPlayerReady.value &&
                            vp.playbackState != androidx.media3.common.Player.STATE_IDLE
                        ) {
                            val pos = vp.currentPosition
                            // বাফারিংয়ের সময় ExoPlayer ক্ষণিকের জন্য 0 দেয় — সেটা যেন আসল পজিশন মুছে না ফেলে
                            if (pos > 0L ||
                                vp.playbackState == androidx.media3.common.Player.STATE_READY ||
                                _currentPosition.value <= 1000L
                            ) {
                                _currentPosition.value = pos
                            }
                            val d = vp.duration
                            if (d > 0) _duration.value = d
                            _isPlaying.value = vp.playWhenReady &&
                                vp.playbackState != androidx.media3.common.Player.STATE_ENDED
                            _isBuffering.value = vp.playbackState == androidx.media3.common.Player.STATE_BUFFERING
                        } else {
                            // ভিডিও এখনো লোড হচ্ছে: পজিশন/প্লে-ইনটেন্ট ছুঁবে না
                            _isBuffering.value = true
                        }
                        kotlinx.coroutines.delay(250)
                        continue
                    }
                }
                mediaController?.let { controller ->
                    if (controller.isPlaying) {
                        _currentPosition.value = controller.currentPosition
                        val context = appContext
                        if (context != null) {
                            val mediaId = controller.currentMediaItem?.mediaId
                            if (mediaId != null) {
                                val prefs = context.getSharedPreferences(
                                    "playback_prefs",
                                    Context.MODE_PRIVATE
                                )
                                // 🔥 CRASH FIX: Safe Try-Catch
                                try {
                                    prefs.edit()
                                        .putLong("last_song_position", controller.currentPosition)
                                        .apply()
                                } catch (e: Exception) {
                                }
                            }

                            val currentSongPlaying = _currentSong.value
                            if (currentSongPlaying != null) {
                                val playtimePrefs = context.getSharedPreferences(
                                    "detailed_playtime",
                                    Context.MODE_PRIVATE
                                )
                                val appPrefs = context.getSharedPreferences(
                                    "app_preferences",
                                    Context.MODE_PRIVATE
                                )
                                // 🔥 CRASH FIX: Safe Try-Catch
                                val currentAudioMs = try {
                                    playtimePrefs.getLong("audio_ms", 0L)
                                } catch (e: Exception) {
                                    0L
                                }

                                val currentVideoMs = try {
                                    playtimePrefs.getLong("video_ms", 0L)
                                } catch (e: Exception) {
                                    0L
                                }

                                val currentWeeklyMs = try {
                                    playtimePrefs.getLong("weekly_ms", 0L)
                                } catch (e: Exception) {
                                    0L
                                }

                                val todayDate = java.text.SimpleDateFormat(
                                    "yyyyMMdd",
                                    java.util.Locale.getDefault()
                                ).format(java.util.Date())
                                val prefsData = try {
                                    context.dataStore.data.first()
                                } catch (e: Exception) {
                                    androidx.datastore.preferences.core.emptyPreferences()
                                }

                                val lastTrackedDate =
                                    prefsData[DataStoreManager.TASK_TRACKED_DATE] ?: ""
                                if (lastTrackedDate != todayDate) {
                                    context.dataStore.edit { p ->
                                        p[DataStoreManager.TASK_TRACKED_DATE] = todayDate
                                        p[DataStoreManager.DAILY_TASK_AUDIO_MS] = 0L
                                        p[DataStoreManager.DAILY_TASK_VIDEO_MS] = 0L
                                    }
                                }

                                val isVideoPlayingFlag = try {
                                    appPrefs.getBoolean("is_video_playing", false)
                                } catch (e: Exception) {
                                    false
                                }

                                if (isVideoPlayingFlag) {
                                    try {
                                        playtimePrefs.edit()
                                            .remove("video_ms")
                                            .putLong("video_ms", currentVideoMs + 400L)
                                            .remove("weekly_ms")
                                            .putLong("weekly_ms", currentWeeklyMs + 400L)
                                            .apply()
                                    } catch (e: Exception) {
                                    }

                                    val currentDailyVideoMs =
                                        prefsData[DataStoreManager.DAILY_TASK_VIDEO_MS] ?: 0L
                                    context.dataStore.edit { p ->
                                        p[DataStoreManager.DAILY_TASK_VIDEO_MS] =
                                            currentDailyVideoMs + 400L
                                    }

                                } else {
                                    try {
                                        playtimePrefs.edit()
                                            .remove("audio_ms")
                                            .putLong("audio_ms", currentAudioMs + 400L)
                                            .remove("weekly_ms")
                                            .putLong("weekly_ms", currentWeeklyMs + 400L)
                                            .apply()
                                    } catch (e: Exception) {
                                    }

                                    val currentDailyAudioMs =
                                        prefsData[DataStoreManager.DAILY_TASK_AUDIO_MS] ?: 0L
                                    context.dataStore.edit { p ->
                                        p[DataStoreManager.DAILY_TASK_AUDIO_MS] =
                                            currentDailyAudioMs + 400L
                                    }
                                }
                            }
                        }
                    }

                    kotlinx.coroutines.delay(400)
                }
            }
        }
    }

    private fun stopProgressPoll() {
        progressJob?.cancel()
        progressJob = null
    }

    private val _currentQueueList = MutableStateFlow<List<Song>>(emptyList())
    val currentQueueList: StateFlow<List<Song>> = _currentQueueList.asStateFlow()

    fun moveQueueItem(from: Int, to: Int) {
        val controller = mediaController ?: return
        val items = _currentQueueList.value.toMutableList()
        if (from in items.indices && to in items.indices) {
            val item = items.removeAt(from)
            items.add(to, item)
            _currentQueueList.value = items
            controller.moveMediaItem(from, to)
        }
    }

    private var lastSentQueueList: List<Song> = emptyList()

    fun playSong(song: Song, contextList: List<Song>? = null, initialPositionMs: Long? = null) {
        if (shouldBlockGuestPlayback()) {
            Toast.makeText(appContext ?: com.musicplayer.lytrix.LytrixApplication.instance, "Only the host can control playback in Listen Together", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            appContext?.getSharedPreferences(
                "app_preferences",
                android.content.Context.MODE_PRIVATE
            )
                ?.edit()?.putBoolean("is_video_playing", false)?.apply()
        } catch (e: Exception) {
        }

        // 🔥 Reset video states completely on any song change!
        _isVideoModeActive.value = false
        _isVideoHandoffPending.value = false
        cancelVideoHandoff()
        releaseVideoPlayer()
        _currentPosition.value = initialPositionMs ?: 0L

        val controller = mediaController ?: return
        val overrideCtx = appContext ?: com.musicplayer.lytrix.LytrixApplication.instance
        val listToPlay = (contextList ?: filteredSongs.value).map { applySongOverrides(overrideCtx, it) }
        val effectiveSong = applySongOverrides(overrideCtx, song)
        _currentQueueList.value = listToPlay
        var queueMatches = false
        if (controller.mediaItemCount == listToPlay.size) {
            val sz = listToPlay.size
            if (sz > 0) {
                if (lastSentQueueList == listToPlay) {
                    queueMatches = true
                } else if (controller.getMediaItemAt(0).mediaId == listToPlay[0].id.toString() &&
                    controller.getMediaItemAt(sz - 1).mediaId == listToPlay[sz - 1].id.toString() &&
                    controller.getMediaItemAt(sz / 2).mediaId == listToPlay[sz / 2].id.toString() && lastSentQueueList.isEmpty()
                ) {
                    queueMatches = true
                }

            } else {
                queueMatches = true
            }
        }

        val index = listToPlay.indexOfFirst { it.id == effectiveSong.id }.coerceAtLeast(0)
        val startPos = if (initialPositionMs != null && initialPositionMs > 0L) initialPositionMs else androidx.media3.common.C.TIME_UNSET
        if (initialPositionMs == null || initialPositionMs == 0L) {
            lastCountedSongId = null
        }

        if (!queueMatches) {
            val mediaItems = listToPlay.map { s ->
                androidx.media3.common.MediaItem.Builder()
                    .setMediaId(s.id.toString())
                    .setUri(s.uriString)
                    .setMediaMetadata(buildQueueMediaMetadata(s))
                    .build()
            }

            controller.setMediaItems(mediaItems, index, startPos)
            lastSentQueueList = listToPlay
            controller.prepare()
        } else {
            if (index >= 0) {
                controller.seekTo(index, startPos)
            }
        }

        if (index >= 0) {
            controller.play()
        }
    }

    fun dismissPlayer() {
        val controller = mediaController
        try {
            controller?.pause()
            controller?.stop()
            controller?.clearMediaItems()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        releaseVideoPlayer()
        _currentSong.value = null
        _isPlaying.value = false
        _currentPosition.value = 0L
        forceSyncToFirebase(isAutoPeriodic = false)
    }

    fun playPause() {
        if (shouldBlockGuestPlayback()) {
            Toast.makeText(appContext ?: com.musicplayer.lytrix.LytrixApplication.instance, "Only the host can control playback in Listen Together", Toast.LENGTH_SHORT).show()
            return
        }
        val current = _currentSong.value
        val is16x9 = current != null && isSong16by9(current)
        if (_isVideoModeActive.value && !is16x9) {
            setVideoPlayIntent(!_isPlaying.value)
            return
        }
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
            if (_isVideoModeActive.value && is16x9) {
                videoPlayer?.playWhenReady = false
            }
        } else {
            controller.play()
            if (_isVideoModeActive.value && is16x9) {
                videoPlayer?.playWhenReady = true
            }
        }

        forceSyncToFirebase(isAutoPeriodic = false)
    }

    fun skipNext() {
        if (shouldBlockGuestPlayback()) {
            Toast.makeText(appContext ?: com.musicplayer.lytrix.LytrixApplication.instance, "Only the host can control playback in Listen Together", Toast.LENGTH_SHORT).show()
            return
        }
        _isVideoModeActive.value = false
        _isVideoHandoffPending.value = false
        cancelVideoHandoff()
        releaseVideoPlayer()
        clearNowPlayingRecommendations()
        val controller = mediaController ?: return
        if (controller.hasNextMediaItem()) {
            controller.seekToNext()
        } else {
            controller.seekTo(0, 0)
        }
    }

    fun skipPrevious() {
        if (shouldBlockGuestPlayback()) {
            Toast.makeText(appContext ?: com.musicplayer.lytrix.LytrixApplication.instance, "Only the host can control playback in Listen Together", Toast.LENGTH_SHORT).show()
            return
        }
        _isVideoModeActive.value = false
        _isVideoHandoffPending.value = false
        cancelVideoHandoff()
        releaseVideoPlayer()
        clearNowPlayingRecommendations()
        val controller = mediaController ?: return
        if (controller.hasPreviousMediaItem()) {
            controller.seekToPrevious()
        } else {
            if (controller.mediaItemCount > 0) {
                controller.seekTo(controller.mediaItemCount - 1, 0)
            }
        }
    }

    fun seekTo(positionMs: Long) {
        if (shouldBlockGuestPlayback()) {
            Toast.makeText(appContext ?: com.musicplayer.lytrix.LytrixApplication.instance, "Only the host can control playback in Listen Together", Toast.LENGTH_SHORT).show()
            return
        }
        val current = _currentSong.value
        val is16x9 = current != null && isSong16by9(current)
        if (_isVideoModeActive.value && !is16x9) {
            videoPlayer?.seekTo(positionMs)
            _currentPosition.value = positionMs
            forceSyncToFirebase(isAutoPeriodic = false)
            return
        }
        _currentPosition.value = positionMs
        mediaController?.seekTo(positionMs)
        if (_isVideoModeActive.value && is16x9) {
            videoPlayer?.seekTo(positionMs)
        }
        forceSyncToFirebase(isAutoPeriodic = false)
        forceSyncToMetro(PlaybackActions.SEEK)
    }

    fun toggleShuffle() {
        val controller = mediaController ?: return
        controller.shuffleModeEnabled = !_shuffleModeEnabled.value
    }

    override fun onCleared() {
        try {
            syncJob?.cancel()
            detachRoomListeners()
            networkListener?.let {
                com.google.firebase.database.FirebaseDatabase.getInstance(dbUrl)
                    .getReference(".info/connected").removeEventListener(it)
            }
            networkListener = null
            playerListener?.let { mediaController?.removeListener(it) }
            playerListener = null
        } catch (e: Throwable) { e.printStackTrace() }

        stopProgressPoll()
        onlineLikedPrefsListener?.let { listener ->
            appContext?.getSharedPreferences("online_liked_songs", Context.MODE_PRIVATE)
                ?.unregisterOnSharedPreferenceChangeListener(listener)
        }
        onlineLikedPrefsListener = null
        releaseVideoPlayer() // 🔥 নতুন লাইন
        try {
            if (metroManager.connectionState.value == com.musicplayer.lytrix.listentogether.metro.MetroConnectionState.CONNECTED) {
                metroManager.client.disconnect()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            controllerFuture?.let { MediaController.releaseFuture(it) }
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        controllerFuture = null
        mediaController = null
        if (activeInstance == this) {
            activeInstance = null
        }
        super.onCleared()
    }

    fun toggleRepeat() {
        val controller = mediaController ?: return
        val nextMode = when (_repeatMode.value) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }

        controller.repeatMode = nextMode
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    private val _recentSongIds = MutableStateFlow<List<Long>>(emptyList())
    val recentSongIds: StateFlow<List<Long>> = _recentSongIds.asStateFlow()
    private val _onlineHistory = MutableStateFlow<List<com.musicplayer.lytrix.db.OnlineHistoryEntity>>(emptyList())
    val onlineHistory: StateFlow<List<com.musicplayer.lytrix.db.OnlineHistoryEntity>> = _onlineHistory.asStateFlow()
    private val _mostPlayedSongIds = MutableStateFlow<List<Long>>(emptyList())
    val mostPlayedSongIds: StateFlow<List<Long>> = _mostPlayedSongIds.asStateFlow()
    private val _playCounts = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val playCounts: StateFlow<Map<Long, Int>> = _playCounts.asStateFlow()
    private val _playlists =
        MutableStateFlow<List<com.musicplayer.lytrix.db.PlaylistEntity>>(emptyList())
    val playlists: StateFlow<List<com.musicplayer.lytrix.db.PlaylistEntity>> =
        combine(_playlists, _searchQuery, _songsList) { lists, query, allSongs ->
            if (query.isBlank()) {
                lists
            } else {
                lists.filter { playlist ->
                    playlist.name.contains(query, ignoreCase = true) ||
                            playlist.songIds.any { id ->
                                allSongs.find { it.id == id }?.title?.contains(
                                    query,
                                    ignoreCase = true
                                ) == true
                            }
                }
            }

        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val likedPlaylistVisible: StateFlow<Boolean> =
        combine(_searchQuery, _favoriteSongIds, _songsList) { query, ids, songs ->
            query.isBlank() || "liked songs".contains(
                query,
                true
            ) || ids.any { id -> songs.find { it.id == id }?.title?.contains(query, true) == true }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val recentPlaylistVisible: StateFlow<Boolean> =
        combine(_searchQuery, _recentSongIds, _songsList) { query, ids, songs ->
            query.isBlank() || "recently played".contains(
                query,
                true
            ) || ids.any { id -> songs.find { it.id == id }?.title?.contains(query, true) == true }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val mostPlayedPlaylistVisible: StateFlow<Boolean> =
        combine(_searchQuery, _mostPlayedSongIds, _songsList) { query, ids, songs ->
            query.isBlank() || "most played".contains(
                query,
                true
            ) || ids.any { id -> songs.find { it.id == id }?.title?.contains(query, true) == true }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun createPlaylist(name: String) {
        val context = appContext ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val dao = com.musicplayer.lytrix.db.AppDatabase.getDatabase(context).playlistDao()
            dao.insertPlaylist(com.musicplayer.lytrix.db.PlaylistEntity(name = name))
        }
    }

    fun deletePlaylist(playlistId: Long) {
        val context = appContext ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val dao = com.musicplayer.lytrix.db.AppDatabase.getDatabase(context).playlistDao()
            dao.deletePlaylistCrossRefs(playlistId)
            val playlist = dao.getPlaylistById(playlistId)
            if (playlist != null) {
                dao.deletePlaylist(playlist)
            }
        }
    }

    fun addSongToPlaylist(playlistId: Long, songId: Long) {
        val context = appContext ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val dao = com.musicplayer.lytrix.db.AppDatabase.getDatabase(context).playlistDao()
            dao.insertPlaylistSongCrossRef(
                com.musicplayer.lytrix.db.PlaylistSongCrossRef(
                    playlistId = playlistId,
                    songId = songId
                )
            )
            val cur = dao.getPlaylistById(playlistId)
            cur?.let { dao.updatePlaylist(it.copy(songCount = it.songCount + 1)) }
        }
    }

    fun addSongsToPlaylist(playlistId: Long, songIds: List<Long>) {
        val context = appContext ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val dao = com.musicplayer.lytrix.db.AppDatabase.getDatabase(context).playlistDao()
            songIds.forEach { id ->
                dao.insertPlaylistSongCrossRef(
                    com.musicplayer.lytrix.db.PlaylistSongCrossRef(
                        playlistId = playlistId,
                        songId = id
                    )
                )
            }

            val cur = dao.getPlaylistById(playlistId)
            cur?.let { dao.updatePlaylist(it.copy(songCount = it.songCount + songIds.size)) }
        }
    }

    fun reorderPlaylist(playlistId: Long, songIds: List<Long>) {
        val context = appContext ?: return
        viewModelScope.launch(Dispatchers.IO) {
            com.musicplayer.lytrix.db.AppDatabase.getDatabase(context).playlistDao()
                .reorderPlaylistSongs(playlistId, songIds)
        }
    }

    fun reorderFavorites(songIds: List<Long>) {
        val context = appContext ?: return
        viewModelScope.launch(Dispatchers.IO) {
            com.musicplayer.lytrix.db.AppDatabase.getDatabase(context).songMetadataDao()
                .reorderFavoriteSongs(songIds)
        }
    }

    fun removeSongsFromPlaylist(playlistId: Long, songIds: List<Long>) {
        val context = appContext ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val dao = com.musicplayer.lytrix.db.AppDatabase.getDatabase(context).playlistDao()
            dao.removeSongsFromPlaylist(playlistId, songIds)
            val cur = dao.getPlaylistById(playlistId)
            cur?.let {
                dao.updatePlaylist(
                    it.copy(
                        songCount = maxOf(
                            0,
                            it.songCount - songIds.size
                        )
                    )
                )
            }
        }
    }

    fun getPlaylistSongsFlow(playlistId: Long): kotlinx.coroutines.flow.Flow<List<Long>> {
        val context = appContext ?: return kotlinx.coroutines.flow.emptyFlow()
        val dao = com.musicplayer.lytrix.db.AppDatabase.getDatabase(context).playlistDao()
        return dao.getPlaylistCrossRefs(playlistId).map { crossRefs -> crossRefs.map { it.songId } }
    }

    // Verbatim MetroList Tempo & Pitch + Sleep Timer state/controller
    var currentPlaybackSpeed = kotlinx.coroutines.flow.MutableStateFlow(1.0f)
    var currentPlaybackPitch = kotlinx.coroutines.flow.MutableStateFlow(1.0f)
    var currentPlaybackPitchSemitones = kotlinx.coroutines.flow.MutableStateFlow(0)
    var currentSleepTimerMinutes = kotlinx.coroutines.flow.MutableStateFlow(0)
    var playLastSongToEnd = kotlinx.coroutines.flow.MutableStateFlow(false)

    private val metroSleepTimerPlayer = object : com.musicplayer.lytrix.playback.SleepTimerPlayer {
        override val isAvailable: Boolean
            get() = mediaController != null || videoPlayer != null
        override val currentPositionMs: Long
            get() = livePosition()
        override val durationMs: Long
            get() = _duration.value
        override val playbackProgress: kotlinx.coroutines.flow.Flow<Pair<Long, Long>> =
            kotlinx.coroutines.flow.combine(_currentPosition, _duration) { pos, dur -> pos to dur }
        override var pauseAtEndOfMediaItems: Boolean = false
            set(value) {
                field = value
                com.musicplayer.lytrix.service.PlaybackService.setPauseAtEndOfMediaItems(value)
            }

        override fun pause() {
            viewModelScope.launch(Dispatchers.Main) {
                if (_isVideoModeActive.value && is1to1VideoActiveNow()) {
                    videoPlayer?.playWhenReady = false
                    videoPlayer?.pause()
                }
                mediaController?.pause()
                com.musicplayer.lytrix.service.PlaybackService.pauseIfPlaying()
                _isPlaying.value = false
                currentSleepTimerMinutes.value = 0
                playLastSongToEnd.value = false
            }
        }

        override fun cancelCrossfadeTransition() {}

        override fun setSleepTimerGain(gain: Float) {
            com.musicplayer.lytrix.service.PlaybackService.setSleepTimerGain(gain)
        }
    }

    private val metroSleepTimer = com.musicplayer.lytrix.playback.MetroSleepTimerController(
        scope = viewModelScope,
        player = metroSleepTimerPlayer,
    )

    val sleepTimerState: StateFlow<com.musicplayer.lytrix.playback.SleepTimerState> = metroSleepTimer.state

    fun startSleepTimer(durationMs: Long, fadeOut: Boolean, finishCurrentSong: Boolean) {
        currentSleepTimerMinutes.value = ((durationMs + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
        playLastSongToEnd.value = finishCurrentSong
        metroSleepTimer.start(durationMs, fadeOut, finishCurrentSong)
    }

    fun stopSleepTimerAfterCurrentSong(fadeOut: Boolean) {
        currentSleepTimerMinutes.value = 1
        playLastSongToEnd.value = true
        metroSleepTimer.stopAfterCurrentSong(fadeOut)
    }

    fun cancelSleepTimer() {
        currentSleepTimerMinutes.value = 0
        playLastSongToEnd.value = false
        metroSleepTimer.cancel()
    }

    fun setSleepTimer(minutes: Int, playLastOneToEnd: Boolean) {
        if (minutes <= 0) {
            cancelSleepTimer()
        } else {
            startSleepTimer(minutes * 60_000L, fadeOut = false, finishCurrentSong = playLastOneToEnd)
        }
    }

    fun setPlaybackSpeed(speed: Float, immediate: Boolean = false) {
        if (!speed.isFinite()) return
        val clampedSpeed = kotlin.math.round(speed.coerceIn(0.5f, 2.0f) * 100f) / 100f
        currentPlaybackSpeed.value = clampedSpeed
        if (immediate) {
            commitPlaybackParametersNow()
        }
    }

    fun setPlaybackPitch(pitch: Float, immediate: Boolean = false) {
        if (!pitch.isFinite()) return
        val clampedPitch = pitch.coerceIn(0.5f, 2.0f)
        currentPlaybackPitch.value = clampedPitch
        currentPlaybackPitchSemitones.value = kotlin.math.round(
            com.musicplayer.lytrix.ui.component.metroPlaybackPitchToSemitones(clampedPitch)
        ).toInt().coerceIn(-12, 12)
        if (immediate) {
            commitPlaybackParametersNow()
        }
    }

    fun commitPlaybackParametersNow() {
        applyPlaybackParametersNow()
    }

    fun setPlaybackPitchSemitones(semitones: Int) {
        val clamped = semitones.coerceIn(-12, 12)
        currentPlaybackPitchSemitones.value = clamped
        currentPlaybackPitch.value = com.musicplayer.lytrix.ui.component.metroSemitonesToPlaybackPitch(clamped.toFloat())
        commitPlaybackParametersNow()
    }

    fun setTempoAndPitch(speed: Float, semitones: Int) {
        val clampedSpeed = kotlin.math.round(speed.coerceIn(0.5f, 2.0f) * 100f) / 100f
        val clampedSemitones = semitones.coerceIn(-12, 12)
        currentPlaybackSpeed.value = clampedSpeed
        currentPlaybackPitchSemitones.value = clampedSemitones
        currentPlaybackPitch.value = com.musicplayer.lytrix.ui.component.metroSemitonesToPlaybackPitch(clampedSemitones.toFloat())
        commitPlaybackParametersNow()
    }

    private fun applyPlaybackParametersNow() {
        val speed = currentPlaybackSpeed.value.coerceIn(0.5f, 2.0f)
        val pitch = currentPlaybackPitch.value.coerceIn(0.5f, 2.0f)
        val params = androidx.media3.common.PlaybackParameters(speed, pitch)
        com.musicplayer.lytrix.service.PlaybackService.setTempoAndPitch(speed, pitch)
        if (com.musicplayer.lytrix.service.PlaybackService.activeInstance == null) {
            mediaController?.let { controller ->
                val prev = controller.playbackParameters
                val pitchChanged = kotlin.math.abs(prev.pitch - params.pitch) > 0.0001f
                val speedChanged = kotlin.math.abs(prev.speed - params.speed) > 0.0001f
                if (pitchChanged || speedChanged) {
                    controller.playbackParameters = params
                    if (pitchChanged && controller.mediaItemCount > 0) {
                        controller.seekTo(controller.currentPosition.coerceAtLeast(0L))
                    }
                }
            }
        }
        videoPlayer?.let { vp ->
            try {
                val prev = vp.playbackParameters
                val pitchChanged = kotlin.math.abs(prev.pitch - params.pitch) > 0.0001f
                val speedChanged = kotlin.math.abs(prev.speed - params.speed) > 0.0001f
                if (pitchChanged || speedChanged) {
                    vp.playbackParameters = params
                    if (pitchChanged &&
                        vp.mediaItemCount > 0 &&
                        (vp.playbackState == Player.STATE_READY || vp.playbackState == Player.STATE_BUFFERING)
                    ) {
                        vp.seekTo(vp.currentPosition.coerceAtLeast(0L))
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun extractColorsAndAccent(context: Context, song: Song) {
        val artUri = song.albumArtUriString
        if (artUri.isNullOrBlank()) {
            _paletteAccentColor.value = generateFallbackColor(song.title)
            return
        }

        viewModelScope.launch {
            try {
                val loader = context.imageLoader
                val request = coil.request.ImageRequest.Builder(context)
                    .data(artUri)
                    .size(200)
                    .allowHardware(false)
                    .build()
                val result = loader.execute(request)
                if (result is coil.request.SuccessResult) {
                    val drawable = result.drawable
                    if (drawable is android.graphics.drawable.BitmapDrawable) {
                        val bitmap = drawable.bitmap
                        androidx.palette.graphics.Palette.from(bitmap).generate { palette ->
                            val vibrant = palette?.vibrantSwatch?.rgb
                            val dominant = palette?.dominantSwatch?.rgb
                            val lightVibrant = palette?.lightVibrantSwatch?.rgb
                            val darkVibrant = palette?.darkVibrantSwatch?.rgb
                            val muted = palette?.mutedSwatch?.rgb
                            val colorInt = vibrant ?: dominant ?: lightVibrant ?: darkVibrant ?: muted
                            _paletteAccentColor.value = if (colorInt != null) {
                                Color(colorInt)
                            } else {
                                generateFallbackColor(song.title)
                            }
                        }

                    } else {
                        _paletteAccentColor.value = generateFallbackColor(song.title)
                    }

                } else {
                    _paletteAccentColor.value = generateFallbackColor(song.title)
                }

            } catch (e: Exception) {
                _paletteAccentColor.value = generateFallbackColor(song.title)
            }
        }
    }

    private fun generateFallbackColor(text: String): Color {
        val hash = text.hashCode()
        val hue = (((hash and 0xFF) / 255f) * 360f)
        val h = hue / 60f
        val c = 0.85f * 0.75f
        val x = c * (1 - kotlin.math.abs((h % 2) - 1))
        val m = 0.85f - c
        val (r, g, b) = when (h.toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return Color(r + m, g + m, b + m, 1f)
    }

    // 🔥 লাইক বাগ ফিক্স (Double Like Solution + Thumbnail Fix)

    fun isSongOnline(song: Song): Boolean {
        return song.filePath in listOf("ONLINE", "ONLINE_LYTRIX", "OFFLINE", "ONLINE_PODCAST", "OFFLINE_PODCAST", "ONLINE_RADIO") ||
                song.uriString.startsWith("http", ignoreCase = true) ||
                song.filePath?.startsWith("http", ignoreCase = true) == true
    }

    fun onlineLikeKey(song: Song): String {
        return song.originalId?.ifBlank { null } ?: song.id.toString()
    }

    private val _currentMediaCategory = MutableStateFlow("MUSIC")
    val currentMediaCategory: StateFlow<String> = _currentMediaCategory.asStateFlow()

    fun setMediaCategory(category: String) {
        _currentMediaCategory.value = category
        appContext?.let { syncOnlineLikedState(it) }
    }

    fun getLikedPrefsNameForSong(song: Song?): String {
        if (song == null) return "online_liked_songs"
        val isAb = song.mediaType.equals("AUDIOBOOK", ignoreCase = true) ||
                song.filePath == "ONLINE_AUDIOBOOK" ||
                song.filePath == "OFFLINE_AUDIOBOOK" ||
                _currentMediaCategory.value.equals("AUDIOBOOK", ignoreCase = true) ||
                (song.album.contains("Audiobook", ignoreCase = true) && !song.album.equals("Podcast/Audiobook", ignoreCase = true))
        if (isAb) return "online_liked_audiobooks"

        val isPod = song.mediaType.equals("PODCAST", ignoreCase = true) ||
                song.filePath == "ONLINE_PODCAST" ||
                song.filePath == "OFFLINE_PODCAST" ||
                _currentMediaCategory.value.equals("PODCAST", ignoreCase = true) ||
                (song.album.contains("Podcast", ignoreCase = true) && !song.album.equals("Podcast/Audiobook", ignoreCase = true))
        if (isPod) return "online_liked_podcasts"

        return "online_liked_songs"
    }

    fun syncOnlineLikedState(context: Context) {
        val prefName = getLikedPrefsNameForSong(_currentSong.value)
        val prefs = context.applicationContext.getSharedPreferences(prefName, Context.MODE_PRIVATE)
        _onlineLikedIds.value = prefs.getStringSet("liked_ids", emptySet())?.toSet() ?: emptySet()
    }

    private fun initOnlineLikedObserver(context: Context) {
        syncOnlineLikedState(context)
        if (onlineLikedPrefsListener == null) {
            val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
                if (key == "liked_ids") {
                    _onlineLikedIds.value = p.getStringSet("liked_ids", emptySet())?.toSet() ?: emptySet()
                }
            }
            onlineLikedPrefsListener = listener
            val pMusic = context.applicationContext.getSharedPreferences("online_liked_songs", Context.MODE_PRIVATE)
            val pPod = context.applicationContext.getSharedPreferences("online_liked_podcasts", Context.MODE_PRIVATE)
            val pAb = context.applicationContext.getSharedPreferences("online_liked_audiobooks", Context.MODE_PRIVATE)
            pMusic.registerOnSharedPreferenceChangeListener(listener)
            pPod.registerOnSharedPreferenceChangeListener(listener)
            pAb.registerOnSharedPreferenceChangeListener(listener)
        }
    }

    fun toggleFavorite(songId: Long) {
        val context = appContext ?: return
        val currentSongPlay = _currentSong.value?.takeIf { it.id == songId } ?: _songsList.value.find { it.id == songId } ?: _currentSong.value ?: return
        val isCloudSong = isSongOnline(currentSongPlay)
        if (isCloudSong) {
            val targetPref = getLikedPrefsNameForSong(currentSongPlay)
            val prefs = context.getSharedPreferences(targetPref, Context.MODE_PRIVATE)
            val oldSet = prefs.getStringSet("liked_ids", emptySet()) ?: emptySet()
            val newSet = java.util.HashSet<String>(oldSet)
            val songIdStr = onlineLikeKey(currentSongPlay)
            val isLikedNow: Boolean
            if (newSet.contains(songIdStr)) {
                newSet.remove(songIdStr)
                isLikedNow = false
                _onlineLikedIds.value = newSet.toSet()
                prefs.edit()
                    .remove("song_data_$songIdStr")
                    .remove("podcast_data_$songIdStr")
                    .remove("audiobook_data_$songIdStr")
                    .putStringSet("liked_ids", newSet)
                    .apply()
            } else {
                newSet.add(songIdStr)
                isLikedNow = true
                _onlineLikedIds.value = newSet.toSet()
                val jsonObj = org.json.JSONObject()
                jsonObj.put("id", songIdStr)
                jsonObj.put("title", currentSongPlay.title)
                jsonObj.put("title_original", currentSongPlay.title)
                jsonObj.put("name", currentSongPlay.title)
                jsonObj.put("subtitle", currentSongPlay.artist)
                jsonObj.put("artist", currentSongPlay.artist)
                jsonObj.put("publisher", currentSongPlay.artist)
                jsonObj.put("image", currentSongPlay.albumArtUriString ?: "")
                jsonObj.put("imageUrl", currentSongPlay.albumArtUriString ?: "")
                jsonObj.put("image_url", currentSongPlay.albumArtUriString ?: "")
                jsonObj.put("url", currentSongPlay.uriString)
                jsonObj.put("downloadUrl", currentSongPlay.uriString)
                jsonObj.put("stream_url", currentSongPlay.uriString)
                jsonObj.put("audio", currentSongPlay.uriString)
                currentSongPlay.channelId?.let { jsonObj.put("channelId", it) }
                prefs.edit()
                    .putString("song_data_$songIdStr", jsonObj.toString())
                    .putString("podcast_data_$songIdStr", jsonObj.toString())
                    .putString("audiobook_data_$songIdStr", jsonObj.toString())
                    .putStringSet("liked_ids", newSet)
                    .apply()
            }
            val toastMsg = when (targetPref) {
                "online_liked_podcasts" -> if (isLikedNow) "Saved to Liked Podcasts" else "Removed from Liked Podcasts"
                "online_liked_audiobooks" -> if (isLikedNow) "Saved to Liked Audiobooks" else "Removed from Liked Audiobooks"
                else -> if (isLikedNow) "Saved to Cloud Likes" else "Removed from Cloud Likes"
            }
            Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
            performSilentSync(context)
            com.musicplayer.lytrix.widget.LytrixWidgetUpdater.scheduleUpdate(context, immediate = true)
        } else {
            // যদি লোকাল গান হয়, তবে আগের মতোই লোকাল ডাটাবেসে সেভ হবে
            viewModelScope.launch(Dispatchers.IO) {
                val dao = com.musicplayer.lytrix.db.AppDatabase.getDatabase(context).songMetadataDao()
                val existing = dao.getMetadata(songId)
                if (existing != null) {
                    dao.updateFavorite(songId, !existing.isFavorite)
                } else {
                    dao.insertOrUpdate(
                        com.musicplayer.lytrix.db.SongMetadataEntity(
                            songId = songId,
                            isFavorite = true
                        )
                    )
                }
                com.musicplayer.lytrix.widget.LytrixWidgetUpdater.scheduleUpdate(context, immediate = true)
            }
        }
    }

    fun setPlayerTheme(theme: String) {
        val context = appContext ?: return
        viewModelScope.launch(Dispatchers.IO) {
            com.musicplayer.lytrix.db.ThemePreferencesManager(context).savePlayerTheme(theme)
        }
    }

    suspend fun renameSong(songId: Long, newName: String): android.content.IntentSender? {
        val context = appContext ?: return null
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val song = _songsList.value.find { it.id == songId } ?: return@withContext null
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                val contentUri = android.content.ContentUris.withAppendedId(
                    if (song.isVideo) android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    else android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    songId
                )
                try {
                    val extension = song.filePath
                        ?.let { java.io.File(it).extension }
                        ?.takeIf { it.isNotBlank() }
                        ?: ""
                    val newDisplayName =
                        if (extension.isNotEmpty()) "$newName.$extension" else newName
                    val values = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, newDisplayName)
                        put(android.provider.MediaStore.MediaColumns.TITLE, newName)
                    }

                    context.contentResolver.update(contentUri, values, null, null)
                    val rowsUpdated = context.contentResolver.update(
                        contentUri,
                        values,
                        null,
                        null
                    )
                    if (rowsUpdated > 0) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            val updatedSong = song.copy(title = newName)
                            _songsList.value =
                                _songsList.value.map { if (it.id == songId) updatedSong else it }
                            if (_currentSong.value?.id == songId) {
                                _currentSong.value = updatedSong
                            }
                        }
                    }

                    return@withContext null
                } catch (e: SecurityException) {
                    val pendingIntent = android.provider.MediaStore.createWriteRequest(
                        context.contentResolver,
                        listOf(contentUri)
                    )
                    return@withContext pendingIntent.intentSender
                } catch (e: Exception) {
                    e.printStackTrace()
                }

            } else {
                try {
                    var path = song.uriString
                    if (path.startsWith("file://")) path = path.substring(7)
                    val oldFile = java.io.File(path)
                    if (oldFile.exists()) {
                        val ext = oldFile.extension
                        val newTitle = newName.replace("/", "_")
                        val newFileName = if (ext.isNotEmpty()) "$newTitle.$ext" else newTitle
                        val newFile = java.io.File(oldFile.parent, newFileName)
                        if (oldFile.renameTo(newFile)) {
                            android.media.MediaScannerConnection.scanFile(
                                context,
                                arrayOf(oldFile.absolutePath, newFile.absolutePath),
                                null,
                                null
                            )
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                val renamedSong = song.copy(
                                    title = newName,
                                    uriString = "file://" + newFile.absolutePath
                                )
                                _songsList.value =
                                    _songsList.value.map { if (it.id == songId) renamedSong else it }
                                if (_currentSong.value?.id == songId) _currentSong.value =
                                    renamedSong
                            }
                        }
                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            return@withContext null
        }
    }

    fun hideSongs(songIds: Set<Long>) {
        songIds.forEach { hideSong(it) }
    }

    fun deleteSongs(songIds: Set<Long>) {
        val context = appContext ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val songsToDelete = _songsList.value.filter { songIds.contains(it.id) }
            var changed = false
            songsToDelete.forEach { song ->
                try {
                    val uri = android.net.Uri.parse(song.uriString)
                    val path = song.filePath ?: ""
                    if (com.musicplayer.lytrix.utils.TrashManager.moveToTrash(
                            context,
                            uri,
                            path,
                            false
                        )
                    ) {
                        changed = true
                    }

                } catch (e: Exception) {
                }
            }

            if (changed) {
                withContext(Dispatchers.Main) {
                    _songsList.value = _songsList.value.filterNot { songIds.contains(it.id) }
                }
            }
        }
    }

    fun addMultipleToPlaylist(playlistId: Long, songIds: Set<Long>) {
        val context = appContext ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val dao = com.musicplayer.lytrix.db.AppDatabase.getDatabase(context).playlistDao()
            songIds.forEach { id ->
                dao.insertPlaylistSongCrossRef(
                    com.musicplayer.lytrix.db.PlaylistSongCrossRef(
                        playlistId = playlistId,
                        songId = id
                    )
                )
            }
        }
    }

    private fun extractYouTubeIdFromUri(uri: String?): String? {
        if (uri.isNullOrBlank()) return null
        if (!uri.contains("youtube.com") && !uri.contains("youtu.be")) return null
        val parsed = runCatching { android.net.Uri.parse(uri) }.getOrNull() ?: return null
        return (parsed.getQueryParameter("v") ?: parsed.lastPathSegment)?.takeIf { it.isNotBlank() }
    }

    private fun songCustomArtKey(song: Song): String =
        song.originalId?.takeIf { it.isNotBlank() } ?: song.id.toString()

    fun hasCustomAlbumArt(context: Context, song: Song): Boolean {
        val prefs = context.getSharedPreferences("custom_album_arts", Context.MODE_PRIVATE)
        return prefs.contains(songCustomArtKey(song)) || prefs.contains(song.id.toString())
    }

    fun getEffectiveYouTubeVideoId(context: Context, song: Song): String? {
        val baseId = song.originalId?.takeIf { it.isNotBlank() } ?: extractYouTubeIdFromUri(song.uriString) ?: return null
        val prefs = context.getSharedPreferences("youtube_match_overrides", Context.MODE_PRIVATE)
        return prefs.getString("override_$baseId", null) ?: baseId
    }

    fun hasYouTubeMatchOverride(context: Context, song: Song): Boolean {
        val baseId = song.originalId?.takeIf { it.isNotBlank() } ?: extractYouTubeIdFromUri(song.uriString) ?: return false
        return context.getSharedPreferences("youtube_match_overrides", Context.MODE_PRIVATE).contains("override_$baseId")
    }

    private fun applySongOverrides(context: Context, song: Song): Song {
        var updated = song
        val baseYtId = song.originalId?.takeIf { it.isNotBlank() } ?: extractYouTubeIdFromUri(song.uriString)
        if (baseYtId != null && (song.uriString.contains("youtube.com") || song.uriString.contains("youtu.be") || song.filePath in listOf("ONLINE", "ONLINE_LYTRIX"))) {
            val matchPrefs = context.getSharedPreferences("youtube_match_overrides", Context.MODE_PRIVATE)
            val overrideId = matchPrefs.getString("override_$baseYtId", null)
            if (!overrideId.isNullOrBlank()) {
                val newTitle = matchPrefs.getString("title_$baseYtId", null) ?: updated.title
                val newArtist = matchPrefs.getString("artist_$baseYtId", null) ?: updated.artist
                val newThumb = matchPrefs.getString("thumb_$baseYtId", null) ?: updated.albumArtUriString
                updated = updated.copy(
                    uriString = "https://www.youtube.com/watch?v=$overrideId",
                    title = newTitle,
                    artist = newArtist,
                    albumArtUriString = newThumb
                )
            }
        }
        val artPrefs = context.getSharedPreferences("custom_album_arts", Context.MODE_PRIVATE)
        val customArt = artPrefs.getString(songCustomArtKey(updated), null)
            ?: artPrefs.getString(updated.id.toString(), null)
        if (!customArt.isNullOrBlank()) {
            updated = updated.copy(albumArtUriString = customArt)
        }
        return updated
    }

    private fun applyUpdatedSongState(context: Context, targetSong: Song, updatedSong: Song, replaceMediaUri: Boolean = false) {
        val currentSongs = _songsList.value.toMutableList()
        val libIdx = currentSongs.indexOfFirst { it.id == targetSong.id }
        if (libIdx != -1) {
            currentSongs[libIdx] = updatedSong
            _songsList.value = currentSongs
        }
        if (_currentSong.value?.id == targetSong.id) {
            _currentSong.value = updatedSong
            extractColorsAndAccent(context, updatedSong)
        }
        _currentQueueList.value = _currentQueueList.value.map {
            if (it.id == targetSong.id) updatedSong else it
        }
        val controller = mediaController ?: return
        for (i in 0 until controller.mediaItemCount) {
            if (controller.getMediaItemAt(i).mediaId == targetSong.id.toString()) {
                val existingItem = controller.getMediaItemAt(i)
                val isCurrent = controller.currentMediaItemIndex == i
                val rebuilt = androidx.media3.common.MediaItem.Builder()
                    .setMediaId(updatedSong.id.toString())
                    .setUri(if (replaceMediaUri) updatedSong.uriString else (existingItem.localConfiguration?.uri?.toString() ?: updatedSong.uriString))
                    .setMediaMetadata(buildSafeMediaMetadata(updatedSong, includeArtworkData = isCurrent))
                    .build()
                val wasPlaying = controller.isPlaying
                controller.replaceMediaItem(i, rebuilt)
                if (replaceMediaUri && isCurrent) {
                    controller.seekTo(i, 0L)
                    controller.prepare()
                    if (wasPlaying) controller.play()
                }
                break
            }
        }
    }

    fun saveCustomAlbumArt(context: Context, songId: Long, uri: android.net.Uri) {
        val target = _songsList.value.find { it.id == songId }
            ?: _currentQueueList.value.find { it.id == songId }
            ?: _currentSong.value?.takeIf { it.id == songId }
            ?: Song(songId, "", "", "", 0L, "")
        saveCustomAlbumArt(context, target, uri)
    }

    fun saveCustomAlbumArt(context: Context, song: Song, uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val artKey = songCustomArtKey(song)
                val safePrefix = "custom_album_art_${artKey.replace(Regex("[^a-zA-Z0-9_-]"), "_")}"
                val fileName = "${safePrefix}_${System.currentTimeMillis()}.jpg"
                context.filesDir.listFiles()?.forEach { f ->
                    if (f.name.startsWith(safePrefix) || f.name.startsWith("custom_album_art_${song.id}")) {
                        f.delete()
                    }
                }

                val file = java.io.File(context.filesDir, fileName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    java.io.FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }

                val newArtUriString = "file://" + file.absolutePath
                val prefs = context.getSharedPreferences("custom_album_arts", Context.MODE_PRIVATE)
                prefs.edit().apply {
                    if (!prefs.contains("orig_$artKey") && !song.albumArtUriString.isNullOrBlank() && !song.albumArtUriString.startsWith("file://")) {
                        putString("orig_$artKey", song.albumArtUriString)
                    }
                    putString(artKey, newArtUriString)
                    putString(song.id.toString(), newArtUriString)
                }.apply()
                withContext(Dispatchers.Main) {
                    applyUpdatedSongState(context, song, song.copy(albumArtUriString = newArtUriString))
                    Toast.makeText(context, "Cover art updated", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun saveCustomAlbumArtUrl(context: Context, song: Song, imageUrl: String) {
        val cleanUrl = imageUrl.trim()
        if (cleanUrl.isBlank()) return
        val artKey = songCustomArtKey(song)
        val prefs = context.getSharedPreferences("custom_album_arts", Context.MODE_PRIVATE)
        prefs.edit().apply {
            if (!prefs.contains("orig_$artKey") && !song.albumArtUriString.isNullOrBlank() && !song.albumArtUriString.startsWith("file://")) {
                putString("orig_$artKey", song.albumArtUriString)
            }
            putString(artKey, cleanUrl)
            putString(song.id.toString(), cleanUrl)
        }.apply()
        applyUpdatedSongState(context, song, song.copy(albumArtUriString = cleanUrl))
        Toast.makeText(context, "Cover URL applied", Toast.LENGTH_SHORT).show()
    }

    fun overrideYouTubeMatch(
        context: Context,
        song: Song,
        selection: com.musicplayer.lytrix.ui.component.YouTubeMatchSelection
    ) {
        if (isInListenTogetherRoom()) {
            Toast.makeText(context, "Changing YouTube match is disabled in Listen Together", Toast.LENGTH_SHORT).show()
            return
        }
        val baseId = song.originalId?.takeIf { it.isNotBlank() } ?: extractYouTubeIdFromUri(song.uriString) ?: return
        val prefs = context.getSharedPreferences("youtube_match_overrides", Context.MODE_PRIVATE)
        prefs.edit().apply {
            if (!prefs.contains("orig_title_$baseId")) putString("orig_title_$baseId", song.title)
            if (!prefs.contains("orig_artist_$baseId")) putString("orig_artist_$baseId", song.artist)
            if (!prefs.contains("orig_thumb_$baseId") && !song.albumArtUriString.isNullOrBlank()) {
                putString("orig_thumb_$baseId", song.albumArtUriString)
            }
            if (!prefs.contains("orig_is_video_$baseId")) {
                putBoolean("orig_is_video_$baseId", song.isVideo)
            }
            putString("override_$baseId", selection.videoId)
            if (selection.keepOriginalMetadata) {
                remove("title_$baseId")
                remove("artist_$baseId")
                remove("thumb_$baseId")
            } else {
                putString("title_$baseId", selection.title.ifBlank { song.title })
                putString("artist_$baseId", selection.artist.ifBlank { song.artist })
                if (!selection.thumbnailUrl.isNullOrBlank()) putString("thumb_$baseId", selection.thumbnailUrl)
            }
        }.apply()

        // 🧹 Clear in-memory counterpart and video unavailable cache for this song & video
        songKeys(song, selection.videoId).forEach { key ->
            counterpartMap.remove(key)
            videoUnavailableKeys.remove(key)
        }
        videoStreamCache.clear()
        preWarmedSongId = null
        preWarmJob?.cancel()
        releaseVideoPlayer()

        val customArt = context.getSharedPreferences("custom_album_arts", Context.MODE_PRIVATE)
            .getString(songCustomArtKey(song), null)
        val updated = if (selection.keepOriginalMetadata) {
            song.copy(
                uriString = "https://www.youtube.com/watch?v=${selection.videoId}",
                duration = selection.durationSec?.let { it * 1000L } ?: song.duration,
                albumArtUriString = customArt ?: song.albumArtUriString,
                isVideo = selection.isVideo
            )
        } else {
            song.copy(
                title = selection.title.ifBlank { song.title },
                artist = selection.artist.ifBlank { song.artist },
                uriString = "https://www.youtube.com/watch?v=${selection.videoId}",
                duration = selection.durationSec?.let { it * 1000L } ?: song.duration,
                albumArtUriString = customArt ?: selection.thumbnailUrl ?: song.albumArtUriString,
                isVideo = selection.isVideo
            )
        }

        if (selection.isVideo) {
            val self = MusicVideoCounterpart(selection.videoId, selection.videoId, "MUSIC_VIDEO_TYPE_OMV", null)
            storeCounterpart(updated, selection.videoId, self)
        }

        applyUpdatedSongState(context, song, updated, replaceMediaUri = true)
        preWarmVideoIfEligible(context, updated)

        val toastMsg = if (selection.keepOriginalMetadata) {
            "Switched YouTube stream to ${selection.videoId}"
        } else {
            "Matched to ${selection.title}"
        }
        Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
    }

    fun resetYouTubeMatchOverride(context: Context, song: Song) {
        if (isInListenTogetherRoom()) {
            Toast.makeText(context, "Changing YouTube match is disabled in Listen Together", Toast.LENGTH_SHORT).show()
            return
        }
        val baseId = song.originalId?.takeIf { it.isNotBlank() } ?: extractYouTubeIdFromUri(song.uriString) ?: return
        val prefs = context.getSharedPreferences("youtube_match_overrides", Context.MODE_PRIVATE)
        val origTitle = prefs.getString("orig_title_$baseId", null) ?: song.title
        val origArtist = prefs.getString("orig_artist_$baseId", null) ?: song.artist
        val origThumb = prefs.getString("orig_thumb_$baseId", null) ?: song.albumArtUriString
        val origIsVideo = prefs.getBoolean("orig_is_video_$baseId", false)

        prefs.edit()
            .remove("override_$baseId")
            .remove("title_$baseId")
            .remove("artist_$baseId")
            .remove("thumb_$baseId")
            .remove("orig_title_$baseId")
            .remove("orig_artist_$baseId")
            .remove("orig_thumb_$baseId")
            .remove("orig_is_video_$baseId")
            .apply()

        // 🧹 Clear in-memory caches so original song gets a fresh lookup
        songKeys(song, baseId).forEach { key ->
            counterpartMap.remove(key)
            videoUnavailableKeys.remove(key)
        }
        videoStreamCache.clear()
        preWarmedSongId = null
        preWarmJob?.cancel()
        releaseVideoPlayer()

        val updated = song.copy(
            title = origTitle,
            artist = origArtist,
            uriString = "https://www.youtube.com/watch?v=$baseId",
            albumArtUriString = origThumb,
            isVideo = origIsVideo
        )
        applyUpdatedSongState(context, song, updated, replaceMediaUri = true)
        preWarmVideoIfEligible(context, updated)
        Toast.makeText(context, "Restored original YouTube match", Toast.LENGTH_SHORT).show()
    }

    private val eqRepository by lazy {
        com.musicplayer.lytrix.eq.data.EQProfileRepository.getInstance(com.musicplayer.lytrix.LytrixApplication.instance)
    }

    val isEqEnabled: StateFlow<Boolean>
        get() = eqRepository.isEqEnabled
    val eqMode: StateFlow<com.musicplayer.lytrix.eq.data.EqMode>
        get() = eqRepository.eqMode
    val eqBandValues: StateFlow<List<Float>>
        get() = eqRepository.graphicBandValues
    val selectedPreset: StateFlow<String>
        get() = eqRepository.selectedPreset
    val eqProfiles: StateFlow<List<com.musicplayer.lytrix.eq.data.SavedEQProfile>>
        get() = eqRepository.profiles
    val activeEqProfile: StateFlow<com.musicplayer.lytrix.eq.data.SavedEQProfile?>
        get() = eqRepository.activeProfile

    fun toggleEqualizer(enabled: Boolean) {
        eqRepository.setEqEnabled(enabled)
        com.musicplayer.lytrix.eq.LytrixEqualizerService.syncFromRepository(eqRepository)
    }

    fun setEqMode(mode: com.musicplayer.lytrix.eq.data.EqMode) {
        eqRepository.setEqMode(mode)
        com.musicplayer.lytrix.eq.LytrixEqualizerService.syncFromRepository(eqRepository)
    }

    fun updateEqBand(index: Int, value: Float) {
        val currentValues = eqRepository.graphicBandValues.value.toMutableList()
        if (index in currentValues.indices) {
            currentValues[index] = value.coerceIn(-15f, 15f)
            eqRepository.saveGraphicBands(currentValues, "Custom")
            com.musicplayer.lytrix.eq.LytrixEqualizerService.syncFromRepository(eqRepository)
        }
    }

    fun applyPreset(presetName: String, levels: List<Float>) {
        eqRepository.saveGraphicBands(levels, presetName)
        com.musicplayer.lytrix.eq.LytrixEqualizerService.syncFromRepository(eqRepository)
    }

    fun selectEqProfile(profileId: String?) {
        if (profileId == null) {
            eqRepository.setActiveProfileSync(null)
        } else {
            eqRepository.setEqEnabled(true)
            eqRepository.setActiveProfileSync(profileId)
        }
        com.musicplayer.lytrix.eq.LytrixEqualizerService.syncFromRepository(eqRepository)
    }

    fun updateProfileBand(bandIndex: Int, value: Float) {
        eqRepository.updateActiveProfileBand(bandIndex, value.toDouble())
        com.musicplayer.lytrix.eq.LytrixEqualizerService.syncFromRepository(eqRepository)
    }

    fun updateProfilePreamp(value: Float) {
        eqRepository.updateActiveProfilePreamp(value.toDouble())
        com.musicplayer.lytrix.eq.LytrixEqualizerService.syncFromRepository(eqRepository)
    }

    fun deleteEqProfile(profileId: String) {
        viewModelScope.launch {
            eqRepository.deleteProfile(profileId)
            com.musicplayer.lytrix.eq.LytrixEqualizerService.syncFromRepository(eqRepository)
        }
    }

    fun saveAutoEqProfiles(profiles: List<com.musicplayer.lytrix.eq.data.SavedEQProfile>, activateFirst: Boolean = true) {
        viewModelScope.launch {
            eqRepository.saveProfiles(profiles)
            if (activateFirst && profiles.isNotEmpty()) {
                eqRepository.setEqEnabled(true)
                eqRepository.setActiveProfileSync(profiles.first().id)
            }
            com.musicplayer.lytrix.eq.LytrixEqualizerService.syncFromRepository(eqRepository)
        }
    }

    fun importCustomEqProfile(
        fileName: String,
        inputStream: java.io.InputStream,
        onSuccess: (com.musicplayer.lytrix.eq.data.SavedEQProfile) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val content = withContext(Dispatchers.IO) {
                    inputStream.bufferedReader().use { it.readText() }
                }
                val parsed = com.musicplayer.lytrix.eq.data.ParametricEQParser.parseText(content)
                val errors = com.musicplayer.lytrix.eq.data.ParametricEQParser.validate(parsed)
                if (errors.isNotEmpty()) {
                    onError(errors.first())
                    return@launch
                }
                val cleanName = fileName.removeSuffix(".txt").removeSuffix(" ParametricEQ").trim().ifBlank { "Custom EQ" }
                val saved = eqRepository.importCustomProfile(cleanName, parsed)
                eqRepository.setEqEnabled(true)
                eqRepository.setActiveProfileSync(saved.id)
                com.musicplayer.lytrix.eq.LytrixEqualizerService.syncFromRepository(eqRepository)
                onSuccess(saved)
            } catch (e: Exception) {
                onError(e.message ?: "Failed to parse EQ profile")
            }
        }
    }

    // --- ONLINE MUSIC INTEGRATION ---

    private fun com.musicplayer.lytrix.model.JioSaavnSong.toAppSong(context: android.content.Context?): Song {
        val uniqueId = this.id.hashCode().toLong()
        var finalUri = this.downloadUrl
        var isOffline = false
        if (context != null) {
            // 🔥 Magic Fix: Use safeName to detect offline playback!
            val safeName = this.name.replace(Regex("[^a-zA-Z0-9 ]"), "_").take(50).trim()
            val localFile = java.io.File(
                context.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC),
                "$safeName.mp3"
            )
            if (localFile.exists()) {
                finalUri = "file://" + localFile.absolutePath
                isOffline = true
            }
        }

        val isYtSong = !this.imageUrl.contains("saavn", ignoreCase = true)
        if (!isOffline && finalUri.isBlank() && isYtSong && this.id.isNotBlank()) {
            finalUri = "https://www.youtube.com/watch?v=" + this.id
        }

        return Song(
            id = uniqueId,
            title = this.name,
            artist = this.artist,
            album = if (isOffline) "Offline Downloaded" else "Online Stream",
            duration = 0L,
            uriString = finalUri,
            filePath = if (isOffline) "OFFLINE" else if (isYtSong) "ONLINE_LYTRIX" else "ONLINE",
            albumArtUriString = this.imageUrl,
            isVideo = false,
            hasLyrics = false,
            originalId = this.id // 🔥 আসল আইডি সেভ
        )
    }

    fun playOnlineSong(
        selectedSong: com.musicplayer.lytrix.model.JioSaavnSong,
        currentOnlineList: List<com.musicplayer.lytrix.model.JioSaavnSong>
    ) {
        if (shouldBlockGuestPlayback()) {
            Toast.makeText(appContext ?: com.musicplayer.lytrix.LytrixApplication.instance, "Only the host can control playback in Listen Together", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            appContext?.getSharedPreferences("app_preferences", android.content.Context.MODE_PRIVATE)
                ?.edit()?.putBoolean("is_video_playing", false)?.apply()
        } catch (e: Exception) {}
        val context = appContext ?: return
        val appSongs = currentOnlineList.map { it.toAppSong(context) }
        val songToPlay = selectedSong.toAppSong(context)
        // 🔥 ম্যাজিক লজিক: চেক করছে গানটা ইউটিউবের কি না এবং লোকাল মেমোরিতে আছে কি না
        val isYtSong = !selectedSong.imageUrl.contains("saavn", ignoreCase = true)
        val isOffline = songToPlay.filePath == "OFFLINE"
        if (isYtSong && !isOffline) {
            // গানটি ইউটিউবের এবং ডাউনলোড করা নেই -> তাই NewPipe দিয়ে ফ্রেশ লিংক আনবে
            val ytList = currentOnlineList.map { PlayerViewModel.YtMusicTrack(it.id, it.name, it.artist, it.imageUrl) }
            val index = ytList.indexOfFirst { it.id == selectedSong.id }
            playLytrixPlaylist(ytList, if (index != -1) index else 0, context)
        } else {
            // গানটি ডাউনলোড করা আছে অথবা জিওসাভনের -> সরাসরি প্লে হবে (ইন্টারনেট ছাড়া)
            playSong(songToPlay, appSongs)
        }
    }

    fun toggleOnlineFavorite(
        context: android.content.Context,
        song: com.musicplayer.lytrix.model.JioSaavnSong
    ) {
        val prefs =
            context.getSharedPreferences("online_liked_songs", android.content.Context.MODE_PRIVATE)
        val oldSet = prefs.getStringSet("liked_ids", emptySet()) ?: emptySet()
        val newSet = java.util.HashSet<String>(oldSet)
        val songIdStr = song.id
        if (newSet.contains(songIdStr)) {
            newSet.remove(songIdStr)
            _onlineLikedIds.value = newSet.toSet()
            prefs.edit()
                .remove("song_data_$songIdStr")
                .putStringSet("liked_ids", newSet)
                .apply()
            android.widget.Toast.makeText(
                context,
                "Removed from Cloud Likes",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            performSilentSync(context)
        } else {
            newSet.add(songIdStr)
            _onlineLikedIds.value = newSet.toSet()
            val jsonObj = org.json.JSONObject()
            jsonObj.put("id", songIdStr)
            jsonObj.put("title", song.name)
            jsonObj.put("name", song.name)
            jsonObj.put("subtitle", song.artist)
            jsonObj.put("artist", song.artist)
            jsonObj.put("image", song.imageUrl)
            jsonObj.put("imageUrl", song.imageUrl)
            jsonObj.put("image_url", song.imageUrl)
            // 🔥 ম্যাজিক ফিক্স: YT গান হলে লিংক ফাঁকা সেভ হবে!
            val isYtSong = !song.imageUrl.contains("saavn", ignoreCase = true)
            val finalUrlToSave = if (isYtSong) "" else song.downloadUrl
            jsonObj.put("url", finalUrlToSave)
            jsonObj.put("downloadUrl", finalUrlToSave)
            jsonObj.put("stream_url", finalUrlToSave)
            prefs.edit()
                .putString("song_data_$songIdStr", jsonObj.toString())
                .putStringSet("liked_ids", newSet)
                .apply()
            android.widget.Toast.makeText(
                context,
                "Saved to Cloud Likes",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            performSilentSync(context)
        }
    }

    // --- PODCAST INTEGRATION ---
    private val _isPodcastLoading = MutableStateFlow(false)
    val isPodcastLoading: StateFlow<Boolean> = _isPodcastLoading.asStateFlow()
    private val _podcastSearchResults =
        MutableStateFlow<List<com.musicplayer.lytrix.PodcastEpisode>>(emptyList())
    val podcastSearchResults: StateFlow<List<com.musicplayer.lytrix.PodcastEpisode>> =
        _podcastSearchResults.asStateFlow()

    fun searchPodcasts(query: String) {
        if (query.isBlank()) {
            _podcastSearchResults.value = emptyList()
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _isPodcastLoading.value = true
            try {
                val response =
                    com.musicplayer.lytrix.RetrofitInstance.podcastApi.searchPodcastsRaw(query)
                val parsedEpisodes = mutableListOf<com.musicplayer.lytrix.PodcastEpisode>()
                if (response.has("results") && response.get("results").isJsonArray) {
                    val resultsArray = response.getAsJsonArray("results")
                    for (i in 0 until resultsArray.size()) {
                        try {
                            val epObj = resultsArray.get(i).asJsonObject
                            val id =
                                if (epObj.has("id") && !epObj.get("id").isJsonNull) epObj.get("id").asString else ""
                            val title =
                                if (epObj.has("title_original") && !epObj.get("title_original").isJsonNull) epObj.get(
                                    "title_original"
                                ).asString else "Unknown"
                            val audio =
                                if (epObj.has("audio") && !epObj.get("audio").isJsonNull) epObj.get(
                                    "audio"
                                ).asString else ""
                            val image =
                                if (epObj.has("image") && !epObj.get("image").isJsonNull) epObj.get(
                                    "image"
                                ).asString else ""
                            val duration =
                                if (epObj.has("audio_length_sec") && !epObj.get("audio_length_sec").isJsonNull) epObj.get(
                                    "audio_length_sec"
                                ).asInt else 0
                            var publisher = "Unknown"
                            if (epObj.has("podcast") && epObj.get("podcast").isJsonObject) {
                                val podObj = epObj.getAsJsonObject("podcast")
                                if (podObj.has("publisher_original") && !podObj.get("publisher_original").isJsonNull) {
                                    publisher = podObj.get("publisher_original").asString
                                }
                            }

                            if (audio.isNotBlank()) {
                                parsedEpisodes.add(
                                    com.musicplayer.lytrix.PodcastEpisode(
                                        id = id,
                                        title_original = title,
                                        audio = audio,
                                        image = image,
                                        audio_length_sec = duration,
                                        podcast = com.musicplayer.lytrix.PodcastInfo(publisher)
                                    )
                                )
                            }

                        } catch (e: Exception) {
                            continue
                        }
                    }
                }

                _podcastSearchResults.value = parsedEpisodes
            } catch (e: Exception) {
                e.printStackTrace()
                _podcastSearchResults.value = emptyList()
            } finally {
                _isPodcastLoading.value = false
            }
        }
    }

    private fun com.musicplayer.lytrix.PodcastEpisode.toAppSong(
        context: android.content.Context?,
        isRadio: Boolean = false,
        isAudiobook: Boolean = false
    ): Song {
        val uniqueId = this.id?.hashCode()?.toLong() ?: 0L
        var finalUri = this.audio ?: ""
        var isOffline = false
        // রেডিও হলে ফাইল স্ক্যান করে সময় নষ্ট করবে না
        if (context != null && !isRadio) {
            var downloadId = ""
            try {
                val dlPrefsName = if (isAudiobook) "downloaded_audiobooks_meta" else "downloaded_meta"
                val prefs = context.getSharedPreferences(dlPrefsName, android.content.Context.MODE_PRIVATE)
                val allMap = prefs.all
                downloadId = allMap[this.id]?.toString() ?: allMap[uniqueId.toString()]?.toString() ?: ""
            } catch (e: Exception) {}
            val dirs = listOfNotNull(
                context.getExternalFilesDir(android.os.Environment.DIRECTORY_PODCASTS),
                context.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC)
            )
            for (dir in dirs) {
                if (!dir.exists()) continue
                val files = dir.listFiles() ?: continue
                for (file in files) {
                    val name = file.name
                    if (!name.endsWith(".mp3", ignoreCase = true)) continue
                    if (name.contains(this.id ?: "", ignoreCase = true) ||
                        name.contains(uniqueId.toString()) ||
                        (downloadId.isNotEmpty() && name.contains(downloadId)) ||
                        (this.title_original != null && this.title_original.length > 3 && name.contains(this.title_original, ignoreCase = true))
                    ) {
                        finalUri = "file://" + file.absolutePath
                        isOffline = true
                        break
                    }
                }

                if (isOffline) break
            }
        }

        val finalAlbum = when {
            isRadio -> "Live Radio"
            isAudiobook -> if (isOffline) "Offline Audiobook" else (this.podcast?.publisher_original ?: "Audiobook")
            isOffline -> "Offline Podcast"
            else -> (this.podcast?.publisher_original ?: "Podcast")
        }
        val finalFilePath = when {
            isRadio -> "ONLINE_RADIO"
            isAudiobook -> if (isOffline) "OFFLINE_AUDIOBOOK" else "ONLINE_AUDIOBOOK"
            isOffline -> "OFFLINE_PODCAST"
            else -> "ONLINE_PODCAST"
        }
        val finalMediaType = when {
            isRadio -> "RADIO"
            isAudiobook -> "AUDIOBOOK"
            else -> "PODCAST"
        }

        return Song(
            id = uniqueId,
            title = this.title_original ?: "Unknown",
            artist = this.podcast?.publisher_original ?: "Unknown",
            album = finalAlbum,
            duration = (this.audio_length_sec ?: 0) * 1000L,
            uriString = finalUri,
            filePath = finalFilePath,
            albumArtUriString = this.image,
            isVideo = false,
            hasLyrics = false,
            originalId = this.id,
            channelId = this.channelId ?: this.podcast?.channelId,
            mediaType = finalMediaType
        )
    }

    private fun saveToContinueListening(ep: com.musicplayer.lytrix.PodcastEpisode, isAudiobook: Boolean) {
        val ctx = appContext ?: return
        try {
            val prefName = if (isAudiobook) "online_ab_Continue Listening" else "online_podcast_Continue Listening"
            val prefs = ctx.getSharedPreferences(prefName, Context.MODE_PRIVATE)
            val json = org.json.JSONObject().apply {
                put("id", ep.id)
                put("name", ep.title_original)
                put("title", ep.title_original)
                put("artist", ep.podcast?.publisher_original ?: "")
                put("publisher", ep.podcast?.publisher_original ?: "")
                put("imageUrl", ep.image ?: "")
                put("downloadUrl", ep.audio ?: "")
                put("album", "Continue Listening")
                ep.channelId?.let { put("channelId", it) }
                put("timestamp", System.currentTimeMillis())
            }
            prefs.edit().putString(ep.id, json.toString()).apply()
        } catch (_: Exception) {}
    }

    fun playPodcast(
        selectedEpisode: com.musicplayer.lytrix.PodcastEpisode,
        currentPodcastList: List<com.musicplayer.lytrix.PodcastEpisode>,
        isRadio: Boolean = false
    ) {
        if (!isRadio) {
            setMediaCategory("PODCAST")
            saveToContinueListening(selectedEpisode, isAudiobook = false)
        }
        try {
            appContext?.getSharedPreferences(
                "app_preferences",
                android.content.Context.MODE_PRIVATE
            )
                ?.edit()?.putBoolean("is_video_playing", false)?.apply()
        } catch (e: Exception) {
        }

        val context = appContext
        val appSongs = currentPodcastList.filter { !it.audio.isNullOrBlank() }.map { it.toAppSong(context, isRadio = isRadio, isAudiobook = false) }
        val songToPlay = selectedEpisode.toAppSong(context, isRadio = isRadio, isAudiobook = false)
        playSong(songToPlay, appSongs)
    }

    fun playAudiobook(
        selectedChapter: com.musicplayer.lytrix.PodcastEpisode,
        currentChapterList: List<com.musicplayer.lytrix.PodcastEpisode>
    ) {
        setMediaCategory("AUDIOBOOK")
        saveToContinueListening(selectedChapter, isAudiobook = true)
        try {
            appContext?.getSharedPreferences(
                "app_preferences",
                android.content.Context.MODE_PRIVATE
            )
                ?.edit()?.putBoolean("is_video_playing", false)?.apply()
        } catch (e: Exception) {
        }

        val context = appContext
        val appSongs = currentChapterList.filter { !it.audio.isNullOrBlank() }.map { it.toAppSong(context, isRadio = false, isAudiobook = true) }
        val songToPlay = selectedChapter.toAppSong(context, isRadio = false, isAudiobook = true)
        playSong(songToPlay, appSongs)
    }

    fun removeCustomAlbumArt(context: Context, songId: Long) {
        val target = _songsList.value.find { it.id == songId }
            ?: _currentQueueList.value.find { it.id == songId }
            ?: _currentSong.value?.takeIf { it.id == songId }
            ?: Song(songId, "", "", "", 0L, "")
        removeCustomAlbumArt(context, target)
    }

    fun removeCustomAlbumArt(context: Context, song: Song) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val artKey = songCustomArtKey(song)
                val safePrefix = "custom_album_art_${artKey.replace(Regex("[^a-zA-Z0-9_-]"), "_")}"
                context.filesDir.listFiles()?.forEach { f ->
                    if (f.name.startsWith(safePrefix) || f.name.startsWith("custom_album_art_${song.id}")) {
                        f.delete()
                    }
                }

                val prefs = context.getSharedPreferences("custom_album_arts", Context.MODE_PRIVATE)
                val restoredOrig = prefs.getString("orig_$artKey", null)
                prefs.edit()
                    .remove(artKey)
                    .remove(song.id.toString())
                    .remove("orig_$artKey")
                    .apply()
                withContext(Dispatchers.Main) {
                    applyUpdatedSongState(context, song, song.copy(albumArtUriString = restoredOrig))
                    Toast.makeText(context, "Cover restored to original", Toast.LENGTH_SHORT).show()
                }

                val isOnline = song.filePath in listOf("ONLINE", "ONLINE_LYTRIX", "ONLINE_PODCAST", "OFFLINE_PODCAST")
                if (!isOnline) {
                    loadAndScanSongs(context)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ==========================================
    // 🔥 Hidden Files Logic

    // ==========================================
    private val _hiddenSongsDetailed = MutableStateFlow<List<Song>>(emptyList())
    val hiddenSongsDetailed: StateFlow<List<Song>> = _hiddenSongsDetailed.asStateFlow()

    fun loadHiddenSongsDetailed() {
        val context = appContext ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val hiddenIds = _hiddenSongIds.value
                val detailedList = mutableListOf<Song>()
                val scanner = SongScanner(context)
                val audioIds = hiddenIds.filter { it >= 0 }
                val recoveredAudioSongs = scanner.scanHiddenSongs(audioIds)
                detailedList.addAll(recoveredAudioSongs)
                val videoIds = hiddenIds.filter { it < 0 }.map { -it }
                if (videoIds.isNotEmpty()) {
                    val videoSelection = "${android.provider.MediaStore.Video.Media._ID} IN (${
                        videoIds.joinToString(",")
                    })"
                    context.contentResolver.query(
                        android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        arrayOf(
                            android.provider.MediaStore.Video.Media._ID,
                            android.provider.MediaStore.Video.Media.TITLE,
                            android.provider.MediaStore.Video.Media.DURATION,
                            android.provider.MediaStore.Video.Media.DATA
                        ),
                        videoSelection, null, null
                    )?.use { cursor ->
                        while (cursor.moveToNext()) {
                            val id = cursor.getLong(0)
                            val title = cursor.getString(1) ?: "Unknown Video"
                            val duration = cursor.getLong(2)
                            val data = cursor.getString(3)
                            val uri = android.content.ContentUris.withAppendedId(
                                android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                                id
                            ).toString()
                            detailedList.add(
                                Song(
                                    id = -id,
                                    title = title,
                                    artist = "Video File",
                                    album = "Hidden Video",
                                    duration = duration,
                                    uriString = uri,
                                    filePath = data,
                                    albumArtUriString = uri,
                                    isVideo = true,
                                    hasLyrics = false
                                )
                            )
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    _hiddenSongsDetailed.value = detailedList
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun hideSong(songId: Long) {
        val context = appContext ?: return
        _songsList.value = _songsList.value.filter { it.id != songId }
        if (!_hiddenSongIds.value.contains(songId)) {
            val newList = _hiddenSongIds.value.toMutableList()
            newList.add(songId)
            _hiddenSongIds.value = newList
        }

        viewModelScope.launch(Dispatchers.IO) {
            val db = com.musicplayer.lytrix.db.AppDatabase.getDatabase(context)
            db.hiddenSongDao().insertHiddenSong(com.musicplayer.lytrix.db.HiddenSongEntity(songId))
            loadHiddenSongsDetailed()
        }
    }

    fun unhideSong(songId: Long) {
        val context = appContext ?: return
        _hiddenSongIds.value = _hiddenSongIds.value.filter { it != songId }
        _hiddenSongsDetailed.value = _hiddenSongsDetailed.value.filter { it.id != songId }
        viewModelScope.launch(Dispatchers.IO) {
            val db = com.musicplayer.lytrix.db.AppDatabase.getDatabase(context)
            db.hiddenSongDao().removeHiddenSong(songId)
            if (songId >= 0) {
                db.whitelistSongDao()
                    .insertWhitelistSong(com.musicplayer.lytrix.db.WhitelistSongEntity(songId))
            }

            withContext(Dispatchers.Main) {
                loadAndScanSongs(context)
            }
        }
    }

    fun playExternalFile(context: Context, uri: android.net.Uri) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            var title = "Unknown Media"
            var isVideoFile = false
            var albumArtPath: String? = null
            val mimeType = context.contentResolver.getType(uri)
            if (mimeType?.startsWith("video/") == true) {
                isVideoFile = true
            } else if (uri.scheme == "file") {
                val extension = java.io.File(uri.path ?: "").extension.lowercase()
                if (listOf("mp4", "mkv", "webm", "avi", "3gp").contains(extension)) {
                    isVideoFile = true
                }
            }

            if (uri.scheme == "content") {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex =
                        cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIndex != -1) {
                        title = cursor.getString(nameIndex).substringBeforeLast(".")
                    }
                }

            } else if (uri.scheme == "file") {
                title = java.io.File(uri.path ?: "").nameWithoutExtension
            }

            if (!isVideoFile) {
                try {
                    val retriever = android.media.MediaMetadataRetriever()
                    retriever.setDataSource(context, uri)
                    val artBytes = retriever.embeddedPicture
                    if (artBytes != null) {
                        val tempFile = java.io.File(
                            context.cacheDir,
                            "ext_thumb_${System.currentTimeMillis()}.jpg"
                        )
                        java.io.FileOutputStream(tempFile).use { it.write(artBytes) }
                        albumArtPath = "file://" + tempFile.absolutePath
                    }

                    retriever.release()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val externalSong = com.musicplayer.lytrix.model.Song(
                id = System.currentTimeMillis(),
                title = title,
                artist = "External File",
                album = "Local Media",
                duration = 0L,
                uriString = uri.toString(),
                filePath = uri.toString(),
                albumArtUriString = albumArtPath,
                isVideo = isVideoFile,
                hasLyrics = false
            )
            while (mediaController == null) {
                kotlinx.coroutines.delay(100)
            }

            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                playSong(externalSong, listOf(externalSong))
            }
        }
    }

    // ==========================================
    // 🔥 Online Music UI States & Network Check (For Lottie Errors)

    // ==========================================

    sealed class OnlineUiState {
        object Loading : OnlineUiState()
        object Success : OnlineUiState()

        data class Error(val message: String, val isNetworkError: Boolean) : OnlineUiState()
    }

    private val _exploreState = MutableStateFlow<OnlineUiState>(OnlineUiState.Loading)
    val exploreState: StateFlow<OnlineUiState> = _exploreState.asStateFlow()
    private val _podcastState = MutableStateFlow<OnlineUiState>(OnlineUiState.Loading)
    val podcastState: StateFlow<OnlineUiState> = _podcastState.asStateFlow()
    private val _audiobookState = MutableStateFlow<OnlineUiState>(OnlineUiState.Loading)
    val audiobookState: StateFlow<OnlineUiState> = _audiobookState.asStateFlow()
    private val _radioState = MutableStateFlow<OnlineUiState>(OnlineUiState.Loading)
    val radioState: StateFlow<OnlineUiState> = _radioState.asStateFlow()

    fun setExploreState(state: OnlineUiState) {
        _exploreState.value = state
    }

    fun setPodcastState(state: OnlineUiState) {
        _podcastState.value = state
    }

    fun setAudiobookState(state: OnlineUiState) {
        _audiobookState.value = state
    }

    fun setRadioState(state: OnlineUiState) {
        _radioState.value = state
    }

    fun checkNetworkStatus(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return activeNetwork.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
                activeNetwork.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)
    }

    // 🔥 ১. সার্চ ফাংশন
    private val _lytrixSearchResults = kotlinx.coroutines.flow.MutableStateFlow<List<com.musicplayer.lytrix.LytrixSongItem>>(emptyList())
    val lytrixSearchResults = _lytrixSearchResults.asStateFlow()

    fun searchLytrixSongs(query: String, context: android.content.Context) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                android.widget.Toast.makeText(context, "Searching YouTube...", android.widget.Toast.LENGTH_SHORT).show()
            }

            try {
                // NewPipe-কে ডাইরেক্ট ইউটিউব সার্চ করতে বলা হলো
                val searchExtractor = ServiceList.YouTube.getSearchExtractor(query)
                searchExtractor.fetchPage()
                val songs = mutableListOf<com.musicplayer.lytrix.LytrixSongItem>()
                // সার্চ রেজাল্ট থেকে একটা একটা করে গান নেওয়া হচ্ছে
                for (item in searchExtractor.initialPage.items) {
                    // আমরা শুধু ভিডিও/গান নেব, কোনো চ্যানেল বা প্লেলিস্ট নেববিধা
                    if (item is StreamInfoItem) {
                        // 🔥 বেস্ট কোয়ালিটি থাম্বনেইল বের করার লজিক
                        val highResThumb = item.thumbnails.maxByOrNull { it.width }?.url ?: ""
                        // 🔥 সেকেন্ডকে মিনিট:সেকেন্ড ফরম্যাটে (যেমন 3:45) কনভার্ট করা
                        val durationInSeconds = item.duration
                        val min = durationInSeconds / 60
                        val sec = durationInSeconds % 60
                        val durationStr = String.format("%02d:%02d", min, sec)
                        // ভিডিওর লিংক থেকে শুধু ID টা বের করা
                        val videoId = item.url.replace("https://www.youtube.com/watch?v=", "")
                        songs.add(
                            com.musicplayer.lytrix.LytrixSongItem(
                                videoId = videoId,
                                title = item.name,
                                artist = item.uploaderName, // ইউটিউব চ্যানেলের নামটা আর্টিস্ট হিসেবে যাবে
                                thumbnailUrl = highResThumb,
                                duration = durationStr
                            )
                        )
                    }
                }

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    _lytrixSearchResults.value = songs
                }

            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Search Error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ==========================================
    // 🔥 Search History & Suggestions Logic

    // ==========================================
    private val _searchHistory = kotlinx.coroutines.flow.MutableStateFlow<List<String>>(emptyList())
    val searchHistory = _searchHistory.asStateFlow()

    fun loadSearchHistory(context: Context) {
        val prefs = context.getSharedPreferences("yt_search_history", Context.MODE_PRIVATE)
        val history = prefs.getString("history", "")?.split("||")?.filter { it.isNotBlank() } ?: emptyList()
        _searchHistory.value = history
    }

    fun saveSearchQuery(context: Context, query: String) {
        if (query.isBlank()) return
        if (com.musicplayer.lytrix.ui.PrivacyPreferencesManager.isPauseSearchHistory(context)) return
        val prefs = context.getSharedPreferences("yt_search_history", Context.MODE_PRIVATE)
        val history = prefs.getString("history", "")?.split("||")?.filter { it.isNotBlank() }?.toMutableList() ?: mutableListOf()
        history.remove(query)
        history.add(0, query) // নতুন সার্চ সবসময় ওপরে থাকবে
        val newList = history.take(10) // সর্বোচ্চ ১০টা হিস্ট্রি সেভ রাখবে
        prefs.edit().putString("history", newList.joinToString("||")).apply()
        _searchHistory.value = newList
    }

    fun removeSearchQuery(context: Context, query: String) {
        val prefs = context.getSharedPreferences("yt_search_history", Context.MODE_PRIVATE)
        val history = prefs.getString("history", "")?.split("||")?.filter { it.isNotBlank() }?.toMutableList() ?: mutableListOf()
        history.remove(query)
        prefs.edit().putString("history", history.joinToString("||")).apply()
        _searchHistory.value = history
    }

    fun clearSearchHistory(context: Context) {
        val prefs = context.getSharedPreferences("yt_search_history", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        _searchHistory.value = emptyList()
    }

    fun clearOnlineListenHistory(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val db = com.musicplayer.lytrix.db.AppDatabase.getDatabase(context)
                db.onlineHistoryDao().clearOnlineHistory()
                val prefs = context.getSharedPreferences("yt_history_db", Context.MODE_PRIVATE)
                prefs.edit().clear().apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            clearAllNotInterested()
            loadSpeedDial()
        }
    }

    fun trackOnlineHistory(
        songId: String,
        title: String,
        artist: String,
        imageUrl: String,
        source: String,
        duration: Long = 0L,
        uriString: String = ""
    ) {
        val context = appContext ?: return
        if (com.musicplayer.lytrix.ui.PrivacyPreferencesManager.isPauseListenHistory(context)) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val db = com.musicplayer.lytrix.db.AppDatabase.getDatabase(context)
                val dao = db.onlineHistoryDao()
                // Preserve every play session row so play counts & Wrapped stats count repeated plays accurately.
                // History UI deduplicates by songId via distinctBy { it.songId }.
                val rowId = dao.insertOnlineHistory(
                    com.musicplayer.lytrix.db.OnlineHistoryEntity(
                        songId = songId,
                        title = title,
                        artist = artist,
                        imageUrl = imageUrl,
                        source = source,
                        timestamp = System.currentTimeMillis(),
                        duration = duration,
                        uriString = uriString
                    )
                )
                lastOnlineHistoryRowId = rowId
                lastOnlineHistorySongId = songId
                if (duration > 0L) {
                    dao.backfillMissingDuration(songId, duration)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteOnlineHistoryItem(id: Long) {
        val context = appContext ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val db = com.musicplayer.lytrix.db.AppDatabase.getDatabase(context)
                db.onlineHistoryDao().deleteHistoryItem(id)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteOnlineHistoryBySongId(songId: String) {
        val context = appContext ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val db = com.musicplayer.lytrix.db.AppDatabase.getDatabase(context)
                db.onlineHistoryDao().deleteHistoryBySongId(songId)
                val prefs = context.getSharedPreferences("yt_history_db", Context.MODE_PRIVATE)
                val historyJson = prefs.getString("history_list", "[]") ?: "[]"
                val jsonArray = org.json.JSONArray(historyJson)
                val filtered = org.json.JSONArray()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.optJSONObject(i) ?: continue
                    if (obj.optString("id") != songId) {
                        filtered.put(obj)
                    }
                }
                prefs.edit().putString("history_list", filtered.toString()).apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private val _remoteHistoryPage = kotlinx.coroutines.flow.MutableStateFlow<com.musicplayer.lytrix.innertube.pages.HistoryPage?>(null)
    val remoteHistoryPage = _remoteHistoryPage.asStateFlow()
    private val _isRemoteHistoryLoading = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isRemoteHistoryLoading = _isRemoteHistoryLoading.asStateFlow()

    fun fetchRemoteHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            _isRemoteHistoryLoading.value = true
            try {
                val result = com.musicplayer.lytrix.innertube.YouTube.musicHistory().getOrNull()
                _remoteHistoryPage.value = result
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isRemoteHistoryLoading.value = false
            }
        }
    }

    fun clearAllListenHistory(context: Context) {
        clearOnlineListenHistory(context)
    }

    fun clearLocalListenHistory(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val db = com.musicplayer.lytrix.db.AppDatabase.getDatabase(context)
                db.songMetadataDao().clearListenHistory()
                db.playtimeDao().clearPlaytimeStats()
                db.audioHistoryDao().clearAudioHistory()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            kotlinx.coroutines.withContext(Dispatchers.Main) {
                _recentSongIds.value = emptyList()
                _mostPlayedSongIds.value = emptyList()
                _mostPlayedSongIdsForSort.value = emptyList()
                _playCounts.value = emptyMap()
            }
        }
    }

    // ==========================================
    // 🔥 InnerTube Search Suggestions & Filtered Results

    // ==========================================
    private val _searchSuggestions = kotlinx.coroutines.flow.MutableStateFlow<List<String>>(emptyList())
    val searchSuggestions = _searchSuggestions.asStateFlow()
    private val _innerTubeSearchResults = kotlinx.coroutines.flow.MutableStateFlow<List<YtMusicSection>>(emptyList())
    val innerTubeSearchResults = _innerTubeSearchResults.asStateFlow()

    fun fetchSearchSuggestions(query: String) {
        if (query.isBlank()) {
            _searchSuggestions.value = emptyList()
            return
        }

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val result = com.musicplayer.lytrix.innertube.YouTube.searchSuggestions(query).getOrNull()
                _searchSuggestions.value = result?.queries ?: emptyList()
            } catch (e: Exception) {
                _searchSuggestions.value = emptyList()
            }
        }
    }

    fun searchInnerTube(query: String, filterContext: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _innerTubeSearchResults.value = emptyList()
            try {
                if (filterContext == "All") {
                    val result = com.musicplayer.lytrix.innertube.YouTube.searchSummary(query, useAccountContext = false).getOrNull()
                    val sections = result?.summaries?.map { summary ->
                        val isEpisodeSummary = summary.title.equals("Episodes", ignoreCase = true)
                        val isProfileSummary = summary.title.equals("Profiles", ignoreCase = true)
                        val tracks = summary.items.mapNotNull { item ->
                            when(item) {
                                is com.musicplayer.lytrix.innertube.models.SongItem -> {
                                    if (isEpisodeSummary) {
                                        val dur = item.duration?.let { android.text.format.DateUtils.formatElapsedTime(it.toLong()) } ?: ""
                                        YtMusicTrack(item.id, item.title, item.artists?.joinToString(", ") { it.name } ?: "Episode", item.thumbnail ?: "", dur, itemType = "Episode")
                                    } else {
                                        YtMusicTrack(item.id, item.title, item.artists?.joinToString(", ") { it.name } ?: "Unknown", item.thumbnail ?: "", item.viewCountText ?: "", itemType = "Song")
                                    }
                                }

                                is com.musicplayer.lytrix.innertube.models.AlbumItem -> {
                                    val author = item.artists?.joinToString(", ") { it.name }
                                    val albumText = if (!author.isNullOrBlank()) "Album • $author" else "Album"
                                    YtMusicTrack(item.browseId, item.title, albumText, item.thumbnail ?: "", itemType = "Album")
                                }

                                is com.musicplayer.lytrix.innertube.models.ArtistItem -> {
                                    val isProfile = item.isProfile || isProfileSummary
                                    val subtitle = if (isProfile) "Profile" else "Artist"
                                    val count = item.subscriberCountText ?: item.monthlyListenerCountText ?: ""
                                    YtMusicTrack(item.id, item.title, subtitle, item.thumbnail ?: "", count, itemType = if (isProfile) "Profile" else "Artist")
                                }

                                is com.musicplayer.lytrix.innertube.models.PlaylistItem -> {
                                    val authorName = item.author?.name
                                    val playlistText = if (!authorName.isNullOrBlank()) "Playlist • $authorName" else "Playlist"
                                    YtMusicTrack(item.id, item.title, playlistText, item.thumbnail ?: "", item.viewCountText ?: "", itemType = "Playlist")
                                }

                                is com.musicplayer.lytrix.innertube.models.VideoItem -> YtMusicTrack(item.id, item.title, cleanArtistDisplayName(item.authors?.joinToString(", ") { it.name } ?: "Unknown"), item.thumbnail ?: "", item.viewCountText ?: "", itemType = "Video")
                                is com.musicplayer.lytrix.innertube.models.PodcastItem -> {
                                    val authorName = item.author?.name
                                    val podcastText = if (!authorName.isNullOrBlank()) "Podcast • $authorName" else "Podcast"
                                    YtMusicTrack(item.id, item.title, podcastText, item.thumbnail ?: "", item.episodeCountText ?: "", itemType = "Podcast")
                                }

                                is com.musicplayer.lytrix.innertube.models.EpisodeItem -> {
                                    val rawAuthor = item.podcast?.name ?: item.author?.name ?: "Unknown"
                                    val authorName = cleanArtistDisplayName(rawAuthor)
                                    val durationStr = item.duration?.let { android.text.format.DateUtils.formatElapsedTime(it.toLong()) } ?: ""
                                    val meta = listOfNotNull(item.publishDateText, durationStr.takeIf { it.isNotBlank() }).joinToString(" • ")
                                    YtMusicTrack(item.id, item.title, authorName, item.thumbnail ?: "", meta, itemType = "Episode")
                                }
                                else -> null
                            }
                        }

                        YtMusicSection(summary.title, tracks)
                    } ?: emptyList()
                    _innerTubeSearchResults.value = sections
                } else {
                    val ytFilter = when(filterContext) {
                        "Songs" -> com.musicplayer.lytrix.innertube.YouTube.SearchFilter.FILTER_SONG
                        "Videos" -> com.musicplayer.lytrix.innertube.YouTube.SearchFilter.FILTER_VIDEO
                        "Albums" -> com.musicplayer.lytrix.innertube.YouTube.SearchFilter.FILTER_ALBUM
                        "Artists" -> com.musicplayer.lytrix.innertube.YouTube.SearchFilter.FILTER_ARTIST
                        "Featured Playlists", "Playlists" -> com.musicplayer.lytrix.innertube.YouTube.SearchFilter.FILTER_FEATURED_PLAYLIST
                        "Community playlists" -> com.musicplayer.lytrix.innertube.YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST
                        "Podcasts" -> com.musicplayer.lytrix.innertube.YouTube.SearchFilter.FILTER_PODCAST
                        "Episodes" -> com.musicplayer.lytrix.innertube.YouTube.SearchFilter.FILTER_EPISODE
                        "Profiles" -> com.musicplayer.lytrix.innertube.YouTube.SearchFilter.FILTER_PROFILE
                        else -> com.musicplayer.lytrix.innertube.YouTube.SearchFilter.FILTER_SONG
                    }

                    val result = com.musicplayer.lytrix.innertube.YouTube.search(query, ytFilter, useAccountContext = false).getOrNull()
                    val tracks = result?.items?.mapNotNull { item ->
                        when(item) {
                            is com.musicplayer.lytrix.innertube.models.SongItem -> {
                                if (filterContext.equals("Episodes", ignoreCase = true)) {
                                    val dur = item.duration?.let { android.text.format.DateUtils.formatElapsedTime(it.toLong()) } ?: ""
                                    YtMusicTrack(item.id, item.title, item.artists?.joinToString(", ") { it.name } ?: "Episode", item.thumbnail ?: "", dur, itemType = "Episode")
                                } else {
                                    YtMusicTrack(item.id, item.title, item.artists?.joinToString(", ") { it.name } ?: "Unknown", item.thumbnail ?: "", item.viewCountText ?: "", itemType = "Song")
                                }
                            }

                            is com.musicplayer.lytrix.innertube.models.AlbumItem -> {
                                val author = item.artists?.joinToString(", ") { it.name }
                                val albumText = if (!author.isNullOrBlank()) "Album • $author" else "Album"
                                YtMusicTrack(item.browseId, item.title, albumText, item.thumbnail ?: "", itemType = "Album")
                            }

                            is com.musicplayer.lytrix.innertube.models.ArtistItem -> {
                                val isProfile = item.isProfile || filterContext.equals("Profiles", ignoreCase = true)
                                val subtitle = if (isProfile) "Profile" else "Artist"
                                val count = item.subscriberCountText ?: item.monthlyListenerCountText ?: ""
                                YtMusicTrack(item.id, item.title, subtitle, item.thumbnail ?: "", count, itemType = if (isProfile) "Profile" else "Artist")
                            }

                            is com.musicplayer.lytrix.innertube.models.PlaylistItem -> {
                                val authorName = item.author?.name
                                val playlistText = if (!authorName.isNullOrBlank()) "Playlist • $authorName" else "Playlist"
                                YtMusicTrack(item.id, item.title, playlistText, item.thumbnail ?: "", item.viewCountText ?: "", itemType = "Playlist")
                            }

                            is com.musicplayer.lytrix.innertube.models.VideoItem -> YtMusicTrack(item.id, item.title, item.authors?.joinToString(", ") { it.name } ?: "Unknown", item.thumbnail ?: "", item.viewCountText ?: "", itemType = "Video")
                            is com.musicplayer.lytrix.innertube.models.PodcastItem -> {
                                val authorName = item.author?.name
                                val podcastText = if (!authorName.isNullOrBlank()) "Podcast • $authorName" else "Podcast"
                                YtMusicTrack(item.id, item.title, podcastText, item.thumbnail ?: "", item.episodeCountText ?: "", itemType = "Podcast")
                            }

                            is com.musicplayer.lytrix.innertube.models.EpisodeItem -> {
                                val rawAuthor = item.podcast?.name ?: item.author?.name ?: "Unknown"
                                val authorName = cleanArtistDisplayName(rawAuthor)
                                val durationStr = item.duration?.let { android.text.format.DateUtils.formatElapsedTime(it.toLong()) } ?: ""
                                val meta = listOfNotNull(item.publishDateText, durationStr.takeIf { it.isNotBlank() }).joinToString(" • ")
                                YtMusicTrack(item.id, item.title, authorName, item.thumbnail ?: "", meta, itemType = "Episode")
                            }
                            else -> null
                        }

                    } ?: emptyList()
                    _innerTubeSearchResults.value = listOf(YtMusicSection(filterContext, tracks))
                }

            } catch (e: Exception) {
                _innerTubeSearchResults.value = emptyList()
            }
        }
    }

    fun cleanArtistDisplayName(raw: String): String = com.musicplayer.lytrix.ui.cleanArtistDisplayName(raw)

    fun playFromYouTubeVideoId(videoId: String, context: android.content.Context, onMusicFound: (() -> Unit)? = null) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val playerResult = com.musicplayer.lytrix.innertube.YouTube.player(
                    videoId = videoId,
                    client = com.musicplayer.lytrix.innertube.models.YouTubeClient.WEB_REMIX
                ).getOrNull()

                val videoDetails = playerResult?.videoDetails
                val musicType = videoDetails?.musicVideoType

                // যদি গান না হয় (যেমন সাধারণ ভ্লগ/টিউটোরিয়াল/খবর), মেল্ডের মতো প্লে করা হবে না
                if (musicType.isNullOrBlank()) {
                    android.util.Log.d("PlayerViewModel", "Shared YouTube video ($videoId) is not a music track (musicVideoType=$musicType). Skipping.")
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "This YouTube link is not a music track", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val title = videoDetails.title?.ifBlank { "Unknown Title" } ?: "Unknown Title"
                val rawAuthor = videoDetails.author?.ifBlank { "YouTube Music" } ?: "YouTube Music"
                val cleanArtist = cleanArtistDisplayName(rawAuthor)
                val rawThumb = videoDetails.thumbnail?.thumbnails?.lastOrNull()?.url
                val thumbUrl = when {
                    rawThumb.isNullOrBlank() -> "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg"
                    rawThumb.contains("ytimg.com") && rawThumb.contains("/vi/") -> {
                        // YouTube-এর সার্ভার-সাইড ?sqp= জুম/ক্রপ প্যারামিটার বাদ দিয়ে আনক্রপড ১৬:৯ কভার লোড করা
                        rawThumb.substringBefore("?")
                            .replace("sddefault.jpg", "maxresdefault.jpg")
                            .replace("hqdefault.jpg", "maxresdefault.jpg")
                    }
                    rawThumb.contains("googleusercontent.com") || rawThumb.contains("ggpht.com") -> {
                        rawThumb.replace(Regex("=w\\d+-h\\d+"), "=w1080-h1080")
                            .replace(Regex("=s\\d+"), "=s1080")
                    }
                    else -> rawThumb.substringBefore("?")
                }
                val durationStr = videoDetails.lengthSeconds?.toLongOrNull()?.let {
                    android.text.format.DateUtils.formatElapsedTime(it)
                } ?: ""

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onMusicFound?.invoke()
                }

                val item = com.musicplayer.lytrix.LytrixSongItem(
                    videoId = videoId,
                    title = title,
                    artist = cleanArtist,
                    thumbnailUrl = thumbUrl,
                    duration = durationStr
                )
                playLytrixSong(item, context)
            } catch (e: Exception) {
                android.util.Log.e("PlayerViewModel", "Failed to play shared YouTube video $videoId", e)
            }
        }
    }

    fun playLytrixSong(
        songItem: com.musicplayer.lytrix.LytrixSongItem,
        context: android.content.Context,
        channelId: String? = null,
        mediaType: String? = null,
    ) {
        val cleanArtist = cleanArtistDisplayName(songItem.artist ?: "Unknown Artist")
        val isShowMedia = mediaType in listOf("PODCAST", "AUDIOBOOK") || currentMediaCategory.value in listOf("PODCAST", "AUDIOBOOK")
        if (!isShowMedia) {
            saveToYtHistory(YtMusicTrack(songItem.videoId ?: "", songItem.title ?: "Unknown", cleanArtist, songItem.thumbnailUrl ?: ""))
        }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // কোল্ড স্টার্টে মিডিয়া কন্ট্রোলার কানেক্ট হওয়া পর্যন্ত নিরাপদ অপেক্ষা (সর্বোচ্চ ৬ সেকেন্ড)
            if (mediaController == null) {
                kotlinx.coroutines.withTimeoutOrNull(6000L) {
                    isServiceConnected.first { it }
                }
            }

            // 🔥 Offline Playback Check
            val safeName = songItem.title?.replace(Regex("[^a-zA-Z0-9 ]"), "_")?.take(50)?.trim() ?: ""
            val localFile = java.io.File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC), "$safeName.mp3")
            if (localFile.exists()) {
                val appSong = Song(
                    id = songItem.videoId?.hashCode()?.toLong() ?: 0L, title = songItem.title ?: "Unknown Title", artist = cleanArtist,
                    album = if (mediaType == "PODCAST") "Offline Podcast" else if (mediaType == "AUDIOBOOK") "Offline Audiobook" else "Offline Downloaded",
                    duration = 0L, uriString = "file://" + localFile.absolutePath,
                    filePath = if (mediaType == "PODCAST") "OFFLINE_PODCAST" else if (mediaType == "AUDIOBOOK") "OFFLINE_AUDIOBOOK" else "OFFLINE",
                    albumArtUriString = songItem.thumbnailUrl ?: "", isVideo = false, hasLyrics = false,
                    originalId = songItem.videoId,
                    channelId = channelId,
                    mediaType = mediaType
                )
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { playSong(appSong, listOf(appSong)) }
                return@launch
            }

            val thumbUrl = songItem.thumbnailUrl ?: ""
            val rawId = songItem.videoId ?: ""
            val realId = if (thumbUrl.contains("ytimg.com/vi/")) {
                thumbUrl.substringAfter("vi/").substringBefore("/")
            } else {
                rawId
            }

            if (realId.isBlank()) return@launch
            // 🔥 JIT Magic: অরিজিনাল ইউটিউব লিংক (যাতে লিরিক্স কাজ করে)
            val fakeUri = "https://www.youtube.com/watch?v=$realId"
            val appSong = Song(
                id = realId.hashCode().toLong(), title = songItem.title ?: "Unknown Title", artist = cleanArtist,
                album = if (mediaType == "PODCAST") "Podcast" else if (mediaType == "AUDIOBOOK") "Audiobook" else "Lytrix Original",
                duration = 0L, uriString = fakeUri,
                filePath = if (mediaType == "PODCAST") "ONLINE_PODCAST" else if (mediaType == "AUDIOBOOK") "ONLINE_AUDIOBOOK" else "ONLINE_LYTRIX",
                albumArtUriString = songItem.thumbnailUrl ?: "",
                isVideo = false, hasLyrics = false, originalId = realId,
                channelId = channelId,
                mediaType = mediaType
            )
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                playSong(appSong, listOf(appSong))
            }
        }
    }

// ==========================================
    // 🔥 PURE YOUTUBE MUSIC MODELS & STATES (No JioSaavn)

    // ==========================================
    // ইউটিউবের জন্য সম্পূর্ণ আলাদা ডেটা ক্লাস

    data class YtMusicTrack(
        val id: String,
        val title: String,
        val artist: String,
        val thumbnailUrl: String,
        val playCount: String = "",
        val itemType: String = ""
    )

    data class YtMusicSection(
        val title: String,
        val tracks: List<YtMusicTrack>, // এখানে আর JioSaavnSong নেই!
        val subtitle: String? = null,
        val iconUrl: String? = null,
        val artistId: String? = null,
        val browseId: String? = null,
        val params: String? = null,
    )

    data class YtChipFeedGroup(
        val chipTitle: String,
        val chipParams: String?,
        val sections: List<YtMusicSection>,
    )

    private val _exploreChipFeeds = MutableStateFlow<List<YtChipFeedGroup>>(emptyList())
    val exploreChipFeeds: StateFlow<List<YtChipFeedGroup>> = _exploreChipFeeds.asStateFlow()
    private val _isLoadingExploreChipFeed = MutableStateFlow(false)
    val isLoadingExploreChipFeed: StateFlow<Boolean> = _isLoadingExploreChipFeed.asStateFlow()
    private val _hasMoreExploreChipFeeds = MutableStateFlow(false)
    val hasMoreExploreChipFeeds: StateFlow<Boolean> = _hasMoreExploreChipFeeds.asStateFlow()
    private val usedExploreChipKeys = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private var exploreChipFeedJob: kotlinx.coroutines.Job? = null

    private fun remainingExploreChips(): List<com.musicplayer.lytrix.innertube.pages.HomePage.Chip> {
        return _archiveToonMoods.value
            .filter { chip ->
                val p = chip.endpoint?.params
                !p.isNullOrBlank() &&
                    !chip.title.contains("Podcast", ignoreCase = true) &&
                    "${chip.title.lowercase()}|$p" !in usedExploreChipKeys
            }
            .distinctBy { it.endpoint?.params }
    }

    fun loadNextExploreChipFeed() {
        if (_isLoadingExploreChipFeed.value) return
        val nextChip = remainingExploreChips().firstOrNull()
        if (nextChip == null) {
            _hasMoreExploreChipFeeds.value = false
            return
        }
        val chipKey = "${nextChip.title.lowercase()}|${nextChip.endpoint?.params.orEmpty()}"
        usedExploreChipKeys.add(chipKey)
        _isLoadingExploreChipFeed.value = true
        exploreChipFeedJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val page = com.musicplayer.lytrix.innertube.YouTube.home(params = nextChip.endpoint?.params).getOrNull()
                val existingItemSets = (_exploreYtMusicSections.value + _exploreChipFeeds.value.flatMap { it.sections })
                    .map { sec -> sec.tracks.map { it.id }.toSet() }
                    .toMutableList()
                val notInterested = _notInterestedSongIds.value
                val parsed = page?.sections.orEmpty().mapNotNull { section ->
                    val tracks = section.items
                        .mapNotNull { it.toYtMusicTrack() }
                        .distinctBy { it.id }
                        .filterNot { notInterested.contains(it.id) }
                    if (tracks.isEmpty()) return@mapNotNull null
                    val idSet = tracks.map { it.id }.toSet()
                    if (existingItemSets.any { it == idSet }) return@mapNotNull null
                    existingItemSets.add(idSet)
                    YtMusicSection(
                        title = section.title,
                        tracks = tracks,
                        subtitle = section.label,
                        iconUrl = section.thumbnail,
                        artistId = section.endpoint?.takeIf { it.isArtistEndpoint }?.browseId,
                        browseId = section.endpoint?.browseId,
                        params = section.endpoint?.params,
                    )
                }
                if (parsed.isNotEmpty()) {
                    _exploreChipFeeds.value = _exploreChipFeeds.value + YtChipFeedGroup(
                        chipTitle = nextChip.title,
                        chipParams = nextChip.endpoint?.params,
                        sections = parsed,
                    )
                }
            } catch (e: Throwable) {
                android.util.Log.e("EXPLORE_DEBUG", "loadNextExploreChipFeed failed: ${e.message}", e)
            } finally {
                _isLoadingExploreChipFeed.value = false
                _hasMoreExploreChipFeeds.value = remainingExploreChips().isNotEmpty()
            }
        }
    }

    suspend fun fetchBrowseSectionTracks(browseId: String, params: String?): List<YtMusicTrack> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val browse = com.musicplayer.lytrix.innertube.YouTube.browse(browseId, params).getOrNull()
                val tracks = browse?.items.orEmpty()
                    .flatMap { it.items }
                    .mapNotNull { it.toYtMusicTrack() }
                    .distinctBy { it.id }
                if (tracks.isNotEmpty()) return@withContext tracks
                val homeFallback = com.musicplayer.lytrix.innertube.YouTube.home(params = params).getOrNull()
                homeFallback?.sections.orEmpty()
                    .flatMap { it.items }
                    .mapNotNull { it.toYtMusicTrack() }
                    .distinctBy { it.id }
            } catch (_: Throwable) {
                emptyList()
            }
        }

    data class FeaturedPlaylistCard(
        val id: String,
        val title: String,
        val subtitle: String,
        val thumbnailUrl: String,
        val previewSongs: List<YtMusicTrack> = emptyList()
    )
    private val _communityPlaylistSections = MutableStateFlow<List<YtMusicSection>>(emptyList())
    val communityPlaylistSections = _communityPlaylistSections.asStateFlow()
    private val _featuredCards = MutableStateFlow<List<FeaturedPlaylistCard>>(emptyList())
    val featuredCards = _featuredCards.asStateFlow()
    private val _archiveToonMoods = MutableStateFlow<List<com.musicplayer.lytrix.innertube.pages.HomePage.Chip>>(emptyList())
    val archiveToonMoods: StateFlow<List<com.musicplayer.lytrix.innertube.pages.HomePage.Chip>> = _archiveToonMoods

    fun loadArchiveToonData(chip: com.musicplayer.lytrix.innertube.pages.HomePage.Chip? = null) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                if (com.musicplayer.lytrix.innertube.YouTube.cookie.isNullOrBlank()) {
                    appContext?.let { ctx ->
                        try {
                            val pref = com.musicplayer.lytrix.ui.YoutubePreferencesManager(ctx)
                            val c = pref.cookie.first()
                            if (c.isNotBlank()) {
                                val vd = pref.visitorData.first()
                                val ds = pref.dataSyncId.first()
                                val au = pref.authUser.first()
                                val ul = pref.useLoginForBrowse.first()
                                com.musicplayer.lytrix.innertube.YouTube.authState = com.musicplayer.lytrix.innertube.PlaybackAuthState(
                                    cookie = c,
                                    visitorData = vd.ifBlank { null },
                                    dataSyncId = ds.ifBlank { null },
                                    authUser = au.ifBlank { null }
                                )
                                com.musicplayer.lytrix.innertube.YouTube.useLoginForBrowse = ul
                            }

                        } catch (_: Exception) {}
                    }
                }

                if (chip != null) {
                    _homeYtMusicSections.value = emptyList()
                } else {
                    _featuredCards.value = emptyList()
                    exploreChipFeedJob?.cancel()
                    usedExploreChipKeys.clear()
                    _exploreChipFeeds.value = emptyList()
                    _isLoadingExploreChipFeed.value = false
                    if (_exploreMoodSections.value.isEmpty() && _explorePageMoods.value.isEmpty()) {
                        loadExploreMoods()
                    }
                }

                if (chip?.title?.contains("Podcast", ignoreCase = true) == true && chip.endpoint?.params == null) {
                    // Fallback only when the chip has no YouTube params (e.g. guest or missing from initial cloud)
                    val podcastSections = mutableListOf<YtMusicSection>()
                    try {
                        val savedShows = com.musicplayer.lytrix.innertube.YouTube.savedPodcastShows().getOrNull()
                        val showTracks = savedShows?.mapNotNull { it.toYtMusicTrack() }?.filter { it.id != "SE" && it.id != "VLSE" }
                        if (!showTracks.isNullOrEmpty()) {
                            podcastSections.add(YtMusicSection("Your Shows", showTracks))
                        }

                        val laterEpisodes = com.musicplayer.lytrix.innertube.YouTube.episodesForLater().getOrNull()
                        val laterTracks = laterEpisodes?.mapNotNull { it.toYtMusicTrack() }
                        if (!laterTracks.isNullOrEmpty()) {
                            podcastSections.add(YtMusicSection("Episodes for Later", laterTracks))
                        }

                        val showsSearch = com.musicplayer.lytrix.innertube.YouTube.search(
                            "Podcasts",
                            com.musicplayer.lytrix.innertube.YouTube.SearchFilter.FILTER_PODCAST,
                            useAccountContext = false
                        ).getOrNull()
                        showsSearch?.items?.mapNotNull { it.toYtMusicTrack() }?.takeIf { it.isNotEmpty() }?.let {
                            podcastSections.add(YtMusicSection("Shows for you", it))
                        }

                        val episodesSearch = com.musicplayer.lytrix.innertube.YouTube.search(
                            "Episodes",
                            com.musicplayer.lytrix.innertube.YouTube.SearchFilter.FILTER_EPISODE,
                            useAccountContext = false
                        ).getOrNull()
                        episodesSearch?.items?.mapNotNull { it.toYtMusicTrack() }?.takeIf { it.isNotEmpty() }?.let {
                            podcastSections.add(YtMusicSection("Episodes for you", it))
                        }

                    } catch (_: Exception) {}
                    _homeYtMusicSections.value = podcastSections
                    return@launch
                }

                val result = com.musicplayer.lytrix.innertube.YouTube.home(params = chip?.endpoint?.params)
                val homePage = result.getOrNull()
                if (homePage != null) {
                    if (chip == null && homePage.chips != null) {
                        _archiveToonMoods.value = homePage.chips!!
                        _hasMoreExploreChipFeeds.value = remainingExploreChips().isNotEmpty()
                    }

                    val finalSections = mutableListOf<YtMusicSection>()
                    val localSections = getLocalYtSections()
                    val remoteSections = mutableListOf<YtMusicSection>()
                    var remoteHasQuickPicks = false
                    var remoteHasKeepListening = false
                    homePage.sections.forEach { section ->
                        if (section.title.contains("Quick pick", ignoreCase = true)) remoteHasQuickPicks = true
                        if (section.title.contains("Listen again", ignoreCase = true) ||
                            section.title.contains("Keep listening", ignoreCase = true) ||
                            section.endpoint?.browseId?.startsWith("FEmusic_listen_again") == true
                        ) {
                            remoteHasKeepListening = true
                        }
                        val sectionTracks = section.items.mapNotNull { ytItem ->
                            ytItem.toYtMusicTrack()
                        }.distinctBy { it.id }

                        if (sectionTracks.isNotEmpty()) {
                            remoteSections.add(
                                YtMusicSection(
                                    title = section.title,
                                    tracks = sectionTracks,
                                    subtitle = section.label,
                                    iconUrl = section.thumbnail,
                                    artistId = section.endpoint?.takeIf { it.isArtistEndpoint }?.browseId,
                                    browseId = section.endpoint?.browseId,
                                    params = section.endpoint?.params,
                                )
                            )
                        }
                    }

                    if (chip == null) {
                        if (!remoteHasKeepListening) {
                            localSections.find { it.title == "Keep listening" }?.let { finalSections.add(it) }
                        }
                        if (remoteHasQuickPicks) {
                            remoteSections.find { it.title.contains("Quick pick", ignoreCase = true) }?.let { finalSections.add(it) }
                        } else {
                            localSections.find { it.title == "Your Quick picks" || it.title == "Quick picks" }?.let { finalSections.add(it) }
                        }
                    }

                    val communitySections = mutableListOf<YtMusicSection>()
                    val standardRemoteSections = mutableListOf<YtMusicSection>()
                    remoteSections.forEach { section ->
                        val isCommunity = section.title.contains("community", ignoreCase = true)
                        if (isCommunity && section.tracks.isNotEmpty()) {
                            communitySections.add(section)
                        } else {
                            standardRemoteSections.add(section)
                        }
                    }

                    if (chip == null) {
                        _communityPlaylistSections.value = communitySections
                        val newFeaturedCards = communitySections.flatMap { it.tracks }.map { track ->
                            FeaturedPlaylistCard(
                                id = track.id,
                                title = track.title,
                                subtitle = track.playCount.takeIf { it.isNotBlank() } ?: track.artist,
                                thumbnailUrl = track.thumbnailUrl
                            )
                        }

                        _featuredCards.value = newFeaturedCards
                        if (newFeaturedCards.isNotEmpty()) {
                            loadPlaylistPreviews(newFeaturedCards)
                        }
                    }

                    finalSections.addAll(standardRemoteSections.filterNot { it.title.contains("Quick pick", ignoreCase = true) })
                    if (chip == null) {
                        // Ensure "Shows for you" is present in the main feed if user has saved podcast shows
                        val isAuth = com.musicplayer.lytrix.innertube.YouTube.hasLoginCookie() || !com.musicplayer.lytrix.innertube.YouTube.cookie.isNullOrBlank()
                        if (isAuth) {
                            try {
                                val savedShows = com.musicplayer.lytrix.innertube.YouTube.savedPodcastShows().getOrNull()
                                val showTracks = savedShows?.mapNotNull { it.toYtMusicTrack() }?.filter { it.id != "SE" && it.id != "VLSE" }
                                if (!showTracks.isNullOrEmpty()) {
                                    finalSections.removeAll { it.title.equals("Shows for you", ignoreCase = true) }
                                    val insertIdx = if (finalSections.size > 1) 1 else finalSections.size
                                    finalSections.add(insertIdx, YtMusicSection("Shows for you", showTracks))
                                }

                            } catch (_: Exception) {}
                        }
                    }

                    if (chip?.title?.contains("Podcast", ignoreCase = true) == true) {
                        try {
                            val laterEpisodes = com.musicplayer.lytrix.innertube.YouTube.episodesForLater().getOrNull()
                            val laterTracks = laterEpisodes?.mapNotNull { it.toYtMusicTrack() }
                            if (!laterTracks.isNullOrEmpty()) {
                                finalSections.add(0, YtMusicSection("Episodes for Later", laterTracks))
                            }

                        } catch (_: Exception) {}
                    }

                    val filteredSections = finalSections.map { sec ->
                        sec.copy(tracks = sec.tracks.filterNot { _notInterestedSongIds.value.contains(it.id) })
                    }.filter { it.tracks.isNotEmpty() }.toMutableList()
                    if (chip == null) {
                        _exploreYtMusicSections.value = filteredSections
                        loadExpandedExploreSections()
                    }
                    else _homeYtMusicSections.value = filteredSections
                    loadSpeedDial() // Refresh speed dial with home sections (Meld-style)
                    // 🔥 Fetch continuations dynamically (like YouTube Music & Meld)
                    var currentContinuation = homePage.continuation
                    var fetchCount = 0
                    while (currentContinuation != null && fetchCount < 5) { // up to 5 more pages
                        val nextResult = com.musicplayer.lytrix.innertube.YouTube.home(continuation = currentContinuation)
                        val nextHomePage = nextResult.getOrNull() ?: break
                        val moreCommunity = mutableListOf<YtMusicSection>()
                        val moreStandard = mutableListOf<YtMusicSection>()
                        nextHomePage.sections.forEach { section ->
                            val sectionTracks = section.items.mapNotNull { ytItem -> ytItem.toYtMusicTrack() }.distinctBy { it.id }
                            if (sectionTracks.isNotEmpty()) {
                                val sec = YtMusicSection(
                                    title = section.title,
                                    tracks = sectionTracks,
                                    subtitle = section.label,
                                    iconUrl = section.thumbnail,
                                    artistId = section.endpoint?.takeIf { it.isArtistEndpoint }?.browseId,
                                    browseId = section.endpoint?.browseId,
                                    params = section.endpoint?.params,
                                )
                                if (section.title.contains("community", ignoreCase = true)) {
                                    moreCommunity.add(sec)
                                } else {
                                    moreStandard.add(sec)
                                }
                            }
                        }

                        if (chip == null && moreCommunity.isNotEmpty()) {
                            val currentCards = _featuredCards.value.toMutableList()
                            val newCards = moreCommunity.flatMap { it.tracks }.map { track ->
                                FeaturedPlaylistCard(track.id, track.title, track.playCount.takeIf { it.isNotBlank() } ?: track.artist, track.thumbnailUrl)
                            }

                            if (newCards.isNotEmpty()) {
                                currentCards.addAll(newCards)
                                _featuredCards.value = currentCards
                                loadPlaylistPreviews(newCards)
                            }
                        }

                        if (moreStandard.isNotEmpty()) {
                            if (chip == null) {
                                val current = _exploreYtMusicSections.value
                                val existingTitles = current.map { it.title.trim().lowercase() }.toSet()
                                val deduped = moreStandard.filterNot { it.title.trim().lowercase() in existingTitles }
                                if (deduped.isNotEmpty()) {
                                    _exploreYtMusicSections.value = current + deduped
                                }
                            } else {
                                val current = _homeYtMusicSections.value
                                val existingTitles = current.map { it.title.trim().lowercase() }.toSet()
                                val deduped = moreStandard.filterNot { it.title.trim().lowercase() in existingTitles }
                                if (deduped.isNotEmpty()) {
                                    _homeYtMusicSections.value = current + deduped
                                }
                            }
                        }

                        currentContinuation = nextHomePage.continuation
                        fetchCount++
                    }
                }

            } catch (e: Throwable) { e.printStackTrace() } // 🔥 Throwable দেওয়া হলো যাতে ক্র্যাশ না করে
        }
    }

    // ==========================================
// 🔥 Explore ট্যাবের জন্য আরও সেকশন (লগইন ছাড়াই) — চার্টস, নতুন রিলিজ, Related

// ==========================================

    private fun com.musicplayer.lytrix.innertube.models.YTItem.toYtMusicTrack(): YtMusicTrack? = when (this) {
        is com.musicplayer.lytrix.innertube.models.SongItem -> {
            val validArtists = artists?.filterNot {
                val n = it.name.lowercase()
                n.contains("view") || n.contains("play") || n.contains("sub") || it.name.matches(Regex("""\d+[\d.,]*\s*[kmbKMB]?"""))
            }

            val artistName = validArtists?.joinToString(", ") { it.name }?.ifBlank { null }
                ?: artists?.firstOrNull()?.name
                ?: "Unknown"
            val dur = duration?.let { android.text.format.DateUtils.formatElapsedTime(it.toLong()) }
            val subtitle = if (dur != null && !viewCountText.isNullOrBlank()) "$viewCountText \u2022 $dur" else viewCountText ?: dur ?: ""
            YtMusicTrack(id, title, artistName, thumbnail ?: "", subtitle, itemType = "Song")
        }

        is com.musicplayer.lytrix.innertube.models.AlbumItem -> {
            val author = artists?.joinToString(", ") { it.name }
            val albumText = if (!author.isNullOrBlank()) "Album \u2022 $author" else "Album"
            YtMusicTrack(browseId, title, albumText, thumbnail ?: "", itemType = "Album")
        }

        is com.musicplayer.lytrix.innertube.models.ArtistItem -> {
            val subtitle = if (isProfile) "Profile" else "Artist"
            val count = subscriberCountText ?: monthlyListenerCountText ?: ""
            YtMusicTrack(id, title, subtitle, thumbnail ?: "", count, itemType = if (isProfile) "Profile" else "Artist")
        }

        is com.musicplayer.lytrix.innertube.models.PlaylistItem -> {
            val authorName = author?.name
            val playlistText = if (!authorName.isNullOrBlank()) "Playlist \u2022 $authorName" else "Playlist"
            YtMusicTrack(id, title, playlistText, thumbnail ?: "", songCountText ?: "", itemType = "Playlist")
        }

        is com.musicplayer.lytrix.innertube.models.PodcastItem -> {
            val authorName = author?.name?.let { cleanArtistDisplayName(it) }
            val podcastText = if (!authorName.isNullOrBlank()) "Podcast \u2022 $authorName" else "Podcast"
            YtMusicTrack(id, title, podcastText, thumbnail ?: "", episodeCountText ?: "", itemType = "Podcast")
        }

        is com.musicplayer.lytrix.innertube.models.VideoItem -> {
            val authorName = cleanArtistDisplayName(
                authors?.joinToString(", ") { it.name }?.ifBlank { null } ?: "Unknown"
            )
            val dur = duration?.let { android.text.format.DateUtils.formatElapsedTime(it.toLong()) }
            val subtitle = if (dur != null && !viewCountText.isNullOrBlank()) "$viewCountText \u2022 $dur" else viewCountText ?: dur ?: ""
            YtMusicTrack(id, title, authorName, thumbnail ?: "", subtitle, itemType = "Video")
        }

        is com.musicplayer.lytrix.innertube.models.EpisodeItem -> {
            val rawAuthor = author?.name ?: podcast?.name ?: "Unknown"
            val authorName = cleanArtistDisplayName(rawAuthor)
            val durationStr = duration?.let { android.text.format.DateUtils.formatElapsedTime(it.toLong()) } ?: ""
            val episodeMeta = listOfNotNull(publishDateText, durationStr.takeIf { it.isNotBlank() }).joinToString(" • ")
            YtMusicTrack(id, title, authorName, thumbnail ?: "", episodeMeta, itemType = "Episode")
        }
        else -> null
    }

    data class NowPlayingRelatedData(
        val artistName: String,
        val artistThumbnail: String?,
        val artistSubscribers: String?,
        val artistBrowseId: String?,
        val moreFromArtist: List<YtMusicTrack>,
        val similarTracks: List<YtMusicTrack>,
        val timestamp: Long = System.currentTimeMillis(),
        val songKey: String = ""
    )
    private val nowPlayingRelatedCache = java.util.concurrent.ConcurrentHashMap<String, NowPlayingRelatedData>()
    private val artistInfoCache = java.util.concurrent.ConcurrentHashMap<String, com.musicplayer.lytrix.innertube.models.ArtistItem>()
    private val CACHE_EXPIRATION_MS = 40 * 60 * 1000L // 40 minutes TTL
    private val _nowPlayingRelatedData = kotlinx.coroutines.flow.MutableStateFlow<NowPlayingRelatedData?>(null)
    val nowPlayingRelatedData: kotlinx.coroutines.flow.StateFlow<NowPlayingRelatedData?> = _nowPlayingRelatedData.asStateFlow()
    private val _isNowPlayingRelatedLoading = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isNowPlayingRelatedLoading: kotlinx.coroutines.flow.StateFlow<Boolean> = _isNowPlayingRelatedLoading.asStateFlow()
    private var nowPlayingRelatedJob: kotlinx.coroutines.Job? = null

    fun extractYouTubeVideoId(song: Song): String? {
        val orig = song.originalId?.trim()
        if (!orig.isNullOrBlank() && orig.length == 11 && !orig.contains(" ")) return orig
        if (song.uriString.contains("watch?v=")) {
            val id = song.uriString.substringAfter("watch?v=").substringBefore("&").substringBefore("?").trim()
            if (id.length == 11) return id
        }

        if (song.uriString.contains("youtu.be/")) {
            val id = song.uriString.substringAfter("youtu.be/").substringBefore("?").substringBefore("&").trim()
            if (id.length == 11) return id
        }

        val art = song.albumArtUriString
        if (art != null && art.contains("vi/")) {
            val id = art.substringAfter("vi/").substringBefore("/").trim()
            if (id.length == 11) return id
        }

        if (!orig.isNullOrBlank() && !orig.contains(" ") && orig.length in 10..15) return orig
        return null
    }

    fun loadNowPlayingRecommendations(song: Song, forceRefresh: Boolean = false) {
        val rawArtistName = song.artist.trim()
        val cleanPrimaryArtist = rawArtistName
            .split(",", "&", " feat.", " ft.", "/")
            .firstOrNull { it.isNotBlank() }
            ?.trim() ?: rawArtistName
        val currentSongTitle = song.title
        val songArt = song.albumArtUriString
        var videoId = extractYouTubeVideoId(song)
        val now = System.currentTimeMillis()
        if (!forceRefresh && !videoId.isNullOrBlank()) {
            val cached = nowPlayingRelatedCache[videoId]
            if (cached != null &&
                (now - cached.timestamp) < CACHE_EXPIRATION_MS &&
                (cached.moreFromArtist.isNotEmpty() || cached.similarTracks.isNotEmpty())
            ) {
                _nowPlayingRelatedData.value = cached.copy(songKey = song.id.toString())
                _isNowPlayingRelatedLoading.value = false
                return
            }
        }

        nowPlayingRelatedJob?.cancel()
        _isNowPlayingRelatedLoading.value = true
        _nowPlayingRelatedData.value = null
        nowPlayingRelatedJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                if (videoId.isNullOrBlank()) {
                    val searchResult = com.musicplayer.lytrix.innertube.YouTube.searchSummary("$currentSongTitle $cleanPrimaryArtist").getOrNull()
                    videoId = searchResult?.summaries?.flatMap { it.items }?.filterIsInstance<com.musicplayer.lytrix.innertube.models.SongItem>()?.firstOrNull()?.id
                }

                if (videoId.isNullOrBlank()) {
                    _isNowPlayingRelatedLoading.value = false
                    return@launch
                }

                val targetId = videoId!!
                val nextResult = com.musicplayer.lytrix.innertube.YouTube.next(
                    endpoint = com.musicplayer.lytrix.innertube.models.WatchEndpoint(videoId = targetId)
                ).getOrNull()
                // Extract primary artist metadata from the queue item or fallback to song's clean artist
                val matchedSongItem = nextResult?.items?.find { it.id == targetId }
                    ?: nextResult?.items?.getOrNull(nextResult.currentIndex ?: 0)
                val songPrimaryArtist = matchedSongItem?.artists?.firstOrNull()
                val targetArtistName = songPrimaryArtist?.name?.takeIf { it.isNotBlank() } ?: cleanPrimaryArtist
                var artistBrowseId = songPrimaryArtist?.id
                val relatedEndpoint = nextResult?.relatedEndpoint
                if (relatedEndpoint == null) {
                    val searchResult = com.musicplayer.lytrix.innertube.YouTube.searchSummary(targetArtistName).getOrNull()
                    val fallbackTracks = searchResult?.summaries?.flatMap { it.items }?.filterIsInstance<com.musicplayer.lytrix.innertube.models.SongItem>()?.map {
                        YtMusicTrack(
                            id = it.id,
                            title = it.title,
                            artist = it.artists?.joinToString(", ") { a -> a.name } ?: targetArtistName,
                            thumbnailUrl = it.thumbnail ?: ""
                        )
                    } ?: emptyList()
                    val matchedArtist = searchResult?.summaries?.flatMap { it.items }
                        ?.filterIsInstance<com.musicplayer.lytrix.innertube.models.ArtistItem>()
                        ?.firstOrNull { it.title.contains(targetArtistName, ignoreCase = true) || targetArtistName.contains(it.title, ignoreCase = true) }
                    val data = NowPlayingRelatedData(
                        artistName = targetArtistName,
                        artistThumbnail = matchedArtist?.thumbnail ?: songArt,
                        artistSubscribers = matchedArtist?.subscriberCountText ?: "Artist",
                        artistBrowseId = matchedArtist?.id,
                        moreFromArtist = fallbackTracks.take(8),
                        similarTracks = fallbackTracks.drop(8),
                        timestamp = System.currentTimeMillis(),
                        songKey = song.id.toString()
                    )
                    if (data.moreFromArtist.isNotEmpty() || data.similarTracks.isNotEmpty()) {
                        nowPlayingRelatedCache[targetId] = data
                    }
                    val activeSongId = _currentSong.value?.id
                    if (activeSongId == null || activeSongId == song.id) {
                        _nowPlayingRelatedData.value = data
                    }
                    _isNowPlayingRelatedLoading.value = false
                    return@launch
                }

                // Check artist info cache first
                var cachedArtistItem = if (!artistBrowseId.isNullOrBlank()) artistInfoCache[artistBrowseId] else null
                if (cachedArtistItem == null) {
                    cachedArtistItem = artistInfoCache[targetArtistName.lowercase()]
                }

                // Fetch related recommendations
                val relatedResult = com.musicplayer.lytrix.innertube.YouTube.nowPlayingRelated(
                    endpoint = relatedEndpoint,
                    targetArtistName = targetArtistName
                ).getOrNull()
                if (artistBrowseId.isNullOrBlank()) {
                    artistBrowseId = relatedResult?.moreFromArtist?.firstOrNull()?.artists?.firstOrNull { it.id != null }?.id
                }

                // Resolve artist profile
                var resolvedArtistItem = if (cachedArtistItem != null) {
                    cachedArtistItem
                } else if (!artistBrowseId.isNullOrBlank() && (artistBrowseId!!.startsWith("UC") || artistBrowseId!!.startsWith("FE"))) {
                    com.musicplayer.lytrix.innertube.YouTube.artist(artistBrowseId!!).getOrNull()?.artist
                } else null
                // If still null, check if relatedResult found a matching artist (not from similar shelf)
                if (resolvedArtistItem == null && relatedResult?.artist != null) {
                    val rArtist = relatedResult.artist
                    if (rArtist.title.contains(targetArtistName, ignoreCase = true) || targetArtistName.contains(rArtist.title, ignoreCase = true)) {
                        resolvedArtistItem = rArtist
                    }
                }

                // Fallback to quick search summary query if thumbnail is still missing
                if (resolvedArtistItem == null || resolvedArtistItem.thumbnail.isNullOrBlank()) {
                    val searchResult = com.musicplayer.lytrix.innertube.YouTube.searchSummary(targetArtistName).getOrNull()
                    val searchArtist = searchResult?.summaries?.flatMap { it.items }
                        ?.filterIsInstance<com.musicplayer.lytrix.innertube.models.ArtistItem>()
                        ?.firstOrNull {
                            it.title.contains(targetArtistName, ignoreCase = true) ||
                            targetArtistName.contains(it.title, ignoreCase = true)
                        }

                    if (searchArtist != null) {
                        resolvedArtistItem = searchArtist
                        if (artistBrowseId.isNullOrBlank()) {
                            artistBrowseId = searchArtist.id
                        }
                    }
                }

                // Store resolved artist in cache
                if (resolvedArtistItem != null) {
                    if (!artistBrowseId.isNullOrBlank()) {
                        artistInfoCache[artistBrowseId!!] = resolvedArtistItem
                    }

                    artistInfoCache[targetArtistName.lowercase()] = resolvedArtistItem
                }

                // Exclude the currently playing song from recommendations
                var moreTracks = relatedResult?.moreFromArtist
                    ?.filter { it.id != targetId }
                    ?.map { songItem ->
                        YtMusicTrack(
                            id = songItem.id,
                            title = songItem.title,
                            artist = songItem.artists?.joinToString(", ") { it.name } ?: targetArtistName,
                            thumbnailUrl = songItem.thumbnail ?: ""
                        )
                    } ?: emptyList()
                var similarTracks = relatedResult?.similarSongs
                    ?.filter { it.id != targetId }
                    ?.map { songItem ->
                        YtMusicTrack(
                            id = songItem.id,
                            title = songItem.title,
                            artist = songItem.artists?.joinToString(", ") { it.name } ?: "Unknown",
                            thumbnailUrl = songItem.thumbnail ?: ""
                        )
                    } ?: emptyList()
                // If similarTracks is empty, supplement from nextResult queue (automix radio recommendations)
                if (similarTracks.isEmpty() && nextResult?.items != null) {
                    val queueItems = nextResult.items
                        .filter { it.id != targetId }
                        .map { songItem ->
                            YtMusicTrack(
                                id = songItem.id,
                                title = songItem.title,
                                artist = songItem.artists?.joinToString(", ") { it.name } ?: "Unknown",
                                thumbnailUrl = songItem.thumbnail ?: ""
                            )
                        }

                    if (queueItems.isNotEmpty()) {
                        similarTracks = queueItems
                    }
                }

                if (moreTracks.isEmpty() && similarTracks.isNotEmpty()) {
                    val cleanTarget = targetArtistName.lowercase().trim()
                    val (artistSongs, otherSongs) = similarTracks.partition { track ->
                        val cleanSongArtist = track.artist.lowercase().trim()
                        cleanSongArtist.contains(cleanTarget) || cleanTarget.contains(cleanSongArtist)
                    }

                    if (artistSongs.isNotEmpty()) {
                        moreTracks = artistSongs
                        similarTracks = otherSongs
                    }
                }

                if (moreTracks.isEmpty()) {
                    val searchResult = com.musicplayer.lytrix.innertube.YouTube.searchSummary(targetArtistName).getOrNull()
                    val searchSongs = searchResult?.summaries?.flatMap { it.items }
                        ?.filterIsInstance<com.musicplayer.lytrix.innertube.models.SongItem>()
                        ?.filter { it.id != targetId }
                        ?.map {
                            YtMusicTrack(
                                id = it.id,
                                title = it.title,
                                artist = it.artists?.joinToString(", ") { a -> a.name } ?: targetArtistName,
                                thumbnailUrl = it.thumbnail ?: ""
                            )
                        } ?: emptyList()
                    if (searchSongs.isNotEmpty()) {
                        moreTracks = searchSongs.take(10)
                    }
                }

                val finalArtistThumbnail = resolvedArtistItem?.thumbnail?.takeIf { it.isNotBlank() } ?: songArt
                val finalArtistSubscribers = when {
                    !resolvedArtistItem?.monthlyListenerCountText.isNullOrBlank() -> {
                        val count = resolvedArtistItem!!.monthlyListenerCountText!!.trim()
                        if (count.contains("listener", ignoreCase = true) || count.contains("monthly", ignoreCase = true)) {
                            count
                        } else {
                            "$count monthly listeners"
                        }
                    }

                    !resolvedArtistItem?.subscriberCountText.isNullOrBlank() -> {
                        val count = resolvedArtistItem!!.subscriberCountText!!.trim()
                        if (count.contains("subscriber", ignoreCase = true)) {
                            count
                        } else {
                            "$count subscribers"
                        }
                    }
                    else -> "Artist"
                }

                val notInterested = _notInterestedSongIds.value
                val finalMoreTracks = moreTracks.filterNot { notInterested.contains(it.id) }
                val finalSimilarTracks = similarTracks.filterNot { notInterested.contains(it.id) }
                val data = NowPlayingRelatedData(
                    artistName = targetArtistName,
                    artistThumbnail = finalArtistThumbnail,
                    artistSubscribers = finalArtistSubscribers,
                    artistBrowseId = artistBrowseId,
                    moreFromArtist = finalMoreTracks,
                    similarTracks = finalSimilarTracks,
                    timestamp = System.currentTimeMillis(),
                    songKey = song.id.toString()
                )
                if (finalMoreTracks.isNotEmpty() || finalSimilarTracks.isNotEmpty()) {
                    nowPlayingRelatedCache[targetId] = data
                }
                val activeSongId = _currentSong.value?.id
                if (activeSongId == null || activeSongId == song.id) {
                    _nowPlayingRelatedData.value = data
                }
            } catch (e: Exception) {
                android.util.Log.e("PlayerViewModel", "loadNowPlayingRecommendations error: ${e.message}", e)
            } finally {
                _isNowPlayingRelatedLoading.value = false
            }
        }
    }

    fun clearNowPlayingRecommendations() {
        nowPlayingRelatedJob?.cancel()
        _nowPlayingRelatedData.value = null
        _isNowPlayingRelatedLoading.value = false
    }

    private var similarSectionsJob: kotlinx.coroutines.Job? = null
    private suspend fun buildSimilarSection(videoId: String, label: String): YtMusicSection? {
        return try {
            val nextResult = com.musicplayer.lytrix.innertube.YouTube.next(
                endpoint = com.musicplayer.lytrix.innertube.models.WatchEndpoint(videoId = videoId)
            ).getOrNull() ?: return null
            val relatedEndpoint = nextResult.relatedEndpoint ?: return null
            val related = com.musicplayer.lytrix.innertube.YouTube.related(relatedEndpoint).getOrNull() ?: return null
            val tracks = mutableListOf<YtMusicTrack>()
            tracks += related.songs.map { YtMusicTrack(it.id, it.title, it.artists?.joinToString(", ") { a -> a.name } ?: "Unknown", it.thumbnail ?: "") }
            tracks += related.artists.map { YtMusicTrack(it.id, it.title, "Artist", it.thumbnail ?: "") }
            tracks += related.albums.map { YtMusicTrack(it.browseId, it.title, "Album • " + (it.artists?.joinToString(", ") { a -> a.name } ?: ""), it.thumbnail ?: "") }
            tracks += related.playlists.map { YtMusicTrack(it.id, it.title, "Playlist", it.thumbnail ?: "") }
            if (tracks.isEmpty()) null else YtMusicSection("Similar to $label", tracks.take(15))
        } catch (e: Throwable) {
            android.util.Log.e("EXPLORE_DEBUG", "buildSimilarSection failed for $videoId: ${e.message}", e)
            null
        }
    }

    private fun loadMultipleSimilarSections() {
        similarSectionsJob?.cancel()
        similarSectionsJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val context = appContext ?: return@launch
            try {
                val prefs = context.getSharedPreferences("yt_history_db", android.content.Context.MODE_PRIVATE)
                val jsonArray = org.json.JSONArray(prefs.getString("history_list", "[]") ?: "[]")
                if (jsonArray.length() == 0) return@launch
                val seen = mutableSetOf<String>()
                val picks = mutableListOf<Pair<String, String>>()
                for (i in 0 until jsonArray.length()) {
                    if (picks.size >= 3) break
                    val obj = jsonArray.getJSONObject(i)
                    val id = obj.getString("id")
                    val title = obj.getString("title")
                    if (seen.add(id)) picks.add(id to title)
                }

                picks.forEach { (id, title) ->
                    buildSimilarSection(id, title)?.let { section ->
                        _exploreYtMusicSections.value = _exploreYtMusicSections.value + section
                    }
                }

            } catch (e: Throwable) { android.util.Log.e("EXPLORE_DEBUG", "loadMultipleSimilarSections crash: ${e.message}", e) }
        }
    }

    private var expandedExploreJob: kotlinx.coroutines.Job? = null

    private fun loadExpandedExploreSections() {
        expandedExploreJob?.cancel() // 🔥 আগেরটা চলতে থাকলে বাতিল — ডুপ্লিকেট বন্ধ করবে
        expandedExploreJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val context = appContext ?: return@launch
            val newSections = mutableListOf<YtMusicSection>()
            try {
                val existingTitles = _exploreYtMusicSections.value.map { it.title.trim().lowercase() }.toSet()
                val alreadyHasSimilar = _exploreYtMusicSections.value.any {
                    it.title.startsWith("Similar to", ignoreCase = true) ||
                        it.subtitle.equals("Similar to", ignoreCase = true)
                }
                val alreadyHasForgotten = existingTitles.any { it.contains("forgotten") }
                val alreadyHasNewReleases = existingTitles.any { it.contains("new release") }
                val isSignedIn = com.musicplayer.lytrix.innertube.YouTube.hasLoginCookie() ||
                    !com.musicplayer.lytrix.innertube.YouTube.cookie.isNullOrBlank()

                // ১. লোকাল হিস্ট্রি থেকে শুধু মিসিং পার্সোনালাইজড ব্যাকআপ (MetroList policy)
                val prefs = context.getSharedPreferences("yt_history_db", android.content.Context.MODE_PRIVATE)
                val historyJson = prefs.getString("history_list", "[]") ?: "[]"
                val jsonArray = org.json.JSONArray(historyJson)
                val historyList = mutableListOf<YtMusicTrack>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    historyList.add(YtMusicTrack(obj.getString("id"), obj.getString("title"), obj.getString("artist"), obj.getString("thumbnailUrl")))
                }

                val deferredSections = mutableListOf<Deferred<List<YtMusicSection>>>()

                // ২. আসল YouTube Explore (New Releases + Moods & Genres) — কোনো হার্ডকোড সার্চ নয়
                deferredSections.add(async {
                    val results = mutableListOf<YtMusicSection>()
                    val explorePage = com.musicplayer.lytrix.innertube.YouTube.explore().getOrNull()
                    if (explorePage != null) {
                        if (explorePage.moodAndGenres.isNotEmpty() && _explorePageMoods.value.isEmpty()) {
                            _explorePageMoods.value = explorePage.moodAndGenres
                        }
                        if (!alreadyHasNewReleases && explorePage.newReleaseAlbums.isNotEmpty()) {
                            val albumTracks = explorePage.newReleaseAlbums.mapNotNull { it.toYtMusicTrack() }.distinctBy { it.id }
                            if (albumTracks.isNotEmpty()) {
                                results.add(
                                    YtMusicSection(
                                        title = "New releases",
                                        tracks = albumTracks,
                                        browseId = "FEmusic_new_releases_albums",
                                    )
                                )
                            }
                        }
                    }
                    results
                })

                // ৩. আসল YouTube Charts (লগআউট থাকলে বা চার্টস না থাকলে)
                if (!isSignedIn) {
                    deferredSections.add(async {
                        val results = mutableListOf<YtMusicSection>()
                        val charts = com.musicplayer.lytrix.innertube.YouTube.getChartsPage().getOrNull()
                        charts?.sections?.forEach { chartSection ->
                            val tracks = chartSection.items.mapNotNull { it.toYtMusicTrack() }.distinctBy { it.id }
                            if (tracks.isNotEmpty()) {
                                results.add(
                                    YtMusicSection(
                                        title = chartSection.title,
                                        tracks = tracks,
                                        browseId = "FEmusic_charts",
                                    )
                                )
                            }
                        }
                        results
                    })
                }

                if (historyList.isNotEmpty()) {
                    // a. "Forgotten favorites" (API থেকে না এলে লোকাল ব্যাকআপ)
                    if (!alreadyHasForgotten && historyList.size > 15) {
                        val forgotten = historyList.drop(10).shuffled().take(12).distinctBy { it.id }
                        if (forgotten.isNotEmpty()) {
                            deferredSections.add(async { listOf(YtMusicSection("Forgotten favorites", forgotten)) })
                        }
                    }

                    // b. সর্বোচ্চ ১টা "Similar to [Artist]" (API থেকে Similar না এলে)
                    if (!alreadyHasSimilar) {
                        deferredSections.add(async {
                            val allArtistsList = historyList
                                .filter { !it.artist.contains("Playlist", true) && !it.artist.contains("Album", true) && !it.artist.contains("Station", true) }
                                .map { it.artist.split(Regex("[,|]")).first().trim() }
                                .filter { it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) }
                            val topArtist = allArtistsList.groupBy { it }.entries.sortedByDescending { it.value.size }.map { it.key }.firstOrNull()
                            if (topArtist != null) {
                                val artistSearch = com.musicplayer.lytrix.innertube.YouTube.searchSummary(query = topArtist, useAccountContext = false).getOrNull()
                                val artistTracks = artistSearch?.summaries?.flatMap { it.items }
                                    ?.mapNotNull { it.toYtMusicTrack() }
                                    ?.map { track ->
                                        if (track.artist.isBlank() || track.artist.equals("Unknown", ignoreCase = true)) {
                                            track.copy(artist = topArtist)
                                        } else track
                                    }
                                    ?.distinctBy { it.id }
                                    ?.take(12)
                                val artistItem = artistSearch?.summaries?.flatMap { it.items }?.filterIsInstance<com.musicplayer.lytrix.innertube.models.ArtistItem>()?.firstOrNull()
                                val artistThumb = artistItem?.thumbnail ?: artistTracks?.firstOrNull()?.thumbnailUrl
                                if (!artistTracks.isNullOrEmpty()) {
                                    listOf(
                                        YtMusicSection(
                                            title = topArtist,
                                            tracks = artistTracks,
                                            subtitle = "Similar to",
                                            iconUrl = artistThumb,
                                            artistId = artistItem?.id,
                                        )
                                    )
                                } else emptyList()
                            } else emptyList()
                        })
                    }
                }

                // ৪. Subscribed Artists থেকে ১টা ডায়নামিক সাজেশন
                deferredSections.add(async {
                    val subPrefs = context.getSharedPreferences("subscribed_artists_prefs", android.content.Context.MODE_PRIVATE)
                    val subIds = subPrefs.getStringSet("subscribed_ids", emptySet())?.toList()
                    if (!subIds.isNullOrEmpty()) {
                        val randomArtistId = subIds.random()
                        var artistName = "Your Subscriptions"
                        subPrefs.getString("artist_data_$randomArtistId", null)?.let {
                            try { artistName = org.json.JSONObject(it).getString("name") } catch (_: Exception) {}
                        }
                        val artistPage = com.musicplayer.lytrix.innertube.YouTube.artist(randomArtistId).getOrNull()
                        val artistSongs = artistPage?.sections?.flatMap { it.items }
                            ?.mapNotNull { it.toYtMusicTrack() }
                            ?.map { track ->
                                if (track.artist.isBlank() || track.artist.equals("Unknown", ignoreCase = true)) {
                                    track.copy(artist = artistName)
                                } else track
                            }
                            ?.distinctBy { it.id }
                            ?.take(12)
                        if (!artistSongs.isNullOrEmpty()) {
                            listOf(
                                YtMusicSection(
                                    title = artistName,
                                    tracks = artistSongs,
                                    subtitle = "From your subscriptions",
                                    iconUrl = artistPage.artist.thumbnail,
                                    artistId = randomArtistId,
                                )
                            )
                        } else emptyList()
                    } else emptyList()
                })

                val allFetchedSections = awaitAll(*deferredSections.toTypedArray()).flatten()
                newSections.addAll(allFetchedSections)
            } catch (e: Throwable) { android.util.Log.e("EXPLORE_DEBUG", "Explore supplementary fetch crash: ${e.message}", e) }

            val currentSections = _exploreYtMusicSections.value
            val currentTitles = currentSections.map { it.title.trim().lowercase() }.toMutableSet()
            val notInterested = _notInterestedSongIds.value
            val filteredNewSections = newSections.mapNotNull { sec ->
                val titleKey = sec.title.trim().lowercase()
                if (!currentTitles.add(titleKey)) return@mapNotNull null
                val cleanTracks = sec.tracks.distinctBy { it.id }.filterNot { notInterested.contains(it.id) }
                if (cleanTracks.isEmpty()) null else sec.copy(tracks = cleanTracks)
            }
            if (filteredNewSections.isNotEmpty()) {
                _exploreYtMusicSections.value = currentSections + filteredNewSections
            }
            _hasMoreExploreChipFeeds.value = remainingExploreChips().isNotEmpty()
        }
    }

    // 🔥 Search (Explore) সেকশনের জন্য ArchiveToon এর Moods & Genres ডেটা
    private val _explorePageMoods = kotlinx.coroutines.flow.MutableStateFlow<List<com.musicplayer.lytrix.innertube.pages.MoodAndGenres.Item>>(emptyList())
    val explorePageMoods: kotlinx.coroutines.flow.StateFlow<List<com.musicplayer.lytrix.innertube.pages.MoodAndGenres.Item>> = _explorePageMoods

    private val _exploreMoodSections = kotlinx.coroutines.flow.MutableStateFlow<List<com.musicplayer.lytrix.innertube.pages.MoodAndGenres>>(emptyList())
    val exploreMoodSections: kotlinx.coroutines.flow.StateFlow<List<com.musicplayer.lytrix.innertube.pages.MoodAndGenres>> = _exploreMoodSections

    private val _moodArtworks = kotlinx.coroutines.flow.MutableStateFlow<Map<String, String>>(emptyMap())
    val moodArtworks: kotlinx.coroutines.flow.StateFlow<Map<String, String>> = _moodArtworks

    private val requestedMoodParams = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    fun loadMoodArtwork(params: String) {
        if (!requestedMoodParams.add(params)) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val result = com.musicplayer.lytrix.innertube.YouTube.browse(
                    "FEmusic_moods_and_genres_category",
                    params
                ).getOrNull()
                val thumb = result?.thumbnail
                    ?: result?.items?.firstNotNullOfOrNull { section ->
                        section.items.firstNotNullOfOrNull { item ->
                            item.thumbnail?.ifBlank { null }
                        }
                    }
                if (thumb != null) {
                    _moodArtworks.value = _moodArtworks.value + (params to thumb)
                }
            } catch (_: Throwable) {}
        }
    }

    fun loadExploreMoods() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val fullMoodAndGenres = com.musicplayer.lytrix.innertube.YouTube.moodAndGenres().getOrNull()
                if (!fullMoodAndGenres.isNullOrEmpty()) {
                    _exploreMoodSections.value = fullMoodAndGenres
                }
                val result = com.musicplayer.lytrix.innertube.YouTube.explore()
                val explorePage = result.getOrNull()
                if (explorePage != null && explorePage.moodAndGenres != null) {
                    _explorePageMoods.value = explorePage.moodAndGenres!!
                }
            } catch (e: Throwable) { e.printStackTrace() }
        }
    }

    // এক্সপ্লোর আইটেমে ক্লিক করলে যে প্লেলিস্টগুলো আসবে তার জন্য
    private val _exploreCategoryPlaylists = kotlinx.coroutines.flow.MutableStateFlow<List<YtMusicSection>>(emptyList())
    val exploreCategoryPlaylists: kotlinx.coroutines.flow.StateFlow<List<YtMusicSection>> = _exploreCategoryPlaylists
    // 🔥 ফিক্স ১: NavigationEndpoint এর জায়গায় BrowseEndpoint দেওয়া হলো

    fun loadExploreCategory(endpoint: com.musicplayer.lytrix.innertube.models.BrowseEndpoint) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _exploreCategoryPlaylists.value = emptyList() // Loading state
            try {
                val result = com.musicplayer.lytrix.innertube.YouTube.browse(
                    endpoint.browseId ?: "",
                    endpoint.params ?: ""
                )
                val browseResult = result.getOrNull()
                val parsedSections = mutableListOf<YtMusicSection>()
                browseResult?.items?.forEach { section ->
                    val sectionTracks = section.items.mapNotNull { ytItem ->
                        ytItem.toYtMusicTrack()
                    }

                    // 🔥 ফিক্স ২: String? এরর ফিক্স করা হলো (null থাকলে ফাঁকা স্ট্রিং বসবে)
                    if (sectionTracks.isNotEmpty()) parsedSections.add(YtMusicSection(section.title ?: "", sectionTracks))
                }

                _exploreCategoryPlaylists.value = parsedSections
            } catch (e: Throwable) { e.printStackTrace() }
        }
    }

    // 🔥 Suggestions ট্যাবের অরিজিনাল লজিক (Top Albums + History থেকে Unique Songs/Artists)
    private val _exploreSuggestions = kotlinx.coroutines.flow.MutableStateFlow<List<YtMusicSection>>(emptyList())
    val exploreSuggestions: kotlinx.coroutines.flow.StateFlow<List<YtMusicSection>> = _exploreSuggestions

    fun loadExploreSuggestions(context: android.content.Context) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val suggestions = mutableListOf<YtMusicSection>()
                // ১. লোকাল হিস্ট্রি থেকে Unique Songs এবং Unique Artists বের করা
                val prefs = context.getSharedPreferences("yt_history_db", android.content.Context.MODE_PRIVATE)
                val historyJson = prefs.getString("history_list", "[]") ?: "[]"
                val jsonArray = org.json.JSONArray(historyJson)
                val historyList = mutableListOf<YtMusicTrack>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    historyList.add(YtMusicTrack(obj.getString("id"), obj.getString("title"), obj.getString("artist"), obj.getString("thumbnailUrl")))
                }

                if (historyList.isNotEmpty()) {
                    val uniqueSongs = historyList.distinctBy { it.id }.shuffled().take(10)
                    if (uniqueSongs.isNotEmpty()) {
                        suggestions.add(YtMusicSection("Unique Songs", uniqueSongs))
                    }

                    val uniqueArtists = historyList.distinctBy { it.artist }.shuffled().take(10).map {
                        YtMusicTrack(it.id, it.artist, "Artist", it.thumbnailUrl)
                    }

                    if (uniqueArtists.isNotEmpty()) {
                        suggestions.add(YtMusicSection("Unique Artists", uniqueArtists))
                    }
                }

                // ২. Lytrix এর ExplorePage থেকে Top Albums লোড করা
                val result = com.musicplayer.lytrix.innertube.YouTube.explore()
                val explorePage = result.getOrNull()
                if (explorePage != null && explorePage.newReleaseAlbums.isNotEmpty()) {
                    val topAlbums = explorePage.newReleaseAlbums.map { album ->
                        YtMusicTrack(album.browseId, album.title, album.artists?.joinToString(", ") { it.name } ?: "Unknown", album.thumbnail)
                    }

                    suggestions.add(YtMusicSection("Top Albums", topAlbums))
                }

                _exploreSuggestions.value = suggestions
            } catch (e: Throwable) { e.printStackTrace() }
        }
    }

    // 🔥 প্লেলিস্ট ওপেন করার জন্য নতুন স্টেট
    val isYtPlaylistLoading = MutableStateFlow(false)
    private val _ytPlaylistDetails = MutableStateFlow<List<YtMusicTrack>?>(null)
    val ytPlaylistDetails: StateFlow<List<YtMusicTrack>?> = _ytPlaylistDetails
    // 🔥 প্লেলিস্টের নাম দেখানোর জন্য
    private val _ytPlaylistName = MutableStateFlow<String>("")
    val ytPlaylistName: StateFlow<String> = _ytPlaylistName
    data class ResolvedSongAlbum(
        val songKey: String,
        val albumId: String?,
        val albumName: String?,
    )

    private val _currentSongAlbumInfo = MutableStateFlow<ResolvedSongAlbum?>(null)
    val currentSongAlbumInfo: StateFlow<ResolvedSongAlbum?> = _currentSongAlbumInfo.asStateFlow()
    private val resolvedSongAlbumCache = java.util.concurrent.ConcurrentHashMap<String, Pair<String?, String?>>()
    private val albumPageCache = java.util.concurrent.ConcurrentHashMap<String, com.musicplayer.lytrix.innertube.pages.AlbumPage>()
    private var resolveSongAlbumJob: Job? = null

    private fun isGenericAlbumPlaceholder(name: String?): Boolean {
        if (name.isNullOrBlank()) return true
        val trimmed = name.trim()
        return trimmed.equals("Online Stream", ignoreCase = true) ||
            trimmed.equals("YouTube Music", ignoreCase = true) ||
            trimmed.equals("YouTube Playlist", ignoreCase = true) ||
            trimmed.equals("Lytrix Original", ignoreCase = true) ||
            trimmed.equals("Offline Downloaded", ignoreCase = true) ||
            trimmed.equals("Unknown Album", ignoreCase = true) ||
            trimmed.startsWith("MPREb_") ||
            trimmed.startsWith("FEmusic_library_album_")
    }

    private fun updateCurrentSongAlbumState(song: Song?) {
        resolveSongAlbumJob?.cancel()
        if (song == null) {
            _currentSongAlbumInfo.value = null
            return
        }
        val rawId = song.originalId?.takeIf { it.isNotBlank() } ?: song.id.toString()
        val cached = resolvedSongAlbumCache[rawId]
        if (cached != null) {
            _currentSongAlbumInfo.value = ResolvedSongAlbum(rawId, cached.first, cached.second)
            return
        }
        // Reset immediately on track change so NowPlaying 3-dots NEVER shows the previous song's album!
        val directId = song.album.takeIf { it.startsWith("MPREb_") || it.startsWith("FEmusic_library_album_") }
        val directName = song.album.takeIf { !isGenericAlbumPlaceholder(it) }
        _currentSongAlbumInfo.value = if (directId != null || directName != null) {
            ResolvedSongAlbum(rawId, directId, directName)
        } else {
            null
        }

        val isYtSong = song.filePath in listOf("ONLINE_LYTRIX", "ONLINE") &&
            !(song.albumArtUriString?.contains("saavn", ignoreCase = true) ?: false)
        if (!isYtSong || rawId.isBlank() || rawId == "0") return

        resolveSongAlbumJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val endpoint = com.musicplayer.lytrix.innertube.models.WatchEndpoint(videoId = rawId)
                // followAutomixPreview = false avoids the extra 2nd /next network roundtrip!
                val nextResult = com.musicplayer.lytrix.innertube.YouTube
                    .next(endpoint, followAutomixPreview = false)
                    .getOrNull()
                val matchedItem = nextResult?.items?.firstOrNull { it.id == rawId }
                    ?: nextResult?.items?.firstOrNull()
                val album = matchedItem?.album
                val resolvedId = album?.id?.takeIf { it.isNotBlank() } ?: directId
                val resolvedName = album?.name?.takeIf { !isGenericAlbumPlaceholder(it) } ?: directName
                if (resolvedId != null || resolvedName != null) {
                    resolvedSongAlbumCache[rawId] = resolvedId to resolvedName
                    val curRaw = _currentSong.value?.let { it.originalId?.takeIf(String::isNotBlank) ?: it.id.toString() }
                    if (curRaw == rawId) {
                        _currentSongAlbumInfo.value = ResolvedSongAlbum(rawId, resolvedId, resolvedName)
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {}
        }
    }

    val ytPlaylistType = kotlinx.coroutines.flow.MutableStateFlow("Playlist")
    val ytPlaylistId = kotlinx.coroutines.flow.MutableStateFlow("")
    val ytAlbumPageDetails = kotlinx.coroutines.flow.MutableStateFlow<com.musicplayer.lytrix.innertube.pages.AlbumPage?>(null)
    val ytPlaylistPageDetails = kotlinx.coroutines.flow.MutableStateFlow<com.musicplayer.lytrix.innertube.pages.PlaylistPage?>(null)
    fun openAlbumForSong(context: android.content.Context, song: com.musicplayer.lytrix.model.Song) {
        val rawId = song.originalId?.takeIf { it.isNotBlank() } ?: song.id.toString()
        val cached = resolvedSongAlbumCache[rawId]
        val currentResolved = _currentSongAlbumInfo.value?.takeIf { it.songKey == rawId }
        val immediateAlbumId = cached?.first
            ?: currentResolved?.albumId
            ?: song.album.takeIf { it.startsWith("MPREb_") || it.startsWith("FEmusic_library_album_") }
        val immediateAlbumTitle = cached?.second
            ?: currentResolved?.albumName
            ?: song.album.takeIf { !isGenericAlbumPlaceholder(it) }

        // Fast path (0ms): albumId was already resolved while the song was playing!
        if (!immediateAlbumId.isNullOrBlank()) {
            loadYtPlaylistDetails(immediateAlbumId, "Album", immediateAlbumTitle ?: "Album")
            return
        }

        // Show Album screen immediately with loading state instead of freezing on Home for 2-3s!
        ytPlaylistType.value = "Album"
        ytPlaylistId.value = ""
        _ytPlaylistName.value = immediateAlbumTitle ?: "Album"
        ytAlbumPageDetails.value = null
        ytPlaylistPageDetails.value = null
        isYtPlaylistLoading.value = true
        _ytPlaylistDetails.value = emptyList()

        viewModelScope.launch(Dispatchers.IO) {
            var foundAlbumId: String? = null
            var foundAlbumTitle: String? = immediateAlbumTitle

            // 1. Single fast /next lookup (followAutomixPreview = false)
            if (rawId.isNotBlank() && rawId != "0") {
                try {
                    val endpoint = com.musicplayer.lytrix.innertube.models.WatchEndpoint(videoId = rawId)
                    val nextResult = com.musicplayer.lytrix.innertube.YouTube
                        .next(endpoint, followAutomixPreview = false)
                        .getOrNull()
                    val album = nextResult?.items?.firstOrNull { it.id == rawId }?.album
                        ?: nextResult?.items?.firstOrNull()?.album
                    if (album?.id != null) {
                        foundAlbumId = album.id
                        if (!album.name.isNullOrBlank()) {
                            foundAlbumTitle = album.name
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("PlayerViewModel", "Error fetching album via next()", e)
                }
            }

            // 2. Fallback: Search for album only if still null
            if (foundAlbumId == null && !foundAlbumTitle.isNullOrBlank()) {
                try {
                    val searchRes = com.musicplayer.lytrix.innertube.YouTube.search(
                        "$foundAlbumTitle ${song.artist}",
                        com.musicplayer.lytrix.innertube.YouTube.SearchFilter.FILTER_ALBUM,
                    ).getOrNull()
                    val topAlbum = searchRes?.items?.firstOrNull() as? com.musicplayer.lytrix.innertube.models.AlbumItem
                    if (topAlbum != null) {
                        foundAlbumId = topAlbum.browseId
                        foundAlbumTitle = topAlbum.title
                    }
                } catch (e: Exception) {
                    android.util.Log.e("PlayerViewModel", "Error fetching album via search", e)
                }
            }

            if (foundAlbumId != null) {
                resolvedSongAlbumCache[rawId] = foundAlbumId to foundAlbumTitle
                withContext(Dispatchers.Main) {
                    loadYtPlaylistDetails(foundAlbumId, "Album", foundAlbumTitle ?: "Album")
                }
            } else {
                withContext(Dispatchers.Main) {
                    isYtPlaylistLoading.value = false
                    _ytPlaylistDetails.value = null
                    android.widget.Toast.makeText(context, "Album not found for this track", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun openAlbumForOnlineSong(context: android.content.Context, song: com.musicplayer.lytrix.model.JioSaavnSong) {
        val isJioSaavn = song.downloadUrl.isNotBlank() ||
            song.imageUrl.contains("saavn", ignoreCase = true)
        if (isJioSaavn) return

        val directAlbumId = song.albumId?.takeIf { it.isNotBlank() }
        if (directAlbumId != null) {
            loadYtPlaylistDetails(directAlbumId, "Album", song.albumName?.takeIf { it.isNotBlank() } ?: "Album")
            return
        }

        val tempSong = com.musicplayer.lytrix.model.Song(
            id = song.id.hashCode().toLong(),
            title = song.name,
            artist = song.artist,
            album = song.albumName ?: "",
            duration = 0L,
            uriString = "https://www.youtube.com/watch?v=${song.id}",
            filePath = "ONLINE_LYTRIX",
            albumArtUriString = song.imageUrl,
            originalId = song.id
        )
        openAlbumForSong(context, tempSong)
    }

    // 🔥 প্লেলিস্ট বা অ্যালবামের ভেতরের গান বের করার ফাংশন

    fun loadYtPlaylistDetails(id: String, type: String, title: String) {
        ytPlaylistType.value = type
        ytPlaylistId.value = id
        if (type == "Album") {
            val cachedAlbum = albumPageCache[id]
            if (cachedAlbum != null) {
                ytAlbumPageDetails.value = cachedAlbum
                ytPlaylistPageDetails.value = null
                _ytPlaylistName.value = cachedAlbum.album.title.ifBlank { title }
                _ytPlaylistDetails.value = cachedAlbum.songs.map { ytItem ->
                    val durStr = ytItem.duration?.let { secs ->
                        val m = secs / 60
                        val s = secs % 60
                        "$m:${s.toString().padStart(2, '0')}"
                    } ?: ""
                    YtMusicTrack(
                        id = ytItem.id,
                        title = ytItem.title,
                        artist = ytItem.artists?.joinToString(", ") { a -> a.name }
                            ?: cachedAlbum.album.artists?.joinToString(", ") { it.name }
                            ?: "Unknown",
                        thumbnailUrl = ytItem.thumbnail ?: cachedAlbum.album.thumbnail,
                        playCount = durStr,
                        itemType = "Song",
                    )
                }
                isYtPlaylistLoading.value = false
                return
            }
        }
        isYtPlaylistLoading.value = true
        _ytPlaylistDetails.value = emptyList()
        if (type != "Album") {
            ytAlbumPageDetails.value = null
        } else if (ytAlbumPageDetails.value?.album?.browseId != id) {
            ytAlbumPageDetails.value = null
        }
        if (type == "Album") {
            ytPlaylistPageDetails.value = null
        } else if (ytPlaylistPageDetails.value?.playlist?.id != id.removePrefix("VL")) {
            ytPlaylistPageDetails.value = null
        }
        viewModelScope.launch(Dispatchers.IO) {
            _ytPlaylistName.value = title
            try {
                val items = if (type == "Album") {
                    val albumPage = com.musicplayer.lytrix.innertube.YouTube.album(id).getOrNull()
                    if (albumPage != null) {
                        albumPageCache[id] = albumPage
                    }
                    ytAlbumPageDetails.value = albumPage
                    if (albumPage != null && albumPage.album.title.isNotBlank()) {
                        _ytPlaylistName.value = albumPage.album.title
                    }
                    albumPage?.songs?.mapNotNull { ytItem ->
                        val durStr = ytItem.duration?.let { secs ->
                            val m = secs / 60
                            val s = secs % 60
                            "$m:${s.toString().padStart(2, '0')}"
                        } ?: ""
                        YtMusicTrack(
                            id = ytItem.id,
                            title = ytItem.title,
                            artist = ytItem.artists?.joinToString(", ") { a -> a.name } ?: albumPage.album.artists?.joinToString(", ") { it.name } ?: "Unknown",
                            thumbnailUrl = ytItem.thumbnail ?: albumPage.album.thumbnail,
                            playCount = durStr,
                            itemType = "Song"
                        )
                    }

                } else if (type == "Podcast" || id.startsWith("MPSP")) {
                    val podcastPage = com.musicplayer.lytrix.innertube.YouTube.podcast(id).getOrNull()
                    var epList = podcastPage?.episodes?.mapNotNull { ep ->
                        val rawArtist = ep.author?.name ?: podcastPage.podcast.author?.name ?: title
                        val artistName = cleanArtistDisplayName(rawArtist)
                        val dur = ep.duration?.let { android.text.format.DateUtils.formatElapsedTime(it.toLong()) } ?: ""
                        val meta = listOfNotNull(ep.publishDateText, dur.takeIf { it.isNotBlank() }).joinToString(" • ")
                        YtMusicTrack(ep.id, ep.title, artistName, ep.thumbnail, meta, itemType = "Episode")
                    }

                    if (epList.isNullOrEmpty()) {
                        val cleanId = if (id.startsWith("MPSP")) id.removePrefix("MPSP") else id.removePrefix("VL")
                        val targetId = if (cleanId.startsWith("VL")) cleanId else "VL$cleanId"
                        val plResult = com.musicplayer.lytrix.innertube.YouTube.playlist(targetId).getOrNull()
                        epList = plResult?.songs?.mapNotNull { ytItem ->
                            val cleanArtists = ytItem.artists?.filterNot {
                                val n = it.name.lowercase()
                                n.contains("view") || n.contains("play") || n.contains("sub") || it.name.matches(Regex("""\d+[\d.,]*\s*[kmbKMB]?"""))
                            }

                            val rawArtist = cleanArtists?.joinToString(", ") { a -> a.name }?.ifBlank { null }
                                ?: ytItem.artists?.firstOrNull()?.name
                                ?: title
                            val artistName = cleanArtistDisplayName(rawArtist)
                            val dur = ytItem.duration?.let { android.text.format.DateUtils.formatElapsedTime(it.toLong()) } ?: ""
                            val meta = listOfNotNull(ytItem.viewCountText, dur.takeIf { it.isNotBlank() }).joinToString(" • ")
                            YtMusicTrack(ytItem.id, ytItem.title, artistName, ytItem.thumbnail ?: "", meta, itemType = "Episode")
                        }
                    }

                    epList
                } else {
                    val cleanId = id.removePrefix("VL")
                    val plPage = com.musicplayer.lytrix.innertube.YouTube.playlist(cleanId).getOrNull()
                    ytPlaylistPageDetails.value = plPage
                    if (plPage != null && plPage.playlist.title.isNotBlank()) {
                        _ytPlaylistName.value = plPage.playlist.title
                    }
                    plPage?.songs?.mapNotNull { ytItem ->
                        val durStr = ytItem.duration?.let { secs ->
                            val m = secs / 60
                            val s = secs % 60
                            "$m:${s.toString().padStart(2, '0')}"
                        } ?: ""
                        YtMusicTrack(
                            id = ytItem.id,
                            title = ytItem.title,
                            artist = ytItem.artists?.joinToString(", ") { a -> a.name } ?: "Unknown",
                            thumbnailUrl = ytItem.thumbnail ?: plPage.playlist.thumbnail ?: "",
                            playCount = durStr,
                            itemType = "Song"
                        )
                    }
                }

                _ytPlaylistDetails.value = items ?: emptyList()
            } catch (e: Exception) {
                _ytPlaylistDetails.value = emptyList()
            } finally {
                isYtPlaylistLoading.value = false
            }
        }
    }

    // 🔥 ম্যাজিক ফাংশন: ArchiveToon এর মতো সাইলেন্টলি ইনারটিউব দিয়ে ১০০+ গান একসাথে সেভ করবে

    fun saveYtPlaylistSilently(id: String, type: String, title: String, thumbnailUrl: String, context: android.content.Context) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val dbName = if (type == "Album") "online_albums_meta" else if (type == "Podcast") "online_podcasts_meta" else "online_playlists_meta"
            val prefix = if (type == "Album") "online_album_" else if (type == "Podcast") "online_podcast_" else "online_pl_"
            val targetPrefs = context.getSharedPreferences(dbName, android.content.Context.MODE_PRIVATE)
            val coverPrefs = context.getSharedPreferences("playlist_covers", android.content.Context.MODE_PRIVATE)
            val timePrefs = context.getSharedPreferences("playlist_time", android.content.Context.MODE_PRIVATE)
            val idPrefs = context.getSharedPreferences("playlist_ids", android.content.Context.MODE_PRIVATE)
            val songPrefs = context.getSharedPreferences("$prefix$title", android.content.Context.MODE_PRIVATE)
            try {
                // ১. ইনারটিউব API দিয়ে চোখের পলকে সব গান ফেচ করে আনা হচ্ছে (NewPipe বাদ!)
                val items = if (type == "Album") {
                    com.musicplayer.lytrix.innertube.YouTube.album(id).getOrNull()?.songs?.mapNotNull { ytItem ->
                        YtMusicTrack(ytItem.id, ytItem.title, ytItem.artists?.joinToString(", ") { a -> a.name } ?: "Unknown", ytItem.thumbnail ?: "")
                    }

                } else if (type == "Podcast" || id.startsWith("MPSP")) {
                    var epItems = com.musicplayer.lytrix.innertube.YouTube.podcast(id).getOrNull()?.episodes?.mapNotNull { ep ->
                        val artistName = cleanArtistDisplayName(ep.author?.name ?: title)
                        YtMusicTrack(ep.id, ep.title, artistName, ep.thumbnail, ep.publishDateText ?: "", itemType = "Episode")
                    }

                    if (epItems.isNullOrEmpty()) {
                        val cleanId = if (id.startsWith("MPSP")) id.removePrefix("MPSP") else id.removePrefix("VL")
                        val targetId = if (cleanId.startsWith("VL")) cleanId else "VL$cleanId"
                        epItems = com.musicplayer.lytrix.innertube.YouTube.playlist(targetId).getOrNull()?.songs?.mapNotNull { ytItem ->
                            val rawArtist = ytItem.artists?.joinToString(", ") { a -> a.name } ?: "Unknown"
                            val artistName = cleanArtistDisplayName(rawArtist)
                            YtMusicTrack(ytItem.id, ytItem.title, artistName, ytItem.thumbnail ?: "", itemType = "Episode")
                        }
                    }

                    epItems
                } else {
                    // "VL" প্রিফিক্স থাকলে রিমুভ করে নেবে, যাতে ইনারটিউব কনফিউজড না হয়
                    val cleanId = id.removePrefix("VL")
                    com.musicplayer.lytrix.innertube.YouTube.playlist(cleanId).getOrNull()?.songs?.mapNotNull { ytItem ->
                        YtMusicTrack(ytItem.id, ytItem.title, ytItem.artists?.joinToString(", ") { a -> a.name } ?: "Unknown", ytItem.thumbnail ?: "")
                    }
                }

                val tracks = items ?: emptyList()
                // ২. গানগুলো ডাটাবেসে সেভ করা হচ্ছে
                val editor = songPrefs.edit()
                editor.clear() // পুরনো ডাটা থাকলে ক্লিন করে নেবে
                tracks.forEach { track ->
                    val obj = org.json.JSONObject()
                    obj.put("id", track.id)
                    obj.put("originalId", track.id)
                    obj.put("title", track.title)
                    obj.put("name", track.title)
                    obj.put("artist", track.artist)
                    // 🔥 গানের নিজস্ব ছবি থাকলে সেটা, না থাকলে এলবামের ছবি বসবে
                    obj.put("imageUrl", track.thumbnailUrl.ifEmpty { thumbnailUrl })
                    obj.put("downloadUrl", "")
                    editor.putString(track.id, obj.toString())
                }

                editor.apply()
                // ৩. এলবাম/প্লেলিস্টের মেটাডেটা সেভ করা হচ্ছে
                val set = targetPrefs.getStringSet("names", emptySet())?.toMutableSet() ?: mutableSetOf()
                set.add(title)
                targetPrefs.edit().putStringSet("names", set).apply()
                coverPrefs.edit().putString(title, thumbnailUrl).apply()
                timePrefs.edit().putLong(title, System.currentTimeMillis()).apply()
                idPrefs.edit().putString(title, id).apply()
                context.getSharedPreferences("playlist_source", android.content.Context.MODE_PRIVATE)
                    .edit()
                    .putString(title, "YouTube")
                    .apply()
                targetPrefs.edit().putInt("count_$title", tracks.size).apply()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "$type Synced! (${tracks.size} tracks saved)", android.widget.Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Failed to sync $type tracks.", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 🔥 ব্যাকগ্রাউন্ডে প্লেলিস্ট/এলবাম/পডকাস্টের সব গান ফেচ করে ডাউনলোড করার ফাংশন

    fun downloadYtPlaylistSilently(id: String, type: String, title: String, context: android.content.Context) {
        android.widget.Toast.makeText(context, "Fetching tracks for '$title' to download...", android.widget.Toast.LENGTH_SHORT).show()
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val prefix = if (type == "Album") "online_album_" else if (type == "Podcast") "online_podcast_" else "online_pl_"
            val songPrefs = context.getSharedPreferences("$prefix$title", android.content.Context.MODE_PRIVATE)
            val tracksToDownload = mutableListOf<YtMusicTrack>()
            // ১. যদি অলরেডি সেভ করা থাকে, লোকাল প্রেফ থেকে গানগুলো নিয়ে নেবে
            val allEntries = songPrefs.all
            if (allEntries.isNotEmpty()) {
                allEntries.values.forEach { v ->
                    try {
                        val obj = org.json.JSONObject(v.toString())
                        val trackId = obj.optString("id", "")
                        val trackTitle = obj.optString("title", obj.optString("name", "Unknown"))
                        val trackArtist = obj.optString("artist", "Unknown Artist")
                        val trackImg = obj.optString("imageUrl", "")
                        if (trackId.isNotBlank()) {
                            tracksToDownload.add(YtMusicTrack(trackId, trackTitle, trackArtist, trackImg))
                        }

                    } catch (_: Exception) {}
                }
            }

            // ২. লোকাল প্রেফে না থাকলে অনলাইন থেকে গানগুলো আনবে
            if (tracksToDownload.isEmpty()) {
                try {
                    val fetched = if (type == "Album") {
                        com.musicplayer.lytrix.innertube.YouTube.album(id).getOrNull()?.songs?.mapNotNull { ytItem ->
                            YtMusicTrack(ytItem.id, ytItem.title, ytItem.artists?.joinToString(", ") { a -> a.name } ?: "Unknown", ytItem.thumbnail ?: "")
                        }

                    } else if (type == "Podcast" || id.startsWith("MPSP")) {
                        var epItems = com.musicplayer.lytrix.innertube.YouTube.podcast(id).getOrNull()?.episodes?.mapNotNull { ep ->
                            YtMusicTrack(ep.id, ep.title, ep.author?.name ?: title, ep.thumbnail, ep.publishDateText ?: "", itemType = "Episode")
                        }

                        if (epItems.isNullOrEmpty()) {
                            val cleanId = if (id.startsWith("MPSP")) id.removePrefix("MPSP") else id.removePrefix("VL")
                            val targetId = if (cleanId.startsWith("VL")) cleanId else "VL$cleanId"
                            epItems = com.musicplayer.lytrix.innertube.YouTube.playlist(targetId).getOrNull()?.songs?.mapNotNull { ytItem ->
                                YtMusicTrack(ytItem.id, ytItem.title, ytItem.artists?.joinToString(", ") { a -> a.name } ?: "Unknown", ytItem.thumbnail ?: "", itemType = "Episode")
                            }
                        }

                        epItems
                    } else {
                        val cleanId = id.removePrefix("VL")
                        com.musicplayer.lytrix.innertube.YouTube.playlist(cleanId).getOrNull()?.songs?.mapNotNull { ytItem ->
                            YtMusicTrack(ytItem.id, ytItem.title, ytItem.artists?.joinToString(", ") { a -> a.name } ?: "Unknown", ytItem.thumbnail ?: "")
                        }
                    }

                    if (!fetched.isNullOrEmpty()) {
                        tracksToDownload.addAll(fetched)
                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (tracksToDownload.isEmpty()) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Could not load tracks for '$title'", android.widget.Toast.LENGTH_SHORT).show()
                }

                return@launch
            }

            // ৩. প্রতিটা গানের ডাউনলোড শুরু করা
            var startedCount = 0
            tracksToDownload.forEach { track ->
                val safeName = track.title.replace(Regex("[^a-zA-Z0-9 ]"), "_").take(50).trim()
                val file = java.io.File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC), "$safeName.mp3")
                if (!file.exists()) {
                    com.musicplayer.lytrix.playback.OkHttpDownloader.startDownload(
                        context, track.id, track.title, track.artist, track.thumbnailUrl ?: "", ""
                    )
                    startedCount++
                }
            }

            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                if (startedCount > 0) {
                    android.widget.Toast.makeText(context, "Downloading $startedCount tracks from '$title'...", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    android.widget.Toast.makeText(context, "All tracks from '$title' are already downloaded!", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // প্লেলিস্ট ক্লোজ করার ফাংশন

    fun closeYtPlaylist() {
        _ytPlaylistDetails.value = null
        ytAlbumPageDetails.value = null
        ytPlaylistPageDetails.value = null
    }

    fun playLytrixPlaylist(
        tracks: List<YtMusicTrack>,
        startIndex: Int,
        context: android.content.Context,
        albumTitle: String? = null
    ) {
        if (shouldBlockGuestPlayback()) {
            Toast.makeText(appContext ?: com.musicplayer.lytrix.LytrixApplication.instance, "Only the host can control playback in Listen Together", Toast.LENGTH_SHORT).show()
            return
        }
        if (tracks.isEmpty()) return
        val safeStartIndex = startIndex.coerceIn(tracks.indices)
        // Do NOT stamp stale ytPlaylistName.value as the song's album name!
        // Only use an explicit albumTitle when actually launching from an Album screen.
        val resolvedAlbum = albumTitle?.takeIf { it.isNotBlank() } ?: ""
        val clickedTrack = tracks[safeStartIndex]
        saveToYtHistory(clickedTrack)
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val firstThumb = clickedTrack.thumbnailUrl
            var firstRealId = if (firstThumb.contains("ytimg.com/vi/")) firstThumb.substringAfter("vi/").substringBefore("/") else clickedTrack.id
            if (firstRealId.startsWith("spotify_")) {
                firstRealId = com.musicplayer.lytrix.spotify.SpotifyTrackResolver.resolveToYouTubeId(context, clickedTrack.title, clickedTrack.artist, firstRealId)
            }
            val cleanFirstArtist = cleanArtistDisplayName(clickedTrack.artist)
            val firstAppSong = Song(
                id = firstRealId.hashCode().toLong(), title = clickedTrack.title, artist = cleanFirstArtist,
                album = resolvedAlbum, duration = 0L,
                uriString = "https://www.youtube.com/watch?v=$firstRealId",
                filePath = "ONLINE_LYTRIX", albumArtUriString = clickedTrack.thumbnailUrl,
                isVideo = false, hasLyrics = false, originalId = clickedTrack.id
            )
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                playSong(firstAppSong, listOf(firstAppSong))
            }

            // 🔥 বাগ ফিক্স: ক্লিক করা গানের আগে যে গানগুলো আছে সেগুলোও queue-এ add করো
            // যাতে Previous বাটনে ক্লিক করলে আগের গান play হয়
            val prevTracks = tracks.take(safeStartIndex).takeLast(10) // সর্বোচ্চ ১০টা আগের গান
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                val controller = mediaController ?: return@withContext
                val prevSongs = prevTracks.map { track ->
                    val thumb = track.thumbnailUrl
                    var realId = if (thumb.contains("ytimg.com/vi/")) thumb.substringAfter("vi/").substringBefore("/") else track.id
                    if (realId.startsWith("spotify_")) {
                        realId = kotlinx.coroutines.runBlocking {
                            com.musicplayer.lytrix.spotify.SpotifyTrackResolver.resolveToYouTubeId(context, track.title, track.artist, realId)
                        }
                    }
                    val cleanPrevArtist = cleanArtistDisplayName(track.artist)
                    Song(
                        id = realId.hashCode().toLong(), title = track.title, artist = cleanPrevArtist,
                        album = resolvedAlbum, duration = 0L,
                        uriString = "https://www.youtube.com/watch?v=$realId",
                        filePath = "ONLINE_LYTRIX", albumArtUriString = track.thumbnailUrl,
                        isVideo = false, hasLyrics = false, originalId = track.id
                    )
                }

                if (prevSongs.isNotEmpty()) {
                    val prevMediaItems = prevSongs.map { song ->
                        androidx.media3.common.MediaItem.Builder()
                            .setMediaId(song.id.toString()).setUri(song.uriString)
                            .setMediaMetadata(buildQueueMediaMetadata(song))
                            .build()
                    }

                    // আগের গানগুলো index 0 এ insert করো (current song index 0 এ আছে, ওগুলো সামনে যাবে)
                    controller.addMediaItems(0, prevMediaItems)
                    // current song টা এখন prevSongs.size index এ আছে, সেখানে seek করো
                    controller.seekTo(prevSongs.size, 0L)
                    val updatedList = _currentQueueList.value.toMutableList()
                    prevSongs.reversed().forEach { updatedList.add(0, it) }
                    _currentQueueList.value = updatedList
                }
            }

            // ক্লিক করা গানের পরের গানগুলো (Next songs)
            val nextTracks = tracks.drop(safeStartIndex + 1).take(15)
            for (track in nextTracks) {
                val thumb = track.thumbnailUrl
                var realId = if (thumb.contains("ytimg.com/vi/")) thumb.substringAfter("vi/").substringBefore("/") else track.id
                if (realId.startsWith("spotify_")) {
                    realId = com.musicplayer.lytrix.spotify.SpotifyTrackResolver.resolveToYouTubeId(context, track.title, track.artist, realId)
                }
                val cleanNextArtist = cleanArtistDisplayName(track.artist)
                val nextAppSong = Song(
                    id = realId.hashCode().toLong(), title = track.title, artist = cleanNextArtist,
                    album = resolvedAlbum, duration = 0L,
                    uriString = "https://www.youtube.com/watch?v=$realId",
                    filePath = "ONLINE_LYTRIX", albumArtUriString = track.thumbnailUrl,
                    isVideo = false, hasLyrics = false, originalId = track.id
                )
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    val controller = mediaController ?: return@withContext
                    val mediaItem = androidx.media3.common.MediaItem.Builder()
                        .setMediaId(nextAppSong.id.toString()).setUri(nextAppSong.uriString)
                        .setMediaMetadata(buildQueueMediaMetadata(nextAppSong))
                        .build()
                    val currentList = _currentQueueList.value.toMutableList()
                    currentList.add(nextAppSong)
                    _currentQueueList.value = currentList
                    controller.addMediaItem(mediaItem)
                }
            }
        }
    }

    // ==========================================
    // 🔥 EXACT QUALITY FETCH LOGIC & AUDIO-VIDEO MERGING
    // ==========================================

    data class VideoStreamPayload(
        val videoUrl: String,
        val audioUrl: String? = null
    )

    suspend fun getYoutubeVideoStreamUrl(videoId: String, qualityPref: String): VideoStreamPayload? {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val youtubeUrl = "https://www.youtube.com/watch?v=$videoId"
                val streamExtractor = org.schabi.newpipe.extractor.ServiceList.YouTube.getStreamExtractor(youtubeUrl)
                streamExtractor.fetchPage()
                val bestAudio = streamExtractor.audioStreams.maxByOrNull { it.bitrate }?.content

                // Auto হলে ডিফল্ট 720p এর সাউন্ডসহ (Muxed) ফাইল আনবে (সবচেয়ে স্মুথ)
                if (qualityPref == "Auto") {
                    val bestAuto = streamExtractor.videoStreams
                        .filter { (it.resolution.replace("p", "").toIntOrNull() ?: 0) <= 720 }
                        .maxByOrNull { it.resolution.replace("p", "").toIntOrNull() ?: 0 }
                    if (bestAuto != null) {
                        return@withContext VideoStreamPayload(bestAuto.content, null)
                    }
                    val bestVideoOnly = streamExtractor.videoOnlyStreams
                        .filter { (it.resolution.replace("p", "").toIntOrNull() ?: 0) <= 720 }
                        .maxByOrNull { it.resolution.replace("p", "").toIntOrNull() ?: 0 }
                    if (bestVideoOnly != null) {
                        return@withContext VideoStreamPayload(bestVideoOnly.content, bestAudio)
                    }
                    return@withContext null
                }

                // ম্যানুয়াল কোয়ালিটি (যেমন 1080p, 720p)
                val targetRes = qualityPref.replace("p", "").toIntOrNull() ?: 720
                // ১. প্রথমে সাউন্ডসহ (Muxed) ফাইলে খুঁজবে (ল্যাগ একদম জিরো হবে)
                val exactMuxed = streamExtractor.videoStreams.find { it.resolution.replace("p", "").toIntOrNull() == targetRes }
                if (exactMuxed != null) return@withContext VideoStreamPayload(exactMuxed.content, null)
                // ২. না পেলে সাউন্ড ছাড়া (Video Only) ফাইলে খুঁজবে (1080p এর জন্য) এবং বেস্ট অডিও মার্জ করবে
                val exactVideoOnly = streamExtractor.videoOnlyStreams.find { it.resolution.replace("p", "").toIntOrNull() == targetRes }
                if (exactVideoOnly != null) return@withContext VideoStreamPayload(exactVideoOnly.content, bestAudio)
                // ৩. কিছু না পেলে সবচেয়ে কাছের বেস্ট কোয়ালিটি
                val fallbackMuxed = streamExtractor.videoStreams.maxByOrNull { it.resolution.replace("p", "").toIntOrNull() ?: 0 }
                if (fallbackMuxed != null) return@withContext VideoStreamPayload(fallbackMuxed.content, null)
                val fallbackVideoOnly = streamExtractor.videoOnlyStreams.maxByOrNull { it.resolution.replace("p", "").toIntOrNull() ?: 0 }
                if (fallbackVideoOnly != null) return@withContext VideoStreamPayload(fallbackVideoOnly.content, bestAudio)
                null
            } catch (e: Throwable) {
                e.printStackTrace()
                null
            }
        }
    }

    // ==========================================
// ==========================================================================================
    // 🔥 VIDEO PRE-WARMING & SMART HANDOFF (Background Preload)
    // REPLACE everything in PlayerViewModel.kt from  `private var videoPlayer: ...`  down to the END of
    // `fun releaseVideoPlayer() { ... }`  with this whole block.
    // Do NOT touch `VideoStreamPayload` / `getYoutubeVideoStreamUrl` (they stay above).
    //
    // What changed vs the previous block
    //  1. resolveCounterpart(): a candidate / counterpart whose id == the song's own id is the song's OWN video
    //     (self counterpart) instead of being rejected  -> fixes "Alone, Pt. II : No music video found".
    //  2. resolveCounterpart(): only OMV videos are accepted (UGC / lyric / dance / cover are never used).
    //  3. isSong16by9(): better detection (googleusercontent w-h ratio, ytimg /vi/, stricter title test).
    //  4. switchToVideoMode(): GAPLESS HAND-OFF. The audio is NOT paused any more. It keeps playing while the
    //     video loads and seeks (video muted + paused = "cued"). When the video is ready and cued exactly where
    //     the audio will be, the sound is switched in one step. No 5-10 s silence.
    // ==========================================================================================
    private var videoPlayer: androidx.media3.exoplayer.ExoPlayer? = null
    private var preWarmJob: Job? = null
    private var preWarmedSongId: Long? = null
    private var preWarmedQuality: String? = null

    /**
     * AUDIO-timeline position (ms) the video has to start from.
     * It is mapped to the VIDEO timeline only at the moment the video is applied, when the counterpart
     * (and its intro offset) is guaranteed to be known.
     */
    private var pendingVideoSeekMs: Long? = null

    /** Latest wanted play/pause state for the video side. Always equals the user's latest intent. */
    private var pendingVideoPlayWhenReady: Boolean? = null

    private val _isVideoPlayerReady = MutableStateFlow(false)
    val isVideoPlayerReady: StateFlow<Boolean> = _isVideoPlayerReady.asStateFlow()

    private val _isVideoModeActive = MutableStateFlow(false)
    val isVideoModeActive: StateFlow<Boolean> = _isVideoModeActive.asStateFlow()

    /**
     * TRUE while the user has asked for video but the audio is still the sound source
     * (video is loading / being cued). During this time `isVideoModeActive` is still FALSE, so every other part of
     * the view-model (progress poll, play/pause, seek, audio listeners) keeps behaving as in normal audio mode.
     * The UI should show the video stage + spinner while this is true.
     */
    private val _isVideoHandoffPending = MutableStateFlow(false)
    val isVideoHandoffPending: StateFlow<Boolean> = _isVideoHandoffPending.asStateFlow()

    private var handoffJob: Job? = null
    private var handoffSongId: Long? = null
    private val HANDOFF_TIMEOUT_MS = 30_000L
    private val HANDOFF_FIRST_LEAD_MS = 2_500L
    private val HANDOFF_TOLERANCE_MS = 150L

    // 🎵 In-memory stream URL cache (key = "videoId|quality")
    private val videoStreamCache = java.util.concurrent.ConcurrentHashMap<String, Pair<VideoStreamPayload, Long>>()
    private val VIDEO_CACHE_TTL_MS = 2 * 60 * 60 * 1000L // 2 hours

    // 🎵 Counterpart cache. Stored under EVERY key a song can be known by (db id, youtube id, raw id)
    private val counterpartMap = java.util.concurrent.ConcurrentHashMap<String, MusicVideoCounterpart>()

    // Songs for which we KNOW (search finished successfully) that no official video exists
    private val videoUnavailableKeys: MutableSet<String> =
        java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private fun songKeys(song: Song, rawId: String? = null): List<String> = listOfNotNull(
        song.id.toString(),
        song.originalId?.takeIf { it.isNotBlank() },
        rawId?.takeIf { it.isNotBlank() }
    ).distinct()

    private fun findCounterpart(song: Song): MusicVideoCounterpart? =
        songKeys(song).firstNotNullOfOrNull { counterpartMap[it] }

    private fun storeCounterpart(song: Song, rawId: String, cp: MusicVideoCounterpart) {
        songKeys(song, rawId).forEach { counterpartMap[it] = cp }
    }

    private fun markVideoUnavailable(song: Song, rawId: String) {
        songKeys(song, rawId).forEach { videoUnavailableKeys.add(it) }
    }

    fun isVideoKnownUnavailable(song: Song): Boolean =
        songKeys(song).any { it in videoUnavailableKeys }

    /** For the UI: call before showing an ad / switching. Returns true (and shows a toast) if there is no video. */
    fun notifyIfVideoUnavailable(song: Song): Boolean {
        if (!isVideoKnownUnavailable(song)) return false
        appContext?.let {
            Toast.makeText(it, "No music video found for this song", Toast.LENGTH_SHORT).show()
        }
        return true
    }

    fun getCounterpart(songId: String): MusicVideoCounterpart? = counterpartMap[songId]

    private fun counterpartForSongId(songId: String): MusicVideoCounterpart? {
        counterpartMap[songId]?.let { return it }
        val cur = _currentSong.value ?: return null
        return if (songId == cur.id.toString() || songId == cur.originalId) findCounterpart(cur) else null
    }

    private val googleThumbWhRegex = Regex("=w(\\d+)-h(\\d+)")

    private fun has16by9Thumbnail(song: Song): Boolean {
        val art = song.albumArtUriString ?: return false
        if (art.contains("saavn", ignoreCase = true)) return false
        if (art.contains("ytimg.com/vi/", ignoreCase = true) ||
            art.contains("img.youtube.com/vi/", ignoreCase = true)
        ) {
            return true
        }
        if (art.contains("googleusercontent.com", ignoreCase = true) ||
            art.contains("ggpht.com", ignoreCase = true)
        ) {
            val match = googleThumbWhRegex.find(art)
            if (match != null) {
                val w = match.groupValues[1].toIntOrNull() ?: 0
                val h = match.groupValues[2].toIntOrNull() ?: 0
                if (w > 0 && h > 0 && w.toFloat() / h.toFloat() >= 1.25f) return true
            }
        }
        return false
    }

    fun isYouTubePodcastTrack(song: Song): Boolean {
        if (song.filePath != "ONLINE_PODCAST") return false
        val uri = song.uriString
        if (uri.contains("youtube.com", ignoreCase = true) || uri.contains("youtu.be", ignoreCase = true)) return true
        val orig = song.originalId
        return !orig.isNullOrBlank() && !orig.startsWith("http", ignoreCase = true) && !orig.startsWith("saavn", ignoreCase = true) && !orig.all(Char::isDigit)
    }

    fun isYouTubeTrack(song: Song): Boolean {
        val isSaavn = (song.albumArtUriString?.contains("saavn", ignoreCase = true) == true) ||
                song.uriString.contains("saavncdn.com", ignoreCase = true)
        if (isSaavn) return false
        if (song.filePath in listOf("ONLINE_LYTRIX", "ONLINE")) return true
        return isYouTubePodcastTrack(song)
    }

    fun isSong16by9(song: Song): Boolean {
        if (song.isVideo && song.filePath != "ONLINE" && song.filePath != "ONLINE_LYTRIX") return true
        if (isYouTubePodcastTrack(song)) return true
        val isYtSong = isYouTubeTrack(song)
        if (!isYtSong) return false
        val rawId = song.originalId ?: song.id.toString()
        val counterpart = findCounterpart(song)
        if (counterpart != null) {
            return counterpart.targetVideoId == rawId
        }
        return has16by9Thumbnail(song)
    }

    fun isInListenTogetherRoom(): Boolean {
        val isFirebase = _myRoomStatus.value in listOf("host", "co_host", "approved", "pending") || _currentListenRoom.value != null
        val isMetro = metroManager.roomRole.value != com.musicplayer.lytrix.listentogether.metro.MetroRoomRole.NONE
        return isFirebase || isMetro
    }

    fun onAppMinimized() {
        val current = _currentSong.value ?: return
        if (_isVideoModeActive.value || _isVideoHandoffPending.value) {
            switchToAudioMode(current)
        }
    }

    private var lastAudioDurationBeforeVideoMs: Long = 0L

    fun mapAudioToVideoPosition(songId: String, audioPositionMs: Long, videoDurationMs: Long? = null): Long {
        val cp = counterpartForSongId(songId)
        if (cp?.positionMap != null) {
            return cp.positionMap.mapPosition(audioPositionMs).coerceAtLeast(0L)
        }
        val videoDur = videoDurationMs ?: videoPlayer?.duration?.takeIf { it > 0L }
        val audioDur = lastAudioDurationBeforeVideoMs.takeIf { it > 0L }
            ?: _currentSong.value?.duration?.takeIf { it > 0L }
            ?: _duration.value.takeIf { it > 0L }
        if (videoDur != null && audioDur != null) {
            val delta = kotlin.math.abs(audioDur - videoDur)
            if (delta > 40_000L || audioPositionMs >= (videoDur - 5_000L)) {
                timber.log.Timber.tag("VIDEO_LOAD").d("🎬 [DURATION_MISMATCH] Audio: ${audioDur}ms vs Video: ${videoDur}ms (Delta: ${delta}ms) -> Starting video from 0:00")
                return 0L
            }
            return audioPositionMs.coerceIn(0L, videoDur - 1L)
        }
        return audioPositionMs.coerceAtLeast(0L)
    }

    fun mapVideoToAudioPosition(songId: String, videoPositionMs: Long, videoDurationMs: Long? = null): Long {
        val cp = counterpartForSongId(songId)
        if (cp?.positionMap != null) {
            return cp.positionMap.reversed().mapPosition(videoPositionMs).coerceAtLeast(0L)
        }
        val audioDur = lastAudioDurationBeforeVideoMs.takeIf { it > 0L }
            ?: _currentSong.value?.duration?.takeIf { it > 0L }
            ?: mediaController?.duration?.takeIf { it != androidx.media3.common.C.TIME_UNSET && it > 0L }
        val videoDur = videoDurationMs
            ?: videoPlayer?.duration?.takeIf { it != androidx.media3.common.C.TIME_UNSET && it > 0L }
            ?: _duration.value.takeIf { it > 0L }
        if (audioDur != null && videoDur != null) {
            val delta = kotlin.math.abs(audioDur - videoDur)
            if (delta > 40_000L) {
                timber.log.Timber.tag("VIDEO_LOAD").d("🎬 [DURATION_MISMATCH] Video: ${videoDur}ms vs Audio: ${audioDur}ms (Delta: ${delta}ms) -> Starting audio from 0:00")
                return 0L
            }
            return videoPositionMs.coerceIn(0L, (audioDur - 1L).coerceAtLeast(0L))
        }
        return if (audioDur != null) videoPositionMs.coerceIn(0L, (audioDur - 1L).coerceAtLeast(0L)) else videoPositionMs.coerceAtLeast(0L)
    }

    fun getEffectiveLyricsPosition(song: Song?, currentPositionMs: Long): Long {
        if (song == null) return currentPositionMs
        return if (_isVideoModeActive.value && !isSong16by9(song)) {
            val cp = counterpartForSongId(song.id.toString())
            if (cp?.positionMap != null) {
                mapVideoToAudioPosition(song.id.toString(), currentPositionMs)
            } else {
                currentPositionMs
            }
        } else {
            currentPositionMs
        }
    }

    fun seekFromLyrics(song: Song?, audioPosMs: Long) {
        if (song != null && _isVideoModeActive.value && !isSong16by9(song)) {
            val targetVideoPos = mapAudioToVideoPosition(song.id.toString(), audioPosMs)
            seekTo(targetVideoPos)
        } else {
            seekTo(audioPosMs)
        }
    }

    fun setVideoPreloadEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("video_background_preload", enabled)
            .apply()
        val inVideo = _isVideoModeActive.value || _isVideoHandoffPending.value
        if (!enabled && !inVideo) {
            releaseVideoPlayer()
        } else if (enabled && !inVideo) {
            _currentSong.value?.let { preWarmVideoIfEligible(context, it) }
        }
    }

    fun getVideoPlayer(): androidx.media3.exoplayer.ExoPlayer? = videoPlayer

    // 🔥 ওয়াইফাই নাকি মোবাইল ডেটা, সেটা চেক করার স্মার্ট ফাংশন
    private fun isWifiConnected(context: android.content.Context): Boolean {
        val connectivityManager = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
    }

    // ------------------------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------------------------

    /**
     * NEVER read `mediaController.duration` from a background thread: Media3 throws
     * IllegalStateException("wrong thread"). `_duration` is a StateFlow -> thread-safe.
     */
    private fun audioDurationNowMs(song: Song): Long {
        if (song.duration > 0L) return song.duration
        val d = _duration.value
        return if (d > 0L && !_isVideoModeActive.value && _currentSong.value?.id == song.id) d else 0L
    }

    private suspend fun awaitAudioDurationMs(song: Song): Long {
        audioDurationNowMs(song).takeIf { it > 0L }?.let { return it }
        return kotlinx.coroutines.withTimeoutOrNull(600L) {
            _duration.first { it > 0L && !_isVideoModeActive.value && _currentSong.value?.id == song.id }
        } ?: 0L
    }

    /** Intro offset between audio and video (SponsorBlock first, duration difference as fallback). */
    private suspend fun buildIntroPositionMap(song: Song, videoId: String, videoDurationMs: Long?): CounterpartPositionMap? {
        val audioDurMs = audioDurationNowMs(song)
        val mapDurMs = if (audioDurMs > 0L) audioDurMs else 3_600_000L
        try {
            val segments = com.musicplayer.lytrix.service.SponsorBlockManager.fetchSegments(videoId, listOf("music_offtopic", "intro"))
            val intro = segments.firstOrNull { it.startMs <= 3500L }
            if (intro != null && intro.endMs > 1000L) {
                timber.log.Timber.d("Intro offset for ${song.title} via SponsorBlock: ${intro.endMs} ms")
                return CounterpartPositionMap(listOf(CounterpartSegment(0L, intro.endMs, mapDurMs)))
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            timber.log.Timber.w(e, "SponsorBlock intro query failed for $videoId")
        }
        if (videoDurationMs != null && audioDurMs > 0L && videoDurationMs > audioDurMs + 2500L) {
            val delta = (videoDurationMs - audioDurMs).coerceIn(0L, 90_000L)
            timber.log.Timber.d("Estimated intro offset for ${song.title}: $delta ms")
            return CounterpartPositionMap(listOf(CounterpartSegment(0L, delta, audioDurMs)))
        }
        return null
    }

    /**
     * Finds the video that belongs to this song. ONLY official music videos (OMV) are ever used.
     *  - 16:9 songs / songs that turn out to be their own video: the song itself is the video.
     *  - otherwise: YouTube's own counterpart (/next) first (if it is an OMV), then the OMV-only search.
     * Returns null when there is NO official video. If we are sure there is none, the song is remembered as
     * "unavailable" and the UI stays on the album cover with the "No music video found" toast.
     */
    private suspend fun resolveCounterpart(song: Song, rawId: String, is16x9: Boolean): MusicVideoCounterpart? {
        findCounterpart(song)?.let { return it }

        fun selfCounterpart(reason: String): MusicVideoCounterpart {
            val self = MusicVideoCounterpart(rawId, rawId, "MUSIC_VIDEO_TYPE_OMV", null)
            storeCounterpart(song, rawId, self)
            timber.log.Timber.d("Song is its own music video ($reason): $rawId")
            return self
        }

        if (song.isVideo && song.filePath != "ONLINE" && song.filePath != "ONLINE_LYTRIX") {
            return selfCounterpart("local")
        }

        // 16:9 YouTube video track: the song's own videoId IS its video — return immediately
        // with 0 network lookups so it never goes through 1:1 counterpart / album / search fallback!
        if (is16x9 || has16by9Thumbnail(song)) {
            return selfCounterpart("16x9")
        }

        val isNonOfficialVariant = com.musicplayer.lytrix.innertube.MusicVideoMatcher
            .isNonOfficialTrack(song.title, song.artist)

        // A. YouTube's authoritative counterpart (/next) + Album ID + source video type discovery
        var discoveredAlbumId: String? = null
        try {
            val (official, albumId, sourceMvType) = YouTube.getCounterpartAndAlbum(rawId)
            discoveredAlbumId = albumId
            // If YouTube Music's own /next says this track IS already a video (OMV/UGC/VIDEO/LIVE),
            // play its own videoId directly as a 16:9 self-counterpart!
            if (com.musicplayer.lytrix.innertube.MusicVideoMatcher.isSourceVideoType(sourceMvType)) {
                return selfCounterpart("/next-source-$sourceMvType")
            }
            if (official != null && official.targetVideoId.isNotBlank()) {
                if (official.targetVideoId == rawId) {
                    if (!isNonOfficialVariant) return selfCounterpart("/next")
                } else if (com.musicplayer.lytrix.innertube.MusicVideoMatcher.isAcceptableVideoType(
                        official.targetMusicVideoType, unknownIsOk = false)
                ) {
                    val withMap = if (official.positionMap != null) official
                    else official.copy(positionMap = buildIntroPositionMap(song, official.targetVideoId, null))
                    storeCounterpart(song, rawId, withMap)
                    timber.log.Timber.d("Counterpart (/next) for ${song.title}: ${withMap.targetVideoId} map=${withMap.positionMap != null}")
                    return withMap
                } else {
                    timber.log.Timber.d("Counterpart (/next) for ${song.title} ignored, not an OMV: ${official.targetMusicVideoType}")
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            timber.log.Timber.w(e, "Counterpart resolution failed for $rawId")
        }

        // B. Official Album Release Lookup (ALBUM_RELEASE) - ONLY using the song's OWN albumId from /next!
        // Never blindly search FILTER_ALBUM (which can return a different single by the same artist).
        val albumIdToLookup = discoveredAlbumId
        if (!albumIdToLookup.isNullOrBlank()) {
            try {
                val albumVideo = YouTube.lookupAlbumVideo(song.title, albumIdToLookup, rawId)
                if (albumVideo != null && albumVideo.targetVideoId.isNotBlank()) {
                    val withMap = if (albumVideo.positionMap != null) albumVideo
                    else albumVideo.copy(positionMap = buildIntroPositionMap(song, albumVideo.targetVideoId, null))
                    storeCounterpart(song, rawId, withMap)
                    timber.log.Timber.tag("VIDEO_LOAD").d("💿 [ALBUM_RELEASE_MATCH] Found official album video for '${song.title}': ${withMap.targetVideoId} map=${withMap.positionMap != null}")
                    return withMap
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                timber.log.Timber.w(e, "Album video lookup failed for $albumIdToLookup")
            }
        }

        // C. OMV-only search fallback (artists are split, "feat." is stripped, duration check is loose)
        var searchCompleted = false
        try {
            val artists = com.musicplayer.lytrix.innertube.MusicVideoMatcher.splitArtistNames(listOf(song.artist))
            val audioDurSec = awaitAudioDurationMs(song).takeIf { it > 0L }?.div(1000L)
            val queries = com.musicplayer.lytrix.innertube.MusicVideoMatcher
                .buildVideoSearchQueries(song.title, artists)
            val excludeId = if (isNonOfficialVariant) rawId else null
            for (query in queries) {
                val result = YouTube.search(query, YouTube.SearchFilter.FILTER_VIDEO).getOrNull() ?: continue
                searchCompleted = true
                val items = result.items.filterIsInstance<SongItem>()
                // Debug: shows exactly which musicVideoType YouTube sent for every result (OMV / UGC / ATV)
                timber.log.Timber.d("Video search '$query' -> " + items.take(8).joinToString { s ->
                    val t = s.endpoint?.watchEndpointMusicSupportedConfigs?.watchEndpointMusicConfig?.musicVideoType
                    "${s.id}[${t?.removePrefix("MUSIC_VIDEO_TYPE_")}] ${s.title}"
                })
                val candidate = com.musicplayer.lytrix.innertube.MusicVideoMatcher.findBestMatch(
                    trackTitle = song.title,
                    trackArtistNames = artists,
                    trackDurationSec = audioDurSec,
                    candidates = items,
                    excludeVideoId = excludeId
                )
                if (candidate != null && candidate.id.isNotBlank()) {
                    // The song's own id IS the official video (e.g. Alone, Pt. II): use it, do NOT reject it.
                    if (candidate.id == rawId) {
                        if (!isNonOfficialVariant) return selfCounterpart("search")
                    } else {
                        val mvType = candidate.endpoint?.watchEndpointMusicSupportedConfigs
                            ?.watchEndpointMusicConfig?.musicVideoType ?: "MUSIC_VIDEO_TYPE_OMV"
                        val candDurMs = (candidate.duration ?: 0) * 1000L
                        val cp = MusicVideoCounterpart(
                            sourceVideoId = rawId,
                            targetVideoId = candidate.id,
                            targetMusicVideoType = mvType,
                            positionMap = buildIntroPositionMap(song, candidate.id, candDurMs.takeIf { it > 0L })
                        )
                        storeCounterpart(song, rawId, cp)
                        timber.log.Timber.d("Matcher counterpart for ${song.title}: ${cp.targetVideoId} map=${cp.positionMap != null}")
                        return cp
                    }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            timber.log.Timber.w(e, "Search fallback failed for ${song.title}")
        }

        if (searchCompleted) markVideoUnavailable(song, rawId)
        return null
    }

    /**
     * Stream resolution.
     *  - "Auto"/"360p": NewPipe PROGRESSIVE MUXED stream first (ONE http connection, starts in ~300 ms).
     *  - higher qualities: InnerTubeX (video-only + audio, merged) first, NewPipe as fallback.
     *  InnerTubeX silently falls back to an AUDIO-ONLY url when the id has no video; that is rejected here.
     */
    private suspend fun resolveVideoStreamPayload(videoId: String, quality: String): VideoStreamPayload? {
        val cacheKey = "$videoId|$quality"
        val nowMs = System.currentTimeMillis()
        videoStreamCache[cacheKey]
            ?.takeIf { nowMs - it.second < VIDEO_CACHE_TTL_MS }
            ?.let {
                timber.log.Timber.tag("VIDEO_LOAD").d("⚡ [EXTRACT_CACHE_HIT] videoId: $videoId (Cached ${it.first.videoUrl.take(60)}...)")
                return it.first
            }

        val maxHeight = when (quality) {
            "1080p" -> 1080
            "720p" -> 720
            "480p" -> 480
            "360p" -> 360
            else -> 720
        }

        timber.log.Timber.tag("VIDEO_LOAD").d("🎬 [EXTRACT_START] videoId: $videoId, quality: $quality, maxHeight: ${maxHeight}p")

        suspend fun viaNewPipe(): VideoStreamPayload? = try {
            getYoutubeVideoStreamUrl(videoId, quality)?.takeIf { it.videoUrl.isNotBlank() }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            timber.log.Timber.w(e, "NewPipe video extraction failed for $videoId")
            null
        }

        suspend fun viaInnerTube(): VideoStreamPayload? = try {
            val r = InnerTubeXPlayer.extractVideoStream(videoId, maxHeight).getOrNull()
            if (r != null && r.url.isNotBlank() && (r.audioUrl != null || (r.height ?: 0) > 0)) {
                VideoStreamPayload(r.url, r.audioUrl)
            } else null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            timber.log.Timber.w(e, "InnerTubeX video extraction failed for $videoId")
            null
        }

        // 🚀 FAST HYBRID STREAM RESOLUTION:
        // InnerTubeX responds in ~350ms (direct JSON API); NewPipe scrapes full HTML in 2-3s.
        // We run both concurrently: if InnerTubeX yields a valid stream, use it immediately and cancel NewPipe;
        // if InnerTubeX fails, NewPipe acts as seamless safety fallback.
        val extractStartTime = android.os.SystemClock.elapsedRealtime()
        val payload = kotlinx.coroutines.coroutineScope {
            val itDeferred = async { viaInnerTube() }
            val npDeferred = async { viaNewPipe() }
            val itResult = itDeferred.await()
            val itTimeMs = android.os.SystemClock.elapsedRealtime() - extractStartTime
            if (itResult != null) {
                npDeferred.cancel()
                timber.log.Timber.tag("VIDEO_LOAD").d("⚡ [ENGINE: INNER_TUBE_X] Success in ${itTimeMs}ms! Video: ${itResult.videoUrl.take(80)}... | Audio: ${itResult.audioUrl?.take(40) ?: "Muxed"}")
                itResult
            } else {
                timber.log.Timber.tag("VIDEO_LOAD").w("⚠️ [ENGINE: INNER_TUBE_X] Failed in ${itTimeMs}ms. Waiting for NewPipe fallback...")
                val npResult = npDeferred.await()
                val npTimeMs = android.os.SystemClock.elapsedRealtime() - extractStartTime
                if (npResult != null) {
                    timber.log.Timber.tag("VIDEO_LOAD").d("🐢 [ENGINE: NEW_PIPE] Success in ${npTimeMs}ms! Video: ${npResult.videoUrl.take(80)}...")
                } else {
                    timber.log.Timber.tag("VIDEO_LOAD").e("❌ [ENGINE: BOTH_FAILED] Failed to extract stream via InnerTubeX and NewPipe (${npTimeMs}ms)")
                }
                npResult
            }
        }
        if (payload != null) videoStreamCache[cacheKey] = Pair(payload, nowMs)
        return payload
    }

    /**
     * Leaves video mode (or cancels a pending hand-off) and hands playback back to the audio player.
     * In the pending case the audio never stopped, so nothing has to be restarted.
     */
    private fun abortVideoMode(message: String) {
        val handoffWasPending = _isVideoHandoffPending.value
        if (!_isVideoModeActive.value && !handoffWasPending) return

        val wantPlay = _isPlaying.value
        cancelVideoHandoff()
        pendingVideoSeekMs = null
        pendingVideoPlayWhenReady = null
        videoPlayer?.playWhenReady = false
        videoPlayer?.volume = 0f

        if (_isVideoModeActive.value) {
            _isVideoModeActive.value = false
            val controller = mediaController
            if (controller != null) {
                if (wantPlay) controller.play() else controller.pause()
                _currentPosition.value = controller.currentPosition
                controller.duration.takeIf { it > 0L }?.let { _duration.value = it }
                _isBuffering.value = controller.playbackState == androidx.media3.common.Player.STATE_BUFFERING
            }
            _isPlaying.value = wantPlay
        }
        appContext?.let { Toast.makeText(it, message, Toast.LENGTH_SHORT).show() }
    }

    /** ONE place that changes the video play/pause state, so UI + player + pending flag never disagree. */
    private fun setVideoPlayIntent(play: Boolean) {
        pendingVideoPlayWhenReady = play
        val vp = videoPlayer
        if (vp != null) {
            if (play && vp.playbackState == androidx.media3.common.Player.STATE_ENDED) vp.seekTo(0L)
            vp.playWhenReady = play
        }
        _isPlaying.value = play
        if (play) startProgressPoll()
    }

    // ------------------------------------------------------------------------------------------
    // Player creation
    // ------------------------------------------------------------------------------------------

    private fun getOrCreateVideoPlayer(context: android.content.Context): androidx.media3.exoplayer.ExoPlayer {
        if (videoPlayer == null) {
            val isWifi = isWifiConnected(context)
            val maxBufferTime = if (isWifi) 50000 else 15000
            val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    15000,          // min buffer
                    maxBufferTime,  // max buffer
                    800,            // start playback after 800ms buffered
                    1200            // resume after rebuffer (1.2s instead of 2.5s for fast handoff)
                )
                .build()

            val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
                .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                .build()

            videoPlayer = androidx.media3.exoplayer.ExoPlayer.Builder(context.applicationContext)
                .setLoadControl(loadControl)
                .setAudioAttributes(audioAttributes, false)
                .build().apply {
                    playbackParameters = com.musicplayer.lytrix.service.PlaybackService.currentPlaybackParameters
                    val is16x9 = _currentSong.value?.let { isSong16by9(it) } ?: false
                    volume = if (_isVideoModeActive.value && !is16x9) 1f else 0f
                    setSeekParameters(androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC)
                    addListener(object : androidx.media3.common.Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == androidx.media3.common.Player.STATE_READY) {
                                _isVideoPlayerReady.value = true
                                // While the hand-off coroutine is cueing the video it alone decides play / pause
                                if (!_isVideoHandoffPending.value) {
                                    // Idempotent: pendingVideoPlayWhenReady always equals the user's latest intent
                                    pendingVideoPlayWhenReady?.let { play -> playWhenReady = play }
                                    if (_isVideoModeActive.value && duration > 0) {
                                        val isCurrent16x9 = _currentSong.value?.let { isSong16by9(it) } ?: false
                                        if (!isCurrent16x9) {
                                            _duration.value = duration
                                        }
                                    }
                                }
                            }
                        }

                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            // _isPlaying is mirrored from the INTENT by the progress poll, not from isPlaying
                            // (isPlaying flickers to false while buffering and made the UI jump).
                            if (_isVideoModeActive.value && isPlaying) {
                                startProgressPoll()
                            }
                        }

                        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                            timber.log.Timber.w(error, "ExoPlayer video error: ${error.message}")
                            _isVideoPlayerReady.value = false
                            videoStreamCache.clear() // expired / rejected url -> re-extract next time
                            // never leave the user on a black screen / endless spinner
                            abortVideoMode("Video playback failed")
                            // drop the broken source so the next preload starts clean
                            stop()
                            clearMediaItems()
                        }
                    })
                }
        }

        com.musicplayer.lytrix.service.PlaybackService.activeVideoPlayerRef = videoPlayer
        return videoPlayer!!
    }

    // ------------------------------------------------------------------------------------------
    // Pre-warm
    // ------------------------------------------------------------------------------------------

    // 🔥 গান পাল্টালে বা কোয়ালিটি পাল্টালে কল হবে — eligible হলে ব্যাকগ্রাউন্ডে বাফার শুরু করবে
    fun preWarmVideoIfEligible(context: Context, song: Song) {
        val isYtSong = isYouTubeTrack(song)
        if (!isYtSong) {
            releaseVideoPlayer() // লোকাল/জিওসাভন গানে ভিডিও নেই
            return
        }

        val isPreloadEnabled = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
            .getBoolean("video_background_preload", true)
        val userWantsVideoNow = _isVideoModeActive.value || _isVideoHandoffPending.value
        if (!isPreloadEnabled && !userWantsVideoNow) {
            return
        }
        if (isInListenTogetherRoom() && !isSong16by9(song)) {
            return
        }

        val quality = _videoQualityPref.value
        val sameSong = preWarmedSongId == song.id
        val sameQuality = preWarmedQuality == quality

        // Same song + same quality: NEVER cancel / restart anything that is running or already loaded
        if (sameSong && sameQuality) {
            if (preWarmJob?.isActive == true) return                 // still resolving -> let it finish
            if ((videoPlayer?.mediaItemCount ?: 0) > 0) return       // source already set (ready or buffering)
            if (isVideoKnownUnavailable(song)) return                // nothing to load
        }

        // A hand-off that belongs to ANOTHER song is obsolete now
        if (_isVideoHandoffPending.value && handoffSongId != song.id) cancelVideoHandoff()

        // Quality changed while watching: remember where the video is so we can resume exactly there
        var resumeVideoPositionMs: Long? = null
        val currentVp = videoPlayer
        if (sameSong && !sameQuality && _isVideoModeActive.value && currentVp != null && _isVideoPlayerReady.value) {
            resumeVideoPositionMs = currentVp.currentPosition
            pendingVideoPlayWhenReady = _isPlaying.value
            currentVp.playWhenReady = false
        }
        if (!sameSong) {
            currentVp?.stop()
            currentVp?.clearMediaItems()
        }
        preWarmJob?.cancel()

        _isVideoPlayerReady.value = false
        preWarmedSongId = song.id
        preWarmedQuality = quality
        val resumeAtVideoMs = resumeVideoPositionMs

        preWarmJob = viewModelScope.launch(Dispatchers.IO) {
            val rawId = getEffectiveYouTubeVideoId(context, song)
                ?: song.originalId?.takeIf { it.isNotBlank() && !it.all(Char::isDigit) }
                ?: runCatching { android.net.Uri.parse(song.uriString).getQueryParameter("v") }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: song.originalId
                ?: song.id.toString()

            // 1. Which video belongs to this song?
            val counterpart = resolveCounterpart(song, rawId, isSong16by9(song))
            if (counterpart == null) {
                withContext(Dispatchers.Main) {
                    if (preWarmedSongId != song.id) return@withContext
                    // abortVideoMode() does nothing unless the user is in (or waiting for) video mode
                    if (isVideoKnownUnavailable(song)) abortVideoMode("No music video found for this song")
                    else abortVideoMode("Couldn't look up the video. Check your connection.")
                }
                return@launch
            }

            // 2. Stream url (fast muxed first for Auto)
            val payload = resolveVideoStreamPayload(counterpart.targetVideoId, quality)
            if (payload == null) {
                timber.log.Timber.e("Could not extract any video stream for ${song.title}")
                withContext(Dispatchers.Main) {
                    if (preWarmedSongId == song.id) abortVideoMode("Couldn't load the video stream")
                }
                return@launch
            }

            val finalVideoUrl = payload.videoUrl
            val finalAudioUrl = payload.audioUrl

            withContext(Dispatchers.Main) {
                // ইতিমধ্যে অন্য গানে চলে গেলে পুরনো রেজাল্ট প্রয়োগ করো না
                if (preWarmedSongId != song.id) return@withContext
                val player = getOrCreateVideoPlayer(context)

                val httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
                    .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .setAllowCrossProtocolRedirects(true)
                    .setConnectTimeoutMs(15000)
                    .setReadTimeoutMs(15000)

                val upstreamFactory = androidx.media3.datasource.DefaultDataSource.Factory(context.applicationContext, httpDataSourceFactory)
                val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(upstreamFactory)

                val mediaSource = if (!finalAudioUrl.isNullOrBlank()) {
                    val videoSource = mediaSourceFactory.createMediaSource(androidx.media3.common.MediaItem.fromUri(finalVideoUrl))
                    val audioSource = mediaSourceFactory.createMediaSource(androidx.media3.common.MediaItem.fromUri(finalAudioUrl))
                    androidx.media3.exoplayer.source.MergingMediaSource(videoSource, audioSource)
                } else {
                    mediaSourceFactory.createMediaSource(androidx.media3.common.MediaItem.fromUri(finalVideoUrl))
                }

                player.setMediaSource(mediaSource)
                player.prepare()

                // The audio is still the sound source and the hand-off coroutine will cue + start the video itself.
                if (_isVideoHandoffPending.value) {
                    player.volume = 0f
                    player.playWhenReady = false
                    return@withContext
                }

                val videoModeNow = _isVideoModeActive.value
                val is16x9 = isSong16by9(song)
                // AUDIO timeline position -> mapped NOW, counterpart is guaranteed to be stored by resolveCounterpart()
                val audioPosMs = pendingVideoSeekMs ?: mediaController?.currentPosition ?: _currentPosition.value
                val targetPos = if (is16x9) {
                    mediaController?.currentPosition ?: audioPosMs
                } else {
                    resumeAtVideoMs ?: mapAudioToVideoPosition(song.id.toString(), audioPosMs)
                }
                val wantPlay = videoModeNow && if (is16x9) {
                    mediaController?.playWhenReady ?: _isPlaying.value
                } else {
                    pendingVideoPlayWhenReady ?: _isPlaying.value
                }

                player.seekTo(targetPos)
                player.volume = if (videoModeNow && !is16x9) 1f else 0f
                player.playWhenReady = wantPlay

                if (videoModeNow) {
                    pendingVideoPlayWhenReady = wantPlay   // kept: STATE_READY listener re-applies the latest intent
                    if (!is16x9) {
                        _currentPosition.value = targetPos
                        _isPlaying.value = wantPlay
                    }
                    startProgressPoll()
                }
                pendingVideoSeekMs = null
                // _isVideoPlayerReady becomes true in the listener, at the real STATE_READY
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Audio <-> Video switching  (GAPLESS)
    // ------------------------------------------------------------------------------------------

    /**
     * Audio -> Video.
     * The audio keeps playing the whole time. The video is loaded, seeked and cued (muted + paused) a little AHEAD of
     * the audio; the moment the audio reaches that point the sound is switched to the video in one step.
     * If anything fails or takes > 30 s, we simply stay on the audio (toast) - the music never stops.
     */
    fun switchToVideoMode(song: Song) {
        if (song.isVideo || _isVideoModeActive.value || _isVideoHandoffPending.value) return
        if (notifyIfVideoUnavailable(song)) return // no video exists -> stay on artwork, no spinner

        val is16x9 = isSong16by9(song)
        if (isInListenTogetherRoom() && !is16x9) {
            appContext?.let { ctx ->
                Toast.makeText(ctx, "Video mode for album tracks is disabled in Listen Together", Toast.LENGTH_SHORT).show()
            }
            return
        }

        if (is16x9) {
            // 🔥 16:9 VIDEO TRACK: 0ms delay, no handoff loop.
            // Audio ALWAYS keeps playing uninterrupted on mediaController; video runs muted (volume = 0f) in sync.
            timber.log.Timber.tag("VIDEO_LOAD").d("⚡ [16:9 FAST_PATH] Instant switch (0ms delay) for 16:9 track: '${song.title}' (${song.id})")
            _isVideoModeActive.value = true
            _isVideoHandoffPending.value = false
            val controller = mediaController
            val currentPos = controller?.currentPosition ?: _currentPosition.value
            val wantPlay = controller?.playWhenReady ?: _isPlaying.value
            pendingVideoPlayWhenReady = wantPlay
            val vp = videoPlayer
            if (vp != null && preWarmedSongId == song.id && vp.mediaItemCount > 0) {
                vp.volume = 0f
                vp.playbackParameters = com.musicplayer.lytrix.service.PlaybackService.currentPlaybackParameters
                vp.setSeekParameters(androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC)
                vp.seekTo(currentPos)
                vp.playWhenReady = wantPlay
            } else {
                appContext?.let { ctx -> preWarmVideoIfEligible(ctx, song) }
            }
            return
        }

        // 🔥 1:1 ALBUM TRACK (Counterpart OMV with intro/dialogue):
        // MetroList style: audio keeps playing, spinner on video button, instant swap when STATE_READY!
        timber.log.Timber.tag("VIDEO_LOAD").d("🎬 [1:1 HANDOFF_START] Initiating handoff for album track: '${song.title}' (${song.id})")
        handoffSongId = song.id
        _isVideoHandoffPending.value = true

        val vp = videoPlayer
        val sourceApplied = vp != null && preWarmedSongId == song.id && vp.mediaItemCount > 0
        if (!sourceApplied) {
            appContext?.let { ctx -> preWarmVideoIfEligible(ctx, song) }
        }
        startVideoHandoff(song)
    }

    private suspend fun awaitVideoReady(vp: androidx.media3.exoplayer.ExoPlayer, deadlineMs: Long): Boolean {
        while (true) {
            val state = vp.playbackState
            if (state == androidx.media3.common.Player.STATE_READY) return true
            if (state == androidx.media3.common.Player.STATE_IDLE) return false
            if (android.os.SystemClock.elapsedRealtime() > deadlineMs) return false
            kotlinx.coroutines.delay(40L)
        }
    }

    private fun startVideoHandoff(song: Song) {
        handoffJob?.cancel()
        handoffJob = viewModelScope.launch {
            val handoffStartTime = android.os.SystemClock.elapsedRealtime()
            val key = song.id.toString()
            val deadline = handoffStartTime + HANDOFF_TIMEOUT_MS

            // 1. Wait until video stream is ready in player
            while (true) {
                if (_currentSong.value?.id != song.id) { cancelVideoHandoff(); return@launch }
                if (android.os.SystemClock.elapsedRealtime() > deadline) {
                    timber.log.Timber.tag("VIDEO_LOAD").e("❌ [STREAM_TIMEOUT] Stream preparation timed out after ${android.os.SystemClock.elapsedRealtime() - handoffStartTime}ms")
                    abortVideoMode("Video is taking too long to load")
                    return@launch
                }
                val controller = mediaController
                val vp = videoPlayer
                if (controller != null && vp != null && preWarmedSongId == song.id && vp.mediaItemCount > 0) {
                    break
                }
                kotlinx.coroutines.delay(25L)
            }

            val controller = mediaController ?: run { cancelVideoHandoff(); return@launch }
            val vp = videoPlayer ?: run { cancelVideoHandoff(); return@launch }
            val streamWaitMs = android.os.SystemClock.elapsedRealtime() - handoffStartTime
            timber.log.Timber.tag("VIDEO_LOAD").d("📦 [STREAM_READY] MediaSource prepared in ${streamWaitMs}ms")

            // 2. Seek video to mapped position while muted
            val currentAudioPos = controller.currentPosition
            val targetVideoPos = mapAudioToVideoPosition(key, currentAudioPos)
            val introDelta = targetVideoPos - currentAudioPos
            timber.log.Timber.tag("VIDEO_LOAD").d("⏱️ [SEEK] Audio: ${currentAudioPos}ms -> Target Video: ${targetVideoPos}ms (Intro Offset: ${introDelta}ms)")
            vp.volume = 0f
            vp.setSeekParameters(androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC)
            vp.seekTo(targetVideoPos)

            // 3. Wait for video buffer (STATE_READY)
            val bufferStartTime = android.os.SystemClock.elapsedRealtime()
            while (vp.playbackState != androidx.media3.common.Player.STATE_READY) {
                if (_currentSong.value?.id != song.id) { cancelVideoHandoff(); return@launch }
                if (android.os.SystemClock.elapsedRealtime() > deadline) {
                    val bufWaitMs = android.os.SystemClock.elapsedRealtime() - bufferStartTime
                    timber.log.Timber.tag("VIDEO_LOAD").e("❌ [BUFFER_TIMEOUT] CDN buffering timed out after ${bufWaitMs}ms")
                    abortVideoMode("Video is taking too long to buffer")
                    return@launch
                }
                if (vp.playbackState == androidx.media3.common.Player.STATE_IDLE) {
                    timber.log.Timber.tag("VIDEO_LOAD").e("❌ [PLAYER_ERROR] Video playback failed in STATE_IDLE")
                    abortVideoMode("Video playback failed")
                    return@launch
                }
                kotlinx.coroutines.delay(25L)
            }
            val bufferDurationMs = android.os.SystemClock.elapsedRealtime() - bufferStartTime
            timber.log.Timber.tag("VIDEO_LOAD").d("✅ [BUFFER_READY] CDN buffer reached STATE_READY in ${bufferDurationMs}ms")

            // 4. ATOMIC SWAP:
            // Safety guard: if the track resolved as its own 16:9 video while handoff was waiting
            // (e.g. square thumbnail whose /next revealed OMV/UGC), NEVER pause mediaController!
            // Keep audio playing on mediaController and run the video silently (volume = 0f) in sync.
            if (isSong16by9(song)) {
                val wantPlay = controller.playWhenReady
                _isVideoModeActive.value = true
                _isVideoHandoffPending.value = false
                handoffJob = null
                pendingVideoPlayWhenReady = wantPlay

                vp.volume = 0f
                vp.playbackParameters = com.musicplayer.lytrix.service.PlaybackService.currentPlaybackParameters
                vp.seekTo(controller.currentPosition)
                vp.playWhenReady = wantPlay

                _isPlaying.value = wantPlay
                _currentPosition.value = controller.currentPosition
                startProgressPoll()
                val totalHandoffMs = android.os.SystemClock.elapsedRealtime() - handoffStartTime
                timber.log.Timber.tag("VIDEO_LOAD").d("⚡ [16:9 HANDOFF_SYNC] Track is 16:9; kept audio on mediaController and synced muted video in ${totalHandoffMs}ms")
                return@launch
            }

            // Save true audio duration before switching to 1:1 video so Video -> Audio can detect
            // large duration mismatches (e.g. Zaalima 2:52 video vs 4:59 audio) accurately!
            val audioDurSnapshot = controller.duration.takeIf { it != androidx.media3.common.C.TIME_UNSET && it > 0L }
                ?: song.duration.takeIf { it > 0L }
                ?: _duration.value.takeIf { it > 0L }
            if (audioDurSnapshot != null) {
                lastAudioDurationBeforeVideoMs = audioDurSnapshot
            }
            val videoDurSnapshot = vp.duration.takeIf { it != androidx.media3.common.C.TIME_UNSET && it > 0L }

            // Audio kept playing without interruption. Align final position if audio moved:
            val finalAudioPos = controller.currentPosition
            val exactVideoPos = mapAudioToVideoPosition(key, finalAudioPos, videoDurSnapshot)
            if (kotlin.math.abs(vp.currentPosition - exactVideoPos) > 150L) {
                vp.seekTo(exactVideoPos)
            }

            // Remap or clear A-B repeat points cleanly for the video timeline
            remapABForModeSwitch(
                song = song,
                toVideo = true,
                audioDurationMs = audioDurSnapshot,
                videoDurationMs = videoDurSnapshot,
            )

            val wantPlay = controller.playWhenReady
            _isVideoModeActive.value = true
            _isVideoHandoffPending.value = false
            handoffJob = null

            vp.volume = com.musicplayer.lytrix.service.PlaybackService.sleepTimerGain
            vp.playbackParameters = com.musicplayer.lytrix.service.PlaybackService.currentPlaybackParameters
            vp.playWhenReady = wantPlay
            controller.pause()

            _isPlaying.value = wantPlay
            _currentPosition.value = vp.currentPosition
            if (vp.duration > 0L) _duration.value = vp.duration
            startProgressPoll()
            val totalHandoffMs = android.os.SystemClock.elapsedRealtime() - handoffStartTime
            timber.log.Timber.tag("VIDEO_LOAD").d("🚀 [HANDOFF_COMPLETE] Video playing at ${vp.currentPosition}ms | Total handoff time: ${totalHandoffMs}ms")
        }
    }

    /** Stops a running / pending audio->video hand-off. The audio is untouched, so nothing else is needed. */
    private fun cancelVideoHandoff() {
        handoffJob?.cancel()
        handoffJob = null
        if (_isVideoHandoffPending.value) {
            timber.log.Timber.tag("VIDEO_LOAD").w("⏹️ [HANDOFF_CANCELLED] Video handoff cancelled")
            _isVideoHandoffPending.value = false
            videoPlayer?.let {
                it.playWhenReady = false
                it.volume = 0f
                it.setSeekParameters(androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC)
            }
        }
    }

    fun switchToAudioMode(song: Song) {
        if (song.isVideo) return

        timber.log.Timber.tag("VIDEO_LOAD").d("🔄 [SWITCH_TO_AUDIO] Returning to audio mode for '${song.title}'")

        // User tapped the audio icon while the video was still loading: the audio never stopped -> just cancel.
        if (_isVideoHandoffPending.value) {
            cancelVideoHandoff()
            return
        }

        // The audio icon in the UI calls this even when we are already in audio mode.
        if (!_isVideoModeActive.value) return

        val is16x9 = isSong16by9(song)
        if (is16x9) {
            // 16:9 track: mediaController was already playing audio (safety-resume if somehow paused)
            _isVideoModeActive.value = false
            videoPlayer?.playWhenReady = false
            videoPlayer?.volume = 0f
            val controller = mediaController
            if (_isPlaying.value && controller != null && !controller.playWhenReady) {
                controller.play()
            }
            return
        }

        // 1:1 track: audio was playing from videoPlayer
        val vp = videoPlayer
        val key = song.id.toString()
        val videoPos = vp?.currentPosition ?: 0L
        val videoDurSnapshot = vp?.duration?.takeIf { it != androidx.media3.common.C.TIME_UNSET && it > 0L }
            ?: _duration.value.takeIf { it > 0L }
        val wasPlaying = vp?.playWhenReady ?: _isPlaying.value
        val controller = mediaController
        val audioDurSnapshot = lastAudioDurationBeforeVideoMs.takeIf { it > 0L }
            ?: song.duration.takeIf { it > 0L }
            ?: controller?.duration?.takeIf { it != androidx.media3.common.C.TIME_UNSET && it > 0L }
        val targetAudioPos = mapVideoToAudioPosition(key, videoPos, videoDurSnapshot)

        // Remap or clear A-B repeat points cleanly for the audio timeline
        remapABForModeSwitch(
            song = song,
            toVideo = false,
            audioDurationMs = audioDurSnapshot,
            videoDurationMs = videoDurSnapshot,
        )

        vp?.playWhenReady = false
        vp?.volume = 0f
        _isVideoModeActive.value = false
        _isVideoHandoffPending.value = false

        if (controller != null) {
            controller.seekTo(targetAudioPos)
            controller.playWhenReady = wasPlaying
            if (wasPlaying) controller.play() else controller.pause()
        }

        _isPlaying.value = wasPlaying
        _isBuffering.value = controller?.playbackState == androidx.media3.common.Player.STATE_BUFFERING
        _currentPosition.value = targetAudioPos
        _duration.value = audioDurSnapshot ?: _duration.value
        startProgressPoll()
    }

    fun toggleVideoMode(song: Song) {
        if (_isVideoModeActive.value || _isVideoHandoffPending.value) {
            switchToAudioMode(song)
        } else {
            switchToVideoMode(song)
        }
    }

    fun releaseVideoPlayer() {
        cancelVideoHandoff()
        preWarmJob?.cancel()
        preWarmedSongId = null
        preWarmedQuality = null
        pendingVideoSeekMs = null
        pendingVideoPlayWhenReady = null
        _isVideoPlayerReady.value = false
        _isVideoModeActive.value = false
        com.musicplayer.lytrix.service.PlaybackService.activeVideoPlayerRef = null
        videoPlayer?.release()
        videoPlayer = null
    }

    // ==========================================

    // 🔥 LOCAL YOUTUBE DATABASE (History & Quick Picks)

    // ==========================================

    fun saveToYtHistory(track: YtMusicTrack) {
        val context = appContext ?: return
        if (com.musicplayer.lytrix.ui.PrivacyPreferencesManager.isPauseListenHistory(context)) return
        if (currentMediaCategory.value in listOf("PODCAST", "AUDIOBOOK")) return
        if (track.id.startsWith("pod_") || track.id.startsWith("ab_")) return
        if (track.title.contains("audiobook", ignoreCase = true) || track.title.contains("podcast", ignoreCase = true) ||
            track.artist.contains("audiobook", ignoreCase = true) || track.artist.contains("podcast", ignoreCase = true) ||
            track.artist.contains("Peaks Audiobooks", ignoreCase = true)) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val prefs = context.getSharedPreferences("yt_history_db", android.content.Context.MODE_PRIVATE)
                val historyJson = prefs.getString("history_list", "[]") ?: "[]"
                val jsonArray = org.json.JSONArray(historyJson)
                val newList = mutableListOf<YtMusicTrack>()
                newList.add(track) // নতুন প্লে করা গানটা সবার ওপরে থাকবে
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val id = obj.getString("id")
                    // ডুপ্লিকেট গান বাদ দেওয়া হচ্ছে এবং ম্যাক্সিমাম ৫০ টা গান সেভ থাকবে
                    if (id != track.id && newList.size < 50) {
                        newList.add(YtMusicTrack(id, obj.getString("title"), obj.getString("artist"), obj.getString("thumbnailUrl")))
                    }
                }

                val newArray = org.json.JSONArray()
                newList.forEach {
                    val obj = org.json.JSONObject()
                    obj.put("id", it.id)
                    obj.put("title", it.title)
                    obj.put("artist", it.artist)
                    obj.put("thumbnailUrl", it.thumbnailUrl)
                    newArray.put(obj)
                }

                prefs.edit().putString("history_list", newArray.toString()).apply()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // 🔥 ২. আরচিভটুনের মতো লোকাল হিস্ট্রি জেনারেটর (Quick Picks না আসলে এটা ব্যাকআপ দেবে)

    private fun loadPlaylistPreviews(cards: List<FeaturedPlaylistCard>) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            cards.forEach { card ->
                try {
                    val cleanId = card.id.removePrefix("VL")
                    val result = com.musicplayer.lytrix.innertube.YouTube.playlist(cleanId)
                    val playlistSongs = result.getOrNull()?.songs
                    if (playlistSongs != null) {
                        val hiddenIds = _notInterestedSongIds.value
                        val nonHiddenSongs = playlistSongs.filterNot { hiddenIds.contains(it.id) }
                        val tracks = nonHiddenSongs.take(7).mapNotNull { ytItem ->
                            val durationString = ytItem.duration?.let { seconds ->
                                val min = seconds / 60
                                val sec = seconds % 60
                                String.format("%d:%02d", min, sec)
                            } ?: ""
                            YtMusicTrack(
                                id = ytItem.id,
                                title = ytItem.title,
                                artist = ytItem.artists?.joinToString(", ") { a -> a.name } ?: "Unknown",
                                thumbnailUrl = ytItem.thumbnail ?: "",
                                playCount = durationString // Storing duration in playCount to reuse the model
                            )
                        }

                        val currentCards = _featuredCards.value.toMutableList()
                        val index = currentCards.indexOfFirst { it.id == card.id }
                        if (index != -1) {
                            currentCards[index] = currentCards[index].copy(previewSongs = tracks)
                            _featuredCards.value = currentCards
                        }
                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun getLocalYtSections(): List<YtMusicSection> {
        val list = mutableListOf<YtMusicTrack>()
        val context = appContext ?: return emptyList()
        try {
            val prefs = context.getSharedPreferences("yt_history_db", android.content.Context.MODE_PRIVATE)
            val historyJson = prefs.getString("history_list", "[]") ?: "[]"
            val jsonArray = org.json.JSONArray(historyJson)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(YtMusicTrack(obj.getString("id"), obj.getString("title"), obj.getString("artist"), obj.getString("thumbnailUrl")))
            }

        } catch (e: Exception) {}
        val sections = mutableListOf<YtMusicSection>()
        if (list.isNotEmpty()) {
            sections.add(YtMusicSection("Keep listening", list.take(15)))
            if (list.size > 3) sections.add(YtMusicSection("Your Quick picks", list.shuffled().take(10)))
        }

        return sections
    }

    fun playNextOnline(context: Context, jioSong: com.musicplayer.lytrix.model.JioSaavnSong) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { Toast.makeText(context, "Adding to Play Next...", Toast.LENGTH_SHORT).show() }
            val safeName = jioSong.name.replace(Regex("[^a-zA-Z0-9 ]"), "_").take(50).trim()
            val localFile = java.io.File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC), "$safeName.mp3")
            val isOffline = localFile.exists()
            var finalUri = ""
            if (isOffline) {
                finalUri = "file://" + localFile.absolutePath
            } else if (jioSong.downloadUrl.isNotBlank()) {
                finalUri = jioSong.downloadUrl
            } else {
                // 🔥 JIT Magic: অরিজিনাল লিংক দেওয়া হলো লিরিক্সের জন্য
                finalUri = "https://www.youtube.com/watch?v=${jioSong.id}"
            }

            if (finalUri.isNotBlank()) {
                val appSong = Song(
                    id = jioSong.id.hashCode().toLong(),
                    title = jioSong.name,
                    artist = jioSong.artist,
                    album = if (isOffline) "Offline Downloaded" else "Play Next",
                    duration = 0L,
                    uriString = finalUri,
                    filePath = if (isOffline) "OFFLINE" else "ONLINE",
                    albumArtUriString = jioSong.imageUrl,
                    isVideo = false,
                    hasLyrics = false,
                    originalId = jioSong.id
                )
                withContext(Dispatchers.Main) {
                    val controller = mediaController ?: return@withContext
                    val mediaItem = androidx.media3.common.MediaItem.Builder()
                        .setMediaId(appSong.id.toString())
                        .setUri(appSong.uriString)
                        .setMediaMetadata(buildQueueMediaMetadata(appSong)).build()
                    val currentList = _currentQueueList.value.toMutableList()
                    val currentIndex = controller.currentMediaItemIndex
                    if (currentIndex >= 0 && currentIndex < currentList.size) {
                        currentList.add(currentIndex + 1, appSong)
                        controller.addMediaItem(currentIndex + 1, mediaItem)
                    } else {
                        currentList.add(appSong)
                        controller.addMediaItem(mediaItem)
                    }

                    _currentQueueList.value = currentList
                    Toast.makeText(context, "Added to Play Next!", Toast.LENGTH_SHORT).show()
                }

            } else {
                withContext(Dispatchers.Main) { Toast.makeText(context, "Failed to load audio!", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    // 🔥 1. Queue Reset: শুধুমাত্র Play Next দিয়ে অ্যাড করা এক্সট্রা গানগুলো রিমুভ করবে

    fun resetPlayNextQueue(context: Context) {
        viewModelScope.launch(Dispatchers.Main) {
            val currentList = _currentQueueList.value.toMutableList()
            val playingId = _currentSong.value?.id
            val controller = mediaController ?: return@launch
            var removedCount = 0
            // পেছনের দিক থেকে স্ক্যান করে রিমুভ করছি যাতে ইনডেক্স ব্রেক না হয়
            for (i in currentList.indices.reversed()) {
                // "Play Next" দিয়ে অ্যাড করা গানগুলোকে আমরা album = "Play Next" দিয়ে ট্যাগ করেছিলাম
                if (currentList[i].album == "Play Next" && currentList[i].id != playingId) {
                    controller.removeMediaItem(i)
                    currentList.removeAt(i)
                    removedCount++
                }
            }

            if (removedCount > 0) {
                _currentQueueList.value = currentList
                Toast.makeText(context, "$removedCount extra songs removed!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "No Play Next songs to remove", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 🔥 2. Queue Delete: বাজতে থাকা গান বাদে পুরো কিউ ডিলিট করে দেবে

    fun clearQueueExceptCurrent(context: Context) {
        viewModelScope.launch(Dispatchers.Main) {
            val currentList = _currentQueueList.value.toMutableList()
            val playingId = _currentSong.value?.id ?: return@launch
            val controller = mediaController ?: return@launch
            val playingIndex = currentList.indexOfFirst { it.id == playingId }
            if (playingIndex != -1) {
                // বর্তমান গানের পরের সব ডিলিট
                for (i in currentList.size - 1 downTo playingIndex + 1) { controller.removeMediaItem(i) }
                // বর্তমান গানের আগের সব ডিলিট
                for (i in playingIndex - 1 downTo 0) { controller.removeMediaItem(i) }
                _currentQueueList.value = listOf(currentList[playingIndex])
                Toast.makeText(context, "Queue Cleared!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ==========================================
    // 🔥 ARTIST PAGE & SUBSCRIBE LOGIC

    // ==========================================
    private val _artistPage = kotlinx.coroutines.flow.MutableStateFlow<com.musicplayer.lytrix.innertube.pages.ArtistPage?>(null)
    val artistPage: kotlinx.coroutines.flow.StateFlow<com.musicplayer.lytrix.innertube.pages.ArtistPage?> = _artistPage.asStateFlow()
    private val _isArtistLoading = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isArtistLoading: kotlinx.coroutines.flow.StateFlow<Boolean> = _isArtistLoading.asStateFlow()

    fun loadArtistPage(artistQuery: String, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            _isArtistLoading.value = true
            _artistPage.value = null
            try {
                val cleanQuery = cleanArtistDisplayName(artistQuery)
                    .replace(Regex("""\d+[\d.,]*\s*[kmbKMB]?\s*(?:views?|plays?|likes?|subscribers?)""", RegexOption.IGNORE_CASE), "")
                    .trim()
                var finalArtistId: String? = null

                val curSong = _currentSong.value
                val isPodcastOrAb = _currentMediaCategory.value in listOf("PODCAST", "AUDIOBOOK") ||
                        curSong?.mediaType in listOf("PODCAST", "AUDIOBOOK") ||
                        curSong?.filePath in listOf("ONLINE_PODCAST", "ONLINE_AUDIOBOOK")

                if ((cleanQuery.startsWith("UC") || cleanQuery.startsWith("HC")) && cleanQuery.length >= 20) {
                    finalArtistId = cleanQuery
                } else if (isPodcastOrAb && curSong != null && !curSong.channelId.isNullOrBlank() &&
                    (curSong.artist.equals(cleanQuery, ignoreCase = true) ||
                     cleanQuery.contains(curSong.artist, ignoreCase = true) ||
                     curSong.artist.contains(cleanQuery, ignoreCase = true))) {
                    finalArtistId = curSong.channelId
                } else if (isPodcastOrAb && (curSong?.channelId?.startsWith("UC") == true || cleanQuery.startsWith("UC"))) {
                    // Podcast-first resolution for YouTube podcast channels only
                    val profileSearch = com.musicplayer.lytrix.innertube.YouTube.search(
                        query = cleanQuery,
                        filter = com.musicplayer.lytrix.innertube.YouTube.SearchFilter.FILTER_PROFILE,
                        useAccountContext = false
                    ).getOrNull()
                    finalArtistId = profileSearch?.items?.filterIsInstance<com.musicplayer.lytrix.innertube.models.ArtistItem>()
                        ?.firstOrNull { it.title.equals(cleanQuery, ignoreCase = true) || it.title.replace(" ", "").equals(cleanQuery.replace(" ", ""), ignoreCase = true) }?.id

                    if (finalArtistId == null) {
                        val podSearch = com.musicplayer.lytrix.innertube.YouTube.search(
                            query = cleanQuery,
                            filter = com.musicplayer.lytrix.innertube.YouTube.SearchFilter.FILTER_PODCAST,
                            useAccountContext = false
                        ).getOrNull()
                        finalArtistId = podSearch?.items?.filterIsInstance<com.musicplayer.lytrix.innertube.models.PodcastItem>()?.firstNotNullOfOrNull { it.author?.id }
                    }
                }

                // 🔥 Apple Podcast & Audiobook Channel Fallback: Prevent collision with random YouTube musicians
                if (isPodcastOrAb && (finalArtistId == null || !finalArtistId.startsWith("UC"))) {
                    val isAb = _currentMediaCategory.value == "AUDIOBOOK" ||
                            curSong?.mediaType == "AUDIOBOOK" ||
                            curSong?.filePath in listOf("ONLINE_AUDIOBOOK", "OFFLINE_AUDIOBOOK") ||
                            cleanQuery.startsWith("AUDIOBOOK_")
                    val safeName = cleanQuery.removePrefix("AUDIOBOOK_").removePrefix("PODCAST_").trim()
                    val thumb = curSong?.albumArtUriString ?: ""

                    if (isAb) {
                        val searchVideos = com.musicplayer.lytrix.innertube.YouTube.search(
                            query = "$safeName audiobook",
                            filter = com.musicplayer.lytrix.innertube.YouTube.SearchFilter.FILTER_VIDEO,
                            useAccountContext = false
                        ).getOrNull()?.items?.filterIsInstance<com.musicplayer.lytrix.innertube.models.SongItem>()?.map { songItem ->
                            songItem.copy(
                                artists = listOf(com.musicplayer.lytrix.innertube.models.Artist(id = "AUDIOBOOK_$safeName", name = safeName))
                            )
                        } ?: emptyList()

                        val section = com.musicplayer.lytrix.innertube.pages.ArtistSection(
                            title = "Audiobooks by $safeName",
                            items = searchVideos,
                            moreEndpoint = null,
                            layout = com.musicplayer.lytrix.innertube.pages.ArtistSectionLayout.GRID
                        )
                        val page = com.musicplayer.lytrix.innertube.pages.ArtistPage(
                            artist = com.musicplayer.lytrix.innertube.models.ArtistItem(
                                id = "AUDIOBOOK_$safeName",
                                title = safeName,
                                thumbnail = thumb.ifBlank { searchVideos.firstOrNull()?.thumbnail },
                                shuffleEndpoint = null,
                                radioEndpoint = null
                            ),
                            sections = listOf(section),
                            description = "Audiobook Author: $safeName"
                        )
                        _artistPage.value = page
                        _isArtistLoading.value = false
                        return@launch
                    } else {
                        val podEpisodes = com.musicplayer.lytrix.innertube.YouTube.search(
                            query = "$safeName podcast",
                            filter = com.musicplayer.lytrix.innertube.YouTube.SearchFilter.FILTER_VIDEO,
                            useAccountContext = false
                        ).getOrNull()?.items?.filterIsInstance<com.musicplayer.lytrix.innertube.models.SongItem>()?.map { songItem ->
                            songItem.copy(
                                artists = listOf(com.musicplayer.lytrix.innertube.models.Artist(id = "PODCAST_$safeName", name = safeName))
                            )
                        } ?: emptyList()

                        val section = com.musicplayer.lytrix.innertube.pages.ArtistSection(
                            title = "Episodes from $safeName",
                            items = podEpisodes,
                            moreEndpoint = null,
                            layout = com.musicplayer.lytrix.innertube.pages.ArtistSectionLayout.LIST
                        )
                        val page = com.musicplayer.lytrix.innertube.pages.ArtistPage(
                            artist = com.musicplayer.lytrix.innertube.models.ArtistItem(
                                id = "PODCAST_$safeName",
                                title = safeName,
                                thumbnail = thumb.ifBlank { podEpisodes.firstOrNull()?.thumbnail },
                                shuffleEndpoint = null,
                                radioEndpoint = null
                            ),
                            sections = listOf(section),
                            description = "Podcast Show: $safeName"
                        )
                        _artistPage.value = page
                        _isArtistLoading.value = false
                        return@launch
                    }
                }

                if (finalArtistId == null) {
                    val summaryResult = com.musicplayer.lytrix.innertube.YouTube.searchSummary(query = cleanQuery, useAccountContext = false).getOrNull()
                    if (summaryResult != null) {
                        for (summary in summaryResult.summaries) {
                            for (item in summary.items) {
                                if (item is com.musicplayer.lytrix.innertube.models.ArtistItem) {
                                    val titleMatches = item.title.equals(cleanQuery, ignoreCase = true) ||
                                            item.title.replace(" ", "").equals(cleanQuery.replace(" ", "").replace("·", "").replace("•", ""), ignoreCase = true)
                                    if (titleMatches) {
                                        finalArtistId = item.id
                                        break
                                    }
                                }
                            }
                            if (finalArtistId != null) break
                        }
                    }
                }

                if (finalArtistId == null) {
                    val podSearch = com.musicplayer.lytrix.innertube.YouTube.search(
                            query = cleanQuery,
                            filter = com.musicplayer.lytrix.innertube.YouTube.SearchFilter.FILTER_PODCAST,
                            useAccountContext = false
                        ).getOrNull()
                        finalArtistId = podSearch?.items?.filterIsInstance<com.musicplayer.lytrix.innertube.models.PodcastItem>()?.firstNotNullOfOrNull { it.author?.id }
                    }

                    if (finalArtistId == null) {
                        val plSearch = com.musicplayer.lytrix.innertube.YouTube.search(
                            query = cleanQuery,
                            filter = com.musicplayer.lytrix.innertube.YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST,
                            useAccountContext = false
                        ).getOrNull()
                        finalArtistId = plSearch?.items?.filterIsInstance<com.musicplayer.lytrix.innertube.models.PlaylistItem>()?.firstNotNullOfOrNull { it.author?.id }
                    }

                    if (finalArtistId == null) {
                        val artistSearch = com.musicplayer.lytrix.innertube.YouTube.search(
                            query = cleanQuery,
                            filter = com.musicplayer.lytrix.innertube.YouTube.SearchFilter.FILTER_ARTIST,
                            useAccountContext = false
                        ).getOrNull()
                        finalArtistId = artistSearch?.items?.filterIsInstance<com.musicplayer.lytrix.innertube.models.ArtistItem>()?.firstOrNull()?.id
                    }

                    if (finalArtistId == null) {
                        val profileSearch = com.musicplayer.lytrix.innertube.YouTube.search(
                            query = cleanQuery,
                            filter = com.musicplayer.lytrix.innertube.YouTube.SearchFilter.FILTER_PROFILE,
                            useAccountContext = false
                        ).getOrNull()
                        finalArtistId = profileSearch?.items?.filterIsInstance<com.musicplayer.lytrix.innertube.models.ArtistItem>()?.firstOrNull()?.id
                    }

                    if (finalArtistId == null) {
                        val generalSearch = com.musicplayer.lytrix.innertube.YouTube.search(
                            query = cleanQuery,
                            filter = null,
                            useAccountContext = false
                        ).getOrNull()
                        finalArtistId = generalSearch?.items?.filterIsInstance<com.musicplayer.lytrix.innertube.models.ArtistItem>()?.firstOrNull()?.id
                            ?: generalSearch?.items?.filterIsInstance<com.musicplayer.lytrix.innertube.models.PlaylistItem>()?.firstNotNullOfOrNull { it.author?.id }
                            ?: generalSearch?.items?.filterIsInstance<com.musicplayer.lytrix.innertube.models.SongItem>()?.firstNotNullOfOrNull { it.artists?.firstOrNull()?.id }
                    }

                if (finalArtistId != null) {
                    val page = com.musicplayer.lytrix.innertube.YouTube.artist(finalArtistId).getOrNull()
                    if (page != null) _artistPage.value = page
                } else {
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Artist or Channel not found.", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isArtistLoading.value = false
            }
        }
    }

    fun clearArtistPage() { _artistPage.value = null }
    private val _artistItemsPage = kotlinx.coroutines.flow.MutableStateFlow<com.musicplayer.lytrix.innertube.pages.ArtistItemsPage?>(null)
    val artistItemsPage: kotlinx.coroutines.flow.StateFlow<com.musicplayer.lytrix.innertube.pages.ArtistItemsPage?> = _artistItemsPage.asStateFlow()

    fun loadArtistItems(browseId: String, params: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            _artistItemsPage.value = null
            try {
                val endpoint = com.musicplayer.lytrix.innertube.models.BrowseEndpoint(browseId = browseId, params = params)
                _artistItemsPage.value = com.musicplayer.lytrix.innertube.YouTube.artistItems(endpoint).getOrNull()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun clearArtistItems() { _artistItemsPage.value = null }

    fun toggleArtistSubscribe(context: Context, artistId: String, artistName: String, artistThumb: String?) {
        val prefs = context.getSharedPreferences("subscribed_artists_prefs", Context.MODE_PRIVATE)
        val oldSet = prefs.getStringSet("subscribed_ids", emptySet()) ?: emptySet()
        val newSet = java.util.HashSet<String>(oldSet)
        if (newSet.contains(artistId)) {
            newSet.remove(artistId)
            prefs.edit().remove("artist_data_$artistId").putStringSet("subscribed_ids", newSet).apply()
            Toast.makeText(context, "Unsubscribed from $artistName", Toast.LENGTH_SHORT).show()
        } else {
            newSet.add(artistId)
            val jsonObj = org.json.JSONObject()
            jsonObj.put("id", artistId); jsonObj.put("name", artistName); jsonObj.put("thumbnail", artistThumb ?: "")
            prefs.edit().putString("artist_data_$artistId", jsonObj.toString()).putStringSet("subscribed_ids", newSet).apply()
            Toast.makeText(context, "Subscribed to $artistName", Toast.LENGTH_SHORT).show()
        }
    }

    fun isArtistSubscribed(context: Context, artistId: String): Boolean {
        return context.getSharedPreferences("subscribed_artists_prefs", Context.MODE_PRIVATE).getStringSet("subscribed_ids", emptySet())?.contains(artistId) == true
    }

    fun togglePodcastChannelSubscribe(context: Context, channelId: String, channelName: String, channelThumb: String?) {
        val prefs = context.getSharedPreferences("subscribed_podcast_channels_prefs", Context.MODE_PRIVATE)
        val oldSet = prefs.getStringSet("subscribed_ids", emptySet()) ?: emptySet()
        val newSet = java.util.HashSet<String>(oldSet)
        if (newSet.contains(channelId)) {
            newSet.remove(channelId)
            prefs.edit().remove("channel_data_$channelId").putStringSet("subscribed_ids", newSet).apply()
            Toast.makeText(context, "Unsubscribed from channel $channelName", Toast.LENGTH_SHORT).show()
        } else {
            newSet.add(channelId)
            val jsonObj = org.json.JSONObject()
            jsonObj.put("id", channelId); jsonObj.put("name", channelName); jsonObj.put("thumbnail", channelThumb ?: "")
            prefs.edit().putString("channel_data_$channelId", jsonObj.toString()).putStringSet("subscribed_ids", newSet).apply()
            Toast.makeText(context, "Subscribed to channel $channelName", Toast.LENGTH_SHORT).show()
        }
    }

    fun isPodcastChannelSubscribed(context: Context, channelId: String): Boolean {
        return context.getSharedPreferences("subscribed_podcast_channels_prefs", Context.MODE_PRIVATE).getStringSet("subscribed_ids", emptySet())?.contains(channelId) == true
    }

    fun toggleAudiobookAuthorSubscribe(context: Context, authorId: String, authorName: String, authorThumb: String?) {
        val prefs = context.getSharedPreferences("subscribed_audiobook_authors_prefs", Context.MODE_PRIVATE)
        val oldSet = prefs.getStringSet("subscribed_ids", emptySet()) ?: emptySet()
        val newSet = java.util.HashSet<String>(oldSet)
        if (newSet.contains(authorId)) {
            newSet.remove(authorId)
            prefs.edit().remove("author_data_$authorId").putStringSet("subscribed_ids", newSet).apply()
            Toast.makeText(context, "Unsubscribed from $authorName", Toast.LENGTH_SHORT).show()
        } else {
            newSet.add(authorId)
            val jsonObj = org.json.JSONObject()
            jsonObj.put("id", authorId); jsonObj.put("name", authorName); jsonObj.put("thumbnail", authorThumb ?: "")
            prefs.edit().putString("author_data_$authorId", jsonObj.toString()).putStringSet("subscribed_ids", newSet).apply()
            Toast.makeText(context, "Subscribed to $authorName", Toast.LENGTH_SHORT).show()
        }
    }

    fun isAudiobookAuthorSubscribed(context: Context, authorId: String): Boolean {
        return context.getSharedPreferences("subscribed_audiobook_authors_prefs", Context.MODE_PRIVATE).getStringSet("subscribed_ids", emptySet())?.contains(authorId) == true
    }

    // 🔥 ভিডিও কোয়ালিটি স্টেট
    private val _videoQualityPref = kotlinx.coroutines.flow.MutableStateFlow("Auto")
    val videoQualityPref: kotlinx.coroutines.flow.StateFlow<String> = _videoQualityPref.asStateFlow()

    fun loadVideoQualityPref(context: android.content.Context) {
        val prefs = context.getSharedPreferences("app_preferences", android.content.Context.MODE_PRIVATE)
        _videoQualityPref.value = prefs.getString("video_quality", "Auto") ?: "Auto"
    }

    fun setVideoQualityPref(context: android.content.Context, quality: String) {
        _videoQualityPref.value = quality
        context.getSharedPreferences("app_preferences", android.content.Context.MODE_PRIVATE)
            .edit().putString("video_quality", quality).apply()
        _currentSong.value?.let { preWarmVideoIfEligible(context, it) } // 🔥 নতুন লাইন
    }

    // ==========================================
    // 🔥 MUSIC RECOGNITION (MELD STYLE SHAZAM ENGINE)

    // ==========================================
    private var shazamJob: kotlinx.coroutines.Job? = null
    private val _shazamState = kotlinx.coroutines.flow.MutableStateFlow<com.musicplayer.lytrix.shazam.RecognitionStatus>(com.musicplayer.lytrix.shazam.RecognitionStatus.Ready)
    val shazamState: kotlinx.coroutines.flow.StateFlow<com.musicplayer.lytrix.shazam.RecognitionStatus> = _shazamState.asStateFlow()
    private val _pendingRecognitionLaunch = kotlinx.coroutines.flow.MutableStateFlow(false)
    val pendingRecognitionLaunch: kotlinx.coroutines.flow.StateFlow<Boolean> = _pendingRecognitionLaunch.asStateFlow()

    fun requestRecognitionFromTile() {
        _pendingRecognitionLaunch.value = true
    }

    fun consumeRecognitionFromTile() {
        _pendingRecognitionLaunch.value = false
    }

    fun startShazamRecognition(context: Context) {
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            android.widget.Toast.makeText(context, "Microphone permission required!", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        shazamJob?.cancel() // আগের কোনো প্রসেস চললে ক্যানসেল করবে
        shazamJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _shazamState.value = com.musicplayer.lytrix.shazam.RecognitionStatus.Listening
            val sampleRate = 16000
            val channelConfig = android.media.AudioFormat.CHANNEL_IN_MONO
            val audioFormat = android.media.AudioFormat.ENCODING_PCM_16BIT
            val bufferSize = android.media.AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 2
            // 🔥 ম্যাজিক ফিক্স ১: UNPROCESSED বা VOICE_RECOGNITION ইউজ করা, যাতে নয়েজ ক্যান্সেলেশন মিউজিক নষ্ট না করে
            var audioSource = android.media.MediaRecorder.AudioSource.UNPROCESSED
            var audioRecord = android.media.AudioRecord(audioSource, sampleRate, channelConfig, audioFormat, bufferSize)
            if (audioRecord.state != android.media.AudioRecord.STATE_INITIALIZED) {
                audioRecord.release()
                audioSource = android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION
                audioRecord = android.media.AudioRecord(audioSource, sampleRate, channelConfig, audioFormat, bufferSize)
            }

            val generator = com.musicplayer.lytrix.shazam.ShazamSignatureGenerator()
            val audioBuffer = ShortArray(bufferSize)
            try {
                audioRecord.startRecording()
                val startTime = System.currentTimeMillis()
                var signature: com.musicplayer.lytrix.shazam.ShazamSignature? = null
                // 🔥 ম্যাজিক ফিক্স: ম্যাক্স ১৫ সেকেন্ড শুনবে, কিন্তু ৭ সেকেন্ডের আগে সিগনেচার চেক করবে না
                while (isActive && System.currentTimeMillis() - startTime < 15000) {
                    val read = audioRecord.read(audioBuffer, 0, audioBuffer.size)
                    if (read > 0) {
                        generator.feedPcm16Mono(audioBuffer.copyOfRange(0, read))
                        // ৭ সেকেন্ড পার হওয়ার পর সিগনেচার চেক করবে, যাতে স্ট্রং ডেটা যায়
                        if (System.currentTimeMillis() - startTime > 7000) {
                            signature = generator.nextSignatureOrNull()
                            if (signature != null) break
                        }
                    }
                }

                // লুপ শেষ হওয়ার পর যদি সিগনেচার না নিয়ে থাকে
                if (signature == null) {
                    signature = generator.nextSignatureOrNull()
                }

                audioRecord.stop()
                audioRecord.release()
                if (!isActive) return@launch // যদি ইউজার Cancel বাটনে ক্লিক করে
                _shazamState.value = com.musicplayer.lytrix.shazam.RecognitionStatus.Processing
                if (signature == null) {
                    _shazamState.value = com.musicplayer.lytrix.shazam.RecognitionStatus.NoMatch("Could not catch the audio clearly.")
                    return@launch
                }

                val result = com.musicplayer.lytrix.shazam.Shazam.recognize(signature.uri, signature.sampleDurationMs)
                if (!isActive) return@launch // আবার চেক, যদি ইউজার কেটে দেয়
                result.fold(
                    onSuccess = { recognized ->
                        _shazamState.value = com.musicplayer.lytrix.shazam.RecognitionStatus.Success(recognized)
                    },
                    onFailure = { error ->
                        _shazamState.value = com.musicplayer.lytrix.shazam.RecognitionStatus.NoMatch("No match found")
                    }

                )
            } catch (e: Exception) {
                e.printStackTrace()
                audioRecord.release()
                if (isActive) {
                    _shazamState.value = com.musicplayer.lytrix.shazam.RecognitionStatus.Error("Microphone Error!")
                }
            }
        }
    }

    // 🔥 Meld এর মতো প্রসেস ক্যানসেল/ক্লোজ করার ফাংশন

    fun cancelShazamRecognition() {
        shazamJob?.cancel()
        _shazamState.value = com.musicplayer.lytrix.shazam.RecognitionStatus.Ready
    }

// ==========================================
    // 🚀 LISTEN TOGETHER (METRO / RENDER SYNC ENGINE)
    // ==========================================
    val metroManager: MetroManager by lazy {
        val ctx = appContext ?: com.musicplayer.lytrix.LytrixApplication.instance
        MetroManager(ctx).also { setupMetroCallbacks(it) }
    }

    private fun createMetroTrackInfo(song: Song): TrackInfo {
        val isSaavn = song.filePath == "ONLINE" ||
                song.albumArtUriString?.contains("saavn", ignoreCase = true) == true ||
                song.uriString.contains("saavncdn.com", ignoreCase = true)

        val cleanId = (song.originalId ?: song.id.toString()).removePrefix("saavn:")
        val trackId = if (isSaavn) "saavn:$cleanId" else cleanId
        val directStream = if (song.uriString.isNotBlank()) song.uriString else (mediaController?.currentMediaItem?.localConfiguration?.uri?.toString() ?: "")
        val isDirectStream = !isSaavn && directStream.startsWith("http", ignoreCase = true) && !directStream.contains("youtube.com", ignoreCase = true) && !directStream.contains("youtu.be", ignoreCase = true)
        val isAb = song.mediaType == "AUDIOBOOK" || song.filePath?.contains("AUDIOBOOK", ignoreCase = true) == true || song.album?.contains("Audiobook", ignoreCase = true) == true
        val isYtPod = !isSaavn && !isDirectStream && (song.mediaType == "PODCAST" || song.filePath == "ONLINE_PODCAST" || song.album?.contains("Podcast", ignoreCase = true) == true || isYouTubePodcastTrack(song))
        val hostSpeed = currentPlaybackSpeed.value.takeIf { it in 0.25f..3.0f } ?: (mediaController?.playbackParameters?.speed ?: 1.0f)
        val spdTag = if (Math.abs(hostSpeed - 1.0f) > 0.05f) ":spd=$hostSpeed" else ""

        val trackAlbum = when {
            isSaavn && directStream.isNotBlank() -> "saavn_url:$directStream$spdTag"
            isDirectStream && isAb -> "direct_ab_url:$directStream$spdTag"
            isDirectStream -> "direct_url:$directStream$spdTag"
            isYtPod -> "yt_pod:$cleanId$spdTag:${song.album ?: "Podcast"}"
            spdTag.isNotEmpty() -> "${song.album ?: "Listen Together"}$spdTag"
            else -> (song.album ?: "")
        }

        return TrackInfo(
            id = trackId,
            title = song.title,
            artist = song.artist,
            album = trackAlbum,
            duration = song.duration,
            thumbnail = song.albumArtUriString ?: ""
        )
    }

    private fun createSongFromMetroTrack(info: TrackInfo): Song {
        var rawAlbum = info.album
        val speedMatch = Regex(":spd=([0-9.]+)").find(rawAlbum)
        if (speedMatch != null) {
            val spdVal = speedMatch.groupValues[1].toFloatOrNull() ?: 1.0f
            if (Math.abs(currentPlaybackSpeed.value - spdVal) > 0.05f) {
                setPlaybackSpeed(spdVal, immediate = true)
            }
            rawAlbum = rawAlbum.replace(speedMatch.value, "")
        }

        val isSaavn = info.id.startsWith("saavn:") || rawAlbum.startsWith("saavn_url:")
        val isDirectAb = rawAlbum.startsWith("direct_ab_url:")
        val isDirectPod = rawAlbum.startsWith("direct_url:")
        val isYtPod = rawAlbum.startsWith("yt_pod:") || rawAlbum.equals("Podcast", ignoreCase = true)

        val streamUri = when {
            isSaavn && rawAlbum.startsWith("saavn_url:") -> rawAlbum.removePrefix("saavn_url:")
            isDirectAb -> rawAlbum.removePrefix("direct_ab_url:")
            isDirectPod -> rawAlbum.removePrefix("direct_url:")
            isSaavn -> ""
            else -> "https://www.youtube.com/watch?v=${info.id}"
        }
        val filePath = when {
            isDirectAb -> "ONLINE_AUDIOBOOK"
            isDirectPod || isYtPod -> "ONLINE_PODCAST"
            isSaavn -> "ONLINE"
            else -> "ONLINE_LYTRIX"
        }
        val cleanId = if (isSaavn) info.id.removePrefix("saavn:") else info.id
        val targetCat = when {
            isDirectAb -> "AUDIOBOOK"
            isDirectPod || isYtPod -> "PODCAST"
            else -> "MUSIC"
        }
        if (targetCat in listOf("PODCAST", "AUDIOBOOK")) {
            _currentMediaCategory.value = targetCat
        }

        return Song(
            id = cleanId.hashCode().toLong(),
            title = info.title,
            artist = info.artist,
            album = when {
                isSaavn -> "JioSaavn"
                isDirectAb -> "Audiobook"
                isDirectPod || isYtPod -> "Podcast"
                else -> rawAlbum.ifEmpty { "Listen Together" }
            },
            duration = info.duration,
            uriString = streamUri,
            filePath = filePath,
            albumArtUriString = info.thumbnail,
            isVideo = false,
            hasLyrics = false,
            originalId = cleanId,
            mediaType = targetCat
        )
    }

    private fun setupMetroCallbacks(manager: MetroManager) {
        manager.onHostNeedsToAnnouncePlayback = {
            val controller = mediaController
            val song = _currentSong.value
            if (controller != null && song != null) {
                val trackInfo = createMetroTrackInfo(song)
                val currentQueue = _currentQueueList.value
                val upcomingQueue = if (currentQueue.isNotEmpty()) {
                    val currentIndex = currentQueue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                    currentQueue.drop(currentIndex + 1).take(15).map { createMetroTrackInfo(it) }
                } else emptyList()
                manager.publishSnapshot(
                    track = trackInfo,
                    isPlaying = controller.playWhenReady,
                    position = controller.currentPosition,
                    volume = controller.volume,
                    queue = upcomingQueue
                )
            }
        }

        manager.onRemotePlaybackAction = { action, trackId, position, trackInfo ->
            val controller = mediaController
            if (controller != null) {
                val effectiveTrack = trackInfo ?: manager.roomState.value?.currentTrack
                val currentlyPlayingId = (_currentSong.value?.originalId ?: _currentSong.value?.id.toString()).removePrefix("saavn:")
                val incomingTrackId = (effectiveTrack?.id ?: trackId).removePrefix("saavn:")

                val needsLoadTrack = (incomingTrackId.isNotEmpty() && currentlyPlayingId != incomingTrackId) || controller.currentMediaItem == null

                if (action == PlaybackActions.CHANGE_TRACK || needsLoadTrack) {
                    val info = effectiveTrack ?: TrackInfo(
                        id = incomingTrackId.ifBlank { trackId },
                        title = "Track $trackId",
                        artist = "Unknown",
                        duration = 0L
                    )
                    markRemoteApply(3000L)
                    pendingMetroSeekPosition = if (position > 0) position else null
                    val syncSong = createSongFromMetroTrack(info)
                    playSong(syncSong, listOf(syncSong), initialPositionMs = if (position > 0) position else null)
                    if (action == PlaybackActions.PAUSE) {
                        controller.pause()
                    } else {
                        controller.play()
                    }
                } else if (action == PlaybackActions.PLAY) {
                    val drift = Math.abs(controller.currentPosition - position)
                    markRemoteApply()
                    if (!controller.playWhenReady) {
                        if (drift > 200L) {
                            controller.seekTo(position)
                        }
                        controller.play()
                    } else if (drift > 1500L) {
                        controller.seekTo(position)
                    }
                } else if (action == PlaybackActions.PAUSE) {
                    val drift = Math.abs(controller.currentPosition - position)
                    markRemoteApply()
                    if (controller.playWhenReady) {
                        controller.pause()
                    }
                    if (drift > 500L) {
                        controller.seekTo(position)
                    }
                } else if (action == PlaybackActions.SEEK) {
                    val drift = Math.abs(controller.currentPosition - position)
                    if (drift > 500L) {
                        markRemoteApply()
                        controller.seekTo(position)
                    }
                }
            }
        }

        manager.onRemoteVolumeChange = { volume ->
            try {
                val ctx = appContext ?: com.musicplayer.lytrix.LytrixApplication.instance
                val audioManager = ctx.getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager
                if (audioManager != null) {
                    val max = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                    val target = (volume * max).toInt().coerceIn(0, max)
                    audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, target, 0)
                }
                mediaController?.volume = 1f
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        manager.onSuggestionApprovedPlay = { track ->
            val song = createSongFromMetroTrack(track)
            playSong(song, listOf(song))
        }
    }

    fun forceSyncToMetro(action: String? = null) {
        if (metroManager.roomRole.value == MetroRoomRole.HOST) {
            val controller = mediaController ?: return
            val song = _currentSong.value ?: return
            val trackInfo = createMetroTrackInfo(song)
            val currentQueue = _currentQueueList.value
            val upcomingQueue = if (currentQueue.isNotEmpty()) {
                val currentIndex = currentQueue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                currentQueue.drop(currentIndex + 1).take(15).map { createMetroTrackInfo(it) }
            } else emptyList()

            if (action == PlaybackActions.CHANGE_TRACK) {
                metroManager.sendTrackChange(trackInfo, controller.playWhenReady, controller.currentPosition, upcomingQueue)
            } else {
                val currentAction = action ?: if (controller.playWhenReady) PlaybackActions.PLAY else PlaybackActions.PAUSE
                // Deliberately empty trackId: server rejects play/pause/seek if trackId differs from held track ("stale_track");
                // sending empty trackId makes the server use its held current track, matching SimpMusic/Metrolist protocol.
                metroManager.sendPlaybackAction(
                    action = currentAction,
                    trackId = "",
                    position = controller.currentPosition,
                    trackInfo = null,
                    volume = controller.volume,
                    queue = upcomingQueue
                )
            }
        }
    }

// ==========================================
    // 🔥 LISTEN TOGETHER (FIREBASE SYNC ENGINE)

    // ==========================================
    private val _currentListenRoom = MutableStateFlow<com.musicplayer.lytrix.model.ListenRoom?>(null)
    val currentListenRoom: StateFlow<com.musicplayer.lytrix.model.ListenRoom?> = _currentListenRoom.asStateFlow()
    private val _myRoomStatus = MutableStateFlow<String>("disconnected")
    val myRoomStatus: StateFlow<String> = _myRoomStatus.asStateFlow()
    private val _isRoomLoading = MutableStateFlow(false)
    val isRoomLoading: StateFlow<Boolean> = _isRoomLoading.asStateFlow()
    private val _isNetworkConnected = MutableStateFlow(true)
    val isNetworkConnected: StateFlow<Boolean> = _isNetworkConnected.asStateFlow()
    private val _pendingRoomCode = MutableStateFlow("")
    val pendingRoomCode: StateFlow<String> = _pendingRoomCode.asStateFlow()
    private var myDeviceId = ""
    private var myUserName = ""
    private var roomDbRef: com.google.firebase.database.DatabaseReference? = null
    private var syncJob: Job? = null
    private var roomMetaListener: com.google.firebase.database.ValueEventListener? = null
    private var membersListener: com.google.firebase.database.ValueEventListener? = null
    private var presenceListener: com.google.firebase.database.ValueEventListener? = null
    private var playbackListener: com.google.firebase.database.ValueEventListener? = null
    private var messagesListener: com.google.firebase.database.ChildEventListener? = null
    private var messagesQuery: com.google.firebase.database.Query? = null
    private var firebaseSyncDebounceJob: Job? = null
    private var networkListener: com.google.firebase.database.ValueEventListener? = null
    private var suggestionsListener: com.google.firebase.database.ValueEventListener? = null
    private val _fbSuggestions = MutableStateFlow<List<com.musicplayer.lytrix.model.FirebaseSongSuggestion>>(emptyList())
    val fbSuggestions: StateFlow<List<com.musicplayer.lytrix.model.FirebaseSongSuggestion>> = _fbSuggestions.asStateFlow()
    private val _fbAutoApproveJoins = MutableStateFlow(false)
    val fbAutoApproveJoins: StateFlow<Boolean> = _fbAutoApproveJoins.asStateFlow()
    private val _fbAutoApproveSuggestions = MutableStateFlow(false)
    val fbAutoApproveSuggestions: StateFlow<Boolean> = _fbAutoApproveSuggestions.asStateFlow()

    fun setFbAutoApproveJoins(enabled: Boolean) {
        _fbAutoApproveJoins.value = enabled
        if (enabled && (_myRoomStatus.value == "host" || _myRoomStatus.value == "co_host")) {
            val currentMembers = _currentListenRoom.value?.members ?: return
            currentMembers.forEach { (key, member) ->
                if (member.status == "pending") {
                    roomDbRef?.child("members")?.child(key)?.child("status")?.setValue("approved")
                }
            }
        }
    }

    fun setFbAutoApproveSuggestions(enabled: Boolean) {
        _fbAutoApproveSuggestions.value = enabled
        if (enabled && (_myRoomStatus.value == "host" || _myRoomStatus.value == "co_host")) {
            _fbSuggestions.value.firstOrNull()?.let { autoSugg ->
                approveFirebaseSuggestion(autoSugg)
            }
        }
    }

    private var titleListener: com.google.firebase.database.ValueEventListener? = null
    private var vibeListener: com.google.firebase.database.ValueEventListener? = null
    private var pinnedNoticeListener: com.google.firebase.database.ValueEventListener? = null
    private var lockListener: com.google.firebase.database.ValueEventListener? = null
    private var pollListener: com.google.firebase.database.ValueEventListener? = null
    private var ratingsListener: com.google.firebase.database.ValueEventListener? = null

    private val _roomTitle = MutableStateFlow("")
    val roomTitle: StateFlow<String> = _roomTitle.asStateFlow()

    private val _roomVibe = MutableStateFlow("Chill 🎧")
    val roomVibe: StateFlow<String> = _roomVibe.asStateFlow()

    private val _pinnedNotice = MutableStateFlow("")
    val pinnedNotice: StateFlow<String> = _pinnedNotice.asStateFlow()

    private val _isRoomLocked = MutableStateFlow(false)
    val isRoomLocked: StateFlow<Boolean> = _isRoomLocked.asStateFlow()

    private val _activePoll = MutableStateFlow<com.musicplayer.lytrix.model.RoomPoll?>(null)
    val activePoll: StateFlow<com.musicplayer.lytrix.model.RoomPoll?> = _activePoll.asStateFlow()

    private val _currentRatings = MutableStateFlow<Map<String, Int>>(emptyMap())
    val currentRatings: StateFlow<Map<String, Int>> = _currentRatings.asStateFlow()

    fun setRoomTitle(title: String) {
        if (_myRoomStatus.value != "host" && _myRoomStatus.value != "co_host") return
        val clean = title.trim().take(25)
        _roomTitle.value = clean
        roomDbRef?.child("roomTitle")?.setValue(clean)
    }

    fun setRoomVibe(vibe: String) {
        if (_myRoomStatus.value != "host" && _myRoomStatus.value != "co_host") return
        _roomVibe.value = vibe
        roomDbRef?.child("roomVibe")?.setValue(vibe)
    }

    fun setPinnedNotice(notice: String) {
        if (_myRoomStatus.value != "host" && _myRoomStatus.value != "co_host") return
        val clean = notice.trim().take(120)
        _pinnedNotice.value = clean
        roomDbRef?.child("pinnedNotice")?.setValue(clean)
    }

    fun clearPinnedNotice() {
        if (_myRoomStatus.value != "host" && _myRoomStatus.value != "co_host") return
        _pinnedNotice.value = ""
        roomDbRef?.child("pinnedNotice")?.setValue("")
    }

    fun setRoomLocked(locked: Boolean) {
        if (_myRoomStatus.value != "host") return
        _isRoomLocked.value = locked
        roomDbRef?.child("isLocked")?.setValue(locked)
    }

    fun createPoll(question: String, options: List<String>) {
        val ref = roomDbRef ?: return
        val cleanQ = question.trim().take(60)
        val cleanOpts = options.map { it.trim().take(30) }.filter { it.isNotBlank() }
        if (cleanQ.isBlank() || cleanOpts.size < 2) return
        val pollId = ref.child("activePoll").push().key ?: System.currentTimeMillis().toString()
        val poll = com.musicplayer.lytrix.model.RoomPoll(
            pollId = pollId,
            creatorName = myUserName.ifEmpty { "Host" },
            question = cleanQ,
            options = cleanOpts,
            votes = hashMapOf(),
            active = true,
            createdAt = System.currentTimeMillis()
        )
        ref.child("activePoll").setValue(poll)
    }

    private var lastVotedPollId: String? = null

    fun votePoll(optionIndex: Int) {
        val ref = roomDbRef ?: return
        val status = _myRoomStatus.value
        if (status != "approved" && status != "host" && status != "co_host") return
        val devId = myDeviceId.ifEmpty { appContext?.let { getDeviceId(it) } ?: "" }
        if (devId.isEmpty()) return
        val poll = _activePoll.value ?: return
        if (!poll.active) return
        if (optionIndex !in poll.options.indices) return
        if (poll.votes.containsKey(devId)) return          // আগেই ভোট দিয়েছে
        if (lastVotedPollId == poll.pollId) return         // দ্রুত ডাবল-ট্যাপ আটকাতে
        lastVotedPollId = poll.pollId
        ref.child("activePoll").child("votes").child(devId).setValue(optionIndex)
    }

    fun endPoll() {
        if (_myRoomStatus.value != "host" && _myRoomStatus.value != "co_host") return
        roomDbRef?.child("activePoll")?.removeValue()
        _activePoll.value = null
    }

    fun rateCurrentSong(rating: Int) {
        val ref = roomDbRef ?: return
        val status = _myRoomStatus.value
        if (status != "approved" && status != "host" && status != "co_host") return   // পেন্ডিং পারবে না
        val devId = myDeviceId.ifEmpty { appContext?.let { getDeviceId(it) } ?: "" }
        if (devId.isEmpty()) return
        if (_currentRatings.value.containsKey(devId)) return                          // আগেই রেট করেছে
        val clamped = rating.coerceIn(1, 5)
        _currentRatings.value = _currentRatings.value + (devId to clamped)            // সাথে সাথে লক
        ref.child("currentRatings").child(devId).setValue(clamped)
    }

    private val dbUrl = "https://lytrix-player-default-rtdb.asia-southeast1.firebasedatabase.app"
    private var lastSyncedUpdatedAt = 0L
    private var lastBroadcastedSongId = ""
    // 🔥 নতুন: অন্যের কমান্ড বসানোর প্রতিধ্বনি (echo) আটকানোর জন্য
    private var ignoreLocalSyncUntil = 0L

    private fun markRemoteApply(durationMs: Long = 2000L) { ignoreLocalSyncUntil = System.currentTimeMillis() + durationMs }

    private fun isRemoteApplyActive(): Boolean = System.currentTimeMillis() < ignoreLocalSyncUntil

    fun shouldBlockGuestPlayback(): Boolean {
        if (isRemoteApplyActive()) return false
        // 🔥 Firebase Listen Together রুমে গেস্ট হলে প্লেব্যাক ও কন্ট্রোল লক
        if (_myRoomStatus.value == "approved" || _myRoomStatus.value == "pending") return true
        return metroManager.roomRole.value == com.musicplayer.lytrix.listentogether.metro.MetroRoomRole.GUEST
    }
    // 🔥 নতুন: ইনফিনিট লোডিং ফিক্সের জন্য
    private var roomAttemptId = 0
    private var roomTimeoutJob: Job? = null
    // 🔥 নতুন: অ্যাপ জোর করে বন্ধ হলে Firebase সার্ভার নিজেই "left the room" মেসেজ লিখবে
    private var leaveMsgRef: com.google.firebase.database.DatabaseReference? = null

    fun setPendingRoomCode(code: String) { _pendingRoomCode.value = code }

    fun clearPendingRoomCode() { _pendingRoomCode.value = "" }

    fun getDeviceId(context: Context): String {
        if (myDeviceId.isEmpty()) {
            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            myDeviceId = prefs.getString("device_id", "") ?: ""
            if (myDeviceId.isEmpty()) {
                myDeviceId = java.util.UUID.randomUUID().toString()
                prefs.edit().putString("device_id", myDeviceId).apply()
            }
        }

        return myDeviceId
    }

    fun getSavedUserName(context: Context): String {
        return context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).getString("lt_username", "") ?: ""
    }

    private fun saveUserName(context: Context, name: String) {
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit().putString("lt_username", name.trim()).apply()
    }

    private fun sendSystemMessage(text: String) {
        if (_myRoomStatus.value == "disconnected") return
        val pushId = roomDbRef?.child("messages")?.push()?.key ?: return
        val chatMessage = com.musicplayer.lytrix.model.ChatMessage(
            senderId = "system", senderName = "System", message = text,
            timestamp = System.currentTimeMillis(), role = "system"
        )
        roomDbRef?.child("messages")?.child(pushId)?.setValue(chatMessage)
    }

    // 🔥 নতুন: Firebase সার্ভারকে আগে থেকেই বলে রাখা — "আমার কানেকশন কেটে গেলে তুমি এই মেসেজটা লিখে দিও"

    private fun registerLeaveMessage() {
        val ref = roomDbRef ?: return
        leaveMsgRef?.onDisconnect()?.cancel()
        val msgRef = ref.child("messages").push()
        leaveMsgRef = msgRef
        msgRef.onDisconnect().setValue(
            mapOf(
                "senderId" to "system",
                "senderName" to "System",
                "message" to "🚶‍♂️ $myUserName left the room",
                "timestamp" to com.google.firebase.database.ServerValue.TIMESTAMP,
                "role" to "system"
            )
        )
    }

    // 🔥 নতুন: ওপরের নির্দেশ বাতিল করা (লিভ বাটন চাপলে, কিক হলে, বা হোস্ট হলে)

    private fun cancelLeaveMessage() {
        leaveMsgRef?.onDisconnect()?.cancel()
        leaveMsgRef = null
    }

    // ==========================================
    // 💓 PRESENCE HEARTBEAT (অ্যাপ জোর করে কেটে গেলে রুম/মেম্বার পরিষ্কার)
    // ==========================================
    private var heartbeatJob: Job? = null
    private var hostBeatListener: com.google.firebase.database.ValueEventListener? = null
    private var memberBeatsListener: com.google.firebase.database.ValueEventListener? = null
    private var lastHostBeatValue = 0L
    private var lastHostBeatSeenAt = 0L
    private var offlineMarkAt = 0L
    private val memberBeatValues = HashMap<String, Long>()
    private val memberBeatSeenAt = HashMap<String, Long>()
    private val memberFirstSeenAt = HashMap<String, Long>()
    private val heartbeatIntervalMs = 15_000L     // কত সেকেন্ড পর পর হার্টবিট
    private val hostDeadTimeoutMs = 5 * 60 * 1000L        // হোস্টের হার্টবিট ৫ মিনিট না বদলালে অটো-মাইগ্রেশন বা রুম ক্লোজ
    private val memberDeadTimeoutMs = 5 * 60 * 1000L      // মেম্বারের হার্টবিট ৫ মিনিট না বদলালে হোস্ট সরাবে
    private val reconnectGraceMs = 40_000L        // নেট ফিরলে এত সময় কাউকে বাদ দেওয়া হবে না

    // ফায়ারবেস সার্ভার টাইম অফসেট হেল্পার
    private fun withServerOffset(action: (Long) -> Unit) {
        com.google.firebase.database.FirebaseDatabase.getInstance(dbUrl)
            .getReference(".info/serverTimeOffset")
            .addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    action(snapshot.getValue(Long::class.java) ?: 0L)
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                    action(0L)
                }
            })
    }

    private fun startPresence(context: Context, ref: com.google.firebase.database.DatabaseReference, deviceId: String) {
        stopPresence()
        val appCtx = context.applicationContext
        val startAt = android.os.SystemClock.elapsedRealtime()
        lastHostBeatValue = 0L
        lastHostBeatSeenAt = startAt
        offlineMarkAt = startAt

        // সবাই হোস্টের হার্টবিট দেখে
        hostBeatListener = object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val value = snapshot.getValue(Long::class.java) ?: return
                if (value != lastHostBeatValue) {
                    lastHostBeatValue = value
                    lastHostBeatSeenAt = android.os.SystemClock.elapsedRealtime()
                }
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        }
        ref.child("hostHeartbeat").addValueEventListener(hostBeatListener!!)

        heartbeatJob = viewModelScope.launch {
            var lastLoopAt = android.os.SystemClock.elapsedRealtime()
            while (isActive) {
                val now = android.os.SystemClock.elapsedRealtime()
                val status = _myRoomStatus.value
                if (status == "disconnected") break

                // ফোন ঘুমিয়ে ছিল বা অ্যাপ ফ্রিজ ছিল: ভুল করে কাউকে বাদ না দিয়ে একটু সময় দেওয়া
                if (now - lastLoopAt > heartbeatIntervalMs * 3) offlineMarkAt = now
                lastLoopAt = now

                if (!_isNetworkConnected.value) {
                    offlineMarkAt = now
                } else {
                    val graceOver = now - offlineMarkAt > reconnectGraceMs
                    when (status) {
                        "host" -> {
                            ref.child("hostHeartbeat").setValue(com.google.firebase.database.ServerValue.TIMESTAMP)
                            attachMemberBeatsListener(ref)
                            if (graceOver) removeDeadMembers(ref, deviceId, now)
                        }
                        "approved", "co_host" -> {
                            detachMemberBeatsListener(ref)
                            if (_currentListenRoom.value?.members?.containsKey(deviceId) == true) {
                                ref.child("heartbeats").child(deviceId)
                                    .setValue(com.google.firebase.database.ServerValue.TIMESTAMP)
                            }
                        }
                        else -> detachMemberBeatsListener(ref)
                    }

                    // হোস্ট ছাড়া বাকিরা: হোস্টের হার্টবিট থেমে গেলে রুম ছেড়ে দাও
                    if (status != "host" && graceOver && lastHostBeatValue != 0L &&
                        now - lastHostBeatSeenAt > hostDeadTimeoutMs
                    ) {
                        handleHostPresenceLost(appCtx)
                        break
                    }
                }
                delay(heartbeatIntervalMs)
            }
        }
    }

    private fun stopPresence() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        hostBeatListener?.let { roomDbRef?.child("hostHeartbeat")?.removeEventListener(it) }
        hostBeatListener = null
        detachMemberBeatsListener(roomDbRef)
        memberBeatValues.clear()
        memberBeatSeenAt.clear()
        memberFirstSeenAt.clear()
    }

    // শুধু হোস্ট মেম্বারদের হার্টবিট দেখে
    private fun attachMemberBeatsListener(ref: com.google.firebase.database.DatabaseReference) {
        if (memberBeatsListener != null) return
        val listener = object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val now = android.os.SystemClock.elapsedRealtime()
                val present = HashSet<String>()
                for (child in snapshot.children) {
                    val id = child.key ?: continue
                    val value = child.getValue(Long::class.java) ?: continue
                    present.add(id)
                    if (memberBeatValues[id] != value) {
                        memberBeatValues[id] = value
                        memberBeatSeenAt[id] = now
                    }
                }
                memberBeatValues.keys.retainAll(present)
                memberBeatSeenAt.keys.retainAll(present)
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        }
        memberBeatsListener = listener
        ref.child("heartbeats").addValueEventListener(listener)
    }

    private fun detachMemberBeatsListener(ref: com.google.firebase.database.DatabaseReference?) {
        memberBeatsListener?.let { ref?.child("heartbeats")?.removeEventListener(it) }
        memberBeatsListener = null
    }

    private fun removeDeadMembers(ref: com.google.firebase.database.DatabaseReference, deviceId: String, now: Long) {
        val members = _currentListenRoom.value?.members ?: return
        val activeIds = HashSet<String>()
        for ((id, member) in members) {
            if (id == deviceId || member.status == "pending") continue
            activeIds.add(id)
            val first = memberFirstSeenAt.getOrPut(id) { now }
            val seen = memberBeatSeenAt[id] ?: continue      // হার্টবিট কখনো আসেনি (পুরনো অ্যাপ ভার্সন) → বাদ দেওয়া হবে না
            val last = if (seen > first) seen else first
            if (now - last > memberDeadTimeoutMs) {
                memberBeatSeenAt.remove(id)
                memberBeatValues.remove(id)
                memberFirstSeenAt.remove(id)
                ref.child("members").child(id).removeValue()
                ref.child("coHosts").child(id).removeValue()
                ref.child("heartbeats").child(id).removeValue()
            }
        }
        memberFirstSeenAt.keys.retainAll(activeIds)
    }

    private fun handleHostPresenceLost(context: Context) {
        val rRef = roomDbRef ?: return
        val code = _currentListenRoom.value?.roomCode
        val myId = myDeviceId.ifEmpty { getDeviceId(context) }
        val formerHostId = _currentListenRoom.value?.hostId ?: ""

        withServerOffset { offset ->
            rRef.child("hostHeartbeat").get().addOnSuccessListener { beatSnap ->
                val lastBeat = beatSnap.getValue(Long::class.java)
                val serverNow = System.currentTimeMillis() + offset
                if (lastBeat == null || serverNow - lastBeat <= hostDeadTimeoutMs) {
                    return@addOnSuccessListener
                }

                // 🔍 হোস্ট ৫ মিনিট অনুপস্থিত! মেম্বার চেক করে অটো-হোস্ট মাইগ্রেশন
                rRef.child("members").get().addOnSuccessListener { memSnap ->
                    data class OnlineCandidate(val id: String, val name: String, val isCoHost: Boolean)
                    val onlineCandidates = mutableListOf<OnlineCandidate>()

                    for (child in memSnap.children) {
                        val mId = child.key ?: continue
                        if (mId == formerHostId) continue
                        val status = child.child("status").getValue(String::class.java) ?: continue
                        val isOnline = child.child("online").getValue(Boolean::class.java) ?: false
                        val name = child.child("name").getValue(String::class.java) ?: "Member"

                        if (isOnline && (status == "co_host" || status == "approved")) {
                            onlineCandidates.add(OnlineCandidate(mId, name, status == "co_host"))
                        }
                    }

                    if (onlineCandidates.isNotEmpty()) {
                        // কো-হোস্ট অগ্রাধিকার পাবে, টাইব্রেকারে id দিয়ে নিরপেক্ষ নির্বাচন
                        val bestCandidate = onlineCandidates.sortedWith(
                            compareByDescending<OnlineCandidate> { it.isCoHost }.thenBy { it.id }
                        ).first()

                        if (myId == bestCandidate.id) {
                            // 👑 আমি নতুন হোস্ট হিসেবে দায়িত্ব গ্রহণ করলাম
                            val updates = hashMapOf<String, Any>(
                                "hostId" to myId,
                                "members/$myId/status" to "host",
                                "members/$myId/online" to true,
                                "members/$formerHostId/status" to "approved",
                                "hostHeartbeat" to com.google.firebase.database.ServerValue.TIMESTAMP
                            )
                            rRef.updateChildren(updates).addOnSuccessListener {
                                _myRoomStatus.value = "host"
                                context.getSharedPreferences("listen_together_session", Context.MODE_PRIVATE).edit()
                                    .putBoolean("is_host", true).apply()
                                startHostSyncing()
                                forceSyncToFirebase(isAutoPeriodic = false)
                                sendSystemMessage("👑 Host was away for 5m. $myUserName is now the Host!")
                                Toast.makeText(context, "You are now the Room Host 👑", Toast.LENGTH_LONG).show()
                                startPresence(context, rRef, myId)
                            }
                        } else {
                            // অন্য মেম্বার হোস্ট হচ্ছে, আমি রুমেই থাকছি
                            Toast.makeText(context, "Host was away for 5m. Transferring room to ${bestCandidate.name}...", Toast.LENGTH_SHORT).show()
                            lastHostBeatSeenAt = android.os.SystemClock.elapsedRealtime()
                            startPresence(context, rRef, myId)
                        }
                    } else {
                        // ❌ রুমে আর কেউ অনলাইন নেই (সবাই Away / পরিত্যক্ত রুম) → রুম ডিলিট (Auto Closed)
                        rRef.removeValue()
                        code?.let {
                            com.google.firebase.database.FirebaseDatabase.getInstance(dbUrl)
                                .getReference("ListenTogetherVoices").child(it).removeValue()
                        }
                        leaveRoom(context)
                        Toast.makeText(context, "Host and all members were away for 5+ minutes. Room closed.", Toast.LENGTH_LONG).show()
                    }
                }.addOnFailureListener {
                    leaveRoom(context)
                }
            }.addOnFailureListener {
                leaveRoom(context)
            }
        }
    }

    private fun monitorNetworkConnection() {
        // পুরোনো লিসেনার থাকলে আগে রিমুভ করো
        networkListener?.let { com.google.firebase.database.FirebaseDatabase.getInstance(dbUrl).getReference(".info/connected").removeEventListener(it) }
        val connectedRef = com.google.firebase.database.FirebaseDatabase.getInstance(dbUrl).getReference(".info/connected")
        networkListener = object : com.google.firebase.database.ValueEventListener {

            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                _isNetworkConnected.value = snapshot.getValue(Boolean::class.java) ?: false
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        }

        connectedRef.addValueEventListener(networkListener!!)
    }

    // 🔥 নতুন: ১২ সেকেন্ডে কিছু না হলে লোডিং বন্ধ করবে এবং ঝুলে থাকা লেখা বাতিল করবে

    private fun startRoomTimeout(context: Context, attemptId: Int, onTimeout: () -> Unit) {
        roomTimeoutJob?.cancel()
        roomTimeoutJob = viewModelScope.launch {
            delay(12_000)
            if (attemptId == roomAttemptId && _isRoomLoading.value) {
                roomAttemptId++ // পুরোনো কলব্যাকগুলো অকেজো হয়ে যাবে
                com.google.firebase.database.FirebaseDatabase.getInstance(dbUrl).purgeOutstandingWrites()
                onTimeout()
                _isRoomLoading.value = false
                Toast.makeText(context, "Connection is slow or offline. Please try again.", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun createListenRoom(context: Context, userName: String) {
        if (_isRoomLoading.value) return
        if (!checkNetworkStatus(context)) {
            Toast.makeText(context, "No internet connection!", Toast.LENGTH_SHORT).show()
            return
        }

        _isRoomLoading.value = true
        val attemptId = ++roomAttemptId
        saveUserName(context, userName)
        val fbInstance = com.google.firebase.database.FirebaseDatabase.getInstance(dbUrl)
        fbInstance.purgeOutstandingWrites()
        fbInstance.goOffline()
        fbInstance.goOnline()
        val deviceId = getDeviceId(context)
        myUserName = userName
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val roomCode = (1..8).map { chars.random() }.joinToString("")
        val dbRef = fbInstance.getReference("ListenTogetherRooms").child(roomCode)
        // 🔒 ১০ মিনিটের সেশন: ডিসকানেক্টে রুম ডিলিট হবে না
        startRoomTimeout(context, attemptId) {
            dbRef.removeValue() // অর্ধেক তৈরি হয়ে থাকলে পরিষ্কার করে দেবে
        }

        val initialRoom = com.musicplayer.lytrix.model.ListenRoom(
            hostId = deviceId, roomCode = roomCode,
            members = hashMapOf(deviceId to com.musicplayer.lytrix.model.RoomMember(name = userName, status = "host", online = true))
        )
        dbRef.setValue(initialRoom).addOnCompleteListener { task ->
            if (attemptId != roomAttemptId) return@addOnCompleteListener
            roomTimeoutJob?.cancel()
            if (task.isSuccessful) {
                context.getSharedPreferences("listen_together_session", Context.MODE_PRIVATE).edit()
                    .putString("current_room_code", roomCode)
                    .putBoolean("is_host", true)
                    .putString("device_id", deviceId)
                    .apply()
                roomDbRef = dbRef
                _myRoomStatus.value = "host"
                listenToRoomUpdates(context, roomCode)
                startHostSyncing()
                forceSyncToFirebase(isAutoPeriodic = false)
                monitorNetworkConnection()
                sendSystemMessage("👑 $userName created the room")
                _isRoomLoading.value = false
            } else {
                dbRef.onDisconnect().cancel()
                _isRoomLoading.value = false
                Toast.makeText(context, "Failed to create room: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun requestToJoinRoom(context: Context, roomCode: String, userName: String) {
        if (_isRoomLoading.value) return
        if (!checkNetworkStatus(context)) {
            Toast.makeText(context, "No internet connection!", Toast.LENGTH_SHORT).show()
            return
        }

        _isRoomLoading.value = true
        val attemptId = ++roomAttemptId
        saveUserName(context, userName)
        val fbInstance = com.google.firebase.database.FirebaseDatabase.getInstance(dbUrl)
        fbInstance.purgeOutstandingWrites()
        fbInstance.goOffline()
        fbInstance.goOnline()
        val deviceId = getDeviceId(context)
        myUserName = userName
        val dbRef = fbInstance.getReference("ListenTogetherRooms").child(roomCode.uppercase())
        startRoomTimeout(context, attemptId) {
            dbRef.child("members").child(deviceId).removeValue()
        }

        dbRef.child("hostId").get().addOnCompleteListener { task ->
            if (attemptId != roomAttemptId) return@addOnCompleteListener
            if (!task.isSuccessful) {
                roomTimeoutJob?.cancel()
                _isRoomLoading.value = false
                Toast.makeText(context, "Connection problem. Please try again.", Toast.LENGTH_SHORT).show()
                return@addOnCompleteListener
            }

            if (!task.result.exists()) {
                roomTimeoutJob?.cancel()
                _isRoomLoading.value = false
                Toast.makeText(context, "Room not found or closed!", Toast.LENGTH_SHORT).show()
                return@addOnCompleteListener
            }

            // 🔒 isLocked আলাদা করে পড়তে হবে (আগে hostId-এর ভেতরে খোঁজা হচ্ছিল, তাই সবসময় false আসত)
            dbRef.child("isLocked").get().addOnCompleteListener { lockTask ->
                if (attemptId != roomAttemptId) return@addOnCompleteListener
                if (!lockTask.isSuccessful) {
                    roomTimeoutJob?.cancel()
                    _isRoomLoading.value = false
                    Toast.makeText(context, "Connection problem. Please try again.", Toast.LENGTH_SHORT).show()
                    return@addOnCompleteListener
                }

                val isLocked = lockTask.result.getValue(Boolean::class.java) ?: false
                if (isLocked) {
                    roomTimeoutJob?.cancel()
                    _isRoomLoading.value = false
                    Toast.makeText(context, "This room is locked by the host!", Toast.LENGTH_SHORT).show()
                    return@addOnCompleteListener
                }

                val memberData = com.musicplayer.lytrix.model.RoomMember(name = userName, status = "pending")
                dbRef.child("members").child(deviceId).setValue(memberData).addOnCompleteListener { writeTask ->
                    if (attemptId != roomAttemptId) return@addOnCompleteListener
                    roomTimeoutJob?.cancel()
                    if (writeTask.isSuccessful) {
                        context.getSharedPreferences("listen_together_session", Context.MODE_PRIVATE).edit()
                            .putString("current_room_code", roomCode.uppercase())
                            .putBoolean("is_host", false)
                            .putString("device_id", deviceId)
                            .apply()
                        dbRef.child("members").child(deviceId).onDisconnect().removeValue()
                        roomDbRef = dbRef
                        _myRoomStatus.value = "pending"
                        listenToRoomUpdates(context, roomCode.uppercase())
                        monitorNetworkConnection()
                        _isRoomLoading.value = false
                    } else {
                        _isRoomLoading.value = false
                        Toast.makeText(context, "Failed to send request", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    fun reconnectToRoom(context: Context) {
        val code = _currentListenRoom.value?.roomCode ?: return
        requestToJoinRoom(context, code, myUserName)
    }

    // 📱 লাইফসাইকেল ভিত্তিক প্রেজেন্স মেথড (অন-স্ক্রিনে থাকলে Online 🟢, ব্যাকগ্রাউন্ডে গেলে Away 💤)
    fun setRoomPresence(isOnline: Boolean) {
        val rRef = roomDbRef ?: return
        if (_myRoomStatus.value == "disconnected" || _myRoomStatus.value == "pending") return
        val deviceId = myDeviceId.ifEmpty { appContext?.let { getDeviceId(it) } ?: return }
        val memberRef = rRef.child("members").child(deviceId)
        if (isOnline) {
            memberRef.child("online").onDisconnect().setValue(false)
            memberRef.child("online").setValue(true)
        } else {
            memberRef.child("online").setValue(false)
        }
    }

    // 🔄 VIP Lounge (Firebase) ১০ মিনিটের সেশন অটো-রিস্টোর
    fun restoreFirebaseSessionIfValid(context: Context, onRestored: (String) -> Unit) {
        // ডাবল-কল গার্ড: অলরেডি রুমে কানেক্টেড থাকলে বা লোডিং চললে আবার রিস্টোর চালাবে না
        if (_myRoomStatus.value != "disconnected" || _isRoomLoading.value) return

        val appCtx = context.applicationContext
        val sessionPrefs = appCtx.getSharedPreferences("listen_together_session", Context.MODE_PRIVATE)
        val savedCode = sessionPrefs.getString("current_room_code", null) ?: return
        val wasHost = sessionPrefs.getBoolean("is_host", false)
        val deviceId = sessionPrefs.getString("device_id", null) ?: getDeviceId(appCtx)

        val fbInstance = com.google.firebase.database.FirebaseDatabase.getInstance(dbUrl)
        val rRef = fbInstance.getReference("ListenTogetherRooms").child(savedCode.uppercase())

        withServerOffset { offset ->
            rRef.get().addOnSuccessListener { snapshot ->
                if (!snapshot.exists()) {
                    sessionPrefs.edit().clear().apply()
                    return@addOnSuccessListener
                }

                val serverNow = System.currentTimeMillis() + offset
                val lastBeat = snapshot.child("hostHeartbeat").getValue(Long::class.java)
                val targetRole: String

                if (wasHost) {
                    val realHostId = snapshot.child("hostId").getValue(String::class.java)
                    if (realHostId != deviceId) {
                        // হোস্ট অনুপস্থিত থাকায় রুম অন্য কারো কাছে অটো-ট্রান্সফার হয়েছে!
                        val memberSnap = snapshot.child("members").child(deviceId)
                        if (memberSnap.exists()) {
                            val status = memberSnap.child("status").getValue(String::class.java)
                            if (status == "approved" || status == "co_host") {
                                targetRole = status
                                sessionPrefs.edit().putBoolean("is_host", false).apply()
                            } else {
                                sessionPrefs.edit().clear().apply()
                                return@addOnSuccessListener
                            }
                        } else {
                            sessionPrefs.edit().clear().apply()
                            return@addOnSuccessListener
                        }
                    } else {
                        if (lastBeat != null && serverNow - lastBeat > hostDeadTimeoutMs) {
                            var anyOnline = false
                            for (child in snapshot.child("members").children) {
                                if (child.key != deviceId && child.child("online").getValue(Boolean::class.java) == true) {
                                    anyOnline = true
                                    break
                                }
                            }
                            if (!anyOnline) {
                                rRef.removeValue()
                                fbInstance.getReference("ListenTogetherVoices").child(savedCode.uppercase()).removeValue()
                                sessionPrefs.edit().clear().apply()
                                return@addOnSuccessListener
                            }
                        }
                        targetRole = "host"
                    }
                } else {
                    val memberSnap = snapshot.child("members").child(deviceId)
                    if (!memberSnap.exists()) {
                        sessionPrefs.edit().clear().apply()
                        return@addOnSuccessListener
                    }
                    val status = memberSnap.child("status").getValue(String::class.java)
                    if (status != "approved" && status != "co_host" && status != "host") {
                        sessionPrefs.edit().clear().apply()
                        return@addOnSuccessListener
                    }
                    if (status == "host") {
                        sessionPrefs.edit().putBoolean("is_host", true).apply()
                    }
                    targetRole = status
                }

                val oldPlaybackUpdatedAt = snapshot.child("playbackState")
                    .child("updatedAt").getValue(Long::class.java) ?: 0L

                viewModelScope.launch {
                    isServiceConnected.first { it }
                    if (_myRoomStatus.value != "disconnected") return@launch

                    myUserName = getSavedUserName(appCtx)
                    roomDbRef = rRef
                    if (targetRole == "host") {
                        lastSyncedUpdatedAt = oldPlaybackUpdatedAt
                        _myRoomStatus.value = "host"
                    } else {
                        // গেস্ট ও কো-হোস্ট প্রথমে গেস্ট হিসেবে ঢুকবে এবং রুমের বর্তমান গানে সিঙ্ক হবে
                        _myRoomStatus.value = "approved"
                    }
                    listenToRoomUpdates(appCtx, savedCode)
                    if (targetRole == "host") {
                        startHostSyncing()
                        rRef.child("hostHeartbeat").setValue(com.google.firebase.database.ServerValue.TIMESTAMP)
                        forceSyncToFirebase(isAutoPeriodic = false)
                    }
                    monitorNetworkConnection()
                    onRestored(savedCode)
                }
            }.addOnFailureListener {
                // নেট না থাকলে সেশন মুছবে না
            }
        }
    }

    private fun isNameChar(c: Char): Boolean =
        c.isLetterOrDigit() ||
                c == '_' ||
                c.category == CharCategory.NON_SPACING_MARK ||
                c.category == CharCategory.COMBINING_SPACING_MARK
    // 🔥 মেনশন চেক: "@নাম" এর পরে নামের অংশ থাকলে সেটা অন্য নাম।
    // একাধিক নাম মিলে গেলে (Rafi / Rafi Ahmed) সবচেয়ে লম্বা নামটাকেই মেনশন ধরা হবে।

    private fun isMentioned(message: String, myName: String, allNames: Collection<String>): Boolean {
        if (myName.isBlank()) return false
        var searchFrom = 0
        while (true) {
            val at = message.indexOf('@', searchFrom)
            if (at == -1) return false
            searchFrom = at + 1
            // ইমেইলের মতো "abc@rafi" ধরবে না
            if (at > 0 && isNameChar(message[at - 1])) continue
            var best: String? = null
            for (name in allNames) {
                if (name.isBlank()) continue
                val end = at + 1 + name.length
                if (end > message.length) continue
                if (!message.regionMatches(at + 1, name, 0, name.length, ignoreCase = true)) continue
                if (end < message.length && isNameChar(message[end])) continue
                if (best == null || name.length > best.length) best = name
            }

            if (best != null && best.equals(myName, ignoreCase = true)) return true
        }
    }

    private fun detachRoomListeners() {
        stopPresence()
        presenceListener?.let {
            try {
                com.google.firebase.database.FirebaseDatabase.getInstance(dbUrl)
                    .getReference(".info/connected").removeEventListener(it)
            } catch (_: Exception) {}
        }
        presenceListener = null
        roomMetaListener?.let { roomDbRef?.child("hostId")?.removeEventListener(it) }
        roomMetaListener = null
        membersListener?.let { roomDbRef?.child("members")?.removeEventListener(it) }
        membersListener = null
        playbackListener?.let { roomDbRef?.child("playbackState")?.removeEventListener(it) }
        playbackListener = null
        val mq = messagesQuery
        val ml = messagesListener
        if (mq != null && ml != null) {
            mq.removeEventListener(ml)
        }
        messagesListener = null
        messagesQuery = null
        suggestionsListener?.let { roomDbRef?.child("suggestions")?.removeEventListener(it) }
        suggestionsListener = null
        _fbSuggestions.value = emptyList()
        titleListener?.let { roomDbRef?.child("roomTitle")?.removeEventListener(it) }
        titleListener = null
        vibeListener?.let { roomDbRef?.child("roomVibe")?.removeEventListener(it) }
        vibeListener = null
        pinnedNoticeListener?.let { roomDbRef?.child("pinnedNotice")?.removeEventListener(it) }
        pinnedNoticeListener = null
        lockListener?.let { roomDbRef?.child("isLocked")?.removeEventListener(it) }
        lockListener = null
        pollListener?.let { roomDbRef?.child("activePoll")?.removeEventListener(it) }
        pollListener = null
        ratingsListener?.let { roomDbRef?.child("currentRatings")?.removeEventListener(it) }
        ratingsListener = null
        _roomTitle.value = ""
        _roomVibe.value = "Chill 🎧"
        _pinnedNotice.value = ""
        _isRoomLocked.value = false
        _activePoll.value = null
        _currentRatings.value = emptyMap()
        firebaseSyncDebounceJob?.cancel()
        firebaseSyncDebounceJob = null
    }

    private fun listenToRoomUpdates(context: Context, roomCode: String) {
        val deviceId = getDeviceId(context)
        // পুরোনো লিসেনার থাকলে আগে সরাও
        detachRoomListeners()
        val ref = com.google.firebase.database.FirebaseDatabase.getInstance(dbUrl).getReference("ListenTogetherRooms").child(roomCode)
        roomDbRef = ref

        // ১. রুম অস্তিত্ব / হোস্ট ট্র্যাকিং (রুম বন্ধ হলে তৎক্ষণাৎ শনাক্ত)
        roomMetaListener = object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                try {
                    if (!snapshot.exists()) {
                        if (_myRoomStatus.value != "disconnected") {
                            leaveRoom(context, skipRemoteCleanup = true)
                            Toast.makeText(context, "Room closed by host", Toast.LENGTH_SHORT).show()
                        }
                        return
                    }
                    val hostId = snapshot.getValue(String::class.java) ?: ""
                    val cur = _currentListenRoom.value ?: com.musicplayer.lytrix.model.ListenRoom(roomCode = roomCode)
                    _currentListenRoom.value = cur.copy(hostId = hostId, roomCode = roomCode)
                } catch (e: Throwable) { e.printStackTrace() }
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        }
        ref.child("hostId").addValueEventListener(roomMetaListener!!)

        // 🟢 প্রেজেন্স লিসেনার: অনলাইনে থাকলে true, ডিসকানেক্ট হলে server-side এ online = false (Away 💤)
        val connectedRef = com.google.firebase.database.FirebaseDatabase.getInstance(dbUrl).getReference(".info/connected")
        presenceListener = object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                if (connected && roomDbRef != null && _myRoomStatus.value != "disconnected" && _myRoomStatus.value != "pending") {
                    val memberRef = roomDbRef?.child("members")?.child(deviceId)
                    memberRef?.child("online")?.onDisconnect()?.setValue(false)
                    memberRef?.child("online")?.setValue(true)
                }
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        }
        connectedRef.addValueEventListener(presenceListener!!)

        // ২. মেম্বার লিসেনার (শুধু মেম্বারদের জয়েন/লিভ/অনুমোদনে কল হবে, গান চলা বা মেসেজে নয়)
        membersListener = object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                try {
                    if (!snapshot.exists()) return
                    val membersMap = HashMap<String, com.musicplayer.lytrix.model.RoomMember>()
                    for (child in snapshot.children) {
                        val member = child.getValue(com.musicplayer.lytrix.model.RoomMember::class.java)
                        val key = child.key
                        if (member != null && key != null) {
                            membersMap[key] = member
                            // 🔥 অটো-অ্যাপ্রুভ অন থাকলে হোস্ট স্বয়ংক্রিয়ভাবে পেন্ডিং মেম্বারকে অ্যাপ্রুভ করবে
                            if ((_myRoomStatus.value == "host" || _myRoomStatus.value == "co_host") && _fbAutoApproveJoins.value && member.status == "pending") {
                                ref.child("members").child(key).child("status").setValue("approved")
                            }
                        }
                    }
                    val myMemberData = membersMap[deviceId]
                    if (myMemberData != null) {
                        if (_myRoomStatus.value != myMemberData.status) {
                            val previousStatus = _myRoomStatus.value
                            val newStatus = myMemberData.status
                            _myRoomStatus.value = newStatus
                            val wasController = previousStatus == "host" || previousStatus == "co_host"
                            val isController = newStatus == "host" || newStatus == "co_host"
                            // 🔥 হোস্ট বা কো-হোস্ট হলে সিঙ্ক চালু, গেস্ট হলে বন্ধ
                            if (isController && !wasController) {
                                startHostSyncing()
                            } else if (wasController && !isController) {
                                syncJob?.cancel()
                                playerListener?.let { mediaController?.removeListener(it) }
                                playerListener = null
                            }

                            // 🔥 নতুন হোস্ট হলে: ডিসকানেক্টে রুম মোছার নির্দেশ বাদ ও প্রেজেন্স স্টার্ট
                            if (newStatus == "host" && previousStatus != "host") {
                                context.getSharedPreferences("listen_together_session", Context.MODE_PRIVATE).edit()
                                    .putBoolean("is_host", true).apply()
                                cancelLeaveMessage()
                                roomDbRef?.child("members")?.child(deviceId)?.onDisconnect()?.cancel()
                                roomDbRef?.let { startPresence(context, it, deviceId) }
                            }

                            // 🔥 হোস্ট থেকে গেস্ট হলে: রুম মোছার নির্দেশ বাতিল
                            if (previousStatus == "host" && newStatus != "host") {
                                context.getSharedPreferences("listen_together_session", Context.MODE_PRIVATE).edit()
                                    .putBoolean("is_host", false).apply()
                                roomDbRef?.onDisconnect()?.cancel()
                                voicesRef()?.onDisconnect()?.cancel()
                            }

                            // 🔥 জয়েন অ্যাপ্রুভ হলে: পেন্ডিং ডিসকানেক্ট বাতিল, প্রেজেন্স সেট এবং হোস্টের গানে সিঙ্ক
                            if (previousStatus == "pending" && (newStatus == "approved" || newStatus == "co_host")) {
                                roomDbRef?.child("members")?.child(deviceId)?.onDisconnect()?.cancel()
                                val memberRef = roomDbRef?.child("members")?.child(deviceId)
                                memberRef?.child("online")?.onDisconnect()?.setValue(false)
                                memberRef?.child("online")?.setValue(true)
                                Toast.makeText(context, "Joined successfully!", Toast.LENGTH_SHORT).show()
                                sendSystemMessage("👋 $myUserName joined the room")
                                _currentListenRoom.value?.playbackState?.let { currentPlayback ->
                                    if (currentPlayback.songId.isNotEmpty()) {
                                        syncPlayerWithHost(context, currentPlayback)
                                    }
                                }
                            }
                        }
                    } else if (_myRoomStatus.value != "disconnected" && _myRoomStatus.value != "pending") {
                        leaveRoom(context, skipRemoteCleanup = true)
                        Toast.makeText(context, "You were removed from the room", Toast.LENGTH_SHORT).show()
                        return
                    }

                    val cur = _currentListenRoom.value ?: com.musicplayer.lytrix.model.ListenRoom(roomCode = roomCode)
                    _currentListenRoom.value = cur.copy(members = membersMap, roomCode = roomCode)
                } catch (e: Throwable) { e.printStackTrace() }
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        }
        ref.child("members").addValueEventListener(membersListener!!)

        // ৩. প্লেব্যাক স্টেট লিসেনার (প্লে/পজ/সিক/গান বদলানোর সময় শুধু এই নোডে ডাউনলোড হবে)
        playbackListener = object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                try {
                    val playbackState = snapshot.getValue(com.musicplayer.lytrix.model.SyncState::class.java) ?: return
                    val cur = _currentListenRoom.value ?: com.musicplayer.lytrix.model.ListenRoom(roomCode = roomCode)
                    _currentListenRoom.value = cur.copy(playbackState = playbackState, roomCode = roomCode)

                    val status = _myRoomStatus.value
                    if (status == "approved" || status == "co_host" || status == "host") {
                        syncPlayerWithHost(context, playbackState)
                    }
                } catch (e: Throwable) { e.printStackTrace() }
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        }
        ref.child("playbackState").addValueEventListener(playbackListener!!)

        // ৪. মেসেজ লিসেনার (চাইল্ড লিসেনার + limitToLast(50) — ব্যান্ডউইথ সাশ্রয়ী)
        val mQuery = ref.child("messages").limitToLast(50)
        messagesQuery = mQuery
        var initialMessagesLoaded = false

        messagesListener = object : com.google.firebase.database.ChildEventListener {
            override fun onChildAdded(snapshot: com.google.firebase.database.DataSnapshot, previousChildName: String?) {
                try {
                    val msg = snapshot.getValue(com.musicplayer.lytrix.model.ChatMessage::class.java) ?: return
                    val msgKey = snapshot.key ?: return
                    val cur = _currentListenRoom.value ?: com.musicplayer.lytrix.model.ListenRoom(roomCode = roomCode)
                    val updatedMessages = HashMap(cur.messages)
                    updatedMessages[msgKey] = msg
                    _currentListenRoom.value = cur.copy(messages = updatedMessages, roomCode = roomCode)

                    // শুধুমাত্র ইনিশিয়াল হিস্টোরি লোডের পরের নতুন লাইভ মেসেজে মেনশন নোটিফিকেশন দেবে
                    if (initialMessagesLoaded && msg.senderId != deviceId) {
                        val currentUserName = myUserName.ifEmpty { getSavedUserName(context) }
                        val memberNames = cur.members.values.map { it.name } + currentUserName
                        if (isMentioned(msg.message, currentUserName, memberNames)) {
                            Toast.makeText(context, "💬 ${msg.senderName} mentioned you!", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Throwable) { e.printStackTrace() }
            }

            override fun onChildChanged(snapshot: com.google.firebase.database.DataSnapshot, previousChildName: String?) {
                try {
                    val msg = snapshot.getValue(com.musicplayer.lytrix.model.ChatMessage::class.java) ?: return
                    val msgKey = snapshot.key ?: return
                    val cur = _currentListenRoom.value ?: return
                    val updatedMessages = HashMap(cur.messages)
                    updatedMessages[msgKey] = msg
                    _currentListenRoom.value = cur.copy(messages = updatedMessages)
                } catch (e: Throwable) { e.printStackTrace() }
            }

            override fun onChildRemoved(snapshot: com.google.firebase.database.DataSnapshot) {
                try {
                    val msgKey = snapshot.key ?: return
                    val cur = _currentListenRoom.value ?: return
                    val updatedMessages = HashMap(cur.messages)
                    updatedMessages.remove(msgKey)
                    _currentListenRoom.value = cur.copy(messages = updatedMessages)
                } catch (e: Throwable) { e.printStackTrace() }
            }

            override fun onChildMoved(snapshot: com.google.firebase.database.DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                Toast.makeText(context, "Database error: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        }

        mQuery.addChildEventListener(messagesListener!!)
        mQuery.addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                initialMessagesLoaded = true
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                initialMessagesLoaded = true
            }
        })

        // ৫. সং সাজেশন লিসেনার (মেম্বারদের গানের রিকোয়েস্ট)
        suggestionsListener = object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                try {
                    val list = mutableListOf<com.musicplayer.lytrix.model.FirebaseSongSuggestion>()
                    for (child in snapshot.children) {
                        val sugg = child.getValue(com.musicplayer.lytrix.model.FirebaseSongSuggestion::class.java)
                        if (sugg != null) {
                            list.add(sugg)
                        }
                    }
                    _fbSuggestions.value = list.sortedBy { it.timestamp }

                    // 🔥 অটো-অ্যাপ্রুভ অন থাকলে হোস্ট সাথে সাথে গান প্লে করবে
                    if ((_myRoomStatus.value == "host" || _myRoomStatus.value == "co_host") && _fbAutoApproveSuggestions.value) {
                        list.firstOrNull()?.let { autoSugg ->
                            approveFirebaseSuggestion(autoSugg)
                        }
                    }
                } catch (e: Throwable) { e.printStackTrace() }
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        }
        ref.child("suggestions").addValueEventListener(suggestionsListener!!)

        // ৬. রুম টাইটেল লিসেনার
        titleListener = object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                _roomTitle.value = snapshot.getValue(String::class.java) ?: ""
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        }
        ref.child("roomTitle").addValueEventListener(titleListener!!)

        // ৭. রুম ভাইব লিসেনার
        vibeListener = object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                _roomVibe.value = snapshot.getValue(String::class.java) ?: "Chill 🎧"
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        }
        ref.child("roomVibe").addValueEventListener(vibeListener!!)

        // ৮. পিন করা নোটিশ লিসেনার
        pinnedNoticeListener = object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                _pinnedNotice.value = snapshot.getValue(String::class.java) ?: ""
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        }
        ref.child("pinnedNotice").addValueEventListener(pinnedNoticeListener!!)

        // ৯. লক লিসেনার
        lockListener = object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                _isRoomLocked.value = snapshot.getValue(Boolean::class.java) ?: false
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        }
        ref.child("isLocked").addValueEventListener(lockListener!!)

        // ১০. লাইভ পোল লিসেনার
        pollListener = object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                _activePoll.value = snapshot.getValue(com.musicplayer.lytrix.model.RoomPoll::class.java)
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        }
        ref.child("activePoll").addValueEventListener(pollListener!!)

        // ১১. স্টার রেটিং লিসেনার
        ratingsListener = object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val map = mutableMapOf<String, Int>()
                for (child in snapshot.children) {
                    val r = child.getValue(Int::class.java)
                    if (r != null && child.key != null) {
                        map[child.key!!] = r
                    }
                }
                _currentRatings.value = map
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        }
        ref.child("currentRatings").addValueEventListener(ratingsListener!!)
        startPresence(context, ref, deviceId)
    }

    private var pendingMetroSeekPosition: Long? = null
    private var playerListener: androidx.media3.common.Player.Listener? = null

    private fun startHostSyncing() {
        syncJob?.cancel()
        val controller = mediaController ?: return
        playerListener?.let { controller.removeListener(it) }
        playerListener = object : androidx.media3.common.Player.Listener {

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                // 🔥 অন্যের কমান্ড বসানোর প্রতিধ্বনি হলে ফায়ারবেসে ফেরত লিখবে না
                if (isRemoteApplyActive()) return
                if (_myRoomStatus.value == "host" || _myRoomStatus.value == "co_host") {
                    val dbState = _currentListenRoom.value?.playbackState
                    // শুধু বাফারিংয়ের কারণে isPlaying বদলালে কিছু লিখবে না
                    if (dbState != null && dbState.isPlaying == controller.playWhenReady) return
                    forceSyncToFirebase(isAutoPeriodic = false)
                }
            }

            override fun onPositionDiscontinuity(oldPosition: androidx.media3.common.Player.PositionInfo, newPosition: androidx.media3.common.Player.PositionInfo, reason: Int) {
                if (isRemoteApplyActive()) return
                if ((_myRoomStatus.value == "host" || _myRoomStatus.value == "co_host") &&
                    reason == androidx.media3.common.Player.DISCONTINUITY_REASON_SEEK) {
                    forceSyncToFirebase(isAutoPeriodic = false)
                }
            }

            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                if (_myRoomStatus.value == "host" || _myRoomStatus.value == "co_host") {
                    val currentId = mediaItem?.mediaId ?: ""
                    if (currentId != lastBroadcastedSongId && currentId.isNotEmpty()) {
                        lastBroadcastedSongId = currentId
                        sendSystemMessage("🎵 Song changed to: ${_currentSong.value?.title}")
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val status = _myRoomStatus.value
                if ((status == "host" || status == "co_host") && playbackState == androidx.media3.common.Player.STATE_READY) {
                    val dbState = _currentListenRoom.value?.playbackState
                    val currentlyPlayingId = _currentSong.value?.originalId ?: _currentSong.value?.id.toString()
                    // Echo Blocker: অন্য কেউ গান চেঞ্জ করলে ফায়ারবেসে ওভাররাইট করবে না
                    if (dbState != null && dbState.songId == currentlyPlayingId) return
                    forceSyncToFirebase(isAutoPeriodic = false)
                } else if (status == "approved" && playbackState == androidx.media3.common.Player.STATE_READY) {
                    val roomState = _currentListenRoom.value?.playbackState
                    if (roomState != null) {
                        val timeDiff = System.currentTimeMillis() - roomState.updatedAt
                        val expectedPosition = roomState.currentPosition + if (roomState.isPlaying) timeDiff else 0L
                        if (Math.abs(controller.currentPosition - expectedPosition) > 2500) {
                            controller.seekTo(expectedPosition)
                        }

                        if (roomState.isPlaying && !controller.playWhenReady) controller.play()
                        else if (!roomState.isPlaying && controller.playWhenReady) controller.pause()
                    }
                }
            }
        }

        controller.addListener(playerListener!!)
    }

    fun forceSyncToFirebase(isAutoPeriodic: Boolean = false) {
        if (_myRoomStatus.value != "host" && _myRoomStatus.value != "co_host") return
        firebaseSyncDebounceJob?.cancel()
        firebaseSyncDebounceJob = viewModelScope.launch(Dispatchers.Main) {
            // ১৫০ms debounce যাতে দ্রুত পর পর ঘটা কলব্যাক একাধিক বার নেটওয়ার্কে না লেখে
            if (!isAutoPeriodic) {
                delay(150)
            }
            val controller = mediaController ?: return@launch
            val song = _currentSong.value ?: return@launch
            val newSongId = song.originalId ?: song.id.toString()
            if (lastBroadcastedSongId != newSongId) {
                lastBroadcastedSongId = newSongId
                roomDbRef?.child("currentRatings")?.removeValue()
            }
            val timestamp = System.currentTimeMillis()
            val syncTimestamp = if (isAutoPeriodic) lastSyncedUpdatedAt else timestamp
            val directStream = if (song.uriString.isNotBlank()) song.uriString else (controller.currentMediaItem?.localConfiguration?.uri?.toString() ?: "")
            val activeParams = controller.playbackParameters
            val hostSpeed = currentPlaybackSpeed.value.takeIf { it in 0.25f..3.0f } ?: activeParams.speed
            val currentCategory = _currentMediaCategory.value.ifBlank {
                if (song.filePath?.contains("PODCAST", ignoreCase = true) == true) "PODCAST"
                else if (song.filePath?.contains("AUDIOBOOK", ignoreCase = true) == true) "AUDIOBOOK"
                else "MUSIC"
            }
            val syncData = com.musicplayer.lytrix.model.SyncState(
                songId = song.originalId ?: song.id.toString(), title = song.title, artist = song.artist,
                // 🔥 isPlaying নয়, playWhenReady: বাফারিংয়ের সময়ও "চলছে" ধরা হবে
                imageUrl = song.albumArtUriString ?: "", streamUrl = directStream, isPlaying = controller.playWhenReady,
                currentPosition = controller.currentPosition, updatedAt = syncTimestamp,
                playbackSpeed = hostSpeed,
                mediaCategory = currentCategory
            )
            if (!isAutoPeriodic) lastSyncedUpdatedAt = syncTimestamp
            roomDbRef?.child("playbackState")?.setValue(syncData)
        }
    }

    private fun syncPlayerWithHost(context: Context, hostState: com.musicplayer.lytrix.model.SyncState) {
        val controller = mediaController ?: return
        if (hostState.songId.isEmpty() || hostState.updatedAt <= lastSyncedUpdatedAt) return
        lastSyncedUpdatedAt = hostState.updatedAt

        // 🔥 প্লেব্যাক স্পিড সিঙ্ক (পডকাস্ট বা অডিওবুকের ক্ষেত্রে নিখুঁত টাইম সিঙ্ক বজায় রাখে)
        val hostSpeed = hostState.playbackSpeed.takeIf { it in 0.25f..3.0f } ?: 1.0f
        if (Math.abs(currentPlaybackSpeed.value - hostSpeed) > 0.05f) {
            setPlaybackSpeed(hostSpeed, immediate = true)
        }

        val currentlyPlayingId = _currentSong.value?.originalId ?: _currentSong.value?.id.toString()
        if (currentlyPlayingId != hostState.songId) {
            _currentRatings.value = emptyMap()
            // 🔥 গান বদল অন্যের কাছ থেকে এসেছে: প্রতিধ্বনি বন্ধ, আর "Song changed" মেসেজ আমি লিখব না
            markRemoteApply()
            lastBroadcastedSongId = hostState.songId.hashCode().toLong().toString()
            val timeDiff = System.currentTimeMillis() - hostState.updatedAt
            val expectedPosition = hostState.currentPosition + if (hostState.isPlaying) timeDiff else 0L
            val isJioSaavn = hostState.imageUrl.contains("saavn", ignoreCase = true) || hostState.streamUrl.contains("saavn", ignoreCase = true)
            val isYouTubeUrl = hostState.streamUrl.contains("youtube.com", ignoreCase = true) || hostState.streamUrl.contains("youtu.be", ignoreCase = true)
            val isDirectHttpUrl = hostState.streamUrl.startsWith("http", ignoreCase = true) && !isYouTubeUrl
            val isShowCategory = hostState.mediaCategory.equals("PODCAST", ignoreCase = true) || hostState.mediaCategory.equals("AUDIOBOOK", ignoreCase = true)
            if (isShowCategory) {
                _currentMediaCategory.value = hostState.mediaCategory.uppercase()
            } else if (!isDirectHttpUrl) {
                _currentMediaCategory.value = "MUSIC"
            }
            val songToSync = if (isJioSaavn && hostState.streamUrl.isNotEmpty()) {
                Song(
                    id = hostState.songId.hashCode().toLong(), title = hostState.title, artist = hostState.artist,
                    album = "Listen Together", duration = 0L, uriString = hostState.streamUrl, filePath = "ONLINE",
                    albumArtUriString = hostState.imageUrl, isVideo = false, hasLyrics = false, originalId = hostState.songId
                )
            } else if (isDirectHttpUrl) {
                val isAb = hostState.title.contains("audiobook", ignoreCase = true) ||
                           hostState.artist.contains("audiobook", ignoreCase = true) ||
                           hostState.streamUrl.contains("librivox", ignoreCase = true) ||
                           hostState.mediaCategory.equals("AUDIOBOOK", ignoreCase = true)
                val targetCategory = if (isAb) "AUDIOBOOK" else "PODCAST"
                _currentMediaCategory.value = targetCategory
                Song(
                    id = hostState.songId.hashCode().toLong(), title = hostState.title, artist = hostState.artist,
                    album = if (isAb) "Audiobook" else "Podcast", duration = 0L, uriString = hostState.streamUrl,
                    filePath = if (isAb) "ONLINE_AUDIOBOOK" else "ONLINE_PODCAST",
                    albumArtUriString = hostState.imageUrl, isVideo = false, hasLyrics = false, originalId = hostState.songId,
                    mediaType = targetCategory
                )
            } else {
                val realId = hostState.songId
                val isPod = hostState.mediaCategory.equals("PODCAST", ignoreCase = true)
                Song(
                    id = realId.hashCode().toLong(), title = hostState.title, artist = hostState.artist,
                    album = if (isPod) "Podcast" else "Listen Together", duration = 0L,
                    uriString = "https://www.youtube.com/watch?v=$realId",
                    filePath = if (isPod) "ONLINE_PODCAST" else "ONLINE_LYTRIX", albumArtUriString = hostState.imageUrl,
                    isVideo = false, hasLyrics = false, originalId = realId,
                    mediaType = if (isPod) "PODCAST" else "MUSIC"
                )
            }
            playSong(songToSync, listOf(songToSync), initialPositionMs = expectedPosition)
            if (!hostState.isPlaying) {
                controller.pause()
            }
            return
        }

        // একই গান বাজলে নরমাল সিঙ্ক
        val timeDiff = System.currentTimeMillis() - hostState.updatedAt
        val expectedPosition = hostState.currentPosition + if (hostState.isPlaying) timeDiff else 0L
        // পজ অবস্থায় পজিশন হুবহু মেলানো হবে (৫০০ms), চলার সময় ২.৫ সেকেন্ডের ফারাক সহ্য করা হবে
        val needSeek = Math.abs(controller.currentPosition - expectedPosition) > (if (hostState.isPlaying) 2500 else 500)
        val needPlay = hostState.isPlaying && !controller.playWhenReady
        val needPause = !hostState.isPlaying && controller.playWhenReady
        if (needSeek || needPlay || needPause) markRemoteApply()
        if (needSeek) controller.seekTo(expectedPosition)
        if (needPlay) controller.play()
        else if (needPause) controller.pause()
    }

    fun sendChatMessage(context: Context, text: String, replyTo: com.musicplayer.lytrix.model.ChatMessage? = null) {
        if (text.isBlank() || roomDbRef == null) return
        if (_myRoomStatus.value == "pending" || _myRoomStatus.value == "disconnected") {
            Toast.makeText(context, "You cannot chat yet!", Toast.LENGTH_SHORT).show()
            return
        }

        val deviceId = getDeviceId(context)
        val pushId = roomDbRef?.child("messages")?.push()?.key ?: return
        val chatMessage = com.musicplayer.lytrix.model.ChatMessage(
            senderId = deviceId, senderName = myUserName, message = text.trim(),
            timestamp = System.currentTimeMillis(), role = _myRoomStatus.value,
            replyToId = replyTo?.let { "${it.senderId}_${it.timestamp}" } ?: "",
            replyToName = replyTo?.senderName ?: "",
            replyToText = replyTo?.let { replyPreviewText(it) } ?: ""
        )
        roomDbRef?.child("messages")?.child(pushId)?.setValue(chatMessage)?.addOnSuccessListener {
            pruneOldMessages()
        }
    }

    fun approveMember(memberId: String) {
        if (_myRoomStatus.value == "host" || _myRoomStatus.value == "co_host") {
            roomDbRef?.child("members")?.child(memberId)?.child("status")?.setValue("approved")
        }
    }

    fun suggestTrackToFirebase(
        title: String,
        artist: String,
        imageUrl: String,
        streamUrl: String,
        songId: String
    ) {
        val ref = roomDbRef ?: return
        val myName = myUserName.ifEmpty { appContext?.let { getSavedUserName(it) } ?: "Guest" }
        val suggestionId = ref.child("suggestions").push().key ?: System.currentTimeMillis().toString()
        val suggestion = com.musicplayer.lytrix.model.FirebaseSongSuggestion(
            suggestionId = suggestionId,
            senderId = myDeviceId,
            senderName = myName,
            songId = songId,
            title = title,
            artist = artist,
            imageUrl = imageUrl,
            streamUrl = streamUrl,
            timestamp = System.currentTimeMillis()
        )
        ref.child("suggestions").child(suggestionId).setValue(suggestion)
    }

    fun approveFirebaseSuggestion(suggestion: com.musicplayer.lytrix.model.FirebaseSongSuggestion) {
        if (_myRoomStatus.value != "host" && _myRoomStatus.value != "co_host") return
        roomDbRef?.child("suggestions")?.child(suggestion.suggestionId)?.removeValue()

        val isJioSaavn = suggestion.imageUrl.contains("saavn", ignoreCase = true) || suggestion.streamUrl.contains("saavn", ignoreCase = true)
        val isYouTubeUrl = suggestion.streamUrl.contains("youtube.com", ignoreCase = true) || suggestion.streamUrl.contains("youtu.be", ignoreCase = true)
        val isDirectHttpUrl = suggestion.streamUrl.startsWith("http", ignoreCase = true) && !isYouTubeUrl

        val songToPlay = when {
            isJioSaavn && suggestion.streamUrl.isNotEmpty() -> Song(
                id = suggestion.songId.hashCode().toLong(), title = suggestion.title, artist = suggestion.artist,
                album = "Listen Together", duration = 0L, uriString = suggestion.streamUrl, filePath = "ONLINE",
                albumArtUriString = suggestion.imageUrl, isVideo = false, hasLyrics = false, originalId = suggestion.songId
            )
            isDirectHttpUrl -> Song(
                id = suggestion.songId.hashCode().toLong(), title = suggestion.title, artist = suggestion.artist,
                album = "Listen Together", duration = 0L, uriString = suggestion.streamUrl, filePath = "ONLINE",
                albumArtUriString = suggestion.imageUrl, isVideo = false, hasLyrics = false, originalId = suggestion.songId
            )
            else -> {
                val realId = suggestion.songId.removePrefix("saavn:")
                Song(
                    id = realId.hashCode().toLong(), title = suggestion.title, artist = suggestion.artist,
                    album = "Listen Together", duration = 0L,
                    uriString = "https://www.youtube.com/watch?v=$realId",
                    filePath = "ONLINE_LYTRIX", albumArtUriString = suggestion.imageUrl,
                    isVideo = false, hasLyrics = false, originalId = realId
                )
            }
        }

        playSong(songToPlay, listOf(songToPlay))
        forceSyncToFirebase(isAutoPeriodic = false)
        sendSystemMessage("💡 Playing suggested song: ${suggestion.title} (from ${suggestion.senderName})")
    }

    fun rejectFirebaseSuggestion(suggestionId: String) {
        if (_myRoomStatus.value != "host" && _myRoomStatus.value != "co_host") return
        roomDbRef?.child("suggestions")?.child(suggestionId)?.removeValue()
    }

    fun suggestTrackToActiveRoom(
        id: String,
        title: String,
        artist: String,
        album: String,
        thumbnail: String,
        streamUrl: String = "",
        context: Context
    ) {
        if (metroManager.roomRole.value == com.musicplayer.lytrix.listentogether.metro.MetroRoomRole.GUEST) {
            val track = com.musicplayer.lytrix.listentogether.metro.TrackInfo(
                id = id,
                title = title,
                artist = artist,
                album = album,
                duration = 0L,
                thumbnail = thumbnail
            )
            metroManager.suggestTrack(track)
            Toast.makeText(context, "Track suggested to Metro Host!", Toast.LENGTH_SHORT).show()
        } else if (_myRoomStatus.value == "approved" || _myRoomStatus.value == "co_host") {
            suggestTrackToFirebase(
                title = title,
                artist = artist,
                imageUrl = thumbnail,
                streamUrl = streamUrl,
                songId = id
            )
            Toast.makeText(context, "Track suggested to VIP Host!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "You are not a guest in any room!", Toast.LENGTH_SHORT).show()
        }
    }

    // 🔥 নতুন kickMember: যে কিক করছে তার ফোনই সাথে সাথে "X kicked Y" মেসেজ দেবে

    fun kickMember(memberId: String) {
        val myStatus = _myRoomStatus.value
        if (myStatus != "host" && myStatus != "co_host") return
        val ref = roomDbRef ?: return
        val target = _currentListenRoom.value?.members?.get(memberId)
        val kickerName = myUserName.ifEmpty { appContext?.let { getSavedUserName(it) } ?: "" }
        // Pending রিকোয়েস্ট রিজেক্ট করলে কিক মেসেজ দেবে না
        if (target != null && target.status != "pending") {
            sendSystemMessage("🚫 $kickerName kicked ${target.name}")
        }

        ref.child("members").child(memberId).removeValue()
        ref.child("coHosts").child(memberId).removeValue()
    }

    fun makeCoHost(memberId: String) {
        if (_myRoomStatus.value == "host") {
            roomDbRef?.child("members")?.child(memberId)?.child("status")?.setValue("co_host")
            roomDbRef?.child("coHosts")?.child(memberId)?.setValue(true)
        }
    }

    fun demoteToGuest(memberId: String) {
        if (_myRoomStatus.value == "host") {
            roomDbRef?.child("members")?.child(memberId)?.child("status")?.setValue("approved")
            roomDbRef?.child("coHosts")?.child(memberId)?.removeValue()
        }
    }

    fun transferHost(newHostId: String) {
        if (_myRoomStatus.value == "host") {
            val appCtx = appContext ?: return
            val myId = myDeviceId.ifEmpty { getDeviceId(appCtx) }
            val targetName = _currentListenRoom.value?.members?.get(newHostId)?.name ?: "Member"
            val updates = hashMapOf<String, Any>(
                "hostId" to newHostId,
                "members/$newHostId/status" to "host",
                "members/$myId/status" to "approved"
            )
            roomDbRef?.updateChildren(updates)?.addOnSuccessListener {
                _myRoomStatus.value = "approved"
                appCtx.getSharedPreferences("listen_together_session", Context.MODE_PRIVATE).edit()
                    .putBoolean("is_host", false).apply()
                syncJob?.cancel()
                playerListener?.let { mediaController?.removeListener(it) }
                playerListener = null
                roomDbRef?.let { startPresence(appCtx, it, myId) }
                sendSystemMessage("👑 Host transferred to $targetName")
                Toast.makeText(appCtx, "Host transferred to $targetName", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun leaveRoom(context: Context, skipRemoteCleanup: Boolean = false) {
        val deviceId = getDeviceId(context)
        cancelLeaveMessage()
        syncJob?.cancel()
        // 🎤 ভয়েস রেকর্ডিং/প্লেব্যাক বন্ধ ও ক্যাশ পরিষ্কার
        cancelVoiceRecording()
        stopVoicePlayback()
        clearVoiceCache(context)
        // আগে লিসেনারগুলো সরাও, যাতে নিজের লেখার কারণে কোনো কলব্যাক না আসে
        detachRoomListeners()
        playerListener?.let { mediaController?.removeListener(it) }
        playerListener = null
        networkListener?.let {
            com.google.firebase.database.FirebaseDatabase.getInstance(dbUrl)
                .getReference(".info/connected").removeEventListener(it)
        }

        val ref = roomDbRef
        val status = _myRoomStatus.value
        // রুম আগেই ডিলিট হয়ে গেলে (বা কিক হলে) Firebase-এ কিছুই লিখবে না
        if (ref != null && !skipRemoteCleanup) {
            if (status == "host") {
                ref.onDisconnect().cancel()
                voicesRef()?.onDisconnect()?.cancel()
                voicesRef()?.removeValue()   // 🎤 ভয়েসগুলোও মুছবে
                ref.removeValue()
                Toast.makeText(context, "Room closed", Toast.LENGTH_SHORT).show()
            } else {
                if (status == "approved" || status == "co_host") {
                    sendSystemMessage("🚶‍♂️ $myUserName left the room")
                }

                ref.child("members").child(deviceId).onDisconnect().cancel()
                ref.child("members").child(deviceId).removeValue().addOnCompleteListener {
                    ref.child("members").get().addOnSuccessListener { memSnap ->
                        var hasOnline = false
                        for (c in memSnap.children) {
                            if (c.child("online").getValue(Boolean::class.java) == true) {
                                hasOnline = true
                                break
                            }
                        }
                        if (!hasOnline) {
                            ref.removeValue()
                            voicesRef()?.removeValue()
                        }
                    }
                }
            }
        }

        context.getSharedPreferences("listen_together_session", Context.MODE_PRIVATE).edit().clear().apply()
        roomDbRef = null
        _currentListenRoom.value = null
        _myRoomStatus.value = "disconnected"
        clearPendingRoomCode()
        lastBroadcastedSongId = ""
        lastSyncedUpdatedAt = 0L
        _fbAutoApproveJoins.value = false
        _fbAutoApproveSuggestions.value = false
    }

    // ==========================================
    // 🎤 VOICE MESSAGE (Listen Together)

    // ==========================================
    private val _voiceState = MutableStateFlow("idle")   // idle / recording / ready / sending
    val voiceState: StateFlow<String> = _voiceState.asStateFlow()
    private val _voiceSeconds = MutableStateFlow(0)
    val voiceSeconds: StateFlow<Int> = _voiceSeconds.asStateFlow()
    private val _playingVoiceId = MutableStateFlow<String?>(null)
    val playingVoiceId: StateFlow<String?> = _playingVoiceId.asStateFlow()
    private val _loadingVoiceId = MutableStateFlow<String?>(null)
    val loadingVoiceId: StateFlow<String?> = _loadingVoiceId.asStateFlow()
    private var maxVoiceSeconds = 15                // 💎 ফ্রি ১৫ সেকেন্ড, প্রিমিয়াম ৩০ (রেকর্ডিং শুরুতে ঠিক হয়)
    private var voiceIsPremium = false
    private val recordingMusicVolume = 0.1f         // 🎵 রেকর্ডিংয়ের সময় গানের ভলিউম (১০%)
    private val _voiceMaxSeconds = MutableStateFlow(15)
    val voiceMaxSeconds: StateFlow<Int> = _voiceMaxSeconds.asStateFlow()
    private var voiceRecorder: android.media.MediaRecorder? = null
    private var voiceFile: java.io.File? = null
    private var voiceStartTime = 0L
    private var voiceFinalSeconds = 0
    private var voiceTimerJob: Job? = null
    private var voicePlayer: android.media.MediaPlayer? = null
    private var voiceLoadJob: Job? = null
    private var duckedPrevVolume: Float? = null
    private var voiceEnhancer: android.media.audiofx.LoudnessEnhancer? = null
    private val voiceBoostMb = 800   // 🔊 ভয়েস প্লে করার সময় +৮ ডেসিবেল (600–1200 এর মধ্যে বদলানো যায়)
    // ভয়েস ডেটা রাখার পাথ: ListenTogetherVoices/{রুমকোড}

    private fun voicesRef(): com.google.firebase.database.DatabaseReference? {
        val code = roomDbRef?.key ?: return null
        return com.google.firebase.database.FirebaseDatabase.getInstance(dbUrl)
            .getReference("ListenTogetherVoices").child(code)
    }

    @Suppress("DEPRECATION")
    private fun newRecorder(context: Context): android.media.MediaRecorder {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            android.media.MediaRecorder(context)
        } else {
            android.media.MediaRecorder()
        }
    }

    // ---------- রেকর্ডিং ----------

    fun startVoiceRecording(context: Context) {
        if (_voiceState.value != "idle") return
        val status = _myRoomStatus.value
        if (roomDbRef == null || status == "pending" || status == "disconnected") {
            Toast.makeText(context, "Join a room first!", Toast.LENGTH_SHORT).show()
            return
        }

        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(context, "Microphone permission required!", Toast.LENGTH_SHORT).show()
            return
        }

        stopVoicePlayback() // কথা বলার সময় অন্য ভয়েস বন্ধ
        // 💎 ফ্রি: ১৫ সেকেন্ড, প্রিমিয়াম: ৩০ সেকেন্ড
        voiceIsPremium = isUserPremium(context)
        maxVoiceSeconds = if (voiceIsPremium) 30 else 15
        _voiceMaxSeconds.value = maxVoiceSeconds
        // 🎵 গানের ভলিউম কমিয়ে দিই, যাতে মাইকে গান না ধরে
        duckMusic(recordingMusicVolume)
        val file = java.io.File(context.cacheDir, "voice_rec_${System.currentTimeMillis()}.m4a")
        var started = false
        // প্রথমে ১৬ kbps (≈১২০KB/মিনিট), কোনো ফোন না নিলে ৩২ kbps
        for (bitRate in listOf(16000, 32000)) {
            val recorder = newRecorder(context)
            try {
                recorder.setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
                recorder.setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
                recorder.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
                recorder.setAudioChannels(1)
                recorder.setAudioSamplingRate(16000)
                recorder.setAudioEncodingBitRate(bitRate)
                recorder.setOutputFile(file.absolutePath)
                recorder.prepare()
                recorder.start()
                voiceRecorder = recorder
                started = true
                break
            } catch (e: Exception) {
                e.printStackTrace()
                try { recorder.release() } catch (x: Exception) {}
            }
        }

        if (!started) {
            restoreMusicVolume()
            file.delete()
            Toast.makeText(context, "Could not start recording", Toast.LENGTH_SHORT).show()
            return
        }

        voiceFile = file
        voiceStartTime = System.currentTimeMillis()
        voiceFinalSeconds = 0
        _voiceSeconds.value = 0
        _voiceState.value = "recording"
        voiceTimerJob?.cancel()
        voiceTimerJob = viewModelScope.launch {
            while (isActive) {
                delay(200)
                val elapsedMs = System.currentTimeMillis() - voiceStartTime
                _voiceSeconds.value = (elapsedMs / 1000).toInt().coerceAtMost(maxVoiceSeconds)
                if (elapsedMs >= maxVoiceSeconds * 1000L) {
                    // সীমা পূর্ণ: রেকর্ডিং বন্ধ, সেন্ডের জন্য রেডি
                    if (!finishRecording()) {
                        cancelVoiceRecording()
                    } else if (!voiceIsPremium) {
                        appContext?.let {
                            Toast.makeText(it, "Free limit reached (${maxVoiceSeconds}s). Premium users can send 30s!", Toast.LENGTH_LONG).show()
                        }
                    }

                    break
                }
            }
        }
    }

    // রেকর্ডিং থামিয়ে ফাইল রেডি করে। সফল হলে true

    private fun finishRecording(): Boolean {
        voiceTimerJob?.cancel()
        voiceTimerJob = null
        restoreMusicVolume() // 🎵 রেকর্ডিং শেষ: গান আবার আগের ভলিউমে
        val recorder = voiceRecorder
        voiceRecorder = null
        var ok = false
        if (recorder != null) {
            try {
                recorder.stop()
                ok = true
            } catch (e: Exception) {
                e.printStackTrace() // খুব ছোট রেকর্ডিংয়ে stop() ফেল করে
            }

            try { recorder.release() } catch (e: Exception) {}
        }

        val elapsedMs = System.currentTimeMillis() - voiceStartTime
        voiceFinalSeconds = ((elapsedMs + 500) / 1000).toInt().coerceIn(0, maxVoiceSeconds)
        _voiceSeconds.value = voiceFinalSeconds
        if (ok) _voiceState.value = "ready"
        return ok
    }

    // 🗑️ রেকর্ডিং বাতিল: কিছুই আপলোড হবে না

    fun cancelVoiceRecording() {
        val state = _voiceState.value
        if (state != "recording" && state != "ready") return
        voiceTimerJob?.cancel()
        voiceTimerJob = null
        restoreMusicVolume() // 🎵 গান আবার আগের ভলিউমে
        voiceRecorder?.let {
            try { it.stop() } catch (e: Exception) {}
            try { it.release() } catch (e: Exception) {}
        }

        voiceRecorder = null
        voiceFile?.delete()
        voiceFile = null
        _voiceSeconds.value = 0
        _voiceState.value = "idle"
    }

    // ---------- সেন্ড ----------

    fun sendVoiceMessage(context: Context, replyTo: com.musicplayer.lytrix.model.ChatMessage? = null) {
        val state = _voiceState.value
        if (state != "recording" && state != "ready") return
        val status = _myRoomStatus.value
        val roomRef = roomDbRef
        val voicesNode = voicesRef()
        if (roomRef == null || voicesNode == null || status == "pending" || status == "disconnected") {
            cancelVoiceRecording()
            Toast.makeText(context, "You cannot send voice messages now!", Toast.LENGTH_SHORT).show()
            return
        }

        if (state == "recording" && !finishRecording()) {
            cancelVoiceRecording()
            Toast.makeText(context, "Recording too short!", Toast.LENGTH_SHORT).show()
            return
        }

        val file = voiceFile
        val seconds = voiceFinalSeconds
        if (file == null || !file.exists() || seconds < 1) {
            cancelVoiceRecording()
            Toast.makeText(context, "Recording too short!", Toast.LENGTH_SHORT).show()
            return
        }

        val voiceId = voicesNode.push().key
        val msgId = roomRef.child("messages").push().key
        if (voiceId == null || msgId == null) {
            cancelVoiceRecording()
            return
        }

        val deviceId = getDeviceId(context)
        val senderName = myUserName.ifEmpty { getSavedUserName(context) }
        val senderRole = status
        val appCtx = context.applicationContext
        _voiceState.value = "sending"
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bytes = file.readBytes()
                val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                if (base64.length > 390_000) throw IllegalStateException("Voice too large")
                // ১. আগে ভয়েস ডেটা আপলোড
                kotlinx.coroutines.withTimeout(20_000) { voicesNode.child(voiceId).setValue(base64).await() }
                // নিজের ফোনে ক্যাশ রাখি, যাতে নিজের ভয়েস প্লে করতে আবার ডাউনলোড না লাগে
                try { file.copyTo(java.io.File(appCtx.cacheDir, "voice_play_$voiceId.m4a"), overwrite = true) } catch (e: Exception) {}
                // ২. তারপর চ্যাটে মেসেজ
                val chatMessage = com.musicplayer.lytrix.model.ChatMessage(
                    senderId = deviceId, senderName = senderName, message = "🎤 Voice message",
                    timestamp = System.currentTimeMillis(), role = senderRole,
                    type = "voice", voiceId = voiceId, audioDuration = seconds,
                    replyToId = replyTo?.let { "${it.senderId}_${it.timestamp}" } ?: "",
                    replyToName = replyTo?.senderName ?: "",
                    replyToText = replyTo?.let { replyPreviewText(it) } ?: ""
                )
                kotlinx.coroutines.withTimeout(20_000) { roomRef.child("messages").child(msgId).setValue(chatMessage).await() }
                withContext(Dispatchers.Main) { pruneOldMessages() }
            } catch (e: Exception) {
                e.printStackTrace()
                try { voicesNode.child(voiceId).removeValue() } catch (x: Exception) {} // আধা-আপলোড মুছে ফেলি
                withContext(Dispatchers.Main) {
                    Toast.makeText(appCtx, "Failed to send voice message", Toast.LENGTH_SHORT).show()
                }

            } finally {
                file.delete()
                withContext(Dispatchers.Main) {
                    voiceFile = null
                    _voiceSeconds.value = 0
                    _voiceState.value = "idle"
                }
            }
        }
    }

    // চ্যাটে ৫০টার বেশি মেসেজ হলে সবচেয়ে পুরনোটা (ভয়েস হলে তার ডেটাসহ) মুছে দেয়

    private fun pruneOldMessages() {
        val msgs = _currentListenRoom.value?.messages ?: return
        if (msgs.size > 50) {
            val oldest = msgs.entries.minByOrNull { it.value.timestamp } ?: return
            roomDbRef?.child("messages")?.child(oldest.key)?.removeValue()
            if (oldest.value.type == "voice" && oldest.value.voiceId.isNotBlank()) {
                voicesRef()?.child(oldest.value.voiceId)?.removeValue()
            }
        }
    }

    // ---------- প্লেব্যাক ----------

    fun toggleVoicePlayback(context: Context, voiceId: String) {
        if (voiceId.isBlank()) return
        if (_voiceState.value == "recording") return // রেকর্ডিং চলার সময় অন্য ভয়েস প্লে হবে না (গানের ভলিউম ঠিক রাখতে)
        if (_playingVoiceId.value == voiceId) {
            stopVoicePlayback()
            return
        }

        if (_loadingVoiceId.value == voiceId) return
        stopVoicePlayback()
        _loadingVoiceId.value = voiceId
        val appCtx = context.applicationContext
        val node = voicesRef()?.child(voiceId)
        voiceLoadJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val cacheFile = java.io.File(appCtx.cacheDir, "voice_play_$voiceId.m4a")
                if (!cacheFile.exists() || cacheFile.length() == 0L) {
                    val snap = kotlinx.coroutines.withTimeout(20_000) { node?.get()?.await() }
                    val b64 = snap?.getValue(String::class.java)
                    if (b64.isNullOrBlank()) {
                        withContext(Dispatchers.Main) {
                            _loadingVoiceId.value = null
                            Toast.makeText(appCtx, "Voice message not available", Toast.LENGTH_SHORT).show()
                        }

                        return@launch
                    }

                    cacheFile.writeBytes(android.util.Base64.decode(b64, android.util.Base64.NO_WRAP))
                }

                withContext(Dispatchers.Main) { startVoicePlayer(appCtx, voiceId, cacheFile) }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    _loadingVoiceId.value = null
                    Toast.makeText(appCtx, "Could not load voice message", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun startVoicePlayer(context: Context, voiceId: String, file: java.io.File) {
        try {
            val player = android.media.MediaPlayer()
            voicePlayer = player
            player.setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            player.setDataSource(file.absolutePath)
            player.setOnCompletionListener { stopVoicePlayback() }
            player.setOnErrorListener { _, _, _ -> stopVoicePlayback(); true }
            player.setVolume(1f, 1f)
            player.prepare()
            // 🔊 ভয়েস জোরালো করা
            try {
                voiceEnhancer?.release()
                voiceEnhancer = android.media.audiofx.LoudnessEnhancer(player.audioSessionId).apply {
                    setTargetGain(voiceBoostMb)
                    enabled = true
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }

            duckMusic() // 🎵 গান ১০%
            player.start()
            _loadingVoiceId.value = null
            _playingVoiceId.value = voiceId
        } catch (e: Exception) {
            e.printStackTrace()
            stopVoicePlayback()
            Toast.makeText(context, "Could not play voice message", Toast.LENGTH_SHORT).show()
        }
    }

    fun stopVoicePlayback() {
        voiceLoadJob?.cancel()
        voiceLoadJob = null
        try { voiceEnhancer?.release() } catch (e: Exception) {}
        voiceEnhancer = null
        voicePlayer?.let {
            try { it.stop() } catch (e: Exception) {}
            try { it.release() } catch (e: Exception) {}
        }

        voicePlayer = null
        _playingVoiceId.value = null
        _loadingVoiceId.value = null
        restoreMusicVolume() // 🎵 গান আবার আগের ভলিউমে
    }

    // ---------- Audio Ducking ----------

    private fun duckMusic(level: Float = 0.1f) {
        val c = mediaController ?: return
        if (duckedPrevVolume == null) duckedPrevVolume = c.volume
        c.volume = level
    }

    private fun restoreMusicVolume() {
        val prev = duckedPrevVolume
        duckedPrevVolume = null
        val c = mediaController
        if (c != null && prev != null) c.volume = if (prev < 0.05f) 1f else prev
    }

    private fun clearVoiceCache(context: Context) {
        try {
            context.cacheDir.listFiles()?.forEach { f ->
                if (f.name.startsWith("voice_play_") || f.name.startsWith("voice_rec_")) f.delete()
            }

        } catch (e: Exception) {}
    }

    fun getLyricsProviderNames(context: Context) = com.musicplayer.lytrix.utils.LyricsFetcher.getProviderNames(context)
    val lyricsProviderNames = listOf("YouLyPlus", "BetterLyrics", "SimpMusic", "YouTube Music", "KuGou", "LrcLib")

    fun changeLyricsSource(context: Context) {
        val song = _currentSong.value ?: return
        if (_currentLyrics.value == "LOADING_LYRICS_STATE") {
            Toast.makeText(context, "Already fetching, please wait...", Toast.LENGTH_SHORT).show()
            return
        }

        val activeProviders = getLyricsProviderNames(context)
        val nextIndex = if (_currentLyricsProviderIndex.value == -1) 0 else (_currentLyricsProviderIndex.value + 1) % activeProviders.size
        _currentLyricsProviderIndex.value = nextIndex
        _currentLyrics.value = "LOADING_LYRICS_STATE"
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = com.musicplayer.lytrix.utils.LyricsFetcher.fetchLyricsWithResult(context, song, nextIndex)
                val providerName = activeProviders.getOrElse(nextIndex) { "Unknown" }
                withContext(Dispatchers.Main) {
                    if (result != null && result.lyrics.isNotBlank()) {
                        Toast.makeText(context, "Loaded from $providerName", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "$providerName has no lyrics for this song", Toast.LENGTH_SHORT).show()
                    }
                }

                if (result != null && result.lyrics.isNotBlank() && result.lyrics != "LOADING_LYRICS_STATE") {
                    val prefs = context.getSharedPreferences("lyrics_prefs", Context.MODE_PRIVATE)
                    val lyricsKey = "${song.title}_${song.artist}_lyrics"
                    val providerKey = "${song.title}_${song.artist}_provider"
                    prefs.edit()
                        .putString(lyricsKey, result.lyrics)
                        .putString(providerKey, providerName)
                        .apply()
                    _currentLyrics.value = result.lyrics
                    _currentLyricsProviderName.value = providerName
                } else {
                    _currentLyrics.value = null
                    _currentLyricsProviderName.value = null
                }

            } catch (e: Exception) {
                _currentLyrics.value = null
                _currentLyricsProviderName.value = null
            }
        }
    }

    private var alarmFadeJob: Job? = null

    /**
     * Triggers wake-up playback for a scheduled [com.musicplayer.lytrix.alarm.MusicAlarmEntry]
     * with optional gentle fade-in volume ramp.
     */
    fun playMusicAlarm(
        context: Context,
        alarm: com.musicplayer.lytrix.alarm.MusicAlarmEntry
    ): Boolean {
        viewModelScope.launch {
            var controllerWaited = 0
            while (mediaController == null && controllerWaited < 4000) {
                delay(150)
                controllerWaited += 150
            }

            if (alarm.sourceType == com.musicplayer.lytrix.alarm.AlarmSourceType.SPECIFIC_SONG &&
                (alarm.songId.isNotBlank() || alarm.songUri.isNotBlank())
            ) {
                if (alarm.songIsOnline) {
                    val isDirectHttpAudio = alarm.songUri.startsWith("http") &&
                        !alarm.songUri.contains("youtube.com", ignoreCase = true) &&
                        !alarm.songUri.contains("youtu.be", ignoreCase = true)
                    if (isDirectHttpAudio) {
                        val onlineSong = Song(
                            id = alarm.songId.hashCode().toLong(),
                            title = alarm.songTitle.ifBlank { "Alarm Song" },
                            artist = alarm.songArtist.ifBlank { "Unknown Artist" },
                            album = "Music Alarm",
                            duration = 0L,
                            uriString = alarm.songUri,
                            filePath = "ONLINE",
                            albumArtUriString = alarm.songArtUrl.ifBlank { null },
                            originalId = alarm.songId
                        )
                        playSong(onlineSong, listOf(onlineSong))
                    } else {
                        val ytId = extractYouTubeVideoId(alarm.songUri) ?: alarm.songId
                        playLytrixPlaylist(
                            listOf(
                                YtMusicTrack(
                                    id = ytId,
                                    title = alarm.songTitle.ifBlank { "Alarm Song" },
                                    artist = alarm.songArtist.ifBlank { "" },
                                    thumbnailUrl = alarm.songArtUrl
                                )
                            ),
                            0,
                            context
                        )
                    }
                    startAlarmFadeIn(alarm.fadeInSeconds)
                    return@launch
                } else {
                    var waitedLocal = 0
                    while (_songsList.value.isEmpty() && waitedLocal < 3000) {
                        delay(150)
                        waitedLocal += 150
                    }
                    val allLocal = _songsList.value
                    val matched = allLocal.firstOrNull {
                        it.id.toString() == alarm.songId || (alarm.songUri.isNotBlank() && it.uriString == alarm.songUri)
                    } ?: alarm.songUri.takeIf { it.isNotBlank() }?.let { uri ->
                        Song(
                            id = alarm.songId.toLongOrNull() ?: uri.hashCode().toLong(),
                            title = alarm.songTitle.ifBlank { "Alarm Song" },
                            artist = alarm.songArtist.ifBlank { "Unknown Artist" },
                            album = "Music Alarm",
                            duration = 0L,
                            uriString = uri,
                            albumArtUriString = alarm.songArtUrl.ifBlank { null }
                        )
                    }
                    if (matched != null) {
                        val tail = allLocal.filter { it.id != matched.id }.take(15)
                        playSong(matched, listOf(matched) + tail)
                        startAlarmFadeIn(alarm.fadeInSeconds)
                        return@launch
                    }
                }
            }

            if (alarm.sourceType == com.musicplayer.lytrix.alarm.AlarmSourceType.ONLINE_PLAYLIST && alarm.playlistId.isNotBlank()) {
                val onlineTracks = withContext(Dispatchers.IO) {
                    val sp = context.getSharedPreferences("online_pl_${alarm.playlistId}", Context.MODE_PRIVATE)
                    sp.all.values.mapNotNull { raw ->
                        runCatching {
                            val o = org.json.JSONObject(raw.toString())
                            YtMusicTrack(
                                id = o.optString("id"),
                                title = o.optString("name", o.optString("title", "Alarm Track")),
                                artist = o.optString("artist", ""),
                                thumbnailUrl = o.optString("imageUrl", "")
                            )
                        }.getOrNull()
                    }
                }
                if (onlineTracks.isNotEmpty()) {
                    val ordered = if (alarm.randomTrack) onlineTracks.shuffled() else onlineTracks
                    playLytrixPlaylist(ordered, 0, context)
                    startAlarmFadeIn(alarm.fadeInSeconds)
                    return@launch
                }
            }

            var waitedSongs = 0
            while (_songsList.value.isEmpty() && waitedSongs < 3000) {
                delay(150)
                waitedSongs += 150
            }

            val allLocal = _songsList.value
            val chosenList: List<Song> = when (alarm.sourceType) {
                com.musicplayer.lytrix.alarm.AlarmSourceType.LIKED -> {
                    val favSet = favoriteSongIds.value.toSet()
                    allLocal.filter { it.id in favSet }.ifEmpty { allLocal }
                }
                com.musicplayer.lytrix.alarm.AlarmSourceType.RECENT -> {
                    val recentSet = recentSongIds.value
                    recentSet.mapNotNull { rid -> allLocal.find { it.id == rid } }.ifEmpty { allLocal }
                }
                com.musicplayer.lytrix.alarm.AlarmSourceType.LOCAL_PLAYLIST -> {
                    val plId = alarm.playlistId.toLongOrNull()
                    if (plId != null) {
                        val ids = withContext(Dispatchers.IO) {
                            com.musicplayer.lytrix.db.AppDatabase.getDatabase(context)
                                .playlistDao().getPlaylistCrossRefs(plId).first().map { it.songId }
                        }
                        ids.mapNotNull { id -> allLocal.find { it.id == id } }.ifEmpty { allLocal }
                    } else allLocal
                }
                else -> allLocal
            }

            if (chosenList.isEmpty()) {
                com.musicplayer.lytrix.alarm.MusicAlarmReceiver.playFallbackRingtone(context)
                return@launch
            }

            val finalQueue = if (alarm.randomTrack) chosenList.shuffled() else chosenList
            playSong(finalQueue.first(), finalQueue)
            startAlarmFadeIn(alarm.fadeInSeconds)
        }
        return true
    }

    private fun startAlarmFadeIn(fadeInSeconds: Int) {
        alarmFadeJob?.cancel()
        if (fadeInSeconds <= 0) {
            PlaybackService.setPlaybackVolume(1.0f)
            mediaController?.volume = 1.0f
            return
        }
        alarmFadeJob = viewModelScope.launch {
            val steps = 20
            val stepDelay = (fadeInSeconds * 1000L / steps).coerceAtLeast(150L)
            for (i in 1..steps) {
                val vol = 0.08f + (0.92f * (i.toFloat() / steps))
                PlaybackService.setPlaybackVolume(vol)
                mediaController?.volume = vol
                delay(stepDelay)
            }
        }
    }
}

fun cleanArtistDisplayName(raw: String): String {
    var cleaned = raw
        .replace(Regex("""\s*[•·∙․|\-–—,]\s*\d+[\d.,]*\s*[kmbKMB]?\s*(?:views?|plays?|subscribers?|subs?|likes?)?.*""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s*[•·∙․|\-–—,]\s*(?:views?|plays?|subscribers?|subs?|likes?).*""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s*\d+[\d.,]*\s*[kmbKMB]?\s*(?:views?|plays?).*""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s*[•·∙․|\-–—,]\s*(?:streamed\s+|premiered\s+)?\d+\s*(?:days?|weeks?|months?|years?|hours?|mins?|minutes?|secs?|seconds?)\s*ago.*""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s*[•·∙․|\-–—,]\s*(?:\d{1,2}\s+)?(?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:tember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\b[,\s\d]*.*""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s*[•·∙․|\-–—,]\s*(?:19\d\d|20\d\d)\b.*""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s*[•·∙․|\-–—,]\s*\d{1,4}[/-]\d{1,2}[/-]\d{2,4}.*""", RegexOption.IGNORE_CASE), "")
        .trim()
    val separators = charArrayOf('•', '·', '∙', '․', '|', '-', '–', '—', ',')
    while (cleaned.isNotEmpty() && cleaned.last() in separators) {
        cleaned = cleaned.dropLast(1).trim()
    }

    return cleaned
}

fun extractYouTubeVideoId(text: String?): String? {
    if (text.isNullOrBlank()) return null
    val patterns = listOf(
        Regex("""youtu\.be/([a-zA-Z0-9_-]{11})"""),
        Regex("""[?&]v=([a-zA-Z0-9_-]{11})"""),
        Regex("""youtube\.com/shorts/([a-zA-Z0-9_-]{11})"""),
        Regex("""youtube\.com/embed/([a-zA-Z0-9_-]{11})""")
    )
    for (pattern in patterns) {
        val match = pattern.find(text)
        if (match != null) {
            return match.groupValues[1]
        }
    }
    return null
}
