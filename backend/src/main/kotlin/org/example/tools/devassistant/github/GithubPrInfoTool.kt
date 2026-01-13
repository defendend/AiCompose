package org.example.tools.devassistant.github

import kotlinx.serialization.json.*
import org.example.tools.annotations.Param
import org.example.tools.annotations.Tool

/**
 * Инструмент для получения информации о Pull Request.
 */
@Tool(
    name = "github_get_pr_info",
    description = "Получить информацию о Pull Request: заголовок, описание, автор, ветки, статус"
)
@Param(
    name = "owner",
    description = "Владелец репозитория (например: anthropics)",
    type = "string",
    required = true
)
@Param(
    name = "repo",
    description = "Название репозитория (например: claude-code)",
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
object GithubPrInfoTool : GithubToolBase() {

    override suspend fun execute(arguments: String): String {
        return try {
            val json = Json.parseToJsonElement(arguments).jsonObject
            val owner = requireParam(json["owner"]?.jsonPrimitive?.content, "owner")
            val repo = requireParam(json["repo"]?.jsonPrimitive?.content, "repo")
            val prNumber = json["pr_number"]?.jsonPrimitive?.intOrNull
                ?: throw IllegalArgumentException("Параметр 'pr_number' обязателен")
            val token = requireParam(json["token"]?.jsonPrimitive?.content, "token")

            val response = githubGet("/repos/$owner/$repo/pulls/$prNumber", token)

            if (!response.success) {
                return formatError("${response.statusCode}: ${response.error ?: response.body}")
            }

            formatPrInfo(response.body)
        } catch (e: IllegalArgumentException) {
            formatError(e.message ?: "Неверные параметры")
        } catch (e: Exception) {
            formatError(e.message ?: "Неизвестная ошибка")
        }
    }

    private fun formatPrInfo(jsonBody: String): String {
        val pr = Json.parseToJsonElement(jsonBody).jsonObject

        val number = pr["number"]?.jsonPrimitive?.intOrNull ?: 0
        val title = pr["title"]?.jsonPrimitive?.content ?: "N/A"
        val state = pr["state"]?.jsonPrimitive?.content ?: "unknown"
        val body = pr["body"]?.jsonPrimitive?.content ?: ""
        val draft = pr["draft"]?.jsonPrimitive?.booleanOrNull ?: false
        val mergeable = pr["mergeable"]?.jsonPrimitive?.booleanOrNull
        val mergeableState = pr["mergeable_state"]?.jsonPrimitive?.content ?: "unknown"

        val user = pr["user"]?.jsonObject
        val authorLogin = user?.get("login")?.jsonPrimitive?.content ?: "unknown"

        val head = pr["head"]?.jsonObject
        val headRef = head?.get("ref")?.jsonPrimitive?.content ?: "unknown"

        val base = pr["base"]?.jsonObject
        val baseRef = base?.get("ref")?.jsonPrimitive?.content ?: "unknown"

        val additions = pr["additions"]?.jsonPrimitive?.intOrNull ?: 0
        val deletions = pr["deletions"]?.jsonPrimitive?.intOrNull ?: 0
        val changedFiles = pr["changed_files"]?.jsonPrimitive?.intOrNull ?: 0
        val commits = pr["commits"]?.jsonPrimitive?.intOrNull ?: 0

        val createdAt = pr["created_at"]?.jsonPrimitive?.content ?: ""
        val updatedAt = pr["updated_at"]?.jsonPrimitive?.content ?: ""

        val labels = pr["labels"]?.jsonArray?.mapNotNull {
            it.jsonObject["name"]?.jsonPrimitive?.content
        } ?: emptyList()

        val stateIcon = when (state) {
            "open" -> "🟢"
            "closed" -> "🔴"
            "merged" -> "🟣"
            else -> "⚪"
        }

        val draftLabel = if (draft) " [DRAFT]" else ""
        val mergeableIcon = when (mergeable) {
            true -> "✅"
            false -> "❌"
            null -> "❓"
        }

        return buildString {
            appendLine("📋 Pull Request #$number$draftLabel")
            appendLine("━".repeat(50))
            appendLine()
            appendLine("📌 Заголовок: $title")
            appendLine("$stateIcon Статус: $state")
            appendLine("👤 Автор: @$authorLogin")
            appendLine()
            appendLine("🌿 Ветки: $headRef → $baseRef")
            appendLine("$mergeableIcon Mergeable: ${mergeable ?: "checking..."} ($mergeableState)")
            appendLine()
            appendLine("📊 Изменения:")
            appendLine("   📁 Файлов: $changedFiles")
            appendLine("   ➕ Добавлено: $additions строк")
            appendLine("   ➖ Удалено: $deletions строк")
            appendLine("   📝 Коммитов: $commits")
            appendLine()
            if (labels.isNotEmpty()) {
                appendLine("🏷️ Метки: ${labels.joinToString(", ")}")
                appendLine()
            }
            appendLine("📅 Создан: $createdAt")
            appendLine("📅 Обновлён: $updatedAt")
            if (body.isNotBlank()) {
                appendLine()
                appendLine("📝 Описание:")
                appendLine("─".repeat(40))
                appendLine(body.take(1000))
                if (body.length > 1000) {
                    appendLine("... (обрезано)")
                }
            }
        }
    }
}
