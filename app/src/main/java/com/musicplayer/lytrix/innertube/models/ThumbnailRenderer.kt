/*
 * Lytrix Player (2026)
 * Licensed under GNU General Public License v3.0 (GPL-3.0)
 */

package com.musicplayer.lytrix.innertube.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ThumbnailRenderer(
    @JsonNames("croppedSquareThumbnailRenderer")
    val musicThumbnailRenderer: MusicThumbnailRenderer? = null,
    val musicAnimatedThumbnailRenderer: MusicAnimatedThumbnailRenderer? = null,
    val croppedSquareThumbnailRenderer: MusicThumbnailRenderer? = null,
) {
    fun getThumbnailUrl(): String? =
        musicThumbnailRenderer?.getThumbnailUrl()
            ?: musicAnimatedThumbnailRenderer?.backupRenderer?.getThumbnailUrl()
            ?: croppedSquareThumbnailRenderer?.getThumbnailUrl()

    @Serializable
    data class MusicThumbnailRenderer(
        val thumbnail: Thumbnails,
        val thumbnailCrop: String? = null,
        val thumbnailScale: String? = null,
    ) {
        fun getBestThumbnail(): Thumbnail? {
            val thumbnails = thumbnail.thumbnails
            return thumbnails
                .filter { candidate ->
                    candidate.normalizedUrl.isNotBlank() &&
                            (candidate.width ?: 0) > 0 &&
                            (candidate.height ?: 0) > 0
                }.maxByOrNull { candidate ->
                    val width = candidate.width ?: 0
                    val height = candidate.height ?: 0
                    width.toLong() * height.toLong()
                } ?: thumbnails.lastOrNull { it.normalizedUrl.isNotBlank() }
        }

        fun getThumbnailUrl(): String? {
            val best = getBestThumbnail()
            val raw = best?.normalizedUrl ?: thumbnail.thumbnails.lastOrNull()?.normalizedUrl ?: return null
            val url = if (raw.startsWith("//")) "https:$raw" else raw
            return when {
                // Do not corrupt signed query parameters (podcast artworks, studio square thumbs)
                url.contains("?sqp=") || url.contains("&rs=") -> url
                url.contains("googleusercontent.com") || url.contains("ggpht.com") -> {
                    url
                }
                url.contains("ytimg.com") && url.contains("/vi/") -> {
                    url.substringBefore("?")
                        .replace("sddefault.jpg", "maxresdefault.jpg")
                        .replace("hqdefault.jpg", "maxresdefault.jpg")
                        .replace("default.jpg", "maxresdefault.jpg")
                }
                else -> url
            }
        }
    }

    @Serializable
    data class MusicAnimatedThumbnailRenderer(
        val animatedThumbnail: Thumbnails,
        val backupRenderer: MusicThumbnailRenderer,
    )
}