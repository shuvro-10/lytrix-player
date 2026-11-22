package com.musicplayer.lytrix.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Privacy-preserving SponsorBlock client (hash-prefix API: sends only the first 4 hex chars of SHA-256).
 */
object SponsorBlockManager {
    private const val TAG = "SponsorBlock"
    private const val BASE_URL = "https://sponsor.ajay.app"
    private const val HASH_PREFIX_LEN = 4
    private const val CACHE_TTL_MS = 30 * 60 * 1000L

    val ALL_CATEGORIES = listOf(
        "music_offtopic",
        "sponsor",
        "selfpromo",
        "interaction",
        "intro",
        "outro",
        "preview",
        "filler"
    )

    const val DEFAULT_CATEGORIES = "sponsor,selfpromo,intro,outro,music_offtopic"

    fun categoryDisplayName(category: String): String = when (category) {
        "music_offtopic" -> "Non-Music Section"
        "sponsor" -> "Sponsor"
        "selfpromo" -> "Self-Promotion / Unpaid"
        "interaction" -> "Interaction Reminder"
        "intro" -> "Intro / Intermission"
        "outro" -> "Outro / Endcards"
        "preview" -> "Preview / Recap"
        "filler" -> "Filler Tangent"
        else -> category.replaceFirstChar { it.uppercaseChar() }
    }

    fun parseCategories(raw: String?): Set<String> {
        val source = if (raw.isNullOrBlank()) DEFAULT_CATEGORIES else raw
        return source.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    @Serializable
    data class Segment(
        val category: String,
        val segment: List<Double>,
        val videoDuration: Double = 0.0,
        val actionType: String = "skip"
    ) {
        val startMs: Long get() = ((segment.getOrNull(0) ?: 0.0) * 1000.0).toLong()
        val endMs: Long get() = ((segment.getOrNull(1) ?: 0.0) * 1000.0).toLong()
    }

    @Serializable
    private data class HashPrefixEntry(
        val videoID: String,
        val segments: List<Segment> = emptyList()
    )

    private data class CacheEntry(
        val segments: List<Segment>,
        val fetchedAt: Long
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    suspend fun fetchSegments(
        videoId: String,
        categories: Collection<String>
    ): List<Segment> = withContext(Dispatchers.IO) {
        if (videoId.isBlank() || categories.isEmpty()) return@withContext emptyList()

        cache[videoId]?.takeIf { System.currentTimeMillis() - it.fetchedAt < CACHE_TTL_MS }?.let {
            return@withContext filterAndSort(it.segments, categories)
        }

        val hashPrefix = sha256(videoId).take(HASH_PREFIX_LEN)
        val urlBuilder = "$BASE_URL/api/skipSegments/$hashPrefix".toHttpUrl().newBuilder()
        ALL_CATEGORIES.forEach { urlBuilder.addQueryParameter("category", it) }
        urlBuilder.addQueryParameter("actionType", "skip")

        val request = Request.Builder()
            .url(urlBuilder.build())
            .get()
            .build()

        val allEntries: List<HashPrefixEntry> = runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    if (response.code == 404) emptyList() else throw IllegalStateException("HTTP ${response.code}")
                } else {
                    val body = response.body?.string().orEmpty()
                    if (body.isBlank()) emptyList() else json.decodeFromString<List<HashPrefixEntry>>(body)
                }
            }
        }.getOrElse { e ->
            Timber.tag(TAG).w("SponsorBlock fetch skipped for $videoId: ${e.message}")
            return@withContext emptyList()
        }

        val segments = allEntries
            .firstOrNull { it.videoID.equals(videoId, ignoreCase = true) }
            ?.segments
            .orEmpty()

        cache[videoId] = CacheEntry(segments, System.currentTimeMillis())
        filterAndSort(segments, categories)
    }

    fun invalidate(videoId: String) {
        cache.remove(videoId)
    }

    private fun filterAndSort(
        segments: List<Segment>,
        categories: Collection<String>
    ): List<Segment> = segments
        .filter { it.actionType == "skip" && it.category in categories && it.endMs > it.startMs }
        .sortedBy { it.startMs }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            val v = b.toInt() and 0xff
            if (v < 16) sb.append('0')
            sb.append(Integer.toHexString(v))
        }
        return sb.toString()
    }
}
