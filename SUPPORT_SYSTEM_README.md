# Система поддержки пользователей (Support System)

## Обзор

Полнофункциональная система технической поддержки для демо-продукта **TaskMaster Pro** с использованием:
- **RAG (Retrieval-Augmented Generation)**: поиск ответов в документации и FAQ
- **MCP (Model Context Protocol)**: интеграция с CRM (пользователи и тикеты)
- **Claude AI**: интеллектуальный ассистент поддержки

## Архитектура

```
┌─────────────────────────────────────────────────────────────┐
│              Web UI (вкладка "Поддержка")                   │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│         Server (порт 8080) - REST API /api/support          │
│  • SupportRoutes: CRUD операции с тикетами и чат            │
│  • RAG: поиск в векторизованной документации                │
│  • MCP: вызов инструментов Support MCP Server               │
└────────────┬──────────────────────────┬─────────────────────┘
             │                          │
             ▼                          ▼
┌────────────────────────┐   ┌──────────────────────────────┐
│  MCP Support Server    │   │   RAG Documents              │
│  (порт 3003)           │   │   docs/support/ (10 файлов)  │
│                        │   │                              │
│  8 MCP инструментов:   │   │  • product-overview.md       │
│  • get_user            │   │  • authentication-guide.md   │
│  • search_users        │   │  • task-management-guide.md  │
│  • get_ticket          │   │  • troubleshooting.md        │
│  • search_tickets      │   │  • faq.md                    │
│  • get_user_tickets    │   │  • api-integration.md        │
│  • create_ticket       │   │  • pricing-billing.md        │
│  • update_ticket       │   │  • и другие...               │
│                        │   │                              │
│  JSON Storage:         │   │  Автоматическая векторизация │
│  • users.json (5)      │   │  при старте сервера          │
│  • tickets.json (12)   │   └──────────────────────────────┘
└────────────────────────┘
```

## Демо-данные

### Пользователи (5):
- `user-001`: Алексей Петров (alex@example.com, **premium**)
- `user-002`: Мария Иванова (maria@example.com, **basic**)
- `user-003`: Дмитрий Сидоров (dmitry@example.com, **enterprise**)
- `user-004`: Елена Кузнецова (elena@example.com, **premium**)
- `user-005`: Иван Смирнов (ivan@example.com, **basic**)

### Тикеты (12 с разными статусами):
- **OPEN**: "Как экспортировать задачи в CSV?", "Проблема с синхронизацией календаря"
- **IN_PROGRESS**: "Ошибка при создании подзадачи", "API: ошибка 403"
- **WAITING_USER**: "Не приходят email-уведомления"
- **RESOLVED**: "Не работает авторизация через Google", "Вопрос по тарифам"
- **CLOSED**: "Не работает фильтр по тегам"

## Запуск системы

### Шаг 1: Запустить Ollama (для RAG векторизации)

```bash
# Если Ollama не установлен:
# brew install ollama (macOS) или скачать с https://ollama.com

# Запустить Ollama сервер
ollama serve

# В отдельном терминале: загрузить модель для векторизации
ollama pull nomic-embed-text
```

### Шаг 2: Запустить MCP Support Server

```bash
# Терминал 1: Собрать и запустить MCP Support Server
./gradlew :mcp-support-server:build
./gradlew :mcp-support-server:run

# Сервер запустится на ws://localhost:3003/mcp
# Логи покажут: "MCP Support Server started on port 3003"
```

### Шаг 3: Запустить основной Server

```bash
# Терминал 2: Установить ANTHROPIC_API_KEY и запустить сервер
export ANTHROPIC_API_KEY="your-api-key-here"
./gradlew :server:run

# Сервер запустится на http://localhost:8080
# Автоматически:
# 1. Подключится к MCP Support Server (порт 3003)
# 2. Векторизует документацию из docs/support/
# 3. Загрузит RAG индекс
```

### Шаг 4: Открыть веб-интерфейс

```bash
# Открыть браузер
open http://localhost:8080

# Перейти на вкладку "🎧 Поддержка"
```

## Использование системы

### 1. Поиск пользователя

