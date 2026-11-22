package com.musicplayer.lytrix.utils

import com.google.gson.JsonArray
import retrofit2.http.GET
import retrofit2.http.Query

interface RadioApi {
    @GET("json/stations/search")
    suspend fun searchRadioStations(
        @Query("country") country: String? = null,
        @Query("tag") tag: String? = null,
        @Query("limit") limit: Int = 30,
        @Query("order") order: String = "clickcount",
        @Query("reverse") reverse: Boolean = true
    ): JsonArray
}