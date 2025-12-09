package org.example.network

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.example.logging.AppLogger
import org.example.model.*

/**
 * Клиент для HuggingFace Inference Providers API.
 *
 * Использует OpenAI-совместимый формат через роутер HuggingFace.
 * Документация: https://huggingface.co/docs/inference-providers/index
 */
class HuggingFaceApiClient(
    private val apiToken: String? = null
) {
    private val baseUrl = "https://router.huggingface.co/v1"

    private val jsonParser = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
    }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(jsonParser)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 120000  // 2 минуты
            connectTimeoutMillis = 15000
            socketTimeoutMillis = 120000
        }
    }

    /**
     * Отправить запрос к модели и замерить метрики.
     *
     * @param model Модель для запроса
     * @param prompt Текст запроса
     * @param maxTokens Максимальное количество токенов в ответе
     * @param temperature Температура генерации
     * @return Результат с метриками
     */
    suspend fun sendRequest(
        model: HuggingFaceModel,
        prompt: String,
        maxTokens: Int = 512,
        temperature: Double = 0.7
    ): ModelComparisonResult {
        val startTime = System.currentTimeMillis()

        return try {
            AppLogger.info("HuggingFaceAPI", "Запрос к модели ${model.id}")

            // Добавляем :fastest для автоматического выбора провайдера
            val modelId = "${model.id}:fastest"

            val request = HFChatRequest(
                model = modelId,
                messages = listOf(
                    HFChatMessage(role = "user", content = prompt)
                ),
                stream = false,
                maxTokens = maxTokens,
                temperature = temperature
            )

            val response = client.post("$baseUrl/chat/completions") {
                contentType(ContentType.Application.Json)
                apiToken?.let {
                    header("Authorization", "Bearer $it")
                }
                setBody(request)
            }

            val responseTime = System.currentTimeMillis() - startTime

            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                AppLogger.error("HuggingFaceAPI", "Ошибка ${response.status}: $errorBody")
                return ModelComparisonResult(
                    model = model,
                    response = "",
                    responseTimeMs = responseTime,
                    inputTokens = 0,
                    outputTokens = 0,
                    totalCost = null,
                    error = "HTTP ${response.status.value}: $errorBody"
                )
            }

            val chatResponse = response.body<HFChatResponse>()

            val responseContent = chatResponse.choices.firstOrNull()?.message?.content ?: ""
            val usage = chatResponse.usage

            val inputTokens = usage?.promptTokens ?: estimateTokens(prompt)
            val outputTokens = usage?.completionTokens ?: estimateTokens(responseContent)

            // Рассчитываем стоимость
            val totalCost = model.pricing?.let { pricing ->
                (inputTokens / 1000.0 * pricing.inputPer1kTokens) +
                        (outputTokens / 1000.0 * pricing.outputPer1kTokens)
            }

            AppLogger.info("HuggingFaceAPI", "Ответ от ${model.name}: ${responseTime}ms, $inputTokens+$outputTokens токенов")

            ModelComparisonResult(
                model = model,
                response = responseContent,
                responseTimeMs = responseTime,
                inputTokens = inputTokens,
                outputTokens = outputTokens,
                totalCost = totalCost
            )

        } catch (e: Exception) {
            val responseTime = System.currentTimeMillis() - startTime
            AppLogger.error("HuggingFaceAPI", "Ошибка запроса к ${model.name}: ${e.message}")

            ModelComparisonResult(
                model = model,
                response = "",
                responseTimeMs = responseTime,
                inputTokens = 0,
                outputTokens = 0,
                totalCost = null,
                error = e.message ?: "Неизвестная ошибка"
            )
        }
    }

    /**
     * Приблизительная оценка количества токенов.
     * Примерно 4 символа = 1 токен для английского текста,
     * для русского примерно 2-3 символа = 1 токен.
     */
    private fun estimateTokens(text: String): Int {
        return (text.length / 3.0).toInt().coerceAtLeast(1)
    }

    fun close() {
        client.close()
    }
}
