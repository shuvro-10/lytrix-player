/*
 * Lytrix Player (2026)
 * Licensed under GNU General Public License v3.0 (GPL-3.0)
 */

package com.musicplayer.lytrix.innertube.models

import com.musicplayer.lytrix.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_OMV
import com.musicplayer.lytrix.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_UGC
import java.util.Locale

sealed class YTItem {
    abstract val id: String
    abstract val title: String
    abstract val thumbnail: String?
    abstract val thumbnailWidth: Int?
    abstract val thumbnailHeight: Int?
    abstract val explicit: Boolean
    abstract val shareLink: String
}

data class Artist(
    val name: String,
    val id: String?,
)

data class Album(
    val name: String,
    val id: String,
)

enum class AlbumReleaseType {
    ALBUM,
    SINGLE,
    EP,
    ;

    companion object {
        fun fromLabel(label: String?): AlbumReleaseType =
            when (label?.trim()?.lowercase(Locale.ROOT)) {
                "single", "singles" -> SINGLE
                "ep", "eps" -> EP
                else -> ALBUM
            }
    }
}

data class SongItem(
    override val id: String,
    override val title: String,
    val artists: List<Artist>,
    val album: Album? = null,
    val duration: Int? = null,
    val chartPosition: Int? = null,
    val chartChange: String? = null,
    override val thumbnail: String,
    override val explicit: Boolean = false,
    val endpoint: WatchEndpoint? = null,
    val setVideoId: String? = null,
    val viewCountText: String? = null,
    val viewCount: Long? = null,
    override val thumbnailWidth: Int? = null,
    override val thumbnailHeight: Int? = null,
) : YTItem() {
    override val shareLink: String
        get() = "https://music.youtube.com/watch?v=$id"
}

fun Iterable<SongItem>.distinctByPlaylistEntry(): List<SongItem> =
    distinctBy { song -> song.setVideoId?.takeIf(String::isNotBlank) ?: song.id }

data class AlbumItem(
    val browseId: String,
    val playlistId: String,
    override val id: String = browseId,
    override val title: String,
    val artists: List<Artist>?,
    val year: Int? = null,
    override val thumbnail: String,
    override val explicit: Boolean = false,
    val releaseType: AlbumReleaseType = AlbumReleaseType.ALBUM,
    override val thumbnailWidth: Int? = null,
    override val thumbnailHeight: Int? = null,
) : YTItem() {
    override val shareLink: String
        get() = "https://music.youtube.com/playlist?list=$playlistId"
}

data class PlaylistItem(
    override val id: String,
    override val title: String,
    val author: Artist?,
    val songCountText: String?,
    override val thumbnail: String?,
    val playEndpoint: WatchEndpoint?,
    val shuffleEndpoint: WatchEndpoint?,
    val radioEndpoint: WatchEndpoint?,
    val isEditable: Boolean = false,
    val description: String? = null,
    override val thumbnailWidth: Int? = null,
    override val thumbnailHeight: Int? = null,
    val viewCountText: String? = null, // 🔥 ভিউ কাউন্ট (যেমন: "17M views")
) : YTItem() {
    override val explicit: Boolean
        get() = false
    override val shareLink: String
        get() = "https://music.youtube.com/playlist?list=$id"
}

data class ArtistItem(
    override val id: String,
    override val title: String,
    override val thumbnail: String?,
    val channelId: String? = null,
    val playEndpoint: WatchEndpoint? = null,
    val shuffleEndpoint: WatchEndpoint?,
    val radioEndpoint: WatchEndpoint?,
    val subscriberCountText: String? = null,
    val monthlyListenerCountText: String? = null,
    override val thumbnailWidth: Int? = null,
    override val thumbnailHeight: Int? = null,
    val isProfile: Boolean = false,
) : YTItem() {
    override val explicit: Boolean
        get() = false
    override val shareLink: String
        get() = "https://music.youtube.com/channel/$id"
}

// 🔥 StationItem অ্যাড করা হলো (রেডিও স্টেশন ক্যাটাগরিগুলো আনার জন্য)
data class StationItem(
    override val id: String,
    override val title: String,
    override val thumbnail: String?,
    val playEndpoint: WatchEndpoint? = null,
    val radioEndpoint: WatchEndpoint? = null,
    override val thumbnailWidth: Int? = null,
    override val thumbnailHeight: Int? = null,
) : YTItem() {
    override val explicit: Boolean
        get() = false
    override val shareLink: String
        get() = "https://music.youtube.com/playlist?list=$id"
}

// 🔥 VideoItem অ্যাড করা হলো (মিউজিক ভিডিও ক্যাটাগরিগুলো আনার জন্য)
data class VideoItem(
    override val id: String,
    override val title: String,
    val authors: List<Artist>? = null,
    override val thumbnail: String,
    override val explicit: Boolean = false,
    val duration: Int? = null,
    val viewCountText: String? = null,
    val endpoint: WatchEndpoint? = null,
    override val thumbnailWidth: Int? = null,
    override val thumbnailHeight: Int? = null,
) : YTItem() {
    override val shareLink: String
        get() = "https://music.youtube.com/watch?v=$id"
}

data class PodcastItem(
    override val id: String,
    override val title: String,
    val author: Artist?,
    val episodeCountText: String? = null,
    override val thumbnail: String?,
    val playEndpoint: WatchEndpoint? = null,
    val shuffleEndpoint: WatchEndpoint? = null,
    val channelId: String? = null,
    override val thumbnailWidth: Int? = null,
    override val thumbnailHeight: Int? = null,
) : YTItem() {
    override val explicit: Boolean
        get() = false
    override val shareLink: String
        get() = "https://music.youtube.com/playlist?list=$id"

    fun asPlaylistItem() = PlaylistItem(
        id = id,
        title = title,
        author = author,
        songCountText = episodeCountText,
        thumbnail = thumbnail,
        playEndpoint = playEndpoint,
        shuffleEndpoint = shuffleEndpoint,
        radioEndpoint = null,
        isEditable = false
    )
}

data class EpisodeItem(
    override val id: String,
    override val title: String,
    val author: Artist?,
    val podcast: Album? = null,
    val duration: Int? = null,
    val publishDateText: String? = null,
    override val thumbnail: String,
    override val explicit: Boolean = false,
    val endpoint: WatchEndpoint? = null,
    override val thumbnailWidth: Int? = null,
    override val thumbnailHeight: Int? = null,
) : YTItem() {
    override val shareLink: String
        get() = "https://music.youtube.com/watch?v=$id"

    fun asSongItem() = SongItem(
        id = id,
        title = title,
        artists = listOfNotNull(author),
        album = podcast,
        duration = duration,
        thumbnail = thumbnail,
        explicit = explicit,
        endpoint = endpoint,
    )
}

fun <T : YTItem> List<T>.filterExplicit(enabled: Boolean = true) =
    if (enabled) {
        filter { !it.explicit }
    } else {
        this
    }

fun <T : YTItem> List<T>.filterVideo(enabled: Boolean = true) =
    if (enabled) {
        filter {
            when (it) {
                is SongItem -> {
                    val musicVideoType =
                        it.endpoint
                            ?.watchEndpointMusicSupportedConfigs
                            ?.watchEndpointMusicConfig
                            ?.musicVideoType
                    val isMusicVideo = musicVideoType == MUSIC_VIDEO_TYPE_OMV || musicVideoType == MUSIC_VIDEO_TYPE_UGC
                    !isMusicVideo
                }
                else -> {
                    true
                }
            }
        }
    } else {
        this
    }