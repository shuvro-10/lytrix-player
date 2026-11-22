package com.musicplayer.lytrix.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.musicplayer.lytrix.MainActivity
import com.musicplayer.lytrix.R
import com.musicplayer.lytrix.service.PlaybackService
import com.musicplayer.lytrix.ui.PlayerViewModel

/**
 * Receives AlarmManager triggers, snooze/dismiss notification actions, and device boot events.
 */
class MusicAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val alarmId = intent.getStringExtra(MusicAlarmScheduler.EXTRA_ALARM_ID)

        when (action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                MusicAlarmScheduler.rescheduleAll(context)
            }

            MusicAlarmScheduler.ACTION_ALARM_DISMISS -> {
                stopFallbackRingtone()
                dismissNotification(context, alarmId)
                PlaybackService.pauseIfPlaying()
            }

            MusicAlarmScheduler.ACTION_ALARM_SNOOZE -> {
                stopFallbackRingtone()
                dismissNotification(context, alarmId)
                PlaybackService.pauseIfPlaying()
                if (alarmId != null) {
                    val alarm = MusicAlarmStore.getById(context, alarmId)
                    MusicAlarmScheduler.scheduleSnooze(context, alarmId, alarm?.snoozeMinutes ?: 10)
                }
            }

            MusicAlarmScheduler.ACTION_ALARM_TRIGGER -> {
                if (alarmId == null) return
                val alarm = MusicAlarmStore.getById(context, alarmId) ?: return
                acquireBriefWakeLock(context)

                // Disable one-shot alarms or schedule the next repeating occurrence.
                if (alarm.repeatDays.isEmpty()) {
                    MusicAlarmStore.setEnabled(context, alarm.id, false)
                } else {
                    MusicAlarmScheduler.schedule(context, alarm)
                }

                showAlarmNotification(context, alarm)
                triggerMusicPlayback(context, alarm)
            }
        }
    }

    private fun triggerMusicPlayback(context: Context, alarm: MusicAlarmEntry) {
        val activeVm = PlayerViewModel.activeInstance
        if (activeVm != null) {
            Handler(Looper.getMainLooper()).post {
                val started = activeVm.playMusicAlarm(context, alarm)
                if (!started) {
                    playFallbackRingtone(context)
                }
            }
            return
        }

        // Launch MainActivity so cold-started app can load the queue and play with fade-in.
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            action = MusicAlarmScheduler.ACTION_OPEN_ALARM_PLAY
            putExtra(MusicAlarmScheduler.EXTRA_ALARM_ID, alarm.id)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
        }
        try {
            context.startActivity(launchIntent)
        } catch (_: Exception) {
            // Android 10+ background activity restriction: full-screen notification remains clickable,
            // plus play a gentle fallback alarm tone if needed so user never oversleeps.
            playFallbackRingtone(context)
        }
    }

    private fun showAlarmNotification(context: Context, alarm: MusicAlarmEntry) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        ensureChannel(nm)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = MusicAlarmScheduler.ACTION_OPEN_ALARM_PLAY
            putExtra(MusicAlarmScheduler.EXTRA_ALARM_ID, alarm.id)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val openPi = PendingIntent.getActivity(
            context,
            MusicAlarmScheduler.requestCodeFor(alarm.id) + 1,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(context, MusicAlarmReceiver::class.java).apply {
            action = MusicAlarmScheduler.ACTION_ALARM_SNOOZE
            putExtra(MusicAlarmScheduler.EXTRA_ALARM_ID, alarm.id)
        }
        val snoozePi = PendingIntent.getBroadcast(
            context,
            MusicAlarmScheduler.requestCodeFor(alarm.id) + 2,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(context, MusicAlarmReceiver::class.java).apply {
            action = MusicAlarmScheduler.ACTION_ALARM_DISMISS
            putExtra(MusicAlarmScheduler.EXTRA_ALARM_ID, alarm.id)
        }
        val dismissPi = PendingIntent.getBroadcast(
            context,
            MusicAlarmScheduler.requestCodeFor(alarm.id) + 3,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val timeText = String.format("%02d:%02d", alarm.hour, alarm.minute)
        val sourceText = if (alarm.sourceType == AlarmSourceType.SPECIFIC_SONG && alarm.songTitle.isNotBlank()) {
            alarm.songTitle
        } else {
            alarm.playlistTitle.ifBlank { alarm.sourceType.displayName }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("${alarm.label.ifBlank { "Music Alarm" }} • $timeText")
            .setContentText("Playing $sourceText")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(openPi)
            .setFullScreenIntent(openPi, true)
            .addAction(0, "Snooze ${alarm.snoozeMinutes}m", snoozePi)
            .addAction(0, "Dismiss", dismissPi)
            .build()

        nm.notify(notificationIdFor(alarm.id), notification)
    }

    private fun dismissNotification(context: Context, alarmId: String?) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (alarmId != null) {
            nm.cancel(notificationIdFor(alarmId))
        }
    }

    private fun ensureChannel(nm: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Alarm Clock",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Wakes you up with your selected Lytrix music or playlist"
                setSound(null, null)
                enableVibration(true)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun acquireBriefWakeLock(context: Context) {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            @Suppress("DEPRECATION")
            val wl = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "lytrix:MusicAlarmWakeLock"
            )
            wl.acquire(10_000L)
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val CHANNEL_ID = "lytrix_music_alarm_channel"
        @Volatile
        private var fallbackRingtone: Ringtone? = null

        fun playFallbackRingtone(context: Context) {
            try {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val ringtone = RingtoneManager.getRingtone(context.applicationContext, uri)
                fallbackRingtone?.stop()
                fallbackRingtone = ringtone
                ringtone?.play()
            } catch (_: Exception) {
            }
        }

        fun stopFallbackRingtone() {
            try {
                fallbackRingtone?.stop()
                fallbackRingtone = null
            } catch (_: Exception) {
            }
        }

        fun notificationIdFor(alarmId: String): Int = 42_000 + (alarmId.hashCode() and 0x0FFF)
    }
}
