package org.example.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Модель для чата из HuggingFace Inference API
 */
data class HuggingFaceModel(
    val id: String,
    val name: String,
    val provider: String,
    val parameters: String,
    val category: ModelCategory,
    val description: String,
    val pricing: ModelPricing? = null
)

enum class ModelCategory {
    TOP,      // Топовые модели (начало списка)
    MIDDLE,   // Средние модели
    SMALL     // Маленькие модели (конец списка)
}

data class ModelPricing(
    val inputPer1kTokens: Double,
    val outputPer1kTokens: Double,
    val currency: String = "USD"
)

/**
 * Результат сравнения модели
 */
data class ModelComparisonResult(
    val model: HuggingFaceModel,
    val response: String,
    val responseTimeMs: Long,
    val inputTokens: Int,
    val outputTokens: Int,
    val totalCost: Double?,
    val error: String? = null
)

/**
 * Состояние сравнения
 */
data class ComparisonState(
    val isRunning: Boolean = false,
    val prompt: String = "",
    val results: List<ModelComparisonResult> = emptyList(),
    val selectedModels: List<HuggingFaceModel> = emptyList()
)

/**
 * Запрос к HuggingFace API (OpenAI-совместимый формат)
 */
@Serializable
data class HFChatRequest(
    val model: String,
    val messages: List<HFChatMessage>,
    val stream: Boolean = false,
    @SerialName("max_tokens")
    val maxTokens: Int = 512,
    val temperature: Double = 0.7
)

@Serializable
data class HFChatMessage(
    val role: String,
    val content: String
)

/**
 * Ответ от HuggingFace API
 */
@Serializable
data class HFChatResponse(
    val id: String = "",
    val model: String = "",
    val choices: List<HFChatChoice> = emptyList(),
    val usage: HFUsage? = null,
    val created: Long = 0
)

@Serializable
data class HFChatChoice(
    val index: Int = 0,
    val message: HFResponseMessage? = null,
    @SerialName("finish_reason")
    val finishReason: String? = null
)

@Serializable
data class HFResponseMessage(
    val role: String = "",
    val content: String = ""
)

@Serializable
data class HFUsage(
    @SerialName("prompt_tokens")
    val promptTokens: Int = 0,
    @SerialName("completion_tokens")
    val completionTokens: Int = 0,
    @SerialName("total_tokens")
    val totalTokens: Int = 0
)

/**
 * Доступные модели для сравнения
 */
object AvailableModels {

    // Топовые модели (большие, мощные)
    val topModels = listOf(
        HuggingFaceModel(
            id = "deepseek-ai/DeepSeek-V3-0324",
            name = "DeepSeek V3",
            provider = "Novita, Fireworks AI",
            parameters = "685B",
            category = ModelCategory.TOP,
            description = "Флагманская модель DeepSeek с отличными рассуждениями",
            pricing = ModelPricing(0.0014, 0.0028)
        ),
        HuggingFaceModel(
            id = "meta-llama/Llama-3.3-70B-Instruct",
            name = "Llama 3.3 70B",
            provider = "Together, Groq, Cerebras",
            parameters = "70B",
            category = ModelCategory.TOP,
            description = "Новейшая модель Llama от Meta",
            pricing = ModelPricing(0.0012, 0.0012)
        ),
        HuggingFaceModel(
            id = "Qwen/Qwen2.5-72B-Instruct",
            name = "Qwen 2.5 72B",
            provider = "Together, Novita",
            parameters = "72B",
            category = ModelCategory.TOP,
            description = "Мощная инструктивная модель от Alibaba",
            pricing = ModelPricing(0.0012, 0.0012)
        )
    )

    // Средние модели (7-10B параметров)
    val middleModels = listOf(
        HuggingFaceModel(
            id = "meta-llama/Llama-3.1-8B-Instruct",
            name = "Llama 3.1 8B",
            provider = "Scaleway, SambaNova, Cerebras",
            parameters = "8B",
            category = ModelCategory.MIDDLE,
            description = "Компактная но мощная модель от Meta",
            pricing = ModelPricing(0.00005, 0.00005)
        ),
        HuggingFaceModel(
            id = "Qwen/Qwen2.5-7B-Instruct",
            name = "Qwen 2.5 7B",
            provider = "Together, Featherless AI",
            parameters = "7.6B",
            category = ModelCategory.MIDDLE,
            description = "Компактная инструктивная модель Qwen",
            pricing = ModelPricing(0.00004, 0.00004)
        ),
        HuggingFaceModel(
            id = "meta-llama/Llama-3.2-3B-Instruct",
            name = "Llama 3.2 3B",
            provider = "Together, Cerebras",
            parameters = "3B",
            category = ModelCategory.MIDDLE,
            description = "Компактная и быстрая модель Llama",
            pricing = ModelPricing(0.0001, 0.0001)
        )
    )

    // Маленькие модели (1-2B параметров, быстрые, экономичные)
    val smallModels = listOf(
        HuggingFaceModel(
            id = "google/gemma-2-2b-it",
            name = "Gemma 2 2B",
            provider = "Novita, Fireworks AI",
            parameters = "2B",
            category = ModelCategory.SMALL,
            description = "Сверхкомпактная модель от Google",
            pricing = ModelPricing(0.00003, 0.00003)
        ),
        HuggingFaceModel(
            id = "meta-llama/Llama-3.2-1B-Instruct",
            name = "Llama 3.2 1B",
            provider = "Together, Cerebras",
            parameters = "1B",
            category = ModelCategory.SMALL,
            description = "Самая компактная Llama модель",
            pricing = ModelPricing(0.00002, 0.00002)
        )
    )

    val allModels = topModels + middleModels + smallModels

    fun getByCategory(category: ModelCategory): List<HuggingFaceModel> {
        return when (category) {
            ModelCategory.TOP -> topModels
            ModelCategory.MIDDLE -> middleModels
            ModelCategory.SMALL -> smallModels
        }
    }
}
