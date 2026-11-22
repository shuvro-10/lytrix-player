/*
 * Lytrix Player (2026)
 * Licensed under GNU General Public License v3.0 (GPL-3.0)
 */

package com.musicplayer.lytrix.innertube.pages

import com.musicplayer.lytrix.innertube.models.Album
import com.musicplayer.lytrix.innertube.models.AlbumItem
import com.musicplayer.lytrix.innertube.models.Artist
import com.musicplayer.lytrix.innertube.models.ArtistItem
import com.musicplayer.lytrix.innertube.models.MusicResponsiveListItemRenderer
import com.musicplayer.lytrix.innertube.models.MusicTwoRowItemRenderer
import com.musicplayer.lytrix.innertube.models.PlaylistItem
import com.musicplayer.lytrix.innertube.models.SongItem
import com.musicplayer.lytrix.innertube.models.YTItem
import com.musicplayer.lytrix.innertube.models.oddElements

data class RelatedPage(
    val songs: List<SongItem>,
    val albums: List<AlbumItem>,
    val artists: List<ArtistItem>,
    val playlists: List<PlaylistItem>,
) {
    companion object {
        fun fromMusicResponsiveListItemRenderer(renderer: MusicResponsiveListItemRenderer): SongItem? {
            val thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getBestThumbnail() ?: return null
            return SongItem(
                id = renderer.playlistItemData?.videoId ?: return null,
                title =
                    renderer.flexColumns
                        .firstOrNull()
                        ?.musicResponsiveListItemFlexColumnRenderer
                        ?.text
                        ?.runs
                        ?.firstOrNull()
                        ?.text ?: return null,
                artists =
                    renderer.flexColumns.getOrNull(1)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.oddElements()?.map {
                        Artist(
                            name = it.text,
                            id = it.navigationEndpoint?.browseEndpoint?.browseId,
                        )
                    } ?: emptyList(),
                album =
                    renderer.flexColumns.getOrNull(2)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()?.let { run ->
                        run.navigationEndpoint?.browseEndpoint?.browseId?.let { browseId ->
                            Album(
                                name = run.text,
                                id = browseId,
                            )
                        }
                    },
                duration = null,
                thumbnail = thumbnail.normalizedUrl,
                thumbnailWidth = thumbnail.width,
                thumbnailHeight = thumbnail.height,
                explicit =
                    renderer.badges?.find {
                        it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                    } != null,
                endpoint =
                    renderer.navigationEndpoint?.anyWatchEndpoint
                        ?: renderer.overlay
                            ?.musicItemThumbnailOverlayRenderer
                            ?.content
                            ?.musicPlayButtonRenderer
                            ?.playNavigationEndpoint
                            ?.anyWatchEndpoint,
            )
        }

        fun fromMusicTwoRowItemRenderer(renderer: MusicTwoRowItemRenderer): YTItem? {
            return when {
                renderer.isAlbum -> {
                    val thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getBestThumbnail() ?: return null
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
                        artists =
                            listOfNotNull(
                                Artist(
                                    name = "",
                                    id =
                                        renderer.menu
                                            ?.menuRenderer
                                            ?.items
                                            ?.find {
                                                it.menuNavigationItemRenderer?.icon?.iconType == "ARTIST"
                                            }?.menuNavigationItemRenderer
                                            ?.navigationEndpoint
                                            ?.browseEndpoint
                                            ?.browseId,
                                ),
                            ),
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

                renderer.isPlaylist -> {
                    val thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getBestThumbnail() ?: return null
                    val authorName = renderer.subtitle?.runs
                        ?.filterNot { it.text.lowercase().contains("view") || it.text.contains("\u2022") || it.text.lowercase().contains("playlist") }
                        ?.map { it.text.replace("\u2022", "").trim() }
                        ?.firstOrNull { it.isNotBlank() } ?: renderer.subtitle?.runs?.lastOrNull()?.text?.replace("\u2022", "")?.trim()

                    val playlistViewCount = renderer.subtitle?.runs
                        ?.map { it.text }
                        ?.firstOrNull { text ->
                            val lower = text.lowercase()
                            lower.contains("view") && Regex("[\\d.,]+\\s*[KMBkmb]?").containsMatchIn(text)
                        }

                    PlaylistItem(
                        id =
                            renderer.navigationEndpoint.browseEndpoint
                                ?.browseId
                                ?.removePrefix("VL") ?: return null,
                        title =
                            renderer.title.runs
                                ?.firstOrNull()
                                ?.text ?: return null,
                        author = Artist(name = authorName ?: "Unknown", id = null),
                        songCountText =
                            renderer.subtitle
                                ?.runs
                                ?.lastOrNull()
                                ?.text,
                        viewCountText = playlistViewCount,
                        thumbnail = thumbnail.normalizedUrl,
                        thumbnailWidth = thumbnail.width,
                        thumbnailHeight = thumbnail.height,
                        playEndpoint =
                            renderer.thumbnailOverlay
                                ?.musicItemThumbnailOverlayRenderer
                                ?.content
                                ?.musicPlayButtonRenderer
                                ?.playNavigationEndpoint
                                ?.watchPlaylistEndpoint ?: return null,
                        shuffleEndpoint =
                            renderer.menu
                                ?.menuRenderer
                                ?.items
                                ?.find {
                                    it.menuNavigationItemRenderer?.icon?.iconType == "MUSIC_SHUFFLE"
                                }?.menuNavigationItemRenderer
                                ?.navigationEndpoint
                                ?.watchPlaylistEndpoint ?: return null,
                        radioEndpoint =
                            renderer.menu.menuRenderer.items
                                .find {
                                    it.menuNavigationItemRenderer?.icon?.iconType == "MIX"
                                }?.menuNavigationItemRenderer
                                ?.navigationEndpoint
                                ?.watchPlaylistEndpoint,
                    )
                }

                renderer.isArtist -> {
                    val thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getBestThumbnail() ?: return null
                    
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
                                ?.firstOrNull()
                                ?.text ?: return null,
                        thumbnail = thumbnail.normalizedUrl,
                        thumbnailWidth = thumbnail.width,
                        thumbnailHeight = thumbnail.height,
                        shuffleEndpoint =
                            renderer.menu
                                ?.menuRenderer
                                ?.items
                                ?.find {
                                    it.menuNavigationItemRenderer?.icon?.iconType == "MUSIC_SHUFFLE"
                                }?.menuNavigationItemRenderer
                                ?.navigationEndpoint
                                ?.watchPlaylistEndpoint ?: return null,
                        radioEndpoint =
                            renderer.menu.menuRenderer.items
                                .find {
                                    it.menuNavigationItemRenderer?.icon?.iconType == "MIX"
                                }?.menuNavigationItemRenderer
                                ?.navigationEndpoint
                                ?.watchPlaylistEndpoint ?: return null,
                    )
                }

                renderer.isSong || renderer.navigationEndpoint.watchEndpoint != null -> {
                    val thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getBestThumbnail() ?: return null
                    val videoId = renderer.navigationEndpoint.watchEndpoint?.videoId
                        ?: renderer.thumbnailOverlay?.musicItemThumbnailOverlayRenderer?.content?.musicPlayButtonRenderer?.playNavigationEndpoint?.watchEndpoint?.videoId
                        ?: return null

                    val artists = renderer.subtitle?.runs
                        ?.filterNot { it.text.lowercase().contains("view") || it.text.contains("•") }
                        ?.map { Artist(name = it.text.replace("•", "").trim(), id = it.navigationEndpoint?.browseEndpoint?.browseId) }
                        ?.filter { it.name.isNotBlank() }
                        ?: emptyList()

                    val viewCount = renderer.subtitle?.runs
                        ?.map { it.text }
                        ?.firstOrNull { text ->
                            val lower = text.lowercase()
                            lower.contains("view") && Regex("[\\d.,]+\\s*[KMBkmb]?").containsMatchIn(text)
                        }

                    SongItem(
                        id = videoId,
                        title = renderer.title.runs?.firstOrNull()?.text ?: return null,
                        artists = artists,
                        album = null,
                        duration = null,
                        viewCountText = viewCount,
                        thumbnail = thumbnail.normalizedUrl,
                        thumbnailWidth = thumbnail.width,
                        thumbnailHeight = thumbnail.height,
                        explicit = renderer.subtitleBadges?.find {
                            it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                        } != null,
                        endpoint = renderer.navigationEndpoint.watchEndpoint,
                    )
                }

                else -> {
                    null
                }
            }
        }
    }
}
