package com.musicplayer.lytrix.stats

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.musicplayer.lytrix.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

enum class StatsPeriod(val label: String, val daysBack: Int?) {
    LAST_7_DAYS("This Week", 7),
    LAST_30_DAYS("This Month", 30),
    ALL_TIME("All Time", null)
}

enum class ListeningBand(val title: String, val subtitle: String, val hoursWindow: String) {
    EARLY_BIRD("Early Bird", "Your playlists wake up before the rest of the world.", "5 AM – 11 AM"),
    DAYTIME("Daytime Cruiser", "Music keeps your afternoon moving in rhythm.", "12 PM – 4 PM"),
    EVENING("Evening Chiller", "Golden-hour wind-downs are your sweet spot.", "5 PM – 9 PM"),
    NIGHT_OWL("Midnight Owl", "When the city slows down, your speakers come alive.", "10 PM – 4 AM")
}

enum class ListenerArchetype(val title: String, val tagline: String) {
    EXPLORER("The Explorer", "Always chasing fresh voices and new sonic corners."),
    DEVOTEE("The Devotee", "When a favorite artist hits, you lock in all the way."),
    DEEP_DIVER("The Deep Diver", "Long sessions, full focus, never skipping the vibe."),
    NIGHT_OWL("The Night Owl", "Your sharpest music discoveries happen after dark."),
    OMNIVORE("The Sound Collector", "Local lossless gems and online hits side by side."),
    REGULAR("The Everyday Listener", "Steady daily soundtrack, day in and day out.")
}

data class RankedTrackStat(
    val title: String,
    val artist: String,
    val playCount: Int,
    val avgDurationSec: Int,
    val artworkUrl: String?,
)

data class RankedArtistStat(
    val artist: String,
    val playCount: Int,
    val artworkUrl: String?,
)

data class WidgetPlaylistSummary(
    val id: String,
    val title: String,
    val trackCount: Int,
    val type: String, // "liked", "local", "online"
    val coverUrl: String? = null
)

data class ListeningInsightsData(
    val period: StatsPeriod,
    val userName: String,
    val totalPlaytimeMs: Long,
    val periodPlaytimeMs: Long,
    val audioPlaytimeMs: Long,
    val videoPlaytimeMs: Long,
    val todayPlaytimeMs: Long,
    val weekPlaytimeMs: Long,
    val totalPlaysCount: Int,
    val uniqueTracksCount: Int,
    val uniqueArtistsCount: Int,
    val currentStreakDays: Int,
    val longestStreakDays: Int,
    val biggestDayLabel: String?,
    val biggestDayMinutes: Int,
    val playsByHour: List<Int>,
    val peakHour: Int,
    val listeningBand: ListeningBand,
    val archetype: ListenerArchetype,
    val topTracks: List<RankedTrackStat>,
    val topArtists: List<RankedArtistStat>,
    val playlists: List<WidgetPlaylistSummary>
)

/**
 * Aggregates real user playback telemetry across Room (`playtime_stats`, `online_history`, `audio_history`, `playlists`)
 * and SharedPreferences (`detailed_playtime`, `online_playlists_meta`, `online_liked_songs`) for
 * Glance Home Widgets, Wrapped Story, Listening Receipt (Receiptify), and Taste Share Card.
 */
object ListeningInsightsRepository {

    private data class RawPlayEvent(
        val title: String,
        val artist: String,
        val timestampMs: Long,
        val durationSec: Int,
        val artworkUrl: String?,
    )

    suspend fun load(
        context: Context,
        period: StatsPeriod = StatsPeriod.ALL_TIME
    ): ListeningInsightsData = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val detailedPrefs = context.getSharedPreferences("detailed_playtime", Context.MODE_PRIVATE)
        val audioMs = detailedPrefs.getLong("audio_ms", 0L)
        val videoMs = detailedPrefs.getLong("video_ms", 0L)
        val weeklyPrefMs = detailedPrefs.getLong("weekly_ms", 0L)

