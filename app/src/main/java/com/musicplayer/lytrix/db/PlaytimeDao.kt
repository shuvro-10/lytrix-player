package com.musicplayer.lytrix.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaytimeDao {
    @Insert
    suspend fun insertPlaytime(playtime: PlaytimeEntity)

    @Query("SELECT * FROM playtime_stats ORDER BY timestampMs DESC")
    suspend fun getAllPlaytime(): List<PlaytimeEntity>

    @Query("SELECT SUM(durationPlayedMs) FROM playtime_stats")
    fun getTotalPlaytimeMs(): Flow<Long?>

    @Query("SELECT SUM(durationPlayedMs) FROM playtime_stats WHERE timestampMs >= :startTimeMs")
    fun getPlaytimeSinceMs(startTimeMs: Long): Flow<Long?>

    @Query("DELETE FROM playtime_stats")
    suspend fun clearPlaytimeStats()
}
