package com.musicplayer.lytrix.ui
import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material3.ripple
import com.musicplayer.lytrix.ui.component.tactileSpringPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.rounded.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import com.musicplayer.lytrix.ui.library.*
import com.musicplayer.lytrix.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.musicplayer.lytrix.RetrofitInstance
import com.musicplayer.lytrix.model.JioSaavnSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import org.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.lazy.LazyListScope

// ==========================================

// 🔥 Global Cache Variables

// ==========================================
var cachedTrendingList: List<JioSaavnSong>? = null
var cachedNewReleasesList: List<JioSaavnSong>? = null
var cachedBollywoodList: List<JioSaavnSong>? = null
var cachedClassicBollywoodList: List<JioSaavnSong>? = null
var cachedArijitSinghList: List<JioSaavnSong>? = null
var cachedShreyaGhoshalList: List<JioSaavnSong>? = null
var cachedKishoreKumarList: List<JioSaavnSong>? = null
var cachedPritamList: List<JioSaavnSong>? = null
var cachedKailashKherList: List<JioSaavnSong>? = null
var cachedPartyBeatsList: List<JioSaavnSong>? = null
var cachedSadSongsList: List<JioSaavnSong>? = null
var cachedRahmanList: List<JioSaavnSong>? = null
var cachedTamilTeluguList: List<JioSaavnSong>? = null
var cachedLofiList: List<JioSaavnSong>? = null
var cachedBengaliStories: List<com.musicplayer.lytrix.PodcastEpisode>? = null
var cachedSundaySuspense: List<com.musicplayer.lytrix.PodcastEpisode>? = null
var cachedSciFiPodcasts: List<com.musicplayer.lytrix.PodcastEpisode>? = null
var cachedGhostStories: List<com.musicplayer.lytrix.PodcastEpisode>? = null
var cachedMotivationList: List<com.musicplayer.lytrix.PodcastEpisode>? = null
var cachedTrueCrimeList: List<com.musicplayer.lytrix.PodcastEpisode>? = null
var cachedHealthList: List<com.musicplayer.lytrix.PodcastEpisode>? = null
var cachedHistoryList: List<com.musicplayer.lytrix.PodcastEpisode>? = null
var cachedBusinessList: List<com.musicplayer.lytrix.PodcastEpisode>? = null
var cachedEducationList: List<com.musicplayer.lytrix.PodcastEpisode>? = null
var cachedAbMystery: List<com.musicplayer.lytrix.PodcastEpisode>? = null
var cachedAbHorror: List<com.musicplayer.lytrix.PodcastEpisode>? = null
var cachedAbRomance: List<com.musicplayer.lytrix.PodcastEpisode>? = null
var cachedAbSciFi: List<com.musicplayer.lytrix.PodcastEpisode>? = null
var cachedAbSelfHelp: List<com.musicplayer.lytrix.PodcastEpisode>? = null
var cachedAbClassics: List<com.musicplayer.lytrix.PodcastEpisode>? = null
var cachedAbFantasy: List<com.musicplayer.lytrix.PodcastEpisode>? = null
var cachedAbBiography: List<com.musicplayer.lytrix.PodcastEpisode>? = null
var cachedRadioBD: List<com.musicplayer.lytrix.PodcastEpisode>? = null
var cachedRadioPop: List<com.musicplayer.lytrix.PodcastEpisode>? = null
var cachedRadioNews: List<com.musicplayer.lytrix.PodcastEpisode>? = null
var cachedRadioChill: List<com.musicplayer.lytrix.PodcastEpisode>? = null
var cachedRadioIndia: List<com.musicplayer.lytrix.PodcastEpisode>? = null

data class MoodCacheEntry(
    val songs: List<PlayerViewModel.YtMusicTrack>,
    val playlists: List<PlayerViewModel.YtMusicTrack>,
    val podcasts: List<com.musicplayer.lytrix.PodcastEpisode>,
    val audiobooks: List<com.musicplayer.lytrix.PodcastEpisode>,
    val timestamp: Long = System.currentTimeMillis()
)
var cachedActiveMoodId: String = "auto"
val globalMoodCache = mutableMapOf<String, MoodCacheEntry>()
var globalActiveYtCover: String = ""
var globalShowOnlinePlaylistDialogForSong by androidx.compose.runtime.mutableStateOf<com.musicplayer.lytrix.model.Song?>(null)

private fun generateGradientFromTitle(title: String): List<Color> {
    val hash = title.hashCode()
    val hue1 = ((hash and 0xFF) / 255f) * 360f
    val hue2 = (((hash shr 8) and 0xFF) / 255f) * 360f
    val color1 = hsvToColor(hue1, 0.7f, 0.9f)
    val color2 = hsvToColor(hue2, 0.7f, 0.85f)
    return listOf(color1, color2)
}

fun playlistTitleGradient(title: String): List<Color> = generateGradientFromTitle(title)

private fun hsvToColor(hue: Float, saturation: Float, value: Float): Color {
    val h = hue / 60f
    val c = value * saturation
    val x = c * (1 - kotlin.math.abs((h % 2) - 1))
    val m = value - c
    val (r, g, b) = when (h.toInt()) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(red = r + m, green = g + m, blue = b + m, alpha = 1f)
}

fun Modifier.angledGradientBackground(
    colors: List<Color>,
    degrees: Float = 45f
): Modifier = this.drawBehind {
    if (colors.size < 2) return@drawBehind
    val (x, y) = size
    val gamma = kotlin.math.atan2(y, x)
    if (gamma == 0f || gamma == (kotlin.math.PI / 2).toFloat()) return@drawBehind
    val degreesNorm = (degrees % 360).let { if (it < 0) it + 360 else it }
    val alpha = (degreesNorm * kotlin.math.PI / 180).toFloat()
    val gradientLength = when (alpha) {
        in 0f..gamma, in (2 * kotlin.math.PI.toFloat() - gamma)..2 * kotlin.math.PI.toFloat() -> x / kotlin.math.cos(alpha)
        in gamma..(kotlin.math.PI.toFloat() - gamma) -> y / kotlin.math.sin(alpha)
        in (kotlin.math.PI.toFloat() - gamma)..(kotlin.math.PI.toFloat() + gamma) -> x / -kotlin.math.cos(alpha)
        in (kotlin.math.PI.toFloat() + gamma)..(2 * kotlin.math.PI.toFloat() - gamma) -> y / -kotlin.math.sin(alpha)
        else -> kotlin.math.hypot(x, y)
    }
    val centerOffsetX = kotlin.math.cos(alpha) * gradientLength / 2
    val centerOffsetY = kotlin.math.sin(alpha) * gradientLength / 2
    drawRect(
        brush = Brush.linearGradient(
            colors = colors,
            start = Offset(center.x - centerOffsetX, center.y - centerOffsetY),
            end = Offset(center.x + centerOffsetX, center.y + centerOffsetY)
        )
    )
}

@Composable
fun SimpMoodCategoryCard(
    title: String,
    artworkUrl: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(2f)
            .clip(RoundedCornerShape(10.dp))
            .angledGradientBackground(
                colors = playlistTitleGradient(title),
                degrees = 45f
            )
            .clickable(onClick = onClick)
    ) {
        if (!artworkUrl.isNullOrBlank()) {
            AsyncImage(
                model = artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 8.dp, y = 12.dp)
                    .size(64.dp)
                    .rotate(25f)
                    .clip(RoundedCornerShape(2.dp))
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            ),
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 68.dp)
        )
    }
}
private val bottomListPadding: androidx.compose.ui.unit.Dp

    @Composable
    get() {
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        return if (isLandscape) 72.dp else 120.dp
    }
@OptIn(ExperimentalMaterial3Api::class)

@Composable

fun OnlineMusicScreen(viewModel: PlayerViewModel, onClose: () -> Unit = {}, onNavigateToListenTogether: () -> Unit = {}, onNavigateToFriends: () -> Unit = {}, onNavigateToProfile: () -> Unit = {}, onNavigateToArtist: (String) -> Unit = {}, onNavigateToOnlineSettings: () -> Unit = {}) {
    val navController = androidx.navigation.compose.rememberNavController()
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val currentPlayingSong by viewModel.currentSong.collectAsState()
    val hasActiveMiniPlayer = currentPlayingSong != null
    val navBarInset = androidx.compose.foundation.layout.WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val floatingBarBottomPadding = if (isLandscape) {
        if (hasActiveMiniPlayer) 68.dp else 12.dp
    } else {
        if (hasActiveMiniPlayer) 90.dp else (navBarInset + 16.dp).coerceAtLeast(16.dp)
    }
    val bottomListPadding = if (isLandscape) {
        if (hasActiveMiniPlayer) 128.dp else 72.dp
    } else {
        if (hasActiveMiniPlayer) 162.dp else (navBarInset + 104.dp).coerceAtLeast(104.dp)
    }
    var currentBottomTab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("HOME") }
    var currentLibraryView by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("MAIN") }
    var exploreTopChip by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("YouTube Music") }
    var selectedMoodChip by remember { mutableStateOf<com.musicplayer.lytrix.innertube.pages.HomePage.Chip?>(null) }

    // 🔥 Search Screen States
    var currentSearchTab by remember { mutableStateOf("EXPLORE") }
    var searchFilterContext by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchLoading by remember { mutableStateOf(false) }
    val shazamState by viewModel.shazamState.collectAsState()
    val isShazamActive = shazamState !is com.musicplayer.lytrix.shazam.RecognitionStatus.Ready

    // 🔥 FAB & TopBar Animation State
    var isFabExpanded by remember { mutableStateOf(true) }
    var isTopBarVisible by remember { mutableStateOf(true) }
    val nestedScrollConnection = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): Offset {
                if (available.y < -15) {
                    isFabExpanded = false
                    isTopBarVisible = false
                }
                if (available.y > 15) {
                    isFabExpanded = true
                    isTopBarVisible = true
                }
                return Offset.Zero
            }
        }
    }
    var refreshTriggerLocal by remember { mutableStateOf(0) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showProfileSheet by remember { mutableStateOf(false) }
    var showDownloadsScreen by remember { mutableStateOf(false) }
    var showHistoryScreen by remember { mutableStateOf(false) }
    var showPremiumDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // 🔥 Bottom Sheets
    var showBottomSheet by remember { mutableStateOf(false) }
    var selectedBottomSong by remember { mutableStateOf<JioSaavnSong?>(null) }
    var selectedBottomPodcast by remember { mutableStateOf<com.musicplayer.lytrix.PodcastEpisode?>(null) }
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var detailsSong by remember { mutableStateOf<JioSaavnSong?>(null) }
    detailsSong?.let { YtSongDetailsSheet(song = it, onDismiss = { detailsSong = null }) }

    // 🔥 ম্যাজিক: একাধিক আর্টিস্টের ডায়ালগ দেখানোর স্টেট
    var artistsListForDialog by remember { mutableStateOf<List<String>?>(null) }
    var showYtPlaylistBottomSheet by remember { mutableStateOf(false) }
    var selectedYtPlaylist by remember { mutableStateOf<PlayerViewModel.YtMusicTrack?>(null) }
    val ytPlaylistBottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var selectedPlaylistName by remember { mutableStateOf("") }
    var showPlaylistDialogForSong by remember { mutableStateOf<JioSaavnSong?>(null) }
    var newPlaylistName by remember { mutableStateOf("") }
    var showCreateShowsPlaylistDialog by remember { mutableStateOf(false) }
    var showAddToShowsPlaylistDialog by remember { mutableStateOf<JioSaavnSong?>(null) }
    var newShowsPlaylistName by remember { mutableStateOf("") }
    val podcastState by viewModel.podcastState.collectAsState()
    val audiobookState by viewModel.audiobookState.collectAsState()
    val radioState by viewModel.radioState.collectAsState()
    var trendingList by remember { mutableStateOf(cachedTrendingList ?: emptyList()) }
    var newReleasesList by remember { mutableStateOf(cachedNewReleasesList ?: emptyList()) }
    var bollywoodList by remember { mutableStateOf(cachedBollywoodList ?: emptyList()) }
    var classicBollywoodList by remember { mutableStateOf(cachedClassicBollywoodList ?: emptyList()) }
    var arijitSinghList by remember { mutableStateOf(cachedArijitSinghList ?: emptyList()) }
    var shreyaGhoshalList by remember { mutableStateOf(cachedShreyaGhoshalList ?: emptyList()) }
    var kishoreKumarList by remember { mutableStateOf(cachedKishoreKumarList ?: emptyList()) }
    var pritamList by remember { mutableStateOf(cachedPritamList ?: emptyList()) }
    var kailashKherList by remember { mutableStateOf(cachedKailashKherList ?: emptyList()) }
    var partyBeatsList by remember { mutableStateOf(cachedPartyBeatsList ?: emptyList()) }
    var sadSongsList by remember { mutableStateOf(cachedSadSongsList ?: emptyList()) }
    var rahmanList by remember { mutableStateOf(cachedRahmanList ?: emptyList()) }
    var tamilTeluguList by remember { mutableStateOf(cachedTamilTeluguList ?: emptyList()) }
    var lofiList by remember { mutableStateOf(cachedLofiList ?: emptyList()) }
    var bengaliStories by remember { mutableStateOf(cachedBengaliStories ?: emptyList()) }
    var sundaySuspense by remember { mutableStateOf(cachedSundaySuspense ?: emptyList()) }
    var sciFiPodcasts by remember { mutableStateOf(cachedSciFiPodcasts ?: emptyList()) }
    var ghostStories by remember { mutableStateOf(cachedGhostStories ?: emptyList()) }
    var motivationList by remember { mutableStateOf(cachedMotivationList ?: emptyList()) }
    var trueCrimeList by remember { mutableStateOf(cachedTrueCrimeList ?: emptyList()) }
    var healthList by remember { mutableStateOf(cachedHealthList ?: emptyList()) }
    var historyList by remember { mutableStateOf(cachedHistoryList ?: emptyList()) }
    var businessList by remember { mutableStateOf(cachedBusinessList ?: emptyList()) }
    var educationList by remember { mutableStateOf(cachedEducationList ?: emptyList()) }
    var abMystery by remember { mutableStateOf(cachedAbMystery ?: emptyList()) }
    var abHorror by remember { mutableStateOf(cachedAbHorror ?: emptyList()) }
    var abRomance by remember { mutableStateOf(cachedAbRomance ?: emptyList()) }
    var abSciFi by remember { mutableStateOf(cachedAbSciFi ?: emptyList()) }
    var abSelfHelp by remember { mutableStateOf(cachedAbSelfHelp ?: emptyList()) }
    var abClassics by remember { mutableStateOf(cachedAbClassics ?: emptyList()) }
    var abFantasy by remember { mutableStateOf(cachedAbFantasy ?: emptyList()) }
    var abBiography by remember { mutableStateOf(cachedAbBiography ?: emptyList()) }
    var radioBD by remember { mutableStateOf(cachedRadioBD ?: emptyList()) }
    var radioPop by remember { mutableStateOf(cachedRadioPop ?: emptyList()) }
    var radioNews by remember { mutableStateOf(cachedRadioNews ?: emptyList()) }
    var radioChill by remember { mutableStateOf(cachedRadioChill ?: emptyList()) }
    var radioIndia by remember { mutableStateOf(cachedRadioIndia ?: emptyList()) }
    var activeMood by remember { mutableStateOf(AtmosphereMood.fromId(cachedActiveMoodId)) }
    val initialMoodInfo = getEffectiveMoodInfo(activeMood)
    val initialCacheKey = atmosphereCacheKey(activeMood, initialMoodInfo)
    var moodSongs by remember { mutableStateOf(globalMoodCache[initialCacheKey]?.songs ?: emptyList()) }
    var moodPlaylists by remember { mutableStateOf(globalMoodCache[initialCacheKey]?.playlists ?: emptyList()) }
    var moodPodcasts by remember { mutableStateOf(globalMoodCache[initialCacheKey]?.podcasts ?: emptyList()) }
    var moodAudiobooks by remember { mutableStateOf(globalMoodCache[initialCacheKey]?.audiobooks ?: emptyList()) }
    var isAtmosphereLoading by remember { mutableStateOf(false) }
    val archiveToonMoods by viewModel.archiveToonMoods.collectAsState()
    val homeYtMusicSections by viewModel.homeYtMusicSections.collectAsState()
    val accountPlaylists by viewModel.accountPlaylists.collectAsState()
    val speedDialItems by viewModel.speedDialItems.collectAsState()
    val pinnedIds by viewModel.pinnedIds.collectAsState()
    val notInterestedSongIds by viewModel.notInterestedSongIds.collectAsState()
    val exploreYtMusicSections by viewModel.exploreYtMusicSections.collectAsState()
    val exploreChipFeeds by viewModel.exploreChipFeeds.collectAsState()
    val isLoadingExploreChipFeed by viewModel.isLoadingExploreChipFeed.collectAsState()
    val hasMoreExploreChipFeeds by viewModel.hasMoreExploreChipFeeds.collectAsState()
    val featuredCards by viewModel.featuredCards.collectAsState()
    val explorePageMoods by viewModel.explorePageMoods.collectAsState()
    val exploreMoodSections by viewModel.exploreMoodSections.collectAsState()
    val moodArtworks by viewModel.moodArtworks.collectAsState()
    val exploreCategoryPlaylists by viewModel.exploreCategoryPlaylists.collectAsState()
    var selectedExploreCategoryName by remember { mutableStateOf<String?>(null) }
    var activeMetroSection by remember { mutableStateOf<MetroSectionNavPayload?>(null) }
    var activeSaavnSection by remember { mutableStateOf<com.musicplayer.lytrix.saavn.SaavnHomeSection?>(null) }
    var activeExploreMoodTitle by remember { mutableStateOf<String?>(null) }
    val exploreSuggestions by viewModel.exploreSuggestions.collectAsState()
    val ytPlaylistDetails by viewModel.ytPlaylistDetails.collectAsState()
    val isYtPlaylistLoading by viewModel.isYtPlaylistLoading.collectAsState()
    val ytPlaylistType by viewModel.ytPlaylistType.collectAsState() // 🔥 Type Added
    val context = LocalContext.current
    var saavnLanguageKey by remember {
        mutableStateOf(com.musicplayer.lytrix.saavn.JioSaavnLiveRepository.getSelectedLanguage(context))
    }
    var saavnHomeData by remember {
        mutableStateOf(com.musicplayer.lytrix.saavn.JioSaavnLiveRepository.getCachedHomeData(context, saavnLanguageKey))
    }
    var isSaavnLoading by remember { mutableStateOf(false) }
    var isSaavnRefreshing by remember { mutableStateOf(false) }
    var activeSaavnCollection by remember { mutableStateOf<com.musicplayer.lytrix.saavn.SaavnCardItem?>(null) }
    var activeSaavnCollectionDetails by remember { mutableStateOf<com.musicplayer.lytrix.saavn.SaavnCollectionDetails?>(null) }
    var isSaavnCollectionLoading by remember { mutableStateOf(false) }
    var podcastTopicKey by remember { mutableStateOf("all") }
    var podcastFeedData by remember {
        mutableStateOf(
            com.musicplayer.lytrix.media.MediaHubRepository.getCachedFeed(
                context,
                com.musicplayer.lytrix.media.MediaHubKind.PODCAST,
                "all",
            )
        )
    }
    var isPodcastHubLoading by remember { mutableStateOf(false) }
    var isPodcastHubRefreshing by remember { mutableStateOf(false) }
    var audiobookTopicKey by remember { mutableStateOf("all") }
    var audiobookFeedData by remember {
        mutableStateOf(
            com.musicplayer.lytrix.media.MediaHubRepository.getCachedFeed(
                context,
                com.musicplayer.lytrix.media.MediaHubKind.AUDIOBOOK,
                "all",
            )
        )
    }
    var isAudiobookHubLoading by remember { mutableStateOf(false) }
    var isAudiobookHubRefreshing by remember { mutableStateOf(false) }
    var activeMediaHubSection by remember { mutableStateOf<com.musicplayer.lytrix.media.MediaHubSection?>(null) }
    var activeMediaHubCollectionCard by remember { mutableStateOf<com.musicplayer.lytrix.media.MediaHubCard?>(null) }
    var activeMediaHubCollectionDetails by remember { mutableStateOf<com.musicplayer.lytrix.media.MediaHubCollectionDetails?>(null) }
    var isMediaHubCollectionLoading by remember { mutableStateOf(false) }
    var selectedMediaHubSheetCard by remember { mutableStateOf<com.musicplayer.lytrix.media.MediaHubCard?>(null) }
    var selectedMediaHubSheetQueue by remember { mutableStateOf<List<com.musicplayer.lytrix.media.MediaHubCard>>(emptyList()) }
    val bgColor = MaterialTheme.colorScheme.background
    val onBgColor = MaterialTheme.colorScheme.onBackground
    val cardBgColor = MaterialTheme.colorScheme.surfaceContainer
    val accentColor = MaterialTheme.colorScheme.primary
    val podcastDao = remember { com.musicplayer.lytrix.db.AppDatabase.getDatabase(context).podcastDao() }
    val downloadedPodcasts by podcastDao.getAllDownloadedPodcasts().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    DisposableEffect(Unit) { onDispose { BannerCache.clear() } }
    var isSearchScreenOpen by remember { mutableStateOf(false) } // 🔥 নতুন লাইন
    val hideBars = (currentBottomTab == "SEARCH" && selectedExploreCategoryName != null) ||
            (currentBottomTab == "LIBRARY" && currentLibraryView != "MAIN") ||
            (ytPlaylistDetails != null) || (activeSaavnCollection != null) ||
            (activeMetroSection != null) || (activeSaavnSection != null) || (activeExploreMoodTitle != null) ||
            (activeMediaHubSection != null) || (activeMediaHubCollectionCard != null) ||
            isSearchScreenOpen || isShazamActive || showDownloadsScreen || showHistoryScreen
    BackHandler(enabled = currentBottomTab == "HOME" && selectedMoodChip != null && !hideBars) {
        selectedMoodChip = null
        viewModel.loadArchiveToonData(null)
    }
    BackHandler(enabled = currentBottomTab == "LIBRARY" && currentLibraryView != "MAIN" && ytPlaylistDetails == null) {
        currentLibraryView = "MAIN"
        viewModel.ytPlaylistType.value = "Playlist"
    }
    BackHandler(enabled = currentBottomTab != "HOME" && !hideBars) {
        if (currentBottomTab == "EXPLORE") {
            exploreTopChip = "YouTube Music"
        }
        currentBottomTab = "HOME"
    }
    suspend fun fetchRadioStationsSafely(country: String? = null, tag: String? = null): List<com.musicplayer.lytrix.PodcastEpisode> {
        val parsedRadios = mutableListOf<com.musicplayer.lytrix.PodcastEpisode>()
        try {
            withContext(Dispatchers.IO) {
                val baseUrl = "https://de1.api.radio-browser.info/json/stations/search"
                val queryParam = if (country != null) "?country=${java.net.URLEncoder.encode(country, "UTF-8")}&limit=30" else "?tag=${java.net.URLEncoder.encode(tag ?: "", "UTF-8")}&limit=30"
                val url = java.net.URL(baseUrl + queryParam)
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "LytrixPlayer/1.0")
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val resultsArray = org.json.JSONArray(response)
                    for (i in 0 until resultsArray.length()) {
                        try {
                            val radioObj = resultsArray.getJSONObject(i)
                            val name = radioObj.optString("name", "Unknown Station").trim()
                            val urlResolved = radioObj.optString("url_resolved", "")
                            var favicon = radioObj.optString("favicon", "https://cdn-icons-png.flaticon.com/512/125/125132.png")
                            val tags = radioObj.optString("tags", "Live Radio")
                            val stationUuid = radioObj.optString("stationuuid", System.currentTimeMillis().toString())
                            if (urlResolved.isNotBlank() && name.isNotBlank()) {
                                parsedRadios.add(com.musicplayer.lytrix.PodcastEpisode(stationUuid, name, urlResolved, favicon, 0, com.musicplayer.lytrix.PodcastInfo(tags)))
                            }
                        } catch (e: Exception) { continue }
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return parsedRadios
    }

    fun loadSaavnLiveHome(langKey: String = saavnLanguageKey, forceRefresh: Boolean = false) {
        val cached = com.musicplayer.lytrix.saavn.JioSaavnLiveRepository.getCachedHomeData(context, langKey)
            ?: com.musicplayer.lytrix.saavn.JioSaavnLiveRepository.getCachedHomeData(context, "all")
        if (cached != null) {
            saavnHomeData = cached
            if (cached.trendingSongs.isNotEmpty()) {
                trendingList = cached.trendingSongs
                cachedTrendingList = cached.trendingSongs
            }
            if (cached.newReleaseSongs.isNotEmpty()) {
                newReleasesList = cached.newReleaseSongs
                cachedNewReleasesList = cached.newReleaseSongs
            }
        }
        if (!forceRefresh && com.musicplayer.lytrix.saavn.JioSaavnLiveRepository.isCacheFresh(cached)) {
            isSaavnLoading = false
            isSaavnRefreshing = false
            viewModel.setExploreState(PlayerViewModel.OnlineUiState.Success)
            return
        }
        if (cached == null) isSaavnLoading = true
        scope.launch(Dispatchers.IO) {
            try {
                val fresh = com.musicplayer.lytrix.saavn.JioSaavnLiveRepository.fetchHomeData(
                    context = context,
                    languageKey = langKey,
                    forceRefresh = forceRefresh,
                )
                withContext(Dispatchers.Main) {
                    if (fresh != null) {
                        if (saavnLanguageKey == langKey || saavnHomeData == null) {
                            saavnHomeData = fresh
                        }
                        if (fresh.trendingSongs.isNotEmpty()) {
                            trendingList = fresh.trendingSongs
                            cachedTrendingList = fresh.trendingSongs
                        }
                        if (fresh.newReleaseSongs.isNotEmpty()) {
                            newReleasesList = fresh.newReleaseSongs
                            cachedNewReleasesList = fresh.newReleaseSongs
                        }
                        viewModel.setExploreState(PlayerViewModel.OnlineUiState.Success)
                    } else if (saavnHomeData == null) {
                        viewModel.setExploreState(PlayerViewModel.OnlineUiState.Error("Server is busy or temporarily closed.", false))
                    }
                    isSaavnLoading = false
                    isSaavnRefreshing = false
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    isSaavnLoading = false
                    isSaavnRefreshing = false
                }
            }
        }
    }

    fun openSaavnCollection(card: com.musicplayer.lytrix.saavn.SaavnCardItem) {
        activeSaavnCollection = card
        activeSaavnCollectionDetails = null
        isSaavnCollectionLoading = true
        scope.launch(Dispatchers.IO) {
            val details = com.musicplayer.lytrix.saavn.JioSaavnLiveRepository.fetchCollectionDetails(
                id = card.id,
                type = card.type,
                titleHint = card.title,
                imageHint = card.imageUrl,
            )
            withContext(Dispatchers.Main) {
                if (activeSaavnCollection?.id == card.id) {
                    activeSaavnCollectionDetails = details
                    isSaavnCollectionLoading = false
                    if (details == null || details.songs.isEmpty()) {
                        Toast.makeText(context, "Could not load tracks", Toast.LENGTH_SHORT).show()
                        activeSaavnCollection = null
                    }
                }
            }
        }
    }

    fun loadPodcastHub(topicKey: String = podcastTopicKey, forceRefresh: Boolean = false) {
        val cached = com.musicplayer.lytrix.media.MediaHubRepository.getCachedFeed(
            context,
            com.musicplayer.lytrix.media.MediaHubKind.PODCAST,
            topicKey,
        )
        if (cached != null) {
            podcastFeedData = cached
            viewModel.setPodcastState(PlayerViewModel.OnlineUiState.Success)
        }
        if (!forceRefresh && com.musicplayer.lytrix.media.MediaHubRepository.isCacheFresh(cached)) {
            isPodcastHubLoading = false
            isPodcastHubRefreshing = false
            return
        }
        if (cached == null) isPodcastHubLoading = true
        scope.launch(Dispatchers.IO) {
            try {
                val fresh = com.musicplayer.lytrix.media.MediaHubRepository.fetchPodcastFeed(
                    context = context,
                    topicKey = topicKey,
                    forceRefresh = forceRefresh,
                )
                withContext(Dispatchers.Main) {
                    if (podcastTopicKey == topicKey) {
                        if (fresh != null && fresh.sections.isNotEmpty()) {
                            podcastFeedData = fresh
                            viewModel.setPodcastState(PlayerViewModel.OnlineUiState.Success)
                        } else if (podcastFeedData == null) {
                            viewModel.setPodcastState(PlayerViewModel.OnlineUiState.Error("No podcasts loaded. Pull to retry.", false))
                        }
                    }
                    isPodcastHubLoading = false
                    isPodcastHubRefreshing = false
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    isPodcastHubLoading = false
                    isPodcastHubRefreshing = false
                }
            }
        }
    }

    fun loadAudiobookHub(topicKey: String = audiobookTopicKey, forceRefresh: Boolean = false) {
        val cached = com.musicplayer.lytrix.media.MediaHubRepository.getCachedFeed(
            context,
            com.musicplayer.lytrix.media.MediaHubKind.AUDIOBOOK,
            topicKey,
        )
        if (cached != null) {
            audiobookFeedData = cached
            viewModel.setAudiobookState(PlayerViewModel.OnlineUiState.Success)
        }
        if (!forceRefresh && com.musicplayer.lytrix.media.MediaHubRepository.isCacheFresh(cached)) {
            isAudiobookHubLoading = false
            isAudiobookHubRefreshing = false
            return
        }
        if (cached == null) isAudiobookHubLoading = true
        scope.launch(Dispatchers.IO) {
            try {
                val fresh = com.musicplayer.lytrix.media.MediaHubRepository.fetchAudiobookFeed(
                    context = context,
                    topicKey = topicKey,
                    forceRefresh = forceRefresh,
                )
                withContext(Dispatchers.Main) {
                    if (audiobookTopicKey == topicKey) {
                        if (fresh != null && fresh.sections.isNotEmpty()) {
                            audiobookFeedData = fresh
                            viewModel.setAudiobookState(PlayerViewModel.OnlineUiState.Success)
                        } else if (audiobookFeedData == null) {
                            viewModel.setAudiobookState(PlayerViewModel.OnlineUiState.Error("No audiobooks loaded. Pull to retry.", false))
                        }
                    }
                    isAudiobookHubLoading = false
                    isAudiobookHubRefreshing = false
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    isAudiobookHubLoading = false
                    isAudiobookHubRefreshing = false
                }
            }
        }
    }

    fun openMediaHubCollection(card: com.musicplayer.lytrix.media.MediaHubCard) {
        activeMediaHubCollectionCard = card
        activeMediaHubCollectionDetails = null
        isMediaHubCollectionLoading = true
        scope.launch(Dispatchers.IO) {
            val details = com.musicplayer.lytrix.media.MediaHubRepository.fetchCollectionDetails(card)
            withContext(Dispatchers.Main) {
                if (activeMediaHubCollectionCard?.id == card.id) {
                    activeMediaHubCollectionDetails = details
                    isMediaHubCollectionLoading = false
                    if (details == null || details.items.isEmpty()) {
                        Toast.makeText(context, "Could not load episodes", Toast.LENGTH_SHORT).show()
                        activeMediaHubCollectionCard = null
                    }
                }
            }
        }
    }

    fun handleMediaHubCardClick(
        card: com.musicplayer.lytrix.media.MediaHubCard,
        sectionCards: List<com.musicplayer.lytrix.media.MediaHubCard>,
    ) {
        if (card.isCollection) {
            openMediaHubCollection(card)
        } else {
            val ep = card.toPodcastEpisode()
            val queue = sectionCards.filter { !it.isCollection }.map { it.toPodcastEpisode() }
            playUnifiedMediaEpisode(context, viewModel, ep, queue.ifEmpty { listOf(ep) })
        }
    }

    fun triggerInitialLoad() {
        CoroutineScope(Dispatchers.IO).launch {
            viewModel.loadExploreMoods()
            viewModel.loadExploreSuggestions(context)
            withContext(Dispatchers.Main) {
                if (viewModel.checkNetworkStatus(context)) {
                    loadSaavnLiveHome(saavnLanguageKey, forceRefresh = false)
                } else if (saavnHomeData == null) {
                    viewModel.setExploreState(PlayerViewModel.OnlineUiState.Error("No Internet Connection,\nPlease Check Your Connection.", true))
                }
            }
            if (cachedRadioBD == null || viewModel.radioState.value is PlayerViewModel.OnlineUiState.Error) {
                viewModel.setRadioState(PlayerViewModel.OnlineUiState.Loading)
                if (viewModel.checkNetworkStatus(context)) {
                    try {
                        radioBD = fetchRadioStationsSafely(country = "Bangladesh").shuffled().take(15); cachedRadioBD = radioBD
                        viewModel.setRadioState(PlayerViewModel.OnlineUiState.Success)
                        launch {
                            delay(500); radioPop = fetchRadioStationsSafely(tag = "pop").shuffled().take(15); cachedRadioPop = radioPop
                            delay(1000); radioNews = fetchRadioStationsSafely(tag = "news").take(15); cachedRadioNews = radioNews
                            delay(1500); radioChill = fetchRadioStationsSafely(tag = "chill").shuffled().take(15); cachedRadioChill = radioChill
                            delay(2000); radioIndia = fetchRadioStationsSafely(country = "India").shuffled().take(15); cachedRadioIndia = radioIndia
                        }
                    } catch (e: Exception) { viewModel.setRadioState(PlayerViewModel.OnlineUiState.Error("Server is busy.", false)) }
                } else { viewModel.setRadioState(PlayerViewModel.OnlineUiState.Error("No Internet.", true)) }
            }
        }
    }

    fun loadAtmosphereMood(mood: AtmosphereMood, force: Boolean = false) {
        activeMood = mood
        cachedActiveMoodId = mood.id
        val moodInfo = getEffectiveMoodInfo(mood)
        val cacheKey = atmosphereCacheKey(mood, moodInfo)
        val cached = globalMoodCache[cacheKey]
        val now = System.currentTimeMillis()
        if (!force && cached != null && (now - cached.timestamp < 30 * 60 * 1000L)) {
            moodSongs = cached.songs
            moodPlaylists = cached.playlists
            moodPodcasts = cached.podcasts
            moodAudiobooks = cached.audiobooks
            isAtmosphereLoading = false
            return
        }
        isAtmosphereLoading = true
        scope.launch(Dispatchers.IO) {
            try {
                // 1. YouTube Music Songs
                val searchSongs = com.musicplayer.lytrix.innertube.YouTube.search(
                    moodInfo.songQuery,
                    com.musicplayer.lytrix.innertube.YouTube.SearchFilter.FILTER_SONG,
                    useAccountContext = false
                ).getOrNull()
                val songs = searchSongs?.items?.filterIsInstance<com.musicplayer.lytrix.innertube.models.SongItem>()?.map { item ->
                    PlayerViewModel.YtMusicTrack(
                        id = item.id,
                        title = item.title,
                        artist = item.artists?.joinToString(", ") { it.name } ?: "Unknown",
                        thumbnailUrl = item.thumbnail ?: "",
                        playCount = item.viewCountText ?: "",
                        itemType = "Song"
                    )
                }?.take(15) ?: emptyList()

                // 2. YouTube Music Playlists
                val searchPlaylists = com.musicplayer.lytrix.innertube.YouTube.search(
                    moodInfo.playlistQuery,
                    com.musicplayer.lytrix.innertube.YouTube.SearchFilter.FILTER_FEATURED_PLAYLIST,
                    useAccountContext = false
                ).getOrNull()
                var playlists = searchPlaylists?.items?.filterIsInstance<com.musicplayer.lytrix.innertube.models.PlaylistItem>()?.map { item ->
                    PlayerViewModel.YtMusicTrack(
                        id = item.id,
                        title = item.title,
                        artist = "Playlist • YouTube Music",
                        thumbnailUrl = item.thumbnail ?: "",
                        itemType = "Playlist"
                    )
                }?.take(10) ?: emptyList()
                if (playlists.isEmpty()) {
                    val searchCommunity = com.musicplayer.lytrix.innertube.YouTube.search(
                        moodInfo.playlistQuery,
                        com.musicplayer.lytrix.innertube.YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST,
                        useAccountContext = false
                    ).getOrNull()
                    playlists = searchCommunity?.items?.filterIsInstance<com.musicplayer.lytrix.innertube.models.PlaylistItem>()?.map { item ->
                        PlayerViewModel.YtMusicTrack(
                            id = item.id,
                            title = item.title,
                            artist = "Playlist • YouTube Music",
                            thumbnailUrl = item.thumbnail ?: "",
                            itemType = "Playlist"
                        )
                    }?.take(10) ?: emptyList()
                }

                // 3. Apple iTunes Podcasts
                val pods = fetchPodcastsSafely(moodInfo.podcastQuery).shuffled().take(10)

                // 4. Apple iTunes Audiobooks
                val audios = fetchPodcastsSafely("${moodInfo.audiobookQuery} Audiobook").shuffled().take(10)
                withContext(Dispatchers.Main) {
                    moodSongs = songs
                    moodPlaylists = playlists
                    moodPodcasts = pods
                    moodAudiobooks = audios
                    globalMoodCache[cacheKey] = MoodCacheEntry(
                        songs = songs,
                        playlists = playlists,
                        podcasts = pods,
                        audiobooks = audios
                    )
                    isAtmosphereLoading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isAtmosphereLoading = false
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        viewModel.initVisitorData(context)
        try { performSilentSync(context) } catch (e: Exception) {}
        triggerInitialLoad()
        viewModel.loadArchiveToonData(null)
    }
    val liveAtmosphereInfo = getEffectiveMoodInfo(activeMood)
    LaunchedEffect(activeMood, liveAtmosphereInfo.phase, liveAtmosphereInfo.title) {
        val currentKey = atmosphereCacheKey(activeMood, liveAtmosphereInfo)
        val cached = globalMoodCache[currentKey]
        val now = System.currentTimeMillis()
        if (cached != null && (now - cached.timestamp < 30 * 60 * 1000L)) {
            moodSongs = cached.songs
            moodPlaylists = cached.playlists
            moodPodcasts = cached.podcasts
            moodAudiobooks = cached.audiobooks
        } else {
            loadAtmosphereMood(activeMood)
        }
    }

    // 🔥 Shazam এর জন্য মাইক্রোফোন পারমিশন লাঞ্চার
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startShazamRecognition(context)
        } else {
            Toast.makeText(context, "Microphone permission required for Music Recognition", Toast.LENGTH_SHORT).show()
        }
    }
    val pendingRecognitionLaunch by viewModel.pendingRecognitionLaunch.collectAsState()
    LaunchedEffect(pendingRecognitionLaunch) {
        if (pendingRecognitionLaunch) {
            showDownloadsScreen = false
            showHistoryScreen = false
            showProfileSheet = false
            isSearchScreenOpen = false
            currentBottomTab = "EXPLORE"
            viewModel.consumeRecognitionFromTile()
            val hasMicPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (hasMicPermission) {
                viewModel.startShazamRecognition(context)
            } else {
                micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
            }
        }
    }
    val handleYtTrackMore: (PlayerViewModel.YtMusicTrack) -> Unit = { track ->
        val isEpisode = track.itemType == "Episode"
        val isPodcast = track.itemType == "Podcast" || (!isEpisode && (track.id.startsWith("MPSP") || track.artist.startsWith("Podcast")))
        val isAlbum = track.itemType == "Album" || (!isEpisode && !isPodcast && track.artist.contains("Album", ignoreCase = true))
        val isPlaylistOrAlbum = !isEpisode && (isAlbum || isPodcast || track.artist.contains("Playlist", ignoreCase = true) || track.artist.contains("Station", ignoreCase = true))
        if (isPlaylistOrAlbum) {
            selectedYtPlaylist = track
            viewModel.ytPlaylistType.value = if (isAlbum) "Album" else if (isPodcast) "Podcast" else "Playlist"
            showYtPlaylistBottomSheet = true
        } else {
            val isAudiobook = viewModel.currentMediaCategory.value == "AUDIOBOOK"
            val isPod = isEpisode || viewModel.currentMediaCategory.value == "PODCAST"
            selectedBottomSong = JioSaavnSong(
                id = track.id,
                name = track.title,
                artist = track.artist,
                imageUrl = track.thumbnailUrl ?: "",
                downloadUrl = "",
                albumName = if (isAudiobook) "Audiobook" else if (isPod) "Podcast" else null
            )
            showBottomSheet = true
        }
    }
    val handleYtTrackTap: (PlayerViewModel.YtMusicTrack, List<PlayerViewModel.YtMusicTrack>, Int) -> Unit = { track, list, idx ->
        val isEpisode = track.itemType == "Episode"
        val isArtist = track.itemType == "Artist" || track.itemType == "Profile" ||
            track.artist.equals("Artist", ignoreCase = true) || track.artist.equals("Profile", ignoreCase = true)
        val isPodcast = track.itemType == "Podcast" || (!isEpisode && (track.id.startsWith("MPSP") || track.artist.startsWith("Podcast", ignoreCase = true)))
        val isAlbum = track.itemType == "Album" || (!isEpisode && !isPodcast && track.artist.startsWith("Album", ignoreCase = true))
        val isPlaylist = track.itemType == "Playlist" || (!isEpisode && !isPodcast && !isAlbum &&
            (track.artist.startsWith("Playlist", ignoreCase = true) || track.artist.equals("Station", ignoreCase = true)))
        when {
            isArtist -> onNavigateToArtist(track.title)
            isEpisode -> {
                val item = com.musicplayer.lytrix.LytrixSongItem(
                    videoId = track.id,
                    title = track.title,
                    artist = track.artist,
                    duration = "",
                    thumbnailUrl = track.thumbnailUrl,
                )
                viewModel.playLytrixSongWithAd(item, context)
            }
            isAlbum || isPlaylist || isPodcast -> {
                globalActiveYtCover = track.thumbnailUrl
                val colType = if (isAlbum) "Album" else if (isPodcast) "Podcast" else "Playlist"
                viewModel.ytPlaylistType.value = colType
                viewModel.loadYtPlaylistDetails(track.id, colType, track.title)
            }
            else -> {
                val songsOnly = list.filter { t ->
                    t.itemType != "Album" && t.itemType != "Playlist" && t.itemType != "Podcast" &&
                        t.itemType != "Artist" && t.itemType != "Profile" &&
                        !t.artist.startsWith("Album", ignoreCase = true) &&
                        !t.artist.startsWith("Playlist", ignoreCase = true) &&
                        !t.artist.startsWith("Podcast", ignoreCase = true) &&
                        !t.artist.equals("Artist", ignoreCase = true) &&
                        !t.artist.equals("Station", ignoreCase = true)
                }
                val safeIndex = songsOnly.indexOfFirst { it.id == track.id }.takeIf { it >= 0 } ?: 0
                viewModel.playLytrixPlaylistWithAd(songsOnly.ifEmpty { listOf(track) }, safeIndex, context)
            }
        }
    }
    val paletteAccentColor by viewModel.paletteAccentColor.collectAsState()
    val onlineAccentColor = com.musicplayer.lytrix.ui.theme.LocalAccentColor.current
    val onlineGlowTint = if (currentPlayingSong != null) paletteAccentColor else onlineAccentColor
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        com.musicplayer.lytrix.ui.component.AmbientThemeGlow(
            tint = onlineGlowTint,
            intensity = 0.20f
        )
        Scaffold(
            containerColor = Color.Transparent,
        snackbarHost = {},
        floatingActionButton = {
            AnimatedVisibility(
                visible = !hideBars && (currentBottomTab == "HOME" || currentBottomTab == "EXPLORE") && isFabExpanded && shazamState is com.musicplayer.lytrix.shazam.RecognitionStatus.Ready,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                FloatingActionButton(
                    onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            viewModel.startShazamRecognition(context)
                        } else {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    containerColor = accentColor,
                    contentColor = Color.Black,
                    shape = CircleShape,

                    // 🔥 ল্যান্ডস্কেপে নিচে ও ডানে সুন্দরভাবে ডক করা
                    modifier = Modifier.padding(
                        bottom = if (isLandscape) 20.dp else 160.dp,
                        end = if (isLandscape) 16.dp else 8.dp
                    )
                ) {
                    Icon(imageVector = com.musicplayer.lytrix.ui.icons.LytrixIcons.Rounded.Mic, contentDescription = "Recognize Music")
                }
            }
        },
        topBar = {
            AnimatedVisibility(
                visible = !hideBars && (currentBottomTab == "HOME" || currentBottomTab == "EXPLORE") && isTopBarVisible,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                LytrixTopAppBar(
                    logoResId = com.musicplayer.lytrix.R.drawable.lytrix_logo,
                    onHistoryClick = { showHistoryScreen = true },
                    onProfileClick = { showProfileSheet = true }
                )
            }
        },
        bottomBar = {}
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLandscape && !hideBars) {
                LytrixNavigationRail(
                    currentBottomTab = currentBottomTab,
                    onTabSelected = { tabId ->
                        currentBottomTab = tabId
                        if (tabId == "SEARCH") currentSearchTab = "EXPLORE"
                        if (tabId == "LIBRARY") currentLibraryView = "MAIN"
                    },
                    accentColor = accentColor,
                    modifier = Modifier.align(Alignment.CenterStart).zIndex(15f)
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = if (isLandscape && !hideBars) 72.dp else 0.dp)
            ) {
            when (currentBottomTab) {
                "HOME" -> {
                    val homeSaavnShelves = remember(newReleasesList, trendingList, saavnHomeData) {
                        val direct = listOf(
                            "New Releases" to newReleasesList,
                            "Trending Now" to trendingList
                        ).filter { it.second.isNotEmpty() }
                        if (direct.isNotEmpty()) {
                            direct
                        } else {
                            saavnHomeData?.sections.orEmpty()
                                .mapNotNull { sec ->
                                    val songs = sec.items.mapNotNull { it.song }
                                    if (songs.isNotEmpty()) sec.title to songs else null
                                }
                                .take(2)
                        }
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().nestedScroll(nestedScrollConnection),
                        contentPadding = PaddingValues(top = 0.dp, bottom = bottomListPadding)
                    ) {
                        val ads = BannerCounter()
                        if (archiveToonMoods.isNotEmpty()) {
                            item {
                                LazyRow(
                                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(archiveToonMoods.distinctBy { it.title }) { chip ->
                                        YTMusicChip(text = chip.title, isSelected = selectedMoodChip == chip) {
                                            if (selectedMoodChip == chip) {
                                                selectedMoodChip = null
                                                viewModel.loadArchiveToonData(null)
                                            } else {
                                                selectedMoodChip = chip
                                                viewModel.loadArchiveToonData(chip)
                                            }
                                        }
                                    }
                                }
                            }
                                                        if (selectedMoodChip == null) {
                            }
                            if (selectedMoodChip != null) {
                                if (homeYtMusicSections.isEmpty()) {
                                    item {
                                        LytrixHorizontalCardSectionShimmer()
                                    }
                                    items(4) {
                                        LytrixSongItemShimmer()
                                    }
                                } else {
                                    homeYtMusicSections.forEach { section ->
                                        rowWithBanner(ads) {
                                            MetroHomeShelfRenderer(
                                                section = section,
                                                viewModel = viewModel,
                                                context = context,
                                                onTrackClick = handleYtTrackTap,
                                                onTrackLongClick = handleYtTrackMore,
                                                onNavigateToArtist = onNavigateToArtist,
                                                onOpenSectionDetail = { payload -> activeMetroSection = payload },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        item {
                            AtmosphericMoodCard(
                                activeMood = activeMood,
                                isLoading = isAtmosphereLoading,
                                onMoodSelected = { selectedMood ->
                                    loadAtmosphereMood(selectedMood, force = false)
                                }
                            )
                        }
                        val currentMoodInfo = getEffectiveMoodInfo(activeMood)
                        val visibleMoodSongs = moodSongs.filterNot { notInterestedSongIds.contains(it.id) }

                        // 1. YouTube Music Mood Songs (MetroList Carousel)
                        if (visibleMoodSongs.isNotEmpty()) rowWithBanner(ads) {
                            MetroHomeShelfRenderer(
                                section = PlayerViewModel.YtMusicSection(
                                    title = "${currentMoodInfo.title} Tracks",
                                    tracks = visibleMoodSongs,
                                    subtitle = "YouTube Music",
                                ),
                                viewModel = viewModel,
                                context = context,
                                onTrackClick = handleYtTrackTap,
                                onTrackLongClick = handleYtTrackMore,
                                onNavigateToArtist = onNavigateToArtist,
                                onOpenSectionDetail = { payload -> activeMetroSection = payload },
                            )
                        }

                        // 2. YouTube Music Mood Playlists (MetroList Carousel)
                        if (moodPlaylists.isNotEmpty()) rowWithBanner(ads) {
                            MetroHomeShelfRenderer(
                                section = PlayerViewModel.YtMusicSection(
                                    title = "${currentMoodInfo.title} Playlists",
                                    tracks = moodPlaylists,
                                    subtitle = "YouTube Music Playlists",
                                ),
                                viewModel = viewModel,
                                context = context,
                                onTrackClick = handleYtTrackTap,
                                onTrackLongClick = handleYtTrackMore,
                                onNavigateToArtist = onNavigateToArtist,
                                onOpenSectionDetail = { payload -> activeMetroSection = payload },
                            )
                        }

                        // 3. Mood Podcasts (MetroList Carousel)
                        if (moodPodcasts.isNotEmpty()) {
                            val moodPodSection = com.musicplayer.lytrix.media.MediaHubSection(
                                id = "home_mood_podcasts_${currentMoodInfo.title}",
                                title = "${currentMoodInfo.title} Podcasts",
                                items = moodPodcasts.map { ep ->
                                    com.musicplayer.lytrix.media.MediaHubCard(
                                        id = ep.id,
                                        title = ep.title_original ?: "Podcast Episode",
                                        subtitle = ep.podcast?.publisher_original ?: "Podcast",
                                        imageUrl = ep.image.orEmpty(),
                                        kind = com.musicplayer.lytrix.media.MediaHubKind.PODCAST,
                                        source = if (ep.audio.orEmpty().startsWith("yt:")) com.musicplayer.lytrix.media.MediaHubSource.YOUTUBE else com.musicplayer.lytrix.media.MediaHubSource.APPLE,
                                        isCollection = false,
                                        audioUrl = ep.audio.orEmpty(),
                                        durationSec = ep.audio_length_sec,
                                    )
                                },
                            )
                            rowWithBanner(ads) {
                                MetroMediaHubCarouselSection(
                                    section = moodPodSection,
                                    onCardClick = { card -> handleMediaHubCardClick(card, moodPodSection.items) },
                                    onCardLongClick = { card ->
                                        selectedMediaHubSheetCard = card
                                        selectedMediaHubSheetQueue = moodPodSection.items
                                    },
                                    onSeeAllClick = { sec -> activeMediaHubSection = sec },
                                )
                            }
                        }

                        // 4. Mood Audiobooks (MetroList Carousel)
                        if (moodAudiobooks.isNotEmpty()) {
                            val moodAbSection = com.musicplayer.lytrix.media.MediaHubSection(
                                id = "home_mood_audiobooks_${currentMoodInfo.title}",
                                title = "${currentMoodInfo.title} Audiobooks",
                                items = moodAudiobooks.map { ep ->
                                    com.musicplayer.lytrix.media.MediaHubCard(
                                        id = ep.id,
                                        title = ep.title_original ?: "Audiobook",
                                        subtitle = ep.podcast?.publisher_original ?: "Audiobook",
                                        imageUrl = ep.image.orEmpty(),
                                        kind = com.musicplayer.lytrix.media.MediaHubKind.AUDIOBOOK,
                                        source = if (ep.audio.orEmpty().startsWith("yt:")) com.musicplayer.lytrix.media.MediaHubSource.YOUTUBE else com.musicplayer.lytrix.media.MediaHubSource.APPLE,
                                        isCollection = false,
                                        audioUrl = ep.audio.orEmpty(),
                                        durationSec = ep.audio_length_sec,
                                    )
                                },
                            )
                            rowWithBanner(ads) {
                                MetroMediaHubCarouselSection(
                                    section = moodAbSection,
                                    onCardClick = { card -> handleMediaHubCardClick(card, moodAbSection.items) },
                                    onCardLongClick = { card ->
                                        selectedMediaHubSheetCard = card
                                        selectedMediaHubSheetQueue = moodAbSection.items
                                    },
                                    onSeeAllClick = { sec -> activeMediaHubSection = sec },
                                )
                            }
                        }
                        homeSaavnShelves.forEach { (title, songs) ->
                            val homeSaavnSection = com.musicplayer.lytrix.saavn.SaavnHomeSection(
                                id = "home_$title",
                                title = title,
                                items = songs.map { s ->
                                    com.musicplayer.lytrix.saavn.SaavnCardItem(
                                        id = s.id,
                                        title = s.name,
                                        subtitle = s.artist,
                                        imageUrl = s.imageUrl,
                                        type = com.musicplayer.lytrix.saavn.SaavnItemType.SONG,
                                        song = s,
                                    )
                                },
                            )
                            rowWithBanner(ads) {
                                MetroSaavnMultiBrowseSection(
                                    section = homeSaavnSection,
                                    onItemClick = { card ->
                                        val clicked = card.song ?: return@MetroSaavnMultiBrowseSection
                                        viewModel.playOnlineSongWithAd(context, clicked, songs)
                                    },
                                    onSongMoreClick = { song ->
                                        selectedBottomSong = song
                                        showBottomSheet = true
                                    },
                                    onSeeAllClick = { sec ->
                                        activeSaavnSection = sec
                                    },
                                )
                            }
                        }
                    }
                }
                "EXPLORE" -> {
                    val exploreChipsRow: @Composable () -> Unit = {
                        val exploreChipsList =
                            listOf("YouTube Music", "JioSaavn", "Shows", "Radio")
                        LazyRow(
                            modifier = Modifier.fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(exploreChipsList) { chip ->
                                YTMusicChip(
                                    text = chip,
                                    isSelected = exploreTopChip == chip
                                ) { exploreTopChip = chip }
                            }
                        }
                    }
                    Box(modifier = Modifier.fillMaxSize()) {
                        when (exploreTopChip) {
                            "YouTube Music" -> {
                                var isYtMusicRefreshing by remember { mutableStateOf(false) }
                                val ytExploreListState = rememberLazyListState()
                                val combinedMoodItems = remember(exploreMoodSections, explorePageMoods) {
                                    (exploreMoodSections.flatMap { it.items }.ifEmpty { explorePageMoods })
                                        .distinctBy { it.title.trim().lowercase() }
                                }
                                LaunchedEffect(Unit) {
                                    if (exploreMoodSections.isEmpty() && explorePageMoods.isEmpty()) {
                                        viewModel.loadExploreMoods()
                                    }
                                }
                                val shouldLoadNextChipFeed by remember(
                                    ytExploreListState,
                                    hasMoreExploreChipFeeds,
                                    isLoadingExploreChipFeed,
                                    exploreYtMusicSections.size,
                                ) {
                                    derivedStateOf {
                                        if (!hasMoreExploreChipFeeds || isLoadingExploreChipFeed || exploreYtMusicSections.isEmpty()) {
                                            false
                                        } else {
                                            val info = ytExploreListState.layoutInfo
                                            val total = info.totalItemsCount
                                            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                                            total > 0 && lastVisible >= (total - 3).coerceAtLeast(0)
                                        }
                                    }
                                }
                                LaunchedEffect(shouldLoadNextChipFeed) {
                                    if (shouldLoadNextChipFeed) {
                                        viewModel.loadNextExploreChipFeed()
                                    }
                                }
                                androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                                    isRefreshing = isYtMusicRefreshing,
                                    onRefresh = {
                                        isYtMusicRefreshing = true
                                        viewModel.refreshExploreYtMusic {
                                            isYtMusicRefreshing = false
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    LazyColumn(
                                        state = ytExploreListState,
                                        modifier = Modifier.fillMaxSize().nestedScroll(nestedScrollConnection),
                                        contentPadding = PaddingValues(bottom = bottomListPadding)
                                    ) {
                                        item { exploreChipsRow() }
                                        if (speedDialItems.isNotEmpty()) {
                                            item {
                                                SpeedDialSection(items = speedDialItems, viewModel = viewModel, onClick = { track ->
                                                    handleYtTrackTap(track, speedDialItems, speedDialItems.indexOf(track).coerceAtLeast(0))
                                                }, onLongClick = { track ->
                                                    handleYtTrackMore(track)
                                                })
                                            }
                                        }
                                        if (featuredCards.isNotEmpty()) {
                                            item {
                                                CommunityPlaylistSection(
                                                    cards = featuredCards,
                                                    viewModel = viewModel,
                                                    context = context,
                                                    onBgColor = onBgColor,
                                                    onCardClick = { card ->
                                                        globalActiveYtCover = card.thumbnailUrl
                                                        viewModel.ytPlaylistType.value = "Playlist"
                                                        viewModel.loadYtPlaylistDetails(card.id.removePrefix("VL"), "Playlist", card.title)
                                                    },
                                                    onCardLongClick = { card ->
                                                        selectedYtPlaylist = PlayerViewModel.YtMusicTrack(card.id.removePrefix("VL"), card.title, card.subtitle, card.thumbnailUrl)
                                                        showYtPlaylistBottomSheet = true
                                                    },
                                                    onSongClick = { song, card ->
                                                        viewModel.playLytrixPlaylistWithAd(card.previewSongs, card.previewSongs.indexOf(song).takeIf { it >= 0 } ?: 0, context)
                                                    },
                                                    onSongLongClick = { song ->
                                                        selectedBottomSong = JioSaavnSong(song.id, song.title, song.artist, song.thumbnailUrl ?: "", "")
                                                        showBottomSheet = true
                                                    }
                                                )
                                            }
                                        }
                                        if (!accountPlaylists.isNullOrEmpty()) {
                                            item {
                                                MixesSection(items = accountPlaylists!!, viewModel = viewModel, onBgColor = onBgColor)
                                            }
                                        }
                                        if (exploreYtMusicSections.isEmpty()) {
                                            item { LytrixHorizontalCardSectionShimmer() }
                                            items(4) { LytrixSongItemShimmer() }
                                        } else {
                                            val ads = BannerCounter()
                                            exploreYtMusicSections.forEach { section ->
                                                rowWithBanner(ads) {
                                                    MetroHomeShelfRenderer(
                                                        section = section,
                                                        viewModel = viewModel,
                                                        context = context,
                                                        onTrackClick = handleYtTrackTap,
                                                        onTrackLongClick = handleYtTrackMore,
                                                        onNavigateToArtist = onNavigateToArtist,
                                                        onOpenSectionDetail = { payload -> activeMetroSection = payload },
                                                    )
                                                }
                                            }
                                            if (combinedMoodItems.isNotEmpty()) {
                                                item(key = "metro_moods_and_genres") {
                                                    MetroMoodsAndGenresShelf(
                                                        items = combinedMoodItems,
                                                        onItemClick = { moodItem ->
                                                            activeExploreMoodTitle = moodItem.title
                                                            viewModel.loadExploreCategory(moodItem.endpoint)
                                                        },
                                                        onSeeAllClick = {
                                                            currentBottomTab = "SEARCH"
                                                        },
                                                    )
                                                }
                                            }
                                            exploreChipFeeds.forEach { group ->
                                                item(key = "chip_group_header_${group.chipTitle}") {
                                                    MetroChipGroupDividerHeader(title = group.chipTitle)
                                                }
                                                group.sections.forEachIndexed { secIdx, sec ->
                                                    item(key = "chip_group_${group.chipTitle}_${secIdx}_${sec.title}") {
                                                        MetroHomeShelfRenderer(
                                                            section = sec,
                                                            viewModel = viewModel,
                                                            context = context,
                                                            onTrackClick = handleYtTrackTap,
                                                            onTrackLongClick = handleYtTrackMore,
                                                            onNavigateToArtist = onNavigateToArtist,
                                                            onOpenSectionDetail = { payload -> activeMetroSection = payload },
                                                        )
                                                    }
                                                }
                                            }
                                            if (isLoadingExploreChipFeed) {
                                                item(key = "chip_feed_loading") {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(vertical = 20.dp),
                                                        contentAlignment = Alignment.Center,
                                                    ) {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.size(28.dp),
                                                            strokeWidth = 2.5.dp,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            "JioSaavn" -> {
                                LaunchedEffect(saavnLanguageKey) {
                                    loadSaavnLiveHome(saavnLanguageKey, forceRefresh = false)
                                }
                                androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                                    isRefreshing = isSaavnRefreshing,
                                    onRefresh = {
                                        isSaavnRefreshing = true
                                        loadSaavnLiveHome(saavnLanguageKey, forceRefresh = true)
                                    },
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    val liveSections = saavnHomeData?.sections.orEmpty()
                                    if (isSaavnLoading && liveSections.isEmpty()) {
                                        Column(modifier = Modifier.fillMaxSize()) {
                                            exploreChipsRow()
                                            LytrixHorizontalCardSectionShimmer()
                                            LytrixHorizontalCardSectionShimmer()
                                        }
                                    } else {
                                        LazyColumn(
                                            modifier = Modifier.fillMaxSize().nestedScroll(nestedScrollConnection),
                                            contentPadding = PaddingValues(bottom = bottomListPadding)
                                        ) {
                                            item { exploreChipsRow() }
                                            item {
                                                LazyRow(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    items(com.musicplayer.lytrix.saavn.JioSaavnLiveRepository.LANGUAGE_CHIPS) { (key, label) ->
                                                        val selected = saavnLanguageKey == key
                                                        FilterChip(
                                                            selected = selected,
                                                            onClick = {
                                                                if (saavnLanguageKey != key) {
                                                                    saavnLanguageKey = key
                                                                    com.musicplayer.lytrix.saavn.JioSaavnLiveRepository.setSelectedLanguage(context, key)
                                                                    loadSaavnLiveHome(key, forceRefresh = false)
                                                                }
                                                            },
                                                            label = { Text(label, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium) },
                                                            shape = RoundedCornerShape(14.dp),
                                                        )
                                                    }
                                                }
                                            }
                                            val ads = BannerCounter()
                                            liveSections.forEach { section ->
                                                rowWithBanner(ads) {
                                                    MetroSaavnMultiBrowseSection(
                                                        section = section,
                                                        onItemClick = { card ->
                                                            if (card.type == com.musicplayer.lytrix.saavn.SaavnItemType.SONG && card.song != null) {
                                                                val songList = section.items.mapNotNull { it.song }
                                                                scope.launch(Dispatchers.IO) {
                                                                    val playable = com.musicplayer.lytrix.saavn.JioSaavnLiveRepository.resolvePlayableSong(card.song)
                                                                    withContext(Dispatchers.Main) {
                                                                        val queue = songList.map { if (it.id == playable.id) playable else it }
                                                                        viewModel.playOnlineSongWithAd(context, playable, queue.ifEmpty { listOf(playable) })
                                                                    }
                                                                }
                                                            } else {
                                                                openSaavnCollection(card)
                                                            }
                                                        },
                                                        onSongMoreClick = { song ->
                                                            selectedBottomSong = song
                                                            showBottomSheet = true
                                                        },
                                                        onSeeAllClick = { sec ->
                                                            activeSaavnSection = sec
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            "Shows", "Podcast", "Audiobooks" -> {
                                LaunchedEffect(podcastTopicKey, audiobookTopicKey) {
                                    loadPodcastHub(podcastTopicKey, forceRefresh = false)
                                    loadAudiobookHub(audiobookTopicKey, forceRefresh = false)
                                }
                                androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                                    isRefreshing = isPodcastHubRefreshing || isAudiobookHubRefreshing,
                                    onRefresh = {
                                        isPodcastHubRefreshing = true
                                        isAudiobookHubRefreshing = true
                                        loadPodcastHub(podcastTopicKey, forceRefresh = true)
                                        loadAudiobookHub(audiobookTopicKey, forceRefresh = true)
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    val livePodcastSections = podcastFeedData?.sections.orEmpty()
                                    val liveAudiobookSections = audiobookFeedData?.sections.orEmpty()
                                    val allShowSections = livePodcastSections + liveAudiobookSections
                                    if ((isPodcastHubLoading || isAudiobookHubLoading) && allShowSections.isEmpty()) {
                                        Column(modifier = Modifier.fillMaxSize()) {
                                            exploreChipsRow()
                                            LytrixHorizontalCardSectionShimmer()
                                            LytrixHorizontalCardSectionShimmer()
                                        }
                                    } else {
                                        LazyColumn(
                                            modifier = Modifier.fillMaxSize().nestedScroll(nestedScrollConnection),
                                            contentPadding = PaddingValues(bottom = bottomListPadding),
                                        ) {
                                            item { exploreChipsRow() }
                                            item {
                                                LazyRow(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                ) {
                                                    items(com.musicplayer.lytrix.media.MediaHubRepository.PODCAST_TOPIC_CHIPS) { (key, label) ->
                                                        val selected = podcastTopicKey == key
                                                        FilterChip(
                                                            selected = selected,
                                                            onClick = {
                                                                if (podcastTopicKey != key) {
                                                                    podcastTopicKey = key
                                                                }
                                                            },
                                                            label = {
                                                                Text(
                                                                    text = label,
                                                                    fontSize = 13.sp,
                                                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                                )
                                                            },
                                                            shape = RoundedCornerShape(14.dp),
                                                        )
                                                    }
                                                }
                                            }
                                            if (allShowSections.isEmpty() && !isPodcastHubLoading && !isAudiobookHubLoading) {
                                                item {
                                                    LottieErrorStateScreen(
                                                        state = PlayerViewModel.OnlineUiState.Error("Could not load live shows. Tap to retry.", false),
                                                        onRetry = {
                                                            loadPodcastHub(podcastTopicKey, forceRefresh = true)
                                                            loadAudiobookHub(audiobookTopicKey, forceRefresh = true)
                                                        },
                                                    )
                                                }
                                            } else {
                                                val ads = BannerCounter()
                                                allShowSections.forEach { section ->
                                                    rowWithBanner(ads) {
                                                        MetroMediaHubCarouselSection(
                                                            section = section,
                                                            onCardClick = { card -> handleMediaHubCardClick(card, section.items) },
                                                            onCardLongClick = { card ->
                                                                selectedMediaHubSheetCard = card
                                                                selectedMediaHubSheetQueue = section.items
                                                            },
                                                            onSeeAllClick = { sec -> activeMediaHubSection = sec },
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            "Radio" -> {
                                when (val state = radioState) {
                                    is PlayerViewModel.OnlineUiState.Loading -> Column(modifier = Modifier.fillMaxSize()) {
                                        exploreChipsRow()
                                        LytrixHorizontalCardSectionShimmer()
                                        LytrixHorizontalCardSectionShimmer()
                                    }
                                    is PlayerViewModel.OnlineUiState.Error -> Column(modifier = Modifier.fillMaxSize()) {
                                        exploreChipsRow()
                                        LottieErrorStateScreen(state = state, onRetry = { triggerInitialLoad() })
                                    }
                                    is PlayerViewModel.OnlineUiState.Success -> {
                                        LazyColumn(
                                            modifier = Modifier.fillMaxSize().nestedScroll(nestedScrollConnection),
                                            contentPadding = PaddingValues(bottom = bottomListPadding)
                                        ) {
                                            item { exploreChipsRow() }
                                            val ads = BannerCounter()
                                            listOf(
                                                "🇧🇩 Bangladesh FM" to radioBD,
                                                "🌍 Global Pop" to radioPop,
                                                "📰 World News" to radioNews,
                                                "🎧 Chill & Relaxing" to radioChill,
                                                "🇮🇳 Indian & Hindi FM" to radioIndia
                                            ).filter { it.second.isNotEmpty() }.forEach { (title, stations) ->
                                                rowWithBanner(ads) {
                                                    HorizontalRadioSection(title, stations, viewModel, onBgColor, context)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                "SEARCH" -> {
                    val searchPlaceholders = remember {
                        listOf(
                            "Search for music...",
                            "Search for playlists...",
                            "Search for artists...",
                            "Search for albums...",
                            "Search for podcasts...",
                            "Search for moods & genres..."
                        )
                    }
                    var placeholderIndex by remember { mutableIntStateOf(0) }
                    LaunchedEffect(Unit) {
                        while (true) {
                            delay(3000L)
                            placeholderIndex = (placeholderIndex + 1) % searchPlaceholders.size
                        }
                    }
                    Column(modifier = Modifier.fillMaxSize()) {

                        // 🔥 ডামি সার্চ বার AnimatedVisibility-এর ভেতরে
                        AnimatedVisibility(
                            visible = selectedExploreCategoryName == null,
                            enter = slideInVertically(initialOffsetY = { -it }) + expandVertically(),
                            exit = slideOutVertically(targetOffsetY = { -it }) + shrinkVertically()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp, start = 16.dp, end = 16.dp, bottom = 12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .clip(RoundedCornerShape(28.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(28.dp))
                                        .clickable {
                                            isSearchScreenOpen = true
                                        },
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
                                        Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onSurface)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        AnimatedContent(
                                            targetState = searchPlaceholders[placeholderIndex],
                                            transitionSpec = {
                                                (slideInVertically { height -> height } + fadeIn()).togetherWith(
                                                    slideOutVertically { height -> -height } + fadeOut()
                                                )
                                            },
                                            label = "SearchPlaceholderAnimation"
                                        ) { text ->
                                            Text(
                                                text = text,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 16.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (selectedExploreCategoryName == null) {
                            if (exploreMoodSections.isEmpty() && explorePageMoods.isEmpty()) {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(2),
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = bottomListPadding),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    item(span = { GridItemSpan(2) }) {
                                        Box(modifier = Modifier.width(160.dp).height(24.dp).clip(RoundedCornerShape(6.dp)).shimmer())
                                    }
                                    items(8) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(2f)
                                                .clip(RoundedCornerShape(10.dp))
                                                .shimmer()
                                        )
                                    }
                                }
                            } else if (exploreMoodSections.isNotEmpty()) {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(2),
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = bottomListPadding),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    exploreMoodSections.forEach { section ->
                                        item(span = { GridItemSpan(2) }) {
                                            Text(
                                                text = section.title,
                                                color = Color.White,
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                                            )
                                        }
                                        items(section.items, key = { "${section.title}_${it.title}_${it.endpoint.params}" }) { item ->
                                            LaunchedEffect(item.endpoint.params) {
                                                item.endpoint.params?.let { params ->
                                                    viewModel.loadMoodArtwork(params)
                                                }
                                            }
                                            val artUrl = item.endpoint.params?.let { moodArtworks[it] }
                                            SimpMoodCategoryCard(
                                                title = item.title,
                                                artworkUrl = artUrl,
                                                onClick = {
                                                    selectedExploreCategoryName = item.title
                                                    viewModel.loadExploreCategory(item.endpoint)
                                                }
                                            )
                                        }
                                    }
                                }
                            } else {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(2),
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = bottomListPadding),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    item(span = { GridItemSpan(2) }) {
                                        Text(
                                            text = "Mood & Genres",
                                            color = Color.White,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                                        )
                                    }
                                    items(explorePageMoods, key = { it.title }) { item ->
                                        LaunchedEffect(item.endpoint.params) {
                                            item.endpoint.params?.let { params ->
                                                viewModel.loadMoodArtwork(params)
                                            }
                                        }
                                        val artUrl = item.endpoint.params?.let { moodArtworks[it] }
                                        SimpMoodCategoryCard(
                                            title = item.title,
                                            artworkUrl = artUrl,
                                            onClick = {
                                                selectedExploreCategoryName = item.title
                                                viewModel.loadExploreCategory(item.endpoint)
                                            }
                                        )
                                    }
                                }
                            }
                        } else {
                            BackHandler(enabled = true) { selectedExploreCategoryName = null }
                            Column(modifier = Modifier.fillMaxSize()) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                                    IconButton(onClick = { selectedExploreCategoryName = null }) {
                                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                                    }
                                    Text(
                                        text = selectedExploreCategoryName ?: "",
                                        color = Color.White,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (exploreCategoryPlaylists.isEmpty()) {
                                    Column(modifier = Modifier.fillMaxSize()) {
                                        LytrixHorizontalCardSectionShimmer()
                                        LytrixHorizontalCardSectionShimmer()
                                    }
                                } else {
                                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomListPadding)) {
                                        items(exploreCategoryPlaylists) { section ->
                                            HorizontalYtMusicSection(
                                                title = section.title,
                                                tracks = section.tracks,
                                                viewModel = viewModel,
                                                onBgColor = onBgColor
                                            ) { track ->
                                                val isAlbum = track.itemType == "Album" || track.artist.contains("Album", ignoreCase = true)
                                                val isPlaylistOrAlbum = isAlbum || track.artist.contains("Playlist", ignoreCase = true) || track.artist.contains("Station", ignoreCase = true)
                                                if (isPlaylistOrAlbum) {
                                                    selectedYtPlaylist = track
                                                    viewModel.ytPlaylistType.value = if (isAlbum) "Album" else "Playlist"
                                                    showYtPlaylistBottomSheet = true
                                                } else {
                                                    selectedBottomSong = JioSaavnSong(track.id, track.title, track.artist, track.thumbnailUrl ?: "", "")
                                                    showBottomSheet = true
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ==========================================

                // 🔥 LIBRARY (Lytrix Layout with FAB & Scroll Animations)

                // ==========================================
                "LIBRARY" -> {
                    val podcastDaoLocal = remember {
                        com.musicplayer.lytrix.db.AppDatabase.getDatabase(context).podcastDao()
                    }
                    val downloadedPodcastsLocal by podcastDaoLocal.getAllDownloadedPodcasts()
                        .collectAsState(initial = emptyList())
                    val podcastLikedPrefsLocal =
                        context.getSharedPreferences("online_liked_podcasts", Context.MODE_PRIVATE)
                    val audiobookLikedPrefsLocal = context.getSharedPreferences(
                        "online_liked_audiobooks",
                        Context.MODE_PRIVATE
                    )
                    val spotifyPrefManager = remember { com.musicplayer.lytrix.spotify.SpotifyPreferencesManager(context) }
                    val isSpotifyLoggedIn by spotifyPrefManager.isLoggedIn.collectAsState(initial = false)
                    val spotifyPlaylistsEnabled by spotifyPrefManager.playlistsEnabled.collectAsState(initial = true)
                    val isSpotifyAvailable = isSpotifyLoggedIn && spotifyPlaylistsEnabled

                    LaunchedEffect(isSpotifyAvailable) {
                        if (isSpotifyAvailable) {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                com.musicplayer.lytrix.spotify.SpotifyRepository.syncAll(context)
                            }
                            refreshTriggerLocal++
                        }
                    }

                    val likedPrefsLocal =
                        context.getSharedPreferences("online_liked_songs", Context.MODE_PRIVATE)
                    val likedSongsLocal = remember(
                        likedPrefsLocal.getStringSet("liked_ids", emptySet()),
                        refreshTriggerLocal
                    ) {
                        (likedPrefsLocal.getStringSet("liked_ids", emptySet())
                            ?: emptySet()).mapNotNull { id ->
                            val json = likedPrefsLocal.getString("song_data_$id", null)
                            if (json != null) {
                                try {
                                    val obj = JSONObject(json)
                                    JioSaavnSong(
                                        id = obj.optString("id", ""),
                                        name = obj.optString("name", "Unknown"),
                                        artist = obj.optString("artist", "Unknown Artist"),
                                        imageUrl = obj.optString("imageUrl", ""),
                                        downloadUrl = obj.optString("downloadUrl", "")
                                    )
                                } catch (e: Exception) {
                                    null
                                }
                            } else null
                        }
                    }
                    val likedPodcastsListLocal = remember(
                        podcastLikedPrefsLocal.getStringSet("liked_ids", emptySet()),
                        refreshTriggerLocal
                    ) {
                        (podcastLikedPrefsLocal.getStringSet("liked_ids", emptySet())
                            ?: emptySet()).mapNotNull { id ->
                            val json = podcastLikedPrefsLocal.getString("song_data_$id", null)
                                ?: podcastLikedPrefsLocal.getString("podcast_data_$id", null)
                            if (json != null) {
                                try {
                                    val obj = JSONObject(json)
                                    val epId = obj.optString("id", id)
                                    val epTitle = obj.optString("title_original").ifBlank { obj.optString("title", obj.optString("name", "Unknown")) }
                                    val epAuthor = obj.optString("publisher").ifBlank { obj.optString("artist", obj.optString("subtitle", "Unknown")) }
                                    val epImg = obj.optString("image").ifBlank { obj.optString("imageUrl", obj.optString("image_url", "")) }
                                    val epAudio = obj.optString("audio").ifBlank { obj.optString("url", obj.optString("downloadUrl", "")) }
                                    com.musicplayer.lytrix.PodcastEpisode(
                                        id = epId,
                                        title_original = epTitle,
                                        audio = epAudio,
                                        image = epImg,
                                        audio_length_sec = obj.optInt("audio_length_sec", 0),
                                        podcast = com.musicplayer.lytrix.PodcastInfo(
                                            publisher_original = epAuthor,
                                            channelId = obj.optString("channelId", null)
                                        )
                                    )
                                } catch (e: Exception) {
                                    null
                                }
                            } else null
                        }
                    }
                    val likedAudiobooksListLocal = remember(
                        audiobookLikedPrefsLocal.getStringSet("liked_ids", emptySet()),
                        refreshTriggerLocal
                    ) {
                        (audiobookLikedPrefsLocal.getStringSet("liked_ids", emptySet())
                            ?: emptySet()).mapNotNull { id ->
                            val json = audiobookLikedPrefsLocal.getString("song_data_$id", null)
                                ?: audiobookLikedPrefsLocal.getString("audiobook_data_$id", null)
                            if (json != null) {
                                try {
                                    val obj = JSONObject(json)
                                    val epId = obj.optString("id", id)
                                    val epTitle = obj.optString("title_original").ifBlank { obj.optString("title", obj.optString("name", "Unknown")) }
                                    val epAuthor = obj.optString("publisher").ifBlank { obj.optString("artist", obj.optString("subtitle", "Unknown")) }
                                    val epImg = obj.optString("image").ifBlank { obj.optString("imageUrl", obj.optString("image_url", "")) }
                                    val epAudio = obj.optString("audio").ifBlank { obj.optString("url", obj.optString("downloadUrl", "")) }
                                    com.musicplayer.lytrix.PodcastEpisode(
                                        id = epId,
                                        title_original = epTitle,
                                        audio = epAudio,
                                        image = epImg,
                                        audio_length_sec = obj.optInt("audio_length_sec", 0),
                                        podcast = com.musicplayer.lytrix.PodcastInfo(
                                            publisher_original = epAuthor,
                                            channelId = obj.optString("channelId", null)
                                        )
                                    )
                                } catch (e: Exception) {
                                    null
                                }
                            } else null
                        }
                    }
                    val downloadedSongsLocal = remember(currentLibraryView, refreshTriggerLocal) {
                        val prefs =
                            context.getSharedPreferences("downloaded_meta", Context.MODE_PRIVATE)
                        prefs.all.mapNotNull { (key, value) ->
                            try {
                                val jsonStr = value.toString()
                                if (jsonStr.startsWith("{")) {
                                    val obj = JSONObject(jsonStr)
                                    JioSaavnSong(
                                        id = obj.optString("id", key),
                                        name = obj.optString("name", "Unknown"),
                                        artist = obj.optString("artist", "Unknown"),
                                        imageUrl = obj.optString("imageUrl", ""),
                                        downloadUrl = obj.optString("downloadUrl", "")
                                    )
                                } else null
                            } catch (e: Exception) {
                                null
                            }
                        }
                    }
                    val downloadedAudiobooksLocal =
                        remember(currentLibraryView, refreshTriggerLocal) {
                            val prefs = context.getSharedPreferences(
                                "downloaded_audiobooks_meta",
                                Context.MODE_PRIVATE
                            )
                            prefs.all.mapNotNull { (key, value) ->
                                try {
                                    val jsonStr = value.toString()
                                    if (jsonStr.startsWith("{")) {
                                        val obj = JSONObject(jsonStr)
                                        com.musicplayer.lytrix.PodcastEpisode(
                                            id = obj.optString(
                                                "id",
                                                key.replace("audiobook_", "")
                                            ),
                                            title_original = obj.optString(
                                                "title_original",
                                                "Unknown"
                                            ),
                                            audio = obj.optString("audio", ""),
                                            image = obj.optString("image", ""),
                                            audio_length_sec = 0,
                                            podcast = com.musicplayer.lytrix.PodcastInfo(
                                                obj.optString(
                                                    "publisher",
                                                    "Unknown"
                                                )
                                            )
                                        )
                                    } else null
                                } catch (e: Exception) {
                                    null
                                }
                            }.distinctBy { it.title_original }
                        }
                    val plPrefs = context.getSharedPreferences("online_playlists_meta", Context.MODE_PRIVATE)
                    val coverPrefs = context.getSharedPreferences("playlist_covers", Context.MODE_PRIVATE)
                    val timePrefs = context.getSharedPreferences("playlist_time", Context.MODE_PRIVATE)
                    val sourcePrefs = context.getSharedPreferences("playlist_source", Context.MODE_PRIVATE)
                    val idPrefsMain = context.getSharedPreferences("playlist_ids", Context.MODE_PRIVATE)
                    val pinnedPrefs = context.getSharedPreferences("pinned_playlists", Context.MODE_PRIVATE)
                    fun detectCollectionSource(name: String, songsPrefs: android.content.SharedPreferences, isAlbum: Boolean): String {
                        val explicit = sourcePrefs.getString(name, null)?.trim().orEmpty()
                        if (explicit.equals("Spotify", ignoreCase = true)) return "Spotify"
                        if (explicit.equals("YouTube", ignoreCase = true)) return "YouTube"
                        if (explicit.equals("JioSaavn", ignoreCase = true)) return "JioSaavn"
                        if (explicit.equals("Custom", ignoreCase = true)) return "Custom"
                        val savedId = idPrefsMain.getString(name, null)?.trim().orEmpty()
                        if (savedId.startsWith("saavn_", ignoreCase = true)) return "JioSaavn"
                        if (savedId.isNotEmpty()) return "YouTube"
                        val firstRaw = songsPrefs.all.values.firstOrNull()?.toString().orEmpty()
                        if (firstRaw.contains("saavncdn", ignoreCase = true)) return "JioSaavn"
                        if (isAlbum) return "YouTube"
                        return "Custom"
                    }
                    val allPlaylistData = remember(plPrefs.getStringSet("names", emptySet()), refreshTriggerLocal) {
                        val names = plPrefs.getStringSet("names", emptySet())?.toList() ?: emptyList()
                        names.map { plName ->
                            val plSongsPrefs = context.getSharedPreferences("online_pl_$plName", Context.MODE_PRIVATE)
                            val customCover = coverPrefs.getString(plName, null)
                            val plSource = detectCollectionSource(plName, plSongsPrefs, isAlbum = false)
                            val time = timePrefs.getLong(plName, 0L)
                            var firstThumb = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745"
                            if (plSongsPrefs.all.isNotEmpty()) try { firstThumb = org.json.JSONObject(plSongsPrefs.all.values.firstOrNull()?.toString() ?: "").optString("imageUrl", firstThumb) } catch(e:Exception){}
                            val storedCount = plPrefs.getInt("count_$plName", -1)
                            val isSpotify = plSource.equals("Spotify", ignoreCase = true)
                            val isLiked = plName.equals("Liked Songs", ignoreCase = true)
                            val isSessionSynced = com.musicplayer.lytrix.spotify.SpotifyRepository.isPlaylistSyncedInSession(plName)

                            val finalCount = when {
                                isLiked -> com.musicplayer.lytrix.spotify.SpotifyRepository.getCachedLikedSongs(context).size
                                isSpotify && !isSessionSynced && plSongsPrefs.all.isEmpty() -> -1
                                plSongsPrefs.all.isNotEmpty() -> plSongsPrefs.all.size
                                storedCount > 0 -> storedCount
                                else -> 0
                            }
                            mapOf("name" to plName, "cover" to (customCover ?: firstThumb), "count" to finalCount, "source" to plSource, "time" to time)
                        }
                    }

                    // 🔥 Local Album Fetch Logic
                    val albumPrefsLocal = context.getSharedPreferences("online_albums_meta", Context.MODE_PRIVATE)
                    val allAlbumDataLocal = remember(albumPrefsLocal.getStringSet("names", emptySet()), refreshTriggerLocal) {
                        val names = albumPrefsLocal.getStringSet("names", emptySet())?.toList() ?: emptyList()
                        names.map { albumName ->
                            val albumSongsPrefs = context.getSharedPreferences("online_album_$albumName", Context.MODE_PRIVATE)
                            val customCover = coverPrefs.getString(albumName, null)
                            val albSource = detectCollectionSource(albumName, albumSongsPrefs, isAlbum = true)
                            val time = timePrefs.getLong(albumName, 0L)
                            var firstThumb = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745"
                            if (albumSongsPrefs.all.isNotEmpty()) {
                                try { firstThumb = org.json.JSONObject(albumSongsPrefs.all.values.firstOrNull()?.toString() ?: "").optString("imageUrl", firstThumb) } catch(e:Exception){}
                            }
                            val storedCount = albumPrefsLocal.getInt("count_$albumName", plPrefs.getInt("count_$albumName", -1))
                            val finalCount = when {
                                albumSongsPrefs.all.isNotEmpty() -> albumSongsPrefs.all.size
                                storedCount > 0 -> storedCount
                                else -> 0
                            }
                            mapOf("name" to albumName, "cover" to (customCover ?: firstThumb), "count" to finalCount, "source" to albSource, "time" to time)
                        }
                    }

// 🔥 Subscribed Artists Fetch Logic
                    val subscribedArtistsLocal = remember(refreshTriggerLocal) {
                        val prefs = context.getSharedPreferences("subscribed_artists_prefs", Context.MODE_PRIVATE)
                        (prefs.getStringSet("subscribed_ids", emptySet()) ?: emptySet()).mapNotNull { id ->
                            val json = prefs.getString("artist_data_$id", null)
                            if (json != null) {
                                try {
                                    val obj = JSONObject(json)
                                    com.musicplayer.lytrix.innertube.models.ArtistItem(
                                        id = obj.optString("id", ""),
                                        title = obj.optString("name", "Unknown"),
                                        thumbnail = obj.optString("thumbnail", ""),
                                        thumbnailWidth = null,
                                        thumbnailHeight = null,
                                        channelId = null,
                                        shuffleEndpoint = null,
                                        radioEndpoint = null
                                    )
                                } catch (e: Exception) { null }
                            } else null
                        }
                    }
                    var currentMediaType by remember { mutableStateOf("MUSIC") }
                    var currentSubTab by remember { mutableStateOf("PLAYLISTS") }
                    var isLibrarySearchActive by remember { mutableStateOf(false) }
                    var librarySearchQuery by remember { mutableStateOf("") }
                    var plSortOrder by remember { mutableStateOf("Recently added") }
                    var showPlSortMenu by remember { mutableStateOf(false) }
                    var isGridView by remember { mutableStateOf(false) }
                    var showOuterRenameDialog by remember { mutableStateOf(false) }
                    var showOuterDeleteWarning by remember { mutableStateOf(false) }
                    var playlistToRename by remember { mutableStateOf("") }
                    var playlistToDelete by remember { mutableStateOf("") }
                    var outerRenameInput by remember { mutableStateOf("") }
                    var songFilter by remember { mutableStateOf("All Songs") }
                    var sortOrder by remember { mutableStateOf("Recently added") }
                    var showSortMenu by remember { mutableStateOf(false) }
                    var albumSortOrder by remember { mutableStateOf("Newest") }
                    var showAlbumSortMenu by remember { mutableStateOf(false) }
                    var isAlbumGridView by remember { mutableStateOf(true) }

                    // 🔥 Local YT Podcast Fetch Logic
                    val autoPodcastTitles = remember { setOf("new episodes", "episodes for later", "continue listening", "books for later") }
                    val podcastPrefsLocal = context.getSharedPreferences("online_podcasts_meta", Context.MODE_PRIVATE)
                    val audiobookPrefsLocal = context.getSharedPreferences("online_audiobooks_meta", Context.MODE_PRIVATE)

                    // Clean poisoned auto-playlists from meta prefs on launch
                    LaunchedEffect(Unit) {
                        val rawPodcastNames = podcastPrefsLocal.getStringSet("names", emptySet()) ?: emptySet()
                        val poisonedPods = rawPodcastNames.filter { it.trim().lowercase() in autoPodcastTitles }
                        if (poisonedPods.isNotEmpty()) {
                            val cleaned = rawPodcastNames.toMutableSet()
                            cleaned.removeAll(poisonedPods.toSet())
                            podcastPrefsLocal.edit().putStringSet("names", cleaned).commit()
                        }
                        val rawAbNames = audiobookPrefsLocal.getStringSet("names", emptySet()) ?: emptySet()
                        val poisonedAbs = rawAbNames.filter { it.trim().lowercase() in autoPodcastTitles }
                        if (poisonedAbs.isNotEmpty()) {
                            val cleaned = rawAbNames.toMutableSet()
                            cleaned.removeAll(poisonedAbs.toSet())
                            audiobookPrefsLocal.edit().putStringSet("names", cleaned).commit()
                        }
                        val podChanPrefs = context.getSharedPreferences("subscribed_podcast_channels_prefs", Context.MODE_PRIVATE)
                        val subChanIds = podChanPrefs.getStringSet("subscribed_ids", emptySet()) ?: emptySet()
                        val poisonedChans = subChanIds.filter { id ->
                            val json = podChanPrefs.getString("channel_data_$id", null)
                            val name = try { JSONObject(json ?: "{}").optString("name", "") } catch (e: Exception) { "" }
                            name.trim().lowercase() in autoPodcastTitles || id.trim().lowercase() in autoPodcastTitles
                        }
                        if (poisonedChans.isNotEmpty()) {
                            val cleanChanIds = subChanIds.toMutableSet()
                            cleanChanIds.removeAll(poisonedChans.toSet())
                            val editor = podChanPrefs.edit().putStringSet("subscribed_ids", cleanChanIds)
                            poisonedChans.forEach { editor.remove("channel_data_$it") }
                            editor.commit()
                        }
                    }

                    val allPodcastDataLocal = remember(podcastPrefsLocal.getStringSet("names", emptySet()), refreshTriggerLocal) {
                        val names = podcastPrefsLocal.getStringSet("names", emptySet())?.toList() ?: emptyList()
                        names.filter { it.trim().lowercase() !in autoPodcastTitles }.map { podName ->
                            val podSongsPrefs = context.getSharedPreferences("online_podcast_$podName", Context.MODE_PRIVATE)
                            val customCover = coverPrefs.getString(podName, null)
                            val time = timePrefs.getLong(podName, 0L)
                            val src = sourcePrefs.getString(podName, "Podcast") ?: "Podcast"
                            var firstThumb = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745"
                            if (podSongsPrefs.all.isNotEmpty()) {
                                try { firstThumb = org.json.JSONObject(podSongsPrefs.all.values.firstOrNull()?.toString() ?: "").optString("imageUrl", firstThumb) } catch(e:Exception){}
                            }
                            val storedCount = podcastPrefsLocal.getInt("count_$podName", -1)
                            val finalCount = if (storedCount != -1) storedCount else podSongsPrefs.all.size
                            mapOf("name" to podName, "cover" to (customCover ?: firstThumb), "count" to finalCount, "time" to time, "source" to src)
                        }
                    }
                    val allAudiobookDataLocal = remember(audiobookPrefsLocal.getStringSet("names", emptySet()), refreshTriggerLocal) {
                        val names = audiobookPrefsLocal.getStringSet("names", emptySet())?.toList() ?: emptyList()
                        names.filter { it.trim().lowercase() !in autoPodcastTitles }.map { abName ->
                            val abSongsPrefs = context.getSharedPreferences("online_ab_$abName", Context.MODE_PRIVATE)
                            val customCover = coverPrefs.getString(abName, null)
                            val time = timePrefs.getLong(abName, 0L)
                            val src = sourcePrefs.getString(abName, "Audiobook") ?: "Audiobook"
                            var firstThumb = "https://images.unsplash.com/photo-1512820790803-83ca734da794"
                            if (abSongsPrefs.all.isNotEmpty()) {
                                try { firstThumb = org.json.JSONObject(abSongsPrefs.all.values.firstOrNull()?.toString() ?: "").optString("imageUrl", firstThumb) } catch(e:Exception){}
                            }
                            val storedCount = audiobookPrefsLocal.getInt("count_$abName", -1)
                            val finalCount = if (storedCount != -1) storedCount else abSongsPrefs.all.size
                            mapOf("name" to abName, "cover" to (customCover ?: firstThumb), "count" to finalCount, "time" to time, "source" to src)
                        }
                    }
                    var podcastSortOrder by remember { mutableStateOf("Newest") }
                    var showPodcastSortMenu by remember { mutableStateOf(false) }
                    var isPodcastGridView by remember { mutableStateOf(true) }
                    var podcastSubTab by remember { mutableStateOf("Episodes") }
                    var audiobookSubTab by remember { mutableStateOf("Books") }
                    var podcastDownloadSort by remember { mutableStateOf("Date Added") }
                    var podcastDownloadDescending by remember { mutableStateOf(true) }
                    var audiobookDownloadSort by remember { mutableStateOf("Date Added") }
                    var audiobookDownloadDescending by remember { mutableStateOf(true) }
                    var audiobookSortOrder by remember { mutableStateOf("Newest") }
                    var showAudiobookSortMenu by remember { mutableStateOf(false) }
                    var isAudiobookGridView by remember { mutableStateOf(true) }
                    var artistFilter by remember { mutableStateOf("All Artists") }
                    var showArtistFilterMenu by remember { mutableStateOf(false) }
                    var artistSortOrder by remember { mutableStateOf("A → Z") }
                    var showArtistSortMenu by remember { mutableStateOf(false) }
                    var isArtistGridView by remember { mutableStateOf(false) }
                    if (currentLibraryView == "MAIN") {
                        val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
                        BackHandler(enabled = isLibrarySearchActive) {
                            isLibrarySearchActive = false
                            librarySearchQuery = ""
                            keyboardController?.hide()
                        }
                        Column(modifier = Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)
                            ) {

                                // প্রথম AnimatedVisibility (মেনু চিপস এর জন্য - অপরিবর্তিত)
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = !isLibrarySearchActive,
                                    enter = fadeIn() + slideInHorizontally(initialOffsetX = { fullWidth -> -fullWidth }),
                                    exit = fadeOut() + slideOutHorizontally(targetOffsetX = { fullWidth -> -fullWidth })
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        LazyRow(
                                            modifier = Modifier.weight(1f),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            val mediaTypes = listOf("MUSIC", "SHOWS")
                                            items(mediaTypes) { type ->
                                                val isSelected = if (type == "MUSIC") currentMediaType == "MUSIC" else currentMediaType in listOf("SHOWS", "PODCAST", "AUDIOBOOK")
                                                val label = if (type == "MUSIC") "Music" else "Shows"
                                                Box(
                                                    modifier = Modifier.clip(RoundedCornerShape(24.dp))
                                                        .background(
                                                            if (isSelected) accentColor else Color(
                                                                0xFF2D2D2D
                                                            )
                                                        )
                                                        .clickable { currentMediaType = if (type == "MUSIC") "MUSIC" else "SHOWS" }
                                                        .padding(
                                                            horizontal = 16.dp,
                                                            vertical = 8.dp
                                                        )
                                                ) {
                                                    Text(
                                                        text = label,
                                                        color = if (isSelected) Color.Black else Color.White,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                            }
                                        }
                                        IconButton(
                                            onClick = { isLibrarySearchActive = true },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Search,
                                                contentDescription = "Search",
                                                tint = Color.White
                                            )
                                        }
                                    }
                                }

                                // দ্বিতীয় AnimatedVisibility (সার্চ বার - স্লিম ও সুন্দর করা হলো)
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = isLibrarySearchActive,
                                    enter = fadeIn() + slideInHorizontally(initialOffsetX = { fullWidth -> fullWidth }),
                                    exit = fadeOut() + slideOutHorizontally(targetOffsetX = { fullWidth -> fullWidth })
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).background(Color(0xFF2D2D2D)),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
                                            IconButton(onClick = { isLibrarySearchActive = false; librarySearchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            androidx.compose.foundation.text.BasicTextField(
                                                value = librarySearchQuery,
                                                onValueChange = { librarySearchQuery = it },
                                                modifier = Modifier.weight(1f),
                                                singleLine = true,
                                                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 16.sp),
                                                cursorBrush = androidx.compose.ui.graphics.SolidColor(accentColor),
                                                decorationBox = { innerTextField ->
                                                    if (librarySearchQuery.isEmpty()) {
                                                        Text("Find in ${currentSubTab.lowercase()}...", color = Color.Gray, fontSize = 15.sp)
                                                    }
                                                    innerTextField()
                                                }
                                            )
                                            if (librarySearchQuery.isNotEmpty()) {
                                                IconButton(onClick = { librarySearchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.White)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            if (currentMediaType == "MUSIC") {
                                LaunchedEffect(currentSubTab) {
                                    if (currentSubTab == "YT PODCASTS") currentSubTab = "PLAYLISTS"
                                }
                                LazyRow(
                                    modifier = Modifier.fillMaxWidth()
                                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val tabs = listOf("PLAYLISTS", "SONGS", "ARTISTS", "ALBUMS")
                                    items(tabs) { tab ->
                                        val isSelected = currentSubTab == tab
                                        Box(
                                            modifier = Modifier.clip(RoundedCornerShape(24.dp))
                                                .border(
                                                    1.dp,
                                                    if (isSelected) accentColor else Color.Gray.copy(
                                                        alpha = 0.3f
                                                    ),
                                                    RoundedCornerShape(24.dp)
                                                )
                                                .background(if (isSelected) accentColor.copy(alpha = 0.1f) else Color.Transparent)
                                                .clickable { currentSubTab = tab }
                                                .padding(horizontal = 14.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = tab.lowercase()
                                                    .replaceFirstChar { it.uppercase() },
                                                color = if (isSelected) accentColor else Color.White.copy(
                                                    alpha = 0.8f
                                                ),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            } else if (currentMediaType in listOf("SHOWS", "PODCAST", "AUDIOBOOK")) {
                                MetroSubTabChips(
                                    options = listOf("Episodes / Books", "Channels / Authors", "Downloaded", "Liked"),
                                    selected = when (podcastSubTab.lowercase()) {
                                        "channels", "authors" -> "Channels / Authors"
                                        "downloaded" -> "Downloaded"
                                        "liked" -> "Liked"
                                        else -> "Episodes / Books"
                                    },
                                    onSelect = { selectedTitle ->
                                        podcastSubTab = when (selectedTitle) {
                                            "Channels / Authors" -> "channels"
                                            "Downloaded" -> "downloaded"
                                            "Liked" -> "liked"
                                            else -> "episodes"
                                        }
                                    },
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                            val autoPlaylistFilterSet = setOf("new episodes", "episodes for later", "continue listening", "books for later")
                            val podcastChannels = remember(refreshTriggerLocal) {
                                val podcastChannelPrefs = context.getSharedPreferences("subscribed_podcast_channels_prefs", Context.MODE_PRIVATE)
                                (podcastChannelPrefs.getStringSet("subscribed_ids", emptySet()) ?: emptySet()).mapNotNull { id ->
                                    val json = podcastChannelPrefs.getString("channel_data_$id", null)
                                    if (json != null) {
                                        try {
                                            val obj = JSONObject(json)
                                            val cName = obj.optString("name", "Unknown Channel")
                                            if (cName.trim().lowercase() !in autoPlaylistFilterSet) {
                                                mapOf("name" to cName, "cover" to obj.optString("thumbnail", ""), "id" to id)
                                            } else null
                                        } catch (e: Exception) { null }
                                    } else null
                                }.distinctBy { (it["name"] as? String)?.trim()?.lowercase() }
                            }
                            val sortedPodcastDl = remember(downloadedPodcastsLocal, podcastDownloadSort, podcastDownloadDescending) {
                                val sorted = when (podcastDownloadSort) {
                                    "Name" -> downloadedPodcastsLocal.sortedBy { it.title.lowercase() }
                                    "Artist" -> downloadedPodcastsLocal.sortedBy { it.publisher.lowercase() }
                                    else -> downloadedPodcastsLocal.sortedBy { it.addedTime }
                                }
                                if (podcastDownloadDescending) sorted.reversed() else sorted
                            }
                            val audiobookAuthors = remember(allAudiobookDataLocal, refreshTriggerLocal) {
                                val authorPrefs = context.getSharedPreferences("subscribed_audiobook_authors_prefs", Context.MODE_PRIVATE)
                                val fromSubscribed = (authorPrefs.getStringSet("subscribed_ids", emptySet()) ?: emptySet()).mapNotNull { id ->
                                    val json = authorPrefs.getString("author_data_$id", null)
                                    if (json != null) {
                                        try {
                                            val obj = JSONObject(json)
                                            val aName = obj.optString("name", "Unknown Author")
                                            if (aName.trim().lowercase() !in autoPlaylistFilterSet) {
                                                mapOf("name" to aName, "cover" to obj.optString("thumbnail", ""), "id" to id)
                                            } else null
                                        } catch (e: Exception) { null }
                                    } else null
                                }
                                val fromPlaylists = allAudiobookDataLocal.mapNotNull { data ->
                                    val src = data["source"] as? String
                                    val cover = data["cover"] as? String
                                    if (!src.isNullOrBlank() && src != "Audiobook") {
                                        mapOf("name" to src, "cover" to cover, "id" to src)
                                    } else null
                                }
                                (fromSubscribed + fromPlaylists).distinctBy { (it["name"] as? String)?.trim()?.lowercase() }
                            }
                            val sortedAudiobookDl = remember(downloadedAudiobooksLocal, audiobookDownloadSort, audiobookDownloadDescending) {
                                val sorted = when (audiobookDownloadSort) {
                                    "Name" -> downloadedAudiobooksLocal.sortedBy { it.title_original.lowercase() }
                                    "Artist" -> downloadedAudiobooksLocal.sortedBy { it.podcast.publisher_original.lowercase() }
                                    else -> downloadedAudiobooksLocal
                                }
                                if (audiobookDownloadDescending) sorted.reversed() else sorted
                            }
                            LazyColumn(
                                modifier = Modifier.fillMaxSize()
                                    .nestedScroll(nestedScrollConnection),
                                contentPadding = PaddingValues(bottom = bottomListPadding)
                            ) {
                                if (currentMediaType == "MUSIC") {
                                    when (currentSubTab) {
                                        "PLAYLISTS" -> {
                                            val searchedPlaylists = if (librarySearchQuery.isNotBlank()) {
                                                allPlaylistData.filter { (it["name"] as String).contains(librarySearchQuery, ignoreCase = true) }
                                            } else { allPlaylistData }
                                            val pinnedPlaylists = pinnedPrefs.getStringSet("names", emptySet()) ?: emptySet()
                                            val sortedPlaylists = when(plSortOrder) {
                                                "A → Z" -> searchedPlaylists.sortedWith(compareByDescending<Map<String, Any>> { pinnedPlaylists.contains(it["name"] as String) }.thenBy { (it["name"] as String).lowercase() })
                                                "Oldest" -> searchedPlaylists.sortedWith(compareByDescending<Map<String, Any>> { pinnedPlaylists.contains(it["name"] as String) }.thenBy { it["time"] as Long })
                                                else -> searchedPlaylists.sortedWith(compareByDescending<Map<String, Any>> { pinnedPlaylists.contains(it["name"] as String) }.thenByDescending { it["time"] as Long })
                                            }
                                            item {
                                                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Box {
                                                            Row(modifier = Modifier.clip(RoundedCornerShape(24.dp)).border(1.dp, Color.Gray.copy(alpha=0.3f), RoundedCornerShape(24.dp)).clickable { showPlSortMenu = true }.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                                Text(text = plSortOrder, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
                                                                Spacer(modifier = Modifier.width(6.dp))
                                                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                                            }
                                                            DropdownMenu(expanded = showPlSortMenu, onDismissRequest = { showPlSortMenu = false }, modifier = Modifier.background(Color(0xFF2D2D2D))) {
                                                                DropdownMenuItem(text = { Text("Recently added", color = Color.White) }, onClick = { plSortOrder = "Recently added"; showPlSortMenu = false })
                                                                DropdownMenuItem(text = { Text("Oldest", color = Color.White) }, onClick = { plSortOrder = "Oldest"; showPlSortMenu = false })
                                                                DropdownMenuItem(text = { Text("A → Z", color = Color.White) }, onClick = { plSortOrder = "A → Z"; showPlSortMenu = false })
                                                            }
                                                        }
                                                        Spacer(modifier = Modifier.width(12.dp))
                                                        Box(modifier = Modifier.clip(RoundedCornerShape(24.dp)).background(Color.White).clickable { showCreatePlaylistDialog = true }.padding(horizontal = 14.dp, vertical = 8.dp)) {
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                                                Spacer(modifier = Modifier.width(4.dp))
                                                                Text(text = "New", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = Color.Black)
                                                            }
                                                        }
                                                    }
                                                    Row(modifier = Modifier.clip(CircleShape).background(Color(0xFF2D2D2D)).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                                        Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(if (!isGridView) accentColor else Color.Transparent).clickable { isGridView = false }, contentAlignment = Alignment.Center) { Icon(Icons.Default.List, contentDescription = null, tint = if (!isGridView) Color.Black else Color.Gray, modifier = Modifier.size(16.dp)) }
                                                        Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(if (isGridView) accentColor else Color.Transparent).clickable { isGridView = true }, contentAlignment = Alignment.Center) { Icon(Icons.Default.GridView, contentDescription = null, tint = if (isGridView) Color.Black else Color.Gray, modifier = Modifier.size(16.dp)) }
                                                    }
                                                }
                                            }
                                            if (isGridView) {
                                                item {
                                                    LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.height(600.dp).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                                        items(sortedPlaylists) { plData ->
                                                            val plName = plData["name"] as String
                                                            LytrixPlaylistGridCard(
                                                                playlistName = plName, sourceText = plData["source"] as String, songCount = plData["count"] as Int, thumbnailUrl = plData["cover"] as String,
                                                                isPinned = pinnedPlaylists.contains(plName),
                                                                onClick = { selectedPlaylistName = plName; viewModel.ytPlaylistType.value = "Playlist"; currentLibraryView = "PLAYLIST_DETAIL" },
                                                                onPlayClick = { selectedPlaylistName = plName; viewModel.ytPlaylistType.value = "Playlist"; currentLibraryView = "PLAYLIST_DETAIL" },
                                                                onRenameClick = { playlistToRename = plName; outerRenameInput = plName; showOuterRenameDialog = true },
                                                                onDeleteClick = { playlistToDelete = plName; showOuterDeleteWarning = true },
                                                                onPinClick = {
                                                                    val set = pinnedPrefs.getStringSet("names", emptySet())?.toMutableSet() ?: mutableSetOf()
                                                                    if (set.contains(plName)) set.remove(plName) else set.add(plName)
                                                                    pinnedPrefs.edit().putStringSet("names", set).apply()
                                                                    refreshTriggerLocal++
                                                                }
                                                            )
                                                        }
                                                    }
                                                }
                                            } else {
                                                items(sortedPlaylists) { plData ->
                                                    val plName = plData["name"] as String
                                                    LytrixPlaylistListCard(
                                                        playlistName = plName, sourceText = plData["source"] as String, thumbnailUrl = plData["cover"] as String, isSynced = false, songCount = plData["count"] as Int,
                                                        isPinned = pinnedPlaylists.contains(plName),
                                                        onClick = { selectedPlaylistName = plName; viewModel.ytPlaylistType.value = "Playlist"; currentLibraryView = "PLAYLIST_DETAIL" },
                                                        onPlayClick = { selectedPlaylistName = plName; viewModel.ytPlaylistType.value = "Playlist"; currentLibraryView = "PLAYLIST_DETAIL" },
                                                        onRenameClick = { playlistToRename = plName; outerRenameInput = plName; showOuterRenameDialog = true },
                                                        onDeleteClick = { playlistToDelete = plName; showOuterDeleteWarning = true },
                                                        onPinClick = {
                                                            val set = pinnedPrefs.getStringSet("names", emptySet())?.toMutableSet() ?: mutableSetOf()
                                                            if (set.contains(plName)) set.remove(plName) else set.add(plName)
                                                            pinnedPrefs.edit().putStringSet("names", set).apply()
                                                            refreshTriggerLocal++
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                        "SONGS" -> {
                                            item {
                                                LazyRow(
                                                    modifier = Modifier.fillMaxWidth().padding(
                                                        start = 16.dp,
                                                        end = 16.dp,
                                                        bottom = 8.dp
                                                    ),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    val filters = if (isSpotifyAvailable) {
                                                        listOf(
                                                            "Liked",
                                                            "Downloaded",
                                                            "JioSaavn",
                                                            "YT Music",
                                                            "Spotify",
                                                            "All Songs"
                                                        )
                                                    } else {
                                                        listOf(
                                                            "Liked",
                                                            "Downloaded",
                                                            "JioSaavn",
                                                            "YT Music",
                                                            "All Songs"
                                                        )
                                                    }
                                                    items(filters) { f ->
                                                        val isSelected = songFilter == f
                                                        val isSpotifyFilter = f == "Spotify"
                                                        val chipBg = when {
                                                            isSelected && isSpotifyFilter -> Color(0xFF1DB954)
                                                            isSelected -> Color(0xFFFFCDD2)
                                                            else -> Color(0xFF2D2D2D)
                                                        }
                                                        val chipTextColor = when {
                                                            isSelected && isSpotifyFilter -> Color.White
                                                            isSelected -> Color.Black
                                                            else -> Color.White
                                                        }
                                                        Box(
                                                            modifier = Modifier.clip(
                                                                RoundedCornerShape(24.dp)
                                                            ).background(chipBg).clickable { songFilter = f }.padding(
                                                                horizontal = 16.dp,
                                                                vertical = 6.dp
                                                            )
                                                        ) {
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                if (isSpotifyFilter) {
                                                                    Icon(
                                                                        painter = painterResource(id = com.musicplayer.lytrix.R.drawable.ic_spotify),
                                                                        contentDescription = null,
                                                                        tint = if (isSelected) Color.White else Color(0xFF1DB954),
                                                                        modifier = Modifier.size(13.dp).padding(end = 4.dp)
                                                                    )
                                                                }
                                                                Text(
                                                                    text = f,
                                                                    color = chipTextColor,
                                                                    fontSize = 13.sp,
                                                                    fontWeight = FontWeight.SemiBold
                                                                )
                                                            }
                                                        }
                                                    }
                                                    item {
                                                        Box {
                                                            Row(
                                                                modifier = Modifier.clip(
                                                                    RoundedCornerShape(24.dp)
                                                                ).border(
                                                                    1.dp,
                                                                    Color.Gray.copy(alpha = 0.3f),
                                                                    RoundedCornerShape(24.dp)
                                                                ).clickable { showSortMenu = true }
                                                                    .padding(
                                                                        horizontal = 12.dp,
                                                                        vertical = 6.dp
                                                                    ),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Text(
                                                                    text = sortOrder,
                                                                    color = Color.White,
                                                                    fontSize = 13.sp,
                                                                    fontWeight = FontWeight.SemiBold
                                                                )
                                                                Spacer(modifier = Modifier.width(4.dp))
                                                                Icon(
                                                                    Icons.Default.KeyboardArrowDown,
                                                                    contentDescription = null,
                                                                    tint = Color.White,
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                            }
                                                            DropdownMenu(
                                                                expanded = showSortMenu,
                                                                onDismissRequest = {
                                                                    showSortMenu = false
                                                                },
                                                                modifier = Modifier.background(
                                                                    Color(0xFF2D2D2D)
                                                                )
                                                            ) {
                                                                DropdownMenuItem(
                                                                    text = {
                                                                        Text(
                                                                            "Recently added",
                                                                            color = Color.White
                                                                        )
                                                                    },
                                                                    onClick = {
                                                                        sortOrder =
                                                                            "Recently added"; showSortMenu =
                                                                        false
                                                                    })
                                                                DropdownMenuItem(
                                                                    text = {
                                                                        Text(
                                                                            "Oldest",
                                                                            color = Color.White
                                                                        )
                                                                    },
                                                                    onClick = {
                                                                        sortOrder =
                                                                            "Oldest"; showSortMenu =
                                                                        false
                                                                    })
                                                                DropdownMenuItem(
                                                                    text = {
                                                                        Text(
                                                                            "A → Z",
                                                                            color = Color.White
                                                                        )
                                                                    },
                                                                    onClick = {
                                                                        sortOrder =
                                                                            "A → Z"; showSortMenu =
                                                                        false
                                                                    })
                                                                DropdownMenuItem(
                                                                    text = {
                                                                        Text(
                                                                            "Artist",
                                                                            color = Color.White
                                                                        )
                                                                    },
                                                                    onClick = {
                                                                        sortOrder =
                                                                            "Artist"; showSortMenu =
                                                                        false
                                                                    })
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            // 🔥 Magic Fix: Combining Liked and Downloaded songs, filtering by 'saavn' in image URL!

                                            // 🔥 ম্যাজিক ফিক্স ২: আইডি নষ্ট থাকলেও নামের সাহায্যে ডুপ্লিকেট গান রিমুভ করবে
                                            val spotifyLikedSongs = if (isSpotifyAvailable) {
                                                com.musicplayer.lytrix.spotify.SpotifyRepository.getCachedLikedSongs(context)
                                            } else {
                                                emptyList()
                                            }
                                            val allLibrarySongs = (likedSongsLocal + downloadedSongsLocal + spotifyLikedSongs).distinctBy { it.name.trim().lowercase() }
                                            val filteredSongs = when (songFilter) {
                                                "Liked" -> likedSongsLocal.reversed()
                                                "Downloaded" -> downloadedSongsLocal.reversed()
                                                "JioSaavn" -> allLibrarySongs.filter { it.imageUrl.contains("saavn", ignoreCase = true) }.reversed()
                                                "YT Music" -> allLibrarySongs.filter { !it.imageUrl.contains("saavn", ignoreCase = true) && !it.id.startsWith("spotify_") }.reversed()
                                                "Spotify" -> spotifyLikedSongs
                                                else -> allLibrarySongs.reversed()
                                            }
                                            val searchedSongs =
                                                if (librarySearchQuery.isNotBlank()) {
                                                    filteredSongs.filter {
                                                        it.name.contains(
                                                            librarySearchQuery,
                                                            ignoreCase = true
                                                        ) || it.artist.contains(
                                                            librarySearchQuery,
                                                            ignoreCase = true
                                                        )
                                                    }
                                                } else {
                                                    filteredSongs
                                                }
                                            val sortedSongs = when (sortOrder) {
                                                "A → Z" -> searchedSongs.sortedBy { it.name }
                                                "Oldest" -> searchedSongs.reversed()
                                                "Artist" -> searchedSongs.sortedBy { it.artist }
                                                else -> searchedSongs
                                            }
                                            item {
                                                Box(
                                                    modifier = Modifier.fillMaxWidth().padding(
                                                        start = 16.dp,
                                                        top = 8.dp,
                                                        end = 16.dp,
                                                        bottom = 12.dp
                                                    ).clip(RoundedCornerShape(24.dp))
                                                        .background(Color(0xFF5D4037)).clickable { }
                                                        .padding(16.dp)
                                                ) {
                                                    Column {
                                                        Text(
                                                            "Your Collection",
                                                            color = Color.White,
                                                            fontSize = 20.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Text(
                                                            "${sortedSongs.size} Songs",
                                                            color = Color.White.copy(alpha = 0.8f),
                                                            fontSize = 14.sp
                                                        )
                                                    }
                                                    IconButton(
                                                        onClick = {
                                                            if (sortedSongs.isNotEmpty()) {
                                                                viewModel.playOnlineSongWithAd(context, sortedSongs.first(), sortedSongs)
                                                            }
                                                        },
                                                        modifier = Modifier.align(Alignment.CenterEnd).background(Color(0xFFFFCDD2), CircleShape)
                                                    ) {
                                                        Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.Black)
                                                    }
                                                }
                                            }
                                            items(sortedSongs) { song ->
                                                PremiumOnlineSongItem(
                                                    song = song,
                                                    onBgColor = onBgColor,
                                                    cardBgColor = cardBgColor,
                                                    onClick = {
                                                        viewModel.playOnlineSongWithAd(context, song, sortedSongs)
                                                    },
                                                    onMoreClick = {
                                                        selectedBottomSong = song; showBottomSheet = true
                                                    })
                                            }
                                        }
                                        "ARTISTS" -> {
                                            item {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Box {
                                                        Row(
                                                            modifier = Modifier.clip(RoundedCornerShape(24.dp)).background(Color(0xFF2D2D2D))
                                                                .clickable { showArtistFilterMenu = true }.padding(horizontal = 14.dp, vertical = 8.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(text = artistFilter, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                                        }
                                                        DropdownMenu(
                                                            expanded = showArtistFilterMenu,
                                                            onDismissRequest = { showArtistFilterMenu = false },
                                                            modifier = Modifier.background(Color(0xFF2D2D2D))
                                                        ) {
                                                            DropdownMenuItem(
                                                                text = { Text("All Artists (Local)", color = Color.White) },
                                                                onClick = { artistFilter = "All Artists"; showArtistFilterMenu = false })
                                                            DropdownMenuItem(
                                                                text = { Text("Subscribed (Online)", color = Color.White) },
                                                                onClick = { artistFilter = "Subscribed"; showArtistFilterMenu = false })
                                                        }
                                                    }
                                                    Row(
                                                        modifier = Modifier.clip(CircleShape).background(Color(0xFF2D2D2D)).padding(4.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Box(
                                                            modifier = Modifier.size(32.dp).clip(CircleShape).background(if (!isArtistGridView) accentColor else Color.Transparent).clickable { isArtistGridView = false },
                                                            contentAlignment = Alignment.Center
                                                        ) { Icon(Icons.Default.List, contentDescription = null, tint = if (!isArtistGridView) Color.Black else Color.Gray, modifier = Modifier.size(16.dp)) }
                                                        Box(
                                                            modifier = Modifier.size(32.dp).clip(CircleShape).background(if (isArtistGridView) accentColor else Color.Transparent).clickable { isArtistGridView = true },
                                                            contentAlignment = Alignment.Center
                                                        ) { Icon(Icons.Default.GridView, contentDescription = null, tint = if (isArtistGridView) Color.Black else Color.Gray, modifier = Modifier.size(16.dp)) }
                                                    }
                                                }
                                            }
                                            val uniqueArtistsLocal = likedSongsLocal.distinctBy { it.artist }.reversed()

                                            // Filter Application
                                            if (artistFilter == "Subscribed") {
                                                val searchedArtists = if (librarySearchQuery.isNotBlank()) {
                                                    subscribedArtistsLocal.filter { it.title.contains(librarySearchQuery, ignoreCase = true) }
                                                } else { subscribedArtistsLocal }
                                                val sortedArtists = if (artistSortOrder == "A → Z") searchedArtists.sortedBy { it.title } else searchedArtists
                                                if (sortedArtists.isEmpty()) {
                                                    item { Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Text("No Subscribed Artists.", color = Color.Gray) } }
                                                } else {
                                                    if (isArtistGridView) {
                                                        val chunks = sortedArtists.chunked(2)
                                                        items(chunks) { rowItems ->
                                                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                                                for (artist in rowItems) {
                                                                    Box(modifier = Modifier.weight(1f).clickable {

                                                                        // 🔥 OnlineMusicScreen থেকে Artist পেজে যাওয়ার জন্য Navigation কল
                                                                        onNavigateToArtist(artist.id)
                                                                    }) {
                                                                        LytrixAlbumCard(title = artist.title, artist = "Artist", thumbnailUrl = artist.thumbnail ?: "")
                                                                    }
                                                                }
                                                                if (rowItems.size == 1) Spacer(modifier = Modifier.weight(1f))
                                                            }
                                                        }
                                                    } else {
                                                        items(sortedArtists) { artist ->
                                                            Box(modifier = Modifier.clickable {

                                                                // 🔥 OnlineMusicScreen থেকে Artist পেজে যাওয়ার জন্য Navigation কল
                                                                onNavigateToArtist(artist.id)
                                                            }) {
                                                                LytrixArtistCard(name = artist.title, thumbnailUrl = artist.thumbnail ?: "")
                                                            }
                                                        }
                                                    }
                                                }
                                            } else {

                                                // Local Artists Logic
                                                val searchedArtists = if (librarySearchQuery.isNotBlank()) {
                                                    uniqueArtistsLocal.filter { it.artist.contains(librarySearchQuery, ignoreCase = true) }
                                                } else { uniqueArtistsLocal }
                                                val sortedArtists = if (artistSortOrder == "A → Z") searchedArtists.sortedBy { it.artist } else searchedArtists
                                                if (isArtistGridView) {
                                                    val chunks = sortedArtists.chunked(2)
                                                    items(chunks) { rowItems ->
                                                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                                            for (artist in rowItems) {
                                                                Box(modifier = Modifier.weight(1f)) {
                                                                    LytrixAlbumCard(title = artist.artist, artist = "Local", thumbnailUrl = artist.imageUrl)
                                                                }
                                                            }
                                                            if (rowItems.size == 1) Spacer(modifier = Modifier.weight(1f))
                                                        }
                                                    }
                                                } else {
                                                    items(sortedArtists) { artist ->
                                                        LytrixArtistCard(name = artist.artist, thumbnailUrl = artist.imageUrl)
                                                    }
                                                }
                                            }
                                        }
                                        "ALBUMS" -> {
                                            item {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(
                                                        horizontal = 16.dp,
                                                        vertical = 8.dp
                                                    ),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Box {
                                                        Row(
                                                            modifier = Modifier.clip(
                                                                RoundedCornerShape(24.dp)
                                                            ).border(
                                                                1.dp,
                                                                Color.Gray.copy(alpha = 0.3f),
                                                                RoundedCornerShape(24.dp)
                                                            ).clickable { showAlbumSortMenu = true }
                                                                .padding(
                                                                    horizontal = 14.dp,
                                                                    vertical = 8.dp
                                                                ),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(
                                                                text = albumSortOrder,
                                                                style = MaterialTheme.typography.labelLarge.copy(
                                                                    fontWeight = FontWeight.SemiBold
                                                                ),
                                                                color = Color.White
                                                            )
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Icon(
                                                                Icons.Default.KeyboardArrowDown,
                                                                contentDescription = null,
                                                                tint = Color.White,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                        DropdownMenu(
                                                            expanded = showAlbumSortMenu,
                                                            onDismissRequest = { showAlbumSortMenu = false },
                                                            modifier = Modifier.background(Color(0xFF2D2D2D))
                                                        ) {
                                                            DropdownMenuItem(
                                                                text = { Text("Newest", color = Color.White) },
                                                                onClick = { albumSortOrder = "Newest"; showAlbumSortMenu = false })
                                                            DropdownMenuItem(
                                                                text = { Text("Oldest", color = Color.White) },
                                                                onClick = { albumSortOrder = "Oldest"; showAlbumSortMenu = false })
                                                            DropdownMenuItem(
                                                                text = { Text("A → Z", color = Color.White) },
                                                                onClick = { albumSortOrder = "A → Z"; showAlbumSortMenu = false })
                                                        }
                                                    }
                                                    Row(
                                                        modifier = Modifier.clip(CircleShape)
                                                            .background(Color(0xFF2D2D2D))
                                                            .padding(4.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Box(
                                                            modifier = Modifier.size(32.dp)
                                                                .clip(CircleShape)
                                                                .background(if (!isAlbumGridView) accentColor else Color.Transparent)
                                                                .clickable { isAlbumGridView = false },
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Icon(Icons.Default.List, contentDescription = null, tint = if (!isAlbumGridView) Color.Black else Color.Gray, modifier = Modifier.size(16.dp))
                                                        }
                                                        Box(
                                                            modifier = Modifier.size(32.dp)
                                                                .clip(CircleShape)
                                                                .background(if (isAlbumGridView) accentColor else Color.Transparent)
                                                                .clickable { isAlbumGridView = true },
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Icon(Icons.Default.GridView, contentDescription = null, tint = if (isAlbumGridView) Color.Black else Color.Gray, modifier = Modifier.size(16.dp))
                                                        }
                                                    }
                                                }
                                            }

                                            // 🔥 Local Album Fetch Logic Apply
                                            val searchedAlbums =
                                                if (librarySearchQuery.isNotBlank()) {
                                                    allAlbumDataLocal.filter {
                                                        (it["name"] as String).contains(
                                                            librarySearchQuery,
                                                            ignoreCase = true
                                                        )
                                                    }
                                                } else {
                                                    allAlbumDataLocal
                                                }
                                            val sortedAlbums = when (albumSortOrder) {
                                                "A → Z" -> searchedAlbums.sortedBy { (it["name"] as String).lowercase() }
                                                "Oldest" -> searchedAlbums.sortedBy { it["time"] as Long }
                                                else -> searchedAlbums.sortedByDescending { it["time"] as Long } // Newest
                                            }
                                            if (sortedAlbums.isEmpty()) {
                                                item {
                                                    Box(
                                                        modifier = Modifier.fillMaxWidth()
                                                            .padding(32.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            "No albums saved yet.",
                                                            color = Color.Gray,
                                                            fontSize = 16.sp
                                                        )
                                                    }
                                                }
                                            } else {
                                                if (isAlbumGridView) {
                                                    item {
                                                        LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.height(600.dp).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                                            items(sortedAlbums) { plData ->
                                                                val plName = plData["name"] as String
                                                                LytrixPlaylistGridCard(
                                                                    playlistName = plName, sourceText = (plData["source"] as? String) ?: "YouTube", songCount = plData["count"] as Int, thumbnailUrl = plData["cover"] as String,
                                                                    isPinned = false,
                                                                    onClick = { selectedPlaylistName = plName; viewModel.ytPlaylistType.value = "Album"; currentLibraryView = "PLAYLIST_DETAIL" },
                                                                    onPlayClick = { selectedPlaylistName = plName; viewModel.ytPlaylistType.value = "Album"; currentLibraryView = "PLAYLIST_DETAIL" },
                                                                    onRenameClick = { playlistToRename = plName; outerRenameInput = plName; showOuterRenameDialog = true },
                                                                    onDeleteClick = { playlistToDelete = plName; showOuterDeleteWarning = true },
                                                                    onPinClick = { }
                                                                )
                                                            }
                                                        }
                                                    }
                                                } else {
                                                    items(sortedAlbums) { plData ->
                                                        val plName = plData["name"] as String
                                                        LytrixPlaylistListCard(
                                                            playlistName = plName, sourceText = (plData["source"] as? String) ?: "YouTube", thumbnailUrl = plData["cover"] as String, isSynced = false, songCount = plData["count"] as Int,
                                                            isPinned = false,
                                                            onClick = { selectedPlaylistName = plName; viewModel.ytPlaylistType.value = "Album"; currentLibraryView = "PLAYLIST_DETAIL" },
                                                            onPlayClick = { selectedPlaylistName = plName; viewModel.ytPlaylistType.value = "Album"; currentLibraryView = "PLAYLIST_DETAIL" },
                                                            onRenameClick = { playlistToRename = plName; outerRenameInput = plName; showOuterRenameDialog = true },
                                                            onDeleteClick = { playlistToDelete = plName; showOuterDeleteWarning = true },
                                                            onPinClick = { }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        "YT PODCASTS" -> {
                                            item {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Box {
                                                        Row(
                                                            modifier = Modifier.clip(RoundedCornerShape(20.dp))
                                                                .background(Color(0xFF2D2D2D))
                                                                .clickable { showPodcastSortMenu = true }
                                                                .padding(horizontal = 14.dp, vertical = 8.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(podcastSortOrder, color = Color.White, fontSize = 13.sp)
                                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                                        }
                                                        DropdownMenu(
                                                            expanded = showPodcastSortMenu,
                                                            onDismissRequest = { showPodcastSortMenu = false },
                                                            modifier = Modifier.background(Color(0xFF2D2D2D))
                                                        ) {
                                                            DropdownMenuItem(
                                                                text = { Text("Newest", color = Color.White) },
                                                                onClick = { podcastSortOrder = "Newest"; showPodcastSortMenu = false })
                                                            DropdownMenuItem(
                                                                text = { Text("Oldest", color = Color.White) },
                                                                onClick = { podcastSortOrder = "Oldest"; showPodcastSortMenu = false })
                                                            DropdownMenuItem(
                                                                text = { Text("A → Z", color = Color.White) },
                                                                onClick = { podcastSortOrder = "A → Z"; showPodcastSortMenu = false })
                                                        }
                                                    }
                                                    Row(
                                                        modifier = Modifier.clip(CircleShape)
                                                            .background(Color(0xFF2D2D2D))
                                                            .padding(4.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Box(
                                                            modifier = Modifier.size(32.dp)
                                                                .clip(CircleShape)
                                                                .background(if (!isPodcastGridView) accentColor else Color.Transparent)
                                                                .clickable { isPodcastGridView = false },
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Icon(Icons.Default.List, contentDescription = null, tint = if (!isPodcastGridView) Color.Black else Color.Gray, modifier = Modifier.size(16.dp))
                                                        }
                                                        Box(
                                                            modifier = Modifier.size(32.dp)
                                                                .clip(CircleShape)
                                                                .background(if (isPodcastGridView) accentColor else Color.Transparent)
                                                                .clickable { isPodcastGridView = true },
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Icon(Icons.Default.GridView, contentDescription = null, tint = if (isPodcastGridView) Color.Black else Color.Gray, modifier = Modifier.size(16.dp))
                                                        }
                                                    }
                                                }
                                            }
                                            val searchedPodcasts =
                                                if (librarySearchQuery.isNotBlank()) {
                                                    allPodcastDataLocal.filter {
                                                        (it["name"] as String).contains(
                                                            librarySearchQuery,
                                                            ignoreCase = true
                                                        )
                                                    }
                                                } else {
                                                    allPodcastDataLocal
                                                }
                                            val sortedPodcasts = when (podcastSortOrder) {
                                                "A → Z" -> searchedPodcasts.sortedBy { (it["name"] as String).lowercase() }
                                                "Oldest" -> searchedPodcasts.sortedBy { it["time"] as Long }
                                                else -> searchedPodcasts.sortedByDescending { it["time"] as Long } // Newest
                                            }
                                            if (sortedPodcasts.isEmpty()) {
                                                item {
                                                    Box(
                                                        modifier = Modifier.fillMaxWidth()
                                                            .padding(32.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            "No podcasts saved yet.",
                                                            color = Color.Gray,
                                                            fontSize = 16.sp
                                                        )
                                                    }
                                                }
                                            } else {
                                                if (isPodcastGridView) {
                                                    item {
                                                        LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.height(600.dp).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                                            items(sortedPodcasts) { plData ->
                                                                val plName = plData["name"] as String
                                                                LytrixPlaylistGridCard(
                                                                    playlistName = plName, sourceText = "Podcast", songCount = plData["count"] as Int, thumbnailUrl = plData["cover"] as String,
                                                                    isPinned = false,
                                                                    onClick = { selectedPlaylistName = plName; viewModel.ytPlaylistType.value = "Podcast"; currentLibraryView = "PLAYLIST_DETAIL" },
                                                                    onPlayClick = { selectedPlaylistName = plName; viewModel.ytPlaylistType.value = "Podcast"; currentLibraryView = "PLAYLIST_DETAIL" },
                                                                    onRenameClick = { playlistToRename = plName; outerRenameInput = plName; showOuterRenameDialog = true },
                                                                    onDeleteClick = { playlistToDelete = plName; showOuterDeleteWarning = true },
                                                                    onPinClick = { }
                                                                )
                                                            }
                                                        }
                                                    }
                                                } else {
                                                    items(sortedPodcasts) { plData ->
                                                        val plName = plData["name"] as String
                                                        LytrixPlaylistListCard(
                                                            playlistName = plName, sourceText = "Podcast", thumbnailUrl = plData["cover"] as String, isSynced = false, songCount = plData["count"] as Int,
                                                            isPinned = false,
                                                            onClick = { selectedPlaylistName = plName; viewModel.ytPlaylistType.value = "Podcast"; currentLibraryView = "PLAYLIST_DETAIL" },
                                                            onPlayClick = { selectedPlaylistName = plName; viewModel.ytPlaylistType.value = "Podcast"; currentLibraryView = "PLAYLIST_DETAIL" },
                                                            onRenameClick = { playlistToRename = plName; outerRenameInput = plName; showOuterRenameDialog = true },
                                                            onDeleteClick = { playlistToDelete = plName; showOuterDeleteWarning = true },
                                                            onPinClick = { }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else if (currentMediaType in listOf("SHOWS", "PODCAST", "AUDIOBOOK")) {
                                    when (podcastSubTab.lowercase()) {
                                        "episodes", "books" -> {
                                            item(key = "pod_auto_new") {
                                                MetroAutoPlaylistCard(
                                                    title = "New episodes",
                                                    subtitle = "Auto playlist",
                                                    coverRes = R.drawable.library_cover_podcasts,
                                                    fallbackIcon = Icons.Default.Mic,
                                                    onClick = {
                                                        selectedPlaylistName = "New episodes"
                                                        viewModel.ytPlaylistType.value = "Podcast"
                                                        currentLibraryView = "PLAYLIST_DETAIL"
                                                    }
                                                )
                                            }
                                            item(key = "pod_auto_later") {
                                                MetroAutoPlaylistCard(
                                                    title = "Episodes for later",
                                                    subtitle = "Auto playlist",
                                                    coverRes = R.drawable.library_cover_episodes_for_later,
                                                    fallbackIcon = Icons.Default.Bookmark,
                                                    onClick = {
                                                        selectedPlaylistName = "Episodes for later"
                                                        viewModel.ytPlaylistType.value = "Podcast"
                                                        currentLibraryView = "PLAYLIST_DETAIL"
                                                    }
                                                )
                                            }
                                            item(key = "ab_auto_continue") {
                                                MetroAutoPlaylistCard(
                                                    title = "Continue Listening",
                                                    subtitle = "Auto playlist",
                                                    coverRes = R.drawable.library_cover_episodes_for_later,
                                                    fallbackIcon = Icons.Default.PlayArrow,
                                                    onClick = {
                                                        selectedPlaylistName = "Continue Listening"
                                                        viewModel.ytPlaylistType.value = "Audiobook"
                                                        currentLibraryView = "PLAYLIST_DETAIL"
                                                    }
                                                )
                                            }
                                            item(key = "ab_auto_later") {
                                                MetroAutoPlaylistCard(
                                                    title = "Books for later",
                                                    subtitle = "Auto playlist",
                                                    coverRes = R.drawable.library_cover_liked,
                                                    fallbackIcon = Icons.Default.Bookmark,
                                                    onClick = {
                                                        selectedPlaylistName = "Books for later"
                                                        viewModel.ytPlaylistType.value = "Audiobook"
                                                        currentLibraryView = "PLAYLIST_DETAIL"
                                                    }
                                                )
                                            }

                                            // 🔥 Custom Shows Playlists
                                            val customShowsPlPrefs = context.getSharedPreferences("online_shows_playlists_meta", Context.MODE_PRIVATE)
                                            val customShowsPlNames = customShowsPlPrefs.getStringSet("names", emptySet())?.toList() ?: emptyList()
                                            item(key = "shows_custom_header") {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(start = 18.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "Shows Playlists",
                                                        style = MaterialTheme.typography.titleSmall,
                                                        color = MaterialTheme.colorScheme.secondary,
                                                        fontWeight = FontWeight.SemiBold,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    Surface(
                                                        onClick = { showCreateShowsPlaylistDialog = true },
                                                        shape = RoundedCornerShape(20.dp),
                                                        color = MaterialTheme.colorScheme.primaryContainer,
                                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                                        ) {
                                                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text(
                                                                text = "New Playlist",
                                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                            if (customShowsPlNames.isNotEmpty()) {
                                                items(customShowsPlNames, key = { "custom_show_pl_$it" }) { plName ->
                                                    val plDataPrefs = context.getSharedPreferences("online_show_pl_$plName", Context.MODE_PRIVATE)
                                                    val count = plDataPrefs.all.size
                                                    val firstItemJson = plDataPrefs.all.values.firstOrNull()?.toString()
                                                    val coverUrl = try {
                                                        if (firstItemJson != null) JSONObject(firstItemJson).optString("imageUrl", "") else ""
                                                    } catch (_: Exception) { "" }
                                                    MetroShowRow(
                                                        title = plName,
                                                        author = "Shows Playlist • $count tracks",
                                                        thumbnailUrl = coverUrl,
                                                        trackCount = count,
                                                        onClick = {
                                                            selectedPlaylistName = plName
                                                            viewModel.ytPlaylistType.value = "ShowsPlaylist"
                                                            currentLibraryView = "PLAYLIST_DETAIL"
                                                        },
                                                        onMenuClick = {
                                                            playlistToDelete = plName
                                                            viewModel.ytPlaylistType.value = "ShowsPlaylist"
                                                            showOuterDeleteWarning = true
                                                        }
                                                    )
                                                }
                                            }

                                            val autoPodcastTitles = setOf("new episodes", "episodes for later")
                                            val searchedShows = allPodcastDataLocal.filter {
                                                val name = (it["name"] as? String)?.trim()?.lowercase().orEmpty()
                                                name !in autoPodcastTitles && (librarySearchQuery.isBlank() || name.contains(librarySearchQuery.trim().lowercase()))
                                            }
                                            val autoAudiobookTitles = setOf("continue listening", "books for later")
                                            val searchedBooks = allAudiobookDataLocal.filter {
                                                val name = (it["name"] as? String)?.trim()?.lowercase().orEmpty()
                                                name !in autoAudiobookTitles && (librarySearchQuery.isBlank() || name.contains(librarySearchQuery.trim().lowercase()))
                                            }
                                            if (searchedShows.isNotEmpty()) {
                                                item(key = "pod_shows_header") {
                                                    MetroSectionLabel("Podcasts")
                                                }
                                                items(searchedShows, key = { "pod_${it["name"]}" }) { podData ->
                                                    val plName = podData["name"] as String
                                                    val src = podData["source"] as? String ?: "Podcast"
                                                    MetroShowRow(
                                                        title = plName,
                                                        author = "$src • Podcast",
                                                        thumbnailUrl = podData["cover"] as String,
                                                        trackCount = podData["count"] as? Int,
                                                        onClick = {
                                                            selectedPlaylistName = plName
                                                            viewModel.ytPlaylistType.value = "Podcast"
                                                            currentLibraryView = "PLAYLIST_DETAIL"
                                                        },
                                                        onMenuClick = {
                                                            playlistToDelete = plName
                                                            viewModel.ytPlaylistType.value = "Podcast"
                                                            showOuterDeleteWarning = true
                                                        }
                                                    )
                                                }
                                            }
                                            if (searchedBooks.isNotEmpty()) {
                                                item(key = "ab_header") {
                                                    MetroSectionLabel("Audiobooks")
                                                }
                                                items(searchedBooks, key = { "ab_${it["name"]}" }) { abData ->
                                                    val abName = abData["name"] as String
                                                    val src = abData["source"] as? String ?: "Audiobook"
                                                    MetroShowRow(
                                                        title = abName,
                                                        author = "$src • Audiobook",
                                                        thumbnailUrl = abData["cover"] as String,
                                                        trackCount = abData["count"] as? Int,
                                                        onClick = {
                                                            selectedPlaylistName = abName
                                                            viewModel.ytPlaylistType.value = "Audiobook"
                                                            currentLibraryView = "PLAYLIST_DETAIL"
                                                        },
                                                        onMenuClick = {
                                                            playlistToDelete = abName
                                                            viewModel.ytPlaylistType.value = "Audiobook"
                                                            showOuterDeleteWarning = true
                                                        }
                                                    )
                                                }
                                            }
                                            if (searchedShows.isEmpty() && searchedBooks.isEmpty()) {
                                                item(key = "shows_empty") {
                                                    MetroEmptyState("Your library is empty")
                                                }
                                            }
                                        }
                                        "channels", "authors" -> {
                                            if (podcastChannels.isNotEmpty()) {
                                                item(key = "channels_header") {
                                                    MetroSectionLabel("Podcast Channels")
                                                }
                                                items(podcastChannels, key = { "chan_${it["name"]}" }) { channel ->
                                                    val name = channel["name"] as String
                                                    val cover = channel["cover"] as? String
                                                    MetroChannelRow(
                                                        name = name,
                                                        thumbnailUrl = cover,
                                                        subtitle = "Podcast Channel",
                                                        onClick = {
                                                            val chanId = channel["id"] as? String
                                                            if (!chanId.isNullOrBlank()) {
                                                                onNavigateToArtist(chanId)
                                                            } else {
                                                                onNavigateToArtist(name)
                                                            }
                                                        }
                                                    )
                                                }
                                            }
                                            if (audiobookAuthors.isNotEmpty()) {
                                                item(key = "ab_authors_header") {
                                                    MetroSectionLabel("Audiobook Authors")
                                                }
                                                items(audiobookAuthors, key = { "ab_auth_${it["name"]}" }) { author ->
                                                    val name = author["name"] as String
                                                    val cover = author["cover"] as? String
                                                    MetroChannelRow(
                                                        name = name,
                                                        thumbnailUrl = cover,
                                                        subtitle = "Author",
                                                        onClick = {
                                                            selectedPlaylistName = name
                                                            viewModel.ytPlaylistType.value = "Audiobook"
                                                            currentLibraryView = "PLAYLIST_DETAIL"
                                                        }
                                                    )
                                                }
                                            }
                                            if (podcastChannels.isEmpty() && audiobookAuthors.isEmpty()) {
                                                item(key = "no_channels_authors") {
                                                    MetroEmptyState("No subscribed channels or authors")
                                                }
                                            }
                                        }
                                        "downloaded" -> {
                                            val totalDl = downloadedPodcastsLocal.size + downloadedAudiobooksLocal.size
                                            if (totalDl > 0) {
                                                item(key = "shows_dl_sort") {
                                                    MetroDownloadSortHeader(
                                                        sort = podcastDownloadSort,
                                                        descending = podcastDownloadDescending,
                                                        onSortSelected = {
                                                            podcastDownloadSort = it
                                                            audiobookDownloadSort = it
                                                        },
                                                        onToggleDirection = {
                                                            podcastDownloadDescending = !podcastDownloadDescending
                                                            audiobookDownloadDescending = !audiobookDownloadDescending
                                                        },
                                                        count = totalDl
                                                    )
                                                }
                                                if (downloadedPodcastsLocal.isNotEmpty()) {
                                                    items(sortedPodcastDl, key = { "dl_pod_${it.id}" }) { ep ->
                                                        MetroEpisodeRow(
                                                            title = ep.title,
                                                            subtitle = "${ep.publisher} • Podcast",
                                                            thumbnailUrl = ep.image,
                                                            durationText = if (ep.durationMs > 0) "${ep.durationMs / 60000} min" else null,
                                                            onClick = {
                                                                val podEp = com.musicplayer.lytrix.PodcastEpisode(
                                                                    id = ep.id,
                                                                    title_original = ep.title,
                                                                    audio = ep.audioUrl,
                                                                    image = ep.image,
                                                                    audio_length_sec = (ep.durationMs / 1000).toInt(),
                                                                    podcast = com.musicplayer.lytrix.PodcastInfo(
                                                                        publisher_original = ep.publisher
                                                                    )
                                                                )
                                                                viewModel.playPodcastWithAd(context, podEp, listOf(podEp))
                                                            },
                                                            onMenuClick = {
                                                                selectedBottomPodcast = com.musicplayer.lytrix.PodcastEpisode(
                                                                    id = ep.id,
                                                                    title_original = ep.title,
                                                                    audio = ep.audioUrl,
                                                                    image = ep.image,
                                                                    audio_length_sec = (ep.durationMs / 1000).toInt(),
                                                                    podcast = com.musicplayer.lytrix.PodcastInfo(
                                                                        publisher_original = ep.publisher
                                                                    )
                                                                )
                                                                selectedBottomSong = null
                                                                showBottomSheet = true
                                                            }
                                                        )
                                                    }
                                                }
                                                if (downloadedAudiobooksLocal.isNotEmpty()) {
                                                    items(sortedAudiobookDl, key = { "dl_ab_${it.id}" }) { ep ->
                                                        MetroEpisodeRow(
                                                            title = ep.title_original,
                                                            subtitle = "${ep.podcast.publisher_original} • Audiobook",
                                                            thumbnailUrl = ep.image,
                                                            durationText = if (ep.audio_length_sec > 0) "${ep.audio_length_sec / 60} min" else null,
                                                            onClick = {
                                                                viewModel.playAudiobookWithAd(context, ep, sortedAudiobookDl)
                                                            },
                                                            onMenuClick = {
                                                                selectedBottomPodcast = ep
                                                                selectedBottomSong = null
                                                                showBottomSheet = true
                                                            }
                                                        )
                                                    }
                                                }
                                            } else {
                                                item(key = "no_dl_shows") {
                                                    MetroEmptyState("No downloaded shows")
                                                }
                                            }
                                        }
                                        "liked" -> {
                                            val totalLiked = likedPodcastsListLocal.size + likedAudiobooksListLocal.size
                                            if (totalLiked > 0) {
                                                item(key = "shows_liked_header") {
                                                    MetroSectionLabel("Liked Shows ($totalLiked)")
                                                }
                                                items(likedPodcastsListLocal, key = { "liked_pod_${it.id}" }) { ep ->
                                                    MetroEpisodeRow(
                                                        title = ep.title_original,
                                                        subtitle = "${ep.podcast.publisher_original} • Podcast",
                                                        thumbnailUrl = ep.image,
                                                        durationText = if (ep.audio_length_sec > 0) "${ep.audio_length_sec / 60} min" else null,
                                                        onClick = {
                                                            viewModel.playPodcastWithAd(context, ep, likedPodcastsListLocal)
                                                        },
                                                        onMenuClick = {
                                                            selectedBottomPodcast = ep
                                                            selectedBottomSong = null
                                                            showBottomSheet = true
                                                        }
                                                    )
                                                }
                                                items(likedAudiobooksListLocal, key = { "liked_ab_${it.id}" }) { ep ->
                                                    MetroEpisodeRow(
                                                        title = ep.title_original,
                                                        subtitle = "${ep.podcast.publisher_original} • Audiobook",
                                                        thumbnailUrl = ep.image,
                                                        durationText = if (ep.audio_length_sec > 0) "${ep.audio_length_sec / 60} min" else null,
                                                        onClick = {
                                                            viewModel.playAudiobookWithAd(context, ep, likedAudiobooksListLocal)
                                                        },
                                                        onMenuClick = {
                                                            selectedBottomPodcast = ep
                                                            selectedBottomSong = null
                                                            showBottomSheet = true
                                                        }
                                                    )
                                                }
                                            } else {
                                                item(key = "no_liked_shows") {
                                                    MetroEmptyState("No liked shows")
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // 🔥 Dialogs moved outside LazyColumn to prevent hidden scroll bug
                            if (showOuterDeleteWarning) {
                                AlertDialog(
                                    onDismissRequest = { showOuterDeleteWarning = false },
                                    containerColor = Color(0xFF1C1C1C),
                                    title = { Text("Delete Playlist?", color = Color.White, fontWeight = FontWeight.Bold) },
                                    text = { Text("Are you sure you want to delete this playlist? This action cannot be undone.", color = Color.LightGray) },
                                    confirmButton = {
                                        TextButton(onClick = {

                                            // 🔥 অ্যালবাম/পডকাস্ট/অডিওবুক/প্লেলিস্ট চেক করে ডিলিট করবে
                                            val currentDbType = viewModel.ytPlaylistType.value
                                            val dbName = when {
                                                currentMediaType == "PODCAST" || currentDbType == "Podcast" -> "online_podcasts_meta"
                                                currentMediaType == "AUDIOBOOK" || currentDbType == "Audiobook" -> "online_audiobooks_meta"
                                                currentSubTab == "ALBUMS" || currentDbType == "Album" -> "online_albums_meta"
                                                else -> "online_playlists_meta"
                                            }
                                            val prefix = when {
                                                currentMediaType == "PODCAST" || currentDbType == "Podcast" -> "online_podcast_"
                                                currentMediaType == "AUDIOBOOK" || currentDbType == "Audiobook" -> "online_ab_"
                                                currentSubTab == "ALBUMS" || currentDbType == "Album" -> "online_album_"
                                                else -> "online_pl_"
                                            }
                                            val targetPrefs = context.getSharedPreferences(dbName, Context.MODE_PRIVATE)
                                            val set = targetPrefs.getStringSet("names", emptySet())?.toMutableSet() ?: mutableSetOf()
                                            set.remove(playlistToDelete)
                                            targetPrefs.edit().putStringSet("names", set).remove("count_$playlistToDelete").apply()

                                            // ভেতরের গান এবং কভার ছবি ডিলিট করা
                                            context.getSharedPreferences("$prefix$playlistToDelete", Context.MODE_PRIVATE).edit().clear().apply()
                                            coverPrefs.edit().remove(playlistToDelete).apply()
                                            sourcePrefs.edit().remove(playlistToDelete).apply()
                                            idPrefsMain.edit().remove(playlistToDelete).apply()
                                            val file = File(context.filesDir, "cover_${playlistToDelete.replace(" ", "_")}.jpg")
                                            if(file.exists()) file.delete()
                                            refreshTriggerLocal++
                                            showOuterDeleteWarning = false
                                            currentLibraryView = "MAIN"
                                        }) { Text("Delete", color = Color.Red, fontWeight = FontWeight.Bold) }
                                    },
                                    dismissButton = { TextButton(onClick = { showOuterDeleteWarning = false }) { Text("Cancel", color = Color.White) } }
                                )
                            }
                            if (showOuterRenameDialog) {
                                AlertDialog(
                                    onDismissRequest = { showOuterRenameDialog = false }, containerColor = cardBgColor,
                                    title = { Text("Rename Playlist", color = Color.White) },
                                    text = { OutlinedTextField(value = outerRenameInput, onValueChange = { outerRenameInput = it }, singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)) },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            val trimmedNew = outerRenameInput.trim()
                                            if (trimmedNew.isNotBlank() && trimmedNew != playlistToRename) {
                                                val set = plPrefs.getStringSet("names", emptySet())?.toMutableSet() ?: mutableSetOf()
                                                set.remove(playlistToRename)
                                                set.add(trimmedNew)
                                                val oldCount = plPrefs.getInt("count_$playlistToRename", -1)
                                                plPrefs.edit().putStringSet("names", set).remove("count_$playlistToRename").apply {
                                                    if (oldCount >= 0) putInt("count_$trimmedNew", oldCount)
                                                }.apply()
                                                val oldSongsPrefs = context.getSharedPreferences("online_pl_$playlistToRename", Context.MODE_PRIVATE)
                                                val newSongsPrefs = context.getSharedPreferences("online_pl_$trimmedNew", Context.MODE_PRIVATE)
                                                val editor = newSongsPrefs.edit()
                                                oldSongsPrefs.all.forEach { (key, value) -> editor.putString(key, value.toString()) }
                                                editor.apply()
                                                oldSongsPrefs.edit().clear().apply()
                                                val oldFile = File(context.filesDir, "cover_${playlistToRename.replace(" ", "_")}.jpg")
                                                val newFile = File(context.filesDir, "cover_${trimmedNew.replace(" ", "_")}.jpg")
                                                val coverUri = coverPrefs.getString(playlistToRename, null)
                                                val migratedCover = if (oldFile.exists() && coverUri == oldFile.absolutePath) {
                                                    oldFile.renameTo(newFile)
                                                    newFile.absolutePath
                                                } else {
                                                    if (oldFile.exists()) oldFile.renameTo(newFile)
                                                    coverUri
                                                }
                                                if (migratedCover != null) coverPrefs.edit().remove(playlistToRename).putString(trimmedNew, migratedCover).apply()
                                                val oldTime = timePrefs.getLong(playlistToRename, System.currentTimeMillis())
                                                val oldSource = sourcePrefs.getString(playlistToRename, "Custom") ?: "Custom"
                                                timePrefs.edit().remove(playlistToRename).putLong(trimmedNew, oldTime).apply()
                                                sourcePrefs.edit().remove(playlistToRename).putString(trimmedNew, oldSource).apply()
                                                val pinnedSet = pinnedPrefs.getStringSet("names", emptySet())?.toMutableSet() ?: mutableSetOf()
                                                if (pinnedSet.remove(playlistToRename)) {
                                                    pinnedSet.add(trimmedNew)
                                                    pinnedPrefs.edit().putStringSet("names", pinnedSet).apply()
                                                }
                                                refreshTriggerLocal++
                                                showOuterRenameDialog = false
                                            }
                                        }) { Text("Save", color = accentColor) }
                                    },
                                    dismissButton = { TextButton(onClick = { showOuterRenameDialog = false }) { Text("Cancel", color = Color.White) } }
                                )
                            }
                        }
                    } else if (currentLibraryView == "LIKED") {
                        val lazyListState = rememberLazyListState()
                        val isScrolled by remember { derivedStateOf { lazyListState.firstVisibleItemIndex > 0 } }
                        Box(modifier = Modifier.fillMaxSize()) {
                            LazyColumn(
                                state = lazyListState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = bottomListPadding)
                            ) {
                                item {
                                    LytrixMediaDetailHero(
                                        title = "Liked Songs",
                                        songCount = likedSongsLocal.size,
                                        thumbnailUrl = likedSongsLocal.lastOrNull()?.imageUrl ?: "",
                                        onPlay = {
                                            if (likedSongsLocal.isNotEmpty()) {
                                                viewModel.playOnlineSongWithAd(context, likedSongsLocal.reversed().first(), likedSongsLocal.reversed())
                                            }
                                        },
                                        onShuffle = {
                                            if (likedSongsLocal.isNotEmpty()) {
                                                viewModel.playOnlineSongWithAd(context, likedSongsLocal.random(), likedSongsLocal.shuffled())
                                            }
                                        })
                                }
                                items(likedSongsLocal.reversed()) { song ->
                                    PremiumOnlineSongItem(
                                        song = song,
                                        onBgColor = onBgColor,
                                        cardBgColor = cardBgColor,
                                        onClick = {
                                            viewModel.playOnlineSongWithAd(context, song, likedSongsLocal.reversed())
                                        },
                                        onMoreClick = {
                                            selectedBottomSong = song; showBottomSheet = true
                                        })
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .background(if (isScrolled) bgColor else Color.Transparent)
                                    .padding(top = 32.dp, start = 8.dp, end = 8.dp, bottom = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = {
                                    currentLibraryView = "MAIN"
                                }) {
                                    Icon(
                                        Icons.Default.ArrowBack,
                                        contentDescription = "Back",
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    } else if (currentLibraryView == "LIKED_PODCASTS") {
                        MetroLibraryMediaCollectionScreen(
                            title = "Liked Podcasts",
                            subtitle = "${likedPodcastsListLocal.size} episodes",
                            kind = com.musicplayer.lytrix.media.MediaHubKind.PODCAST,
                            episodes = likedPodcastsListLocal,
                            isDownloadedView = false,
                            context = context,
                            viewModel = viewModel,
                            onBack = { currentLibraryView = "MAIN" },
                            onToggleLike = { ep ->
                                toggleMediaHubEpisodeLiked(context, com.musicplayer.lytrix.media.MediaHubKind.PODCAST, ep)
                                refreshTriggerLocal++
                            },
                            onDownload = { ep -> downloadPodcastSecurely(context, ep) },
                            bottomPadding = bottomListPadding,
                        )
                    } else if (currentLibraryView == "LIKED_AUDIOBOOKS") {
                        MetroLibraryMediaCollectionScreen(
                            title = "Liked Audiobooks",
                            subtitle = "${likedAudiobooksListLocal.size} items",
                            kind = com.musicplayer.lytrix.media.MediaHubKind.AUDIOBOOK,
                            episodes = likedAudiobooksListLocal,
                            isDownloadedView = false,
                            context = context,
                            viewModel = viewModel,
                            onBack = { currentLibraryView = "MAIN" },
                            onToggleLike = { ep ->
                                toggleMediaHubEpisodeLiked(context, com.musicplayer.lytrix.media.MediaHubKind.AUDIOBOOK, ep)
                                refreshTriggerLocal++
                            },
                            onDownload = { ep -> downloadAudiobookSecurely(context, ep) },
                            bottomPadding = bottomListPadding,
                        )
                    } else if (currentLibraryView == "PODCAST_DOWNLOADS") {
                        val dlPodcastEpisodes = remember(downloadedPodcastsLocal) {
                            downloadedPodcastsLocal.map { dbPodcast ->
                                com.musicplayer.lytrix.PodcastEpisode(
                                    id = dbPodcast.id,
                                    title_original = dbPodcast.title,
                                    audio = dbPodcast.audioUrl,
                                    image = dbPodcast.image,
                                    audio_length_sec = (dbPodcast.durationMs / 1000).toInt(),
                                    podcast = com.musicplayer.lytrix.PodcastInfo(dbPodcast.publisher),
                                )
                            }
                        }
                        MetroLibraryMediaCollectionScreen(
                            title = "Downloaded Podcasts",
                            subtitle = "${dlPodcastEpisodes.size} offline episodes",
                            kind = com.musicplayer.lytrix.media.MediaHubKind.PODCAST,
                            episodes = dlPodcastEpisodes,
                            isDownloadedView = true,
                            context = context,
                            viewModel = viewModel,
                            onBack = { currentLibraryView = "MAIN" },
                            onToggleLike = { },
                            onEpisodeClick = { clickedEp, queue ->
                                val offlineQueue = queue.map { ep ->
                                    val localPath = context.getExternalFilesDir(Environment.DIRECTORY_PODCASTS)?.absolutePath + "/${ep.id}.mp3"
                                    com.musicplayer.lytrix.model.Song(
                                        id = ep.id.hashCode().toLong(),
                                        title = ep.title_original ?: "Podcast",
                                        artist = ep.podcast?.publisher_original ?: "Podcast",
                                        album = "Offline Podcast",
                                        duration = ep.audio_length_sec * 1000L,
                                        uriString = "file://$localPath",
                                        filePath = "OFFLINE_PODCAST",
                                        albumArtUriString = ep.image ?: "",
                                        isVideo = false,
                                        hasLyrics = false,
                                    )
                                }
                                val target = offlineQueue.firstOrNull { it.id == clickedEp.id.hashCode().toLong() } ?: offlineQueue.first()
                                viewModel.playSong(target, offlineQueue)
                            },
                            onDeleteDownload = { ep ->
                                val match = downloadedPodcastsLocal.firstOrNull { it.id == ep.id }
                                if (match != null) {
                                    val localFile = File(context.getExternalFilesDir(Environment.DIRECTORY_PODCASTS), "${match.id}.mp3")
                                    if (localFile.exists()) localFile.delete()
                                    scope.launch(Dispatchers.IO) { podcastDao.deletePodcast(match) }
                                }
                            },
                            bottomPadding = bottomListPadding,
                        )
                    } else if (currentLibraryView == "AUDIOBOOK_DOWNLOADS") {
                        MetroLibraryMediaCollectionScreen(
                            title = "Downloaded Audiobooks",
                            subtitle = "${downloadedAudiobooksLocal.size} offline books",
                            kind = com.musicplayer.lytrix.media.MediaHubKind.AUDIOBOOK,
                            episodes = downloadedAudiobooksLocal,
                            isDownloadedView = true,
                            context = context,
                            viewModel = viewModel,
                            onBack = { currentLibraryView = "MAIN" },
                            onToggleLike = { },
                            onEpisodeClick = { clickedEp, queue ->
                                val offlineQueue = queue.map { ep ->
                                    val file = File(context.getExternalFilesDir(Environment.DIRECTORY_PODCASTS), "AB_${ep.id}.mp3")
                                    com.musicplayer.lytrix.model.Song(
                                        id = ep.id.hashCode().toLong(),
                                        title = ep.title_original ?: "Audiobook",
                                        artist = ep.podcast?.publisher_original ?: "Audiobook",
                                        album = "Offline Audiobook",
                                        duration = ep.audio_length_sec * 1000L,
                                        uriString = "file://${file.absolutePath}",
                                        filePath = "OFFLINE_PODCAST",
                                        albumArtUriString = ep.image ?: "",
                                        isVideo = false,
                                        hasLyrics = false,
                                    )
                                }
                                val target = offlineQueue.firstOrNull { it.id == clickedEp.id.hashCode().toLong() } ?: offlineQueue.first()
                                viewModel.playSong(target, offlineQueue)
                            },
                            onDeleteDownload = { ep ->
                                val file = File(context.getExternalFilesDir(Environment.DIRECTORY_PODCASTS), "AB_${ep.id}.mp3")
                                if (file.exists()) file.delete()
                                context.getSharedPreferences("downloaded_audiobooks_meta", Context.MODE_PRIVATE)
                                    .edit().remove("audiobook_${ep.id}").apply()
                                refreshTriggerLocal++
                            },
                            bottomPadding = bottomListPadding,
                        )
                    } else if (currentLibraryView == "DOWNLOADS") {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .padding(top = 32.dp, bottom = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = {
                                    currentLibraryView = "MAIN"
                                }) {
                                    Icon(
                                        Icons.Default.ArrowBack,
                                        contentDescription = "Back",
                                        tint = Color.White
                                    )
                                }
                                Text(
                                    "Offline & Downloaded",
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = bottomListPadding)
                            ) {
                                items(downloadedSongsLocal) { song ->
                                    PremiumOnlineSongItem(
                                        song = song,
                                        onBgColor = onBgColor,
                                        cardBgColor = cardBgColor,
                                        onClick = {
                                            viewModel.playOnlineSongWithAd(context, song, downloadedSongsLocal)
                                        },
                                        onMoreClick = {
                                            selectedBottomSong = song; showBottomSheet = true
                                        })
                                }
                            }
                        }
                    } else if (currentLibraryView == "PLAYLIST_DETAIL") {
                        val rawDbType = viewModel.ytPlaylistType.collectAsState().value
                        val isShowsPlaylist = remember(selectedPlaylistName, refreshTriggerLocal) {
                            context.getSharedPreferences("online_shows_playlists_meta", Context.MODE_PRIVATE)
                                .getStringSet("names", emptySet())?.contains(selectedPlaylistName) == true
                        }
                        val isMusicPlaylist = remember(selectedPlaylistName, refreshTriggerLocal) {
                            context.getSharedPreferences("online_playlists_meta", Context.MODE_PRIVATE)
                                .getStringSet("names", emptySet())?.contains(selectedPlaylistName) == true
                        }
                        val isPodcastShow = remember(selectedPlaylistName, refreshTriggerLocal) {
                            context.getSharedPreferences("online_podcasts_meta", Context.MODE_PRIVATE)
                                .getStringSet("names", emptySet())?.contains(selectedPlaylistName) == true
                        }
                        val isAudiobookShow = remember(selectedPlaylistName, refreshTriggerLocal) {
                            context.getSharedPreferences("online_audiobooks_meta", Context.MODE_PRIVATE)
                                .getStringSet("names", emptySet())?.contains(selectedPlaylistName) == true
                        }
                        val isAlbumShow = remember(selectedPlaylistName, refreshTriggerLocal) {
                            context.getSharedPreferences("online_albums_meta", Context.MODE_PRIVATE)
                                .getStringSet("names", emptySet())?.contains(selectedPlaylistName) == true
                        }
                        val dbType = when {
                            isShowsPlaylist -> "ShowsPlaylist"
                            isMusicPlaylist -> "Playlist"
                            isPodcastShow -> "Podcast"
                            isAudiobookShow -> "Audiobook"
                            isAlbumShow -> "Album"
                            selectedPlaylistName.trim().lowercase() in setOf("episodes for later", "new episodes", "continue listening") -> "Podcast"
                            selectedPlaylistName.trim().lowercase() == "books for later" -> "Audiobook"
                            else -> rawDbType
                        }
                        val dbName = when (dbType) {
                            "Album" -> "online_albums_meta"
                            "Podcast" -> "online_podcasts_meta"
                            "Audiobook" -> "online_audiobooks_meta"
                            "ShowsPlaylist" -> "online_shows_playlists_meta"
                            else -> "online_playlists_meta"
                        }
                        val prefix = when (dbType) {
                            "Album" -> "online_album_"
                            "Podcast" -> "online_podcast_"
                            "Audiobook" -> "online_ab_"
                            "ShowsPlaylist" -> "online_show_pl_"
                            else -> "online_pl_"
                        }
                        val plPrefs = context.getSharedPreferences(dbName, Context.MODE_PRIVATE)
                        val plSongsPrefs = context.getSharedPreferences("$prefix$selectedPlaylistName", Context.MODE_PRIVATE)
                        val coverPrefs = context.getSharedPreferences("playlist_covers", Context.MODE_PRIVATE)
                        val idPrefs = context.getSharedPreferences("playlist_ids", Context.MODE_PRIVATE) // 🔥 আইডি রিড করা
                        var customCoverUri by remember(selectedPlaylistName, refreshTriggerLocal) { mutableStateOf(coverPrefs.getString(selectedPlaylistName, null)) }
                        var showCoverMenu by remember { mutableStateOf(false) }
                        var songToRemoveFromPlaylist by remember { mutableStateOf<JioSaavnSong?>(null) }

                        if (dbType == "Podcast" || dbType == "Audiobook" || dbType == "ShowsPlaylist") {
                            val autoCoverRes: Any? = when (selectedPlaylistName.trim().lowercase()) {
                                "new episodes" -> com.musicplayer.lytrix.R.drawable.library_cover_podcasts
                                "episodes for later" -> com.musicplayer.lytrix.R.drawable.library_cover_episodes_for_later
                                "continue listening" -> com.musicplayer.lytrix.R.drawable.library_cover_episodes_for_later
                                "books for later" -> com.musicplayer.lytrix.R.drawable.library_cover_liked
                                else -> null
                            }
                            val podcastSongs = remember(selectedPlaylistName, refreshTriggerLocal) {
                                val lower = selectedPlaylistName.trim().lowercase()
                                when (lower) {
                                    "new episodes" -> {
                                        val direct = plSongsPrefs.all.values.mapNotNull { value ->
                                            try {
                                                val jsonStr = value.toString()
                                                if (jsonStr.startsWith("{")) {
                                                    val obj = JSONObject(jsonStr)
                                                    JioSaavnSong(
                                                        id = obj.optString("id", ""),
                                                        name = obj.optString("name", obj.optString("title", "Episode")),
                                                        artist = obj.optString("artist", obj.optString("publisher", "")),
                                                        imageUrl = obj.optString("imageUrl", obj.optString("image", "")),
                                                        downloadUrl = obj.optString("downloadUrl", obj.optString("mediaUrl", obj.optString("audio", ""))),
                                                        albumName = obj.optString("album", selectedPlaylistName),
                                                    )
                                                } else null
                                            } catch (e: Exception) { null }
                                        }
                                        val fromShows = allPodcastDataLocal.flatMap { podData ->
                                            val plName = podData["name"] as String
                                            if (plName.trim().lowercase() !in setOf("new episodes", "episodes for later", "continue listening", "books for later")) {
                                                val prefs = context.getSharedPreferences("online_podcast_$plName", Context.MODE_PRIVATE)
                                                prefs.all.values.mapNotNull { v ->
                                                    try {
                                                        val jsonStr = v.toString()
                                                        if (jsonStr.startsWith("{")) {
                                                            val obj = JSONObject(jsonStr)
                                                            JioSaavnSong(
                                                                id = obj.optString("id", ""),
                                                                name = obj.optString("name", obj.optString("title", "Episode")),
                                                                artist = obj.optString("artist", obj.optString("publisher", "")),
                                                                imageUrl = obj.optString("imageUrl", obj.optString("image", "")),
                                                                downloadUrl = obj.optString("downloadUrl", obj.optString("mediaUrl", obj.optString("audio", ""))),
                                                                albumName = obj.optString("album", plName),
                                                            )
                                                        } else null
                                                    } catch (e: Exception) { null }
                                                }.take(5)
                                            } else emptyList()
                                        }
                                        (direct + fromShows).distinctBy { it.id }
                                    }
                                    "continue listening" -> {
                                        val abPrefs = context.getSharedPreferences("online_ab_Continue Listening", Context.MODE_PRIVATE)
                                        val podPrefs = context.getSharedPreferences("online_podcast_Continue Listening", Context.MODE_PRIVATE)
                                        val direct = (abPrefs.all.values + podPrefs.all.values).mapNotNull { value ->
                                            try {
                                                val jsonStr = value.toString()
                                                if (jsonStr.startsWith("{")) {
                                                    val obj = JSONObject(jsonStr)
                                                    JioSaavnSong(
                                                        id = obj.optString("id", ""),
                                                        name = obj.optString("name", obj.optString("title", "Episode")),
                                                        artist = obj.optString("artist", obj.optString("publisher", "")),
                                                        imageUrl = obj.optString("imageUrl", obj.optString("image", "")),
                                                        downloadUrl = obj.optString("downloadUrl", obj.optString("mediaUrl", obj.optString("audio", ""))),
                                                        albumName = obj.optString("album", "Continue Listening"),
                                                    )
                                                } else null
                                            } catch (e: Exception) { null }
                                        }
                                        direct.distinctBy { it.id }
                                    }
                                    else -> {
                                        plSongsPrefs.all.values.mapNotNull { value ->
                                            try {
                                                val jsonStr = value.toString()
                                                if (jsonStr.startsWith("{")) {
                                                    val obj = JSONObject(jsonStr)
                                                    JioSaavnSong(
                                                        id = obj.optString("id", ""),
                                                        name = obj.optString("name", obj.optString("title", "Episode")),
                                                        artist = obj.optString("artist", obj.optString("publisher", "")),
                                                        imageUrl = obj.optString("imageUrl", obj.optString("image", "")),
                                                        downloadUrl = obj.optString("downloadUrl", obj.optString("mediaUrl", obj.optString("audio", ""))),
                                                        albumName = obj.optString("album", selectedPlaylistName),
                                                    )
                                                } else null
                                            } catch (e: Exception) { null }
                                        }
                                    }
                                }
                            }
                            val isShowSaved = plPrefs.getStringSet("names", emptySet())?.contains(selectedPlaylistName) == true
                            val showAuthor = sourcePrefs.getString(selectedPlaylistName, null)?.takeIf { it != "Podcast" && it != "Audiobook" }
                                ?: podcastSongs.firstOrNull()?.artist
                            val heroThumb: Any = customCoverUri ?: autoCoverRes ?: podcastSongs.firstOrNull()?.imageUrl.orEmpty()

                            val targetCategory = if (dbType == "Audiobook") "AUDIOBOOK" else "PODCAST"
                            val laterName = if (dbType == "Podcast") "Episodes for later" else "Books for later"
                            val targetPrefix = if (dbType == "Podcast") "online_podcast_" else "online_ab_"
                            val targetMeta = if (dbType == "Podcast") "online_podcasts_meta" else "online_audiobooks_meta"
                            val laterPrefs = remember(dbType, laterName, refreshTriggerLocal) { context.getSharedPreferences("$targetPrefix$laterName", Context.MODE_PRIVATE) }
                            val savedForLaterIds = remember(laterPrefs, refreshTriggerLocal) { laterPrefs.all.keys.toSet() }
                            val likedPrefName = if (dbType == "Audiobook") "online_liked_audiobooks" else "online_liked_podcasts"
                            val likedEpisodePrefs = remember(likedPrefName, refreshTriggerLocal) { context.getSharedPreferences(likedPrefName, Context.MODE_PRIVATE) }
                            val likedEpisodeIds = remember(likedEpisodePrefs, refreshTriggerLocal) {
                                likedEpisodePrefs.getStringSet("liked_ids", emptySet())?.toSet() ?: emptySet()
                            }
                            val savedChannelId = remember(selectedPlaylistName, refreshTriggerLocal) { idPrefs.getString(selectedPlaylistName, null) }

                            MetroPodcastShowDetailScreen(
                                title = selectedPlaylistName,
                                author = showAuthor,
                                thumbnailUrl = heroThumb,
                                episodes = podcastSongs,
                                isSaved = isShowSaved,
                                onToggleSave = {
                                    val autoTitles = setOf("episodes for later", "new episodes", "continue listening", "books for later")
                                    if (selectedPlaylistName.trim().lowercase() !in autoTitles) {
                                        val currentNames = plPrefs.getStringSet("names", emptySet())?.toMutableSet() ?: mutableSetOf()
                                        if (currentNames.contains(selectedPlaylistName)) {
                                            currentNames.remove(selectedPlaylistName)
                                        } else {
                                            currentNames.add(selectedPlaylistName)
                                        }
                                        plPrefs.edit().putStringSet("names", currentNames).commit()
                                        refreshTriggerLocal++
                                    }
                                },
                                onPlayEpisode = { ep, list ->
                                    val eps = list.map { s ->
                                        com.musicplayer.lytrix.PodcastEpisode(
                                            id = s.id,
                                            title_original = s.name,
                                            audio = s.downloadUrl.ifBlank { "yt:${s.id}" },
                                            image = s.imageUrl,
                                            audio_length_sec = 0,
                                            podcast = com.musicplayer.lytrix.PodcastInfo(s.artist, savedChannelId),
                                            channelId = savedChannelId,
                                        )
                                    }
                                    val targetEp = eps.find { it.id == ep.id } ?: eps.first()
                                    playUnifiedMediaEpisode(context, viewModel, targetEp, eps, mediaCategory = targetCategory)
                                },
                                onToggleLikeEpisode = { ep ->
                                    val set = likedEpisodePrefs.getStringSet("liked_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
                                    if (set.contains(ep.id)) {
                                        set.remove(ep.id)
                                        likedEpisodePrefs.edit().remove("song_data_${ep.id}").putStringSet("liked_ids", set).commit()
                                        Toast.makeText(context, "Removed from Liked", Toast.LENGTH_SHORT).show()
                                    } else {
                                        set.add(ep.id)
                                        val jsonObj = JSONObject().apply {
                                            put("id", ep.id); put("title", ep.name); put("name", ep.name)
                                            put("subtitle", ep.artist); put("artist", ep.artist); put("image", ep.imageUrl)
                                            put("imageUrl", ep.imageUrl); put("url", ep.downloadUrl); put("downloadUrl", ep.downloadUrl)
                                            savedChannelId?.let { put("channelId", it) }
                                        }
                                        likedEpisodePrefs.edit().putString("song_data_${ep.id}", jsonObj.toString()).putStringSet("liked_ids", set).commit()
                                        Toast.makeText(context, "Saved to Liked", Toast.LENGTH_SHORT).show()
                                    }
                                    refreshTriggerLocal++
                                },
                                likedEpisodeIds = likedEpisodeIds,
                                onSaveForLater = { ep ->
                                    if (laterPrefs.contains(ep.id)) {
                                        laterPrefs.edit().remove(ep.id).commit()
                                        Toast.makeText(context, "Removed from $laterName", Toast.LENGTH_SHORT).show()
                                    } else {
                                        val json = JSONObject().apply {
                                            put("id", ep.id)
                                            put("name", ep.name)
                                            put("artist", ep.artist)
                                            put("imageUrl", ep.imageUrl)
                                            put("downloadUrl", ep.downloadUrl)
                                            put("albumName", ep.albumName ?: selectedPlaylistName)
                                            savedChannelId?.let { put("channelId", it) }
                                        }
                                        laterPrefs.edit().putString(ep.id, json.toString()).commit()
                                        Toast.makeText(context, "Saved to $laterName", Toast.LENGTH_SHORT).show()
                                    }
                                    refreshTriggerLocal++
                                },
                                savedForLaterIds = savedForLaterIds,
                                onMoreClick = { s ->
                                    val isAb = dbType == "Audiobook"
                                    val isPod = dbType == "Podcast" || dbType == "ShowsPlaylist"
                                    selectedBottomSong = s.copy(
                                        albumName = if (isAb) "Audiobook" else if (isPod) "Podcast" else s.albumName
                                    )
                                    showBottomSheet = true
                                },
                                onChannelClick = { onNavigateToArtist(it) },
                                channelId = savedChannelId,
                                onBackClick = { currentLibraryView = "MAIN" }
                            )
                        } else {

                        // 🔥 প্লেলিস্ট থেকে গান রিমুভ করার ডায়ালগ
                        if (songToRemoveFromPlaylist != null) {
                            AlertDialog(
                                onDismissRequest = { songToRemoveFromPlaylist = null },
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                title = { Text("Remove from Playlist?", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
                                text = { Text("Are you sure you want to remove '${songToRemoveFromPlaylist!!.name}' from $selectedPlaylistName?", color = Color.LightGray) },
                                confirmButton = {
                                    TextButton(onClick = {
                                        val songPrefs = context.getSharedPreferences("$prefix$selectedPlaylistName", Context.MODE_PRIVATE)
                                        val songJson = songPrefs.getString(songToRemoveFromPlaylist!!.id, null)
                                        songPrefs.edit().remove(songToRemoveFromPlaylist!!.id).apply()
                                        refreshTriggerLocal++ // প্লেলিস্ট রিফ্রেশ করবে
                                        android.widget.Toast.makeText(context, "Song removed", android.widget.Toast.LENGTH_SHORT).show()
                                        songToRemoveFromPlaylist = null
                                    }) { Text("Remove", color = Color.Red, fontWeight = FontWeight.Bold) }
                                },
                                dismissButton = { TextButton(onClick = { songToRemoveFromPlaylist = null }) { Text("Cancel", color = Color.White) } }
                            )
                        }

                        // 🔥 Playlist Search States
                        var isPlaylistSearchActive by remember { mutableStateOf(false) }
                        var playlistSearchQuery by remember { mutableStateOf("") }
                        BackHandler(enabled = isPlaylistSearchActive) {
                            isPlaylistSearchActive = false
                            playlistSearchQuery = ""
                        }
                        val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                            uri?.let {
                                try {
                                    val inputStream = context.contentResolver.openInputStream(it)
                                    val file = File(context.filesDir, "cover_${selectedPlaylistName.replace(" ", "_")}.jpg")
                                    val outputStream = java.io.FileOutputStream(file)
                                    inputStream?.copyTo(outputStream)
                                    inputStream?.close()
                                    outputStream.close()
                                    val localPath = file.absolutePath
                                    customCoverUri = localPath
                                    coverPrefs.edit().putString(selectedPlaylistName, localPath).apply()
                                    refreshTriggerLocal++
                                } catch (e: Exception) { e.printStackTrace() }
                            }
                        }
                        val plSongs = remember(selectedPlaylistName, refreshTriggerLocal) {
                            plSongsPrefs.all.entries.sortedBy { it.key }.mapNotNull { (_, json) ->
                                try {
                                    val obj = JSONObject(json.toString())
                                    JioSaavnSong(id = obj.optString("id", ""), name = obj.optString("name", "Unknown"), artist = obj.optString("artist", "Unknown Artist"), imageUrl = obj.optString("imageUrl", ""), downloadUrl = obj.optString("downloadUrl", ""))
                                } catch (e: Exception) { null }
                            }
                        }
                        val detailSource = remember(selectedPlaylistName, dbType, refreshTriggerLocal) {
                            detectCollectionSource(selectedPlaylistName, plSongsPrefs, isAlbum = dbType.equals("Album", ignoreCase = true))
                        }
                        val isEditableCollection = remember(detailSource, dbType) {
                            detailSource.equals("Custom", ignoreCase = true) &&
                                !dbType.equals("Album", ignoreCase = true) &&
                                !dbType.equals("Podcast", ignoreCase = true) &&
                                !dbType.equals("Audiobook", ignoreCase = true)
                        }

                        // ========================================================

                        // 🔥 ম্যাজিক লজিক: ArchiveToon Auto-Sync

                        // ========================================================
                        val ytDetails = viewModel.ytPlaylistDetails.collectAsState().value

                        // ১. যদি ডাটাবেস ফাঁকা থাকে, তাহলে আইডি ধরে ইন্টারনেট থেকে লাইভ ফেচ করবে
                        LaunchedEffect(selectedPlaylistName) {
                            val savedId = idPrefs.getString(selectedPlaylistName, null)
                            val isSpotify = detailSource.equals("Spotify", ignoreCase = true)
                            val isSaavn = detailSource.equals("JioSaavn", ignoreCase = true) || savedId?.startsWith("saavn_", ignoreCase = true) == true
                            val isSessionSynced = com.musicplayer.lytrix.spotify.SpotifyRepository.isPlaylistSyncedInSession(selectedPlaylistName)

                            if (plSongs.isEmpty() || (isSpotify && !isSessionSynced)) {
                                if (isSpotify) {
                                    if (selectedPlaylistName.equals("Liked Songs", ignoreCase = true) || savedId == "collection") {
                                        val likedSongs = com.musicplayer.lytrix.spotify.SpotifyRepository.getCachedLikedSongs(context)
                                        if (likedSongs.isNotEmpty()) {
                                            val editor = plSongsPrefs.edit()
                                            likedSongs.forEach { song ->
                                                val obj = org.json.JSONObject().apply {
                                                    put("id", song.id)
                                                    put("name", song.name)
                                                    put("artist", song.artist)
                                                    put("imageUrl", song.imageUrl)
                                                    put("downloadUrl", "")
                                                }
                                                editor.putString(song.id, obj.toString())
                                            }
                                            editor.apply()
                                            plPrefs.edit().putInt("count_Liked Songs", likedSongs.size).apply()
                                            com.musicplayer.lytrix.spotify.SpotifyRepository.markPlaylistSyncedInSession("Liked Songs")
                                            refreshTriggerLocal++
                                        }
                                    } else if (savedId != null) {
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            com.musicplayer.lytrix.spotify.SpotifyRepository.fetchPlaylistTracks(context, savedId, selectedPlaylistName)
                                        }
                                        refreshTriggerLocal++
                                    }
                                } else if (isSaavn && savedId != null) {
                                    val isAlb = savedId.startsWith("saavn_album_", ignoreCase = true) || dbType.equals("Album", ignoreCase = true)
                                    val rawSaavnId = savedId.removePrefix("saavn_album_").removePrefix("saavn_playlist_").removePrefix("saavn_")
                                    val fetched = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        com.musicplayer.lytrix.saavn.JioSaavnLiveRepository.fetchCollectionDetails(
                                            id = rawSaavnId,
                                            type = if (isAlb) com.musicplayer.lytrix.saavn.SaavnItemType.ALBUM else com.musicplayer.lytrix.saavn.SaavnItemType.PLAYLIST,
                                            titleHint = selectedPlaylistName,
                                            imageHint = customCoverUri.orEmpty(),
                                        )
                                    }
                                    if (fetched != null && fetched.songs.isNotEmpty()) {
                                        val editor = plSongsPrefs.edit().clear()
                                        fetched.songs.forEach { s ->
                                            val obj = JSONObject()
                                            obj.put("id", s.id); obj.put("title", s.name); obj.put("name", s.name)
                                            obj.put("artist", s.artist); obj.put("imageUrl", s.imageUrl); obj.put("downloadUrl", s.downloadUrl)
                                            editor.putString(s.id, obj.toString())
                                        }
                                        editor.apply()
                                        plPrefs.edit().putInt("count_$selectedPlaylistName", fetched.songs.size).apply()
                                        refreshTriggerLocal++
                                    }
                                } else if (savedId != null) {
                                    viewModel.loadYtPlaylistDetails(savedId, dbType, selectedPlaylistName)
                                }
                            }
                        }

                        // ২. ফেচ হয়ে গেলেই সাথে সাথে ডাটাবেসে সব গান আর অরিজিনাল কভার ছবি সেভ করে নেবে!
                        LaunchedEffect(ytDetails) {
                            if (plSongs.isEmpty() && ytDetails != null && ytDetails.isNotEmpty() && viewModel.ytPlaylistName.value == selectedPlaylistName) {
                                val editor = plSongsPrefs.edit()
                                ytDetails.forEach { track ->
                                    val obj = org.json.JSONObject()
                                    obj.put("id", track.id)
                                    obj.put("originalId", track.id)
                                    obj.put("title", track.title)
                                    obj.put("name", track.title)
                                    obj.put("artist", track.artist)
                                    obj.put("imageUrl", track.thumbnailUrl ?: customCoverUri ?: "")
                                    obj.put("downloadUrl", "")
                                    editor.putString(track.id, obj.toString())
                                }
                                editor.apply()
                                plPrefs.edit().putInt("count_$selectedPlaylistName", ytDetails.size).apply()
                                refreshTriggerLocal++ // UI রিলোড হবে
                                Toast.makeText(context, "$dbType Synced for Offline!", Toast.LENGTH_SHORT).show()
                            }
                        }

                        // ========================================================
                        var downloadedCount by remember { mutableStateOf(0) }
                        LaunchedEffect(plSongs) {
                            downloadedCount = plSongs.count { File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "${it.name}.mp3").exists() }
                        }
                        val isFullyDownloaded = plSongs.isNotEmpty() && downloadedCount == plSongs.size
                        val isSpotifyLibraryPlaylist = detailSource.equals("Spotify", ignoreCase = true)
                        val savedSourceId = remember(selectedPlaylistName, refreshTriggerLocal) {
                            idPrefs.getString(selectedPlaylistName, null)
                        }
                        val displayCover = customCoverUri ?: plSongs.firstOrNull()?.imageUrl ?: "https://images.unsplash.com/photo-1470225620780-dba8ba36b745"

                        Box(modifier = Modifier.fillMaxSize()) {
                            MetroLibraryPlaylistScreen(
                                viewModel = viewModel,
                                playlistName = selectedPlaylistName,
                                dbType = dbType,
                                artworkUrl = displayCover,
                                songs = plSongs,
                                isFullyDownloaded = isFullyDownloaded,
                                isSpotifyPlaylist = isSpotifyLibraryPlaylist,
                                source = detailSource,
                                isEditable = isEditableCollection,
                                onBack = {
                                    viewModel.ytPlaylistType.value = "Playlist"
                                    currentLibraryView = "MAIN"
                                },
                                onPlayTrack = { clickedSong, orderedSongs, index ->
                                    if (dbType == "Podcast" || dbType == "Audiobook") {
                                        val eps = orderedSongs.map { s ->
                                            com.musicplayer.lytrix.PodcastEpisode(
                                                id = s.id,
                                                title_original = s.name,
                                                audio = s.downloadUrl.ifBlank { "yt:${s.id}" },
                                                image = s.imageUrl,
                                                audio_length_sec = 0,
                                                podcast = com.musicplayer.lytrix.PodcastInfo(s.artist),
                                            )
                                        }
                                        val targetEp = eps.getOrElse(index) { eps.first() }
                                        playUnifiedMediaEpisode(context, viewModel, targetEp, eps)
                                    } else if (clickedSong.downloadUrl.isEmpty()) {
                                        val ytList = orderedSongs.map { PlayerViewModel.YtMusicTrack(it.id, it.name, it.artist, it.imageUrl) }
                                        viewModel.playLytrixPlaylistWithAd(ytList, index, context)
                                    } else {
                                        viewModel.playOnlineSongWithAd(context, clickedSong, orderedSongs)
                                    }
                                },
                                onShufflePlay = { orderedSongs ->
                                    val shuffled = orderedSongs.shuffled()
                                    val first = shuffled.first()
                                    if (dbType == "Podcast" || dbType == "Audiobook") {
                                        val eps = shuffled.map { s ->
                                            com.musicplayer.lytrix.PodcastEpisode(
                                                id = s.id,
                                                title_original = s.name,
                                                audio = s.downloadUrl.ifBlank { "yt:${s.id}" },
                                                image = s.imageUrl,
                                                audio_length_sec = 0,
                                                podcast = com.musicplayer.lytrix.PodcastInfo(s.artist),
                                            )
                                        }
                                        playUnifiedMediaEpisode(context, viewModel, eps.first(), eps)
                                    } else if (first.downloadUrl.isEmpty()) {
                                        val ytList = shuffled.map { PlayerViewModel.YtMusicTrack(it.id, it.name, it.artist, it.imageUrl) }
                                        viewModel.playLytrixPlaylistWithAd(ytList, 0, context)
                                    } else {
                                        viewModel.playOnlineSongWithAd(context, first, shuffled)
                                    }
                                },
                                onTrackMenuClick = { song ->
                                    if (dbType == "Podcast" || dbType == "Audiobook") {
                                        val kind = if (dbType == "Audiobook") com.musicplayer.lytrix.media.MediaHubKind.AUDIOBOOK else com.musicplayer.lytrix.media.MediaHubKind.PODCAST
                                        val isYt = song.downloadUrl.isBlank() || song.downloadUrl.startsWith("yt:")
                                        selectedMediaHubSheetCard = com.musicplayer.lytrix.media.MediaHubCard(
                                            id = song.id,
                                            title = song.name,
                                            subtitle = song.artist,
                                            imageUrl = song.imageUrl,
                                            kind = kind,
                                            source = if (isYt) com.musicplayer.lytrix.media.MediaHubSource.YOUTUBE else com.musicplayer.lytrix.media.MediaHubSource.APPLE,
                                            isCollection = false,
                                            audioUrl = song.downloadUrl.ifBlank { "yt:${song.id}" },
                                        )
                                        selectedMediaHubSheetQueue = plSongs.map { s ->
                                            val sYt = s.downloadUrl.isBlank() || s.downloadUrl.startsWith("yt:")
                                            com.musicplayer.lytrix.media.MediaHubCard(
                                                id = s.id,
                                                title = s.name,
                                                subtitle = s.artist,
                                                imageUrl = s.imageUrl,
                                                kind = kind,
                                                source = if (sYt) com.musicplayer.lytrix.media.MediaHubSource.YOUTUBE else com.musicplayer.lytrix.media.MediaHubSource.APPLE,
                                                isCollection = false,
                                                audioUrl = s.downloadUrl.ifBlank { "yt:${s.id}" },
                                            )
                                        }
                                    } else {
                                        selectedBottomSong = song
                                        showBottomSheet = true
                                    }
                                },
                                onTrackLongClick = { song ->
                                    if (isEditableCollection) songToRemoveFromPlaylist = song
                                },
                                onDownloadAll = {
                                    if (dbType == "Podcast" || dbType == "Audiobook") {
                                        val mp3Items = plSongs.filter { it.downloadUrl.isNotBlank() && !it.downloadUrl.startsWith("yt:") }
                                        if (mp3Items.isEmpty()) {
                                            Toast.makeText(context, "Stream-only YouTube episodes cannot be bulk downloaded", Toast.LENGTH_SHORT).show()
                                        } else {
                                            mp3Items.forEach { s ->
                                                val ep = com.musicplayer.lytrix.PodcastEpisode(
                                                    id = s.id,
                                                    title_original = s.name,
                                                    audio = s.downloadUrl,
                                                    image = s.imageUrl,
                                                    audio_length_sec = 0,
                                                    podcast = com.musicplayer.lytrix.PodcastInfo(s.artist),
                                                )
                                                if (dbType == "Audiobook") downloadAudiobookSecurely(context, ep) else downloadPodcastSecurely(context, ep)
                                            }
                                        }
                                    } else if (!isFullyDownloaded) {
                                        plSongs.forEach { song ->
                                            if (!File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "${song.name}.mp3").exists()) {
                                                downloadSongSecurely(context, song)
                                            }
                                        }
                                        Toast.makeText(context, "Downloading tracks...", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onRenameClick = {
                                    if (isEditableCollection) {
                                        playlistToRename = selectedPlaylistName
                                        outerRenameInput = selectedPlaylistName
                                        showOuterRenameDialog = true
                                    }
                                },
                                onChangeCoverClick = {
                                    if (isEditableCollection) {
                                        showCoverMenu = true
                                    }
                                },
                                onSyncYouTubeClick = if (detailSource.equals("YouTube", ignoreCase = true) && !savedSourceId.isNullOrBlank()) {
                                    {
                                        Toast.makeText(context, "Syncing from YouTube...", Toast.LENGTH_SHORT).show()
                                        viewModel.saveYtPlaylistSilently(
                                            id = savedSourceId,
                                            type = dbType,
                                            title = selectedPlaylistName,
                                            thumbnailUrl = displayCover,
                                            context = context,
                                        )
                                        scope.launch {
                                            kotlinx.coroutines.delay(1800)
                                            refreshTriggerLocal++
                                        }
                                    }
                                } else null,
                                onSyncSpotifyClick = if (isSpotifyLibraryPlaylist && !savedSourceId.isNullOrBlank()) {
                                    {
                                        Toast.makeText(context, "Syncing from Spotify...", Toast.LENGTH_SHORT).show()
                                        scope.launch(Dispatchers.IO) {
                                            com.musicplayer.lytrix.spotify.SpotifyRepository.fetchPlaylistTracks(context, savedSourceId, selectedPlaylistName)
                                            refreshTriggerLocal++
                                        }
                                    }
                                } else null,
                                onSyncSaavnClick = if (detailSource.equals("JioSaavn", ignoreCase = true) && !savedSourceId.isNullOrBlank()) {
                                    {
                                        val isAlb = savedSourceId.startsWith("saavn_album_", ignoreCase = true) || dbType.equals("Album", ignoreCase = true)
                                        val rawId = savedSourceId.removePrefix("saavn_album_").removePrefix("saavn_playlist_").removePrefix("saavn_")
                                        Toast.makeText(context, "Syncing from JioSaavn...", Toast.LENGTH_SHORT).show()
                                        scope.launch(Dispatchers.IO) {
                                            val fetched = com.musicplayer.lytrix.saavn.JioSaavnLiveRepository.fetchCollectionDetails(
                                                id = rawId,
                                                type = if (isAlb) com.musicplayer.lytrix.saavn.SaavnItemType.ALBUM else com.musicplayer.lytrix.saavn.SaavnItemType.PLAYLIST,
                                                titleHint = selectedPlaylistName,
                                                imageHint = displayCover,
                                            )
                                            withContext(Dispatchers.Main) {
                                                if (fetched != null && fetched.songs.isNotEmpty()) {
                                                    val editor = plSongsPrefs.edit().clear()
                                                    fetched.songs.forEach { s ->
                                                        val obj = JSONObject()
                                                        obj.put("id", s.id); obj.put("title", s.name); obj.put("name", s.name)
                                                        obj.put("artist", s.artist); obj.put("imageUrl", s.imageUrl); obj.put("downloadUrl", s.downloadUrl)
                                                        editor.putString(s.id, obj.toString())
                                                    }
                                                    editor.apply()
                                                    plPrefs.edit().putInt("count_$selectedPlaylistName", fetched.songs.size).apply()
                                                    refreshTriggerLocal++
                                                    Toast.makeText(context, "Synced ${fetched.songs.size} tracks from JioSaavn", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "Could not sync from JioSaavn", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    }
                                } else null,
                                onDeleteClick = {
                                    playlistToDelete = selectedPlaylistName
                                    showOuterDeleteWarning = true
                                },
                                bottomPadding = bottomListPadding,
                            )

                            if (showCoverMenu && isEditableCollection) {
                                var coverUrlInput by remember(selectedPlaylistName, showCoverMenu) {
                                    mutableStateOf(
                                        customCoverUri?.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }.orEmpty()
                                    )
                                }
                                AlertDialog(
                                    onDismissRequest = { showCoverMenu = false },
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    title = { Text("Change Cover", fontWeight = FontWeight.Bold) },
                                    text = {
                                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            androidx.compose.material3.FilledTonalButton(
                                                onClick = {
                                                    showCoverMenu = false
                                                    imagePickerLauncher.launch("image/*")
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(14.dp),
                                            ) {
                                                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.width(8.dp))
                                                Text("Choose from Gallery")
                                            }
                                            OutlinedTextField(
                                                value = coverUrlInput,
                                                onValueChange = { coverUrlInput = it },
                                                label = { Text("Or paste Image URL") },
                                                placeholder = { Text("https://...") },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                            if (customCoverUri != null) {
                                                TextButton(
                                                    onClick = {
                                                        coverPrefs.edit().remove(selectedPlaylistName).apply()
                                                        val f = File(context.filesDir, "cover_${selectedPlaylistName.replace(" ", "_")}.jpg")
                                                        if (f.exists()) f.delete()
                                                        customCoverUri = null
                                                        refreshTriggerLocal++
                                                        showCoverMenu = false
                                                        Toast.makeText(context, "Cover reset to default", Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier.align(Alignment.End),
                                                ) {
                                                    Text("Reset to Default", color = MaterialTheme.colorScheme.error)
                                                }
                                            }
                                        }
                                    },
                                    confirmButton = {
                                        TextButton(
                                            enabled = coverUrlInput.trim().startsWith("http://", true) || coverUrlInput.trim().startsWith("https://", true),
                                            onClick = {
                                                val cleanUrl = coverUrlInput.trim()
                                                coverPrefs.edit().putString(selectedPlaylistName, cleanUrl).apply()
                                                customCoverUri = cleanUrl
                                                refreshTriggerLocal++
                                                showCoverMenu = false
                                                Toast.makeText(context, "Cover updated", Toast.LENGTH_SHORT).show()
                                            },
                                        ) { Text("Apply URL", fontWeight = FontWeight.Bold) }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showCoverMenu = false }) { Text("Cancel") }
                                    },
                                )
                            }

                            // 🔥 Dialogs moved outside LazyColumn to prevent hidden scroll bug
                            if (showOuterDeleteWarning) {
                                AlertDialog(
                                    onDismissRequest = { showOuterDeleteWarning = false },
                                    containerColor = Color(0xFF1C1C1C),
                                    title = { Text("Delete $dbType?", color = Color.White, fontWeight = FontWeight.Bold) },
                                    text = { Text("Are you sure you want to delete this $dbType? This action cannot be undone.", color = Color.LightGray) },
                                    confirmButton = {
                                        TextButton(onClick = {

                                            // 🔥 অ্যালবাম নাকি প্লেলিস্ট সেটা চেক করে ডিলিট করবে
                                            val dbNameDel = if (dbType == "Album") "online_albums_meta" else if (dbType == "Podcast") "online_podcasts_meta" else if (dbType == "Audiobook") "online_audiobooks_meta" else if (dbType == "ShowsPlaylist") "online_shows_playlists_meta" else "online_playlists_meta"
                                            val prefixDel = if (dbType == "Album") "online_album_" else if (dbType == "Podcast") "online_podcast_" else if (dbType == "Audiobook") "online_ab_" else if (dbType == "ShowsPlaylist") "online_show_pl_" else "online_pl_"
                                            val targetPrefs = context.getSharedPreferences(dbNameDel, Context.MODE_PRIVATE)
                                            val set = targetPrefs.getStringSet("names", emptySet())?.toMutableSet() ?: mutableSetOf()
                                            set.remove(playlistToDelete)
                                            targetPrefs.edit().putStringSet("names", set).remove("count_$playlistToDelete").apply()

                                            // ভেতরের গান এবং কভার ছবি ডিলিট করা
                                            context.getSharedPreferences("$prefixDel$playlistToDelete", Context.MODE_PRIVATE).edit().clear().apply()
                                            coverPrefs.edit().remove(playlistToDelete).apply()
                                            idPrefs.edit().remove(playlistToDelete).apply()
                                            sourcePrefs.edit().remove(playlistToDelete).apply()
                                            val file = File(context.filesDir, "cover_${playlistToDelete.replace(" ", "_")}.jpg")
                                            if(file.exists()) file.delete()
                                            refreshTriggerLocal++
                                            showOuterDeleteWarning = false
                                            currentLibraryView = "MAIN"
                                        }) { Text("Delete", color = Color.Red, fontWeight = FontWeight.Bold) }
                                    },
                                    dismissButton = { TextButton(onClick = { showOuterDeleteWarning = false }) { Text("Cancel", color = Color.White) } }
                                )
                            }
                            if (showOuterRenameDialog) {
                                AlertDialog(
                                    onDismissRequest = { showOuterRenameDialog = false }, containerColor = cardBgColor,
                                    title = { Text("Rename $dbType", color = Color.White) },
                                    text = { OutlinedTextField(value = outerRenameInput, onValueChange = { outerRenameInput = it }, singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)) },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            val trimmedNew = outerRenameInput.trim()
                                            if (trimmedNew.isNotBlank() && trimmedNew != playlistToRename) {
                                                val set = plPrefs.getStringSet("names", emptySet())?.toMutableSet() ?: mutableSetOf()
                                                set.remove(playlistToRename)
                                                set.add(trimmedNew)
                                                val oldCount = plPrefs.getInt("count_$playlistToRename", -1)
                                                plPrefs.edit().putStringSet("names", set).remove("count_$playlistToRename").apply {
                                                    if (oldCount >= 0) putInt("count_$trimmedNew", oldCount)
                                                }.apply()

                                                val oldSongsPrefs = context.getSharedPreferences("$prefix$playlistToRename", Context.MODE_PRIVATE)
                                                val newSongsPrefs = context.getSharedPreferences("$prefix$trimmedNew", Context.MODE_PRIVATE)
                                                val editor = newSongsPrefs.edit()
                                                oldSongsPrefs.all.forEach { (key, value) -> editor.putString(key, value.toString()) }
                                                editor.apply()
                                                oldSongsPrefs.edit().clear().apply()

                                                val oldFile = File(context.filesDir, "cover_${playlistToRename.replace(" ", "_")}.jpg")
                                                val newFile = File(context.filesDir, "cover_${trimmedNew.replace(" ", "_")}.jpg")
                                                val coverUri = coverPrefs.getString(playlistToRename, null)
                                                val migratedCover = if (oldFile.exists() && coverUri == oldFile.absolutePath) {
                                                    oldFile.renameTo(newFile)
                                                    newFile.absolutePath
                                                } else {
                                                    if (oldFile.exists()) oldFile.renameTo(newFile)
                                                    coverUri
                                                }
                                                if (migratedCover != null) {
                                                    coverPrefs.edit().remove(playlistToRename).putString(trimmedNew, migratedCover).apply()
                                                }

                                                val timePrefsLocal = context.getSharedPreferences("playlist_time", Context.MODE_PRIVATE)
                                                val oldTime = timePrefsLocal.getLong(playlistToRename, System.currentTimeMillis())
                                                timePrefsLocal.edit().remove(playlistToRename).putLong(trimmedNew, oldTime).apply()

                                                val oldSource = sourcePrefs.getString(playlistToRename, "Custom") ?: "Custom"
                                                sourcePrefs.edit().remove(playlistToRename).putString(trimmedNew, oldSource).apply()

                                                val oldSavedId = idPrefs.getString(playlistToRename, null)
                                                if (oldSavedId != null) {
                                                    idPrefs.edit().remove(playlistToRename).putString(trimmedNew, oldSavedId).apply()
                                                }

                                                val pinnedSet = pinnedPrefs.getStringSet("names", emptySet())?.toMutableSet() ?: mutableSetOf()
                                                if (pinnedSet.remove(playlistToRename)) {
                                                    pinnedSet.add(trimmedNew)
                                                    pinnedPrefs.edit().putStringSet("names", pinnedSet).apply()
                                                }

                                                selectedPlaylistName = trimmedNew
                                                customCoverUri = migratedCover
                                                refreshTriggerLocal++
                                                showOuterRenameDialog = false
                                            }
                                        }) { Text("Save", color = accentColor) }
                                    },
                                    dismissButton = { TextButton(onClick = { showOuterRenameDialog = false }) { Text("Cancel", color = Color.White) } }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

            // 🔥 নতুন সার্চ স্ক্রিনের ওভারলে
            AnimatedVisibility(
                visible = isSearchScreenOpen,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.zIndex(30f)
            ) {
                SearchScreen(
                    viewModel = viewModel,
                    onNavigateBack = { isSearchScreenOpen = false },
                    onNavigateToArtist = onNavigateToArtist,
                    onSongMoreClick = { song ->
                        selectedBottomSong = song
                        showBottomSheet = true // 🔥 গানের বটম-শিট ওপেন হবে
                    },
                    onPlaylistMoreClick = { playlistTrack ->
                        selectedYtPlaylist = playlistTrack

                        // 🔥 ম্যাজিক ফিক্স: টাইপ সেট করা হচ্ছে যাতে এলবাম এলবামে আর প্লেলিস্ট প্লেলিস্টে সেভ হয়!
                        val isAlbum = playlistTrack.itemType.equals("Album", ignoreCase = true) || playlistTrack.artist.contains("Album", ignoreCase = true)
                        val isPodcast = playlistTrack.itemType.equals("Podcast", ignoreCase = true) || playlistTrack.artist.contains("Podcast", ignoreCase = true) || playlistTrack.id.startsWith("MPSP") || searchFilterContext == "Podcasts"
                        viewModel.ytPlaylistType.value = if (isAlbum) "Album" else if (isPodcast) "Podcast" else "Playlist"
                        showYtPlaylistBottomSheet = true
                    }
                )
            }

            // ==========================================

            // 🔥 Choose Artist Premium Dialog (ArchiveToon Style)

            // ==========================================
            if (artistsListForDialog != null) {
                AlertDialog(
                    onDismissRequest = { artistsListForDialog = null },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(24.dp),
                    title = {
                        Text(
                            text = "Choose Artist",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    },
                    text = {
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(artistsListForDialog ?: emptyList()) { artistName ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable {
                                            artistsListForDialog = null
                                            onNavigateToArtist(artistName) // 🔥 ক্লিক করলেই আর্টিস্ট পেজ ওপেন হবে
                                        }
                                        .padding(vertical = 12.dp, horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        text = artistName,
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { artistsListForDialog = null }) {
                            Text("Cancel", color = Color.Gray, fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }

// ==========================================

            // 🔥 MELD STYLE FULL-SCREEN RECOGNITION UI (DIALOG FIX)

            // ==========================================
            if (isShazamActive) {
                androidx.compose.ui.window.Dialog(
                    onDismissRequest = { viewModel.cancelShazamRecognition() },
                    properties = androidx.compose.ui.window.DialogProperties(
                        usePlatformDefaultWidth = false, // পুরো স্ক্রিন নেবে
                        decorFitsSystemWindows = false // স্ট্যাটাস বার ও মিনি-প্লেয়ার কভার করবে
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF030303).copy(alpha = 0.98f))
                    ) {

                        // 🔥 মাঝখানের কন্টেন্ট
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)
                        ) {
                            when (val state = shazamState) {
                                is com.musicplayer.lytrix.shazam.RecognitionStatus.Listening -> {
                                    val infiniteTransition = rememberInfiniteTransition()
                                    val scale by infiniteTransition.animateFloat(
                                        initialValue = 1f, targetValue = 1.3f,
                                        animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse)
                                    )
                                    Box(modifier = Modifier.size(120.dp).graphicsLayer { scaleX = scale; scaleY = scale }.clip(CircleShape).background(Color(0xFFE57373).copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                                        Box(modifier = Modifier.size(80.dp).clip(CircleShape).background(Color(0xFFE57373)), contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Mic, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Text("Listening...", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                }
                                is com.musicplayer.lytrix.shazam.RecognitionStatus.Processing -> {
                                    val infiniteTransition = rememberInfiniteTransition()
                                    val rotation by infiniteTransition.animateFloat(initialValue = 0f, targetValue = 360f, animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing)))
                                    Box(modifier = Modifier.size(80.dp).clip(CircleShape).background(accentColor.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = accentColor, modifier = Modifier.size(40.dp).graphicsLayer { rotationZ = rotation })
                                    }
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Text("Processing...", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                }
                                is com.musicplayer.lytrix.shazam.RecognitionStatus.NoMatch -> {
                                    Box(modifier = Modifier.size(80.dp).clip(CircleShape).background(Color.Gray.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Close, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(40.dp))
                                    }
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Text(state.message, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                }
                                is com.musicplayer.lytrix.shazam.RecognitionStatus.Success -> {
                                    val result = state.result
                                    AsyncImage(
                                        model = result.coverArtHqUrl ?: result.coverArtUrl,
                                        contentDescription = null, contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(240.dp).clip(RoundedCornerShape(16.dp)).shadow(12.dp)
                                    )
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Text(text = result.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(text = result.artist, color = Color.Gray, fontSize = 16.sp, textAlign = TextAlign.Center)
                                    Spacer(modifier = Modifier.height(40.dp))

                                    // Play on Lytrix
                                    Button(
                                        onClick = {
                                            viewModel.cancelShazamRecognition()
                                            val query = "${result.title} ${result.artist}"
                                            searchQuery = query
                                            viewModel.setSearchQuery(query)
                                            currentBottomTab = "SEARCH"
                                            currentSearchTab = "SEARCH_RESULTS"
                                            isSearchScreenOpen = true
                                            viewModel.searchInnerTube(query, "All")
                                        },
                                        modifier = Modifier.fillMaxWidth(0.85f).height(56.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                                        shape = RoundedCornerShape(28.dp)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Play on Lytrix", color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                is com.musicplayer.lytrix.shazam.RecognitionStatus.Error -> {
                                    Text(state.message, color = Color.Red, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                }
                                else -> {}
                            }
                        }

                        // 🔥 নিচের বটম বার (Cancel / Re-listen / Close)
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(bottom = 48.dp, start = 24.dp, end = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            when (shazamState) {
                                is com.musicplayer.lytrix.shazam.RecognitionStatus.Listening,
                                is com.musicplayer.lytrix.shazam.RecognitionStatus.Processing -> {
                                    OutlinedButton(
                                        onClick = { viewModel.cancelShazamRecognition() },
                                        modifier = Modifier.height(50.dp).width(140.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                                    ) {
                                        Text("Cancel", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                else -> { // Success, NoMatch, Error
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                            OutlinedButton(
                                                onClick = { viewModel.startShazamRecognition(context) },
                                                modifier = Modifier.height(50.dp).weight(1f),
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                                            ) {
                                                Text("Re-listen", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                            }
                                            OutlinedButton(
                                                onClick = { viewModel.cancelShazamRecognition() },
                                                modifier = Modifier.height(50.dp).weight(1f),
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                                            ) {
                                                Text("Close", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        TextButton(
                                            onClick = { com.musicplayer.lytrix.utils.QuickSettingsTileHelper.requestRecognizeSongTile(context) }
                                        ) {
                                            Text(
                                                text = "Pin \"Recognize Song\" to Quick Settings",
                                                fontSize = 12.sp,
                                                color = Color(0xFF4FD1C5),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

// 🔥 প্রিমিয়াম ভাসমান বটম বার (ল্যান্ডস্কেপে হাইড)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(20f),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = !hideBars && !isLandscape,
            enter = slideInVertically(initialOffsetY = { it }) + expandVertically(),
            exit = slideOutVertically(targetOffsetY = { it }) + shrinkVertically(),
            modifier = Modifier.padding(bottom = floatingBarBottomPadding) // 🔥 মিনিপ্লেয়ারের ঠিক উপরে পারফেক্ট গ্যাপ (৭৬ ডিপি বা ১৬ ডিপি)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.88f) // একটু স্লিম এবং স্মার্ট লুক
                    .height(64.dp)

                    // 🔥 ম্যাজিক ৩: নিচে একটা সুন্দর প্রিমিয়াম শ্যাডো (Shadow)
                    .shadow(elevation = 16.dp, shape = RoundedCornerShape(32.dp), spotColor = Color.Black)
                    .clip(RoundedCornerShape(32.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(32.dp))
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val navHaptic = androidx.compose.ui.platform.LocalHapticFeedback.current
                val tabs = listOf(
                    Triple("HOME", Pair(com.musicplayer.lytrix.ui.icons.LytrixIcons.Outlined.Home, com.musicplayer.lytrix.ui.icons.LytrixIcons.Filled.Home), "Home"),
                    Triple("EXPLORE", Pair(Icons.Outlined.Explore, Icons.Filled.Explore), "Explore"),
                    Triple("SEARCH", Pair(com.musicplayer.lytrix.ui.icons.LytrixIcons.Outlined.Search, com.musicplayer.lytrix.ui.icons.LytrixIcons.Filled.Search), "Search"),
                    Triple("LIBRARY", Pair(com.musicplayer.lytrix.ui.icons.LytrixIcons.Outlined.LibraryMusic, com.musicplayer.lytrix.ui.icons.LytrixIcons.Filled.LibraryMusic), "Library")
                )
                tabs.forEach { (tabId, iconPair, label) ->
                    val isSelected = currentBottomTab == tabId
                    val tabInteraction = remember { MutableInteractionSource() }
                    val tabBg by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        animationSpec = tween(260),
                        label = "onlineTabBg"
                    )
                    val contentColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = tween(260),
                        label = "onlineTabContent"
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(vertical = 8.dp, horizontal = 4.dp)
                            .tactileSpringPress(tabInteraction, 0.92f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(tabBg)
                            .clickable(interactionSource = tabInteraction, indication = ripple()) {
                                navHaptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                currentBottomTab = tabId
                                if (tabId == "SEARCH") currentSearchTab = "EXPLORE"
                                if (tabId == "LIBRARY") currentLibraryView = "MAIN"
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            com.musicplayer.lytrix.ui.icons.MorphingMetroIcon(
                                selected = isSelected,
                                outlinedIcon = iconPair.first,
                                filledIcon = iconPair.second,
                                contentDescription = label,
                                tint = contentColor,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = label,
                                color = contentColor,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
    }
    if (showProfileSheet) {
        val isPremiumUser = remember { isUserPremium(context) }
        val context = LocalContext.current
        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        ProfileBottomSheet(
            isPremium = isPremiumUser,
            onDismiss = { showProfileSheet = false },
            onMyProfile = {
                showProfileSheet = false
                if (currentUser != null) {
                    onNavigateToProfile()  // ইউজার লগ-ইন করা থাকলে প্রোফাইল পেজে যাবে
                } else {
                    android.widget.Toast.makeText(context, "Please Sign In to view your Profile", android.widget.Toast.LENGTH_SHORT).show()
                }
            },
            onFriends = {
                showProfileSheet = false
                if (currentUser != null) {
                    onNavigateToFriends()  // ইউজার লগ-ইন করা থাকলে ফ্রেন্ড পেজে যাবে
                } else {
                    android.widget.Toast.makeText(context, "Please Sign In first to use Friends feature", android.widget.Toast.LENGTH_SHORT).show() // গেস্ট হলে এই মেসেজ দেখাবে, পেজে যাবে না
                }
            },
            onListenTogether = {
                showProfileSheet = false
                onNavigateToListenTogether()  // 🔥 সরাসরি navigate
            },
            onUpgrade = {
                showProfileSheet = false
                showPremiumDialog = true
            },
            onSettings = {
                showProfileSheet = false
                onNavigateToOnlineSettings()
            },
            onDownloads = {
                showProfileSheet = false
                showDownloadsScreen = true
            },
            onSupport = {
                showProfileSheet = false
                try {
                    val intent = android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://t.me/lytrixsupport")
                    )
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "Could not open Telegram link", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // 🔥 প্রিমিয়াম ডাউনলোড স্ক্রিন ডিসপ্লে (প্রোফাইল শিটের বাইরে আনা হয়েছে)
    if (showDownloadsScreen) {
        androidx.activity.compose.BackHandler { showDownloadsScreen = false }
        DownloadsScreen(
            onNavigateBack = { showDownloadsScreen = false },
            onPlaySong = { task ->
                val song = com.musicplayer.lytrix.model.JioSaavnSong(
                    id = task.id,
                    name = task.title,
                    artist = task.artist,
                    imageUrl = task.imageUrl,
                    downloadUrl = task.providedUrl
                )
                viewModel.playOnlineSongWithAd(context, song, listOf(song))
            }
        )
    }
    if (showHistoryScreen) {
        androidx.activity.compose.BackHandler { showHistoryScreen = false }
        com.musicplayer.lytrix.ui.screens.HistoryScreen(
            viewModel = viewModel,
            onNavigateBack = { showHistoryScreen = false }
        )
    }
    activeSaavnCollection?.let { card ->
        androidx.activity.compose.BackHandler { activeSaavnCollection = null }
        val details = activeSaavnCollectionDetails
        val colSongs = details?.songs.orEmpty()
        val colTitle = details?.title?.takeIf { it.isNotBlank() } ?: card.title
        val colSubtitle = details?.subtitle?.takeIf { it.isNotBlank() } ?: card.subtitle
        val colArtwork = details?.imageUrl?.takeIf { it.isNotBlank() } ?: card.imageUrl
        val isAlbumCol = card.type == com.musicplayer.lytrix.saavn.SaavnItemType.ALBUM
        val dbName = if (isAlbumCol) "online_albums_meta" else "online_playlists_meta"
        val prefix = if (isAlbumCol) "online_album_" else "online_pl_"
        val plPrefs = context.getSharedPreferences(dbName, Context.MODE_PRIVATE)
        var isSavedCol by remember(colTitle) {
            mutableStateOf(plPrefs.getStringSet("names", emptySet())?.contains(colTitle) == true)
        }
        val isFullyDlCol = remember(colSongs) {
            colSongs.isNotEmpty() && colSongs.all {
                File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "${it.name.replace(Regex("[^a-zA-Z0-9 ]"), "_").take(50).trim()}.mp3").exists()
            }
        }
        MetroSaavnCollectionScreen(
            viewModel = viewModel,
            collectionId = card.id,
            title = colTitle,
            subtitle = details?.author?.takeIf { it.isNotBlank() } ?: if (isAlbumCol) colSubtitle.removePrefix("Album • ").ifBlank { "JioSaavn" } else "JioSaavn",
            artworkUrl = colArtwork,
            songs = colSongs,
            isLoading = isSaavnCollectionLoading,
            isSaved = isSavedCol,
            isFullyDownloaded = isFullyDlCol,
            isAlbum = isAlbumCol,
            year = details?.year.orEmpty(),
            fansText = details?.fansText.orEmpty(),
            description = details?.description.orEmpty(),
            onBack = { activeSaavnCollection = null },
            onToggleSave = { displayImage ->
                val set = plPrefs.getStringSet("names", emptySet())?.toMutableSet() ?: mutableSetOf()
                val songPrefs = context.getSharedPreferences("$prefix$colTitle", Context.MODE_PRIVATE)
                val coverPrefs = context.getSharedPreferences("playlist_covers", Context.MODE_PRIVATE)
                val timePrefs = context.getSharedPreferences("playlist_time", Context.MODE_PRIVATE)
                val sourcePrefsSave = context.getSharedPreferences("playlist_source", Context.MODE_PRIVATE)
                val idPrefsSave = context.getSharedPreferences("playlist_ids", Context.MODE_PRIVATE)
                if (isSavedCol) {
                    set.remove(colTitle)
                    songPrefs.edit().clear().apply()
                    coverPrefs.edit().remove(colTitle).apply()
                    timePrefs.edit().remove(colTitle).apply()
                    sourcePrefsSave.edit().remove(colTitle).apply()
                    idPrefsSave.edit().remove(colTitle).apply()
                    plPrefs.edit().remove("count_$colTitle").apply()
                    Toast.makeText(context, "Removed from Library", Toast.LENGTH_SHORT).show()
                } else {
                    set.add(colTitle)
                    val editor = songPrefs.edit()
                    colSongs.forEach { s ->
                        val obj = JSONObject()
                        obj.put("id", s.id)
                        obj.put("title", s.name)
                        obj.put("name", s.name)
                        obj.put("artist", s.artist)
                        obj.put("imageUrl", s.imageUrl)
                        obj.put("downloadUrl", s.downloadUrl)
                        editor.putString(s.id, obj.toString())
                    }
                    editor.apply()
                    coverPrefs.edit().putString(colTitle, displayImage).apply()
                    timePrefs.edit().putLong(colTitle, System.currentTimeMillis()).apply()
                    sourcePrefsSave.edit().putString(colTitle, "JioSaavn").apply()
                    idPrefsSave.edit().putString(colTitle, "saavn_${if (isAlbumCol) "album" else "playlist"}_${card.id}").apply()
                    plPrefs.edit().putInt("count_$colTitle", colSongs.size).apply()
                    Toast.makeText(context, "Saved to Library", Toast.LENGTH_SHORT).show()
                }
                plPrefs.edit().putStringSet("names", set).apply()
                isSavedCol = !isSavedCol
                refreshTriggerLocal++
            },
            onDownloadAll = {
                if (!isFullyDlCol && colSongs.isNotEmpty()) {
                    requestDownloadWithAd(context, colTitle, isPlaylist = true) {
                        colSongs.forEach { s ->
                            val safeName = s.name.replace(Regex("[^a-zA-Z0-9 ]"), "_").take(50).trim()
                            if (!File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "$safeName.mp3").exists()) {
                                com.musicplayer.lytrix.playback.OkHttpDownloader.startDownload(
                                    context = context,
                                    songId = s.id,
                                    title = s.name,
                                    artist = s.artist,
                                    imageUrl = s.imageUrl,
                                    providedUrl = s.downloadUrl,
                                    source = com.musicplayer.lytrix.playback.DownloadSource.JIOSAAVN,
                                )
                            }
                        }
                        Toast.makeText(context, "Downloading tracks...", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onPlayTrack = { clickedSong, orderedSongs, _ ->
                viewModel.playOnlineSongWithAd(context, clickedSong, orderedSongs)
            },
            onTrackMenuClick = { song ->
                selectedBottomSong = song
                showBottomSheet = true
            },
            bottomPadding = bottomListPadding,
        )
    }
    if (showPremiumDialog) {
        val accentColor = com.musicplayer.lytrix.ui.theme.LocalAccentColor.current
        PremiumActivationDialog(
            accentColor = accentColor,
            onDismiss = { showPremiumDialog = false },
            onSuccess = {
                showPremiumDialog = false
                Toast.makeText(context, "🎉 Premium Activated!", Toast.LENGTH_LONG).show()
            }
        )
    }

// ==========================================

    // 🔥 Dialog: Create Playlist (Called from FAB)

    // ==========================================
    activeSaavnSection?.let { sec ->
        Box(modifier = Modifier.fillMaxSize().zIndex(34f)) {
            MetroSaavnSectionDetailOverlay(
                section = sec,
                onBack = { activeSaavnSection = null },
                onItemClick = { card ->
                    if (card.type == com.musicplayer.lytrix.saavn.SaavnItemType.SONG && card.song != null) {
                        val songList = sec.items.mapNotNull { it.song }
                        scope.launch(Dispatchers.IO) {
                            val playable = com.musicplayer.lytrix.saavn.JioSaavnLiveRepository.resolvePlayableSong(card.song)
                            withContext(Dispatchers.Main) {
                                val queue = songList.map { if (it.id == playable.id) playable else it }
                                viewModel.playOnlineSongWithAd(context, playable, queue.ifEmpty { listOf(playable) })
                            }
                        }
                    } else {
                        openSaavnCollection(card)
                    }
                },
                onSongMoreClick = { song ->
                    selectedBottomSong = song
                    showBottomSheet = true
                },
                bottomPadding = bottomListPadding,
            )
        }
    }
    activeMediaHubSection?.let { sec ->
        Box(modifier = Modifier.fillMaxSize().zIndex(34f)) {
            MetroMediaHubSectionDetailOverlay(
                section = sec,
                onBack = { activeMediaHubSection = null },
                onCardClick = { card -> handleMediaHubCardClick(card, sec.items) },
                onCardLongClick = { card ->
                    selectedMediaHubSheetCard = card
                    selectedMediaHubSheetQueue = sec.items
                },
                bottomPadding = bottomListPadding,
            )
        }
    }
    activeMediaHubCollectionCard?.let { card ->
        Box(modifier = Modifier.fillMaxSize().zIndex(35f)) {
            MetroMediaHubCollectionOverlay(
                card = card,
                details = activeMediaHubCollectionDetails,
                isLoading = isMediaHubCollectionLoading,
                context = context,
                viewModel = viewModel,
                onBack = {
                    activeMediaHubCollectionCard = null
                    activeMediaHubCollectionDetails = null
                },
                onSavedStateChanged = { refreshTriggerLocal++ },
                onDownloadEpisode = { kind, ep ->
                    if (kind == com.musicplayer.lytrix.media.MediaHubKind.AUDIOBOOK) {
                        downloadAudiobookSecurely(context, ep)
                    } else {
                        downloadPodcastSecurely(context, ep)
                    }
                },
                bottomPadding = bottomListPadding,
            )
        }
    }
    selectedMediaHubSheetCard?.let { card ->
        MetroMediaHubCardBottomSheet(
            card = card,
            context = context,
            onDismiss = { selectedMediaHubSheetCard = null },
            onOpenOrPlay = {
                selectedMediaHubSheetCard = null
                handleMediaHubCardClick(card, selectedMediaHubSheetQueue)
            },
            onLikedChanged = { refreshTriggerLocal++ },
            onDownloadEpisode = { kind, ep ->
                if (kind == com.musicplayer.lytrix.media.MediaHubKind.AUDIOBOOK) {
                    downloadAudiobookSecurely(context, ep)
                } else {
                    downloadPodcastSecurely(context, ep)
                }
            },
        )
    }
    activeExploreMoodTitle?.let { moodTitle ->
        Box(modifier = Modifier.fillMaxSize().zIndex(34f)) {
            MetroMoodCategoryOverlay(
                title = moodTitle,
                sections = exploreCategoryPlaylists,
                onBack = { activeExploreMoodTitle = null },
                onTrackClick = handleYtTrackTap,
                onTrackLongClick = handleYtTrackMore,
                onOpenSectionDetail = { payload -> activeMetroSection = payload },
                bottomPadding = bottomListPadding,
            )
        }
    }
    activeMetroSection?.let { payload ->
        Box(modifier = Modifier.fillMaxSize().zIndex(35f)) {
            MetroSectionDetailOverlay(
                payload = payload,
                viewModel = viewModel,
                onBack = { activeMetroSection = null },
                onTrackClick = handleYtTrackTap,
                onTrackLongClick = handleYtTrackMore,
                bottomPadding = bottomListPadding,
            )
        }
    }
    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            containerColor = cardBgColor,
            title = { Text("New Playlist", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    placeholder = { Text("Title...", color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newPlaylistName.isNotBlank()) {
                        val plPrefs = context.getSharedPreferences("online_playlists_meta", Context.MODE_PRIVATE)
                        val existing = plPrefs.getStringSet("names", emptySet<String>())?.toMutableSet() ?: mutableSetOf()
                        existing.add(newPlaylistName)
                        plPrefs.edit().putStringSet("names", existing).apply()

                        // 🔥 Add Timestamp & Source for perfect sorting
                        context.getSharedPreferences("playlist_time", Context.MODE_PRIVATE).edit().putLong(newPlaylistName, System.currentTimeMillis()).apply()
                        context.getSharedPreferences("playlist_source", Context.MODE_PRIVATE).edit().putString(newPlaylistName, "Custom").apply()
                        Toast.makeText(context, "Playlist created", Toast.LENGTH_SHORT).show()
                        newPlaylistName = ""
                        refreshTriggerLocal++ // 🔥 Instant Refresh Trigger!
                        showCreatePlaylistDialog = false
                    }
                }) { Text("Create", color = accentColor) }
            },
            dismissButton = { TextButton(onClick = { showCreatePlaylistDialog = false }) { Text("Cancel", color = Color.White) } }
        )
    }
    if (showPlaylistDialogForSong != null) {
        AlertDialog(
            onDismissRequest = { showPlaylistDialogForSong = null },
            containerColor = cardBgColor,
            title = { Text("Add to Playlist", color = Color.White) },
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
                                        val obj = JSONObject()
                                        obj.put("id", showPlaylistDialogForSong!!.id)
                                        obj.put("title", showPlaylistDialogForSong!!.name)
                                        obj.put("name", showPlaylistDialogForSong!!.name)
                                        obj.put("artist", showPlaylistDialogForSong!!.artist)
                                        obj.put("imageUrl", showPlaylistDialogForSong!!.imageUrl)
                                        obj.put("downloadUrl", showPlaylistDialogForSong!!.downloadUrl)
                                        songPrefs.edit().putString(showPlaylistDialogForSong!!.id, obj.toString()).apply()
                                        Toast.makeText(context, "Added to $plName", Toast.LENGTH_SHORT).show()
                                        showPlaylistDialogForSong = null
                                    }.padding(8.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = newPlaylistName, onValueChange = { newPlaylistName = it }, placeholder = { Text("Or create new playlist...", color = Color.Gray) }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newPlaylistName.isNotBlank()) {
                        val plPrefs = context.getSharedPreferences("online_playlists_meta", Context.MODE_PRIVATE)
                        val existing = plPrefs.getStringSet("names", emptySet<String>())?.toMutableSet() ?: mutableSetOf()
                        existing.add(newPlaylistName)
                        context.getSharedPreferences("playlist_time", Context.MODE_PRIVATE).edit().putLong(newPlaylistName, System.currentTimeMillis()).apply()
                        context.getSharedPreferences("playlist_source", Context.MODE_PRIVATE).edit().putString(newPlaylistName, "Custom").apply()
                        plPrefs.edit().putStringSet("names", existing).apply()
                        val songPrefs = context.getSharedPreferences("online_pl_$newPlaylistName", Context.MODE_PRIVATE)
                        val obj = JSONObject()
                        obj.put("id", showPlaylistDialogForSong!!.id)
                        obj.put("title", showPlaylistDialogForSong!!.name)
                        obj.put("name", showPlaylistDialogForSong!!.name)
                        obj.put("artist", showPlaylistDialogForSong!!.artist)
                        obj.put("imageUrl", showPlaylistDialogForSong!!.imageUrl)
                        obj.put("downloadUrl", showPlaylistDialogForSong!!.downloadUrl)
                        songPrefs.edit().putString(showPlaylistDialogForSong!!.id, obj.toString()).apply()
                        Toast.makeText(context, "Created & Added", Toast.LENGTH_SHORT).show()
                        newPlaylistName = ""
                        showPlaylistDialogForSong = null
                    }
                }) { Text("Create & Add", color = accentColor) }
            },
            dismissButton = { TextButton(onClick = { showPlaylistDialogForSong = null }) { Text("Cancel", color = Color.White) } }
        )
    }

    if (showCreateShowsPlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreateShowsPlaylistDialog = false },
            containerColor = cardBgColor,
            title = { Text("New Shows Playlist", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newShowsPlaylistName,
                    onValueChange = { newShowsPlaylistName = it },
                    placeholder = { Text("Playlist name...", color = Color.Gray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newShowsPlaylistName.isNotBlank()) {
                        val plPrefs = context.getSharedPreferences("online_shows_playlists_meta", Context.MODE_PRIVATE)
                        val existing = plPrefs.getStringSet("names", emptySet<String>())?.toMutableSet() ?: mutableSetOf()
                        val trimmedName = newShowsPlaylistName.trim()
                        existing.add(trimmedName)
                        plPrefs.edit().putStringSet("names", existing).apply()
                        context.getSharedPreferences("playlist_time", Context.MODE_PRIVATE).edit().putLong(trimmedName, System.currentTimeMillis()).apply()
                        context.getSharedPreferences("playlist_source", Context.MODE_PRIVATE).edit().putString(trimmedName, "Shows").apply()
                        Toast.makeText(context, "Shows Playlist created", Toast.LENGTH_SHORT).show()
                        newShowsPlaylistName = ""
                        refreshTriggerLocal++
                        showCreateShowsPlaylistDialog = false
                    }
                }) { Text("Create", color = accentColor) }
            },
            dismissButton = { TextButton(onClick = { showCreateShowsPlaylistDialog = false }) { Text("Cancel", color = Color.White) } }
        )
    }

    if (showAddToShowsPlaylistDialog != null) {
        val targetItem = showAddToShowsPlaylistDialog!!
        AlertDialog(
            onDismissRequest = { showAddToShowsPlaylistDialog = null },
            containerColor = cardBgColor,
            title = { Text("Add to Shows Playlist", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    val plPrefs = context.getSharedPreferences("online_shows_playlists_meta", Context.MODE_PRIVATE)
                    val playlists = plPrefs.getStringSet("names", emptySet<String>())?.toList() ?: emptyList()
                    if (playlists.isNotEmpty()) {
                        Text("Existing Playlists:", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                        LazyColumn(modifier = Modifier.heightIn(max = 160.dp).padding(vertical = 8.dp)) {
                            items(playlists) { plName ->
                                Text(
                                    text = plName,
                                    color = Color.White,
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        val showPlPrefs = context.getSharedPreferences("online_show_pl_$plName", Context.MODE_PRIVATE)
                                        val obj = JSONObject().apply {
                                            put("id", targetItem.id)
                                            put("title", targetItem.name)
                                            put("name", targetItem.name)
                                            put("artist", targetItem.artist)
                                            put("imageUrl", targetItem.imageUrl)
                                            put("downloadUrl", targetItem.downloadUrl)
                                            put("albumName", targetItem.albumName ?: "Shows")
                                            put("timestamp", System.currentTimeMillis())
                                        }
                                        showPlPrefs.edit().putString(targetItem.id, obj.toString()).apply()
                                        Toast.makeText(context, "Added to $plName", Toast.LENGTH_SHORT).show()
                                        refreshTriggerLocal++
                                        showAddToShowsPlaylistDialog = null
                                    }.padding(8.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newShowsPlaylistName,
                        onValueChange = { newShowsPlaylistName = it },
                        placeholder = { Text("Or create new shows playlist...", color = Color.Gray) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newShowsPlaylistName.isNotBlank()) {
                        val plPrefs = context.getSharedPreferences("online_shows_playlists_meta", Context.MODE_PRIVATE)
                        val existing = plPrefs.getStringSet("names", emptySet<String>())?.toMutableSet() ?: mutableSetOf()
                        val trimmedName = newShowsPlaylistName.trim()
                        existing.add(trimmedName)
                        plPrefs.edit().putStringSet("names", existing).apply()
                        context.getSharedPreferences("playlist_time", Context.MODE_PRIVATE).edit().putLong(trimmedName, System.currentTimeMillis()).apply()
                        context.getSharedPreferences("playlist_source", Context.MODE_PRIVATE).edit().putString(trimmedName, "Shows").apply()

                        val showPlPrefs = context.getSharedPreferences("online_show_pl_$trimmedName", Context.MODE_PRIVATE)
                        val obj = JSONObject().apply {
                            put("id", targetItem.id)
                            put("title", targetItem.name)
                            put("name", targetItem.name)
                            put("artist", targetItem.artist)
                            put("imageUrl", targetItem.imageUrl)
                            put("downloadUrl", targetItem.downloadUrl)
                            put("albumName", targetItem.albumName ?: "Shows")
                            put("timestamp", System.currentTimeMillis())
                        }
                        showPlPrefs.edit().putString(targetItem.id, obj.toString()).apply()
                        Toast.makeText(context, "Created & Added", Toast.LENGTH_SHORT).show()
                        newShowsPlaylistName = ""
                        refreshTriggerLocal++
                        showAddToShowsPlaylistDialog = null
                    }
                }) { Text("Create & Add", color = accentColor) }
            },
            dismissButton = { TextButton(onClick = { showAddToShowsPlaylistDialog = null }) { Text("Cancel", color = Color.White) } }
        )
    }

    // ==========================================

    // 🔥 1. Bottom Sheet for Songs (GRID Menu)

    // ==========================================
    if (showBottomSheet && (selectedBottomSong != null || selectedBottomPodcast != null)) {
        val isExplicitAudiobook = (selectedBottomSong != null && (selectedBottomSong!!.albumName == "Audiobook" || selectedBottomSong!!.id.startsWith("ab:"))) || (selectedBottomPodcast != null && (selectedBottomPodcast?.audio?.contains("librivox", ignoreCase = true) == true || selectedBottomPodcast?.title_original?.contains("audiobook", ignoreCase = true) == true)) || viewModel.currentMediaCategory.value == "AUDIOBOOK"
        val isExplicitPodcast = (selectedBottomSong != null && (selectedBottomSong!!.albumName == "Podcast" || selectedBottomSong!!.id.startsWith("pod:"))) || selectedBottomPodcast != null || (!isExplicitAudiobook && viewModel.currentMediaCategory.value == "PODCAST")
        val isShowItem = isExplicitAudiobook || isExplicitPodcast
        val isSong = selectedBottomSong != null && !isShowItem
        val isMetroGuest = viewModel.metroManager.roomRole.collectAsState().value == com.musicplayer.lytrix.listentogether.metro.MetroRoomRole.GUEST
        val myFbStatus = viewModel.myRoomStatus.collectAsState().value
        val isRoomGuest = isMetroGuest || (myFbStatus == "approved" || myFbStatus == "co_host")
        val title =
            if (isSong) selectedBottomSong!!.name else (selectedBottomPodcast?.title_original ?: selectedBottomSong?.name ?: "Unknown")
        val artist =
            if (isSong) selectedBottomSong!!.artist else (selectedBottomPodcast?.podcast?.publisher_original ?: selectedBottomSong?.artist ?: "Unknown")
        val image =
            if (isSong) selectedBottomSong!!.imageUrl else (selectedBottomPodcast?.image ?: selectedBottomSong?.imageUrl ?: "")
        val id = if (isSong) selectedBottomSong!!.id else (selectedBottomPodcast?.id ?: selectedBottomSong?.id ?: "")
        val isAudiobookItem = isExplicitAudiobook
        val prefKey = if (isSong) "online_liked_songs" else if (isAudiobookItem) "online_liked_audiobooks" else "online_liked_podcasts"
        val prefs = context.getSharedPreferences(prefKey, Context.MODE_PRIVATE)
        var isLiked by remember(id, prefKey) {
            mutableStateOf(
                prefs.getStringSet("liked_ids", mutableSetOf())?.contains(id) == true
            )
        }

        // 🔥 URL Check for YT vs JioSaavn (Fixed!)
        val isJioSaavn = isSong && (
            selectedBottomSong!!.downloadUrl.isNotBlank() ||
                selectedBottomSong!!.imageUrl.contains("saavn", ignoreCase = true)
            )
        val isYtItem = isSong && !isJioSaavn

        // 🔥 Dynamic Download/Remove Logic
        val safeSongName =
            if (isSong) selectedBottomSong!!.name.replace(Regex("[^a-zA-Z0-9 ]"), "_").take(50)
                .trim() else ""
        val songFile = remember(id) {
            File(
                context.getExternalFilesDir(Environment.DIRECTORY_MUSIC),
                "$safeSongName.mp3"
            )
        }
        var isDownloadedLocally by remember(id) { mutableStateOf(false) }
        LaunchedEffect(id) {
            if (isSong) isDownloadedLocally = songFile.exists()
        }
        com.musicplayer.lytrix.ui.component.LytrixFluidBottomSheet(
            onDismissRequest = {
                showBottomSheet = false; selectedBottomSong = null; selectedBottomPodcast = null
            },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 32.dp)
            ) {

                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = image,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
                            .background(Color.DarkGray)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = artist,
                            color = Color.Gray,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = {
                        if (isSong) {
                            viewModel.toggleOnlineFavorite(context, selectedBottomSong!!)
                        } else {
                            val ep = selectedBottomPodcast ?: selectedBottomSong?.let { s ->
                                com.musicplayer.lytrix.PodcastEpisode(
                                    id = s.id,
                                    title_original = s.name,
                                    audio = s.downloadUrl ?: "",
                                    image = s.imageUrl,
                                    audio_length_sec = 0,
                                    podcast = com.musicplayer.lytrix.PodcastInfo(publisher_original = s.artist)
                                )
                            }
                            if (ep != null) {
                                if (isAudiobookItem) {
                                    toggleAudiobookFavorite(context, ep)
                                } else {
                                    togglePodcastFavorite(context, ep)
                                }
                            }
                        }
                        isLiked = !isLiked
                        refreshTriggerLocal++
                    }) {
                        Icon(
                            if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Like",
                            tint = if (isLiked) Color.Red else Color.White
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f), thickness = 1.dp)
                Spacer(modifier = Modifier.height(16.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isSong) {
                        YtSongMenuBody(
                            isDownloadedLocally = isDownloadedLocally,
                            isPinnedToSpeedDial = pinnedIds.contains(selectedBottomSong?.id ?: ""),
                            showPinToSpeedDial = !isJioSaavn,
                            onPlaylist = {
                                showPlaylistDialogForSong = selectedBottomSong
                                showBottomSheet = false
                            },
                            onShare = {
                                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    val shareMsg = if (isJioSaavn) "Listen to ${selectedBottomSong?.name} by ${selectedBottomSong?.artist} on Lytrix Player!" else "https://music.youtube.com/watch?v=$id"
                                        putExtra(android.content.Intent.EXTRA_TEXT, shareMsg)
                                }
                                context.startActivity(android.content.Intent.createChooser(intent, "Share"))
                                showBottomSheet = false
                            },
                            onDetails = {
                                detailsSong = selectedBottomSong
                                showBottomSheet = false
                            },
                            onPlayNext = {
                                viewModel.playNextOnline(context, selectedBottomSong!!)
                                showBottomSheet = false
                            },
                            onDownloadToggle = {
                                if (isDownloadedLocally) {
                                    if (songFile.exists()) songFile.delete()
                                    com.musicplayer.lytrix.playback.OkHttpDownloader.deleteSongFiles(context, selectedBottomSong!!.name)
                                    context.getSharedPreferences("downloaded_meta", Context.MODE_PRIVATE).edit().remove("song_$id").apply()
                                    isDownloadedLocally = false
                                    refreshTriggerLocal++
                                    Toast.makeText(context, "Removed from device", Toast.LENGTH_SHORT).show()
                                } else {
                                    downloadSongSecurely(context, selectedBottomSong!!)
                                }
                                showBottomSheet = false
                            },
                            onExportToStorage = {
                                selectedBottomSong?.let { songItem ->
                                    if (!isUserPremium(context)) {
                                        Toast.makeText(context, "👑 Save to Storage is exclusive to Lytrix Premium users!", Toast.LENGTH_LONG).show()
                                        return@let
                                    }
                                    Toast.makeText(context, "Saving...", Toast.LENGTH_SHORT).show()
                                    CoroutineScope(Dispatchers.IO).launch {
                                        val success = com.musicplayer.lytrix.playback.OkHttpDownloader.exportSongToStorage(
                                            context = context,
                                            songId = songItem.id,
                                            title = songItem.name,
                                            artist = songItem.artist
                                        )
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(
                                                context,
                                                if (success) "Saved to Music/Lytrix 🎵" else "Export failed",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                                showBottomSheet = false
                            },
                            onStartRadio = { showBottomSheet = false },
                            onGoToArtist = {
                                val cleanArtist = viewModel.cleanArtistDisplayName(selectedBottomSong!!.artist)
                                val parsedArtists = cleanArtist.split(",", " & ").map { it.trim() }.filter { it.isNotBlank() }
                                if (parsedArtists.size == 1) {
                                    showBottomSheet = false
                                    onNavigateToArtist(parsedArtists.first())
                                } else if (parsedArtists.size > 1) {
                                    showBottomSheet = false
                                    artistsListForDialog = parsedArtists
                                } else {
                                    Toast.makeText(context, "Artist not found", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onViewAlbum = if (!isJioSaavn) {
                                {
                                    val target = selectedBottomSong
                                    showBottomSheet = false
                                    if (target != null) {
                                        viewModel.openAlbumForOnlineSong(context, target)
                                    }
                                }
                            } else null,
                            onPinToSpeedDial = {
                                selectedBottomSong?.let { song ->
                                    val isPinned = pinnedIds.contains(song.id)
                                    viewModel.toggleSpeedDial(PlayerViewModel.YtMusicTrack(
                                        id = song.id, title = song.name, artist = song.artist, thumbnailUrl = song.imageUrl
                                    ))
                                    val msg = if (isPinned) "Unpinned from Speed Dial" else "Pinned to Speed Dial"
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                                showBottomSheet = false
                            },
                            onNotInterested = {
                                val songToRemove = selectedBottomSong
                                showBottomSheet = false
                                songToRemove?.let { song ->
                                    viewModel.markSongNotInterested(song.id, song.name, song.artist, song.imageUrl)
                                    viewModel.triggerUndoSnackbar(song.id)
                                }
                            },
                            isMetroGuest = isRoomGuest,
                            onSuggestToHost = {
                                selectedBottomSong?.let { songItem ->
                                    val isSaavn = songItem.downloadUrl.isNotBlank() || songItem.imageUrl.contains("saavn", ignoreCase = true)
                                    val trackId = if (isSaavn) "saavn:${songItem.id}" else songItem.id
                                    val trackAlbum = if (isSaavn && songItem.downloadUrl.isNotBlank()) "saavn_url:${songItem.downloadUrl}" else "JioSaavn"
                                    viewModel.suggestTrackToActiveRoom(
                                        id = trackId,
                                        title = songItem.name,
                                        artist = songItem.artist,
                                        album = trackAlbum,
                                        thumbnail = songItem.imageUrl,
                                        streamUrl = songItem.downloadUrl ?: "",
                                        context = context
                                    )
                                }
                                showBottomSheet = false
                            }
                        )
                    } else {
                        // Dedicated Podcast & Audiobook Menu (MetroList vertical rows)
                        val ep = selectedBottomPodcast ?: selectedBottomSong?.let { s ->
                            com.musicplayer.lytrix.PodcastEpisode(
                                id = s.id,
                                title_original = s.name,
                                audio = s.downloadUrl ?: "",
                                image = s.imageUrl,
                                audio_length_sec = 0,
                                podcast = com.musicplayer.lytrix.PodcastInfo(publisher_original = s.artist)
                            )
                        }
                        val isAbItem = isAudiobookItem
                        val laterPrefix = if (isAbItem) "online_ab_" else "online_podcast_"
                        val laterName = if (isAbItem) "Books for later" else "Episodes for later"
                        val laterPlaylistPrefs = context.getSharedPreferences("$laterPrefix$laterName", Context.MODE_PRIVATE)
                        val metaPrefKey = if (isAbItem) "online_audiobooks_meta" else "online_podcasts_meta"
                        val laterIdsKey = if (isAbItem) "books_for_later_ids" else "episodes_for_later_ids"
                        val laterPrefs = context.getSharedPreferences(metaPrefKey, Context.MODE_PRIVATE)
                        val laterSet = laterPrefs.getStringSet(laterIdsKey, emptySet())?.toMutableSet() ?: mutableSetOf()
                        val isSavedForLater = ep != null && (laterPlaylistPrefs.contains(ep.id) || laterSet.contains(ep.id))

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            ) {
                                LytrixBottomSheetListItem(
                                    icon = Icons.Default.PlaylistPlay,
                                    text = "Play next",
                                    onClick = {
                                        if (ep != null) {
                                            val fakeSong = JioSaavnSong(
                                                id = ep.id,
                                                name = ep.title_original ?: "Episode",
                                                artist = ep.podcast?.publisher_original ?: "",
                                                imageUrl = ep.image ?: "",
                                                downloadUrl = ep.audio ?: "",
                                                albumName = if (isAbItem) "Audiobook" else "Podcast"
                                            )
                                            viewModel.playNextOnline(context, fakeSong)
                                        }
                                        showBottomSheet = false
                                    }
                                )
                                LytrixBottomSheetListItem(
                                    icon = if (isSavedForLater) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    text = if (isSavedForLater) (if (isAbItem) "Saved in Books for later" else "Saved in Episodes for later") else (if (isAbItem) "Save to Books for later" else "Save to Episodes for later"),
                                    onClick = {
                                        if (ep != null) {
                                            if (isSavedForLater) {
                                                laterSet.remove(ep.id)
                                                laterPlaylistPrefs.edit().remove(ep.id).apply()
                                                Toast.makeText(context, if (isAbItem) "Removed from Books for Later" else "Removed from Episodes for Later", Toast.LENGTH_SHORT).show()
                                            } else {
                                                laterSet.add(ep.id)
                                                val json = JSONObject().apply {
                                                    put("id", ep.id)
                                                    put("name", ep.title_original ?: "Episode")
                                                    put("title", ep.title_original ?: "Episode")
                                                    put("artist", ep.podcast?.publisher_original ?: "")
                                                    put("publisher", ep.podcast?.publisher_original ?: "")
                                                    put("imageUrl", ep.image ?: "")
                                                    put("downloadUrl", ep.audio ?: "")
                                                    put("albumName", if (isAbItem) "Audiobook" else "Podcast")
                                                    ep.channelId?.let { put("channelId", it) }
                                                }
                                                laterPlaylistPrefs.edit().putString(ep.id, json.toString()).apply()
                                                Toast.makeText(context, if (isAbItem) "Saved to Books for Later" else "Saved to Episodes for Later", Toast.LENGTH_SHORT).show()
                                            }
                                            laterPrefs.edit().putStringSet(laterIdsKey, laterSet).apply()
                                            refreshTriggerLocal++
                                        }
                                        showBottomSheet = false
                                    }
                                )
                                LytrixBottomSheetListItem(
                                    icon = Icons.Default.PlaylistAdd,
                                    text = "Add to playlist",
                                    onClick = {
                                        showBottomSheet = false
                                        val songForPl = selectedBottomSong ?: if (ep != null) JioSaavnSong(
                                            id = ep.id,
                                            name = ep.title_original ?: "Episode",
                                            artist = ep.podcast?.publisher_original ?: "",
                                            imageUrl = ep.image ?: "",
                                            downloadUrl = ep.audio ?: "",
                                            albumName = if (isAbItem) "Audiobook" else "Podcast"
                                        ) else null
                                        showAddToShowsPlaylistDialog = songForPl
                                    }
                                )
                                LytrixBottomSheetListItem(
                                    icon = Icons.Default.Info,
                                    text = "Details & Show Notes",
                                    onClick = {
                                        if (ep != null) {
                                            val fakeSong = JioSaavnSong(
                                                id = ep.id,
                                                name = ep.title_original ?: "Episode",
                                                artist = ep.podcast?.publisher_original ?: "",
                                                imageUrl = ep.image ?: "",
                                                downloadUrl = ep.audio ?: "",
                                                albumName = if (isAbItem) "Audiobook" else "Podcast"
                                            )
                                            detailsSong = fakeSong
                                        }
                                        showBottomSheet = false
                                    }
                                )
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            ) {
                                LytrixBottomSheetListItem(
                                    icon = Icons.Default.Person,
                                    text = if (isAbItem) "Go to Author" else "Go to Channel / Show",
                                    onClick = {
                                        showBottomSheet = false
                                        val target = ep?.podcast?.channelId ?: ep?.podcast?.publisher_original
                                        if (!target.isNullOrBlank()) {
                                            val cleanT = viewModel.cleanArtistDisplayName(target)
                                            val routedTarget = if (!cleanT.startsWith("UC") && !cleanT.startsWith("AUDIOBOOK_") && !cleanT.startsWith("PODCAST_")) {
                                                if (isAbItem) "AUDIOBOOK_$cleanT" else "PODCAST_$cleanT"
                                            } else {
                                                cleanT
                                            }
                                            onNavigateToArtist(routedTarget)
                                        }
                                    }
                                )
                                LytrixBottomSheetListItem(
                                    icon = Icons.Default.Download,
                                    text = if (isAbItem) "Download Audiobook" else "Download Episode",
                                    onClick = {
                                        if (ep != null) {
                                            downloadPodcastSecurely(context, ep)
                                        }
                                        showBottomSheet = false
                                    }
                                )
                                LytrixBottomSheetListItem(
                                    icon = Icons.Default.Share,
                                    text = "Share",
                                    onClick = {
                                        val shareUrl = if (ep?.audio.isNullOrBlank() || ep?.audio?.startsWith("http") != true) "https://music.youtube.com/watch?v=${ep?.id}" else ep?.audio
                                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(android.content.Intent.EXTRA_TEXT, "${ep?.title_original ?: "Episode"} - $shareUrl")
                                        }
                                        context.startActivity(android.content.Intent.createChooser(intent, "Share"))
                                        showBottomSheet = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

// ==========================================

    // 🔥 2. YT Playlist Custom Sheet (Advanced Background Save)

    // ==========================================
    if (showYtPlaylistBottomSheet && selectedYtPlaylist != null) {
        val playlist = selectedYtPlaylist!!
        com.musicplayer.lytrix.ui.component.LytrixFluidBottomSheet(
            onDismissRequest = { showYtPlaylistBottomSheet = false; selectedYtPlaylist = null },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {

                // Header (Thumbnail, Title, Close)
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(model = playlist.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = playlist.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(text = playlist.artist, color = Color.Gray, fontSize = 14.sp, maxLines = 1)
                    }
                    IconButton(onClick = { showYtPlaylistBottomSheet = false; selectedYtPlaylist = null }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // 🔥 ডাটাবেস ঠিক করা হলো (Album নাকি Playlist)
                val dbType = ytPlaylistType // "Album" or "Playlist"
                val dbName = if (dbType == "Album") "online_albums_meta" else if (dbType == "Podcast") "online_podcasts_meta" else "online_playlists_meta"
                val prefix = if (dbType == "Album") "online_album_" else if (dbType == "Podcast") "online_podcast_" else "online_pl_"
                val plPrefs = context.getSharedPreferences(dbName, Context.MODE_PRIVATE)
                var isSaved by remember(playlist.title) { mutableStateOf(plPrefs.getStringSet("names", emptySet())?.contains(playlist.title) == true) }

                // Top 3 action row (Play next, Save, Share)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { showYtPlaylistBottomSheet = false; viewModel.loadYtPlaylistDetails(playlist.id, dbType, playlist.title) }.padding(8.dp)) {
                        Icon(Icons.Default.PlaylistPlay, contentDescription = "Play next", tint = Color.White, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Play next", color = Color.White, fontSize = 12.sp)
                    }

// 🔥 সলিউশন ৩: 100% ArchiveToon Style Save (Fast & Clean)
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable {
                        val targetPrefs = context.getSharedPreferences(dbName, Context.MODE_PRIVATE)
                        val coverPrefs = context.getSharedPreferences("playlist_covers", Context.MODE_PRIVATE)
                        val timePrefs = context.getSharedPreferences("playlist_time", Context.MODE_PRIVATE)
                        val idPrefs = context.getSharedPreferences("playlist_ids", Context.MODE_PRIVATE)
                        val set = targetPrefs.getStringSet("names", emptySet())?.toMutableSet() ?: mutableSetOf()
                        if (isSaved) {

                            // রিমুভ লজিক
                            set.remove(playlist.title)
                            targetPrefs.edit().putStringSet("names", set).apply()
                            context.getSharedPreferences("$prefix${playlist.title}", Context.MODE_PRIVATE).edit().clear().apply()
                            coverPrefs.edit().remove(playlist.title).apply()
                            timePrefs.edit().remove(playlist.title).apply()
                            idPrefs.edit().remove(playlist.title).apply()
                            Toast.makeText(context, "Removed from $dbType", Toast.LENGTH_SHORT).show()
                            isSaved = false
                            refreshTriggerLocal++
                        } else {

                            // 🔥 ম্যাজিক: Optimistic UI (সাথে সাথে সেভ দেখাবে)
                            isSaved = true
                            refreshTriggerLocal++
                            showYtPlaylistBottomSheet = false // শিট ক্লোজ হয়ে যাবে
                            Toast.makeText(context, "Saving $dbType in background...", Toast.LENGTH_SHORT).show()

                            // 🔥 ব্যাকগ্রাউন্ডে ইনারটিউব দিয়ে পারফেক্টলি সব ডেটা সেভ করবে (NewPipe বাদ!)
                            viewModel.saveYtPlaylistSilently(
                                id = playlist.id,
                                type = dbType,
                                title = playlist.title,
                                thumbnailUrl = playlist.thumbnailUrl,
                                context = context
                            )
                        }
                    }.padding(8.dp)) {
                        Icon(if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, contentDescription = "Save", tint = Color.White, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(if (isSaved) "Saved" else "Save to library", color = Color.White, fontSize = 12.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable {
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            val searchLink = "https://music.youtube.com/search?q=${java.net.URLEncoder.encode(playlist.title, "UTF-8")}"
                            putExtra(android.content.Intent.EXTRA_TEXT, searchLink)
                        }
                        context.startActivity(android.content.Intent.createChooser(intent, "Share"))
                        showYtPlaylistBottomSheet = false
                    }.padding(8.dp)) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Share", color = Color.White, fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color(0xFF2D2D2D), thickness = 1.dp)

                // List Items (Shuffle, Download)
                Row(modifier = Modifier.fillMaxWidth().clickable { showYtPlaylistBottomSheet = false; viewModel.loadYtPlaylistDetails(playlist.id, dbType, playlist.title) /* Need details to shuffle */ }.padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Shuffle, contentDescription = "Shuffle", tint = Color.White)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Shuffle play", color = Color.White, fontSize = 16.sp)
                }
                Row(modifier = Modifier.fillMaxWidth().clickable {
                    showYtPlaylistBottomSheet = false
                    requestDownloadWithAd(context, playlist.title, isPlaylist = true) {
                        viewModel.downloadYtPlaylistSilently(
                            id = playlist.id,
                            type = dbType,
                            title = playlist.title,
                            context = context
                        )
                    }
                }.padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Download, contentDescription = "Download", tint = Color.White)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Download", color = Color.White, fontSize = 16.sp)
                }
                val isPlaylistPinned = pinnedIds.contains(playlist.id)
                Row(modifier = Modifier.fillMaxWidth().clickable { 
                    viewModel.toggleSpeedDial(playlist)
                    val msg = if (isPlaylistPinned) "Unpinned from Speed Dial" else "Pinned to Speed Dial"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    showYtPlaylistBottomSheet = false
                }.padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (isPlaylistPinned) Icons.Default.Close else Icons.Default.PushPin, 
                        contentDescription = null, tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        if (isPlaylistPinned) "Unpin from Speed Dial" else "Pin to Speed Dial", 
                        color = Color.White, fontSize = 16.sp
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
}
}

@Composable

fun LytrixNavigationRail(
    currentBottomTab: String,
    onTabSelected: (String) -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val railHaptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val tabs = listOf(
        Triple("HOME", Pair(com.musicplayer.lytrix.ui.icons.LytrixIcons.Outlined.Home, com.musicplayer.lytrix.ui.icons.LytrixIcons.Filled.Home), "Home"),
        Triple("EXPLORE", Pair(Icons.Outlined.Explore, Icons.Filled.Explore), "Explore"),
        Triple("SEARCH", Pair(com.musicplayer.lytrix.ui.icons.LytrixIcons.Outlined.Search, com.musicplayer.lytrix.ui.icons.LytrixIcons.Filled.Search), "Search"),
        Triple("LIBRARY", Pair(com.musicplayer.lytrix.ui.icons.LytrixIcons.Outlined.LibraryMusic, com.musicplayer.lytrix.ui.icons.LytrixIcons.Filled.LibraryMusic), "Library")
    )
    Column(
        modifier = modifier
            .width(72.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        tabs.forEach { (tabId, iconPair, label) ->
            val isSelected = currentBottomTab == tabId
            val railInteraction = remember { MutableInteractionSource() }
            val tabBg by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                animationSpec = tween(260),
                label = "railTabBg"
            )
            val iconTint by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = tween(260),
                label = "railIconTint"
            )
            Box(
                modifier = Modifier
                    .size(width = 52.dp, height = 34.dp)
                    .tactileSpringPress(railInteraction, 0.90f)
                    .clip(RoundedCornerShape(17.dp))
                    .background(tabBg)
                    .clickable(interactionSource = railInteraction, indication = ripple()) {
                        railHaptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        onTabSelected(tabId)
                    },
                contentAlignment = Alignment.Center
            ) {
                com.musicplayer.lytrix.ui.icons.MorphingMetroIcon(
                    selected = isSelected,
                    outlinedIcon = iconPair.first,
                    filledIcon = iconPair.second,
                    contentDescription = label,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable

fun LytrixTopAppBar(
    logoResId: Int,
    onHistoryClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val topHaptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = if (isLandscape) 88.dp else 16.dp, end = 16.dp, top = 0.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(id = logoResId),
            contentDescription = "Lytrix Logo",
            modifier = Modifier.height(if (isLandscape) 36.dp else 56.dp),
            contentScale = androidx.compose.ui.layout.ContentScale.Fit
        )
        Spacer(modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = {
                    topHaptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                    onHistoryClick()
                },
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(com.musicplayer.lytrix.ui.icons.LytrixIcons.Outlined.History, contentDescription = "History", tint = MaterialTheme.colorScheme.onSurface)
            }
            IconButton(
                onClick = {
                    topHaptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                    onProfileClick()
                },
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(com.musicplayer.lytrix.ui.icons.LytrixIcons.Outlined.AccountCircle, contentDescription = "Profile", tint = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

// ==========================================

// 🔥 Helper & UI Components (Unified & Private)

// ==========================================

@Composable

private fun PremiumLibraryListItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBgColor: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(56.dp).clip(CircleShape).background(iconBgColor.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconBgColor, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = subtitle, color = Color.Gray, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

@Composable

private fun LytrixPlaylistListCard(playlistName: String, sourceText: String = "Custom", thumbnailUrl: String, isSynced: Boolean = true, songCount: Int = 0, isPinned: Boolean = false, onClick: () -> Unit, onPlayClick: () -> Unit, onRenameClick: () -> Unit, onDeleteClick: () -> Unit, onPinClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.97f else 1.0f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), label = "Scale")
    var showMenu by remember { mutableStateOf(false) }
    val isCustomPlaylist = sourceText.equals("Custom", ignoreCase = true) || sourceText.equals("Playlist", ignoreCase = true)
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).graphicsLayer { scaleX = scale; scaleY = scale }.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainer).border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f), RoundedCornerShape(16.dp)).clickable(interactionSource = interactionSource, indication = null, onClick = onClick).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(Color.DarkGray))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isPinned) { Icon(Icons.Default.PushPin, contentDescription = "Pinned", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp).padding(end = 4.dp)) }
                Text(text = playlistName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(modifier = Modifier.height(4.dp))
            when {
                sourceText.equals("Spotify", ignoreCase = true) -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = com.musicplayer.lytrix.R.drawable.ic_spotify),
                            contentDescription = "Spotify",
                            tint = Color(0xFF1DB954),
                            modifier = Modifier.size(14.dp).padding(end = 4.dp)
                        )
                        Text(
                            text = if (songCount > 0) "Spotify • $songCount tracks" else "Spotify • Tap to sync",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF1DB954), fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                sourceText.equals("YouTube", ignoreCase = true) -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = com.musicplayer.lytrix.R.drawable.ic_youtube_music),
                            contentDescription = "YouTube",
                            tint = Color(0xFFFF0033),
                            modifier = Modifier.size(14.dp).padding(end = 4.dp)
                        )
                        Text(
                            text = if (songCount > 0) "YouTube • $songCount tracks" else "YouTube • Tap to sync",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFFF4D6D), fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                sourceText.equals("JioSaavn", ignoreCase = true) -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = com.musicplayer.lytrix.R.drawable.ic_jiosaavn),
                            contentDescription = "JioSaavn",
                            tint = Color(0xFF2BC5B4),
                            modifier = Modifier.size(14.dp).padding(end = 4.dp)
                        )
                        Text(
                            text = if (songCount > 0) "JioSaavn • $songCount tracks" else "JioSaavn • Tap to sync",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF2BC5B4), fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                else -> {
                    val label = if (sourceText.equals("Custom", ignoreCase = true)) "Playlist" else sourceText
                    Text(text = "$label • ${songCount.coerceAtLeast(0)} tracks", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Box {
            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.MoreVert, contentDescription = "More", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                DropdownMenuItem(text = { Text(if (isPinned) "Unpin" else "Pin to top", color = MaterialTheme.colorScheme.onSurface) }, onClick = { showMenu = false; onPinClick() })
                if (isCustomPlaylist) {
                    DropdownMenuItem(text = { Text("Rename Playlist", color = MaterialTheme.colorScheme.onSurface) }, onClick = { showMenu = false; onRenameClick() })
                }
                DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, onClick = { showMenu = false; onDeleteClick() })
            }
        }
    }
}
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

@Composable

fun LytrixPlaylistGridCard(playlistName: String, sourceText: String = "Custom", songCount: Int, thumbnailUrl: String, isPinned: Boolean = false, onClick: () -> Unit, onPlayClick: () -> Unit, onRenameClick: () -> Unit, onDeleteClick: () -> Unit, onPinClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.97f else 1.0f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), label = "PlaylistGridCardScale")
    var showMenu by remember { mutableStateOf(false) }
    val isCustomPlaylist = sourceText.equals("Custom", ignoreCase = true) || sourceText.equals("Playlist", ignoreCase = true)
    Column(modifier = Modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale }.clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainer).border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f), RoundedCornerShape(24.dp)).combinedClickable(interactionSource = interactionSource, indication = null, onClick = onClick, onLongClick = { showMenu = true }).padding(12.dp)) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp))) {
            AsyncImage(model = thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().background(Color.DarkGray))
            Box(modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).clickable(onClick = onPlayClick), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
            }
            if (isPinned) {
                Box(modifier = Modifier.align(Alignment.TopStart).padding(8.dp).size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PushPin, contentDescription = "Pinned", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = playlistName, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        when {
            sourceText.equals("Spotify", ignoreCase = true) -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = com.musicplayer.lytrix.R.drawable.ic_spotify),
                        contentDescription = "Spotify",
                        tint = Color(0xFF1DB954),
                        modifier = Modifier.size(13.dp).padding(end = 4.dp)
                    )
                    Text(
                        text = if (songCount > 0) "Spotify • $songCount tracks" else "Spotify • Tap to sync",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF1DB954), fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            sourceText.equals("YouTube", ignoreCase = true) -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = com.musicplayer.lytrix.R.drawable.ic_youtube_music),
                        contentDescription = "YouTube",
                        tint = Color(0xFFFF0033),
                        modifier = Modifier.size(13.dp).padding(end = 4.dp)
                    )
                    Text(
                        text = if (songCount > 0) "YouTube • $songCount tracks" else "YouTube • Tap to sync",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFFF4D6D), fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            sourceText.equals("JioSaavn", ignoreCase = true) -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = com.musicplayer.lytrix.R.drawable.ic_jiosaavn),
                        contentDescription = "JioSaavn",
                        tint = Color(0xFF2BC5B4),
                        modifier = Modifier.size(13.dp).padding(end = 4.dp)
                    )
                    Text(
                        text = if (songCount > 0) "JioSaavn • $songCount tracks" else "JioSaavn • Tap to sync",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF2BC5B4), fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            else -> {
                val label = if (sourceText.equals("Custom", ignoreCase = true)) "Playlist" else sourceText
                Text(text = "$label • ${songCount.coerceAtLeast(0)} tracks", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            DropdownMenuItem(text = { Text(if (isPinned) "Unpin" else "Pin to top", color = MaterialTheme.colorScheme.onSurface) }, onClick = { showMenu = false; onPinClick() })
            if (isCustomPlaylist) {
                DropdownMenuItem(text = { Text("Rename Playlist", color = MaterialTheme.colorScheme.onSurface) }, onClick = { showMenu = false; onRenameClick() })
            }
            DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, onClick = { showMenu = false; onDeleteClick() })
        }
    }
}

@Composable

private fun LytrixArtistCard(name: String, thumbnailUrl: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainer).border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f), RoundedCornerShape(16.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(64.dp).clip(CircleShape).background(Color.DarkGray))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = "Artist", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable

private fun LytrixAlbumCard(title: String, artist: String, thumbnailUrl: String) {
    Column(modifier = Modifier.fillMaxWidth()) { // 🔥 এখানকার ফাঁকা clickable মুছে দেওয়া হয়েছে
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(22.dp))) {
            AsyncImage(model = thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().background(Color.DarkGray))
            Box(modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {

                // 🔥 এখানকার ফাঁকা clickable ও মুছে দেওয়া হয়েছে
                Icon(painter = painterResource(id = android.R.drawable.ic_media_play), contentDescription = null, tint = Color.Black, modifier = Modifier.size(12.dp))
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = title, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(text = artist, style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun LytrixBottomSheetListItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit = {}) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = text, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable

private fun PremiumMenuSquareButton(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier.aspectRatio(1.2f).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = text, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable

private fun YTMusicChip(text: String, isSelected: Boolean, onClick: () -> Unit) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val unselectedBg = Color.White.copy(alpha = 0.08f)
    val unselectedText = Color.White.copy(alpha = 0.92f)
    val unselectedBorder = Color.White.copy(alpha = 0.12f)
    val chipBg by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else unselectedBg,
        animationSpec = tween(250),
        label = "chipBg"
    )
    val chipContentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else unselectedText,
        animationSpec = tween(250),
        label = "chipText"
    )
    val chipBorderColor by animateColorAsState(
        targetValue = if (isSelected) Color.Transparent else unselectedBorder,
        animationSpec = tween(250),
        label = "chipBorder"
    )
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(chipBg)
            .border(1.dp, chipBorderColor, RoundedCornerShape(50))
            .clickable {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            color = chipContentColor,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}
private const val BANNER_EVERY = 3
private class BannerCounter(val scope: String = "main") {
    var count = 0
    var shown = 0
}

private fun LazyListScope.rowWithBanner(counter: BannerCounter, content: @Composable () -> Unit) {
    item { content() }
    counter.count++
    if (counter.count % BANNER_EVERY == 0) {
        counter.shown++
        val slot = "${counter.scope}-${counter.shown}"
        item { BannerAdView(slot = slot) }
    }
}

private fun <T> LazyListScope.itemsWithBanner(list: List<T>, scope: String = "main", content: @Composable (T) -> Unit) {
    list.forEachIndexed { index, value ->
        item { content(value) }
        if ((index + 1) % BANNER_EVERY == 0) {
            val slot = "$scope-${(index + 1) / BANNER_EVERY}"
            item { BannerAdView(slot = slot) }
        }
    }
}

@Composable

private fun HorizontalSongSection(title: String, songs: List<JioSaavnSong>, viewModel: PlayerViewModel, onBgColor: Color, onMoreClick: (JioSaavnSong) -> Unit) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(text = title, color = onBgColor, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(songs) { song ->
                val curOnlineSong = viewModel.currentSong.collectAsState().value
                val isCurOnlinePlaying = viewModel.isPlaying.collectAsState().value
                val isThisOnlineActive = curOnlineSong != null && (curOnlineSong.id.toString() == song.id || curOnlineSong.originalId == song.id || (curOnlineSong.title.equals(song.name, ignoreCase = true) && curOnlineSong.artist.equals(song.artist, ignoreCase = true)))
                Column(modifier = Modifier.animateItem().width(135.dp)) {
                    Box(modifier = Modifier.size(135.dp).clip(RoundedCornerShape(12.dp)).bouncePress().clickable { viewModel.playOnlineSongWithAd(context, song, songs) }) {
                        AsyncImage(model = coil.request.ImageRequest.Builder(LocalContext.current).data(song.imageUrl).crossfade(true).build(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().background(onBgColor.copy(alpha = 0.1f)))
                        if (isThisOnlineActive) {
                            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                                LytrixEqualizerWaves(isPlaying = isCurOnlinePlaying, barColor = Color.White, maxHeight = 24.dp, barWidth = 4.dp, spacing = 3.dp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = song.name, color = onBgColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(text = song.artist, color = Color.Gray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { onMoreClick(song) }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

@Composable

private fun HorizontalYtMusicSection(
    title: String,
    tracks: List<PlayerViewModel.YtMusicTrack>,
    viewModel: PlayerViewModel,
    onBgColor: Color,
    subtitle: String? = null,
    iconUrl: String? = null,
    artistId: String? = null,
    onNavigateToArtist: (String) -> Unit = {},
    onMoreClick: (PlayerViewModel.YtMusicTrack) -> Unit
) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp).clickable(enabled = artistId != null) { artistId?.let { onNavigateToArtist(it) } }) {
            if (iconUrl != null) {
                coil.compose.AsyncImage(
                    model = iconUrl,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp).clip(androidx.compose.foundation.shape.CircleShape),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(16.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                if (subtitle != null) {
                    Text(
                        text = subtitle.uppercase(),
                        color = onBgColor.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
                Text(text = title, color = onBgColor, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            }
            if (artistId != null) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    tint = onBgColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(tracks, key = { it.id }) { track ->

                // 🔥 ম্যাজিক ফিক্স: Album • 2023 বা Podcast থাকলেও ধরবে!
                val isEpisode = track.itemType == "Episode"
                val isAlbum = track.itemType == "Album" || (!isEpisode && track.artist.contains("Album", ignoreCase = true))
                val isPodcast = track.itemType == "Podcast" || (!isEpisode && (track.id.startsWith("MPSP") || track.artist.startsWith("Podcast")))
                val isPlaylist = track.itemType == "Playlist" || (!isEpisode && !isPodcast && !isAlbum && (track.artist.contains("Playlist", ignoreCase = true) || track.artist.contains("Station", ignoreCase = true)))
                val isArtist = track.itemType == "Artist" || track.itemType == "Profile" || (!isEpisode && !isPodcast && track.artist.contains("Artist", ignoreCase = true))
                val isPlaylistOrAlbum = !isEpisode && (isAlbum || isPodcast || isPlaylist || isArtist)
                val curPlayingSong = viewModel.currentSong.collectAsState().value
                val isCurPlaying = viewModel.isPlaying.collectAsState().value
                val isThisTrackActive = !isPlaylistOrAlbum && curPlayingSong != null && (
                    (track.id.isNotBlank() && (
                        curPlayingSong.originalId == track.id ||
                        curPlayingSong.id.toString() == track.id ||
                        curPlayingSong.uriString.contains(track.id)
                    )) ||
                    (curPlayingSong.title.equals(track.title, ignoreCase = true) && curPlayingSong.artist.equals(track.artist, ignoreCase = true))
                )
                Column(modifier = Modifier.width(135.dp)) {
                    Box(modifier = Modifier.size(135.dp).clip(RoundedCornerShape(12.dp)).bouncePress().pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                if (isEpisode) {
                                    val cleanArtist = viewModel.cleanArtistDisplayName(track.artist)
                                    val lytrixSong = com.musicplayer.lytrix.LytrixSongItem(videoId = track.id, title = track.title, artist = cleanArtist, duration = "", thumbnailUrl = track.thumbnailUrl)
                                    viewModel.playLytrixSongWithAd(lytrixSong, context)
                                } else if (isPlaylistOrAlbum) {
                                    if (isArtist) onNavigateToArtist(track.id)
                                    else {
                                        globalActiveYtCover = track.thumbnailUrl ?: ""
                                        val type = if (isAlbum) "Album" else if (isPodcast) "Podcast" else "Playlist"
                                        viewModel.loadYtPlaylistDetails(track.id, type, track.title)
                                    }
                                } else {
                                    val songsOnly = tracks.filter { t -> 
                                        t.itemType != "Playlist" && t.itemType != "Album" && t.itemType != "Podcast" && t.itemType != "Artist" &&
                                        !t.artist.contains("Playlist", ignoreCase = true) && 
                                        !t.artist.contains("Album", ignoreCase = true) && 
                                        !t.artist.contains("Station", ignoreCase = true) &&
                                        !t.artist.startsWith("Podcast") &&
                                        !t.artist.contains("Artist", ignoreCase = true)
                                    }
                                    val safeIndex = songsOnly.indexOfFirst { it.id == track.id }.takeIf { it >= 0 } ?: 0
                                    viewModel.playLytrixPlaylistWithAd(songsOnly.ifEmpty { listOf(track) }, safeIndex, context)
                                }
                            },
                            onLongPress = {
                                if (isPlaylistOrAlbum) {
                                    onMoreClick(track)
                                }
                            }
                        )
                    }) {
                        AsyncImage(
                            model = coil.request.ImageRequest.Builder(LocalContext.current).data(track.thumbnailUrl).crossfade(true).build(),
                            contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().blur(16.dp).background(Color.Black.copy(alpha = 0.4f))
                        )
                        AsyncImage(
                            model = coil.request.ImageRequest.Builder(LocalContext.current).data(track.thumbnailUrl).crossfade(true).build(),
                            contentDescription = null, contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (isThisTrackActive) {
                            Box(
                                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
                                contentAlignment = Alignment.Center
                            ) {
                                LytrixEqualizerWaves(isPlaying = isCurPlaying, barColor = Color.White, maxHeight = 24.dp, barWidth = 4.dp, spacing = 3.dp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = track.title, color = onBgColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(text = track.artist, color = Color.Gray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (!isPlaylistOrAlbum) {
                            IconButton(onClick = { onMoreClick(track) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable

private fun LytrixQuickPicksSection(title: String, tracks: List<PlayerViewModel.YtMusicTrack>, viewModel: PlayerViewModel, context: android.content.Context, onMoreClick: (PlayerViewModel.YtMusicTrack) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(text = title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(tracks, key = { it.id }) { track ->
                Box(
                    modifier = Modifier.animateItem().width(300.dp).height(200.dp).clip(RoundedCornerShape(16.dp)).clickable {
                        viewModel.playLytrixPlaylistWithAd(tracks, tracks.indexOf(track).takeIf { it >= 0 } ?: 0, context)
                    }
                ) {
                    AsyncImage(model = track.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f)), startY = 100f)))
                    IconButton(onClick = { onMoreClick(track) }, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White)
                    }
                    Column(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                        Text(text = track.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = track.artist, color = Color.LightGray, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable

private fun MusicVideoSection(title: String, tracks: List<PlayerViewModel.YtMusicTrack>, viewModel: PlayerViewModel, context: android.content.Context) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(text = title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(tracks) { track ->
                Column(modifier = Modifier.width(260.dp).clickable {
                    val lytrixSong = com.musicplayer.lytrix.LytrixSongItem(videoId = track.id, title = track.title, artist = track.artist, duration = "", thumbnailUrl = track.thumbnailUrl)
                    viewModel.playLytrixSongWithAd(lytrixSong, context)
                }) {
                    Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f/9f).clip(RoundedCornerShape(12.dp))) {
                        AsyncImage(model = track.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        Box(modifier = Modifier.align(Alignment.Center).size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = track.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(text = track.artist, color = Color.Gray, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable

private fun HorizontalPodcastSection(title: String, episodes: List<com.musicplayer.lytrix.PodcastEpisode>, viewModel: PlayerViewModel, onBgColor: Color, context: Context, isRadio: Boolean = false, isAudiobook: Boolean = false) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(text = title, color = onBgColor, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(episodes) { episode ->
                var isDownloadedLocally by remember { mutableStateOf(false) }
                val file = remember(episode.id) { File(context.getExternalFilesDir(Environment.DIRECTORY_PODCASTS), "${episode.id}.mp3") }
                val abFile = remember(episode.id) { File(context.getExternalFilesDir(Environment.DIRECTORY_PODCASTS), "AB_${episode.id}.mp3") }
                LaunchedEffect(episode.id) { isDownloadedLocally = file.exists() || abFile.exists() }
                val prefKey = if(isAudiobook) "online_liked_audiobooks" else "online_liked_podcasts"
                val prefs = context.getSharedPreferences(prefKey, Context.MODE_PRIVATE)
                var isLiked by remember(episode.id) { mutableStateOf(prefs.getStringSet("liked_ids", mutableSetOf())?.contains(episode.id) == true) }
                Column(modifier = Modifier.width(135.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = Modifier.size(135.dp).clip(RoundedCornerShape(12.dp)).bouncePress().clickable {
                        if (isAudiobook) viewModel.playAudiobookWithAd(context, episode, episodes)
                        else viewModel.playPodcastWithAd(context, episode, episodes, isRadio)
                    }) {
                        AsyncImage(model = coil.request.ImageRequest.Builder(LocalContext.current).data(episode.image).crossfade(true).build(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().background(onBgColor.copy(alpha = 0.1f)))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = episode.title_original ?: "Unknown", color = onBgColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!isRadio) {
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = "Like", tint = if(isLiked) Color.Red else Color.Gray, modifier = Modifier.size(20.dp).clickable { if (isAudiobook) toggleAudiobookFavorite(context, episode) else togglePodcastFavorite(context, episode); isLiked = !isLiked })
                            Icon(Icons.Default.Download, contentDescription = "Download", tint = Color.Gray, modifier = Modifier.size(20.dp).clickable { if(!isDownloadedLocally) { if (isAudiobook) downloadAudiobookSecurely(context, episode) else downloadPodcastSecurely(context, episode); isDownloadedLocally = true } })
                            Icon(Icons.Default.PlaylistAdd, contentDescription = "Playlist", tint = Color.Gray, modifier = Modifier.size(20.dp).clickable { Toast.makeText(context, "Feature coming soon", Toast.LENGTH_SHORT).show() })
                        }
                    }
                }
            }
        }
    }
}

@Composable

private fun HorizontalAudiobookSection(title: String, episodes: List<com.musicplayer.lytrix.PodcastEpisode>, viewModel: PlayerViewModel, onBgColor: Color, context: Context) {
    HorizontalPodcastSection(title, episodes, viewModel, onBgColor, context, isRadio = false, isAudiobook = true)
}

@Composable

private fun HorizontalRadioSection(title: String, stations: List<com.musicplayer.lytrix.PodcastEpisode>, viewModel: PlayerViewModel, onBgColor: Color, context: Context) {
    HorizontalPodcastSection(title, stations, viewModel, onBgColor, context, isRadio = true, isAudiobook = false)
}

@Composable

private fun LottieErrorStateScreen(state: PlayerViewModel.OnlineUiState.Error, onRetry: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = state.message, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRetry, shape = RoundedCornerShape(24.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.White)) { Text("Retry Connection", color = Color.Black, fontWeight = FontWeight.ExtraBold) }
    }
}

@Composable

private fun LytrixMediaDetailHero(title: String, songCount: Int, thumbnailUrl: String, onPlay: () -> Unit, onShuffle: () -> Unit) {
    val totalMins = songCount * 4
    val hrs = totalMins / 60
    val mins = totalMins % 60
    val timeString = if (hrs > 0) "$hrs hr $mins min" else "$mins min"
    Box(modifier = Modifier.fillMaxWidth().height(420.dp).background(Color(0xFF030303))) {
        if (thumbnailUrl.isNotEmpty()) { AsyncImage(model = coil.request.ImageRequest.Builder(LocalContext.current).data(thumbnailUrl).crossfade(true).build(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.4f), 0.2f to Color.Transparent, 0.5f to Color.Transparent, 0.8f to Color(0xFF030303).copy(alpha = 0.8f), 1f to Color(0xFF030303))))
        Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = title, color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = "$songCount songs  •  $timeString", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onShuffle, modifier = Modifier.size(52.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f))) { Icon(Icons.Default.Shuffle, contentDescription = "Shuffle", tint = Color.White, modifier = Modifier.size(22.dp)) }
                Spacer(modifier = Modifier.width(16.dp))
                Button(onClick = onPlay, shape = RoundedCornerShape(50), colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black), contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp), modifier = Modifier.height(52.dp)) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play", modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Play", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(16.dp))
                IconButton(onClick = { }, modifier = Modifier.size(52.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f))) { Icon(Icons.Default.List, contentDescription = "List", tint = Color.White, modifier = Modifier.size(22.dp)) }
            }
        }
    }
}

@Composable

private fun LytrixSongListItem(title: String, artist: String, duration: String, thumbnailUrl: String, isActive: Boolean = false, isPlaying: Boolean = false, onClick: () -> Unit, onMoreClick: () -> Unit) {
    val cornerRadius = if (isActive) 36.dp else 24.dp
    val thumbCorner = if (isActive) 26.dp else 10.dp
    val cardBgColor = if (isActive) Color(0xFF381E72) else Color.Transparent
    val titleColor = if (isActive) Color(0xFFEADDFF) else Color.White
    val artistColor = if (isActive) Color(0xFFEADDFF).copy(alpha = 0.75f) else Color.White.copy(alpha = 0.6f)
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(cornerRadius)).background(cardBgColor).clickable(onClick = onClick).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(52.dp).clip(RoundedCornerShape(thumbCorner)), contentAlignment = Alignment.Center) {
            AsyncImage(model = thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (isActive) { Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) { Icon(imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp)) } }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = titleColor, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = artist, color = artistColor, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {

            // 🔥 ম্যাজিক ফিক্স: ডিউরেশন ফাঁকা থাকলে এই ধূসর বক্সটা আর দেখাবে না!
            if (duration.isNotBlank()) {
                Box(modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = if (isActive) 0.2f else 0.1f)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text(text = duration, color = titleColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(4.dp))
            }
            IconButton(onClick = onMoreClick, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.MoreVert, contentDescription = "More", tint = titleColor) }
        }
    }
}
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

@Composable

private fun PremiumOnlineSongItem(
    song: JioSaavnSong,
    onBgColor: Color,
    cardBgColor: Color,
    viewModel: PlayerViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onMoreClick: () -> Unit
) {
    val context = LocalContext.current
    var isDownloadedLocally by remember(song.id) { mutableStateOf(false) }
    val safeName = song.name.replace(Regex("[^a-zA-Z0-9 ]"), "_").take(50).trim()
    val musicDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
    val file = remember(song.id) { File(musicDir, "$safeName.mp3") }
    val coverFile = remember(song.id) { File(musicDir, "${safeName}_cover.jpg") }

    // মেমোরিতে কভার ছবি থাকলে মেমোরি থেকে, না থাকলে ইন্টারনেট থেকে!
    val coverData = remember(song.id) {
        if (coverFile.exists() && coverFile.length() > 0) coverFile else song.imageUrl
    }
    LaunchedEffect(song.id) { isDownloadedLocally = file.exists() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(cardBgColor)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .bouncePress()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val curOnlineSong = viewModel.currentSong.collectAsState().value
        val isCurOnlinePlaying = viewModel.isPlaying.collectAsState().value
        val isThisOnlineActive = curOnlineSong != null && (curOnlineSong.id.toString() == song.id || curOnlineSong.originalId == song.id || (curOnlineSong.title.equals(song.name, ignoreCase = true) && curOnlineSong.artist.equals(song.artist, ignoreCase = true)))
        Box(modifier = Modifier.size(56.dp)) {
            AsyncImage(
                model = coil.request.ImageRequest.Builder(LocalContext.current)
                    .data(coverData)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.DarkGray)
            )
            if (isThisOnlineActive) {
                Box(
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    LytrixEqualizerWaves(isPlaying = isCurOnlinePlaying, barColor = Color.White, maxHeight = 16.dp, barWidth = 3.dp, spacing = 2.dp)
                }
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.name,
                color = onBgColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (song.id.startsWith("spotify_") || song.imageUrl.contains("scdn.co", ignoreCase = true)) {
                    Icon(
                        painter = painterResource(id = com.musicplayer.lytrix.R.drawable.ic_spotify),
                        contentDescription = "Spotify",
                        tint = Color(0xFF1DB954),
                        modifier = Modifier.size(12.dp).padding(end = 3.dp)
                    )
                }
                Text(
                    text = song.artist,
                    color = Color.Gray,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (isDownloadedLocally) {
            Icon(
                Icons.Default.DownloadDone,
                contentDescription = "Downloaded",
                tint = Color(0xFF00E676),
                modifier = Modifier.size(20.dp).padding(end = 4.dp)
            )
        }
        IconButton(onClick = onMoreClick) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "More",
                tint = Color.Gray
            )
        }
    }
}

@Composable

private fun PodcastEpisodeItem(episode: com.musicplayer.lytrix.PodcastEpisode, isLiked: Boolean, isDownloadedView: Boolean, onBgColor: Color, cardBgColor: Color, onClick: () -> Unit, onLikeClick: () -> Unit, onDownloadClick: () -> Unit) {
    val context = LocalContext.current
    var isDownloadedLocally by remember(episode.id) { mutableStateOf(false) }
    val file = remember(episode.id) { File(context.getExternalFilesDir(Environment.DIRECTORY_PODCASTS), "${episode.id}.mp3") }
    val abFile = remember(episode.id) { File(context.getExternalFilesDir(Environment.DIRECTORY_PODCASTS), "AB_${episode.id}.mp3") }
    LaunchedEffect(episode.id) { isDownloadedLocally = file.exists() || abFile.exists() }
    var showMenu by remember { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(cardBgColor).border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f), RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = coil.request.ImageRequest.Builder(LocalContext.current).data(episode.image).crossfade(true).build(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(Color.DarkGray))
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = episode.title_original ?: "Unknown", color = onBgColor, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = episode.podcast?.publisher_original ?: "Unknown", color = Color.Gray, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box {
            IconButton(onClick = { showMenu = true }) { Icon(imageVector = Icons.Default.MoreVert, contentDescription = "More", tint = Color.Gray) }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, modifier = Modifier.background(cardBgColor)) {
                if (isDownloadedLocally) {
                    DropdownMenuItem(text = { Text("Remove from device", color = Color.Red.copy(alpha = 0.9f)) }, onClick = { if (file.exists()) file.delete(); if (abFile.exists()) abFile.delete(); onDownloadClick(); isDownloadedLocally = false; Toast.makeText(context, "Removed", Toast.LENGTH_SHORT).show(); showMenu = false })
                } else {
                    DropdownMenuItem(text = { Text("Download", color = Color.White) }, onClick = { if (episode.audio?.contains("audiobook", ignoreCase = true) == true) downloadAudiobookSecurely(context, episode) else downloadPodcastSecurely(context, episode); isDownloadedLocally = true; showMenu = false })
                }
                DropdownMenuItem(text = { Text(if (isLiked) "Remove from Liked" else "Like", color = Color.White) }, onClick = { onLikeClick(); showMenu = false })
            }
        }
    }
}

private fun downloadSongSecurely(context: Context, song: JioSaavnSong) {
    requestDownloadWithAd(context, song.name, isPlaylist = false) {
        val isYtSong = !song.imageUrl.contains("saavn", ignoreCase = true)

        // আগের মতোই: আইডি নষ্ট থাকলে ছবির লিংক থেকে আসল ইউটিউব আইডি বের করবে
        val realId = if (isYtSong && song.imageUrl.contains("ytimg.com/vi/")) {
            song.imageUrl.substringAfter("vi/").substringBefore("/")
        } else {
            song.id
        }
        val useDirectUrl = !isYtSong && song.downloadUrl.isNotBlank()
        com.musicplayer.lytrix.playback.OkHttpDownloader.startDownload(
            context = context,
            songId = realId,
            title = song.name,
            artist = song.artist,
            imageUrl = song.imageUrl,
            providedUrl = if (useDirectUrl) song.downloadUrl else ""
        )
        Toast.makeText(context, "Download Started 🚀", Toast.LENGTH_SHORT).show()
    }
}

private fun downloadPodcastSecurely(context: Context, episode: com.musicplayer.lytrix.PodcastEpisode) {
    val title = episode.title_original ?: "Podcast_Episode"
    val id = episode.id ?: System.currentTimeMillis().toString()
    if (episode.audio.isNullOrBlank()) {
        val fakeSong = JioSaavnSong(id, title, episode.podcast?.publisher_original ?: "Podcast", episode.image ?: "", "")
        downloadSongSecurely(context, fakeSong)
        return
    }

    // 🔥 OkHttpDownloader দিয়ে ডাউনলোড হবে এবং 'Others' ট্যাবে যাবে
    com.musicplayer.lytrix.playback.OkHttpDownloader.startDownload(
        context = context,
        songId = id,
        title = title,
        artist = episode.podcast?.publisher_original ?: "Podcast",
        imageUrl = episode.image ?: "",
        providedUrl = episode.audio,
        source = com.musicplayer.lytrix.playback.DownloadSource.OTHERS
    )

    // ডাটাবেজে এন্ট্রি রাখা
    CoroutineScope(Dispatchers.IO).launch {
        val db = com.musicplayer.lytrix.db.AppDatabase.getDatabase(context)
        val safeName = title.replace(Regex("[^a-zA-Z0-9 ]"), "_").take(50).trim()
        val path = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)?.absolutePath + "/$safeName.mp3"
        val entity = com.musicplayer.lytrix.db.PodcastEntity(
            id = id, title = title, publisher = episode.podcast?.publisher_original ?: "Unknown",
            audioUrl = episode.audio, image = episode.image ?: "", durationMs = (episode.audio_length_sec ?: 0) * 1000L,
            downloadPath = path, language = "Unknown", isDownloaded = true, addedTime = System.currentTimeMillis()
        )
        db.podcastDao().insertPodcast(entity)
    }
    Toast.makeText(context, "Podcast Download Started 🎙️", Toast.LENGTH_SHORT).show()
}

private fun togglePodcastFavorite(context: Context, episode: com.musicplayer.lytrix.PodcastEpisode) {
    val prefs = context.getSharedPreferences("online_liked_podcasts", Context.MODE_PRIVATE)
    val oldSet = prefs.getStringSet("liked_ids", emptySet()) ?: emptySet()
    val newSet = java.util.HashSet<String>(oldSet)
    val episodeId = episode.id ?: return
    if (newSet.contains(episodeId)) {
        newSet.remove(episodeId)
        prefs.edit()
            .remove("podcast_data_$episodeId")
            .remove("song_data_$episodeId")
            .putStringSet("liked_ids", newSet)
            .apply()
        Toast.makeText(context, "Removed from Liked Podcasts", Toast.LENGTH_SHORT).show()
        try { /* performSilentSync(context) */ } catch(e: Exception){}
    } else {
        newSet.add(episodeId)
        val jsonObj = org.json.JSONObject()
        jsonObj.put("id", episodeId)
        jsonObj.put("title", episode.title_original ?: "")
        jsonObj.put("title_original", episode.title_original ?: "")
        jsonObj.put("name", episode.title_original ?: "")
        jsonObj.put("audio", episode.audio ?: "")
        jsonObj.put("url", episode.audio ?: "")
        jsonObj.put("downloadUrl", episode.audio ?: "")
        jsonObj.put("image", episode.image ?: "")
        jsonObj.put("imageUrl", episode.image ?: "")
        jsonObj.put("audio_length_sec", episode.audio_length_sec ?: 0)
        jsonObj.put("publisher", episode.podcast?.publisher_original ?: "")
        jsonObj.put("artist", episode.podcast?.publisher_original ?: "")
        episode.podcast?.channelId?.let { jsonObj.put("channelId", it) }
        prefs.edit()
            .putString("podcast_data_$episodeId", jsonObj.toString())
            .putString("song_data_$episodeId", jsonObj.toString())
            .putStringSet("liked_ids", newSet)
            .apply()
        Toast.makeText(context, "Saved to Liked Podcasts", Toast.LENGTH_SHORT).show()
        try { performSilentSync(context) } catch(e: Exception){}
    }
}

private fun toggleAudiobookFavorite(context: Context, episode: com.musicplayer.lytrix.PodcastEpisode) {
    val prefs = context.getSharedPreferences("online_liked_audiobooks", Context.MODE_PRIVATE)
    val oldSet = prefs.getStringSet("liked_ids", emptySet()) ?: emptySet()
    val newSet = java.util.HashSet<String>(oldSet)
    val episodeId = episode.id ?: return
    if (newSet.contains(episodeId)) {
        newSet.remove(episodeId)
        prefs.edit()
            .remove("audiobook_data_$episodeId")
            .remove("song_data_$episodeId")
            .putStringSet("liked_ids", newSet)
            .apply()
        Toast.makeText(context, "Removed from Liked Audiobooks", Toast.LENGTH_SHORT).show()
        try { performSilentSync(context) } catch(e: Exception){}
    } else {
        newSet.add(episodeId)
        val jsonObj = org.json.JSONObject()
        jsonObj.put("id", episodeId)
        jsonObj.put("title", episode.title_original ?: "")
        jsonObj.put("title_original", episode.title_original ?: "")
        jsonObj.put("name", episode.title_original ?: "")
        jsonObj.put("audio", episode.audio ?: "")
        jsonObj.put("url", episode.audio ?: "")
        jsonObj.put("downloadUrl", episode.audio ?: "")
        jsonObj.put("image", episode.image ?: "")
        jsonObj.put("imageUrl", episode.image ?: "")
        jsonObj.put("audio_length_sec", episode.audio_length_sec ?: 0)
        jsonObj.put("publisher", episode.podcast?.publisher_original ?: "")
        jsonObj.put("artist", episode.podcast?.publisher_original ?: "")
        episode.podcast?.channelId?.let { jsonObj.put("channelId", it) }
        prefs.edit()
            .putString("audiobook_data_$episodeId", jsonObj.toString())
            .putString("song_data_$episodeId", jsonObj.toString())
            .putStringSet("liked_ids", newSet)
            .apply()
        Toast.makeText(context, "Saved to Liked Audiobooks", Toast.LENGTH_SHORT).show()
        try { performSilentSync(context) } catch(e: Exception){}
    }
}

private fun downloadAudiobookSecurely(context: Context, episode: com.musicplayer.lytrix.PodcastEpisode) {
    if (episode.audio.isNullOrBlank()) return
    val title = episode.title_original ?: "Audiobook"
    val id = episode.id ?: System.currentTimeMillis().toString()

    // 🔥 OkHttpDownloader দিয়ে ডাউনলোড হবে এবং 'Others' ট্যাবে যাবে
    com.musicplayer.lytrix.playback.OkHttpDownloader.startDownload(
        context = context,
        songId = id,
        title = title,
        artist = episode.podcast?.publisher_original ?: "Audiobook",
        imageUrl = episode.image ?: "",
        providedUrl = episode.audio,
        source = com.musicplayer.lytrix.playback.DownloadSource.OTHERS
    )
    Toast.makeText(context, "Audiobook Download Started 📖", Toast.LENGTH_SHORT).show()
}

suspend fun extractRealRadioUrl(playlistUrl: String): String {
    return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            if (playlistUrl.endsWith(".m3u", ignoreCase = true) || playlistUrl.endsWith(".pls", ignoreCase = true)) {
                val connection = java.net.URL(playlistUrl).openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 3000; connection.readTimeout = 3000
                val reader = connection.inputStream.bufferedReader(); val lines = reader.readLines(); reader.close()
                for (line in lines) { val trimmed = line.trim(); if (trimmed.startsWith("http", ignoreCase = true)) return@withContext trimmed }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return@withContext playlistUrl
    }
}
enum class AtmosphereMood(
    val id: String,
    val emoji: String,
    val title: String,
    val subtitle: String,
    val defaultSongQuery: String,
    val defaultPlaylistQuery: String,
    val podcastQuery: String,
    val audiobookQuery: String
) {
    CLEAR(
        id = "auto",
        emoji = "☀️",
        title = "Auto Atmosphere",
        subtitle = "Harmonious soundscapes tuned to current time",
        defaultSongQuery = "Popular Trending Hits",
        defaultPlaylistQuery = "Top Hits Playlist",
        podcastQuery = "Daily Stories",
        audiobookQuery = "Adventure Novel"
    ),
    RAIN(
        id = "rain",
        emoji = "🌧️",
        title = "Rainy Melodies",
        subtitle = "Gentle rain & cozy acoustic melodies",
        defaultSongQuery = "Rainy Day Lofi",
        defaultPlaylistQuery = "Rain Lofi Beats",
        podcastQuery = "Rain Sounds Sleep",
        audiobookQuery = "Mystery Thriller"
    ),
    STORM(
        id = "storm",
        emoji = "⛈️",
        title = "Stormy Beats",
        subtitle = "Heavy rain & deep atmospheric vibes",
        defaultSongQuery = "Dark Moody Beats",
        defaultPlaylistQuery = "Cinematic Ambient Soundscapes",
        podcastQuery = "Horror Stories",
        audiobookQuery = "Dark Mystery"
    ),
    SNOW(
        id = "snow",
        emoji = "❄️",
        title = "Winter Chill",
        subtitle = "Crisp snowfall & cozy winter acoustic",
        defaultSongQuery = "Cozy Winter Acoustic",
        defaultPlaylistQuery = "Winter Coffeehouse Beats",
        podcastQuery = "Calm Meditation",
        audiobookQuery = "Classic Literature"
    ),
    CHILL(
        id = "chill",
        emoji = "☕",
        title = "Chill & Lo-Fi",
        subtitle = "Relax, study, and unwind with soothing sounds",
        defaultSongQuery = "Chill Lofi Study Beats",
        defaultPlaylistQuery = "Lofi Instrumental Chill",
        podcastQuery = "Life Stories",
        audiobookQuery = "Personal Growth"
    );
    companion object {
        fun fromId(id: String): AtmosphereMood = values().find { it.id == id } ?: CLEAR
    }
}
enum class DayPhase {
    SUNRISE,
    DAY,
    SUNSET,
    NIGHT,
    RAIN,
    STORM,
    SNOW,
    CHILL
}

data class MoodDisplayInfo(
    val title: String,
    val subtitle: String,
    val badge: String,
    val emoji: String,
    val phase: DayPhase,
    val songQuery: String,
    val playlistQuery: String,
    val podcastQuery: String,
    val audiobookQuery: String
)

private fun atmosphereCacheKey(mood: AtmosphereMood, info: MoodDisplayInfo): String =
    if (mood == AtmosphereMood.CLEAR) "auto_${info.phase.name}_${info.title}" else mood.id

fun getEffectiveMoodInfo(
    mood: AtmosphereMood,
    hourOfDay: Int = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
): MoodDisplayInfo {
    return when (mood) {
        AtmosphereMood.CLEAR -> {
            when (hourOfDay) {
                in 5..7 -> MoodDisplayInfo(
                    title = "Golden Sunrise",
                    subtitle = "Morning dawn & uplifting acoustic melodies",
                    badge = "🌅 Morning Dawn",
                    emoji = "🌅",
                    phase = DayPhase.SUNRISE,
                    songQuery = "Morning Acoustic Melodies",
                    playlistQuery = "Morning Acoustic Chill",
                    podcastQuery = "Morning Routine",
                    audiobookQuery = "Inspirational Journey"
                )
                in 8..16 -> MoodDisplayInfo(
                    title = "Daylight Energy",
                    subtitle = "Bright sunny skies & energetic vibrant tunes",
                    badge = "☀️ Daylight Focus",
                    emoji = "☀️",
                    phase = DayPhase.DAY,
                    songQuery = "Daytime Upbeat Pop Hits",
                    playlistQuery = "Feel Good Pop Hits",
                    podcastQuery = "Comedy and Fun",
                    audiobookQuery = "Adventure Novel"
                )
                in 17..18 -> MoodDisplayInfo(
                    title = "Golden Sunset",
                    subtitle = "Twilight breeze & relaxing sunset chill",
                    badge = "🌇 Golden Twilight",
                    emoji = "🌇",
                    phase = DayPhase.SUNSET,
                    songQuery = "Sunset Chill Vibes",
                    playlistQuery = "Golden Hour Chill",
                    podcastQuery = "Evening Reflection",
                    audiobookQuery = "Relaxing Stories"
                )
                in 19..23 -> MoodDisplayInfo(
                    title = "Night Serenade",
                    subtitle = "Starry evening breeze & relaxing night melodies",
                    badge = "🌙 Night Calm",
                    emoji = "🌙",
                    phase = DayPhase.NIGHT,
                    songQuery = "Night Chill Lofi Vibes",
                    playlistQuery = "Evening Acoustic Chill",
                    podcastQuery = "Evening Reflection",
                    audiobookQuery = "Calm Philosophy"
                )
                else -> MoodDisplayInfo(
                    title = "Midnight Serenade",
                    subtitle = "Starry night calm & soothing midnight melodies",
                    badge = "🌙 Midnight Calm",
                    emoji = "🌙",
                    phase = DayPhase.NIGHT,
                    songQuery = "Late Night Lofi Chill",
                    playlistQuery = "Peaceful Sleep Piano",
                    podcastQuery = "Deep Sleep Stories",
                    audiobookQuery = "Calm Philosophy"
                )
            }
        }
        AtmosphereMood.RAIN -> MoodDisplayInfo(
            title = mood.title,
            subtitle = mood.subtitle,
            badge = "🌧️ Rainy Mood",
            emoji = mood.emoji,
            phase = DayPhase.RAIN,
            songQuery = mood.defaultSongQuery,
            playlistQuery = mood.defaultPlaylistQuery,
            podcastQuery = mood.podcastQuery,
            audiobookQuery = mood.audiobookQuery
        )
        AtmosphereMood.STORM -> MoodDisplayInfo(
            title = mood.title,
            subtitle = mood.subtitle,
            badge = "⛈️ Stormy Beats",
            emoji = mood.emoji,
            phase = DayPhase.STORM,
            songQuery = mood.defaultSongQuery,
            playlistQuery = mood.defaultPlaylistQuery,
            podcastQuery = mood.podcastQuery,
            audiobookQuery = mood.audiobookQuery
        )
        AtmosphereMood.SNOW -> MoodDisplayInfo(
            title = mood.title,
            subtitle = mood.subtitle,
            badge = "❄️ Winter Breeze",
            emoji = mood.emoji,
            phase = DayPhase.SNOW,
            songQuery = mood.defaultSongQuery,
            playlistQuery = mood.defaultPlaylistQuery,
            podcastQuery = mood.podcastQuery,
            audiobookQuery = mood.audiobookQuery
        )
        AtmosphereMood.CHILL -> MoodDisplayInfo(
            title = mood.title,
            subtitle = mood.subtitle,
            badge = "☕ Cozy Lo-Fi",
            emoji = mood.emoji,
            phase = DayPhase.CHILL,
            songQuery = mood.defaultSongQuery,
            playlistQuery = mood.defaultPlaylistQuery,
            podcastQuery = mood.podcastQuery,
            audiobookQuery = mood.audiobookQuery
        )
    }
}

@Composable
private fun AtmosphericMoodCard(
    activeMood: AtmosphereMood,
    isLoading: Boolean = false,
    onMoodSelected: (AtmosphereMood) -> Unit = {}
) {
    val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val effectiveInfo = getEffectiveMoodInfo(activeMood, currentHour)
    val currentDateTime = remember {
        val now = java.util.Date()
        val dFmt = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.ENGLISH)
        val tFmt = java.text.SimpleDateFormat("h:mm a", java.util.Locale.ENGLISH)
        Pair(dFmt.format(now), tFmt.format(now))
    }
    val infiniteTransition = rememberInfiniteTransition(label = "cardPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .border(
                1.2.dp,
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.40f),
                        Color.White.copy(alpha = 0.15f),
                        Color.Transparent,
                        Color.White.copy(alpha = 0.22f)
                    )
                ),
                RoundedCornerShape(26.dp)
            ),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(234.dp)
                .clip(RoundedCornerShape(26.dp))
        ) {
            // 1. Dynamic Landscape Artwork + Live Particle Animation Backdrop
            AtmosphericSkyBackground(phase = effectiveInfo.phase)

            // 2. Interactive Overlay
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Row: Dynamic Atmosphere Badge + Live Date & Time
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Frosted Glass Atmosphere Pill
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.16f))
                            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(50))
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = if (isLoading) Color(0xFFFFD54F) else Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isLoading) "Updating Vibes..." else effectiveInfo.badge,
                            color = Color.White.copy(alpha = if (isLoading) pulseAlpha else 1f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Live Date & Time
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = currentDateTime.first,
                            color = Color.White.copy(alpha = 0.90f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = currentDateTime.second,
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                }

                // Middle Row: 3D Animated Orb + Mood Title & Subtitle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 2.dp)
                ) {
                    // 3D Weather/Mood Icon
                    Atmospheric3DIcon(phase = effectiveInfo.phase)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = effectiveInfo.title,
                            color = Color.White,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = effectiveInfo.subtitle,
                            color = Color.White.copy(alpha = 0.76f),
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Bottom Row: Horizontally Scrollable Interactive Effect Pills (Only 5 essential pills!)
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(AtmosphereMood.values()) { moodItem ->
                        val isSelected = activeMood == moodItem
                        val pillBg = if (isSelected) Color.White.copy(alpha = 0.30f) else Color.Black.copy(alpha = 0.22f)
                        val pillBorderColor = if (isSelected) Color.White.copy(alpha = 0.90f) else Color.White.copy(alpha = 0.16f)
                        val pillTextColor = if (isSelected) Color.White else Color.White.copy(alpha = 0.72f)
                        val pillLabel = when (moodItem) {
                            AtmosphereMood.CLEAR -> "Auto"
                            AtmosphereMood.RAIN -> "Rain"
                            AtmosphereMood.STORM -> "Storm"
                            AtmosphereMood.SNOW -> "Snow"
                            AtmosphereMood.CHILL -> "Chill"
                        }
                        val pillEmoji = if (moodItem == AtmosphereMood.CLEAR) {
                            getEffectiveMoodInfo(AtmosphereMood.CLEAR, currentHour).emoji
                        } else {
                            moodItem.emoji
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(pillBg)
                                .border(if (isSelected) 1.5.dp else 1.dp, pillBorderColor, RoundedCornerShape(50))
                                .clickable {
                                    onMoodSelected(moodItem)
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = pillEmoji,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = pillLabel,
                                color = pillTextColor,
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AtmosphericSkyBackground(phase: DayPhase) {
    val bgRes = when (phase) {
        DayPhase.SUNRISE -> com.musicplayer.lytrix.R.drawable.bg_atmosphere_sunrise
        DayPhase.DAY -> com.musicplayer.lytrix.R.drawable.bg_atmosphere_day
        DayPhase.SUNSET -> com.musicplayer.lytrix.R.drawable.bg_atmosphere_sunset
        DayPhase.NIGHT -> com.musicplayer.lytrix.R.drawable.bg_atmosphere_night
        DayPhase.RAIN -> com.musicplayer.lytrix.R.drawable.bg_atmosphere_rain
        DayPhase.STORM -> com.musicplayer.lytrix.R.drawable.bg_atmosphere_storm
        DayPhase.SNOW -> com.musicplayer.lytrix.R.drawable.bg_atmosphere_snow
        DayPhase.CHILL -> com.musicplayer.lytrix.R.drawable.bg_atmosphere_chill
    }
    Box(modifier = Modifier.fillMaxSize()) {
        // 1. High-Definition Scenic Photographic Art Backdrop (Photorealistic, majestic mountain ridges!)
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(id = bgRes),
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // 2. Live 60 FPS Particle Animation & Dynamic Atmosphere Canvas Overlay
        AtmosphericParticleCanvas(phase = phase)
    }
}

@Composable
private fun AtmosphericParticleCanvas(phase: DayPhase) {
    val infiniteTransition = rememberInfiniteTransition(label = "particleAnim")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val rainFall by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 400f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (phase == DayPhase.STORM) 400 else 700, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rainFall"
    )
    val snowFall by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 350f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "snowFall"
    )
    val thunderPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "thunderPhase"
    )
    val thunderFlash = if (thunderPhase in 0.70f..0.73f) {
        0.55f
    } else if (thunderPhase in 0.76f..0.79f) {
        0.80f
    } else {
        0.0f
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // 1. NIGHT: Twinkling Starfield over the sky
        if (phase == DayPhase.NIGHT) {
            val stars = listOf(
                Offset(w * 0.48f, h * 0.12f),
                Offset(w * 0.58f, h * 0.22f),
                Offset(w * 0.65f, h * 0.08f),
                Offset(w * 0.72f, h * 0.26f),
                Offset(w * 0.78f, h * 0.14f),
                Offset(w * 0.86f, h * 0.10f),
                Offset(w * 0.94f, h * 0.20f),
                Offset(w * 0.62f, h * 0.36f)
            )
            stars.forEachIndexed { i, pt ->
                val starAlpha = if (i % 2 == 0) 0.85f * pulse else 0.45f
                drawCircle(color = Color.White.copy(alpha = starAlpha), radius = if (i == 2 || i == 5) 2.2f else 1.3f, center = pt)
            }
        }

        // 2. RAIN / STORM: Slanted falling rain streaks
        if (phase == DayPhase.RAIN || phase == DayPhase.STORM) {
            val dropCount = if (phase == DayPhase.STORM) 36 else 22
            val slant = if (phase == DayPhase.STORM) -11f else -7f
            val dropLen = if (phase == DayPhase.STORM) 28f else 18f
            val strokeW = if (phase == DayPhase.STORM) 2.8f else 2.0f
            val rainAlpha = if (phase == DayPhase.STORM) 0.35f else 0.22f
            for (i in 0 until dropCount) {
                val rx = w * 0.25f + (i * w * 0.75f / dropCount.toFloat())
                val ry = (rainFall * (1f + (i % 5) * 0.15f) + (i * 65f)) % h
                drawLine(
                    color = Color.White.copy(alpha = rainAlpha),
                    start = Offset(rx, ry),
                    end = Offset(rx + slant, ry + dropLen),
                    strokeWidth = strokeW,
                    cap = StrokeCap.Round
                )
            }
        }

        // 3. STORM: Ambient Lightning Flash
        if (phase == DayPhase.STORM && thunderFlash > 0.05f) {
            drawRect(
                color = Color(0xFFE0F7FA).copy(alpha = thunderFlash * 0.45f),
                size = size
            )
        }

        // 4. SNOW: Swaying falling snowflakes with horizontal sine drift
        if (phase == DayPhase.SNOW) {
            val flakeCount = 30
            for (i in 0 until flakeCount) {
                val baseX = w * 0.30f + (i * w * 0.70f / flakeCount.toFloat())
                val sway = kotlin.math.sin((snowFall * 0.03f + i).toDouble()).toFloat() * 12f
                val sy = (snowFall * (0.6f + (i % 4) * 0.15f) + (i * 55f)) % h
                val flakeR = 1.6f + (i % 3) * 0.9f
                val flakeAlpha = 0.40f + (i % 4) * 0.15f
                drawCircle(
                    color = Color.White.copy(alpha = flakeAlpha),
                    radius = flakeR,
                    center = Offset(baseX + sway, sy)
                )
            }
        }

        // 5. CHILL: Warm floating ambient bokeh orbs
        if (phase == DayPhase.CHILL) {
            val orbs = listOf(
                Offset(w * 0.65f, h * 0.25f),
                Offset(w * 0.78f, h * 0.20f),
                Offset(w * 0.88f, h * 0.38f)
            )
            orbs.forEachIndexed { idx, pt ->
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color(0xFFFFB74D).copy(alpha = 0.28f * pulse), Color.Transparent),
                        center = pt,
                        radius = 45f + idx * 15f
                    ),
                    radius = 45f + idx * 15f,
                    center = pt
                )
            }
        }

        // 6. LEFT VIGNETTE GRADIENT: Ensures 100% crisp typography and 3D icon visibility against the artwork!
        val vignetteDark = when (phase) {
            DayPhase.SUNRISE -> Color(0xFF1E1526)
            DayPhase.DAY -> Color(0xFF0D1D30)
            DayPhase.SUNSET -> Color(0xFF1E0A1A)
            DayPhase.NIGHT -> Color(0xFF060914)
            DayPhase.RAIN, DayPhase.STORM -> Color(0xFF080D16)
            DayPhase.SNOW -> Color(0xFF0F1722)
            DayPhase.CHILL -> Color(0xFF181120)
        }
        drawRect(
            brush = Brush.horizontalGradient(
                listOf(
                    vignetteDark.copy(alpha = 0.96f),
                    vignetteDark.copy(alpha = 0.88f),
                    vignetteDark.copy(alpha = 0.48f),
                    Color.Transparent
                ),
                startX = 0f,
                endX = w * 0.65f
            )
        )
    }
}

@Composable
private fun Atmospheric3DIcon(
    phase: DayPhase,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "3d_atmosphere")
    val floatAnim by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "floatAnim"
    )
    val dropAnim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 24f,
        animationSpec = infiniteRepeatable(tween(650, easing = LinearEasing)),
        label = "dropAnim"
    )
    Box(
        modifier = modifier
            .size(76.dp)
            .background(Color(0xFF0B1728).copy(alpha = 0.45f), CircleShape)
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.32f),
                        Color.White.copy(alpha = 0.08f),
                        Color.Transparent
                    )
                ),
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            val w = size.width
            val h = size.height
            val minDim = minOf(w, h)
            val cx = w / 2f
            val cy = h / 2f + floatAnim

            // 1. Ambient Frosted Glass Outer Backdrop Circle
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.04f), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = minDim * 0.48f
                ),
                radius = minDim * 0.48f,
                center = Offset(cx, cy)
            )
            when (phase) {
                DayPhase.SUNSET -> {
                    // Sunset warm radial glow
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color(0xFFFF5722).copy(alpha = 0.65f), Color(0xFFE91E63).copy(alpha = 0.20f), Color.Transparent),
                            center = Offset(cx, cy - minDim * 0.02f),
                            radius = minDim * 0.44f
                        ),
                        radius = minDim * 0.44f,
                        center = Offset(cx, cy - minDim * 0.02f)
                    )
                    // Radiant Golden Sun dipping into the horizon
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color(0xFFFFF59D), Color(0xFFFF9800), Color(0xFFD84315)),
                            center = Offset(cx, cy - minDim * 0.05f),
                            radius = minDim * 0.22f
                        ),
                        radius = minDim * 0.22f,
                        center = Offset(cx, cy - minDim * 0.05f)
                    )
                    // Horizon / Mountain ridge silhouette in front of the sun
                    val sunsetHorizon = Path().apply {
                        moveTo(cx - minDim * 0.44f, cy + minDim * 0.40f)
                        lineTo(cx - minDim * 0.44f, cy + minDim * 0.08f)
                        lineTo(cx - minDim * 0.20f, cy + minDim * 0.02f)
                        lineTo(cx, cy + minDim * 0.06f)
                        lineTo(cx + minDim * 0.22f, cy - minDim * 0.01f)
                        lineTo(cx + minDim * 0.44f, cy + minDim * 0.07f)
                        lineTo(cx + minDim * 0.44f, cy + minDim * 0.40f)
                        close()
                    }
                    drawPath(
                        sunsetHorizon,
                        brush = Brush.verticalGradient(
                            listOf(Color(0xFF3E1F47).copy(alpha = 0.95f), Color(0xFF1B0B20)),
                            startY = cy,
                            endY = cy + minDim * 0.40f
                        )
                    )
                    // Golden twilight water/horizon reflection lines
                    drawLine(
                        color = Color(0xFFFFB74D).copy(alpha = 0.70f),
                        start = Offset(cx - minDim * 0.18f, cy + minDim * 0.13f),
                        end = Offset(cx + minDim * 0.18f, cy + minDim * 0.13f),
                        strokeWidth = 2.2f,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = Color(0xFFFF7043).copy(alpha = 0.45f),
                        start = Offset(cx - minDim * 0.11f, cy + minDim * 0.21f),
                        end = Offset(cx + minDim * 0.11f, cy + minDim * 0.21f),
                        strokeWidth = 1.8f,
                        cap = StrokeCap.Round
                    )
                }
                DayPhase.SUNRISE -> {
                    // Morning glow
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color(0xFFFFD54F).copy(alpha = 0.60f), Color(0xFFFF9800).copy(alpha = 0.18f), Color.Transparent),
                            center = Offset(cx, cy + minDim * 0.02f),
                            radius = minDim * 0.44f
                        ),
                        radius = minDim * 0.44f,
                        center = Offset(cx, cy + minDim * 0.02f)
                    )
                    // Morning sun rising above dawn ridge
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color(0xFFFFF9C4), Color(0xFFFFD54F), Color(0xFFFF9800)),
                            center = Offset(cx, cy - minDim * 0.03f),
                            radius = minDim * 0.20f
                        ),
                        radius = minDim * 0.20f,
                        center = Offset(cx, cy - minDim * 0.03f)
                    )
                    // Soft upward dawn sun rays
                    for (i in -2..2) {
                        val angle = (i * 24f - 90f) * (Math.PI / 180f)
                        val startR = minDim * 0.24f
                        val endR = minDim * 0.35f
                        drawLine(
                            color = Color(0xFFFFD54F).copy(alpha = 0.55f),
                            start = Offset(cx + (startR * Math.cos(angle)).toFloat(), cy - minDim * 0.03f + (startR * Math.sin(angle)).toFloat()),
                            end = Offset(cx + (endR * Math.cos(angle)).toFloat(), cy - minDim * 0.03f + (endR * Math.sin(angle)).toFloat()),
                            strokeWidth = 2f,
                            cap = StrokeCap.Round
                        )
                    }
                    // Dawn hill silhouette
                    val sunriseRidge = Path().apply {
                        moveTo(cx - minDim * 0.44f, cy + minDim * 0.40f)
                        lineTo(cx - minDim * 0.44f, cy + minDim * 0.10f)
                        lineTo(cx - minDim * 0.14f, cy + minDim * 0.03f)
                        lineTo(cx + minDim * 0.16f, cy + minDim * 0.09f)
                        lineTo(cx + minDim * 0.44f, cy + minDim * 0.05f)
                        lineTo(cx + minDim * 0.44f, cy + minDim * 0.40f)
                        close()
                    }
                    drawPath(
                        sunriseRidge,
                        brush = Brush.verticalGradient(
                            listOf(Color(0xFF422E1A).copy(alpha = 0.94f), Color(0xFF21150A)),
                            startY = cy,
                            endY = cy + minDim * 0.40f
                        )
                    )
                    // Morning horizon line
                    drawLine(
                        color = Color(0xFFFFE082).copy(alpha = 0.70f),
                        start = Offset(cx - minDim * 0.16f, cy + minDim * 0.12f),
                        end = Offset(cx + minDim * 0.16f, cy + minDim * 0.12f),
                        strokeWidth = 2f,
                        cap = StrokeCap.Round
                    )
                }
                DayPhase.DAY -> {
                    // Radiant Sun
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color(0xFFFFEE58).copy(alpha = 0.65f), Color(0xFFFFA726).copy(alpha = 0.22f), Color.Transparent),
                            center = Offset(cx, cy),
                            radius = minDim * 0.44f
                        ),
                        radius = minDim * 0.44f,
                        center = Offset(cx, cy)
                    )
                    // 8 corona rays
                    for (i in 0 until 8) {
                        val angle = (i * 45f) * (Math.PI / 180f)
                        val startR = minDim * 0.25f
                        val endR = minDim * 0.35f
                        drawLine(
                            color = Color(0xFFFFCA28).copy(alpha = 0.75f),
                            start = Offset(cx + (startR * Math.cos(angle)).toFloat(), cy + (startR * Math.sin(angle)).toFloat()),
                            end = Offset(cx + (endR * Math.cos(angle)).toFloat(), cy + (endR * Math.sin(angle)).toFloat()),
                            strokeWidth = 2.2f,
                            cap = StrokeCap.Round
                        )
                    }
                    // Sun core
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color(0xFFFFFDE7), Color(0xFFFFEB3B), Color(0xFFFB8C00)),
                            center = Offset(cx - minDim * 0.05f, cy - minDim * 0.05f),
                            radius = minDim * 0.22f
                        ),
                        radius = minDim * 0.22f,
                        center = Offset(cx, cy)
                    )
                }
                DayPhase.NIGHT -> {
                    // Starry glow
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color(0xFFE0F7FA).copy(alpha = 0.45f), Color(0xFF80DEEA).copy(alpha = 0.15f), Color.Transparent),
                            center = Offset(cx, cy),
                            radius = minDim * 0.42f
                        ),
                        radius = minDim * 0.42f,
                        center = Offset(cx, cy)
                    )
                    // Crescent moon path
                    val moonPath = Path().apply {
                        moveTo(cx - minDim * 0.04f, cy - minDim * 0.24f)
                        cubicTo(
                            cx + minDim * 0.22f, cy - minDim * 0.20f,
                            cx + minDim * 0.22f, cy + minDim * 0.20f,
                            cx - minDim * 0.04f, cy + minDim * 0.24f
                        )
                        cubicTo(
                            cx + minDim * 0.08f, cy + minDim * 0.14f,
                            cx + minDim * 0.08f, cy - minDim * 0.14f,
                            cx - minDim * 0.04f, cy - minDim * 0.24f
                        )
                        close()
                    }
                    drawPath(
                        moonPath,
                        brush = Brush.linearGradient(
                            listOf(Color(0xFFFFFFFF), Color(0xFFE1F5FE), Color(0xFF81D4FA))
                        )
                    )
                    // Twinkling stars
                    drawCircle(Color.White.copy(alpha = 0.90f), radius = 2.2f, center = Offset(cx - minDim * 0.18f, cy - minDim * 0.12f))
                    drawCircle(Color.White.copy(alpha = 0.75f), radius = 1.6f, center = Offset(cx - minDim * 0.10f, cy + minDim * 0.16f))
                }
                DayPhase.RAIN -> {
                    val cloudCenter = Offset(cx, cy - minDim * 0.05f)
                    drawCircle(Color(0xFF78909C), radius = minDim * 0.22f, center = Offset(cloudCenter.x - minDim * 0.14f, cloudCenter.y))
                    drawCircle(Color(0xFF90A4AE), radius = minDim * 0.28f, center = Offset(cloudCenter.x + minDim * 0.02f, cloudCenter.y - minDim * 0.08f))
                    drawCircle(Color(0xFF607D8B), radius = minDim * 0.20f, center = Offset(cloudCenter.x + minDim * 0.16f, cloudCenter.y + minDim * 0.02f))
                    for (d in 0..2) {
                        val dx = cx - minDim * 0.14f + d * minDim * 0.14f
                        val dy = cy + minDim * 0.14f + (dropAnim + d * 8f) % 20f
                        drawLine(
                            color = Color(0xFF64B5F6),
                            start = Offset(dx, dy),
                            end = Offset(dx - 3f, dy + 8f),
                            strokeWidth = 2.5f,
                            cap = StrokeCap.Round
                        )
                    }
                }
                DayPhase.STORM -> {
                    val cloudCenter = Offset(cx, cy - minDim * 0.06f)
                    drawCircle(Color(0xFF37474F), radius = minDim * 0.22f, center = Offset(cloudCenter.x - minDim * 0.14f, cloudCenter.y))
                    drawCircle(Color(0xFF455A64), radius = minDim * 0.28f, center = Offset(cloudCenter.x + minDim * 0.02f, cloudCenter.y - minDim * 0.08f))
                    drawCircle(Color(0xFF263238), radius = minDim * 0.20f, center = Offset(cloudCenter.x + minDim * 0.16f, cloudCenter.y + minDim * 0.02f))
                    val boltPath = Path().apply {
                        moveTo(cx + 2f, cy)
                        lineTo(cx - 5f, cy + minDim * 0.16f)
                        lineTo(cx + 1f, cy + minDim * 0.16f)
                        lineTo(cx - 3f, cy + minDim * 0.32f)
                    }
                    drawPath(boltPath, color = Color(0xFFFFEB3B), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.5f, cap = StrokeCap.Round))
                }
                DayPhase.SNOW -> {
                    val cloudCenter = Offset(cx, cy - minDim * 0.05f)
                    drawCircle(Color(0xFFB0BEC5), radius = minDim * 0.22f, center = Offset(cloudCenter.x - minDim * 0.14f, cloudCenter.y))
                    drawCircle(Color(0xFFECEFF1), radius = minDim * 0.28f, center = Offset(cloudCenter.x + minDim * 0.02f, cloudCenter.y - minDim * 0.08f))
                    drawCircle(Color(0xFFCFD8DC), radius = minDim * 0.20f, center = Offset(cloudCenter.x + minDim * 0.16f, cloudCenter.y + minDim * 0.02f))
                    for (d in 0..2) {
                        val dx = cx - minDim * 0.14f + d * minDim * 0.14f
                        val dy = cy + minDim * 0.16f + (dropAnim * 0.7f + d * 7f) % 18f
                        drawCircle(Color.White, radius = 2.4f, center = Offset(dx, dy))
                    }
                }
                DayPhase.CHILL -> {
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color(0xFFFFCC80).copy(alpha = 0.55f), Color(0xFFFF8A65).copy(alpha = 0.20f), Color.Transparent),
                            center = Offset(cx, cy),
                            radius = minDim * 0.40f
                        ),
                        radius = minDim * 0.40f,
                        center = Offset(cx, cy)
                    )
                    drawRoundRect(
                        color = Color(0xFFFFE0B2),
                        topLeft = Offset(cx - minDim * 0.18f, cy - minDim * 0.08f),
                        size = Size(minDim * 0.36f, minDim * 0.30f),
                        cornerRadius = CornerRadius(10f, 10f)
                    )
                    for (s in -1..1) {
                        val sx = cx + s * 10f
                        val sy = cy - minDim * 0.12f - (dropAnim * 0.5f + (s + 1) * 6f) % 14f
                        drawCircle(Color.White.copy(alpha = 0.6f), radius = 2.2f, center = Offset(sx, sy))
                    }
                }
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)

@Composable

fun GlobalYtPlaylistOverlay(viewModel: PlayerViewModel, onNavigateToArtist: (String) -> Unit = {}) {
    val ytPlaylistDetails by viewModel.ytPlaylistDetails.collectAsState()
    val pinnedIds by viewModel.pinnedIds.collectAsState()
    val isYtPlaylistLoading by viewModel.isYtPlaylistLoading.collectAsState()
    if (ytPlaylistDetails == null) return
    val ytPlaylistName by viewModel.ytPlaylistName.collectAsState()
    val ytPlaylistId by viewModel.ytPlaylistId.collectAsState()
    val ytPlaylistType by viewModel.ytPlaylistType.collectAsState()
    val ytAlbumPageDetails by viewModel.ytAlbumPageDetails.collectAsState()
    val ytPlaylistPageDetails by viewModel.ytPlaylistPageDetails.collectAsState()
    val context = LocalContext.current
    val bgColor = MaterialTheme.colorScheme.background
    val onBgColor = MaterialTheme.colorScheme.onBackground
    val cardBgColor = MaterialTheme.colorScheme.surfaceContainer
    val accentColor = MaterialTheme.colorScheme.primary
    val detailsList = ytPlaylistDetails!!
    var isYtPlaylistSearchActive by remember { mutableStateOf(false) }
    var ytPlaylistSearchQuery by remember { mutableStateOf("") }
    BackHandler {
        if (isYtPlaylistSearchActive) {
            isYtPlaylistSearchActive = false
            ytPlaylistSearchQuery = ""
        } else {
            viewModel.closeYtPlaylist()
        }
    }
    val filteredYtSongs = if (ytPlaylistSearchQuery.isNotBlank()) {
        detailsList.filter { it.title.contains(ytPlaylistSearchQuery, ignoreCase = true) || it.artist.contains(ytPlaylistSearchQuery, ignoreCase = true) }
    } else { detailsList }
    val dbName = if (ytPlaylistType == "Album") "online_albums_meta" else if (ytPlaylistType == "Podcast") "online_podcasts_meta" else "online_playlists_meta"
    val prefix = if (ytPlaylistType == "Album") "online_album_" else if (ytPlaylistType == "Podcast") "online_podcast_" else "online_pl_"
    val plPrefs = context.getSharedPreferences(dbName, Context.MODE_PRIVATE)
    var isSaved by remember(ytPlaylistName) { mutableStateOf(plPrefs.getStringSet("names", emptySet())?.contains(ytPlaylistName) == true) }
    var downloadedCount by remember { mutableStateOf(0) }
    LaunchedEffect(detailsList) {
        downloadedCount = detailsList.count { track ->
            val safeName = track.title.replace(Regex("[^a-zA-Z0-9 ]"), "_").take(50).trim()
            File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "$safeName.mp3").exists()
        }
    }
    val isFullyDownloaded = detailsList.isNotEmpty() && downloadedCount == detailsList.size
    var showBottomSheet by remember { mutableStateOf(false) }
    var selectedBottomSong by remember { mutableStateOf<com.musicplayer.lytrix.model.JioSaavnSong?>(null) }
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var detailsSong by remember { mutableStateOf<com.musicplayer.lytrix.model.JioSaavnSong?>(null) }
    var showAddToShowsPlaylistDialog by remember { mutableStateOf<com.musicplayer.lytrix.model.JioSaavnSong?>(null) }
    var newShowsPlaylistName by remember { mutableStateOf("") }
    detailsSong?.let { YtSongDetailsSheet(song = it, onDismiss = { detailsSong = null }) }

    if (showAddToShowsPlaylistDialog != null) {
        val targetItem = showAddToShowsPlaylistDialog!!
        AlertDialog(
            onDismissRequest = { showAddToShowsPlaylistDialog = null },
            containerColor = cardBgColor,
            title = { Text("Add to Shows Playlist", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    val plMetaPrefs = context.getSharedPreferences("online_shows_playlists_meta", Context.MODE_PRIVATE)
                    val playlists = plMetaPrefs.getStringSet("names", emptySet<String>())?.toList() ?: emptyList()
                    if (playlists.isNotEmpty()) {
                        Text("Existing Playlists:", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                        LazyColumn(modifier = Modifier.heightIn(max = 160.dp).padding(vertical = 8.dp)) {
                            items(playlists) { plName ->
                                Text(
                                    text = plName,
                                    color = Color.White,
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        val showPlPrefs = context.getSharedPreferences("online_show_pl_$plName", Context.MODE_PRIVATE)
                                        val obj = JSONObject().apply {
                                            put("id", targetItem.id)
                                            put("title", targetItem.name)
                                            put("name", targetItem.name)
                                            put("artist", targetItem.artist)
                                            put("imageUrl", targetItem.imageUrl)
                                            put("downloadUrl", targetItem.downloadUrl ?: "")
                                            put("albumName", targetItem.albumName ?: "Shows")
                                            put("timestamp", System.currentTimeMillis())
                                        }
                                        showPlPrefs.edit().putString(targetItem.id, obj.toString()).apply()
                                        Toast.makeText(context, "Added to $plName", Toast.LENGTH_SHORT).show()
                                        showAddToShowsPlaylistDialog = null
                                    }.padding(8.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newShowsPlaylistName,
                        onValueChange = { newShowsPlaylistName = it },
                        placeholder = { Text("Or create new shows playlist...", color = Color.Gray) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newShowsPlaylistName.isNotBlank()) {
                        val plMetaPrefs = context.getSharedPreferences("online_shows_playlists_meta", Context.MODE_PRIVATE)
                        val existing = plMetaPrefs.getStringSet("names", emptySet<String>())?.toMutableSet() ?: mutableSetOf()
                        val trimmedName = newShowsPlaylistName.trim()
                        existing.add(trimmedName)
                        plMetaPrefs.edit().putStringSet("names", existing).apply()
                        context.getSharedPreferences("playlist_time", Context.MODE_PRIVATE).edit().putLong(trimmedName, System.currentTimeMillis()).apply()
                        context.getSharedPreferences("playlist_source", Context.MODE_PRIVATE).edit().putString(trimmedName, "Shows").apply()

                        val showPlPrefs = context.getSharedPreferences("online_show_pl_$trimmedName", Context.MODE_PRIVATE)
                        val obj = JSONObject().apply {
                            put("id", targetItem.id)
                            put("title", targetItem.name)
                            put("name", targetItem.name)
                            put("artist", targetItem.artist)
                            put("imageUrl", targetItem.imageUrl)
                            put("downloadUrl", targetItem.downloadUrl ?: "")
                            put("albumName", targetItem.albumName ?: "Shows")
                            put("timestamp", System.currentTimeMillis())
                        }
                        showPlPrefs.edit().putString(targetItem.id, obj.toString()).apply()
                        Toast.makeText(context, "Created & Added", Toast.LENGTH_SHORT).show()
                        newShowsPlaylistName = ""
                        showAddToShowsPlaylistDialog = null
                    }
                }) { Text("Create & Add", color = accentColor) }
            },
            dismissButton = { TextButton(onClick = { showAddToShowsPlaylistDialog = null }) { Text("Cancel", color = Color.White) } }
        )
    }
    Box(modifier = Modifier.fillMaxSize().background(bgColor)) {
        if (ytPlaylistType == "Album") {
            MetroAlbumScreen(
                viewModel = viewModel,
                albumPage = ytAlbumPageDetails,
                albumTitle = ytPlaylistName,
                albumId = ytPlaylistId,
                tracks = detailsList,
                isLoading = isYtPlaylistLoading,
                isSaved = isSaved,
                isFullyDownloaded = isFullyDownloaded,
                onBack = { viewModel.closeYtPlaylist() },
                onToggleSave = { displayImage ->
                    val set = plPrefs.getStringSet("names", emptySet())?.toMutableSet() ?: mutableSetOf()
                    val songPrefs = context.getSharedPreferences("$prefix$ytPlaylistName", Context.MODE_PRIVATE)
                    val coverPrefs = context.getSharedPreferences("playlist_covers", Context.MODE_PRIVATE)
                    val timePrefs = context.getSharedPreferences("playlist_time", Context.MODE_PRIVATE)
                    val sourcePrefsSave = context.getSharedPreferences("playlist_source", Context.MODE_PRIVATE)
                    val idPrefsSave = context.getSharedPreferences("playlist_ids", Context.MODE_PRIVATE)
                    if (isSaved) {
                        set.remove(ytPlaylistName)
                        songPrefs.edit().clear().apply()
                        coverPrefs.edit().remove(ytPlaylistName).apply()
                        timePrefs.edit().remove(ytPlaylistName).apply()
                        sourcePrefsSave.edit().remove(ytPlaylistName).apply()
                        idPrefsSave.edit().remove(ytPlaylistName).apply()
                        plPrefs.edit().remove("count_$ytPlaylistName").apply()
                        Toast.makeText(context, "Removed from Album", Toast.LENGTH_SHORT).show()
                    } else {
                        set.add(ytPlaylistName)
                        val editor = songPrefs.edit()
                        detailsList.forEach { track ->
                            val obj = org.json.JSONObject()
                            obj.put("id", track.id); obj.put("title", track.title); obj.put("name", track.title); obj.put("artist", track.artist); obj.put("imageUrl", track.thumbnailUrl); obj.put("downloadUrl", "")
                            editor.putString(track.id, obj.toString())
                        }
                        editor.apply()
                        coverPrefs.edit().putString(ytPlaylistName, displayImage).apply()
                        timePrefs.edit().putLong(ytPlaylistName, System.currentTimeMillis()).apply()
                        sourcePrefsSave.edit().putString(ytPlaylistName, "YouTube").apply()
                        if (ytPlaylistId.isNotBlank()) idPrefsSave.edit().putString(ytPlaylistName, ytPlaylistId).apply()
                        plPrefs.edit().putInt("count_$ytPlaylistName", detailsList.size).apply()
                        Toast.makeText(context, "Saved to Album", Toast.LENGTH_SHORT).show()
                    }
                    plPrefs.edit().putStringSet("names", set).apply()
                    isSaved = !isSaved
                },
                onDownloadAll = {
                    if (!isFullyDownloaded) {
                        requestDownloadWithAd(context, ytPlaylistName, isPlaylist = true) {
                            detailsList.forEach { track ->
                                val safeName = track.title.replace(Regex("[^a-zA-Z0-9 ]"), "_").take(50).trim()
                                if (!File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "$safeName.mp3").exists()) {
                                    com.musicplayer.lytrix.playback.OkHttpDownloader.startDownload(
                                        context, track.id, track.title, track.artist, track.thumbnailUrl, ""
                                    )
                                }
                            }
                            Toast.makeText(context, "Downloading tracks...", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onNavigateToArtist = { artistName ->
                    viewModel.closeYtPlaylist()
                    onNavigateToArtist(artistName)
                },
                onOpenOtherVersion = { otherAlbum ->
                    viewModel.loadYtPlaylistDetails(otherAlbum.browseId, "Album", otherAlbum.title)
                },
                onTrackClick = { _, playlist, startIdx ->
                    viewModel.playLytrixPlaylistWithAd(playlist, startIdx, context)
                },
                onTrackMenuClick = { jioSong ->
                    selectedBottomSong = jioSong
                    showBottomSheet = true
                },
                bottomPadding = bottomListPadding,
            )
        } else if (ytPlaylistType != "Podcast") {
            MetroPlaylistScreen(
                viewModel = viewModel,
                playlistPage = ytPlaylistPageDetails,
                playlistTitle = ytPlaylistName,
                playlistId = ytPlaylistId,
                tracks = detailsList,
                isLoading = isYtPlaylistLoading,
                isSaved = isSaved,
                isFullyDownloaded = isFullyDownloaded,
                onBack = { viewModel.closeYtPlaylist() },
                onToggleSave = { displayImage ->
                    val set = plPrefs.getStringSet("names", emptySet())?.toMutableSet() ?: mutableSetOf()
                    val songPrefs = context.getSharedPreferences("$prefix$ytPlaylistName", Context.MODE_PRIVATE)
                    val coverPrefs = context.getSharedPreferences("playlist_covers", Context.MODE_PRIVATE)
                    val timePrefs = context.getSharedPreferences("playlist_time", Context.MODE_PRIVATE)
                    val sourcePrefsSave = context.getSharedPreferences("playlist_source", Context.MODE_PRIVATE)
                    val idPrefsSave = context.getSharedPreferences("playlist_ids", Context.MODE_PRIVATE)
                    if (isSaved) {
                        set.remove(ytPlaylistName)
                        songPrefs.edit().clear().apply()
                        coverPrefs.edit().remove(ytPlaylistName).apply()
                        timePrefs.edit().remove(ytPlaylistName).apply()
                        sourcePrefsSave.edit().remove(ytPlaylistName).apply()
                        idPrefsSave.edit().remove(ytPlaylistName).apply()
                        plPrefs.edit().remove("count_$ytPlaylistName").apply()
                        Toast.makeText(context, "Removed from Playlist", Toast.LENGTH_SHORT).show()
                    } else {
                        set.add(ytPlaylistName)
                        val editor = songPrefs.edit()
                        detailsList.forEach { track ->
                            val obj = org.json.JSONObject()
                            obj.put("id", track.id); obj.put("title", track.title); obj.put("name", track.title); obj.put("artist", track.artist); obj.put("imageUrl", track.thumbnailUrl); obj.put("downloadUrl", "")
                            editor.putString(track.id, obj.toString())
                        }
                        editor.apply()
                        coverPrefs.edit().putString(ytPlaylistName, displayImage).apply()
                        timePrefs.edit().putLong(ytPlaylistName, System.currentTimeMillis()).apply()
                        sourcePrefsSave.edit().putString(ytPlaylistName, "YouTube").apply()
                        if (ytPlaylistId.isNotBlank()) idPrefsSave.edit().putString(ytPlaylistName, ytPlaylistId).apply()
                        plPrefs.edit().putInt("count_$ytPlaylistName", detailsList.size).apply()
                        Toast.makeText(context, "Saved to Playlist", Toast.LENGTH_SHORT).show()
                    }
                    plPrefs.edit().putStringSet("names", set).apply()
                    isSaved = !isSaved
                },
                onDownloadAll = {
                    if (!isFullyDownloaded) {
                        requestDownloadWithAd(context, ytPlaylistName, isPlaylist = true) {
                            detailsList.forEach { track ->
                                val safeName = track.title.replace(Regex("[^a-zA-Z0-9 ]"), "_").take(50).trim()
                                if (!File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "$safeName.mp3").exists()) {
                                    com.musicplayer.lytrix.playback.OkHttpDownloader.startDownload(
                                        context, track.id, track.title, track.artist, track.thumbnailUrl, ""
                                    )
                                }
                            }
                            Toast.makeText(context, "Downloading tracks...", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onNavigateToArtist = { artistName ->
                    viewModel.closeYtPlaylist()
                    onNavigateToArtist(artistName)
                },
                onTrackClick = { _, playlist, startIdx ->
                    viewModel.playLytrixPlaylistWithAd(playlist, startIdx, context)
                },
                onTrackMenuClick = { jioSong ->
                    selectedBottomSong = jioSong
                    showBottomSheet = true
                },
                bottomPadding = bottomListPadding,
            )
        } else if (isYtPlaylistLoading) {
            LytrixPlaylistDetailShimmer()
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 48.dp, bottom = 8.dp, start = 8.dp, end = 16.dp)) {
                    if (isYtPlaylistSearchActive) {
                        OutlinedTextField(
                            value = ytPlaylistSearchQuery, onValueChange = { ytPlaylistSearchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Search songs...", color = Color.Gray) },
                            leadingIcon = { IconButton(onClick = { isYtPlaylistSearchActive = false; ytPlaylistSearchQuery = "" }) { Icon(Icons.Default.ArrowBack, tint = Color.White, contentDescription = "Back") } },
                            singleLine = true, shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color(0xFF2D2D2D), unfocusedContainerColor = Color(0xFF2D2D2D), focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent, focusedTextColor = Color.White)
                        )
                    } else {
                        IconButton(onClick = { viewModel.closeYtPlaylist() }) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White) }
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = { isYtPlaylistSearchActive = true }) { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White) }
                    }
                }
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomListPadding)) {
                    item {
                        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {

                            // 🔥 কভার ছবির ফিক্স: globalActiveYtCover আবার যুক্ত করা হয়েছে!
                            val savedCover = context.getSharedPreferences("playlist_covers", Context.MODE_PRIVATE).getString(ytPlaylistName, null)
                            val displayImage = savedCover ?: globalActiveYtCover.takeIf { it.isNotBlank() } ?: detailsList.firstOrNull()?.thumbnailUrl ?: "https://images.unsplash.com/photo-1470225620780-dba8ba36b745"
                            
                            Box(contentAlignment = Alignment.Center) {
                                if (ytPlaylistType == "Album") {
                                    AsyncImage(
                                        model = displayImage,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(260.dp)
                                            .blur(36.dp)
                                            .alpha(0.55f)
                                    )
                                }
                                Box(modifier = Modifier.size(240.dp).clip(RoundedCornerShape(16.dp))) {
                                    AsyncImage(model = displayImage, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                }
                            }
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(text = ytPlaylistName, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 16.dp))
                            
                            if (ytPlaylistType == "Album") {
                                val primaryArtist = detailsList.firstOrNull()?.artist?.split(",")?.firstOrNull()?.substringBefore(" • ")?.trim() ?: ""
                                if (primaryArtist.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Surface(
                                        onClick = { onNavigateToArtist(primaryArtist) },
                                        shape = RoundedCornerShape(20.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Person,
                                                contentDescription = null,
                                                tint = accentColor,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = primaryArtist,
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = if (ytPlaylistType == "Album") "Album • ${detailsList.size} tracks" else "YouTube Music • ${detailsList.size} tracks", color = Color.Gray, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(20.dp))
                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = {
                                    if (!isFullyDownloaded) {
                                        requestDownloadWithAd(context, ytPlaylistName, isPlaylist = true) {
                                            detailsList.forEach { track ->
                                                val tempSong = com.musicplayer.lytrix.model.JioSaavnSong(track.id, track.title, track.artist, track.thumbnailUrl, "")
                                                val safeName = track.title.replace(Regex("[^a-zA-Z0-9 ]"), "_").take(50).trim()
                                                if (!File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "$safeName.mp3").exists()) {
                                                    com.musicplayer.lytrix.playback.OkHttpDownloader.startDownload(
                                                        context, track.id, track.title, track.artist, track.thumbnailUrl ?: "", ""
                                                    )
                                                }
                                            }
                                            Toast.makeText(context, "Downloading tracks...", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }, modifier = Modifier.size(48.dp)) { Icon(if (isFullyDownloaded) Icons.Default.DownloadDone else Icons.Default.Download, contentDescription = "Download", tint = if (isFullyDownloaded) accentColor else Color.White) }
                                IconButton(onClick = {
                                    val set = plPrefs.getStringSet("names", emptySet())?.toMutableSet() ?: mutableSetOf()
                                    val songPrefs = context.getSharedPreferences("$prefix$ytPlaylistName", Context.MODE_PRIVATE)
                                    val coverPrefs = context.getSharedPreferences("playlist_covers", Context.MODE_PRIVATE)
                                    val timePrefs = context.getSharedPreferences("playlist_time", Context.MODE_PRIVATE)
                                    if (isSaved) {
                                        set.remove(ytPlaylistName)
                                        songPrefs.edit().clear().apply()
                                        coverPrefs.edit().remove(ytPlaylistName).apply()
                                        timePrefs.edit().remove(ytPlaylistName).apply()
                                        Toast.makeText(context, "Removed from $ytPlaylistType", Toast.LENGTH_SHORT).show()
                                    } else {
                                        set.add(ytPlaylistName)
                                        val editor = songPrefs.edit()
                                        detailsList.forEach { track ->
                                            val obj = org.json.JSONObject()
                                            obj.put("id", track.id); obj.put("title", track.title); obj.put("name", track.title); obj.put("artist", track.artist); obj.put("imageUrl", track.thumbnailUrl); obj.put("downloadUrl", "")
                                            editor.putString(track.id, obj.toString())
                                        }
                                        editor.apply()
                                        coverPrefs.edit().putString(ytPlaylistName, displayImage).apply()
                                        timePrefs.edit().putLong(ytPlaylistName, System.currentTimeMillis()).apply()
                                        Toast.makeText(context, "Saved to $ytPlaylistType", Toast.LENGTH_SHORT).show()
                                    }
                                    plPrefs.edit().putStringSet("names", set).apply()
                                    isSaved = !isSaved
                                }, modifier = Modifier.size(48.dp)) { Icon(if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, contentDescription = "Save", tint = Color.White) }
                                Box(modifier = Modifier.size(72.dp).clip(CircleShape).background(Color.White).clickable {
                                    if (filteredYtSongs.isNotEmpty()) {
                                        val firstTrack = filteredYtSongs.first()
                                        val cleanArtist = firstTrack.artist.substringBefore(" • ").substringBefore(" \u2022 ").trim()
                                        val lytrixSong = com.musicplayer.lytrix.LytrixSongItem(videoId = firstTrack.id, title = firstTrack.title, artist = cleanArtist, duration = "", thumbnailUrl = firstTrack.thumbnailUrl)
                                        viewModel.playLytrixSongWithAd(lytrixSong, context)
                                    }
                                }, contentAlignment = Alignment.Center) { Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.Black, modifier = Modifier.size(36.dp)) }
                                IconButton(onClick = {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(android.content.Intent.EXTRA_TEXT, "https://music.youtube.com/search?q=${java.net.URLEncoder.encode(ytPlaylistName, "UTF-8")}")
                                    }
                                    context.startActivity(android.content.Intent.createChooser(intent, "Share"))
                                }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White) }
                                IconButton(onClick = {
                                    if (filteredYtSongs.isNotEmpty()) {
                                        val randomTrack = filteredYtSongs.random()
                                        val cleanArtist = viewModel.cleanArtistDisplayName(randomTrack.artist)
                                        val lytrixSong = com.musicplayer.lytrix.LytrixSongItem(videoId = randomTrack.id, title = randomTrack.title, artist = cleanArtist, duration = "", thumbnailUrl = randomTrack.thumbnailUrl)
                                        viewModel.playLytrixSongWithAd(lytrixSong, context)
                                    }
                                }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Shuffle, contentDescription = "Shuffle", tint = Color.White) }
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                    itemsIndexed(filteredYtSongs) { index, track ->
                        val cleanArtist = viewModel.cleanArtistDisplayName(track.artist)
                        val tempJioSong = com.musicplayer.lytrix.model.JioSaavnSong(track.id, track.title, cleanArtist, track.thumbnailUrl, if (ytPlaylistType == "Podcast") "Podcast" else if (ytPlaylistType == "Audiobook") "Audiobook" else "")
                        val currentSong by viewModel.currentSong.collectAsState()
                        val isCurrent = currentSong?.originalId == track.id || currentSong?.id?.toString() == track.id
                        val isPlaying by viewModel.isPlaying.collectAsState()

                        if (ytPlaylistType == "Album") {
                            AlbumTrackRow(
                                trackNumber = index + 1,
                                title = track.title,
                                artist = cleanArtist,
                                isCurrent = isCurrent,
                                isPlaying = isCurrent && isPlaying,
                                accentColor = accentColor,
                                onClick = { val origIndex = detailsList.indexOf(track); viewModel.playLytrixPlaylistWithAd(detailsList, origIndex, context) },
                                onMoreClick = { selectedBottomSong = tempJioSong; showBottomSheet = true }
                            )
                        } else {
                            PremiumOnlineSongItem(
                                song = tempJioSong, onBgColor = onBgColor, cardBgColor = cardBgColor,
                                onClick = { val origIndex = detailsList.indexOf(track); viewModel.playLytrixPlaylistWithAd(detailsList, origIndex, context) },
                                onMoreClick = { selectedBottomSong = tempJioSong; showBottomSheet = true }
                            )
                        }
                    }
                }
            }
        }

        // Bottom Sheet (available for Album, Playlist, and Podcast)
        if (showBottomSheet && selectedBottomSong != null) {
                val isDownloadedLocally = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "${selectedBottomSong!!.name.replace(Regex("[^a-zA-Z0-9 ]"), "_").take(50).trim()}.mp3").exists()

                val isPodcastOverlay = ytPlaylistType == "Podcast" || selectedBottomSong?.albumName == "Podcast"
                val isAudiobookOverlay = ytPlaylistType == "Audiobook" || selectedBottomSong?.albumName == "Audiobook"
                val isShowOverlay = isPodcastOverlay || isAudiobookOverlay

                // 🔥 ম্যাজিক ১: আগে থেকেই লাইক করা আছে কি না তার রিয়েল-টাইম স্টেট
                val onlineLikedPrefs = if (isAudiobookOverlay) {
                    context.getSharedPreferences("online_liked_audiobooks", Context.MODE_PRIVATE)
                } else if (isPodcastOverlay) {
                    context.getSharedPreferences("online_liked_podcasts", Context.MODE_PRIVATE)
                } else {
                    context.getSharedPreferences("online_liked_songs", Context.MODE_PRIVATE)
                }
                var isOnlineLiked by remember(selectedBottomSong) {
                    mutableStateOf(onlineLikedPrefs.getStringSet("liked_ids", emptySet())?.contains(selectedBottomSong!!.id) == true)
                }
                com.musicplayer.lytrix.ui.component.LytrixFluidBottomSheet(
                    onDismissRequest = { showBottomSheet = false; selectedBottomSong = null },
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(model = selectedBottomSong!!.imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)))
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = selectedBottomSong!!.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                Text(text = selectedBottomSong!!.artist, color = Color.Gray, fontSize = 14.sp, maxLines = 1)
                            }

                            // 🔥 ম্যাজিক ২: ডানপাশে লাইক বাটন এবং বাগ-ফ্রি সেভ লজিক
                            IconButton(onClick = {
                                val currentLikedIds = onlineLikedPrefs.getStringSet("liked_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
                                val songIdStr = selectedBottomSong!!.id
                                if (isOnlineLiked) {
                                    currentLikedIds.remove(songIdStr)
                                    onlineLikedPrefs.edit()
                                        .remove("song_data_$songIdStr")
                                        .remove("podcast_data_$songIdStr")
                                        .putStringSet("liked_ids", currentLikedIds)
                                        .apply()
                                    isOnlineLiked = false
                                    Toast.makeText(context, if (isShowOverlay) "Removed from Liked Shows" else "Removed from Cloud Likes", Toast.LENGTH_SHORT).show()
                                } else {
                                    currentLikedIds.add(songIdStr)
                                    val jsonObj = org.json.JSONObject()

                                    // 🔥 বাগ-কিলার: একদম পারফেক্ট JSON ডেটা তৈরি হচ্ছে
                                    jsonObj.put("id", songIdStr)
                                    jsonObj.put("originalId", songIdStr)
                                    jsonObj.put("title", selectedBottomSong!!.name)
                                    jsonObj.put("title_original", selectedBottomSong!!.name)
                                    jsonObj.put("name", selectedBottomSong!!.name)
                                    jsonObj.put("subtitle", selectedBottomSong!!.artist)
                                    jsonObj.put("artist", selectedBottomSong!!.artist)
                                    jsonObj.put("publisher", selectedBottomSong!!.artist)
                                    jsonObj.put("image", selectedBottomSong!!.imageUrl)
                                    jsonObj.put("imageUrl", selectedBottomSong!!.imageUrl)
                                    jsonObj.put("url", selectedBottomSong!!.downloadUrl ?: "")
                                    jsonObj.put("downloadUrl", selectedBottomSong!!.downloadUrl ?: "")
                                    onlineLikedPrefs.edit()
                                        .putString("song_data_$songIdStr", jsonObj.toString())
                                        .putString("podcast_data_$songIdStr", jsonObj.toString())
                                        .putStringSet("liked_ids", currentLikedIds)
                                        .apply()
                                    isOnlineLiked = true
                                    Toast.makeText(context, if (isShowOverlay) "Saved to Liked Shows" else "Saved to Cloud Likes", Toast.LENGTH_SHORT).show()
                                }

                                // 🔥 ফায়ারবেস অটো-সিঙ্ক
                                try {
                                    performSilentSync(context)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }) {
                                Icon(
                                    imageVector = if (isOnlineLiked) androidx.compose.material.icons.Icons.Default.Favorite else androidx.compose.material.icons.Icons.Default.FavoriteBorder,
                                    contentDescription = "Like",
                                    tint = if (isOnlineLiked) Color.Red else Color.White
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                        Spacer(modifier = Modifier.height(16.dp))
                        if (isShowOverlay) {
                            val epSong = selectedBottomSong!!
                            val laterPrefix = if (isAudiobookOverlay) "online_ab_" else "online_podcast_"
                            val laterName = if (isAudiobookOverlay) "Books for later" else "Episodes for later"
                            val laterPlaylistPrefs = context.getSharedPreferences("$laterPrefix$laterName", Context.MODE_PRIVATE)
                            val metaPrefKey = if (isAudiobookOverlay) "online_audiobooks_meta" else "online_podcasts_meta"
                            val laterIdsKey = if (isAudiobookOverlay) "books_for_later_ids" else "episodes_for_later_ids"
                            val laterPrefs = context.getSharedPreferences(metaPrefKey, Context.MODE_PRIVATE)
                            val laterSet = laterPrefs.getStringSet(laterIdsKey, emptySet())?.toMutableSet() ?: mutableSetOf()
                            val isSavedForLater = laterPlaylistPrefs.contains(epSong.id) || laterSet.contains(epSong.id)

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                ) {
                                    LytrixBottomSheetListItem(
                                        icon = Icons.Default.PlaylistPlay,
                                        text = "Play next",
                                        onClick = {
                                            viewModel.playNextOnline(context, epSong)
                                            showBottomSheet = false
                                        }
                                    )
                                    LytrixBottomSheetListItem(
                                        icon = if (isSavedForLater) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                        text = if (isSavedForLater) {
                                            if (isAudiobookOverlay) "Saved in Books for later" else "Saved in Episodes for later"
                                        } else {
                                            if (isAudiobookOverlay) "Save to Books for later" else "Save to Episodes for later"
                                        },
                                        onClick = {
                                            if (isSavedForLater) {
                                                laterSet.remove(epSong.id)
                                                laterPlaylistPrefs.edit().remove(epSong.id).apply()
                                                Toast.makeText(context, if (isAudiobookOverlay) "Removed from Books for Later" else "Removed from Episodes for Later", Toast.LENGTH_SHORT).show()
                                            } else {
                                                laterSet.add(epSong.id)
                                                val json = JSONObject().apply {
                                                    put("id", epSong.id)
                                                    put("name", epSong.name)
                                                    put("title", epSong.name)
                                                    put("artist", epSong.artist)
                                                    put("publisher", epSong.artist)
                                                    put("imageUrl", epSong.imageUrl)
                                                    put("downloadUrl", epSong.downloadUrl ?: "")
                                                    put("albumName", if (isAudiobookOverlay) "Audiobook" else "Podcast")
                                                }
                                                laterPlaylistPrefs.edit().putString(epSong.id, json.toString()).apply()
                                                Toast.makeText(context, if (isAudiobookOverlay) "Saved to Books for Later" else "Saved to Episodes for Later", Toast.LENGTH_SHORT).show()
                                            }
                                            laterPrefs.edit().putStringSet(laterIdsKey, laterSet).apply()
                                            try { performSilentSync(context) } catch(e: Exception){}
                                            showBottomSheet = false
                                        }
                                    )
                                    LytrixBottomSheetListItem(
                                        icon = Icons.Default.PlaylistAdd,
                                        text = "Add to playlist",
                                        onClick = {
                                            showBottomSheet = false
                                            showAddToShowsPlaylistDialog = epSong
                                        }
                                    )
                                    LytrixBottomSheetListItem(
                                        icon = Icons.Default.Info,
                                        text = "Details & Show Notes",
                                        onClick = {
                                            detailsSong = epSong
                                            showBottomSheet = false
                                        }
                                    )
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                ) {
                                    LytrixBottomSheetListItem(
                                        icon = Icons.Default.Person,
                                        text = if (isAudiobookOverlay) "Go to Author" else "Go to Channel / Show",
                                        onClick = {
                                            showBottomSheet = false
                                            val cleanArtist = viewModel.cleanArtistDisplayName(epSong.artist)
                                            val target = if (isAudiobookOverlay) "AUDIOBOOK_$cleanArtist" else "PODCAST_$cleanArtist"
                                            onNavigateToArtist(target)
                                        }
                                    )
                                    LytrixBottomSheetListItem(
                                        icon = Icons.Default.Download,
                                        text = if (isAudiobookOverlay) "Download Audiobook" else "Download Episode",
                                        onClick = {
                                            downloadSongSecurely(context, epSong)
                                            showBottomSheet = false
                                        }
                                    )
                                    LytrixBottomSheetListItem(
                                        icon = Icons.Default.Share,
                                        text = "Share",
                                        onClick = {
                                            val shareUrl = "https://music.youtube.com/watch?v=${epSong.id}"
                                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(android.content.Intent.EXTRA_TEXT, "${epSong.name} - $shareUrl")
                                            }
                                            context.startActivity(android.content.Intent.createChooser(intent, "Share"))
                                            showBottomSheet = false
                                        }
                                    )
                                }
                            }
                        } else {
                        val isMetroGuest = viewModel.metroManager.roomRole.collectAsState().value == com.musicplayer.lytrix.listentogether.metro.MetroRoomRole.GUEST
                        val myFbStatus = viewModel.myRoomStatus.collectAsState().value
                        val isRoomGuest = isMetroGuest || (myFbStatus == "approved" || myFbStatus == "co_host")
                        val isJioSaavnItem = selectedBottomSong!!.downloadUrl.isNotBlank() ||
                            selectedBottomSong!!.imageUrl.contains("saavn", ignoreCase = true)
                        YtSongMenuBody(
                            isDownloadedLocally = isDownloadedLocally,
                            isPinnedToSpeedDial = pinnedIds.contains(selectedBottomSong?.id ?: ""),
                            showPinToSpeedDial = !isJioSaavnItem,
                            isMetroGuest = isRoomGuest,
                            onSuggestToHost = {
                                selectedBottomSong?.let { jio ->
                                    val isSaavn = jio.downloadUrl.isNotBlank() || jio.imageUrl.contains("saavn", ignoreCase = true)
                                    val trackId = if (isSaavn) "saavn:${jio.id}" else jio.id
                                    val trackAlbum = if (isSaavn && jio.downloadUrl.isNotBlank()) "saavn_url:${jio.downloadUrl}" else "JioSaavn"
                                    viewModel.suggestTrackToActiveRoom(
                                        id = trackId,
                                        title = jio.name,
                                        artist = jio.artist,
                                        album = trackAlbum,
                                        thumbnail = jio.imageUrl,
                                        streamUrl = jio.downloadUrl ?: "",
                                        context = context
                                    )
                                }
                                showBottomSheet = false
                            },
                            onExportToStorage = {
                                selectedBottomSong?.let { songItem ->
                                    if (!isUserPremium(context)) {
                                        Toast.makeText(context, "👑 Save to Storage is exclusive to Lytrix Premium users!", Toast.LENGTH_LONG).show()
                                        return@let
                                    }
                                    Toast.makeText(context, "Saving...", Toast.LENGTH_SHORT).show()
                                    CoroutineScope(Dispatchers.IO).launch {
                                        val success = com.musicplayer.lytrix.playback.OkHttpDownloader.exportSongToStorage(
                                            context = context,
                                            songId = songItem.id,
                                            title = songItem.name,
                                            artist = songItem.artist
                                        )
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(
                                                context,
                                                if (success) "Saved to Music/Lytrix 🎵" else "Export failed",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                                showBottomSheet = false
                            },
                            onPlaylist = {
                                Toast.makeText(context, "Use main library to add to playlist", Toast.LENGTH_SHORT).show()
                                showBottomSheet = false
                            },
                            onShare = {
                                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_TEXT, "https://music.youtube.com/watch?v=${selectedBottomSong!!.id}")
                                }
                                context.startActivity(android.content.Intent.createChooser(intent, "Share"))
                                showBottomSheet = false
                            },
                            onDetails = {
                                detailsSong = selectedBottomSong
                                showBottomSheet = false
                            },
                            onPlayNext = {
                                viewModel.playNextOnline(context, selectedBottomSong!!)
                                showBottomSheet = false
                            },
                            onDownloadToggle = {
                                if (isDownloadedLocally) {
                                    val file = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "${selectedBottomSong!!.name.replace(Regex("[^a-zA-Z0-9 ]"), "_").take(50).trim()}.mp3")
                                    if (file.exists()) file.delete()
                                    com.musicplayer.lytrix.playback.OkHttpDownloader.deleteSongFiles(context, selectedBottomSong!!.name)
                                    context.getSharedPreferences("downloaded_meta", Context.MODE_PRIVATE).edit().remove("song_${selectedBottomSong!!.id}").apply()
                                    Toast.makeText(context, "Removed from device", Toast.LENGTH_SHORT).show()
                                } else {
                                    downloadSongSecurely(context, selectedBottomSong!!)
                                }
                                showBottomSheet = false
                            },
                            onStartRadio = { showBottomSheet = false },
                            onGoToArtist = {
                                val cleanArtist = viewModel.cleanArtistDisplayName(selectedBottomSong!!.artist)
                                val parsedArtists = cleanArtist.split(",", " & ").map { it.trim() }.filter { it.isNotBlank() }
                                if (parsedArtists.isNotEmpty()) {
                                    onNavigateToArtist(parsedArtists.first())
                                }
                                showBottomSheet = false
                            },
                            onViewAlbum = if (!isJioSaavnItem) {
                                {
                                    val target = selectedBottomSong
                                    showBottomSheet = false
                                    if (target != null) {
                                        viewModel.openAlbumForOnlineSong(context, target)
                                    }
                                }
                            } else null,
                            onPinToSpeedDial = {
                                selectedBottomSong?.let { song ->
                                    val isPinned = pinnedIds.contains(song.id)
                                    viewModel.toggleSpeedDial(PlayerViewModel.YtMusicTrack(
                                        id = song.id, title = song.name, artist = song.artist, thumbnailUrl = song.imageUrl
                                    ))
                                    val msg = if (isPinned) "Unpinned from Speed Dial" else "Pinned to Speed Dial"
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                                showBottomSheet = false
                            },
                            onNotInterested = {
                                val songToRemove = selectedBottomSong
                                showBottomSheet = false
                                songToRemove?.let { song ->
                                    viewModel.markSongNotInterested(song.id, song.name, song.artist, song.imageUrl)
                                    viewModel.triggerUndoSnackbar(song.id)
                                }
                            }
                        )
                        }
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
    }
}

@Composable
fun AlbumTrackRow(
    trackNumber: Int,
    title: String,
    artist: String,
    isCurrent: Boolean,
    isPlaying: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isCurrent) accentColor.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(32.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isCurrent && isPlaying) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = "Playing",
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            } else if (isCurrent) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "Selected",
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text(
                    text = trackNumber.toString(),
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (isCurrent) accentColor else Color.White,
                fontSize = 15.sp,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = artist,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(onClick = onMoreClick) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "More",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

suspend fun fetchSongs(query: String, limit: Int = 30): List<JioSaavnSong> {
    val parsedSongs = mutableListOf<JioSaavnSong>()
    try {
        withContext(Dispatchers.IO) {
            val response = RetrofitInstance.api.searchSongs(query = query, limit = limit)
            var resultsArray: com.google.gson.JsonArray? = null
            if (response.has("data")) {
                val dataElem = response.get("data")
                if (dataElem.isJsonObject && dataElem.asJsonObject.has("results")) resultsArray = dataElem.asJsonObject.getAsJsonArray("results")
                else if (dataElem.isJsonArray) resultsArray = dataElem.asJsonArray
            } else if (response.has("results")) resultsArray = response.getAsJsonArray("results")
            if (resultsArray != null) {
                for (i in 0 until resultsArray.size()) {
                    try {
                        val songObj = resultsArray.get(i).asJsonObject
                        val id = if (songObj.has("id")) songObj.get("id").asString else ""
                        val name = when {
                            songObj.has("name") -> songObj.get("name").asString
                            songObj.has("title") -> songObj.get("title").asString
                            else -> "Unknown"
                        }.replace("&quot;", "\"").replace("&amp;", "&")
                        var artistName = "Unknown Artist"
                        if (songObj.has("primaryArtists") && songObj.get("primaryArtists").isJsonPrimitive) {
                            artistName = songObj.get("primaryArtists").asString
                        } else if (songObj.has("artists")) {
                            val artistsElement = songObj.get("artists")
                            if (artistsElement.isJsonObject && artistsElement.asJsonObject.has("primary") && artistsElement.asJsonObject.getAsJsonArray("primary").size() > 0) {
                                artistName = artistsElement.asJsonObject.getAsJsonArray("primary").get(0).asJsonObject.get("name").asString
                            } else if (artistsElement.isJsonPrimitive) {
                                artistName = artistsElement.asString
                            }
                        } else if (songObj.has("subtitle")) {
                            artistName = songObj.get("subtitle").asString
                        }
                        var imageUrl = ""
                        if (songObj.has("image") && songObj.get("image").isJsonArray) {
                            val imgArr = songObj.getAsJsonArray("image")
                            if (imgArr.size() > 0) {
                                val lastImg = imgArr.get(imgArr.size() - 1).asJsonObject
                                imageUrl = if (lastImg.has("url")) lastImg.get("url").asString else if (lastImg.has("link")) lastImg.get("link").asString else ""
                            }
                        } else if (songObj.has("image") && songObj.get("image").isJsonPrimitive) imageUrl = songObj.get("image").asString
                        var downloadUrl = ""
                        if (songObj.has("downloadUrl") && songObj.get("downloadUrl").isJsonArray) {
                            val dlArr = songObj.getAsJsonArray("downloadUrl")
                            if (dlArr.size() > 0) {
                                val lastDl = dlArr.get(dlArr.size() - 1).asJsonObject
                                downloadUrl = if (lastDl.has("url")) lastDl.get("url").asString else if (lastDl.has("link")) lastDl.get("link").asString else ""
                            }
                        } else if (songObj.has("downloadUrl") && songObj.get("downloadUrl").isJsonPrimitive) downloadUrl = songObj.get("downloadUrl").asString
                        else if (songObj.has("media_url")) downloadUrl = songObj.get("media_url").asString
                        if (id.isNotBlank()) parsedSongs.add(JioSaavnSong(id, name, artistName, imageUrl, downloadUrl))
                    } catch (e: Exception) { continue }
                }
            }
        }
    } catch (e: Exception) { e.printStackTrace() }
    return parsedSongs
}
suspend fun fetchPodcastsSafely(query: String): List<com.musicplayer.lytrix.PodcastEpisode> {
    val parsedEpisodes = mutableListOf<com.musicplayer.lytrix.PodcastEpisode>()
    try {
        withContext(Dispatchers.IO) {
            val response = RetrofitInstance.podcastApi.searchPodcastsRaw(query)
            if (response.has("results") && response.get("results").isJsonArray) {
                val resultsArray = response.getAsJsonArray("results")
                for (i in 0 until resultsArray.size()) {
                    try {
                        val epObj = resultsArray.get(i).asJsonObject
                        val id = if (epObj.has("trackId") && !epObj.get("trackId").isJsonNull) epObj.get("trackId").asString else System.currentTimeMillis().toString()
                        val title = if (epObj.has("trackName") && !epObj.get("trackName").isJsonNull) epObj.get("trackName").asString else "Unknown Episode"
                        val audio = if (epObj.has("episodeUrl") && !epObj.get("episodeUrl").isJsonNull) epObj.get("episodeUrl").asString else ""
                        var image = ""
                        if (epObj.has("artworkUrl600") && !epObj.get("artworkUrl600").isJsonNull) image = epObj.get("artworkUrl600").asString
                        else if (epObj.has("artworkUrl100") && !epObj.get("artworkUrl100").isJsonNull) image = epObj.get("artworkUrl100").asString
                        val durationSec = if (epObj.has("trackTimeMillis") && !epObj.get("trackTimeMillis").isJsonNull) epObj.get("trackTimeMillis").asInt / 1000 else 0
                        val publisher = if (epObj.has("collectionName") && !epObj.get("collectionName").isJsonNull) epObj.get("collectionName").asString else "Unknown Publisher"
                        if (audio.isNotBlank()) parsedEpisodes.add(com.musicplayer.lytrix.PodcastEpisode(id, title, audio, image, durationSec, com.musicplayer.lytrix.PodcastInfo(publisher)))
                    } catch (e: Exception) { continue }
                }
            }
        }
    } catch (e: Exception) { e.printStackTrace() }
    return parsedEpisodes
}

// ==========================================

// 🔥 Dedicated Search Screen (ArchiveToon Style)

// ==========================================
@OptIn(ExperimentalMaterial3Api::class)

@Composable

fun SearchScreen(
    viewModel: PlayerViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToArtist: (String) -> Unit,
    onSongMoreClick: (JioSaavnSong) -> Unit,
    onPlaylistMoreClick: (PlayerViewModel.YtMusicTrack) -> Unit
) {
    val context = LocalContext.current
    val bgColor = MaterialTheme.colorScheme.background
    val cardBgColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val accentColor = MaterialTheme.colorScheme.primary
    val onBgColor = MaterialTheme.colorScheme.onBackground
    var searchQuery by remember { mutableStateOf(viewModel.searchQuery.value) }
    var globalSearchSource by remember { mutableStateOf("YouTube Music") }
    var showGlobalSearchFilter by remember { mutableStateOf(false) }
    var currentSearchTab by remember { mutableStateOf(if (viewModel.searchQuery.value.isNotBlank()) "SEARCH_RESULTS" else "YT_SUGGESTIONS") }
    var searchFilterContext by remember { mutableStateOf("All") }
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    BackHandler {
        if (currentSearchTab == "SEARCH_RESULTS" || searchQuery.isNotBlank()) {
            searchQuery = ""
            viewModel.setSearchQuery("")
            currentSearchTab = "YT_SUGGESTIONS"
            keyboardController?.hide()
        } else {
            viewModel.setSearchQuery("")
            keyboardController?.hide()
            onNavigateBack()
        }
    }
    LaunchedEffect(Unit) {
        viewModel.loadSearchHistory(context)
        kotlinx.coroutines.delay(100)

        // 🔥 ম্যাজিক: যদি আগে থেকেই গানের নাম থাকে, তবে কীবোর্ড ওপেন করবে না!
        if (searchQuery.isBlank()) {
            focusRequester.requestFocus()
        }
    }
    Box(modifier = Modifier.fillMaxSize().background(bgColor)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, start = 16.dp, end = 16.dp, bottom = 8.dp)
                    .height(52.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(cardBgColor)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(26.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    viewModel.setSearchQuery("") // 🔥 ব্যাক করলে মেমোরি মুছে ফ্রেশ হয়ে যাবে
                    onNavigateBack()
                }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = onBgColor)
                }
                androidx.compose.foundation.text.BasicTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        viewModel.setSearchQuery(it) // 🔥 যা টাইপ করবেন, সেটা মেমোরিতেও যাবে
                        if (it.trim().length > 1 && globalSearchSource == "YouTube Music") {
                            viewModel.fetchSearchSuggestions(it.trim())
                            currentSearchTab = "YT_SUGGESTIONS"
                        } else if (it.trim().isEmpty()) {
                            currentSearchTab = "YT_SUGGESTIONS"
                        } else if (it.trim().length > 2) {
                            currentSearchTab = "SEARCH_RESULTS"
                        }
                    },
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = onBgColor, fontSize = 16.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(accentColor),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onSearch = {
                            if (searchQuery.trim().isNotEmpty()) {
                                viewModel.saveSearchQuery(context, searchQuery.trim())
                                keyboardController?.hide()
                                currentSearchTab = "SEARCH_RESULTS"
                            }
                        }
                    ),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (searchQuery.isEmpty()) {
                                Text("Search $globalSearchSource...", color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            innerTextField()
                        }
                    }
                )
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = {
                        searchQuery = ""
                        viewModel.setSearchQuery("") // 🔥 X বাটনে ক্লিক করলেও মেমোরি মুছে যাবে
                        currentSearchTab = "YT_SUGGESTIONS"
                    }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.Gray)
                    }
                }
                Box {
                    IconButton(onClick = { showGlobalSearchFilter = true }) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filter", tint = Color.White)
                    }
                    DropdownMenu(expanded = showGlobalSearchFilter, onDismissRequest = { showGlobalSearchFilter = false }, modifier = Modifier.background(Color(0xFF2D2D2D))) {
                        DropdownMenuItem(text = { Text("YouTube Music", color = if (globalSearchSource == "YouTube Music") accentColor else Color.White) }, onClick = { globalSearchSource = "YouTube Music"; showGlobalSearchFilter = false })
                        DropdownMenuItem(text = { Text("JioSaavn", color = if (globalSearchSource == "JioSaavn") accentColor else Color.White) }, onClick = { globalSearchSource = "JioSaavn"; showGlobalSearchFilter = false })
                        DropdownMenuItem(text = { Text("Podcasts", color = if (globalSearchSource == "Podcasts") accentColor else Color.White) }, onClick = { globalSearchSource = "Podcasts"; showGlobalSearchFilter = false })
                        DropdownMenuItem(text = { Text("Audiobooks", color = if (globalSearchSource == "Audiobooks") accentColor else Color.White) }, onClick = { globalSearchSource = "Audiobooks"; showGlobalSearchFilter = false })
                    }
                }
            }
            when (currentSearchTab) {
                "YT_SUGGESTIONS" -> {
                    val suggestions by viewModel.searchSuggestions.collectAsState()
                    val history by viewModel.searchHistory.collectAsState()
                    val filteredHistory = if (searchQuery.isNotBlank()) { history.filter { it.contains(searchQuery, ignoreCase = true) } } else { history }
                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                        if (filteredHistory.isNotEmpty()) {
                            item { Text("Search history", color = Color.Gray, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp, top = 8.dp)) }
                            items(filteredHistory) { text ->
                                Row(modifier = Modifier.fillMaxWidth().clickable {
                                    searchQuery = text; viewModel.saveSearchQuery(context, text); keyboardController?.hide(); currentSearchTab = "SEARCH_RESULTS"
                                }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.History, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(text, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { viewModel.removeSearchQuery(context, text) }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.Gray) }
                                }
                            }
                        }
                        if (suggestions.isNotEmpty() && searchQuery.isNotBlank() && globalSearchSource == "YouTube Music") {
                            item { Text("Suggestions", color = Color.Gray, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp, top = 8.dp)) }
                            items(suggestions) { text ->
                                Row(modifier = Modifier.fillMaxWidth().clickable {
                                    searchQuery = text; viewModel.saveSearchQuery(context, text); keyboardController?.hide(); currentSearchTab = "SEARCH_RESULTS"
                                }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(text, color = Color.White, fontSize = 16.sp)
                                }
                            }
                        }
                    }
                }
                "SEARCH_RESULTS" -> {
                    var localJioResults by remember { mutableStateOf<List<JioSaavnSong>>(emptyList()) }
                    var localPodcastResults by remember { mutableStateOf<List<com.musicplayer.lytrix.PodcastEpisode>>(emptyList()) }
                    var isLocalSearching by remember { mutableStateOf(false) }
                    val innerTubeResults by viewModel.innerTubeSearchResults.collectAsState()
                    LaunchedEffect(searchQuery, globalSearchSource, searchFilterContext) {
                        if (searchQuery.trim().length > 2) {
                            if (globalSearchSource == "YouTube Music") {
                                kotlinx.coroutines.delay(500)
                                viewModel.searchInnerTube(searchQuery.trim(), searchFilterContext)
                            } else {
                                isLocalSearching = true; kotlinx.coroutines.delay(800)
                                if (globalSearchSource == "JioSaavn") localJioResults = fetchSongs(searchQuery.trim(), 30)
                                else if (globalSearchSource == "Podcasts") localPodcastResults = fetchPodcastsSafely(searchQuery.trim())
                                else if (globalSearchSource == "Audiobooks") localPodcastResults = fetchPodcastsSafely(searchQuery.trim() + " audiobook")
                                isLocalSearching = false
                            }
                        }
                    }
                    if (globalSearchSource == "YouTube Music") {
                        val searchChips = listOf("All", "Songs", "Videos", "Albums", "Artists", "Featured Playlists", "Community playlists", "Podcasts", "Episodes", "Profiles")
                        LazyRow(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(searchChips) { chip ->
                                Box(modifier = Modifier.clip(RoundedCornerShape(24.dp)).background(if (searchFilterContext == chip) Color.White else Color.Transparent)
                                    .then(if (searchFilterContext != chip) Modifier.border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(24.dp)) else Modifier)
                                    .clickable { searchFilterContext = chip }.padding(horizontal = 16.dp, vertical = 8.dp)
                                ) { Text(text = chip, color = if (searchFilterContext == chip) Color.Black else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
                            }
                        }
                        if (innerTubeResults.isEmpty()) {
                            LytrixSongListShimmer()
                        } else {
                            val allSearchQueue = remember(innerTubeResults) {
                                filterQueueableYtSongs(innerTubeResults.flatMap { it.tracks })
                            }
                            val filteredTabQueue = remember(innerTubeResults) {
                                filterQueueableYtSongs(innerTubeResults.firstOrNull()?.tracks ?: emptyList())
                            }
                            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomListPadding)) {
                                if (searchFilterContext == "All") {
                                    val ads = BannerCounter("search")
                                    innerTubeResults.forEach { section ->
                                        val isVerticalSection = section.title.equals("Top results", ignoreCase = true) || section.title.equals("Songs", ignoreCase = true) || section.title.equals("Videos", ignoreCase = true) || section.title.equals("Episodes", ignoreCase = true)
                                        if (isVerticalSection) {
                                            item {
                                                Text(text = section.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp))
                                            }
                                            section.tracks.forEach { track ->
                                                rowWithBanner(ads) {
                                                    val kind = classifyYtTrack(track)
                                                    val isArtist = kind == YtTrackKind.ARTIST_OR_PROFILE
                                                    val isContainer = kind == YtTrackKind.ALBUM || kind == YtTrackKind.PLAYLIST || kind == YtTrackKind.PODCAST
                                                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                                        SearchSongListItem(title = track.title, artist = track.artist, playCount = track.playCount, thumbnailUrl = track.thumbnailUrl ?: "", isCircleShape = isArtist, trackId = track.id, viewModel = viewModel,
                                                            onClick = {
                                                                handleSearchYtTrackClick(
                                                                    track = track,
                                                                    queueCandidates = allSearchQueue,
                                                                    viewModel = viewModel,
                                                                    context = context,
                                                                    onNavigateToArtist = onNavigateToArtist
                                                                )
                                                            },
                                                            onMoreClick = {
                                                                if (isContainer) onPlaylistMoreClick(track)
                                                                else {
                                                                    val isEp = kind == YtTrackKind.EPISODE || searchFilterContext == "Episodes" || section.title.equals("Episodes", ignoreCase = true)
                                                                    val albumTag = if (isEp) "Podcast" else ""
                                                                    onSongMoreClick(JioSaavnSong(track.id, track.title, track.artist, track.thumbnailUrl ?: "", albumTag))
                                                                }
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        } else {
                                            rowWithBanner(ads) {
                                                SearchHorizontalSection(title = section.title, tracks = section.tracks, viewModel = viewModel, onNavigateToArtist = onNavigateToArtist, onSongMoreClick = onSongMoreClick, onPlaylistMoreClick = onPlaylistMoreClick, queueTracksOverride = allSearchQueue)
                                            }
                                        }
                                    }
                                } else {
                                    val tracks = innerTubeResults.firstOrNull()?.tracks ?: emptyList()
                                    itemsWithBanner(tracks, "search") { track ->
                                        val kind = classifyYtTrack(track)
                                        val isArtist = kind == YtTrackKind.ARTIST_OR_PROFILE
                                        val isContainer = kind == YtTrackKind.ALBUM || kind == YtTrackKind.PLAYLIST || kind == YtTrackKind.PODCAST
                                        SearchSongListItem(title = track.title, artist = track.artist, playCount = track.playCount, thumbnailUrl = track.thumbnailUrl ?: "", isCircleShape = isArtist, trackId = track.id, viewModel = viewModel,
                                            onClick = {
                                                handleSearchYtTrackClick(
                                                    track = track,
                                                    queueCandidates = filteredTabQueue,
                                                    viewModel = viewModel,
                                                    context = context,
                                                    onNavigateToArtist = onNavigateToArtist
                                                )
                                            },
                                            onMoreClick = {
                                                if (isContainer) onPlaylistMoreClick(track)
                                                else {
                                                    val isEp = kind == YtTrackKind.EPISODE || searchFilterContext == "Episodes"
                                                    val albumTag = if (isEp) "Podcast" else ""
                                                    onSongMoreClick(JioSaavnSong(track.id, track.title, track.artist, track.thumbnailUrl ?: "", albumTag))
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        if (isLocalSearching) {
                            LytrixSongListShimmer()
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomListPadding)) {
                                if (globalSearchSource == "JioSaavn") {
                                    itemsWithBanner(localJioResults, "search") { song ->
                                        PremiumOnlineSongItem(song = song, onBgColor = onBgColor, cardBgColor = cardBgColor, onClick = { viewModel.playOnlineSongWithAd(context, song, localJioResults) }, onMoreClick = { onSongMoreClick(song) })
                                    }
                                } else {
                                    itemsWithBanner(localPodcastResults, "search") { ep ->
                                        val isAbSearch = globalSearchSource == "Audiobooks"
                                        PodcastEpisodeItem(
                                            episode = ep,
                                            isLiked = false,
                                            isDownloadedView = false,
                                            onBgColor = onBgColor,
                                            cardBgColor = cardBgColor,
                                            onClick = {
                                                if (isAbSearch) viewModel.playAudiobookWithAd(context, ep, localPodcastResults)
                                                else viewModel.playPodcastWithAd(context, ep, localPodcastResults)
                                            },
                                            onLikeClick = {},
                                            onDownloadClick = {}
                                        )
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

@Composable

private fun VerticalYtSongListSection(
    title: String,
    tracks: List<PlayerViewModel.YtMusicTrack>,
    viewModel: PlayerViewModel,
    onBgColor: Color,
    context: Context,
    subtitle: String? = null,
    iconUrl: String? = null,
    artistId: String? = null,
    onNavigateToArtist: (String) -> Unit = {},
    onMoreClick: (PlayerViewModel.YtMusicTrack) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp).clickable(enabled = artistId != null) { artistId?.let { onNavigateToArtist(it) } }) {
            if (iconUrl != null) {
                coil.compose.AsyncImage(
                    model = iconUrl,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp).clip(androidx.compose.foundation.shape.CircleShape),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(16.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                if (subtitle != null) {
                    Text(text = subtitle, color = onBgColor.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 4.dp))
                }
                Text(text = title, color = onBgColor, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(modifier = Modifier.width(8.dp))
            androidx.compose.material3.OutlinedButton(
                onClick = { 
                    if (tracks.isNotEmpty()) {
                        viewModel.playLytrixPlaylistWithAd(tracks, 0, context)
                    }
                },
                border = androidx.compose.foundation.BorderStroke(1.dp, onBgColor.copy(alpha = 0.5f)),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(50)
            ) {
                Text(text = "Play all", color = onBgColor, fontSize = 12.sp)
            }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(tracks.chunked(4), key = { chunk -> chunk.joinToString(",") { it.id } }) { chunk ->
                Column(modifier = Modifier.animateItem().width(320.dp)) {
                    chunk.forEach { track ->
                        SearchSongListItem(
                            title = track.title,
                            artist = track.artist,
                            playCount = track.playCount,
                            thumbnailUrl = track.thumbnailUrl ?: "",
                            trackId = track.id,
                            viewModel = viewModel,
                            onClick = {
                                viewModel.playLytrixPlaylistWithAd(tracks, tracks.indexOf(track).takeIf { it >= 0 } ?: 0, context)
                            },
                            onMoreClick = { onMoreClick(track) }
                        )
                    }
                }
            }
        }
    }
}

@Composable

private fun SearchSongListItem(title: String, artist: String, playCount: String, thumbnailUrl: String, isCircleShape: Boolean = false, trackId: String = "", viewModel: PlayerViewModel = androidx.lifecycle.viewmodel.compose.viewModel(), onClick: () -> Unit, onMoreClick: () -> Unit) {
    val subtitleText = if (playCount.isNotBlank()) "$artist • $playCount" else artist
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(24.dp)).bouncePress().clickable(onClick = onClick).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        val curPlayingSong = viewModel.currentSong.collectAsState().value
        val isCurPlaying = viewModel.isPlaying.collectAsState().value
        val videoIdFromThumb = if (thumbnailUrl.contains("ytimg.com/vi/")) {
            thumbnailUrl.substringAfter("vi/").substringBefore("/")
        } else ""
        val isThisSongActive = curPlayingSong != null && (
            (trackId.isNotBlank() && (
                curPlayingSong.originalId == trackId ||
                curPlayingSong.id.toString() == trackId ||
                curPlayingSong.uriString.contains(trackId)
            )) ||
            (videoIdFromThumb.isNotBlank() && (
                curPlayingSong.originalId == videoIdFromThumb ||
                curPlayingSong.uriString.contains(videoIdFromThumb)
            )) ||
            (trackId.isBlank() && curPlayingSong.title.equals(title, ignoreCase = true) && (
                artist.isBlank() || curPlayingSong.artist.isBlank() ||
                curPlayingSong.artist.equals(artist, ignoreCase = true)
            ))
        )
        Box(modifier = Modifier.size(52.dp).clip(if (isCircleShape) CircleShape else RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            AsyncImage(model = thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (isThisSongActive) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    LytrixEqualizerWaves(isPlaying = isCurPlaying, barColor = Color.White, maxHeight = 16.dp, barWidth = 3.dp, spacing = 2.dp)
                }
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtitleText, color = Color.White.copy(alpha=0.6f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(onClick = onMoreClick, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White) }
    }
}

private enum class YtTrackKind {
    SONG_OR_VIDEO,
    EPISODE,
    PODCAST,
    ALBUM,
    ARTIST_OR_PROFILE,
    PLAYLIST
}

private fun classifyYtTrack(track: PlayerViewModel.YtMusicTrack): YtTrackKind {
    val type = track.itemType.trim()
    if (type.equals("Episode", ignoreCase = true)) return YtTrackKind.EPISODE
    if (type.equals("Podcast", ignoreCase = true)) return YtTrackKind.PODCAST
    if (type.equals("Album", ignoreCase = true)) return YtTrackKind.ALBUM
    if (type.equals("Artist", ignoreCase = true) || type.equals("Profile", ignoreCase = true)) {
        return YtTrackKind.ARTIST_OR_PROFILE
    }
    if (type.equals("Playlist", ignoreCase = true)) return YtTrackKind.PLAYLIST
    if (type.equals("Song", ignoreCase = true) || type.equals("Video", ignoreCase = true)) {
        return YtTrackKind.SONG_OR_VIDEO
    }

    return when {
        track.id.startsWith("MPSP") || track.artist.startsWith("Podcast", ignoreCase = true) -> YtTrackKind.PODCAST
        track.artist.contains("Album", ignoreCase = true) -> YtTrackKind.ALBUM
        track.artist.contains("Artist", ignoreCase = true) || track.artist.contains("Profile", ignoreCase = true) -> YtTrackKind.ARTIST_OR_PROFILE
        track.artist.contains("Playlist", ignoreCase = true) || track.artist.contains("Station", ignoreCase = true) -> YtTrackKind.PLAYLIST
        else -> YtTrackKind.SONG_OR_VIDEO
    }
}

private fun filterQueueableYtSongs(tracks: List<PlayerViewModel.YtMusicTrack>): List<PlayerViewModel.YtMusicTrack> {
    return tracks
        .filter {
            val kind = classifyYtTrack(it)
            it.id.isNotBlank() && (kind == YtTrackKind.SONG_OR_VIDEO || kind == YtTrackKind.EPISODE)
        }
        .distinctBy { it.id }
}

private fun handleSearchYtTrackClick(
    track: PlayerViewModel.YtMusicTrack,
    queueCandidates: List<PlayerViewModel.YtMusicTrack>,
    viewModel: PlayerViewModel,
    context: Context,
    onNavigateToArtist: (String) -> Unit
) {
    when (val kind = classifyYtTrack(track)) {
        YtTrackKind.ARTIST_OR_PROFILE -> {
            onNavigateToArtist(track.id)
        }
        YtTrackKind.ALBUM, YtTrackKind.PODCAST, YtTrackKind.PLAYLIST -> {
            com.musicplayer.lytrix.ui.globalActiveYtCover = track.thumbnailUrl ?: ""
            val type = when (kind) {
                YtTrackKind.ALBUM -> "Album"
                YtTrackKind.PODCAST -> "Podcast"
                else -> "Playlist"
            }
            viewModel.loadYtPlaylistDetails(track.id, type, track.title)
        }
        YtTrackKind.EPISODE, YtTrackKind.SONG_OR_VIDEO -> {
            val songsOnly = filterQueueableYtSongs(queueCandidates)
            val safeIndex = songsOnly.indexOfFirst { it.id == track.id }.takeIf { it >= 0 } ?: 0
            viewModel.playLytrixPlaylistWithAd(
                songsOnly.ifEmpty { listOf(track) },
                safeIndex,
                context,
                albumTitle = "YouTube Music"
            )
        }
    }
}

@Composable
private fun SearchHorizontalSection(
    title: String,
    tracks: List<PlayerViewModel.YtMusicTrack>,
    viewModel: PlayerViewModel,
    onNavigateToArtist: (String) -> Unit,
    onSongMoreClick: (JioSaavnSong) -> Unit,
    onPlaylistMoreClick: (PlayerViewModel.YtMusicTrack) -> Unit,
    queueTracksOverride: List<PlayerViewModel.YtMusicTrack>? = null
) {
    val context = LocalContext.current
    val sectionQueue = remember(tracks, queueTracksOverride) {
        queueTracksOverride ?: filterQueueableYtSongs(tracks)
    }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(text = title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(tracks) { track ->
                val kind = classifyYtTrack(track)
                val isArtist = kind == YtTrackKind.ARTIST_OR_PROFILE
                val isPlaylistOrAlbum = kind != YtTrackKind.SONG_OR_VIDEO && kind != YtTrackKind.EPISODE
                val curPlayingSong = viewModel.currentSong.collectAsState().value
                val isCurPlaying = viewModel.isPlaying.collectAsState().value
                val isThisSongActive = !isPlaylistOrAlbum && curPlayingSong != null && (
                    (track.id.isNotBlank() && (
                        curPlayingSong.originalId == track.id ||
                        curPlayingSong.id.toString() == track.id ||
                        curPlayingSong.uriString.contains(track.id)
                    )) ||
                    (curPlayingSong.title.equals(track.title, ignoreCase = true) && curPlayingSong.artist.equals(track.artist, ignoreCase = true))
                )
                val subtitleText = if (track.playCount.isNotBlank()) "${track.artist} • ${track.playCount}" else track.artist
                Column(modifier = Modifier.width(135.dp)) {
                    Box(modifier = Modifier.size(135.dp).clip(if (isArtist) CircleShape else RoundedCornerShape(12.dp)).clickable {
                        handleSearchYtTrackClick(
                            track = track,
                            queueCandidates = sectionQueue,
                            viewModel = viewModel,
                            context = context,
                            onNavigateToArtist = onNavigateToArtist
                        )
                    }) {
                        AsyncImage(model = track.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        if (isThisSongActive) {
                            Box(
                                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
                                contentAlignment = Alignment.Center
                            ) {
                                LytrixEqualizerWaves(isPlaying = isCurPlaying, barColor = Color.White, maxHeight = 24.dp, barWidth = 4.dp, spacing = 3.dp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = track.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(text = subtitleText, color = Color.Gray, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable

private fun MixesSection(items: List<PlayerViewModel.YtMusicTrack>, viewModel: PlayerViewModel, onBgColor: Color) {
    val context = LocalContext.current
    val prefsManager = remember { YoutubePreferencesManager(context) }
    val accountName by prefsManager.accountName.collectAsState(initial = "")
    val initial = if (accountName.isNotBlank()) accountName.take(1).uppercase() else "U"
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1976D2)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initial,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = "Mixes", color = Color.Gray, fontSize = 12.sp)
                Text(text = "Your Liked Music", color = onBgColor, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(items) { item ->
                Column(
                    modifier = Modifier.width(135.dp).clickable {
                        viewModel.ytPlaylistType.value = "Playlist"
                        viewModel.loadYtPlaylistDetails(item.id, "Playlist", item.title)
                    }
                ) {
                    Box(modifier = Modifier.size(135.dp).clip(RoundedCornerShape(12.dp))) {
                        coil.compose.AsyncImage(
                            model = item.thumbnailUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = item.title, color = onBgColor, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(text = item.artist, color = Color.Gray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
@kotlin.OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

private fun SpeedDialSection(items: List<PlayerViewModel.YtMusicTrack>, viewModel: PlayerViewModel, onClick: (PlayerViewModel.YtMusicTrack) -> Unit, onLongClick: (PlayerViewModel.YtMusicTrack) -> Unit) {
    val pinnedIds by viewModel.pinnedIds.collectAsState()
    if (items.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(
            text = "Speed dial", 
            color = Color(0xFFFF8A80), 
            fontSize = 22.sp, 
            fontWeight = FontWeight.ExtraBold, 
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
        )
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val columns = if (isLandscape) 6 else 3
        val rows = if (isLandscape) 2 else 3
        val itemsPerPage = columns * rows
        val totalSlots = items.size + 1 // +1 for randomize dice
        val pageCountCalc = (totalSlots + itemsPerPage - 1) / itemsPerPage
        val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { pageCountCalc })
        var isRandomizing by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 16.dp,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            Column(modifier = Modifier.fillMaxWidth()) {
                for (row in 0 until rows) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (col in 0 until columns) {
                            val itemIndexInPage = row * columns + col
                            val absoluteSlotIndex = (page * itemsPerPage) + itemIndexInPage
                            val isRandomizeSlot = (absoluteSlotIndex == itemsPerPage - 1)
                            if (isRandomizeSlot) {
                                Box(modifier = Modifier.weight(1f).aspectRatio(1f).padding(bottom = 8.dp)) {
                                    RandomizeGridItem(isLoading = isRandomizing) {
                                        if (isRandomizing) return@RandomizeGridItem
                                        scope.launch {
                                            isRandomizing = true
                                            kotlinx.coroutines.delay(1000)
                                            val randomTrack = items.randomOrNull()
                                            if (randomTrack != null) onClick(randomTrack)
                                            isRandomizing = false
                                        }
                                    }
                                }
                            } else {
                                val dataIndex = if (absoluteSlotIndex > (itemsPerPage - 1)) absoluteSlotIndex - 1 else absoluteSlotIndex
                                if (dataIndex < items.size) {
                                    val track = items[dataIndex]
                                    val isEpisode = track.itemType == "Episode"
                                    val isArtist = track.itemType == "Artist" || track.itemType == "Profile" || (!isEpisode && track.artist == "Artist")
                                    val isPodcast = track.itemType == "Podcast" || (!isEpisode && (track.id.startsWith("MPSP") || track.artist.startsWith("Podcast")))
                                    val isAlbum = track.itemType == "Album" || (!isEpisode && !isPodcast && track.artist.contains("Album", ignoreCase = true))
                                    val isPlaylist = track.itemType == "Playlist" || (!isEpisode && !isPodcast && !isArtist && !isAlbum && (track.artist.contains("Playlist", ignoreCase = true) || track.artist.contains("Station", ignoreCase = true)))
                                    val isPlaylistOrAlbum = !isEpisode && (isPlaylist || isAlbum || isPodcast)
                                    Box(
                                        modifier = Modifier.weight(1f).aspectRatio(1f).padding(bottom = 8.dp)
                                            .clip(if (isArtist) CircleShape else RoundedCornerShape(8.dp))
                                            .background(Color(0xFF2A2A2A))
                                            .combinedClickable(
                                                onClick = { onClick(track) },
                                                onLongClick = { onLongClick(track) }
                                            )
                                    ) {
                                        coil.compose.AsyncImage(
                                            model = track.thumbnailUrl, contentDescription = null,
                                            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                                        )
                                        Box(modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)), startY = 50f)))
                                        Text(
                                            text = track.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                            modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 8.dp, end = 24.dp),
                                            maxLines = 2, overflow = TextOverflow.Ellipsis
                                        )
                                        if (isPlaylistOrAlbum) {
                                            androidx.compose.material3.Icon(
                                                imageVector = androidx.compose.material.icons.Icons.Filled.KeyboardArrowRight,
                                                contentDescription = "Playlist",
                                                tint = Color.White,
                                                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).size(16.dp)
                                            )
                                        }
                                        if (pinnedIds.contains(track.id)) {
                                            androidx.compose.material3.Icon(
                                                imageVector = androidx.compose.material.icons.Icons.Filled.PushPin,
                                                contentDescription = "Pinned",
                                                tint = Color.White,
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(8.dp)
                                                    .size(16.dp)
                                            )
                                        }
                                    }
                                } else { Spacer(modifier = Modifier.weight(1f).aspectRatio(1f)) }
                            }
                        }
                    }
                }
            }
        }
        if (pageCountCalc > 1) {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
                repeat(pageCountCalc) { iteration ->
                    val color = if (pagerState.currentPage == iteration) Color(0xFFFF8A80) else Color.Gray.copy(alpha = 0.5f)
                    Box(modifier = Modifier.padding(2.dp).clip(CircleShape).background(color).size(6.dp))
                }
            }
        }
    }
}

@Composable

private fun RandomizeGridItem(isLoading: Boolean, onClick: () -> Unit) {
    val dotOffsetMultiplier by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isLoading) 0f else 1f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 600),
        label = "dotOffset"
    )
    Box(
        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).background(Color(0xFF5A4444)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        val dotColor = Color(0xFFFFCDD2)
        val dotSize = 12.dp
        val padding = 16.dp
        val offset = padding * dotOffsetMultiplier
        Box(modifier = Modifier.align(Alignment.Center).offset(x = -offset, y = -offset).size(dotSize).clip(CircleShape).background(dotColor))
        Box(modifier = Modifier.align(Alignment.Center).offset(x = offset, y = -offset).size(dotSize).clip(CircleShape).background(dotColor))
        Box(modifier = Modifier.align(Alignment.Center).size(dotSize).clip(CircleShape).background(dotColor))
        Box(modifier = Modifier.align(Alignment.Center).offset(x = -offset, y = offset).size(dotSize).clip(CircleShape).background(dotColor))
        Box(modifier = Modifier.align(Alignment.Center).offset(x = offset, y = offset).size(dotSize).clip(CircleShape).background(dotColor))
    }
}

@Composable

private fun YtSongMenuBody(
    isDownloadedLocally: Boolean,
    isPinnedToSpeedDial: Boolean = false,
    showPinToSpeedDial: Boolean = true,
    onPlaylist: () -> Unit,
    onShare: () -> Unit,
    onDetails: () -> Unit,
    onPlayNext: () -> Unit,
    onDownloadToggle: () -> Unit,
    onStartRadio: () -> Unit,
    onGoToArtist: () -> Unit,
    onViewAlbum: (() -> Unit)? = null,
    onPinToSpeedDial: () -> Unit,
    onNotInterested: () -> Unit = {},
    isMetroGuest: Boolean = false,
    onSuggestToHost: () -> Unit = {},
    onExportToStorage: (() -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PremiumMenuSquareButton(icon = Icons.Default.PlaylistAdd, text = "Playlist", modifier = Modifier.weight(1f), onClick = onPlaylist)
            PremiumMenuSquareButton(icon = Icons.Default.Share, text = "Share", modifier = Modifier.weight(1f), onClick = onShare)
            PremiumMenuSquareButton(icon = Icons.Default.Info, text = "Details", modifier = Modifier.weight(1f), onClick = onDetails)
        }
        if (isMetroGuest) {
            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF2A2A2A))) {
                LytrixBottomSheetListItem(icon = Icons.Default.MusicNote, text = "Suggest to host", onClick = onSuggestToHost)
            }
        }
        Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF2A2A2A))) {
            LytrixBottomSheetListItem(icon = Icons.Default.PlaylistPlay, text = "Play next", onClick = onPlayNext)
            LytrixBottomSheetListItem(
                icon = if (isDownloadedLocally) Icons.Default.Delete else Icons.Default.Download,
                text = if (isDownloadedLocally) "Remove from device" else "Download",
                onClick = onDownloadToggle
            )
            if (isDownloadedLocally && onExportToStorage != null) {
                LytrixBottomSheetListItem(
                    icon = Icons.Default.Download,
                    text = "Save to Storage 👑",
                    onClick = onExportToStorage
                )
            }
        }
        Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF2A2A2A))) {
            LytrixBottomSheetListItem(icon = Icons.Default.Radio, text = "Start radio", onClick = onStartRadio)
            LytrixBottomSheetListItem(icon = Icons.Default.Person, text = "Go to Artist", onClick = onGoToArtist)
            if (onViewAlbum != null) {
                LytrixBottomSheetListItem(
                    icon = com.musicplayer.lytrix.ui.icons.LytrixIcons.Outlined.Album,
                    text = "View album",
                    onClick = onViewAlbum
                )
            }
        }
        Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF2A2A2A))) {
            if (showPinToSpeedDial) {
                LytrixBottomSheetListItem(
                    icon = if (isPinnedToSpeedDial) Icons.Default.Close else Icons.Default.PushPin,
                    text = if (isPinnedToSpeedDial) "Unpin from Speed Dial" else "Pin to Speed Dial",
                    onClick = onPinToSpeedDial
                )
            }
            LytrixBottomSheetListItem(
                icon = Icons.Default.Block, 
                text = "Not interested", 
                onClick = onNotInterested
            )
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)

@Composable

private fun YtSongDetailsSheet(song: JioSaavnSong, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val detailsSong = remember(song.id) {
        com.musicplayer.lytrix.model.Song(
            id = song.id.hashCode().toLong(),
            title = song.name,
            artist = song.artist,
            album = "YouTube Music",
            duration = 0L,
            uriString = "https://www.youtube.com/watch?v=" + song.id,
            filePath = "ONLINE_LYTRIX",
            albumArtUriString = song.imageUrl,
            isVideo = false,
            hasLyrics = false,
            originalId = song.id
        )
    }
    com.musicplayer.lytrix.ui.component.LytrixFluidBottomSheet(
        onDismissRequest = onDismiss, 
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow, 
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        MeldDetailsScreen(song = detailsSong, accentColor = Color(0xFF00E676), onDismiss = onDismiss)
    }
}
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

@Composable

private fun CommunityPlaylistSection(
    cards: List<PlayerViewModel.FeaturedPlaylistCard>,
    viewModel: PlayerViewModel,
    context: android.content.Context,
    onBgColor: Color,
    onCardClick: (PlayerViewModel.FeaturedPlaylistCard) -> Unit,
    onCardLongClick: (PlayerViewModel.FeaturedPlaylistCard) -> Unit,
    onSongClick: (PlayerViewModel.YtMusicTrack, PlayerViewModel.FeaturedPlaylistCard) -> Unit,
    onSongLongClick: (PlayerViewModel.YtMusicTrack) -> Unit
) {
    if (cards.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(
            text = "Trending community playlists",
            color = Color(0xFFFF8A80),
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
        )
        androidx.compose.foundation.lazy.LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(cards) { card ->
                CommunityPlaylistCard(
                    card = card,
                    viewModel = viewModel,
                    context = context,
                    onBgColor = onBgColor,
                    onClick = { onCardClick(card) },
                    onLongClick = { onCardLongClick(card) },
                    onSongClick = { song -> onSongClick(song, card) },
                    onSongLongClick = { song -> onSongLongClick(song) }
                )
            }
        }
    }
}
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

@Composable

private fun CommunityPlaylistCard(
    card: PlayerViewModel.FeaturedPlaylistCard,
    viewModel: PlayerViewModel,
    context: android.content.Context,
    onBgColor: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSongClick: (PlayerViewModel.YtMusicTrack) -> Unit,
    onSongLongClick: (PlayerViewModel.YtMusicTrack) -> Unit
) {
    val screenWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp
    val cardWidth = (screenWidth - 40.dp).coerceAtLeast(264.dp).coerceAtMost(520.dp)
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.width(cardWidth)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // Header: Thumbnail and Title
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp)
                    .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            ) {
                coil.compose.AsyncImage(
                    model = card.thumbnailUrl.replace("w226-h226", "w320-h320"), // Increase resolution slightly
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(96.dp).clip(RoundedCornerShape(16.dp))
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = card.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (card.subtitle.isNotBlank()) {
                        Text(
                            text = card.subtitle,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Preview Songs (Always displays first 3 non-hidden songs from pool of 7)
            val notInterestedSongIds by viewModel.notInterestedSongIds.collectAsState()
            val visibleSongs = card.previewSongs.filterNot { notInterestedSongIds.contains(it.id) }.take(3)
            visibleSongs.forEach { song ->
                val curPlayingSong = viewModel.currentSong.collectAsState().value
                val isCurPlaying = viewModel.isPlaying.collectAsState().value
                val isThisSongActive = curPlayingSong != null && (
                    (song.id.isNotBlank() && (
                        curPlayingSong.originalId == song.id ||
                        curPlayingSong.id.toString() == song.id ||
                        curPlayingSong.uriString.contains(song.id)
                    )) ||
                    (curPlayingSong.title.equals(song.title, ignoreCase = true) && curPlayingSong.artist.equals(song.artist, ignoreCase = true))
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { onSongClick(song) },
                            onLongClick = { onSongLongClick(song) }
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(50.dp).clip(RoundedCornerShape(10.dp))) {
                        coil.compose.AsyncImage(
                            model = song.thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (isThisSongActive) {
                            Box(
                                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
                                contentAlignment = Alignment.Center
                            ) {
                                LytrixEqualizerWaves(isPlaying = isCurPlaying, barColor = MaterialTheme.colorScheme.primary, maxHeight = 16.dp, barWidth = 3.dp, spacing = 2.dp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val subtitleText = if (song.playCount.isNotBlank()) "${song.artist} • ${song.playCount}" else song.artist
                        Text(
                            text = subtitleText,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Action Buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {

                // Play Button
                androidx.compose.material3.FilledIconButton(
                    onClick = { 
                        if (card.previewSongs.isNotEmpty()) {
                            viewModel.playLytrixPlaylistWithAd(card.previewSongs, 0, context)
                            Toast.makeText(context, "Playing playlist...", Toast.LENGTH_SHORT).show()
                        } else {
                            onClick()
                        }
                    },
                    modifier = Modifier.size(56.dp),
                    colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = MaterialTheme.colorScheme.onPrimary)
                }
                // Shuffle Button
                androidx.compose.material3.FilledTonalIconButton(
                    onClick = {
                        if (card.previewSongs.isNotEmpty()) {
                            viewModel.playLytrixPlaylistWithAd(card.previewSongs.shuffled(), 0, context)
                            Toast.makeText(context, "Shuffling playlist...", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.size(56.dp),
                    colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Icon(Icons.Default.Shuffle, contentDescription = "Shuffle", tint = MaterialTheme.colorScheme.onSurface)
                }
                // More Button
                androidx.compose.material3.FilledTonalIconButton(
                    onClick = onLongClick,
                    modifier = Modifier.size(56.dp),
                    colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color(0xFF2A2A2A))
                ) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White)
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun HorizontalSaavnLiveSection(
    title: String,
    items: List<com.musicplayer.lytrix.saavn.SaavnCardItem>,
    onBgColor: Color,
    onItemClick: (com.musicplayer.lytrix.saavn.SaavnCardItem) -> Unit,
    onSongMoreClick: (JioSaavnSong) -> Unit,
) {
    if (items.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 22.dp)) {
        Text(
            text = title,
            color = onBgColor,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(items, key = { "${it.type.name}_${it.id}" }) { card ->
                val badgeText = when (card.type) {
                    com.musicplayer.lytrix.saavn.SaavnItemType.ALBUM -> "Album"
                    com.musicplayer.lytrix.saavn.SaavnItemType.PLAYLIST -> "Playlist"
                    com.musicplayer.lytrix.saavn.SaavnItemType.SONG -> null
                }
                Column(
                    modifier = Modifier
                        .width(148.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .combinedClickable(
                            onClick = { onItemClick(card) },
                            onLongClick = {
                                card.song?.let(onSongMoreClick)
                            }
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .size(148.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        AsyncImage(
                            model = card.imageUrl,
                            contentDescription = card.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (badgeText != null) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(8.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black.copy(alpha = 0.68f))
                                    .padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = badgeText,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                        } else if (card.song != null) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.55f))
                                    .clickable { onSongMoreClick(card.song) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = card.title,
                        color = onBgColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (card.subtitle.isNotBlank()) {
                        Text(
                            text = card.subtitle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}