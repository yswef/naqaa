package com.naqaa.app.vpn

import android.content.Context
import android.provider.Settings
import com.naqaa.app.guard.GuardSignals
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Protection status shared inside the process.
 *
 * A plain listener list is used instead of a flow: the few screens that care register while
 * they are visible, and the guard is a callback away without pulling a reactive library
 * into the service layer. The blocked name itself is never stored, only that a request was
 * filtered.
 */
object VpnState {

    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private val runningFlag = AtomicBoolean(false)
    private val blocks = AtomicLong(0L)
    private val startedAt = AtomicLong(0L)

    val running: Boolean get() = runningFlag.get()

    fun addListener(listener: () -> Unit): () -> Unit {
        listeners.add(listener)
        return { listeners.remove(listener) }
    }

    fun markStarted() {
        startedAt.set(System.currentTimeMillis())
        runningFlag.set(true)
        notifyChanged()
    }

    fun markStopped() {
        runningFlag.set(false)
        notifyChanged()
    }

    /** Blocked attempts in this session, shown on the home screen. */
    fun blockedCount(): Long = blocks.get()

    fun noteBlock() {
        blocks.incrementAndGet()
        GuardSignals.raise(GuardSignals.Reason.BLOCKED_DOMAIN)
        notifyChanged()
    }

    fun sessionStartedAt(): Long = startedAt.get()

    private fun notifyChanged() {
        listeners.forEach { listener -> runCatching { listener() } }
    }

    /** Private DNS host names bypass local filtering, so the user is warned about them. */
    fun privateDnsMode(context: Context): String? = runCatching {
        Settings.Global.getString(context.contentResolver, "private_dns_mode")
    }.getOrNull()

    fun privateDnsHost(context: Context): String? = runCatching {
        Settings.Global.getString(context.contentResolver, "private_dns_specifier")
    }.getOrNull()
}
