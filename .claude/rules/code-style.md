---
name: code-style
description: Общие правила стиля кода Kotlin для проекта AiCompose
---

# Code Style

Общие правила стиля кода для проекта AiCompose.

## Kotlin Best Practices

### Data Classes
- Использовать `data class` для DTO и моделей
- Immutability где возможно (`val` вместо `var`)

```kotlin
// ✅ Хорошо
data class ChatMessage(
    val id: String,
    val role: MessageRole,
    val content: String
)

// ❌ Плохо
class ChatMessage {
    var id: String = ""
    var role: MessageRole = MessageRole.USER
    var content: String = ""
}
```

### Null Safety
- Правильное использование `?.` и `?:`
- Избегать `!!` где возможно

```kotlin
// ✅ Хорошо
val name = user?.name ?: "Unknown"

// ❌ Плохо
val name = user!!.name
```

### Scope Functions
- `let` — для nullable операций
- `run` — для конфигурации и возврата результата
- `apply` — для конфигурации объекта
- `also` — для side effects

```kotlin
// ✅ Хорошо
user?.let { saveUser(it) }

val config = Config().apply {
    timeout = 30
    retries = 3
}

// ❌ Плохо
if (user != null) {
    saveUser(user)
}
```

### Exception Handling
- Нет подавленных исключений (пустые catch блоки)
- Логировать или пробрасывать исключения

```kotlin
// ✅ Хорошо
try {
    processData()
} catch (e: IOException) {
    logger.error("Failed to process data", e)
    throw ProcessingException("Data processing failed", e)
}

// ❌ Плохо
try {
    processData()
} catch (e: Exception) {
    // ignore
}
```

## Naming Conventions

| Элемент | Стиль | Пример |
|---------|-------|--------|
| Классы | PascalCase | `ChatViewModel` |
| Функции | camelCase | `sendMessage()` |
| Константы | SCREAMING_SNAKE_CASE | `MAX_RETRY_COUNT` |
| Пакеты | lowercase | `org.example.model` |
| Переменные | camelCase | `messageCount` |

## Форматирование

- Максимальная длина строки: 120 символов
- Отступы: 4 пробела
- Пустая строка между функциями
- Группировка импортов: стандартные, сторонние, проектные

## Комментарии

- Комментарии на русском где нужно пояснение бизнес-логики
- Не комментировать очевидный код
- KDoc для публичных API

```kotlin
/**
 * Отправляет сообщение AI агенту и возвращает ответ.
 *
 * @param message Текст сообщения пользователя
 * @param conversationId ID диалога (опционально)
 * @return Ответ агента с метаданными
 */
suspend fun sendMessage(message: String, conversationId: String? = null): ChatResponse
```

---

## Связанные документы

- Правила тестирования — см. rules/testing.md
- Правила безопасности — см. rules/security.md
