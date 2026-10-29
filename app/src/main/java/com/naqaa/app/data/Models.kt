package com.naqaa.app.data

import com.naqaa.app.prayer.CalculationMethod
import com.naqaa.app.prayer.PrayerName
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class EventKind { LAPSE, RESISTED, PRAYER, ADHKAR, QURAN, PLAN, GUARD_OFF }

enum class Trigger(val key: String) {
    BOREDOM("boredom"),
    LONELINESS("loneliness"),
    STRESS("stress"),
    ANGER("anger"),
    FATIGUE("fatigue"),
    NIGHT("night"),
    SOCIAL("social"),
    BED("bed"),
    PHONE("phone"),
    OTHER("other");

    companion object {
        fun from(value: String): Trigger? = entries.firstOrNull { it.key == value }
    }
}

enum class Place(val key: String) {
    ROOM("room"),
    BATHROOM("bathroom"),
    HOME("home"),
    WORK("work"),
    OUTSIDE("outside"),
    OTHER("other");

    companion object {
        fun from(value: String): Place? = entries.firstOrNull { it.key == value }
    }
}

enum class Feeling(val key: String) {
    ANXIOUS("anxious"),
    SAD("sad"),
    LONELY("lonely"),
    ANGRY("angry"),
    TIRED("tired"),
    BORED("bored"),
    NUMB("numb"),
    CALM("calm"),
    OTHER("other");

    companion object {
        fun from(value: String): Feeling? = entries.firstOrNull { it.key == value }
    }
}

enum class Persona(val key: String, val component: String) {
    NAQAA("naqaa", "PersonaNaqaa"),
    CALCULATOR("calculator", "PersonaCalculator"),
    NOTES("notes", "PersonaNotes"),
    TASKS("tasks", "PersonaTasks");

    companion object {
        fun from(value: String): Persona = entries.firstOrNull { it.key == value } ?: NAQAA
    }
}

data class JournalEvent(
    val id: String = UUID.randomUUID().toString(),
    val at: Instant = Instant.now(),
    val kind: EventKind,
    val trigger: String = "",
    val place: String = "",
    val feeling: String = "",
    val note: String = ""
)

/** A daily window during which one application is covered by the lock screen. */
data class LockRule(
    val packageName: String,
    val startMinute: Int,
    val endMinute: Int
) {
    val always: Boolean get() = startMinute == endMinute
}

data class Preferences(
    val startedAt: Instant = Instant.now(),
    val onboarded: Boolean = false,
    val language: String = "ar",
    val reason: String = "",
    val trustedName: String = "",
    val trustedPhone: String = "",
    val cityId: String = "",
    val latitude: Double = 21.4225,
    val longitude: Double = 39.8262,
    val prayerMethod: CalculationMethod = CalculationMethod.UMM_AL_QURA,
    val hanafiAsr: Boolean = false,
    val prayerReminders: Boolean = true,
    val adhkarReminders: Boolean = false,
    val vpnAutoStart: Boolean = false,
    val nightMode: Boolean = true,
    val blockShorts: Boolean = true,
    val blockTikTok: Boolean = false,
    val blockBrowserKeywords: Boolean = true,
    val lockedApps: List<LockRule> = emptyList(),
    val nightAllowlist: Set<String> = emptySet(),
    val persona: Persona = Persona.NAQAA,
    val pinSalt: String = "",
    val pinHash: String = "",
    val biometric: Boolean = false,
    val pinFailures: Int = 0,
    val pinLockedUntil: Instant? = null,
    val planStart: LocalDate? = null,
    val lastDelayCompletedAt: Instant? = null,
    /** Minutes added to (or taken from) each computed prayer time, keyed by [PrayerName.name]. */
    val prayerOffsets: Map<String, Int> = emptyMap(),
    /** Finished plan tasks as "day:task" keys, where task is b (behaviour), s (worship) or c (challenge). */
    val planTasks: Set<String> = emptySet(),
    /** Closes the apps of other VPN services while the filter is meant to be on. */
    val blockOtherVpn: Boolean = true,
    /** Plays the bundled call to prayer at prayer time when the audio file exists. */
    val adhanSound: Boolean = true,
    /** Plays a bundled hadith recording with the daily remembrance reminder. */
    val hadithSound: Boolean = true
) {
    val hasPin: Boolean get() = pinHash.isNotEmpty()

    /** The offsets in a form the calculator takes; unknown keys are ignored. */
    val prayerOffsetMap: Map<PrayerName, Int>
        get() = prayerOffsets.mapNotNull { (key, minutes) ->
            PrayerName.entries.firstOrNull { it.name == key }?.let { it to minutes }
        }.toMap()

    val hasTrustedContact: Boolean get() = trustedPhone.isNotBlank()
}
