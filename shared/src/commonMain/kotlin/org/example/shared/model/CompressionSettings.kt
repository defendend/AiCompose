package org.example.shared.model

import kotlinx.serialization.Serializable

/**
 * Настройки сжатия истории диалога.
 */
@Serializable
data class CompressionSettings(
    val enabled: Boolean = false,
    val messageThreshold: Int = 10,     // После скольки сообщений делать сжатие
    val keepRecentMessages: Int = 4     // Сколько последних сообщений оставлять
)

/**
 * Статистика сжатия для ответа API.
 */
@Serializable
data class CompressionStats(
    val totalCompressions: Int = 0,
    val totalTokensSaved: Int = 0,
    val currentSummary: String? = null,
    val lastCompressionResult: CompressionResult? = null
)

/**
 * Результат операции сжатия.
 */
@Serializable
data class CompressionResult(
    val compressed: Boolean,
    val originalMessageCount: Int,
    val compressedMessageCount: Int,
    val summary: String? = null,
    val estimatedTokensSaved: Int = 0
)
