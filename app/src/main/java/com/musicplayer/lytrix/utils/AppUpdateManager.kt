package com.musicplayer.lytrix.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.musicplayer.lytrix.BuildConfig
import com.musicplayer.lytrix.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class AppUpdateInfo(
    val versionCode: Long = 0L,
    val versionName: String = "",
    val currentVersion: String = BuildConfig.VERSION_NAME,
    val title: String = "",
    val changelog: String = "",
    val downloadUrl: String = "",
    val apkSize: Long = 0L,
    val apkName: String = "",
    val htmlUrl: String = "",
    val forceUpdate: Boolean = false
)

sealed class DownloadProgressState {
    object Idle : DownloadProgressState()
    data class Downloading(val progress: Float, val downloadedBytes: Long, val totalBytes: Long) : DownloadProgressState()
    data class Completed(val file: File) : DownloadProgressState()
    data class Error(val message: String) : DownloadProgressState()
}

object AppUpdateManager {

    private const val GITHUB_REPO = "shuvro-10/lytrix-player"
    private const val GITHUB_API_URL = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"

    private const val CHANNEL_ID = "app_updates_channel"
    private const val NOTIFICATION_ID = 9001
    private const val PREFS_NAME = "app_update_preferences"
    private const val KEY_DISMISSED_VERSION = "dismissed_version_name"
    private const val CHECK_INTERVAL_MILLIS = 3 * 60 * 60 * 1000L // 3 Hours cache

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    var lastCheckTime: Long = 0L
        private set

    var cachedUpdateInfo: AppUpdateInfo? = null
        private set

    /**
     * Checks for the latest release directly from GitHub Releases API.
     * forceRefresh = true bypasses the 3-hour cache and hits GitHub immediately.
     */
    suspend fun checkForUpdate(forceRefresh: Boolean = false): Result<Pair<AppUpdateInfo?, Boolean>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val currentTime = System.currentTimeMillis()
                val shouldFetch = forceRefresh || (currentTime - lastCheckTime > CHECK_INTERVAL_MILLIS)

                if (!shouldFetch && cachedUpdateInfo != null) {
                    val info = cachedUpdateInfo!!
                    val hasUpdate = isUpdateAvailable(info.versionName)
                    return@runCatching (info to hasUpdate)
                }

                val request = Request.Builder()
                    .url(GITHUB_API_URL)
                    .header("User-Agent", "Lytrix-Player/${BuildConfig.VERSION_NAME}")
                    .header("Accept", "application/vnd.github.v3+json")
                    .build()

