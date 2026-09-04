package com.naqaa.app.guard

import com.naqaa.app.data.LockRule
import com.naqaa.app.util.TimeX

/**
 * Scheduled application locks. A window may cross midnight, in which case the minute range
 * is read as "from the start minute until the end minute on the next day". A rule whose
 * start and end minutes are equal means the application is covered all day.
 */
object LockWindow {

    fun covers(rule: LockRule, minuteOfDay: Int): Boolean {
        val start = normalize(rule.startMinute)
        val end = normalize(rule.endMinute)
        val minute = normalize(minuteOfDay)
        if (rule.always) return true
        return if (start < end) minute in start until end else minute >= start || minute < end
    }

    fun coversAny(rules: List<LockRule>, packageName: String, minuteOfDay: Int): Boolean =
        rules.any { it.packageName == packageName && covers(it, minuteOfDay) }

    fun ruleFor(rules: List<LockRule>, packageName: String): LockRule? =
        rules.firstOrNull { it.packageName == packageName }

    fun isAllDay(rule: LockRule): Boolean = rule.always

    /** Minutes until the lock lifts, used to explain the lock screen. */
    fun minutesUntilEnd(rule: LockRule, minuteOfDay: Int): Int {
        if (rule.always) return 0
        val minute = normalize(minuteOfDay)
        val end = normalize(rule.endMinute)
        val start = normalize(rule.startMinute)
        return when {
            start < end -> (end - minute).coerceAtLeast(0)
            minute >= start -> TimeX.MINUTES_PER_DAY - minute + end
            else -> end - minute
        }
    }

    private fun normalize(value: Int): Int = ((value % TimeX.MINUTES_PER_DAY) + TimeX.MINUTES_PER_DAY) % TimeX.MINUTES_PER_DAY
}
