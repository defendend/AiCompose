package org.example.notification

import org.slf4j.LoggerFactory
import java.io.File

/**
 * Менеджер системных уведомлений для macOS/Windows
 */
object NotificationManager {
    private val logger = LoggerFactory.getLogger(NotificationManager::class.java)
    private val isMacOS = System.getProperty("os.name").lowercase().contains("mac")

    /**
     * Инициализация (проверяем доступность osascript на macOS)
     */
    fun initialize() {
        try {
            if (isMacOS) {
                // Проверяем наличие osascript
                val osascriptExists = File("/usr/bin/osascript").exists()
                if (osascriptExists) {
                    logger.info("✅ macOS уведомления готовы (osascript)")
                } else {
                    logger.warn("⚠️ osascript не найден, уведомления могут не работать")
                }
            } else {
                logger.info("✅ Платформа: ${System.getProperty("os.name")}")
            }
        } catch (e: Exception) {
            logger.error("Ошибка инициализации NotificationManager", e)
        }
    }

    /**
     * Показать системное уведомление
     *
     * @param title Заголовок уведомления
     * @param message Текст уведомления
     */
    fun showNotification(title: String, message: String) {
        try {
            if (isMacOS) {
                showMacOSNotification(title, message)
            } else {
                // Для Windows/Linux можно добавить другие методы
                logger.warn("Уведомления на этой платформе пока не поддерживаются")
            }
        } catch (e: Exception) {
            logger.error("Ошибка показа уведомления", e)
        }
    }

    /**
     * Показать нативное macOS уведомление через osascript
     */
    private fun showMacOSNotification(title: String, message: String) {
        try {
            // Экранируем кавычки в title и message
            val escapedTitle = title.replace("\"", "\\\"")
            val escapedMessage = message.replace("\"", "\\\"")

            // AppleScript команда для показа нотификации
            val script = """display notification "$escapedMessage" with title "$escapedTitle" sound name "Frog""""

            val process = Runtime.getRuntime().exec(arrayOf(
                "osascript",
                "-e",
                script
            ))

            val exitCode = process.waitFor()
            if (exitCode == 0) {
                logger.info("📬 Показано macOS уведомление: $title")
            } else {
                logger.error("osascript завершился с кодом $exitCode")
            }
        } catch (e: Exception) {
            logger.error("Ошибка показа macOS уведомления", e)
        }
    }

    /**
     * Очистка ресурсов (не требуется для osascript)
     */
    fun cleanup() {
        logger.info("NotificationManager cleanup")
    }
}
