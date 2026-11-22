package com.musicplayer.lytrix.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "video_history")
data class VideoHistoryEntity(
    @PrimaryKey val uriString: String, // URI কেই প্রাইমারি কি বানালাম যাতে ডাবল সেভ না হয়
    val title: String,
    val duration: Long,
    val timestamp: Long
)