package com.musicplayer.lytrix.artwork

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class CanvasContentSource {
    YOUTUBE_MUSIC_VIDEO,
    SPOTIFY_CANVAS,
    APPLE_MUSIC;

    companion object {
        val defaultOrder = listOf(YOUTUBE_MUSIC_VIDEO, SPOTIFY_CANVAS, APPLE_MUSIC)

        fun parseOrder(csv: String): List<CanvasContentSource> {
            val parsed = csv.split(",").mapNotNull { token -> entries.find { it.name == token.trim() } }
            return (parsed + defaultOrder).distinct()
        }

        fun serialize(order: List<CanvasContentSource>): String =
            order.distinct().filter { it in defaultOrder }.joinToString(",") { it.name }

        fun parseEnabled(csv: String): Set<CanvasContentSource> =
            if (csv.isBlank()) entries.toSet()
            else if (csv == "NONE") emptySet()
            else csv.split(",").mapNotNull { token -> entries.find { it.name == token.trim() } }.toSet()
    }
}

fun CanvasContentSource.isEnabled(
    canvasEnabled: Boolean,
    animatedCoversEnabled: Boolean,
): Boolean = if (this == CanvasContentSource.APPLE_MUSIC) animatedCoversEnabled else canvasEnabled

enum class YouTubeCanvasMode {
    SHORT_CLIPS,
    FULL_VIDEO
}

data class YouTubeCanvasClip(
    val startMs: Long,
    val endMs: Long,
)

const val YOUTUBE_CANVAS_CLIP_LENGTH_MS = 3_000L

fun youtubeCanvasClips(
    durationMs: Long?,
    randomStart: (Long) -> Long = kotlin.random.Random::nextLong,
): List<YouTubeCanvasClip> {
    val duration = durationMs?.takeIf { it > 0L } ?: return emptyList()
    val clipLength = YOUTUBE_CANVAS_CLIP_LENGTH_MS.coerceAtMost(duration)
    // Avoid intro logo/silence (skip first 18% or 20s) and outro movie credits/name cards (skip last 20% or 40s)
    val minStart = if (duration > 40_000L) (duration * 0.18f).toLong().coerceAtLeast(20_000L) else 0L
    val maxEnd = if (duration > 40_000L) (duration * 0.78f).toLong().coerceAtLeast(minStart + clipLength) else duration
    val latestStart = (maxEnd - clipLength).coerceAtLeast(minStart)
    val range = (latestStart - minStart).coerceAtLeast(0L)

    // Evenly distribute 4 highlight clips across the core music performance
    return List(4) { index ->
        val anchor = minStart + if (range > 0L) (range * (index + 0.5f) / 4f).toLong() else 0L
        val jitter = if (range > 10_000L) (randomStart(6_000L) - 3_000L) else 0L
        val start = (anchor + jitter).coerceIn(minStart, latestStart)
        YouTubeCanvasClip(start, start + clipLength)
    }
}

