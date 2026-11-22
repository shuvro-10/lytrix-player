package com.musicplayer.lytrix.update

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.musicplayer.lytrix.BuildConfig
import com.musicplayer.lytrix.MainActivity
import com.musicplayer.lytrix.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String = "",
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
    @SerialName("target_commitish") val targetCommit: String? = null,
    val assets: List<GitHubReleaseAsset> = emptyList(),
) {
    val version: String get() = tagName.removePrefix("v")
    val isNightly: Boolean get() = (tagName.equals(AppUpdateManager.NIGHTLY_TAG, ignoreCase = true) || tagName.contains("nightly", ignoreCase = true)) && !draft

    /** Nightlies share one tag, so their package digest tells them apart. */
    val updateKey: String get() = if (isNightly) "${AppUpdateManager.NIGHTLY_TAG}:${apk?.digest ?: targetCommit.orEmpty()}" else version
    val isPublishedStable: Boolean
        get() = !draft && !prerelease && ReleaseVersion.parse(tagName)?.prerelease?.isEmpty() == true
    val apk: GitHubReleaseAsset?
        get() {
            val candidates =
                assets.filter { asset ->
                    asset.state == "uploaded" &&
                        asset.name.endsWith(".apk", ignoreCase = true) &&
                        AppUpdateManager.BLOCKED_APK_MARKERS.none { marker -> asset.name.contains(marker, ignoreCase = true) }
                }
            if (candidates.isEmpty()) return null

            val supportedAbis = Build.SUPPORTED_ABIS.toList()
            val isArm64Supported = supportedAbis.any { it.contains("arm64", ignoreCase = true) }
            val isArmv7Supported = supportedAbis.any { it.contains("armeabi", ignoreCase = true) || it.contains("armv7", ignoreCase = true) }

            // 1. If device supports 64-bit ARM, match arm64 APK first
            if (isArm64Supported) {
                val arm64Apk = candidates.firstOrNull { asset ->
                    asset.name.contains("arm64", ignoreCase = true) ||
                        asset.name.contains("v8a", ignoreCase = true)
                }
                if (arm64Apk != null) return arm64Apk
            }

            // 2. If device is 32-bit ARM, match armv7a APK
            if (isArmv7Supported && !isArm64Supported) {
                val armv7Apk = candidates.firstOrNull { asset ->
                    asset.name.contains("armeabi-v7a", ignoreCase = true) ||
                        asset.name.contains("armv7", ignoreCase = true)
                }
                if (armv7Apk != null) return armv7Apk
            }

            // 3. Match Universal APK
            val universalApk = candidates.firstOrNull { asset ->
                asset.name.contains("universal", ignoreCase = true)
            }
            if (universalApk != null) return universalApk

            // 4. Preferred standard names
            val preferredNames =
                listOf(
                    "Lytrix-$version.apk",
                    "Lytrix-v$version.apk",
                    "lytrix-player-$version.apk",
                    "lytrix-$version.apk",
                    "app-release.apk"
                )
            return preferredNames.firstNotNullOfOrNull { preferred ->
                candidates.singleOrNull { it.name.equals(preferred, ignoreCase = true) }
            } ?: candidates.firstOrNull()
        }
}

@Serializable
data class GitHubReleaseAsset(
    val name: String = "",
    val state: String = "uploaded",
    val size: Long = 0L,
    val digest: String? = null,
    @SerialName("browser_download_url") val downloadUrl: String = "",
)

sealed interface AppUpdateState {
    data object Idle : AppUpdateState

    data object Checking : AppUpdateState

    data class UpToDate(
        val checkedAt: Long,
    ) : AppUpdateState

    data class Available(
        val release: GitHubRelease,
    ) : AppUpdateState

    data class Downloading(
        val release: GitHubRelease,
        val progress: Int,
    ) : AppUpdateState

    data class Downloaded(
        val release: GitHubRelease,
        val file: File,
        val installing: Boolean = false,
        val verified: Boolean = true,
    ) : AppUpdateState

    data class Error(
        val failure: UpdateFailure,
        val release: GitHubRelease? = null,
    ) : AppUpdateState
}

enum class UpdateFailure { CHECK, NO_APK, DOWNLOAD, DIGEST, INSTALL }

object AppUpdateManager {
    const val CHANNEL_ID = "updates"
    const val NIGHTLY_TAG = "nightly"
    const val ACTION_UPDATES = "com.musicplayer.lytrix.action.UPDATES"

