package com.musicplayer.lytrix.ui

import android.annotation.SuppressLint
import android.widget.Toast
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import compose.icons.TablerIcons
import compose.icons.tablericons.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private const val TIER_AD_DURATION_SECONDS = 20
private const val TIER1_MILLIS = 4L * 60L * 60L * 1000L
private const val TIER2_MILLIS = 12L * 60L * 60L * 1000L

// ── Palette ──────────────────────────────────────────────
private val SheetBg = Color(0xFF0B0F14)
private val CardBg = Color(0xFF111720)
private val Indigo = Color(0xFF6366F1)
private val Violet = Color(0xFF8B5CF6)
private val Mint = Color(0xFF34D399)
private val Amber = Color(0xFFF59E0B)
private val AccentBrush = Brush.horizontalGradient(listOf(Indigo, Violet))

private enum class BtnStyle { Primary, Secondary, Active, Muted }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LytrixAdFreeSheet(
    onDismiss: () -> Unit,
    onNavigateToPremium: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    // Live states
    var currentTier by remember { mutableIntStateOf(AdManager.getAdFreeTier(context)) }
    var remainingTime by remember { mutableStateOf(AdManager.getRemainingAdFreeTime(context)) }
    var remainingMillis by remember { mutableLongStateOf(AdManager.getRemainingAdFreeMillis(context)) }

    // Ad player states
    var isWebViewOpen by remember { mutableStateOf(false) }
    var adTargetTier by remember { mutableIntStateOf(1) }
    var adStepIndex by remember { mutableIntStateOf(1) }
    var adTotalSteps by remember { mutableIntStateOf(1) }
    var timeLeft by remember { mutableIntStateOf(TIER_AD_DURATION_SECONDS) }
    var pageLoaded by remember { mutableStateOf(false) }
    var timerStarted by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // Live countdown ticker
    LaunchedEffect(Unit) {
        while (true) {
            currentTier = AdManager.getAdFreeTier(context)
            remainingTime = AdManager.getRemainingAdFreeTime(context)
            remainingMillis = AdManager.getRemainingAdFreeMillis(context)
            delay(1000L)
        }
    }

    // 20s ad timer
    LaunchedEffect(isWebViewOpen, adStepIndex) {
        if (isWebViewOpen) {
            timeLeft = TIER_AD_DURATION_SECONDS
            pageLoaded = false
            timerStarted = false

            // 5s fallback: start the timer even if the page never reports "loaded"
            launch {
                delay(5000L)
                timerStarted = true
            }

            while (timeLeft > 0) {
                delay(1000L)
                if (timerStarted) timeLeft--
            }
        }
    }

    com.musicplayer.lytrix.ui.component.LytrixFluidBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SheetBg,
        enableTwoStage = false,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f))
            )
        },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 4.dp, bottom = 20.dp)
                .navigationBarsPadding()
        ) {
            // ── Header ──
            Text(
                text = "Ad-Free Pass",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Watch a short clip, enjoy music without interruptions.",
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = Color.White.copy(alpha = 0.55f)
            )

            Spacer(modifier = Modifier.height(20.dp))

            val isUserVip = remember { isUserPremium(context) }

            if (isUserVip) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF1E1B4B))
                        .border(1.dp, Indigo.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "👑 Premium Active",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFBBF24)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Your account has full Premium access. Unlimited ad-free music is permanently unlocked with no ads needed!",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.85f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 20.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            } else {
                // ── Active pass status ──
                AnimatedVisibility(
                    visible = currentTier > 0,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        ActiveStatusCard(tier = currentTier, remainingMillis = remainingMillis)
                        Spacer(modifier = Modifier.height(14.dp))
                    }
                }

                // ── Tier 1 ──
                AdTierCard(
                    title = "4 Hours Ad-Free",
                    subtitle = "1 short clip · 20 seconds",
                    icon = TablerIcons.Bolt,
                    iconTint = Mint,
                    isBestValue = false,
                    isActive = currentTier == 1,
                    buttonText = when (currentTier) {
                        0 -> "Watch 1 Ad"
                        1 -> "Active"
                        else -> "Included"
                    },
                    buttonStyle = when (currentTier) {
                        0 -> BtnStyle.Secondary
                        1 -> BtnStyle.Active
                        else -> BtnStyle.Muted
                    },
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        adTargetTier = 1
                        adStepIndex = 1
                        adTotalSteps = 1
                        isWebViewOpen = true
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // ── Tier 2 (Best value) ──
                AdTierCard(
                    title = "12 Hours Ad-Free",
                    subtitle = if (currentTier == 1) "Just 1 more clip to upgrade" else "2 short clips · 20 seconds each",
                    icon = TablerIcons.Flame,
                    iconTint = Amber,
                    isBestValue = true,
                    isActive = currentTier == 2,
                    buttonText = when (currentTier) {
                        2 -> "Active"
                        1 -> "Upgrade with 1 Ad"
                        else -> "Watch 2 Ads"
                    },
                    buttonStyle = if (currentTier == 2) BtnStyle.Active else BtnStyle.Primary,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        adTargetTier = 2
                        adStepIndex = 1
                        // Already on 4h → only 1 more ad to reach 12h total (never 16h)
                        adTotalSteps = if (currentTier == 1) 1 else 2
                        isWebViewOpen = true
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            // ── Premium upsell ──
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onDismiss()
                        onNavigateToPremium()
                    },
                shape = RoundedCornerShape(16.dp),
                color = Color.Transparent,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = TablerIcons.Crown,
                        contentDescription = null,
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Go Premium",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = "No ads, ever + offline MP3 export",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                    Icon(
                        imageVector = TablerIcons.ChevronRight,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    // ── In-app ad viewer ──
    if (isWebViewOpen) {
        DisposableEffect(Unit) {
            onDispose {
                webViewRef?.stopLoading()
                webViewRef?.destroy()
                webViewRef = null
            }
        }

        Dialog(
            onDismissRequest = {
                if (timeLeft <= 0) isWebViewOpen = false
            },
            properties = DialogProperties(
                dismissOnBackPress = (timeLeft <= 0),
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false
            )
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Top bar
                    Surface(color = SheetBg, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "SPONSORED",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        letterSpacing = 1.2.sp,
                                        color = Color.White.copy(alpha = 0.4f)
                                    )
                                    Text(
                                        text = if (adTotalSteps > 1) "Ad $adStepIndex of $adTotalSteps" else "Reward Ad",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                }

                                if (timeLeft > 0) {
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = Color.White.copy(alpha = 0.06f),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                                    ) {
                                        Text(
                                            text = "Reward in ${timeLeft}s",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White.copy(alpha = 0.85f),
                                            maxLines = 1,
                                            softWrap = false,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                        )
                                    }
                                } else {
                                    val hasNext = adStepIndex < adTotalSteps
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(
                                                if (hasNext) AccentBrush
                                                else Brush.horizontalGradient(listOf(Color(0xFF10B981), Mint))
                                            )
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                if (hasNext) {
                                                    adStepIndex++
                                                    timeLeft = TIER_AD_DURATION_SECONDS
                                                    pageLoaded = false
                                                    timerStarted = false
                                                    webViewRef?.loadUrl(AdManager.ADSTERRA_SMARTLINK)
                                                } else {
                                                    AdManager.grantTierAdFree(context, adTargetTier)
                                                    currentTier = AdManager.getAdFreeTier(context)
                                                    remainingTime = AdManager.getRemainingAdFreeTime(context)
                                                    isWebViewOpen = false
                                                    Toast.makeText(
                                                        context,
                                                        "🎉 ${if (adTargetTier == 2) "12 Hours" else "4 Hours"} Ad-Free Activated!",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                }
                                            }
                                            .padding(horizontal = 18.dp, vertical = 9.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (hasNext) "Next Ad" else "Claim Pass",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }

                            val progress = 1f - (timeLeft.toFloat() / TIER_AD_DURATION_SECONDS.toFloat())
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp),
                                color = Indigo,
                                trackColor = Color.White.copy(alpha = 0.08f)
                            )
                        }
                    }

                    // WebView
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    webViewRef = this
                                    @SuppressLint("SetJavaScriptEnabled")
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                                    webViewClient = object : WebViewClient() {
                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            super.onPageFinished(view, url)
                                            pageLoaded = true
                                            timerStarted = true
                                        }

                                        override fun onReceivedError(
                                            view: WebView?,
                                            request: WebResourceRequest?,
                                            error: WebResourceError?
                                        ) {
                                            super.onReceivedError(view, request, error)
                                            timerStarted = true
                                        }
                                    }
                                    loadUrl(AdManager.ADSTERRA_SMARTLINK)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

