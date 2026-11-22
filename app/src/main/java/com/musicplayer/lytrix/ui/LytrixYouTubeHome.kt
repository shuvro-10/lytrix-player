@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
)

package com.musicplayer.lytrix.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.carousel.CarouselDefaults
import androidx.compose.material3.carousel.CarouselItemDrawInfo
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.musicplayer.lytrix.innertube.pages.MoodAndGenres
import com.musicplayer.lytrix.model.JioSaavnSong
import com.musicplayer.lytrix.saavn.SaavnCardItem
import com.musicplayer.lytrix.saavn.SaavnHomeSection
import kotlin.math.ceil
import kotlin.math.floor

// Exact MetroList Dimensions.kt values
internal val MetroCarouselItemSize = 115.dp
internal val MetroCarouselItemSpacing = 5.dp
internal val MetroCarouselCornerRadius = 12.dp

/**
 * Exact port of MetroList's CarouselCaption.kt:
 * Aligns and sizes card captions inside the visible maskRect of HorizontalMultiBrowseCarousel
 * so left/right characters are never sliced off, and smoothly fades captions as items shrink.
 */
internal fun Modifier.metroCarouselCaption(info: CarouselItemDrawInfo?): Modifier =
    if (info == null) {
        this
    } else {
        layout { measurable, constraints ->
            val mask = info.maskRect
            val itemWidth = constraints.maxWidth
            val left = ceil(mask.left).toInt().coerceIn(0, itemWidth)
            val right = floor(mask.right).toInt().coerceIn(left, itemWidth)
            val visibleWidth = right - left
            val minimumWidth = itemWidth / 2
            val captionWidth = maxOf(visibleWidth, minimumWidth)
            val alpha = if (itemWidth > 0) ((visibleWidth.toFloat() / itemWidth - 0.5f) * 2f).coerceIn(0f, 1f) else 0f
            val placeable = measurable.measure(constraints.copy(minWidth = captionWidth, maxWidth = captionWidth))
            layout(itemWidth, placeable.height) {
                placeable.placeWithLayer(left, 0) { this.alpha = alpha }
            }
        }
    }

data class MetroSectionNavPayload(
    val title: String,
    val subtitle: String? = null,
    val tracks: List<PlayerViewModel.YtMusicTrack>,
    val browseId: String? = null,
    val params: String? = null,
    val isVideoSection: Boolean = false,
)

