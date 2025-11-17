package dev.skorobogatov.mcp.weather

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.seconds

/**
 * WebSocket MCP сервер для получения информации о погоде
 */
class WeatherMCPServer(
    private val port: Int = 3000,
    private val host: String = "0.0.0.0"
) {
    private val logger = LoggerFactory.getLogger(WeatherMCPServer::class.java)
    private val weatherService: WeatherService
    private val httpClient: HttpClient

    init {
        // Создаем HTTP клиент для WeatherService
        httpClient = HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }
        weatherService = WeatherService(httpClient)
    }

    /**
     * Запуск MCP сервера
     */
    fun start() {
        logger.info("Starting Weather MCP Server on ws://$host:$port/mcp")

        embeddedServer(Netty, port = port, host = host) {
            install(WebSockets) {
                pingPeriod = 15.seconds
                timeout = 15.seconds
                maxFrameSize = Long.MAX_VALUE
                masking = false
            }

            routing {
                webSocket("/mcp") {
                    logger.info("New MCP client connected")

                    try {
                        // Создаем MCP сервер
                        val mcpServer = Server(
                            serverInfo = Implementation(
                                name = "weather-mcp-server",
                                version = "1.0.0"
                            ),
                            options = ServerOptions(
                                capabilities = ServerCapabilities(
                                    tools = ServerCapabilities.Tools(listChanged = null)
                                )
                            )
                        )

                        // Регистрируем инструмент для получения текущей погоды
                        mcpServer.addTool(
                            name = "get_weather",
                            description = "Получить текущую погоду для указанного города",
                            inputSchema = Tool.Input(
                                properties = buildJsonObject {
                                    putJsonObject("city") {
                                        put("type", "string")
                                        put("description", "Название города (на русском или английском)")
                                    }
                                },
                                required = listOf("city")
                            )
                        ) { request ->
                            handleGetWeather(request)
                        }

                        // Регистрируем инструмент для получения прогноза
                        mcpServer.addTool(
                            name = "get_forecast",
                            description = "Получить прогноз погоды на несколько дней для указанного города",
                            inputSchema = Tool.Input(
                                properties = buildJsonObject {
                                    putJsonObject("city") {
                                        put("type", "string")
                                        put("description", "Название города (на русском или английском)")
                                    }
                                    putJsonObject("days") {
                                        put("type", "number")
                                        put("description", "Количество дней прогноза (1-16)")
                                        put("minimum", 1)
                                        put("maximum", 16)
                                        put("default", 7)
                                    }
                                },
                                required = listOf("city")
                            )
                        ) { request ->
                            handleGetForecast(request)
                        }

                        // Создаем WebSocket transport
                        var messageHandler: (suspend (JSONRPCMessage) -> Unit)? = null

                        val transport = object : Transport {
                            override suspend fun start() {
                                // Already started via WebSocket connection
                            }

                            override suspend fun send(message: JSONRPCMessage) {
                                val json = Json {
                                    ignoreUnknownKeys = true
                                    encodeDefaults = true
                                }
                                val messageText = json.encodeToString(JSONRPCMessage.serializer(), message)
                                send(Frame.Text(messageText))
                            }

                            override suspend fun close() {
                                this@webSocket.close(CloseReason(CloseReason.Codes.NORMAL, "Connection closed"))
                            }

                            override fun onClose(block: () -> Unit) {
                                // Handle close callbacks
                            }

                            override fun onError(block: (Throwable) -> Unit) {
                                // Handle error callbacks
                            }

                            override fun onMessage(block: suspend (JSONRPCMessage) -> Unit) {
                                // Save the message handler callback
                                messageHandler = block
                            }
                        }

                        // Подключаем MCP сервер к transport
                        mcpServer.connect(transport)

                        // Обрабатываем входящие сообщения
                        for (frame in incoming) {
                            when (frame) {
                                is Frame.Text -> {
                                    val messageText = frame.readText()
                                    logger.debug("Received message: $messageText")

                                    try {
                                        val json = Json { ignoreUnknownKeys = true }
                                        val message = json.decodeFromString(JSONRPCMessage.serializer(), messageText)
                                        // Передаем сообщение в MCP сервер через callback
                                        messageHandler?.invoke(message)
                                    } catch (e: Exception) {
                                        logger.error("Error parsing message: ${e.message}", e)
                                    }
                                }
                                else -> {
                                    logger.debug("Received non-text frame: ${frame.frameType}")
                                }
                            }
                        }
                    } catch (e: ClosedReceiveChannelException) {
                        logger.info("MCP client disconnected")
                    } catch (e: Exception) {
                        logger.error("Error in MCP WebSocket handler: ${e.message}", e)
                    }
                }
            }
        }.start(wait = true)
    }

    /**
     * Обработка запроса текущей погоды
     */
    private suspend fun handleGetWeather(request: CallToolRequest): CallToolResult {
        return try {
            val arguments = request.arguments as? JsonObject
            val city = arguments?.get("city")?.jsonPrimitive?.content ?: ""

            if (city.isBlank()) {
                return CallToolResult(
                    content = listOf(
                        TextContent(
                            text = "Ошибка: не указано название города"
                        )
                    ),
                    isError = true
                )
            }

            logger.info("Getting weather for city: $city")

            val result = weatherService.getCurrentWeatherForCity(city)
            if (result == null) {
                return CallToolResult(
                    content = listOf(
                        TextContent(
                            text = "Город '$city' не найден или произошла ошибка при получении данных"
                        )
                    ),
                    isError = true
                )
            }

            val (location, weather) = result
            val weatherDesc = getWeatherDescription(weather.current.weather_code)

            val response = buildString {
                appendLine("🌍 Погода в городе ${location.name}, ${location.country}")
                appendLine()
                appendLine("🌡️ Температура: ${weather.current.temperature_2m}${weather.current_units.temperature_2m}")
                appendLine("🤚 Ощущается как: ${weather.current.apparent_temperature}${weather.current_units.apparent_temperature}")
                appendLine("☁️ Условия: $weatherDesc")
                appendLine("💧 Влажность: ${weather.current.relative_humidity_2m}${weather.current_units.relative_humidity_2m}")
                appendLine("🌧️ Осадки: ${weather.current.precipitation}${weather.current_units.precipitation}")
                appendLine("💨 Скорость ветра: ${weather.current.wind_speed_10m}${weather.current_units.wind_speed_10m}")
                appendLine()
                appendLine("📅 Время: ${weather.current.time}")
            }

            CallToolResult(
                content = listOf(
                    TextContent(text = response)
                )
            )
        } catch (e: Exception) {
            logger.error("Error handling get_weather: ${e.message}", e)
            CallToolResult(
                content = listOf(
                    TextContent(
                        text = "Ошибка при получении погоды: ${e.message}"
                    )
                ),
                isError = true
            )
        }
    }

    /**
     * Обработка запроса прогноза погоды
     */
    private suspend fun handleGetForecast(request: CallToolRequest): CallToolResult {
        return try {
            val arguments = request.arguments as? JsonObject
            val city = arguments?.get("city")?.jsonPrimitive?.content ?: ""
            val days = arguments?.get("days")?.jsonPrimitive?.intOrNull ?: 7

            if (city.isBlank()) {
                return CallToolResult(
                    content = listOf(
                        TextContent(
                            text = "Ошибка: не указано название города"
                        )
                    ),
                    isError = true
                )
            }

            if (days < 1 || days > 16) {
                return CallToolResult(
                    content = listOf(
                        TextContent(
                            text = "Ошибка: количество дней должно быть от 1 до 16"
                        )
                    ),
                    isError = true
                )
            }

            logger.info("Getting $days-day forecast for city: $city")

            val result = weatherService.getForecastForCity(city, days)
            if (result == null) {
                return CallToolResult(
                    content = listOf(
                        TextContent(
                            text = "Город '$city' не найден или произошла ошибка при получении данных"
                        )
                    ),
                    isError = true
                )
            }

            val (location, forecast) = result

            val response = buildString {
                appendLine("🌍 Прогноз погоды для города ${location.name}, ${location.country}")
                appendLine()

                forecast.daily.time.forEachIndexed { index, date ->
                    val weatherCode = forecast.daily.weather_code[index]
                    val weatherDesc = getWeatherDescription(weatherCode)
                    val tempMax = forecast.daily.temperature_2m_max[index]
                    val tempMin = forecast.daily.temperature_2m_min[index]
                    val precipitation = forecast.daily.precipitation_sum[index]

                    appendLine("📅 $date")
                    appendLine("   ☁️ $weatherDesc")
                    appendLine("   🌡️ Макс: ${tempMax}°C, Мин: ${tempMin}°C")
                    appendLine("   🌧️ Осадки: ${precipitation} мм")
                    appendLine()
                }
            }

            CallToolResult(
                content = listOf(
                    TextContent(text = response)
                )
            )
        } catch (e: Exception) {
            logger.error("Error handling get_forecast: ${e.message}", e)
            CallToolResult(
                content = listOf(
                    TextContent(
                        text = "Ошибка при получении прогноза: ${e.message}"
                    )
                ),
                isError = true
            )
        }
    }
}

/**
 * Main function для запуска Weather MCP сервера
 */
fun main() {
    val server = WeatherMCPServer(port = 3000)
    server.start()
}
