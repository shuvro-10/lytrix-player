package com.musicplayer.lytrix.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import android.view.View
import android.widget.RemoteViews
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.musicplayer.lytrix.MainActivity
import com.musicplayer.lytrix.R
import com.musicplayer.lytrix.db.AppDatabase
import com.musicplayer.lytrix.db.SongMetadataEntity
import com.musicplayer.lytrix.service.PlaybackService
import com.musicplayer.lytrix.stats.ListeningInsightsData
import com.musicplayer.lytrix.stats.ListeningInsightsRepository
import com.musicplayer.lytrix.stats.StatsPeriod
import com.musicplayer.lytrix.stats.WidgetPlaylistSummary
import com.musicplayer.lytrix.ui.PlayerViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Coordinates updates across all pinned Material 3 RemoteViews Home Screen widgets and executes
 * remote transport + favorite actions safely even when [MainActivity] is backgrounded.
 */
object LytrixWidgetUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val renderMutex = Mutex()
    private var updateJob: Job? = null

    private var cachedArtSourceKey: String? = null
    private var cachedRawArt: Bitmap? = null
    private var cachedRoundedDeckArt: Bitmap? = null
    private var cachedRoundedCompactArt: Bitmap? = null
    private var cachedCircularVinylArt: Bitmap? = null

    private var defaultRoundedDeckIcon: Bitmap? = null
    private var defaultRoundedCompactIcon: Bitmap? = null
    private val playlistArtCache = object : LruCache<String, Bitmap>(16) {}

    fun scheduleUpdate(context: Context, immediate: Boolean = false) {
        val appCtx = context.applicationContext
        updateJob?.cancel()
        updateJob = scope.launch {
            if (!immediate) delay(120L)
            pushAllWidgets(appCtx)
        }
    }

    suspend fun handleCommand(context: Context, command: String) {
        val appCtx = context.applicationContext
        val vm = PlayerViewModel.activeInstance
        val service = PlaybackService.activeInstance

        if (vm == null && service == null) {
            if (command == WidgetConstants.CMD_PLAY_PAUSE) {
                appCtx.startActivity(
                    Intent(appCtx, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                )
            }
            return
        }

        when (command) {
            WidgetConstants.CMD_PLAY_PAUSE -> withContext(Dispatchers.Main) {
                if (vm != null) vm.playPause() else service?.togglePlayPause()
            }
            WidgetConstants.CMD_NEXT -> withContext(Dispatchers.Main) {
                if (vm != null) vm.skipNext() else service?.skipToNextTrack()
            }
            WidgetConstants.CMD_PREV -> withContext(Dispatchers.Main) {
                if (vm != null) vm.skipPrevious() else service?.skipToPreviousTrack()
            }
            WidgetConstants.CMD_TOGGLE_LIKE -> toggleCurrentFavorite(appCtx, vm, service)
        }

        delay(120L)
        pushAllWidgets(appCtx)
    }

    private suspend fun toggleCurrentFavorite(
        context: Context,
        vm: PlayerViewModel?,
        service: PlaybackService?
    ) {
        val currentSong = vm?.currentSong?.value
        if (vm != null && currentSong != null) {
            withContext(Dispatchers.Main) {
                vm.toggleFavorite(currentSong.id)
            }
            return
        }

        val snap = withContext(Dispatchers.Main) { service?.currentDreamSnapshot() } ?: return
        val mediaId = snap.mediaId.orEmpty()
        if (mediaId.isBlank()) return

        val numericId = mediaId.toLongOrNull()
        val isOnline = snap.artworkUri?.startsWith("http", ignoreCase = true) == true || numericId == null || numericId < 0L
        if (isOnline) {
            val prefs = context.getSharedPreferences("online_liked_songs", Context.MODE_PRIVATE)
            val set = prefs.getStringSet("liked_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
            if (!set.remove(mediaId)) {
                set.add(mediaId)
            }
            prefs.edit().putStringSet("liked_ids", set).apply()
        } else {
            val dao = AppDatabase.getDatabase(context).songMetadataDao()
            val existing = dao.getMetadata(numericId)
            if (existing != null) {
                dao.updateFavorite(numericId, !existing.isFavorite)
            } else {
                dao.insertOrUpdate(SongMetadataEntity(songId = numericId, isFavorite = true))
            }
        }
    }

    suspend fun pushAllWidgets(context: Context) {
        renderMutex.withLock {
            val appWidgetManager = runCatching { AppWidgetManager.getInstance(context) }.getOrNull()
                ?: return

            val turntableIds = getIds(appWidgetManager, context, TurntableWidgetReceiver::class.java)
            val capsuleIds = getIds(appWidgetManager, context, CapsuleDeckWidgetReceiver::class.java)
            val insightsIds = getIds(appWidgetManager, context, InsightsWidgetReceiver::class.java)
            val playlistsIds = getIds(appWidgetManager, context, PlaylistsWidgetReceiver::class.java)

            if (turntableIds.isEmpty() && capsuleIds.isEmpty() && insightsIds.isEmpty() && playlistsIds.isEmpty()) {
                return
            }

            val vm = PlayerViewModel.activeInstance
            val service = PlaybackService.activeInstance
            val currentSong = vm?.currentSong?.value
            val snap = withContext(Dispatchers.Main) { service?.currentDreamSnapshot() }

            val hasTrack = currentSong != null || snap != null
            val title = currentSong?.title?.takeIf { it.isNotBlank() }
                ?: snap?.title?.takeIf { it.isNotBlank() }
                ?: "Lytrix Player"
            val artist = currentSong?.artist?.takeIf { it.isNotBlank() }
                ?: snap?.artist?.takeIf { it.isNotBlank() }
                ?: if (hasTrack) "Unknown Artist" else "Tap to play music"
            val isPlaying = vm?.isPlaying?.value ?: snap?.isPlaying ?: false
            val posMs = (vm?.currentPosition?.value ?: snap?.positionMs ?: 0L).coerceAtLeast(0L)
            val durMs = (vm?.duration?.value ?: snap?.durationMs ?: 0L).coerceAtLeast(0L)
            val progressLevel = if (durMs > 0L) {
                ((posMs.toDouble() / durMs.toDouble()) * 10000.0).toInt().coerceIn(0, 10000)
            } else {
                0
            }

            val isLiked = resolveIsLiked(context, vm, currentSong, snap?.mediaId)
            val rawArtUri = currentSong?.albumArtUriString ?: snap?.artworkUri ?: currentSong?.uriString
            val needsArt = turntableIds.isNotEmpty() || capsuleIds.isNotEmpty() ||
                playlistsIds.isNotEmpty() || (insightsIds.isNotEmpty() && !rawArtUri.isNullOrBlank())

            if (needsArt) {
                ensureArtworkBitmaps(context, rawArtUri)
            }

            val roundedDeckArt = cachedRoundedDeckArt ?: getDefaultRoundedDeckIcon(context)
            val roundedCompactArt = cachedRoundedCompactArt ?: getDefaultRoundedCompactIcon(context)

            if (turntableIds.isNotEmpty()) {
                val views = buildTurntableViews(context, cachedCircularVinylArt, isPlaying)
                turntableIds.forEach { id ->
                    runCatching { appWidgetManager.updateAppWidget(id, views) }
                }
            }

            if (capsuleIds.isNotEmpty()) {
                capsuleIds.forEach { id ->
                    val options = runCatching { appWidgetManager.getAppWidgetOptions(id) }.getOrNull()
                    val views = buildCapsuleViewsForSize(
                        context = context,
                        options = options,
                        title = title,
                        artist = artist,
                        deckArt = roundedDeckArt,
                        compactArt = roundedCompactArt,
                        isPlaying = isPlaying,
                        isLiked = isLiked,
                        progressLevel = progressLevel
                    )
                    runCatching { appWidgetManager.updateAppWidget(id, views) }
                }
            }

            val needsStats = insightsIds.isNotEmpty() || playlistsIds.isNotEmpty()
            val insights = if (needsStats) {
                runCatching { ListeningInsightsRepository.load(context, StatsPeriod.ALL_TIME) }.getOrNull()
            } else {
                null
            }

            if (insightsIds.isNotEmpty()) {
                val views = buildInsightsViews(
                    context = context,
                    hasTrack = hasTrack,
                    title = title,
                    artist = artist,
                    compactArt = if (!rawArtUri.isNullOrBlank() && cachedRoundedCompactArt != null) {
                        cachedRoundedCompactArt
                    } else {
                        null
                    },
                    isPlaying = isPlaying,
                    insights = insights
                )
                insightsIds.forEach { id ->
                    runCatching { appWidgetManager.updateAppWidget(id, views) }
                }
            }

            if (playlistsIds.isNotEmpty()) {
                val tiles = resolvePlaylistTiles(insights)
                val tileBitmaps = tiles.map { tile ->
                    loadPlaylistTileBitmap(context, tile)
                }
                val views = buildPlaylistsViews(
                    context = context,
                    title = title,
                    artist = artist,
                    compactArt = roundedCompactArt,
                    isPlaying = isPlaying,
                    isLiked = isLiked,
                    progressLevel = progressLevel,
                    tiles = tiles,
                    tileBitmaps = tileBitmaps
                )
                playlistsIds.forEach { id ->
                    runCatching { appWidgetManager.updateAppWidget(id, views) }
                }
            }
        }
    }

    private fun getIds(
        manager: AppWidgetManager,
        context: Context,
        cls: Class<*>
    ): IntArray = runCatching {
        manager.getAppWidgetIds(ComponentName(context, cls))
    }.getOrDefault(IntArray(0))

    private suspend fun resolveIsLiked(
        context: Context,
        vm: PlayerViewModel?,
        currentSong: com.musicplayer.lytrix.model.Song?,
        fallbackMediaId: String?
    ): Boolean {
        if (vm != null && currentSong != null) {
            return if (vm.isSongOnline(currentSong)) {
                vm.onlineLikeKey(currentSong) in vm.onlineLikedIds.value
            } else {
                currentSong.id in vm.favoriteSongIds.value
            }
        }
        val key = fallbackMediaId?.takeIf { it.isNotBlank() } ?: return false
        val onlineLiked = context.getSharedPreferences("online_liked_songs", Context.MODE_PRIVATE)
            .getStringSet("liked_ids", emptySet()).orEmpty()
        if (key in onlineLiked) return true
        val numId = key.toLongOrNull() ?: return false
        return runCatching {
            AppDatabase.getDatabase(context).songMetadataDao().getMetadata(numId)?.isFavorite == true
        }.getOrDefault(false)
    }

    private suspend fun ensureArtworkBitmaps(context: Context, sourceUri: String?) {
        if (sourceUri.isNullOrBlank()) {
            cachedArtSourceKey = null
            cachedRawArt = null
            cachedRoundedDeckArt = null
            cachedRoundedCompactArt = null
            cachedCircularVinylArt = null
            return
        }
        if (sourceUri == cachedArtSourceKey && cachedRoundedDeckArt != null && cachedCircularVinylArt != null) {
            return
        }

        val decoded = loadBitmapFromCoil(context, sourceUri, size = 280) ?: return
        cachedArtSourceKey = sourceUri
        cachedRawArt = decoded
        cachedRoundedDeckArt = WidgetBitmapHelper.createRoundedBitmap(decoded, cornerRadiusPx = 36f, targetSize = 240)
        cachedRoundedCompactArt = WidgetBitmapHelper.createRoundedBitmap(decoded, cornerRadiusPx = 28f, targetSize = 180)
        cachedCircularVinylArt = WidgetBitmapHelper.createCircularVinylBitmap(decoded, targetSize = 300)
    }

    private suspend fun loadBitmapFromCoil(context: Context, uri: String, size: Int): Bitmap? {
        return withContext(Dispatchers.IO) {
            runCatching {
                val request = ImageRequest.Builder(context)
                    .data(uri)
                    .size(size, size)
                    .allowHardware(false)
                    .build()
                ((context.imageLoader.execute(request) as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
            }.getOrNull()
        }
    }

    private fun getDefaultRoundedDeckIcon(context: Context): Bitmap {
        return defaultRoundedDeckIcon ?: WidgetBitmapHelper.createRoundedAppIcon(context, 36f, 220).also {
            defaultRoundedDeckIcon = it
        }
    }

    private fun getDefaultRoundedCompactIcon(context: Context): Bitmap {
        return defaultRoundedCompactIcon ?: WidgetBitmapHelper.createRoundedAppIcon(context, 28f, 180).also {
            defaultRoundedCompactIcon = it
        }
    }

    private suspend fun loadPlaylistTileBitmap(
        context: Context,
        tile: WidgetPlaylistSummary
    ): Bitmap {
        val key = "${tile.type}:${tile.id}:${tile.coverUrl.orEmpty()}:${tile.title}"
        playlistArtCache.get(key)?.takeIf { !it.isRecycled }?.let { return it }

        val cover = tile.coverUrl?.takeIf { it.isNotBlank() }?.let { url ->
            loadBitmapFromCoil(context, url, size = 168)
        }
        val rendered = WidgetBitmapHelper.createPlaylistTileBitmap(
            context = context,
            title = tile.title,
            type = tile.type,
            coverBitmap = cover,
            targetSize = 168,
            cornerRadiusPx = 28f
        )
        playlistArtCache.put(key, rendered)
        return rendered
    }

    private fun buildTurntableViews(
        context: Context,
        circularArt: Bitmap?,
        isPlaying: Boolean
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_turntable)
        if (circularArt != null) {
            views.setImageViewBitmap(R.id.widget_turntable_album_art, circularArt)
        } else {
            views.setImageViewResource(R.id.widget_turntable_album_art, R.drawable.widget_turntable_default_art)
        }
        views.setImageViewResource(
            R.id.widget_turntable_play_pause,
            if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
        )

        val openIntent = WidgetBitmapHelper.openMainPendingIntent(context)
        views.setOnClickPendingIntent(android.R.id.background, openIntent)
        views.setOnClickPendingIntent(R.id.widget_turntable_album_art, openIntent)
        views.setOnClickPendingIntent(
            R.id.widget_turntable_play_container,
            WidgetBitmapHelper.broadcastPendingIntent(context, 201, WidgetConstants.ACTION_WIDGET_PLAY_PAUSE)
        )
        views.setOnClickPendingIntent(
            R.id.widget_turntable_prev_button,
            WidgetBitmapHelper.broadcastPendingIntent(context, 202, WidgetConstants.ACTION_WIDGET_PREV)
        )
        views.setOnClickPendingIntent(
            R.id.widget_turntable_next_button,
            WidgetBitmapHelper.broadcastPendingIntent(context, 203, WidgetConstants.ACTION_WIDGET_NEXT)
        )
        return views
    }

    private fun buildCapsuleViewsForSize(
        context: Context,
        options: android.os.Bundle?,
        title: String,
        artist: String,
        deckArt: Bitmap,
        compactArt: Bitmap,
        isPlaying: Boolean,
        isLiked: Boolean,
        progressLevel: Int
    ): RemoteViews {
        val minW = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) ?: 260
        val minH = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) ?: 130

        return when {
            minW < 185 -> buildCapsuleSquareViews(context, deckArt, isPlaying, isLiked)
            minH < 102 -> buildCapsuleWideViews(context, title, artist, compactArt, isPlaying, isLiked)
            else -> buildCapsuleFullDeckViews(context, title, artist, deckArt, isPlaying, isLiked, progressLevel)
        }
    }

    private fun buildCapsuleSquareViews(
        context: Context,
        albumArt: Bitmap,
        isPlaying: Boolean,
        isLiked: Boolean
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_capsule_square)
        views.setImageViewBitmap(R.id.widget_compact_album_art, albumArt)
        views.setImageViewResource(
            R.id.widget_compact_play_pause,
            if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
        )
        views.setImageViewResource(
            R.id.widget_compact_like_icon,
            if (isLiked) R.drawable.ic_widget_heart_nav else R.drawable.ic_widget_heart_outline_nav
        )

        val openIntent = WidgetBitmapHelper.openMainPendingIntent(context)
        views.setOnClickPendingIntent(android.R.id.background, openIntent)
        views.setOnClickPendingIntent(R.id.widget_compact_album_art, openIntent)
        views.setOnClickPendingIntent(
            R.id.widget_compact_play_container,
            WidgetBitmapHelper.broadcastPendingIntent(context, 211, WidgetConstants.ACTION_WIDGET_PLAY_PAUSE)
        )
        views.setOnClickPendingIntent(
            R.id.widget_compact_like_container,
            WidgetBitmapHelper.broadcastPendingIntent(context, 212, WidgetConstants.ACTION_WIDGET_TOGGLE_LIKE)
        )
        return views
    }

    private fun buildCapsuleWideViews(
        context: Context,
        title: String,
        artist: String,
        compactArt: Bitmap,
        isPlaying: Boolean,
        isLiked: Boolean
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_capsule_wide)
        views.setTextViewText(R.id.widget_wide_title, title)
        views.setTextViewText(R.id.widget_wide_artist, artist)
        views.setImageViewBitmap(R.id.widget_wide_album_art, compactArt)
        views.setImageViewResource(
            R.id.widget_wide_play_pause,
            if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
        )
        views.setImageViewResource(
            R.id.widget_wide_like_icon,
            if (isLiked) R.drawable.ic_widget_heart_nav else R.drawable.ic_widget_heart_outline_nav
        )

        val openIntent = WidgetBitmapHelper.openMainPendingIntent(context)
        views.setOnClickPendingIntent(android.R.id.background, openIntent)
        views.setOnClickPendingIntent(R.id.widget_wide_album_art, openIntent)
        views.setOnClickPendingIntent(
            R.id.widget_wide_like_container,
            WidgetBitmapHelper.broadcastPendingIntent(context, 221, WidgetConstants.ACTION_WIDGET_TOGGLE_LIKE)
        )
        views.setOnClickPendingIntent(
            R.id.widget_wide_play_container,
            WidgetBitmapHelper.broadcastPendingIntent(context, 222, WidgetConstants.ACTION_WIDGET_PLAY_PAUSE)
        )
        views.setOnClickPendingIntent(
            R.id.widget_wide_next_container,
            WidgetBitmapHelper.broadcastPendingIntent(context, 223, WidgetConstants.ACTION_WIDGET_NEXT)
        )
        return views
    }

    private fun buildCapsuleFullDeckViews(
        context: Context,
        title: String,
        artist: String,
        deckArt: Bitmap,
        isPlaying: Boolean,
        isLiked: Boolean,
        progressLevel: Int
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_capsule_deck)
        views.setTextViewText(R.id.widget_deck_title, title)
        views.setTextViewText(R.id.widget_deck_artist, artist)
        views.setImageViewBitmap(R.id.widget_deck_album_art, deckArt)
        views.setImageViewResource(
            R.id.widget_deck_play_pause,
            if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
        )
        views.setImageViewResource(
            R.id.widget_deck_like_icon,
            if (isLiked) R.drawable.ic_widget_heart_nav else R.drawable.ic_widget_heart_outline_nav
        )
        views.setInt(R.id.widget_deck_progress_fill, "setImageLevel", progressLevel)

        val openIntent = WidgetBitmapHelper.openMainPendingIntent(context)
        views.setOnClickPendingIntent(android.R.id.background, openIntent)
        views.setOnClickPendingIntent(R.id.widget_deck_album_art, openIntent)
        views.setOnClickPendingIntent(
            R.id.widget_deck_like_container,
            WidgetBitmapHelper.broadcastPendingIntent(context, 231, WidgetConstants.ACTION_WIDGET_TOGGLE_LIKE)
        )
        views.setOnClickPendingIntent(
            R.id.widget_deck_prev_container,
            WidgetBitmapHelper.broadcastPendingIntent(context, 232, WidgetConstants.ACTION_WIDGET_PREV)
        )
        views.setOnClickPendingIntent(
            R.id.widget_deck_play_container,
            WidgetBitmapHelper.broadcastPendingIntent(context, 233, WidgetConstants.ACTION_WIDGET_PLAY_PAUSE)
        )
        views.setOnClickPendingIntent(
            R.id.widget_deck_next_container,
            WidgetBitmapHelper.broadcastPendingIntent(context, 234, WidgetConstants.ACTION_WIDGET_NEXT)
        )
        return views
    }

    private fun buildInsightsViews(
        context: Context,
        hasTrack: Boolean,
        title: String,
        artist: String,
        compactArt: Bitmap?,
        isPlaying: Boolean,
        insights: ListeningInsightsData?
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_insights)
        if (compactArt != null) {
            views.setImageViewBitmap(R.id.widget_insights_art, compactArt)
        } else {
            views.setImageViewResource(R.id.widget_insights_art, R.drawable.widget_insights_badge)
        }

        views.setTextViewText(R.id.widget_insights_heading, "Listening Insights")
        val sub = if (hasTrack) "$title · $artist" else "Tap to open your stats"
        views.setTextViewText(R.id.widget_insights_now_playing, sub)
        views.setImageViewResource(
            R.id.widget_insights_play_pause,
            if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
        )

        val todayStr = formatShortMinutes(insights?.todayPlaytimeMs ?: 0L)
        val weekStr = formatShortMinutes(insights?.weekPlaytimeMs ?: 0L)
        val streakStr = "${insights?.currentStreakDays ?: 0}d"
        views.setTextViewText(R.id.widget_insights_today_value, todayStr)
        views.setTextViewText(R.id.widget_insights_week_value, weekStr)
        views.setTextViewText(R.id.widget_insights_streak_value, streakStr)

        val topArtist = insights?.topArtists?.firstOrNull()?.artist?.takeIf { it.isNotBlank() }
        val topTrack = insights?.topTracks?.firstOrNull()?.title?.takeIf { it.isNotBlank() }
        val topLine = when {
            topArtist != null && topTrack != null -> "Top: $topArtist · $topTrack"
            topArtist != null -> "Top artist: $topArtist"
            topTrack != null -> "Top track: $topTrack"
            else -> "Open insights & receipt"
        }
        views.setTextViewText(R.id.widget_insights_top_line, topLine)

        val insightsIntent = WidgetBitmapHelper.openInsightsPendingIntent(context)
        views.setOnClickPendingIntent(android.R.id.background, insightsIntent)
        views.setOnClickPendingIntent(R.id.widget_insights_header, insightsIntent)
        views.setOnClickPendingIntent(R.id.widget_insights_footer, insightsIntent)
        views.setOnClickPendingIntent(
            R.id.widget_insights_play_container,
            WidgetBitmapHelper.broadcastPendingIntent(context, 241, WidgetConstants.ACTION_WIDGET_PLAY_PAUSE)
        )
        return views
    }

    private fun resolvePlaylistTiles(insights: ListeningInsightsData?): List<WidgetPlaylistSummary> {
        val raw = insights?.playlists.orEmpty()
        if (raw.isNotEmpty()) return raw.take(4)
        return listOf(
            WidgetPlaylistSummary(id = "-1", title = "Liked Songs", trackCount = 0, type = "liked"),
            WidgetPlaylistSummary(id = "-1", title = "Most Played", trackCount = 0, type = "most_played"),
            WidgetPlaylistSummary(id = "-1", title = "Recent Plays", trackCount = 0, type = "recent")
        )
    }

    private fun buildPlaylistsViews(
        context: Context,
        title: String,
        artist: String,
        compactArt: Bitmap,
        isPlaying: Boolean,
        isLiked: Boolean,
        progressLevel: Int,
        tiles: List<WidgetPlaylistSummary>,
        tileBitmaps: List<Bitmap>
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_playlists)
        views.setImageViewBitmap(R.id.widget_playlist_album_art, compactArt)
        views.setTextViewText(R.id.widget_playlist_song_title, title)
        views.setTextViewText(R.id.widget_playlist_artist_name, artist)
        views.setImageViewResource(
            R.id.widget_playlist_play_pause,
            if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
        )
        views.setImageViewResource(
            R.id.widget_playlist_like_icon,
            if (isLiked) R.drawable.ic_widget_heart_nav else R.drawable.ic_widget_heart_outline_nav
        )
        views.setInt(R.id.widget_playlist_progress_fill, "setImageLevel", progressLevel)

        val openApp = WidgetBitmapHelper.openMainPendingIntent(context)
        views.setOnClickPendingIntent(android.R.id.background, openApp)
        views.setOnClickPendingIntent(R.id.widget_playlist_player_section, openApp)
        views.setOnClickPendingIntent(
            R.id.widget_playlist_prev_container,
            WidgetBitmapHelper.broadcastPendingIntent(context, 251, WidgetConstants.ACTION_WIDGET_PREV)
        )
        views.setOnClickPendingIntent(
            R.id.widget_playlist_play_pause_container,
            WidgetBitmapHelper.broadcastPendingIntent(context, 252, WidgetConstants.ACTION_WIDGET_PLAY_PAUSE)
        )
        views.setOnClickPendingIntent(
            R.id.widget_playlist_next_container,
            WidgetBitmapHelper.broadcastPendingIntent(context, 253, WidgetConstants.ACTION_WIDGET_NEXT)
        )
        views.setOnClickPendingIntent(
            R.id.widget_playlist_like_container,
            WidgetBitmapHelper.broadcastPendingIntent(context, 254, WidgetConstants.ACTION_WIDGET_TOGGLE_LIKE)
        )

        val slots = listOf(
            Triple(R.id.widget_playlist_card_1, R.id.widget_playlist_card_1_art, R.id.widget_playlist_card_1_title),
            Triple(R.id.widget_playlist_card_2, R.id.widget_playlist_card_2_art, R.id.widget_playlist_card_2_title),
            Triple(R.id.widget_playlist_card_3, R.id.widget_playlist_card_3_art, R.id.widget_playlist_card_3_title),
            Triple(R.id.widget_playlist_card_4, R.id.widget_playlist_card_4_art, R.id.widget_playlist_card_4_title)
        )
        slots.forEachIndexed { index, (cardId, artId, titleId) ->
            val tile = tiles.getOrNull(index)
            if (tile == null) {
                views.setViewVisibility(cardId, View.GONE)
            } else {
                views.setViewVisibility(cardId, View.VISIBLE)
                views.setTextViewText(titleId, tile.title)
                tileBitmaps.getOrNull(index)?.let { bmp ->
                    views.setImageViewBitmap(artId, bmp)
                }
                views.setOnClickPendingIntent(
                    cardId,
                    WidgetBitmapHelper.openPlaylistPendingIntent(
                        context = context,
                        requestCode = 300 + index,
                        type = tile.type,
                        id = tile.id
                    )
                )
            }
        }
        return views
    }

    private fun formatShortMinutes(ms: Long): String {
        val totalMin = (ms / 60_000L).coerceAtLeast(0L)
        val hrs = totalMin / 60L
        val mins = totalMin % 60L
        return if (hrs > 0) "${hrs}h ${mins}m" else "${mins}m"
    }
}
