package com.musicplayer.lytrix.media

import android.content.Context
import android.util.Log
import com.musicplayer.lytrix.PodcastEpisode
import com.musicplayer.lytrix.PodcastInfo
import com.musicplayer.lytrix.innertube.YouTube
import com.musicplayer.lytrix.innertube.models.EpisodeItem
import com.musicplayer.lytrix.innertube.models.PlaylistItem
import com.musicplayer.lytrix.innertube.models.PodcastItem
import com.musicplayer.lytrix.innertube.models.SongItem
import com.musicplayer.lytrix.innertube.models.VideoItem
import com.musicplayer.lytrix.innertube.models.YTItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

enum class MediaHubKind {
    PODCAST,
    AUDIOBOOK,
}

enum class MediaHubSource {
    APPLE,
    YOUTUBE,
    LIBRIVOX,
}

data class MediaHubCard(
    val id: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String,
    val kind: MediaHubKind,
    val source: MediaHubSource,
    val isCollection: Boolean,
    val audioUrl: String = "",
    val durationSec: Int = 0,
    val rssUrl: String = "",
    val channelId: String? = null,
)

data class MediaHubSection(
    val id: String,
    val title: String,
    val items: List<MediaHubCard>,
)

data class MediaHubFeedData(
    val kind: MediaHubKind,
    val topicKey: String,
    val fetchedAtMs: Long,
    val sections: List<MediaHubSection>,
)

data class MediaHubCollectionDetails(
    val id: String,
    val title: String,
    val author: String,
    val description: String,
    val imageUrl: String,
    val kind: MediaHubKind,
    val source: MediaHubSource,
    val items: List<PodcastEpisode>,
    val channelId: String? = null,
)

/**
 * Global, location-aware live repository for Podcasts and Audiobooks.
 * - Podcasts: YouTube Music live podcasts (1 lightweight call, location-aware) + Apple Podcasts
 *   official live daily genre charts (auto-detecting user country via Locale/TimeZone).
 * - Audiobooks: YouTube full audiobooks (location-aware) + LibriVox free chapter-by-chapter
 *   classics + spam-filtered Apple audio stories.
 * - 100% on-demand (0 startup calls) with 6-hour disk + memory cache to prevent rate limits.
 */
object MediaHubRepository {
    private const val TAG = "MediaHubRepo"
    private const val PREFS_NAME = "media_hub_live_cache_v2"
    private const val CACHE_TTL_MS = 6L * 60L * 60L * 1000L // 6 hours
    private const val UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

    val PODCAST_TOPIC_CHIPS: List<Pair<String, String>> = listOf(
        "all" to "All",
        "charts" to "Top Charts",
        "fiction" to "Fiction & Stories",
        "crime" to "True Crime",
        "comedy" to "Comedy",
        "business" to "Business",
        "education" to "Education",
        "health" to "Health & Fitness",
        "history" to "History",
        "science" to "Science & Tech",
    )

    val AUDIOBOOK_TOPIC_CHIPS: List<Pair<String, String>> = listOf(
        "all" to "All",
        "trending" to "Trending",
        "mystery" to "Mystery & Thriller",
        "horror" to "Horror",
        "romance" to "Romance",
        "scifi" to "Sci-Fi",
        "selfhelp" to "Self-Help",
        "classics" to "Classics",
        "fantasy" to "Fantasy",
        "biography" to "Biography",
    )

    private val APPLE_GENRE_IDS = mapOf(
        "fiction" to 1483,
        "crime" to 1488,
        "comedy" to 1303,
        "business" to 1321,
        "education" to 1304,
        "health" to 1512,
        "history" to 1487,
        "science" to 1533,
    )

