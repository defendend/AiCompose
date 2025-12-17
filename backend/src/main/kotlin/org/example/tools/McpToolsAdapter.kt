package org.example.tools

import io.ktor.client.*
import kotlinx.serialization.json.*
import org.example.data.ReminderRepository
import org.example.integrations.WeatherMcpClient
import org.example.integrations.YandexTrackerClient
import org.example.model.Reminder
import org.example.model.ReminderStatus
import org.example.tools.core.AgentTool
import org.example.model.FunctionDefinition
import org.example.model.FunctionParameters
import org.example.model.PropertyDefinition
import org.example.model.Tool
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Адаптер MCP инструментов для использования в Agent
 *
 * Предоставляет инструменты Яндекс.Трекера, погоды (Open-Meteo) и напоминаний как обычные AgentTool
 */
class McpToolsAdapter(
    private val httpClient: HttpClient,
    private val trackerToken: String?,
    private val trackerOrgId: String?,
    private val weatherMcpClient: WeatherMcpClient?,
    private val reminderRepository: ReminderRepository
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

        // Инструменты напоминаний
        add(ReminderAdd())
        add(ReminderList())
        add(ReminderComplete())
        add(ReminderDelete())
        add(ReminderGetSummary())
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
        override val description = "Получает текущую погоду для указанного города"

        override fun getDefinition() = Tool(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = FunctionParameters(
                    type = "object",
                    properties = mapOf(
                        "city" to PropertyDefinition(
                            type = "string",
                            description = "Название города (например, 'Moscow', 'London', 'New York')"
                        )
                    ),
                    required = listOf("city")
                )
            )
        )

        override suspend fun execute(arguments: String): String {
            if (weatherMcpClient == null) {
                return "⚠️ MCP сервер погоды недоступен. Убедитесь, что установлен пакет mcp_weather_server"
            }

            return try {
                val args = Json.parseToJsonElement(arguments).jsonObject
                val city = args["city"]?.jsonPrimitive?.content
                    ?: return "Ошибка: не указан город"

                weatherMcpClient.getCurrentWeather(city)
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
                        "city" to PropertyDefinition(
                            type = "string",
                            description = "Название города"
                        )
                    ),
                    required = listOf("city")
                )
            )
        )

        override suspend fun execute(arguments: String): String {
            if (weatherMcpClient == null) {
                return "⚠️ MCP сервер погоды недоступен"
            }

            return try {
                val args = Json.parseToJsonElement(arguments).jsonObject
                val city = args["city"]?.jsonPrimitive?.content
                    ?: return "Ошибка: не указан город"

                weatherMcpClient.getWeatherDetails(city)
            } catch (e: Exception) {
                "❌ Ошибка при получении деталей погоды: ${e.message}"
            }
        }
    }

    // Инструмент: качество воздуха
    inner class WeatherGetAirQuality : AgentTool {
        override val name = "weather_get_air_quality"
        override val description = "Получает информацию о качестве воздуха для указанного города"

        override fun getDefinition() = Tool(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = FunctionParameters(
                    type = "object",
                    properties = mapOf(
                        "city" to PropertyDefinition(
                            type = "string",
                            description = "Название города"
                        )
                    ),
                    required = listOf("city")
                )
            )
        )

        override suspend fun execute(arguments: String): String {
            if (weatherMcpClient == null) {
                return "⚠️ MCP сервер погоды недоступен"
            }

            return try {
                val args = Json.parseToJsonElement(arguments).jsonObject
                val city = args["city"]?.jsonPrimitive?.content
                    ?: return "Ошибка: не указан город"

                weatherMcpClient.getAirQuality(city)
            } catch (e: Exception) {
                "❌ Ошибка при получении качества воздуха: ${e.message}"
            }
        }
    }

    // ==================== Инструменты напоминаний ====================

    // Инструмент: добавить напоминание
    inner class ReminderAdd : AgentTool {
        override val name = "reminder_add"
        override val description = "Добавляет новое напоминание с указанным временем"

        override fun getDefinition() = Tool(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = FunctionParameters(
                    type = "object",
                    properties = mapOf(
                        "title" to PropertyDefinition(
                            type = "string",
                            description = "Название напоминания"
                        ),
                        "description" to PropertyDefinition(
                            type = "string",
                            description = "Описание напоминания (опционально)"
                        ),
                        "reminder_time" to PropertyDefinition(
                            type = "string",
                            description = "Время напоминания в формате ISO 8601 (например, '2024-01-20T15:30:00Z')"
                        )
                    ),
                    required = listOf("title", "reminder_time")
                )
            )
        )

        override suspend fun execute(arguments: String): String {
            return try {
                val args = Json.parseToJsonElement(arguments).jsonObject
                val title = args["title"]?.jsonPrimitive?.content
                    ?: return "Ошибка: не указан title"
                val description = args["description"]?.jsonPrimitive?.contentOrNull
                val reminderTimeStr = args["reminder_time"]?.jsonPrimitive?.content
                    ?: return "Ошибка: не указано reminder_time"

                val reminderTime = try {
                    Instant.parse(reminderTimeStr)
                } catch (e: Exception) {
                    return "Ошибка: неверный формат времени. Используйте ISO 8601 (например, '2024-01-20T15:30:00Z')"
                }

                val reminder = Reminder(
                    title = title,
                    description = description,
                    reminderTime = reminderTime
                )

                val created = reminderRepository.add(reminder)

                val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
                    .withZone(ZoneId.systemDefault())
                val formattedTime = formatter.format(created.reminderTime)

                "✅ Напоминание создано!\n" +
                "📋 ${created.title}\n" +
                "⏰ $formattedTime\n" +
                "🆔 ${created.id}"
            } catch (e: Exception) {
                "❌ Ошибка при создании напоминания: ${e.message}"
            }
        }
    }

    // Инструмент: список напоминаний
    inner class ReminderList : AgentTool {
        override val name = "reminder_list"
        override val description = "Получает список напоминаний с возможностью фильтрации по статусу"

        override fun getDefinition() = Tool(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = FunctionParameters(
                    type = "object",
                    properties = mapOf(
                        "status" to PropertyDefinition(
                            type = "string",
                            description = "Статус для фильтрации: PENDING, COMPLETED, CANCELLED (опционально, без фильтра показать все)"
                        )
                    ),
                    required = emptyList()
                )
            )
        )

        override suspend fun execute(arguments: String): String {
            return try {
                val args = Json.parseToJsonElement(arguments).jsonObject
                val statusStr = args["status"]?.jsonPrimitive?.contentOrNull

                val reminders = if (statusStr != null) {
                    val status = try {
                        ReminderStatus.valueOf(statusStr.uppercase())
                    } catch (e: Exception) {
                        return "Ошибка: неверный статус. Используйте: PENDING, COMPLETED или CANCELLED"
                    }
                    reminderRepository.getByStatus(status)
                } else {
                    reminderRepository.getAll()
                }

                if (reminders.isEmpty()) {
                    return "📭 Напоминаний не найдено"
                }

                val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
                    .withZone(ZoneId.systemDefault())

                buildString {
                    appendLine("📋 Список напоминаний (${reminders.size}):")
                    appendLine()
                    reminders.forEachIndexed { index, reminder ->
                        appendLine("${index + 1}. ${reminder.title}")
                        appendLine("   ⏰ ${formatter.format(reminder.reminderTime)}")
                        appendLine("   📌 Статус: ${reminder.status}")
                        if (reminder.description != null) {
                            appendLine("   💬 ${reminder.description}")
                        }
                        appendLine("   🆔 ${reminder.id}")
                        if (index < reminders.size - 1) appendLine()
                    }
                }.trim()
            } catch (e: Exception) {
                "❌ Ошибка при получении списка напоминаний: ${e.message}"
            }
        }
    }

    // Инструмент: пометить как выполненное
    inner class ReminderComplete : AgentTool {
        override val name = "reminder_complete"
        override val description = "Помечает напоминание как выполненное"

        override fun getDefinition() = Tool(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = FunctionParameters(
                    type = "object",
                    properties = mapOf(
                        "id" to PropertyDefinition(
                            type = "string",
                            description = "ID напоминания"
                        )
                    ),
                    required = listOf("id")
                )
            )
        )

        override suspend fun execute(arguments: String): String {
            return try {
                val args = Json.parseToJsonElement(arguments).jsonObject
                val id = args["id"]?.jsonPrimitive?.content
                    ?: return "Ошибка: не указан id"

                val reminder = reminderRepository.complete(id)
                    ?: return "❌ Напоминание с ID '$id' не найдено"

                "✅ Напоминание \"${reminder.title}\" отмечено как выполненное!"
            } catch (e: Exception) {
                "❌ Ошибка при завершении напоминания: ${e.message}"
            }
        }
    }

    // Инструмент: удалить напоминание
    inner class ReminderDelete : AgentTool {
        override val name = "reminder_delete"
        override val description = "Удаляет напоминание по ID"

        override fun getDefinition() = Tool(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = FunctionParameters(
                    type = "object",
                    properties = mapOf(
                        "id" to PropertyDefinition(
                            type = "string",
                            description = "ID напоминания"
                        )
                    ),
                    required = listOf("id")
                )
            )
        )

        override suspend fun execute(arguments: String): String {
            return try {
                val args = Json.parseToJsonElement(arguments).jsonObject
                val id = args["id"]?.jsonPrimitive?.content
                    ?: return "Ошибка: не указан id"

                val deleted = reminderRepository.delete(id)
                if (deleted) {
                    "✅ Напоминание удалено успешно"
                } else {
                    "❌ Напоминание с ID '$id' не найдено"
                }
            } catch (e: Exception) {
                "❌ Ошибка при удалении напоминания: ${e.message}"
            }
        }
    }

    // Инструмент: получить сводку
    inner class ReminderGetSummary : AgentTool {
        override val name = "reminder_get_summary"
        override val description = "Получает сводку по всем напоминаниям (количество, просроченные, на сегодня, ближайшие)"

        override fun getDefinition() = Tool(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = FunctionParameters(
                    type = "object",
                    properties = emptyMap(),
                    required = emptyList()
                )
            )
        )

        override suspend fun execute(arguments: String): String {
            return try {
                val all = reminderRepository.getAll()
                val pending = reminderRepository.getByStatus(ReminderStatus.PENDING)
                val overdue = reminderRepository.getOverdue()
                val today = reminderRepository.getToday()
                val upcoming = reminderRepository.getUpcoming(5)

                val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
                    .withZone(ZoneId.systemDefault())

                buildString {
                    appendLine("📊 Сводка по напоминаниям")
                    appendLine()
                    appendLine("📋 Всего напоминаний: ${all.size}")
                    appendLine("⏳ Ожидают выполнения: ${pending.size}")
                    appendLine("⚠️  Просроченных: ${overdue.size}")
                    appendLine("📅 На сегодня: ${today.size}")
                    appendLine()

                    if (overdue.isNotEmpty()) {
                        appendLine("❗ Просроченные напоминания:")
                        overdue.take(3).forEach { reminder ->
                            appendLine("   • ${reminder.title} (${formatter.format(reminder.reminderTime)})")
                        }
                        if (overdue.size > 3) {
                            appendLine("   ... и ещё ${overdue.size - 3}")
                        }
                        appendLine()
                    }

                    if (upcoming.isNotEmpty()) {
                        appendLine("🔜 Ближайшие напоминания:")
                        upcoming.forEach { reminder ->
                            appendLine("   • ${reminder.title} (${formatter.format(reminder.reminderTime)})")
                        }
                    } else if (overdue.isEmpty()) {
                        appendLine("✨ Все задачи под контролем!")
                    }
                }.trim()
            } catch (e: Exception) {
                "❌ Ошибка при получении сводки: ${e.message}"
            }
        }
    }
}
