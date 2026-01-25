---
name: AiCompose
description: Desktop-приложение чат с AI-агентом на Compose Multiplatform + Ktor backend
version: "1.0"
---

# AiCompose

## Язык общения
- Общаемся на **русском языке**

## О проекте

Desktop-приложение на **Compose Multiplatform** (Mac / Windows) — чат с AI-агентом "Профессор Архивариус" (DeepSeek). Агент — увлечённый историк с энциклопедическими знаниями, умеет вызывать инструменты и дополняет их данные интересными историями. Бэкенд развёрнут на сервере Яндекс.Облака.

## Структура проекта

```
AiCompose/
├── desktop/     # Desktop клиент (Compose Multiplatform)
├── shared/      # Общие модели (Kotlin Multiplatform)
├── backend/     # Бэкенд сервер (Ktor)
├── docs/        # Документация
├── scripts/     # Вспомогательные скрипты
└── .github/     # CI/CD workflows
```

**Подробная структура** → [docs/structure.md](docs/structure.md)

## Технологии

| Компонент | Технологии |
|-----------|------------|
| Desktop | Kotlin 2.1.10, Compose Multiplatform 1.7.3, Material3, Koin 4.0.0 |
| Backend | Kotlin 2.1.10, Ktor Server 3.0.3, DeepSeek API, Koin 4.0.0 |
| Хранилище | In-Memory / Redis / PostgreSQL |
| Тесты | MockK, kotlinx-coroutines-test, ktor-test-host (170 тестов) |

## Документация

### Справочники
| Документ | Описание |
|----------|----------|
| [docs/api.md](docs/api.md) | API endpoints |
| [docs/tools.md](docs/tools.md) | Инструменты агента |
| [docs/mcp.md](docs/mcp.md) | MCP интеграция |
| [docs/rag.md](docs/rag.md) | RAG система |
| [docs/testing.md](docs/testing.md) | Тестирование |
| [docs/deployment.md](docs/deployment.md) | Деплой и запуск |
| [docs/changelog.md](docs/changelog.md) | История разработки |

### Правила разработки
| Категория | Правила |
|-----------|---------|
| Общие | [code-style](rules/code-style.md), [security](rules/security.md), [testing](rules/testing.md), [architecture](rules/architecture.md), [git-workflow](rules/git-workflow.md) |
| Backend | [ktor](rules/backend/ktor.md), [di](rules/backend/di.md), [serialization](rules/backend/serialization.md), [agent](rules/backend/agent.md) |
| Desktop | [compose](rules/desktop/compose.md), [viewmodel](rules/desktop/viewmodel.md), [navigation](rules/desktop/navigation.md), [theme](rules/desktop/theme.md) |

### Задачи
- [todos/backlog.md](todos/backlog.md) — бэклог задач

## Быстрый старт

```bash
# Desktop
./gradlew :desktop:run

# Backend (нужен DEEPSEEK_API_KEY)
DEEPSEEK_API_KEY=xxx ./gradlew :backend:run

# Тесты
./gradlew :backend:test
```

**Полная инструкция** → [docs/deployment.md](docs/deployment.md)

## Ключевые особенности

### DeepSeek API
- Модель: `deepseek-chat`
- При сериализации tools обязательно указывать `type: "function"` явно
- LLMToolCall.type может быть null — заполнять "function" при отправке назад

**Подробнее** → [rules/backend/serialization.md](rules/backend/serialization.md)

### Koin DI
При использовании `singleOf()` Koin инжектирует ВСЕ параметры. Для классов с default параметрами используйте `single { }`:

```kotlin
// ❌ Не работает
singleOf(::ChatApiClient)

// ✅ Работает
single { ChatApiClient() }
```

**Подробнее** → [rules/backend/di.md](rules/backend/di.md)

### Агент
- Декомпозирован на: Agent, PromptBuilder, ToolExecutor, LLMClient, ConversationRepository
- Tool calling loop с максимум 10 итерациями
- Сжатие истории при достижении порога сообщений

**Подробнее** → [rules/backend/agent.md](rules/backend/agent.md)

## Claude CLI команды

| Команда | Описание |
|---------|----------|
| `/review` | Code Review изменённых файлов |
| `/architecture-check` | Проверка соответствия архитектуре |
| `/help` | Помощь по проекту |

## Соглашения

- Kotlin style guide
- Комментарии на русском где нужно пояснение бизнес-логики
- Все правила в [rules/](rules/)

---

## Приватная информация

Серверные данные, ключи и локальная конфигурация → [CLAUDE.local.md](CLAUDE.local.md) (gitignored)
