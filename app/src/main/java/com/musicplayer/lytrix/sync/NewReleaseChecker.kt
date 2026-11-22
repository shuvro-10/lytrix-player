package com.musicplayer.lytrix.sync

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Xml
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.musicplayer.lytrix.MainActivity
import com.musicplayer.lytrix.R
import com.musicplayer.lytrix.innertube.YouTube
import com.musicplayer.lytrix.innertube.models.AlbumItem
import com.musicplayer.lytrix.innertube.models.SongItem
import com.musicplayer.lytrix.innertube.models.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL

data class DiscoveredReleaseItem(
    val id: String,
    val browseId: String,
    val title: String,
    val artistName: String,
    val artistId: String,
    val releaseType: String, // "ALBUM", "SINGLE", "SONG", "VIDEO"
    val thumbnailUrl: String,
    val year: Int?,
    val discoveredAtMs: Long,
    val isNew: Boolean
)

data class NewReleaseCheckResult(
    val checkedArtistsCount: Int,
    val newlyFoundCount: Int,
    val items: List<DiscoveredReleaseItem>
)

/**
 * SimpMusic-inspired new-release watcher for artists in `subscribed_artists_prefs`.
 * Fetches artist release grids via InnerTube (with YouTube RSS channel fallback for UC channels),
 * preserves seen IDs cumulatively, surfaces an in-app feed, and pushes local alerts for fresh drops.
 */
object NewReleaseChecker {
    private const val PREF_NAME = "new_releases_prefs"
    private const val KEY_ENABLED = "alerts_enabled"
    private const val KEY_SEEN_IDS = "seen_release_ids"
    private const val KEY_SEEDED_ARTISTS = "seeded_artist_ids"
    private const val KEY_FEED_JSON = "discovered_releases_json"
    private const val KEY_LAST_CHECK_MS = "last_check_ms"