        val nowMs = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val todayStartMs = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val weekStartMs = today.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()
        val cutoffMs = period.daysBack?.let { days ->
            today.minusDays((days - 1).toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
        } ?: 0L

        val playtimeRows = runCatching { db.playtimeDao().getAllPlaytime() }.getOrDefault(emptyList())
        val totalDbMs = playtimeRows.sumOf { it.durationPlayedMs }
        val totalMs = maxOf(totalDbMs, audioMs + videoMs)
        val periodPlayMs = if (cutoffMs == 0L) {
            totalMs
        } else {
            val dbPeriod = playtimeRows.filter { it.timestampMs >= cutoffMs }.sumOf { it.durationPlayedMs }
            if (period == StatsPeriod.LAST_7_DAYS) maxOf(dbPeriod, weeklyPrefMs) else dbPeriod
        }
        val todayMs = playtimeRows.filter { it.timestampMs >= todayStartMs }.sumOf { it.durationPlayedMs }
        val weekMs = maxOf(
            playtimeRows.filter { it.timestampMs >= weekStartMs }.sumOf { it.durationPlayedMs },
            weeklyPrefMs
        )

        // Daily aggregation from playtime rows + history timestamps
        val dailyMsMap = HashMap<LocalDate, Long>()
        playtimeRows.forEach { row ->
            val d = Instant.ofEpochMilli(row.timestampMs).atZone(zone).toLocalDate()
            dailyMsMap[d] = (dailyMsMap[d] ?: 0L) + row.durationPlayedMs
        }

        val onlineHistory = runCatching { db.onlineHistoryDao().getAllOnlineHistory().first() }.getOrDefault(emptyList())
        val audioHistory = runCatching { db.audioHistoryDao().getAllAudioHistory() }.getOrDefault(emptyList())
        val (localDurationsById, localDurationsByKey) = loadLocalMediaDurationsSec(context)

        val combinedEvents = buildList {
            onlineHistory.forEach { e ->
                add(
                    RawPlayEvent(
                        title = e.title.trim(),
                        artist = cleanArtistText(e.artist),
                        timestampMs = e.timestamp,
                        durationSec = (e.duration / 1000L).toInt().coerceAtLeast(0),
                        artworkUrl = e.imageUrl.takeIf { it.isNotBlank() }
                    )
                )
            }
            audioHistory.forEach { e ->
                val resolvedTitle = e.title.trim()
                val resolvedArtist = cleanArtistText(e.artist)
                val key = "${resolvedTitle.lowercase(Locale.ROOT)}||${resolvedArtist.lowercase(Locale.ROOT)}"
                val sec = (e.durationMs / 1000L).toInt().takeIf { it > 0 }
                    ?: localDurationsById[e.songId]
                    ?: localDurationsByKey[key]
                    ?: 0
                add(
                    RawPlayEvent(
                        title = resolvedTitle,
                        artist = resolvedArtist,
                        timestampMs = e.timestamp,
                        durationSec = sec,
                        artworkUrl = null
                    )
                )
            }
        }

        // Seed active days from play events if playtime_stats didn't capture a quick skip/track
        val activeDates = HashSet<LocalDate>(dailyMsMap.keys)
        combinedEvents.forEach { ev ->
            val d = Instant.ofEpochMilli(ev.timestampMs).atZone(zone).toLocalDate()
            activeDates.add(d)
            if (d !in dailyMsMap) dailyMsMap[d] = (ev.durationSec.coerceAtLeast(180) * 1000L)
        }

        val filteredEvents = if (cutoffMs == 0L) combinedEvents else combinedEvents.filter { it.timestampMs >= cutoffMs }
        val eventsForStats = if (filteredEvents.isNotEmpty()) filteredEvents else combinedEvents

        val playsByHour = IntArray(24)
        eventsForStats.forEach { ev ->
            val h = Instant.ofEpochMilli(ev.timestampMs).atZone(zone).hour
            if (h in 0..23) playsByHour[h]++
        }
        // Fallback to playtime timestamps if play history list was cleared
        if (playsByHour.sum() == 0) {
            playtimeRows.forEach { row ->
                val h = Instant.ofEpochMilli(row.timestampMs).atZone(zone).hour
                if (h in 0..23) playsByHour[h]++
            }
        }

        val peakHour = playsByHour.indices.maxByOrNull { playsByHour[it] } ?: 20
        val listeningBand = when (peakHour) {
            in 5..11 -> ListeningBand.EARLY_BIRD
            in 12..16 -> ListeningBand.DAYTIME
            in 17..21 -> ListeningBand.EVENING
            else -> ListeningBand.NIGHT_OWL
        }

        // Aggregate Top Tracks
        val trackGroups = eventsForStats
            .filter { it.title.isNotBlank() }
            .groupBy { "${it.title.lowercase(Locale.ROOT)}||${it.artist.lowercase(Locale.ROOT)}" }

        val topTracks = trackGroups.values
            .map { group ->
                val sample = group.maxByOrNull { it.artworkUrl?.length ?: 0 } ?: group.first()
                val validDurs = group.map { it.durationSec }.filter { it > 0 }
                val avgDur = if (validDurs.isNotEmpty()) validDurs.average().toInt() else 0
                RankedTrackStat(
                    title = sample.title,
                    artist = sample.artist.ifBlank { "Unknown Artist" },
                    playCount = group.size,
                    avgDurationSec = avgDur,
                    artworkUrl = group.firstNotNullOfOrNull { it.artworkUrl }
                )
            }
            .sortedByDescending { it.playCount }
            .take(15)

        // Aggregate Top Artists using configurable ArtistSeparatorManager tokens
        val artistCounts = HashMap<String, MutableList<RawPlayEvent>>()
        eventsForStats.forEach { ev ->
            val primary = com.musicplayer.lytrix.utils.ArtistSeparatorManager.splitArtists(context, ev.artist)
            if (primary.isEmpty()) {
                if (ev.artist.isNotBlank()) {
                    artistCounts.getOrPut(ev.artist) { mutableListOf() }.add(ev)
                }
            } else {
                primary.take(2).forEach { name ->
                    artistCounts.getOrPut(name) { mutableListOf() }.add(ev)
                }
            }
        }
        val topArtists = artistCounts.entries
            .map { (name, list) ->
                RankedArtistStat(
                    artist = name,
                    playCount = list.size,
                    artworkUrl = list.firstNotNullOfOrNull { it.artworkUrl }
                )
            }
            .sortedByDescending { it.playCount }
            .take(12)

        // Compute streaks & biggest listening day
        val (currentStreak, longestStreak) = computeStreaks(activeDates, today)
        val biggestEntry = dailyMsMap.entries.maxByOrNull { it.value }
        val biggestLabel = biggestEntry?.key?.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
        val biggestMinutes = ((biggestEntry?.value ?: 0L) / 60_000L).toInt()

        val uniqueTracksCount = trackGroups.size
        val uniqueArtistsCount = artistCounts.size
        val topArtistShare = if (eventsForStats.isNotEmpty() && topArtists.isNotEmpty()) {
            topArtists.first().playCount.toFloat() / eventsForStats.size.toFloat()
        } else 0f

        val archetype = when {
            listeningBand == ListeningBand.NIGHT_OWL && peakHour in listOf(22, 23, 0, 1, 2, 3) -> ListenerArchetype.NIGHT_OWL
            topArtistShare >= 0.30f -> ListenerArchetype.DEVOTEE
            uniqueArtistsCount >= 12 -> ListenerArchetype.EXPLORER
            audioHistory.isNotEmpty() && onlineHistory.isNotEmpty() -> ListenerArchetype.OMNIVORE
            totalMs >= 5L * 3600_000L -> ListenerArchetype.DEEP_DIVER
            else -> ListenerArchetype.REGULAR
        }

        val playlists = loadPlaylistsSummary(context, db)
        val userName = resolveUserName(context)

        ListeningInsightsData(
            period = period,
            userName = userName,
            totalPlaytimeMs = totalMs,
            periodPlaytimeMs = periodPlayMs,
            audioPlaytimeMs = audioMs,
            videoPlaytimeMs = videoMs,
            todayPlaytimeMs = todayMs,
            weekPlaytimeMs = weekMs,
            totalPlaysCount = eventsForStats.size,
            uniqueTracksCount = uniqueTracksCount,
            uniqueArtistsCount = uniqueArtistsCount,
            currentStreakDays = currentStreak,
            longestStreakDays = longestStreak,
            biggestDayLabel = biggestLabel,
            biggestDayMinutes = biggestMinutes,
            playsByHour = playsByHour.toList(),
            peakHour = peakHour,
            listeningBand = listeningBand,
            archetype = archetype,
            topTracks = topTracks,
            topArtists = topArtists,
            playlists = playlists
        )
    }

