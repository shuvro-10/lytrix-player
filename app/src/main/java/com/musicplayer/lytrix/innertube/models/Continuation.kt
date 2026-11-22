/*
 * Lytrix Player (2026)
 * Licensed under GNU General Public License v3.0 (GPL-3.0)
 */

package com.musicplayer.lytrix.innertube.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Continuation(
    @JsonNames("nextContinuationData", "nextRadioContinuationData")
    val nextContinuationData: NextContinuationData?,
    val reloadContinuationData: NextContinuationData? = null,
) {
    @Serializable
    data class NextContinuationData(
        val continuation: String,
    )
}

fun List<Continuation>.getContinuation() = firstOrNull()?.nextContinuationData?.continuation
