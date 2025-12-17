package org.example.scheduler

import kotlinx.coroutines.*
import org.example.data.ReminderRepository
import org.example.logging.ServerLogger
import org.example.model.LogLevel
import org.slf4j.LoggerFactory
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.minutes

/**
 * Планировщик для автоматических уведомлений о напоминаниях
 *
 * Работает в фоновом режиме и периодически проверяет:
 * - Просроченные напоминания
 * - Напоминания на сегодня
 * - Ближайшие напоминания
 */
class ReminderScheduler(
    private val reminderRepository: ReminderRepository,
    private val checkIntervalMinutes: Long = 5 // Проверять каждые 5 минут
) {
    private val logger = LoggerFactory.getLogger(ReminderScheduler::class.java)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault())

    private var job: Job? = null

    /**
     * Запустить планировщик
     */
    fun start() {
        if (job?.isActive == true) {
            logger.warn("Планировщик напоминаний уже запущен")
            return
        }

        job = scope.launch {
            logger.info("🔔 Планировщик напоминаний запущен (интервал: $checkIntervalMinutes мин)")
            ServerLogger.logSystem("Планировщик напоминаний запущен", LogLevel.INFO)

            while (isActive) {
                try {
                    checkAndNotify()
                } catch (e: Exception) {
                    logger.error("Ошибка при проверке напоминаний", e)
                }

                delay(checkIntervalMinutes.minutes)
            }
        }
    }

    /**
     * Остановить планировщик
     */
    fun stop() {
        job?.cancel()
        logger.info("🔕 Планировщик напоминаний остановлен")
        ServerLogger.logSystem("Планировщик напоминаний остановлен", LogLevel.INFO)
    }

    /**
     * Проверить напоминания и отправить уведомления
     */
    private suspend fun checkAndNotify() {
        val overdue = reminderRepository.getOverdue()

        if (overdue.isEmpty()) {
            logger.debug("Нет просроченных напоминаний")
            return
        }

        logger.info("⏰ Найдено ${overdue.size} просроченных напоминаний")

        // Формируем сводку
        val summary = buildString {
            appendLine("📊 СВОДКА НАПОМИНАНИЙ")
            appendLine("=" .repeat(50))
            appendLine()
            appendLine("⚠️  Просроченных напоминаний: ${overdue.size}")
            appendLine()

            overdue.forEach { reminder ->
                appendLine("📋 ${reminder.title}")
                appendLine("   ⏰ Время: ${formatter.format(reminder.reminderTime)}")
                if (reminder.description != null) {
                    appendLine("   💬 ${reminder.description}")
                }
                appendLine("   🆔 ${reminder.id}")
                appendLine()
            }

            appendLine("=" .repeat(50))
        }

        // Логируем сводку на уровне WARNING для видимости
        logger.warn("\n$summary")
        ServerLogger.logSystem(summary, LogLevel.WARNING)

        // Помечаем как уведомленные
        overdue.forEach { reminder ->
            reminderRepository.markNotified(reminder.id)
        }
    }

    /**
     * Получить текущую сводку (для ручного вызова)
     */
    suspend fun getCurrentSummary(): String {
        val all = reminderRepository.getAll()
        val pending = reminderRepository.getByStatus(org.example.model.ReminderStatus.PENDING)
        val overdue = reminderRepository.getOverdue()
        val today = reminderRepository.getToday()
        val upcoming = reminderRepository.getUpcoming(5)

        return buildString {
            appendLine("📊 Текущая сводка напоминаний")
            appendLine("=" .repeat(50))
            appendLine()
            appendLine("📋 Всего: ${all.size}")
            appendLine("⏳ Ожидают: ${pending.size}")
            appendLine("⚠️  Просрочено: ${overdue.size}")
            appendLine("📅 На сегодня: ${today.size}")
            appendLine()

            if (overdue.isNotEmpty()) {
                appendLine("❗ Просроченные:")
                overdue.take(3).forEach { reminder ->
                    appendLine("   • ${reminder.title} (${formatter.format(reminder.reminderTime)})")
                }
                if (overdue.size > 3) {
                    appendLine("   ... и ещё ${overdue.size - 3}")
                }
                appendLine()
            }

            if (upcoming.isNotEmpty()) {
                appendLine("🔜 Ближайшие:")
                upcoming.forEach { reminder ->
                    appendLine("   • ${reminder.title} (${formatter.format(reminder.reminderTime)})")
                }
            }

            appendLine("=" .repeat(50))
        }
    }
}
