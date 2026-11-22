package com.musicplayer.lytrix.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.musicplayer.lytrix.ui.aod.AlwaysOnDisplayOverlay
import com.musicplayer.lytrix.ui.theme.LocalAccentColor

/**
 * Displays Lytrix's AMOLED Always-On Display + Pocket Touch Lock overlay wired to live playback state.
 */
@Composable
fun PowerSavingModeScreen(
    initialLocked: Boolean = false,
    onExit: () -> Unit
) {
    val vm = PlayerViewModel.activeInstance
    val accent = LocalAccentColor.current
    if (vm != null) {
        val currentSong by vm.currentSong.collectAsState()
        val isPlaying by vm.isPlaying.collectAsState()
        val currentPosition by vm.currentPosition.collectAsState()
        val duration by vm.duration.collectAsState()
        val currentLyrics by vm.currentLyrics.collectAsState()

        AlwaysOnDisplayOverlay(
            title = currentSong?.title ?: "Lytrix Player",
            artist = currentSong?.artist ?: "",
            album = currentSong?.album ?: "",
            artworkData = currentSong?.albumArtUriString,
            lyricsText = currentLyrics,
            isPlaying = isPlaying,
            positionMs = currentPosition,
            durationMs = duration,
            canSkipPrev = true,
            canSkipNext = true,
            accentColor = accent,
            initialLocked = initialLocked,
            onPlayPause = { vm.playPause() },
            onSkipPrev = { vm.skipPrevious() },
            onSkipNext = { vm.skipNext() },
            onSeek = { vm.seekTo(it) },
            onExit = onExit
        )
    } else {
        AlwaysOnDisplayOverlay(
            title = "Lytrix Player",
            artist = "Always-On Display",
            album = "",
            artworkData = null,
            lyricsText = null,
            isPlaying = false,
            positionMs = 0L,
            durationMs = 0L,
            canSkipPrev = false,
            canSkipNext = false,
            accentColor = accent,
            initialLocked = initialLocked,
            onPlayPause = {},
            onSkipPrev = {},
            onSkipNext = {},
            onSeek = {},
            onExit = onExit
        )
    }
}
