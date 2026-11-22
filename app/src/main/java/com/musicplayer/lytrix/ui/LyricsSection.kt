package com.musicplayer.lytrix.ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.musicplayer.lytrix.model.Song
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.mocharealm.accompanist.lyrics.core.parser.AutoParser
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.KaraokeLyricsView
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.LyricsAnchor
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.LyricsFade
import com.mocharealm.accompanist.lyrics.ui.composable.list.LyricsLazyListState
import com.mocharealm.accompanist.lyrics.ui.composable.list.rememberLyricsLazyListState
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeSyllable
import com.mocharealm.accompanist.lyrics.core.model.karaoke.copy
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import com.musicplayer.lytrix.lyrics.ai.LyricsTranslationHelper
import com.musicplayer.lytrix.lyrics.ai.TranslationStatus
import com.musicplayer.lytrix.ui.component.LyricsImageShareStudioDialog
import com.musicplayer.lytrix.ui.component.LyricsShareOptionDialog
import com.musicplayer.lytrix.ui.component.SharedLyricLine

// ==========================================
// 🔥 HTML/XML numeric entity decoder
// The accompanist-lyrics-ui library's own decodeXmlEntities() only handles
// the 5 named XML entities (&amp; &lt; &gt; &apos; &quot;) — it does NOT
// handle numeric character references like &#x27; or &#39;, which is
// exactly what BetterLyrics/YouLyPlus send for apostrophes. Since we can't
// edit the library, we decode these ourselves before handing raw data to it.
// ==========================================
fun decodeHtmlEntities(text: String): String {
    if (!text.contains('&')) return text
    return text
        .replace(Regex("""&#x([0-9a-fA-F]+);""")) { m ->
            m.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: m.value
        }
        .replace(Regex("""&#(\d+);""")) { m ->
            m.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: m.value
        }
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
}

// ==========================================
// 🔥 LRC / TTML display-text fallback parser
// Used only for: the mini preview bar, and the last-resort plain-text
// view when AutoParser genuinely can't find any timestamps at all.
// ==========================================
data class LytrixLyricLine(val startTimeMs: Long, val text: String)

private fun parseTtmlTimeMs(raw: String): Long? {
    val v = raw.trim()
    if (v.isEmpty()) return null
    Regex("""^(\d+(?:\.\d+)?)ms$""", RegexOption.IGNORE_CASE).matchEntire(v)?.let { m ->
        return m.groupValues[1].toDoubleOrNull()?.toLong()
    }
    Regex("""^(\d+(?:\.\d+)?)s$""", RegexOption.IGNORE_CASE).matchEntire(v)?.let { m ->
        return m.groupValues[1].toDoubleOrNull()?.times(1000.0)?.toLong()
    }
    Regex("""^(\d+(?:\.\d+)?)m$""", RegexOption.IGNORE_CASE).matchEntire(v)?.let { m ->
        return m.groupValues[1].toDoubleOrNull()?.times(60000.0)?.toLong()
    }

    val parts = v.replace(';', ':').split(':')
    return when (parts.size) {
        1 -> parts[0].toDoubleOrNull()?.let { (it * 1000.0).toLong() }
        2 -> {
            val m = parts[0].toDoubleOrNull() ?: return null
            val s = parts[1].toDoubleOrNull() ?: return null
            ((m * 60.0 + s) * 1000.0).toLong()
        }
        3 -> {
            val h = parts[0].toDoubleOrNull() ?: return null
            val m = parts[1].toDoubleOrNull() ?: return null
            val s = parts[2].toDoubleOrNull() ?: return null
            ((h * 3600.0 + m * 60.0 + s) * 1000.0).toLong()
        }
        else -> null
    }
}

