package com.musicplayer.lytrix.sync

import android.content.Context
import com.musicplayer.lytrix.innertube.YouTube
import com.musicplayer.lytrix.innertube.utils.completed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

object YoutubeSyncManager {

    suspend fun syncAll(context: Context) = withContext(Dispatchers.IO) {
        if (YouTube.cookie == null) return@withContext

        // Use EXACT Meld Logic: completed() for full pagination!
        syncCategory(context, "FEmusic_liked_videos", "online_liked_songs", "song_data_")
        syncPlaylistsOrAlbums(context, "FEmusic_liked_playlists", "online_playlists_meta", "Playlist")
        syncPlaylistsOrAlbums(context, "FEmusic_liked_albums", "online_albums_meta", "Album")
        syncPlaylistsOrAlbums(context, "FEmusic_library_non_music_audio_list", "online_podcasts_meta", "Podcast")
        syncCategory(context, "FEmusic_library_corpus_artists", "subscribed_artists_prefs", "artist_data_")
    }
    
    suspend fun clearYtmSyncData(context: Context) = withContext(Dispatchers.IO) {
        clearCategory(context, "online_liked_songs", "song_data_")
        clearPlaylistsOrAlbums(context, "online_playlists_meta")
        clearPlaylistsOrAlbums(context, "online_albums_meta")
        clearPlaylistsOrAlbums(context, "online_podcasts_meta")
        clearCategory(context, "subscribed_artists_prefs", "artist_data_")
    }

    private fun extractMetadata(item: com.musicplayer.lytrix.innertube.models.YTItem): Map<String, String>? {
        return when(item) {
            is com.musicplayer.lytrix.innertube.models.SongItem -> mapOf("id" to item.id, "title" to item.title, "subtitle" to (item.artists?.joinToString(", ") { it.name } ?: ""), "thumbnail" to (item.thumbnail ?: ""))
            is com.musicplayer.lytrix.innertube.models.PlaylistItem -> mapOf("id" to item.id, "title" to item.title, "subtitle" to (item.author?.name ?: ""), "thumbnail" to (item.thumbnail ?: ""), "songCount" to (item.songCountText?.filter { it.isDigit() } ?: "0"))
            is com.musicplayer.lytrix.innertube.models.AlbumItem -> mapOf("id" to item.browseId, "title" to item.title, "subtitle" to (item.artists?.joinToString(", ") { it.name } ?: ""), "thumbnail" to (item.thumbnail ?: ""))
            is com.musicplayer.lytrix.innertube.models.ArtistItem -> mapOf("id" to item.id, "title" to item.title, "subtitle" to "Artist", "thumbnail" to (item.thumbnail ?: ""))
            is com.musicplayer.lytrix.innertube.models.PodcastItem -> mapOf("id" to item.id, "title" to item.title, "subtitle" to (item.author?.name ?: "Podcast"), "thumbnail" to (item.thumbnail ?: ""), "songCount" to (item.episodeCountText?.filter { it.isDigit() } ?: "0"))
            is com.musicplayer.lytrix.innertube.models.EpisodeItem -> mapOf("id" to item.id, "title" to item.title, "subtitle" to (item.podcast?.name ?: item.author?.name ?: "Episode"), "thumbnail" to (item.thumbnail ?: ""))
            else -> null
        }
    }

    private fun clearCategory(context: Context, prefsName: String, prefix: String) {
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        val allEntries = prefs.all
        val oldYtmIds = mutableSetOf<String>()
        
        for ((key, value) in allEntries) {
            if (key.startsWith(prefix) && value is String) {
                try {
                    val json = JSONObject(value)
                    if (json.optBoolean("isYtmSync", false)) {
                        val id = key.removePrefix(prefix)
                        oldYtmIds.add(id)
                        editor.remove(key)
                    }
                } catch (e: Exception) {}
            }
        }
        val keySetId = if (prefsName == "subscribed_artists_prefs") "subscribed_ids" else "liked_ids"
        val oldSet = prefs.getStringSet(keySetId, emptySet()) ?: emptySet()
        val newSet = mutableSetOf<String>().apply { addAll(oldSet); removeAll(oldYtmIds) }
        editor.putStringSet(keySetId, newSet)
        editor.apply()
    }

