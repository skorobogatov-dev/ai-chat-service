package dev.skorobogatov.mcp.weather

import kotlinx.serialization.Serializable

/**
 * Open-Meteo API Response для текущей погоды
 */
@Serializable
data class OpenMeteoCurrentWeather(
    val latitude: Double,
    val longitude: Double,
    val current: CurrentWeather,
    val current_units: CurrentWeatherUnits
)

@Serializable
data class CurrentWeather(
    val time: String,
    val temperature_2m: Double,
    val relative_humidity_2m: Int,
    val apparent_temperature: Double,
    val precipitation: Double,
    val weather_code: Int,
    val wind_speed_10m: Double
)

@Serializable
data class CurrentWeatherUnits(
    val temperature_2m: String,
    val relative_humidity_2m: String,
    val apparent_temperature: String,
    val precipitation: String,
    val weather_code: String,
    val wind_speed_10m: String
)

/**
 * Open-Meteo API Response для прогноза
 */
@Serializable
data class OpenMeteoForecast(
    val latitude: Double,
    val longitude: Double,
    val daily: DailyForecast
)

@Serializable
data class DailyForecast(
    val time: List<String>,
    val temperature_2m_max: List<Double>,
    val temperature_2m_min: List<Double>,
    val precipitation_sum: List<Double>,
    val weather_code: List<Int>
)

/**
 * Geocoding API Response для поиска координат города
 */
@Serializable
data class GeocodingResponse(
    val results: List<GeocodingResult>? = null
)

@Serializable
data class GeocodingResult(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String,
    val admin1: String? = null
)

/**
 * Описание погоды по коду WMO
 */
fun getWeatherDescription(code: Int): String {
    return when (code) {
        0 -> "Ясно"
        1, 2, 3 -> "Переменная облачность"
        45, 48 -> "Туман"
        51, 53, 55 -> "Морось"
        61, 63, 65 -> "Дождь"
        71, 73, 75 -> "Снег"
        77 -> "Снежные зёрна"
        80, 81, 82 -> "Ливень"
        85, 86 -> "Снегопад"
        95 -> "Гроза"
        96, 99 -> "Гроза с градом"
        else -> "Неизвестно"
    }
}
