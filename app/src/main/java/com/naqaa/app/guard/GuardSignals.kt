package com.naqaa.app.guard

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Raised whenever the guard interferes with something the user tried to open: a filtered
 * name, a closed short video, a locked application or a settings page put behind the
 * waiting screen.
 *
 * The signal carries a reason and a moment, never the address or the page. Screens
 * register while they are visible and use it to show an encouragement card, and the journal
 * can record the event if the user asks for it.
 */
object GuardSignals {

    enum class Reason { BLOCKED_DOMAIN, SHORTS, REELS, BROWSER_KEYWORD, LOCKED_APP, RISKY_SETTINGS, GUARD_OFF }

    private val listeners = CopyOnWriteArrayList<(Reason) -> Unit>()

    @Volatile
    var lastReason: Reason? = null
        private set

    @Volatile
    var lastRaisedAt: Long = 0L
        private set

    fun addListener(listener: (Reason) -> Unit): () -> Unit {
        listeners.add(listener)
        return { listeners.remove(listener) }
    }

    fun raise(reason: Reason) {
        lastReason = reason
        lastRaisedAt = System.currentTimeMillis()
        listeners.forEach { listener -> runCatching { listener(reason) } }
    }
}
