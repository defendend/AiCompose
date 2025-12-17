package org.example.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.example.model.Reminder
import org.example.model.ReminderStatus
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Репозиторий для управления напоминаниями
 * Хранит данные в JSON файле
 */
class ReminderRepository(
    private val storageFile: File = File("reminders.json")
) {
    private val logger = LoggerFactory.getLogger(ReminderRepository::class.java)
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val reminders = mutableListOf<Reminder>()

    init {
        loadFromFile()
    }

    /**
     * Добавить новое напоминание
     */
    fun add(reminder: Reminder): Reminder {
        reminders.add(reminder)
        saveToFile()
        logger.info("Добавлено напоминание: ${reminder.title} на ${reminder.reminderTime}")
        return reminder
    }

    /**
     * Получить напоминание по ID
     */
    fun getById(id: String): Reminder? {
        return reminders.find { it.id == id }
    }

    /**
     * Получить все напоминания
     */
    fun getAll(): List<Reminder> {
        return reminders.toList()
    }

    /**
     * Получить напоминания по статусу
     */
    fun getByStatus(status: ReminderStatus): List<Reminder> {
        return reminders.filter { it.status == status }
    }

    /**
     * Получить просроченные напоминания
     */
    fun getOverdue(): List<Reminder> {
        val now = Instant.now()
        return reminders.filter {
            it.status == ReminderStatus.PENDING &&
            it.reminderTime.isBefore(now) &&
            !it.notified
        }
    }

    /**
     * Получить напоминания на сегодня
     */
    fun getToday(): List<Reminder> {
        val today = LocalDate.now()
        return reminders.filter {
            it.status == ReminderStatus.PENDING &&
            it.reminderTime.atZone(ZoneId.systemDefault()).toLocalDate() == today
        }
    }

    /**
     * Получить ближайшие напоминания (следующие 24 часа)
     */
    fun getUpcoming(limit: Int = 10): List<Reminder> {
        val now = Instant.now()
        val next24Hours = now.plusSeconds(24 * 60 * 60)

        return reminders
            .filter {
                it.status == ReminderStatus.PENDING &&
                it.reminderTime.isAfter(now) &&
                it.reminderTime.isBefore(next24Hours)
            }
            .sortedBy { it.reminderTime }
            .take(limit)
    }

    /**
     * Получить уведомленные напоминания (для отображения в Desktop)
     * Возвращает последние N напоминаний, которые были помечены как notified
     */
    fun getNotifications(limit: Int = 10): List<Reminder> {
        return reminders
            .filter { it.notified }
            .sortedByDescending { it.updatedAt }
            .take(limit)
    }

    /**
     * Обновить напоминание
     */
    fun update(id: String, updater: (Reminder) -> Reminder): Reminder? {
        val index = reminders.indexOfFirst { it.id == id }
        if (index == -1) return null

        val updated = updater(reminders[index])
        reminders[index] = updated
        saveToFile()
        logger.info("Обновлено напоминание: ${updated.title}")
        return updated
    }

    /**
     * Пометить как выполненное
     */
    fun complete(id: String): Reminder? {
        return update(id) { it.copy(status = ReminderStatus.COMPLETED, updatedAt = Instant.now()) }
    }

    /**
     * Пометить как уведомленное
     */
    fun markNotified(id: String): Reminder? {
        return update(id) { it.copy(notified = true, updatedAt = Instant.now()) }
    }

    /**
     * Удалить напоминание
     */
    fun delete(id: String): Boolean {
        val removed = reminders.removeIf { it.id == id }
        if (removed) {
            saveToFile()
            logger.info("Удалено напоминание с ID: $id")
        }
        return removed
    }

    /**
     * Сохранить в файл
     */
    private fun saveToFile() {
        try {
            val jsonString = json.encodeToString(reminders)
            storageFile.writeText(jsonString)
        } catch (e: Exception) {
            logger.error("Ошибка сохранения напоминаний в файл", e)
        }
    }

    /**
     * Загрузить из файла
     */
    private fun loadFromFile() {
        try {
            if (!storageFile.exists()) {
                logger.info("Файл напоминаний не найден, создаём новый")
                return
            }

            val jsonString = storageFile.readText()
            if (jsonString.isBlank()) return

            val loaded = json.decodeFromString<List<Reminder>>(jsonString)
            reminders.clear()
            reminders.addAll(loaded)
            logger.info("Загружено ${reminders.size} напоминаний из файла")
        } catch (e: Exception) {
            logger.error("Ошибка загрузки напоминаний из файла", e)
        }
    }
}
