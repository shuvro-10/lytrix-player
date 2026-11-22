package com.musicplayer.lytrix.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "online_history")
data class OnlineHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: String,
    val title: String,
    val artist: String,
    val imageUrl: String,
    val source: String, // "YOUTUBE" or "JIOSAAVN"
    val timestamp: Long,
    val duration: Long = 0L,
    val uriString: String = ""
)
