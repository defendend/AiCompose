package org.example.team

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Репозиторий данных команды с демо-данными.
 */
class TeamRepository {

    private val dateFormat = DateTimeFormatter.ISO_LOCAL_DATE
    private val today = LocalDate.now()

    // === Члены команды ===
    private val teamMembers = mutableListOf(
        TeamMember(
            id = "DEV-001",
            name = "Алексей Иванов",
            role = "Senior Backend Developer",
            email = "alexey@team.dev",
            skills = listOf("kotlin", "ktor", "postgresql", "redis"),
            capacity = 40,
            currentLoad = 32
        ),
        TeamMember(
            id = "DEV-002",
            name = "Мария Петрова",
            role = "Frontend Developer",
            email = "maria@team.dev",
            skills = listOf("kotlin", "compose", "ui/ux", "android"),
            capacity = 40,
            currentLoad = 24
        ),
        TeamMember(
            id = "DEV-003",
            name = "Дмитрий Сидоров",
            role = "Full Stack Developer",
            email = "dmitry@team.dev",
            skills = listOf("kotlin", "typescript", "react", "docker"),
            capacity = 40,
            currentLoad = 40
        ),
        TeamMember(
            id = "QA-001",
            name = "Елена Козлова",
            role = "QA Engineer",
            email = "elena@team.dev",
            skills = listOf("testing", "automation", "selenium", "api-testing"),
            capacity = 40,
            currentLoad = 16
        ),
        TeamMember(
            id = "PM-001",
            name = "Игорь Новиков",
            role = "Project Manager",
            email = "igor@team.dev",
            skills = listOf("agile", "scrum", "jira", "planning"),
            capacity = 40,
            currentLoad = 20
        )
    )

    // === Спринты ===
    private val sprints = mutableListOf(
        Sprint(
            id = "SPR-001",
            name = "Sprint 23 - AI Integration",
            goal = "Интеграция AI ассистента в основной продукт",
            startDate = today.minusDays(7).format(dateFormat),
            endDate = today.plusDays(7).format(dateFormat),
            status = SprintStatus.ACTIVE
        ),
        Sprint(
            id = "SPR-002",
            name = "Sprint 24 - Performance",
            goal = "Оптимизация производительности и масштабирование",
            startDate = today.plusDays(8).format(dateFormat),
            endDate = today.plusDays(22).format(dateFormat),
            status = SprintStatus.PLANNED
        )
    )

