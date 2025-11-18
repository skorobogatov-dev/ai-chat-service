# 🏗️ Архитектура MCP Weather Integration

## 📊 Общая схема системы

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          ПОЛЬЗОВАТЕЛЬ                                    │
│                    (curl, Postman, Frontend)                             │
└─────────────────────────┬───────────────────────────────────────────────┘
                          │
                          │ HTTP POST /api/chat
                          │ {"message": "Какая погода в Москве?"}
                          ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                    ОСНОВНОЕ ПРИЛОЖЕНИЕ (Port 8080)                       │
│                         Application.kt                                   │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                           │
│  ┌─────────────┐     ┌──────────────┐     ┌────────────────────┐       │
│  │ ChatRoutes  │────>│ ClaudeService│────>│ ConversationHistory│       │
│  │             │     │              │     │     Service        │       │
│  └─────┬───────┘     └──────┬───────┘     └────────────────────┘       │
│        │                    │                                            │
│        │                    │                                            │
│        ▼                    ▼                                            │
│  ┌──────────────────────────────────┐                                   │
│  │        MCPService                │                                   │
│  │  ┌────────────────────────────┐  │                                   │
│  │  │ MCP Client (Kotlin SDK)    │  │                                   │
│  │  │ - listTools()              │  │                                   │
│  │  │ - callTool(name, args)     │  │                                   │
│  │  └────────────────────────────┘  │                                   │
│  └───────────────┬──────────────────┘                                   │
│                  │                                                        │
└──────────────────┼────────────────────────────────────────────────────────┘
                   │
                   │ WebSocket (ws://localhost:3000/mcp)
                   │ MCP Protocol (JSON-RPC 2.0)
                   ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                  WEATHER MCP SERVER (Port 3000)                          │
│                    WeatherMCPServer.kt                                   │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                           │
│  ┌───────────────────────────────────────────────────────────────┐     │
│  │              MCP Server (MCP Kotlin SDK)                      │     │
│  │                                                                │     │
│  │  Tools:                                                        │     │
│  │  ┌─────────────────────────────────────────────────────────┐ │     │
│  │  │ get_weather(city: string)                               │ │     │
│  │  │ - Получить текущую погоду для города                   │ │     │
│  │  │ - Input: {"city": "Moscow"}                             │ │     │
│  │  │ - Output: температура, влажность, ветер, осадки        │ │     │
│  │  └─────────────────────────────────────────────────────────┘ │     │
│  │                                                                │     │
│  │  ┌─────────────────────────────────────────────────────────┐ │     │
│  │  │ get_forecast(city: string, days: number)                │ │     │
│  │  │ - Получить прогноз погоды на N дней                    │ │     │
│  │  │ - Input: {"city": "Moscow", "days": 7}                 │ │     │
│  │  │ - Output: прогноз по дням с температурой и осадками    │ │     │
│  │  └─────────────────────────────────────────────────────────┘ │     │
│  └───────────────────────────┬───────────────────────────────────┘     │
│                              │                                          │
│                              ▼                                          │
│                    ┌──────────────────┐                                │
│                    │  WeatherService  │                                │
│                    └────────┬─────────┘                                │
└─────────────────────────────┼──────────────────────────────────────────┘
                              │
                              │ HTTP GET
                              │ https://api.open-meteo.com/v1/forecast
                              ▼
                    ┌──────────────────────┐
                    │  Open-Meteo API      │
                    │  (External Service)  │
                    │  - Реальные данные   │
                    │  - Бесплатный API    │
                    └──────────────────────┘
                              │
                              │ HTTP GET
                              │ https://geocoding-api.open-meteo.com
                              ▼
                    ┌──────────────────────┐
                    │ Geocoding API        │
                    │ (External Service)   │
                    │ - Координаты города  │
                    └──────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│                      CLAUDE API (External)                               │
│                   https://api.anthropic.com                              │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                           │
│  Получает:                                                               │
│  - Сообщение пользователя: "Какая погода в Москве?"                    │
│  - Список инструментов: [get_weather, get_forecast]                    │
│  - История диалога                                                       │
│                                                                           │
│  Анализирует и решает:                                                  │
│  - Это вопрос о текущей погоде                                          │
│  - Нужен инструмент: get_weather                                        │
│  - Параметры: {city: "Moscow"}                                          │
│                                                                           │
│  Возвращает:                                                            │
│  - stop_reason: "tool_use"                                               │
│  - tool_use: {name: "get_weather", input: {city: "Moscow"}}            │
│                                                                           │
└─────────────────────────────────────────────────────────────────────────┘
```

## 🔄 Поток данных при запросе о погоде

```
┌────────┐                                                      ┌─────────┐
│  User  │                                                      │ Claude  │
└───┬────┘                                                      └────┬────┘
    │                                                                │
    │ 1. "Какая погода в Москве?"                                  │
    │────────────────────────────────────>                          │
    │                                   REST API                    │
    │                                                                │
    │              2. POST /v1/messages                             │
    │              {                                                │
    │                "messages": [...],                             │
    │                "tools": [                                     │
    │                  {                                            │
    │                    "name": "get_weather",                     │
    │                    "description": "...",                      │
    │                    "input_schema": {...}                      │
    │                  }                                            │
    │                ]                                              │
    │              }                                                │
    │              ─────────────────────────────────────────────>  │
    │                                                                │
    │              3. Response                                      │
    │              {                                                │
    │                "stop_reason": "tool_use",                     │
    │                "content": [                                   │
    │                  {                                            │
    │                    "type": "tool_use",                        │
    │                    "name": "get_weather",                     │
    │                    "input": {"city": "Moscow"}                │
    │                  }                                            │
    │                ]                                              │
    │              }                                                │
    │              <─────────────────────────────────────────────  │
    │                                                                │
    │              4. Call MCP Tool                                │
    │              mcpService.callTool("get_weather", ...)          │
    │              ─────────────────────────────>                   │
    │                                          Weather MCP Server   │
    │                                                                │
    │              5. Weather data                                  │
    │              "🌍 Погода в Москве: +5°C..."                   │
    │              <─────────────────────────────                   │
    │                                                                │
    │              6. POST /v1/messages (with tool result)          │
    │              {                                                │
    │                "messages": [                                  │
    │                  ...,                                         │
    │                  {                                            │
    │                    "role": "user",                            │
    │                    "content": [                               │
    │                      {                                        │
    │                        "type": "tool_result",                 │
    │                        "tool_use_id": "...",                  │
    │                        "content": "🌍 Погода..."              │
    │                      }                                        │
    │                    ]                                          │
    │                  }                                            │
    │                ]                                              │
    │              }                                                │
    │              ─────────────────────────────────────────────>  │
    │                                                                │
    │              7. Final response                                │
    │              {                                                │
    │                "stop_reason": "end_turn",                     │
    │                "content": [                                   │
    │                  {                                            │
    │                    "type": "text",                            │
    │                    "text": "В Москве сейчас +5°C,            │
    │                             облачно с прояснениями..."        │
    │                  }                                            │
    │                ]                                              │
    │              }                                                │
    │              <─────────────────────────────────────────────  │
    │                                                                │
    │ 8. "В Москве сейчас +5°C, облачно..."                        │
    │<────────────────────────────────────                          │
    │                                                                │
```

## 🔧 Ключевые компоненты

### 1. ChatRoutes.kt
**Роль:** Обработка HTTP запросов
```kotlin
// Проверяет подключение к MCP
val connectionStatus = mcpService.getConnectionStatus()

if (connectionStatus.connected) {
    // Получает список инструментов
    val tools = mcpService.listTools()

    // Конвертирует в формат Claude
    val claudeTools = tools.map { convertToClaude(it) }

    // Отправляет с tool use loop
    claudeService.sendMessageWithTools(
        messages,
        claudeTools,
        onToolCall = { name, args ->
            mcpService.callTool(name, args)
        }
    )
}
```

### 2. ClaudeService.kt
**Роль:** Tool Use Loop с Claude API
```kotlin
suspend fun sendMessageWithTools(
    messages: List<ClaudeMessage>,
    tools: List<ClaudeTool>,
    onToolCall: suspend (String, JsonObject) -> String
): ChatResponse {
    while (iterations < maxIterations) {
        val response = claudeApi.post(request)

        if (response.stop_reason == "tool_use") {
            // Извлекаем tool_use
            val toolName = response.content.name
            val toolInput = response.content.input

            // Вызываем MCP инструмент
            val result = onToolCall(toolName, toolInput)

            // Добавляем результат в историю
            messages.add(tool_result)

            // Продолжаем цикл
            continue
        }

        return response // Финальный ответ
    }
}
```

### 3. MCPService.kt
**Роль:** MCP клиент
```kotlin
class MCPService {
    private var mcpClient: Client? = null

    suspend fun connect(serverUrl: String): Status {
        val client = Client(...)
        val transport = WebSocketClientTransport(serverUrl)
        client.connect(transport)
        mcpClient = client
    }

    suspend fun listTools(): List<Tool> {
        return mcpClient?.listTools()
    }

    suspend fun callTool(name: String, args: Map): String {
        return mcpClient?.callTool(name, args)
    }
}
```

### 4. WeatherMCPServer.kt
**Роль:** MCP сервер с инструментами
```kotlin
class WeatherMCPServer {
    fun start() {
        val mcpServer = Server(...)

        // Регистрация инструментов
        mcpServer.addTool("get_weather", ...) { request ->
            val city = request.arguments["city"]
            val weather = weatherService.getCurrentWeather(city)
            CallToolResult(content = [TextContent(weather)])
        }

        mcpServer.addTool("get_forecast", ...) { request ->
            // ...
        }
    }
}
```

## 📝 Модели данных

### Claude API Request
```json
{
  "model": "claude-sonnet-4-20250514",
  "max_tokens": 1024,
  "messages": [
    {"role": "user", "content": "Какая погода в Москве?"}
  ],
  "tools": [
    {
      "name": "get_weather",
      "description": "Получить текущую погоду",
      "input_schema": {
        "type": "object",
        "properties": {
          "city": {
            "type": "string",
            "description": "Название города"
          }
        },
        "required": ["city"]
      }
    }
  ]
}
```

### Claude API Response (Tool Use)
```json
{
  "stop_reason": "tool_use",
  "content": [
    {
      "type": "tool_use",
      "id": "toolu_01234",
      "name": "get_weather",
      "input": {
        "city": "Moscow"
      }
    }
  ]
}
```

### MCP Tool Call
```json
{
  "jsonrpc": "2.0",
  "method": "tools/call",
  "params": {
    "name": "get_weather",
    "arguments": {
      "city": "Moscow"
    }
  }
}
```

### MCP Tool Result
```json
{
  "content": [
    {
      "type": "text",
      "text": "🌍 Погода в городе Moscow, RU\n🌡️ Температура: 5.2°C\n..."
    }
  ]
}
```

## 🔐 Безопасность

1. **API Keys:** Хранятся в environment variables
2. **WebSocket:** Локальное подключение (localhost)
3. **Rate Limiting:** Контролируется на уровне Claude API
4. **Validation:** Проверка параметров в MCP Server

## 🎯 Преимущества архитектуры

✅ **Модульность** - каждый компонент независим
✅ **Расширяемость** - легко добавить новые MCP серверы
✅ **Стандартность** - использует MCP протокол
✅ **AI-driven** - Claude сам решает когда использовать инструменты
✅ **Async** - все операции асинхронные (coroutines)
✅ **Type-safe** - полная типизация на Kotlin
