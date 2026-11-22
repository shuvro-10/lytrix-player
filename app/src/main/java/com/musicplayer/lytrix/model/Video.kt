package com.musicplayer.lytrix.model

data class Video(
    val id: Long,
    val title: String,
    val durationText: String,
    val sizeText: String,
    val path: String,
    val uriString: String,
    val dateModified: Long
)
