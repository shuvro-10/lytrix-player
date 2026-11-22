/*
 * Lytrix (2026)
 * Â© Lytrix â€” github.com/Lytrix
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.musicplayer.lytrix.innertube.pages

import com.musicplayer.lytrix.innertube.models.Album
import com.musicplayer.lytrix.innertube.models.AlbumItem
import com.musicplayer.lytrix.innertube.models.Artist
import com.musicplayer.lytrix.innertube.models.ArtistItem
import com.musicplayer.lytrix.innertube.models.BrowseEndpoint
import com.musicplayer.lytrix.innertube.models.GridRenderer
import com.musicplayer.lytrix.innertube.models.MusicCarouselShelfRenderer
import com.musicplayer.lytrix.innertube.models.MusicMultiRowListItemRenderer
import com.musicplayer.lytrix.innertube.models.MusicPlaylistShelfRenderer
import com.musicplayer.lytrix.innertube.models.MusicResponsiveListItemRenderer
import com.musicplayer.lytrix.innertube.models.MusicShelfRenderer
import com.musicplayer.lytrix.innertube.models.MusicTwoRowItemRenderer
import com.musicplayer.lytrix.innertube.models.PlaylistItem
import com.musicplayer.lytrix.innertube.models.SectionListRenderer
import com.musicplayer.lytrix.innertube.models.SongItem
import com.musicplayer.lytrix.innertube.models.YTItem
import com.musicplayer.lytrix.innertube.models.getItems
import com.musicplayer.lytrix.innertube.models.oddElements

enum class ArtistSectionLayout {
    LIST,
    GRID,
}

data class ArtistSection(
    val title: String,
    val items: List<YTItem>,
    val moreEndpoint: BrowseEndpoint?,
    val layout: ArtistSectionLayout,
)

data class ArtistPage(
    val artist: ArtistItem,
    val sections: List<ArtistSection>,
    val description: String?,
) {
    companion object {
        fun fromSectionListRendererContent(content: SectionListRenderer.Content): ArtistSection? =
            when {
                content.musicShelfRenderer != null -> fromMusicShelfRenderer(content.musicShelfRenderer)
                content.musicCarouselShelfRenderer != null -> fromMusicCarouselShelfRenderer(content.musicCarouselShelfRenderer)
                content.gridRenderer != null -> fromGridRenderer(content.gridRenderer)
                content.musicPlaylistShelfRenderer != null -> fromMusicPlaylistShelfRenderer(content.musicPlaylistShelfRenderer)
                content.itemSectionRenderer != null -> fromItemSectionRenderer(content.itemSectionRenderer)
                else -> null
            }

        private fun fromGridRenderer(renderer: GridRenderer): ArtistSection? {
            return ArtistSection(
                title = renderer.header?.gridHeaderRenderer?.title?.runs?.firstOrNull()?.text ?: "",
                items = renderer.items.mapNotNull {
                    it.musicTwoRowItemRenderer?.let { twoRow ->
                        fromMusicTwoRowItemRenderer(twoRow)
                    }
                }.ifEmpty { null } ?: return null,
                moreEndpoint = null,
                layout = ArtistSectionLayout.GRID,
            )
        }

        private fun fromMusicPlaylistShelfRenderer(renderer: MusicPlaylistShelfRenderer): ArtistSection? {
            return ArtistSection(
                title = "Episodes",
                items = renderer.contents.getItems().mapNotNull {
                    fromMusicResponsiveListItemRenderer(it)
                }.ifEmpty { null } ?: return null,
                moreEndpoint = null,
                layout = ArtistSectionLayout.LIST,
            )
        }

        private fun fromItemSectionRenderer(renderer: SectionListRenderer.ItemSectionRenderer): ArtistSection? {
            val listItems = renderer.contents?.mapNotNull { it.musicResponsiveListItemRenderer }?.mapNotNull {
                fromMusicResponsiveListItemRenderer(it)
            }
            if (!listItems.isNullOrEmpty()) {
                return ArtistSection(
                    title = "",
                    items = listItems,
                    moreEndpoint = null,
                    layout = ArtistSectionLayout.LIST,
                )
            }
            val grid = renderer.contents?.mapNotNull { it.gridRenderer }?.firstOrNull()
            if (grid != null) {
                return fromGridRenderer(grid)
            }
            val shelf = renderer.contents?.mapNotNull { it.musicShelfRenderer }?.firstOrNull()
            if (shelf != null) {
                return fromMusicShelfRenderer(shelf)
            }
            return null
        }

        private fun fromMusicShelfRenderer(renderer: MusicShelfRenderer): ArtistSection? {
            return ArtistSection(
                title =
                    renderer.title
                        ?.runs
                        ?.firstOrNull()
                        ?.text ?: "",
                items =
                    renderer.contents
                        ?.getItems()
                        ?.mapNotNull {
                            fromMusicResponsiveListItemRenderer(it)
                        }?.ifEmpty { null } ?: return null,
                moreEndpoint =
                    renderer.title
                        ?.runs
                        ?.firstOrNull()
                        ?.navigationEndpoint
                        ?.browseEndpoint,
                layout = ArtistSectionLayout.LIST,
            )
        }

        private fun fromMusicCarouselShelfRenderer(renderer: MusicCarouselShelfRenderer): ArtistSection? {
            return ArtistSection(
                title =
                    renderer.header
                        ?.musicCarouselShelfBasicHeaderRenderer
                        ?.title
                        ?.runs
                        ?.firstOrNull()
                        ?.text ?: return null,
                items =
                    renderer.contents
                        .mapNotNull { content ->
                            content.musicTwoRowItemRenderer?.let { fromMusicTwoRowItemRenderer(it) }
                                ?: content.musicMultiRowListItemRenderer?.let { fromMusicMultiRowListItemRenderer(it) }
                                ?: content.musicResponsiveListItemRenderer?.let { fromMusicResponsiveListItemRenderer(it) }
                        }.ifEmpty { null } ?: return null,
                moreEndpoint =
                    renderer.header.musicCarouselShelfBasicHeaderRenderer.moreContentButton
                        ?.buttonRenderer
                        ?.navigationEndpoint
                        ?.browseEndpoint,
                layout = if (renderer.contents.any { it.musicMultiRowListItemRenderer != null }) ArtistSectionLayout.LIST else ArtistSectionLayout.GRID,
            )
        }

        private fun fromMusicMultiRowListItemRenderer(renderer: MusicMultiRowListItemRenderer): SongItem? {
            val thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getBestThumbnail() ?: return null
            val episodeId = renderer.title?.runs?.firstOrNull()?.navigationEndpoint?.browseEndpoint?.browseId?.removePrefix("MPED")
                ?: renderer.onTap?.anyWatchEndpoint?.videoId
                ?: renderer.menu?.menuRenderer?.items?.firstNotNullOfOrNull {
                    it.menuServiceItemRenderer?.serviceEndpoint?.queueAddEndpoint?.queueTarget?.videoId
                }
                ?: return null

            val title = renderer.title?.runs?.firstOrNull()?.text ?: return null
            val viewCount = renderer.subtitle?.runs
                ?.map { it.text }
                ?.firstOrNull { text ->
                    val lower = text.lowercase()
                    (lower.contains("view") || lower.contains("play")) && Regex("""[\d.,]+\s*[KMBkmb]?""").containsMatchIn(text)
                }

            val author = renderer.subtitle?.runs
                ?.map { it.text.replace("•", "").trim() }
                ?.firstOrNull { it.isNotBlank() && !it.lowercase().contains("view") && !it.lowercase().contains("ago") }

            return SongItem(
                id = episodeId,
                title = title,
                artists = listOfNotNull(author?.let { Artist(name = it, id = null) }),
                album = null,
                duration = null,
                thumbnail = thumbnail.normalizedUrl,
                thumbnailWidth = thumbnail.width,
                thumbnailHeight = thumbnail.height,
                explicit = false,
                endpoint = renderer.onTap?.anyWatchEndpoint,
                viewCountText = viewCount,
            )
        }

        private fun fromMusicResponsiveListItemRenderer(renderer: MusicResponsiveListItemRenderer): SongItem? {
            val thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getBestThumbnail() ?: return null
            val endpoint =
                renderer.overlay
                    ?.musicItemThumbnailOverlayRenderer
                    ?.content
                    ?.musicPlayButtonRenderer
                    ?.playNavigationEndpoint
            val watchEndpoint =
                endpoint?.anyWatchEndpoint
                    ?: renderer.navigationEndpoint?.anyWatchEndpoint

            // ðŸ”¥ à¦­à¦¿à¦‰ à¦?à¦¾à¦‰à¦¨à§à¦Ÿ à¦Ÿà§‡à¦?à§à¦¸à¦Ÿ parse à¦?à¦°à§‹ (à¦¯à§‡à¦®à¦¨ "109M plays")
            val viewCountText = renderer.flexColumns
                .drop(1)
                .mapNotNull { it.musicResponsiveListItemFlexColumnRenderer.text?.runs }
                .flatten()
                .map { it.text }
                .firstOrNull { text ->
                    val lower = text.lowercase()
                    (lower.contains("play") || lower.contains("view")) &&
                    Regex("[\\d.,]+\\s*[KMBkmb]?").containsMatchIn(text)
                }

            return SongItem(
                id =
                    renderer.playlistItemData?.videoId
                        ?: watchEndpoint?.videoId
                        ?: return null,
                title =
                    renderer.flexColumns
                        .firstOrNull()
                        ?.musicResponsiveListItemFlexColumnRenderer
                        ?.text
                        ?.runs
                        ?.firstOrNull()
                        ?.text ?: return null,
                artists =
                    PageHelper
                        .extractRuns(renderer.flexColumns, "MUSIC_PAGE_TYPE_ARTIST")
                        .ifEmpty {
                            renderer.flexColumns
                                .getOrNull(1)
                                ?.musicResponsiveListItemFlexColumnRenderer
                                ?.text
                                ?.runs
                        }?.oddElements()
                        ?.map {
                            Artist(
                                name = it.text,
                                id = it.navigationEndpoint?.browseEndpoint?.browseId,
                            )
                        }.orEmpty(),
                album =
                    PageHelper
                        .extractRuns(renderer.flexColumns, "MUSIC_PAGE_TYPE_ALBUM")
                        .ifEmpty {
                            renderer.flexColumns
                                .getOrNull(3)
                                ?.musicResponsiveListItemFlexColumnRenderer
                                ?.text
                                ?.runs
                        }?.firstOrNull()
                        ?.let {
                            Album(
                                name = it.text,
                                id = it.navigationEndpoint?.browseEndpoint?.browseId ?: return@let null,
                            )
                        },
                duration = null,
                thumbnail = thumbnail.normalizedUrl,
                thumbnailWidth = thumbnail.width,
                thumbnailHeight = thumbnail.height,
                explicit =
                    renderer.badges?.find {
                        it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                    } != null,
                endpoint = watchEndpoint,
                viewCountText = viewCountText,
            )
        }

        private fun fromMusicTwoRowItemRenderer(renderer: MusicTwoRowItemRenderer): YTItem? {
            return when {
                renderer.isSong -> {
                    val thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getBestThumbnail() ?: return null
                    // ðŸ”¥ à¦­à¦¿à¦‰ à¦?à¦¾à¦‰à¦¨à§à¦Ÿ à¦Ÿà§‡à¦?à§à¦¸à¦Ÿ parse à¦?à¦°à§‹ (à¦­à¦¿à¦¡à¦¿à¦“ à¦“ à¦²à¦¾à¦‡à¦­ à¦ªà¦¾à¦°à¦«à¦°à¦®à§à¦¯à¦¾à¦¨à§à¦¸à§‡à¦° à¦œà¦¨à§à¦¯)
                    val viewCount = renderer.subtitle?.runs
                        ?.map { it.text }
                        ?.firstOrNull { text ->
                            val lower = text.lowercase()
                            (lower.contains("view") || lower.contains("play")) && Regex("[\\d.,]+\\s*[KMBkmb]?").containsMatchIn(text)
                        }
                    SongItem(
                        id = renderer.navigationEndpoint.watchEndpoint?.videoId ?: return null,
                        title =
                            renderer.title.runs
                                ?.firstOrNull()
                                ?.text ?: return null,
                        artists =
                            listOfNotNull(
                                renderer.subtitle?.runs?.firstOrNull()?.let {
                                    Artist(
                                        name = it.text,
                                        id = it.navigationEndpoint?.browseEndpoint?.browseId,
                                    )
                                },
                            ),
                        album = null,
                        duration = null,
                        thumbnail = thumbnail.normalizedUrl,
                        thumbnailWidth = thumbnail.width,
                        thumbnailHeight = thumbnail.height,
                        explicit =
                            renderer.subtitleBadges?.find {
                                it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                            } != null,
                        endpoint = renderer.navigationEndpoint.anyWatchEndpoint,
                        viewCountText = viewCount,
                    )
                }

                renderer.isEpisode || renderer.navigationEndpoint.browseEndpoint?.browseId?.startsWith("MPED") == true -> {
                    val thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getBestThumbnail() ?: return null
                    val episodeId = renderer.thumbnailOverlay?.musicItemThumbnailOverlayRenderer?.content?.musicPlayButtonRenderer?.playNavigationEndpoint?.anyWatchEndpoint?.videoId
                        ?: renderer.navigationEndpoint.browseEndpoint?.browseId?.removePrefix("VL")
                        ?: return null
                    SongItem(
                        id = episodeId,
                        title = renderer.title.runs?.firstOrNull()?.text ?: return null,
                        artists = listOfNotNull(renderer.subtitle?.runs?.firstOrNull()?.let {
                            Artist(name = it.text, id = it.navigationEndpoint?.browseEndpoint?.browseId)
                        }),
                        album = null,
                        duration = null,
                        thumbnail = thumbnail.normalizedUrl,
                        thumbnailWidth = thumbnail.width,
                        thumbnailHeight = thumbnail.height,
                        explicit = false,
                        endpoint = renderer.navigationEndpoint.anyWatchEndpoint ?: renderer.thumbnailOverlay?.musicItemThumbnailOverlayRenderer?.content?.musicPlayButtonRenderer?.playNavigationEndpoint?.watchEndpoint,
                        viewCountText = null,
                    )
                }

                renderer.isAlbum -> {
                    val thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getBestThumbnail() ?: return null
                    // à¦…à§à¦¯à¦¾à¦²à¦¬à¦¾à¦®à§‡à¦° à¦†à¦°à§à¦Ÿà¦¿à¦¸à§à¦Ÿà§‡à¦° à¦¨à¦¾à¦® à¦¬à§‡à¦° à¦?à¦°à§‹
                    val albumArtist = renderer.subtitle?.runs
                        ?.firstOrNull { it.navigationEndpoint?.browseEndpoint?.browseId != null }
                        ?.let { Artist(name = it.text, id = it.navigationEndpoint?.browseEndpoint?.browseId) }
                    AlbumItem(
                        browseId = renderer.navigationEndpoint.browseEndpoint?.browseId ?: return null,
                        playlistId =
                            renderer.thumbnailOverlay
                                ?.musicItemThumbnailOverlayRenderer
                                ?.content
                                ?.musicPlayButtonRenderer
                                ?.playNavigationEndpoint
                                ?.anyWatchEndpoint
                                ?.playlistId ?: return null,
                        title =
                            renderer.title.runs
                                ?.firstOrNull()
                                ?.text ?: return null,
                        artists = listOfNotNull(albumArtist),
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

                renderer.isPlaylist || renderer.isPodcast -> {
                    // Playlist from YouTube Music
                    val thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getBestThumbnail() ?: return null
                    // ðŸ”¥ à¦­à¦¿à¦‰ à¦?à¦¾à¦‰à¦¨à§à¦Ÿ à¦Ÿà§‡à¦?à§à¦¸à¦Ÿ parse à¦?à¦°à§‹ (à¦¯à§‡à¦®à¦¨ "17M views")
                    val playlistViewCount = renderer.subtitle?.runs
                        ?.map { it.text }
                        ?.firstOrNull { text ->
                            val lower = text.lowercase()
                            lower.contains("view") && Regex("[\\d.,]+\\s*[KMBkmb]?").containsMatchIn(text)
                        }
                    
                    // ðŸ”¥ à¦¡à¦¾à¦¬à¦² à¦­à¦¿à¦‰à¦œ à¦«à¦¿à¦?à§à¦¸: author à¦¹à¦¿à¦¸à§‡à¦¬à§‡ à¦­à¦¿à¦‰à¦œà¦Ÿà¦¾ à¦¬à¦¾à¦¦ à¦¦à¦¾à¦“
                    val authorName = renderer.subtitle?.runs
                        ?.filterNot { it.text.lowercase().contains("view") || it.text.contains("•") || it.text.lowercase().contains("playlist") }
                        ?.map { it.text.replace("•", "").trim() }
                        ?.firstOrNull { it.isNotBlank() } ?: renderer.subtitle?.runs?.lastOrNull()?.text?.replace("•", "")?.trim()

                    PlaylistItem(
                        id =
                            renderer.navigationEndpoint.browseEndpoint
                                ?.browseId
                                ?.removePrefix("VL")
                                ?: renderer.navigationEndpoint.anyWatchEndpoint?.playlistId
                                ?: return null,
                        title =
                            renderer.title.runs
                                ?.firstOrNull()
                                ?.text ?: return null,
                        author =
                            Artist(
                                name = authorName ?: "Unknown",
                                id = null,
                            ),
                        songCountText = null,
                        thumbnail = thumbnail.normalizedUrl,
                        thumbnailWidth = thumbnail.width,
                        thumbnailHeight = thumbnail.height,
                        viewCountText = playlistViewCount,
                        playEndpoint =
                            renderer.thumbnailOverlay
                                ?.musicItemThumbnailOverlayRenderer
                                ?.content
                                ?.musicPlayButtonRenderer
                                ?.playNavigationEndpoint
                                ?.watchPlaylistEndpoint,
                        shuffleEndpoint =
                            renderer.menu
                                ?.menuRenderer
                                ?.items
                                ?.find {
                                    it.menuNavigationItemRenderer?.icon?.iconType == "MUSIC_SHUFFLE"
                                }?.menuNavigationItemRenderer
                                ?.navigationEndpoint
                                ?.watchPlaylistEndpoint,
                        radioEndpoint =
                            renderer.menu
                                ?.menuRenderer
                                ?.items
                                ?.find {
                                    it.menuNavigationItemRenderer?.icon?.iconType == "MIX"
                                }?.menuNavigationItemRenderer
                                ?.navigationEndpoint
                                ?.watchPlaylistEndpoint,
                    )
                }

                renderer.isArtist || renderer.isUserChannel -> {
                    val thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getBestThumbnail() ?: return null
                    // ðŸ”¥ monthly listeners parse à¦?à¦°à§‹
                    val rawMonthlyText = renderer.subtitle?.runs
                        ?.joinToString("") { it.text }
                        ?.trim()
                    
                    val monthlyText = rawMonthlyText?.let { text ->
                        if (text.contains("monthly", ignoreCase = true) && !text.contains("audience", ignoreCase = true)) {
                            text.replace("monthly", "monthly audience", ignoreCase = true).replace("listeners", "").trim()
                        } else if (text.contains("subscribers", ignoreCase = true)) {
                            text.replace("subscribers", "monthly audience", ignoreCase = true)
                        } else {
                            text
                        }
                    }?.takeIf { it.isNotBlank() }

                    ArtistItem(
                        id = renderer.navigationEndpoint.browseEndpoint?.browseId ?: return null,
                        title =
                            renderer.title.runs
                                ?.lastOrNull()
                                ?.text ?: return null,
                        thumbnail = thumbnail.normalizedUrl,
                        thumbnailWidth = thumbnail.width,
                        thumbnailHeight = thumbnail.height,
                        monthlyListenerCountText = monthlyText,
                        channelId =
                            renderer.menu
                                ?.menuRenderer
                                ?.items
                                ?.find {
                                    it.toggleMenuServiceItemRenderer?.defaultIcon?.iconType == "SUBSCRIBE"
                                }?.toggleMenuServiceItemRenderer
                                ?.defaultServiceEndpoint
                                ?.subscribeEndpoint
                                ?.channelIds
                                ?.firstOrNull(),
                        shuffleEndpoint =
                            renderer.menu
                                ?.menuRenderer
                                ?.items
                                ?.find {
                                    it.menuNavigationItemRenderer?.icon?.iconType == "MUSIC_SHUFFLE"
                                }?.menuNavigationItemRenderer
                                ?.navigationEndpoint
                                ?.watchPlaylistEndpoint,
                        radioEndpoint =
                            renderer.menu
                                ?.menuRenderer
                                ?.items
                                ?.find {
                                    it.menuNavigationItemRenderer?.icon?.iconType == "MIX"
                                }?.menuNavigationItemRenderer
                                ?.navigationEndpoint
                                ?.watchPlaylistEndpoint,
                    )
                }

                else -> {
                    null
                }
            }
        }
    }
}
