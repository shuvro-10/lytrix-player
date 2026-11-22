package com.musicplayer.lytrix.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "audio_history")
data class AudioHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: Long,
    val title: String,
    val artist: String,
    val timestamp: Long, // কখন শুনেছে সেটার রেকর্ড
    val durationMs: Long = 0L
)