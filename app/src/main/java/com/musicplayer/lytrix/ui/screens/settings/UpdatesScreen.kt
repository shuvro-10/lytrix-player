@file:OptIn(ExperimentalMaterial3Api::class)

package com.musicplayer.lytrix.ui.screens.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.icu.text.RelativeDateTimeFormatter
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.musicplayer.lytrix.BuildConfig
import com.musicplayer.lytrix.update.AppUpdateManager
import com.musicplayer.lytrix.update.AppUpdateState
import com.musicplayer.lytrix.update.GitHubRelease
import com.musicplayer.lytrix.update.UpdateFailure
import com.musicplayer.lytrix.update.formatReleaseAge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale

@Composable
fun UpdatesScreen(
    onNavigateBack: () -> Unit,
    onOpenDetails: () -> Unit,
) {
    val context = LocalContext.current
    val state by AppUpdateManager.state.collectAsState()
    val releases by AppUpdateManager.releases.collectAsState()
    val scope = rememberCoroutineScope()
    var expandedRelease by remember { mutableStateOf<GitHubRelease?>(null) }

    var notificationsAllowed by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }

    DisposableEffect(context) {
        notificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
        onDispose { }
    }

    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            AppUpdateManager.setNotificationsEnabled(context, granted)
            notificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

    var autoCheckUpdates by remember { mutableStateOf(AppUpdateManager.isAutoCheckEnabled(context)) }
    var nightlyBuilds by remember { mutableStateOf(AppUpdateManager.isNightlyEnabled(context)) }
    var updateNotifications by remember { mutableStateOf(AppUpdateManager.isNotificationsEnabled(context)) }

    LaunchedEffect(Unit) {
        AppUpdateManager.loadReleaseHistory(context)
        if (state is AppUpdateState.Idle) {
            AppUpdateManager.check(context)
        }
    }

    SettingsSubScreen(
        title = "Updates",
        onNavigateBack = onNavigateBack,
    ) {
        // App update status card
        SettingsSubScreenGroup(
            items = listOf(statusItem(state = state, currentVersion = BuildConfig.VERSION_NAME, onClick = onOpenDetails))
        )

        // General Section
        SettingsSubScreenGroup(
            title = "General",
            items = listOf(
                switchSettingsItem(
                    title = "Automatically check for updates",
                    wrapTitle = true,
                    checked = autoCheckUpdates,
                    onCheckedChange = {
                        autoCheckUpdates = it
                        AppUpdateManager.setAutoCheckEnabled(context, it)
                    },
                ),
                switchSettingsItem(
                    title = "Nightly builds",
                    description = "Get a new build after every change. Nightly builds may be unstable.",
                    wrapTitle = true,
                    checked = nightlyBuilds,
                    onCheckedChange = { enabled ->
                        nightlyBuilds = enabled
                        AppUpdateManager.setNightlyEnabled(context, enabled)
                        scope.launch { AppUpdateManager.check(context) }
                    },
                ),
                switchSettingsItem(
                    title = "Update notifications",
                    wrapTitle = true,
                    checked = updateNotifications && notificationsAllowed,
                    onCheckedChange = { enabled ->
                        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else if (enabled && !notificationsAllowed) {
                            AppUpdateManager.setNotificationsEnabled(context, true)
                            context.startActivity(appNotificationSettingsIntent(context))
                        } else {
                            updateNotifications = enabled
                            AppUpdateManager.setNotificationsEnabled(context, enabled)
                        }
                    },
                ),
                SettingsSubScreenItem(
                    title = "Android notification settings",
                    description = "Choose which notifications Android allows for Lytrix",
                    onClick = {
                        context.startActivity(appNotificationSettingsIntent(context))
                    },
                ),
            )
        )

        // Share app Section
        SettingsSubScreenGroup(
            title = "Share app",
            items = listOf(
                SettingsSubScreenItem(
                    title = "Share app APK",
                    description = "Share the installed app package as a file",
                    icon = Icons.Outlined.Share,
                    onClick = {
                        scope.launch {
                            try {
                                AppUpdateManager.shareInstalledApk(context)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                Toast.makeText(context, "Could not share APK", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                )
            )
        )

        // Changelogs Section
        if (releases.isNotEmpty()) {
            val locale = LocalConfiguration.current.locales[0]
            val formatter = remember(locale) { RelativeDateTimeFormatter.getInstance(locale) }
            val now by produceState(Instant.now()) {
                while (true) {
                    delay(60_000)
                    value = Instant.now()
                }
            }

            SettingsSubScreenGroup(
                title = "Changelogs",
                items = releases.map { release ->
                    val date = release.publishedAt?.substringBefore('T')
                    val age = formatReleaseAge(release.publishedAt, now, formatter)
                    val dateAgeText = if (date != null && age != null) "$date · $age" else date.orEmpty()
                    val subtitle = settingsSubtitle("Release notes and installation options", dateAgeText)

                    SettingsSubScreenItem(
                        title = release.version,
                        description = subtitle,
                        onClick = { expandedRelease = release },
                    )
                }
            )
        }

        // Bottom version footer (matching screenshot: centered "14.0.1" / current version)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 48.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = BuildConfig.VERSION_NAME,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                ),
                textAlign = TextAlign.Center
            )
        }
    }

    expandedRelease?.let { release ->
        ReleaseNotesSheet(
            release = release,
            onDismiss = { expandedRelease = null }
        )
    }
}

private fun settingsSubtitle(prefix: String, suffix: String?): String {
    return if (suffix.isNullOrBlank()) prefix else "$prefix · $suffix"
}

@Composable
private fun statusItem(
    state: AppUpdateState,
    currentVersion: String,
    onClick: () -> Unit,
): SettingsSubScreenItem {
    val pending = state.release != null
    val isChecking = state is AppUpdateState.Checking
    val isError = state is AppUpdateState.Error

    val statusText = when {
        pending -> "Update available · v${state.release?.version}"
        isChecking -> "Checking for updates..."
        isError -> "Check failed"
        else -> "Updated to Lytrix $currentVersion"
    }

    val subtitle = settingsSubtitle("Release notes and installation options", statusText)

    val icon = when {
        pending -> Icons.Filled.NewReleases
        isError -> Icons.Filled.Error
        else -> Icons.Filled.Verified
    }

    val iconTint = when {
        pending -> MaterialTheme.colorScheme.tertiary
        isError -> MaterialTheme.colorScheme.error
        isChecking -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.primary
    }

    return SettingsSubScreenItem(
        title = "App update",
        description = subtitle,
        icon = icon,
        iconTint = iconTint,
        onClick = onClick,
    )
}

val AppUpdateState.release: GitHubRelease?
    get() = when (this) {
        is AppUpdateState.Available -> release
        is AppUpdateState.Downloading -> release
        is AppUpdateState.Downloaded -> release
        is AppUpdateState.Error -> release
        else -> null
    }

@Composable
fun UpdateDetailsScreen(
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by AppUpdateManager.state.collectAsState()

    var installAfterPermission by remember { mutableStateOf<AppUpdateState.Downloaded?>(null) }
    var autoInstallHandled by rememberSaveable { mutableStateOf(false) }
    var installerError by remember { mutableStateOf(false) }

    val installPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            installAfterPermission?.let { downloaded ->
                if (AppUpdateManager.canInstallPackages(context)) {
                    scope.launch { installerError = !AppUpdateManager.install(context, downloaded) }
                    installAfterPermission = null
                }
            }
        }

    fun install(downloaded: AppUpdateState.Downloaded) {
        installerError = false
        if (AppUpdateManager.canInstallPackages(context)) {
            scope.launch { installerError = !AppUpdateManager.install(context, downloaded) }
        } else {
            installAfterPermission = downloaded
            val permissionIntent =
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                )
            installerError =
                runCatching {
                    check(permissionIntent.resolveActivity(context.packageManager) != null)
                    installPermissionLauncher.launch(permissionIntent)
                }.isFailure
        }
    }

    LaunchedEffect(state) {
        val downloaded = state as? AppUpdateState.Downloaded ?: return@LaunchedEffect
        if (!autoInstallHandled && !downloaded.installing) {
            autoInstallHandled = true
            install(downloaded)
        }
    }

    val current = state
    val release = current.release
    val installing = current is AppUpdateState.Downloading || (current as? AppUpdateState.Downloaded)?.installing == true

    val screenTitle = when {
        current is AppUpdateState.Error -> when (current.failure) {
            UpdateFailure.NO_APK -> "Release unavailable"
            UpdateFailure.DIGEST -> "Verification failed"
            UpdateFailure.DOWNLOAD -> "Download failed"
            UpdateFailure.INSTALL -> "Installation failed"
            UpdateFailure.CHECK -> "Update check failed"
        }
        installing -> if (current is AppUpdateState.Downloading) "Downloading update..." else "Installing update..."
        release != null -> "Update available"
        current is AppUpdateState.Checking -> "Checking for updates..."
        else -> "Up to date"
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
            )
        },
        bottomBar = {
            val buttonModifier =
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 48.dp)
                    .heightIn(min = 56.dp)

            when {
                current is AppUpdateState.Downloading -> {
                    OutlinedButton(
                        onClick = AppUpdateManager::cancelDownload,
                        modifier = buttonModifier,
                    ) {
                        Text("Cancel download")
                    }
                }
                current is AppUpdateState.Downloaded -> {
                    Button(
                        enabled = !current.installing,
                        onClick = { install(current) },
                        modifier = buttonModifier,
                    ) {
                        Text(if (current.installing) "Installing..." else "Install update")
                    }
                }
                current is AppUpdateState.Available -> {
                    Button(
                        onClick = { scope.launch { AppUpdateManager.download(context, current.release) } },
                        modifier = buttonModifier,
                    ) {
                        Text("Download update")
                    }
                }
                current is AppUpdateState.Error -> {
                    Button(
                        onClick = {
                            scope.launch {
                                if (release == null || current.failure != UpdateFailure.DOWNLOAD) {
                                    AppUpdateManager.check(context)
                                } else {
                                    AppUpdateManager.download(context, release)
                                }
                            }
                        },
                        modifier = buttonModifier,
                    ) {
                        Text("Retry")
                    }
                }
                else -> {
                    Button(
                        enabled = current !is AppUpdateState.Checking,
                        onClick = { scope.launch { AppUpdateManager.check(context) } },
                        modifier = buttonModifier,
                    ) {
                        Text(if (current is AppUpdateState.Checking) "Checking..." else "Check for updates")
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = screenTitle,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp
                )
            )

            if (installing || current is AppUpdateState.Checking) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (current is AppUpdateState.Downloading) {
                        LinearProgressIndicator(
                            progress = { current.progress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "${current.progress}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            if (installerError) {
                Text(
                    text = "Installation could not be started. Check unknown app sources permission.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (release == null) {
                val lastCheckTimestamp = AppUpdateManager.getLastCheckTimestamp(context)
                val lastCheckFormatted = if (lastCheckTimestamp > 0) {
                    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(lastCheckTimestamp))
                } else "Never"

                UpdateInfoCard(
                    version = BuildConfig.VERSION_NAME,
                    versionLabel = "Installed Version",
                    details = listOf(
                        "Last check" to lastCheckFormatted
                    )
                )
            } else {
                val sizeFormatted = release.apk?.size?.let { formatBytes(it) } ?: "N/A"
                val dateFormatted = release.publishedAt?.substringBefore('T') ?: "N/A"

                UpdateInfoCard(
                    version = release.version,
                    versionLabel = "Latest Version",
                    details = listOf(
                        "Size" to sizeFormatted,
                        "Date" to dateFormatted
                    )
                )

                Text(
                    text = "Changelog",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(top = 8.dp)
                )

                val bodyText = release.body.orEmpty().trim().ifEmpty { "No release notes available." }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(16.dp)
                ) {
                    Text(
                        text = bodyText,
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun UpdateInfoCard(
    version: String,
    details: List<Pair<String, String>>,
    versionLabel: String = "Version",
) {
    val outer = 20.dp
    val inner = 4.dp

    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(topStart = outer, topEnd = outer, bottomStart = inner, bottomEnd = inner)
                )
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Column {
                Text(
                    text = versionLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = version,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            details.forEachIndexed { index, (label, value) ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(
                                topStart = inner,
                                topEnd = inner,
                                bottomStart = if (index == 0) outer else inner,
                                bottomEnd = if (index == details.lastIndex) outer else inner,
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Column {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = value,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val mb = bytes.toDouble() / (1024 * 1024)
    return String.format(Locale.US, "%.1f MB", mb)
}

private fun appNotificationSettingsIntent(context: Context): Intent =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
    }
