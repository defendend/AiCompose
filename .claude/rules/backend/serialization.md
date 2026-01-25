---
name: serialization-rules
description: Правила kotlinx.serialization для backend AiCompose — DeepSeek API, default values
---

# Serialization Rules

Правила kotlinx.serialization для backend.

## Основные правила

### Default values НЕ сериализуются

```kotlin
@Serializable
data class MyRequest(
    val message: String,
    val temperature: Float = 0.7f  // НЕ будет в JSON если не изменён!
)
```

### Решение для обязательных полей API

Для полей, которые API ТРЕБУЕТ явно, убрать default value:

```kotlin
// DeepSeek требует type: "function" явно
@Serializable
data class Tool(
    val type: String,  // Без default!
    val function: FunctionDefinition
)
```

## DeepSeek API особенности

### Tools serialization

```kotlin
// ✅ Правильно — type указан явно
@Serializable
data class Tool(
    val type: String = "function",  // Нужен в JSON!
    val function: FunctionDefinition
)

// Но default не сериализуется, поэтому:
val tool = Tool(type = "function", function = functionDef)
```

### FunctionParameters

```kotlin
// ✅ Правильно — type: "object" обязателен
@Serializable
data class FunctionParameters(
    val type: String = "object",  // Нужен!
    val properties: Map<String, PropertyDefinition>,
    val required: List<String> = emptyList()
)
```

### ToolCall.type может быть null

```kotlin
@Serializable
data class LLMToolCall(
    val id: String,
    val type: String? = null,  // DeepSeek может вернуть null
    val function: FunctionCall
)

// При отправке назад — заполнять "function"
val fixedToolCall = toolCall.copy(type = toolCall.type ?: "function")
```

## Nullable поля

```kotlin
@Serializable
data class ChatMessage(
    val id: String,
    val content: String,
    val toolCall: ToolCall? = null  // Optional
)
```

## Enum сериализация

```kotlin
@Serializable
enum class MessageRole {
    @SerialName("user") USER,
    @SerialName("assistant") ASSISTANT,
    @SerialName("system") SYSTEM,
    @SerialName("tool") TOOL
}
```

## JSON конфигурация

```kotlin
val json = Json {
    ignoreUnknownKeys = true  // Игнорировать неизвестные поля от API
    encodeDefaults = true      // Включать default values в JSON
    prettyPrint = false
}
```

## Проверка сериализации

```kotlin
@Test
fun `should serialize tool with type`() {
    val tool = Tool(type = "function", function = functionDef)
    val json = Json.encodeToString(tool)

    assertThat(json).contains("\"type\":\"function\"")
}
```

---

## Связанные документы

- Ktor правила — см. rules/backend/ktor.md
- API endpoints — см. docs/api.md