    private suspend fun syncCategory(
        context: Context,
        browseId: String,
        prefsName: String,
        prefix: String
    ) {
        // EXACT Meld Logic: completed() gets all pages!
        val response = YouTube.library(browseId).completed().getOrNull() ?: return
        val items = response.items.mapNotNull { extractMetadata(it) }

        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        
        val allEntries = prefs.all
        val oldYtmIds = mutableSetOf<String>()
        for ((key, value) in allEntries) {
            if (key.startsWith(prefix) && value is String) {
                try {
                    val json = JSONObject(value)
                    if (json.optBoolean("isYtmSync", false)) {
                        val id = key.removePrefix(prefix)
                        oldYtmIds.add(id)
                        editor.remove(key)
                    }
                } catch (e: Exception) {}
            }
        }
        
        val keySetId = if (prefsName == "subscribed_artists_prefs") "subscribed_ids" else "liked_ids"
        val oldSet = prefs.getStringSet(keySetId, emptySet()) ?: emptySet()
        val newSet = mutableSetOf<String>().apply { addAll(oldSet); removeAll(oldYtmIds) }

        items.forEach { track ->
            val json = JSONObject().apply {
                put("id", track["id"])
                put("originalId", track["id"])
                put("title", track["title"])
                put("name", track["title"])
                put("subtitle", track["subtitle"])
                put("artist", track["subtitle"])
                put("primaryArtists", track["subtitle"])
                put("thumbnail", track["thumbnail"])
                put("image", track["thumbnail"])
                put("imageUrl", track["thumbnail"])
                put("url", "")
                put("downloadUrl", "")
                put("isYtmSync", true)
            }
            editor.putString("$prefix${track["id"]}", json.toString())
            newSet.add(track["id"]!!)
        }

        editor.putStringSet(keySetId, newSet)
        editor.apply()
    }

    private fun clearPlaylistsOrAlbums(context: Context, metaPrefsName: String) {
        val metaPrefs = context.getSharedPreferences(metaPrefsName, Context.MODE_PRIVATE)
        val coverPrefs = context.getSharedPreferences("playlist_covers", Context.MODE_PRIVATE)
        val timePrefs = context.getSharedPreferences("playlist_time", Context.MODE_PRIVATE)
        val sourcePrefs = context.getSharedPreferences("playlist_source", Context.MODE_PRIVATE)
        val idPrefs = context.getSharedPreferences("playlist_ids", Context.MODE_PRIVATE)
        
        val names = metaPrefs.getStringSet("names", emptySet()) ?: emptySet()
        val toRemove = names.filter { name -> sourcePrefs.getString(name, "")?.contains("YouTube Sync") == true }
        
        if (toRemove.isNotEmpty()) {
            val metaEditor = metaPrefs.edit()
            val coverEditor = coverPrefs.edit()
            val timeEditor = timePrefs.edit()
            val sourceEditor = sourcePrefs.edit()
            val idEditor = idPrefs.edit()
            
            val newNames = mutableSetOf<String>().apply { addAll(names); removeAll(toRemove) }
            metaEditor.putStringSet("names", newNames)
            
            toRemove.forEach { name ->
                coverEditor.remove(name)
                timeEditor.remove(name)
                sourceEditor.remove(name)
                idEditor.remove(name)
                metaEditor.remove("count_$name")
                val prefix = if (metaPrefsName == "online_albums_meta") "online_album_" else if (metaPrefsName == "online_podcasts_meta") "online_podcast_" else "online_pl_"
                context.getSharedPreferences("$prefix$name", Context.MODE_PRIVATE).edit().clear().apply()
            }
            
            metaEditor.apply()
            coverEditor.apply()
            timeEditor.apply()
            sourceEditor.apply()
            idEditor.apply()
        }
    }

