@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.musicplayer.lytrix.ui.stats

import android.content.Context
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
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
import coil.compose.AsyncImage
import com.musicplayer.lytrix.stats.ListeningInsightsData
import com.musicplayer.lytrix.stats.ListeningInsightsRepository
import com.musicplayer.lytrix.stats.StatsPeriod
import com.musicplayer.lytrix.ui.component.LyricsImageGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import kotlin.math.ceil
import kotlin.math.roundToInt

enum class ShareStatsTab { RECEIPT, TASTE_CARD }

private val ReceiptPaperBase = Color(0xFFF6F3EB)
private val ReceiptInk = Color(0xFF17171A)
private val ReceiptSubtleInk = Color(0xFF52525B)

private val TasteCardSwatches = listOf(
    Color(0xFF1A1E3A),
    Color(0xFF5B21B6),
    Color(0xFF9F1239),
    Color(0xFF0F766E),
    Color(0xFF16161A),
)

/**
 * Captures any Compose [GraphicsLayer], writes it to Pictures/Lytrix Player via [LyricsImageGenerator],
 * and optionally triggers the Android share sheet.
 */
suspend fun shareOrSaveStatsBitmap(
    context: Context,
    graphicsLayer: GraphicsLayer,
    filePrefix: String,
    shareTitle: String,
    saveOnly: Boolean
) {
    try {
        val imageBitmap = graphicsLayer.toImageBitmap()
        val androidBitmap = imageBitmap.asAndroidBitmap()
        val fileName = "${filePrefix}_${System.currentTimeMillis()}"
        val uri = LyricsImageGenerator.saveBitmapAsFile(context, androidBitmap, fileName)
        if (saveOnly) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Saved to Pictures/Lytrix Player", Toast.LENGTH_SHORT).show()
            }
        } else {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            withContext(Dispatchers.Main) {
                context.startActivity(Intent.createChooser(intent, shareTitle))
            }
        }
    } catch (e: Exception) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * Unified dialog displaying SimpMusic-style real thermal paper Listening Receipt
 * and editorial mosaic Music Taste Poster with period filters & PNG export.
 */
