@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)

package com.musicplayer.lytrix.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Explicit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.musicplayer.lytrix.innertube.models.AlbumItem
import com.musicplayer.lytrix.innertube.pages.AlbumPage
import com.musicplayer.lytrix.innertube.pages.PlaylistPage
import com.musicplayer.lytrix.model.JioSaavnSong
import com.musicplayer.lytrix.ui.icons.MetrolistIcons
import com.musicplayer.lytrix.ui.icons.MorphingMetroIcon
import java.net.URLEncoder

/**
 * Genuine Material 3 Expressive (1.5.0-rc01) port of MetroList's:
 * - AlbumContent.kt & AlbumComponents.kt
 * - PlaylistContent.kt & PlaylistComponents.kt
 * - CollectionHeader.kt & PlaylistHeaderActions.kt
 * - PlaylistOrderControls.kt & InlineSearchRow
 * - GroupedListItem.kt & CurrentSongRow.kt & SongListItem
 *
 * Uses authentic Material 3 Expressive ButtonGroup, SplitButtonLayout, ToggleButton,
 * ContainedLoadingIndicator, Emphasized typography, MotionScheme, and MetrolistIcons.
 */

private enum class MetroCollectionSort(val label: String) {
    ORIGINAL("Original"),
    TITLE("Title"),
    ARTIST("Artist"),
    DURATION("Duration"),
}

private enum class MetroSortDirection {
    ASCENDING,
    DESCENDING,
}

private data class MetroCollectionTrack(
    val track: PlayerViewModel.YtMusicTrack,
    val originalIndex: Int,
    val durationSec: Int?,
    val isExplicit: Boolean,
    val cleanArtist: String,
)

private val MetroCollectionHorizontalPadding = 16.dp

private fun upgradeCollectionArtworkUrl(url: String): String {
    if (url.isBlank()) return url
    if (url.contains("lh3.googleusercontent.com") || url.contains("yt3.ggpht.com")) {
        return url.replace(Regex("""=w\d+-h\d+[^&?]*"""), "=w1080-h1080-l90-rj")
    }
    return url
}

private fun formatCollectionTotalDuration(totalSeconds: Int): String? {
    val totalMins = totalSeconds / 60
    if (totalMins <= 0) return null
    val hours = totalMins / 60
    val remMins = totalMins % 60
    return when {
        hours > 0 && remMins > 0 -> {
            val hLabel = if (hours == 1) "1 hour" else "$hours hours"
            val mLabel = if (remMins == 1) "1 minute" else "$remMins minutes"
            "$hLabel, $mLabel"
        }
        hours > 0 -> if (hours == 1) "1 hour" else "$hours hours"
        else -> "$totalMins min"
    }
}

private fun formatTrackDuration(seconds: Int?): String? {
    if (seconds == null || seconds <= 0) return null
    val mins = seconds / 60
    val secs = seconds % 60
    return "$mins:${secs.toString().padStart(2, '0')}"
}

private fun parseDurationTextToSec(text: String): Int? {
    val parts = text.trim().split(":")
    return when (parts.size) {
        2 -> {
            val m = parts[0].toIntOrNull() ?: return null
            val s = parts[1].toIntOrNull() ?: return null
            m * 60 + s
        }
        3 -> {
            val h = parts[0].toIntOrNull() ?: return null
            val m = parts[1].toIntOrNull() ?: return null
            val s = parts[2].toIntOrNull() ?: return null
            h * 3600 + m * 60 + s
        }
        else -> null
    }
}

@Composable
fun MetroAlbumScreen(
    viewModel: PlayerViewModel,
    albumPage: AlbumPage?,
    albumTitle: String,
    albumId: String,
    tracks: List<PlayerViewModel.YtMusicTrack>,
    isLoading: Boolean,
    isSaved: Boolean,
    isFullyDownloaded: Boolean,
    onBack: () -> Unit,
    onToggleSave: (displayImage: String) -> Unit,
    onDownloadAll: () -> Unit,
    onNavigateToArtist: (String) -> Unit,
    onOpenOtherVersion: (AlbumItem) -> Unit,
    onTrackClick: (track: PlayerViewModel.YtMusicTrack, playlist: List<PlayerViewModel.YtMusicTrack>, index: Int) -> Unit,
    onTrackMenuClick: (JioSaavnSong) -> Unit,
    bottomPadding: Dp = 140.dp,
) {
    val context = LocalContext.current
    val enrichedTracks = remember(tracks, albumPage) {
        val songMap = albumPage?.songs?.associateBy { it.id }.orEmpty()
        tracks.mapIndexed { idx, yt ->
            val matched = songMap[yt.id]
            val durSec = matched?.duration ?: parseDurationTextToSec(yt.playCount)
            MetroCollectionTrack(
                track = yt,
                originalIndex = idx,
                durationSec = durSec,
                isExplicit = matched?.explicit == true,
                cleanArtist = viewModel.cleanArtistDisplayName(yt.artist),
            )
        }
    }

    val resolvedTitle = albumPage?.album?.title?.takeIf { it.isNotBlank() } ?: albumTitle
    val primaryArtistName = remember(albumPage, enrichedTracks) {
        albumPage?.album?.artists?.joinToString(", ") { it.name }?.takeIf { it.isNotBlank() }
            ?: enrichedTracks.firstOrNull()?.cleanArtist?.takeIf { it.isNotBlank() }
            ?: "Unknown Artist"
    }
    val clickableFirstArtist = remember(albumPage, primaryArtistName) {
        albumPage?.album?.artists?.firstOrNull()?.name?.takeIf { it.isNotBlank() }
            ?: primaryArtistName.split(",").firstOrNull()?.trim().orEmpty()
    }
    val savedCover = remember(resolvedTitle) {
        context.getSharedPreferences("playlist_covers", Context.MODE_PRIVATE).getString(resolvedTitle, null)
    }
    val rawArtwork = albumPage?.album?.thumbnail?.takeIf { it.isNotBlank() }
        ?: savedCover
        ?: globalActiveYtCover.takeIf { it.isNotBlank() }
        ?: tracks.firstOrNull()?.thumbnailUrl
        ?: ""
    val highResArtwork = remember(rawArtwork) { upgradeCollectionArtworkUrl(rawArtwork) }

    val metadataLine = remember(albumPage, enrichedTracks) {
        val yearStr = albumPage?.album?.year?.toString()
        val count = enrichedTracks.size
        val songsText = if (count == 1) "1 song" else "$count songs"
        val totalSeconds = enrichedTracks.sumOf { it.durationSec ?: 0 }
        val durText = formatCollectionTotalDuration(totalSeconds)
        listOfNotNull(yearStr, if (count > 0) songsText else null, durText).joinToString(" • ")
    }

    val shareUrl = if (albumId.startsWith("MPREb_") || albumId.startsWith("OLAK")) {
        "https://music.youtube.com/browse/$albumId"
    } else {
        "https://music.youtube.com/search?q=${URLEncoder.encode(resolvedTitle, "UTF-8")}"
    }

    MetroCollectionScreenCore(
        viewModel = viewModel,
        collectionId = albumId,
        resolvedTitle = resolvedTitle,
        subtitleAuthor = primaryArtistName,
        clickableArtist = clickableFirstArtist.takeIf { it.isNotBlank() },
        metadataLine = metadataLine,
        artworkUrl = highResArtwork,
        description = albumPage?.description?.takeIf { it.isNotBlank() },
        tracks = enrichedTracks,
        showTrackThumbnails = true,
        otherVersions = albumPage?.otherVersions.orEmpty(),
        isLoading = isLoading,
        isSaved = isSaved,
        isFullyDownloaded = isFullyDownloaded,
        shareUrl = shareUrl,
        downloadLabel = "Download album",
        onBack = onBack,
        onToggleSave = { onToggleSave(highResArtwork) },
        onDownloadAll = onDownloadAll,
        onNavigateToArtist = onNavigateToArtist,
        onOpenOtherVersion = onOpenOtherVersion,
        onTrackClick = onTrackClick,
        onTrackMenuClick = onTrackMenuClick,
        bottomPadding = bottomPadding,
    )
}

