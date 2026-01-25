---
name: architecture-rules
description: Архитектурные правила проекта AiCompose — слои, зависимости, модули
---

# Architecture Rules

Архитектурные правила для проекта AiCompose.

## Модульная структура

```
shared ← desktop
shared ← backend
desktop → backend (только через HTTP!)
```

### Правила зависимостей
- ✅ desktop импортирует из shared
- ✅ backend импортирует из shared
- ❌ desktop НЕ импортирует классы из backend напрямую
- ❌ backend НЕ импортирует классы из desktop
- ❌ Нет циклических зависимостей

## Слоистая архитектура

```
UI/API Layer (components, routes)
    ↓
ViewModel/Controller Layer
    ↓
UseCase/Service Layer (бизнес-логика)
    ↓
Repository Layer (доступ к данным)
    ↓
Data Sources (API clients, databases)
```

### Правила слоёв
- UI не вызывает Repository напрямую
- Repository не знает о UI
- Бизнес-логика в UseCase/Service, а не в ViewModel

## Backend структура

```
backend/src/main/kotlin/org/example/
├── Application.kt      # Точка входа
├── api/               # HTTP routes
│   └── Routes.kt
├── di/                # Dependency Injection
│   └── AppModule.kt
├── agent/             # AI агент
│   ├── Agent.kt       # Оркестратор (~150 строк)
│   ├── PromptBuilder.kt
│   ├── ToolExecutor.kt
│   └── HistoryCompressor.kt
├── data/              # Репозитории
│   ├── ConversationRepository.kt
│   ├── LLMClient.kt
│   └── ...
├── model/             # Модели данных
├── tools/             # Инструменты агента
└── logging/           # Логирование
```

## Desktop структура

```
desktop/src/main/kotlin/org/example/
├── Main.kt            # Entry point
├── di/                # Dependency Injection
│   └── AppModule.kt
├── model/             # Модели
├── network/           # API clients
│   └── ChatApiClient.kt
└── ui/
    ├── ChatViewModel.kt
    └── components/
```

## Agent декомпозиция

Agent.kt должен быть оркестратором (~150 строк):
- Делегирует prompt building → `PromptBuilder`
- Делегирует LLM calls → `LLMClient`
- Делегирует history management → `ConversationRepository`
- Делегирует tool execution → `ToolExecutor`

```kotlin
class Agent(
    private val llmClient: LLMClient,
    private val repository: ConversationRepository,
    private val promptBuilder: PromptBuilder,
    private val toolExecutor: ToolExecutor
) {
    suspend fun chat(request: ChatRequest): ChatResponse {
        // Оркестрация без деталей реализации
    }
}
```

## Shared модуль

Общие модели между desktop и backend:
- `ChatMessage`, `ChatRequest`, `ChatResponse`
- `CollectionMode`, `CollectionSettings`
- `MessageRole`, `ResponseFormat`
- `TokenUsage`
- `ToolCall`, `ToolResult`

## Dependency Injection (Koin)

### Правила
- Один модуль на слой/фичу
- Использовать `single` для stateful сервисов
- Использовать `factory` для stateless

```kotlin
// ❌ Не работает — Koin ищет все параметры
singleOf(::ChatApiClient)

// ✅ Работает — явно вызываем конструктор
single { ChatApiClient() }
```

---

## Связанные документы

- Структура проекта — см. docs/structure.md
- Backend правила — см. rules/backend/
- Desktop правила — см. rules/desktop/
