package com.musicplayer.lytrix.ui

import compose.icons.tablericons.*

import android.app.Activity
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NoPhotography
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicplayer.lytrix.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySettingsScreen(
    viewModel: PlayerViewModel? = null,
    onNavigateBack: () -> Unit,
    onNavigateToNotInterested: () -> Unit = {}
) {
    BackHandler { onNavigateBack() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val privacyManager = remember { PrivacyPreferencesManager(context) }

    val pauseListenHistory by privacyManager.pauseListenHistory.collectAsState(
        initial = PrivacyPreferencesManager.isPauseListenHistory(context)
    )
    val pauseSearchHistory by privacyManager.pauseSearchHistory.collectAsState(
        initial = PrivacyPreferencesManager.isPauseSearchHistory(context)
    )
    val disableScreenshot by privacyManager.disableScreenshot.collectAsState(
        initial = PrivacyPreferencesManager.isDisableScreenshot(context)
    )
    val crashReportingEnabled by privacyManager.crashReportingEnabled.collectAsState(
        initial = PrivacyPreferencesManager.isCrashReportingEnabled(context)
    )

    var showClearListenDialog by remember { mutableStateOf(false) }
    var showClearSearchDialog by remember { mutableStateOf(false) }

    val appBg = Color(0xFF070504)
    val cardBg = Color(0xFF161210)
    val iconBg = Color(0xFF2A1F1B)
    val iconTint = Color(0xFFD6B5A6)
    val textColor = Color(0xFFF7EBE5)
    val headerColor = Color(0xFFD6B5A6)
    val dialogRed = Color(0xFFE57373)

    // ── Clear Listen History Confirmation Dialog ──────────────────────────────
    if (showClearListenDialog) {
        AlertDialog(
            onDismissRequest = { showClearListenDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.clear_listen_history),
                    color = textColor,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.clear_online_listen_history_confirm),
                    color = textColor.copy(alpha = 0.75f)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel?.clearOnlineListenHistory(context)
                        Toast.makeText(context, "Online listen history cleared", Toast.LENGTH_SHORT).show()
                        showClearListenDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = dialogRed)
                ) {
                    Text(text = stringResource(R.string.delete), color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearListenDialog = false }) {
                    Text(text = stringResource(R.string.cancel), color = textColor)
                }
            },
            containerColor = cardBg,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // ── Clear Search History Confirmation Dialog ──────────────────────────────
    if (showClearSearchDialog) {
        AlertDialog(
            onDismissRequest = { showClearSearchDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.clear_search_history),
                    color = textColor,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.clear_search_history_confirm),
                    color = textColor.copy(alpha = 0.75f)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel?.clearSearchHistory(context)
                        Toast.makeText(context, "Search history cleared", Toast.LENGTH_SHORT).show()
                        showClearSearchDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = dialogRed)
                ) {
                    Text(text = stringResource(R.string.delete), color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearSearchDialog = false }) {
                    Text(text = stringResource(R.string.cancel), color = textColor)
                }
            },
            containerColor = cardBg,
            shape = RoundedCornerShape(20.dp)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.privacy),
                        color = textColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = textColor
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = appBg)
            )
        },
        containerColor = appBg
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ── Group 1: Listen History ───────────────────────────────────────
            item {
                Text(
                    text = stringResource(R.string.listen_history),
                    color = headerColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 4.dp)
                )
            }
            item {
                OnlineSettingsCardGroup(cardBg) {
                    PrivacySwitchItem(
                        icon = Icons.Rounded.History,
                        title = stringResource(R.string.pause_listen_history),
                        checked = pauseListenHistory,
                        onCheckedChange = { checked ->
                            scope.launch { privacyManager.setPauseListenHistory(checked) }
                        },
                        iconBg = iconBg,
                        iconTint = iconTint,
                        textColor = textColor
                    )
                    HorizontalDivider(
                        color = Color.White.copy(alpha = 0.05f),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    PrivacyClickableItem(
                        icon = Icons.Rounded.Delete,
                        title = stringResource(R.string.clear_listen_history),
                        onClick = { showClearListenDialog = true },
                        iconBg = iconBg,
                        iconTint = iconTint,
                        textColor = textColor
                    )
                    HorizontalDivider(
                        color = Color.White.copy(alpha = 0.05f),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    val hiddenCount = viewModel?.notInterestedSongs?.collectAsState()?.value?.size ?: 0
                    PrivacyClickableItem(
                        icon = compose.icons.TablerIcons.EyeOff,
                        title = "Not interested songs",
                        subtitle = if (hiddenCount > 0) "$hiddenCount songs hidden" else "No songs hidden",
                        onClick = onNavigateToNotInterested,
                        iconBg = iconBg,
                        iconTint = iconTint,
                        textColor = textColor
                    )
                }
            }

            // ── Group 2: Search History ───────────────────────────────────────
            item {
                Text(
                    text = stringResource(R.string.search_history),
                    color = headerColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 4.dp)
                )
            }
            item {
                OnlineSettingsCardGroup(cardBg) {
                    PrivacySwitchItem(
                        icon = Icons.Rounded.Search,
                        title = stringResource(R.string.pause_search_history),
                        checked = pauseSearchHistory,
                        onCheckedChange = { checked ->
                            scope.launch { privacyManager.setPauseSearchHistory(checked) }
                        },
                        iconBg = iconBg,
                        iconTint = iconTint,
                        textColor = textColor
                    )
                    HorizontalDivider(
                        color = Color.White.copy(alpha = 0.05f),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    PrivacyClickableItem(
                        icon = Icons.Rounded.Delete,
                        title = stringResource(R.string.clear_search_history),
                        onClick = { showClearSearchDialog = true },
                        iconBg = iconBg,
                        iconTint = iconTint,
                        textColor = textColor
                    )
                }
            }

            // ── Group 3: Misc ─────────────────────────────────────────────────
            item {
                Text(
                    text = stringResource(R.string.misc),
                    color = headerColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 4.dp)
                )
            }
            item {
                OnlineSettingsCardGroup(cardBg) {
                    PrivacySwitchItem(
                        icon = Icons.Rounded.NoPhotography,
                        title = stringResource(R.string.disable_screenshot),
                        description = stringResource(R.string.disable_screenshot_desc),
                        checked = disableScreenshot,
                        onCheckedChange = { checked ->
                            scope.launch {
                                privacyManager.setDisableScreenshot(checked)
                                val act = context as? Activity
                                if (checked) {
                                    act?.window?.setFlags(
                                        WindowManager.LayoutParams.FLAG_SECURE,
                                        WindowManager.LayoutParams.FLAG_SECURE
                                    )
                                } else {
                                    act?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                                }
                            }
                        },
                        iconBg = iconBg,
                        iconTint = iconTint,
                        textColor = textColor
                    )
                    HorizontalDivider(
                        color = Color.White.copy(alpha = 0.05f),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    PrivacySwitchItem(
                        icon = Icons.Rounded.BugReport,
                        title = stringResource(R.string.crash_reporting),
                        description = stringResource(R.string.crash_reporting_desc),
                        checked = crashReportingEnabled,
                        onCheckedChange = { checked ->
                            scope.launch { privacyManager.setCrashReportingEnabled(checked) }
                        },
                        iconBg = iconBg,
                        iconTint = iconTint,
                        textColor = textColor
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(40.dp)) }
        }
    }
}

@Composable
private fun PrivacySwitchItem(
    icon: ImageVector,
    title: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    iconBg: Color,
    iconTint: Color,
    textColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = textColor.copy(alpha = 0.55f),
                    lineHeight = 16.sp
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFFFFCDD2),
                checkedTrackColor = Color(0xFFD32F2F)
            )
        )
    }
}

@Composable
private fun PrivacyClickableItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    iconBg: Color,
    iconTint: Color,
    textColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = textColor.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (subtitle != null) {
            Icon(
                imageVector = compose.icons.TablerIcons.ChevronRight,
                contentDescription = null,
                tint = textColor.copy(alpha = 0.35f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
