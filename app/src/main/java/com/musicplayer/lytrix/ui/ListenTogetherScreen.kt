package com.musicplayer.lytrix.ui
import com.musicplayer.lytrix.ui.component.AmbientThemeGlow

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.*
import kotlinx.coroutines.delay
import androidx.activity.compose.BackHandler
import android.Manifest
import coil.compose.AsyncImage
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

// 🎨 Theme Colors: Clean Material 3 Dark (Matching main Songs/Downloads tab)
private val DeepBlackBg = Color(0xFF0F0F0F)
private val DarkCardBg = Color(0xFF181818)
private val DarkElevatedBg = Color(0xFF222222)
private val DarkBorderColor = Color.White.copy(alpha = 0.08f)

// 🔥 মেসেজের ভেতরে রুমের সদস্যদের "@নাম" খুঁজে হাইলাইট করে
private fun isMentionNameChar(c: Char): Boolean =
    c.isLetterOrDigit() ||
            c == '_' ||
            c.category == CharCategory.NON_SPACING_MARK ||
            c.category == CharCategory.COMBINING_SPACING_MARK

private fun buildMessageWithMentions(
    message: String,
    memberNames: Collection<String>,
    mentionColor: Color,
    mentionBackground: Color
): AnnotatedString {
    val names = memberNames.filter { it.isNotBlank() }
    if (names.isEmpty() || !message.contains('@')) return AnnotatedString(message)

    return buildAnnotatedString {
        var written = 0
        var searchFrom = 0
        while (true) {
            val at = message.indexOf('@', searchFrom)
            if (at == -1) break
            searchFrom = at + 1

            if (at > 0 && isMentionNameChar(message[at - 1])) continue

            var best: String? = null
            for (name in names) {
                val end = at + 1 + name.length
                if (end > message.length) continue
                if (!message.regionMatches(at + 1, name, 0, name.length, ignoreCase = true)) continue
                if (end < message.length && isMentionNameChar(message[end])) continue
                if (best == null || name.length > best.length) best = name
            }

            val matched = best
            if (matched != null) {
                val mentionEnd = at + 1 + matched.length
                append(message.substring(written, at))
                withStyle(
                    SpanStyle(
                        color = mentionColor,
                        fontWeight = FontWeight.Bold,
                        background = mentionBackground
                    )
                ) {
                    append(message.substring(at, mentionEnd))
                }
                written = mentionEnd
                searchFrom = mentionEnd
            }
        }
        append(message.substring(written))
    }
}

private fun formatVoiceTime(sec: Int): String = "%d:%02d".format(sec / 60, sec % 60)

fun replyPreviewText(m: com.musicplayer.lytrix.model.ChatMessage): String {
    if (m.type == "voice") return "🎤 Voice message (${formatVoiceTime(m.audioDuration)})"
    val t = m.message.replace("\n", " ").trim()
    if (t.length <= 60) return t
    var cut = t.substring(0, 60)
    if (cut.isNotEmpty() && Character.isHighSurrogate(cut.last())) cut = cut.dropLast(1)
    return cut + "…"
}

