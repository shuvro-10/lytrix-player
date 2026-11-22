package com.musicplayer.lytrix.alarm

import java.util.UUID

/**
 * Source category of tracks launched when a music alarm rings.
 */
enum class AlarmSourceType(val displayName: String) {
    SPECIFIC_SONG("Specific Song"),
    RECENT("Recently Played"),
    LIKED("Liked Songs"),
    ALL_LOCAL("All Local Songs"),
    LOCAL_PLAYLIST("Local Playlist"),
    ONLINE_PLAYLIST("Online Playlist");

    companion object {
        fun fromStored(value: String?): AlarmSourceType =
            entries.firstOrNull { it.name == value } ?: ALL_LOCAL
    }
}

/**
 * Persistent music alarm definition inspired by Meld's wake-up alarm clock.
 */
data class MusicAlarmEntry(
    val id: String = UUID.randomUUID().toString(),
    val hour: Int = 7,
    val minute: Int = 0,
    val enabled: Boolean = true,
    val label: String = "Morning Music",
    val repeatDays: Set<Int> = emptySet(), // Calendar.SUNDAY (1) .. Calendar.SATURDAY (7)
    val sourceType: AlarmSourceType = AlarmSourceType.ALL_LOCAL,
    val playlistId: String = "",
    val playlistTitle: String = "",
    val songId: String = "",
    val songTitle: String = "",
    val songArtist: String = "",
    val songUri: String = "",
    val songArtUrl: String = "",
    val songIsOnline: Boolean = false,
    val randomTrack: Boolean = true,
    val fadeInSeconds: Int = 15,
    val snoozeMinutes: Int = 10
)