    // === Задачи ===
    private val tasks = mutableListOf(
        // CRITICAL задачи
        TeamTask(
            id = "TASK-001",
            title = "Критический баг: утечка памяти при streaming",
            description = "При длительном streaming ответов происходит утечка памяти. Сервер падает через 2-3 часа работы.",
            type = TaskType.BUG,
            priority = TaskPriority.CRITICAL,
            status = TaskStatus.IN_PROGRESS,
            assigneeId = "DEV-001",
            reporterId = "QA-001",
            sprintId = "SPR-001",
            estimateHours = 8,
            labels = listOf("backend", "memory", "urgent"),
            createdAt = today.minusDays(2).format(dateFormat),
            updatedAt = today.format(dateFormat),
            dueDate = today.plusDays(1).format(dateFormat)
        ),

        // HIGH задачи
        TeamTask(
            id = "TASK-002",
            title = "Реализовать RAG для документации проекта",
            description = "Добавить поиск по внутренней документации с использованием RAG. Индексировать README, CLAUDE.md, docs/",
            type = TaskType.FEATURE,
            priority = TaskPriority.HIGH,
            status = TaskStatus.REVIEW,
            assigneeId = "DEV-001",
            reporterId = "PM-001",
            sprintId = "SPR-001",
            estimateHours = 16,
            labels = listOf("backend", "ai", "rag"),
            createdAt = today.minusDays(5).format(dateFormat),
            updatedAt = today.minusDays(1).format(dateFormat)
        ),
        TeamTask(
            id = "TASK-003",
            title = "Добавить экран Team Assistant в Desktop",
            description = "UI для командного ассистента: чат, быстрые действия, статистика спринта",
            type = TaskType.FEATURE,
            priority = TaskPriority.HIGH,
            status = TaskStatus.TODO,
            assigneeId = "DEV-002",
            reporterId = "PM-001",
            sprintId = "SPR-001",
            estimateHours = 12,
            labels = listOf("frontend", "ui", "desktop"),
            createdAt = today.minusDays(3).format(dateFormat),
            updatedAt = today.minusDays(3).format(dateFormat)
        ),
        TeamTask(
            id = "TASK-004",
            title = "Интеграция с Slack для уведомлений",
            description = "Отправка уведомлений о важных событиях в Slack канал команды",
            type = TaskType.FEATURE,
            priority = TaskPriority.HIGH,
            status = TaskStatus.BLOCKED,
            assigneeId = "DEV-003",
            reporterId = "PM-001",
            sprintId = "SPR-001",
            estimateHours = 8,
            labels = listOf("integration", "slack", "notifications"),
            blockedBy = listOf("TASK-001"),
            createdAt = today.minusDays(4).format(dateFormat),
            updatedAt = today.minusDays(1).format(dateFormat)
        ),

        // MEDIUM задачи
        TeamTask(
            id = "TASK-005",
            title = "Написать unit тесты для TeamRepository",
            description = "Покрыть тестами основные методы: CRUD операции, фильтрация, статистика",
            type = TaskType.TASK,
            priority = TaskPriority.MEDIUM,
            status = TaskStatus.TODO,
            assigneeId = "DEV-001",
            reporterId = "DEV-001",
            sprintId = "SPR-001",
            estimateHours = 4,
            labels = listOf("testing", "backend"),
            createdAt = today.minusDays(2).format(dateFormat),
            updatedAt = today.minusDays(2).format(dateFormat)
        ),
        TeamTask(
            id = "TASK-006",
            title = "Обновить документацию API",
            description = "Добавить описание новых endpoints: /api/team, /api/support. Примеры запросов и ответов.",
            type = TaskType.TASK,
            priority = TaskPriority.MEDIUM,
            status = TaskStatus.TODO,
            assigneeId = null,
            reporterId = "PM-001",
            sprintId = "SPR-001",
            estimateHours = 3,
            labels = listOf("docs", "api"),
            createdAt = today.minusDays(1).format(dateFormat),
            updatedAt = today.minusDays(1).format(dateFormat)
        ),
        TeamTask(
            id = "TASK-007",
            title = "Оптимизировать запросы к DeepSeek API",
            description = "Добавить кэширование embeddings, батчинг запросов для снижения latency",
            type = TaskType.TASK,
            priority = TaskPriority.MEDIUM,
            status = TaskStatus.BACKLOG,
            assigneeId = null,
            reporterId = "DEV-001",
            sprintId = null,
            estimateHours = 12,
            labels = listOf("backend", "optimization", "ai"),
            createdAt = today.minusDays(6).format(dateFormat),
            updatedAt = today.minusDays(6).format(dateFormat)
        ),

        // LOW задачи
        TeamTask(
            id = "TASK-008",
            title = "Рефакторинг ToolRegistry",
            description = "Разбить на модули, улучшить типизацию, добавить lazy loading",
            type = TaskType.TASK,
            priority = TaskPriority.LOW,
            status = TaskStatus.BACKLOG,
            assigneeId = null,
            reporterId = "DEV-003",
            sprintId = null,
            estimateHours = 8,
            labels = listOf("refactoring", "backend"),
            createdAt = today.minusDays(10).format(dateFormat),
            updatedAt = today.minusDays(10).format(dateFormat)
        ),
        TeamTask(
            id = "TASK-009",
            title = "Добавить тёмную тему в Desktop",
            description = "Поддержка системных настроек темы, ручное переключение",
            type = TaskType.FEATURE,
            priority = TaskPriority.LOW,
            status = TaskStatus.BACKLOG,
            assigneeId = null,
            reporterId = "DEV-002",
            sprintId = null,
            estimateHours = 6,
            labels = listOf("frontend", "ui", "theme"),
            createdAt = today.minusDays(14).format(dateFormat),
            updatedAt = today.minusDays(14).format(dateFormat)
        ),

        // Выполненные задачи
        TeamTask(
            id = "TASK-010",
            title = "Реализовать Support Assistant",
            description = "Ассистент поддержки с FAQ, тикетами и пользователями",
            type = TaskType.FEATURE,
            priority = TaskPriority.HIGH,
            status = TaskStatus.DONE,
            assigneeId = "DEV-001",
            reporterId = "PM-001",
            sprintId = "SPR-001",
            estimateHours = 16,
            labels = listOf("backend", "ai", "support"),
            createdAt = today.minusDays(7).format(dateFormat),
            updatedAt = today.minusDays(1).format(dateFormat)
        ),
        TeamTask(
            id = "TASK-011",
            title = "Code Review автоматизация",
            description = "GitHub Action для автоматического ревью PR",
            type = TaskType.FEATURE,
            priority = TaskPriority.HIGH,
            status = TaskStatus.DONE,
            assigneeId = "DEV-003",
            reporterId = "PM-001",
            sprintId = "SPR-001",
            estimateHours = 12,
            labels = listOf("ci/cd", "github", "automation"),
            createdAt = today.minusDays(8).format(dateFormat),
            updatedAt = today.minusDays(2).format(dateFormat)
        )
    )

