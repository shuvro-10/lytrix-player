/*
 * Lytrix Player (2026)
 * Licensed under GNU General Public License v3.0 (GPL-3.0)
 */

package com.musicplayer.lytrix.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class Thumbnails(
    val thumbnails: List<Thumbnail>,
)

@Serializable
data class Thumbnail(
    val url: String,
    val width: Int?,
    val height: Int?,
) {
    // 🔥 এই একটা ম্যাজিক ফাংশন পুরো অ্যাপের সব ছবিকে 1080p HD করে দেবে!
    val normalizedUrl: String get() {
        val base = if (url.startsWith("//")) "https:$url" else url
        if (base.contains("?sqp=") || base.contains("&rs=")) return base
        if (base.contains("googleusercontent.com") || base.contains("ggpht.com")) {
            return if (base.contains("=w") && base.contains("-h")) {
                base.replace(Regex("=w\\d+-h\\d+"), "=w1080-h1080")
            } else if (base.contains("=s") && !base.contains("?")) {
                base.replace(Regex("=s\\d+"), "=s1080")
            } else {
                base
            }
        }
        return if (base.contains("=w") && base.contains("-h")) {
            base.replace(Regex("=w\\d+-h\\d+.*"), "=w1080-h1080-l90-rj")
        } else if (base.contains("=s") && !base.contains("?")) {
            base.replace(Regex("=s\\d+.*"), "=s1080-l90-rj")
        } else {
            base
        }
    }
}