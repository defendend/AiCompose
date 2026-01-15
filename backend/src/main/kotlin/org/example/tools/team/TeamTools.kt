package org.example.tools.team

import kotlinx.serialization.json.*
import org.example.team.*
import org.example.tools.annotations.Param
import org.example.tools.annotations.Tool
import org.example.tools.core.AnnotatedAgentTool

// Глобальный репозиторий команды
val teamRepository = TeamRepository()

/**
 * Получить информацию о задаче.
 */
@Tool(
    name = "team_get_task",
    description = "Получить детальную информацию о задаче по ID. Возвращает: название, описание, статус, приоритет, исполнитель, оценка времени."
)
@Param(name = "task_id", description = "ID задачи (например TASK-001)", type = "string", required = true)
object TeamGetTaskTool : AnnotatedAgentTool() {
    override suspend fun execute(arguments: String): String {
        val json = Json.parseToJsonElement(arguments).jsonObject
        val taskId = json["task_id"]?.jsonPrimitive?.content
            ?: return "Ошибка: не указан task_id"

        val task = teamRepository.getTask(taskId)
            ?: return "Задача $taskId не найдена"

        val assignee = task.assigneeId?.let { teamRepository.getTeamMember(it) }

        return buildString {
            appendLine("📋 Задача: ${task.id}")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("📌 ${task.title}")
            appendLine()
            appendLine("📝 Описание: ${task.description}")
            appendLine()
            appendLine("🔹 Тип: ${task.type}")
            appendLine("🔹 Приоритет: ${task.priority}")
            appendLine("🔹 Статус: ${task.status}")
            appendLine("🔹 Исполнитель: ${assignee?.name ?: "Не назначен"}")
            appendLine("🔹 Оценка: ${task.estimateHours ?: "?"} часов")
            appendLine("🔹 Спринт: ${task.sprintId ?: "Backlog"}")
            if (task.labels.isNotEmpty()) {
                appendLine("🏷️ Метки: ${task.labels.joinToString(", ")}")
            }
            if (task.blockedBy.isNotEmpty()) {
                appendLine("⛔ Заблокирована: ${task.blockedBy.joinToString(", ")}")
            }
            task.dueDate?.let { appendLine("📅 Дедлайн: $it") }
            appendLine()
            appendLine("📆 Создана: ${task.createdAt}")
            appendLine("📆 Обновлена: ${task.updatedAt}")
        }
    }
}

/**
 * Поиск задач по фильтрам.
 */
@Tool(
    name = "team_search_tasks",
    description = "Поиск задач по фильтрам: статус, приоритет, исполнитель, спринт, метка. Можно комбинировать несколько фильтров."
)
@Param(name = "status", description = "Статус задачи", type = "string", required = false, enumValues = ["BACKLOG", "TODO", "IN_PROGRESS", "REVIEW", "TESTING", "DONE", "BLOCKED"])
@Param(name = "priority", description = "Приоритет задачи", type = "string", required = false, enumValues = ["CRITICAL", "HIGH", "MEDIUM", "LOW"])
@Param(name = "assignee_id", description = "ID исполнителя", type = "string", required = false)
@Param(name = "sprint_id", description = "ID спринта", type = "string", required = false)
@Param(name = "label", description = "Метка для фильтрации", type = "string", required = false)
object TeamSearchTasksTool : AnnotatedAgentTool() {
    override suspend fun execute(arguments: String): String {
        val json = Json.parseToJsonElement(arguments).jsonObject

        val status = json["status"]?.jsonPrimitive?.content?.let {
            try { TaskStatus.valueOf(it) } catch (e: Exception) { null }
        }
        val priority = json["priority"]?.jsonPrimitive?.content?.let {
            try { TaskPriority.valueOf(it) } catch (e: Exception) { null }
        }
        val assigneeId = json["assignee_id"]?.jsonPrimitive?.content
        val sprintId = json["sprint_id"]?.jsonPrimitive?.content
        val label = json["label"]?.jsonPrimitive?.content

        val tasks = teamRepository.searchTasks(
            status = status,
            priority = priority,
            assigneeId = assigneeId,
            sprintId = sprintId,
            label = label
        )

        if (tasks.isEmpty()) {
            return "Задачи не найдены по указанным фильтрам"
        }

        return buildString {
            appendLine("🔍 Найдено задач: ${tasks.size}")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            tasks.forEach { task ->
                val priorityIcon = when (task.priority) {
                    TaskPriority.CRITICAL -> "🔴"
                    TaskPriority.HIGH -> "🟠"
                    TaskPriority.MEDIUM -> "🟡"
                    TaskPriority.LOW -> "🟢"
                }
                val statusIcon = when (task.status) {
                    TaskStatus.DONE -> "✅"
                    TaskStatus.IN_PROGRESS -> "🔄"
                    TaskStatus.BLOCKED -> "⛔"
                    TaskStatus.REVIEW -> "👀"
                    TaskStatus.TESTING -> "🧪"
                    else -> "📋"
                }
                appendLine("$statusIcon $priorityIcon ${task.id}: ${task.title}")
                appendLine("   └─ ${task.status} | ${task.assigneeId ?: "Не назначен"}")
            }
        }
    }
}

