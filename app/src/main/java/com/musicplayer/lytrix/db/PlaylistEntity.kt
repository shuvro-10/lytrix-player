package com.musicplayer.lytrix.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

@Entity(tableName = "playlists")
@TypeConverters(Converters::class)
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val songCount: Int = 0,
    val songIds: List<Long> = emptyList()
)
