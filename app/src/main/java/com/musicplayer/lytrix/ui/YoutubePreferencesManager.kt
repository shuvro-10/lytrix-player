package com.musicplayer.lytrix.ui

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.youtubeDataStore: DataStore<Preferences> by preferencesDataStore(name = "youtube_preferences")

class YoutubePreferencesManager(private val context: Context) {

    companion object {
        val COOKIE_KEY = stringPreferencesKey("cookie")
        val VISITOR_DATA_KEY = stringPreferencesKey("visitorData")
        val DATA_SYNC_ID_KEY = stringPreferencesKey("dataSyncId")
        val AUTH_USER_KEY = stringPreferencesKey("authUser")
        val ACCOUNT_NAME_KEY = stringPreferencesKey("accountName")
        val ACCOUNT_EMAIL_KEY = stringPreferencesKey("accountEmail")
        val ACCOUNT_CHANNEL_HANDLE_KEY = stringPreferencesKey("accountChannelHandle")
        val USE_LOGIN_FOR_BROWSE_KEY = booleanPreferencesKey("useLoginForBrowse")
        val YTM_SYNC_KEY = booleanPreferencesKey("ytmSync")
    }

    suspend fun saveAuthData(
        cookie: String,
        visitorData: String,
        dataSyncId: String,
        authUser: String,
        accountName: String,
        accountEmail: String,
        accountChannelHandle: String
    ) {
        context.youtubeDataStore.edit { preferences ->
            preferences[COOKIE_KEY] = cookie
            preferences[VISITOR_DATA_KEY] = visitorData
            preferences[DATA_SYNC_ID_KEY] = dataSyncId
            preferences[AUTH_USER_KEY] = authUser
            preferences[ACCOUNT_NAME_KEY] = accountName
            preferences[ACCOUNT_EMAIL_KEY] = accountEmail
            preferences[ACCOUNT_CHANNEL_HANDLE_KEY] = accountChannelHandle
        }
    }

    suspend fun clearAuthData() {
        context.youtubeDataStore.edit { preferences ->
            preferences.clear()
        }
    }

    val cookie: Flow<String> = context.youtubeDataStore.data.map { it[COOKIE_KEY] ?: "" }
    val visitorData: Flow<String> = context.youtubeDataStore.data.map { it[VISITOR_DATA_KEY] ?: "" }
    val dataSyncId: Flow<String> = context.youtubeDataStore.data.map { it[DATA_SYNC_ID_KEY] ?: "" }
    val authUser: Flow<String> = context.youtubeDataStore.data.map { it[AUTH_USER_KEY] ?: "0" }
    val accountName: Flow<String> = context.youtubeDataStore.data.map { it[ACCOUNT_NAME_KEY] ?: "" }
    val accountEmail: Flow<String> = context.youtubeDataStore.data.map { it[ACCOUNT_EMAIL_KEY] ?: "" }
    val accountChannelHandle: Flow<String> = context.youtubeDataStore.data.map { it[ACCOUNT_CHANNEL_HANDLE_KEY] ?: "" }
    val useLoginForBrowse: Flow<Boolean> = context.youtubeDataStore.data.map { it[USE_LOGIN_FOR_BROWSE_KEY] ?: true }
    val ytmSync: Flow<Boolean> = context.youtubeDataStore.data.map { it[YTM_SYNC_KEY] ?: true }

    suspend fun setUseLoginForBrowse(enabled: Boolean) {
        context.youtubeDataStore.edit { preferences ->
            preferences[USE_LOGIN_FOR_BROWSE_KEY] = enabled
        }
    }

    suspend fun setYtmSync(enabled: Boolean) {
        context.youtubeDataStore.edit { preferences ->
            preferences[YTM_SYNC_KEY] = enabled
        }
    }
}
