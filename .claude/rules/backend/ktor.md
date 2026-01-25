---
name: ktor-rules
description: Правила Ktor для backend AiCompose — routes, plugins, таймауты
---

# Ktor Rules

Правила для Ktor backend в проекте AiCompose.

## Routes

### Структура

```kotlin
fun Application.configureRouting() {
    routing {
        route("/api") {
            chatRoutes()
            logRoutes()
            healthRoutes()
        }
    }
}

fun Route.chatRoutes() {
    post("/chat") {
        // ...
    }
}
```

### Обработка ошибок

```kotlin
install(StatusPages) {
    exception<IllegalArgumentException> { call, cause ->
        call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.message))
    }
    exception<Exception> { call, cause ->
        logger.error("Unhandled exception", cause)
        call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Internal error"))
    }
}
```

## Таймауты

| Компонент | Таймаут | Описание |
|-----------|---------|----------|
| Backend к DeepSeek | 120 сек | HTTP запросы к LLM |
| SSE streaming | без таймаута | Server-Sent Events |

```kotlin
val client = HttpClient(CIO) {
    install(HttpTimeout) {
        requestTimeoutMillis = 120_000
        connectTimeoutMillis = 10_000
    }
}
```

## Suspend функции

### Правила
- Все route handlers — suspend
- Не блокировать корутины
- Использовать `withContext(Dispatchers.IO)` для I/O операций

```kotlin
// ✅ Хорошо
post("/chat") {
    val response = withContext(Dispatchers.IO) {
        agent.chat(request)
    }
    call.respond(response)
}

// ❌ Плохо
post("/chat") {
    val response = runBlocking {  // Блокирует поток!
        agent.chat(request)
    }
    call.respond(response)
}
```

## SSE (Server-Sent Events)

```kotlin
post("/chat/stream") {
    call.respondTextWriter(contentType = ContentType.Text.EventStream) {
        agent.chatStream(request).collect { event ->
            write("data: ${Json.encodeToString(event)}\n\n")
            flush()
        }
    }
}
```

## Валидация запросов

```kotlin
post("/chat") {
    val request = call.receive<ChatRequest>()

    require(request.message.isNotBlank()) { "Message cannot be empty" }
    require(request.message.length <= 10_000) { "Message too long" }

    val response = agent.chat(request)
    call.respond(response)
}
```

## Логирование

```kotlin
install(CallLogging) {
    level = Level.INFO
    filter { call -> call.request.path().startsWith("/api") }
}
```

---

## Связанные документы

- Архитектура — см. rules/architecture.md
- API endpoints — см. docs/api.md
