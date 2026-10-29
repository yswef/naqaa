package com.naqaa.app.guard

import java.util.concurrent.ConcurrentHashMap

/**
 * Time the user gives himself on purpose: a pass for one locked application, or for the
 * short video surfaces of every application the rules cover.
 *
 * Like [GuardGrace] it lives in memory only. After a restart the guard is armed again, so a
 * pass is never a permanent exception. When a pass runs out the next event that would have
 * been blocked is reported once as "the time is over" ([consumeAppExpired],
 * [consumeShortsExpired]), which is what lets the screen say so instead of silently closing
 * the application.
 */
object UsagePass {

    private val appUntil = ConcurrentHashMap<String, Long>()
    private val appExpiredAt = ConcurrentHashMap<String, Long>()

    @Volatile
    private var shortsUntil = 0L

    @Volatile
    private var shortsExpiredAt = 0L

    fun allowApp(packageName: String, minutes: Int, now: Long = System.currentTimeMillis()) {
        appUntil[packageName] = now + minutes * 60_000L
        appExpiredAt.remove(packageName)
    }

    fun appAllowed(packageName: String, now: Long = System.currentTimeMillis()): Boolean {
        val until = appUntil[packageName] ?: return false
        if (now < until) return true
        appUntil.remove(packageName)
        appExpiredAt[packageName] = now
        return false
    }

    /** True once after an application pass ended; the caller shows the "time is over" screen. */
    fun consumeAppExpired(packageName: String, now: Long = System.currentTimeMillis()): Boolean {
        val at = appExpiredAt.remove(packageName) ?: return false
        return now - at < FRESH_MILLIS
    }

    fun allowShorts(minutes: Int, now: Long = System.currentTimeMillis()) {
        shortsUntil = now + minutes * 60_000L
        shortsExpiredAt = 0L
    }

    fun shortsAllowed(now: Long = System.currentTimeMillis()): Boolean {
        val until = shortsUntil
        if (until == 0L) return false
        if (now < until) return true
        shortsUntil = 0L
        shortsExpiredAt = now
        return false
    }

    fun shortsRemainingSeconds(now: Long = System.currentTimeMillis()): Long =
        ((shortsUntil - now) / 1000).coerceAtLeast(0)

    /** Ends the short video pass at once, without the "time is over" screen. */
    fun cancelShorts() {
        shortsUntil = 0L
        shortsExpiredAt = 0L
    }

    fun consumeShortsExpired(now: Long = System.currentTimeMillis()): Boolean {
        val at = shortsExpiredAt
        shortsExpiredAt = 0L
        return at != 0L && now - at < FRESH_MILLIS
    }

    /** An expiry older than this is stale: the user was not using the application when it ended. */
    private const val FRESH_MILLIS = 10 * 60_000L
}
