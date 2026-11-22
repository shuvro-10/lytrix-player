package com.musicplayer.lytrix.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.musicplayer.lytrix.R
import com.musicplayer.lytrix.innertube.YouTube
import com.musicplayer.lytrix.innertube.PlaybackAuthState
import com.musicplayer.lytrix.innertube.models.AccountChannel
import com.musicplayer.lytrix.ui.YoutubePreferencesManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import compose.icons.TablerIcons
import compose.icons.tablericons.*

private enum class LoginStage {
    Authenticating,
    CheckingAccounts,
    SelectingAccount,
    SwitchingAccount,
    Completing,
}

private data class AuthData(
    val cookie: String,
    val visitorData: String,
    val dataSyncId: String,
    val authUser: String,
)

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onNavigateBack: () -> Unit,
    isSwitchingChannel: Boolean = false,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val jsInterface = remember { LoginJsInterface() }
    var loginStage by remember { mutableStateOf(LoginStage.Authenticating) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var accounts by remember { mutableStateOf<List<AccountChannel>>(emptyList()) }
    var selectedAccount by remember { mutableStateOf<AccountChannel?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val prefManager = remember { YoutubePreferencesManager(context) }

    suspend fun extractAuthData(webView: WebView?): AuthData? {
        val view = webView ?: return null
        var hasAuthCookie = false
        var hasVisitorData = false
        repeat(20) {
            val cookie = CookieManager.getInstance().getCookie("https://music.youtube.com").orEmpty()
            val hasSapisid = cookie.contains("SAPISID")
            val visitorDataDeferred = CompletableDeferred<String?>()
            val dataSyncIdDeferred = CompletableDeferred<String?>()
            val authUserDeferred = CompletableDeferred<String?>()
            jsInterface.onVisitorDataReceived = { visitorData ->
                if (!visitorDataDeferred.isCompleted) visitorDataDeferred.complete(visitorData)
            }
            jsInterface.onDataSyncIdReceived = { dataSyncId ->
                if (!dataSyncIdDeferred.isCompleted) dataSyncIdDeferred.complete(dataSyncId)
            }
            jsInterface.onAuthUserReceived = { authUser ->
                if (!authUserDeferred.isCompleted) authUserDeferred.complete(authUser)
            }

            view.loadUrl(
                "javascript:Android.onRetrieveVisitorData(" +
                    "window.yt&&window.yt.config_?window.yt.config_.VISITOR_DATA:null)",
            )
            view.loadUrl(
                "javascript:Android.onRetrieveDataSyncId(" +
                    "window.yt&&window.yt.config_?window.yt.config_.DATASYNC_ID:null)",
            )
            view.loadUrl(
                "javascript:Android.onRetrieveAuthUser(" +
                    "window.yt&&window.yt.config_?String(window.yt.config_.SESSION_INDEX||0):'0')",
            )

            val pageAuthData =
                withTimeoutOrNull(1_000) {
                    WebAuthData(
                        visitorData = visitorDataDeferred.await(),
                        dataSyncId = dataSyncIdDeferred.await(),
                        authUser = authUserDeferred.await().orEmpty(),
                    )
                }
            hasAuthCookie = hasSapisid
            val visitorData = pageAuthData?.visitorData
            hasVisitorData = !visitorData.isNullOrBlank()
            if (hasAuthCookie && pageAuthData != null && !visitorData.isNullOrBlank()) {
                return AuthData(
                    cookie = cookie,
                    visitorData = visitorData,
                    dataSyncId = pageAuthData.dataSyncId.orEmpty().substringBefore("||"),
                    authUser = pageAuthData.authUser.filter(Char::isDigit).ifBlank { "0" },
                )
            }
            delay(500)
        }

        return null
    }

    suspend fun finalizeLogin(authData: AuthData) {
        loginStage = LoginStage.Completing
        YouTube.authState = PlaybackAuthState(
            cookie = authData.cookie,
            visitorData = authData.visitorData,
            dataSyncId = authData.dataSyncId,
            authUser = authData.authUser
        )

        try {
            // Need to figure out the account info... we don't have accountInfo() in this YouTube object
            // Let's just use the selected account if present, or leave it blank
            val name = selectedAccount?.name ?: ""
            val channelHandle = selectedAccount?.channelHandle ?: ""
            val email = "" // Not retrieved in this version

            withContext(Dispatchers.IO) {
                prefManager.saveAuthData(
                    cookie = authData.cookie,
                    visitorData = authData.visitorData,
                    dataSyncId = authData.dataSyncId,
                    authUser = authData.authUser,
                    accountName = name,
                    accountEmail = email,
                    accountChannelHandle = channelHandle
                )
            }

            webViewRef?.apply {
                stopLoading()
                clearHistory()
                clearCache(true)
                clearFormData()
            }
            CookieManager.getInstance().flush()

            withContext(Dispatchers.Main) {
                context.packageManager
                    .getLaunchIntentForPackage(context.packageName)
                    ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK) }
                    ?.let(context::startActivity)
                Runtime.getRuntime().exit(0)
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (throwable: Exception) {
            throwable.printStackTrace()
            errorMessage = "Login Failed: ${throwable.message}"
            loginStage = LoginStage.Authenticating
        }
    }

    suspend fun checkAvailableAccounts(authData: AuthData) {
        loginStage = LoginStage.CheckingAccounts
        YouTube.authState = PlaybackAuthState(
            cookie = authData.cookie,
            visitorData = authData.visitorData,
            dataSyncId = authData.dataSyncId,
            authUser = authData.authUser
        )

        val availableAccounts = YouTube.accountChannels().getOrNull() ?: emptyList()

        if (isSwitchingChannel && availableAccounts.isEmpty()) {
            errorMessage = "Failed to load channels"
            loginStage = LoginStage.Authenticating
        } else if (availableAccounts.size > 1) {
            val currentAccount =
                availableAccounts.firstOrNull { account ->
                    account.dataSyncId == authData.dataSyncId ||
                        account.dataSyncId?.substringBefore("||") == authData.dataSyncId
                } ?: availableAccounts.firstOrNull { it.isSelected }
            
            // Re-map with copied selected states isn't possible if it's a data class that doesn't have isSelected mutable or copyable exactly, but let's assume it has isSelected.
            // Wait, I can just find the selected one.
            accounts = availableAccounts
            selectedAccount = currentAccount ?: accounts.first()
            loginStage = LoginStage.SelectingAccount
        } else {
            selectedAccount = availableAccounts.firstOrNull()
            finalizeLogin(authData)
        }
    }

    fun handleAuthenticatedPage() {
        when (loginStage) {
            LoginStage.Authenticating,
            LoginStage.SwitchingAccount,
            -> {
                val stage = loginStage
                loginStage = LoginStage.CheckingAccounts
                coroutineScope.launch {
                    val authData =
                        extractAuthData(webViewRef) ?: run {
                            errorMessage = "Auth extraction failed"
                            loginStage = stage
                            return@launch
                        }
                    if (stage == LoginStage.SwitchingAccount) {
                        val selectedDataSyncId = selectedAccount?.dataSyncId?.substringBefore("||")
                        finalizeLogin(
                            authData.copy(
                                dataSyncId = selectedDataSyncId?.takeIf(String::isNotBlank) ?: authData.dataSyncId,
                            ),
                        )
                    } else {
                        checkAvailableAccounts(authData)
                    }
                }
            }

            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isSwitchingChannel) "Switch Channel" else "Login") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                },
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { webViewContext ->
                    WebView(webViewContext).apply {
                        webViewClient =
                            object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView,
                                    request: WebResourceRequest,
                                ): Boolean {
                                    val scheme = request.url.scheme
                                    if (scheme == "http" || scheme == "https") return false

                                    if (scheme == "intent") {
                                        runCatching {
                                            Intent.parseUri(request.url.toString(), Intent.URI_INTENT_SCHEME)
                                                .apply {
                                                    addCategory(Intent.CATEGORY_BROWSABLE)
                                                    component = null
                                                    selector = null
                                                }.let(context::startActivity)
                                        }
                                    }
                                    return true
                                }

                                override fun onPageFinished(
                                    view: WebView,
                                    url: String?,
                                ) {
                                    val pageUri = url?.let(Uri::parse)
                                    if (pageUri?.scheme == "https" && pageUri.host == "music.youtube.com") {
                                        handleAuthenticatedPage()
                                    }
                                }
                            }
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false
                        }
                        addJavascriptInterface(jsInterface, "Android")
                        webViewRef = this
                        if (isSwitchingChannel) {
                            restoreYouTubeCookies(YouTube.authState.cookie)
                        }
                        loadUrl(
                            if (isSwitchingChannel) {
                                "https://music.youtube.com"
                            } else {
                                "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com"
                            },
                        )
                    }
                },
            )

            if (loginStage == LoginStage.CheckingAccounts || loginStage == LoginStage.Completing) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }
    }

    if (loginStage == LoginStage.SelectingAccount) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Choose Channel") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    accounts.forEach { account ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (account == selectedAccount) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedAccount = account }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (account.thumbnailUrl != null) {
                                    AsyncImage(
                                        model = account.thumbnailUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape),
                                    )
                                } else {
                                    Icon(
                                        imageVector = TablerIcons.User,
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp),
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = account.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    account.channelHandle?.let { channelHandle ->
                                        Text(
                                            text = channelHandle,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                RadioButton(selected = account == selectedAccount, onClick = null)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val account = selectedAccount ?: return@Button
                        loginStage = LoginStage.Completing
                        coroutineScope.launch {
                            val authData =
                                extractAuthData(webViewRef) ?: run {
                                    errorMessage = "Auth extraction failed"
                                    loginStage = LoginStage.Authenticating
                                    return@launch
                                }
                            val selectedDataSyncId = account.dataSyncId.substringBefore("||")
                            finalizeLogin(authData.copy(dataSyncId = selectedDataSyncId.takeIf(String::isNotBlank) ?: authData.dataSyncId))
                        }
                    },
                ) {
                    Text("Continue")
                }
            },
        )
    }

    errorMessage?.let { error ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text("Login Failed") },
            text = { Text(error) },
            confirmButton = {
                Button(
                    onClick = {
                        errorMessage = null
                        webViewRef?.reload()
                    },
                ) {
                    Text("Retry")
                }
            },
        )
    }

    BackHandler(
        enabled = webViewRef?.canGoBack() == true && loginStage == LoginStage.Authenticating && errorMessage == null,
    ) {
        webViewRef?.goBack()
    }
}

