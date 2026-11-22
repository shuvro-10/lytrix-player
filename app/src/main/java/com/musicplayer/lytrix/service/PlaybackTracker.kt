package com.musicplayer.lytrix.service

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.exoplayer.analytics.AnalyticsListener
import com.musicplayer.lytrix.db.PlaytimeDao
import com.musicplayer.lytrix.db.PlaytimeEntity
import com.musicplayer.lytrix.ui.PrivacyPreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackTracker(
    private val playtimeDao: PlaytimeDao,
    private val context: Context? = null
) : AnalyticsListener {
    private var isPlaying = false
    private var lastPlayStartTime: Long = 0

    override fun onIsPlayingChanged(eventTime: AnalyticsListener.EventTime, isPlaying: Boolean) {
        if (this.isPlaying == isPlaying) return
        this.isPlaying = isPlaying

        val now = System.currentTimeMillis()
        if (isPlaying) {
            lastPlayStartTime = now
        } else {
            recordPlaytime(now)
        }
    }

    override fun onPlaybackStateChanged(eventTime: AnalyticsListener.EventTime, state: Int) {
        if (state == Player.STATE_ENDED || state == Player.STATE_IDLE) {
            if (isPlaying) {
                recordPlaytime(System.currentTimeMillis())
                isPlaying = false
            }
        }
    }
    
    private fun recordPlaytime(now: Long) {
        if (context != null && PrivacyPreferencesManager.isPauseLocalListenHistory(context)) {
            lastPlayStartTime = 0
            return
        }
        if (lastPlayStartTime > 0) {
            val durationPlayedMs = now - lastPlayStartTime
            if (durationPlayedMs > 1000) { // Only track if played for > 1 sec
                CoroutineScope(Dispatchers.IO).launch {
                    playtimeDao.insertPlaytime(PlaytimeEntity(durationPlayedMs = durationPlayedMs))
                }
            }
            lastPlayStartTime = 0
        }
    }
}
