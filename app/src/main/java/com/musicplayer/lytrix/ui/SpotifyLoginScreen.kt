package com.musicplayer.lytrix.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowLeft
import compose.icons.tablericons.Key
import com.musicplayer.lytrix.spotify.SpotifyAuth
import com.musicplayer.lytrix.spotify.SpotifyPreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

private const val USER_AGENT_DESKTOP =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SpotifyLoginScreen(
    onNavigateBack: () -> Unit,
    onLoginSuccess: () -> Unit
) {
    BackHandler { onNavigateBack() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefManager = remember { SpotifyPreferencesManager(context) }

    var isLoading by remember { mutableStateOf(true) }
    var isProcessing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    var hasError by remember { mutableStateOf(false) }
    var showManualTokenDialog by remember { mutableStateOf(false) }
    var showLoginTip by remember { mutableStateOf(true) }

    val tokenFetchStarted = remember { AtomicBoolean(false) }

    val appBg = Color(0xFF070504)
    val cardBg = Color(0xFF161210)
    val textColor = Color(0xFFF7EBE5)
    val spotifyGreen = Color(0xFF1DB954)

    // ── Function to process sp_dc cookie ─────────────────────────────────────
    fun processSpDcCookie(spDc: String, spKey: String = "", webView: WebView? = null) {
        if (!tokenFetchStarted.compareAndSet(false, true)) return

        webView?.stopLoading()
        webView?.loadUrl("about:blank")

        isProcessing = true
        hasError = false
        statusMessage = "Authenticating with Spotify..."

        scope.launch(Dispatchers.IO) {
            try {
                // Step 1: Fetch Access Token via TOTP
                withContext(Dispatchers.Main) {
                    statusMessage = "Generating secure session token..."
                }
                val tokenResult = SpotifyAuth.fetchAccessToken(spDc, spKey)
                val token = tokenResult.getOrThrow()

                // Step 2: Fetch User Profile
                withContext(Dispatchers.Main) {
                    statusMessage = "Loading Spotify profile..."
                }
                var displayName = ""
                var userId = ""
                var avatarUrl = ""
                var product = ""

                SpotifyAuth.fetchUserProfile(token.accessToken).onSuccess { user ->
                    displayName = user.displayName ?: user.id
                    userId = user.id
                    avatarUrl = user.images.firstOrNull()?.url.orEmpty()
                    product = user.product.orEmpty()
                }.onFailure {
                    Timber.w(it, "SpotifyLoginScreen: could not fetch profile details")
                }

                // Step 3: Save to DataStore
                prefManager.saveAuth(
                    spDc = spDc,
                    spKey = spKey,
                    accessToken = token.accessToken,
                    expiryMs = token.accessTokenExpirationTimestampMs,
                    name = displayName,
                    id = userId,
                    avatar = avatarUrl,
                    product = product
                )

                withContext(Dispatchers.Main) {
                    statusMessage = "Connected successfully!"
                    Toast.makeText(context, "Logged into Spotify successfully!", Toast.LENGTH_SHORT).show()
                }

                delay(400)
                withContext(Dispatchers.Main) {
                    onLoginSuccess()
                }
            } catch (e: Exception) {
                Timber.e(e, "SpotifyLoginScreen: login failed: ${e.message}")
                withContext(Dispatchers.Main) {
                    statusMessage = "Login failed: ${e.message?.take(100)}"
                    hasError = true
                    tokenFetchStarted.set(false)
                }
            }
        }
    }

    // ── Manual sp_dc Token Dialog ────────────────────────────────────────────
    if (showManualTokenDialog) {
        var tokenInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showManualTokenDialog = false },
            title = { Text("Log in with sp_dc Token", color = textColor) },
            text = {
                Column {
                    Text(
                        "Paste your Spotify 'sp_dc' cookie from open.spotify.com here:\n(Tip: Or reset your Spotify password at spotify.com/password-reset to log in directly via email & password!)",
                        color = textColor.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("sp_dc cookie value...", color = textColor.copy(alpha = 0.4f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = textColor,
                            unfocusedTextColor = textColor,
                            focusedBorderColor = spotifyGreen,
                            unfocusedBorderColor = textColor.copy(alpha = 0.3f)
                        ),
                        singleLine = false,
                        maxLines = 4
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleaned = tokenInput.trim()
                            .replace("sp_dc=", "")
                            .replace(";", "")
                            .trim()
                        if (cleaned.isNotBlank()) {
                            showManualTokenDialog = false
                            processSpDcCookie(cleaned)
                        } else {
                            Toast.makeText(context, "Please enter a valid sp_dc cookie", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = spotifyGreen)
                ) {
                    Text("Connect", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualTokenDialog = false }) {
                    Text("Cancel", color = textColor)
                }
            },
            containerColor = cardBg
        )
    }

    // ── Main Layout ──────────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Log in to Spotify",
                        color = textColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(TablerIcons.ArrowLeft, contentDescription = "Back", tint = textColor)
                    }
                },
                actions = {
                    IconButton(onClick = { showManualTokenDialog = true }) {
                        Icon(TablerIcons.Key, contentDescription = "Manual sp_dc login", tint = spotifyGreen)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = appBg)
            )
        },
        containerColor = appBg
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (showLoginTip) {
                Surface(
                    color = cardBg,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = spotifyGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Log in with your Spotify Email & Password, or tap the 🔑 icon at top-right to paste your sp_dc cookie.",
                            fontSize = 11.sp,
                            color = textColor.copy(alpha = 0.85f),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { showLoginTip = false },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Dismiss",
                                tint = textColor.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)

                    WebView(ctx).apply {
                        cookieManager.setAcceptThirdPartyCookies(this, true)

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            javaScriptCanOpenWindowsAutomatically = true
                            setSupportMultipleWindows(false)
                            userAgentString = USER_AGENT_DESKTOP
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                isLoading = true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false

                                // Apply CSS layout fix to avoid collapsed black screen
                                view?.evaluateJavascript(
                                    """
                                    (function() {
                                      var id = 'lytrix-spotify-fix';
                                      if (!document.getElementById(id)) {
                                        var st = document.createElement('style');
                                        st.id = id;
                                        st.textContent = 'html, body { height: auto !important; min-height: 100% !important; overflow: visible !important; } body > div { height: auto !important; min-height: 100% !important; } main { position: static !important; height: auto !important; min-height: 100dvh !important; max-height: none !important; overflow: visible !important; }';
                                        document.head.appendChild(st);
                                      }
                                    })();
                                    """.trimIndent(),
                                    null
                                )

                                checkCookies(url, view)
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                                val reqUrl = request?.url?.toString() ?: return false
                                return checkCookies(reqUrl, view)
                            }

                            private fun checkCookies(url: String?, view: WebView?): Boolean {
                                if (url == null) return false

                                val isSpotifyOpen = url.startsWith("https://open.spotify.com")
                                val isSpotifyStatus = url.contains("accounts.spotify.com") && url.contains("status")

                                if (isSpotifyOpen || isSpotifyStatus) {
                                    val allCookies = CookieManager.getInstance().getCookie(url)
                                        ?: CookieManager.getInstance().getCookie("https://open.spotify.com")
                                        ?: CookieManager.getInstance().getCookie("https://accounts.spotify.com")

                                    val spDc = extractCookie(allCookies, "sp_dc")
                                    val spKey = extractCookie(allCookies, "sp_key") ?: ""

                                    if (!spDc.isNullOrBlank()) {
                                        processSpDcCookie(spDc, spKey, view)
                                        return true
                                    }
                                }
                                return false
                            }

                            private fun extractCookie(cookieHeader: String?, name: String): String? {
                                if (cookieHeader.isNullOrBlank()) return null
                                return cookieHeader.split(";")
                                    .mapNotNull {
                                        val parts = it.trim().split("=", limit = 2)
                                        if (parts.size == 2) parts[0].trim() to parts[1].trim() else null
                                    }
                                    .firstOrNull { it.first == name && it.second.isNotBlank() }
                                    ?.second
                            }
                        }

                        loadUrl(SpotifyAuth.LOGIN_URL)
                    }
                }
            )

            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                    color = spotifyGreen
                )
            }

            if (isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(appBg.copy(alpha = 0.92f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (!hasError) {
                                CircularProgressIndicator(color = spotifyGreen)
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                            Text(
                                text = statusMessage,
                                color = if (hasError) MaterialTheme.colorScheme.error else textColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            if (hasError) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        hasError = false
                                        isProcessing = false
                                        statusMessage = ""
                                        tokenFetchStarted.set(false)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = spotifyGreen)
                                ) {
                                    Text("Retry", color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
}
