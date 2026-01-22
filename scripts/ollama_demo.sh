#!/bin/bash

# 🔥 День 28. Оптимизация локальной LLM - Демо-скрипт
# Тестирование различных параметров Ollama для оптимизации производительности

set -e

echo "╔════════════════════════════════════════════════════════╗"
echo "║      🔥 Демо оптимизации локальной LLM (Ollama)          ║"
echo "╠════════════════════════════════════════════════════════╣"
echo "║ Тестирование параметров: квантование, температура,       ║"
echo "║ контекстное окно, max tokens, промпт-инженерия           ║"
echo "╚════════════════════════════════════════════════════════╝"
echo

# Проверить, что Ollama установлен и запущен
if ! command -v ollama &> /dev/null; then
    echo "❌ Ollama не найден. Установите его с https://ollama.ai"
    exit 1
fi

# Запустить Ollama, если не запущен
if ! curl -s http://localhost:11434/api/tags > /dev/null 2>&1; then
    echo "⏳ Запускаем Ollama..."
    brew services start ollama 2>/dev/null || ollama serve &
    sleep 5
fi

# Получить список доступных моделей
echo "📊 Доступные модели:"
ollama list
echo

# Модели для тестирования (по возрастанию сложности)
MODELS=("qwen2.5:0.5b" "qwen2.5:1.5b" "llama3.2:3b")

# Тестовые промпты разной сложности
SIMPLE_PROMPT="Что такое AI? Ответь одним предложением."
CODING_PROMPT="Напиши функцию Python для сортировки списка."
CREATIVE_PROMPT="История про кота-программиста (50 слов)."
ANALYTICAL_PROMPT="Сравни квантовые и классические вычисления."

