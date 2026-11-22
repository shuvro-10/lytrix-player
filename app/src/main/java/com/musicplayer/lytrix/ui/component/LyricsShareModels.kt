package com.musicplayer.lytrix.ui.component

import androidx.annotation.StringRes
import com.musicplayer.lytrix.R

enum class LyricsBackgroundStyle {
    SOLID, BLUR, GRADIENT
}

enum class LyricsShareAspectRatio(
    @StringRes val labelRes: Int,
    val exportWidth: Int,
    val exportHeight: Int,
) {
    Square(
        labelRes = R.string.layout_square,
        exportWidth = 1080,
        exportHeight = 1080,
    ),
    Story(
        labelRes = R.string.layout_story,
        exportWidth = 1080,
        exportHeight = 1920,
    ),
    Portrait(
        labelRes = R.string.layout_portrait,
        exportWidth = 1080,
        exportHeight = 1350,
    );

    val previewAspectRatio: Float
        get() = exportWidth.toFloat() / exportHeight.toFloat()
}

data class SharedLyricLine(
    val original: String,
    val translation: String? = null
)