@Composable
fun ShareReceiptAndTasteDialog(
    initialTab: ShareStatsTab = ShareStatsTab.RECEIPT,
    accentColor: Color,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val captureLayer = rememberGraphicsLayer()

    var activeTab by remember { mutableStateOf(initialTab) }
    var period by remember { mutableStateOf(StatsPeriod.LAST_30_DAYS) }
    var insights by remember { mutableStateOf<ListeningInsightsData?>(null) }
    var selectedSwatchIdx by remember { mutableIntStateOf(0) }
    var isExporting by remember { mutableStateOf(false) }

    LaunchedEffect(period) {
        insights = ListeningInsightsRepository.load(context, period)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val currentData = insights
        val bgSeed = if (activeTab == ShareStatsTab.RECEIPT) accentColor else TasteCardSwatches[selectedSwatchIdx]

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF08090C))
                .background(
                    Brush.verticalGradient(
                        listOf(bgSeed.copy(alpha = 0.28f), Color(0xFF0E1015), Color(0xFF08090C))
                    )
                )
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (activeTab == ShareStatsTab.RECEIPT) "Listening Receipt" else "Music Taste Poster",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatsTabPill(
                    label = "Receiptify",
                    selected = activeTab == ShareStatsTab.RECEIPT,
                    accentColor = accentColor,
                    modifier = Modifier.weight(1f),
                    onClick = { activeTab = ShareStatsTab.RECEIPT }
                )
                StatsTabPill(
                    label = "Taste Poster",
                    selected = activeTab == ShareStatsTab.TASTE_CARD,
                    accentColor = accentColor,
                    modifier = Modifier.weight(1f),
                    onClick = { activeTab = ShareStatsTab.TASTE_CARD }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatsPeriod.entries.forEach { p ->
                    val selected = period == p
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(50))
                            .background(if (selected) Color.White else Color.White.copy(alpha = 0.10f))
                            .clickable { period = p }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = p.label,
                            color = if (selected) Color.Black else Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (currentData == null) {
                    Box(modifier = Modifier.fillMaxWidth().height(360.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = accentColor)
                    }
                } else {
                    val captureModifier = Modifier.drawWithContent {
                        captureLayer.record {
                            this@drawWithContent.drawContent()
                        }
                        drawLayer(captureLayer)
                    }

                    if (activeTab == ShareStatsTab.RECEIPT) {
                        ListeningReceiptPaper(
                            data = currentData,
                            modifier = Modifier.shadow(16.dp).then(captureModifier)
                        )
                    } else {
                        ShareTasteCard(
                            data = currentData,
                            cardColor = TasteCardSwatches[selectedSwatchIdx],
                            modifier = Modifier.shadow(18.dp, RoundedCornerShape(24.dp)).then(captureModifier)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TasteCardSwatches.forEachIndexed { idx, swatch ->
                                val selected = idx == selectedSwatchIdx
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(swatch)
                                        .border(
                                            width = if (selected) 2.5.dp else 1.dp,
                                            color = if (selected) Color.White else Color.White.copy(alpha = 0.25f),
                                            shape = CircleShape
                                        )
                                        .clickable { selectedSwatchIdx = idx }
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        if (!isExporting && currentData != null) {
                            isExporting = true
                            scope.launch {
                                val prefix = if (activeTab == ShareStatsTab.RECEIPT) "lytrix_receipt" else "lytrix_taste"
                                shareOrSaveStatsBitmap(context, captureLayer, prefix, "Save Image", saveOnly = true)
                                isExporting = false
                            }
                        }
                    },
                    enabled = !isExporting && currentData != null,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.Rounded.Download, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save", color = Color.White, fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = {
                        if (!isExporting && currentData != null) {
                            isExporting = true
                            scope.launch {
                                val prefix = if (activeTab == ShareStatsTab.RECEIPT) "lytrix_receipt" else "lytrix_taste"
                                val title = if (activeTab == ShareStatsTab.RECEIPT) "Share Listening Receipt" else "Share Music Taste Poster"
                                shareOrSaveStatsBitmap(context, captureLayer, prefix, title, saveOnly = false)
                                isExporting = false
                            }
                        }
                    },
                    enabled = !isExporting && currentData != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    if (isExporting) {
                        CircularProgressIndicator(color = Color.Black, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    } else {
                        Icon(Icons.Rounded.Share, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsTabPill(
    label: String,
    selected: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) accentColor else Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        val textColor = if (selected && accentColor.luminance() > 0.55f) Color.Black else Color.White
        Text(label, color = textColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

/**
 * SimpMusic-ported scanned paper Listening Receipt card captured cleanly to shareable PNG.
 */
@Composable
fun ListeningReceiptPaper(
    data: ListeningInsightsData,
    modifier: Modifier = Modifier
) {
    val lines = data.topTracks.take(10)
    val totalPlays = lines.sumOf { it.playCount }.coerceAtLeast(data.totalPlaysCount)
    val totalSec = lines.sumOf { it.playCount.toLong() * it.avgDurationSec.toLong() }
        .coerceAtLeast(data.periodPlaytimeMs / 1000L)
    val seedStr = lines.joinToString("|") { "${it.title}:${it.playCount}" }.ifBlank { data.userName }
    val hash = fnv1aHash(seedStr)
    val authCode = (hash.toUInt() % 1_000_000u).toString().padStart(6, '0')
    val orderNum = (totalPlays.coerceAtLeast(1)).toString().padStart(4, '0')
    val year = remember { Calendar.getInstance().get(Calendar.YEAR) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 348.dp)
            .receiptPaperBackground()
            .padding(horizontal = 18.dp, vertical = 20.dp)
    ) {
        Text(
            text = "LYTRIXIFY",
            color = ReceiptInk,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Black,
            fontSize = 27.sp,
            letterSpacing = 2.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = data.period.label.uppercase(),
            color = ReceiptSubtleInk,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.5.sp,
            letterSpacing = 1.6.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(14.dp))

        ReceiptMonoText("ORDER #$orderNum FOR ${data.userName.uppercase()}")
        ReceiptMonoText(ListeningInsightsRepository.todayReceiptDate().uppercase())

        ReceiptDashedDivider()

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ReceiptMonoText("QTY", bold = true, modifier = Modifier.width(34.dp))
            ReceiptMonoText("ITEM", bold = true, modifier = Modifier.weight(1f))
            ReceiptMonoText("AMT", bold = true, modifier = Modifier.width(50.dp), alignEnd = true)
        }

        ReceiptDashedDivider()

        if (lines.isEmpty()) {
            Text(
                text = "NO TRACKS RECORDED IN PERIOD",
                color = ReceiptSubtleInk,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp)
            )
        } else {
            lines.forEach { track ->
                val qty = track.playCount.toString().padStart(2, '0')
                val itemText = "${track.title} - ${track.artist}".uppercase()
                val amt = ListeningInsightsRepository.formatReceiptClock(track.avgDurationSec)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.5.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    ReceiptMonoText(qty, modifier = Modifier.width(34.dp))
                    Text(
                        text = itemText,
                        color = ReceiptInk,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.5.sp,
                        lineHeight = 14.5.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 6.dp)
                    )
                    ReceiptMonoText(amt, modifier = Modifier.width(50.dp), alignEnd = true)
                }
            }
        }

        ReceiptDashedDivider()

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ReceiptMonoText("ITEM COUNT:")
            ReceiptMonoText(totalPlays.toString(), bold = true)
        }
        Spacer(modifier = Modifier.height(2.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ReceiptMonoText("TOTAL:", bold = true)
            ReceiptMonoText(ListeningInsightsRepository.formatReceiptTotal(totalSec), bold = true)
        }

        ReceiptDashedDivider()

        ReceiptMonoText("CARD #: **** **** **** $year")
        ReceiptMonoText("AUTH CODE: $authCode")
        ReceiptMonoText("CARDHOLDER: ${data.userName.uppercase()}")
        ReceiptMonoText("ARCHETYPE: ${data.archetype.title.uppercase()}")

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "THANK YOU FOR LISTENING!",
            color = ReceiptInk,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 11.5.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        Canvas(modifier = Modifier.fillMaxWidth().height(34.dp).padding(horizontal = 24.dp)) {
            val totalBars = 68
            var s = hash
            fun nextBit(): Float {
                s = s xor (s shl 13)
                s = s xor (s ushr 17)
                s = s xor (s shl 5)
                return ((s.toUInt() % 1000u).toFloat() / 1000f)
            }
            val slotW = size.width / totalBars
            for (i in 0 until totalBars) {
                val r = nextBit()
                if (r > 0.36f) {
                    val barW = slotW * (0.45f + r * 0.45f)
                    drawRect(
                        color = ReceiptInk,
                        topLeft = Offset(i * slotW, 0f),
                        size = Size(barW, size.height)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "LYTRIX PLAYER",
            color = ReceiptSubtleInk,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun Modifier.receiptPaperBackground(): Modifier =
    drawBehind {
        val toothW = 7.dp.toPx()
        val toothH = 5.dp.toPx()
        val teethCount = (size.width / toothW).toInt().coerceAtLeast(10)
        val step = size.width / teethCount

        val path = Path().apply {
            moveTo(0f, toothH)
            for (i in 0 until teethCount) {
                lineTo(i * step + step * 0.5f, 0f)
                lineTo((i + 1) * step, toothH)
            }
            lineTo(size.width, size.height - toothH)
            for (i in teethCount downTo 1) {
                lineTo(i * step - step * 0.5f, size.height)
                lineTo((i - 1) * step, size.height - toothH)
            }
            close()
        }
        drawPath(path, color = ReceiptPaperBase)
        drawPath(
            path,
            brush = Brush.verticalGradient(
                listOf(
                    Color(0x0A000000),
                    Color.Transparent,
                    Color(0x06FFFFFF),
                    Color(0x0C000000)
                )
            )
        )
    }

@Composable
private fun ReceiptMonoText(
    text: String,
    bold: Boolean = false,
    modifier: Modifier = Modifier,
    alignEnd: Boolean = false
) {
    Text(
        text = text,
        color = ReceiptInk,
        fontFamily = FontFamily.Monospace,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        fontSize = 11.5.sp,
        lineHeight = 15.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
        modifier = modifier
    )
}

@Composable
private fun ReceiptDashedDivider() {
    Canvas(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).height(1.5.dp)) {
        drawLine(
            color = ReceiptInk.copy(alpha = 0.65f),
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = size.height,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
        )
    }
}

/**
 * Editorial SimpMusic-poster style Music Taste Card with tiled artwork mosaic backdrop,
 * top track hero showcase, miniature ranked artist/track rows, and stat footer.
 */
@Composable
fun ShareTasteCard(
    data: ListeningInsightsData,
    cardColor: Color,
    modifier: Modifier = Modifier
) {
    val mosaicUrls = remember(data) {
        (data.topTracks.mapNotNull { it.artworkUrl } + data.topArtists.mapNotNull { it.artworkUrl })
            .filter { it.isNotBlank() }
            .distinct()
    }
    val groundDark = lerp(cardColor, Color.Black, 0.68f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 360.dp)
            .clip(RoundedCornerShape(24.dp))
            .clipToBounds()
            .background(groundDark)
    ) {
        ArtworkMosaicBackdrop(
            mosaicUrls = mosaicUrls,
            modifier = Modifier.matchParentSize()
        )

        // Rich multi-stop editorial veil blending mosaic into card tint
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0.00f to cardColor.copy(alpha = 0.70f),
                        0.42f to lerp(cardColor, Color(0xFF0E1016), 0.55f).copy(alpha = 0.90f),
                        1.00f to Color(0xFF0A0B0F).copy(alpha = 0.96f)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SOUND TASTE · ${data.period.label.uppercase()}",
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.6.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = data.userName,
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }

            // Hero #1 Track Spotlight
            val heroTrack = data.topTracks.firstOrNull()
            if (heroTrack != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.11f))
                        .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!heroTrack.artworkUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = heroTrack.artworkUrl,
                                contentDescription = heroTrack.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(Icons.Rounded.MusicNote, null, tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "#1 MOST REPLAYED",
                            color = Color(0xFFFFD54F),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.1.sp
                        )
                        Text(
                            text = heroTrack.title,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${heroTrack.artist} · ${heroTrack.playCount} plays",
                            color = Color.White.copy(alpha = 0.72f),
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Ranked Two-Column Poster Body
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "TOP ARTISTS",
                        color = Color.White.copy(alpha = 0.62f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                    val artists = data.topArtists.take(4)
                    if (artists.isEmpty()) {
                        Text("—", color = Color.White, fontSize = 13.sp)
                    } else {
                        artists.forEachIndexed { idx, a ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${idx + 1}",
                                    color = if (idx == 0) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.55f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.width(16.dp)
                                )
                                Text(
                                    text = a.artist,
                                    color = Color.White,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "TOP SONGS",
                        color = Color.White.copy(alpha = 0.62f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                    val tracks = data.topTracks.take(4)
                    if (tracks.isEmpty()) {
                        Text("—", color = Color.White, fontSize = 13.sp)
                    } else {
                        tracks.forEachIndexed { idx, t ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${idx + 1}",
                                    color = if (idx == 0) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.55f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.width(16.dp)
                                )
                                Text(
                                    text = t.title,
                                    color = Color.White,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // Hairline rule divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.16f))
            )

            // Stats Strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TasteStatCol("PLAYTIME", ListeningInsightsRepository.formatDurationReadable(data.periodPlaytimeMs))
                TasteStatCol("PLAYS", data.totalPlaysCount.toString())
                TasteStatCol("STREAK", "${data.currentStreakDays}d")
                TasteStatCol("PEAK", ListeningInsightsRepository.formatHourShort(data.peakHour))
            }

            // Archetype + Wordmark Footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.14f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = data.archetype.title,
                        color = Color.White,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "LYTRIX PLAYER",
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.6.sp
                )
            }
        }
    }
}

@Composable
private fun TasteStatCol(label: String, value: String) {
    Column {
        Text(label, color = Color.White.copy(alpha = 0.58f), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        Spacer(modifier = Modifier.height(2.dp))
        Text(value, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
    }
}

private fun fnv1aHash(text: String): Int {
    var h = 0x811C9DC5.toInt()
    for (c in text) {
        h = (h xor c.code) * 0x01000193
    }
    return h
}
