package com.musicplayer.lytrix.ui.cinema

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import java.util.Locale

// ---------------------------------------------------------------------------
// Root
// ---------------------------------------------------------------------------

private sealed interface FeedState {
    data object Loading : FeedState
    data object Error : FeedState
    data class Ready(val feed: CinemaFeed) : FeedState
}

/**
 * Fullscreen, edge-to-edge Cinema module. Call [androidx.activity.ComponentActivity.enableEdgeToEdge]
 * in your Activity so the content can draw behind the system bars.
 */
@Composable
fun CinemaScreen(
    repository: CinemaRepository,
    streamResolver: CinemaStreamResolver,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var isEngineReady by remember { mutableStateOf(CinemaEngineManager.isEngineReady(context)) }
    var reloadTick by remember { mutableIntStateOf(0) }
    var filter by rememberSaveable { mutableStateOf(CinemaFilter.ALL) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var watchlistOnly by rememberSaveable { mutableStateOf(false) }
    var downloadsOpen by rememberSaveable { mutableStateOf(false) }
    var feedState by remember { mutableStateOf<FeedState>(FeedState.Loading) }
    var selectedItem by remember { mutableStateOf<CinemaItem?>(null) }
    var playback by remember { mutableStateOf<PlaybackRequest?>(null) }
    val watchlist = remember { mutableStateMapOf<String, CinemaItem>() }

    LaunchedEffect(Unit) {
        CinemaRemoteConfig.init(context)
        CinemaRemoteConfig.syncRemoteConfig(context)
        GeoSafety.refreshIpCountry(context)
        reloadTick++
    }

    CinemaSystemBars()

    if (!isEngineReady) {
        CinemaEngineDownloadScreen(
            onEngineReady = {
                isEngineReady = true
                reloadTick++
            },
            onExit = onExit,
            modifier = modifier
        )
        return
    }

    LaunchedEffect(reloadTick) {
        feedState = FeedState.Loading
        feedState = cinemaCatching { repository.loadFeed() }
            .fold(onSuccess = { FeedState.Ready(it) }, onFailure = { FeedState.Error })
    }

    val toggleWatchlist = { item: CinemaItem ->
        if (item.id in watchlist) watchlist.remove(item.id) else watchlist.put(item.id, item)
        Unit
    }

    // Lowest priority: leave the module (or leave the downloads/watchlist view first).
    BackHandler {
        when {
            downloadsOpen -> downloadsOpen = false
            watchlistOnly -> watchlistOnly = false
            else -> onExit()
        }
    }

    Box(modifier.fillMaxSize().background(CinemaPalette.background)) {
        when (val state = feedState) {
            FeedState.Loading -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = cinemaAccent(),
            )

            FeedState.Error -> CinemaMessage(
                text = "Couldn't load Cinema right now.",
                modifier = Modifier.align(Alignment.Center),
                actionLabel = "Retry",
                onAction = { reloadTick++ },
            )

            is FeedState.Ready -> {
                CinemaHome(
                    feed = state.feed,
                    filter = filter,
                    watchlist = watchlist,
                    watchlistOnly = watchlistOnly,
                    onFilterChange = { filter = it },
                    onToggleWatchlistView = { watchlistOnly = !watchlistOnly },
                    onOpenDownloads = { downloadsOpen = true },
                    onSearch = { searchOpen = true },
                    onOpen = { selectedItem = it },
                    onToggleWatchlist = toggleWatchlist,
                )
            }
        }

        if (searchOpen) {
            CinemaSearchOverlay(
                repository = repository,
                onClose = { searchOpen = false },
                onItemClick = { selectedItem = it },
            )
        }

        if (downloadsOpen) {
            CinemaDownloadsScreen(
                onBack = { downloadsOpen = false }
            )
        }

        selectedItem?.let { item ->
            key(item.id) {
                val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }
                AnimatedVisibility(
                    visibleState = visibleState,
                    enter = fadeIn(tween(220)) + slideInVertically(tween(300)) { it / 10 },
                    exit = ExitTransition.None,
                ) {
                    CinemaDetailSheet(
                        item = item,
                        repository = repository,
                        servers = streamResolver.servers,
                        inWatchlist = item.id in watchlist,
                        onToggleWatchlist = { toggleWatchlist(item) },
                        onBack = { selectedItem = null },
                        onPlay = { detail, server, season, episode ->
                            playback = PlaybackRequest(detail, server, season, episode)
                        },
                    )
                }
            }
        }

        playback?.let { request ->
            key(request.detail.item.id, request.seasonNumber, request.episode?.id) {
                CinemaPlayerView(
                    request = request,
                    servers = streamResolver.servers,
                    resolver = streamResolver,
                    onClose = { playback = null },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Home
// ---------------------------------------------------------------------------

@Composable
private fun CinemaHome(
    feed: CinemaFeed,
    filter: CinemaFilter,
    watchlist: Map<String, CinemaItem>,
    watchlistOnly: Boolean,
    onFilterChange: (CinemaFilter) -> Unit,
    onToggleWatchlistView: () -> Unit,
    onOpenDownloads: () -> Unit,
    onSearch: () -> Unit,
    onOpen: (CinemaItem) -> Unit,
    onToggleWatchlist: (CinemaItem) -> Unit,
) {
    val accent = cinemaAccent()
    val spotlight = remember(feed, filter) { feed.spotlight.filter { filter.matches(it) } }
    val rows = remember(feed, filter) {
        feed.rows
            .map { row -> row.copy(items = row.items.filter { filter.matches(it) }) }
            .filter { it.items.isNotEmpty() }
    }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = navBottom + 32.dp),
        ) {
            if (watchlistOnly) {
                val saved = watchlist.values.filter { filter.matches(it) }
                item(key = "watchlist-space") { Spacer(Modifier.statusBarsPadding().height(124.dp)) }
                if (saved.isEmpty()) {
                    item(key = "watchlist-empty") {
                        CinemaMessage(
                            text = "Your watchlist is empty.\nTap “+ Watchlist” on any title to save it.",
                            modifier = Modifier.fillMaxWidth().padding(top = 80.dp),
                        )
                    }
                } else {
                    item(key = "watchlist-row") {
                        ContentRow(
                            row = CinemaRow("watchlist", "My Watchlist", saved),
                            onOpen = onOpen,
                        )
                    }
                }
            } else {
                if (spotlight.isNotEmpty()) {
                    item(key = "hero") {
                        HeroCarousel(
                            items = spotlight,
                            watchlist = watchlist,
                            onOpen = onOpen,
                            onToggleWatchlist = onToggleWatchlist,
                        )
                    }
                } else {
                    item(key = "hero-space") { Spacer(Modifier.statusBarsPadding().height(124.dp)) }
                }
                items(rows, key = { it.id }) { row ->
                    ContentRow(row = row, onOpen = onOpen)
                }
                if (rows.isEmpty() && spotlight.isEmpty()) {
                    item(key = "nothing") {
                        CinemaMessage(
                            text = "Nothing here for this filter yet.",
                            modifier = Modifier.fillMaxWidth().padding(top = 80.dp),
                        )
                    }
                }
            }
        }

        // Floating top bar + chips, drawn over the hero and behind-the-status-bar.
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
                .statusBarsPadding(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onSearch) {
                    Icon(Icons.Rounded.Search, contentDescription = "Search", tint = Color.White)
                }

                Text(
                    text = "Cinema",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                )

                IconButton(onClick = onOpenDownloads) {
                    Icon(
                        imageVector = Icons.Rounded.Download,
                        contentDescription = "Downloads",
                        tint = Color.White
                    )
                }
                IconButton(onClick = onToggleWatchlistView) {
                    Icon(
                        imageVector = if (watchlistOnly || watchlist.isNotEmpty()) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                        contentDescription = "Watchlist",
                        tint = if (watchlistOnly) accent else Color.White,
                    )
                }
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 12.dp),
            ) {
                items(CinemaFilter.entries, key = { it.name }) { f ->
                    CinemaChip(label = f.label, selected = f == filter, onClick = { onFilterChange(f) })
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Hero Carousel
// ---------------------------------------------------------------------------

@Composable
private fun HeroCarousel(
    items: List<CinemaItem>,
    watchlist: Map<String, CinemaItem>,
    onOpen: (CinemaItem) -> Unit,
    onToggleWatchlist: (CinemaItem) -> Unit,
) {
    val accent = cinemaAccent()
    val pagerState = rememberPagerState(pageCount = { items.size })

    // Auto-scroll every 6 seconds if multiple heroes
    LaunchedEffect(items.size) {
        if (items.size <= 1) return@LaunchedEffect
        while (true) {
            delay(6_000)
            val next = (pagerState.currentPage + 1) % items.size
            pagerState.animateScrollToPage(next, animationSpec = tween(700))
        }
    }

    Box(Modifier.fillMaxWidth().height(540.dp)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { items[it].id },
        ) { page ->
            val item = items[page]
            HeroPage(
                item = item,
                inWatchlist = item.id in watchlist,
                onOpen = { onOpen(item) },
                onToggleWatchlist = { onToggleWatchlist(item) },
            )
        }
        if (items.size > 1) {
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                repeat(items.size) { index ->
                    val selected = pagerState.currentPage == index
                    val width by animateDpAsState(if (selected) 18.dp else 6.dp, label = "dotWidth")
                    Box(
                        Modifier
                            .height(6.dp)
                            .width(width)
                            .clip(CircleShape)
                            .background(if (selected) accent else Color.White.copy(alpha = 0.3f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroPage(
    item: CinemaItem,
    inWatchlist: Boolean,
    onOpen: () -> Unit,
    onToggleWatchlist: () -> Unit,
) {
    val accent = cinemaAccent()
    Box(Modifier.fillMaxSize().clickable(onClick = onOpen)) {
        CinemaImage(
            url = item.backdropUrl.ifBlank { item.posterUrl },
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.72f)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF0C0C0E)))),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, end = 20.dp, bottom = 40.dp),
        ) {
            Text(
                text = item.title,
                color = Color.White,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RatingBadge(item.rating)
                if (item.year.isNotBlank()) {
                    Text(item.year, color = CinemaPalette.textSecondary, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (item.genres.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item.genres.take(3).forEach { GenreTag(it) }
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onOpen,
                    shape = RoundedCornerShape(percent = 50),
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White),
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Watch Now", fontWeight = FontWeight.SemiBold)
                }
                FilledTonalButton(
                    onClick = onToggleWatchlist,
                    shape = RoundedCornerShape(percent = 50),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color.White.copy(alpha = 0.14f),
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(
                        if (inWatchlist) Icons.Rounded.Check else Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (inWatchlist) "Added" else "Watchlist")
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Rows & Cards
// ---------------------------------------------------------------------------

@Composable
private fun ContentRow(
    row: CinemaRow,
    onOpen: (CinemaItem) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 22.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = row.title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(row.items, key = { "${row.id}-${it.id}" }) { item ->
                PosterCard(item = item, onClick = { onOpen(item) })
            }
        }
    }
}

@Composable
internal fun PosterCard(
    item: CinemaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .width(132.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
                .background(CinemaPalette.surface),
        ) {
            CinemaImage(url = item.posterUrl, modifier = Modifier.fillMaxSize())
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color.Transparent,
                            0.7f to Color.Transparent,
                            1.0f to Color.Black.copy(alpha = 0.65f),
                        ),
                    ),
            )
            RatingBadge(
                rating = item.rating,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = item.title,
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (item.year.isNotBlank()) {
            Text(
                text = item.year,
                color = CinemaPalette.textSecondary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Common components
// ---------------------------------------------------------------------------

@Composable
fun RatingBadge(
    rating: Double,
    modifier: Modifier = Modifier,
) {
    if (rating <= 0.0) return
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = Color.Black.copy(alpha = 0.6f),
    ) {
        Row(
            Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Icon(Icons.Rounded.Star, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.size(11.dp))
            Text(
                text = String.format(Locale.US, "%.1f", rating),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun GenreTag(name: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color.White.copy(alpha = 0.12f),
    ) {
        Text(
            text = name,
            color = Color.White.copy(alpha = 0.9f),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
internal fun CinemaChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accent = cinemaAccent()
    val bg by animateColorAsState(if (selected) accent else Color.White.copy(alpha = 0.08f), label = "chipBg")
    val fg = if (selected) Color.White else Color.White.copy(alpha = 0.78f)
    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = bg,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            color = fg,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}

@Composable
internal fun CinemaImage(
    url: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    if (url.isNullOrBlank()) {
        Box(modifier.background(CinemaPalette.surface))
        return
    }
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(url)
            .crossfade(true)
            .build(),
        contentDescription = null,
        contentScale = contentScale,
        modifier = modifier,
    )
}

@Composable
internal fun CinemaMessage(
    text: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val accent = cinemaAccent()
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = text,
            color = CinemaPalette.textSecondary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = accent),
                shape = RoundedCornerShape(percent = 50),
            ) {
                Text(actionLabel, color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Sets system bars transparent + light-icons-false (always light icons over dark cinema bg). */
@Composable
private fun CinemaSystemBars() {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(Unit) {
        val window = (view.context.findActivity())?.window
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        }
        onDispose { }
    }
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
