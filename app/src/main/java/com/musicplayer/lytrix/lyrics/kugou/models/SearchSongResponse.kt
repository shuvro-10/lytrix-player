/*
 * Lytrix Player (2026)
 * Licensed under GNU General Public License v3.0 (GPL-3.0)
 */
package com.musicplayer.lytrix.lyrics.kugou.models

import kotlinx.serialization.Serializable

@Serializable
data class SearchSongResponse(
    val status: Int = 0,
    val errcode: Int = 0,
    val error: String? = null,
    val data: Data = Data(),
) {
    @Serializable
    data class Data(
        val info: List<Info> = emptyList(),
    ) {
        @Serializable
        data class Info(
            val duration: Int = 0,
            val hash: String = "",
        )
    }
}
