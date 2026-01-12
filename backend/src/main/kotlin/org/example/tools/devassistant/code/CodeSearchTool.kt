package org.example.tools.devassistant.code

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.example.tools.annotations.Param
import org.example.tools.annotations.Tool
import org.example.tools.core.AnnotatedAgentTool
import java.io.File

/**
 * Инструмент для поиска по коду проекта (grep-like).
 */
@Tool(
    name = "code_search",
    description = "Поиск по коду проекта. Находит файлы и строки, содержащие указанный паттерн"
)
@Param(
    name = "pattern",
    description = "Паттерн для поиска (поддерживает regex)",
    type = "string",
    required = true
)
@Param(
    name = "file_pattern",
    description = "Фильтр файлов (например: *.kt, *.java)",
    type = "string",
    required = false
)
@Param(
    name = "path",
    description = "Путь для поиска (по умолчанию: текущая директория)",
    type = "string",
    required = false
)
@Param(
    name = "max_results",
    description = "Максимум результатов (по умолчанию: 30)",
    type = "integer",
    required = false
)
@Param(
    name = "context_lines",
    description = "Количество строк контекста (по умолчанию: 0)",
    type = "integer",
    required = false
)
object CodeSearchTool : AnnotatedAgentTool() {

    // Исключаемые директории
    private val EXCLUDED_DIRS = setOf(
        ".git", ".gradle", ".idea", "build", "out", "target",
        "node_modules", "__pycache__", ".venv", "venv"
    )

    // Бинарные расширения для пропуска
    private val BINARY_EXTENSIONS = setOf(
        "jar", "war", "class", "exe", "dll", "so", "dylib",
        "png", "jpg", "jpeg", "gif", "ico", "pdf", "zip"
    )

    override suspend fun execute(arguments: String): String {
        val json = Json.parseToJsonElement(arguments).jsonObject
        val pattern = json["pattern"]?.jsonPrimitive?.content
            ?: return "❌ Ошибка: pattern не может быть пустым"
        val filePattern = json["file_pattern"]?.jsonPrimitive?.content
        val path = json["path"]?.jsonPrimitive?.content
            ?: System.getenv("PROJECT_PATH")
            ?: "."
        val maxResults = json["max_results"]?.jsonPrimitive?.intOrNull ?: 30
        val contextLines = json["context_lines"]?.jsonPrimitive?.intOrNull ?: 0

        val baseDir = File(path).absoluteFile
        if (!baseDir.exists()) {
            return "❌ Директория не существует: ${baseDir.absolutePath}"
        }

        return try {
            val regex = Regex(pattern, RegexOption.IGNORE_CASE)
            val results = searchInFiles(baseDir, regex, filePattern, maxResults, contextLines)
            formatResults(pattern, results, filePattern)
        } catch (e: Exception) {
            "❌ Ошибка поиска: ${e.message}"
        }
    }

    private fun searchInFiles(
        baseDir: File,
        regex: Regex,
        filePattern: String?,
        maxResults: Int,
        contextLines: Int
    ): List<SearchMatch> {
        val results = mutableListOf<SearchMatch>()
        var totalMatches = 0

        baseDir.walkTopDown()
            .filter { it.isFile }
            .filter { file -> !isExcluded(file, baseDir) }
            .filter { file -> filePattern == null || matchesPattern(file.name, filePattern) }
            .filter { file -> file.extension.lowercase() !in BINARY_EXTENSIONS }
            .takeWhile { totalMatches < maxResults }
            .forEach { file ->
                try {
                    val lines = file.readLines()
                    lines.forEachIndexed { index, line ->
                        if (totalMatches >= maxResults) return@forEach

                        if (regex.containsMatchIn(line)) {
                            val relativePath = file.relativeTo(baseDir).path
                            val lineNumber = index + 1

                            // Собираем контекст
                            val context = if (contextLines > 0) {
                                val start = maxOf(0, index - contextLines)
                                val end = minOf(lines.size - 1, index + contextLines)
                                (start..end).map { i ->
                                    ContextLine(i + 1, lines[i], i == index)
                                }
                            } else {
                                emptyList()
                            }

                            results.add(
                                SearchMatch(
                                    file = relativePath,
                                    lineNumber = lineNumber,
                                    line = line.trim(),
                                    context = context
                                )
                            )
                            totalMatches++
                        }
                    }
                } catch (e: Exception) {
                    // Пропускаем файлы с ошибками чтения
                }
            }

        return results
    }

    private fun isExcluded(file: File, baseDir: File): Boolean {
        val relativePath = file.relativeTo(baseDir).path
        return EXCLUDED_DIRS.any { dir ->
            relativePath.startsWith("$dir/") || relativePath.startsWith("$dir\\")
        }
    }

    private fun matchesPattern(filename: String, pattern: String): Boolean {
        val regexPattern = pattern
            .replace(".", "\\.")
            .replace("*", ".*")
            .replace("?", ".")
        return Regex(regexPattern, RegexOption.IGNORE_CASE).matches(filename)
    }

    private fun formatResults(pattern: String, results: List<SearchMatch>, filePattern: String?): String {
        if (results.isEmpty()) {
            return buildString {
                appendLine("🔍 Поиск: \"$pattern\"")
                if (filePattern != null) {
                    appendLine("📄 Фильтр: $filePattern")
                }
                appendLine()
                appendLine("📭 Ничего не найдено")
            }
        }

        // Группируем по файлам
        val byFile = results.groupBy { it.file }

        return buildString {
            appendLine("🔍 Поиск: \"$pattern\"")
            if (filePattern != null) {
                appendLine("📄 Фильтр: $filePattern")
            }
            appendLine("━".repeat(50))
            appendLine()

            byFile.forEach { (file, matches) ->
                appendLine("📄 $file (${matches.size} совпадений)")
                appendLine("─".repeat(40))

                matches.forEach { match ->
                    if (match.context.isNotEmpty()) {
                        // С контекстом
                        match.context.forEach { ctx ->
                            val prefix = if (ctx.isMatch) " ▶ " else "   "
                            val lineNum = ctx.lineNumber.toString().padStart(4)
                            appendLine("$prefix$lineNum: ${ctx.content.take(100)}")
                        }
                        appendLine()
                    } else {
                        // Без контекста
                        val lineNum = match.lineNumber.toString().padStart(4)
                        appendLine("   $lineNum: ${match.line.take(100)}")
                    }
                }
                appendLine()
            }

            appendLine("━".repeat(50))
            appendLine("Найдено: ${results.size} совпадений в ${byFile.size} файлах")
        }
    }

    private data class SearchMatch(
        val file: String,
        val lineNumber: Int,
        val line: String,
        val context: List<ContextLine>
    )

    private data class ContextLine(
        val lineNumber: Int,
        val content: String,
        val isMatch: Boolean
    )
}
