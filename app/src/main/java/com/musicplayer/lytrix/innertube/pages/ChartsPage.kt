/*
 * Lytrix Player (2026)
 * Licensed under GNU General Public License v3.0 (GPL-3.0)
 */

package com.musicplayer.lytrix.innertube.pages

import com.musicplayer.lytrix.innertube.models.*

data class ChartsPage(
    val sections: List<ChartSection>,
    val continuation: String?,
) {
    data class ChartSection(
        val title: String,
        val items: List<YTItem>,
        val chartType: ChartType,
    )

    enum class ChartType {
        TRENDING,
        TOP,
        GENRE,
        NEW_RELEASES,
    }
}
