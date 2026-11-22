package com.musicplayer.lytrix.lyrics.ai

import android.content.Context
import android.content.SharedPreferences

object AiConstants {
    const val PREFS_NAME = "ai_lyrics_prefs"

    const val KEY_AI_PROVIDER = "ai_provider"
    const val KEY_API_KEY = "ai_api_key"
    const val KEY_BASE_URL = "ai_base_url"
    const val KEY_MODEL = "ai_model"
    const val KEY_TRANSLATE_LANGUAGE = "ai_translate_language"
    const val KEY_TRANSLATE_MODE = "ai_translate_mode"
    const val KEY_DEEPL_API_KEY = "ai_deepl_api_key"
    const val KEY_DEEPL_FORMALITY = "ai_deepl_formality"
    const val KEY_SYSTEM_PROMPT = "ai_system_prompt"

    const val DEFAULT_BASE_URL = "https://openrouter.ai/api/v1/chat/completions"
    const val DEFAULT_MODEL = "google/gemini-2.5-flash-lite"
    const val DEFAULT_PROVIDER = "OpenRouter"
    const val DEFAULT_LANGUAGE = "bn" // Default to Bengali for Lytrix users, or selectable
    const val DEFAULT_MODE = "Literal"
    const val DEFAULT_DEEPL_FORMALITY = "default"

    const val DEFAULT_AI_SYSTEM_PROMPT = """You are a precise lyrics translation assistant. Your output must ALWAYS be a valid JSON object of the form {"lines": ["line1", "line2", "line3"]}.

CRITICAL RULES:
1. Output ONLY the JSON object: {"lines": ["line1", "line2", "line3"]}
2. NO explanations, NO questions, NO additional text
3. Each input line maps to exactly one entry in the "lines" array
4. Preserve empty lines as empty strings ""
5. The "lines" array must contain EXACTLY {lineCount} items
6. If uncertain, provide best approximation but maintain line count"""

    val aiProviders = mapOf(
        "OpenRouter" to "https://openrouter.ai/api/v1/chat/completions",
        "OpenAI" to "https://api.openai.com/v1/chat/completions",
        "Gemini" to "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
        "Claude" to "https://api.anthropic.com/v1/messages",
        "Perplexity" to "https://api.perplexity.ai/chat/completions",
        "XAi" to "https://api.x.ai/v1/chat/completions",
        "Mistral" to "https://api.mistral.ai/v1/chat/completions",
        "Inception" to "https://api.inceptionlabs.ai/v1/chat/completions",
        "DeepL" to "https://api.deepl.com/v2/translate",
        "Custom" to ""
    )

    val providerHelpText = mapOf(
        "OpenRouter" to "Visit https://openrouter.ai for free and paid models",
        "OpenAI" to "Visit https://platform.openai.com/api-keys",
        "Gemini" to "Visit https://aistudio.google.com/apikey (Free Google API Key)",
        "Claude" to "Visit https://console.anthropic.com/settings/keys",
        "Perplexity" to "Visit https://perplexity.ai/settings/api",
        "XAi" to "Visit https://console.x.ai",
        "Mistral" to "Visit https://console.mistral.ai/api-keys",
        "Inception" to "Visit https://platform.inceptionlabs.ai for API keys",
        "DeepL" to "Visit https://deepl.com/pro-api for free and paid keys",
        "Custom" to "Connect to any OpenAI-compatible server (e.g., Ollama, LM Studio, Groq)"
    )

    val modelsByProvider = mapOf(
        "OpenRouter" to listOf(
            "~google/gemini-flash-latest",
            "~deepseek/deepseek-v4-flash-latest",
            "~openai/gpt-mini-latest",
            "inception/mercury-2.5-preview",
            "meta/muse-spark-1.3",
            "z-ai/glm-5.3-flash",
            "qwen/qwen3.8-flash",
            "openai/gpt-oss-120b"
        ),
        "Gemini" to listOf(
            "gemini-flash-lite-latest",
            "gemini-flash-latest",
            "gemini-pro-latest",
            "gemini-2.5-flash-lite",
            "gemini-2.5-flash",
            "gemini-3.8-flash"
        ),
        "OpenAI" to listOf(
            "gpt-4o-mini",
            "gpt-4o",
            "gpt-5.6-sol",
            "gpt-5.6-terra",
            "gpt-5.6-luna",
            "gpt-5.5-2026-04-23",
            "gpt-5.4-2026-03-05"
        ),
        "Claude" to listOf(
            "claude-haiku-4-5-20251001",
            "claude-sonnet-5",
            "claude-opus-5",
            "claude-fable-5-1"
        ),
        "Perplexity" to listOf(
            "sonar",
            "sonar-pro",
            "sonar-reasoning-pro"
        ),
        "XAi" to listOf(
            "grok-4.1-fast",
            "grok-4.3",
            "grok-4.6"
        ),
        "Mistral" to listOf(
            "mistral-small-latest",
            "mistral-medium-latest",
            "mistral-large-latest",
            "mistral-tiny-latest"
        ),
        "Inception" to listOf(
            "mercury-2"
        ),
        "DeepL" to emptyList(),
        "Custom" to emptyList()
    )

    val LanguageCodeToName = mapOf(
        "bn" to "Bengali",
        "en" to "English",
        "hi" to "Hindi",
        "es" to "Spanish",
        "fr" to "French",
        "de" to "German",
        "it" to "Italian",
        "pt" to "Portuguese",
        "ru" to "Russian",
        "ja" to "Japanese",
        "ko" to "Korean",
        "zh" to "Chinese",
        "ar" to "Arabic",
        "pa" to "Punjabi",
        "tr" to "Turkish",
        "vi" to "Vietnamese",
        "th" to "Thai",
        "id" to "Indonesian",
        "pl" to "Polish",
        "nl" to "Dutch",
        "sv" to "Swedish",
        "uk" to "Ukrainian"
    )

    fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getProvider(context: Context): String =
        getPrefs(context).getString(KEY_AI_PROVIDER, DEFAULT_PROVIDER) ?: DEFAULT_PROVIDER

    fun getApiKey(context: Context): String =
        getPrefs(context).getString(KEY_API_KEY, "") ?: ""

    fun getBaseUrl(context: Context): String {
        val provider = getProvider(context)
        val custom = getPrefs(context).getString(KEY_BASE_URL, "") ?: ""
        return if (provider == "Custom") custom else (aiProviders[provider] ?: DEFAULT_BASE_URL)
    }

    fun getModel(context: Context): String {
        val provider = getProvider(context)
        val savedModel = getPrefs(context).getString(KEY_MODEL, "") ?: ""
        if (savedModel.isNotBlank()) return savedModel
        val defaultList = modelsByProvider[provider]
        return defaultList?.firstOrNull() ?: DEFAULT_MODEL
    }

    fun getTranslateLanguage(context: Context): String =
        getPrefs(context).getString(KEY_TRANSLATE_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE

    fun getTranslateMode(context: Context): String =
        getPrefs(context).getString(KEY_TRANSLATE_MODE, DEFAULT_MODE) ?: DEFAULT_MODE

    fun getDeeplApiKey(context: Context): String =
        getPrefs(context).getString(KEY_DEEPL_API_KEY, "") ?: ""

    fun getDeeplFormality(context: Context): String =
        getPrefs(context).getString(KEY_DEEPL_FORMALITY, DEFAULT_DEEPL_FORMALITY) ?: DEFAULT_DEEPL_FORMALITY

    fun getSystemPrompt(context: Context): String =
        getPrefs(context).getString(KEY_SYSTEM_PROMPT, "") ?: ""
}
