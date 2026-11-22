package com.musicplayer.lytrix.ui.cinema

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

@Immutable
data class TorrentStream(
    val name: String,
    val title: String,
    val infoHash: String,
    val fileIdx: Int = -1,
    val quality: String,
    val sizeStr: String,
    val seeders: Int,
    val isHindiOrDual: Boolean,
    val languageLabel: String,
    val filename: String,
    val magnetUri: String,
    val languageScore: Int = 0
)

object TorrentioRepository {

    private const val TORRENTIO_BASE = "https://torrentio.strem.fun"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private val SEEDERS_REGEX = Pattern.compile("""[👤👥]\s*(\d+)""")
    private val SIZE_REGEX = Pattern.compile("""[💾📦]\s*([\d\.]+\s*[GMK]B)""", Pattern.CASE_INSENSITIVE)

    private val HINDI_REGEX = Pattern.compile(
        """(?i)(\b(hindi|hin|jhs|jiohotstar|jiocinema|hotstar|bolly|desiremovies|mkvcinemas|vegamovies|katmoviehd)\b|[\[\(\+\-_/\s]hin[\]\)\+\-_/\s])"""
    )
    private val BENGALI_REGEX = Pattern.compile(
        """(?i)(\b(bengali|bangla|ben)\b|[\[\(\+\-_/\s]ben[\]\)\+\-_/\s])"""
    )
    private val REGIONAL_INDIAN_REGEX = Pattern.compile(
        """(?i)(\b(tamil|tam|telugu|tel|malayalam|mal|kannada|kan)\b|[\[\(\+\-_/\s](tam|tel|mal|kan)[\]\)\+\-_/\s])"""
    )
    private val MULTI_REGEX = Pattern.compile(
        """(?i)(\b(dual|multi|dubbed|audios?|multiaudio)\b|[\[\(\+\-_/\s](dual|multi)[\]\)\+\-_/\s])"""
    )

    private val TRACKERS = listOf(
        "udp://tracker.opentrackr.org:1337/announce",
        "udp://open.stealth.si:80/announce",
        "udp://tracker.torrent.eu.org:451/announce",
        "udp://open.demonii.com:1337/announce",
        "udp://open.tracker.cl:1337/announce",
        "udp://exodus.desync.com:6969/announce",
        "udp://tracker.bittor.pw:1337/announce",
        "udp://explodie.org:6969/announce"
    )

    /**
     * Fetches real-time multi-audio streams directly from Torrentio.
     * Returns sorted list prioritizing Hindi / Dual Audio and high seeders.
     */
    suspend fun fetchStreams(
        imdbId: String,
        isTv: Boolean = false,
        season: Int = 1,
        episode: Int = 1
    ): List<TorrentStream> = withContext(Dispatchers.IO) {
        if (imdbId.isBlank()) return@withContext emptyList()

        val endpoint = if (isTv) {
            "$TORRENTIO_BASE/stream/series/$imdbId:$season:$episode.json"
        } else {
            "$TORRENTIO_BASE/stream/movie/$imdbId.json"
        }

        try {
            val request = Request.Builder()
                .url(endpoint)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Accept", "application/json")
                .build()

            val jsonStr = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                response.body?.string() ?: return@withContext emptyList()
            }

            val json = JSONObject(jsonStr)
            val streamsArray = json.optJSONArray("streams") ?: return@withContext emptyList()

            val results = mutableListOf<TorrentStream>()

            for (i in 0 until streamsArray.length()) {
                val obj = streamsArray.optJSONObject(i) ?: continue
                val rawName = obj.optString("name", "Torrentio")
                val rawTitle = obj.optString("title", "")
                val infoHash = obj.optString("infoHash", "")
                if (infoHash.isBlank()) continue

                val fileIdx = obj.optInt("fileIdx", -1)
                val behaviorHints = obj.optJSONObject("behaviorHints")
                val filename = behaviorHints?.optString("filename")?.takeIf { it.isNotBlank() }
                    ?: rawTitle.substringBefore('\n').trim()

                // Quality detection
                val combinedText = "$rawName $rawTitle $filename".lowercase()
                val quality = when {
                    combinedText.contains("2160p") || combinedText.contains("4k") -> "4K UHD"
                    combinedText.contains("1080p") -> "1080p"
                    combinedText.contains("720p") -> "720p"
                    combinedText.contains("480p") -> "480p"
                    combinedText.contains("hdrip") || combinedText.contains("webrip") -> "HD"
                    else -> "HD"
                }

                // Seeders count
                val seedersMatcher = SEEDERS_REGEX.matcher(rawTitle)
                val seeders = if (seedersMatcher.find()) {
                    seedersMatcher.group(1)?.toIntOrNull() ?: 1
                } else {
                    1
                }

                // File size
                val sizeMatcher = SIZE_REGEX.matcher(rawTitle)
                val sizeStr = if (sizeMatcher.find()) {
                    sizeMatcher.group(1)?.trim() ?: ""
                } else {
                    ""
                }

                // Language Label detection
                val isHindi = HINDI_REGEX.matcher(combinedText).find()
                val isBengali = BENGALI_REGEX.matcher(combinedText).find()
                val isRegional = REGIONAL_INDIAN_REGEX.matcher(combinedText).find()
                val isMulti = MULTI_REGEX.matcher(combinedText).find()

                val languageLabel = when {
                    isHindi && (isRegional || isMulti || combinedText.contains("eng")) -> "Hindi + Multi 🇮🇳"
                    isHindi -> "Hindi Dubbed 🇮🇳"
                    isBengali -> "Bengali Dubbed 🇧🇩"
                    isRegional -> "South Indian Regional 🇮🇳"
                    isMulti -> "Multi-Audio 🌐"
                    else -> "English 🇬🇧"
                }
                val isHindiOrDual = isHindi || isBengali || isRegional || isMulti

                val languageScore = when {
                    isHindi -> 4000
                    isBengali -> 3500
                    isRegional -> 3000
                    isMulti -> 2000
                    else -> 1000
                }

                // Build Magnet URI
                val encodedDn = try {
                    URLEncoder.encode(filename, "UTF-8")
                } catch (_: Exception) {
                    filename
                }
                val trackersStr = TRACKERS.joinToString("") { "&tr=" + URLEncoder.encode(it, "UTF-8") }
                val magnetUri = "magnet:?xt=urn:btih:$infoHash&dn=$encodedDn$trackersStr"

                results.add(
                    TorrentStream(
                        name = rawName,
                        title = rawTitle,
                        infoHash = infoHash,
                        fileIdx = fileIdx,
                        quality = quality,
                        sizeStr = sizeStr,
                        seeders = seeders,
                        isHindiOrDual = isHindiOrDual,
                        languageLabel = languageLabel,
                        filename = filename,
                        magnetUri = magnetUri,
                        languageScore = languageScore
                    )
                )
            }

            // Priority: Hindi/Bengali first, then Regional, then Multi-Audio, then English, then highest seeders
            results.sortedWith(
                compareByDescending<TorrentStream> { it.languageScore }
                    .thenByDescending { it.seeders }
            )
        } catch (_: Exception) {
            emptyList()
        }
    }
}
