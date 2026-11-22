package com.musicplayer.lytrix.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedbackDao {
    // সব ফিডব্যাক নতুন থেকে পুরোনো হিসেবে সাজিয়ে আনা
    @Query("SELECT * FROM feedback_history ORDER BY timestamp DESC")
    fun getAllFeedbacks(): Flow<List<FeedbackEntity>>

    // নতুন ফিডব্যাক সেভ করা
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeedback(feedback: FeedbackEntity)

    // মোট কয়টা ফিডব্যাক আছে তা গোনা
    @Query("SELECT COUNT(*) FROM feedback_history")
    suspend fun getFeedbackCount(): Int

    // সবচেয়ে পুরোনো ফিডব্যাকটি মুছে ফেলা
    @Query("DELETE FROM feedback_history WHERE timestamp = (SELECT MIN(timestamp) FROM feedback_history)")
    suspend fun deleteOldestFeedback()
}