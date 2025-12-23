#!/bin/bash
# Тест RAG инструментов (локальный backend)

set -e

BASE_URL="http://localhost:8080"

echo "🧪 Тестирование RAG инструментов"
echo ""

# 1. Индексация
echo "1️⃣ Индексация документов из /tmp/local_test_docs"
curl -s -X POST "$BASE_URL/api/chat" \
  -H 'Content-Type: application/json' \
  -d '{
    "message": "Проиндексируй все .md файлы из директории /tmp/local_test_docs. Используй инструмент rag_index_documents с параметром path=/tmp/local_test_docs"
  }' | jq -r '.message.content'

echo ""
echo "---"
echo ""

# 2. Информация об индексе
echo "2️⃣ Информация об индексе"
curl -s -X POST "$BASE_URL/api/chat" \
  -H 'Content-Type: application/json' \
  -d '{
    "message": "Покажи информацию о проиндексированных документах. Используй инструмент rag_index_info"
  }' | jq -r '.message.content'

echo ""
echo "---"
echo ""

# 3. Поиск
echo "3️⃣ Поиск информации про Docker"
curl -s -X POST "$BASE_URL/api/chat" \
  -H 'Content-Type: application/json' \
  -d '{
    "message": "Найди информацию про Docker инструменты в проиндексированных документах. Используй rag_search с query=Docker"
  }' | jq -r '.message.content'

echo ""
echo "---"
echo ""

echo "✅ Тест завершён"