class ArtworkPreferencesManager private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "artwork_preferences"

        const val KEY_CANVAS_ENABLED = "player_canvas_enabled"
        const val KEY_ANIMATED_COVERS_ENABLED = "enable_animated_covers"
        const val KEY_DISABLE_ON_MOBILE_DATA = "disable_animated_artwork_on_mobile_data"
        const val KEY_ANIMATED_COVER_RESOLUTION = "animated_cover_resolution"

        const val KEY_CANVAS_CONTENT_ORDER = "canvas_content_order"
        const val KEY_CANVAS_ENABLED_SOURCES = "canvas_enabled_sources"

        const val KEY_YOUTUBE_CANVAS_QUALITY = "youtube_canvas_quality"
        const val KEY_YOUTUBE_CANVAS_MODE = "youtube_canvas_mode"

        const val KEY_APPLE_ARTIST_IMAGES = "apple_music_artist_images"
        const val KEY_APPLE_ARTIST_VIDEOS = "apple_music_artist_videos"
        const val KEY_APPLE_ARTIST_VIDEO_RES = "apple_music_artist_video_resolution"

        @Volatile
        private var instance: ArtworkPreferencesManager? = null

        fun getInstance(context: Context): ArtworkPreferencesManager =
            instance ?: synchronized(this) {
                instance ?: ArtworkPreferencesManager(context.applicationContext).also { instance = it }
            }
    }

    private val _canvasEnabled = MutableStateFlow(prefs.getBoolean(KEY_CANVAS_ENABLED, false))
    val canvasEnabled: StateFlow<Boolean> = _canvasEnabled.asStateFlow()

    private val _animatedCoversEnabled = MutableStateFlow(prefs.getBoolean(KEY_ANIMATED_COVERS_ENABLED, false))
    val animatedCoversEnabled: StateFlow<Boolean> = _animatedCoversEnabled.asStateFlow()

    private val _disableOnMobileData = MutableStateFlow(prefs.getBoolean(KEY_DISABLE_ON_MOBILE_DATA, false))
    val disableOnMobileData: StateFlow<Boolean> = _disableOnMobileData.asStateFlow()

    private val _animatedCoverResolution = MutableStateFlow(prefs.getString(KEY_ANIMATED_COVER_RESOLUTION, "MEDIUM") ?: "MEDIUM")
    val animatedCoverResolution: StateFlow<String> = _animatedCoverResolution.asStateFlow()

    private val _canvasContentOrder = MutableStateFlow(
        CanvasContentSource.parseOrder(
            prefs.getString(KEY_CANVAS_CONTENT_ORDER, CanvasContentSource.serialize(CanvasContentSource.defaultOrder))
                ?: CanvasContentSource.serialize(CanvasContentSource.defaultOrder)
        )
    )
    val canvasContentOrder: StateFlow<List<CanvasContentSource>> = _canvasContentOrder.asStateFlow()

    private val _canvasEnabledSources = MutableStateFlow(
        CanvasContentSource.parseEnabled(prefs.getString(KEY_CANVAS_ENABLED_SOURCES, "") ?: "")
    )
    val canvasEnabledSources: StateFlow<Set<CanvasContentSource>> = _canvasEnabledSources.asStateFlow()

    private val _youtubeCanvasQuality = MutableStateFlow(prefs.getString(KEY_YOUTUBE_CANVAS_QUALITY, "AUTO") ?: "AUTO")
    val youtubeCanvasQuality: StateFlow<String> = _youtubeCanvasQuality.asStateFlow()

    private val _youtubeCanvasMode = MutableStateFlow(
        runCatching {
            YouTubeCanvasMode.valueOf(prefs.getString(KEY_YOUTUBE_CANVAS_MODE, YouTubeCanvasMode.SHORT_CLIPS.name) ?: YouTubeCanvasMode.SHORT_CLIPS.name)
        }.getOrDefault(YouTubeCanvasMode.SHORT_CLIPS)
    )
    val youtubeCanvasMode: StateFlow<YouTubeCanvasMode> = _youtubeCanvasMode.asStateFlow()

    private val _appleArtistImages = MutableStateFlow(prefs.getBoolean(KEY_APPLE_ARTIST_IMAGES, false))
    val appleArtistImages: StateFlow<Boolean> = _appleArtistImages.asStateFlow()

    private val _appleArtistVideos = MutableStateFlow(prefs.getBoolean(KEY_APPLE_ARTIST_VIDEOS, false))
    val appleArtistVideos: StateFlow<Boolean> = _appleArtistVideos.asStateFlow()

    private val _appleArtistVideoResolution = MutableStateFlow(prefs.getString(KEY_APPLE_ARTIST_VIDEO_RES, "MEDIUM") ?: "MEDIUM")
    val appleArtistVideoResolution: StateFlow<String> = _appleArtistVideoResolution.asStateFlow()

    fun setCanvasEnabled(enabled: Boolean) {
        _canvasEnabled.value = enabled
        prefs.edit().putBoolean(KEY_CANVAS_ENABLED, enabled).apply()
    }

    fun setAnimatedCoversEnabled(enabled: Boolean) {
        _animatedCoversEnabled.value = enabled
        prefs.edit().putBoolean(KEY_ANIMATED_COVERS_ENABLED, enabled).apply()
    }

    fun setDisableOnMobileData(disabled: Boolean) {
        _disableOnMobileData.value = disabled
        prefs.edit().putBoolean(KEY_DISABLE_ON_MOBILE_DATA, disabled).apply()
    }

    fun setAnimatedCoverResolution(resolution: String) {
        _animatedCoverResolution.value = resolution
        prefs.edit().putString(KEY_ANIMATED_COVER_RESOLUTION, resolution).apply()
    }

    fun setCanvasContentOrder(order: List<CanvasContentSource>) {
        _canvasContentOrder.value = order
        prefs.edit().putString(KEY_CANVAS_CONTENT_ORDER, CanvasContentSource.serialize(order)).apply()
    }

    fun setCanvasEnabledSources(sources: Set<CanvasContentSource>) {
        _canvasEnabledSources.value = sources
        val serialized = if (sources.isEmpty()) "NONE" else sources.joinToString(",") { it.name }
        prefs.edit().putString(KEY_CANVAS_ENABLED_SOURCES, serialized).apply()
    }

    fun setYouTubeCanvasQuality(quality: String) {
        _youtubeCanvasQuality.value = quality
        prefs.edit().putString(KEY_YOUTUBE_CANVAS_QUALITY, quality).apply()
    }

    fun setYouTubeCanvasMode(mode: YouTubeCanvasMode) {
        _youtubeCanvasMode.value = mode
        prefs.edit().putString(KEY_YOUTUBE_CANVAS_MODE, mode.name).apply()
    }

    fun setAppleArtistImages(enabled: Boolean) {
        _appleArtistImages.value = enabled
        prefs.edit().putBoolean(KEY_APPLE_ARTIST_IMAGES, enabled).apply()
    }

    fun setAppleArtistVideos(enabled: Boolean) {
        _appleArtistVideos.value = enabled
        prefs.edit().putBoolean(KEY_APPLE_ARTIST_VIDEOS, enabled).apply()
    }

    fun setAppleArtistVideoResolution(resolution: String) {
        _appleArtistVideoResolution.value = resolution
        prefs.edit().putString(KEY_APPLE_ARTIST_VIDEO_RES, resolution).apply()
    }
}
