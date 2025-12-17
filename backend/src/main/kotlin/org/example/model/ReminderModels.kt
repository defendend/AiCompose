package org.example.model

import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.*

/**
 * Статус напоминания
 */
enum class ReminderStatus {
    PENDING,    // Ожидает выполнения
    COMPLETED,  // Выполнено
    CANCELLED   // Отменено
}

/**
 * Модель напоминания
 */
@Serializable
data class Reminder(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String? = null,
    @Serializable(with = InstantSerializer::class)
    val reminderTime: Instant,
    val status: ReminderStatus = ReminderStatus.PENDING,
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant = Instant.now(),
    @Serializable(with = InstantSerializer::class)
    val updatedAt: Instant = Instant.now(),
    val notified: Boolean = false  // Было ли отправлено уведомление
)

/**
 * Сводка напоминаний
 */
data class ReminderSummary(
    val totalCount: Int,
    val pendingCount: Int,
    val overdueCount: Int,
    val todayCount: Int,
    val upcomingReminders: List<Reminder>
)

/**
 * Сериализатор для Instant
 */
object InstantSerializer : kotlinx.serialization.KSerializer<Instant> {
    override val descriptor = kotlinx.serialization.descriptors.PrimitiveSerialDescriptor(
        "Instant",
        kotlinx.serialization.descriptors.PrimitiveKind.STRING
    )

    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: Instant) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): Instant {
        return Instant.parse(decoder.decodeString())
    }
}
