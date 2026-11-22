package com.musicplayer.lytrix.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlin.math.sin
import kotlin.math.PI

// ══════════════════════════════════════════════
// 🔥 Premium Profile Screen
// ══════════════════════════════════════════════

private const val PROFILE_DB_URL =
    "https://lytrix-player-default-rtdb.asia-southeast1.firebasedatabase.app"

private fun getResIdForName(name: String?): Int? {
    return when (name) {
        "anim_cube" -> com.musicplayer.lytrix.R.drawable.anim_cube
        "anim_moon" -> com.musicplayer.lytrix.R.drawable.anim_moon
        "banner_smoking" -> com.musicplayer.lytrix.R.drawable.banner_smoking
        "banner_cherry" -> com.musicplayer.lytrix.R.drawable.banner_cherry
        else -> null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: PlayerViewModel,
    accentColor: Color,
    friendUid: String? = null,
    onNavigateBack: () -> Unit,
    onManagePremium: () -> Unit = {},
    onNavigateToFriends: () -> Unit = {},
    onNavigateToProfileEdit: () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val currentUser = remember { FirebaseAuth.getInstance().currentUser }
    val myUid = currentUser?.uid ?: ""
    val isFriendView = friendUid != null

    var friendName by remember { mutableStateOf("Loading...") }
    var friendPhoto by remember { mutableStateOf<String?>(null) }
    var friendBio by remember { mutableStateOf("Music is my escape. Lyrics are my therapy.") }
    var friendCurrentSong by remember { mutableStateOf<String?>(null) }
    var friendIsPremium by remember { mutableStateOf(false) }

    var profileBanner by remember { mutableStateOf<String?>(null) }
    var avatarAnim by remember { mutableStateOf<String?>(null) }

    val displayUid = friendUid ?: myUid

    LaunchedEffect(displayUid) {
        if (displayUid.isNotBlank()) {
            val db = FirebaseDatabase.getInstance(PROFILE_DB_URL).reference
            db.child("public_profiles").child(displayUid).get().addOnSuccessListener { snap ->
                if (isFriendView) {
                    friendName = snap.child("name").getValue(String::class.java) ?: "Unknown"
                    friendPhoto = snap.child("photoUrl").getValue(String::class.java)
                    friendBio = snap.child("bio").getValue(String::class.java) ?: "Music is my escape. Lyrics are my therapy."
                    friendCurrentSong = snap.child("currentSong").getValue(String::class.java)
                    friendIsPremium = snap.child("isPremium").getValue(Boolean::class.java) ?: false
                }
                profileBanner = snap.child("profileBanner").getValue(String::class.java)
                avatarAnim = snap.child("avatarAnim").getValue(String::class.java)
            }
        }
    }

    val displayName = if (isFriendView) friendName else currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "Guest"
    val photoUrl = if (isFriendView) friendPhoto else currentUser?.photoUrl?.toString()
    val displayBio = if (isFriendView) friendBio else "Music is my escape. Lyrics are my therapy."

    var isPremium by remember { mutableStateOf(isUserPremium(context)) }
    val isPremiumUser = if (isFriendView) friendIsPremium else isPremium

    DisposableEffect(context) {
        val prefs = context.getSharedPreferences("premium_prefs", android.content.Context.MODE_PRIVATE)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "is_permanent" || key == "subscription_expiry" || key == "is_premium") {
                isPremium = isUserPremium(context)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val favoriteSongIds by viewModel.favoriteSongIds.collectAsState()

    val displaySong = if (isFriendView) {
        if (!friendCurrentSong.isNullOrBlank()) {
            val parts = friendCurrentSong!!.split(" - ", limit = 2)
            com.musicplayer.lytrix.model.Song(
                id = 0, title = parts.getOrElse(1) { friendCurrentSong!! }, artist = parts.getOrElse(0) { "Unknown" },
                album = "", duration = 0, uriString = "", filePath = null, albumArtUriString = null, hasLyrics = false, isVideo = false
            )
        } else null
    } else currentSong

    // Friend count from Firebase
    var friendCount by remember { mutableStateOf(0) }
    DisposableEffect(displayUid) {
        if (displayUid.isBlank()) return@DisposableEffect onDispose {}
        val db = FirebaseDatabase.getInstance(PROFILE_DB_URL).reference
        val ref = db.child("social").child(displayUid).child("friends")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                friendCount = snapshot.childrenCount.toInt()
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(listener)
        onDispose { ref.removeEventListener(listener) }
    }

    BackHandler { onNavigateBack() }

    // ── Animations ──
    val infiniteTransition = rememberInfiniteTransition(label = "profile")

    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)),
        label = "wavePhase"
    )
    val ringGlow by infiniteTransition.animateFloat(
        initialValue = 0.6f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2500, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "ringGlow"
    )
    val friendGlow by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "friendGlow"
    )

    // Colors
    val bgColor = Color(0xFF07060F)
    val cardBg = Color(0xFF12103A)
    val neonPurple = Color(0xFF9B5DE5)
    val neonPink = Color(0xFFE040FB)
    val neonCyan = Color(0xFF00E5FF)
    val gold = Color(0xFFFFB300)
    val verifiedBlue = Color(0xFF1E88E5)

    var profileWeekListeningText by remember { mutableStateOf("—") }
    var showProfileWrapped by remember { mutableStateOf(false) }
    var showProfileReceipt by remember { mutableStateOf(false) }

    LaunchedEffect(isFriendView) {
        if (!isFriendView) {
            val data = com.musicplayer.lytrix.stats.ListeningInsightsRepository.load(
                context,
                com.musicplayer.lytrix.stats.StatsPeriod.LAST_7_DAYS
            )
            profileWeekListeningText = com.musicplayer.lytrix.stats.ListeningInsightsRepository
                .formatDurationReadable(data.weekPlaytimeMs)
        }
    }

    if (showProfileWrapped) {
        com.musicplayer.lytrix.ui.stats.WrappedStoryDialog(
            onDismiss = { showProfileWrapped = false },
            onOpenReceipt = { showProfileReceipt = true }
        )
    }
    if (showProfileReceipt) {
        com.musicplayer.lytrix.ui.stats.ShareReceiptAndTasteDialog(
            initialTab = com.musicplayer.lytrix.ui.stats.ShareStatsTab.RECEIPT,
            accentColor = accentColor,
            onDismiss = { showProfileReceipt = false }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(bgColor)
            .statusBarsPadding().navigationBarsPadding(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // ══════════════════════════════════
        // BANNER + PROFILE SECTION
        // ══════════════════════════════════
        item {
            Box(modifier = Modifier.fillMaxWidth()) {
                // ── Banner (compact Discord-style ~140dp) ──
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                ) {
                    val bannerRes = getResIdForName(profileBanner)
                    if (bannerRes != null) {
                        AsyncImage(model = bannerRes, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height
                            drawRect(Brush.verticalGradient(listOf(Color(0xFF1A0845), Color(0xFF2D1070), Color(0xFF0E0825))))
                            drawCircle(
                                brush = Brush.radialGradient(
                                    listOf(neonPurple.copy(alpha = 0.25f * ringGlow), Color.Transparent),
                                    center = Offset(w * 0.7f, h * 0.3f),
                                    radius = w * 0.5f
                                )
                            )
                            drawCircle(
                                brush = Brush.radialGradient(
                                    listOf(neonPink.copy(alpha = 0.15f * ringGlow), Color.Transparent),
                                    center = Offset(w * 0.2f, h * 0.6f),
                                    radius = w * 0.35f
                                )
                            )
                        }
                    }

                    // Top bar
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp).align(Alignment.TopStart),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                        }
                        Spacer(Modifier.weight(1f))
                        Text(if (isFriendView) "Friend's Profile" else "Profile", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        if (!isFriendView) {
                            IconButton(onClick = { Toast.makeText(context, "Settings coming soon", Toast.LENGTH_SHORT).show() }) {
                                Icon(Icons.Default.MoreVert, null, tint = Color.White)
                            }
                        } else {
                            Spacer(Modifier.width(48.dp))
                        }
                    }

                    // Banner edit button
                    if (!isFriendView) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(10.dp)
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                                .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                                .clickable { onNavigateToProfileEdit() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Edit, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(15.dp))
                        }
                    }
                }

                // ── Profile photo (overlapping banner) ──
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 20.dp)
                        .offset(y = 38.dp)
                ) {
                    Box(modifier = Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier.size(80.dp)
                                .border(4.dp, bgColor, CircleShape)
                                .padding(4.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1A1535)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!photoUrl.isNullOrBlank()) {
                                AsyncImage(model = photoUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            } else {
                                Icon(Icons.Default.Person, null, tint = neonPurple, modifier = Modifier.size(40.dp))
                            }
                        }
                        // Draw Animation overlay
                        val animRes = getResIdForName(avatarAnim)
                        if (animRes != null) {
                            AsyncImage(
                                model = animRes, 
                                contentDescription = null, 
                                contentScale = ContentScale.Crop, 
                                modifier = Modifier.requiredSize(110.dp)
                            )
                        }
                        // Edit avatar button
                        if (!isFriendView) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .offset(x = (-18).dp, y = (-18).dp)
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(neonPurple)
                                    .border(2.dp, bgColor, CircleShape)
                                    .clickable { onNavigateToProfileEdit() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Edit, null, tint = Color.White, modifier = Modifier.size(13.dp))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(44.dp))
        }

        // ══════════════════════════════════
        // Name + Premium + UID
        // ══════════════════════════════════
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(displayName, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (currentUser != null && !isFriendView) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Default.Verified, null, tint = verifiedBlue, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isPremiumUser) Text("👑", fontSize = 13.sp) else Text("🎵", fontSize = 13.sp)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (isPremiumUser) "Premium Member" else "Free Member",
                        color = if (isPremiumUser) gold else Color.White.copy(alpha = 0.65f),
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                    )
                }
                if (displayUid.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .clickable {
                                clipboardManager.setText(AnnotatedString(displayUid))
                                Toast.makeText(context, "UID copied!", Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "UID: $displayUid",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Default.ContentCopy, null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(12.dp))
                    }
                }
            }
        }

        // ══════════════════════════════════
        // Bio
        // ══════════════════════════════════
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    displayBio,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                if (!isFriendView) {
                    IconButton(onClick = { Toast.makeText(context, "Bio editing coming soon", Toast.LENGTH_SHORT).show() }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Edit, null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // ══════════════════════════════════
        // 🔥 Friends Card (Premium style)
        // ══════════════════════════════════
        item {
            val friendBorderGradient = Brush.horizontalGradient(
                listOf(
                    neonCyan.copy(alpha = 0.5f * friendGlow),
                    neonPurple.copy(alpha = 0.7f * friendGlow),
                    neonCyan.copy(alpha = 0.5f * friendGlow)
                )
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.5.dp, friendBorderGradient, RoundedCornerShape(18.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF0E0D2A), Color(0xFF15103E), Color(0xFF0E0D2A))
                        )
                    )
                    .then(if (isFriendView) Modifier else Modifier.clickable { onNavigateToFriends() })
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Friends icon circle
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(neonPurple.copy(alpha = 0.35f), Color(0xFF3D5AFE).copy(alpha = 0.35f))
                                )
                            )
                            .border(1.5.dp, neonPurple.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Group, null, tint = neonCyan, modifier = Modifier.size(26.dp))
                    }

                    Spacer(Modifier.width(14.dp))

                    // Friends text + count
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Friends", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "$friendCount",
                            color = neonCyan,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    // View All button
                    if (!isFriendView) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(22.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.14f))
                                    )
                                )
                                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(22.dp))
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("View All", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Default.ChevronRight, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }

        // ══════════════════════════════════
        // Current Vibe
        // ══════════════════════════════════
        if (displaySong != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF1E1A3A)), contentAlignment = Alignment.Center) {
                            if (!displaySong.albumArtUriString.isNullOrBlank()) {
                                AsyncImage(model = displaySong.albumArtUriString, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            } else {
                                Icon(Icons.Default.MusicNote, null, tint = accentColor, modifier = Modifier.size(28.dp))
                            }
                            if (!isFriendView) {
                                Box(modifier = Modifier.size(20.dp).align(Alignment.Center).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                                    Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Current Vibe", color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp)
                            Text(displaySong.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(displaySong.artist, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.width(8.dp))
                        if (!isFriendView || (isFriendView && displaySong.title.isNotBlank())) {
                            Canvas(modifier = Modifier.width(64.dp).height(32.dp)) {
                                val barCount = 16
                                val bW = size.width / barCount * 0.6f
                                val spacing = size.width / barCount
                                for (i in 0 until barCount) {
                                    val h = if (!isFriendView && isPlaying || isFriendView) {
                                        (sin(wavePhase + i * 0.5f).toFloat() * 0.4f + 0.6f) * size.height * 0.85f
                                    } else size.height * 0.3f
                                    val t = i.toFloat() / barCount
                                    drawRoundRect(
                                        color = androidx.compose.ui.graphics.lerp(neonPurple, neonPink, t),
                                        topLeft = Offset(i * spacing, (size.height - h) / 2f),
                                        size = androidx.compose.ui.geometry.Size(bW, h),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(bW / 2)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ══════════════════════════════════
        // Lytrix Premium Card
        // ══════════════════════════════════
        if (!isFriendView) {
            item {
                val shimmer by infiniteTransition.animateFloat(
                    initialValue = -400f, targetValue = 800f,
                    animationSpec = infiniteRepeatable(tween(2500, easing = LinearEasing)),
                    label = "premiumShimmer"
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF3A1070), Color(0xFF5A1590), Color(0xFF8B1FC8)),
                                start = Offset(shimmer, 0f), end = Offset(shimmer + 600f, 400f)
                            )
                        )
                        .clickable { if (!isPremiumUser) onManagePremium() else { Toast.makeText(context, "Premium Activated! 🎉", Toast.LENGTH_SHORT).show() } }
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                            Text("👑", fontSize = 22.sp)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(if (isPremiumUser) "Lytrix Premium Active" else "Lytrix Premium", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                            Text(
                                if (isPremiumUser) "You're enjoying ad-free music, exclusive themes & high quality audio."
                                else "Upgrade for ad-free music, exclusive themes & high quality audio.",
                                color = Color(0xFFD5A8FF), fontSize = 12.sp, lineHeight = 17.sp
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        if (!isPremiumUser) {
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.18f)).padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Go Pro", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.width(4.dp))
                                    Icon(Icons.Default.ChevronRight, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // ══════════════════════════════════
        // Achievements
        // ══════════════════════════════════
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Achievements", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                if (!isFriendView) {
                    TextButton(onClick = { Toast.makeText(context, "Coming soon", Toast.LENGTH_SHORT).show() }) {
                        Text("View All", color = neonPurple, fontSize = 13.sp)
                    }
                }
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val achievements = listOf(
                    Triple("🎧", "Music Lover", if (isFriendView) friendIsPremium else favoriteSongIds.size >= 50),
                    Triple("🔥", "On Fire", false),
                    Triple("⭐", "Top Listener", isPremiumUser),
                    Triple("👑", "Premium", isPremiumUser)
                )
                achievements.forEach { (emoji, label, unlocked) ->
                    AchievementBadge(
                        emoji = emoji, label = label, unlocked = unlocked,
                        cardBg = cardBg, neonPurple = neonPurple, neonPink = neonPink,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // ══════════════════════════════════
        // Listening Stats
        // ══════════════════════════════════
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Listening Stats", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                if (!isFriendView) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(cardBg)
                                .clickable { showProfileReceipt = true }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Receipt", color = neonPink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(cardBg)
                                .clickable { showProfileWrapped = true }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Wrapped", color = neonPurple, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                } else {
                    Text("Private", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ListenStatCard("⏱", if (isFriendView) "🔒" else profileWeekListeningText, "7-Day\nListening", cardBg, neonPurple, modifier = Modifier.weight(1f))
                ListenStatCard("♪", if (isFriendView) "🔒" else "${favoriteSongIds.size}", "Favorites", cardBg, neonPink, modifier = Modifier.weight(1f))
                ListenStatCard("💿", if (isFriendView) "🔒" else "${playlists.size}", "Playlists", cardBg, Color(0xFF26C6DA), modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))
        }

        // ══════════════════════════════════
        // Recent Activity
        // ══════════════════════════════════
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Recent Activity", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                if (!isFriendView) {
                    TextButton(onClick = { Toast.makeText(context, "Coming soon", Toast.LENGTH_SHORT).show() }) {
                        Text("View All", color = neonPurple, fontSize = 13.sp)
                    }
                }
            }
            if (!isFriendView && currentSong != null) {
                ActivityRow(song = currentSong!!, action = "Currently playing", timeAgo = "Now", cardBg = cardBg, neonPurple = neonPurple)
            } else if (isFriendView && displaySong != null) {
                ActivityRow(song = displaySong, action = "Listening to", timeAgo = "Now", cardBg = cardBg, neonPurple = neonPurple)
            } else {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(if (isFriendView) "Activity is hidden" else "No recent activity", color = Color.White.copy(alpha = 0.35f), fontSize = 13.sp)
                }
            }
        }
    }
}

// ── Supporting composables ──

@Composable
private fun AchievementBadge(
    emoji: String, label: String, unlocked: Boolean,
    cardBg: Color, neonPurple: Color, neonPink: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (unlocked) cardBg else Color(0xFF0D0B1F))
            .border(1.dp, if (unlocked) Brush.sweepGradient(listOf(neonPurple, neonPink)) else Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.06f))), RoundedCornerShape(14.dp))
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                .background(if (unlocked) Brush.linearGradient(listOf(neonPurple.copy(alpha = 0.4f), neonPink.copy(alpha = 0.4f))) else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0.05f)))),
            contentAlignment = Alignment.Center
        ) {
            Text(emoji, fontSize = 20.sp, color = if (unlocked) Color.Unspecified else Color.Unspecified.copy(alpha = 0.3f))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = if (unlocked) Color.White else Color.White.copy(alpha = 0.3f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ListenStatCard(emoji: String, value: String, label: String, cardBg: Color, accentColor: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .height(100.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(cardBg)
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.Start
    ) {
        Text(emoji, fontSize = 18.sp)
        Text(value, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        Text(label, color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp, maxLines = 2)
    }
}

@Composable
private fun ActivityRow(
    song: com.musicplayer.lytrix.model.Song,
    action: String,
    timeAgo: String,
    cardBg: Color,
    neonPurple: Color
) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(cardBg).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(neonPurple.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            if (!song.albumArtUriString.isNullOrBlank()) {
                AsyncImage(song.albumArtUriString, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(Icons.Default.MusicNote, null, tint = neonPurple)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(action, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
            Text(song.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
        }
        Text(timeAgo, color = Color.White.copy(alpha = 0.35f), fontSize = 12.sp)
    }
    Spacer(Modifier.height(8.dp))
}
