package com.musicplayer.lytrix.alarm

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stores and retrieves scheduled [MusicAlarmEntry] items in SharedPreferences.
 */
object MusicAlarmStore {
    private const val PREF_NAME = "music_alarm_prefs"
    private const val KEY_ALARMS_JSON = "alarms_json_v1"

    fun loadAll(context: Context): List<MusicAlarmEntry> {
        val raw = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ALARMS_JSON, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val daysArr = obj.optJSONArray("repeatDays") ?: JSONArray()
                    val days = buildSet {
                        for (j in 0 until daysArr.length()) {
                            add(daysArr.getInt(j))
                        }
                    }
                    add(
                        MusicAlarmEntry(
                            id = obj.optString("id"),
                            hour = obj.optInt("hour", 7).coerceIn(0, 23),
                            minute = obj.optInt("minute", 0).coerceIn(0, 59),
                            enabled = obj.optBoolean("enabled", true),
                            label = obj.optString("label", "Morning Music"),
                            repeatDays = days,
                            sourceType = AlarmSourceType.fromStored(obj.optString("sourceType")),
                            playlistId = obj.optString("playlistId", ""),
                            playlistTitle = obj.optString("playlistTitle", ""),
                            songId = obj.optString("songId", ""),
                            songTitle = obj.optString("songTitle", ""),
                            songArtist = obj.optString("songArtist", ""),
                            songUri = obj.optString("songUri", ""),
                            songArtUrl = obj.optString("songArtUrl", ""),
                            songIsOnline = obj.optBoolean("songIsOnline", false),
                            randomTrack = obj.optBoolean("randomTrack", true),
                            fadeInSeconds = obj.optInt("fadeInSeconds", 15).coerceIn(0, 60),
                            snoozeMinutes = obj.optInt("snoozeMinutes", 10).coerceIn(1, 30)
                        )
                    )
                }
            }.sortedWith(compareBy<MusicAlarmEntry> { it.hour }.thenBy { it.minute })
        }.getOrDefault(emptyList())
    }

    fun getById(context: Context, alarmId: String): MusicAlarmEntry? =
        loadAll(context).firstOrNull { it.id == alarmId }

    fun saveAll(context: Context, alarms: List<MusicAlarmEntry>) {
        val arr = JSONArray()
        alarms.forEach { alarm ->
            val obj = JSONObject().apply {
                put("id", alarm.id)
                put("hour", alarm.hour)
                put("minute", alarm.minute)
                put("enabled", alarm.enabled)
                put("label", alarm.label)
                put("repeatDays", JSONArray(alarm.repeatDays.sorted()))
                put("sourceType", alarm.sourceType.name)
                put("playlistId", alarm.playlistId)
                put("playlistTitle", alarm.playlistTitle)
                put("songId", alarm.songId)
                put("songTitle", alarm.songTitle)
                put("songArtist", alarm.songArtist)
                put("songUri", alarm.songUri)
                put("songArtUrl", alarm.songArtUrl)
                put("songIsOnline", alarm.songIsOnline)
                put("randomTrack", alarm.randomTrack)
                put("fadeInSeconds", alarm.fadeInSeconds)
                put("snoozeMinutes", alarm.snoozeMinutes)
            }
            arr.put(obj)
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ALARMS_JSON, arr.toString())
            .apply()
    }

    fun upsert(context: Context, alarm: MusicAlarmEntry): List<MusicAlarmEntry> {
        val current = loadAll(context).toMutableList()
        val idx = current.indexOfFirst { it.id == alarm.id }
        if (idx >= 0) current[idx] = alarm else current.add(alarm)
        saveAll(context, current)
        return loadAll(context)
    }

    fun delete(context: Context, alarmId: String): List<MusicAlarmEntry> {
        val updated = loadAll(context).filterNot { it.id == alarmId }
        saveAll(context, updated)
        return updated
    }

    fun setEnabled(context: Context, alarmId: String, enabled: Boolean): List<MusicAlarmEntry> {
        val updated = loadAll(context).map {
            if (it.id == alarmId) it.copy(enabled = enabled) else it
        }
        saveAll(context, updated)
        return updated
    }
}
