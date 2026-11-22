package com.musicplayer.lytrix.spotify.canvas

import android.content.Context
import android.util.Log
import com.musicplayer.lytrix.model.Song
import com.musicplayer.lytrix.spotify.SpotifyPreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object ViviMusicCanvas {
    private const val TAG = "ViviMusicCanvas"

    // Multi-source community database URLs
    private val REPOSITORY_SOURCES = listOf(
        // Primary massive database: 4,946 songs, 775+ Hindi/Bollywood/Punjabi
        "https://raw.githubusercontent.com/jems-trepo/Echo-Music-Canvas/main/canvas.json",
        // Secondary official repository: 187 verified songs
        "https://raw.githubusercontent.com/ViviMusicGroup/vivimusicanvas/main/canvas.json"
    )

    private const val PREFS_NAME = "vivimusic_canvas_prefs"
    private const val CACHE_FILE_NAME = "unified_community_canvas_cache.json"
    private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L // 24 hours

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    data class ViviItem(
        val song: String,
        val artist: String,
        val album: String?,
        val url: String,
        val normSong: String,
        val normArtist: String
    )

    private val inMemorySongCache = ConcurrentHashMap<String, String>()
    private var cachedItems: List<ViviItem>? = null
    private val fetchMutex = Mutex()

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
        // Remove non-alphanumeric characters, keep spaces
        return cleaned.replace(Regex("[^a-z0-9\\s]"), " ").replace(Regex("\\s+"), " ").trim()
    }

    /**
     * Converts dead / offline domain URLs (like canvas.echomusic.fun) to direct GitHub Raw CDN streams
     */
    fun sanitizeUrl(url: String): String {
        var sanitized = url.trim()
        if (sanitized.startsWith("https://canvas.echomusic.fun/")) {
            sanitized = sanitized.replace(
                "https://canvas.echomusic.fun/",
                "https://raw.githubusercontent.com/jems-trepo/Echo-Music-Canvas/main/"
            )
        }
        return sanitized
    }

    suspend fun getCanvasUrl(context: Context, song: Song): String? = withContext(Dispatchers.IO) {
        if (!SpotifyPreferencesManager.isViviCanvasEnabled(context)) {
            return@withContext null
        }

        // Ignore offline local songs
        val isLocal = song.filePath !in listOf("ONLINE_LYTRIX", "ONLINE") && !song.uriString.startsWith("http")
        if (isLocal) {
            return@withContext null
        }

        val cacheKey = "${normalize(song.title)}_${normalize(song.artist)}"
        if (cacheKey.isBlank() || cacheKey == "_") return@withContext null

        inMemorySongCache[cacheKey]?.let {
            return@withContext if (it.isEmpty()) null else it
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.contains(cacheKey)) {
            val cached = prefs.getString(cacheKey, null)
            inMemorySongCache[cacheKey] = cached ?: ""
            return@withContext cached
        }

        ensureItemsLoaded(context)

        val items = cachedItems
        if (items.isNullOrEmpty()) {
            return@withContext null
        }

        val normTitle = normalize(song.title)
        val normArtist = normalize(song.artist)
        val titleTokens = normTitle.split(" ").filter { it.length > 1 }.toSet()
        val artistTokens = normArtist.split(" ").filter { it.length > 1 }.toSet()

        var matchedUrl: String? = null

        // 1. Exact title and artist match
        for (item in items) {
            if (item.normSong == normTitle) {
                val itemArtistTokens = item.normArtist.split(" ").filter { it.length > 1 }.toSet()
                val artistMatches = normArtist.isEmpty() ||
                        item.normArtist.contains(normArtist) ||
                        normArtist.contains(item.normArtist) ||
                        artistTokens.intersect(itemArtistTokens).isNotEmpty()

                if (artistMatches) {
                    matchedUrl = item.url
                    break
                }
            }
        }

        // 2. Token overlap fallback if not found
        if (matchedUrl == null && titleTokens.size >= 2) {
            for (item in items) {
                val itemTitleTokens = item.normSong.split(" ").filter { it.length > 1 }.toSet()
                if (titleTokens.intersect(itemTitleTokens).size >= titleTokens.size) {
                    val itemArtistTokens = item.normArtist.split(" ").filter { it.length > 1 }.toSet()
                    val artistMatches = normArtist.isEmpty() ||
                            item.normArtist.contains(normArtist) ||
                            normArtist.contains(item.normArtist) ||
                            artistTokens.intersect(itemArtistTokens).isNotEmpty()
                    if (artistMatches) {
                        matchedUrl = item.url
                        break
                    }
                }
            }
        }

        if (!matchedUrl.isNullOrBlank()) {
            val finalUrl = sanitizeUrl(matchedUrl)
            Log.i(TAG, "[VisualMedia] Community Canvas MATCH -> '${song.title}' by '${song.artist}' | URL: $finalUrl")
            inMemorySongCache[cacheKey] = finalUrl
            prefs.edit().putString(cacheKey, finalUrl).apply()
            return@withContext finalUrl
        }

        // Negative hit cache
        inMemorySongCache[cacheKey] = ""
        prefs.edit().putString(cacheKey, "").apply()
        null
    }

    private suspend fun ensureItemsLoaded(context: Context) {
        if (cachedItems != null) return

        fetchMutex.withLock {
            if (cachedItems != null) return

            // 1. Try local disk cache first
            val cacheFile = File(context.cacheDir, CACHE_FILE_NAME)
            if (cacheFile.exists()) {
                try {
                    val content = cacheFile.readText()
                    val parsed = parseCanvasJson(content)
                    if (parsed.isNotEmpty()) {
                        cachedItems = parsed
                        Log.d(TAG, "Loaded ${parsed.size} unified community canvas items from local cache")
                        // If file is older than TTL, refresh in background
                        if (System.currentTimeMillis() - cacheFile.lastModified() > CACHE_TTL_MS) {
                            fetchAndMergeSources(context, cacheFile)
                        }
                        return
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error reading local community cache: ${e.message}")
                }
            }

            // 2. If no cache or invalid, fetch and merge directly
            fetchAndMergeSources(context, cacheFile)
        }
    }

    private fun fetchAndMergeSources(context: Context, cacheFile: File) {
        val mergedItems = mutableListOf<ViviItem>()
        val seenKeys = mutableSetOf<String>()

        for (sourceUrl in REPOSITORY_SOURCES) {
            try {
                val req = Request.Builder()
                    .url(sourceUrl)
                    .header("User-Agent", "Lytrix/1.0")
                    .build()

                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string()
                        if (!body.isNullOrBlank()) {
                            val parsed = parseCanvasJson(body)
                            for (item in parsed) {
                                val key = "${item.normSong}_${item.normArtist}"
                                if (seenKeys.add(key)) {
                                    mergedItems.add(item)
                                }
                            }
                            Log.i(TAG, "Fetched ${parsed.size} items from $sourceUrl (Total merged: ${mergedItems.size})")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch community source $sourceUrl: ${e.message}")
            }
        }

        if (mergedItems.isNotEmpty()) {
            cachedItems = mergedItems
            try {
                // Serialize merged list into cache file for instant startup next time
                val root = JSONObject()
                val jsonArr = org.json.JSONArray()
                for (item in mergedItems) {
                    val obj = JSONObject()
                    obj.put("song", item.song)
                    obj.put("artist", item.artist)
                    obj.put("album", item.album ?: "")
                    obj.put("url", item.url)
                    jsonArr.put(obj)
                }
                root.put("items", jsonArr)
                cacheFile.writeText(root.toString())
                Log.i(TAG, "Cached unified community database with ${mergedItems.size} items")
            } catch (e: Exception) {
                Log.w(TAG, "Error saving community cache file: ${e.message}")
            }
        }
    }

    private fun parseCanvasJson(jsonStr: String): List<ViviItem> {
        val list = mutableListOf<ViviItem>()
        try {
            val root = JSONObject(jsonStr)
            val items = root.optJSONArray("items") ?: return emptyList()
            for (i in 0 until items.length()) {
                val it = items.optJSONObject(i) ?: continue
                val song = it.optString("song", "")
                val artist = it.optString("artist", "")
                val album = it.optString("album", "").ifBlank { null }
                val rawUrl = it.optString("url", "")
                val url = sanitizeUrl(rawUrl)
                if (song.isNotBlank() && url.isNotBlank()) {
                    list.add(
                        ViviItem(
                            song = song,
                            artist = artist,
                            album = album,
                            url = url,
                            normSong = normalize(song),
                            normArtist = normalize(artist)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing canvas.json: ${e.message}")
        }
        return list
    }

    fun clearCache() {
        inMemorySongCache.clear()
        cachedItems = null
    }
}
