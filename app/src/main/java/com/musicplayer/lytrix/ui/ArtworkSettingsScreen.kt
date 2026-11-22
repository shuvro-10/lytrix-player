package com.musicplayer.lytrix.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicplayer.lytrix.artwork.ArtworkPreferencesManager
import com.musicplayer.lytrix.artwork.CanvasContentSource
import com.musicplayer.lytrix.artwork.YouTubeCanvasMode
import com.musicplayer.lytrix.artwork.isEnabled
import com.musicplayer.lytrix.spotify.SpotifyPreferencesManager
import com.musicplayer.lytrix.spotify.canvas.VisualMediaManager
import compose.icons.TablerIcons
import compose.icons.tablericons.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtworkSettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSpotifyLogin: () -> Unit = {}
) {
    BackHandler { onNavigateBack() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val artworkPrefs = remember { ArtworkPreferencesManager.getInstance(context) }
    val spotifyPrefs = remember { SpotifyPreferencesManager.getInstance(context) }

    val canvasEnabled by artworkPrefs.canvasEnabled.collectAsState()
    val animatedCoversEnabled by artworkPrefs.animatedCoversEnabled.collectAsState()
    val disableOnMobileData by artworkPrefs.disableOnMobileData.collectAsState()
    val animatedCoverResolution by artworkPrefs.animatedCoverResolution.collectAsState()

    val contentOrder by artworkPrefs.canvasContentOrder.collectAsState()
    val enabledSources by artworkPrefs.canvasEnabledSources.collectAsState()

    val youtubeCanvasQuality by artworkPrefs.youtubeCanvasQuality.collectAsState()
    val youtubeCanvasMode by artworkPrefs.youtubeCanvasMode.collectAsState()

    val appleArtistImages by artworkPrefs.appleArtistImages.collectAsState()
    val appleArtistVideos by artworkPrefs.appleArtistVideos.collectAsState()
    val appleArtistVideoResolution by artworkPrefs.appleArtistVideoResolution.collectAsState()

    val isSpotifyLoggedIn by spotifyPrefs.isLoggedIn.collectAsState(initial = false)

    var showCoverResolutionDialog by remember { mutableStateOf(false) }
    var showYtQualityDialog by remember { mutableStateOf(false) }
    var showYtModeDialog by remember { mutableStateOf(false) }
    var showArtistResolutionDialog by remember { mutableStateOf(false) }

    val bgColor = MaterialTheme.colorScheme.background
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val textColor = MaterialTheme.colorScheme.onBackground
    val textSub = MaterialTheme.colorScheme.onSurfaceVariant
    val accentColor = MaterialTheme.colorScheme.primary

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Artwork", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor)
            )
        },
        containerColor = bgColor
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ───────────────────────────────────────────
            // 1️⃣ PLAYBACK SECTION
            // ───────────────────────────────────────────
            item {
                Text(
                    text = "Playback",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
                )
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(surfaceColor)
                ) {
                    // Canvas (Master Switch)
                    ArtworkSwitchRow(
                        icon = TablerIcons.Video,
                        title = "Canvas",
                        subtitle = "Display animated looping visuals in player",
                        checked = canvasEnabled,
                        onCheckedChange = {
                            artworkPrefs.setCanvasEnabled(it)
                            VisualMediaManager.clearCache(context)
                        }
                    )

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.5.dp)

                    // Enable animated covers (Apple Music Motion)
                    ArtworkSwitchRow(
                        icon = TablerIcons.Photo,
                        title = "Enable animated covers",
                        subtitle = "Display official animated album covers from Apple Music",
                        checked = animatedCoversEnabled,
                        onCheckedChange = {
                            artworkPrefs.setAnimatedCoversEnabled(it)
                            VisualMediaManager.clearCache(context)
                        }
                    )

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.5.dp)

                    // Disable on mobile data
                    ArtworkSwitchRow(
                        icon = TablerIcons.DeviceMobile,
                        title = "Disable on mobile data",
                        subtitle = "Save mobile cellular data by pausing animated artwork",
                        checked = disableOnMobileData,
                        onCheckedChange = { artworkPrefs.setDisableOnMobileData(it) }
                    )

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.5.dp)

                    // Animated cover resolution
                    ArtworkActionRow(
                        icon = TablerIcons.Adjustments,
                        title = "Animated cover resolution",
                        subtitle = "Current: $animatedCoverResolution",
                        enabled = animatedCoversEnabled,
                        onClick = { showCoverResolutionDialog = true }
                    )
                }
            }

            // ───────────────────────────────────────────
            // 2️⃣ ARTWORK SOURCES (Reorderable Priority)
            // ───────────────────────────────────────────
            item {
                Text(
                    text = "Artwork sources",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                )
                Text(
                    text = "Reorder canvas sources to choose which visual displays first. If a visual is not found, the next source will be tried.",
                    fontSize = 12.sp,
                    color = textSub,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 4.dp)
                )
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(surfaceColor)
                ) {
                    contentOrder.forEachIndexed { index, source ->
                        val label = when (source) {
                            CanvasContentSource.YOUTUBE_MUSIC_VIDEO -> "YouTube Music video"
                            CanvasContentSource.SPOTIFY_CANVAS -> "Spotify Canvas"
                            CanvasContentSource.APPLE_MUSIC -> "Apple Music"
                        }
                        val isSourceActive = source.isEnabled(canvasEnabled, animatedCoversEnabled)
                        val isChecked = source in enabledSources

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 68.dp)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = label,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = textColor.copy(alpha = if (isSourceActive) 1f else 0.4f)
                                )
                                if (source == CanvasContentSource.SPOTIFY_CANVAS && !isSpotifyLoggedIn) {
                                    TextButton(
                                        onClick = onNavigateToSpotifyLogin,
                                        contentPadding = PaddingValues(0.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("Sign in to Spotify", fontSize = 12.sp, color = accentColor)
                                    }
                                } else {
                                    Text(
                                        text = "Priority #${index + 1}",
                                        fontSize = 11.sp,
                                        color = textSub.copy(alpha = if (isSourceActive) 0.8f else 0.4f)
                                    )
                                }
                            }

                            // ⬆️ Move Up
                            IconButton(
                                onClick = {
                                    if (index > 0) {
                                        val updated = contentOrder.toMutableList()
                                        val temp = updated[index]
                                        updated[index] = updated[index - 1]
                                        updated[index - 1] = temp
                                        artworkPrefs.setCanvasContentOrder(updated)
                                        VisualMediaManager.clearCache(context)
                                    }
                                },
                                enabled = index > 0 && isSourceActive
                            ) {
                                Icon(
                                    Icons.Default.KeyboardArrowUp,
                                    contentDescription = "Move Up",
                                    tint = if (index > 0 && isSourceActive) textColor else textColor.copy(alpha = 0.25f)
                                )
                            }

                            // ⬇️ Move Down
                            IconButton(
                                onClick = {
                                    if (index < contentOrder.lastIndex) {
                                        val updated = contentOrder.toMutableList()
                                        val temp = updated[index]
                                        updated[index] = updated[index + 1]
                                        updated[index + 1] = temp
                                        artworkPrefs.setCanvasContentOrder(updated)
                                        VisualMediaManager.clearCache(context)
                                    }
                                },
                                enabled = index < contentOrder.lastIndex && isSourceActive
                            ) {
                                Icon(
                                    Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Move Down",
                                    tint = if (index < contentOrder.lastIndex && isSourceActive) textColor else textColor.copy(alpha = 0.25f)
                                )
                            }

                            // Individual Enable Switch
                            Switch(
                                checked = isChecked,
                                enabled = isSourceActive,
                                onCheckedChange = { checked ->
                                    val updated = if (checked) enabledSources + source else enabledSources - source
                                    artworkPrefs.setCanvasEnabledSources(updated)
                                    VisualMediaManager.clearCache(context)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = accentColor
                                )
                            )
                        }

                        if (index < contentOrder.lastIndex) {
                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.5.dp)
                        }
                    }
                }
            }

            // ───────────────────────────────────────────
            // 3️⃣ YOUTUBE VIDEO CANVAS
            // ───────────────────────────────────────────
            item {
                Text(
                    text = "YouTube video canvas",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
                )
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(surfaceColor)
                ) {
                    ArtworkActionRow(
                        icon = TablerIcons.Video,
                        title = "YouTube canvas quality",
                        subtitle = "Current: $youtubeCanvasQuality",
                        enabled = canvasEnabled,
                        onClick = { showYtQualityDialog = true }
                    )

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.5.dp)

                    ArtworkActionRow(
                        icon = TablerIcons.Movie,
                        title = "YouTube canvas mode",
                        subtitle = if (youtubeCanvasMode == YouTubeCanvasMode.SHORT_CLIPS) "Short clips (3-second highlights)" else "Full video",
                        enabled = canvasEnabled,
                        onClick = { showYtModeDialog = true }
                    )
                }
            }

            // ───────────────────────────────────────────
            // 4️⃣ ARTIST ARTWORK
            // ───────────────────────────────────────────
            item {
                Text(
                    text = "Artist artwork",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
                )
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(surfaceColor)
                ) {
                    ArtworkSwitchRow(
                        icon = TablerIcons.User,
                        title = "Apple Music artist images",
                        subtitle = "Display high-quality artist images from Apple Music",
                        checked = appleArtistImages,
                        onCheckedChange = { artworkPrefs.setAppleArtistImages(it) }
                    )

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.5.dp)

                    ArtworkSwitchRow(
                        icon = TablerIcons.Video,
                        title = "Apple Music artist videos",
                        subtitle = "Display animated artist video headers from Apple Music",
                        checked = appleArtistVideos,
                        onCheckedChange = { artworkPrefs.setAppleArtistVideos(it) }
                    )

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.5.dp)

                    ArtworkActionRow(
                        icon = TablerIcons.Adjustments,
                        title = "Artist video quality",
                        subtitle = "Current: $appleArtistVideoResolution",
                        enabled = appleArtistVideos,
                        onClick = { showArtistResolutionDialog = true }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // ───────────────────────────────────────────
    // DIALOGS
    // ───────────────────────────────────────────
    if (showCoverResolutionDialog) {
        ArtworkSingleChoiceDialog(
            title = "Animated cover resolution",
            options = listOf("LOW" to "Low (480p)", "MEDIUM" to "Medium (720p)", "HIGH" to "High (1080p)"),
            selected = animatedCoverResolution,
            onSelect = {
                artworkPrefs.setAnimatedCoverResolution(it)
                VisualMediaManager.clearCache(context)
                showCoverResolutionDialog = false
            },
            onDismiss = { showCoverResolutionDialog = false }
        )
    }

    if (showYtQualityDialog) {
        ArtworkSingleChoiceDialog(
            title = "YouTube canvas quality",
            options = listOf(
                "AUTO" to "Auto",
                "2160p" to "2160p",
                "1080p" to "1080p",
                "720p" to "720p",
                "480p" to "480p"
            ),
            selected = youtubeCanvasQuality,
            onSelect = {
                artworkPrefs.setYouTubeCanvasQuality(it)
                VisualMediaManager.clearCache(context)
                showYtQualityDialog = false
            },
            onDismiss = { showYtQualityDialog = false }
        )
    }

    if (showYtModeDialog) {
        ArtworkSingleChoiceDialog(
            title = "YouTube canvas mode",
            options = listOf(
                YouTubeCanvasMode.SHORT_CLIPS.name to "Short clips (3-second highlights)",
                YouTubeCanvasMode.FULL_VIDEO.name to "Full video"
            ),
            selected = youtubeCanvasMode.name,
            onSelect = {
                artworkPrefs.setYouTubeCanvasMode(YouTubeCanvasMode.valueOf(it))
                VisualMediaManager.clearCache(context)
                showYtModeDialog = false
            },
            onDismiss = { showYtModeDialog = false }
        )
    }

    if (showArtistResolutionDialog) {
        ArtworkSingleChoiceDialog(
            title = "Artist video quality",
            options = listOf("LOW" to "Low (480p)", "MEDIUM" to "Medium (720p)", "HIGH" to "High (1080p)"),
            selected = appleArtistVideoResolution,
            onSelect = {
                artworkPrefs.setAppleArtistVideoResolution(it)
                showArtistResolutionDialog = false
            },
            onDismiss = { showArtistResolutionDialog = false }
        )
    }
}

@Composable
private fun ArtworkSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    val accentColor = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onBackground
    val textSub = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) accentColor else accentColor.copy(alpha = 0.38f),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) textColor else textColor.copy(alpha = 0.38f)
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = if (enabled) textSub else textSub.copy(alpha = 0.38f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accentColor
            )
        )
    }
}

@Composable
private fun ArtworkActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val accentColor = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onBackground
    val textSub = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) accentColor else accentColor.copy(alpha = 0.38f),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) textColor else textColor.copy(alpha = 0.38f)
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = if (enabled) textSub else textSub.copy(alpha = 0.38f)
            )
        }
    }
}

@Composable
private fun ArtworkSingleChoiceDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val accentColor = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onBackground

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                options.forEach { (id, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(id) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selected == id),
                            onClick = { onSelect(id) },
                            colors = RadioButtonDefaults.colors(selectedColor = accentColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(label, fontSize = 15.sp, color = textColor)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = accentColor)
            }
        }
    )
}
