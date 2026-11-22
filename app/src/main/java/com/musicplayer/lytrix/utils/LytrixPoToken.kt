package com.musicplayer.lytrix.utils

import android.content.Context
import android.util.Base64
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

object LytrixPoToken {
    private const val TAG = "LytrixPoToken"
    private const val GOOGLE_API_KEY = "AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw"
    private const val REQUEST_KEY = "O43z0dpjhgX20SCx4KAo"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.3"

    private val httpClient = OkHttpClient.Builder().build()
    private var cachedPoToken: String? = null
    private var isGenerating = false
    private var webView: WebView? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // ─── Token Generation API ───
    fun getTokenSynchronously(context: Context, videoId: String): String? {
        if (cachedPoToken != null) return cachedPoToken

        return runBlocking {
            try {
                withTimeout(10_000L) { // সর্বোচ্চ ১০ সেকেন্ড অপেক্ষা করবে
                    generatePoTokenAsync(context, videoId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get PoToken: ${e.message}")
                null
            }
        }
    }

    private suspend fun generatePoTokenAsync(context: Context, videoId: String): String {
        return suspendCancellableCoroutine { cont ->
            if (isGenerating) {
                // ইতিমধ্যে কাজ চললে ফেইল সেফ
                if (!cont.isCompleted) cont.resumeWith(Result.failure(Exception("Already generating")))
                return@suspendCancellableCoroutine
            }
            isGenerating = true

            scope.launch {
                webView = WebView(context.applicationContext).apply {
                    settings.javaScriptEnabled = true
                    settings.userAgentString = USER_AGENT
                    settings.blockNetworkLoads = false
                    settings.databaseEnabled = true
                    settings.domStorageEnabled = true

                    addJavascriptInterface(object : Any() {
                        @JavascriptInterface
                        fun downloadAndRunBotguard() {
                            Log.d(TAG, "downloadAndRunBotguard called from JS")
                            makeApiRequest(
                                "https://www.youtube.com/api/jnn/v1/Create",
                                "[ \"$REQUEST_KEY\" ]"
                            ) { responseBody ->
                                val parsedChallenge = parseChallengeData(responseBody)
                                val jsCommand = """
                                    try {
                                        var data = $parsedChallenge;
                                        runBotGuard(data).then(function(result) {
                                            window.webPoSignalOutput = result.webPoSignalOutput;
                                            AndroidBridge.onRunBotguardResult(result.botguardResponse);
                                        }).catch(function(error) {
                                            AndroidBridge.onError(error.toString());
                                        });
                                    } catch (e) { AndroidBridge.onError(e.toString()); }
                                """.trimIndent()

                                scope.launch(Dispatchers.Main) { evaluateJavascript(jsCommand, null) }
                            }
                        }

                        @JavascriptInterface
                        fun onRunBotguardResult(botguardResponse: String) {
                            makeApiRequest(
                                "https://www.youtube.com/api/jnn/v1/GenerateIT",
                                "[ \"$REQUEST_KEY\", \"$botguardResponse\" ]"
                            ) { responseBody ->
                                val integrityData = parseIntegrityTokenData(responseBody)
                                val jsCommand = """
                                    try {
                                        var integrityToken = ${integrityData.first};
                                        createPoTokenMinter(window.webPoSignalOutput, integrityToken).then(function() {
                                            AndroidBridge.onMinterCreated();
                                        }).catch(function(e) { AndroidBridge.onError(e.toString()); });
                                    } catch(e) { AndroidBridge.onError(e.toString()); }
                                """.trimIndent()
                                scope.launch(Dispatchers.Main) { evaluateJavascript(jsCommand, null) }
                            }
                        }

                        @JavascriptInterface
                        fun onMinterCreated() {
                            val u8Id = stringToU8(videoId)
                            val jsCommand = """
                                try {
                                    obtainPoToken($u8Id).then(function(poTokenU8) {
                                        AndroidBridge.onTokenSuccess(poTokenU8.join(","));
                                    }).catch(function(e) { AndroidBridge.onError(e.toString()); });
                                } catch(e) { AndroidBridge.onError(e.toString()); }
                            """.trimIndent()
                            scope.launch(Dispatchers.Main) { evaluateJavascript(jsCommand, null) }
                        }

                        @JavascriptInterface
                        fun onTokenSuccess(poTokenU8: String) {
                            try {
                                val finalToken = u8ToBase64(poTokenU8)
                                cachedPoToken = finalToken
                                isGenerating = false
                                if (!cont.isCompleted) cont.resumeWith(Result.success(finalToken))
                            } catch (e: Exception) {
                                if (!cont.isCompleted) cont.resumeWith(Result.failure(e))
                            }
                        }

                        @JavascriptInterface
                        fun onError(error: String) {
                            Log.e(TAG, "JS Error: $error")
                            isGenerating = false
                            if (!cont.isCompleted) cont.resumeWith(Result.failure(Exception(error)))
                        }
                    }, "AndroidBridge")

                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                            Log.d(TAG, "JS Console: ${consoleMessage?.message()}")
                            return super.onConsoleMessage(consoleMessage)
                        }
                    }
                }

                // Load HTML and trigger BotGuard
                withContext(Dispatchers.IO) {
                    try {
                        val html = context.assets.open("solver/po_token.html").bufferedReader().use { it.readText() }
                        val data = html.replaceFirst("</script>", "\nAndroidBridge.downloadAndRunBotguard();\n</script>")
                        withContext(Dispatchers.Main) {
                            webView?.loadDataWithBaseURL("https://www.youtube.com", data, "text/html", "utf-8", null)
                        }
                    } catch (e: Exception) {
                        isGenerating = false
                        if (!cont.isCompleted) cont.resumeWith(Result.failure(e))
                    }
                }
            }

            cont.invokeOnCancellation {
                isGenerating = false
                scope.launch(Dispatchers.Main) { webView?.destroy(); webView = null }
            }
        }
    }

