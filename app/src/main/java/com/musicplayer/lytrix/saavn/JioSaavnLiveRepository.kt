package com.musicplayer.lytrix.saavn

import android.content.Context
import android.util.Base64
import android.util.Log
import com.musicplayer.lytrix.model.JioSaavnSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

enum class SaavnItemType {
    SONG,
    ALBUM,
    PLAYLIST,
}

data class SaavnCardItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String,
    val type: SaavnItemType,
    val song: JioSaavnSong? = null,
)

data class SaavnHomeSection(
    val id: String,
    val title: String,
    val items: List<SaavnCardItem>,
)

data class SaavnHomeData(
    val languageKey: String,
    val fetchedAtMs: Long,
    val trendingSongs: List<JioSaavnSong>,
    val newReleaseSongs: List<JioSaavnSong>,
    val sections: List<SaavnHomeSection>,
)

data class SaavnCollectionDetails(
    val id: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String,
    val type: SaavnItemType,
    val songs: List<JioSaavnSong>,
    val author: String = "",
    val year: String = "",
    val fansText: String = "",
    val description: String = "",
)

/**
 * Live JioSaavn hub backed primarily by direct JioSaavn web endpoints + local DES-ECB URL
 * decryption (0 Cloudflare Worker requests, residential-IP traffic) with automatic silent fallback
 * to the deployed Sumit Kolhe Cloudflare Worker if direct access ever fails.
 */
object JioSaavnLiveRepository {
    private const val TAG = "JioSaavnLiveRepo"
    private const val SAAVN_API = "https://www.jiosaavn.com/api.php"
    private const val CF_FALLBACK_BASE = "https://jiosaavn-api.shuvrodaspranto20170014.workers.dev"
    private const val PREFS_NAME = "saavn_live_home_cache_v3"
    private const val LANG_PREF_KEY = "selected_language_key"
    private const val CACHE_TTL_MS = 6L * 60L * 60L * 1000L // 6 hours
    private const val DES_KEY = "38346591"
    private const val BROWSER_UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

