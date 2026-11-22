/*
 * Lytrix Player (2026)
 * Licensed under GNU General Public License v3.0 (GPL-3.0)
 */

package com.musicplayer.lytrix.innertube.pages

import com.musicplayer.lytrix.innertube.models.AlbumItem
import com.musicplayer.lytrix.innertube.models.MusicTwoRowItemRenderer

data class LibraryAlbumsPage(
    val albums: List<AlbumItem>,
    val continuation: String?,
) {
    companion object {
        fun fromMusicTwoRowItemRenderer(renderer: MusicTwoRowItemRenderer): AlbumItem? {
            val thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getBestThumbnail() ?: return null
            val browseId = renderer.navigationEndpoint.browseEndpoint?.browseId ?: return null
            val playlistId =
                renderer.thumbnailOverlay
                    ?.musicItemThumbnailOverlayRenderer
                    ?.content
                    ?.musicPlayButtonRenderer
                    ?.playNavigationEndpoint
                    ?.watchPlaylistEndpoint
                    ?.playlistId
                    ?: renderer.menu
                        ?.menuRenderer
                        ?.items
                        ?.firstOrNull()
                        ?.menuNavigationItemRenderer
                        ?.navigationEndpoint
                        ?.watchPlaylistEndpoint
                        ?.playlistId
                    ?: browseId.removePrefix("MPREb_").let { "OLAK5uy_$it" }

            return AlbumItem(
                browseId = browseId,
                playlistId = playlistId,
                title =
                    renderer.title.runs
                        ?.firstOrNull()
                        ?.text ?: return null,
                artists = null,
                year =
                    renderer.subtitle
                        ?.runs
                        ?.lastOrNull()
                        ?.text
                        ?.toIntOrNull(),
                thumbnail = thumbnail.normalizedUrl,
                thumbnailWidth = thumbnail.width,
                thumbnailHeight = thumbnail.height,
                explicit =
                    renderer.subtitleBadges?.find {
                        it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                    } != null,
            )
        }
    }
}
