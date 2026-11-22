package com.musicplayer.lytrix.spotify

import android.content.Context
import android.util.Log
import com.musicplayer.lytrix.spotify.models.SpotifyInternalToken
import com.musicplayer.lytrix.spotify.models.SpotifyUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.floor

object SpotifyAuth {
    private const val TOKEN_URL = "https://open.spotify.com/api/token"
    private const val SERVER_TIME_URL = "https://open.spotify.com/api/server-time"
    private const val USER_PROFILE_URL = "https://api.spotify.com/v1/me"
    private const val NUANCE_GIST_URL = "https://api.github.com/gists/22ed9c6ba463899e933427f7de1f0eef"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    const val LOGIN_URL = "https://accounts.spotify.com/login?continue=https%3A%2F%2Fopen.spotify.com%2F"

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    @Serializable
    private data class Nuance(val s: String, val v: Int)

    @Serializable
    private data class GistFile(val content: String)

    @Serializable
    private data class GistFiles(val files: Map<String, GistFile>)

    @Serializable
    private data class ServerTimeResponse(val serverTime: Long)

    // Fallback nuance if GitHub Gist is unreachable
    private val FALLBACK_NUANCE = Nuance(
        s = "GM3TMMJTGYZTQNZVGM4DINJZHA4TGOBYGMZTCMRTGEYDSMJRHE4TEOBUG4YTCMRUGQ4DQOJUGQYTAMRRGA2TCMJSHE3TCMBY",
        v = 61
    )

    /**
     * Fetches an internal web-player access token using session cookies and TOTP.
     */
    suspend fun fetchAccessToken(
        spDc: String,
        spKey: String = "",
    ): Result<SpotifyInternalToken> = runCatching {
        val nuance = fetchNuance()
        val serverTimeSec = fetchServerTime()
        val totp = generateTotp(nuance.s, serverTimeSec)

        val cookieHeader = buildString {
            append("sp_dc=$spDc")
            if (spKey.isNotEmpty()) {
                append("; sp_key=$spKey")
            }
        }

        // Try with reason=transport first
        var token = requestToken(totp, nuance.v, "transport", cookieHeader)

        // If returned empty or anonymous, retry with reason=init
        if (token == null || token.accessToken.isBlank() || token.isAnonymous) {
            token = requestToken(totp, nuance.v, "init", cookieHeader)
        }

        if (token == null || token.accessToken.isBlank() || token.isAnonymous) {
            throw Exception("Received anonymous or invalid token — sp_dc cookie is invalid or expired")
        }

        token
    }

    private suspend fun requestToken(
        totp: String,
        totpVer: Int,
        reason: String,
        cookieHeader: String
    ): SpotifyInternalToken? = withContext(Dispatchers.IO) {
        try {
            val tokenUrl = buildString {
                append(TOKEN_URL)
                append("?reason=$reason")
                append("&productType=mobile-web-player")
                append("&totp=$totp")
                append("&totpServer=$totp")
                append("&totpVer=$totpVer")
            }

            val extraHeaders = mapOf(
                "Cookie" to cookieHeader,
                "Origin" to "https://open.spotify.com",
                "Referer" to "https://open.spotify.com/"
            )
            Log.i("SpotifyCanvas", "SpotifyAuth: Requesting token (reason=$reason, productType=mobile-web-player)")
            val body = httpGet(tokenUrl, extraHeaders)
            val parsed = json.decodeFromString<SpotifyInternalToken>(body)
            Log.i("SpotifyCanvas", "SpotifyAuth: Token received successfully (len=${parsed.accessToken.length}, isAnonymous=${parsed.isAnonymous})")
            parsed
        } catch (e: Exception) {
            Timber.w(e, "SpotifyAuth: requestToken with reason=$reason failed: ${e.message}")
            Log.w("SpotifyCanvas", "SpotifyAuth: requestToken with reason=$reason failed: ${e.message}")
            null
        }
    }

    /**
     * Fetches the current user profile from Spotify Web API.
     */
    suspend fun fetchUserProfile(accessToken: String): Result<SpotifyUser> = runCatching {
        withContext(Dispatchers.IO) {
            val headers = mapOf("Authorization" to "Bearer $accessToken")
            val body = httpGet(USER_PROFILE_URL, headers)
            json.decodeFromString<SpotifyUser>(body)
        }
    }

