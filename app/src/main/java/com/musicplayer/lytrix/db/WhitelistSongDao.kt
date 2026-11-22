package com.musicplayer.lytrix.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WhitelistSongDao {
    @Query("SELECT * FROM whitelist_songs")
    fun getAllWhitelistSongsFlow(): Flow<List<WhitelistSongEntity>>

    @Query("SELECT * FROM whitelist_songs")
    suspend fun getAllWhitelistSongs(): List<WhitelistSongEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWhitelistSong(whitelistSong: WhitelistSongEntity)

    @Query("DELETE FROM whitelist_songs WHERE songId = :songId")
    suspend fun removeWhitelistSong(songId: Long)
}