/**
 * Создать новую задачу.
 */
@Tool(
    name = "team_create_task",
    description = "Создать новую задачу в системе. Возвращает ID созданной задачи."
)
@Param(name = "title", description = "Название задачи", type = "string", required = true)
@Param(name = "description", description = "Описание задачи", type = "string", required = true)
@Param(name = "type", description = "Тип задачи", type = "string", required = true, enumValues = ["FEATURE", "BUG", "TASK", "EPIC", "STORY"])
@Param(name = "priority", description = "Приоритет", type = "string", required = true, enumValues = ["CRITICAL", "HIGH", "MEDIUM", "LOW"])
@Param(name = "assignee_id", description = "ID исполнителя", type = "string", required = false)
@Param(name = "estimate_hours", description = "Оценка в часах", type = "integer", required = false)
@Param(name = "labels", description = "Метки через запятую", type = "string", required = false)
object TeamCreateTaskTool : AnnotatedAgentTool() {
    override suspend fun execute(arguments: String): String {
        val json = Json.parseToJsonElement(arguments).jsonObject

        val title = json["title"]?.jsonPrimitive?.content
            ?: return "Ошибка: не указан title"
        val description = json["description"]?.jsonPrimitive?.content
            ?: return "Ошибка: не указан description"
        val type = json["type"]?.jsonPrimitive?.content?.let {
            try { TaskType.valueOf(it) } catch (e: Exception) { null }
        } ?: return "Ошибка: неверный type"
        val priority = json["priority"]?.jsonPrimitive?.content?.let {
            try { TaskPriority.valueOf(it) } catch (e: Exception) { null }
        } ?: return "Ошибка: неверный priority"

        val assigneeId = json["assignee_id"]?.jsonPrimitive?.content
        val estimateHours = json["estimate_hours"]?.jsonPrimitive?.intOrNull
        val labels = json["labels"]?.jsonPrimitive?.content?.split(",")?.map { it.trim() } ?: emptyList()

        val currentSprint = teamRepository.getCurrentSprint()

        val task = teamRepository.createTask(
            title = title,
            description = description,
            type = type,
            priority = priority,
            assigneeId = assigneeId,
            reporterId = "PM-001", // По умолчанию PM
            sprintId = currentSprint?.id,
            estimateHours = estimateHours,
            labels = labels
        )

        return buildString {
            appendLine("✅ Задача создана!")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("🆔 ID: ${task.id}")
            appendLine("📌 ${task.title}")
            appendLine("🔹 Тип: ${task.type}")
            appendLine("🔹 Приоритет: ${task.priority}")
            appendLine("🔹 Статус: ${task.status}")
            if (assigneeId != null) {
                val assignee = teamRepository.getTeamMember(assigneeId)
                appendLine("👤 Исполнитель: ${assignee?.name ?: assigneeId}")
            }
            currentSprint?.let { appendLine("📅 Спринт: ${it.name}") }
        }
    }
}

/**
 * Обновить статус задачи.
 */
