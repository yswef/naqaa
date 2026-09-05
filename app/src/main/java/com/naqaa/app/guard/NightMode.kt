package com.naqaa.app.guard

import com.naqaa.app.prayer.DayPrayerTimes
import com.naqaa.app.util.TimeX
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * From midnight until Fajr the guard is stricter: browsers and social applications are
 * covered unless the user put them on the allowlist, and short video surfaces are always
 * refused. The window is derived from the local prayer calculation, with a fixed fallback
 * when twilight is undefined at very high latitudes.
 */
object NightMode {

    data class Window(val start: Instant, val end: Instant) {
        fun contains(now: Instant): Boolean = !now.isBefore(start) && now.isBefore(end)
    }

    fun window(times: DayPrayerTimes, zone: ZoneId): Window {
        val start = TimeX.startOfDay(times.date, zone)
        val fallback = start.plusSeconds(FALLBACK_FAJR_MINUTE * 60L)
        val end = times.fajr?.takeIf { it.isAfter(start) } ?: fallback
        return Window(start, end)
    }

    /** Night mode applies from the start of the local day until its dawn prayer. */
    fun activeAt(now: Instant, times: (LocalDate) -> DayPrayerTimes, zone: ZoneId): Boolean =
        window(times(TimeX.day(now, zone)), zone).contains(now)

    fun applies(packageName: String, allowlist: Set<String>): Boolean = packageName !in allowlist

    private const val FALLBACK_FAJR_MINUTE = 5 * 60
}
