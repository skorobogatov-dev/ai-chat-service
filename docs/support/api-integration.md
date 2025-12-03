# API интеграция

**Доступно в:** Enterprise тариф

## Обзор REST API

TaskMaster Pro предоставляет полнофункциональный REST API для интеграции с внешними системами и создания кастомных приложений.

**Base URL:** `https://api.taskmaster.pro/v1`

**Формат:** JSON

**Аутентификация:** Bearer token (API ключ)

## Получение API ключа

### Создание API ключа

1. Войдите в TaskMaster Pro
2. Настройки → API & Интеграции
3. Нажмите "Создать API ключ"
4. Заполните форму:
   - **Название**: Описательное имя (например, "Mobile App", "CI/CD Integration")
   - **Scopes** (области доступа): Выберите необходимые права
   - **Срок действия**: 30 дней, 90 дней, 1 год, или бессрочный
5. Нажмите "Создать"
6. **Важно**: Скопируйте ключ немедленно (он показывается только один раз!)

### Доступные Scopes (права)

- `tasks:read` - Чтение задач
- `tasks:write` - Создание и редактирование задач
- `tasks:delete` - Удаление задач
- `projects:read` - Чтение проектов
- `projects:write` - Создание и редактирование проектов
- `users:read` - Чтение информации о пользователях
- `comments:read` - Чтение комментариев
- `comments:write` - Создание комментариев
- `webhooks:manage` - Управление webhooks

### Ротация ключей

Рекомендуется регулярно обновлять API ключи:

1. Создайте новый ключ
2. Обновите конфигурацию в ваших приложениях
3. Дождитесь полного переключения
4. Отзовите старый ключ

## Аутентификация

### Bearer Token аутентификация

Все запросы должны включать заголовок авторизации:

```http
Authorization: Bearer YOUR_API_KEY
Content-Type: application/json
```

### Пример запроса

```bash
curl -X GET "https://api.taskmaster.pro/v1/tasks" \
  -H "Authorization: Bearer sk_live_abc123..." \
  -H "Content-Type: application/json"
```

### Ошибки аутентификации

**401 Unauthorized** - Невалидный или отсутствующий токен
```json
{
  "error": {
    "code": "unauthorized",
    "message": "Invalid API key"
  }
}
```

**403 Forbidden** - Недостаточно прав (scope)
```json
{
  "error": {
    "code": "forbidden",
    "message": "Insufficient permissions. Required scope: tasks:write"
  }
}
```

## Основные Endpoints

### Задачи (Tasks)

#### Получить список задач

```http
GET /v1/projects/{projectId}/tasks
```

**Query параметры:**
- `status` (optional): `open`, `in_progress`, `done`
- `assignee` (optional): User ID
- `priority` (optional): `low`, `medium`, `high`, `urgent`
- `limit` (optional): Количество задач (default: 50, max: 100)
- `offset` (optional): Смещение для пагинации

**Пример:**
```bash
curl "https://api.taskmaster.pro/v1/projects/proj_123/tasks?status=open&limit=20" \
  -H "Authorization: Bearer sk_live_..."
```

**Ответ:**
```json
{
  "data": [
    {
      "id": "task_abc123",
      "title": "Fix homepage bug",
      "description": "The header is not displaying correctly on mobile",
      "status": "open",
      "priority": "high",
      "assignee": {
        "id": "user_456",
        "name": "John Doe",
        "email": "john@example.com"
      },
      "dueDate": "2024-12-20T10:00:00Z",
      "createdAt": "2024-12-01T08:30:00Z",
      "updatedAt": "2024-12-03T14:20:00Z"
    }
  ],
  "pagination": {
    "total": 150,
    "limit": 20,
    "offset": 0,
    "hasMore": true
  }
}
```

#### Получить конкретную задачу

```http
GET /v1/tasks/{taskId}
```

**Ответ:** Детальная информация о задаче, включая подзадачи и комментарии.

#### Создать задачу

```http
POST /v1/projects/{projectId}/tasks
```

**Body:**
```json
{
  "title": "New task title",
  "description": "Task description",
  "assignee": "user_456",
  "priority": "medium",
  "dueDate": "2024-12-25T10:00:00Z",
  "tags": ["bug", "frontend"]
}
```

