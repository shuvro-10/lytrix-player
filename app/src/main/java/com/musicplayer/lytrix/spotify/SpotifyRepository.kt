package com.musicplayer.lytrix.spotify

import android.content.Context
import android.util.Log
import java.net.URLEncoder
import com.musicplayer.lytrix.model.JioSaavnSong
import com.musicplayer.lytrix.spotify.sync.SpotifyHashProvider
import com.musicplayer.lytrix.spotify.sync.SpotifyHashSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL

data class SpotifyPlaylistItem(
    val id: String,
    val name: String,
    val coverUrl: String,
    val songCount: Int,
    val source: String = "Spotify"
)

object SpotifyRepository {
    private const val GQL_URL = "https://api-partner.spotify.com/pathfinder/v2/query"
    private const val LIKED_PREFS_NAME = "spotify_liked_songs_cache"

    val syncedPlaylistsInSession = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    fun isPlaylistSyncedInSession(playlistName: String): Boolean {
        return syncedPlaylistsInSession.contains(playlistName)
    }

    fun markPlaylistSyncedInSession(playlistName: String) {
        syncedPlaylistsInSession.add(playlistName)
    }

    /**
     * Synchronizes hashes, playlists and liked songs in the background.
     */
    suspend fun syncAll(context: Context) = withContext(Dispatchers.IO) {
        if (!SpotifyPreferencesManager.isLoggedIn(context)) return@withContext

        // 1. Sync GQL Hashes
        SpotifyHashSync.sync(context)

        // 2. Fetch Liked Songs via GQL
        try {
            fetchLikedSongs(context)
        } catch (e: Exception) {
            Timber.w(e, "SpotifyRepository: sync liked songs failed: ${e.message}")
        }

        // 3. Fetch Playlists via GQL
        try {
            fetchPlaylists(context)
        } catch (e: Exception) {
            Timber.w(e, "SpotifyRepository: sync playlists failed: ${e.message}")
        }
    }

    /**
     * Fetches user's Spotify playlists via GQL operation 'libraryV3' (matching Meld).
     */
    suspend fun fetchPlaylists(context: Context): List<SpotifyPlaylistItem> = withContext(Dispatchers.IO) {
        val token = SpotifyAuth.ensureValidToken(context) ?: return@withContext emptyList()
        val vars = JSONObject().apply {
            put("filters", org.json.JSONArray().apply { put("Playlists") })
            put("order", JSONObject.NULL)
            put("textFilter", "")
            put("features", org.json.JSONArray().apply {
                put("LIKED_SONGS")
                put("YOUR_EPISODES_V2")
                put("PRERELEASES")
                put("EVENTS")
            })
            put("limit", 50)
            put("offset", 0)
            put("flatten", true)
            put("expandedFolders", org.json.JSONArray())
            put("folderUri", JSONObject.NULL)
            put("includeFoldersWhenFlattening", false)
        }

        val json = executeGql(token, "libraryV3", vars) ?: return@withContext emptyList()
        val results = mutableListOf<SpotifyPlaylistItem>()

        try {
            val libraryData = json.optJSONObject("data")?.optJSONObject("me")?.optJSONObject("libraryV3")
            val items = libraryData?.optJSONArray("items") ?: return@withContext emptyList()

            val plPrefs = context.getSharedPreferences("online_playlists_meta", Context.MODE_PRIVATE)
            val coverPrefs = context.getSharedPreferences("playlist_covers", Context.MODE_PRIVATE)
            val sourcePrefs = context.getSharedPreferences("playlist_source", Context.MODE_PRIVATE)
            val timePrefs = context.getSharedPreferences("playlist_time", Context.MODE_PRIVATE)
            val idPrefs = context.getSharedPreferences("playlist_ids", Context.MODE_PRIVATE)

            val existingNames = plPrefs.getStringSet("names", emptySet())?.toMutableSet() ?: mutableSetOf()

            for (i in 0 until items.length()) {
                val itemElem = items.optJSONObject(i) ?: continue
                val wrapper = itemElem.optJSONObject("item") ?: continue
                val typeName = wrapper.optString("__typename")
                if (!typeName.contains("Playlist", ignoreCase = true)) continue

                val data = wrapper.optJSONObject("data") ?: continue
                val playlistUri = wrapper.optString("_uri").ifBlank { wrapper.optString("uri") }
                val id = playlistUri.substringAfterLast(":")
                if (id.isBlank()) continue

                val name = data.optString("name", "Spotify Playlist")
                val imagesItems = data.optJSONObject("images")?.optJSONArray("items")
                var cover = ""
                if (imagesItems != null && imagesItems.length() > 0) {
                    val sources = imagesItems.getJSONObject(0).optJSONArray("sources")
                    if (sources != null && sources.length() > 0) {
                        cover = sources.getJSONObject(0).optString("url", "")
                    }
                }
                if (cover.isBlank()) {
                    cover = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745"
                }

                val isLiked = name.equals("Liked Songs", ignoreCase = true)
                val count = if (isLiked) {
                    val likedCount = getCachedLikedSongs(context).size
                    if (likedCount > 0) likedCount else -1
                } else {
                    val existingCount = plPrefs.getInt("count_$name", -1)
                    if (existingCount > 0) existingCount else -1
                }

                results.add(SpotifyPlaylistItem(id, name, cover, count, "Spotify"))

                existingNames.add(name)
                sourcePrefs.edit().putString(name, "Spotify").apply()
                idPrefs.edit().putString(name, id).apply()
                coverPrefs.edit().putString(name, cover).apply()
                if (count > 0) {
                    plPrefs.edit().putInt("count_$name", count).apply()
                }
                if (!timePrefs.contains(name)) {
                    timePrefs.edit().putLong(name, System.currentTimeMillis()).apply()
                }
            }
            plPrefs.edit().putStringSet("names", existingNames).apply()
            Timber.d("SpotifyRepository: fetched ${results.size} playlists via GQL libraryV3")
        } catch (e: Exception) {
            Timber.e(e, "SpotifyRepository: failed to parse GQL playlists: ${e.message}")
        }
        results
    }

