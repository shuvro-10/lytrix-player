package com.musicplayer.lytrix.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "whitelist_songs")
data class WhitelistSongEntity(
    @PrimaryKey val songId: Long
)