    val LANGUAGE_CHIPS: List<Pair<String, String>> = listOf(
        "all" to "All",
        "hindi" to "Hindi",
        "bengali" to "Bengali",
        "english" to "English",
        "punjabi" to "Punjabi",
        "tamil" to "Tamil",
        "telugu" to "Telugu",
        "malayalam" to "Malayalam",
        "kannada" to "Kannada",
        "marathi" to "Marathi",
        "gujarati" to "Gujarati",
        "bhojpuri" to "Bhojpuri",
        "urdu" to "Urdu",
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val memoryHomeCache = ConcurrentHashMap<String, SaavnHomeData>()
    private val memoryCollectionCache = ConcurrentHashMap<String, Pair<Long, SaavnCollectionDetails>>()

    fun getSelectedLanguage(context: Context): String {
        val saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(LANG_PREF_KEY, "all") ?: "all"
        return if (LANGUAGE_CHIPS.any { it.first == saved }) saved else "all"
    }

    fun setSelectedLanguage(context: Context, key: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(LANG_PREF_KEY, key)
            .apply()
    }

    private fun languageCookieFor(key: String): String = when (key.lowercase()) {
        "hindi" -> "hindi"
        "bengali" -> "bengali"
        "english" -> "english"
        "punjabi" -> "punjabi"
        "tamil" -> "tamil"
        "telugu" -> "telugu"
        "malayalam" -> "malayalam"
        "kannada" -> "kannada"
        "marathi" -> "marathi"
        "gujarati" -> "gujarati"
        "bhojpuri" -> "bhojpuri"
        "urdu" -> "urdu"
        else -> "hindi%2Cenglish"
    }

    fun getCachedHomeData(context: Context, languageKey: String): SaavnHomeData? {
        memoryHomeCache[languageKey]?.let { return it }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString("home_$languageKey", null) ?: return null
        return runCatching { decodeHomeData(JSONObject(raw)) }
            .getOrNull()
            ?.also { memoryHomeCache[languageKey] = it }
    }

    fun isCacheFresh(data: SaavnHomeData?): Boolean {
        if (data == null || data.sections.isEmpty()) return false
        return (System.currentTimeMillis() - data.fetchedAtMs) < CACHE_TTL_MS
    }

    suspend fun fetchHomeData(
        context: Context,
        languageKey: String = "all",
        forceRefresh: Boolean = false,
    ): SaavnHomeData? = withContext(Dispatchers.IO) {
        val cached = getCachedHomeData(context, languageKey)
        if (!forceRefresh && isCacheFresh(cached)) {
            return@withContext cached
        }

        val direct = runCatching { fetchLaunchDataDirect(languageKey) }
            .onFailure { Log.w(TAG, "Direct LaunchData failed ($languageKey): ${it.message}") }
            .getOrNull()
            ?.takeIf { it.sections.isNotEmpty() }

        val result = direct ?: runCatching { fetchHomeFallbackFromWorker(languageKey) }
            .onFailure { Log.w(TAG, "Cloudflare Worker home fallback failed: ${it.message}") }
            .getOrNull()
            ?.takeIf { it.sections.isNotEmpty() }

        if (result != null) {
            memoryHomeCache[languageKey] = result
            runCatching {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString("home_$languageKey", encodeHomeData(result).toString())
                    .apply()
            }
            return@withContext result
        }
        cached
    }

    suspend fun fetchCollectionDetails(
        id: String,
        type: SaavnItemType,
        titleHint: String = "",
        imageHint: String = "",
    ): SaavnCollectionDetails? = withContext(Dispatchers.IO) {
        val cacheKey = "${type.name}:$id"
        memoryCollectionCache[cacheKey]?.let { (ts, cached) ->
            if (System.currentTimeMillis() - ts < 30L * 60L * 1000L && cached.songs.isNotEmpty()) {
                return@withContext cached
            }
        }

        val direct = runCatching { fetchCollectionDirect(id, type, titleHint, imageHint) }
            .onFailure { Log.w(TAG, "Direct collection fetch failed ($type $id): ${it.message}") }
            .getOrNull()
            ?.takeIf { it.songs.isNotEmpty() }

        val resolved = direct ?: runCatching { fetchCollectionFromWorker(id, type, titleHint, imageHint) }
            .onFailure { Log.w(TAG, "Worker collection fallback failed ($type $id): ${it.message}") }
            .getOrNull()
            ?.takeIf { it.songs.isNotEmpty() }

        if (resolved != null) {
            memoryCollectionCache[cacheKey] = System.currentTimeMillis() to resolved
        }
        resolved
    }

    suspend fun resolvePlayableSong(song: JioSaavnSong): JioSaavnSong = withContext(Dispatchers.IO) {
        if (song.downloadUrl.isNotBlank()) return@withContext song
        val direct = runCatching { fetchSongDirect(song.id) }.getOrNull()
        if (direct != null && direct.downloadUrl.isNotBlank()) return@withContext direct
        val worker = runCatching { fetchSongFromWorker(song.id) }.getOrNull()
        if (worker != null && worker.downloadUrl.isNotBlank()) return@withContext worker
        song
    }

    // -------------------------------------------------------------------------
    // Direct JioSaavn Web API (Primary - 0 Cloudflare Worker requests)
    // -------------------------------------------------------------------------

    private fun fetchLaunchDataDirect(languageKey: String): SaavnHomeData? {
        val url = "$SAAVN_API?__call=webapi.getLaunchData&api_version=4&_format=json&_marker=0&ctx=web6dot0"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", BROWSER_UA)
            .header("Accept", "application/json")
            .header("Cookie", "L=${languageCookieFor(languageKey)}")
            .build()

        val body = client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            resp.body?.string()
        } ?: return null

        val root = JSONObject(body)
        val modules = root.optJSONObject("modules")
        val sections = mutableListOf<SaavnHomeSection>()
        val trendingSongs = mutableListOf<JioSaavnSong>()
        val newReleaseSongs = mutableListOf<JioSaavnSong>()

        fun parseArrayIntoCards(arr: JSONArray?, defaultType: SaavnItemType): List<SaavnCardItem> {
            if (arr == null) return emptyList()
            val out = mutableListOf<SaavnCardItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val card = parseRawSaavnObject(obj, defaultType) ?: continue
                out.add(card)
            }
            return out
        }

