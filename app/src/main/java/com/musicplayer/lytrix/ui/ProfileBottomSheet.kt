package com.musicplayer.lytrix.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

// ==========================================
// 🔥 Premium Profile Bottom Sheet v3
// ==========================================
private val PfBg          = Color(0xFF0F0C22)
private val PfCard        = Color(0xFF1A1640)
private val PfCardAlt     = Color(0xFF16122E)
private val PfBorder      = Color.White.copy(alpha = 0.07f)
private val PfPurple      = Color(0xFF7C5BF0)
private val PfPurpleLight = Color(0xFFAA88FF)
private val PfPink        = Color(0xFFE040FB)
private val PfGold        = Color(0xFFFFB300)
private val PfBlue        = Color(0xFF3D5AFE)
private val PfTeal        = Color(0xFF26C6DA)
private val PfText        = Color.White
private val PfSub         = Color.White.copy(alpha = 0.45f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileBottomSheet(
    isPremium: Boolean,
    onDismiss: () -> Unit,
    onMyProfile: () -> Unit = {},
    onFriends: () -> Unit = {},
    onListenTogether: () -> Unit = {},
    onUpgrade: () -> Unit = {},
    onDownloads: () -> Unit = {},
    onSettings: () -> Unit = {},
    onSupport: () -> Unit = {}
) {
    val context = LocalContext.current

    val user = remember { runCatching { FirebaseAuth.getInstance().currentUser }.getOrNull() }
    val displayName = if (user == null) "Guest"
    else (user.displayName?.takeIf { it.isNotBlank() } ?: "User")
    val photoUrl = user?.photoUrl?.toString()

    val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(80); visible = true }

    com.musicplayer.lytrix.ui.component.LytrixFluidBottomSheet(
        onDismissRequest = onDismiss,
        shape = sheetShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                PfAnimatedItem(visible, 0) {
                    ProfileHeaderCard(
                        displayName = displayName,
                        isPremium = isPremium,
                        photoUrl = photoUrl,
                        onClick = onMyProfile
                    )
                }
                Spacer(Modifier.height(10.dp))
            }

            item {
                // ② Friends
                PfAnimatedItem(visible, 1) {
                    PfMenuRow(
                        icon = Icons.Default.Group,
                        iconColors = listOf(Color(0xFF1565C0), Color(0xFF3D5AFE)),
                        title = "Friends",
                        subtitle = "See what they're listening to",
                        onClick = onFriends
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            item {
                // ③ Listen Together
                PfAnimatedItem(visible, 2) {
                    PfMenuRow(
                        icon = Icons.Default.Headset,
                        iconColors = listOf(Color(0xFF00838F), Color(0xFF26C6DA)),
                        title = "Listen Together",
                        subtitle = "Sync music with friends in real time",
                        onClick = {
                            if (user == null) {
                                Toast.makeText(
                                    context,
                                    "You need to sign in first",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                onListenTogether()
                            }
                        }
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            if (!isPremium) {
                item {
                    // ④ Upgrade card
                    PfAnimatedItem(visible, 3) {
                        PfUpgradeCard(onClick = onUpgrade)
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            val baseIdx = if (!isPremium) 4 else 3

            item {
                // ⑤ Downloads
                PfAnimatedItem(visible, baseIdx) {
                    PfMenuRow(
                        icon = Icons.Default.Download,
                        iconColors = listOf(Color(0xFF006064), Color(0xFF26C6DA)),
                        title = "Downloads",
                        subtitle = "Your offline songs & playlists",
                        onClick = onDownloads
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            item {
                // ⑥ Settings
                PfAnimatedItem(visible, baseIdx + 1) {
                    PfMenuRow(
                        icon = Icons.Default.Settings,
                        iconColors = listOf(Color(0xFF6D4C41), Color(0xFF8D6E63)),
                        title = "Settings",
                        subtitle = "App preferences & controls",
                        onClick = onSettings
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            item {
                // ⑦ Support
                PfAnimatedItem(visible, baseIdx + 2) {
                    PfMenuRow(
                        icon = Icons.Default.Help,
                        iconColors = listOf(Color(0xFFAD1457), Color(0xFFE040FB)),
                        title = "Support",
                        subtitle = "Help & contact us",
                        onClick = onSupport
                    )
                }
                Spacer(Modifier.height(20.dp))
            }

            item {
                PfWaveFooter()
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

// ─────────────────────────────────────────
// Staggered entrance animation wrapper
// ─────────────────────────────────────────
@Composable
private fun PfAnimatedItem(visible: Boolean, index: Int, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(280, delayMillis = index * 55)) +
                slideInVertically(
                    animationSpec = tween(320, delayMillis = index * 55, easing = FastOutSlowInEasing),
                    initialOffsetY = { it / 3 }
                )
    ) { content() }
}

// ─────────────────────────────────────────
// Profile header card
// My Profile row বাদ — এই card-এই click করলেই profile খুলবে
// ─────────────────────────────────────────
@Composable
private fun ProfileHeaderCard(
    displayName: String,
    isPremium: Boolean,
    photoUrl: String?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(PfCard)
            .border(1.dp, PfBorder, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar with sweep-gradient ring
        Box(
            modifier = Modifier
                .size(60.dp)
                .border(
                    2.dp,
                    Brush.sweepGradient(listOf(PfPink, PfPurple, PfBlue, PfTeal, PfPink)),
                    CircleShape
                )
                .padding(3.dp)
                .clip(CircleShape)
                .background(Color(0xFF1E1A3A)),
            contentAlignment = Alignment.Center
        ) {
            if (!photoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = "Profile photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = PfPurpleLight,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = displayName,
                    color = PfText,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isPremium) {
                    Spacer(Modifier.width(6.dp))
                    Text("👑", fontSize = 15.sp)
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (isPremium) "Premium User" else "Free User",
                color = if (isPremium) PfGold else PfPurpleLight,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Good music. Better days.",
                color = PfSub,
                fontSize = 12.sp
            )
        }

        Icon(
            Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint = PfSub,
            modifier = Modifier.size(20.dp)
        )
    }
}

// ─────────────────────────────────────────
// Generic menu row (individual card)
// ─────────────────────────────────────────
@Composable
private fun PfMenuRow(
    icon: ImageVector,
    iconColors: List<Color>,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(PfCardAlt)
            .border(1.dp, PfBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(iconColors)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = PfText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = PfSub, fontSize = 12.sp)
        }
        Icon(
            Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint = PfSub,
            modifier = Modifier.size(18.dp)
        )
    }
}

// ─────────────────────────────────────────
// Upgrade card with shimmer border
// ─────────────────────────────────────────
@Composable
private fun PfUpgradeCard(onClick: () -> Unit) {
    val shimmer by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = -400f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label = "shimmerX"
    )

    val cardShape = RoundedCornerShape(16.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF3A1070), Color(0xFF251055), Color(0xFF5A1880)),
                    start = Offset(shimmer * 0.3f, 0f),
                    end = Offset(shimmer * 0.3f + 800f, 600f)
                )
            )
            .border(
                1.dp,
                Brush.linearGradient(
                    colors = listOf(
                        PfPurple.copy(alpha = 0.3f),
                        PfPink.copy(alpha = 0.9f),
                        PfPurple.copy(alpha = 0.3f)
                    ),
                    start = Offset(shimmer, 0f),
                    end = Offset(shimmer + 500f, 0f)
                ),
                cardShape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.28f)),
            contentAlignment = Alignment.Center
        ) { Text("👑", fontSize = 20.sp) }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text("Upgrade to Premium", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                "Unlock more features & remove ads",
                color = Color(0xFFD5A8FF),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.width(8.dp))

        // "Go Pro" pill
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.horizontalGradient(listOf(Color(0xFF8B1FC8), Color(0xFFE040FB)))
                )
                .padding(horizontal = 12.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("👑", fontSize = 11.sp)
                Spacer(Modifier.width(4.dp))
                Text("Go Pro", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.width(6.dp))
        Icon(
            Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.size(18.dp)
        )
    }
}

// ─────────────────────────────────────────
// Animated wave footer
// ─────────────────────────────────────────
@Composable
private fun PfWaveFooter() {
    val phase by rememberInfiniteTransition(label = "wave").animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(4500, easing = LinearEasing)),
        label = "wavePhase"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(36.dp).height(1.dp).background(Color.White.copy(alpha = 0.12f)))
            Spacer(Modifier.width(10.dp))
            Text(
                "LOVE MUSIC  •  SUPPORT US",
                color = Color.White.copy(alpha = 0.28f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.8.sp
            )
            Spacer(Modifier.width(10.dp))
            Spacer(Modifier.width(36.dp).height(1.dp).background(Color.White.copy(alpha = 0.12f)))
        }
        Spacer(Modifier.height(8.dp))
        Text("❤️", fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            val w = size.width
            val h = size.height

            // Wave 1 — filled
            val path1 = Path().apply {
                moveTo(0f, h * 0.55f)
                var x = 0f
                while (x <= w) {
                    val y = sin((x / w * 2.5f * PI) + phase).toFloat() * h * 0.28f + h * 0.55f
                    lineTo(x, y)
                    x += 3f
                }
                lineTo(w, h); lineTo(0f, h); close()
            }
            drawPath(
                path = path1,
                brush = Brush.horizontalGradient(
                    listOf(
                        Color(0xFF3D5AFE).copy(alpha = 0.35f),
                        Color(0xFF7C5BF0).copy(alpha = 0.45f),
                        Color(0xFFE040FB).copy(alpha = 0.4f)
                    )
                )
            )

            // Wave 2 — stroke line
            val path2 = Path().apply {
                var x = 0f
                val startY = sin((0f / w * 3f * PI) + phase + 1.2f).toFloat() * h * 0.2f + h * 0.42f
                moveTo(0f, startY)
                while (x <= w) {
                    val y = sin((x / w * 3f * PI) + phase + 1.2f).toFloat() * h * 0.2f + h * 0.42f
                    lineTo(x, y)
                    x += 3f
                }
            }
            drawPath(
                path = path2,
                brush = Brush.horizontalGradient(
                    listOf(
                        Color(0xFF7C5BF0).copy(alpha = 0.6f),
                        Color(0xFFE040FB).copy(alpha = 0.8f),
                        Color(0xFFFF4081).copy(alpha = 0.5f)
                    )
                ),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f)
            )
        }
    }
}
