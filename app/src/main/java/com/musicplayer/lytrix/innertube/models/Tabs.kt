/*
 * Lytrix Player (2026)
 * Licensed under GNU General Public License v3.0 (GPL-3.0)
 */

package com.musicplayer.lytrix.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class Tabs(
    val tabs: List<Tab>? = null,
) {
    @Serializable
    data class Tab(
        val tabRenderer: TabRenderer? = null,
    ) {
        @Serializable
        data class TabRenderer(
            val title: String? = null,
            val content: Content? = null,
            val endpoint: NavigationEndpoint? = null,
        ) {
            @Serializable
            data class Content(
                val sectionListRenderer: SectionListRenderer? = null,
                val musicQueueRenderer: MusicQueueRenderer? = null,
            )
        }
    }
}

