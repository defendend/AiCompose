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
 * Инструмент для чтения содержимого файла.
 */
@Tool(
    name = "file_read",
    description = "Прочитать содержимое файла. Поддерживает ограничение по строкам"
)
@Param(
    name = "path",
    description = "Путь к файлу",
    type = "string",
    required = true
)
@Param(
    name = "start_line",
    description = "Начальная строка (по умолчанию: 1)",
    type = "integer",
    required = false
)
@Param(
    name = "end_line",
    description = "Конечная строка (по умолчанию: до конца файла, максимум 500 строк)",
    type = "integer",
    required = false
)
object FileReadTool : AnnotatedAgentTool() {

    private const val MAX_LINES = 500
    private const val MAX_LINE_LENGTH = 500

    // Бинарные расширения
    private val BINARY_EXTENSIONS = setOf(
        "jar", "war", "class", "exe", "dll", "so", "dylib",
        "png", "jpg", "jpeg", "gif", "ico", "pdf", "zip",
        "tar", "gz", "rar", "7z", "bin", "dat"
    )

    override suspend fun execute(arguments: String): String {
        val json = Json.parseToJsonElement(arguments).jsonObject
        val path = json["path"]?.jsonPrimitive?.content
            ?: return "❌ Ошибка: path не может быть пустым"
        val startLine = json["start_line"]?.jsonPrimitive?.intOrNull ?: 1
        val endLine = json["end_line"]?.jsonPrimitive?.intOrNull

        val file = File(path)
        if (!file.exists()) {
            return "❌ Файл не найден: $path"
        }

        if (file.isDirectory) {
            return "❌ Это директория, а не файл: $path"
        }

        if (file.extension.lowercase() in BINARY_EXTENSIONS) {
            return "⚠️ Это бинарный файл: $path\nТип: ${file.extension}"
        }

        return try {
            val lines = file.readLines()
            val totalLines = lines.size

            // Нормализуем диапазон
            val start = maxOf(1, startLine)
            val end = minOf(
                endLine ?: (start + MAX_LINES - 1),
                totalLines,
                start + MAX_LINES - 1
            )

            if (start > totalLines) {
                return "❌ Начальная строка ($start) больше общего количества строк ($totalLines)"
            }

            formatFileContent(file, lines, start, end, totalLines)
        } catch (e: Exception) {
            "❌ Ошибка чтения файла: ${e.message}"
        }
    }

    private fun formatFileContent(
        file: File,
        lines: List<String>,
        start: Int,
        end: Int,
        totalLines: Int
    ): String {
        val selectedLines = lines.subList(start - 1, end)
        val extension = file.extension.lowercase()

        return buildString {
            appendLine("📄 ${file.name}")
            appendLine("━".repeat(50))
            appendLine("📍 Строки: $start-$end из $totalLines")
            appendLine("📦 Размер: ${formatFileSize(file.length())}")
            if (extension.isNotEmpty()) {
                appendLine("🔤 Тип: $extension")
            }
            appendLine()

            // Определяем язык для подсветки (можно использовать в UI)
            val language = getLanguage(extension)
            if (language.isNotEmpty()) {
                appendLine("```$language")
            }

            selectedLines.forEachIndexed { index, line ->
                val lineNumber = (start + index).toString().padStart(4)
                val truncatedLine = if (line.length > MAX_LINE_LENGTH) {
                    line.take(MAX_LINE_LENGTH) + "..."
                } else {
                    line
                }
                appendLine("$lineNumber │ $truncatedLine")
            }

            if (language.isNotEmpty()) {
                appendLine("```")
            }

            if (end < totalLines) {
                appendLine()
                appendLine("... ещё ${totalLines - end} строк")
            }
        }
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${bytes / (1024 * 1024)} MB"
        }
    }

    private fun getLanguage(extension: String): String = when (extension) {
        "kt" -> "kotlin"
        "java" -> "java"
        "js", "jsx" -> "javascript"
        "ts", "tsx" -> "typescript"
        "py" -> "python"
        "rb" -> "ruby"
        "go" -> "go"
        "rs" -> "rust"
        "swift" -> "swift"
        "c", "h" -> "c"
        "cpp", "hpp", "cc" -> "cpp"
        "cs" -> "csharp"
        "sh", "bash" -> "bash"
        "sql" -> "sql"
        "html" -> "html"
        "css" -> "css"
        "json" -> "json"
        "xml" -> "xml"
        "yaml", "yml" -> "yaml"
        "md" -> "markdown"
        "gradle" -> "groovy"
        else -> ""
    }
}
