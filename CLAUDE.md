# CLAUDE.md

This file provides guidance to Claude Code when working with this repository.

## Project Overview

AI Chat Service - multi-module Ktor проект для взаимодействия с Claude AI через Anthropic API.

### Модульная структура

```
ai-chat-service/
├── server/                      # Основной REST API сервер (порт 8080)
├── mcp-weather-server/          # MCP сервер для погоды (порт 3000)
├── mcp-activities-server/       # MCP сервер для активностей (порт 3001)
├── mcp-filesystem-server/       # MCP сервер для файловой системы и git (порт 3002)
├── web/                         # Веб-интерфейс
├── settings.gradle.kts
└── build.gradle.kts
```

## Build Commands

```bash
# Основные команды
./gradlew build                          # Сборка всех модулей
./gradlew clean                          # Очистка
./gradlew :server:run                    # Запуск сервера (порт 8080)
./gradlew :server:test                   # Тесты сервера

# MCP серверы
./gradlew :mcp-weather-server:run        # Погода (порт 3000)
./gradlew :mcp-activities-server:run     # Активности (порт 3001)
./gradlew :mcp-filesystem-server:run     # Файлы и git (порт 3002)
```

## Configuration

| Переменная | Описание | Значение по умолчанию |
|------------|----------|----------------------|
| ANTHROPIC_API_KEY | API ключ Anthropic | - |
| MCP_SERVER_URL | URL MCP сервера для автоподключения | - |
| CLAUDE_SYSTEM_PROMPT | Системный промпт | JSON формат |
| OLLAMA_BASE_URL | URL Ollama сервера | http://localhost:11434 |
| OLLAMA_MODEL | Модель для embeddings | nomic-embed-text |
| OLLAMA_CHAT_MODEL | Модель для chat (локальная LLM) | llama3.2 |

### Персонализация
- **user_profile.json** - профиль пользователя для персонализации ответов Ollama
- См. подробную документацию в [PERSONALIZATION.md](PERSONALIZATION.md)

## API Endpoints

### Chat API
```bash
# Базовый запрос с Claude (создает новую сессию)
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Привет!"}'

# Запрос с локальной Ollama LLM
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Привет!", "provider": "ollama"}'

# Ollama с конкретной моделью
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Привет!", "provider": "ollama", "model": "mistral"}'

# Продолжение диалога
curl -X POST http://localhost:8080/api/chat \
  -d '{"message": "Расскажи подробнее", "sessionId": "uuid-from-previous-response"}'

# С RAG и reranking (только Claude)
curl -X POST http://localhost:8080/api/chat \
  -d '{"message": "Вопрос", "useRAG": true, "useReranking": true, "ragTopK": 3}'

# RAG с Ollama (reranking недоступен)
curl -X POST http://localhost:8080/api/chat \
  -d '{"message": "Вопрос", "provider": "ollama", "useRAG": true, "ragTopK": 3}'
```

**Параметры ChatRequest:**
- `message` (обязательно) - сообщение пользователя
- `sessionId` - ID сессии для продолжения диалога
- `systemPrompt` - переопределение системного промпта
- `provider` - провайдер LLM: "claude" (default) или "ollama"
- `model` - модель (для Claude: claude-3-haiku-20240307, claude-sonnet-4-20250514; для Ollama: llama3.2, mistral, etc.)
- `useRAG` - включить RAG (default: false)
- `ragTopK` - количество чанков для контекста (default: 3)
- `ragMinSimilarity` - минимальное сходство (default: 0.5)
- `useReranking` - включить reranking (default: false, только для Claude)

### MCP API
```bash
# Подключение к MCP серверу
curl -X POST http://localhost:8080/api/mcp/connect \
  -d '{"serverUrl": "ws://localhost:3000/mcp", "transportType": "websocket"}'

# Список инструментов
curl http://localhost:8080/api/mcp/tools

# Вызов инструмента
curl -X POST http://localhost:8080/api/mcp/tools/call \
  -d '{"toolName": "get_weather", "arguments": {"city": "Москва"}}'

# Статус и отключение
curl http://localhost:8080/api/mcp/status
curl -X POST http://localhost:8080/api/mcp/disconnect
```

