/*
 * Lytrix Player (2026)
 * Licensed under GNU General Public License v3.0 (GPL-3.0)
 */

package com.musicplayer.lytrix.innertube.pages

import com.musicplayer.lytrix.innertube.models.Album
import com.musicplayer.lytrix.innertube.models.AlbumItem
import com.musicplayer.lytrix.innertube.models.Artist
import com.musicplayer.lytrix.innertube.models.ArtistItem
import com.musicplayer.lytrix.innertube.models.BrowseEndpoint
import com.musicplayer.lytrix.innertube.models.MusicCarouselShelfRenderer
import com.musicplayer.lytrix.innertube.models.MusicTwoRowItemRenderer
import com.musicplayer.lytrix.innertube.models.EpisodeItem
import com.musicplayer.lytrix.innertube.models.MusicMultiRowListItemRenderer
import com.musicplayer.lytrix.innertube.models.PlaylistItem
import com.musicplayer.lytrix.innertube.models.PodcastItem
import com.musicplayer.lytrix.innertube.models.SectionListRenderer
import com.musicplayer.lytrix.innertube.models.SongItem
import com.musicplayer.lytrix.innertube.models.YTItem
import com.musicplayer.lytrix.innertube.models.filterExplicit
import com.musicplayer.lytrix.innertube.models.splitBySeparator
import com.musicplayer.lytrix.innertube.models.toArtists
import com.musicplayer.lytrix.innertube.utils.parseTime