    /**
     * Fetches tracks for a given Spotify playlist ID via GQL operation 'fetchPlaylist' (matching Meld).
     */
    suspend fun fetchPlaylistTracks(
        context: Context,
        playlistId: String,
        playlistName: String
    ): List<JioSaavnSong> = withContext(Dispatchers.IO) {
        val token = SpotifyAuth.ensureValidToken(context) ?: return@withContext emptyList()
        val cleanPlaylistId = playlistId.removePrefix("spotify:playlist:").trim()
        val vars = JSONObject().apply {
            put("uri", "spotify:playlist:$cleanPlaylistId")
            put("offset", 0)
            put("limit", 100)
            put("enableWatchFeedEntrypoint", false)
        }

        val json = executeGql(token, "fetchPlaylist", vars) ?: return@withContext emptyList()
        val songs = mutableListOf<JioSaavnSong>()

        try {
            val content = json.optJSONObject("data")?.optJSONObject("playlistV2")?.optJSONObject("content")
            val totalCount = content?.optInt("totalCount", 0) ?: 0
            val items = content?.optJSONArray("items") ?: return@withContext emptyList()

            val songPrefs = context.getSharedPreferences("online_pl_$playlistName", Context.MODE_PRIVATE)
            val editor = songPrefs.edit()
            editor.clear()

            for (i in 0 until items.length()) {
                val elem = items.optJSONObject(i) ?: continue
                val itemWrapper = elem.optJSONObject("itemV2") ?: continue
                val itemData = itemWrapper.optJSONObject("data") ?: continue
                val wrapperUri = itemWrapper.optString("_uri").ifBlank { itemWrapper.optString("uri") }

                val song = parseGqlTrack(itemData, wrapperUri) ?: continue
                songs.add(song)

                val songJson = JSONObject().apply {
                    put("id", song.id)
                    put("name", song.name)
                    put("artist", song.artist)
                    put("imageUrl", song.imageUrl)
                    put("downloadUrl", "")
                }
                editor.putString(song.id, songJson.toString())
            }
            editor.apply()
            val finalTrackCount = if (totalCount > 0) totalCount else songs.size
            if (finalTrackCount > 0) {
                val plMeta = context.getSharedPreferences("online_playlists_meta", Context.MODE_PRIVATE)
                plMeta.edit().putInt("count_$playlistName", finalTrackCount).apply()
            }
            markPlaylistSyncedInSession(playlistName)
            Timber.d("SpotifyRepository: fetched ${songs.size} tracks for playlist $playlistName (total: $totalCount)")
        } catch (e: Exception) {
            Timber.e(e, "SpotifyRepository: failed to parse GQL playlist tracks: ${e.message}")
        }
        songs
    }