    const val ACTION_CHECK_RELEASES = "com.musicplayer.lytrix.action.CHECK_NEW_RELEASES"
    const val CHANNEL_ID = "lytrix_new_releases_channel"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
        if (enabled) {
            schedulePeriodicChecks(context)
        } else {
            cancelPeriodicChecks(context)
        }
    }

    fun getLastCheckMs(context: Context): Long =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_CHECK_MS, 0L)

    fun loadFeed(context: Context): List<DiscoveredReleaseItem> {
        val raw = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_FEED_JSON, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        DiscoveredReleaseItem(
                            id = o.optString("id"),
                            browseId = o.optString("browseId"),
                            title = o.optString("title"),
                            artistName = o.optString("artistName"),
                            artistId = o.optString("artistId"),
                            releaseType = o.optString("releaseType", "SINGLE"),
                            thumbnailUrl = o.optString("thumbnailUrl"),
                            year = o.optInt("year", 0).takeIf { it > 0 },
                            discoveredAtMs = o.optLong("discoveredAtMs", 0L),
                            isNew = o.optBoolean("isNew", false)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun markAllSeenInUi(context: Context) {
        val current = loadFeed(context).map { it.copy(isNew = false) }
        saveFeed(context, current)
    }

    fun schedulePeriodicChecks(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = periodicPendingIntent(context)
        val firstRun = System.currentTimeMillis() + AlarmManager.INTERVAL_HALF_DAY
        am.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            firstRun,
            AlarmManager.INTERVAL_HALF_DAY,
            pi
        )
    }

    fun cancelPeriodicChecks(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        am.cancel(periodicPendingIntent(context))
    }

    private fun periodicPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, NewReleaseReceiver::class.java).apply {
            action = ACTION_CHECK_RELEASES
        }
        return PendingIntent.getBroadcast(
            context,
            44_100,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    suspend fun checkForNewReleases(
        context: Context,
        pushNotification: Boolean = true
    ): NewReleaseCheckResult = withContext(Dispatchers.IO) {
        val subPrefs = context.getSharedPreferences("subscribed_artists_prefs", Context.MODE_PRIVATE)
        val subscribedIds = (subPrefs.getStringSet("subscribed_ids", emptySet()) ?: emptySet()).toList()
        if (subscribedIds.isEmpty()) {
            return@withContext NewReleaseCheckResult(0, 0, loadFeed(context))
        }

        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val seenIds = (prefs.getStringSet(KEY_SEEN_IDS, emptySet()) ?: emptySet()).toMutableSet()
        val seededArtists = (prefs.getStringSet(KEY_SEEDED_ARTISTS, emptySet()) ?: emptySet()).toMutableSet()
        val previousFeed = loadFeed(context).associateBy { it.id }.toMutableMap()
        val newlyDiscovered = mutableListOf<DiscoveredReleaseItem>()
        val nowMs = System.currentTimeMillis()

        for (artistId in subscribedIds.take(20)) {
            val metaJson = subPrefs.getString("artist_data_$artistId", null)
            val fallbackName = runCatching {
                JSONObject(metaJson ?: "").optString("name", "Subscribed Artist")
            }.getOrDefault("Subscribed Artist")

            val artistResult = runCatching { YouTube.artist(artistId).getOrNull() }.getOrNull()
            val artistDisplayName = artistResult?.artist?.title?.takeIf { it.isNotBlank() } ?: fallbackName
            val alreadySeeded = artistId in seededArtists
            val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)

            val discoveredForArtist = mutableListOf<Pair<DiscoveredReleaseItem, Boolean>>()
            artistResult?.sections?.forEach { section ->
                val secTitle = section.title
                val isReleaseGrid = secTitle.contains("album", ignoreCase = true) ||
                    secTitle.contains("single", ignoreCase = true) ||
                    secTitle.contains("release", ignoreCase = true) ||
                    secTitle.contains("latest", ignoreCase = true)
                section.items.forEachIndexed { idx, ytItem ->
                    val release = when (ytItem) {
                        is AlbumItem -> DiscoveredReleaseItem(
                            id = ytItem.browseId.ifBlank { ytItem.id },
                            browseId = ytItem.browseId,
                            title = ytItem.title,
                            artistName = artistDisplayName,
                            artistId = artistId,
                            releaseType = ytItem.releaseType.name,
                            thumbnailUrl = ytItem.thumbnail,
                            year = ytItem.year,
                            discoveredAtMs = nowMs,
                            isNew = false
                        )
                        is SongItem -> DiscoveredReleaseItem(
                            id = ytItem.id,
                            browseId = ytItem.id,
                            title = ytItem.title,
                            artistName = artistDisplayName,
                            artistId = artistId,
                            releaseType = "SONG",
                            thumbnailUrl = ytItem.thumbnail,
                            year = null,
                            discoveredAtMs = nowMs,
                            isNew = false
                        )
                        is VideoItem -> DiscoveredReleaseItem(
                            id = ytItem.id,
                            browseId = ytItem.id,
                            title = ytItem.title,
                            artistName = artistDisplayName,
                            artistId = artistId,
                            releaseType = "VIDEO",
                            thumbnailUrl = ytItem.thumbnail,
                            year = null,
                            discoveredAtMs = nowMs,
                            isNew = false
                        )
                        else -> null
                    }
                    if (release != null && release.id.isNotBlank()) {
                        val yr = release.year
                        val isEligibleAsNew = when {
                            yr != null -> yr >= currentYear - 1
                            isReleaseGrid && idx < 4 -> true
                            else -> false
                        }
                        discoveredForArtist.add(release to isEligibleAsNew)
                    }
                }
            }

            // Fallback RSS check for regular YouTube UC channels (seed or update with published-date recency)
            if (artistId.startsWith("UC")) {
                val rssCutoffMs = nowMs - 30L * 24L * 60L * 60L * 1000L
                val rssEntries = fetchRssEntries(artistId, artistDisplayName, nowMs)
                rssEntries.forEach { entry ->
                    val eligibleRss = discoveredForArtist.isEmpty() && entry.discoveredAtMs >= rssCutoffMs
                    discoveredForArtist.add(entry.copy(discoveredAtMs = nowMs) to eligibleRss)
                }
            }

            if (discoveredForArtist.isEmpty()) continue

            val knownFeedTitles = previousFeed.values
                .filter { it.artistId == artistId }
                .map { it.title.trim().lowercase() }
                .toSet()

            discoveredForArtist.distinctBy { it.first.id }.forEach { (item, eligibleAsNew) ->
                val isUnseen = item.id !in seenIds
                seenIds.add(item.id)
                val normalizedTitle = item.title.trim().lowercase()
                if (alreadySeeded && isUnseen && eligibleAsNew && normalizedTitle !in knownFeedTitles) {
                    val flagged = item.copy(isNew = true)
                    newlyDiscovered.add(flagged)
                    previousFeed[item.id] = flagged
                } else if (item.id !in previousFeed && (item.year == null || item.year >= currentYear - 1)) {
                    previousFeed[item.id] = item
                }
            }

            if (!alreadySeeded) {
                seededArtists.add(artistId)
            }
        }

        val sortedFeed = previousFeed.values
            .sortedWith(compareByDescending<DiscoveredReleaseItem> { it.isNew }.thenByDescending { it.discoveredAtMs })
            .take(60)

        prefs.edit()
            .putStringSet(KEY_SEEN_IDS, seenIds)
            .putStringSet(KEY_SEEDED_ARTISTS, seededArtists)
            .putLong(KEY_LAST_CHECK_MS, nowMs)
            .apply()
        saveFeed(context, sortedFeed)

        if (pushNotification && newlyDiscovered.isNotEmpty() &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        ) {
            postNewReleaseNotification(context, newlyDiscovered)
        }

        NewReleaseCheckResult(
            checkedArtistsCount = subscribedIds.size,
            newlyFoundCount = newlyDiscovered.size,
            items = sortedFeed
        )
    }

    private fun saveFeed(context: Context, items: List<DiscoveredReleaseItem>) {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("browseId", item.browseId)
                    put("title", item.title)
                    put("artistName", item.artistName)
                    put("artistId", item.artistId)
                    put("releaseType", item.releaseType)
                    put("thumbnailUrl", item.thumbnailUrl)
                    if (item.year != null) put("year", item.year)
                    put("discoveredAtMs", item.discoveredAtMs)
                    put("isNew", item.isNew)
                }
            )
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FEED_JSON, arr.toString())
            .apply()
    }

    private fun postNewReleaseNotification(context: Context, releases: List<DiscoveredReleaseItem>) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Artist New Releases",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Alerts when your subscribed artists upload new tracks or albums"
                }
            )
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pi = PendingIntent.getActivity(
            context,
            44_101,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val first = releases.first()
        val title = if (releases.size == 1) {
            "New from ${first.artistName}"
        } else {
            "${releases.size} new releases from subscribed artists"
        }
        val body = releases.take(3).joinToString(" • ") { "${it.title} (${it.artistName})" }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        nm.notify(44_102, notification)
    }

    private fun fetchRssEntries(
        channelId: String,
        artistName: String,
        nowMs: Long
    ): List<DiscoveredReleaseItem> {
        val url = "https://www.youtube.com/feeds/videos.xml?channel_id=$channelId"
        return runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("User-Agent", "LytrixPlayer")
            }
            val xmlText = try {
                conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
            parseYoutubeAtomRss(xmlText, channelId, artistName, nowMs)
        }.getOrDefault(emptyList())
    }

    private fun parseYoutubeAtomRss(
        xml: String,
        channelId: String,
        artistName: String,
        nowMs: Long
    ): List<DiscoveredReleaseItem> {
        val items = mutableListOf<DiscoveredReleaseItem>()
        val parser = Xml.newPullParser()
        parser.setInput(xml.reader())
        var event = parser.eventType
        var inEntry = false
        var tag = ""
        var videoId = ""
        var title = ""
        var publishedMs = nowMs
        var publishedYear: Int? = null
        while (event != XmlPullParser.END_DOCUMENT && items.size < 12) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    tag = parser.name ?: ""
                    if (tag == "entry") {
                        inEntry = true
                        videoId = ""
                        title = ""
                        publishedMs = 0L
                        publishedYear = null
                    }
                }
                XmlPullParser.TEXT -> if (inEntry) {
                    val txt = parser.text?.trim().orEmpty()
                    if (tag == "videoId" || tag == "yt:videoId") videoId = txt
                    if (tag == "title" && title.isBlank()) title = txt
                    if (tag == "published" && txt.isNotBlank()) {
                        runCatching {
                            val instant = java.time.Instant.parse(txt)
                            publishedMs = instant.toEpochMilli()
                            publishedYear = instant.atZone(java.time.ZoneOffset.UTC).year
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "entry" && inEntry) {
                        inEntry = false
                        if (videoId.isNotBlank() && title.isNotBlank()) {
                            items.add(
                                DiscoveredReleaseItem(
                                    id = videoId,
                                    browseId = videoId,
                                    title = title,
                                    artistName = artistName,
                                    artistId = channelId,
                                    releaseType = "VIDEO",
                                    thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                                    year = publishedYear,
                                    discoveredAtMs = if (publishedMs > 0L) publishedMs else 0L,
                                    isNew = false
                                )
                            )
                        }
                    }
                    tag = ""
                }
            }
            event = parser.next()
        }
        return items
    }
}
