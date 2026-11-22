package com.musicplayer.lytrix.aod

import android.service.dreams.DreamService
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.musicplayer.lytrix.R
import com.musicplayer.lytrix.service.PlaybackService
import com.musicplayer.lytrix.ui.PlayerViewModel
import com.musicplayer.lytrix.ui.aod.AlwaysOnDisplayOverlay
import kotlinx.coroutines.delay

/**
 * Android System Screensaver (DreamService) rendering Lytrix's AMOLED Always-On Display
 * while charging or docked, connected live to [PlaybackService] and [PlayerViewModel].
 */
class LytrixDreamService :
    DreamService(),
    LifecycleOwner,
    SavedStateRegistryOwner,
    OnBackPressedDispatcherOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val backPressedDispatcher = OnBackPressedDispatcher { finish() }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val onBackPressedDispatcher: OnBackPressedDispatcher get() = backPressedDispatcher

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        if (!AodPreferences.read(this).dreamEnabled) {
            finish()
            return
        }

        isInteractive = true
        isFullscreen = true
        isScreenBright = false

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@LytrixDreamService)
            setViewTreeSavedStateRegistryOwner(this@LytrixDreamService)
            setViewTreeOnBackPressedDispatcherOwner(this@LytrixDreamService)

            setContent {
                val amoledScheme = darkColorScheme(
                    background = Color.Black,
                    surface = Color.Black,
                    onBackground = Color.White,
                    onSurface = Color.White
                )
                MaterialTheme(colorScheme = amoledScheme) {
                    val vm = PlayerViewModel.activeInstance
                    if (vm != null) {
                        val currentSong by vm.currentSong.collectAsState()
                        val isPlaying by vm.isPlaying.collectAsState()
                        val currentPosition by vm.currentPosition.collectAsState()
                        val duration by vm.duration.collectAsState()
                        val currentLyrics by vm.currentLyrics.collectAsState()

                        AlwaysOnDisplayOverlay(
                            title = currentSong?.title ?: getString(R.string.app_name),
                            artist = currentSong?.artist ?: "",
                            album = currentSong?.album ?: "",
                            artworkData = currentSong?.albumArtUriString,
                            lyricsText = currentLyrics,
                            isPlaying = isPlaying,
                            positionMs = currentPosition,
                            durationMs = duration,
                            canSkipPrev = true,
                            canSkipNext = true,
                            accentColor = Color(0xFF3182CE),
                            onPlayPause = { vm.playPause() },
                            onSkipPrev = { vm.skipPrevious() },
                            onSkipNext = { vm.skipNext() },
                            onSeek = { vm.seekTo(it) },
                            onExit = { finish() }
                        )
                    } else {
                        var snapshot by remember {
                            mutableStateOf(PlaybackService.activeInstance?.currentDreamSnapshot())
                        }
                        LaunchedEffect(Unit) {
                            while (true) {
                                snapshot = PlaybackService.activeInstance?.currentDreamSnapshot()
                                delay(500L)
                            }
                        }

                        val snap = snapshot
                        AlwaysOnDisplayOverlay(
                            title = snap?.title ?: getString(R.string.app_name),
                            artist = snap?.artist ?: "",
                            album = snap?.album ?: "",
                            artworkData = snap?.artworkUri,
                            lyricsText = null,
                            isPlaying = snap?.isPlaying ?: false,
                            positionMs = snap?.positionMs ?: 0L,
                            durationMs = snap?.durationMs ?: 0L,
                            canSkipPrev = snap?.canSkipPrev ?: false,
                            canSkipNext = snap?.canSkipNext ?: false,
                            accentColor = Color(0xFF3182CE),
                            onPlayPause = { PlaybackService.activeInstance?.togglePlayPause() },
                            onSkipPrev = { PlaybackService.activeInstance?.skipToPreviousTrack() },
                            onSkipNext = { PlaybackService.activeInstance?.skipToNextTrack() },
                            onSeek = { PlaybackService.activeInstance?.seekToPosition(it) },
                            onExit = { finish() }
                        )
                    }
                }
            }
        }
        setContentView(composeView)
    }

    override fun onDetachedFromWindow() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        super.onDetachedFromWindow()
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }
}
