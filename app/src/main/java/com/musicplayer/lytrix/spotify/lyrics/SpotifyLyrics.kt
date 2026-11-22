package com.musicplayer.lytrix.spotify.lyrics

import android.content.Context
import android.util.Log
import com.musicplayer.lytrix.model.Song
import com.musicplayer.lytrix.spotify.SpotifyAuth
import com.musicplayer.lytrix.spotify.SpotifyPreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

object SpotifyLyrics {
    private const val TAG = "SpotifyLyrics"
    private const val PREFS_NAME = "spotify_lyrics_cache"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val inMemoryCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    suspend fun getLyrics(context: Context, song: Song): String? = withContext(Dispatchers.IO) {
        try {
            if (!SpotifyPreferencesManager.isLoggedIn(context) || !SpotifyPreferencesManager.isLyricsEnabled(context)) {
                return@withContext null
            }

            val title = song.title.trim()
            val artist = song.artist.trim()
            if (title.isBlank()) return@withContext null

            val cacheKey = "${title.lowercase(Locale.US)}_${artist.lowercase(Locale.US)}"
            inMemoryCache[cacheKey]?.let { return@withContext it }

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getString(cacheKey, null)?.let {
                inMemoryCache[cacheKey] = it
                return@withContext it
            }

            val token = SpotifyAuth.ensureValidToken(context) ?: run {
                Log.w(TAG, "No valid Spotify token available for lyrics")
                return@withContext null
            }

            val spotifyTrackId = resolveSpotifyTrackId(token, song) ?: run {
                Log.w(TAG, "Could not resolve Spotify track ID for: $title by $artist")
                return@withContext null
            }

            val lyrics = fetchColorLyrics(token, spotifyTrackId)
            if (!lyrics.isNullOrBlank()) {
                inMemoryCache[cacheKey] = lyrics
                prefs.edit().putString(cacheKey, lyrics).apply()
            }
            lyrics
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Spotify lyrics", e)
            null
        }
    }

    private suspend fun resolveSpotifyTrackId(token: String, song: Song): String? = withContext(Dispatchers.IO) {
        val origId = song.originalId ?: ""
        if (origId.startsWith("spotify_")) {
            val id = origId.removePrefix("spotify_")
            if (id.length >= 15) return@withContext id
        }

        val query = "${song.title} ${song.artist}".trim()
        val foundId = com.musicplayer.lytrix.spotify.SpotifyRepository.searchTrackGql(token, query)
        if (foundId != null) return@withContext foundId

        return@withContext com.musicplayer.lytrix.spotify.SpotifyRepository.searchTrackGql(token, song.title.trim())
    }

    private suspend fun fetchColorLyrics(token: String, trackId: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = "https://spclient.wg.spotify.com/color-lyrics/v2/track/$trackId?format=json&vocalRemoval=false&market=from_token"
            val clientToken = com.musicplayer.lytrix.spotify.canvas.SpotifyCanvas.getClientToken()
            val reqBuilder = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .header("App-platform", "WebPlayer")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            if (!clientToken.isNullOrBlank()) {
                reqBuilder.header("Client-Token", clientToken)
            }
            val request = reqBuilder.build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Color lyrics HTTP error ${response.code} for track $trackId")
                    return@withContext null
                }
                val body = response.body?.string() ?: return@withContext null
                val root = JSONObject(body)
                val lyricsObj = root.optJSONObject("lyrics") ?: return@withContext null
                val linesArr = lyricsObj.optJSONArray("lines") ?: return@withContext null
                val syncType = lyricsObj.optString("syncType", "UNSYNCED")
                val isSynced = syncType.equals("LINE_SYNCED", ignoreCase = true) || syncType.equals("SYLLABLE_SYNCED", ignoreCase = true)

                val sb = StringBuilder()
                for (i in 0 until linesArr.length()) {
                    val line = linesArr.getJSONObject(i)
                    val words = line.optString("words").trim()
                    val startTimeMs = line.optLong("startTimeMs", -1L)

                    if (isSynced && startTimeMs >= 0) {
                        val minutes = startTimeMs / 60000
                        val seconds = (startTimeMs % 60000) / 1000
                        val hundredths = (startTimeMs % 1000) / 10
                        sb.append(String.format(Locale.US, "[%02d:%02d.%02d] %s\n", minutes, seconds, hundredths, words))
                    } else {
                        sb.append(words).append("\n")
                    }
                }
                return@withContext sb.toString().trim().takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching color lyrics for track $trackId", e)
            null
        }
    }
}
