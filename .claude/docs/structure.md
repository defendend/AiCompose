---
name: project-structure
description: Структура проекта AiCompose — модули, папки, ключевые файлы
---

# Структура проекта

AiCompose — мультимодульный проект на Kotlin.

## Модули

| Модуль | Описание |
|--------|----------|
| `desktop/` | Desktop клиент (Compose Multiplatform) |
| `backend/` | Backend сервер (Ktor) |
| `shared/` | Общие модели (Kotlin Multiplatform) |

## Дерево проекта

```
AiCompose/
├── desktop/                    # Desktop клиент (Compose Multiplatform)
│   └── src/main/kotlin/org/example/
│       ├── Main.kt
│       ├── di/AppModule.kt           # Koin DI модуль
│       ├── model/
│       │   ├── ChatModels.kt
│       │   ├── CollectionMode.kt
│       │   ├── ModelComparisonModels.kt
│       │   └── ServerLogModels.kt
│       ├── network/
│       │   ├── ChatApiClient.kt
│       │   └── HuggingFaceApiClient.kt
│       ├── demo/
│       │   └── HuggingFaceTokenDemo.kt
│       ├── logging/{LogEntry.kt, AppLogger.kt}
│       └── ui/
│           ├── ChatViewModel.kt
│           ├── McpViewModel.kt
│           ├── SupportViewModel.kt
│           ├── ModelComparisonViewModel.kt
│           ├── theme/AppTheme.kt
│           └── components/
│               ├── ChatScreen.kt
│               ├── LogWindow.kt
│               ├── McpServersScreen.kt
│               ├── ModelComparisonScreen.kt
│               ├── ServerLogWindow.kt
│               ├── SettingsScreen.kt
│               └── SupportScreen.kt
│
├── shared/                     # Общие модели (Kotlin Multiplatform)
│   └── src/commonMain/kotlin/org/example/shared/model/
│       ├── ChatMessage.kt, ChatRequest.kt, ChatResponse.kt
│       ├── CollectionMode.kt, CollectionSettings.kt
│       ├── CompressionSettings.kt
│       ├── MessageRole.kt, ResponseFormat.kt
│       ├── TokenUsage.kt
│       └── ToolCall.kt, ToolResult.kt
│
├── backend/                    # Backend сервер (Ktor)
│   └── src/main/kotlin/org/example/
│       ├── Application.kt
│       ├── api/Routes.kt
│       ├── di/AppModule.kt
│       ├── agent/
│       │   ├── Agent.kt              # Оркестратор (~150 строк)
│       │   ├── PromptBuilder.kt
│       │   ├── ToolExecutor.kt
│       │   └── HistoryCompressor.kt
│       ├── data/
│       │   ├── ConversationRepository.kt
│       │   ├── RedisConversationRepository.kt
│       │   ├── PostgresConversationRepository.kt
│       │   ├── ReminderRepository.kt
│       │   ├── schema/Tables.kt
│       │   └── LLMClient.kt
│       ├── model/
│       │   ├── Models.kt
│       │   ├── LogModels.kt
│       │   └── ReminderModels.kt
│       ├── logging/ServerLogger.kt
│       ├── scheduler/ReminderScheduler.kt
│       ├── integrations/WeatherMcpClient.kt
│       ├── rag/
│       │   ├── DocumentChunker.kt
│       │   ├── SimpleEmbeddings.kt
│       │   ├── DocumentIndex.kt
│       │   ├── RerankerService.kt
│       │   ├── RagQueryService.kt
│       │   └── RagChatBot.kt
│       ├── support/
│       │   ├── SupportModels.kt
│       │   └── SupportRepository.kt
│       └── tools/
│           ├── annotations/ToolAnnotations.kt
│           ├── core/{AgentTool.kt, ToolMetadata.kt, ToolRegistry.kt}
│           ├── historical/
│           ├── pipeline/PipelineTools.kt
│           ├── rag/{RagTools.kt, RagQueryTools.kt}
│           ├── support/SupportTools.kt
│           ├── system/CurrentTimeTool.kt
│           └── McpToolsAdapter.kt
│   └── src/test/kotlin/org/example/  # Unit и интеграционные тесты
│
├── docs/
│   ├── ARCHITECTURE_IMPROVEMENT_PLAN.md
│   ├── RAG_DEMO.md
│   ├── RAG_USAGE_EXAMPLES.md
│   └── OLLAMA_OPTIMIZATION_GUIDE.md
│
├── scripts/
│   ├── compression_demo.sh
│   ├── token_demo.sh
│   ├── test_rag_local.sh
│   ├── rag_chat_demo.sh
│   └── ollama_demo.sh
│
└── .github/workflows/
    ├── ci.yml
    └── deploy-backend.yml
```

## Технологии

### Desktop
- **Kotlin 2.1.10** + **Compose Multiplatform 1.7.3**
- **Material3** для UI (тёмная тема)
- **Ktor Client 3.0.3** для HTTP
- **Kotlinx Serialization** для JSON
- **Koin 4.0.0** — Dependency Injection

### Backend
- **Kotlin 2.1.10** + **Ktor Server 3.0.3**
- **DeepSeek API** (модель deepseek-chat)
- **Logback** для логирования
- **JVM Toolchain**: 21
- **Koin 4.0.0** — Dependency Injection
- **Lettuce 6.3.2** — async Redis client
- **MCP Kotlin SDK 0.8.1** — Model Context Protocol

### Тестирование
- **MockK 1.13.13** — мокирование
- **kotlinx-coroutines-test 1.9.0** — тесты корутин
- **ktor-server-test-host 3.0.3** — интеграционные тесты API
- **Koin-test 4.0.0** — тестирование DI

---

## Связанные документы

- Архитектура — см. rules/architecture.md
- Тестирование — см. docs/testing.md
- Деплой — см. docs/deployment.md
