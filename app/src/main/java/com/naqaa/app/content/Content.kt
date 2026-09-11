package com.naqaa.app.content

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import kotlin.random.Random

data class Verse(
    val surahNumber: Int,
    val surahNameAr: String,
    val surahNameEn: String,
    val ayah: Int,
    val textAr: String,
    val topics: Set<String>
) {
    val referenceAr: String get() = "$surahNameAr، الآية $ayah"
    val referenceEn: String get() = "$surahNameEn $surahNumber:$ayah"
}

data class Hadith(
    val collectionKey: String,
    val collectionAr: String,
    val collectionEn: String,
    val number: Int,
    val textAr: String,
    val topics: Set<String>
) {
    val referenceAr: String get() = "$collectionAr، رقم $number"
    val referenceEn: String get() = "$collectionEn $number"
}

data class Dhikr(
    val id: String,
    val period: String,
    val textAr: String,
    val repeat: Int,
    val sourceAr: String
)

data class Motivation(val id: String, val context: String, val textAr: String, val textEn: String) {
    fun text(language: String): String = if (language == "en") textEn else textAr
}

data class PlanDay(
    val day: Int,
    val behaviorAr: String,
    val behaviorEn: String,
    val spiritualAr: String,
    val spiritualEn: String,
    val challengeAr: String,
    val challengeEn: String
) {
    fun behavior(language: String): String = if (language == "en") behaviorEn else behaviorAr
    fun spiritual(language: String): String = if (language == "en") spiritualEn else spiritualAr
    fun challenge(language: String): String = if (language == "en") challengeEn else challengeAr
}

/** A short encouragement pair shown when protection starts or a page is filtered. */
data class Card(
    val verseAr: String,
    val referenceAr: String,
    val referenceEn: String,
    val motivationAr: String,
    val motivationEn: String
) {
    fun motivation(language: String): String = if (language == "en") motivationEn else motivationAr
    fun reference(language: String): String = if (language == "en") referenceEn else referenceAr
}

/**
 * Bundled religious and motivational material.
 *
 * Scripture is stored in Arabic only: the Uthmani text is the reference, and a translation
 * produced here would misleadingly look authoritative. Explanatory and motivational
 * material is available in both interface languages.
 */
object Content {

    data class Library(
        val verses: List<Verse>,
        val hadiths: List<Hadith>,
        val adhkar: List<Dhikr>,
        val motivations: List<Motivation>,
        val plan: List<PlanDay>
    )

    private val empty = Library(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())

    @Volatile
    private var library: Library = empty

    @Synchronized
    fun load(context: Context) {
        if (library !== empty) return
        library = runCatching { read(context) }.getOrDefault(empty)
    }

    fun verses(topic: String): List<Verse> = library.verses.filter { topic in it.topics }

    fun hadiths(topic: String): List<Hadith> = library.hadiths.filter { topic in it.topics }

    fun adhkar(period: String): List<Dhikr> = library.adhkar.filter { it.period == period || it.period == "any" }

    fun plan(): List<PlanDay> = library.plan

    fun verseFor(topics: List<String>, random: Random): Verse? =
        topics.asSequence().flatMap { verses(it).asSequence() }.distinct().toList().randomOrNull(random)

    fun hadithFor(topics: List<String>, random: Random): Hadith? =
        topics.asSequence().flatMap { hadiths(it).asSequence() }.distinct().toList().randomOrNull(random)

    fun motivation(context: String, random: Random): Motivation? =
        library.motivations.filter { it.context == context }.randomOrNull(random)

    fun verseOfDay(date: LocalDate): Verse? = pick(library.verses, date.toEpochDay())

    fun hadithOfDay(date: LocalDate): Hadith? = pick(library.hadiths, date.toEpochDay() * 3 + 1)

    fun wirdOfDay(date: LocalDate): Verse? = pick(library.verses, date.toEpochDay() * 5 + 2)

    fun protectionCard(random: Random = Random.Default): Card {
        val verse = verseFor(listOf(TOPIC_URGE, TOPIC_STRENGTH, TOPIC_PATIENCE), random)
        val motivation = motivation(CONTEXT_PROTECT, random) ?: motivation(CONTEXT_URGE, random)
        return Card(
            verseAr = verse?.textAr.orEmpty(),
            referenceAr = verse?.referenceAr.orEmpty(),
            referenceEn = verse?.referenceEn.orEmpty(),
            motivationAr = motivation?.textAr.orEmpty(),
            motivationEn = motivation?.textEn.orEmpty()
        )
    }

    private fun <T> pick(items: List<T>, salt: Long): T? {
        if (items.isEmpty()) return null
        val index = (((salt % items.size) + items.size) % items.size).toInt()
        return items[index]
    }

