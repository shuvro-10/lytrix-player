package com.musicplayer.lytrix.service.audio

enum class LoudnessLevel(
    val targetLufs: Float,
    val displayName: String,
    val description: String
) {
    AGGRESSIVE(-7f, "Aggressive", "-7 LUFS (Loudest)"),
    LOUD(-11f, "Loud", "-11 LUFS"),
    BALANCED(-14f, "Balanced", "-14 LUFS (Recommended)"),
    QUIET(-19f, "Quiet", "-19 LUFS");

    companion object {
        fun fromStored(name: String?): LoudnessLevel {
            return entries.find { it.name.equals(name, ignoreCase = true) } ?: BALANCED
        }
    }
}

/**
 * Computes gain in millibels (mB) from YouTube loudness metadata and target LUFS.
 * YouTube loudnessDb is reported relative to -7 LUFS unless perceptualLoudnessDb is provided directly.
 */
fun normalizationGainMb(
    loudnessDb: Double?,
    perceptualLoudnessDb: Double?,
    targetLufs: Float
): Int? {
    val measuredLufs = perceptualLoudnessDb
        ?: loudnessDb?.let { it + LoudnessLevel.AGGRESSIVE.targetLufs }
    return measuredLufs
        ?.let { (-(it - targetLufs) * 100.0).toInt() }
        ?.coerceIn(-1500, 600)
}

/**
 * Computes gain in millibels (mB) from a local ReplayGain tag (referenced to -18 LUFS)
 * and the selected [targetLufs].
 */
fun replayGainToGainMb(replayGainDb: Double, targetLufs: Float): Int {
    val measuredLufs = -18.0 - replayGainDb
    return ((targetLufs - measuredLufs) * 100.0).toInt().coerceIn(-1500, 600)
}

/**
 * Computes gain in millibels (mB) from an Opus/Ogg R128_TRACK_GAIN tag
 * (stored in 1/256 dB units referenced to -23 LUFS) and [targetLufs].
 */
fun r128TrackGainToGainMb(r128Raw: Int, targetLufs: Float): Int {
    val measuredLufs = -23.0 - (r128Raw / 256.0)
    return ((targetLufs - measuredLufs) * 100.0).toInt().coerceIn(-1500, 600)
}
