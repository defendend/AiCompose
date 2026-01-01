#!/bin/bash

# RAG Chat Bot Demo
# Запуск CLI чат-бота с RAG-памятью и выводом источников
#
# Использование:
#   DEEPSEEK_API_KEY=xxx ./scripts/rag_chat_demo.sh [путь_к_документам]
#
# Примеры:
#   DEEPSEEK_API_KEY=xxx ./scripts/rag_chat_demo.sh ./docs
#   DEEPSEEK_API_KEY=xxx ./scripts/rag_chat_demo.sh /Users/user/documents
#
# Команды в чате:
#   /index <путь>  - Индексировать документы
#   /history       - Показать историю диалога
#   /clear         - Очистить историю
#   /status        - Показать статус
#   /help          - Справка
#   /exit          - Выход

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

# Проверяем API ключ
if [ -z "$DEEPSEEK_API_KEY" ]; then
    echo "❌ DEEPSEEK_API_KEY не установлен!"
    echo ""
    echo "Использование:"
    echo "  DEEPSEEK_API_KEY=xxx ./scripts/rag_chat_demo.sh [путь_к_документам]"
    echo ""
    exit 1
fi

cd "$PROJECT_DIR"

echo "🚀 Запуск RAG Chat Bot..."
echo ""

# Запускаем с аргументами (только если они есть)
if [ -n "$1" ]; then
    ./gradlew :backend:runRagChat --args="$*" --console=plain -q
else
    ./gradlew :backend:runRagChat --console=plain -q
fi
