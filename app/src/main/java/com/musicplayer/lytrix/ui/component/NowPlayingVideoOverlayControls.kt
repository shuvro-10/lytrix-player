package com.musicplayer.lytrix.ui.component

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
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
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.ui.AspectRatioFrameLayout
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
            val format = videoPlayer?.videoFormat
            val width = format?.width ?: 16
            val height = format?.height ?: 9
            // Android enforces PiP aspect ratio between 1:2.39 (0.418) and 2.39:1 (2.39)
            val rawRatio = if (width > 0 && height > 0) width.toFloat() / height.toFloat() else 16f / 9f
            val clampedRatio = rawRatio.coerceIn(0.42f, 2.38f)
            val rational = if (clampedRatio >= 1f) {
                Rational((clampedRatio * 1000).toInt(), 1000)
            } else {
                Rational(1000, (1000 / clampedRatio).toInt())
            }
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(rational)
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
    val resizeMode by (viewModel?.videoResizeMode?.collectAsState() ?: remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) })

    Box(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Aspect Ratio / Scale Adjust (Fit vs Zoom/Fill)
            FilledTonalIconButton(
                onClick = {
                    viewModel?.toggleVideoResizeMode()
                },
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = if (resizeMode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM)
                        Color(0xFF2196F3).copy(alpha = 0.85f)
                    else
                        Color.Black.copy(alpha = 0.65f),
                    contentColor = Color.White
                ),
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AspectRatio,
                    contentDescription = if (resizeMode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM) "Zoomed (Fill)" else "Fit to screen",
                    modifier = Modifier.size(18.dp)
                )
            }

            // 2. Picture in Picture Button
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

            // 3. Landscape Fullscreen Button
            FilledTonalIconButton(
                onClick = {
                    if (onFullscreenToggle != null) {
                        onFullscreenToggle()
                    } else if (activity != null) {
                        val isLandscape = activity.resources.configuration.orientation ==
                            Configuration.ORIENTATION_LANDSCAPE
                        activity.requestedOrientation = if (isLandscape) {
                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        } else {
                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
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
