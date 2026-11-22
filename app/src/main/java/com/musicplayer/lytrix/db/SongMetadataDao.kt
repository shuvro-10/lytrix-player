package com.musicplayer.lytrix.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SongMetadataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(metadata: SongMetadataEntity)

    @Query("SELECT * FROM song_metadata WHERE songId = :songId")
    suspend fun getMetadata(songId: Long): SongMetadataEntity?
    
    @Query("SELECT songId FROM song_metadata WHERE isFavorite = 1 ORDER BY favoriteOrder ASC")
    fun getAllFavoriteSongIds(): Flow<List<Long>>

    @Query("UPDATE song_metadata SET isFavorite = :isFavorite WHERE songId = :songId")
    suspend fun updateFavorite(songId: Long, isFavorite: Boolean)

    @Query("UPDATE song_metadata SET favoriteOrder = :newOrder WHERE songId = :songId")
    suspend fun updateFavoriteOrder(songId: Long, newOrder: Int)

    @Transaction
    suspend fun reorderFavoriteSongs(orderedSongIds: List<Long>) {
        orderedSongIds.forEachIndexed { index, songId ->
            updateFavoriteOrder(songId, index)
        }
    }

    @Query("UPDATE song_metadata SET playCount = playCount + 1, lastPlayedTimestamp = :timestamp WHERE songId = :songId")
    suspend fun incrementPlayCount(songId: Long, timestamp: Long)

    @Query("SELECT songId FROM song_metadata WHERE lastPlayedTimestamp > 0 ORDER BY lastPlayedTimestamp DESC LIMIT 100")
    fun getRecentlyPlayedSongIds(): Flow<List<Long>>

    @Query("SELECT songId FROM song_metadata WHERE playCount > 0 ORDER BY playCount DESC LIMIT 100")
    fun getMostPlayedSongIds(): Flow<List<Long>>

    @Query("SELECT * FROM song_metadata WHERE playCount > 0")
    fun getAllPlayCounts(): Flow<List<SongMetadataEntity>>

    @Query("UPDATE song_metadata SET playCount = 0, lastPlayedTimestamp = 0")
    suspend fun clearListenHistory()

    @Transaction
    suspend fun transferPlayStats(fromSongId: Long, toSongId: Long): Int {
        if (fromSongId == toSongId) return 0
        val source = getMetadata(fromSongId) ?: return 0
        val movedPlays = source.playCount
        val target = getMetadata(toSongId)
        val mergedTimestamp = maxOf(target?.lastPlayedTimestamp ?: 0L, source.lastPlayedTimestamp)
        if (target == null) {
            insertOrUpdate(
                SongMetadataEntity(
                    songId = toSongId,
                    isFavorite = false,
                    favoriteOrder = 0,
                    playCount = movedPlays,
                    lastPlayedTimestamp = mergedTimestamp
                )
            )
        } else {
            insertOrUpdate(
                target.copy(
                    playCount = target.playCount + movedPlays,
                    lastPlayedTimestamp = mergedTimestamp
                )
            )
        }
        insertOrUpdate(source.copy(playCount = 0, lastPlayedTimestamp = 0L))
        return movedPlays
    }
}
