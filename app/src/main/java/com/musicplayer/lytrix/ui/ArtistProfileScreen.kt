@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
)

package com.musicplayer.lytrix.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalToggleButton
import androidx.compose.material3.FilledTonalToggleButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.carousel.CarouselDefaults
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.musicplayer.lytrix.LytrixSongItem
import com.musicplayer.lytrix.innertube.models.AlbumItem
import com.musicplayer.lytrix.innertube.models.ArtistItem
import com.musicplayer.lytrix.innertube.models.PlaylistItem
import com.musicplayer.lytrix.innertube.models.SongItem
import com.musicplayer.lytrix.innertube.models.VideoItem
import com.musicplayer.lytrix.innertube.models.YTItem
import com.musicplayer.lytrix.innertube.pages.ArtistSection
import com.musicplayer.lytrix.innertube.pages.ArtistSectionLayout
import com.musicplayer.lytrix.model.JioSaavnSong
import com.musicplayer.lytrix.spotify.canvas.AppleMusicAnimatedArtwork
import com.musicplayer.lytrix.ui.component.ArtistMotionVideoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Exact MetroList ArtistActionWidths.kt pixel-budget layout calculation
 * so Subscribe + Radio + Shuffle + Play All fit cleanly on one row without overflow.
 */
private data class MetroArtistActionWidths(
    val subscribeWidth: Dp,
    val separation: Dp,
    val radioWidth: Dp,
    val shuffleWidth: Dp,
    val playWidth: Dp,
    val spacing: Dp,
    val labelRadio: Boolean,
)

private fun Density.computeMetroArtistActionWidths(
    availableWidth: Int,
    subscribeLabelWidth: Int,
    radioLabelWidth: Int,
    showSubscribe: Boolean,
): MetroArtistActionWidths {
    val gapCount = if (showSubscribe) 4 else 2
    val spacing = minOf(4.dp.roundToPx(), availableWidth / gapCount)
    val contentWidth = availableWidth - spacing * gapCount
    val iconWidth = minOf(48.dp.roundToPx(), contentWidth / if (showSubscribe) 4 else 3)
    val minimumPlayWidth = minOf(64.dp.roundToPx(), contentWidth - iconWidth * if (showSubscribe) 3 else 2)
    val minimumPlaybackWidth = iconWidth * 2 + minimumPlayWidth
    val minimumSeparation = if (showSubscribe) 20.dp.roundToPx() else 0
    val subscribeWidth = if (showSubscribe) {
        minOf(
            subscribeLabelWidth + 28.dp.roundToPx(),
            (contentWidth - minimumPlaybackWidth - minimumSeparation).coerceAtLeast(iconWidth),
        )
    } else {
        0
    }
    val playbackWidth = contentWidth - subscribeWidth - minOf(minimumSeparation, contentWidth - subscribeWidth - minimumPlaybackWidth)
    val labeledRadioWidth = radioLabelWidth + 56.dp.roundToPx()
    val labelRadio = playbackWidth >= labeledRadioWidth * 2 + 16.dp.roundToPx() + iconWidth
    val radioWidth = if (labelRadio) labeledRadioWidth else iconWidth
    val playWidth = minOf(playbackWidth - radioWidth - iconWidth, maxOf(140.dp.roundToPx(), radioWidth + 16.dp.roundToPx()))
    val separation = if (showSubscribe) (contentWidth - subscribeWidth - radioWidth - iconWidth - playWidth).coerceAtLeast(0) else 0
    return MetroArtistActionWidths(
        subscribeWidth = subscribeWidth.toDp(),
        separation = separation.toDp(),
        radioWidth = radioWidth.toDp(),
        shuffleWidth = iconWidth.toDp(),
        playWidth = playWidth.toDp(),
        spacing = spacing.toDp(),
        labelRadio = labelRadio,
    )
}

