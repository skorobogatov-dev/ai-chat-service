# 📚 Навигация по документации

## 🚀 С чего начать?

### Для быстрого запуска
**→ [QUICKSTART.md](./QUICKSTART.md)** ⚡
- Запуск за 3 команды
- Примеры curl запросов
- Проверка что всё работает

### Для понимания как это работает
**→ [HOW_IT_WORKS.md](./HOW_IT_WORKS.md)** 🎯
- Простым языком что происходит
- Пошаговый разбор каждого запроса
- Почему это круто

## 📖 Полная документация

### Основная документация
**→ [README.md](./README.md)** 📝
- Общее описание проекта
- Технологии
- Стандартные API endpoints
- Конфигурация

### MCP интеграция
**→ [MCP_INTEGRATION_GUIDE.md](./MCP_INTEGRATION_GUIDE.md)** 🌤️
- Что такое MCP
- Как работает интеграция
- Детальные примеры
- Troubleshooting
- Ресурсы для изучения

### Архитектура
**→ [ARCHITECTURE.md](./ARCHITECTURE.md)** 🏗️
- Диаграммы системы
- Поток данных
- Ключевые компоненты
- Модели данных
- Архитектурные решения

### Для разработчиков
**→ [CLAUDE.md](./CLAUDE.md)** 👨‍💻
- Команды для сборки и запуска
- Структура кода
- Design decisions
- Зависимости

## 🛠️ Скрипты для запуска

### Запуск Weather MCP Server
```bash
./run-weather-server.sh
```

### Запуск основного приложения с MCP
```bash
./run-main-with-mcp.sh
```

### Автоматическое тестирование
```bash
./test-weather-integration.sh
```

## 📂 Структура файлов

```
.
├── README.md                          # Главный README проекта
├── QUICKSTART.md                      # ⚡ Быстрый старт
├── HOW_IT_WORKS.md                    # 🎯 Как это работает
├── MCP_INTEGRATION_GUIDE.md           # 🌤️ Полный гайд по MCP
├── ARCHITECTURE.md                    # 🏗️ Архитектура системы
├── CLAUDE.md                          # 👨‍💻 Для разработчиков
├── DOCS_INDEX.md                      # 📚 Этот файл - навигация
│
├── run-weather-server.sh              # Скрипт запуска MCP сервера
├── run-main-with-mcp.sh               # Скрипт запуска приложения
├── test-weather-integration.sh        # Скрипт тестирования
│
└── src/
    └── main/kotlin/dev/skorobogatov/
        ├── Application.kt                     # Entry point
        ├── services/
        │   ├── ClaudeService.kt              # Claude API интеграция
        │   ├── MCPService.kt                 # MCP клиент
        │   ├── ConversationHistoryService.kt # История диалогов
        │   └── FileStorageService.kt         # Персистентность
        ├── routes/
        │   ├── ChatRoutes.kt                 # REST endpoints
        │   └── MCPRoutes.kt                  # MCP management API
        ├── models/
        │   ├── ChatModels.kt                 # Request/Response DTOs
        │   ├── ClaudeApiModels.kt            # Claude API модели
        │   ├── MCPModels.kt                  # MCP модели
        │   └── ConversationHistory.kt        # История диалогов
        └── mcp/
            └── weather/
                ├── WeatherMCPServer.kt       # Weather MCP Server
                └── WeatherService.kt         # Open-Meteo интеграция
```

## 🎓 Рекомендуемый порядок чтения

### Новичок в проекте

1. **[README.md](./README.md)** - общее понимание
2. **[QUICKSTART.md](./QUICKSTART.md)** - запуск и тестирование
3. **[HOW_IT_WORKS.md](./HOW_IT_WORKS.md)** - как работает MCP
4. **[MCP_INTEGRATION_GUIDE.md](./MCP_INTEGRATION_GUIDE.md)** - детали

### Разработчик

1. **[CLAUDE.md](./CLAUDE.md)** - build commands
2. **[ARCHITECTURE.md](./ARCHITECTURE.md)** - архитектура
3. **[MCP_INTEGRATION_GUIDE.md](./MCP_INTEGRATION_GUIDE.md)** - детали MCP
4. **Код в src/** - implementation details

### Просто хочу попробовать

1. **[QUICKSTART.md](./QUICKSTART.md)** - запуск за 3 шага
2. **[README.md](./README.md)** - примеры использования

## 🔍 Поиск информации

### Как запустить?
→ [QUICKSTART.md](./QUICKSTART.md) - раздел "Запуск за 3 шага"

### Какие API endpoints доступны?
→ [README.md](./README.md) - раздел "API Endpoints"
→ [CLAUDE.md](./CLAUDE.md) - раздел "Testing API"

### Как работает MCP?
→ [HOW_IT_WORKS.md](./HOW_IT_WORKS.md) - пошаговый разбор
→ [ARCHITECTURE.md](./ARCHITECTURE.md) - диаграммы

### Как добавить свой MCP инструмент?
→ [MCP_INTEGRATION_GUIDE.md](./MCP_INTEGRATION_GUIDE.md) - раздел "Следующие шаги"

### Проблемы с запуском?
→ [QUICKSTART.md](./QUICKSTART.md) - раздел "Проблемы?"
→ [MCP_INTEGRATION_GUIDE.md](./MCP_INTEGRATION_GUIDE.md) - раздел "Troubleshooting"

### Как это устроено внутри?
→ [ARCHITECTURE.md](./ARCHITECTURE.md) - полные диаграммы
→ [CLAUDE.md](./CLAUDE.md) - структура кода

## 💡 Примеры использования

### Простой вопрос к Claude
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Что такое Kotlin?"}'
```
→ Подробнее: [README.md](./README.md)

### Вопрос о погоде (с MCP)
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Какая погода в Москве?"}'
```
→ Подробнее: [QUICKSTART.md](./QUICKSTART.md)
→ Как работает: [HOW_IT_WORKS.md](./HOW_IT_WORKS.md)

### Продолжение диалога
→ Примеры: [QUICKSTART.md](./QUICKSTART.md)
→ Механизм: [CLAUDE.md](./CLAUDE.md) - "Механизм истории диалога"

## 🎯 Быстрые ссылки

| Что нужно | Куда смотреть |
|-----------|---------------|
| Запустить проект | [QUICKSTART.md](./QUICKSTART.md) |
| Понять как работает | [HOW_IT_WORKS.md](./HOW_IT_WORKS.md) |
| Примеры API | [README.md](./README.md), [CLAUDE.md](./CLAUDE.md) |
| Архитектура | [ARCHITECTURE.md](./ARCHITECTURE.md) |
| Детали MCP | [MCP_INTEGRATION_GUIDE.md](./MCP_INTEGRATION_GUIDE.md) |
| Build commands | [CLAUDE.md](./CLAUDE.md) |
| Troubleshooting | [MCP_INTEGRATION_GUIDE.md](./MCP_INTEGRATION_GUIDE.md) |

## 📬 Дополнительные ресурсы

- [Model Context Protocol](https://modelcontextprotocol.io/)
- [MCP Kotlin SDK](https://github.com/modelcontextprotocol/kotlin-sdk)
- [Claude API Docs](https://docs.anthropic.com/)
- [Ktor Documentation](https://ktor.io/)

---

**Есть вопросы?** Начните с [HOW_IT_WORKS.md](./HOW_IT_WORKS.md) - там всё объяснено простым языком! 🎉
