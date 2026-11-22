package com.musicplayer.lytrix.ui

import android.content.Context

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.*
import kotlinx.coroutines.launch
import com.musicplayer.lytrix.innertube.utils.parseCookieString
import com.musicplayer.lytrix.spotify.SpotifyPreferencesManager
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.CircleShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineSettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToAiSettings: () -> Unit = {},
    onNavigateToPrivacySettings: () -> Unit = {},
    onNavigateToSpotifyLogin: () -> Unit = {},
    onNavigateToArtworkSettings: () -> Unit = {}
) {
    BackHandler { onNavigateBack() }

    val context = LocalContext.current
    val activity = context as? android.app.Activity
    var showPremiumDialog by remember { mutableStateOf(false) }
    var showAdFreeDialog by remember { mutableStateOf(false) }
    var showTokenDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var autoExportToStorage by remember {
        mutableStateOf(context.getSharedPreferences("download_settings", Context.MODE_PRIVATE).getBoolean("auto_export_to_storage", false))
    }
    var preloadNextStream by remember {
        mutableStateOf(context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE).getBoolean("preload_next_stream", true))
    }
    val onlineAppPrefs = remember { context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE) }
    var videoBackgroundPreload by remember {
        mutableStateOf(onlineAppPrefs.getBoolean("video_background_preload", true))
    }
    var sponsorBlockEnabled by remember { mutableStateOf(onlineAppPrefs.getBoolean("sponsorblock_enabled", false)) }
    var sponsorBlockShowToast by remember { mutableStateOf(onlineAppPrefs.getBoolean("sponsorblock_show_toast", false)) }
    var sponsorBlockCategoriesRaw by remember {
        mutableStateOf(onlineAppPrefs.getString("sponsorblock_categories", com.musicplayer.lytrix.service.SponsorBlockManager.DEFAULT_CATEGORIES) ?: com.musicplayer.lytrix.service.SponsorBlockManager.DEFAULT_CATEGORIES)
    }
    var showSponsorBlockCategoriesDialog by remember { mutableStateOf(false) }
    var newReleaseAlertsEnabled by remember { mutableStateOf(com.musicplayer.lytrix.sync.NewReleaseChecker.isEnabled(context)) }
    var showNewReleasesDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val prefManager = remember { YoutubePreferencesManager(context) }
    val spotifyPrefManager = remember { SpotifyPreferencesManager(context) }
    var showSpotifyLogoutDialog by remember { mutableStateOf(false) }

    // Spotify লগিন ও সেটিংস রিয়েলটাইম অবজার্ভ
    val isSpotifyLoggedIn by spotifyPrefManager.isLoggedIn.collectAsState(initial = false)
    val spotifyAccountName by spotifyPrefManager.accountName.collectAsState(initial = "")
    val spotifyAvatarUrl by spotifyPrefManager.accountAvatarUrl.collectAsState(initial = "")
    val spotifyLyricsEnabled by spotifyPrefManager.lyricsEnabled.collectAsState(initial = false)
    val spotifyCanvasEnabled by spotifyPrefManager.canvasEnabled.collectAsState(initial = false)
    val spotifyAnimatedArtworkEnabled by spotifyPrefManager.animatedArtworkEnabled.collectAsState(initial = false)
    val viviCanvasEnabled by spotifyPrefManager.viviCanvasEnabled.collectAsState(initial = false)
    val artistHeroVideoEnabled by spotifyPrefManager.artistHeroVideoEnabled.collectAsState(initial = false)
    val spotifyPlaylistsEnabled by spotifyPrefManager.playlistsEnabled.collectAsState(initial = true)

    // লগিন স্ট্যাটাস রিয়েলটাইম অবজার্ভ
    val cookieState by prefManager.cookie.collectAsState(initial = "")
    val visitorDataState by prefManager.visitorData.collectAsState(initial = "")
    val dataSyncIdState by prefManager.dataSyncId.collectAsState(initial = "")
    val authUserState by prefManager.authUser.collectAsState(initial = "")
    val accountNameState by prefManager.accountName.collectAsState(initial = "")
    val useLoginForBrowseState by prefManager.useLoginForBrowse.collectAsState(initial = true)
    val ytmSyncState by prefManager.ytmSync.collectAsState(initial = true)
    val accountEmailState by prefManager.accountEmail.collectAsState(initial = "")
    val accountChannelHandleState by prefManager.accountChannelHandle.collectAsState(initial = "")
    val isLoggedIn = remember(cookieState) {
        cookieState.isNotBlank() && "SAPISID" in parseCookieString(cookieState)
    }

    val appBg = Color(0xFF070504)
    val cardBg = Color(0xFF161210)
    val iconBg = Color(0xFF2A1F1B)
    val iconTint = Color(0xFFD6B5A6)
    val textColor = Color(0xFFF7EBE5)
    val headerColor = Color(0xFFD6B5A6)
    val logoutRed = Color(0xFFE57373)

    // ── Logout Confirmation Dialog ───────────────────────────────────────────
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Logout?", color = textColor) },
            text = {
                Text(
                    "Are you sure you want to logout from your YouTube account?",
                    color = textColor.copy(alpha = 0.7f)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            prefManager.clearAuthData()
                            com.musicplayer.lytrix.innertube.YouTube.authState =
                                com.musicplayer.lytrix.innertube.PlaybackAuthState.EMPTY
                            android.webkit.CookieManager.getInstance().removeAllCookies(null)
                            android.webkit.CookieManager.getInstance().flush()
                            showLogoutDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = logoutRed)
                ) { Text("Logout", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel", color = textColor)
                }
            },
            containerColor = cardBg
        )
    }

    // ── Token Login Dialog (Meld-এর হুবহু টেমপ্লেট) ────────────────────────
    if (showTokenDialog) {
        var tokenText by remember { mutableStateOf("") }
          LaunchedEffect(cookieState) {
            tokenText = buildString {
                appendLine("***INNERTUBE COOKIE*** =$cookieState")
                appendLine("***VISITOR DATA*** =$visitorDataState")
                appendLine("***DATASYNC ID*** =$dataSyncIdState")
                appendLine("***AUTH USER*** =$authUserState")
                appendLine("***ACCOUNT NAME*** =$accountNameState")
                appendLine("***ACCOUNT EMAIL*** =$accountEmailState")
                append("***ACCOUNT CHANNEL HANDLE*** =$accountChannelHandleState")
            }
        }
        val cookieLine = tokenText.lines().find { it.startsWith("***INNERTUBE COOKIE*** =") }
        val cookieValue = cookieLine?.substringAfter("***INNERTUBE COOKIE*** =")?.trim() ?: ""
        val isValid = cookieValue.isNotEmpty() && "SAPISID" in parseCookieString(cookieValue)
        AlertDialog(
            onDismissRequest = { showTokenDialog = false },
            title = { Text("Login with Token", color = textColor) },
            text = {
                Column {
                    Text(
                        "Fill in the fields with your YouTube session data.",
                        color = textColor.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tokenText,
                        onValueChange = { tokenText = it },
                        modifier = Modifier.fillMaxWidth().height(260.dp),
                        maxLines = 20,
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = textColor)
                    )
                    if (!isValid && cookieValue.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text("⚠️ Cookie must contain SAPISID", color = logoutRed, fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = isValid,
                    onClick = {
                        var cookie = ""
                        var visitorDataValue = ""
                        var dataSyncIdValue = ""
                        var authUserValue = "0"
                        var accountNameValue = ""
                        var accountEmailValue = ""
                        var accountChannelHandleValue = ""

                        tokenText.split("\n").forEach { line ->
                            when {
                                line.startsWith("***INNERTUBE COOKIE*** =") ->
                                    cookie = line.substringAfter("***INNERTUBE COOKIE*** =")
                                line.startsWith("***VISITOR DATA*** =") ->
                                    visitorDataValue = line.substringAfter("***VISITOR DATA*** =")
                                line.startsWith("***DATASYNC ID*** =") ->
                                    dataSyncIdValue = line.substringAfter("***DATASYNC ID*** =")
                                line.startsWith("***AUTH USER*** =") ->
                                    authUserValue = line.substringAfter("***AUTH USER*** =")
                                line.startsWith("***ACCOUNT NAME*** =") ->
                                    accountNameValue = line.substringAfter("***ACCOUNT NAME*** =")
                                line.startsWith("***ACCOUNT EMAIL*** =") ->
                                    accountEmailValue = line.substringAfter("***ACCOUNT EMAIL*** =")
                                line.startsWith("***ACCOUNT CHANNEL HANDLE*** =") ->
                                    accountChannelHandleValue = line.substringAfter("***ACCOUNT CHANNEL HANDLE*** =")
                            }
                        }

                        scope.launch {
                            prefManager.saveAuthData(
                                cookie = cookie,
                                visitorData = visitorDataValue,
                                dataSyncId = dataSyncIdValue,
                                authUser = authUserValue,
                                accountName = accountNameValue,
                                accountEmail = accountEmailValue,
                                accountChannelHandle = accountChannelHandleValue
                            )
                            com.musicplayer.lytrix.innertube.YouTube.authState =
                                com.musicplayer.lytrix.innertube.PlaybackAuthState(
                                    cookie = cookie,
                                    visitorData = visitorDataValue,
                                    dataSyncId = dataSyncIdValue
                                )
                            showTokenDialog = false
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showTokenDialog = false }) {
                    Text("Cancel", color = textColor)
                }
            },
            containerColor = cardBg
        )
    }

        // ── Spotify Logout Confirmation Dialog ──────────────────────────────
    if (showSpotifyLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showSpotifyLogoutDialog = false },
            title = { Text("Disconnect Spotify?", color = textColor) },
            text = {
                Text(
                    "Are you sure you want to disconnect your Spotify account?",
                    color = textColor.copy(alpha = 0.7f)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            spotifyPrefManager.clearAuthData()
                            showSpotifyLogoutDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = logoutRed)
                ) { Text("Disconnect", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showSpotifyLogoutDialog = false }) {
                    Text("Cancel", color = textColor)
                }
            },
            containerColor = cardBg
        )
    }

    // ── Main Screen ──────────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settings",
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = appBg)
            )
        },
        containerColor = appBg
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ── YouTube Account Section ──────────────────────────────────────
            item {
                Text(
                    text = "YouTube Account",
                    color = headerColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 4.dp)
                )
            }
            item {
                OnlineSettingsCardGroup(cardBg) {
                    if (isLoggedIn) {
                        // লগিন থাকলে: একাউন্ট নাম + Logout বাটন
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 14.dp, horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(iconBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = TablerIcons.User,
                                    contentDescription = null,
                                    tint = iconTint,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = accountNameState.ifBlank { "Logged In" },
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = textColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "YouTube Account",
                                    fontSize = 12.sp,
                                    color = textColor.copy(alpha = 0.5f)
                                )
                            }
                            OutlinedButton(
                                onClick = { showLogoutDialog = true },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = logoutRed),
                                border = ButtonDefaults.outlinedButtonBorder.copy(
                                    brush = SolidColor(logoutRed.copy(alpha = 0.5f))
                                )
                            ) {
                                Text("Logout", color = logoutRed, fontSize = 13.sp)
                            }
                        }
                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.05f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        // লগিন থাকলে Token দেখা/এডিট করার অপশন
                        OnlinePremiumSettingsItem(
                            icon = TablerIcons.Key,
                            title = "View / Edit Token",
                            iconBg = iconBg,
                            iconTint = iconTint,
                            textColor = textColor,
                            onClick = { showTokenDialog = true }
                        )
                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.05f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(iconBg), contentAlignment = Alignment.Center) {
                                Icon(TablerIcons.Refresh, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("More content", color = textColor, fontSize = 15.sp, modifier = Modifier.weight(1f))
                            androidx.compose.material3.Switch(
                                checked = useLoginForBrowseState,
                                onCheckedChange = { scope.launch { prefManager.setUseLoginForBrowse(it) } },
                                colors = androidx.compose.material3.SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFFFFCDD2),
                                    checkedTrackColor = Color(0xFFD32F2F)
                                )
                            )
                        }
                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.05f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(iconBg), contentAlignment = Alignment.Center) {
                                Icon(TablerIcons.Refresh, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("Auto-sync with account", color = textColor, fontSize = 15.sp, modifier = Modifier.weight(1f))
                            androidx.compose.material3.Switch(
                                checked = ytmSyncState,
                                onCheckedChange = { scope.launch { prefManager.setYtmSync(it) } },
                                colors = androidx.compose.material3.SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFFFFCDD2),
                                    checkedTrackColor = Color(0xFFD32F2F)
                                )
                            )
                        }
                    } else {
                        // লগিন না থাকলে: Login + Login With Token
                        OnlinePremiumSettingsItem(
                            icon = TablerIcons.Login,
                            title = "Login",
                            iconBg = iconBg,
                            iconTint = iconTint,
                            textColor = textColor,
                            onClick = onNavigateToLogin
                        )
                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.05f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        OnlinePremiumSettingsItem(
                            icon = TablerIcons.Key,
                            title = "Login With Token",
                            iconBg = iconBg,
                            iconTint = iconTint,
                            textColor = textColor,
                            onClick = { showTokenDialog = true }
                        )
                        }
                }
            }

            // ── Spotify Section ──────────────────────────────────────────────
            item {
                Text(
                    text = "Spotify",
                    color = headerColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 4.dp)
                )
            }
            item {
                OnlineSettingsCardGroup(cardBg) {
                    if (isSpotifyLoggedIn) {
                        // Logged in: Profile Row + Disconnect Button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 14.dp, horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (spotifyAvatarUrl.isNotBlank()) {
                                AsyncImage(
                                    model = spotifyAvatarUrl,
                                    contentDescription = "Spotify Avatar",
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF1DB954).copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = com.musicplayer.lytrix.R.drawable.ic_spotify),
                                        contentDescription = null,
                                        tint = Color(0xFF1DB954),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = spotifyAccountName.ifBlank { "Spotify User" },
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = textColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Connected",
                                    fontSize = 12.sp,
                                    color = Color(0xFF1DB954)
                                )
                            }
                            OutlinedButton(
                                onClick = { showSpotifyLogoutDialog = true },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = logoutRed),
                                border = ButtonDefaults.outlinedButtonBorder.copy(
                                    brush = SolidColor(logoutRed.copy(alpha = 0.5f))
                                )
                            ) {
                                Text("Disconnect", color = logoutRed, fontSize = 13.sp)
                            }
                        }

                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.05f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        // Switch 1: Enable Spotify Lyrics
                        SpotifySettingsSwitchItem(
                            icon = TablerIcons.Microphone,
                            title = "Enable Spotify Lyrics",
                            subtitle = "More synced lyrics for your tracks",
                            checked = spotifyLyricsEnabled,
                            onCheckedChange = { scope.launch { spotifyPrefManager.setLyricsEnabled(it) } },
                            textColor = textColor,
                            iconBg = iconBg,
                            iconTint = iconTint
                        )

                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.05f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )



                        // Switch 4: Show Spotify in Library
                        SpotifySettingsSwitchItem(
                            icon = TablerIcons.Playlist,
                            title = "Show Spotify in Library",
                            subtitle = "Display Spotify playlists & liked songs in Discover tab",
                            checked = spotifyPlaylistsEnabled,
                            onCheckedChange = { scope.launch { spotifyPrefManager.setPlaylistsEnabled(it) } },
                            textColor = textColor,
                            iconBg = iconBg,
                            iconTint = iconTint
                        )
                    } else {
                        // Not logged in: Log in to Spotify
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onNavigateToSpotifyLogin)
                                .padding(vertical = 16.dp, horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF1DB954).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = com.musicplayer.lytrix.R.drawable.ic_spotify),
                                    contentDescription = null,
                                    tint = Color(0xFF1DB954),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "Log in to Spotify",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = textColor
                                )
                                Text(
                                    text = "Connect for Spotify Lyrics, Canvas and Playlists",
                                    fontSize = 12.sp,
                                    color = textColor.copy(alpha = 0.5f)
                                )
                            }
                            Icon(
                                imageVector = TablerIcons.ChevronRight,
                                contentDescription = null,
                                tint = textColor.copy(alpha = 0.4f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Player & Content",
                    color = headerColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 4.dp)
                )
            }
            item {
                OnlineSettingsCardGroup(cardBg) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onNavigateToArtworkSettings)
                            .padding(vertical = 16.dp, horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(iconBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = TablerIcons.Palette,
                                contentDescription = null,
                                tint = iconTint,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "Artwork",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = textColor
                            )
                            Text(
                                text = "Canvas, animated covers, and artist visual sources",
                                fontSize = 12.sp,
                                color = textColor.copy(alpha = 0.5f)
                            )
                        }
                        Icon(
                            imageVector = TablerIcons.ChevronRight,
                            contentDescription = null,
                            tint = textColor.copy(alpha = 0.4f),
                            modifier = Modifier.size(20.dp)
                        )
                    }



                    HorizontalDivider(
                        color = Color.White.copy(alpha = 0.05f),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    OnlinePremiumSettingsItem(
                        icon = TablerIcons.Language, 
                        title = "AI lyrics translation", 
                        iconBg = iconBg, 
                        iconTint = iconTint, 
                        textColor = textColor, 
                        onClick = onNavigateToAiSettings
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(horizontal = 16.dp))
                    val isPrem = isUserPremium(context)
                    val isAdFree = AdManager.isAdFree(context)
                    val adFreeText = when {
                        isPrem -> "Included 👑"
                        isAdFree -> AdManager.getRemainingAdFreeTime(context).ifEmpty { "Active" }
                        else -> "Get Pass"
                    }
                    OnlinePremiumSettingsItem(
                        icon = TablerIcons.Ban, 
                        title = "Ad-free Pass", 
                        value = adFreeText,
                        iconBg = iconBg, 
                        iconTint = iconTint, 
                        textColor = textColor, 
                        onClick = {
                            if (isPrem) {
                                android.widget.Toast.makeText(context, "👑 You have Premium! Ad-free music is fully included.", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                showAdFreeDialog = true
                            }
                        }
                    )
                }
            }

            item {
                Text(
                    text = "Online Playback",
                    color = headerColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 4.dp)
                )
            }
            item {
                OnlineSettingsCardGroup(cardBg) {
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(iconBg), contentAlignment = Alignment.Center) {
                            Icon(TablerIcons.PlayerSkipForward, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Next Stream Preloader", color = textColor, fontSize = 15.sp)
                            Text("Pre-resolve upcoming YouTube tracks for instant gapless skips", color = textColor.copy(alpha = 0.5f), fontSize = 12.sp)
                        }
                        androidx.compose.material3.Switch(
                            checked = preloadNextStream,
                            onCheckedChange = {
                                preloadNextStream = it
                                onlineAppPrefs.edit().putBoolean("preload_next_stream", it).apply()
                            },
                            colors = androidx.compose.material3.SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFFFFCDD2),
                                checkedTrackColor = Color(0xFFD32F2F)
                            )
                        )
                    }
                    HorizontalDivider(
                        color = Color.White.copy(alpha = 0.05f),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(iconBg), contentAlignment = Alignment.Center) {
                            Icon(TablerIcons.Video, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Smart Video Pre-loading", color = textColor, fontSize = 15.sp)
                            Text("Pre-buffer video streams in background for instant, zero-lag video playback", color = textColor.copy(alpha = 0.5f), fontSize = 12.sp)
                        }
                        androidx.compose.material3.Switch(
                            checked = videoBackgroundPreload,
                            onCheckedChange = {
                                videoBackgroundPreload = it
                                onlineAppPrefs.edit().putBoolean("video_background_preload", it).apply()
                                PlayerViewModel.activeInstance?.setVideoPreloadEnabled(context, it)
                            },
                            colors = androidx.compose.material3.SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFFFFCDD2),
                                checkedTrackColor = Color(0xFFD32F2F)
                            )
                        )
                    }
                    HorizontalDivider(
                        color = Color.White.copy(alpha = 0.05f),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(iconBg), contentAlignment = Alignment.Center) {
                            Icon(TablerIcons.Ban, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("SponsorBlock Auto-Skip", color = textColor, fontSize = 15.sp)
                            Text("Auto-skip sponsors, intros, outros & non-music video segments", color = textColor.copy(alpha = 0.5f), fontSize = 12.sp)
                        }
                        androidx.compose.material3.Switch(
                            checked = sponsorBlockEnabled,
                            onCheckedChange = {
                                sponsorBlockEnabled = it
                                onlineAppPrefs.edit().putBoolean("sponsorblock_enabled", it).apply()
                            },
                            colors = androidx.compose.material3.SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFFFFCDD2),
                                checkedTrackColor = Color(0xFFD32F2F)
                            )
                        )
                    }
                    if (sponsorBlockEnabled) {
                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.05f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Spacer(modifier = Modifier.width(52.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Show Skip Toast", color = textColor, fontSize = 14.sp)
                                Text("Notify when a SponsorBlock segment is skipped", color = textColor.copy(alpha = 0.5f), fontSize = 11.5.sp)
                            }
                            androidx.compose.material3.Switch(
                                checked = sponsorBlockShowToast,
                                onCheckedChange = {
                                    sponsorBlockShowToast = it
                                    onlineAppPrefs.edit().putBoolean("sponsorblock_show_toast", it).apply()
                                },
                                colors = androidx.compose.material3.SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFFFFCDD2),
                                    checkedTrackColor = Color(0xFFD32F2F)
                                )
                            )
                        }
                        val activeCategoryCount = com.musicplayer.lytrix.service.SponsorBlockManager.parseCategories(sponsorBlockCategoriesRaw).size
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showSponsorBlockCategoriesDialog = true }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Spacer(modifier = Modifier.width(52.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Segment Categories", color = textColor, fontSize = 14.sp)
                                Text("$activeCategoryCount categories selected", color = Color(0xFFFF8A80), fontSize = 12.sp)
                            }
                            Icon(TablerIcons.ChevronRight, contentDescription = null, tint = textColor.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Alerts & Releases",
                    color = headerColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 4.dp)
                )
            }
            item {
                OnlineSettingsCardGroup(cardBg) {
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(iconBg), contentAlignment = Alignment.Center) {
                            Icon(TablerIcons.Bell, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Artist New Release Alerts", color = textColor, fontSize = 15.sp)
                            Text("Notify when subscribed artists drop new tracks or albums", color = textColor.copy(alpha = 0.5f), fontSize = 12.sp)
                        }
                        androidx.compose.material3.Switch(
                            checked = newReleaseAlertsEnabled,
                            onCheckedChange = {
                                newReleaseAlertsEnabled = it
                                com.musicplayer.lytrix.sync.NewReleaseChecker.setEnabled(context, it)
                                if (it) {
                                    scope.launch {
                                        com.musicplayer.lytrix.sync.NewReleaseChecker.checkForNewReleases(context, pushNotification = false)
                                    }
                                }
                            },
                            colors = androidx.compose.material3.SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFFFFCDD2),
                                checkedTrackColor = Color(0xFFD32F2F)
                            )
                        )
                    }
                    HorizontalDivider(
                        color = Color.White.copy(alpha = 0.05f),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    OnlinePremiumSettingsItem(
                        icon = TablerIcons.Music,
                        title = "Artist New Releases Feed",
                        value = "Check",
                        iconBg = iconBg,
                        iconTint = iconTint,
                        textColor = textColor,
                        onClick = { showNewReleasesDialog = true }
                    )
                }
            }

            item {
                Text(
                    text = "Privacy and Security",
                    color = headerColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 4.dp)
                )
            }
            item {
                OnlineSettingsCardGroup(cardBg) {
                    OnlinePremiumSettingsItem(icon = TablerIcons.Shield, title = "Privacy", iconBg = iconBg, iconTint = iconTint, textColor = textColor, onClick = onNavigateToPrivacySettings)
                    HorizontalDivider(
                        color = Color.White.copy(alpha = 0.05f),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(iconBg), contentAlignment = Alignment.Center) {
                            Icon(TablerIcons.Download, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto Export to Storage", color = textColor, fontSize = 15.sp)
                            Text("Save ID3-tagged MP3 to Music/Lytrix automatically", color = textColor.copy(alpha = 0.5f), fontSize = 12.sp)
                        }
                        androidx.compose.material3.Switch(
                            checked = autoExportToStorage,
                            onCheckedChange = {
                                autoExportToStorage = it
                                context.getSharedPreferences("download_settings", Context.MODE_PRIVATE)
                                    .edit().putBoolean("auto_export_to_storage", it).apply()
                            },
                            colors = androidx.compose.material3.SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFFFFCDD2),
                                checkedTrackColor = Color(0xFFD32F2F)
                            )
                        )
                    }
                }
            }
            
            item { Spacer(modifier = Modifier.height(40.dp)) }
        }
    }

    if (showSponsorBlockCategoriesDialog) {
        val selectedSet = com.musicplayer.lytrix.service.SponsorBlockManager.parseCategories(sponsorBlockCategoriesRaw)
        AlertDialog(
            onDismissRequest = { showSponsorBlockCategoriesDialog = false },
            containerColor = Color(0xFF1A181C),
            title = { Text("SponsorBlock Categories", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    com.musicplayer.lytrix.service.SponsorBlockManager.ALL_CATEGORIES.forEach { category ->
                        val checked = category in selectedSet
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val updated = if (checked) selectedSet - category else selectedSet + category
                                    val encoded = updated.joinToString(",")
                                    sponsorBlockCategoriesRaw = encoded
                                    onlineAppPrefs.edit().putString("sponsorblock_categories", encoded).apply()
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = {
                                    val updated = if (checked) selectedSet - category else selectedSet + category
                                    val encoded = updated.joinToString(",")
                                    sponsorBlockCategoriesRaw = encoded
                                    onlineAppPrefs.edit().putString("sponsorblock_categories", encoded).apply()
                                }
                            )
                            Text(
                                text = com.musicplayer.lytrix.service.SponsorBlockManager.categoryDisplayName(category),
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSponsorBlockCategoriesDialog = false }) {
                    Text("Done", color = Color(0xFFFF8A80))
                }
            }
        )
    }

    if (showPremiumDialog) {
        PremiumActivationDialog(
            accentColor = com.musicplayer.lytrix.ui.theme.LocalAccentColor.current,
            onDismiss = { showPremiumDialog = false },
            onSuccess = { showPremiumDialog = false }
        )
    }

    if (showNewReleasesDialog) {
        com.musicplayer.lytrix.ui.components.NewReleasesDialog(
            accentColor = Color(0xFFD32F2F),
            onDismiss = { showNewReleasesDialog = false }
        )
    }

    if (showAdFreeDialog) {
        LytrixAdFreeSheet(
            onDismiss = { showAdFreeDialog = false },
            onNavigateToPremium = {
                showAdFreeDialog = false
                showPremiumDialog = true
            }
        )
    }


}

@Composable
fun OnlineSettingsCardGroup(bgColor: Color, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.elevatedCardElevation(0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
fun OnlinePremiumSettingsItem(
    icon: ImageVector, 
    title: String, 
    value: String? = null,
    iconBg: Color, 
    iconTint: Color, 
    textColor: Color, 
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title, 
            fontSize = 16.sp, 
            fontWeight = FontWeight.SemiBold, 
            color = textColor, 
            maxLines = 1, 
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (!value.isNullOrEmpty()) {
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = iconTint
            )
        }
    }
}

@Composable
fun SpotifySettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    textColor: Color,
    iconBg: Color,
    iconTint: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = textColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                color = textColor.copy(alpha = 0.5f),
                fontSize = 12.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF1DB954)
            )
        )
    }
}
