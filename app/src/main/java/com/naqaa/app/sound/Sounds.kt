package com.naqaa.app.sound

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.naqaa.app.prayer.PrayerName
import java.time.LocalDate

/**
 * The bundled recordings: the call to prayer and short hadith readings.
 *
 * The files are not part of the source. They are dropped into `app/src/main/assets/sounds/`
 * and found by name at run time, so the application builds and works without them and a file
 * added later needs no code change. Assets are used instead of raw resources because the
 * resource shrinker may remove or rename a raw resource that no code names, and an asset is
 * always kept as it is.
 *
 * Names: `adhan.mp3` for every prayer, optionally `adhan_fajr.mp3` for the dawn prayer
 * (a different call), and any number of files starting with `hadith`, one of which is
 * chosen per day. mp3, ogg, m4a and wav are accepted.
 */
object Sounds {

    const val DIRECTORY = "sounds"
    private val EXTENSIONS = setOf("mp3", "ogg", "m4a", "wav", "aac")

    /** The files found in the folder, sorted by name; empty when the folder does not exist. */
    fun files(context: Context): List<String> = runCatching {
        context.assets.list(DIRECTORY).orEmpty()
            .filter { it.substringAfterLast('.', "").lowercase() in EXTENSIONS }
            .sorted()
    }.getOrDefault(emptyList())

    /** The call to prayer for this prayer: its own file first, then the common one. */
    fun adhan(context: Context, prayer: PrayerName?): String? {
        val all = files(context)
        val own = prayer?.let { name -> all.firstOrNull { it.startsWith("adhan_" + name.name.lowercase()) } }
        return own ?: all.firstOrNull { it.startsWith("adhan.") } ?: all.firstOrNull { it.startsWith("adhan") }
    }

    fun hadithFiles(context: Context): List<String> = files(context).filter { it.startsWith("hadith") }

    /** One hadith recording per day, cycling through the files in name order. */
    fun hadithOfDay(context: Context, date: LocalDate): String? {
        val list = hadithFiles(context)
        if (list.isEmpty()) return null
        return list[date.dayOfYear % list.size]
    }

    /**
     * Starts playing a file from a foreground service. Returns false when the system refuses
     * to start it (a background start the platform does not allow), so the caller can fall
     * back to an ordinary notification.
     */
    fun play(context: Context, file: String, loud: Boolean): Boolean = runCatching {
        val intent = Intent(context, SoundService::class.java)
            .putExtra(SoundService.EXTRA_FILE, file)
            .putExtra(SoundService.EXTRA_LOUD, loud)
        ContextCompat.startForegroundService(context, intent)
    }.isSuccess

    fun stop(context: Context) {
        runCatching { context.stopService(Intent(context, SoundService::class.java)) }
    }
}
