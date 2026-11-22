/*
 * Lytrix Player (2026)
 * Licensed under GNU General Public License v3.0 (GPL-3.0)
 */

package com.musicplayer.lytrix.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class MusicShelfRenderer(
    val title: Runs?,
    val contents: List<Content>?,
    val continuations: List<Continuation>?,
    val bottomEndpoint: NavigationEndpoint?,
    val moreContentButton: Button?,
) {
    @Serializable
    data class Content(
        val musicResponsiveListItemRenderer: MusicResponsiveListItemRenderer? = null,
        val musicMultiRowListItemRenderer: MusicMultiRowListItemRenderer? = null,
        val continuationItemRenderer: ContinuationItemRenderer? = null,
    )
}

fun List<MusicShelfRenderer.Content>.getItems(): List<MusicResponsiveListItemRenderer> = mapNotNull { it.musicResponsiveListItemRenderer }

fun List<MusicShelfRenderer.Content>.getContinuation(): String? =
    firstOrNull { it.continuationItemRenderer != null }
        ?.continuationItemRenderer
        ?.continuationEndpoint
        ?.continuationCommand
        ?.token
