package com.example.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.data.ReminderSettings
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object BackgroundReminderScheduler {
    const val ACTION_DAILY_SMART_REMINDER = "com.example.kua.ACTION_DAILY_SMART_REMINDER"
    private const val REQUEST_CODE_DAILY_REMINDER = 41022

    fun scheduleDailyReminder(context: Context, settings: ReminderSettings): Long? {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return null
        val pendingIntent = buildPendingIntent(context)

        if (!settings.autoPhoneNotifEnabled) {
            alarmManager.cancel(pendingIntent)
            return null
        }

        val triggerAtMillis = computeNextTriggerMillis(settings.reminderTime)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        } else {
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
        return triggerAtMillis
    }

    fun cancelDailyReminder(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        alarmManager.cancel(buildPendingIntent(context))
    }

    fun computeNextTriggerMillis(reminderTime: String, nowMillis: Long = System.currentTimeMillis()): Long {
        val parts = reminderTime.trim().split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 7
        val minute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0

        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= nowMillis + 60_000L) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    fun formatNextTriggerLabel(reminderTime: String): String {
        val nextMillis = computeNextTriggerMillis(reminderTime)
        val formatter = SimpleDateFormat("EEEE, dd MMM • HH:mm 'WIB'", Locale("id", "ID"))
        return formatter.format(Date(nextMillis))
    }

    private fun buildPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context.applicationContext, SmartReminderReceiver::class.java).apply {
            action = ACTION_DAILY_SMART_REMINDER
        }
        return PendingIntent.getBroadcast(
            context.applicationContext,
            REQUEST_CODE_DAILY_REMINDER,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
