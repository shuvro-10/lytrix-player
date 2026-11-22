package com.musicplayer.lytrix

import com.google.gson.JsonObject
import retrofit2.http.GET
import retrofit2.http.Query

interface JioSaavnApi {
    // 🔥 ম্যাজিক: রুট লিংকের সাথে 'api/' যোগ করা হলো!
    @GET("api/search/songs")
    suspend fun searchSongs(
        @Query("query") query: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 30
    ): JsonObject
}