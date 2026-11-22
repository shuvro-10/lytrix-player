package com.musicplayer.lytrix

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
import okhttp3.ResponseBody

// শুধু এই একটাই মডেল থাকবে
data class LytrixSongItem(
    val videoId: String?,
    val title: String?,
    val artist: String?,
    val thumbnailUrl: String?,
    val duration: String?
)

interface LytrixApi {
    @GET("search")
    suspend fun searchMusic(
        @Query("q") query: String
    ): Response<ResponseBody> // 🔥 Gson বাদ, সরাসরি টেক্সট আনব

    @GET("stream")
    suspend fun getStreamUrl(
        @Query("video_id") videoId: String
    ): Response<ResponseBody> // 🔥 Gson বাদ
}