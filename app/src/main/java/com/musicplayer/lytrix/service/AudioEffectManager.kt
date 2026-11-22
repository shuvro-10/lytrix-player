package com.musicplayer.lytrix.service

import android.media.audiofx.Equalizer

object AudioEffectManager {
    var equalizer: Equalizer? = null
        private set

    fun init(audioSessionId: Int) {
        try {
            if (equalizer != null) {
                equalizer?.release()
                equalizer = null
            }
            if (audioSessionId != 0 && audioSessionId != androidx.media3.common.C.AUDIO_SESSION_ID_UNSET) {
                equalizer = Equalizer(0, audioSessionId)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun release() {
        equalizer?.release()
        equalizer = null
    }
}
