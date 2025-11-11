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
# Базовый запрос (использует модель и JSON формат из конфигурации)
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Привет!"}'

# Ответ: {"response": "...", "model": "claude-3-5-sonnet-20241022"}

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

### Доступные модели Claude (от слабых к сильным)

1. **claude-3-haiku-20240307** - Самая быстрая и доступная модель, идеальна для простых задач
2. **claude-sonnet-4-20250514** - Самая продвинутая модель Claude 4 поколения (рекомендуется по умолчанию)

Модель по умолчанию настраивается в `application.conf`. Если не указано иное, используется модель из конфигурации.

## Architecture Overview

### Request Flow
1. HTTP запрос → ChatRoutes (routes/)
2. ChatRoutes → ClaudeService (services/)
3. ClaudeService → Anthropic API (через Ktor HTTP Client)
4. Ответ от Claude → ChatResponse DTO → JSON → клиент

### Key Components

**Application.kt** - Entry point, инициализирует:
- HTTP Client (CIO engine) для запросов к Anthropic
- ClaudeService с конфигурацией из application.conf
- Plugins (Serialization, CORS, StatusPages, Routing)

**ClaudeService** - Инкапсулирует логику работы с Anthropic API:
- Формирует запросы в формате Claude Messages API
- Поддерживает динамический выбор модели (дефолтная из конфигурации → переопределение в запросе)
- Поддерживает системные промпты (встроенный JSON формат → env переменная → переопределение в запросе)
- Дефолтный промпт: возвращает все ответы в JSON формате {question, answer, tags}
- Парсит JSON ответ и извлекает только поле `answer` для пользователя (с fallback на полный текст при ошибке парсинга)
- Измеряет время выполнения запросов и собирает статистику по токенам
- Обрабатывает ошибки и логирует usage statistics
- Возвращает упрощенные ChatResponse объекты с информацией об использованной модели, токенах и времени ответа

**Plugins** - Модульная конфигурация Ktor:
- Serialization: kotlinx.serialization для JSON
- HTTP: CORS для кросс-доменных запросов
- StatusPages: глобальная обработка исключений
- Routing: регистрация всех routes

### Data Models
- `ChatRequest/ChatResponse` - публичные API DTOs
  - `ChatRequest` содержит `message`, опциональный `systemPrompt` и опциональный `model`
  - `ChatResponse` содержит `response`, `model`, `inputTokens`, `outputTokens`, `totalTokens`, `responseTimeMs`
  - `model` позволяет выбрать конкретную модель Claude для запроса
- `ClaudeModels.kt` - enum с доступными моделями Claude (Haiku, Sonnet 4)
- `ClaudeApiModels.kt` - внутренние модели Anthropic API (messages, content, usage)
  - `ClaudeApiRequest` поддерживает опциональное поле `system` для системных промптов
  - `ClaudeJsonResponse` - модель для парсинга JSON ответов от Claude (question, answer, tags)

### Design Decisions
- **Stateless architecture** - нет сохранения истории диалогов
- **Single responsibility** - ClaudeService только для AI интеграции
- **Configuration over code** - все настройки в application.conf
- **Defensive error handling** - все исключения логируются и возвращают понятные сообщения

## External Dependencies
- Anthropic Claude API (requires API key)
- Ktor Server (Netty engine)
- Ktor Client (CIO engine)
