package org.example.tools.devassistant.git

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.example.tools.annotations.Param
import org.example.tools.annotations.Tool

/**
 * Инструмент для получения списка файлов в git репозитории.
 */
@Tool(
    name = "git_files",
    description = "Получить список файлов в git репозитории: все отслеживаемые файлы или только изменённые"
)
@Param(
    name = "modified_only",
    description = "Показать только изменённые файлы (по умолчанию: false)",
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
    name = "pattern",
    description = "Фильтр по паттерну (например: *.kt)",
    type = "string",
    required = false
)
object GitFilesTool : GitToolBase() {

    override suspend fun execute(arguments: String): String {
        val json = Json.parseToJsonElement(arguments).jsonObject
        val modifiedOnly = json["modified_only"]?.jsonPrimitive?.booleanOrNull ?: false
        val path = json["path"]?.jsonPrimitive?.content
            ?: System.getenv("PROJECT_PATH")
            ?: "."
        val pattern = json["pattern"]?.jsonPrimitive?.content

        val result = if (modifiedOnly) {
            // Получаем изменённые файлы (staged + unstaged + untracked)
            getModifiedFiles(path, pattern)
        } else {
            // Получаем все отслеживаемые файлы
            getAllTrackedFiles(path, pattern)
        }

        return result
    }

    private fun getModifiedFiles(path: String, pattern: String?): String {
        // Staged и unstaged изменения
        val diffResult = runGitCommand("diff", "--name-status", "HEAD", workDir = path)

        // Untracked файлы
        val untrackedResult = runGitCommand("ls-files", "--others", "--exclude-standard", workDir = path)

        val files = mutableMapOf<String, String>()

        if (diffResult.success) {
            diffResult.output.lines().filter { it.isNotBlank() }.forEach { line ->
                val parts = line.split("\t", limit = 2)
                if (parts.size == 2) {
                    val status = parts[0]
                    val filePath = parts[1]
                    files[filePath] = getStatusDescription(status)
                }
            }
        }

        if (untrackedResult.success) {
            untrackedResult.output.lines().filter { it.isNotBlank() }.forEach { filePath ->
                files[filePath] = "untracked"
            }
        }

        // Применяем фильтр
        val filteredFiles = if (pattern != null) {
            files.filter { matchesPattern(it.key, pattern) }
        } else {
            files
        }

        return formatModifiedFiles(filteredFiles, pattern)
    }

    private fun getAllTrackedFiles(path: String, pattern: String?): String {
        val args = mutableListOf("ls-files")
        if (pattern != null) {
            args.add(pattern)
        }

        val result = runGitCommand(*args.toTypedArray(), workDir = path)

        if (!result.success) {
            return "❌ ${result.output}"
        }

        val files = result.output.lines().filter { it.isNotBlank() }

        return formatAllFiles(files, pattern)
    }

    private fun formatModifiedFiles(files: Map<String, String>, pattern: String?): String {
        return buildString {
            appendLine("📁 Изменённые файлы")
            appendLine("━".repeat(50))
            if (pattern != null) {
                appendLine("🔍 Фильтр: $pattern")
            }
            appendLine()

            if (files.isEmpty()) {
                appendLine("✨ Нет изменённых файлов")
                return@buildString
            }

            // Группируем по статусу
            val byStatus = files.entries.groupBy { it.value }

            byStatus.forEach { (status, entries) ->
                val icon = getStatusIcon(status)
                appendLine("$icon ${status.replaceFirstChar { it.uppercase() }} (${entries.size}):")
                entries.forEach { (file, _) ->
                    appendLine("   $file")
                }
                appendLine()
            }

            appendLine("Всего: ${files.size} файлов")
        }
    }

    private fun formatAllFiles(files: List<String>, pattern: String?): String {
        return buildString {
            appendLine("📁 Отслеживаемые файлы")
            appendLine("━".repeat(50))
            if (pattern != null) {
                appendLine("🔍 Фильтр: $pattern")
            }
            appendLine()

            if (files.isEmpty()) {
                appendLine("📭 Файлы не найдены")
                return@buildString
            }

            // Группируем по расширению
            val byExtension = files.groupBy { file ->
                file.substringAfterLast(".", "no extension")
            }

            byExtension.entries.sortedByDescending { it.value.size }.forEach { (ext, extFiles) ->
                appendLine("📄 .$ext (${extFiles.size}):")
                extFiles.take(10).forEach { file ->
                    appendLine("   $file")
                }
                if (extFiles.size > 10) {
                    appendLine("   ... и ещё ${extFiles.size - 10}")
                }
                appendLine()
            }

            appendLine("Всего: ${files.size} файлов")
        }
    }

    private fun getStatusDescription(status: String): String = when (status) {
        "M" -> "modified"
        "A" -> "added"
        "D" -> "deleted"
        "R" -> "renamed"
        "C" -> "copied"
        "U" -> "unmerged"
        else -> status.lowercase()
    }

    private fun getStatusIcon(status: String): String = when (status) {
        "modified" -> "📝"
        "added" -> "➕"
        "deleted" -> "🗑️"
        "renamed" -> "📛"
        "copied" -> "📋"
        "unmerged" -> "⚠️"
        "untracked" -> "❓"
        else -> "📄"
    }

    private fun matchesPattern(filename: String, pattern: String): Boolean {
        val regexPattern = pattern
            .replace(".", "\\.")
            .replace("*", ".*")
            .replace("?", ".")
        return Regex(regexPattern, RegexOption.IGNORE_CASE).containsMatchIn(filename)
    }
}
