package com.naqaa.app.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.naqaa.app.guard.DelayGate

/**
 * The two screens that cover what is underneath them: the waiting screen in front of a
 * protection change, and the lock that covers an application outside its allowed window.
 * Both are addressed through this file so the guard never needs the interface classes.
 */
object Overlays {

    const val EXTRA_ACTION = "action"
    const val EXTRA_MESSAGE = "message"
    const val EXTRA_PACKAGE = "package"
    const val EXTRA_ENDED = "ended"

    fun delay(context: Context, action: DelayGate.Action): Intent =
        Intent(Intent.ACTION_VIEW)
            .setComponent(ComponentName(context.packageName, "${context.packageName}.ui.DelayActivity"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(EXTRA_ACTION, action.name)

    /** [ended] marks the screen that says the time the user gave himself is over. */
    fun lock(context: Context, messageId: Int, packageName: String?, ended: Boolean = false): Intent =
        Intent(Intent.ACTION_VIEW)
            .setComponent(ComponentName(context.packageName, "${context.packageName}.ui.AppLockActivity"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(EXTRA_MESSAGE, messageId)
            .putExtra(EXTRA_PACKAGE, packageName)
            .putExtra(EXTRA_ENDED, ended)

    /** Asks for VPN consent again after another VPN replaced the local filter. */
    fun vpnResume(context: Context): Intent =
        Intent(Intent.ACTION_VIEW)
            .setComponent(ComponentName(context.packageName, "${context.packageName}.ui.VpnResumeActivity"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
