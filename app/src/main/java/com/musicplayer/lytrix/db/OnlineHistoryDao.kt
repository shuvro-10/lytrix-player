package com.musicplayer.lytrix.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OnlineHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOnlineHistory(history: OnlineHistoryEntity): Long

    @Query("UPDATE online_history SET duration = :durationMs WHERE id = :id AND :durationMs > 0")
    suspend fun updateDurationById(id: Long, durationMs: Long)

    @Query("UPDATE online_history SET duration = :durationMs WHERE songId = :songId AND duration <= 0 AND :durationMs > 0")
    suspend fun backfillMissingDuration(songId: String, durationMs: Long)

    @Query("SELECT * FROM online_history ORDER BY timestamp DESC")
    fun getAllOnlineHistory(): Flow<List<OnlineHistoryEntity>>

    @Query("DELETE FROM online_history WHERE id = :id")
    suspend fun deleteHistoryItem(id: Long)

    @Query("DELETE FROM online_history WHERE songId = :songId")
    suspend fun deleteHistoryBySongId(songId: String)

    @Query("SELECT * FROM online_history WHERE songId = :songId ORDER BY timestamp DESC")
    suspend fun getHistoryBySongId(songId: String): List<OnlineHistoryEntity>

    @Query("DELETE FROM online_history")
    suspend fun clearOnlineHistory()

    @Query("UPDATE online_history SET songId = :toSongId, title = :toTitle, artist = :toArtist, imageUrl = CASE WHEN :toImageUrl != '' THEN :toImageUrl ELSE imageUrl END WHERE songId = :fromSongId")
    suspend fun reassignOnlineHistory(
        fromSongId: String,
        toSongId: String,
        toTitle: String,
        toArtist: String,
        toImageUrl: String
    ): Int
}