    private const val GITHUB_REPO = "shuvro-10/lytrix-player"
    private const val RELEASES_URL = "https://api.github.com/repos/$GITHUB_REPO/releases?per_page=50"
    private const val NIGHTLY_RELEASE_URL = "https://api.github.com/repos/$GITHUB_REPO/releases/tags/$NIGHTLY_TAG"
    private const val NOTIFICATION_ID = 1400
    private const val MAX_APK_BYTES = 512L * 1024 * 1024
    private const val MIN_FREE_SPACE_BYTES = 16L * 1024 * 1024
    private const val CHECK_INTERVAL_MILLIS = 60 * 60 * 1_000L
    private const val PREFS_NAME = "lytrix_update_preferences"
    private const val APK_SHARE_CACHE_MAX_AGE_MS = 24 * 60 * 60 * 1000L

    val BLOCKED_APK_MARKERS = listOf("unsigned", "unaligned", "benchmark")

    val currentVersion: String = BuildConfig.VERSION_NAME

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val mutableState = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    private val mutableReleases = MutableStateFlow<List<GitHubRelease>>(emptyList())
    private val operationMutex = Mutex()

    @Volatile private var activeDownload: Call? = null

    val state: StateFlow<AppUpdateState> = mutableState.asStateFlow()
    val releases: StateFlow<List<GitHubRelease>> = mutableReleases.asStateFlow()

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isAutoCheckEnabled(context: Context): Boolean =
        getPrefs(context).getBoolean("check_for_updates", true)

