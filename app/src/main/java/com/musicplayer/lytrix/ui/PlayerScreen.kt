@file:kotlin.OptIn(ExperimentalMaterial3Api::class, androidx.media3.common.util.UnstableApi::class)
package com.musicplayer.lytrix.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.media3.common.Player
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musicplayer.lytrix.ui.component.PlayerCoverWithGestures
import com.musicplayer.lytrix.ui.component.LoopingCoverVideoPlayer
import com.musicplayer.lytrix.spotify.canvas.VisualMediaManager
import com.musicplayer.lytrix.spotify.canvas.VisualMediaResult
import coil.Coil
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import coil.imageLoader
import com.musicplayer.lytrix.model.Song
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.roundToInt
import androidx.annotation.OptIn
import androidx.compose.ui.draw.alpha
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import com.musicplayer.lytrix.R
import compose.icons.TablerIcons
import compose.icons.tablericons.*
import androidx.datastore.preferences.core.edit
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.withStyle

import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.musicplayer.lytrix.ui.VideosScreen

fun formatTime(ms: Long): String {
    val totalSecs = (ms / 1000).coerceAtLeast(0)
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return String.format("%02d:%02d", mins, secs)
}

fun isPremiumVideoThemeActive(themeName: String): Boolean {
    return themeName in listOf("Cyber City", "Campside Rain", "Cat Rain", "Cartoon Walk", "Summer Home", "Summer Village", "Snowy Home")
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun PremiumVideoBackground(theme: String, isDiscoverTab: Boolean, modifier: Modifier = Modifier) {
    if (!isPremiumVideoThemeActive(theme) || isDiscoverTab) return
    val context = androidx.compose.ui.platform.LocalContext.current
    val videoResourceId = when (theme) {
        "Cyber City" -> com.musicplayer.lytrix.R.raw.video_cyber_city
        "Campside Rain" -> com.musicplayer.lytrix.R.raw.video_campside_rain
        "Cat Rain" -> com.musicplayer.lytrix.R.raw.video_cat_rain
        "Cartoon Walk" -> com.musicplayer.lytrix.R.raw.video_cartoon_walk
        "Summer Home" -> com.musicplayer.lytrix.R.raw.video_summer_home
        "Summer Village" -> com.musicplayer.lytrix.R.raw.video_summer_village
        "Snowy Home" -> com.musicplayer.lytrix.R.raw.video_snowy_home
        else -> null
    }

    if (videoResourceId != null) {
        val exoPlayer = remember {
            androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
                repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL
                volume = 0f
            }
        }
        LaunchedEffect(videoResourceId) {
            val uri = androidx.media3.datasource.RawResourceDataSource.buildRawResourceUri(videoResourceId)
            exoPlayer.setMediaItem(androidx.media3.common.MediaItem.fromUri(uri))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }
        DisposableEffect(Unit) {
            onDispose { exoPlayer.release() }
        }
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { ctx ->
                androidx.media3.ui.PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                }
            },
            modifier = modifier.fillMaxSize()
        )
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.50f))
        )
    }
}

@Composable
fun MainPlayerOceanWaveBg(isPlaying: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI.toFloat()),
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    val currentPhase = if (isPlaying) phase else 0f

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        val path = Path().apply {
            moveTo(0f, height)
            for (x in 0..width.toInt() step 5) {
                val y = kotlin.math.sin((x / width * 2 * Math.PI) + currentPhase) * 20f + (height * 0.4f)
                lineTo(x.toFloat(), y.toFloat())
            }
            lineTo(width, height)
            close()
        }
        drawPath(path, Color(0xFF0083B0).copy(alpha = 0.5f))
    }
}

