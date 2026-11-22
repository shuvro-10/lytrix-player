@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
)

package com.musicplayer.lytrix.ui

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.carousel.CarouselDefaults
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.musicplayer.lytrix.LytrixSongItem
import com.musicplayer.lytrix.model.JioSaavnSong
import com.musicplayer.lytrix.PodcastEpisode
import com.musicplayer.lytrix.PodcastInfo
import com.musicplayer.lytrix.media.MediaHubCard
import com.musicplayer.lytrix.media.MediaHubCollectionDetails
import com.musicplayer.lytrix.media.MediaHubKind
import com.musicplayer.lytrix.media.MediaHubRepository
import com.musicplayer.lytrix.media.MediaHubSection
import com.musicplayer.lytrix.media.MediaHubSource
import org.json.JSONObject

fun MediaHubCard.toPodcastEpisode(): PodcastEpisode = PodcastEpisode(
    id = id,
    title_original = title,
    audio = audioUrl.ifBlank { if (source == MediaHubSource.YOUTUBE) "yt:$id" else "" },
    image = imageUrl,
    audio_length_sec = durationSec,
    podcast = PodcastInfo(
        publisher_original = subtitle.substringBefore(" • ").ifBlank {
            when (source) {
                MediaHubSource.YOUTUBE -> "YouTube"
                MediaHubSource.LIBRIVOX -> "LibriVox"
                MediaHubSource.APPLE -> "Apple Podcasts"
            }
        },
        channelId = channelId
    ),
    channelId = channelId,
)

fun playUnifiedMediaEpisode(
    context: Context,
    viewModel: PlayerViewModel,
    episode: PodcastEpisode,
    queue: List<PodcastEpisode>,
    mediaCategory: String = "PODCAST",
) {
    viewModel.setMediaCategory(mediaCategory)
    val audio = episode.audio.orEmpty()
    if (audio.startsWith("yt:")) {
        val vid = audio.removePrefix("yt:").trim()
        val ytItem = LytrixSongItem(
            videoId = vid,
            title = episode.title_original ?: "Episode",
            artist = episode.podcast?.publisher_original ?: "Podcast",
            duration = MediaHubRepository.formatMinutes(episode.audio_length_sec),
            thumbnailUrl = episode.image.orEmpty(),
        )
        viewModel.playLytrixSongWithAd(ytItem, context, channelId = episode.channelId, mediaType = mediaCategory)
    } else if (audio.isNotBlank()) {
        val mp3Queue = queue.filter { !it.audio.orEmpty().startsWith("yt:") && !it.audio.isNullOrBlank() }
        if (mediaCategory == "AUDIOBOOK") {
            viewModel.playAudiobookWithAd(context, episode, mp3Queue.ifEmpty { listOf(episode) })
        } else {
            viewModel.playPodcastWithAd(context, episode, mp3Queue.ifEmpty { listOf(episode) }, isRadio = false)
        }
    } else {
        Toast.makeText(context, "Audio stream unavailable", Toast.LENGTH_SHORT).show()
    }
}

fun isMediaHubEpisodeLiked(context: Context, kind: MediaHubKind, id: String): Boolean {
    val prefName = if (kind == MediaHubKind.AUDIOBOOK) "online_liked_audiobooks" else "online_liked_podcasts"
    return context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
        .getStringSet("liked_ids", emptySet())
        ?.contains(id) == true
}

