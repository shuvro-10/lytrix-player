package com.musicplayer.lytrix.ui.component

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.musicplayer.lytrix.MainActivity
import com.musicplayer.lytrix.ui.PlayerViewModel

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

fun safeEnterPip(activity: Activity?, playerViewModel: PlayerViewModel?) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
        runCatching {
            val videoPlayer = playerViewModel?.getVideoPlayer()
            val isPlaying = playerViewModel?.isPlaying?.value ?: true
            val format = videoPlayer?.videoFormat
            val width = format?.width ?: 16
            val height = format?.height ?: 9
            // Android limits PiP aspect ratio between 1:2.39 (0.418) and 2.39:1 (2.39)
            val rawRatio = if (width > 0 && height > 0) width.toFloat() / height.toFloat() else 16f / 9f
            val clampedRatio = rawRatio.coerceIn(0.42f, 2.38f)
            val rational = if (clampedRatio >= 1f) {
                Rational((clampedRatio * 1000).toInt(), 1000)
            } else {
                Rational(1000, (1000 / clampedRatio).toInt())
            }

            val actions = mutableListOf<RemoteAction>()

            // 1. Previous Action
            val prevPendingIntent = PendingIntent.getBroadcast(
                activity,
                101,
                Intent(MainActivity.ACTION_PIP_PREV).setPackage(activity.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            actions.add(
                RemoteAction(
                    Icon.createWithResource(activity, android.R.drawable.ic_media_previous),
                    "Previous",
                    "Previous",
                    prevPendingIntent
                )
            )

            // 2. Play / Pause Action
            val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            val playPauseTitle = if (isPlaying) "Pause" else "Play"
            val playPausePendingIntent = PendingIntent.getBroadcast(
                activity,
                102,
                Intent(MainActivity.ACTION_PIP_PLAY_PAUSE).setPackage(activity.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            actions.add(
                RemoteAction(
                    Icon.createWithResource(activity, playPauseIcon),
                    playPauseTitle,
                    playPauseTitle,
                    playPausePendingIntent
                )
            )

            // 3. Next Action
            val nextPendingIntent = PendingIntent.getBroadcast(
                activity,
                103,
                Intent(MainActivity.ACTION_PIP_NEXT).setPackage(activity.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            actions.add(
                RemoteAction(
                    Icon.createWithResource(activity, android.R.drawable.ic_media_next),
                    "Next",
                    "Next",
                    nextPendingIntent
                )
            )

            val params = PictureInPictureParams.Builder()
                .setAspectRatio(rational)
                .setActions(actions)
                .build()
            activity.enterPictureInPictureMode(params)
        }.onFailure {
            it.printStackTrace()
        }
    }
}

@Composable
fun NowPlayingVideoOverlayControls(
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel? = PlayerViewModel.activeInstance,
    onFullscreenToggle: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val activity = context.findActivity()

    Box(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Picture in Picture Button
            FilledTonalIconButton(
                onClick = {
                    safeEnterPip(activity, viewModel)
                },
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.65f),
                    contentColor = Color.White
                ),
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PictureInPictureAlt,
                    contentDescription = "Picture in picture",
                    modifier = Modifier.size(18.dp)
                )
            }

            // 2. Landscape Fullscreen Button
            FilledTonalIconButton(
                onClick = {
                    if (onFullscreenToggle != null) {
                        onFullscreenToggle()
                    } else if (activity != null) {
                        val nextFullscreen = !(viewModel?.isLandscapeVideoFullscreen?.value ?: false)
                        viewModel?.isLandscapeVideoFullscreen?.value = nextFullscreen
                        activity.requestedOrientation = if (nextFullscreen) {
                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        } else {
                            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        }
                    }
                },
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.65f),
                    contentColor = Color.White
                ),
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Fullscreen,
                    contentDescription = "Fullscreen",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
