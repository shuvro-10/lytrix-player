package com.musicplayer.lytrix.aod

import android.content.Context
import android.content.SharedPreferences

/**
 * Shared preferences contract for Always-On Display (AOD), Pocket Touch Lock, and the DreamService screensaver.
 */
object AodPreferences {
    const val ACTION_AOD_MODE = "com.musicplayer.lytrix.action.AOD_MODE"

    private const val PREFS_NAME = "app_preferences"
    private const val KEY_AOD_DREAM_ENABLED = "aod_dream_enabled"
    private const val KEY_AOD_PIXEL_SHIFT = "aod_pixel_shift"
    private const val KEY_AOD_SHOW_LYRICS = "aod_show_lyrics"
    private const val KEY_AOD_AUTO_LOCK = "aod_auto_lock"
    private const val KEY_AOD_PROXIMITY_LOCK = "aod_proximity_lock"
    private const val KEY_AOD_UNLOCK_METHOD = "aod_unlock_method"
    private const val KEY_AOD_AMBIENT_GLOW = "aod_ambient_glow"
    private const val KEY_AOD_CLOCK_STYLE = "aod_clock_style"

    const val UNLOCK_SLIDE = "SLIDE"
    const val UNLOCK_HOLD = "HOLD"

    const val CLOCK_PIXEL_STACKED = "PIXEL_STACKED"
    const val CLOCK_BOLD_DIGITAL = "BOLD_DIGITAL"
    const val CLOCK_MINIMAL = "MINIMAL"
    const val CLOCK_ELEGANT_THIN = "ELEGANT_THIN"

    data class Config(
        val dreamEnabled: Boolean = true,
        val pixelShiftEnabled: Boolean = true,
        val showLyricsTicker: Boolean = true,
        val autoLockEnabled: Boolean = false,
        val proximityPocketLock: Boolean = true,
        val unlockMethod: String = UNLOCK_SLIDE,
        val ambientGlowEnabled: Boolean = true,
        val clockStyle: String = CLOCK_PIXEL_STACKED,
    )

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(context: Context): Config {
        val sp = prefs(context)
        return Config(
            dreamEnabled = sp.getBoolean(KEY_AOD_DREAM_ENABLED, true),
            pixelShiftEnabled = sp.getBoolean(KEY_AOD_PIXEL_SHIFT, true),
            showLyricsTicker = sp.getBoolean(KEY_AOD_SHOW_LYRICS, true),
            autoLockEnabled = sp.getBoolean(KEY_AOD_AUTO_LOCK, false),
            proximityPocketLock = sp.getBoolean(KEY_AOD_PROXIMITY_LOCK, true),
            unlockMethod = sp.getString(KEY_AOD_UNLOCK_METHOD, UNLOCK_SLIDE) ?: UNLOCK_SLIDE,
            ambientGlowEnabled = sp.getBoolean(KEY_AOD_AMBIENT_GLOW, true),
            clockStyle = sp.getString(KEY_AOD_CLOCK_STYLE, CLOCK_PIXEL_STACKED) ?: CLOCK_PIXEL_STACKED,
        )
    }

    fun setClockStyle(context: Context, style: String) {
        prefs(context).edit().putString(KEY_AOD_CLOCK_STYLE, style).apply()
    }

    fun setDreamEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AOD_DREAM_ENABLED, enabled).apply()
    }

    fun setPixelShiftEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AOD_PIXEL_SHIFT, enabled).apply()
    }

    fun setShowLyricsTicker(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AOD_SHOW_LYRICS, enabled).apply()
    }

    fun setAutoLockEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AOD_AUTO_LOCK, enabled).apply()
    }

    fun setProximityPocketLock(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AOD_PROXIMITY_LOCK, enabled).apply()
    }

    fun setUnlockMethod(context: Context, method: String) {
        val clean = if (method == UNLOCK_HOLD) UNLOCK_HOLD else UNLOCK_SLIDE
        prefs(context).edit().putString(KEY_AOD_UNLOCK_METHOD, clean).apply()
    }

    fun setAmbientGlowEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AOD_AMBIENT_GLOW, enabled).apply()
    }
}

/**
 * Lightweight bridge allowing player screens and Settings to open the Always-On Display overlay
 * (unlocked AOD or immediately armed Pocket Touch Lock) hosted in MainActivity.
 */
object AodLaunchTrigger {
    @Volatile
    var onRequestAod: ((initialLocked: Boolean) -> Unit)? = null

    fun launch(initialLocked: Boolean = false) {
        onRequestAod?.invoke(initialLocked)
    }
}