    fun setAutoCheckEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean("check_for_updates", enabled).apply()
    }

    fun isNightlyEnabled(context: Context): Boolean =
        getPrefs(context).getBoolean("nightly_builds", false)

    fun setNightlyEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean("nightly_builds", enabled).apply()
    }

    fun isNotificationsEnabled(context: Context): Boolean =
        getPrefs(context).getBoolean("update_notifications", true)

    fun setNotificationsEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean("update_notifications", enabled).apply()
    }

    fun getLastCheckTimestamp(context: Context): Long =
        getPrefs(context).getLong("last_check_timestamp", 0L)

    private fun setLastCheckTimestamp(context: Context, timestamp: Long) {
        getPrefs(context).edit().putLong("last_check_timestamp", timestamp).apply()
    }

    private fun getCachedReleasesJson(context: Context): String =
        getPrefs(context).getString("cached_releases_json", "").orEmpty()

    private fun setCachedReleasesJson(context: Context, value: String) {
        getPrefs(context).edit().putString("cached_releases_json", value).apply()
    }

    private fun getCachedAvailableUpdateJson(context: Context): String =
        getPrefs(context).getString("cached_available_update_json", "").orEmpty()

    private fun setCachedAvailableUpdateJson(context: Context, value: String) {
        getPrefs(context).edit().putString("cached_available_update_json", value).apply()
    }

    private fun getLastNotifiedVersion(context: Context): String =
        getPrefs(context).getString("last_notified_version", "").orEmpty()

    private fun setLastNotifiedVersion(context: Context, value: String) {
        getPrefs(context).edit().putString("last_notified_version", value).apply()
    }

    suspend fun loadReleaseHistory(context: Context? = null) =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                if (mutableReleases.value.isEmpty() && context != null) {
                    val cached = getCachedReleasesJson(context)
                    if (cached.isNotEmpty()) {
                        runCatching {
                            mutableReleases.value = json.decodeFromString<List<GitHubRelease>>(cached)
                        }
                    }
                }
            }
        }

    suspend fun check(
        context: Context,
        notify: Boolean = false,
        crashRecovery: Boolean = false,
    ): GitHubRelease? =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                when (val active = mutableState.value) {
                    is AppUpdateState.Downloading -> return@withLock active.release
                    is AppUpdateState.Downloaded -> {
                        if (!active.file.isFile) {
                            mutableState.value = AppUpdateState.Available(active.release)
                        }
                        return@withLock active.release
                    }
                    else -> Unit
                }

                mutableState.value = AppUpdateState.Checking

                runCatching {
                    val request =
                        Request.Builder()
                            .url(RELEASES_URL)
                            .header("Accept", "application/vnd.github+json")
                            .header("X-GitHub-Api-Version", "2022-11-28")
                            .header("User-Agent", "Lytrix-Player/$currentVersion")
                            .build()

                    val allReleases =
                        client.newCall(request).execute().use { response ->
                            check(response.isSuccessful) { "GitHub returned ${response.code}" }
                            json.decodeFromString<List<GitHubRelease>>(checkNotNull(response.body).string())
                        }

                    val publicReleases =
                        allReleases
                            .filter { release -> !release.draft }
                            .sortedWith { left, right -> compareVersions(right.version, left.version) }

                    mutableReleases.value = publicReleases
                    setCachedReleasesJson(context, json.encodeToString(publicReleases))

                    val now = System.currentTimeMillis()
                    setLastCheckTimestamp(context, now)

                    val nightlyEnabled = isNightlyEnabled(context)
                    val update =
                        publicReleases.firstOrNull { release ->
                            (crashRecovery || release.isPublishedStable) &&
                                compareVersions(release.version, currentVersion) > 0
                        } ?: if (nightlyEnabled) {
                            allReleases.firstOrNull(GitHubRelease::isNightly)
                        } else null

                    setCachedAvailableUpdateJson(context, update?.let { json.encodeToString(it) }.orEmpty())
                    mutableState.value = if (update == null) AppUpdateState.UpToDate(now) else AppUpdateState.Available(update)

                    if (update != null && notify && !crashRecovery && isNotificationsEnabled(context)) {
                        notifyAvailable(context, update)
                    }
                    update
                }.getOrElse {
                    val cachedJson = getCachedAvailableUpdateJson(context)
                    val cached = runCatching { json.decodeFromString<GitHubRelease>(cachedJson) }.getOrNull()
                    mutableState.value = cached?.let(AppUpdateState::Available) ?: AppUpdateState.Error(UpdateFailure.CHECK)
                    cached
                }
            }
        }

    suspend fun download(
        context: Context,
        release: GitHubRelease,
        crashRecovery: Boolean = false,
        skipVerification: Boolean = false,
    ) = withContext(Dispatchers.IO) {
        operationMutex.withLock {
            when (val active = mutableState.value) {
                is AppUpdateState.Downloading -> if (active.release.updateKey == release.updateKey) return@withLock
                is AppUpdateState.Downloaded -> if (active.release.updateKey == release.updateKey && active.file.isFile) return@withLock
                else -> Unit
            }

            val asset = release.apk
            if (asset == null) {
                mutableState.value = AppUpdateState.Error(UpdateFailure.NO_APK, release)
                return@withLock
            }

            runCatching {
                check(asset.size in 1..MAX_APK_BYTES) { "Invalid update size" }
                val updateDir = File(context.cacheDir, "updates").apply { mkdirs() }
                updateDir.listFiles()?.forEach(File::delete)
                val partial = File(updateDir, "Lytrix-${release.version}.apk.part")
                val output = File(updateDir, "Lytrix-${release.version}.apk")

                val request =
                    Request.Builder()
                        .url(asset.downloadUrl)
                        .header("User-Agent", "Lytrix-Player/$currentVersion")
                        .build()

                var downloaded = 0L
                val call = client.newCall(request).also { activeDownload = it }
                call.execute().use { response ->
                    check(response.isSuccessful) { "Download returned ${response.code}" }
                    val body = checkNotNull(response.body)
                    val total = asset.size.takeIf { it > 0 } ?: body.contentLength()
                    check(updateDir.usableSpace > asset.size + MIN_FREE_SPACE_BYTES) { "Insufficient space for update" }

                    body.byteStream().use { input ->
                        partial.outputStream().buffered().use { target ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                check(downloaded + count <= MAX_APK_BYTES) { "Update exceeded expected size" }
                                target.write(buffer, 0, count)
                                downloaded += count
                                val progress = if (total > 0) ((downloaded * 100) / total).toInt().coerceIn(0, 100) else 0
                                mutableState.value = AppUpdateState.Downloading(release, progress)
                            }
                        }
                    }
                }

                if (!skipVerification && asset.digest != null && !verifyDigest(partial, asset.digest)) {
                    partial.delete()
                    mutableState.value = AppUpdateState.Error(UpdateFailure.DIGEST, release)
                    return@runCatching
                }

                check(partial.renameTo(output)) { "Could not finalize update file" }
                mutableState.value = AppUpdateState.Downloaded(release, output, verified = !skipVerification)
            }.onFailure {
                mutableState.value =
                    if (activeDownload?.isCanceled() == true) {
                        AppUpdateState.Available(release)
                    } else {
                        AppUpdateState.Error(UpdateFailure.DOWNLOAD, release)
                    }
            }
            activeDownload = null
        }
    }

    fun cancelDownload() {
        activeDownload?.cancel()
    }

    fun canInstallPackages(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    suspend fun install(
        context: Context,
        downloaded: AppUpdateState.Downloaded,
        crashRecovery: Boolean = false,
    ): Boolean =
        withContext(Dispatchers.IO) {
            mutableState.value = downloaded.copy(installing = true)
            if (!downloaded.file.isFile) {
                mutableState.value = AppUpdateState.Available(downloaded.release)
                return@withContext false
            }

            var success = false
            try {
                // Try modern PackageInstaller session first
                val installer = context.packageManager.packageInstaller
                var sessionId = -1
                val sessionSuccess = runCatching {
                    val params =
                        PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                            setAppPackageName(context.packageName)
                            setSize(downloaded.file.length())
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                            }
                        }
                    sessionId = installer.createSession(params)
                    installer.openSession(sessionId).use { session ->
                        downloaded.file.inputStream().use { input ->
                            session.openWrite("base.apk", 0, downloaded.file.length()).use { output ->
                                input.copyTo(output)
                                session.fsync(output)
                            }
                        }
                        val callback =
                            PendingIntent.getBroadcast(
                                context,
                                sessionId,
                                Intent(context, UpdateInstallReceiver::class.java),
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                            )
                        session.commit(callback.intentSender)
                    }
                    true
                }.getOrElse {
                    if (sessionId >= 0) {
                        runCatching { installer.abandonSession(sessionId) }
                    }
                    false
                }

                if (sessionSuccess) {
                    success = true
                } else {
                    // Fallback to FileProvider VIEW intent for guaranteed compatibility
                    val apkUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.FileProvider",
                        downloaded.file
                    )
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(apkUri, "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    success = true
                }
            } catch (e: Exception) {
                mutableState.value = downloaded.copy(installing = false)
                success = false
            }
            success
        }

    internal fun onInstallFinished(success: Boolean) {
        val current = mutableState.value as? AppUpdateState.Downloaded ?: return
        mutableState.value =
            if (success) current.copy(installing = false) else AppUpdateState.Error(UpdateFailure.INSTALL, current.release)
    }

    internal fun onInstallAborted() {
        val current = mutableState.value as? AppUpdateState.Downloaded ?: return
        mutableState.value = current.copy(installing = false)
    }

    suspend fun shareInstalledApk(context: Context) = withContext(Dispatchers.IO) {
        val sourceApk = File(context.applicationInfo.sourceDir)
        check(sourceApk.isFile) { "Installed APK is unavailable" }
        val directory = File(context.cacheDir, "shared_apks").apply { mkdirs() }
        val now = System.currentTimeMillis()
        directory
            .listFiles()
            ?.filter { it.name.startsWith("lytrix-") && now - it.lastModified() > APK_SHARE_CACHE_MAX_AGE_MS }
            ?.forEach(File::delete)

        val destination = File(directory, "Lytrix-v$currentVersion.apk")
        sourceApk.copyTo(destination, overwrite = true)
        val apkUri = FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", destination)

        val shareTitle = "Share Lytrix APK"
        val sendIntent =
            Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, apkUri)
                clipData = ClipData.newUri(context.contentResolver, shareTitle, apkUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        context.startActivity(Intent.createChooser(sendIntent, shareTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun notifyAvailable(context: Context, release: GitHubRelease) {
        if (getLastNotifiedVersion(context) == release.updateKey) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Lytrix Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for new Lytrix Player releases"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_UPDATES
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent =
            PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val notification =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Lytrix ${release.version} is available")
                .setContentText("Tap to review release notes and install the update.")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
        setLastNotifiedVersion(context, release.updateKey)
    }

    private fun verifyDigest(file: File, digest: String?): Boolean {
        val expected = digest?.takeIf { it.matches(Regex("sha256:[0-9a-fA-F]{64}")) }?.removePrefix("sha256:") ?: return false
        val messageDigest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                messageDigest.update(buffer, 0, count)
            }
        }
        val actual = messageDigest.digest().joinToString("") { "%02x".format(it) }
        return actual.equals(expected, ignoreCase = true)
    }

    internal fun compareVersions(left: String, right: String): Int {
        val parsedLeft = ReleaseVersion.parse(left)
        val parsedRight = ReleaseVersion.parse(right)
        if (parsedLeft != null && parsedRight != null) {
            return parsedLeft.compareTo(parsedRight)
        }
        return left.compareTo(right)
    }
}
