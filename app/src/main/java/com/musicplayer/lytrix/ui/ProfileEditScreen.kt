package com.musicplayer.lytrix.ui

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*

// ══════════════════════════════════════════════
// 🔥 Profile Edit Screen
// Tabs: Avatars, Avatar Animations, Banners, Profile Effects
// ══════════════════════════════════════════════

data class EditTabItem(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private fun getResIdForName(name: String?): Int? {
    return when (name) {
        "anim_cube" -> com.musicplayer.lytrix.R.drawable.anim_cube
        "anim_moon" -> com.musicplayer.lytrix.R.drawable.anim_moon
        "banner_smoking" -> com.musicplayer.lytrix.R.drawable.banner_smoking
        "banner_cherry" -> com.musicplayer.lytrix.R.drawable.banner_cherry
        else -> null
    }
}

@Composable
fun ProfileEditScreen(
    accentColor: Color,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val currentUser = remember { FirebaseAuth.getInstance().currentUser }
    val myUid = currentUser?.uid
    val db = remember { FirebaseDatabase.getInstance("https://lytrix-player-default-rtdb.asia-southeast1.firebasedatabase.app").reference }

    val tabs = remember {
        listOf(
            EditTabItem("Avatars", Icons.Default.Face),
            EditTabItem("Animations", Icons.Default.Animation),
            EditTabItem("Banners", Icons.Default.Image),
            EditTabItem("Effects", Icons.Default.AutoAwesome)
        )
    }

    var selectedTab by remember { mutableStateOf(0) }
    var selectedAnim by remember { mutableStateOf<String?>("none") }
    var selectedBanner by remember { mutableStateOf<String?>("none") }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(myUid) {
        if (myUid != null) {
            db.child("public_profiles").child(myUid).get().addOnSuccessListener { snap ->
                selectedAnim = snap.child("avatarAnim").getValue(String::class.java) ?: "none"
                selectedBanner = snap.child("profileBanner").getValue(String::class.java) ?: "none"
            }
        }
    }

    fun saveChanges() {
        if (myUid == null) return
        isSaving = true
        db.child("public_profiles").child(myUid).updateChildren(
            mapOf(
                "avatarAnim" to (selectedAnim ?: "none"),
                "profileBanner" to (selectedBanner ?: "none")
            )
        ).addOnCompleteListener {
            isSaving = false
            Toast.makeText(context, "Changes saved!", Toast.LENGTH_SHORT).show()
        }
    }

    val bgColor = Color(0xFF07060F)
    val cardBg = Color(0xFF12103A)
    val neonPurple = Color(0xFF9B5DE5)
    val neonPink = Color(0xFFE040FB)
    val neonCyan = Color(0xFF00E5FF)

    // Animations
    val infiniteTransition = rememberInfiniteTransition(label = "editProfile")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "glow"
    )

    BackHandler { onNavigateBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // ══════════════════════════════════
        // Header
        // ══════════════════════════════════
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF1A0845), Color(0xFF120838), bgColor)
                    )
                )
        ) {
            // Decorative glow
            Canvas(modifier = Modifier.fillMaxWidth().height(80.dp)) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(neonPurple.copy(alpha = 0.2f * glowAlpha), Color.Transparent),
                        center = Offset(size.width * 0.8f, 0f),
                        radius = size.width * 0.4f
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(neonPink.copy(alpha = 0.12f * glowAlpha), Color.Transparent),
                        center = Offset(size.width * 0.15f, size.height * 0.5f),
                        radius = size.width * 0.3f
                    )
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    "Edit Profile",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f)
                )
                // Save button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.horizontalGradient(listOf(neonPurple, neonPink))
                        )
                        .clickable(enabled = !isSaving) { saveChanges() }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Save", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.width(8.dp))
            }
        }

        // ══════════════════════════════════
        // Premium Pill-shaped Tabs
        // ══════════════════════════════════
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(tabs) { index, tab ->
                val isSelected = selectedTab == index

                val tabBorderGlow = if (isSelected) {
                    Brush.horizontalGradient(
                        listOf(neonPurple.copy(alpha = 0.8f), neonPink.copy(alpha = 0.8f))
                    )
                } else {
                    Brush.horizontalGradient(
                        listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.08f))
                    )
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .then(
                            if (isSelected) {
                                Modifier.background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            neonPurple.copy(alpha = 0.25f),
                                            neonPink.copy(alpha = 0.15f)
                                        )
                                    )
                                )
                            } else {
                                Modifier.background(cardBg)
                            }
                        )
                        .border(1.5.dp, tabBorderGlow, RoundedCornerShape(24.dp))
                        .clickable { selectedTab = index }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        tab.icon, null,
                        tint = if (isSelected) neonCyan else Color.White.copy(alpha = 0.45f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        tab.label,
                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f),
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                    // Premium sparkle for selected
                    if (isSelected) {
                        Spacer(Modifier.width(5.dp))
                        Text("✨", fontSize = 11.sp)
                    }
                }
            }
        }

        // ══════════════════════════════════
        // Tab Content
        // ══════════════════════════════════
        when (selectedTab) {
            0 -> AvatarsTab(cardBg = cardBg, neonPurple = neonPurple, neonPink = neonPink, neonCyan = neonCyan)
            1 -> AnimationsTab(cardBg = cardBg, neonPurple = neonPurple, neonPink = neonPink, neonCyan = neonCyan, selectedAnim = selectedAnim, onAnimSelected = { selectedAnim = it })
            2 -> BannersTab(cardBg = cardBg, neonPurple = neonPurple, neonPink = neonPink, neonCyan = neonCyan, selectedBanner = selectedBanner, onBannerSelected = { selectedBanner = it })
            3 -> EffectsTab(cardBg = cardBg, neonPurple = neonPurple, neonPink = neonPink, neonCyan = neonCyan)
        }
    }
}

