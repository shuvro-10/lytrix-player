package com.musicplayer.lytrix.utils

import coil.intercept.Interceptor
import coil.request.ErrorResult
import coil.request.ImageResult

/**
 * MetroList-equivalent video artwork fallback interceptor.
 * Whenever YouTube returns HTTP 404 on high-resolution thumbnails (e.g. maxresdefault.jpg
 * for videos that only have 360p/480p uploads), this interceptor catches the failure
 * and automatically retries with hqdefault.jpg and mqdefault.jpg.
 *
 * This ensures:
 * 1. Tracks with HD thumbnails remain 100% full HD with ZERO quality loss.
 * 2. Tracks without maxresdefault (e.g. U2's "Can't Stop Us Now") cleanly load hqdefault
 *    instead of displaying a blank/empty grey box.
 */
class VideoArtworkFallbackInterceptor : Interceptor {
    private val youtubePathRegex =
        Regex("(/vi(?:_webp)?/[^/?#]+/)(mqdefault|hqdefault|sddefault|hq720|maxresdefault)(\\.(?:jpg|webp))(?=[?#]|$)")

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val result = chain.proceed(chain.request)
        if (result !is ErrorResult) return result

        val url = chain.request.data as? String ?: return result
        val fallback = getFallbackUrl(url) ?: return result

        val fallbackRequest = chain.request.newBuilder()
            .data(fallback)
            .memoryCacheKey(fallback)
            .diskCacheKey(fallback)
            .build()

        val fallbackResult = chain.proceed(fallbackRequest)
        if (fallbackResult !is ErrorResult) return fallbackResult

        // If hqdefault also failed (rare), fall back to mqdefault (which YouTube creates unconditionally)
        val secondaryFallback = getSecondaryFallbackUrl(url) ?: return fallbackResult
        val secondaryRequest = chain.request.newBuilder()
            .data(secondaryFallback)
            .memoryCacheKey(secondaryFallback)
            .diskCacheKey(secondaryFallback)
            .build()

        return chain.proceed(secondaryRequest)
    }

    private fun getFallbackUrl(url: String): String? {
        val host = url.substringAfter("://", "").substringBefore('/').substringBefore(':').lowercase()
        if (host != "i.ytimg.com" && !host.endsWith(".ytimg.com")) return null
        val path = youtubePathRegex.find(url.substringBefore('#').substringBefore('?')) ?: return null
        if (path.groupValues[2] != "maxresdefault") return null
        return url.replaceRange(path.range, "${path.groupValues[1]}hqdefault${path.groupValues[3]}")
    }

    private fun getSecondaryFallbackUrl(url: String): String? {
        val host = url.substringAfter("://", "").substringBefore('/').substringBefore(':').lowercase()
        if (host != "i.ytimg.com" && !host.endsWith(".ytimg.com")) return null
        val path = youtubePathRegex.find(url.substringBefore('#').substringBefore('?')) ?: return null
        if (path.groupValues[2] == "mqdefault") return null
        return url.replaceRange(path.range, "${path.groupValues[1]}mqdefault${path.groupValues[3]}")
    }
}