**Ответ:**
```json
{
  "id": "task_new123",
  "title": "New task title",
  "status": "open",
  "createdAt": "2024-12-03T15:00:00Z"
}
```

#### Обновить задачу

```http
PUT /v1/tasks/{taskId}
```

**Body:** Частичное обновление (только измененные поля)
```json
{
  "status": "in_progress",
  "assignee": "user_789"
}
```

#### Удалить задачу

```http
DELETE /v1/tasks/{taskId}
```

**Ответ:** `204 No Content`

### Проекты (Projects)

#### Получить список проектов

```http
GET /v1/projects
```

#### Создать проект

```http
POST /v1/projects
```

**Body:**
```json
{
  "name": "Website Redesign",
  "description": "Complete redesign of company website",
  "members": ["user_123", "user_456"]
}
```

### Комментарии (Comments)

#### Получить комментарии задачи

```http
GET /v1/tasks/{taskId}/comments
```

#### Добавить комментарий

```http
POST /v1/tasks/{taskId}/comments
```

**Body:**
```json
{
  "content": "This is a comment on the task",
  "mentions": ["user_456"]
}
```

### Пользователи (Users)

#### Получить информацию о пользователе

```http
GET /v1/users/{userId}
```

#### Получить текущего пользователя

```http
GET /v1/users/me
```

## Webhooks

Webhooks позволяют получать уведомления о событиях в реальном времени.

### Создание Webhook

```http
POST /v1/webhooks
```

**Body:**
```json
{
  "url": "https://your-server.com/webhook-endpoint",
  "events": ["task.created", "task.updated", "task.deleted"],
  "secret": "your_webhook_secret"
}
```

### Доступные события

- `task.created` - Задача создана
- `task.updated` - Задача обновлена
- `task.deleted` - Задача удалена
- `task.completed` - Задача завершена
- `comment.created` - Добавлен комментарий
- `project.created` - Проект создан

### Формат webhook payload

```json
{
  "event": "task.created",
  "timestamp": "2024-12-03T15:30:00Z",
  "data": {
    "taskId": "task_abc123",
    "projectId": "proj_456",
    "title": "New task",
    "createdBy": "user_789"
  }
}
```

### Верификация webhook

Каждый webhook запрос включает подпись в заголовке:

```http
X-TaskMaster-Signature: sha256=abc123...
```

Вычислите HMAC SHA256 с вашим секретом и сравните:

```python
import hmac
import hashlib

def verify_signature(payload, signature, secret):
    computed = hmac.new(
        secret.encode(),
        payload.encode(),
        hashlib.sha256
    ).hexdigest()
    return hmac.compare_digest(f"sha256={computed}", signature)
```

## Rate Limits

**Лимиты по тарифам:**
- **Basic**: 100 запросов/час
- **Premium**: 1,000 запросов/час
- **Enterprise**: 10,000 запросов/час (или кастомный)

**Заголовки ответа:**
```http
X-RateLimit-Limit: 1000
X-RateLimit-Remaining: 850
X-RateLimit-Reset: 1638370800
```

**При превышении лимита:**
```http
HTTP/1.1 429 Too Many Requests
```

```json
{
  "error": {
    "code": "rate_limit_exceeded",
    "message": "Rate limit exceeded. Retry after 300 seconds"
  }
}
```

## Обработка ошибок

### Стандартные коды ошибок

- `400 Bad Request` - Некорректные параметры
- `401 Unauthorized` - Неправильный API ключ
- `403 Forbidden` - Недостаточно прав
- `404 Not Found` - Ресурс не найден
- `422 Unprocessable Entity` - Валидация не прошла
- `429 Too Many Requests` - Превышен лимит запросов
- `500 Internal Server Error` - Ошибка сервера

### Формат ошибки

```json
{
  "error": {
    "code": "validation_error",
    "message": "Validation failed",
    "details": {
      "title": "Title is required",
      "dueDate": "Invalid date format"
    }
  }
}
```

## Пагинация

Для endpoints, возвращающих списки:

**Request:**
```http
GET /v1/tasks?limit=50&offset=100
```

