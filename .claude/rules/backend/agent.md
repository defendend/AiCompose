---
name: agent-rules
description: Правила для Agent, ToolExecutor, PromptBuilder в backend AiCompose
---

# Agent Rules

Правила для AI агента в backend.

## Декомпозиция Agent.kt

Agent.kt должен быть оркестратором (~150 строк):

| Компонент | Ответственность |
|-----------|-----------------|
| `Agent.kt` | Оркестрация tool calling loop |
| `PromptBuilder.kt` | Построение системных промптов |
| `ToolExecutor.kt` | Выполнение tool calls |
| `LLMClient.kt` | HTTP клиент для DeepSeek |
| `ConversationRepository.kt` | Хранение истории |
| `HistoryCompressor.kt` | Сжатие истории диалога |

## Agent структура

```kotlin
class Agent(
    private val llmClient: LLMClient,
    private val repository: ConversationRepository,
    private val promptBuilder: PromptBuilder,
    private val toolExecutor: ToolExecutor,
    private val historyCompressor: HistoryCompressor
) {
    suspend fun chat(request: ChatRequest): ChatResponse {
        // 1. Получить историю
        val history = repository.getHistory(request.conversationId)

        // 2. Построить промпт
        val systemPrompt = promptBuilder.build(request.collectionSettings)

        // 3. Сжать историю если нужно
        val compressedHistory = historyCompressor.compressIfNeeded(history)

        // 4. Вызвать LLM
        val response = llmClient.chat(systemPrompt, compressedHistory, request.message)

        // 5. Обработать tool calls если есть
        val finalResponse = processToolCalls(response)

        // 6. Сохранить в историю
        repository.addMessage(request.conversationId, finalResponse)

        return finalResponse
    }
}
```

## ToolExecutor

```kotlin
class ToolExecutor(
    private val toolRegistry: ToolRegistry
) {
    suspend fun execute(toolCall: ToolCall): ToolResult {
        val tool = toolRegistry.get(toolCall.name)
            ?: return ToolResult(toolCall.id, "Unknown tool: ${toolCall.name}")

        return try {
            val result = tool.execute(toolCall.arguments)
            ToolResult(toolCall.id, result)
        } catch (e: Exception) {
            ToolResult(toolCall.id, "Error: ${e.message}")
        }
    }

    fun fixToolCalls(toolCalls: List<LLMToolCall>): List<LLMToolCall> {
        return toolCalls.map { it.copy(type = it.type ?: "function") }
    }
}
```

## PromptBuilder

```kotlin
class PromptBuilder {
    fun build(settings: CollectionSettings?): String {
        val basePrompt = getBasePrompt()
        val modePrompt = getModePrompt(settings?.mode)
        val customPrompt = settings?.customSystemPrompt

        return customPrompt ?: "$basePrompt\n\n$modePrompt"
    }

    private fun getBasePrompt(): String {
        return """
            Ты — Профессор Архивариус, увлечённый историк...
        """.trimIndent()
    }
}
```

## Tool calling loop

```kotlin
private suspend fun processToolCalls(response: LLMResponse): ChatResponse {
    var currentResponse = response
    var iterations = 0
    val maxIterations = 10

    while (currentResponse.toolCalls.isNotEmpty() && iterations < maxIterations) {
        val toolResults = currentResponse.toolCalls.map { toolCall ->
            toolExecutor.execute(toolCall)
        }

        currentResponse = llmClient.continueWithToolResults(toolResults)
        iterations++
    }

    return ChatResponse(message = currentResponse.message)
}
```

## История диалога

- Хранится на сервере по `conversationId`
- Отправляется полностью в каждом запросе к DeepSeek
- Сжимается при достижении порога сообщений

```kotlin
interface ConversationRepository {
    suspend fun getHistory(conversationId: String): List<ChatMessage>
    suspend fun addMessage(conversationId: String, message: ChatMessage)
    suspend fun replaceHistory(conversationId: String, messages: List<ChatMessage>)
}
```

---

## Связанные документы

- Инструменты агента — см. docs/tools.md
- Архитектура — см. rules/architecture.md