    /**
     * Ensures an active, non-expired access token. Refreshes if needed.
     */
    suspend fun ensureValidToken(context: Context, forceRefresh: Boolean = false): String? {
        val prefs = SpotifyPreferencesManager(context)
        val token = prefs.accessToken.first()
        val expiry = prefs.tokenExpiry.first()
        val spDc = prefs.spDc.first()

        if (spDc.isBlank()) return null

        val now = System.currentTimeMillis()
        // If forceRefresh is false and token has at least 60 seconds of validity remaining, use it
        if (!forceRefresh && token.isNotBlank() && expiry > now + 60_000) {
            return token
        }

        // Refresh token
        return try {
            val result = fetchAccessToken(spDc)
            val newToken = result.getOrThrow()
            prefs.updateAccessToken(newToken.accessToken, newToken.accessTokenExpirationTimestampMs)
            newToken.accessToken
        } catch (e: Exception) {
            Timber.e(e, "SpotifyAuth: failed to auto-refresh token")
            token.ifBlank { null }
        }
    }

    private suspend fun fetchNuance(): Nuance = withContext(Dispatchers.IO) {
        try {
            val body = httpGet(NUANCE_GIST_URL, emptyMap())
            val gist = json.decodeFromString<GistFiles>(body)
            val nuancesJson = gist.files.values.firstOrNull()?.content
                ?: return@withContext FALLBACK_NUANCE
            val nuances = json.decodeFromString<List<Nuance>>(nuancesJson)
            nuances.maxByOrNull { it.v } ?: FALLBACK_NUANCE
        } catch (e: Exception) {
            Timber.w(e, "SpotifyAuth: Failed to fetch nuance from gist, using fallback: ${e.message}")
            FALLBACK_NUANCE
        }
    }

    private suspend fun fetchServerTime(): Long = withContext(Dispatchers.IO) {
        try {
            val body = httpGet(SERVER_TIME_URL, emptyMap())
            val response = json.decodeFromString<ServerTimeResponse>(body)
            response.serverTime
        } catch (e: Exception) {
            Timber.w(e, "SpotifyAuth: Failed to fetch server time, using local: ${e.message}")
            System.currentTimeMillis() / 1000L
        }
    }

    /**
     * Generates a 6-digit TOTP using HMAC-SHA1 (RFC 6238).
     */
    private fun generateTotp(secret: String, serverTimeSec: Long): String {
        val key = base32Decode(secret)
        val interval = 30L
        val timeStep = floor(serverTimeSec.toDouble() / interval).toLong()

        val timeBytes = ByteArray(8)
        var value = timeStep
        for (i in 7 downTo 0) {
            timeBytes[i] = (value and 0xFF).toByte()
            value = value shr 8
        }

        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "HmacSHA1"))
        val hash = mac.doFinal(timeBytes)

        val offset = hash[hash.size - 1].toInt() and 0x0F
        val code = ((hash[offset].toInt() and 0x7F) shl 24) or
            ((hash[offset + 1].toInt() and 0xFF) shl 16) or
            ((hash[offset + 2].toInt() and 0xFF) shl 8) or
            (hash[offset + 3].toInt() and 0xFF)

        val otp = code % 1_000_000
        return otp.toString().padStart(6, '0')
    }

    private fun base32Decode(input: String): ByteArray {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val cleaned = input.uppercase().replace("=", "")

        val output = mutableListOf<Byte>()
        var buffer = 0
        var bitsLeft = 0

        for (c in cleaned) {
            val value = alphabet.indexOf(c)
            if (value < 0) continue
            buffer = (buffer shl 5) or value
            bitsLeft += 5
            if (bitsLeft >= 8) {
                bitsLeft -= 8
                output.add(((buffer shr bitsLeft) and 0xFF).toByte())
            }
        }

        return output.toByteArray()
    }

    private fun httpGet(urlString: String, extraHeaders: Map<String, String>): String {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "application/json, text/plain, */*")
            connection.setRequestProperty("Accept-Language", "en")
            for ((key, value) in extraHeaders) {
                connection.setRequestProperty(key, value)
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                throw Exception("HTTP $responseCode: $errorBody")
            }

            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
