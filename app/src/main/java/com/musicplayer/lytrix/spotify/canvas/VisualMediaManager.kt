package com.musicplayer.lytrix.spotify.canvas

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.musicplayer.lytrix.artwork.ArtworkPreferencesManager
import com.musicplayer.lytrix.artwork.CanvasContentSource
import com.musicplayer.lytrix.artwork.YouTubeVideoCanvasManager
import com.musicplayer.lytrix.artwork.isEnabled
import com.musicplayer.lytrix.model.Song
import com.musicplayer.lytrix.spotify.SpotifyPreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

enum class VisualMediaType {
    SPOTIFY_CANVAS,
    APPLE_MUSIC_MOTION,
    YOUTUBE_MUSIC_VIDEO
}

data class VisualMediaResult(
    val url: String,
    val type: VisualMediaType,
    val clips: List<com.musicplayer.lytrix.artwork.YouTubeCanvasClip> = emptyList()
)

object VisualMediaManager {
    private const val TAG = "VisualMediaManager"
    private val inMemoryCache = java.util.concurrent.ConcurrentHashMap<String, VisualMediaResult>()

    private fun isCellular(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }

    suspend fun getVisualMedia(context: Context, song: Song): VisualMediaResult? = withContext(Dispatchers.IO) {
        // 🔥 Local / Offline songs MUST NEVER show online motion / canvas
        val isLocal = song.filePath !in listOf("ONLINE_LYTRIX", "ONLINE") && !song.uriString.startsWith("http")
        if (isLocal) {
            Log.d(TAG, "[VisualMedia] Ignored local offline song '${song.title}'")
            return@withContext null
        }

        val artworkPrefs = ArtworkPreferencesManager.getInstance(context)
        val isCanvasEnabled = artworkPrefs.canvasEnabled.value
        val isAnimatedCoversEnabled = artworkPrefs.animatedCoversEnabled.value
        val disableOnMobileData = artworkPrefs.disableOnMobileData.value

        // If on cellular data and user wants to save mobile data
        if (disableOnMobileData && isCellular(context)) {
            Log.d(TAG, "[VisualMedia] Cellular data active & disableOnMobileData is enabled -> skipped")
            return@withContext null
        }

        // If neither Canvas nor Animated Covers are enabled, exit immediately
        if (!isCanvasEnabled && !isAnimatedCoversEnabled) {
            Log.d(TAG, "[VisualMedia] All visual media toggles are disabled")
            return@withContext null
        }

        val spotifyPrefs = SpotifyPreferencesManager.getInstance(context)
        val isSpotifyLoggedIn = spotifyPrefs.spDc.first().isNotBlank()

        val contentOrder = artworkPrefs.canvasContentOrder.value
        val enabledSources = artworkPrefs.canvasEnabledSources.value

        // Execute fallback chain according to user-configured priority order (Exact MetroList logic)
        for (source in contentOrder) {
            if (source !in enabledSources || !source.isEnabled(isCanvasEnabled, isAnimatedCoversEnabled)) {
                continue
            }

            when (source) {
                CanvasContentSource.YOUTUBE_MUSIC_VIDEO -> {
                    val ytMode = artworkPrefs.youtubeCanvasMode.value
                    val ytQuality = artworkPrefs.youtubeCanvasQuality.value
                    val ytKey = "yt_${song.id}_${song.originalId ?: ""}_${ytMode}_$ytQuality"
                    inMemoryCache[ytKey]?.let {
                        Log.i(TAG, "[VisualMedia] CACHE HIT -> YouTube Video Canvas | Song: '${song.title}'")
                        return@withContext it
                    }
                    val ytUrl = YouTubeVideoCanvasManager.getVideoCanvasUrl(context, song)
                    if (!ytUrl.isNullOrBlank()) {
                        val clips = if (ytMode == com.musicplayer.lytrix.artwork.YouTubeCanvasMode.SHORT_CLIPS) {
                            val dur = (song.duration).takeIf { it > 10_000L } ?: 180_000L
                            com.musicplayer.lytrix.artwork.youtubeCanvasClips(dur)
                        } else {
                            emptyList()
                        }
                        val res = VisualMediaResult(ytUrl, VisualMediaType.YOUTUBE_MUSIC_VIDEO, clips)
                        inMemoryCache[ytKey] = res
                        Log.i(TAG, "[VisualMedia] MATCH FOUND -> YouTube Video Canvas | Song: '${song.title}' | Clips: ${clips.size} | URL: $ytUrl")
                        return@withContext res
                    }
                }

                CanvasContentSource.APPLE_MUSIC -> {
                    val appleRes = artworkPrefs.animatedCoverResolution.value
                    val appleKey = "apple_${song.id}_${song.originalId ?: ""}_$appleRes"
                    inMemoryCache[appleKey]?.let {
                        Log.i(TAG, "[VisualMedia] CACHE HIT -> Apple Music Animated Artwork | Song: '${song.title}'")
                        return@withContext it
                    }
                    val appleMotionUrl = AppleMusicAnimatedArtwork.getAnimatedArtworkUrl(context, song)
                    if (!appleMotionUrl.isNullOrBlank()) {
                        val res = VisualMediaResult(appleMotionUrl, VisualMediaType.APPLE_MUSIC_MOTION)
                        inMemoryCache[appleKey] = res
                        Log.i(TAG, "[VisualMedia] MATCH FOUND -> Apple Music Animated Artwork | Song: '${song.title}' | URL: $appleMotionUrl")
                        return@withContext res
                    }
                }

                CanvasContentSource.SPOTIFY_CANVAS -> {
                    if (isSpotifyLoggedIn) {
                        val spotifyKey = "spotify_${song.id}_${song.originalId ?: ""}"
                        inMemoryCache[spotifyKey]?.let {
                            Log.i(TAG, "[VisualMedia] CACHE HIT -> Spotify Canvas | Song: '${song.title}'")
                            return@withContext it
                        }
                        val canvasUrl = SpotifyCanvas.getCanvasUrl(context, song)
                        if (!canvasUrl.isNullOrBlank()) {
                            val res = VisualMediaResult(canvasUrl, VisualMediaType.SPOTIFY_CANVAS)
                            inMemoryCache[spotifyKey] = res
                            Log.i(TAG, "[VisualMedia] MATCH FOUND -> Spotify Canvas | Song: '${song.title}' | URL: $canvasUrl")
                            return@withContext res
                        }
                    }
                }
            }
        }

        Log.i(TAG, "[VisualMedia] No visual media matched priority sources for '${song.title}'")
        null
    }

    fun clearCache(context: Context? = null) {
        inMemoryCache.clear()
        AppleMusicAnimatedArtwork.clearCache()
        YouTubeVideoCanvasManager.clearCache()
        CanvasVideoPlayerManager.releaseAll()
        context?.let { CanvasVideoPlayerManager.clearDiskCache(it) }
    }
}
