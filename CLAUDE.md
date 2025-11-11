# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

AI Chat Service - Kotlin Multiplatform проект с REST API сервером на Ktor и UI на Compose Multiplatform.

**Модули:**
- **server** (JVM) - Ktor REST API сервер для взаимодействия с Claude AI
- **shared** (KMP: JVM + JS + Android) - Общие модели данных и API клиент
- **composeApp** (JS + Android) - UI на Compose Multiplatform с таргетами для веба и Android

## Build and Development Commands

### Build and Run
```bash
# Сборка всех модулей
./gradlew build

# Запуск сервера (JVM)
./gradlew :server:run         # Сервер на http://localhost:8080

# Запуск Web версии (JS)
./gradlew :composeApp:jsBrowserDevelopmentRun

# Сборка Android версии
./gradlew :composeApp:assembleDebug

# Запуск тестов
./gradlew test
./gradlew :server:test        # Только серверные тесты
```

### Configuration
- Установить ANTHROPIC_API_KEY через environment или в `server/src/main/resources/application.conf`
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
```

## Architecture Overview

### Module Structure

**server/** (JVM)
- `Application.kt` - Entry point, настройка Ktor сервера
- `models/` - ClaudeApiModels (внутренние модели для Anthropic API)
- `services/ClaudeService.kt` - Интеграция с Claude AI
- `routes/ChatRoutes.kt` - REST API endpoints
- `plugins/` - Ktor плагины (Serialization, CORS, StatusPages, Routing)

**shared/** (KMP)
- `commonMain/` - Общий код для всех платформ
  - `models/` - ChatRequest, ChatResponse (публичные DTOs)
  - `api/ChatApi.kt` - HTTP клиент для взаимодействия с сервером
- `jvmMain/` - JVM-специфичный код (Ktor CIO client)
- `jsMain/` - JS-специфичный код (Ktor JS client)
- `androidMain/` - Android-специфичный код (Ktor Android client)

**composeApp/** (Compose Multiplatform)
- `commonMain/ui/App.kt` - Общий UI код на Compose
  - Material 3 дизайн
  - Chat интерфейс
  - Состояния загрузки и ошибок
- `jsMain/` - JS entry point (Canvas-based Compose)
  - `main.kt` - Инициализация для браузера
  - `resources/index.html` - HTML страница
- `androidMain/` - Android entry point
  - `MainActivity.kt` - Activity для Android приложения

### Request Flow
1. UI (Compose) → ChatApi (shared) → HTTP запрос
2. Server: ChatRoutes → ClaudeService → Anthropic API
3. Anthropic API → ClaudeService → ChatResponse
4. ChatResponse → ChatApi → UI (Compose)

### Key Components

**ClaudeService** (server) - Работа с Anthropic API:
- Формирует запросы в формате Claude Messages API
- Поддерживает системные промпты
- Обрабатывает ошибки и логирует usage statistics

**ChatApi** (shared) - HTTP клиент:
- Ktor HTTP Client настроен для каждой платформы
- Единый API для всех платформ
- Сериализация/десериализация через kotlinx.serialization

**App.kt** (composeApp) - UI компонент:
- Список сообщений с автоскроллом
- Поле ввода и кнопка отправки
- Индикаторы загрузки
- Обработка ошибок

### Design Decisions
- **Kotlin Multiplatform** - единый код для JVM, JS и Android
- **Compose Multiplatform** - общий UI код для всех платформ
- **Stateless server** - нет сохранения истории диалогов
- **Модульная архитектура** - разделение на server, shared и composeApp
- **Type-safe** - строгая типизация через Kotlin

## External Dependencies
- Kotlin 2.0.20
- Ktor 2.3.12 (Server + Client)
- Compose Multiplatform 1.7.0
- kotlinx.serialization
- Anthropic Claude API
