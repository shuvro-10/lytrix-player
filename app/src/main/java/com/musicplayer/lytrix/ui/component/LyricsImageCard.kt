package com.musicplayer.lytrix.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.musicplayer.lytrix.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun rememberAdjustedLyricsFontSize(
    text: String,
    maxWidth: Dp,
    maxHeight: Dp,
    density: Density,
    initialFontSize: TextUnit = 22.sp,
    minFontSize: TextUnit = 11.sp,
    style: TextStyle = TextStyle.Default,
): TextUnit {
    val measurer = rememberTextMeasurer()

    var calculatedFontSize by remember(text, maxWidth, maxHeight, style, density) {
        val initialSize = when {
            text.length < 50 -> initialFontSize
            text.length < 100 -> (initialFontSize.value * 0.85f).sp
            text.length < 200 -> (initialFontSize.value * 0.70f).sp
            else -> (initialFontSize.value * 0.55f).sp
        }
        mutableStateOf(initialSize)
    }

    LaunchedEffect(text, maxWidth, maxHeight) {
        val targetWidthPx = with(density) { maxWidth.toPx() * 0.94f }
        val targetHeightPx = with(density) { maxHeight.toPx() * 0.94f }
        if (text.isBlank() || targetWidthPx <= 0f || targetHeightPx <= 0f) {
            calculatedFontSize = minFontSize
            return@LaunchedEffect
        }

        var minSize = minFontSize.value
        var maxSize = initialFontSize.value
        var bestFit = minSize
        var iterations = 0

        while (minSize <= maxSize && iterations < 16) {
            iterations++
            val midSize = (minSize + maxSize) / 2
            val midSizeSp = midSize.sp

            val result = measurer.measure(
                text = AnnotatedString(text),
                style = style.copy(fontSize = midSizeSp, lineHeight = (midSize * 1.25f).sp)
            )

            if (result.size.width <= targetWidthPx && result.size.height <= targetHeightPx) {
                bestFit = midSize
                minSize = midSize + 0.5f
            } else {
                maxSize = midSize - 0.5f
            }
        }

        calculatedFontSize = if (bestFit < minFontSize.value) minFontSize else bestFit.sp
    }

    return calculatedFontSize
}

@Composable
fun LyricsImageCard(
    sharedLines: List<SharedLyricLine> = emptyList(),
    lyricText: String = "",
    songTitle: String,
    artistName: String,
    coverArtUrl: String?,
    aspectRatio: LyricsShareAspectRatio = LyricsShareAspectRatio.Square,
    backgroundColor: Color = Color(0xFF18181B),
    backgroundStyle: LyricsBackgroundStyle = LyricsBackgroundStyle.SOLID,
    textColor: Color = Color.White,
    secondaryTextColor: Color = Color.White.copy(alpha = 0.7f),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val lines = remember(sharedLines, lyricText) {
        if (sharedLines.isNotEmpty()) sharedLines
        else lyricText.lines().filter { it.isNotBlank() }.map { SharedLyricLine(it, null) }
    }
    val combinedForMeasure = remember(lines) {
        lines.joinToString("\n") { it.original + if (!it.translation.isNullOrBlank()) "\n${it.translation}" else "" }
    }

    var gradientBrush by remember(coverArtUrl, backgroundStyle, backgroundColor) {
        mutableStateOf<Brush?>(null)
    }

    if (backgroundStyle == LyricsBackgroundStyle.GRADIENT) {
        LaunchedEffect(coverArtUrl, backgroundColor) {
            if (!coverArtUrl.isNullOrBlank()) {
                withContext(Dispatchers.IO) {
                    try {
                        val loader = ImageLoader(context)
                        val req = ImageRequest.Builder(context)
                            .data(coverArtUrl)
                            .allowHardware(false)
                            .build()
                        val result = loader.execute(req)
                        if (result is SuccessResult) {
                            val bmp = result.drawable.toBitmap()
                            val palette = Palette.from(bmp).generate()
                            val vibrant = Color(palette.getVibrantColor(backgroundColor.toArgb()))
                            val darkVibrant = Color(palette.getDarkVibrantColor(0xFF0F0F12.toInt()))
                            gradientBrush = Brush.linearGradient(listOf(vibrant, darkVibrant))
                        }
                    } catch (_: Exception) {
                    }
                }
            } else {
                gradientBrush = Brush.linearGradient(listOf(backgroundColor, Color.Black))
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, textColor.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
    ) {
        // Background layer
        when (backgroundStyle) {
            LyricsBackgroundStyle.SOLID -> {
                Box(modifier = Modifier.fillMaxSize().background(backgroundColor))
            }
            LyricsBackgroundStyle.BLUR -> {
                Box(modifier = Modifier.fillMaxSize().background(Color(0xFF101014)))
                if (!coverArtUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = coverArtUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(40.dp)
                    )
                }
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.38f)))
            }
            LyricsBackgroundStyle.GRADIENT -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(gradientBrush ?: Brush.linearGradient(listOf(backgroundColor, Color.Black)))
                )
            }
        }

        // Card Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!coverArtUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = coverArtUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(0.8.dp, textColor.copy(alpha = 0.20f), RoundedCornerShape(8.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(textColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🎵", fontSize = 18.sp)
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = songTitle,
                        color = textColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = artistName,
                        color = secondaryTextColor,
                        fontSize = 12.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Lyrics Middle Section
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                val availableWidth = maxWidth
                val availableHeight = maxHeight

                val textStyle = TextStyle(
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )

                val dynamicFontSize = rememberAdjustedLyricsFontSize(
                    text = combinedForMeasure,
                    maxWidth = availableWidth - 12.dp,
                    maxHeight = availableHeight - 12.dp,
                    density = density,
                    initialFontSize = when (aspectRatio) {
                        LyricsShareAspectRatio.Story -> 24.sp
                        LyricsShareAspectRatio.Portrait -> 22.sp
                        LyricsShareAspectRatio.Square -> 20.sp
                    },
                    minFontSize = 11.sp,
                    style = textStyle
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    lines.forEach { line ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = line.original,
                                style = textStyle.copy(
                                    fontSize = dynamicFontSize,
                                    lineHeight = (dynamicFontSize.value * 1.25f).sp
                                ),
                                textAlign = TextAlign.Center,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (!line.translation.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = line.translation,
                                    style = textStyle.copy(
                                        fontSize = (dynamicFontSize.value * 0.72f).sp,
                                        fontWeight = FontWeight.Normal,
                                        color = textColor.copy(alpha = 0.78f),
                                        lineHeight = (dynamicFontSize.value * 0.72f * 1.25f).sp
                                    ),
                                    textAlign = TextAlign.Center,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }

            // Footer Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(secondaryTextColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.app_icon),
                        contentDescription = null,
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "Lytrix Player",
                    color = secondaryTextColor,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