    /**
     * Fetches user's Spotify Liked Songs via GQL operation 'fetchLibraryTracks' (matching Meld).
     */
    suspend fun fetchLikedSongs(context: Context): List<JioSaavnSong> = withContext(Dispatchers.IO) {
        val token = SpotifyAuth.ensureValidToken(context) ?: return@withContext emptyList()
        val vars = JSONObject().apply {
            put("offset", 0)
            put("limit", 50)
        }

        val json = executeGql(token, "fetchLibraryTracks", vars) ?: return@withContext emptyList()
        val songs = mutableListOf<JioSaavnSong>()

        try {
            val tracksObj = json.optJSONObject("data")?.optJSONObject("me")?.optJSONObject("library")?.optJSONObject("tracks")
            val items = tracksObj?.optJSONArray("items") ?: return@withContext emptyList()

            val cachePrefs = context.getSharedPreferences(LIKED_PREFS_NAME, Context.MODE_PRIVATE)
            val editor = cachePrefs.edit()
            editor.clear()
            val idsSet = mutableSetOf<String>()

            for (i in 0 until items.length()) {
                val elem = items.optJSONObject(i) ?: continue
                val trackWrapper = elem.optJSONObject("track") ?: continue
                val trackData = trackWrapper.optJSONObject("data") ?: continue
                val wrapperUri = trackWrapper.optString("_uri").ifBlank { trackWrapper.optString("uri") }

                val song = parseGqlTrack(trackData, wrapperUri) ?: continue
                songs.add(song)
                idsSet.add(song.id)

                val songJson = JSONObject().apply {
                    put("id", song.id)
                    put("name", song.name)
                    put("artist", song.artist)
                    put("imageUrl", song.imageUrl)
                    put("downloadUrl", "")
                }
                editor.putString(song.id, songJson.toString())
            }
            editor.putStringSet("song_ids", idsSet)
            editor.apply()

            // Pre-populate online_pl_Liked Songs so Liked Songs opens instantly
            try {
                val likedPlPrefs = context.getSharedPreferences("online_pl_Liked Songs", Context.MODE_PRIVATE)
                val likedPlEditor = likedPlPrefs.edit()
                for (s in songs) {
                    val sJson = JSONObject().apply {
                        put("id", s.id)
                        put("name", s.name)
                        put("artist", s.artist)
                        put("imageUrl", s.imageUrl)
                        put("downloadUrl", "")
                    }
                    likedPlEditor.putString(s.id, sJson.toString())
                }
                likedPlEditor.apply()
                val plMeta = context.getSharedPreferences("online_playlists_meta", Context.MODE_PRIVATE)
                plMeta.edit().putInt("count_Liked Songs", songs.size).apply()
            } catch (ignored: Exception) {}

            markPlaylistSyncedInSession("Liked Songs")
            Timber.d("SpotifyRepository: fetched ${songs.size} Liked Songs via GQL fetchLibraryTracks")
        } catch (e: Exception) {
            Timber.e(e, "SpotifyRepository: failed to fetch GQL liked songs: ${e.message}")
        }
        songs
    }

