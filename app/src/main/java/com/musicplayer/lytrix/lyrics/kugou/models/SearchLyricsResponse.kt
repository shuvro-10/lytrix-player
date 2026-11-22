/*
 * Lytrix Player (2026)
 * Licensed under GNU General Public License v3.0 (GPL-3.0)
 */
package com.musicplayer.lytrix.lyrics.kugou.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SearchLyricsResponse(
    val status: Int = 0,
    val info: String? = null,
    val errcode: Int = 0,
    val errmsg: String? = null,
    val expire: Int = 0,
    val candidates: List<Candidate> = emptyList(),
) {
    @Serializable
    data class Candidate(
        val id: Long = 0L,
        @SerialName("product_from")
        val productFrom: String? = null, // Consider choosing '官方推荐歌词'
        val duration: Long = 0L,
        val accesskey: String = "",
    )
}