@Tool(
    name = "team_update_task_status",
    description = "Изменить статус задачи (TODO, IN_PROGRESS, REVIEW, TESTING, DONE, BLOCKED)"
)
@Param(name = "task_id", description = "ID задачи", type = "string", required = true)
@Param(name = "status", description = "Новый статус", type = "string", required = true, enumValues = ["BACKLOG", "TODO", "IN_PROGRESS", "REVIEW", "TESTING", "DONE", "BLOCKED"])
object TeamUpdateTaskStatusTool : AnnotatedAgentTool() {
    override suspend fun execute(arguments: String): String {
        val json = Json.parseToJsonElement(arguments).jsonObject

        val taskId = json["task_id"]?.jsonPrimitive?.content
            ?: return "Ошибка: не указан task_id"
        val status = json["status"]?.jsonPrimitive?.content?.let {
            try { TaskStatus.valueOf(it) } catch (e: Exception) { null }
        } ?: return "Ошибка: неверный status"

        val task = teamRepository.updateTaskStatus(taskId, status)
            ?: return "Задача $taskId не найдена"

        return "✅ Статус задачи ${task.id} обновлён на ${task.status}"
    }
}

/**
 * Получить информацию о члене команды.
 */
@Tool(
    name = "team_get_member",
    description = "Получить информацию о члене команды: роль, навыки, загрузка, текущие задачи"
)
@Param(name = "member_id", description = "ID члена команды", type = "string", required = true)
object TeamGetMemberTool : AnnotatedAgentTool() {
    override suspend fun execute(arguments: String): String {
        val json = Json.parseToJsonElement(arguments).jsonObject
        val memberId = json["member_id"]?.jsonPrimitive?.content
            ?: return "Ошибка: не указан member_id"

        val member = teamRepository.getTeamMember(memberId)
            ?: return "Член команды $memberId не найден"

        val tasks = teamRepository.searchTasks(assigneeId = memberId)
        val inProgressTasks = tasks.filter { it.status == TaskStatus.IN_PROGRESS }
        val todoTasks = tasks.filter { it.status == TaskStatus.TODO }

        return buildString {
            appendLine("👤 ${member.name}")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("🔹 ID: ${member.id}")
            appendLine("🔹 Роль: ${member.role}")
            appendLine("🔹 Email: ${member.email}")
            appendLine("🔹 Навыки: ${member.skills.joinToString(", ")}")
            appendLine()
            appendLine("📊 Загрузка: ${member.currentLoad}/${member.capacity} часов")
            val loadPercent = (member.currentLoad * 100 / member.capacity)
            val loadBar = "█".repeat(loadPercent / 10) + "░".repeat(10 - loadPercent / 10)
            appendLine("   [$loadBar] $loadPercent%")
            appendLine()
            appendLine("📋 Задачи в работе: ${inProgressTasks.size}")
            inProgressTasks.forEach { appendLine("   • ${it.id}: ${it.title}") }
            appendLine("📋 К выполнению: ${todoTasks.size}")
            todoTasks.take(3).forEach { appendLine("   • ${it.id}: ${it.title}") }
            if (todoTasks.size > 3) appendLine("   ... и ещё ${todoTasks.size - 3}")
        }
    }
}

/**
 * Получить статистику команды.
 */