fun parseLytrixLrcStrict(lrcContent: String): List<LytrixLyricLine> {
    val content = lrcContent.trim()
    val lines = mutableListOf<LytrixLyricLine>()

    if (content.startsWith("<?xml") || content.contains("<tt")) {
        val pTagRegex = Regex("""<p[^>]*begin="([^"]+)"[^>]*>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL)

        pTagRegex.findAll(content).forEach { match ->
            val timeStr = match.groupValues[1]
            val rawText = match.groupValues[2].replace(Regex("""<[^>]*>"""), "").trim()
            val timeMs = parseTtmlTimeMs(timeStr) ?: 0L
            val cleanText = decodeHtmlEntities(rawText)
            if (cleanText.isNotEmpty()) {
                lines.add(LytrixLyricLine(timeMs, cleanText))
            }
        }
    } else {
        val cleanContent = content
            .replace("\\n", "\n")
            .replace("\\r", "")
            .replace("\\", "")
            .replace(Regex("""<\d{2,}:\d{2}\.\d{1,3}>"""), "")

        val timeRegex = Regex("""\[(\d{2,}):(\d{2})(?:\.(\d{1,3}))?\]""")

        cleanContent.lines().forEach { line ->
            if (line.startsWith("[ar:") || line.startsWith("[ti:") || line.startsWith("[al:") || line.startsWith("[length:") || line.startsWith("[by:")) return@forEach

            val text = line.replace(timeRegex, "").trim()
            val matches = timeRegex.findAll(line)

            if (matches.any()) {
                for (match in matches) {
                    val min = match.groupValues[1].toLongOrNull() ?: 0L
                    val sec = match.groupValues[2].toLongOrNull() ?: 0L
                    val milStr = match.groupValues[3]
                    val mil = if (milStr.isNotEmpty()) {
                        when (milStr.length) {
                            1 -> milStr.toLong() * 100
                            2 -> milStr.toLong() * 10
                            else -> milStr.toLong()
                        }
                    } else 0L

                    val timeMs = (min * 60 * 1000) + (sec * 1000) + mil
                    lines.add(LytrixLyricLine(timeMs, decodeHtmlEntities(text)))
                }
            }
        }

        if (lines.isEmpty() && cleanContent.isNotBlank()) {
            return cleanContent.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { LytrixLyricLine(-1L, decodeHtmlEntities(it)) }
        }
    }

    return lines.sortedBy { it.startTimeMs }.distinctBy { it.startTimeMs }
}

private data class KaraokeStripLine(
    val startMs: Long,
    val endMs: Long,
    val syllables: List<KaraokeSyllable>,
    val fullText: String
)

private sealed class ParsedNowPlayingLyrics {
    data object Empty : ParsedNowPlayingLyrics()
    data class Karaoke(val lines: List<KaraokeStripLine>) : ParsedNowPlayingLyrics()
    data class Standard(val lines: List<LytrixLyricLine>, val isSynced: Boolean) : ParsedNowPlayingLyrics()
}

@Composable
private fun KaraokeBreathingDots(
    progress: Float,
    contentAlignment: Alignment,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "karaokeDotsBreath")
    val breath by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotBreathScale"
    )

    Box(
        modifier = modifier,
        contentAlignment = contentAlignment
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(3) { i ->
                val thresholdStart = i * 0.28f
                val dotFill = ((progress - thresholdStart) / 0.40f).coerceIn(0f, 1f)
                val dotAlpha = 0.25f + (0.72f * dotFill)
                val dotScale = (0.82f + (0.28f * dotFill)) * (0.94f + 0.08f * breath)
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .graphicsLayer {
                            scaleX = dotScale
                            scaleY = dotScale
                        }
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = dotAlpha))
                )
            }
        }
    }
}

private fun lyricsOffsetKey(song: Song): String = "${song.title}_${song.artist}_offset_ms"

private fun saveLyricsOffsetMs(context: Context, song: Song, offsetMs: Long) {
    val clamped = offsetMs.coerceIn(-10_000L, 10_000L)
    val prefs = context.getSharedPreferences("lyrics_prefs", Context.MODE_PRIVATE)
    val key = lyricsOffsetKey(song)
    prefs.edit().apply {
        if (clamped == 0L) remove(key) else putLong(key, clamped)
    }.apply()
}

@Composable
private fun rememberLyricsOffsetMs(song: Song): Long {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("lyrics_prefs", Context.MODE_PRIVATE) }
    val key = remember(song.title, song.artist) { lyricsOffsetKey(song) }
    var offsetMs by remember(key) { mutableLongStateOf(prefs.getLong(key, 0L)) }

    DisposableEffect(prefs, key) {
        offsetMs = prefs.getLong(key, 0L)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (changedKey == key || changedKey == null) {
                offsetMs = prefs.getLong(key, 0L)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    return offsetMs
}

/**
 * Shared live NowPlaying lyric strip for BOTH AppleMusicNowPlaying & MeldNowPlaying:
 * - Karaoke providers (YouLyPlus / BetterLyrics with syllables): 60fps word-by-word sweep
 *   with 3 animated karaoke breathing dots during intro & long instrumental gaps (matching Lyrics page).
 * - Standard sentence-by-sentence providers (SimpMusic, LRCLIB, KuGou): classic line display.
 * - Missing/no lyrics: clean blank space (no static dots).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NowPlayingLiveLyricStrip(
    song: Song,
    currentLyrics: String?,
    currentPosition: Long,
    isPlaying: Boolean,
    fontSize: TextUnit,
    textAlign: TextAlign,
    contentAlignment: Alignment,
    loadingPlaceholderText: String = "",
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lyricsPrefs = remember { context.getSharedPreferences("lyrics_prefs", Context.MODE_PRIVATE) }
    val lyricsKey = remember(song.title, song.artist) { "${song.title}_${song.artist}_lyrics" }
    val lyricsOffsetMs = rememberLyricsOffsetMs(song)
    val adjustedPosition = (currentPosition - lyricsOffsetMs).coerceAtLeast(0L)
    val rawLyrics = remember(song.id, lyricsKey, currentLyrics) {
        if (currentLyrics == "LOADING_LYRICS_STATE") {
            "LOADING_LYRICS_STATE"
        } else {
            lyricsPrefs.getString(lyricsKey, currentLyrics) ?: currentLyrics
        }
    }

    val parsedModel = remember(rawLyrics) {
        if (rawLyrics.isNullOrBlank() || rawLyrics == "LOADING_LYRICS_STATE") {
            ParsedNowPlayingLyrics.Empty
        } else {
            val decoded = decodeHtmlEntities(rawLyrics)
            val parsed = try {
                autoParser.parse(decoded)
            } catch (_: Throwable) {
                null
            }
            val karaokeLines = parsed?.lines
                ?.filterIsInstance<KaraokeLine>()
                ?.filter { it.syllables.isNotEmpty() }
                ?.mapNotNull { line ->
                    val text = line.syllables.joinToString("") { it.content }.trim()
                    if (text.isBlank()) return@mapNotNull null
                    val sylMaxEnd = line.syllables.maxOfOrNull { it.end.toLong() } ?: 0L
                    val safeEnd = maxOf(line.start.toLong() + 300L, line.end.toLong(), sylMaxEnd)
                    KaraokeStripLine(
                        startMs = line.start.toLong(),
                        endMs = safeEnd,
                        syllables = line.syllables,
                        fullText = text
                    )
                }
                .orEmpty()

            if (karaokeLines.isNotEmpty()) {
                ParsedNowPlayingLyrics.Karaoke(karaokeLines.sortedBy { it.startMs })
            } else {
                val synced = parsed?.lines?.mapNotNull { line ->
                    val text = when (line) {
                        is KaraokeLine -> line.syllables.joinToString("") { it.content }
                        is SyncedLine -> line.content
                        else -> ""
                    }.trim()
                    if (text.isNotBlank()) LytrixLyricLine(line.start.toLong(), text) else null
                }?.takeIf { it.isNotEmpty() } ?: parseLytrixLrcStrict(rawLyrics)
                val hasSynced = synced.any { it.startTimeMs >= 0L }
                ParsedNowPlayingLyrics.Standard(synced, hasSynced)
            }
        }
    }

    val isKaraoke = parsedModel is ParsedNowPlayingLyrics.Karaoke
    var smoothPositionMs by remember(song.id) { mutableLongStateOf(adjustedPosition) }
    LaunchedEffect(adjustedPosition, isPlaying, isKaraoke, song.id) {
        if (!isKaraoke) {
            smoothPositionMs = adjustedPosition
            return@LaunchedEffect
        }
        val anchorPos = adjustedPosition
        val anchorNanos = System.nanoTime()
        smoothPositionMs = anchorPos
        if (!isPlaying) return@LaunchedEffect
        while (isActive) {
            withFrameNanos { nowNanos ->
                smoothPositionMs = anchorPos + (nowNanos - anchorNanos) / 1_000_000L
            }
        }
    }

    // Compute stable AnimatedContent transition key (never embeds per-frame float/ms values!)
    val displayKey: String = remember(rawLyrics, parsedModel, adjustedPosition, smoothPositionMs) {
        when {
            rawLyrics == "LOADING_LYRICS_STATE" -> if (loadingPlaceholderText.isNotEmpty()) "LOADING" else "BLANK"
            rawLyrics.isNullOrBlank() -> "BLANK"
            parsedModel is ParsedNowPlayingLyrics.Karaoke -> {
                val lines = parsedModel.lines
                val pos = smoothPositionMs
                if (lines.isEmpty()) {
                    "BLANK"
                } else {
                    val first = lines.first()
                    if (pos < first.startMs) {
                        "DOTS_INTRO"
                    } else {
                        val idx = lines.indexOfLast { pos >= it.startMs }.coerceAtLeast(0)
                        val cur = lines[idx]
                        val next = lines.getOrNull(idx + 1)
                        if (next != null) {
                            val gap = next.startMs - cur.endMs
                            if (gap >= 3000L && pos > cur.endMs + 450L) {
                                "DOTS_GAP_$idx"
                            } else {
                                "KARAOKE_$idx"
                            }
                        } else {
                            if (pos > cur.endMs + 1500L) "BLANK" else "KARAOKE_$idx"
                        }
                    }
                }
            }
            parsedModel is ParsedNowPlayingLyrics.Standard -> {
                val lines = parsedModel.lines
                if (lines.isEmpty()) {
                    "BLANK"
                } else if (!parsedModel.isSynced) {
                    "STD_0_${lines.first().text}"
                } else {
                    val idx = lines.indexOfLast { adjustedPosition >= it.startTimeMs }.let { if (it == -1) 0 else it }
                    val txt = lines.getOrNull(idx)?.text.orEmpty()
                    if (txt.isBlank()) "BLANK" else "STD_${idx}_$txt"
                }
            }
            else -> "BLANK"
        }
    }

    AnimatedContent(
        targetState = displayKey,
        transitionSpec = {
            val enter = fadeIn(tween(320, easing = FastOutSlowInEasing)) +
                slideInVertically(tween(320, easing = FastOutSlowInEasing)) { (it * 0.35f).toInt() }
            val exit = fadeOut(tween(320, easing = FastOutSlowInEasing)) +
                slideOutVertically(tween(320, easing = FastOutSlowInEasing)) { -(it * 0.35f).toInt() }
            enter togetherWith exit
        },
        label = "nowPlayingLiveLyricStrip",
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    ) { key ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 28.dp),
            contentAlignment = contentAlignment
        ) {
            when {
                key == "LOADING" -> {
                    Text(
                        text = loadingPlaceholderText,
                        fontSize = fontSize,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.72f),
                        textAlign = textAlign,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                key == "DOTS_INTRO" && parsedModel is ParsedNowPlayingLyrics.Karaoke -> {
                    val firstStart = parsedModel.lines.firstOrNull()?.startMs ?: 1L
                    val progress = (smoothPositionMs.toFloat() / firstStart.coerceAtLeast(1L)).coerceIn(0f, 1f)
                    KaraokeBreathingDots(
                        progress = progress,
                        contentAlignment = contentAlignment,
                        modifier = Modifier.fillMaxWidth().height(26.dp)
                    )
                }
                key.startsWith("DOTS_GAP_") && parsedModel is ParsedNowPlayingLyrics.Karaoke -> {
                    val idx = key.removePrefix("DOTS_GAP_").toIntOrNull() ?: 0
                    val cur = parsedModel.lines.getOrNull(idx)
                    val next = parsedModel.lines.getOrNull(idx + 1)
                    val gapStart = (cur?.endMs ?: 0L) + 450L
                    val gapEnd = next?.startMs ?: (gapStart + 1L)
                    val progress = ((smoothPositionMs - gapStart).toFloat() / (gapEnd - gapStart).coerceAtLeast(1L)).coerceIn(0f, 1f)
                    KaraokeBreathingDots(
                        progress = progress,
                        contentAlignment = contentAlignment,
                        modifier = Modifier.fillMaxWidth().height(26.dp)
                    )
                }
                key.startsWith("KARAOKE_") && parsedModel is ParsedNowPlayingLyrics.Karaoke -> {
                    val idx = key.removePrefix("KARAOKE_").toIntOrNull() ?: 0
                    val line = parsedModel.lines.getOrNull(idx)
                    if (line != null) {
                        val pos = smoothPositionMs
                        val annotated = remember(line, pos) {
                            buildAnnotatedString {
                                val syllables = line.syllables
                                syllables.forEachIndexed { sIdx, syl ->
                                    val sStart = syl.start.toLong()
                                    val sEnd = if (syl.end > syl.start) syl.end.toLong() else {
                                        syllables.getOrNull(sIdx + 1)?.start?.toLong() ?: line.endMs
                                    }
                                    val frac = when {
                                        pos >= sEnd -> 1f
                                        pos <= sStart -> 0f
                                        else -> ((pos - sStart).toFloat() / (sEnd - sStart).coerceAtLeast(1L)).coerceIn(0f, 1f)
                                    }
                                    val alpha = 0.40f + (0.60f * frac)
                                    withStyle(
                                        SpanStyle(
                                            color = Color.White.copy(alpha = alpha),
                                            fontWeight = FontWeight.Bold
                                        )
                                    ) {
                                        append(syl.content)
                                    }
                                }
                            }
                        }
                        Text(
                            text = annotated,
                            fontSize = fontSize,
                            textAlign = textAlign,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .basicMarquee(
                                    iterations = Int.MAX_VALUE,
                                    initialDelayMillis = 1000,
                                    repeatDelayMillis = 1000,
                                    velocity = 35.dp
                                )
                        )
                    }
                }
                key.startsWith("STD_") -> {
                    val text = key.substringAfter("_").substringAfter("_")
                    if (text.isNotBlank()) {
                        Text(
                            text = text,
                            fontSize = fontSize,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.95f),
                            textAlign = textAlign,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .basicMarquee(
                                    iterations = Int.MAX_VALUE,
                                    initialDelayMillis = 1000,
                                    repeatDelayMillis = 1000,
                                    velocity = 35.dp
                                )
                        )
                    }
                }
                else -> {
                    Spacer(modifier = Modifier.fillMaxWidth().height(26.dp))
                }
            }
        }
    }
}

// ==========================================
// 🔥 Small Lyrics Card (Player Screen)
// ==========================================
@Composable
fun LyricsSection(
    song: Song,
    lyrics: String?,
    currentPosition: Long,
    modifier: Modifier = Modifier,
    onExpand: () -> Unit
) {
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("lyrics_prefs", Context.MODE_PRIVATE)
    val lyricsKey = "${song.title}_${song.artist}_lyrics"
    val savedLyrics = sharedPreferences.getString(lyricsKey, lyrics) ?: lyrics

    val parsedLines = remember(savedLyrics) { parseLytrixLrcStrict(savedLyrics ?: "") }
    val isSynced = remember(parsedLines) { parsedLines.any { it.startTimeMs >= 0L } }

    val activeIndex = remember(parsedLines, currentPosition, isSynced) {
        if (!isSynced || parsedLines.isEmpty()) -1 else {
            val idx = parsedLines.indexOfLast { currentPosition >= it.startTimeMs }
            if (idx == -1) 0 else idx
        }
    }

    val displayLine = if (savedLyrics == "LOADING_LYRICS_STATE") "🎵 Loading lyrics..."
    else if (savedLyrics.isNullOrBlank()) "Click to see lyrics..."
    else if (!isSynced) parsedLines.firstOrNull()?.text ?: "..."
    else if (activeIndex != -1 && activeIndex < parsedLines.size) parsedLines[activeIndex].text
    else "..."

    Card(
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            .defaultMinSize(minHeight = 40.dp)
            .clickable { onExpand() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = displayLine,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = if (savedLyrics.isNullOrBlank()) FontWeight.Normal else FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ==========================================
// 🔥 Full Screen Premium Lyrics Overlay (Apple Music Ambient Style)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsFullScreenOverlay(
    song: Song,
    lyrics: String?,
    currentPosition: Long,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onClose: () -> Unit,
    onSeek: (Long) -> Unit,
    onChangeSource: () -> Unit,
    onPrevious: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    durationMs: Long = song.duration,
    accentColor: Color? = null,
    providerName: String? = null
) {
    BackHandler { onClose() }
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("lyrics_prefs", Context.MODE_PRIVATE)
    val lyricsKey = "${song.title}_${song.artist}_lyrics"
    val providerKey = "${song.title}_${song.artist}_provider"

    // 🔥 FIX: LOADING সিগন্যালটা cache-এর চেয়ে অগ্রাধিকার পাবে
    var savedLyrics by remember(lyrics) {
        mutableStateOf(
            if (lyrics == "LOADING_LYRICS_STATE") "LOADING_LYRICS_STATE"
            else sharedPreferences.getString(lyricsKey, lyrics) ?: lyrics
        )
    }
    var currentProvider by remember(providerName, lyrics) {
        mutableStateOf(
            providerName ?: sharedPreferences.getString(providerKey, null)
        )
    }
    var isEditing by remember { mutableStateOf(false) }
    var editTextValue by remember { mutableStateOf(savedLyrics ?: "") }
    var showMenuSheet by remember { mutableStateOf(false) }
    var showOffsetSheet by remember { mutableStateOf(false) }
    var isRefetching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val lyricsOffsetMs = rememberLyricsOffsetMs(song)
    val adjustedPosition = remember(currentPosition, lyricsOffsetMs) {
        (currentPosition - lyricsOffsetMs).coerceAtLeast(0L)
    }

    val translationStatus by LyricsTranslationHelper.status.collectAsState()
    val hasActiveTranslations by LyricsTranslationHelper.hasActiveTranslations.collectAsState()
    var translatedLines by remember(song.id, savedLyrics) {
        mutableStateOf(LyricsTranslationHelper.getCachedTranslations(song.title, song.artist, context))
    }
    LaunchedEffect(song.id, savedLyrics) {
        translatedLines = LyricsTranslationHelper.getCachedTranslations(song.title, song.artist, context)
    }

    val romanizeKey = "${song.title}_${song.artist}_romanized"
    val appPrefs = remember { context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE) }
    var isRomanized by remember(song.id, romanizeKey) {
        mutableStateOf(
            sharedPreferences.getBoolean(romanizeKey, appPrefs.getBoolean("auto_romanize_lyrics", false))
        )
    }
    var romanizedLines by remember(song.id, savedLyrics, isRomanized) { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(song.id, savedLyrics, isRomanized) {
        if (!isRomanized || savedLyrics.isNullOrBlank() || savedLyrics == "LOADING_LYRICS_STATE") {
            romanizedLines = null
        } else {
            val raw = savedLyrics!!
            val generated = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                val baseLines = try {
                    autoParser.parse(decodeHtmlEntities(raw)).lines.map { line ->
                        when (line) {
                            is KaraokeLine -> line.syllables.joinToString("") { it.content }.trim()
                            is SyncedLine -> line.content.trim()
                            else -> ""
                        }
                    }
                } catch (_: Throwable) {
                    emptyList()
                }
                val sourceLines = if (baseLines.isNotEmpty()) baseLines else {
                    val strict = parseLytrixLrcStrict(raw)
                    if (strict.isNotEmpty()) strict.map { it.text } else raw.lines().map { it.trim() }
                }
                com.musicplayer.lytrix.lyrics.romanization.LyricsRomanizer.romanizeLines(sourceLines)
            }
            romanizedLines = generated.takeIf { list -> list.any { it.isNotBlank() } }
        }
    }
    val effectiveSecondaryLines = remember(translatedLines, romanizedLines, isRomanized) {
        val hasTrans = !translatedLines.isNullOrEmpty()
        when {
            hasTrans && !isRomanized -> translatedLines
            !romanizedLines.isNullOrEmpty() -> romanizedLines
            else -> translatedLines
        }
    }

    val isSwitchingSource = savedLyrics == "LOADING_LYRICS_STATE"
    val effectiveTint = accentColor ?: Color(0xFF4FD1C5)

    val parsedLines = remember(savedLyrics) {
        if (savedLyrics.isNullOrBlank() || savedLyrics == "LOADING_LYRICS_STATE") emptyList()
        else {
            val parsed = try {
                autoParser.parse(decodeHtmlEntities(savedLyrics!!))
            } catch (e: Throwable) {
                android.util.Log.w("LyricsSection", "AutoParser failed in overlay, falling back to strict LRC: ${e.message}")
                null
            }
            if (parsed != null && parsed.lines.isNotEmpty()) {
                parsed.lines.mapNotNull { line ->
                    val text = when (line) {
                        is KaraokeLine -> line.syllables.joinToString("") { it.content }
                        is SyncedLine -> line.content
                        else -> ""
                    }.trim()
                    if (text.isNotBlank()) LytrixLyricLine(line.start.toLong(), text) else null
                }
            } else {
                parseLytrixLrcStrict(savedLyrics!!)
            }
        }
    }

    val hasSyncedTimestamps = remember(parsedLines) {
        parsedLines.any { it.startTimeMs >= 0L }
    }
    val hasTranslation = !translatedLines.isNullOrEmpty()
    val maxSelectionLimit = if (hasTranslation) 3 else 5

    var isSelectionModeActive by remember { mutableStateOf(false) }
    val selectedIndices = remember { mutableStateListOf<Int>() }
    var showShareOptionDialog by remember { mutableStateOf(false) }
    var showStudioDialog by remember { mutableStateOf(false) }
    val shareDialogLines = remember { mutableStateListOf<SharedLyricLine>() }

    BackHandler(enabled = isSelectionModeActive) {
        isSelectionModeActive = false
        selectedIndices.clear()
    }

    val handleLineLongClick: (Int) -> Unit = { index ->
        if (!isSelectionModeActive) {
            isSelectionModeActive = true
            selectedIndices.clear()
            selectedIndices.add(index)
        } else {
            if (selectedIndices.contains(index)) {
                selectedIndices.remove(index)
                if (selectedIndices.isEmpty()) {
                    isSelectionModeActive = false
                }
            } else if (selectedIndices.size < maxSelectionLimit) {
                selectedIndices.add(index)
            } else {
                val msg = if (hasTranslation) "Maximum 3 lines can be selected with translation" else "Maximum 5 lines can be selected"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val handleLineClickInSelection: (Int) -> Unit = { index ->
        if (selectedIndices.contains(index)) {
            selectedIndices.remove(index)
            if (selectedIndices.isEmpty()) {
                isSelectionModeActive = false
            }
        } else if (selectedIndices.size < maxSelectionLimit) {
            selectedIndices.add(index)
        } else {
            val msg = if (hasTranslation) "Maximum 3 lines can be selected with translation" else "Maximum 5 lines can be selected"
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {} // 🔥 Consumes all touches so background icons (Cast button, etc.) never get clicked
            )
            .background(Color(0xFF09090B))
    ) {
        // 1. Dynamic Blurred Album Art Background
        if (song.albumArtUriString != null) {
            AsyncImage(
                model = song.albumArtUriString,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(50.dp)
                    .alpha(0.55f)
            )
        }

        // 2. Harmonic Ambient Gradient Overlay
        val ambientGradient = remember(effectiveTint) {
            Brush.verticalGradient(
                colors = listOf(
                    effectiveTint.copy(alpha = 0.42f),
                    effectiveTint.copy(alpha = 0.16f),
                    Color(0xFF09090B).copy(alpha = 0.88f),
                    Color.Black.copy(alpha = 0.96f)
                )
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ambientGradient)
        )

        // 3. Contrast Scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.22f))
        )

        // 4. Foreground Content Column
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // ==========================================
            // 🔥 Frosted Glass Pill-Shaped Top Bar
            // ==========================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // Prevents clicks beside 3-dots leaking through
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.12f))
                        .border(0.5.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(50))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (song.albumArtUriString != null) {
                        AsyncImage(
                            model = song.albumArtUriString,
                            contentDescription = "Album Art",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(38.dp).clip(CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) { Text("🎵", fontSize = 16.sp) }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (translationStatus is TranslationStatus.Translating) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                LytrixExpressiveSpinner(color = effectiveTint, size = 12.dp)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Translating with AI...",
                                    color = effectiveTint,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        } else {
                            Text(
                                text = song.artist,
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                            .clickable { onClose() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                            .clickable { showMenuSheet = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.MoreHoriz,
                            contentDescription = "Menu",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            val activeProvider = providerName ?: currentProvider

            // ==========================================
            // 🔥 CONTENT AREA
            // ==========================================
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (isRefetching || isSwitchingSource) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LytrixExpressiveSpinner(color = effectiveTint, size = 42.dp)
                    }
                } else if (isEditing) {
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        TextField(
                            value = editTextValue,
                            onValueChange = { editTextValue = it },
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            placeholder = { Text("Paste lyrics here...", color = Color.White.copy(alpha = 0.5f)) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Button(onClick = { isEditing = false }) { Text("Cancel") }
                            Button(onClick = {
                                sharedPreferences.edit().putString(lyricsKey, editTextValue).apply()
                                savedLyrics = editTextValue
                                isEditing = false
                            }) { Text("Save") }
                        }
                    }
                } else if (savedLyrics.isNullOrBlank()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "No lyrics available.",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 24.dp)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Button(
                                onClick = {
                                    isRefetching = true
                                    scope.launch {
                                        val result = com.musicplayer.lytrix.utils.LyricsFetcher.fetchLyricsWithResult(context, song)
                                        if (result != null) {
                                            sharedPreferences.edit()
                                                .putString(lyricsKey, result.lyrics)
                                                .putString(providerKey, result.providerName)
                                                .apply()
                                            savedLyrics = result.lyrics
                                            currentProvider = result.providerName
                                        } else {
                                            Toast.makeText(context, "No lyrics found online.", Toast.LENGTH_SHORT).show()
                                        }
                                        isRefetching = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = effectiveTint)
                            ) { Text("Search Web", color = Color.Black) }

                            Button(
                                onClick = { editTextValue = ""; isEditing = true },
                                colors = ButtonDefaults.buttonColors(containerColor = effectiveTint)
                            ) { Text("Add Manual", color = Color.Black) }
                        }
                    }
                } else {
                    LytrixPremiumLyricsEngine(
                        lyricsList = parsedLines,
                        rawLrcData = savedLyrics!!,
                        currentPositionMs = adjustedPosition,
                        isPlaying = isPlaying,
                        onSeekTo = { targetMs -> onSeek((targetMs + lyricsOffsetMs).coerceAtLeast(0L)) },
                        modifier = Modifier.fillMaxSize(),
                        providerName = activeProvider,
                        translatedLines = effectiveSecondaryLines,
                        onLineLongClick = handleLineLongClick,
                        isSelectionActive = isSelectionModeActive,
                        onLineClickInSelection = handleLineClickInSelection,
                        selectedIndices = selectedIndices,
                        accentColor = effectiveTint
                    )
                }
            }

            // ==========================================
            // 🔥 Sleek Bottom Playback Controls
            // ==========================================
            LyricsBottomPlaybackBar(
                currentPosition = currentPosition,
                durationMs = durationMs,
                isPlaying = isPlaying,
                onPlayPause = onPlayPause,
                onSeek = onSeek,
                onPrevious = onPrevious,
                onNext = onNext,
                accentColor = effectiveTint
            )
        }

        // ==========================================
        // 🔥 Floating Multi-Line Selection Action Bar
        // ==========================================
        AnimatedVisibility(
            visible = isSelectionModeActive,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 100.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = Color(0xFF1E1E24),
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            isSelectionModeActive = false
                            selectedIndices.clear()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Cancel",
                            tint = Color.White.copy(alpha = 0.8f)
                        )
                    }

                    Button(
                        onClick = {
                            val linesToShare = selectedIndices.sorted().mapNotNull { idx ->
                                val orig = parsedLines.getOrNull(idx)?.text?.trim()
                                if (!orig.isNullOrBlank()) {
                                    val trans = translatedLines?.getOrNull(idx)?.trim()?.takeIf { it.isNotBlank() }
                                    SharedLyricLine(orig, trans)
                                } else null
                            }
                            if (linesToShare.isNotEmpty()) {
                                shareDialogLines.clear()
                                shareDialogLines.addAll(linesToShare)
                                showShareOptionDialog = true
                            }
                            isSelectionModeActive = false
                            selectedIndices.clear()
                        },
                        enabled = selectedIndices.isNotEmpty(),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = effectiveTint),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Share (${selectedIndices.size})",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        if (showOffsetSheet) {
            LyricsSyncOffsetFloatingBar(
                offsetMs = lyricsOffsetMs,
                hasSyncedTimestamps = hasSyncedTimestamps,
                currentPositionMs = currentPosition,
                accentColor = effectiveTint,
                onOffsetChange = { newOffset ->
                    saveLyricsOffsetMs(context, song, newOffset)
                },
                onDismiss = { showOffsetSheet = false },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 14.dp, vertical = 14.dp)
            )
        }
    }

    if (showShareOptionDialog && shareDialogLines.isNotEmpty()) {
        LyricsShareOptionDialog(
            sharedLines = shareDialogLines,
            songTitle = song.title,
            artistName = song.artist,
            onDismiss = { showShareOptionDialog = false },
            onShareAsImage = {
                showShareOptionDialog = false
                showStudioDialog = true
            }
        )
    }

    if (showStudioDialog && shareDialogLines.isNotEmpty()) {
        LyricsImageShareStudioDialog(
            sharedLines = shareDialogLines,
            songTitle = song.title,
            artistName = song.artist,
            coverArtUrl = song.albumArtUriString,
            onDismiss = { showStudioDialog = false }
        )
    }

    // ==========================================
    // 🔥 Lytrix EXACT Grid Menu
    // ==========================================
    if (showMenuSheet) {
        com.musicplayer.lytrix.ui.component.LytrixFluidBottomSheet(
            onDismissRequest = { showMenuSheet = false },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .padding(bottom = 24.dp)
            ) {
                // Row 1
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MenuGridItem(icon = Icons.Rounded.Edit, label = "Edit", modifier = Modifier.weight(1f)) {
                        showMenuSheet = false
                        editTextValue = savedLyrics ?: ""
                        isEditing = true
                    }
                    MenuGridItem(icon = Icons.Rounded.Refresh, label = "Refetch", modifier = Modifier.weight(1f)) {
                        showMenuSheet = false
                        isRefetching = true
                        scope.launch {
                            val result = com.musicplayer.lytrix.utils.LyricsFetcher.fetchLyricsWithResult(context, song)
                            if (result != null) {
                                sharedPreferences.edit()
                                    .putString(lyricsKey, result.lyrics)
                                    .putString(providerKey, result.providerName)
                                    .apply()
                                savedLyrics = result.lyrics
                                currentProvider = result.providerName
                                LyricsTranslationHelper.clearTranslations(song.title, song.artist, context)
                                translatedLines = null
                                Toast.makeText(context, "Loaded from ${result.providerName}", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "No new lyrics found.", Toast.LENGTH_SHORT).show()
                            }
                            isRefetching = false
                        }
                    }
                    val isTranslating = translationStatus is TranslationStatus.Translating
                    val isTranslated = hasActiveTranslations || !translatedLines.isNullOrEmpty()

                    MenuGridItem(
                        icon = Icons.Rounded.Translate,
                        label = if (isTranslating) "Translating..." else if (isTranslated) "Untranslate" else "Translate",
                        modifier = Modifier.weight(1f)
                    ) {
                        showMenuSheet = false
                        if (isTranslating) {
                            LyricsTranslationHelper.cancelTranslation()
                            Toast.makeText(context, "Translation cancelled", Toast.LENGTH_SHORT).show()
                        } else if (isTranslated) {
                            LyricsTranslationHelper.clearTranslations(song.title, song.artist, context)
                            translatedLines = null
                            Toast.makeText(context, "Translation removed", Toast.LENGTH_SHORT).show()
                        } else {
                            if (savedLyrics.isNullOrBlank()) {
                                Toast.makeText(context, "No lyrics to translate", Toast.LENGTH_SHORT).show()
                                return@MenuGridItem
                            }
                            val strictLines = parseLytrixLrcStrict(savedLyrics!!)
                            val linesToTranslate = if (strictLines.isNotEmpty()) {
                                strictLines.map { it.text }
                            } else {
                                savedLyrics!!.lines().map { it.trim() }.filter { it.isNotBlank() }
                            }

                            LyricsTranslationHelper.translateLines(
                                lines = linesToTranslate,
                                songTitle = song.title,
                                songArtist = song.artist,
                                context = context,
                                scope = scope,
                                onSuccess = { res ->
                                    isRomanized = false
                                    sharedPreferences.edit().putBoolean(romanizeKey, false).apply()
                                    translatedLines = res
                                    Toast.makeText(context, "Lyrics translated!", Toast.LENGTH_SHORT).show()
                                },
                                onError = { err ->
                                    Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Row 2
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MenuGridItem(icon = Icons.Rounded.SwapHoriz, label = "Change", modifier = Modifier.weight(1f)) {
                        showMenuSheet = false
                        onChangeSource()
                    }
                    MenuGridItem(icon = Icons.Rounded.Search, label = "Search", modifier = Modifier.weight(1f)) {
                        showMenuSheet = false
                        try {
                            val query = Uri.encode("${song.title} ${song.artist} lyrics")
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$query"))
                            context.startActivity(intent)
                        } catch (e: Exception) {}
                    }
                    MenuGridItem(icon = Icons.Rounded.Delete, label = "Delete", modifier = Modifier.weight(1f)) {
                        showMenuSheet = false
                        sharedPreferences.edit()
                            .remove(lyricsKey)
                            .remove(providerKey)
                            .remove(lyricsOffsetKey(song))
                            .apply()
                        LyricsTranslationHelper.clearTranslations(song.title, song.artist, context)
                        translatedLines = null
                        savedLyrics = null
                        currentProvider = null
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Sync Offset & Offline Romanization Action Row
                val offsetBadge = when {
                    lyricsOffsetMs > 0L -> "+${lyricsOffsetMs} ms"
                    lyricsOffsetMs < 0L -> "${lyricsOffsetMs} ms"
                    else -> "0 ms"
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f)),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                showMenuSheet = false
                                showOffsetSheet = true
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Tune,
                                    contentDescription = "Sync Offset",
                                    tint = if (lyricsOffsetMs != 0L) effectiveTint else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Offset",
                                    fontSize = 13.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (lyricsOffsetMs != 0L) effectiveTint.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f)
                            ) {
                                Text(
                                    text = offsetBadge,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (lyricsOffsetMs != 0L) effectiveTint else Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isRomanized) effectiveTint.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                showMenuSheet = false
                                val nextVal = !isRomanized
                                isRomanized = nextVal
                                sharedPreferences.edit().putBoolean(romanizeKey, nextVal).apply()
                                val msg = if (nextVal) "Offline romanization enabled" else "Romanization off"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Spellcheck,
                                    contentDescription = "Romanize",
                                    tint = if (isRomanized) effectiveTint else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Romanize",
                                    fontSize = 13.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isRomanized) effectiveTint.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.1f)
                            ) {
                                Text(
                                    text = if (isRomanized) "ON" else "OFF",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isRomanized) effectiveTint else Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricsSyncOffsetFloatingBar(
    offsetMs: Long,
    hasSyncedTimestamps: Boolean,
    currentPositionMs: Long,
    accentColor: Color,
    onOffsetChange: (Long) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val formattedOffset = if (offsetMs > 0L) "+${offsetMs}ms" else "${offsetMs}ms"
    val adjustedMs = (currentPositionMs - offsetMs).coerceAtLeast(0L)
    val hintText = when {
        !hasSyncedTimestamps -> "Plain text lyrics — timestamps required for sync offset"
        offsetMs > 0L -> "Lyrics delayed (+${offsetMs / 1000f}s) · Active @ ${formatMinSecMs(adjustedMs)}"
        offsetMs < 0L -> "Lyrics earlier (${offsetMs / 1000f}s) · Active @ ${formatMinSecMs(adjustedMs)}"
        else -> "Synced @ ${formatMinSecMs(adjustedMs)} · Lyrics remain visible above"
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xEA121216),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Sync Offset",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = accentColor.copy(alpha = 0.18f)
                    ) {
                        Text(
                            text = formattedOffset,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (offsetMs != 0L) accentColor else Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = { onOffsetChange(0L) },
                        enabled = offsetMs != 0L,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Reset",
                            fontSize = 12.sp,
                            color = if (offsetMs != 0L) accentColor else Color.White.copy(alpha = 0.35f)
                        )
                    }
                    FilledTonalButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = accentColor,
                            contentColor = Color.Black
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("Done", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Text(
                text = hintText,
                fontSize = 11.5.sp,
                color = if (!hasSyncedTimestamps) Color(0xFFFFB74D) else Color.White.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Slider(
                value = offsetMs.toFloat().coerceIn(-6000f, 6000f),
                onValueChange = { raw ->
                    val snapped = ((raw / 100f).toInt() * 100L).coerceIn(-15_000L, 15_000L)
                    onOffsetChange(snapped)
                },
                valueRange = -6000f..6000f,
                colors = SliderDefaults.colors(
                    thumbColor = accentColor,
                    activeTrackColor = accentColor,
                    inactiveTrackColor = Color.White.copy(alpha = 0.18f)
                ),
                modifier = Modifier.fillMaxWidth().height(26.dp)
            )

            val stepDeltas = listOf(-2000L to "-2s", -500L to "-0.5s", -100L to "-0.1s", 100L to "+0.1s", 500L to "+0.5s", 2000L to "+2s")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                stepDeltas.forEach { (delta, label) ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White.copy(alpha = 0.1f),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onOffsetChange((offsetMs + delta).coerceIn(-15_000L, 15_000L)) }
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatMinSecMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    return "%02d:%02d".format(totalSec / 60, totalSec % 60)
}

@Composable
fun LyricsBottomPlaybackBar(
    currentPosition: Long,
    durationMs: Long,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?,
    accentColor: Color
) {
    val durationSafe = if (durationMs > 0L) durationMs else 1L
    val progress = (currentPosition.toFloat() / durationSafe).coerceIn(0f, 1f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Slider(
            value = progress,
            onValueChange = { frac -> onSeek((frac * durationSafe).toLong()) },
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White.copy(alpha = 0.92f),
                inactiveTrackColor = Color.White.copy(alpha = 0.22f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTime(currentPosition),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.65f)
            )
            Text(
                text = formatTime(durationMs),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.65f)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onPrevious != null) {
                IconButton(
                    onClick = onPrevious,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = "Previous",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(28.dp))
            }

            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.22f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                    .clickable { onPlayPause() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }

            if (onNext != null) {
                Spacer(modifier = Modifier.width(28.dp))
                IconButton(
                    onClick = onNext,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun MenuGridItem(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f)),
        modifier = modifier.aspectRatio(1f).clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(26.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = label, fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ==========================================
// 🔥 REAL PREMIUM ENGINE (mocharealm 2.0.0-rc.2)
// ==========================================
private val autoParser = AutoParser()

@Composable
fun LytrixPremiumLyricsEngine(
    lyricsList: List<LytrixLyricLine>? = null,
    rawLrcData: String,
    currentPositionMs: Long,
    isPlaying: Boolean,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
    providerName: String? = null,
    translatedLines: List<String>? = null,
    onLineLongClick: ((Int) -> Unit)? = null,
    isSelectionActive: Boolean = false,
    onLineClickInSelection: ((Int) -> Unit)? = null,
    selectedIndices: List<Int> = emptyList(),
    accentColor: Color = Color(0xFF4FD1C5)
) {
    val parsedLyrics = remember(rawLrcData, translatedLines) {
        val base = try {
            autoParser.parse(decodeHtmlEntities(rawLrcData))
        } catch (e: Throwable) {
            android.util.Log.w("LyricsSection", "AutoParser failed in Premium Engine: ${e.message}")
            val parsedLines = parseLytrixLrcStrict(rawLrcData)
            val mappedLines: List<com.mocharealm.accompanist.lyrics.core.model.ISyncedLine> = parsedLines.mapIndexed { idx, lrcLine ->
                val nextStart = parsedLines.getOrNull(idx + 1)?.startTimeMs?.toInt() ?: (lrcLine.startTimeMs.toInt() + 4000)
                SyncedLine(
                    content = lrcLine.text,
                    translation = null,
                    start = lrcLine.startTimeMs.toInt(),
                    end = nextStart
                )
            }
            com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics(
                lines = mappedLines,
                title = "",
                id = "",
                artists = emptyList()
            )
        }
        if (translatedLines.isNullOrEmpty()) {
            base
        } else {
            val updatedLines = base.lines.mapIndexed { index, line ->
                val trans = translatedLines.getOrNull(index)?.takeIf { it.isNotBlank() }
                when (line) {
                    is KaraokeLine -> line.copy(translation = trans)
                    is SyncedLine -> line.copy(translation = trans)
                    else -> line
                }
            }
            base.copy(lines = updatedLines)
        }
    }

    val linesList = remember(lyricsList, parsedLyrics, rawLrcData) {
        lyricsList ?: if (parsedLyrics.lines.isNotEmpty()) {
            parsedLyrics.lines.mapNotNull { line ->
                val text = when (line) {
                    is KaraokeLine -> line.syllables.joinToString("") { it.content }
                    is SyncedLine -> line.content
                    else -> ""
                }.trim()
                if (text.isNotBlank()) LytrixLyricLine(line.start.toLong(), text) else null
            }
        } else {
            parseLytrixLrcStrict(rawLrcData)
        }
    }

    var smoothPositionMs by remember { mutableLongStateOf(currentPositionMs) }

    LaunchedEffect(currentPositionMs, isPlaying) {
        val anchorRealPositionMs = currentPositionMs
        val anchorNanos = System.nanoTime()
        smoothPositionMs = currentPositionMs

        if (!isPlaying) return@LaunchedEffect

        while (isActive) {
            withFrameNanos { nowNanos ->
                val elapsedMs = (nowNanos - anchorNanos) / 1_000_000
                smoothPositionMs = anchorRealPositionMs + elapsedMs
            }
        }
    }

    val latestSmoothPosition = rememberUpdatedState(smoothPositionMs)

    if (parsedLyrics.lines.isNotEmpty() && !isSelectionActive) {
        val listState = rememberLyricsLazyListState()

        // Meld-style scroll-away: visible at the start of the song and smoothly scrolls up & fades away as lyrics advance
        val scrollOffset = listState.firstVisibleItemScrollOffset
        val firstItem = listState.firstVisibleItemIndex
        val progress = if (firstItem > 0) 1f else (scrollOffset / 120f).coerceIn(0f, 1f)
        val providerAlpha = (1f - progress) * 0.6f
        val providerTranslationY = -progress * 24f

        Column(modifier = modifier.fillMaxSize()) {
            if (providerAlpha > 0.02f && !providerName.isNullOrBlank()) {
                Text(
                    text = "Lyrics from $providerName",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = providerAlpha),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            alpha = providerAlpha
                            translationY = providerTranslationY
                        }
                        .padding(top = 4.dp, bottom = 2.dp)
                )
            }

            KaraokeLyricsView(
                listState = listState,
                lyrics = parsedLyrics,
                currentPosition = { latestSmoothPosition.value.toInt() },
                onLineClicked = { line ->
                    if (isSelectionActive) {
                        val idx = parsedLyrics.lines.indexOf(line)
                        if (idx >= 0) onLineClickInSelection?.invoke(idx)
                    } else {
                        onSeekTo(line.start.toLong())
                    }
                },
                onLinePressed = { line ->
                    val idx = parsedLyrics.lines.indexOf(line)
                    if (idx >= 0) {
                        onLineLongClick?.invoke(idx)
                    }
                },
                normalLineTextStyle = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 36.sp
                ),
                accompanimentLineTextStyle = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 21.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 30.sp
                ),
                textColor = Color.White,
                blendMode = BlendMode.SrcOver,
                useBlurEffect = true,
                anchor = LyricsAnchor.Fraction(0.30f),
                topFade = LyricsFade.Fraction(0.12f),
                bottomFade = LyricsFade.Fraction(0.20f),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            )
        }
    } else {
        SmoothStandardLrcEngine(
            lyricsList = linesList,
            rawLrcData = rawLrcData,
            currentPositionMs = currentPositionMs,
            onSeekTo = onSeekTo,
            modifier = modifier,
            providerName = providerName,
            translatedLines = translatedLines,
            onLineLongClick = onLineLongClick,
            isSelectionActive = isSelectionActive,
            onLineClickInSelection = onLineClickInSelection,
            selectedIndices = selectedIndices,
            accentColor = accentColor
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SmoothStandardLrcEngine(
    lyricsList: List<LytrixLyricLine>? = null,
    rawLrcData: String? = null,
    currentPositionMs: Long,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
    providerName: String? = null,
    translatedLines: List<String>? = null,
    onLineLongClick: ((Int) -> Unit)? = null,
    isSelectionActive: Boolean = false,
    onLineClickInSelection: ((Int) -> Unit)? = null,
    selectedIndices: List<Int> = emptyList(),
    accentColor: Color = Color(0xFF4FD1C5)
) {
    val listState = rememberLazyListState()
    val lyrics = remember(lyricsList, rawLrcData) {
        lyricsList ?: parseLytrixLrcStrict(rawLrcData ?: "")
    }
    val isSynced = remember(lyrics) { lyrics.any { it.startTimeMs >= 0L } }

    var isManualScrolling by remember { mutableStateOf(false) }

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            isManualScrolling = true
        } else {
            delay(3000)
            isManualScrolling = false
        }
    }

    LaunchedEffect(isSelectionActive) {
        if (isSelectionActive && selectedIndices.isNotEmpty()) {
            val firstSelectedIdx = selectedIndices.firstOrNull() ?: -1
            if (firstSelectedIdx >= 0) {
                listState.scrollToItem(maxOf(0, firstSelectedIdx - 1))
            }
        }
    }

    val currentLineIndex = remember(currentPositionMs, lyrics, isSynced) {
        if (!isSynced || lyrics.isEmpty()) -1 else {
            val idx = lyrics.indexOfLast { currentPositionMs >= it.startTimeMs }
            if (idx == -1) 0 else idx
        }
    }

    LaunchedEffect(currentLineIndex, isManualScrolling, isSelectionActive) {
        if (!isSelectionActive && isSynced && !isManualScrolling && currentLineIndex >= 0 && lyrics.isNotEmpty()) {
            val targetIndex = maxOf(0, currentLineIndex - 1)
            listState.animateScrollToItem(index = targetIndex)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 80.dp, bottom = 120.dp, start = 24.dp, end = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = if (isSynced) Alignment.Start else Alignment.CenterHorizontally
    ) {
        if (!providerName.isNullOrBlank()) {
            item {
                Text(
                    text = "Lyrics from $providerName",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
            }
        }
        itemsIndexed(lyrics) { index, line ->
            val isActive = isSynced && (index == currentLineIndex)
            val isSelected = selectedIndices.contains(index)

            val scale by animateFloatAsState(
                targetValue = if (isSelected) 1.02f else if (isActive) 1.04f else 1f,
                animationSpec = tween(300, easing = FastOutSlowInEasing),
                label = "scale"
            )

            // When in selection mode: selected lines are 1f, unselected lines are clear at 0.55f (no blur!)
            val alpha by animateFloatAsState(
                targetValue = if (isSelectionActive) {
                    if (isSelected) 1f else 0.55f
                } else {
                    if (isActive) 1f else if (!isSynced) 0.9f else 0.35f
                },
                animationSpec = tween(300),
                label = "alpha"
            )

            // When in selection mode: ALL blur is 0.dp so lyrics are 100% crisp and readable
            val blurRadius by animateDpAsState(
                targetValue = if (isSelectionActive) 0.dp else if (isActive || isSelected || !isSynced) 0.dp else 2.5.dp,
                animationSpec = tween(300),
                label = "blur"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }
                    .blur(blurRadius)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (isSelected) accentColor.copy(alpha = 0.30f)
                        else Color.Transparent
                    )
                    .then(
                        if (isSelected) Modifier.border(
                            width = 1.5.dp,
                            color = accentColor.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(14.dp)
                        ) else Modifier
                    )
                    .combinedClickable(
                        onClick = {
                            if (isSelectionActive) {
                                onLineClickInSelection?.invoke(index)
                            } else if (line.startTimeMs >= 0) {
                                onSeekTo(line.startTimeMs)
                                isManualScrolling = false
                            }
                        },
                        onLongClick = {
                            onLineLongClick?.invoke(index)
                        }
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Column(
                    horizontalAlignment = if (isSynced) Alignment.Start else Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = line.text,
                        color = Color.White,
                        fontWeight = if (isActive || isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                        fontSize = if (isActive) 27.sp else 23.sp,
                        lineHeight = 36.sp,
                        textAlign = if (isSynced) TextAlign.Start else TextAlign.Center
                    )
                    val trans = translatedLines?.getOrNull(index)
                    if (!trans.isNullOrBlank()) {
                        Text(
                            text = trans,
                            color = Color.White.copy(alpha = if (isActive || isSelected) 0.85f else 0.5f),
                            fontWeight = FontWeight.Normal,
                            fontSize = if (isActive) 18.sp else 15.sp,
                            lineHeight = 26.sp,
                            textAlign = if (isSynced) TextAlign.Start else TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
