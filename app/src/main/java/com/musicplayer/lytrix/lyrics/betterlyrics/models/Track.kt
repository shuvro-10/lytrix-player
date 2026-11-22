/*
 * Lytrix Player (2026)
 * Licensed under GNU General Public License v3.0 (GPL-3.0)
 */
package com.musicplayer.lytrix.lyrics.betterlyrics.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class TTMLResponse(
    @JsonNames("ttml", "lyrics")
    val ttml: String = "",
    @SerialName("provider")
    val provider: String? = null,
    @SerialName("score")
    val score: Double? = null,
)

@Serializable
data class SearchResponse(
    val results: List<Track>,
)

@Serializable
data class Track(
    val title: String,
    val artist: String,
    val album: String? = null,
    val duration: Double,
    val lyrics: Lyrics? = null,
)

@Serializable
data class Lyrics(
    val lines: List<Line>,
)

@Serializable
data class Line(
    val text: String,
    val startTime: Double,
    val words: List<Word>? = null,
)

@Serializable
data class Word(
    val text: String,
    val startTime: Double,
    val endTime: Double,
)
