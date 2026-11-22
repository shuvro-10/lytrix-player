package com.musicplayer.lytrix.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AudioHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAudioHistory(history: AudioHistoryEntity)

    @Query("SELECT * FROM audio_history ORDER BY timestamp DESC")
    suspend fun getAllAudioHistory(): List<AudioHistoryEntity>

    // কোন গানটি সবচেয়ে বেশিবার শুনেছে (Most Played)
    @Query("SELECT title, artist, COUNT(songId) as playCount FROM audio_history GROUP BY songId ORDER BY playCount DESC LIMIT 5")
    suspend fun getTopSongs(): List<TopSongResult>

    // কোন আর্টিস্টকে সবচেয়ে বেশি শুনেছে (Top Artist)
    @Query("SELECT artist, COUNT(songId) as playCount FROM audio_history GROUP BY artist ORDER BY playCount DESC LIMIT 1")
    suspend fun getTopArtist(): TopArtistResult?

    @Query("DELETE FROM audio_history")
    suspend fun clearAudioHistory()

    @Query("UPDATE audio_history SET songId = :toSongId, title = :toTitle, artist = :toArtist WHERE songId = :fromSongId")
    suspend fun reassignSongHistory(fromSongId: Long, toSongId: Long, toTitle: String, toArtist: String): Int

    @Query("SELECT * FROM audio_history WHERE songId = :songId ORDER BY timestamp DESC")
    suspend fun getHistoryForSong(songId: Long): List<AudioHistoryEntity>

    @Query("DELETE FROM audio_history WHERE songId = :songId")
    suspend fun deleteHistoryForSong(songId: Long)

    @Query("UPDATE song_metadata SET playCount = 0, lastPlayedTimestamp = 0 WHERE songId = :songId")
    suspend fun resetPlayStatsForSong(songId: Long)
}

// এই রেজাল্টগুলো রিসিভ করার জন্য ছোট্ট দুটো ডেটা ক্লাস
data class TopSongResult(val title: String, val artist: String, val playCount: Int)
data class TopArtistResult(val artist: String, val playCount: Int)
