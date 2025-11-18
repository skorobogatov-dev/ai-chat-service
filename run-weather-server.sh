#!/bin/bash
# Скрипт для запуска Weather MCP сервера

echo "🌤️  Запуск Weather MCP Server..."
echo "📡 Сервер будет доступен на: ws://localhost:3000/mcp"
echo ""
echo "Доступные инструменты:"
echo "  - get_weather (city) - текущая погода"
echo "  - get_forecast (city, days) - прогноз погоды"
echo ""

./gradlew runWeatherMCP
