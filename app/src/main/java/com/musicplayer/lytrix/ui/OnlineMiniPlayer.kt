package com.musicplayer.lytrix.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.musicplayer.lytrix.R
import com.musicplayer.lytrix.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun OnlineMiniPlayer(
    song: Song,
    isPlaying: Boolean,
    isBuffering: Boolean,
    currentPosition: Long,
    duration: Long,
    accentColor: Color,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onExpand: () -> Unit,
    onDismiss: () -> Unit = {},
    viewModel: PlayerViewModel = viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val currentTheme = viewModel.playerTheme.collectAsState(initial = "Solid Default").value
    val themeFont = when (currentTheme) {
        "Cyber City" -> FontFamily(Font(R.font.font_quantico))
        "Campside Rain", "Cat Rain", "Snowy Home" -> FontFamily(Font(R.font.font_poppins))
        "Cartoon Walk", "Summer Home", "Summer Village" -> FontFamily(Font(R.font.font_montserrat))
        else -> FontFamily.Default
    }

    // ==========================================
    // 🔥 Lytrix: Image Palette Extraction
    // ==========================================
    var paletteColors by remember { mutableStateOf<List<Color>>(emptyList()) }
    val artData = rememberEmbeddedArt(context, song)

    LaunchedEffect(song.id, artData) {
        withContext(Dispatchers.IO) {
            try {
                val dataToLoad = artData ?: song.uriString
                val req = ImageRequest.Builder(context)
                    .data(dataToLoad)
                    .allowHardware(false)
                    .apply { if ((artData == null || artData == song.uriString) && song.isVideo) videoFrameMillis(1000) }
                    .build()

                val result = context.imageLoader.execute(req)
                val bitmap = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                if (bitmap != null) {
                    val palette = Palette.from(bitmap).maximumColorCount(8).generate()
                    val swatches = listOfNotNull(
                        palette.dominantSwatch, palette.vibrantSwatch,
                        palette.mutedSwatch, palette.darkVibrantSwatch
                    )
                    if (swatches.isNotEmpty()) {
                        paletteColors = swatches.map { Color(it.rgb) }
                    }
                }
            } catch (e: Exception) {}
        }
    }

    val c1 = paletteColors.getOrNull(0) ?: accentColor
    val c2 = paletteColors.getOrNull(1) ?: c1
    val c3 = paletteColors.getOrNull(2) ?: c2
    val c4 = paletteColors.getOrNull(3) ?: c1

    // ==========================================
    // 🔥 Lytrix: Swipe Physics
    // ==========================================
    val offsetXAnimatable = remember { Animatable(0f) }
    var totalDragX by remember { mutableFloatStateOf(0f) }
    var totalDragY by remember { mutableFloatStateOf(0f) }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (isLandscape) 0.dp else 12.dp,
                end = if (isLandscape) 0.dp else 12.dp,
                top = 4.dp,
                bottom = if (isLandscape) 6.dp else 40.dp
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = (if (isLandscape) Modifier.width(420.dp) else Modifier.fillMaxWidth())
                .height(if (isLandscape) 54.dp else 68.dp)
                .offset { IntOffset(offsetXAnimatable.value.roundToInt(), 0) }
                .clip(RoundedCornerShape(if (isLandscape) 18.dp else 20.dp))
                .clickable { onExpand() }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {
                            val swipeThreshold = 65f
                            if (totalDragY > swipeThreshold) {
                                // 🔥 মেল্ডের মতো নিচে টানলে গান বন্ধ ও প্লেয়ার ডিসমিস
                                onDismiss()
                            } else if (totalDragY < -swipeThreshold) {
                                // 🔥 মেল্ডের মতো উপরে টানলে স্মুথলি নাওপ্লেয়িং ফুল স্ক্রিন ওপেন
                                onExpand()
                            } else if (offsetXAnimatable.value > 120f) {
                                AdManager.runWithAd(context) { onSkipPrevious() }
                            } else if (offsetXAnimatable.value < -120f) {
                                AdManager.runWithAd(context) { onSkipNext() }
                            }

                            coroutineScope.launch {
                                offsetXAnimatable.animateTo(0f, spring(stiffness = Spring.StiffnessLow))
                            }
                            totalDragX = 0f
                            totalDragY = 0f
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                offsetXAnimatable.animateTo(0f, spring(stiffness = Spring.StiffnessLow))
                            }
                            totalDragX = 0f
                            totalDragY = 0f
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            totalDragX += dragAmount.x
                            totalDragY += dragAmount.y
                            // Strictly Y = 0 (No vertical card looseness); ONLY horizontal translation
                            coroutineScope.launch {
                                offsetXAnimatable.snapTo(offsetXAnimatable.value + dragAmount.x)
                            }
                        }
                    )
                }
                .drawWithCache {
                    val width = size.width
                    val height = size.height
                    val startGlow = Brush.radialGradient(
                        listOf(c1.copy(alpha = 0.82f), c1.copy(alpha = 0.38f), Color.Transparent),
                        center = Offset(width * 0.12f, height * 0.42f), radius = width * 0.72f
                    )
                    val endGlow = Brush.radialGradient(
                        listOf(c2.copy(alpha = 0.78f), c2.copy(alpha = 0.34f), Color.Transparent),
                        center = Offset(width * 0.88f, height * 0.58f), radius = width * 0.72f
                    )
                    val topGlow = Brush.radialGradient(
                        listOf(c3.copy(alpha = 0.58f), Color.Transparent),
                        center = Offset(width * 0.52f, height * 0.05f), radius = width * 0.54f
                    )
                    val bottomGlow = Brush.radialGradient(
                        listOf(c4.copy(alpha = 0.46f), Color.Transparent),
                        center = Offset(width * 0.46f, height * 1.05f), radius = width * 0.54f
                    )
                    onDrawBehind {
                        drawRect(Color.Black)
                        drawRect(startGlow)
                        drawRect(endGlow)
                        drawRect(topGlow)
                        drawRect(bottomGlow)
                        drawRect(Color.Black.copy(alpha = 0.24f))
                    }
                }
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ==========================================
                // 🔥 Artwork & Progress (SimpMusic Style - Clean & No Outer Rings)
                // ==========================================
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(42.dp)) {
                    val targetProgress = if (duration > 0) (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
                    val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = targetProgress,
                        animationSpec = androidx.compose.animation.core.tween(300),
                        label = "mini_progress"
                    )

                    LytrixWavyCircularProgress(
                        progress = animatedProgress,
                        isBuffering = isBuffering,
                        isPlaying = isPlaying,
                        color = Color.White,
                        trackColor = Color.Transparent,
                        strokeWidth = 2.6.dp,
                        modifier = Modifier.fillMaxSize()
                    )

                    AsyncImage(
                        model = ImageRequest.Builder(context).data(artData ?: song.uriString).crossfade(true).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // ==========================================
                // 🔥 Title & Artist
                // ==========================================
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    Text(
                        text = song.title, fontFamily = themeFont,
                        color = Color.White, fontSize = 15.sp,
                        fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.artist, fontFamily = themeFont,
                        color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }

                // ==========================================
                // 🔥 Transport Controls
                // ==========================================
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    // 🔥 Previous বাটনে ক্লিক করলে অ্যাড চেক করবে
                    IconButton(
                        onClick = { AdManager.runWithAd(context) { onSkipPrevious() } },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(Icons.Rounded.SkipPrevious, contentDescription = "Prev", tint = Color.White, modifier = Modifier.size(24.dp))
                    }

                    FilledIconButton(
                        onClick = onPlayPause,
                        modifier = Modifier.size(44.dp).bouncePress(),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.92f),
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = "Play/Pause", modifier = Modifier.size(24.dp))
                    }

                    // 🔥 Next বাটনে ক্লিক করলে অ্যাড চেক করবে
                    IconButton(
                        onClick = { AdManager.runWithAd(context) { onSkipNext() } },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}