@Tool(
    name = "team_get_stats",
    description = "Получить статистику команды: количество задач по статусам, прогресс спринта, заблокированные задачи"
)
object TeamGetStatsTool : AnnotatedAgentTool() {
    override suspend fun execute(arguments: String): String {
        val stats = teamRepository.getTeamStats()
        val members = teamRepository.getAllTeamMembers()

        return buildString {
            appendLine("📊 Статистика команды")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine()
            appendLine("📋 Всего задач: ${stats.totalTasks}")
            appendLine()
            appendLine("📈 По статусам:")
            stats.byStatus.forEach { (status, count) ->
                val icon = when (status) {
                    "DONE" -> "✅"
                    "IN_PROGRESS" -> "🔄"
                    "BLOCKED" -> "⛔"
                    "REVIEW" -> "👀"
                    "TESTING" -> "🧪"
                    "TODO" -> "📝"
                    else -> "📋"
                }
                appendLine("   $icon $status: $count")
            }
            appendLine()
            appendLine("🎯 По приоритетам:")
            stats.byPriority.forEach { (priority, count) ->
                val icon = when (priority) {
                    "CRITICAL" -> "🔴"
                    "HIGH" -> "🟠"
                    "MEDIUM" -> "🟡"
                    "LOW" -> "🟢"
                    else -> "⚪"
                }
                appendLine("   $icon $priority: $count")
            }
            appendLine()
            if (stats.blockedTasks > 0) {
                appendLine("⚠️ Заблокировано задач: ${stats.blockedTasks}")
                appendLine()
            }

            stats.sprintProgress?.let { sprint ->
                appendLine("🏃 Спринт: ${sprint.sprintName}")
                appendLine("   Выполнено: ${sprint.doneTasks}/${sprint.totalTasks}")
                val progressBar = "█".repeat(sprint.velocityPercent / 10) + "░".repeat(10 - sprint.velocityPercent / 10)
                appendLine("   [$progressBar] ${sprint.velocityPercent}%")
                appendLine("   Осталось: ${sprint.remainingHours} часов")
                appendLine("   Дней до конца: ${sprint.daysLeft}")
            }

            appendLine()
            appendLine("👥 Команда (${members.size} человек):")
            members.forEach { member ->
                val loadPercent = (member.currentLoad * 100 / member.capacity)
                val loadIcon = when {
                    loadPercent >= 100 -> "🔴"
                    loadPercent >= 80 -> "🟠"
                    loadPercent >= 50 -> "🟡"
                    else -> "🟢"
                }
                appendLine("   $loadIcon ${member.name}: $loadPercent% загрузки")
            }
        }
    }
}

/**
 * Получить рекомендации по приоритетам.
 */
@Tool(
    name = "team_get_priorities",
    description = "Получить рекомендации: какие задачи делать в первую очередь. Учитывает приоритет, дедлайны, блокировки."
)
@Param(name = "limit", description = "Количество рекомендаций (по умолчанию 5)", type = "integer", required = false)
object TeamGetPrioritiesTool : AnnotatedAgentTool() {
    override suspend fun execute(arguments: String): String {
        val json = Json.parseToJsonElement(arguments).jsonObject
        val limit = json["limit"]?.jsonPrimitive?.intOrNull ?: 5

        val recommendations = teamRepository.getPriorityRecommendations(limit)

        if (recommendations.isEmpty()) {
            return "Нет активных задач для рекомендаций"
        }

        return buildString {
            appendLine("🎯 Рекомендации по приоритетам")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("Что делать в первую очередь:")
            appendLine()

            recommendations.forEachIndexed { index, rec ->
                val medal = when (index) {
                    0 -> "🥇"
                    1 -> "🥈"
                    2 -> "🥉"
                    else -> "${index + 1}."
                }
                appendLine("$medal ${rec.taskId}: ${rec.taskTitle}")
                appendLine("   📊 Score: ${rec.score}/100")
                appendLine("   💡 ${rec.reason}")
                appendLine()
            }

            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("💡 Совет: Начните с задачи #1 (${recommendations.first().taskId})")
        }
    }
}

/**
 * Получить информацию о проекте.
 */
@Tool(
    name = "team_get_project",
    description = "Получить информацию о проекте: название, описание, технологии, команда, текущий спринт"
)
object TeamGetProjectTool : AnnotatedAgentTool() {
    override suspend fun execute(arguments: String): String {
        val project = teamRepository.getProjectInfo()
        val stats = teamRepository.getTeamStats()

        return buildString {
            appendLine("🚀 Проект: ${project.name}")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine()
            appendLine("📝 ${project.description}")
            appendLine()
            appendLine("🔧 Технологии: ${project.techStack.joinToString(", ")}")
            appendLine("📦 Репозиторий: ${project.repository}")
            appendLine()
            appendLine("👥 Команда: ${project.team.size} человек")
            project.team.forEach { member ->
                appendLine("   • ${member.name} — ${member.role}")
            }
            appendLine()
            project.currentSprint?.let { sprint ->
                appendLine("🏃 Текущий спринт: ${sprint.name}")
                appendLine("   🎯 Цель: ${sprint.goal}")
                appendLine("   📅 ${sprint.startDate} — ${sprint.endDate}")
                stats.sprintProgress?.let {
                    appendLine("   📊 Прогресс: ${it.velocityPercent}%")
                }
            }
        }
    }
}
