package com.naqaa.app.prayer

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.tan

/**
 * Solar based prayer times, computed on the device from the standard astronomical
 * approximations (apparent solar longitude, obliquity, declination and the equation of
 * time). Nothing is fetched, and the calculation is deliberately free of corrections that
 * only a local authority can decide, such as one or two minutes added by a mosque.
 *
 * A missing twilight returns null instead of an invented value, which is what happens
 * inside the polar circles where the sun never reaches the Fajr angle.
 */
data class DayPrayerTimes(
    val date: LocalDate,
    val fajr: Instant?,
    val sunrise: Instant?,
    val dhuhr: Instant,
    val asr: Instant?,
    val maghrib: Instant?,
    val isha: Instant?
) {
    /**
     * The day's times in the order they happen. The instants carry the day they belong to,
     * so Fajr may sit before midnight and Isha after it; sorting the instants is what keeps
     * the sequence truthful in those cases instead of forcing the calendar day onto them.
     */
    fun ordered(): List<Pair<PrayerName, Instant>> = listOfNotNull(
        fajr?.let { PrayerName.FAJR to it },
        sunrise?.let { PrayerName.SUNRISE to it },
        PrayerName.DHUHR to dhuhr,
        asr?.let { PrayerName.ASR to it },
        maghrib?.let { PrayerName.MAGHRIB to it },
        isha?.let { PrayerName.ISHA to it }
    ).sortedBy { it.second }
}

enum class PrayerName { FAJR, SUNRISE, DHUHR, ASR, MAGHRIB, ISHA;
    val isPrayer: Boolean get() = this != SUNRISE
}

object PrayerCalculator {

    fun calculate(
        date: LocalDate,
        latitude: Double,
        longitude: Double,
        method: CalculationMethod,
        hanafiAsr: Boolean = false,
        ramadan: Boolean = false
    ): DayPrayerTimes {
        require(latitude.isFinite() && latitude in -90.0..90.0) { "latitude outside the globe" }
        require(longitude.isFinite() && longitude in -180.0..180.0) { "longitude outside the globe" }

        val dayOfYear = date.dayOfYear
        val declination = solarDeclination(dayOfYear)
        val equationOfTime = equationOfTime(dayOfYear)
        val noonMinutes = 720.0 - 4.0 * longitude - equationOfTime

        fun time(minutesFromMidnight: Double): Instant =
            Instant.ofEpochSecond(date.toEpochDay() * SECONDS_PER_DAY + (minutesFromMidnight * 60).roundToLong())

        /** A time before noon, the form Fajr and sunrise take. */
        fun beforeNoon(angle: Double): Instant? =
            hourAngle(angle, latitude, declination)?.let { time(noonMinutes - minutes(it)) }

        /** A time after noon, the form Asr, Maghrib and Isha take. */
        fun afterNoon(angle: Double): Instant? =
            hourAngle(angle, latitude, declination)?.let { time(noonMinutes + minutes(it)) }

        val sunrise = beforeNoon(SUNRISE_ALTITUDE)
        val maghrib = afterNoon(SUNRISE_ALTITUDE)
        val isha = method.ishaMinutes(ramadan)?.let { interval ->
            maghrib?.plusSeconds(interval * 60L)
        } ?: method.ishaAngle?.let { afterNoon(-it) }

        return DayPrayerTimes(
            date = date,
            fajr = beforeNoon(-method.fajrAngle),
            sunrise = sunrise,
            dhuhr = time(noonMinutes),
            asr = afterNoon(asrAltitude(latitude, declination, hanafiAsr)),
            maghrib = maghrib,
            isha = isha
        )
    }

    /** Converts a solar hour angle into minutes; 360 degrees equals one solar day. */
    private fun minutes(angleDegrees: Double): Double = angleDegrees * 4.0

    private fun hourAngle(altitude: Double, latitude: Double, declination: Double): Double? {
        val denominator = cos(Math.toRadians(latitude)) * cos(Math.toRadians(declination))
        if (abs(denominator) < 1e-9) return null
        val ratio = (sin(Math.toRadians(altitude)) - sin(Math.toRadians(latitude)) * sin(Math.toRadians(declination))) / denominator
        if (ratio !in -1.0..1.0) return null
        return Math.toDegrees(acos(ratio))
    }

    /** Shadow length used for Asr: one shadow unit normally, two for the Hanafi school. */
    private fun asrAltitude(latitude: Double, declination: Double, hanafi: Boolean): Double =
        Math.toDegrees(atan(1.0 / ((if (hanafi) 2.0 else 1.0) + tan(Math.toRadians(abs(latitude - declination))))))

    /**
     * Solar declination in degrees, from the NOAA series in gamma, the fractional year.
     * Declination swings between the two tropics and drives every twilight angle here.
     */
    private fun solarDeclination(dayOfYear: Int): Double {
        val gamma = 2.0 * Math.PI / 365.0 * (dayOfYear - 1)
        val radians = 0.006918 -
            0.399912 * cos(gamma) + 0.070257 * sin(gamma) -
            0.006758 * cos(2 * gamma) + 0.000907 * sin(2 * gamma) -
            0.002697 * cos(3 * gamma) + 0.00148 * sin(3 * gamma)
        return Math.toDegrees(radians)
    }

    /** NOAA approximation of the equation of time, in minutes. */
    private fun equationOfTime(dayOfYear: Int): Double {
        val gamma = 2.0 * Math.PI / 365.0 * (dayOfYear - 1 + 0.5)
        return 229.18 * (
            0.000075 +
                0.001868 * cos(gamma) - 0.032077 * sin(gamma) -
                0.014615 * cos(2 * gamma) - 0.040849 * sin(2 * gamma)
            )
    }

    /** The first prayer of a day that has not passed yet, with the day it belongs to. */
    fun next(times: DayPrayerTimes, now: Instant): Pair<PrayerName, Instant>? =
        times.ordered().firstOrNull { it.first.isPrayer && it.second.isAfter(now) }

    fun today(date: LocalDate, zone: ZoneId): LocalDate = date

    private const val SECONDS_PER_DAY = 86_400L
    private const val SUNRISE_ALTITUDE = -0.833
}
