/*
 * Lytrix Player (2026)
 * Licensed under GNU General Public License v3.0 (GPL-3.0)
 */

package com.musicplayer.lytrix.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class ResponseContext(
    val visitorData: String?,
    val serviceTrackingParams: List<ServiceTrackingParam>?,
) {
    @Serializable
    data class ServiceTrackingParam(
        val params: List<Param>,
        val service: String,
    ) {
        @Serializable
        data class Param(
            val key: String,
            val value: String,
        )
    }
}