    private val SPAM_KEYWORDS = listOf(
        "free trial",
        "download full",
        "audible free",
        "trial offer",
        "breathwork in bed",
        "morning routine before work",
        "on the train home reflecting",
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    private val memoryFeedCache = ConcurrentHashMap<String, MediaHubFeedData>()
    private val memoryCollectionCache = ConcurrentHashMap<String, Pair<Long, MediaHubCollectionDetails>>()

    private fun cacheKey(kind: MediaHubKind, topicKey: String) = "${kind.name.lowercase()}_${topicKey.lowercase()}"

    fun isCacheFresh(data: MediaHubFeedData?): Boolean {
        if (data == null || data.sections.isEmpty()) return false
        return (System.currentTimeMillis() - data.fetchedAtMs) < CACHE_TTL_MS
    }

    fun getCachedFeed(context: Context, kind: MediaHubKind, topicKey: String): MediaHubFeedData? {
        val key = cacheKey(kind, topicKey)
        memoryFeedCache[key]?.let { return it }
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(key, null) ?: return null
        return runCatching {
            val parsed = deserializeFeed(raw, kind, topicKey)
            if (parsed != null) memoryFeedCache[key] = parsed
            parsed
        }.getOrNull()
    }

    private fun saveCachedFeed(context: Context, data: MediaHubFeedData) {
        val key = cacheKey(data.kind, data.topicKey)
        memoryFeedCache[key] = data
        runCatching {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(key, serializeFeed(data))
                .apply()
        }
    }

    private fun detectAppleCountryCode(): String {
        val localeCc = Locale.getDefault().country.lowercase(Locale.US)
        val supportedRich = setOf("us", "gb", "in", "ca", "au", "de", "fr", "es", "it", "br", "mx", "jp", "kr", "nl", "se", "no", "pl", "ie", "nz", "sg", "id", "ph")
        if (localeCc in supportedRich) return localeCc
        val tz = TimeZone.getDefault().id.lowercase(Locale.US)
        return when {
            tz.contains("dhaka") || tz.contains("kolkata") || tz.contains("calcutta") || tz.contains("karachi") || tz.contains("kathmandu") || tz.contains("colombo") -> "in"
            tz.contains("europe/london") -> "gb"
            tz.contains("europe/berlin") -> "de"
            tz.contains("europe/paris") -> "fr"
            tz.contains("asia/tokyo") -> "jp"
            tz.contains("asia/seoul") -> "kr"
            else -> "us"
        }
    }

    suspend fun fetchPodcastFeed(
        context: Context,
        topicKey: String = "all",
        forceRefresh: Boolean = false,
        ytPodcastChipParams: String? = null,
    ): MediaHubFeedData? = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            val cached = getCachedFeed(context, MediaHubKind.PODCAST, topicKey)
            if (isCacheFresh(cached)) return@withContext cached
        }

        val cc = detectAppleCountryCode()
        val sections = mutableListOf<MediaHubSection>()

