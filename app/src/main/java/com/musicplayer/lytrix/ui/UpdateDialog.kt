package com.musicplayer.lytrix.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.CloudDownload
import compose.icons.tablericons.Download
import compose.icons.tablericons.Flame
import com.musicplayer.lytrix.utils.AppUpdateInfo
import com.musicplayer.lytrix.utils.AppUpdateManager

@Composable
fun AppUpdateDialog(
    updateInfo: AppUpdateInfo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val accentColor = com.musicplayer.lytrix.ui.theme.LocalAccentColor.current
    val isLightMode = MaterialTheme.colorScheme.background.luminance() > 0.5f

    // 🎨 সেটিংস স্ক্রিনের মতোই পিচ ডার্ক ব্ল্যাক কালার
    val dialogBg = if (isLightMode) Color(0xFFFFFFFF) else Color(0xFF161819)
    val changelogBg = if (isLightMode) Color(0xFFF3F4F6) else Color(0xFF0E1011)
    val borderColor = if (isLightMode) Color(0xFFE5E7EB) else Color(0xFF26292B)
    val mainTextColor = if (isLightMode) Color(0xFF111827) else Color(0xFFF3F4F6)
    val subTextColor = if (isLightMode) Color(0xFF4B5563) else Color(0xFFD1D5DB)

    // 🔥 \n কে স্বয়ংক্রিয়ভাবে আসল লাইনে রূপান্তর
    val cleanChangelog = updateInfo.changelog
        .replace("\\n", "\n")
        .replace("\\r", "")
        .trim()

    AlertDialog(
        onDismissRequest = {
            if (!updateInfo.forceUpdate) {
                onDismiss()
            }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = dialogBg,
        icon = {
            Box(
                modifier = Modifier
                    .size(54.dp)
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
        },
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Update Available",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = mainTextColor
                )
                if (updateInfo.versionName.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = accentColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "Version ${updateInfo.versionName}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = accentColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (cleanChangelog.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Icon(
                            imageVector = TablerIcons.Flame,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "What's New:",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = mainTextColor
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = changelogBg,
                        border = BorderStroke(1.dp, borderColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = cleanChangelog,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = subTextColor,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                } else {
                    Text(
                        text = "A new version of Lytrix Player is ready to install with performance improvements and bug fixes.",
                        fontSize = 14.sp,
                        color = subTextColor
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    AppUpdateManager.openDownloadUrl(context, updateInfo.downloadUrl)
                    if (!updateInfo.forceUpdate) {
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.padding(end = 4.dp)
            ) {
                Icon(
                    imageVector = TablerIcons.Download,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Update Now",
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            if (!updateInfo.forceUpdate) {
                TextButton(
                    onClick = {
                        AppUpdateManager.dismissVersion(context, updateInfo.versionCode)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Later",
                        color = subTextColor
                    )
                }
            }
        }
    )
}
