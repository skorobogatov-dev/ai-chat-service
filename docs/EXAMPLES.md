# Примеры использования системных промптов

## Важно: Дефолтный формат ответа

⚠️ **По умолчанию все ответы возвращаются в JSON формате:**
```json
{
  "question": "вопрос пользователя",
  "answer": "ответ Claude",
  "tags": ["тег1", "тег2", "тег3"]
}
```

Чтобы получить обычный текстовый ответ, нужно переопределить `systemPrompt` в запросе.

## Базовое использование

### 1. Запрос с дефолтным промптом (JSON формат)

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Расскажи про Kotlin"}'

# Ответ: {"question": "Расскажи про Kotlin", "answer": "...", "tags": ["kotlin", "programming", "jvm"]}
```

### 1a. Запрос с текстовым ответом (переопределение дефолтного промпта)

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Расскажи про Kotlin",
    "systemPrompt": "Отвечай обычным текстом, без JSON форматирования"
  }'
```

## Форматирование ответов

### 2. Ответ в формате JSON

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Какие основные преимущества Kotlin?",
    "systemPrompt": "Отвечай строго в формате JSON с полями: advantages (массив строк), summary (строка)"
  }'
```

Ожидаемый формат ответа:
```json
{
  "response": "{\"advantages\": [\"Null safety\", \"Coroutines\", \"Interoperability with Java\"], \"summary\": \"Modern, concise, safe\"}",
  "model": "claude-sonnet-4-20250514"
}
```

### 3. Таблица в Markdown

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Сравни Kotlin и Java",
    "systemPrompt": "Отвечай в формате Markdown таблицы с колонками: Feature, Kotlin, Java"
  }'
```

### 4. Краткие ответы

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Что такое coroutines?",
    "systemPrompt": "Отвечай максимально кратко, одним предложением, максимум 20 слов"
  }'
```

## Специализированные роли

### 5. Технический эксперт

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Как работает suspend функция?",
    "systemPrompt": "Ты - senior Kotlin разработчик. Давай технически точные ответы с примерами кода"
  }'
```

### 6. Учитель для начинающих

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Что такое lambda функция?",
    "systemPrompt": "Объясняй простым языком для начинающих программистов, используй аналогии из реальной жизни"
  }'
```

## Структурированные данные

### 7. Список задач

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Что нужно сделать для создания REST API на Ktor?",
    "systemPrompt": "Отвечай в формате нумерованного списка шагов"
  }'
```

### 8. Код с комментариями

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Покажи пример coroutine",
    "systemPrompt": "Отвечай только кодом Kotlin с подробными комментариями на русском"
  }'
```

## Глобальная конфигурация

### 9. Установка дефолтного промпта через переменную окружения

```bash
export CLAUDE_SYSTEM_PROMPT="Отвечай всегда кратко и по делу, используя bullet points"
./gradlew run
```

Теперь все запросы будут использовать этот промпт по умолчанию:

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Расскажи про Ktor"}'
```

### 10. Переопределение глобального промпта

Даже если установлен глобальный промпт, его можно переопределить в конкретном запросе:

```bash
# Глобальный: "Отвечай кратко"
# Запрос переопределяет на детальный ответ
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Объясни coroutines",
    "systemPrompt": "Дай максимально подробный ответ с примерами и объяснением каждого аспекта"
  }'
```

## Продвинутые примеры

### 11. API документация

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Создай endpoint для регистрации пользователя",
    "systemPrompt": "Отвечай в формате OpenAPI 3.0 спецификации (YAML)"
  }'
```

### 12. Тестовые данные

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Создай 5 примеров пользователей",
    "systemPrompt": "Отвечай JSON массивом объектов с полями: id, name, email, age"
  }'
```

### 13. Code review

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "fun getUser(id: Int) { return users[id] }",
    "systemPrompt": "Ты - code reviewer. Найди проблемы в коде и предложи улучшения. Формат: Problem, Suggestion, Improved Code"
  }'
```

## Tips & Tricks

### Комбинирование требований

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Сравни Ktor и Spring Boot",
    "systemPrompt": "Отвечай: 1) Кратко (max 100 слов), 2) Bullet points, 3) Только факты без мнений"
  }'
```

### Специфичный output format

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Создай data class для User",
    "systemPrompt": "Отвечай ТОЛЬКО кодом Kotlin, без объяснений, без markdown блоков кода"
  }'
```

## Тестирование с httpie

Для более удобного тестирования можно использовать httpie:

```bash
# Установка
brew install httpie  # macOS
# или
apt install httpie   # Ubuntu

# Использование
http POST localhost:8080/api/chat \
  message="Что такое Kotlin?" \
  systemPrompt="Отвечай кратко"
```
