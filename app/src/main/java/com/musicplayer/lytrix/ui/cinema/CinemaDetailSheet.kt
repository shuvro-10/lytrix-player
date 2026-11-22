package com.musicplayer.lytrix.ui.cinema

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.MovieFilter
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private sealed interface DetailState {
    data object Loading : DetailState
    data object Error : DetailState
    data class Ready(val detail: CinemaDetail) : DetailState
}

/** Fullscreen details page (named "sheet" to match the module layout, but it is a full page). */
@Composable
fun CinemaDetailSheet(
    item: CinemaItem,
    repository: CinemaRepository,
    servers: List<ServerSource>,
    inWatchlist: Boolean,
    onToggleWatchlist: () -> Unit,
    onBack: () -> Unit,
    onPlay: (detail: CinemaDetail, server: ServerSource, seasonNumber: Int?, episode: Episode?) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)

    val accent = cinemaAccent()
    val context = LocalContext.current
    val openTrailer = { key: String ->
        val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$key"))
        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$key"))
        try {
            context.startActivity(appIntent)
        } catch (_: Exception) {
            context.startActivity(webIntent)
        }
    }
    var reloadTick by remember { mutableIntStateOf(0) }
    val state by produceState<DetailState>(DetailState.Loading, item.id, reloadTick) {
        value = DetailState.Loading
        value = cinemaCatching { repository.loadDetail(item) }
            .fold(onSuccess = { DetailState.Ready(it) }, onFailure = { DetailState.Error })
    }
    val detail = (state as? DetailState.Ready)?.detail

    var selectedServer by remember(servers) { mutableStateOf(servers.firstOrNull()) }
    var selectedSeasonNumber by rememberSaveable { mutableIntStateOf(-1) }

    val listState = rememberLazyListState()
    val parallaxOffset = remember {
        derivedStateOf { if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset else 0 }
    }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(modifier.fillMaxSize().background(CinemaPalette.background)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = navBottom + 32.dp),
        ) {
            item(key = "header") {
                DetailHeader(
                    item = item,
                    tagline = detail?.tagline,
                    scrollOffset = { parallaxOffset.value },
                )
            }

            if (item.genres.isNotEmpty()) {
                item(key = "genres") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        items(item.genres) { GenreTag(it) }
                    }
                }
            }

            when (state) {
                DetailState.Loading -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = accent)
                    }
                }

                DetailState.Error -> item(key = "error") {
                    CinemaMessage(
                        text = "Couldn't load details.",
                        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                        actionLabel = "Retry",
                        onAction = { reloadTick++ },
                    )
                }

                is DetailState.Ready -> {
                    val d = (state as DetailState.Ready).detail
                    val isSeries = d.seasons.isNotEmpty() || d.item.kind == CinemaKind.TV

                    if (d.overview.isNotBlank()) {
                        item(key = "overview") {
                            Column {
                                SectionTitle("Synopsis")
                                ExpandableSynopsis(d.overview)
                            }
                        }
                    }

                    if (d.cast.isNotEmpty()) {
                        item(key = "cast") {
                            Column {
                                SectionTitle("Cast")
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    items(d.cast, key = { it.id }) { CastAvatar(it) }
                                }
                            }
                        }
                    }

                    if (!isSeries) {
                        item(key = "movie-actions") {
                            Column {
                                SectionTitle("Watch")
                                if (d.isUnreleased) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 20.dp, vertical = 4.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(Color(0xFFE5A00D).copy(alpha = 0.12f))
                                            .border(1.dp, Color(0xFFE5A00D).copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                                            .padding(horizontal = 14.dp, vertical = 8.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Rounded.MovieFilter,
                                                contentDescription = null,
                                                tint = Color(0xFFE5A00D),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                "Notice: New / In Theaters Title",
                                                color = Color(0xFFF6C343),
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                }

                                Button(
                                    onClick = { selectedServer?.let { onPlay(d, it, null, null) } },
                                    enabled = selectedServer != null,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(52.dp),
                                    shape = RoundedCornerShape(percent = 50),
                                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White),
                                ) {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Watch Full Movie (HD)", fontWeight = FontWeight.SemiBold)
                                }

                                if (!d.trailerYoutubeKey.isNullOrBlank()) {
                                    Spacer(Modifier.height(10.dp))
                                    FilledTonalButton(
                                        onClick = { openTrailer(d.trailerYoutubeKey) },
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(46.dp),
                                        shape = RoundedCornerShape(percent = 50),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = Color.White.copy(alpha = 0.12f),
                                            contentColor = Color.White
                                        ),
                                    ) {
                                        Icon(Icons.Rounded.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Watch Official Trailer", fontWeight = FontWeight.Medium)
                                    }
                                }
                                if (servers.isNotEmpty()) {
                                    Text(
                                        "Server",
                                        color = CinemaPalette.textSecondary,
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
                                    )
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 20.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        items(servers, key = { it.id }) { s ->
                                            CinemaChip(
                                                label = s.name,
                                                selected = s.id == selectedServer?.id,
                                                onClick = { selectedServer = s },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        val season = d.seasons.firstOrNull { it.seasonNumber == selectedSeasonNumber }
                            ?: d.seasons.firstOrNull()

                        if (!d.trailerYoutubeKey.isNullOrBlank()) {
                            item(key = "series-trailer") {
                                FilledTonalButton(
                                    onClick = { openTrailer(d.trailerYoutubeKey) },
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp).height(46.dp),
                                    shape = RoundedCornerShape(percent = 50),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = Color.White.copy(alpha = 0.1f),
                                        contentColor = Color.White
                                    ),
                                ) {
                                    Icon(Icons.Rounded.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Watch Official Trailer", fontWeight = FontWeight.Medium)
                                }
                            }
                        }

                        if (d.seasons.size > 1) {
                            item(key = "seasons") {
                                Column {
                                    SectionTitle("Seasons")
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 20.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        items(d.seasons, key = { it.seasonNumber }) { s ->
                                            CinemaChip(
                                                label = s.name.ifBlank { "Season ${s.seasonNumber}" },
                                                selected = s.seasonNumber == season?.seasonNumber,
                                                onClick = { selectedSeasonNumber = s.seasonNumber },
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item(key = "episodes-title") { SectionTitle("Episodes") }

                        if (season == null || season.episodes.isEmpty()) {
                            item(key = "no-episodes") {
                                CinemaMessage(
                                    "No episodes available yet.",
                                    Modifier.fillMaxWidth(),
                                )
                            }
                        } else {
                            items(season.episodes, key = { "${season.seasonNumber}-${it.id}" }) { episode ->
                                EpisodeCard(
                                    episode = episode,
                                    enabled = selectedServer != null,
                                    onClick = {
                                        selectedServer?.let { onPlay(d, it, season.seasonNumber, episode) }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        // Floating back / watchlist buttons.
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            RoundGlassButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            RoundGlassButton(onClick = onToggleWatchlist) {
                Icon(
                    if (inWatchlist) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                    contentDescription = "Watchlist",
                    tint = if (inWatchlist) accent else Color.White,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------

@Composable
private fun DetailHeader(item: CinemaItem, tagline: String?, scrollOffset: () -> Int) {
    val posterShape = RoundedCornerShape(16.dp)
    Box(Modifier.fillMaxWidth().height(400.dp).clipToBounds()) {
        CinemaImage(
            url = item.backdropUrl.ifBlank { item.posterUrl },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = scrollOffset() * 0.5f }, // parallax
        )
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(120.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent))),
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF0C0C0E)))),
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 20.dp)
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Box(
                Modifier
                    .width(112.dp)
                    .aspectRatio(2f / 3f)
                    .clip(posterShape)
                    .border(1.dp, CinemaPalette.border, posterShape),
            ) {
                CinemaImage(item.posterUrl.ifBlank { item.backdropUrl }, Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!tagline.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        tagline,
                        color = CinemaPalette.textSecondary,
                        fontStyle = FontStyle.Italic,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(10.dp))
                val meta = listOfNotNull(
                    item.year.takeIf { it.isNotBlank() },
                    item.duration,
                    item.seasonsCount?.let { if (it == 1) "1 Season" else "$it Seasons" },
                ).joinToString("  •  ")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RatingBadge(item.rating)
                    if (meta.isNotEmpty()) {
                        Text(meta, color = CinemaPalette.textSecondary, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun RoundGlassButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        color = CinemaPalette.textPrimary,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp),
    )
}

@Composable
private fun ExpandableSynopsis(text: String) {
    val accent = cinemaAccent()
    var expanded by rememberSaveable { mutableStateOf(false) }
    var overflowing by remember { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 20.dp)) {
        Text(
            text,
            color = Color.White.copy(alpha = 0.82f),
            style = MaterialTheme.typography.bodyMedium,
            lineHeight = 22.sp,
            maxLines = if (expanded) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) overflowing = it.hasVisualOverflow },
        )
        if (overflowing || expanded) {
            Text(
                if (expanded) "Show less" else "Read more",
                color = accent,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .clickable { expanded = !expanded }
                    .padding(top = 8.dp, bottom = 4.dp),
            )
        }
    }
}

@Composable
private fun CastAvatar(member: CastMember) {
    Column(Modifier.width(72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(CinemaPalette.surface)
                .border(1.dp, CinemaPalette.border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (member.profileUrl.isNullOrBlank()) {
                Text(
                    member.name.take(1).uppercase(),
                    color = CinemaPalette.textSecondary,
                    style = MaterialTheme.typography.titleMedium,
                )
            } else {
                CinemaImage(member.profileUrl, Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            member.name,
            color = CinemaPalette.textPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (member.character.isNotBlank()) {
            Text(
                member.character,
                color = CinemaPalette.textSecondary,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EpisodeCard(episode: Episode, enabled: Boolean, onClick: () -> Unit) {
    val accent = cinemaAccent()
    val shape = RoundedCornerShape(16.dp)
    val number = episode.episodeNumber.toString().padStart(2, '0')
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.04f))
            .border(1.dp, CinemaPalette.border, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CinemaImage(
            url = episode.stillUrl,
            modifier = Modifier
                .width(124.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "E$number  •  ${episode.title}",
                color = CinemaPalette.textPrimary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = listOfNotNull(episode.durationMin?.let { "${it}m" }, episode.airDate).joinToString("  •  ")
            if (sub.isNotEmpty()) {
                Text(sub, color = CinemaPalette.textSecondary, fontSize = 11.sp)
            }
            if (episode.overview.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    episode.overview,
                    color = CinemaPalette.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = "Play episode", tint = accent, modifier = Modifier.size(22.dp))
        }
    }
}
