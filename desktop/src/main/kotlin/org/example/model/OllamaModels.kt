package org.example.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Модель Ollama для бенчмарка (не путать с OllamaModel в OllamaClient.kt).
 */
data class OllamaBenchmarkModel(
    val id: String,
    val name: String,
    val parameters: String,
    val description: String,
    val contextSize: Int = 2048,
    val category: OllamaBenchmarkCategory = OllamaBenchmarkCategory.SMALL
)

enum class OllamaBenchmarkCategory(val label: String, val icon: String) {
    LARGE("Большие", "🏆"),
    MEDIUM("Средние", "⚖️"),
    SMALL("Компактные", "🚀")
}

/**
 * Доступные модели Ollama для тестирования.
 */
object AvailableOllamaModels {
    val largeModels = listOf(
        OllamaBenchmarkModel(
            id = "llama3.2:3b",
            name = "Llama 3.2 3B",
            parameters = "3B",
            description = "Meta Llama 3.2, баланс качества и скорости",
            contextSize = 128000,
            category = OllamaBenchmarkCategory.LARGE
        ),
        OllamaBenchmarkModel(
            id = "qwen2.5:3b",
            name = "Qwen 2.5 3B",
            parameters = "3B",
            description = "Alibaba Qwen 2.5, хорош для кода",
            contextSize = 32000,
            category = OllamaBenchmarkCategory.LARGE
        )
    )

    val mediumModels = listOf(
        OllamaBenchmarkModel(
            id = "qwen2.5:1.5b",
            name = "Qwen 2.5 1.5B",
            parameters = "1.5B",
            description = "Рекомендуемая: лучший баланс скорости и качества",
            contextSize = 32000,
            category = OllamaBenchmarkCategory.MEDIUM
        ),
        OllamaBenchmarkModel(
            id = "llama3.2:1b",
            name = "Llama 3.2 1B",
            parameters = "1B",
            description = "Meta Llama 3.2, компактная версия",
            contextSize = 128000,
            category = OllamaBenchmarkCategory.MEDIUM
        )
    )

    val smallModels = listOf(
        OllamaBenchmarkModel(
            id = "qwen2.5:0.5b",
            name = "Qwen 2.5 0.5B",
            parameters = "0.5B",
            description = "Самая быстрая, для простых задач",
            contextSize = 32000,
            category = OllamaBenchmarkCategory.SMALL
        )
    )

    val allModels = largeModels + mediumModels + smallModels

    fun getByCategory(category: OllamaBenchmarkCategory): List<OllamaBenchmarkModel> = when (category) {
        OllamaBenchmarkCategory.LARGE -> largeModels
        OllamaBenchmarkCategory.MEDIUM -> mediumModels
        OllamaBenchmarkCategory.SMALL -> smallModels
    }
}

/**
 * Конфигурация параметров для тестирования.
 */
data class OllamaTestConfig(
    val name: String,
    val temperature: Float,
    val maxTokens: Int,
    val numCtx: Int,
    val description: String
)

/**
 * Предустановленные конфигурации для тестирования.
 */
object OllamaTestConfigs {
    val temperatureConfigs = listOf(
        OllamaTestConfig("Детерминированный", 0.1f, 200, 2048, "Минимальная случайность"),
        OllamaTestConfig("Сбалансированный", 0.7f, 200, 2048, "Рекомендуемый баланс"),
        OllamaTestConfig("Креативный", 1.2f, 200, 2048, "Больше разнообразия")
    )

    val tokenConfigs = listOf(
        OllamaTestConfig("Краткий", 0.7f, 50, 2048, "До 50 токенов"),
        OllamaTestConfig("Средний", 0.7f, 200, 2048, "До 200 токенов"),
        OllamaTestConfig("Подробный", 0.7f, 500, 2048, "До 500 токенов")
    )

    val contextConfigs = listOf(
        OllamaTestConfig("Малый контекст", 0.7f, 200, 1024, "1K контекст"),
        OllamaTestConfig("Средний контекст", 0.7f, 200, 2048, "2K контекст"),
        OllamaTestConfig("Большой контекст", 0.7f, 200, 4096, "4K контекст")
    )

    val defaultConfig = OllamaTestConfig("Базовый", 0.7f, 200, 2048, "Стандартные параметры")
}

/**
 * Результат теста Ollama.
 */
data class OllamaTestResult(
    val model: OllamaBenchmarkModel,
    val config: OllamaTestConfig,
    val prompt: String,
    val response: String,
    val responseTimeMs: Long,
    val responseLength: Int,
    val estimatedTokens: Int,
    val tokensPerSecond: Float,
    val error: String? = null
) {
    val isSuccess: Boolean get() = error == null
}

/**
 * Состояние бенчмарка Ollama.
 */
data class OllamaBenchmarkState(
    val isRunning: Boolean = false,
    val currentTest: String = "",
    val progress: Int = 0,
    val totalTests: Int = 0,
    val results: List<OllamaTestResult> = emptyList(),
    val error: String? = null
)

// === API Models ===

@Serializable
data class OllamaGenerateRequest(
    val model: String,
    val prompt: String,
    val stream: Boolean = false,
    val options: OllamaOptions? = null
)

@Serializable
data class OllamaOptions(
    val temperature: Float? = null,
    @SerialName("num_predict")
    val numPredict: Int? = null,
    @SerialName("num_ctx")
    val numCtx: Int? = null,
    @SerialName("top_k")
    val topK: Int? = null,
    @SerialName("top_p")
    val topP: Float? = null,
    @SerialName("repeat_penalty")
    val repeatPenalty: Float? = null
)

@Serializable
data class OllamaGenerateResponse(
    val model: String = "",
    val response: String = "",
    val done: Boolean = false,
    @SerialName("total_duration")
    val totalDuration: Long? = null,
    @SerialName("load_duration")
    val loadDuration: Long? = null,
    @SerialName("prompt_eval_count")
    val promptEvalCount: Int? = null,
    @SerialName("eval_count")
    val evalCount: Int? = null,
    @SerialName("eval_duration")
    val evalDuration: Long? = null
)

@Serializable
data class OllamaBenchmarkTagsResponse(
    val models: List<OllamaBenchmarkModelInfo> = emptyList()
)

@Serializable
data class OllamaBenchmarkModelInfo(
    val name: String = "",
    val size: Long = 0,
    @SerialName("modified_at")
    val modifiedAt: String = ""
)
