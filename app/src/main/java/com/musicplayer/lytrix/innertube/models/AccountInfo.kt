/*
 * Lytrix Player (2026)
 * Licensed under GNU General Public License v3.0 (GPL-3.0)
 */

package com.musicplayer.lytrix.innertube.models

data class AccountInfo(
    val name: String,
    val email: String?,
    val channelHandle: String?,
    val thumbnailUrl: String?,
)

data class AccountChannel(
    val name: String,
    val byline: String?,
    val channelHandle: String?,
    val thumbnailUrl: String?,
    val dataSyncId: String,
    val isSelected: Boolean,
)
