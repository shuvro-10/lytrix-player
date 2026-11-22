package com.musicplayer.lytrix.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)

    @Query("SELECT p.id, p.name, (SELECT COUNT(*) FROM playlist_song_cross_ref cr WHERE cr.playlistId = p.id) AS songCount, p.songIds FROM playlists p ORDER BY p.id DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT p.id, p.name, (SELECT COUNT(*) FROM playlist_song_cross_ref cr WHERE cr.playlistId = p.id) AS songCount, p.songIds FROM playlists p WHERE p.id = :playlistId")
    suspend fun getPlaylistById(playlistId: Long): PlaylistEntity?

    @Query("DELETE FROM playlist_song_cross_ref WHERE playlistId = :playlistId")
    suspend fun deletePlaylistCrossRefs(playlistId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaylistSongCrossRef(crossRef: PlaylistSongCrossRef)

    @Query("DELETE FROM playlist_song_cross_ref WHERE playlistId = :playlistId AND songId IN (:songIds)")
    suspend fun removeSongsFromPlaylist(playlistId: Long, songIds: List<Long>)
    
    @Query("SELECT * FROM playlist_song_cross_ref WHERE playlistId = :playlistId ORDER BY orderIndex ASC")
    fun getPlaylistCrossRefs(playlistId: Long): Flow<List<PlaylistSongCrossRef>>

    @Query("UPDATE playlist_song_cross_ref SET orderIndex = :newOrder WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun updateSongOrder(playlistId: Long, songId: Long, newOrder: Int)

    @Transaction
    suspend fun reorderPlaylistSongs(playlistId: Long, orderedSongIds: List<Long>) {
        orderedSongIds.forEachIndexed { index, songId ->
            updateSongOrder(playlistId, songId, index)
        }
    }

    @Query("SELECT COUNT(*) FROM playlist_song_cross_ref WHERE playlistId = :playlistId")
    fun getPlaylistSongCount(playlistId: Long): Flow<Int>
}