@Composable
fun MetroPlaylistScreen(
    viewModel: PlayerViewModel,
    playlistPage: PlaylistPage?,
    playlistTitle: String,
    playlistId: String,
    tracks: List<PlayerViewModel.YtMusicTrack>,
    isLoading: Boolean,
    isSaved: Boolean,
    isFullyDownloaded: Boolean,
    onBack: () -> Unit,
    onToggleSave: (displayImage: String) -> Unit,
    onDownloadAll: () -> Unit,
    onNavigateToArtist: (String) -> Unit,
    onTrackClick: (track: PlayerViewModel.YtMusicTrack, playlist: List<PlayerViewModel.YtMusicTrack>, index: Int) -> Unit,
    onTrackMenuClick: (JioSaavnSong) -> Unit,
    bottomPadding: Dp = 140.dp,
) {
    val context = LocalContext.current
    val enrichedTracks = remember(tracks, playlistPage) {
        val songMap = playlistPage?.songs?.associateBy { it.id }.orEmpty()
        tracks.mapIndexed { idx, yt ->
            val matched = songMap[yt.id]
            val durSec = matched?.duration ?: parseDurationTextToSec(yt.playCount)
            MetroCollectionTrack(
                track = yt,
                originalIndex = idx,
                durationSec = durSec,
                isExplicit = matched?.explicit == true,
                cleanArtist = viewModel.cleanArtistDisplayName(yt.artist),
            )
        }
    }

    val resolvedTitle = playlistPage?.playlist?.title?.takeIf { it.isNotBlank() } ?: playlistTitle
    val authorName = playlistPage?.playlist?.author?.name?.takeIf { it.isNotBlank() } ?: "YouTube Music"
    val clickableAuthor = playlistPage?.playlist?.author?.name?.takeIf { it.isNotBlank() && it != "YouTube Music" }

    val savedCover = remember(resolvedTitle) {
        context.getSharedPreferences("playlist_covers", Context.MODE_PRIVATE).getString(resolvedTitle, null)
    }
    val rawArtwork = playlistPage?.playlist?.thumbnail?.takeIf { it.isNotBlank() }
        ?: savedCover
        ?: globalActiveYtCover.takeIf { it.isNotBlank() }
        ?: tracks.firstOrNull()?.thumbnailUrl
        ?: ""
    val highResArtwork = remember(rawArtwork) { upgradeCollectionArtworkUrl(rawArtwork) }

    val metadataLine = remember(playlistPage, enrichedTracks) {
        val count = enrichedTracks.size
        val songsText = playlistPage?.playlist?.songCountText?.takeIf { it.isNotBlank() }
            ?: if (count == 1) "1 song" else "$count songs"
        val totalSeconds = enrichedTracks.sumOf { it.durationSec ?: 0 }
        val durText = formatCollectionTotalDuration(totalSeconds)
        listOfNotNull(if (count > 0 || playlistPage?.playlist?.songCountText != null) songsText else null, durText)
            .joinToString(" • ")
    }

    val cleanId = playlistId.removePrefix("VL")
    val shareUrl = if (cleanId.isNotBlank()) {
        "https://music.youtube.com/playlist?list=$cleanId"
    } else {
        "https://music.youtube.com/search?q=${URLEncoder.encode(resolvedTitle, "UTF-8")}"
    }

    MetroCollectionScreenCore(
        viewModel = viewModel,
        collectionId = playlistId,
        resolvedTitle = resolvedTitle,
        subtitleAuthor = authorName,
        clickableArtist = clickableAuthor,
        metadataLine = metadataLine,
        artworkUrl = highResArtwork,
        description = playlistPage?.playlist?.description?.takeIf { it.isNotBlank() },
        tracks = enrichedTracks,
        showTrackThumbnails = true,
        otherVersions = emptyList(),
        isLoading = isLoading,
        isSaved = isSaved,
        isFullyDownloaded = isFullyDownloaded,
        shareUrl = shareUrl,
        downloadLabel = "Download playlist",
        originalSortLabel = "Playlist order",
        onBack = onBack,
        onToggleSave = { onToggleSave(highResArtwork) },
        onDownloadAll = onDownloadAll,
        onNavigateToArtist = onNavigateToArtist,
        onOpenOtherVersion = {},
        onTrackClick = onTrackClick,
        onTrackMenuClick = onTrackMenuClick,
        bottomPadding = bottomPadding,
    )
}

