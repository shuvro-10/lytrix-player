package com.musicplayer.lytrix.eq.data

import kotlinx.serialization.Serializable

@Serializable
enum class FilterType {
    /** Peaking filter - boosts or cuts around a center frequency */
    PK,
    /** Low-shelf filter - affects frequencies below the cutoff */
    LSC,
    /** High-shelf filter - affects frequencies above the cutoff */
    HSC,
    /** Low-pass filter - attenuates frequencies above the cutoff */
    LPQ,
    /** High-pass filter - attenuates frequencies below the cutoff */
    HPQ
}

@Serializable
enum class EqMode {
    GRAPHIC,
    PROFILE
}

@Serializable
data class ParametricEQBand(
    val frequency: Double,
    val gain: Double,
    val q: Double = 1.41,
    val filterType: FilterType = FilterType.PK,
    val enabled: Boolean = true
)

@Serializable
data class ParametricEQ(
    val preamp: Double,
    val bands: List<ParametricEQBand>,
    val metadata: Map<String, String> = emptyMap()
) {
    companion object {
        const val MAX_BANDS = 20
        val GRAPHIC_BAND_FREQUENCIES = listOf(
            31.25, 62.5, 125.0, 250.0, 500.0,
            1000.0, 2000.0, 4000.0, 8000.0, 16000.0
        )
    }
}

@Serializable
data class SavedEQProfile(
    val id: String,
    val name: String,
    val deviceModel: String,
    val bands: List<ParametricEQBand>,
    val preamp: Double = 0.0,
    val source: String = "unknown",
    val rig: String = "unknown",
    val isCustom: Boolean = true,
    val addedTimestamp: Long = System.currentTimeMillis()
)

@Serializable
data class AutoEqEntry(
    val label: String,
    val form: String,
    val rig: String,
    val source: String,
    val formDirectory: String
)