**MCP инструменты:**

| Сервер | Инструмент | Описание |
|--------|-----------|----------|
| Weather (3000) | get_weather(city) | Текущая погода |
| Weather (3000) | get_forecast(city, days) | Прогноз погоды |
| Activities (3001) | suggest_activities(temperature, weather_code, ...) | Рекомендации активностей |
| Filesystem (3002) | list_files(path?, recursive?) | Список файлов |
| Filesystem (3002) | read_file(path) | Чтение файла |
| Filesystem (3002) | get_git_branch() | Текущая ветка |
| Filesystem (3002) | get_git_status() | Статус git |
| Filesystem (3002) | get_git_diff(staged?, file?) | Diff изменений |
| Filesystem (3002) | get_git_log(limit?, file?) | История коммитов |
| Filesystem (3002) | get_git_commit(commit_hash) | Детали коммита |

### Tasks API (Scheduled Tasks)
```bash
# Создание задачи
curl -X POST http://localhost:8080/api/tasks \
  -d '{"name": "Daily Weather", "question": "Погода?", "schedule": {"type": "DAILY", "hour": 9, "minute": 0}}'

# Список / получение / обновление / удаление
curl http://localhost:8080/api/tasks
curl http://localhost:8080/api/tasks/{taskId}
curl -X PUT http://localhost:8080/api/tasks/{taskId} -d '{"enabled": false}'
curl -X DELETE http://localhost:8080/api/tasks/{taskId}

# История выполнений
curl http://localhost:8080/api/tasks/{taskId}/executions
```

**Типы расписания:** ONCE (startTime), DAILY (hour, minute), WEEKLY (dayOfWeek, hour, minute)

### Embeddings API
```bash
# Получение embedding
curl -X POST http://localhost:8080/api/embeddings -d '{"text": "Текст"}'

# Batch embeddings
curl -X POST http://localhost:8080/api/embeddings/batch -d '{"texts": ["Текст 1", "Текст 2"]}'

# Векторизация для RAG
curl -X POST http://localhost:8080/api/embeddings/vectorize \
  -d '{"text": "...", "chunkSize": 750, "overlap": 75, "saveToFile": true}'

# Векторизация файла
curl -X POST http://localhost:8080/api/embeddings/vectorize-file -F "file=@document.txt"

# RAG управление
curl http://localhost:8080/api/embeddings/rag/status
curl -X POST http://localhost:8080/api/embeddings/rag/reload
curl -X POST http://localhost:8080/api/embeddings/rag/search -d '{"query": "...", "topK": 5}'
curl -X DELETE http://localhost:8080/api/embeddings/rag/documents/{fileName}

# Статус Ollama (embeddings)
curl http://localhost:8080/api/embeddings/status
```

### Ollama API (Chat)
```bash
# Список доступных моделей для chat
curl http://localhost:8080/api/ollama/models

# Статус Ollama chat сервиса
curl http://localhost:8080/api/ollama/status

# Проверка доступности конкретной модели
curl http://localhost:8080/api/ollama/models/llama3.2/check
```

### Profile API (Персонализация)
```bash
# Получить профиль пользователя
curl http://localhost:8080/api/profile

# Статус персонализации
curl http://localhost:8080/api/profile/status

# Перезагрузить профиль из файла
curl -X POST http://localhost:8080/api/profile/reload

# Посмотреть сгенерированный системный промпт (отладка)
curl http://localhost:8080/api/profile/system-prompt
```

Подробнее см. [PERSONALIZATION.md](PERSONALIZATION.md)

### Swagger UI
- **Swagger UI**: http://localhost:8080/swagger
- **OpenAPI Spec**: http://localhost:8080/openapi

## Architecture