// ── Active pass card with live timer ─────────────────────
@Composable
private fun ActiveStatusCard(tier: Int, remainingMillis: Long) {
    val accent = if (tier == 2) Amber else Mint
    val total = if (tier == 2) TIER2_MILLIS else TIER1_MILLIS
    val progress = (remainingMillis.toFloat() / total.toFloat()).coerceIn(0f, 1f)

    val hours = remainingMillis / 3_600_000L
    val mins = (remainingMillis / 60_000L) % 60
    val secs = (remainingMillis / 1000L) % 60

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = accent.copy(alpha = 0.07f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(accent)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AD-FREE ACTIVE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.2.sp,
                            color = accent
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = String.format(Locale.US, "%02d:%02d:%02d", hours, mins, secs),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        style = TextStyle(fontFeatureSettings = "tnum")
                    )
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.White.copy(alpha = 0.06f)
                ) {
                    Text(
                        text = if (tier == 2) "12h pass" else "4h pass",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape),
                color = accent,
                trackColor = Color.White.copy(alpha = 0.08f)
            )
        }
    }
}

// ── Tier card: info on top, full-width button below (never squashed) ──
@Composable
private fun AdTierCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    isBestValue: Boolean,
    isActive: Boolean,
    buttonText: String,
    buttonStyle: BtnStyle,
    onClick: () -> Unit
) {
    val border = when {
        isActive -> BorderStroke(1.dp, iconTint.copy(alpha = 0.5f))
        isBestValue -> BorderStroke(1.dp, AccentBrush)
        else -> BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = CardBg,
        border = border
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(iconTint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    if (isBestValue) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Indigo.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "BEST VALUE",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp,
                                color = Color(0xFFA5B4FC),
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Text(
                        text = title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontSize = 12.5.sp,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            ActionButton(
                text = buttonText,
                style = buttonStyle,
                accent = iconTint,
                onClick = onClick
            )
        }
    }
}

// ── Full-width pill button ──
@Composable
private fun ActionButton(
    text: String,
    style: BtnStyle,
    accent: Color,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    val enabled = style == BtnStyle.Primary || style == BtnStyle.Secondary

    val bgModifier = when (style) {
        BtnStyle.Primary -> Modifier.background(AccentBrush)
        BtnStyle.Secondary -> Modifier.background(Color.White.copy(alpha = 0.09f))
        BtnStyle.Active -> Modifier.background(accent.copy(alpha = 0.12f))
        BtnStyle.Muted -> Modifier.background(Color.White.copy(alpha = 0.04f))
    }
    val textColor = when (style) {
        BtnStyle.Primary, BtnStyle.Secondary -> Color.White
        BtnStyle.Active -> accent
        BtnStyle.Muted -> Color.White.copy(alpha = 0.35f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(shape)
            .then(bgModifier)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (style == BtnStyle.Active) {
                Icon(
                    imageVector = TablerIcons.Check,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
