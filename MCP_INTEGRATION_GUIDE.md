# 🌤️ Гайд по интеграции Weather MCP + Claude AI

## 📋 Что это такое?

Этот проект демонстрирует **полную интеграцию MCP (Model Context Protocol)** между:
- **Weather MCP Server** - предоставляет инструменты для получения погоды
- **Основное приложение** - REST API с Claude AI, который автоматически использует MCP инструменты
- **Claude AI** - принимает решения о том, когда нужны инструменты

## 🎯 Как это работает?

```
┌─────────────┐      ┌──────────────────┐      ┌─────────────┐      ┌────────────────┐
│   Клиент    │─────>│ REST API (8080)  │─────>│  Claude AI  │      │ Weather MCP    │
│ (curl/app)  │      │ Application.kt   │      │   (API)     │      │ Server (3000)  │
└─────────────┘      └──────────────────┘      └─────────────┘      └────────────────┘
                              │                        │                      │
                              │   1. Вопрос о погоде   │                      │
                              │───────────────────────>│                      │
                              │                        │                      │
                              │   2. Список tools      │                      │
                              │<───────────────────────│                      │
                              │                        │                      │
                              │   3. Claude решает:    │                      │
                              │      "Нужен get_weather"                     │
                              │<───────────────────────│                      │
                              │                        │                      │
                              │   4. Вызов MCP инструмента                   │
                              │──────────────────────────────────────────────>│
                              │                        │                      │
                              │   5. Данные о погоде   │                      │
                              │<──────────────────────────────────────────────│
                              │                        │                      │
                              │   6. Отправка данных   │                      │
                              │───────────────────────>│                      │
                              │                        │                      │
                              │   7. Финальный ответ   │                      │
                              │<───────────────────────│                      │
     Получаем ответ          │                        │                      │
         с погодой!          │                        │                      │
```

## 🚀 Быстрый старт

### Предварительные требования

```bash
# Установите переменную окружения с API ключом Anthropic
export ANTHROPIC_API_KEY="your-anthropic-api-key"
```

### Шаг 1: Соберите проект

```bash
./gradlew build
```

### Шаг 2: Запустите Weather MCP Server (Терминал 1)

```bash
./run-weather-server.sh
```

Вы увидите:
```
🌤️  Запуск Weather MCP Server...
📡 Сервер будет доступен на: ws://localhost:3000/mcp

Доступные инструменты:
  - get_weather (city) - текущая погода
  - get_forecast (city, days) - прогноз погоды

Starting Weather MCP Server on ws://0.0.0.0:3000/mcp
```

### Шаг 3: Запустите основное приложение (Терминал 2)

```bash
./run-main-with-mcp.sh
```

Вы увидите:
```
🚀 Запуск основного приложения с MCP интеграцией...

✅ Конфигурация:
   - REST API: http://localhost:8080
   - MCP Server: ws://localhost:3000/mcp

MCP server URL configured: ws://localhost:3000/mcp (transport: websocket)
Connecting to MCP server: ws://localhost:3000/mcp
Successfully connected to MCP server
Available MCP tools: get_weather, get_forecast
```

### Шаг 4: Протестируйте (Терминал 3)

```bash
./test-weather-integration.sh
```

Или вручную:

```bash
# Вопрос о погоде - Claude автоматически использует MCP инструмент
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Какая сейчас погода в Москве?",
    "systemPrompt": "Отвечай кратко на русском языке"
  }'
```

## 📝 Примеры использования

### Пример 1: Текущая погода

**Запрос:**
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Какая погода в Санкт-Петербурге?"}'
```

**Что происходит под капотом:**
1. REST API отправляет вопрос в Claude API вместе со списком инструментов `[get_weather, get_forecast]`
2. Claude анализирует: "Это вопрос о текущей погоде в Санкт-Петербурге"
3. Claude возвращает: `tool_use: get_weather, arguments: {city: "Saint Petersburg"}`
4. REST API вызывает Weather MCP Server: `mcpService.callTool("get_weather", {city: "Saint Petersburg"})`
5. Weather MCP Server обращается к Open-Meteo API и получает реальные данные
6. Данные возвращаются в REST API
7. REST API отправляет данные обратно в Claude
8. Claude формирует красивый ответ на русском языке

**Ответ:**
```json
{
  "response": "В Санкт-Петербурге сейчас +3°C, облачно с прояснениями. Влажность 78%, скорость ветра 12 км/ч.",
  "sessionId": "uuid...",
  "model": "claude-sonnet-4-20250514",
  "inputTokens": 245,
  "outputTokens": 87,
  "totalTokens": 332,
  "responseTimeMs": 2341
}
```

### Пример 2: Прогноз погоды

**Запрос:**
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Дай прогноз погоды в Москве на 5 дней"}'
```

**Что происходит:**
1. Claude определяет: нужен инструмент `get_forecast`
2. Claude извлекает параметры: `city="Moscow", days=5`
3. REST API вызывает MCP: `callTool("get_forecast", {city: "Moscow", days: 5})`
4. Weather MCP Server возвращает прогноз на 5 дней
5. Claude формирует структурированный ответ

