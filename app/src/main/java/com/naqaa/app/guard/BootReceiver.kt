package com.naqaa.app.guard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.naqaa.app.NaqaaApplication
import com.naqaa.app.notify.Reminders
import com.naqaa.app.prayer.PrayerAlarms
import com.naqaa.app.util.CrashLog
import com.naqaa.app.vpn.DnsVpnService
import com.naqaa.app.vpn.VpnState
import com.naqaa.app.widget.ProgressWidget
import java.util.concurrent.Executors

/**
 * Restores everything that must survive a reboot or a clock change: prayer and remembrance
 * alarms, the widget, and the local resolver when the user asked for it to start
 * automatically. The system always-on VPN setting remains the primary mechanism; this
 * receiver is the fallback for devices where the user did not enable it.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in HANDLED) return
        val application = context.applicationContext as NaqaaApplication
        val pending = goAsync()
        executor.execute {
            try {
                val preferences = application.graph.current()
                if (!preferences.onboarded) return@execute
                runCatching { PrayerAlarms.schedule(context, preferences) }
                    .onFailure { CrashLog.note(context, "boot alarms", it) }
                runCatching { Reminders.schedule(context, preferences) }
                    .onFailure { CrashLog.note(context, "boot reminders", it) }
                runCatching { ProgressWidget.refresh(context) }
                // Only a restart of the phone or of the application may start the service from
                // the background. A clock or time zone change arrives while the application
                // is idle, and Android 12 and newer refuse a foreground start there with an
                // exception that used to end the process.
                if (action in SERVICE_ACTIONS && preferences.vpnAutoStart && !VpnState.running) {
                    runCatching {
                        if (VpnService.prepare(context) == null) {
                            val service = Intent(context, DnsVpnService::class.java)
                            context.startForegroundService(service)
                        }
                    }.onFailure { CrashLog.note(context, "boot vpn", it) }
                }
            } catch (error: Exception) {
                CrashLog.note(context, "boot receiver", error)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val HANDLED = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
        )
        val SERVICE_ACTIONS = setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)
        val executor = Executors.newSingleThreadExecutor()
    }
}
