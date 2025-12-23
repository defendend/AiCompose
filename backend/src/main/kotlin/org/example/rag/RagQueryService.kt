package org.example.rag

import kotlinx.serialization.Serializable
import org.example.data.LLMClient
import org.example.model.LLMMessage
import org.example.model.LLMRequest
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Сервис для RAG-запросов с автоматическим обогащением контекста.
 *
 * Реализует pipeline:
 * 1. Вопрос пользователя
 * 2. Поиск релевантных чанков в индексе
 * 3. Объединение чанков с вопросом
 * 4. Запрос к LLM с обогащённым контекстом
 *
 * ВАЖНО: Использует globalIndex из RagTools, который должен быть проиндексирован
 * через rag_index_documents перед использованием RAG запросов.
 */
class RagQueryService(
    private val llmClient: LLMClient,
    private val index: DocumentIndex = org.example.tools.rag.globalIndex
) {
    private val logger = LoggerFactory.getLogger(RagQueryService::class.java)

    /**
     * Запрос С RAG: поиск релевантных чанков + LLM.
     */
    suspend fun queryWithRag(
        question: String,
        topK: Int = 3,
        systemPrompt: String = "Ты полезный AI ассистент. Отвечай на вопросы на основе предоставленного контекста."
    ): RagQueryResult {
        logger.info("RAG запрос: $question (topK=$topK)")

        // 1. Поиск релевантных чанков
        val searchResults = if (index.size() > 0) {
            index.search(question, topK)
        } else {
            logger.warn("Индекс пуст, пропускаем поиск")
            emptyList()
        }

        // 2. Формирование контекста из найденных чанков
        val context = if (searchResults.isNotEmpty()) {
            buildString {
                appendLine("Релевантная информация из документов:")
                appendLine()
                searchResults.forEachIndexed { idx, result ->
                    appendLine("--- Документ ${idx + 1} (релевантность: ${String.format("%.2f", result.score)}) ---")
                    appendLine("Источник: ${result.source}")
                    appendLine()
                    appendLine(result.content)
                    appendLine()
                }
            }
        } else {
            null
        }

        // 3. Объединение контекста с вопросом
        val enrichedQuestion = if (context != null) {
            """
            $context

            Вопрос пользователя: $question

            Ответь на вопрос используя информацию из документов выше. Если в документах нет нужной информации, скажи об этом честно.
            """.trimIndent()
        } else {
            question
        }

        // 4. Запрос к LLM
        val startTime = System.currentTimeMillis()
        val llmResponse = try {
            llmClient.chat(
                messages = listOf(
                    LLMMessage(role = "system", content = systemPrompt),
                    LLMMessage(role = "user", content = enrichedQuestion)
                ),
                tools = emptyList(),
                temperature = null,
                conversationId = "rag-query-${System.currentTimeMillis()}"
            )
        } catch (e: Exception) {
            logger.error("Ошибка LLM запроса с RAG", e)
            throw e
        }
        val duration = System.currentTimeMillis() - startTime

        val answer = llmResponse.choices.firstOrNull()?.message?.content ?: "Нет ответа"

        logger.info("RAG ответ получен за ${duration}ms, найдено чанков: ${searchResults.size}")

        return RagQueryResult(
            question = question,
            answer = answer,
            usedRag = true,
            foundChunks = searchResults.size,
            relevanceScores = searchResults.map { it.score },
            sources = searchResults.map { it.source }.distinct(),
            durationMs = duration,
            promptTokens = llmResponse.usage?.prompt_tokens,
            completionTokens = llmResponse.usage?.completion_tokens
        )
    }

    /**
     * Запрос БЕЗ RAG: обычный LLM без дополнительного контекста.
     */
    suspend fun queryWithoutRag(
        question: String,
        systemPrompt: String = "Ты полезный AI ассистент. Отвечай на вопросы кратко и точно."
    ): RagQueryResult {
        logger.info("Запрос БЕЗ RAG: $question")

        val startTime = System.currentTimeMillis()
        val llmResponse = try {
            llmClient.chat(
                messages = listOf(
                    LLMMessage(role = "system", content = systemPrompt),
                    LLMMessage(role = "user", content = question)
                ),
                tools = emptyList(),
                temperature = null,
                conversationId = "no-rag-query-${System.currentTimeMillis()}"
            )
        } catch (e: Exception) {
            logger.error("Ошибка LLM запроса без RAG", e)
            throw e
        }
        val duration = System.currentTimeMillis() - startTime

        val answer = llmResponse.choices.firstOrNull()?.message?.content ?: "Нет ответа"

        logger.info("Ответ БЕЗ RAG получен за ${duration}ms")

        return RagQueryResult(
            question = question,
            answer = answer,
            usedRag = false,
            foundChunks = 0,
            relevanceScores = emptyList(),
            sources = emptyList(),
            durationMs = duration,
            promptTokens = llmResponse.usage?.prompt_tokens,
            completionTokens = llmResponse.usage?.completion_tokens
        )
    }

    /**
     * Сравнение ответов с RAG и без RAG.
     */
    suspend fun compareAnswers(
        question: String,
        topK: Int = 3
    ): RagComparisonResult {
        logger.info("Сравнение ответов для вопроса: $question")

        val withoutRag = queryWithoutRag(question)
        val withRag = queryWithRag(question, topK)

        return RagComparisonResult(
            question = question,
            withoutRag = withoutRag,
            withRag = withRag,
            totalDurationMs = withoutRag.durationMs + withRag.durationMs
        )
    }
}

/**
 * Результат RAG запроса.
 */
@Serializable
data class RagQueryResult(
    val question: String,
    val answer: String,
    val usedRag: Boolean,
    val foundChunks: Int,
    val relevanceScores: List<Float>,
    val sources: List<String>,
    val durationMs: Long,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null
)

/**
 * Результат сравнения ответов.
 */
@Serializable
data class RagComparisonResult(
    val question: String,
    val withoutRag: RagQueryResult,
    val withRag: RagQueryResult,
    val totalDurationMs: Long
)