        // 1. Trending Now
        val trendingCards = parseArrayIntoCards(root.optJSONArray("new_trending"), SaavnItemType.SONG)
        trendingCards.forEach { card -> card.song?.let { trendingSongs.add(it) } }
        if (trendingCards.isNotEmpty()) {
            val title = modules?.optJSONObject("new_trending")?.optString("title")
                ?.takeIf { it.isNotBlank() } ?: "Trending Now"
            sections.add(SaavnHomeSection("new_trending", cleanHtml(title), trendingCards))
        }

        // 2. Top Charts (Live official chart playlists)
        val chartCards = parseArrayIntoCards(root.optJSONArray("charts"), SaavnItemType.PLAYLIST)
        if (chartCards.isNotEmpty()) {
            val title = modules?.optJSONObject("charts")?.optString("title")
                ?.takeIf { it.isNotBlank() } ?: "Top Charts"
            sections.add(SaavnHomeSection("charts", cleanHtml(title), chartCards))
        }

        // 3. New Releases (Fresh singles & albums)
        val newAlbumCards = parseArrayIntoCards(root.optJSONArray("new_albums"), SaavnItemType.ALBUM)
        newAlbumCards.forEach { card -> card.song?.let { newReleaseSongs.add(it) } }
        if (newAlbumCards.isNotEmpty()) {
            val title = modules?.optJSONObject("new_albums")?.optString("title")
                ?.takeIf { it.isNotBlank() } ?: "New Releases"
            sections.add(SaavnHomeSection("new_albums", cleanHtml(title), newAlbumCards))
        }

        // 4. Editorial Picks (Curated weekly-updated playlists)
        val playlistCards = parseArrayIntoCards(root.optJSONArray("top_playlists"), SaavnItemType.PLAYLIST)
        if (playlistCards.isNotEmpty()) {
            val title = modules?.optJSONObject("top_playlists")?.optString("title")
                ?.takeIf { it.isNotBlank() } ?: "Editorial Picks"
            sections.add(SaavnHomeSection("top_playlists", cleanHtml(title), playlistCards))
        }

        // 5. Location-aware city picks (e.g. "What's Hot In Dhaka")
        val cityCards = parseArrayIntoCards(root.optJSONArray("city_mod"), SaavnItemType.PLAYLIST)
        if (cityCards.isNotEmpty()) {
            val title = modules?.optJSONObject("city_mod")?.optString("title")
                ?.takeIf { it.isNotBlank() } ?: "Hot Nearby"
            sections.add(SaavnHomeSection("city_mod", cleanHtml(title), cityCards))
        }

