@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.musicplayer.lytrix.ui.stats

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.musicplayer.lytrix.stats.ListeningInsightsData
import com.musicplayer.lytrix.stats.ListeningInsightsRepository
import com.musicplayer.lytrix.stats.RankedArtistStat
import com.musicplayer.lytrix.stats.RankedTrackStat
import com.musicplayer.lytrix.stats.StatsPeriod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

private val WrappedGroundDark = Color(0xFF0A0B0F)

private val WrappedPageAccents = listOf(
    Color(0xFF8B5CF6), // 0: Opening mosaic violet
    Color(0xFF22C55E), // 1: Minutes emerald
    Color(0xFFF59E0B), // 2: #1 Artist amber gold
    Color(0xFFE11D48), // 3: Top 5 Artists rose
    Color(0xFF38BDF8), // 4: Top 5 Tracks sky blue
    Color(0xFFA855F7), // 5: Listening Clock orchid
    Color(0xFF10B981), // 6: Share Poster mint
)

/**
 * Shared 4-column artwork mosaic backdrop reused across Wrapped cards and Taste poster.
 */
@Composable
internal fun ArtworkMosaicBackdrop(
    mosaicUrls: List<String>,
    modifier: Modifier = Modifier,
    tileAspectRatio: Float = 1.15f,
) {
    if (mosaicUrls.isEmpty()) return
    BoxWithConstraints(modifier = modifier) {
        val cols = 4
        val tileW = maxWidth / cols
        val tileH = tileW * tileAspectRatio
        val rows = ceil(maxHeight / tileH).toInt().coerceAtLeast(4)
        Column {
            repeat(rows) { r ->
                Row {
                    repeat(cols) { c ->
                        AsyncImage(
                            model = mosaicUrls[(r * cols + c) % mosaicUrls.size],
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(tileW)
                                .height(tileH)
                        )
                    }
                }
            }
        }
    }
}

/**
 * SimpMusic-inspired full-screen 7-card Lytrix Wrapped story deck with Palette seed extraction,
 * editorial artwork mosaic opening/share poster, and asymmetrical #1 track & artist showcases.
 */
