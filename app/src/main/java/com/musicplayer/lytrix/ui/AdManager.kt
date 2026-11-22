package com.musicplayer.lytrix.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color as AndroidGraphicsColor
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Environment
import android.util.Log
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.delay
import java.io.File
import android.view.ViewGroup

object AdManager {

    const val ADSTERRA_SMARTLINK = "https://www.effectivecpmnetwork.com/cz76bibkd?key=803540cda27a85c41ceffe30fb64dfd0"

    private var songClickCount = 0
    private const val AD_INTERVAL = 3            // প্রতি ৩ ক্লিকে ১টা ad
    private const val AD_DURATION_SECONDS = 10   // ১০ সেকেন্ড পর Skip বাটন

    // গান চেঞ্জ হলে এই ভেরিয়েবল আপডেট হবে
    private var currentPlayingSongId: String = ""
    // বর্তমান গানে ভিডিও অ্যাড স্কিপ করা হয়েছে কিনা তার ট্র্যাক
    private var isVideoAdSkippedForCurrentSong: Boolean = false

    /** ভিডিও বাটনের জন্য স্পেশাল অ্যাড ফাংশন */
    fun showAdForVideo(activity: Activity, songId: String, onAdDismissed: () -> Unit) {
        // যদি গান চেঞ্জ হয়ে থাকে, তাহলে আগের মেমোরি রিসেট করে দাও
        if (currentPlayingSongId != songId) {
            currentPlayingSongId = songId
            isVideoAdSkippedForCurrentSong = false
        }

        // যদি এই গানে অলরেডি স্কিপ করে থাকে, অথবা প্রিমিয়াম/অফলাইন হয়, তাহলে অ্যাড ছাড়াই ভিডিও দেখাবে
        if (isVideoAdSkippedForCurrentSong || isAdFree(activity) || !isNetworkAvailable(activity)) {
            onAdDismissed()
            return
        }

        // অ্যাড দেখাও, এবং স্কিপ করার পর ট্র্যাকার true করে দাও
        showAdInternal(activity, countClick = false) {
            isVideoAdSkippedForCurrentSong = true
            onAdDismissed()
        }
    }

    /** Context থেকে Activity বের করে ad চালায়। গান প্লে ও স্কিপিংয়ে সরাসরি action চালায়। */
    fun runWithAd(context: Context, action: () -> Unit) {
        action()
    }

    // গান স্কিপ ও চেঞ্জে কোনো ইন্টারস্টিশিয়াল এড দেখাবে না:
    fun showInterstitialAd(activity: Activity, onAdDismissed: () -> Unit) {
        onAdDismissed()
    }

    /** কাউন্টার ছাড়া সরাসরি ad (Video বাটনের জন্য) */
    fun showAdNow(activity: Activity, onAdDismissed: () -> Unit) =
        showAdInternal(activity, countClick = false, onAdDismissed = onAdDismissed)