**Response:**
```json
{
  "data": [...],
  "pagination": {
    "total": 500,
    "limit": 50,
    "offset": 100,
    "hasMore": true,
    "nextOffset": 150
  }
}
```

## Фильтрация и сортировка

### Фильтры

```http
GET /v1/tasks?status=open&priority=high&assignee=user_123
```

### Сортировка

```http
GET /v1/tasks?sort=dueDate&order=asc
```

Доступные поля для сортировки:
- `createdAt`
- `updatedAt`
- `dueDate`
- `priority`
- `title`

## Батчевые операции

### Массовое обновление задач

```http
PATCH /v1/tasks/bulk
```

**Body:**
```json
{
  "taskIds": ["task_1", "task_2", "task_3"],
  "updates": {
    "status": "done"
  }
}
```

## Примеры интеграции

### Python

```python
import requests

API_KEY = "sk_live_your_key"
BASE_URL = "https://api.taskmaster.pro/v1"

headers = {
    "Authorization": f"Bearer {API_KEY}",
    "Content-Type": "application/json"
}

# Получить задачи
response = requests.get(
    f"{BASE_URL}/projects/proj_123/tasks",
    headers=headers
)
tasks = response.json()["data"]

# Создать задачу
new_task = {
    "title": "New task from API",
    "priority": "high"
}
response = requests.post(
    f"{BASE_URL}/projects/proj_123/tasks",
    headers=headers,
    json=new_task
)
```

### JavaScript (Node.js)

```javascript
const axios = require('axios');

const API_KEY = 'sk_live_your_key';
const BASE_URL = 'https://api.taskmaster.pro/v1';

const client = axios.create({
  baseURL: BASE_URL,
  headers: {
    'Authorization': `Bearer ${API_KEY}`,
    'Content-Type': 'application/json'
  }
});

// Получить задачи
async function getTasks(projectId) {
  const response = await client.get(`/projects/${projectId}/tasks`);
  return response.data;
}

// Создать задачу
async function createTask(projectId, taskData) {
  const response = await client.post(
    `/projects/${projectId}/tasks`,
    taskData
  );
  return response.data;
}
```

### cURL

```bash
# Получить задачи
curl -X GET "https://api.taskmaster.pro/v1/projects/proj_123/tasks" \
  -H "Authorization: Bearer sk_live_your_key"

# Создать задачу
curl -X POST "https://api.taskmaster.pro/v1/projects/proj_123/tasks" \
  -H "Authorization: Bearer sk_live_your_key" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "New task",
    "priority": "high",
    "assignee": "user_456"
  }'
```

## Частые ошибки

### "401 Unauthorized: Invalid API key"

**Причина:** Неправильный или отозванный API ключ

**Решение:**
1. Проверьте правильность ключа
2. Убедитесь, что ключ не истек
3. Проверьте формат заголовка: `Authorization: Bearer sk_live_...`

### "403 Forbidden: Insufficient permissions"

**Причина:** У API ключа нет нужного scope

**Решение:**
1. Проверьте scopes ключа: Настройки → API → Просмотреть ключ
2. Создайте новый ключ с необходимыми правами
3. Или обновите существующий ключ

### "429 Too Many Requests"

**Причина:** Превышен rate limit

**Решение:**
1. Реализуйте exponential backoff
2. Используйте батчевые операции
3. Обновите тариф для большего лимита
4. Кэшируйте часто запрашиваемые данные

### "422 Unprocessable Entity: Validation failed"

**Причина:** Некорректные данные в запросе

**Решение:**
1. Проверьте формат дат: ISO 8601 (`YYYY-MM-DDTHH:MM:SSZ`)
2. Убедитесь, что все обязательные поля заполнены
3. Проверьте типы данных (строки, числа, булевы)
4. Проверьте допустимые значения enum (например, priority: low/medium/high/urgent)

## Поддержка и ресурсы

- **Полная документация API**: api.taskmaster.pro/docs
- **Интерактивная документация (Swagger)**: api.taskmaster.pro/swagger
- **SDK библиотеки**: github.com/taskmaster/sdk (Python, JavaScript, Ruby, PHP)
- **Примеры кода**: github.com/taskmaster/api-examples
- **Поддержка**: api-support@taskmaster.pro
