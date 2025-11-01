# AI Chat Service

REST API сервис на Ktor для взаимодействия с Claude AI от Anthropic.

## Технологии

- **Kotlin** 1.9.24
- **Ktor** 2.3.12 (Server + Client)
- **Kotlinx Serialization** для работы с JSON
- **Anthropic Claude API** (claude-3-5-sonnet-20241022)

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
  "message": "Что такое Kotlin coroutines?"
}
```

**Response:**
```json
{
  "response": "Kotlin coroutines - это мощный инструмент для асинхронного программирования...",
  "model": "claude-3-5-sonnet-20241022"
}
```

**Пример с curl:**
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Привет! Расскажи про себя"}'
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
}
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

## Возможные улучшения

- [ ] Добавить Swagger/OpenAPI документацию
- [ ] Реализовать streaming ответов (Server-Sent Events)
- [ ] Добавить rate limiting
- [ ] Сохранение истории сообщений в БД
- [ ] Поддержка контекста диалога
- [ ] Аутентификация пользователей
- [ ] Метрики и мониторинг

## Лицензия

MIT
