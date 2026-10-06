package com.naqaa.app.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.naqaa.app.data.Persona

/**
 * Switches which launcher entry is visible: the application itself, or one of the three
 * neutral aliases. Both the visible entry and its icon come from the manifest alias, so the
 * home screen never shows a name that describes what the application does.
 */
object PersonaSwitch {

    fun apply(context: Context, persona: Persona) {
        val manager = context.packageManager
        Persona.entries.forEach { entry ->
            val component = ComponentName(context, "${context.packageName}.ui.${entry.component}")
            val newState = if (entry == persona) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            // Writing the same state again on every start wakes the package manager for
            // nothing, and some vendor builds make that write expensive, so only a change
            // is written.
            runCatching {
                if (manager.getComponentEnabledSetting(component) != newState) {
                    manager.setComponentEnabledSetting(component, newState, PackageManager.DONT_KILL_APP)
                }
            }
        }
    }
}