@Composable
private fun VoiceBubble(
    isPlaying: Boolean,
    isLoading: Boolean,
    durationSec: Int,
    isMe: Boolean,
    onClick: () -> Unit
) {
    val fg = if (isMe) Color.Black else Color.White
    val barHeights = listOf(8, 14, 10, 18, 12, 20, 9, 16, 11, 7, 15, 10)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(38.dp).clip(CircleShape).background(fg.copy(alpha = 0.15f)).clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = fg, strokeWidth = 2.dp)
            } else {
                Icon(
                    if (isPlaying) TablerIcons.PlayerPause else TablerIcons.PlayerPlay,
                    contentDescription = null, tint = fg, modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            barHeights.forEach { h ->
                Box(
                    modifier = Modifier.padding(horizontal = 1.dp).width(3.dp).height(h.dp)
                        .clip(RoundedCornerShape(2.dp)).background(fg.copy(alpha = if (isPlaying) 0.9f else 0.5f))
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(formatVoiceTime(durationSec), color = fg.copy(alpha = 0.8f), fontSize = 12.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListenTogetherScreen(
    viewModel: PlayerViewModel,
    accentColor: Color,
    onNavigateBack: () -> Unit,
    onExitRoom: () -> Unit = onNavigateBack
) {
    val context = LocalContext.current
    val currentRoom by viewModel.currentListenRoom.collectAsState()
    val myStatus by viewModel.myRoomStatus.collectAsState()
    val isLoading by viewModel.isRoomLoading.collectAsState()
    val isNetworkConnected by viewModel.isNetworkConnected.collectAsState()
    val pendingCode by viewModel.pendingRoomCode.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()

    // 🌟 VIP Features State
    val roomTitle by viewModel.roomTitle.collectAsState()
    val pinnedNotice by viewModel.pinnedNotice.collectAsState()
    val isRoomLocked by viewModel.isRoomLocked.collectAsState()
    val activePoll by viewModel.activePoll.collectAsState()
    val currentRatings by viewModel.currentRatings.collectAsState()

    var username by remember { mutableStateOf(viewModel.getSavedUserName(context)) }
    var joinCode by remember { mutableStateOf(pendingCode) }
    val clipboardManager = LocalClipboardManager.current

    var selectedTab by remember { mutableStateOf("members") }
    var chatValue by remember { mutableStateOf(TextFieldValue("")) }
    val chatMessage = chatValue.text
    var cooldownSeconds by remember { mutableStateOf(0) }
    val myDeviceId = viewModel.getDeviceId(context)

    // Dialogs
    var showWarningDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showPollDialog by remember { mutableStateOf(false) }

    var editTitleText by remember(roomTitle) { mutableStateOf(roomTitle) }
    var editNoticeText by remember(pinnedNotice) { mutableStateOf(pinnedNotice) }

    val fbAutoApproveJoins by viewModel.fbAutoApproveJoins.collectAsState()
    val fbAutoApproveSuggestions by viewModel.fbAutoApproveSuggestions.collectAsState()
    val fbSuggestions by viewModel.fbSuggestions.collectAsState()

    // Voice Message
    val voiceState by viewModel.voiceState.collectAsState()
    val voiceSeconds by viewModel.voiceSeconds.collectAsState()
    val playingVoiceId by viewModel.playingVoiceId.collectAsState()
    val loadingVoiceId by viewModel.loadingVoiceId.collectAsState()
    val voiceMax by viewModel.voiceMaxSeconds.collectAsState()

    var replyingTo by remember { mutableStateOf<com.musicplayer.lytrix.model.ChatMessage?>(null) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val replyThresholdPx = with(density) { 56.dp.toPx() }
    val maxSwipePx = with(density) { 90.dp.toPx() }

    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.startVoiceRecording(context)
        else android.widget.Toast.makeText(context, "Microphone permission is needed for voice messages", android.widget.Toast.LENGTH_SHORT).show()
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.cancelVoiceRecording()
            viewModel.stopVoicePlayback()
        }
    }

    // @ Mention logic
    val cursorPos = chatValue.selection.start.coerceIn(0, chatValue.text.length)
    val textBeforeCursor = chatValue.text.substring(0, cursorPos)
    val atIndex = textBeforeCursor.lastIndexOf('@')
    val mentionQuery: String? =
        if (atIndex >= 0 && (atIndex == 0 || textBeforeCursor[atIndex - 1].isWhitespace())) {
            val q = textBeforeCursor.substring(atIndex + 1)
            if (q.contains('\n') || q.length > 30) null else q
        } else null

    val mentionSuggestions = if (mentionQuery != null) {
        val candidates = currentRoom?.members?.entries?.filter {
            it.key != myDeviceId && it.value.status != "pending" && it.value.name.isNotBlank()
        } ?: emptyList()

        val alreadyCompleted = mentionQuery.endsWith(" ") &&
                candidates.any { it.value.name.equals(mentionQuery.trimEnd(), ignoreCase = true) }

        if (alreadyCompleted) {
            emptyList()
        } else {
            candidates
                .filter { it.value.name.contains(mentionQuery, ignoreCase = true) }
                .sortedByDescending { it.value.name.startsWith(mentionQuery, ignoreCase = true) }
        }
    } else emptyList()

    LaunchedEffect(Unit) {
        val clipData = clipboardManager.getText()?.text
        if (!clipData.isNullOrBlank() && clipData.length == 8 && clipData.all { it.isLetterOrDigit() }) {
            joinCode = clipData.uppercase()
        }
    }

    LaunchedEffect(cooldownSeconds) {
        if (cooldownSeconds > 0) { delay(1000); cooldownSeconds-- }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(1500, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = ""
    )

    BackHandler {
        onNavigateBack()
    }

    // 💡 Tips & Info Dialog
    if (showWarningDialog) {
        AlertDialog(
            onDismissRequest = { showWarningDialog = false },
            containerColor = DarkCardBg,
            title = {
                Text(
                    text = "Tips & Troubleshooting",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = accentColor)) { append("• Small Delay Between Phones:\n") }
                            append("A gap of 1-2 seconds is normal because of internet latency. The Host or Co-host can Pause and Play again to sync up.\n\n")

                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = accentColor)) { append("• Star Ratings:\n") }
                            append("Everyone in the room can rate the current song 1-5 stars. When the host changes the song, ratings automatically reset for the new track!\n\n")

                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = accentColor)) { append("• Live Polls:\n") }
                            append("Tap the poll icon (🗳️) next to the chat bar to launch a question and vote in real-time with live progress bars.\n\n")

                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = accentColor)) { append("• Lock Room (Private):\n") }
                            append("Host can lock the room in settings to stop anyone else from joining.\n\n")

                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = accentColor)) { append("• 10-Minute Reconnect Window:\n") }
                            append("If you close or swipe away the app, your room stays active for up to 5 minutes. Opening the app will automatically reconnect you!")
                        },
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showWarningDialog = false }) {
                    Text("Got it", color = accentColor, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // ⚙️ Room Settings Dialog (Host & Co-Host)
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            containerColor = DarkCardBg,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(TablerIcons.Settings, contentDescription = null, tint = accentColor, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Room Preferences",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 🏷️ Custom Room Title (max 25 chars)
                    Column {
                        Text("Custom Room Title (Max 25)", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        TextField(
                            value = editTitleText,
                            onValueChange = { if (it.length <= 25) editTitleText = it },
                            placeholder = { Text("e.g. Midnight Beats", color = Color.Gray, fontSize = 13.sp) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = DarkElevatedBg,
                                unfocusedContainerColor = DarkElevatedBg,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                Text(
                                    "${editTitleText.length}/25",
                                    color = Color.Gray,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            }
                        )
                        if (editTitleText.trim() != roomTitle) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = {
                                    viewModel.setRoomTitle(editTitleText)
                                    android.widget.Toast.makeText(context, "Room title updated", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("Save Title", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    HorizontalDivider(color = DarkBorderColor)

                    // 🔒 Lock Room (Host Only)
                    if (myStatus == "host") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Lock Room 🔒", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    if (isRoomLocked) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("(Private)", color = Color(0xFFF59E0B), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Text("Reject all new join requests", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
                            }
                            Switch(
                                checked = isRoomLocked,
                                onCheckedChange = { viewModel.setRoomLocked(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.Black,
                                    checkedTrackColor = accentColor,
                                    uncheckedThumbColor = Color.Gray,
                                    uncheckedTrackColor = Color.DarkGray
                                )
                            )
                        }
                        HorizontalDivider(color = DarkBorderColor)
                    }



                    // 📌 Pinned Announcement
                    Column {
                        Text("Pinned Notice 📌", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        TextField(
                            value = editNoticeText,
                            onValueChange = { if (it.length <= 120) editNoticeText = it },
                            placeholder = { Text("e.g. Welcome friends! Suggest songs below 🎧", color = Color.Gray, fontSize = 13.sp) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = DarkElevatedBg,
                                unfocusedContainerColor = DarkElevatedBg,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    viewModel.setPinnedNotice(editNoticeText)
                                    android.widget.Toast.makeText(context, "Notice pinned", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("Pin Notice", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            if (pinnedNotice.isNotBlank()) {
                                TextButton(
                                    onClick = {
                                        editNoticeText = ""
                                        viewModel.clearPinnedNotice()
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text("Clear", color = Color(0xFFEF4444), fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = DarkBorderColor)

                    // Auto-approve joins
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Auto-approve joins", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Automatically accept new listeners", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
                        }
                        Switch(
                            checked = fbAutoApproveJoins,
                            onCheckedChange = { viewModel.setFbAutoApproveJoins(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = accentColor,
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.DarkGray
                            )
                        )
                    }

                    HorizontalDivider(color = DarkBorderColor)

                    // Auto-approve suggestions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Auto-approve suggestions", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Automatically play suggested songs", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
                        }
                        Switch(
                            checked = fbAutoApproveSuggestions,
                            onCheckedChange = { viewModel.setFbAutoApproveSuggestions(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = accentColor,
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.DarkGray
                            )
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("Done", color = accentColor, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // 🗳️ Create Poll Dialog
    if (showPollDialog) {
        var pollQ by remember { mutableStateOf("") }
        var opt1 by remember { mutableStateOf("") }
        var opt2 by remember { mutableStateOf("") }
        var opt3 by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showPollDialog = false },
            containerColor = DarkCardBg,
            title = {
                Text("Create a Room Poll 🗳️", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextField(
                        value = pollQ,
                        onValueChange = { if (it.length <= 60) pollQ = it },
                        placeholder = { Text("Ask a question (e.g. Next song?)", color = Color.Gray, fontSize = 13.sp) },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = DarkElevatedBg,
                            unfocusedContainerColor = DarkElevatedBg,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextField(
                        value = opt1,
                        onValueChange = { if (it.length <= 30) opt1 = it },
                        placeholder = { Text("Option 1", color = Color.Gray, fontSize = 13.sp) },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = DarkElevatedBg,
                            unfocusedContainerColor = DarkElevatedBg,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextField(
                        value = opt2,
                        onValueChange = { if (it.length <= 30) opt2 = it },
                        placeholder = { Text("Option 2", color = Color.Gray, fontSize = 13.sp) },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = DarkElevatedBg,
                            unfocusedContainerColor = DarkElevatedBg,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextField(
                        value = opt3,
                        onValueChange = { if (it.length <= 30) opt3 = it },
                        placeholder = { Text("Option 3 (optional)", color = Color.Gray, fontSize = 13.sp) },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = DarkElevatedBg,
                            unfocusedContainerColor = DarkElevatedBg,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val opts = listOf(opt1, opt2, opt3).filter { it.isNotBlank() }
                        if (pollQ.isNotBlank() && opts.size >= 2) {
                            viewModel.createPoll(pollQ, opts)
                            showPollDialog = false
                        } else {
                            android.widget.Toast.makeText(context, "Enter a question & at least 2 options", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                ) {
                    Text("Start Poll", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPollDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }

    val darkBgColor = MaterialTheme.colorScheme.background
    val bgUri = com.musicplayer.lytrix.ui.theme.LocalCustomBackgroundUri.current

    // Full Screen Container with Main Interface Ambient Glow (Matching PlayerScreen / Offline Songs tab)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(darkBgColor)
    ) {
        if (bgUri.isNotBlank()) {
            AsyncImage(
                model = bgUri,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
            )
        }

        // Ambient Theme Glow (Main Interface / Offline section top-left ambient glow)
        AmbientThemeGlow(
            tint = accentColor,
            glowHeight = 400.dp,
            intensity = 0.22f
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
        // ── Top Bar ──
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                IconButton(onClick = onNavigateBack, modifier = Modifier.padding(end = 4.dp).offset(x = (-8).dp)) {
                    Icon(TablerIcons.ArrowLeft, contentDescription = "Back", tint = Color.White)
                }
                if (myStatus == "disconnected") {
                    Icon(TablerIcons.Users, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Listen Together", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                } else {
                    Column(modifier = Modifier.weight(1f)) {
                        val displayTitle = if (roomTitle.isNotBlank()) roomTitle.take(25) else "VIP Lounge"
                        Text(
                            displayTitle,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "#${currentRoom?.roomCode ?: "------"}",
                            fontSize = 12.sp,
                            color = accentColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (isRoomLocked && myStatus != "disconnected") {
                    IconButton(
                        onClick = {
                            android.widget.Toast.makeText(context, "This room is locked", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            TablerIcons.Lock,
                            contentDescription = "Room Locked",
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                if (myStatus == "host" || myStatus == "co_host") {
                    IconButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            TablerIcons.Settings,
                            contentDescription = "Room Settings",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                if (myStatus != "disconnected") {
                    IconButton(
                        onClick = { showWarningDialog = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            TablerIcons.InfoCircle,
                            contentDescription = "Info & Tips",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {

            // Tab Selector (Lounge / Live Chat)
            if (myStatus == "approved" || myStatus == "host" || myStatus == "co_host") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp).clip(RoundedCornerShape(16.dp)).background(DarkCardBg).padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                            .background(if (selectedTab == "members") Color.White.copy(alpha = 0.15f) else Color.Transparent)
                            .clickable { selectedTab = "members" }.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("👥 Lounge", color = if (selectedTab == "members") Color.White else Color.Gray, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Box(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                            .background(if (selectedTab == "chat") Color.White.copy(alpha = 0.15f) else Color.Transparent)
                            .clickable { selectedTab = "chat" }.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("💬 Live Chat", color = if (selectedTab == "chat") Color.White else Color.Gray, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            } else if (myStatus == "pending") {
                selectedTab = "members"
            }

            Column(modifier = Modifier.weight(1f)) {
                if (myStatus == "disconnected") {
                    // Disconnected Screen
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(DarkCardBg).padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color.Gray.copy(alpha = 0.5f)))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Disconnected", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Text("Listen to music with friends in real time. Create a VIP room or join an existing room with a code.", color = Color.White.copy(alpha=0.6f), fontSize = 14.sp, lineHeight = 20.sp)
                        Spacer(modifier = Modifier.height(24.dp))

                        val textFieldColors = TextFieldDefaults.colors(
                            focusedContainerColor = DarkCardBg, unfocusedContainerColor = DarkCardBg,
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            cursorColor = accentColor, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
                        )
                        TextField(value = username, onValueChange = { username = it }, placeholder = { Text("Username", color = Color.Gray) }, leadingIcon = { Icon(TablerIcons.User, null, tint = Color.Gray) }, singleLine = true, shape = RoundedCornerShape(14.dp), colors = textFieldColors, modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(20.dp))
                        Text("Join existing room", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        TextField(value = joinCode, onValueChange = { joinCode = it.uppercase() }, placeholder = { Text("Room code (e.g. F51KX2Q2)", color = Color.Gray) }, leadingIcon = { Icon(TablerIcons.Box, null, tint = Color.Gray) }, singleLine = true, shape = RoundedCornerShape(14.dp), colors = textFieldColors, modifier = Modifier.fillMaxWidth())
                    }
                } else {
                    if (selectedTab == "members") {
                        // ── Tab 1: Lounge / Members ──
                        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            // Room Code & Status Card
                            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(DarkCardBg).padding(20.dp)) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                                        val statusColor = if (!isNetworkConnected) Color(0xFFEF4444) else if(myStatus == "pending") Color(0xFFF59E0B) else Color(0xFF10B981)
                                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(statusColor.copy(alpha = if(!isNetworkConnected) 1f else dotAlpha)))
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(if(!isNetworkConnected) "Disconnected" else if(myStatus == "pending") "Waiting for host approval..." else "Connected", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    }
                                    if (!isNetworkConnected) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Button(onClick = { viewModel.reconnectToRoom(context) }, colors = ButtonDefaults.buttonColors(containerColor = accentColor), shape = RoundedCornerShape(50), modifier = Modifier.height(34.dp)) {
                                            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black, strokeWidth = 2.dp)
                                            else Text("Reconnect", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(18.dp))
                                    Text("Room Code", color = Color.White.copy(alpha=0.5f), fontSize = 12.sp)
                                    Text(currentRoom?.roomCode ?: "------", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 6.sp, color = Color.White, modifier = Modifier.padding(vertical = 4.dp))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        OutlinedButton(
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString(currentRoom?.roomCode ?: ""))
                                                android.widget.Toast.makeText(context, "Copied room code", android.widget.Toast.LENGTH_SHORT).show()
                                            },
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                                            shape = RoundedCornerShape(50),
                                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                                        ) {
                                            Icon(TablerIcons.Copy, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Copy", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                                        }

                                        Button(
                                            onClick = {
                                                val code = currentRoom?.roomCode ?: return@Button
                                                val roomUrl = "https://lytrixteam.blogspot.com/?code=$code&type=firebase"
                                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(
                                                        android.content.Intent.EXTRA_TEXT,
                                                        "👑 Join my VIP Lounge session on Lytrix Player! Tap to listen together:\n$roomUrl"
                                                    )
                                                }
                                                context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Room"))
                                            },
                                            shape = RoundedCornerShape(50),
                                            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                                        ) {
                                            Icon(TablerIcons.Share, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Share", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        }
                                    }
                                }
                            }

                            // ⭐ Feature 2: Live Song Star Rating Card
                            val playingSong = currentSong
                            if (playingSong != null && (myStatus == "approved" || myStatus == "host" || myStatus == "co_host")) {
                                val myRating = currentRatings[myDeviceId] ?: 0
                                val totalRatings = currentRatings.size
                                val avgRating = if (totalRatings > 0) currentRatings.values.average() else 0.0

                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                                Text("⭐ Rate Current Song", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                                Text(playingSong.title, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                            if (totalRatings > 0) {
                                                Text(
                                                    String.format("%.1f ★ (%d %s)", avgRating, totalRatings, if (totalRatings == 1) "vote" else "votes"),
                                                    color = Color(0xFFFFD700),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            } else {
                                                Text("Tap to rate", color = Color.Gray, fontSize = 11.sp)
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceEvenly,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            for (star in 1..5) {
                                                val isFilled = star <= myRating
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .clip(CircleShape)
                                                        .background(if (isFilled) Color(0xFFFFD700).copy(alpha = 0.2f) else DarkElevatedBg)
                                                        .clickable(enabled = myRating == 0) { viewModel.rateCurrentSong(star) },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        if (isFilled) "★" else "☆",
                                                        color = if (isFilled) Color(0xFFFFD700) else Color.Gray,
                                                        fontSize = 20.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Song Suggestions Card (if any pending)
                            if ((myStatus == "host" || myStatus == "co_host") && fbSuggestions.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(20.dp))
                                Text("SONG SUGGESTIONS (${fbSuggestions.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.5f), letterSpacing = 1.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    fbSuggestions.forEach { sugg ->
                                        Card(
                                            shape = RoundedCornerShape(14.dp),
                                            colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (sugg.imageUrl.isNotEmpty()) {
                                                    AsyncImage(
                                                        model = sugg.imageUrl,
                                                        contentDescription = null,
                                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
                                                    )
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                }
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(sugg.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    Text("${sugg.artist} • from ${sugg.senderName}", color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    TextButton(onClick = { viewModel.rejectFirebaseSuggestion(sugg.suggestionId) }) {
                                                        Text("Decline", color = Color(0xFFEF4444), fontSize = 12.sp)
                                                    }
                                                    Button(
                                                        onClick = { viewModel.approveFirebaseSuggestion(sugg) },
                                                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                                                        shape = RoundedCornerShape(50),
                                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                                    ) {
                                                        Text("Play", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))
                            Text("Connected Users", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Spacer(modifier = Modifier.height(10.dp))

                            Column(modifier = Modifier.fillMaxWidth()) {
                                currentRoom?.members?.forEach { (memberId, member) ->
                                    if (member.status != "pending" || myStatus == "host" || myStatus == "co_host") {
                                        val isOnline = member.online
                                        val rowAlpha = if (isOnline) 1f else 0.5f
                                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.alpha(rowAlpha)) {
                                                Box(modifier = Modifier.size(46.dp).clip(CircleShape).background(accentColor.copy(alpha=if (isOnline) 0.15f else 0.08f)), contentAlignment = Alignment.Center) {
                                                    Text(member.name.take(1).uppercase(), color = if (isOnline) accentColor else Color.Gray, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                                }
                                                Spacer(modifier = Modifier.width(14.dp))
                                                Column {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(member.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                                        if (!isOnline && member.status != "pending") {
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Text("💤", fontSize = 12.sp)
                                                        }
                                                    }
                                                    val baseRole = when(member.status) { "host" -> "Host 👑"; "co_host" -> "Co-Host 🛡️"; "pending" -> "Pending..."; else -> "Guest" }
                                                    val statusDesc = if (isOnline || member.status == "pending") baseRole else "$baseRole • Away"
                                                    Text(text = statusDesc, color = if (isOnline) Color.White.copy(alpha=0.5f) else Color.White.copy(alpha=0.35f), fontSize = 12.sp)
                                                }
                                            }

                                            if ((myStatus == "host" || myStatus == "co_host") && memberId != myDeviceId) {
                                                Row {
                                                    if (member.status == "pending") {
                                                        IconButton(onClick = { viewModel.approveMember(memberId) }) { Icon(TablerIcons.Check, null, tint = Color(0xFF10B981)) }
                                                        IconButton(onClick = { viewModel.kickMember(memberId) }) { Icon(TablerIcons.X, null, tint = Color(0xFFEF4444)) }
                                                    } else {
                                                        var showMenu by remember { mutableStateOf(false) }
                                                        Box {
                                                            IconButton(onClick = { showMenu = true }) { Icon(TablerIcons.DotsVertical, null, tint = Color.White.copy(alpha=0.7f)) }
                                                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, modifier = Modifier.background(DarkCardBg)) {
                                                                if (myStatus == "host") {
                                                                    if (member.status != "co_host") DropdownMenuItem(text = { Text("Make Co-Host", color = Color.White) }, onClick = { viewModel.makeCoHost(memberId); showMenu = false })
                                                                    else DropdownMenuItem(text = { Text("Demote to Guest", color = Color.White) }, onClick = { viewModel.demoteToGuest(memberId); showMenu = false })
                                                                    DropdownMenuItem(text = { Text("Transfer Host", color = Color.White) }, onClick = { viewModel.transferHost(memberId); showMenu = false })
                                                                }
                                                                if (!(myStatus == "co_host" && member.status == "host")) {
                                                                    DropdownMenuItem(text = { Text("Kick User", color = Color(0xFFEF4444)) }, onClick = { viewModel.kickMember(memberId); showMenu = false })
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // ── Tab 2: Live Chat ──
                        Column(modifier = Modifier.fillMaxSize()) {
                            // 📌 Feature 4: Pinned Notice Banner (Sticky atop chat)
                            if (pinnedNotice.isNotBlank()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(DarkCardBg)
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("📌", fontSize = 15.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        pinnedNotice,
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontSize = 12.sp,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (myStatus == "host" || myStatus == "co_host") {
                                        IconButton(onClick = { viewModel.clearPinnedNotice() }, modifier = Modifier.size(24.dp)) {
                                            Icon(TablerIcons.X, contentDescription = "Unpin", tint = Color.Gray, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }

                            // 🗳️ Feature 1: Live Active Poll Card (if any active poll, collapsible to save space)
                            val poll = activePoll
                            if (poll != null && poll.active) {
                                val totalVotes = poll.votes.size
                                val myVotedIndex = poll.votes[myDeviceId]
                                var isPollExpanded by remember(poll.question) { mutableStateOf(myVotedIndex == null) }

                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { isPollExpanded = !isPollExpanded },
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                Text("🗳️", fontSize = 14.sp)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    poll.question,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    maxLines = if (isPollExpanded) 2 else 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Icon(
                                                    if (isPollExpanded) TablerIcons.ChevronUp else TablerIcons.ChevronDown,
                                                    contentDescription = if (isPollExpanded) "Collapse" else "Expand",
                                                    tint = Color.White.copy(alpha = 0.6f),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            if (myStatus == "host" || myStatus == "co_host") {
                                                TextButton(
                                                    onClick = { viewModel.endPoll() },
                                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                                    modifier = Modifier.height(24.dp)
                                                ) {
                                                    Text("End Poll", color = Color(0xFFEF4444), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }

                                        AnimatedVisibility(
                                            visible = isPollExpanded,
                                            enter = expandVertically() + fadeIn(),
                                            exit = shrinkVertically() + fadeOut()
                                        ) {
                                            Column {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    poll.options.forEachIndexed { index, optionText ->
                                                        val optionVotes = poll.votes.values.count { it == index }
                                                        val percent = if (totalVotes > 0) (optionVotes.toFloat() / totalVotes) else 0f
                                                        val isMyVote = myVotedIndex == index

                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .clip(RoundedCornerShape(8.dp))
                                                                .background(DarkElevatedBg)
                                                                .clickable(enabled = myVotedIndex == null) { viewModel.votePoll(index) }
                                                        ) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .fillMaxWidth(fraction = percent.coerceIn(0f, 1f))
                                                                    .matchParentSize()
                                                                    .clip(RoundedCornerShape(8.dp))
                                                                    .background(if (isMyVote) accentColor.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.10f))
                                                            )
                                                            Row(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                                    if (isMyVote) {
                                                                        Text("✓ ", color = accentColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                                    }
                                                                    Text(
                                                                        optionText,
                                                                        color = if (isMyVote) accentColor else Color.White,
                                                                        fontSize = 12.sp,
                                                                        fontWeight = if (isMyVote) FontWeight.Bold else FontWeight.Normal,
                                                                        maxLines = 1,
                                                                        overflow = TextOverflow.Ellipsis
                                                                    )
                                                                }
                                                                Text(
                                                                    "${(percent * 100).roundToInt()}% ($optionVotes)",
                                                                    color = Color.White.copy(alpha = 0.6f),
                                                                    fontSize = 10.sp
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    "Total: $totalVotes ${if (totalVotes == 1) "vote" else "votes"} • by ${poll.creatorName}",
                                                    color = Color.White.copy(alpha = 0.45f),
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            val messagesList = currentRoom?.messages?.values?.sortedBy { it.timestamp } ?: emptyList()
                            val memberNamesForHighlight = currentRoom?.members?.values?.map { it.name } ?: emptyList()
                            val listState = rememberLazyListState()

                            LaunchedEffect(messagesList.size) { if (messagesList.isNotEmpty()) listState.animateScrollToItem(messagesList.size - 1) }

                            LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth()) {
                                if (messagesList.isEmpty()) {
                                    item { Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) { Text("No messages yet. Say hi! 👋", color = Color.Gray) } }
                                }

                                items(messagesList) { msg ->
                                    if (msg.role == "system") {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = msg.message,
                                                color = Color.White.copy(alpha = 0.5f),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    } else {
                                        val isMe = msg.senderId == myDeviceId
                                        val dir = if (isMe) -1f else 1f
                                        val swipeOffset = remember { Animatable(0f) }

                                        Box(modifier = Modifier.fillMaxWidth()) {
                                            if (swipeOffset.value > 8f) {
                                                Icon(
                                                    TablerIcons.ArrowLeft,
                                                    contentDescription = "Reply",
                                                    tint = accentColor.copy(alpha = (swipeOffset.value / replyThresholdPx).coerceIn(0f, 1f)),
                                                    modifier = Modifier
                                                        .align(if (isMe) Alignment.CenterEnd else Alignment.CenterStart)
                                                        .padding(horizontal = 6.dp)
                                                        .size(22.dp)
                                                )
                                            }

                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .offset { IntOffset((dir * swipeOffset.value).roundToInt(), 0) }
                                                    .pointerInput(msg.senderId, msg.timestamp) {
                                                        detectHorizontalDragGestures(
                                                            onDragEnd = {
                                                                if (swipeOffset.value >= replyThresholdPx) replyingTo = msg
                                                                scope.launch { swipeOffset.animateTo(0f) }
                                                            },
                                                            onDragCancel = { scope.launch { swipeOffset.animateTo(0f) } },
                                                            onHorizontalDrag = { change, dragAmount ->
                                                                change.consume()
                                                                scope.launch { swipeOffset.snapTo((swipeOffset.value + dragAmount * dir).coerceIn(0f, maxSwipePx)) }
                                                            }
                                                        )
                                                    }
                                                    .padding(vertical = 3.dp),
                                                horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
                                            ) {
                                                if (!isMe) {
                                                    val roleIcon = when(msg.role) { "host" -> "👑"; "co_host" -> "🛡️"; else -> "" }
                                                    Text("${msg.senderName} $roleIcon", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 2.dp, start = 4.dp))
                                                }
                                                Box(
                                                    modifier = Modifier.clip(RoundedCornerShape(
                                                        topStart = 16.dp, topEnd = 16.dp,
                                                        bottomStart = if (isMe) 16.dp else 4.dp,
                                                        bottomEnd = if (isMe) 4.dp else 16.dp
                                                    )).background(if (isMe) accentColor else DarkCardBg).padding(horizontal = 14.dp, vertical = 9.dp)
                                                ) {
                                                    Column {
                                                        if (msg.replyToId.isNotBlank()) {
                                                            Row(
                                                                modifier = Modifier
                                                                    .padding(bottom = 6.dp)
                                                                    .clip(RoundedCornerShape(8.dp))
                                                                    .background((if (isMe) Color.Black else Color.White).copy(alpha = 0.12f))
                                                                    .clickable {
                                                                        val target = messagesList.indexOfFirst { "${it.senderId}_${it.timestamp}" == msg.replyToId }
                                                                        if (target >= 0) scope.launch { listState.animateScrollToItem(target) }
                                                                        else android.widget.Toast.makeText(context, "Original message is no longer available", android.widget.Toast.LENGTH_SHORT).show()
                                                                    }
                                                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Box(modifier = Modifier.width(3.dp).height(30.dp).clip(RoundedCornerShape(2.dp)).background(if (isMe) Color.Black.copy(alpha = 0.6f) else accentColor))
                                                                Spacer(modifier = Modifier.width(8.dp))
                                                                Column {
                                                                    Text(msg.replyToName, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, color = if (isMe) Color.Black.copy(alpha = 0.8f) else accentColor)
                                                                    Text(msg.replyToText, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = if (isMe) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.7f))
                                                                }
                                                            }
                                                        }

                                                        if (msg.type == "voice") {
                                                            VoiceBubble(
                                                                isPlaying = playingVoiceId == msg.voiceId,
                                                                isLoading = loadingVoiceId == msg.voiceId,
                                                                durationSec = msg.audioDuration,
                                                                isMe = isMe,
                                                                onClick = { viewModel.toggleVoicePlayback(context, msg.voiceId) }
                                                            )
                                                        } else {
                                                            val bubbleText = buildMessageWithMentions(
                                                                message = msg.message,
                                                                memberNames = memberNamesForHighlight,
                                                                mentionColor = if (isMe) Color.Black else accentColor,
                                                                mentionBackground = if (isMe) Color.Black.copy(alpha = 0.15f) else accentColor.copy(alpha = 0.18f)
                                                            )
                                                            Text(bubbleText, color = if (isMe) Color.Black else Color.White, fontSize = 14.sp)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // @ Mentions popup
                            if (mentionSuggestions.isNotEmpty()) {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 160.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(DarkCardBg)
                                ) {
                                    items(mentionSuggestions, key = { it.key }) { entry ->
                                        val suggestedName = entry.value.name
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    val fullText = chatValue.text
                                                    val before = fullText.substring(0, atIndex)
                                                    val after = fullText.substring(cursorPos)
                                                    val inserted = "@$suggestedName "
                                                    chatValue = TextFieldValue(
                                                        text = before + inserted + after,
                                                        selection = TextRange((before + inserted).length)
                                                    )
                                                }
                                                .padding(horizontal = 14.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier.size(34.dp).clip(CircleShape).background(accentColor.copy(alpha = 0.15f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(suggestedName.take(1).uppercase(), color = accentColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(suggestedName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                                Text(
                                                    text = when (entry.value.status) { "host" -> "Host 👑"; "co_host" -> "Co-Host 🛡️"; else -> "Guest" },
                                                    color = Color.Gray,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                            }

                            // Replying To Preview Bar
                            replyingTo?.let { r ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(12.dp)).background(DarkCardBg).padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.width(3.dp).height(30.dp).clip(RoundedCornerShape(2.dp)).background(accentColor))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Replying to ${r.senderName}", color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                        Text(replyPreviewText(r), color = Color.Gray, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    IconButton(onClick = { replyingTo = null }, modifier = Modifier.size(26.dp)) {
                                        Icon(TablerIcons.X, contentDescription = "Cancel reply", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }

                            // Chat Input Bottom Bar
                            when (voiceState) {
                                "idle" -> {
                                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        // 🗳️ Create Poll Action Button (shown when input text is blank)
                                        if (chatMessage.isBlank()) {
                                            Box(
                                                modifier = Modifier
                                                    .size(46.dp)
                                                    .clip(CircleShape)
                                                    .background(DarkElevatedBg)
                                                    .clickable { showPollDialog = true },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("🗳️", fontSize = 17.sp)
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                        }

                                        TextField(
                                            value = chatValue,
                                            onValueChange = { chatValue = it },
                                            placeholder = { Text("Message...", color = Color.Gray, fontSize = 14.sp) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 48.dp, max = 120.dp)
                                                .clip(RoundedCornerShape(24.dp)),
                                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = Color.White),
                                            singleLine = false,
                                            maxLines = 4,
                                            colors = TextFieldDefaults.colors(
                                                focusedContainerColor = DarkElevatedBg,
                                                unfocusedContainerColor = DarkElevatedBg,
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                cursorColor = accentColor,
                                                focusedIndicatorColor = Color.Transparent,
                                                unfocusedIndicatorColor = Color.Transparent
                                            )
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        if (chatMessage.isBlank()) {
                                            // 🎤 Voice Record Button
                                            Box(
                                                modifier = Modifier.size(48.dp).clip(CircleShape)
                                                    .background(if (cooldownSeconds > 0) Color.Gray else accentColor)
                                                    .clickable(enabled = cooldownSeconds == 0) {
                                                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                                            viewModel.startVoiceRecording(context)
                                                        } else {
                                                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                                        }
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (cooldownSeconds > 0) Text("$cooldownSeconds", color = Color.White, fontWeight = FontWeight.Bold)
                                                else Icon(TablerIcons.Microphone, contentDescription = "Record voice", tint = Color.Black)
                                            }
                                        } else {
                                            // Send Text Button
                                            Box(
                                                modifier = Modifier.size(48.dp).clip(CircleShape).background(if (cooldownSeconds > 0) Color.Gray else accentColor).clickable(enabled = cooldownSeconds == 0) {
                                                    viewModel.sendChatMessage(context, chatMessage, replyingTo)
                                                    replyingTo = null
                                                    chatValue = TextFieldValue("")
                                                    cooldownSeconds = if (isUserPremium(context)) 3 else 7
                                                },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (cooldownSeconds > 0) Text("$cooldownSeconds", color = Color.White, fontWeight = FontWeight.Bold)
                                                else Icon(TablerIcons.Send, contentDescription = "Send", tint = Color.Black)
                                            }
                                        }
                                    }
                                }

                                "sending" -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp, top = 6.dp).height(48.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = accentColor, strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text("Sending voice message...", color = Color.Gray, fontSize = 13.sp)
                                    }
                                }

                                else -> {
                                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier.size(48.dp).clip(CircleShape).background(Color(0xFFEF4444).copy(alpha = 0.15f)).clickable { viewModel.cancelVoiceRecording() },
                                            contentAlignment = Alignment.Center
                                        ) { Icon(TablerIcons.Trash, contentDescription = "Cancel", tint = Color(0xFFEF4444)) }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Row(
                                            modifier = Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(24.dp)).background(DarkElevatedBg).padding(horizontal = 16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (voiceState == "recording") {
                                                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFEF4444).copy(alpha = dotAlpha)))
                                                Spacer(modifier = Modifier.width(10.dp))
                                            }
                                            Text(formatVoiceTime(voiceSeconds), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                            Text(" / ${formatVoiceTime(voiceMax)}", color = Color.Gray, fontSize = 13.sp)
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Box(
                                            modifier = Modifier.size(48.dp).clip(CircleShape).background(accentColor).clickable {
                                                viewModel.sendVoiceMessage(context, replyingTo)
                                                replyingTo = null
                                                cooldownSeconds = if (isUserPremium(context)) 3 else 7
                                            },
                                            contentAlignment = Alignment.Center
                                        ) { Icon(TablerIcons.Send, contentDescription = "Send voice", tint = Color.Black) }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (myStatus == "disconnected") {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Button(
                        onClick = { if (username.isNotBlank()) viewModel.createListenRoom(context, username.trim()) else android.widget.Toast.makeText(context, "Enter username", android.widget.Toast.LENGTH_SHORT).show() },
                        modifier = Modifier.weight(1f).height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = DarkCardBg), shape = RoundedCornerShape(26.dp), enabled = !isLoading
                    ) {
                        if (isLoading) CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                        else Text("+ Create", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }

                    Button(
                        onClick = { if (username.isNotBlank() && joinCode.isNotBlank()) viewModel.requestToJoinRoom(context, joinCode.trim(), username.trim()) else android.widget.Toast.makeText(context, "Enter name & code", android.widget.Toast.LENGTH_SHORT).show() },
                        modifier = Modifier.weight(1f).height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = accentColor), shape = RoundedCornerShape(26.dp), enabled = !isLoading
                    ) {
                        if (isLoading) CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.Black, strokeWidth = 2.dp)
                        else Text("Join", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            } else if (selectedTab == "members") {
                Button(
                    onClick = { viewModel.leaveRoom(context); onExitRoom() },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp).height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444).copy(alpha=0.15f)),
                    shape = RoundedCornerShape(25.dp), elevation = ButtonDefaults.buttonElevation(0.dp)
                ) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Icon(TablerIcons.Logout, null, tint=Color(0xFFEF4444))
                        Spacer(modifier=Modifier.width(8.dp))
                        Text("Leave Room", color=Color(0xFFEF4444), fontWeight=FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
            }
    }
    }
