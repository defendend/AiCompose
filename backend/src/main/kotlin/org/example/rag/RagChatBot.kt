package org.example.rag

import kotlinx.serialization.Serializable
import org.example.data.LLMClient
import org.example.model.LLMMessage
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * RAG чат-бот с памятью диалога.
 *
 * Возможности:
 * - Хранит историю диалога
 * - При каждом вопросе ищет контекст в базе документов
 * - Возвращает ответ с указанием источников
 */
class RagChatBot(
    private val llmClient: LLMClient,
    private val documentIndex: DocumentIndex = org.example.tools.rag.globalIndex,
    private val maxHistoryMessages: Int = 20,
    private val topK: Int = 3,
    private val minRelevance: Float? = 0.1f
) {
    private val logger = LoggerFactory.getLogger(RagChatBot::class.java)
    private val history = mutableListOf<ChatMessage>()

    /**
     * Сообщение в истории диалога
     */
    @Serializable
    data class ChatMessage(
        val role: String,
        val content: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    /**
     * Ответ чат-бота с источниками
     */
    @Serializable
    data class ChatResponse(
        val answer: String,
        val sources: List<SourceInfo>,
        val usedRag: Boolean,
        val historySize: Int,
        val durationMs: Long
    )

    /**
     * Информация об источнике
     */
    @Serializable
    data class SourceInfo(
        val file: String,
        val relevance: Float,
        val snippet: String
    )

    /**
     * Системный промпт для чат-бота
     */
    private val systemPrompt = """
        Ты полезный AI ассистент с доступом к базе знаний.

        Правила:
        1. Отвечай на вопросы используя предоставленный контекст из документов
        2. Если в контексте нет нужной информации, честно скажи об этом
        3. Ссылайся на источники информации когда используешь их
        4. Помни историю диалога и поддерживай контекст разговора
        5. Будь кратким, но информативным
    """.trimIndent()

    /**
     * Отправить сообщение и получить ответ.
     *
     * @param userMessage Сообщение пользователя
     * @return Ответ с источниками
     */
    suspend fun chat(userMessage: String): ChatResponse {
        logger.info("Новое сообщение: ${userMessage.take(50)}...")
        val startTime = System.currentTimeMillis()

        // 1. Добавляем сообщение пользователя в историю
        history.add(ChatMessage(role = "user", content = userMessage))

        // 2. Ищем релевантные документы через RAG
        val searchResults = if (documentIndex.size() > 0) {
            documentIndex.search(userMessage, topK, minRelevance)
        } else {
            logger.warn("Индекс документов пуст")
            emptyList()
        }

        // 3. Формируем контекст из найденных документов
        val ragContext = if (searchResults.isNotEmpty()) {
            buildString {
                appendLine()
                appendLine("=== КОНТЕКСТ ИЗ ДОКУМЕНТОВ ===")
                searchResults.forEachIndexed { idx, result ->
                    appendLine()
                    appendLine("[Источник ${idx + 1}] ${result.source} (релевантность: ${String.format("%.2f", result.score)})")
                    appendLine(result.content)
                }
                appendLine("=== КОНЕЦ КОНТЕКСТА ===")
                appendLine()
            }
        } else {
            ""
        }

        // 4. Собираем сообщения для LLM с историей диалога
        val messages = mutableListOf<LLMMessage>()

        // Системный промпт
        messages.add(LLMMessage(role = "system", content = systemPrompt))

        // История диалога (ограничиваем размер)
        val historyToSend = history.takeLast(maxHistoryMessages)
        historyToSend.dropLast(1).forEach { msg ->
            messages.add(LLMMessage(role = msg.role, content = msg.content))
        }

        // Текущее сообщение с контекстом
        val enrichedMessage = if (ragContext.isNotEmpty()) {
            """
            $ragContext

            Вопрос пользователя: $userMessage

            Ответь на вопрос, используя информацию из контекста выше и историю диалога.
            """.trimIndent()
        } else {
            userMessage
        }
        messages.add(LLMMessage(role = "user", content = enrichedMessage))

        // 5. Вызываем LLM
        val llmResponse = try {
            llmClient.chat(
                messages = messages,
                tools = emptyList(),
                temperature = 0.7f,
                conversationId = "rag-chat-${System.currentTimeMillis()}"
            )
        } catch (e: Exception) {
            logger.error("Ошибка LLM запроса", e)
            throw e
        }

        val answer = llmResponse.choices.firstOrNull()?.message?.content ?: "Нет ответа"
        val duration = System.currentTimeMillis() - startTime

        // 6. Добавляем ответ в историю
        history.add(ChatMessage(role = "assistant", content = answer))

        // 7. Формируем информацию об источниках
        val sources = searchResults.map { result ->
            SourceInfo(
                file = result.source,
                relevance = result.score,
                snippet = result.content.take(150) + if (result.content.length > 150) "..." else ""
            )
        }

        logger.info("Ответ сформирован за ${duration}ms, источников: ${sources.size}")

        return ChatResponse(
            answer = answer,
            sources = sources,
            usedRag = searchResults.isNotEmpty(),
            historySize = history.size,
            durationMs = duration
        )
    }

    /**
     * Очистить историю диалога
     */
    fun clearHistory() {
        history.clear()
        logger.info("История диалога очищена")
    }

    /**
     * Получить историю диалога
     */
    fun getHistory(): List<ChatMessage> = history.toList()

    /**
     * Размер истории
     */
    fun historySize(): Int = history.size

    /**
     * Проиндексировать документы
     */
    fun indexDocuments(chunks: List<DocumentChunker.DocumentChunk>) {
        documentIndex.clear()
        documentIndex.indexChunks(chunks)
        logger.info("Проиндексировано ${chunks.size} чанков")
    }

    /**
     * Размер индекса
     */
    fun indexSize(): Int = documentIndex.size()
}
