package com.musicplayer.lytrix.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import compose.icons.TablerIcons
import compose.icons.tablericons.*
import com.musicplayer.lytrix.utils.AppUpdateInfo
import com.musicplayer.lytrix.utils.AppUpdateManager
import com.musicplayer.lytrix.utils.DownloadProgressState
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

@Composable
fun AppUpdateDialog(
    updateInfo: AppUpdateInfo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accentColor = com.musicplayer.lytrix.ui.theme.LocalAccentColor.current
    val isLightMode = MaterialTheme.colorScheme.background.luminance() > 0.5f

    val dialogBg = if (isLightMode) Color(0xFFFFFFFF) else Color(0xFF131518)
    val changelogBg = if (isLightMode) Color(0xFFF3F4F6) else Color(0xFF0C0E10)
    val borderColor = if (isLightMode) Color(0xFFE5E7EB) else Color(0xFF22262B)
    val mainTextColor = if (isLightMode) Color(0xFF111827) else Color(0xFFF3F4F6)
    val subTextColor = if (isLightMode) Color(0xFF4B5563) else Color(0xFFD1D5DB)

    var downloadState by remember { mutableStateOf<DownloadProgressState>(DownloadProgressState.Idle) }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 MB"
        val mb = bytes.toDouble() / (1024 * 1024)
        return String.format(Locale.US, "%.1f MB", mb)
    }

    val cleanChangelog = updateInfo.changelog
        .replace("\\n", "\n")
        .replace("\\r", "")
        .trim()

    val isApkAvailable = updateInfo.downloadUrl.endsWith(".apk", ignoreCase = true)

    Dialog(
        onDismissRequest = {
            if (downloadState !is DownloadProgressState.Downloading && !updateInfo.forceUpdate) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = downloadState !is DownloadProgressState.Downloading && !updateInfo.forceUpdate,
            dismissOnClickOutside = downloadState !is DownloadProgressState.Downloading && !updateInfo.forceUpdate
        )
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = dialogBg,
            border = BorderStroke(1.dp, borderColor),
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Icon Halo
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = TablerIcons.CloudDownload,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Title
                Text(
                    text = "Update Available",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = mainTextColor
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Version Transition Row: Current -> New
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Current version chip
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isLightMode) Color(0xFFE5E7EB) else Color(0xFF1E2125)
                    ) {
                        Text(
                            text = "v${updateInfo.currentVersion}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = subTextColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = TablerIcons.ArrowRight,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    // New version chip
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = accentColor.copy(alpha = 0.18f)
                    ) {
                        Text(
                            text = "v${updateInfo.versionName}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    if (updateInfo.apkSize > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isLightMode) Color(0xFFE5E7EB) else Color(0xFF1E2125)
                        ) {
                            Text(
                                text = formatBytes(updateInfo.apkSize),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = subTextColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // What's New Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Icon(
                        imageVector = TablerIcons.Flame,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "What's New in this update",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = mainTextColor
                    )
                }

                // Scrollable Changelog Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = changelogBg,
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (cleanChangelog.isNotBlank()) {
                            Text(
                                text = cleanChangelog,
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                color = subTextColor
                            )
                        } else {
                            Text(
                                text = "A new version of Lytrix Player is ready to install with stability and performance improvements.",
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                color = subTextColor
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Download Progress View (when downloading)
                when (val state = downloadState) {
                    is DownloadProgressState.Downloading -> {
                        val animatedProgress by animateFloatAsState(targetValue = state.progress, label = "DownloadProgress")
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            LinearProgressIndicator(
                                progress = { animatedProgress },
                                color = accentColor,
                                trackColor = accentColor.copy(alpha = 0.2f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${(state.progress * 100).toInt()}%",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accentColor
                                )
                                Text(
                                    text = "${formatBytes(state.downloadedBytes)} / ${formatBytes(state.totalBytes)}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = subTextColor
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    is DownloadProgressState.Error -> {
                        Text(
                            text = "Download error: ${state.message}",
                            fontSize = 12.sp,
                            color = Color(0xFFEF5350),
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }
                    is DownloadProgressState.Completed -> {
                        Text(
                            text = "✓ Download completed! Launching installer...",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF66BB6A),
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }
                    else -> {}
                }

                // Action Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Secondary Button: Later
                    if (downloadState !is DownloadProgressState.Downloading && !updateInfo.forceUpdate) {
                        TextButton(
                            onClick = {
                                AppUpdateManager.dismissVersion(context, updateInfo.versionName)
                                onDismiss()
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Later",
                                color = subTextColor,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Primary Button: Download / Install / View
                    when (val state = downloadState) {
                        is DownloadProgressState.Downloading -> {
                            Button(
                                onClick = { downloadState = DownloadProgressState.Idle },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF262A2E)),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Cancel", color = Color.White, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        is DownloadProgressState.Completed -> {
                            Button(
                                onClick = { AppUpdateManager.installApk(context, state.file) },
                                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(imageVector = TablerIcons.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Install Now", fontWeight = FontWeight.Bold, color = Color.Black)
                            }
                        }
                        else -> {
                            Button(
                                onClick = {
                                    if (isApkAvailable) {
                                        scope.launch {
                                            AppUpdateManager.downloadApk(
                                                context = context,
                                                downloadUrl = updateInfo.downloadUrl,
                                                versionName = updateInfo.versionName,
                                                onProgress = { downloadState = it }
                                            )
                                        }
                                    } else {
                                        AppUpdateManager.openDownloadUrl(context, updateInfo.htmlUrl)
                                        onDismiss()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1.3f)
                            ) {
                                Icon(
                                    imageVector = if (isApkAvailable) TablerIcons.Download else TablerIcons.ExternalLink,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isApkAvailable) "Download & Install" else "View on GitHub",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