data class HomePage(
    val chips: List<Chip>?,
    val sections: List<Section>,
    val continuation: String? = null,
) {
    data class Chip(
        val title: String,
        val endpoint: BrowseEndpoint?,
        val deselectEndPoint: BrowseEndpoint? = null,
    ) {
        companion object {
            fun fromChipCloudChipRenderer(renderer: SectionListRenderer.Header.ChipCloudRenderer.Chip): Chip? {
                return Chip(
                    title =
                        renderer.chipCloudChipRenderer.text
                            ?.runs
                            ?.firstOrNull()
                            ?.text ?: return null,
                    endpoint = renderer.chipCloudChipRenderer.navigationEndpoint?.browseEndpoint,
                    deselectEndPoint = renderer.chipCloudChipRenderer.onDeselectedCommand?.browseEndpoint,
                )
            }
        }
    }

    data class Section(
        val title: String,
        val label: String?,
        val thumbnail: String?,
        val endpoint: BrowseEndpoint?,
        val items: List<YTItem>,
    ) {
        companion object {
            fun fromMusicCarouselShelfRenderer(renderer: MusicCarouselShelfRenderer): Section? {
                val title = renderer.header
                    ?.musicCarouselShelfBasicHeaderRenderer
                    ?.title
                    ?.runs
                    ?.firstOrNull()
                    ?.text ?: return null

                val items = buildList {
                    addAll(
                        renderer.contents
                            .mapNotNull { it.musicTwoRowItemRenderer }
                            .mapNotNull { fromMusicTwoRowItemRenderer(it) }
                    )
                    addAll(
                        renderer.contents
                            .mapNotNull { it.musicMultiRowListItemRenderer }
                            .mapNotNull { fromMusicMultiRowListItemRenderer(it) }
                    )
                    addAll(
                        renderer.contents
                            .mapNotNull { it.musicResponsiveListItemRenderer }
                            .mapNotNull { SearchSummaryPage.fromMusicResponsiveListItemRenderer(it) }
                    )
                }.ifEmpty { return null }

                return Section(
                    title = title,
                    label =
                        renderer.header.musicCarouselShelfBasicHeaderRenderer.strapline
                            ?.runs
                            ?.firstOrNull()
                            ?.text,
                    thumbnail =
                        renderer.header.musicCarouselShelfBasicHeaderRenderer.thumbnail
                            ?.getThumbnailUrl(),
                    endpoint =
                        renderer.header.musicCarouselShelfBasicHeaderRenderer.moreContentButton
                            ?.buttonRenderer
                            ?.navigationEndpoint
                            ?.browseEndpoint,
                    items = items,
                )
            }

            private fun fromMusicMultiRowListItemRenderer(renderer: MusicMultiRowListItemRenderer): EpisodeItem? {
                val subtitleRuns = renderer.subtitle?.runs?.splitBySeparator()
                val thumbUrl = renderer.thumbnail?.getThumbnailUrl()

                return EpisodeItem(
                    id = renderer.onTap?.watchEndpoint?.videoId ?: return null,
                    title = renderer.title?.runs?.firstOrNull()?.text ?: return null,
                    author = null,
                    podcast = null,
                    duration = subtitleRuns?.lastOrNull()?.firstOrNull()?.text?.parseTime(),
                    publishDateText = subtitleRuns?.firstOrNull()?.firstOrNull()?.text,
                    thumbnail = thumbUrl ?: "",
                    explicit = false,
                    endpoint = renderer.onTap.watchEndpoint,
                )
            }

            fun fromMusicShelfRenderer(renderer: com.musicplayer.lytrix.innertube.models.MusicShelfRenderer): Section? {
                val items = renderer.contents
                    ?.mapNotNull { it.musicResponsiveListItemRenderer }
                    ?.mapNotNull { SearchSummaryPage.fromMusicResponsiveListItemRenderer(it) }
                    ?: return null
                if (items.isEmpty()) return null
                return Section(
                    title = renderer.title?.runs?.firstOrNull()?.text ?: return null,
                    label = null,
                    thumbnail = null,
                    endpoint = null,
                    items = items
                )
            }

            private fun fromMusicTwoRowItemRenderer(renderer: MusicTwoRowItemRenderer): YTItem? {
                return when {
                    renderer.isSong -> {
                        val subtitleRuns = renderer.subtitle?.runs ?: return null
                        val artists = subtitleRuns.toArtists()
                        // ðŸ”¥ à¦­à¦¿à¦‰ à¦?à¦¾à¦‰à¦¨à§à¦Ÿ à¦ªà¦¾à¦°à§à¦¸ à¦?à¦°à§‹
                        val viewCount = subtitleRuns
                            .map { it.text }
                            .firstOrNull { text ->
                                val lower = text.lowercase()
                                (lower.contains("view") || lower.contains("play")) && Regex("[\\d.,]+\\s*[KMBkmb]?").containsMatchIn(text)
                            }

                        SongItem(
                            id = renderer.navigationEndpoint.watchEndpoint?.videoId ?: return null,
                            title =
                                renderer.title.runs
                                    ?.firstOrNull()
                                    ?.text ?: return null,
                            artists = artists,
                            album =
                                subtitleRuns
                                    .firstOrNull { run ->
                                        run.navigationEndpoint
                                            ?.browseEndpoint
                                            ?.browseId
                                            ?.startsWith("MPREb_") == true
                                    }?.let { run ->
                                        val endpoint = run.navigationEndpoint?.browseEndpoint ?: return null
                                        Album(
                                            name = run.text,
                                            id = endpoint.browseId,
                                        )
                                    },
                            duration = null,
                            thumbnail = renderer.thumbnailRenderer.getThumbnailUrl()
                                ?: return null,
                            explicit =
                                renderer.subtitleBadges?.any {
                                    it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                                } == true,
                            endpoint = renderer.navigationEndpoint.anyWatchEndpoint,
                            viewCountText = viewCount,
                        )
                    }

                    renderer.isAlbum -> {
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
                                    ?.watchPlaylistEndpoint
                                    ?.playlistId ?: return null,
                            title =
                                renderer.title.runs
                                    ?.firstOrNull()
                                    ?.text ?: return null,
                            artists = listOfNotNull(albumArtist),
                            year = null,
                            thumbnail = renderer.thumbnailRenderer.getThumbnailUrl() ?: return null,
                            explicit =
                                renderer.subtitleBadges?.find {
                                    it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                                } != null,
                        )
                    }

                    renderer.isPlaylist -> {
                        
                        // ðŸ”¥ à¦­à¦¿à¦‰ à¦?à¦¾à¦‰à¦¨à§à¦Ÿ à¦Ÿà§‡à¦?à§à¦¸à¦Ÿ parse à¦?à¦°à§‹
                        val playlistViewCount = renderer.subtitle?.runs
                            ?.map { it.text }
                            ?.firstOrNull { text ->
                                val lower = text.lowercase()
                                lower.contains("view") && Regex("[\\d.,]+\\s*[KMBkmb]?").containsMatchIn(text)
                            }
                        
                        // ðŸ”¥ author à¦¹à¦¿à¦¸à§‡à¦¬à§‡ à¦­à¦¿à¦‰à¦œà¦Ÿà¦¾ à¦¬à¦¾à¦¦ à¦¦à¦¾à¦“
                        val authorName = renderer.subtitle?.runs
                            ?.filterNot { it.text.lowercase().contains("view") || it.text.contains("•") || it.text.lowercase().contains("playlist") }
                            ?.map { it.text.replace("•", "").trim() }
                            ?.firstOrNull { it.isNotBlank() } ?: renderer.subtitle?.runs?.lastOrNull()?.text?.replace("•", "")?.trim()

                        PlaylistItem(
                            id =
                                renderer.navigationEndpoint.browseEndpoint
                                    ?.browseId
                                    ?.removePrefix("VL") ?: return null,
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
                            thumbnail = renderer.thumbnailRenderer.getThumbnailUrl() ?: return null,
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
                        
                        val rawMonthlyText = renderer.subtitle?.runs?.joinToString("") { it.text }?.trim()
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
                            monthlyListenerCountText = monthlyText,
                            id = renderer.navigationEndpoint.browseEndpoint?.browseId ?: return null,
                            title =
                                renderer.title.runs
                                    ?.lastOrNull()
                                    ?.text
                                    ?: renderer.title.runs?.firstOrNull()?.text ?: return null,
                            thumbnail = renderer.thumbnailRenderer.getThumbnailUrl() ?: return null,
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

                    renderer.isPodcast -> {
                        val thumbnail = renderer.thumbnailRenderer.getThumbnailUrl()
                        val authorName = renderer.subtitle?.runs
                            ?.filterNot { it.text.lowercase().contains("view") || it.text.contains("•") }
                            ?.map { it.text.replace("•", "").trim() }
                            ?.firstOrNull { it.isNotBlank() } ?: renderer.subtitle?.runs?.firstOrNull()?.text?.replace("•", "")?.trim()

                        PodcastItem(
                            id = renderer.navigationEndpoint.browseEndpoint?.browseId ?: return null,
                            title = renderer.title.runs?.firstOrNull()?.text ?: return null,
                            author = authorName?.let { Artist(name = it, id = renderer.subtitle?.runs?.firstOrNull()?.navigationEndpoint?.browseEndpoint?.browseId) },
                            episodeCountText = null,
                            thumbnail = thumbnail,
                            thumbnailWidth = null,
                            thumbnailHeight = null,
                            playEndpoint = renderer.thumbnailOverlay
                                ?.musicItemThumbnailOverlayRenderer?.content
                                ?.musicPlayButtonRenderer?.playNavigationEndpoint
                                ?.watchPlaylistEndpoint,
                            shuffleEndpoint = renderer.menu?.menuRenderer?.items?.find {
                                it.menuNavigationItemRenderer?.icon?.iconType == "MUSIC_SHUFFLE"
                            }?.menuNavigationItemRenderer?.navigationEndpoint?.watchPlaylistEndpoint,
                        )
                    }

                    renderer.isEpisode -> {
                        val videoId = renderer.thumbnailOverlay
                            ?.musicItemThumbnailOverlayRenderer?.content
                            ?.musicPlayButtonRenderer?.playNavigationEndpoint
                            ?.watchEndpoint?.videoId
                            ?: renderer.navigationEndpoint.watchEndpoint?.videoId
                        val titleText = renderer.title.runs?.firstOrNull()?.text
                        val thumbnail = renderer.thumbnailRenderer.getThumbnailUrl()

                        if (videoId == null || titleText == null || thumbnail == null) {
                            return null
                        }

                        val subtitleRuns = renderer.subtitle?.runs
                        val author = subtitleRuns?.firstOrNull()?.let {
                            Artist(
                                name = it.text,
                                id = it.navigationEndpoint?.browseEndpoint?.browseId
                            )
                        }
                        val dateText = subtitleRuns?.getOrNull(2)?.text

                        EpisodeItem(
                            id = videoId,
                            title = titleText,
                            author = author,
                            podcast = null,
                            duration = null,
                            publishDateText = dateText,
                            thumbnail = thumbnail,
                            explicit = renderer.subtitleBadges?.find {
                                it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                            } != null,
                            endpoint = renderer.navigationEndpoint.watchEndpoint
                        )
                    }

                    else -> {
                        null
                    }
                }
            }
        }
    }

    fun filterExplicit(enabled: Boolean = true) =
        if (enabled) {
            copy(
                sections =
                    sections.map {
                        it.copy(items = it.items.filterExplicit())
                    },
            )
        } else {
            this
        }
}
