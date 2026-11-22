package com.musicplayer.lytrix.ui

import android.annotation.SuppressLint
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import compose.icons.TablerIcons
import compose.icons.tablericons.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.first

val GoldGradient = Brush.linearGradient(listOf(Color(0xFFFFD700), Color(0xFFFFA500)))
val PremiumDarkCard = Color(0xFF1E1E1E)
val AccentNeon = Color(0xFF00E676)

data class LiveTask(val id: String, val title: String, val coins: Int)
data class CoinHistory(val title: String, val amount: Int, val timestamp: Long)

// 🔥 ফায়ারবেস ক্র্যাশ ফিক্স (যেকোনো টাইপের ডাটা সেফলি রিড করবে)
fun getSafeInt(snapshot: DataSnapshot): Int {
    val v = snapshot.getValue(Any::class.java)
    return when (v) {
        is Long -> v.toInt()
        is Int -> v
        is String -> v.toIntOrNull() ?: 0
        else -> 0
    }
}

fun getSafeInt(data: com.google.firebase.database.MutableData): Int {
    val v = data.getValue(Any::class.java)
    return when (v) {
        is Long -> v.toInt()
        is Int -> v
        is String -> v.toIntOrNull() ?: 0
        else -> 0
    }
}

@Composable
fun ShopScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var isOffline by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf("EARN") }
    var coinBalance by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }

    var adRewardCoins by remember { mutableIntStateOf(100) }
    var prices by remember { mutableStateOf(mapOf("p1" to 400, "p2" to 1000, "p3" to 2000, "p4" to 4000, "p5" to 8000, "p6" to 4000)) }
    var liveTasks by remember { mutableStateOf<List<LiveTask>>(emptyList()) }

    var showHistory by remember { mutableStateOf(false) }
    var historyList by remember { mutableStateOf<List<CoinHistory>>(emptyList()) }

    val bgColor = MaterialTheme.colorScheme.background
    val onBgColor = MaterialTheme.colorScheme.onBackground

    LaunchedEffect(Unit) {
        try { performSilentSync(context) } catch (e: Exception) {}
        if (!com.musicplayer.lytrix.ui.AdManager.isNetworkAvailable(context)) {
            isOffline = true
            isLoading = false
            return@LaunchedEffect
        }
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            val database = FirebaseDatabase.getInstance().reference
            database.child("users").child(user.uid).child("coins")
                .addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        coinBalance = getSafeInt(snapshot)
                        isLoading = false
                    }
                    override fun onCancelled(error: DatabaseError) { isLoading = false }
                })

            database.child("users").child(user.uid).child("coin_history").limitToLast(30)
                .addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val list = mutableListOf<CoinHistory>()
                        for (child in snapshot.children) {
                            val title = child.child("title").getValue(String::class.java) ?: "Unknown"
                            val amount = getSafeInt(child.child("amount"))
                            val time = child.child("timestamp").getValue(Any::class.java)?.let {
                                when(it) { is Long -> it; is Int -> it.toLong(); is String -> it.toLongOrNull(); else -> null }
                            } ?: 0L
                            list.add(CoinHistory(title, amount, time))
                        }
                        historyList = list.sortedByDescending { it.timestamp }
                    }
                    override fun onCancelled(error: DatabaseError) {}
                })

            database.child("shop_config").addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        adRewardCoins = getSafeInt(snapshot.child("ad_reward_coins")).takeIf { it > 0 } ?: 100
                        prices = mapOf(
                            "p1" to (getSafeInt(snapshot.child("pack_1_coins")).takeIf { it > 0 } ?: 400),
                            "p2" to (getSafeInt(snapshot.child("pack_2_coins")).takeIf { it > 0 } ?: 1000),
                            "p3" to (getSafeInt(snapshot.child("pack_3_coins")).takeIf { it > 0 } ?: 2000),
                            "p4" to (getSafeInt(snapshot.child("pack_4_coins")).takeIf { it > 0 } ?: 4000),
                            "p5" to (getSafeInt(snapshot.child("pack_5_coins")).takeIf { it > 0 } ?: 8000),
                            "p6" to (getSafeInt(snapshot.child("pack_6_coins")).takeIf { it > 0 } ?: 4000)
                        )
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })

            database.child("daily_tasks").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val loadedTasks = mutableListOf<LiveTask>()
                        for (i in 1..6) {
                            if (snapshot.hasChild("task_${i}_title")) {
                                val title = snapshot.child("task_${i}_title").getValue(String::class.java) ?: ""
                                val coins = getSafeInt(snapshot.child("task_${i}_coins"))
                                loadedTasks.add(LiveTask("task_$i", title, coins))
                            }
                        }
                        liveTasks = loadedTasks
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        } else {
            isLoading = false
        }
    }

    if (showHistory) {
        Dialog(onDismissRequest = { showHistory = false }) {
            Card(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.7f),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = PremiumDarkCard)
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Coin History", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { showHistory = false }) { Icon(TablerIcons.X, contentDescription = "Close", tint = Color.Gray) }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    if (historyList.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No history found", color = Color.Gray)
                        }
                    } else {
                        LazyColumn {
                            items(historyList) { item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).background(Color(0xFF2C2C2C), RoundedCornerShape(12.dp)).padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                        val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
                                        Text(sdf.format(Date(item.timestamp)), color = Color.Gray, fontSize = 12.sp)
                                    }
                                    Text(if(item.amount > 0) "+${item.amount}" else "${item.amount}", color = if(item.amount > 0) AccentNeon else Color(0xFFE91E63), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(bgColor)) {
        Box(modifier = Modifier.fillMaxWidth().height(260.dp).background(Brush.verticalGradient(listOf(Color(0xFF2C2C2C), bgColor))))
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(TablerIcons.ArrowLeft, contentDescription = "Back", tint = onBgColor) }
                Text("Lytrix Rewards", color = onBgColor, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 16.dp),
                shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = PremiumDarkCard), elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF232526), Color(0xFF414345)))).padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Total Coins", color = Color.Gray, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(TablerIcons.Coin, contentDescription = "Coin", tint = Color(0xFFFFD700), modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color(0xFFFFD700), strokeWidth = 2.dp)
                            } else {
                                Text("$coinBalance", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                    Button(onClick = { showHistory = true }, colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent), modifier = Modifier.border(1.dp, Color(0xFFFFD700), CircleShape)) { Text("History", color = Color(0xFFFFD700)) }
                }
            }
            if (isOffline) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(TablerIcons.WifiOff, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No Internet Connection", color = onBgColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { if (com.musicplayer.lytrix.ui.AdManager.isNetworkAvailable(context)) { isOffline = false; isLoading = true } }, colors = ButtonDefaults.buttonColors(containerColor = AccentNeon)) { Text("Retry", color = Color.Black, fontWeight = FontWeight.Bold) }
                    }
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    PremiumTabItem(title = "Get Coins", isSelected = selectedTab == "EARN", onClick = { selectedTab = "EARN" }, modifier = Modifier.weight(1f))
                    PremiumTabItem(title = "Redeem Gifts", isSelected = selectedTab == "REDEEM", onClick = { selectedTab = "REDEEM" }, modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (selectedTab == "EARN") { EarnCoinsContent(onBgColor, adRewardCoins, liveTasks) }
                else { RedeemGiftsContent(onBgColor, coinBalance, prices) }
            }
        }
    }
}

