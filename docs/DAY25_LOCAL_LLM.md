# День 25: Локальная LLM с Ollama

## Цель
Установить и запустить локальную LLM, проверить доступ через CLI и API.

## Результат
- Ollama установлен и работает
- Модель `qwen2.5:0.5b` скачана (397 MB)
- CLI и API доступны

---

## Установка Ollama

### macOS (Homebrew)
```bash
brew install ollama
brew services start ollama
```

### macOS (Direct)
```bash
curl -fsSL https://ollama.com/install.sh | sh
```

### Проверка установки
```bash
ollama --version
# ollama version is 0.14.2
```

---

## Скачивание модели

### Доступные модели (по размеру)

| Модель | Размер | RAM | Качество |
|--------|--------|-----|----------|
| `qwen2.5:0.5b` | 397 MB | ~1 GB | Базовое |
| `llama3.2:1b` | 1.3 GB | ~2 GB | Хорошее |
| `llama3.2:3b` | 2 GB | ~4 GB | Отличное |
| `mistral:7b` | 4 GB | ~8 GB | Отличное |
| `llama3.1:8b` | 4.7 GB | ~10 GB | Превосходное |

### Скачать модель
```bash
# Маленькая модель для тестов
ollama pull qwen2.5:0.5b

# Качественная модель
ollama pull llama3.2:3b
```

### Список скачанных моделей
```bash
ollama list
# NAME            ID              SIZE      MODIFIED
# qwen2.5:0.5b    a8b0c5157701    397 MB    2 minutes ago
```

---

## CLI Usage

### Интерактивный режим
```bash
ollama run qwen2.5:0.5b
>>> Привет!
>>> /bye
```

### Одиночный запрос
```bash
ollama run qwen2.5:0.5b "Расскажи о себе в 2 предложениях"
```

### Пример ответа
```
Конечно, я могу рассказать вам о себе в двух предложениях:
1. Я - AI助手 (Aid), созданный на основе данных Alibaba Cloud.
2. Я - искусственный интеллект, разработанный для предоставления помощи пользователям.
```

---

## API Usage

Ollama API доступен на `http://localhost:11434`

### Endpoints

| Endpoint | Метод | Описание |
|----------|-------|----------|
| `/api/generate` | POST | Completion API |
| `/api/chat` | POST | Chat API (рекомендуется) |
| `/api/tags` | GET | Список моделей |
| `/api/show` | POST | Информация о модели |

### Chat API (рекомендуется)

```bash
curl http://localhost:11434/api/chat -d '{
  "model": "qwen2.5:0.5b",
  "messages": [
    {"role": "user", "content": "Привет! Как дела?"}
  ],
  "stream": false
}'
```

**Ответ:**
```json
{
  "model": "qwen2.5:0.5b",
  "created_at": "2026-01-19T01:50:00.000Z",
  "message": {
    "role": "assistant",
    "content": "Привет! Я отлично, спасибо за вопрос. Как я могу помочь?"
  },
  "done": true
}
```

### Generate API (completion)

```bash
curl http://localhost:11434/api/generate -d '{
  "model": "qwen2.5:0.5b",
  "prompt": "The capital of France is",
  "stream": false
}'
```

### Streaming (SSE)

```bash
curl http://localhost:11434/api/chat -d '{
  "model": "qwen2.5:0.5b",
  "messages": [{"role": "user", "content": "Count to 5"}],
  "stream": true
}'
```

---

## Kotlin клиент для Ollama

### Зависимости (build.gradle.kts)
```kotlin
dependencies {
    implementation("io.ktor:ktor-client-core:2.3.7")
    implementation("io.ktor:ktor-client-cio:2.3.7")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.7")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.7")
}
```

### OllamaClient.kt
```kotlin
package org.example.llm

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class OllamaChatRequest(
    val model: String,
    val messages: List<OllamaMessage>,
    val stream: Boolean = false
)

@Serializable
data class OllamaMessage(
    val role: String,
    val content: String
)

@Serializable
data class OllamaChatResponse(
    val model: String,
    val message: OllamaMessage,
    val done: Boolean
)

class OllamaClient(
    private val baseUrl: String = "http://localhost:11434"
) {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    suspend fun chat(
        model: String,
        messages: List<OllamaMessage>
    ): OllamaChatResponse {
        return client.post("$baseUrl/api/chat") {
            contentType(ContentType.Application.Json)
            setBody(OllamaChatRequest(model, messages, stream = false))
        }.body()
    }

    suspend fun chat(model: String, userMessage: String): String {
        val response = chat(model, listOf(OllamaMessage("user", userMessage)))
        return response.message.content
    }
}
```

### Использование
```kotlin
suspend fun main() {
    val ollama = OllamaClient()

    val response = ollama.chat(
        model = "qwen2.5:0.5b",
        userMessage = "Расскажи анекдот"
    )

    println(response)
}
```

---

## Управление сервисом

### Запуск/остановка
```bash
# Запустить как сервис
brew services start ollama

# Остановить
brew services stop ollama

# Статус
brew services info ollama

# Запустить в foreground (для отладки)
ollama serve
```

### Очистка
```bash
# Удалить модель
ollama rm qwen2.5:0.5b

# Удалить все модели
rm -rf ~/.ollama/models
```

---

## Сравнение с облачными API

| Параметр | Ollama (локально) | DeepSeek API |
|----------|-------------------|--------------|
| Цена | Бесплатно | $0.14-0.28/1M токенов |
| Латентность | ~100ms | ~500-2000ms |
| Приватность | Полная | Данные на сервере |
| Качество (0.5b) | Базовое | - |
| Качество (7b+) | Отличное | Отличное |
| Offline | Да | Нет |

---

## Демо-скрипт

```bash
#!/bin/bash
# scripts/ollama_demo.sh

echo "=== Ollama Local LLM Demo ==="

# Check if Ollama is running
if ! curl -s http://localhost:11434/api/tags > /dev/null 2>&1; then
    echo "Starting Ollama..."
    brew services start ollama
    sleep 3
fi

# List models
echo -e "\n📦 Available models:"
ollama list

# Test CLI
echo -e "\n🔤 CLI Test:"
ollama run qwen2.5:0.5b "Say hello in Russian" --nowordwrap

# Test API
echo -e "\n🌐 API Test:"
curl -s http://localhost:11434/api/chat -d '{
  "model": "qwen2.5:0.5b",
  "messages": [{"role": "user", "content": "What is 1+1?"}],
  "stream": false
}' | jq -r '.message.content'

echo -e "\n✅ Demo complete!"
```

---

## Troubleshooting

### Ollama не запускается
```bash
# Проверить порт
lsof -i :11434

# Перезапустить
brew services restart ollama
```

### Модель не загружается
```bash
# Проверить место
df -h ~/.ollama

# Переустановить модель
ollama rm qwen2.5:0.5b
ollama pull qwen2.5:0.5b
```

### Медленная генерация
```bash
# Использовать меньшую модель
ollama pull qwen2.5:0.5b  # вместо 7b

# Или включить GPU (если есть)
OLLAMA_GPU_LAYERS=999 ollama serve
```

---

## Следующие шаги

1. **Интеграция с AiCompose** — добавить OllamaClient в backend
2. **Сравнение моделей** — протестировать разные модели на одних задачах
3. **Fine-tuning** — создать кастомную модель с помощью Modelfile
4. **RAG + Ollama** — использовать локальную LLM для RAG запросов
