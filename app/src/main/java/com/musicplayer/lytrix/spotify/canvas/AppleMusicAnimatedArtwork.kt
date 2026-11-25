package com.musicplayer.lytrix.spotify.canvas

import android.content.Context
import android.util.Log
import com.musicplayer.lytrix.model.Song
import com.musicplayer.lytrix.spotify.SpotifyPreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object AppleMusicAnimatedArtwork {
    private const val TAG = "AppleMusicAnimatedArtwork"
    private const val PREFS_NAME = "apple_motion_cache_v5"
    private const val TOKEN_PREFS = "apple_music_token_prefs"
    private const val KEY_DEVELOPER_TOKEN = "developer_token"

    // Default long-lived web player developer token (valid till 2027)
    private const val DEFAULT_TOKEN = "eyJ0eXAiOiJKV1QiLCJhbGciOiJFUzI1NiIsImtpZCI6IldlYlBsYXlLaWQifQ.eyJpc3MiOiJBTVBXZWJQbGF5IiwiaWF0IjoxNzkyNzgwNTM0LCJleHAiOjE3OTg4Mjg1MzQsInJvb3RfaHR0cHNfb3JpZ2luIjpbImFwcGxlLmNvbSJdfQ.wcf_7U-zcgUjY2L8d4HWFkdxP1YSJCkkqEpEtVPKpRARrlaag0DHyIpRcoUEWZU8kJlvx5Q7ZNp04OabfuiuAw"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val inMemoryCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val inMemoryArtistCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private var cachedToken: String? = null

    private val CLEAN_PARENS_REGEX = Pattern.compile("\\(.*?\\b(?:official|video|audio|lyrics|feat|ft\\.)\\b.*?\\)", Pattern.CASE_INSENSITIVE)
    private val CLEAN_BRACKETS_REGEX = Pattern.compile("\\[.*?\\b(?:official|video|audio|lyrics|feat|ft\\.)\\b.*?\\]", Pattern.CASE_INSENSITIVE)
    private val CLEAN_MOVIE_REGEX = Pattern.compile("[\\[(].*?\\b(?:from|soundtrack|ost|theme|motion\\s+picture|original\\s+soundtrack|album\\s+version|original\\s+motion\\s+picture|score)\\b.*?[\\])]", Pattern.CASE_INSENSITIVE)
    private val DASH_SOUNDTRACK_REGEX = Pattern.compile("\\s+[-–—~]\\s+(?:from|soundtrack|ost|theme)\\b.*$", Pattern.CASE_INSENSITIVE)
    private val SMART_QUOTES_REGEX = Pattern.compile("[\u2018\u2019\u201C\u201D\"']")
    private val CLEAN_FEAT_REGEX = Pattern.compile("\\b(?:feat|ft)\\.?\\s+.*", Pattern.CASE_INSENSITIVE)

    private val TITLE_STOPWORDS = setOf(
        "from", "the", "a", "an", "and", "or", "in", "on", "at", "to", "by", "for",
        "with", "of", "soundtrack", "ost", "theme", "song", "version", "full", "audio", "video", "part", "pt"
    )

    private val STREAM_INF_REGEX = Regex("""#EXT-X-STREAM-INF:([^\n]*)\r?\n(\S+)""")
    private val RESOLUTION_REGEX = Regex("""RESOLUTION=(\d+)x(\d+)""")
    private val CODECS_REGEX = Regex("""CODECS="([^"]+)"""")
    private val AVERAGE_BANDWIDTH_REGEX = Regex("""AVERAGE-BANDWIDTH=(\d+)""")
    private val BANDWIDTH_REGEX = Regex("""(?:^|,)BANDWIDTH=(\d+)""")

    data class AMHlsVariant(
        val uri: String,
        val width: Int,
        val height: Int,
        val bandwidth: Int,
        val codecs: String
    ) {
        val isH264: Boolean get() = codecs.startsWith("avc1")
    }

    fun cleanTitle(title: String): String {
        var s = SMART_QUOTES_REGEX.matcher(title).replaceAll(" ")
        s = CLEAN_PARENS_REGEX.matcher(s).replaceAll(" ")
        s = CLEAN_BRACKETS_REGEX.matcher(s).replaceAll(" ")
        s = CLEAN_MOVIE_REGEX.matcher(s).replaceAll(" ")
        s = DASH_SOUNDTRACK_REGEX.matcher(s).replaceAll(" ")
        s = CLEAN_FEAT_REGEX.matcher(s).replaceAll(" ")
        return s.replace(Regex("""\s+"""), " ").trim()
    }

    suspend fun getAnimatedArtworkUrl(context: Context, song: Song): String? = withContext(Dispatchers.IO) {
        try {
            // 🔥 Local / Offline songs MUST NEVER show online motion / canvas
            val isLocal = song.filePath !in listOf("ONLINE_LYTRIX", "ONLINE") && !song.uriString.startsWith("http")
            if (isLocal) return@withContext null

            val artworkPrefs = com.musicplayer.lytrix.artwork.ArtworkPreferencesManager.getInstance(context)
            if (!artworkPrefs.animatedCoversEnabled.value) {
                return@withContext null
            }

            val title = song.title.trim()
            val artist = song.artist.trim()
            if (title.isBlank()) return@withContext null

            // 🔥 Filter out Mashup / Remix / Mix / Slowed / Jukebox to prevent wrong random covers
            val lowerTitle = title.lowercase(Locale.US)
            val isMashup = lowerTitle.contains("mashup") || lowerTitle.contains("remix") || 
                           lowerTitle.contains("nonstop") || lowerTitle.contains("jukebox") || 
                           lowerTitle.contains("slowed") || lowerTitle.contains("reverb")
            if (isMashup) return@withContext null

            val preferredResolution = artworkPrefs.animatedCoverResolution.value
            val cacheKey = "${title.lowercase(Locale.US)}_${artist.lowercase(Locale.US)}_$preferredResolution"
            inMemoryCache[cacheKey]?.let { return@withContext it }

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getString(cacheKey, null)?.let {
                inMemoryCache[cacheKey] = it
                return@withContext it
            }

            var token = getDeveloperToken(context)
            if (isTokenExpired(token)) {
                token = scrapeFreshToken(context) ?: token
            }
            var videoUrl = searchMotionArtwork(token, title, artist, song.album, preferredResolution)

            // If failed with 401 or null, refresh token and retry once
            if (videoUrl == null) {
                val refreshedToken = scrapeFreshToken(context)
                if (refreshedToken != null && refreshedToken != token) {
                    videoUrl = searchMotionArtwork(refreshedToken, title, artist, song.album, preferredResolution)
                }
            }

            if (!videoUrl.isNullOrBlank()) {
                inMemoryCache[cacheKey] = videoUrl
                prefs.edit().putString(cacheKey, videoUrl).apply()
                Log.d(TAG, "Found Apple Music motion cover for $title by $artist: $videoUrl")
            }
            videoUrl
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Apple Music animated artwork", e)
            null
        }
    }

    private fun getDeveloperToken(context: Context): String {
        cachedToken?.let { return it }
        val prefs = context.getSharedPreferences(TOKEN_PREFS, Context.MODE_PRIVATE)
        val token = prefs.getString(KEY_DEVELOPER_TOKEN, DEFAULT_TOKEN) ?: DEFAULT_TOKEN
        cachedToken = token
        return token
    }

    private suspend fun scrapeFreshToken(context: Context): String? = withContext(Dispatchers.IO) {
        try {
            val req1 = Request.Builder()
                .url("https://music.apple.com")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val html = httpClient.newCall(req1).execute().use { it.body?.string() } ?: return@withContext null
            val bundleMatch = Regex("""/assets/index~[^/"]+\.js""").find(html)
            val bundlePath = bundleMatch?.value ?: return@withContext null

            val req2 = Request.Builder()
                .url("https://music.apple.com$bundlePath")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val js = httpClient.newCall(req2).execute().use { it.body?.string() } ?: return@withContext null
            val jwtRegex = Regex("""eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+""")
            val candidates = jwtRegex.findAll(js).map { it.value }.toList()
            val token = candidates.firstOrNull { isWebPlayToken(it) } ?: candidates.firstOrNull()

            if (token != null) {
                cachedToken = token
                context.getSharedPreferences(TOKEN_PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_DEVELOPER_TOKEN, token)
                    .apply()
                Log.d(TAG, "Scraped and updated Apple Music developer token: ${token.take(20)}...")
                return@withContext token
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scraping Apple Music token", e)
        }
        null
    }

    private fun isTokenExpired(jwt: String): Boolean {
        return try {
            val parts = jwt.split(".")
            if (parts.size < 2) return true
            var payload = parts[1]
            val remainder = payload.length % 4
            if (remainder > 0) payload += "=".repeat(4 - remainder)
            val decoded = android.util.Base64.decode(payload, android.util.Base64.URL_SAFE).toString(Charsets.UTF_8)
            val expSec = JSONObject(decoded).optLong("exp", 0L)
            expSec > 0L && (expSec * 1000L) <= (System.currentTimeMillis() + 60_000L)
        } catch (e: Exception) {
            false
        }
    }

    private fun isWebPlayToken(jwt: String): Boolean {
        return try {
            val parts = jwt.split(".")
            if (parts.size < 2) return false
            var payload = parts[1]
            val remainder = payload.length % 4
            if (remainder > 0) payload += "=".repeat(4 - remainder)
            val decoded = android.util.Base64.decode(payload, android.util.Base64.URL_SAFE).toString(Charsets.UTF_8)
            decoded.contains("\"iss\":\"AMPWebPlay\"")
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun searchMotionArtwork(
        token: String,
        title: String,
        artist: String,
        albumName: String?,
        preferredResolution: String = SpotifyPreferencesManager.DEFAULT_ARTIST_VIDEO_RESOLUTION
    ): String? = withContext(Dispatchers.IO) {
        val cleanedTitle = cleanTitle(title)
        val primaryArtist = artist.split(",", "&", "/").firstOrNull()?.trim() ?: artist.trim()

        // Dual storefronts: "in" (India - for Bollywood/Hindi/Punjabi/regional) and "us" (Global/Western Pop)
        val storefronts = listOf("in", "us")

        for (sf in storefronts) {
            // Tier 1: Search album name if available
            if (!albumName.isNullOrBlank() && !albumName.equals(title, ignoreCase = true) && albumName != "Album") {
                val albumQuery = "${cleanTitle(albumName)} $primaryArtist".trim()
                val rawMaster = executeSearch(token, sf, albumQuery, cleanedTitle, primaryArtist)
                if (rawMaster != null) {
                    return@withContext resolveBestRendition(rawMaster, preferredResolution)
                }
            }

            // Tier 2: Search song title + primary artist
            val primaryQuery = "$cleanedTitle $primaryArtist".trim()
            val rawMaster = executeSearch(token, sf, primaryQuery, cleanedTitle, primaryArtist)
            if (rawMaster != null) {
                return@withContext resolveBestRendition(rawMaster, preferredResolution)
            }

            // Tier 3: Raw original title fallback
            if (cleanedTitle != title) {
                val rawQuery = "$title $primaryArtist".trim()
                val rawMasterFallback = executeSearch(token, sf, rawQuery, cleanedTitle, primaryArtist)
                if (rawMasterFallback != null) {
                    return@withContext resolveBestRendition(rawMasterFallback, preferredResolution)
                }
            }
        }

        null
    }

    private fun isInvalidAlbum(albAttr: JSONObject?): Boolean {
        if (albAttr == null) return true
        val isCompilation = albAttr.optBoolean("isCompilation", false)
        if (isCompilation) return true

        val name = albAttr.optString("name", "").lowercase(Locale.US)
        val artist = albAttr.optString("artistName", "").lowercase(Locale.US)

        val bannedKeywords = listOf(
            "top trending", "trending hits", "best of bollywood", "top 100", 
            "top 50", "various artists", "apple music", "soundtrack compilation",
            "greatest hits", "essential hits", "popular tracks", "workout mix"
        )
        if (bannedKeywords.any { name.contains(it) || artist.contains(it) }) {
            return true
        }

        if (artist == "various artists" || artist == "apple music") {
            return true
        }

        return false
    }

    private fun isMatch(expectedTitle: String, expectedArtist: String, candidateTitle: String, candidateArtist: String): Boolean {
        val expClean = cleanTitle(expectedTitle).lowercase(Locale.US).replace(Regex("[^a-z0-9]"), " ").trim()
        val candClean = cleanTitle(candidateTitle).lowercase(Locale.US).replace(Regex("[^a-z0-9]"), " ").trim()
        if (expClean.isBlank() || candClean.isBlank()) return false

        val expTokens = expClean.split(Regex("\\s+")).filter { it.length > 1 }
        val candTokens = candClean.split(Regex("\\s+")).filter { it.length > 1 }

        val sigExpTokens = expTokens.filter { it !in TITLE_STOPWORDS }
        val sigCandTokens = candTokens.filter { it !in TITLE_STOPWORDS }

        val titleMatch = when {
            expClean == candClean -> true
            sigExpTokens.isNotEmpty() && sigCandTokens.isNotEmpty() -> {
                val commonSig = sigExpTokens.intersect(sigCandTokens.toSet())
                if (sigExpTokens.size <= 2) {
                    commonSig.size == sigExpTokens.size
                } else {
                    commonSig.size >= (sigExpTokens.size * 0.6).toInt().coerceAtLeast(2)
                }
            }
            candClean.contains(expClean) || expClean.contains(candClean) -> true
            else -> false
        }
        if (!titleMatch) return false

        val expA = expectedArtist.split(",", "&", "/").firstOrNull()?.trim()?.lowercase(Locale.US)?.replace(Regex("[^a-z0-9]"), " ")?.trim() ?: ""
        val candA = candidateArtist.lowercase(Locale.US).replace(Regex("[^a-z0-9]"), " ").trim()
        if (expA.isNotBlank() && candA.isNotBlank()) {
            val expATokens = expA.split(Regex("\\s+")).filter { it.length > 1 }
            val candATokens = candA.split(Regex("\\s+")).filter { it.length > 1 }
            if (expATokens.isNotEmpty() && candATokens.isNotEmpty()) {
                return expATokens.any { candATokens.contains(it) } || candA.contains(expA) || expA.contains(candA)
            }
            return candA.contains(expA) || expA.contains(candA)
        }
        return true
    }

    private suspend fun executeSearch(token: String, storefront: String, query: String, expectedTitle: String, expectedArtist: String): String? = withContext(Dispatchers.IO) {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://amp-api.music.apple.com/v1/catalog/$storefront/search?term=$encodedQuery&types=songs,albums&include[songs]=albums&extend=editorialVideo"

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .header("Origin", "https://music.apple.com")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val root = JSONObject(body)
                val results = root.optJSONObject("results") ?: return@withContext null

                // 1. Check songs -> parent albums
                val songs = results.optJSONObject("songs")?.optJSONArray("data")
                if (songs != null) {
                    for (i in 0 until songs.length()) {
                        val songObj = songs.getJSONObject(i)
                        val songAttr = songObj.optJSONObject("attributes")
                        val songName = songAttr?.optString("name", "") ?: ""
                        val songArtist = songAttr?.optString("artistName", "") ?: ""
                        if (!isMatch(expectedTitle, expectedArtist, songName, songArtist)) continue

                        val relAlbums = songObj.optJSONObject("relationships")
                            ?.optJSONObject("albums")
                            ?.optJSONArray("data")
                        if (relAlbums != null) {
                            for (j in 0 until relAlbums.length()) {
                                val alb = relAlbums.getJSONObject(j)
                                val albAttr = alb.optJSONObject("attributes")
                                if (isInvalidAlbum(albAttr)) continue
                                val video = extractVideoFromEditorial(albAttr?.optJSONObject("editorialVideo"))
                                if (video != null) return@withContext video
                            }
                        }
                    }
                }

                // 2. Check direct albums results
                val albums = results.optJSONObject("albums")?.optJSONArray("data")
                if (albums != null) {
                    for (i in 0 until albums.length()) {
                        val album = albums.getJSONObject(i)
                        val albAttr = album.optJSONObject("attributes")
                        val albName = albAttr?.optString("name", "") ?: ""
                        val albArtist = albAttr?.optString("artistName", "") ?: ""
                        if (isInvalidAlbum(albAttr)) continue
                        if (!isMatch(expectedTitle, expectedArtist, albName, albArtist)) continue

                        val video = extractVideoFromEditorial(albAttr?.optJSONObject("editorialVideo"))
                        if (video != null) return@withContext video
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing Apple Music search ($storefront) for: $query", e)
        }
        null
    }

    private fun extractVideoFromEditorial(edVideo: JSONObject?): String? {
        if (edVideo == null) return null

        // Check pure 1:1 square motion covers first (matches Nov 15 behavior & eliminates 3:4 bottom cloud padding)
        val squareVideo = edVideo.optJSONObject("motionDetailSquare")?.optString("video")
            ?.takeIf { it.isNotBlank() }
            ?: edVideo.optJSONObject("motionSquareVideo1x1")?.optString("video")
                ?.takeIf { it.isNotBlank() }
        if (squareVideo != null) return squareVideo

        // Fallback to portrait tall video only if no 1:1 square motion cover exists
        val tallVideo = edVideo.optJSONObject("motionDetailTall")?.optString("video")
            ?.takeIf { it.isNotBlank() }
            ?: edVideo.optJSONObject("motionTallVideo3x4")?.optString("video")
                ?.takeIf { it.isNotBlank() }
        if (tallVideo != null) return tallVideo

        // Check any available video entry
        val keys = edVideo.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val obj = edVideo.optJSONObject(key)
            val v = obj?.optString("video")
            if (!v.isNullOrBlank()) return v
        }
        return null
    }

    /**
     * Parses the Apple Music master playlist and picks an H.264 (avc1) rendition
     * matching the user's preferred resolution (480p, 720p, 1080p, Original).
     * This guarantees silky-smooth playback and eliminates
     * 10-bit HEVC hardware decoding failures on Android devices.
     */
    private suspend fun resolveBestRendition(
        masterUrl: String,
        preferredResolution: String = SpotifyPreferencesManager.DEFAULT_ARTIST_VIDEO_RESOLUTION
    ): String = withContext(Dispatchers.IO) {
        if (!masterUrl.endsWith(".m3u8", ignoreCase = true)) {
            return@withContext masterUrl
        }
        try {
            val req = Request.Builder()
                .url(masterUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val playlistText = httpClient.newCall(req).execute().use { it.body?.string() } ?: return@withContext masterUrl

            val variants = STREAM_INF_REGEX.findAll(playlistText).mapNotNull { match ->
                val attributes = match.groupValues[1]
                val resolution = RESOLUTION_REGEX.find(attributes) ?: return@mapNotNull null
                val codecs = CODECS_REGEX.find(attributes)?.groupValues?.get(1) ?: return@mapNotNull null
                val bandwidth = (AVERAGE_BANDWIDTH_REGEX.find(attributes) ?: BANDWIDTH_REGEX.find(attributes))
                    ?.groupValues?.last()?.toIntOrNull() ?: return@mapNotNull null

                val resolvedUri = resolveAgainst(masterUrl, match.groupValues[2])
                AMHlsVariant(
                    uri = resolvedUri,
                    width = resolution.groupValues[1].toInt(),
                    height = resolution.groupValues[2].toInt(),
                    bandwidth = bandwidth,
                    codecs = codecs
                )
            }.toList()

            if (variants.isEmpty()) return@withContext masterUrl

            // Filter for H.264 (avc1) first for smooth decoding on all Android hardware
            val pool = variants.filter { it.isH264 }.ifEmpty { variants }

            val p480 = pool.filter { maxOf(it.width, it.height) in 360 until 720 }
            val p720 = pool.filter { maxOf(it.width, it.height) in 720 until 1080 }
            val p1080 = pool.filter { maxOf(it.width, it.height) in 1080..1920 }

            val chosenVariant = when (preferredResolution.lowercase()) {
                "480p", "low", "low_480", "low_360" -> {
                    p480.maxByOrNull { it.bandwidth }
                        ?: p720.minByOrNull { it.bandwidth }
                        ?: pool.minByOrNull { maxOf(it.width, it.height) }
                        ?: pool.first()
                }
                "1080p", "high", "high_1080" -> {
                    p1080.maxByOrNull { it.bandwidth }
                        ?: p720.maxByOrNull { it.bandwidth }
                        ?: pool.maxByOrNull { maxOf(it.width, it.height) }
                        ?: pool.first()
                }
                "original" -> {
                    pool.maxByOrNull { it.bandwidth }
                        ?: pool.maxByOrNull { maxOf(it.width, it.height) }
                        ?: pool.first()
                }
                else -> { // "720p" (Default / Balanced)
                    p720.maxByOrNull { it.bandwidth }
                        ?: p1080.minByOrNull { it.bandwidth }
                        ?: p480.maxByOrNull { it.bandwidth }
                        ?: pool.first()
                }
            }

            val chosenUrl = chosenVariant.uri
            Log.i(TAG, "[VisualMedia] Selected Apple Music rendition for ($preferredResolution): ${chosenVariant.width}x${chosenVariant.height} (${chosenVariant.codecs}, ${chosenVariant.bandwidth / 1000} kbps) -> $chosenUrl")
            chosenUrl
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve rendition ladder, using master: ${e.message}")
            masterUrl
        }
    }

    private fun resolveAgainst(baseUrl: String, uri: String): String {
        return when {
            uri.startsWith("http://") || uri.startsWith("https://") -> uri
            uri.startsWith("/") -> {
                val schemeEnd = baseUrl.indexOf("://").takeIf { it >= 0 }?.plus(3) ?: 0
                val root = baseUrl.indexOf('/', schemeEnd).takeIf { it >= 0 } ?: baseUrl.length
                baseUrl.substring(0, root) + uri
            }
            else -> baseUrl.substringBeforeLast('/', "") + "/" + uri
        }
    }

    private val inMemoryArtistImageCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun cleanArtistName(raw: String): String {
        var s = CLEAN_PARENS_REGEX.matcher(raw).replaceAll("")
        s = CLEAN_BRACKETS_REGEX.matcher(s).replaceAll("")
        s = CLEAN_FEAT_REGEX.matcher(s).replaceAll("")
        s = s.split(Regex("[,&/]")).firstOrNull()?.trim() ?: s
        return s.trim()
    }

    private sealed class ArtistQueryResult {
        data class Found(val url: String) : ArtistQueryResult()
        object NotFound : ArtistQueryResult()
        object AuthError : ArtistQueryResult()
        object NetworkError : ArtistQueryResult()
    }

    private suspend fun fetchArtistMediaWithRetry(
        context: Context,
        cleanName: String,
        extendEditorialVideo: Boolean,
        extractor: (JSONObject) -> String?
    ): Pair<String?, Boolean> {
        var token = getDeveloperToken(context)
        if (isTokenExpired(token)) {
            token = scrapeFreshToken(context) ?: token
        }
        if (token.isBlank()) return null to true

        var hadError = false
        var refreshed = false
        for (sf in listOf("us", "in")) {
            var res = queryArtistCatalog(sf, cleanName, token, extendEditorialVideo, extractor)
            if (res is ArtistQueryResult.AuthError && !refreshed) {
                refreshed = true
                val fresh = scrapeFreshToken(context)
                if (!fresh.isNullOrBlank() && fresh != token) {
                    token = fresh
                    res = queryArtistCatalog(sf, cleanName, token, extendEditorialVideo, extractor)
                }
            }
            when (res) {
                is ArtistQueryResult.Found -> return res.url to false
                is ArtistQueryResult.AuthError, is ArtistQueryResult.NetworkError -> hadError = true
                is ArtistQueryResult.NotFound -> Unit
            }
        }
        return null to hadError
    }

    suspend fun getArtistMotionUrl(context: Context, rawArtistName: String, forcedResolution: String? = null): String? = withContext(Dispatchers.IO) {
        val artworkPrefs = com.musicplayer.lytrix.artwork.ArtworkPreferencesManager.getInstance(context)
        if (!artworkPrefs.appleArtistVideos.value) {
            return@withContext null
        }

        val cleanName = cleanArtistName(rawArtistName)
        if (cleanName.isBlank()) return@withContext null

        val preferredResolution = forcedResolution ?: artworkPrefs.appleArtistVideoResolution.value
        val cacheKey = "artist_v4_${cleanName.lowercase(Locale.ROOT)}_$preferredResolution"
        inMemoryArtistCache[cacheKey]?.let {
            return@withContext if (it.isEmpty()) null else it
        }

        val prefs = context.getSharedPreferences("apple_artist_motion_cache_v4", Context.MODE_PRIVATE)
        if (prefs.contains(cacheKey)) {
            val cached = prefs.getString(cacheKey, null)
            inMemoryArtistCache[cacheKey] = cached ?: ""
            return@withContext cached?.ifEmpty { null }
        }

        val (motionUrl, hadError) = fetchArtistMediaWithRetry(context, cleanName, extendEditorialVideo = true) { attrs ->
            val editorialVideo = attrs.optJSONObject("editorialVideo") ?: return@fetchArtistMediaWithRetry null
            // Prioritize 1:1 square for 400dp header to avoid 200% zoom blur, then widescreen, then fullscreen
            editorialVideo.optJSONObject("motionArtistSquare1x1")?.optString("video")?.takeIf { it.isNotBlank() }
                ?: editorialVideo.optJSONObject("motionArtistWide16x9")?.optString("video")?.takeIf { it.isNotBlank() }
                ?: editorialVideo.optJSONObject("motionArtistFullscreen16x9")?.optString("video")?.takeIf { it.isNotBlank() }
        }

        if (!motionUrl.isNullOrBlank()) {
            val resolvedUrl = resolveBestRendition(motionUrl, preferredResolution)
            inMemoryArtistCache[cacheKey] = resolvedUrl
            prefs.edit().putString(cacheKey, resolvedUrl).apply()
            Log.i(TAG, "Found Apple Music Artist Motion for '$cleanName' [$preferredResolution]: $resolvedUrl")
            return@withContext resolvedUrl
        }

        // Only negative-cache when Apple Music responded cleanly with no match (never on token/network errors)
        if (!hadError) {
            inMemoryArtistCache[cacheKey] = ""
            prefs.edit().putString(cacheKey, "").apply()
        }
        null
    }

    suspend fun getArtistImageUrl(context: Context, rawArtistName: String): String? = withContext(Dispatchers.IO) {
        val artworkPrefs = com.musicplayer.lytrix.artwork.ArtworkPreferencesManager.getInstance(context)
        if (!artworkPrefs.appleArtistImages.value) {
            return@withContext null
        }

        val cleanName = cleanArtistName(rawArtistName)
        if (cleanName.isBlank()) return@withContext null

        val cacheKey = "artist_img_${cleanName.lowercase(Locale.ROOT)}"
        inMemoryArtistImageCache[cacheKey]?.let {
            return@withContext if (it.isEmpty()) null else it
        }

        val prefs = context.getSharedPreferences("apple_artist_image_cache_v1", Context.MODE_PRIVATE)
        if (prefs.contains(cacheKey)) {
            val cached = prefs.getString(cacheKey, null)
            inMemoryArtistImageCache[cacheKey] = cached ?: ""
            return@withContext cached?.ifEmpty { null }
        }

        val (imgUrl, hadError) = fetchArtistMediaWithRetry(context, cleanName, extendEditorialVideo = false) { attrs ->
            val rawArtworkUrl = attrs.optJSONObject("artwork")?.optString("url")
            if (!rawArtworkUrl.isNullOrBlank()) {
                rawArtworkUrl.replace("{w}", "1200").replace("{h}", "1200").replace("{f}", "jpg")
            } else null
        }

        if (!imgUrl.isNullOrBlank()) {
            inMemoryArtistImageCache[cacheKey] = imgUrl
            prefs.edit().putString(cacheKey, imgUrl).apply()
            Log.i(TAG, "Found Apple Music Artist Image for '$cleanName': $imgUrl")
            return@withContext imgUrl
        }

        if (!hadError) {
            inMemoryArtistImageCache[cacheKey] = ""
            prefs.edit().putString(cacheKey, "").apply()
        }
        null
    }

    private fun queryArtistCatalog(
        storefront: String,
        artistName: String,
        token: String,
        extendEditorialVideo: Boolean,
        extractor: (JSONObject) -> String?
    ): ArtistQueryResult {
        try {
            val encodedTerm = URLEncoder.encode(artistName, "UTF-8")
            val extendParam = if (extendEditorialVideo) "&extend=editorialVideo" else ""
            val url = "https://amp-api.music.apple.com/v1/catalog/$storefront/search?term=$encodedTerm&types=artists$extendParam"
            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .header("Origin", "https://music.apple.com")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (resp.code == 401 || resp.code == 403) return ArtistQueryResult.AuthError
                if (!resp.isSuccessful) return ArtistQueryResult.NetworkError
                val body = resp.body?.string() ?: return ArtistQueryResult.NotFound
                val root = JSONObject(body)
                val artists = root.optJSONObject("results")
                    ?.optJSONObject("artists")
                    ?.optJSONArray("data") ?: return ArtistQueryResult.NotFound

                val cleanTarget = artistName.lowercase(Locale.ROOT)
                val targetTokens = cleanTarget.split(Regex("\\s+")).filter { it.length > 1 }.toSet()

                for (i in 0 until artists.length()) {
                    val art = artists.optJSONObject(i) ?: continue
                    val attrs = art.optJSONObject("attributes") ?: continue
                    val name = attrs.optString("name", "").lowercase(Locale.ROOT)
                    val artTokens = name.split(Regex("\\s+")).filter { it.length > 1 }.toSet()

                    val isMatch = name == cleanTarget ||
                            (targetTokens.isNotEmpty() && targetTokens.intersect(artTokens).size >= targetTokens.size) ||
                            (name.contains(cleanTarget) || cleanTarget.contains(name))

                    if (!isMatch) continue
                    val extracted = extractor(attrs)
                    if (!extracted.isNullOrBlank()) return ArtistQueryResult.Found(extracted)
                }
                return ArtistQueryResult.NotFound
            }
        } catch (e: Exception) {
            Log.w(TAG, "queryArtistCatalog error for '$artistName' ($storefront): ${e.message}")
            return ArtistQueryResult.NetworkError
        }
    }

    fun clearCache() {
        inMemoryCache.clear()
        inMemoryArtistCache.clear()
        inMemoryArtistImageCache.clear()
    }
}
