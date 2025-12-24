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
            "и использует их для ответа на вопрос. Поддерживает фильтрацию по порогу релевантности."
)
@Param(name = "question", description = "Вопрос пользователя", type = "string", required = true)
@Param(name = "top_k", description = "Количество релевантных фрагментов для поиска (по умолчанию: 3)", type = "integer", required = false)
@Param(name = "min_relevance", description = "Минимальный порог релевантности 0.0-1.0 (по умолчанию: без фильтрации). Рекомендуется: 0.3 для умеренной фильтрации, 0.5 для строгой", type = "number", required = false)
object AskWithRagTool : AnnotatedAgentTool(), KoinComponent {
    private val llmClient: LLMClient by inject()
    private val ragService by lazy { RagQueryService(llmClient) }

    override suspend fun execute(arguments: String): String {
        val json = Json.parseToJsonElement(arguments).jsonObject
        val question = json["question"]?.jsonPrimitive?.content
            ?: return "❌ Ошибка: question не может быть пустым"
        val topK = json["top_k"]?.jsonPrimitive?.intOrNull ?: 3
        val minRelevance = json["min_relevance"]?.jsonPrimitive?.floatOrNull

        return try {
            val result = ragService.queryWithRag(question, topK, minRelevance)

            buildString {
                appendLine("🤖 Ответ с использованием RAG:")
                if (minRelevance != null) {
                    appendLine("   [Фильтрация: порог релевантности ≥ ${String.format("%.2f", minRelevance)}]")
                }
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
                    val minScore = result.relevanceScores.minOrNull() ?: 0.0f
                    val maxScore = result.relevanceScores.maxOrNull() ?: 0.0f
                    appendLine("  • Релевантность: мин=${String.format("%.2f", minScore)}, средн=${String.format("%.2f", avgRelevance)}, макс=${String.format("%.2f", maxScore)}")
                }
                if (minRelevance != null) {
                    appendLine("  • Фильтр релевантности: ≥ ${String.format("%.2f", minRelevance)}")
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

/**
 * Инструмент для сравнения RAG с фильтрацией и без.
 * Показывает влияние порога релевантности на качество ответа.
 */
@Tool(
    name = "compare_rag_with_reranking",
    description = "Сравнить ответы AI в трёх режимах: БЕЗ RAG, С RAG (без фильтрации) и С RAG + ФИЛЬТРАЦИЯ. " +
            "Показывает влияние порога релевантности на качество ответа и отсекает нерелевантные результаты."
)
@Param(name = "question", description = "Вопрос для сравнения", type = "string", required = true)
@Param(name = "top_k", description = "Количество фрагментов для RAG режима (по умолчанию: 3)", type = "integer", required = false)
@Param(name = "min_relevance", description = "Порог релевантности для фильтрации (по умолчанию: 0.3). Рекомендуется: 0.3 (умеренная), 0.5 (строгая)", type = "number", required = false)
object CompareRagWithRerankingTool : AnnotatedAgentTool(), KoinComponent {
    private val llmClient: LLMClient by inject()
    private val ragService by lazy { RagQueryService(llmClient) }

    override suspend fun execute(arguments: String): String {
        val json = Json.parseToJsonElement(arguments).jsonObject
        val question = json["question"]?.jsonPrimitive?.content
            ?: return "❌ Ошибка: question не может быть пустым"
        val topK = json["top_k"]?.jsonPrimitive?.intOrNull ?: 3
        val minRelevance = json["min_relevance"]?.jsonPrimitive?.floatOrNull ?: 0.3f

        return try {
            val comparison = ragService.compareWithReranking(question, topK, minRelevance)

            buildString {
                appendLine("📊 Сравнение: БЕЗ RAG vs С RAG (без фильтра) vs С RAG + ФИЛЬТРАЦИЯ")
                appendLine()
                appendLine("❓ Вопрос: ${comparison.question}")
                appendLine("🔧 Порог фильтрации: ≥ ${String.format("%.2f", comparison.threshold)}")
                appendLine()
                appendLine("═".repeat(60))
                appendLine()

                // Режим 1: БЕЗ RAG
                appendLine("1️⃣ БЕЗ RAG (обычный LLM):")
                appendLine()
                appendLine(comparison.withoutRag.answer.take(200) + "...")
                appendLine()
                appendLine("Статистика:")
                appendLine("  • Время: ${comparison.withoutRag.durationMs}ms")
                if (comparison.withoutRag.promptTokens != null) {
                    appendLine("  • Токены: ${comparison.withoutRag.promptTokens} → ${comparison.withoutRag.completionTokens}")
                }
                appendLine()
                appendLine("═".repeat(60))
                appendLine()

                // Режим 2: С RAG БЕЗ ФИЛЬТРАЦИИ
                appendLine("2️⃣ С RAG (БЕЗ фильтрации):")
                appendLine()
                appendLine(comparison.withRagNoFilter.answer.take(200) + "...")
                appendLine()
                appendLine("Статистика:")
                appendLine("  • Найдено фрагментов: ${comparison.withRagNoFilter.foundChunks}")
                if (comparison.withRagNoFilter.sources.isNotEmpty()) {
                    appendLine("  • Источники: ${comparison.withRagNoFilter.sources.joinToString(", ")}")
                }
                if (comparison.withRagNoFilter.relevanceScores.isNotEmpty()) {
                    val avg = comparison.withRagNoFilter.relevanceScores.average()
                    val min = comparison.withRagNoFilter.relevanceScores.minOrNull() ?: 0.0f
                    val max = comparison.withRagNoFilter.relevanceScores.maxOrNull() ?: 0.0f
                    appendLine("  • Релевантность: мин=${String.format("%.2f", min)}, средн=${String.format("%.2f", avg)}, макс=${String.format("%.2f", max)}")
                }
                appendLine("  • Время: ${comparison.withRagNoFilter.durationMs}ms")
                if (comparison.withRagNoFilter.promptTokens != null) {
                    appendLine("  • Токены: ${comparison.withRagNoFilter.promptTokens} → ${comparison.withRagNoFilter.completionTokens}")
                }
                appendLine()
                appendLine("═".repeat(60))
                appendLine()

                // Режим 3: С RAG + ФИЛЬТРАЦИЯ
                appendLine("3️⃣ С RAG + ФИЛЬТРАЦИЯ (порог ≥ ${String.format("%.2f", comparison.threshold)}):")
                appendLine()
                appendLine(comparison.withRagFiltered.answer.take(200) + "...")
                appendLine()
                appendLine("Статистика:")
                appendLine("  • Найдено фрагментов: ${comparison.withRagFiltered.foundChunks}")
                if (comparison.withRagFiltered.sources.isNotEmpty()) {
                    appendLine("  • Источники: ${comparison.withRagFiltered.sources.joinToString(", ")}")
                }
                if (comparison.withRagFiltered.relevanceScores.isNotEmpty()) {
                    val avg = comparison.withRagFiltered.relevanceScores.average()
                    val min = comparison.withRagFiltered.relevanceScores.minOrNull() ?: 0.0f
                    val max = comparison.withRagFiltered.relevanceScores.maxOrNull() ?: 0.0f
                    appendLine("  • Релевантность: мин=${String.format("%.2f", min)}, средн=${String.format("%.2f", avg)}, макс=${String.format("%.2f", max)}")
                }
                appendLine("  • Фильтр: ≥ ${String.format("%.2f", comparison.threshold)}")
                appendLine("  • Время: ${comparison.withRagFiltered.durationMs}ms")
                if (comparison.withRagFiltered.promptTokens != null) {
                    appendLine("  • Токены: ${comparison.withRagFiltered.promptTokens} → ${comparison.withRagFiltered.completionTokens}")
                }
                appendLine()
                appendLine("═".repeat(60))
                appendLine()

                // Анализ
                appendLine("🔍 Анализ фильтрации:")
                appendLine()

                val removedChunks = comparison.withRagNoFilter.foundChunks - comparison.withRagFiltered.foundChunks
                if (removedChunks > 0) {
                    appendLine("  • Отфильтровано нерелевантных фрагментов: $removedChunks из ${comparison.withRagNoFilter.foundChunks}")
                    appendLine("  • Оставлено релевантных: ${comparison.withRagFiltered.foundChunks}")
                } else {
                    appendLine("  • Фильтр не исключил ни одного фрагмента")
                    appendLine("  • Все найденные фрагменты выше порога ${String.format("%.2f", comparison.threshold)}")
                }

                if (comparison.withRagFiltered.foundChunks > 0) {
                    val avgFiltered = comparison.withRagFiltered.relevanceScores.average()
                    val avgNoFilter = comparison.withRagNoFilter.relevanceScores.average()
                    val improvement = ((avgFiltered - avgNoFilter) / avgNoFilter * 100).toInt()

                    if (improvement > 0) {
                        appendLine("  • Средняя релевантность ПОВЫСИЛАСЬ на $improvement% после фильтрации")
                    } else if (improvement < 0) {
                        appendLine("  • Средняя релевантность не изменилась существенно")
                    }
                }

                appendLine("  • Общее время трёх запросов: ${comparison.totalDurationMs}ms")

                appendLine()
                appendLine("💡 Вывод:")

                when {
                    comparison.withRagFiltered.foundChunks == 0 -> {
                        appendLine("  ⚠️  Фильтр слишком строгий - отсеял ВСЕ результаты!")
                        appendLine("  Рекомендуется снизить порог (попробуйте ${String.format("%.2f", comparison.threshold * 0.7f)}).")
                    }
                    removedChunks > 0 -> {
                        appendLine("  ✅ Фильтрация улучшила качество: отсеяны нерелевантные фрагменты.")
                        appendLine("  Ответ с фильтром более точен и основан на качественных источниках.")
                    }
                    comparison.withRagFiltered.foundChunks > 0 -> {
                        appendLine("  ✅ Все найденные фрагменты высокого качества (выше порога).")
                        appendLine("  Фильтрация подтвердила релевантность результатов.")
                    }
                    else -> {
                        appendLine("  ℹ️  Недостаточно релевантной информации в индексе.")
                    }
                }
            }
        } catch (e: Exception) {
            "❌ Ошибка при сравнении с реранкингом: ${e.message}"
        }
    }
}
