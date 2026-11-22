package com.musicplayer.lytrix.spotify.sync

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL

/**
 * Synchronizes Spotify GraphQL hashes from remote GitHub Pages registry
 * to ensure persistent query hashes remain up-to-date automatically.
 */
object SpotifyHashSync {
    private const val REMOTE_URL = "https://francescograzioso.github.io/Meld/spotify-gql-hashes.json"
    private const val PREFS_NAME = "spotify_gql_hashes"
    private const val CACHED_JSON_KEY = "cached_json"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun sync(context: Context) = withContext(Dispatchers.IO) {
        try {
            // First load from cached SharedPreferences
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val cachedJson = prefs.getString(CACHED_JSON_KEY, null)
            if (!cachedJson.isNullOrBlank()) {
                parseAndApply(cachedJson, SpotifyHashProvider.HashSource.CACHED)
            }

            // Then fetch remote
            val connection = URL(REMOTE_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.requestMethod = "GET"
            if (connection.responseCode in 200..299) {
                val remoteJson = connection.inputStream.bufferedReader().use { it.readText() }
                if (remoteJson.isNotBlank()) {
                    parseAndApply(remoteJson, SpotifyHashProvider.HashSource.REMOTE)
                    prefs.edit().putString(CACHED_JSON_KEY, remoteJson).apply()
                    Timber.d("SpotifyHashSync: successfully updated remote hashes")
                }
            }
            connection.disconnect()
        } catch (e: Exception) {
            Timber.w(e, "SpotifyHashSync: sync failed, using hardcoded/cached hashes: ${e.message}")
        }
    }

    private fun parseAndApply(jsonString: String, source: SpotifyHashProvider.HashSource) {
        try {
            val root = json.parseToJsonElement(jsonString).jsonObject
            val ops = root["operations"]?.jsonObject ?: return
            val parsed = mutableMapOf<String, SpotifyHashProvider.GqlHashEntry>()
            ops.forEach { (opName, elem) ->
                val opObj = elem as? JsonObject ?: return@forEach
                val hash = opObj["hash"]?.jsonPrimitive?.content ?: return@forEach
                val prev = opObj["previous_hash"]?.jsonPrimitive?.content
                parsed[opName] = SpotifyHashProvider.GqlHashEntry(
                    hash = hash,
                    previousHash = prev,
                    source = source
                )
            }
            if (parsed.isNotEmpty()) {
                SpotifyHashProvider.updateHashes(parsed, source)
            }
        } catch (e: Exception) {
            Timber.w(e, "SpotifyHashSync: parse error")
        }
    }
}
