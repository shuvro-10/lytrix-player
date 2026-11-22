/*
 * Lytrix Player (2026)
 * Licensed under GNU General Public License v3.0 (GPL-3.0)
 */

package com.musicplayer.lytrix.innertube.pages

import com.musicplayer.lytrix.innertube.models.AlbumItem
import com.musicplayer.lytrix.innertube.models.Artist
import com.musicplayer.lytrix.innertube.models.ArtistItem
import com.musicplayer.lytrix.innertube.models.MusicResponsiveListItemRenderer
import com.musicplayer.lytrix.innertube.models.MusicTwoRowItemRenderer
import com.musicplayer.lytrix.innertube.models.PlaylistItem
import com.musicplayer.lytrix.innertube.models.PodcastItem
import com.musicplayer.lytrix.innertube.models.Run
import com.musicplayer.lytrix.innertube.models.YTItem

data class LibraryPage(
    val items: List<YTItem>,
    val continuation: String?,
) {
    companion object {
        fun fromMusicTwoRowItemRenderer(renderer: MusicTwoRowItemRenderer): YTItem? {
            return when {
                renderer.isAlbum -> {
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

                    AlbumItem(
                        browseId = browseId,
                        playlistId = playlistId,
                        title =
                            renderer.title.runs
                                ?.firstOrNull()
                                ?.text ?: return null,
                        artists = parseArtists(renderer.subtitle?.runs),
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
                    val thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getBestThumbnail()?.normalizedUrl
                        ?: renderer.thumbnailRenderer.getThumbnailUrl()
                        ?: return null
                    PlaylistItem(
                        id =
                            renderer.navigationEndpoint.browseEndpoint
                                ?.browseId
                                ?.removePrefix("VL") ?: return null,
                        title =
                            renderer.title.runs
                                ?.firstOrNull()
                                ?.text ?: return null,
                        author = null,
                        songCountText =
                            renderer.subtitle
                                ?.runs
                                ?.lastOrNull()
                                ?.text,
                        thumbnail = thumbnail,
                        thumbnailWidth = renderer.thumbnailRenderer.musicThumbnailRenderer?.getBestThumbnail()?.width,
                        thumbnailHeight = renderer.thumbnailRenderer.musicThumbnailRenderer?.getBestThumbnail()?.height,
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
                        isEditable =
                            renderer.menu?.menuRenderer?.items?.find {
                                it.menuNavigationItemRenderer?.icon?.iconType == "EDIT"
                            } != null,
                    )
                }

                renderer.isArtist || renderer.isUserChannel -> {
                    val thumbnail = renderer.thumbnailRenderer.getThumbnailUrl()
                        ?: renderer.thumbnailRenderer.musicThumbnailRenderer?.getBestThumbnail()?.normalizedUrl
                    ArtistItem(
                        id = renderer.navigationEndpoint.browseEndpoint?.browseId ?: return null,
                        title =
                            renderer.title.runs
                                ?.firstOrNull()
                                ?.text
                                ?: renderer.title.runs?.lastOrNull()?.text ?: return null,
                        thumbnail = thumbnail,
                        thumbnailWidth = null,
                        thumbnailHeight = null,
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
                            renderer.menu?.menuRenderer?.items
                                ?.find {
                                    it.menuNavigationItemRenderer?.icon?.iconType == "MIX"
                                }?.menuNavigationItemRenderer
                                ?.navigationEndpoint
                                ?.watchPlaylistEndpoint,
                    )
                }

                renderer.isPodcast -> {
                    val thumbnail = renderer.thumbnailRenderer.getThumbnailUrl()
                        ?: renderer.thumbnailRenderer.musicThumbnailRenderer?.getBestThumbnail()?.normalizedUrl
                    val authorName = renderer.subtitle?.runs
                        ?.filterNot { it.text.lowercase().contains("view") || it.text.contains("•") || it.text.contains("·") }
                        ?.map { it.text.replace("•", "").replace("·", "").trim() }
                        ?.firstOrNull { it.isNotBlank() } ?: renderer.subtitle?.runs?.firstOrNull()?.text?.replace("•", "")?.replace("·", "")?.trim()

                    PodcastItem(
                        id = renderer.navigationEndpoint.browseEndpoint?.browseId ?: return null,
                        title = renderer.title.runs?.firstOrNull()?.text ?: return null,
                        author = authorName?.let { Artist(name = it, id = renderer.subtitle?.runs?.firstOrNull()?.navigationEndpoint?.browseEndpoint?.browseId) },
                        episodeCountText = renderer.subtitle?.runs?.lastOrNull()?.text,
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

                else -> {
                    null
                }
            }
        }

        fun fromMusicResponsiveListItemRenderer(renderer: MusicResponsiveListItemRenderer): YTItem? {
            return when {
                renderer.isSong -> {
                    renderer.toSongItem()
                }

                renderer.isArtist || renderer.isUserChannel -> {
                    val thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getBestThumbnail()?.normalizedUrl
                        ?: renderer.thumbnail?.getThumbnailUrl()
                    ArtistItem(
                        id = renderer.navigationEndpoint?.browseEndpoint?.browseId ?: return null,
                        title =
                            renderer.flexColumns
                                .firstOrNull()
                                ?.musicResponsiveListItemFlexColumnRenderer
                                ?.text
                                ?.runs
                                ?.firstOrNull()
                                ?.text
                                ?: return null,
                        thumbnail = thumbnail,
                        thumbnailWidth = null,
                        thumbnailHeight = null,
                        shuffleEndpoint =
                            renderer.menu
                                ?.menuRenderer
                                ?.items
                                ?.find { it.menuNavigationItemRenderer?.icon?.iconType == "MUSIC_SHUFFLE" }
                                ?.menuNavigationItemRenderer
                                ?.navigationEndpoint
                                ?.watchPlaylistEndpoint,
                        radioEndpoint =
                            renderer.menu
                                ?.menuRenderer
                                ?.items
                                ?.find { it.menuNavigationItemRenderer?.icon?.iconType == "MIX" }
                                ?.menuNavigationItemRenderer
                                ?.navigationEndpoint
                                ?.watchPlaylistEndpoint,
                    )
                }

                renderer.isPodcast -> {
                    val authorRun = renderer.flexColumns.getOrNull(1)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()
                    PodcastItem(
                        id = renderer.navigationEndpoint?.browseEndpoint?.browseId ?: return null,
                        title = renderer.flexColumns.firstOrNull()?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()?.text ?: return null,
                        author = authorRun?.let { Artist(name = it.text, id = it.navigationEndpoint?.browseEndpoint?.browseId) },
                        episodeCountText = renderer.flexColumns.getOrNull(1)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.lastOrNull()?.text,
                        thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getBestThumbnail()?.normalizedUrl
                        ?: renderer.thumbnail?.getThumbnailUrl(),
                        thumbnailWidth = null,
                        thumbnailHeight = null,
                        playEndpoint = renderer.overlay?.musicItemThumbnailOverlayRenderer?.content?.musicPlayButtonRenderer?.playNavigationEndpoint?.watchPlaylistEndpoint,
                        shuffleEndpoint = renderer.menu?.menuRenderer?.items?.find {
                            it.menuNavigationItemRenderer?.icon?.iconType == "MUSIC_SHUFFLE"
                        }?.menuNavigationItemRenderer?.navigationEndpoint?.watchPlaylistEndpoint,
                    )
                }

                else -> {
                    null
                }
            }
        }

        private fun parseArtists(runs: List<Run>?): List<Artist> {
            val artists = mutableListOf<Artist>()

            if (runs != null) {
                for (run in runs) {
                    if (run.navigationEndpoint != null) {
                        artists.add(
                            Artist(
                                id = run.navigationEndpoint.browseEndpoint?.browseId!!,
                                name = run.text,
                            ),
                        )
                    }
                }
            }
            return artists
        }
    }
}