@Composable
fun MainPlayerNeonGradientBg(isPlaying: Boolean) {
    val transition = rememberInfiniteTransition(label = "neon")
    val offset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offset"
    )
    val currentOffset = if (isPlaying) offset else 500f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF240046).copy(alpha = 0.8f),
                        Color(0xFFFF007F).copy(alpha = 0.4f),
                        Color(0xFF00F2FE).copy(alpha = 0.5f)
                    ),
                    start = Offset(0f, currentOffset),
                    end = Offset(currentOffset, 1000f)
                )
            )
    )
}

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(onNavigateToOnlineSettings: () -> Unit = {}, 
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
    onNavigateToSettings: () -> Unit = {},
    onOpenPremiumDialog: () -> Unit = {},
    onNavigateToPlaylist: (String, String) -> Unit = { _, _ -> },
    onNavigateToArtist: (String) -> Unit = {},
    onNavigateToAlbum: (String) -> Unit = {},
    onExpandPlayer: () -> Unit = {},
    onPlayVideo: (com.musicplayer.lytrix.model.Video) -> Unit = {},
    onNavigateToListenTogether: () -> Unit = {},
    onNavigateToFriends: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {}
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { BackupTrigger.start(context) }
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isBuffering by viewModel.isBuffering.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val songsList by viewModel.songsList.collectAsState()
    val filteredSongs by viewModel.filteredSongs.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val shuffleEnabled by viewModel.shuffleModeEnabled.collectAsState()
    val currentLyrics by viewModel.currentLyrics.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    val isServiceConnected by viewModel.isServiceConnected.collectAsState()
    val currentPremiumTheme by viewModel.playerTheme.collectAsState()

    val poppinsFont = remember { FontFamily(Font(R.font.font_poppins)) }
    val quanticoFont = remember { FontFamily(Font(R.font.font_quantico)) }
    val montserratFont = remember { FontFamily(Font(R.font.font_montserrat)) }

    val themeFont = when (currentPremiumTheme) {
        "Cyber City" -> quanticoFont
        "Campside Rain", "Cat Rain", "Snowy Home" -> poppinsFont
        "Cartoon Walk", "Summer Home", "Summer Village" -> montserratFont
        else -> androidx.compose.ui.text.font.FontFamily.Default
    }

    var isNowPlayingExpanded by remember { mutableStateOf(false) }
    var isPlaylistSheetOpen by remember { mutableStateOf(false) }
    var showCreateOfflinePlaylistDialog by remember { mutableStateOf(false) }
    var newOfflinePlaylistName by remember { mutableStateOf("") }
    var showOnlinePlaylistDialogForSong by remember { mutableStateOf<Song?>(null) }
    var newOnlinePlaylistName by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var selectedSongIdsForPlaylist by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showRenameDialog by remember { mutableStateOf<Long?>(null) }
    var renameText by remember { mutableStateOf("") }
    var pendingRenameId by remember { mutableStateOf<Long?>(null) }
    var pendingRenameName by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var showAdFreeSheet by remember { mutableStateOf(false) }

    val tabs = listOf("Discover", "Videos", getString("songs"), getString("playlists"), getString("folders"), getString("artists"), getString("albums"))
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(initialPage = 2, pageCount = { tabs.size })

    val pendingRecognitionLaunch by viewModel.pendingRecognitionLaunch.collectAsState()
    LaunchedEffect(pendingRecognitionLaunch) {
        if (pendingRecognitionLaunch) {
            isNowPlayingExpanded = false
            isSearchExpanded = false
            if (pagerState.currentPage != 0) {
                pagerState.scrollToPage(0)
            }
        }
    }

    val keyboardController = LocalSoftwareKeyboardController.current

    BackHandler(enabled = isNowPlayingExpanded) { isNowPlayingExpanded = false }

    BackHandler(enabled = isSearchExpanded) {
        isSearchExpanded = false
        viewModel.setSearchQuery("")
        keyboardController?.hide()
    }

    var lastBackPressTime by remember { mutableLongStateOf(0L) }
    val activity = context as? android.app.Activity
    BackHandler(enabled = !isNowPlayingExpanded && !isSearchExpanded) {
        if (pagerState.currentPage != 3) {
            scope.launch { pagerState.animateScrollToPage(3) }
        } else {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastBackPressTime < 2000) {
                activity?.finish()
            } else {
                lastBackPressTime = currentTime
                android.widget.Toast.makeText(context, "Press back again to exit", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    val renameLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            pendingRenameId?.let { id -> scope.launch { viewModel.renameSong(id, pendingRenameName) } }
        }
    }

    var showTrashConfirmForCurrentSong by remember { mutableStateOf(false) }
    val playlists by viewModel.playlists.collectAsState()
    val favoriteSongIds by viewModel.favoriteSongIds.collectAsState()
    val recentSongIds by viewModel.recentSongIds.collectAsState()
    val mostPlayedSongIds by viewModel.mostPlayedSongIds.collectAsState()

    val permissionToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var permissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, permissionToRequest) == PackageManager.PERMISSION_GRANTED)
    }

    val launcher = rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestPermission()) { isGranted ->
        permissionGranted = isGranted
        viewModel.loadAndScanSongs(context)
    }

    LaunchedEffect(permissionGranted) {
        if (permissionGranted) { viewModel.loadAndScanSongs(context) }
    }

    val darkBgColor = MaterialTheme.colorScheme.background
    val surfaceColor = MaterialTheme.colorScheme.surface
    val bgUri = com.musicplayer.lytrix.ui.theme.LocalCustomBackgroundUri.current
    val accentColor = com.musicplayer.lytrix.ui.theme.LocalAccentColor.current

    Box(modifier = modifier.fillMaxSize()) {
        val isDiscoverTab = pagerState.currentPage <= 1
        PremiumVideoBackground(theme = currentPremiumTheme, isDiscoverTab = isDiscoverTab)

        if (bgUri.isNotBlank()) {
            AsyncImage(model = bgUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
        } else if (!isPremiumVideoThemeActive(currentPremiumTheme)) {
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(colors = listOf(darkBgColor, darkBgColor.copy(alpha = 0.95f), darkBgColor))
                )
            )
        }

        val isVideoThemeActive = isPremiumVideoThemeActive(currentPremiumTheme) && !isDiscoverTab
        val primaryTextColor = if (isVideoThemeActive) Color.White else MaterialTheme.colorScheme.onBackground

        Box(
            modifier = Modifier.fillMaxSize().background(if (isVideoThemeActive) Color.Black.copy(alpha = 0.05f) else Color.Transparent)
        ) {
            if (!isVideoThemeActive) {
                val currentAccentColor = com.musicplayer.lytrix.ui.theme.LocalAccentColor.current
                com.musicplayer.lytrix.ui.component.AmbientThemeGlow(
                    tint = currentAccentColor,
                    intensity = 0.22f
                )
            }
            androidx.compose.runtime.CompositionLocalProvider(
                LocalContentColor provides primaryTextColor,
                LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = themeFont, color = primaryTextColor)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {

                    AnimatedVisibility(
                        visible = pagerState.currentPage > 1,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
                            val haptic = LocalHapticFeedback.current
                            var showSortBottomSheet by remember { mutableStateOf(false) }
                            val keyboardController = LocalSoftwareKeyboardController.current
                            val focusRequester = remember { FocusRequester() }

                            val isVideoThemeActiveForTabs = isPremiumVideoThemeActive(currentPremiumTheme)
                            val activeTabColor = when(currentPremiumTheme) {
                                "Cyber City" -> Color(0xFF9D4EDD)
                                "Campside Rain" -> Color(0xFF10B981)
                                "Cat Rain" -> Color(0xFF3B82F6)
                                "Cartoon Walk" -> Color(0xFFF59E0B)
                                "Summer Home" -> Color(0xFFEC4899)
                                "Summer Village" -> Color(0xFFFF4500)
                                "Snowy Home" -> Color(0xFF00BFFF)
                                else -> MaterialTheme.colorScheme.primary
                            }

                            Column(modifier = Modifier.fillMaxWidth().animateContentSize()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = if (isSearchExpanded) 0.dp else 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Lytrix Player",
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = poppinsFont,
                                        letterSpacing = (-0.3).sp,
                                        color = if (isVideoThemeActive) Color.White else MaterialTheme.colorScheme.onBackground
                                    )

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        val topBtnTint = if (isVideoThemeActive) Color.White else MaterialTheme.colorScheme.onSurface

                                        val isAdFreeLive by AdManager.isAdFreeFlow.collectAsState()
                                        val isUserVip = remember(isAdFreeLive) {
                                            isUserPremium(context) ||
                                            AdManager.hasPremiumFeatures(context) ||
                                            context.getSharedPreferences("premium_prefs", Context.MODE_PRIVATE).getBoolean("is_permanent", false)
                                        }

                                        if (!isUserVip) {
                                            val currentAdFreeTier = AdManager.getAdFreeTier(context)
                                            val infiniteTransition = rememberInfiniteTransition(label = "AdFreePulse")
                                            val pulseScale by infiniteTransition.animateFloat(
                                                initialValue = 1f,
                                                targetValue = 1.15f,
                                                animationSpec = infiniteRepeatable(
                                                    animation = tween(1100, easing = FastOutSlowInEasing),
                                                    repeatMode = RepeatMode.Reverse
                                                ),
                                                label = "pulse"
                                            )

                                            IconButton(
                                                onClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    showAdFreeSheet = true
                                                },
                                                modifier = Modifier.size(40.dp)
                                            ) {
                                                Box(
                                                    contentAlignment = Alignment.Center,
                                                    modifier = if (currentAdFreeTier == 0) Modifier.scale(pulseScale) else Modifier
                                                ) {
                                                    Icon(
                                                        imageVector = when (currentAdFreeTier) {
                                                            2 -> TablerIcons.Flame
                                                            1 -> TablerIcons.Bolt
                                                            else -> TablerIcons.Gift
                                                        },
                                                        contentDescription = "Ad-Free Pass",
                                                        tint = when (currentAdFreeTier) {
                                                            2 -> Color(0xFFF59E0B)
                                                            1 -> Color(0xFF10B981)
                                                            else -> Color(0xFF818CF8)
                                                        },
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                }
                                            }
                                        }

                                        IconButton(
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                isSearchExpanded = !isSearchExpanded
                                                if (!isSearchExpanded) {
                                                    viewModel.setSearchQuery("")
                                                    keyboardController?.hide()
                                                }
                                            },
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Icon(
                                                if (isSearchExpanded) TablerIcons.X else TablerIcons.Search,
                                                contentDescription = "Search",
                                                tint = if (isSearchExpanded) MaterialTheme.colorScheme.primary else topBtnTint,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                showSortBottomSheet = true
                                            },
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Icon(TablerIcons.ArrowsSort, contentDescription = "Sort Options", tint = topBtnTint, modifier = Modifier.size(22.dp))
                                        }
                                        IconButton(
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                onNavigateToSettings()
                                            },
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Icon(TablerIcons.Settings, contentDescription = "Settings", tint = topBtnTint, modifier = Modifier.size(22.dp))
                                        }
                                    }
                                }

                                AnimatedVisibility(
                                    visible = isSearchExpanded,
                                    enter = expandVertically(animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
                                    exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
                                ) {
                                    OutlinedTextField(
                                        value = searchQuery,
                                        onValueChange = { viewModel.setSearchQuery(it) },
                                        placeholder = {
                                            Text(
                                                "Search local songs...",
                                                color = if (isVideoThemeActive) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 14.sp
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                TablerIcons.Search,
                                                contentDescription = null,
                                                tint = if (isVideoThemeActive) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        },
                                        trailingIcon = {
                                            if (searchQuery.isNotEmpty()) {
                                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                                    Icon(
                                                        TablerIcons.X,
                                                        contentDescription = "Clear",
                                                        tint = if (isVideoThemeActive) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                        keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = if (isVideoThemeActive) Color.White else MaterialTheme.colorScheme.onSurface,
                                            unfocusedTextColor = if (isVideoThemeActive) Color.White else MaterialTheme.colorScheme.onSurface,
                                            cursorColor = if (isVideoThemeActive) activeTabColor else MaterialTheme.colorScheme.primary,
                                            focusedBorderColor = if (isVideoThemeActive) activeTabColor else MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = if (isVideoThemeActive) Color.White.copy(alpha = 0.15f) else Color.Transparent,
                                            focusedContainerColor = if (isVideoThemeActive) Color.Black.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceContainerHighest,
                                            unfocusedContainerColor = if (isVideoThemeActive) Color.Black.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceContainerHigh
                                        ),
                                        shape = RoundedCornerShape(24.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                                            .focusRequester(focusRequester)
                                    )
                                }
                            }

                            LaunchedEffect(isSearchExpanded) {
                                if (isSearchExpanded) focusRequester.requestFocus()
                            }

                            if (showSortBottomSheet) {
                                SortBottomSheet(currentSortOption = viewModel.sortOption.collectAsState().value, onSortSelected = { viewModel.setSortOption(it) }, onDismiss = { showSortBottomSheet = false })
                            }

                            if (showAdFreeSheet) {
                                LytrixAdFreeSheet(
                                    onDismiss = { showAdFreeSheet = false },
                                    onNavigateToPremium = {
                                        showAdFreeSheet = false
                                        onOpenPremiumDialog()
                                    }
                                )
                            }

                            val tabItems = listOf(
                                Triple("Discover", com.musicplayer.lytrix.ui.icons.LytrixIcons.Outlined.Home, com.musicplayer.lytrix.ui.icons.LytrixIcons.Filled.Home),
                                Triple("Videos", com.musicplayer.lytrix.ui.icons.LytrixIcons.Outlined.SmartDisplay, com.musicplayer.lytrix.ui.icons.LytrixIcons.Filled.SmartDisplay),
                                Triple(getString("songs"), com.musicplayer.lytrix.ui.icons.LytrixIcons.Outlined.MusicNote, com.musicplayer.lytrix.ui.icons.LytrixIcons.Filled.MusicNote),
                                Triple(getString("playlists"), com.musicplayer.lytrix.ui.icons.LytrixIcons.Outlined.LibraryMusic, com.musicplayer.lytrix.ui.icons.LytrixIcons.Filled.LibraryMusic),
                                Triple(getString("folders"), Icons.Outlined.Folder, Icons.Filled.Folder),
                                Triple(getString("artists"), com.musicplayer.lytrix.ui.icons.LytrixIcons.Outlined.Person, com.musicplayer.lytrix.ui.icons.LytrixIcons.Filled.Person),
                                Triple(getString("albums"), com.musicplayer.lytrix.ui.icons.LytrixIcons.Outlined.Album, com.musicplayer.lytrix.ui.icons.LytrixIcons.Filled.Album)
                            )

                            val tabListState = rememberLazyListState()

                            LaunchedEffect(pagerState.currentPage) {
                                tabListState.animateScrollToItem(pagerState.currentPage)
                            }

                            androidx.compose.foundation.lazy.LazyRow(
                                state = tabListState,
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                itemsIndexed(tabItems) { index, tabTriple ->
                                    val isSelected = pagerState.currentPage == index
                                    val title = tabTriple.first
                                    val outlinedIcon = tabTriple.second
                                    val filledIcon = tabTriple.third

                                    val animatedBgColor by animateColorAsState(
                                        targetValue = if (isSelected) {
                                            if (isVideoThemeActiveForTabs) activeTabColor else MaterialTheme.colorScheme.primary
                                        } else {
                                            if (isVideoThemeActiveForTabs) Color.Black.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceContainerHigh
                                        },
                                        animationSpec = tween(durationMillis = 250),
                                        label = "tab_bg"
                                    )
                                    val animatedContentColor by animateColorAsState(
                                        targetValue = if (isSelected) {
                                            if (isVideoThemeActiveForTabs) {
                                                if (activeTabColor.luminance() > 0.5f) Color.Black else Color.White
                                            } else {
                                                MaterialTheme.colorScheme.onPrimary
                                            }
                                        } else {
                                            if (isVideoThemeActiveForTabs) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        animationSpec = tween(durationMillis = 250),
                                        label = "tab_content"
                                    )
                                    val animatedBorderColor by animateColorAsState(
                                        targetValue = if (isSelected) {
                                            Color.Transparent
                                        } else {
                                            if (isVideoThemeActiveForTabs) Color.White.copy(alpha = 0.15f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                        },
                                        animationSpec = tween(durationMillis = 250),
                                        label = "tab_border"
                                    )

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(animatedBgColor)
                                            .border(1.dp, animatedBorderColor, RoundedCornerShape(50))
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                scope.launch { pagerState.animateScrollToPage(index) }
                                            }
                                            .padding(horizontal = 16.dp, vertical = 9.dp)
                                    ) {
                                        com.musicplayer.lytrix.ui.icons.MorphingMetroIcon(
                                            selected = isSelected,
                                            outlinedIcon = outlinedIcon,
                                            filledIcon = filledIcon,
                                            contentDescription = title,
                                            tint = animatedContentColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(7.dp))
                                        Text(
                                            text = title,
                                            fontFamily = themeFont,
                                            fontSize = 13.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = animatedContentColor
                                        )
                                    }
                                }
                            }
                        }
                    }

                    val groupedArtists by viewModel.groupedArtists.collectAsState()
                    val groupedAlbums by viewModel.groupedAlbums.collectAsState()
                    val likedVisible by viewModel.likedPlaylistVisible.collectAsState()
                    val recentVisible by viewModel.recentPlaylistVisible.collectAsState()
                    val mostPlayedVisible by viewModel.mostPlayedPlaylistVisible.collectAsState()

                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        androidx.compose.foundation.pager.HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            userScrollEnabled = pagerState.currentPage > 0
                        ) { page ->
                            when (page) {
                                0 -> {
                                    // 🔥 ONLINE HUB (DISCOVER) - Completely Unlocked
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        OnlineMusicScreen(
                                            viewModel = viewModel,
                                            onClose = { scope.launch { pagerState.animateScrollToPage(2) } },
                                            onNavigateToArtist = onNavigateToArtist,
                                            onNavigateToListenTogether = onNavigateToListenTogether,
                                            onNavigateToFriends = onNavigateToFriends,
                                            onNavigateToProfile = onNavigateToProfile,
                                            onNavigateToOnlineSettings = onNavigateToOnlineSettings
                                        )
                                    }
                                }
                                1 -> VideosScreen(
                                    onNavigateToSettings = onNavigateToSettings, searchQuery = searchQuery, playerViewModel = viewModel, sortOption = sortOption,
                                    onPlayVideo = { video -> if (isPlaying) { viewModel.playPause() }; onPlayVideo(video) },
                                    currentTheme = currentPremiumTheme, themeFont = themeFont
                                )
                                2 -> SongsTabContent(
                                    filteredSongs = filteredSongs, currentSong = currentSong, isPlaying = isPlaying, accentColor = accentColor, isLoading = isLoading,
                                    onSongClick = { viewModel.playSong(it) },
                                    onRenameClick = { id -> showRenameDialog = id; val song = filteredSongs.find { it.id == id }; renameText = song?.title ?: "" },
                                    onHideClick = { ids -> viewModel.hideSongs(ids) }, onDeleteClick = { ids -> viewModel.deleteSongs(ids) },
                                    onAddToPlaylistClick = { ids -> selectedSongIdsForPlaylist = ids; isPlaylistSheetOpen = true },
                                    onRefresh = { onComplete ->
                                        viewModel.refreshLocalSongs(context) {
                                            onComplete()
                                        }
                                    },
                                    currentTheme = currentPremiumTheme, themeFont = themeFont
                                )
                                3 -> {
                                    val songIdSet = remember(songsList) { songsList.map { it.id }.toHashSet() }
                                    val actualFavCount = remember(favoriteSongIds, songIdSet) { favoriteSongIds.distinct().count { songIdSet.contains(it) } }
                                    val actualRecentCount = remember(recentSongIds, songIdSet) { recentSongIds.distinct().count { songIdSet.contains(it) } }
                                    val actualMostPlayedCount = remember(mostPlayedSongIds, songIdSet) { mostPlayedSongIds.distinct().count { songIdSet.contains(it) } }

                                    PlaylistsTabContent(
                                        favoriteCount = actualFavCount,
                                        recentCount = actualRecentCount,
                                        mostPlayedCount = actualMostPlayedCount,
                                        playlists = playlists,
                                        accentColor = accentColor,
                                        likedVisible = likedVisible,
                                        recentVisible = recentVisible,
                                        mostPlayedVisible = mostPlayedVisible,
                                        onLikedSongsClick = { onNavigateToPlaylist("liked", "-1") },
                                        onRecentSongsClick = { onNavigateToPlaylist("recent", "-1") },
                                        onMostPlayedClick = { onNavigateToPlaylist("most_played", "-1") },
                                        onCustomPlaylistClick = { id -> onNavigateToPlaylist("custom", id.toString()) },
                                        onDeleteCustomPlaylist = { id -> viewModel.deletePlaylist(id) },
                                        onAddNewClick = { showCreateOfflinePlaylistDialog = true }
                                    )
                                }
                                4 -> FoldersTabContent(
                                    folders = viewModel.groupedFolders.collectAsState().value,
                                    currentSong = currentSong,
                                    isPlaying = isPlaying,
                                    accentColor = accentColor,
                                    currentTheme = currentPremiumTheme,
                                    themeFont = themeFont,
                                    onSongClick = { song, list -> viewModel.playSong(song, list) }
                                )
                                5 -> ArtistsTabContent(
                                    artists = groupedArtists, onArtistClick = { onNavigateToPlaylist("artist", it) }, currentTheme = currentPremiumTheme, themeFont = themeFont
                                )
                                6 -> AlbumsTabContent(
                                    albums = groupedAlbums, onAlbumClick = { onNavigateToPlaylist("album", it) }, currentTheme = currentPremiumTheme, themeFont = themeFont
                                )
                                else -> { EmptyStateView() }
                            }
                        }
                    }
                }

                if (isPlaylistSheetOpen) {
                    val currentVideoQuality by viewModel.videoQualityPref.collectAsState()

                    PlayerBottomSheetController(
                        viewModel = viewModel,
                        sheetType = BottomSheetType.PLAYLIST,
                        onDismiss = { isPlaylistSheetOpen = false; selectedSongIdsForPlaylist = emptySet() },
                        onNavigate = { },
                        playlists = playlists,
                        onAddToPlaylist = { playlistId ->
                            if (selectedSongIdsForPlaylist.isNotEmpty()) {
                                viewModel.addMultipleToPlaylist(playlistId, selectedSongIdsForPlaylist)
                            } else {
                                val cur = currentSong
                                if (cur != null) {
                                    val isOnline = cur.filePath == "ONLINE_LYTRIX" || cur.uriString.startsWith("http")
                                    if (!isOnline) {
                                        viewModel.addSongToPlaylist(playlistId, cur.id)
                                    } else {
                                        Toast.makeText(context, "Cannot add online song to offline playlist", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            CoroutineScope(Dispatchers.IO).launch { delay(2000); performSilentSync(context) }
                            isPlaylistSheetOpen = false
                            selectedSongIdsForPlaylist = emptySet()
                        },
                        onCreatePlaylist = { playlistName ->
                            viewModel.createPlaylist(playlistName)
                            CoroutineScope(Dispatchers.IO).launch {
                                kotlinx.coroutines.delay(2000)
                                performSilentSync(context)
                            }
                        },
                        onSetSleepTimer = { _, _ -> },
                        onSetSpeed = { },
                        onHideSong = { },
                        accentColor = accentColor,
                        song = currentSong,
                        currentVideoQuality = currentVideoQuality,
                        onNavigateToArtist = { artistName ->
                            isPlaylistSheetOpen = false
                            isNowPlayingExpanded = false
                            val isAb = currentSong?.mediaType == "AUDIOBOOK" || currentSong?.filePath in listOf("ONLINE_AUDIOBOOK", "OFFLINE_AUDIOBOOK")
                            val isPod = currentSong?.mediaType == "PODCAST" || currentSong?.filePath in listOf("ONLINE_PODCAST", "OFFLINE_PODCAST")
                            val target = when {
                                isAb -> "AUDIOBOOK_${currentSong?.artist ?: artistName}"
                                isPod && (currentSong?.channelId.isNullOrBlank() || !currentSong?.channelId!!.startsWith("UC")) -> "PODCAST_${currentSong?.album.takeIf { !it.isNullOrBlank() } ?: artistName}"
                                !currentSong?.channelId.isNullOrBlank() -> currentSong!!.channelId!!
                                else -> artistName
                            }
                            onNavigateToArtist(target)
                        },
                        onViewAlbum = {
                            isPlaylistSheetOpen = false
                            isNowPlayingExpanded = false
                            currentSong?.let { viewModel.openAlbumForSong(context, it) }
                        }
                    )
                }
                if (showOnlinePlaylistDialogForSong != null) {
                    AlertDialog(
                        onDismissRequest = { showOnlinePlaylistDialogForSong = null },
                        containerColor = surfaceColor,
                        title = { Text("Add to Cloud Playlist", color = MaterialTheme.colorScheme.onBackground) },
                        text = {
                            Column {
                                val plPrefs = context.getSharedPreferences("online_playlists_meta", Context.MODE_PRIVATE)
                                val onlinePlaylists = plPrefs.getStringSet("names", emptySet())?.toList() ?: emptyList()
                                if (onlinePlaylists.isNotEmpty()) {
                                    Text("Existing Playlists:", color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp)
                                    LazyColumn(modifier = Modifier.heightIn(max = 150.dp).padding(vertical = 8.dp)) {
                                        items(onlinePlaylists) { plName ->
                                            Text(
                                                text = plName, color = MaterialTheme.colorScheme.onBackground,
                                                modifier = Modifier.fillMaxWidth().clickable {
                                                    val songPrefs = context.getSharedPreferences("online_pl_$plName", Context.MODE_PRIVATE)
                                                    val song = showOnlinePlaylistDialogForSong!!
                                                    val songIdStr = song.originalId ?: song.id.toString()

                                                    val jsonObj = JSONObject()
                                                    jsonObj.put("id", songIdStr); jsonObj.put("title", song.title); jsonObj.put("name", song.title)
                                                    jsonObj.put("subtitle", song.artist); jsonObj.put("artist", song.artist)
                                                    jsonObj.put("image", song.albumArtUriString ?: ""); jsonObj.put("imageUrl", song.albumArtUriString ?: ""); jsonObj.put("image_url", song.albumArtUriString ?: "")
                                                    jsonObj.put("url", song.uriString); jsonObj.put("downloadUrl", song.uriString); jsonObj.put("stream_url", song.uriString)

                                                    songPrefs.edit().putString(songIdStr, jsonObj.toString()).apply()
                                                    Toast.makeText(context, "Added to $plName", Toast.LENGTH_SHORT).show()
                                                    CoroutineScope(Dispatchers.IO).launch { delay(2000); performSilentSync(context) }
                                                    showOnlinePlaylistDialogForSong = null
                                                }.padding(8.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = newOnlinePlaylistName, onValueChange = { newOnlinePlaylistName = it },
                                    placeholder = { Text("Or create new playlist...", color = MaterialTheme.colorScheme.onBackground.copy(alpha=0.5f)) },
                                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onBackground, unfocusedTextColor = MaterialTheme.colorScheme.onBackground)
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                if (newOnlinePlaylistName.isNotBlank()) {
                                    val plPrefs = context.getSharedPreferences("online_playlists_meta", Context.MODE_PRIVATE)
                                    val existing = plPrefs.getStringSet("names", emptySet())?.toMutableSet() ?: mutableSetOf()
                                    existing.add(newOnlinePlaylistName)
                                    plPrefs.edit().putStringSet("names", existing).apply()

                                    val songPrefs = context.getSharedPreferences("online_pl_$newOnlinePlaylistName", Context.MODE_PRIVATE)
                                    val song = showOnlinePlaylistDialogForSong!!
                                    val songIdStr = song.originalId ?: song.id.toString()

                                    val jsonObj = JSONObject()
                                    jsonObj.put("id", songIdStr); jsonObj.put("title", song.title); jsonObj.put("name", song.title)
                                    jsonObj.put("subtitle", song.artist); jsonObj.put("artist", song.artist)
                                    jsonObj.put("image", song.albumArtUriString ?: ""); jsonObj.put("imageUrl", song.albumArtUriString ?: ""); jsonObj.put("image_url", song.albumArtUriString ?: "")
                                    jsonObj.put("url", song.uriString); jsonObj.put("downloadUrl", song.uriString); jsonObj.put("stream_url", song.uriString)

                                    songPrefs.edit().putString(songIdStr, jsonObj.toString()).apply()
                                    Toast.makeText(context, "Created & Added", Toast.LENGTH_SHORT).show()
                                    CoroutineScope(Dispatchers.IO).launch { delay(2000); performSilentSync(context) }
                                    newOnlinePlaylistName = ""
                                    showOnlinePlaylistDialogForSong = null
                                }
                            }) { Text("Create & Add", color = accentColor) }
                        },
                        dismissButton = { TextButton(onClick = { showOnlinePlaylistDialogForSong = null }) { Text("Cancel", color = MaterialTheme.colorScheme.onBackground) } }
                    )
                }
                if (showCreateOfflinePlaylistDialog) {
                    AlertDialog(
                        onDismissRequest = { showCreateOfflinePlaylistDialog = false; newOfflinePlaylistName = "" },
                        title = { Text(getString("new_playlist"), fontWeight = FontWeight.Bold, fontFamily = themeFont) },
                        text = {
                            OutlinedTextField(
                                value = newOfflinePlaylistName,
                                onValueChange = { newOfflinePlaylistName = it },
                                placeholder = { Text(getString("new_playlist"), fontFamily = themeFont) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accentColor, focusedLabelColor = accentColor, cursorColor = accentColor)
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    if (newOfflinePlaylistName.isNotBlank()) {
                                        viewModel.createPlaylist(newOfflinePlaylistName.trim())
                                        CoroutineScope(Dispatchers.IO).launch {
                                            delay(2000)
                                            performSilentSync(context)
                                        }
                                    }
                                    showCreateOfflinePlaylistDialog = false
                                    newOfflinePlaylistName = ""
                                }
                            ) {
                                Text(getString("create"), fontWeight = FontWeight.Bold, color = accentColor, fontFamily = themeFont)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showCreateOfflinePlaylistDialog = false; newOfflinePlaylistName = "" }) {
                                Text(getString("cancel"), fontFamily = themeFont)
                            }
                        }
                    )
                }
                if (showRenameDialog != null) {
                    val s = filteredSongs.find { it.id == showRenameDialog }
                    if (s != null) {
                        AlertDialog(
                            onDismissRequest = { showRenameDialog = null },
                            title = { Text("Audio Info & Rename") },
                            text = {
                                Column {
                                    OutlinedTextField(
                                        value = renameText, onValueChange = { renameText = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accentColor, focusedLabelColor = accentColor, cursorColor = accentColor)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    val totalSecs = (s.duration / 1000).coerceAtLeast(0)
                                    val m = totalSecs / 60
                                    val sec = totalSecs % 60
                                    val durText = String.format("%02d:%02d", m, sec)
                                    val fileRoot = java.io.File(s.filePath ?: "")
                                    val sizeInMb = if (fileRoot.exists()) fileRoot.length() / (1024f * 1024f) else 0f
                                    val sizeText = String.format("%.2f MB", sizeInMb)
                                    Text("Duration: $durText", style = MaterialTheme.typography.bodySmall)
                                    Text("Size: $sizeText", style = MaterialTheme.typography.bodySmall)
                                    Text("Path:\n${s.filePath}", style = MaterialTheme.typography.bodySmall)
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    if (renameText.isNotBlank()) {
                                        val newName = renameText.trim()
                                        pendingRenameId = s.id
                                        pendingRenameName = newName
                                        scope.launch {
                                            val intentSender = viewModel.renameSong(s.id, newName)
                                            if (intentSender != null) {
                                                val request = IntentSenderRequest.Builder(intentSender).build()
                                                renameLauncher.launch(request)
                                            }
                                        }
                                    }
                                    showRenameDialog = null
                                }) { Text("Save", color = accentColor) }
                            },
                            dismissButton = { TextButton(onClick = { showRenameDialog = null }) { Text("Cancel", color = MaterialTheme.colorScheme.onSurface) } }
                        )
                    }
                }

                if (showTrashConfirmForCurrentSong && currentSong != null) {
                    val uri = android.net.Uri.parse(currentSong!!.uriString)
                    PremiumTrashConfirmDialog(
                        uris = listOf(uri),
                        onConfirm = { val id = currentSong!!.id; viewModel.skipNext(); viewModel.deleteSongs(setOf(id)); showTrashConfirmForCurrentSong = false },
                        onDismiss = { showTrashConfirmForCurrentSong = false }
                    )
                }
            }
        }
    }
}

@Composable
fun NowPlayingFullScreen(
    viewModel: PlayerViewModel,
    song: Song,
    isPlaying: Boolean,
    isBuffering: Boolean,
    currentPosition: Long,
    duration: Long,
    repeatMode: Int,
    shuffleEnabled: Boolean,
    accentColor: Color,
    currentLyrics: String?,
    isFavorite: Boolean,
    onClose: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenMenu: () -> Unit,
    onNavigateToArtist: (String) -> Unit,
    onPlayVideo: (com.musicplayer.lytrix.model.Video) -> Unit = {}
) {
    val context = LocalContext.current
    val isVideoModeActive by viewModel.isVideoModeActive.collectAsState()

    val myRoomStatus by viewModel.myRoomStatus.collectAsState()
    val metroRoomRole by viewModel.metroManager.roomRole.collectAsState()
    val isGuest = myRoomStatus == "approved" || myRoomStatus == "pending" || metroRoomRole == com.musicplayer.lytrix.listentogether.metro.MetroRoomRole.GUEST
    val dataStoreManager = remember { DataStoreManager(context) }
    val playerGesturesEnabled by dataStoreManager.playerGesturesEnabledFlow.collectAsState(initial = true)

    val currentQueue by viewModel.currentQueueList.collectAsState()
    val currentSongIndex = remember(currentQueue, song.id) { currentQueue.indexOfFirst { it.id == song.id } }
    val previousSong = remember(currentQueue, currentSongIndex, repeatMode) {
        if (currentSongIndex > 0) {
            currentQueue[currentSongIndex - 1]
        } else if (currentSongIndex == 0 && repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF && currentQueue.size > 1) {
            currentQueue.lastOrNull()
        } else null
    }
    val nextSong = remember(currentQueue, currentSongIndex, repeatMode) {
        if (currentSongIndex in 0 until currentQueue.lastIndex) {
            currentQueue[currentSongIndex + 1]
        } else if (currentSongIndex == currentQueue.lastIndex && repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF && currentQueue.size > 1) {
            currentQueue.firstOrNull()
        } else null
    }

    val artworkPrefs = remember { com.musicplayer.lytrix.artwork.ArtworkPreferencesManager.getInstance(context) }
    val isCanvasEnabled by artworkPrefs.canvasEnabled.collectAsState()
    val isAppleMotionEnabled by artworkPrefs.animatedCoversEnabled.collectAsState()
    val contentOrder by artworkPrefs.canvasContentOrder.collectAsState()
    val enabledSources by artworkPrefs.canvasEnabledSources.collectAsState()

    var visualMedia by remember(song.id) { mutableStateOf<VisualMediaResult?>(null) }
    LaunchedEffect(song.id, isAppleMotionEnabled, isCanvasEnabled, contentOrder, enabledSources) {
        visualMedia = null
        com.musicplayer.lytrix.spotify.canvas.CanvasVideoPlayerManager.releaseIfDifferentSong(song.id)
        if (!isAppleMotionEnabled && !isCanvasEnabled) {
            return@LaunchedEffect
        }
        visualMedia = VisualMediaManager.getVisualMedia(context, song)
    }

    val sharedPrefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
    var nowPlayingTheme by remember { mutableStateOf(sharedPrefs.getString("main_player_theme", "Solid Default") ?: "Solid Default") }

    DisposableEffect(sharedPrefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == "main_player_theme") {
                nowPlayingTheme = prefs.getString(key, "Solid Default") ?: "Solid Default"
            }
        }
        sharedPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { sharedPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val scrollableIntensity = accentColor.copy(alpha = 0.12f)
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    val albumArtPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.saveCustomAlbumArt(context, song.id, uri)
        }
    }

    var paletteRgb by remember { mutableStateOf<Int?>(null) }
    val coroutineScope = rememberCoroutineScope()

    var showArtOptionsDialog by remember { mutableStateOf(false) }
    val isLocalSong = remember(song.filePath, song.uriString) {
        song.filePath !in listOf("ONLINE_LYTRIX", "ONLINE") && !song.uriString.startsWith("http")
    }

    val artDataForBlur = rememberEmbeddedArt(context, song)
    LaunchedEffect(song.albumArtUriString, artDataForBlur) {
        withContext(Dispatchers.IO) {
            try {
                val dataToLoad = artDataForBlur ?: song.uriString
                val reqBuilder = ImageRequest.Builder(context)
                    .data(dataToLoad)
                    .allowHardware(false)
                if ((artDataForBlur == null || dataToLoad is String) && song.isVideo && dataToLoad == song.uriString) {
                    reqBuilder.videoFrameMillis(1000)
                }
                val result = Coil.imageLoader(context).execute(reqBuilder.build())
                val bitmap = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                if (bitmap != null) {
                    androidx.palette.graphics.Palette.from(bitmap).generate().dominantSwatch?.rgb?.let {
                        paletteRgb = it
                    }
                }
            } catch (e: Exception) {}
        }
    }

    val baseBackdrop = MaterialTheme.colorScheme.background
    val baseOnBackdrop = MaterialTheme.colorScheme.onBackground

    val backdropColor = animateColorAsState(
        targetValue = when (nowPlayingTheme) {
            "Dynamic Blur", "Vinyl Record" -> if (paletteRgb != null) Color(paletteRgb!!) else baseBackdrop
            else -> baseBackdrop
        }
    ).value

    val onBackdropColor = when (nowPlayingTheme) {
        "Dynamic Blur", "Vinyl Record" -> if (paletteRgb != null) {
            if (Color(paletteRgb!!).luminance() > 0.5f) Color.Black else Color.White
        } else baseOnBackdrop
        "Glassmorphism", "Neon Gradient", "Cartoon" -> Color.White
        else -> baseOnBackdrop
    }

    var isLyricsExpanded by remember { mutableStateOf(false) }
    var showEqualizer by remember { mutableStateOf(false) }
    var showThemeSheet by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var dragAmountAccumulator by remember { mutableFloatStateOf(0f) }

    var draggingPosition by remember { mutableStateOf<Long?>(null) }
    val positionToShow = draggingPosition ?: currentPosition

    var isPressingRewind by remember { mutableStateOf(false) }
    var isPressingForward by remember { mutableStateOf(false) }

    LaunchedEffect(isPressingForward) {
        if (isPressingForward) {
            var tempPos = currentPosition
            while (true) {
                delay(50)
                tempPos = minOf(duration, tempPos + 1000)
                draggingPosition = tempPos
            }
        } else if (draggingPosition != null && !isPressingRewind) {
            onSeek(draggingPosition!!)
            draggingPosition = null
        }
    }

    LaunchedEffect(isPressingRewind) {
        if (isPressingRewind) {
            var tempPos = currentPosition
            while (true) {
                delay(50)
                tempPos = maxOf(0, tempPos - 1000)
                draggingPosition = tempPos
            }
        } else if (draggingPosition != null && !isPressingForward) {
            onSeek(draggingPosition!!)
            draggingPosition = null
        }
    }

    val isAppOnlineSong = song.filePath in listOf("ONLINE", "ONLINE_LYTRIX", "OFFLINE", "ONLINE_PODCAST", "OFFLINE_PODCAST")
    val onlineLikedPrefs = context.getSharedPreferences("online_liked_songs", Context.MODE_PRIVATE)

    var isOnlineLiked by remember(song.id) {
        val checkId = song.originalId ?: song.id.toString()
        mutableStateOf(onlineLikedPrefs.getStringSet("liked_ids", emptySet())?.contains(checkId) == true)
    }

    DisposableEffect(song.id) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == "liked_ids") {
                val checkId = song.originalId ?: song.id.toString()
                isOnlineLiked = prefs.getStringSet("liked_ids", emptySet())?.contains(checkId) == true
            }
        }
        onlineLikedPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { onlineLikedPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val displayIsFavorite = if (isAppOnlineSong) isOnlineLiked else isFavorite

    BackHandler(enabled = true) {
        if (isLyricsExpanded) {
            isLyricsExpanded = false
        } else if (showQueue) {
            showQueue = false
        } else if (showEqualizer) {
            showEqualizer = false
        } else if (isVideoModeActive) {
            viewModel.switchToAudioMode(song)
        } else {
            onClose()
        }
    }

    val mainActivity = context as? com.musicplayer.lytrix.MainActivity
    DisposableEffect(isLyricsExpanded, showQueue, showEqualizer, isVideoModeActive) {
        if (mainActivity != null) {
            mainActivity.nowPlayingBackHandler = {
                if (isLyricsExpanded) {
                    isLyricsExpanded = false
                    true
                } else if (showQueue) {
                    showQueue = false
                    true
                } else if (showEqualizer) {
                    showEqualizer = false
                    true
                } else if (isVideoModeActive) {
                    viewModel.switchToAudioMode(song)
                    true
                } else {
                    false
                }
            }
        }
        onDispose {
            if (mainActivity != null) {
                mainActivity.nowPlayingBackHandler = null
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (nowPlayingTheme == "Neon Gradient" || nowPlayingTheme == "Cartoon") Color.Black else backdropColor)
            .pointerInput(isLyricsExpanded) {
                if (!isLyricsExpanded) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (dragAmountAccumulator > 200) {
                                onClose()
                            } else if (dragAmountAccumulator < -200) {
                                showQueue = true
                            }
                            dragAmountAccumulator = 0f
                        },
                        onVerticalDrag = { change, dragAmount ->
                            dragAmountAccumulator += dragAmount
                        }
                    )
                }
            }
    ) {
        when (nowPlayingTheme) {
            "Glassmorphism" -> {
                val artData = rememberEmbeddedArt(context, song)
                val imageRequest = remember(artData, song.uriString, song.albumArtUriString) {
                    val dataToLoad = artData ?: song.uriString
                    val reqBuilder = ImageRequest.Builder(context).data(dataToLoad).crossfade(true)
                    if ((artData == null || dataToLoad is String) && song.isVideo && dataToLoad == song.uriString) {
                        reqBuilder.videoFrameMillis(1000)
                    }
                    reqBuilder.build()
                }
                Image(
                    painter = rememberAsyncImagePainter(model = imageRequest),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().blur(50.dp)
                )
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)))
            }
            "Vinyl Record" -> {
                val artData = rememberEmbeddedArt(context, song)
                val imageRequest = remember(artData, song.uriString, song.albumArtUriString) {
                    val dataToLoad = artData ?: song.uriString
                    val reqBuilder = ImageRequest.Builder(context).data(dataToLoad).crossfade(true)
                    if ((artData == null || dataToLoad is String) && song.isVideo && dataToLoad == song.uriString) {
                        reqBuilder.videoFrameMillis(1000)
                    }
                    reqBuilder.build()
                }
                Image(
                    painter = rememberAsyncImagePainter(model = imageRequest),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().blur(40.dp).graphicsLayer { alpha = 0.8f }
                )
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
            }
            "Neon Gradient" -> { MainPlayerNeonGradientBg(isPlaying = isPlaying) }
            "Cartoon" -> {
                val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.theme_cartoon))
                val animProgress by animateLottieCompositionAsState(composition, iterations = LottieConstants.IterateForever, isPlaying = isPlaying)
                LottieAnimation(composition = composition, progress = { animProgress }, modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.6f }, contentScale = ContentScale.Crop)
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val safeRadius = size.width * 1.2f
                    val r = if (safeRadius.isNaN() || safeRadius.isInfinite() || safeRadius <= 0f) 1f else safeRadius
                    drawCircle(brush = Brush.radialGradient(colors = listOf(scrollableIntensity, Color.Transparent), center = center.copy(y = center.y * 0.75f), radius = r))
                }
        )

        // 🔥 Landscape Immersive Mode - Status Bar Hide
        val activityContext = context as? android.app.Activity
        DisposableEffect(isLandscape, isVideoModeActive) {
            val window = activityContext?.window
            if (window != null) {
                val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                if (isLandscape && isVideoModeActive) {
                    insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                    insetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                } else {
                    insetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                }
            }
            onDispose {
                activityContext?.window?.let { win ->
                    androidx.core.view.WindowCompat.getInsetsController(win, win.decorView).show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                }
            }
        }

        if (isLandscape && isVideoModeActive) {
            // 🔥 Landscape Video Player
            val isVideoPlayerReady by viewModel.isVideoPlayerReady.collectAsState()
            val exoPlayer = viewModel.getVideoPlayer()
            var showControls by remember { mutableStateOf(true) }

            LaunchedEffect(showControls, isPlaying) {
                if (showControls && isPlaying) {
                    delay(3500)
                    showControls = false
                }
            }

            PlayerCoverWithGestures(
                gesturesEnabled = playerGesturesEnabled,
                isGuest = isGuest,
                isPlaying = isPlaying,
                currentPosition = currentPosition,
                duration = duration,
                onSeek = { seekPos ->
                    onSeek(seekPos)
                    exoPlayer?.seekTo(seekPos)
                    viewModel.forceSyncToFirebase()
                },
                onPlayPause = {
                    onPlayPause()
                    viewModel.forceSyncToFirebase()
                },
                onTap = { showControls = !showControls },
                swipeEnabled = false,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                if (!isVideoPlayerReady || exoPlayer == null) {
                    LytrixExpressiveSpinner(color = accentColor, size = 42.dp)
                } else {
                    androidx.compose.ui.viewinterop.AndroidView(
                        factory = { ctx -> androidx.media3.ui.PlayerView(ctx).apply { useController = false; controllerAutoShow = false; resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT; setBackgroundColor(android.graphics.Color.TRANSPARENT); hideController() } },
                        update = { playerView -> if (playerView.player != exoPlayer) { playerView.player = exoPlayer } },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                AnimatedVisibility(
                    visible = showControls, enter = fadeIn() + slideInVertically(initialOffsetY = { -50 }), exit = fadeOut() + slideOutVertically(targetOffsetY = { -50 }),
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp).statusBarsPadding()
                ) {
                    Row(
                        modifier = Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.fillMaxHeight().clip(RoundedCornerShape(14.dp)).clickable { viewModel.switchToAudioMode(song) }.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) { Text("Song", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        Box(modifier = Modifier.fillMaxHeight().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.2f)).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) { Text("Video", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    }
                }

                AnimatedVisibility(
                    visible = showControls, enter = fadeIn() + slideInVertically(initialOffsetY = { 50 }), exit = fadeOut() + slideOutVertically(targetOffsetY = { 50 }),
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f)))).navigationBarsPadding().padding(horizontal = 32.dp, vertical = 16.dp)
                ) {
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(String.format("%02d:%02d", (positionToShow / 1000) / 60, (positionToShow / 1000) % 60), fontSize = 12.sp, color = Color.White)
                            Slider(
                                value = (if (duration > 0) positionToShow.toFloat() / duration else 0f).coerceIn(0f, 1f),
                                onValueChange = { if (!isGuest) draggingPosition = (it * duration).toLong() },
                                onValueChangeFinished = { if (!isGuest) draggingPosition?.let { onSeek(it); viewModel.forceSyncToFirebase(); draggingPosition = null } },
                                colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor, inactiveTrackColor = Color.White.copy(alpha = 0.3f)),
                                modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
                            )
                            Text(String.format("%02d:%02d", (duration / 1000) / 60, (duration / 1000) % 60), fontSize = 12.sp, color = Color.White)
                        }

                        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(48.dp).pointerInput(Unit) { detectTapGestures(onPress = { if (!isGuest) { val job = coroutineScope.launch { delay(400); isPressingRewind = true }; tryAwaitRelease(); job.cancel(); if (!isPressingRewind) { onSkipPrevious(); viewModel.forceSyncToFirebase() }; isPressingRewind = false } }) }, contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.SkipPrevious, contentDescription = "Skip Previous", tint = Color.White, modifier = Modifier.size(34.dp))
                            }
                            Spacer(modifier = Modifier.width(32.dp))
                            Box(modifier = Modifier.size(64.dp).clip(CircleShape).background(accentColor).clickable { if (!isGuest) { onPlayPause(); viewModel.forceSyncToFirebase() } }, contentAlignment = Alignment.Center) {
                                if (isBuffering) LytrixExpressiveSpinner(color = Color.Black, strokeWidth = 3.dp, size = 28.dp)
                                else Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = "Play/Pause", tint = Color.Black, modifier = Modifier.size(36.dp))
                            }
                            Spacer(modifier = Modifier.width(32.dp))
                            Box(modifier = Modifier.size(48.dp).pointerInput(Unit) { detectTapGestures(onPress = { if (!isGuest) { val job = coroutineScope.launch { delay(400); isPressingForward = true }; tryAwaitRelease(); job.cancel(); if (!isPressingForward) { onSkipNext(); viewModel.forceSyncToFirebase() }; isPressingForward = false } }) }, contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.SkipNext, contentDescription = "Skip Next", tint = Color.White, modifier = Modifier.size(34.dp))
                            }
                        }
                    }
                }
            }
        } else {
            // 🔥 Normal UI (Portrait or Non-Video Landscape)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 28.dp)
                    .padding(top = 16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose, modifier = Modifier.testTag("now_playing_close")) {
                        Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = "Collapse", tint = onBackdropColor, modifier = Modifier.size(28.dp))
                    }

                    val isYtSong = com.musicplayer.lytrix.ui.player.isYouTubeSong(song)

                    if (song.isVideo) {
                        // 🔥 লোকাল গানে কোনো অ্যাড নেই। সরাসরি ভিডিও প্লেয়ার ওপেন হবে।
                        TextButton(onClick = {
                            onClose(); if (isPlaying) onPlayPause()
                            val v = com.musicplayer.lytrix.model.Video(id = -song.id, title = song.title, durationText = "", sizeText = "", uriString = song.uriString, path = song.filePath ?: "", dateModified = 0L)
                            onPlayVideo(v)
                        }) {
                            Icon(Icons.Default.Videocam, contentDescription = null, tint = accentColor)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("BACK TO VIDEO", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = accentColor)
                        }
                    } else if (isYtSong) {
                        // 🔥 ইউটিউব গানের ভিডিও বাটন
                        Row(
                            modifier = Modifier
                                .height(38.dp)
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(50))
                                .padding(3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val songBg by animateColorAsState(if (!isVideoModeActive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, label = "npSongBg")
                            val songContent by animateColorAsState(if (!isVideoModeActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, label = "npSongText")
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(50))
                                    .background(songBg)
                                    .clickable { viewModel.switchToAudioMode(song) }
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Song", color = songContent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            val videoBg by animateColorAsState(if (isVideoModeActive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, label = "npVideoBg")
                            val videoContent by animateColorAsState(if (isVideoModeActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, label = "npVideoText")
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(50))
                                    .background(videoBg)
                                    .clickable {
                                        if (!isVideoModeActive) {
                                            val activity = context as? android.app.Activity
                                            if (activity != null) {
                                                AdManager.showAdForVideo(activity, song.id.toString()) {
                                                    viewModel.switchToVideoMode(song)
                                                }
                                            } else {
                                                viewModel.switchToVideoMode(song)
                                            }
                                        }
                                    }
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Video", color = videoContent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Text("NOW PLAYING", fontWeight = FontWeight.Medium, letterSpacing = 2.sp, fontSize = 11.sp, color = onBackdropColor.copy(alpha = 0.5f))
                    }
                    IconButton(onClick = onOpenMenu) { Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Options", tint = onBackdropColor.copy(alpha = 0.5f)) }
                }

                if (isLandscape) {
                    Row(modifier = Modifier.weight(1f).fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.weight(0.45f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            PlayerCoverWithGestures(
                                gesturesEnabled = playerGesturesEnabled,
                                isGuest = isGuest,
                                isPlaying = isPlaying,
                                currentPosition = currentPosition,
                                duration = duration,
                                onSeek = { seekPos ->
                                    onSeek(seekPos)
                                    viewModel.forceSyncToFirebase()
                                },
                                onPlayPause = {
                                    onPlayPause()
                                    viewModel.forceSyncToFirebase()
                                },
                                onSkipNext = {
                                    onSkipNext()
                                    viewModel.forceSyncToFirebase()
                                },
                                onSkipPrevious = {
                                    onSkipPrevious()
                                    viewModel.forceSyncToFirebase()
                                },
                                onLongPress = { if (isLocalSong) showArtOptionsDialog = true },
                                modifier = Modifier
                                    .aspectRatio(1f, matchHeightConstraintsFirst = false)
                                    .clip(RoundedCornerShape(28.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainer)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f), RoundedCornerShape(28.dp))
                            ) {
                                val artData = rememberEmbeddedArt(context, song)
                                val imageRequest = remember(artData, song.uriString, song.albumArtUriString) {
                                    val dataToLoad = artData ?: song.uriString
                                    val reqBuilder = ImageRequest.Builder(context).data(dataToLoad).crossfade(true)
                                    if ((artData == null || dataToLoad is String) && song.isVideo && dataToLoad == song.uriString) { reqBuilder.videoFrameMillis(1000) }
                                    reqBuilder.build()
                                }
                                if (visualMedia != null) {
                                    LoopingCoverVideoPlayer(
                                        videoUrl = visualMedia!!.url,
                                        mediaType = visualMedia!!.type,
                                        song = song,
                                        artData = artData,
                                        isPlaying = isPlaying,
                                        currentPositionMs = currentPosition,
                                        durationMs = duration,
                                        onSeek = onSeek,
                                        onPlayPause = onPlayPause,
                                        onSkipNext = onSkipNext,
                                        onSkipPrevious = onSkipPrevious,
                                        onLongPress = { if (isLocalSong) showArtOptionsDialog = true },
                                        gesturesEnabled = playerGesturesEnabled,
                                        isGuest = isGuest,
                                        clips = visualMedia!!.clips,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Image(painter = rememberAsyncImagePainter(model = imageRequest), contentDescription = "Cover Art Large", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.15f)))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(32.dp))

                        Column(modifier = Modifier.weight(0.55f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.SpaceEvenly) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(song.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = onBackdropColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    val annotatedArtistString = androidx.compose.ui.text.buildAnnotatedString {
                                        val artists = com.musicplayer.lytrix.utils.ArtistSeparatorManager.splitArtists(context, song.artist)
                                        artists.forEachIndexed { index, artistName ->
                                            val trimmedName = artistName
                                            if (trimmedName.isNotBlank()) {
                                                pushStringAnnotation(tag = "ARTIST", annotation = trimmedName)
                                                withStyle(style = androidx.compose.ui.text.SpanStyle(color = accentColor.copy(alpha = 0.85f), fontWeight = FontWeight.Light, fontSize = 14.sp)) { append(trimmedName) }
                                                pop()
                                                if (index < artists.size - 1) { withStyle(style = androidx.compose.ui.text.SpanStyle(color = accentColor.copy(alpha = 0.85f), fontWeight = FontWeight.Light, fontSize = 14.sp)) { append(", ") } }
                                            }
                                        }
                                    }
                                    androidx.compose.foundation.text.ClickableText(
                                        text = annotatedArtistString, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        onClick = { offset ->
                                            if (isAppOnlineSong) {
                                                annotatedArtistString.getStringAnnotations(tag = "ARTIST", start = offset, end = offset).firstOrNull()?.let { annotation ->
                                                    onClose()
                                                    val clicked = annotation.item
                                                    val isAb = song.mediaType == "AUDIOBOOK" || song.filePath in listOf("ONLINE_AUDIOBOOK", "OFFLINE_AUDIOBOOK")
                                                    val isPod = song.mediaType == "PODCAST" || song.filePath in listOf("ONLINE_PODCAST", "OFFLINE_PODCAST")
                                                    val target = when {
                                                        isAb -> "AUDIOBOOK_$clicked"
                                                        isPod && (song.channelId.isNullOrBlank() || !song.channelId!!.startsWith("UC")) -> "PODCAST_${song.album.takeIf { !it.isNullOrBlank() } ?: clicked}"
                                                        !song.channelId.isNullOrBlank() -> song.channelId!!
                                                        else -> clicked
                                                    }
                                                    onNavigateToArtist(target)
                                                }
                                            }
                                        }
                                    )
                                }
                                IconButton(onClick = {
                                    onToggleFavorite()
                                    // backup trigger
                                    CoroutineScope(Dispatchers.IO).launch {
                                        kotlinx.coroutines.delay(2000)
                                        performSilentSync(context)
                                    }
                                }) {
                                    Icon(imageVector = if (displayIsFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, contentDescription = "Like", tint = if (displayIsFavorite) accentColor else onBackdropColor.copy(alpha = 0.4f), modifier = Modifier.size(24.dp))
                                }
                            }

                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { showEqualizer = true }) { Icon(TablerIcons.Adjustments, contentDescription = "Equalizer", tint = onBackdropColor.copy(alpha = 0.7f), modifier = Modifier.size(24.dp)) }
                                Box(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) { LyricsSection(song = song, lyrics = currentLyrics, currentPosition = currentPosition, modifier = Modifier.fillMaxWidth(), onExpand = { isLyricsExpanded = true }) }
                                IconButton(onClick = { showThemeSheet = true }) { Icon(TablerIcons.Palette, contentDescription = "Theme", tint = onBackdropColor.copy(alpha = 0.7f), modifier = Modifier.size(24.dp)) }
                            }

                            Column(modifier = Modifier.fillMaxWidth()) {
                                Slider(
                                    value = (if (duration > 0) positionToShow.toFloat() / duration else 0f).coerceIn(0f, 1f),
                                    onValueChange = { if (!isGuest) draggingPosition = (it * duration).toLong() },
                                    onValueChangeFinished = { if (!isGuest) draggingPosition?.let { onSeek(it); viewModel.forceSyncToFirebase(); draggingPosition = null } },
                                    colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor, inactiveTrackColor = onBackdropColor.copy(alpha = 0.12f)),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(String.format("%02d:%02d", (positionToShow / 1000) / 60, (positionToShow / 1000) % 60), fontSize = 12.sp, color = onBackdropColor.copy(alpha = 0.4f))
                                    Text(String.format("%02d:%02d", (duration / 1000) / 60, (duration / 1000) % 60), fontSize = 12.sp, color = onBackdropColor.copy(alpha = 0.4f))
                                }
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { if (!isGuest) { onToggleShuffle(); viewModel.forceSyncToFirebase() } }) {
                                    androidx.compose.animation.AnimatedContent(targetState = shuffleEnabled, transitionSpec = { (androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.8f)).togetherWith(androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(targetScale = 0.8f)) }, label = "shuffle") { isShuffle ->
                                        Icon(Icons.Rounded.Shuffle, contentDescription = "Shuffle", tint = if (isShuffle) accentColor else onBackdropColor.copy(alpha = 0.4f), modifier = Modifier.size(24.dp))
                                    }
                                }

                                Box(modifier = Modifier.size(48.dp).pointerInput(Unit) { detectTapGestures(onPress = { if (!isGuest) { val job = coroutineScope.launch { delay(400); isPressingRewind = true }; tryAwaitRelease(); job.cancel(); if (!isPressingRewind) { onSkipPrevious(); viewModel.forceSyncToFirebase() }; isPressingRewind = false } }) }, contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "Skip Previous", tint = if (isGuest) onBackdropColor.copy(alpha=0.3f) else onBackdropColor, modifier = Modifier.size(34.dp))
                                }

                                Box(modifier = Modifier.size(64.dp).clip(CircleShape).background(if (isGuest) accentColor.copy(alpha=0.5f) else accentColor).clickable { if (!isGuest) { onPlayPause(); viewModel.forceSyncToFirebase() } }, contentAlignment = Alignment.Center) {
                                    if (isBuffering) {
                                        LytrixExpressiveSpinner(color = Color.Black, strokeWidth = 3.dp, size = 28.dp)
                                    } else {
                                        androidx.compose.animation.AnimatedContent(targetState = isPlaying, transitionSpec = { (androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.5f)).togetherWith(androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(targetScale = 0.5f)) }, label = "playPause") { playing ->
                                            Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = "Play/Pause", tint = Color.Black, modifier = Modifier.size(36.dp))
                                        }
                                    }
                                }

                                Box(modifier = Modifier.size(48.dp).pointerInput(Unit) { detectTapGestures(onPress = { if (!isGuest) { val job = coroutineScope.launch { delay(400); isPressingForward = true }; tryAwaitRelease(); job.cancel(); if (!isPressingForward) { onSkipNext(); viewModel.forceSyncToFirebase() }; isPressingForward = false } }) }, contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.SkipNext, contentDescription = "Skip Next", tint = if (isGuest) onBackdropColor.copy(alpha=0.3f) else onBackdropColor, modifier = Modifier.size(34.dp))
                                }

                                IconButton(onClick = { if (!isGuest) { onToggleRepeat(); viewModel.forceSyncToFirebase() } }) {
                                    androidx.compose.animation.AnimatedContent(targetState = repeatMode, transitionSpec = { (androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.8f)).togetherWith(androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(targetScale = 0.8f)) }, label = "repeat") { mode ->
                                        val icon = if (mode == 1 /* REPEAT_MODE_ONE */) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat
                                        val tint = if (mode == 0 /* REPEAT_MODE_OFF */) onBackdropColor.copy(alpha = 0.4f) else accentColor
                                        Icon(icon, contentDescription = "Repeat", tint = tint, modifier = Modifier.size(24.dp))
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                        val screenWidth = configuration.screenWidthDp.dp

                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            if (isVideoModeActive) {
                                val isVideoPlayerReady by viewModel.isVideoPlayerReady.collectAsState()
                                val exoPlayer = viewModel.getVideoPlayer()
                                val isYtSong = song.filePath in listOf("ONLINE_LYTRIX", "ONLINE") && !(song.albumArtUriString?.contains("saavn", ignoreCase = true) ?: false)

                                if (!isYtSong) {
                                    Box(modifier = Modifier.requiredWidth(screenWidth).aspectRatio(16f / 9f).background(Color.Black.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
                                        Text("Video stream not found", color = Color.White)
                                    }
                                } else if (!isVideoPlayerReady || exoPlayer == null) {
                                    Box(modifier = Modifier.requiredWidth(screenWidth).aspectRatio(16f / 9f).background(Color.Black.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
                                        LytrixExpressiveSpinner(color = accentColor, size = 42.dp)
                                    }
                                } else {
                                    PlayerCoverWithGestures(
                                        gesturesEnabled = playerGesturesEnabled,
                                        isGuest = isGuest,
                                        isPlaying = isPlaying,
                                        currentPosition = currentPosition,
                                        duration = duration,
                                        onSeek = { seekPos ->
                                            onSeek(seekPos)
                                            exoPlayer?.seekTo(seekPos)
                                            viewModel.forceSyncToFirebase()
                                        },
                                        onPlayPause = {
                                            onPlayPause()
                                            viewModel.forceSyncToFirebase()
                                        },
                                        swipeEnabled = false,
                                        modifier = Modifier.requiredWidth(screenWidth).aspectRatio(16f / 9f).background(Color.Black)
                                    ) {
                                        androidx.compose.ui.viewinterop.AndroidView(
                                            factory = { ctx -> androidx.media3.ui.PlayerView(ctx).apply { useController = false; controllerAutoShow = false; resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT; setBackgroundColor(android.graphics.Color.TRANSPARENT); hideController() } },
                                            update = { playerView -> if (playerView.player != exoPlayer) { playerView.player = exoPlayer } },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            } else {
                                var currentRotation by remember { mutableFloatStateOf(0f) }
                                LaunchedEffect(isPlaying) { while(isPlaying) { delay(16); currentRotation = (currentRotation + 0.4f) % 360f } }

                                val artData = rememberEmbeddedArt(context, song)
                                val imageRequest = remember(artData, song.uriString, song.albumArtUriString) {
                                    val dataToLoad = artData ?: song.uriString
                                    val reqBuilder = coil.request.ImageRequest.Builder(context).data(dataToLoad).crossfade(true)
                                    if ((artData == null || dataToLoad is String) && song.isVideo && dataToLoad == song.uriString) { reqBuilder.videoFrameMillis(1000) }
                                    reqBuilder.build()
                                }
                                PlayerCoverWithGestures(
                                    gesturesEnabled = playerGesturesEnabled,
                                    isGuest = isGuest,
                                    isPlaying = isPlaying,
                                    currentPosition = currentPosition,
                                    duration = duration,
                                    onSeek = { seekPos ->
                                        onSeek(seekPos)
                                        viewModel.forceSyncToFirebase()
                                    },
                                    onPlayPause = {
                                        onPlayPause()
                                        viewModel.forceSyncToFirebase()
                                    },
                                    onSkipNext = {
                                        onSkipNext()
                                        viewModel.forceSyncToFirebase()
                                    },
                                    onSkipPrevious = {
                                        onSkipPrevious()
                                        viewModel.forceSyncToFirebase()
                                    },
                                    onLongPress = { if (isLocalSong) showArtOptionsDialog = true },
                                    modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                                ) {
                                    if (nowPlayingTheme == "Vinyl Record") {
                                        Box(
                                            modifier = Modifier.padding(horizontal = 24.dp).aspectRatio(1f).clip(CircleShape).background(Color(0xFF121212)).border(2.dp, Color.Black.copy(alpha=0.5f), CircleShape).shadow(24.dp, CircleShape).graphicsLayer { rotationZ = currentRotation },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Canvas(modifier = Modifier.fillMaxSize()) { val stroke = Stroke(width = 1.5f.dp.toPx()); for (i in 1..10) { drawCircle(color = Color.White.copy(alpha = 0.05f), radius = size.minDimension / 2 - (i * 12.dp.toPx()), style = stroke) } }
                                            Image(painter = rememberAsyncImagePainter(model = imageRequest), contentDescription = "Vinyl Cover", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(0.45f).clip(CircleShape).border(4.dp, Color.Black, CircleShape))
                                            Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(Color.Black).border(1.dp, Color.Gray, CircleShape))
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .padding(horizontal = 24.dp)
                                                .aspectRatio(1f, matchHeightConstraintsFirst = false)
                                                .clip(if (nowPlayingTheme == "Neon Gradient") CircleShape else RoundedCornerShape(28.dp))
                                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                                .border(
                                                    if (nowPlayingTheme == "Neon Gradient") 4.dp else 1.dp,
                                                    if (nowPlayingTheme == "Neon Gradient") accentColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                                    if (nowPlayingTheme == "Neon Gradient") CircleShape else RoundedCornerShape(28.dp)
                                                )
                                        ) {
                                            val artData = rememberEmbeddedArt(context, song)
                                            if (visualMedia != null) {
                                                LoopingCoverVideoPlayer(
                                                    videoUrl = visualMedia!!.url,
                                                    mediaType = visualMedia!!.type,
                                                    song = song,
                                                    artData = artData,
                                                    isPlaying = isPlaying,
                                                    currentPositionMs = currentPosition,
                                                    durationMs = duration,
                                                    onSeek = onSeek,
                                                    onPlayPause = onPlayPause,
                                                    onSkipNext = onSkipNext,
                                                    onSkipPrevious = onSkipPrevious,
                                                    onLongPress = { if (isLocalSong) showArtOptionsDialog = true },
                                                    gesturesEnabled = playerGesturesEnabled,
                                                    isGuest = isGuest,
                                                    clips = visualMedia!!.clips,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                Image(painter = rememberAsyncImagePainter(model = imageRequest), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().blur(25.dp).alpha(0.6f))
                                                Image(painter = rememberAsyncImagePainter(model = imageRequest), contentDescription = "Cover Art Large", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                                                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.15f)))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(song.title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = onBackdropColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(modifier = Modifier.height(4.dp))
                                val annotatedArtistStringPortrait = androidx.compose.ui.text.buildAnnotatedString {
                                    val artists = com.musicplayer.lytrix.utils.ArtistSeparatorManager.splitArtists(context, song.artist)
                                    artists.forEachIndexed { index, artistName ->
                                        val trimmedName = artistName
                                        if (trimmedName.isNotBlank()) {
                                            pushStringAnnotation(tag = "ARTIST", annotation = trimmedName)
                                            withStyle(style = androidx.compose.ui.text.SpanStyle(color = accentColor.copy(alpha = 0.85f), fontWeight = FontWeight.Light, fontSize = 16.sp)) { append(trimmedName) }
                                            pop()
                                            if (index < artists.size - 1) { withStyle(style = androidx.compose.ui.text.SpanStyle(color = accentColor.copy(alpha = 0.85f), fontWeight = FontWeight.Light, fontSize = 16.sp)) { append(", ") } }
                                        }
                                    }
                                }
                                androidx.compose.foundation.text.ClickableText(
                                    text = annotatedArtistStringPortrait, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    onClick = { offset ->
                                        if (isAppOnlineSong) {
                                            annotatedArtistStringPortrait.getStringAnnotations(tag = "ARTIST", start = offset, end = offset).firstOrNull()?.let { annotation ->
                                                onClose()
                                                val clicked = annotation.item
                                                val isAb = song.mediaType == "AUDIOBOOK" || song.filePath in listOf("ONLINE_AUDIOBOOK", "OFFLINE_AUDIOBOOK")
                                                val isPod = song.mediaType == "PODCAST" || song.filePath in listOf("ONLINE_PODCAST", "OFFLINE_PODCAST")
                                                val target = when {
                                                    isAb -> "AUDIOBOOK_$clicked"
                                                    isPod && (song.channelId.isNullOrBlank() || !song.channelId!!.startsWith("UC")) -> "PODCAST_${song.album.takeIf { !it.isNullOrBlank() } ?: clicked}"
                                                    !song.channelId.isNullOrBlank() -> song.channelId!!
                                                    else -> clicked
                                                }
                                                onNavigateToArtist(target)
                                            }
                                        }
                                    }
                                )
                            }

                            IconButton(onClick = {
                                onToggleFavorite()
                                // backup trigger
                                CoroutineScope(Dispatchers.IO).launch {
                                    kotlinx.coroutines.delay(2000)
                                    performSilentSync(context)
                                }
                            }) {
                                Icon(imageVector = if (displayIsFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, contentDescription = "Like", tint = if (displayIsFavorite) accentColor else onBackdropColor.copy(alpha = 0.4f), modifier = Modifier.size(24.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { showEqualizer = true }) { Icon(TablerIcons.Adjustments, contentDescription = "Equalizer", tint = onBackdropColor.copy(alpha = 0.7f), modifier = Modifier.size(24.dp)) }
                            Box(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) { LyricsSection(song = song, lyrics = currentLyrics, currentPosition = currentPosition, modifier = Modifier.fillMaxWidth(), onExpand = { isLyricsExpanded = true }) }
                            IconButton(onClick = { showThemeSheet = true }) { Icon(TablerIcons.Palette, contentDescription = "Theme", tint = onBackdropColor.copy(alpha = 0.7f), modifier = Modifier.size(24.dp)) }
                        }
                    }

                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Slider(
                            value = (if (duration > 0) positionToShow.toFloat() / duration else 0f).coerceIn(0f, 1f),
                            onValueChange = { if (!isGuest) draggingPosition = (it * duration).toLong() },
                            onValueChangeFinished = { if (!isGuest) draggingPosition?.let { onSeek(it); viewModel.forceSyncToFirebase(); draggingPosition = null } },
                            colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor, inactiveTrackColor = onBackdropColor.copy(alpha = 0.16f)),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(String.format("%02d:%02d", (positionToShow / 1000) / 60, (positionToShow / 1000) % 60), fontSize = 12.sp, color = onBackdropColor.copy(alpha = 0.65f), fontWeight = FontWeight.Medium)
                            Text(String.format("%02d:%02d", (duration / 1000) / 60, (duration / 1000) % 60), fontSize = 12.sp, color = onBackdropColor.copy(alpha = 0.65f), fontWeight = FontWeight.Medium)
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { if (!isGuest) { onToggleShuffle(); viewModel.forceSyncToFirebase() } }) {
                                androidx.compose.animation.AnimatedContent(targetState = shuffleEnabled, transitionSpec = { (androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.8f)).togetherWith(androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(targetScale = 0.8f)) }, label = "shuffle") { isShuffle ->
                                    Icon(Icons.Rounded.Shuffle, contentDescription = "Shuffle", tint = if (isShuffle) accentColor else onBackdropColor.copy(alpha = 0.45f), modifier = Modifier.size(24.dp))
                                }
                            }

                            Box(modifier = Modifier.size(52.dp).clip(CircleShape).background(onBackdropColor.copy(alpha = 0.08f)).pointerInput(Unit) { detectTapGestures(onPress = { if (!isGuest) { val job = coroutineScope.launch { delay(400); isPressingRewind = true }; tryAwaitRelease(); job.cancel(); if (!isPressingRewind) { onSkipPrevious(); viewModel.forceSyncToFirebase() }; isPressingRewind = false } }) }, contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.SkipPrevious, contentDescription = "Skip Previous", tint = if (isGuest) onBackdropColor.copy(alpha=0.3f) else onBackdropColor, modifier = Modifier.size(30.dp))
                            }

                            Box(modifier = Modifier.size(72.dp).clip(CircleShape).background(if (isGuest) accentColor.copy(alpha=0.5f) else accentColor).clickable { if (!isGuest) { onPlayPause(); viewModel.forceSyncToFirebase() } }, contentAlignment = Alignment.Center) {
                                if (isBuffering) {
                                    LytrixExpressiveSpinner(color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 3.dp, size = 28.dp)
                                } else {
                                    androidx.compose.animation.AnimatedContent(targetState = isPlaying, transitionSpec = { (androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.5f)).togetherWith(androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(targetScale = 0.5f)) }, label = "playPause") { playing ->
                                        Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = "Play/Pause", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(38.dp))
                                    }
                                }
                            }

                            Box(modifier = Modifier.size(52.dp).clip(CircleShape).background(onBackdropColor.copy(alpha = 0.08f)).pointerInput(Unit) { detectTapGestures(onPress = { if (!isGuest) { val job = coroutineScope.launch { delay(400); isPressingForward = true }; tryAwaitRelease(); job.cancel(); if (!isPressingForward) { onSkipNext(); viewModel.forceSyncToFirebase() }; isPressingForward = false } }) }, contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.SkipNext, contentDescription = "Skip Next", tint = if (isGuest) onBackdropColor.copy(alpha=0.3f) else onBackdropColor, modifier = Modifier.size(30.dp))
                            }

                            IconButton(onClick = { if (!isGuest) { onToggleRepeat(); viewModel.forceSyncToFirebase() } }) {
                                androidx.compose.animation.AnimatedContent(targetState = repeatMode, transitionSpec = { (androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.8f)).togetherWith(androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(targetScale = 0.8f)) }, label = "repeat") { mode ->
                                    val icon = if (mode == 1 /* REPEAT_MODE_ONE */) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat
                                    val tint = if (mode == 0 /* REPEAT_MODE_OFF */) onBackdropColor.copy(alpha = 0.45f) else accentColor
                                    Icon(icon, contentDescription = "Repeat", tint = tint, modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showArtOptionsDialog && isLocalSong) {
            val prefs = context.getSharedPreferences("custom_album_arts", Context.MODE_PRIVATE)
            val hasCustomArt = prefs.contains(song.id.toString())

            androidx.compose.ui.window.Dialog(onDismissRequest = { showArtOptionsDialog = false }) {
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    elevation = CardDefaults.cardElevation(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Album Art Options", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(bottom = 24.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(accentColor.copy(alpha = 0.12f)).clickable {
                                showArtOptionsDialog = false
                                albumArtPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }.padding(16.dp)
                        ) {
                            Icon(Icons.Outlined.Image, contentDescription = "Change", tint = accentColor)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("Change Image", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = accentColor)
                        }

                        if (hasCustomArt) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.Red.copy(alpha = 0.12f)).clickable {
                                    showArtOptionsDialog = false
                                    viewModel.removeCustomAlbumArt(context, song.id)
                                }.padding(16.dp)
                            ) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Remove", tint = Color.Red)
                                Spacer(modifier = Modifier.width(16.dp))
                                Text("Remove Custom Image", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.Red)
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        TextButton(onClick = { showArtOptionsDialog = false }, modifier = Modifier.align(Alignment.End)) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }

        if (showEqualizer) { com.musicplayer.lytrix.ui.EqualizerBottomSheet(viewModel = viewModel, onDismiss = { showEqualizer = false }) }
        if (showThemeSheet) { com.musicplayer.lytrix.ui.PlayerThemeBottomSheet(onDismiss = { showThemeSheet = false }) }

        val currentLyricsProviderName by viewModel.currentLyricsProviderName.collectAsState()
        AnimatedVisibility(visible = isLyricsExpanded, enter = fadeIn() + slideInVertically { it }, exit = fadeOut() + slideOutVertically { it }) {
            LyricsFullScreenOverlay(
                song = song,
                lyrics = currentLyrics,
                currentPosition = currentPosition,
                isPlaying = isPlaying,
                onPlayPause = onPlayPause,
                onClose = { isLyricsExpanded = false },
                onSeek = onSeek,
                onChangeSource = { viewModel.changeLyricsSource(context) },
                onPrevious = onSkipPrevious,
                onNext = onSkipNext,
                durationMs = song.duration,
                accentColor = accentColor,
                providerName = currentLyricsProviderName
            )
        }
        if (showQueue) { UpNextQueueSheet(viewModel = viewModel, accentColor = accentColor, onDismiss = { showQueue = false }) }
    }
}
@Composable
fun MiniPlayerCard(
    song: com.musicplayer.lytrix.model.Song,
    isPlaying: Boolean,
    accentColor: androidx.compose.ui.graphics.Color,
    currentPosition: Long = 0L,
    duration: Long = 0L,
    onPlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onExpand: () -> Unit,
    onDismiss: () -> Unit = {},
    currentTheme: String = "Solid Default",
    themeFont: androidx.compose.ui.text.font.FontFamily = androidx.compose.ui.text.font.FontFamily.Default
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isVideoThemeActive = isPremiumVideoThemeActive(currentTheme)

    val appBgColor = MaterialTheme.colorScheme.background
    val isLightMode = (appBgColor.red + appBgColor.green + appBgColor.blue) > 1.5f

    val themeColors = when (currentTheme) {
        "Cyber City" -> listOf(Color(0xFF9D4EDD), Color(0xFF00F2FE))
        "Campside Rain" -> listOf(Color(0xFF10B981), Color(0xFFA5D6A7))
        "Cat Rain" -> listOf(Color(0xFF3B82F6), Color(0xFF87CEEB))
        "Cartoon Walk" -> listOf(Color(0xFFF59E0B), Color(0xFFFFD700))
        "Summer Home" -> listOf(Color(0xFF00E5FF), Color(0xFF84FFFF))
        "Summer Village" -> listOf(Color(0xFF00E676), Color(0xFF69F0AE))
        "Snowy Home" -> listOf(Color(0xFF00BFFF), Color(0xFFE0FFFF))
        else -> listOf(accentColor, accentColor.copy(alpha = 0.5f))
    }

    val primaryAccent = if (isVideoThemeActive) themeColors[0] else MaterialTheme.colorScheme.primary
    val cardShape = RoundedCornerShape(20.dp)

    // Gesture & Physics animatables (Meld Swipe Up / Down, Y = 0 Fixed)
    val offsetXAnimatable = remember { Animatable(0f) }
    var totalDragX by remember { mutableFloatStateOf(0f) }
    var totalDragY by remember { mutableFloatStateOf(0f) }

    val textColor = if (isVideoThemeActive) Color.White else MaterialTheme.colorScheme.onSurface
    val subtextColor = if (isVideoThemeActive) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
    val progress = if (duration > 0) (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

    val artData = rememberEmbeddedArt(context, song)

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    Box(
        modifier = (if (isLandscape) Modifier.width(420.dp) else Modifier.fillMaxWidth())
            .padding(start = if (isLandscape) 0.dp else 12.dp, end = if (isLandscape) 0.dp else 12.dp, top = 4.dp, bottom = if (isLandscape) 6.dp else 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isLandscape) 54.dp else 68.dp)
                .offset { IntOffset(offsetXAnimatable.value.roundToInt(), 0) }
                .clip(cardShape)
                .then(
                    if (isVideoThemeActive) {
                        Modifier.background(
                            Brush.horizontalGradient(
                                listOf(
                                    themeColors[0].copy(alpha = 0.22f),
                                    Color(0xFF161618),
                                    themeColors.getOrElse(1) { themeColors[0] }.copy(alpha = 0.14f)
                                )
                            )
                        )
                    } else {
                        Modifier.background(MaterialTheme.colorScheme.surfaceContainer)
                    }
                )
                .border(
                    width = 1.dp,
                    color = if (isVideoThemeActive) themeColors[0].copy(alpha = 0.25f)
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    shape = cardShape
                )
                .clickable(onClick = onExpand)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {
                            val swipeThreshold = 65f
                            if (totalDragY > swipeThreshold) {
                                // 🔥 মেল্ডের মতো নিচে টানলে গান বন্ধ ও প্লেয়ার ডিসমিস
                                onDismiss()
                            } else if (totalDragY < -swipeThreshold) {
                                // 🔥 মেল্ডের মতো উপরে টানলে স্মুথলি নাওপ্লেয়িং ফুল স্ক্রিন ওপেন
                                onExpand()
                            } else if (offsetXAnimatable.value > 120f) {
                                onSkipPrevious()
                            } else if (offsetXAnimatable.value < -120f) {
                                onSkipNext()
                            }

                            coroutineScope.launch {
                                offsetXAnimatable.animateTo(0f, spring(stiffness = Spring.StiffnessLow))
                            }
                            totalDragX = 0f
                            totalDragY = 0f
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                offsetXAnimatable.animateTo(0f, spring(stiffness = Spring.StiffnessLow))
                            }
                            totalDragX = 0f
                            totalDragY = 0f
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            totalDragX += dragAmount.x
                            totalDragY += dragAmount.y
                            // Strictly Y = 0; ONLY horizontal translation
                            coroutineScope.launch {
                                offsetXAnimatable.snapTo(offsetXAnimatable.value + dragAmount.x)
                            }
                        }
                    )
                }
                .testTag("mini_player")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Artwork (48dp squircle with 12dp corners)
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(artData ?: song.uriString)
                        .crossfade(true)
                        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                        .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                        .apply {
                            if ((artData == null || artData == song.uriString) && song.isVideo) {
                                videoFrameMillis(1000)
                            }
                        }
                        .build(),
                    contentDescription = "Cover",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            1.dp,
                            if (isVideoThemeActive) Color.White.copy(alpha = 0.15f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            RoundedCornerShape(12.dp)
                        )
                        .background(if (isVideoThemeActive) Color.DarkGray else MaterialTheme.colorScheme.surfaceContainerHigh)
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Song Title & Artist
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = song.title,
                        fontFamily = themeFont,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = song.artist,
                        fontFamily = themeFont,
                        fontSize = 12.sp,
                        color = subtextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Transport Controls
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onSkipPrevious,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Rounded.SkipPrevious,
                            contentDescription = "Prev",
                            tint = textColor.copy(alpha = 0.85f),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    val playPauseButtonContainerColor = if (isVideoThemeActive) primaryAccent else MaterialTheme.colorScheme.primary
                    val playPauseButtonContentColor = if (isVideoThemeActive) {
                        if (primaryAccent.luminance() > 0.5f) Color.Black else Color.White
                    } else {
                        MaterialTheme.colorScheme.onPrimary
                    }

                    FilledIconButton(
                        onClick = onPlayPause,
                        modifier = Modifier.size(42.dp).bouncePress(),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = playPauseButtonContainerColor,
                            contentColor = playPauseButtonContentColor
                        )
                    ) {
                        Icon(
                            if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = "Play/Pause",
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(
                        onClick = onSkipNext,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Rounded.SkipNext,
                            contentDescription = "Next",
                            tint = textColor.copy(alpha = 0.85f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Continuous Linear Progress Bar at Bottom
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        if (isVideoThemeActive) Color.White.copy(alpha = 0.12f)
                        else MaterialTheme.colorScheme.surfaceContainerHighest
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .background(
                            if (isVideoThemeActive) Brush.horizontalGradient(themeColors)
                            else Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)))
                        )
                )
            }
        }
    }
}

