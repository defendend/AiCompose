package org.example.mcp.tools

import io.ktor.client.*
import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.server.*
import kotlinx.serialization.json.*
import org.example.integrations.YandexTrackerClient

/**
 * MCP инструмент для работы с Яндекс.Трекером
 *
 * Предоставляет методы для:
 * - Получения количества открытых задач
 * - Поиска задач по фильтрам
 * - Получения деталей задачи
 */
class YandexTrackerMcpTool(
    private val httpClient: HttpClient,
    private val oauthToken: String?,
    private val orgId: String?
) {
    private val trackerClient: YandexTrackerClient? by lazy {
        if (oauthToken != null && orgId != null) {
            YandexTrackerClient(httpClient, oauthToken, orgId)
        } else {
            null
        }
    }

    /**
     * Регистрирует инструменты Яндекс.Трекера в MCP сервере
     */
    fun register(server: Server) {
        // Инструмент: получить количество открытых задач
        server.addTool(
            name = "yandex_tracker_get_open_issues_count",
            description = "Получает количество открытых задач в очереди Яндекс.Трекера"
        ) { request ->
            val args = request.arguments?.jsonObject
            val queue = args?.get("queue")?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent(text = "Ошибка: не указана очередь (queue)"))
                )

            try {
                if (trackerClient == null) {
                    return@addTool CallToolResult(
                        content = listOf(TextContent(
                            text = "⚠️ Яндекс.Трекер не настроен. " +
                                    "Установите переменные окружения YANDEX_TRACKER_TOKEN и YANDEX_TRACKER_ORG_ID"
                        ))
                    )
                }

                val count = trackerClient!!.getOpenIssuesCount(queue)
                CallToolResult(
                    content = listOf(TextContent(
                        text = "📊 В очереди '$queue' найдено открытых задач: $count"
                    ))
                )
            } catch (e: Exception) {
                CallToolResult(
                    content = listOf(TextContent(
                        text = "❌ Ошибка при обращении к Яндекс.Трекеру: ${e.message}"
                    ))
                )
            }
        }

        // Инструмент: поиск задач
        server.addTool(
            name = "yandex_tracker_search_issues",
            description = "Ищет задачи в Яндекс.Трекере по фильтрам (очередь, статус, исполнитель)"
        ) { request ->
            val args = request.arguments?.jsonObject
            val queue = args?.get("queue")?.jsonPrimitive?.content
            val status = args?.get("status")?.jsonPrimitive?.content
            val assignee = args?.get("assignee")?.jsonPrimitive?.content

            try {
                if (trackerClient == null) {
                    return@addTool CallToolResult(
                        content = listOf(TextContent(
                            text = "⚠️ Яндекс.Трекер не настроен. " +
                                    "Установите переменные окружения YANDEX_TRACKER_TOKEN и YANDEX_TRACKER_ORG_ID"
                        ))
                    )
                }

                val result = trackerClient!!.searchIssues(
                    queue = queue,
                    status = status,
                    assignee = assignee
                )

                val issuesText = result.issues.joinToString("\n") { issue ->
                    "• ${issue.key}: ${issue.summary} [${issue.status?.display ?: "нет статуса"}]"
                }

                CallToolResult(
                    content = listOf(TextContent(
                        text = "🔍 Найдено задач: ${result.totalCount}\n\n$issuesText"
                    ))
                )
            } catch (e: Exception) {
                CallToolResult(
                    content = listOf(TextContent(
                        text = "❌ Ошибка при поиске задач: ${e.message}"
                    ))
                )
            }
        }

        // Инструмент: получить детали задачи
        server.addTool(
            name = "yandex_tracker_get_issue",
            description = "Получает детальную информацию о задаче по её ключу (например, PROJECT-123)"
        ) { request ->
            val args = request.arguments?.jsonObject
            val issueKey = args?.get("issue_key")?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent(text = "Ошибка: не указан ключ задачи (issue_key)"))
                )

            try {
                if (trackerClient == null) {
                    return@addTool CallToolResult(
                        content = listOf(TextContent(
                            text = "⚠️ Яндекс.Трекер не настроен. " +
                                    "Установите переменные окружения YANDEX_TRACKER_TOKEN и YANDEX_TRACKER_ORG_ID"
                        ))
                    )
                }

                val issue = trackerClient!!.getIssue(issueKey)

                val details = buildString {
                    appendLine("📋 Задача: ${issue.key}")
                    appendLine("Название: ${issue.summary}")
                    issue.description?.let { appendLine("Описание: $it") }
                    issue.status?.let { appendLine("Статус: ${it.display}") }
                    issue.assignee?.let { appendLine("Исполнитель: ${it.display}") }
                    issue.queue?.let { appendLine("Очередь: ${it.display}") }
                }

                CallToolResult(
                    content = listOf(TextContent(text = details.trim()))
                )
            } catch (e: Exception) {
                CallToolResult(
                    content = listOf(TextContent(
                        text = "❌ Ошибка при получении задачи: ${e.message}"
                    ))
                )
            }
        }
    }
}
