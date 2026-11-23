package com.musicplayer.lytrix



import android.annotation.SuppressLint

import android.content.BroadcastReceiver

import android.content.Context

import android.content.Intent

import android.content.IntentFilter

import android.content.pm.PackageManager

import android.os.Build

import android.os.Bundle

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.graphics.drawable.Icon
import android.view.WindowManager

import androidx.activity.ComponentActivity

import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler

import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.animation.AnimatedVisibility

import androidx.compose.animation.core.Spring

import androidx.compose.animation.core.spring

import androidx.compose.animation.fadeIn

import androidx.compose.animation.fadeOut

import androidx.compose.animation.slideInVertically

import androidx.compose.animation.slideOutVertically

import androidx.compose.foundation.background

import androidx.compose.foundation.gestures.detectHorizontalDragGestures

import androidx.compose.foundation.layout.*

import androidx.compose.foundation.shape.CircleShape

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight

import androidx.compose.material.icons.filled.Pause

import androidx.compose.material.icons.filled.PlayArrow

import androidx.compose.material.icons.filled.SkipNext

import androidx.compose.material.icons.filled.SkipPrevious

import androidx.compose.material3.*

import androidx.compose.runtime.*

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex

import androidx.compose.ui.draw.clip

import androidx.compose.ui.graphics.Color

import androidx.compose.ui.graphics.graphicsLayer

import androidx.compose.ui.input.pointer.pointerInput

import androidx.compose.ui.layout.ContentScale

import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.text.style.TextOverflow

import androidx.compose.ui.unit.IntOffset

import androidx.compose.ui.unit.dp

import androidx.compose.ui.unit.sp

import androidx.core.content.ContextCompat

import androidx.core.view.WindowCompat

import androidx.lifecycle.viewmodel.compose.viewModel

import androidx.navigation.compose.NavHost

import androidx.navigation.compose.composable

import androidx.navigation.compose.currentBackStackEntryAsState

import androidx.navigation.compose.rememberNavController

import coil.Coil

import coil.ImageLoader

import coil.compose.AsyncImage

import coil.decode.VideoFrameDecoder

import coil.decode.GifDecoder

import coil.decode.ImageDecoderDecoder

import coil.memory.MemoryCache

import coil.request.ImageRequest

import com.musicplayer.lytrix.ui.*

import com.musicplayer.lytrix.ui.theme.MyApplicationTheme

import kotlinx.coroutines.delay

import kotlinx.coroutines.flow.first

import kotlinx.coroutines.runBlocking

import java.text.SimpleDateFormat

import java.util.Date

import java.util.Locale

import kotlin.math.roundToInt

import androidx.compose.foundation.lazy.LazyColumn

import androidx.compose.foundation.lazy.items

import androidx.compose.foundation.clickable

import androidx.compose.foundation.layout.heightIn



class MainActivity : ComponentActivity() {



