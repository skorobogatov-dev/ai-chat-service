package dev.skorobogatov.mcp.activities

import kotlinx.serialization.Serializable

/**
 * Тип активности
 */
enum class ActivityType {
    OUTDOOR,    // На улице
    INDOOR,     // В помещении
    SPORT,      // Спорт
    CULTURAL,   // Культурные мероприятия
    RELAXATION  // Отдых
}

/**
 * Условия погоды для выбора активностей
 */
enum class WeatherCondition {
    CLEAR,          // Ясно
    PARTLY_CLOUDY,  // Переменная облачность
    CLOUDY,         // Облачно
    RAIN,           // Дождь
    SNOW,           // Снег
    STORM,          // Гроза
    FOG             // Туман
}

/**
 * Рекомендация активности
 */
@Serializable
data class ActivityRecommendation(
    val name: String,
    val type: String,
    val description: String,
    val reason: String,
    val suitability: Int // 1-10, насколько подходит для текущей погоды
)

/**
 * Погодные условия для анализа
 */
data class WeatherContext(
    val temperature: Double,
    val condition: WeatherCondition,
    val precipitation: Double,
    val windSpeed: Double,
    val humidity: Int
)

/**
 * Определение погодного условия по коду WMO
 */
fun getWeatherConditionFromCode(code: Int): WeatherCondition {
    return when (code) {
        0 -> WeatherCondition.CLEAR
        1, 2, 3 -> WeatherCondition.PARTLY_CLOUDY
        45, 48 -> WeatherCondition.FOG
        51, 53, 55, 61, 63, 65, 80, 81, 82 -> WeatherCondition.RAIN
        71, 73, 75, 77, 85, 86 -> WeatherCondition.SNOW
        95, 96, 99 -> WeatherCondition.STORM
        else -> WeatherCondition.CLOUDY
    }
}