### Пример 3: Обычный вопрос (без MCP)

**Запрос:**
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Что такое Kotlin?"}'
```

**Что происходит:**
1. Claude анализирует вопрос: "Это общий вопрос, инструменты не нужны"
2. Claude отвечает напрямую без вызова MCP инструментов
3. Вы получаете обычный текстовый ответ

## 🔍 Детали реализации

### Weather MCP Server (порт 3000)

**Файл:** `src/main/kotlin/dev/skorobogatov/mcp/weather/WeatherMCPServer.kt`

**Инструменты:**
- `get_weather` - текущая погода для города
- `get_forecast` - прогноз на N дней

**Запуск:**
```bash
./gradlew runWeatherMCP
# или
./run-weather-server.sh
```

### Основное приложение (порт 8080)

**Файл:** `src/main/kotlin/dev/skorobogatov/Application.kt`

**Ключевые компоненты:**
1. **MCPService** - подключение к Weather MCP Server через WebSocket
2. **ClaudeService.sendMessageWithTools()** - tool use loop с Claude API
3. **ChatRoutes** - интеграция MCP инструментов в обработку запросов

**Запуск:**
```bash
export MCP_SERVER_URL="ws://localhost:3000/mcp"
./gradlew run
# или
./run-main-with-mcp.sh
```

### Конфигурация

**application.conf:**
```hocon
mcp {
    serverUrl = ${?MCP_SERVER_URL}  # ws://localhost:3000/mcp
    transportType = "websocket"
}
```

## 🎓 Архитектурные решения

### 1. Почему Claude принимает решения о вызове инструментов?

**Преимущества:**
- ✅ Claude понимает контекст лучше любого парсера
- ✅ Claude извлекает параметры из естественного языка
- ✅ Работает с разными формулировками: "погода", "какая погодка", "сколько градусов"
- ✅ Стандартный подход в MCP экосистеме

**Альтернатива (не рекомендуется):**
```kotlin
// Плохой подход - приложение парсит вопрос
if (message.contains("погода")) {
    // Как извлечь город из "А какая нынче погодка в матушке Москве?"
    val city = ???
}
```

### 2. Tool Use Loop

**Реализация:** `ClaudeService.sendMessageWithTools()`

**Процесс:**
```kotlin
while (iterations < maxIterations) {
    // 1. Отправляем запрос с tools
    val response = claudeApi.post(request)

    // 2. Проверяем stop_reason
    if (response.stop_reason == "tool_use") {
        // 3. Вызываем MCP инструмент
        val result = onToolCall(toolName, input)

        // 4. Добавляем результат в историю
        messages.add(tool_result)

        // 5. Повторяем цикл
        continue
    } else {
        // Получили финальный ответ
        return response
    }
}
```

### 3. MCP Integration

**MCPService** (через MCP Kotlin SDK):
- Подключение через WebSocket к Weather MCP Server
- Получение списка инструментов: `listTools()`
- Вызов инструментов: `callTool(name, arguments)`

**ChatRoutes** интеграция:
```kotlin
val mcpTools = mcpService.listTools()
val claudeTools = mcpTools.map { convertToClaudeTool(it) }

claudeService.sendMessageWithTools(
    messages = history,
    tools = claudeTools,
    onToolCall = { name, input ->
        mcpService.callTool(name, input.toMap())
    }
)
```

## 🐛 Troubleshooting

### Weather MCP Server не запускается

**Проблема:** `Address already in use: bind`
**Решение:** Порт 3000 занят. Остановите другой процесс или измените порт в WeatherMCPServer.kt

### Основное приложение не подключается к MCP

**Проблема:** `Failed to connect to MCP server`
**Решение:**
1. Убедитесь что Weather MCP Server запущен
2. Проверьте `MCP_SERVER_URL=ws://localhost:3000/mcp`
3. Проверьте логи Weather MCP Server

### Claude не использует инструменты

**Проблема:** Вопрос о погоде, но MCP не вызывается
**Решение:**
1. Проверьте что MCP подключен: смотрите логи при старте
2. Убедитесь что вопрос действительно о погоде
3. Проверьте systemPrompt - не блокирует ли он использование инструментов

## 📚 Дополнительные ресурсы

- [Model Context Protocol Documentation](https://modelcontextprotocol.io/)
- [MCP Kotlin SDK](https://github.com/modelcontextprotocol/kotlin-sdk)
- [Claude API Tool Use Guide](https://docs.anthropic.com/claude/docs/tool-use)
- [Open-Meteo API](https://open-meteo.com/) - источник данных о погоде

## 🎉 Готово!

Теперь у вас есть полностью работающая система с:
- ✅ Weather MCP Server предоставляющий инструменты
- ✅ Claude AI автоматически использующий инструменты
- ✅ REST API для взаимодействия с пользователями
- ✅ Полная интеграция через MCP протокол

**Следующие шаги:**
- Добавьте больше инструментов в Weather MCP Server
- Создайте другие MCP серверы (база данных, файлы, и т.д.)
- Интегрируйте с фронтендом
- Добавьте аутентификацию и authorization
