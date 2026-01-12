package org.example.tools.devassistant.docs

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.example.tools.annotations.Param
import org.example.tools.annotations.Tool
import org.example.tools.core.AnnotatedAgentTool

/**
 * Глобальный индекс документации (singleton для переиспользования).
 */
internal var globalDocsIndex: DocsIndex? = null

/**
 * Инструмент для индексации документации проекта.
 */
@Tool(
    name = "docs_index",
    description = "Индексирует документацию проекта (README, CLAUDE.md, docs/) для последующего поиска и RAG запросов"
)
@Param(
    name = "path",
    description = "Путь к проекту (по умолчанию: текущая директория)",
    type = "string",
    required = false
)
object DocsIndexTool : AnnotatedAgentTool() {

    override suspend fun execute(arguments: String): String {
        val json = Json.parseToJsonElement(arguments).jsonObject
        val path = json["path"]?.jsonPrimitive?.content ?: "."

        // Создаём или обновляем глобальный индекс
        globalDocsIndex = DocsIndex(path)
        val result = globalDocsIndex!!.indexProjectDocs()

        return if (result.success) {
            buildString {
                appendLine("✅ Документация проиндексирована")
                appendLine("━".repeat(40))
                appendLine()
                appendLine("📁 Проект: ${globalDocsIndex!!.projectPath}")
                appendLine("📄 Файлов: ${result.filesIndexed}")
                appendLine("📦 Чанков: ${result.chunksCreated}")
                appendLine()
                appendLine("📚 Источники:")
                result.sources.forEach { source ->
                    appendLine("   • $source")
                }
            }
        } else {
            "❌ Ошибка индексации: ${result.error}"
        }
    }
}
