package org.example.tools.devassistant.docs

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.example.data.LLMClient
import org.example.rag.RagQueryService
import org.example.tools.annotations.Param
import org.example.tools.annotations.Tool
import org.example.tools.core.AnnotatedAgentTool
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Инструмент для вопросов по документации с RAG.
 * Использует RagQueryService для формирования ответа на основе документации.
 */
@Tool(
    name = "docs_query",
    description = "Задать вопрос по документации проекта. Использует RAG для поиска релевантной информации и формирования ответа"
)
@Param(
    name = "question",
    description = "Вопрос о проекте",
    type = "string",
    required = true
)
@Param(
    name = "top_k",
    description = "Количество фрагментов для контекста (по умолчанию: 5)",
    type = "integer",
    required = false
)
@Param(
    name = "min_relevance",
    description = "Минимальная релевантность 0.0-1.0 (по умолчанию: 0.2)",
    type = "number",
    required = false
)
@Param(
    name = "path",
    description = "Путь к проекту (по умолчанию: текущая директория)",
    type = "string",
    required = false
)
object DocsQueryTool : AnnotatedAgentTool(), KoinComponent {
    private val llmClient: LLMClient by inject()

    override suspend fun execute(arguments: String): String {
        val json = Json.parseToJsonElement(arguments).jsonObject
        val question = json["question"]?.jsonPrimitive?.content
            ?: return "❌ Ошибка: question не может быть пустым"
        val topK = json["top_k"]?.jsonPrimitive?.intOrNull ?: 5
        val minRelevance = json["min_relevance"]?.jsonPrimitive?.floatOrNull ?: 0.2f
        val path = json["path"]?.jsonPrimitive?.content ?: "."

        // Проверяем/создаём индекс
        if (globalDocsIndex == null || globalDocsIndex!!.projectPath != path) {
            globalDocsIndex = DocsIndex(path)
            val indexResult = globalDocsIndex!!.indexProjectDocs()
            if (!indexResult.success) {
                return "❌ Ошибка индексации: ${indexResult.error}"
            }
        }

        // Создаём RagQueryService с индексом документации
        val ragService = RagQueryService(llmClient, globalDocsIndex!!.getDocumentIndex())

        val systemPrompt = """
            Ты — ассистент разработчика для проекта.
            Отвечай на вопросы о проекте, используя информацию из документации.

            Правила:
            - Если информация найдена в документации — используй её
            - Если информации нет — честно скажи об этом
            - Будь конкретным и практичным
            - Ссылайся на источники (названия файлов)
            - Приводи примеры кода где уместно
        """.trimIndent()

        return try {
            val result = ragService.queryWithRag(
                question = question,
                topK = topK,
                minRelevance = minRelevance,
                systemPrompt = systemPrompt
            )

            buildString {
                appendLine("📚 Ответ по документации")
                appendLine("━".repeat(50))
                appendLine()
                appendLine(result.answer)
                appendLine()
                appendLine("━".repeat(50))
                appendLine("📊 Статистика:")
                appendLine("   • Найдено фрагментов: ${result.foundChunks}")
                if (result.sources.isNotEmpty()) {
                    appendLine("   • Источники: ${result.sources.joinToString(", ")}")
                }
                if (result.relevanceScores.isNotEmpty()) {
                    val avgRelevance = (result.relevanceScores.average() * 100).toInt()
                    appendLine("   • Средняя релевантность: $avgRelevance%")
                }
                appendLine("   • Время ответа: ${result.durationMs}ms")
            }
        } catch (e: Exception) {
            "❌ Ошибка при обработке запроса: ${e.message}"
        }
    }
}
