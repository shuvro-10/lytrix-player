package com.musicplayer.lytrix.ui

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import compose.icons.TablerIcons
import compose.icons.tablericons.Download
import compose.icons.tablericons.PlayerPlay
import compose.icons.tablericons.X
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class DownloadRewardRequest(
    val title: String,
    val isPlaylist: Boolean = false,
    val onConfirmDownload: () -> Unit
)

var globalDownloadRewardRequest by mutableStateOf<DownloadRewardRequest?>(null)

/**
 * Universal gate for downloads:
 * - If user is VIP / Ad-Free Pass active -> executes [onStartDownload] immediately without ad.
 * - If user is Free:
 *   - Single song -> 10-second rewarded ad
 *   - Full playlist -> 20-second rewarded ad (1 single ad for all songs)
 */
fun requestDownloadWithAd(
    context: Context,
    title: String,
    isPlaylist: Boolean = false,
    onStartDownload: () -> Unit
) {
    if (AdManager.isAdFree(context)) {
        onStartDownload()
    } else {
        globalDownloadRewardRequest = DownloadRewardRequest(
            title = title,
            isPlaylist = isPlaylist,
            onConfirmDownload = onStartDownload
        )
    }
}

private val SheetBg = Color(0xFF0B0F14)
private val Indigo = Color(0xFF6366F1)
private val Violet = Color(0xFF8B5CF6)
private val Mint = Color(0xFF10B981)
private val AccentBrush = Brush.horizontalGradient(listOf(Indigo, Violet))

@Composable
fun DownloadRewardAdDialog(
    request: DownloadRewardRequest,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val totalDurationSeconds = if (request.isPlaylist) 20 else 10
    var timeLeft by remember { mutableIntStateOf(totalDurationSeconds) }
    var pageLoaded by remember { mutableStateOf(false) }
    var timerStarted by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var hasCompleted by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.stopLoading()
            webViewRef?.destroy()
            webViewRef = null
        }
    }

    // 10s (single) or 20s (playlist) countdown
    LaunchedEffect(Unit) {
        timeLeft = totalDurationSeconds
        pageLoaded = false
        timerStarted = false

        // 5s fallback: start timer even if page reports slow/no load
        launch {
            delay(5000L)
            timerStarted = true
        }

        while (timeLeft > 0) {
            delay(1000L)
            if (timerStarted) timeLeft--
        }

        hasCompleted = true
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    Dialog(
        onDismissRequest = {
            if (hasCompleted) onDismiss()
        },
        properties = DialogProperties(
            dismissOnBackPress = hasCompleted,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top header bar
                Surface(color = SheetBg, modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (request.isPlaylist) "PLAYLIST DOWNLOAD · SPONSORED" else "SONG DOWNLOAD · SPONSORED",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 1.2.sp,
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = request.title,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            if (!hasCompleted) {
                                // Countdown timer pill + Close button
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = Color.White.copy(alpha = 0.08f),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                                    ) {
                                        Text(
                                            text = "Reward in ${timeLeft}s",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White.copy(alpha = 0.9f),
                                            maxLines = 1,
                                            softWrap = false,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    IconButton(
                                        onClick = {
                                            Toast.makeText(context, "Download cancelled", Toast.LENGTH_SHORT).show()
                                            onDismiss()
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = TablerIcons.X,
                                            contentDescription = "Cancel",
                                            tint = Color.White.copy(alpha = 0.6f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            } else {
                                // Claim & Start Download button
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(Brush.horizontalGradient(listOf(Color(0xFF10B981), Mint)))
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onDismiss()
                                            request.onConfirmDownload()
                                            Toast.makeText(context, "🎉 Starting download...", Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(horizontal = 18.dp, vertical = 9.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = TablerIcons.Download,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Download Now",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }

                        // Progress indicator bar
                        val progress = 1f - (timeLeft.toFloat() / totalDurationSeconds.toFloat())
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp),
                            color = if (hasCompleted) Mint else Indigo,
                            trackColor = Color.White.copy(alpha = 0.08f)
                        )
                    }
                }

                // WebView area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                webViewRef = this
                                @SuppressLint("SetJavaScriptEnabled")
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                settings.mediaPlaybackRequiresUserGesture = false

                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        pageLoaded = true
                                        timerStarted = true
                                    }

                                    override fun onReceivedError(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                        error: WebResourceError?
                                    ) {
                                        timerStarted = true
                                    }
                                }

                                loadUrl(AdManager.ADSTERRA_SMARTLINK)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Bottom bar helper hint
                Surface(
                    color = SheetBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (hasCompleted) {
                                "Tap 'Download Now' above to begin downloading."
                            } else {
                                if (request.isPlaylist) {
                                    "Watch this short 20s clip to unlock download for all tracks in this playlist."
                                } else {
                                    "Watch this short 10s clip to download this song."
                                }
                            },
                            fontSize = 11.5.sp,
                            color = Color.White.copy(alpha = 0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