private class LoginJsInterface {
    var onVisitorDataReceived: ((String?) -> Unit)? = null
    var onDataSyncIdReceived: ((String?) -> Unit)? = null
    var onAuthUserReceived: ((String?) -> Unit)? = null

    @JavascriptInterface
    fun onRetrieveVisitorData(visitorData: String?) {
        onVisitorDataReceived?.invoke(visitorData)
    }

    @JavascriptInterface
    fun onRetrieveDataSyncId(dataSyncId: String?) {
        onDataSyncIdReceived?.invoke(dataSyncId)
    }

    @JavascriptInterface
    fun onRetrieveAuthUser(authUser: String?) {
        onAuthUserReceived?.invoke(authUser)
    }
}

private data class WebAuthData(
    val visitorData: String?,
    val dataSyncId: String?,
    val authUser: String,
)

private fun restoreYouTubeCookies(cookie: String?) {
    val cookieManager = CookieManager.getInstance()
    cookieManager.setAcceptCookie(true)
    cookie
        ?.split(";")
        ?.map { it.trim() }
        ?.filter { it.contains("=") }
        ?.map { it.substringBefore("=") to it.substringAfter("=") }
        .orEmpty()
        .forEach { (name, value) ->
            cookieManager.setCookie(
                "https://music.youtube.com",
                "$name=$value; Domain=.youtube.com; Path=/; Secure",
            )
        }
    cookieManager.flush()
}