// ══════════════════════════════════════════════
// Tab Contents
// ══════════════════════════════════════════════

@Composable
private fun AvatarsTab(cardBg: Color, neonPurple: Color, neonPink: Color, neonCyan: Color) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Current avatar section
        item {
            Text("Current Avatar", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBg),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(neonPurple.copy(alpha = 0.2f))
                            .border(2.dp, Brush.sweepGradient(listOf(neonPurple, neonPink)), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Person, null, tint = neonPurple, modifier = Modifier.size(34.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Default Avatar", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("Tap on an avatar below to change", color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
                    }
                }
            }
        }

        // Available avatars header
        item {
            Spacer(Modifier.height(4.dp))
            Text("Available Avatars", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
        }

        // Placeholder grid
        item {
            EmptyStateCard(
                emoji = "🎭",
                title = "Avatars Coming Soon",
                subtitle = "Custom avatars will appear here. Stay tuned!",
                cardBg = cardBg,
                neonPurple = neonPurple
            )
        }
    }
}

@Composable
private fun AnimationsTab(
    cardBg: Color, neonPurple: Color, neonPink: Color, neonCyan: Color,
    selectedAnim: String?, onAnimSelected: (String) -> Unit
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Avatar Animations", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Add animated effects around your avatar", color = Color.White.copy(alpha = 0.45f), fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
        }

        item {
            Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.size(100.dp)) {
                    // Base avatar
                    Box(modifier = Modifier.fillMaxSize().padding(10.dp).clip(CircleShape).background(neonPurple.copy(alpha=0.3f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(40.dp))
                    }
                    // Animation overlay
                    if (selectedAnim != null && selectedAnim != "none") {
                        val resId = getResIdForName(selectedAnim)
                        if (resId != null) {
                            coil.compose.AsyncImage(
                                model = resId,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            Text("Available Animations", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
        }

        item {
            val anims = listOf(
                "anim_cube" to "Dark Cube",
                "anim_moon" to "Moon Flowers"
            )
            
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                anims.forEach { (animKey, name) ->
                    val isSelected = selectedAnim == animKey
                    val resId = getResIdForName(animKey)
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(cardBg)
                                .border(if (isSelected) 2.dp else 1.dp, if (isSelected) neonCyan else Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                                .clickable { onAnimSelected(animKey) }
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (resId != null) {
                                coil.compose.AsyncImage(
                                    model = coil.request.ImageRequest.Builder(context).data(resId).build(),
                                    contentDescription = name,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            if (isSelected) {
                                Box(modifier = Modifier.align(Alignment.TopEnd).size(20.dp).clip(CircleShape).background(neonCyan), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Check, null, tint = Color.Black, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
private fun BannersTab(
    cardBg: Color, neonPurple: Color, neonPink: Color, neonCyan: Color,
    selectedBanner: String?, onBannerSelected: (String) -> Unit
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Profile Banners", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Customize your profile header", color = Color.White.copy(alpha = 0.45f), fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .border(1.dp, neonPurple.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (selectedBanner != null && selectedBanner != "none") {
                        val resId = getResIdForName(selectedBanner)
                        if (resId != null) {
                            coil.compose.AsyncImage(
                                model = resId,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xFF1A0845), Color(0xFF2D1070), Color(0xFF1A0845)))))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Current Banner", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                            Text("Default Gradient", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            Text("Available Banners", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
        }

        item {
            // Grid of banners
            val banners = listOf(
                "banner_smoking" to "Smoking Aesthetic",
                "banner_cherry" to "Cherry Blossom"
            )
            
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                banners.forEach { (bannerKey, name) ->
                    val isSelected = selectedBanner == bannerKey
                    val resId = getResIdForName(bannerKey)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .border(if (isSelected) 2.dp else 1.dp, if (isSelected) neonCyan else Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                            .clickable { onBannerSelected(bannerKey) }
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (resId != null) {
                                coil.compose.AsyncImage(
                                    model = coil.request.ImageRequest.Builder(context).data(resId).build(),
                                    contentDescription = name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth()
                                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                            if (isSelected) {
                                Box(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(24.dp).clip(CircleShape).background(neonCyan), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Check, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EffectsTab(cardBg: Color, neonPurple: Color, neonPink: Color, neonCyan: Color) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Profile Effects", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Special visual effects for your profile page", color = Color.White.copy(alpha = 0.45f), fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
        }

        item {
            EmptyStateCard(
                emoji = "🎆",
                title = "Effects Coming Soon",
                subtitle = "Particles, neon trails, and profile themes coming!",
                cardBg = cardBg,
                neonPurple = neonPurple
            )
        }
    }
}

@Composable
private fun EmptyStateCard(
    emoji: String,
    title: String,
    subtitle: String,
    cardBg: Color,
    neonPurple: Color
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(emoji, fontSize = 48.sp)
            Spacer(Modifier.height(14.dp))
            Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                subtitle,
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
