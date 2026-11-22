package com.musicplayer.lytrix.spotify

import android.content.Context
import android.webkit.CookieManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

val Context.spotifyDataStore: DataStore<Preferences> by preferencesDataStore(name = "spotify_preferences")

class SpotifyPreferencesManager(private val context: Context) {

    companion object {
        val SP_DC_KEY = stringPreferencesKey("spotify_sp_dc")
        val SP_KEY_KEY = stringPreferencesKey("spotify_sp_key")
        val ACCESS_TOKEN_KEY = stringPreferencesKey("spotify_access_token")
        val TOKEN_EXPIRY_KEY = longPreferencesKey("spotify_token_expiry")
        val ACCOUNT_NAME_KEY = stringPreferencesKey("spotify_account_name")
        val ACCOUNT_ID_KEY = stringPreferencesKey("spotify_account_id")
        val ACCOUNT_AVATAR_KEY = stringPreferencesKey("spotify_account_avatar")
        val PRODUCT_KEY = stringPreferencesKey("spotify_product")

        val LYRICS_ENABLED_KEY = booleanPreferencesKey("spotify_lyrics_enabled")
        val CANVAS_ENABLED_KEY = booleanPreferencesKey("spotify_canvas_enabled")
        val ANIMATED_ARTWORK_KEY = booleanPreferencesKey("spotify_animated_artwork")
        val PLAYLISTS_ENABLED_KEY = booleanPreferencesKey("spotify_playlists_enabled")
        val VIVI_CANVAS_ENABLED_KEY = booleanPreferencesKey("vivi_canvas_enabled")
        val ARTIST_HERO_VIDEO_ENABLED_KEY = booleanPreferencesKey("artist_hero_video_enabled")
        val ARTIST_HERO_VIDEO_RESOLUTION_KEY = stringPreferencesKey("artist_hero_video_resolution")

        const val RESOLUTION_480P = "480p"
        const val RESOLUTION_720P = "720p"
        const val RESOLUTION_1080P = "1080p"
        const val RESOLUTION_ORIGINAL = "original"
        const val DEFAULT_ARTIST_VIDEO_RESOLUTION = RESOLUTION_720P

        @Volatile
        var currentAccessToken: String? = null
            private set

        @Volatile
        var currentSpDc: String? = null
            private set

        @Volatile
        var isSpotifyLoggedIn: Boolean = false
            private set

        @Volatile
        var isLyricsEnabledCached: Boolean = false
            private set

        @Volatile
        var isCanvasEnabledCached: Boolean = false
            private set

        @Volatile
        var isAnimatedArtworkEnabledCached: Boolean = false
            private set

        @Volatile
        var isViviCanvasEnabledCached: Boolean = false
            private set

        @Volatile
        var isArtistHeroVideoEnabledCached: Boolean = false
            private set

        @Volatile
        var artistHeroVideoResolutionCached: String = DEFAULT_ARTIST_VIDEO_RESOLUTION
            private set

        @Volatile
        var isPlaylistsEnabledCached: Boolean = true
            private set

        @Volatile
        private var instance: SpotifyPreferencesManager? = null

        fun getInstance(context: Context): SpotifyPreferencesManager {
            return instance ?: synchronized(this) {
                instance ?: SpotifyPreferencesManager(context.applicationContext).also { instance = it }
            }
        }

        fun isLoggedIn(context: Context): Boolean {
            getInstance(context)
            return isSpotifyLoggedIn
        }

        fun isLyricsEnabled(context: Context): Boolean {
            getInstance(context)
            return isLyricsEnabledCached
        }

        fun isCanvasEnabled(context: Context): Boolean {
            getInstance(context)
            return isCanvasEnabledCached
        }

        fun isAnimatedArtworkEnabled(context: Context): Boolean {
            getInstance(context)
            return isAnimatedArtworkEnabledCached
        }

        fun isViviCanvasEnabled(context: Context): Boolean {
            getInstance(context)
            return isViviCanvasEnabledCached
        }

        fun isArtistHeroVideoEnabled(context: Context): Boolean {
            getInstance(context)
            return isArtistHeroVideoEnabledCached
        }

        fun getArtistHeroVideoResolution(context: Context): String {
            getInstance(context)
            return artistHeroVideoResolutionCached
        }

        fun isPlaylistsEnabled(context: Context): Boolean {
            getInstance(context)
            return isPlaylistsEnabledCached
        }
    }

