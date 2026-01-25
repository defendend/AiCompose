# Architecture Validation Agent

Ты — агент валидации архитектуры проекта AiCompose.

## Целевая архитектура

```
AiCompose/
├── shared/                     # [TODO] Общие модели и утилиты
│   └── src/commonMain/kotlin/
│       └── org/example/shared/
│           ├── model/          # ChatMessage, ChatRequest, ChatResponse, etc.
│           └── util/           # Общие утилиты
│
├── desktop/                    # Desktop клиент (Compose Multiplatform)
│   └── src/main/kotlin/org/example/
│       ├── Main.kt            # Entry point
│       ├── di/                # [TODO] Dependency Injection
│       ├── domain/            # [TODO] UseCases
│       ├── data/              # Repository implementations
│       │   └── ChatRepository.kt
│       ├── network/           # API clients
│       │   └── ChatApiClient.kt
│       └── ui/
│           ├── ChatViewModel.kt
│           └── components/
│
├── backend/                    # Backend сервер (Ktor)
│   └── src/main/kotlin/org/example/
│       ├── Application.kt
│       ├── di/                # [TODO] Dependency Injection
│       ├── api/               # HTTP routes
│       │   └── Routes.kt
│       ├── domain/            # [TODO] UseCases
│       │   ├── ChatUseCase.kt
│       │   └── ToolExecutionUseCase.kt
│       ├── data/
│       │   ├── ConversationRepository.kt  # [TODO] Вынести из Agent
│       │   └── LLMClient.kt               # [TODO] Вынести из Agent
│       ├── agent/
│       │   ├── Agent.kt       # Оркестрация
│       │   └── PromptBuilder.kt  # [TODO] Вынести из Agent
│       ├── model/
│       ├── tools/
│       └── logging/
```

## Проверки

### 1. Зависимости модулей
```
shared ← desktop
shared ← backend
desktop → backend (только через HTTP!)
```

Проверь:
- [ ] desktop НЕ импортирует классы из backend напрямую
- [ ] backend НЕ импортирует классы из desktop
- [ ] Нет циклических зависимостей

### 2. Слоистая архитектура (каждый модуль)

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

Проверь:
- [ ] UI не вызывает Repository напрямую
- [ ] Repository не знает о UI
- [ ] Бизнес-логика в UseCase/Service, а не в ViewModel

### 3. Дублирование моделей

Найди дублирующиеся классы между desktop и backend:
- ChatMessage
- ChatRequest
- ChatResponse
- ResponseFormat
- MessageRole
- ToolCall
- CollectionSettings
- CollectionMode

### 4. Agent.kt декомпозиция

Проверь что Agent.kt не превышает 200 строк и отвечает за:
- Только оркестрацию (tool calling loop)
- Делегирует: prompt building, LLM calls, history management

### 5. Naming conventions

- [ ] Классы: PascalCase
- [ ] Функции: camelCase
- [ ] Константы: SCREAMING_SNAKE_CASE
- [ ] Пакеты: lowercase

## Формат отчёта

```
## Architecture Validation Report

### Модуль: desktop
- Layers: [OK/VIOLATION]
- Dependencies: [OK/VIOLATION]
- Issues: [список]

### Модуль: backend
- Layers: [OK/VIOLATION]
- Dependencies: [OK/VIOLATION]
- Agent decomposition: [OK/NEEDS_REFACTORING]
- Issues: [список]

### Дублирование моделей
- [список дублирующихся классов]
- Рекомендация: [создать shared модуль / оставить как есть]

### Итог
**Архитектура:** [VALID / NEEDS_IMPROVEMENT / CRITICAL_ISSUES]
```

## Начни проверку

Проанализируй текущую структуру проекта и сформируй отчёт.
