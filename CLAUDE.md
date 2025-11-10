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

### Testing API
```bash
# Базовый запрос (использует дефолтный JSON формат)
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Привет!"}'

# Ответ: {"question": "Привет!", "answer": "...", "tags": [...]}

# С переопределением системного промпта
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Что такое Kotlin?", "systemPrompt": "Отвечай обычным текстом"}'

# С параметром temperature для управления креативностью ответов (0.0-1.0)
# По умолчанию Claude использует temperature = 1.0
# Низкая temperature (0.0-0.3) - более предсказуемые и консервативные ответы
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Напиши стихотворение", "temperature": 0.0}'

# Средняя temperature (0.5) - сбалансированное поведение
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Напиши стихотворение", "temperature": 0.5}'

# Высокая temperature (1.0) - максимальная креативность и разнообразие
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Напиши стихотворение", "temperature": 1.0}'

# Тестирование влияния temperature
./test_temperature.sh
```

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
- Поддерживает системные промпты (встроенный JSON формат → env переменная → переопределение в запросе)
- Дефолтный промпт: возвращает все ответы в JSON формате {question, answer, tags}
- Обрабатывает ошибки и логирует usage statistics
- Возвращает упрощенные ChatResponse объекты

**Plugins** - Модульная конфигурация Ktor:
- Serialization: kotlinx.serialization для JSON
- HTTP: CORS для кросс-доменных запросов
- StatusPages: глобальная обработка исключений
- Routing: регистрация всех routes

### Data Models
- `ChatRequest/ChatResponse` - публичные API DTOs
  - `ChatRequest` содержит:
    - `message` (обязательное) - текст сообщения пользователя
    - `systemPrompt` (опциональное) - переопределение системного промпта
    - `temperature` (опциональное, 0.0-1.0) - контроль степени креативности ответов
      - **0.0** - максимально предсказуемые ответы (для аналитических задач)
      - **0.5** - сбалансированное поведение
      - **1.0** - максимальная креативность (по умолчанию в Claude)
      - Даже при 0.0 ответы не будут полностью детерминированными
- `ClaudeApiModels.kt` - внутренние модели Anthropic API (messages, content, usage)
  - `ClaudeApiRequest` поддерживает опциональные поля `system` и `temperature`

### Testing Temperature
Для проверки влияния temperature на ответы используйте тестовый скрипт:
```bash
./test_temperature.sh
```
Скрипт отправит одинаковый запрос с разными значениями temperature (0.0, 0.5, 1.0) и покажет разницу в ответах.

### Design Decisions
- **Stateless architecture** - нет сохранения истории диалогов
- **Single responsibility** - ClaudeService только для AI интеграции
- **Configuration over code** - все настройки в application.conf
- **Defensive error handling** - все исключения логируются и возвращают понятные сообщения

## External Dependencies
- Anthropic Claude API (requires API key)
- Ktor Server (Netty engine)
- Ktor Client (CIO engine)
