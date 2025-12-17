package org.example.model

import kotlinx.serialization.Serializable

/**
 * Ответ API с уведомлениями о напоминаниях
 */
@Serializable
data class ReminderNotificationsResponse(
    val notifications: List<ReminderNotification>,
    val count: Int
)

/**
 * Уведомление о напоминании (упрощённая модель для Desktop)
 */
@Serializable
data class ReminderNotification(
    val id: String,
    val title: String,
    val description: String? = null,
    val reminderTime: String,  // ISO-8601 string
    val notified: Boolean = false
)
