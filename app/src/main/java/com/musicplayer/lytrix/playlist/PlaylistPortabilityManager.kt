package com.musicplayer.lytrix.playlist

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.musicplayer.lytrix.db.AppDatabase
import com.musicplayer.lytrix.db.PlaylistEntity
import com.musicplayer.lytrix.db.PlaylistSongCrossRef
import com.musicplayer.lytrix.model.Song
import com.musicplayer.lytrix.ui.extractYouTubeVideoId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

enum class ExportFormat(val extension: String, val mimeType: String, val label: String) {
    CSV("csv", "text/csv", "CSV Spreadsheet (.csv)"),
    M3U("m3u", "audio/x-mpegurl", "M3U Playlist (.m3u)"),
    M3U8("m3u8", "application/vnd.apple.mpegurl", "Extended M3U8 (.m3u8)")
}

enum class PlaylistKind {
    LOCAL_LIKED,
    LOCAL_CUSTOM,
    ONLINE_PLAYLIST
}

data class ExportablePlaylistOption(
    val id: String,
    val name: String,
    val kind: PlaylistKind,
    val trackCount: Int
)

data class PortableTrack(
    val title: String,
    val artist: String,
    val album: String = "",
    val durationSec: Int = 0,
    val youtubeId: String = "",
    val location: String = "",
    val imageUrl: String = ""
)

data class PlaylistImportSummary(
    val playlistName: String,
    val totalEntries: Int,
    val matchedLocalCount: Int,
    val importedOnlineCount: Int
)

/**
 * Meld-style playlist import/export supporting CSV, M3U, and M3U8 formats across both
 * Lytrix local Room playlists and YouTube/Online saved playlists.
 */
object PlaylistPortabilityManager {

    suspend fun listExportablePlaylists(context: Context): List<ExportablePlaylistOption> =
        withContext(Dispatchers.IO) {
            val result = mutableListOf<ExportablePlaylistOption>()
            val db = AppDatabase.getDatabase(context)

            val likedIds = db.songMetadataDao().getAllFavoriteSongIds().first()
            if (likedIds.isNotEmpty()) {
                result.add(ExportablePlaylistOption("liked", "Local Liked Songs", PlaylistKind.LOCAL_LIKED, likedIds.size))
            }

            val localPlaylists = db.playlistDao().getAllPlaylists().first()
            localPlaylists.forEach { pl ->
                result.add(ExportablePlaylistOption(pl.id.toString(), pl.name, PlaylistKind.LOCAL_CUSTOM, pl.songCount))
            }

            val plMeta = context.getSharedPreferences("online_playlists_meta", Context.MODE_PRIVATE)
            val onlineNames = (plMeta.getStringSet("names", emptySet()) ?: emptySet()).sorted()
            onlineNames.forEach { name ->
                val sp = context.getSharedPreferences("online_pl_$name", Context.MODE_PRIVATE)
                val count = sp.all.size
                if (count > 0) {
                    result.add(ExportablePlaylistOption(name, "$name (Online)", PlaylistKind.ONLINE_PLAYLIST, count))
                }
            }
            result
        }

