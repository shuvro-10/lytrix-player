package com.musicplayer.lytrix.eq

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.musicplayer.lytrix.eq.audio.CustomEqualizerAudioProcessor
import com.musicplayer.lytrix.eq.data.EQProfileRepository
import com.musicplayer.lytrix.eq.data.EqMode
import com.musicplayer.lytrix.eq.data.ParametricEQ
import com.musicplayer.lytrix.eq.data.SavedEQProfile
import timber.log.Timber
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Central coordinator managing all active ExoPlayer CustomEqualizerAudioProcessor instances.
 */
@OptIn(UnstableApi::class)
object LytrixEqualizerService {

    private const val TAG = "LytrixEqualizerService"
    private val audioProcessors = CopyOnWriteArrayList<CustomEqualizerAudioProcessor>()

    @Volatile
    private var pendingParametricEq: ParametricEQ? = null

    @Volatile
    private var shouldDisable = false

    fun addAudioProcessor(processor: CustomEqualizerAudioProcessor) {
        if (!audioProcessors.contains(processor)) {
            audioProcessors.add(processor)
        }
        Timber.tag(TAG).d("Audio processor attached. Active count: ${audioProcessors.size}")

        val currentEq = pendingParametricEq
        if (shouldDisable || currentEq == null) {
            processor.disable()
        } else {
            processor.applyProfile(currentEq)
        }
    }

    fun removeAudioProcessor(processor: CustomEqualizerAudioProcessor) {
        audioProcessors.remove(processor)
    }

    fun restoreSavedState(context: Context) {
        val repo = EQProfileRepository.getInstance(context)
        syncFromRepository(repo)
    }

    fun syncFromRepository(repo: EQProfileRepository) {
        val activeEq = repo.resolveActiveParametricEQ()
        if (activeEq == null) {
            disable()
        } else {
            applyParametricEQ(activeEq)
        }
    }

    fun applyProfile(profile: SavedEQProfile) {
        applyParametricEQ(
            ParametricEQ(
                preamp = profile.preamp,
                bands = profile.bands
            )
        )
    }

    fun applyParametricEQ(parametricEQ: ParametricEQ) {
        pendingParametricEq = parametricEQ
        shouldDisable = false
        audioProcessors.forEach { processor ->
            runCatching { processor.applyProfile(parametricEQ) }
                .onFailure { Timber.tag(TAG).e(it, "Failed to apply parametric EQ") }
        }
    }

    fun disable() {
        shouldDisable = true
        pendingParametricEq = null
        audioProcessors.forEach { processor ->
            runCatching { processor.disable() }
                .onFailure { Timber.tag(TAG).e(it, "Failed to disable EQ processor") }
        }
    }
}
