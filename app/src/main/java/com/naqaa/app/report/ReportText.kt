package com.naqaa.app.report

import android.content.Context
import com.naqaa.app.R
import com.naqaa.app.util.TimeX

/**
 * Turns the analysis into words once, so the screen and the shared file say the same thing
 * and a new recommendation kind cannot be forgotten in one of them.
 */
object ReportText {

    fun risk(context: Context, risk: Risk): String = context.getString(
        when (risk) {
            Risk.LOW -> R.string.risk_low
            Risk.MEDIUM -> R.string.risk_medium
            Risk.HIGH -> R.string.risk_high
        }
    )

    fun trigger(context: Context, key: String?): String = context.getString(
        when (key) {
            "boredom" -> R.string.trigger_boredom
            "loneliness" -> R.string.trigger_loneliness
            "stress" -> R.string.trigger_stress
            "anger" -> R.string.trigger_anger
            "fatigue" -> R.string.trigger_fatigue
            "night" -> R.string.trigger_night
            "social" -> R.string.trigger_social
            "bed" -> R.string.trigger_bed
            "phone" -> R.string.trigger_phone
            else -> R.string.trigger_other
        }
    )

    fun place(context: Context, key: String?): String = context.getString(
        when (key) {
            "room" -> R.string.place_room
            "bathroom" -> R.string.place_bathroom
            "home" -> R.string.place_home
            "work" -> R.string.place_work
            "outside" -> R.string.place_outside
            else -> R.string.place_other
        }
    )

    fun feeling(context: Context, key: String?): String = context.getString(
        when (key) {
            "anxious" -> R.string.feeling_anxious
            "sad" -> R.string.feeling_sad
            "lonely" -> R.string.feeling_lonely
            "angry" -> R.string.feeling_angry
            "tired" -> R.string.feeling_tired
            "bored" -> R.string.feeling_bored
            "numb" -> R.string.feeling_numb
            "calm" -> R.string.feeling_calm
            else -> R.string.feeling_other
        }
    )

    fun recommendation(context: Context, recommendation: Recommendation): String = when (recommendation.kind) {
        Recommendation.Kind.WATCH_HOUR -> context.getString(
            R.string.recommendation_hour,
            TimeX.clock((recommendation.hour ?: 0) * 60, com.naqaa.app.util.LocaleX.displayLocale(context))
        )
        Recommendation.Kind.AVOID_TRIGGER -> context.getString(
            R.string.recommendation_trigger,
            trigger(context, recommendation.key)
        )
        Recommendation.Kind.CHANGE_PLACE -> context.getString(
            R.string.recommendation_place,
            place(context, recommendation.key)
        )
        Recommendation.Kind.PRAY_MORE -> context.getString(R.string.recommendation_prayer)
        Recommendation.Kind.READ_ADHKAR -> context.getString(R.string.recommendation_adhkar)
        Recommendation.Kind.START_PLAN -> context.getString(R.string.recommendation_plan)
        Recommendation.Kind.BREAK_ISOLATION -> context.getString(R.string.recommendation_contact)
        Recommendation.Kind.KEEP_GOING -> context.getString(R.string.recommendation_keep_going)
    }
}