    private suspend fun syncPlaylistsOrAlbums(context: Context, browseId: String, metaPrefsName: String, sourceType: String) {
        val response = com.musicplayer.lytrix.innertube.YouTube.library(browseId).completed().getOrNull() ?: return
        val items = response.items.mapNotNull { extractMetadata(it) }
        
        clearPlaylistsOrAlbums(context, metaPrefsName)
        
        val metaPrefs = context.getSharedPreferences(metaPrefsName, Context.MODE_PRIVATE)
        val coverPrefs = context.getSharedPreferences("playlist_covers", Context.MODE_PRIVATE)
        val timePrefs = context.getSharedPreferences("playlist_time", Context.MODE_PRIVATE)
        val sourcePrefs = context.getSharedPreferences("playlist_source", Context.MODE_PRIVATE)
        val idPrefs = context.getSharedPreferences("playlist_ids", Context.MODE_PRIVATE)
        
        val metaEditor = metaPrefs.edit()
        val coverEditor = coverPrefs.edit()
        val timeEditor = timePrefs.edit()
        val sourceEditor = sourcePrefs.edit()
        val idEditor = idPrefs.edit()
        
        val names = metaPrefs.getStringSet("names", emptySet())?.toMutableSet() ?: mutableSetOf()
        
        items.forEach { track ->
            val name = track["title"]!! 
            val id = track["id"]!!
            if (!names.contains(name)) {
                names.add(name)
                coverEditor.putString(name, track["thumbnail"])
                timeEditor.putLong(name, System.currentTimeMillis())
                sourceEditor.putString(name, "$sourceType • YouTube Sync")
                idEditor.putString(name, id)
                
                var finalCount = 0
                try {
                    val songs = when {
                        sourceType == "Album" -> com.musicplayer.lytrix.innertube.YouTube.album(id).getOrNull()?.songs
                        sourceType == "Podcast" -> {
                            val podPage = com.musicplayer.lytrix.innertube.YouTube.podcast(id).getOrNull()
                            if (podPage != null && podPage.episodes.isNotEmpty()) {
                                podPage.episodes.map { ep ->
                                    com.musicplayer.lytrix.innertube.models.SongItem(
                                        id = ep.id,
                                        title = ep.title,
                                        artists = listOfNotNull(ep.author ?: ep.podcast?.let { com.musicplayer.lytrix.innertube.models.Artist(it.name, it.id) }),
                                        album = null,
                                        duration = ep.duration,
                                        thumbnail = ep.thumbnail
                                    )
                                }
                            } else {
                                com.musicplayer.lytrix.innertube.YouTube.playlist(id).completed().getOrNull()?.songs
                            }
                        }
                        else -> com.musicplayer.lytrix.innertube.YouTube.playlist(id).completed().getOrNull()?.songs
                    }?.mapNotNull { item ->
                        val jsonObj = org.json.JSONObject()
                        jsonObj.put("id", item.id)
                        jsonObj.put("name", item.title)
                        jsonObj.put("artist", item.artists?.joinToString(", ") { it.name } ?: "")
                        jsonObj.put("imageUrl", item.thumbnail ?: track["thumbnail"])
                        jsonObj.put("downloadUrl", "")
                        jsonObj.put("setVideoId", item.setVideoId ?: "")
                        jsonObj.put("isYtmSync", true)
                        jsonObj.toString()
                    }
                    if (songs != null) {
                        finalCount = songs.size
                        val prefix = if (sourceType == "Album") "online_album_" else if (sourceType == "Podcast") "online_podcast_" else "online_pl_"
                        val plSongsPrefs = context.getSharedPreferences("$prefix$name", Context.MODE_PRIVATE)
                        val plEditor = plSongsPrefs.edit()
                        plEditor.clear()
                        songs.forEach { songJson ->
                            val parsed = org.json.JSONObject(songJson)
                            plEditor.putString("song_${parsed.getString("id")}", songJson)
                        }
                        plEditor.apply()
                    }
                } catch (e: Exception) {}
                
                metaEditor.putInt("count_$name", finalCount)
            }
        }
        
        metaEditor.putStringSet("names", names)
        metaEditor.apply()
        coverEditor.apply()
        timeEditor.apply()
        sourceEditor.apply()
        idEditor.apply()
    }
}
