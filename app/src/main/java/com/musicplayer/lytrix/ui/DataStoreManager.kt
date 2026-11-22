package com.musicplayer.lytrix.ui

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// পুরো অ্যাপের জন্য DataStore ইনস্ট্যান্স তৈরি (Singleton)
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "lytrix_settings")

class DataStoreManager(private val context: Context) {

    companion object {
        // App Preferences
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val LOCK_SCREEN_PLAYING = booleanPreferencesKey("lock_screen_playing")

        // Premium Prefs
        val IS_PREMIUM = booleanPreferencesKey("is_premium")
        val SUBSCRIPTION_EXPIRY = longPreferencesKey("subscription_expiry")
        val IS_PREMIUM_PLUS = booleanPreferencesKey("is_premium_plus")
        val AD_FREE_EXPIRY = longPreferencesKey("ad_free_expiry")

        // Rewards Prefs
        val LAST_AD_DATE = stringPreferencesKey("last_ad_date")
        val TASK_TRACKED_DATE = stringPreferencesKey("task_tracked_date")
        val IS_CODE_APPLIED = booleanPreferencesKey("is_code_applied")
        val DAILY_TASK_AUDIO_MS = longPreferencesKey("daily_task_audio_ms")
        val DAILY_TASK_VIDEO_MS = longPreferencesKey("daily_task_video_ms")
        val DAILY_TASK_SONG_DOWNLOAD_COUNT = intPreferencesKey("daily_task_song_download_count")
        val DAILY_TASK_PODCAST_DOWNLOAD_COUNT = intPreferencesKey("daily_task_podcast_download_count")
        val DAILY_TASK_AD_COUNT = intPreferencesKey("daily_task_ad_count")
        val HAS_APPLIED_CODE = booleanPreferencesKey("has_applied_code")

        // 🔥 Playback & Playtime Prefs (নতুন অ্যাড করা হলো)
        val LAST_SONG_ID = stringPreferencesKey("last_song_id")
        val LAST_SONG_POSITION = longPreferencesKey("last_song_position")
        val TOTAL_AUDIO_MS = longPreferencesKey("audio_ms")
        val TOTAL_VIDEO_MS = longPreferencesKey("video_ms")
        val WEEKLY_MS = longPreferencesKey("weekly_ms")
        const val PLAYER_STYLE_MATERIAL3 = "MATERIAL_3"
        const val PLAYER_STYLE_APPLE_MUSIC = "APPLE_MUSIC"
        const val PLAYER_STYLE_CLASSIC = "CLASSIC"
        const val PLAYER_STYLE_M3_EXPRESSIVE = "M3_EXPRESSIVE"
        const val PLAYER_STYLE_CINEMA = "CINEMA"

        val PLAYER_STYLE = stringPreferencesKey("player_style")
        val USE_NEW_PLAYER_DESIGN = booleanPreferencesKey("use_new_player_design")
        val NEXT_SONG_TRANSITION_ANIMATION = booleanPreferencesKey("next_song_transition_animation")
        val PLAYER_GESTURES_ENABLED = booleanPreferencesKey("player_gestures_enabled")
    }

