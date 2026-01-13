package org.example.tools.devassistant.github

import kotlinx.serialization.json.*
import org.example.tools.annotations.Param
import org.example.tools.annotations.Tool

/**
 * Инструмент для публикации ревью на Pull Request.
 */
@Tool(
    name = "github_post_review",
    description = "Опубликовать ревью на Pull Request с комментариями"
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
    name = "body",
    description = "Текст ревью (общий комментарий)",
    type = "string",
    required = true
)
@Param(
    name = "event",
    description = "Тип ревью: APPROVE, REQUEST_CHANGES, или COMMENT (по умолчанию: COMMENT)",
    type = "string",
    required = false
)
@Param(
    name = "comments",
    description = "JSON массив комментариев к строкам: [{\"path\": \"file.kt\", \"line\": 10, \"body\": \"Comment\"}]",
    type = "string",
    required = false
)
object GithubPostReviewTool : GithubToolBase() {

    override suspend fun execute(arguments: String): String {
        return try {
            val json = Json.parseToJsonElement(arguments).jsonObject
            val owner = requireParam(json["owner"]?.jsonPrimitive?.content, "owner")
            val repo = requireParam(json["repo"]?.jsonPrimitive?.content, "repo")
            val prNumber = json["pr_number"]?.jsonPrimitive?.intOrNull
                ?: throw IllegalArgumentException("Параметр 'pr_number' обязателен")
            val token = requireParam(json["token"]?.jsonPrimitive?.content, "token")
            val body = requireParam(json["body"]?.jsonPrimitive?.content, "body")
            val event = json["event"]?.jsonPrimitive?.content?.uppercase() ?: "COMMENT"
            val commentsJson = json["comments"]?.jsonPrimitive?.content

            // Валидация event
            val validEvents = setOf("APPROVE", "REQUEST_CHANGES", "COMMENT")
            if (event !in validEvents) {
                return formatError("Неверный event: $event. Допустимые: $validEvents")
            }

            // Парсим комментарии если есть
            val comments = commentsJson?.let { parseComments(it) } ?: emptyList()

            // Формируем запрос
            val requestBody = buildJsonObject {
                put("body", body)
                put("event", event)
                if (comments.isNotEmpty()) {
                    putJsonArray("comments") {
                        comments.forEach { comment ->
                            addJsonObject {
                                put("path", comment.path)
                                put("line", comment.line)
                                put("body", comment.body)
                            }
                        }
                    }
                }
            }.toString()

            val response = githubPost(
                "/repos/$owner/$repo/pulls/$prNumber/reviews",
                token,
                requestBody
            )

            if (!response.success) {
                return formatError("${response.statusCode}: ${response.error ?: response.body}")
            }

            formatReviewResult(response.body, owner, repo, prNumber, event, comments.size)
        } catch (e: IllegalArgumentException) {
            formatError(e.message ?: "Неверные параметры")
        } catch (e: Exception) {
            formatError(e.message ?: "Неизвестная ошибка")
        }
    }

    private data class ReviewComment(
        val path: String,
        val line: Int,
        val body: String
    )

    private fun parseComments(json: String): List<ReviewComment> {
        return try {
            val array = Json.parseToJsonElement(json).jsonArray
            array.map { element ->
                val obj = element.jsonObject
                ReviewComment(
                    path = obj["path"]?.jsonPrimitive?.content ?: "",
                    line = obj["line"]?.jsonPrimitive?.intOrNull ?: 0,
                    body = obj["body"]?.jsonPrimitive?.content ?: ""
                )
            }.filter { it.path.isNotBlank() && it.line > 0 && it.body.isNotBlank() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun formatReviewResult(
        jsonBody: String,
        owner: String,
        repo: String,
        prNumber: Int,
        event: String,
        commentsCount: Int
    ): String {
        val review = Json.parseToJsonElement(jsonBody).jsonObject
        val reviewId = review["id"]?.jsonPrimitive?.longOrNull ?: 0
        val state = review["state"]?.jsonPrimitive?.content ?: event
        val htmlUrl = review["html_url"]?.jsonPrimitive?.content ?: ""

        val eventIcon = when (event) {
            "APPROVE" -> "✅"
            "REQUEST_CHANGES" -> "🔴"
            else -> "💬"
        }

        return buildString {
            appendLine("$eventIcon Ревью опубликовано!")
            appendLine("━".repeat(50))
            appendLine()
            appendLine("📋 PR: #$prNumber ($owner/$repo)")
            appendLine("🆔 Review ID: $reviewId")
            appendLine("📊 Статус: $state")
            appendLine("💬 Комментариев: $commentsCount")
            appendLine()
            if (htmlUrl.isNotBlank()) {
                appendLine("🔗 Ссылка: $htmlUrl")
            }
        }
    }
}