@Composable
fun SongListItem(
    song: com.musicplayer.lytrix.model.Song,
    isActive: Boolean,
    isPlaying: Boolean,
    activeColor: Color,
    playCountText: String? = null,
    showDragHandle: Boolean = false,
    onClick: () -> Unit,
    currentTheme: String = "Solid Default",
    themeFont: androidx.compose.ui.text.font.FontFamily = androidx.compose.ui.text.font.FontFamily.Default
) {
    val infiniteTransition = rememberInfiniteTransition(label = "glow_anim")
    val themeColors = when(currentTheme) {
        "Cyber City" -> listOf(Color(0xFF9D4EDD), Color(0xFF00F2FE))
        "Campside Rain" -> listOf(Color(0xFF10B981), Color(0xFFA5D6A7))
        "Cat Rain" -> listOf(Color(0xFF3B82F6), Color(0xFF87CEEB))
        "Cartoon Walk" -> listOf(Color(0xFFF59E0B), Color(0xFFFFD700))
        "Summer Home" -> listOf(Color(0xFF00E5FF), Color(0xFF84FFFF))
        "Summer Village" -> listOf(Color(0xFF00E676), Color(0xFF69F0AE))
        "Snowy Home" -> listOf(Color(0xFF00BFFF), Color(0xFFE0FFFF))
        else -> listOf(activeColor, activeColor.copy(alpha = 0.5f))
    }

    val gradientOffset by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart),
        label = "gradient_offset"
    )

    val isVideoThemeActive = isPremiumVideoThemeActive(currentTheme)
    val borderModifier = if (isActive && isVideoThemeActive) {
        val borderBrush = Brush.linearGradient(
            colors = themeColors,
            start = Offset(gradientOffset, 0f),
            end = Offset(gradientOffset + 500f, 500f)
        )
        Modifier.border(2.dp, borderBrush, RoundedCornerShape(16.dp))
    } else if (isActive) {
        Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
    } else Modifier

    val isSystemDark = isSystemInDarkTheme()

    val panelColor = if (isVideoThemeActive) Color.Black.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceContainer
    val defaultTextColor = if (isVideoThemeActive) Color.White else MaterialTheme.colorScheme.onSurface

    val animatedBg by animateColorAsState(targetValue = if (isActive) (if (isVideoThemeActive) themeColors[0].copy(alpha = 0.2f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)) else panelColor, animationSpec = tween(300))
    val animatedText by animateColorAsState(targetValue = if (isActive) (if (isVideoThemeActive) themeColors[0] else MaterialTheme.colorScheme.primary) else defaultTextColor, animationSpec = tween(300))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .then(borderModifier)
            .clip(RoundedCornerShape(16.dp))
            .background(animatedBg)
            .then(if (!isVideoThemeActive && !isActive) Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f), RoundedCornerShape(16.dp)) else Modifier)
            .bouncePress()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 12.dp)
            .testTag("track_${song.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val context = LocalContext.current
        val artData = rememberEmbeddedArt(context, song)

        val painter = rememberAsyncImagePainter(
            model = ImageRequest.Builder(context)
                .data(artData ?: song.uriString)
                .apply {
                    if ((artData == null || artData == song.uriString) && song.isVideo) {
                        videoFrameMillis(1000)
                    }
                }
                .crossfade(true).memoryCachePolicy(coil.request.CachePolicy.ENABLED).build()
        )

        Image(
            painter = painter, contentDescription = "Cover Image", contentScale = ContentScale.Crop,
            modifier = Modifier.size(50.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = song.title, fontFamily = themeFont, fontSize = 16.sp, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium, color = animatedText, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (song.isVideo) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(activeColor.copy(alpha = 0.3f)).padding(horizontal = 4.dp, vertical = 2.dp)) {
                        Text(text = "MV", fontFamily = themeFont, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = activeColor)
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = playCountText ?: song.artist, fontFamily = themeFont, fontSize = 13.sp, fontWeight = FontWeight.Light, color = if (isActive) themeColors[0].copy(alpha = 0.9f) else defaultTextColor.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        Spacer(modifier = Modifier.width(12.dp))

        if (isActive && isPlaying) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.height(16.dp)) {
                listOf(400, 600, 500).forEach { dur ->
                    val scale by infiniteTransition.animateFloat(initialValue = 0.3f, targetValue = 1f, animationSpec = infiniteRepeatable(tween(dur, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bar_scale")
                    Box(modifier = Modifier.width(3.dp).fillMaxHeight(scale).clip(CircleShape).background(themeColors[0]))
                }
            }
        } else {
            Text(text = formatTime(song.duration), fontFamily = themeFont, fontSize = 12.sp, color = if (isActive) themeColors[0].copy(alpha = 0.8f) else defaultTextColor.copy(alpha = 0.5f), fontWeight = FontWeight.Light)
        }
    }
}

@Composable
fun QueueSongRow(
    song: Song,
    playingSong: Song?,
    isPlaying: Boolean = true,
    isDragged: Boolean,
    accentColor: Color,
    onDragStart: () -> Unit,
    onDrag: (change: androidx.compose.ui.input.pointer.PointerInputChange, dragAmount: Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isCurrent = song.id == playingSong?.id
    val elevation by animateDpAsState(if (isDragged) 14.dp else 0.dp)
    val scale by animateFloatAsState(if (isDragged) 1.035f else 1f)

    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentOnDragCancel by rememberUpdatedState(onDragCancel)

    val context = LocalContext.current

    val artData = rememberEmbeddedArt(context, song)
    val imageRequest = remember(artData, song.uriString) {
        val dataToLoad = artData ?: song.uriString
        val reqBuilder = coil.request.ImageRequest.Builder(context)
            .data(dataToLoad)
            .crossfade(true)
            .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
            .diskCachePolicy(coil.request.CachePolicy.ENABLED)

        if ((artData == null || dataToLoad is String) && song.isVideo) {
            reqBuilder.videoFrameMillis(1000)
        }
        reqBuilder.build()
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 3.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .zIndex(if (isDragged) 10f else 0f)
            .shadow(elevation, shape = RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(
                when {
                    isDragged -> MaterialTheme.colorScheme.surfaceContainerHighest
                    isCurrent -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    else -> Color.Transparent
                }
            )
            .then(
                when {
                    isDragged -> Modifier.border(1.5.dp, accentColor.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                    isCurrent -> Modifier.border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                    else -> Modifier
                }
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail with rounded squircle
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            coil.compose.AsyncImage(
                model = imageRequest,
                contentDescription = "Album Art",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // When current, subtle overlay
            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.20f))
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Title and artist
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                fontSize = 15.sp,
                color = if (isCurrent) accentColor else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = song.artist,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 12.sp,
                color = if (isCurrent) accentColor.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Live Dancing Equalizer Waveform if currently playing!
        if (isCurrent) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .height(24.dp),
                contentAlignment = Alignment.Center
            ) {
                LytrixEqualizerWaves(
                    isPlaying = isPlaying,
                    barColor = accentColor,
                    maxHeight = 16.dp,
                    barWidth = 3.dp,
                    spacing = 2.dp
                )
            }
        }

        // Apple Music style Drag Handle (Touch Target 40dp, NO vibration)
        Box(
            modifier = Modifier
                .size(40.dp)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { currentOnDragStart() },
                        onVerticalDrag = { change, dragAmount -> currentOnDrag(change, dragAmount) },
                        onDragEnd = { currentOnDragEnd() },
                        onDragCancel = { currentOnDragCancel() }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = "Drag to reorder",
                tint = if (isCurrent) accentColor.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.40f),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
@androidx.annotation.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@androidx.compose.runtime.Composable
fun UpNextQueueSheet(viewModel: com.musicplayer.lytrix.ui.PlayerViewModel, accentColor: androidx.compose.ui.graphics.Color, onDismiss: () -> Unit) {
    val currentQueue by viewModel.currentQueueList.collectAsState()
    val playingSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    var localQueue by androidx.compose.runtime.remember(currentQueue) { androidx.compose.runtime.mutableStateOf(currentQueue) }
    var draggedSongId by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Long?>(null) }
    var initialDragIndex by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(-1) }
    var fingerY by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }

    fun updateSwap() {
        val sId = draggedSongId ?: return
        val fromIndex = localQueue.indexOfFirst { it.id == sId }
        if (fromIndex == -1) return
        val items = listState.layoutInfo.visibleItemsInfo
        if (items.isEmpty()) return
        val target = items.firstOrNull { fingerY >= it.offset && fingerY <= it.offset + it.size }
            ?: if (fingerY < items.first().offset) items.first() else items.last()
        val toIndex = target.index
        if (toIndex in localQueue.indices && toIndex != fromIndex) {
            val list = localQueue.toMutableList()
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            localQueue = list
        }
    }

    fun finishDrag() {
        val sId = draggedSongId
        val startIdx = initialDragIndex
        draggedSongId = null
        initialDragIndex = -1
        if (sId != null && startIdx != -1) {
            val finalIdx = localQueue.indexOfFirst { it.id == sId }
            if (finalIdx != -1 && finalIdx != startIdx) {
                viewModel.moveQueueItem(startIdx, finalIdx)
            }
        }
    }

    // ড্র্যাগ চলাকালীন প্রতি ফ্রেমে চলবে — আঙুল স্থির থাকলেও স্ক্রল হবে
    androidx.compose.runtime.LaunchedEffect(draggedSongId != null) {
        if (draggedSongId == null) return@LaunchedEffect
        while (isActive) {
            val info = listState.layoutInfo
            val margin = 120f
            val topEdge = info.viewportStartOffset + margin
            val bottomEdge = info.viewportEndOffset - margin
            val delta = when {
                fingerY < topEdge && listState.canScrollBackward ->
                    -16f * ((topEdge - fingerY) / margin).coerceIn(0.2f, 1f)
                fingerY > bottomEdge && listState.canScrollForward ->
                    16f * ((fingerY - bottomEdge) / margin).coerceIn(0.2f, 1f)
                else -> 0f
            }
            if (delta != 0f) {
                listState.scrollBy(delta)
                updateSwap()
            }
            delay(16)
        }
    }

    // ড্র্যাগ চলাকালীন বটমশিট ডিসমিস জেসচার ইন্টারসেপ্ট ও লক করে রাখা যাতে নিচে টানলে শিট বন্ধ না হয়!
    val nestedScrollConnection = androidx.compose.runtime.remember(draggedSongId != null) {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource
            ): androidx.compose.ui.geometry.Offset {
                return if (draggedSongId != null) available else androidx.compose.ui.geometry.Offset.Zero
            }
            override fun onPostScroll(
                consumed: androidx.compose.ui.geometry.Offset,
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource
            ): androidx.compose.ui.geometry.Offset {
                return if (draggedSongId != null) available else androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }

    com.musicplayer.lytrix.ui.component.LytrixFluidBottomSheet(
        onDismissRequest = onDismiss,
        enableTwoStage = true,
        halfFraction = 0.55f,
        expandedFraction = 0.90f,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            androidx.compose.foundation.layout.Box(
                modifier = androidx.compose.ui.Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            )
        }
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = androidx.compose.ui.Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.22f),
                            accentColor.copy(alpha = 0.08f),
                            androidx.compose.ui.graphics.Color.Transparent
                        ),
                        startY = 0f,
                        endY = 550f
                    )
                )
        ) {

            androidx.compose.foundation.layout.Row(
                modifier = androidx.compose.ui.Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    androidx.compose.material3.Text(
                        text = "Up Next",
                        style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.width(8.dp))
                    androidx.compose.foundation.layout.Box(
                        modifier = androidx.compose.ui.Modifier
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                            .background(accentColor.copy(alpha = 0.16f))
                            .border(1.dp, accentColor.copy(alpha = 0.35f), androidx.compose.foundation.shape.RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        androidx.compose.material3.Text(
                            text = "${localQueue.size} songs",
                            fontSize = 11.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                            color = accentColor
                        )
                    }
                }

                androidx.compose.foundation.layout.Row(
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    // 1. Reset Button (Refresh Icon): Removes ONLY "Play Next" songs
                    androidx.compose.foundation.layout.Box(
                        modifier = androidx.compose.ui.Modifier
                            .size(36.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable { viewModel.resetPlayNextQueue(context) },
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = androidx.compose.material.icons.Icons.Rounded.Refresh,
                            contentDescription = "Reset Play Next",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = androidx.compose.ui.Modifier.size(19.dp)
                        )
                    }

                    // 2. Target Icon: Scroll to current playing song
                    androidx.compose.foundation.layout.Box(
                        modifier = androidx.compose.ui.Modifier
                            .size(36.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(accentColor.copy(alpha = 0.18f))
                            .border(1.dp, accentColor.copy(alpha = 0.38f), androidx.compose.foundation.shape.CircleShape)
                            .clickable {
                                val currentIndex = localQueue.indexOfFirst { it.id == playingSong?.id }
                                if (currentIndex != -1) {
                                    coroutineScope.launch { listState.animateScrollToItem(currentIndex) }
                                }
                            },
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = androidx.compose.material.icons.Icons.Rounded.MyLocation,
                            contentDescription = "Scroll to current playing song",
                            tint = accentColor,
                            modifier = androidx.compose.ui.Modifier.size(18.dp)
                        )
                    }

                    // 3. Delete Button: Clears entire queue except current song
                    androidx.compose.foundation.layout.Box(
                        modifier = androidx.compose.ui.Modifier
                            .size(36.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(androidx.compose.ui.graphics.Color(0xFFE53935).copy(alpha = 0.15f))
                            .border(1.dp, androidx.compose.ui.graphics.Color(0xFFE53935).copy(alpha = 0.30f), androidx.compose.foundation.shape.CircleShape)
                            .clickable { viewModel.clearQueueExceptCurrent(context) },
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = androidx.compose.material.icons.Icons.Rounded.Delete,
                            contentDescription = "Clear Queue",
                            tint = androidx.compose.ui.graphics.Color(0xFFFF5252),
                            modifier = androidx.compose.ui.Modifier.size(19.dp)
                        )
                    }
                }
            }

            androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.height(8.dp))

            androidx.compose.foundation.lazy.LazyColumn(
                state = listState,
                modifier = androidx.compose.ui.Modifier.nestedScroll(nestedScrollConnection),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
            ) {
                itemsIndexed(items = localQueue, key = { _, song -> song.id }) { index, song ->
                    val isDragged = song.id == draggedSongId

                    QueueSongRow(
                        song = song,
                        playingSong = playingSong,
                        isPlaying = isPlaying,
                        isDragged = isDragged,
                        accentColor = accentColor,
                        modifier = Modifier.animateItem(),
                        onClick = { viewModel.playSong(song, localQueue) },
                        onDragStart = {
                            draggedSongId = song.id
                            initialDragIndex = index
                            listState.layoutInfo.visibleItemsInfo
                                .firstOrNull { it.index == index }
                                ?.let { fingerY = it.offset + it.size / 2f }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val info = listState.layoutInfo
                            fingerY = (fingerY + dragAmount).coerceIn(
                                info.viewportStartOffset.toFloat(),
                                info.viewportEndOffset.toFloat()
                            )
                            updateSwap()
                        },
                        onDragEnd = { finishDrag() },
                        onDragCancel = { finishDrag() }
                    )
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
fun rememberEmbeddedArt(context: android.content.Context, song: com.musicplayer.lytrix.model.Song?): Any? {
    if (song == null) return null

    // ১. গ্যালারি থেকে কাস্টম কভার বা অনলাইন ইউআরএল
    val customArt = song.albumArtUriString?.takeIf { it.isNotBlank() && it != "null" }
    if (customArt != null && (customArt.startsWith("file://") || customArt.startsWith("http://") || customArt.startsWith("https://"))) {
        return customArt
    }
    if (song.uriString.startsWith("http")) {
        return customArt ?: song.uriString
    }

    // ২. শুধুমাত্র Lytrix থেকে ডাউনলোড করা গান হলেই অফলাইন কভার ফাইল চেক করবে!
    val isLytrixDownloaded = song.filePath == "OFFLINE"
    if (isLytrixDownloaded) {
        val safeName = song.title.replace(Regex("[^a-zA-Z0-9 ]"), "_").take(50).trim()
        val coverFile = java.io.File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC), "${safeName}_cover.jpg")
        if (coverFile.exists() && coverFile.length() > 0) {
            return coverFile
        }
    }

    // ৩. লোকাল অডিও বা ভিডিও গান (LibraryCache চেক)
    val safeHash = song.uriString.hashCode().toString()
    val libCache = java.io.File(context.cacheDir, "lytrix_lib_$safeHash.jpg")

    var artData by androidx.compose.runtime.remember(song.id, song.uriString) {
        val initial = if (libCache.exists() && libCache.length() > 0) {
            libCache.absolutePath
        } else {
            customArt ?: song.uriString
        }
        androidx.compose.runtime.mutableStateOf<Any?>(initial)
    }

    androidx.compose.runtime.LaunchedEffect(song.id, song.uriString) {
        if (libCache.exists() && libCache.length() > 0) {
            artData = libCache.absolutePath
            return@LaunchedEffect
        }

        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            var retriever: android.media.MediaMetadataRetriever? = null
            try {
                if (!song.uriString.startsWith("http")) {
                    retriever = android.media.MediaMetadataRetriever()
                    retriever.setDataSource(context, android.net.Uri.parse(song.uriString))
                    val pic = retriever.embeddedPicture
                    if (pic != null && pic.isNotEmpty()) {
                        java.io.FileOutputStream(libCache).use { it.write(pic) }
                        artData = libCache.absolutePath
                    }
                }
            } catch (_: Exception) {
            } finally {
                try { retriever?.release() } catch (_: Exception) {}
            }
        }
    }
    return artData
}

