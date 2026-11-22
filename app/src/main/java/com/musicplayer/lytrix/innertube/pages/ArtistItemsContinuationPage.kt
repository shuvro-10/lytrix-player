/*
 * Lytrix Player (2026)
 * Licensed under GNU General Public License v3.0 (GPL-3.0)
 */

package com.musicplayer.lytrix.innertube.pages

import com.musicplayer.lytrix.innertube.models.YTItem

data class ArtistItemsContinuationPage(
    val items: List<YTItem>,
    val continuation: String?,
)
