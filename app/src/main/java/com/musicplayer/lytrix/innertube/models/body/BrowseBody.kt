/*
 * Lytrix Player (2026)
 * Licensed under GNU General Public License v3.0 (GPL-3.0)
 */

package com.musicplayer.lytrix.innertube.models.body

import kotlinx.serialization.Serializable
import com.musicplayer.lytrix.innertube.models.Context

@Serializable
data class BrowseBody(
    val context: Context,
    val browseId: String?,
    val params: String?,
    val continuation: String?,
)