fun isUserPremium(context: android.content.Context): Boolean {
    val prefs = context.getSharedPreferences("premium_prefs", android.content.Context.MODE_PRIVATE)
    if (prefs.getBoolean("is_permanent", false)) return true
    val expiryTime = prefs.getLong("subscription_expiry", 0L)
    return System.currentTimeMillis() < expiryTime
}

fun activatePremium(context: android.content.Context, days: Int, isPremiumPlus: Boolean) {
    val prefs = context.getSharedPreferences("premium_prefs", android.content.Context.MODE_PRIVATE)

    var expiry = prefs.getLong("subscription_expiry", 0L)
    var adFreeExpiry = prefs.getLong("ad_free_expiry", 0L)

    prefs.edit().putBoolean("is_premium", true).apply()
    if (isPremiumPlus) prefs.edit().putBoolean("is_premium_plus", true).apply()

    if (days == -1) {
        prefs.edit().putBoolean("is_permanent", true).apply()
        expiry = Long.MAX_VALUE
        if (isPremiumPlus) adFreeExpiry = Long.MAX_VALUE
    } else {
        val addedMillis = days.toLong() * 24L * 60L * 60L * 1000L

        if (expiry != Long.MAX_VALUE) {
            expiry = maxOf(System.currentTimeMillis(), expiry) + addedMillis
        }

        if (isPremiumPlus) {
            if (adFreeExpiry != Long.MAX_VALUE) {
                adFreeExpiry = maxOf(System.currentTimeMillis(), adFreeExpiry) + addedMillis
            }
        }
    }

    prefs.edit().putLong("subscription_expiry", expiry).putLong("ad_free_expiry", adFreeExpiry).putBoolean("is_permanent", expiry == Long.MAX_VALUE).apply()

    val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
    if (user != null) {
        val dbRef = com.google.firebase.database.FirebaseDatabase.getInstance().reference.child("users").child(user.uid)
        val updateMap = mutableMapOf<String, Any>("is_premium" to true, "subscription_expiry" to expiry)
        if (isPremiumPlus) updateMap["ad_free_expiry"] = adFreeExpiry
        dbRef.updateChildren(updateMap)
    }

    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
        com.musicplayer.lytrix.ui.DataStoreManager(context).savePremiumStatus(true, expiry)
        if (isPremiumPlus) {
            context.dataStore.edit { p ->
                p[androidx.datastore.preferences.core.booleanPreferencesKey("is_premium_plus")] = true
                p[androidx.datastore.preferences.core.longPreferencesKey("ad_free_expiry")] = adFreeExpiry
            }
        }
    }
}