1. В разделе "👤 Информация о пользователе" введите User ID: `user-001`
2. Нажмите "Найти пользователя"
3. Система отобразит:
   - Полную информацию о пользователе (имя, email, роль)
   - Все тикеты пользователя (если есть)
   - Автоматически заполнит User ID в форме создания тикета

### 2. Просмотр тикетов

- Тикеты отображаются в виде карточек с:
  - ID, темой, описанием
  - Статусом (цветовая индикация)
  - Приоритетом
  - Датой создания
- Кнопки:
  - "Подробнее" → открывает модальное окно с полной историей сообщений
  - "Открыть в чате" → загружает контекст тикета в чат с ассистентом

### 3. Создание тикета

1. Заполните форму:
   - **User ID**: `user-002`
   - **Тема**: "Проблема с импортом задач"
   - **Описание**: "При импорте CSV файла появляется ошибка 'Invalid date format'"
   - **Приоритет**: Средний/Высокий/Срочный
2. Нажмите "Создать тикет"
3. Тикет создается через MCP API и сохраняется в `tickets.json`

### 4. Чат с ассистентом поддержки (RAG + MCP)

**Пример 1: Вопрос с контекстом пользователя**

```
User ID: user-001
Ticket ID: (пусто)
Вопрос: "Почему не работает авторизация через Google?"

Ассистент выполняет:
1. Вызов MCP: get_user(user-001) → получает имя, роль, email
2. Вызов MCP: get_user_tickets(user-001) → находит историю обращений
3. RAG поиск: находит authentication-guide.md с решением проблемы
4. Формирует персонализированный ответ с учетом контекста

Ответ:
"Здравствуйте, Алексей! Вижу, что вы используете Premium тариф.

Проблемы с авторизацией через Google обычно возникают по следующим причинам:
1. Проверьте, предоставили ли вы доступ к email в настройках Google аккаунта
2. Очистите кэш браузера и cookie
3. Попробуйте авторизоваться в режиме инкогнито

Также я вижу, что у вас уже был похожий тикет #ticket-001, который был решен.
Хотите, я создам новый тикет для более детального расследования?"
```

**Пример 2: Вопрос с контекстом тикета**

```
User ID: user-003
Ticket ID: ticket-010
Вопрос: "Какие scopes нужны для создания задач через API?"

Ассистент выполняет:
1. MCP: get_user(user-003) → Дмитрий Сидоров, Enterprise
2. MCP: get_ticket(ticket-010) → "API: ошибка 403 при создании задачи"
3. RAG: находит api-integration.md с описанием scopes

Ответ:
"Здравствуйте, Дмитрий! По тикету #ticket-010 вижу, что вы получаете ошибку 403.

Для создания задач через API требуется scope 'tasks:write'.

Проверьте ваш API ключ:
1. Настройки → API & Интеграции
2. Просмотрите scopes вашего ключа
3. Если 'tasks:write' отсутствует - создайте новый ключ с этим scope

[Источники: api-integration.md]"
```

**Пример 3: Общий вопрос (только RAG)**

```
User ID: (пусто)
Ticket ID: (пусто)
Вопрос: "Сколько стоит Premium тариф?"

Ассистент выполняет:
1. RAG: находит pricing-billing.md

Ответ:
"Premium тариф стоит $9.99/месяц на пользователя.
При годовой подписке: $99.99/год (скидка 17%).

Включает:
- До 50 пользователей
- Неограниченное количество задач
- 10 ГБ хранилища на пользователя
- Интеграции: Google Calendar, Slack, Zapier
- Приоритетная поддержка

[Источник: pricing-billing.md]"
```

### 5. Контекстная информация

После каждого ответа ассистента отображается:
- **📚 Использована документация**: количество найденных разделов и источники
- **👤 Контекст пользователя**: имя, email, роль (если указан User ID)
- **🎫 Контекст тикета**: ID, тема, статус (если указан Ticket ID)

## REST API Endpoints

### Пользователи

```bash
# Получить информацию о пользователе
GET /api/support/user/{userId}

curl http://localhost:8080/api/support/user/user-001
```

### Тикеты

