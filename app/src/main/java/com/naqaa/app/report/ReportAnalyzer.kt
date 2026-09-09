package com.naqaa.app.report

import com.naqaa.app.data.EventKind
import com.naqaa.app.data.JournalEvent
import com.naqaa.app.data.Preferences
import com.naqaa.app.data.Progress
import com.naqaa.app.data.StreakSummary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** How a seven day block looked, in the terms the report talks about. */
data class WeekStats(
    val from: LocalDate,
    val to: LocalDate,
    val logged: Int,
    val lapses: Int,
    val resisted: Int,
    val prayerDays: Int,
    val adhkarDays: Int,
    val quranDays: Int,
    val planDays: Int,
    val hourHistogram: IntArray,
    val triggers: List<String>,
    val places: List<String>,
    val feelings: List<String>
) {
    fun topTrigger(): String? = triggers.firstOrNull()
    fun topPlace(): String? = places.firstOrNull()
    fun topFeeling(): String? = feelings.firstOrNull()

    /** The two hour window holding the most lapses, as the hour it starts at. */
    fun riskiestHour(): Int? {
        if (hourHistogram.sum() == 0) return null
        var bestStart = -1
        var bestCount = -1
        for (hour in 0 until HOURS) {
            val count = hourHistogram[hour] + hourHistogram[(hour + 1) % HOURS]
            if (count > bestCount) {
                bestCount = count
                bestStart = hour
            }
        }
        return bestStart
    }

    companion object {
        const val HOURS = 24
    }
}

enum class Risk { LOW, MEDIUM, HIGH }

data class Recommendation(val kind: Kind, val hour: Int?, val key: String?) {
    enum class Kind { WATCH_HOUR, AVOID_TRIGGER, CHANGE_PLACE, PRAY_MORE, READ_ADHKAR, START_PLAN, BREAK_ISOLATION, KEEP_GOING }
}

data class Report(
    val week: WeekStats,
    val previous: WeekStats,
    val streak: StreakSummary,
    val points: Int,
    val risk: Risk,
    val score: Int,
    val recommendations: List<Recommendation>,
    val badges: List<String>
)

/**
 * Rule based analysis of the last seven days.
 *
 * The score adds and subtracts small weights and is then cut into three bands:
 *   low    below 1
 *   medium 1 to 3
 *   high   4 or more
 * One lapse costs one point, a second two, three or more three; a week worse than the one
 * before adds a point and a better week removes one; a lapse with nothing resisted adds a
 * point, and a lapse in the last two days adds another. Prayer, remembrance and the plan
 * are judged per day: no day at all adds two points, three days or fewer adds one, and
 * every day of the week removes one. The bands keep a clean week with prayer and
 * remembrance in the low band even when a single lapse was logged, and a week without any
 * record therefore lands in the high band.
 *
 * The analysis reads only the journal and the settings, and produces text for the report
 * screens and the shared file. Instants are converted with the device zone, so a test can
 * run the same week at a chosen moment.
 */
object ReportAnalyzer {

    const val WEEK_DAYS = 7L

    fun analyse(events: List<JournalEvent>, preferences: Preferences, now: Instant, zone: ZoneId): Report {
        val today = LocalDate.ofInstant(now, zone)
        val weekStart = today.minusDays(WEEK_DAYS - 1)
        val previousStart = weekStart.minusDays(WEEK_DAYS)
        val current = stats(events, weekStart, today, zone)
        val previous = stats(events, previousStart, weekStart.minusDays(1), zone)
        val summary = Progress.streak(events, preferences.startedAt, now, zone)
        val lastLapse = events.filter { it.kind == EventKind.LAPSE }
            .maxByOrNull(JournalEvent::at)
            ?.let { LocalDate.ofInstant(it.at, zone) }
        val score = score(current, previous, lastLapse, today)
        return Report(
            week = current,
            previous = previous,
            streak = summary,
            points = Progress.points(events, summary),
            risk = when {
                score < 1 -> Risk.LOW
                score <= 3 -> Risk.MEDIUM
                else -> Risk.HIGH
            },
            score = score,
            recommendations = recommendations(current),
            badges = badges(events, summary, weekStart, today, zone)
        )
    }

