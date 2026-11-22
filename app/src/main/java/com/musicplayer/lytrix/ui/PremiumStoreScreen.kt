package com.musicplayer.lytrix.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Download

import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap

// =====================================================================
//  ফায়ারবেস ডাইনামিক ডেটা স্টোর করার ক্লাস
// =====================================================================
data class ShopData(
    val bkash: String = "017XXXXXXXX",
    val nagad: String = "018XXXXXXXX",
    val p3: Int = 10,
    val p7: Int = 20,
    val p15: Int = 30,
    val p30: Int = 50,
    val p90: Int = 120
)

private const val SHOW_DEMO_PAYMENTS = false
private val StoreBg = Color(0xFF0B0B12)
private val PendingColor = Color(0xFFFFB020)
private val CompletedColor = Color(0xFF34D399)
private val DeclinedColor = Color(0xFFFF6B6B)

private enum class PremiumStoreStep { PLANS, PAYMENT, PENDING }

private enum class PremiumPayMethod(val title: String, val letter: String, val color: Color, val packageName: String, val logoName: String) {
    BKASH("bKash", "b", Color(0xFFE2136E), "com.bKash.customerapp", "ic_bkash"),
    NAGAD("Nagad", "N", Color(0xFFF6921E), "com.konasl.nagad", "ic_nagad")
}

private data class PremiumPlanItem(val id: String, val title: String, val price: Int, val days: Int, val best: Boolean = false)

private fun readableOn(c: Color): Color = if (c.luminance() > 0.5f) Color.Black else Color.White

// =====================================================================
//  PAYMENT REQUEST
// =====================================================================
enum class PremiumPaymentStatus { PENDING, COMPLETED, DECLINED }

data class PremiumPaymentRequest(
    val planTitle: String,
    val priceBdt: Int,
    val days: Int,
    val method: String,
    val trxId: String,
    val status: PremiumPaymentStatus = PremiumPaymentStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis()
)

object PremiumPaymentStore {
    private const val PREFS = "premium_payment_requests"
    private const val KEY = "list"