@Composable
fun MetroLibraryPlaylistScreen(
    viewModel: PlayerViewModel,
    playlistName: String,
    dbType: String,
    artworkUrl: String,
    songs: List<JioSaavnSong>,
    isFullyDownloaded: Boolean,
    isSpotifyPlaylist: Boolean,
    source: String = if (isSpotifyPlaylist) "Spotify" else "Custom",
    isEditable: Boolean = !isSpotifyPlaylist && !source.equals("YouTube", ignoreCase = true) && !source.equals("JioSaavn", ignoreCase = true) && !source.equals("Spotify", ignoreCase = true) && !dbType.equals("Album", ignoreCase = true),
    onBack: () -> Unit,
    onPlayTrack: (song: JioSaavnSong, list: List<JioSaavnSong>, index: Int) -> Unit,
    onShufflePlay: (list: List<JioSaavnSong>) -> Unit,
    onTrackMenuClick: (JioSaavnSong) -> Unit,
    onTrackLongClick: (JioSaavnSong) -> Unit,
    onDownloadAll: () -> Unit,
    onRenameClick: () -> Unit,
    onChangeCoverClick: () -> Unit,
    onSyncSpotifyClick: (() -> Unit)? = null,
    onSyncYouTubeClick: (() -> Unit)? = null,
    onSyncSaavnClick: (() -> Unit)? = null,
    onDeleteClick: () -> Unit,
    bottomPadding: Dp = 140.dp,
) {
    val songById = remember(songs) { songs.associateBy { it.id } }
    val downloadUrlMap = remember(songs) { songs.associate { it.id to it.downloadUrl } }
    val enrichedTracks = remember(songs) {
        songs.mapIndexed { idx, s ->
            MetroCollectionTrack(
                track = PlayerViewModel.YtMusicTrack(
                    id = s.id,
                    title = s.name,
                    artist = s.artist,
                    thumbnailUrl = s.imageUrl,
                ),
                originalIndex = idx,
                durationSec = null,
                isExplicit = false,
                cleanArtist = viewModel.cleanArtistDisplayName(s.artist),
            )
        }
    }
    val metadataLine = remember(songs.size, dbType, source) {
        val count = songs.size
        val label = if (count == 1) "1 track" else "$count tracks"
        when {
            source.equals("YouTube", ignoreCase = true) -> "YouTube • $label"
            source.equals("Spotify", ignoreCase = true) -> "Spotify • $label"
            source.equals("JioSaavn", ignoreCase = true) -> "JioSaavn • $label"
            else -> "$dbType • $label"
        }
    }
    val subtitleAuthor = remember(source, dbType) {
        when {
            source.equals("Spotify", ignoreCase = true) -> "Spotify $dbType"
            source.equals("YouTube", ignoreCase = true) -> "YouTube $dbType"
            source.equals("JioSaavn", ignoreCase = true) -> "JioSaavn $dbType"
            else -> "Your Library"
        }
    }

    MetroCollectionScreenCore(
        viewModel = viewModel,
        collectionId = "lib_$playlistName",
        resolvedTitle = playlistName,
        subtitleAuthor = subtitleAuthor,
        clickableArtist = null,
        metadataLine = metadataLine,
        artworkUrl = artworkUrl,
        description = null,
        tracks = enrichedTracks,
        showTrackThumbnails = true,
        otherVersions = emptyList(),
        isLoading = false,
        isSaved = true,
        isFullyDownloaded = isFullyDownloaded,
        shareUrl = "",
        downloadLabel = "Download all",
        originalSortLabel = "Recently added",
        onBack = onBack,
        onToggleSave = {},
        onDownloadAll = onDownloadAll,
        onNavigateToArtist = {},
        onOpenOtherVersion = {},
        onTrackClick = { clicked, orderedList, idx ->
            val mapped = orderedList.mapNotNull { t ->
                songById[t.id] ?: JioSaavnSong(t.id, t.title, t.artist, t.thumbnailUrl, downloadUrlMap[t.id].orEmpty())
            }
            val clickedSong = songById[clicked.id] ?: mapped.getOrNull(idx) ?: return@MetroCollectionScreenCore
            onPlayTrack(clickedSong, mapped, idx.coerceIn(0, (mapped.size - 1).coerceAtLeast(0)))
        },
        onShuffleOverride = { orderedList ->
            val mapped = orderedList.mapNotNull { t ->
                songById[t.id] ?: JioSaavnSong(t.id, t.title, t.artist, t.thumbnailUrl, downloadUrlMap[t.id].orEmpty())
            }
            if (mapped.isNotEmpty()) onShufflePlay(mapped)
        },
        onTrackMenuClick = onTrackMenuClick,
        onTrackLongClick = if (isEditable) {
            { yt -> songById[yt.id]?.let(onTrackLongClick) }
        } else null,
        onCoverAction = if (isEditable) onChangeCoverClick else null,
        secondaryHeaderIcon = if (isEditable) Icons.Rounded.Edit else null,
        secondaryHeaderDescription = if (isEditable) "Rename $dbType" else null,
        onSecondaryHeaderAction = if (isEditable) onRenameClick else null,
        showSaveMenuItem = false,
        showShareMenuItem = false,
        applyStatusBarPadding = false,
        downloadUrlMap = downloadUrlMap,
        extraOverflowMenuItems = { dismiss ->
            if (isEditable) {
                DropdownMenuItem(
                    text = { Text("Rename $dbType") },
                    leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                    onClick = {
                        dismiss()
                        onRenameClick()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Change cover") },
                    leadingIcon = { Icon(Icons.Rounded.Image, contentDescription = null) },
                    onClick = {
                        dismiss()
                        onChangeCoverClick()
                    },
                )
            }
            if (source.equals("YouTube", ignoreCase = true) && onSyncYouTubeClick != null) {
                DropdownMenuItem(
                    text = { Text("Sync from YouTube", color = Color(0xFFFF0033)) },
                    leadingIcon = { Icon(Icons.Rounded.Sync, contentDescription = null, tint = Color(0xFFFF0033)) },
                    onClick = {
                        dismiss()
                        onSyncYouTubeClick()
                    },
                )
            }
            if ((isSpotifyPlaylist || source.equals("Spotify", ignoreCase = true)) && onSyncSpotifyClick != null) {
                DropdownMenuItem(
                    text = { Text("Sync from Spotify", color = Color(0xFF1DB954)) },
                    leadingIcon = { Icon(Icons.Rounded.Sync, contentDescription = null, tint = Color(0xFF1DB954)) },
                    onClick = {
                        dismiss()
                        onSyncSpotifyClick()
                    },
                )
            }
            if (source.equals("JioSaavn", ignoreCase = true) && onSyncSaavnClick != null) {
                DropdownMenuItem(
                    text = { Text("Sync from JioSaavn", color = Color(0xFF2BC5B4)) },
                    leadingIcon = { Icon(Icons.Rounded.Sync, contentDescription = null, tint = Color(0xFF2BC5B4)) },
                    onClick = {
                        dismiss()
                        onSyncSaavnClick()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("Delete $dbType", color = MaterialTheme.colorScheme.error) },
                leadingIcon = {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    dismiss()
                    onDeleteClick()
                },
            )
        },
        bottomPadding = bottomPadding,
    )
}

@Composable
fun MetroSaavnCollectionScreen(
    viewModel: PlayerViewModel,
    collectionId: String,
    title: String,
    subtitle: String,
    artworkUrl: String,
    songs: List<JioSaavnSong>,
    isLoading: Boolean,
    isSaved: Boolean,
    isFullyDownloaded: Boolean,
    isAlbum: Boolean = false,
    year: String = "",
    fansText: String = "",
    description: String = "",
    onBack: () -> Unit,
    onToggleSave: (displayImage: String) -> Unit,
    onDownloadAll: () -> Unit,
    onPlayTrack: (song: JioSaavnSong, list: List<JioSaavnSong>, index: Int) -> Unit,
    onTrackMenuClick: (JioSaavnSong) -> Unit,
    bottomPadding: Dp = 140.dp,
) {
    val songById = remember(songs) { songs.associateBy { it.id } }
    val downloadUrlMap = remember(songs) { songs.associate { it.id to it.downloadUrl } }
    val enrichedTracks = remember(songs) {
        songs.mapIndexed { idx, s ->
            MetroCollectionTrack(
                track = PlayerViewModel.YtMusicTrack(
                    id = s.id,
                    title = s.name,
                    artist = s.artist,
                    thumbnailUrl = s.imageUrl,
                ),
                originalIndex = idx,
                durationSec = null,
                isExplicit = false,
                cleanArtist = viewModel.cleanArtistDisplayName(s.artist),
            )
        }
    }
    val displayArt = artworkUrl.ifBlank { songs.firstOrNull()?.imageUrl.orEmpty() }
    val metadataLine = remember(songs.size, isAlbum, year, fansText) {
        val typeLabel = if (isAlbum) "Album" else "Playlist"
        val songLabel = when {
            songs.isEmpty() -> null
            songs.size == 1 -> "1 song"
            else -> "${songs.size} songs"
        }
        listOfNotNull(
            typeLabel,
            year.takeIf { it.isNotBlank() },
            songLabel,
            fansText.takeIf { it.isNotBlank() },
        ).joinToString(" • ")
    }

    MetroCollectionScreenCore(
        viewModel = viewModel,
        collectionId = "saavn_$collectionId",
        resolvedTitle = title,
        subtitleAuthor = subtitle.ifBlank { "JioSaavn" },
        clickableArtist = null,
        metadataLine = metadataLine,
        artworkUrl = displayArt,
        description = description.takeIf { it.isNotBlank() },
        tracks = enrichedTracks,
        showTrackThumbnails = true,
        otherVersions = emptyList(),
        isLoading = isLoading,
        isSaved = isSaved,
        isFullyDownloaded = isFullyDownloaded,
        shareUrl = "",
        downloadLabel = "Download all",
        originalSortLabel = "Original",
        onBack = onBack,
        onToggleSave = { onToggleSave(displayArt) },
        onDownloadAll = onDownloadAll,
        onNavigateToArtist = {},
        onOpenOtherVersion = {},
        onTrackClick = { clicked, orderedList, idx ->
            val mapped = orderedList.mapNotNull { t ->
                songById[t.id] ?: JioSaavnSong(t.id, t.title, t.artist, t.thumbnailUrl, downloadUrlMap[t.id].orEmpty())
            }
            val clickedSong = songById[clicked.id] ?: mapped.getOrNull(idx) ?: return@MetroCollectionScreenCore
            onPlayTrack(clickedSong, mapped, idx.coerceIn(0, (mapped.size - 1).coerceAtLeast(0)))
        },
        onTrackMenuClick = onTrackMenuClick,
        showShareMenuItem = false,
        downloadUrlMap = downloadUrlMap,
        bottomPadding = bottomPadding,
    )
}

@Composable
private fun MetroCollectionScreenCore(
    viewModel: PlayerViewModel,
    collectionId: String,
    resolvedTitle: String,
    subtitleAuthor: String,
    clickableArtist: String?,
    metadataLine: String,
    artworkUrl: String,
    description: String?,
    tracks: List<MetroCollectionTrack>,
    showTrackThumbnails: Boolean,
    otherVersions: List<AlbumItem>,
    isLoading: Boolean,
    isSaved: Boolean,
    isFullyDownloaded: Boolean,
    shareUrl: String,
    downloadLabel: String,
    originalSortLabel: String = "Original",
    onBack: () -> Unit,
    onToggleSave: () -> Unit,
    onDownloadAll: () -> Unit,
    onNavigateToArtist: (String) -> Unit,
    onOpenOtherVersion: (AlbumItem) -> Unit,
    onTrackClick: (track: PlayerViewModel.YtMusicTrack, playlist: List<PlayerViewModel.YtMusicTrack>, index: Int) -> Unit,
    onTrackMenuClick: (JioSaavnSong) -> Unit,
    bottomPadding: Dp,
    onShuffleOverride: ((List<PlayerViewModel.YtMusicTrack>) -> Unit)? = null,
    onTrackLongClick: ((PlayerViewModel.YtMusicTrack) -> Unit)? = null,
    onCoverAction: (() -> Unit)? = null,
    secondaryHeaderIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    secondaryHeaderDescription: String? = null,
    onSecondaryHeaderAction: (() -> Unit)? = null,
    showSaveMenuItem: Boolean = true,
    showShareMenuItem: Boolean = true,
    applyStatusBarPadding: Boolean = true,
    downloadUrlMap: Map<String, String> = emptyMap(),
    extraOverflowMenuItems: (@Composable (dismiss: () -> Unit) -> Unit)? = null,
) {
    val context = LocalContext.current
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()

    var sort by remember(collectionId) { mutableStateOf(MetroCollectionSort.ORIGINAL) }
    var sortDirection by remember(collectionId) { mutableStateOf(MetroSortDirection.ASCENDING) }
    var isSearchOpen by remember(collectionId) { mutableStateOf(false) }
    var searchQuery by remember(collectionId) { mutableStateOf("") }
    var showOverflowMenu by remember { mutableStateOf(false) }

    val toggleSearch: () -> Unit = {
        isSearchOpen = !isSearchOpen
        if (!isSearchOpen) searchQuery = ""
    }

    BackHandler {
        if (isSearchOpen) toggleSearch() else onBack()
    }

    val activeListState = rememberLazyListState()

    val sortedTracks = remember(tracks, sort, sortDirection) {
        val base = when (sort) {
            MetroCollectionSort.ORIGINAL -> tracks
            MetroCollectionSort.TITLE -> tracks.sortedBy { it.track.title.lowercase() }
            MetroCollectionSort.ARTIST -> tracks.sortedBy { it.cleanArtist.lowercase() }
            MetroCollectionSort.DURATION -> tracks.sortedBy { it.durationSec ?: Int.MAX_VALUE }
        }
        if (sort != MetroCollectionSort.ORIGINAL && sortDirection == MetroSortDirection.DESCENDING) {
            base.reversed()
        } else {
            base
        }
    }

    val displayedTracks = remember(sortedTracks, searchQuery) {
        if (searchQuery.isBlank()) {
            sortedTracks
        } else {
            val q = searchQuery.trim()
            sortedTracks.filter {
                it.track.title.contains(q, ignoreCase = true) ||
                    it.cleanArtist.contains(q, ignoreCase = true)
            }
        }
    }

    var titleBottom by remember(collectionId) { mutableFloatStateOf(Float.POSITIVE_INFINITY) }
    var listTop by remember { mutableFloatStateOf(0f) }
    val showCollapsedTitle by remember(collectionId, activeListState) {
        derivedStateOf { activeListState.firstVisibleItemIndex > 0 || titleBottom <= listTop }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (applyStatusBarPadding) Modifier.statusBarsPadding() else Modifier),
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 64.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    ContainedLoadingIndicator()
                }
            } else {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 64.dp),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    val isMedium = maxWidth >= 600.dp

                    LaunchedEffect(isSearchOpen) {
                        if (isSearchOpen) {
                            activeListState.scrollToItem(0)
                        }
                    }

                    val playbackActions: @Composable () -> Unit = {
                        MetroExpressiveHeaderActions(
                            onShuffle = {
                                val list = displayedTracks.map { it.track }
                                if (list.isNotEmpty()) {
                                    if (onShuffleOverride != null) {
                                        onShuffleOverride(list)
                                    } else {
                                        val shuffled = list.shuffled()
                                        onTrackClick(shuffled.first(), shuffled, 0)
                                    }
                                }
                            },
                            onPlayAll = {
                                val list = displayedTracks.map { it.track }
                                if (list.isNotEmpty()) {
                                    onTrackClick(list.first(), list, 0)
                                }
                            },
                            showPlayback = displayedTracks.isNotEmpty(),
                            isFavorite = isSaved,
                            onFavorite = onToggleSave,
                            buttonHeight = 46.dp,
                            secondaryIcon = secondaryHeaderIcon,
                            secondaryDescription = secondaryHeaderDescription,
                            onSecondaryAction = onSecondaryHeaderAction,
                        )
                    }

                    LazyColumn(
                        state = activeListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .imePadding()
                            .onGloballyPositioned { listTop = it.positionInWindow().y },
                        contentPadding = PaddingValues(bottom = bottomPadding),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val itemModifier = Modifier
                            .widthIn(max = 960.dp)
                            .fillMaxWidth()

                        // 1. MetroList Collection Header (hidden while searching so results stay pinned at top!)
                        if (!isSearchOpen) {
                            item(key = "collection_header") {
                                Box(itemModifier) {
                                    MetroCollectionHeader(
                                        title = resolvedTitle,
                                        artistText = subtitleAuthor,
                                        metadataText = metadataLine,
                                        artworkUrl = artworkUrl,
                                        description = description,
                                        isMedium = isMedium,
                                        onArtistClick = clickableArtist?.let { artist ->
                                            { onNavigateToArtist(artist) }
                                        },
                                        onCoverAction = onCoverAction,
                                        titleModifier = Modifier.onGloballyPositioned {
                                            titleBottom = it.positionInWindow().y + it.size.height
                                        },
                                        actions = playbackActions,
                                    )
                                }
                            }
                        }

                        // 2. MetroList SplitButtonLayout Order Controls + InlineSearchRow
                        item(key = "order_controls") {
                            Box(
                                itemModifier.padding(
                                    horizontal = MetroCollectionHorizontalPadding,
                                    vertical = 4.dp,
                                ),
                            ) {
                                MetroInlineSearchRow(
                                    isSearchOpen = isSearchOpen,
                                    query = searchQuery,
                                    onQueryChange = { searchQuery = it },
                                    onClose = toggleSearch,
                                    onSearchToggle = toggleSearch,
                                ) {
                                    MetroExpressiveOrderControls(
                                        sort = sort,
                                        sortDirection = sortDirection,
                                        originalSortLabel = originalSortLabel,
                                        onSortChange = { sort = it },
                                        onSortDirectionChange = { sortDirection = it },
                                    )
                                }
                            }
                        }

                        // 3. MetroList GroupedListItem + SongListItem Tracklist
                        if (displayedTracks.isEmpty() && searchQuery.isNotBlank()) {
                            item(key = "search_no_results") {
                                Box(
                                    modifier = itemModifier.padding(vertical = 32.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "No results found",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        } else {
                            itemsIndexed(
                                items = displayedTracks,
                                key = { index, item -> "${item.track.id}_$index" },
                            ) { index, item ->
                                val isCurrent = currentSong?.originalId == item.track.id ||
                                    currentSong?.id?.toString() == item.track.id
                                val isTrackPlaying = isCurrent && isPlaying

                                MetroGroupedListItem(
                                    index = index,
                                    count = displayedTracks.size,
                                    isCurrent = isCurrent,
                                    modifier = itemModifier.padding(horizontal = MetroCollectionHorizontalPadding),
                                ) {
                                    MetroCollectionSongListItem(
                                        track = item,
                                        displayIndex = index,
                                        collectionArtworkUrl = artworkUrl,
                                        showThumbnail = showTrackThumbnails,
                                        isCurrent = isCurrent,
                                        isPlaying = isTrackPlaying,
                                        onClick = {
                                            val playList = sortedTracks.map { it.track }
                                            val targetIdx = playList.indexOfFirst { it.id == item.track.id }.coerceAtLeast(0)
                                            onTrackClick(item.track, playList, targetIdx)
                                        },
                                        onLongClick = onTrackLongClick?.let { cb -> { cb(item.track) } },
                                        onMoreClick = {
                                            onTrackMenuClick(
                                                JioSaavnSong(
                                                    id = item.track.id,
                                                    name = item.track.title,
                                                    artist = item.cleanArtist,
                                                    imageUrl = item.track.thumbnailUrl.ifBlank { artworkUrl },
                                                    downloadUrl = downloadUrlMap[item.track.id].orEmpty(),
                                                ),
                                            )
                                        },
                                    )
                                }
                            }
                        }

                        // 4. Other versions (Albums)
                        if (otherVersions.isNotEmpty() && searchQuery.isBlank()) {
                            item(key = "other_versions_title") {
                                Box(itemModifier) {
                                    Text(
                                        text = "Other versions",
                                        style = MaterialTheme.typography.titleLargeEmphasized,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(
                                            start = MetroCollectionHorizontalPadding,
                                            end = MetroCollectionHorizontalPadding,
                                            top = 24.dp,
                                            bottom = 12.dp,
                                        ),
                                    )
                                }
                            }

                            item(key = "other_versions_list") {
                                Box(itemModifier) {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = MetroCollectionHorizontalPadding),
                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        items(otherVersions, key = { it.browseId }) { other ->
                                            MetroOtherVersionItem(
                                                album = other,
                                                onClick = { onOpenOtherVersion(other) },
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item(key = "bottom_spacer") {
                            Spacer(Modifier.height(24.dp))
                        }
                    }
                }
            }

            // MetroList Top Navigation Bar (CollectionHeaderNavigation)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.94f)),
            ) {
                IconButton(
                    onClick = { if (isSearchOpen) toggleSearch() else onBack() },
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 8.dp),
                ) {
                    Icon(
                        imageVector = MetrolistIcons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }

                AnimatedVisibility(
                    visible = showCollapsedTitle && !isSearchOpen && !isLoading,
                    enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
                    exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 60.dp, end = 60.dp),
                ) {
                    Text(
                        text = resolvedTitle,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (!isLoading) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 8.dp),
                    ) {
                        IconButton(
                            onClick = { showOverflowMenu = true },
                            shapes = IconButtonDefaults.shapes(),
                        ) {
                            Icon(
                                imageVector = MetrolistIcons.Default.MoreVert,
                                contentDescription = "More options",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }

                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false },
                            shape = RoundedCornerShape(16.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (isFullyDownloaded) "Downloaded" else downloadLabel) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (isFullyDownloaded) MetrolistIcons.Outlined.OfflinePin else MetrolistIcons.Outlined.Download,
                                        contentDescription = null,
                                        tint = if (isFullyDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    onDownloadAll()
                                },
                            )
                            if (showSaveMenuItem) {
                                DropdownMenuItem(
                                    text = { Text(if (isSaved) "Remove from library" else "Save to library") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (isSaved) MetrolistIcons.Filled.Favorite else MetrolistIcons.Outlined.FavoriteBorder,
                                            contentDescription = null,
                                            tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        )
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        onToggleSave()
                                    },
                                )
                            }
                            if (showShareMenuItem && shareUrl.isNotBlank()) {
                                DropdownMenuItem(
                                    text = { Text("Share") },
                                    leadingIcon = {
                                        Icon(MetrolistIcons.Outlined.Share, contentDescription = null)
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, "$resolvedTitle - $subtitleAuthor\n$shareUrl")
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Share"))
                                    },
                                )
                            }
                            extraOverflowMenuItems?.invoke { showOverflowMenu = false }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetroCollectionHeader(
    title: String,
    artistText: String,
    metadataText: String,
    artworkUrl: String,
    description: String?,
    isMedium: Boolean,
    onArtistClick: (() -> Unit)?,
    onCoverAction: (() -> Unit)? = null,
    titleModifier: Modifier = Modifier,
    actions: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MetroCollectionHorizontalPadding, vertical = 8.dp),
    ) {
        if (isMedium) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(176.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .then(
                            if (onCoverAction != null) {
                                Modifier.combinedClickable(
                                    onClick = {},
                                    onLongClick = onCoverAction,
                                )
                            } else Modifier
                        ),
                ) {
                    AsyncImage(
                        model = artworkUrl,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                MetroCollectionInfoCard(
                    modifier = Modifier.weight(1f),
                    title = title,
                    artistText = artistText,
                    metadataText = metadataText,
                    description = description,
                    onArtistClick = onArtistClick,
                    titleModifier = titleModifier,
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .then(
                        if (onCoverAction != null) {
                            Modifier.combinedClickable(
                                onClick = {},
                                onLongClick = onCoverAction,
                            )
                        } else Modifier
                    ),
            ) {
                AsyncImage(
                    model = artworkUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.height(16.dp))
            MetroCollectionInfoCard(
                title = title,
                artistText = artistText,
                metadataText = metadataText,
                description = description,
                onArtistClick = onArtistClick,
                titleModifier = titleModifier,
            )
        }
        Spacer(Modifier.height(16.dp))
        actions()
    }
}

@Composable
private fun MetroCollectionInfoCard(
    title: String,
    artistText: String,
    metadataText: String,
    description: String?,
    onArtistClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    titleModifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                modifier = titleModifier,
                style = MaterialTheme.typography.headlineMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = artistText,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (onArtistClick != null) Modifier.clickable { onArtistClick() } else Modifier,
            )
            if (metadataText.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = metadataText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            AnimatedVisibility(
                visible = !description.isNullOrBlank(),
                enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                    fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                    fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
            ) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    MetroExpandableText(text = description.orEmpty())
                }
            }
        }
    }
}

