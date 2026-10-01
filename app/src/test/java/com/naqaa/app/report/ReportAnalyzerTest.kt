package com.naqaa.app.report

import com.naqaa.app.data.EventKind
import com.naqaa.app.data.JournalEvent
import com.naqaa.app.data.Preferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * The three bands and the recommendations that follow from them.
 *
 * The scenarios pin the thresholds written down in the analyzer: five recorded prayers a
 * day keeps a week with one lapse in the middle band, a week without prayer or remembrance
 * is high, and a week with neither records nor a lapse count is treated as high as well.
 */
class ReportAnalyzerTest {

    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 5, 20)

    private fun at(day: LocalDate, hour: Int = 21): Instant =
        day.atStartOfDay(zone).toInstant().plusSeconds(hour * 3600L)

    private fun event(
        day: LocalDate,
        kind: EventKind,
        trigger: String = "",
        place: String = "",
        feeling: String = "",
        hour: Int = 21
    ) = JournalEvent(at = at(day, hour), kind = kind, trigger = trigger, place = place, feeling = feeling)

    private fun preferences() = Preferences(startedAt = today.minusDays(60).atStartOfDay(zone).toInstant())

    private fun analyse(events: List<JournalEvent>) = ReportAnalyzer.analyse(events, preferences(), at(today, 23), zone)

    private fun worship(day: LocalDate, kind: EventKind) = event(day, kind, hour = 5)

    /** Five prayers on one day, the way somebody praying on time would log them. */
    private fun fivePrayers(day: LocalDate, out: MutableList<JournalEvent>) {
        listOf(5, 12, 15, 18, 20).forEach { hour -> out += event(day, EventKind.PRAYER, hour = hour) }
    }

    @Test
    fun `a clean week of prayer and remembrance is low risk`() {
        val events = mutableListOf<JournalEvent>()
        (0..6).forEach { offset ->
            val day = today.minusDays(offset.toLong())
            fivePrayers(day, events)
            events += worship(day, EventKind.ADHKAR)
        }
        events += worship(today.minusDays(2), EventKind.PLAN)
        val report = analyse(events)
        assertEquals(Risk.LOW, report.risk)
        assertEquals(0, report.week.lapses)
        assertEquals(7, report.week.prayerDays)
        assertEquals(7, report.week.adhkarDays)
        assertTrue(report.recommendations.any { it.kind == Recommendation.Kind.KEEP_GOING })
    }

    @Test
    fun `a week of lapses without worship is high risk`() {
        val events = listOf(
            event(today.minusDays(1), EventKind.LAPSE, trigger = "boredom", place = "room", feeling = "bored"),
            event(today.minusDays(2), EventKind.LAPSE, trigger = "boredom", place = "room", feeling = "bored"),
            event(today.minusDays(3), EventKind.LAPSE, trigger = "night", place = "bed", feeling = "lonely")
        )
        val report = analyse(events)
        assertEquals(Risk.HIGH, report.risk)
        assertTrue(report.score >= 4)
        assertEquals("boredom", report.week.topTrigger())
        assertEquals("room", report.week.topPlace())
        assertEquals("bored", report.week.topFeeling())
    }

    @Test
    fun `one lapse in a week of prayer is still medium risk`() {
        val events = mutableListOf<JournalEvent>()
        (0..6).forEach { offset ->
            val day = today.minusDays(offset.toLong())
            fivePrayers(day, events)
        }
        (3..6).forEach { offset -> events += worship(today.minusDays(offset.toLong()), EventKind.ADHKAR) }
        events += event(today.minusDays(5), EventKind.LAPSE)
        events += event(today.minusDays(5), EventKind.RESISTED)
        val report = analyse(events)
        assertEquals(Risk.MEDIUM, report.risk)
    }

    @Test
    fun `a week with no records at all is treated as high risk`() {
        val report = analyse(emptyList())
        assertEquals(Risk.HIGH, report.risk)
        assertNull(report.week.riskiestHour())
        assertNull(report.week.topTrigger())
    }

    @Test
    fun `the riskiest window is the two hours holding most lapses`() {
        val events = listOf(
            event(today.minusDays(1), EventKind.LAPSE, hour = 23),
            event(today.minusDays(2), EventKind.LAPSE, hour = 22),
            event(today.minusDays(3), EventKind.LAPSE, hour = 9)
        )
        val report = analyse(events)
        assertEquals(22, report.week.riskiestHour())
    }

    @Test
    fun `the first three recommendations follow the week`() {
        val events = listOf(
            event(today.minusDays(1), EventKind.LAPSE, trigger = "stress", place = "work", hour = 16),
            event(today.minusDays(2), EventKind.LAPSE, trigger = "stress", place = "work", hour = 17)
        )
        val report = analyse(events)
        assertEquals(3, report.recommendations.size)
        assertEquals(Recommendation.Kind.WATCH_HOUR, report.recommendations[0].kind)
        assertEquals(16, report.recommendations[0].hour)
        assertEquals(Recommendation.Kind.PRAY_MORE, report.recommendations[1].kind)
        assertEquals(Recommendation.Kind.AVOID_TRIGGER, report.recommendations[2].kind)
        assertEquals("stress", report.recommendations[2].key)
    }

    @Test
    fun `remembrance and the plan earn their badges`() {
        val events = mutableListOf<JournalEvent>()
        (0..6).forEach { offset ->
            val day = today.minusDays(offset.toLong())
            fivePrayers(day, events)
            events += worship(day, EventKind.ADHKAR)
            events += worship(day, EventKind.PLAN)
            events += event(day, EventKind.RESISTED, hour = 16)
            events += event(day, EventKind.RESISTED, hour = 22)
        }
        val report = analyse(events)
        assertEquals(Risk.LOW, report.risk)
        assertTrue(report.badges.contains("WEEK"))
        assertTrue(report.badges.contains("ADHKAR_WEEK"))
        assertTrue(report.badges.contains("RESISTED_TEN"))
        assertTrue(report.points > 0)
    }

    @Test
    fun `the week window is seven days and the comparison stays inside it`() {
        val events = listOf(
            event(today.minusDays(1), EventKind.LAPSE),
            event(today.minusDays(9), EventKind.LAPSE)
        )
        val report = analyse(events)
        assertEquals(1, report.week.lapses)
        assertEquals(1, report.previous.lapses)
        assertEquals(ReportAnalyzer.WEEK_DAYS - 1, ChronoUnit.DAYS.between(report.week.from, report.week.to))
        assertNotNull(report.streak)
    }

    @Test
    fun `prayer days are counted per day not per record`() {
        val events = listOf(
            event(today.minusDays(1), EventKind.PRAYER, hour = 5),
            event(today.minusDays(1), EventKind.PRAYER, hour = 12),
            event(today.minusDays(1), EventKind.PRAYER, hour = 18)
        )
        val report = analyse(events)
        assertEquals(1, report.week.prayerDays)
    }

    @Test
    fun `a lapse earlier in the day still lands in the same week`() {
        val events = listOf(event(today, EventKind.LAPSE, hour = 1))
        val report = analyse(events)
        assertEquals(1, report.week.lapses)
        assertEquals(0, report.previous.lapses)
    }
}