@Composable
fun ArtistProfileScreen(
    viewModel: PlayerViewModel,
    artistName: String,
    onNavigateBack: () -> Unit,
    onNavigateToItems: (String, String) -> Unit,
    onNavigateToArtist: (String) -> Unit = {},
) {
    val context = LocalContext.current
    var showSongBottomSheet by remember { mutableStateOf<JioSaavnSong?>(null) }
    var showYtPlaylistBottomSheet by remember { mutableStateOf<YTItem?>(null) }
    var showAboutOverlay by remember { mutableStateOf(false) }
    var showTopMenu by remember { mutableStateOf(false) }

    val artistPage by viewModel.artistPage.collectAsState()
    val isLoading by viewModel.isArtistLoading.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()

    val artworkPrefs = remember { com.musicplayer.lytrix.artwork.ArtworkPreferencesManager.getInstance(context) }
    val isAppleArtistImagesEnabled by artworkPrefs.appleArtistImages.collectAsState()
    val isAppleArtistVideosEnabled by artworkPrefs.appleArtistVideos.collectAsState()
    val appleArtistVideoResolution by artworkPrefs.appleArtistVideoResolution.collectAsState()

    var artistMotionUrl by remember(artistName) { mutableStateOf<String?>(null) }
    var appleArtistImageUrl by remember(artistName) { mutableStateOf<String?>(null) }

    LaunchedEffect(artistName, isAppleArtistVideosEnabled, appleArtistVideoResolution) {
        if (isAppleArtistVideosEnabled && artistName.isNotBlank()) {
            artistMotionUrl = withContext(Dispatchers.IO) {
                AppleMusicAnimatedArtwork.getArtistMotionUrl(context, artistName, appleArtistVideoResolution)
            }
        } else {
            artistMotionUrl = null
        }
    }

    LaunchedEffect(artistName, isAppleArtistImagesEnabled) {
        if (isAppleArtistImagesEnabled && artistName.isNotBlank()) {
            appleArtistImageUrl = withContext(Dispatchers.IO) {
                AppleMusicAnimatedArtwork.getArtistImageUrl(context, artistName)
            }
        } else {
            appleArtistImageUrl = null
        }
    }

    LaunchedEffect(artistName) {
        viewModel.clearArtistPage()
        viewModel.loadArtistPage(artistName, context)
    }

    val surfaceColor = MaterialTheme.colorScheme.surface

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = surfaceColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isLoading && artistPage == null) {
                LytrixArtistProfileShimmer()
            } else if (artistPage != null) {
                val page = artistPage!!
                val isAudiobookAuthor = viewModel.currentMediaCategory.value == "AUDIOBOOK" || page.artist.id.startsWith("AUDIOBOOK_")
                val isPodcastChannel = !isAudiobookAuthor && (page.artist.id.startsWith("UC") && (viewModel.currentMediaCategory.value == "PODCAST" || page.artist.title.contains("podcast", ignoreCase = true) || page.sections.any { it.title.contains("Podcast", ignoreCase = true) || it.title.contains("Episode", ignoreCase = true) || it.title.contains("Show", ignoreCase = true) }))
                var isSubscribed by remember(page.artist.id, isPodcastChannel, isAudiobookAuthor) {
                    mutableStateOf(
                        if (isAudiobookAuthor) viewModel.isAudiobookAuthorSubscribed(context, page.artist.id)
                        else if (isPodcastChannel) viewModel.isPodcastChannelSubscribed(context, page.artist.id)
                        else viewModel.isArtistSubscribed(context, page.artist.id)
                    )
                }
                val listState = rememberLazyListState()
                val titleBottom = remember { mutableFloatStateOf(Float.POSITIVE_INFINITY) }
                val appBarBottom = remember { mutableFloatStateOf(0f) }

                val heroThumb = remember(page.artist.thumbnail, appleArtistImageUrl, isAppleArtistImagesEnabled) {
                    val appleImg = if (isAppleArtistImagesEnabled) appleArtistImageUrl else null
                    appleImg ?: page.artist.thumbnail?.let { raw ->
                        val base = if (raw.startsWith("//")) "https:$raw" else raw
                        when {
                            base.contains("=w") && base.contains("-h") ->
                                base.replace(Regex("=w\\d+-h\\d+.*"), "=w1200-h1200-l90-rj")
                            base.contains("=s") && !base.contains("?") ->
                                base.replace(Regex("=s\\d+.*"), "=s1200-l90-rj")
                            else -> base
                        }
                    } ?: "https://images.unsplash.com/photo-1470225620780-dba8ba36b745"
                }

                val topSongs = remember(page.sections) {
                    page.sections
                        .firstOrNull { it.layout == ArtistSectionLayout.LIST || it.items.all { item -> item is SongItem } }
                        ?.items
                        ?.filterIsInstance<SongItem>()
                        .orEmpty()
                }

                val playArtistQueue: (List<SongItem>, Int) -> Unit = { songs, startIdx ->
                    if (songs.isNotEmpty()) {
                        val ytTracks = songs.map { s ->
                            val cleanArtist = s.artists.joinToString(", ") { it.name }.ifBlank { page.artist.title }
                            PlayerViewModel.YtMusicTrack(
                                id = s.id,
                                title = s.title,
                                artist = cleanArtist,
                                thumbnailUrl = s.thumbnail.orEmpty(),
                                playCount = s.viewCountText.orEmpty(),
                                itemType = "Song",
                            )
                        }
                        viewModel.playLytrixPlaylistWithAd(ytTracks, startIdx.coerceIn(0, ytTracks.lastIndex), context)
                    }
                }

                val openSectionBrowse: (ArtistSection) -> Unit = { section ->
                    section.moreEndpoint?.let { ep ->
                        val safeBrowseId = java.net.URLEncoder.encode(ep.browseId ?: "", "UTF-8")
                        val safeParams = if (ep.params.isNullOrBlank()) "null" else java.net.URLEncoder.encode(ep.params, "UTF-8")
                        onNavigateToItems(safeBrowseId, safeParams)
                    }
                }

                val handleArtistItemClick: (YTItem, List<YTItem>) -> Unit = { item, sectionItems ->
                    when (item) {
                        is ArtistItem -> onNavigateToArtist(item.id)
                        is AlbumItem -> {
                            globalActiveYtCover = item.thumbnail.orEmpty()
                            viewModel.ytPlaylistType.value = "Album"
                            viewModel.loadYtPlaylistDetails(item.browseId, "Album", item.title)
                        }
                        is PlaylistItem -> {
                            globalActiveYtCover = item.thumbnail.orEmpty()
                            viewModel.ytPlaylistType.value = "Playlist"
                            viewModel.loadYtPlaylistDetails(item.id, "Playlist", item.title)
                        }
                        is SongItem -> {
                            val songsInSec = sectionItems.filterIsInstance<SongItem>()
                            val idx = songsInSec.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
                            playArtistQueue(songsInSec.ifEmpty { listOf(item) }, idx)
                        }
                        is VideoItem -> {
                            val author = item.authors?.joinToString(", ") { it.name }?.ifBlank { page.artist.title } ?: page.artist.title
                            viewModel.playLytrixSong(
                                LytrixSongItem(
                                    videoId = item.id,
                                    title = item.title,
                                    artist = author,
                                    duration = "",
                                    thumbnailUrl = item.thumbnail.orEmpty(),
                                ),
                                context,
                            )
                        }
                        else -> Unit
                    }
                }

                val handleArtistItemLongClick: (YTItem) -> Unit = { item ->
                    val resolvedAlbum = if (isAudiobookAuthor) "Audiobook" else if (isPodcastChannel) "Podcast" else null
                    when (item) {
                        is PlaylistItem, is AlbumItem -> showYtPlaylistBottomSheet = item
                        is SongItem -> {
                            val cleanArtist = item.artists.joinToString(", ") { it.name }.ifBlank { page.artist.title }
                            showSongBottomSheet = JioSaavnSong(
                                id = item.id,
                                name = item.title,
                                artist = cleanArtist,
                                imageUrl = item.thumbnail.orEmpty(),
                                downloadUrl = "",
                                albumId = item.album?.id,
                                albumName = resolvedAlbum ?: item.album?.name,
                            )
                        }
                        is VideoItem -> {
                            val author = item.authors?.joinToString(", ") { it.name }?.ifBlank { page.artist.title } ?: page.artist.title
                            showSongBottomSheet = JioSaavnSong(
                                id = item.id,
                                name = item.title,
                                artist = author,
                                imageUrl = item.thumbnail.orEmpty(),
                                downloadUrl = "",
                                albumName = resolvedAlbum,
                            )
                        }
                        else -> Unit
                    }
                }

                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val headerImageHeight = minOf(maxWidth, 380.dp)

                    // 1. Pinned Hero Artwork + Apple Music Motion Video (stays fixed while sheet slides over it)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(headerImageHeight)
                            .clipToBounds(),
                    ) {
                        AsyncImage(
                            model = heroThumb,
                            contentDescription = page.artist.title,
                            contentScale = ContentScale.Crop,
                            alignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (isAppleArtistVideosEnabled && !artistMotionUrl.isNullOrBlank()) {
                            ArtistMotionVideoPlayer(
                                videoUrl = artistMotionUrl!!,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        // Exact MetroList square hero vertical fade into surface
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        0f to Color.Black.copy(alpha = 0.28f),
                                        0.2f to Color.Transparent,
                                        0.76f to Color.Transparent,
                                        0.92f to surfaceColor.copy(alpha = 0.72f),
                                        1f to surfaceColor,
                                    )
                                ),
                        )
                    }

                    // 2. Scrollable Content Sheet (MetroList 28.dp sliding rounded sheet via drawBehind)
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .drawBehind {
                                val coverTop = if (listState.firstVisibleItemIndex == 0) {
                                    headerImageHeight.toPx() - listState.firstVisibleItemScrollOffset
                                } else {
                                    0f
                                }
                                val top = coverTop.coerceAtLeast(0f)
                                val radius = minOf(28.dp.toPx(), top)
                                drawRoundRect(
                                    color = surfaceColor,
                                    topLeft = Offset(0f, top),
                                    size = Size(size.width, size.height - top + radius),
                                    cornerRadius = CornerRadius(radius),
                                )
                            },
                        contentPadding = PaddingValues(bottom = 140.dp),
                    ) {
                        // Header gap + Artist Title + Stats + Expressive Action ButtonGroup
                        item(key = "metro_artist_header") {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Spacer(modifier = Modifier.fillMaxWidth().height(headerImageHeight))
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 16.dp),
                                ) {
                                    Text(
                                        text = page.artist.title,
                                        style = MaterialTheme.typography.headlineLargeEmphasized.copy(
                                            fontSize = 30.sp,
                                            fontWeight = FontWeight.Bold,
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.onGloballyPositioned {
                                            titleBottom.floatValue = it.boundsInWindow().bottom
                                        },
                                    )
                                    val subText = page.artist.subscriberCountText?.takeIf { it.isNotBlank() }?.let {
                                        if (it.contains("subscriber", ignoreCase = true)) it else "$it subscribers"
                                    }
                                    val monthlyText = page.artist.monthlyListenerCountText?.takeIf { it.isNotBlank() }
                                    if (!subText.isNullOrBlank()) {
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = subText,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (!monthlyText.isNullOrBlank()) {
                                        Text(
                                            text = monthlyText,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }

                                    Spacer(Modifier.height(16.dp))

                                    // Exact MetroList Expressive Action Row (Subscribe + Radio + Shuffle + Play)
                                    MetroArtistActionRow(
                                        isSubscribed = isSubscribed,
                                        onSubscribeClick = {
                                            if (isAudiobookAuthor) {
                                                viewModel.toggleAudiobookAuthorSubscribe(context, page.artist.id, page.artist.title, page.artist.thumbnail)
                                            } else if (isPodcastChannel) {
                                                viewModel.togglePodcastChannelSubscribe(context, page.artist.id, page.artist.title, page.artist.thumbnail)
                                            } else {
                                                viewModel.toggleArtistSubscribe(context, page.artist.id, page.artist.title, page.artist.thumbnail)
                                            }
                                            isSubscribed = !isSubscribed
                                            CoroutineScope(Dispatchers.IO).launch {
                                                delay(2000)
                                                performSilentSync(context)
                                            }
                                        },
                                        onRadioClick = {
                                            if (topSongs.isNotEmpty()) {
                                                playArtistQueue(topSongs.shuffled(), 0)
                                            }
                                        },
                                        onShuffleClick = {
                                            if (topSongs.isNotEmpty()) {
                                                playArtistQueue(topSongs.shuffled(), 0)
                                            }
                                        },
                                        onPlayAllClick = {
                                            if (topSongs.isNotEmpty()) {
                                                playArtistQueue(topSongs, 0)
                                            }
                                        },
                                    )
                                }
                            }
                        }

                        // Exact MetroList artistSectionsWithLatestRelease: auto-picks highest year release (2026, 2027, ...)
                        val latestReleaseAlbum = page.sections
                            .asSequence()
                            .flatMap { it.items.asSequence() }
                            .filterIsInstance<AlbumItem>()
                            .filter { album ->
                                album.artists.isNullOrEmpty() ||
                                    album.artists.any { a ->
                                        a.id == page.artist.id || a.name.equals(page.artist.title, ignoreCase = true)
                                    }
                            }
                            .maxWithOrNull(compareBy<AlbumItem> { it.year ?: 0 })

                        if (latestReleaseAlbum != null) {
                            item(key = "metro_artist_latest_release") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp, bottom = 8.dp),
                                ) {
                                    MetroSectionTitle(
                                        title = "Latest release",
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    MetroArtistLatestReleaseCard(
                                        album = latestReleaseAlbum,
                                        onClick = { handleArtistItemClick(latestReleaseAlbum, listOf(latestReleaseAlbum)) },
                                        onLongClick = { handleArtistItemLongClick(latestReleaseAlbum) },
                                    )
                                }
                            }
                        }

                        // Artist Sections (Top Tracks, Albums, Singles, Videos, Live performances, Fans might also like)
                        page.sections.forEachIndexed { secIdx, section ->
                            if (section.items.isEmpty()) return@forEachIndexed
                            if (section.title.contains("latest", ignoreCase = true) && section.items.singleOrNull() is AlbumItem) {
                                return@forEachIndexed
                            }
                            item(key = "artist_sec_${secIdx}_${section.title}") {
                                val hasNonVideoContainers = section.items.any { it is AlbumItem || it is PlaylistItem || it is ArtistItem }
                                val isVideoSection = !hasNonVideoContainers && (
                                    section.title.contains("video", ignoreCase = true) ||
                                    section.title.contains("live", ignoreCase = true) ||
                                    section.title.contains("performance", ignoreCase = true) ||
                                    (section.items.isNotEmpty() && section.items.all { it is VideoItem }) ||
                                    section.items.any { item ->
                                        (item as? SongItem)?.let { s ->
                                            val w = s.thumbnailWidth ?: 0
                                            val h = s.thumbnailHeight ?: 0
                                            w > 0 && h > 0 && w.toFloat() / h.toFloat() > 1.25f
                                        } == true
                                    }
                                )
                                val isSongListSection = section.layout == ArtistSectionLayout.LIST && !isVideoSection

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 14.dp, bottom = 6.dp),
                                ) {
                                    MetroSectionTitle(
                                        title = if (isSongListSection && section.title.equals("Songs", ignoreCase = true)) "Top tracks" else section.title,
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        onClick = section.moreEndpoint?.let { { openSectionBrowse(section) } },
                                    )
                                    Spacer(Modifier.height(10.dp))

                                    when {
                                        isVideoSection -> {
                                            val videoTracks = remember(section.items) {
                                                section.items.mapNotNull { item ->
                                                    when (item) {
                                                        is VideoItem -> PlayerViewModel.YtMusicTrack(
                                                            id = item.id,
                                                            title = item.title,
                                                            artist = item.authors?.joinToString(", ") { it.name }?.ifBlank { page.artist.title } ?: page.artist.title,
                                                            thumbnailUrl = item.thumbnail.orEmpty(),
                                                            playCount = item.viewCountText.orEmpty(),
                                                            itemType = "Video",
                                                        )
                                                        is SongItem -> PlayerViewModel.YtMusicTrack(
                                                            id = item.id,
                                                            title = item.title,
                                                            artist = item.artists.joinToString(", ") { it.name }.ifBlank { page.artist.title },
                                                            thumbnailUrl = item.thumbnail.orEmpty(),
                                                            playCount = item.viewCountText.orEmpty(),
                                                            itemType = "Video",
                                                        )
                                                        else -> null
                                                    }
                                                }
                                            }
                                            MetroVideoMultiBrowseCarousel(
                                                tracks = videoTracks,
                                                onTrackClick = { _, idx ->
                                                    section.items.getOrNull(idx)?.let { handleArtistItemClick(it, section.items) }
                                                },
                                                onTrackLongClick = { track ->
                                                    section.items.firstOrNull {
                                                        (it as? VideoItem)?.id == track.id || (it as? SongItem)?.id == track.id
                                                    }?.let(handleArtistItemLongClick)
                                                },
                                            )
                                        }
                                        isSongListSection -> {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp),
                                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                            ) {
                                                section.items.take(5).forEach { item ->
                                                    MetroArtistSongRow(
                                                        item = item,
                                                        fallbackArtist = page.artist.title,
                                                        currentSongId = currentSong?.originalId ?: currentSong?.id?.toString(),
                                                        isPlaying = isPlaying,
                                                        onClick = { handleArtistItemClick(item, section.items) },
                                                        onLongClick = { handleArtistItemLongClick(item) },
                                                    )
                                                }
                                            }
                                        }
                                        else -> {
                                            MetroArtistCarouselShelf(
                                                items = section.items,
                                                fallbackArtist = page.artist.title,
                                                onItemClick = { item -> handleArtistItemClick(item, section.items) },
                                                onItemLongClick = handleArtistItemLongClick,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // MetroList About Photo Card (28.dp rounded hero photo card)
                        if (!page.description.isNullOrBlank()) {
                            item(key = "metro_artist_about_card") {
                                MetroArtistAboutCard(
                                    artistTitle = page.artist.title,
                                    monthlyListeners = page.artist.monthlyListenerCountText,
                                    description = page.description!!,
                                    imageUrl = heroThumb,
                                    onClick = { showAboutOverlay = true },
                                )
                            }
                        }
                    }

                    // 3. Collapsing Floating TopAppBar (exact MetroList behavior)
                    val showAppBar by remember(listState) {
                        derivedStateOf {
                            listState.firstVisibleItemIndex > 0 || titleBottom.floatValue <= appBarBottom.floatValue
                        }
                    }
                    val progress by animateFloatAsState(
                        targetValue = if (showAppBar) 1f else 0f,
                        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                        label = "artistAppBarAlpha",
                    )
                    val iconColor = lerp(Color.White, MaterialTheme.colorScheme.onSurface, progress)
                    val buttonHaloModifier = Modifier.drawBehind {
                        drawCircle(Color.Black, alpha = 0.4f * (1f - progress))
                    }

                    TopAppBar(
                        title = {
                            Text(
                                text = page.artist.title,
                                style = MaterialTheme.typography.titleLargeEmphasized,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.graphicsLayer { alpha = progress },
                            )
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = onNavigateBack,
                                modifier = buttonHaloModifier,
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Back",
                                    tint = iconColor,
                                )
                            }
                        },
                        actions = {
                            Box {
                                IconButton(
                                    onClick = { showTopMenu = true },
                                    modifier = buttonHaloModifier,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.MoreVert,
                                        contentDescription = "More options",
                                        tint = iconColor,
                                    )
                                }
                                DropdownMenu(
                                    expanded = showTopMenu,
                                    onDismissRequest = { showTopMenu = false },
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Share artist") },
                                        leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                                        onClick = {
                                            showTopMenu = false
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(
                                                    Intent.EXTRA_TEXT,
                                                    "Listen to ${page.artist.title} on YouTube Music: https://music.youtube.com/channel/${page.artist.id}",
                                                )
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Share Artist"))
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Refresh") },
                                        leadingIcon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                                        onClick = {
                                            showTopMenu = false
                                            viewModel.loadArtistPage(artistName, context)
                                        },
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .onGloballyPositioned { appBarBottom.floatValue = it.boundsInWindow().bottom }
                            .drawBehind { drawRect(surfaceColor, alpha = progress) },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                        windowInsets = WindowInsets.statusBars,
                    )
                }

                // Full-screen MetroList About Artist Overlay
                if (showAboutOverlay) {
                    MetroAboutArtistOverlay(
                        artistTitle = page.artist.title,
                        subscribers = page.artist.subscriberCountText,
                        monthlyListeners = page.artist.monthlyListenerCountText,
                        description = page.description.orEmpty(),
                        imageUrl = heroThumb,
                        onBack = { showAboutOverlay = false },
                    )
                }
            }

            showSongBottomSheet?.let { song ->
                SharedSongBottomSheet(
                    context = context,
                    song = song,
                    viewModel = viewModel,
                    onDismiss = { showSongBottomSheet = null },
                    onNavigateToArtist = onNavigateToArtist,
                )
            }

            showYtPlaylistBottomSheet?.let { item ->
                val id = when (item) {
                    is AlbumItem -> item.browseId
                    is PlaylistItem -> item.id
                    else -> ""
                }
                val type = if (item is AlbumItem) "Album" else "Playlist"
                val title = when (item) {
                    is AlbumItem -> item.title
                    is PlaylistItem -> item.title
                    else -> ""
                }
                val artist = when (item) {
                    is AlbumItem -> item.artists?.joinToString(", ") { it.name }.orEmpty()
                    is PlaylistItem -> item.author?.name.orEmpty()
                    else -> ""
                }
                val thumb = when (item) {
                    is AlbumItem -> item.thumbnail
                    is PlaylistItem -> item.thumbnail
                    else -> ""
                }
                SharedYtPlaylistBottomSheet(
                    context = context,
                    playlistId = id,
                    playlistTitle = title,
                    playlistArtist = artist,
                    playlistThumbnail = thumb.orEmpty(),
                    playlistType = type,
                    viewModel = viewModel,
                    onDismiss = { showYtPlaylistBottomSheet = null },
                )
            }
        }
    }
}

