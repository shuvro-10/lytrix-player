package com.musicplayer.lytrix.lyrics.youtube

import com.musicplayer.lytrix.innertube.YouTube
import com.musicplayer.lytrix.innertube.models.WatchEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object YouTubeLyrics {
    suspend fun getLyrics(videoId: String): Result<String?> = withContext(Dispatchers.IO) {
        runCatching {
            if (videoId.isBlank()) return@runCatching null

            // 1. First try timed captions/transcript from YouTube (returns LRC formatted timestamps)
            val transcript = YouTube.transcript(videoId).getOrNull()?.takeIf { it.isNotBlank() }
            if (transcript != null) {
                return@runCatching transcript
            }

            // 2. Second, try YouTube Music official lyrics endpoint
            val nextResult = YouTube.next(WatchEndpoint(videoId = videoId)).getOrNull()
            val lyricsEndpoint = nextResult?.lyricsEndpoint ?: return@runCatching null
            YouTube.lyrics(lyricsEndpoint).getOrNull()
        }
    }
}
