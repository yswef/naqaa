package com.naqaa.app.prayer

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * A short list of cities with coordinates rounded to the city centre. It exists so the
 * user can pick a location without a network request; the settings screen accepts exact
 * coordinates as well, and a one time location fix can pre-select the nearest entry.
 */
data class City(
    val id: String,
    val nameAr: String,
    val nameEn: String,
    val countryAr: String,
    val countryEn: String,
    val latitude: Double,
    val longitude: Double,
    val method: CalculationMethod
)

object Cities {

    val all: List<City> = listOf(
        City("makkah", "مكة المكرمة", "Makkah", "السعودية", "Saudi Arabia", 21.4225, 39.8262, CalculationMethod.UMM_AL_QURA),
        City("madinah", "المدينة المنورة", "Madinah", "السعودية", "Saudi Arabia", 24.4672, 39.6111, CalculationMethod.UMM_AL_QURA),
        City("riyadh", "الرياض", "Riyadh", "السعودية", "Saudi Arabia", 24.7136, 46.6753, CalculationMethod.UMM_AL_QURA),
        City("jeddah", "جدة", "Jeddah", "السعودية", "Saudi Arabia", 21.4858, 39.1925, CalculationMethod.UMM_AL_QURA),
        City("dammam", "الدمام", "Dammam", "السعودية", "Saudi Arabia", 26.3927, 49.9777, CalculationMethod.UMM_AL_QURA),
        City("abha", "أبها", "Abha", "السعودية", "Saudi Arabia", 18.2465, 42.5117, CalculationMethod.UMM_AL_QURA),
        City("dubai", "دبي", "Dubai", "الإمارات", "United Arab Emirates", 25.2048, 55.2708, CalculationMethod.DUBAI),
        City("abudhabi", "أبوظبي", "Abu Dhabi", "الإمارات", "United Arab Emirates", 24.4539, 54.3773, CalculationMethod.DUBAI),
        City("sharjah", "الشارقة", "Sharjah", "الإمارات", "United Arab Emirates", 25.3463, 55.4209, CalculationMethod.DUBAI),
        City("doha", "الدوحة", "Doha", "قطر", "Qatar", 25.2854, 51.5310, CalculationMethod.UMM_AL_QURA),
        City("kuwait", "مدينة الكويت", "Kuwait City", "الكويت", "Kuwait", 29.3759, 47.9774, CalculationMethod.UMM_AL_QURA),
        City("manama", "المنامة", "Manama", "البحرين", "Bahrain", 26.2285, 50.5860, CalculationMethod.UMM_AL_QURA),
        City("muscat", "مسقط", "Muscat", "عمان", "Oman", 23.5880, 58.3829, CalculationMethod.UMM_AL_QURA),
        City("sanaa", "صنعاء", "Sanaa", "اليمن", "Yemen", 15.3694, 44.1910, CalculationMethod.UMM_AL_QURA),
        City("aden", "عدن", "Aden", "اليمن", "Yemen", 12.7855, 45.0187, CalculationMethod.UMM_AL_QURA),
        City("cairo", "القاهرة", "Cairo", "مصر", "Egypt", 30.0444, 31.2357, CalculationMethod.EGYPTIAN),
        City("alexandria", "الإسكندرية", "Alexandria", "مصر", "Egypt", 31.2001, 29.9187, CalculationMethod.EGYPTIAN),
        City("aswan", "أسوان", "Aswan", "مصر", "Egypt", 24.0889, 32.8998, CalculationMethod.EGYPTIAN),
        City("khartoum", "الخرطوم", "Khartoum", "السودان", "Sudan", 15.5007, 32.5599, CalculationMethod.EGYPTIAN),
        City("amman", "عمّان", "Amman", "الأردن", "Jordan", 31.9454, 35.9284, CalculationMethod.MUSLIM_WORLD_LEAGUE),
        City("quds", "القدس", "Jerusalem", "فلسطين", "Palestine", 31.7683, 35.2137, CalculationMethod.MUSLIM_WORLD_LEAGUE),
        City("gaza", "غزة", "Gaza", "فلسطين", "Palestine", 31.5017, 34.4668, CalculationMethod.EGYPTIAN),
        City("beirut", "بيروت", "Beirut", "لبنان", "Lebanon", 33.8938, 35.5018, CalculationMethod.MUSLIM_WORLD_LEAGUE),
        City("damascus", "دمشق", "Damascus", "سوريا", "Syria", 33.5138, 36.2765, CalculationMethod.MUSLIM_WORLD_LEAGUE),
        City("aleppo", "حلب", "Aleppo", "سوريا", "Syria", 36.2021, 37.1343, CalculationMethod.MUSLIM_WORLD_LEAGUE),
        City("baghdad", "بغداد", "Baghdad", "العراق", "Iraq", 33.3152, 44.3661, CalculationMethod.KARACHI),
        City("basra", "البصرة", "Basra", "العراق", "Iraq", 30.5085, 47.7804, CalculationMethod.KARACHI),
        City("mosul", "الموصل", "Mosul", "العراق", "Iraq", 36.3350, 43.1189, CalculationMethod.KARACHI),
        City("tripoli", "طرابلس", "Tripoli", "ليبيا", "Libya", 32.8872, 13.1913, CalculationMethod.EGYPTIAN),
        City("tunis", "تونس", "Tunis", "تونس", "Tunisia", 36.8065, 10.1815, CalculationMethod.EGYPTIAN),
        City("algiers", "الجزائر", "Algiers", "الجزائر", "Algeria", 36.7538, 3.0588, CalculationMethod.MUSLIM_WORLD_LEAGUE),
        City("oran", "وهران", "Oran", "الجزائر", "Algeria", 35.6971, -0.6308, CalculationMethod.MUSLIM_WORLD_LEAGUE),
        City("casablanca", "الدار البيضاء", "Casablanca", "المغرب", "Morocco", 33.5731, -7.5898, CalculationMethod.MUSLIM_WORLD_LEAGUE),
        City("rabat", "الرباط", "Rabat", "المغرب", "Morocco", 34.0209, -6.8416, CalculationMethod.MUSLIM_WORLD_LEAGUE),
        City("marrakesh", "مراكش", "Marrakesh", "المغرب", "Morocco", 31.6295, -7.9811, CalculationMethod.MUSLIM_WORLD_LEAGUE),
        City("nouakchott", "نواكشوط", "Nouakchott", "موريتانيا", "Mauritania", 18.0735, -15.9582, CalculationMethod.MUSLIM_WORLD_LEAGUE),
        City("mogadishu", "مقديشو", "Mogadishu", "الصومال", "Somalia", 2.0469, 45.3182, CalculationMethod.MUSLIM_WORLD_LEAGUE),
        City("dakar", "داكار", "Dakar", "السنغال", "Senegal", 14.7167, -17.4677, CalculationMethod.MUSLIM_WORLD_LEAGUE),
        City("bamako", "باماكو", "Bamako", "مالي", "Mali", 12.6392, -8.0029, CalculationMethod.MUSLIM_WORLD_LEAGUE),
        City("kano", "كانو", "Kano", "نيجيريا", "Nigeria", 12.0022, 8.5920, CalculationMethod.MUSLIM_WORLD_LEAGUE),
        City("lagos", "لاغوس", "Lagos", "نيجيريا", "Nigeria", 6.5244, 3.3792, CalculationMethod.MUSLIM_WORLD_LEAGUE),
        City("istanbul", "إسطنبول", "Istanbul", "تركيا", "Türkiye", 41.0082, 28.9784, CalculationMethod.MUSLIM_WORLD_LEAGUE),
        City("ankara", "أنقرة", "Ankara", "تركيا", "Türkiye", 39.9334, 32.8597, CalculationMethod.MUSLIM_WORLD_LEAGUE),
        City("tehran", "طهران", "Tehran", "إيران", "Iran", 35.6892, 51.3890, CalculationMethod.KARACHI),
        City("karachi", "كراتشي", "Karachi", "باكستان", "Pakistan", 24.8607, 67.0011, CalculationMethod.KARACHI),
        City("lahore", "لاهور", "Lahore", "باكستان", "Pakistan", 31.5204, 74.3587, CalculationMethod.KARACHI),
        City("islamabad", "إسلام آباد", "Islamabad", "باكستان", "Pakistan", 33.6844, 73.0479, CalculationMethod.KARACHI),
        City("kabul", "كابل", "Kabul", "أفغانستان", "Afghanistan", 34.5553, 69.2075, CalculationMethod.KARACHI),
        City("delhi", "دلهي", "Delhi", "الهند", "India", 28.6139, 77.2090, CalculationMethod.KARACHI),
        City("mumbai", "مومباي", "Mumbai", "الهند", "India", 19.0760, 72.8777, CalculationMethod.KARACHI),
        City("dhaka", "دكا", "Dhaka", "بنغلاديش", "Bangladesh", 23.8103, 90.4125, CalculationMethod.KARACHI),
        City("jakarta", "جاكرتا", "Jakarta", "إندونيسيا", "Indonesia", -6.2088, 106.8456, CalculationMethod.EGYPTIAN),
        City("kualalumpur", "كوالالمبور", "Kuala Lumpur", "ماليزيا", "Malaysia", 3.1390, 101.6869, CalculationMethod.MUSLIM_WORLD_LEAGUE),
        City("singapore", "سنغافورة", "Singapore", "سنغافورة", "Singapore", 1.3521, 103.8198, CalculationMethod.MUSLIM_WORLD_LEAGUE),
        City("tashkent", "طشقند", "Tashkent", "أوزبكستان", "Uzbekistan", 41.2995, 69.2401, CalculationMethod.MUSLIM_WORLD_LEAGUE),
        City("almaty", "ألماتي", "Almaty", "كازاخستان", "Kazakhstan", 43.2220, 76.8512, CalculationMethod.MUSLIM_WORLD_LEAGUE),
        City("baku", "باكو", "Baku", "أذربيجان", "Azerbaijan", 40.4093, 49.8671, CalculationMethod.MUSLIM_WORLD_LEAGUE),
        City("sarajevo", "سراييفو", "Sarajevo", "البوسنة", "Bosnia and Herzegovina", 43.8563, 18.4131, CalculationMethod.MUSLIM_WORLD_LEAGUE),
        City("london", "لندن", "London", "بريطانيا", "United Kingdom", 51.5074, -0.1278, CalculationMethod.MUSLIM_WORLD_LEAGUE),
        City("paris", "باريس", "Paris", "فرنسا", "France", 48.8566, 2.3522, CalculationMethod.MUSLIM_WORLD_LEAGUE),
        City("berlin", "برلين", "Berlin", "ألمانيا", "Germany", 52.5200, 13.4050, CalculationMethod.MUSLIM_WORLD_LEAGUE),
        City("rome", "روما", "Rome", "إيطاليا", "Italy", 41.9028, 12.4964, CalculationMethod.MUSLIM_WORLD_LEAGUE),
        City("madrid", "مدريد", "Madrid", "إسبانيا", "Spain", 40.4168, -3.7038, CalculationMethod.MUSLIM_WORLD_LEAGUE),
        City("moscow", "موسكو", "Moscow", "روسيا", "Russia", 55.7558, 37.6173, CalculationMethod.MUSLIM_WORLD_LEAGUE),
        City("newyork", "نيويورك", "New York", "الولايات المتحدة", "United States", 40.7128, -74.0060, CalculationMethod.ISNA),
        City("chicago", "شيكاغو", "Chicago", "الولايات المتحدة", "United States", 41.8781, -87.6298, CalculationMethod.ISNA),
        City("toronto", "تورونتو", "Toronto", "كندا", "Canada", 43.6532, -79.3832, CalculationMethod.ISNA),
        City("sydney", "سيدني", "Sydney", "أستراليا", "Australia", -33.8688, 151.2093, CalculationMethod.MUSLIM_WORLD_LEAGUE),
        City("tokyo", "طوكيو", "Tokyo", "اليابان", "Japan", 35.6762, 139.6503, CalculationMethod.MUSLIM_WORLD_LEAGUE),
        City("beijing", "بكين", "Beijing", "الصين", "China", 39.9042, 116.4074, CalculationMethod.MUSLIM_WORLD_LEAGUE)
    )

    fun byId(id: String): City? = all.firstOrNull { it.id == id }

    /** Nearest entry by equirectangular distance, used to pre-select a location from GPS. */
    fun nearest(latitude: Double, longitude: Double): City = all.minByOrNull { city ->
        val meanLatitude = Math.toRadians((city.latitude + latitude) / 2)
        val north = city.latitude - latitude
        val east = (city.longitude - longitude) * cos(meanLatitude)
        north * north + east * east
    } ?: all.first()

    fun distanceKm(city: City, latitude: Double, longitude: Double): Int {
        val north = Math.toRadians(city.latitude - latitude)
        val east = Math.toRadians(city.longitude - longitude)
        val mid = Math.toRadians((city.latitude + latitude) / 2)
        val x = east * cos(mid)
        return (EARTH_RADIUS_KM * kotlin.math.sqrt(north * north + x * x)).roundToInt().let { abs(it) }
    }

    private const val EARTH_RADIUS_KM = 6371.0
}
