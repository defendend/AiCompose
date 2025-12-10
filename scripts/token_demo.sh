#!/bin/bash
# Скрипт для демонстрации подсчёта токенов
# Запуск: ./scripts/token_demo.sh [server_url]

SERVER_URL="${1:-http://localhost:8080}"

echo "========================================"
echo "    ДЕМО: Подсчёт токенов DeepSeek"
echo "========================================"
echo ""
echo "Сервер: $SERVER_URL"
echo ""

# 1. Получить лимиты модели
echo "1. ЛИМИТЫ МОДЕЛИ"
echo "----------------"
curl -s "$SERVER_URL/api/tokens/limits" | python3 -m json.tool 2>/dev/null || curl -s "$SERVER_URL/api/tokens/limits"
echo ""
echo ""

# 2. Оценка токенов без отправки в API
echo "2. ОЦЕНКА ТОКЕНОВ (без API)"
echo "---------------------------"

echo "Короткий текст (русский):"
curl -s -X POST "$SERVER_URL/api/tokens/count" \
  -H "Content-Type: application/json" \
  -d '{"text": "Привет, как дела?", "sendToApi": false}' | python3 -m json.tool 2>/dev/null || \
curl -s -X POST "$SERVER_URL/api/tokens/count" \
  -H "Content-Type: application/json" \
  -d '{"text": "Привет, как дела?", "sendToApi": false}'
echo ""

echo "Короткий текст (английский):"
curl -s -X POST "$SERVER_URL/api/tokens/count" \
  -H "Content-Type: application/json" \
  -d '{"text": "Hello, how are you?", "sendToApi": false}' | python3 -m json.tool 2>/dev/null || \
curl -s -X POST "$SERVER_URL/api/tokens/count" \
  -H "Content-Type: application/json" \
  -d '{"text": "Hello, how are you?", "sendToApi": false}'
echo ""
echo ""

# 3. Реальный подсчёт через API (требует DEEPSEEK_API_KEY)
echo "3. РЕАЛЬНЫЙ ПОДСЧЁТ ТОКЕНОВ (через API)"
echo "---------------------------------------"
echo "Отправка короткого запроса..."
curl -s -X POST "$SERVER_URL/api/tokens/count" \
  -H "Content-Type: application/json" \
  -d '{"text": "Расскажи кратко что такое токен в контексте LLM?", "sendToApi": true}' | python3 -m json.tool 2>/dev/null || \
curl -s -X POST "$SERVER_URL/api/tokens/count" \
  -H "Content-Type: application/json" \
  -d '{"text": "Расскажи кратко что такое токен в контексте LLM?", "sendToApi": true}'
echo ""
echo ""

# 4. Полное сравнение (занимает несколько минут)
echo "4. ПОЛНОЕ СРАВНЕНИЕ ТОКЕНОВ"
echo "---------------------------"
echo "ВНИМАНИЕ: Этот тест занимает несколько минут и делает множество API запросов!"
echo ""
read -p "Запустить полное сравнение? (y/n): " confirm
if [ "$confirm" = "y" ] || [ "$confirm" = "Y" ]; then
    echo "Запускаем полное сравнение..."
    curl -s "$SERVER_URL/api/tokens/demo" | python3 -m json.tool 2>/dev/null || curl -s "$SERVER_URL/api/tokens/demo"
else
    echo "Пропущено."
fi

echo ""
echo "========================================"
echo "    ДЕМО ЗАВЕРШЕНО"
echo "========================================"
