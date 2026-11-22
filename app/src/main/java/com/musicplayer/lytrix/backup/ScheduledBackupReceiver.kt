package com.musicplayer.lytrix.backup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Triggers periodic local backups on AlarmManager cadence and restores schedules on boot.
 */
class ScheduledBackupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED) {
            ScheduledBackupManager.rescheduleIfEnabled(context)
            return
        }
        if (action == ScheduledBackupManager.ACTION_RUN_AUTO_BACKUP) {
            val status = ScheduledBackupManager.getStatus(context)
            if (status.interval == AutoBackupInterval.OFF) return
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    ScheduledBackupManager.runAutoBackupNow(context.applicationContext)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
