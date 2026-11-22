package com.musicplayer.lytrix.db

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "theme_preferences")

class ThemePreferencesManager(context: Context) {
    private val appContext = context.applicationContext
    
    companion object {
        val APP_THEME_MODE = stringPreferencesKey("app_theme_mode")
        val ACCENT_COLOR = stringPreferencesKey("accent_color")
        val GRID_VIEW_SIZE = intPreferencesKey("grid_view_size")
        val CUSTOM_BG_URI = stringPreferencesKey("custom_bg_uri")
        val PLAYER_THEME = stringPreferencesKey("player_theme")
        val LANGUAGE_CODE = stringPreferencesKey("language_code")
        val PURE_BLACK = booleanPreferencesKey("pure_black")
        val DYNAMIC_THEME = booleanPreferencesKey("dynamic_theme")
    }

    val appThemeMode: Flow<String> = appContext.dataStore.data.map { preferences ->
        preferences[APP_THEME_MODE] ?: "Dark"
    }
    
    val playerTheme: Flow<String> = appContext.dataStore.data.map { preferences ->
        preferences[PLAYER_THEME] ?: "Solid Default"
    }

    val languageCode: Flow<String> = appContext.dataStore.data.map { preferences ->
        preferences[LANGUAGE_CODE] ?: "en"
    }

    val accentColor: Flow<String> = appContext.dataStore.data.map { preferences ->
        preferences[ACCENT_COLOR] ?: "0xFF3182CE" // Default Sky Blue
    }

    val gridViewSize: Flow<Int> = appContext.dataStore.data.map { preferences ->
        preferences[GRID_VIEW_SIZE] ?: 1 // 1 for List (Small), 2 for Medium (Grid 2), 3 for Big (Grid 3)
    }

    val customBackgroundUri: Flow<String> = appContext.dataStore.data.map { preferences ->
        preferences[CUSTOM_BG_URI] ?: ""
    }

    val isPureBlack: Flow<Boolean> = appContext.dataStore.data.map { preferences ->
        preferences[PURE_BLACK] ?: false
    }

    val isDynamicTheme: Flow<Boolean> = appContext.dataStore.data.map { preferences ->
        preferences[DYNAMIC_THEME] ?: false
    }

    suspend fun saveAppThemeMode(mode: String) {
        appContext.dataStore.edit { preferences ->
            preferences[APP_THEME_MODE] = mode
        }
    }
    
    suspend fun savePlayerTheme(theme: String) {
        appContext.dataStore.edit { preferences ->
            preferences[PLAYER_THEME] = theme
        }
    }

    suspend fun saveLanguageCode(code: String) {
        appContext.dataStore.edit { preferences ->
            preferences[LANGUAGE_CODE] = code
        }
    }

    suspend fun saveAccentColor(colorHex: String) {
        appContext.dataStore.edit { preferences ->
            preferences[ACCENT_COLOR] = colorHex
        }
    }

    suspend fun saveGridViewSize(size: Int) {
        appContext.dataStore.edit { preferences ->
            preferences[GRID_VIEW_SIZE] = size
        }
    }

    suspend fun saveCustomBackgroundUri(uri: String) {
        appContext.dataStore.edit { preferences ->
            preferences[CUSTOM_BG_URI] = uri
        }
    }

    suspend fun savePureBlack(enabled: Boolean) {
        appContext.dataStore.edit { preferences ->
            preferences[PURE_BLACK] = enabled
        }
    }

    suspend fun saveDynamicTheme(enabled: Boolean) {
        appContext.dataStore.edit { preferences ->
            preferences[DYNAMIC_THEME] = enabled
        }
    }
}