```bash
# Получить все тикеты пользователя
GET /api/support/tickets/{userId}

curl http://localhost:8080/api/support/tickets/user-001

# Получить конкретный тикет
GET /api/support/ticket/{ticketId}

curl http://localhost:8080/api/support/ticket/ticket-001

# Создать тикет
POST /api/support/tickets

curl -X POST http://localhost:8080/api/support/tickets \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user-002",
    "subject": "Проблема с экспортом",
    "description": "Не могу экспортировать задачи в CSV",
    "priority": "MEDIUM"
  }'

# Обновить тикет
PUT /api/support/ticket/{ticketId}

curl -X PUT http://localhost:8080/api/support/ticket/ticket-001 \
  -H "Content-Type: application/json" \
  -d '{
    "status": "RESOLVED",
    "notes": "Проблема решена, отправлен гайд"
  }'
```

### Чат с ассистентом

```bash
# Отправить сообщение ассистенту поддержки
POST /api/support/chat

curl -X POST http://localhost:8080/api/support/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Как настроить 2FA?",
    "userId": "user-001",
    "ticketId": null,
    "useRAG": true,
    "ragTopK": 5,
    "ragMinSimilarity": 0.7
  }'

# Ответ:
{
  "response": "Для включения двухфакторной аутентификации...",
  "sessionId": "uuid",
  "model": "claude-sonnet-4-20250514",
  "ragUsed": true,
  "ragChunksFound": 3,
  "ragSources": ["authentication-guide.md", "faq.md"],
  "userContext": {
    "userId": "user-001",
    "name": "Алексей Петров",
    "email": "alex@example.com",
    "role": "premium"
  }
}
```

## Тестирование системы

### Сценарий 1: Новый пользователь с проблемой

1. Поиск пользователя: `user-002` (Мария Иванова, Basic)
2. Просмотр её тикетов (должен быть ticket-002: "Как экспортировать задачи в CSV?")
3. Открыть тикет в чате
4. Задать уточняющий вопрос: "А можно экспортировать вместе с подзадачами?"
5. Ассистент найдет в RAG (export-import-guide.md), что это доступно только в Premium
6. Предложит обновить тариф

### Сценарий 2: Пользователь Enterprise с API проблемой

1. Поиск: `user-003` (Дмитрий Сидоров, Enterprise)
2. Найти ticket-010: "API: ошибка 403 при создании задачи" (IN_PROGRESS)
3. Открыть в чате
4. Спросить: "Как проверить scopes API ключа?"
5. Ассистент использует RAG (api-integration.md) + контекст тикета
6. Даёт точную инструкцию с учетом Enterprise тарифа

### Сценарий 3: Создание нового тикета

1. User ID: `user-004`
2. Тема: "Не приходят Slack уведомления"
3. Описание: "Настроил интеграцию со Slack, но уведомления не приходят в канал"
4. Приоритет: HIGH
5. Создать тикет
6. Перейти в чат и загрузить созданный тикет
7. Ассистент найдет решение в troubleshooting.md и notifications-settings.md

## Структура файлов

```
cc_test/
├── mcp-support-server/                  # MCP сервер для поддержки
│   ├── build.gradle.kts
│   ├── src/main/kotlin/
│   │   ├── SupportMCPServer.kt         # WebSocket сервер + 8 MCP tools
│   │   ├── models/                      # User, Ticket, TicketStatus, Priority
│   │   └── storage/                     # UserStorage, TicketStorage
│   └── src/main/resources/data/
│       ├── users.json                   # 5 демо-пользователей
│       └── tickets.json                 # 12 демо-тикетов
│
├── server/src/main/kotlin/dev/skorobogatov/
│   ├── Application.kt                   # Автоподключение к Support MCP + RAG векторизация
│   ├── models/SupportModels.kt          # DTO для Support API
│   ├── routes/SupportRoutes.kt          # REST API /api/support/*
│   └── plugins/Routing.kt               # Регистрация supportRoutes
│
├── docs/support/                         # Документация для RAG (10 файлов)
│   ├── product-overview.md
│   ├── authentication-guide.md
│   ├── task-management-guide.md
│   ├── export-import-guide.md
│   ├── notifications-settings.md
│   ├── calendar-integration.md
│   ├── troubleshooting.md
│   ├── faq.md
│   ├── api-integration.md
│   └── pricing-billing.md
│
├── web/
│   ├── index.html                       # Добавлена вкладка "🎧 Поддержка"
│   ├── js/support.js                    # UI логика для поддержки
│   └── css/styles.css                   # Стили для вкладки поддержки
│
└── server/src/main/resources/
    └── application.conf                 # Конфигурация support.systemPrompt
```

