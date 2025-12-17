package org.example.model

import kotlinx.serialization.Serializable

/**
 * DTO для уведомлений о напоминаниях (для API)
 */
@Serializable
data class ReminderNotificationDto(
    val id: String,
    val title: String,
    val description: String? = null,
    val reminderTime: String,  // ISO-8601 string
    val notified: Boolean
)

/**
 * Ответ API с уведомлениями
 */
@Serializable
data class ReminderNotificationsResponse(
    val notifications: List<ReminderNotificationDto>,
    val count: Int
)
