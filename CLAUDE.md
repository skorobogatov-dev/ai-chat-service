# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

AI Chat Service - REST API сервис на Ktor для взаимодействия с Claude AI через Anthropic API.

## Build and Development Commands

### Build and Run
```bash
./gradlew build           # Сборка проекта
./gradlew run             # Запуск в dev режиме
./gradlew test            # Запуск тестов
./gradlew installDist     # Production build
```

### Configuration
- Установить ANTHROPIC_API_KEY через environment или в `src/main/resources/application.conf`
- Сервер запускается на порту 8080 (настраивается в application.conf)
- По умолчанию включен системный промпт для JSON ответов (можно переопределить через CLAUDE_SYSTEM_PROMPT)
- JSON формат: `{"question": "...", "answer": "...", "tags": [...]}` - в ответе пользователю выводится только поле `answer`

### Testing API
```bash
# Базовый запрос (создает новую сессию)
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Привет!"}'

# Ответ: {"response": "...", "sessionId": "uuid", "model": "claude-sonnet-4-20250514", ...}

# Продолжение диалога (используем sessionId из предыдущего ответа)
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Расскажи подробнее",
    "sessionId": "полученный-uuid-из-предыдущего-ответа"
  }'

# С переопределением системного промпта
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Что такое Kotlin?", "systemPrompt": "Отвечай обычным текстом"}'

# С выбором конкретной модели (быстрая и доступная Haiku)
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Простой вопрос", "model": "claude-3-haiku-20240307"}'

# С выбором самой продвинутой модели (Sonnet 4 для сложных задач)
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Сложная задача", "model": "claude-sonnet-4-20250514"}'

# С переопределением модели и системного промпта одновременно
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Explain quantum computing",
    "model": "claude-sonnet-4-20250514",
    "systemPrompt": "Answer in Russian, be concise"
  }'
```

### Механизм истории диалога

Сервис автоматически сохраняет историю диалогов и использует механизм **сжатия истории**:

- **Автоматическое сжатие**: Каждые 3 пары сообщений (user + assistant) автоматически заменяются на summary
- **Управление сессиями**:
  - Если `sessionId` не указан в запросе - создается новая сессия
  - Если `sessionId` указан - продолжается существующий диалог
  - `sessionId` возвращается в каждом ответе для продолжения диалога
- **Флаг сжатия**: В ответе присутствует поле `historyCompressed` (true/false), показывающее, была ли сжата история в этом запросе
- **Персистентное хранилище**:
  - История автоматически сохраняется в JSON файлы в директории `chat_sessions/`
  - Каждый диалог хранится в отдельном файле: `{sessionId}.json`
  - При перезапуске сервера все диалоги автоматически загружаются из файлов
  - Можно продолжить беседу после перезапуска сервера
- **Автоматическая генерация названий**:
  - При создании нового диалога автоматически генерируется название по первому сообщению пользователя
  - Название создается с помощью Claude AI (краткое, 5-7 слов)
  - Названия сохраняются вместе с историей диалога

### Доступные модели Claude (от слабых к сильным)

1. **claude-3-haiku-20240307** - Самая быстрая и доступная модель, идеальна для простых задач
2. **claude-sonnet-4-20250514** - Самая продвинутая модель Claude 4 поколения (рекомендуется по умолчанию)

Модель по умолчанию настраивается в `application.conf`. Если не указано иное, используется модель из конфигурации.

## Architecture Overview

### Request Flow
1. HTTP запрос → ChatRoutes (routes/)
2. ChatRoutes → ConversationHistoryService (получить/создать сессию)
3. ChatRoutes → ConversationHistoryService (добавить сообщение пользователя) → FileStorageService (автосохранение)
4. ChatRoutes → ConversationHistoryService (проверка необходимости сжатия)
5. Если нужно сжатие → ClaudeService.createSummary() → ConversationHistoryService.compressHistory() → FileStorageService
6. ChatRoutes → ClaudeService.sendMessage() с полной историей
7. ClaudeService → Anthropic API (через Ktor HTTP Client)
8. Ответ от Claude → ConversationHistoryService (добавить ответ ассистента) → FileStorageService (автосохранение)
9. Если новая сессия → ClaudeService.generateConversationTitle() → ConversationHistoryService.setConversationTitle() → FileStorageService
10. ChatResponse DTO (с sessionId) → JSON → клиент

### Key Components

**Application.kt** - Entry point, инициализирует:
- HTTP Client (CIO engine) для запросов к Anthropic
- ClaudeService с конфигурацией из application.conf
- FileStorageService (директория: `chat_sessions/`)
- ConversationHistoryService (порог сжатия = 3 пары сообщений, автосохранение через FileStorageService)
- Plugins (Serialization, CORS, StatusPages, Routing)