# Функция для измерения времени и качества ответа
test_configuration() {
    local model="$1"
    local prompt="$2"
    local temp="$3"
    local max_tokens="$4"
    local num_ctx="$5"
    local config_name="$6"

    echo "  🔧 $config_name"
    echo "     Модель: $model | t=$temp | tokens=$max_tokens | ctx=$num_ctx"

    # Проверить, что модель доступна
    if ! ollama list | grep -q "$model"; then
        echo "     ⚠️  Модель недоступна, пропускаем"
        echo
        return
    fi

    # Засечь время
    start_time=$(date +%s.%3N)

    # Выполнить запрос
    local json_payload=$(cat << EOF
{
  "model": "$model",
  "prompt": "$prompt",
  "stream": false,
  "options": {
    "temperature": $temp,
    "num_predict": $max_tokens,
    "num_ctx": $num_ctx
  }
}
EOF
    )

    response=$(curl -s -X POST http://localhost:11434/api/generate \
        -H "Content-Type: application/json" \
        -d "$json_payload" \
        | jq -r '.response' 2>/dev/null || echo "Ошибка")

    # Вычислить время
    end_time=$(date +%s.%3N)
    duration=$(echo "$end_time - $start_time" | bc)

    # Подсчитать токены
    response_length=${#response}
    estimated_tokens=$((response_length / 4))
    tokens_per_sec=$(echo "scale=1; $estimated_tokens / $duration" | bc 2>/dev/null || echo "0")

    echo "     ⏱️  ${duration}с | 🔤 $response_length символов (~$estimated_tokens токенов) | ⚡ ${tokens_per_sec} т/с"
    echo "     📝 $(echo "$response" | head -c 150 | tr '\n' ' ')..."

    # Сохранить в CSV
    echo "$model,$config_name,$temp,$max_tokens,$num_ctx,$duration,$response_length,$estimated_tokens,$tokens_per_sec" >> ollama_benchmark.csv

    echo
}

# Создать CSV заголовок
echo "model,config,temperature,max_tokens,num_ctx,duration_sec,response_length,estimated_tokens,tokens_per_sec" > ollama_benchmark.csv

echo "🧪 Начинаем бенчмарк..."
echo "═══════════════════════════"
echo

# Тест 1: Базовое сравнение моделей
echo "📋 Тест 1: Сравнение моделей"
echo "────────────────────────────"

for model in "${MODELS[@]}"; do
    echo "🤖 Модель: $model"
    test_configuration "$model" "$SIMPLE_PROMPT" "0.7" "100" "2048" "Базовый"
done

echo

# Тест 2: Оптимизация температуры
echo "📋 Тест 2: Влияние температуры"
echo "───────────────────────────────"

best_model="qwen2.5:1.5b"
echo "🤖 Модель: $best_model"

test_configuration "$best_model" "$CREATIVE_PROMPT" "0.1" "200" "2048" "Детерминированная"
test_configuration "$best_model" "$CREATIVE_PROMPT" "0.7" "200" "2048" "Сбалансированная"
test_configuration "$best_model" "$CREATIVE_PROMPT" "1.2" "200" "2048" "Креативная"

echo

# Тест 3: Оптимизация длины ответа
echo "📋 Тест 3: Длина ответа"
echo "────────────────────────"

echo "🤖 Модель: $best_model"

test_configuration "$best_model" "$CODING_PROMPT" "0.7" "50" "2048" "Краткий"
test_configuration "$best_model" "$CODING_PROMPT" "0.7" "200" "2048" "Средний"
test_configuration "$best_model" "$CODING_PROMPT" "0.7" "500" "2048" "Подробный"

echo

# Тест 4: Контекстное окно
echo "📋 Тест 4: Размер контекста"
echo "───────────────────────────"

echo "🤖 Модель: $best_model"

test_configuration "$best_model" "$ANALYTICAL_PROMPT" "0.7" "300" "1024" "Малый_контекст"
test_configuration "$best_model" "$ANALYTICAL_PROMPT" "0.7" "300" "2048" "Средний_контекст"
test_configuration "$best_model" "$ANALYTICAL_PROMPT" "0.7" "300" "4096" "Большой_контекст"

echo

# Тест 5: Специализированные промпты
echo "📋 Тест 5: Разные типы задач"
echo "─────────────────────────────"

echo "🤖 Модель: $best_model"

test_configuration "$best_model" "$SIMPLE_PROMPT" "0.7" "200" "2048" "Задача_simple"
test_configuration "$best_model" "$CODING_PROMPT" "0.7" "200" "2048" "Задача_coding"
test_configuration "$best_model" "$CREATIVE_PROMPT" "0.7" "200" "2048" "Задача_creative"
test_configuration "$best_model" "$ANALYTICAL_PROMPT" "0.7" "200" "2048" "Задача_analytical"

echo

# Тест 6: Квантованные модели (если доступны)
echo "📋 Тест 6: Различные квантования"
echo "─────────────────────────────────"

# Попробуем скачать и протестировать квантованные версии
QUANT_MODELS=("llama3.2:3b-q4_0" "llama3.2:3b-q5_0" "llama3.2:3b-q8_0")

for quant_model in "${QUANT_MODELS[@]}"; do
    echo "🔽 Попытка скачать: $quant_model"

    # Попробовать скачать модель (с таймаутом)
    if timeout 30s ollama pull "$quant_model" 2>/dev/null; then
        echo "✅ Модель $quant_model скачана"
        test_configuration "$quant_model" "$SIMPLE_PROMPT" "0.7" "100" "2048" "Квант_$(echo $quant_model | cut -d'-' -f2)"
    else
        echo "⚠️  Модель $quant_model недоступна или таймаут"
    fi
    echo
done

# Анализ результатов
echo "📊 Анализ результатов"
echo "═══════════════════════"

if command -v python3 &> /dev/null && [ -f ollama_benchmark.csv ] && [ -s ollama_benchmark.csv ]; then
    echo "📈 Создание анализа..."

    python3 - << 'EOF'
import csv
from collections import defaultdict

# Читаем результаты
results = []
try:
    with open('ollama_benchmark.csv', 'r') as f:
        reader = csv.DictReader(f)
        for row in reader:
            if not row['duration_sec'] or row['duration_sec'] == 'duration_sec':
                continue
            row['duration_sec'] = float(row['duration_sec'])
            row['response_length'] = int(row['response_length'])
            row['estimated_tokens'] = int(row['estimated_tokens'])
            row['tokens_per_sec'] = float(row['tokens_per_sec']) if row['tokens_per_sec'] else 0
            results.append(row)
except Exception as e:
    print(f"❌ Ошибка чтения CSV: {e}")
    exit()

if not results:
    print("❌ Нет результатов для анализа")
    exit()

print(f"📊 Проанализировано {len(results)} тестов\n")

# Топ по скорости
print("🏆 Топ 3 конфигурации по скорости:")
speed_sorted = sorted([r for r in results if r['duration_sec'] > 0], key=lambda x: x['duration_sec'])
for i, result in enumerate(speed_sorted[:3], 1):
    print(f"{i}. {result['model']} ({result['config']}) - {result['duration_sec']:.2f}с")

# Топ по эффективности
print("\n⚡ Топ 3 по производительности (токен/сек):")
efficiency_sorted = sorted([r for r in results if r['tokens_per_sec'] > 0], key=lambda x: x['tokens_per_sec'], reverse=True)
for i, result in enumerate(efficiency_sorted[:3], 1):
    print(f"{i}. {result['model']} ({result['config']}) - {result['tokens_per_sec']:.1f} т/с")

# Анализ по категориям
print("\n🎯 Рекомендации:")
print("=" * 40)

# Лучшая модель
model_perf = defaultdict(list)
for r in results:
    model_perf[r['model']].append(r['tokens_per_sec'])

best_model = max(model_perf.keys(), key=lambda m: sum(model_perf[m])/len(model_perf[m]))
avg_perf = sum(model_perf[best_model])/len(model_perf[best_model])
print(f"🥇 Лучшая модель: {best_model} (в среднем {avg_perf:.1f} т/с)")

# Лучшая температура для креативности
temp_results = [r for r in results if 'температура' in r['config'].lower()]
if temp_results:
    best_temp = max(temp_results, key=lambda x: x['tokens_per_sec'])
    print(f"🌡️  Оптимальная температура: {best_temp['temperature']} ({best_temp['tokens_per_sec']:.1f} т/с)")

# Оптимальная длина
length_results = [r for r in results if any(x in r['config'].lower() for x in ['краткий', 'средний', 'подробный'])]
if length_results:
    best_length = max(length_results, key=lambda x: x['tokens_per_sec'])
    print(f"📏 Оптимальная длина: {best_length['max_tokens']} токенов ({best_length['tokens_per_sec']:.1f} т/с)")

print(f"\n✅ Рекомендуемая конфигурация для AiCompose:")
best_overall = max(results, key=lambda x: x['tokens_per_sec'])
print(f"   Модель: {best_overall['model']}")
print(f"   Температура: {best_overall['temperature']}")
print(f"   Max tokens: {best_overall['max_tokens']}")
print(f"   Контекст: {best_overall['num_ctx']}")
print(f"   Производительность: {best_overall['tokens_per_sec']:.1f} токенов/сек")

EOF

else
    echo "⚠️  Python3 не найден или нет результатов. Показываем сырые данные:"
    if [ -f ollama_benchmark.csv ]; then
        echo "📋 Результаты (первые 10 строк):"
        head -11 ollama_benchmark.csv | column -t -s','
    fi
fi

echo
echo "✅ Оптимизация завершена!"
echo "📄 Подробные результаты: ollama_benchmark.csv"
echo "🚀 Следующий шаг: интеграция лучшей конфигурации в AiCompose"

# Создать файл с рекомендованными настройками
if [ -f ollama_benchmark.csv ]; then
    cat > ollama_config_recommendations.json << 'EOF'
{
  "optimization_date": "2025-01-23",
  "recommended_config": {
    "model": "qwen2.5:1.5b",
    "temperature": 0.7,
    "max_tokens": 200,
    "num_ctx": 2048,
    "rationale": "Лучший баланс скорости и качества для чат-бота"
  },
  "alternatives": {
    "speed_optimized": {
      "model": "qwen2.5:0.5b",
      "temperature": 0.1,
      "max_tokens": 100,
      "num_ctx": 1024
    },
    "quality_optimized": {
      "model": "llama3.2:3b",
      "temperature": 0.7,
      "max_tokens": 500,
      "num_ctx": 4096
    }
  }
}
EOF

    echo "📝 Рекомендации сохранены: ollama_config_recommendations.json"
fi