    fun load(context: Context): List<PremiumPaymentRequest> {
        return try {
            val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val status = when (o.optString("status")) {
                    "COMPLETED" -> PremiumPaymentStatus.COMPLETED
                    "DECLINED" -> PremiumPaymentStatus.DECLINED
                    else -> PremiumPaymentStatus.PENDING
                }
                PremiumPaymentRequest(
                    o.optString("plan"),
                    o.optInt("price"),
                    o.optInt("days"),
                    o.optString("method"),
                    o.optString("trx"),
                    status,
                    o.optLong("time")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun save(context: Context, list: List<PremiumPaymentRequest>) {
        val arr = JSONArray()
        list.forEach { r ->
            arr.put(
                JSONObject()
                    .put("plan", r.planTitle)
                    .put("price", r.priceBdt)
                    .put("days", r.days)
                    .put("method", r.method)
                    .put("trx", r.trxId)
                    .put("status", r.status.name)
                    .put("time", r.createdAt)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }

    fun add(context: Context, req: PremiumPaymentRequest) {
        try {
            save(context, (listOf(req) + load(context).filter { it.trxId != req.trxId }).take(10))
        } catch (e: Exception) {}
    }

    fun updateStatus(context: Context, trxId: String, status: PremiumPaymentStatus) {
        try {
            val list = load(context)
            if (list.none { it.trxId == trxId && it.status != status }) return
            save(context, list.map { if (it.trxId == trxId) it.copy(status = status) else it })
        } catch (e: Exception) {}
    }
    fun rateLimitMessage(context: Context): String? {
        val now = System.currentTimeMillis()
        val list = load(context)
        val last5min = list.count { now - it.createdAt < 5 * 60_000L }
        val last24h = list.count { now - it.createdAt < 24 * 60 * 60_000L }
        return when {
            last5min >= 2 -> "Too many attempts. Please wait 5 minutes."
            last24h >= 7 -> "Daily limit reached. Please try again tomorrow."
            else -> null
        }
    }
}

private fun demoPayments(): List<PremiumPaymentRequest> = listOf(
    PremiumPaymentRequest("1 Month", 50, 30, "bKash", "8N7A6D5EFG", PremiumPaymentStatus.PENDING, System.currentTimeMillis())
)

// =====================================================================
//  MAIN SCREEN
// =====================================================================
@Composable
fun PremiumStoreScreen(
    accentColor: Color,
    onNavigateBack: () -> Unit,
    onPremiumActivated: () -> Unit = {},
    onSubmitTrx: (
        planTitle: String, priceBdt: Int, days: Int, method: String, trxId: String,
        onSuccess: () -> Unit, onError: (String) -> Unit
    ) -> Unit = { _, _, _, _, _, onSuccess, _ -> onSuccess() },
    payments: List<PremiumPaymentRequest>? = null
) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableStateOf(PremiumStoreStep.PLANS) }
    var selectedPlanId by rememberSaveable { mutableStateOf("p30") }
    var methodName by rememberSaveable { mutableStateOf<String?>(null) }
    var trxId by rememberSaveable { mutableStateOf("") }
    var localPayments by remember { mutableStateOf(PremiumPaymentStore.load(context)) }
    var submitting by remember { mutableStateOf(false) }

    var shopData by remember { mutableStateOf(ShopData()) }

    LaunchedEffect(Unit) {
        val ref = FirebaseDatabase.getInstance("https://lytrix-player-default-rtdb.asia-southeast1.firebasedatabase.app")
            .reference.child("shop_config")

        ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val bNum = (snapshot.child("bkash_number").value?.toString() ?: "017XXXXXXXX").replace("\"", "").trim()
                    val nNum = (snapshot.child("nagad_number").value?.toString() ?: "018XXXXXXXX").replace("\"", "").trim()

                    fun getPrice(key1: String, key2: String, default: Int): Int {
                        var rawText = snapshot.child(key1).value?.toString()
                        if (rawText == null) rawText = snapshot.child(key2).value?.toString()
                        if (rawText == null) return default
                        val cleanNumber = rawText.replace(Regex("[^0-9]"), "")
                        return cleanNumber.toIntOrNull() ?: default
                    }

                    val p3 = getPrice("price_3_days", "price_3_day", 10)
                    val p7 = getPrice("price_7_days", "price_7_day", 20)
                    val p15 = getPrice("price_15_days", "price_15_day", 30)
                    val p30 = getPrice("price_1_month", "price_1_months", 50)
                    val p90 = getPrice("price_3_months", "price_3_month", 120)

                    shopData = ShopData(bNum, nNum, p3, p7, p15, p30, p90)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    // 🔥 প্রতিটা Pending TrxID এর জন্য Firebase লিসেনার
    val pendingKey = localPayments
        .filter { it.status == PremiumPaymentStatus.PENDING }
        .joinToString(",") { it.trxId }

    DisposableEffect(pendingKey) {
        val db = FirebaseDatabase.getInstance("https://lytrix-player-default-rtdb.asia-southeast1.firebasedatabase.app")
        val seenOnServer = mutableSetOf<String>()

        val attached = localPayments
            .filter { it.status == PremiumPaymentStatus.PENDING }
            .map { req ->
                val ref = db.reference.child("pending_payments").child(req.trxId)
                val listener = object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (snapshot.exists()) {
                            val owner = snapshot.child("uid").getValue(String::class.java)
                            val me = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                            if (owner != null && me != null && owner != me) {
                                PremiumPaymentStore.updateStatus(context, req.trxId, PremiumPaymentStatus.DECLINED)
                                localPayments = PremiumPaymentStore.load(context)
                                return
                            }
                            seenOnServer.add(req.trxId)
                            val newStatus = when (snapshot.child("status").getValue(String::class.java)?.uppercase()) {
                                "COMPLETED" -> PremiumPaymentStatus.COMPLETED
                                "DECLINED" -> PremiumPaymentStatus.DECLINED
                                else -> null
                            }
                            if (newStatus != null) {
                                PremiumPaymentStore.updateStatus(context, req.trxId, newStatus)
                                localPayments = PremiumPaymentStore.load(context)
                                if (newStatus == PremiumPaymentStatus.COMPLETED) {
                                    PremiumSyncManager.start(context)
                                    AdManager.refreshAdFreeState(context)
                                }
                            }
                        } else {
                            val oldEnough = System.currentTimeMillis() - req.createdAt > 20_000
                            if (req.trxId in seenOnServer || oldEnough) {
                                PremiumPaymentStore.updateStatus(context, req.trxId, PremiumPaymentStatus.DECLINED)
                                localPayments = PremiumPaymentStore.load(context)
                            }
                        }
                    }
                    override fun onCancelled(error: DatabaseError) {}
                }
                ref.addValueEventListener(listener)
                ref to listener
            }

        onDispose { attached.forEach { (ref, l) -> ref.removeEventListener(l) } }
    }

    // 🔥 স্ট্যাটাস Pending থেকে বদলে গেলে Pending স্ক্রিন অটো বন্ধ
    LaunchedEffect(localPayments, step) {
        if (step == PremiumStoreStep.PENDING) {
            val current = localPayments.firstOrNull { it.trxId == trxId.trim() }
            if (current != null && current.status != PremiumPaymentStatus.PENDING) {
                trxId = ""
                methodName = null
                step = PremiumStoreStep.PLANS
            }
        }
    }

    val dynamicPlans = listOf(
        PremiumPlanItem("p3", "3 Days", shopData.p3, 3),
        PremiumPlanItem("p7", "7 Days", shopData.p7, 7),
        PremiumPlanItem("p15", "15 Days", shopData.p15, 15),
        PremiumPlanItem("p30", "1 Month", shopData.p30, 30, best = true),
        PremiumPlanItem("p90", "3 Months", shopData.p90, 90)
    )

    val plan = dynamicPlans.firstOrNull { it.id == selectedPlanId } ?: dynamicPlans.first()
    val method = PremiumPayMethod.entries.firstOrNull { it.name == methodName }
    val shownPayments = (if (SHOW_DEMO_PAYMENTS) demoPayments() else emptyList()) + (payments ?: localPayments)

    BackHandler(enabled = step == PremiumStoreStep.PAYMENT) { step = PremiumStoreStep.PLANS }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(StoreBg)
            .drawBehind {
                val r = size.width * 0.95f
                val c1 = Offset(size.width * 0.9f, size.height * 0.04f)
                drawCircle(Brush.radialGradient(listOf(accentColor.copy(alpha = 0.32f), Color.Transparent), center = c1, radius = r), radius = r, center = c1)
                val c2 = Offset(size.width * 0.05f, size.height * 0.92f)
                drawCircle(Brush.radialGradient(listOf(accentColor.copy(alpha = 0.16f), Color.Transparent), center = c2, radius = r), radius = r, center = c2)
            }
    ) {
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                (fadeIn(tween(320)) + slideInHorizontally(tween(320)) { w -> if (forward) w / 8 else -w / 8 })
                    .togetherWith(fadeOut(tween(200)) + slideOutHorizontally(tween(200)) { w -> if (forward) -w / 8 else w / 8 })
            },
            label = "premium_step",
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) { currentStep ->
            when (currentStep) {
                PremiumStoreStep.PLANS -> PlansStep(
                    accent = accentColor,
                    payments = shownPayments,
                    dynamicPlans = dynamicPlans,
                    onBack = onNavigateBack,
                    onSelectPlan = { p ->
                        selectedPlanId = p.id
                        step = PremiumStoreStep.PAYMENT
                    },
                    onGiftActivated = onPremiumActivated
                )
                PremiumStoreStep.PAYMENT -> PaymentStep(
                    accent = accentColor,
                    plan = plan,
                    method = method,
                    trxId = trxId,
                    shopData = shopData,
                    onMethod = { methodName = it.name },
                    onTrx = { trxId = it },
                    onBack = { step = PremiumStoreStep.PLANS },
                    submitting = submitting,
                    onSubmit = { m ->
                        val limitMsg = PremiumPaymentStore.rateLimitMessage(context)
                        if (limitMsg != null) {
                            Toast.makeText(context, limitMsg, Toast.LENGTH_LONG).show()
                        } else if (!submitting) {
                            submitting = true
                            val req = PremiumPaymentRequest(plan.title, plan.price, plan.days, m.title, trxId.trim())
                            onSubmitTrx(
                                plan.title, plan.price, plan.days, m.title, req.trxId,
                                {
                                    submitting = false
                                    PremiumPaymentStore.add(context, req)
                                    localPayments = PremiumPaymentStore.load(context)
                                    step = PremiumStoreStep.PENDING
                                },
                                { errorMsg ->
                                    submitting = false
                                    Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                                }
                            )
                        }
                    }
                )
                PremiumStoreStep.PENDING -> PendingStep(
                    accent = accentColor,
                    plan = plan,
                    method = method,
                    trxId = trxId.trim(),
                    onViewPayments = {
                        trxId = ""
                        methodName = null
                        step = PremiumStoreStep.PLANS
                    },
                    onHome = onNavigateBack
                )
            }
        }
    }
}

