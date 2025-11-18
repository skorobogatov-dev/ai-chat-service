# ⚡ Quick Start - MCP Weather Integration

## 🚀 Запуск за 3 шага

### 1️⃣ Установите API ключ
```bash
export ANTHROPIC_API_KEY="sk-ant-..."
```

### 2️⃣ Запустите серверы (в двух разных терминалах)

**Терминал 1 - Weather MCP Server:**
```bash
./run-weather-server.sh
```

**Терминал 2 - Основное приложение:**
```bash
./run-main-with-mcp.sh
```

### 3️⃣ Протестируйте

**Терминал 3:**
```bash
# Автоматический тест
./test-weather-integration.sh

# ИЛИ ручной тест
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Какая погода в Москве?"}'
```

## 📝 Примеры запросов

### Текущая погода
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Какая погода в Санкт-Петербурге?",
    "systemPrompt": "Отвечай кратко на русском"
  }'
```

### Прогноз погоды
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Дай прогноз погоды на неделю в Москве",
    "systemPrompt": "Отвечай кратко на русском"
  }'
```

### Продолжение диалога
```bash
# Первый запрос
RESPONSE=$(curl -s -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Какая погода в Москве?"}')

# Получить sessionId
SESSION_ID=$(echo $RESPONSE | jq -r '.sessionId')

# Продолжить диалог
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d "{
    \"message\": \"А в Санкт-Петербурге?\",
    \"sessionId\": \"$SESSION_ID\"
  }"
```

## 🔍 Как проверить что всё работает

### Weather MCP Server работает:
```bash
# Должен быть доступен на порту 3000
curl -s http://localhost:3000 || echo "Сервер запущен"
```

### Основное приложение работает:
```bash
curl -s http://localhost:8080/api/health
# Ответ: {"status":"UP"}
```

### MCP подключен:
```bash
curl -s http://localhost:8080/api/mcp/status
# Ответ: {"connected":true,"serverUrl":"ws://localhost:3000/mcp"}
```

### Список доступных инструментов:
```bash
curl -s http://localhost:8080/api/mcp/tools | jq
# Ответ:
# {
#   "tools": [
#     {
#       "name": "get_weather",
#       "description": "Получить текущую погоду..."
#     },
#     {
#       "name": "get_forecast",
#       "description": "Получить прогноз погоды..."
#     }
#   ]
# }
```

## 🎯 Что происходит под капотом

```
Вы: "Какая погода в Москве?"
  ↓
REST API получает запрос
  ↓
Отправляет в Claude API с tools=[get_weather, get_forecast]
  ↓
Claude: "Нужен инструмент get_weather для Moscow"
  ↓
REST API вызывает Weather MCP Server
  ↓
Weather MCP → Open-Meteo API → реальные данные
  ↓
Данные возвращаются в Claude
  ↓
Claude формирует красивый ответ
  ↓
Вы получаете: "В Москве +5°C, облачно..."
```

## 📚 Полная документация

- **Подробный гайд:** [MCP_INTEGRATION_GUIDE.md](./MCP_INTEGRATION_GUIDE.md)
- **Архитектура проекта:** [CLAUDE.md](./CLAUDE.md)

## 🐛 Проблемы?

**Port already in use:**
```bash
# Найти процесс на порту 3000
lsof -ti:3000 | xargs kill -9

# Найти процесс на порту 8080
lsof -ti:8080 | xargs kill -9
```

**MCP не подключается:**
```bash
# Проверьте что Weather MCP Server запущен
ps aux | grep Weather

# Перезапустите серверы в правильном порядке:
# 1. Сначала Weather MCP
# 2. Потом основное приложение
```

**Claude не использует инструменты:**
- Убедитесь что `MCP_SERVER_URL` установлен
- Проверьте логи при старте: должно быть "Successfully connected to MCP server"
- Проверьте что вопрос действительно о погоде

---

**Готово!** 🎉 Теперь у вас работает полная MCP интеграция!
