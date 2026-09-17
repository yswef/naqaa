package com.naqaa.app.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * The names of the screens that other components ask to open.
 *
 * The extras are plain strings, and the target is built from the package name rather than
 * from a class reference, so notifications, the widget and the guard do not depend on the
 * interface classes to build an intent. The launcher aliases forward to this activity, so
 * an explicit component still resolves whichever entry the user chose.
 */
object Screens {

    const val EXTRA = "screen"
    const val HOME = "home"
    const val EMERGENCY = "emergency"
    const val PROTECTION = "protection"
    const val PRAYER = "prayer"

    fun mainActivity(context: Context): ComponentName =
        ComponentName(context.packageName, "${context.packageName}.ui.MainActivity")

    fun intent(context: Context, screen: String): Intent = Intent(Intent.ACTION_MAIN)
        .setComponent(mainActivity(context))
        .putExtra(EXTRA, screen)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
}