    /**
     * Searches a Spotify track ID using SimpMusic's Pathfinder v1 GET + Spotify Web API fallback.
     */
    suspend fun searchTrackGql(token: String, query: String): String? = withContext(Dispatchers.IO) {
        val clientToken = try {
            com.musicplayer.lytrix.spotify.canvas.SpotifyCanvas.getClientToken()
        } catch (e: Exception) {
            null
        }

        // 1. SimpMusic exact Pathfinder v1 searchTracks GET
        try {
            val vars = JSONObject().apply {
                put("searchTerm", query)
                put("offset", 0)
                put("limit", 3)
                put("numberOfTopResults", 3)
                put("includeAudiobooks", true)
                put("includePreReleases", false)
            }
            val sha = "bc1ca2fcd0ba1013a0fc88e6cc4f190af501851e3dafd3e1ef85840297694428"
            val ext = JSONObject().apply {
                put("persistedQuery", JSONObject().apply {
                    put("version", 1)
                    put("sha256Hash", sha)
                })
            }
            val pathfinderUrl = "https://api-partner.spotify.com/pathfinder/v1/query?operationName=searchTracks" +
                    "&variables=" + URLEncoder.encode(vars.toString(), "UTF-8") +
                    "&extensions=" + URLEncoder.encode(ext.toString(), "UTF-8")

            val conn = URL(pathfinderUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Authorization", "Bearer $token")
            if (!clientToken.isNullOrBlank()) {
                conn.setRequestProperty("Client-Token", clientToken)
            }
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
            conn.setRequestProperty("Accept", "application/json")

            if (conn.responseCode in 200..299) {
                val respText = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(respText)
                val tracks = json.optJSONObject("data")?.optJSONObject("searchV2")?.optJSONObject("tracksV2")?.optJSONArray("items")
                if (tracks != null && tracks.length() > 0) {
                    val item = tracks.getJSONObject(0).optJSONObject("item")
                    val id = item?.optJSONObject("data")?.optString("id")?.takeIf { it.isNotBlank() }
                        ?: item?.optString("id")?.takeIf { it.isNotBlank() }
                        ?: item?.optString("_uri")?.substringAfterLast(":")?.takeIf { it.isNotBlank() }
                        ?: item?.optString("uri")?.substringAfterLast(":")?.takeIf { it.isNotBlank() }
                    if (!id.isNullOrBlank()) {
                        Timber.d("SpotifyRepository: Pathfinder searchTracks resolved '$query' -> ID: $id")
                        Log.i("SpotifyCanvas", "Pathfinder searchTracks resolved '$query' -> ID: $id")
                        return@withContext id
                    }
                }
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }
                Log.w("SpotifyCanvas", "Pathfinder searchTracks HTTP ${conn.responseCode}: $err")
            }
        } catch (e: Exception) {
            Log.w("SpotifyCanvas", "Pathfinder searchTracks error for '$query': ${e.message}")
        }

        // 2. Official Spotify Web API search fallback
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val webUrl = "https://api.spotify.com/v1/search?q=$encodedQuery&type=track&limit=3"
            val conn = URL(webUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            conn.setRequestProperty("Accept", "application/json")

            if (conn.responseCode in 200..299) {
                val respText = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(respText)
                val items = json.optJSONObject("tracks")?.optJSONArray("items")
                if (items != null && items.length() > 0) {
                    val id = items.getJSONObject(0).optString("id")
                    if (id.isNotBlank()) {
                        Timber.d("SpotifyRepository: Web API search resolved '$query' -> ID: $id")
                        Log.i("SpotifyCanvas", "Web API search resolved '$query' -> ID: $id")
                        return@withContext id
                    }
                }
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }
                Log.w("SpotifyCanvas", "Web API search HTTP ${conn.responseCode}: $err")
            }
        } catch (e: Exception) {
            Log.e("SpotifyCanvas", "Web API search error for '$query': ${e.message}")
        }