    suspend fun loadPlaylistsOnly(context: Context): List<WidgetPlaylistSummary> = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        loadPlaylistsSummary(context, db)
    }

    private suspend fun loadPlaylistsSummary(
        context: Context,
        db: AppDatabase
    ): List<WidgetPlaylistSummary> {
        val result = mutableListOf<WidgetPlaylistSummary>()

        // 1. Liked Songs count
        val likedCount = runCatching {
            val onlineLiked = context.getSharedPreferences("online_liked_songs", Context.MODE_PRIVATE)
                .getStringSet("liked_ids", emptySet())?.size ?: 0
            val localLiked = db.favoriteDao().getAllFavoriteSongIds().first().size
            onlineLiked + localLiked
        }.getOrDefault(0)
        result.add(WidgetPlaylistSummary(id = "-1", title = "Liked Songs", trackCount = likedCount, type = "liked"))

        // 2. Most Played & Recent Quick Collections
        val historyCount = runCatching {
            val uniqueLocal = db.audioHistoryDao().getAllAudioHistory().distinctBy { it.songId }.size
            val metaPlays = db.songMetadataDao().getAllPlayCounts().first().size
            maxOf(uniqueLocal, metaPlays)
        }.getOrDefault(0)
        result.add(WidgetPlaylistSummary(id = "-1", title = "Most Played", trackCount = historyCount, type = "most_played"))
        result.add(WidgetPlaylistSummary(id = "-1", title = "Recent Plays", trackCount = historyCount, type = "recent"))

        // 3. Local Room Playlists
        runCatching {
            val localPls = db.playlistDao().getAllPlaylists().first()
            localPls.take(3).forEach { pl ->
                result.add(
                    WidgetPlaylistSummary(
                        id = pl.id.toString(),
                        title = pl.name,
                        trackCount = pl.songCount,
                        type = "custom"
                    )
                )
            }
        }

        // 4. Cloud / Online Playlists
        runCatching {
            val plMeta = context.getSharedPreferences("online_playlists_meta", Context.MODE_PRIVATE)
            val coverPrefs = context.getSharedPreferences("playlist_covers", Context.MODE_PRIVATE)
            val names = plMeta.getStringSet("names", emptySet())?.toList().orEmpty()
            names.take(3).forEach { name ->
                val plSp = context.getSharedPreferences("online_pl_$name", Context.MODE_PRIVATE)
                val count = plSp.all.size
                result.add(
                    WidgetPlaylistSummary(
                        id = name,
                        title = name,
                        trackCount = count,
                        type = "online",
                        coverUrl = coverPrefs.getString(name, null)
                    )
                )
            }
        }

        return result.distinctBy { "${it.type}_${it.id}_${it.title}" }.take(6)
    }

    private fun loadLocalMediaDurationsSec(context: Context): Pair<Map<Long, Int>, Map<String, Int>> {
        val byId = HashMap<Long, Int>()
        val byKey = HashMap<String, Int>()
        runCatching {
            val projection = arrayOf(
                android.provider.MediaStore.Audio.Media._ID,
                android.provider.MediaStore.Audio.Media.TITLE,
                android.provider.MediaStore.Audio.Media.ARTIST,
                android.provider.MediaStore.Audio.Media.DURATION
            )
            context.contentResolver.query(
                android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                "${android.provider.MediaStore.Audio.Media.IS_MUSIC} != 0",
                null,
                null
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(android.provider.MediaStore.Audio.Media._ID)
                val titleIdx = cursor.getColumnIndex(android.provider.MediaStore.Audio.Media.TITLE)
                val artistIdx = cursor.getColumnIndex(android.provider.MediaStore.Audio.Media.ARTIST)
                val durIdx = cursor.getColumnIndex(android.provider.MediaStore.Audio.Media.DURATION)
                while (cursor.moveToNext()) {
                    val durMs = if (durIdx >= 0) cursor.getLong(durIdx) else 0L
                    val sec = (durMs / 1000L).toInt()
                    if (sec <= 0) continue
                    if (idIdx >= 0) {
                        byId[cursor.getLong(idIdx)] = sec
                    }
                    if (titleIdx >= 0) {
                        val t = cursor.getString(titleIdx).orEmpty().trim().lowercase(Locale.ROOT)
                        val a = cleanArtistText(if (artistIdx >= 0) cursor.getString(artistIdx).orEmpty() else "").lowercase(Locale.ROOT)
                        if (t.isNotBlank()) byKey["$t||$a"] = sec
                    }
                }
            }
        }
        return byId to byKey
    }

    private fun resolveUserName(context: Context): String {
        val savedLt = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .getString("lt_username", "")?.trim().orEmpty()
        if (savedLt.isNotBlank()) return savedLt
        val fbName = runCatching { FirebaseAuth.getInstance().currentUser?.displayName?.trim() }.getOrNull().orEmpty()
        if (fbName.isNotBlank()) return fbName
        return "Music Lover"
    }

    private fun computeStreaks(dates: Set<LocalDate>, today: LocalDate): Pair<Int, Int> {
        if (dates.isEmpty()) return 0 to 0
        var current = 0
        var cursor = if (today in dates) today else today.minusDays(1)
        if (cursor in dates) {
            while (cursor in dates) {
                current++
                cursor = cursor.minusDays(1)
            }
        }

        val sorted = dates.sorted()
        var longest = 1
        var run = 1
        for (i in 1 until sorted.size) {
            if (sorted[i - 1].plusDays(1) == sorted[i]) {
                run++
                if (run > longest) longest = run
            } else {
                run = 1
            }
        }
        return current to longest
    }

    private fun cleanArtistText(raw: String): String {
        return raw
            .replace(Regex("""\s*-\s*Topic$""", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    fun formatDurationReadable(ms: Long): String {
        val totalMin = (ms / 60_000L).coerceAtLeast(0L)
        val hours = totalMin / 60L
        val mins = totalMin % 60L
        return if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
    }

    fun formatHourShort(hour24: Int): String {
        val normalized = ((hour24 % 24) + 24) % 24
        val amPm = if (normalized < 12) "AM" else "PM"
        val h12 = when {
            normalized == 0 -> 12
            normalized > 12 -> normalized - 12
            else -> normalized
        }
        return "$h12 $amPm"
    }

    fun formatReceiptClock(seconds: Int): String {
        if (seconds <= 0) return "--:--"
        return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
    }

    fun formatReceiptTotal(seconds: Long): String {
        val s = seconds.coerceAtLeast(0L)
        return "${s / 3600}:${((s / 60) % 60).toString().padStart(2, '0')}:${(s % 60).toString().padStart(2, '0')}"
    }

    fun todayReceiptDate(): String =
        SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(Date())
}
