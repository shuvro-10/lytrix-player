package com.musicplayer.lytrix.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

// 🔥 Data Models
data class FriendProfile(
    val uid: String = "",
    val name: String = "",
    val photoUrl: String = "",
    val isOnline: Boolean = false,
    val lastSeen: Long = 0L,
    val currentSong: String = "",
    val currentRoomCode: String = ""
)

data class FriendRequest(
    val uid: String = "",
    val name: String = "",
    val photoUrl: String = "",
    val timestamp: Long = 0L
)

private const val FRIENDS_DB_URL =
    "https://lytrix-player-default-rtdb.asia-southeast1.firebasedatabase.app"

@Composable
fun FriendsScreen(
    viewModel: PlayerViewModel,
    accentColor: Color,
    onNavigateBack: () -> Unit,
    onNavigateToFriendProfile: (String) -> Unit = {},
    onJoinRoom: (roomCode: String) -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val currentUser = remember { FirebaseAuth.getInstance().currentUser }
    val myUid = currentUser?.uid
    val myName = currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "User"
    val myPhoto = currentUser?.photoUrl?.toString() ?: ""
    val db = remember { FirebaseDatabase.getInstance(FRIENDS_DB_URL).reference }

    val currentSong by viewModel.currentSong.collectAsState()

    var friends by remember { mutableStateOf<List<FriendProfile>>(emptyList()) }
    var friendRequests by remember { mutableStateOf<List<FriendRequest>>(emptyList()) }
    var selectedTab by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var addFriendUid by remember { mutableStateOf("") }
    var searchResult by remember { mutableStateOf<FriendProfile?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    var searchStatus by remember { mutableStateOf("") }
    var searchErrorMsg by remember { mutableStateOf("") }
    var isOfflineExpanded by remember { mutableStateOf(true) }

    // View Profile state (removed)
    // var viewProfileFriend by remember { mutableStateOf<FriendProfile?>(null) }

    BackHandler { onNavigateBack() }

    // ── Update my profile + Real-time Presence with onDisconnect ──
    LaunchedEffect(myUid) {
        if (myUid == null) return@LaunchedEffect
        val myProfileRef = db.child("public_profiles").child(myUid)

        // Set online now
        myProfileRef.child("isOnline").setValue(true)
        myProfileRef.child("lastSeen").setValue(ServerValue.TIMESTAMP)

        // When disconnected, auto set offline + update lastSeen
        myProfileRef.child("isOnline").onDisconnect().setValue(false)
        myProfileRef.child("lastSeen").onDisconnect().setValue(ServerValue.TIMESTAMP)
    }

    // Update profile info & current song
    LaunchedEffect(myUid, currentSong) {
        if (myUid == null) return@LaunchedEffect
        val songText = currentSong?.let { "${it.artist} - ${it.title}" } ?: ""
        db.child("public_profiles").child(myUid).updateChildren(
            mapOf(
                "name" to myName, "photoUrl" to myPhoto,
                "isOnline" to true,
                "lastSeen" to ServerValue.TIMESTAMP,
                "currentSong" to songText
            )
        )
    }

    DisposableEffect(myUid) {
        onDispose {
            myUid?.let {
                db.child("public_profiles").child(it)
                    .updateChildren(mapOf("isOnline" to false, "lastSeen" to ServerValue.TIMESTAMP))
            }
        }
    }

    // ── Keep Firebase connection alive for presence ──
    LaunchedEffect(Unit) {
        val connectedRef = FirebaseDatabase.getInstance(FRIENDS_DB_URL).getReference(".info/connected")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                if (connected && myUid != null) {
                    val myProfileRef = db.child("public_profiles").child(myUid)
                    myProfileRef.child("isOnline").setValue(true)
                    myProfileRef.child("lastSeen").setValue(ServerValue.TIMESTAMP)
                    myProfileRef.child("isOnline").onDisconnect().setValue(false)
                    myProfileRef.child("lastSeen").onDisconnect().setValue(ServerValue.TIMESTAMP)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        connectedRef.addValueEventListener(listener)
    }

    // ── Friends real-time listener ──
    DisposableEffect(myUid) {
        if (myUid == null) return@DisposableEffect onDispose {}
        val friendsRef = db.child("social").child(myUid).child("friends")
        val profileListeners = mutableMapOf<String, ValueEventListener>()
        val profileCache = mutableMapOf<String, FriendProfile>()
        val friendsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val currentUids = snapshot.children.mapNotNull { it.key }.toSet()
                (profileListeners.keys - currentUids).forEach { uid ->
                    db.child("public_profiles").child(uid).removeEventListener(profileListeners.remove(uid)!!)
                    profileCache.remove(uid)
                }
                friends = profileCache.values.toList()
                    .sortedWith(compareByDescending<FriendProfile> { it.isOnline }.thenBy { it.name })
                currentUids.filter { it !in profileListeners }.forEach { friendUid ->
                    val listener = object : ValueEventListener {
                        override fun onDataChange(ps: DataSnapshot) {
                            profileCache[friendUid] = FriendProfile(
                                uid = friendUid,
                                name = ps.child("name").getValue(String::class.java) ?: "User",
                                photoUrl = ps.child("photoUrl").getValue(String::class.java) ?: "",
                                isOnline = ps.child("isOnline").getValue(Boolean::class.java) ?: false,
                                lastSeen = ps.child("lastSeen").getValue(Long::class.java) ?: 0L,
                                currentSong = ps.child("currentSong").getValue(String::class.java) ?: "",
                                currentRoomCode = ps.child("currentRoomCode").getValue(String::class.java) ?: ""
                            )
                            friends = profileCache.values.toList()
                                .sortedWith(compareByDescending<FriendProfile> { it.isOnline }.thenBy { it.name })
                        }
                        override fun onCancelled(e: DatabaseError) {}
                    }
                    profileListeners[friendUid] = listener
                    db.child("public_profiles").child(friendUid).addValueEventListener(listener)
                }
            }
            override fun onCancelled(e: DatabaseError) {}
        }
        friendsRef.addValueEventListener(friendsListener)
        onDispose {
            friendsRef.removeEventListener(friendsListener)
            profileListeners.forEach { (uid, l) ->
                db.child("public_profiles").child(uid).removeEventListener(l)
            }
        }
    }

    // ── Friend requests listener ──
    DisposableEffect(myUid) {
        if (myUid == null) return@DisposableEffect onDispose {}
        val reqRef = db.child("social").child(myUid).child("friendRequests")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                friendRequests = snapshot.children.mapNotNull { child ->
                    val uid = child.key ?: return@mapNotNull null
                    FriendRequest(
                        uid = uid,
                        name = child.child("name").getValue(String::class.java) ?: "User",
                        photoUrl = child.child("photoUrl").getValue(String::class.java) ?: "",
                        timestamp = child.child("timestamp").getValue(Long::class.java) ?: 0L
                    )
                }.sortedByDescending { it.timestamp }
            }
            override fun onCancelled(e: DatabaseError) {}
        }
        reqRef.addValueEventListener(listener)
        onDispose { reqRef.removeEventListener(listener) }
    }

    // ── Actions ──
    fun removeFriend(friendUid: String) {
        if (myUid == null) return
        db.child("social").child(myUid).child("friends").child(friendUid).removeValue()
        db.child("social").child(friendUid).child("friends").child(myUid).removeValue()
        Toast.makeText(context, "Friend removed", Toast.LENGTH_SHORT).show()
    }

    fun acceptRequest(req: FriendRequest) {
        if (myUid == null) return
        db.child("social").child(myUid).child("friends").child(req.uid).setValue(true)
        db.child("social").child(req.uid).child("friends").child(myUid).setValue(true)
        db.child("social").child(myUid).child("friendRequests").child(req.uid).removeValue()
        db.child("social").child(req.uid).child("sentRequests").child(myUid).removeValue()
        Toast.makeText(context, "Friend added! 🎉", Toast.LENGTH_SHORT).show()
    }

    fun declineRequest(req: FriendRequest) {
        if (myUid == null) return
        db.child("social").child(myUid).child("friendRequests").child(req.uid).removeValue()
        db.child("social").child(req.uid).child("sentRequests").child(myUid).removeValue()
    }

    fun sendFriendRequest(targetUid: String) {
        if (myUid == null || targetUid.isBlank()) return
        if (targetUid == myUid) { searchStatus = "self"; return }
        db.child("social").child(targetUid).child("friendRequests").child(myUid).setValue(
            mapOf("name" to myName, "photoUrl" to myPhoto, "timestamp" to System.currentTimeMillis())
        )
        db.child("social").child(myUid).child("sentRequests").child(targetUid).setValue(true)
        searchStatus = "sent"
        Toast.makeText(context, "Friend request sent! 📨", Toast.LENGTH_SHORT).show()
    }

    fun searchUser(uid: String) {
        val cleanUid = uid.trim()
        if (cleanUid.isBlank()) return
        isSearching = true; searchResult = null; searchStatus = ""; searchErrorMsg = ""
        db.child("public_profiles").child(cleanUid).get()
            .addOnSuccessListener { snap ->
                isSearching = false
                if (!snap.exists()) {
                    searchStatus = "not_found"
                    return@addOnSuccessListener
                }
                searchResult = FriendProfile(
                    uid = cleanUid,
                    name = snap.child("name").getValue(String::class.java) ?: "User",
                    photoUrl = snap.child("photoUrl").getValue(String::class.java) ?: ""
                )
                when {
                    cleanUid == myUid -> searchStatus = "self"
                    friends.any { it.uid == cleanUid } -> searchStatus = "friend"
                    friendRequests.any { it.uid == cleanUid } -> searchStatus = "incoming"
                    else -> myUid?.let { me ->
                        db.child("social").child(me).child("sentRequests").child(cleanUid).get()
                            .addOnSuccessListener { sentSnap ->
                                searchStatus = if (sentSnap.exists()) "sent" else "none"
                            }
                    } ?: run { searchStatus = "none" }
                }
            }
            .addOnFailureListener { e ->
                isSearching = false
                searchErrorMsg = when {
                    e.message?.contains("Permission denied", ignoreCase = true) == true ->
                        "⚠️ Firebase rules এ permission নেই।"
                    e.message?.contains("network", ignoreCase = true) == true ->
                        "📡 Network error। Internet connection চেক করুন।"
                    e.message?.contains("interrupted", ignoreCase = true) == true ->
                        "⏱️ Timeout। আবার চেষ্টা করুন।"
                    else -> "❌ Error: ${e.message?.take(80) ?: "Unknown"}"
                }
                searchStatus = "error"
            }
    }

    val onlineFriends = friends.filter { it.isOnline }
    val offlineFriends = friends.filter { !it.isOnline }

    val bgColor = Color(0xFF09081A)
    val cardBg = Color(0xFF12103A)
    val friendPurple = Color(0xFF7C5BF0)
    val onlineGreen = Color(0xFF4CAF50)
    val offlineGray = Color(0xFF6B6B8A)

    val friendsView = androidx.compose.ui.platform.LocalView.current
    val isDarkFriends = com.musicplayer.lytrix.ui.theme.LocalIsDarkTheme.current
    DisposableEffect(isDarkFriends, friendsView) {
        if (!friendsView.isInEditMode && !isDarkFriends) {
            val window = (friendsView.context as? android.app.Activity)?.window
            if (window != null) {
                androidx.core.view.WindowCompat.getInsetsController(window, friendsView).isAppearanceLightStatusBars = false
            }
        }
        onDispose {
            if (!friendsView.isInEditMode && !isDarkFriends) {
                val window = (friendsView.context as? android.app.Activity)?.window
                if (window != null) {
                    androidx.core.view.WindowCompat.getInsetsController(window, friendsView).isAppearanceLightStatusBars = true
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(bgColor)
            .statusBarsPadding().navigationBarsPadding()
    ) {
        // ═══ Compact Header ═══
        Box(
            modifier = Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xFF2A1860), Color(0xFF120840), bgColor)))
        ) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxWidth().height(100.dp)) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color(0xFF7C5BF0).copy(alpha = 0.3f), Color.Transparent),
                        center = Offset(size.width * 0.8f, size.height * 0.3f),
                        radius = size.width * 0.4f
                    )
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                }
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Default.Group, null, tint = Color.White, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Friends", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Music is better together 💜", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
                }
                if (friendRequests.isNotEmpty()) {
                    BadgedBox(badge = {
                        Badge(containerColor = Color(0xFFE040FB)) {
                            Text("${friendRequests.size}", fontSize = 10.sp)
                        }
                    }) {
                        IconButton(onClick = { selectedTab = 3 }) {
                            Icon(Icons.Default.PersonAdd, null, tint = Color.White)
                        }
                    }
                }
            }
        }

        // ═══ Search bar ═══
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search friends...", color = Color.White.copy(alpha = 0.38f)) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.White.copy(alpha = 0.45f)) },
            singleLine = true,
            shape = CircleShape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                focusedBorderColor = friendPurple.copy(alpha = 0.5f),
                unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                focusedContainerColor = cardBg, unfocusedContainerColor = cardBg,
                cursorColor = friendPurple
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // ═══ Tabs ═══
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val tabData = listOf(
                Triple("All Friends", friends.size, 0),
                Triple("Online", onlineFriends.size, 1),
                Triple("Offline", offlineFriends.size, 2),
                Triple("Add Friend", friendRequests.size, 3)
            )
            itemsIndexed(tabData) { index, (label, count, _) ->
                val isSelected = selectedTab == index
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSelected) Brush.horizontalGradient(listOf(Color(0xFF5B2FBE), friendPurple)) else Brush.horizontalGradient(listOf(cardBg, cardBg)))
                        .border(1.dp, if (isSelected) Color.Transparent else Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
                        .clickable { selectedTab = index }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when (index) {
                        0 -> Icon(Icons.Default.Group, null, tint = Color.White, modifier = Modifier.size(14.dp))
                        1 -> Box(Modifier.size(8.dp).clip(CircleShape).background(onlineGreen))
                        2 -> Box(Modifier.size(8.dp).clip(CircleShape).background(offlineGray))
                        3 -> Icon(Icons.Default.PersonAdd, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(label, color = Color.White, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                    if (count > 0) {
                        Spacer(Modifier.width(5.dp))
                        Box(modifier = Modifier.clip(CircleShape).background(if (isSelected) Color.White.copy(alpha = 0.22f) else friendPurple.copy(alpha = 0.25f)).padding(horizontal = 6.dp, vertical = 1.dp)) {
                            Text("$count", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        // ═══ Content ═══
        if (selectedTab == 3) {
            AddFriendTab(
                myUid = myUid, clipboardManager = clipboardManager,
                addFriendUid = addFriendUid, onUidChange = { addFriendUid = it },
                searchResult = searchResult, isSearching = isSearching,
                searchStatus = searchStatus, searchErrorMsg = searchErrorMsg,
                friendRequests = friendRequests,
                onSearch = { searchUser(addFriendUid); keyboardController?.hide() },
                onSendRequest = { sendFriendRequest(addFriendUid.trim()); keyboardController?.hide() },
                onAcceptRequest = { acceptRequest(it) },
                onDeclineRequest = { declineRequest(it) },
                cardBg = cardBg, accentColor = friendPurple
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                if (myUid == null) {
                    item { Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { Text("Sign in to see your friends", color = Color.White.copy(alpha = 0.5f)) } }
                    return@LazyColumn
                }

                val showOnline = selectedTab == 0 || selectedTab == 1
                val filteredOnline = if (searchQuery.isBlank()) onlineFriends else onlineFriends.filter { it.name.contains(searchQuery, ignoreCase = true) }
                if (showOnline && filteredOnline.isNotEmpty()) {
                    item {
                        FriendsSectionHeader(dot = onlineGreen, label = "Online", count = filteredOnline.size) {
                            if (filteredOnline.any { it.currentRoomCode.isNotBlank() }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Headset, null, tint = friendPurple, modifier = Modifier.size(13.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Listening Together", color = friendPurple, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    Icon(Icons.Default.ChevronRight, null, tint = friendPurple, modifier = Modifier.size(15.dp))
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = cardBg), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                            Column {
                                filteredOnline.forEachIndexed { idx, friend ->
                                    FriendRow(
                                        friend = friend, accentColor = friendPurple,
                                        onlineGreen = onlineGreen, offlineGray = offlineGray,
                                        cardBg = cardBg,
                                        onJoin = { onJoinRoom(friend.currentRoomCode) },
                                        onRemove = { removeFriend(friend.uid) },
                                        onViewProfile = { onNavigateToFriendProfile(friend.uid) }
                                    )
                                    if (idx < filteredOnline.lastIndex) HorizontalDivider(color = Color.White.copy(alpha = 0.04f), modifier = Modifier.padding(horizontal = 16.dp))
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }

                val showOffline = selectedTab == 0 || selectedTab == 2
                val filteredOffline = if (searchQuery.isBlank()) offlineFriends else offlineFriends.filter { it.name.contains(searchQuery, ignoreCase = true) }
                if (showOffline && filteredOffline.isNotEmpty()) {
                    item {
                        FriendsSectionHeader(dot = offlineGray, label = "Offline", count = filteredOffline.size) {
                            IconButton(onClick = { isOfflineExpanded = !isOfflineExpanded }, modifier = Modifier.size(28.dp)) {
                                Icon(if (isOfflineExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = Color.White.copy(alpha = 0.45f), modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    if (isOfflineExpanded) {
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = cardBg), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                                Column {
                                    filteredOffline.forEachIndexed { idx, friend ->
                                        FriendRow(
                                            friend = friend, accentColor = friendPurple,
                                            onlineGreen = onlineGreen, offlineGray = offlineGray,
                                            cardBg = cardBg,
                                            onJoin = {},
                                            onRemove = { removeFriend(friend.uid) },
                                            onViewProfile = { onNavigateToFriendProfile(friend.uid) }
                                        )
                                        if (idx < filteredOffline.lastIndex) HorizontalDivider(color = Color.White.copy(alpha = 0.04f), modifier = Modifier.padding(horizontal = 16.dp))
                                    }
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                        }
                    }
                }

                if (friends.isEmpty()) {
                    item {
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🎵", fontSize = 52.sp)
                            Spacer(Modifier.height(16.dp))
                            Text("No friends yet", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text("Add friends to listen together!", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
                            Spacer(Modifier.height(24.dp))
                            Button(onClick = { selectedTab = 3 }, colors = ButtonDefaults.buttonColors(containerColor = friendPurple), shape = RoundedCornerShape(50)) {
                                Icon(Icons.Default.PersonAdd, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Add Friend", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

}

@Composable
private fun FriendsSectionHeader(dot: Color, label: String, count: Int, trailing: @Composable () -> Unit = {}) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(8.dp))
        Text("$label ($count)", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun FriendRow(
    friend: FriendProfile, accentColor: Color, onlineGreen: Color,
    offlineGray: Color, cardBg: Color, onJoin: () -> Unit, onRemove: () -> Unit,
    onViewProfile: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val lastSeenText = remember(friend.lastSeen) {
        if (friend.lastSeen == 0L) "unknown" else {
            val diff = System.currentTimeMillis() - friend.lastSeen
            when {
                diff < 0L -> "just now"
                diff < 60_000L -> "just now"
                diff < 3_600_000L -> "${diff / 60_000}m ago"
                diff < 86_400_000L -> "${diff / 3_600_000}h ago"
                else -> "${diff / 86_400_000}d ago"
            }
        }
    }
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        // Avatar with status dot
        Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.BottomEnd) {
            Box(modifier = Modifier.size(52.dp).border(2.dp, if (friend.isOnline) onlineGreen.copy(alpha = 0.65f) else Color.Transparent, CircleShape).padding(2.dp).clip(CircleShape).background(Color(0xFF1E1A3A)), contentAlignment = Alignment.Center) {
                if (friend.photoUrl.isNotBlank()) AsyncImage(model = friend.photoUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Text(friend.name.take(1).uppercase(), color = accentColor, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Box(modifier = Modifier.size(13.dp).border(2.dp, cardBg, CircleShape).clip(CircleShape).background(if (friend.isOnline) onlineGreen else offlineGray))
        }
        Spacer(Modifier.width(12.dp))

        // Name + status text
        Column(modifier = Modifier.weight(1f)) {
            Text(friend.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(if (friend.isOnline) onlineGreen else offlineGray))
                Spacer(Modifier.width(5.dp))
                Text(
                    if (friend.isOnline) {
                        if (friend.currentRoomCode.isNotBlank()) "Online • Listening together"
                        else "Online"
                    } else "Offline • Last seen $lastSeenText",
                    color = if (friend.isOnline) onlineGreen.copy(alpha = 0.85f) else offlineGray,
                    fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            if (friend.currentSong.isNotBlank() && friend.isOnline) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.MusicNote, null, tint = accentColor.copy(alpha = 0.65f), modifier = Modifier.size(11.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(friend.currentSong, color = Color.White.copy(alpha = 0.48f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.width(8.dp))

        // Join button (only when friend is in a Listen Together room)
        if (friend.isOnline && friend.currentRoomCode.isNotBlank()) {
            Box(modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Brush.horizontalGradient(listOf(Color(0xFF3D5AFE), Color(0xFF7C5BF0)))).clickable(onClick = onJoin).padding(horizontal = 14.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Headset, null, tint = Color.White, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Join", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 3-dot menu only
        Box {
            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.MoreVert, null, tint = Color.White.copy(alpha = 0.45f), modifier = Modifier.size(17.dp))
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, modifier = Modifier.background(Color(0xFF1E1B4B))) {
                DropdownMenuItem(
                    text = { Text("View Profile", color = Color.White) },
                    leadingIcon = { Icon(Icons.Default.Person, null, tint = Color.White.copy(alpha = 0.7f)) },
                    onClick = { showMenu = false; onViewProfile() }
                )
                DropdownMenuItem(
                    text = { Text("Remove Friend", color = Color(0xFFEF4444)) },
                    leadingIcon = { Icon(Icons.Default.PersonRemove, null, tint = Color(0xFFEF4444)) },
                    onClick = { showMenu = false; onRemove() }
                )
            }
        }
    }
}

// ═══════════════════════════════════════════
// 🔥 Friend Profile Dialog
// ═══════════════════════════════════════════
@Composable
private fun FriendProfileDialog(
    friend: FriendProfile,
    accentColor: Color,
    onlineGreen: Color,
    offlineGray: Color,
    onDismiss: () -> Unit,
    onRemoveFriend: () -> Unit
) {
    val lastSeenText = remember(friend.lastSeen) {
        if (friend.lastSeen == 0L) "Unknown" else {
            val diff = System.currentTimeMillis() - friend.lastSeen
            when {
                diff < 0L -> "Just now"
                diff < 60_000L -> "Just now"
                diff < 3_600_000L -> "${diff / 60_000} minutes ago"
                diff < 86_400_000L -> "${diff / 3_600_000} hours ago"
                else -> "${diff / 86_400_000} days ago"
            }
        }
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF12103A)),
            elevation = CardDefaults.cardElevation(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header gradient
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(accentColor.copy(alpha = 0.4f), Color.Transparent)
                            )
                        ),
                    contentAlignment = Alignment.TopEnd
                ) {
                    IconButton(onClick = onDismiss, modifier = Modifier.padding(8.dp)) {
                        Icon(Icons.Default.Close, null, tint = Color.White.copy(alpha = 0.7f))
                    }
                }

                // Avatar (overlapping header)
                Box(
                    modifier = Modifier
                        .offset(y = (-40).dp)
                        .size(80.dp)
                        .border(
                            3.dp,
                            if (friend.isOnline) onlineGreen else Color(0xFF12103A),
                            CircleShape
                        )
                        .padding(3.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E1A3A)),
                    contentAlignment = Alignment.Center
                ) {
                    if (friend.photoUrl.isNotBlank()) {
                        AsyncImage(
                            model = friend.photoUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            friend.name.take(1).uppercase(),
                            color = accentColor,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Content (shifted up because avatar overlaps)
                Column(
                    modifier = Modifier
                        .offset(y = (-24).dp)
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Name
                    Text(
                        friend.name,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(6.dp))

                    // Status badge
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (friend.isOnline) onlineGreen.copy(alpha = 0.15f)
                                else offlineGray.copy(alpha = 0.15f)
                            )
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(8.dp).clip(CircleShape)
                                .background(if (friend.isOnline) onlineGreen else offlineGray)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (friend.isOnline) "Online" else "Offline",
                            color = if (friend.isOnline) onlineGreen else offlineGray,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(Modifier.height(20.dp))

                    // Info rows
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // UID
                            ProfileInfoRow(
                                icon = Icons.Default.Fingerprint,
                                label = "UID",
                                value = friend.uid,
                                accentColor = accentColor
                            )

                            HorizontalDivider(
                                color = Color.White.copy(alpha = 0.06f),
                                modifier = Modifier.padding(vertical = 10.dp)
                            )

                            // Last Seen
                            ProfileInfoRow(
                                icon = Icons.Default.AccessTime,
                                label = "Last Seen",
                                value = if (friend.isOnline) "Now" else lastSeenText,
                                accentColor = accentColor
                            )

                            // Current Song (if online & playing)
                            if (friend.currentSong.isNotBlank() && friend.isOnline) {
                                HorizontalDivider(
                                    color = Color.White.copy(alpha = 0.06f),
                                    modifier = Modifier.padding(vertical = 10.dp)
                                )
                                ProfileInfoRow(
                                    icon = Icons.Default.MusicNote,
                                    label = "Listening to",
                                    value = friend.currentSong,
                                    accentColor = accentColor
                                )
                            }

                            // Listen Together room
                            if (friend.currentRoomCode.isNotBlank() && friend.isOnline) {
                                HorizontalDivider(
                                    color = Color.White.copy(alpha = 0.06f),
                                    modifier = Modifier.padding(vertical = 10.dp)
                                )
                                ProfileInfoRow(
                                    icon = Icons.Default.Headset,
                                    label = "Listen Together",
                                    value = "In a room",
                                    accentColor = accentColor
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // Remove Friend button
                    OutlinedButton(
                        onClick = onRemoveFriend,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444))
                    ) {
                        Icon(Icons.Default.PersonRemove, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Remove Friend", fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
private fun ProfileInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    accentColor: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = accentColor.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp)
            Text(
                value,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AddFriendTab(
    myUid: String?, clipboardManager: androidx.compose.ui.platform.ClipboardManager,
    addFriendUid: String, onUidChange: (String) -> Unit,
    searchResult: FriendProfile?, isSearching: Boolean, searchStatus: String, searchErrorMsg: String,
    friendRequests: List<FriendRequest>, onSearch: () -> Unit, onSendRequest: () -> Unit,
    onAcceptRequest: (FriendRequest) -> Unit, onDeclineRequest: (FriendRequest) -> Unit,
    cardBg: Color, accentColor: Color
) {
    val context = LocalContext.current
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (myUid != null) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = cardBg), shape = RoundedCornerShape(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Your UID", color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp)
                            Text(myUid, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { clipboardManager.setText(AnnotatedString(myUid)); Toast.makeText(context, "UID copied!", Toast.LENGTH_SHORT).show() }) {
                            Icon(Icons.Default.ContentCopy, null, tint = accentColor, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }

        item {
            Text("Add by UID", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = addFriendUid, onValueChange = onUidChange,
                    placeholder = { Text("Paste user UID here...", color = Color.White.copy(alpha = 0.38f)) },
                    singleLine = true, shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedBorderColor = accentColor, unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                        focusedContainerColor = cardBg, unfocusedContainerColor = cardBg, cursorColor = accentColor
                    ),
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = onSearch, enabled = addFriendUid.isNotBlank() && !isSearching, colors = ButtonDefaults.buttonColors(containerColor = accentColor), shape = RoundedCornerShape(12.dp), modifier = Modifier.height(56.dp)) {
                    if (isSearching) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    else Icon(Icons.Default.Search, null, tint = Color.White)
                }
            }
        }

        if (searchStatus == "error") {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF2A0A0A)), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(12.dp)) {
                        Text(searchErrorMsg.ifBlank { "❌ Unknown error" }, color = Color(0xFFFF6B6B), fontSize = 12.sp)
                    }
                }
            }
        } else if (searchStatus == "not_found") {
            item { Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) { Text("No user found with that UID.", color = Color.White.copy(alpha = 0.45f)) } }
        }

        if (searchResult != null) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = cardBg), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(16.dp))) {
                    Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(accentColor.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                            if (searchResult!!.photoUrl.isNotBlank()) AsyncImage(searchResult!!.photoUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape))
                            else Text(searchResult!!.name.take(1).uppercase(), color = accentColor, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(searchResult!!.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text(when (searchStatus) { "friend" -> "✓ Already friends"; "sent" -> "Request sent"; "self" -> "That's you!"; "incoming" -> "Sent you a request"; else -> "Lytrix User" }, color = Color.White.copy(alpha = 0.48f), fontSize = 12.sp)
                        }
                        when (searchStatus) {
                            "none" -> Button(onClick = onSendRequest, colors = ButtonDefaults.buttonColors(containerColor = accentColor), shape = RoundedCornerShape(20.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) { Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(4.dp)); Text("Add", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                            "sent" -> Box(modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.08f)).padding(horizontal = 12.dp, vertical = 8.dp)) { Text("Sent ✓", color = Color.White.copy(alpha = 0.45f), fontSize = 13.sp) }
                            "friend" -> Icon(Icons.Default.Check, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(22.dp))
                            "incoming" -> Button(onClick = { onAcceptRequest(FriendRequest(uid = searchResult!!.uid, name = searchResult!!.name, photoUrl = searchResult!!.photoUrl)) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)), shape = RoundedCornerShape(20.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) { Text("Accept", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                            else -> {}
                        }
                    }
                }
            }
        }

        if (friendRequests.isNotEmpty()) {
            item { Row(verticalAlignment = Alignment.CenterVertically) { Text("Friend Requests", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold); Spacer(Modifier.width(8.dp)); Box(modifier = Modifier.clip(CircleShape).background(accentColor).padding(horizontal = 8.dp, vertical = 2.dp)) { Text("${friendRequests.size}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) } } }
            items(friendRequests, key = { it.uid }) { req ->
                Card(colors = CardDefaults.cardColors(containerColor = cardBg), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(46.dp).clip(CircleShape).background(accentColor.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                            if (req.photoUrl.isNotBlank()) AsyncImage(req.photoUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape))
                            else Text(req.name.take(1).uppercase(), color = accentColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) { Text(req.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold); Text("Wants to be friends", color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp) }
                        Spacer(Modifier.width(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF4CAF50).copy(alpha = 0.18f)).clickable { onAcceptRequest(req) }, contentAlignment = Alignment.Center) { Icon(Icons.Default.Check, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(17.dp)) }
                            Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFFEF4444).copy(alpha = 0.18f)).clickable { onDeclineRequest(req) }, contentAlignment = Alignment.Center) { Icon(Icons.Default.Close, null, tint = Color(0xFFEF4444), modifier = Modifier.size(17.dp)) }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}
