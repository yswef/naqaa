package com.naqaa.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Streak arithmetic: what counts as a clean day, how a lapse interrupts a run, and that the
 * best run is remembered after a later lapse.
 */
class ProgressTest {

    private val zone = ZoneId.of("UTC")
    private val start = LocalDate.of(2026, 1, 1)

    private fun event(day: LocalDate, kind: EventKind = EventKind.LAPSE): JournalEvent =
        JournalEvent(at = day.atStartOfDay(zone).toInstant().plusSeconds(12 * 3600), kind = kind)

    @Test
    fun `a day without a lapse counts as clean`() {
        val events = listOf(event(start.plusDays(1), EventKind.PRAYER))
        val summary = Progress.streak(events, start.atStartOfDay(zone).toInstant(), start.plusDays(2).atStartOfDay(zone).toInstant().plusSeconds(6 * 3600), zone)
        assertEquals(3, summary.current)
        assertEquals(3, summary.cleanDays)
        assertEquals(0, summary.lapseDays)
    }

    @Test
    fun `a lapse today resets the current run`() {
        val today = start.plusDays(4)
        val events = listOf(event(today))
        val summary = Progress.streak(events, start.atStartOfDay(zone).toInstant(), today.atStartOfDay(zone).toInstant().plusSeconds(20 * 3600), zone)
        assertEquals(0, summary.current)
        assertEquals(4, summary.best)
        assertEquals(1, summary.lapseDays)
    }

    @Test
    fun `the best run survives a later lapse`() {
        val events = listOf(event(start.plusDays(5)))
        val now = start.plusDays(8).atStartOfDay(zone).toInstant().plusSeconds(3600)
        val summary = Progress.streak(events, start.atStartOfDay(zone).toInstant(), now, zone)
        assertEquals(3, summary.current)
        assertEquals(5, summary.best)
    }

    @Test
    fun `other records never break a run`() {
        val events = listOf(
            event(start.plusDays(1), EventKind.RESISTED),
            event(start.plusDays(2), EventKind.ADHKAR),
            event(start.plusDays(2), EventKind.PLAN)
        )
        val now = start.plusDays(3).atStartOfDay(zone).toInstant().plusSeconds(3600)
        val summary = Progress.streak(events, start.atStartOfDay(zone).toInstant(), now, zone)
        assertEquals(4, summary.current)
        assertEquals(0, summary.lapseDays)
    }

    @Test
    fun `install day is the first day that can be clean`() {
        val now = start.atStartOfDay(zone).toInstant().plusSeconds(3600)
        val summary = Progress.streak(emptyList(), start.atStartOfDay(zone).toInstant(), now, zone)
        assertEquals(1, summary.current)
        assertEquals(1, summary.cleanDays)
        assertEquals(start, summary.since)
    }

    @Test
    fun `the ninetieth day view marks lapse days`() {
        val events = listOf(event(start.plusDays(10)))
        val now = start.plusDays(20).atStartOfDay(zone).toInstant()
        val clean = Progress.cleanDaysBetween(events, start, start.plusDays(20), zone)
        assertTrue(start.plusDays(10) !in clean)
        assertEquals(20, clean.size)
    }

    @Test
    fun `resisted urges are counted on their own`() {
        val events = listOf(
            event(start, EventKind.RESISTED),
            event(start.plusDays(1), EventKind.RESISTED),
            event(start.plusDays(1), EventKind.LAPSE)
        )
        assertEquals(2, Progress.resisted(events))
        assertEquals(1, Progress.count(events, EventKind.LAPSE))
    }

    @Test
    fun `points reward clean days and resisted urges`() {
        val events = listOf(event(start.plusDays(2), EventKind.RESISTED), event(start.plusDays(3), EventKind.PRAYER))
        val now = start.plusDays(3).atStartOfDay(zone).toInstant().plusSeconds(3600)
        val summary = Progress.streak(events, start.atStartOfDay(zone).toInstant(), now, zone)
        // four clean days, one resisted urge, one recorded prayer
        assertEquals(4 * 10 + 5 + 3, Progress.points(events, summary))
    }

    @Test
    fun `badges follow the best run and the counters`() {
        val now = start.plusDays(9).atStartOfDay(zone).toInstant().plusSeconds(3600)
        val summary = Progress.streak(emptyList(), start.atStartOfDay(zone).toInstant(), now, zone)
        assertTrue(Progress.earned(Progress.Badge.WEEK, summary, adhkarDays = 0, resistedCount = 0))
        assertTrue(!Progress.earned(Progress.Badge.FORTNIGHT, summary, adhkarDays = 0, resistedCount = 0))
        assertTrue(Progress.earned(Progress.Badge.RESISTED_TEN, summary, adhkarDays = 0, resistedCount = 10))
        assertTrue(Progress.earned(Progress.Badge.ADHKAR_WEEK, summary, adhkarDays = 7, resistedCount = 0))
    }

    @Test
    fun `days with a kind are counted once per day`() {
        val events = listOf(
            event(start, EventKind.PRAYER),
            event(start, EventKind.PRAYER)
        )
        val days = Progress.daysWith(events, EventKind.PRAYER, start, start.plusDays(1), zone)
        assertEquals(1, days)
        assertEquals(2, Progress.count(events, EventKind.PRAYER))
    }
}
