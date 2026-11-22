package com.musicplayer.lytrix.model

data class JioSaavnSong(
    val id: String,
    val name: String,
    val artist: String,
    val imageUrl: String,
    val downloadUrl: String,
    val albumId: String? = null,
    val albumName: String? = null
)