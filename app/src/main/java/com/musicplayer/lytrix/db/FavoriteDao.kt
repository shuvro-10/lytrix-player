package com.musicplayer.lytrix.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setFavorite(favorite: FavoriteSongEntity)

    @Query("SELECT * FROM favorite_songs WHERE songId = :songId")
    suspend fun getFavorite(songId: Long): FavoriteSongEntity?
    
    @Query("SELECT songId FROM favorite_songs WHERE isFavorite = 1")
    fun getAllFavoriteSongIds(): Flow<List<Long>>

    @Query("DELETE FROM favorite_songs WHERE songId = :songId")
    suspend fun removeFavorite(songId: Long)
}
