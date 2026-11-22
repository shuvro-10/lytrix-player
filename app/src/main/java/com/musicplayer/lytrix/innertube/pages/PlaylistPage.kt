/*
 * Lytrix Player (2026)
 * Licensed under GNU General Public License v3.0 (GPL-3.0)
 */

package com.musicplayer.lytrix.innertube.pages

import com.musicplayer.lytrix.innertube.models.Album
import com.musicplayer.lytrix.innertube.models.Artist
import com.musicplayer.lytrix.innertube.models.MusicResponsiveListItemRenderer
import com.musicplayer.lytrix.innertube.models.PlaylistItem
import com.musicplayer.lytrix.innertube.models.Run
import com.musicplayer.lytrix.innertube.models.SongItem
import com.musicplayer.lytrix.innertube.models.WatchEndpoint
import com.musicplayer.lytrix.innertube.models.clean
import com.musicplayer.lytrix.innertube.models.oddElements
import com.musicplayer.lytrix.innertube.models.splitBySeparator
import com.musicplayer.lytrix.innertube.utils.parseTime

data class PlaylistPage(
    val playlist: PlaylistItem,
    val songs: List<SongItem>,
    val songsContinuation: String?,
    val continuation: String?,
) {
    companion object {
        fun fromMusicResponsiveListItemRenderer(
            renderer: MusicResponsiveListItemRenderer,
            playlistId: String? = null,
        ): SongItem? {
            if (playlistId != null && !renderer.belongsToPlaylist(playlistId)) return null
            return renderer.toSongItem(albumColumnIndex = 2)
        }
    }
}

private fun MusicResponsiveListItemRenderer.belongsToPlaylist(playlistId: String): Boolean {
    val expectedPlaylistId = playlistId.removePrefix("VL")
    val endpoint = watchEndpoint()
    val endpointPlaylistId = endpoint?.playlistId?.removePrefix("VL")
    if (playlistItemData?.playlistSetVideoId?.isNotBlank() == true) {
        return endpointPlaylistId == null || endpointPlaylistId == expectedPlaylistId
    }
    if (endpointPlaylistId != expectedPlaylistId) return false
    return endpoint.playlistSetVideoId?.isNotBlank() == true || endpoint.index != null
}

internal fun MusicResponsiveListItemRenderer.toSongItem(albumColumnIndex: Int? = 2): SongItem? {
    val endpoint = watchEndpoint()
    val videoId = playlistItemData?.videoId ?: endpoint?.videoId ?: return null
    val thumbnail = thumbnail?.musicThumbnailRenderer?.getBestThumbnail() ?: return null
    val metadataGroups = metadataGroups()
    return SongItem(
        id = videoId,
        title = titleText ?: return null,
        artists = artistsFromColumn(1).ifEmpty { metadataGroups.firstOrNull().toArtists() },
        album =
            albumColumnIndex?.let(::albumFromColumn)
                ?: metadataGroups.drop(1).firstNotNullOfOrNull { it.toAlbum() },
        duration = fixedDuration ?: metadataGroups.duration(),
        thumbnail = thumbnail.normalizedUrl,
        thumbnailWidth = thumbnail.width,
        thumbnailHeight = thumbnail.height,
        explicit = isExplicit,
        endpoint = endpoint,
        setVideoId = playlistItemData?.playlistSetVideoId ?: endpoint?.playlistSetVideoId,
    )
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

private val MusicResponsiveListItemRenderer.fixedDuration: Int?
    get() =
        fixedColumns
            ?.firstOrNull()
            ?.musicResponsiveListItemFlexColumnRenderer
            ?.text
            ?.runs
            ?.firstOrNull()
            ?.text
            ?.parseTime()

private fun MusicResponsiveListItemRenderer.watchEndpoint(): WatchEndpoint? =
    navigationEndpoint?.anyWatchEndpoint
        ?: overlay
            ?.musicItemThumbnailOverlayRenderer
            ?.content
            ?.musicPlayButtonRenderer
            ?.playNavigationEndpoint
            ?.anyWatchEndpoint

private fun MusicResponsiveListItemRenderer.metadataGroups(): List<List<Run>> =
    flexColumns
        .drop(1)
        .flatMap {
            it.musicResponsiveListItemFlexColumnRenderer.text
                ?.runs
                ?.splitBySeparator()
                .orEmpty()
        }.clean()

private fun MusicResponsiveListItemRenderer.artistsFromColumn(index: Int): List<Artist> =
    flexColumns
        .getOrNull(index)
        ?.musicResponsiveListItemFlexColumnRenderer
        ?.text
        ?.runs
        ?.splitBySeparator()
        ?.clean()
        ?.firstOrNull()
        .toArtists()

private fun MusicResponsiveListItemRenderer.albumFromColumn(index: Int): Album? =
    flexColumns
        .getOrNull(index)
        ?.musicResponsiveListItemFlexColumnRenderer
        ?.text
        ?.runs
        ?.toAlbum()

private fun List<Run>?.toArtists(): List<Artist> =
    this
        ?.oddElements()
        ?.mapNotNull { run ->
            val text = run.text.trim()
            if (text.isBlank() || text.parseTime() != null || isViewOrMetaText(text)) {
                null
            } else {
                Artist(
                    name = text,
                    id = run.navigationEndpoint?.browseEndpoint?.browseId,
                )
            }
        }.orEmpty()

private fun isViewOrMetaText(text: String): Boolean {
    val lower = text.lowercase()
    if (lower.contains("view") || lower.contains("play") || lower.contains("subscriber") || lower.contains("sub") || lower.contains("like")) {
        return true
    }
    return text.matches(Regex("""\d+[\d.,]*\s*[kmbKMB]?"""))
}

private fun List<Run>.toAlbum(): Album? =
    firstNotNullOfOrNull { run ->
        val browseId = run.navigationEndpoint?.browseEndpoint?.browseId ?: return@firstNotNullOfOrNull null
        run.text
            .takeIf { it.isNotBlank() && it.parseTime() == null }
            ?.let { name ->
                Album(
                    name = name,
                    id = browseId,
                )
            }
    }

private fun List<List<Run>>.duration(): Int? {
    for (group in asReversed()) {
        for (run in group.asReversed()) {
            run.text.parseTime()?.let { return it }
        }
    }
    return null
}