fun verifyPremiumCode(context: android.content.Context, code: String, onResult: (Boolean, String) -> Unit) {
    if (code.isBlank()) {
        onResult(false, "Please enter a code")
        return
    }

    val isPremiumPlus = true // 🔥 PRO বা PLUS যেকোনো কোডেই সব আনলক + অ্যাড-ফ্রি
    val prefs = context.getSharedPreferences("premium_prefs", android.content.Context.MODE_PRIVATE)

    if (prefs.getLong("subscription_expiry", 0L) == Long.MAX_VALUE || prefs.getLong("ad_free_expiry", 0L) == Long.MAX_VALUE) {
        onResult(false, "You already have Lifetime Premium! 🎉")
        return
    }

    val db = com.google.firebase.database.FirebaseDatabase.getInstance("https://lytrix-player-default-rtdb.asia-southeast1.firebasedatabase.app")
    val codeRef = db.getReference("activation_codes").child(code)

    codeRef.get().addOnSuccessListener { snapshot ->
        if (snapshot.exists()) {
            val isUsed = snapshot.child("used").getValue(Boolean::class.java) ?: true
            val days = snapshot.child("days").getValue(Int::class.java) ?: 0
            if (!isUsed) {
                codeRef.child("used").setValue(true).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        activatePremium(context, days, isPremiumPlus)
                        onResult(true, "Success! Premium Activated.")
                    } else { onResult(false, "Network error! Try again.") }
                }
            } else onResult(false, "This code has already been used!")
        } else onResult(false, "Invalid Code! Please check again.")
    }.addOnFailureListener { onResult(false, "Server error! Check internet connection.") }
}

