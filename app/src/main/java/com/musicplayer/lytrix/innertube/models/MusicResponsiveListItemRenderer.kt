/*
 * Lytrix Player (2026)
 * Licensed under GNU General Public License v3.0 (GPL-3.0)
 */

@file:OptIn(ExperimentalSerializationApi::class)

package com.musicplayer.lytrix.innertube.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import com.musicplayer.lytrix.innertube.models.BrowseEndpoint.BrowseEndpointContextSupportedConfigs.BrowseEndpointContextMusicConfig.Companion.MUSIC_PAGE_TYPE_ALBUM
import com.musicplayer.lytrix.innertube.models.BrowseEndpoint.BrowseEndpointContextSupportedConfigs.BrowseEndpointContextMusicConfig.Companion.MUSIC_PAGE_TYPE_ARTIST
import com.musicplayer.lytrix.innertube.models.BrowseEndpoint.BrowseEndpointContextSupportedConfigs.BrowseEndpointContextMusicConfig.Companion.MUSIC_PAGE_TYPE_AUDIOBOOK
import com.musicplayer.lytrix.innertube.models.BrowseEndpoint.BrowseEndpointContextSupportedConfigs.BrowseEndpointContextMusicConfig.Companion.MUSIC_PAGE_TYPE_LIBRARY_ARTIST
import com.musicplayer.lytrix.innertube.models.BrowseEndpoint.BrowseEndpointContextSupportedConfigs.BrowseEndpointContextMusicConfig.Companion.MUSIC_PAGE_TYPE_PLAYLIST
import com.musicplayer.lytrix.innertube.models.BrowseEndpoint.BrowseEndpointContextSupportedConfigs.BrowseEndpointContextMusicConfig.Companion.MUSIC_PAGE_TYPE_PODCAST_SHOW_DETAIL_PAGE
import com.musicplayer.lytrix.innertube.models.BrowseEndpoint.BrowseEndpointContextSupportedConfigs.BrowseEndpointContextMusicConfig.Companion.MUSIC_PAGE_TYPE_NON_MUSIC_AUDIO_TRACK_PAGE
import com.musicplayer.lytrix.innertube.models.BrowseEndpoint.BrowseEndpointContextSupportedConfigs.BrowseEndpointContextMusicConfig.Companion.MUSIC_PAGE_TYPE_USER_CHANNEL

/**
 * Typical list item
 * Used in [MusicCarouselShelfRenderer], [MusicShelfRenderer]
 * Appears in quick picks, search results, table items, etc.
 */
