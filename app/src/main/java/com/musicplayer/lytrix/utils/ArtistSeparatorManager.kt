package com.musicplayer.lytrix.utils

import android.content.Context
import com.musicplayer.lytrix.ui.cleanArtistDisplayName
import org.json.JSONArray

/**
 * Lytrix-inspired configurable artist separator parser stored in `app_preferences`.
 */
object ArtistSeparatorManager {
    private const val PREF_NAME = "app_preferences"
    private const val KEY_SEPARATORS_JSON = "artist_separators_json"

    val DEFAULT_SEPARATORS: List<String> = listOf(",", "&", "/", "•", "|", "feat.", "ft.")

    fun loadSeparators(context: Context?): List<String> {
        if (context == null) return DEFAULT_SEPARATORS
        return runCatching {
            val raw = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString(KEY_SEPARATORS_JSON, null) ?: return DEFAULT_SEPARATORS
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val token = arr.optString(i)
                    if (token.isNotBlank() && !contains(token)) add(token)
                }
            }
        }.getOrDefault(DEFAULT_SEPARATORS)
    }

    fun saveSeparators(context: Context, separators: List<String>) {
        runCatching {
            val normalized = separators.filter { it.isNotBlank() }.distinct()
            val arr = JSONArray(normalized)
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_SEPARATORS_JSON, arr.toString())
                .apply()
        }
    }

    fun resetDefaults(context: Context): List<String> {
        saveSeparators(context, DEFAULT_SEPARATORS)
        return DEFAULT_SEPARATORS
    }

    /**
     * Cleans stats metadata first and splits collaborator names according to user-configured
     * separators (safely respecting word boundaries for alphabetical delimiters such as `feat.`).
     */
    fun splitArtists(context: Context?, rawArtist: String): List<String> {
        val cleaned = cleanArtistDisplayName(rawArtist)
        if (cleaned.isBlank()) return emptyList()
        val separators = loadSeparators(context).filter { it.isNotBlank() }
        if (separators.isEmpty()) return listOf(cleaned)

        return runCatching {
            val patternParts = separators.sortedByDescending { it.length }.mapNotNull { token ->
                val trimmed = token.trim()
                if (trimmed.isEmpty()) return@mapNotNull null
                val escaped = Regex.escape(trimmed)
                if (trimmed.any { it.isLetterOrDigit() }) {
                    "(?:\\s+$escaped\\s+|\\s+$escaped$)"
                } else {
                    "\\s*$escaped\\s*"
                }
            }
            if (patternParts.isEmpty()) return@runCatching listOf(cleaned)
            val regex = Regex(patternParts.joinToString("|"), RegexOption.IGNORE_CASE)
            val split = cleaned.split(regex)
                .map { it.trim() }
                .filter { it.isNotBlank() && !it.equals("Unknown Artist", ignoreCase = true) }
                .distinct()
            if (split.isNotEmpty()) split else listOf(cleaned)
        }.getOrDefault(listOf(cleaned))
    }
}
