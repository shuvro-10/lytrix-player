package com.musicplayer.lytrix.playback

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Verbatim port of MetroList's SleepTimerController.kt & SleepTimerDefaults.kt.
 */
data class SleepTimerDefaults(
    val durationMinutes: Int = 15,
    val finishCurrentSong: Boolean = false,
    val fadeOut: Boolean = false,
)

fun sleepTimerDurationMs(
    hours: String,
    minutes: String,
): Long? {
    if (hours.length > 2 || minutes.length > 2 || (hours + minutes).any { it !in '0'..'9' }) return null
    val hour = if (hours.isEmpty()) 0 else hours.toIntOrNull() ?: return null
    val minute = if (minutes.isEmpty()) 0 else minutes.toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return ((hour * 60L + minute) * 60_000L).takeIf { it > 0L }
}

object SleepTimerPrefsStore {
    private const val PREFS_NAME = "metro_sleep_timer_prefs"
    private const val KEY_DURATION_MINUTES = "duration_minutes"
    private const val KEY_FINISH_CURRENT_SONG = "finish_current_song"
    private const val KEY_FADE_OUT = "fade_out"

    fun load(context: Context): SleepTimerDefaults {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return SleepTimerDefaults(
            durationMinutes = prefs.getInt(KEY_DURATION_MINUTES, 15).coerceIn(1, 23 * 60 + 59),
            finishCurrentSong = prefs.getBoolean(KEY_FINISH_CURRENT_SONG, false),
            fadeOut = prefs.getBoolean(KEY_FADE_OUT, false),
        )
    }

    fun save(context: Context, defaults: SleepTimerDefaults) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_DURATION_MINUTES, defaults.durationMinutes.coerceIn(1, 23 * 60 + 59))
            .putBoolean(KEY_FINISH_CURRENT_SONG, defaults.finishCurrentSong)
            .putBoolean(KEY_FADE_OUT, defaults.fadeOut)
            .apply()
    }
}

data class SleepTimerState(
    val isActive: Boolean = false,
    val remainingMs: Long? = null,
    val totalDurationMs: Long? = null,
    val stopAfterCurrentSong: Boolean = false,
    val fadeOut: Boolean = false,
    val finishCurrentSong: Boolean = false,
)

interface SleepTimerPlayer {
    val isAvailable: Boolean
    val currentPositionMs: Long
    val durationMs: Long
    val playbackProgress: Flow<Pair<Long, Long>>
    var pauseAtEndOfMediaItems: Boolean

    fun pause()

    fun cancelCrossfadeTransition()

    fun setSleepTimerGain(gain: Float)
}

fun formatSleepTimerRemaining(remainingMs: Long?): String {
    if (remainingMs == null) return "--:--"
    val totalSeconds = ((remainingMs.coerceAtLeast(0L) + 999L) / 1000L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

open class MetroSleepTimerController(
    private val scope: CoroutineScope,
    private val player: SleepTimerPlayer,
    private val elapsedRealtimeMs: () -> Long = { SystemClock.elapsedRealtime() },
    private val updateIntervalMs: Long = 500L,
) {
    companion object {
        private const val FADE_DURATION_MS = 60_000L
    }

    private var timerJob: Job? = null
    private var appliedGain = 1f
    private var fadeDurationMs: Long? = null
    private val _state = MutableStateFlow(SleepTimerState())
    val state: StateFlow<SleepTimerState> = _state.asStateFlow()

    val isStopAfterCurrentSong: Boolean
        get() = _state.value.stopAfterCurrentSong

    fun start(
        durationMs: Long,
        fadeOut: Boolean,
        finishCurrentSong: Boolean = false,
    ) {
        if (durationMs <= 0L) return
        cancel()
        val deadline = elapsedRealtimeMs() + durationMs
        fadeDurationMs = minOf(FADE_DURATION_MS, durationMs)
        _state.value = SleepTimerState(
            isActive = true,
            remainingMs = durationMs,
            totalDurationMs = durationMs,
            stopAfterCurrentSong = false,
            fadeOut = fadeOut,
            finishCurrentSong = finishCurrentSong,
        )
        timerJob =
            scope.launch {
                while (isActive) {
                    val remainingMs = (deadline - elapsedRealtimeMs()).coerceAtLeast(0L)
                    _state.value = _state.value.copy(remainingMs = remainingMs)
                    if (!finishCurrentSong) updateFade(remainingMs)
                    if (remainingMs == 0L) {
                        if (finishCurrentSong) {
                            stopAfterCurrentSong(fadeOut)
                        } else {
                            player.pause()
                            finish()
                        }
                        break
                    }
                    val subscribers = _state.subscriptionCount.value
                    val untilFade = if (fadeOut && !finishCurrentSong) (remainingMs - FADE_DURATION_MS).coerceAtLeast(0L) else remainingMs
                    val waitMs = if (subscribers > 0 || untilFade == 0L) updateIntervalMs.coerceAtMost(remainingMs) else untilFade
                    withTimeoutOrNull(waitMs.coerceAtLeast(50L)) { _state.subscriptionCount.first { it != subscribers } }
                }
            }
    }

    fun stopAfterCurrentSong(fadeOut: Boolean) {
        cancel()
        player.cancelCrossfadeTransition()
        if (!player.isAvailable) return
        val remainingMs = currentTrackRemainingMs()
        fadeDurationMs = remainingMs?.coerceIn(1L, FADE_DURATION_MS)
        player.pauseAtEndOfMediaItems = true
        _state.value = SleepTimerState(
            isActive = true,
            remainingMs = remainingMs,
            totalDurationMs = remainingMs,
            stopAfterCurrentSong = true,
            fadeOut = fadeOut,
        )
        timerJob =
            scope.launch {
                player.playbackProgress.collect { (position, duration) ->
                    val remaining = if (duration > 0L) (duration - position).coerceAtLeast(0L) else null
                    _state.value = _state.value.copy(remainingMs = remaining)
                    updateFade(remaining)
                }
            }
    }

    fun cancel() {
        timerJob?.cancel()
        timerJob = null
        player.pauseAtEndOfMediaItems = false
        appliedGain = 1f
        fadeDurationMs = null
        player.setSleepTimerGain(1f)
        _state.value = SleepTimerState()
    }

    fun onEndOfMediaItem() {
        if (!isStopAfterCurrentSong) return
        player.pause()
        finish()
    }

    fun onDestroy() {
        cancel()
    }

    private fun finish() {
        timerJob?.cancel()
        timerJob = null
        player.pauseAtEndOfMediaItems = false
        appliedGain = 1f
        fadeDurationMs = null
        player.setSleepTimerGain(1f)
        _state.value = SleepTimerState()
    }

    private fun currentTrackRemainingMs(): Long? {
        if (player.durationMs <= 0L) return null
        return (player.durationMs - player.currentPositionMs).coerceAtLeast(0L)
    }

    private fun applyGain(gain: Float) {
        if (appliedGain == gain) return
        appliedGain = gain
        player.setSleepTimerGain(gain)
    }

    private fun updateFade(remainingMs: Long?) {
        if (fadeDurationMs == null && remainingMs != null) fadeDurationMs = remainingMs.coerceIn(1L, FADE_DURATION_MS)
        val fadeDuration = fadeDurationMs ?: FADE_DURATION_MS
        val gain =
            if (_state.value.fadeOut && remainingMs != null && remainingMs <= fadeDuration) {
                (remainingMs.toFloat() / fadeDuration).coerceIn(0f, 1f)
            } else {
                1f
            }
        applyGain(gain)
    }
}