@Composable
fun PremiumTabItem(title: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) Color(0xFF333333) else Color.Transparent)
            .border(if (isSelected) 2.dp else 1.dp, if (isSelected) Color(0xFFFFD700) else Color.DarkGray, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) { Text(title, color = if (isSelected) Color(0xFFFFD700) else Color.Gray, fontWeight = FontWeight.Bold) }
}

@Composable
fun EarnCoinsContent(onBgColor: Color, adRewardCoins: Int, liveTasks: List<LiveTask>) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val prefsData by context.dataStore.data.collectAsState(initial = emptyPreferences())
    val todayDate = remember { SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date()) }

    val lastAdDate = prefsData[stringPreferencesKey("last_ad_date")] ?: ""
    val hasWatchedAdToday = lastAdDate == todayDate

    var isAdWebViewOpen by remember { mutableStateOf(false) }
    var adTypeOpened by remember { mutableStateOf("DAILY") }
    var adTimeLeft by remember { mutableIntStateOf(45) }

    val getSafeIntPref = { key: String ->
        try { prefsData[intPreferencesKey(key)] ?: prefsData[stringPreferencesKey(key)]?.toIntOrNull() ?: 0 } catch(e:Exception){0}
    }
    val getSafeLongPref = { key: String ->
        try { prefsData[longPreferencesKey(key)] ?: prefsData[stringPreferencesKey(key)]?.toLongOrNull() ?: 0L } catch(e:Exception){0L}
    }

    val dailyAudioMinutes = (getSafeLongPref("daily_task_audio_ms") / 60000L).toInt()
    val dailyVideoMinutes = (getSafeLongPref("daily_task_video_ms") / 60000L).toInt()
    val dailySongDownloadCount = getSafeIntPref("daily_task_song_download_count")
    val dailyPodcastDownloadCount = getSafeIntPref("daily_task_podcast_download_count")
    val dailyAdCount = getSafeIntPref("daily_task_ad_count")

    LaunchedEffect(prefsData) {
        val trackedDate = prefsData[stringPreferencesKey("task_tracked_date")] ?: ""
        if (trackedDate != todayDate && trackedDate.isNotEmpty()) {
            context.dataStore.edit { p ->
                p[stringPreferencesKey("task_tracked_date")] = todayDate
                p[longPreferencesKey("daily_task_audio_ms")] = 0L
                p[longPreferencesKey("daily_task_video_ms")] = 0L
                p[intPreferencesKey("daily_task_song_download_count")] = 0
                p[intPreferencesKey("daily_task_podcast_download_count")] = 0
                p[intPreferencesKey("daily_task_ad_count")] = 0
            }
        }
    }

    val claimReward = { taskCoins: Int, taskId: String, taskTitle: String ->
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            val dbRef = FirebaseDatabase.getInstance().reference.child("users").child(user.uid)
            dbRef.child("coins").runTransaction(object : com.google.firebase.database.Transaction.Handler {
                override fun doTransaction(currentData: com.google.firebase.database.MutableData): com.google.firebase.database.Transaction.Result {
                    val currentCoins = getSafeInt(currentData)
                    currentData.value = currentCoins + taskCoins
                    return com.google.firebase.database.Transaction.success(currentData)
                }
                override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                    if (committed) {
                        coroutineScope.launch(Dispatchers.IO) {
                            context.dataStore.edit { p -> p[booleanPreferencesKey("claimed_${taskId}_$todayDate")] = true }
                            withContext(Dispatchers.Main) {
                                dbRef.child("coin_history").push().setValue(mapOf("title" to "Completed: $taskTitle", "amount" to taskCoins, "timestamp" to System.currentTimeMillis()))
                                Toast.makeText(context, "+$taskCoins Coins Added! 🎉", Toast.LENGTH_SHORT).show()

                                // 🔥 ফিক্স: টাস্ক শেষ হওয়ার সাথে সাথেই ফায়ারবেসে সিঙ্ক করে দেবে
                                performSilentSync(context)
                            }
                        }
                    }
                }
            })
        }
    }

    if (isAdWebViewOpen) {
        LaunchedEffect(Unit) {
            adTimeLeft = 45
            while (adTimeLeft > 0) { delay(1000); adTimeLeft-- }
        }
        Dialog(onDismissRequest = { if (adTimeLeft > 0) { isAdWebViewOpen = false; Toast.makeText(context, "Ad closed early!", Toast.LENGTH_SHORT).show() } }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                Column(modifier = Modifier.fillMaxSize()) {
                    LinearProgressIndicator(progress = { adTimeLeft / 45f }, modifier = Modifier.fillMaxWidth().height(6.dp), color = AccentNeon, trackColor = Color.DarkGray)
                    Row(modifier = Modifier.fillMaxWidth().background(Color(0xFF1C1B1F)).padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "Verifying sponsor pass...", color = Color.White, fontSize = 14.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                                Text(text = if (adTimeLeft > 0) "${adTimeLeft}s left" else "Completed! 🎉", color = AccentNeon, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            if (adTimeLeft == 0) {
                                Spacer(modifier = Modifier.width(12.dp))
                                Icon(
                                    imageVector = TablerIcons.X, contentDescription = "Close Ad", tint = Color.White,
                                    modifier = Modifier.size(24.dp).clickable {
                                        if (adTypeOpened == "TASK") {
                                            coroutineScope.launch(Dispatchers.IO) {
                                                try {
                                                    context.dataStore.edit { p ->
                                                        // 🔥 ফিক্স: Safe Read ব্যবহার করা হলো যাতে ক্র্যাশ না করে
                                                        val curCount = try { p[intPreferencesKey("daily_task_ad_count")] ?: 0 } catch(e: Exception) { 0 }
                                                        p[intPreferencesKey("daily_task_ad_count")] = curCount + 1
                                                    }
                                                } catch(e: Exception) { e.printStackTrace() }
                                            }
                                            Toast.makeText(context, "Task progress updated!", Toast.LENGTH_SHORT).show()
                                        } else if (adTypeOpened == "DAILY") {
                                            val user = FirebaseAuth.getInstance().currentUser
                                            if (user != null && !hasWatchedAdToday) {
                                                val dbRef = FirebaseDatabase.getInstance().reference.child("users").child(user.uid)
                                                dbRef.child("coins").runTransaction(object : com.google.firebase.database.Transaction.Handler {
                                                    override fun doTransaction(currentData: com.google.firebase.database.MutableData): com.google.firebase.database.Transaction.Result {
                                                        val currentCoins = getSafeInt(currentData)
                                                        currentData.value = currentCoins + adRewardCoins
                                                        return com.google.firebase.database.Transaction.success(currentData)
                                                    }
                                                    override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                                                        if (committed) {
                                                            coroutineScope.launch(Dispatchers.IO) {
                                                                context.dataStore.edit { p -> p[stringPreferencesKey("last_ad_date")] = todayDate }
                                                                withContext(Dispatchers.Main) {
                                                                    dbRef.child("coin_history").push().setValue(mapOf("title" to "Daily Video Check-in", "amount" to adRewardCoins, "timestamp" to System.currentTimeMillis()))
                                                                    Toast.makeText(context, "+$adRewardCoins Coins Added! 🎉", Toast.LENGTH_LONG).show()

                                                                    // 🔥 ফিক্স: অ্যাড দেখা শেষ হওয়ার সাথে সাথেই ফায়ারবেসে সিঙ্ক করে দেবে
                                                                    performSilentSync(context)
                                                                }
                                                            }
                                                        }
                                                    }
                                                })
                                            }
                                        }
                                        isAdWebViewOpen = false
                                    }
                                )
                            }
                        }
                    }
                    @SuppressLint("SetJavaScriptEnabled")
                    androidx.compose.ui.viewinterop.AndroidView(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        factory = { ctx ->
                            android.webkit.WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                webViewClient = android.webkit.WebViewClient()
                                loadUrl("https://www.effectivecpmnetwork.com/cz76bibkd?key=803540cda27a85c41ceffe30fb64dfd0")
                            }
                        }
                    )
                }
            }
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), shape = RoundedCornerShape(20.dp), elevation = CardDefaults.cardElevation(8.dp)) {
                Column(modifier = Modifier.background(GoldGradient).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Daily Check-in", color = Color.Black, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            if (hasWatchedAdToday) { Toast.makeText(context, "You already claimed today's reward! 😊", Toast.LENGTH_LONG).show() }
                            else {
                                if (com.musicplayer.lytrix.ui.AdManager.isNetworkAvailable(context)) { adTypeOpened = "DAILY"; isAdWebViewOpen = true }
                                else { Toast.makeText(context, "No internet!", Toast.LENGTH_SHORT).show() }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Black), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Icon(if (hasWatchedAdToday) TablerIcons.CircleCheck else TablerIcons.PlayerPlay, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = if (hasWatchedAdToday) "Claimed Today ✅" else "Watch Ad to Get +$adRewardCoins Coins", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item { Text("Daily Music Tasks", color = onBgColor, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp, start = 8.dp)) }

        items(liveTasks) { task ->
            val lowerTitle = task.title.lowercase()
            val target = Regex("\\d+").find(task.title)?.value?.toInt() ?: 1

            val isAdTask = lowerTitle.matches(".*\\b(ad|ads)\\b.*".toRegex())
            val isShareTask = lowerTitle.contains("share")
            val isDownloadTask = lowerTitle.contains("download")
            val isVideoPlayTask = (lowerTitle.contains("video") || lowerTitle.contains("watch")) && !isAdTask && !isDownloadTask && !isShareTask
            val isAudioTask = (lowerTitle.contains("listen") || lowerTitle.contains("music") || task.id == "task_1") && !isAdTask && !isDownloadTask && !isVideoPlayTask && !isShareTask

            val isSongDownload = isDownloadTask && (lowerTitle.contains("song") || lowerTitle.contains("track"))
            val isPodcastDownload = isDownloadTask && (lowerTitle.contains("podcast") || lowerTitle.contains("episode"))
            val isBothDownload = isSongDownload && isPodcastDownload
            val isGenericDownload = isDownloadTask && !isSongDownload && !isPodcastDownload

            val icon = when {
                isAdTask -> TablerIcons.DeviceTv
                isVideoPlayTask -> TablerIcons.Video
                isDownloadTask -> TablerIcons.Download
                isShareTask -> TablerIcons.Share
                else -> TablerIcons.Headphones
            }

            var progress = 0
            if (isAudioTask) { progress = minOf(dailyAudioMinutes, target) }
            else if (isVideoPlayTask) { progress = minOf(dailyVideoMinutes, target) }
            else if (isBothDownload || isGenericDownload) { progress = minOf(dailySongDownloadCount + dailyPodcastDownloadCount, target) }
            else if (isSongDownload) { progress = minOf(dailySongDownloadCount, target) }
            else if (isPodcastDownload) { progress = minOf(dailyPodcastDownloadCount, target) }
            else if (isAdTask) { progress = minOf(dailyAdCount, target) }

            // 🔥 আনলিমিটেড কয়েন লক: UI সাথে সাথে আপডেট হবে
            var isClaimedUI by remember(prefsData) { mutableStateOf(prefsData[booleanPreferencesKey("claimed_${task.id}_$todayDate")] ?: false) }

            if (isShareTask) progress = if(isClaimedUI) target else 0
            val progressPercent = if (target > 0) progress.toFloat() / target.toFloat() else 0f

            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(16.dp)).background(PremiumDarkCard).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(48.dp).background(Color(0xFF2C2C2C), CircleShape), contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, tint = AccentNeon) }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(task.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    if (!isShareTask) {
                        LinearProgressIndicator(progress = { progressPercent }, modifier = Modifier.fillMaxWidth(0.8f).height(4.dp).clip(RoundedCornerShape(2.dp)), color = AccentNeon, trackColor = Color.DarkGray)
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(TablerIcons.Coin, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(14.dp))
                        Text(" +${task.coins} Coins", color = Color(0xFFFFD700), fontSize = 13.sp)
                        if (!isShareTask) Text("  •  $progress/$target", color = Color.Gray, fontSize = 12.sp)
                    }
                }

                val canClaim = progress >= target
                val buttonColor = if(isClaimedUI) Color.DarkGray else if(canClaim) Color(0xFFFFD700) else AccentNeon
                val textColor = if(isClaimedUI) Color.Gray else Color.Black
                val buttonText = if(isClaimedUI) "Done" else if(canClaim && !isShareTask) "Claim" else if(isAdTask) "Watch" else "GO"

                Button(
                    onClick = {
                        if (isClaimedUI) { Toast.makeText(context, "Already claimed!", Toast.LENGTH_SHORT).show(); return@Button }

                        if (!canClaim && isShareTask) {
                            isClaimedUI = true // 🔥 লুপহোল বন্ধ
                            try {
                                val sendIntent = android.content.Intent().apply { action = android.content.Intent.ACTION_SEND; putExtra(android.content.Intent.EXTRA_TEXT, "Experience the ultimate music app! Download Lytrix Player for free! 🚀"); type = "text/plain" }
                                context.startActivity(android.content.Intent.createChooser(sendIntent, "Share Lytrix"))
                                claimReward(task.coins, task.id, task.title)
                            } catch (e: Exception) { Toast.makeText(context, "No app found to share!", Toast.LENGTH_SHORT).show() }
                        } else if (!canClaim && isAdTask) {
                            if (com.musicplayer.lytrix.ui.AdManager.isNetworkAvailable(context)) { adTypeOpened = "TASK"; isAdWebViewOpen = true }
                            else { Toast.makeText(context, "No internet!", Toast.LENGTH_SHORT).show() }
                        } else if (canClaim) {
                            isClaimedUI = true // 🔥 লুপহোল বন্ধ
                            claimReward(task.coins, task.id, task.title)
                        } else {
                            if (isDownloadTask) Toast.makeText(context, "Download $target items to complete!", Toast.LENGTH_SHORT).show()
                            else if (isVideoPlayTask) Toast.makeText(context, "Watch videos for $target mins to complete! 🎬", Toast.LENGTH_SHORT).show()
                            else Toast.makeText(context, "Keep listening to complete the task! 🎧", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = buttonColor)
                ) { Text(buttonText, color = textColor, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
fun RedeemGiftsContent(onBgColor: Color, coinBalance: Int, prices: Map<String, Int>) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val items = listOf(
        Triple("1-Hour Ad Free", prices["p1"].toString(), TablerIcons.Ban),
        Triple("24-Hour Ad Free", prices["p2"].toString(), TablerIcons.Ban),
        Triple("3-Day Premium", prices["p3"].toString(), TablerIcons.Crown),
        Triple("7-Day Premium", prices["p4"].toString(), TablerIcons.Crown),
        Triple("15-Day Premium", prices["p5"].toString(), TablerIcons.Crown)
    )

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        item { Text("Lytrix Benefits", color = onBgColor, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp, start = 8.dp)) }
        items(items.chunked(2)) { rowItems ->
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                rowItems.forEach { item ->
                    val requiredCoins = item.second.toIntOrNull() ?: 99999
                    Card(modifier = Modifier.weight(1f).height(180.dp), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = PremiumDarkCard)) {
                        Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
                            Box(modifier = Modifier.size(60.dp).background(Color(0xFF2C2C2C), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(item.third, contentDescription = null, tint = if(item.first.contains("Plus")) Color(0xFFE91E63) else Color(0xFF64B5F6), modifier = Modifier.size(32.dp))
                            }
                            Text(item.first, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(TablerIcons.Coin, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(item.second, color = Color(0xFFFFD700), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = {
                                    if (coinBalance >= requiredCoins) {
                                        val prefs = context.getSharedPreferences("premium_prefs", Context.MODE_PRIVATE)
                                        val isPremPerm = prefs.getLong("subscription_expiry", 0L) == Long.MAX_VALUE

                                        if (isPremPerm && !item.first.contains("Ad Free")) {
                                            Toast.makeText(context, "You have Lifetime Premium! You can only redeem Ad-Free.", Toast.LENGTH_LONG).show()
                                        } else {
                                            val user = FirebaseAuth.getInstance().currentUser
                                            if (user != null) {
                                                val dbRef = FirebaseDatabase.getInstance().reference.child("users").child(user.uid)
                                                dbRef.child("coins").runTransaction(object : com.google.firebase.database.Transaction.Handler {
                                                    override fun doTransaction(currentData: com.google.firebase.database.MutableData): com.google.firebase.database.Transaction.Result {
                                                        val currentCoins = getSafeInt(currentData)
                                                        currentData.value = currentCoins - requiredCoins
                                                        return com.google.firebase.database.Transaction.success(currentData)
                                                    }
                                                    override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                                                        if (committed) {
                                                            var addedMs = 0L
                                                            if(item.first.contains("1-Hour")) addedMs = 3600000L
                                                            if(item.first.contains("24-Hour")) addedMs = 86400000L
                                                            if(item.first.contains("3-Day")) addedMs = 3L * 86400000L
                                                            if(item.first.contains("7-Day")) addedMs = 7L * 86400000L
                                                            if(item.first.contains("15-Day")) addedMs = 15L * 86400000L

                                                            coroutineScope.launch(Dispatchers.IO) {
                                                                val prefsScope = context.getSharedPreferences("premium_prefs", Context.MODE_PRIVATE)

                                                                if (item.first.contains("Premium")) {
                                                                    val currentExpiry = prefsScope.getLong("subscription_expiry", 0L)
                                                                    val newExpiry = (if (currentExpiry > System.currentTimeMillis()) currentExpiry else System.currentTimeMillis()) + addedMs

                                                                    prefsScope.edit()
                                                                        .putBoolean("is_premium", true)
                                                                        .putLong("subscription_expiry", newExpiry)
                                                                        .apply()

                                                                    dbRef.updateChildren(mapOf("is_premium" to true, "subscription_expiry" to newExpiry))

                                                                    context.dataStore.edit { p ->
                                                                        val currentDsExpiry = p[DataStoreManager.SUBSCRIPTION_EXPIRY] ?: 0L
                                                                        p[DataStoreManager.IS_PREMIUM] = true
                                                                        p[DataStoreManager.SUBSCRIPTION_EXPIRY] = (if (currentDsExpiry > System.currentTimeMillis()) currentDsExpiry else System.currentTimeMillis()) + addedMs
                                                                    }
                                                                } else if (item.first.contains("Ad Free")) {
                                                                    val adFreeExpiry = prefsScope.getLong("ad_free_expiry", 0L)
                                                                    val newExpiry = (if (adFreeExpiry > System.currentTimeMillis()) adFreeExpiry else System.currentTimeMillis()) + addedMs

                                                                    prefsScope.edit().putLong("ad_free_expiry", newExpiry).apply()
                                                                    dbRef.updateChildren(mapOf("ad_free_expiry" to newExpiry))
                                                                }

                                                                withContext(Dispatchers.Main) {
                                                                    dbRef.child("coin_history").push().setValue(mapOf("title" to "Redeemed: ${item.first}", "amount" to -requiredCoins, "timestamp" to System.currentTimeMillis()))
                                                                    Toast.makeText(context, "${item.first} Activated! 🎉", Toast.LENGTH_LONG).show()
                                                                    performSilentSync(context)
                                                                }
                                                            }
                                                        }
                                                    }
                                                })
                                            }
                                        }
                                    } else { Toast.makeText(context, "Not enough coins! Watch more ads.", Toast.LENGTH_SHORT).show() }
                                },
                                modifier = Modifier.fillMaxWidth().height(36.dp), contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = if(coinBalance >= requiredCoins) AccentNeon else Color(0xFFE91E63))
                            ) { Text(if(coinBalance >= requiredCoins) "Claim" else "Redeem", fontSize = 12.sp, color = if(coinBalance >= requiredCoins) Color.Black else Color.White, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
                if (rowItems.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReferralScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val bgColor = MaterialTheme.colorScheme.background
    val onBgColor = MaterialTheme.colorScheme.onBackground
    val coroutineScope = rememberCoroutineScope()

    val prefsData by context.dataStore.data.collectAsState(initial = emptyPreferences())
    val isCodeAlreadyApplied = prefsData[DataStoreManager.IS_CODE_APPLIED] ?: false

    var isOffline by remember { mutableStateOf(false) }
    var inviteCode by remember { mutableStateOf("LOADING...") }
    var friendCode by remember { mutableStateOf("") }
    var inviteCount by remember { mutableIntStateOf(0) }
    var isVerifying by remember { mutableStateOf(false) }
    val isLoggedIn = FirebaseAuth.getInstance().currentUser != null

    LaunchedEffect(Unit) {
        try { performSilentSync(context) } catch (e: Exception) {}
        if (!com.musicplayer.lytrix.ui.AdManager.isNetworkAvailable(context)) { isOffline = true; return@LaunchedEffect }
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            val code = "LYT-" + user.uid.take(6).uppercase()
            inviteCode = code
            val database = FirebaseDatabase.getInstance().reference
            database.child("referral_codes").child(code).setValue(user.uid)
            database.child("users").child(user.uid).child("invite_count").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) { inviteCount = getSafeInt(snapshot) }
                override fun onCancelled(error: DatabaseError) {}
            })
        } else { inviteCode = "LOGIN_REQUIRED" }
    }

    Box(modifier = Modifier.fillMaxSize().background(bgColor)) {
        Box(modifier = Modifier.fillMaxWidth().height(260.dp).background(Brush.verticalGradient(listOf(Color(0xFF1E1E1E), bgColor))))
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(TablerIcons.ArrowLeft, contentDescription = "Back", tint = onBgColor) }
            }
            if (isOffline) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(TablerIcons.WifiOff, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No Internet Connection", color = onBgColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Button(onClick = { onBack() }, colors = ButtonDefaults.buttonColors(containerColor = AccentNeon)) { Text("Go Back", color = Color.Black) }
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
                    item {
                        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(TablerIcons.Crown, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(80.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Invite Friends", color = onBgColor, fontSize = 28.sp, fontWeight = FontWeight.Black)
                            Text("Get up to 2 months Lytrix Premium for free!", color = Color.Gray, fontSize = 16.sp)
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                    item {
                        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = PremiumDarkCard)) {
                            Row(modifier = Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Column { Text("Total Invited Friends", color = Color.Gray, fontSize = 14.sp); Spacer(modifier = Modifier.height(4.dp)); Text("$inviteCount / 20", color = Color(0xFFFFD700), fontSize = 32.sp, fontWeight = FontWeight.Black) }
                                Icon(TablerIcons.Gift, contentDescription = null, tint = AccentNeon, modifier = Modifier.size(48.dp))
                            }
                        }
                    }
                    item {
                        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Button(
                                onClick = {
                                    if (!isLoggedIn) { Toast.makeText(context, "Please login to get your code!", Toast.LENGTH_SHORT).show(); return@Button }
                                    context.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, "Experience the ultimate music app! Download Lytrix Player and enter my code $inviteCode to get 3 Days of Free VIP Premium! 🚀") }, "Share your code"))
                                },
                                modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700)), shape = RoundedCornerShape(16.dp)
                            ) { Icon(TablerIcons.Share, contentDescription = null, tint = Color.Black); Spacer(modifier = Modifier.width(8.dp)); Text("Share to Friends", color = Color.Black, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.background(PremiumDarkCard, RoundedCornerShape(8.dp)).padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Text("Invite Code: ", color = Color.Gray, fontSize = 16.sp)
                                Text(inviteCode, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
                                Icon(TablerIcons.Copy, contentDescription = "Copy", tint = Color.Gray, modifier = Modifier.size(20.dp).clickable {
                                    if (isLoggedIn) { (context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("Invite Code", inviteCode)); Toast.makeText(context, "Code Copied!", Toast.LENGTH_SHORT).show() }
                                    else { Toast.makeText(context, "Please login first!", Toast.LENGTH_SHORT).show() }
                                })
                            }
                        }
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                    item {
                        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = PremiumDarkCard)) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text("Enter Friend's Code", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(16.dp))
                                OutlinedTextField(
                                    value = friendCode, onValueChange = { if(!isCodeAlreadyApplied && isLoggedIn) friendCode = it.uppercase() },
                                    enabled = !isCodeAlreadyApplied && isLoggedIn, placeholder = { Text(if(!isLoggedIn) "Login required ❌" else if(isCodeAlreadyApplied) "Code already applied ✅" else "Paste code here", color = Color.Gray) },
                                    modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFFFD700), unfocusedBorderColor = Color.DarkGray, focusedTextColor = Color.White, unfocusedTextColor = Color.White, disabledBorderColor = Color.DarkGray),
                                    shape = RoundedCornerShape(12.dp),
                                    trailingIcon = {
                                        if (isVerifying) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = AccentNeon, strokeWidth = 2.dp)
                                        else if (!isCodeAlreadyApplied) {
                                            TextButton(onClick = {
                                                if (!isLoggedIn) Toast.makeText(context, "Please sign in with Google first!", Toast.LENGTH_SHORT).show()
                                                else if(friendCode.isBlank()) Toast.makeText(context, "Enter a code first!", Toast.LENGTH_SHORT).show()
                                                else if (friendCode == inviteCode) Toast.makeText(context, "You cannot enter your own code! ❌", Toast.LENGTH_SHORT).show()
                                                else {
                                                    isVerifying = true
                                                    val currentUser = FirebaseAuth.getInstance().currentUser
                                                    if (currentUser != null) {
                                                        val myRef = FirebaseDatabase.getInstance().reference.child("users").child(currentUser.uid)
                                                        myRef.child("has_applied_code").addListenerForSingleValueEvent(object : ValueEventListener {
                                                            override fun onDataChange(mySnapshot: DataSnapshot) {
                                                                val alreadyUsed = mySnapshot.getValue(Boolean::class.java) ?: false
                                                                if (alreadyUsed) {
                                                                    isVerifying = false
                                                                    coroutineScope.launch { context.dataStore.edit { p -> p[DataStoreManager.IS_CODE_APPLIED] = true } }
                                                                    Toast.makeText(context, "You have already used a referral code! ❌", Toast.LENGTH_LONG).show()
                                                                } else {
                                                                    FirebaseDatabase.getInstance().reference.child("referral_codes").child(friendCode).addListenerForSingleValueEvent(object : ValueEventListener {
                                                                        override fun onDataChange(snapshot: DataSnapshot) {
                                                                            val friendUid = snapshot.getValue(String::class.java)
                                                                            if (friendUid != null) {
                                                                                FirebaseDatabase.getInstance().reference.child("users").child(friendUid).child("invite_count").runTransaction(object : com.google.firebase.database.Transaction.Handler {
                                                                                    override fun doTransaction(currentData: com.google.firebase.database.MutableData): com.google.firebase.database.Transaction.Result {
                                                                                        val fCount = getSafeInt(currentData)
                                                                                        if (fCount >= 20) return com.google.firebase.database.Transaction.abort()
                                                                                        currentData.value = fCount + 1
                                                                                        return com.google.firebase.database.Transaction.success(currentData)
                                                                                    }
                                                                                    override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                                                                                        if (committed) {
// 🔥 ফিক্স ১: বন্ধুর (যাকে রেফার করা হয়েছে) একাউন্টে আগের মেয়াদের সাথে যোগ করা
                                                                                            val friendRef = FirebaseDatabase.getInstance().reference.child("users").child(friendUid)
                                                                                            friendRef.child("subscription_expiry").addListenerForSingleValueEvent(object : ValueEventListener {
                                                                                                override fun onDataChange(expirySnapshot: DataSnapshot) {
                                                                                                    val currentFriendExpiry = expirySnapshot.getValue(Any::class.java)?.let {
                                                                                                        when(it) { is Long -> it; is Int -> it.toLong(); is String -> it.toLongOrNull(); else -> null }
                                                                                                    } ?: 0L
                                                                                                    val baseFriendTime = if (currentFriendExpiry > System.currentTimeMillis()) currentFriendExpiry else System.currentTimeMillis()
                                                                                                    friendRef.updateChildren(mapOf("is_premium" to true, "subscription_expiry" to (baseFriendTime + (3L * 86400000L))))
                                                                                                }
                                                                                                override fun onCancelled(error: DatabaseError) {}
                                                                                            })

                                                                                            // 🔥 ফিক্স ২: নিজের (যে রেফার করেছে) একাউন্টে আগের মেয়াদের সাথে যোগ করা
                                                                                            coroutineScope.launch(Dispatchers.IO) {
                                                                                                val prefs = context.getSharedPreferences("premium_prefs", Context.MODE_PRIVATE)
                                                                                                val currentUserExpiry = prefs.getLong("subscription_expiry", 0L)
                                                                                                val newExpiry = (if (currentUserExpiry > System.currentTimeMillis()) currentUserExpiry else System.currentTimeMillis()) + (3L * 86400000L)

                                                                                                prefs.edit()
                                                                                                    .putBoolean("is_premium", true)
                                                                                                    .putLong("subscription_expiry", newExpiry)
                                                                                                    .apply()

                                                                                                context.dataStore.edit { p ->
                                                                                                    p[DataStoreManager.IS_PREMIUM] = true
                                                                                                    p[DataStoreManager.SUBSCRIPTION_EXPIRY] = newExpiry
                                                                                                    p[DataStoreManager.HAS_APPLIED_CODE] = true
                                                                                                    p[DataStoreManager.IS_CODE_APPLIED] = true
                                                                                                }

                                                                                                withContext(Dispatchers.Main) {
                                                                                                    myRef.updateChildren(mapOf("is_premium" to true, "subscription_expiry" to newExpiry, "has_applied_code" to true))
                                                                                                    isVerifying = false
                                                                                                    Toast.makeText(context, "Success! 3 Days Premium Added! 🎉", Toast.LENGTH_LONG).show()
                                                                                                    performSilentSync(context)
                                                                                                }
                                                                                            }
                                                                                        } else {
                                                                                            isVerifying = false
                                                                                            Toast.makeText(context, "That friend's invite limit is full! ❌", Toast.LENGTH_LONG).show()
                                                                                        }
                                                                                    }
                                                                                })
                                                                            } else {
                                                                                isVerifying = false
                                                                                Toast.makeText(context, "Invalid Referral Code! ❌", Toast.LENGTH_SHORT).show()
                                                                            }
                                                                        }
                                                                        override fun onCancelled(error: DatabaseError) { isVerifying = false }
                                                                    })
                                                                }
                                                            }
                                                            override fun onCancelled(error: DatabaseError) { isVerifying = false }
                                                        })
                                                    }
                                                }
                                            }) { Text("Apply", color = if(isLoggedIn) AccentNeon else Color.Gray, fontWeight = FontWeight.Bold) }
                                        }
                                    }
                                )
                            }
                        }
                    }
                    item {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                            Text("How To Earn Premium", color = Color.Gray, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
                            Text("1. Share your code with a new user.\n2. When they enter the code, you both get 3-Day Premium.\n3. Maximum 20 friends can be invited.\n4. Premium days obtained by cheating behaviors will be terminated.", color = Color.Gray, fontSize = 14.sp, lineHeight = 22.sp)
                        }
                    }
                }
            }
        }
    }
}