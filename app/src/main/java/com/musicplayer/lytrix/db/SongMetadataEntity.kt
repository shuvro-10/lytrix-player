package com.musicplayer.lytrix.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "song_metadata")
data class SongMetadataEntity(
    @PrimaryKey
    val songId: Long,
    val isFavorite: Boolean = false,
    val favoriteOrder: Int = 0,
    val playCount: Int = 0,
    val lastPlayedTimestamp: Long = 0L
)
