package com.musicplayer.lytrix.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/**
 * Schedules, reschedules, and cancels [MusicAlarmEntry] alarms with [AlarmManager].
 */
object MusicAlarmScheduler {
    const val ACTION_ALARM_TRIGGER = "com.musicplayer.lytrix.action.MUSIC_ALARM_TRIGGER"
    const val ACTION_ALARM_SNOOZE = "com.musicplayer.lytrix.action.MUSIC_ALARM_SNOOZE"
    const val ACTION_ALARM_DISMISS = "com.musicplayer.lytrix.action.MUSIC_ALARM_DISMISS"
    const val ACTION_OPEN_ALARM_PLAY = "com.musicplayer.lytrix.action.TRIGGER_MUSIC_ALARM"
    const val EXTRA_ALARM_ID = "extra_alarm_id"

    fun rescheduleAll(context: Context) {
        val alarms = MusicAlarmStore.loadAll(context)
        alarms.forEach { alarm ->
            if (alarm.enabled) schedule(context, alarm) else cancel(context, alarm.id)
        }
    }

    fun schedule(context: Context, alarm: MusicAlarmEntry) {
        if (!alarm.enabled) {
            cancel(context, alarm.id)
            return
        }
        val triggerAtMillis = computeNextTriggerMillis(alarm) ?: return
        scheduleExactAt(context, alarm.id, triggerAtMillis)
    }

    fun scheduleSnooze(context: Context, alarmId: String, minutes: Int) {
        val triggerAtMillis = System.currentTimeMillis() + minutes.coerceIn(1, 30) * 60_000L
        scheduleExactAt(context, alarmId, triggerAtMillis)
    }

    fun scheduleTestInSeconds(context: Context, alarm: MusicAlarmEntry, seconds: Int = 5): Long {
        val triggerAt = System.currentTimeMillis() + seconds.coerceIn(2, 60) * 1000L
        MusicAlarmStore.upsert(context, alarm.copy(enabled = true))
        scheduleExactAt(context, alarm.id, triggerAt)
        return triggerAt
    }

    fun cancel(context: Context, alarmId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        alarmManager.cancel(triggerPendingIntent(context, alarmId))
    }

    fun computeNextTriggerMillis(alarm: MusicAlarmEntry, nowMillis: Long = System.currentTimeMillis()): Long? {
        if (!alarm.enabled) return null
        val nowCal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val candidate = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (alarm.repeatDays.isEmpty()) {
            if (!candidate.after(nowCal)) {
                candidate.add(Calendar.DAY_OF_YEAR, 1)
            }
            return candidate.timeInMillis
        }

        for (offset in 0..7) {
            val probe = (candidate.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, offset)
            }
            val dayOfWeek = probe.get(Calendar.DAY_OF_WEEK)
            if (dayOfWeek in alarm.repeatDays && probe.after(nowCal)) {
                return probe.timeInMillis
            }
        }
        return null
    }

    private fun scheduleExactAt(context: Context, alarmId: String, triggerAtMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = triggerPendingIntent(context, alarmId)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        try {
            if (canExact) {
                alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMillis, pi), pi)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        }
    }

    fun triggerPendingIntent(context: Context, alarmId: String): PendingIntent {
        val intent = Intent(context, MusicAlarmReceiver::class.java).apply {
            action = ACTION_ALARM_TRIGGER
            putExtra(EXTRA_ALARM_ID, alarmId)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCodeFor(alarmId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun requestCodeFor(alarmId: String): Int = 41_000 + (alarmId.hashCode() and 0x7FFF)
}
