package com.musicplayer.lytrix.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.database.FirebaseDatabase
import com.musicplayer.lytrix.BuildConfig
import com.musicplayer.lytrix.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class AppUpdateInfo(
    val versionCode: Long = 0L,
    val versionName: String = "",
    val changelog: String = "",
    val downloadUrl: String = "",
    val forceUpdate: Boolean = false
)

object AppUpdateManager {

    private const val CHANNEL_ID = "app_updates_channel"
    private const val NOTIFICATION_ID = 9001
    private const val PREFS_NAME = "app_update_preferences"
    private const val KEY_DISMISSED_VERSION = "dismissed_version_code"
    private const val CHECK_INTERVAL_MILLIS = 3 * 60 * 60 * 1000L // 3 Hours

    private const val RTDB_URL = "https://lytrix-player-default-rtdb.asia-southeast1.firebasedatabase.app"

    var lastCheckTime: Long = 0L
        private set

    var cachedUpdateInfo: AppUpdateInfo? = null
        private set

    /**
     * Firebase Realtime Database থেকে app_update নোড চেক করে।
     * forceRefresh = true দিলে ক্যাশ বাইপাস করে সরাসরি ফায়ারবেস থেকে নতুন ডাটা আনবে।
     */
    suspend fun checkForUpdate(forceRefresh: Boolean = false): Result<Pair<AppUpdateInfo?, Boolean>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val currentTime = System.currentTimeMillis()
                val shouldFetch = forceRefresh || (currentTime - lastCheckTime > CHECK_INTERVAL_MILLIS)

                if (!shouldFetch && cachedUpdateInfo != null) {
                    val info = cachedUpdateInfo!!
                    val hasUpdate = isUpdateAvailable(info.versionCode)
                    return@runCatching (info to hasUpdate)
                }

                val dbRef = try {
                    FirebaseDatabase.getInstance(RTDB_URL).reference.child("app_update")
                } catch (_: Exception) {
                    FirebaseDatabase.getInstance().reference.child("app_update")
                }

                val snapshot = dbRef.get().await()

                if (!snapshot.exists()) {
                    return@runCatching (null to false)
                }

                val versionCodeRaw = snapshot.child("versionCode").value
                val versionCode = when (versionCodeRaw) {
                    is Number -> versionCodeRaw.toLong()
                    is String -> versionCodeRaw.toLongOrNull() ?: 0L
                    else -> 0L
                }

                val versionName = snapshot.child("versionName").getValue(String::class.java) ?: ""
                val changelog = snapshot.child("changelog").getValue(String::class.java) ?: ""
                val downloadUrl = snapshot.child("downloadUrl").getValue(String::class.java) ?: ""
                val forceUpdate = snapshot.child("forceUpdate").getValue(Boolean::class.java) ?: false

                val info = AppUpdateInfo(
                    versionCode = versionCode,
                    versionName = versionName,
                    changelog = changelog,
                    downloadUrl = downloadUrl,
                    forceUpdate = forceUpdate
                )

                cachedUpdateInfo = info
                lastCheckTime = currentTime

                val hasUpdate = isUpdateAvailable(info.versionCode)
                (info to hasUpdate)
            }
        }

    /**
     * সার্ভারের ভার্সন কোড বর্তমান অ্যাপের ভার্সন কোডের চেয়ে বড় কিনা তা তুলনা করে।
     */
    fun isUpdateAvailable(serverVersionCode: Long): Boolean {
        val currentVersionCode = BuildConfig.VERSION_CODE.toLong()
        return serverVersionCode > currentVersionCode
    }

    /**
     * নির্দিষ্ট ভার্সনটি ইউজার আগে 'Later' দিয়ে ডিসমিস করেছিল কিনা চেক করে।
     */
    fun isVersionDismissed(context: Context, versionCode: Long): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_DISMISSED_VERSION, -1L) == versionCode
    }

    /**
     * ইউজার 'Later' চাপলে ঐ ভার্সনটি সেভ করে রাখে যাতে বারবার ডিস্টার্ব না করে।
     */
    fun dismissVersion(context: Context, versionCode: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_DISMISSED_VERSION, versionCode).apply()
    }

    /**
     * অ্যান্ড্রয়েডের নোটিফিকেশন চ্যানেলে Meld এর মতো সুন্দর সিস্টেম নোটিফিকেশন পাঠায়।
     */
    fun showUpdateNotification(context: Context, updateInfo: AppUpdateInfo) {
        try {
            createNotificationChannel(context)

            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo.downloadUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val pendingIntent = PendingIntent.getActivity(context, NOTIFICATION_ID, intent, flags)

            val title = if (updateInfo.versionName.isNotBlank()) {
                "Lytrix Player v${updateInfo.versionName} Available"
            } else {
                "Lytrix Player Update Available"
            }

            val text = "A new version is ready to install. Tap to download."

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_update_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "App Updates"
            val descriptionText = "Notifications for new versions and upgrades"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * সরাসরি ব্রাউজার বা ডাউনলোডারে APK ডাউনলোড লিংক ওপেন করে।
     */
    fun openDownloadUrl(context: Context, url: String) {
        if (url.isBlank()) {
            Toast.makeText(context, "Download link not available", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open download link: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
