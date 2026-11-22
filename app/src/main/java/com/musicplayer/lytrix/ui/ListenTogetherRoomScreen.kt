package com.musicplayer.lytrix.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import compose.icons.TablerIcons
import compose.icons.tablericons.*
import com.musicplayer.lytrix.listentogether.metro.MetroConnectionState
import com.musicplayer.lytrix.listentogether.metro.MetroRoomRole
import com.musicplayer.lytrix.listentogether.metro.UserInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetroRoomScreen(
    viewModel: PlayerViewModel,
    accentColor: Color,
    onNavigateBack: () -> Unit,
    onExitRoom: () -> Unit = onNavigateBack,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val metro = viewModel.metroManager
    val clipboardManager = LocalClipboardManager.current

    val connectionState by metro.connectionState.collectAsState()
    val roomRole by metro.roomRole.collectAsState()
    val roomState by metro.roomState.collectAsState()
    val selfUserId by metro.selfUserId.collectAsState()
    val pendingJoinCode by metro.pendingJoinCode.collectAsState()
    val pendingRequests by metro.pendingJoinRequests.collectAsState()
    val suggestions by metro.suggestions.collectAsState()
    val waitingForNames by metro.waitingForNames.collectAsState()
    val savedUsername by metro.username.collectAsState()

    var usernameInput by remember { mutableStateOf(savedUsername) }
    var roomCodeInput by remember { mutableStateOf("") }
    var selectedMemberForAction by remember { mutableStateOf<UserInfo?>(null) }
    var showActionSheet by remember { mutableStateOf(false) }

    // Toast event collector
    LaunchedEffect(Unit) {
        metro.toastEvents.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    // Auto read 8-char code from clipboard
    LaunchedEffect(Unit) {
        val clip = clipboardManager.getText()?.text?.trim()
        if (!clip.isNullOrBlank() && clip.length == 8 && clip.all { it.isLetterOrDigit() }) {
            roomCodeInput = clip.uppercase()
        }
    }

    // Pulsing dot animation for connection status
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    BackHandler(enabled = true) {
        android.util.Log.d("BACKDBG", "1. Metro BackHandler triggered")
        onNavigateBack()
    }

    // Member Action Bottom Sheet
    if (showActionSheet && selectedMemberForAction != null) {
        val member = selectedMemberForAction!!
        ModalBottomSheet(
            onDismissRequest = {
                showActionSheet = false
                selectedMemberForAction = null
            },
            containerColor = MeldCardColor,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Manage ${member.username}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MeldTextColor
                )

                // Transfer Host
                if (member.isConnected) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                metro.transferHost(member.userId)
                                showActionSheet = false
                                selectedMemberForAction = null
                            }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(TablerIcons.Crown, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text("Transfer Host", color = MeldTextColor, fontWeight = FontWeight.SemiBold)
                            Text("Make ${member.username} the host of this room", color = MeldTextColor.copy(alpha = 0.55f), fontSize = 12.sp)
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(TablerIcons.Crown, contentDescription = null, tint = Color.Gray.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text("Transfer Host (Unavailable)", color = MeldTextColor.copy(alpha = 0.4f), fontWeight = FontWeight.SemiBold)
                            Text("${member.username} is offline and cannot become host", color = MeldTextColor.copy(alpha = 0.35f), fontSize = 12.sp)
                        }
                    }
                }

                // Kick Member
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            metro.kickUser(member.userId)
                            showActionSheet = false
                            selectedMemberForAction = null
                        }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(TablerIcons.UserX, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text("Kick from room", color = Color(0xFFEF4444), fontWeight = FontWeight.SemiBold)
                        Text("Remove user from this session", color = MeldTextColor.copy(alpha = 0.55f), fontSize = 12.sp)
                    }
                }

                // Block and Kick
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            metro.blockAndKick(member.userId, member.username)
                            showActionSheet = false
                            selectedMemberForAction = null
                        }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(TablerIcons.ShieldLock, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text("Block & Kick", color = Color(0xFFDC2626), fontWeight = FontWeight.SemiBold)
                        Text("Block ${member.username} from joining future sessions", color = MeldTextColor.copy(alpha = 0.55f), fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MeldBgColor)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onNavigateBack) {
                    Icon(TablerIcons.ArrowLeft, contentDescription = "Back", tint = MeldTextColor)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Standard Room", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = MeldTextColor)
                    Text("Lytrix Cloud Sync • Free", fontSize = 12.sp, color = MeldTextColor.copy(alpha = 0.55f))
                }
            }

            IconButton(onClick = onNavigateToSettings) {
                Icon(TablerIcons.Settings, contentDescription = "Settings", tint = MeldTextColor)
            }
        }

        // Screen Body
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Buffer Banner
            AnimatedVisibility(visible = waitingForNames.isNotEmpty()) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF59E0B).copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFFF59E0B))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Buffering... waiting for ${waitingForNames.joinToString(", ")}",
                            color = Color(0xFFF59E0B),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // State A: Disconnected / Not in a room
            if (roomRole == MetroRoomRole.NONE && pendingJoinCode == null) {
                // Status Pill
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MeldCardColor)
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color.Gray.copy(alpha = 0.6f)))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Not in a room", color = MeldTextColor.copy(alpha = 0.8f), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                }

                Text(
                    text = "Listen together with friends anywhere with zero delay. Create a room to host, or join an existing session with an 8-character code.",
                    color = MeldTextColor.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )

                // Username input
                OutlinedTextField(
                    value = usernameInput,
                    onValueChange = { usernameInput = it },
                    placeholder = { Text("Your Display Name", color = MeldTextColor.copy(alpha = 0.4f)) },
                    leadingIcon = { Icon(TablerIcons.User, null, tint = MeldTextColor.copy(alpha = 0.6f)) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MeldCardColor,
                        unfocusedContainerColor = MeldCardColor,
                        focusedTextColor = MeldTextColor,
                        unfocusedTextColor = MeldTextColor,
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Create Room Button
                Button(
                    onClick = {
                        val name = usernameInput.trim()
                        if (name.isNotBlank()) {
                            metro.createRoom(name)
                        } else {
                            Toast.makeText(context, "Please enter your username", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(TablerIcons.Plus, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Create Free Room", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                // Divider Label
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = Color.White.copy(alpha = 0.1f))
                    Text("OR JOIN WITH CODE", color = MeldTextColor.copy(alpha = 0.4f), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp))
                    HorizontalDivider(modifier = Modifier.weight(1f), color = Color.White.copy(alpha = 0.1f))
                }

                // Room Code input
                OutlinedTextField(
                    value = roomCodeInput,
                    onValueChange = { roomCodeInput = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(8) },
                    placeholder = { Text("8-Character Room Code", color = MeldTextColor.copy(alpha = 0.4f)) },
                    leadingIcon = { Icon(TablerIcons.Key, null, tint = MeldTextColor.copy(alpha = 0.6f)) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MeldCardColor,
                        unfocusedContainerColor = MeldCardColor,
                        focusedTextColor = MeldTextColor,
                        unfocusedTextColor = MeldTextColor,
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Join Room Button
                FilledTonalButton(
                    onClick = {
                        val name = usernameInput.trim()
                        val code = roomCodeInput.trim()
                        if (name.isBlank()) {
                            Toast.makeText(context, "Please enter your username", Toast.LENGTH_SHORT).show()
                        } else if (code.length != 8) {
                            Toast.makeText(context, "Please enter a valid 8-character code", Toast.LENGTH_SHORT).show()
                        } else {
                            metro.joinRoom(code, name)
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MeldCardColor,
                        contentColor = MeldTextColor
                    ),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(TablerIcons.Login, null, tint = MeldTextColor, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Join Room", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }

            // State B: Waiting for Host Approval
            else if (pendingJoinCode != null) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MeldCardColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFF59E0B).copy(alpha = dotAlpha)))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Waiting for host approval...", color = Color(0xFFF59E0B), fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Text("Requested Room", color = MeldTextColor.copy(alpha = 0.5f), fontSize = 12.sp)
                        Text(
                            text = pendingJoinCode ?: "",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 6.sp,
                            color = MeldTextColor
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        OutlinedButton(
                            onClick = { metro.cancelJoin() },
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444))
                        ) {
                            Text("Cancel Request", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // State C: Active in Room
            else if (roomState != null) {
                val state = roomState!!

                // Status Indicator & Reconnection Card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when (connectionState) {
                            MetroConnectionState.CONNECTED -> MeldCardColor
                            MetroConnectionState.CONNECTING -> Color(0xFFF59E0B).copy(alpha = 0.15f)
                            MetroConnectionState.DISCONNECTED -> Color(0xFFEF4444).copy(alpha = 0.15f)
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            val statusColor = when (connectionState) {
                                MetroConnectionState.CONNECTED -> Color(0xFF10B981)
                                MetroConnectionState.CONNECTING -> Color(0xFFF59E0B)
                                MetroConnectionState.DISCONNECTED -> Color(0xFFEF4444)
                            }
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(statusColor.copy(alpha = dotAlpha))
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = when (connectionState) {
                                    MetroConnectionState.CONNECTED -> if (roomRole == MetroRoomRole.HOST) "Hosting Session • Connected" else "Listening in Sync • Connected"
                                    MetroConnectionState.CONNECTING -> "Reconnecting to room..."
                                    MetroConnectionState.DISCONNECTED -> "Disconnected from room"
                                },
                                color = statusColor,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }

                        // Show Reconnect Now button ONLY when disconnected
                        if (connectionState == MetroConnectionState.DISCONNECTED) {
                            Button(
                                onClick = { metro.forceReconnect() },
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Icon(TablerIcons.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Reconnect Now", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }

                        // Guest only: Manual Sync Button when connected
                        if (connectionState == MetroConnectionState.CONNECTED && roomRole == MetroRoomRole.GUEST) {
                            FilledTonalButton(
                                onClick = {
                                    metro.requestSync()
                                    Toast.makeText(context, "Sync requested...", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White.copy(alpha = 0.08f), contentColor = accentColor),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Icon(TablerIcons.Refresh, contentDescription = null, tint = accentColor, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sync Now", color = accentColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Room Code Poster (SimpMusic Style)
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MeldCardColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("ROOM CODE", color = MeldTextColor.copy(alpha = 0.5f), fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = state.roomCode,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 6.sp,
                            color = MeldTextColor,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(state.roomCode))
                                    Toast.makeText(context, "Copied room code", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(50),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                            ) {
                                Icon(TablerIcons.Copy, null, tint = MeldTextColor, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy", color = MeldTextColor, fontSize = 13.sp)
                            }

                            Button(
                                onClick = {
                                    val roomUrl = "https://lytrixteam.blogspot.com/?code=${state.roomCode}&type=metro"
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            "🎵 Join my Standard Room music session on Lytrix Player! Tap to listen together:\n$roomUrl"
                                        )
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Room"))
                                },
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                            ) {
                                Icon(TablerIcons.Share, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Share", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }

                // Host Only: Join Requests Card
                if (roomRole == MetroRoomRole.HOST && pendingRequests.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("JOIN REQUESTS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MeldTextColor.copy(alpha = 0.5f), letterSpacing = 1.sp)
                        pendingRequests.forEach { req ->
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MeldCardColor),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(req.username, color = MeldTextColor, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton(onClick = { metro.rejectJoin(req.userId) }) {
                                            Text("Decline", color = Color(0xFFEF4444), fontSize = 13.sp)
                                        }
                                        Button(
                                            onClick = { metro.approveJoin(req.userId) },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                            shape = RoundedCornerShape(50),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                        ) {
                                            Text("Approve", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Host Only: Suggestions Card
                if (roomRole == MetroRoomRole.HOST && suggestions.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("SONG SUGGESTIONS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MeldTextColor.copy(alpha = 0.5f), letterSpacing = 1.sp)
                        suggestions.forEach { sugg ->
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MeldCardColor),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(sugg.trackInfo?.title ?: "Track", color = MeldTextColor, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("from ${sugg.fromUsername}", color = MeldTextColor.copy(alpha = 0.55f), fontSize = 12.sp)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        TextButton(onClick = { metro.rejectSuggestion(sugg.suggestionId) }) {
                                            Text("Decline", color = Color(0xFFEF4444), fontSize = 12.sp)
                                        }
                                        Button(
                                            onClick = { metro.approveSuggestion(sugg.suggestionId) },
                                            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                                            shape = RoundedCornerShape(50),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                        ) {
                                            Text("Add", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Members List (SimpMusic Style)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "MEMBERS (${state.users.size})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MeldTextColor.copy(alpha = 0.5f),
                        letterSpacing = 1.sp
                    )

                    state.users.forEach { member ->
                        val isSelf = member.userId == selfUserId
                        val isOffline = !member.isConnected
                        val canManage = roomRole == MetroRoomRole.HOST && !isSelf

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MeldCardColor)
                                .clickable(enabled = canManage) {
                                    selectedMemberForAction = member
                                    showActionSheet = true
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Avatar with Initials
                            val initial = member.username.trim().firstOrNull()?.uppercase() ?: "?"
                            val avatarBg = remember(member.userId) {
                                val colors = listOf(Color(0xFF6366F1), Color(0xFFEC4899), Color(0xFF10B981), Color(0xFFF59E0B), Color(0xFF8B5CF6))
                                colors[Math.abs(member.userId.hashCode()) % colors.size]
                            }
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(if (isOffline) avatarBg.copy(alpha = 0.4f) else avatarBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(initial, color = if (isOffline) Color.White.copy(alpha = 0.5f) else Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        member.username,
                                        color = if (isOffline) MeldTextColor.copy(alpha = 0.5f) else MeldTextColor,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp
                                    )
                                    if (isOffline) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("(Offline)", color = Color(0xFFEF4444).copy(alpha = 0.85f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                                if (isSelf) {
                                    Text("You", color = accentColor, fontSize = 12.sp)
                                }
                            }

                            if (member.isHost) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(Color(0xFFFBBF24).copy(alpha = 0.15f))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("HOST", color = Color(0xFFFBBF24), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            } else if (canManage) {
                                Icon(TablerIcons.DotsVertical, contentDescription = "Manage", tint = MeldTextColor.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                // Upcoming Queue Section (SimpMusic / Spotify style)
                if (state.queue.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "UP NEXT (${state.queue.size})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MeldTextColor.copy(alpha = 0.5f),
                                letterSpacing = 1.sp
                            )
                            Text("Synced with Host", fontSize = 11.sp, color = accentColor.copy(alpha = 0.8f), fontWeight = FontWeight.Medium)
                        }

                        state.queue.forEachIndexed { index, queueTrack ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MeldCardColor)
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    color = MeldTextColor.copy(alpha = 0.45f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(22.dp)
                                )

                                AsyncImage(
                                    model = queueTrack.thumbnail,
                                    contentDescription = null,
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.DarkGray)
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = queueTrack.title,
                                        color = MeldTextColor,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = queueTrack.artist,
                                        color = MeldTextColor.copy(alpha = 0.55f),
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Leave Room Button
                OutlinedButton(
                    onClick = { metro.leaveRoom(); onExitRoom() },
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(TablerIcons.Logout, null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Leave Room", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