    private fun read(context: Context): Library = Library(
        verses = context.assets.open(VERSES).use { parseVerses(it.readBytes().decodeToString()) },
        hadiths = context.assets.open(HADITHS).use { parseHadiths(it.readBytes().decodeToString()) },
        adhkar = context.assets.open(ADHKAR).use { parseAdhkar(it.readBytes().decodeToString()) },
        motivations = context.assets.open(MOTIVATION).use { parseMotivation(it.readBytes().decodeToString()) },
        plan = context.assets.open(PLAN).use { parsePlan(it.readBytes().decodeToString()) }
    )

    private fun parseVerses(text: String): List<Verse> = array(text).mapNotNull { item ->
        val number = item.optInt("surah")
        val ayah = item.optInt("ayah")
        val body = item.optString("text")
        if (number <= 0 || ayah <= 0 || body.isBlank()) return@mapNotNull null
        Verse(
            surahNumber = number,
            surahNameAr = item.optString("surahAr"),
            surahNameEn = item.optString("surahEn"),
            ayah = ayah,
            textAr = body,
            topics = item.optJSONArray("topics").toStrings()
        )
    }

    private fun parseHadiths(text: String): List<Hadith> = array(text).mapNotNull { item ->
        val collection = item.optString("collection")
        val number = item.optInt("number")
        val body = item.optString("text")
        if (collection.isBlank() || number <= 0 || body.isBlank()) return@mapNotNull null
        Hadith(
            collectionKey = collection,
            collectionAr = if (collection == "muslim") "صحيح مسلم" else "صحيح البخاري",
            collectionEn = if (collection == "muslim") "Sahih Muslim" else "Sahih al-Bukhari",
            number = number,
            textAr = body,
            topics = item.optJSONArray("topics").toStrings()
        )
    }

    private fun parseAdhkar(text: String): List<Dhikr> = array(text).mapNotNull { item ->
        val body = item.optString("text")
        if (body.isBlank()) return@mapNotNull null
        Dhikr(
            id = item.optString("id", body.take(12)),
            period = item.optString("period", "any"),
            textAr = body,
            repeat = item.optInt("repeat", 1).coerceAtLeast(1),
            sourceAr = item.optString("source")
        )
    }

    private fun parseMotivation(text: String): List<Motivation> = array(text).mapNotNull { item ->
        val arabic = item.optString("ar")
        if (arabic.isBlank()) return@mapNotNull null
        Motivation(
            id = item.optString("id", arabic.take(12)),
            context = item.optString("context", "protect"),
            textAr = arabic,
            textEn = item.optString("en")
        )
    }

    private fun parsePlan(text: String): List<PlanDay> = array(text).mapNotNull { item ->
        val day = item.optInt("day")
        if (day <= 0) return@mapNotNull null
        PlanDay(
            day = day,
            behaviorAr = item.optString("behaviorAr"),
            behaviorEn = item.optString("behaviorEn"),
            spiritualAr = item.optString("spiritualAr"),
            spiritualEn = item.optString("spiritualEn"),
            challengeAr = item.optString("challengeAr"),
            challengeEn = item.optString("challengeEn")
        )
    }

    private fun array(text: String): List<JSONObject> {
        val parsed = JSONArray(text)
        return (0 until parsed.length()).mapNotNull { parsed.optJSONObject(it) }
    }

    private fun JSONArray?.toStrings(): Set<String> {
        if (this == null) return emptySet()
        return (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }.toSet()
    }

    const val TOPIC_URGE = "urge"
    const val TOPIC_REPENTANCE = "repentance"
    const val TOPIC_HOPE = "hope"
    const val TOPIC_STRENGTH = "strength"
    const val TOPIC_PATIENCE = "patience"
    const val TOPIC_GAZE = "gaze"
    const val TOPIC_PRAYER = "prayer"
    const val TOPIC_DHIKR = "dhikr"
    const val TOPIC_MERCY = "mercy"

    const val CONTEXT_PROTECT = "protect"
    const val CONTEXT_URGE = "urge"
    const val CONTEXT_LAPSE = "lapse"
    const val CONTEXT_DAWN = "dawn"
    const val CONTEXT_STREAK = "streak"
    const val CONTEXT_NIGHT = "night"

    const val PERIOD_MORNING = "morning"
    const val PERIOD_EVENING = "evening"

    private const val VERSES = "content/verses.json"
    private const val HADITHS = "content/hadiths.json"
    private const val ADHKAR = "content/adhkar.json"
    private const val MOTIVATION = "content/motivation.json"
    private const val PLAN = "content/plan.json"
}