    private fun showAdInternal(activity: Activity, countClick: Boolean, onAdDismissed: () -> Unit) {
        if (isAdFree(activity)) {
            Log.d("LytrixAd", "User is Ad-Free or Premium. Bypassing Ad.")
            onAdDismissed()
            return
        }
        if (!isNetworkAvailable(activity)) {
            Log.d("LytrixAd", "No network available. Bypassing Ad.")
            onAdDismissed()
            return
        }

        val shouldShow = if (countClick) {
            songClickCount++
            Log.d("LytrixAd", "Click Count: $songClickCount")
            songClickCount % AD_INTERVAL == 0
        } else {
            Log.d("LytrixAd", "Forced Ad (showAdNow) called.")
            true
        }

        if (shouldShow) {
            Log.d("LytrixAd", "Triggering Interstitial Ad...")
            val componentActivity = activity as? ComponentActivity
            if (componentActivity == null) {
                Log.e("LytrixAd", "Activity is not ComponentActivity. Bypassing Ad.")
                onAdDismissed()
                return
            }

            val dialog = Dialog(componentActivity, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            dialog.setCancelable(false)

            val composeView = ComposeView(componentActivity).apply {
                setViewTreeLifecycleOwner(componentActivity)
                setViewTreeViewModelStoreOwner(componentActivity)
                setViewTreeSavedStateRegistryOwner(componentActivity)

                setContent {
                    var timeLeft by remember { mutableIntStateOf(AD_DURATION_SECONDS) }
                    var pageLoaded by remember { mutableStateOf(false) }
                    var hasError by remember { mutableStateOf(false) }
                    var timerStarted by remember { mutableStateOf(false) }
                    var webViewRef by remember { mutableStateOf<WebView?>(null) }

                    // পেজ ৫ সেকেন্ডে লোড না হলেও টাইমার চালু হবে
                    LaunchedEffect(Unit) {
                        delay(5000)
                        if (!timerStarted) {
                            Log.d("LytrixAd", "Timeout reached (5s). Force starting timer.")
                            timerStarted = true
                        }
                    }
                    LaunchedEffect(pageLoaded) {
                        if (pageLoaded) {
                            Log.d("LytrixAd", "Page loaded successfully. Starting timer.")
                            timerStarted = true
                        }
                    }

                    LaunchedEffect(timerStarted, hasError) {
                        if (hasError) {
                            Log.e("LytrixAd", "Error loading Ad. Setting timeLeft to 0 to show Skip immediately.")
                            timeLeft = 0
                        } else if (timerStarted) {
                            while (timeLeft > 0) {
                                delay(1000)
                                timeLeft--
                            }
                            Log.d("LytrixAd", "Timer finished. Skip button is now visible.")
                        }
                    }

                    // 🔥 বাগ ফিক্স: শুধুমাত্র এখানেই destroy হবে।
                    DisposableEffect(Unit) {
                        onDispose {
                            Log.d("LytrixAd", "DisposableEffect: Ad Dialog closed. Cleaning up WebView.")
                            webViewRef?.let {
                                it.stopLoading()
                                it.destroy()
                            }
                        }
                    }

                    val canSkip = timeLeft <= 0

                    // 🔥 বাগ ফিক্স: windowInsetsPadding সরানো হয়েছে যাতে ফুলস্ক্রিন হয়
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    webViewClient = object : WebViewClient() {
                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            pageLoaded = true
                                        }
                                        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                                            if (request?.isForMainFrame == true) {
                                                Log.e("LytrixAd", "WebView Error: ${error?.description}")
                                                hasError = true
                                            }
                                        }
                                    }
                                    loadUrl(ADSTERRA_SMARTLINK)
                                    webViewRef = this
                                }
                            }
                        )

                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .systemBarsPadding() // শুধুমাত্র বাটনে প্যাডিং থাকবে
                                .padding(top = 16.dp, end = 16.dp)
                        ) {
                            if (!canSkip) {
                                Box(
                                    modifier = Modifier
                                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = if (!timerStarted) "Loading Ad..." else "Skip in ${timeLeft}s",
                                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp
                                    )
                                }
                            } else {
                                Row(
                                    modifier = Modifier
                                        .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(16.dp))
                                        .clickable {
                                            Log.d("LytrixAd", "Skip Clicked. Stopping WebView logic.")
                                            // 🔥 বাগ ফিক্স: এখানে destroy হবে না, শুধু ব্ল্যাংক করে ক্লিয়ার করা হবে
                                            webViewRef?.let {
                                                it.stopLoading()
                                                it.loadUrl("about:blank")
                                                it.clearHistory()
                                            }
                                            dialog.dismiss()

                                            // 300ms delay
                                            Log.d("LytrixAd", "Waiting 300ms before dispatching onAdDismissed")
                                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                                Log.d("LytrixAd", "Calling onAdDismissed() -> Song should play now")
                                                onAdDismissed()
                                            }, 300)
                                        }
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Skip Ad", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(Icons.Default.SkipNext, contentDescription = "Skip", tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
            dialog.setOnDismissListener { composeView.disposeComposition() }
            dialog.setContentView(composeView)
            dialog.show()
        } else {
            onAdDismissed()
        }
    }

    val isAdFreeFlow = kotlinx.coroutines.flow.MutableStateFlow(false)

    fun refreshAdFreeState(context: Context) {
        val result = isAdFree(context)
        isAdFreeFlow.value = result
    }

    /** প্রিমিয়াম (subscription/lifetime) অথবা Ad-Free পাস থাকলে true */
    fun isAdFree(context: Context): Boolean {
        val prefs = context.getSharedPreferences("premium_prefs", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val result = prefs.getBoolean("is_permanent", false) ||
                now < prefs.getLong("ad_free_expiry", 0L) ||
                now < prefs.getLong("subscription_expiry", 0L)
        if (isAdFreeFlow.value != result) {
            isAdFreeFlow.value = result
        }
        return result
    }

    fun hasPremiumFeatures(context: Context): Boolean {
        val prefs = context.getSharedPreferences("premium_prefs", Context.MODE_PRIVATE)
        val expiryTime = prefs.getLong("subscription_expiry", 0L)
        return System.currentTimeMillis() < expiryTime
    }

    fun getAdFreeTier(context: Context): Int {
        val prefs = context.getSharedPreferences("premium_prefs", Context.MODE_PRIVATE)
        val expiry = prefs.getLong("ad_free_expiry", 0L)
        if (System.currentTimeMillis() >= expiry) return 0
        return prefs.getInt("ad_free_tier", 1)
    }

    fun getRemainingAdFreeMillis(context: Context): Long {
        val prefs = context.getSharedPreferences("premium_prefs", Context.MODE_PRIVATE)
        val expiry = prefs.getLong("ad_free_expiry", 0L)
        val diff = expiry - System.currentTimeMillis()
        return if (diff > 0) diff else 0L
    }

    fun grantTierAdFree(context: Context, tier: Int) {
        val prefs = context.getSharedPreferences("premium_prefs", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val durationMillis = if (tier == 2) 12 * 3600 * 1000L else 4 * 3600 * 1000L
        val newExpiry = now + durationMillis
        prefs.edit()
            .putLong("ad_free_expiry", newExpiry)
            .putInt("ad_free_tier", tier)
            .apply()
        isAdFreeFlow.value = true
        Log.d("LytrixAd", "Granted Tier $tier Ad Free Pass until $newExpiry.")
    }

    fun grantOneHourAdFree(context: Context) {
        val prefs = context.getSharedPreferences("premium_prefs", Context.MODE_PRIVATE)
        val currentExpiry = prefs.getLong("ad_free_expiry", 0L)
        val baseTime = if (currentExpiry > System.currentTimeMillis()) currentExpiry else System.currentTimeMillis()
        val newExpiry = baseTime + 3600000L
        prefs.edit().putLong("ad_free_expiry", newExpiry).apply()
        isAdFreeFlow.value = true
        Log.d("LytrixAd", "Granted 1-Hour Ad Free Pass.")
    }

    fun getRemainingAdFreeTime(context: Context): String {
        val prefs = context.getSharedPreferences("premium_prefs", Context.MODE_PRIVATE)
        val expiry = prefs.getLong("ad_free_expiry", 0L)
        val diff = expiry - System.currentTimeMillis()
        if (diff <= 0) return ""
        val minutes = (diff / 1000) / 60
        return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m left" else "${minutes}m left"
    }

    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return when {
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            else -> false
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

// ==========================================
// 🔥 Ad-gated play helpers (PlayerViewModel extensions)
// ডাউনলোড করা গান/পডকাস্টে ad আসবে না, রেডিওতেও না
// ==========================================
private fun adSafeName(name: String) = name.replace(Regex("[^a-zA-Z0-9 ]"), "_").take(50).trim()

private fun adIsMusicDownloaded(context: Context, title: String): Boolean =
    File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "${adSafeName(title)}.mp3").exists()

private fun adIsPodcastDownloaded(context: Context, id: String?): Boolean {
    if (id == null) return false
    val dir = context.getExternalFilesDir(Environment.DIRECTORY_PODCASTS)
    return File(dir, "$id.mp3").exists() || File(dir, "AB_$id.mp3").exists()
}

fun PlayerViewModel.playOnlineSongWithAd(
    context: Context,
    song: com.musicplayer.lytrix.model.JioSaavnSong,
    list: List<com.musicplayer.lytrix.model.JioSaavnSong>
) {
    playOnlineSong(song, list)
}

fun PlayerViewModel.playLytrixSongWithAd(
    song: com.musicplayer.lytrix.LytrixSongItem,
    context: Context,
    channelId: String? = null,
    mediaType: String? = null,
) {
    playLytrixSong(song, context, channelId, mediaType)
}

fun PlayerViewModel.playLytrixPlaylistWithAd(
    list: List<PlayerViewModel.YtMusicTrack>,
    index: Int,
    context: Context,
    albumTitle: String? = null
) {
    playLytrixPlaylist(list, index, context, albumTitle)
}

fun PlayerViewModel.playPodcastWithAd(
    context: Context,
    episode: com.musicplayer.lytrix.PodcastEpisode,
    list: List<com.musicplayer.lytrix.PodcastEpisode>,
    isRadio: Boolean = false
) {
    playPodcast(episode, list, isRadio)
}

fun PlayerViewModel.playAudiobookWithAd(
    context: Context,
    chapter: com.musicplayer.lytrix.PodcastEpisode,
    list: List<com.musicplayer.lytrix.PodcastEpisode>
) {
    playAudiobook(chapter, list)
}

@SuppressLint("SetJavaScriptEnabled")
private fun createBannerWebView(ctx: Context): WebView {
    return WebView(ctx).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadsImagesAutomatically = true
        settings.blockNetworkImage = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        setBackgroundColor(AndroidGraphicsColor.TRANSPARENT)
        val htmlData = """
            <!DOCTYPE html>
            <html>
            <head><style>body { margin: 0; padding: 0; display: flex; justify-content: center; align-items: center; background-color: transparent; }</style></head>
            <body>
                <script>atOptions = {'key' : '90668d6e56533e0c9fdb600aa04d2dc4','format' : 'iframe','height' : 50,'width' : 320,'params' : {}};</script>
                <script src="https://www.highperformanceformat.com/90668d6e56533e0c9fdb600aa04d2dc4/invoke.js"></script>
            </body>
            </html>
        """.trimIndent()
        loadDataWithBaseURL("https://lytrixplayer.blogspot.com", htmlData, "text/html", "UTF-8", null)
    }
}

/** ব্যানারের WebView লিস্টের বাইরে ধরে রাখে, যাতে স্ক্রলে রিলোড না হয় */
object BannerCache {
    private const val MAX_SIZE = 10
    private val map = LinkedHashMap<String, WebView>(16, 0.75f, true)

    fun get(key: String, context: Context): WebView {
        map[key]?.let { existing ->
            (existing.parent as? ViewGroup)?.removeView(existing)   // আগের holder থেকে ছাড়িয়ে নেওয়া
            return existing
        }
        val webView = createBannerWebView(context)
        map[key] = webView
        trim(keep = key)
        return webView
    }

    private fun trim(keep: String) {
        if (map.size <= MAX_SIZE) return
        val iterator = map.entries.iterator()
        while (map.size > MAX_SIZE && iterator.hasNext()) {
            val entry = iterator.next()
            val view = entry.value
            if (entry.key != keep && !view.isAttachedToWindow) {   // স্ক্রিনে থাকা ব্যানার মুছবে না
                (view.parent as? ViewGroup)?.removeView(view)
                view.stopLoading()
                view.destroy()
                iterator.remove()
            }
        }
    }

    fun clear() {
        map.values.forEach {
            (it.parent as? ViewGroup)?.removeView(it)
            it.stopLoading()
            it.destroy()
        }
        map.clear()
    }
}

@Composable
fun BannerAdView(modifier: Modifier = Modifier, slot: String? = null) {
    val context = LocalContext.current
    val isAdFreeLive by AdManager.isAdFreeFlow.collectAsState()
    if (!isAdFreeLive && !AdManager.isAdFree(context) && AdManager.isNetworkAvailable(context)) {
        Box(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            AndroidView(
                modifier = Modifier.width(320.dp).height(50.dp),
                factory = { ctx ->
                    if (slot != null) BannerCache.get(slot, ctx) else createBannerWebView(ctx)
                }
            )
        }
    }
}