**ClaudeService** - Инкапсулирует логику работы с Anthropic API:
- Формирует запросы в формате Claude Messages API с поддержкой истории
- Поддерживает динамический выбор модели (дефолтная из конфигурации → переопределение в запросе)
- Поддерживает системные промпты (встроенный JSON формат → env переменная → переопределение в запросе)
- Дефолтный промпт: возвращает все ответы в JSON формате {question, answer, tags}
- Парсит JSON ответ и извлекает только поле `answer` для пользователя (с fallback на полный текст при ошибке парсинга)
- Измеряет время выполнения запросов и собирает статистику по токенам
- Обрабатывает ошибки и логирует usage statistics
- Возвращает упрощенные ChatResponse объекты с информацией об использованной модели, токенах и времени ответа
- **createSummary()** - создает краткое резюме истории для сжатия
- **generateConversationTitle()** - генерирует название диалога на основе первого сообщения пользователя

**FileStorageService** - Персистентное хранилище диалогов:
- Сохранение/загрузка диалогов в/из JSON файлов
- Каждый диалог в отдельном файле: `{sessionId}.json`
- Автоматическая загрузка всех диалогов при инициализации
- Поддержка операций: save, load, loadAll, delete, exists, getAllSessionIds
- Thread-safe операции с файловой системой

**ConversationHistoryService** - Управление историями диалогов:
- Hybrid хранилище: in-memory (ConcurrentHashMap для thread-safety) + персистентное (через FileStorageService)
- Автоматическая загрузка существующих диалогов из файлов при старте
- Создание и получение сессий по sessionId
- Добавление сообщений пользователя и ассистента с автосохранением
- Установка названия диалога с автосохранением
- Проверка необходимости сжатия (порог = 3 пары по умолчанию)
- Автоматическое сжатие истории: каждые N пар → summary с автосохранением
- Конвертация истории в формат Claude Messages API
- Очистка старых сессий (cleanup по таймауту)

**Plugins** - Модульная конфигурация Ktor:
- Serialization: kotlinx.serialization для JSON
- HTTP: CORS для кросс-доменных запросов
- StatusPages: глобальная обработка исключений
- Routing: регистрация всех routes

### Data Models
- `ChatRequest/ChatResponse` - публичные API DTOs
  - `ChatRequest` содержит `message`, опциональный `sessionId`, опциональный `systemPrompt` и опциональный `model`
  - `ChatResponse` содержит `response`, `sessionId`, `model`, `inputTokens`, `outputTokens`, `totalTokens`, `responseTimeMs`, `historyCompressed`
  - `sessionId` используется для продолжения диалога
  - `model` позволяет выбрать конкретную модель Claude для запроса
  - `historyCompressed` показывает, была ли сжата история в этом запросе
- `ConversationHistory.kt` - модели для хранения истории
  - `ConversationHistory` - история одного диалога с sessionId, title и списком сообщений (Serializable для JSON)
  - `HistoryMessage` - одно сообщение с типом (USER/ASSISTANT/SUMMARY) и контентом (Serializable)
  - `MessageType` - enum типов сообщений (Serializable)
  - Все модели сериализуемы для сохранения в JSON файлы
- `ClaudeModels.kt` - enum с доступными моделями Claude (Haiku, Sonnet 4)
- `ClaudeApiModels.kt` - внутренние модели Anthropic API (messages, content, usage)
  - `ClaudeApiRequest` поддерживает опциональное поле `system` для системных промптов
  - `ClaudeJsonResponse` - модель для парсинга JSON ответов от Claude (question, answer, tags)

### Design Decisions
- **Stateful sessions** - hybrid хранилище (in-memory + персистентное) истории диалогов с автоматическим сжатием
- **Persistent storage** - автоматическое сохранение всех изменений в JSON файлы для сохранения между перезапусками
- **Automatic history compression** - каждые 3 пары сообщений автоматически заменяются на AI-generated summary
- **AI-generated titles** - автоматическое создание понятных названий диалогов с помощью Claude AI
- **Single responsibility** - ClaudeService для AI интеграции, ConversationHistoryService для управления историями, FileStorageService для персистентности
- **Configuration over code** - все настройки в application.conf
- **Defensive error handling** - все исключения логируются и возвращают понятные сообщения
- **Thread-safe** - ConcurrentHashMap для безопасного доступа к сессиям из разных потоков

## External Dependencies
- Anthropic Claude API (requires API key)
- Ktor Server (Netty engine)
- Ktor Client (CIO engine)
