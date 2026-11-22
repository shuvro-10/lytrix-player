package com.musicplayer.lytrix.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.*
import com.musicplayer.lytrix.listentogether.metro.MetroClient
import com.musicplayer.lytrix.listentogether.metro.MetroLogEntry
import com.musicplayer.lytrix.listentogether.metro.MetroRoomRole

data class MetroServerPreset(
    val name: String,
    val location: String,
    val url: String
)

val PRESET_SERVERS = listOf(
    MetroServerPreset("Lytrix Cloud Sync", "Singapore (Primary)", MetroClient.DEFAULT_SERVER_URL)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetroSettingsScreen(
    viewModel: PlayerViewModel,
    accentColor: Color,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val metro = viewModel.metroManager
    val clipboardManager = LocalClipboardManager.current

    val serverUrl by metro.serverUrl.collectAsState()
    val username by metro.username.collectAsState()
    val autoApproveJoins by metro.autoApproveJoins.collectAsState()
    val autoApproveSuggestions by metro.autoApproveSuggestions.collectAsState()
    val syncHostVolume by metro.syncHostVolume.collectAsState()
    val blockedUsers by metro.blockedUsers.collectAsState()
    val logs by metro.logs.collectAsState()
    val role by metro.roomRole.collectAsState()

    var showServerUrlDialog by rememberSaveable { mutableStateOf(false) }
    var showUsernameDialog by rememberSaveable { mutableStateOf(false) }
    var showBlockedUsersDialog by rememberSaveable { mutableStateOf(false) }
    var showLogsDialog by rememberSaveable { mutableStateOf(false) }

    // Dialog 1: Server Chooser Dialog
    if (showServerUrlDialog) {
        var customUrlInput by remember { mutableStateOf(serverUrl) }
        var isCustomSelected by remember {
            mutableStateOf(PRESET_SERVERS.none { it.url.equals(serverUrl, ignoreCase = true) })
        }

        AlertDialog(
            onDismissRequest = { showServerUrlDialog = false },
            containerColor = MeldCardColor,
            title = {
                Text("Select Server", color = MeldTextColor, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PRESET_SERVERS.forEach { preset ->
                        val isSelected = !isCustomSelected && serverUrl.equals(preset.url, ignoreCase = true)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) accentColor.copy(alpha = 0.15f) else Color.Transparent)
                                .clickable {
                                    isCustomSelected = false
                                    metro.setServerUrl(preset.url)
                                    showServerUrlDialog = false
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    isCustomSelected = false
                                    metro.setServerUrl(preset.url)
                                    showServerUrlDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = accentColor)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(preset.name, color = MeldTextColor, fontWeight = FontWeight.SemiBold)
                                Text(preset.location, color = MeldTextColor.copy(alpha = 0.6f), fontSize = 12.sp)
                            }
                        }
                    }

                    // Custom URL Option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isCustomSelected) accentColor.copy(alpha = 0.15f) else Color.Transparent)
                            .clickable { isCustomSelected = true }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isCustomSelected,
                            onClick = { isCustomSelected = true },
                            colors = RadioButtonDefaults.colors(selectedColor = accentColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Custom Server URL", color = MeldTextColor, fontWeight = FontWeight.SemiBold)
                    }

                    if (isCustomSelected) {
                        OutlinedTextField(
                            value = customUrlInput,
                            onValueChange = { customUrlInput = it },
                            placeholder = { Text("wss://example.com/ws", color = MeldTextColor.copy(alpha = 0.4f)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MeldTextColor,
                                unfocusedTextColor = MeldTextColor,
                                focusedBorderColor = accentColor,
                                unfocusedBorderColor = Color.Gray.copy(alpha = 0.4f)
                            )
                        )
                    }
                }
            },
            confirmButton = {
                if (isCustomSelected) {
                    TextButton(onClick = {
                        val trimmed = customUrlInput.trim()
                        if (trimmed.isNotBlank()) {
                            metro.setServerUrl(trimmed)
                            showServerUrlDialog = false
                        }
                    }) {
                        Text("Save", color = accentColor, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showServerUrlDialog = false }) {
                    Text("Cancel", color = MeldTextColor.copy(alpha = 0.7f))
                }
            }
        )
    }

    // Dialog 2: Username Dialog
    if (showUsernameDialog) {
        var tempName by remember { mutableStateOf(username) }
        AlertDialog(
            onDismissRequest = { showUsernameDialog = false },
            containerColor = MeldCardColor,
            title = { Text("Set Username", color = MeldTextColor, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = tempName,
                    onValueChange = { tempName = it },
                    placeholder = { Text("Enter your name", color = MeldTextColor.copy(alpha = 0.4f)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MeldTextColor,
                        unfocusedTextColor = MeldTextColor,
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = Color.Gray.copy(alpha = 0.4f)
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = tempName.trim()
                    if (trimmed.isNotBlank()) {
                        metro.setUsername(trimmed)
                        showUsernameDialog = false
                    }
                }) {
                    Text("Save", color = accentColor, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUsernameDialog = false }) {
                    Text("Cancel", color = MeldTextColor.copy(alpha = 0.7f))
                }
            }
        )
    }

    // Dialog 3: Blocked Users Dialog
    if (showBlockedUsersDialog) {
        AlertDialog(
            onDismissRequest = { showBlockedUsersDialog = false },
            containerColor = MeldCardColor,
            title = { Text("Blocked Users", color = MeldTextColor, fontWeight = FontWeight.Bold) },
            text = {
                if (blockedUsers.isEmpty()) {
                    Text("No blocked users.", color = MeldTextColor.copy(alpha = 0.7f))
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(blockedUsers.toList()) { blockedName ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(blockedName, color = MeldTextColor, fontSize = 15.sp)
                                TextButton(onClick = { metro.unblockUser(blockedName) }) {
                                    Text("Unblock", color = accentColor, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBlockedUsersDialog = false }) {
                    Text("Done", color = accentColor)
                }
            }
        )
    }

    // Dialog 4: Connection Logs Dialog
    if (showLogsDialog) {
        AlertDialog(
            onDismissRequest = { showLogsDialog = false },
            containerColor = MeldCardColor,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Connection Logs", color = MeldTextColor, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Row {
                        IconButton(onClick = {
                            val text = logs.joinToString("\n") { "[${it.level}] ${it.message}" }
                            clipboardManager.setText(AnnotatedString(text))
                            Toast.makeText(context, "Logs copied", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(TablerIcons.Copy, contentDescription = "Copy", tint = MeldTextColor)
                        }
                        IconButton(onClick = { metro.clearLogs() }) {
                            Icon(TablerIcons.Trash, contentDescription = "Clear", tint = Color.Red.copy(alpha = 0.8f))
                        }
                    }
                }
            },
            text = {
                if (logs.isEmpty()) {
                    Text("No logs recorded yet.", color = MeldTextColor.copy(alpha = 0.6f))
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 350.dp)) {
                        items(logs.reversed()) { log ->
                            val color = when (log.level) {
                                "ERROR" -> Color(0xFFEF4444)
                                "WARN" -> Color(0xFFF59E0B)
                                else -> MeldTextColor.copy(alpha = 0.85f)
                            }
                            Text(
                                text = "[${log.level}] ${log.message}",
                                color = color,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLogsDialog = false }) {
                    Text("Close", color = accentColor)
                }
            }
        )
    }

    // Main Scaffold Column
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MeldBgColor)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(TablerIcons.ArrowLeft, contentDescription = "Back", tint = MeldTextColor)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Listen Together Settings",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MeldTextColor
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: Server & Account
            Text(
                "SERVER & PROFILE",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MeldTextColor.copy(alpha = 0.5f),
                letterSpacing = 1.sp
            )

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MeldCardColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    // Server Item
                    val currentPreset = PRESET_SERVERS.find { it.url.equals(serverUrl, ignoreCase = true) }
                    SettingsRow(
                        icon = TablerIcons.Cloud,
                        title = "Server URL",
                        subtitle = currentPreset?.name ?: serverUrl,
                        onClick = { showServerUrlDialog = true }
                    )

                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                    // Username Item
                    SettingsRow(
                        icon = TablerIcons.User,
                        title = "Username",
                        subtitle = if (username.isNotBlank()) username else "Not set",
                        onClick = {
                            if (role == MetroRoomRole.NONE) {
                                showUsernameDialog = true
                            } else {
                                Toast.makeText(context, "Cannot edit username while in a room", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }

            // Section 2: Room Preferences
            Text(
                "ROOM PREFERENCES",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MeldTextColor.copy(alpha = 0.5f),
                letterSpacing = 1.sp
            )

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MeldCardColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    // Auto-approve joins
                    SettingsToggleRow(
                        icon = TablerIcons.UserCheck,
                        title = "Auto-approve joins",
                        subtitle = "Automatically accept join requests from listeners",
                        checked = autoApproveJoins,
                        onCheckedChange = { metro.setAutoApproveJoins(it) }
                    )

                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                    // Auto-approve suggestions
                    SettingsToggleRow(
                        icon = TablerIcons.Music,
                        title = "Auto-approve song suggestions",
                        subtitle = "Automatically add tracks proposed by friends to queue",
                        checked = autoApproveSuggestions,
                        onCheckedChange = { metro.setAutoApproveSuggestions(it) }
                    )

                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                    // Sync volume
                    SettingsToggleRow(
                        icon = TablerIcons.Volume,
                        title = "Sync host volume",
                        subtitle = "Synchronize playback volume with the room host",
                        checked = syncHostVolume,
                        onCheckedChange = { metro.setSyncHostVolume(it) }
                    )
                }
            }

            // Section 3: Moderation & Diagnostics
            Text(
                "MODERATION & DIAGNOSTICS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MeldTextColor.copy(alpha = 0.5f),
                letterSpacing = 1.sp
            )

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MeldCardColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    // Blocked Users
                    SettingsRow(
                        icon = TablerIcons.ShieldLock,
                        title = "Blocked Users",
                        subtitle = if (blockedUsers.isNotEmpty()) "${blockedUsers.size} user(s) blocked" else "None",
                        onClick = { showBlockedUsersDialog = true }
                    )

                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                    // Connection Logs
                    SettingsRow(
                        icon = TablerIcons.Terminal,
                        title = "Connection Logs",
                        subtitle = "View diagnostic protocol logs",
                        onClick = { showLogsDialog = true }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MeldTextColor.copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = MeldTextColor, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(subtitle, color = MeldTextColor.copy(alpha = 0.55f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(TablerIcons.ChevronRight, contentDescription = null, tint = MeldTextColor.copy(alpha = 0.35f), modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SettingsToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MeldTextColor.copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = MeldTextColor, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(subtitle, color = MeldTextColor.copy(alpha = 0.55f), fontSize = 12.sp, lineHeight = 16.sp)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF10B981)
            )
        )
    }
}