                val responseStr = httpClient.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        throw IllegalStateException("GitHub API returned HTTP ${resp.code}")
                    }
                    resp.body?.string() ?: throw IllegalStateException("Empty response body from GitHub")
                }

                val json = JSONObject(responseStr)
                val rawTag = json.optString("tag_name", "")
                val cleanVersion = rawTag.removePrefix("v").removePrefix("V").trim()
                val releaseTitle = json.optString("name", "Lytrix Player v$cleanVersion")
                val changelog = json.optString("body", "").trim()
                val htmlUrl = json.optString("html_url", "https://github.com/$GITHUB_REPO/releases")

                // Locate the .apk asset in the release
                val assets = json.optJSONArray("assets")
                var apkUrl = ""
                var apkSize = 0L
                var apkName = ""

                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.optJSONObject(i) ?: continue
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            apkUrl = asset.optString("browser_download_url", "")
                            apkSize = asset.optLong("size", 0L)
                            apkName = name
                            // Prefer arm64 or universal apk if multiple present
                            if (name.contains("arm64", ignoreCase = true) || name.contains("universal", ignoreCase = true)) {
                                break
                            }
                        }
                    }
                }

                if (apkUrl.isBlank()) {
                    apkUrl = htmlUrl // fallback to GitHub release page if no direct APK uploaded yet
                }

                val info = AppUpdateInfo(
                    versionCode = 0L,
                    versionName = cleanVersion,
                    currentVersion = BuildConfig.VERSION_NAME,
                    title = releaseTitle,
                    changelog = changelog,
                    downloadUrl = apkUrl,
                    apkSize = apkSize,
                    apkName = apkName,
                    htmlUrl = htmlUrl,
                    forceUpdate = false
                )

                cachedUpdateInfo = info
                lastCheckTime = currentTime

                val hasUpdate = isUpdateAvailable(cleanVersion)
                (info to hasUpdate)
            }
        }

    /**
     * Compares semantic versions (e.g. "1.0.3" > "1.0.2")
     */
    fun isUpdateAvailable(serverVersionName: String): Boolean {
        if (serverVersionName.isBlank()) return false
        val currentVer = BuildConfig.VERSION_NAME.removePrefix("v").removePrefix("V").substringBefore("-").trim()
        val targetVer = serverVersionName.removePrefix("v").removePrefix("V").substringBefore("-").trim()

        val currParts = currentVer.split(".").mapNotNull { it.toIntOrNull() }
        val targetParts = targetVer.split(".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(currParts.size, targetParts.size)
        for (i in 0 until maxLen) {
            val c = currParts.getOrElse(i) { 0 }
            val t = targetParts.getOrElse(i) { 0 }
            if (t > c) return true
            if (t < c) return false
        }
        return false
    }

    fun isUpdateAvailable(serverVersionCode: Long): Boolean {
        return cachedUpdateInfo?.let { isUpdateAvailable(it.versionName) } ?: false
    }

    /**
     * Checks if this version was previously dismissed with 'Later'
     */
    fun isVersionDismissed(context: Context, versionName: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_DISMISSED_VERSION, null) == versionName
    }

    fun isVersionDismissed(context: Context, versionCode: Long): Boolean {
        return isVersionDismissed(context, cachedUpdateInfo?.versionName ?: versionCode.toString())
    }

    /**
     * Records dismissed version
     */
    fun dismissVersion(context: Context, versionName: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_DISMISSED_VERSION, versionName).apply()
    }

    fun dismissVersion(context: Context, versionCode: Long) {
        dismissVersion(context, cachedUpdateInfo?.versionName ?: versionCode.toString())
    }

    /**
     * Downloads the APK directly inside the app with real-time stream progress.
     */
    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        versionName: String,
        onProgress: (DownloadProgressState) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            onProgress(DownloadProgressState.Downloading(0f, 0L, 0L))

            val downloadDir = File(context.externalCacheDir ?: context.cacheDir, "updates").apply { mkdirs() }
            val targetFile = File(downloadDir, "LytrixPlayer-v$versionName.apk")
            if (targetFile.exists()) targetFile.delete()

            val request = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "Lytrix-Player/${BuildConfig.VERSION_NAME}")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                onProgress(DownloadProgressState.Error("Server returned HTTP ${response.code}"))
                return@withContext
            }

            val body = response.body ?: run {
                onProgress(DownloadProgressState.Error("Empty download stream"))
                return@withContext
            }

            val totalBytes = body.contentLength()
            var downloadedBytes = 0L

            body.byteStream().use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var read: Int
                    var lastUpdate = System.currentTimeMillis()

                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloadedBytes += read

                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > 100 || downloadedBytes == totalBytes) {
                            lastUpdate = now
                            val progress = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f
                            onProgress(DownloadProgressState.Downloading(progress.coerceIn(0f, 1f), downloadedBytes, totalBytes))
                        }
                    }
                    output.flush()
                }
            }

            onProgress(DownloadProgressState.Completed(targetFile))
            withContext(Dispatchers.Main) {
                installApk(context, targetFile)
            }
        } catch (e: Exception) {
            onProgress(DownloadProgressState.Error(e.message ?: "Download failed"))
        }
    }

    /**
     * Prompts the Android Package Installer to install the downloaded APK.
     */
    fun installApk(context: Context, apkFile: File) {
        if (!apkFile.exists() || apkFile.length() == 0L) {
            Toast.makeText(context, "APK file not found", Toast.LENGTH_SHORT).show()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Toast.makeText(context, "Please allow installation permission for Lytrix Player", Toast.LENGTH_LONG).show()
                return
            }
        }

        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.FileProvider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to launch installer: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Fallback: opens GitHub release in browser.
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

    /**
     * Android system notification for new version.
     */
    fun showUpdateNotification(context: Context, updateInfo: AppUpdateInfo) {
        try {
            createNotificationChannel(context)

            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo.htmlUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val pendingIntent = PendingIntent.getActivity(context, NOTIFICATION_ID, intent, flags)

            val title = "Lytrix Player v${updateInfo.versionName} Available"
            val text = "A new update is ready to install. Tap to view."

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
}
