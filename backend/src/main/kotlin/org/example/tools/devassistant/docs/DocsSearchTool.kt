package org.example.tools.devassistant.docs

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.example.tools.annotations.Param
import org.example.tools.annotations.Tool
import org.example.tools.core.AnnotatedAgentTool

/**
 * Инструмент для низкоуровневого поиска по документации.
 * Возвращает релевантные фрагменты без LLM обработки.
 */
@Tool(
    name = "docs_search",
    description = "Поиск по документации проекта. Возвращает релевантные фрагменты из README, CLAUDE.md, docs/"
)
@Param(
    name = "query",
    description = "Поисковый запрос",
    type = "string",
    required = true
)
@Param(
    name = "top_k",
    description = "Количество результатов (по умолчанию: 8)",
    type = "integer",
    required = false
)
@Param(
    name = "min_relevance",
    description = "Минимальная релевантность 0.0-1.0 (по умолчанию: 0.1)",
    type = "number",
    required = false
)
@Param(
    name = "path",
    description = "Путь к проекту (по умолчанию: текущая директория)",
    type = "string",
    required = false
)
object DocsSearchTool : AnnotatedAgentTool() {

    override suspend fun execute(arguments: String): String {
        val json = Json.parseToJsonElement(arguments).jsonObject
        val query = json["query"]?.jsonPrimitive?.content
            ?: return "❌ Ошибка: query не может быть пустым"
        val topK = json["top_k"]?.jsonPrimitive?.intOrNull ?: 8
        val minRelevance = json["min_relevance"]?.jsonPrimitive?.floatOrNull ?: 0.1f
        val path = json["path"]?.jsonPrimitive?.content
            ?: System.getenv("PROJECT_PATH")
            ?: "."

        // Проверяем/создаём индекс
        if (globalDocsIndex == null || globalDocsIndex!!.projectPath != path) {
            globalDocsIndex = DocsIndex(path)
            val indexResult = globalDocsIndex!!.indexProjectDocs()
            if (!indexResult.success) {
                return "❌ Ошибка индексации: ${indexResult.error}"
            }
        }

        val results = globalDocsIndex!!.search(query, topK, minRelevance)

        if (results.isEmpty()) {
            return buildString {
                appendLine("🔍 Поиск: \"$query\"")
                appendLine()
                appendLine("📭 Ничего не найдено")
                appendLine()
                appendLine("💡 Попробуйте:")
                appendLine("   • Использовать другие ключевые слова")
                appendLine("   • Уменьшить min_relevance")
                appendLine("   • Переиндексировать документацию")
            }
        }

        return buildString {
            appendLine("🔍 Поиск: \"$query\"")
            appendLine("━".repeat(50))
            appendLine()

            results.forEachIndexed { index, result ->
                val relevancePercent = (result.score * 100).toInt()
                val relevanceBar = "█".repeat(relevancePercent / 10) + "░".repeat(10 - relevancePercent / 10)

                appendLine("📄 ${index + 1}. ${result.source}")
                appendLine("   Релевантность: $relevanceBar $relevancePercent%")
                appendLine()
                appendLine("   ${result.content.take(300)}${if (result.content.length > 300) "..." else ""}")
                appendLine()
            }

            appendLine("━".repeat(50))
            appendLine("Найдено: ${results.size} результатов")
        }
    }
}
