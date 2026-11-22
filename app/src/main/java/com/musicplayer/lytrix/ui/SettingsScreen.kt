package com.musicplayer.lytrix.ui

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.musicplayer.lytrix.R
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.luminance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import compose.icons.TablerIcons
import compose.icons.tablericons.*
import kotlinx.coroutines.flow.first
import androidx.datastore.preferences.core.*
import androidx.compose.foundation.border
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import android.util.Log
import kotlinx.coroutines.tasks.await
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.Icons
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import com.musicplayer.lytrix.BuildConfig
import com.musicplayer.lytrix.ui.screens.settings.UpdatesScreen
import com.musicplayer.lytrix.ui.screens.settings.UpdateDetailsScreen

enum class LayoutMode { LIST, GRID_2, GRID_3 }

@Composable
fun SettingsNavGraph(
    playerViewModel: PlayerViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToOnlineSettings: () -> Unit = {},
    onOpenSleepTimer: () -> Unit
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val viewModel: SettingsViewModel = viewModel(
        factory = ViewModelProvider.AndroidViewModelFactory.getInstance(context.applicationContext as Application)
    )

    NavHost(navController = navController, startDestination = "settings_main") {
        composable("settings_main") {
            SettingsMainScreen(
                navController = navController,
                viewModel = viewModel,
                playerViewModel = playerViewModel,
                onNavigateBack = onNavigateBack,
                onNavigateToOnlineSettings = onNavigateToOnlineSettings,
                onOpenSleepTimer = onOpenSleepTimer
            )
        }
        composable("settings_theme") {
            ThemeScreen(
                viewModel = viewModel,
                playerViewModel = playerViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("settings_language") {
            LanguageScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("settings_playtime") {
            PlaytimeScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("settings_hidden") {
            HiddenFilesScreen(
                viewModel = playerViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("settings_trash") {
            TrashScreen(
                onBack = { navController.popBackStack() },
                accentColor = com.musicplayer.lytrix.ui.theme.LocalAccentColor.current
            )
        }
        composable("settings_backup") {
            BackupRestoreScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("settings_shop") {
            ShopScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable("settings_referral") {
            ReferralScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable("settings_preference") {
            PreferenceScreen(
                onNavigateBack = { navController.popBackStack() },
                playerViewModel = playerViewModel
            )
        }
        composable("settings_about") {
            AboutScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("settings_feedback") {
            FeedbackScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToFaq = { navController.navigate("settings_faq") },
                onNavigateToHistory = { navController.navigate("settings_feedback_history") }
            )
        }
        composable("settings_feedback_history") {
            FeedbackHistoryScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("settings_faq") {
            FaqScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("settings_updates") {
            UpdatesScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenDetails = { navController.navigate("settings_update_details") }
            )
        }
        composable("settings_update_details") {
            UpdateDetailsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsMainScreen(
    navController: NavController,
    viewModel: SettingsViewModel,
    playerViewModel: PlayerViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToOnlineSettings: () -> Unit = {},
    onOpenSleepTimer: () -> Unit
) {
    BackHandler { onNavigateBack() }

    LaunchedEffect(Unit) {
        com.musicplayer.lytrix.ui.AnalyticsManager.logScreenView("Settings_Screen")
    }

    val themeMode by viewModel.appThemeMode.collectAsState()
    val languageCode by viewModel.languageCode.collectAsState()
    val sleepTimer by playerViewModel.currentSleepTimerMinutes.collectAsState()

    val langDisplay = when(languageCode) {
        "bn" -> "à¦¬à¦¾à¦‚à¦²à¦¾ (Bengali)"
        "hi" -> "à¤¹à¤¿à¤‚à¤¦à¥€ (Hindi)"
        else -> "English"
    }
    val sleepDisplay = if (sleepTimer > 0) "$sleepTimer mins" else "Off"

    val context = LocalContext.current
    val activity = context as? android.app.Activity
    var showPremiumDialog by remember { mutableStateOf(false) }
    var showAdFreeDialog by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val dataStoreManager = remember { DataStoreManager(context) }
    val isPremiumActive by dataStoreManager.isPremiumFlow.collectAsState(initial = false)
    val expiryTime by dataStoreManager.subscriptionExpiryFlow.collectAsState(initial = 0L)

    var isAdFreeState by remember { mutableStateOf(com.musicplayer.lytrix.ui.AdManager.isAdFree(context)) }
    var adFreeExpiryState by remember { mutableLongStateOf(context.getSharedPreferences("premium_prefs", Context.MODE_PRIVATE).getLong("ad_free_expiry", 0L)) }

    DisposableEffect(Unit) {
        val detach = attachPremiumListener(context)
        onDispose { detach() }
    }
    LaunchedEffect(Unit) {
        performSilentSync(context)
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            isAdFreeState = com.musicplayer.lytrix.ui.AdManager.isAdFree(context)
            adFreeExpiryState = context.getSharedPreferences("premium_prefs", Context.MODE_PRIVATE).getLong("ad_free_expiry", 0L)
        }
    }

    val premiumStatus = remember(isPremiumActive, expiryTime, isAdFreeState, adFreeExpiryState) {
        val currentTime = System.currentTimeMillis()

        val isPermFromPrefs = context.getSharedPreferences("premium_prefs", Context.MODE_PRIVATE).getBoolean("is_permanent", false)
        if (isPremiumActive || isPermFromPrefs) {
            val isPremPerm = expiryTime == Long.MAX_VALUE || isPermFromPrefs
            if (isPremPerm) {
                "Lifetime 👑"
            } else if (expiryTime > currentTime) {
                val premiumDays = kotlin.math.ceil((expiryTime - currentTime).toDouble() / (1000L * 60 * 60 * 24)).toInt().coerceAtLeast(1)
                "$premiumDays Days Left"
            } else {
                "Not Active"
            }
        } else if (isAdFreeState && adFreeExpiryState > currentTime) {
            val adFreeHours = ((adFreeExpiryState - currentTime) / (1000L * 60 * 60)).coerceAtLeast(1)
            "Ad-Free (${adFreeHours}h left)"
        } else {
            "Not Active"
        }
    }
    val bgColor = MaterialTheme.colorScheme.background
    val cardBgColor = MaterialTheme.colorScheme.surfaceContainer

    Box(modifier = Modifier.fillMaxSize().background(bgColor)) {
        val accentColor = com.musicplayer.lytrix.ui.theme.LocalAccentColor.current
        com.musicplayer.lytrix.ui.component.AmbientThemeGlow(
            tint = accentColor,
            intensity = 0.22f
        )

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Settings", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(TablerIcons.ArrowLeft, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            containerColor = Color.Transparent
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            item {
                SettingsCardGroup(cardBgColor) {
                    PremiumSettingsItem(icon = TablerIcons.Clock, title = "Playtime", value = "", onClick = { navController.navigate("settings_playtime") })
                    HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f), modifier = Modifier.padding(horizontal = 16.dp))
                    PremiumSettingsItem(icon = TablerIcons.CloudUpload, title = "Backup & restore", value = "", onClick = { navController.navigate("settings_backup") })

                    HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f), modifier = Modifier.padding(horizontal = 16.dp))
                    PremiumSettingsItem(icon = TablerIcons.BuildingStore, title = "Lytrix Rewards", value = "", onClick = { navController.navigate("settings_shop") })
                    HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f), modifier = Modifier.padding(horizontal = 16.dp))
                    PremiumSettingsItem(icon = TablerIcons.Gift, title = "Share For Premium", value = "", onClick = { navController.navigate("settings_referral") })
                }
            }

            item {
                SettingsCardGroup(cardBgColor) {
                    PremiumSettingsItem(icon = TablerIcons.Palette, title = "Theme", value = themeMode, onClick = { navController.navigate("settings_theme") })
                    HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f), modifier = Modifier.padding(horizontal = 16.dp))

                    val isPremiumUser = isPremiumActive && (expiryTime == Long.MAX_VALUE || expiryTime > System.currentTimeMillis())
                    PremiumSettingsItem(icon = TablerIcons.Crown, title = "Premium", value = premiumStatus, isPremium = true, onClick = { showPremiumDialog = true })
                    HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f), modifier = Modifier.padding(horizontal = 16.dp))
                    PremiumSettingsItem(
                        icon = TablerIcons.Ban, 
                        title = "Ad-free", 
                        value = if (isPremiumUser) "Included 👑" else if (isAdFreeState) AdManager.getRemainingAdFreeTime(context).ifEmpty { "Active" } else "Watch to unlock", 
                        isAdFree = true, 
                        onClick = {
                            if (isPremiumUser) {
                                Toast.makeText(context, "👑 You have Premium! Ad-free music is fully included.", Toast.LENGTH_SHORT).show()
                            } else {
                                showAdFreeDialog = true
                            }
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f), modifier = Modifier.padding(horizontal = 16.dp))
                    PremiumSettingsItem(icon = TablerIcons.EyeOff, title = "Hidden files", value = "", onClick = { navController.navigate("settings_hidden") })
                }
            }

            item {
                SettingsCardGroup(cardBgColor) {
                    PremiumSettingsItem(
                        icon = TablerIcons.Cloud,
                        title = "YouTube & Online Settings",
                        value = "",
                        onClick = onNavigateToOnlineSettings
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f), modifier = Modifier.padding(horizontal = 16.dp))
                    PremiumSettingsItem(icon = TablerIcons.AdjustmentsHorizontal, title = "Preferences", value = "", onClick = { navController.navigate("settings_preference") })
                    HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f), modifier = Modifier.padding(horizontal = 16.dp))
                    PremiumSettingsItem(icon = TablerIcons.Trash, title = "Recently deleted", value = "", onClick = { navController.navigate("settings_trash") })
                    HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f), modifier = Modifier.padding(horizontal = 16.dp))
                    PremiumSettingsItem(icon = TablerIcons.Language, title = "Language", value = langDisplay, onClick = { navController.navigate("settings_language") })
                }
            }

            item {
                SettingsCardGroup(cardBgColor) {
                    PremiumSettingsItem(icon = TablerIcons.Message, title = "Feedback", value = "", onClick = { navController.navigate("settings_feedback") })
                    HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f), modifier = Modifier.padding(horizontal = 16.dp))
                    PremiumSettingsItem(
                        icon = TablerIcons.Refresh,
                        title = "Updates",
                        value = "v${BuildConfig.VERSION_NAME}",
                        onClick = {
                            navController.navigate("settings_updates")
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f), modifier = Modifier.padding(horizontal = 16.dp))
                    PremiumSettingsItem(icon = TablerIcons.InfoCircle, title = "About Lytrix Player", value = "v${BuildConfig.VERSION_NAME}", onClick = { navController.navigate("settings_about") })
                }
            }
            item { Spacer(modifier = Modifier.height(40.dp)) }
        }
    }
}

    if (showPremiumDialog) {
        PremiumActivationDialog(
            accentColor = com.musicplayer.lytrix.ui.theme.LocalAccentColor.current,
            onDismiss = { showPremiumDialog = false },
            onSuccess = {
                showPremiumDialog = false
                isAdFreeState = com.musicplayer.lytrix.ui.AdManager.isAdFree(context)
            }
        )
    }

    if (showAdFreeDialog) {
        LytrixAdFreeSheet(
            onDismiss = {
                showAdFreeDialog = false
                isAdFreeState = com.musicplayer.lytrix.ui.AdManager.isAdFree(context)
            },
            onNavigateToPremium = {
                showAdFreeDialog = false
                isAdFreeState = com.musicplayer.lytrix.ui.AdManager.isAdFree(context)
                showPremiumDialog = true
            }
        )
    }
}

