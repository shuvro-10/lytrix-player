package com.musicplayer.lytrix.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.musicplayer.lytrix.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SongScanner(private val context: Context) {

    suspend fun scanSongs(whitelistIds: List<Long> = emptyList()): List<Song> = withContext(Dispatchers.IO) {
        val songList = mutableListOf<Song>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATA
        )

        // Select all music files (is_music != 0)
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        try {
            // Scan Music first
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val dataColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val filePath = if (dataColumn != -1) cursor.getString(dataColumn) else null
                    val duration = cursor.getLong(durationColumn)
                    
                    var isFiltered = false
                    if (duration < 45000) {
                        isFiltered = true
                    }
                    if (filePath != null) {
                        val lowerPath = filePath.lowercase()
                        if (lowerPath.contains("/ringtones/") || 
                            lowerPath.contains("/alarms/") || 
                            lowerPath.contains("/notifications/") || 
                            lowerPath.contains("/voice messages/") || 
                            lowerPath.contains("/whatsapp audio/")) {
                            isFiltered = true
                        }
                    }

                    if (isFiltered && !whitelistIds.contains(id)) {
                        continue
                    }

                    val albumId = cursor.getLong(albumIdColumn)
                    var hasLyrics = false

                    val title = cursor.getString(titleColumn) ?: "Unknown Track"
                    val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                    val album = cursor.getString(albumColumn) ?: "Unknown Album"

                    val uri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                    )
                    
                    val albumArtUri = ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"),
                        albumId
                    ).toString()

                    val tempSong = Song(
                        id = id,
                        title = title,
                        artist = artist,
                        album = album,
                        duration = duration,
                        uriString = uri.toString(),
                        albumArtUriString = albumArtUri,
                        filePath = filePath
                    )
                    
                    if (com.musicplayer.lytrix.utils.LyricsFetcher.hasLyricsFast(tempSong)) {
                        hasLyrics = true
                    }

                    songList.add(tempSong.copy(hasLyrics = hasLyrics))
                }
            }

            // Scan Video (MVs)
            val videoProjection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.TITLE,
                MediaStore.Video.Media.ARTIST,
                MediaStore.Video.Media.ALBUM,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.DATA
            )
            val videoSelection = "${MediaStore.Video.Media.DURATION} >= 90000 AND ${MediaStore.Video.Media.DURATION} <= 600000" // 1.5 mins to 10 mins
            val videoSortOrder = "${MediaStore.Video.Media.TITLE} ASC"
            
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                videoProjection,
                videoSelection,
                null,
                videoSortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val dataColumn = cursor.getColumnIndex(MediaStore.Video.Media.DATA)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val filePath = if (dataColumn != -1) cursor.getString(dataColumn) else null
                    val duration = cursor.getLong(durationColumn)

                    if (filePath != null) {
                        val lowerPath = filePath.lowercase()
                        if (lowerPath.contains("/dcim/camera/") ||
                            lowerPath.contains("/screenrecord") ||
                            lowerPath.contains("/whatsapp video/") ||
                            lowerPath.contains("/movies/") ||
                            lowerPath.contains("/telegram video/")) {
                            continue
                        }
                    }
                    
                    val title = cursor.getString(titleColumn) ?: "Unknown Video"
                    // Try to get artist nicely from metadata if possible, but keep it simple
                    val artist = "Music Video"
                    val album = "Video"

                    val uri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id
                    )

                    // For Video IDs, use negative or bitmask to identify them easily if needed,
                    // but they have their own URI so Player/ExoPlayer will handle it naturally.
                    // We prepend "MV: " or just rely on the badge to show it
                    val tempSong = Song(
                        id = -id, // negative id to avoid collision with audio ID, or just use id
                        title = title,
                        artist = artist,
                        album = album,
                        duration = duration,
                        uriString = uri.toString(),
                        albumArtUriString = null,
                        filePath = filePath,
                        isVideo = true // We need to add this property to Song data class
                    )
                    
                    songList.add(tempSong)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Return local songs
        songList
    }

    suspend fun scanHiddenSongs(hiddenIds: List<Long>): List<Song> = withContext(Dispatchers.IO) {
        val songList = mutableListOf<Song>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATA
        )

        // Select all music files
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        try {
            // Scan Audio
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val dataColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val duration = cursor.getLong(durationColumn)
                    val albumId = cursor.getLong(albumIdColumn)
                    val filePath = if (dataColumn != -1) cursor.getString(dataColumn) else null
                    
                    var isFilteredBySystem = false

                    if (duration < 45000) {
                        isFilteredBySystem = true
                    }
                    if (filePath != null) {
                        val lowerPath = filePath.lowercase()
                        if (lowerPath.contains("/ringtones/") || 
                            lowerPath.contains("/alarms/") || 
                            lowerPath.contains("/notifications/") || 
                            lowerPath.contains("/voice messages/") || 
                            lowerPath.contains("/whatsapp audio/")) {
                            isFilteredBySystem = true
                        }
                    }

                    if (isFilteredBySystem || hiddenIds.contains(id)) {
                        val title = cursor.getString(titleColumn) ?: "Unknown Track"
                        val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                        val album = cursor.getString(albumColumn) ?: "Unknown Album"

                        val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                        val albumArtUri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId).toString()

                        val tempSong = Song(
                            id = id,
                            title = title,
                            artist = artist,
                            album = album,
                            duration = duration,
                            uriString = uri.toString(),
                            albumArtUriString = albumArtUri,
                            filePath = filePath
                        )
                        songList.add(tempSong)
                    }
                }
            }

            // Scan Video
            val videoProjection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.TITLE,
                MediaStore.Video.Media.ARTIST,
                MediaStore.Video.Media.ALBUM,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.DATA
            )
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                videoProjection,
                null,
                null,
                "${MediaStore.Video.Media.TITLE} ASC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val dataColumn = cursor.getColumnIndex(MediaStore.Video.Media.DATA)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val negId = -id
                    
                    // Always include if it's explicitly explicitly hidden
                    if (hiddenIds.contains(negId)) {
                        val duration = cursor.getLong(durationColumn)
                        val filePath = if (dataColumn != -1) cursor.getString(dataColumn) else null
                        val title = cursor.getString(titleColumn) ?: "Unknown Video"
                        
                        val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                        
                        val tempSong = Song(
                            id = negId,
                            title = title,
                            artist = "Music Video",
                            album = "Video",
                            duration = duration,
                            uriString = uri.toString(),
                            albumArtUriString = null,
                            filePath = filePath,
                            isVideo = true
                        )
                        songList.add(tempSong)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        songList
    }
}