    suspend fun loadTracksForOption(context: Context, option: ExportablePlaylistOption): List<PortableTrack> =
        withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            when (option.kind) {
                PlaylistKind.LOCAL_LIKED -> {
                    val ids = db.songMetadataDao().getAllFavoriteSongIds().first()
                    resolveLocalSongsByIds(context, ids)
                }

                PlaylistKind.LOCAL_CUSTOM -> {
                    val plId = option.id.toLongOrNull() ?: return@withContext emptyList()
                    val ids = db.playlistDao().getPlaylistCrossRefs(plId).first().map { it.songId }
                    resolveLocalSongsByIds(context, ids)
                }

                PlaylistKind.ONLINE_PLAYLIST -> {
                    val sp = context.getSharedPreferences("online_pl_${option.id}", Context.MODE_PRIVATE)
                    sp.all.values.mapNotNull { raw ->
                        runCatching {
                            val obj = JSONObject(raw.toString())
                            val id = obj.optString("id", obj.optString("videoId", ""))
                            val title = obj.optString("name", obj.optString("title", "Unknown Track"))
                            val artist = obj.optString("artist", "Unknown Artist")
                            val dur = (obj.optLong("duration", 0L) / 1000L).toInt().coerceAtLeast(0)
                            val url = if (id.length == 11 && !id.startsWith("http")) {
                                "https://music.youtube.com/watch?v=$id"
                            } else {
                                obj.optString("uriString", "")
                            }
                            PortableTrack(
                                title = title,
                                artist = artist,
                                album = obj.optString("album", ""),
                                durationSec = dur,
                                youtubeId = id,
                                location = url,
                                imageUrl = obj.optString("imageUrl", "")
                            )
                        }.getOrNull()
                    }
                }
            }
        }

    fun formatSongs(songs: List<Song>, format: ExportFormat): String {
        val tracks = songs.map { song ->
            PortableTrack(
                title = song.title,
                artist = song.artist,
                album = song.album,
                durationSec = (song.duration / 1000L).toInt().coerceAtLeast(0),
                youtubeId = "",
                location = song.filePath ?: song.uriString
            )
        }
        return formatTracks(tracks, format)
    }

    fun formatTracks(tracks: List<PortableTrack>, format: ExportFormat): String {
        if (format == ExportFormat.CSV) {
            val sb = StringBuilder()
            sb.appendLine("Title,Artist,Album,DurationSec,YouTubeId,Location")
            tracks.forEach { t ->
                sb.appendLine(
                    listOf(
                        escapeCsv(t.title),
                        escapeCsv(t.artist),
                        escapeCsv(t.album),
                        t.durationSec.toString(),
                        escapeCsv(t.youtubeId),
                        escapeCsv(t.location)
                    ).joinToString(",")
                )
            }
            return sb.toString()
        }

        val sb = StringBuilder()
        sb.appendLine("#EXTM3U")
        tracks.forEach { t ->
            val sec = if (t.durationSec > 0) t.durationSec else -1
            val label = if (t.artist.isNotBlank()) "${t.artist} - ${t.title}" else t.title
            sb.appendLine("#EXTINF:$sec,$label")
            if (t.youtubeId.isNotBlank()) {
                sb.appendLine("#YTM:${t.youtubeId}")
            }
            val lineTarget = t.location.ifBlank {
                if (t.youtubeId.isNotBlank()) "https://music.youtube.com/watch?v=${t.youtubeId}" else t.title
            }
            sb.appendLine(lineTarget)
        }
        return sb.toString()
    }

    suspend fun exportPlaylistToUri(
        context: Context,
        uri: Uri,
        tracks: List<PortableTrack>,
        format: ExportFormat
    ): Int = withContext(Dispatchers.IO) {
        val serialized = formatTracks(tracks, format)
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.write(serialized.toByteArray(Charsets.UTF_8))
            stream.flush()
        } ?: error("Cannot write destination file")
        tracks.size
    }

    suspend fun importPlaylistFromUri(
        context: Context,
        uri: Uri,
        overridePlaylistName: String? = null
    ): PlaylistImportSummary = withContext(Dispatchers.IO) {
        val rawName = overridePlaylistName?.takeIf { it.isNotBlank() }
            ?: resolveDisplayName(context, uri)
                .substringBeforeLast('.')
                .ifBlank { "Imported Playlist" }

        val content = context.contentResolver.openInputStream(uri)?.use { stream ->
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).readText()
        } ?: error("Cannot read selected file")

        val parsed = if (isM3uContent(rawName, content)) {
            parseM3u(content)
        } else {
            parseCsv(content)
        }
        if (parsed.isEmpty()) {
            error("No valid tracks found in file")
        }

        val localLibrary = queryAllLocalAudio(context)
        val matchedLocalIds = mutableListOf<Long>()
        val onlineTracks = mutableListOf<PortableTrack>()

        parsed.forEach { track ->
            val match = findMatchingLocalSong(localLibrary, track)
            if (match != null) {
                if (match.id !in matchedLocalIds) matchedLocalIds.add(match.id)
            } else {
                val ytId = track.youtubeId.ifBlank {
                    extractYouTubeVideoId(track.location) ?: ""
                }
                if (ytId.isNotBlank() || track.title.isNotBlank()) {
                    onlineTracks.add(track.copy(youtubeId = ytId))
                }
            }
        }

        if (matchedLocalIds.isNotEmpty()) {
            val dao = AppDatabase.getDatabase(context).playlistDao()
            val newPlId = dao.insertPlaylist(
                PlaylistEntity(name = rawName, songCount = matchedLocalIds.size)
            )
            matchedLocalIds.forEachIndexed { index, songId ->
                dao.insertPlaylistSongCrossRef(
                    PlaylistSongCrossRef(
                        playlistId = newPlId,
                        songId = songId,
                        orderIndex = index
                    )
                )
            }
        }

        val unresolvedOnline = onlineTracks.filter { it.youtubeId.isBlank() && it.title.isNotBlank() }
        val resolvedFromSearch = mutableListOf<PortableTrack>()
        for (t in unresolvedOnline.take(40)) {
            val query = "${t.title} ${t.artist}".trim()
            val matchItem = runCatching {
                com.musicplayer.lytrix.innertube.YouTube.search(
                    query,
                    com.musicplayer.lytrix.innertube.YouTube.SearchFilter.FILTER_SONG
                ).getOrNull()?.items?.firstOrNull() as? com.musicplayer.lytrix.innertube.models.SongItem
            }.getOrNull()
            if (matchItem != null && matchItem.id.isNotBlank()) {
                resolvedFromSearch.add(
                    t.copy(
                        youtubeId = matchItem.id,
                        title = t.title.ifBlank { matchItem.title },
                        artist = t.artist.ifBlank { matchItem.artists.joinToString(", ") { it.name } },
                        durationSec = if (t.durationSec > 0) t.durationSec else (matchItem.duration ?: 0),
                        imageUrl = t.imageUrl.ifBlank { matchItem.thumbnail }
                    )
                )
            }
        }

        val ytImportable = (onlineTracks.filter { it.youtubeId.isNotBlank() } + resolvedFromSearch)
            .distinctBy { it.youtubeId }
        if (ytImportable.isNotEmpty()) {
            val onlinePlName = if (matchedLocalIds.isNotEmpty()) "$rawName (Online)" else rawName
            val meta = context.getSharedPreferences("online_playlists_meta", Context.MODE_PRIVATE)
            val existingNames = (meta.getStringSet("names", emptySet()) ?: emptySet()).toMutableSet()
            existingNames.add(onlinePlName)
            meta.edit()
                .putStringSet("names", existingNames)
                .putInt("count_$onlinePlName", ytImportable.size)
                .apply()

            context.getSharedPreferences("playlist_source", Context.MODE_PRIVATE)
                .edit().putString(onlinePlName, "Imported").apply()
            context.getSharedPreferences("playlist_time", Context.MODE_PRIVATE)
                .edit().putLong(onlinePlName, System.currentTimeMillis()).apply()

            val songsPref = context.getSharedPreferences("online_pl_$onlinePlName", Context.MODE_PRIVATE)
            val editor = songsPref.edit()
            ytImportable.forEach { t ->
                val obj = JSONObject().apply {
                    put("id", t.youtubeId)
                    put("name", t.title.ifBlank { "YouTube Track ${t.youtubeId}" })
                    put("artist", t.artist.ifBlank { "Unknown Artist" })
                    put("imageUrl", t.imageUrl.ifBlank { "https://i.ytimg.com/vi/${t.youtubeId}/hqdefault.jpg" })
                    put("duration", t.durationSec * 1000L)
                }
                editor.putString(t.youtubeId, obj.toString())
            }
            editor.apply()
        }

        PlaylistImportSummary(
            playlistName = rawName,
            totalEntries = parsed.size,
            matchedLocalCount = matchedLocalIds.size,
            importedOnlineCount = ytImportable.size
        )
    }

    private fun isM3uContent(name: String, content: String): Boolean {
        val trimmed = content.trimStart()
        return trimmed.startsWith("#EXTM3U", ignoreCase = true) ||
            trimmed.startsWith("#EXTINF", ignoreCase = true) ||
            name.endsWith(".m3u", ignoreCase = true) ||
            name.endsWith(".m3u8", ignoreCase = true)
    }

    private fun parseM3u(content: String): List<PortableTrack> {
        val tracks = mutableListOf<PortableTrack>()
        var pendingTitle = ""
        var pendingArtist = ""
        var pendingDurSec = 0
        var pendingYtId = ""

        content.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isBlank()) return@forEach
            when {
                line.startsWith("#EXTM3U", ignoreCase = true) -> Unit
                line.startsWith("#EXTINF:", ignoreCase = true) -> {
                    val body = line.substringAfter(":", "")
                    val commaIdx = body.indexOf(',')
                    val secPart = if (commaIdx >= 0) body.substring(0, commaIdx) else body
                    pendingDurSec = secPart.trim().toIntOrNull()?.coerceAtLeast(0) ?: 0
                    val display = if (commaIdx >= 0) body.substring(commaIdx + 1).trim() else ""
                    val dashSplit = display.split(" - ", limit = 2)
                    if (dashSplit.size == 2) {
                        pendingArtist = dashSplit[0].trim()
                        pendingTitle = dashSplit[1].trim()
                    } else {
                        pendingTitle = display
                    }
                }
                line.startsWith("#YTM:", ignoreCase = true) -> {
                    pendingYtId = line.substringAfter(":", "").trim()
                }
                line.startsWith("#") -> Unit
                else -> {
                    val yt = pendingYtId.ifBlank { extractYouTubeVideoId(line) ?: "" }
                    val fallbackTitle = pendingTitle.ifBlank {
                        line.substringAfterLast('/').substringBeforeLast('.')
                    }
                    tracks.add(
                        PortableTrack(
                            title = fallbackTitle,
                            artist = pendingArtist,
                            durationSec = pendingDurSec,
                            youtubeId = yt,
                            location = line
                        )
                    )
                    pendingTitle = ""
                    pendingArtist = ""
                    pendingDurSec = 0
                    pendingYtId = ""
                }
            }
        }
        return tracks
    }

    private fun parseCsv(content: String): List<PortableTrack> {
        val lines = content.lineSequence()
            .map { it.trimEnd('\r') }
            .filter { it.isNotBlank() }
            .toList()
        if (lines.isEmpty()) return emptyList()

        val firstCols = splitCsvLine(lines.first()).map { it.trim().lowercase() }
        val hasHeader = firstCols.any {
            it in setOf("title", "track", "track name", "name", "song", "artist", "youtubeid", "youtube_id", "video_id")
        }

        val titleIdx = firstCols.indexOfFirst { it in setOf("title", "track", "track name", "name", "song") }.let { if (it >= 0) it else 0 }
        val artistIdx = firstCols.indexOfFirst { it in setOf("artist", "artists", "artist name", "artist(s)") }.let { if (it >= 0) it else 1 }
        val albumIdx = firstCols.indexOfFirst { it in setOf("album", "album name") }.let { if (it >= 0) it else 2 }
        val durIdx = firstCols.indexOfFirst { it in setOf("durationsec", "duration", "duration_sec") }
        val ytIdx = firstCols.indexOfFirst { it in setOf("youtubeid", "youtube_id", "videoid", "video_id", "id") }
        val locIdx = firstCols.indexOfFirst { it in setOf("location", "filepath", "path", "url", "uri") }

        val dataRows = if (hasHeader) lines.drop(1) else lines
        return dataRows.mapNotNull { row ->
            val cols = splitCsvLine(row)
            if (cols.isEmpty()) return@mapNotNull null
            val title = cols.getOrNull(titleIdx)?.trim().orEmpty()
            val artist = cols.getOrNull(artistIdx)?.trim().orEmpty()
            val album = if (albumIdx in cols.indices) cols[albumIdx].trim() else ""
            val dur = if (durIdx in cols.indices) cols[durIdx].trim().toIntOrNull() ?: 0 else 0
            val loc = if (locIdx in cols.indices) cols[locIdx].trim() else ""
            val rawYt = if (ytIdx in cols.indices) cols[ytIdx].trim() else ""
            val yt = (extractYouTubeVideoId(rawYt) ?: rawYt.takeIf { it.length == 11 })
                ?: extractYouTubeVideoId(loc).orEmpty()
            if (title.isBlank() && yt.isBlank() && loc.isBlank()) null
            else PortableTrack(title = title, artist = artist, album = album, durationSec = dur, youtubeId = yt, location = loc)
        }
    }

    private fun splitCsvLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                    cur.append('"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (ch == ',' && !inQuotes) {
                out.add(cur.toString())
                cur.setLength(0)
            } else {
                cur.append(ch)
            }
            i++
        }
        out.add(cur.toString())
        return out
    }

    private fun escapeCsv(value: String): String {
        val clean = value.replace("\r", " ").replace("\n", " ")
        return if (clean.contains(',') || clean.contains('"')) {
            "\"${clean.replace("\"", "\"\"")}\""
        } else clean
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) return c.getString(idx) ?: "Playlist"
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "Playlist"
    }

    private fun findMatchingLocalSong(library: List<LocalAudioCandidate>, track: PortableTrack): LocalAudioCandidate? {
        if (track.location.isNotBlank() && !track.location.startsWith("http")) {
            library.firstOrNull { it.path.equals(track.location, ignoreCase = true) }?.let { return it }
            val fileName = track.location.substringAfterLast('/').substringAfterLast('\\')
            library.firstOrNull { it.path.substringAfterLast('/').equals(fileName, ignoreCase = true) }?.let { return it }
        }
        val normTitle = track.title.trim().lowercase()
        val normArtist = track.artist.trim().lowercase()
        if (normTitle.isBlank()) return null

        if (normArtist.isNotBlank()) {
            library.firstOrNull {
                it.title.equals(normTitle, ignoreCase = true) &&
                    it.artist.contains(normArtist, ignoreCase = true)
            }?.let { return it }
        }
        return library.firstOrNull { it.title.equals(normTitle, ignoreCase = true) }
    }

    private data class LocalAudioCandidate(
        val id: Long,
        val title: String,
        val artist: String,
        val album: String,
        val durationSec: Int,
        val path: String
    )

    private fun queryAllLocalAudio(context: Context): List<LocalAudioCandidate> {
        val list = mutableListOf<LocalAudioCandidate>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA
        )
        runCatching {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                null,
                null
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                while (c.moveToNext()) {
                    list.add(
                        LocalAudioCandidate(
                            id = c.getLong(idCol),
                            title = c.getString(titleCol) ?: "",
                            artist = c.getString(artistCol) ?: "",
                            album = c.getString(albumCol) ?: "",
                            durationSec = (c.getLong(durCol) / 1000L).toInt().coerceAtLeast(0),
                            path = c.getString(dataCol) ?: ""
                        )
                    )
                }
            }
        }
        return list
    }

    private fun resolveLocalSongsByIds(context: Context, ids: List<Long>): List<PortableTrack> {
        if (ids.isEmpty()) return emptyList()
        val byId = queryAllLocalAudio(context).associateBy { it.id }
        return ids.mapNotNull { id ->
            byId[id]?.let {
                PortableTrack(
                    title = it.title,
                    artist = it.artist,
                    album = it.album,
                    durationSec = it.durationSec,
                    location = it.path
                )
            }
        }
    }
}
