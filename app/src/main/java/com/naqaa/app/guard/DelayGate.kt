package com.naqaa.app.guard

import java.time.Duration
import java.time.Instant

/**
 * The waiting period in front of a change that lowers protection.
 *
 * Android does not let an application forbid its own removal or the disabling of an
 * accessibility service, so this is what remains: a ten minute wait, a sentence about what
 * is about to be lost, and a recorded decision. The arithmetic lives here and works on two
 * instants, which keeps it testable and keeps the screens free of rules.
 */
object DelayGate {

    val DURATION: Duration = Duration.ofMinutes(10)

    enum class Action { VPN_OFF, ACCESSIBILITY_OFF, ADMIN_OFF, UNINSTALL, LOCK_DISABLE, SHORTS_OFF, LOCK_OFF, OTHER }

    fun remainingSeconds(startedAt: Instant?, now: Instant): Long {
        if (startedAt == null) return DURATION.seconds
        val elapsed = Duration.between(startedAt, now).seconds
        return (DURATION.seconds - elapsed).coerceAtLeast(0)
    }

    fun isReady(startedAt: Instant?, now: Instant): Boolean =
        startedAt != null && remainingSeconds(startedAt, now) == 0L

    fun progress(startedAt: Instant?, now: Instant): Float {
        if (startedAt == null) return 0f
        val elapsed = Duration.between(startedAt, now).seconds
        return (elapsed.toFloat() / DURATION.seconds).coerceIn(0f, 1f)
    }

    fun actionOf(key: String?): Action = Action.entries.firstOrNull { it.name == key } ?: Action.OTHER
}