@Composable
fun WrappedStoryDialog(
    onDismiss: () -> Unit,
    onOpenReceipt: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { WrappedPageAccents.size })
    val posterLayer = rememberGraphicsLayer()

    var data by remember { mutableStateOf<ListeningInsightsData?>(null) }
    var seedPaletteColor by remember { mutableStateOf<Color?>(null) }
    var isExporting by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) {
            ListeningInsightsRepository.load(context, StatsPeriod.ALL_TIME)
        }
        data = loaded
        val seedArt = loaded.topTracks.firstOrNull { !it.artworkUrl.isNullOrBlank() }?.artworkUrl
            ?: loaded.topArtists.firstOrNull { !it.artworkUrl.isNullOrBlank() }?.artworkUrl
        if (!seedArt.isNullOrBlank()) {
            seedPaletteColor = withContext(Dispatchers.IO) {
                runCatching {
                    val req = ImageRequest.Builder(context)
                        .data(seedArt)
                        .allowHardware(false)
                        .size(200)
                        .build()
                    val result = context.imageLoader.execute(req) as? SuccessResult
                    val bmp = (result?.drawable as? BitmapDrawable)?.bitmap ?: return@runCatching null
                    val palette = Palette.from(bmp).maximumColorCount(16).generate()
                    val argb = palette.getVibrantColor(
                        palette.getMutedColor(palette.getDominantColor(0xFF8B5CF6.toInt()))
                    )
                    Color(argb)
                }.getOrNull()
            }
        }
    }

    val rawPageAccent = WrappedPageAccents[pagerState.currentPage]
    val targetAccent = seedPaletteColor?.let { lerp(rawPageAccent, it, 0.35f) } ?: rawPageAccent
    val pageAccent by animateColorAsState(
        targetValue = targetAccent,
        animationSpec = tween(420, easing = EaseOutCubic),
        label = "wrappedAccent"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(WrappedGroundDark)
        ) {
            // Soft ambient top glow behind story cards
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.00f to pageAccent.copy(alpha = 0.24f),
                            0.42f to lerp(WrappedGroundDark, pageAccent, 0.08f),
                            1.00f to WrappedGroundDark
                        )
                    )
            )

            val loaded = data
            if (loaded == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = pageAccent)
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = "Composing your Wrapped story...",
                            color = Color.White.copy(alpha = 0.78f),
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                val year = remember { Calendar.getInstance().get(Calendar.YEAR) }
                val mosaicUrls = remember(loaded) {
                    (loaded.topTracks.mapNotNull { it.artworkUrl } +
                        loaded.topArtists.mapNotNull { it.artworkUrl })
                        .filter { it.isNotBlank() }
                        .distinct()
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                    // Story progress bars + Top bar
                    WrappedStoryHeader(
                        pageCount = WrappedPageAccents.size,
                        currentPage = pagerState.currentPage,
                        userName = loaded.userName,
                        year = year,
                        accentColor = pageAccent,
                        onClose = onDismiss
                    )

                    // Swipeable 7-card deck
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize()
                        ) { page ->
                            when (page) {
                                0 -> WrappedOpeningCard(loaded, year, mosaicUrls, pageAccent)
                                1 -> WrappedMinutesCard(loaded, mosaicUrls, pageAccent)
                                2 -> WrappedTopArtistHeroCard(loaded, pageAccent)
                                3 -> WrappedTopArtistsListCard(loaded, pageAccent)
                                4 -> WrappedTopTracksListCard(loaded, pageAccent)
                                5 -> WrappedListeningClockCard(loaded, pageAccent)
                                6 -> WrappedSharePosterCard(
                                    data = loaded,
                                    year = year,
                                    mosaicUrls = mosaicUrls,
                                    accentColor = pageAccent,
                                    posterLayer = posterLayer,
                                    isExporting = isExporting,
                                    onSavePoster = {
                                        if (!isExporting) {
                                            scope.launch {
                                                isExporting = true
                                                shareOrSaveStatsBitmap(
                                                    context = context,
                                                    graphicsLayer = posterLayer,
                                                    filePrefix = "Lytrix_Wrapped_$year",
                                                    shareTitle = "Save Lytrix Wrapped $year",
                                                    saveOnly = true
                                                )
                                                isExporting = false
                                            }
                                        }
                                    },
                                    onSharePoster = {
                                        if (!isExporting) {
                                            scope.launch {
                                                isExporting = true
                                                shareOrSaveStatsBitmap(
                                                    context = context,
                                                    graphicsLayer = posterLayer,
                                                    filePrefix = "Lytrix_Wrapped_$year",
                                                    shareTitle = "Share Lytrix Wrapped $year",
                                                    saveOnly = false
                                                )
                                                isExporting = false
                                            }
                                        }
                                    },
                                    onOpenReceipt = {
                                        onDismiss()
                                        onOpenReceipt()
                                    }
                                )
                            }
                        }

                        // Subtle edge tap zones (top 65% only so share card buttons never collide)
                        if (pagerState.currentPage > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .width(44.dp)
                                    .fillMaxHeight(0.65f)
                                    .clickable(
                                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        scope.launch {
                                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                        }
                                    }
                            )
                        }
                        if (pagerState.currentPage < WrappedPageAccents.lastIndex) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .width(44.dp)
                                    .fillMaxHeight(0.65f)
                                    .clickable(
                                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        scope.launch {
                                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                        }
                                    }
                            )
                        }
                    }

                    // Bottom pager controls
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0))
                                }
                            },
                            enabled = pagerState.currentPage > 0,
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White,
                                disabledContentColor = Color.White.copy(alpha = 0.25f)
                            )
                        ) {
                            Icon(Icons.Rounded.ChevronLeft, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Prev", fontSize = 13.sp)
                        }

                        Text(
                            text = "${pagerState.currentPage + 1} / ${WrappedPageAccents.size}",
                            color = Color.White.copy(alpha = 0.58f),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )

                        Button(
                            onClick = {
                                if (pagerState.currentPage < WrappedPageAccents.lastIndex) {
                                    scope.launch {
                                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                    }
                                } else {
                                    onDismiss()
                                }
                            },
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = pageAccent,
                                contentColor = Color.Black
                            )
                        ) {
                            Text(
                                text = if (pagerState.currentPage == WrappedPageAccents.lastIndex) "Done" else "Next",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            if (pagerState.currentPage < WrappedPageAccents.lastIndex) {
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Rounded.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WrappedStoryHeader(
    pageCount: Int,
    currentPage: Int,
    userName: String,
    year: Int,
    accentColor: Color,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            repeat(pageCount) { idx ->
                val fill = when {
                    idx < currentPage -> Color.White.copy(alpha = 0.72f)
                    idx == currentPage -> accentColor
                    else -> Color.White.copy(alpha = 0.20f)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(3.5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(fill)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.22f))
                        .border(1.dp, accentColor.copy(alpha = 0.55f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "LYTRIX WRAPPED · $year",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp
                    )
                    Text(
                        text = userName.ifBlank { "Music Listener" },
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White)
            }
        }
    }
}

@Composable
private fun WrappedOpeningCard(
    data: ListeningInsightsData,
    year: Int,
    mosaicUrls: List<String>,
    accentColor: Color,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFF11131B))
    ) {
        ArtworkMosaicBackdrop(
            mosaicUrls = mosaicUrls,
            modifier = Modifier.fillMaxSize().alpha(0.58f)
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.00f to accentColor.copy(alpha = 0.42f),
                        0.38f to WrappedGroundDark.copy(alpha = 0.80f),
                        0.82f to WrappedGroundDark.copy(alpha = 0.94f),
                        1.00f to WrappedGroundDark
                    )
                )
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                color = Color.White.copy(alpha = 0.12f),
                shape = RoundedCornerShape(50),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.22f))
            ) {
                Text(
                    text = "YOUR SOUND ARCHIVE IS READY",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.6.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            Column {
                Text(
                    text = year.toString(),
                    color = lerp(Color.White, accentColor, 0.28f),
                    fontSize = 96.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-4).sp,
                    lineHeight = 92.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Your year in\nsound & rhythm",
                    color = Color.White,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 38.sp
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "${data.archetype.title} — ${data.archetype.tagline}",
                    color = Color.White.copy(alpha = 0.80f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OpeningHighlightChip(
                    value = data.uniqueTracksCount.toString(),
                    label = "UNIQUE TRACKS",
                    accentColor = accentColor,
                    modifier = Modifier.weight(1f)
                )
                OpeningHighlightChip(
                    value = data.uniqueArtistsCount.toString(),
                    label = "ARTISTS",
                    accentColor = accentColor,
                    modifier = Modifier.weight(1f)
                )
                OpeningHighlightChip(
                    value = data.totalPlaysCount.toString(),
                    label = "TOTAL PLAYS",
                    accentColor = accentColor,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun OpeningHighlightChip(
    value: String,
    label: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, accentColor.copy(alpha = 0.32f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Text(
            text = value,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.62f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun WrappedMinutesCard(
    data: ListeningInsightsData,
    mosaicUrls: List<String>,
    accentColor: Color,
) {
    val totalMinutes = (data.totalPlaytimeMs / 60_000L).toInt().coerceAtLeast(0)
    val animatedMinutes by animateIntAsState(
        targetValue = totalMinutes,
        animationSpec = tween(durationMillis = 900, easing = EaseOutCubic),
        label = "wrappedMinutes"
    )
    val totalHoursText = remember(totalMinutes) {
        String.format(Locale.US, "%.1f", totalMinutes / 60f)
    }
    val audioMin = (data.audioPlaytimeMs / 60_000L).toInt().coerceAtLeast(0)
    val videoMin = (data.videoPlaytimeMs / 60_000L).toInt().coerceAtLeast(0)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFF0E1418))
    ) {
        // SimpMusic-style horizontal artwork filmstrip at top
        if (mosaicUrls.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .align(Alignment.TopCenter)
                    .alpha(0.45f)
            ) {
                val strip = mosaicUrls.take(5)
                strip.forEach { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            1f to Color(0xFF0E1418)
                        )
                    )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 26.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "TIME ON PLAYBACK",
                color = accentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.8.sp
            )

            Column {
                Text(
                    text = animatedMinutes.toString(),
                    color = Color.White,
                    fontSize = 84.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-3).sp,
                    lineHeight = 82.sp
                )
                Text(
                    text = "MINUTES",
                    color = accentColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 5.sp
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "You stayed tuned for $totalHoursText hours across ${data.totalPlaysCount} plays.",
                    color = Color.White.copy(alpha = 0.80f),
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.07f))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(20.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("CURRENT STREAK", color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp, letterSpacing = 1.sp)
                        Text("${data.currentStreakDays} days", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("BEST STREAK", color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp, letterSpacing = 1.sp)
                        Text("${data.longestStreakDays} days", color = accentColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.10f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Audio: $audioMin min",
                        color = Color.White.copy(alpha = 0.78f),
                        fontSize = 12.sp
                    )
                    Text(
                        text = "Video: $videoMin min",
                        color = Color.White.copy(alpha = 0.78f),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun WrappedTopArtistHeroCard(
    data: ListeningInsightsData,
    accentColor: Color,
) {
    val topArtist: RankedArtistStat? = data.topArtists.firstOrNull()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFF100E12))
    ) {
        if (topArtist == null) {
            Box(modifier = Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "Play a few tracks to crown your #1 artist.",
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
            }
            return
        }

        // Top 54% full-bleed hero artwork
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.56f)
                .align(Alignment.TopCenter)
        ) {
            if (!topArtist.artworkUrl.isNullOrBlank()) {
                AsyncImage(
                    model = topArtist.artworkUrl,
                    contentDescription = topArtist.artist,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.linearGradient(listOf(accentColor.copy(alpha = 0.55f), Color(0xFF1A1A26)))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.45f),
                        modifier = Modifier.size(96.dp)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.00f to Color.Black.copy(alpha = 0.15f),
                            0.45f to Color.Transparent,
                            0.78f to Color(0xFF100E12).copy(alpha = 0.75f),
                            1.00f to Color(0xFF100E12)
                        )
                    )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 26.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            Text(
                text = "#1",
                color = accentColor,
                fontSize = 68.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 64.sp
            )
            Text(
                text = "MOST PLAYED ARTIST",
                color = Color.White.copy(alpha = 0.68f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.6.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = topArtist.artist,
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 38.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(10.dp))
            Surface(
                color = accentColor.copy(alpha = 0.18f),
                shape = RoundedCornerShape(50),
                border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.45f))
            ) {
                Text(
                    text = "${topArtist.playCount} plays in rotation",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = "You kept coming back whenever their voice queued up.",
                color = Color.White.copy(alpha = 0.70f),
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun WrappedTopArtistsListCard(
    data: ListeningInsightsData,
    accentColor: Color,
) {
    val artists = data.topArtists.take(5)
    val maxPlays = (artists.firstOrNull()?.playCount ?: 1).coerceAtLeast(1)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFF130F17))
            .padding(horizontal = 22.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "TOP ARTISTS",
                color = accentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.8.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "The voices that\nshaped your queue",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 30.sp
            )
        }

        if (artists.isEmpty()) {
            Text(
                text = "No artist plays recorded yet.",
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(vertical = 32.dp)
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                artists.forEachIndexed { index, item ->
                    val ratio = (item.playCount.toFloat() / maxPlays).coerceIn(0.12f, 1f)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "#${index + 1}",
                            color = if (index == 0) accentColor else Color.White.copy(alpha = 0.55f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.width(30.dp)
                        )
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.10f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!item.artworkUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = item.artworkUrl,
                                    contentDescription = item.artist,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(Icons.Rounded.Person, contentDescription = null, tint = Color.White.copy(alpha = 0.55f))
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = item.artist,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = if (index == 0) FontWeight.Bold else FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "${item.playCount}x",
                                    color = Color.White.copy(alpha = 0.72f),
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.White.copy(alpha = 0.10f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(ratio)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(50))
                                        .background(if (index == 0) accentColor else accentColor.copy(alpha = 0.65f))
                                )
                            }
                        }
                    }
                }
            }
        }

        Text(
            text = "${data.uniqueArtistsCount} unique artists across your library",
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 12.sp
        )
    }
}