// =====================================================================
//  STEP 1 : PLANS
// =====================================================================
@Composable
private fun PlansStep(
    accent: Color,
    payments: List<PremiumPaymentRequest>,
    dynamicPlans: List<PremiumPlanItem>,
    onBack: () -> Unit,
    onSelectPlan: (PremiumPlanItem) -> Unit,
    onGiftActivated: () -> Unit
) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(scroll)
            .padding(horizontal = 20.dp)
    ) {
        StoreTopBar(title = "Lytrix Premium", onBack = onBack)

        val pulse by rememberInfiniteTransition(label = "crown").animateFloat(
            initialValue = 1f,
            targetValue = 1.06f,
            animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "crownScale"
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(132.dp)
                    .background(Brush.radialGradient(listOf(accent.copy(alpha = 0.38f), Color.Transparent)), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(92.dp)
                        .scale(pulse)
                        .clip(CircleShape)
                        .border(1.5.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.8f), accent.copy(alpha = 0.2f))), CircleShape)
                        .background(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.5f)))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.WorkspacePremium, contentDescription = null, tint = readableOn(accent), modifier = Modifier.size(50.dp))
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = buildAnnotatedString {
                    append("Go ")
                    withStyle(SpanStyle(brush = Brush.linearGradient(listOf(Color.White, accent)))) {
                        append("Premium")
                    }
                },
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "No ads, instant downloads, storage export, premium themes & VIP Listen Together.",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }

        if (payments.isNotEmpty()) {
            PaymentsSection(payments = payments.take(5))
            Spacer(Modifier.height(26.dp))
        }

        Text("Free vs Premium", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        ComparisonCard(accent = accent)

        Spacer(Modifier.height(26.dp))
        Text("Choose your plan", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        val basePlan = dynamicPlans.firstOrNull()
        if (basePlan != null) {
            dynamicPlans.forEach { p ->
                PlanCard(plan = p, basePlan = basePlan, accent = accent, onClick = { onSelectPlan(p) })
                Spacer(Modifier.height(12.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        GiftCodeSection(accent = accent, scroll = scroll, onActivated = onGiftActivated)
        Spacer(Modifier.height(14.dp))
        TelegramSupportCard()
        Spacer(Modifier.height(20.dp))
        PaymentNotice()
        Spacer(Modifier.height(32.dp))
    }
}

// =====================================================================
//  STEP 2 : PAYMENT
// =====================================================================
@Composable
private fun PaymentStep(
    accent: Color,
    plan: PremiumPlanItem,
    method: PremiumPayMethod?,
    trxId: String,
    shopData: ShopData,
    onMethod: (PremiumPayMethod) -> Unit,
    onTrx: (String) -> Unit,
    onBack: () -> Unit,
    submitting: Boolean,
    onSubmit: (PremiumPayMethod) -> Unit
) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val activeNumber = if (method?.name == "BKASH") shopData.bkash else shopData.nagad

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        StoreTopBar(title = "Checkout", onBack = onBack)
        Spacer(Modifier.height(8.dp))

        StoreGlassCard(
            modifier = Modifier.fillMaxWidth(),
            tint = accent.copy(alpha = 0.10f),
            borderBrush = Brush.linearGradient(listOf(accent.copy(alpha = 0.8f), accent.copy(alpha = 0.15f)))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("SELECTED PLAN", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Premium · ${plan.title}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("${plan.days} days of full access", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                }
                Text("৳${plan.price}", color = accent, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("Payment method", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PremiumPayMethod.entries.forEach { m ->
                PayMethodCard(m = m, selected = method == m, modifier = Modifier.weight(1f), onClick = { onMethod(m) })
            }
        }

        AnimatedVisibility(
            visible = method != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            if (method != null) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 18.dp)) {
                    StoreGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        tint = method.color.copy(alpha = 0.08f),
                        borderBrush = Brush.linearGradient(listOf(method.color.copy(alpha = 0.7f), method.color.copy(alpha = 0.1f)))
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PayLogo(method = method, size = 36.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "Send Money ৳${plan.price} to this ${method.title} number",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    activeNumber,
                                    color = Color.White,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(method.color.copy(alpha = 0.18f))
                                        .clickable {
                                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            cm.setPrimaryClip(ClipData.newPlainText("number", activeNumber))
                                            Toast.makeText(context, "Number copied", Toast.LENGTH_SHORT).show()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy Number", tint = method.color, modifier = Modifier.size(22.dp))
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Choose “Send Money”, then copy the TrxID from the confirmation message.",
                                color = Color.White.copy(alpha = 0.45f),
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = { openPaymentApp(context, method) },
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = method.color, contentColor = Color.White),
                                modifier = Modifier.fillMaxWidth().height(50.dp)
                            ) {
                                Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Open ${method.title} App", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                    Text("Transaction ID", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = trxId,
                        onValueChange = { raw -> onTrx(raw.filter { c -> c.isLetterOrDigit() && c.code < 128 }.uppercase().take(20)) },
                        label = { Text("TrxID") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
                        colors = storeFieldColors(accent),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        StorePrimaryButton(
            text = if (submitting) "Submitting..." else "Submit TrxID",
            accent = accent,
            enabled = method != null && trxId.length >= 8 && !submitting,
            onClick = { method?.let { onSubmit(it) } }
        )
        Spacer(Modifier.height(32.dp))
    }
}

// =====================================================================
//  STEP 3 : PENDING
// =====================================================================
@Composable
private fun PendingStep(
    accent: Color,
    plan: PremiumPlanItem,
    method: PremiumPayMethod?,
    trxId: String,
    onViewPayments: () -> Unit,
    onHome: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(150.dp)
                .background(Brush.radialGradient(listOf(accent.copy(alpha = 0.28f), Color.Transparent)), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(modifier = Modifier.size(104.dp), color = accent, strokeWidth = 4.dp)
            Icon(Icons.Rounded.HourglassTop, contentDescription = null, tint = accent, modifier = Modifier.size(38.dp))
        }
        Spacer(Modifier.height(28.dp))
        Text("Verifying your payment", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            "Verification in progress. Your premium will be activated automatically once the transaction is verified.",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp,
            lineHeight = 21.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Spacer(Modifier.height(28.dp))

        StoreGlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
                SummaryLine("Plan", "${plan.title} · ৳${plan.price}")
                SummaryLine("Method", method?.title ?: "-")
                SummaryLine("TrxID", trxId)
            }
        }

        Spacer(Modifier.height(32.dp))
        StorePrimaryButton(text = "View my payments", accent = accent, onClick = onViewPayments)
        Spacer(Modifier.height(6.dp))
        TextButton(onClick = onHome) {
            Text("Back to Home", color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp, modifier = Modifier.width(72.dp))
        Text(value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// =====================================================================
//  SHARED COMPONENTS
// =====================================================================
@Composable
private fun PlanCard(plan: PremiumPlanItem, basePlan: PremiumPlanItem, accent: Color, onClick: () -> Unit) {
    val perDay = String.format(Locale.US, "%.1f", plan.price.toFloat() / plan.days)
    val basePerDay = basePlan.price.toFloat() / basePlan.days
    val save = ((1f - (plan.price.toFloat() / plan.days) / basePerDay) * 100f).toInt()

    val shift by rememberInfiniteTransition(label = "bestBorder").animateFloat(
        initialValue = 0f,
        targetValue = 1400f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart),
        label = "bestShift"
    )
    val bestBorder = if (plan.best) {
        Brush.linearGradient(
            colors = listOf(accent.copy(alpha = 0.35f), Color.White.copy(alpha = 0.95f), accent, accent.copy(alpha = 0.35f)),
            start = Offset(shift - 700f, 0f),
            end = Offset(shift, 700f)
        )
    } else null

    StoreGlassCard(
        modifier = Modifier.fillMaxWidth(),
        tint = if (plan.best) accent.copy(alpha = 0.14f) else Color.Transparent,
        borderBrush = bestBorder,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(plan.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    if (plan.best) {
                        Spacer(Modifier.width(8.dp))
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(accent)
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Star, contentDescription = null, tint = readableOn(accent), modifier = Modifier.size(11.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("BEST VALUE", color = readableOn(accent), fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp, maxLines = 1)
                        }
                    }
                }
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("৳$perDay / day", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
                    if (save >= 5) {
                        Spacer(Modifier.width(8.dp))
                        Text("Save $save%", color = CompletedColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Text("৳${plan.price}", color = if (plan.best) accent else Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.width(14.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (plan.best) accent else Color.White.copy(alpha = 0.12f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("Buy", color = if (plan.best) readableOn(accent) else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private val FreeColW = 76.dp
private val PremiumColW = 104.dp

@Composable
private fun ComparisonCard(accent: Color) {
    StoreGlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Text("Compare", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, modifier = Modifier.weight(1f).padding(start = 16.dp, top = 14.dp, bottom = 14.dp))
            Box(Modifier.width(FreeColW), contentAlignment = Alignment.Center) {
                Text("FREE", color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
            Box(Modifier.width(PremiumColW).fillMaxHeight().background(accent.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.WorkspacePremium, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("PREMIUM", color = accent, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp, maxLines = 1)
                }
            }
        }

        StoreDivider()
        CompareLine(Icons.Rounded.Block, "Advertisements", "Ads shown", "No ads", accent)
        StoreDivider()
        CompareLine(Icons.Rounded.Bolt, "Download speed", "10-20s ad", "Instant (0s)", accent)
        StoreDivider()
        CompareLine(Icons.Rounded.Download, "Save to storage", "Locked", "Unlocked", accent)
        StoreDivider()
        CompareLine(Icons.Rounded.Palette, "Premium themes", "Locked", "All Unlocked", accent)
        StoreDivider()

        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Row(Modifier.weight(1f).padding(start = 16.dp, top = 14.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Headphones, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("LISTEN TOGETHER", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
            Box(Modifier.width(FreeColW))
            Box(Modifier.width(PremiumColW).fillMaxHeight().background(accent.copy(alpha = 0.10f)))
        }
        CompareLine(Icons.Rounded.WorkspacePremium, "VIP Lounge access", "Locked", "Unlocked", accent)
        StoreDivider()
        CompareLine(Icons.Rounded.Chat, "Live Chat & Voice", "Locked", "Unlocked", accent)
    }
}

@Composable
private fun CompareLine(icon: ImageVector, label: String, free: String, premium: String, accent: Color) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp, end = 8.dp, top = 13.dp, bottom = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        Box(Modifier.width(FreeColW), contentAlignment = Alignment.Center) {
            Text(free, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        }
        Box(Modifier.width(PremiumColW).fillMaxHeight().background(accent.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
            Text(premium, color = accent, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun StoreDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.06f)))
}

@Composable
private fun PaymentsSection(payments: List<PremiumPaymentRequest>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Your payments", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        payments.forEachIndexed { i, req ->
            PaymentRequestCard(req)
            if (i < payments.lastIndex) Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun PaymentRequestCard(req: PremiumPaymentRequest) {
    val isPending = req.status == PremiumPaymentStatus.PENDING
    val statusColor = when (req.status) {
        PremiumPaymentStatus.PENDING -> PendingColor
        PremiumPaymentStatus.COMPLETED -> CompletedColor
        PremiumPaymentStatus.DECLINED -> DeclinedColor
    }
    val method = PremiumPayMethod.entries.firstOrNull { it.title.equals(req.method, ignoreCase = true) }
    val dateText = if (req.createdAt > 0L) SimpleDateFormat("dd MMM, hh:mm a", Locale.US).format(Date(req.createdAt)) else ""

    StoreGlassCard(
        modifier = Modifier.fillMaxWidth(),
        tint = statusColor.copy(alpha = 0.06f),
        borderBrush = Brush.linearGradient(listOf(statusColor.copy(alpha = 0.55f), statusColor.copy(alpha = 0.08f)))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PayLogo(method = method, size = 46.dp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Premium · ${req.planTitle}",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "৳${req.priceBdt} · ${req.method}",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                StatusChip(status = req.status, color = statusColor)
            }
            Spacer(Modifier.height(12.dp))
            StoreDivider()
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("TrxID", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, modifier = Modifier.width(52.dp))
                Text(
                    req.trxId,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (dateText.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Time", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, modifier = Modifier.width(52.dp))
                    Text(dateText, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                }
            }
            if (isPending) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(50)),
                    color = PendingColor
                )
            }
        }
    }
}

@Composable
private fun StatusChip(status: PremiumPaymentStatus, color: Color) {
    val dotAlpha by rememberInfiniteTransition(label = "dot").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "dotAlpha"
    )
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (status) {
            PremiumPaymentStatus.PENDING -> Box(Modifier.size(7.dp).clip(CircleShape).background(color.copy(alpha = dotAlpha)))
            PremiumPaymentStatus.COMPLETED -> Icon(Icons.Rounded.Check, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
            PremiumPaymentStatus.DECLINED -> Icon(Icons.Rounded.Close, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
        }
        Spacer(Modifier.width(6.dp))
        Text(
            when (status) {
                PremiumPaymentStatus.PENDING -> "Pending"
                PremiumPaymentStatus.COMPLETED -> "Completed"
                PremiumPaymentStatus.DECLINED -> "Declined"
            },
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun GiftCodeSection(accent: Color, scroll: ScrollState, onActivated: () -> Unit) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    var code by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var isOk by remember { mutableStateOf(false) }
    val arrow by animateFloatAsState(if (expanded) 180f else 0f, label = "giftArrow")

    LaunchedEffect(expanded) {
        if (expanded) {
            delay(320)
            scroll.animateScrollTo(scroll.maxValue)
        }
    }

    StoreGlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.CardGiftcard, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Use gift code", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Have an activation code? Redeem it here", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
            }
            Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.rotate(arrow))
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, bottom = 18.dp)
            ) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { raw ->
                        code = raw.filter { c -> (c.isLetterOrDigit() && c.code < 128) || c == '-' || c == '_' }.uppercase().take(32)
                        message = ""
                    },
                    placeholder = { Text("ENTER CODE", fontSize = 14.sp, color = Color.White.copy(alpha = 0.35f)) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
                    colors = storeFieldColors(accent),
                    modifier = Modifier.fillMaxWidth()
                )
                if (message.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(message, color = if (isOk) accent else Color(0xFFFF6B6B), fontSize = 13.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = {
                        keyboard?.hide()
                        loading = true
                        message = "Verifying..."
                        isOk = false
                    },
                    enabled = !loading && code.isNotBlank(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = readableOn(accent),
                        disabledContainerColor = Color.White.copy(alpha = 0.08f),
                        disabledContentColor = Color.White.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    if (loading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = readableOn(accent))
                    } else {
                        Text("REDEEM", fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun PayMethodCard(m: PremiumPayMethod, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    StoreGlassCard(
        modifier = modifier,
        tint = if (selected) m.color.copy(alpha = 0.16f) else Color.Transparent,
        borderBrush = if (selected) Brush.linearGradient(listOf(m.color, m.color.copy(alpha = 0.5f))) else null,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PayLogo(method = m, size = 42.dp)
            Spacer(Modifier.width(10.dp))
            Text(m.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
            if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = m.color, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun PayLogo(method: PremiumPayMethod?, size: Dp) {
    val ctx = LocalContext.current
    val resId = remember(method) {
        if (method == null) 0 else ctx.resources.getIdentifier(method.logoName, "drawable", ctx.packageName)
    }
    val shape = RoundedCornerShape(size * 0.28f)
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(if (resId != 0) Color.White else (method?.color ?: Color.Gray)),
        contentAlignment = Alignment.Center
    ) {
        if (resId != 0) {
            Image(
                painter = painterResource(resId),
                contentDescription = method?.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(size * 0.1f)
            )
        } else {
            Text(method?.letter ?: "৳", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = (size.value * 0.45f).sp)
        }
    }
}

private fun openPaymentApp(context: Context, method: PremiumPayMethod) {
    try {
        val intent = context.packageManager.getLaunchIntentForPackage(method.packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } else {
            Toast.makeText(context, "${method.title} app is not installed", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Could not open ${method.title}", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun StoreGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    tint: Color = Color.Transparent,
    borderBrush: Brush? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val border = borderBrush ?: Brush.linearGradient(listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.04f)))
    Column(
        modifier = modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.03f))))
            .background(tint)
            .border(1.dp, border, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        content = content
    )
}

@Composable
private fun StoreTopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
                .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StorePrimaryButton(text: String, accent: Color, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = accent,
            contentColor = readableOn(accent),
            disabledContainerColor = Color.White.copy(alpha = 0.08f),
            disabledContentColor = Color.White.copy(alpha = 0.35f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp)
    }
}

@Composable
private fun storeFieldColors(accent: Color) = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = accent,
    unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
    focusedLabelColor = accent,
    unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
    cursorColor = accent,
    focusedContainerColor = Color.White.copy(alpha = 0.05f),
    unfocusedContainerColor = Color.White.copy(alpha = 0.05f)
)
// =====================================================================
//  SUPPORT (Telegram) + PAYMENT NOTICE
// =====================================================================
private const val SUPPORT_TELEGRAM_URL = "https://t.me/lytrixsupport"
private val TelegramBlue = Color(0xFF2AABEE)
private val TelegramBlueDark = Color(0xFF229ED9)

@Composable
private fun TelegramSupportCard() {
    val context = LocalContext.current
    StoreGlassCard(
        modifier = Modifier.fillMaxWidth(),
        tint = TelegramBlue.copy(alpha = 0.08f),
        borderBrush = Brush.linearGradient(
            listOf(TelegramBlue.copy(alpha = 0.7f), TelegramBlue.copy(alpha = 0.10f))
        ),
        onClick = { openSupportLink(context, SUPPORT_TELEGRAM_URL) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TelegramLogo(diameter = 44.dp)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Need help?", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Chat with us on Telegram",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(TelegramBlue)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("Chat", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TelegramLogo(diameter: Dp) {
    Box(
        modifier = Modifier
            .size(diameter)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(TelegramBlue, TelegramBlueDark))),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val plane = Path().apply {
                moveTo(w * 0.20f, h * 0.49f)
                lineTo(w * 0.78f, h * 0.26f)
                lineTo(w * 0.66f, h * 0.76f)
                lineTo(w * 0.47f, h * 0.62f)
                lineTo(w * 0.38f, h * 0.72f)
                lineTo(w * 0.37f, h * 0.58f)
                close()
            }
            drawPath(plane, Color.White)
            drawLine(
                color = TelegramBlueDark,
                start = Offset(w * 0.37f, h * 0.58f),
                end = Offset(w * 0.70f, h * 0.34f),
                strokeWidth = w * 0.03f,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun PaymentNotice() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.Lock,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.4f),
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "Payments are verified manually. Activation may take some time.",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 11.sp,
                lineHeight = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.7f))) {
                    append("Payments are non-refundable.")
                }
                append(" By making a payment, you agree to all our terms and conditions.")
            },
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 11.sp,
            lineHeight = 16.sp,
            textAlign = TextAlign.Center
        )
    }
}

private fun openSupportLink(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Could not open link", Toast.LENGTH_SHORT).show()
    }
}