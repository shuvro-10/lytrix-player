package com.musicplayer.lytrix.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playtime_stats")
data class PlaytimeEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestampMs: Long = System.currentTimeMillis(),
    val durationPlayedMs: Long
)
