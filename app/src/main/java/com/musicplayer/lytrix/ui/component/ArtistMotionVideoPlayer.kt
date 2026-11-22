package com.musicplayer.lytrix.ui.component

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.musicplayer.lytrix.spotify.canvas.CanvasVideoPlayerManager

@OptIn(UnstableApi::class)
@Composable
fun ArtistMotionVideoPlayer(
    videoUrl: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isVideoReady by remember(videoUrl) { mutableStateOf(false) }

    val exoPlayer = remember(videoUrl) {
        CanvasVideoPlayerManager.buildPlayer(context, videoUrl, isPlaying = true).apply {
            addListener(object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    isVideoReady = true
                }
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        isVideoReady = true
                    }
                }
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    isVideoReady = false
                }
            })
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    val videoAlpha by animateFloatAsState(
        targetValue = if (isVideoReady) 1f else 0f,
        animationSpec = tween(400),
        label = "artistVideoAlpha"
    )

    AndroidView(
        factory = { ctx ->
            (android.view.LayoutInflater.from(ctx).inflate(
                com.musicplayer.lytrix.R.layout.apple_music_texture_player_view,
                null
            ) as PlayerView).apply {
                player = exoPlayer
                useController = false
                isClickable = false
                isFocusable = false
                setOnTouchListener { _, _ -> false }
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        update = { playerView ->
            if (playerView.player != exoPlayer) {
                playerView.player = exoPlayer
            }
        },
        modifier = modifier
            .fillMaxSize()
            .alpha(videoAlpha)
    )
}
