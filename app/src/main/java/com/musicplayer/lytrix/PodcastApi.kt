package com.musicplayer.lytrix

import com.google.gson.JsonObject
import retrofit2.http.GET
import retrofit2.http.Query

// 🔥 ম্যাজিক: Apple iTunes Search API (100% Free & Unlimited)
interface PodcastApi {
    @GET("search")
    suspend fun searchPodcastsRaw(
        @Query("term") query: String,
        @Query("media") media: String = "podcast",
        @Query("entity") entity: String = "podcastEpisode", // ডিরেক্ট অডিও লিংক আনবে
        @Query("limit") limit: Int = 50
    ): JsonObject
}

// ডেটা মডেল (Apple-এর রেসপন্স অনুযায়ী)
data class PodcastEpisode(
    val id: String,
    val title_original: String,
    val audio: String,
    val image: String,
    val audio_length_sec: Int,
    val podcast: PodcastInfo,
    val channelId: String? = null
)

data class PodcastInfo(
    val publisher_original: String,
    val channelId: String? = null
)