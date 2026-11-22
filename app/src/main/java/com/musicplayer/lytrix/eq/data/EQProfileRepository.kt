package com.musicplayer.lytrix.eq.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber

class EQProfileRepository private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    private val _profiles = MutableStateFlow<List<SavedEQProfile>>(emptyList())
    val profiles: StateFlow<List<SavedEQProfile>> = _profiles.asStateFlow()

    private val _activeProfile = MutableStateFlow<SavedEQProfile?>(null)
    val activeProfile: StateFlow<SavedEQProfile?> = _activeProfile.asStateFlow()

    private val _isEqEnabled = MutableStateFlow(prefs.getBoolean(KEY_EQ_ENABLED, false))
    val isEqEnabled: StateFlow<Boolean> = _isEqEnabled.asStateFlow()

    private val _eqMode = MutableStateFlow(loadEqMode())
    val eqMode: StateFlow<EqMode> = _eqMode.asStateFlow()

    private val _graphicBandValues = MutableStateFlow(loadGraphicBands())
    val graphicBandValues: StateFlow<List<Float>> = _graphicBandValues.asStateFlow()

    private val _selectedPreset = MutableStateFlow(prefs.getString(KEY_SELECTED_PRESET, "Custom") ?: "Custom")
    val selectedPreset: StateFlow<String> = _selectedPreset.asStateFlow()

    init {
        loadProfiles()
    }

    private fun loadEqMode(): EqMode {
        val raw = prefs.getString(KEY_EQ_MODE, EqMode.GRAPHIC.name)
        return EqMode.entries.find { it.name == raw } ?: EqMode.GRAPHIC
    }

    private fun loadGraphicBands(): List<Float> {
        val raw = prefs.getString(KEY_GRAPHIC_BANDS, null) ?: return List(10) { 0f }
        val parsed = raw.split(",").mapNotNull { it.trim().toFloatOrNull()?.coerceIn(-15f, 15f) }
        return if (parsed.size == 10) parsed else List(10) { 0f }
    }

    private fun loadProfiles() {
        try {
            val profilesJson = prefs.getString(KEY_PROFILES, null)
            if (!profilesJson.isNullOrBlank()) {
                val loaded = json.decodeFromString<List<SavedEQProfile>>(profilesJson)
                _profiles.value = loaded
                val activeId = prefs.getString(KEY_ACTIVE_PROFILE_ID, null)
                _activeProfile.value = loaded.find { it.id == activeId }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error loading EQ profiles")
            _profiles.value = emptyList()
            _activeProfile.value = null
        }
    }

    @Synchronized
    fun setEqEnabled(enabled: Boolean) {
        _isEqEnabled.value = enabled
        prefs.edit().putBoolean(KEY_EQ_ENABLED, enabled).apply()
    }

    @Synchronized
    fun setEqMode(mode: EqMode) {
        _eqMode.value = mode
        prefs.edit().putString(KEY_EQ_MODE, mode.name).apply()
    }

    @Synchronized
    fun saveGraphicBands(bands: List<Float>, presetName: String) {
        val safeBands = List(10) { idx -> (bands.getOrNull(idx) ?: 0f).coerceIn(-15f, 15f) }
        _graphicBandValues.value = safeBands
        _selectedPreset.value = presetName
        _eqMode.value = EqMode.GRAPHIC
        prefs.edit()
            .putString(KEY_GRAPHIC_BANDS, safeBands.joinToString(","))
            .putString(KEY_SELECTED_PRESET, presetName)
            .putString(KEY_EQ_MODE, EqMode.GRAPHIC.name)
            .apply()
    }

    suspend fun saveProfile(profile: SavedEQProfile) = withContext(Dispatchers.IO) {
        saveProfiles(listOf(profile))
    }

    suspend fun saveProfiles(newProfiles: List<SavedEQProfile>) = withContext(Dispatchers.IO) {
        val updated = _profiles.value.toMutableList()
        val activeId = _activeProfile.value?.id

        newProfiles.forEach { incoming ->
            val idx = updated.indexOfFirst { it.id == incoming.id }
            if (idx >= 0) updated[idx] = incoming else updated.add(incoming)
            if (incoming.id == activeId) _activeProfile.value = incoming
        }

        val sorted = updated.sortedByDescending { it.addedTimestamp }
        prefs.edit().putString(KEY_PROFILES, json.encodeToString(sorted)).apply()
        _profiles.value = sorted
    }

    suspend fun deleteProfile(profileId: String) = withContext(Dispatchers.IO) {
        val updated = _profiles.value.filterNot { it.id == profileId }
        val editor = prefs.edit().putString(KEY_PROFILES, json.encodeToString(updated))
        if (_activeProfile.value?.id == profileId) {
            _activeProfile.value = null
            editor.remove(KEY_ACTIVE_PROFILE_ID)
            if (_eqMode.value == EqMode.PROFILE) {
                _eqMode.value = EqMode.GRAPHIC
                editor.putString(KEY_EQ_MODE, EqMode.GRAPHIC.name)
            }
        }
        editor.apply()
        _profiles.value = updated
    }

    @Synchronized
    fun setActiveProfileSync(profileId: String?) {
        if (profileId == null) {
            _activeProfile.value = null
            _eqMode.value = EqMode.GRAPHIC
            prefs.edit()
                .remove(KEY_ACTIVE_PROFILE_ID)
                .putString(KEY_EQ_MODE, EqMode.GRAPHIC.name)
                .apply()
        } else {
            val profile = _profiles.value.find { it.id == profileId }
            _activeProfile.value = profile
            if (profile != null) {
                _eqMode.value = EqMode.PROFILE
                prefs.edit()
                    .putString(KEY_ACTIVE_PROFILE_ID, profileId)
                    .putString(KEY_EQ_MODE, EqMode.PROFILE.name)
                    .apply()
            }
        }
    }

    @Synchronized
    fun updateActiveProfileBand(bandIndex: Int, gainDb: Double) {
        val current = _activeProfile.value ?: return
        if (bandIndex !in current.bands.indices) return
        val updatedBands = current.bands.toMutableList()
        updatedBands[bandIndex] = updatedBands[bandIndex].copy(
            gain = gainDb.coerceIn(-15.0, 15.0)
        )
        val updatedProfile = current.copy(bands = updatedBands)
        persistUpdatedActiveProfile(updatedProfile)
    }

    @Synchronized
    fun updateActiveProfilePreamp(preampDb: Double) {
        val current = _activeProfile.value ?: return
        val updatedProfile = current.copy(preamp = preampDb.coerceIn(-15.0, 15.0))
        persistUpdatedActiveProfile(updatedProfile)
    }

    private fun persistUpdatedActiveProfile(updatedProfile: SavedEQProfile) {
        _activeProfile.value = updatedProfile
        val updatedList = _profiles.value.map {
            if (it.id == updatedProfile.id) updatedProfile else it
        }
        _profiles.value = updatedList
        runCatching {
            prefs.edit().putString(KEY_PROFILES, json.encodeToString(updatedList)).apply()
        }
    }

    suspend fun importCustomProfile(name: String, parametricEQ: ParametricEQ): SavedEQProfile =
        withContext(Dispatchers.IO) {
            val id = "custom_${System.currentTimeMillis()}_${name.hashCode()}"
            val profile = SavedEQProfile(
                id = id,
                name = name,
                deviceModel = name,
                bands = parametricEQ.bands,
                preamp = parametricEQ.preamp,
                source = "Imported APO",
                rig = "Custom",
                isCustom = true
            )
            saveProfile(profile)
            profile
        }

    fun buildGraphicParametricEQ(bands: List<Float> = _graphicBandValues.value): ParametricEQ {
        val eqBands = ParametricEQ.GRAPHIC_BAND_FREQUENCIES.mapIndexed { idx, freq ->
            ParametricEQBand(
                frequency = freq,
                gain = (bands.getOrNull(idx) ?: 0f).toDouble(),
                q = 1.41,
                filterType = FilterType.PK,
                enabled = true
            )
        }
        return ParametricEQ(preamp = 0.0, bands = eqBands)
    }

    fun resolveActiveParametricEQ(): ParametricEQ? {
        if (!_isEqEnabled.value) return null
        return when (_eqMode.value) {
            EqMode.PROFILE -> {
                val p = _activeProfile.value
                if (p != null) ParametricEQ(preamp = p.preamp, bands = p.bands)
                else buildGraphicParametricEQ()
            }
            EqMode.GRAPHIC -> buildGraphicParametricEQ()
        }
    }

    companion object {
        private const val TAG = "EQProfileRepository"
        private const val PREFS_NAME = "nanosonic_eq_profiles"
        private const val KEY_PROFILES = "eq_profiles"
        private const val KEY_ACTIVE_PROFILE_ID = "active_profile_id"
        private const val KEY_EQ_ENABLED = "eq_enabled"
        private const val KEY_EQ_MODE = "eq_mode"
        private const val KEY_GRAPHIC_BANDS = "graphic_bands"
        private const val KEY_SELECTED_PRESET = "selected_preset"

        @Volatile
        private var INSTANCE: EQProfileRepository? = null

        fun getInstance(context: Context): EQProfileRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: EQProfileRepository(context).also { INSTANCE = it }
            }
        }
    }
}
