package com.musicplayer.lytrix.ui.library

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.musicplayer.lytrix.model.JioSaavnSong

/**
 * 1:1 MetroList-style dedicated Podcast / Audiobook Show screen.
 * Replaces generic playlist view with a specialized podcast UI featuring:
 * - Square hero artwork
 * - Author / Channel link
 * - Subscribe / Bookmark button
 * - Play Latest button
 * - Expandable description
 * - Episode / Chapter list with duration, play next, download, and bookmark actions
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetroPodcastShowDetailScreen(
    title: String,
    author: String?,
    thumbnailUrl: Any?,
    description: String? = null,
    episodes: List<JioSaavnSong>,
    isSaved: Boolean,
    onToggleSave: () -> Unit,
    onPlayEpisode: (JioSaavnSong, List<JioSaavnSong>) -> Unit,
    onToggleLikeEpisode: ((JioSaavnSong) -> Unit)? = null,
    likedEpisodeIds: Set<String> = emptySet(),
    onSaveForLater: ((JioSaavnSong) -> Unit)? = null,
    savedForLaterIds: Set<String> = emptySet(),
    onDownloadEpisode: ((JioSaavnSong) -> Unit)? = null,
    onMoreClick: ((JioSaavnSong) -> Unit)? = null,
    onChannelClick: ((String) -> Unit)? = null,
    channelId: String? = null,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isDescriptionExpanded by remember { mutableStateOf(false) }
    var currentSavedIds by remember(savedForLaterIds) { mutableStateOf(savedForLaterIds) }
    var currentLikedIds by remember(likedEpisodeIds) { mutableStateOf(likedEpisodeIds) }
    val isAutoPlaylist = title.trim().lowercase() in setOf("episodes for later", "new episodes", "continue listening", "books for later")

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Header: Hero Artwork + Title + Author + Buttons
            item(key = "header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        AsyncImage(
                            model = thumbnailUrl,
                            contentDescription = title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(130.dp)
                                .clip(RoundedCornerShape(18.dp)),
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            author?.takeIf { it.isNotBlank() }?.let { auth ->
                                Text(
                                    text = auth,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.clickable {
                                        val targetChannel = channelId?.takeIf { it.isNotBlank() } ?: auth
                                        onChannelClick?.invoke(targetChannel)
                                    },
                                )
                            }
                            Text(
                                text = "${episodes.size} episodes",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // Action buttons: Save to Library + Play Latest
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (episodes.isNotEmpty()) {
                            Button(
                                onClick = { onPlayEpisode(episodes.first(), episodes) },
                                shape = RoundedCornerShape(20.dp),
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Play latest")
                            }
                        }
                        if (!isAutoPlaylist) {
                            FilledTonalButton(
                                onClick = onToggleSave,
                                shape = RoundedCornerShape(20.dp),
                            ) {
                                Icon(
                                    if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    contentDescription = null,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(if (isSaved) "Saved" else "Save")
                            }
                        }
                    }

                    // Expandable Description
                    description?.takeIf { it.isNotBlank() }?.let { desc ->
                        Spacer(Modifier.height(12.dp))
                        Column(modifier = Modifier.animateContentSize()) {
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                            TextButton(
                                onClick = { isDescriptionExpanded = !isDescriptionExpanded },
                                contentPadding = PaddingValues(0.dp),
                            ) {
                                Text(
                                    if (isDescriptionExpanded) "Show less" else "Show more",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Episodes",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }

            // Episode List
            if (episodes.isEmpty()) {
                item(key = "empty_episodes") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No episodes found.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                itemsIndexed(episodes, key = { index, ep -> ep.id.ifBlank { "ep_$index" } }) { index, ep ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 2.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onPlayEpisode(ep, episodes) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            AsyncImage(
                                model = ep.imageUrl.ifBlank { thumbnailUrl },
                                contentDescription = ep.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = ep.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                val sub = listOfNotNull(
                                    ep.artist.takeIf { it.isNotBlank() },
                                    ep.albumName?.takeIf { it.isNotBlank() },
                                ).joinToString(" • ")
                                Text(
                                    text = sub,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (onMoreClick != null) {
                                IconButton(
                                    onClick = { onMoreClick(ep) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.MoreVert,
                                        contentDescription = "More",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            } else {
                                if (onToggleLikeEpisode != null) {
                                    val isLiked = ep.id in currentLikedIds
                                    IconButton(
                                        onClick = {
                                            currentLikedIds = if (isLiked) currentLikedIds - ep.id else currentLikedIds + ep.id
                                            onToggleLikeEpisode(ep)
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                            contentDescription = if (isLiked) "Unlike" else "Like",
                                            tint = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                                if (onSaveForLater != null) {
                                    val isSavedLater = ep.id in currentSavedIds
                                    IconButton(
                                        onClick = {
                                            currentSavedIds = if (isSavedLater) currentSavedIds - ep.id else currentSavedIds + ep.id
                                            onSaveForLater(ep)
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            if (isSavedLater) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                            contentDescription = if (isSavedLater) "Remove from saved" else "Save for later",
                                            tint = if (isSavedLater) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
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
