/*
 * Lytrix Player (2026)
 * Licensed under GNU General Public License v3.0 (GPL-3.0)
 */

package com.musicplayer.lytrix.innertube.pages

import com.musicplayer.lytrix.innertube.models.Album
import com.musicplayer.lytrix.innertube.models.AlbumItem
import com.musicplayer.lytrix.innertube.models.Artist
import com.musicplayer.lytrix.innertube.models.ArtistItem
import com.musicplayer.lytrix.innertube.models.EpisodeItem
import com.musicplayer.lytrix.innertube.models.MusicResponsiveListItemRenderer
import com.musicplayer.lytrix.innertube.models.PlaylistItem
import com.musicplayer.lytrix.innertube.models.PodcastItem
import com.musicplayer.lytrix.innertube.models.Run
import com.musicplayer.lytrix.innertube.models.SongItem
import com.musicplayer.lytrix.innertube.models.WatchEndpoint
import com.musicplayer.lytrix.innertube.models.YTItem
import com.musicplayer.lytrix.innertube.models.clean
import com.musicplayer.lytrix.innertube.models.splitBySeparator
import com.musicplayer.lytrix.innertube.models.toArtists
import com.musicplayer.lytrix.innertube.utils.parseTime

data class SearchResult(
    val items: List<YTItem>,
    val continuation: String? = null,
)