    // === Проект ===
    private val project = ProjectInfo(
        name = "AiCompose",
        description = "Desktop-приложение с AI-агентом на Compose Multiplatform",
        techStack = listOf("Kotlin", "Compose", "Ktor", "DeepSeek", "PostgreSQL", "Redis"),
        repository = "https://github.com/defendend/AiCompose",
        team = teamMembers,
        currentSprint = sprints.find { it.status == SprintStatus.ACTIVE }
    )

    // === CRUD операции ===

    fun getTask(taskId: String): TeamTask? {
        return tasks.find { it.id == taskId }
    }

    fun searchTasks(
        status: TaskStatus? = null,
        priority: TaskPriority? = null,
        assigneeId: String? = null,
        sprintId: String? = null,
        type: TaskType? = null,
        label: String? = null
    ): List<TeamTask> {
        return tasks.filter { task ->
            (status == null || task.status == status) &&
            (priority == null || task.priority == priority) &&
            (assigneeId == null || task.assigneeId == assigneeId) &&
            (sprintId == null || task.sprintId == sprintId) &&
            (type == null || task.type == type) &&
            (label == null || task.labels.any { it.contains(label, ignoreCase = true) })
        }
    }

    fun createTask(
        title: String,
        description: String,
        type: TaskType,
        priority: TaskPriority,
        assigneeId: String?,
        reporterId: String,
        sprintId: String?,
        estimateHours: Int?,
        labels: List<String>
    ): TeamTask {
        val newId = "TASK-${(tasks.maxOfOrNull { it.id.removePrefix("TASK-").toIntOrNull() ?: 0 } ?: 0) + 1}".padStart(7, '0')
        val task = TeamTask(
            id = newId,
            title = title,
            description = description,
            type = type,
            priority = priority,
            status = TaskStatus.TODO,
            assigneeId = assigneeId,
            reporterId = reporterId,
            sprintId = sprintId,
            estimateHours = estimateHours,
            labels = labels,
            createdAt = today.format(dateFormat),
            updatedAt = today.format(dateFormat)
        )
        tasks.add(task)
        return task
    }

    fun updateTaskStatus(taskId: String, newStatus: TaskStatus): TeamTask? {
        val index = tasks.indexOfFirst { it.id == taskId }
        if (index == -1) return null

        val updated = tasks[index].copy(
            status = newStatus,
            updatedAt = today.format(dateFormat)
        )
        tasks[index] = updated
        return updated
    }

    fun assignTask(taskId: String, assigneeId: String?): TeamTask? {
        val index = tasks.indexOfFirst { it.id == taskId }
        if (index == -1) return null

        val updated = tasks[index].copy(
            assigneeId = assigneeId,
            updatedAt = today.format(dateFormat)
        )
        tasks[index] = updated
        return updated
    }

    // === Члены команды ===

    fun getTeamMember(memberId: String): TeamMember? {
        return teamMembers.find { it.id == memberId }
    }

    fun getAllTeamMembers(): List<TeamMember> = teamMembers.toList()

    fun getAvailableMembers(): List<TeamMember> {
        return teamMembers.filter { it.currentLoad < it.capacity }
            .sortedBy { it.currentLoad.toFloat() / it.capacity }
    }

