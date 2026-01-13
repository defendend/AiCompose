package org.example.tools.devassistant.github

import kotlinx.serialization.json.*
import org.example.tools.annotations.Param
import org.example.tools.annotations.Tool

/**
 * Инструмент для получения diff Pull Request.
 */
@Tool(
    name = "github_get_pr_diff",
    description = "Получить diff (изменения) Pull Request в unified diff формате"
)
@Param(
    name = "owner",
    description = "Владелец репозитория",
    type = "string",
    required = true
)
@Param(
    name = "repo",
    description = "Название репозитория",
    type = "string",
    required = true
)
@Param(
    name = "pr_number",
    description = "Номер Pull Request",
    type = "integer",
    required = true
)
@Param(
    name = "token",
    description = "GitHub Personal Access Token",
    type = "string",
    required = true
)
@Param(
    name = "max_lines",
    description = "Максимальное количество строк diff (по умолчанию: 500)",
    type = "integer",
    required = false
)
object GithubPrDiffTool : GithubToolBase() {

    override suspend fun execute(arguments: String): String {
        return try {
            val json = Json.parseToJsonElement(arguments).jsonObject
            val owner = requireParam(json["owner"]?.jsonPrimitive?.content, "owner")
            val repo = requireParam(json["repo"]?.jsonPrimitive?.content, "repo")
            val prNumber = json["pr_number"]?.jsonPrimitive?.intOrNull
                ?: throw IllegalArgumentException("Параметр 'pr_number' обязателен")
            val token = requireParam(json["token"]?.jsonPrimitive?.content, "token")
            val maxLines = json["max_lines"]?.jsonPrimitive?.intOrNull ?: 500

            // Запрашиваем diff формат
            val response = githubGet(
                "/repos/$owner/$repo/pulls/$prNumber",
                token,
                accept = "application/vnd.github.v3.diff"
            )

            if (!response.success) {
                return formatError("${response.statusCode}: ${response.error ?: response.body}")
            }

            formatDiff(response.body, maxLines, owner, repo, prNumber)
        } catch (e: IllegalArgumentException) {
            formatError(e.message ?: "Неверные параметры")
        } catch (e: Exception) {
            formatError(e.message ?: "Неизвестная ошибка")
        }
    }

    private fun formatDiff(diff: String, maxLines: Int, owner: String, repo: String, prNumber: Int): String {
        val lines = diff.lines()
        val totalLines = lines.size

        // Подсчитываем статистику
        var additions = 0
        var deletions = 0
        val files = mutableSetOf<String>()
        var currentFile: String? = null

        lines.forEach { line ->
            when {
                line.startsWith("diff --git") -> {
                    val match = Regex("b/(.+)$").find(line)
                    currentFile = match?.groupValues?.get(1)
                    currentFile?.let { files.add(it) }
                }
                line.startsWith("+") && !line.startsWith("+++") -> additions++
                line.startsWith("-") && !line.startsWith("---") -> deletions++
            }
        }

        val truncatedLines = lines.take(maxLines)
        val truncated = totalLines > maxLines

        return buildString {
            appendLine("📝 Diff для PR #$prNumber ($owner/$repo)")
            appendLine("━".repeat(50))
            appendLine()
            appendLine("📊 Статистика:")
            appendLine("   📁 Файлов изменено: ${files.size}")
            appendLine("   ➕ Добавлено: $additions строк")
            appendLine("   ➖ Удалено: $deletions строк")
            appendLine("   📄 Всего строк diff: $totalLines")
            appendLine()
            appendLine("📄 Изменённые файлы:")
            files.forEach { file ->
                appendLine("   • $file")
            }
            appendLine()
            appendLine("─".repeat(50))
            appendLine()
            appendLine("```diff")
            appendLine(truncatedLines.joinToString("\n"))
            if (truncated) {
                appendLine()
                appendLine("... (показано $maxLines из $totalLines строк)")
            }
            appendLine("```")
        }
    }
}