    fun stats(events: List<JournalEvent>, from: LocalDate, to: LocalDate, zone: ZoneId): WeekStats {
        val inRange = events.filter {
            val day = LocalDate.ofInstant(it.at, zone)
            !day.isBefore(from) && !day.isAfter(to)
        }
        val lapses = inRange.filter { it.kind == EventKind.LAPSE }
        val hours = IntArray(WeekStats.HOURS)
        lapses.forEach { hours[ZonedDateTime.ofInstant(it.at, zone).hour] += 1 }
        return WeekStats(
            from = from,
            to = to,
            logged = inRange.size,
            lapses = lapses.size,
            resisted = inRange.count { it.kind == EventKind.RESISTED },
            prayerDays = distinctDays(inRange, EventKind.PRAYER, zone),
            adhkarDays = distinctDays(inRange, EventKind.ADHKAR, zone),
            quranDays = distinctDays(inRange, EventKind.QURAN, zone),
            planDays = distinctDays(inRange, EventKind.PLAN, zone),
            hourHistogram = hours,
            triggers = ranking(lapses.map { it.trigger }),
            places = ranking(lapses.map { it.place }),
            feelings = ranking(lapses.map { it.feeling })
        )
    }

    private fun score(week: WeekStats, previous: WeekStats, lastLapse: LocalDate?, today: LocalDate): Int {
        var score = when {
            week.lapses == 0 -> 0
            week.lapses == 1 -> 1
            week.lapses == 2 -> 2
            else -> 3
        }
        if (week.lapses > previous.lapses) score += 1
        if (week.lapses < previous.lapses) score -= 1
        if (week.lapses > 0 && week.resisted == 0) score += 1

        when {
            week.prayerDays == 0 -> score += 2
            week.prayerDays <= 2 -> score += 1
            week.prayerDays >= WEEK_DAYS.toInt() -> score -= 1
        }
        if (week.adhkarDays == 0) score += 1
        if (week.adhkarDays >= 6) score -= 1
        if (week.planDays == 0) score += 1
        if (week.planDays >= 5) score -= 1

        if (lastLapse != null && Progress.daysBetween(lastLapse, today) <= 2) score += 1
        return score
    }

    private fun recommendations(week: WeekStats): List<Recommendation> {
        val items = mutableListOf<Recommendation>()
        week.riskiestHour()?.let { items.add(Recommendation(Recommendation.Kind.WATCH_HOUR, it, null)) }
        week.riskiestHour()?.let { hour ->
            items.add(Recommendation(Recommendation.Kind.PRAY_MORE, hour, null))
        }
        week.topTrigger()?.let { items.add(Recommendation(Recommendation.Kind.AVOID_TRIGGER, null, it)) }
        week.topPlace()?.let { items.add(Recommendation(Recommendation.Kind.CHANGE_PLACE, null, it)) }
        if (week.adhkarDays < 3) items.add(Recommendation(Recommendation.Kind.READ_ADHKAR, null, null))
        if (week.planDays == 0) items.add(Recommendation(Recommendation.Kind.START_PLAN, null, null))
        if (week.lapses > 0 && week.resisted == 0) items.add(Recommendation(Recommendation.Kind.BREAK_ISOLATION, null, null))
        if (items.isEmpty()) items.add(Recommendation(Recommendation.Kind.KEEP_GOING, null, null))
        return items.distinctBy { it.kind }.take(3)
    }

    private fun badges(
        events: List<JournalEvent>,
        summary: StreakSummary,
        weekStart: LocalDate,
        today: LocalDate,
        zone: ZoneId
    ): List<String> {
        val resisted = Progress.resisted(events)
        val adhkarDays = Progress.daysWith(events, EventKind.ADHKAR, weekStart, today, zone)
        return Progress.Badge.entries
            .filter { Progress.earned(it, summary, adhkarDays, resisted) }
            .map { it.name }
    }

    private fun distinctDays(events: List<JournalEvent>, kind: EventKind, zone: ZoneId): Int =
        events.filter { it.kind == kind }.map { LocalDate.ofInstant(it.at, zone) }.toSet().size

    private fun ranking(values: List<String>): List<String> =
        values.filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key }
}
