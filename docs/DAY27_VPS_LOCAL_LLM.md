# День 27: Локальная LLM на VPS

## Цель
Интегрировать локальную модель (Ollama) на удалённом сервере для работы без внешних API.

## Результат
- Backend использует локальный Ollama вместо DeepSeek API
- Приложение отвечает на запросы через локальную модель
- Нет зависимости от внешних LLM API
- Полная приватность данных

---

## Архитектура

```
┌─────────────────────────────────────────────────────────┐
│                  Desktop Client                         │
│                (любое устройство)                       │
└────────────────────────┬────────────────────────────────┘
                         │ HTTP
                         ▼
┌─────────────────────────────────────────────────────────┐
│                   VPS Server                            │
│                 (89.169.190.22)                         │
│  ┌─────────────────────────────────────────────────┐   │
│  │              aicompose.service                   │   │
│  │           (Ktor Backend Server)                  │   │
│  │                                                  │   │
│  │  LLM_PROVIDER=ollama                            │   │
│  │  ┌────────────────┐   ┌────────────────────┐   │   │
│  │  │ OllamaLLMClient│──►│    Ollama API      │   │   │
│  │  │                │   │  localhost:11434   │   │   │
│  │  └────────────────┘   └────────────────────┘   │   │
│  └─────────────────────────────────────────────────┘   │
│                                                         │
│  ┌─────────────────────────────────────────────────┐   │
│  │              ollama.service                      │   │
│  │            (qwen2.5:0.5b)                        │   │
│  └─────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
```

---

## Установка Ollama на VPS

### 1. Установка
```bash
ssh defendend@89.169.190.22
curl -fsSL https://ollama.com/install.sh | sh
```

### 2. Проверка сервиса
```bash
systemctl status ollama
# Должен быть active (running)
```

### 3. Скачивание модели
```bash
ollama pull qwen2.5:0.5b
```

### 4. Тест
```bash
curl http://localhost:11434/api/chat \
  -d '{"model": "qwen2.5:0.5b", "messages": [{"role": "user", "content": "Hello"}], "stream": false}'
```

---

## Новые файлы

### `backend/src/main/kotlin/org/example/data/OllamaLLMClient.kt`

Клиент для локального Ollama API, реализующий интерфейс `LLMClient`:

```kotlin
class OllamaLLMClient(
    private val baseUrl: String = "http://localhost:11434",
    private val defaultModel: String = "qwen2.5:0.5b"
) : LLMClient {
    // Реализует chat(), chatStream(), healthCheck()
}
```

**Особенности:**
- Конвертация LLMMessage ↔ OllamaChatMessage
- Поддержка streaming ответов
- Подсчёт токенов (prompt_eval_count, eval_count)
- Timeout 5 минут для CPU-only серверов

---

## Конфигурация LLM провайдера

### Переменные окружения

| Переменная | Описание | По умолчанию |
|------------|----------|--------------|
| `LLM_PROVIDER` | Тип провайдера (`deepseek` или `ollama`) | `deepseek` |
| `OLLAMA_URL` | URL Ollama API | `http://localhost:11434` |
| `OLLAMA_MODEL` | Модель для использования | `qwen2.5:0.5b` |

### Пример `.env` файла
```bash
# LLM Provider
LLM_PROVIDER=ollama
OLLAMA_URL=http://localhost:11434
OLLAMA_MODEL=qwen2.5:0.5b

# Storage
STORAGE_TYPE=postgres
DB_URL=jdbc:postgresql://localhost:5432/aicompose
```

### Код конфигурации (AppModule.kt)
```kotlin
enum class LLMProvider {
    DEEPSEEK,
    OLLAMA
}

data class LLMConfig(
    val provider: LLMProvider = LLMProvider.DEEPSEEK,
    val ollamaUrl: String = "http://localhost:11434",
    val ollamaModel: String = "qwen2.5:0.5b"
)

// DI модуль выбирает клиент на основе конфигурации
single<LLMClient> {
    when (llmConfig.provider) {
        LLMProvider.OLLAMA -> OllamaLLMClient(...)
        LLMProvider.DEEPSEEK -> DeepSeekClient(...)
    }
}
```

