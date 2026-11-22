package com.musicplayer.lytrix.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles scheduled background checks for artist new releases and device boot reschedule.
 */
class NewReleaseReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED) {
            if (NewReleaseChecker.isEnabled(context)) {
                NewReleaseChecker.schedulePeriodicChecks(context)
            }
            return
        }
        if (action == NewReleaseChecker.ACTION_CHECK_RELEASES && NewReleaseChecker.isEnabled(context)) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    NewReleaseChecker.checkForNewReleases(context.applicationContext, pushNotification = true)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
