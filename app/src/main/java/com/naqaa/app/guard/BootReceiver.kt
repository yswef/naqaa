package com.naqaa.app.guard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import com.naqaa.app.NaqaaApplication
import com.naqaa.app.notify.Reminders
import com.naqaa.app.prayer.PrayerAlarms
import com.naqaa.app.vpn.DnsVpnService
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
                PrayerAlarms.schedule(context, preferences)
                Reminders.schedule(context, preferences)
                ProgressWidget.refresh(context)
                if (preferences.vpnAutoStart && VpnService.prepare(context) == null) {
                    val service = Intent(context, DnsVpnService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(service) else context.startService(service)
                }
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
        val executor = Executors.newSingleThreadExecutor()
    }
}
