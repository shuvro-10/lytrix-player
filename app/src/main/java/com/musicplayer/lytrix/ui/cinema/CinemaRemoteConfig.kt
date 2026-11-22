package com.musicplayer.lytrix.ui.cinema

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Remote OTA Configuration Manager for Cinema streaming servers.
 * Fetches updated server domains from GitHub or remote config over the air.
 * Falls back safely to built-in stable defaults if offline or network fails.
 */
object CinemaRemoteConfig {

    private const val PREFS_NAME = "lytrix_cinema_remote_config"
    private const val KEY_VIDLINK_URL = "url_vidlink"
    private const val KEY_VIDSRC_URL = "url_vidsrc"
    private const val KEY_2EMBED_URL = "url_2embed"
    private const val KEY_LAST_FETCH = "last_fetch_timestamp"

    // Default stable fallback endpoints
    private const val DEFAULT_VIDLINK = "https://vidlink.pro"
    private const val DEFAULT_VIDSRC = "https://vidsrc.sh"
    private const val DEFAULT_2EMBED = "https://www.2embed.cc"

    // Public Raw Config URL (Can be pointed to user's GitHub repo or Gist)
    private const val REMOTE_CONFIG_URL =
        "https://raw.githubusercontent.com/sujan-lytrix/cinema-config/main/servers.json"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    @Volatile
    private var cachedVidLink = DEFAULT_VIDLINK
    @Volatile
    private var cachedVidSrc = DEFAULT_VIDSRC
    @Volatile
    private var cached2Embed = DEFAULT_2EMBED

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        cachedVidLink = prefs.getString(KEY_VIDLINK_URL, DEFAULT_VIDLINK) ?: DEFAULT_VIDLINK
        cachedVidSrc = prefs.getString(KEY_VIDSRC_URL, DEFAULT_VIDSRC) ?: DEFAULT_VIDSRC
        cached2Embed = prefs.getString(KEY_2EMBED_URL, DEFAULT_2EMBED) ?: DEFAULT_2EMBED
    }

    fun getVidLinkBase(): String = cachedVidLink
    fun getVidSrcBase(): String = cachedVidSrc
    fun get2EmbedBase(): String = cached2Embed

    /**
     * Checks for updated domains in background without blocking app startup.
     * Caches for 6 hours so it never spams GitHub.
     */
    suspend fun syncRemoteConfig(context: Context) = withContext(Dispatchers.IO) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val lastFetch = prefs.getLong(KEY_LAST_FETCH, 0L)
            val now = System.currentTimeMillis()
            // Check once every 6 hours
            if (now - lastFetch < 6 * 3600 * 1000L) {
                return@withContext
            }

            val request = Request.Builder()
                .url(REMOTE_CONFIG_URL)
                .header("User-Agent", "LytrixPlayer/1.0")
                .header("Cache-Control", "no-cache")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext
                val body = response.body?.string() ?: return@withContext
                val json = JSONObject(body)

                val vidlink = json.optString("vidlink", DEFAULT_VIDLINK)
                val vidsrc = json.optString("vidsrc", DEFAULT_VIDSRC)
                val twoembed = json.optString("twoembed", DEFAULT_2EMBED)

                cachedVidLink = vidlink
                cachedVidSrc = vidsrc
                cached2Embed = twoembed

                prefs.edit()
                    .putString(KEY_VIDLINK_URL, vidlink)
                    .putString(KEY_VIDSRC_URL, vidsrc)
                    .putString(KEY_2EMBED_URL, twoembed)
                    .putLong(KEY_LAST_FETCH, now)
                    .apply()
            }
        } catch (_: Exception) {
            // Silently ignore network hiccup and keep using cached defaults
        }
    }
}
