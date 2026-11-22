package com.musicplayer.lytrix.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloaded_podcasts")
data class PodcastEntity(
    @PrimaryKey val id: String,
    val title: String,
    val publisher: String,
    val audioUrl: String,
    val image: String,
    val durationMs: Long,
    val downloadPath: String,
    val language: String,
    val isDownloaded: Boolean = false,
    val addedTime: Long = System.currentTimeMillis()
)