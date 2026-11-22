package com.musicplayer.lytrix.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.*
import com.musicplayer.lytrix.listentogether.metro.MetroRoomRole

@Composable
fun ListenTogetherHubScreen(
    viewModel: PlayerViewModel,
    accentColor: Color,
    onNavigateBack: () -> Unit,
    onNavigateToMetro: () -> Unit,
    onNavigateToFirebase: () -> Unit,
    onOpenPremiumDialog: () -> Unit
) {
    val context = LocalContext.current
    val isPremium = remember(context) { isUserPremium(context) }

    // Session states for mutual exclusivity check
    val metroRole by viewModel.metroManager.roomRole.collectAsState()
    val metroPendingCode by viewModel.metroManager.pendingJoinCode.collectAsState()
    val isMetroActive = metroRole != MetroRoomRole.NONE || metroPendingCode != null

    val firebaseStatus by viewModel.myRoomStatus.collectAsState()
    val isFirebaseActive = firebaseStatus != "disconnected"

    var showConflictDialog by remember { mutableStateOf<String?>(null) }

    BackHandler {
        android.util.Log.d("BACKDBG", "2. Hub BackHandler triggered")
        onNavigateBack()
    }

    // Mutual exclusivity warning dialog
    showConflictDialog?.let { message ->
        AlertDialog(
            onDismissRequest = { showConflictDialog = null },
            containerColor = MeldCardColor,
            title = {
                Text("Active Session Found", color = MeldTextColor, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Text(message, color = MeldTextColor.copy(alpha = 0.85f), fontSize = 14.sp, lineHeight = 20.sp)
            },
            confirmButton = {
                TextButton(onClick = { showConflictDialog = null }) {
                    Text("OK", color = accentColor, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

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
                text = "Listen Together",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MeldTextColor
            )
        }

        // Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Choose your preferred session mode to enjoy music with friends in real time.",
                color = MeldTextColor.copy(alpha = 0.6f),
                fontSize = 14.sp,
                lineHeight = 20.sp
            )

            // Card 1: Standard Room (Render / Free • Unlimited)
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MeldCardColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .clickable {
                        if (isFirebaseActive) {
                            showConflictDialog = "You are currently in a VIP Room. Please leave the VIP Room before entering Standard Room."
                        } else {
                            onNavigateToMetro()
                        }
                    }
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF10B981).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(TablerIcons.Headphones, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(22.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Standard Room", color = MeldTextColor, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                Text("Lytrix Cloud Sync", color = MeldTextColor.copy(alpha = 0.55f), fontSize = 12.sp)
                            }
                        }

                        // Free Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color(0xFF10B981).copy(alpha = 0.15f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("FREE", color = Color(0xFF10B981), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Text(
                        text = "Ultra-fast playback synchronization powered by dedicated cloud server. Ideal for endless music listening sessions.",
                        color = MeldTextColor.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    // Features List
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        HubFeatureItem("Real-time song & queue synchronization", isPremium = false)
                        HubFeatureItem("Unlimited duration with zero latency", isPremium = false)
                        HubFeatureItem("Song suggestions & host volume sync", isPremium = false)
                    }

                    // Button
                    Button(
                        onClick = {
                            if (isFirebaseActive) {
                                showConflictDialog = "You are currently in a VIP Room. Please leave the VIP Room before entering Standard Room."
                            } else {
                                onNavigateToMetro()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(44.dp)
                    ) {
                        Text(
                            text = if (isMetroActive) "Resume Standard Room" else "Enter Standard Room",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(TablerIcons.ArrowRight, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                    }
                }
            }

            // Card 2: VIP Lounge (Firebase / 👑 Premium Only)
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MeldCardColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .clickable {
                        if (isMetroActive) {
                            showConflictDialog = "You are currently in a Standard Room. Please leave the Standard Room before entering VIP Lounge."
                        } else if (!isPremium) {
                            onOpenPremiumDialog()
                        } else {
                            onNavigateToFirebase()
                        }
                    }
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFF59E0B).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(TablerIcons.Crown, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(22.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("VIP Lounge", color = MeldTextColor, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                Text("Firebase Sync & Voice", color = MeldTextColor.copy(alpha = 0.55f), fontSize = 12.sp)
                            }
                        }

                        // Premium Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color(0xFFF59E0B).copy(alpha = 0.15f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!isPremium) {
                                    Icon(TablerIcons.Lock, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text("👑 PRO ONLY", color = Color(0xFFF59E0B), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Text(
                        text = "Full interactive social experience with live text chat and voice messages alongside synchronized music.",
                        color = MeldTextColor.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    // Features List
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        HubFeatureItem("Real-time Live Chat with member mentions (@)", isPremium = true)
                        HubFeatureItem("High-fidelity Voice Notes & audio messaging", isPremium = true)
                        HubFeatureItem("Private rooms with host approval & co-hosts", isPremium = true)
                    }

                    // Button
                    Button(
                        onClick = {
                            if (isMetroActive) {
                                showConflictDialog = "You are currently in a Standard Room. Please leave the Standard Room before entering VIP Lounge."
                            } else if (!isPremium) {
                                onOpenPremiumDialog()
                            } else {
                                onNavigateToFirebase()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPremium) Color(0xFFF59E0B) else Color(0xFF374151)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(44.dp)
                    ) {
                        if (!isPremium) {
                            Icon(TablerIcons.Lock, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Unlock with Premium", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        } else {
                            Text(
                                text = if (isFirebaseActive) "Resume VIP Lounge" else "Enter VIP Lounge",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(TablerIcons.ArrowRight, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun HubFeatureItem(text: String, isPremium: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (isPremium) TablerIcons.Star else TablerIcons.Check,
            contentDescription = null,
            tint = if (isPremium) Color(0xFFF59E0B) else Color(0xFF10B981),
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, color = MeldTextColor.copy(alpha = 0.85f), fontSize = 13.sp)
    }
}
