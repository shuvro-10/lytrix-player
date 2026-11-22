package com.musicplayer.lytrix.spotify.canvas

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.musicplayer.lytrix.artwork.YouTubeCanvasClip
import kotlinx.coroutines.*
import java.io.File

@OptIn(UnstableApi::class)
object CanvasVideoPlayerManager {
    private const val TAG = "CanvasVideoPlayer"
    private const val MAX_CACHE_SIZE = 100L * 1024 * 1024 // 100 MB LRU disk cache

    @Volatile
    private var simpleCache: SimpleCache? = null
    @Volatile
    private var cacheDataSourceFactory: CacheDataSource.Factory? = null

    @Volatile
    private var activePlayer: ExoPlayer? = null
    @Volatile
    private var activeSongId: String? = null
    @Volatile
    private var activeVideoUrl: String? = null
    @Volatile
    private var activeClips: List<YouTubeCanvasClip> = emptyList()

    private var clockJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    @Synchronized
    fun getCache(context: Context): SimpleCache {
        simpleCache?.let { return it }
        val appContext = context.applicationContext
        val cacheDir = File(appContext.cacheDir, "canvas_video_cache")
        val evictor = LeastRecentlyUsedCacheEvictor(MAX_CACHE_SIZE)
        val databaseProvider = StandaloneDatabaseProvider(appContext)
        val cache = SimpleCache(cacheDir, evictor, databaseProvider)
        simpleCache = cache
        return cache
    }

    @Synchronized
    fun getCacheDataSourceFactory(context: Context): CacheDataSource.Factory {
        cacheDataSourceFactory?.let { return it }
        val appContext = context.applicationContext
        val cache = getCache(appContext)
        val upstreamFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Lytrix/1.0")
            .setConnectTimeoutMs(10000)
            .setReadTimeoutMs(15000)

        val factory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        cacheDataSourceFactory = factory
        return factory
    }

    @Synchronized
    fun buildPlayer(context: Context, videoUrl: String, isPlaying: Boolean): ExoPlayer {
        val appContext = context.applicationContext
        val dataSourceFactory = getCacheDataSourceFactory(appContext)
        val mediaSourceFactory = DefaultMediaSourceFactory(appContext)
            .setDataSourceFactory(dataSourceFactory)

        val selector = DefaultTrackSelector(appContext).apply {
            parameters = buildUponParameters()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .setTrackTypeDisabled(C.TRACK_TYPE_METADATA, true)
                .build()
        }

        return ExoPlayer.Builder(appContext)
            .setTrackSelector(selector)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(videoUrl))
                repeatMode = Player.REPEAT_MODE_ONE
                volume = 0f
                prepare()
                playWhenReady = isPlaying
            }
    }

    @Synchronized
    fun getOrCreatePlayer(
        context: Context,
        videoUrl: String,
        songId: Any,
        isPlaying: Boolean,
        clips: List<YouTubeCanvasClip> = emptyList()
    ): ExoPlayer {
        val sId = songId.toString()
        val current = activePlayer
        if (current != null && activeSongId == sId && activeVideoUrl == videoUrl) {
            current.playWhenReady = isPlaying
            if (activeClips != clips) {
                activeClips = clips
                restartClock(current, clips)
            }
            return current
        }

        // Song changed or new video URL -> release previous to save RAM & hardware decoder
        releaseCurrentPlayer()

        val newPlayer = buildPlayer(context, videoUrl, isPlaying)

        activePlayer = newPlayer
        activeSongId = sId
        activeVideoUrl = videoUrl
        activeClips = clips

        if (clips.isNotEmpty()) {
            restartClock(newPlayer, clips)
        }

        return newPlayer
    }

    private fun restartClock(player: ExoPlayer, clips: List<YouTubeCanvasClip>) {
        clockJob?.cancel()
        if (clips.isEmpty()) return
        clockJob = scope.launch {
            var clipIndex = 0
            fun seekToClip() {
                if (clips.isNotEmpty() && clipIndex in clips.indices) {
                    val start = clips[clipIndex].startMs
                    player.seekTo(start)
                }
            }
            seekToClip()
            while (isActive) {
                delay(100L)
                if (!player.playWhenReady) continue
                val position = player.currentPosition
                val isPlaying = player.isPlaying
                val ended = player.playbackState == Player.STATE_ENDED
                val currentClip = clips.getOrNull(clipIndex) ?: break
                if (ended || (isPlaying && position >= currentClip.endMs)) {
                    clipIndex = (clipIndex + 1) % clips.size
                    seekToClip()
                }
            }
        }
    }

    @Synchronized
    fun releaseIfDifferentSong(newSongId: Any) {
        val sId = newSongId.toString()
        if (activeSongId != null && activeSongId != sId) {
            releaseCurrentPlayer()
        }
    }

    @Synchronized
    fun releaseAll() {
        releaseCurrentPlayer()
    }

    @Synchronized
    fun clearDiskCache(context: Context) {
        releaseCurrentPlayer()
        try {
            simpleCache?.release()
            simpleCache = null
            cacheDataSourceFactory = null
            val appContext = context.applicationContext
            val cacheDir = File(appContext.cacheDir, "canvas_video_cache")
            val databaseProvider = StandaloneDatabaseProvider(appContext)
            SimpleCache.delete(cacheDir, databaseProvider)
            Log.i(TAG, "Canvas video disk cache cleared successfully")
        } catch (e: Exception) {
            Log.w(TAG, "Error clearing video disk cache: ${e.message}")
        }
    }

    private fun releaseCurrentPlayer() {
        clockJob?.cancel()
        clockJob = null
        activeClips = emptyList()
        try {
            activePlayer?.stop()
            activePlayer?.clearMediaItems()
            activePlayer?.release()
        } catch (_: Exception) {}
        activePlayer = null
        activeSongId = null
        activeVideoUrl = null
    }
}
