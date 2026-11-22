package com.musicplayer.lytrix.spotify

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves Spotify track metadata (Title, Artist) to a playable YouTube Video ID
 * with in-memory and persistent SharedPreferences caching.
 */
object SpotifyTrackResolver {
    private val memoryCache = ConcurrentHashMap<String, String>()
    private const val PREFS_NAME = "spotify_yt_matches"

    suspend fun resolveToYouTubeId(
        context: Context,
        title: String,
        artist: String,
        spotifyTrackId: String
    ): String = withContext(Dispatchers.IO) {
        val cleanSpotifyId = spotifyTrackId.removePrefix("spotify_").trim()

        // 1. In-memory LRU cache
        memoryCache[cleanSpotifyId]?.let { return@withContext it }

        // 2. Persistent cache
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cached = prefs.getString(cleanSpotifyId, null)
        if (!cached.isNullOrBlank()) {
            memoryCache[cleanSpotifyId] = cached
            return@withContext cached
        }

        // 3. Search via InnerTube (YouTube)
        val query = "$title $artist"
        Timber.d("SpotifyTrackResolver: Resolving '$query' to YouTube ID...")

        val ytId = try {
            val searchResult = com.musicplayer.lytrix.innertube.YouTube.search(
                query,
                com.musicplayer.lytrix.innertube.YouTube.SearchFilter.FILTER_SONG
            ).getOrNull()
            val songItem = searchResult?.items?.firstOrNull()
            songItem?.id
        } catch (e: Exception) {
            Timber.w(e, "SpotifyTrackResolver: InnerTube search failed: ${e.message}")
            null
        } ?: try {
            // Fallback to NewPipe search
            val searchExtractor = org.schabi.newpipe.extractor.ServiceList.YouTube.getSearchExtractor(query)
            searchExtractor.fetchPage()
            val firstItem = searchExtractor.initialPage.items.firstOrNull()
            if (firstItem is org.schabi.newpipe.extractor.stream.StreamInfoItem) {
                firstItem.url.substringAfter("v=").substringBefore("&")
            } else null
        } catch (e: Exception) {
            Timber.w(e, "SpotifyTrackResolver: NewPipe search failed: ${e.message}")
            null
        }

        val resolvedId = ytId ?: cleanSpotifyId
        if (ytId != null) {
            memoryCache[cleanSpotifyId] = ytId
            prefs.edit().putString(cleanSpotifyId, ytId).apply()
            Timber.d("SpotifyTrackResolver: Match found! '$query' -> $ytId")
        }

        resolvedId
    }
}