object SearchPage {
    fun toYTItem(renderer: MusicResponsiveListItemRenderer): YTItem? {
        val title = renderer.titleText ?: return null
        val thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getBestThumbnail()
        val metadata = renderer.metadataGroups()
        return when {
            renderer.isEpisode -> {
                val endpoint = renderer.watchEndpoint()
                val thumbUrl = renderer.thumbnail?.getThumbnailUrl() ?: thumbnail?.normalizedUrl ?: return null

                val secondaryLine = renderer.flexColumns.getOrNull(1)
                    ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.splitBySeparator()
                    ?: emptyList()

                val podcastSectionIndex = secondaryLine.indexOfFirst { section ->
                    section.any { run ->
                        run.navigationEndpoint?.browseEndpoint
                            ?.browseEndpointContextSupportedConfigs
                            ?.browseEndpointContextMusicConfig
                            ?.pageType == com.musicplayer.lytrix.innertube.models.BrowseEndpoint.BrowseEndpointContextSupportedConfigs.BrowseEndpointContextMusicConfig.Companion.MUSIC_PAGE_TYPE_PODCAST_SHOW_DETAIL_PAGE ||
                        run.navigationEndpoint?.browseEndpoint?.browseId?.startsWith("MPSP") == true
                    }
                }

                val podcast = if (podcastSectionIndex >= 0) {
                    secondaryLine[podcastSectionIndex].firstOrNull()?.let { run ->
                        Album(
                            name = run.text,
                            id = run.navigationEndpoint?.browseEndpoint?.browseId ?: return@let null,
                        )
                    }
                } else null

                val publishDateText = if (podcastSectionIndex > 0)
                    secondaryLine.getOrNull(podcastSectionIndex - 1)?.firstOrNull()?.text
                else metadata.getOrNull(0)?.firstOrNull()?.text

                val duration = secondaryLine.lastOrNull()?.firstOrNull()?.text?.parseTime() ?: metadata.duration()

                val author = metadata.asSequence().map { it.toArtists() }.firstOrNull { it.isNotEmpty() }?.firstOrNull()

                val episodeId = renderer.videoId
                    ?: renderer.playlistItemData?.videoId
                    ?: endpoint?.videoId
                    ?: renderer.navigationEndpoint?.watchEndpoint?.videoId
                    ?: renderer.overlay?.musicItemThumbnailOverlayRenderer?.content?.musicPlayButtonRenderer?.playNavigationEndpoint?.watchEndpoint?.videoId
                    ?: renderer.flexColumns.firstOrNull()?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()?.navigationEndpoint?.watchEndpoint?.videoId
                    ?: return null

                EpisodeItem(
                    id = episodeId,
                    title = title,
                    author = author,
                    podcast = podcast,
                    duration = duration,
                    publishDateText = publishDateText,
                    thumbnail = thumbUrl,
                    thumbnailWidth = thumbnail?.width,
                    thumbnailHeight = thumbnail?.height,
                    explicit = renderer.isExplicit,
                    endpoint = endpoint ?: renderer.navigationEndpoint?.watchEndpoint,
                )
            }

            renderer.isSong -> {
                val endpoint = renderer.watchEndpoint()
                val itemThumbnail = thumbnail ?: return null
                SongItem(
                    id = renderer.playlistItemData?.videoId ?: endpoint?.videoId ?: return null,
                    title = title,
                    artists =
                        metadata
                            .asSequence()
                            .map { it.toArtists() }
                            .firstOrNull { it.isNotEmpty() }
                            ?: metadata
                                .firstOrNull { group ->
                                    group.any { run ->
                                        val t = run.text.trim()
                                        val lower = t.lowercase()
                                        t.isNotBlank() &&
                                            t != "•" && t != "&" && t != "," &&
                                            lower != "song" && lower != "video" && lower != "album" && lower != "single" && lower != "ep" &&
                                            t.parseTime() == null &&
                                            !(lower.contains("view") || lower.contains("play")) &&
                                            !(t.length == 4 && t.all(Char::isDigit))
                                    }
                                }
                                ?.filter { run ->
                                    val t = run.text.trim()
                                    val lower = t.lowercase()
                                    t.isNotBlank() &&
                                        t != "•" && t != "&" && t != "," &&
                                        lower != "song" && lower != "video" &&
                                        t.parseTime() == null &&
                                        !(lower.contains("view") || lower.contains("play"))
                                }
                                ?.map { Artist(name = it.text.trim(), id = it.navigationEndpoint?.browseEndpoint?.browseId) }
                                .orEmpty(),
                    album =
                        metadata.getOrNull(1)?.firstOrNull()?.takeIf { it.navigationEndpoint?.browseEndpoint != null }?.let {
                            Album(
                                name = it.text,
                                id = it.navigationEndpoint?.browseEndpoint?.browseId!!,
                            )
                        },
                    duration = metadata.duration(),
                    viewCountText = metadata.viewCountText(),
                    viewCount = metadata.viewCount(),
                    thumbnail = itemThumbnail.normalizedUrl,
                    thumbnailWidth = itemThumbnail.width,
                    thumbnailHeight = itemThumbnail.height,
                    explicit = renderer.isExplicit,
                    endpoint = endpoint,
                )
            }

            renderer.isArtist -> {
                ArtistItem(
                    id = renderer.navigationEndpoint?.browseEndpoint?.browseId ?: return null,
                    title = title,
                    thumbnail = thumbnail?.normalizedUrl,
                    thumbnailWidth = thumbnail?.width,
                    thumbnailHeight = thumbnail?.height,
                    playEndpoint = renderer.watchEndpoint(),
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

            renderer.isAlbum -> {
                val itemThumbnail = thumbnail ?: return null
                AlbumItem(
                    browseId = renderer.navigationEndpoint?.browseEndpoint?.browseId ?: return null,
                    playlistId =
                        renderer
                            .watchEndpoint()
                            ?.playlistId
                            ?: return null,
                    title = title,
                    artists = metadata.getOrNull(0)?.toArtists()?.takeIf { it.isNotEmpty() },
                    year = metadata.year(),
                    thumbnail = itemThumbnail.normalizedUrl,
                    thumbnailWidth = itemThumbnail.width,
                    thumbnailHeight = itemThumbnail.height,
                    explicit = renderer.isExplicit,
                )
            }

            renderer.isPlaylist -> {
                val playlistMetadata = renderer.metadataGroups(clean = false)
                // 🔥 ভিউ কাউন্ট টেক্সট
                val viewCount = playlistMetadata.viewCountText() ?: playlistMetadata.lastText()?.takeIf { text -> 
                    val lower = text.lowercase()
                    lower.contains("view") && Regex("[\\d.,]+\\s*[KMBkmb]?").containsMatchIn(text)
                }
                
                PlaylistItem(
                    id =
                        renderer.navigationEndpoint
                            ?.browseEndpoint
                            ?.browseId
                            ?.removePrefix("VL")
                            ?: renderer.watchEndpoint()?.playlistId?.removePrefix("VL")
                            ?: return null,
                    title = title,
                    author = playlistMetadata.playlistAuthor(),
                    songCountText = playlistMetadata.lastText(),
                    thumbnail = thumbnail?.normalizedUrl,
                    thumbnailWidth = thumbnail?.width,
                    thumbnailHeight = thumbnail?.height,
                    viewCountText = viewCount,
                    playEndpoint =
                        renderer.watchEndpoint(),
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

            renderer.isUserChannel -> {
                ArtistItem(
                    id = renderer.navigationEndpoint?.browseEndpoint?.browseId ?: return null,
                    title = title,
                    thumbnail = thumbnail?.normalizedUrl,
                    thumbnailWidth = thumbnail?.width,
                    thumbnailHeight = thumbnail?.height,
                    playEndpoint = renderer.watchEndpoint(),
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
                    isProfile = true,
                )
            }

            renderer.isPodcast -> {
                val playlistMetadata = renderer.metadataGroups(clean = false)
                val podcastBrowseId = renderer.navigationEndpoint
                    ?.browseEndpoint
                    ?.browseId
                    ?: return null
                PodcastItem(
                    id = podcastBrowseId,
                    title = title,
                    author = playlistMetadata.playlistAuthor(),
                    episodeCountText = playlistMetadata.lastText(),
                    thumbnail = thumbnail?.normalizedUrl,
                    thumbnailWidth = thumbnail?.width,
                    thumbnailHeight = thumbnail?.height,
                    playEndpoint = renderer.watchEndpoint(),
                    shuffleEndpoint =
                        renderer.menu
                            ?.menuRenderer
                            ?.items
                            ?.find { it.menuNavigationItemRenderer?.icon?.iconType == "MUSIC_SHUFFLE" }
                            ?.menuNavigationItemRenderer
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

private val MusicResponsiveListItemRenderer.titleText: String?
    get() =
        flexColumns
            .firstOrNull()
            ?.musicResponsiveListItemFlexColumnRenderer
            ?.text
            ?.runs
            ?.joinToString(separator = "") { it.text }
            ?.takeIf { it.isNotBlank() }

private val MusicResponsiveListItemRenderer.isExplicit: Boolean
    get() =
        badges?.any {
            it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
        } == true

private fun MusicResponsiveListItemRenderer.metadataGroups(clean: Boolean = true): List<List<Run>> {
    val groups =
        flexColumns
            .drop(1)
            .flatMap {
                it.musicResponsiveListItemFlexColumnRenderer.text
                    ?.runs
                    ?.splitBySeparator()
                    .orEmpty()
            }
    return if (clean) groups.clean() else groups
}

private fun MusicResponsiveListItemRenderer.watchEndpoint(): WatchEndpoint? =
    navigationEndpoint?.anyWatchEndpoint
        ?: overlay
            ?.musicItemThumbnailOverlayRenderer
            ?.content
            ?.musicPlayButtonRenderer
            ?.playNavigationEndpoint
            ?.anyWatchEndpoint
        ?: flexColumns.firstOrNull()
            ?.musicResponsiveListItemFlexColumnRenderer
            ?.text?.runs?.firstOrNull()
            ?.navigationEndpoint?.anyWatchEndpoint

private fun List<List<Run>>.duration(): Int? {
    for (group in asReversed()) {
        for (run in group.asReversed()) {
            run.text.parseTime()?.let { return it }
        }
    }
    return null
}

private fun List<List<Run>>.year(): Int? {
    for (group in asReversed()) {
        for (run in group.asReversed()) {
            run.text.toIntOrNull()?.let { return it }
        }
    }
    return null
}

private fun List<List<Run>>.viewCountText(): String? =
    firstNotNullOfOrNull { group ->
        val text = group.joinToString(separator = "") { it.text }.trim()
        val lower = text.lowercase()
        text.takeIf {
            (lower.contains("view") || lower.contains("play")) && Regex("[\\d.,]+\\s*[KMBkmb]?").containsMatchIn(text)
        }
    }

private fun List<List<Run>>.viewCount(): Long? = viewCountText()?.let(::parseViewCount)

private fun parseViewCount(text: String): Long? {
    val match = ViewCountRegex.find(text) ?: return null
    val numberText = match.groupValues[1]
    val suffix = match.groupValues[2].uppercase()
    val value =
        if (suffix.isNotEmpty()) {
            numberText.replace(',', '.').toDoubleOrNull()
        } else {
            numberText.filter(Char::isDigit).toDoubleOrNull()
        } ?: return null
    val multiplier =
        when (suffix) {
            "K" -> 1_000.0
            "M" -> 1_000_000.0
            "B" -> 1_000_000_000.0
            else -> 1.0
        }
    return (value * multiplier).toLong()
}

private fun List<List<Run>>.playlistAuthor(): Artist? {
    val authorIndex = if (size >= 3) 1 else 0
    return getOrNull(authorIndex)
        ?.firstOrNull()
        ?.let {
            Artist(
                name = it.text,
                id = it.navigationEndpoint?.browseEndpoint?.browseId,
            )
        }
}

private fun List<List<Run>>.lastText(): String? =
    lastOrNull()
        ?.joinToString(separator = "") { it.text }
        ?.takeIf { it.isNotBlank() }

private val ViewCountRegex = Regex("""([\d.,]+)\s*([KMB]?)""", RegexOption.IGNORE_CASE)
