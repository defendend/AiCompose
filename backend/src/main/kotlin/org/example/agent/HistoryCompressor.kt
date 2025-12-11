package org.example.agent

import org.example.data.LLMClient
import org.example.logging.ServerLogger
import org.example.model.LLMMessage

/**
 * Механизм сжатия истории диалога.
 *
 * Каждые N сообщений создаёт summary и заменяет оригинальные сообщения на сжатую версию.
 * Это позволяет:
 * - Уменьшить использование токенов
 * - Сохранить контекст диалога
 * - Работать с длинными диалогами без превышения лимита контекста
 */
class HistoryCompressor(
    private val llmClient: LLMClient,
    private val config: CompressionConfig = CompressionConfig()
) {
    /**
     * Конфигурация сжатия истории.
     */
    data class CompressionConfig(
        val enabled: Boolean = false,
        val messageThreshold: Int = 10,        // После скольки сообщений делать сжатие
        val keepRecentMessages: Int = 4,       // Сколько последних сообщений оставлять без сжатия
        val summaryMaxTokens: Int = 500,       // Максимум токенов для summary
        val summaryTemperature: Float = 0.3f   // Низкая температура для точного резюме
    )

    /**
     * Результат сжатия истории.
     */
    data class CompressionResult(
        val compressed: Boolean,
        val originalMessageCount: Int,
        val compressedMessageCount: Int,
        val summary: String? = null,
        val estimatedTokensSaved: Int = 0
    )

    /**
     * Статистика сжатия для диалога.
     */
    data class CompressionStats(
        val totalCompressions: Int = 0,
        val totalTokensSaved: Int = 0,
        val currentSummary: String? = null
    )

    // Хранение статистики по диалогам
    private val stats = mutableMapOf<String, CompressionStats>()

    // Промпт для генерации summary
    private val summarySystemPrompt = """
        Ты — ассистент для создания кратких резюме диалогов.

        Твоя задача: создать краткое, но информативное резюме диалога, сохраняя:
        1. Ключевые факты и решения
        2. Важные вопросы пользователя
        3. Основные ответы и рекомендации
        4. Контекст, необходимый для продолжения диалога

        Формат резюме:
        - Начни с "📋 Резюме предыдущего диалога:"
        - Используй маркированный список
        - Будь лаконичен, но не теряй важную информацию
        - Пиши на том же языке, что и диалог
    """.trimIndent()

    /**
     * Проверить, нужно ли сжатие для данной истории.
     */
    fun needsCompression(history: List<LLMMessage>): Boolean {
        if (!config.enabled) return false

        // Считаем только user и assistant сообщения (не system, не tool)
        val dialogueMessages = history.count { it.role in listOf("user", "assistant") }
        return dialogueMessages >= config.messageThreshold
    }

    /**
     * Сжать историю диалога.
     *
     * @param history Текущая история диалога
     * @param conversationId ID диалога для статистики
     * @return Сжатая история + результат операции
     */
    suspend fun compress(
        history: List<LLMMessage>,
        conversationId: String
    ): Pair<List<LLMMessage>, CompressionResult> {
        if (!config.enabled || history.isEmpty()) {
            return history to CompressionResult(
                compressed = false,
                originalMessageCount = history.size,
                compressedMessageCount = history.size
            )
        }

        // Разделяем историю на части
        val systemMessage = history.firstOrNull { it.role == "system" }
        val dialogueMessages = history.filter { it.role != "system" }

        // Если недостаточно сообщений для сжатия
        if (dialogueMessages.size < config.messageThreshold) {
            return history to CompressionResult(
                compressed = false,
                originalMessageCount = history.size,
                compressedMessageCount = history.size
            )
        }

        // Определяем, какие сообщения сжимать, а какие оставить
        val messagesToCompress = dialogueMessages.dropLast(config.keepRecentMessages)
        val recentMessages = dialogueMessages.takeLast(config.keepRecentMessages)

        // Если нечего сжимать
        if (messagesToCompress.isEmpty()) {
            return history to CompressionResult(
                compressed = false,
                originalMessageCount = history.size,
                compressedMessageCount = history.size
            )
        }

        ServerLogger.logSystem("HistoryCompressor: Сжатие истории: ${messagesToCompress.size} сообщений -> summary")

        // Генерируем summary
        val summary = generateSummary(messagesToCompress)

        // Оцениваем сэкономленные токены (грубая оценка: ~4 символа = 1 токен)
        val originalChars = messagesToCompress.sumOf { (it.content?.length ?: 0) }
        val summaryChars = summary.length
        val estimatedTokensSaved = ((originalChars - summaryChars) / 4).coerceAtLeast(0)

        // Создаём сжатую историю
        val compressedHistory = buildList {
            // System message
            systemMessage?.let { add(it) }

            // Summary как сообщение от assistant
            add(LLMMessage(
                role = "assistant",
                content = summary
            ))

            // Последние сообщения без изменений
            addAll(recentMessages)
        }

        // Обновляем статистику
        val currentStats = stats.getOrDefault(conversationId, CompressionStats())
        stats[conversationId] = currentStats.copy(
            totalCompressions = currentStats.totalCompressions + 1,
            totalTokensSaved = currentStats.totalTokensSaved + estimatedTokensSaved,
            currentSummary = summary
        )

        ServerLogger.logSystem(
            "HistoryCompressor: Сжатие завершено: ${history.size} -> ${compressedHistory.size} сообщений, " +
                "~$estimatedTokensSaved токенов сэкономлено"
        )

        return compressedHistory to CompressionResult(
            compressed = true,
            originalMessageCount = history.size,
            compressedMessageCount = compressedHistory.size,
            summary = summary,
            estimatedTokensSaved = estimatedTokensSaved
        )
    }

    /**
     * Сгенерировать summary для списка сообщений.
     */
    private suspend fun generateSummary(messages: List<LLMMessage>): String {
        // Форматируем диалог для LLM
        val dialogueText = messages.joinToString("\n") { msg ->
            val role = when (msg.role) {
                "user" -> "Пользователь"
                "assistant" -> "Ассистент"
                "tool" -> "Инструмент"
                else -> msg.role
            }
            "$role: ${msg.content ?: "[пусто]"}"
        }

        val summaryRequest = listOf(
            LLMMessage(role = "system", content = summarySystemPrompt),
            LLMMessage(role = "user", content = "Создай краткое резюме следующего диалога:\n\n$dialogueText")
        )

        return try {
            val response = llmClient.chat(
                messages = summaryRequest,
                tools = emptyList(),
                temperature = config.summaryTemperature,
                conversationId = "compression-temp"
            )

            response.choices.firstOrNull()?.message?.content
                ?: "📋 Резюме недоступно"
        } catch (e: Exception) {
            ServerLogger.logError("HistoryCompressor: Ошибка генерации summary: ${e.message}", e)
            // Fallback: простое сокращение
            createFallbackSummary(messages)
        }
    }

    /**
     * Создать fallback summary без вызова LLM.
     */
    private fun createFallbackSummary(messages: List<LLMMessage>): String {
        val userMessages = messages.filter { it.role == "user" }
        val topics = userMessages.mapNotNull { it.content?.take(100) }

        return buildString {
            appendLine("📋 Резюме предыдущего диалога:")
            appendLine("• Обсуждено ${messages.size} сообщений")
            if (topics.isNotEmpty()) {
                appendLine("• Основные темы:")
                topics.take(3).forEach { topic ->
                    appendLine("  - ${topic}...")
                }
            }
        }
    }

    /**
     * Получить статистику сжатия для диалога.
     */
    fun getStats(conversationId: String): CompressionStats? = stats[conversationId]

    /**
     * Получить общую статистику.
     */
    fun getAllStats(): Map<String, CompressionStats> = stats.toMap()

    /**
     * Очистить статистику.
     */
    fun clearStats() = stats.clear()
}
