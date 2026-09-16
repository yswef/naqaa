package com.naqaa.app.ui

import android.util.Base64
import com.naqaa.app.data.PinHasher
import com.naqaa.app.data.Preferences
import java.time.Duration
import java.time.Instant

/**
 * PIN verification and the wait after repeated failures.
 *
 * The hash and the salt are stored as text so the settings file stays readable JSON. Five
 * wrong entries trigger a wait that doubles every five further failures, up to fifteen
 * minutes; the counter is kept in the settings file so restarting the application does not
 * clear it. Only the correct PIN clears it.
 */
object PinGate {

    const val PIN_LENGTH = PinHasher.MIN_LENGTH
    const val ATTEMPTS_BEFORE_WAIT = 5
    private const val BASE_WAIT_SECONDS = 60L
    private const val MAX_WAIT_MINUTES = 15L

    fun verify(preferences: Preferences, pin: String): Boolean {
        val salt = decode(preferences.pinSalt) ?: return false
        val hash = decode(preferences.pinHash) ?: return false
        if (salt.isEmpty() || hash.isEmpty()) return false
        return PinHasher.verify(pin.toCharArray(), salt, hash)
    }

    fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    fun decode(value: String): ByteArray? = runCatching { Base64.decode(value, Base64.NO_WRAP) }.getOrNull()

    fun waitingSeconds(preferences: Preferences, now: Instant): Long {
        val until = preferences.pinLockedUntil ?: return 0
        return Duration.between(now, until).seconds.coerceAtLeast(0)
    }

    fun isWaiting(preferences: Preferences, now: Instant): Boolean = waitingSeconds(preferences, now) > 0

    /** Applied after a wrong entry: one more failure, and a wait from the fifth one on. */
    fun onFailure(preferences: Preferences, now: Instant): Preferences {
        val failures = preferences.pinFailures + 1
        val wait = waitFor(failures)
        return preferences.copy(
            pinFailures = failures,
            pinLockedUntil = if (wait > 0) now.plusSeconds(wait) else preferences.pinLockedUntil
        )
    }

    fun onSuccess(preferences: Preferences): Preferences =
        preferences.copy(pinFailures = 0, pinLockedUntil = null)

    private fun waitFor(failures: Int): Long {
        if (failures < ATTEMPTS_BEFORE_WAIT) return 0
        val steps = (failures - ATTEMPTS_BEFORE_WAIT) / ATTEMPTS_BEFORE_WAIT
        val minutes = minOf(BASE_WAIT_SECONDS * (1L shl steps.toInt()), MAX_WAIT_MINUTES * 60)
        return minutes
    }
}
