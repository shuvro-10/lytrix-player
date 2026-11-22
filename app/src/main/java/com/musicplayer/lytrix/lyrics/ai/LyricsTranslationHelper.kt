package com.musicplayer.lytrix.lyrics.ai

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

sealed class TranslationStatus {
    object Idle : TranslationStatus()
    object Translating : TranslationStatus()
    object Success : TranslationStatus()
    data class Error(val message: String) : TranslationStatus()
}

object LyricsTranslationHelper {
    private val _status = MutableStateFlow<TranslationStatus>(TranslationStatus.Idle)
    val status: StateFlow<TranslationStatus> = _status.asStateFlow()

    private val _hasActiveTranslations = MutableStateFlow(false)
    val hasActiveTranslations: StateFlow<Boolean> = _hasActiveTranslations.asStateFlow()

    private val translationCache = ConcurrentHashMap<String, List<String>>()
    private var translationJob: Job? = null

    private fun getCacheKey(songTitle: String, songArtist: String, lang: String, mode: String): String {
        return "${songTitle.trim().lowercase()}_${songArtist.trim().lowercase()}_${lang}_${mode}"
    }

    fun getCachedTranslations(
        songTitle: String,
        songArtist: String,
        context: Context,
        targetLang: String = AiConstants.getTranslateLanguage(context),
        mode: String = AiConstants.getTranslateMode(context)
    ): List<String>? {
        val cacheKey = getCacheKey(songTitle, songArtist, targetLang, mode)
        val inMem = translationCache[cacheKey]
        if (inMem != null && inMem.isNotEmpty()) {
            _hasActiveTranslations.value = true
            return inMem
        }

        // Check sharedPreferences
        val prefs = context.getSharedPreferences("lyrics_translation_cache", Context.MODE_PRIVATE)
        val savedLang = prefs.getString("${cacheKey}_lang", null)
        val savedMode = prefs.getString("${cacheKey}_mode", null)

        if (savedLang == targetLang && savedMode == mode) {
            val raw = prefs.getString("${cacheKey}_data", null)
            if (!raw.isNullOrBlank()) {
                val lines = raw.split("\n")
                translationCache[cacheKey] = lines
                _hasActiveTranslations.value = true
                return lines
            }
        }

        _hasActiveTranslations.value = false
        return null
    }

    fun saveTranslations(
        songTitle: String,
        songArtist: String,
        translations: List<String>,
        context: Context,
        targetLang: String = AiConstants.getTranslateLanguage(context),
        mode: String = AiConstants.getTranslateMode(context)
    ) {
        val cacheKey = getCacheKey(songTitle, songArtist, targetLang, mode)
        translationCache[cacheKey] = translations

        val prefs = context.getSharedPreferences("lyrics_translation_cache", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("${cacheKey}_data", translations.joinToString("\n"))
            .putString("${cacheKey}_lang", targetLang)
            .putString("${cacheKey}_mode", mode)
            .apply()

        _hasActiveTranslations.value = true
    }

    fun clearTranslations(
        songTitle: String,
        songArtist: String,
        context: Context
    ) {
        val targetLang = AiConstants.getTranslateLanguage(context)
        val mode = AiConstants.getTranslateMode(context)
        val cacheKey = getCacheKey(songTitle, songArtist, targetLang, mode)

        translationCache.remove(cacheKey)
        val prefs = context.getSharedPreferences("lyrics_translation_cache", Context.MODE_PRIVATE)
        prefs.edit()
            .remove("${cacheKey}_data")
            .remove("${cacheKey}_lang")
            .remove("${cacheKey}_mode")
            .apply()

        _hasActiveTranslations.value = false
        _status.value = TranslationStatus.Idle
    }

    fun cancelTranslation() {
        translationJob?.cancel()
        if (_status.value is TranslationStatus.Translating) {
            _status.value = TranslationStatus.Idle
        }
    }

    fun translateLines(
        lines: List<String>,
        songTitle: String,
        songArtist: String,
        context: Context,
        scope: CoroutineScope,
        onSuccess: (List<String>) -> Unit,
        onError: (String) -> Unit
    ) {
        translationJob?.cancel()

        if (lines.isEmpty() || lines.all { it.isBlank() }) {
            _status.value = TranslationStatus.Error("No lyrics to translate")
            onError("No lyrics to translate")
            return
        }

        val provider = AiConstants.getProvider(context)
        val targetLanguage = AiConstants.getTranslateLanguage(context)
        val mode = AiConstants.getTranslateMode(context)
        val fullLangName = AiConstants.LanguageCodeToName[targetLanguage] ?: targetLanguage

        // Check cache first
        val cached = getCachedTranslations(songTitle, songArtist, context, targetLanguage, mode)
        if (cached != null && cached.size >= lines.size) {
            _status.value = TranslationStatus.Success
            onSuccess(cached)
            return
        }

        val apiKey = if (provider == "DeepL") AiConstants.getDeeplApiKey(context) else AiConstants.getApiKey(context)
        if (apiKey.isBlank()) {
            val err = "API key is required. Please set it in Settings -> YouTube & Online Settings -> AI lyrics translation"
            _status.value = TranslationStatus.Error(err)
            onError(err)
            return
        }

        _status.value = TranslationStatus.Translating

        translationJob = scope.launch(Dispatchers.IO) {
            try {
                val fullText = lines.joinToString("\n")

                val result: Result<List<String>> = if (provider == "DeepL") {
                    DeepLService.translate(
                        text = fullText,
                        targetLanguage = targetLanguage,
                        apiKey = apiKey,
                        formality = AiConstants.getDeeplFormality(context)
                    )
                } else {
                    OpenRouterService.translate(
                        text = fullText,
                        targetLanguage = fullLangName,
                        apiKey = apiKey,
                        baseUrl = AiConstants.getBaseUrl(context),
                        model = AiConstants.getModel(context),
                        mode = mode,
                        customSystemPrompt = AiConstants.getSystemPrompt(context)
                    )
                }

                withContext(Dispatchers.Main) {
                    result.fold(
                        onSuccess = { translatedLines ->
                            saveTranslations(songTitle, songArtist, translatedLines, context, targetLanguage, mode)
                            _status.value = TranslationStatus.Success
                            onSuccess(translatedLines)
                        },
                        onFailure = { ex ->
                            val msg = ex.message ?: "Translation failed"
                            _status.value = TranslationStatus.Error(msg)
                            onError(msg)
                        }
                    )
                }
            } catch (e: CancellationException) {
                withContext(Dispatchers.Main) {
                    _status.value = TranslationStatus.Idle
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val msg = e.message ?: "Unknown error during translation"
                    _status.value = TranslationStatus.Error(msg)
                    onError(msg)
                }
            }
        }
    }
}
