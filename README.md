# AI Chat Service

REST API сервис на Ktor для взаимодействия с Claude AI от Anthropic.

## ✨ Новое: MCP Integration

**Теперь с полной поддержкой Model Context Protocol (MCP)!**

🌤️ **Weather MCP Server** - Claude автоматически получает данные о погоде через MCP протокол

📚 **Быстрый старт:**
- **[QUICKSTART.md](./QUICKSTART.md)** - запуск за 3 шага
- **[MCP_INTEGRATION_GUIDE.md](./MCP_INTEGRATION_GUIDE.md)** - полный гайд по MCP
- **[ARCHITECTURE.md](./ARCHITECTURE.md)** - архитектура и диаграммы

## Технологии

- **Kotlin** 2.1.0
- **Ktor** 3.0.3 (Server + Client + WebSockets)
- **Kotlinx Serialization** для работы с JSON
- **Anthropic Claude API** (claude-sonnet-4-20250514)
- **Model Context Protocol (MCP)** - интеграция с инструментами

## Требования

- JDK 11 или выше
- Anthropic API ключ

## Настройка

### 1. Получение API ключа

Зарегистрируйтесь на [Anthropic Console](https://console.anthropic.com/) и получите API ключ.

### 2. Конфигурация API ключа

**Способ 1 (рекомендуется): Локальный .env файл**

Создайте файл `.env` в корне проекта (уже в .gitignore):

```bash
# .env
ANTHROPIC_API_KEY=sk-ant-api03-...
```

Перед запуском загрузите переменные:
```bash
export $(cat .env | xargs)
./gradlew run
```

**Способ 2: Environment переменная**

```bash
export ANTHROPIC_API_KEY="sk-ant-api03-..."
./gradlew run
```

**⚠️ Важно:** Никогда не коммитьте API ключи в git! Файл `application.conf` содержит placeholder, реальный ключ должен быть в `.env` или переменной окружения.

### 3. Установка зависимостей

```bash
./gradlew build
```

## Запуск

### Development режим

```bash
./gradlew run
```

Сервер запустится на `http://localhost:8080`

### Production build

```bash
./gradlew installDist
./build/install/ai-chat-service/bin/ai-chat-service
```

## API Endpoints

### POST /api/chat

Отправка сообщения в AI чат.

**Request:**
```json
{
  "message": "Что такое Kotlin coroutines?",
  "systemPrompt": "Отвечай кратко, максимум 2 предложения" // опционально
}
```

**Response (с дефолтным промптом):**
```json
{
  "response": "{\"question\": \"Что такое Kotlin coroutines?\", \"answer\": \"Kotlin coroutines - это мощный инструмент для асинхронного программирования, который позволяет писать неблокирующий код в последовательном стиле.\", \"tags\": [\"kotlin\", \"coroutines\", \"async\", \"concurrency\"]}",
  "model": "claude-sonnet-4-20250514"
}
```

**Response (если отключить дефолтный промпт):**
```json
{
  "response": "Kotlin coroutines - это мощный инструмент для асинхронного программирования...",
  "model": "claude-sonnet-4-20250514"
}
```

**Пример с curl (базовый, использует дефолтный JSON формат):**
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Что такое Kotlin?"}'

# Ответ будет в JSON формате: {question, answer, tags}
```

**Пример с переопределением systemPrompt (текстовый ответ):**
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Расскажи про Kotlin",
    "systemPrompt": "Отвечай обычным текстом, кратко и по делу"
  }'
```

**Пример с кастомным JSON форматом:**
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Какая погода в Москве?",
    "systemPrompt": "Отвечай только в формате JSON с полями: city, temperature, condition, humidity"
  }'
```

**Пример с httpie:**
```bash
http POST localhost:8080/api/chat message="Что такое Kotlin?"
```

### GET /api/health

Проверка статуса сервиса.

**Response:**
```json
{
  "status": "UP"
}
```

### GET /

Базовый endpoint, показывает что сервис запущен.

## Структура проекта

```
src/main/kotlin/com/example/
├── Application.kt              # Entry point, конфигурация
├── models/
│   ├── ChatRequest.kt         # DTO для запроса
│   ├── ChatResponse.kt        # DTO для ответа
│   └── ClaudeApiModels.kt     # Модели Anthropic API
├── services/
│   └── ClaudeService.kt       # Сервис для работы с Claude API
├── routes/
│   └── ChatRoutes.kt          # REST endpoints
└── plugins/
    ├── Serialization.kt       # JSON сериализация
    ├── HTTP.kt                # CORS настройки
    ├── StatusPages.kt         # Обработка ошибок
    └── Routing.kt             # Routing конфигурация
