package com.musicplayer.lytrix

import org.junit.Assert.assertEquals
import org.junit.Test
import com.musicplayer.lytrix.innertube.CounterpartPositionMap
import com.musicplayer.lytrix.innertube.CounterpartSegment

class HomeTest {
    @Test
    fun testCounterpartMapping() {
        // Beat It intro offset: 21,607 ms
        val introOffsetMs = 21607L
        val audioDurationMs = 258000L
        val posMap = CounterpartPositionMap(
            listOf(CounterpartSegment(0L, introOffsetMs, audioDurationMs))
        )

        // 1. Audio 50s -> Video 71.607s
        val audioPos = 50000L
        val videoPos = posMap.mapPosition(audioPos)
        assertEquals(71607L, videoPos)

        // 2. Video 71.607s -> Audio 50s (Reversed)
        val reversedMap = posMap.reversed()
        val mappedBackAudioPos = reversedMap.mapPosition(videoPos)
        assertEquals(50000L, mappedBackAudioPos)

        // 3. Video before intro (10s) -> Audio 0s
        val introVideoPos = 10000L
        val mappedIntroAudio = reversedMap.mapPosition(introVideoPos)
        assertEquals(0L, mappedIntroAudio)
    }

    @Test
    fun testChandSifarishMatcherResolution() {
        kotlinx.coroutines.runBlocking {
            val officialQuery = com.musicplayer.lytrix.innertube.MusicVideoMatcher.buildVideoSearchQuery(
                title = "Chand Sifarish",
                artistNames = listOf("Shaan", "Kailash Kher"),
                includeOfficial = true
            )
            assertEquals("chand sifarish Shaan Kailash Kher official video", officialQuery)

            val searchResult = com.musicplayer.lytrix.innertube.YouTube.search(
                officialQuery,
                com.musicplayer.lytrix.innertube.YouTube.SearchFilter.FILTER_VIDEO
            ).getOrNull()

            val candidates = searchResult?.items?.filterIsInstance<com.musicplayer.lytrix.innertube.models.SongItem>().orEmpty()
            val bestMatch = com.musicplayer.lytrix.innertube.MusicVideoMatcher.findBestMatch(
                trackTitle = "Chand Sifarish",
                trackArtistNames = listOf("Shaan", "Kailash Kher"),
                trackDurationSec = 275L, // 4:35 audio
                candidates = candidates
            )

            println("Best match ID: ${bestMatch?.id}, title: ${bestMatch?.title}, duration: ${bestMatch?.duration}")
            org.junit.Assert.assertNotNull(bestMatch)
            // Must NOT be the live concert show (147s, Lz2Xw7NSm8I)
            org.junit.Assert.assertNotEquals("Lz2Xw7NSm8I", bestMatch?.id)
            // Must be the official music video with duration ~277s
            org.junit.Assert.assertEquals("zWEOx7TSM6I", bestMatch?.id)
        }
    }
}



