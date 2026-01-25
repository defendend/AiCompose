---
name: api-endpoints
description: Описание всех API endpoints проекта AiCompose (chat, logs, health, tokens, reminders, review, support)
---

# API Endpoints

Все endpoints доступны по адресу `http://localhost:8080/api/` (локально) или `http://89.169.190.22/api/` (продакшн).

## Основные endpoints

### POST /api/chat

Отправка сообщения AI агенту.

**Request:**
```json
{
  "message": "текст",
  "conversationId": "optional",
  "responseFormat": "PLAIN|JSON|MARKDOWN",
  "collectionSettings": {
    "mode": "NONE|TECHNICAL_SPEC|DESIGN_BRIEF|PROJECT_SUMMARY|CUSTOM|SOLVE_DIRECT|SOLVE_STEP_BY_STEP|SOLVE_EXPERT_PANEL",
    "customPrompt": "инструкции для CUSTOM режима",
    "resultTitle": "Название результата",
    "enabled": true,
    "customSystemPrompt": "кастомный системный промпт (персонаж агента)"
  },
  "temperature": 0.7
}
```

**Response:**
```json
{
  "message": {
    "id": "uuid",
    "role": "ASSISTANT",
    "content": "ответ",
    "toolCall": {"id": "...", "name": "get_historical_figure", "arguments": "{}"}
  },
  "conversationId": "id"
}
```

### POST /api/chat/stream (SSE)

Streaming версия чата. Возвращает Server-Sent Events.

**Request:** такой же как POST /api/chat

**Response (SSE события):**
```
data: {"type":"START","conversationId":"uuid","messageId":"uuid"}
data: {"type":"CONTENT","content":"Привет"}
data: {"type":"CONTENT","content":"! Как"}
data: {"type":"TOOL_CALL","toolCall":{"id":"...","name":"get_historical_events","arguments":"{}"}}
data: {"type":"TOOL_RESULT","toolResult":"..."}
data: {"type":"DONE","conversationId":"uuid","messageId":"uuid"}
```

**Типы событий:**
- `START` — начало стриминга
- `CONTENT` — часть текста ответа
- `TOOL_CALL` — вызов инструмента
- `TOOL_RESULT` — результат инструмента
- `DONE` — завершение

---

## Логирование

### GET /api/logs

Получение серверных логов.

**Параметры:** `limit`, `offset`, `level`, `category`

**Response:**
```json
{
  "logs": [...],
  "totalCount": 100
}
```

### DELETE /api/logs

Очистка логов на сервере.

---

## Health Checks

### GET /api/health

Простой health check.

**Response:**
```json
{"status": "ok"}
```

### GET /api/health/detailed

Расширенный health check с проверкой всех сервисов.

**Response:**
```json
{
  "status": "healthy|degraded",
  "services": {
    "llm": {"status": "healthy", "message": "DeepSeek API доступен", "latencyMs": 150},
    "storage": {"status": "healthy", "message": "In-Memory storage", "latencyMs": 0}
  },
  "timestamp": 1733680000000
}
```

---

## Токены

### GET /api/tokens/limits

Информация о лимитах модели DeepSeek.

**Response:**
```json
{
  "model": "deepseek-chat",
  "maxContextTokens": 64000,
  "maxOutputTokens": 8192,
  "defaultMaxOutput": 4096,
  "avgCharsPerTokenRu": 2,
  "avgCharsPerTokenEn": 4,
  "notes": ["..."]
}
```

### POST /api/tokens/count

Оценка или реальный подсчёт токенов.

**Request:**
```json
{
  "text": "текст для подсчёта",
  "sendToApi": false
}
```

**Response (sendToApi=false):**
```json
{
  "text": "текст...",
  "length": 100,
  "estimatedTokens": 50,
  "note": "Оценка: ~2 символа/токен для русского"
}
```

**Response (sendToApi=true):**
```json
{
  "testName": "custom",
  "inputLength": 100,
  "estimatedInputTokens": 50,
  "actualPromptTokens": 45,
  "actualCompletionTokens": 120,
  "actualTotalTokens": 165,
  "durationMs": 1500,
  "success": true,
  "response": "ответ модели..."
}
```

### GET /api/tokens/demo

Запуск полного сравнения токенов: короткий, средний, длинный и превышающий лимит запросы.

---

## Напоминания

### GET /api/reminders/notifications

Получить список уведомлений о напоминаниях (для Desktop polling).

**Параметры:**
- `limit` (optional, default: 10) — количество уведомлений

**Response:**
```json
{
  "notifications": [
    {
      "id": "uuid",
      "title": "Проверить систему",
      "description": "Описание (optional)",
      "reminderTime": "2025-12-17T22:47:50Z",
      "notified": true
    }
  ],
  "count": 1
}
```

---

## Code Review

### POST /api/review

Автоматический code review Pull Request.

**Request:**
```json
{
  "owner": "owner",
  "repo": "repo",
  "prNumber": 123,
  "githubToken": "ghp_xxx",
  "postReview": false
}
```

**Response:**
```json
{
  "status": "completed",
  "owner": "owner",
  "repo": "repo",
  "prNumber": 123,
  "review": "Текст ревью...",
  "durationMs": 15000
}
```

---

## Поддержка

### POST /api/support

Ассистент поддержки пользователей.

**Request:**
```json
{
  "question": "Почему не работает авторизация?",
  "ticketId": "TKT-001",
  "userId": "USR-001"
}
```

**Response:**
```json
{
  "answer": "Судя по вашему тикету TKT-001, проблема в сбросе пароля...",
  "ticketId": "TKT-001",
  "durationMs": 3500
}
```

---

## Связанные документы

- Инструменты агента — см. docs/tools.md
- MCP интеграция — см. docs/mcp.md
- Деплой — см. docs/deployment.md
