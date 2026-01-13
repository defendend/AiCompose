package org.example.tools.devassistant.github

import kotlinx.serialization.json.*
import org.example.tools.annotations.Param
import org.example.tools.annotations.Tool

/**
 * Инструмент для получения списка файлов Pull Request.
 */
@Tool(
    name = "github_get_pr_files",
    description = "Получить список изменённых файлов в Pull Request с детальной информацией"
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
object GithubPrFilesTool : GithubToolBase() {

    override suspend fun execute(arguments: String): String {
        return try {
            val json = Json.parseToJsonElement(arguments).jsonObject
            val owner = requireParam(json["owner"]?.jsonPrimitive?.content, "owner")
            val repo = requireParam(json["repo"]?.jsonPrimitive?.content, "repo")
            val prNumber = json["pr_number"]?.jsonPrimitive?.intOrNull
                ?: throw IllegalArgumentException("Параметр 'pr_number' обязателен")
            val token = requireParam(json["token"]?.jsonPrimitive?.content, "token")

            val response = githubGet("/repos/$owner/$repo/pulls/$prNumber/files", token)

            if (!response.success) {
                return formatError("${response.statusCode}: ${response.error ?: response.body}")
            }

            formatFiles(response.body, owner, repo, prNumber)
        } catch (e: IllegalArgumentException) {
            formatError(e.message ?: "Неверные параметры")
        } catch (e: Exception) {
            formatError(e.message ?: "Неизвестная ошибка")
        }
    }

    private fun formatFiles(jsonBody: String, owner: String, repo: String, prNumber: Int): String {
        val files = Json.parseToJsonElement(jsonBody).jsonArray

        var totalAdditions = 0
        var totalDeletions = 0

        data class FileInfo(
            val filename: String,
            val status: String,
            val additions: Int,
            val deletions: Int,
            val changes: Int,
            val patch: String?
        )

        val fileInfos = files.map { fileElement ->
            val file = fileElement.jsonObject
            val additions = file["additions"]?.jsonPrimitive?.intOrNull ?: 0
            val deletions = file["deletions"]?.jsonPrimitive?.intOrNull ?: 0
            totalAdditions += additions
            totalDeletions += deletions

            FileInfo(
                filename = file["filename"]?.jsonPrimitive?.content ?: "unknown",
                status = file["status"]?.jsonPrimitive?.content ?: "unknown",
                additions = additions,
                deletions = deletions,
                changes = file["changes"]?.jsonPrimitive?.intOrNull ?: 0,
                patch = file["patch"]?.jsonPrimitive?.content
            )
        }

        // Группируем по директориям
        val byDirectory = fileInfos.groupBy { file ->
            val parts = file.filename.split("/")
            if (parts.size > 1) parts.dropLast(1).joinToString("/") else "."
        }

        return buildString {
            appendLine("📁 Файлы PR #$prNumber ($owner/$repo)")
            appendLine("━".repeat(50))
            appendLine()
            appendLine("📊 Общая статистика:")
            appendLine("   📁 Файлов: ${fileInfos.size}")
            appendLine("   ➕ Добавлено: $totalAdditions строк")
            appendLine("   ➖ Удалено: $totalDeletions строк")
            appendLine()

            // Группируем по статусу
            val byStatus = fileInfos.groupBy { it.status }
            appendLine("📋 По статусу:")
            byStatus.forEach { (status, files) ->
                val icon = getStatusIcon(status)
                appendLine("   $icon ${status.replaceFirstChar { it.uppercase() }}: ${files.size}")
            }
            appendLine()

            appendLine("📂 Структура изменений:")
            appendLine("─".repeat(40))

            byDirectory.entries.sortedBy { it.key }.forEach { (dir, files) ->
                appendLine()
                appendLine("📁 $dir/")
                files.sortedBy { it.filename }.forEach { file ->
                    val icon = getStatusIcon(file.status)
                    val name = file.filename.substringAfterLast("/")
                    val stats = "+${file.additions}/-${file.deletions}"
                    appendLine("   $icon $name ($stats)")
                }
            }

            appendLine()
            appendLine("─".repeat(40))
            appendLine()
            appendLine("📄 Детали по файлам:")

            fileInfos.take(20).forEach { file ->
                val icon = getStatusIcon(file.status)
                appendLine()
                appendLine("$icon ${file.filename}")
                appendLine("   Статус: ${file.status}")
                appendLine("   Изменения: +${file.additions}/-${file.deletions} (${file.changes} всего)")

                // Показываем краткий patch если есть
                file.patch?.let { patch ->
                    val patchLines = patch.lines().take(10)
                    if (patchLines.isNotEmpty()) {
                        appendLine("   Превью:")
                        patchLines.forEach { line ->
                            appendLine("   │ ${line.take(80)}")
                        }
                        if (patch.lines().size > 10) {
                            appendLine("   │ ... (ещё ${patch.lines().size - 10} строк)")
                        }
                    }
                }
            }

            if (fileInfos.size > 20) {
                appendLine()
                appendLine("... и ещё ${fileInfos.size - 20} файлов")
            }
        }
    }

    private fun getStatusIcon(status: String): String = when (status) {
        "added" -> "➕"
        "removed" -> "🗑️"
        "modified" -> "📝"
        "renamed" -> "📛"
        "copied" -> "📋"
        else -> "📄"
    }
}
