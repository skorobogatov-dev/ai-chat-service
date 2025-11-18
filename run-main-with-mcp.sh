#!/bin/bash
# Скрипт для запуска основного приложения с MCP интеграцией

echo "🚀 Запуск основного приложения с MCP интеграцией..."
echo ""

# Проверка наличия API ключа
if [ -z "$ANTHROPIC_API_KEY" ]; then
    echo "⚠️  ВНИМАНИЕ: ANTHROPIC_API_KEY не установлен!"
    echo "Установите через: export ANTHROPIC_API_KEY='your-key'"
    echo ""
    read -p "Продолжить без ключа? (y/n) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
fi

# Установка MCP_SERVER_URL
export MCP_SERVER_URL="ws://localhost:3000/mcp"

echo "✅ Конфигурация:"
echo "   - REST API: http://localhost:8080"
echo "   - MCP Server: $MCP_SERVER_URL"
echo ""
echo "Подключение к Weather MCP Server произойдет автоматически при старте..."
echo ""

./gradlew run
