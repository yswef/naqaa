package com.naqaa.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject
import java.time.Instant

/**
 * Encrypted journal.
 *
 * The table keeps only an opaque row identifier and the event timestamp in the clear so
 * that range queries stay cheap; every meaningful field, including the event kind, is
 * sealed inside the payload. A row that cannot be decrypted is counted instead of
 * aborting the read, so a damaged or replaced Keystore key cannot hide the rest of the
 * history from the user.
 */
class JournalStore(context: Context, private val vault: Vault = Vault()) :
    SQLiteOpenHelper(context, DATABASE, null, SCHEMA) {

    data class Snapshot(val events: List<JournalEvent>, val unreadable: Int)

    @Synchronized
    fun append(event: JournalEvent) {
        writableDatabase.insertOrThrow(TABLE, null, values(event))
    }

    @Synchronized
    fun update(event: JournalEvent) {
        val changed = writableDatabase.update(TABLE, values(event), "id = ?", arrayOf(event.id))
        check(changed == 1) { "event ${event.id} is not stored" }
    }

    @Synchronized
    fun delete(id: String) {
        writableDatabase.delete(TABLE, "id = ?", arrayOf(id))
    }

    @Synchronized
    fun snapshot(from: Instant? = null, to: Instant? = null): Snapshot {
        val selection = StringBuilder()
        val arguments = mutableListOf<String>()
        if (from != null) {
            selection.append("at >= ?")
            arguments += from.toEpochMilli().toString()
        }
        if (to != null) {
            if (selection.isNotEmpty()) selection.append(" AND ")
            selection.append("at < ?")
            arguments += to.toEpochMilli().toString()
        }
        val events = mutableListOf<JournalEvent>()
        var unreadable = 0
        readableDatabase.query(
            TABLE, arrayOf("id", "payload"), selection.toString().ifEmpty { null },
            arguments.takeIf { it.isNotEmpty() }?.toTypedArray(), null, null, "at ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                val parsed = runCatching { decode(id, cursor.getBlob(1)) }.getOrNull()
                if (parsed == null) unreadable++ else events += parsed
            }
        }
        return Snapshot(events, unreadable)
    }

    @Synchronized
    fun count(): Long = readableDatabase.rawQuery("SELECT COUNT(*) FROM $TABLE", null).use { cursor ->
        if (cursor.moveToFirst()) cursor.getLong(0) else 0L
    }

    @Synchronized
    fun erase() {
        writableDatabase.delete(TABLE, null, null)
        vault.destroy()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE $TABLE (id TEXT PRIMARY KEY NOT NULL, at INTEGER NOT NULL, payload BLOB NOT NULL)")
        db.execSQL("CREATE INDEX events_at ON $TABLE (at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Schema 1 is the first published version. Later releases add explicit steps here
        // instead of recreating the table, because the payloads cannot be regenerated.
        check(newVersion == oldVersion) { "no migration from $oldVersion to $newVersion" }
    }

    private fun values(event: JournalEvent): ContentValues {
        val json = JSONObject()
            .put("at", event.at.toString())
            .put("kind", event.kind.name)
            .put("trigger", event.trigger)
            .put("place", event.place)
            .put("feeling", event.feeling)
            .put("note", event.note)
        return ContentValues().apply {
            put("id", event.id)
            put("at", event.at.toEpochMilli())
            put("payload", vault.seal(json.toString().toByteArray(Charsets.UTF_8), event.id))
        }
    }

    private fun decode(id: String, payload: ByteArray): JournalEvent {
        val json = JSONObject(String(vault.open(payload, id), Charsets.UTF_8))
        return JournalEvent(
            id = id,
            at = Instant.parse(json.getString("at")),
            kind = EventKind.valueOf(json.getString("kind")),
            trigger = json.optString("trigger"),
            place = json.optString("place"),
            feeling = json.optString("feeling"),
            note = json.optString("note")
        )
    }

    private companion object {
        const val DATABASE = "journal.db"
        const val TABLE = "events"
        const val SCHEMA = 1
    }
}