### Request Flow
1. HTTP → ChatRoutes → ConversationHistoryService (сессия)
2. → ClaudeService.sendMessage() → Anthropic API **или** OllamaChatService.sendMessage() → Ollama API (в зависимости от provider)
3. → ConversationHistoryService (сохранение) → FileStorageService
4. → ChatResponse → клиент

### Key Components

| Компонент | Назначение |
|-----------|------------|
| Application.kt | Entry point, инициализация сервисов |
| ClaudeService | Anthropic API, history compression, title generation |
| OllamaChatService | Ollama Chat API для локальной LLM (альтернатива Claude) |
| ConversationHistoryService | Управление сессиями, in-memory + persistent |
| FileStorageService | Сохранение диалогов в JSON (chat_sessions/) |
| TaskStorageService | Сохранение задач (scheduled_tasks/) |
| SchedulerService | Планировщик задач на coroutines |
| MCPService | MCP клиент, подключение и вызов инструментов |
| OllamaService | Embeddings через Ollama |
| RAGService | RAG индекс и поиск |
| UserProfileService | Персонализация ответов на основе профиля пользователя |

### Data Models

**API DTOs:**
- `ChatRequest/ChatResponse` - Chat API
- `MCPModels.kt` - MCP интеграция
- `TaskModels.kt` - Scheduled Tasks API
- `OllamaModels.kt` - Embeddings API

**Internal:**
- `ConversationHistory.kt` - история диалогов (sessionId, title, messages)
- `ScheduledTask.kt` - задачи и выполнения
- `ClaudeApiModels.kt` - Anthropic API формат

### Design Decisions
- **Hybrid storage**: in-memory (ConcurrentHashMap) + JSON files для persistence
- **Auto compression**: каждые 3 пары сообщений → AI summary
- **Coroutine scheduler**: async task execution
- **Thread-safe**: ConcurrentHashMap, Mutex для MCP
- **Local embeddings**: Ollama для контроля данных

## Features

### История диалогов
- Автосохранение в `chat_sessions/{sessionId}.json`
- Сжатие истории каждые 3 пары сообщений
- Автогенерация названий через Claude AI
- Восстановление при перезапуске

### RAG System
1. Векторизация документов → `embeddings_output/`
2. При `useRAG: true` запрос векторизуется
3. Поиск похожих чанков (cosine similarity)
4. Добавление контекста в системный промпт
5. Опциональный reranking через Claude AI

### Code Review через MCP
Claude AI автоматически использует MCP инструменты:
- `get_git_status()` / `get_git_diff()` - анализ изменений
- `read_file()` - контекст файлов
- RAG - проверка соответствия стандартам

### Персонализация AI (Ollama)
1. Загрузка профиля из `user_profile.json`
2. Генерация персонализированного системного промпта
3. Обращение к пользователю по имени
4. Адаптация стиля ответов под предпочтения
5. Учет технического стека и контекста проектов
6. Использование культурных отсылок (книги, фильмы, музыка)
См. [PERSONALIZATION.md](PERSONALIZATION.md) для деталей

## External Dependencies
- **Anthropic Claude API** (requires API key) - облачная LLM
- **Ollama** (optional) - локальная LLM (llama3.2, mistral, etc.) и embeddings (nomic-embed-text)
- **Ktor 3.0.3** - Server (Netty), Client (CIO), WebSockets
- **Kotlin 2.1.0**
- **MCP Kotlin SDK 0.6.0**
- **kotlinx.serialization**
- **Logback**

### Сравнение провайдеров

| Функция | Claude | Ollama |
|---------|--------|--------|
| Chat/генерация | ✅ | ✅ |
| MCP Tools | ✅ | ❌ |
| RAG | ✅ | ✅ |
| Reranking | ✅ | ❌ |
| История/сжатие | ✅ | ✅ |
| Генерация названий | ✅ | ✅ |
| Персонализация | ❌ | ✅ |
| Требует API ключ | ✅ | ❌ |
| Работает локально | ❌ | ✅ |
