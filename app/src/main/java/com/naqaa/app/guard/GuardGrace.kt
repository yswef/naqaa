package com.naqaa.app.guard

import java.time.Instant

/**
 * A short window in which the guard stops interfering.
 *
 * It is granted only after the user waited out the delay screen or entered the PIN, and it
 * lives in memory on purpose: after a restart the guard is armed again, so a wait is never
 * a permanent exception, and no record of the exception is written to disk.
 */
object GuardGrace {

    @Volatile
    private var until: Instant? = null

    fun suppress(minutes: Int, now: Instant = Instant.now()) {
        until = now.plusSeconds(minutes * 60L)
    }

    fun isActive(now: Instant = Instant.now()): Boolean {
        val limit = until ?: return false
        return now.isBefore(limit)
    }

    fun clear() {
        until = null
    }
}
