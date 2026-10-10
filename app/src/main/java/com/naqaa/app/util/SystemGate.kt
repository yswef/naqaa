package com.naqaa.app.util

import android.app.AlarmManager
import android.app.admin.DevicePolicyManager
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.naqaa.app.accessibility.GuardAccessibilityService
import com.naqaa.app.guard.NaqaaDeviceAdmin
import com.naqaa.app.vpn.VpnState

/**
 * Reads the state of the system switches this application asks for and builds the intents
 * that lead the user to them. Nothing here is requested silently: every switch belongs to
 * the user and can be turned off again from the same screens.
 */
object SystemGate {

    fun accessibilityEnabled(context: Context): Boolean {
        val component = ComponentName(context, GuardAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == component }
    }

    fun isDeviceAdmin(context: Context): Boolean =
        devicePolicy(context).isAdminActive(ComponentName(context, NaqaaDeviceAdmin::class.java))

    fun notificationsEnabled(context: Context): Boolean =
        context.getSystemService(android.app.NotificationManager::class.java)?.areNotificationsEnabled() ?: false

    fun canScheduleExactAlarms(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms(context).canScheduleExactAlarms()

    fun ignoringBatteryOptimizations(context: Context): Boolean =
        power(context).isIgnoringBatteryOptimizations(context.packageName)

    fun vpnConsentMissing(context: Context): Intent? = VpnService.prepare(context)

    fun privateDnsMode(context: Context): String? = VpnState.privateDnsMode(context)

    fun privateDnsIsStrict(context: Context): Boolean {
        val mode = privateDnsMode(context) ?: return false
        if (mode == "off") return false
        return mode == "hostname" || mode == "opportunistic"
    }

    fun vpnSettings(): Intent = Intent(Settings.ACTION_VPN_SETTINGS)

    fun accessibilitySettings(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    /** App info is where Android 13+ lets a user allow restricted settings for a sideloaded app. */
    fun appDetailsSettings(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

    /** The security page is where a device administrator is removed. */
    fun deviceAdminSettings(): Intent = Intent(Settings.ACTION_SECURITY_SETTINGS)

    fun privateDnsSettings(): Intent = Intent(PRIVATE_DNS_ACTION)

    fun exactAlarmSettings(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            .setData(Uri.parse("package:${context.packageName}"))

    fun batterySettings(context: Context): Intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:${context.packageName}"))
    } else {
        appDetailsSettings(context)
    }

    /** Opens the requested system page and falls back to this app's info page on OEM variants. */
    fun openSettings(context: Context, intent: Intent): Boolean {
        fun launch(target: Intent): Boolean {
            val flags = if (context is Activity) 0 else Intent.FLAG_ACTIVITY_NEW_TASK
            return runCatching { context.startActivity(Intent(target).addFlags(flags)); true }.getOrDefault(false)
        }
        return launch(intent) || launch(appDetailsSettings(context))
    }

    fun notificationSettings(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    fun deviceAdminRequest(context: Context): Intent =
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, ComponentName(context, NaqaaDeviceAdmin::class.java))
            .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, context.getString(com.naqaa.app.R.string.admin_explanation))

    fun dial(number: String): Intent = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))

    private fun devicePolicy(context: Context): DevicePolicyManager =
        context.getSystemService(DevicePolicyManager::class.java)

    private fun alarms(context: Context): AlarmManager = context.getSystemService(AlarmManager::class.java)

    private fun power(context: Context): PowerManager = context.getSystemService(PowerManager::class.java)

    private const val PRIVATE_DNS_ACTION = "android.settings.PRIVATE_DNS_SETTINGS"
}
