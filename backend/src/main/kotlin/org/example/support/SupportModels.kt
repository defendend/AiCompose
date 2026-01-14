package org.example.support

import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * Статус тикета поддержки.
 */
@Serializable
enum class TicketStatus {
    OPEN,       // Открыт, ожидает ответа
    IN_PROGRESS, // В работе
    WAITING,    // Ожидает ответа пользователя
    RESOLVED,   // Решён
    CLOSED      // Закрыт
}

/**
 * Приоритет тикета.
 */
@Serializable
enum class TicketPriority {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

/**
 * Категория тикета.
 */
@Serializable
enum class TicketCategory {
    AUTH,           // Авторизация
    BILLING,        // Оплата
    TECHNICAL,      // Технические проблемы
    FEATURE_REQUEST, // Запрос функции
    BUG,            // Баг
    GENERAL         // Общие вопросы
}

/**
 * Тарифный план пользователя.
 */
@Serializable
enum class UserPlan {
    FREE,
    BASIC,
    PRO,
    ENTERPRISE
}

/**
 * Тикет поддержки.
 */
@Serializable
data class Ticket(
    val id: String,
    val userId: String,
    val subject: String,
    val description: String,
    val category: TicketCategory = TicketCategory.GENERAL,
    val status: TicketStatus = TicketStatus.OPEN,
    val priority: TicketPriority = TicketPriority.MEDIUM,
    val messages: List<TicketMessage> = emptyList(),
    val createdAt: String, // ISO-8601
    val updatedAt: String  // ISO-8601
)

/**
 * Сообщение в тикете.
 */
@Serializable
data class TicketMessage(
    val id: String,
    val author: String, // userId или "support"
    val content: String,
    val createdAt: String
)

/**
 * Пользователь системы.
 */
@Serializable
data class SupportUser(
    val id: String,
    val name: String,
    val email: String,
    val plan: UserPlan = UserPlan.FREE,
    val registeredAt: String,
    val lastLoginAt: String? = null,
    val ticketCount: Int = 0
)

/**
 * FAQ запись.
 */
@Serializable
data class FaqEntry(
    val id: String,
    val question: String,
    val answer: String,
    val category: TicketCategory,
    val keywords: List<String> = emptyList()
)

/**
 * Хранилище данных поддержки.
 */
@Serializable
data class SupportData(
    val tickets: List<Ticket> = emptyList(),
    val users: List<SupportUser> = emptyList(),
    val faq: List<FaqEntry> = emptyList()
)

/**
 * Запрос в поддержку.
 */
@Serializable
data class SupportRequest(
    val question: String,
    val ticketId: String? = null,
    val userId: String? = null
)

/**
 * Ответ поддержки.
 */
@Serializable
data class SupportResponse(
    val answer: String,
    val sources: List<String> = emptyList(),
    val relatedFaq: List<String> = emptyList(),
    val ticketContext: TicketContext? = null,
    val durationMs: Long
)

/**
 * Контекст тикета для ответа.
 */
@Serializable
data class TicketContext(
    val ticketId: String,
    val subject: String,
    val status: TicketStatus,
    val category: TicketCategory,
    val userName: String?
)