```

## Конфигурация

Все настройки находятся в `src/main/resources/application.conf`:

```hocon
ktor {
    deployment {
        port = 8080                # Порт сервера
    }
}

claude {
    apiKey = "..."                 # API ключ Anthropic
    apiUrl = "..."                 # URL Anthropic API
    model = "claude-3-5-sonnet-20241022"  # Модель Claude
    maxTokens = 1024              # Максимум токенов в ответе
    systemPrompt = "..."          # Системный промпт по умолчанию (опционально)
}
```

### Системный промпт

Сервис поддерживает системные промпты для форматирования ответов Claude.

**Дефолтный промпт:**

По умолчанию все ответы возвращаются в формате JSON:
```json
{
  "question": "вопрос пользователя",
  "answer": "ответ Claude",
  "tags": ["тег1", "тег2", "тег3"]
}
```

**1. Глобальный промпт (через конфигурацию)**

Переопределяет дефолтный промпт для всех запросов:

```bash
export CLAUDE_SYSTEM_PROMPT="Отвечай всегда кратко, максимум 50 слов"
./gradlew run
```

**2. Промпт в запросе (переопределяет глобальный и дефолтный)**

Передается в каждом запросе через поле `systemPrompt`:

```json
{
  "message": "Что такое Kotlin?",
  "systemPrompt": "Отвечай кратко, максимум 100 слов"
}
```

**Приоритет:** Промпт из запроса → Глобальный промпт (CLAUDE_SYSTEM_PROMPT) → Дефолтный промпт (JSON формат)

**Примеры использования:**

- Форматирование в JSON:
  ```json
  {"systemPrompt": "Отвечай только в формате JSON: {answer: string, tags: string[]}"}
  ```

- Краткие ответы:
  ```json
  {"systemPrompt": "Отвечай максимально кратко, одним предложением"}
  ```

- Специфический стиль:
  ```json
  {"systemPrompt": "Ты - эксперт по Kotlin. Отвечай технически точно с примерами кода"}
  ```

## Разработка

### Запуск тестов

```bash
./gradlew test
```

### Форматирование кода

```bash
./gradlew ktlintFormat
```

## 🌤️ MCP Integration - Weather Tools

### Быстрый запуск MCP

**Терминал 1 - Weather MCP Server:**
```bash
./run-weather-server.sh
```

**Терминал 2 - Основное приложение:**
```bash
export ANTHROPIC_API_KEY="your-key"
./run-main-with-mcp.sh
```

**Терминал 3 - Тест:**
```bash
./test-weather-integration.sh

# Или вручную:
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Какая погода в Москве?"}'
```

### Как это работает

```
Вы: "Какая погода в Москве?"
  ↓
REST API → Claude API (с tools от Weather MCP Server)
  ↓
Claude: "Нужен инструмент get_weather для Moscow"
  ↓
REST API → Weather MCP Server → Open-Meteo API
  ↓
Данные о погоде → Claude → Красивый ответ
  ↓
Вы: "В Москве +5°C, облачно..."
```

### Доступные MCP инструменты

- `get_weather(city)` - текущая погода
- `get_forecast(city, days)` - прогноз на N дней

### Документация MCP

- **[QUICKSTART.md](./QUICKSTART.md)** - запуск за 3 шага
- **[MCP_INTEGRATION_GUIDE.md](./MCP_INTEGRATION_GUIDE.md)** - полный гайд
- **[ARCHITECTURE.md](./ARCHITECTURE.md)** - архитектура системы

## Возможные улучшения

- [x] Поддержка контекста диалога (сделано с history compression)
- [x] Сохранение истории сообщений (персистентное хранилище в JSON)
- [x] Model Context Protocol (MCP) интеграция
- [ ] Добавить Swagger/OpenAPI документацию
- [ ] Реализовать streaming ответов (Server-Sent Events)
- [ ] Добавить rate limiting
- [ ] Сохранение истории в БД (сейчас JSON файлы)
- [ ] Аутентификация пользователей
- [ ] Метрики и мониторинг

## Лицензия

MIT
