package com.musicplayer.lytrix.spotify.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SpotifyInternalToken(
    @SerialName("accessToken") val accessToken: String = "",
    @SerialName("accessTokenExpirationTimestampMs") val accessTokenExpirationTimestampMs: Long = 0L,
    @SerialName("isAnonymous") val isAnonymous: Boolean = false,
    @SerialName("clientId") val clientId: String = "",
)

@Serializable
data class SpotifyToken(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String = "Bearer",
    @SerialName("scope") val scope: String = "",
    @SerialName("expires_in") val expiresIn: Int = 3600,
    @SerialName("refresh_token") val refreshToken: String? = null,
)