@Composable
private fun MetroExpandableText(
    text: String,
    collapsedMaxLines: Int = 2,
) {
    var expanded by remember(text) { mutableStateOf(false) }
    var hasOverflow by remember(text) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())
            .clickable(enabled = hasOverflow || expanded) { expanded = !expanded },
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else collapsedMaxLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { result ->
                if (!expanded) {
                    hasOverflow = result.hasVisualOverflow
                }
            },
        )
        if (hasOverflow || expanded) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (expanded) "Show less" else "Show more",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * Authentic Material 3 Expressive ButtonGroup + ToggleButton (verbatim from MetroList PlaylistHeaderActions.kt).
 */
@Composable
private fun MetroExpressiveHeaderActions(
    onShuffle: () -> Unit,
    onPlayAll: () -> Unit,
    showPlayback: Boolean,
    isFavorite: Boolean,
    onFavorite: () -> Unit,
    buttonHeight: Dp,
    secondaryIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    secondaryDescription: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showPlayback) {
            val labels = listOf("Shuffle", "Play all")
            val icons = listOf(MetrolistIcons.Rounded.Shuffle, MetrolistIcons.Default.PlayArrow)
            val callbacks = listOf(onShuffle, onPlayAll)

            ButtonGroup(
                overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) },
                modifier = Modifier.weight(1f),
                expandedRatio = 0.08f,
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                labels.forEachIndexed { index, label ->
                    customItem(
                        buttonGroupContent = {
                            val interactionSource = remember { MutableInteractionSource() }
                            val groupShapes = if (index == 0) {
                                ButtonGroupDefaults.connectedLeadingButtonShapes()
                            } else {
                                ButtonGroupDefaults.connectedTrailingButtonShapes()
                            }
                            val content: @Composable RowScope.() -> Unit = {
                                Icon(icons[index], contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(label, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                            }
                            Button(
                                onClick = callbacks[index],
                                interactionSource = interactionSource,
                                modifier = Modifier
                                    .weight(1f)
                                    .animateWidth(interactionSource)
                                    .height(buttonHeight),
                                shapes = ButtonDefaults.shapes(
                                    shape = groupShapes.shape,
                                    pressedShape = groupShapes.pressedShape,
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                content = content,
                            )
                        },
                        menuContent = {
                            DropdownMenuItem(text = { Text(label) }, onClick = callbacks[index])
                        },
                    )
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }

        if (secondaryIcon != null && onSecondaryAction != null) {
            FilledTonalIconButton(
                onClick = onSecondaryAction,
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier
                    .height(buttonHeight)
                    .width(48.dp),
            ) {
                Icon(
                    imageVector = secondaryIcon,
                    contentDescription = secondaryDescription,
                    modifier = Modifier.size(20.dp),
                )
            }
        } else {
            // Authentic Material 3 Expressive ToggleButton with MorphingMetroIcon (Outlined -> Filled heart!)
            ToggleButton(
                checked = isFavorite,
                onCheckedChange = { onFavorite() },
                modifier = Modifier
                    .height(buttonHeight)
                    .width(48.dp),
                shapes = ToggleButtonDefaults.shapesFor(buttonHeight),
                contentPadding = PaddingValues(0.dp),
            ) {
                MorphingMetroIcon(
                    selected = isFavorite,
                    outlinedIcon = MetrolistIcons.Outlined.FavoriteBorder,
                    filledIcon = MetrolistIcons.Filled.Favorite,
                    contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun MetroInlineSearchRow(
    isSearchOpen: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    onSearchToggle: () -> Unit,
    orderControls: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        if (!isSearchOpen) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.weight(1f)) {
                    orderControls()
                }
                FilledTonalIconButton(
                    onClick = onSearchToggle,
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(MetrolistIcons.Default.Search, contentDescription = "Search songs", modifier = Modifier.size(20.dp))
                }
            }
        } else {
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = MetrolistIcons.Outlined.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                text = "Search songs...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                        )
                    }
                    IconButton(
                        onClick = {
                            if (query.isNotEmpty()) onQueryChange("") else onClose()
                        },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = MetrolistIcons.Default.Close,
                            contentDescription = "Close search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Authentic Material 3 Expressive SplitButtonLayout (verbatim from MetroList PlaylistOrderControls.kt).
 */
@Composable
private fun MetroExpressiveOrderControls(
    sort: MetroCollectionSort,
    sortDirection: MetroSortDirection,
    originalSortLabel: String = "Original",
    onSortChange: (MetroCollectionSort) -> Unit,
    onSortDirectionChange: (MetroSortDirection) -> Unit,
) {
    fun labelFor(option: MetroCollectionSort): String =
        if (option == MetroCollectionSort.ORIGINAL) originalSortLabel else option.label

    var sortMenuExpanded by remember { mutableStateOf(false) }
    val directionEnabled = sort != MetroCollectionSort.ORIGINAL
    val arrowRotation by animateFloatAsState(
        targetValue = if (sortDirection == MetroSortDirection.ASCENDING) 180f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "sortDirectionRotation",
    )

    Box {
        SplitButtonLayout(
            leadingButton = {
                SplitButtonDefaults.TonalLeadingButton(
                    onClick = { sortMenuExpanded = true },
                ) {
                    Text(
                        text = labelFor(sort),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            trailingButton = {
                SplitButtonDefaults.TrailingButton(
                    onClick = {
                        onSortDirectionChange(
                            if (sortDirection == MetroSortDirection.ASCENDING) {
                                MetroSortDirection.DESCENDING
                            } else {
                                MetroSortDirection.ASCENDING
                            },
                        )
                    },
                    enabled = directionEnabled,
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    elevation = ButtonDefaults.filledTonalButtonElevation(),
                    shapes = SplitButtonDefaults.trailingButtonShapesFor(SplitButtonDefaults.MediumContainerHeight),
                ) {
                    Icon(
                        imageVector = MetrolistIcons.Outlined.KeyboardArrowDown,
                        contentDescription = "Sort direction",
                        modifier = Modifier
                            .size(SplitButtonDefaults.TrailingIconSize)
                            .graphicsLayer { rotationZ = arrowRotation },
                    )
                }
            },
        )

        DropdownMenu(
            expanded = sortMenuExpanded,
            onDismissRequest = { sortMenuExpanded = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            MetroCollectionSort.entries.forEach { option ->
                val isSelected = option == sort
                DropdownMenuItem(
                    text = {
                        Text(
                            text = labelFor(option),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    trailingIcon = if (isSelected) {
                        {
                            Icon(
                                imageVector = MetrolistIcons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else {
                        null
                    },
                    onClick = {
                        onSortChange(option)
                        sortMenuExpanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun MetroGroupedListItem(
    index: Int,
    count: Int,
    isCurrent: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val topRadius = if (index == 0) 16.dp else 4.dp
    val bottomRadius = if (index == count - 1) 16.dp else 4.dp

    val progress by animateFloatAsState(
        targetValue = if (isCurrent) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "groupedItemCurrentProgress",
    )
    val containerColor = lerp(
        MaterialTheme.colorScheme.surfaceContainer,
        MaterialTheme.colorScheme.primaryContainer,
        progress,
    )

    Box(modifier.padding(bottom = if (index < count - 1) 3.dp else 0.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = if (isCurrent) {
                CircleShape
            } else {
                RoundedCornerShape(
                    topStart = topRadius,
                    topEnd = topRadius,
                    bottomStart = bottomRadius,
                    bottomEnd = bottomRadius,
                )
            },
            color = containerColor,
            content = content,
        )
    }
}

@Composable
private fun MetroCollectionSongListItem(
    track: MetroCollectionTrack,
    displayIndex: Int,
    collectionArtworkUrl: String,
    showThumbnail: Boolean,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val progress by animateFloatAsState(
        targetValue = if (isCurrent) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "songRowColorProgress",
    )
    val contentColor = lerp(
        MaterialTheme.colorScheme.onSurface,
        MaterialTheme.colorScheme.onPrimaryContainer,
        progress,
    )
    val subtitleColor = lerp(
        MaterialTheme.colorScheme.onSurfaceVariant,
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f),
        progress,
    )

    val rotationTransition = rememberInfiniteTransition(label = "currentTrackRotation")
    val spinningAngle by rotationTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "currentArtSpin",
    )
    val effectiveRotation = if (isCurrent && isPlaying) spinningAngle else 0f

    val durText = formatTrackDuration(track.durationSec)
    val subtitleText = buildString {
        append(track.cleanArtist)
        if (durText != null) {
            append(" • ")
            append(durText)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(if (isCurrent) CircleShape else RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick ?: onMoreClick,
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isCurrent) {
            // MetroList Current Playing Pill: spinning circular thumbnail
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .graphicsLayer { rotationZ = effectiveRotation }
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = track.track.thumbnailUrl.ifBlank { collectionArtworkUrl },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.width(10.dp))
        } else if (showThumbnail) {
            // Playlist row: 48.dp rounded thumbnail (matches MetroList PlaylistContent)
            AsyncImage(
                model = track.track.thumbnailUrl.ifBlank { collectionArtworkUrl },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
            )
            Spacer(Modifier.width(12.dp))
        } else {
            // Album row: track number (1, 2, 3...)
            Box(
                modifier = Modifier.width(36.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${displayIndex + 1}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.track.title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                ),
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (track.isExplicit) {
                    Icon(
                        imageVector = Icons.Rounded.Explicit,
                        contentDescription = "Explicit",
                        tint = subtitleColor,
                        modifier = Modifier
                            .size(14.dp)
                            .padding(end = 3.dp),
                    )
                }
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = subtitleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        IconButton(
            onClick = onMoreClick,
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.size(38.dp),
        ) {
            Icon(
                imageVector = MetrolistIcons.Default.MoreVert,
                contentDescription = "More options",
                tint = if (isCurrent) contentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun MetroOtherVersionItem(
    album: AlbumItem,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        AsyncImage(
            model = upgradeCollectionArtworkUrl(album.thumbnail),
            contentDescription = album.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(132.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = album.title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        album.year?.let { year ->
            Text(
                text = year.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