    // ─── Network & Parsing Utils (Converted from Meld) ───
    private fun makeApiRequest(url: String, jsonBody: String, onSuccess: (String) -> Unit) {
        scope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(url)
                    .post(jsonBody.toRequestBody("application/json+protobuf".toMediaType()))
                    .header("User-Agent", USER_AGENT)
                    .header("Content-Type", "application/json+protobuf")
                    .header("x-goog-api-key", GOOGLE_API_KEY)
                    .build()
                val response = httpClient.newCall(request).execute()
                response.body?.string()?.let { onSuccess(it) }
            } catch (e: Exception) {
                Log.e(TAG, "API Request failed: ${e.message}")
            }
        }
    }

    private fun parseChallengeData(raw: String): String {
        val scrambled = JSONArray(raw)
        val challengeData = if (scrambled.length() > 1 && scrambled.opt(1) is String) {
            JSONArray(descramble(scrambled.getString(1)))
        } else {
            scrambled.getJSONArray(0)
        }

        val obj = JSONObject()
        obj.put("messageId", challengeData.getString(0))

        val interpreterJs = JSONObject()
        interpreterJs.put("privateDoNotAccessOrElseSafeScriptWrappedValue", challengeData.optJSONArray(1)?.optString(0) ?: JSONObject.NULL)
        interpreterJs.put("privateDoNotAccessOrElseTrustedResourceUrlWrappedValue", challengeData.optJSONArray(2)?.optString(0) ?: JSONObject.NULL)
        obj.put("interpreterJavascript", interpreterJs)

        obj.put("interpreterHash", challengeData.getString(3))
        obj.put("program", challengeData.getString(4))
        obj.put("globalName", challengeData.getString(5))
        obj.put("clientExperimentsStateBlob", challengeData.getString(7))

        return obj.toString()
    }

    private fun parseIntegrityTokenData(raw: String): Pair<String, Long> {
        val arr = JSONArray(raw)
        return Pair(base64ToU8(arr.getString(0)), arr.getLong(1))
    }

    private fun stringToU8(str: String): String = "new Uint8Array([" + str.toByteArray().joinToString(",") { it.toUByte().toString() } + "])"

    private fun base64ToU8(base64: String): String {
        val bytes = Base64.decode(base64.replace('-', '+').replace('_', '/').replace('.', '='), Base64.DEFAULT)
        return "new Uint8Array([" + bytes.joinToString(",") { it.toUByte().toString() } + "])"
    }

    private fun u8ToBase64(poTokenU8: String): String {
        val bytes = poTokenU8.split(",").map { it.toUByte().toByte() }.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP).replace("+", "-").replace("/", "_")
    }

    private fun descramble(scrambled: String): String {
        val bytes = Base64.decode(scrambled.replace('-', '+').replace('_', '/').replace('.', '='), Base64.DEFAULT)
        return String(bytes.map { (it + 97).toByte() }.toByteArray())
    }
}