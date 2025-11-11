# AI Chat - Kotlin Multiplatform

Проект успешно мигрирован на Kotlin Multiplatform с Compose Multiplatform!

## Структура проекта

### Модули

- **server** (JVM) - Ktor сервер с REST API
- **shared** (KMP: JVM + JS + Android) - Общие модели и API клиент
- **composeApp** (JS + Android) - UI на Compose Multiplatform

## Технологии

- Kotlin 2.0.20
- Ktor 2.3.12
- Compose Multiplatform 1.7.0
- Kotlin Multiplatform
- Material 3

## Запуск проекта

### 1. Запуск сервера

```bash
# Собрать и запустить сервер
./gradlew :server:run

# Сервер запустится на http://localhost:8080
```

### 2. Запуск Web версии (JS)

```bash
# В отдельном терминале (сервер должен быть запущен)
./gradlew :composeApp:jsBrowserDevelopmentRun

# Web версия откроется в браузере автоматически
```

### 3. Запуск Android версии

```bash
# Подключите Android устройство или запустите эмулятор
./gradlew :composeApp:installDebug

# Или откройте проект в Android Studio и запустите composeApp
```

## Сборка проекта

```bash
# Собрать все модули
./gradlew build

# Собрать отдельные модули
./gradlew :server:build
./gradlew :shared:build
./gradlew :composeApp:build
```

## Возможности

### Shared модуль (KMP)
- ✅ Общие модели данных для всех платформ
- ✅ HTTP клиент для API запросов
- ✅ Работает на JVM, JS и Android

### Server модуль (JVM)
- ✅ REST API на Ktor
- ✅ Интеграция с Claude AI (Anthropic API)
- ✅ CORS поддержка
- ✅ Обработка ошибок

### ComposeApp модуль (JS + Android)
- ✅ Единый UI код для веба и Android
- ✅ Material 3 дизайн
- ✅ Адаптивная верстка
- ✅ Состояния загрузки и ошибок

## Конфигурация

### Переменные окружения

```bash
export ANTHROPIC_API_KEY="your-api-key"
```

Или настройте в `server/src/main/resources/application.conf`

## Архитектура

```
.
├── server/                      # JVM сервер
│   ├── src/main/kotlin/
│   │   └── dev/skorobogatov/
│   │       ├── Application.kt   # Entry point
│   │       ├── models/          # Серверные модели (Claude API)
│   │       ├── services/        # ClaudeService
│   │       ├── routes/          # API routes
│   │       └── plugins/         # Ktor плагины
│   └── build.gradle.kts
│
├── shared/                      # Общий KMP модуль
│   ├── src/
│   │   ├── commonMain/kotlin/
│   │   │   └── dev/skorobogatov/
│   │   │       ├── models/      # ChatRequest, ChatResponse
│   │   │       └── api/         # ChatApi клиент
│   │   ├── jvmMain/
│   │   ├── jsMain/
│   │   └── androidMain/
│   └── build.gradle.kts
│
└── composeApp/                  # Compose Multiplatform UI
    ├── src/
    │   ├── commonMain/kotlin/
    │   │   └── dev/skorobogatov/ui/
    │   │       └── App.kt       # Общий UI код
    │   ├── jsMain/kotlin/
    │   │   └── main.kt          # JS entry point
    │   ├── androidMain/kotlin/
    │   │   └── MainActivity.kt  # Android entry point
    │   └── jsMain/resources/
    │       └── index.html
    └── build.gradle.kts
```

## Развитие проекта

### Добавление новых платформ

Проект готов для расширения на:
- iOS (добавить `iosTarget` в shared и composeApp)
- Desktop (JVM Desktop с Compose)
- WASM (экспериментально)

### Следующие шаги

1. Добавить сохранение истории чата
2. Реализовать поддержку файлов/изображений
3. Добавить настройки и темы
4. Реализовать offline поддержку

## Тестирование

```bash
# Запустить тесты
./gradlew test

# Тесты для отдельных модулей
./gradlew :server:test
./gradlew :shared:allTests
```

## Production Build

```bash
# Server
./gradlew :server:installDist
./server/build/install/server/bin/server

# JS (production)
./gradlew :composeApp:jsBrowserProductionWebpack

# Android (release)
./gradlew :composeApp:assembleRelease
```

## Поддержка

Для вопросов и предложений создавайте issues в репозитории.
