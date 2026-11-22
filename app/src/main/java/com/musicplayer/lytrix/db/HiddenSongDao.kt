package com.musicplayer.lytrix.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HiddenSongDao {
    @Query("SELECT * FROM hidden_songs")
    fun getAllHiddenSongs(): Flow<List<HiddenSongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHiddenSong(hiddenSong: HiddenSongEntity)

    @Query("DELETE FROM hidden_songs WHERE songId = :songId")
    suspend fun removeHiddenSong(songId: Long)
}
