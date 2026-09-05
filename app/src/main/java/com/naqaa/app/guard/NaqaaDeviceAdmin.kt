package com.naqaa.app.guard

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.naqaa.app.R

/**
 * Device administrator enrollment.
 *
 * Android no longer allows an application to make itself uninstallable, and this class
 * does not pretend otherwise. Enrollment raises the effort needed to remove the
 * application, and the request to disable it shows an explanation before anything happens.
 * The user remains in control and can always revoke the administrator from system settings.
 */
class NaqaaDeviceAdmin : DeviceAdminReceiver() {

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        context.getString(R.string.admin_disable_explanation)

    override fun onDisabled(context: Context, intent: Intent) {
        GuardSignals.raise(GuardSignals.Reason.GUARD_OFF)
    }
}
