package org.example.tools

import io.ktor.client.*
import kotlinx.serialization.json.*
import org.example.integrations.WeatherMcpClient
import org.example.integrations.YandexTrackerClient
import org.example.tools.core.AgentTool
import org.example.model.FunctionDefinition
import org.example.model.FunctionParameters
import org.example.model.PropertyDefinition
import org.example.model.Tool

/**
 * Адаптер MCP инструментов для использования в Agent
 *
 * Предоставляет инструменты Яндекс.Трекера и погоды (Open-Meteo) как обычные AgentTool
 */
class McpToolsAdapter(
    private val httpClient: HttpClient,
    private val trackerToken: String?,
    private val trackerOrgId: String?,
    private val weatherMcpClient: WeatherMcpClient?
) {
    private val trackerClient: YandexTrackerClient? by lazy {
        if (trackerToken != null && trackerOrgId != null) {
            YandexTrackerClient(httpClient, trackerToken, trackerOrgId)
        } else null
    }

    /**
     * Получить все доступные MCP инструменты
     */
    fun getTools(): List<AgentTool> = buildList {
        // Инструменты Яндекс.Трекера
        add(YandexTrackerGetOpenIssuesCount())
        add(YandexTrackerSearchIssues())
        add(YandexTrackerGetIssue())

        // Инструменты погоды (если доступны)
        if (weatherMcpClient != null) {
            add(WeatherGetCurrent())
            add(WeatherGetDetails())
            add(WeatherGetAirQuality())
        }
    }

    // Инструмент: получить количество открытых задач
    inner class YandexTrackerGetOpenIssuesCount : AgentTool {
        override val name = "yandex_tracker_get_open_issues_count"
        override val description = "Получает количество открытых задач в очереди Яндекс.Трекера"

        override fun getDefinition() = Tool(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = FunctionParameters(
                    type = "object",
                    properties = mapOf(
                        "queue" to PropertyDefinition(
                            type = "string",
                            description = "Ключ очереди (например, PROJECT, MYQUEUE)"
                        )
                    ),
                    required = listOf("queue")
                )
            )
        )

        override suspend fun execute(arguments: String): String {
            val client = trackerClient
            if (client == null) {
                return "⚠️ Яндекс.Трекер не настроен. Установите YANDEX_TRACKER_TOKEN и YANDEX_TRACKER_ORG_ID"
            }

            return try {
                val args = Json.parseToJsonElement(arguments).jsonObject
                val queue = args["queue"]?.jsonPrimitive?.content
                    ?: return "Ошибка: не указана очередь"

                val count = client.getOpenIssuesCount(queue)
                "📊 В очереди '$queue' найдено открытых задач: $count"
            } catch (e: Exception) {
                "❌ Ошибка: ${e.message}"
            }
        }
    }

    // Инструмент: поиск задач
    inner class YandexTrackerSearchIssues : AgentTool {
        override val name = "yandex_tracker_search_issues"
        override val description = "Ищет задачи в Яндекс.Трекере по фильтрам"

        override fun getDefinition() = Tool(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = FunctionParameters(
                    type = "object",
                    properties = mapOf(
                        "queue" to PropertyDefinition("string", "Ключ очереди (опционально)"),
                        "status" to PropertyDefinition("string", "Статус задачи: open, closed (опционально)"),
                        "assignee" to PropertyDefinition("string", "ID исполнителя (опционально)")
                    ),
                    required = emptyList()
                )
            )
        )

        override suspend fun execute(arguments: String): String {
            val client = trackerClient
            if (client == null) {
                return "⚠️ Яндекс.Трекер не настроен"
            }

            return try {
                val args = Json.parseToJsonElement(arguments).jsonObject
                val queue = args["queue"]?.jsonPrimitive?.content
                val status = args["status"]?.jsonPrimitive?.content
                val assignee = args["assignee"]?.jsonPrimitive?.content

                val result = client.searchIssues(queue, status, assignee)

                val issuesText = result.issues.take(10).joinToString("\n") { issue ->
                    "• ${issue.key}: ${issue.summary} [${issue.status?.display ?: "нет статуса"}]"
                }

                "🔍 Найдено задач: ${result.totalCount}\n\n$issuesText"
            } catch (e: Exception) {
                "❌ Ошибка: ${e.message}"
            }
        }
    }

    // Инструмент: получить детали задачи
    inner class YandexTrackerGetIssue : AgentTool {
        override val name = "yandex_tracker_get_issue"
        override val description = "Получает детальную информацию о задаче по её ключу"

        override fun getDefinition() = Tool(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = FunctionParameters(
                    type = "object",
                    properties = mapOf(
                        "issue_key" to PropertyDefinition("string", "Ключ задачи (например, PROJECT-123)")
                    ),
                    required = listOf("issue_key")
                )
            )
        )

        override suspend fun execute(arguments: String): String {
            val client = trackerClient
            if (client == null) {
                return "⚠️ Яндекс.Трекер не настроен"
            }

            return try {
                val args = Json.parseToJsonElement(arguments).jsonObject
                val issueKey = args["issue_key"]?.jsonPrimitive?.content
                    ?: return "Ошибка: не указан ключ задачи"

                val issue = client.getIssue(issueKey)

                buildString {
                    appendLine("📋 Задача: ${issue.key}")
                    appendLine("Название: ${issue.summary}")
                    issue.description?.let { appendLine("Описание: $it") }
                    issue.status?.let { appendLine("Статус: ${it.display}") }
                    issue.assignee?.let { appendLine("Исполнитель: ${it.display}") }
                    issue.queue?.let { appendLine("Очередь: ${it.display}") }
                }.trim()
            } catch (e: Exception) {
                "❌ Ошибка: ${e.message}"
            }
        }
    }

    // ==================== Инструменты погоды (Open-Meteo MCP) ====================

    // Инструмент: текущая погода
    inner class WeatherGetCurrent : AgentTool {
        override val name = "weather_get_current"
        override val description = "Получает текущую погоду для указанного города или местоположения"

        override fun getDefinition() = Tool(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = FunctionParameters(
                    type = "object",
                    properties = mapOf(
                        "location" to PropertyDefinition(
                            type = "string",
                            description = "Название города или местоположения (например, 'Moscow', 'London', 'New York')"
                        )
                    ),
                    required = listOf("location")
                )
            )
        )

        override suspend fun execute(arguments: String): String {
            if (weatherMcpClient == null) {
                return "⚠️ MCP сервер погоды недоступен. Убедитесь, что установлен пакет mcp_weather_server"
            }

            return try {
                val args = Json.parseToJsonElement(arguments).jsonObject
                val location = args["location"]?.jsonPrimitive?.content
                    ?: return "Ошибка: не указано местоположение"

                weatherMcpClient.getCurrentWeather(location)
            } catch (e: Exception) {
                "❌ Ошибка при получении погоды: ${e.message}"
            }
        }
    }

    // Инструмент: детальная погода
    inner class WeatherGetDetails : AgentTool {
        override val name = "weather_get_details"
        override val description = "Получает детальную информацию о погоде в JSON формате"

        override fun getDefinition() = Tool(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = FunctionParameters(
                    type = "object",
                    properties = mapOf(
                        "location" to PropertyDefinition(
                            type = "string",
                            description = "Название города или местоположения"
                        )
                    ),
                    required = listOf("location")
                )
            )
        )

        override suspend fun execute(arguments: String): String {
            if (weatherMcpClient == null) {
                return "⚠️ MCP сервер погоды недоступен"
            }

            return try {
                val args = Json.parseToJsonElement(arguments).jsonObject
                val location = args["location"]?.jsonPrimitive?.content
                    ?: return "Ошибка: не указано местоположение"

                weatherMcpClient.getWeatherDetails(location)
            } catch (e: Exception) {
                "❌ Ошибка при получении деталей погоды: ${e.message}"
            }
        }
    }

    // Инструмент: качество воздуха
    inner class WeatherGetAirQuality : AgentTool {
        override val name = "weather_get_air_quality"
        override val description = "Получает информацию о качестве воздуха для указанного местоположения"

        override fun getDefinition() = Tool(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = FunctionParameters(
                    type = "object",
                    properties = mapOf(
                        "location" to PropertyDefinition(
                            type = "string",
                            description = "Название города или местоположения"
                        )
                    ),
                    required = listOf("location")
                )
            )
        )

        override suspend fun execute(arguments: String): String {
            if (weatherMcpClient == null) {
                return "⚠️ MCP сервер погоды недоступен"
            }

            return try {
                val args = Json.parseToJsonElement(arguments).jsonObject
                val location = args["location"]?.jsonPrimitive?.content
                    ?: return "Ошибка: не указано местоположение"

                weatherMcpClient.getAirQuality(location)
            } catch (e: Exception) {
                "❌ Ошибка при получении качества воздуха: ${e.message}"
            }
        }
    }
}
