package com.musicplayer.lytrix.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PodcastDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPodcast(podcast: PodcastEntity)

    @Delete
    suspend fun deletePodcast(podcast: PodcastEntity)

    @Query("SELECT * FROM downloaded_podcasts ORDER BY addedTime DESC")
    fun getAllDownloadedPodcasts(): Flow<List<PodcastEntity>>

    @Query("SELECT * FROM downloaded_podcasts WHERE id = :podcastId LIMIT 1")
    suspend fun getPodcastById(podcastId: String): PodcastEntity?
}