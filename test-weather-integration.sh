#!/bin/bash
# Скрипт для тестирования интеграции с Weather MCP

echo "🧪 Тестирование интеграции Weather MCP + Claude AI"
echo "=================================================="
echo ""

# Проверка что серверы запущены
echo "1️⃣  Проверка Weather MCP Server..."
if ! curl -s --max-time 2 http://localhost:3000 > /dev/null 2>&1; then
    echo "❌ Weather MCP Server не запущен на порту 3000"
    echo "   Запустите в отдельном терминале: ./run-weather-server.sh"
    exit 1
fi
echo "✅ Weather MCP Server работает"
echo ""

echo "2️⃣  Проверка основного приложения..."
if ! curl -s --max-time 2 http://localhost:8080/api/health > /dev/null 2>&1; then
    echo "❌ Основное приложение не запущено на порту 8080"
    echo "   Запустите в отдельном терминале: ./run-main-with-mcp.sh"
    exit 1
fi
echo "✅ Основное приложение работает"
echo ""

# Тест 1: Вопрос о погоде (должен использовать MCP инструмент)
echo "3️⃣  Тест: Вопрос о погоде в Москве"
echo "   Запрос: 'Какая сейчас погода в Москве?'"
echo ""

RESPONSE=$(curl -s -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Какая сейчас погода в Москве?",
    "systemPrompt": "Отвечай кратко на русском языке"
  }')

echo "📊 Ответ от сервера:"
echo "$RESPONSE" | jq '.'
echo ""

# Извлекаем ответ
ANSWER=$(echo "$RESPONSE" | jq -r '.response')
echo "💬 Ответ Claude AI:"
echo "$ANSWER"
echo ""
echo "=================================================="
echo ""

# Тест 2: Обычный вопрос (не должен использовать MCP)
echo "4️⃣  Тест: Обычный вопрос (без MCP)"
echo "   Запрос: 'Что такое Kotlin?'"
echo ""

RESPONSE2=$(curl -s -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Что такое Kotlin?",
    "systemPrompt": "Отвечай в одном предложении"
  }')

ANSWER2=$(echo "$RESPONSE2" | jq -r '.response')
echo "💬 Ответ Claude AI:"
echo "$ANSWER2"
echo ""
echo "=================================================="
echo ""

# Тест 3: Прогноз погоды
echo "5️⃣  Тест: Прогноз погоды"
echo "   Запрос: 'Какой прогноз погоды в Санкт-Петербурге на 3 дня?'"
echo ""

RESPONSE3=$(curl -s -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Какой прогноз погоды в Санкт-Петербурге на 3 дня?",
    "systemPrompt": "Отвечай кратко на русском языке"
  }')

ANSWER3=$(echo "$RESPONSE3" | jq -r '.response')
echo "💬 Ответ Claude AI:"
echo "$ANSWER3"
echo ""
echo "=================================================="
echo ""

echo "✅ Все тесты завершены!"
echo ""
echo "🔍 Как это работало:"
echo "   1. Вы задали вопрос о погоде"
echo "   2. Основное приложение отправило вопрос в Claude API"
echo "   3. Claude получил список инструментов от Weather MCP Server"
echo "   4. Claude определил: 'Нужен инструмент get_weather'"
echo "   5. Основное приложение вызвало Weather MCP Server"
echo "   6. Weather MCP Server запросил данные из Open-Meteo API"
echo "   7. Данные вернулись в основное приложение"
echo "   8. Основное приложение отправило данные обратно Claude"
echo "   9. Claude сформировал понятный ответ на русском"
echo "  10. Вы получили ответ!"
