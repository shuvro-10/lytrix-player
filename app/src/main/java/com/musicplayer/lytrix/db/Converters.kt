package com.musicplayer.lytrix.db

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromList(value: List<Long>): String {
        return value.joinToString(",")
    }

    @TypeConverter
    fun toList(value: String): List<Long> {
        return if (value.isBlank()) emptyList()
        else value.split(",").mapNotNull { it.toLongOrNull() }
    }
}
