# Персонализация AI-агента

## Обзор

Система персонализации позволяет AI-агенту (Ollama) знать о вас, ваших привычках, интересах и предпочтениях. Это делает общение более персонализированным и контекстуально-релевантным.

## Быстрый старт

1. **Создайте профиль**: В корне проекта уже есть `user_profile.json` с примером
2. **Отредактируйте**: Заполните своими данными
3. **Запустите сервер**: `./gradlew :server:run`
4. **Общайтесь**: Ollama будет использовать вашу персональную информацию в ответах

## Структура профиля

### Базовая информация
```json
{
  "name": "Ваше имя",
  "role": "Senior Software Engineer",
  "timezone": "Europe/Moscow",
  "preferredLanguage": "ru",
  "location": "Москва, Россия",
  "birthDate": "1990-05-15"
}
```

### Стиль общения
```json
{
  "communicationStyle": {
    "responseLength": "BALANCED",        // BRIEF, BALANCED, DETAILED
    "formality": "FRIENDLY",            // CASUAL, FRIENDLY, PROFESSIONAL
    "technicalLevel": "ADVANCED",       // BEGINNER, INTERMEDIATE, ADVANCED, EXPERT
    "useEmojis": false,
    "preferredTone": "прямолинейный и конструктивный"
  }
}
```

### Рабочий контекст
```json
{
  "workContext": {
    "currentProjects": [
      "AI Chat Service с интеграцией Ollama"
    ],
    "goals": [
      "Освоить продвинутые техники работы с LLM"
    ],
    "challenges": [
      "Оптимизация производительности Ollama"
    ],
    "workingHours": "10:00 - 19:00 MSK",
    "notes": "Предпочитаю локальные решения"
  }
}
```

### Технический стек
```json
{
  "technicalStack": {
    "programmingLanguages": ["Kotlin", "Python", "TypeScript"],
    "frameworks": ["Ktor", "Spring Boot", "React"],
    "tools": ["IntelliJ IDEA", "Git", "Docker"],
    "preferredCodingStyle": "Clean Code, SOLID принципы",
    "operatingSystem": "macOS",
    "ide": "IntelliJ IDEA Ultimate"
  }
}
```

### Личные интересы
```json
{
  "personalInterests": {
    "hobbies": ["Программирование", "Шахматы", "Фотография"],
    "favoriteBooks": ["Чистая архитектура", "1984"],
    "favoriteMovies": ["Матрица", "Интерстеллар"],
    "favoriteTvShows": ["Silicon Valley", "Mr. Robot"],
    "musicGenres": ["Electronic", "Progressive Rock"],
    "favoriteArtists": ["Pink Floyd", "Daft Punk"],
    "sports": ["Бег", "Плавание"],
    "learningGoals": ["Глубокое изучение LLM архитектуры"],
    "favoriteTopics": ["AI", "Квантовые вычисления"],
    "travelDestinations": ["Япония", "Исландия"],
    "favoriteFood": ["Суши", "Итальянская кухня"],
    "pets": ["Кот по имени Байт"]
  }
}
```

## API Endpoints

### GET /api/profile
Получить текущий профиль пользователя

```bash
curl http://localhost:8080/api/profile
```

### GET /api/profile/status
Проверить статус персонализации

```bash
curl http://localhost:8080/api/profile/status
```

Ответ:
```json
{
  "enabled": true,
  "profileLoaded": true,
  "userName": "Денис",
  "message": "Personalization is enabled"
}
```

### POST /api/profile/reload
Перезагрузить профиль из файла (после редактирования)

```bash
curl -X POST http://localhost:8080/api/profile/reload
```

### GET /api/profile/system-prompt
Посмотреть сгенерированный системный промпт (для отладки)

```bash
curl http://localhost:8080/api/profile/system-prompt
```

С базовым промптом:
```bash
curl "http://localhost:8080/api/profile/system-prompt?base=Ты полезный ассистент"
```

## Как работает персонализация

1. **Загрузка профиля**: При запуске сервера `UserProfileService` загружает `user_profile.json`
2. **Генерация промпта**: На основе профиля генерируется персонализированный контекст
3. **Интеграция с Ollama**: Контекст автоматически добавляется в системный промпт
4. **Адаптация ответов**: Ollama учитывает ваши предпочтения в каждом ответе

### Пример сгенерированного промпта

