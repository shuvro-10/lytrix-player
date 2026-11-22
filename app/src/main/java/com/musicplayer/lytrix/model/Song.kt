package com.musicplayer.lytrix.model

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long, // in milliseconds
    val uriString: String,
    val albumArtUriString: String? = null,
    val filePath: String? = null,
    val hasLyrics: Boolean = false,
    val isVideo: Boolean = false,
    val originalId: String? = null, // 🔥 ম্যাজিক প্রপার্টি: আসল ইউটিউব/জিওসাভন আইডি ধরে রাখার জন্য
    val channelId: String? = null,
    val mediaType: String? = null // "MUSIC", "PODCAST", "AUDIOBOOK"
)