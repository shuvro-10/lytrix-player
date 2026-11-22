package com.musicplayer.lytrix.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hidden_songs")
data class HiddenSongEntity(
    @PrimaryKey val songId: Long
)
