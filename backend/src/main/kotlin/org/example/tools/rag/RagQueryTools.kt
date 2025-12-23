package org.example.tools.rag

import kotlinx.serialization.json.*
import org.example.data.LLMClient
import org.example.rag.RagQueryService
import org.example.tools.annotations.Param
import org.example.tools.annotations.Tool
import org.example.tools.core.AnnotatedAgentTool
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Инструмент для запросов С RAG.
 * Автоматически ищет релевантные чанки и обогащает контекст.
 */
@Tool(
    name = "ask_with_rag",
    description = "Задать вопрос агенту с использованием RAG (Retrieval-Augmented Generation). " +
            "Система автоматически найдёт релевантные фрагменты из проиндексированных документов " +
            "и использует их для ответа на вопрос."
)
@Param(name = "question", description = "Вопрос пользователя", type = "string", required = true)
@Param(name = "top_k", description = "Количество релевантных фрагментов для поиска (по умолчанию: 3)", type = "integer", required = false)
object AskWithRagTool : AnnotatedAgentTool(), KoinComponent {
    private val llmClient: LLMClient by inject()
    private val ragService by lazy { RagQueryService(llmClient) }

    override suspend fun execute(arguments: String): String {
        val json = Json.parseToJsonElement(arguments).jsonObject
        val question = json["question"]?.jsonPrimitive?.content
            ?: return "❌ Ошибка: question не может быть пустым"
        val topK = json["top_k"]?.jsonPrimitive?.intOrNull ?: 3

        return try {
            val result = ragService.queryWithRag(question, topK)

            buildString {
                appendLine("🤖 Ответ с использованием RAG:")
                appendLine()
                appendLine(result.answer)
                appendLine()
                appendLine("---")
                appendLine("📊 Статистика:")
                appendLine("  • Найдено фрагментов: ${result.foundChunks}")
                if (result.sources.isNotEmpty()) {
                    appendLine("  • Источники: ${result.sources.joinToString(", ")}")
                }
                if (result.relevanceScores.isNotEmpty()) {
                    val avgRelevance = result.relevanceScores.average()
                    appendLine("  • Средняя релевантность: ${String.format("%.2f", avgRelevance)}")
                }
                appendLine("  • Время ответа: ${result.durationMs}ms")
                if (result.promptTokens != null && result.completionTokens != null) {
                    appendLine("  • Токены: ${result.promptTokens} → ${result.completionTokens}")
                }
            }
        } catch (e: Exception) {
            "❌ Ошибка при RAG запросе: ${e.message}"
        }
    }
}

/**
 * Инструмент для сравнения ответов с RAG и без RAG.
 */
@Tool(
    name = "compare_rag_answers",
    description = "Сравнить ответы AI агента с использованием RAG и без RAG. " +
            "Показывает два ответа на один вопрос: с поиском по документам и без поиска. " +
            "Полезно для оценки эффективности RAG системы."
)
@Param(name = "question", description = "Вопрос для сравнения", type = "string", required = true)
@Param(name = "top_k", description = "Количество фрагментов для RAG режима (по умолчанию: 3)", type = "integer", required = false)
object CompareRagAnswersTool : AnnotatedAgentTool(), KoinComponent {
    private val llmClient: LLMClient by inject()
    private val ragService by lazy { RagQueryService(llmClient) }

    override suspend fun execute(arguments: String): String {
        val json = Json.parseToJsonElement(arguments).jsonObject
        val question = json["question"]?.jsonPrimitive?.content
            ?: return "❌ Ошибка: question не может быть пустым"
        val topK = json["top_k"]?.jsonPrimitive?.intOrNull ?: 3

        return try {
            val comparison = ragService.compareAnswers(question, topK)

            buildString {
                appendLine("📊 Сравнение ответов С RAG и БЕЗ RAG")
                appendLine()
                appendLine("❓ Вопрос: ${comparison.question}")
                appendLine()
                appendLine("═".repeat(60))
                appendLine()

                // Ответ БЕЗ RAG
                appendLine("🚫 БЕЗ RAG (обычный LLM):")
                appendLine()
                appendLine(comparison.withoutRag.answer)
                appendLine()
                appendLine("Статистика:")
                appendLine("  • Время: ${comparison.withoutRag.durationMs}ms")
                if (comparison.withoutRag.promptTokens != null) {
                    appendLine("  • Токены: ${comparison.withoutRag.promptTokens} → ${comparison.withoutRag.completionTokens}")
                }
                appendLine()
                appendLine("═".repeat(60))
                appendLine()

                // Ответ С RAG
                appendLine("✅ С RAG (с поиском по документам):")
                appendLine()
                appendLine(comparison.withRag.answer)
                appendLine()
                appendLine("Статистика:")
                appendLine("  • Найдено фрагментов: ${comparison.withRag.foundChunks}")
                if (comparison.withRag.sources.isNotEmpty()) {
                    appendLine("  • Источники: ${comparison.withRag.sources.joinToString(", ")}")
                }
                if (comparison.withRag.relevanceScores.isNotEmpty()) {
                    val avgRelevance = comparison.withRag.relevanceScores.average()
                    appendLine("  • Средняя релевантность: ${String.format("%.2f", avgRelevance)}")
                }
                appendLine("  • Время: ${comparison.withRag.durationMs}ms")
                if (comparison.withRag.promptTokens != null) {
                    appendLine("  • Токены: ${comparison.withRag.promptTokens} → ${comparison.withRag.completionTokens}")
                }
                appendLine()
                appendLine("═".repeat(60))
                appendLine()

                // Анализ
                appendLine("🔍 Анализ:")
                appendLine()

                val ragWasFaster = comparison.withRag.durationMs < comparison.withoutRag.durationMs
                appendLine("  • Скорость: ${if (ragWasFaster) "RAG быстрее" else "Обычный режим быстрее"} " +
                        "(${comparison.totalDurationMs}ms общее время)")

                if (comparison.withRag.foundChunks > 0) {
                    appendLine("  • RAG использовал ${comparison.withRag.foundChunks} фрагментов из документов")
                    appendLine("  • Ответ с RAG основан на реальных данных из: ${comparison.withRag.sources.joinToString(", ")}")
                } else {
                    appendLine("  • RAG не нашёл релевантных документов, ответ аналогичен обычному режиму")
                }

                appendLine()
                appendLine("💡 Вывод:")
                if (comparison.withRag.foundChunks > 0) {
                    appendLine("  RAG помог найти конкретную информацию в документах.")
                    appendLine("  Ответ с RAG более фактологичен и подкреплён источниками.")
                } else {
                    appendLine("  RAG не помог - нет релевантной информации в индексе.")
                    appendLine("  Оба ответа основаны только на знаниях модели.")
                }
            }
        } catch (e: Exception) {
            "❌ Ошибка при сравнении: ${e.message}"
        }
    }
}