    // === Спринты ===

    fun getCurrentSprint(): Sprint? {
        return sprints.find { it.status == SprintStatus.ACTIVE }
    }

    fun getSprint(sprintId: String): Sprint? {
        return sprints.find { it.id == sprintId }
    }

    // === Проект ===

    fun getProjectInfo(): ProjectInfo = project

    // === Статистика ===

    fun getTeamStats(): TeamStats {
        val currentSprint = getCurrentSprint()
        val sprintTasks = currentSprint?.let { sprint ->
            tasks.filter { it.sprintId == sprint.id }
        } ?: emptyList()

        val sprintProgress = currentSprint?.let { sprint ->
            val doneTasks = sprintTasks.count { it.status == TaskStatus.DONE }
            val remainingHours = sprintTasks
                .filter { it.status != TaskStatus.DONE }
                .sumOf { it.estimateHours ?: 0 }
            val daysLeft = LocalDate.parse(sprint.endDate).toEpochDay() - today.toEpochDay()

            SprintProgress(
                sprintName = sprint.name,
                totalTasks = sprintTasks.size,
                doneTasks = doneTasks,
                remainingHours = remainingHours,
                daysLeft = daysLeft.toInt(),
                velocityPercent = if (sprintTasks.isNotEmpty()) (doneTasks * 100 / sprintTasks.size) else 0
            )
        }

        return TeamStats(
            totalTasks = tasks.size,
            byStatus = tasks.groupBy { it.status.name }.mapValues { it.value.size },
            byPriority = tasks.groupBy { it.priority.name }.mapValues { it.value.size },
            byAssignee = tasks
                .filter { it.assigneeId != null }
                .groupBy { it.assigneeId!! }
                .mapValues { it.value.size },
            sprintProgress = sprintProgress,
            blockedTasks = tasks.count { it.status == TaskStatus.BLOCKED }
        )
    }

    // === Рекомендации по приоритетам ===

    fun getPriorityRecommendations(limit: Int = 5): List<PriorityRecommendation> {
        val activeTasks = tasks.filter {
            it.status in listOf(TaskStatus.TODO, TaskStatus.BACKLOG, TaskStatus.BLOCKED)
        }

        return activeTasks.map { task ->
            var score = 0
            val reasons = mutableListOf<String>()

            // Приоритет
            when (task.priority) {
                TaskPriority.CRITICAL -> { score += 40; reasons.add("CRITICAL приоритет") }
                TaskPriority.HIGH -> { score += 30; reasons.add("HIGH приоритет") }
                TaskPriority.MEDIUM -> { score += 15; reasons.add("MEDIUM приоритет") }
                TaskPriority.LOW -> { score += 5 }
            }

            // Тип задачи
            if (task.type == TaskType.BUG) { score += 15; reasons.add("Баг") }

            // Блокирует другие задачи
            val blocksCount = tasks.count { task.id in it.blockedBy }
            if (blocksCount > 0) {
                score += blocksCount * 10
                reasons.add("Блокирует $blocksCount задач")
            }

            // Близкий дедлайн
            task.dueDate?.let { due ->
                val daysUntilDue = LocalDate.parse(due).toEpochDay() - today.toEpochDay()
                if (daysUntilDue <= 1) { score += 20; reasons.add("Дедлайн завтра!") }
                else if (daysUntilDue <= 3) { score += 10; reasons.add("Дедлайн через $daysUntilDue дней") }
            }

            // В текущем спринте
            if (task.sprintId == getCurrentSprint()?.id) {
                score += 10
                reasons.add("В текущем спринте")
            }

            // Заблокирована - снижаем приоритет
            if (task.status == TaskStatus.BLOCKED) {
                score -= 20
                reasons.add("ЗАБЛОКИРОВАНА")
            }

            PriorityRecommendation(
                taskId = task.id,
                taskTitle = task.title,
                reason = reasons.joinToString(", "),
                score = score.coerceIn(0, 100)
            )
        }.sortedByDescending { it.score }.take(limit)
    }

    // === Поиск по навыкам ===

    fun findMembersBySkill(skill: String): List<TeamMember> {
        return teamMembers.filter { member ->
            member.skills.any { it.contains(skill, ignoreCase = true) }
        }
    }
}