---

## Деплой

### 1. Сборка JAR
```bash
./gradlew :backend:buildFatJar
```

### 2. Копирование на сервер
```bash
scp backend/build/libs/aicompose-backend.jar defendend@89.169.190.22:/home/defendend/
```

### 3. Обновление .env
```bash
ssh defendend@89.169.190.22 'sudo bash -c "cat >> /opt/aicompose/.env << EOF
LLM_PROVIDER=ollama
OLLAMA_URL=http://localhost:11434
OLLAMA_MODEL=qwen2.5:0.5b
EOF"'
```

### 4. Перезапуск сервиса
```bash
ssh defendend@89.169.190.22 'sudo cp /home/defendend/aicompose-backend.jar /opt/aicompose/ && sudo systemctl restart aicompose'
```

### 5. Проверка логов
```bash
ssh defendend@89.169.190.22 'sudo journalctl -u aicompose -f'
```

Ожидаемый вывод:
```
INFO [Koin] - 🤖 Using Ollama LLM: http://localhost:11434 (model: qwen2.5:0.5b)
```

---

## Тестирование

### Простой запрос
```bash
curl -X POST http://89.169.190.22/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Hello"}'
```

### Ожидаемый ответ
```json
{
  "message": {
    "id": "uuid",
    "role": "ASSISTANT",
    "content": "Hello! How can I help you today?",
    "tokenUsage": {
      "promptTokens": 939,
      "completionTokens": 10,
      "totalTokens": 949
    }
  },
  "conversationId": "uuid"
}
```

### Health check
```bash
curl http://89.169.190.22/api/health/detailed
```

---

## Сравнение DeepSeek vs Ollama

| Аспект | DeepSeek API | Ollama (локальный) |
|--------|--------------|-------------------|
| Интернет | Требуется | Не нужен |
| Приватность | Данные уходят наружу | Полная локальность |
| Скорость | Зависит от сети | ~10-50 tok/s (CPU) |
| Качество | deepseek-chat (высокое) | qwen2.5:0.5b (базовое) |
| Стоимость | API токены | Бесплатно |
| Tools | Поддерживает | Не поддерживает* |

*Ollama поддерживает tools в некоторых моделях, но qwen2.5:0.5b не поддерживает.

---

## Доступные модели

```bash
ollama list
```

| Модель | Размер | RAM | Рекомендация |
|--------|--------|-----|--------------|
| `qwen2.5:0.5b` | 397 MB | ~1 GB | Быстрая, базовое качество |
| `qwen2.5:1.5b` | 935 MB | ~2 GB | Баланс скорость/качество |
| `llama3.2:1b` | 1.3 GB | ~2 GB | Хорошее качество |
| `llama3.2:3b` | 2.0 GB | ~4 GB | Отличное качество |

---

## Переключение между провайдерами

### На DeepSeek (по умолчанию)
```bash
# .env
LLM_PROVIDER=deepseek
DEEPSEEK_API_KEY=sk-xxx
```

### На Ollama (локальный)
```bash
# .env
LLM_PROVIDER=ollama
OLLAMA_URL=http://localhost:11434
OLLAMA_MODEL=qwen2.5:0.5b
```

После изменения:
```bash
sudo systemctl restart aicompose
```

---

## Troubleshooting

### Ollama недоступен
```bash
# Проверить статус
systemctl status ollama

# Перезапустить
sudo systemctl restart ollama

# Проверить порт
curl http://localhost:11434/api/tags
```

### Медленные ответы
- Использовать меньшую модель (qwen2.5:0.5b)
- Увеличить timeout в OllamaLLMClient

### Ошибка парсинга JSON
- Убедиться что `stream: false` включён в запрос
- Проверить `encodeDefaults = true` в Json конфигурации

---

## Файлы

| Файл | Изменение |
|------|-----------|
| `backend/.../data/OllamaLLMClient.kt` | Новый — клиент Ollama API |
| `backend/.../di/AppModule.kt` | LLMConfig, LLMProvider enum |
| `docs/DAY27_VPS_LOCAL_LLM.md` | Документация |
