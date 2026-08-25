package com.naqaa.app.data

import android.content.Context
import android.util.AtomicFile
import com.naqaa.app.prayer.CalculationMethod
import java.util.concurrent.CopyOnWriteArrayList
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate

/**
 * Preferences live in one encrypted file written through [AtomicFile], so an interrupted
 * write cannot leave a half serialized document behind. The current value is cached in
 * memory for the process and re-read from disk only when it starts, and registered
 * listeners are told after every write.
 */
class PreferencesStore(context: Context, private val vault: Vault) {

    private val file = AtomicFile(File(context.filesDir, FILE_NAME))
    private val listeners = CopyOnWriteArrayList<(Preferences) -> Unit>()
    private var cached = Preferences()
    private var loaded = false

    /** True when a stored document exists but could not be decrypted. */
    var unreadable: Boolean = false
        private set

    /** Told after every write, so long lived components can follow the settings. */
    fun addListener(listener: (Preferences) -> Unit): () -> Unit {
        listeners.add(listener)
        return { listeners.remove(listener) }
    }

    @Synchronized
    fun load(): Preferences {
        if (loaded) return cached
        val value = if (!file.baseFile.exists()) {
            Preferences()
        } else {
            runCatching { decode(String(vault.open(file.readFully(), CONTEXT), Charsets.UTF_8)) }
                .onFailure { unreadable = true }
                .getOrDefault(Preferences())
        }
        cached = value
        loaded = true
        return value
    }

    @Synchronized
    fun replace(value: Preferences) {
        val bytes = vault.seal(encode(value).toByteArray(Charsets.UTF_8), CONTEXT)
        val stream = file.startWrite()
        try {
            stream.write(bytes)
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
        cached = value
        loaded = true
        notifyChanged()
    }

    @Synchronized
    fun update(transform: (Preferences) -> Preferences): Preferences {
        val next = transform(load())
        replace(next)
        return next
    }

    @Synchronized
    fun wipe() {
        file.delete()
        cached = Preferences()
        loaded = true
        unreadable = false
        notifyChanged()
    }

    private fun notifyChanged() {
        val value = cached
        listeners.forEach { listener -> runCatching { listener(value) } }
    }

    private fun encode(value: Preferences): String {
        val locks = JSONArray()
        value.lockedApps.forEach { rule ->
            locks.put(
                JSONObject()
                    .put("package", rule.packageName)
                    .put("start", rule.startMinute)
                    .put("end", rule.endMinute)
            )
        }
        val allowlist = JSONArray()
        value.nightAllowlist.forEach { allowlist.put(it) }
        return JSONObject()
            .put("startedAt", value.startedAt.toString())
            .put("onboarded", value.onboarded)
            .put("language", value.language)
            .put("reason", value.reason)
            .put("trustedName", value.trustedName)
            .put("trustedPhone", value.trustedPhone)
            .put("cityId", value.cityId)
            .put("latitude", value.latitude)
            .put("longitude", value.longitude)
            .put("prayerMethod", value.prayerMethod.name)
            .put("hanafiAsr", value.hanafiAsr)
            .put("prayerReminders", value.prayerReminders)
            .put("adhkarReminders", value.adhkarReminders)
            .put("vpnAutoStart", value.vpnAutoStart)
            .put("nightMode", value.nightMode)
            .put("blockShorts", value.blockShorts)
            .put("blockTikTok", value.blockTikTok)
            .put("blockBrowserKeywords", value.blockBrowserKeywords)
            .put("lockedApps", locks)
            .put("nightAllowlist", allowlist)
            .put("persona", value.persona.key)
            .put("pinSalt", value.pinSalt)
            .put("pinHash", value.pinHash)
            .put("biometric", value.biometric)
            .put("pinFailures", value.pinFailures)
            .put("pinLockedUntil", value.pinLockedUntil?.toString())
            .put("planStart", value.planStart?.toString())
            .put("lastDelayCompletedAt", value.lastDelayCompletedAt?.toString())
            .toString()
    }

    private fun decode(text: String): Preferences {
        val json = JSONObject(text)
        val locks = json.optJSONArray("lockedApps")?.let { array ->
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val name = item.optString("package")
                if (name.isBlank()) null else LockRule(name, item.optInt("start"), item.optInt("end"))
            }
        }.orEmpty()
        val allowlist = json.optJSONArray("nightAllowlist")?.let { array ->
            (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }.toSet()
        }.orEmpty()
        return Preferences(
            startedAt = json.optString("startedAt").let { if (it.isBlank()) Instant.now() else Instant.parse(it) },
            onboarded = json.optBoolean("onboarded"),
            language = json.optString("language", "ar").takeIf { it == "ar" || it == "en" } ?: "ar",
            reason = json.optString("reason"),
            trustedName = json.optString("trustedName"),
            trustedPhone = json.optString("trustedPhone"),
            cityId = json.optString("cityId"),
            latitude = json.optDouble("latitude", 21.4225),
            longitude = json.optDouble("longitude", 39.8262),
            prayerMethod = CalculationMethod.from(json.optString("prayerMethod")),
            hanafiAsr = json.optBoolean("hanafiAsr"),
            prayerReminders = json.optBoolean("prayerReminders", true),
            adhkarReminders = json.optBoolean("adhkarReminders"),
            vpnAutoStart = json.optBoolean("vpnAutoStart"),
            nightMode = json.optBoolean("nightMode", true),
            blockShorts = json.optBoolean("blockShorts", true),
            blockTikTok = json.optBoolean("blockTikTok"),
            blockBrowserKeywords = json.optBoolean("blockBrowserKeywords", true),
            lockedApps = locks,
            nightAllowlist = allowlist,
            persona = Persona.from(json.optString("persona")),
            pinSalt = json.optString("pinSalt"),
            pinHash = json.optString("pinHash"),
            biometric = json.optBoolean("biometric"),
            pinFailures = json.optInt("pinFailures"),
            pinLockedUntil = json.optString("pinLockedUntil").takeIf(String::isNotBlank)?.let(Instant::parse),
            planStart = json.optString("planStart").takeIf(String::isNotBlank)?.let(LocalDate::parse),
            lastDelayCompletedAt = json.optString("lastDelayCompletedAt").takeIf(String::isNotBlank)?.let(Instant::parse)
        )
    }

    private companion object {
        const val FILE_NAME = "preferences.enc"
        const val CONTEXT = "preferences"
    }
}
