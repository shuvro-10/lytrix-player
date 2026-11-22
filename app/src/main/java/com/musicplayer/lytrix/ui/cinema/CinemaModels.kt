package com.musicplayer.lytrix.ui.cinema

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.musicplayer.lytrix.ui.theme.LocalAccentColor
import kotlinx.coroutines.CancellationException

// ---------------------------------------------------------------------------
// Core models
// ---------------------------------------------------------------------------

enum class CinemaKind { MOVIE, TV, ANIME }

@Immutable
data class CinemaItem(
    val id: String,
    val title: String,
    val posterUrl: String,
    val backdropUrl: String,
    val rating: Double,
    val year: String,
    val kind: CinemaKind,
    val genres: List<String> = emptyList(),
    val duration: String? = null,
    val seasonsCount: Int? = null,
    val provider: String? = null,
)

@Immutable
data class Episode(
    val id: String,
    val episodeNumber: Int,
    val title: String,
    val overview: String,
    val stillUrl: String?,
    val durationMin: Int?,
    val airDate: String? = null,
)

@Immutable
data class Season(
    val seasonNumber: Int,
    val name: String,
    val episodes: List<Episode>,
)

@Immutable
data class CastMember(
    val id: String,
    val name: String,
    val character: String,
    val profileUrl: String?,
)

@Immutable
data class CinemaDetail(
    val item: CinemaItem,
    val tagline: String?,
    val overview: String,
    val cast: List<CastMember>,
    val seasons: List<Season> = emptyList(),
    val trailerYoutubeKey: String? = null,
    val isUnreleased: Boolean = false,
    val imdbId: String? = null,
    val movieDataUrl: String? = null,
)

@Immutable
data class ServerSource(
    val id: String,
    val name: String,
    val badge: String = "HD",
    val headers: Map<String, String> = emptyMap(),
)

// ---------------------------------------------------------------------------
// Feed / playback models
// ---------------------------------------------------------------------------

/** One horizontal row on the home screen. [progress] (0..1 by item id) turns cards into "Continue Watching" cards. */
@Immutable
data class CinemaRow(
    val id: String,
    val title: String,
    val items: List<CinemaItem>,
    val progress: Map<String, Float> = emptyMap(),
)

@Immutable
data class CinemaFeed(
    val spotlight: List<CinemaItem>,
    val rows: List<CinemaRow>,
)

@Immutable
data class PlaybackRequest(
    val detail: CinemaDetail,
    val server: ServerSource,
    val seasonNumber: Int? = null,
    val episode: Episode? = null,
)

enum class CinemaFilter(val label: String, val searchLabel: String, val kind: CinemaKind?) {
    ALL("All", "All", null),
    MOVIES("Movies", "Movies", CinemaKind.MOVIE),
    SERIES("TV Series", "Series", CinemaKind.TV),
    ANIME("Anime", "Anime", CinemaKind.ANIME);

    fun matches(item: CinemaItem): Boolean = kind == null || item.kind == kind
}

// ---------------------------------------------------------------------------
// Contracts: plug your own data / stream sources in here.
// The UI never talks to a network directly.
// ---------------------------------------------------------------------------

interface CinemaRepository {
    suspend fun loadFeed(): CinemaFeed
    suspend fun search(query: String): List<CinemaItem>
    suspend fun loadDetail(item: CinemaItem): CinemaDetail
}

interface CinemaStreamResolver {
    /** Servers shown in the detail screen and in the player's server switcher. */
    val servers: List<ServerSource>

    /**
     * Dynamically extract stream mirrors and quality options for the specific title/episode.
     */
    suspend fun extractServers(
        detail: CinemaDetail,
        seasonNumber: Int?,
        episode: Episode?,
    ): List<ServerSource> = emptyList()

    /**
     * Return a DIRECT media URL (HLS .m3u8, DASH .mpd or progressive .mp4) that ExoPlayer can play,
     * or null if this server has nothing for the title. Called off the main thread-safe: do your IO inside.
     */
    suspend fun resolve(
        detail: CinemaDetail,
        server: ServerSource,
        seasonNumber: Int?,
        episode: Episode?,
    ): String?
}

// ---------------------------------------------------------------------------
// Design tokens
// ---------------------------------------------------------------------------

internal object CinemaPalette {
    val background = Color(0xFF0C0C0E)
    val surface = Color(0xFF141418)
    val border = Color.White.copy(alpha = 0.08f)
    val textPrimary = Color(0xFFF2F2F5)
    val textSecondary = Color.White.copy(alpha = 0.62f)
    val imdbGold = Color(0xFFF5C518)
    val fallbackAccent = Color(0xFF3182CE)
}

/** Accent from the app theme with the requested fallback. Works whether LocalAccentColor holds Color or Color?. */
@Composable
internal fun cinemaAccent(): Color {
    val accent: Color? = LocalAccentColor.current
    return accent?.takeIf { it != Color.Unspecified } ?: CinemaPalette.fallbackAccent
}

/** try/catch that never swallows coroutine cancellation. */
internal inline fun <T> cinemaCatching(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
