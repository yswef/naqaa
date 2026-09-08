package com.naqaa.app.prayer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.naqaa.app.R
import com.naqaa.app.data.Preferences
import com.naqaa.app.notify.Notices
import com.naqaa.app.util.LocaleX
import com.naqaa.app.util.TimeX
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.Executors

/**
 * Reminder alarms for the five prayers.
 *
 * Exact alarms are used when the platform allows them and a windowed alarm otherwise, so
 * a missing special permission degrades delivery precision instead of silencing the
 * reminders. Every alarm is re-scheduled when it fires, at boot, and after a clock or
 * time zone change, which keeps the schedule aligned with the device calendar.
 */
object PrayerAlarms {

    const val ACTION_REMINDER = "com.naqaa.app.action.PRAYER_REMINDER"
    const val EXTRA_PRAYER = "prayer"

    fun schedule(context: Context, preferences: Preferences) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        cancel(context, manager)
        for (offset in 0..DAYS_AHEAD) {
            val date = LocalDate.now().plusDays(offset.toLong())
            val times = PrayerCalculator.calculate(
                date = date,
                latitude = preferences.latitude,
                longitude = preferences.longitude,
                method = preferences.prayerMethod,
                hanafiAsr = preferences.hanafiAsr,
                ramadan = TimeX.isRamadan(date)
            )
            times.ordered().forEach { (name, instant) ->
                if (!name.isPrayer || !instant.isAfter(now)) return@forEach
                val pending = pending(context, offset, name)
                if (exact) {
                    manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, instant.toEpochMilli(), pending)
                } else {
                    manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, instant.toEpochMilli(), pending)
                }
            }
        }
    }

    fun cancel(context: Context, manager: AlarmManager = context.getSystemService(AlarmManager::class.java)) {
        for (offset in 0..DAYS_AHEAD) {
            PrayerName.entries.forEach { name ->
                val existing = PendingIntent.getBroadcast(
                    context, requestCode(offset, name),
                    intent(context, name),
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )
                if (existing != null) {
                    manager.cancel(existing)
                    existing.cancel()
                }
            }
        }
    }

    private fun pending(context: Context, dayOffset: Int, name: PrayerName): PendingIntent =
        PendingIntent.getBroadcast(
            context, requestCode(dayOffset, name), intent(context, name),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun intent(context: Context, name: PrayerName): Intent =
        Intent(context, PrayerAlarmReceiver::class.java)
            .setAction(ACTION_REMINDER)
            .putExtra(EXTRA_PRAYER, name.name)

    private fun requestCode(dayOffset: Int, name: PrayerName): Int = dayOffset * 10 + name.ordinal

    private const val DAYS_AHEAD = 1
}

class PrayerAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PrayerAlarms.ACTION_REMINDER) return
        val pending = goAsync()
        val name = PrayerName.entries.firstOrNull { it.name == intent.getStringExtra(PrayerAlarms.EXTRA_PRAYER) }
            ?: PrayerName.DHUHR
        executor.execute {
            try {
                val application = context.applicationContext as com.naqaa.app.NaqaaApplication
                val preferences = application.graph.current()
                if (preferences.prayerReminders) {
                    val localized = LocaleX.localized(context, preferences.language)
                    Notices.prayerReminder(localized, localized.getString(label(name)))
                    PrayerAlarms.schedule(localized, preferences)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun label(name: PrayerName): Int = when (name) {
        PrayerName.FAJR -> R.string.prayer_fajr
        PrayerName.SUNRISE -> R.string.prayer_sunrise
        PrayerName.DHUHR -> R.string.prayer_dhuhr
        PrayerName.ASR -> R.string.prayer_asr
        PrayerName.MAGHRIB -> R.string.prayer_maghrib
        PrayerName.ISHA -> R.string.prayer_isha
    }

    private companion object {
        val executor = Executors.newSingleThreadExecutor()
    }
}
