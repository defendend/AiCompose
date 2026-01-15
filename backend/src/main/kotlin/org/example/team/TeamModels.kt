package org.example.team

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate

/**
 * Приоритет задачи.
 */
@Serializable
enum class TaskPriority {
    CRITICAL,   // Критический - блокирует релиз
    HIGH,       // Высокий - важная фича/баг
    MEDIUM,     // Средний - стандартная задача
    LOW         // Низкий - улучшения, рефакторинг
}

/**
 * Статус задачи.
 */
@Serializable
enum class TaskStatus {
    BACKLOG,      // В бэклоге
    TODO,         // К выполнению
    IN_PROGRESS,  // В работе
    REVIEW,       // На ревью
    TESTING,      // На тестировании
    DONE,         // Выполнено
    BLOCKED       // Заблокировано
}

/**
 * Тип задачи.
 */
@Serializable
enum class TaskType {
    FEATURE,    // Новая функциональность
    BUG,        // Баг
    TASK,       // Техническая задача
    EPIC,       // Эпик (большая фича)
    STORY       // Пользовательская история
}

/**
 * Член команды.
 */
@Serializable
data class TeamMember(
    val id: String,
    val name: String,
    val role: String,           // Developer, QA, Designer, PM, etc.
    val email: String,
    val skills: List<String>,   // kotlin, compose, backend, etc.
    val capacity: Int = 40,     // Часов в неделю
    val currentLoad: Int = 0    // Текущая загрузка в часах
)

/**
 * Задача команды.
 */
@Serializable
data class TeamTask(
    val id: String,
    val title: String,
    val description: String,
    val type: TaskType,
    val priority: TaskPriority,
    val status: TaskStatus,
    val assigneeId: String? = null,
    val reporterId: String,
    val sprintId: String? = null,
    val estimateHours: Int? = null,
    val labels: List<String> = emptyList(),
    val blockedBy: List<String> = emptyList(),  // ID задач-блокеров
    val createdAt: String,
    val updatedAt: String,
    val dueDate: String? = null
)

/**
 * Спринт.
 */
@Serializable
data class Sprint(
    val id: String,
    val name: String,
    val goal: String,
    val startDate: String,
    val endDate: String,
    val status: SprintStatus
)

@Serializable
enum class SprintStatus {
    PLANNED,    // Запланирован
    ACTIVE,     // Активный
    COMPLETED   // Завершён
}

/**
 * Информация о проекте.
 */
@Serializable
data class ProjectInfo(
    val name: String,
    val description: String,
    val techStack: List<String>,
    val repository: String,
    val team: List<TeamMember>,
    val currentSprint: Sprint?
)

/**
 * Рекомендация по приоритетам.
 */
@Serializable
data class PriorityRecommendation(
    val taskId: String,
    val taskTitle: String,
    val reason: String,
    val score: Int  // 1-100, чем выше - тем приоритетнее
)

/**
 * Статистика команды.
 */
@Serializable
data class TeamStats(
    val totalTasks: Int,
    val byStatus: Map<String, Int>,
    val byPriority: Map<String, Int>,
    val byAssignee: Map<String, Int>,
    val sprintProgress: SprintProgress?,
    val blockedTasks: Int
)

@Serializable
data class SprintProgress(
    val sprintName: String,
    val totalTasks: Int,
    val doneTasks: Int,
    val remainingHours: Int,
    val daysLeft: Int,
    val velocityPercent: Int
)
