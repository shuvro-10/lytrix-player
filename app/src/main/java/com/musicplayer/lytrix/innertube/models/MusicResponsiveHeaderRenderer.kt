/*
 * Lytrix Player (2026)
 * Licensed under GNU General Public License v3.0 (GPL-3.0)
 */

package com.musicplayer.lytrix.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class MusicResponsiveHeaderRenderer(
    val thumbnail: ThumbnailRenderer? = null,
    val buttons: List<Button> = emptyList(),
    val title: Runs? = null,
    val subtitle: Runs? = null,
    val secondSubtitle: Runs? = null,
    val straplineTextOne: Runs? = null,
    val description: Description? = null,
) {
    @Serializable
    data class Description(
        val musicDescriptionShelfRenderer: MusicDescriptionShelfRenderer? = null,
    )

    @Serializable
    data class Button(
        val musicPlayButtonRenderer: MusicPlayButtonRenderer? = null,
        val menuRenderer: Menu.MenuRenderer? = null,
    ) {
        @Serializable
        data class MusicPlayButtonRenderer(
            val playNavigationEndpoint: NavigationEndpoint? = null,
        )
    }
}
