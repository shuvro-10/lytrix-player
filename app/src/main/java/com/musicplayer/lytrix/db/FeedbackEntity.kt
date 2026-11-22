package com.musicplayer.lytrix.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "feedback_history")
data class FeedbackEntity(
    @PrimaryKey val id: String,
    val category: String,
    val details: String,
    val timestamp: Long,
    val status: String = "Pending"
)