@Composable
private fun MetroArtistActionRow(
    isSubscribed: Boolean,
    onSubscribeClick: () -> Unit,
    onRadioClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onPlayAllClick: () -> Unit,
) {
    val rowHeight = 48.dp
    val subscribeLabel = if (isSubscribed) "Subscribed" else "Subscribe"
    val radioLabel = "Radio"
    val shuffleLabel = "Shuffle"
    val textStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val tonalColors = FilledTonalToggleButtonDefaults.colors()
    val subscribeColor by animateColorAsState(
        targetValue = if (isSubscribed) MaterialTheme.colorScheme.primary else tonalColors.containerColor,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "subBg",
    )
    val subscribeContentColor by animateColorAsState(
        targetValue = if (isSubscribed) MaterialTheme.colorScheme.onPrimary else tonalColors.contentColor,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "subFg",
    )

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val widths = with(density) {
            computeMetroArtistActionWidths(
                availableWidth = constraints.maxWidth,
                subscribeLabelWidth = textMeasurer.measure(subscribeLabel, textStyle).size.width,
                radioLabelWidth = textMeasurer.measure(radioLabel, textStyle).size.width,
                showSubscribe = true,
            )
        }
        ButtonGroup(
            overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) },
            expandedRatio = 1f,
            horizontalArrangement = Arrangement.spacedBy(widths.spacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            customItem(
                buttonGroupContent = {
                    val interactionSource = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .animateWidth(interactionSource)
                            .width(widths.subscribeWidth)
                            .height(rowHeight),
                    ) {
                        FilledTonalToggleButton(
                            checked = isSubscribed,
                            onCheckedChange = { onSubscribeClick() },
                            interactionSource = interactionSource,
                            modifier = Modifier.fillMaxSize(),
                            shapes = FilledTonalToggleButtonDefaults.shapesFor(rowHeight).copy(
                                shape = MaterialTheme.shapes.small,
                                checkedShape = MaterialTheme.shapes.medium,
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            colors = tonalColors.copy(
                                containerColor = subscribeColor,
                                contentColor = subscribeContentColor,
                                checkedContainerColor = subscribeColor,
                                checkedContentColor = subscribeContentColor,
                            ),
                        ) {
                            Text(subscribeLabel, style = textStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                },
                menuContent = { DropdownMenuItem(text = { Text(subscribeLabel) }, onClick = onSubscribeClick) },
            )
            customItem(
                buttonGroupContent = { Spacer(Modifier.width(widths.separation)) },
                menuContent = {},
            )
            customItem(
                buttonGroupContent = {
                    val interactionSource = remember { MutableInteractionSource() }
                    val shapes = ButtonGroupDefaults.connectedLeadingButtonShapes()
                    FilledTonalButton(
                        onClick = onRadioClick,
                        interactionSource = interactionSource,
                        modifier = Modifier
                            .animateWidth(interactionSource)
                            .width(widths.radioWidth)
                            .height(rowHeight),
                        shapes = ButtonDefaults.shapes(shape = shapes.shape, pressedShape = shapes.pressedShape),
                        contentPadding = PaddingValues(horizontal = if (widths.labelRadio) 12.dp else 0.dp),
                    ) {
                        Icon(Icons.Rounded.Radio, contentDescription = if (widths.labelRadio) null else radioLabel, modifier = Modifier.size(22.dp))
                        if (widths.labelRadio) {
                            Spacer(Modifier.width(6.dp))
                            Text(radioLabel, style = textStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                },
                menuContent = { DropdownMenuItem(text = { Text(radioLabel) }, onClick = onRadioClick) },
            )
            customItem(
                buttonGroupContent = {
                    val interactionSource = remember { MutableInteractionSource() }
                    val shapes = ButtonGroupDefaults.connectedMiddleButtonShapes()
                    FilledTonalButton(
                        onClick = onShuffleClick,
                        interactionSource = interactionSource,
                        modifier = Modifier
                            .animateWidth(interactionSource)
                            .width(widths.shuffleWidth)
                            .height(rowHeight),
                        shapes = ButtonDefaults.shapes(shape = shapes.shape, pressedShape = shapes.pressedShape),
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Icon(Icons.Rounded.Shuffle, contentDescription = shuffleLabel, modifier = Modifier.size(22.dp))
                    }
                },
                menuContent = { DropdownMenuItem(text = { Text(shuffleLabel) }, onClick = onShuffleClick) },
            )
            customItem(
                buttonGroupContent = {
                    val interactionSource = remember { MutableInteractionSource() }
                    val shapes = ButtonGroupDefaults.connectedTrailingButtonShapes()
                    Button(
                        onClick = onPlayAllClick,
                        interactionSource = interactionSource,
                        modifier = Modifier
                            .animateWidth(interactionSource)
                            .width(widths.playWidth)
                            .height(rowHeight),
                        shapes = ButtonDefaults.shapes(shape = shapes.shape, pressedShape = shapes.pressedShape),
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = "Play all", modifier = Modifier.size(30.dp))
                    }
                },
                menuContent = { DropdownMenuItem(text = { Text("Play all") }, onClick = onPlayAllClick) },
            )
        }
    }
}

@Composable
private fun MetroArtistLatestReleaseCard(
    album: AlbumItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val imageSize = 132.dp
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AsyncImage(
                model = album.thumbnail,
                contentDescription = album.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(imageSize)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            )
            Column(modifier = Modifier.height(imageSize).weight(1f)) {
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (album.explicit) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f),
                        ) {
                            Text(
                                text = "E",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            )
                        }
                    }
                    val meta = album.year?.toString() ?: "Album"
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.weight(1f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(
                        onClick = onClick,
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Play")
                    }
                }
            }
        }
    }
}

