package org.example.tools.system

import org.example.tools.annotations.Tool
import org.example.tools.core.AnnotatedAgentTool
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Инструмент для получения текущего времени.
 * Позволяет агенту вычислять относительное время ("через 2 минуты", "завтра").
 */
@Tool(
    name = "get_current_time",
    description = "Получить текущее время и дату в формате ISO-8601. Используй для вычисления относительного времени (через N минут/часов, завтра, послезавтра)"
)
object CurrentTimeTool : AnnotatedAgentTool() {

    private val humanReadableFormatter = DateTimeFormatter
        .ofPattern("dd.MM.yyyy HH:mm:ss")
        .withZone(ZoneId.systemDefault())

    override suspend fun execute(arguments: String): String {
        return try {
            val now = Instant.now()

            buildString {
                appendLine("📅 Текущее время:")
                appendLine("ISO-8601: ${now}")
                appendLine("Читаемый формат: ${humanReadableFormatter.format(now)}")
                appendLine()
                appendLine("💡 Для вычислений относительного времени:")
                appendLine("  • Через 1 минуту:  ${now.plusSeconds(60)}")
                appendLine("  • Через 5 минут:   ${now.plusSeconds(300)}")
                appendLine("  • Через 1 час:     ${now.plusSeconds(3600)}")
                appendLine("  • Через 1 день:    ${now.plusSeconds(86400)}")
                appendLine("  • Через 1 неделю:  ${now.plusSeconds(604800)}")
                appendLine()
                appendLine("⏰ Используй эти значения для reminder_add с параметром reminder_time")
            }
        } catch (e: Exception) {
            "Ошибка получения времени: ${e.message}"
        }
    }
}