@Composable
fun MetroSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    thumbnailUrl: String? = null,
    isArtistEndpoint: Boolean = false,
    onClick: (() -> Unit)? = null,
    onPlayAllClick: (() -> Unit)? = null,
    onShuffleClick: (() -> Unit)? = null,
) {
    val rowHeight = if (label != null) 58.dp else 48.dp
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = rowHeight)
            .then(
                if (onClick != null) {
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                }
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!thumbnailUrl.isNullOrBlank()) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(46.dp)
                    .clip(if (isArtistEndpoint) CircleShape else RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentScale = ContentScale.Crop,
            )
            Spacer(modifier = Modifier.width(10.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            if (!label.isNullOrBlank()) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmallEmphasized.copy(fontWeight = FontWeight.Bold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        when {
            onClick != null -> MetroSectionActionPill(
                icon = Icons.Rounded.ChevronRight,
                contentDescription = "See all",
                onClick = onClick,
            )
            onShuffleClick != null -> MetroSectionActionPill(
                icon = Icons.Rounded.Shuffle,
                contentDescription = "Shuffle",
                onClick = onShuffleClick,
            )
            onPlayAllClick != null -> MetroSectionActionPill(
                icon = Icons.Rounded.PlayArrow,
                contentDescription = "Play all",
                onClick = onPlayAllClick,
            )
        }
    }
}

@Composable
private fun MetroSectionActionPill(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier.size(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            onClick = onClick,
            modifier = Modifier.size(width = 40.dp, height = 32.dp),
            shape = RoundedCornerShape(100.dp),
            color = MaterialTheme.colorScheme.primary,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
fun MetroChipGroupDividerHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 18.dp, bottom = 8.dp),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun isYtTrackCollectionOrArtist(track: PlayerViewModel.YtMusicTrack): Boolean {
    return track.itemType == "Album" ||
        track.itemType == "Playlist" ||
        track.itemType == "Artist" ||
        track.itemType == "Profile" ||
        track.itemType == "Podcast" ||
        track.artist.startsWith("Album", ignoreCase = true) ||
        track.artist.startsWith("Playlist", ignoreCase = true) ||
        track.artist.equals("Artist", ignoreCase = true) ||
        track.artist.equals("Profile", ignoreCase = true) ||
        track.artist.equals("Station", ignoreCase = true) ||
        track.artist.startsWith("Podcast", ignoreCase = true)
}

private fun isYtTrackArtist(track: PlayerViewModel.YtMusicTrack): Boolean {
    return track.itemType == "Artist" ||
        track.itemType == "Profile" ||
        track.artist.equals("Artist", ignoreCase = true) ||
        track.artist.equals("Profile", ignoreCase = true)
}

fun isMetroSongGridSection(section: PlayerViewModel.YtMusicSection): Boolean {
    val t = section.title
    if (t.contains("video", ignoreCase = true) ||
        t.contains("shows", ignoreCase = true) ||
        t.contains("podcast", ignoreCase = true)
    ) {
        return false
    }
    if (t.contains("quick pick", ignoreCase = true) ||
        t.contains("song mix", ignoreCase = true) ||
        t.equals("Trending songs", ignoreCase = true)
    ) {
        return section.tracks.none { isYtTrackCollectionOrArtist(it) }
    }
    return section.tracks.size >= 4 && section.tracks.none { isYtTrackCollectionOrArtist(it) } &&
        !t.equals("Keep listening", ignoreCase = true) &&
        !t.equals("Listen again", ignoreCase = true) &&
        !t.contains("forgotten", ignoreCase = true)
}

@Composable
fun MetroHomeShelfRenderer(
    section: PlayerViewModel.YtMusicSection,
    viewModel: PlayerViewModel,
    context: Context,
    onTrackClick: (PlayerViewModel.YtMusicTrack, List<PlayerViewModel.YtMusicTrack>, Int) -> Unit,
    onTrackLongClick: (PlayerViewModel.YtMusicTrack) -> Unit,
    onNavigateToArtist: (String) -> Unit,
    onOpenSectionDetail: (MetroSectionNavPayload) -> Unit,
) {
    if (section.tracks.isEmpty()) return
    val isExplicitVideoTitle = section.title.contains("video", ignoreCase = true) ||
        section.title.contains("live", ignoreCase = true) ||
        section.title.contains("performance", ignoreCase = true)
    val hasContainers = section.tracks.any { it.itemType.equals("Album", true) || it.itemType.equals("Playlist", true) || it.itemType.equals("Artist", true) }
    val allVideoItems = section.tracks.isNotEmpty() && !hasContainers && section.tracks.all { t ->
        t.itemType.equals("Video", true) || t.itemType.equals("Live", true)
    }
    val isVideo = !hasContainers && (allVideoItems || (isExplicitVideoTitle && section.tracks.none { it.itemType.equals("Album", true) || it.itemType.equals("Playlist", true) }))
    val isSongGrid = !isVideo && isMetroSongGridSection(section)
    val isSimilar = section.subtitle.equals("Similar to", ignoreCase = true) ||
        section.title.startsWith("Similar to ", ignoreCase = true)
    val headerTitle = if (section.title.startsWith("Similar to ", ignoreCase = true) && section.subtitle == null) {
        section.title.removePrefix("Similar to ").trim()
    } else {
        section.title
    }
    val headerLabel = section.subtitle ?: if (section.title.startsWith("Similar to ", ignoreCase = true)) "Similar to" else null
    val isArtistHeader = !section.artistId.isNullOrBlank() || isSimilar

    val openDetail: () -> Unit = {
        onOpenSectionDetail(
            MetroSectionNavPayload(
                title = section.title,
                subtitle = section.subtitle,
                tracks = section.tracks,
                browseId = section.browseId,
                params = section.params,
                isVideoSection = isVideo,
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 18.dp),
    ) {
        MetroSectionTitle(
            title = headerTitle,
            label = headerLabel,
            thumbnailUrl = section.iconUrl,
            isArtistEndpoint = isArtistHeader,
            modifier = Modifier.padding(horizontal = 16.dp),
            onClick = openDetail,
        )
        Spacer(Modifier.height(10.dp))

        when {
            isVideo -> MetroVideoMultiBrowseCarousel(
                tracks = section.tracks,
                onTrackClick = { track, idx -> onTrackClick(track, section.tracks, idx) },
                onTrackLongClick = onTrackLongClick,
            )
            isSongGrid -> MetroSongGridPager(
                tracks = section.tracks,
                onTrackClick = { track, idx -> onTrackClick(track, section.tracks, idx) },
                onTrackLongClick = onTrackLongClick,
            )
            else -> MetroYtMultiBrowseCarousel(
                tracks = section.tracks,
                onTrackClick = { track, idx -> onTrackClick(track, section.tracks, idx) },
                onTrackLongClick = onTrackLongClick,
            )
        }
    }
}

@Composable
fun MetroYtMultiBrowseCarousel(
    tracks: List<PlayerViewModel.YtMusicTrack>,
    onTrackClick: (PlayerViewModel.YtMusicTrack, Int) -> Unit,
    onTrackLongClick: (PlayerViewModel.YtMusicTrack) -> Unit,
) {
    if (tracks.isEmpty()) return
    val carouselState = rememberCarouselState { tracks.size }
    val flingBehavior = CarouselDefaults.multiBrowseFlingBehavior(carouselState)

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
        val item = tracks[index]
        val isArtist = isYtTrackArtist(item)
        val artShape: Shape = if (isArtist) CircleShape else RoundedCornerShape(MetroCarouselCornerRadius)
        val subtitleStyle = MaterialTheme.typography.labelSmall.copy(
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.sp,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clipToBounds()
                .combinedClickable(
                    onClick = { onTrackClick(item, index) },
                    onLongClick = { onTrackLongClick(item) },
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
                    model = item.thumbnailUrl,
                    contentDescription = item.title,
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
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = if (isArtist) TextAlign.Center else TextAlign.Start,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (item.artist.isNotBlank() && !isArtist) {
                    Text(
                        text = item.artist,
                        style = subtitleStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
fun MetroVideoMultiBrowseCarousel(
    tracks: List<PlayerViewModel.YtMusicTrack>,
    onTrackClick: (PlayerViewModel.YtMusicTrack, Int) -> Unit,
    onTrackLongClick: (PlayerViewModel.YtMusicTrack) -> Unit,
) {
    if (tracks.isEmpty()) return
    val videoWidth = 280.dp
    val carouselState = rememberCarouselState { tracks.size }
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
        preferredItemWidth = videoWidth,
        itemSpacing = 12.dp,
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) { index ->
        val item = tracks[index]
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clipToBounds()
                .combinedClickable(
                    onClick = { onTrackClick(item, index) },
                    onLongClick = { onTrackLongClick(item) },
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .maskClip(RoundedCornerShape(MetroCarouselCornerRadius))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
            ) {
                AsyncImage(
                    model = item.thumbnailUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Column(modifier = Modifier.metroCarouselCaption(carouselItemDrawInfo)) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
                val sub = if (item.playCount.isNotBlank()) "${item.artist} • ${item.playCount}" else item.artist
                if (sub.isNotBlank()) {
                    Text(
                        text = sub,
                        style = subtitleStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
fun MetroSuffixedSubtitle(
    primaryText: String,
    suffixText: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val style = MaterialTheme.typography.bodySmall
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (primaryText.isNotBlank()) {
            Text(
                text = primaryText,
                style = style,
                color = color,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        if (suffixText.isNotBlank()) {
            Text(
                text = if (primaryText.isNotBlank()) " • $suffixText" else suffixText,
                style = style,
                color = color,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

@Composable
private fun MetroSongGridPager(
    tracks: List<PlayerViewModel.YtMusicTrack>,
    onTrackClick: (PlayerViewModel.YtMusicTrack, Int) -> Unit,
    onTrackLongClick: (PlayerViewModel.YtMusicTrack) -> Unit,
) {
    val rows = minOf(4, tracks.size.coerceAtLeast(1))
    val pageCount = (tracks.size + rows - 1) / rows
    val rowHeight = 58.dp
    val rowSpacing = 8.dp
    val pagerState = rememberPagerState { pageCount }
    val flingBehavior = PagerDefaults.flingBehavior(
        state = pagerState,
        pagerSnapDistance = PagerSnapDistance.atMost(pageCount),
    )

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val pageWidth = (maxWidth - 52.dp).coerceIn(250.dp, 356.dp)
        HorizontalPager(
            state = pagerState,
            pageSize = PageSize.Fixed(pageWidth),
            pageSpacing = 14.dp,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalAlignment = Alignment.Top,
            modifier = Modifier
                .fillMaxWidth()
                .height(rowHeight * rows + rowSpacing * (rows - 1))
                .clipToBounds(),
        ) { page ->
            Column(
                modifier = Modifier.width(pageWidth),
                verticalArrangement = Arrangement.spacedBy(rowSpacing),
            ) {
                repeat(rows) { r ->
                    val idx = page * rows + r
                    val track = tracks.getOrNull(idx)
                    if (track != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(rowHeight)
                                .clip(RoundedCornerShape(12.dp))
                                .combinedClickable(
                                    onClick = { onTrackClick(track, idx) },
                                    onLongClick = { onTrackLongClick(track) },
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AsyncImage(
                                model = track.thumbnailUrl,
                                contentDescription = track.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                MetroSuffixedSubtitle(
                                    primaryText = track.artist,
                                    suffixText = track.playCount,
                                )
                            }
                            IconButton(
                                onClick = { onTrackLongClick(track) },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = "More",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetroMoodsAndGenresShelf(
    items: List<MoodAndGenres.Item>,
    onItemClick: (MoodAndGenres.Item) -> Unit,
    onSeeAllClick: (() -> Unit)? = null,
) {
    if (items.isEmpty()) return
    val rows = 4
    val cardWidth = 178.dp
    val rowHeight = 52.dp
    val rowSpacing = 6.dp
    val pageSpacing = 6.dp
    val pageCount = (items.size + rows - 1) / rows
    val pagerState = rememberPagerState { pageCount }
    val flingBehavior = PagerDefaults.flingBehavior(
        state = pagerState,
        pagerSnapDistance = PagerSnapDistance.atMost(pageCount),
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 18.dp),
    ) {
        MetroSectionTitle(
            title = "Moods & genres",
            modifier = Modifier.padding(horizontal = 16.dp),
            onClick = onSeeAllClick,
        )
        Spacer(Modifier.height(10.dp))
        HorizontalPager(
            state = pagerState,
            pageSize = PageSize.Fixed(cardWidth),
            pageSpacing = pageSpacing,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalAlignment = Alignment.Top,
            modifier = Modifier
                .fillMaxWidth()
                .height(rowHeight * rows + rowSpacing * (rows - 1))
                .clipToBounds(),
        ) { page ->
            Column(
                modifier = Modifier.width(cardWidth),
                verticalArrangement = Arrangement.spacedBy(rowSpacing),
            ) {
                repeat(rows) { r ->
                    val index = page * rows + r
                    val item = items.getOrNull(index)
                    if (item != null) {
                        val shape = RoundedCornerShape(
                            topStart = if (index == 0) 14.dp else 6.dp,
                            topEnd = if (index == (items.size - 1) / rows * rows) 14.dp else 6.dp,
                            bottomStart = if (index == minOf(rows - 1, items.size - 1)) 14.dp else 6.dp,
                            bottomEnd = if (index == items.size - 1) 14.dp else 6.dp,
                        )
                        Surface(
                            onClick = { onItemClick(item) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(rowHeight),
                            shape = shape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            tonalElevation = 1.dp,
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Text(
                                    text = item.title,
                                    modifier = Modifier.padding(horizontal = 14.dp),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetroSaavnMultiBrowseSection(
    section: SaavnHomeSection,
    onItemClick: (SaavnCardItem) -> Unit,
    onSongMoreClick: (JioSaavnSong) -> Unit,
    onSeeAllClick: (SaavnHomeSection) -> Unit,
) {
    if (section.items.isEmpty()) return
    val carouselState = rememberCarouselState { section.items.size }
    val flingBehavior = CarouselDefaults.multiBrowseFlingBehavior(carouselState)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 18.dp),
    ) {
        MetroSectionTitle(
            title = section.title,
            modifier = Modifier.padding(horizontal = 16.dp),
            onClick = { onSeeAllClick(section) },
        )
        Spacer(Modifier.height(10.dp))
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
            val card = section.items[index]
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clipToBounds()
                    .combinedClickable(
                        onClick = { onItemClick(card) },
                        onLongClick = { card.song?.let(onSongMoreClick) },
                    ),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .maskClip(RoundedCornerShape(MetroCarouselCornerRadius))
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                ) {
                    AsyncImage(
                        model = card.imageUrl,
                        contentDescription = card.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Column(modifier = Modifier.metroCarouselCaption(carouselItemDrawInfo)) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = card.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (card.subtitle.isNotBlank()) {
                        Text(
                            text = card.subtitle,
                            style = subtitleStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
fun MetroSectionDetailOverlay(
    payload: MetroSectionNavPayload,
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
    onTrackClick: (PlayerViewModel.YtMusicTrack, List<PlayerViewModel.YtMusicTrack>, Int) -> Unit,
    onTrackLongClick: (PlayerViewModel.YtMusicTrack) -> Unit,
    bottomPadding: Dp = 140.dp,
) {
    BackHandler { onBack() }
    var tracks by remember(payload) { mutableStateOf(payload.tracks) }
    var isFetchingMore by remember(payload) { mutableStateOf(!payload.browseId.isNullOrBlank()) }

    LaunchedEffect(payload.browseId, payload.params) {
        val bid = payload.browseId
        if (!bid.isNullOrBlank()) {
            isFetchingMore = true
            val fetched = viewModel.fetchBrowseSectionTracks(bid, payload.params)
            if (fetched.isNotEmpty()) {
                tracks = (payload.tracks + fetched).distinctBy { it.id }
            }
            isFetchingMore = false
        } else {
            isFetchingMore = false
        }
    }

    val isPureSongList = remember(tracks, payload.isVideoSection) {
        !payload.isVideoSection && tracks.isNotEmpty() && tracks.none { isYtTrackCollectionOrArtist(it) }
    }

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
                    Column {
                        if (!payload.subtitle.isNullOrBlank()) {
                            Text(
                                text = payload.subtitle,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = payload.title,
                            style = MaterialTheme.typography.titleLargeEmphasized,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )

            if (isPureSongList) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = bottomPadding),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Button(
                                onClick = {
                                    if (tracks.isNotEmpty()) onTrackClick(tracks.first(), tracks, 0)
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(100.dp),
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Play all")
                            }
                            FilledTonalButton(
                                onClick = {
                                    if (tracks.isNotEmpty()) {
                                        val shuffled = tracks.shuffled()
                                        onTrackClick(shuffled.first(), shuffled, 0)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(100.dp),
                            ) {
                                Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Shuffle")
                            }
                        }
                    }
                    itemsIndexed(tracks, key = { idx, t -> "${t.id}_$idx" }) { idx, track ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .combinedClickable(
                                    onClick = { onTrackClick(track, tracks, idx) },
                                    onLongClick = { onTrackLongClick(track) },
                                )
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AsyncImage(
                                model = track.thumbnailUrl,
                                contentDescription = track.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                MetroSuffixedSubtitle(
                                    primaryText = track.artist,
                                    suffixText = track.playCount,
                                )
                            }
                            IconButton(onClick = { onTrackLongClick(track) }) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = "More")
                            }
                        }
                    }
                    if (isFetchingMore) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                ContainedLoadingIndicator()
                            }
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPadding),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    itemsIndexed(tracks, key = { idx, t -> "${t.id}_$idx" }) { idx, item ->
                        val isArtist = isYtTrackArtist(item)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .combinedClickable(
                                    onClick = { onTrackClick(item, tracks, idx) },
                                    onLongClick = { onTrackLongClick(item) },
                                ),
                            horizontalAlignment = if (isArtist) Alignment.CenterHorizontally else Alignment.Start,
                        ) {
                            AsyncImage(
                                model = item.thumbnailUrl,
                                contentDescription = item.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(if (payload.isVideoSection) 16f / 9f else 1f)
                                    .clip(if (isArtist) CircleShape else RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (item.artist.isNotBlank()) {
                                Text(
                                    text = item.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    if (isFetchingMore) {
                        item(span = { GridItemSpan(2) }) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                ContainedLoadingIndicator()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetroSaavnSectionDetailOverlay(
    section: SaavnHomeSection,
    onBack: () -> Unit,
    onItemClick: (SaavnCardItem) -> Unit,
    onSongMoreClick: (JioSaavnSong) -> Unit,
    bottomPadding: Dp = 140.dp,
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
                        text = section.title,
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
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPadding),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                itemsIndexed(section.items, key = { idx, c -> "${c.type.name}_${c.id}_$idx" }) { _, card ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .combinedClickable(
                                onClick = { onItemClick(card) },
                                onLongClick = { card.song?.let(onSongMoreClick) },
                            ),
                    ) {
                        AsyncImage(
                            model = card.imageUrl,
                            contentDescription = card.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = card.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (card.subtitle.isNotBlank()) {
                            Text(
                                text = card.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetroMoodCategoryOverlay(
    title: String,
    sections: List<PlayerViewModel.YtMusicSection>,
    onBack: () -> Unit,
    onTrackClick: (PlayerViewModel.YtMusicTrack, List<PlayerViewModel.YtMusicTrack>, Int) -> Unit,
    onTrackLongClick: (PlayerViewModel.YtMusicTrack) -> Unit,
    onOpenSectionDetail: (MetroSectionNavPayload) -> Unit,
    bottomPadding: Dp = 140.dp,
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
                        text = title,
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
            if (sections.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    ContainedLoadingIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding),
                ) {
                    sections.forEach { sec ->
                        item(key = "mood_sec_${sec.title}_${sec.tracks.size}") {
                            Column(modifier = Modifier.padding(bottom = 18.dp)) {
                                if (sec.title.isNotBlank()) {
                                    MetroSectionTitle(
                                        title = sec.title,
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        onClick = {
                                            onOpenSectionDetail(
                                                MetroSectionNavPayload(
                                                    title = sec.title,
                                                    subtitle = sec.subtitle,
                                                    tracks = sec.tracks,
                                                    browseId = sec.browseId,
                                                    params = sec.params,
                                                )
                                            )
                                        },
                                    )
                                    Spacer(Modifier.height(10.dp))
                                }
                                MetroYtMultiBrowseCarousel(
                                    tracks = sec.tracks,
                                    onTrackClick = { t, idx -> onTrackClick(t, sec.tracks, idx) },
                                    onTrackLongClick = onTrackLongClick,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
