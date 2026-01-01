#!/bin/bash

# RAG Chat Bot (Remote) - работает через сервер
# НЕ требует локального DEEPSEEK_API_KEY
#
# Использование:
#   ./scripts/rag_chat_remote.sh
#   ./scripts/rag_chat_remote.sh http://localhost:8080  # локальный сервер

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_DIR"

echo "🚀 Запуск RAG Chat Bot (Remote)..."
echo "   Используется API ключ на сервере"
echo ""

# Запускаем с аргументами (только если они есть)
if [ -n "$1" ]; then
    ./gradlew :backend:runRagChatRemote --args="$*" --console=plain -q
else
    ./gradlew :backend:runRagChatRemote --console=plain -q
fi