    // ডাটা রিড (Read) করার জন্য Kotlin Flow
    val isPremiumFlow: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[IS_PREMIUM] ?: false }
    val subscriptionExpiryFlow: Flow<Long> = context.dataStore.data.map { prefs -> prefs[SUBSCRIPTION_EXPIRY] ?: 0L }
    val keepScreenOnFlow: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[KEEP_SCREEN_ON] ?: false }
    val lockScreenPlayingFlow: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[LOCK_SCREEN_PLAYING] ?: false }
    val playerStyleFlow: Flow<String> = context.dataStore.data.map { prefs ->
        val saved = prefs[PLAYER_STYLE]
        if (!saved.isNullOrBlank()) {
            if (saved == "SOUNDCLOUD") PLAYER_STYLE_MATERIAL3 else saved
        } else {
            val useNew = prefs[USE_NEW_PLAYER_DESIGN] ?: true
            if (useNew) PLAYER_STYLE_MATERIAL3 else PLAYER_STYLE_CLASSIC
        }
    }
    val useNewPlayerDesignFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        val style = prefs[PLAYER_STYLE]
        if (!style.isNullOrBlank()) {
            style != PLAYER_STYLE_CLASSIC
        } else {
            prefs[USE_NEW_PLAYER_DESIGN] ?: true
        }
    }
    val nextSongTransitionAnimationFlow: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[NEXT_SONG_TRANSITION_ANIMATION] ?: true }
    val playerGesturesEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[PLAYER_GESTURES_ENABLED] ?: true }

    // Rewards Progress Flow
    val dailyAdCountFlow: Flow<Int> = context.dataStore.data.map { prefs -> prefs[DAILY_TASK_AD_COUNT] ?: 0 }
    val dailyAudioMinutesFlow: Flow<Int> = context.dataStore.data.map { prefs -> ((prefs[DAILY_TASK_AUDIO_MS] ?: 0L) / 60000).toInt() }
    val dailyVideoMinutesFlow: Flow<Int> = context.dataStore.data.map { prefs -> ((prefs[DAILY_TASK_VIDEO_MS] ?: 0L) / 60000).toInt() }
    val isCodeAppliedFlow: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[IS_CODE_APPLIED] ?: false }

    // Playtime Flow
    val totalAudioMsFlow: Flow<Long> = context.dataStore.data.map { prefs -> prefs[TOTAL_AUDIO_MS] ?: 0L }
    val totalVideoMsFlow: Flow<Long> = context.dataStore.data.map { prefs -> prefs[TOTAL_VIDEO_MS] ?: 0L }
    val weeklyMsFlow: Flow<Long> = context.dataStore.data.map { prefs -> prefs[WEEKLY_MS] ?: 0L }

    // ডাটা সেভ (Write) করার ফাংশনসমূহ
    suspend fun savePremiumStatus(isPremium: Boolean, expiry: Long) {
        context.dataStore.edit { prefs ->
            prefs[IS_PREMIUM] = isPremium
            prefs[SUBSCRIPTION_EXPIRY] = expiry
        }
    }

    suspend fun setKeepScreenOn(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEEP_SCREEN_ON] = enabled }
    }

    suspend fun setLockScreenPlaying(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[LOCK_SCREEN_PLAYING] = enabled }
    }

    suspend fun setPlayerStyle(style: String) {
        context.dataStore.edit { prefs ->
            prefs[PLAYER_STYLE] = style
            prefs[USE_NEW_PLAYER_DESIGN] = (style != PLAYER_STYLE_CLASSIC)
        }
    }

    suspend fun setUseNewPlayerDesign(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[USE_NEW_PLAYER_DESIGN] = enabled
            prefs[PLAYER_STYLE] = if (enabled) PLAYER_STYLE_MATERIAL3 else PLAYER_STYLE_CLASSIC
        }
    }

    suspend fun setNextSongTransitionAnimation(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[NEXT_SONG_TRANSITION_ANIMATION] = enabled }
    }

    suspend fun setPlayerGesturesEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[PLAYER_GESTURES_ENABLED] = enabled }
    }

    suspend fun resetDailyTasks(currentDate: String) {
        context.dataStore.edit { prefs ->
            prefs[TASK_TRACKED_DATE] = currentDate
            prefs[DAILY_TASK_AUDIO_MS] = 0L
            prefs[DAILY_TASK_VIDEO_MS] = 0L
            prefs[DAILY_TASK_SONG_DOWNLOAD_COUNT] = 0
            prefs[DAILY_TASK_PODCAST_DOWNLOAD_COUNT] = 0
            prefs[DAILY_TASK_AD_COUNT] = 0
        }
    }

    suspend fun incrementAdCount() {
        context.dataStore.edit { prefs ->
            val current = prefs[DAILY_TASK_AD_COUNT] ?: 0
            prefs[DAILY_TASK_AD_COUNT] = current + 1
        }
    }

    // Playback Update
    suspend fun updatePlaytime(isAudio: Boolean, addMs: Long) {
        context.dataStore.edit { prefs ->
            if (isAudio) {
                val curAudio = prefs[TOTAL_AUDIO_MS] ?: 0L
                prefs[TOTAL_AUDIO_MS] = curAudio + addMs
            } else {
                val curVideo = prefs[TOTAL_VIDEO_MS] ?: 0L
                prefs[TOTAL_VIDEO_MS] = curVideo + addMs
            }
            val curWeekly = prefs[WEEKLY_MS] ?: 0L
            prefs[WEEKLY_MS] = curWeekly + addMs
        }
    }
}