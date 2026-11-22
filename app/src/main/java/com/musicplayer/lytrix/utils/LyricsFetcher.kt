package com.musicplayer.lytrix.utils

import android.content.Context
import android.util.Log // 🔥 লগ দেখার জন্য ইমপোর্ট করা হলো
import com.musicplayer.lytrix.model.Song
import com.musicplayer.lytrix.lyrics.betterlyrics.BetterLyrics
import com.musicplayer.lytrix.lyrics.kugou.KuGou
import com.musicplayer.lytrix.lyrics.lrclib.LrcLib
import com.musicplayer.lytrix.lyrics.paxsenix.PaxsenixLyrics
import com.musicplayer.lytrix.lyrics.simpmusic.SimpMusicLyrics
import com.musicplayer.lytrix.lyrics.unison.Unison
import com.musicplayer.lytrix.lyrics.youlyplus.YouLyPlus
import com.musicplayer.lytrix.lyrics.youtube.YouTubeLyrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlinx.coroutines.async
object LyricsFetcher {

    private val lrcCache = java.util.concurrent.ConcurrentHashMap<String, Set<String>>()

    fun clearLrcCache() {
        lrcCache.clear()
    }

    // SongScanner-এর জন্য সুপার-ফাস্ট ক্যাশড লিরিক্স চেকার (ব্যাজ ১০০% কাজ করবে)
    fun hasLyricsFast(song: Song): Boolean {
        try {
            val path = song.filePath
            if (path != null && !path.startsWith("ONLINE") && !path.startsWith("http")) {
                val lastSlash = path.lastIndexOf('/')
                if (lastSlash != -1) {
                    val parent = path.substring(0, lastSlash)
                    val fileName = path.substring(lastSlash + 1)
                    val dot = fileName.lastIndexOf('.')
                    val nameWithoutExt = if (dot != -1) fileName.substring(0, dot) else fileName
                    val expectedLrc = (nameWithoutExt + ".lrc").lowercase()

                    val set = lrcCache.getOrPut(parent) {
                        try {
                            val dir = File(parent)
                            dir.list { _, name -> name.endsWith(".lrc", ignoreCase = true) }
                                ?.map { it.lowercase() }
                                ?.toSet() ?: emptySet()
                        } catch (e: Exception) {
                            emptySet()
                        }
                    }
                    return set.contains(expectedLrc)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }
    suspend fun readLocalLrcFile(song: Song): String? {
        return withContext(Dispatchers.IO) {
            try {
                val path = song.filePath
                if (path != null && !path.startsWith("ONLINE") && !path.startsWith("OFFLINE") && !path.startsWith("http")) {
                    val file = File(path)
                    val lrcFile = File(file.parent, file.nameWithoutExtension + ".lrc")
                    if (lrcFile.exists()) {
                        return@withContext lrcFile.readText()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            null
        }
    }
    val PROVIDER_NAMES = listOf(
        "YouLyPlus",
        "BetterLyrics",
        "SimpMusic",
        "YouTube Music",
        "KuGou",
        "LrcLib"
    )

    fun getProviderNames(context: Context): List<String> {
        val list = mutableListOf<String>()
        if (com.musicplayer.lytrix.spotify.SpotifyPreferencesManager.isLoggedIn(context) &&
            com.musicplayer.lytrix.spotify.SpotifyPreferencesManager.isLyricsEnabled(context)) {
            list.add("Spotify")
        }
        list.addAll(PROVIDER_NAMES)
        return list
    }

    data class LyricsResult(
        val lyrics: String,
        val providerName: String,
        val providerIndex: Int
    )

    // আমাদের নতুন Dynamic Premium API Engine (Spotify Synced + 6-Layer Fallback)
    suspend fun fetchLyricsWithResult(context: Context, song: Song, providerIndex: Int = -1): LyricsResult? {
        return withContext(Dispatchers.IO) {
            val title = song.title.trim()
            val artist = song.artist.trim()
            val album = song.album.trim().takeIf { it.isNotBlank() }
            val durationSec = (song.duration / 1000).toInt()
            val videoId = song.originalId ?: song.uriString.substringAfter("v=").substringBefore("&")

            if (title.isBlank()) return@withContext null

            val providers = getProviderNames(context)

            suspend fun fetchFromIndex(index: Int): String? {
                try {
                    val providerName = providers.getOrNull(index) ?: return null
                    val result = when (providerName) {
                        "Spotify" -> com.musicplayer.lytrix.spotify.lyrics.SpotifyLyrics.getLyrics(context, song)
                        "YouLyPlus" -> YouLyPlus.getLyrics(title = title, artist = artist, durationSeconds = durationSec).getOrNull()
                        "BetterLyrics" -> BetterLyrics.getLyrics(title = title, artist = artist, durationSeconds = durationSec).getOrNull()
                        "SimpMusic" -> if (videoId.isNotBlank() && videoId.length == 11) SimpMusicLyrics.getLyrics(videoId, durationSec).getOrNull() else null
                        "YouTube Music" -> if (videoId.isNotBlank()) YouTubeLyrics.getLyrics(videoId).getOrNull() else null
                        "KuGou" -> KuGou.getLyrics(title = title, artist = artist, duration = durationSec).getOrNull()
                        "LrcLib" -> LrcLib.getLyrics(title = title, artist = artist, duration = if (durationSec > 0) durationSec else -1).getOrNull()
                        else -> null
                    }
                    if (!result.isNullOrBlank()) {
                        result.chunked(800).forEachIndexed { i, chunk ->
                            Log.d("LYRICS_DEBUG", "[$index-$i] $chunk")
                        }
                    }
                    return result
                } catch (e: Exception) {
                    e.printStackTrace()
                    return null
                }
            }

            // যদি ViewModel থেকে স্পেসিফিক ইনডেক্স দেয় (Change বাটনে ক্লিক করলে)
            if (providerIndex in 0 until providers.size) {
                val r = fetchFromIndex(providerIndex)
                return@withContext if (!r.isNullOrBlank()) {
                    LyricsResult(r, providers[providerIndex], providerIndex)
                } else null
            } else {
                // 🔥 সব provider সমান্তরালে (parallel) শুরু হবে — priority অনুযায়ী প্রথম যেটা সফল হয় সেটাই নেওয়া হবে
                val deferreds = (0 until providers.size).map { i -> async { Pair(i, fetchFromIndex(i)) } }
                var finalResult: LyricsResult? = null
                for (d in deferreds) {
                    val (idx, r) = d.await()
                    if (!r.isNullOrBlank()) {
                        finalResult = LyricsResult(r, providers[idx], idx)
                        break
                    }
                }
                deferreds.forEach { it.cancel() }
                return@withContext finalResult
            }
        }
    }

    suspend fun fetchLyrics(context: Context, song: Song, providerIndex: Int = -1): String? {
        return fetchLyricsWithResult(context, song, providerIndex)?.lyrics
    }
}