@Serializable
data class MusicResponsiveListItemRenderer(
    val badges: List<Badges>?,
    val fixedColumns: List<FlexColumn>?,
    val flexColumns: List<FlexColumn>,
    val thumbnail: ThumbnailRenderer?,
    val menu: Menu?,
    val playlistItemData: PlaylistItemData?,
    val overlay: Overlay?,
    val navigationEndpoint: NavigationEndpoint?,
) {
    val isSong: Boolean
        get() = navigationEndpoint == null
            || navigationEndpoint.watchEndpoint != null
            || navigationEndpoint.watchPlaylistEndpoint != null
            || overlay?.musicItemThumbnailOverlayRenderer
                ?.content?.musicPlayButtonRenderer
                ?.playNavigationEndpoint?.watchEndpoint != null
    val isPlaylist: Boolean
        get() =
            navigationEndpoint
                ?.browseEndpoint
                ?.browseEndpointContextSupportedConfigs
                ?.browseEndpointContextMusicConfig
                ?.pageType ==
                MUSIC_PAGE_TYPE_PLAYLIST
    val isAlbum: Boolean
        get() =
            navigationEndpoint
                ?.browseEndpoint
                ?.browseEndpointContextSupportedConfigs
                ?.browseEndpointContextMusicConfig
                ?.pageType ==
                MUSIC_PAGE_TYPE_ALBUM ||
                navigationEndpoint
                    ?.browseEndpoint
                    ?.browseEndpointContextSupportedConfigs
                    ?.browseEndpointContextMusicConfig
                    ?.pageType ==
                MUSIC_PAGE_TYPE_AUDIOBOOK
    val isArtist: Boolean
        get() =
            navigationEndpoint
                ?.browseEndpoint
                ?.browseEndpointContextSupportedConfigs
                ?.browseEndpointContextMusicConfig
                ?.pageType ==
                MUSIC_PAGE_TYPE_ARTIST ||
                navigationEndpoint
                    ?.browseEndpoint
                    ?.browseEndpointContextSupportedConfigs
                    ?.browseEndpointContextMusicConfig
                    ?.pageType ==
                MUSIC_PAGE_TYPE_LIBRARY_ARTIST
    val isPodcast: Boolean
        get() =
            navigationEndpoint
                ?.browseEndpoint
                ?.browseEndpointContextSupportedConfigs
                ?.browseEndpointContextMusicConfig
                ?.pageType ==
                MUSIC_PAGE_TYPE_PODCAST_SHOW_DETAIL_PAGE ||
            navigationEndpoint?.browseEndpoint?.browseId?.startsWith("MPSP") == true
    val isUserChannel: Boolean
        get() =
            navigationEndpoint
                ?.browseEndpoint
                ?.browseEndpointContextSupportedConfigs
                ?.browseEndpointContextMusicConfig
                ?.pageType ==
                MUSIC_PAGE_TYPE_USER_CHANNEL
    val isEpisode: Boolean
        get() {
            val firstRunEndpoint = flexColumns.firstOrNull()
                ?.musicResponsiveListItemFlexColumnRenderer
                ?.text?.runs?.firstOrNull()?.navigationEndpoint
            if (navigationEndpoint?.browseEndpoint?.browseEndpointContextSupportedConfigs?.browseEndpointContextMusicConfig?.pageType == MUSIC_PAGE_TYPE_NON_MUSIC_AUDIO_TRACK_PAGE ||
                firstRunEndpoint?.browseEndpoint?.browseEndpointContextSupportedConfigs?.browseEndpointContextMusicConfig?.pageType == MUSIC_PAGE_TYPE_NON_MUSIC_AUDIO_TRACK_PAGE ||
                firstRunEndpoint?.browseEndpoint?.browseId?.startsWith("MPED") == true ||
                navigationEndpoint?.browseEndpoint?.browseId?.startsWith("MPED") == true) {
                return true
            }
            if (overlay?.musicItemThumbnailOverlayRenderer?.content?.musicPlayButtonRenderer?.playNavigationEndpoint?.watchEndpoint?.watchEndpointMusicSupportedConfigs?.watchEndpointMusicConfig?.musicVideoType == "MUSIC_VIDEO_TYPE_PODCAST_EPISODE") {
                return true
            }
            val firstSubtitleText = flexColumns.getOrNull(1)
                ?.musicResponsiveListItemFlexColumnRenderer
                ?.text?.runs?.firstOrNull()?.text
            if (firstSubtitleText == "Episode") {
                return true
            }
            val hasPodcastLink = flexColumns.getOrNull(1)
                ?.musicResponsiveListItemFlexColumnRenderer
                ?.text?.runs?.any { run ->
                    run.navigationEndpoint?.browseEndpoint
                        ?.browseEndpointContextSupportedConfigs
                        ?.browseEndpointContextMusicConfig
                        ?.pageType == MUSIC_PAGE_TYPE_PODCAST_SHOW_DETAIL_PAGE ||
                    run.navigationEndpoint?.browseEndpoint?.browseId?.startsWith("MPSP") == true
                } == true
            val hasVideoId = videoId != null
            return hasPodcastLink && hasVideoId
        }

    val videoId: String?
        get() = playlistItemData?.videoId
            ?: flexColumns.firstOrNull()
                ?.musicResponsiveListItemFlexColumnRenderer
                ?.text?.runs?.firstOrNull()
                ?.navigationEndpoint?.watchEndpoint?.videoId
            ?: overlay?.musicItemThumbnailOverlayRenderer
                ?.content?.musicPlayButtonRenderer
                ?.playNavigationEndpoint?.watchEndpoint?.videoId
            ?: flexColumns.firstOrNull()
                ?.musicResponsiveListItemFlexColumnRenderer
                ?.text?.runs?.firstOrNull()
                ?.navigationEndpoint?.browseEndpoint?.browseId?.takeIf { it.startsWith("MPED") }?.removePrefix("MPED")
            ?: navigationEndpoint?.browseEndpoint?.browseId?.takeIf { it.startsWith("MPED") }?.removePrefix("MPED")

    @Serializable
    data class FlexColumn(
        @JsonNames("musicResponsiveListItemFixedColumnRenderer")
        val musicResponsiveListItemFlexColumnRenderer: MusicResponsiveListItemFlexColumnRenderer,
    ) {
        @Serializable
        data class MusicResponsiveListItemFlexColumnRenderer(
            val text: Runs?,
        )
    }

    @Serializable
    data class PlaylistItemData(
        val playlistSetVideoId: String?,
        val videoId: String,
    )

    @Serializable
    data class Overlay(
        val musicItemThumbnailOverlayRenderer: MusicItemThumbnailOverlayRenderer,
    ) {
        @Serializable
        data class MusicItemThumbnailOverlayRenderer(
            val content: Content,
        ) {
            @Serializable
            data class Content(
                val musicPlayButtonRenderer: MusicPlayButtonRenderer,
            ) {
                @Serializable
                data class MusicPlayButtonRenderer(
                    val playNavigationEndpoint: NavigationEndpoint?,
                )
            }
        }
    }
}
