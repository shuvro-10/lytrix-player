package com.musicplayer.lytrix.ui

import android.app.Application
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.database.FirebaseDatabase
import com.musicplayer.lytrix.db.AppDatabase
import com.musicplayer.lytrix.db.ThemePreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val playtimeDao = database.playtimeDao()
    private val themePrefs = ThemePreferencesManager(application)
    private val feedbackDao = database.feedbackDao()

    val feedbackHistory = feedbackDao.getAllFeedbacks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val detailedPrefs = application.getSharedPreferences("detailed_playtime", Context.MODE_PRIVATE)

    // 🔥 Firebase
    private val auth = FirebaseAuth.getInstance()
    private val databaseRef = FirebaseDatabase.getInstance().reference

    private val _isUserLoggedIn = MutableStateFlow(false)
    val isUserLoggedIn: StateFlow<Boolean> = _isUserLoggedIn.asStateFlow()

    private val _userEmail = MutableStateFlow("")
    val userEmail: StateFlow<String> = _userEmail.asStateFlow()

    private val _userName = MutableStateFlow("Lytrix User")
    val userName: StateFlow<String> = _userName.asStateFlow()

    // NEW: login method = "google" or "password"
    private val _loginMethod = MutableStateFlow("")
    val loginMethod: StateFlow<String> = _loginMethod.asStateFlow()

    private val _isEmailLoading = MutableStateFlow(false)
    val isEmailLoading: StateFlow<Boolean> = _isEmailLoading.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _resendCooldown = MutableStateFlow(0L)
    val resendCooldown: StateFlow<Long> = _resendCooldown.asStateFlow()

    private var lastResendTime = 0L

    private val _audioPlaytimeMs = MutableStateFlow(detailedPrefs.getLong("audio_ms", 0L))
    val audioPlaytimeMs: StateFlow<Long> = _audioPlaytimeMs.asStateFlow()

    private val _videoPlaytimeMs = MutableStateFlow(detailedPrefs.getLong("video_ms", 0L))
    val videoPlaytimeMs: StateFlow<Long> = _videoPlaytimeMs.asStateFlow()

    private val _weeklyPlaytimeMs = MutableStateFlow(detailedPrefs.getLong("weekly_ms", 0L))
    val weeklyPlaytimeMs: StateFlow<Long> = _weeklyPlaytimeMs.asStateFlow()

    init {
        // Guard: যদি unverified password user থেকে থাকে, সাথে সাথে signOut
        checkAndRefreshAuthState()

        // Auth state listener
        auth.addAuthStateListener {
            checkAndRefreshAuthState()
        }

        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                val lastReset = detailedPrefs.getLong("week_reset_time", 0L)
                val currentTime = System.currentTimeMillis()
                if (lastReset == 0L || currentTime - lastReset > 7L * 24 * 60 * 60 * 1000L) {
                    detailedPrefs.edit().putLong("weekly_ms", 0L).putLong("week_reset_time", currentTime).apply()
                }
                _audioPlaytimeMs.value = detailedPrefs.getLong("audio_ms", 0L)
                _videoPlaytimeMs.value = detailedPrefs.getLong("video_ms", 0L)
                _weeklyPlaytimeMs.value = detailedPrefs.getLong("weekly_ms", 0L)
                kotlinx.coroutines.delay(1000)
            }
        }

        // Cooldown ticker
        viewModelScope.launch {
            while (true) {
                val remaining = (lastResendTime + 60_000 - System.currentTimeMillis()) / 1000
                _resendCooldown.value = if (remaining > 0) remaining else 0
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    private fun checkAndRefreshAuthState() {
        val user = auth.currentUser
        if (user == null) {
            _isUserLoggedIn.value = false
            _userEmail.value = ""
            _userName.value = "Lytrix User"
            _loginMethod.value = ""
            return
        }

        // ONE USER = ONE METHOD guard: password user কিন্তু emailVerified false হলে signOut
        val isPasswordProvider = user.providerData.any { it.providerId == "password" }
        if (isPasswordProvider && !user.isEmailVerified) {
            auth.signOut()
            _isUserLoggedIn.value = false
            _userEmail.value = ""
            _userName.value = "Lytrix User"
            _loginMethod.value = ""
            return
        }

        _isUserLoggedIn.value = true
        _userEmail.value = user.email ?: ""
        _userName.value = user.displayName ?: "Lytrix User"
        _loginMethod.value = when {
            user.providerData.any { it.providerId == "google.com" } -> "google"
            user.providerData.any { it.providerId == "password" } -> "password"
            else -> user.providerId ?: ""
        }
    }

    val totalPlaytimeMs: StateFlow<Long> = playtimeDao.getTotalPlaytimeMs()
        .map { it ?: 0L }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val appThemeMode: StateFlow<String> = themePrefs.appThemeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Dark")

    val languageCode: StateFlow<String> = themePrefs.languageCode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "en")

    val accentColor: StateFlow<String> = themePrefs.accentColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "0xFF3182CE")

    val gridViewSize: StateFlow<Int> = themePrefs.gridViewSize
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)

    val customBackgroundUri: StateFlow<String> = themePrefs.customBackgroundUri
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    // ==========================================
    // 🔥 Google Login (Existing - Unchanged logic, only added method check)
    // ==========================================
    fun signInWithGoogle(context: Context, webClientId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val credentialManager = CredentialManager.create(context)
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(webClientId)
                    .setAutoSelectEnabled(false)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = credentialManager.getCredential(context, request)
                val credential = result.credential

                if (credential is androidx.credentials.CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val idToken = googleIdTokenCredential.idToken
                    val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                    val authResult = auth.signInWithCredential(firebaseCredential).await()

                    if (authResult.user != null) {
                        // Success - refresh will handle provider check
                        checkAndRefreshAuthState()
                        onSuccess()
                    } else {
                        onError("Firebase Authentication Failed")
                    }
                } else {
                    onError("Unexpected credential type: ${credential.type}")
                }
            } catch (e: Exception) {
                // IMPORTANT: ONE USER = ONE METHOD enforcement
                // যদি এই Gmail আগে Email/Password দিয়ে তৈরি হয়ে থাকে, Firebase error দেবে
                val msg = e.localizedMessage ?: ""
                if (e is FirebaseAuthUserCollisionException || msg.contains("already in use", ignoreCase = true) || msg.contains("different credential", ignoreCase = true) || msg.contains("ACCOUNT_EXISTS", ignoreCase = true)) {
                    onError("This email is already registered with Email login. Please Sign in with Email.")
                } else {
                    val errorType = e::class.java.simpleName
                    onError("$errorType: $msg")
                }
            }
        }
    }

    // ==========================================
    // 🔥 NEW: Email/Password Registration (ONE USER = ONE UID = ONE METHOD)
    // ==========================================
    fun registerWithEmail(name: String, email: String, password: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        if (name.isBlank()) { onError("Please enter your name"); return }
        if (password.length < 6) { onError("Password must be at least 6 characters"); return }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) { onError("Invalid email address"); return }

        viewModelScope.launch {
            _isEmailLoading.value = true
            _authError.value = null
            try {
                val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
                val user = result.user ?: throw Exception("Account creation failed")

                // Name set
                val profileUpdates = UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build()
                user.updateProfile(profileUpdates).await()

                // Send verification email (uses Firebase default template)
                user.sendEmailVerification().await()
                lastResendTime = System.currentTimeMillis()

                // CRITICAL: সাথে সাথে signOut - unverified কে login রাখা যাবে না, নাহলে performSilentSync ভাববে login আছে
                auth.signOut()
                checkAndRefreshAuthState()
                _isEmailLoading.value = false
                onSuccess("Verification email sent to $email. Please verify and then Sign in.")

            } catch (e: Exception) {
                _isEmailLoading.value = false
                if (e is FirebaseAuthUserCollisionException) {
                    // এই email আগে Google দিয়ে তৈরি
                    onError("This email is already registered with Google. Please Sign in with Google.")
                } else {
                    onError(e.localizedMessage ?: "Registration failed")
                }
            }
        }
    }

    // ==========================================
    // 🔥 NEW: Email/Password Sign In with verification check
    // ==========================================
    fun signInWithEmail(email: String, password: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) { onError("Invalid email"); return }
        if (password.isBlank()) { onError("Please enter password"); return }

        viewModelScope.launch {
            _isEmailLoading.value = true
            try {
                val result = auth.signInWithEmailAndPassword(email.trim(), password).await()
                val user = result.user ?: throw Exception("Sign in failed")
                user.reload().await()

                if (!user.isEmailVerified) {
                    auth.signOut()
                    checkAndRefreshAuthState()
                    _isEmailLoading.value = false
                    onError("Email not verified. Please check inbox or spam folder and verify first.")
                    return@launch
                }

                // Verified হলেই কেবল success
                checkAndRefreshAuthState()
                _isEmailLoading.value = false
                onSuccess()

            } catch (e: Exception) {
                _isEmailLoading.value = false
                onError(e.localizedMessage ?: "Login failed")
            }
        }
    }
    // ==========================================
    // 🔥 NEW: Resend verification with 60s cooldown
    // ==========================================
    fun resendVerificationEmail(email: String, password: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        if (_resendCooldown.value > 0) {
            onError("Please wait ${_resendCooldown.value}s before resending.")
            return
        }
        viewModelScope.launch {
            try {
                // Temporarily sign in to send verification
                val result = auth.signInWithEmailAndPassword(email.trim(), password).await()
                val user = result.user
                if (user == null) { onError("Account not found"); return@launch }
                user.reload().await()
                if (user.isEmailVerified) {
                    auth.signOut()
                    onError("Email already verified. Please Sign in.")
                    return@launch
                }
                user.sendEmailVerification().await()
                lastResendTime = System.currentTimeMillis()
                auth.signOut()
                checkAndRefreshAuthState()
                onSuccess("Verification email resent to $email")
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Failed to resend")
            }
        }
    }

    // ==========================================
    // 🔥 NEW: Password Reset Email via Firebase
    // ==========================================
    fun sendPasswordResetEmail(email: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        val trimmedEmail = email.trim()
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
            onError("Please enter a valid email address.")
            return
        }
        viewModelScope.launch {
            _isEmailLoading.value = true
            try {
                auth.sendPasswordResetEmail(trimmedEmail).await()
                _isEmailLoading.value = false
                onSuccess("Password reset link sent to $trimmedEmail. Please check your inbox or spam folder.")
            } catch (e: Exception) {
                _isEmailLoading.value = false
                onError(e.localizedMessage ?: "Failed to send reset email.")
            }
        }
    }

    fun clearAuthError() { _authError.value = null }

    fun signOut() {
        auth.signOut()
        _isUserLoggedIn.value = false
        _userEmail.value = ""
        _userName.value = ""
        _loginMethod.value = ""
        _authError.value = null
    }

    // ==========================================
    // Backup / Restore (unchanged)
    // ==========================================
    fun uploadBackupToFirebase(backupData: JSONObject, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val uid = auth.currentUser?.uid
        if (uid == null) { onError("You must be signed in to sync to cloud!"); return }
        val json = backupData.toString()
        if (json.length > 9_000_000) { onError("Backup too large (${json.length / 1_000_000}MB). Delete some history."); return }
        val liked = backupData.optJSONObject("online_liked_songs")?.optJSONArray("liked_ids")?.length() ?: 0
        val pls = backupData.optJSONObject("online_playlists_meta")?.optJSONArray("names")?.length() ?: 0
        val dl = backupData.optJSONObject("downloaded_meta")?.length() ?: 0
        if (liked == 0 && pls == 0 && dl == 0) { onError("No data to backup! Save some songs first."); return }
        viewModelScope.launch {
            try {
                val ref = databaseRef.child("users").child(uid)
                val old = ref.child("backup_data").get().await().value as? String
                if (old != null) ref.child("backup_data_prev").setValue(old).await()
                ref.child("backup_data").setValue(json).await()
                onSuccess()
            } catch (e: Exception) { onError(e.message ?: "Failed to upload data to cloud.") }
        }
    }
    fun downloadBackupFromFirebase(onSuccess: (JSONObject) -> Unit, onError: (String) -> Unit) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onError("You must be signed in to restore data!")
            return
        }
        viewModelScope.launch {
            try {
                val snapshot = databaseRef.child("users").child(uid).child("backup_data").get().await()
                if (snapshot.exists()) {
                    val rawString = snapshot.value as? String
                    if (rawString != null) {
                        val jsonString = try { gzipDecompress(rawString) } catch (_: Exception) { rawString }
                        onSuccess(JSONObject(jsonString))
                    } else {
                        onError("Backup data is corrupted.")
                    }
                } else {
                    onError("No backup found in your cloud account.")
                }
            } catch (e: Exception) {
                onError(e.message ?: "Failed to download data.")
            }
        }
    }

    fun setLanguageCode(code: String) { viewModelScope.launch(Dispatchers.IO) { themePrefs.saveLanguageCode(code) } }
    fun setAppThemeMode(mode: String) { viewModelScope.launch(Dispatchers.IO) { themePrefs.saveAppThemeMode(mode) } }
    fun setAccentColor(colorHex: String) { viewModelScope.launch(Dispatchers.IO) { themePrefs.saveAccentColor(colorHex) } }
    fun setGridViewSize(size: Int) { viewModelScope.launch(Dispatchers.IO) { themePrefs.saveGridViewSize(size) } }
    fun setCustomBackgroundUri(uri: String) { viewModelScope.launch(Dispatchers.IO) { themePrefs.saveCustomBackgroundUri(uri) } }

    fun submitFeedback(category: String, adIssue: String, details: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val uid = auth.currentUser?.uid ?: "anonymous_${java.util.UUID.randomUUID().toString().substring(0, 8)}"
        val feedbackId = java.util.UUID.randomUUID().toString()
        val currentTime = System.currentTimeMillis()
        val feedbackData = hashMapOf(
            "userId" to uid,
            "category" to category,
            "adIssue" to adIssue,
            "details" to details,
            "timestamp" to currentTime,
            "status" to "Pending"
        )
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("feedbacks")
            .document(feedbackId)
            .set(feedbackData)
            .addOnSuccessListener {
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val currentCount = feedbackDao.getFeedbackCount()
                        if (currentCount >= 10) { feedbackDao.deleteOldestFeedback() }
                        feedbackDao.insertFeedback(
                            com.musicplayer.lytrix.db.FeedbackEntity(id = feedbackId, category = category, details = if (category == "ADs") adIssue else details, timestamp = currentTime)
                        )
                        kotlinx.coroutines.withContext(Dispatchers.Main) { onSuccess() }
                    } catch (e: Exception) {
                        kotlinx.coroutines.withContext(Dispatchers.Main) { onError(e.localizedMessage ?: "Database Error") }
                    }
                }
            }
            .addOnFailureListener { e -> onError(e.localizedMessage ?: "Failed to upload feedback") }
    }

    fun submitPremiumPayment(planTitle: String, priceBdt: Int, days: Int, method: String, trxId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onError("Please sign in first to buy premium.") // Changed from Google only
            return
        }
        val userName = auth.currentUser?.displayName ?: "Lytrix User"
        val userEmail = auth.currentUser?.email ?: "No Email"
        val requestData = hashMapOf<String, Any>(
            "uid" to uid, "email" to userEmail, "name" to userName,
            "plan" to planTitle, "price" to priceBdt, "days" to days,
            "method" to method, "trxId" to trxId, "status" to "PENDING",
            "timestamp" to com.google.firebase.database.ServerValue.TIMESTAMP
        )
        val updates = hashMapOf<String, Any>(
            "pending_payments/$trxId" to requestData,
            "payment_limits/$uid/last_submit" to com.google.firebase.database.ServerValue.TIMESTAMP
        )
        viewModelScope.launch {
            val error: String? = try { databaseRef.updateChildren(updates).await(); null } catch (e: Exception) { explainSubmitError(trxId, e) }
            if (error == null) { onSuccess(); sendWebhookToMakeCom(planTitle, priceBdt, method, trxId, userName, userEmail) } else { onError(error) }
        }
    }

    private suspend fun explainSubmitError(trxId: String, e: Exception): String {
        val isPermissionError = e.message?.contains("permission", ignoreCase = true) == true
        if (!isPermissionError) { return e.localizedMessage ?: "Network Error! Failed to submit." }
        return try {
            val existing = databaseRef.child("pending_payments").child(trxId).get().await()
            if (existing.exists()) { "This TrxID has already been used. Please check it and try again." } else { "Please wait 2 minutes between submissions." }
        } catch (e2: Exception) { "Could not submit right now. Please try again in a couple of minutes." }
    }

    private fun sendWebhookToMakeCom(plan: String, price: Int, method: String, trxId: String, name: String, email: String) {
        Thread {
            try {
                val url = java.net.URL("https://hook.us2.make.com/h8sp05krctn8dsfvz5a9c4wthy7lq9wi")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                val jsonParam = org.json.JSONObject()
                jsonParam.put("plan", plan); jsonParam.put("price", price); jsonParam.put("method", method); jsonParam.put("trxId", trxId); jsonParam.put("name", name); jsonParam.put("email", email)
                val os = java.io.OutputStreamWriter(conn.outputStream)
                os.write(jsonParam.toString()); os.flush(); os.close()
                println("Make.com Webhook Response: ${conn.responseCode}")
            } catch (e: Exception) { e.printStackTrace() }
        }.start()
    }
}
