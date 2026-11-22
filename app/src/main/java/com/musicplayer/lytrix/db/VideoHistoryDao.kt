package com.musicplayer.lytrix.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface VideoHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideoHistory(history: VideoHistoryEntity)

    // সর্বশেষ দেখা ২০টি ভিডিও আনবে
    @Query("SELECT * FROM video_history ORDER BY timestamp DESC LIMIT 20")
    suspend fun getRecentVideos(): List<VideoHistoryEntity>

    // হিস্ট্রি ডিলিট করার জন্য
    @Query("DELETE FROM video_history")
    suspend fun clearHistory()
}