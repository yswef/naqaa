package com.naqaa.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Current and best run of days without a lapse, plus how many clean days happened. */
data class StreakSummary(
    val current: Int,
    val best: Int,
    val cleanDays: Int,
    val lapseDays: Int,
    val since: LocalDate
)

/**
 * Streak arithmetic over the journal.
 *
 * A day counts as clean when it holds no lapse record, whatever else was logged in it:
 * prayer, remembrance, reading and resisted urges never break a run. The day the user
 * started is the first day that can be clean, so a fresh install can reach one day
 * immediately rather than waiting until tomorrow.
 */
object Progress {

    fun streak(events: List<JournalEvent>, startedAt: Instant, now: Instant, zone: ZoneId): StreakSummary {
        val today = LocalDate.ofInstant(now, zone)
        val since = minOf(LocalDate.ofInstant(startedAt, zone), today)
        val lapseDays = events.filter { it.kind == EventKind.LAPSE }
            .map { LocalDate.ofInstant(it.at, zone) }
            .toSet()
        val clean = countClean(lapseDays, since, today)
        return StreakSummary(
            current = currentRun(lapseDays, since, today),
            best = bestRun(lapseDays, since, today),
            cleanDays = clean,
            lapseDays = lapseDays.count { !it.isBefore(since) && !it.isAfter(today) },
            since = since
        )
    }

    fun cleanDaysBetween(events: List<JournalEvent>, from: LocalDate, to: LocalDate, zone: ZoneId): Set<LocalDate> {
        val lapseDays = events.filter { it.kind == EventKind.LAPSE }
            .map { LocalDate.ofInstant(it.at, zone) }
            .toSet()
        val clean = LinkedHashSet<LocalDate>()
        var day = from
        while (!day.isAfter(to)) {
            if (day !in lapseDays) clean.add(day)
            day = day.plusDays(1)
        }
        return clean
    }

    private fun countClean(lapseDays: Set<LocalDate>, from: LocalDate, to: LocalDate): Int {
        var day = from
        var count = 0
        while (!day.isAfter(to)) {
            if (day !in lapseDays) count += 1
            day = day.plusDays(1)
        }
        return count
    }

    fun resisted(events: List<JournalEvent>): Int = events.count { it.kind == EventKind.RESISTED }

    fun count(events: List<JournalEvent>, kind: EventKind): Int = events.count { it.kind == kind }

    fun daysWith(events: List<JournalEvent>, kind: EventKind, from: LocalDate, to: LocalDate, zone: ZoneId): Int =
        events.filter { it.kind == kind }
            .map { LocalDate.ofInstant(it.at, zone) }
            .filter { !it.isBefore(from) && !it.isAfter(to) }
            .toSet()
            .size

    private fun currentRun(lapseDays: Set<LocalDate>, since: LocalDate, today: LocalDate): Int {
        var day = today
        var run = 0
        while (!day.isBefore(since) && day !in lapseDays) {
            run += 1
            day = day.minusDays(1)
        }
        return run
    }

    private fun bestRun(lapseDays: Set<LocalDate>, since: LocalDate, today: LocalDate): Int {
        var day = since
        var run = 0
        var best = 0
        while (!day.isAfter(today)) {
            if (day in lapseDays) {
                run = 0
            } else {
                run += 1
                if (run > best) best = run
            }
            day = day.plusDays(1)
        }
        return best
    }

    /** Badge thresholds, kept here so the interface and the report agree on one table. */
    enum class Badge(val days: Int?) {
        THREE_DAYS(3), WEEK(7), FORTNIGHT(14), MONTH(30), RESISTED_TEN(null), ADHKAR_WEEK(null)
    }

    fun earned(badge: Badge, summary: StreakSummary, adhkarDays: Int, resistedCount: Int): Boolean = when (badge) {
        Badge.THREE_DAYS -> summary.best >= 3
        Badge.WEEK -> summary.best >= 7
        Badge.FORTNIGHT -> summary.best >= 14
        Badge.MONTH -> summary.best >= 30
        Badge.RESISTED_TEN -> resistedCount >= 10
        Badge.ADHKAR_WEEK -> adhkarDays >= 7
    }

    /** Points are for private encouragement only; nothing is shared or uploaded. */
    fun points(events: List<JournalEvent>, summary: StreakSummary): Int =
        summary.cleanDays * 10 +
            summary.lapseDays * 2 +
            resisted(events) * 5 +
            count(events, EventKind.PRAYER) * 3 +
            count(events, EventKind.ADHKAR) * 3 +
            count(events, EventKind.QURAN) * 4 +
            count(events, EventKind.PLAN) * 2

    fun daysBetween(from: LocalDate, to: LocalDate): Long = ChronoUnit.DAYS.between(from, to)
}
