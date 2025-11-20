package dev.skorobogatov.mcp.activities

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
 * WebSocket MCP сервер для рекомендаций активностей на основе погоды
 */
class ActivitiesMCPServer(
    private val port: Int = 3001,
    private val host: String = "0.0.0.0"
) {
    private val logger = LoggerFactory.getLogger(ActivitiesMCPServer::class.java)
    private val activitiesService = ActivitiesService()

    /**
     * Запуск MCP сервера
     */
    fun start() {
        logger.info("Starting Activities MCP Server on ws://$host:$port/mcp")

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
                                name = "activities-mcp-server",
                                version = "1.0.0"
                            ),
                            options = ServerOptions(
                                capabilities = ServerCapabilities(
                                    tools = ServerCapabilities.Tools(listChanged = null)
                                )
                            )
                        )

                        // Регистрируем инструмент для получения рекомендаций активностей
                        mcpServer.addTool(
                            name = "suggest_activities",
                            description = "Предложить активности на основе погодных условий",
                            inputSchema = Tool.Input(
                                properties = buildJsonObject {
                                    putJsonObject("temperature") {
                                        put("type", "number")
                                        put("description", "Температура воздуха в градусах Цельсия")
                                    }
                                    putJsonObject("weather_code") {
                                        put("type", "number")
                                        put("description", "Код погоды по WMO стандарту (0-99)")
                                    }
                                    putJsonObject("precipitation") {
                                        put("type", "number")
                                        put("description", "Количество осадков в мм (необязательно)")
                                        put("default", 0.0)
                                    }
                                    putJsonObject("wind_speed") {
                                        put("type", "number")
                                        put("description", "Скорость ветра в км/ч (необязательно)")
                                        put("default", 0.0)
                                    }
                                    putJsonObject("humidity") {
                                        put("type", "number")
                                        put("description", "Влажность воздуха в процентах (необязательно)")
                                        put("default", 50)
                                    }
                                },
                                required = listOf("temperature", "weather_code")
                            )
                        ) { request ->
                            handleSuggestActivities(request)
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
     * Обработка запроса рекомендаций активностей
     */
    private fun handleSuggestActivities(request: CallToolRequest): CallToolResult {
        return try {
            val arguments = request.arguments as? JsonObject

            // Получаем параметры из запроса
            val temperature = arguments?.get("temperature")?.jsonPrimitive?.doubleOrNull
            val weatherCode = arguments?.get("weather_code")?.jsonPrimitive?.intOrNull
            val precipitation = arguments?.get("precipitation")?.jsonPrimitive?.doubleOrNull ?: 0.0
            val windSpeed = arguments?.get("wind_speed")?.jsonPrimitive?.doubleOrNull ?: 0.0
            val humidity = arguments?.get("humidity")?.jsonPrimitive?.intOrNull ?: 50

            // Валидация параметров
            if (temperature == null || weatherCode == null) {
                return CallToolResult(
                    content = listOf(
                        TextContent(
                            text = "Ошибка: необходимо указать temperature и weather_code"
                        )
                    ),
                    isError = true
                )
            }

            logger.info("Suggesting activities for: temp=${temperature}°C, code=$weatherCode")

            // Получаем рекомендации
            val recommendations = activitiesService.getRecommendations(
                temperature = temperature,
                weatherCode = weatherCode,
                precipitation = precipitation,
                windSpeed = windSpeed,
                humidity = humidity
            )

            // Формируем ответ
            val response = buildString {
                val condition = getWeatherConditionFromCode(weatherCode)
                appendLine("🎯 Рекомендации активностей для погоды:")
                appendLine("   Температура: ${temperature}°C")
                appendLine("   Условия: ${getWeatherConditionDescription(condition)}")
                if (precipitation > 0) {
                    appendLine("   Осадки: ${precipitation} мм")
                }
                appendLine()
                appendLine("📋 Топ-${recommendations.size} активностей:")
                appendLine()

                recommendations.forEachIndexed { index, activity ->
                    appendLine("${index + 1}. ${activity.name} (${getActivityTypeEmoji(activity.type)} ${activity.type})")
                    appendLine("   Подходящесть: ${getStars(activity.suitability)}")
                    appendLine("   ${activity.description}")
                    appendLine("   💡 ${activity.reason}")
                    appendLine()
                }
            }

            CallToolResult(
                content = listOf(
                    TextContent(text = response)
                )
            )
        } catch (e: Exception) {
            logger.error("Error handling suggest_activities: ${e.message}", e)
            CallToolResult(
                content = listOf(
                    TextContent(
                        text = "Ошибка при получении рекомендаций: ${e.message}"
                    )
                ),
                isError = true
            )
        }
    }

    /**
     * Получить описание погодного условия на русском
     */
    private fun getWeatherConditionDescription(condition: WeatherCondition): String {
        return when (condition) {
            WeatherCondition.CLEAR -> "Ясно"
            WeatherCondition.PARTLY_CLOUDY -> "Переменная облачность"
            WeatherCondition.CLOUDY -> "Облачно"
            WeatherCondition.RAIN -> "Дождь"
            WeatherCondition.SNOW -> "Снег"
            WeatherCondition.STORM -> "Гроза"
            WeatherCondition.FOG -> "Туман"
        }
    }

    /**
     * Получить emoji для типа активности
     */
    private fun getActivityTypeEmoji(type: String): String {
        return when (type) {
            "OUTDOOR" -> "🌳"
            "INDOOR" -> "🏠"
            "SPORT" -> "⚽"
            "CULTURAL" -> "🎭"
            "RELAXATION" -> "😌"
            else -> "📍"
        }
    }

    /**
     * Получить звездочки для рейтинга
     */
    private fun getStars(suitability: Int): String {
        val stars = suitability / 2 // 10 -> 5 звезд
        return "⭐".repeat(stars.coerceIn(0, 5))
    }
}

/**
 * Main function для запуска Activities MCP сервера
 */
fun main() {
    val server = ActivitiesMCPServer(port = 3001)
    server.start()
}