        try {
            coroutineScope {
                when (topicKey.lowercase()) {
                    "all" -> {
                        val ytDef = async { fetchYouTubePodcastSections(ytPodcastChipParams) }
                        val topDef = async { fetchApplePodcastChart(cc, null, "Top Podcasts") }
                        val crimeDef = async { fetchApplePodcastChart(cc, APPLE_GENRE_IDS["crime"], "True Crime & Mysteries") }
                        val comDef = async { fetchApplePodcastChart(cc, APPLE_GENRE_IDS["comedy"], "Comedy & Entertainment") }
                        val bizDef = async { fetchApplePodcastChart(cc, APPLE_GENRE_IDS["business"], "Business, Finance & Tech") }
                        val fictionDef = async { fetchApplePodcastChart(cc, APPLE_GENRE_IDS["fiction"], "Stories & Audio Fiction") }
                        val healthDef = async { fetchApplePodcastChart(cc, APPLE_GENRE_IDS["health"], "Health, Mind & Fitness") }

                        sections.addAll(ytDef.await())
                        topDef.await()?.let(sections::add)
                        crimeDef.await()?.let(sections::add)
                        comDef.await()?.let(sections::add)
                        bizDef.await()?.let(sections::add)
                        fictionDef.await()?.let(sections::add)
                        healthDef.await()?.let(sections::add)
                    }
                    "charts" -> {
                        val localDef = async { fetchApplePodcastChart(cc, null, "Top Podcasts in Your Region") }
                        val globalDef = async {
                            if (cc != "us") fetchApplePodcastChart("us", null, "Global Top Podcasts") else null
                        }
                        val ytDef = async { fetchYouTubePodcastSections(ytPodcastChipParams) }
                        val ytTrendDef = async { fetchYouTubePodcastTopicSearch("trending podcast show", "Trending Video Podcasts") }
                        localDef.await()?.let(sections::add)
                        globalDef.await()?.let(sections::add)
                        sections.addAll(ytDef.await().take(2))
                        ytTrendDef.await()?.let(sections::add)
                    }
                    else -> {
                        val label = PODCAST_TOPIC_CHIPS.firstOrNull { it.first == topicKey }?.second ?: topicKey
                        val genreId = APPLE_GENRE_IDS[topicKey]
                        val appleLocal = async { fetchApplePodcastChart(cc, genreId, "Top $label in Your Region") }
                        val appleGlobal = async {
                            if (cc != "us") fetchApplePodcastChart("us", genreId, "Global $label Hits") else null
                        }
                        val ytShows = async { fetchYouTubePodcastTopicSearch("$label podcast show", "Popular $label Shows on YouTube") }
                        val ytEpisodes = async { fetchYouTubePodcastTopicSearch("$label podcast episode documentary", "$label Discussions & Stories") }

                        appleLocal.await()?.let(sections::add)
                        appleGlobal.await()?.let(sections::add)
                        ytShows.await()?.let(sections::add)
                        ytEpisodes.await()?.let(sections::add)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchPodcastFeed error: ${e.message}")
        }

        val cleanSections = sections.filter { it.items.isNotEmpty() }.distinctBy { it.title.lowercase(Locale.US) }
        if (cleanSections.isEmpty()) return@withContext getCachedFeed(context, MediaHubKind.PODCAST, topicKey)

        val result = MediaHubFeedData(
            kind = MediaHubKind.PODCAST,
            topicKey = topicKey,
            fetchedAtMs = System.currentTimeMillis(),
            sections = cleanSections,
        )
        saveCachedFeed(context, result)
        result
    }

    suspend fun fetchAudiobookFeed(
        context: Context,
        topicKey: String = "all",
        forceRefresh: Boolean = false,
    ): MediaHubFeedData? = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            val cached = getCachedFeed(context, MediaHubKind.AUDIOBOOK, topicKey)
            if (isCacheFresh(cached)) return@withContext cached
        }

        val sections = mutableListOf<MediaHubSection>()

        try {
            coroutineScope {
                when (topicKey.lowercase()) {
                    "all" -> {
                        val ytTrending = async { fetchYouTubeAudiobookSearch("popular full audiobook", "Featured Audiobooks") }
                        val ytThriller = async { fetchYouTubeAudiobookSearch("mystery thriller full audiobook", "Mystery & Thriller") }
                        val ytBestsellers = async { fetchYouTubeAudiobookSearch("bestseller full audiobook", "Worldwide Bestsellers") }
                        val ytHorror = async { fetchYouTubeAudiobookSearch("horror full audiobook", "Horror & Supernatural") }
                        val ytSelfHelp = async { fetchYouTubeAudiobookSearch("self help full audiobook", "Self-Help & Growth") }
                        val appleStories = async { fetchAppleAudioStories("audio story drama", "Audio Stories & Dramas") }
                        val ytSciFi = async { fetchYouTubeAudiobookSearch("sci fi fantasy full audiobook", "Sci-Fi & Epic Fantasy") }

                        ytTrending.await()?.let(sections::add)
                        ytThriller.await()?.let(sections::add)
                        ytBestsellers.await()?.let(sections::add)
                        ytHorror.await()?.let(sections::add)
                        ytSelfHelp.await()?.let(sections::add)
                        appleStories.await()?.let(sections::add)
                        ytSciFi.await()?.let(sections::add)
                    }
                    "trending" -> {
                        val s1 = async { fetchYouTubeAudiobookSearch("trending full audiobook", "Trending Audiobooks") }
                        val s2 = async { fetchYouTubeAudiobookSearch("popular bestseller full audiobook", "Popular Bestsellers") }
                        val s3 = async { fetchAppleAudioStories("audio story trending", "Trending Audio Stories") }
                        val s4 = async { fetchYouTubeAudiobookSearch("top rated full audiobook", "Top Rated Audiobooks") }
                        s1.await()?.let(sections::add)
                        s2.await()?.let(sections::add)
                        s3.await()?.let(sections::add)
                        s4.await()?.let(sections::add)
                    }
                    "mystery" -> {
                        val s1 = async { fetchYouTubeAudiobookSearch("mystery thriller full audiobook", "Mystery & Thriller Audiobooks") }
                        val s2 = async { fetchYouTubeAudiobookSearch("detective crime novel audiobook", "Detective & Crime Novels") }
                        val s3 = async { fetchAppleAudioStories("mystery crime audio story", "Mystery Audio Stories") }
                        val s4 = async { fetchYouTubeAudiobookSearch("sherlock holmes agatha christie audiobook", "Classic Detective Fiction") }
                        s1.await()?.let(sections::add)
                        s2.await()?.let(sections::add)
                        s3.await()?.let(sections::add)
                        s4.await()?.let(sections::add)
                    }
                    "horror" -> {
                        val s1 = async { fetchYouTubeAudiobookSearch("horror full audiobook", "Horror Full Audiobooks") }
                        val s2 = async { fetchYouTubeAudiobookSearch("ghost supernatural stories audiobooks", "Ghost & Supernatural Stories") }
                        val s3 = async { fetchAppleAudioStories("horror supernatural audio story", "Horror Audio Dramas") }
                        val s4 = async { fetchYouTubeAudiobookSearch("gothic horror classic audiobook", "Gothic & Classic Horror") }
                        s1.await()?.let(sections::add)
                        s2.await()?.let(sections::add)
                        s3.await()?.let(sections::add)
                        s4.await()?.let(sections::add)
                    }
                    "romance" -> {
                        val s1 = async { fetchYouTubeAudiobookSearch("romance full audiobook", "Romance Audiobooks") }
                        val s2 = async { fetchYouTubeAudiobookSearch("romantic love story novel audiobook", "Romantic Novels & Stories") }
                        val s3 = async { fetchAppleAudioStories("romance drama audio story", "Romantic Audio Stories") }
                        val s4 = async { fetchYouTubeAudiobookSearch("classic romance novel audiobook", "Classic Love Stories") }
                        s1.await()?.let(sections::add)
                        s2.await()?.let(sections::add)
                        s3.await()?.let(sections::add)
                        s4.await()?.let(sections::add)
                    }
                    "scifi" -> {
                        val s1 = async { fetchYouTubeAudiobookSearch("sci fi full audiobook", "Sci-Fi Full Audiobooks") }
                        val s2 = async { fetchYouTubeAudiobookSearch("space cyberpunk adventure audiobook", "Space & Cyberpunk Adventures") }
                        val s3 = async { fetchAppleAudioStories("science fiction audio story", "Sci-Fi Audio Dramas") }
                        val s4 = async { fetchYouTubeAudiobookSearch("classic science fiction novel audiobook", "Epic Sci-Fi Classics") }
                        s1.await()?.let(sections::add)
                        s2.await()?.let(sections::add)
                        s3.await()?.let(sections::add)
                        s4.await()?.let(sections::add)
                    }
                    "selfhelp" -> {
                        val s1 = async { fetchYouTubeAudiobookSearch("self help full audiobook", "Self-Help Full Audiobooks") }
                        val s2 = async { fetchYouTubeAudiobookSearch("habits mindset productivity audiobook", "Mindset, Habits & Productivity") }
                        val s3 = async { fetchAppleAudioStories("motivation personal growth audio", "Personal Growth Stories") }
                        val s4 = async { fetchYouTubeAudiobookSearch("psychology life philosophy audiobook", "Psychology & Life Lessons") }
                        s1.await()?.let(sections::add)
                        s2.await()?.let(sections::add)
                        s3.await()?.let(sections::add)
                        s4.await()?.let(sections::add)
                    }
                    "classics" -> {
                        val s1 = async { fetchYouTubeAudiobookSearch("classic literature novel full audiobook", "Classic World Literature") }
                        val s2 = async { fetchYouTubeAudiobookSearch("classic literature full audiobook", "Timeless Masterpieces on YouTube") }
                        val s3 = async { fetchAppleAudioStories("classic literature audio story", "Classic Audio Dramas") }
                        val s4 = async { fetchYouTubeAudiobookSearch("historical novel classic full audiobook", "Great Historical Novels") }
                        s1.await()?.let(sections::add)
                        s2.await()?.let(sections::add)
                        s3.await()?.let(sections::add)
                        s4.await()?.let(sections::add)
                    }
                    "fantasy" -> {
                        val s1 = async { fetchYouTubeAudiobookSearch("fantasy full audiobook", "Epic Fantasy Audiobooks") }
                        val s2 = async { fetchYouTubeAudiobookSearch("magic myth legend novel audiobook", "Magic, Myths & Legends") }
                        val s3 = async { fetchAppleAudioStories("fantasy audio story drama", "Fantasy Audio Dramas") }
                        val s4 = async { fetchYouTubeAudiobookSearch("dark fantasy adventure audiobook", "Dark Fantasy & Lore") }
                        s1.await()?.let(sections::add)
                        s2.await()?.let(sections::add)
                        s3.await()?.let(sections::add)
                        s4.await()?.let(sections::add)
                    }
                    "biography" -> {
                        val s1 = async { fetchYouTubeAudiobookSearch("biography full audiobook", "Biographies & Memoirs") }
                        val s2 = async { fetchYouTubeAudiobookSearch("autobiography inspiring life story audiobook", "Inspiring Life Stories") }
                        val s3 = async { fetchAppleAudioStories("biography history audio story", "Historical Biographies") }
                        val s4 = async { fetchYouTubeAudiobookSearch("history leaders biography audiobook", "Icons & World Leaders") }
                        s1.await()?.let(sections::add)
                        s2.await()?.let(sections::add)
                        s3.await()?.let(sections::add)
                        s4.await()?.let(sections::add)
                    }
                    else -> {
                        val label = AUDIOBOOK_TOPIC_CHIPS.firstOrNull { it.first == topicKey }?.second ?: topicKey
                        val s1 = async { fetchYouTubeAudiobookSearch("$label full audiobook", "Top $label Audiobooks") }
                        val s2 = async { fetchYouTubeAudiobookSearch("$label popular novel audiobook", "Popular $label Stories") }
                        val s3 = async { fetchAppleAudioStories("$label audio story", "$label Audio Dramas") }
                        s1.await()?.let(sections::add)
                        s2.await()?.let(sections::add)
                        s3.await()?.let(sections::add)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchAudiobookFeed error: ${e.message}")
        }

        val cleanSections = sections.filter { it.items.isNotEmpty() }.distinctBy { it.title.lowercase(Locale.US) }
        if (cleanSections.isEmpty()) return@withContext getCachedFeed(context, MediaHubKind.AUDIOBOOK, topicKey)

        val result = MediaHubFeedData(
            kind = MediaHubKind.AUDIOBOOK,
            topicKey = topicKey,
            fetchedAtMs = System.currentTimeMillis(),
            sections = cleanSections,
        )
        saveCachedFeed(context, result)
        result
    }

    suspend fun fetchCollectionDetails(card: MediaHubCard): MediaHubCollectionDetails? = withContext(Dispatchers.IO) {
        val key = "${card.source.name}:${card.id}"
        memoryCollectionCache[key]?.let { (ts, cached) ->
            if ((System.currentTimeMillis() - ts) < CACHE_TTL_MS && cached.items.isNotEmpty()) {
                return@withContext cached
            }
        }
        val fetched = when (card.source) {
            MediaHubSource.APPLE -> fetchAppleShowDetails(card)
            MediaHubSource.YOUTUBE -> fetchYouTubeCollectionDetails(card)
            MediaHubSource.LIBRIVOX -> null
        }
        if (fetched != null && fetched.items.isNotEmpty()) {
            memoryCollectionCache[key] = System.currentTimeMillis() to fetched
        }
        fetched
    }

    // -------------------------------------------------------------------------
    // Apple Podcasts Live Charts & Show Lookup
    // -------------------------------------------------------------------------

    private fun fetchApplePodcastChart(cc: String, genreId: Int?, title: String): MediaHubSection? {
        val genrePart = if (genreId != null) "/genre=$genreId" else ""
        val url = "https://itunes.apple.com/$cc/rss/toppodcasts/limit=20$genrePart/json"
        val items = parseAppleChartEntries(url)
        val finalItems = if (items.size >= 5 || cc == "us") {
            items
        } else {
            parseAppleChartEntries("https://itunes.apple.com/us/rss/toppodcasts/limit=20$genrePart/json")
        }
        if (finalItems.isEmpty()) return null
        return MediaHubSection(
            id = "apple_${cc}_${genreId ?: "top"}",
            title = title,
            items = finalItems,
        )
    }

    private fun parseAppleChartEntries(url: String): List<MediaHubCard> {
        val body = executeGet(url) ?: return emptyList()
        return runCatching {
            val entries = JSONObject(body).optJSONObject("feed")?.optJSONArray("entry") ?: return emptyList()
            buildList {
                for (i in 0 until entries.length()) {
                    val obj = entries.optJSONObject(i) ?: continue
                    val id = obj.optJSONObject("id")?.optJSONObject("attributes")?.optString("im:id").orEmpty().trim()
                    val name = obj.optJSONObject("im:name")?.optString("label").orEmpty().trim()
                    val artist = obj.optJSONObject("im:artist")?.optString("label").orEmpty().trim()
                    val imgArr = obj.optJSONArray("im:image")
                    val image = if (imgArr != null && imgArr.length() > 0) {
                        imgArr.optJSONObject(imgArr.length() - 1)?.optString("label").orEmpty()
                            .replace("170x170bb", "600x600bb")
                            .replace("100x100bb", "600x600bb")
                    } else ""
                    if (id.isBlank() || name.isBlank()) continue
                    add(
                        MediaHubCard(
                            id = id,
                            title = name,
                            subtitle = if (artist.isNotBlank()) "Podcast • $artist" else "Apple Podcasts",
                            imageUrl = image,
                            kind = MediaHubKind.PODCAST,
                            source = MediaHubSource.APPLE,
                            isCollection = true,
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun fetchAppleAudioStories(query: String, sectionTitle: String): MediaHubSection? {
        val url = "https://itunes.apple.com/search?term=${URLEncoder.encode(query, "UTF-8")}&media=podcast&entity=podcastEpisode&limit=20"
        val body = executeGet(url) ?: return null
        val cards = runCatching {
            val arr = JSONObject(body).optJSONArray("results") ?: return@runCatching emptyList<MediaHubCard>()
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optString("trackId").trim()
                    val title = obj.optString("trackName").trim()
                    val collection = obj.optString("collectionName").trim()
                    val audio = obj.optString("episodeUrl").trim()
                    val img = obj.optString("artworkUrl600").ifBlank { obj.optString("artworkUrl100") }
                    val durSec = (obj.optLong("trackTimeMillis", 0L) / 1000L).toInt()
                    if (id.isBlank() || title.isBlank() || audio.isBlank()) continue
                    val combinedLower = "$title $collection".lowercase(Locale.US)
                    if (SPAM_KEYWORDS.any { combinedLower.contains(it) }) continue
                    val durLabel = formatMinutes(durSec)
                    val sub = listOfNotNull(collection.takeIf { it.isNotBlank() }, durLabel.takeIf { it.isNotBlank() }).joinToString(" • ")
                    add(
                        MediaHubCard(
                            id = id,
                            title = title,
                            subtitle = sub.ifBlank { "Audiobook" },
                            imageUrl = img,
                            kind = MediaHubKind.AUDIOBOOK,
                            source = MediaHubSource.APPLE,
                            isCollection = false,
                            audioUrl = audio,
                            durationSec = durSec,
                        )
                    )
                }
            }.distinctBy { it.id }
        }.getOrDefault(emptyList())
        if (cards.isEmpty()) return null
        return MediaHubSection(id = "apple_ab_$query", title = sectionTitle, items = cards)
    }

    private fun fetchAppleShowDetails(card: MediaHubCard): MediaHubCollectionDetails? {
        val url = "https://itunes.apple.com/lookup?id=${URLEncoder.encode(card.id, "UTF-8")}&entity=podcastEpisode&limit=35"
        val body = executeGet(url) ?: return null
        return runCatching {
            val arr = JSONObject(body).optJSONArray("results") ?: return null
            var showTitle = card.title
            var author = card.subtitle.removePrefix("Podcast • ").ifBlank { "Apple Podcasts" }
            var showImg = card.imageUrl
            var desc = ""
            val episodes = mutableListOf<PodcastEpisode>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val wrapper = obj.optString("wrapperType")
                if (wrapper == "track" && obj.optString("kind") == "podcast") {
                    showTitle = obj.optString("collectionName").ifBlank { showTitle }
                    author = obj.optString("artistName").ifBlank { author }
                    showImg = obj.optString("artworkUrl600").ifBlank { showImg }
                } else if (wrapper == "podcastEpisode") {
                    val epId = obj.optString("trackId").ifBlank { "${card.id}_$i" }
                    val epTitle = obj.optString("trackName").ifBlank { "Episode" }
                    val epAudio = obj.optString("episodeUrl")
                    if (epAudio.isBlank()) continue
                    val epImg = obj.optString("artworkUrl600").ifBlank { obj.optString("artworkUrl160") }.ifBlank { showImg }
                    val durSec = (obj.optLong("trackTimeMillis", 0L) / 1000L).toInt()
                    if (desc.isBlank()) {
                        desc = obj.optString("shortDescription").ifBlank { obj.optString("description") }.take(400)
                    }
                    episodes.add(
                        PodcastEpisode(
                            id = epId,
                            title_original = epTitle,
                            audio = epAudio,
                            image = epImg,
                            audio_length_sec = durSec,
                            podcast = PodcastInfo(author),
                        )
                    )
                }
            }
            if (episodes.isEmpty()) return null
            MediaHubCollectionDetails(
                id = card.id,
                title = showTitle,
                author = author,
                description = desc,
                imageUrl = showImg,
                kind = card.kind,
                source = MediaHubSource.APPLE,
                items = episodes,
            )
        }.getOrNull()
    }

    // -------------------------------------------------------------------------
    // YouTube Live Podcasts & Full Audiobooks (Minimal On-Demand Calls)
    // -------------------------------------------------------------------------

    private suspend fun fetchYouTubePodcastSections(chipParams: String?): List<MediaHubSection> {
        val out = mutableListOf<MediaHubSection>()
        if (!chipParams.isNullOrBlank()) {
            val page = YouTube.home(params = chipParams).getOrNull()
            page?.sections?.forEachIndexed { idx, sec ->
                val cards = sec.items.mapNotNull { it.toMediaHubCard(MediaHubKind.PODCAST) }.distinctBy { it.id }
                if (cards.isNotEmpty()) {
                    out.add(MediaHubSection("yt_pod_home_$idx", sec.title, cards))
                }
            }
        }
        if (out.isEmpty()) {
            val search = YouTube.search("Podcasts", YouTube.SearchFilter.FILTER_PODCAST, useAccountContext = false).getOrNull()
            val shows = search?.items.orEmpty().mapNotNull { it.toMediaHubCard(MediaHubKind.PODCAST) }.distinctBy { it.id }
            if (shows.isNotEmpty()) {
                out.add(MediaHubSection("yt_pod_shows", "Popular Shows on YouTube", shows))
            }
        }
        return out.take(3)
    }

    private suspend fun fetchYouTubePodcastTopicSearch(query: String, title: String): MediaHubSection? {
        val search = YouTube.search(query, YouTube.SearchFilter.FILTER_PODCAST, useAccountContext = false).getOrNull()
        val shows = search?.items.orEmpty().mapNotNull { it.toMediaHubCard(MediaHubKind.PODCAST) }.distinctBy { it.id }.take(15)
        if (shows.isEmpty()) return null
        return MediaHubSection("yt_pod_topic_$query", title, shows)
    }

    private suspend fun fetchYouTubeAudiobookSearch(query: String, title: String): MediaHubSection? {
        val search = YouTube.search(query, YouTube.SearchFilter.FILTER_VIDEO, useAccountContext = false).getOrNull()
        val items = search?.items.orEmpty()
            .mapNotNull { it.toMediaHubCard(MediaHubKind.AUDIOBOOK) }
            .distinctBy { it.id }
            .take(15)
        if (items.isEmpty()) return null
        return MediaHubSection("yt_ab_$query", title, items)
    }

    private suspend fun fetchYouTubeCollectionDetails(card: MediaHubCard): MediaHubCollectionDetails? {
        val podPage = YouTube.podcast(card.id).getOrNull()
        val channelId = podPage?.podcast?.author?.id ?: card.channelId
        val episodes = if (podPage != null && podPage.episodes.isNotEmpty()) {
            podPage.episodes.map { ep ->
                val epChanId = ep.author?.id ?: channelId
                PodcastEpisode(
                    id = ep.id,
                    title_original = ep.title,
                    audio = "yt:${ep.id}",
                    image = ep.thumbnail.ifBlank { card.imageUrl },
                    audio_length_sec = ep.duration ?: 0,
                    podcast = PodcastInfo(ep.author?.name ?: podPage.podcast.author?.name ?: card.title, epChanId),
                    channelId = epChanId,
                )
            }
        } else {
            val cleanId = card.id.removePrefix("MPSP").removePrefix("VL")
            val pl = YouTube.playlist("VL$cleanId").getOrNull()
            val plChanId = pl?.playlist?.author?.id ?: channelId
            pl?.songs.orEmpty().map { s ->
                val sChanId = s.artists.firstOrNull()?.id ?: plChanId
                PodcastEpisode(
                    id = s.id,
                    title_original = s.title,
                    audio = "yt:${s.id}",
                    image = s.thumbnail.ifBlank { card.imageUrl },
                    audio_length_sec = s.duration ?: 0,
                    podcast = PodcastInfo(s.artists.joinToString(", ") { it.name }.ifBlank { card.title }, sChanId),
                    channelId = sChanId,
                )
            }
        }
        if (episodes.isEmpty()) return null
        return MediaHubCollectionDetails(
            id = card.id,
            title = podPage?.podcast?.title?.takeIf { it.isNotBlank() } ?: card.title,
            author = podPage?.podcast?.author?.name?.takeIf { it.isNotBlank() } ?: card.subtitle.removePrefix("Podcast • ").ifBlank { "YouTube" },
            description = "",
            imageUrl = podPage?.podcast?.thumbnail?.takeIf { it.isNotBlank() } ?: card.imageUrl,
            kind = card.kind,
            source = MediaHubSource.YOUTUBE,
            items = episodes,
            channelId = channelId,
        )
    }

    // -------------------------------------------------------------------------
    // LibriVox Official Free Public-Domain Audiobooks (Chapter-by-Chapter MP3)
    // -------------------------------------------------------------------------

    private fun fetchLibriVoxClassics(title: String): MediaHubSection? = null

    private fun fetchLibriVoxBookDetails(card: MediaHubCard): MediaHubCollectionDetails? = null

    // -------------------------------------------------------------------------
    // Helpers & JSON Cache Serialization
    // -------------------------------------------------------------------------

    private fun YTItem.toMediaHubCard(kind: MediaHubKind): MediaHubCard? = when (this) {
        is PodcastItem -> MediaHubCard(
            id = id,
            title = title,
            subtitle = author?.name?.takeIf { it.isNotBlank() }?.let { "Podcast • $it" } ?: "YouTube Podcast",
            imageUrl = thumbnail.orEmpty(),
            kind = kind,
            source = MediaHubSource.YOUTUBE,
            isCollection = true,
            channelId = author?.id,
        )
        is EpisodeItem -> MediaHubCard(
            id = id,
            title = title,
            subtitle = listOfNotNull(
                author?.name ?: podcast?.name,
                formatMinutes(duration ?: 0).takeIf { it.isNotBlank() },
            ).joinToString(" • ").ifBlank { if (kind == MediaHubKind.AUDIOBOOK) "Audiobook" else "Episode" },
            imageUrl = thumbnail,
            kind = kind,
            source = MediaHubSource.YOUTUBE,
            isCollection = false,
            audioUrl = "yt:$id",
            durationSec = duration ?: 0,
            channelId = author?.id,
        )
        is VideoItem -> MediaHubCard(
            id = id,
            title = title,
            subtitle = listOfNotNull(
                authors?.joinToString(", ") { it.name }?.takeIf { it.isNotBlank() },
                formatMinutes(duration ?: 0).takeIf { it.isNotBlank() },
            ).joinToString(" • ").ifBlank { if (kind == MediaHubKind.AUDIOBOOK) "Audiobook" else "Podcast" },
            imageUrl = thumbnail,
            kind = kind,
            source = MediaHubSource.YOUTUBE,
            isCollection = false,
            audioUrl = "yt:$id",
            durationSec = duration ?: 0,
            channelId = authors?.firstOrNull()?.id,
        )
        is SongItem -> MediaHubCard(
            id = id,
            title = title,
            subtitle = listOfNotNull(
                artists.joinToString(", ") { it.name }.takeIf { it.isNotBlank() },
                formatMinutes(duration ?: 0).takeIf { it.isNotBlank() },
            ).joinToString(" • ").ifBlank { if (kind == MediaHubKind.AUDIOBOOK) "Audiobook" else "Podcast" },
            imageUrl = thumbnail,
            kind = kind,
            source = MediaHubSource.YOUTUBE,
            isCollection = false,
            audioUrl = "yt:$id",
            durationSec = duration ?: 0,
            channelId = artists.firstOrNull()?.id,
        )
        is PlaylistItem -> MediaHubCard(
            id = id,
            title = title,
            subtitle = author?.name?.takeIf { it.isNotBlank() }?.let { "Collection • $it" } ?: "YouTube",
            imageUrl = thumbnail.orEmpty(),
            kind = kind,
            source = MediaHubSource.YOUTUBE,
            isCollection = true,
            channelId = author?.id,
        )
        else -> null
    }

    private fun executeGet(url: String): String? = runCatching {
        val req = Request.Builder().url(url).header("User-Agent", UA).build()
        client.newCall(req).execute().use { resp ->
            if (resp.isSuccessful) resp.body.string() else null
        }
    }.getOrNull()

    fun formatMinutes(sec: Int): String {
        if (sec <= 0) return ""
        val hrs = sec / 3600
        val mins = (sec % 3600) / 60
        return when {
            hrs > 0 && mins > 0 -> "${hrs}h ${mins}m"
            hrs > 0 -> "${hrs}h"
            mins > 0 -> "$mins min"
            else -> "${sec}s"
        }
    }

    private fun parseHmsToSec(raw: String): Int {
        val parts = raw.split(":").mapNotNull { it.trim().toIntOrNull() }
        return when (parts.size) {
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            2 -> parts[0] * 60 + parts[1]
            1 -> parts[0]
            else -> 0
        }
    }

    private fun cleanHtml(raw: String): String =
        raw.replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("<[^>]+>"), "")
            .trim()

    private fun serializeFeed(data: MediaHubFeedData): String {
        val root = JSONObject()
        root.put("fetchedAtMs", data.fetchedAtMs)
        val secArr = JSONArray()
        data.sections.forEach { sec ->
            val sObj = JSONObject()
            sObj.put("id", sec.id)
            sObj.put("title", sec.title)
            val itArr = JSONArray()
            sec.items.forEach { c ->
                val cObj = JSONObject()
                cObj.put("id", c.id)
                cObj.put("title", c.title)
                cObj.put("subtitle", c.subtitle)
                cObj.put("imageUrl", c.imageUrl)
                cObj.put("source", c.source.name)
                cObj.put("isCollection", c.isCollection)
                cObj.put("audioUrl", c.audioUrl)
                cObj.put("durationSec", c.durationSec)
                cObj.put("rssUrl", c.rssUrl)
                itArr.put(cObj)
            }
            sObj.put("items", itArr)
            secArr.put(sObj)
        }
        root.put("sections", secArr)
        return root.toString()
    }

    private fun deserializeFeed(raw: String, kind: MediaHubKind, topicKey: String): MediaHubFeedData? {
        val root = JSONObject(raw)
        val fetchedAt = root.optLong("fetchedAtMs", 0L)
        val secArr = root.optJSONArray("sections") ?: return null
        val sections = mutableListOf<MediaHubSection>()
        for (i in 0 until secArr.length()) {
            val sObj = secArr.optJSONObject(i) ?: continue
            val itArr = sObj.optJSONArray("items") ?: continue
            val cards = mutableListOf<MediaHubCard>()
            for (j in 0 until itArr.length()) {
                val cObj = itArr.optJSONObject(j) ?: continue
                val src = runCatching { MediaHubSource.valueOf(cObj.optString("source")) }.getOrDefault(MediaHubSource.APPLE)
                cards.add(
                    MediaHubCard(
                        id = cObj.optString("id"),
                        title = cObj.optString("title"),
                        subtitle = cObj.optString("subtitle"),
                        imageUrl = cObj.optString("imageUrl"),
                        kind = kind,
                        source = src,
                        isCollection = cObj.optBoolean("isCollection", false),
                        audioUrl = cObj.optString("audioUrl"),
                        durationSec = cObj.optInt("durationSec", 0),
                        rssUrl = cObj.optString("rssUrl"),
                    )
                )
            }
            if (cards.isNotEmpty()) {
                sections.add(MediaHubSection(sObj.optString("id"), sObj.optString("title"), cards))
            }
        }
        if (sections.isEmpty()) return null
        return MediaHubFeedData(kind, topicKey, fetchedAt, sections)
    }
}
