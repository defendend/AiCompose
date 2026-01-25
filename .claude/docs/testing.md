---
name: testing-documentation
description: Тестирование проекта AiCompose — покрытие, стратегия, запуск тестов
---

# Тестирование

## Запуск тестов

```bash
./gradlew :backend:test           # Запуск всех тестов
./gradlew :backend:test --info    # С подробным выводом
```

## Технологии

- **MockK 1.13.13** — мокирование
- **kotlinx-coroutines-test 1.9.0** — тесты корутин
- **ktor-server-test-host 3.0.3** — интеграционные тесты API
- **Koin-test 4.0.0** — тестирование DI

## Покрытие тестами

| Компонент | Тесты | Описание |
|-----------|-------|----------|
| PromptBuilder | 19 | Форматы, режимы сбора, персонажи |
| ConversationRepository (InMemory) | 16 | История, форматы, настройки, изоляция |
| PostgresConversationRepository | 25 | CRUD, tool_calls, персистентность (H2) |
| ToolExecutor | 8 | fixToolCalls, executeToolCall |
| Agent | 14 | Chat flow, tool calling, настройки |
| Routes | 14 | API endpoints + streaming + health checks |
| Tools | 24 | Все инструменты + ToolRegistry |
| AppModule (DI) | 11 | Singleton, зависимости, injection, RepositoryConfig |
| ToolMetadata | 20 | Аннотации, рефлексия, генерация definition |
| ToolRegistry (core) | 15 | Регистрация, инициализация, выполнение |
| RAG Integration | 4 | Индексация, поиск, info, edge cases |

**Всего: 170 тестов**

## Структура тестов

```
backend/src/test/kotlin/org/example/
├── agent/
│   ├── AgentTest.kt
│   ├── PromptBuilderTest.kt
│   └── ToolExecutorTest.kt
├── data/
│   ├── ConversationRepositoryTest.kt      # Тесты InMemory
│   └── PostgresConversationRepositoryTest.kt  # Тесты PostgreSQL (H2)
├── di/
│   └── AppModuleTest.kt  # Тесты DI модуля
├── api/
│   └── RoutesTest.kt
├── rag/
│   └── RagIntegrationTest.kt  # Интеграционный тест RAG системы
└── tools/
    ├── ToolsTest.kt
    └── core/
        ├── ToolMetadataTest.kt
        └── ToolRegistryTest.kt
```

## Демо-скрипты для ручного тестирования

| Скрипт | Описание |
|--------|----------|
| `scripts/compression_demo.sh` | Демо сжатия истории |
| `scripts/token_demo.sh` | Демо подсчёта токенов |
| `scripts/test_rag_local.sh` | Тест RAG инструментов |
| `scripts/rag_chat_demo.sh` | CLI чат-бот с RAG-памятью |
| `scripts/ollama_demo.sh` | Бенчмарк Ollama моделей |

## Тестирование RAG

```bash
# Интеграционный тест
./gradlew :backend:test --tests "org.example.rag.RagIntegrationTest"

# Ручной тест инструментов
./scripts/test_rag_local.sh
```

## Тестирование сжатия истории

```bash
chmod +x scripts/compression_demo.sh
./scripts/compression_demo.sh http://localhost:8080
```

Скрипт отправляет 12 сообщений дважды:
1. Без сжатия — накопление полной истории
2. Со сжатием (threshold=6) — автоматическое резюме

Результат: сравнение общего количества токенов и экономия в %.

---

## Связанные документы

- Архитектура — см. rules/architecture.md
- Правила тестирования — см. rules/testing.md
