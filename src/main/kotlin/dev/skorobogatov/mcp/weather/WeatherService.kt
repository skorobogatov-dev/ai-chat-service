package dev.skorobogatov.mcp.weather

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Сервис для работы с Open-Meteo API
 */
class WeatherService(private val httpClient: HttpClient) {
    private val logger = LoggerFactory.getLogger(WeatherService::class.java)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    companion object {
        private const val GEOCODING_API = "https://geocoding-api.open-meteo.com/v1/search"
        private const val WEATHER_API = "https://api.open-meteo.com/v1/forecast"
    }

    /**
     * Поиск координат города по названию
     */
    suspend fun searchCity(cityName: String): GeocodingResult? {
        return try {
            logger.info("Searching for city: $cityName")

            val response: HttpResponse = httpClient.get(GEOCODING_API) {
                parameter("name", cityName)
                parameter("count", 1)
                parameter("language", "ru")
                parameter("format", "json")
            }

            if (!response.status.isSuccess()) {
                logger.error("Geocoding API returned status: ${response.status}")
                return null
            }

            val geocodingResponse = json.decodeFromString<GeocodingResponse>(response.bodyAsText())
            val result = geocodingResponse.results?.firstOrNull()

            if (result != null) {
                logger.info("Found city: ${result.name}, ${result.country} at ${result.latitude},${result.longitude}")
            } else {
                logger.warn("City not found: $cityName")
            }

            result
        } catch (e: Exception) {
            logger.error("Error searching city: ${e.message}", e)
            null
        }
    }

    /**
     * Получение текущей погоды для координат
     */
    suspend fun getCurrentWeather(latitude: Double, longitude: Double): OpenMeteoCurrentWeather? {
        return try {
            logger.info("Getting current weather for: $latitude,$longitude")

            val response: HttpResponse = httpClient.get(WEATHER_API) {
                parameter("latitude", latitude)
                parameter("longitude", longitude)
                parameter("current", "temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m")
                parameter("timezone", "auto")
            }

            if (!response.status.isSuccess()) {
                logger.error("Weather API returned status: ${response.status}")
                return null
            }

            val weatherResponse = json.decodeFromString<OpenMeteoCurrentWeather>(response.bodyAsText())
            logger.info("Current weather retrieved: ${weatherResponse.current.temperature_2m}°C")

            weatherResponse
        } catch (e: Exception) {
            logger.error("Error getting current weather: ${e.message}", e)
            null
        }
    }

    /**
     * Получение прогноза погоды на несколько дней
     */
    suspend fun getForecast(latitude: Double, longitude: Double, days: Int = 7): OpenMeteoForecast? {
        return try {
            logger.info("Getting $days-day forecast for: $latitude,$longitude")

            val response: HttpResponse = httpClient.get(WEATHER_API) {
                parameter("latitude", latitude)
                parameter("longitude", longitude)
                parameter("daily", "temperature_2m_max,temperature_2m_min,precipitation_sum,weather_code")
                parameter("forecast_days", days)
                parameter("timezone", "auto")
            }

            if (!response.status.isSuccess()) {
                logger.error("Forecast API returned status: ${response.status}")
                return null
            }

            val forecastResponse = json.decodeFromString<OpenMeteoForecast>(response.bodyAsText())
            logger.info("Forecast retrieved for ${forecastResponse.daily.time.size} days")

            forecastResponse
        } catch (e: Exception) {
            logger.error("Error getting forecast: ${e.message}", e)
            null
        }
    }

    /**
     * Получение текущей погоды для города по названию
     */
    suspend fun getCurrentWeatherForCity(cityName: String): Pair<GeocodingResult, OpenMeteoCurrentWeather>? {
        val location = searchCity(cityName) ?: return null
        val weather = getCurrentWeather(location.latitude, location.longitude) ?: return null
        return Pair(location, weather)
    }

    /**
     * Получение прогноза для города по названию
     */
    suspend fun getForecastForCity(cityName: String, days: Int = 7): Pair<GeocodingResult, OpenMeteoForecast>? {
        val location = searchCity(cityName) ?: return null
        val forecast = getForecast(location.latitude, location.longitude, days) ?: return null
        return Pair(location, forecast)
    }
}