@Composable
fun SettingsCardGroup(bgColor: Color, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
        elevation = CardDefaults.elevatedCardElevation(0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
fun PremiumSettingsItem(icon: ImageVector, title: String, value: String, isPremium: Boolean = false, isAdFree: Boolean = false, onClick: () -> Unit) {
    val premiumColor = Color(0xFF03A9F4)
    val adFreeColor = Color(0xFFFFB74D)

    val isLightMode = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val defaultIconColor = if (isLightMode) {
        Color.Black.copy(alpha = 0.65f)
    } else {
        Color.White.copy(alpha = 0.7f)
    }

    val iconTint = when {
        isPremium -> premiumColor
        isAdFree -> adFreeColor
        else -> defaultIconColor
    }

    val textColor = when {
        isPremium -> premiumColor
        isAdFree -> adFreeColor
        else -> MaterialTheme.colorScheme.onBackground
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 18.dp, horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1.5f)) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
            if (value.isNotEmpty()) {
                Text(
                    text = value,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    textAlign = TextAlign.End,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Icon(imageVector = TablerIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f), modifier = Modifier.size(20.dp))
        }
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreferenceScreen(
    onNavigateBack: () -> Unit,
    playerViewModel: PlayerViewModel? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val privacyManager = remember { PrivacyPreferencesManager(context) }
    val pauseLocalListenHistory by privacyManager.pauseLocalListenHistory.collectAsState(
        initial = PrivacyPreferencesManager.isPauseLocalListenHistory(context)
    )
    var showClearLocalDialog by remember { mutableStateOf(false) }

    val dataStoreManager = remember { DataStoreManager(context) }
    val keepScreenOn by dataStoreManager.keepScreenOnFlow.collectAsState(initial = false)
    val lockScreenPlaying by dataStoreManager.lockScreenPlayingFlow.collectAsState(initial = false)
    val useNewPlayerDesign by dataStoreManager.useNewPlayerDesignFlow.collectAsState(initial = true)
    val playerStyle by dataStoreManager.playerStyleFlow.collectAsState(initial = DataStoreManager.PLAYER_STYLE_MATERIAL3)
    var showPlayerStyleDialog by remember { mutableStateOf(false) }
    val nextSongTransitionAnimation by dataStoreManager.nextSongTransitionAnimationFlow.collectAsState(initial = true)
    val playerGesturesEnabled by dataStoreManager.playerGesturesEnabledFlow.collectAsState(initial = true)

    val prefs = remember { context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE) }
    var showVideoHistory by remember { mutableStateOf(prefs.getBoolean("show_video_history", true)) }
    var playTogether by remember { mutableStateOf(prefs.getBoolean("play_together", false)) }
    var gaplessPlayback by remember { mutableStateOf(prefs.getBoolean("gapless_playback", false)) }
    var crossfade by remember { mutableStateOf(prefs.getBoolean("crossfade", false)) }
    var crossfadeDuration by remember { mutableFloatStateOf(prefs.getFloat("crossfade_duration", 4f)) }
    var audioNormalization by remember { mutableStateOf(prefs.getBoolean("audio_normalization", false)) }
    var loudnessLevel by remember { mutableStateOf(com.musicplayer.lytrix.service.audio.LoudnessLevel.fromStored(prefs.getString("loudness_level", null))) }
    var skipSilence by remember { mutableStateOf(prefs.getBoolean("skip_silence", false)) }
    var skipSilenceInstant by remember { mutableStateOf(prefs.getBoolean("skip_silence_instant", false)) }
    var aodConfig by remember { mutableStateOf(com.musicplayer.lytrix.aod.AodPreferences.read(context)) }
    var showLoudnessDialog by remember { mutableStateOf(false) }
    var showEqualizerSheet by remember { mutableStateOf(false) }
    var showAlarmDialog by remember { mutableStateOf(false) }
    var showSeparatorsDialog by remember { mutableStateOf(false) }
    var showAppIconDialog by remember { mutableStateOf(false) }
    var alarmCount by remember { mutableIntStateOf(com.musicplayer.lytrix.alarm.MusicAlarmStore.loadAll(context).size) }
    var separatorsPreview by remember { mutableStateOf(com.musicplayer.lytrix.utils.ArtistSeparatorManager.loadSeparators(context).joinToString("  ")) }
    var currentAppIcon by remember { mutableStateOf(com.musicplayer.lytrix.appicon.AppIconManager.getCurrentIcon(context)) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val pm = remember { context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager }
    var continuousPlay by remember { mutableStateOf(pm.isIgnoringBatteryOptimizations(context.packageName)) }

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                continuousPlay = pm.isIgnoringBatteryOptimizations(context.packageName)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val accentColor = com.musicplayer.lytrix.ui.theme.LocalAccentColor.current
    val isLightMode = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val iconTint = if (isLightMode) Color.Black.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.7f)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Preferences", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(TablerIcons.ArrowLeft, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            val cardBgColor = MaterialTheme.colorScheme.surfaceContainer

            SettingsCardGroup(cardBgColor) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(TablerIcons.Sun, contentDescription = null, tint = iconTint)
                    Spacer(modifier = Modifier.width(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Keep screen on", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                        Text("Stay on while on the player screen", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                    }
                    Switch(
                        checked = keepScreenOn,
                        onCheckedChange = { coroutineScope.launch { dataStoreManager.setKeepScreenOn(it) } },
                        colors = SwitchDefaults.colors(checkedTrackColor = accentColor)
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f), modifier = Modifier.padding(horizontal = 16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(TablerIcons.Lock, contentDescription = null, tint = iconTint)
                    Spacer(modifier = Modifier.width(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Lock screen playing", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                        Text("Show now playing when lock screen", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                    }
                    Switch(
                        checked = lockScreenPlaying,
                        onCheckedChange = { coroutineScope.launch { dataStoreManager.setLockScreenPlaying(it) } },
                        colors = SwitchDefaults.colors(checkedTrackColor = accentColor)
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f), modifier = Modifier.padding(horizontal = 16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showPlayerStyleDialog = true }
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(TablerIcons.PlayerPlay, contentDescription = null, tint = iconTint)
                    Spacer(modifier = Modifier.width(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Now Playing design", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                        val subtitle = when (playerStyle) {
                            DataStoreManager.PLAYER_STYLE_APPLE_MUSIC -> "Apple Music"
                            DataStoreManager.PLAYER_STYLE_CLASSIC -> "Classic Player"
                            DataStoreManager.PLAYER_STYLE_M3_EXPRESSIVE -> "M3 Expressive (Wavy & Morph)"
                            DataStoreManager.PLAYER_STYLE_CINEMA -> "Immersive Cinema"
                            else -> "Material 3 (Default)"
                        }
                        Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                    }
                    Icon(imageVector = TablerIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f), modifier = Modifier.size(20.dp))
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f), modifier = Modifier.padding(horizontal = 16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(TablerIcons.Disc, contentDescription = null, tint = iconTint)
                    Spacer(modifier = Modifier.width(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Next song track transition", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                        Text("Animated flower deck transition before song ends", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                    }
                    Switch(
                        checked = nextSongTransitionAnimation,
                        onCheckedChange = { coroutineScope.launch { dataStoreManager.setNextSongTransitionAnimation(it) } },
                        colors = SwitchDefaults.colors(checkedTrackColor = accentColor)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Privacy settings", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))

            SettingsCardGroup(cardBgColor) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(TablerIcons.PlayerPause, contentDescription = null, tint = iconTint)
                    Spacer(modifier = Modifier.width(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Pause listen history", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                        Text("Pause tracking listening history for local songs", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                    }
                    Switch(
                        checked = pauseLocalListenHistory,
                        onCheckedChange = { checked ->
                            coroutineScope.launch {
                                privacyManager.setPauseLocalListenHistory(checked)
                            }
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = accentColor)
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f), modifier = Modifier.padding(horizontal = 16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showClearLocalDialog = true }
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(TablerIcons.Trash, contentDescription = null, tint = iconTint)
                    Spacer(modifier = Modifier.width(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Clear listen history", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                        Text("Clear listening history for local songs", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                    }
                }
            }

            if (showPlayerStyleDialog) {
                val options = listOf(
                    DataStoreManager.PLAYER_STYLE_MATERIAL3 to "Material 3 (Default)",
                    DataStoreManager.PLAYER_STYLE_APPLE_MUSIC to "Apple Music",
                    DataStoreManager.PLAYER_STYLE_CLASSIC to "Classic Player",
                    DataStoreManager.PLAYER_STYLE_M3_EXPRESSIVE to "M3 Expressive (Wavy & Morph)",
                    DataStoreManager.PLAYER_STYLE_CINEMA to "Immersive Cinema"
                )
                AlertDialog(
                    onDismissRequest = { showPlayerStyleDialog = false },
                    title = {
                        Text(
                            text = "Select Player Design",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    text = {
                        Column {
                            options.forEach { (style, label) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            coroutineScope.launch {
                                                dataStoreManager.setPlayerStyle(style)
                                            }
                                            showPlayerStyleDialog = false
                                        }
                                        .padding(vertical = 12.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = (playerStyle == style),
                                        onClick = {
                                            coroutineScope.launch {
                                                dataStoreManager.setPlayerStyle(style)
                                            }
                                            showPlayerStyleDialog = false
                                        },
                                        colors = RadioButtonDefaults.colors(selectedColor = accentColor)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = label,
                                        fontSize = 15.sp,
                                        fontWeight = if (playerStyle == style) FontWeight.SemiBold else FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showPlayerStyleDialog = false }) {
                            Text("Close", color = accentColor)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(24.dp)
                )
            }

            if (showClearLocalDialog) {
                AlertDialog(
                    onDismissRequest = { showClearLocalDialog = false },
                    title = {
                        Text(
                            "Clear listen history",
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Text(
                            "Are you sure you want to clear listening history for local songs? This will reset Recently Played and Most Played for all local tracks.",
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showClearLocalDialog = false
                                playerViewModel?.clearLocalListenHistory(context)
                                Toast.makeText(context, "Local listen history cleared", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text("Clear", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showClearLocalDialog = false }) {
                            Text("Cancel", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surface
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Playback settings", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))

            SettingsCardGroup(cardBgColor) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.TouchApp, contentDescription = null, tint = iconTint)
                    Spacer(modifier = Modifier.width(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Player gestures", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                        Text("Double tap cover to seek 5s / play-pause, swipe to change track", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), lineHeight = 16.sp)
                    }
                    Switch(
                        checked = playerGesturesEnabled,
                        onCheckedChange = {
                            coroutineScope.launch {
                                dataStoreManager.setPlayerGesturesEnabled(it)
                            }
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = accentColor)
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f), modifier = Modifier.padding(horizontal = 16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(TablerIcons.History, contentDescription = null, tint = iconTint)
                    Spacer(modifier = Modifier.width(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Video Playback History", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                        Text("Keep track of your recently watched videos on the videos page", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), lineHeight = 16.sp)
                    }
                    Switch(
                        checked = showVideoHistory,
                        onCheckedChange = {
                            showVideoHistory = it
                            prefs.edit().putBoolean("show_video_history", it).apply()
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = accentColor)
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f), modifier = Modifier.padding(horizontal = 16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(TablerIcons.PlayerPlay, contentDescription = null, tint = iconTint)
                    Spacer(modifier = Modifier.width(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Ensure continuous background play", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                        Text("Grant permission to prevent background play from being forcibly interrupted", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), lineHeight = 16.sp)
                    }
                    Switch(
                        checked = continuousPlay,
                        onCheckedChange = {
                            if (!continuousPlay) {
                                val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = android.net.Uri.parse("package:${context.packageName}")
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Permission request failed", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                val intent = Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                context.startActivity(intent)
                            }
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = accentColor)
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f), modifier = Modifier.padding(horizontal = 16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(TablerIcons.Headphones, contentDescription = null, tint = iconTint)
                    Spacer(modifier = Modifier.width(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Play together with other apps", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                    }
                    Switch(checked = playTogether, onCheckedChange = { playTogether = it; prefs.edit().putBoolean("play_together", it).apply() }, colors = SwitchDefaults.colors(checkedTrackColor = accentColor))
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f), modifier = Modifier.padding(horizontal = 16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(TablerIcons.Activity, contentDescription = null, tint = iconTint)
                    Spacer(modifier = Modifier.width(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Gapless playback", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                        Text("Removes any gaps or pauses that may occur in between tracks", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), lineHeight = 16.sp)
                    }
                    Switch(
                        checked = gaplessPlayback,
                        onCheckedChange = { isChecked ->
                            gaplessPlayback = isChecked
                            prefs.edit().putBoolean("gapless_playback", isChecked).apply()

                            if (isChecked && crossfade) {
                                crossfade = false
                                prefs.edit().putBoolean("crossfade", false).apply()
                            }
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = accentColor)
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f), modifier = Modifier.padding(horizontal = 16.dp))

                Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = if (crossfade) 8.dp else 16.dp, start = 24.dp, end = 24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(TablerIcons.ArrowsRightLeft, contentDescription = null, tint = iconTint)
                        Spacer(modifier = Modifier.width(20.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Crossfade", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                            Text("Set the fade and overlap between tracks", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                        }
                        Switch(
                            checked = crossfade,
                            onCheckedChange = { isChecked ->
                                crossfade = isChecked
                                prefs.edit().putBoolean("crossfade", isChecked).apply()

                                if (isChecked && gaplessPlayback) {
                                    gaplessPlayback = false
                                    prefs.edit().putBoolean("gapless_playback", false).apply()
                                }
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = accentColor)
                        )
                    }

                    if (crossfade) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(modifier = Modifier.padding(start = 44.dp)) {
                            Text(
                                text = "${crossfadeDuration.toInt()}s",
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Slider(
                                value = crossfadeDuration,
                                onValueChange = { crossfadeDuration = it },
                                onValueChangeFinished = {
                                    prefs.edit().putFloat("crossfade_duration", crossfadeDuration).apply()
                                },
                                valueRange = 1f..10f,
                                steps = 8,
                                colors = SliderDefaults.colors(
                                    thumbColor = accentColor,
                                    activeTrackColor = accentColor
                                )
                            )
                        }
                    }
                }

                if (playerViewModel != null) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f), modifier = Modifier.padding(horizontal = 16.dp))

                    val eqEnabledState by playerViewModel.isEqEnabled.collectAsState()
                    val eqModeState by playerViewModel.eqMode.collectAsState()
                    val activeEqProfileState by playerViewModel.activeEqProfile.collectAsState()
                    val eqPresetState by playerViewModel.selectedPreset.collectAsState()
                    val eqSummary = when {
                        !eqEnabledState -> "Off · Tap to tune"
                        eqModeState == com.musicplayer.lytrix.eq.data.EqMode.PROFILE && activeEqProfileState != null -> "AutoEq · ${activeEqProfileState!!.deviceModel}"
                        else -> "On · $eqPresetState"
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showEqualizerSheet = true }
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(TablerIcons.AdjustmentsHorizontal, contentDescription = null, tint = iconTint)
                        Spacer(modifier = Modifier.width(20.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Parametric Equalizer & AutoEq", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                            Text(eqSummary, fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), maxLines = 1)
                        }
                        Icon(TablerIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f), modifier = Modifier.padding(horizontal = 16.dp))

                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(TablerIcons.Volume, contentDescription = null, tint = iconTint)
                        Spacer(modifier = Modifier.width(20.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Volume normalization", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                            Text("Balance loudness across local, JioSaavn & YouTube tracks", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), lineHeight = 16.sp)
                        }
                        Switch(
                            checked = audioNormalization,
                            onCheckedChange = { isChecked ->
                                audioNormalization = isChecked
                                prefs.edit().putBoolean("audio_normalization", isChecked).apply()
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = accentColor)
                        )
                    }
                    if (audioNormalization) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f))
                                .clickable { showLoudnessDialog = true }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Target loudness level", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
                                Text("${loudnessLevel.displayName} (${loudnessLevel.description})", fontSize = 12.sp, color = accentColor)
                            }
                            Icon(TablerIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f), modifier = Modifier.padding(horizontal = 16.dp))

                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(TablerIcons.PlayerSkipForward, contentDescription = null, tint = iconTint)
                        Spacer(modifier = Modifier.width(20.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Skip silence", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                            Text("Trim silent passages in local & online audio / podcasts", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), lineHeight = 16.sp)
                        }
                        Switch(
                            checked = skipSilence,
                            onCheckedChange = { isChecked ->
                                skipSilence = isChecked
                                prefs.edit().putBoolean("skip_silence", isChecked).apply()
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = accentColor)
                        )
                    }
                    if (skipSilence) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 44.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Instant skip long silences", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
                                Text("Jump ahead when dead air exceeds 2 seconds", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                            }
                            Switch(
                                checked = skipSilenceInstant,
                                onCheckedChange = { isChecked ->
                                    skipSilenceInstant = isChecked
                                    prefs.edit().putBoolean("skip_silence_instant", isChecked).apply()
                                },
                                colors = SwitchDefaults.colors(checkedTrackColor = accentColor)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Always-On Display & Pocket Lock", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))

            SettingsCardGroup(cardBgColor) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { com.musicplayer.lytrix.aod.AodLaunchTrigger.launch(initialLocked = false) }
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(TablerIcons.BatteryAutomotive, contentDescription = null, tint = iconTint)
                    Spacer(modifier = Modifier.width(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Launch Always-On Display", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                        Text("AMOLED pure-black player with live clock & lyrics", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                    }
                    Icon(imageVector = TablerIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f), modifier = Modifier.size(20.dp))
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f), modifier = Modifier.padding(horizontal = 16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { com.musicplayer.lytrix.aod.AodLaunchTrigger.launch(initialLocked = true) }
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(TablerIcons.Lock, contentDescription = null, tint = iconTint)
                    Spacer(modifier = Modifier.width(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Pocket Touch Lock", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                        Text("Lock screen touches & arm proximity blackout", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                    }
                    Icon(imageVector = TablerIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f), modifier = Modifier.size(20.dp))
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f), modifier = Modifier.padding(horizontal = 16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(TablerIcons.Sun, contentDescription = null, tint = iconTint)
                    Spacer(modifier = Modifier.width(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("System Screensaver (DreamService)", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                        Text("Show AOD automatically while charging or docked", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), lineHeight = 16.sp)
                    }
                    Switch(
                        checked = aodConfig.dreamEnabled,
                        onCheckedChange = { checked ->
                            com.musicplayer.lytrix.aod.AodPreferences.setDreamEnabled(context, checked)
                            aodConfig = com.musicplayer.lytrix.aod.AodPreferences.read(context)
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = accentColor)
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f), modifier = Modifier.padding(horizontal = 16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            try {
                                context.startActivity(Intent(android.provider.Settings.ACTION_DREAM_SETTINGS))
                            } catch (_: Exception) {
                                Toast.makeText(context, "Screensaver settings unavailable on this device", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(TablerIcons.Settings, contentDescription = null, tint = iconTint)
                    Spacer(modifier = Modifier.width(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Open System Screensaver Settings", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                        Text("Select Lytrix Always-On Display as Android Dream", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                    }
                    Icon(imageVector = TablerIcons.ExternalLink, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f), modifier = Modifier.size(20.dp))
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f), modifier = Modifier.padding(horizontal = 16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(TablerIcons.Eye, contentDescription = null, tint = iconTint)
                    Spacer(modifier = Modifier.width(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Burn-in pixel shift", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                        Text("Subtle periodic OLED offset protection", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                    }
                    Switch(
                        checked = aodConfig.pixelShiftEnabled,
                        onCheckedChange = { checked ->
                            com.musicplayer.lytrix.aod.AodPreferences.setPixelShiftEnabled(context, checked)
                            aodConfig = com.musicplayer.lytrix.aod.AodPreferences.read(context)
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = accentColor)
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f), modifier = Modifier.padding(horizontal = 16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(TablerIcons.ShieldLock, contentDescription = null, tint = iconTint)
                    Spacer(modifier = Modifier.width(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Proximity pocket blackout", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                        Text("Turn black & engage touch lock when covered in pocket", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), lineHeight = 16.sp)
                    }
                    Switch(
                        checked = aodConfig.proximityPocketLock,
                        onCheckedChange = { checked ->
                            com.musicplayer.lytrix.aod.AodPreferences.setProximityPocketLock(context, checked)
                            aodConfig = com.musicplayer.lytrix.aod.AodPreferences.read(context)
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = accentColor)
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f), modifier = Modifier.padding(horizontal = 16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val next = if (aodConfig.unlockMethod == com.musicplayer.lytrix.aod.AodPreferences.UNLOCK_HOLD) {
                                com.musicplayer.lytrix.aod.AodPreferences.UNLOCK_SLIDE
                            } else {
                                com.musicplayer.lytrix.aod.AodPreferences.UNLOCK_HOLD
                            }
                            com.musicplayer.lytrix.aod.AodPreferences.setUnlockMethod(context, next)
                            aodConfig = com.musicplayer.lytrix.aod.AodPreferences.read(context)
                        }
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(TablerIcons.ArrowsRightLeft, contentDescription = null, tint = iconTint)
                    Spacer(modifier = Modifier.width(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Pocket lock unlock gesture", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                        val gestureLabel = if (aodConfig.unlockMethod == com.musicplayer.lytrix.aod.AodPreferences.UNLOCK_HOLD) {
                            "Long press (Hold 1.5s)"
                        } else {
                            "Slide to unlock"
                        }
                        Text(gestureLabel, fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f), modifier = Modifier.padding(horizontal = 16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { com.musicplayer.lytrix.utils.QuickSettingsTileHelper.requestAodTile(context) }
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(TablerIcons.LayoutGrid, contentDescription = null, tint = iconTint)
                    Spacer(modifier = Modifier.width(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Add \"Always-On Player\" Quick Tile", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                        Text("Pin directly to Android Quick Settings panel", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                    }
                    Icon(imageVector = TablerIcons.Plus, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f), modifier = Modifier.padding(horizontal = 16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { com.musicplayer.lytrix.utils.QuickSettingsTileHelper.requestRecognizeSongTile(context) }
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(TablerIcons.Music, contentDescription = null, tint = iconTint)
                    Spacer(modifier = Modifier.width(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Add \"Recognize Song\" Quick Tile", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                        Text("Launch Shazam recognition from notification shade", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                    }
                    Icon(imageVector = TablerIcons.Plus, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Personalization & utilities", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))

            SettingsCardGroup(cardBgColor) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAlarmDialog = true }
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(TablerIcons.Alarm, contentDescription = null, tint = iconTint)
                    Spacer(modifier = Modifier.width(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Music Alarm Clock", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                        val alarmSummary = if (alarmCount == 0) "Wake up to any playlist with gentle fade-in" else "$alarmCount alarm(s) configured"
                        Text(alarmSummary, fontSize = 13.sp, color = accentColor)
                    }
                    Icon(TablerIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f), modifier = Modifier.padding(horizontal = 16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showSeparatorsDialog = true }
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(TablerIcons.Users, contentDescription = null, tint = iconTint)
                    Spacer(modifier = Modifier.width(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Artist Separators", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                        Text("Active tokens: $separatorsPreview", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(TablerIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f), modifier = Modifier.padding(horizontal = 16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAppIconDialog = true }
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(TablerIcons.Palette, contentDescription = null, tint = iconTint)
                    Spacer(modifier = Modifier.width(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("App Icon Style", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                        Text(currentAppIcon.title, fontSize = 13.sp, color = accentColor)
                    }
                    Icon(TablerIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        if (showAlarmDialog) {
            com.musicplayer.lytrix.ui.components.MusicAlarmDialog(
                accentColor = accentColor,
                onDismiss = {
                    showAlarmDialog = false
                    alarmCount = com.musicplayer.lytrix.alarm.MusicAlarmStore.loadAll(context).size
                }
            )
        }

        if (showSeparatorsDialog) {
            com.musicplayer.lytrix.ui.components.ArtistSeparatorsDialog(
                accentColor = accentColor,
                onDismiss = { showSeparatorsDialog = false },
                onSaved = { updated -> separatorsPreview = updated.joinToString("  ") }
            )
        }

        if (showAppIconDialog) {
            com.musicplayer.lytrix.ui.components.AppIconPickerDialog(
                accentColor = accentColor,
                onDismiss = { showAppIconDialog = false },
                onIconChanged = { currentAppIcon = it }
            )
        }

        if (showLoudnessDialog) {
            AlertDialog(
                onDismissRequest = { showLoudnessDialog = false },
                title = { Text("Target Loudness Level", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        com.musicplayer.lytrix.service.audio.LoudnessLevel.entries.forEach { option ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        loudnessLevel = option
                                        prefs.edit().putString("loudness_level", option.name).apply()
                                        showLoudnessDialog = false
                                    }
                                    .padding(vertical = 10.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = loudnessLevel == option,
                                    onClick = {
                                        loudnessLevel = option
                                        prefs.edit().putString("loudness_level", option.name).apply()
                                        showLoudnessDialog = false
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(option.displayName, fontWeight = FontWeight.SemiBold)
                                    Text(option.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showLoudnessDialog = false }) { Text("Close") }
                }
            )
        }

        if (showEqualizerSheet && playerViewModel != null) {
            EqualizerBottomSheet(
                viewModel = playerViewModel,
                onDismiss = { showEqualizerSheet = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(viewModel: SettingsViewModel, onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isProcessing by remember { mutableStateOf(false) }
    var isGoogleLoading by remember { mutableStateOf(false) }

    val isUserLoggedIn by viewModel.isUserLoggedIn.collectAsStateWithLifecycle()
    val userName by viewModel.userName.collectAsStateWithLifecycle()
    val userEmail by viewModel.userEmail.collectAsStateWithLifecycle()
    val loginMethod by viewModel.loginMethod.collectAsStateWithLifecycle()
    val isEmailLoading by viewModel.isEmailLoading.collectAsStateWithLifecycle()
    val resendCooldown by viewModel.resendCooldown.collectAsStateWithLifecycle()

    val WEB_CLIENT_ID = "597739659905-rql30u499qhg4drtmsobb34tkp9d0cgg.apps.googleusercontent.com"

    var showEmailDialog by remember { mutableStateOf(false) }
    var emailTab by remember { mutableIntStateOf(0) }
    var nameField by remember { mutableStateOf("") }
    var emailField by remember { mutableStateOf("") }
    var passwordField by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var dialogError by remember { mutableStateOf<String?>(null) }

    // NEW: Verification success state
    var showVerifyScreen by remember { mutableStateOf(false) }
    var verifyEmailSentTo by remember { mutableStateOf("") }
    var showImportWarningDialog by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var autoBackupStatus by remember { mutableStateOf(com.musicplayer.lytrix.backup.ScheduledBackupManager.getStatus(context)) }
    var showPlaylistPortabilityDialog by remember { mutableStateOf(false) }
    val autoBackupFolderLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            com.musicplayer.lytrix.backup.ScheduledBackupManager.setTreeUri(context, uri)
            autoBackupStatus = com.musicplayer.lytrix.backup.ScheduledBackupManager.getStatus(context)
            Toast.makeText(context, "Auto-backup folder linked!", Toast.LENGTH_SHORT).show()
        }
    }

    fun importLocalBackup(uri: android.net.Uri, uploadToCloud: Boolean) {
        isProcessing = true
        coroutineScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val jsonString = context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
                        ?: throw Exception("Cannot read file")
                    restoreBackupData(context, JSONObject(jsonString))
                }
                if (uploadToCloud) {
                    BackupSyncState.lastSyncTime = 0L
                    performSilentSync(context) // Room ছাড়া cloud এ upload করবে
                    var waited = 0
                    while (BackupSyncState.isSyncing && waited < 20000) { delay(300); waited += 300 }
                }
                Toast.makeText(context, "Restore Successful! Restarting...", Toast.LENGTH_LONG).show()
                delay(1500)
                val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                context.startActivity(Intent.makeRestartActivityTask(intent?.component))
                Runtime.getRuntime().exit(0)
            } catch (e: Exception) {
                Toast.makeText(context, "Restore Failed! Invalid File.", Toast.LENGTH_LONG).show()
            } finally { isProcessing = false }
        }
    }

    fun openEmailApp() {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_EMAIL)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("mailto:")))
            } catch (_: Exception) {
                Toast.makeText(context, "Please open Gmail manually", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.CreateDocument("*/*")) { uri ->
        if (uri != null) {
            isProcessing = true
            coroutineScope.launch {
                try {
                    val backupData = generateBackupData(context, includeRoom = true)
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { stream ->
                            val writer = java.io.OutputStreamWriter(stream, Charsets.UTF_8)
                            writer.write(backupData.toString())
                            writer.flush()
                        }
                    }
                    Toast.makeText(context, "Backup Successful! ✨", Toast.LENGTH_SHORT).show()
                } catch (t: Throwable) {
                    Toast.makeText(context, "Backup Failed: ${t.localizedMessage ?: "Error saving backup"}", Toast.LENGTH_LONG).show()
                } finally { isProcessing = false }
            }
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            if (isUserLoggedIn) {
                pendingImportUri = uri
                showImportWarningDialog = true
            } else {
                importLocalBackup(uri, uploadToCloud = false)
            }
        }
    }

    val accentColor = com.musicplayer.lytrix.ui.theme.LocalAccentColor.current
    val isLightMode = MaterialTheme.colorScheme.background.luminance() > 0.5f

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup & Sync", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(TablerIcons.ArrowLeft, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = if (isLightMode) Color(0xFFF3F4F6) else Color(0xFF1E1E1E))) {
                    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(modifier = Modifier.size(80.dp).background(accentColor.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(TablerIcons.CloudUpload, contentDescription = null, tint = accentColor, modifier = Modifier.size(40.dp))
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Cloud Sync", color = MaterialTheme.colorScheme.onBackground, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Securely backup your playlists, likes, and history.", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 18.sp)
                        Spacer(modifier = Modifier.height(24.dp))

                        if (isUserLoggedIn) {
                            Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f), RoundedCornerShape(16.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(TablerIcons.User, contentDescription = null, tint = accentColor, modifier = Modifier.size(40.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(userName, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                                    Text(userEmail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                                    Spacer(modifier = Modifier.height(2.dp))
                                    val methodLabel = if (loginMethod == "google") "Google Account" else "Email Account"
                                    Text(methodLabel, fontSize = 10.sp, color = accentColor, fontWeight = FontWeight.Bold, modifier = Modifier.background(accentColor.copy(alpha=0.12f), RoundedCornerShape(6.dp)).padding(horizontal=6.dp, vertical=2.dp))
                                }
                                IconButton(onClick = {
                                    if (!isProcessing) {
                                        isProcessing = true
                                        coroutineScope.launch {
                                            viewModel.signOut() // আগে signOut
                                            kotlinx.coroutines.delay(500)
                                            withContext(Dispatchers.IO) {
                                                context.dataStore.edit { it.clear() }
                                                listOf("app_preferences", "online_liked_songs", "online_liked_podcasts", "detailed_playtime", "premium_prefs", "rewards_prefs").forEach {
                                                    context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().apply()
                                                }
                                                context.getSharedPreferences("backup_prefs", Context.MODE_PRIVATE)
                                                    .edit().putString("last_synced_uid", "").putString("last_backup_hash", "").commit()
                                            }
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(context, "Logged out!", Toast.LENGTH_SHORT).show()
                                                kotlinx.coroutines.delay(500)
                                                val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                                                context.startActivity(Intent.makeRestartActivityTask(intent?.component))
                                                Runtime.getRuntime().exit(0)
                                            }
                                        }
                                    }
                                }) { Icon(TablerIcons.Logout, contentDescription = "Logout", tint = Color.Red.copy(alpha=0.7f)) }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = {
                                isProcessing = true
                                coroutineScope.launch(Dispatchers.IO) {
                                    val backupData = generateBackupData(context, includeRoom = false)
                                    withContext(Dispatchers.Main) {
                                        viewModel.uploadBackupToFirebase(backupData, onSuccess = { isProcessing = false; Toast.makeText(context, "Cloud Sync Successful! ☁️", Toast.LENGTH_SHORT).show() }, onError = { err -> isProcessing = false; Toast.makeText(context, "Sync Error: $err", Toast.LENGTH_LONG).show() })
                                    }
                                }
                            }, modifier = Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = accentColor), shape = CircleShape) {
                                Icon(TablerIcons.CloudUpload, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Sync Now", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(onClick = {
                                isProcessing = true
                                viewModel.downloadBackupFromFirebase(onSuccess = { backupData ->
                                    coroutineScope.launch {
                                        restoreBackupData(context, backupData, restoreRoom = false)
                                        isProcessing = false
                                        Toast.makeText(context, "Data Restored! Restarting...", Toast.LENGTH_LONG).show()
                                        delay(1500)
                                        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                                        context.startActivity(Intent.makeRestartActivityTask(intent?.component))
                                        Runtime.getRuntime().exit(0)
                                    }
                                }, onError = { err -> isProcessing = false; Toast.makeText(context, "Restore Error: $err", Toast.LENGTH_LONG).show() })
                            }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = CircleShape, border = androidx.compose.foundation.BorderStroke(1.dp, accentColor)) {
                                Icon(TablerIcons.CloudDownload, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Restore from Cloud", color = accentColor, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(onClick = {
                                isGoogleLoading = true
                                viewModel.signInWithGoogle(context as? android.app.Activity ?: context, WEB_CLIENT_ID, onSuccess = {
                                    isGoogleLoading = false
                                    Toast.makeText(context, "Welcome ${viewModel.userName.value}!", Toast.LENGTH_SHORT).show()
                                    BackupSyncState.lastSyncTime = 0L   // 60s throttle বাইপাস
                                    performSilentSync(context)          // cloud-এ ডেটা থাকলে restore + restart
                                }, onError = { error -> isGoogleLoading = false; Toast.makeText(context, "Login Failed: $error", Toast.LENGTH_LONG).show() })
                            }, modifier = Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = if (isLightMode) Color.White else Color(0xFF2C2C2C)), shape = CircleShape) {
                                if (isGoogleLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = accentColor, strokeWidth = 2.dp)
                                else {
                                    Icon(TablerIcons.BrandGoogle, contentDescription = "Google", tint = if (isLightMode) Color.Black else Color.White, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("Sign in with Google", color = if (isLightMode) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(onClick = { dialogError = null; showVerifyScreen = false; showEmailDialog = true }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = CircleShape, border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha=0.5f))) {
                                Icon(TablerIcons.Mail, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Sign in with Email", color = accentColor, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = accentColor.copy(alpha = 0.12f))
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(TablerIcons.AlertTriangle, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Cloud backup only includes online data (playlists, liked songs, subscribe artists, album etc.). For local data like local playlists, playtime & history, please use Local Backup.",
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
                Text("Local Backup (Manual)", modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Card(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable {
                    try {
                        createDocumentLauncher.launch("Lytrix_Backup_${System.currentTimeMillis()}.json")
                    } catch (e: Exception) {
                        Toast.makeText(context, "File manager not available: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.04f))) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f), CircleShape), contentAlignment = Alignment.Center) { Icon(TablerIcons.DeviceFloppy, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(24.dp)) }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Export Backup", color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text("Save to phone's storage", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 12.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Card(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable { openDocumentLauncher.launch("*/*") }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.04f))) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f), CircleShape), contentAlignment = Alignment.Center) { Icon(TablerIcons.FileImport, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(24.dp)) }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Import Backup", color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text("Restore from local file", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Text("Scheduled Auto-Backup", modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.04f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Auto-backup cadence (saves both local & online data)", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            com.musicplayer.lytrix.backup.AutoBackupInterval.entries.forEach { option ->
                                val selected = autoBackupStatus.interval == option
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (selected) accentColor else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
                                        .clickable {
                                            com.musicplayer.lytrix.backup.ScheduledBackupManager.setInterval(context, option)
                                            autoBackupStatus = com.musicplayer.lytrix.backup.ScheduledBackupManager.getStatus(context)
                                        }
                                        .padding(vertical = 9.dp, horizontal = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = option.displayName,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) Color.White else MaterialTheme.colorScheme.onBackground,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        val lastLabel = if (autoBackupStatus.lastBackupAtMs > 0L) {
                            val dt = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(autoBackupStatus.lastBackupAtMs))
                            "Last auto-backup: $dt (${autoBackupStatus.lastLocationLabel})"
                        } else {
                            val destHint = if (autoBackupStatus.treeUriString != null) "Custom SAF folder linked" else "Default: App External Backups"
                            "No scheduled run yet • $destHint"
                        }
                        Text(lastLabel, fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f))
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { runCatching { autoBackupFolderLauncher.launch(null) } },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(if (autoBackupStatus.treeUriString != null) "Change Folder" else "Choose Folder", fontSize = 12.sp)
                            }
                            Button(
                                onClick = {
                                    isProcessing = true
                                    coroutineScope.launch {
                                        runCatching { com.musicplayer.lytrix.backup.ScheduledBackupManager.runAutoBackupNow(context) }
                                            .onSuccess { dest ->
                                                autoBackupStatus = com.musicplayer.lytrix.backup.ScheduledBackupManager.getStatus(context)
                                                Toast.makeText(context, "Saved $dest", Toast.LENGTH_LONG).show()
                                            }
                                            .onFailure { Toast.makeText(context, "Backup failed: ${it.localizedMessage}", Toast.LENGTH_LONG).show() }
                                        isProcessing = false
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Backup Now", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                isProcessing = true
                                coroutineScope.launch {
                                    val found = com.musicplayer.lytrix.backup.ScheduledBackupManager.loadLatestAutoBackupJson(context)
                                    if (found == null) {
                                        isProcessing = false
                                        Toast.makeText(context, "No auto-backup file found yet", Toast.LENGTH_SHORT).show()
                                    } else {
                                        val (fileName, json) = found
                                        restoreBackupData(context, json, restoreRoom = true)
                                        isProcessing = false
                                        Toast.makeText(context, "Restored from $fileName! Restarting...", Toast.LENGTH_LONG).show()
                                        delay(1200)
                                        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                                        context.startActivity(Intent.makeRestartActivityTask(intent?.component))
                                        Runtime.getRuntime().exit(0)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(TablerIcons.History, contentDescription = null, modifier = Modifier.size(16.dp), tint = accentColor)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Restore Latest Auto-Backup", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = accentColor)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Text("Playlist Portability (CSV / M3U)", modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable { showPlaylistPortabilityDialog = true },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.04f))
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(48.dp).background(accentColor.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(TablerIcons.FileExport, contentDescription = null, tint = accentColor, modifier = Modifier.size(24.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Import / Export Playlists", color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text("Transfer playlists cleanly via .csv, .m3u, and .m3u8 files", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 12.sp)
                        }
                        Icon(TablerIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))
                    }
                }

                Spacer(modifier = Modifier.height(80.dp))
            }
            if (isProcessing) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha=0.4f)), contentAlignment = Alignment.Center) { LytrixExpressiveSpinner(color = accentColor, size = 42.dp) }
            }
        }
    }

    if (showPlaylistPortabilityDialog) {
        com.musicplayer.lytrix.ui.components.PlaylistPortabilityDialog(
            accentColor = accentColor,
            onDismiss = { showPlaylistPortabilityDialog = false }
        )
    }

    if (showImportWarningDialog) {
        AlertDialog(
            onDismissRequest = { showImportWarningDialog = false; pendingImportUri = null },
            title = { Text("Overwrite Cloud Data?", fontWeight = FontWeight.Bold) },
            text = { Text("Importing local backup while logged in will overwrite your cloud backup with this file's data (Room DB will stay local only and won't be uploaded to cloud). Your current cloud data will be replaced. Continue?") },
            confirmButton = {
                TextButton(onClick = {
                    val uri = pendingImportUri
                    showImportWarningDialog = false
                    pendingImportUri = null
                    if (uri != null) importLocalBackup(uri, uploadToCloud = true)
                }) { Text("Yes, Overwrite", color = Color.Red, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showImportWarningDialog = false; pendingImportUri = null }) { Text("Cancel") }
            }
        )
    }

    // ===== PREMIUM UI DIALOG =====
    if (showEmailDialog) {
        Dialog(onDismissRequest = { if (!isEmailLoading) { showEmailDialog = false; showVerifyScreen = false } }) {
            Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                if (showVerifyScreen) {
                    // ===== VERIFICATION SENT SCREEN - PREMIUM STYLE =====
                    Column(modifier = Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(modifier = Modifier.size(80.dp).background(accentColor.copy(alpha=0.15f), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(TablerIcons.Mail, contentDescription = null, tint = accentColor, modifier = Modifier.size(44.dp))
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Text("Check your email", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "We've sent a confirmation link to",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = verifyEmailSentTo,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.background(MaterialTheme.colorScheme.onSurface.copy(alpha=0.06f), RoundedCornerShape(8.dp)).padding(horizontal=12.dp, vertical=6.dp)
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3CD)), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                                Icon(TablerIcons.AlertTriangle, contentDescription = null, tint = Color(0xFF856404), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("Didn't get the email?", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF856404))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Please check your Spam / Promotions folder. Gmail sometimes moves our mail there.", fontSize = 12.sp, color = Color(0xFF856404), lineHeight = 16.sp)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            "After confirming, come back and Sign in again with Email.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f),
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { openEmailApp() },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                            shape = CircleShape
                        ) {
                            Icon(TablerIcons.ExternalLink, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open Gmail", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                viewModel.resendVerificationEmail(verifyEmailSentTo, passwordField,
                                    onSuccess = { msg -> dialogError = msg },
                                    onError = { err -> dialogError = err }
                                )
                            },
                            enabled = resendCooldown == 0L && !isEmailLoading,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = CircleShape,
                            border = androidx.compose.foundation.BorderStroke(1.dp, accentColor)
                        ) {
                            if (isEmailLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = accentColor, strokeWidth = 2.dp)
                            } else {
                                Text(if (resendCooldown > 0) "Resend in ${resendCooldown}s" else "Resend Email", color = accentColor, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (dialogError != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(dialogError!!, color = if (dialogError!!.contains("resent", true) || dialogError!!.contains("sent", true)) Color(0xFF2E7D32) else Color(0xFFFF5252), fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.background((if (dialogError!!.contains("resent", true)) Color(0xFF2E7D32) else Color(0xFFFF5252)).copy(alpha=0.1f), RoundedCornerShape(8.dp)).padding(10.dp).fillMaxWidth())
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(
                            onClick = {
                                showVerifyScreen = false
                                emailTab = 0
                                dialogError = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Back to Sign In", color = accentColor, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    // ===== NORMAL SIGN IN / REGISTER FORM =====
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text("Sign in with Email", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("One account = One login method", fontSize = 11.sp, color = accentColor, fontWeight = FontWeight.Bold, modifier = Modifier.background(accentColor.copy(alpha=0.12f), RoundedCornerShape(6.dp)).padding(horizontal=8.dp, vertical=2.dp))
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.onSurface.copy(alpha=0.08f), RoundedCornerShape(12.dp)).padding(4.dp)) {
                            Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (emailTab == 0) accentColor else Color.Transparent).clickable { emailTab = 0; dialogError = null }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                                Text("Sign In", color = if (emailTab == 0) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha=0.7f), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (emailTab == 1) accentColor else Color.Transparent).clickable { emailTab = 1; dialogError = null }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                                Text("Register", color = if (emailTab == 1) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha=0.7f), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (emailTab == 1) {
                            OutlinedTextField(value = nameField, onValueChange = { nameField = it; dialogError = null }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(TablerIcons.User, contentDescription = null, modifier=Modifier.size(18.dp)) })
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        OutlinedTextField(value = emailField, onValueChange = { emailField = it; dialogError = null }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), leadingIcon = { Icon(TablerIcons.Mail, contentDescription = null, modifier=Modifier.size(18.dp)) })
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(value = passwordField, onValueChange = { passwordField = it; dialogError = null }, label = { Text("Password (min 6)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = { IconButton(onClick = { passwordVisible = !passwordVisible }) { Icon(if (passwordVisible) TablerIcons.EyeOff else TablerIcons.Eye, contentDescription = null, modifier=Modifier.size(18.dp)) } }, leadingIcon = { Icon(TablerIcons.Lock, contentDescription = null, modifier=Modifier.size(18.dp)) })

                        if (dialogError != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            val isSuccess = dialogError!!.contains("sent", true) || dialogError!!.contains("resent", true)
                            Text(dialogError!!, color = if (isSuccess) Color(0xFF2E7D32) else Color(0xFFFF5252), fontSize = 12.sp, lineHeight = 14.sp, modifier = Modifier.background((if (isSuccess) Color(0xFF2E7D32) else Color(0xFFFF5252)).copy(alpha=0.12f), RoundedCornerShape(8.dp)).padding(10.dp).fillMaxWidth())
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = {
                                dialogError = null
                                if (emailTab == 0) {
                                    viewModel.signInWithEmail(emailField, passwordField,
                                        onSuccess = {
                                            showEmailDialog = false
                                            showVerifyScreen = false
                                            Toast.makeText(context, "Welcome back!", Toast.LENGTH_SHORT).show()
                                            BackupSyncState.lastSyncTime = 0L
                                            performSilentSync(context)
                                        },
                                        onError = { err ->
                                            if (err.contains("not verified", true)) {
                                                verifyEmailSentTo = emailField.trim()
                                                showVerifyScreen = true
                                                dialogError = null
                                            } else {
                                                dialogError = err
                                            }
                                        }
                                    )
                                } else {
                                    viewModel.registerWithEmail(nameField, emailField, passwordField,
                                        onSuccess = { _ ->
                                            verifyEmailSentTo = emailField.trim()
                                            showVerifyScreen = true
                                            dialogError = null
                                        },
                                        onError = { err -> dialogError = err }
                                    )
                                }
                            },
                            enabled = !isEmailLoading,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                            shape = CircleShape
                        ) {
                            if (isEmailLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            else Text(if (emailTab == 0) "Sign in with Email" else "Create Account", color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        // If not verified, show verify screen link
                        if (dialogError != null && dialogError!!.contains("not verified", true)) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Your email was in Spam? Check there.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.5f), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { if (!isEmailLoading) { showEmailDialog = false; showVerifyScreen = false } }, modifier = Modifier.fillMaxWidth()) { Text("Cancel", color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f)) }
                    }
                }
            }
        }
    }
}

suspend fun generateBackupData(context: Context, includeRoom: Boolean = true): JSONObject {
    return withContext(Dispatchers.IO) {
        val backupObj = JSONObject()
        val prefs = context.dataStore.data.first()
        val dataStoreObj = JSONObject()
        prefs.asMap().forEach { (key, value) -> dataStoreObj.put(key.name, value.toString()) }
        backupObj.put("datastore_prefs", dataStoreObj)

        val playtimePrefs = context.getSharedPreferences("detailed_playtime", Context.MODE_PRIVATE)
        val playtimeObj = JSONObject()
        playtimePrefs.all.forEach { (k, v) -> playtimeObj.put(k, v.toString()) }
        backupObj.put("detailed_playtime", playtimeObj)

        // Liked Songs - FULL RAW (fix)
        val likedSongsPrefs = context.getSharedPreferences("online_liked_songs", Context.MODE_PRIVATE)
        val likedIds = likedSongsPrefs.getStringSet("liked_ids", emptySet()) ?: emptySet()
        val likedSongsObj = JSONObject()
        likedSongsObj.put("liked_ids", JSONArray(likedIds.toList()))
        likedIds.forEach { id ->
            val raw = likedSongsPrefs.getString("song_data_$id", "") ?: ""
            if(raw.isNotEmpty()) likedSongsObj.put("song_data_$id", raw)
        }
        backupObj.put("online_liked_songs", likedSongsObj)

        // Liked Podcasts, Audiobooks, Downloaded - full raw
        val likedPodcastsPrefs = context.getSharedPreferences("online_liked_podcasts", Context.MODE_PRIVATE)
        val likedPodcastsObj = JSONObject()
        val podIds = likedPodcastsPrefs.getStringSet("liked_ids", emptySet()) ?: emptySet()
        likedPodcastsObj.put("liked_ids", JSONArray(podIds.toList()))
        podIds.forEach { id -> likedPodcastsObj.put("podcast_data_$id", likedPodcastsPrefs.getString("podcast_data_$id", "") ?: "") }
        backupObj.put("online_liked_podcasts", likedPodcastsObj)

        val likedAudiobooksPrefs = context.getSharedPreferences("online_liked_audiobooks", Context.MODE_PRIVATE)
        val likedAudiobooksObj = JSONObject()
        val abIds = likedAudiobooksPrefs.getStringSet("liked_ids", emptySet()) ?: emptySet()
        likedAudiobooksObj.put("liked_ids", JSONArray(abIds.toList()))
        abIds.forEach { id -> likedAudiobooksObj.put("audiobook_data_$id", likedAudiobooksPrefs.getString("audiobook_data_$id", "") ?: "") }
        backupObj.put("online_liked_audiobooks", likedAudiobooksObj)

        val dlMeta = context.getSharedPreferences("downloaded_meta", Context.MODE_PRIVATE)
        val dlObj = JSONObject()
        dlMeta.all.forEach { (k, v) -> dlObj.put(k, v.toString()) }
        backupObj.put("downloaded_meta", dlObj)

        val dlAbMeta = context.getSharedPreferences("downloaded_audiobooks_meta", Context.MODE_PRIVATE)
        val dlAbObj = JSONObject()
        dlAbMeta.all.forEach { (k, v) -> dlAbObj.put(k, v.toString()) }
        backupObj.put("downloaded_audiobooks_meta", dlAbObj)

        listOf("playlist_covers", "playlist_ids", "playlist_time", "playlist_source").forEach { name ->
            val sp = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            val obj = JSONObject()
            sp.all.forEach { (k, v) -> if (v is Long) obj.put(k, v) else obj.put(k, v.toString()) }
            backupObj.put(name, obj)
        }

        // Playlists - FULL RAW (fix)
        val plMetaPrefs = context.getSharedPreferences("online_playlists_meta", Context.MODE_PRIVATE)
        val plNames = plMetaPrefs.getStringSet("names", emptySet()) ?: emptySet()
        backupObj.put("online_playlists_meta", JSONObject().apply { put("names", JSONArray(plNames.toList())) })
        val plDataObj = JSONObject()
        plNames.forEach { name ->
            val sp = context.getSharedPreferences("online_pl_$name", Context.MODE_PRIVATE)
            val o = JSONObject()
            sp.all.forEach { (k, v) -> o.put(k, v.toString()) }
            plDataObj.put(name, o)
        }
        backupObj.put("online_playlists_data", plDataObj)

        // Albums, Artists
        val alMetaPrefs = context.getSharedPreferences("online_albums_meta", Context.MODE_PRIVATE)
        val alNames = alMetaPrefs.getStringSet("names", emptySet()) ?: emptySet()
        backupObj.put("online_albums_meta", JSONObject().apply { put("names", JSONArray(alNames.toList())) })
        val alDataObj = JSONObject()
        alNames.forEach { name ->
            val sp = context.getSharedPreferences("online_album_$name", Context.MODE_PRIVATE)
            val o = JSONObject()
            sp.all.forEach { (k, v) -> o.put(k, v.toString()) }
            alDataObj.put(name, o)
        }
        backupObj.put("online_albums_data", alDataObj)

        // 🔥 YT Podcasts ব্যাকআপ (New episodes ও Continue Listening বাদ দিয়ে আসল শোজ ও Episodes for later)
        val pcMetaPrefs = context.getSharedPreferences("online_podcasts_meta", Context.MODE_PRIVATE)
        val pcNames = (pcMetaPrefs.getStringSet("names", emptySet()) ?: emptySet())
            .filter { it.trim().lowercase() !in setOf("new episodes", "continue listening") }
        val pcMetaObj = JSONObject().apply { 
            put("names", JSONArray(pcNames.toList()))
            val laterEpisodes = pcMetaPrefs.getStringSet("episodes_for_later_ids", emptySet()) ?: emptySet()
            if (laterEpisodes.isNotEmpty()) put("episodes_for_later_ids", JSONArray(laterEpisodes.toList()))
        }
        backupObj.put("online_podcasts_meta", pcMetaObj)
        val pcDataObj = JSONObject()
        pcNames.forEach { name ->
            val sp = context.getSharedPreferences("online_podcast_$name", Context.MODE_PRIVATE)
            val o = JSONObject()
            sp.all.forEach { (k, v) -> o.put(k, v.toString()) }
            pcDataObj.put(name, o)
        }
        // Always include Episodes for later if present
        val epLaterSp = context.getSharedPreferences("online_podcast_Episodes for later", Context.MODE_PRIVATE)
        if (epLaterSp.all.isNotEmpty()) {
            val o = JSONObject()
            epLaterSp.all.forEach { (k, v) -> o.put(k, v.toString()) }
            pcDataObj.put("Episodes for later", o)
        }
        backupObj.put("online_podcasts_data", pcDataObj)

        val podChanPrefs = context.getSharedPreferences("subscribed_podcast_channels_prefs", Context.MODE_PRIVATE)
        val podChanObj = JSONObject()
        val podChanIds = podChanPrefs.getStringSet("subscribed_ids", emptySet()) ?: emptySet()
        podChanObj.put("subscribed_ids", JSONArray(podChanIds.toList()))
        podChanIds.forEach { id -> podChanObj.put("channel_data_$id", podChanPrefs.getString("channel_data_$id", "") ?: "") }
        backupObj.put("subscribed_podcast_channels_prefs", podChanObj)

        // 🔥 Audiobooks ব্যাকআপ (Continue Listening বাদ দিয়ে আসল অডিওবুকস ও Books for later)
        val abMetaPrefs = context.getSharedPreferences("online_audiobooks_meta", Context.MODE_PRIVATE)
        val abNames = (abMetaPrefs.getStringSet("names", emptySet()) ?: emptySet())
            .filter { it.trim().lowercase() != "continue listening" }
        val abMetaObj = JSONObject().apply { 
            put("names", JSONArray(abNames.toList()))
            val laterBooks = abMetaPrefs.getStringSet("books_for_later_ids", emptySet()) ?: emptySet()
            if (laterBooks.isNotEmpty()) put("books_for_later_ids", JSONArray(laterBooks.toList()))
        }
        backupObj.put("online_audiobooks_meta", abMetaObj)
        val abDataObj = JSONObject()
        abNames.forEach { name ->
            val sp = context.getSharedPreferences("online_ab_$name", Context.MODE_PRIVATE)
            val o = JSONObject()
            sp.all.forEach { (k, v) -> o.put(k, v.toString()) }
            abDataObj.put(name, o)
        }
        val bkLaterSp = context.getSharedPreferences("online_ab_Books for later", Context.MODE_PRIVATE)
        if (bkLaterSp.all.isNotEmpty()) {
            val o = JSONObject()
            bkLaterSp.all.forEach { (k, v) -> o.put(k, v.toString()) }
            abDataObj.put("Books for later", o)
        }
        backupObj.put("online_audiobooks_data", abDataObj)

        // 🔥 Custom Shows Playlists ব্যাকআপ
        val showPlMetaPrefs = context.getSharedPreferences("online_shows_playlists_meta", Context.MODE_PRIVATE)
        val showPlNames = showPlMetaPrefs.getStringSet("names", emptySet()) ?: emptySet()
        if (showPlNames.isNotEmpty()) {
            backupObj.put("online_shows_playlists_meta", JSONObject().apply { put("names", JSONArray(showPlNames.toList())) })
            val showPlDataObj = JSONObject()
            showPlNames.forEach { name ->
                val sp = context.getSharedPreferences("online_show_pl_$name", Context.MODE_PRIVATE)
                val o = JSONObject()
                sp.all.forEach { (k, v) -> o.put(k, v.toString()) }
                showPlDataObj.put(name, o)
            }
            backupObj.put("online_shows_playlists_data", showPlDataObj)
        }

        val artistPrefs = context.getSharedPreferences("subscribed_artists_prefs", Context.MODE_PRIVATE)
        val artistObj = JSONObject()
        val artistIds = artistPrefs.getStringSet("subscribed_ids", emptySet()) ?: emptySet()
        artistObj.put("subscribed_ids", JSONArray(artistIds.toList()))
        artistIds.forEach { id -> artistObj.put("artist_data_$id", artistPrefs.getString("artist_data_$id", "") ?: "") }
        backupObj.put("subscribed_artists_prefs", artistObj)

        val alarmRaw = context.getSharedPreferences("music_alarm_prefs", Context.MODE_PRIVATE).getString("alarms_json_v1", null)
        if (!alarmRaw.isNullOrBlank()) backupObj.put("music_alarm_prefs_json", alarmRaw)
        val sepRaw = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE).getString("artist_separators_json", null)
        if (!sepRaw.isNullOrBlank()) backupObj.put("artist_separators_json", sepRaw)

        // Room DB - শুধু Local Export এ
        if (includeRoom) {
            try {
                com.musicplayer.lytrix.db.AppDatabase.getDatabase(context)
                    .openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
            } catch (_: Exception) {}

            val dbDir = File(context.applicationInfo.dataDir, "databases")
            val dbList = dbDir.listFiles()
            if (dbList != null) {
                val dbFilesObj = JSONObject()
                dbList.forEach { f ->
                    try {
                        val n = f.name
                        if (!f.isFile) return@forEach
                        if (n.endsWith("-wal") || n.endsWith("-shm")) return@forEach
                        if (n.contains("firebase", true) || n.contains("google", true)) return@forEach
                        if (f.length() <= 0L) return@forEach
                        if (!(n.contains("app_database") || f.length() > 10000)) return@forEach
                        val baos = java.io.ByteArrayOutputStream()
                        java.util.zip.GZIPOutputStream(baos).use { gz -> f.inputStream().use { it.copyTo(gz) } }
                        dbFilesObj.put(n + ".gz", android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.NO_WRAP))
                    } catch (_: Exception) {}
                }
                if (dbFilesObj.length() > 0) backupObj.put("room_databases", dbFilesObj)
            }
        }
        backupObj
    }
}

suspend fun restoreBackupData(context: Context, backupData: JSONObject, restoreRoom: Boolean = true) {
    withContext(Dispatchers.IO) {
        try {
            com.musicplayer.lytrix.db.AppDatabase.getDatabase(context)
                .openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        } catch (_: Exception) {}
        BackupSyncState.isRestoring = true
        try {
            if (backupData.has("datastore_prefs")) {
                val obj = backupData.getJSONObject("datastore_prefs")
                context.dataStore.edit { prefs ->
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next(); val v = obj.getString(k)
                        if (k == "current_song_id" || k == "current_position" || k == "last_song_id" || k == "last_song_position") continue
                        if (k.startsWith("claimed_task_")) { prefs[booleanPreferencesKey(k)] = v.toBoolean(); continue }
                        when(k) {
                            "subscription_expiry","ad_free_expiry","daily_task_audio_ms","daily_task_video_ms","audio_ms","video_ms","weekly_ms" -> prefs[longPreferencesKey(k)] = v.toLongOrNull() ?: 0L
                            "daily_task_song_download_count","daily_task_podcast_download_count","daily_task_ad_count","repeat_mode" -> prefs[intPreferencesKey(k)] = v.toIntOrNull() ?: 0
                            "playback_speed","crossfade_duration" -> prefs[floatPreferencesKey(k)] = v.toFloatOrNull() ?: 1f
                            "keep_screen_on","lock_screen_playing","is_premium","is_premium_plus","is_code_applied","has_applied_code","shuffle_mode" -> prefs[booleanPreferencesKey(k)] = v.toBoolean()
                            "last_ad_date","task_tracked_date","app_theme_mode","accent_color","language_code","sort_option" -> prefs[stringPreferencesKey(k)] = v
                        }
                    }
                }
            }

            if (backupData.has("playlist_covers")) { val o = backupData.getJSONObject("playlist_covers"); val ed = context.getSharedPreferences("playlist_covers", Context.MODE_PRIVATE).edit(); ed.clear(); o.keys().forEach { k -> ed.putString(k, o.optString(k)) }; ed.apply() }
            if (backupData.has("playlist_ids")) { val o = backupData.getJSONObject("playlist_ids"); val ed = context.getSharedPreferences("playlist_ids", Context.MODE_PRIVATE).edit(); ed.clear(); o.keys().forEach { k -> ed.putString(k, o.optString(k)) }; ed.apply() }
            if (backupData.has("playlist_time")) { val o = backupData.getJSONObject("playlist_time"); val ed = context.getSharedPreferences("playlist_time", Context.MODE_PRIVATE).edit(); ed.clear(); o.keys().forEach { k -> ed.putLong(k, o.optLong(k)) }; ed.apply() }
            if (backupData.has("playlist_source")) { val o = backupData.getJSONObject("playlist_source"); val ed = context.getSharedPreferences("playlist_source", Context.MODE_PRIVATE).edit(); ed.clear(); o.keys().forEach { k -> ed.putString(k, o.optString(k)) }; ed.apply() }

            if (backupData.has("detailed_playtime")) { val o = backupData.getJSONObject("detailed_playtime"); val ed = context.getSharedPreferences("detailed_playtime", Context.MODE_PRIVATE).edit(); o.keys().forEach { k -> try{ ed.putLong(k, o.getString(k).toLong()) }catch(_:Exception){} }; ed.apply() }

            if (backupData.has("online_liked_songs")) {
                val o = backupData.getJSONObject("online_liked_songs")
                val prefs = context.getSharedPreferences("online_liked_songs", Context.MODE_PRIVATE)
                val ed = prefs.edit()
                if (o.has("liked_ids")) {
                    val arr = o.getJSONArray("liked_ids"); val set = mutableSetOf<String>()
                    for (i in 0 until arr.length()) set.add(arr.getString(i))
                    set.forEach { id -> if (o.has("song_data_$id")) ed.putString("song_data_$id", o.getString("song_data_$id")) }
                    ed.putStringSet("liked_ids", set)
                }
                ed.apply()
            }
            if (backupData.has("online_liked_podcasts")) { val o = backupData.getJSONObject("online_liked_podcasts"); val ed = context.getSharedPreferences("online_liked_podcasts", Context.MODE_PRIVATE).edit(); ed.clear(); if (o.has("liked_ids")) { val arr = o.getJSONArray("liked_ids"); val set = mutableSetOf<String>(); for (i in 0 until arr.length()) set.add(arr.getString(i)); ed.putStringSet("liked_ids", set); set.forEach { id -> if (o.has("podcast_data_$id")) ed.putString("podcast_data_$id", o.getString("podcast_data_$id")) } }; ed.apply() }
            if (backupData.has("online_liked_audiobooks")) { val o = backupData.getJSONObject("online_liked_audiobooks"); val ed = context.getSharedPreferences("online_liked_audiobooks", Context.MODE_PRIVATE).edit(); ed.clear(); if (o.has("liked_ids")) { val arr = o.getJSONArray("liked_ids"); val set = mutableSetOf<String>(); for (i in 0 until arr.length()) set.add(arr.getString(i)); ed.putStringSet("liked_ids", set); set.forEach { id -> if (o.has("audiobook_data_$id")) ed.putString("audiobook_data_$id", o.getString("audiobook_data_$id")) } }; ed.apply() }

            if (backupData.has("downloaded_meta")) { val o = backupData.getJSONObject("downloaded_meta"); val ed = context.getSharedPreferences("downloaded_meta", Context.MODE_PRIVATE).edit(); ed.clear(); o.keys().forEach { k -> ed.putString(k, o.getString(k)) }; ed.apply() }
            if (backupData.has("downloaded_audiobooks_meta")) { val o = backupData.getJSONObject("downloaded_audiobooks_meta"); val ed = context.getSharedPreferences("downloaded_audiobooks_meta", Context.MODE_PRIVATE).edit(); ed.clear(); o.keys().forEach { k -> ed.putString(k, o.getString(k)) }; ed.apply() }

            if (backupData.has("online_playlists_meta")) {
                val meta = backupData.getJSONObject("online_playlists_meta")
                if (meta.has("names")) {
                    val arr = meta.getJSONArray("names"); val set = mutableSetOf<String>(); for (i in 0 until arr.length()) set.add(arr.getString(i))
                    context.getSharedPreferences("online_playlists_meta", Context.MODE_PRIVATE).edit().putStringSet("names", set).apply()
                    if (backupData.has("online_playlists_data")) {
                        val dataObj = backupData.getJSONObject("online_playlists_data")
                        set.forEach { name -> if (dataObj.has(name)) { val plObj = dataObj.getJSONObject(name); val ed = context.getSharedPreferences("online_pl_$name", Context.MODE_PRIVATE).edit(); ed.clear(); plObj.keys().forEach { kk -> ed.putString(kk, plObj.getString(kk)) }; ed.apply() } }
                    }
                }
            }
            if (backupData.has("online_albums_meta")) {
                val meta = backupData.getJSONObject("online_albums_meta")
                if (meta.has("names")) {
                    val arr = meta.getJSONArray("names"); val set = mutableSetOf<String>(); for (i in 0 until arr.length()) set.add(arr.getString(i))
                    context.getSharedPreferences("online_albums_meta", Context.MODE_PRIVATE).edit().putStringSet("names", set).apply()
                    if (backupData.has("online_albums_data")) {
                        val dataObj = backupData.getJSONObject("online_albums_data")
                        set.forEach { name -> if (dataObj.has(name)) { val alObj = dataObj.getJSONObject(name); val ed = context.getSharedPreferences("online_album_$name", Context.MODE_PRIVATE).edit(); ed.clear(); alObj.keys().forEach { kk -> ed.putString(kk, alObj.getString(kk)) }; ed.apply() } }
                    }
                }
            }

            // 🔥 Custom Shows Playlists রিস্টোর
            if (backupData.has("online_shows_playlists_meta")) {
                val meta = backupData.getJSONObject("online_shows_playlists_meta")
                val edMeta = context.getSharedPreferences("online_shows_playlists_meta", Context.MODE_PRIVATE).edit()
                if (meta.has("names")) {
                    val arr = meta.getJSONArray("names"); val set = mutableSetOf<String>(); for (i in 0 until arr.length()) set.add(arr.getString(i))
                    edMeta.putStringSet("names", set)
                    if (backupData.has("online_shows_playlists_data")) {
                        val dataObj = backupData.getJSONObject("online_shows_playlists_data")
                        set.forEach { name ->
                            if (dataObj.has(name)) {
                                val plObj = dataObj.getJSONObject(name)
                                val ed = context.getSharedPreferences("online_show_pl_$name", Context.MODE_PRIVATE).edit()
                                ed.clear()
                                plObj.keys().forEach { kk -> ed.putString(kk, plObj.getString(kk)) }
                                ed.apply()
                            }
                        }
                    }
                }
                edMeta.apply()
            }
            // 🔥 YT Podcasts রিস্টোর
            if (backupData.has("online_podcasts_meta")) {
                val meta = backupData.getJSONObject("online_podcasts_meta")
                val edMeta = context.getSharedPreferences("online_podcasts_meta", Context.MODE_PRIVATE).edit()
                if (meta.has("names")) {
                    val arr = meta.getJSONArray("names"); val set = mutableSetOf<String>(); for (i in 0 until arr.length()) set.add(arr.getString(i))
                    edMeta.putStringSet("names", set)
                    if (backupData.has("online_podcasts_data")) {
                        val dataObj = backupData.getJSONObject("online_podcasts_data")
                        set.forEach { name -> if (dataObj.has(name)) { val pcObj = dataObj.getJSONObject(name); val ed = context.getSharedPreferences("online_podcast_$name", Context.MODE_PRIVATE).edit(); ed.clear(); pcObj.keys().forEach { kk -> ed.putString(kk, pcObj.getString(kk)) }; ed.apply() } }
                        if (dataObj.has("Episodes for later")) { val pcObj = dataObj.getJSONObject("Episodes for later"); val ed = context.getSharedPreferences("online_podcast_Episodes for later", Context.MODE_PRIVATE).edit(); ed.clear(); pcObj.keys().forEach { kk -> ed.putString(kk, pcObj.getString(kk)) }; ed.apply() }
                    }
                }
                if (meta.has("episodes_for_later_ids")) {
                    val arr = meta.getJSONArray("episodes_for_later_ids"); val set = mutableSetOf<String>(); for (i in 0 until arr.length()) set.add(arr.getString(i))
                    edMeta.putStringSet("episodes_for_later_ids", set)
                }
                edMeta.apply()
            }
            if (backupData.has("subscribed_podcast_channels_prefs")) {
                val o = backupData.getJSONObject("subscribed_podcast_channels_prefs"); val ed = context.getSharedPreferences("subscribed_podcast_channels_prefs", Context.MODE_PRIVATE).edit()
                if (o.has("subscribed_ids")) { val arr = o.getJSONArray("subscribed_ids"); val set = mutableSetOf<String>(); for (i in 0 until arr.length()) set.add(arr.getString(i)); ed.putStringSet("subscribed_ids", set); set.forEach { id -> if (o.has("channel_data_$id")) ed.putString("channel_data_$id", o.getString("channel_data_$id")) } }; ed.apply()
            }

            // 🔥 Audiobooks রিস্টোর
            if (backupData.has("online_audiobooks_meta")) {
                val meta = backupData.getJSONObject("online_audiobooks_meta")
                val edMeta = context.getSharedPreferences("online_audiobooks_meta", Context.MODE_PRIVATE).edit()
                if (meta.has("names")) {
                    val arr = meta.getJSONArray("names"); val set = mutableSetOf<String>(); for (i in 0 until arr.length()) set.add(arr.getString(i))
                    edMeta.putStringSet("names", set)
                    if (backupData.has("online_audiobooks_data")) {
                        val dataObj = backupData.getJSONObject("online_audiobooks_data")
                        set.forEach { name -> if (dataObj.has(name)) { val abObj = dataObj.getJSONObject(name); val ed = context.getSharedPreferences("online_ab_$name", Context.MODE_PRIVATE).edit(); ed.clear(); abObj.keys().forEach { kk -> ed.putString(kk, abObj.getString(kk)) }; ed.apply() } }
                        if (dataObj.has("Books for later")) { val abObj = dataObj.getJSONObject("Books for later"); val ed = context.getSharedPreferences("online_ab_Books for later", Context.MODE_PRIVATE).edit(); ed.clear(); abObj.keys().forEach { kk -> ed.putString(kk, abObj.getString(kk)) }; ed.apply() }
                    }
                }
                if (meta.has("books_for_later_ids")) {
                    val arr = meta.getJSONArray("books_for_later_ids"); val set = mutableSetOf<String>(); for (i in 0 until arr.length()) set.add(arr.getString(i))
                    edMeta.putStringSet("books_for_later_ids", set)
                }
                edMeta.apply()
            }
            if (backupData.has("subscribed_audiobook_authors_prefs")) {
                val o = backupData.getJSONObject("subscribed_audiobook_authors_prefs"); val ed = context.getSharedPreferences("subscribed_audiobook_authors_prefs", Context.MODE_PRIVATE).edit()
                if (o.has("subscribed_ids")) { val arr = o.getJSONArray("subscribed_ids"); val set = mutableSetOf<String>(); for (i in 0 until arr.length()) set.add(arr.getString(i)); ed.putStringSet("subscribed_ids", set); set.forEach { id -> if (o.has("author_data_$id")) ed.putString("author_data_$id", o.getString("author_data_$id")) } }; ed.apply()
            }
            if (backupData.has("downloaded_audiobooks_meta")) {
                val o = backupData.getJSONObject("downloaded_audiobooks_meta"); val ed = context.getSharedPreferences("downloaded_audiobooks_meta", Context.MODE_PRIVATE).edit(); ed.clear(); o.keys().forEach { k -> ed.putString(k, o.getString(k)) }; ed.apply()
            }
            if (backupData.has("music_alarm_prefs_json")) {
                context.getSharedPreferences("music_alarm_prefs", Context.MODE_PRIVATE).edit().putString("alarms_json_v1", backupData.getString("music_alarm_prefs_json")).apply()
            }
            if (backupData.has("artist_separators_json")) {
                context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE).edit().putString("artist_separators_json", backupData.getString("artist_separators_json")).apply()
            }
            if (backupData.has("subscribed_artists_prefs")) {
                val o = backupData.getJSONObject("subscribed_artists_prefs"); val ed = context.getSharedPreferences("subscribed_artists_prefs", Context.MODE_PRIVATE).edit()
                if (o.has("subscribed_ids")) { val arr = o.getJSONArray("subscribed_ids"); val set = mutableSetOf<String>(); for (i in 0 until arr.length()) set.add(arr.getString(i)); ed.putStringSet("subscribed_ids", set); set.forEach { id -> if (o.has("artist_data_$id")) ed.putString("artist_data_$id", o.getString("artist_data_$id")) } }; ed.apply()
            }
            if (restoreRoom && backupData.has("room_databases")) {
                try { com.musicplayer.lytrix.db.AppDatabase.getDatabase(context).close() } catch (_: Exception) {}
                val dbFilesObj = backupData.getJSONObject("room_databases")
                val dbDir = File(context.applicationInfo.dataDir, "databases"); if (!dbDir.exists()) dbDir.mkdirs()
                dbFilesObj.keys().forEach { fileName ->
                    try {
                        val isGz = fileName.endsWith(".gz")
                        val realName = if (isGz) fileName.removeSuffix(".gz") else fileName
                        File(dbDir, "$realName-wal").delete()
                        File(dbDir, "$realName-shm").delete()
                        val raw = android.util.Base64.decode(dbFilesObj.getString(fileName), android.util.Base64.DEFAULT)
                        val bytes = if (isGz) {
                            java.util.zip.GZIPInputStream(java.io.ByteArrayInputStream(raw)).use { it.readBytes() }
                        } else raw
                        File(dbDir, realName).writeBytes(bytes)
                    } catch (_: Exception) {}
                }
            }
        } finally {
            BackupSyncState.isRestoring = false
        }
    }
}
private fun Color.blendWith(other: Color, ratio: Float): Color {
    val r = this.red * (1 - ratio) + other.red * ratio
    val g = this.green * (1 - ratio) + other.green * ratio
    val b = this.blue * (1 - ratio) + other.blue * ratio
    return Color(r, g, b, this.alpha)
}

@Composable
fun AppThemePreview(
    appThemeMode: String,
    accentColor: Color,
    content: @Composable () -> Unit
) {
    val darkTheme = when (appThemeMode) {
        "Dark" -> true
        "Light" -> false
        else -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        darkTheme -> darkColorScheme(
            primary = accentColor,
            secondary = accentColor,
            background = Color(0xFF101010).blendWith(accentColor, 0.05f),
            surface = Color(0xFF1E1E1E).blendWith(accentColor, 0.10f),
            surfaceVariant = Color(0xFF2C2C2C).blendWith(accentColor, 0.15f),
            surfaceContainer = Color(0xFF1E1E1E).blendWith(accentColor, 0.10f),
            onBackground = Color.White,
            onSurface = Color.White,
            onSurfaceVariant = Color(0xFFAAAAAA)
        )
        else -> lightColorScheme(
            primary = accentColor,
            secondary = accentColor,
            background = Color(0xFFFDFDFD).blendWith(accentColor, 0.05f),
            surface = Color(0xFFFFFFFF).blendWith(accentColor, 0.10f),
            surfaceVariant = Color(0xFFF0F0F0).blendWith(accentColor, 0.15f),
            surfaceContainer = Color(0xFFFFFFFF).blendWith(accentColor, 0.10f),
            onBackground = Color(0xFF1A1A1A),
            onSurface = Color(0xFF1A1A1A),
            onSurfaceVariant = Color(0xFF666666)
        )
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ThemeScreen(
    viewModel: SettingsViewModel,
    playerViewModel: PlayerViewModel,
    onNavigateBack: () -> Unit
) {
    val appThemeMode by viewModel.appThemeMode.collectAsStateWithLifecycle()
    val accentColorHex by viewModel.accentColor.collectAsStateWithLifecycle()
    val gridViewSize by viewModel.gridViewSize.collectAsStateWithLifecycle()
    val currentPremiumTheme by playerViewModel.playerTheme.collectAsStateWithLifecycle()

    val accentColor = try { Color(android.graphics.Color.parseColor(accentColorHex.replace("0xFF", "#"))) } catch (e: Exception) { Color(0xFF3182CE) }
    val context = LocalContext.current

    val premiumThemes = listOf(
        "Solid Default" to Color.Gray,
        "Cyber City" to Color(0xFF9D4EDD),
        "Campside Rain" to Color(0xFF10B981),
        "Cat Rain" to Color(0xFF3B82F6),
        "Cartoon Walk" to Color(0xFFF59E0B),
        "Summer Home" to Color(0xFFEC4899),
        "Summer Village" to Color(0xFFFF4500),
        "Snowy Home" to Color(0xFF00BFFF)
    )
    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            try {
                val flag = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flag)
            } catch (e: Exception) {}
            viewModel.setCustomBackgroundUri(uri.toString())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(getString("theme"), color = MaterialTheme.colorScheme.onBackground) },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(TablerIcons.ArrowLeft, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground) } },
                actions = {
                    Row(modifier = Modifier.padding(end = 16.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f))) {
                        IconButton(onClick = { viewModel.setGridViewSize(1) }) { Icon(painterResource(R.drawable.ic_list_view), contentDescription = "List View", tint = if(gridViewSize == 1) accentColor else Color.Gray) }
                        IconButton(onClick = { viewModel.setGridViewSize(2) }) { Icon(painterResource(R.drawable.ic_grid_view), contentDescription = "2-Column Grid", tint = if(gridViewSize == 2) accentColor else Color.Gray) }
                        IconButton(onClick = { viewModel.setGridViewSize(3) }) { Icon(painterResource(R.drawable.ic_grid_3x3), contentDescription = "3-Column Grid", tint = if(gridViewSize == 3) accentColor else Color.Gray) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            com.musicplayer.lytrix.ui.component.AmbientThemeGlow(
                tint = accentColor,
                intensity = 0.22f
            )
            Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { 4 })

            Box(modifier = Modifier.height(380.dp)) {
                AppThemePreview(appThemeMode = appThemeMode, accentColor = accentColor) {
                    val layoutMode = when (gridViewSize) { 1 -> LayoutMode.LIST; 2 -> LayoutMode.GRID_2; else -> LayoutMode.GRID_3 }
                    androidx.compose.foundation.pager.HorizontalPager(
                        state = pagerState, modifier = Modifier.fillMaxWidth().fillMaxHeight().padding(vertical = 16.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp), pageSpacing = 16.dp
                    ) { page ->
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            ElevatedCard(
                                modifier = Modifier.fillMaxHeight().aspectRatio(9f / 19f, matchHeightConstraintsFirst = true),
                                shape = RoundedCornerShape(24.dp), elevation = CardDefaults.elevatedCardElevation(defaultElevation = 12.dp),
                                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.background)
                            ) {
                                BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    val scale = minOf(maxWidth / 360.dp, maxHeight / 760.dp)
                                    Box(modifier = Modifier.requiredSize(360.dp, 760.dp).graphicsLayer(scaleX = scale, scaleY = scale, transformOrigin = TransformOrigin(0.5f, 0.5f))) {
                                        MockAppLayout(accentColor = accentColor, currentTab = page) {
                                            when (page) { 0 -> MockSongsTab(layoutMode, accentColor); 1 -> MockPlaylistsTab(layoutMode, accentColor); 2 -> MockArtistsTab(layoutMode, accentColor); 3 -> MockAlbumsTab(layoutMode, accentColor) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "Premium Themes", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
                        Box(modifier = Modifier.background(accentColor.copy(alpha = 0.2f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                            Text("LIVE", color = accentColor, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 24.dp)) {
                        items(premiumThemes) { (themeName, dotColor) ->
                            val isSelected = currentPremiumTheme == themeName
                            val isFreeTheme = themeName == "Solid Default"
                            var isPremiumUser by remember { mutableStateOf(false) }

                            LaunchedEffect(Unit) {
                                isPremiumUser = com.musicplayer.lytrix.ui.AdManager.hasPremiumFeatures(context)
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        if (isFreeTheme || isPremiumUser) {
                                            playerViewModel.setPlayerTheme(themeName)
                                            AnalyticsManager.logThemeChanged(themeName)
                                        } else {
                                            Toast.makeText(context, "Please activate Premium to unlock this theme!", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    .background(if (isSelected) accentColor.copy(alpha = 0.15f) else Color.Transparent)
                                    .padding(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier.size(48.dp).clip(CircleShape).background(if (isSelected) accentColor else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(dotColor))
                                    if (!isFreeTheme && !isPremiumUser) {
                                        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha=0.4f)))
                                        Icon(TablerIcons.Lock, contentDescription = "Locked", tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(20.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = themeName.replace(" ", "\n"), fontSize = 10.sp,
                                    color = if (isSelected) accentColor else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    textAlign = TextAlign.Center, lineHeight = 12.sp
                                )
                            }
                        }
                    }

                    Text(getString("app_theme_mode"), color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(bottom = 8.dp), fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(Color.Black.copy(alpha = 0.5f)), horizontalArrangement = Arrangement.SpaceEvenly) {
                        listOf("Dark" to getString("dark"), "Light" to getString("light"), "System" to getString("system")).forEach { (modeStr, displayName) ->
                            val isSelected = appThemeMode == modeStr
                            Box(
                                modifier = Modifier.weight(1f).clickable { viewModel.setAppThemeMode(modeStr) }.background(if (isSelected) accentColor else Color.Transparent).padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) { Text(text = displayName, color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurface) }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(getString("accent_color"), color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(bottom = 12.dp), fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                        item {
                            Box(
                                modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)).clickable { pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                contentAlignment = Alignment.Center
                            ) { Icon(TablerIcons.Photo, contentDescription = "Gallery", tint = MaterialTheme.colorScheme.onBackground) }
                        }
                        val colors = listOf("0xFF3182CE", "0xFFE53E3E", "0xFFD69E2E", "0xFF38A169", "0xFF4FD1C5", "0xFF805AD5", "0xFFD53F8C")
                        items(colors) { hex ->
                            val parsedColor = try { Color(android.graphics.Color.parseColor(hex.replace("0xFF", "#"))) } catch(e:Exception) { MaterialTheme.colorScheme.onBackground }
                            Box(
                                modifier = Modifier.size(48.dp).clip(CircleShape).background(parsedColor).clickable { viewModel.setAccentColor(hex); viewModel.setCustomBackgroundUri("") },
                                contentAlignment = Alignment.Center
                            ) { if (accentColorHex == hex) Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onBackground)) }
                        }
                    }
                }
            }
        }
    }
}
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PlaytimeScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val weeklyPlaytimeMs by viewModel.weeklyPlaytimeMs.collectAsStateWithLifecycle()
    val accentColor = com.musicplayer.lytrix.ui.theme.LocalAccentColor.current
    val audioPlaytimeMs by viewModel.audioPlaytimeMs.collectAsStateWithLifecycle()
    val videoPlaytimeMs by viewModel.videoPlaytimeMs.collectAsStateWithLifecycle()
    val totalPlaytimeMs = audioPlaytimeMs + videoPlaytimeMs

    var insightsData by remember { mutableStateOf<com.musicplayer.lytrix.stats.ListeningInsightsData?>(null) }
    var showWrappedDialog by remember { mutableStateOf(false) }
    var shareSheetTab by remember { mutableStateOf<com.musicplayer.lytrix.ui.stats.ShareStatsTab?>(null) }
    var showTimeTransferDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        insightsData = com.musicplayer.lytrix.stats.ListeningInsightsRepository.load(
            context,
            com.musicplayer.lytrix.stats.StatsPeriod.ALL_TIME
        )
    }

    val formatTime = { ms: Long ->
        val hours = (ms / (1000 * 60 * 60))
        val minutes = (ms / (1000 * 60)) % 60
        if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Playtime Analytics", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(TablerIcons.ArrowLeft, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState())
        ) {

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .clickable { showWrappedDialog = true },
                elevation = CardDefaults.elevatedCardElevation(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(Brush.linearGradient(listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0))))
                        .padding(24.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(TablerIcons.Star, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("YOUR MUSIC JOURNEY", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Lytrix Wrapped Memories", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                        Text("Explore your top tracks, most replayed artists, and unique stats.", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.2f), CircleShape)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Reveal Insights", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(TablerIcons.ChevronRight, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Icon(TablerIcons.Clock, contentDescription = null, tint = accentColor, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Total playtime", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 13.sp)
                    Text(formatTime(totalPlaytimeMs), color = MaterialTheme.colorScheme.onBackground, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Icon(TablerIcons.Headphones, contentDescription = null, tint = accentColor, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Music playtime", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 13.sp)
                            Text(formatTime(audioPlaytimeMs), color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Icon(TablerIcons.PlayerPlay, contentDescription = null, tint = accentColor, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Video playtime", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 13.sp)
                            Text(formatTime(videoPlaytimeMs), color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("This week", color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        val weekDisplay = formatTime(maxOf(weeklyPlaytimeMs, insightsData?.weekPlaytimeMs ?: 0L))
                        Text(weekDisplay, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 14.sp)
                    }
                    Icon(TablerIcons.Calendar, contentDescription = null, tint = accentColor.copy(alpha = 0.8f))
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .clickable { shareSheetTab = com.musicplayer.lytrix.ui.stats.ShareStatsTab.RECEIPT },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(TablerIcons.Share, contentDescription = null, tint = accentColor, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Receiptify & Taste Card", color = MaterialTheme.colorScheme.onBackground, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("Print Receiptify slip or export shareable Taste Poster", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f), fontSize = 12.sp)
                    }
                    Icon(TablerIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .clickable { showTimeTransferDialog = true },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f))
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(42.dp).clip(CircleShape).background(accentColor.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                        Icon(TablerIcons.ArrowsRightLeft, contentDescription = null, tint = accentColor, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Time & Stats Transfer", color = MaterialTheme.colorScheme.onBackground, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("Merge play counts & listening history from one song to another", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f), fontSize = 12.sp)
                    }
                    Icon(TablerIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Home Screen Widgets", color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Pin Material 3 Lytrix widgets directly to your home screen", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    val widgetChoices = listOf(
                        "Turntable" to com.musicplayer.lytrix.widget.TurntableWidgetReceiver::class.java,
                        "Capsule" to com.musicplayer.lytrix.widget.CapsuleDeckWidgetReceiver::class.java,
                        "Insights" to com.musicplayer.lytrix.widget.InsightsWidgetReceiver::class.java,
                        "Playlists" to com.musicplayer.lytrix.widget.PlaylistsWidgetReceiver::class.java
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        widgetChoices.forEach { (label, receiverCls) ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(accentColor.copy(alpha = 0.16f))
                                    .clickable {
                                        val awm = android.appwidget.AppWidgetManager.getInstance(context)
                                        val provider = android.content.ComponentName(context, receiverCls)
                                        if (awm.isRequestPinAppWidgetSupported) {
                                            awm.requestPinAppWidget(provider, null, null)
                                        } else {
                                            Toast.makeText(context, "Add from launcher widget picker", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(label, color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showWrappedDialog) {
        com.musicplayer.lytrix.ui.stats.WrappedStoryDialog(
            onDismiss = { showWrappedDialog = false },
            onOpenReceipt = { shareSheetTab = com.musicplayer.lytrix.ui.stats.ShareStatsTab.RECEIPT }
        )
    }

    if (showTimeTransferDialog) {
        com.musicplayer.lytrix.ui.stats.TimeTransferDialog(
            accentColor = accentColor,
            onDismiss = { showTimeTransferDialog = false },
            onTransferComplete = {
                showTimeTransferDialog = false
                scope.launch {
                    insightsData = com.musicplayer.lytrix.stats.ListeningInsightsRepository.load(
                        context,
                        com.musicplayer.lytrix.stats.StatsPeriod.ALL_TIME
                    )
                }
            }
        )
    }

    shareSheetTab?.let { tab ->
        com.musicplayer.lytrix.ui.stats.ShareReceiptAndTasteDialog(
            initialTab = tab,
            accentColor = accentColor,
            onDismiss = { shareSheetTab = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onNavigateBack: () -> Unit
) {
    var showTermsDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(getString("about_lytrix_player"), color = MaterialTheme.colorScheme.onBackground) },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(TablerIcons.ArrowLeft, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Developer: Lytrix Team", color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Version: v1.0.2 (Stable)", color = Color.Gray, fontSize = 14.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth().clickable { showTermsDialog = true }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Terms and Conditions", color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Icon(TablerIcons.ChevronRight, contentDescription = null, tint = Color.Gray)
                }
            }
        }
    }

    if (showTermsDialog) {
        AlertDialog(
            onDismissRequest = { showTermsDialog = false },
            title = { Text("Terms and Conditions", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "1. Acceptance of Terms\nBy accessing and using Lytrix Player, you accept and agree to be bound by the terms and provision of this agreement.\n\n" +
                                "2. Privacy Policy\nYour privacy is critically important. We do not collect or share any of your personal local data without consent. All backups are stored locally on your device.\n\n" +
                                "3. Use of Content\nLytrix Player provides access to third-party online content via APIs. We do not own or host the online media streamed through the app. Please respect the copyright of the content owners.\n\n" +
                                "4. Disclaimer\nThe application is provided on an 'as is' basis without any warranties.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showTermsDialog = false }) { Text("I Agree") } }
        )
    }
}

@Composable
fun MockAlbumArt(size: androidx.compose.ui.unit.Dp = 50.dp, icon: ImageVector = TablerIcons.PlayerPlay, cornerRadius: androidx.compose.ui.unit.Dp = 12.dp) {
    Box(
        modifier = Modifier.size(size).clip(RoundedCornerShape(cornerRadius)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.5f)) }
}

@Composable
fun MockSongRow(title: String, artist: String, showLyrics: Boolean = false, isSelected: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        MockAlbumArt(56.dp, cornerRadius = 12.dp)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showLyrics) {
                    Box(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp)) {
                        Text("LYRICS", color = MaterialTheme.colorScheme.primary, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(artist, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, maxLines = 1)
            }
        }
        Icon(TablerIcons.DotsVertical, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun MockArtistRow(title: String, subtitle1: String, subtitle2: String?, tracks: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        MockAlbumArt(72.dp, icon = TablerIcons.User, cornerRadius = 16.dp)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(TablerIcons.Music, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(subtitle1, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, maxLines = 1)
            }
            if (subtitle2 != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(TablerIcons.Music, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(subtitle2, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, maxLines = 1)
                }
            }
        }
        Text(tracks, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
}

@Composable
fun MockAlbumRow(title: String, subtitle: String, tracks: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        MockAlbumArt(72.dp, icon = TablerIcons.Disc, cornerRadius = 16.dp)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(TablerIcons.Music, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
        }
        Text(tracks, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
}

@Composable
fun MockGridCard(title: String, subtitle: String, extra: String? = null, icon: ImageVector = TablerIcons.Disc) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().aspectRatio(0.9f),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Box(
                    modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.5f)) }
                if (extra != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(extra, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (title != "Playlists") {
                    Icon(TablerIcons.Music, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(10.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

@Composable
fun MockAppLayout(accentColor: Color, currentTab: Int, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp)) {
            Text("LIBRARY", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = accentColor, letterSpacing = 1.sp)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Lytrix Player", fontWeight = FontWeight.Medium, fontSize = 24.sp, color = MaterialTheme.colorScheme.onBackground)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(TablerIcons.Dots, contentDescription = null, tint = accentColor, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Icon(TablerIcons.Settings, contentDescription = null, tint = accentColor, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f).height(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.CenterStart) {
                    Row(modifier = Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(TablerIcons.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Search...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Icon(TablerIcons.ArrowsSort, contentDescription = null, tint = accentColor, modifier = Modifier.size(24.dp))
            }
        }

        val tabs = listOf(getString("songs"), getString("playlists"), getString("artists"), getString("albums"))
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            tabs.forEachIndexed { index, title ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text(
                        text = title, fontWeight = if (currentTab == index) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 14.sp, color = if (currentTab == index) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    if (currentTab == index) Box(modifier = Modifier.fillMaxWidth(0.6f).height(2.dp).background(accentColor))
                    else Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(Color.Transparent))
                }
            }
        }
        Box(modifier = Modifier.weight(1f)) { content() }
        Box(modifier = Modifier.padding(bottom = 16.dp, start = 16.dp, end = 16.dp)) {
            Card(modifier = Modifier.fillMaxWidth(), shape = CircleShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    MockAlbumArt(40.dp, cornerRadius = 20.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("JONY - Lali ...", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                        Text("MUSIC KING", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1)
                    }
                    Icon(TablerIcons.PlayerSkipBack, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(TablerIcons.PlayerPlay, contentDescription = null, tint = accentColor, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(TablerIcons.PlayerSkipForward, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}

@Composable
fun MockSongsTab(layoutMode: LayoutMode, accentColor: Color) {
    when (layoutMode) {
        LayoutMode.LIST -> {
            androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 16.dp), userScrollEnabled = false) {
                items(1) { MockSongRow("Midnight City", "Generic Band", showLyrics = true) }
                items(1) { MockSongRow("Neon Dreams", "The Melodies", showLyrics = false) }
                items(1) { MockSongRow("Ocean Waves", "Chill Sounds", showLyrics = false) }
                items(1) { MockSongRow("Lo-Fi Chill", "Unknown Artist", showLyrics = false) }
                items(1) { MockSongRow("Acoustic Sunset", "The Melodies", showLyrics = true) }
            }
        }
        LayoutMode.GRID_2 -> {
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2), modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), userScrollEnabled = false
            ) {
                items(1) { MockGridCard(title = "Midnight City", subtitle = "Generic Band", icon = TablerIcons.Music) }
                items(1) { MockGridCard(title = "Neon Dreams", subtitle = "The Melodies", icon = TablerIcons.Music) }
                items(1) { MockGridCard(title = "Ocean Waves", subtitle = "Chill Sounds", icon = TablerIcons.Music) }
                items(1) { MockGridCard(title = "Lo-Fi Chill", subtitle = "Unknown Artist", icon = TablerIcons.Music) }
            }
        }
        LayoutMode.GRID_3 -> {
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3), modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), userScrollEnabled = false
            ) {
                items(1) { MockGridCard(title = "Midnight City", subtitle = "Generic Band", icon = TablerIcons.Music) }
                items(1) { MockGridCard(title = "Neon Dreams", subtitle = "The Melodies", icon = TablerIcons.Music) }
                items(1) { MockGridCard(title = "Ocean Waves", subtitle = "Chill Sounds", icon = TablerIcons.Music) }
                items(1) { MockGridCard(title = "Lo-Fi Chill", subtitle = "Unknown Artist", icon = TablerIcons.Music) }
                items(1) { MockGridCard(title = "Acoustic Sunset", subtitle = "The Melodies", icon = TablerIcons.Music) }
                items(1) { MockGridCard(title = "Electric Night", subtitle = "Generic Band", icon = TablerIcons.Music) }
            }
        }
    }
}

@Composable
fun MockPlaylistListCard(title: String, tracks: String, icon: ImageVector, color1: Color, color2: Color) {
    Box(
        modifier = Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(16.dp)).background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(color1, color2))).padding(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column { Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp); Spacer(modifier = Modifier.height(4.dp)); Text(tracks, color = Color.White.copy(alpha=0.8f), fontSize = 12.sp) }
        }
    }
}

@Composable
fun MockPlaylistColorCard(title: String, tracks: String, icon: ImageVector, color1: Color, color2: Color) {
    Box(
        modifier = Modifier.fillMaxWidth().aspectRatio(0.9f).clip(RoundedCornerShape(16.dp)).background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(color1, color2))).padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 2)
            Spacer(modifier = Modifier.height(4.dp))
            Text(tracks, color = Color.White.copy(alpha=0.8f), fontSize = 12.sp)
        }
    }
}

@Composable
fun MockPlaylistsTab(layoutMode: LayoutMode, accentColor: Color) {
    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(16.dp))
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(48.dp).clip(CircleShape).background(accentColor), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(TablerIcons.Plus, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary); Spacer(modifier = Modifier.width(8.dp)); Text("Add new", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Medium) }
        }
        Spacer(modifier = Modifier.height(16.dp))
        when (layoutMode) {
            LayoutMode.LIST -> {
                androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), userScrollEnabled = false) {
                    item { MockPlaylistListCard(getString("liked_songs"), "20 ${getString("tracks")}", TablerIcons.Heart, Color(0xFFF94B73), Color(0xFFCE0A42)) }
                    item { MockPlaylistListCard(getString("recently_played"), "45 ${getString("tracks")}", TablerIcons.History, Color(0xFF56A8F4), Color(0xFF2C6ED5)) }
                    item { MockPlaylistListCard(getString("most_played"), "15 ${getString("tracks")}", TablerIcons.ChartBar, Color(0xFF45C088), Color(0xFF168153)) }
                    item {
                        ElevatedCard(modifier = Modifier.fillMaxWidth().height(80.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)) {
                            Row(modifier = Modifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(TablerIcons.Playlist, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(16.dp))
                                Column { Text(getString("songs"), color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 16.sp); Spacer(modifier = Modifier.height(4.dp)); Text("50 ${getString("tracks")}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
                            }
                        }
                    }
                }
            }
            LayoutMode.GRID_2, LayoutMode.GRID_3 -> {
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(columns = if (layoutMode == LayoutMode.GRID_3) androidx.compose.foundation.lazy.grid.GridCells.Fixed(3) else androidx.compose.foundation.lazy.grid.GridCells.Fixed(2), modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), userScrollEnabled = false) {
                    item { MockPlaylistColorCard(getString("liked_songs"), "20 ${getString("tracks")}", TablerIcons.Heart, Color(0xFFF94B73), Color(0xFFCE0A42)) }
                    item { MockPlaylistColorCard(getString("recently_played"), "45 ${getString("tracks")}", TablerIcons.History, Color(0xFF56A8F4), Color(0xFF2C6ED5)) }
                    item { MockPlaylistColorCard(getString("most_played"), "15 ${getString("tracks")}", TablerIcons.ChartBar, Color(0xFF45C088), Color(0xFF168153)) }
                    item {
                        ElevatedCard(modifier = Modifier.fillMaxWidth().aspectRatio(0.9f), shape = RoundedCornerShape(16.dp), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)) {
                            Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center) {
                                Icon(TablerIcons.Playlist, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(getString("songs"), color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 2)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("50 ${getString("tracks")}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MockArtistsTab(layoutMode: LayoutMode, accentColor: Color) {
    when (layoutMode) {
        LayoutMode.LIST -> {
            androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 16.dp), userScrollEnabled = false) {
                items(1) { MockArtistRow("Unknown Artist", "Midnight City", "Neon Dreams...", "12 tracks") }
                items(1) { MockArtistRow("Generic Band", "Electric Night...", null, "1 tracks") }
                items(1) { MockArtistRow("The Melodies", "Acoustic Sunset...", null, "1 tracks") }
                items(1) { MockArtistRow("Chill Sounds", "Ocean Waves...", null, "1 tracks") }
            }
        }
        LayoutMode.GRID_2 -> {
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2), modifier = Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), userScrollEnabled = false) {
                items(1) { MockGridCard(title = "Unknown Artist", subtitle = "12 tracks", icon = TablerIcons.User) }
                items(1) { MockGridCard(title = "Generic Band", subtitle = "1 tracks", icon = TablerIcons.User) }
                items(1) { MockGridCard(title = "The Melodies", subtitle = "1 tracks", icon = TablerIcons.User) }
                items(1) { MockGridCard(title = "Chill Sounds", subtitle = "1 tracks", icon = TablerIcons.User) }
            }
        }
        LayoutMode.GRID_3 -> {
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3), modifier = Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), userScrollEnabled = false) {
                items(1) { MockGridCard(title = "Unknown Artist", subtitle = "12 tracks", icon = TablerIcons.User) }
                items(1) { MockGridCard(title = "Generic Band", subtitle = "1 tracks", icon = TablerIcons.User) }
                items(1) { MockGridCard(title = "The Melodies", subtitle = "1 tracks", icon = TablerIcons.User) }
                items(1) { MockGridCard(title = "Chill Sounds", subtitle = "1 tracks", icon = TablerIcons.User) }
                items(1) { MockGridCard(title = "Local Heroes", subtitle = "3 tracks", icon = TablerIcons.User) }
                items(1) { MockGridCard(title = "Sound Garden", subtitle = "5 tracks", icon = TablerIcons.User) }
            }
        }
    }
}

@Composable
fun MockAlbumsTab(layoutMode: LayoutMode, accentColor: Color) {
    when (layoutMode) {
        LayoutMode.LIST -> {
            androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 16.dp), userScrollEnabled = false) {
                items(1) { MockAlbumRow("First Encounter", "Unknown Artist", "1 tracks") }
                items(1) { MockAlbumRow("Deep House", "Generic Band", "1 tracks") }
                items(1) { MockAlbumRow("Acoustic Sessions", "The Melodies", "1 tracks") }
                items(1) { MockAlbumRow("Future Bass", "Chill Sounds", "1 tracks") }
            }
        }
        LayoutMode.GRID_2 -> {
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2), modifier = Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), userScrollEnabled = false) {
                items(1) { MockGridCard(title = "First Encounter", subtitle = "Unknown Artist", extra = "1 tracks") }
                items(1) { MockGridCard(title = "Deep House", subtitle = "Generic Band", extra = "1 tracks") }
                items(1) { MockGridCard(title = "Acoustic Sessions", subtitle = "The Melodies", extra = "1 tracks") }
                items(1) { MockGridCard(title = "Future Bass", subtitle = "Chill Sounds", extra = "1 tracks") }
            }
        }
        LayoutMode.GRID_3 -> {
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3), modifier = Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), userScrollEnabled = false) {
                items(1) { MockGridCard(title = "First Encounter", subtitle = "Unknown Artist", extra = "1 tracks") }
                items(1) { MockGridCard(title = "Deep House", subtitle = "Generic Band", extra = "1 tracks") }
                items(1) { MockGridCard(title = "Acoustic Sessions", subtitle = "The Melodies", extra = "1 tracks") }
                items(1) { MockGridCard(title = "Future Bass", subtitle = "Chill Sounds", extra = "1 tracks") }
                items(1) { MockGridCard(title = "Local Heroes", subtitle = "Generic Band", extra = "2 tracks") }
                items(1) { MockGridCard(title = "Sound Garden", subtitle = "The Melodies", extra = "5 tracks") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val languageCode by viewModel.languageCode.collectAsStateWithLifecycle()
    val languages = listOf("en" to "English", "bn" to "বাংলা (Bengali)", "hi" to "हिंदी (Hindi)")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(getString("language"), color = MaterialTheme.colorScheme.onBackground) },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(TablerIcons.ArrowLeft, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            languages.forEach { (code, name) ->
                val isSelected = languageCode == code
                val accentColor = com.musicplayer.lytrix.ui.theme.LocalAccentColor.current
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { viewModel.setLanguageCode(code) }.padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = name, fontSize = 18.sp, color = if (isSelected) accentColor else MaterialTheme.colorScheme.onBackground, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                    if (isSelected) { Icon(imageVector = TablerIcons.CircleCheck, contentDescription = "Selected", tint = accentColor) }
                }
            }
        }
    }
}

fun gzipCompress(input: String): String {
    val baos = java.io.ByteArrayOutputStream()
    java.util.zip.GZIPOutputStream(baos).use { it.write(input.toByteArray(Charsets.UTF_8)) }
    return android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.NO_WRAP)
}

fun gzipDecompress(base64: String): String {
    val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
    return java.util.zip.GZIPInputStream(java.io.ByteArrayInputStream(bytes))
        .bufferedReader(Charsets.UTF_8)
        .use { it.readText() }
}
fun restartApp(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
    context.startActivity(Intent.makeRestartActivityTask(intent?.component))
    Runtime.getRuntime().exit(0)
}

fun performSilentSync(context: Context) {
    if (BackupSyncState.isSyncing || BackupSyncState.isRestoring) return
    if (System.currentTimeMillis() - BackupSyncState.lastSyncTime < 60000) return

    val user = FirebaseAuth.getInstance().currentUser ?: return
    val appContext = context.applicationContext
    val prefs = appContext.getSharedPreferences("premium_prefs", Context.MODE_PRIVATE)
    val backupPrefs = appContext.getSharedPreferences("backup_prefs", Context.MODE_PRIVATE)
    val userRef = FirebaseDatabase.getInstance().reference.child("users").child(user.uid)

    // Premium sync (আগের মতোই)
    listOf("is_premium", "is_permanent").forEach { name ->
        userRef.child(name).get().addOnSuccessListener { snap ->
            snap.getValue(Boolean::class.java)?.let { value ->
                prefs.edit().putBoolean(name, value).apply()
                CoroutineScope(Dispatchers.IO).launch {
                    appContext.dataStore.edit { it[booleanPreferencesKey(name)] = value }
                }
            }
        }
    }

    BackupSyncState.isSyncing = true
    CoroutineScope(Dispatchers.IO).launch {
        var needRestart = false
        try {
            val plNames = appContext.getSharedPreferences("online_playlists_meta", Context.MODE_PRIVATE).getStringSet("names", emptySet()) ?: emptySet()
            val showPlNames = appContext.getSharedPreferences("online_shows_playlists_meta", Context.MODE_PRIVATE).getStringSet("names", emptySet()) ?: emptySet()
            val pcNames = (appContext.getSharedPreferences("online_podcasts_meta", Context.MODE_PRIVATE).getStringSet("names", emptySet()) ?: emptySet()).filter { it.trim().lowercase() !in setOf("new episodes", "continue listening") }
            val abNames = (appContext.getSharedPreferences("online_audiobooks_meta", Context.MODE_PRIVATE).getStringSet("names", emptySet()) ?: emptySet()).filter { it.trim().lowercase() != "continue listening" }
            val likedIds = appContext.getSharedPreferences("online_liked_songs", Context.MODE_PRIVATE).getStringSet("liked_ids", emptySet()) ?: emptySet()
            val podLikedIds = appContext.getSharedPreferences("online_liked_podcasts", Context.MODE_PRIVATE).getStringSet("liked_ids", emptySet()) ?: emptySet()
            val abLikedIds = appContext.getSharedPreferences("online_liked_audiobooks", Context.MODE_PRIVATE).getStringSet("liked_ids", emptySet()) ?: emptySet()
            val subArtists = appContext.getSharedPreferences("subscribed_artists_prefs", Context.MODE_PRIVATE).getStringSet("subscribed_ids", emptySet()) ?: emptySet()
            val subPods = appContext.getSharedPreferences("subscribed_podcast_channels_prefs", Context.MODE_PRIVATE).getStringSet("subscribed_ids", emptySet()) ?: emptySet()
            val downloadedExists = appContext.getSharedPreferences("downloaded_meta", Context.MODE_PRIVATE).all.isNotEmpty() || appContext.getSharedPreferences("downloaded_audiobooks_meta", Context.MODE_PRIVATE).all.isNotEmpty()
            val laterEpisodes = appContext.getSharedPreferences("online_podcasts_meta", Context.MODE_PRIVATE).getStringSet("episodes_for_later_ids", emptySet()) ?: emptySet()
            val laterBooks = appContext.getSharedPreferences("online_audiobooks_meta", Context.MODE_PRIVATE).getStringSet("books_for_later_ids", emptySet()) ?: emptySet()
            val hasLaterItems = laterEpisodes.isNotEmpty() || laterBooks.isNotEmpty() ||
                appContext.getSharedPreferences("online_podcast_Episodes for later", Context.MODE_PRIVATE).all.isNotEmpty() ||
                appContext.getSharedPreferences("online_ab_Books for later", Context.MODE_PRIVATE).all.isNotEmpty()

            val localEmpty = plNames.isEmpty() && showPlNames.isEmpty() && pcNames.isEmpty() && abNames.isEmpty() &&
                likedIds.isEmpty() && podLikedIds.isEmpty() && abLikedIds.isEmpty() && subArtists.isEmpty() &&
                subPods.isEmpty() && !downloadedExists && !hasLaterItems

            val lastUid = backupPrefs.getString("last_synced_uid", null)
            val skipCloudCheck = lastUid == null && !localEmpty   // পুরনো ইউজার, লোকাল ডেটা আছে

            if (lastUid != user.uid && !skipCloudCheck) {
                // নতুন লগইন: cloud-এ ডেটা থাকলে cloud-ই জিতবে
                val cloudRaw = userRef.child("backup_data").get().await().value as? String
                val cloudJson = try {
                    if (!cloudRaw.isNullOrEmpty()) gzipDecompress(cloudRaw) else null
                } catch (_: Exception) {
                    cloudRaw   // পুরনো plain backup থাকলে সেটাই ব্যবহার করবে
                }
                val hasCloud = !cloudJson.isNullOrEmpty() && cloudJson.length > 100
                Log.d("Backup", "New login. Cloud data found? $hasCloud")
                backupPrefs.edit().putString("last_synced_uid", user.uid).commit()  // loop আটকানোর ফ্ল্যাগ
                if (hasCloud) {
                    restoreBackupData(appContext, JSONObject(cloudJson!!), restoreRoom = false)
                    backupPrefs.edit().putString("last_backup_hash", cloudJson.hashCode().toString()).commit()
                    BackupSyncState.lastSyncTime = System.currentTimeMillis()
                    needRestart = true
                    return@launch
                }
            } else if (lastUid == null) {
                backupPrefs.edit().putString("last_synced_uid", user.uid).commit()
            }

            if (localEmpty) {
                Log.d("Backup", "Local empty, nothing to upload")
                return@launch
            }

            // ===== UPLOAD =====
            val rawJson = generateBackupData(appContext, includeRoom = false).toString()
            if (rawJson.length > 9_000_000) {
                Log.w("Backup", "Still too large, skip")
                return@launch
            }

            val newHash = rawJson.hashCode().toString()
            if (newHash == backupPrefs.getString("last_backup_hash", "")) {
                Log.d("Backup", "Same data, skip")
                return@launch
            }

            // safety check - পুরনো ডাটা gzip করা থাকতে পারে
            val oldRaw = userRef.child("backup_data").get().await().value as? String
            if (!oldRaw.isNullOrEmpty() && oldRaw.length > 100) {
                val oldJson = try {
                    gzipDecompress(oldRaw)
                } catch (_: Exception) {
                    oldRaw
                }
                val oldLiked = try {
                    JSONObject(oldJson).optJSONObject("online_liked_songs")?.optJSONArray("liked_ids")?.length() ?: 0
                } catch (_: Exception) {
                    0
                }
                val hasLocalShowsData = podLikedIds.isNotEmpty() || abLikedIds.isNotEmpty() ||
                    showPlNames.isNotEmpty() || hasLaterItems || pcNames.isNotEmpty() || abNames.isNotEmpty() || subPods.isNotEmpty()
                if (oldLiked > 0 && likedIds.isEmpty() && !hasLocalShowsData) {
                    Log.w("Backup", "Local looks wiped, upload blocked")
                    return@launch
                }
                // পুরনো raw (gzipped বা plain) ব্যাকআপ রাখছি
                userRef.child("backup_data_prev").setValue(oldRaw).await()
            }

            // Compress করে আপলোড
            val jsonStringToUpload = try {
                gzipCompress(rawJson)
            } catch (_: Exception) {
                rawJson   // fallback
            }

            userRef.child("backup_data").setValue(jsonStringToUpload).await()
            backupPrefs.edit().putString("last_backup_hash", newHash).apply()
            BackupSyncState.lastSyncTime = System.currentTimeMillis()
            Log.d("Backup", "Auto backup success (compressed: ${jsonStringToUpload.length} bytes)")
        } catch (e: Exception) {
            Log.e("Backup", "Sync failed", e)
        } finally {
            BackupSyncState.isSyncing = false
            if (needRestart) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(appContext, "Data restored! Restarting...", Toast.LENGTH_LONG).show()
                }
                delay(1500)
                restartApp(appContext)
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun FeedbackScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToFaq: () -> Unit,
    onNavigateToHistory: () -> Unit
) {
    val context = LocalContext.current
    val accentColor = com.musicplayer.lytrix.ui.theme.LocalAccentColor.current
    val isLightMode = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val iconTint = if (isLightMode) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.8f)

    // UI States
    var selectedCategory by remember { mutableStateOf("") }
    var selectedAdIssue by remember { mutableStateOf("") }
    var feedbackText by remember { mutableStateOf("") }
    var isConsentChecked by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }

    val categories = listOf("Lyric", "ADs", "File not found", "File can't play", "Music stops", "App Slow", "Others")
    val adIssues = listOf("Full screen ads show too long", "Too big", "Too many", "Full screen ads unskippable", "Inappropriate contents", "Non-full screen ads can't be closed")

    val isFormValid = selectedCategory.isNotEmpty() && isConsentChecked &&
            (if (selectedCategory == "ADs") selectedAdIssue.isNotEmpty() else feedbackText.isNotBlank())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tell us the problem", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(TablerIcons.ArrowLeft, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground) }
                },
                actions = {
                    IconButton(onClick = onNavigateToHistory) { Icon(TablerIcons.Mail, contentDescription = "History", tint = iconTint) }
                    IconButton(onClick = onNavigateToFaq) { Icon(imageVector = TablerIcons.InfoCircle, contentDescription = "FAQ", tint = iconTint) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // 🔥 ফিক্স: ম্যানুয়াল গ্রুপিং, কোনো টেক্সট আর ভাঙবে না
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // আমরা ম্যানুয়ালি সারিগুলো (Rows) সাজিয়ে দিচ্ছি যাতে টেক্সট জায়গা পায়
                val rows = listOf(
                    listOf("Lyric", "ADs", "Others"),
                    listOf("File not found", "App Slow"),
                    listOf("File can't play", "Music stops")
                )

                rows.forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        rowItems.forEach { category ->
                            val isSelected = selectedCategory == category
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (isSelected) accentColor else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
                                    .clickable { selectedCategory = category; selectedAdIssue = "" }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = category,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            androidx.compose.animation.AnimatedVisibility(visible = selectedCategory == "ADs") {
                Column {
                    Text("Select what Ad issues you met:", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    adIssues.forEach { issue ->
                        Row(modifier = Modifier.fillMaxWidth().clickable { selectedAdIssue = issue }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selectedAdIssue == issue, onClick = { selectedAdIssue = issue }, colors = RadioButtonDefaults.colors(selectedColor = accentColor))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = issue, color = MaterialTheme.colorScheme.onBackground, fontSize = 15.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            Text("Details *", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = feedbackText,
                onValueChange = { if (it.length <= 500) feedbackText = it },
                modifier = Modifier.fillMaxWidth().height(150.dp),
                placeholder = { Text("Explain what happened, or suggestions.", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)) },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.03f), focusedContainerColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f), unfocusedBorderColor = Color.Transparent, focusedBorderColor = accentColor)
            )
            Text(text = "${feedbackText.length}/500", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), fontSize = 12.sp, modifier = Modifier.align(Alignment.End).padding(top = 8.dp))

            Spacer(modifier = Modifier.height(32.dp))

            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                Checkbox(checked = isConsentChecked, onCheckedChange = { isConsentChecked = it }, colors = CheckboxDefaults.colors(checkedColor = accentColor))
                Text("To continue, Lytrix Player will share your personal data with our customer service provider for providing and improving customer problem solving solutions.", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 10.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    isSubmitting = true
                    viewModel.submitFeedback(
                        category = selectedCategory, adIssue = selectedAdIssue, details = feedbackText,
                        onSuccess = {
                            isSubmitting = false
                            Toast.makeText(context, "Feedback sent successfully! Thank you.", Toast.LENGTH_SHORT).show()
                            onNavigateBack()
                        },
                        onError = { errorMsg ->
                            isSubmitting = false
                            Toast.makeText(context, "Error: $errorMsg", Toast.LENGTH_LONG).show()
                        }
                    )
                },
                enabled = isFormValid && !isSubmitting,
                modifier = Modifier.fillMaxWidth().height(55.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor, disabledContainerColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)),
                shape = RoundedCornerShape(24.dp)
            ) {
                if (isSubmitting) {
                    LytrixExpressiveSpinner(color = Color.White, size = 24.dp, strokeWidth = 2.5.dp)
                } else {
                    Text(text = "Submit", color = if (isFormValid) Color.White else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaqScreen(onNavigateBack: () -> Unit) {
    val accentColor = com.musicplayer.lytrix.ui.theme.LocalAccentColor.current
    val isLightMode = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val iconTint = if (isLightMode) Color.Black else Color.White

    // FAQ ডাটা লিস্ট (প্রশ্ন এবং উত্তর)
    val faqList = listOf(
        Pair("I cannot play my songs/files", "Please check if the file format is supported. Sometimes corrupted files or files with DRM protection cannot be played. Try playing another file to confirm."),
        Pair("My songs/files stops by itself during playing", "This usually happens due to background battery optimization. Please go to Settings > Preferences and enable 'Ensure continuous background play'."),
        Pair("My songs/files play without sounds", "Make sure your device media volume is turned up and no other app is hijacking the audio focus. Try restarting the app."),
        Pair("How do 'Gapless', 'Crossfade' and 'Play together with other apps' work?", "Gapless removes silence between tracks. Crossfade blends the end of one song into the next. 'Play together' allows Lytrix to play audio even if another app is playing sound."),
        Pair("How to hidden/unhidden songs/files? What's that for?", "You can hide short audio clips (like voice notes) from appearing in your library. Go to Settings > Hidden files to manage these rules."),
        Pair("My songs don't have lyrics, I want lyrics", "Currently, Lytrix Player supports embedded lyrics. If a song file has lyrics inside its metadata, it will be shown automatically."),
        Pair("I cannot find my songs/files, they disappeared", "Try pulling down on the Songs list to refresh and rescan your device storage. Also, ensure the app has the necessary storage permissions."),
        Pair("I deleted my songs/files, I want them back", "Check the 'Recently deleted' section in Settings. If it was deleted from there as well, it cannot be recovered by the app."),
        Pair("How to change playing mode, such as random playback?", "Tap on the playing song to open the full player screen. Use the Shuffle icon (crossed arrows) to turn on random playback."),
        Pair("Where Can I get new songs or download songs?", "You can explore the 'Online' section to find trending music. Currently, direct downloads may depend on the source availability.")
    )

    var expandedIndex by remember { mutableIntStateOf(-1) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FAQ", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(TablerIcons.ArrowLeft, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground) }
                },
                actions = {
                    IconButton(onClick = { /* TODO: Search feature */ }) { Icon(TablerIcons.Search, contentDescription = "Search", tint = iconTint) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            items(faqList.size) { index ->
                val faq = faqList[index]
                val isExpanded = expandedIndex == index

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandedIndex = if (isExpanded) -1 else index }
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = TablerIcons.Help,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = faq.first,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 15.sp,
                            fontWeight = if (isExpanded) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    androidx.compose.animation.AnimatedVisibility(visible = isExpanded) {
                        Text(
                            text = faq.second,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            modifier = Modifier.padding(top = 12.dp, start = 40.dp)
                        )
                    }
                }

                if (index < faqList.size - 1) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f), modifier = Modifier.padding(horizontal = 20.dp))
                }
            }
            item { Spacer(modifier = Modifier.height(40.dp)) }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackHistoryScreen(viewModel: SettingsViewModel, onNavigateBack: () -> Unit) {
    val historyList by viewModel.feedbackHistory.collectAsStateWithLifecycle()
    val accentColor = com.musicplayer.lytrix.ui.theme.LocalAccentColor.current
    val isLightMode = MaterialTheme.colorScheme.background.luminance() > 0.5f

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Feedback History", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(TablerIcons.ArrowLeft, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (historyList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(TablerIcons.Inbox, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f), modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No feedback submitted yet", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                }
            }
        } else {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(historyList) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Pending স্ট্যাটাস ব্যাজ এখান থেকে রিমুভ করা হয়েছে
                            Text(
                                text = item.category,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = item.details,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                fontSize = 14.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            val sdf = java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault())
                            Text(
                                text = sdf.format(java.util.Date(item.timestamp)),
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
// 🔥 রিয়েল-টাইম প্রিমিয়াম সিঙ্ক (performSilentSync যেমন আছে তেমনই থাকবে)
fun attachPremiumListener(context: Context): () -> Unit {
    val user = FirebaseAuth.getInstance().currentUser ?: return {}
    val appContext = context.applicationContext
    val prefs = appContext.getSharedPreferences("premium_prefs", Context.MODE_PRIVATE)
    val userRef = FirebaseDatabase.getInstance().reference.child("users").child(user.uid)
    val attached = mutableListOf<Pair<DatabaseReference, ValueEventListener>>()

    listOf("is_premium", "is_permanent").forEach { name ->
        val ref = userRef.child(name)
        val listener = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                val v = s.getValue(Boolean::class.java) ?: false
                prefs.edit().putBoolean(name, v).apply()
                CoroutineScope(Dispatchers.IO).launch {
                    appContext.dataStore.edit { it[booleanPreferencesKey(name)] = v }
                }
                AdManager.refreshAdFreeState(appContext)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(listener)
        attached += ref to listener
    }

    listOf("subscription_expiry", "ad_free_expiry").forEach { name ->
        val ref = userRef.child(name)
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                val v = (s.value as? Number)?.toLong() ?: return
                prefs.edit().putLong(name, v).apply()
                CoroutineScope(Dispatchers.IO).launch {
                    appContext.dataStore.edit { it[longPreferencesKey(name)] = v }
                }
                AdManager.refreshAdFreeState(appContext)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(l)
        attached += ref to l
    }

    return { attached.forEach { (ref, l) -> ref.removeEventListener(l) } }
}

object PremiumSyncManager {
    private var detachCurrent: (() -> Unit)? = null

    fun start(context: Context) {
        detachCurrent?.invoke()
        detachCurrent = attachPremiumListener(context)
    }

    fun stop() {
        detachCurrent?.invoke()
        detachCurrent = null
    }
}
object BackupTrigger {
    private var job: kotlinx.coroutines.Job? = null
    private var started = false
    private var appCtx: Context? = null

    private val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        appCtx?.let { request(it) }
    }

    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        appCtx = app
        listOf(
            "online_liked_songs", "online_liked_podcasts", "online_liked_audiobooks",
            "subscribed_artists_prefs", "online_playlists_meta", "online_albums_meta",
            "online_podcasts_meta", "online_audiobooks_meta", "online_shows_playlists_meta", "playlist_covers"
        ).forEach { app.getSharedPreferences(it, Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(listener) }
    }

    // ৩ সেকেন্ড অপেক্ষা (পরপর লাইক এক সিঙ্কে যাবে), চলমান সিঙ্ক শেষ হলে throttle বাইপাস করে সিঙ্ক
    fun request(context: Context) {
        if (BackupSyncState.isRestoring) return
        val app = context.applicationContext
        job?.cancel()
        job = CoroutineScope(Dispatchers.IO).launch {
            delay(3000)
            var waited = 0
            while (BackupSyncState.isSyncing && waited < 30000) { delay(500); waited += 500 }
            if (BackupSyncState.isRestoring) return@launch
            BackupSyncState.lastSyncTime = 0L
            performSilentSync(app)
        }
    }
}