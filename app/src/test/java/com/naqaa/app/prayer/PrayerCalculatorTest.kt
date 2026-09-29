package com.naqaa.app.prayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * The calculator is checked against properties that follow from the geometry rather than
 * against a printed table: solar noon in Greenwich on the equinox, sunrise and sunset six
 * hours either side of it, the two shadow conventions for Asr, and the Ramadan extension of
 * Isha. A local authority may shift times by a minute or two; the properties do not.
 */
class PrayerCalculatorTest {

    private val london = ZoneId.of("Europe/London")

    @Test
    fun `solar noon in Greenwich on the equinox falls near twelve utc`() {
        val times = PrayerCalculator.calculate(
            date = LocalDate.of(2026, 3, 20),
            latitude = 51.4779,
            longitude = 0.0,
            method = CalculationMethod.MUSLIM_WORLD_LEAGUE
        )
        val noon = ZonedDateTime.ofInstant(times.dhuhr, ZoneOffset.UTC)
        assertEquals(12, noon.hour)
        assertTrue("minutes were ${noon.minute}", noon.minute <= 20)
        assertTrue("minutes were ${noon.minute}", noon.minute >= 0)
    }

    @Test
    fun `sunrise and sunset sit around noon on the equinox`() {
        val times = PrayerCalculator.calculate(
            date = LocalDate.of(2026, 3, 20),
            latitude = 0.0,
            longitude = 0.0,
            method = CalculationMethod.MUSLIM_WORLD_LEAGUE
        )
        val sunrise = times.sunrise ?: error("sunrise missing")
        val sunset = times.maghrib ?: error("sunset missing")
        val noon = times.dhuhr.toEpochMilli()
        val morning = (noon - sunrise.toEpochMilli()) / 60_000.0
        val evening = (sunset.toEpochMilli() - noon) / 60_000.0
        assertEquals(360.0, morning, 12.0)
        assertEquals(360.0, evening, 12.0)
    }

    @Test
    fun `times are ordered through the day`() {
        val times = PrayerCalculator.calculate(
            date = LocalDate.of(2026, 6, 15),
            latitude = 21.4225,
            longitude = 39.8262,
            method = CalculationMethod.UMM_AL_QURA
        )
        val ordered = times.ordered().map { it.second }
        assertEquals(ordered.sorted(), ordered)
        assertTrue(times.ordered().all { it.first.isPrayer || it.first == PrayerName.SUNRISE })
    }

    @Test
    fun `hanafi asr comes later than the standard calculation`() {
        val base = PrayerCalculator.calculate(
            date = LocalDate.of(2026, 5, 1),
            latitude = 24.7136,
            longitude = 46.6753,
            method = CalculationMethod.UMM_AL_QURA
        )
        val hanafi = PrayerCalculator.calculate(
            date = LocalDate.of(2026, 5, 1),
            latitude = 24.7136,
            longitude = 46.6753,
            method = CalculationMethod.UMM_AL_QURA,
            hanafiAsr = true
        )
        assertNotNull(base.asr)
        assertNotNull(hanafi.asr)
        assertTrue(hanafi.asr!!.isAfter(base.asr!!))
    }

    @Test
    fun `umm al qura extends isha in ramadan`() {
        val day = LocalDate.of(2026, 3, 1)
        val ordinary = PrayerCalculator.calculate(day, 21.4225, 39.8262, CalculationMethod.UMM_AL_QURA)
        val ramadan = PrayerCalculator.calculate(day, 21.4225, 39.8262, CalculationMethod.UMM_AL_QURA, ramadan = true)
        val plainGap = ordinary.isha!!.epochSecond - ordinary.maghrib!!.epochSecond
        val ramadanGap = ramadan.isha!!.epochSecond - ramadan.maghrib!!.epochSecond
        assertEquals(90 * 60L, plainGap)
        assertEquals(120 * 60L, ramadanGap)
    }

    @Test
    fun `a polar summer has no fajr rather than an invented one`() {
        val times = PrayerCalculator.calculate(
            date = LocalDate.of(2026, 6, 21),
            latitude = 69.6492,
            longitude = 18.9553,
            method = CalculationMethod.MUSLIM_WORLD_LEAGUE
        )
        assertNull(times.fajr)
        assertNotNull(times.dhuhr)
    }

    @Test
    fun `the next prayer is the first one still ahead`() {
        val times = PrayerCalculator.calculate(
            date = LocalDate.of(2026, 4, 10),
            latitude = 30.0444,
            longitude = 31.2357,
            method = CalculationMethod.EGYPTIAN
        )
        val beforeDhuhr = times.dhuhr.minusSeconds(60)
        val next = PrayerCalculator.next(times, beforeDhuhr)
        assertEquals(PrayerName.DHUHR, next?.first)
        val afterIsha = times.isha!!.plusSeconds(60)
        assertNull(PrayerCalculator.next(times, afterIsha))
    }

    @Test
    fun `a summer day at mid latitude lasts about fifteen and a half hours`() {
        val times = PrayerCalculator.calculate(
            date = LocalDate.of(2026, 6, 21),
            latitude = 45.0,
            longitude = 0.0,
            method = CalculationMethod.MUSLIM_WORLD_LEAGUE
        )
        val sunrise = times.sunrise ?: error("sunrise missing")
        val sunset = times.maghrib ?: error("sunset missing")
        val hours = java.time.Duration.between(sunrise, sunset).toMinutes() / 60.0
        assertEquals(15.6, hours, 0.2)
        assertTrue(times.fajr!!.isBefore(sunrise))
        assertTrue(times.isha!!.isAfter(sunset))
    }

    @Test
    fun `a coordinate outside the globe is refused`() {
        val failure = runCatching {
            PrayerCalculator.calculate(LocalDate.of(2026, 1, 1), 91.0, 0.0, CalculationMethod.ISNA)
        }
        assertTrue(failure.isFailure)
    }

    @Test
    fun `calculation is stable within the same minute`() {
        val date = LocalDate.of(2026, 2, 2)
        val first = PrayerCalculator.calculate(date, 25.2048, 55.2708, CalculationMethod.DUBAI)
        val second = PrayerCalculator.calculate(date, 25.2048, 55.2708, CalculationMethod.DUBAI)
        assertEquals(first.dhuhr, second.dhuhr)
        assertEquals(first.maghrib, second.maghrib)
        assertNotNull(first.fajr)
        assertTrue(first.fajr!!.isBefore(Instant.from(ZonedDateTime.of(date, java.time.LocalTime.NOON, ZoneId.of("Asia/Dubai")))))
    }
}