## Особенности реализации

### 1. RAG (Retrieval-Augmented Generation)

- **Автоматическая векторизация**: При старте сервера все `.md` файлы из `docs/support/` автоматически векторизуются через Ollama (nomic-embed-text, 768-мерные векторы)
- **Cosine Similarity Search**: Поиск топ-K наиболее похожих чанков (по умолчанию: topK=5, minSimilarity=0.7)
- **Контекст в промпте**: Найденные чанки добавляются в системный промпт Claude AI как "Relevant Documentation"

### 2. MCP (Model Context Protocol)

- **8 инструментов**: get_user, search_users, get_ticket, search_tickets, get_user_tickets, create_ticket, update_ticket, search_users
- **Автоматический вызов**: При наличии userId/ticketId в запросе, SupportRoutes автоматически вызывает соответствующие MCP инструменты
- **JSON Storage**: Простое хранилище в JSON файлах с thread-safe операциями

### 3. Интеграция RAG + MCP

SupportRoutes.kt объединяет оба подхода:
1. Если указан `userId` → вызов MCP `get_user()` + `get_user_tickets()`
2. Если указан `ticketId` → вызов MCP `get_ticket()` с полной историей
3. Если `useRAG=true` → векторизация запроса + поиск в документации
4. Все контексты (MCP + RAG) добавляются в системный промпт
5. Claude AI формирует комплексный ответ с учетом всех источников

## Конфигурация

### Environment Variables

```bash
# Anthropic API ключ (обязательно)
export ANTHROPIC_API_KEY="sk-ant-..."

# Системный промпт для поддержки (опционально)
export SUPPORT_SYSTEM_PROMPT="Custom support prompt..."

# URL Support MCP сервера (опционально, по умолчанию ws://localhost:3003/mcp)
export MCP_SUPPORT_SERVER_URL="ws://localhost:3003/mcp"

# Ollama конфигурация (опционально)
export OLLAMA_BASE_URL="http://localhost:11434"
export OLLAMA_MODEL="nomic-embed-text"
```

### application.conf

```hocon
support {
    systemPrompt = """You are a technical support assistant for TaskMaster Pro..."""
    systemPrompt = ${?SUPPORT_SYSTEM_PROMPT}
}

mcp {
    supportServerUrl = "ws://localhost:3003/mcp"
    supportServerUrl = ${?MCP_SUPPORT_SERVER_URL}
}
```

## Troubleshooting

### Проблема: MCP Support Server не подключается

**Решение:**
1. Проверьте, что Support MCP сервер запущен: `./gradlew :mcp-support-server:run`
2. Проверьте порт 3003: `lsof -i :3003`
3. Посмотрите логи сервера на наличие ошибок

### Проблема: RAG не находит документацию

**Решение:**
1. Убедитесь, что Ollama запущен: `ollama list`
2. Проверьте, что модель nomic-embed-text загружена: `ollama pull nomic-embed-text`
3. Проверьте логи сервера: должны быть сообщения "Successfully vectorized and saved..."
4. Проверьте директорию `embeddings_output/` - должны быть JSON файлы с embeddings

### Проблема: Ассистент не использует контекст пользователя

**Решение:**
1. Проверьте, что User ID указан корректно
2. Проверьте, что пользователь существует в `users.json`
3. Посмотрите логи SupportRoutes - должны быть сообщения "Support chat request: userId=..."

## Дальнейшее развитие

Возможные улучшения:
1. **Reranking**: Добавить reranking для повышения точности RAG
2. **Analytics**: Статистика по тикетам (время ответа, популярные темы)
3. **Auto-tagging**: Автоматическая категоризация тикетов с помощью Claude
4. **Sentiment analysis**: Анализ тональности сообщений пользователей
5. **Suggested responses**: Предложение готовых ответов на основе истории
6. **Email integration**: Создание тикетов из входящих email
7. **Webhooks**: Уведомления о новых тикетах через webhooks

---

**Поздравляем! Система поддержки полностью реализована и готова к использованию! 🎉**
