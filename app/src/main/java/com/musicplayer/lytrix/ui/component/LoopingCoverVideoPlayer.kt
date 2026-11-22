package com.musicplayer.lytrix.ui.component

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.musicplayer.lytrix.R
import com.musicplayer.lytrix.model.Song
import com.musicplayer.lytrix.spotify.canvas.VisualMediaType
import com.musicplayer.lytrix.spotify.canvas.CanvasVideoPlayerManager

private val SquircleShape = RoundedCornerShape(20.dp)

@OptIn(UnstableApi::class)
@Composable
fun LoopingCoverVideoPlayer(
    videoUrl: String,
    mediaType: VisualMediaType,
    song: Song,
    artData: Any?,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    gesturesEnabled: Boolean = true,
    isGuest: Boolean = false,
    clips: List<com.musicplayer.lytrix.artwork.YouTubeCanvasClip> = emptyList(),
    clipShape: androidx.compose.ui.graphics.Shape = SquircleShape,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val exoPlayer = remember(videoUrl, song.id, clips) {
        CanvasVideoPlayerManager.getOrCreatePlayer(context, videoUrl, song.id, isPlaying, clips)
    }

    var isVideoReady by remember(videoUrl, song.id) {
        mutableStateOf(exoPlayer.playbackState == Player.STATE_READY)
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
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
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    LaunchedEffect(isPlaying) {
        exoPlayer.playWhenReady = isPlaying
    }

    val imageRequest = remember(artData, song.uriString) {
        ImageRequest.Builder(context)
            .data(artData ?: song.albumArtUriString ?: song.uriString)
            .crossfade(true)
            .build()
    }

    val videoAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isVideoReady) 1f else 0f,
        animationSpec = tween(400),
        label = "videoAlpha"
    )

    PlayerCoverWithGestures(
        gesturesEnabled = gesturesEnabled,
        isGuest = isGuest,
        isPlaying = isPlaying,
        currentPosition = currentPositionMs,
        duration = durationMs,
        onSeek = onSeek,
        onPlayPause = onPlayPause,
        onSkipNext = onSkipNext,
        onSkipPrevious = onSkipPrevious,
        onLongPress = onLongPress,
        swipeEnabled = true,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(clipShape)
                .background(Color(0xFF141416)),
            contentAlignment = Alignment.Center
        ) {
            // Static album art shown while video buffers
            AsyncImage(
                model = imageRequest,
                contentDescription = "Cover Art",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Video player view with TextureView (prevents SurfaceView scroll punch-through & enables DstIn fade mask)
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
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(videoAlpha)
            )


        }
    }
}
