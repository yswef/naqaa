package com.naqaa.app.prayer

/**
 * Fajr and Isha conventions. Angles follow the published definitions of each authority.
 * Umm al-Qura fixes Isha a number of minutes after sunset instead of using an angle, and
 * extends that interval during Ramadan.
 */
enum class CalculationMethod(
    val fajrAngle: Double,
    val ishaAngle: Double?,
    val ishaMinutesAfterMaghrib: Int?
) {
    UMM_AL_QURA(18.5, null, 90),
    MUSLIM_WORLD_LEAGUE(18.0, 17.0, null),
    EGYPTIAN(19.5, 17.5, null),
    KARACHI(18.0, 18.0, null),
    ISNA(15.0, 15.0, null),
    DUBAI(18.2, 18.2, null);

    fun ishaMinutes(ramadan: Boolean): Int? =
        ishaMinutesAfterMaghrib?.let { if (ramadan) it + RAMADAN_EXTRA_MINUTES else it }

    companion object {
        fun from(value: String): CalculationMethod = entries.firstOrNull { it.name == value } ?: UMM_AL_QURA

        private const val RAMADAN_EXTRA_MINUTES = 30
    }
}
