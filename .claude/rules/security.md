---
name: security-rules
description: Правила безопасности для проекта AiCompose — API ключи, валидация, OWASP
---

# Security Rules

Правила безопасности для проекта AiCompose.

## API ключи и секреты

### Запрещено
- Хардкодить API ключи в коде
- Коммитить `.env` файлы
- Логировать секреты

### Правильно
- Использовать переменные окружения
- Хранить в `.env` (gitignored)
- Передавать через системные переменные

```kotlin
// ✅ Хорошо
val apiKey = System.getenv("DEEPSEEK_API_KEY")
    ?: throw IllegalStateException("DEEPSEEK_API_KEY not set")

// ❌ Плохо
val apiKey = "sk-1234567890abcdef"
```

## Валидация входных данных

### API endpoints
- Валидировать все входные данные
- Ограничивать размер запросов
- Проверять типы и форматы

```kotlin
// ✅ Хорошо
post("/api/chat") {
    val request = call.receive<ChatRequest>()

    require(request.message.isNotBlank()) { "Message cannot be empty" }
    require(request.message.length <= 10000) { "Message too long" }

    // ...
}
```

### SQL/Command Injection
- Использовать параметризованные запросы
- Не конкатенировать пользовательский ввод в команды

```kotlin
// ✅ Хорошо (Exposed ORM)
Messages.select { Messages.conversationId eq conversationId }

// ❌ Плохо
database.exec("SELECT * FROM messages WHERE conversation_id = '$conversationId'")
```

## OWASP Top 10

### 1. Injection
- Параметризованные запросы
- Валидация входных данных

### 2. Broken Authentication
- Безопасное хранение токенов
- Таймауты сессий

### 3. Sensitive Data Exposure
- Не логировать секреты
- HTTPS для API

### 4. XML External Entities (XXE)
- Не использовать XML парсеры без настройки
- Отключать внешние entities

### 5. Broken Access Control
- Проверять права доступа
- Не доверять client-side валидации

## Логирование

### Что НЕ логировать
- API ключи
- Пароли
- Персональные данные
- Токены авторизации

### Что логировать
- ID запросов (без содержимого секретов)
- Ошибки (без stack trace с секретами)
- Метрики производительности

```kotlin
// ✅ Хорошо
logger.info("Request to DeepSeek API, conversation: $conversationId")

// ❌ Плохо
logger.info("Request with API key: $apiKey")
```

## Зависимости

- Регулярно обновлять зависимости
- Использовать Dependabot/Renovate
- Проверять CVE перед добавлением новых библиотек

---

## Связанные документы

- Правила стиля — см. rules/code-style.md
- Деплой — см. docs/deployment.md
