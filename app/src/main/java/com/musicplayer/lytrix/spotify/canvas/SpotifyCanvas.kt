package com.musicplayer.lytrix.spotify.canvas

import android.content.Context
import android.util.Log
import com.musicplayer.lytrix.model.Song
import com.musicplayer.lytrix.spotify.SpotifyAuth
import com.musicplayer.lytrix.spotify.SpotifyPreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.TimeUnit

@Serializable
data class SpotifyCanvasRequestBody(
    val tracks: List<TrackReq>
) {
    @Serializable
    data class TrackReq(
        val track_uri: String
    )
}

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class SpotifyCanvasResponse(
    @ProtoNumber(1)
    val canvases: List<CanvasItem> = emptyList()
) {
    @Serializable
    data class CanvasItem(
        @ProtoNumber(1)
        val id: String = "",
        @ProtoNumber(2)
        val canvas_url: String = "",
        @ProtoNumber(5)
        val track_uri: String = "",
        @ProtoNumber(11)
        val canvas_uri: String = ""
    )
}

object SpotifyCanvas {
    private const val TAG = "SpotifyCanvas"
    private const val PREFS_NAME = "spotify_canvas_cache"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    private val inMemoryCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val CANVAS_URL_REGEX = Regex("""https://canvaz\.scdn\.co/[^\s"'\x00-\x1F<>]+\.mp4""")

    @Volatile
    private var cachedClientToken: String? = null
    @Volatile
    private var clientTokenExpiryTimeMs: Long = 0L

    fun getClientTokenSync(): String? {
        val now = System.currentTimeMillis()
        if (!cachedClientToken.isNullOrBlank() && now < clientTokenExpiryTimeMs) {
            return cachedClientToken
        }
        return null
    }