    init {
        CoroutineScope(Dispatchers.IO).launch {
            context.spotifyDataStore.data.collect { prefs ->
                val spDc = prefs[SP_DC_KEY].orEmpty()
                currentSpDc = spDc.ifBlank { null }
                currentAccessToken = prefs[ACCESS_TOKEN_KEY]?.ifBlank { null }
                isSpotifyLoggedIn = spDc.isNotBlank()
                isLyricsEnabledCached = prefs[LYRICS_ENABLED_KEY] ?: false
                isCanvasEnabledCached = prefs[CANVAS_ENABLED_KEY] ?: false
                isAnimatedArtworkEnabledCached = prefs[ANIMATED_ARTWORK_KEY] ?: false
                isViviCanvasEnabledCached = prefs[VIVI_CANVAS_ENABLED_KEY] ?: false
                isArtistHeroVideoEnabledCached = prefs[ARTIST_HERO_VIDEO_ENABLED_KEY] ?: false
                artistHeroVideoResolutionCached = prefs[ARTIST_HERO_VIDEO_RESOLUTION_KEY] ?: DEFAULT_ARTIST_VIDEO_RESOLUTION
                isPlaylistsEnabledCached = prefs[PLAYLISTS_ENABLED_KEY] ?: true
            }
        }
    }

    val spDc: Flow<String> = context.spotifyDataStore.data.map { it[SP_DC_KEY] ?: "" }
    val spKey: Flow<String> = context.spotifyDataStore.data.map { it[SP_KEY_KEY] ?: "" }
    val accessToken: Flow<String> = context.spotifyDataStore.data.map { it[ACCESS_TOKEN_KEY] ?: "" }
    val tokenExpiry: Flow<Long> = context.spotifyDataStore.data.map { it[TOKEN_EXPIRY_KEY] ?: 0L }
    val accountName: Flow<String> = context.spotifyDataStore.data.map { it[ACCOUNT_NAME_KEY] ?: "" }
    val accountId: Flow<String> = context.spotifyDataStore.data.map { it[ACCOUNT_ID_KEY] ?: "" }
    val accountAvatarUrl: Flow<String> = context.spotifyDataStore.data.map { it[ACCOUNT_AVATAR_KEY] ?: "" }
    val product: Flow<String> = context.spotifyDataStore.data.map { it[PRODUCT_KEY] ?: "" }

    val isLoggedIn: Flow<Boolean> = context.spotifyDataStore.data.map {
        (it[SP_DC_KEY] ?: "").isNotBlank()
    }

    val lyricsEnabled: Flow<Boolean> = context.spotifyDataStore.data.map { it[LYRICS_ENABLED_KEY] ?: false }
    val canvasEnabled: Flow<Boolean> = context.spotifyDataStore.data.map { it[CANVAS_ENABLED_KEY] ?: false }
    val animatedArtworkEnabled: Flow<Boolean> = context.spotifyDataStore.data.map { it[ANIMATED_ARTWORK_KEY] ?: false }
    val viviCanvasEnabled: Flow<Boolean> = context.spotifyDataStore.data.map { it[VIVI_CANVAS_ENABLED_KEY] ?: false }
    val artistHeroVideoEnabled: Flow<Boolean> = context.spotifyDataStore.data.map { it[ARTIST_HERO_VIDEO_ENABLED_KEY] ?: false }
    val artistHeroVideoResolution: Flow<String> = context.spotifyDataStore.data.map { it[ARTIST_HERO_VIDEO_RESOLUTION_KEY] ?: DEFAULT_ARTIST_VIDEO_RESOLUTION }
    val playlistsEnabled: Flow<Boolean> = context.spotifyDataStore.data.map { it[PLAYLISTS_ENABLED_KEY] ?: true }

    suspend fun saveAuth(
        spDc: String,
        spKey: String = "",
        accessToken: String = "",
        expiryMs: Long = 0L,
        name: String = "",
        id: String = "",
        avatar: String = "",
        product: String = "",
    ) {
        currentSpDc = spDc.ifBlank { null }
        currentAccessToken = accessToken.ifBlank { null }
        isSpotifyLoggedIn = spDc.isNotBlank()

        context.spotifyDataStore.edit { prefs ->
            prefs[SP_DC_KEY] = spDc
            if (spKey.isNotBlank()) prefs[SP_KEY_KEY] = spKey
            if (accessToken.isNotBlank()) prefs[ACCESS_TOKEN_KEY] = accessToken
            if (expiryMs > 0L) prefs[TOKEN_EXPIRY_KEY] = expiryMs
            if (name.isNotBlank()) prefs[ACCOUNT_NAME_KEY] = name
            if (id.isNotBlank()) prefs[ACCOUNT_ID_KEY] = id
            if (avatar.isNotBlank()) prefs[ACCOUNT_AVATAR_KEY] = avatar
            if (product.isNotBlank()) prefs[PRODUCT_KEY] = product
        }
    }

    suspend fun updateAccessToken(token: String, expiryMs: Long) {
        currentAccessToken = token.ifBlank { null }
        context.spotifyDataStore.edit { prefs ->
            prefs[ACCESS_TOKEN_KEY] = token
            prefs[TOKEN_EXPIRY_KEY] = expiryMs
        }
    }