    companion object {

        const val ACTION_PIP_PLAY_PAUSE = "com.musicplayer.lytrix.pip.PLAY_PAUSE"
        const val ACTION_PIP_PREV = "com.musicplayer.lytrix.pip.PREV"
        const val ACTION_PIP_NEXT = "com.musicplayer.lytrix.pip.NEXT"

        @SuppressLint("StaticFieldLeak")

        var instance: MainActivity? = null

    }

    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_PIP_PLAY_PAUSE -> {
                    playerViewModel?.playPause()
                }
                ACTION_PIP_PREV -> {
                    playerViewModel?.skipPrevious()
                }
                ACTION_PIP_NEXT -> {
                    playerViewModel?.skipNext()
                }
            }
        }
    }

    fun updatePipActions(isPlaying: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val actions = mutableListOf<RemoteAction>()

            // 1. Previous
            val prevPendingIntent = PendingIntent.getBroadcast(
                this,
                101,
                Intent(ACTION_PIP_PREV).setPackage(packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            actions.add(
                RemoteAction(
                    Icon.createWithResource(this, android.R.drawable.ic_media_previous),
                    "Previous",
                    "Previous",
                    prevPendingIntent
                )
            )

            // 2. Play / Pause
            val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            val playPauseTitle = if (isPlaying) "Pause" else "Play"
            val playPausePendingIntent = PendingIntent.getBroadcast(
                this,
                102,
                Intent(ACTION_PIP_PLAY_PAUSE).setPackage(packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            actions.add(
                RemoteAction(
                    Icon.createWithResource(this, playPauseIcon),
                    playPauseTitle,
                    playPauseTitle,
                    playPausePendingIntent
                )
            )

            // 3. Next
            val nextPendingIntent = PendingIntent.getBroadcast(
                this,
                103,
                Intent(ACTION_PIP_NEXT).setPackage(packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            actions.add(
                RemoteAction(
                    Icon.createWithResource(this, android.R.drawable.ic_media_next),
                    "Next",
                    "Next",
                    nextPendingIntent
                )
            )

            runCatching {
                val params = PictureInPictureParams.Builder()
                    .setActions(actions)
                    .build()
                setPictureInPictureParams(params)
            }
        }
    }

    var playerViewModel: PlayerViewModel? = null

    var isInPipMode by androidx.compose.runtime.mutableStateOf(false)
        private set

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipMode = isInPictureInPictureMode
        if (isInPictureInPictureMode) {
            updatePipActions(playerViewModel?.isPlaying?.value ?: true)
        }
    }

    private var isNowPlayingExpandedGlobal = false
    private var closeNowPlayingGlobal: (() -> Unit)? = null
    var nowPlayingBackHandler: (() -> Boolean)? = null

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        android.util.Log.d("BACKDBG", "3. MainActivity.onBackPressed() called. isNowPlayingExpandedGlobal=$isNowPlayingExpandedGlobal")
        if (isNowPlayingExpandedGlobal) {
            if (nowPlayingBackHandler?.invoke() == true) {
                return
            }
            if (closeNowPlayingGlobal != null) {
                closeNowPlayingGlobal?.invoke()
                return
            }
        }
        android.util.Log.d("BACKDBG", "MainActivity: super.onBackPressed() called")
        super.onBackPressed()
    }

    private val pendingIntentState = androidx.compose.runtime.mutableStateOf<Intent?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingIntentState.value = intent
    }



    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        pendingIntentState.value = intent

        // 🔥 NEW GUARD - Unverified email user থাকলে সাথে সাথে signOut

        try {

            val auth = com.google.firebase.auth.FirebaseAuth.getInstance()

            val user = auth.currentUser

            if (user != null) {

                val isPasswordUser = user.providerData.any { it.providerId == "password" }

                if (isPasswordUser && !user.isEmailVerified) {

                    auth.signOut()

                }

            }

        } catch (_: Exception) {}



        instance = this

        val pipFilter = IntentFilter().apply {
            addAction(ACTION_PIP_PLAY_PAUSE)
            addAction(ACTION_PIP_PREV)
            addAction(ACTION_PIP_NEXT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(pipReceiver, pipFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(pipReceiver, pipFilter)
        }



        AnalyticsManager.init(this)



        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        window.statusBarColor = android.graphics.Color.TRANSPARENT

        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            window.isNavigationBarContrastEnforced = false

        }

        if (com.musicplayer.lytrix.ui.PrivacyPreferencesManager.isDisableScreenshot(this)) {

            window.setFlags(

                android.view.WindowManager.LayoutParams.FLAG_SECURE,

                android.view.WindowManager.LayoutParams.FLAG_SECURE

            )

        }



        val imageLoader = ImageLoader.Builder(this)
            .components {
                add(com.musicplayer.lytrix.utils.VideoArtworkFallbackInterceptor())
                add(VideoFrameDecoder.Factory())
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.2).build() }
            .build()
        Coil.setImageLoader(imageLoader)



        // ── Meld-এর মতো: setContent শুরু হওয়ার আগেই কুকি সেট করা হচ্ছে ──

        // এটা না করলে Race Condition হয়: loadArchiveToonData() কুকি সেট হওয়ার

        // আগেই চলে যায়, ফলে লগিন থাকলেও anonymous ডেটা আসে।

        runBlocking {

            try {

                val prefManager = com.musicplayer.lytrix.ui.YoutubePreferencesManager(this@MainActivity)

                val cookie = prefManager.cookie.first()

                val visitorData = prefManager.visitorData.first()

                val dataSyncId = prefManager.dataSyncId.first()

                val authUser = prefManager.authUser.first()

                val useLoginForBrowse = prefManager.useLoginForBrowse.first()

                if (cookie.isNotBlank()) {

                    com.musicplayer.lytrix.innertube.YouTube.authState =

                        com.musicplayer.lytrix.innertube.PlaybackAuthState(

                            cookie = cookie,

                            visitorData = visitorData,

                            dataSyncId = dataSyncId,

                            authUser = authUser

                        )

                    com.musicplayer.lytrix.innertube.YouTube.useLoginForBrowse = useLoginForBrowse

                }

            } catch (_: Exception) {}

        }



        setContent {

            val playerViewModel: PlayerViewModel = viewModel()

            this@MainActivity.playerViewModel = playerViewModel

            val context = LocalContext.current

            val activity = context as? android.app.Activity



            // 🔥 ফায়ারবেস ওয়ার্ম-আপ (অ্যাপ খুললেই কানেকশন রেডি করে রাখবে)

            LaunchedEffect(Unit) {

                try {

                    com.musicplayer.lytrix.ui.performSilentSync(context)

                    com.google.firebase.database.FirebaseDatabase.getInstance("https://lytrix-player-default-rtdb.asia-southeast1.firebasedatabase.app").reference.child(".info/connected").keepSynced(true)

                } catch (e: Exception) { e.printStackTrace() }

            }



            // Updates checker (Checks for updates in background & notifies if available)
            LaunchedEffect(Unit) {
                try {
                    delay(3000)
                    if (com.musicplayer.lytrix.update.AppUpdateManager.isAutoCheckEnabled(context)) {
                        com.musicplayer.lytrix.update.AppUpdateManager.check(context, notify = true)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }



            val dataStoreManager = remember { DataStoreManager(context) }

            val youtubePrefManager = remember { com.musicplayer.lytrix.ui.YoutubePreferencesManager(context) }

            val cookieFlow by youtubePrefManager.cookie.collectAsState(initial = "")

            val visitorDataFlow by youtubePrefManager.visitorData.collectAsState(initial = "")

            val dataSyncIdFlow by youtubePrefManager.dataSyncId.collectAsState(initial = "")

            val authUserFlow by youtubePrefManager.authUser.collectAsState(initial = "")

            val useLoginForBrowseFlow by youtubePrefManager.useLoginForBrowse.collectAsState(initial = true)

            val ytmSyncFlow by youtubePrefManager.ytmSync.collectAsState(initial = true)



            // লগিন/লগআউট হলে authState আপডেট রাখে।

            // copy() ব্যবহার করা হচ্ছে যাতে poToken বা অন্য ফিল্ড মুছে না যায়।

            LaunchedEffect(useLoginForBrowseFlow) { com.musicplayer.lytrix.innertube.YouTube.useLoginForBrowse = useLoginForBrowseFlow }

            LaunchedEffect(cookieFlow, visitorDataFlow, dataSyncIdFlow, authUserFlow, ytmSyncFlow) {

                com.musicplayer.lytrix.innertube.YouTube.authState =

                    com.musicplayer.lytrix.innertube.YouTube.authState.copy(

                        cookie = cookieFlow.ifBlank { null },

                        visitorData = visitorDataFlow.ifBlank { null },

                        dataSyncId = dataSyncIdFlow.ifBlank { null },

                        authUser = authUserFlow.ifBlank { null }

                    )

                if (cookieFlow.isNotBlank()) {

                    playerViewModel.loadArchiveToonData(null)

                    if (ytmSyncFlow) {

                        com.musicplayer.lytrix.sync.YoutubeSyncManager.syncAll(context)

                    }

                } else if (!ytmSyncFlow || cookieFlow.isBlank()) {

                    com.musicplayer.lytrix.sync.YoutubeSyncManager.clearYtmSyncData(context)

                }

            }



            val keepScreenOn by dataStoreManager.keepScreenOnFlow.collectAsState(initial = false)

            val lockScreenPlaying by dataStoreManager.lockScreenPlayingFlow.collectAsState(initial = false)

            val useNewPlayerDesign by dataStoreManager.useNewPlayerDesignFlow.collectAsState(initial = true)
            val playerStyle by dataStoreManager.playerStyleFlow.collectAsState(initial = com.musicplayer.lytrix.ui.DataStoreManager.PLAYER_STYLE_MATERIAL3)

            var showCustomLockScreen by remember { mutableStateOf(false) }



            LaunchedEffect(keepScreenOn) {

                if (keepScreenOn) {

                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

                } else {

                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

                }

            }



            LaunchedEffect(lockScreenPlaying) {

                if (lockScreenPlaying) {

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {

                        setShowWhenLocked(true)

                    } else {

                        @Suppress("DEPRECATION")

                        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)

                    }

                } else {

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {

                        setShowWhenLocked(false)

                    } else {

                        @Suppress("DEPRECATION")

                        window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)

                    }

                }

            }



            DisposableEffect(Unit) {

                val receiver = object : BroadcastReceiver() {

                    override fun onReceive(context: Context?, intent: Intent?) {

                        if (intent?.action == Intent.ACTION_SCREEN_OFF) {

                            val isPlaying = playerViewModel.isPlaying.value

                            if (lockScreenPlaying && isPlaying) {

                                showCustomLockScreen = true

                            }

                        }

                    }

                }

                val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)

                context.registerReceiver(receiver, filter)

                onDispose { context.unregisterReceiver(receiver) }

            }



            MyApplicationTheme {

                val permissionsToRequest =

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

                        arrayOf(

                            android.Manifest.permission.READ_MEDIA_AUDIO,

                            android.Manifest.permission.READ_MEDIA_VIDEO,

                            android.Manifest.permission.READ_MEDIA_IMAGES,

                            android.Manifest.permission.POST_NOTIFICATIONS

                        )

                    } else {

                        arrayOf(

                            android.Manifest.permission.READ_EXTERNAL_STORAGE,

                            android.Manifest.permission.WRITE_EXTERNAL_STORAGE

                        )

                    }



                val permissionLauncher =

                    androidx.activity.compose.rememberLauncherForActivityResult(

                        contract = ActivityResultContracts.RequestMultiplePermissions()

                    ) { permissions ->

                        if (permissions.values.any { it }) playerViewModel.loadAndScanSongs(context)

                    }



                LaunchedEffect(Unit) {

                    val allGranted = permissionsToRequest.all {

                        ContextCompat.checkSelfPermission(

                            context,

                            it

                        ) == PackageManager.PERMISSION_GRANTED

                    }

                    if (!allGranted) permissionLauncher.launch(permissionsToRequest) else playerViewModel.loadAndScanSongs(

                        context

                    )

                }



                val navController = rememberNavController()

                val currentSong by playerViewModel.currentSong.collectAsState()

                val isPlaying by playerViewModel.isPlaying.collectAsState()

                LaunchedEffect(isPlaying, isInPipMode) {
                    if (isInPipMode) {
                        updatePipActions(isPlaying)
                    }
                }

                val isBuffering by playerViewModel.isBuffering.collectAsState()

                val currentPosition by playerViewModel.currentPosition.collectAsState()

                val duration by playerViewModel.duration.collectAsState()

                val repeatMode by playerViewModel.repeatMode.collectAsState()

                val shuffleEnabled by playerViewModel.shuffleModeEnabled.collectAsState()

                val currentLyrics by playerViewModel.currentLyrics.collectAsState()

                val favoriteSongIds by playerViewModel.favoriteSongIds.collectAsState()

                val playlists by playerViewModel.playlists.collectAsState()

                val navBackStackEntry by navController.currentBackStackEntryAsState()

                val currentRoute = navBackStackEntry?.destination?.route



                var isNowPlayingExpanded by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    com.musicplayer.lytrix.ui.AdManager.refreshAdFreeState(applicationContext)
                    com.musicplayer.lytrix.ui.PremiumSyncManager.start(applicationContext)
                    try {
                        com.google.firebase.auth.FirebaseAuth.getInstance().addAuthStateListener { auth ->
                            if (auth.currentUser != null) {
                                com.musicplayer.lytrix.ui.PremiumSyncManager.start(applicationContext)
                            } else {
                                com.musicplayer.lytrix.ui.PremiumSyncManager.stop()
                            }
                        }
                    } catch (_: Exception) {}
                }

                // 🔄 VIP Lounge (Firebase) ১০ মিনিটের সেশন অটো-রিস্টোর
                LaunchedEffect(Unit) {
                    playerViewModel.restoreFirebaseSessionIfValid(context) { roomCode ->
                        if (navController.currentDestination?.route != "listen_together_firebase") {
                            navController.navigate("listen_together_firebase")
                        }
                        android.widget.Toast.makeText(context, "Reconnected to room $roomCode", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }

                // 📱 লাইফসাইকেল ভিত্তিক প্রেজেন্স (অন-স্ক্রিনে থাকলে Online 🟢, ব্যাকগ্রাউন্ড/লক হলে Away 💤)
                DisposableEffect(Unit) {
                    val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                        when (event) {
                            androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                                playerViewModel.setRoomPresence(true)
                            }
                            androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                                playerViewModel.setRoomPresence(false)
                            }
                            else -> {}
                        }
                    }
                    val lifecycle = this@MainActivity.lifecycle
                    lifecycle.addObserver(observer)
                    onDispose {
                        lifecycle.removeObserver(observer)
                    }
                }

                SideEffect {
                    isNowPlayingExpandedGlobal = isNowPlayingExpanded
                    closeNowPlayingGlobal = { isNowPlayingExpanded = false }
                }

                val globalSnackbarHostState = remember { SnackbarHostState() }

                LaunchedEffect(playerViewModel) {

                    playerViewModel.snackbarEvent.collect { event: com.musicplayer.lytrix.ui.PlayerViewModel.UndoSnackbarEvent ->

                        globalSnackbarHostState.currentSnackbarData?.dismiss()

                        val result = globalSnackbarHostState.showSnackbar(

                            message = event.message,

                            actionLabel = "Undo",

                            duration = SnackbarDuration.Short

                        )

                        if (result == SnackbarResult.ActionPerformed) {

                            playerViewModel.undoNotInterested(event.songId)

                            android.widget.Toast.makeText(this@MainActivity, "Recommendation restored", android.widget.Toast.LENGTH_SHORT).show()

                        }

                    }

                }

                var returnToNowPlaying by remember { mutableStateOf(false) }

                var isMenuSheetOpen by remember { mutableStateOf(false) }

                var newOnlinePlaylistName by remember { mutableStateOf("") }

                var currentSheetType by remember { mutableStateOf(BottomSheetType.MAIN) }

                val accentColor = com.musicplayer.lytrix.ui.theme.LocalAccentColor.current



                // 🔥 PREMIUM DIALOG STATE (Added)

                var showPremiumDialog by remember { mutableStateOf(false) }



                var videoToPlay by remember { mutableStateOf<com.musicplayer.lytrix.model.Video?>(null) }



                val currentPlaybackSpeed by playerViewModel.currentPlaybackSpeed.collectAsState()

                val currentSleepTimerMinutes by playerViewModel.currentSleepTimerMinutes.collectAsState()

                val playLastSongToEnd by playerViewModel.playLastSongToEnd.collectAsState()

                val pointA by playerViewModel.pointA.collectAsState()

                val pointB by playerViewModel.pointB.collectAsState()

                var isPowerSavingMode by remember { mutableStateOf(false) }
                var aodInitialLocked by remember { mutableStateOf(false) }

                DisposableEffect(Unit) {
                    com.musicplayer.lytrix.aod.AodLaunchTrigger.onRequestAod = { locked ->
                        aodInitialLocked = locked
                        isPowerSavingMode = true
                    }
                    onDispose {
                        com.musicplayer.lytrix.aod.AodLaunchTrigger.onRequestAod = null
                    }
                }



                var showTrashConfirmForCurrentSong by remember { mutableStateOf(false) }



                val currentPendingIntent = pendingIntentState.value
                androidx.compose.runtime.LaunchedEffect(currentPendingIntent) {
                    val targetIntent = currentPendingIntent ?: return@LaunchedEffect

                    val action = targetIntent.action
                    if (action == com.musicplayer.lytrix.shazam.MusicRecognizerTileService.ACTION_RECOGNITION) {
                        isNowPlayingExpanded = false
                        if (navController.currentDestination?.route != "player") {
                            if (!navController.popBackStack("player", inclusive = false)) {
                                navController.navigate("player") { launchSingleTop = true }
                            }
                        }
                        playerViewModel.requestRecognitionFromTile()
                        pendingIntentState.value = null
                        return@LaunchedEffect
                    }
                    if (action == com.musicplayer.lytrix.aod.AodPreferences.ACTION_AOD_MODE) {
                        aodInitialLocked = false
                        isPowerSavingMode = true
                        pendingIntentState.value = null
                        return@LaunchedEffect
                    }
                    if (action == com.musicplayer.lytrix.widget.WidgetConstants.ACTION_OPEN_INSIGHTS) {
                        isNowPlayingExpanded = false
                        navController.navigate("listening_insights") { launchSingleTop = true }
                        pendingIntentState.value = null
                        return@LaunchedEffect
                    }
                    if (action == com.musicplayer.lytrix.widget.WidgetConstants.ACTION_OPEN_PLAYLIST) {
                        val plType = targetIntent.getStringExtra(com.musicplayer.lytrix.widget.WidgetConstants.EXTRA_PLAYLIST_TYPE) ?: "liked"
                        val plId = targetIntent.getStringExtra(com.musicplayer.lytrix.widget.WidgetConstants.EXTRA_PLAYLIST_ID) ?: "-1"
                        isNowPlayingExpanded = false
                        navController.navigate("playlist/$plType/${android.net.Uri.encode(plId)}") { launchSingleTop = true }
                        pendingIntentState.value = null
                        return@LaunchedEffect
                    }
                    if (action == com.musicplayer.lytrix.alarm.MusicAlarmScheduler.ACTION_OPEN_ALARM_PLAY) {
                        com.musicplayer.lytrix.alarm.MusicAlarmReceiver.stopFallbackRingtone()
                        val alarmId = targetIntent.getStringExtra(com.musicplayer.lytrix.alarm.MusicAlarmScheduler.EXTRA_ALARM_ID)
                        val alarm = alarmId?.let { com.musicplayer.lytrix.alarm.MusicAlarmStore.getById(this@MainActivity, it) }
                        val alreadyPlaying = com.musicplayer.lytrix.service.PlaybackService.isCurrentlyPlaying()
                        if (alarm != null && !alreadyPlaying) {
                            playerViewModel.playMusicAlarm(this@MainActivity, alarm)
                        }
                        isNowPlayingExpanded = true
                        pendingIntentState.value = null
                        return@LaunchedEffect
                    }
                    val uri = targetIntent.data
                    val sharedText = if (action == Intent.ACTION_SEND && targetIntent.type == "text/plain") {
                        targetIntent.getStringExtra(Intent.EXTRA_TEXT)
                    } else null

                    val targetString = sharedText ?: uri?.toString()

                    // 🎧 Handle Lytrix Listen Together Deep Link (Custom Scheme & Blogger URL)
                    val uriScheme = uri?.scheme?.lowercase()
                    val uriHost = uri?.host?.lowercase()
                    val isLytrixDeepLink = (uriScheme == "lytrix" && uriHost == "listen") ||
                            (uriHost != null && (uriHost == "lytrixteam.blogspot.com" || uriHost.endsWith(".blogspot.com") || uriHost == "lytrix.app"))

                    if (isLytrixDeepLink && uri != null) {
                        val roomCode = uri.getQueryParameter("code")
                        val serverType = uri.getQueryParameter("type")?.lowercase() ?: "metro"

                        if (!roomCode.isNullOrBlank()) {
                            val cleanCode = roomCode.trim().uppercase()
                            isNowPlayingExpanded = false
                            if (serverType == "firebase") {
                                playerViewModel.setPendingRoomCode(cleanCode)
                                navController.navigate("listen_together_firebase")
                                val savedName = playerViewModel.getSavedUserName(this@MainActivity)
                                if (savedName.isNotBlank()) {
                                    playerViewModel.requestToJoinRoom(this@MainActivity, cleanCode, savedName)
                                }
                            } else {
                                navController.navigate("listen_together_metro")
                                val metroName = playerViewModel.metroManager.username.value.ifBlank {
                                    playerViewModel.getSavedUserName(this@MainActivity).ifBlank { "User" }
                                }
                                playerViewModel.metroManager.joinRoom(cleanCode, metroName)
                            }
                            pendingIntentState.value = null
                            return@LaunchedEffect
                        }
                    }

                    // Also check if text shared contains a room invite link
                    if (sharedText != null && sharedText.contains("code=")) {
                        val parsedUri = try { android.net.Uri.parse(sharedText.substringAfter("http").let { "http$it" }) } catch (e: Exception) { null }
                        val roomCode = parsedUri?.getQueryParameter("code")
                            ?: Regex("[?&]code=([A-Za-z0-9]+)").find(sharedText)?.groupValues?.getOrNull(1)
                        if (!roomCode.isNullOrBlank()) {
                            val serverType = parsedUri?.getQueryParameter("type")?.lowercase()
                                ?: if (sharedText.contains("firebase", ignoreCase = true) || sharedText.contains("VIP", ignoreCase = true)) "firebase" else "metro"
                            val cleanCode = roomCode.trim().uppercase()
                            isNowPlayingExpanded = false
                            if (serverType == "firebase") {
                                playerViewModel.setPendingRoomCode(cleanCode)
                                navController.navigate("listen_together_firebase")
                                val savedName = playerViewModel.getSavedUserName(this@MainActivity)
                                if (savedName.isNotBlank()) {
                                    playerViewModel.requestToJoinRoom(this@MainActivity, cleanCode, savedName)
                                }
                            } else {
                                navController.navigate("listen_together_metro")
                                val metroName = playerViewModel.metroManager.username.value.ifBlank {
                                    playerViewModel.getSavedUserName(this@MainActivity).ifBlank { "User" }
                                }
                                playerViewModel.metroManager.joinRoom(cleanCode, metroName)
                            }
                            pendingIntentState.value = null
                            return@LaunchedEffect
                        }
                    }

                    val ytVideoId = extractYouTubeVideoId(targetString)

                    if (ytVideoId != null) {
                        // Meld-এর মতো সরাসরি MiniPlayer-এ প্লে হবে, NowPlaying এক্সপ্যান্ড হবে না
                        playerViewModel.playFromYouTubeVideoId(ytVideoId, this@MainActivity)
                        pendingIntentState.value = null
                        return@LaunchedEffect
                    }

                    if (uri != null) {
                        val mimeType = context.contentResolver.getType(uri)
                        val uriStr = uri.toString().lowercase()

                        if (mimeType?.startsWith("video/") == true ||
                            uriStr.endsWith(".mp4") || uriStr.endsWith(".mkv") ||
                            uriStr.endsWith(".webm") || uriStr.endsWith(".avi") || uriStr.endsWith(".3gp")) {

                            var title = "External Video"
                            if (uri.scheme == "content") {
                                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                    if (cursor.moveToFirst() && nameIndex != -1) {
                                        title = cursor.getString(nameIndex).substringBeforeLast(".")
                                    }
                                }
                            } else if (uri.scheme == "file") {
                                title = java.io.File(uri.path ?: "").nameWithoutExtension
                            }

                            videoToPlay = com.musicplayer.lytrix.model.Video(
                                id = System.currentTimeMillis(),
                                title = title,
                                path = uri.toString(),
                                uriString = uri.toString(),
                                dateModified = System.currentTimeMillis(),
                                durationText = "",
                                sizeText = ""
                            )
                        } else {
                            playerViewModel.playExternalFile(this@MainActivity, uri)
                        }
                    }
                    pendingIntentState.value = null
                }



                Box(modifier = Modifier.fillMaxSize()) {
                    if (isInPipMode && videoToPlay == null) {
                        val pipVideoPlayer = playerViewModel.getVideoPlayer()
                        val pipResizeMode by playerViewModel.videoResizeMode.collectAsState()
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(androidx.compose.ui.graphics.Color.Black)
                        ) {
                            if (pipVideoPlayer != null) {
                                androidx.compose.ui.viewinterop.AndroidView(
                                    factory = { ctx ->
                                        androidx.media3.ui.PlayerView(ctx).apply {
                                            player = pipVideoPlayer
                                            useController = false
                                            controllerAutoShow = false
                                            resizeMode = pipResizeMode
                                            subtitleView?.visibility = android.view.View.GONE
                                            setBackgroundColor(android.graphics.Color.BLACK)
                                        }
                                    },
                                    update = { pv ->
                                        if (pv.player != pipVideoPlayer) pv.player = pipVideoPlayer
                                        pv.resizeMode = pipResizeMode
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    } else {
                        val customBgUri = com.musicplayer.lytrix.ui.theme.LocalCustomBackgroundUri.current

                    Scaffold(

                        modifier = Modifier.fillMaxSize(),

                        containerColor = if (customBgUri.isNotBlank()) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.background,

                        contentWindowInsets = WindowInsets(0, 0, 0, 0),

                        bottomBar = {

                            val isSettingsRoute = currentRoute?.contains("settings") == true || currentRoute == "login"

                            val isListenTogetherRoute = currentRoute?.startsWith("listen_together") == true

                            val configuration = androidx.compose.ui.platform.LocalConfiguration.current

                            val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE



                            AnimatedVisibility(

                                visible = currentSong != null && !isNowPlayingExpanded && !isSettingsRoute && !isListenTogetherRoute && videoToPlay == null && !isPowerSavingMode,

                                enter = slideInVertically(

                                    initialOffsetY = { it },

                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)

                                ) + fadeIn(),

                                exit = slideOutVertically(

                                    targetOffsetY = { it },

                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)

                                ) + fadeOut()

                            ) {

                                Box(

                                    modifier = Modifier

                                        .fillMaxWidth()

                                        .padding(bottom = if (isLandscape) 6.dp else 0.dp),

                                    contentAlignment = Alignment.BottomCenter

                                ) {

                                    currentSong?.let { song ->

                                        val activeTheme =

                                            playerViewModel.playerTheme.collectAsState(initial = "Solid Default").value

                                        val customFont = when (activeTheme) {

                                            "Cyber City" -> androidx.compose.ui.text.font.FontFamily(

                                                androidx.compose.ui.text.font.Font(R.font.font_quantico)

                                            )



                                            "Campside Rain", "Cat Rain", "Snowy Home" -> androidx.compose.ui.text.font.FontFamily(

                                                androidx.compose.ui.text.font.Font(R.font.font_poppins)

                                            )



                                            "Cartoon Walk", "Summer Home", "Summer Village" -> androidx.compose.ui.text.font.FontFamily(

                                                androidx.compose.ui.text.font.Font(R.font.font_montserrat)

                                            )



                                            else -> androidx.compose.ui.text.font.FontFamily.Default

                                        }



                                        val isOnline = song.filePath in listOf("ONLINE", "ONLINE_LYTRIX", "OFFLINE", "ONLINE_PODCAST", "OFFLINE_PODCAST", "ONLINE_AUDIOBOOK", "OFFLINE_AUDIOBOOK")



                                        if (isOnline) {

                                            com.musicplayer.lytrix.ui.OnlineMiniPlayer(

                                                song = song,

                                                isPlaying = isPlaying,

                                                isBuffering = isBuffering,

                                                currentPosition = currentPosition,

                                                duration = duration,

                                                accentColor = accentColor,

                                                onPlayPause = { playerViewModel.playPause() },

                                                onSkipNext = { playerViewModel.skipNext() },

                                                onSkipPrevious = { playerViewModel.skipPrevious() },

                                                onExpand = { isNowPlayingExpanded = true },

                                                onDismiss = { playerViewModel.dismissPlayer() }

                                            )

                                        } else {

                                            MiniPlayerCard(

                                                song = song,

                                                isPlaying = isPlaying,

                                                accentColor = accentColor,

                                                currentPosition = currentPosition,

                                                duration = duration,

                                                onPlayPause = { playerViewModel.playPause() },

                                                onSkipPrevious = { playerViewModel.skipPrevious() },

                                                onSkipNext = { playerViewModel.skipNext() },

                                                onExpand = { isNowPlayingExpanded = true },

                                                onDismiss = { playerViewModel.dismissPlayer() },

                                                currentTheme = activeTheme,

                                                themeFont = customFont

                                            )

                                        }

                                    }

                                }

                            }

                        }

                    ) { _ ->

                        Box(modifier = Modifier.fillMaxSize()) {

                            NavHost(navController = navController, startDestination = "player") {

                                composable("player") {

                                    Column(modifier = Modifier.fillMaxSize()) {

                                        PlayerScreen(

                                            viewModel = playerViewModel,

                                            modifier = Modifier.weight(1f),

                                            onNavigateToSettings = { navController.navigate("settings") },

                                            onOpenPremiumDialog = { showPremiumDialog = true },

                                            onNavigateToOnlineSettings = { navController.navigate("online_settings") },

                                            onNavigateToPlaylist = { type, id ->

                                                navController.navigate(

                                                    "playlist/$type/${android.net.Uri.encode(id)}"

                                                )

                                            },

                                            onNavigateToArtist = { artistName ->

                                                navController.navigate(

                                                    "ArtistDetailScreen/${

                                                        android.net.Uri.encode(playerViewModel.cleanArtistDisplayName(artistName))

                                                    }"

                                                )

                                            },

                                            onNavigateToListenTogether = {
                                                returnToNowPlaying = false
                                                val isMetroActive = playerViewModel.metroManager.roomRole.value != com.musicplayer.lytrix.listentogether.metro.MetroRoomRole.NONE || playerViewModel.metroManager.pendingJoinCode.value != null
                                                val isFirebaseActive = playerViewModel.myRoomStatus.value != "disconnected"
                                                if (isMetroActive) {
                                                    navController.navigate("listen_together_metro")
                                                } else if (isFirebaseActive) {
                                                    navController.navigate("listen_together_firebase")
                                                } else {
                                                    navController.navigate("listen_together")
                                                }
                                            },

                                            onNavigateToFriends = { navController.navigate("friends") },

                                            onNavigateToProfile = { navController.navigate("profile") }, // 🔥 Added Navigation

                                            onNavigateToAlbum = { albumName ->

                                                navController.navigate(

                                                    "AlbumDetailScreen/${

                                                        android.net.Uri.encode(albumName)

                                                    }"

                                                )

                                            },

                                            onExpandPlayer = { isNowPlayingExpanded = true },

                                            onPlayVideo = { videoToPlay = it }

                                        )



                                    }

                                }

                                composable("online_settings") {
                                    com.musicplayer.lytrix.ui.OnlineSettingsScreen(
                                        onNavigateBack = { navController.popBackStack() },
                                        onNavigateToLogin = { navController.navigate("login") },
                                        onNavigateToAiSettings = { navController.navigate("ai_settings") },
                                        onNavigateToPrivacySettings = { navController.navigate("privacy_settings") },
                                        onNavigateToSpotifyLogin = { navController.navigate("spotify_login") },
                                        onNavigateToArtworkSettings = { navController.navigate("artwork_settings") }
                                    )
                                }

                                composable("artwork_settings") {
                                    com.musicplayer.lytrix.ui.ArtworkSettingsScreen(
                                        onNavigateBack = { navController.popBackStack() },
                                        onNavigateToSpotifyLogin = { navController.navigate("spotify_login") }
                                    )
                                }

                                composable("spotify_login") {
                                    com.musicplayer.lytrix.ui.SpotifyLoginScreen(
                                        onNavigateBack = { navController.popBackStack() },
                                        onLoginSuccess = { navController.popBackStack() }
                                    )
                                }

                                composable("privacy_settings") {

                                    com.musicplayer.lytrix.ui.PrivacySettingsScreen(

                                        viewModel = playerViewModel,

                                        onNavigateBack = { navController.popBackStack() },

                                        onNavigateToNotInterested = { navController.navigate("not_interested_settings") }

                                    )

                                }

                                composable("not_interested_settings") {

                                    com.musicplayer.lytrix.ui.NotInterestedSongsScreen(

                                        viewModel = playerViewModel,

                                        onNavigateBack = { navController.popBackStack() }

                                    )

                                }

                                composable("ai_settings") {

                                    com.musicplayer.lytrix.ui.AiSettingsScreen(

                                        onNavigateBack = { navController.popBackStack() }

                                    )

                                }

                                composable("login") {

                                    com.musicplayer.lytrix.ui.screens.LoginScreen(

                                        onNavigateBack = { navController.popBackStack() }

                                    )

                                }

                                composable("listening_insights") {
                                    val settingsVm: com.musicplayer.lytrix.ui.SettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                                        factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(applicationContext as android.app.Application)
                                    )
                                    com.musicplayer.lytrix.ui.PlaytimeScreen(
                                        viewModel = settingsVm,
                                        onNavigateBack = { navController.popBackStack() }
                                    )
                                }

                                composable("settings") {

                                    SettingsNavGraph(

                                        playerViewModel = playerViewModel,

                                        onNavigateBack = { navController.popBackStack() },

                                        onNavigateToOnlineSettings = { navController.navigate("online_settings") },

                                        onOpenSleepTimer = {

                                            currentSheetType =

                                                BottomSheetType.SLEEP_TIMER; isMenuSheetOpen = true

                                        }

                                    )

                                }

                                val exitListenTogether: () -> Unit = {
                                    android.util.Log.d("BACKDBG", "exitListenTogether called. returnToNowPlaying=$returnToNowPlaying")
                                    val popped = navController.popBackStack("listen_together", inclusive = true)
                                    if (!popped) {
                                        navController.popBackStack()
                                    }
                                    if (returnToNowPlaying) {
                                        android.util.Log.d("BACKDBG", "exitListenTogether: expanding NowPlaying")
                                        isNowPlayingExpanded = true
                                        returnToNowPlaying = false
                                    }
                                }

                                composable("listen_together") {
                                    ListenTogetherHubScreen(
                                        viewModel = playerViewModel,
                                        accentColor = accentColor,
                                        onNavigateBack = {
                                            android.util.Log.d("BACKDBG", "Hub onNavigateBack called. returnToNowPlaying=$returnToNowPlaying")
                                            navController.popBackStack()
                                            if (returnToNowPlaying) {
                                                android.util.Log.d("BACKDBG", "Hub onNavigateBack: expanding NowPlaying")
                                                isNowPlayingExpanded = true
                                                returnToNowPlaying = false
                                            }
                                        },
                                        onNavigateToMetro = {
                                            navController.navigate("listen_together_metro")
                                        },
                                        onNavigateToFirebase = {
                                             navController.navigate("listen_together_firebase")
                                        },
                                        onOpenPremiumDialog = {
                                            showPremiumDialog = true
                                        }
                                    )
                                }

                                composable("listen_together_metro") {
                                    MetroRoomScreen(
                                        viewModel = playerViewModel,
                                        accentColor = accentColor,
                                        onNavigateBack = {
                                            val isInRoom = playerViewModel.metroManager.roomRole.value != com.musicplayer.lytrix.listentogether.metro.MetroRoomRole.NONE ||
                                                    playerViewModel.metroManager.pendingJoinCode.value != null
                                            android.util.Log.d("BACKDBG", "Metro onNavigateBack called. isInRoom=$isInRoom, returnToNowPlaying=$returnToNowPlaying")
                                            if (isInRoom) {
                                                exitListenTogether()
                                            } else {
                                                navController.popBackStack()
                                            }
                                        },
                                        onExitRoom = {
                                            exitListenTogether()
                                        },
                                        onNavigateToSettings = {
                                            navController.navigate("listen_together_metro_settings")
                                        }
                                    )
                                }

                                composable("listen_together_metro_settings") {
                                    MetroSettingsScreen(
                                        viewModel = playerViewModel,
                                        accentColor = accentColor,
                                        onNavigateBack = {
                                            navController.popBackStack()
                                        }
                                    )
                                }

                                composable("listen_together_firebase") {
                                    ListenTogetherScreen(
                                        viewModel = playerViewModel,
                                        accentColor = accentColor,
                                        onNavigateBack = {
                                            val isInRoom = playerViewModel.myRoomStatus.value != "disconnected"
                                            android.util.Log.d("BACKDBG", "Firebase onNavigateBack called. isInRoom=$isInRoom, returnToNowPlaying=$returnToNowPlaying")
                                            if (isInRoom) {
                                                exitListenTogether()
                                            } else {
                                                navController.popBackStack()
                                            }
                                        },
                                        onExitRoom = {
                                            exitListenTogether()
                                        }
                                    )
                                }

                                composable("friends") {

                                    FriendsScreen(

                                        viewModel = playerViewModel,

                                        accentColor = accentColor,

                                        onNavigateBack = { navController.popBackStack() },

                                        onNavigateToFriendProfile = { friendUid -> navController.navigate("friend_profile/$friendUid") },

                                        onJoinRoom = { roomCode ->

                                            playerViewModel.setPendingRoomCode(roomCode)

                                            navController.navigate("listen_together_firebase")

                                        }

                                    )

                                }

                                composable("profile") {

                                    ProfileScreen(

                                        viewModel = playerViewModel,

                                        accentColor = accentColor,

                                        onNavigateBack = { navController.popBackStack() },

                                        onManagePremium = { showPremiumDialog = true },

                                        onNavigateToFriends = { navController.navigate("friends") },

                                        onNavigateToProfileEdit = { navController.navigate("profile_edit") }

                                    )

                                }

                                composable("friend_profile/{uid}") { backStackEntry ->

                                    val uid = backStackEntry.arguments?.getString("uid")

                                    ProfileScreen(

                                        viewModel = playerViewModel,

                                        accentColor = accentColor,

                                        friendUid = uid,

                                        onNavigateBack = { navController.popBackStack() },

                                        onManagePremium = { showPremiumDialog = true },

                                        onNavigateToFriends = { navController.navigate("friends") },

                                        onNavigateToProfileEdit = { navController.navigate("profile_edit") }

                                    )

                                }

                                composable("profile_edit") {

                                    ProfileEditScreen(

                                        accentColor = accentColor,

                                        onNavigateBack = { navController.popBackStack() }

                                    )

                                }

                                composable("playlist/{type}/{id}") { backStackEntry ->

                                    val type =

                                        backStackEntry.arguments?.getString("type") ?: "liked"

                                    val rawId = backStackEntry.arguments?.getString("id") ?: ""

                                    val idString = try { android.net.Uri.decode(rawId) } catch (e: Exception) { rawId }

                                    PlaylistDetailScreen(

                                        viewModel = playerViewModel,

                                        type = type,

                                        idString = idString,

                                        onNavigateBack = { navController.popBackStack() })

                                }

                                composable("ArtistDetailScreen/{artistName}") { backStackEntry ->

                                    val rawArtistName = backStackEntry.arguments?.getString("artistName") ?: ""
                                    val artistName = try { android.net.Uri.decode(rawArtistName) } catch (e: Exception) { rawArtistName }

                                    val curMediaCat = playerViewModel.currentMediaCategory.collectAsState().value
                                    val curSong = playerViewModel.currentSong.collectAsState().value
                                    val isAudiobookMedia = curMediaCat == "AUDIOBOOK" || curSong?.mediaType == "AUDIOBOOK" || curSong?.filePath in listOf("ONLINE_AUDIOBOOK", "OFFLINE_AUDIOBOOK")
                                    val isPodcastMedia = curMediaCat == "PODCAST" || curSong?.mediaType == "PODCAST" || curSong?.filePath in listOf("ONLINE_PODCAST", "OFFLINE_PODCAST")
                                    val isRealYtChannel = (curSong != null && !curSong.channelId.isNullOrBlank() && curSong.channelId!!.startsWith("UC")) || (artistName.startsWith("UC") || artistName.startsWith("HC"))

                                    val isAudiobook = artistName.startsWith("AUDIOBOOK_") || (isAudiobookMedia && !isRealYtChannel)
                                    val isPodcast = artistName.startsWith("PODCAST_") || (isPodcastMedia && !isRealYtChannel)

                                    if (isAudiobook || isPodcast) {
                                        val cleanTitle = artistName.removePrefix("AUDIOBOOK_").removePrefix("PODCAST_").trim()
                                        com.musicplayer.lytrix.ui.library.ShowsDetailRoute(
                                            viewModel = playerViewModel,
                                            title = cleanTitle,
                                            isAudiobook = isAudiobook,
                                            onBackClick = {
                                                playerViewModel.clearArtistPage()
                                                navController.popBackStack()
                                            },
                                            onNavigateToArtist = { name -> navController.navigate("ArtistDetailScreen/${android.net.Uri.encode(name)}") }
                                        )
                                    } else {
                                        com.musicplayer.lytrix.ui.ArtistProfileScreen(

                                            viewModel = playerViewModel,

                                            artistName = artistName,

                                            onNavigateBack = {

                                                playerViewModel.clearArtistPage()

                                                navController.popBackStack()

                                            },

                                            onNavigateToItems = { browseId, params ->

                                                navController.navigate("ArtistItemsScreen/${browseId}/${params}")

                                            },

                                            onNavigateToArtist = { name -> navController.navigate("ArtistDetailScreen/${android.net.Uri.encode(name)}") }

                                        )
                                    }

                                }

                                composable("ArtistItemsScreen/{browseId}/{params}") { backStackEntry ->

                                    val browseId = backStackEntry.arguments?.getString("browseId") ?: ""

                                    val params = backStackEntry.arguments?.getString("params")?.takeIf { it != "null" }

                                    com.musicplayer.lytrix.ui.ArtistItemsScreen(

                                        viewModel = playerViewModel,

                                        browseId = browseId,

                                        params = params,

                                        onNavigateBack = {

                                            playerViewModel.clearArtistItems()

                                            navController.popBackStack()

                                        }

                                    )

                                }

                                composable("AlbumDetailScreen/{albumName}") { backStackEntry ->

                                    val albumName =

                                        backStackEntry.arguments?.getString("albumName") ?: ""

                                    PlaylistDetailScreen(

                                        viewModel = playerViewModel,

                                        type = "album",

                                        idString = albumName,

                                        onNavigateBack = { navController.popBackStack() })

                                }

                            }

                            AnimatedVisibility(

                                visible = currentRoute == "player" || currentRoute?.startsWith("ArtistDetailScreen") == true || currentRoute?.startsWith("ArtistItemsScreen") == true,

                                enter = fadeIn(),

                                exit = fadeOut()

                            ) {

                                com.musicplayer.lytrix.ui.GlobalYtPlaylistOverlay(viewModel = playerViewModel, onNavigateToArtist = { id -> navController.navigate("ArtistDetailScreen/$id") })

                            }

                            AnimatedVisibility(

                                visible = isNowPlayingExpanded && currentSong != null,

                                enter = slideInVertically(

                                    initialOffsetY = { it },

                                    animationSpec = spring(

                                        dampingRatio = 0.85f,

                                        stiffness = Spring.StiffnessLow

                                    )

                                ) + fadeIn(),

                                exit = slideOutVertically(

                                    targetOffsetY = { it },

                                    animationSpec = spring(

                                        dampingRatio = 0.85f,

                                        stiffness = Spring.StiffnessLow

                                    )

                                ) + fadeOut(),

                                modifier = Modifier.fillMaxSize()

                            ) {

                                currentSong?.let { song ->

                                    BackHandler(enabled = isNowPlayingExpanded) {
                                        android.util.Log.d("BACKDBG", "4. NowPlaying BackHandler triggered -> closing NowPlaying")
                                        if (this@MainActivity.requestedOrientation != android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
                                            this@MainActivity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                        }
                                        isNowPlayingExpanded = false
                                    }

                                    when (playerStyle) {
                                        com.musicplayer.lytrix.ui.DataStoreManager.PLAYER_STYLE_APPLE_MUSIC -> {
                                        AppleMusicNowPlayingFullScreen(
                                            viewModel = playerViewModel,
                                            song = song,
                                            isPlaying = isPlaying,
                                            isBuffering = isBuffering,
                                            currentPosition = currentPosition,
                                            duration = duration,
                                            repeatMode = repeatMode,
                                            shuffleEnabled = shuffleEnabled,
                                            accentColor = accentColor,
                                            currentLyrics = currentLyrics,
                                            isFavorite = favoriteSongIds.contains(song.id),
                                            onClose = { isNowPlayingExpanded = false },
                                            onPlayPause = { playerViewModel.playPause() },
                                            onSkipNext = { playerViewModel.skipNext() },
                                            onSkipPrevious = { playerViewModel.skipPrevious() },
                                            onSeek = { playerViewModel.seekTo(it) },
                                            onToggleShuffle = { playerViewModel.toggleShuffle() },
                                            onToggleRepeat = { playerViewModel.toggleRepeat() },
                                            onToggleFavorite = { playerViewModel.toggleFavorite(song.id) },
                                            onOpenMenu = {
                                                currentSheetType = BottomSheetType.MAIN; isMenuSheetOpen = true
                                            },
                                            onOpenSleepTimer = {
                                                currentSheetType = BottomSheetType.SLEEP_TIMER; isMenuSheetOpen = true
                                            },
                                            onNavigateToArtist = { artistName ->
                                                navController.navigate("ArtistDetailScreen/${android.net.Uri.encode(playerViewModel.cleanArtistDisplayName(artistName))}")
                                            },
                                            onPlayVideo = { videoToPlay = it }
                                        )
                                        }
                                        com.musicplayer.lytrix.ui.DataStoreManager.PLAYER_STYLE_M3_EXPRESSIVE -> {
                                        com.musicplayer.lytrix.ui.player.M3ExpressiveNowPlayingFullScreen(
                                            viewModel = playerViewModel,
                                            song = song,
                                            isPlaying = isPlaying,
                                            isBuffering = isBuffering,
                                            currentPosition = currentPosition,
                                            duration = duration,
                                            repeatMode = repeatMode,
                                            shuffleEnabled = shuffleEnabled,
                                            accentColor = accentColor,
                                            currentLyrics = currentLyrics,
                                            isFavorite = favoriteSongIds.contains(song.id),
                                            onClose = { isNowPlayingExpanded = false },
                                            onPlayPause = { playerViewModel.playPause() },
                                            onSkipNext = { playerViewModel.skipNext() },
                                            onSkipPrevious = { playerViewModel.skipPrevious() },
                                            onSeek = { playerViewModel.seekTo(it) },
                                            onToggleShuffle = { playerViewModel.toggleShuffle() },
                                            onToggleRepeat = { playerViewModel.toggleRepeat() },
                                            onToggleFavorite = { playerViewModel.toggleFavorite(song.id) },
                                            onOpenMenu = { currentSheetType = BottomSheetType.MAIN; isMenuSheetOpen = true },
                                            onOpenSleepTimer = { currentSheetType = BottomSheetType.SLEEP_TIMER; isMenuSheetOpen = true },
                                            onNavigateToArtist = { artistName ->
                                                navController.navigate("ArtistDetailScreen/${android.net.Uri.encode(playerViewModel.cleanArtistDisplayName(artistName))}")
                                            },
                                            onPlayVideo = { videoToPlay = it }
                                        )
                                        }
                                        com.musicplayer.lytrix.ui.DataStoreManager.PLAYER_STYLE_CINEMA -> {
                                        com.musicplayer.lytrix.ui.player.CinemaNowPlayingFullScreen(
                                            viewModel = playerViewModel,
                                            song = song,
                                            isPlaying = isPlaying,
                                            isBuffering = isBuffering,
                                            currentPosition = currentPosition,
                                            duration = duration,
                                            repeatMode = repeatMode,
                                            shuffleEnabled = shuffleEnabled,
                                            accentColor = accentColor,
                                            currentLyrics = currentLyrics,
                                            isFavorite = favoriteSongIds.contains(song.id),
                                            onClose = { isNowPlayingExpanded = false },
                                            onPlayPause = { playerViewModel.playPause() },
                                            onSkipNext = { playerViewModel.skipNext() },
                                            onSkipPrevious = { playerViewModel.skipPrevious() },
                                            onSeek = { playerViewModel.seekTo(it) },
                                            onToggleShuffle = { playerViewModel.toggleShuffle() },
                                            onToggleRepeat = { playerViewModel.toggleRepeat() },
                                            onToggleFavorite = { playerViewModel.toggleFavorite(song.id) },
                                            onOpenMenu = { currentSheetType = BottomSheetType.MAIN; isMenuSheetOpen = true },
                                            onOpenSleepTimer = { currentSheetType = BottomSheetType.SLEEP_TIMER; isMenuSheetOpen = true },
                                            onNavigateToArtist = { artistName ->
                                                navController.navigate("ArtistDetailScreen/${android.net.Uri.encode(playerViewModel.cleanArtistDisplayName(artistName))}")
                                            },
                                            onPlayVideo = { videoToPlay = it }
                                        )
                                        }
                                        else -> {
                                        MeldNowPlayingFullScreen(

                                            viewModel = playerViewModel,

                                            song = song,

                                            isPlaying = isPlaying,

                                            isBuffering = isBuffering,

                                            currentPosition = currentPosition,

                                            duration = duration,

                                            repeatMode = repeatMode,

                                            shuffleEnabled = shuffleEnabled,

                                            accentColor = accentColor,

                                            currentLyrics = currentLyrics,

                                            isFavorite = favoriteSongIds.contains(song.id),

                                            onClose = { isNowPlayingExpanded = false },

                                            onPlayPause = { playerViewModel.playPause() },

                                            onSkipNext = {

                                                val activityContext = context as? android.app.Activity

                                                if (activityContext != null) {

                                                    playerViewModel.skipNext()

                                                } else {

                                                    playerViewModel.skipNext()

                                                }

                                            },

                                            onSkipPrevious = {

                                                val activityContext = context as? android.app.Activity

                                                if (activityContext != null) {

                                                    playerViewModel.skipPrevious()

                                                } else {

                                                    playerViewModel.skipPrevious()

                                                }

                                            },

                                            onSeek = { playerViewModel.seekTo(it) },

                                            onToggleShuffle = { playerViewModel.toggleShuffle() },

                                            onToggleRepeat = { playerViewModel.toggleRepeat() },

                                            onToggleFavorite = { playerViewModel.toggleFavorite(song.id) },

                                            onOpenMenu = {

                                                currentSheetType = BottomSheetType.MAIN; isMenuSheetOpen = true

                                            },

                                            onOpenSleepTimer = {

                                                currentSheetType = BottomSheetType.SLEEP_TIMER; isMenuSheetOpen = true

                                            },

                                            onNavigateToArtist = { artistName ->

                                                navController.navigate("ArtistDetailScreen/${android.net.Uri.encode(playerViewModel.cleanArtistDisplayName(artistName))}")

                                            },

                                            onPlayVideo = { videoToPlay = it }

                                        )
                                        }
                                    }

                                }

                            }



                            if (isMenuSheetOpen) {

                                val currentVideoQuality by playerViewModel.videoQualityPref.collectAsState()



                                PlayerBottomSheetController(

                                    viewModel = playerViewModel,

                                    sheetType = currentSheetType,

                                    onDismiss = { isMenuSheetOpen = false; currentSheetType = BottomSheetType.MAIN },

                                    onNavigate = { targetType ->

                                        val isOnlineSong = currentSong?.uriString?.startsWith("http", ignoreCase = true) == true || currentSong?.filePath?.startsWith("http", ignoreCase = true) == true

                                        if (targetType == BottomSheetType.PLAYLIST && isOnlineSong && currentSong != null) {

                                            isMenuSheetOpen = false

                                            currentSheetType = BottomSheetType.MAIN

                                            com.musicplayer.lytrix.ui.globalShowOnlinePlaylistDialogForSong = currentSong

                                        } else {

                                            currentSheetType = targetType

                                        }

                                    },

                                    playlists = playlists,

                                    onAddToPlaylist = { playlistId ->

                                        currentSong?.id?.let { songId -> playerViewModel.addSongToPlaylist(playlistId, songId) }

                                        isMenuSheetOpen = false

                                    },

                                    onCreatePlaylist = { playlistName -> playerViewModel.createPlaylist(playlistName) },

                                    onSetSleepTimer = { mins, wait -> playerViewModel.setSleepTimer(mins, wait) },

                                    onSetSpeed = { speed -> playerViewModel.setPlaybackSpeed(speed) },

                                    onDeleteSong = { showTrashConfirmForCurrentSong = true },

                                    accentColor = accentColor,



                                    currentSpeed = currentPlaybackSpeed,

                                    currentSleepTimerMinutes = currentSleepTimerMinutes,

                                    playLastSongToEnd = playLastSongToEnd,



                                    onHideSong = {

                                        currentSong?.let { s ->

                                            if (s.filePath != null && !s.filePath!!.startsWith("ONLINE") && !s.filePath!!.startsWith("OFFLINE")) {

                                                playerViewModel.skipNext()

                                                playerViewModel.hideSong(s.id)

                                                isNowPlayingExpanded = false

                                                android.widget.Toast.makeText(context, "Song hidden", android.widget.Toast.LENGTH_SHORT).show()

                                            }

                                        }

                                    },

                                    onPowerSavingMode = { isPowerSavingMode = true },

                                    onShareSong = {

                                        currentSong?.let { com.musicplayer.lytrix.ui.shareLocalSong(context, it) }

                                    },

                                    onSetPointA = {

                                        playerViewModel.setPointA()

                                        android.widget.Toast.makeText(context, "Point A set", android.widget.Toast.LENGTH_SHORT).show()

                                    },

                                    onSetPointB = {

                                        playerViewModel.setPointB()

                                        val msg = if (playerViewModel.pointB.value != null) "A-B Repeat ON" else "Point B must be after Point A"

                                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()

                                    },

                                    onClearAB = {

                                        playerViewModel.clearAB()

                                        android.widget.Toast.makeText(context, "A-B Repeat OFF", android.widget.Toast.LENGTH_SHORT).show()

                                    },

                                    isPointASet = pointA != null,

                                    isPointBSet = pointB != null,



                                    song = currentSong,

                                    currentVideoQuality = currentVideoQuality,

                                    onSetVideoQuality = { quality ->

                                        playerViewModel.setVideoQualityPref(context, quality)

                                    },

                                    onNavigateToArtist = { artistName ->

                                        isMenuSheetOpen = false

                                        isNowPlayingExpanded = false

                                        navController.navigate("ArtistDetailScreen/${android.net.Uri.encode(playerViewModel.cleanArtistDisplayName(artistName))}")

                                    },

                                    onViewAlbum = {

                                        isMenuSheetOpen = false

                                        isNowPlayingExpanded = false

                                        currentSong?.let { playerViewModel.openAlbumForSong(context, it) }

                                    },

                                    onPlayOnline = {

                                        isMenuSheetOpen = false

                                        currentSong?.let { song ->

                                            val isYtSong = !(song.albumArtUriString?.contains("saavn", ignoreCase = true) ?: false)

                                            if (isYtSong) {

                                                val realId = song.originalId ?: song.id.toString()

                                                val onlineFakeSong = com.musicplayer.lytrix.model.Song(

                                                    id = realId.hashCode().toLong(),

                                                    title = song.title,

                                                    artist = song.artist,

                                                    album = "Online Stream",

                                                    duration = 0L,

                                                    uriString = "https://www.youtube.com/watch?v=$realId",

                                                    filePath = "ONLINE_LYTRIX",

                                                    albumArtUriString = song.albumArtUriString,

                                                    isVideo = false,

                                                    hasLyrics = false,

                                                    originalId = realId

                                                )

                                                playerViewModel.playSong(onlineFakeSong, listOf(onlineFakeSong))

                                            } else {

                                                android.widget.Toast.makeText(context, "Fetching Stream...", android.widget.Toast.LENGTH_SHORT).show()

                                                val onlineSong = song.copy(filePath = "ONLINE", uriString = song.uriString.replace("file://", ""))

                                                playerViewModel.playSong(onlineSong, listOf(onlineSong))

                                            }

                                        }

                                    },

                                    onNavigateToListenTogether = {
                                        isMenuSheetOpen = false
                                        returnToNowPlaying = isNowPlayingExpanded
                                        isNowPlayingExpanded = false
                                        val isMetroActive = playerViewModel.metroManager.roomRole.value != com.musicplayer.lytrix.listentogether.metro.MetroRoomRole.NONE || playerViewModel.metroManager.pendingJoinCode.value != null
                                        val isFirebaseActive = playerViewModel.myRoomStatus.value != "disconnected"
                                        if (isMetroActive) {
                                            navController.navigate("listen_together_metro")
                                        } else if (isFirebaseActive) {
                                            navController.navigate("listen_together_firebase")
                                        } else {
                                            navController.navigate("listen_together")
                                        }
                                    }

                                )

                            }



                            // 🎁 DOWNLOAD REWARD AD DIALOG (Single: 10s, Playlist: 20s)
                            if (com.musicplayer.lytrix.ui.globalDownloadRewardRequest != null) {
                                com.musicplayer.lytrix.ui.DownloadRewardAdDialog(
                                    request = com.musicplayer.lytrix.ui.globalDownloadRewardRequest!!,
                                    onDismiss = { com.musicplayer.lytrix.ui.globalDownloadRewardRequest = null }
                                )
                            }

                            // 🔥 PREMIUM DIALOG UI (Added)

                            if (showPremiumDialog) {

                                PremiumActivationDialog(

                                    accentColor = accentColor,

                                    onDismiss = { showPremiumDialog = false },

                                    onSuccess = { showPremiumDialog = false }

                                )

                            }



                            if (isPowerSavingMode) PowerSavingModeScreen(
                                initialLocked = aodInitialLocked,
                                onExit = {
                                    isPowerSavingMode = false
                                    aodInitialLocked = false
                                }
                            )



                            if (showTrashConfirmForCurrentSong && currentSong != null) {

                                PremiumTrashConfirmDialog(

                                    uris = listOf(android.net.Uri.parse(currentSong!!.uriString)),

                                    onConfirm = {

                                        playerViewModel.skipNext(); playerViewModel.deleteSongs(

                                        setOf(currentSong!!.id)

                                    ); showTrashConfirmForCurrentSong = false

                                    },

                                    onDismiss = { showTrashConfirmForCurrentSong = false }

                                )

                            }



                            if (com.musicplayer.lytrix.ui.globalShowOnlinePlaylistDialogForSong != null) {

                                var newOnlinePlaylistName by remember { mutableStateOf("") }

                                AlertDialog(

                                    onDismissRequest = { com.musicplayer.lytrix.ui.globalShowOnlinePlaylistDialogForSong = null },

                                    containerColor = Color(0xFF1E1E1E),

                                    title = { Text("Add to Cloud Playlist", color = Color.White) },

                                    text = {

                                        Column {

                                            val plPrefs = context.getSharedPreferences("online_playlists_meta", Context.MODE_PRIVATE)

                                            val playlists = plPrefs.getStringSet("names", emptySet<String>())?.toList() ?: emptyList()

                                            if (playlists.isNotEmpty()) {

                                                Text("Existing Playlists:", color = Color.White, fontSize = 14.sp)

                                                LazyColumn(modifier = Modifier.heightIn(max = 150.dp).padding(vertical = 8.dp)) {

                                                    items(playlists) { plName ->

                                                        Text(

                                                            text = plName, color = Color.White, modifier = Modifier.fillMaxWidth().clickable {

                                                                val songPrefs = context.getSharedPreferences("online_pl_$plName", Context.MODE_PRIVATE)

                                                                val song = com.musicplayer.lytrix.ui.globalShowOnlinePlaylistDialogForSong!!

                                                                val songIdStr = song.originalId ?: song.id.toString()



                                                                val obj = org.json.JSONObject()

                                                                obj.put("id", songIdStr)

                                                                obj.put("title", song.title)

                                                                obj.put("name", song.title)

                                                                obj.put("artist", song.artist)

                                                                obj.put("imageUrl", song.albumArtUriString ?: "")

                                                                obj.put("downloadUrl", song.uriString)

                                                                songPrefs.edit().putString(songIdStr, obj.toString()).apply()



                                                                android.widget.Toast.makeText(context, "Added to $plName", android.widget.Toast.LENGTH_SHORT).show()

                                                                com.musicplayer.lytrix.ui.globalShowOnlinePlaylistDialogForSong = null

                                                            }.padding(8.dp)

                                                        )

                                                    }

                                                }

                                            }

                                            Spacer(modifier = Modifier.height(8.dp))

                                            OutlinedTextField(

                                                value = newOnlinePlaylistName,

                                                onValueChange = { newOnlinePlaylistName = it },

                                                placeholder = { Text("Or create new playlist...", color = Color.Gray) },

                                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)

                                            )

                                        }

                                    },

                                    confirmButton = {

                                        TextButton(onClick = {

                                            if (newOnlinePlaylistName.isNotBlank()) {

                                                val plPrefs = context.getSharedPreferences("online_playlists_meta", Context.MODE_PRIVATE)

                                                val existing = plPrefs.getStringSet("names", emptySet<String>())?.toMutableSet() ?: mutableSetOf()

                                                existing.add(newOnlinePlaylistName)

                                                context.getSharedPreferences("playlist_time", Context.MODE_PRIVATE).edit().putLong(newOnlinePlaylistName, System.currentTimeMillis()).apply()

                                                context.getSharedPreferences("playlist_source", Context.MODE_PRIVATE).edit().putString(newOnlinePlaylistName, "Playlist").apply()

                                                plPrefs.edit().putStringSet("names", existing).apply()



                                                val songPrefs = context.getSharedPreferences("online_pl_$newOnlinePlaylistName", Context.MODE_PRIVATE)

                                                val song = com.musicplayer.lytrix.ui.globalShowOnlinePlaylistDialogForSong!!

                                                val songIdStr = song.originalId ?: song.id.toString()



                                                val obj = org.json.JSONObject()

                                                obj.put("id", songIdStr)

                                                obj.put("title", song.title)

                                                obj.put("name", song.title)

                                                obj.put("artist", song.artist)

                                                obj.put("imageUrl", song.albumArtUriString ?: "")

                                                obj.put("downloadUrl", song.uriString)

                                                songPrefs.edit().putString(songIdStr, obj.toString()).apply()



                                                android.widget.Toast.makeText(context, "Created & Added", android.widget.Toast.LENGTH_SHORT).show()

                                                com.musicplayer.lytrix.ui.globalShowOnlinePlaylistDialogForSong = null

                                            }

                                        }) { Text("Create & Add", color = accentColor) }

                                    },

                                    dismissButton = { TextButton(onClick = { com.musicplayer.lytrix.ui.globalShowOnlinePlaylistDialogForSong = null }) { Text("Cancel", color = Color.White) } }

                                )

                            }

                            videoToPlay?.let { video ->

                                VideoPlayerFullscreen(

                                    video = video,

                                    onClose = { videoToPlay = null },

                                    onPlayAsAudio = { currentPlayingVideo ->

                                        videoToPlay = null

                                        val sg = com.musicplayer.lytrix.model.Song(

                                            id = -currentPlayingVideo.id,

                                            title = currentPlayingVideo.title,

                                            artist = "Music Video",

                                            album = "Video",

                                            duration = -1,

                                            uriString = currentPlayingVideo.uriString,

                                            albumArtUriString = null,

                                            filePath = currentPlayingVideo.path,

                                            isVideo = true

                                        )

                                        playerViewModel.playSong(

                                            sg,

                                            listOf(sg)

                                        ); isNowPlayingExpanded = true

                                    },

                                    playerViewModel = playerViewModel

                                )

                            }



                            AnimatedVisibility(

                                visible = showCustomLockScreen && currentSong != null,

                                enter = fadeIn(),

                                exit = fadeOut()

                            ) {

                                currentSong?.let { song ->

                                    Box(

                                        modifier = Modifier

                                            .fillMaxSize()

                                            .background(Color(0xFF2E3E3E))

                                    ) {

                                        AsyncImage(

                                            model = ImageRequest.Builder(context)

                                                .data(song.albumArtUriString).crossfade(true)

                                                .build(),

                                            contentDescription = null,

                                            contentScale = ContentScale.Crop,

                                            modifier = Modifier.fillMaxSize()

                                                .graphicsLayer { alpha = 0.4f }

                                        )

                                        Box(

                                            modifier = Modifier.fillMaxSize()

                                                .background(Color.Black.copy(alpha = 0.4f))

                                        )



                                        Column(

                                            modifier = Modifier.fillMaxSize().padding(24.dp),

                                            horizontalAlignment = Alignment.CenterHorizontally

                                        ) {

                                            Spacer(modifier = Modifier.height(60.dp))



                                            var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }

                                            LaunchedEffect(Unit) {

                                                while (true) {

                                                    delay(1000); currentTime =

                                                        System.currentTimeMillis()

                                                }

                                            }

                                            Text(

                                                SimpleDateFormat(

                                                    "HH:mm",

                                                    Locale.getDefault()

                                                ).format(Date(currentTime)),

                                                color = Color.White,

                                                fontSize = 64.sp,

                                                fontWeight = FontWeight.Light

                                            )

                                            Text(

                                                SimpleDateFormat(

                                                    "EEE, MMM dd",

                                                    Locale.getDefault()

                                                ).format(Date(currentTime)),

                                                color = Color.White.copy(alpha = 0.8f),

                                                fontSize = 16.sp

                                            )



                                            Spacer(modifier = Modifier.weight(1f))



                                            Card(

                                                modifier = Modifier.size(300.dp),

                                                shape = RoundedCornerShape(12.dp),

                                                elevation = CardDefaults.cardElevation(16.dp)

                                            ) {

                                                AsyncImage(

                                                    model = ImageRequest.Builder(context)

                                                        .data(song.albumArtUriString)

                                                        .crossfade(true).build(),

                                                    contentDescription = null,

                                                    contentScale = ContentScale.Crop,

                                                    modifier = Modifier.fillMaxSize()

                                                        .background(Color.Gray)

                                                )

                                            }



                                            Spacer(modifier = Modifier.height(32.dp))



                                            Text(

                                                song.title,

                                                color = Color.White,

                                                fontSize = 22.sp,

                                                fontWeight = FontWeight.Bold,

                                                maxLines = 1,

                                                overflow = TextOverflow.Ellipsis

                                            )

                                            Text(

                                                song.artist,

                                                color = Color.White.copy(alpha = 0.7f),

                                                fontSize = 16.sp,

                                                maxLines = 1,

                                                overflow = TextOverflow.Ellipsis

                                            )



                                            Spacer(modifier = Modifier.height(32.dp))



                                            Row(

                                                modifier = Modifier.fillMaxWidth()

                                                    .padding(horizontal = 32.dp),

                                                horizontalArrangement = Arrangement.SpaceEvenly,

                                                verticalAlignment = Alignment.CenterVertically

                                            ) {

                                                IconButton(onClick = { playerViewModel.skipPrevious() }) {

                                                    Icon(

                                                        Icons.Default.SkipPrevious,

                                                        contentDescription = null,

                                                        tint = Color.White,

                                                        modifier = Modifier.size(40.dp)

                                                    )

                                                }

                                                IconButton(

                                                    onClick = { playerViewModel.playPause() },

                                                    modifier = Modifier.size(64.dp)

                                                ) {

                                                    Icon(

                                                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,

                                                        contentDescription = null,

                                                        tint = Color.White,

                                                        modifier = Modifier.size(56.dp)

                                                    )

                                                }

                                                IconButton(onClick = { playerViewModel.skipNext() }) {

                                                    Icon(

                                                        Icons.Default.SkipNext,

                                                        contentDescription = null,

                                                        tint = Color.White,

                                                        modifier = Modifier.size(40.dp)

                                                    )

                                                }

                                            }



                                            Spacer(modifier = Modifier.weight(1f))



                                            var offsetX by remember { mutableStateOf(0f) }

                                            Row(

                                                modifier = Modifier

                                                    .padding(bottom = 32.dp)

                                                    .offset { IntOffset(offsetX.roundToInt(), 0) }

                                                    .pointerInput(Unit) {

                                                        detectHorizontalDragGestures(

                                                            onDragEnd = {

                                                                if (offsetX > 250f) {

                                                                    showCustomLockScreen = false

                                                                    activity?.let { act ->

                                                                        val keyguardManager =

                                                                            act.getSystemService(

                                                                                Context.KEYGUARD_SERVICE

                                                                            ) as android.app.KeyguardManager

                                                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

                                                                            keyguardManager.requestDismissKeyguard(

                                                                                act,

                                                                                null

                                                                            )

                                                                        } else {

                                                                            @Suppress("DEPRECATION")

                                                                            act.window.addFlags(

                                                                                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD

                                                                            )

                                                                        }

                                                                    }

                                                                }

                                                                offsetX = 0f

                                                            }

                                                        ) { change, dragAmount ->

                                                            change.consume()

                                                            offsetX =

                                                                (offsetX + dragAmount).coerceIn(

                                                                    0f,

                                                                    600f

                                                                )

                                                        }

                                                    },

                                                verticalAlignment = Alignment.CenterVertically

                                            ) {

                                                Icon(

                                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,

                                                    contentDescription = null,

                                                    tint = Color.White.copy(alpha = 0.7f)

                                                )

                                                Spacer(modifier = Modifier.width(8.dp))

                                                Text(

                                                    "Slide to unlock",

                                                    color = Color.White.copy(alpha = 0.7f),

                                                    fontSize = 16.sp

                                                )

                                            }

                                        }

                                    }

                                }

                            }

                        }

                    }







                    val curSongForSnack = playerViewModel.currentSong.collectAsState().value

                    val isSettingsRouteForSnack = currentRoute?.contains("settings") == true || currentRoute == "login"

                    val isListenTogetherRouteForSnack = currentRoute?.startsWith("listen_together") == true

                    val configForSnack = androidx.compose.ui.platform.LocalConfiguration.current

                    val isLandscapeForSnack = configForSnack.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

                    val isMiniPlayerVisibleForSnack = curSongForSnack != null && !isNowPlayingExpanded && !isSettingsRouteForSnack && !isListenTogetherRouteForSnack && videoToPlay == null && !isPowerSavingMode

                    val snackbarBottomPadding = if (isNowPlayingExpanded) {

                        32.dp

                    } else if (isMiniPlayerVisibleForSnack) {

                        if (isLandscapeForSnack) 76.dp else 128.dp

                    } else {

                        24.dp

                    }



                    Box(

                        modifier = Modifier

                            .fillMaxSize()

                            .zIndex(100f),

                        contentAlignment = Alignment.BottomCenter

                    ) {

                        SnackbarHost(

                            hostState = globalSnackbarHostState,

                            modifier = Modifier.padding(bottom = snackbarBottomPadding)

                        ) { data ->

                            Snackbar(

                                snackbarData = data,

                                containerColor = Color(0xFF262626),

                                contentColor = Color.White,

                                actionColor = Color(0xFF00E676),

                                shape = RoundedCornerShape(14.dp)

                            )

                        }

                    }

                    }

                }

            }

        }

    }



    override fun onStop() {
        super.onStop()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N && isInPictureInPictureMode) {
            return
        }
        val powerManager = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        val isScreenOn = powerManager?.isInteractive ?: true
        if (isScreenOn && !isFinishing) {
            // User minimized the app while screen is ON (Home or App Switcher)
            playerViewModel?.onAppMinimized()
        }
    }

    override fun onDestroy() {

        super.onDestroy()
        runCatching { unregisterReceiver(pipReceiver) }

        if (instance == this) {

            instance = null

            playerViewModel = null
            closeNowPlayingGlobal = null

        }

    }

}