    /**
     * Obtains or refreshes Spotify's official Client-Token (matching SimpMusic).
     * Valid for 2 hours, automatically renewed in the background.
     */
    suspend fun getClientToken(): String? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!cachedClientToken.isNullOrBlank() && now < clientTokenExpiryTimeMs) {
            return@withContext cachedClientToken
        }

        try {
            val clientBodyJson = JSONObject().apply {
                put("client_data", JSONObject().apply {
                    put("client_version", "1.2.62.476.g2ad6e7f3")
                    put("client_id", "d8a5ed958d274c2e8ee717e6a4b0971d")
                    put("js_sdk_data", JSONObject().apply {
                        put("device_brand", "Apple")
                        put("device_model", "unknown")
                        put("os", "macos")
                        put("os_version", "10.15.7")
                        put("device_id", "4fd0c748-b282-4927-9658-6d51a24e58b7")
                        put("device_type", "computer")
                    })
                })
            }

            val conn = URL("https://clienttoken.spotify.com/v1/clienttoken").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.doOutput = true
            // Spotify strictly requires Content-Type to be 'application/json' without any 'charset' parameter
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36 Edg/135.0.0.0")

            val jsonBytes = clientBodyJson.toString().toByteArray(Charsets.UTF_8)
            conn.outputStream.use { os ->
                os.write(jsonBytes)
            }

            val code = conn.responseCode
            if (code in 200..299) {
                val respStr = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(respStr)
                val granted = json.optJSONObject("granted_token")
                val token = granted?.optString("token")
                val expiresAfter = granted?.optLong("expires_after_seconds", 7200L) ?: 7200L
                if (!token.isNullOrBlank()) {
                    cachedClientToken = token
                    clientTokenExpiryTimeMs = System.currentTimeMillis() + (expiresAfter - 60) * 1000L
                    Log.i(TAG, "Spotify client token obtained successfully (len=${token.length}, expires in ${expiresAfter}s)")
                    return@withContext token
                }
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Log.w(TAG, "Spotify client token HTTP error $code: $err")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Spotify client token: ${e.message}", e)
        }
        null
    }

    suspend fun getCanvasUrl(context: Context, song: Song): String? = withContext(Dispatchers.IO) {
        try {
            val isLocal = song.filePath !in listOf("ONLINE_LYTRIX", "ONLINE") && !song.uriString.startsWith("http")
            if (isLocal) {
                Log.d(TAG, "Skipping local song: ${song.title}")
                return@withContext null
            }

            val prefsMgr = SpotifyPreferencesManager.getInstance(context)
            val spDc = prefsMgr.spDc.first()
            val canvasEnabled = com.musicplayer.lytrix.artwork.ArtworkPreferencesManager.getInstance(context).canvasEnabled.value
            Log.i(TAG, "getCanvasUrl called for '${song.title}' by '${song.artist}' | spDcPresent=${spDc.isNotBlank()}, canvasEnabled=$canvasEnabled")

            if (spDc.isBlank()) {
                Log.w(TAG, "Spotify not logged in (sp_dc is empty)")
                return@withContext null
            }
            if (!canvasEnabled) {
                Log.w(TAG, "Spotify Canvas toggle is OFF in Settings")
                return@withContext null
            }

            val title = song.title.trim()
            val artist = song.artist.trim()
            if (title.isBlank()) return@withContext null

            val cacheKey = "${title.lowercase(Locale.US)}_${artist.lowercase(Locale.US)}"
            inMemoryCache[cacheKey]?.let {
                Log.i(TAG, "Canvas found in memory cache for '$title': $it")
                return@withContext it
            }

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getString(cacheKey, null)?.let {
                Log.i(TAG, "Canvas found in disk cache for '$title': $it")
                inMemoryCache[cacheKey] = it
                return@withContext it
            }

            var token = SpotifyAuth.ensureValidToken(context)
            if (token.isNullOrBlank()) {
                Log.i(TAG, "Token invalid or expired, force-refreshing mobile-web-player token...")
                token = SpotifyAuth.ensureValidToken(context, forceRefresh = true)
            }
            if (token.isNullOrBlank()) {
                Log.e(TAG, "No valid Spotify token available for canvas")
                return@withContext null
            }

            val spotifyTrackId = resolveSpotifyTrackId(token, song) ?: run {
                Log.w(TAG, "Could not resolve Spotify track ID for canvas: '$title' by '$artist'")
                return@withContext null
            }
            Log.i(TAG, "Resolved Spotify track ID: $spotifyTrackId for '$title'")

            var canvasUrl = fetchCanvasByTrackId(token, spotifyTrackId)
            if (canvasUrl.isNullOrBlank()) {
                Log.i(TAG, "Canvas not found with initial token, retrying with fresh mobile-web-player token...")
                val freshToken = SpotifyAuth.ensureValidToken(context, forceRefresh = true)
                if (!freshToken.isNullOrBlank()) {
                    canvasUrl = fetchCanvasByTrackId(freshToken, spotifyTrackId)
                }
            }
            if (!canvasUrl.isNullOrBlank()) {
                inMemoryCache[cacheKey] = canvasUrl
                prefs.edit().putString(cacheKey, canvasUrl).apply()
                Log.i(TAG, "SUCCESS: Found and cached Spotify Canvas for '$title': $canvasUrl")
            } else {
                Log.w(TAG, "No Spotify Canvas exists on Spotify servers for track $spotifyTrackId ('$title')")
            }
            canvasUrl
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Spotify canvas for ${song.title}", e)
            null
        }
    }

    private suspend fun resolveSpotifyTrackId(token: String, song: Song): String? = withContext(Dispatchers.IO) {
        val origId = song.originalId ?: ""
        if (origId.startsWith("spotify_")) {
            val id = origId.removePrefix("spotify_")
            if (id.length >= 15) return@withContext id
        }

        // Clean query matching SimpMusic:
        val cleanSongTitle = song.title
            .replace(Regex("""\((feat\.|ft\.|official|video|audio|lyrics).*?\)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\[(feat\.|ft\.|official|video|audio|lyrics).*?\]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex(""" (feat|ft)\.?\s+.*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""[()\[\].]"""), " ")
            .trim()

        val primaryArtist = song.artist.split(",", "&", "/").firstOrNull()?.trim() ?: song.artist.trim()
        val query = "$cleanSongTitle $primaryArtist".trim()

        Log.i(TAG, "Resolving track ID for query: '$query'")
        val foundId = com.musicplayer.lytrix.spotify.SpotifyRepository.searchTrackGql(token, query)
        if (foundId != null) return@withContext foundId

        // Try title-artist if hyphenated (e.g. 'Sabrina Carpenter - Espresso')
        if (cleanSongTitle.contains(" - ")) {
            val after = cleanSongTitle.substringAfter(" - ").trim()
            val before = cleanSongTitle.substringBefore(" - ").trim()
            val hyphenQuery = "$after $before".trim()
            Log.i(TAG, "Trying hyphen-swapped query: '$hyphenQuery'")
            val idHyphen = com.musicplayer.lytrix.spotify.SpotifyRepository.searchTrackGql(token, hyphenQuery)
            if (idHyphen != null) return@withContext idHyphen
        }

        Log.i(TAG, "Trying clean title only: '$cleanSongTitle'")
        return@withContext com.musicplayer.lytrix.spotify.SpotifyRepository.searchTrackGql(token, cleanSongTitle)
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun fetchCanvasByTrackId(token: String, trackId: String): String? = withContext(Dispatchers.IO) {
        val cleanTrackId = trackId.removePrefix("spotify:track:").trim()
        var clientToken = getClientToken()
        if (clientToken.isNullOrBlank()) {
            Log.w(TAG, "Client-Token is empty, clearing cache and refetching immediately...")
            cachedClientToken = null
            clientToken = getClientToken()
        }

        try {
            val trackUri = "spotify:track:$cleanTrackId"
            val reqProto = SpotifyCanvasRequestBody(
                tracks = listOf(SpotifyCanvasRequestBody.TrackReq(track_uri = trackUri))
            )
            val requestBytes = try {
                ProtoBuf.encodeToByteArray(SpotifyCanvasRequestBody.serializer(), reqProto)
            } catch (e: Exception) {
                val uriBytes = trackUri.toByteArray(Charsets.UTF_8)
                val trackReqBytes = byteArrayOf(0x0A, uriBytes.size.toByte()) + uriBytes
                byteArrayOf(0x0A, trackReqBytes.size.toByte()) + trackReqBytes
            }

            val mediaType = "application/x-protobuf".toMediaTypeOrNull()
            val requestBody = requestBytes.toRequestBody(mediaType)

            val url = "https://spclient.wg.spotify.com/canvaz-cache/v0/canvases"
            val reqBuilder = Request.Builder()
                .url(url)
                .post(requestBody)
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/x-protobuf")
                .header("Accept", "application/x-protobuf, application/protobuf")
                .header("User-Agent", "Spotify/9.0.34.593 iOS/18.4 (iPhone15,3)")

            if (!clientToken.isNullOrBlank()) {
                reqBuilder.header("Client-Token", clientToken)
            }

            Log.i(TAG, "Sending canvaz-cache request for $trackUri (token len=${token.length}, clientTokenPresent=${!clientToken.isNullOrBlank()})")

            httpClient.newCall(reqBuilder.build()).execute().use { response ->
                Log.i(TAG, "canvaz-cache HTTP code: ${response.code}")
                if (!response.isSuccessful) {
                    Log.w(TAG, "Canvas HTTP error ${response.code} for track $cleanTrackId")
                    if (response.code == 401 || response.code == 403) {
                        cachedClientToken = null
                    }
                    return@withContext null
                }
                val bytes = response.body?.bytes() ?: return@withContext null
                val contentEncoding = response.header("Content-Encoding")
                Log.i(TAG, "canvaz-cache response size: ${bytes.size} bytes (encoding: $contentEncoding)")

                var canvasUrl: String? = null
                try {
                    val decoded = ProtoBuf.decodeFromByteArray(SpotifyCanvasResponse.serializer(), bytes)
                    canvasUrl = decoded.canvases.firstOrNull()?.canvas_url?.takeIf { it.isNotBlank() }
                    if (canvasUrl != null) {
                        Log.i(TAG, "Decoded canvas URL from protobuf: $canvasUrl")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Protobuf decode failed: ${e.message}, trying regex fallback")
                }
                if (canvasUrl.isNullOrBlank()) {
                    val rawString = String(bytes, Charsets.ISO_8859_1)
                    canvasUrl = CANVAS_URL_REGEX.find(rawString)?.value
                    if (canvasUrl != null) {
                        Log.i(TAG, "Found canvas URL via Regex fallback: $canvasUrl")
                    }
                }
                if (!canvasUrl.isNullOrBlank()) {
                    Log.i(TAG, "Successfully extracted Spotify Canvas for $cleanTrackId: $canvasUrl")
                } else {
                    Log.w(TAG, "No Canvas URL in response for track $cleanTrackId (len=${bytes.size})")
                }
                return@withContext canvasUrl
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching canvas bytes for track $cleanTrackId", e)
            null
        }
    }

}
