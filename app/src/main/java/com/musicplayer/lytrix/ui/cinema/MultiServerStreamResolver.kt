package com.musicplayer.lytrix.ui.cinema

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Smart Multi-Server video stream resolver for Movies and TV Series.
 * Supports direct HLS/DASH/MP4 streams from high-speed CDN embed streams (VidLink, VidSrc, 2Embed)
 * for guaranteed, reliable playback.
 */
class MultiServerStreamResolver(
    private val client: OkHttpClient = defaultClient,
) : CinemaStreamResolver {

    companion object {
        val SERVER_TORRENTIO = ServerSource("torrentio", "Torrentio (Dual-Audio)", "Dual HD")
        val SERVER_VIDLINK = ServerSource("vidlink", "VidLink (Fast HD)", "1080p")
        val SERVER_VIDSRC = ServerSource("vidsrc", "VidSrc Gateway", "HD")
        val SERVER_2EMBED = ServerSource("2embed", "2Embed", "Fast")

        val DEFAULT_SERVERS = listOf(
            SERVER_TORRENTIO,
            SERVER_VIDLINK,
            SERVER_VIDSRC,
            SERVER_2EMBED
        )

        private val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(12, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
        }

        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
    }

    override val servers: List<ServerSource>
        get() = DEFAULT_SERVERS

    override suspend fun extractServers(
        detail: CinemaDetail,
        seasonNumber: Int?,
        episode: Episode?,
    ): List<ServerSource> = withContext(Dispatchers.IO) {
        DEFAULT_SERVERS
    }

    override suspend fun resolve(
        detail: CinemaDetail,
        server: ServerSource,
        seasonNumber: Int?,
        episode: Episode?,
    ): String? = withContext(Dispatchers.IO) {
        val season = seasonNumber ?: 1
        val ep = episode?.episodeNumber ?: 1

        val validId = detail.imdbId?.takeIf { it.startsWith("tt") }
            ?: detail.item.id.takeIf { it.all { c -> c.isDigit() } }

        if (validId != null) {
            val isTv = detail.item.kind == CinemaKind.TV || detail.item.kind == CinemaKind.ANIME || seasonNumber != null
            return@withContext when (server.id) {
                "torrentio" -> resolveTorrentio(detail, isTv, season, ep)
                "vidlink" -> resolveVidLink(validId, isTv, season, ep)
                "vidsrc" -> resolveVidSrc(validId, isTv, season, ep)
                "2embed" -> resolve2Embed(validId, isTv, season, ep)
                else -> resolveVidLink(validId, isTv, season, ep)
            }
        }

        null
    }

    private suspend fun resolveTorrentio(
        detail: CinemaDetail,
        isTv: Boolean,
        season: Int,
        episode: Int
    ): String? {
        val imdbId = detail.imdbId
        if (!imdbId.isNullOrBlank()) {
            val streams = TorrentioRepository.fetchStreams(imdbId, isTv, season, episode)
            if (streams.isNotEmpty()) {
                val bestStream = streams.first()
                return bestStream.magnetUri
            }
        }
        // Fallback to VidLink if no torrent stream found
        val validId = detail.imdbId?.takeIf { it.startsWith("tt") }
            ?: detail.item.id.takeIf { it.all { c -> c.isDigit() } }
            ?: detail.item.id
        return resolveVidLink(validId, isTv, season, episode)
    }

    private fun resolveVidLink(tmdbId: String, isTv: Boolean, season: Int, episode: Int): String {
        val base = CinemaRemoteConfig.getVidLinkBase().trimEnd('/')
        val embedUrl = if (isTv) {
            "$base/tv/$tmdbId/$season/$episode?autoplay=true"
        } else {
            "$base/movie/$tmdbId?autoplay=true"
        }

        try {
            val apiUrl = if (isTv) {
                "$base/api/b/tv/$tmdbId/$season/$episode"
            } else {
                "$base/api/b/movie/$tmdbId"
            }
            val json = httpGetJson(apiUrl, referer = "$base/")
            val direct = json?.optJSONObject("stream")?.optString("playlist")
                ?: json?.optString("url")?.takeIf { it.isNotBlank() }
            if (!direct.isNullOrBlank() && isDirectMedia(direct)) return direct
        } catch (_: Exception) {
            // ignore and fallback
        }

        return embedUrl
    }

    private fun resolveVidSrc(tmdbId: String, isTv: Boolean, season: Int, episode: Int): String {
        val base = CinemaRemoteConfig.getVidSrcBase().trimEnd('/')
        return if (isTv) {
            "$base/embed/tv?tmdb=$tmdbId&season=$season&episode=$episode"
        } else {
            "$base/embed/movie?tmdb=$tmdbId"
        }
    }

    private fun resolve2Embed(tmdbId: String, isTv: Boolean, season: Int, episode: Int): String {
        val base = CinemaRemoteConfig.get2EmbedBase().trimEnd('/')
        return if (isTv) {
            "$base/embedtv/$tmdbId&s=$season&e=$episode"
        } else {
            "$base/embed/$tmdbId"
        }
    }

    private fun isDirectMedia(url: String): Boolean {
        val clean = url.substringBefore('?').lowercase()
        return clean.endsWith(".m3u8") || clean.endsWith(".mp4") || clean.endsWith(".mpd") || clean.endsWith(".mkv")
    }

    private fun httpGetJson(url: String, referer: String): JSONObject? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Referer", referer)
                .header("Accept", "application/json, text/plain, */*")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                JSONObject(body)
            }
        } catch (_: Exception) {
            null
        }
    }
}
