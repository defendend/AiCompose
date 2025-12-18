package org.example.tools.pipeline

import kotlinx.serialization.json.*
import org.example.tools.annotations.Param
import org.example.tools.annotations.Tool
import org.example.tools.core.AnnotatedAgentTool
import java.io.File
import java.time.Instant

/**
 * Симулированная база документов для поиска
 */
private val documentDatabase = mapOf(
    "kotlin" to listOf(
        "Kotlin — современный язык программирования для JVM, Android и веб-разработки.",
        "Kotlin поддерживает null-safety, coroutines, extension functions и data classes.",
        "Kotlin был создан JetBrains в 2011 году и стал официальным языком для Android в 2017."
    ),
    "compose" to listOf(
        "Jetpack Compose — современный декларативный UI фреймворк для Android.",
        "Compose Multiplatform позволяет использовать Compose для Desktop, Web и iOS.",
        "Compose использует реактивный подход с State и remember для управления UI."
    ),
    "mcp" to listOf(
        "MCP (Model Context Protocol) — стандарт для интеграции LLM с внешними системами.",
        "MCP поддерживает инструменты (tools), ресурсы (resources) и промпты (prompts).",
        "MCP использует транспорты stdio и SSE для коммуникации между клиентом и сервером."
    )
)

/**
 * Инструмент 1: Поиск документов по ключевым словам
 */
@Tool(
    name = "pipeline_search_docs",
    description = "Поиск документов по ключевым словам. Возвращает список релевантных документов."
)
@Param(name = "query", description = "Поисковый запрос", type = "string", required = true)
object PipelineSearchDocs : AnnotatedAgentTool() {
    override suspend fun execute(arguments: String): String {
        val json = Json.parseToJsonElement(arguments).jsonObject
        val query = json["query"]?.jsonPrimitive?.content?.lowercase() ?: ""

        if (query.isBlank()) {
            return "❌ Ошибка: query не может быть пустым"
        }

        // Поиск по ключевым словам
        val results = documentDatabase.entries
            .filter { (key, _) -> query.contains(key) }
            .flatMap { (key, docs) ->
                docs.map { doc -> "[$key] $doc" }
            }

        return if (results.isEmpty()) {
            """
            🔍 Поиск по запросу: "$query"

            Результатов не найдено.
            Доступные темы: ${documentDatabase.keys.joinToString(", ")}
            """.trimIndent()
        } else {
            """
            🔍 Поиск по запросу: "$query"

            Найдено документов: ${results.size}

            ${results.joinToString("\n\n") { "• $it" }}
            """.trimIndent()
        }
    }
}

/**
 * Инструмент 2: Суммаризация текста
 */
@Tool(
    name = "pipeline_summarize",
    description = "Суммаризация текста. Создает краткую выжимку из переданного контента."
)
@Param(name = "text", description = "Текст для суммаризации", type = "string", required = true)
object PipelineSummarize : AnnotatedAgentTool() {
    override suspend fun execute(arguments: String): String {
        val json = Json.parseToJsonElement(arguments).jsonObject
        val text = json["text"]?.jsonPrimitive?.content ?: ""

        if (text.isBlank()) {
            return "❌ Ошибка: text не может быть пустым"
        }

        // Простая суммаризация: берем первое предложение из каждого параграфа
        val lines = text.split("\n").filter { it.isNotBlank() }
        val summary = lines
            .filter { !it.startsWith("🔍") && !it.startsWith("Найдено") }
            .filter { it.contains("•") || it.contains("[") }
            .take(5)
            .joinToString("\n") {
                it.replace("•", "→").trim()
            }

        val timestamp = Instant.now()

        return """
            📝 Суммаризация завершена

            Исходный текст: ${text.length} символов
            Итоговая сводка: ${summary.length} символов
            Время: $timestamp

            --- СВОДКА ---
            $summary
            --- КОНЕЦ СВОДКИ ---
        """.trimIndent()
    }
}

/**
 * Инструмент 3: Сохранение контента в файл
 */
@Tool(
    name = "pipeline_save_to_file",
    description = "Сохранение текста в файл. Создает файл в директории pipeline_results/."
)
@Param(name = "content", description = "Содержимое для сохранения", type = "string", required = true)
@Param(name = "filename", description = "Имя файла (опционально)", type = "string", required = false)
object PipelineSaveToFile : AnnotatedAgentTool() {
    override suspend fun execute(arguments: String): String {
        val json = Json.parseToJsonElement(arguments).jsonObject
        val content = json["content"]?.jsonPrimitive?.content ?: ""
        val filename = json["filename"]?.jsonPrimitive?.content ?: "result.txt"

        if (content.isBlank()) {
            return "❌ Ошибка: content не может быть пустым"
        }

        return try {
            // Создаем директорию если не существует
            val dir = File("pipeline_results")
            if (!dir.exists()) {
                dir.mkdirs()
            }

            // Добавляем timestamp к имени файла
            val timestamp = Instant.now().toString().replace(":", "-")
            val finalFilename = filename.replace(".txt", "_$timestamp.txt")
            val file = File(dir, finalFilename)

            // Сохраняем контент
            file.writeText(content)

            """
            💾 Файл успешно сохранен

            Путь: ${file.absolutePath}
            Размер: ${content.length} символов
            Время: $timestamp

            Содержимое (первые 200 символов):
            ${content.take(200)}${if (content.length > 200) "..." else ""}
            """.trimIndent()
        } catch (e: Exception) {
            "❌ Ошибка при сохранении файла: ${e.message}"
        }
    }
}
