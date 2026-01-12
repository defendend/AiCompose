package org.example.tools.devassistant.git

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.example.tools.annotations.Param
import org.example.tools.annotations.Tool

/**
 * Инструмент для показа изменений в git репозитории.
 */
@Tool(
    name = "git_diff",
    description = "Показать изменения в файлах git репозитория"
)
@Param(
    name = "staged",
    description = "Показать staged изменения (по умолчанию: false - показывает unstaged)",
    type = "boolean",
    required = false
)
@Param(
    name = "file",
    description = "Показать изменения конкретного файла",
    type = "string",
    required = false
)
@Param(
    name = "path",
    description = "Путь к репозиторию (по умолчанию: текущая директория)",
    type = "string",
    required = false
)
@Param(
    name = "stat",
    description = "Показать только статистику изменений (по умолчанию: false)",
    type = "boolean",
    required = false
)
object GitDiffTool : GitToolBase() {

    override suspend fun execute(arguments: String): String {
        val json = Json.parseToJsonElement(arguments).jsonObject
        val staged = json["staged"]?.jsonPrimitive?.booleanOrNull ?: false
        val file = json["file"]?.jsonPrimitive?.content
        val path = json["path"]?.jsonPrimitive?.content
            ?: System.getenv("PROJECT_PATH")
            ?: "."
        val stat = json["stat"]?.jsonPrimitive?.booleanOrNull ?: false

        val args = mutableListOf("diff")

        if (staged) {
            args.add("--staged")
        }

        if (stat) {
            args.add("--stat")
        }

        if (file != null) {
            args.add("--")
            args.add(file)
        }

        val result = runGitCommand(*args.toTypedArray(), workDir = path)

        if (!result.success) {
            return "❌ ${result.output}"
        }

        if (result.output.isBlank()) {
            val type = if (staged) "staged" else "unstaged"
            return "✨ Нет ${type} изменений" + (file?.let { " в файле $it" } ?: "")
        }

        return formatDiff(result.output, staged, stat)
    }

    private fun formatDiff(output: String, staged: Boolean, stat: Boolean): String {
        val type = if (staged) "Staged" else "Unstaged"

        return buildString {
            appendLine("📝 Git Diff ($type)")
            appendLine("━".repeat(50))
            appendLine()

            if (stat) {
                // Статистика - показываем как есть
                appendLine(output)
            } else {
                // Полный diff - форматируем
                val lines = output.lines()
                var currentFile: String? = null

                lines.forEach { line ->
                    when {
                        line.startsWith("diff --git") -> {
                            val filePath = line.substringAfterLast(" b/")
                            currentFile = filePath
                            appendLine()
                            appendLine("📄 $filePath")
                            appendLine("─".repeat(40))
                        }
                        line.startsWith("+++") || line.startsWith("---") -> {
                            // Пропускаем заголовки файлов
                        }
                        line.startsWith("@@") -> {
                            // Контекст изменений
                            val context = line.substringAfter("@@ ").substringBefore(" @@")
                            appendLine("   📍 $context")
                        }
                        line.startsWith("+") && !line.startsWith("+++") -> {
                            appendLine("   + ${line.drop(1)}")
                        }
                        line.startsWith("-") && !line.startsWith("---") -> {
                            appendLine("   - ${line.drop(1)}")
                        }
                        line.startsWith(" ") -> {
                            // Контекстные строки - можно пропустить для краткости
                        }
                    }
                }
            }
        }
    }
}
