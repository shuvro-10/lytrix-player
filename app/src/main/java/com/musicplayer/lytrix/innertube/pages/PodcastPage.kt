package com.musicplayer.lytrix.innertube.pages

import com.musicplayer.lytrix.innertube.models.Album
import com.musicplayer.lytrix.innertube.models.Artist
import com.musicplayer.lytrix.innertube.models.EpisodeItem
import com.musicplayer.lytrix.innertube.models.MusicMultiRowListItemRenderer
import com.musicplayer.lytrix.innertube.models.MusicResponsiveListItemRenderer
import com.musicplayer.lytrix.innertube.models.PodcastItem
import com.musicplayer.lytrix.innertube.models.splitBySeparator
import com.musicplayer.lytrix.innertube.utils.parseTime

data class PodcastPage(
    val podcast: PodcastItem,
    val episodes: List<EpisodeItem>,
    val continuation: String?,
) {
    companion object {
        fun fromMusicMultiRowListItemRenderer(
            renderer: MusicMultiRowListItemRenderer,
            podcast: PodcastItem? = null
        ): EpisodeItem? {
            val subtitleRuns = renderer.subtitle?.runs?.splitBySeparator()

            val thumbUrl = renderer.thumbnail?.musicThumbnailRenderer?.getBestThumbnail()?.normalizedUrl
                ?: renderer.thumbnail?.getThumbnailUrl()
            val watchEp = renderer.onTap?.watchEndpoint ?: renderer.onTap?.anyWatchEndpoint
            val videoId = watchEp?.videoId ?: return null

            return EpisodeItem(
                id = videoId,
                title = renderer.title?.runs?.firstOrNull()?.text ?: return null,
                author = podcast?.author,
                podcast = podcast?.let {
                    Album(name = it.title, id = it.id)
                },
                duration = subtitleRuns?.lastOrNull()?.firstOrNull()?.text?.parseTime(),
                publishDateText = subtitleRuns?.firstOrNull()?.firstOrNull()?.text,
                thumbnail = thumbUrl ?: "",
                explicit = false,
                endpoint = watchEp,
            )
        }

        fun fromMusicResponsiveListItemRenderer(
            renderer: MusicResponsiveListItemRenderer,
            podcast: PodcastItem? = null
        ): EpisodeItem? {
            val secondaryLineRuns = renderer.flexColumns
                .getOrNull(1)
                ?.musicResponsiveListItemFlexColumnRenderer
                ?.text
                ?.runs
                ?.splitBySeparator()

            val thumbUrl = renderer.thumbnail?.getThumbnailUrl()
                ?: renderer.thumbnail?.musicThumbnailRenderer?.getBestThumbnail()?.normalizedUrl

            val endpoint = renderer.navigationEndpoint?.anyWatchEndpoint
                ?: renderer.overlay?.musicItemThumbnailOverlayRenderer?.content?.musicPlayButtonRenderer?.playNavigationEndpoint?.anyWatchEndpoint
            val videoId = renderer.playlistItemData?.videoId ?: endpoint?.videoId ?: return null

            val authorRun = secondaryLineRuns?.firstOrNull()?.firstOrNull()
            val authorName = authorRun?.text?.trim()
            val isAuthorValid = authorName != null && !authorName.lowercase().contains("view") && !authorName.lowercase().contains("play") && !authorName.lowercase().contains("sub")

            return EpisodeItem(
                id = videoId,
                title = renderer.flexColumns.firstOrNull()
                    ?.musicResponsiveListItemFlexColumnRenderer?.text
                    ?.runs?.firstOrNull()?.text ?: return null,
                author = podcast?.author ?: if (isAuthorValid) {
                    Artist(
                        name = authorName!!,
                        id = authorRun?.navigationEndpoint?.browseEndpoint?.browseId,
                    )
                } else null,
                podcast = podcast?.let {
                    Album(name = it.title, id = it.id)
                },
                duration = secondaryLineRuns?.lastOrNull()?.firstOrNull()?.text?.parseTime(),
                publishDateText = secondaryLineRuns?.getOrNull(1)?.firstOrNull()?.text,
                thumbnail = thumbUrl ?: "",
                explicit = renderer.badges?.find {
                    it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                } != null,
                endpoint = endpoint,
            )
        }
    }
}
