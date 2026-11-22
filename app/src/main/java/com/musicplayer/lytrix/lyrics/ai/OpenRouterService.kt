package com.musicplayer.lytrix.lyrics.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object OpenRouterService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun translate(
        text: String,
        targetLanguage: String,
        apiKey: String,
        baseUrl: String,
        model: String,
        mode: String,
        maxRetries: Int = 2,
        customSystemPrompt: String = ""
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext Result.failure(Exception("Input text is empty"))

        val lineCount = text.lines().size
        val effectiveUrl = baseUrl.ifBlank { AiConstants.DEFAULT_BASE_URL }

        val requestJson = buildTranslationRequest(
            text = text,
            targetLanguage = targetLanguage,
            model = model.ifBlank { AiConstants.DEFAULT_MODEL },
            mode = mode,
            customSystemPrompt = customSystemPrompt,
            lineCount = lineCount
        )

        var lastException: Exception? = null

        for (attempt in 0..maxRetries) {
            try {
                val requestBuilder = Request.Builder()
                    .url(effectiveUrl)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("HTTP-Referer", "https://github.com/lytrixplayer/lytrix")
                    .addHeader("X-Title", "Lytrix Player")

                if (apiKey.isNotBlank()) {
                    requestBuilder.addHeader("Authorization", "Bearer ${apiKey.trim()}")
                }

                val request = requestBuilder
                    .post(requestJson.toString().toRequestBody(jsonMediaType))
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val errorMsg = parseErrorMessage(responseBody, response.code, response.message)
                        if (response.code >= 500 && attempt < maxRetries) {
                            lastException = Exception(errorMsg)
                            return@use
                        }
                        return@withContext Result.failure(Exception("Translation failed: $errorMsg"))
                    }

                    val content = parseContentFromResponse(responseBody)
                        ?: return@withContext Result.failure(Exception("Empty translation response from AI"))

                    val lines = parseJsonLines(content, lineCount)
                    if (lines.isNotEmpty()) {
                        return@withContext Result.success(lines)
                    } else {
                        // Fallback: try line-by-line parsing if model returned plain lines instead of JSON
                        val fallback = parseFallbackLines(content, lineCount)
                        if (fallback.isNotEmpty()) {
                            return@withContext Result.success(fallback)
                        }
                        return@withContext Result.failure(Exception("Could not parse translation output"))
                    }
                }
            } catch (e: Exception) {
                lastException = e
                if (attempt >= maxRetries) break
            }
        }

        Result.failure(lastException ?: Exception("Translation failed after retries"))
    }

    private fun buildTranslationRequest(
        text: String,
        targetLanguage: String,
        model: String,
        mode: String,
        customSystemPrompt: String,
        lineCount: Int
    ): JSONObject {
        val systemPrompt = customSystemPrompt.takeIf { it.isNotBlank() }
            ?.replace("{lineCount}", lineCount.toString())
            ?: AiConstants.DEFAULT_AI_SYSTEM_PROMPT.replace("{lineCount}", lineCount.toString())

        val userPrompt = when (mode) {
            "Transcribed" -> """Transcribe/transliterate the following $lineCount lines phonetically into $targetLanguage script.

CRITICAL REQUIREMENTS:
- Convert the SOUND/PRONUNCIATION of the original text into $targetLanguage script
- DO NOT translate the meaning - only represent how the original words SOUND
- Use the native script of $targetLanguage (e.g., Bengali for Bengali, Devanagari for Hindi, etc.)
- Preserve the original pronunciation as closely as possible in the target script
- Keep punctuation and formatting
- Preserve line-by-line structure exactly
- If text is already in $targetLanguage script, return it UNCHANGED

Input ($lineCount lines):
$text

Output MUST be a JSON object {"lines": [...]} with EXACTLY $lineCount strings in $targetLanguage script."""

            else -> """Translate the following $lineCount lines to $targetLanguage.

IMPORTANT:
- Provide natural, accurate translation
- Maintain poetic flow and meaning
- Keep punctuation appropriate for target language
- Preserve line-by-line structure exactly
- For song lyrics, prioritize singability

Input ($lineCount lines):
$text

Output MUST be a JSON object {"lines": [...]} with EXACTLY $lineCount strings."""
        }

        val json = JSONObject()
        json.put("model", model)

        val messages = JSONArray()

        val sysMsg = JSONObject()
        sysMsg.put("role", "system")
        sysMsg.put("content", systemPrompt)
        messages.put(sysMsg)

        val userMsg = JSONObject()
        userMsg.put("role", "user")
        userMsg.put("content", userPrompt)
        messages.put(userMsg)

        json.put("messages", messages)
        json.put("temperature", 0.3)

        // Request JSON object format if supported
        val responseFormat = JSONObject()
        responseFormat.put("type", "json_object")
        json.put("response_format", responseFormat)

        return json
    }

    private fun parseContentFromResponse(responseBody: String): String? {
        return try {
            val root = JSONObject(responseBody)
            val choices = root.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val firstChoice = choices.getJSONObject(0)
                val message = firstChoice.optJSONObject("message")
                message?.optString("content")
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun parseJsonLines(content: String, expectedCount: Int): List<String> {
        return try {
            // Trim markdown json fences if any
            val clean = content.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val json = JSONObject(clean)
            val linesArray = json.optJSONArray("lines") ?: return emptyList()

            val list = mutableListOf<String>()
            for (i in 0 until linesArray.length()) {
                list.add(linesArray.getString(i))
            }

            if (list.size == expectedCount) {
                list
            } else if (list.isNotEmpty()) {
                // Pad or take to match expectedCount
                if (list.size < expectedCount) {
                    list + List(expectedCount - list.size) { "" }
                } else {
                    list.take(expectedCount)
                }
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseFallbackLines(content: String, expectedCount: Int): List<String> {
        val lines = content.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { line ->
                line.replace(Regex("^\\d+\\.\\s*"), "")
                    .replace(Regex("^Line\\s+\\d+:\\s*", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("^-\\s*"), "")
            }

        return if (lines.size == expectedCount) {
            lines
        } else if (lines.isNotEmpty()) {
            if (lines.size < expectedCount) {
                lines + List(expectedCount - lines.size) { "" }
            } else {
                lines.take(expectedCount)
            }
        } else emptyList()
    }

    private fun parseErrorMessage(responseBody: String, code: Int, message: String): String {
        return try {
            val root = JSONObject(responseBody)
            val error = root.optJSONObject("error")
            val msg = error?.optString("message")
            if (!msg.isNullOrBlank()) msg else "HTTP $code: $message"
        } catch (e: Exception) {
            "HTTP $code: $message"
        }
    }
}
