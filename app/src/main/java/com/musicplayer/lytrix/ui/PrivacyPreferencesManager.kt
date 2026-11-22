package com.musicplayer.lytrix.ui

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.privacyDataStore: DataStore<Preferences> by preferencesDataStore(name = "privacy_preferences")

class PrivacyPreferencesManager(private val context: Context) {

    companion object {
        val PAUSE_LISTEN_HISTORY_KEY = booleanPreferencesKey("pause_listen_history")
        val PAUSE_LOCAL_LISTEN_HISTORY_KEY = booleanPreferencesKey("pause_local_listen_history")
        val PAUSE_SEARCH_HISTORY_KEY = booleanPreferencesKey("pause_search_history")
        val DISABLE_SCREENSHOT_KEY = booleanPreferencesKey("disable_screenshot")
        val CRASH_REPORTING_ENABLED_KEY = booleanPreferencesKey("crash_reporting_enabled")

        private const val PREFS_NAME = "privacy_quick_prefs"
        private const val PREF_PAUSE_LISTEN = "quick_pause_listen"
        private const val PREF_PAUSE_LOCAL_LISTEN = "quick_pause_local_listen"
        private const val PREF_PAUSE_SEARCH = "quick_pause_search"
        private const val PREF_DISABLE_SCREENSHOT = "quick_disable_screenshot"
        private const val PREF_CRASH_REPORTING = "quick_crash_reporting"

        fun isPauseListenHistory(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(PREF_PAUSE_LISTEN, false)
        }

        fun isPauseLocalListenHistory(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(PREF_PAUSE_LOCAL_LISTEN, false)
        }

        fun isPauseSearchHistory(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(PREF_PAUSE_SEARCH, false)
        }

        fun isDisableScreenshot(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(PREF_DISABLE_SCREENSHOT, false)
        }

        fun isCrashReportingEnabled(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(PREF_CRASH_REPORTING, true)
        }
    }

    val pauseListenHistory: Flow<Boolean> = context.privacyDataStore.data.map {
        it[PAUSE_LISTEN_HISTORY_KEY] ?: false
    }

    val pauseLocalListenHistory: Flow<Boolean> = context.privacyDataStore.data.map {
        it[PAUSE_LOCAL_LISTEN_HISTORY_KEY] ?: false
    }

    val pauseSearchHistory: Flow<Boolean> = context.privacyDataStore.data.map {
        it[PAUSE_SEARCH_HISTORY_KEY] ?: false
    }

    val disableScreenshot: Flow<Boolean> = context.privacyDataStore.data.map {
        it[DISABLE_SCREENSHOT_KEY] ?: false
    }

    val crashReportingEnabled: Flow<Boolean> = context.privacyDataStore.data.map {
        it[CRASH_REPORTING_ENABLED_KEY] ?: true
    }

    suspend fun setPauseListenHistory(enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(PREF_PAUSE_LISTEN, enabled).apply()
        context.privacyDataStore.edit { it[PAUSE_LISTEN_HISTORY_KEY] = enabled }
    }

    suspend fun setPauseLocalListenHistory(enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(PREF_PAUSE_LOCAL_LISTEN, enabled).apply()
        context.privacyDataStore.edit { it[PAUSE_LOCAL_LISTEN_HISTORY_KEY] = enabled }
    }

    suspend fun setPauseSearchHistory(enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(PREF_PAUSE_SEARCH, enabled).apply()
        context.privacyDataStore.edit { it[PAUSE_SEARCH_HISTORY_KEY] = enabled }
    }

    suspend fun setDisableScreenshot(enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(PREF_DISABLE_SCREENSHOT, enabled).apply()
        context.privacyDataStore.edit { it[DISABLE_SCREENSHOT_KEY] = enabled }
    }

    suspend fun setCrashReportingEnabled(enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(PREF_CRASH_REPORTING, enabled).apply()
        context.privacyDataStore.edit { it[CRASH_REPORTING_ENABLED_KEY] = enabled }
    }
}
