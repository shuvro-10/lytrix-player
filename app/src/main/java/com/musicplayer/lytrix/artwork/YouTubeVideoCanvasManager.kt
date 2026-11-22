package com.musicplayer.lytrix.artwork

import android.content.Context
import android.util.Log
import com.musicplayer.lytrix.innertube.MusicVideoMatcher
import com.musicplayer.lytrix.innertube.YouTube
import com.musicplayer.lytrix.innertube.models.SongItem
import com.musicplayer.lytrix.model.Song
import com.musicplayer.lytrix.utils.InnerTubeXPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList

object YouTubeVideoCanvasManager {
    private const val TAG = "YouTubeVideoCanvas"
    private val inMemoryCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    suspend fun getVideoCanvasUrl(context: Context, song: Song): String? = withContext(Dispatchers.IO) {
        val title = song.title.trim()
        val artist = song.artist.trim()
        if (title.isBlank()) return@withContext null

        val qualityPref = ArtworkPreferencesManager.getInstance(context).youtubeCanvasQuality.value
        val modePref = ArtworkPreferencesManager.getInstance(context).youtubeCanvasMode.value.name
        val cacheKey = "${song.id}_${song.originalId ?: ""}_${title}_${artist}_${qualityPref}_${modePref}"
        inMemoryCache[cacheKey]?.let { return@withContext it }

        try {
            val rawId = song.originalId?.takeIf { it.isNotBlank() && !it.all(Char::isDigit) }
                ?: song.id.toString().takeIf { !it.all(Char::isDigit) }
                ?: ""

            var targetVideoId: String? = null

            // A. Check authoritative counterpart (/next) + Album ID
            var discoveredAlbumId: String? = null
            if (rawId.isNotBlank()) {
                val (official, albumId) = YouTube.getCounterpartAndAlbum(rawId)
                discoveredAlbumId = albumId
                if (official != null && official.targetVideoId.isNotBlank()) {
                    if (MusicVideoMatcher.isAcceptableVideoType(official.targetMusicVideoType, unknownIsOk = true)) {
                        targetVideoId = official.targetVideoId
                    }
                }
            }

            // B. Direct album shelf browse (MetroList method with album search fallback)
            if (targetVideoId == null && rawId.isNotBlank()) {
                var albumIdToLookup = discoveredAlbumId
                if (albumIdToLookup.isNullOrBlank()) {
                    try {
                        val albumSearchQuery = "${song.album?.takeIf { it.isNotBlank() } ?: title} $artist"
                        val albumSearchResult = YouTube.search(albumSearchQuery, YouTube.SearchFilter.FILTER_ALBUM).getOrNull()
                        albumIdToLookup = albumSearchResult?.items?.firstOrNull()?.id?.takeIf { it.startsWith("MPREb_") }
                    } catch (ignored: Exception) {}
                }
                if (!albumIdToLookup.isNullOrBlank()) {
                    val albumVideo = YouTube.lookupAlbumVideo(title, albumIdToLookup, rawId)
                    if (albumVideo != null && albumVideo.targetVideoId.isNotBlank()) {
                        targetVideoId = albumVideo.targetVideoId
                    }
                }
            }

            // C. Fallback search for official video via MusicVideoMatcher
            if (targetVideoId == null) {
                val artists = MusicVideoMatcher.splitArtistNames(listOf(artist))
                val queries = MusicVideoMatcher.buildVideoSearchQueries(title, artists)
                for (query in queries) {
                    val result = YouTube.search(query, YouTube.SearchFilter.FILTER_VIDEO).getOrNull() ?: continue
                    val items = result.items.filterIsInstance<SongItem>()
                    val candidate = MusicVideoMatcher.findBestMatch(
                        trackTitle = title,
                        trackArtistNames = artists,
                        trackDurationSec = (song.duration / 1000L).takeIf { it > 0L },
                        candidates = items,
                        excludeVideoId = null
                    )
                    if (candidate != null && candidate.id.isNotBlank()) {
                        targetVideoId = candidate.id
                        break
                    }
                }
            }

            if (targetVideoId.isNullOrBlank()) {
                Log.d(TAG, "No official YouTube music video found for canvas: '$title'")
                return@withContext null
            }

            // 2. Extract video stream URL with desired quality
            val qualityPref = ArtworkPreferencesManager.getInstance(context).youtubeCanvasQuality.value
            val maxHeight = when (qualityPref) {
                "480p" -> 480
                "720p" -> 720
                "1080p" -> 1080
                "2160p" -> 2160
                else -> 1080 // Auto defaults to 1080p for sharp display on modern devices
            }

            var streamUrl: String? = null

            // Priority 1: NewPipe video-only DASH stream extraction.
            // Since Canvas is a silent looping visual in background (audio already plays via main player),
            // DASH videoOnlyStreams provide true, unthrottled 720p / 1080p / 2160p (4K) without YouTube's 360p progressive capping!
            streamUrl = runCatching {
                val youtubeUrl = "https://www.youtube.com/watch?v=$targetVideoId"
                val extractor = ServiceList.YouTube.getStreamExtractor(youtubeUrl)
                extractor.fetchPage()

                // Try high-resolution video-only DASH stream first (itag 136/137/247/248/313)
                val videoOnly = extractor.videoOnlyStreams
                    ?.filter { (it.resolution?.replace("p", "")?.toIntOrNull() ?: 0) <= maxHeight }
                    ?.maxByOrNull { it.resolution?.replace("p", "")?.toIntOrNull() ?: 0 }
                    ?.content

                videoOnly ?: extractor.videoStreams
                    ?.filter { (it.resolution?.replace("p", "")?.toIntOrNull() ?: 0) <= maxHeight }
                    ?.maxByOrNull { it.resolution?.replace("p", "")?.toIntOrNull() ?: 0 }
                    ?.content
            }.getOrNull()

            // Priority 2: InnerTubeX fallback if NewPipe extractor failed
            if (streamUrl.isNullOrBlank()) {
                streamUrl = runCatching {
                    InnerTubeXPlayer.initialize(context)
                    InnerTubeXPlayer.extractVideoStream(targetVideoId, maxHeight).getOrNull()?.url
                }.getOrNull()?.takeIf { it.isNotBlank() }
            }

            if (!streamUrl.isNullOrBlank()) {
                inMemoryCache[cacheKey] = streamUrl
                Log.i(TAG, "Resolved YouTube Music Video Canvas for '$title' ($targetVideoId): $streamUrl")
                return@withContext streamUrl
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve YouTube video canvas for '${song.title}'", e)
        }

        null
    }

    fun clearCache() {
        inMemoryCache.clear()
    }
}