fun toggleMediaHubEpisodeLiked(context: Context, kind: MediaHubKind, episode: PodcastEpisode): Boolean {
    val prefName = if (kind == MediaHubKind.AUDIOBOOK) "online_liked_audiobooks" else "online_liked_podcasts"
    val prefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
    val set = prefs.getStringSet("liked_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
    val dataKey = if (kind == MediaHubKind.AUDIOBOOK) "audiobook_data_${episode.id}" else "podcast_data_${episode.id}"
    val nowLiked = if (set.contains(episode.id)) {
        set.remove(episode.id)
        prefs.edit()
            .remove(dataKey)
            .remove("song_data_${episode.id}")
            .remove("ep_${episode.id}")
            .putStringSet("liked_ids", set)
            .apply()
        false
    } else {
        set.add(episode.id)
        val obj = JSONObject().apply {
            put("id", episode.id)
            put("title", episode.title_original ?: "Unknown")
            put("title_original", episode.title_original ?: "Unknown")
            put("name", episode.title_original ?: "Unknown")
            put("audio", episode.audio ?: "")
            put("url", episode.audio ?: "")
            put("downloadUrl", episode.audio ?: "")
            put("image", episode.image ?: "")
            put("imageUrl", episode.image ?: "")
            put("duration", episode.audio_length_sec)
            put("audio_length_sec", episode.audio_length_sec)
            put("publisher", episode.podcast?.publisher_original ?: "")
            put("artist", episode.podcast?.publisher_original ?: "")
            episode.podcast?.channelId?.let { put("channelId", it) }
        }
        prefs.edit()
            .putString(dataKey, obj.toString())
            .putString("song_data_${episode.id}", obj.toString())
            .putString("ep_${episode.id}", obj.toString())
            .putStringSet("liked_ids", set)
            .apply()
        true
    }
    val label = if (kind == MediaHubKind.AUDIOBOOK) "Audiobooks" else "Podcasts"
    Toast.makeText(context, if (nowLiked) "Saved to Liked $label" else "Removed from Liked $label", Toast.LENGTH_SHORT).show()
    return nowLiked
}

fun saveOrRemoveMediaHubCollection(
    context: Context,
    details: MediaHubCollectionDetails,
): Boolean {
    val autoTitles = setOf("episodes for later", "new episodes", "continue listening", "books for later")
    if (details.title.trim().lowercase() in autoTitles) {
        // Auto-playlists are hardcoded virtual playlists and should not be saved as custom shows
        return false
    }
    val isAudiobook = details.kind == MediaHubKind.AUDIOBOOK
    val dbName = if (isAudiobook) "online_audiobooks_meta" else "online_podcasts_meta"
    val prefix = if (isAudiobook) "online_ab_" else "online_podcast_"
    val metaPrefs = context.getSharedPreferences(dbName, Context.MODE_PRIVATE)
    val coverPrefs = context.getSharedPreferences("playlist_covers", Context.MODE_PRIVATE)
    val timePrefs = context.getSharedPreferences("playlist_time", Context.MODE_PRIVATE)
    val sourcePrefs = context.getSharedPreferences("playlist_source", Context.MODE_PRIVATE)
    val idPrefs = context.getSharedPreferences("playlist_ids", Context.MODE_PRIVATE)
    val set = metaPrefs.getStringSet("names", emptySet())?.toMutableSet() ?: mutableSetOf()
    val itemsPrefs = context.getSharedPreferences("$prefix${details.title}", Context.MODE_PRIVATE)

    val nowSaved = if (set.contains(details.title)) {
        set.remove(details.title)
        metaPrefs.edit().putStringSet("names", set).remove("count_${details.title}").apply()
        itemsPrefs.edit().clear().apply()
        coverPrefs.edit().remove(details.title).apply()
        timePrefs.edit().remove(details.title).apply()
        sourcePrefs.edit().remove(details.title).apply()
        idPrefs.edit().remove(details.title).apply()
        false
    } else {
        set.add(details.title)
        val editor = itemsPrefs.edit().clear()
        details.items.forEachIndexed { idx, ep ->
            val obj = JSONObject().apply {
                put("id", ep.id)
                put("title", ep.title_original ?: "Episode ${idx + 1}")
                put("name", ep.title_original ?: "Episode ${idx + 1}")
                put("artist", ep.podcast?.publisher_original ?: details.author)
                put("imageUrl", ep.image ?: details.imageUrl)
                put("downloadUrl", ep.audio ?: "")
                put("duration", ep.audio_length_sec)
            }
            editor.putString(String.format("%04d_%s", idx, ep.id), obj.toString())
        }
        editor.apply()
        val srcLabel = when (details.source) {
            MediaHubSource.YOUTUBE -> "YouTube"
            MediaHubSource.LIBRIVOX -> "LibriVox"
            MediaHubSource.APPLE -> "Apple"
        }
        metaPrefs.edit().putStringSet("names", set).putInt("count_${details.title}", details.items.size).apply()
        coverPrefs.edit().putString(details.title, details.imageUrl).apply()
        timePrefs.edit().putLong(details.title, System.currentTimeMillis()).apply()
        sourcePrefs.edit().putString(details.title, srcLabel).apply()
        idPrefs.edit().putString(details.title, "${srcLabel.lowercase()}_${details.id}").apply()
        true
    }
    val dest = if (isAudiobook) "Library → Audiobook" else "Library → Podcast"
    Toast.makeText(context, if (nowSaved) "Saved to $dest" else "Removed from Library", Toast.LENGTH_SHORT).show()
    return nowSaved
}

@Composable
fun MetroMediaHubCarouselSection(
    section: MediaHubSection,
    onCardClick: (MediaHubCard) -> Unit,
    onCardLongClick: (MediaHubCard) -> Unit,
    onSeeAllClick: (MediaHubSection) -> Unit,
) {
    if (section.items.isEmpty()) return
    val carouselState = rememberCarouselState { section.items.size }
    val flingBehavior = CarouselDefaults.multiBrowseFlingBehavior(carouselState)
    val subtitleStyle = MaterialTheme.typography.labelSmall.copy(
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.sp,
    )

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
                        onClick = { onCardClick(card) },
                        onLongClick = { onCardLongClick(card) },
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
fun MetroMediaHubSectionDetailOverlay(
    section: MediaHubSection,
    onBack: () -> Unit,
    onCardClick: (MediaHubCard) -> Unit,
    onCardLongClick: (MediaHubCard) -> Unit,
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
                itemsIndexed(section.items, key = { idx, c -> "${c.id}_$idx" }) { _, card ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .combinedClickable(
                                onClick = { onCardClick(card) },
                                onLongClick = { onCardLongClick(card) },
                            ),
                    ) {
                        AsyncImage(
                            model = card.imageUrl,
                            contentDescription = card.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(14.dp))
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
fun MetroMediaHubCollectionOverlay(
    card: MediaHubCard,
    details: MediaHubCollectionDetails?,
    isLoading: Boolean,
    context: Context,
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
    onSavedStateChanged: () -> Unit,
    onDownloadEpisode: (MediaHubKind, PodcastEpisode) -> Unit,
    bottomPadding: Dp = 140.dp,
) {
    BackHandler { onBack() }
    val title = details?.title?.takeIf { it.isNotBlank() } ?: card.title
    val author = details?.author?.takeIf { it.isNotBlank() } ?: card.subtitle
    val cover = details?.imageUrl?.takeIf { it.isNotBlank() } ?: card.imageUrl
    val items = details?.items.orEmpty()
    val isAudiobook = card.kind == MediaHubKind.AUDIOBOOK
    val dbName = if (isAudiobook) "online_audiobooks_meta" else "online_podcasts_meta"

    var isSaved by remember(title) {
        mutableStateOf(
            context.getSharedPreferences(dbName, Context.MODE_PRIVATE)
                .getStringSet("names", emptySet())
                ?.contains(title) == true
        )
    }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchOpen by remember { mutableStateOf(false) }
    var selectedEpisodeForMenu by remember { mutableStateOf<PodcastEpisode?>(null) }
    var showAddToPlaylistDialogForEpisode by remember { mutableStateOf<PodcastEpisode?>(null) }
    var newShowsPlNameInput by remember { mutableStateOf("") }

    val filtered = remember(items, searchQuery) {
        if (searchQuery.isBlank()) items
        else items.filter { (it.title_original ?: "").contains(searchQuery.trim(), ignoreCase = true) }
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
                    if (isSearchOpen) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text(if (isAudiobook) "Search chapters..." else "Search episodes...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(
                            text = if (isAudiobook) "Audiobook" else "Podcast Show",
                            style = MaterialTheme.typography.titleLargeEmphasized,
                            maxLines = 1,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (isSearchOpen) { isSearchOpen = false; searchQuery = "" } else onBack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!isSearchOpen && items.isNotEmpty()) {
                        IconButton(onClick = { isSearchOpen = true }) {
                            Icon(Icons.Rounded.Search, contentDescription = "Search")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )

            if (isLoading && items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ContainedLoadingIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPadding),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            AsyncImage(
                                model = cover,
                                contentDescription = title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth(0.72f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            )
                            Spacer(Modifier.height(16.dp))
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.headlineSmallEmphasized.copy(fontWeight = FontWeight.Bold),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = author,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    val sourceTag = when (card.source) {
                                        MediaHubSource.YOUTUBE -> "YouTube"
                                        MediaHubSource.LIBRIVOX -> "LibriVox"
                                        MediaHubSource.APPLE -> "Apple Podcasts"
                                    }
                                    val unit = if (isAudiobook) "chapters" else "episodes"
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "$sourceTag • ${items.size} $unit",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (!details?.description.isNullOrBlank()) {
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            text = details!!.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 4,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Button(
                                    onClick = {
                                        if (filtered.isNotEmpty()) {
                                            playUnifiedMediaEpisode(context, viewModel, filtered.first(), filtered)
                                        }
                                    },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    shape = RoundedCornerShape(100.dp),
                                ) {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Play all", fontWeight = FontWeight.Bold)
                                }
                                FilledTonalButton(
                                    onClick = {
                                        if (filtered.isNotEmpty()) {
                                            val sh = filtered.shuffled()
                                            playUnifiedMediaEpisode(context, viewModel, sh.first(), sh)
                                        }
                                    },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    shape = RoundedCornerShape(100.dp),
                                ) {
                                    Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Shuffle", fontWeight = FontWeight.Bold)
                                }
                                if (details != null && items.isNotEmpty()) {
                                    FilledTonalIconButton(
                                        onClick = {
                                            isSaved = saveOrRemoveMediaHubCollection(context, details)
                                            onSavedStateChanged()
                                        },
                                        modifier = Modifier.size(48.dp),
                                    ) {
                                        Icon(
                                            imageVector = if (isSaved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                                            contentDescription = if (isSaved) "Saved" else "Save",
                                            tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    itemsIndexed(filtered, key = { idx, ep -> "${ep.id}_$idx" }) { idx, ep ->
                        var liked by remember(ep.id) { mutableStateOf(isMediaHubEpisodeLiked(context, card.kind, ep.id)) }
                        val durText = MediaHubRepository.formatMinutes(ep.audio_length_sec)
                        val isYt = ep.audio.orEmpty().startsWith("yt:")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .combinedClickable(
                                    onClick = { playUnifiedMediaEpisode(context, viewModel, ep, filtered) },
                                    onLongClick = {
                                        liked = toggleMediaHubEpisodeLiked(context, card.kind, ep)
                                        onSavedStateChanged()
                                    },
                                )
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AsyncImage(
                                model = ep.image?.takeIf { it.isNotBlank() } ?: cover,
                                contentDescription = ep.title_original,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = ep.title_original ?: "Episode ${idx + 1}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                MetroSuffixedSubtitle(
                                    primaryText = ep.podcast?.publisher_original ?: author,
                                    suffixText = durText,
                                )
                            }
                            IconButton(
                                onClick = { selectedEpisodeForMenu = ep },
                                modifier = Modifier.size(38.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = "Options",
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

    if (showAddToPlaylistDialogForEpisode != null) {
        val targetEp = showAddToPlaylistDialogForEpisode!!
        val plPrefs = context.getSharedPreferences("online_shows_playlists_meta", Context.MODE_PRIVATE)
        val playlists = plPrefs.getStringSet("names", emptySet())?.toList() ?: emptyList()
        AlertDialog(
            onDismissRequest = { showAddToPlaylistDialogForEpisode = null },
            title = { Text("Add to Shows Playlist", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    if (playlists.isNotEmpty()) {
                        Text("Existing Playlists:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                        LazyColumn(modifier = Modifier.heightIn(max = 160.dp).padding(vertical = 8.dp)) {
                            items(playlists) { plName ->
                                Text(
                                    text = plName,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        val showPlPrefs = context.getSharedPreferences("online_show_pl_$plName", Context.MODE_PRIVATE)
                                        val obj = org.json.JSONObject().apply {
                                            put("id", targetEp.id)
                                            put("title", targetEp.title_original ?: "Episode")
                                            put("name", targetEp.title_original ?: "Episode")
                                            put("artist", targetEp.podcast?.publisher_original ?: author)
                                            put("imageUrl", targetEp.image ?: cover)
                                            put("downloadUrl", targetEp.audio ?: "")
                                            put("albumName", if (isAudiobook) "Audiobook" else "Podcast")
                                            put("timestamp", System.currentTimeMillis())
                                        }
                                        showPlPrefs.edit().putString(targetEp.id, obj.toString()).apply()
                                        Toast.makeText(context, "Added to $plName", Toast.LENGTH_SHORT).show()
                                        onSavedStateChanged()
                                        showAddToPlaylistDialogForEpisode = null
                                    }.padding(8.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newShowsPlNameInput,
                        onValueChange = { newShowsPlNameInput = it },
                        placeholder = { Text("Or create new playlist...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newShowsPlNameInput.isNotBlank()) {
                        val trimmedName = newShowsPlNameInput.trim()
                        val existing = plPrefs.getStringSet("names", emptySet())?.toMutableSet() ?: mutableSetOf()
                        existing.add(trimmedName)
                        plPrefs.edit().putStringSet("names", existing).apply()
                        context.getSharedPreferences("playlist_time", Context.MODE_PRIVATE).edit().putLong(trimmedName, System.currentTimeMillis()).apply()
                        context.getSharedPreferences("playlist_source", Context.MODE_PRIVATE).edit().putString(trimmedName, "Shows").apply()

                        val showPlPrefs = context.getSharedPreferences("online_show_pl_$trimmedName", Context.MODE_PRIVATE)
                        val obj = org.json.JSONObject().apply {
                            put("id", targetEp.id)
                            put("title", targetEp.title_original ?: "Episode")
                            put("name", targetEp.title_original ?: "Episode")
                            put("artist", targetEp.podcast?.publisher_original ?: author)
                            put("imageUrl", targetEp.image ?: cover)
                            put("downloadUrl", targetEp.audio ?: "")
                            put("albumName", if (isAudiobook) "Audiobook" else "Podcast")
                            put("timestamp", System.currentTimeMillis())
                        }
                        showPlPrefs.edit().putString(targetEp.id, obj.toString()).apply()
                        Toast.makeText(context, "Created & Added", Toast.LENGTH_SHORT).show()
                        newShowsPlNameInput = ""
                        onSavedStateChanged()
                        showAddToPlaylistDialogForEpisode = null
                    }
                }) { Text("Create & Add") }
            },
            dismissButton = { TextButton(onClick = { showAddToPlaylistDialogForEpisode = null }) { Text("Cancel") } }
        )
    }

    if (selectedEpisodeForMenu != null) {
        val menuEp = selectedEpisodeForMenu!!
        var isEpLiked by remember(menuEp.id) { mutableStateOf(isMediaHubEpisodeLiked(context, card.kind, menuEp.id)) }
        val laterPrefix = if (isAudiobook) "online_ab_" else "online_podcast_"
        val laterName = if (isAudiobook) "Books for later" else "Episodes for later"
        val laterPrefs = remember(menuEp.id) { context.getSharedPreferences("$laterPrefix$laterName", Context.MODE_PRIVATE) }
        var isEpLaterSaved by remember(menuEp.id) { mutableStateOf(menuEp.id.isNotBlank() && laterPrefs.contains(menuEp.id)) }

        com.musicplayer.lytrix.ui.component.LytrixFluidBottomSheet(
            onDismissRequest = { selectedEpisodeForMenu = null },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = menuEp.image?.takeIf { it.isNotBlank() } ?: cover,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(54.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = menuEp.title_original ?: "Episode",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = menuEp.podcast?.publisher_original ?: author,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = {
                        isEpLiked = toggleMediaHubEpisodeLiked(context, card.kind, menuEp)
                        onSavedStateChanged()
                    }) {
                        Icon(
                            imageVector = if (isEpLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = "Like",
                            tint = if (isEpLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    LytrixBottomSheetListItem(
                        icon = Icons.Default.PlaylistPlay,
                        text = "Play next",
                        onClick = {
                            val fakeSong = JioSaavnSong(
                                id = menuEp.id,
                                name = menuEp.title_original ?: "Episode",
                                artist = menuEp.podcast?.publisher_original ?: author,
                                imageUrl = menuEp.image ?: cover,
                                downloadUrl = menuEp.audio ?: "",
                                albumName = if (isAudiobook) "Audiobook" else "Podcast"
                            )
                            viewModel.playNextOnline(context, fakeSong)
                            selectedEpisodeForMenu = null
                        }
                    )
                    LytrixBottomSheetListItem(
                        icon = if (isEpLaterSaved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                        text = if (isEpLaterSaved) (if (isAudiobook) "Saved in Books for later" else "Saved in Episodes for later") else (if (isAudiobook) "Save to Books for later" else "Save to Episodes for later"),
                        onClick = {
                            if (isEpLaterSaved) {
                                laterPrefs.edit().remove(menuEp.id).apply()
                                isEpLaterSaved = false
                                Toast.makeText(context, "Removed from $laterName", Toast.LENGTH_SHORT).show()
                            } else {
                                val json = org.json.JSONObject().apply {
                                    put("id", menuEp.id)
                                    put("name", menuEp.title_original ?: "Episode")
                                    put("title", menuEp.title_original ?: "Episode")
                                    put("artist", menuEp.podcast?.publisher_original ?: author)
                                    put("publisher", menuEp.podcast?.publisher_original ?: author)
                                    put("imageUrl", menuEp.image ?: cover)
                                    put("downloadUrl", menuEp.audio ?: "")
                                    put("albumName", if (isAudiobook) "Audiobook" else "Podcast")
                                    menuEp.channelId?.let { put("channelId", it) }
                                    put("timestamp", System.currentTimeMillis())
                                }
                                laterPrefs.edit().putString(menuEp.id, json.toString()).apply()
                                isEpLaterSaved = true
                                Toast.makeText(context, "Saved to $laterName", Toast.LENGTH_SHORT).show()
                            }
                            onSavedStateChanged()
                            selectedEpisodeForMenu = null
                        }
                    )
                    LytrixBottomSheetListItem(
                        icon = Icons.Default.PlaylistAdd,
                        text = "Add to playlist",
                        onClick = {
                            val epToSave = menuEp
                            selectedEpisodeForMenu = null
                            showAddToPlaylistDialogForEpisode = epToSave
                        }
                    )
                }

                Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    LytrixBottomSheetListItem(
                        icon = Icons.Rounded.Download,
                        text = if (isAudiobook) "Download Audiobook" else "Download Episode",
                        onClick = {
                            onDownloadEpisode(card.kind, menuEp)
                            selectedEpisodeForMenu = null
                        }
                    )
                    LytrixBottomSheetListItem(
                        icon = Icons.Rounded.Share,
                        text = "Share",
                        onClick = {
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, "${menuEp.title_original ?: "Episode"} - ${menuEp.audio ?: ""}")
                            }
                            context.startActivity(android.content.Intent.createChooser(intent, "Share"))
                            selectedEpisodeForMenu = null
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MetroLibraryMediaCollectionScreen(
    title: String,
    subtitle: String,
    kind: MediaHubKind,
    episodes: List<PodcastEpisode>,
    isDownloadedView: Boolean,
    context: Context,
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
    onToggleLike: (PodcastEpisode) -> Unit,
    onDownload: ((PodcastEpisode) -> Unit)? = null,
    onEpisodeClick: ((PodcastEpisode, List<PodcastEpisode>) -> Unit)? = null,
    onDeleteDownload: ((PodcastEpisode) -> Unit)? = null,
    bottomPadding: Dp = 140.dp,
) {
    BackHandler { onBack() }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchOpen by remember { mutableStateOf(false) }
    var selectedEpisodeForMenu by remember { mutableStateOf<PodcastEpisode?>(null) }
    var showAddToPlaylistDialogForEpisode by remember { mutableStateOf<PodcastEpisode?>(null) }
    var newShowsPlNameInput by remember { mutableStateOf("") }

    val filtered = remember(episodes, searchQuery) {
        if (searchQuery.isBlank()) episodes
        else episodes.filter {
            (it.title_original ?: "").contains(searchQuery.trim(), ignoreCase = true) ||
                (it.podcast?.publisher_original ?: "").contains(searchQuery.trim(), ignoreCase = true)
        }
    }

    val playItem: (PodcastEpisode, List<PodcastEpisode>) -> Unit = { ep, queue ->
        if (onEpisodeClick != null) {
            onEpisodeClick(ep, queue)
        } else {
            playUnifiedMediaEpisode(context, viewModel, ep, queue)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            TopAppBar(
                title = {
                    if (isSearchOpen) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Column {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLargeEmphasized,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (isSearchOpen) { isSearchOpen = false; searchQuery = "" } else onBack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!isSearchOpen && episodes.isNotEmpty()) {
                        IconButton(onClick = { isSearchOpen = true }) {
                            Icon(Icons.Rounded.Search, contentDescription = "Search")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )

            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Nothing saved here yet.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPadding),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Button(
                                onClick = { playItem(filtered.first(), filtered) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(100.dp),
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Play all")
                            }
                            FilledTonalButton(
                                onClick = {
                                    val sh = filtered.shuffled()
                                    playItem(sh.first(), sh)
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
                    itemsIndexed(filtered, key = { idx, ep -> "${ep.id}_$idx" }) { _, ep ->
                        val durText = MediaHubRepository.formatMinutes(ep.audio_length_sec)
                        val isYt = ep.audio.orEmpty().startsWith("yt:")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .combinedClickable(
                                    onClick = { playItem(ep, filtered) },
                                    onLongClick = { if (!isDownloadedView) onToggleLike(ep) },
                                )
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AsyncImage(
                                model = ep.image,
                                contentDescription = ep.title_original,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = ep.title_original ?: "Unknown",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                MetroSuffixedSubtitle(
                                    primaryText = ep.podcast?.publisher_original ?: if (kind == MediaHubKind.AUDIOBOOK) "Audiobook" else "Podcast",
                                    suffixText = durText,
                                )
                            }
                            if (isDownloadedView) {
                                if (onDeleteDownload != null) {
                                    IconButton(onClick = { onDeleteDownload(ep) }, modifier = Modifier.size(38.dp)) {
                                        Icon(
                                            imageVector = Icons.Rounded.Delete,
                                            contentDescription = "Delete download",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                            } else {
                                IconButton(onClick = { onToggleLike(ep) }, modifier = Modifier.size(38.dp)) {
                                    Icon(
                                        imageVector = Icons.Rounded.Favorite,
                                        contentDescription = "Unlike",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                if (!isYt && onDownload != null) {
                                    IconButton(onClick = { onDownload(ep) }, modifier = Modifier.size(38.dp)) {
                                        Icon(
                                            imageVector = Icons.Rounded.Download,
                                            contentDescription = "Download",
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
    }
}

@Composable
fun MetroMediaHubCardBottomSheet(
    card: MediaHubCard,
    context: Context,
    onDismiss: () -> Unit,
    onOpenOrPlay: () -> Unit,
    onLikedChanged: () -> Unit,
    onDownloadEpisode: (MediaHubKind, PodcastEpisode) -> Unit,
) {
    val ep = remember(card) { card.toPodcastEpisode() }
    var isLiked by remember(card.id) { mutableStateOf(isMediaHubEpisodeLiked(context, card.kind, card.id)) }
    val kindLabel = if (card.kind == MediaHubKind.AUDIOBOOK) "Audiobooks" else "Podcasts"

    com.musicplayer.lytrix.ui.component.LytrixFluidBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = card.imageUrl,
                    contentDescription = card.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = card.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = card.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!card.isCollection) {
                    IconButton(onClick = {
                        isLiked = toggleMediaHubEpisodeLiked(context, card.kind, ep)
                        onLikedChanged()
                    }) {
                        Icon(
                            imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = "Like",
                            tint = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = {
                        onDismiss()
                        onOpenOrPlay()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(100.dp),
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (card.isCollection) "Open" else "Play")
                }
                if (!card.isCollection) {
                    val isAb = card.kind == MediaHubKind.AUDIOBOOK
                    val laterPrefix = if (isAb) "online_ab_" else "online_podcast_"
                    val laterName = if (isAb) "Books for later" else "Episodes for later"
                    val laterPrefs = remember(ep.id) { context.getSharedPreferences("$laterPrefix$laterName", Context.MODE_PRIVATE) }
                    var isLaterSaved by remember(ep.id) { mutableStateOf(ep.id.isNotBlank() && laterPrefs.contains(ep.id)) }
                    FilledTonalButton(
                        onClick = {
                            if (ep.id.isNotBlank()) {
                                if (isLaterSaved) {
                                    laterPrefs.edit().remove(ep.id).apply()
                                    isLaterSaved = false
                                    android.widget.Toast.makeText(context, "Removed from $laterName", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    val json = org.json.JSONObject().apply {
                                        put("id", ep.id)
                                        put("name", ep.title_original ?: card.title)
                                        put("title", ep.title_original ?: card.title)
                                        put("artist", ep.podcast?.publisher_original ?: card.subtitle)
                                        put("publisher", ep.podcast?.publisher_original ?: card.subtitle)
                                        put("imageUrl", ep.image ?: card.imageUrl)
                                        put("downloadUrl", ep.audio ?: "")
                                        put("albumName", if (isAb) "Audiobook" else "Podcast")
                                        ep.channelId?.let { put("channelId", it) }
                                        put("timestamp", System.currentTimeMillis())
                                    }
                                    laterPrefs.edit().putString(ep.id, json.toString()).apply()
                                    isLaterSaved = true
                                    android.widget.Toast.makeText(context, "Saved to $laterName", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(100.dp),
                    ) {
                        Icon(
                            imageVector = if (isLaterSaved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (isLaterSaved) "Saved" else "Save later", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (!card.isCollection) {
                Spacer(Modifier.height(10.dp))
                FilledTonalButton(
                    onClick = {
                        onDownloadEpisode(card.kind, ep)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(100.dp),
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (card.kind == MediaHubKind.AUDIOBOOK) "Download Audiobook" else "Download Episode")
                }
            }
        }
    }
}
