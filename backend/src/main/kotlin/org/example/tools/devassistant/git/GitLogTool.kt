package org.example.tools.devassistant.git

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.example.tools.annotations.Param
import org.example.tools.annotations.Tool

/**
 * Инструмент для получения истории коммитов git репозитория.
 */
@Tool(
    name = "git_log",
    description = "Получить историю коммитов git репозитория"
)
@Param(
    name = "limit",
    description = "Количество коммитов (по умолчанию: 10)",
    type = "integer",
    required = false
)
@Param(
    name = "oneline",
    description = "Компактный формат (по умолчанию: false)",
    type = "boolean",
    required = false
)
@Param(
    name = "path",
    description = "Путь к репозиторию (по умолчанию: текущая директория)",
    type = "string",
    required = false
)
@Param(
    name = "file",
    description = "Показать историю конкретного файла",
    type = "string",
    required = false
)
object GitLogTool : GitToolBase() {

    override suspend fun execute(arguments: String): String {
        val json = Json.parseToJsonElement(arguments).jsonObject
        val limit = json["limit"]?.jsonPrimitive?.intOrNull ?: 10
        val oneline = json["oneline"]?.jsonPrimitive?.booleanOrNull ?: false
        val path = json["path"]?.jsonPrimitive?.content ?: "."
        val file = json["file"]?.jsonPrimitive?.content

        val args = mutableListOf("log", "-n", limit.toString())

        if (oneline) {
            args.add("--oneline")
        } else {
            // Кастомный формат: hash|author|date|subject
            args.add("--format=%H|%an|%ad|%s")
            args.add("--date=short")
        }

        if (file != null) {
            args.add("--")
            args.add(file)
        }

        val result = runGitCommand(*args.toTypedArray(), workDir = path)

        if (!result.success) {
            return "❌ ${result.output}"
        }

        return if (oneline) {
            formatOnelineLog(result.output)
        } else {
            formatDetailedLog(result.output, file)
        }
    }

    private fun formatOnelineLog(output: String): String {
        val lines = output.lines().filter { it.isNotBlank() }

        return buildString {
            appendLine("📜 Git Log (compact)")
            appendLine("━".repeat(50))
            appendLine()
            lines.forEach { line ->
                val parts = line.split(" ", limit = 2)
                val hash = parts.getOrNull(0)?.take(7) ?: ""
                val message = parts.getOrNull(1) ?: ""
                appendLine("• $hash $message")
            }
        }
    }

    private fun formatDetailedLog(output: String, file: String?): String {
        val lines = output.lines().filter { it.isNotBlank() }

        return buildString {
            if (file != null) {
                appendLine("📜 Git Log для: $file")
            } else {
                appendLine("📜 Git Log")
            }
            appendLine("━".repeat(50))
            appendLine()

            lines.forEach { line ->
                val parts = line.split("|")
                if (parts.size >= 4) {
                    val hash = parts[0].take(7)
                    val author = parts[1]
                    val date = parts[2]
                    val message = parts[3]

                    appendLine("📌 $hash")
                    appendLine("   👤 $author • 📅 $date")
                    appendLine("   💬 $message")
                    appendLine()
                }
            }
        }
    }
}