@androidx.compose.runtime.Composable
fun LockedPremiumScreen(accentColor: androidx.compose.ui.graphics.Color, onUnlockClick: () -> Unit) {
    val isLightMode = androidx.compose.material3.MaterialTheme.colorScheme.background.luminance() > 0.5f
    val btnTextColor = if (isLightMode && accentColor.luminance() < 0.5f) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Black

    androidx.compose.foundation.layout.Column(
        modifier = androidx.compose.ui.Modifier
            .fillMaxSize()
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(
                        androidx.compose.material3.MaterialTheme.colorScheme.background,
                        accentColor.copy(alpha = 0.1f)
                    )
                )
            )
            .padding(32.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = androidx.compose.ui.Modifier
                .size(100.dp)
                .background(accentColor.copy(alpha = 0.15f), androidx.compose.foundation.shape.CircleShape)
                .padding(24.dp),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            androidx.compose.material3.Icon(
                androidx.compose.material.icons.Icons.Filled.LockOpen,
                contentDescription = "Locked",
                tint = accentColor,
                modifier = androidx.compose.ui.Modifier.fillMaxSize()
            )
        }
        androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.height(24.dp))
        androidx.compose.material3.Text("Unlock the Full Experience", fontSize = 24.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold, color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.height(12.dp))
        androidx.compose.material3.Text("Get access to Discover, Cloud Playlists, Premium Themes, and an Ad-Free experience.", textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 14.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f), lineHeight = 20.sp)
        androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.height(40.dp))

        androidx.compose.material3.Button(
            onClick = onUnlockClick,
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = accentColor),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
            modifier = androidx.compose.ui.Modifier.fillMaxWidth(0.85f).height(56.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            elevation = androidx.compose.material3.ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
        ) {
            androidx.compose.foundation.layout.Row(
                modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.WorkspacePremium, contentDescription = null, tint = btnTextColor)
                androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.width(8.dp))
                androidx.compose.material3.Text("SEE PLANS & ACTIVATE", color = btnTextColor, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold, letterSpacing = 1.sp)
            }
        }
    }
}