@Composable
private fun WrappedTopTracksListCard(
    data: ListeningInsightsData,
    accentColor: Color,
) {
    val tracks: List<RankedTrackStat> = data.topTracks.take(5)
    val hero = tracks.firstOrNull()
    val runnerUps = if (tracks.size > 1) tracks.drop(1) else emptyList()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFF0E121B))
    ) {
        if (hero == null) {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "No track replays logged yet.",
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 16.sp
                )
            }
            return
        }

        // Top asymmetrical #1 Track Hero Half
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.46f)
        ) {
            if (!hero.artworkUrl.isNullOrBlank()) {
                AsyncImage(
                    model = hero.artworkUrl,
                    contentDescription = hero.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.linearGradient(listOf(accentColor.copy(alpha = 0.45f), Color(0xFF141824)))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = Color.White.copy(alpha = 0.45f), modifier = Modifier.size(72.dp))
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.00f to Color.Black.copy(alpha = 0.25f),
                            0.55f to Color(0xFF0E121B).copy(alpha = 0.72f),
                            1.00f to Color(0xFF0E121B)
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 22.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "TOP TRACKS",
                    color = accentColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.8.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "#1 ON REPEAT",
                            color = accentColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.2.sp
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = hero.title,
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 25.sp
                        )
                        Text(
                            text = "${hero.artist} · ${hero.playCount} plays",
                            color = Color.White.copy(alpha = 0.78f),
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "#1",
                        color = Color.White.copy(alpha = 0.22f),
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

        // Bottom half: Ranked #2-#5 tracks
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.54f)
                .padding(horizontal = 22.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            runnerUps.forEachIndexed { i, track ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${i + 2}",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(24.dp)
                    )
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!track.artworkUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = track.artworkUrl,
                                contentDescription = track.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = Color.White.copy(alpha = 0.45f), modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = track.artist,
                            color = Color.White.copy(alpha = 0.62f),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${track.playCount} plays",
                        color = accentColor.copy(alpha = 0.90f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun WrappedListeningClockCard(
    data: ListeningInsightsData,
    accentColor: Color,
) {
    val peakHourLabel = remember(data.peakHour) {
        ListeningInsightsRepository.formatHourShort(data.peakHour)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFF110F1B))
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "LISTENING CLOCK",
                color = accentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.8.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "You're an\n${data.listeningBand.title}",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 32.sp
            )
        }

        // 24-hour radial dial canvas
        Box(
            modifier = Modifier.size(210.dp),
            contentAlignment = Alignment.Center
        ) {
            val hours = data.playsByHour
            val maxHour = max(1, hours.maxOrNull() ?: 1)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val innerR = size.minDimension * 0.27f
                val maxBarLen = size.minDimension * 0.21f

                drawCircle(
                    color = Color.White.copy(alpha = 0.08f),
                    radius = innerR + maxBarLen,
                    center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                )

                for (h in 0 until 24) {
                    val count = hours.getOrElse(h) { 0 }
                    val frac = count.toFloat() / maxHour.toFloat()
                    val angleRad = ((h * 15f) - 90f) * (PI / 180f)
                    val start = Offset(
                        x = center.x + (innerR * cos(angleRad)).toFloat(),
                        y = center.y + (innerR * sin(angleRad)).toFloat()
                    )
                    val len = innerR + 6.dp.toPx() + maxBarLen * frac
                    val end = Offset(
                        x = center.x + (len * cos(angleRad)).toFloat(),
                        y = center.y + (len * sin(angleRad)).toFloat()
                    )
                    val isPeak = h == data.peakHour
                    drawLine(
                        color = when {
                            isPeak -> accentColor
                            count > 0 -> Color.White.copy(alpha = 0.35f + 0.55f * frac)
                            else -> Color.White.copy(alpha = 0.12f)
                        },
                        start = start,
                        end = end,
                        strokeWidth = if (isPeak) 6.dp.toPx() else 3.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = peakHourLabel,
                    color = accentColor,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "PEAK HOUR",
                    color = Color.White.copy(alpha = 0.58f),
                    fontSize = 9.sp,
                    letterSpacing = 1.4.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = data.listeningBand.subtitle,
                color = Color.White.copy(alpha = 0.80f),
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
            if (!data.biggestDayLabel.isNullOrBlank()) {
                Surface(
                    color = Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = "Biggest day: ${data.biggestDayLabel} (${data.biggestDayMinutes} min)",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun WrappedSharePosterCard(
    data: ListeningInsightsData,
    year: Int,
    mosaicUrls: List<String>,
    accentColor: Color,
    posterLayer: GraphicsLayer,
    isExporting: Boolean,
    onSavePoster: () -> Unit,
    onSharePoster: () -> Unit,
    onOpenReceipt: () -> Unit,
) {
    val totalMinutes = (data.totalPlaytimeMs / 60_000L).toInt().coerceAtLeast(0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Exportable SimpMusic-style 9:16 editorial mosaic poster
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 360.dp)
                .drawWithContent {
                    posterLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(posterLayer)
                }
                .clip(RoundedCornerShape(24.dp))
                .clipToBounds()
                .background(Color(0xFF0C0D12))
        ) {
            ArtworkMosaicBackdrop(
                mosaicUrls = mosaicUrls,
                modifier = Modifier.matchParentSize().alpha(0.55f)
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0.00f to accentColor.copy(alpha = 0.45f),
                            0.35f to Color(0xFF0D0E14).copy(alpha = 0.88f),
                            1.00f to Color(0xFF090A0E).copy(alpha = 0.96f)
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 22.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = "LYTRIX WRAPPED",
                            color = accentColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.8.sp
                        )
                        Text(
                            text = year.toString(),
                            color = Color.White,
                            fontSize = 46.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-2).sp,
                            lineHeight = 44.sp
                        )
                    }
                    Surface(
                        color = accentColor.copy(alpha = 0.20f),
                        shape = RoundedCornerShape(50),
                        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.50f))
                    ) {
                        Text(
                            text = data.archetype.title,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Two-column Top Tracks & Top Artists summary
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "TOP TRACKS",
                            color = accentColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.4.sp
                        )
                        data.topTracks.take(5).forEachIndexed { idx, track ->
                            Text(
                                text = "${idx + 1}. ${track.title}",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = if (idx == 0) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (data.topTracks.isEmpty()) {
                            Text("No tracks yet", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                        }
                    }

                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "TOP ARTISTS",
                            color = accentColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.4.sp
                        )
                        data.topArtists.take(5).forEachIndexed { idx, artist ->
                            Text(
                                text = "${idx + 1}. ${artist.artist}",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = if (idx == 0) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (data.topArtists.isEmpty()) {
                            Text("No artists yet", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.16f))
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    PosterMetric("MINUTES", totalMinutes.toString(), accentColor)
                    PosterMetric("TRACKS", data.uniqueTracksCount.toString(), accentColor)
                    PosterMetric("STREAK", "${data.longestStreakDays}d", accentColor)
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    text = "LYTRIX PLAYER · ${data.userName.uppercase(Locale.US)}",
                    color = Color.White.copy(alpha = 0.50f),
                    fontSize = 9.sp,
                    letterSpacing = 1.4.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Export & cross-navigation actions (outside capture layer)
        Row(
            modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onSavePoster,
                enabled = !isExporting,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Save PNG", fontSize = 13.sp)
            }
            Button(
                onClick = onSharePoster,
                enabled = !isExporting,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color.Black),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Share", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onOpenReceipt,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = accentColor),
            modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp)
        ) {
            Icon(Icons.Rounded.ReceiptLong, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Print Listening Receipt", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun PosterMetric(label: String, value: String, accentColor: Color) {
    Column {
        Text(
            text = label,
            color = accentColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black
        )
    }
}