@Composable
private fun MetroArtistSongRow(
    item: YTItem,
    fallbackArtist: String,
    currentSongId: String?,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val id = when (item) {
        is SongItem -> item.id
        is VideoItem -> item.id
        is PlaylistItem -> item.id
        is AlbumItem -> item.browseId
        else -> ""
    }
    val title = when (item) {
        is SongItem -> item.title
        is VideoItem -> item.title
        is PlaylistItem -> item.title
        is AlbumItem -> item.title
        else -> ""
    }
    val primaryArtist = when (item) {
        is SongItem -> item.artists.joinToString(", ") { it.name }.ifBlank { fallbackArtist }
        is VideoItem -> item.authors?.joinToString(", ") { it.name }?.ifBlank { fallbackArtist } ?: fallbackArtist
        is PlaylistItem -> item.author?.name?.takeIf { it.isNotBlank() }?.let { "Playlist • $it" } ?: "Playlist"
        is AlbumItem -> item.artists?.joinToString(", ") { it.name }?.takeIf { it.isNotBlank() }?.let { "Album • $it" } ?: "Album"
        else -> ""
    }
    val suffix = when (item) {
        is SongItem -> item.viewCountText.orEmpty()
        is VideoItem -> item.viewCountText.orEmpty()
        is PlaylistItem -> item.viewCountText.orEmpty()
        else -> ""
    }
    val thumb = when (item) {
        is SongItem -> item.thumbnail
        is VideoItem -> item.thumbnail
        is PlaylistItem -> item.thumbnail
        is AlbumItem -> item.thumbnail
        else -> null
    }
    val isCurrent = id.isNotBlank() && id == currentSongId
    val containerColor by animateColorAsState(
        targetValue = if (isCurrent) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        label = "artistSongRowBg",
    )
    val titleColor = if (isCurrent) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
    val subColor = if (isCurrent) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f) else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .clip(if (isCurrent) CircleShape else RoundedCornerShape(12.dp))
            .background(containerColor)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(if (isCurrent) CircleShape else RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = thumb,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (isCurrent) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    LytrixEqualizerWaves(isPlaying = isPlaying, barColor = Color.White, maxHeight = 14.dp, barWidth = 2.5.dp, spacing = 2.dp)
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            MetroSuffixedSubtitle(
                primaryText = primaryArtist,
                suffixText = suffix,
                color = subColor,
            )
        }
        IconButton(onClick = onLongClick, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = subColor, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun MetroArtistCarouselShelf(
    items: List<YTItem>,
    fallbackArtist: String,
    onItemClick: (YTItem) -> Unit,
    onItemLongClick: (YTItem) -> Unit,
) {
    if (items.isEmpty()) return
    val carouselState = rememberCarouselState { items.size }
    val flingBehavior = CarouselDefaults.multiBrowseFlingBehavior(carouselState)
    val subtitleStyle = MaterialTheme.typography.labelSmall.copy(
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.sp,
    )

    HorizontalMultiBrowseCarousel(
        state = carouselState,
        flingBehavior = flingBehavior,
        preferredItemWidth = MetroCarouselItemSize,
        itemSpacing = MetroCarouselItemSpacing,
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) { index ->
        val item = items[index]
        val isArtist = item is ArtistItem
        val artShape: Shape = if (isArtist) CircleShape else RoundedCornerShape(MetroCarouselCornerRadius)
        val thumb = when (item) {
            is AlbumItem -> item.thumbnail
            is PlaylistItem -> item.thumbnail
            is ArtistItem -> item.thumbnail
            is SongItem -> item.thumbnail
            is VideoItem -> item.thumbnail
            else -> null
        }
        val title = when (item) {
            is AlbumItem -> item.title
            is PlaylistItem -> item.title
            is ArtistItem -> item.title
            is SongItem -> item.title
            is VideoItem -> item.title
            else -> ""
        }
        val sub = when (item) {
            is AlbumItem -> listOfNotNull(
                "Album",
                item.year?.toString(),
                item.artists?.joinToString(", ") { it.name }?.takeIf { it.isNotBlank() && item.year == null },
            ).joinToString(" • ")
            is PlaylistItem -> item.author?.name?.takeIf { it.isNotBlank() }?.let { "Playlist • $it" } ?: "Playlist"
            is ArtistItem -> item.monthlyListenerCountText?.takeIf { it.isNotBlank() }.orEmpty()
            is SongItem -> item.artists.joinToString(", ") { it.name }.ifBlank { fallbackArtist }
            is VideoItem -> item.authors?.joinToString(", ") { it.name }?.ifBlank { fallbackArtist } ?: fallbackArtist
            else -> ""
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clipToBounds()
                .combinedClickable(
                    onClick = { onItemClick(item) },
                    onLongClick = { onItemLongClick(item) },
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .maskClip(artShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
            ) {
                AsyncImage(
                    model = thumb,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Column(
                modifier = Modifier.metroCarouselCaption(carouselItemDrawInfo),
                horizontalAlignment = if (isArtist) Alignment.CenterHorizontally else Alignment.Start,
            ) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = if (isArtist) TextAlign.Center else TextAlign.Start,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (sub.isNotBlank()) {
                    Text(
                        text = sub,
                        style = subtitleStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = if (isArtist) TextAlign.Center else TextAlign.Start,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun MetroArtistAboutCard(
    artistTitle: String,
    monthlyListeners: String?,
    description: String,
    imageUrl: String,
    onClick: () -> Unit,
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp),
    ) {
        MetroSectionTitle(
            title = "About",
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(10.dp))
        Surface(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp),
                contentAlignment = Alignment.BottomStart,
            ) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = artistTitle,
                    contentScale = ContentScale.Crop,
                    alignment = BiasAlignment(0f, -0.4f),
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.35f to surfaceColor.copy(alpha = 0.45f),
                                0.72f to surfaceColor.copy(alpha = 0.88f),
                                1f to surfaceColor.copy(alpha = 0.96f),
                            )
                        ),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 22.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = artistTitle,
                            style = MaterialTheme.typography.headlineMediumEmphasized.copy(
                                fontSize = 28.sp,
                                lineHeight = 32.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!monthlyListeners.isNullOrBlank()) {
                            Text(
                                text = monthlyListeners,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = "Read more",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 2.dp).size(26.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MetroAboutArtistOverlay(
    artistTitle: String,
    subscribers: String?,
    monthlyListeners: String?,
    description: String,
    imageUrl: String,
    onBack: () -> Unit,
) {
    BackHandler { onBack() }
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            TopAppBar(
                title = {
                    Text(
                        text = artistTitle,
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 140.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = artistTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    )
                }
                if (!subscribers.isNullOrBlank() || !monthlyListeners.isNullOrBlank()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (!subscribers.isNullOrBlank()) {
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(18.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = "Subscribers",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = subscribers.replace("subscribers", "", ignoreCase = true).trim(),
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                            if (!monthlyListeners.isNullOrBlank()) {
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(18.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = "Monthly audience",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = monthlyListeners,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (description.isNotBlank()) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text(
                                    text = "About",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = description,
                                    style = MaterialTheme.typography.bodyLarge,
                                    lineHeight = 24.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
