package com.naqaa.app.util

import android.content.Context
import android.content.res.Configuration
import com.naqaa.app.NaqaaApplication
import java.util.Locale

/**
 * Applies the interface language chosen in the application to any context, including the
 * ones created by services and receivers where the system locale would otherwise win.
 * Arabic is the default because the content of the application is Arabic.
 */
object LocaleX {

    const val ARABIC = "ar"
    const val ENGLISH = "en"
    const val DEFAULT_LANGUAGE = ARABIC

    fun sanitize(language: String?): String = if (language == ENGLISH) ENGLISH else DEFAULT_LANGUAGE

    fun language(context: Context): String = sanitize(
        runCatching { (context.applicationContext as NaqaaApplication).graph.current().language }.getOrNull()
    )

    /**
     * Locale for dates and numbers: Arabic month names, Latin digits. The interface mixes
     * both, so a single convention keeps times and counters readable in either language.
     */
    fun displayLocale(language: String): Locale =
        if (sanitize(language) == ENGLISH) Locale.ENGLISH else Locale.forLanguageTag("ar-u-nu-latn")

    fun displayLocale(context: Context): Locale = displayLocale(language(context))

    fun localized(context: Context, language: String = language(context)): Context {
        val locale = Locale.forLanguageTag(sanitize(language))
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        return context.createConfigurationContext(configuration)
    }
}
