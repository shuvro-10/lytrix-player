@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
)

package com.musicplayer.lytrix.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.musicplayer.lytrix.LytrixSongItem
import com.musicplayer.lytrix.innertube.models.AlbumItem
import com.musicplayer.lytrix.innertube.models.ArtistItem
import com.musicplayer.lytrix.innertube.models.PlaylistItem
import com.musicplayer.lytrix.innertube.models.SongItem
import com.musicplayer.lytrix.innertube.models.VideoItem
import com.musicplayer.lytrix.innertube.models.YTItem
import com.musicplayer.lytrix.innertube.pages.ArtistItemsPageLayout
import com.musicplayer.lytrix.model.JioSaavnSong

@Composable
fun ArtistItemsScreen(
    viewModel: PlayerViewModel,
    browseId: String,
    params: String?,
    onNavigateBack: () -> Unit,
    onNavigateToArtist: (String) -> Unit = {},
) {
    val context = LocalContext.current
    var showSongBottomSheet by remember { mutableStateOf<JioSaavnSong?>(null) }
    var showYtPlaylistBottomSheet by remember { mutableStateOf<YTItem?>(null) }

    val page by viewModel.artistItemsPage.collectAsState()

    LaunchedEffect(browseId, params) {
        val decodedBrowseId = java.net.URLDecoder.decode(browseId, "UTF-8")
        val decodedParams = if (params == "null" || params?.isBlank() == true) null else java.net.URLDecoder.decode(params, "UTF-8")
        viewModel.clearArtistItems()
        viewModel.loadArtistItems(decodedBrowseId, decodedParams)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
            ) {
                TopAppBar(
                    title = {
                        Text(
                            text = page?.title ?: "Loading...",
                            style = MaterialTheme.typography.titleLargeEmphasized,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )

                if (page == null) {
                    LytrixSongListShimmer()
                } else {
                    val itemsPage = page!!
                    val songsOnly = remember(itemsPage.items) {
                        itemsPage.items.filterIsInstance<SongItem>()
                    }
                    val playSongsQueue: (List<SongItem>, Int) -> Unit = { list, idx ->
                        if (list.isNotEmpty()) {
                            val tracks = list.map { s ->
                                PlayerViewModel.YtMusicTrack(
                                    id = s.id,
                                    title = s.title,
                                    artist = s.artists.joinToString(", ") { it.name }.ifBlank { "Song" },
                                    thumbnailUrl = s.thumbnail.orEmpty(),
                                    playCount = s.viewCountText.orEmpty(),
                                    itemType = "Song",
                                )
                            }
                            viewModel.playLytrixPlaylistWithAd(tracks, idx.coerceIn(0, tracks.lastIndex), context)
                        }
                    }

                    if (itemsPage.layout == ArtistItemsPageLayout.LIST) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 140.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (songsOnly.isNotEmpty()) {
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Button(
                                            onClick = { playSongsQueue(songsOnly, 0) },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(100.dp),
                                        ) {
                                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Play all")
                                        }
                                        FilledTonalButton(
                                            onClick = { playSongsQueue(songsOnly.shuffled(), 0) },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(100.dp),
                                        ) {
                                            Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Shuffle")
                                        }
                                    }
                                }
                            }
                            itemsIndexed(itemsPage.items) { idx, item ->
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
                                val sub = when (item) {
                                    is SongItem -> item.artists.joinToString(", ") { it.name }.ifBlank { "Song" }
                                    is VideoItem -> item.authors?.joinToString(", ") { it.name }?.ifBlank { "Video" } ?: "Video"
                                    is PlaylistItem -> item.author?.name?.takeIf { it.isNotBlank() }?.let { "Playlist • $it" } ?: "Playlist"
                                    is AlbumItem -> item.artists?.joinToString(", ") { it.name }?.takeIf { it.isNotBlank() }?.let { "Album • $it" } ?: "Album"
                                    else -> ""
                                }
                                val views = when (item) {
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
                                    else -> ""
                                }
                                val isVideo = item is VideoItem

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .combinedClickable(
                                            onClick = {
                                                when (item) {
                                                    is SongItem -> {
                                                        val sIdx = songsOnly.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
                                                        playSongsQueue(songsOnly.ifEmpty { listOf(item) }, sIdx)
                                                    }
                                                    is VideoItem -> {
                                                        viewModel.playLytrixSong(
                                                            LytrixSongItem(id, title, sub, "", thumb.orEmpty()),
                                                            context,
                                                        )
                                                    }
                                                    is PlaylistItem -> {
                                                        globalActiveYtCover = thumb.orEmpty()
                                                        viewModel.ytPlaylistType.value = "Playlist"
                                                        viewModel.loadYtPlaylistDetails(id, "Playlist", title)
                                                    }
                                                    is AlbumItem -> {
                                                        globalActiveYtCover = thumb.orEmpty()
                                                        viewModel.ytPlaylistType.value = "Album"
                                                        viewModel.loadYtPlaylistDetails(id, "Album", title)
                                                    }
                                                    else -> Unit
                                                }
                                            },
                                            onLongClick = {
                                                when (item) {
                                                    is PlaylistItem, is AlbumItem -> showYtPlaylistBottomSheet = item
                                                    is SongItem -> showSongBottomSheet = JioSaavnSong(id, title, sub, thumb.orEmpty(), "", item.album?.id, item.album?.name)
                                                    is VideoItem -> showSongBottomSheet = JioSaavnSong(id, title, sub, thumb.orEmpty(), "")
                                                    else -> Unit
                                                }
                                            },
                                        )
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    AsyncImage(
                                        model = thumb,
                                        contentDescription = title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(width = if (isVideo) 96.dp else 54.dp, height = 54.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        MetroSuffixedSubtitle(primaryText = sub, suffixText = views)
                                    }
                                    if (item is SongItem || item is VideoItem) {
                                        IconButton(
                                            onClick = {
                                                val songAlbum = (item as? SongItem)?.album
                                                showSongBottomSheet = JioSaavnSong(id, title, sub, thumb.orEmpty(), "", songAlbum?.id, songAlbum?.name)
                                            },
                                        ) {
                                            Icon(Icons.Rounded.MoreVert, contentDescription = "More")
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 140.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            items(itemsPage.items) { item ->
                                val isCircle = item is ArtistItem
                                val isVideo = item is VideoItem
                                val itemImage = when (item) {
                                    is AlbumItem -> item.thumbnail
                                    is VideoItem -> item.thumbnail
                                    is PlaylistItem -> item.thumbnail
                                    is ArtistItem -> item.thumbnail
                                    is SongItem -> item.thumbnail
                                    else -> null
                                }
                                val itemTitle = when (item) {
                                    is AlbumItem -> item.title
                                    is VideoItem -> item.title
                                    is PlaylistItem -> item.title
                                    is ArtistItem -> item.title
                                    is SongItem -> item.title
                                    else -> ""
                                }
                                val itemSub = when (item) {
                                    is AlbumItem -> listOfNotNull("Album", item.year?.toString()).joinToString(" • ")
                                    is VideoItem -> item.authors?.joinToString(", ") { it.name }?.ifBlank { "Video" } ?: "Video"
                                    is PlaylistItem -> item.author?.name?.takeIf { it.isNotBlank() }?.let { "Playlist • $it" } ?: "Playlist"
                                    is ArtistItem -> item.monthlyListenerCountText?.takeIf { it.isNotBlank() } ?: "Artist"
                                    is SongItem -> item.artists.joinToString(", ") { it.name }.ifBlank { "Song" }
                                    else -> ""
                                }
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .combinedClickable(
                                            onClick = {
                                                when (item) {
                                                    is ArtistItem -> onNavigateToArtist(item.id)
                                                    is AlbumItem -> {
                                                        globalActiveYtCover = itemImage.orEmpty()
                                                        viewModel.ytPlaylistType.value = "Album"
                                                        viewModel.loadYtPlaylistDetails(item.browseId, "Album", itemTitle)
                                                    }
                                                    is PlaylistItem -> {
                                                        globalActiveYtCover = itemImage.orEmpty()
                                                        viewModel.ytPlaylistType.value = "Playlist"
                                                        viewModel.loadYtPlaylistDetails(item.id, "Playlist", itemTitle)
                                                    }
                                                    is SongItem -> {
                                                        viewModel.playLytrixSong(
                                                            LytrixSongItem(item.id, itemTitle, itemSub, "", itemImage.orEmpty()),
                                                            context,
                                                        )
                                                    }
                                                    is VideoItem -> {
                                                        viewModel.playLytrixSong(
                                                            LytrixSongItem(item.id, itemTitle, itemSub, "", itemImage.orEmpty()),
                                                            context,
                                                        )
                                                    }
                                                    else -> Unit
                                                }
                                            },
                                            onLongClick = {
                                                when (item) {
                                                    is PlaylistItem, is AlbumItem -> showYtPlaylistBottomSheet = item
                                                    is SongItem -> showSongBottomSheet = JioSaavnSong(item.id, itemTitle, itemSub, itemImage.orEmpty(), "", item.album?.id, item.album?.name)
                                                    is VideoItem -> showSongBottomSheet = JioSaavnSong(item.id, itemTitle, itemSub, itemImage.orEmpty(), "")
                                                    else -> Unit
                                                }
                                            },
                                        ),
                                    horizontalAlignment = if (isCircle) Alignment.CenterHorizontally else Alignment.Start,
                                ) {
                                    AsyncImage(
                                        model = itemImage,
                                        contentDescription = itemTitle,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(if (isVideo) 16f / 9f else 1f)
                                            .clip(if (isCircle) CircleShape else RoundedCornerShape(14.dp))
                                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = itemTitle,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (itemSub.isNotBlank()) {
                                        Text(
                                            text = itemSub,
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