@Composable
fun PremiumActivationDialog(accentColor: Color, onDismiss: () -> Unit, onSuccess: () -> Unit) {
    val context = LocalContext.current
    // 🔥 ম্যাজিক: প্যারামিটার চেঞ্জ না করেই আমরা এখানেই SettingsViewModel কল করে নিচ্ছি
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(context.applicationContext as android.app.Application)
    )

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        PremiumStoreScreen(
            accentColor = accentColor,
            onNavigateBack = onDismiss,
            onPremiumActivated = { onSuccess(); onDismiss() },
            onSubmitTrx = { planTitle, price, days, method, trxId, onOk, onFail ->
                // 🔥 ফায়ারবেসে TrxID সাবমিট করা হচ্ছে
                settingsViewModel.submitPremiumPayment(
                    planTitle = planTitle,
                    priceBdt = price,
                    days = days,
                    method = method,
                    trxId = trxId,
                    onSuccess = {
                        android.widget.Toast.makeText(context, "Payment submitted successfully!", android.widget.Toast.LENGTH_SHORT).show()
                        onOk()
                    },
                    onError = { errorMsg ->
                        onFail(errorMsg)
                    }
                )
            }
        )
    }
}

@androidx.compose.runtime.Composable
fun PremiumStatusBanner(accentColor: androidx.compose.ui.graphics.Color) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = context.getSharedPreferences("premium_prefs", android.content.Context.MODE_PRIVATE)
    val isPermanent = prefs.getBoolean("is_permanent", false)
    val expiryTime = prefs.getLong("subscription_expiry", 0L)

    val isPremiumPlus = prefs.getBoolean("is_premium_plus", false)
    val planName = if (isPremiumPlus) "Premium Plus" else "Premium"

    val statusText = if (isPermanent) {
        "Lifetime $planName Activated"
    } else {
        val remainingMillis = expiryTime - System.currentTimeMillis()
        val remainingDays = (remainingMillis / (1000L * 60 * 60 * 24)).coerceAtLeast(0)
        "$planName Active: $remainingDays Days Left"
    }

    androidx.compose.foundation.layout.Row(
        modifier = androidx.compose.ui.Modifier
            .fillMaxWidth()
            .background(accentColor.copy(alpha = 0.1f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        androidx.compose.material3.Icon(
            androidx.compose.material.icons.Icons.Filled.WorkspacePremium,
            contentDescription = null,
            tint = accentColor,
            modifier = androidx.compose.ui.Modifier.size(18.dp)
        )
        androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.width(8.dp))
        androidx.compose.material3.Text(
            text = statusText,
            color = accentColor,
            fontSize = 12.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            modifier = androidx.compose.ui.Modifier.weight(1f, fill = false),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}
@Composable
fun FoldersTabContent(
    folders: List<Pair<String, List<com.musicplayer.lytrix.model.Song>>>,
    currentSong: com.musicplayer.lytrix.model.Song?,
    isPlaying: Boolean,
    accentColor: androidx.compose.ui.graphics.Color,
    currentTheme: String,
    themeFont: androidx.compose.ui.text.font.FontFamily,
    onSongClick: (com.musicplayer.lytrix.model.Song, List<com.musicplayer.lytrix.model.Song>) -> Unit
) {
    var selectedFolder by remember { mutableStateOf<Pair<String, List<com.musicplayer.lytrix.model.Song>>?>(null) }

    val currentFolder = selectedFolder

    BackHandler(enabled = currentFolder != null) {
        selectedFolder = null
    }

    if (currentFolder == null) {
        if (folders.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No Folders Found", color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                item {
                    Text("${folders.size} Folders", fontSize = 14.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f), modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 16.dp))
                }
                items(folders) { folder ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .bouncePress()
                            .clickable { selectedFolder = folder }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Folder, contentDescription = "Folder", tint = Color(0xFFFFC107), modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = folder.first, fontFamily = themeFont, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if(isPremiumVideoThemeActive(currentTheme)) Color.White else androidx.compose.material3.MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text(text = "${folder.second.size} songs", fontFamily = themeFont, fontSize = 12.sp, color = if(isPremiumVideoThemeActive(currentTheme)) Color.White.copy(alpha=0.6f) else androidx.compose.material3.MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                        }
                    }
                }
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
                IconButton(onClick = { selectedFolder = null }) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = accentColor) }
                Text(text = currentFolder.first, fontFamily = themeFont, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = if(isPremiumVideoThemeActive(currentTheme)) Color.White else androidx.compose.material3.MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }

            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = { if (currentFolder.second.isNotEmpty()) onSongClick(currentFolder.second.random(), currentFolder.second) },
                    modifier = Modifier.weight(1f).height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(50)
                ) {
                    Icon(Icons.Rounded.Shuffle, contentDescription = null, tint = accentColor)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Shuffle", color = accentColor, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { if (currentFolder.second.isNotEmpty()) onSongClick(currentFolder.second.first(), currentFolder.second) },
                    modifier = Modifier.weight(1f).height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(50)
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = accentColor)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Play", color = accentColor, fontWeight = FontWeight.Bold)
                }
            }

            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 100.dp)) {
                items(currentFolder.second) { song ->
                    SongListItem(
                        song = song,
                        isActive = currentSong?.id == song.id,
                        isPlaying = currentSong?.id == song.id && isPlaying,
                        activeColor = accentColor,
                        onClick = { onSongClick(song, currentFolder.second) },
                        currentTheme = currentTheme,
                        themeFont = themeFont
                    )
                }
            }
        }
    }
}