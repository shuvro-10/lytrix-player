package com.musicplayer.lytrix.ui.cinema

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.LocalMovies
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun CinemaEngineDownloadScreen(
    onEngineReady: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloadState by CinemaEngineManager.downloadState.collectAsState()
    val accent = cinemaAccent()

    val expectedSize = remember { CinemaEngineManager.getExpectedDownloadSize() }
    val archName = remember { CinemaEngineManager.getArchitectureName() }

    LaunchedEffect(downloadState) {
        if (downloadState is EngineDownloadState.Ready) {
            onEngineReady()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CinemaPalette.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onExit) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Lytrix Cinema Studio",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Scrollable Content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(16.dp))

                // Hero Graphic Icon Container
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(accent.copy(alpha = 0.35f), Color.Transparent)
                            )
                        )
                        .border(1.dp, accent.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Movie,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(46.dp)
                    )
                }

                Spacer(Modifier.height(20.dp))

                Text(
                    text = "Cinema & Torrent Studio",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Stream unlimited Movies, TV Series, Dual-Audio tracks, and direct P2P torrents.",
                    color = CinemaPalette.textSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                Spacer(Modifier.height(24.dp))

                // Architecture and Size Pill Badges
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PillBadge(label = "Download: $expectedSize", accent = accent)
                    PillBadge(label = archName, accent = Color.White.copy(alpha = 0.2f))
                }

                Spacer(Modifier.height(28.dp))

                // Feature Highlights
                FeatureRow(
                    icon = Icons.Rounded.LocalMovies,
                    title = "Complete TMDb Catalog",
                    desc = "Trending Movies, TV Series, Anime, Cast, and Trailers in HD."
                )
                Spacer(Modifier.height(14.dp))
                FeatureRow(
                    icon = Icons.Rounded.Speed,
                    title = "Instant P2P Streaming",
                    desc = "Dual-Audio & Hindi torrent streams buffered directly into ExoPlayer."
                )
                Spacer(Modifier.height(14.dp))
                FeatureRow(
                    icon = Icons.Rounded.Download,
                    title = "Background Torrent Downloader",
                    desc = "Download movies directly to your storage for seamless offline watching."
                )
                Spacer(Modifier.height(14.dp))
                FeatureRow(
                    icon = Icons.Rounded.Tv,
                    title = "Multi-Server Web Mirrors",
                    desc = "VidLink 1080p, VidSrc Gateway, and 2Embed with zero ads."
                )

                Spacer(Modifier.height(36.dp))

                // Action Area
                when (val state = downloadState) {
                    is EngineDownloadState.Idle -> {
                        Button(
                            onClick = {
                                scope.launch {
                                    CinemaEngineManager.downloadAndInstallEngine(context)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = accent),
                            shape = RoundedCornerShape(percent = 50),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Icon(Icons.Rounded.CloudDownload, contentDescription = null)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Download Cinema Engine ($expectedSize)",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    is EngineDownloadState.Downloading -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = 0.05f))
                                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                                .padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Downloading Cinema Engine...",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                            Spacer(Modifier.height(12.dp))
                            LinearProgressIndicator(
                                progress = { state.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = accent,
                                trackColor = Color.White.copy(alpha = 0.1f)
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${(state.progress * 100).toInt()}% • ${state.speedStr}",
                                    color = accent,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${formatBytes(state.downloadedBytes)} / ${formatBytes(state.totalBytes)}",
                                    color = CinemaPalette.textSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    is EngineDownloadState.Extracting -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(36.dp),
                                color = accent,
                                strokeWidth = 3.dp
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "Installing & Verifying Engine...",
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        }
                    }

                    is EngineDownloadState.Ready -> {
                        Text(
                            text = "✅ Engine Ready! Launching Cinema...",
                            color = Color(0xFF4CAF50),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    is EngineDownloadState.Error -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = state.message,
                                color = Color(0xFFFF5252),
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    scope.launch {
                                        CinemaEngineManager.downloadAndInstallEngine(context)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = accent),
                                shape = RoundedCornerShape(percent = 50)
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Retry Download", color = Color.White)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun PillBadge(label: String, accent: Color) {
    Surface(
        color = accent.copy(alpha = 0.16f),
        shape = RoundedCornerShape(percent = 50),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.35f))
    ) {
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, title: String, desc: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.03f))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = desc,
                color = CinemaPalette.textSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val mb = bytes / (1024f * 1024f)
    return String.format(Locale.US, "%.1f MB", mb)
}