    suspend fun updateProfile(name: String, avatar: String, id: String = "", product: String = "") {
        context.spotifyDataStore.edit { prefs ->
            if (name.isNotBlank()) prefs[ACCOUNT_NAME_KEY] = name
            if (avatar.isNotBlank()) prefs[ACCOUNT_AVATAR_KEY] = avatar
            if (id.isNotBlank()) prefs[ACCOUNT_ID_KEY] = id
            if (product.isNotBlank()) prefs[PRODUCT_KEY] = product
        }
    }

    suspend fun setLyricsEnabled(enabled: Boolean) {
        isLyricsEnabledCached = enabled
        context.spotifyDataStore.edit { it[LYRICS_ENABLED_KEY] = enabled }
        if (!enabled) {
            try {
                context.getSharedPreferences("spotify_lyrics_cache", Context.MODE_PRIVATE).edit().clear().apply()
                context.getSharedPreferences("lyrics_prefs", Context.MODE_PRIVATE).edit().clear().apply()
            } catch (_: Exception) {}
        }
    }

    suspend fun setCanvasEnabled(enabled: Boolean) {
        isCanvasEnabledCached = enabled
        com.musicplayer.lytrix.spotify.canvas.VisualMediaManager.clearCache()
        context.spotifyDataStore.edit { it[CANVAS_ENABLED_KEY] = enabled }
    }

    suspend fun setAnimatedArtworkEnabled(enabled: Boolean) {
        isAnimatedArtworkEnabledCached = enabled
        com.musicplayer.lytrix.spotify.canvas.VisualMediaManager.clearCache()
        try {
            context.getSharedPreferences("apple_motion_cache", Context.MODE_PRIVATE).edit().clear().apply()
        } catch (_: Exception) {}
        context.spotifyDataStore.edit { it[ANIMATED_ARTWORK_KEY] = enabled }
    }

    suspend fun setViviCanvasEnabled(enabled: Boolean) {
        isViviCanvasEnabledCached = enabled
        com.musicplayer.lytrix.spotify.canvas.VisualMediaManager.clearCache()
        try {
            context.getSharedPreferences("vivimusic_canvas_prefs", Context.MODE_PRIVATE).edit().clear().apply()
        } catch (_: Exception) {}
        context.spotifyDataStore.edit { it[VIVI_CANVAS_ENABLED_KEY] = enabled }
    }

    suspend fun setArtistHeroVideoEnabled(enabled: Boolean) {
        isArtistHeroVideoEnabledCached = enabled
        try {
            context.getSharedPreferences("apple_artist_motion_cache", Context.MODE_PRIVATE).edit().clear().apply()
            context.getSharedPreferences("apple_artist_motion_cache_v4", Context.MODE_PRIVATE).edit().clear().apply()
        } catch (_: Exception) {}
        context.spotifyDataStore.edit { it[ARTIST_HERO_VIDEO_ENABLED_KEY] = enabled }
    }

    suspend fun setArtistHeroVideoResolution(resolution: String) {
        artistHeroVideoResolutionCached = resolution
        try {
            com.musicplayer.lytrix.spotify.canvas.AppleMusicAnimatedArtwork.clearCache()
            com.musicplayer.lytrix.spotify.canvas.VisualMediaManager.clearCache(context)
            context.getSharedPreferences("apple_artist_motion_cache", Context.MODE_PRIVATE).edit().clear().apply()
            context.getSharedPreferences("apple_artist_motion_cache_v4", Context.MODE_PRIVATE).edit().clear().apply()
        } catch (_: Exception) {}
        context.spotifyDataStore.edit { it[ARTIST_HERO_VIDEO_RESOLUTION_KEY] = resolution }
    }

    suspend fun setPlaylistsEnabled(enabled: Boolean) {
        context.spotifyDataStore.edit { it[PLAYLISTS_ENABLED_KEY] = enabled }
    }

    suspend fun clearAuthData() {
        currentAccessToken = null
        currentSpDc = null
        isSpotifyLoggedIn = false

        context.spotifyDataStore.edit { prefs ->
            prefs.remove(SP_DC_KEY)
            prefs.remove(SP_KEY_KEY)
            prefs.remove(ACCESS_TOKEN_KEY)
            prefs.remove(TOKEN_EXPIRY_KEY)
            prefs.remove(ACCOUNT_NAME_KEY)
            prefs.remove(ACCOUNT_ID_KEY)
            prefs.remove(ACCOUNT_AVATAR_KEY)
            prefs.remove(PRODUCT_KEY)
        }

        try {
            val cookieManager = CookieManager.getInstance()
            val spotifyCookies = cookieManager.getCookie("https://open.spotify.com")
            if (!spotifyCookies.isNullOrBlank()) {
                spotifyCookies.split(";").forEach { cookie ->
                    val name = cookie.substringBefore("=").trim()
                    cookieManager.setCookie("https://open.spotify.com", "$name=; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/; Domain=.spotify.com")
                    cookieManager.setCookie("https://accounts.spotify.com", "$name=; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/; Domain=.spotify.com")
                }
                cookieManager.flush()
            }
        } catch (_: Exception) {}
    }
}
