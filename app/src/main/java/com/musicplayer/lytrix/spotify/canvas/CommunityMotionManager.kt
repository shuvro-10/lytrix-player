package com.musicplayer.lytrix.spotify.canvas

import android.content.Context
import android.util.Log
import com.musicplayer.lytrix.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object CommunityMotionManager {
    private const val TAG = "CommunityMotionManager"

    // Default GitHub repository URLs for the community database
    const val DEFAULT_CUSTOM_MOTIONS_URL = "https://raw.githubusercontent.com/shuvro-10/lytrix-motions/main/custom_motions.json"
    const val DEFAULT_ARTISTS_URL = "https://raw.githubusercontent.com/shuvro-10/lytrix-motions/main/artists.json"

    private const val PREFS_NAME = "community_motion_prefs"
    private const val KEY_CUSTOM_MOTIONS_URL = "custom_motions_repo_url"
    private const val KEY_CUSTOM_ARTISTS_URL = "custom_artists_repo_url"

    private const val CACHE_MOTIONS_FILE = "cached_custom_motions.json"
    private const val CACHE_ARTISTS_FILE = "cached_community_artists.json"
    private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L // 24 hours

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    data class MotionSongItem(
        val id: String?,
        val title: String?,
        val artist: String?,
        val videoUrl: String,
        val normTitle: String,
        val normArtist: String
    )

    data class MotionArtistItem(
        val name: String,
        val aliases: List<String>,
        val videoUrl: String,
        val normName: String
    )

    private val inMemorySongCache = ConcurrentHashMap<String, String>()
    private val inMemoryArtistCache = ConcurrentHashMap<String, String>()

    private var cachedSongItems: List<MotionSongItem>? = null
    private var cachedArtistItems: List<MotionArtistItem>? = null

    private val fetchMutex = Mutex()

    private val MASHUP_REGEX = Pattern.compile(
        "\\b(?:mashup|medley|jukebox|nonstop|megamix|remix|dj\\s+mix)\\b|[\\/xX&]\\s*[A-Za-z0-9]",
        Pattern.CASE_INSENSITIVE
    )

    private val CLEAN_PARENS_REGEX = Pattern.compile("\\(.*?\\b(?:official|video|audio|lyrics|feat|ft\\.|remix)\\b.*?\\)", Pattern.CASE_INSENSITIVE)
    private val CLEAN_BRACKETS_REGEX = Pattern.compile("\\[.*?\\b(?:official|video|audio|lyrics|feat|ft\\.|remix)\\b.*?\\]", Pattern.CASE_INSENSITIVE)
    private val CLEAN_FEAT_REGEX = Pattern.compile("\\b(?:feat|ft)\\.?\\s+.*", Pattern.CASE_INSENSITIVE)

    fun cleanText(text: String): String {
        var s = CLEAN_PARENS_REGEX.matcher(text).replaceAll("")
        s = CLEAN_BRACKETS_REGEX.matcher(s).replaceAll("")
        s = CLEAN_FEAT_REGEX.matcher(s).replaceAll("")
        return s.trim()
    }

    private fun normalize(text: String): String {
        val cleaned = cleanText(text).lowercase(Locale.ROOT)
        return cleaned.replace(Regex("[^a-z0-9\\s]"), " ").replace(Regex("\\s+"), " ").trim()
    }

    fun isMashupOrCollab(title: String): Boolean {
        return MASHUP_REGEX.matcher(title).find() || title.contains(" / ") || title.contains(" x ") || title.contains(" X ")
    }

    fun getMotionsRepoUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_CUSTOM_MOTIONS_URL, DEFAULT_CUSTOM_MOTIONS_URL)?.ifBlank { DEFAULT_CUSTOM_MOTIONS_URL }
            ?: DEFAULT_CUSTOM_MOTIONS_URL
    }

    fun setMotionsRepoUrl(context: Context, url: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CUSTOM_MOTIONS_URL, url.trim())
            .apply()
        clearCache()
    }

    fun getArtistsRepoUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_CUSTOM_ARTISTS_URL, DEFAULT_ARTISTS_URL)?.ifBlank { DEFAULT_ARTISTS_URL }
            ?: DEFAULT_ARTISTS_URL
    }

    fun setArtistsRepoUrl(context: Context, url: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CUSTOM_ARTISTS_URL, url.trim())
            .apply()
        clearCache()
    }

    suspend fun getSongMotionUrl(context: Context, song: Song): String? = withContext(Dispatchers.IO) {
        val songId = song.id.toString()
        val originalId = song.originalId ?: ""
        val cacheKey = "song_${songId}_${song.title}_${song.artist}"

        inMemorySongCache[cacheKey]?.let {
            return@withContext if (it.isEmpty()) null else it
        }

        ensureItemsLoaded(context)
        val items = cachedSongItems
        if (items.isNullOrEmpty()) return@withContext null

        val songTitle = song.title.trim()
        val songArtist = song.artist.trim()
        val normTitle = normalize(songTitle)
        val normArtist = normalize(songArtist)
        val isMashup = isMashupOrCollab(songTitle)

        // 🔥 STEP 1: Strict ID Matching
        // If an item has an `id`, ONLY match if `item.id == songId || item.id == originalId`.
        // "আমি চাই আইডি থাকলে টাইটেল কাজ না করুক।" -> Title is COMPLETELY IGNORED if ID is present!
        for (item in items) {
            if (!item.id.isNullOrBlank()) {
                if (item.id.equals(songId, ignoreCase = true) || (originalId.isNotBlank() && item.id.equals(originalId, ignoreCase = true))) {
                    Log.i(TAG, "[CommunityMotion] ID MATCH -> '${song.title}' ($songId) | Video: ${item.videoUrl}")
                    inMemorySongCache[cacheKey] = item.videoUrl
                    return@withContext item.videoUrl
                }
            }
        }

        // 🔥 STEP 2: Title-Only Matching (ONLY for items where `id` is null or blank)
        // If the current song is a mashup, STRICTLY SKIP title matching to avoid contamination!
        if (!isMashup) {
            for (item in items) {
                if (item.id.isNullOrBlank() && item.normTitle.isNotBlank()) {
                    if (item.normTitle == normTitle) {
                        val artistMatches = item.normArtist.isBlank() ||
                                normArtist.contains(item.normArtist) ||
                                item.normArtist.contains(normArtist)

                        if (artistMatches) {
                            Log.i(TAG, "[CommunityMotion] TITLE MATCH -> '${song.title}' by '${song.artist}' | Video: ${item.videoUrl}")
                            inMemorySongCache[cacheKey] = item.videoUrl
                            return@withContext item.videoUrl
                        }
                    }
                }
            }
        }

        inMemorySongCache[cacheKey] = ""
        null
    }

    suspend fun getArtistMotionUrl(context: Context, rawArtistName: String): String? = withContext(Dispatchers.IO) {
        val cleanName = AppleMusicAnimatedArtwork.cleanArtistName(rawArtistName).trim()
        if (cleanName.isBlank()) return@withContext null

        // Block "Various Artists" and multi-artist mashups
        if (cleanName.contains("Various Artists", ignoreCase = true) ||
            cleanName.contains("Various", ignoreCase = true) ||
            cleanName.contains("Mashup", ignoreCase = true)) {
            return@withContext null
        }

        val cacheKey = "art_${cleanName.lowercase(Locale.ROOT)}"
        inMemoryArtistCache[cacheKey]?.let {
            return@withContext if (it.isEmpty()) null else it
        }

        ensureItemsLoaded(context)
        val items = cachedArtistItems
        if (items.isNullOrEmpty()) return@withContext null

        val normName = normalize(cleanName)

        for (item in items) {
            if (item.normName == normName || item.aliases.any { normalize(it) == normName }) {
                Log.i(TAG, "[CommunityMotion] ARTIST MATCH -> '$cleanName' | Video: ${item.videoUrl}")
                inMemoryArtistCache[cacheKey] = item.videoUrl
                return@withContext item.videoUrl
            }
        }

        inMemoryArtistCache[cacheKey] = ""
        null
    }

    private suspend fun ensureItemsLoaded(context: Context) {
        if (cachedSongItems != null && cachedArtistItems != null) return

        fetchMutex.withLock {
            if (cachedSongItems != null && cachedArtistItems != null) return

            val motionsCacheFile = File(context.cacheDir, CACHE_MOTIONS_FILE)
            val artistsCacheFile = File(context.cacheDir, CACHE_ARTISTS_FILE)

            // 1. Try local cache
            if (cachedSongItems == null && motionsCacheFile.exists()) {
                try {
                    cachedSongItems = parseCustomMotionsJson(motionsCacheFile.readText())
                } catch (e: Exception) {
                    Log.w(TAG, "Error reading cached motions: ${e.message}")
                }
            }
            if (cachedArtistItems == null && artistsCacheFile.exists()) {
                try {
                    cachedArtistItems = parseArtistsJson(artistsCacheFile.readText())
                } catch (e: Exception) {
                    Log.w(TAG, "Error reading cached artists: ${e.message}")
                }
            }

            // If empty or expired, fetch from remote
            val motionsExpired = !motionsCacheFile.exists() || (System.currentTimeMillis() - motionsCacheFile.lastModified() > CACHE_TTL_MS)
            val artistsExpired = !artistsCacheFile.exists() || (System.currentTimeMillis() - artistsCacheFile.lastModified() > CACHE_TTL_MS)

            if (cachedSongItems == null || motionsExpired) {
                fetchMotionsRemote(context, motionsCacheFile)
            }
            if (cachedArtistItems == null || artistsExpired) {
                fetchArtistsRemote(context, artistsCacheFile)
            }
        }
    }

    private fun fetchMotionsRemote(context: Context, cacheFile: File) {
        val url = getMotionsRepoUrl(context)
        try {
            val req = Request.Builder().url(url).header("User-Agent", "Lytrix/1.0").build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string()
                    if (!body.isNullOrBlank()) {
                        val parsed = parseCustomMotionsJson(body)
                        if (parsed.isNotEmpty()) {
                            cachedSongItems = parsed
                            cacheFile.writeText(body)
                            Log.i(TAG, "Fetched and cached ${parsed.size} custom motions from $url")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch custom motions from $url: ${e.message}")
        }
    }

    private fun fetchArtistsRemote(context: Context, cacheFile: File) {
        val url = getArtistsRepoUrl(context)
        try {
            val req = Request.Builder().url(url).header("User-Agent", "Lytrix/1.0").build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string()
                    if (!body.isNullOrBlank()) {
                        val parsed = parseArtistsJson(body)
                        if (parsed.isNotEmpty()) {
                            cachedArtistItems = parsed
                            cacheFile.writeText(body)
                            Log.i(TAG, "Fetched and cached ${parsed.size} community artists from $url")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch community artists from $url: ${e.message}")
        }
    }

    private fun parseCustomMotionsJson(jsonStr: String): List<MotionSongItem> {
        val list = mutableListOf<MotionSongItem>()
        try {
            val arr = if (jsonStr.trim().startsWith("[")) {
                JSONArray(jsonStr)
            } else {
                JSONObject(jsonStr).optJSONArray("items") ?: JSONArray()
            }

            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optString("id", "").trim().ifBlank { null }
                val title = obj.optString("title", "").trim().ifBlank { null }
                val artist = obj.optString("artist", "").trim().ifBlank { null }
                val video = obj.optString("video", "").trim()

                if (video.isNotBlank() && (id != null || title != null)) {
                    list.add(
                        MotionSongItem(
                            id = id,
                            title = title,
                            artist = artist,
                            videoUrl = ViviMusicCanvas.sanitizeUrl(video),
                            normTitle = title?.let { normalize(it) } ?: "",
                            normArtist = artist?.let { normalize(it) } ?: ""
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing custom motions JSON: ${e.message}")
        }
        return list
    }

    private fun parseArtistsJson(jsonStr: String): List<MotionArtistItem> {
        val list = mutableListOf<MotionArtistItem>()
        try {
            val arr = if (jsonStr.trim().startsWith("[")) {
                JSONArray(jsonStr)
            } else {
                JSONObject(jsonStr).optJSONArray("items") ?: JSONArray()
            }

            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val name = obj.optString("name", "").trim()
                val video = obj.optString("video", "").trim()
                val aliases = mutableListOf<String>()
                obj.optJSONArray("aliases")?.let { aliasArr ->
                    for (j in 0 until aliasArr.length()) {
                        val al = aliasArr.optString(j, "").trim()
                        if (al.isNotBlank()) aliases.add(al)
                    }
                }

                if (name.isNotBlank() && video.isNotBlank()) {
                    list.add(
                        MotionArtistItem(
                            name = name,
                            aliases = aliases,
                            videoUrl = ViviMusicCanvas.sanitizeUrl(video),
                            normName = normalize(name)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing artists JSON: ${e.message}")
        }
        return list
    }

    fun clearCache() {
        inMemorySongCache.clear()
        inMemoryArtistCache.clear()
        cachedSongItems = null
        cachedArtistItems = null
    }
}
