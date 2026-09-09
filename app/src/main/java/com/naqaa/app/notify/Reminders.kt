package com.naqaa.app.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.naqaa.app.NaqaaApplication
import com.naqaa.app.content.Content
import com.naqaa.app.data.Preferences
import com.naqaa.app.util.LocaleX
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.random.Random
import java.util.concurrent.Executors

/**
 * Daily encouragement: a short reminder to read the morning or evening remembrance, and a
 * motivational note chosen for the time of day. Times are fixed to keep the schedule easy
 * to reason about; the text itself is neutral so nothing private is shown on the shade.
 */
object Reminders {

    const val ACTION_REMINDER = "com.naqaa.app.action.DAILY_REMINDER"
    const val EXTRA_MORNING = "morning"

    fun schedule(context: Context, preferences: Preferences) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
        for (morning in listOf(true, false)) {
            val pending = pending(context, morning)
            manager.cancel(pending)
        }
        if (!preferences.adhkarReminders) return
        val now = Instant.now()
        listOf(true, false).forEach { morning ->
            val at = next(morning, now, ZoneId.systemDefault())
            val pending = pending(context, morning)
            if (exact) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pending)
            } else {
                manager.setWindow(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), WINDOW_MILLIS, pending)
            }
        }
    }

    fun cancel(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java)
        listOf(true, false).forEach { morning ->
            val existing = PendingIntent.getBroadcast(
                context, requestCode(morning),
                Intent(context, ReminderReceiver::class.java).setAction(ACTION_REMINDER),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (existing != null) {
                manager.cancel(existing)
                existing.cancel()
            }
        }
    }

    /** Next occurrence of the morning or evening reminder, always in the future. */
    fun next(morning: Boolean, now: Instant, zone: ZoneId): Instant {
        val target = if (morning) MORNING_MINUTE else EVENING_MINUTE
        val today = LocalDate.now(zone)
        val candidate = at(today, target, zone)
        return if (candidate.isAfter(now)) candidate else at(today.plusDays(1), target, zone)
    }

    private fun at(date: LocalDate, minuteOfDay: Int, zone: ZoneId): Instant =
        ZonedDateTime.of(date, java.time.LocalTime.of(minuteOfDay / 60, minuteOfDay % 60), zone).toInstant()

    private fun pending(context: Context, morning: Boolean): PendingIntent = PendingIntent.getBroadcast(
        context, requestCode(morning),
        Intent(context, ReminderReceiver::class.java).setAction(ACTION_REMINDER).putExtra(EXTRA_MORNING, morning),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun requestCode(morning: Boolean): Int = if (morning) 400 else 401

    const val MORNING_MINUTE = 6 * 60 + 30
    const val EVENING_MINUTE = 18 * 60
    private const val WINDOW_MILLIS = 10 * 60 * 1000L
}

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Reminders.ACTION_REMINDER) return
        val morning = intent.getBooleanExtra(Reminders.EXTRA_MORNING, true)
        val pending = goAsync()
        executor.execute {
            try {
                val application = context.applicationContext as NaqaaApplication
                val preferences = application.graph.current()
                if (!preferences.adhkarReminders) return@execute
                Content.load(context)
                val localized = LocaleX.localized(context, preferences.language)
                Notices.adhkarReminder(localized, morning)
                val reminder = Content.motivation(
                    context = if (morning) Content.CONTEXT_DAWN else Content.CONTEXT_NIGHT,
                    random = Random.Default
                )
                reminder?.let { Notices.motivation(localized, it.text(preferences.language)) }
                Reminders.schedule(localized, preferences)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val executor = Executors.newSingleThreadExecutor()
    }
}
