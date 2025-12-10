package org.example.shared.model

import kotlinx.serialization.Serializable

/**
 * Информация об использовании токенов.
 */
@Serializable
data class TokenUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
    val estimatedCostUsd: Double? = null
) {
    companion object {
        // Цены DeepSeek API (декабрь 2024)
        // https://platform.deepseek.com/api-docs/pricing
        private const val PRICE_PER_1M_INPUT_TOKENS = 0.14  // $0.14 за 1M input
        private const val PRICE_PER_1M_OUTPUT_TOKENS = 0.28 // $0.28 за 1M output

        fun fromUsage(promptTokens: Int, completionTokens: Int): TokenUsage {
            val totalTokens = promptTokens + completionTokens
            val cost = calculateCost(promptTokens, completionTokens)
            return TokenUsage(
                promptTokens = promptTokens,
                completionTokens = completionTokens,
                totalTokens = totalTokens,
                estimatedCostUsd = cost
            )
        }

        /**
         * Рассчитать стоимость в USD.
         */
        fun calculateCost(promptTokens: Int, completionTokens: Int): Double {
            val inputCost = promptTokens * PRICE_PER_1M_INPUT_TOKENS / 1_000_000
            val outputCost = completionTokens * PRICE_PER_1M_OUTPUT_TOKENS / 1_000_000
            return inputCost + outputCost
        }
    }

    /**
     * Форматированная строка стоимости.
     */
    fun formatCost(): String {
        return estimatedCostUsd?.let {
            if (it < 0.0001) "< $0.0001"
            else "$${"%.4f".format(it)}"
        } ?: "N/A"
    }

    /**
     * Краткая строка для отображения в UI.
     */
    fun toShortString(): String {
        return "$totalTokens токенов (${formatCost()})"
    }

    /**
     * Полная строка с деталями.
     */
    fun toDetailedString(): String {
        return "Вход: $promptTokens | Выход: $completionTokens | Всего: $totalTokens | Стоимость: ${formatCost()}"
    }

    /**
     * Сложить два TokenUsage (для накопления по диалогу).
     */
    operator fun plus(other: TokenUsage): TokenUsage {
        return TokenUsage(
            promptTokens = this.promptTokens + other.promptTokens,
            completionTokens = this.completionTokens + other.completionTokens,
            totalTokens = this.totalTokens + other.totalTokens,
            estimatedCostUsd = (this.estimatedCostUsd ?: 0.0) + (other.estimatedCostUsd ?: 0.0)
        )
    }
}
