package com.naqaa.app.data

import com.naqaa.app.AppGraph
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.time.Instant
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * A portable copy of everything the application keeps: settings, journal and accounts.
 *
 * The Keystore key that seals the data on the device cannot leave it, so a copy of the data
 * folder is useless elsewhere. The backup is therefore re-sealed with a key derived from a
 * password chosen at export time (PBKDF2 + AES-256-GCM), which lets the file move to another
 * phone or survive a reinstall. The payload is plain JSON with a format number; readers
 * ignore fields they do not know and give defaults to fields that an older file lacks, so a
 * file from any version opens in any later version.
 *
 * File layout: "NQBK" + format byte, 16 bytes of salt, 12 bytes of IV, then the ciphertext.
 */
object Backup {

    const val FORMAT = 1
    const val MIN_PASSWORD = 6
    private val MAGIC = byteArrayOf('N'.code.toByte(), 'Q'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte(), FORMAT.toByte())
    private const val SALT = 16
    private const val IV = 12
    private const val ITERATIONS = 200_000
    private const val AAD = "naqaa-backup"
    private const val MAX_BYTES = 64 * 1024 * 1024

    sealed interface Outcome {
        data class Imported(val events: Int, val users: Int) : Outcome
        data object WrongPasswordOrDamaged : Outcome
        data object NotABackup : Outcome
        data object TooNew : Outcome
        data class Failed(val reason: String) : Outcome
    }

    /** Builds the encrypted file contents. Slow (PBKDF2); call it off the main thread. */
    fun export(graph: AppGraph, password: CharArray, appVersion: String): ByteArray {
        val events = JSONArray()
        graph.snapshot().events.forEach { event ->
            events.put(
                JSONObject()
                    .put("id", event.id)
                    .put("at", event.at.toString())
                    .put("kind", event.kind.name)
                    .put("trigger", event.trigger)
                    .put("place", event.place)
                    .put("feeling", event.feeling)
                    .put("note", event.note)
            )
        }
        val users = JSONObject(UserStore.encode(graph.users.all())).getJSONArray("users")
        val payload = JSONObject()
            .put("format", FORMAT)
            .put("app", "naqaa")
            .put("appVersion", appVersion)
            .put("exportedAt", Instant.now().toString())
            .put("preferences", JSONObject(graph.preferences.exportJson()))
            .put("events", events)
            .put("users", users)
            .toString()
            .toByteArray(Charsets.UTF_8)

        val salt = ByteArray(SALT).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
        cipher.updateAAD(AAD.toByteArray(Charsets.UTF_8))
        return MAGIC + salt + iv + cipher.doFinal(payload)
    }

    /**
     * Reads a backup and merges it into this installation: the settings of the file replace
     * the current ones, events are added when their id is new, and accounts are added when
     * their name is free. Nothing is deleted. Slow; call it off the main thread.
     */
    fun import(graph: AppGraph, data: ByteArray, password: CharArray): Outcome {
        if (data.size < MAGIC.size + SALT + IV + 16 || data.size > MAX_BYTES) return Outcome.NotABackup
        if (!data.copyOfRange(0, 4).contentEquals(MAGIC.copyOfRange(0, 4))) return Outcome.NotABackup
        if (data[4].toInt() > FORMAT) return Outcome.TooNew
        val salt = data.copyOfRange(5, 5 + SALT)
        val iv = data.copyOfRange(5 + SALT, 5 + SALT + IV)
        val body = data.copyOfRange(5 + SALT + IV, data.size)
        val plain = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
            cipher.updateAAD(AAD.toByteArray(Charsets.UTF_8))
            cipher.doFinal(body)
        } catch (_: Exception) {
            return Outcome.WrongPasswordOrDamaged
        }
        return try {
            val root = JSONObject(String(plain, Charsets.UTF_8))
            if (root.optString("app") != "naqaa") return Outcome.NotABackup
            val incoming = graph.preferences.parse(root.getJSONObject("preferences").toString())
            val events = root.optJSONArray("events")?.let { array ->
                (0 until array.length()).mapNotNull { index ->
                    val item = array.optJSONObject(index) ?: return@mapNotNull null
                    runCatching {
                        JournalEvent(
                            id = item.getString("id"),
                            at = Instant.parse(item.getString("at")),
                            kind = EventKind.valueOf(item.getString("kind")),
                            trigger = item.optString("trigger"),
                            place = item.optString("place"),
                            feeling = item.optString("feeling"),
                            note = item.optString("note")
                        )
                    }.getOrNull()
                }
            }.orEmpty()
            val users = UserStore.decode(JSONObject().put("users", root.optJSONArray("users") ?: JSONArray()).toString())
            graph.preferences.replace(incoming)
            val added = graph.journal.importEvents(events)
            val newUsers = graph.users.merge(users)
            Outcome.Imported(added, newUsers)
        } catch (error: Exception) {
            Outcome.Failed(error.javaClass.simpleName + ": " + (error.message ?: ""))
        }
    }

    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        return try {
            SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