        Log.w("SpotifyCanvas", "Could not resolve track ID for '$query'")
        null
    }

    /**
     * Parses a GQL track object into Lytrix's JioSaavnSong format.
     */
    fun parseGqlTrack(trackData: JSONObject, uriOverride: String? = null): JioSaavnSong? {
        val uri = uriOverride?.takeIf { it.isNotBlank() }
            ?: trackData.optString("uri").ifBlank { trackData.optString("_uri") }
        val id = uri.substringAfterLast(":")
        if (id.isBlank()) return null

        val title = trackData.optString("name", "Unknown Title")
        val artistsArr = trackData.optJSONObject("artists")?.optJSONArray("items")
        val artistsList = mutableListOf<String>()
        if (artistsArr != null) {
            for (a in 0 until artistsArr.length()) {
                val aObj = artistsArr.getJSONObject(a)
                val aName = aObj.optJSONObject("profile")?.optString("name") ?: aObj.optString("name")
                if (aName.isNotBlank()) artistsList.add(aName)
            }
        }
        val artist = artistsList.joinToString(", ").ifBlank { "Unknown Artist" }

        val albumData = trackData.optJSONObject("albumOfTrack")
        val coverSources = albumData?.optJSONObject("coverArt")?.optJSONArray("sources")
        val cover = coverSources?.optJSONObject(0)?.optString("url") ?: ""

        return JioSaavnSong(
            id = "spotify_$id",
            name = title,
            artist = artist,
            imageUrl = cover,
            downloadUrl = ""
        )
    }

    /**
     * Reads cached Spotify liked songs from local storage.
     */
    fun getCachedLikedSongs(context: Context): List<JioSaavnSong> {
        val cachePrefs = context.getSharedPreferences(LIKED_PREFS_NAME, Context.MODE_PRIVATE)
        val ids = cachePrefs.getStringSet("song_ids", emptySet()) ?: emptySet()
        if (ids.isEmpty()) return emptyList()

        return ids.mapNotNull { id ->
            val json = cachePrefs.getString(id, null) ?: return@mapNotNull null
            try {
                val obj = JSONObject(json)
                JioSaavnSong(
                    id = obj.optString("id", id),
                    name = obj.optString("name", "Unknown Title"),
                    artist = obj.optString("artist", "Unknown Artist"),
                    imageUrl = obj.optString("imageUrl", ""),
                    downloadUrl = ""
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun executeGql(token: String, operationName: String, variables: JSONObject): JSONObject? {
        val primaryHash = SpotifyHashProvider.getHash(operationName)
        val hashCandidates = listOfNotNull(
            primaryHash,
            SpotifyHashProvider.getPreviousHash(operationName)
        ).distinct()

        for (sha256Hash in hashCandidates) {
            try {
                val payload = JSONObject().apply {
                    put("variables", variables)
                    put("operationName", operationName)
                    put("extensions", JSONObject().apply {
                        put("persistedQuery", JSONObject().apply {
                            put("version", 1)
                            put("sha256Hash", sha256Hash)
                        })
                    })
                }

                val connection = URL(GQL_URL).openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = 15_000
                connection.readTimeout = 15_000
                connection.doOutput = true
                connection.setRequestProperty("Authorization", "Bearer $token")
                val clientToken = com.musicplayer.lytrix.spotify.canvas.SpotifyCanvas.getClientTokenSync()
                if (!clientToken.isNullOrBlank()) {
                    connection.setRequestProperty("Client-Token", clientToken)
                }
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")

                connection.outputStream.use { os ->
                    os.write(payload.toString().toByteArray(Charsets.UTF_8))
                }

                val responseCode = connection.responseCode
                if (responseCode in 200..299) {
                    val respText = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(respText)
                    val errors = json.optJSONArray("errors")
                    if (errors != null && errors.length() > 0) {
                        val msg = errors.getJSONObject(0).optString("message", "")
                        if (msg.contains("PersistedQueryNotFound", ignoreCase = true)) {
                            Timber.w("SpotifyRepository: PersistedQueryNotFound for $operationName with hash $sha256Hash, trying next...")
                            continue
                        }
                    }
                    return json
                } else if (responseCode == 412) {
                    continue
                } else {
                    val err = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    Timber.w("SpotifyRepository: GQL $operationName HTTP $responseCode: $err")
                }
            } catch (e: Exception) {
                Timber.e(e, "SpotifyRepository: GQL exception for $operationName: ${e.message}")
            }
        }
        return null
    }
}
