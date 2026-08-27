package com.naqaa.app.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField
import java.util.Locale

/**
 * Calendar helpers. Everything takes the zone and locale as arguments so that the same
 * functions stay usable in unit tests and in background services without a device clock.
 */
object TimeX {

    fun day(instant: Instant, zone: ZoneId): LocalDate = instant.atZone(zone).toLocalDate()

    fun startOfDay(date: LocalDate, zone: ZoneId): Instant = date.atStartOfDay(zone).toInstant()

    fun minuteOfDay(instant: Instant, zone: ZoneId): Int {
        val local = instant.atZone(zone).toLocalTime()
        return local.hour * 60 + local.minute
    }

    fun minuteOfDay(time: LocalTime): Int = time.hour * 60 + time.minute

    fun atMinute(date: LocalDate, minuteOfDay: Int, zone: ZoneId): Instant {
        val normalized = ((minuteOfDay % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
        return date.atTime(normalized / 60, normalized % 60).atZone(zone).toInstant()
    }

    fun clock(instant: Instant, zone: ZoneId, locale: Locale): String =
        DateTimeFormatter.ofPattern("HH:mm", locale).withZone(zone).format(instant)

    fun clock(minuteOfDay: Int, locale: Locale): String =
        clock(LocalTime.of(minuteOfDay / 60, minuteOfDay % 60), locale)

    fun clock(time: LocalTime, locale: Locale): String =
        DateTimeFormatter.ofPattern("HH:mm", locale).format(time)

    fun clockOf(date: LocalDate, zone: ZoneId, locale: Locale): String =
        DateTimeFormatter.ofPattern("EEEE، d MMMM", locale).format(date)

    fun timestamp(instant: Instant, zone: ZoneId, locale: Locale): String =
        DateTimeFormatter.ofPattern("d MMMM yyyy HH:mm", locale).withZone(zone).format(instant)

    fun countdown(seconds: Long): String {
        val safe = seconds.coerceAtLeast(0)
        return "%02d:%02d".format(safe / 60, safe % 60)
    }

    fun hijriMonth(date: LocalDate): Int =
        runCatching { HijrahDate.from(date).get(ChronoField.MONTH_OF_YEAR) }.getOrDefault(0)

    fun isRamadan(date: LocalDate): Boolean = hijriMonth(date) == RAMADAN_MONTH

    const val MINUTES_PER_DAY = 24 * 60
    private const val RAMADAN_MONTH = 9
}
