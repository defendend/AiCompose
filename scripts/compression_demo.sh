#!/bin/bash

# Демо-скрипт для тестирования сжатия истории диалога
# Сравнивает использование токенов с/без сжатия

BASE_URL="${1:-http://localhost:8080}"

echo "=== Демо сжатия истории диалога ==="
echo "URL: $BASE_URL"
echo ""

# Генерируем уникальные ID для двух диалогов
CONVERSATION_NO_COMPRESSION="test-no-compression-$(date +%s)"
CONVERSATION_WITH_COMPRESSION="test-with-compression-$(date +%s)"

# Сообщения для симуляции длинного диалога
MESSAGES=(
    "Привет! Расскажи мне о Древнем Риме."
    "Кто был самым известным императором?"
    "А что случилось с Юлием Цезарем?"
    "Расскажи про Колизей"
    "Какие гладиаторские бои там проводились?"
    "А что ели древние римляне?"
    "Какая была их одежда?"
    "Расскажи про римские дороги"
    "Как римляне вели войны?"
    "Когда пала Римская империя?"
    "Какое наследие оставил Рим?"
    "А что насчёт римского права?"
)

echo "=== Тест 1: Без сжатия ==="
echo "Conversation ID: $CONVERSATION_NO_COMPRESSION"
echo ""

TOTAL_TOKENS_NO_COMPRESSION=0

for i in "${!MESSAGES[@]}"; do
    MSG="${MESSAGES[$i]}"
    echo "[$((i+1))] Отправляю: $MSG"

    RESPONSE=$(curl -s -X POST "$BASE_URL/api/chat" \
        -H "Content-Type: application/json" \
        -d "{
            \"message\": \"$MSG\",
            \"conversationId\": \"$CONVERSATION_NO_COMPRESSION\"
        }")

    # Извлекаем токены
    PROMPT_TOKENS=$(echo "$RESPONSE" | jq -r '.tokenUsage.promptTokens // 0')
    COMPLETION_TOKENS=$(echo "$RESPONSE" | jq -r '.tokenUsage.completionTokens // 0')
    TOTAL=$((PROMPT_TOKENS + COMPLETION_TOKENS))
    TOTAL_TOKENS_NO_COMPRESSION=$((TOTAL_TOKENS_NO_COMPRESSION + TOTAL))

    echo "    Токены: prompt=$PROMPT_TOKENS, completion=$COMPLETION_TOKENS, total=$TOTAL"
    echo ""

    sleep 1  # Небольшая пауза между запросами
done

echo "=== Итого без сжатия: $TOTAL_TOKENS_NO_COMPRESSION токенов ==="
echo ""
echo "=========================================="
echo ""

echo "=== Тест 2: Со сжатием (threshold=6, keep=3) ==="
echo "Conversation ID: $CONVERSATION_WITH_COMPRESSION"
echo ""

TOTAL_TOKENS_WITH_COMPRESSION=0
TOTAL_SAVED=0

for i in "${!MESSAGES[@]}"; do
    MSG="${MESSAGES[$i]}"
    echo "[$((i+1))] Отправляю: $MSG"

    RESPONSE=$(curl -s -X POST "$BASE_URL/api/chat" \
        -H "Content-Type: application/json" \
        -d "{
            \"message\": \"$MSG\",
            \"conversationId\": \"$CONVERSATION_WITH_COMPRESSION\",
            \"compressionSettings\": {
                \"enabled\": true,
                \"messageThreshold\": 6,
                \"keepRecentMessages\": 3
            }
        }")

    # Извлекаем токены
    PROMPT_TOKENS=$(echo "$RESPONSE" | jq -r '.tokenUsage.promptTokens // 0')
    COMPLETION_TOKENS=$(echo "$RESPONSE" | jq -r '.tokenUsage.completionTokens // 0')
    TOTAL=$((PROMPT_TOKENS + COMPLETION_TOKENS))
    TOTAL_TOKENS_WITH_COMPRESSION=$((TOTAL_TOKENS_WITH_COMPRESSION + TOTAL))

    # Проверяем была ли компрессия
    COMPRESSED=$(echo "$RESPONSE" | jq -r '.compressionStats.lastCompressionResult.compressed // false')
    SAVED=$(echo "$RESPONSE" | jq -r '.compressionStats.lastCompressionResult.estimatedTokensSaved // 0')

    if [ "$COMPRESSED" = "true" ]; then
        TOTAL_SAVED=$((TOTAL_SAVED + SAVED))
        echo "    Токены: prompt=$PROMPT_TOKENS, completion=$COMPLETION_TOKENS, total=$TOTAL"
        echo "    !!! СЖАТИЕ: сэкономлено ~$SAVED токенов"
    else
        echo "    Токены: prompt=$PROMPT_TOKENS, completion=$COMPLETION_TOKENS, total=$TOTAL"
    fi
    echo ""

    sleep 1
done

echo "=== Итого со сжатием: $TOTAL_TOKENS_WITH_COMPRESSION токенов ==="
echo ""
echo "=========================================="
echo ""
echo "=== СРАВНЕНИЕ ==="
echo "Без сжатия:   $TOTAL_TOKENS_NO_COMPRESSION токенов"
echo "Со сжатием:   $TOTAL_TOKENS_WITH_COMPRESSION токенов"
DIFF=$((TOTAL_TOKENS_NO_COMPRESSION - TOTAL_TOKENS_WITH_COMPRESSION))
echo "Разница:      $DIFF токенов"

if [ $TOTAL_TOKENS_NO_COMPRESSION -gt 0 ]; then
    PERCENT=$((DIFF * 100 / TOTAL_TOKENS_NO_COMPRESSION))
    echo "Экономия:     ~${PERCENT}%"
fi

echo ""
echo "Оценочно сэкономлено компрессором: ~$TOTAL_SAVED токенов"