```
# ИНФОРМАЦИЯ О ПОЛЬЗОВАТЕЛЕ

Имя пользователя: Денис
Роль/профессия: Senior Software Engineer
Местоположение: Москва, Россия
Часовой пояс: Europe/Moscow

## Предпочтения в общении:
- Длина ответов: Сбалансированные (средняя длина)
- Формальность: Дружелюбный, но профессиональный
- Технический уровень: Продвинутый (глубокие технические детали)
- Предпочитаемый тон: прямолинейный и конструктивный

## Рабочий контекст:
Текущие проекты: AI Chat Service с интеграцией Ollama
Цели: Освоить продвинутые техники работы с LLM
...

## Технический стек:
Языки программирования: Kotlin, Python, TypeScript
Фреймворки: Ktor, Spring Boot, React
...

## Личные интересы:
Хобби: Программирование, Шахматы
Любимые книги: Чистая архитектура, 1984
Любимые фильмы: Матрица, Интерстеллар
...

---

Используй эту информацию, чтобы:
- Обращаться к пользователю по имени (Денис)
- Адаптировать стиль и формат ответов под предпочтения
- Давать релевантные примеры и рекомендации на основе технического стека
- Учитывать контекст текущих проектов и целей
- Использовать культурные отсылки к любимым книгам, фильмам, музыке
```

## Примеры использования

### Обращение по имени
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Привет! Как дела?",
    "provider": "ollama"
  }'
```

Ollama ответит: "Привет, Денис! Всё отлично, готов помочь..."

### Адаптация к техническому стеку
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Как мне обработать JSON в моем проекте?",
    "provider": "ollama"
  }'
```

Ollama даст пример на Kotlin с использованием kotlinx.serialization (знает ваш стек)

### Учет контекста проектов
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Как оптимизировать производительность?",
    "provider": "ollama"
  }'
```

Ollama даст советы по Ollama оптимизации (знает о вашем текущем проекте)

### Культурные отсылки
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Объясни концепцию многопоточности через метафору",
    "provider": "ollama"
  }'
```

Ollama может использовать отсылки к "Матрице" или другим вашим любимым фильмам

## Отключение персонализации

Если вы хотите временно отключить персонализацию:
1. Переименуйте `user_profile.json` в `user_profile.json.disabled`
2. Перезапустите сервер

В логах появится: `User profile not found. Personalization disabled.`

## Редактирование профиля

1. Отредактируйте `user_profile.json`
2. Вызовите `POST /api/profile/reload` для перезагрузки без перезапуска сервера

## Технические детали

### Файлы
- **Модель**: `server/src/main/kotlin/dev/skorobogatov/models/UserProfile.kt`
- **Сервис**: `server/src/main/kotlin/dev/skorobogatov/services/UserProfileService.kt`
- **Роуты**: `server/src/main/kotlin/dev/skorobogatov/routes/ProfileRoutes.kt`
- **Интеграция**: `server/src/main/kotlin/dev/skorobogatov/services/OllamaChatService.kt:150-154`

### Приоритет системных промптов
1. Пользовательский systemPrompt из ChatRequest (если передан)
2. Пресет (standard/coding)
3. Дефолтный системный промпт

Персонализация применяется **поверх** выбранного промпта.

### Логирование
```
INFO  User profile loaded: Денис
INFO  Ollama chat service configured: http://localhost:11434 (model: llama3.2)
```

## Расширение профиля

Можно добавить собственные поля в `additionalInfo`:
```json
{
  "additionalInfo": "Люблю решать сложные архитектурные задачи. Считаю, что код должен быть самодокументируемым."
}
```

Эта информация будет добавлена в системный промпт в секции "Дополнительная информация".

## Безопасность

⚠️ **Важно**: `user_profile.json` содержит персональную информацию.
- Добавьте в `.gitignore` если не хотите коммитить в репозиторий
- Не храните чувствительные данные (пароли, токены) в профиле
- Профиль используется только локально с Ollama

## Troubleshooting

### Профиль не загружается
- Проверьте, что `user_profile.json` находится в корне проекта
- Проверьте валидность JSON (используйте валидатор)
- Посмотрите логи сервера на наличие ошибок парсинга

### Персонализация не работает
- Проверьте статус: `GET /api/profile/status`
- Убедитесь, что используете `provider: "ollama"` в запросах
- Проверьте, что сервер успешно загрузил профиль (логи)

### Слишком длинный системный промпт
- Уберите лишние поля из профиля
- Сократите списки (например, оставьте топ-5 книг/фильмов)
- Используйте `additionalInfo` для краткого резюме
