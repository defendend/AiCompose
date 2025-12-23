package org.example.tools.rag

import kotlinx.serialization.json.*
import org.example.rag.DocumentChunker
import org.example.rag.DocumentIndex
import org.example.tools.annotations.Param
import org.example.tools.annotations.Tool
import org.example.tools.core.AnnotatedAgentTool
import java.io.File

/**
 * Глобальный индекс документов
 * В продакшене лучше использовать DI, но для демо подойдет
 */
private val globalIndex = DocumentIndex()

/**
 * Инструмент 1: Индексация документов
 */
@Tool(
    name = "rag_index_documents",
    description = "Индексирует документы для векторного поиска. " +
            "Разбивает на чанки, генерирует эмбеддинги и создает индекс."
)
@Param(name = "path", description = "Путь к файлу или директории с документами", type = "string", required = true)
@Param(name = "extensions", description = "Расширения файлов через запятую (по умолчанию: md,txt,kt,java)", type = "string", required = false)
object RagIndexDocuments : AnnotatedAgentTool() {
    override suspend fun execute(arguments: String): String {
        val json = Json.parseToJsonElement(arguments).jsonObject
        val path = json["path"]?.jsonPrimitive?.content
            ?: return "❌ Ошибка: path не может быть пустым"
        val extensionsStr = json["extensions"]?.jsonPrimitive?.content ?: "md,txt,kt,java"

        val file = File(path)
        if (!file.exists()) {
            return "❌ Путь не существует: $path"
        }

        return try {
            val extensions = extensionsStr.split(",").map { it.trim() }.toSet()

            // Загружаем и чанкуем документы
            val chunks = if (file.isDirectory) {
                DocumentChunker.chunkDirectory(file, extensions)
            } else {
                DocumentChunker.chunkFile(file)
            }

            if (chunks.isEmpty()) {
                return "⚠️  Не найдено документов для индексации в $path"
            }

            // Индексируем
            globalIndex.clear()
            globalIndex.indexChunks(chunks)

            // Сохраняем индекс
            val indexFile = File("document_index.json")
            globalIndex.save(indexFile)

            """
            📚 Индексация завершена

            Обработано:
            - Путь: $path
            - Файлов: ${chunks.map { it.source }.distinct().size}
            - Чанков: ${chunks.size}
            - Индекс: ${indexFile.absolutePath}

            Примеры источников:
            ${chunks.map { it.source }.distinct().take(5).joinToString("\n") { "  • $it" }}

            Теперь можно искать по документам через rag_search!
            """.trimIndent()
        } catch (e: Exception) {
            "❌ Ошибка при индексации: ${e.message}"
        }
    }
}

/**
 * Инструмент 2: Поиск по индексу
 */
@Tool(
    name = "rag_search",
    description = "Выполняет семантический поиск по проиндексированным документам. " +
            "Возвращает наиболее релевантные фрагменты."
)
@Param(name = "query", description = "Поисковый запрос", type = "string", required = true)
@Param(name = "top_k", description = "Количество результатов (по умолчанию: 3)", type = "integer", required = false)
object RagSearch : AnnotatedAgentTool() {
    override suspend fun execute(arguments: String): String {
        val json = Json.parseToJsonElement(arguments).jsonObject
        val query = json["query"]?.jsonPrimitive?.content
            ?: return "❌ Ошибка: query не может быть пустым"
        val topK = json["top_k"]?.jsonPrimitive?.intOrNull ?: 3

        return try {
            // Загружаем индекс если еще не загружен
            if (globalIndex.size() == 0) {
                val indexFile = File("document_index.json")
                if (indexFile.exists()) {
                    globalIndex.load(indexFile)
                } else {
                    return "❌ Индекс не найден. Сначала проиндексируйте документы через rag_index_documents"
                }
            }

            // Выполняем поиск
            val results = globalIndex.search(query, topK)

            if (results.isEmpty()) {
                return """
                🔍 Поиск по запросу: "$query"

                Ничего не найдено. Попробуйте другой запрос.
                """.trimIndent()
            }

            buildString {
                appendLine("🔍 Поиск по запросу: \"$query\"")
                appendLine()
                appendLine("Найдено ${results.size} релевантных фрагментов:")
                appendLine()

                results.forEachIndexed { index, result ->
                    appendLine("--- Результат ${index + 1} (релевантность: ${String.format("%.2f", result.score)}) ---")
                    appendLine("Источник: ${result.source}")
                    appendLine()
                    appendLine(result.content.take(500)) // Ограничиваем длину
                    if (result.content.length > 500) {
                        appendLine("...")
                    }
                    appendLine()
                }
            }
        } catch (e: Exception) {
            "❌ Ошибка при поиске: ${e.message}"
        }
    }
}

/**
 * Инструмент 3: Информация об индексе
 */
@Tool(
    name = "rag_index_info",
    description = "Показывает информацию о текущем индексе документов: " +
            "количество документов, размер индекса, статистику."
)
object RagIndexInfo : AnnotatedAgentTool() {
    override suspend fun execute(arguments: String): String {
        return try {
            val indexFile = File("document_index.json")

            if (!indexFile.exists()) {
                return """
                📊 Информация об индексе

                Статус: Индекс не создан

                Используйте rag_index_documents для создания индекса.
                """.trimIndent()
            }

            // Загружаем если не загружен
            if (globalIndex.size() == 0) {
                globalIndex.load(indexFile)
            }

            """
            📊 Информация об индексе

            Статус: Активен ✅
            Документов: ${globalIndex.size()}
            Файл индекса: ${indexFile.absolutePath}
            Размер файла: ${indexFile.length() / 1024} KB
            Последнее обновление: ${java.time.Instant.ofEpochMilli(indexFile.lastModified())}

            Для поиска используйте: rag_search
            """.trimIndent()
        } catch (e: Exception) {
            "❌ Ошибка при получении информации: ${e.message}"
        }
    }
}