        // 6. Dynamic editorial promo modules (e.g. Best of 90s, Moods)
        if (modules != null) {
            val keys = modules.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (!key.startsWith("promo:")) continue
                val promoArr = root.optJSONArray(key) ?: continue
                val promoCards = parseArrayIntoCards(promoArr, SaavnItemType.PLAYLIST)
                    .filter { it.id.isNotBlank() }
                if (promoCards.size >= 3) {
                    val promoTitle = modules.optJSONObject(key)?.optString("title")
                        ?.takeIf { it.isNotBlank() } ?: continue
                    sections.add(SaavnHomeSection(key, cleanHtml(promoTitle), promoCards))
                }
            }
        }

        return SaavnHomeData(
            languageKey = languageKey,
            fetchedAtMs = System.currentTimeMillis(),
            trendingSongs = trendingSongs,
            newReleaseSongs = newReleaseSongs,
            sections = sections,
        )
    }

    private fun fetchCollectionDirect(
        id: String,
        type: SaavnItemType,
        titleHint: String,
        imageHint: String,
    ): SaavnCollectionDetails? {
        val url = when (type) {
            SaavnItemType.ALBUM ->
                "$SAAVN_API?__call=content.getAlbumDetails&albumid=${URLEncoder.encode(id, "UTF-8")}&api_version=4&_format=json&_marker=0&ctx=web6dot0"
            else ->
                "$SAAVN_API?__call=playlist.getDetails&listid=${URLEncoder.encode(id, "UTF-8")}&api_version=4&_format=json&_marker=0&ctx=web6dot0&n=50&p=1"
        }
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", BROWSER_UA)
            .header("Accept", "application/json")
            .build()

        val body = client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            resp.body?.string()
        } ?: return null

        val root = JSONObject(body)
        val more = root.optJSONObject("more_info")
        val title = cleanHtml(root.optString("title").ifBlank { root.optString("listname") }.ifBlank { titleHint })
        val rawSub = cleanHtml(root.optString("subtitle"))
        val rawDesc = cleanHtml(root.optString("header_desc"))
        val cleanDesc = rawDesc.takeIf {
            it.isNotBlank() &&
                !it.startsWith("0 ·") &&
                !it.equals("Album", ignoreCase = true) &&
                !it.equals("Playlist", ignoreCase = true)
        }.orEmpty()
        val year = root.optString("year").trim().takeIf { it.length == 4 && it.all(Char::isDigit) }.orEmpty()
        val fansText = when {
            rawSub.contains("fan", ignoreCase = true) -> rawSub
            else -> {
                val fc = more?.optString("follower_count")?.toLongOrNull()
                    ?: more?.optString("fan_count")?.toLongOrNull()
                    ?: root.optString("fan_count").toLongOrNull()
                if (fc != null && fc > 0L) formatFansCount(fc) else ""
            }
        }
        val image = upgradeImage(root.optString("image").ifBlank { imageHint })
        val listArr = root.optJSONArray("list") ?: root.optJSONArray("songs") ?: JSONArray()
        val songs = mutableListOf<JioSaavnSong>()
        for (i in 0 until listArr.length()) {
            val sObj = listArr.optJSONObject(i) ?: continue
            parseRawSong(sObj)?.let { songs.add(it) }
        }
        if (songs.isEmpty()) return null
        val author = when (type) {
            SaavnItemType.ALBUM -> extractArtistFromMoreInfo(more)
                .ifBlank { songs.firstOrNull()?.artist.orEmpty() }
                .ifBlank { "JioSaavn" }
            else -> cleanHtml(more?.optString("firstname").orEmpty())
                .ifBlank { "JioSaavn" }
        }
        return SaavnCollectionDetails(
            id = id,
            title = title.ifBlank { "JioSaavn" },
            subtitle = author,
            imageUrl = image.ifBlank { songs.firstOrNull()?.imageUrl.orEmpty() },
            type = type,
            songs = songs,
            author = author,
            year = year,
            fansText = fansText,
            description = cleanDesc,
        )
    }

    private fun fetchSongDirect(id: String): JioSaavnSong? {
        val url = "$SAAVN_API?__call=song.getDetails&pids=${URLEncoder.encode(id, "UTF-8")}&api_version=4&_format=json&_marker=0&ctx=web6dot0"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", BROWSER_UA)
            .header("Accept", "application/json")
            .build()
        val body = client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            resp.body?.string()
        } ?: return null
        val root = JSONObject(body)
        val songObj = root.optJSONArray("songs")?.optJSONObject(0) ?: root.optJSONObject(id) ?: return null
        return parseRawSong(songObj)
    }

    // -------------------------------------------------------------------------
    // Cloudflare Worker Fallback (Only used if direct JioSaavn API fails)
    // -------------------------------------------------------------------------

    private suspend fun fetchHomeFallbackFromWorker(languageKey: String): SaavnHomeData = coroutineScope {
        val langLabel = when (languageKey.lowercase()) {
            "bengali" -> "Bengali"
            "english" -> "English"
            "punjabi" -> "Punjabi"
            else -> "Hindi"
        }
        val dTrending = async { fetchWorkerSongs("Trending $langLabel", 18) }
        val dNew = async { fetchWorkerSongs("New $langLabel Songs", 18) }
        val dPlaylists = async { fetchWorkerPlaylists("Top $langLabel", 12) }

        val trending = dTrending.await()
        val newReleases = dNew.await()
        val playlists = dPlaylists.await()

        val sections = buildList {
            if (trending.isNotEmpty()) {
                add(
                    SaavnHomeSection(
                        "worker_trending",
                        "🔥 Trending Now",
                        trending.map { SaavnCardItem(it.id, it.name, it.artist, it.imageUrl, SaavnItemType.SONG, it) }
                    )
                )
            }
            if (playlists.isNotEmpty()) {
                add(SaavnHomeSection("worker_playlists", "📊 Top Playlists", playlists))
            }
            if (newReleases.isNotEmpty()) {
                add(
                    SaavnHomeSection(
                        "worker_new",
                        "🆕 New Releases",
                        newReleases.map { SaavnCardItem(it.id, it.name, it.artist, it.imageUrl, SaavnItemType.SONG, it) }
                    )
                )
            }
        }

        SaavnHomeData(
            languageKey = languageKey,
            fetchedAtMs = System.currentTimeMillis(),
            trendingSongs = trending,
            newReleaseSongs = newReleases,
            sections = sections,
        )
    }

    private fun fetchWorkerSongs(query: String, limit: Int): List<JioSaavnSong> {
        val url = "$CF_FALLBACK_BASE/api/search/songs?query=${URLEncoder.encode(query, "UTF-8")}&limit=$limit"
        val req = Request.Builder().url(url).header("User-Agent", BROWSER_UA).build()
        val body = client.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null } ?: return emptyList()
        val root = JSONObject(body)
        val arr = root.optJSONObject("data")?.optJSONArray("results") ?: return emptyList()
        return parseWorkerSongsArray(arr)
    }

    private fun fetchWorkerPlaylists(query: String, limit: Int): List<SaavnCardItem> {
        val url = "$CF_FALLBACK_BASE/api/search/playlists?query=${URLEncoder.encode(query, "UTF-8")}&limit=$limit"
        val req = Request.Builder().url(url).header("User-Agent", BROWSER_UA).build()
        val body = client.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null } ?: return emptyList()
        val root = JSONObject(body)
        val arr = root.optJSONObject("data")?.optJSONArray("results") ?: return emptyList()
        val out = mutableListOf<SaavnCardItem>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val id = obj.optString("id").takeIf { it.isNotBlank() } ?: continue
            val name = cleanHtml(obj.optString("name"))
            val img = extractWorkerImage(obj.optJSONArray("image"))
            val count = obj.optInt("songCount", 0)
            val sub = if (count > 0) "Playlist • $count songs" else "JioSaavn Playlist"
            out.add(SaavnCardItem(id, name, sub, img, SaavnItemType.PLAYLIST))
        }
        return out
    }

    private fun fetchCollectionFromWorker(
        id: String,
        type: SaavnItemType,
        titleHint: String,
        imageHint: String,
    ): SaavnCollectionDetails? {
        val endpoint = if (type == SaavnItemType.ALBUM) {
            "$CF_FALLBACK_BASE/api/albums?id=${URLEncoder.encode(id, "UTF-8")}"
        } else {
            "$CF_FALLBACK_BASE/api/playlists?id=${URLEncoder.encode(id, "UTF-8")}&limit=50"
        }
        val req = Request.Builder().url(endpoint).header("User-Agent", BROWSER_UA).build()
        val body = client.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null } ?: return null
        val data = JSONObject(body).optJSONObject("data") ?: return null
        val songs = parseWorkerSongsArray(data.optJSONArray("songs"))
        if (songs.isEmpty()) return null
        val title = cleanHtml(data.optString("name").ifBlank { titleHint })
        val subtitle = cleanHtml(
            data.optString("description")
                .ifBlank { if (type == SaavnItemType.ALBUM) "JioSaavn Album" else "JioSaavn Playlist" }
        )
        val img = extractWorkerImage(data.optJSONArray("image")).ifBlank { imageHint }
        return SaavnCollectionDetails(id, title, subtitle, img, type, songs)
    }

    private fun fetchSongFromWorker(id: String): JioSaavnSong? {
        val url = "$CF_FALLBACK_BASE/api/songs?ids=${URLEncoder.encode(id, "UTF-8")}"
        val req = Request.Builder().url(url).header("User-Agent", BROWSER_UA).build()
        val body = client.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null } ?: return null
        val arr = JSONObject(body).optJSONArray("data") ?: return null
        return parseWorkerSongsArray(arr).firstOrNull()
    }

    private fun parseWorkerSongsArray(arr: JSONArray?): List<JioSaavnSong> {
        if (arr == null) return emptyList()
        val out = mutableListOf<JioSaavnSong>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val id = obj.optString("id").takeIf { it.isNotBlank() } ?: continue
            val name = cleanHtml(obj.optString("name").ifBlank { obj.optString("title") })
            val primaryArr = obj.optJSONObject("artists")?.optJSONArray("primary")
            val artist = buildString {
                if (primaryArr != null && primaryArr.length() > 0) {
                    for (j in 0 until primaryArr.length()) {
                        val aName = primaryArr.optJSONObject(j)?.optString("name")?.takeIf { it.isNotBlank() } ?: continue
                        if (isNotEmpty()) append(", ")
                        append(aName)
                    }
                }
            }.ifBlank { cleanHtml(obj.optString("primaryArtists").ifBlank { "JioSaavn" }) }
            val image = extractWorkerImage(obj.optJSONArray("image"))
            val dlArr = obj.optJSONArray("downloadUrl")
            val dlUrl = if (dlArr != null && dlArr.length() > 0) {
                dlArr.optJSONObject(dlArr.length() - 1)?.optString("url").orEmpty()
            } else ""
            out.add(JioSaavnSong(id, name, cleanHtml(artist), image, dlUrl))
        }
        return out
    }

    private fun extractWorkerImage(arr: JSONArray?): String {
        if (arr == null || arr.length() == 0) return ""
        return arr.optJSONObject(arr.length() - 1)?.optString("url")
            ?.takeIf { it.isNotBlank() }
            ?: arr.optJSONObject(arr.length() - 1)?.optString("link").orEmpty()
    }

    // -------------------------------------------------------------------------
    // Direct JioSaavn JSON Parsers + DES-ECB Decryptor
    // -------------------------------------------------------------------------

    private fun parseRawSaavnObject(obj: JSONObject, defaultType: SaavnItemType): SaavnCardItem? {
        val rawType = obj.optString("type").lowercase()
        val type = when (rawType) {
            "song" -> SaavnItemType.SONG
            "album" -> SaavnItemType.ALBUM
            "playlist", "chart" -> SaavnItemType.PLAYLIST
            "radio_station", "mix", "channel", "artist" -> return null // Skip unsupported non-song/album/playlist cards
            else -> defaultType
        }
        val id = obj.optString("id").ifBlank { obj.optString("listid") }.trim()
        if (id.isBlank()) return null

        val title = cleanHtml(obj.optString("title").ifBlank { obj.optString("name") })
        if (title.isBlank()) return null
        val image = upgradeImage(obj.optString("image"))

        return if (type == SaavnItemType.SONG) {
            val song = parseRawSong(obj) ?: return null
            SaavnCardItem(
                id = song.id,
                title = song.name,
                subtitle = song.artist,
                imageUrl = song.imageUrl,
                type = SaavnItemType.SONG,
                song = song,
            )
        } else {
            val more = obj.optJSONObject("more_info")
            val count = more?.optString("song_count")?.toIntOrNull()
                ?: obj.optString("count").toIntOrNull()
                ?: 0
            val year = obj.optString("year").trim().takeIf { it.length == 4 && it.all(Char::isDigit) }.orEmpty()
            val rawSub = cleanHtml(obj.optString("subtitle"))
            val isFollowerOrFanSub = rawSub.contains("follower", ignoreCase = true) || rawSub.contains("fan", ignoreCase = true)
            val subtitle = when (type) {
                SaavnItemType.ALBUM -> {
                    val artistFromMore = extractArtistFromMoreInfo(more)
                    val cleanSubArtist = if (!isFollowerOrFanSub) {
                        rawSub.removeSuffix(" - $title").removeSuffix("- $title").trim()
                    } else ""
                    val artistLabel = artistFromMore.ifBlank { cleanSubArtist }
                    when {
                        artistLabel.isNotBlank() -> "Album • $artistLabel"
                        year.isNotBlank() -> "Album • $year"
                        else -> "Album"
                    }
                }
                else -> {
                    val curator = cleanHtml(more?.optString("firstname").orEmpty()).ifBlank { "JioSaavn" }
                    if (count > 0) "Playlist • $count songs" else "Playlist • $curator"
                }
            }
            SaavnCardItem(
                id = id,
                title = title,
                subtitle = subtitle,
                imageUrl = image,
                type = type,
                song = null,
            )
        }
    }

    private fun formatFansCount(count: Long): String = when {
        count >= 1_000_000L -> String.format(java.util.Locale.US, "%.1fM Fans", count / 1_000_000.0).replace(".0M", "M")
        count >= 1_000L -> String.format(java.util.Locale.US, "%.1fK Fans", count / 1_000.0).replace(".0K", "K")
        else -> "$count Fans"
    }

    private fun parseRawSong(obj: JSONObject): JioSaavnSong? {
        val id = obj.optString("id").trim()
        if (id.isBlank()) return null
        val title = cleanHtml(obj.optString("title").ifBlank { obj.optString("song") })
        if (title.isBlank()) return null

        val more = obj.optJSONObject("more_info")
        val artist = extractArtistFromMoreInfo(more)
            .ifBlank { cleanHtml(obj.optString("subtitle")) }
            .ifBlank { cleanHtml(obj.optString("singers")) }
            .ifBlank { "JioSaavn" }

        val image = upgradeImage(obj.optString("image"))
        val encUrl = more?.optString("encrypted_media_url")?.takeIf { it.isNotBlank() }
            ?: obj.optString("encrypted_media_url").takeIf { it.isNotBlank() }
        val is320 = (more?.optString("320kbps") ?: obj.optString("320kbps")).equals("true", ignoreCase = true)
        val decryptedUrl = if (encUrl != null) decryptSaavnMediaUrl(encUrl, prefer320 = is320) else ""

        return JioSaavnSong(
            id = id,
            name = title,
            artist = artist,
            imageUrl = image,
            downloadUrl = decryptedUrl,
        )
    }

    private fun extractArtistFromMoreInfo(more: JSONObject?): String {
        if (more == null) return ""
        val primary = more.optJSONObject("artistMap")?.optJSONArray("primary_artists")
        if (primary != null && primary.length() > 0) {
            val names = mutableListOf<String>()
            for (i in 0 until primary.length()) {
                val n = primary.optJSONObject(i)?.optString("name")?.trim()
                if (!n.isNullOrBlank()) names.add(cleanHtml(n))
            }
            if (names.isNotEmpty()) return names.distinct().joinToString(", ")
        }
        val singers = more.optJSONObject("artistMap")?.optJSONArray("artists")
        if (singers != null && singers.length() > 0) {
            val names = mutableListOf<String>()
            for (i in 0 until minOf(singers.length(), 3)) {
                val n = singers.optJSONObject(i)?.optString("name")?.trim()
                if (!n.isNullOrBlank()) names.add(cleanHtml(n))
            }
            if (names.isNotEmpty()) return names.distinct().joinToString(", ")
        }
        return cleanHtml(more.optString("music"))
    }

    /**
     * Exact DES-ECB decryption used by JioSaavn web player & Sumit Kolhe's JioSaavn API.
     */
    fun decryptSaavnMediaUrl(encryptedUrl: String, prefer320: Boolean = true): String {
        return try {
            val keySpec = SecretKeySpec(DES_KEY.toByteArray(Charsets.UTF_8), "DES")
            val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, keySpec)
            val encryptedBytes = Base64.decode(encryptedUrl.trim(), Base64.DEFAULT)
            val decrypted = String(cipher.doFinal(encryptedBytes), Charsets.UTF_8).trim()
            val upgraded = when {
                prefer320 && decrypted.contains("_96.mp4") -> decrypted.replace("_96.mp4", "_320.mp4")
                decrypted.contains("_96.mp4") -> decrypted.replace("_96.mp4", "_160.mp4")
                else -> decrypted
            }
            upgraded.replace("http://", "https://")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decrypt JioSaavn media URL: ${e.message}")
            ""
        }
    }

    private fun upgradeImage(url: String): String {
        if (url.isBlank()) return ""
        return url
            .replace("http://", "https://")
            .replace("50x50.jpg", "500x500.jpg")
            .replace("150x150.jpg", "500x500.jpg")
    }

    private fun cleanHtml(text: String): String {
        return text
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&#039;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .trim()
    }

    // -------------------------------------------------------------------------
    // Local JSON Cache Codec (Instant 0ms load on revisit)
    // -------------------------------------------------------------------------

    private fun encodeHomeData(data: SaavnHomeData): JSONObject {
        return JSONObject().apply {
            put("lang", data.languageKey)
            put("ts", data.fetchedAtMs)
            put("trending", encodeSongs(data.trendingSongs))
            put("newReleases", encodeSongs(data.newReleaseSongs))
            val secArr = JSONArray()
            data.sections.forEach { sec ->
                val secObj = JSONObject().apply {
                    put("id", sec.id)
                    put("title", sec.title)
                    val itemArr = JSONArray()
                    sec.items.forEach { item ->
                        itemArr.put(JSONObject().apply {
                            put("id", item.id)
                            put("title", item.title)
                            put("subtitle", item.subtitle)
                            put("image", item.imageUrl)
                            put("type", item.type.name)
                            if (item.song != null) {
                                put("dl", item.song.downloadUrl)
                            }
                        })
                    }
                    put("items", itemArr)
                }
                secArr.put(secObj)
            }
            put("sections", secArr)
        }
    }

    private fun decodeHomeData(obj: JSONObject): SaavnHomeData {
        val lang = obj.optString("lang", "all")
        val ts = obj.optLong("ts", 0L)
        val trending = decodeSongs(obj.optJSONArray("trending"))
        val newReleases = decodeSongs(obj.optJSONArray("newReleases"))
        val secArr = obj.optJSONArray("sections") ?: JSONArray()
        val sections = mutableListOf<SaavnHomeSection>()
        for (i in 0 until secArr.length()) {
            val sObj = secArr.optJSONObject(i) ?: continue
            val itemsArr = sObj.optJSONArray("items") ?: JSONArray()
            val items = mutableListOf<SaavnCardItem>()
            for (j in 0 until itemsArr.length()) {
                val itObj = itemsArr.optJSONObject(j) ?: continue
                val type = runCatching { SaavnItemType.valueOf(itObj.optString("type", "SONG")) }
                    .getOrDefault(SaavnItemType.SONG)
                val id = itObj.optString("id")
                val title = itObj.optString("title")
                val subtitle = itObj.optString("subtitle")
                val image = itObj.optString("image")
                val dl = itObj.optString("dl")
                val song = if (type == SaavnItemType.SONG) JioSaavnSong(id, title, subtitle, image, dl) else null
                items.add(SaavnCardItem(id, title, subtitle, image, type, song))
            }
            if (items.isNotEmpty()) {
                sections.add(SaavnHomeSection(sObj.optString("id"), sObj.optString("title"), items))
            }
        }
        return SaavnHomeData(lang, ts, trending, newReleases, sections)
    }

    private fun encodeSongs(songs: List<JioSaavnSong>): JSONArray {
        val arr = JSONArray()
        songs.forEach { s ->
            arr.put(JSONObject().apply {
                put("id", s.id)
                put("name", s.name)
                put("artist", s.artist)
                put("img", s.imageUrl)
                put("dl", s.downloadUrl)
            })
        }
        return arr
    }

    private fun decodeSongs(arr: JSONArray?): List<JioSaavnSong> {
        if (arr == null) return emptyList()
        val out = mutableListOf<JioSaavnSong>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(
                JioSaavnSong(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    artist = o.optString("artist"),
                    imageUrl = o.optString("img"),
                    downloadUrl = o.optString("dl"),
                )
            )
        }
        return out
    }
}
