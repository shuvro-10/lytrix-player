package com.musicplayer.lytrix.ui.component

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.musicplayer.lytrix.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsShareOptionDialog(
    sharedLines: List<SharedLyricLine> = emptyList(),
    lyricsText: String = "",
    songTitle: String,
    artistName: String,
    songId: String? = null,
    onDismiss: () -> Unit,
    onShareAsImage: () -> Unit
) {
    val context = LocalContext.current

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF141416),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            tonalElevation = 0.dp,
            shadowElevation = 14.dp,
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(0.90f)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = stringResource(R.string.share_lyrics),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(18.dp))

                // Option 1: Share as Text
                Surface(
                    onClick = {
                        val shareBody = buildString {
                            append("\"")
                            if (sharedLines.isNotEmpty()) {
                                sharedLines.forEachIndexed { i, line ->
                                    append(line.original)
                                    if (!line.translation.isNullOrBlank()) {
                                        append("\n")
                                        append(line.translation)
                                    }
                                    if (i < sharedLines.size - 1) append("\n\n")
                                }
                            } else {
                                append(lyricsText.trim())
                            }
                            append("\"\n\n")
                            append(songTitle)
                            append(" - ")
                            append(artistName)
                        }
                        val intent = Intent().apply {
                            action = Intent.ACTION_SEND
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareBody)
                        }
                        context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_lyrics)))
                        onDismiss()
                    },
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF202024),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.TextFields,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.share_as_text),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = "Quotes, artist & song link",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.65f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Option 2: Share as Image
                Surface(
                    onClick = {
                        onShareAsImage()
                    },
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF202024),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Image,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.share_as_image),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = "1:1, 9:16 Story card with colors",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.65f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = stringResource(R.string.cancel),
                            color = Color.White.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LyricsImageShareStudioDialog(
    sharedLines: List<SharedLyricLine> = emptyList(),
    lyricsText: String = "",
    songTitle: String,
    artistName: String,
    coverArtUrl: String?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedAspectRatio by remember { mutableStateOf(LyricsShareAspectRatio.Square) }
    var selectedBackgroundStyle by remember { mutableStateOf(LyricsBackgroundStyle.BLUR) }
    var selectedBgColor by remember { mutableStateOf(Color(0xFF000000)) }
    var selectedTextColor by remember { mutableStateOf(Color.White) }
    var selectedSecondaryTextColor by remember { mutableStateOf(Color.White.copy(alpha = 0.7f)) }
    var isGenerating by remember { mutableStateOf(false) }

    val extractedColors = remember { mutableStateListOf<Color>() }

    // Palette extraction from album art (Meld style: extracts vivid/saturated swatches from album cover)
    LaunchedEffect(coverArtUrl) {
        if (!coverArtUrl.isNullOrBlank()) {
            withContext(Dispatchers.IO) {
                try {
                    val loader = ImageLoader(context)
                    val req = ImageRequest.Builder(context)
                        .data(coverArtUrl)
                        .allowHardware(false)
                        .build()
                    val res = loader.execute(req)
                    if (res is SuccessResult) {
                        val bmp = res.drawable.toBitmap()
                        val palette = Palette.from(bmp).generate()
                        val dominantRgb = palette.dominantSwatch?.rgb
                            ?: palette.vibrantSwatch?.rgb
                            ?: palette.swatches.maxByOrNull { it.population }?.rgb
                        extractedColors.clear()
                        if (dominantRgb != null) {
                            val albumColor = Color(dominantRgb)
                            extractedColors.add(albumColor)
                            selectedBgColor = albumColor
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    Dialog(
        onDismissRequest = { if (!isGenerating) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF141416),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            tonalElevation = 0.dp,
            shadowElevation = 16.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.92f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                // Top Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.share_lyrics),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    IconButton(
                        onClick = onDismiss,
                        enabled = !isGenerating,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Live Preview Card Box with dynamic aspect ratio
                    val previewMaxHeight = when (selectedAspectRatio) {
                        LyricsShareAspectRatio.Story -> 320.dp
                        LyricsShareAspectRatio.Portrait -> 280.dp
                        LyricsShareAspectRatio.Square -> 250.dp
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(previewMaxHeight)
                            .animateContentSize(animationSpec = tween(350)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .aspectRatio(selectedAspectRatio.previewAspectRatio)
                                .clip(RoundedCornerShape(20.dp))
                        ) {
                            LyricsImageCard(
                                sharedLines = sharedLines,
                                lyricText = lyricsText,
                                songTitle = songTitle,
                                artistName = artistName,
                                coverArtUrl = coverArtUrl,
                                aspectRatio = selectedAspectRatio,
                                backgroundColor = selectedBgColor,
                                backgroundStyle = selectedBackgroundStyle,
                                textColor = selectedTextColor,
                                secondaryTextColor = selectedSecondaryTextColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 1. Aspect Ratio (Size) Selector
                    Text(
                        text = stringResource(R.string.aspect_ratio),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LyricsShareAspectRatio.entries.forEach { ratio ->
                            val isSelected = selectedAspectRatio == ratio
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedAspectRatio = ratio },
                                label = {
                                    Text(
                                        text = stringResource(ratio.labelRes),
                                        fontSize = 12.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = Color(0xFF202024),
                                    labelColor = Color.White.copy(alpha = 0.75f),
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = Color.White.copy(alpha = 0.14f),
                                    selectedBorderColor = MaterialTheme.colorScheme.primary
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 2. Background Style Selector
                    Text(
                        text = stringResource(R.string.player_background_style),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LyricsBackgroundStyle.entries.forEach { style ->
                            val isSelected = selectedBackgroundStyle == style
                            val styleLabel = when (style) {
                                LyricsBackgroundStyle.SOLID -> stringResource(R.string.player_background_solid)
                                LyricsBackgroundStyle.BLUR -> stringResource(R.string.player_background_blur)
                                LyricsBackgroundStyle.GRADIENT -> stringResource(R.string.gradient)
                            }
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedBackgroundStyle = style },
                                label = {
                                    Text(
                                        text = styleLabel,
                                        fontSize = 12.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = Color(0xFF202024),
                                    labelColor = Color.White.copy(alpha = 0.75f),
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = Color.White.copy(alpha = 0.14f),
                                    selectedBorderColor = MaterialTheme.colorScheme.primary
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 1 album color + 7 diverse, distinct, recognizable defaults (Total 8 unique swatches)
                    val defaultBackgroundSwatches = listOf(
                        Color(0xFF000000), // Pure Black
                        Color(0xFFFFFFFF), // Pure White
                        Color(0xFF2563EB), // Royal Blue
                        Color(0xFFDC2626), // Crimson Red
                        Color(0xFF059669), // Emerald Green
                        Color(0xFF7C3AED), // Royal Purple
                        Color(0xFFF59E0B)  // Golden Amber / Yellow
                    )
                    val allBgSwatches = (extractedColors + defaultBackgroundSwatches).distinct().take(8)

                    // 3. Background Color Picker
                    Text(
                        text = stringResource(R.string.background_color),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        allBgSwatches.forEach { color ->
                            ColorSwatchCircle(
                                color = color,
                                isSelected = selectedBgColor == color,
                                onClick = { selectedBgColor = color }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 4. Lyrics Text Color Picker (Only modifies selectedTextColor)
                    // Replaced redundant off-white with vibrant coral red (0xFFFF6B6B) and sky blue (0xFF38BDF8)
                    val textSwatches = (listOf(
                        Color.White,
                        Color(0xFFFF6B6B),
                        Color.Black,
                        Color(0xFF1DB954),
                        Color(0xFF4FD1C5),
                        Color(0xFFFFD54F),
                        Color(0xFF38BDF8)
                    ) + extractedColors).distinct().take(8)

                    Text(
                        text = stringResource(R.string.text_color),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        textSwatches.forEach { color ->
                            ColorSwatchCircle(
                                color = color,
                                isSelected = selectedTextColor == color,
                                onClick = {
                                    selectedTextColor = color
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 5. Secondary Text Color Picker (Only modifies selectedSecondaryTextColor)
                    val secondaryTextSwatches = (extractedColors.map { it.copy(alpha = 0.75f) } + listOf(
                        Color.White.copy(alpha = 0.70f),
                        Color(0xFFFF6B6B).copy(alpha = 0.75f),
                        Color.Black.copy(alpha = 0.70f),
                        Color(0xFF1DB954),
                        Color(0xFF4FD1C5),
                        Color(0xFFFFD54F),
                        Color(0xFF38BDF8)
                    )).distinct().take(8)

                    Text(
                        text = stringResource(R.string.secondary_text_color),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        secondaryTextSwatches.forEach { color ->
                            ColorSwatchCircle(
                                color = color,
                                isSelected = selectedSecondaryTextColor == color,
                                onClick = {
                                    selectedSecondaryTextColor = color
                                }
                            )
                        }
                    }

                    // Spacer at the bottom of the scroll view to ensure nothing is covered by the Share button
                    Spacer(modifier = Modifier.height(28.dp))
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Share Action Button
                Button(
                    onClick = {
                        isGenerating = true
                        scope.launch {
                            try {
                                val bitmap = LyricsImageGenerator.createLyricsImage(
                                    context = context,
                                    coverArtUrl = coverArtUrl,
                                    songTitle = songTitle,
                                    artistName = artistName,
                                    sharedLines = sharedLines,
                                    lyrics = lyricsText,
                                    aspectRatio = selectedAspectRatio,
                                    backgroundColor = selectedBgColor.toArgb(),
                                    backgroundStyle = selectedBackgroundStyle,
                                    textColor = selectedTextColor.toArgb(),
                                    secondaryTextColor = selectedSecondaryTextColor.toArgb()
                                )
                                val fileName = "lytrix_lyrics_${System.currentTimeMillis()}"
                                val uri = LyricsImageGenerator.saveBitmapAsFile(context, bitmap, fileName)

                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "image/png"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(
                                    Intent.createChooser(
                                        shareIntent,
                                        context.getString(R.string.share_lyrics)
                                    )
                                )
                                onDismiss()
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    "Failed to generate image: ${e.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } finally {
                                isGenerating = false
                            }
                        }
                    },
                    enabled = !isGenerating,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.generating_image),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.share),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorSwatchCircle(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = onClick)
            .border(
                width = if (isSelected) 2.5.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.2f),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (color == Color.White) Color.Black else Color.White)
            )
        }
    }
}
