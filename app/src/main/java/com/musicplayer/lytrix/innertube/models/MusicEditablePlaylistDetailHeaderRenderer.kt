/*
 * Lytrix Player (2026)
 * Licensed under GNU General Public License v3.0 (GPL-3.0)
 */

package com.musicplayer.lytrix.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class MusicEditablePlaylistDetailHeaderRenderer(
    val header: Header,
    val editHeader: EditHeader,
) {
    @Serializable
    data class Header(
        val musicDetailHeaderRenderer: MusicDetailHeaderRenderer?,
        val musicResponsiveHeaderRenderer: MusicResponsiveHeaderRenderer?,
    )

    @Serializable
    data class EditHeader(
        val musicPlaylistEditHeaderRenderer: MusicPlaylistEditHeaderRenderer?,
    )
}

@Serializable
data class MusicDetailHeaderRenderer(
    val title: Runs? = null,
    val subtitle: Runs? = null,
    val secondSubtitle: Runs? = null,
    val description: Runs? = null,
    val thumbnail: ThumbnailRenderer? = null,
    val menu: Menu? = null,
)

@Serializable
data class MusicPlaylistEditHeaderRenderer(
    val editTitle: Runs?,
)
