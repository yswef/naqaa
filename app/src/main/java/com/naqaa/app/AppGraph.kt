package com.naqaa.app

import android.content.Context
import com.naqaa.app.data.EventKind
import com.naqaa.app.data.JournalEvent
import com.naqaa.app.data.JournalStore
import com.naqaa.app.data.Preferences
import com.naqaa.app.data.PreferencesStore
import com.naqaa.app.data.Vault
import com.naqaa.app.widget.ProgressWidget

/**
 * Manual wiring for the few long-lived collaborators of the application. The graph is
 * small enough that a container framework would only add weight and indirection.
 */
class AppGraph(context: Context) {

    private val appContext = context.applicationContext
    private val vault = Vault()

    val journal = JournalStore(appContext, vault)
    val preferences = PreferencesStore(appContext, vault)

    fun snapshot(): JournalStore.Snapshot = journal.snapshot()

    fun record(
        kind: EventKind,
        trigger: String = "",
        place: String = "",
        feeling: String = "",
        note: String = ""
    ): JournalEvent {
        val event = JournalEvent(kind = kind, trigger = trigger, place = place, feeling = feeling, note = note)
        journal.append(event)
        ProgressWidget.refresh(appContext)
        return event
    }

    fun update(transform: (Preferences) -> Preferences): Preferences {
        val updated = preferences.update(transform)
        ProgressWidget.refresh(appContext)
        return updated
    }

    fun current(): Preferences = preferences.load()

    fun wipe() {
        preferences.wipe()
        journal.erase()
        ProgressWidget.refresh(appContext)
    }
}
