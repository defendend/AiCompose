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
import org.example.model.OllamaBenchmarkModel
import org.example.model.OllamaBenchmarkTagsResponse
import org.example.model.OllamaGenerateRequest
import org.example.model.OllamaGenerateResponse
import org.example.model.OllamaOptions
import org.example.model.OllamaTestConfig
import org.example.model.OllamaTestResult

/**
 * Клиент для Ollama API (бенчмарк версия).
 *
 * Ollama запускается локально на http://localhost:11434
 * Документация: https://github.com/ollama/ollama/blob/main/docs/api.md
 */
class OllamaBenchmarkClient(
    private val baseUrl: String = "http://localhost:11434"
) {
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
            connectTimeoutMillis = 5000
            socketTimeoutMillis = 120000
        }
    }

    /**
     * Проверить, запущен ли Ollama.
     */
    suspend fun isRunning(): Boolean {
        return try {
            val response = client.get("$baseUrl/api/tags")
            response.status.isSuccess()
        } catch (e: Exception) {
            AppLogger.warning("OllamaBenchmark", "Ollama не запущен: ${e.message}")
            false
        }
    }

    /**
     * Получить список установленных моделей.
     */
    suspend fun getInstalledModels(): List<String> {
        return try {
            val response = client.get("$baseUrl/api/tags")
            if (response.status.isSuccess()) {
                val tagsResponse = response.body<OllamaBenchmarkTagsResponse>()
                tagsResponse.models.map { it.name }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            AppLogger.error("OllamaBenchmark", "Ошибка получения моделей: ${e.message}")
            emptyList()
        }
    }

    /**
     * Проверить, установлена ли модель.
     */
    suspend fun isModelInstalled(modelId: String): Boolean {
        val installed = getInstalledModels()
        return installed.any { it.startsWith(modelId) || modelId.startsWith(it.substringBefore(":")) }
    }

    /**
     * Отправить запрос к модели и замерить метрики.
     */
    suspend fun sendRequest(
        model: OllamaBenchmarkModel,
        prompt: String,
        config: OllamaTestConfig
    ): OllamaTestResult {
        val startTime = System.currentTimeMillis()

        return try {
            AppLogger.info("OllamaBenchmark", "Запрос к модели ${model.id} (${config.name})")

            val request = OllamaGenerateRequest(
                model = model.id,
                prompt = prompt,
                stream = false,
                options = OllamaOptions(
                    temperature = config.temperature,
                    numPredict = config.maxTokens,
                    numCtx = config.numCtx,
                    topK = 40,
                    topP = 0.9f,
                    repeatPenalty = 1.1f
                )
            )

            val response = client.post("$baseUrl/api/generate") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            val responseTime = System.currentTimeMillis() - startTime

            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                AppLogger.error("OllamaBenchmark", "Ошибка ${response.status}: $errorBody")
                return OllamaTestResult(
                    model = model,
                    config = config,
                    prompt = prompt,
                    response = "",
                    responseTimeMs = responseTime,
                    responseLength = 0,
                    estimatedTokens = 0,
                    tokensPerSecond = 0f,
                    error = "HTTP ${response.status.value}: $errorBody"
                )
            }

            // Ollama возвращает application/x-ndjson — несколько JSON объектов по строкам
            // Нужно объединить все части ответа
            val responseText = response.bodyAsText()
            val generateResponse = parseNdjsonResponse(responseText)

            val responseContent = generateResponse.response
            val responseLength = responseContent.length
            // Примерная оценка: 4 символа = 1 токен для английского, 2-3 для русского
            val estimatedTokens = (responseLength / 3.0).toInt().coerceAtLeast(1)
            val tokensPerSecond = if (responseTime > 0) {
                (estimatedTokens * 1000f / responseTime)
            } else 0f

            AppLogger.info(
                "OllamaBenchmark",
                "Ответ от ${model.name}: ${responseTime}ms, ~$estimatedTokens токенов, ${String.format("%.1f", tokensPerSecond)} т/с"
            )

            OllamaTestResult(
                model = model,
                config = config,
                prompt = prompt,
                response = responseContent,
                responseTimeMs = responseTime,
                responseLength = responseLength,
                estimatedTokens = estimatedTokens,
                tokensPerSecond = tokensPerSecond
            )

        } catch (e: Exception) {
            val responseTime = System.currentTimeMillis() - startTime
            AppLogger.error("OllamaBenchmark", "Ошибка запроса к ${model.name}: ${e.message}")

            OllamaTestResult(
                model = model,
                config = config,
                prompt = prompt,
                response = "",
                responseTimeMs = responseTime,
                responseLength = 0,
                estimatedTokens = 0,
                tokensPerSecond = 0f,
                error = e.message ?: "Неизвестная ошибка"
            )
        }
    }

    /**
     * Парсит NDJSON ответ от Ollama.
     * Ollama возвращает несколько JSON объектов, по одному на строку.
     * Нужно объединить все "response" поля и взять метаданные из последней строки.
     */
    private fun parseNdjsonResponse(ndjson: String): OllamaGenerateResponse {
        val lines = ndjson.trim().lines().filter { it.isNotBlank() }

        if (lines.isEmpty()) {
            return OllamaGenerateResponse()
        }

        // Собираем все части ответа
        val responseParts = StringBuilder()
        var lastResponse: OllamaGenerateResponse? = null

        for (line in lines) {
            try {
                val parsed = jsonParser.decodeFromString<OllamaGenerateResponse>(line)
                responseParts.append(parsed.response)
                if (parsed.done) {
                    lastResponse = parsed
                }
            } catch (e: Exception) {
                AppLogger.warning("OllamaBenchmark", "Не удалось распарсить строку NDJSON: $line")
            }
        }

        // Возвращаем объединённый ответ с метаданными из последней строки
        return lastResponse?.copy(response = responseParts.toString())
            ?: OllamaGenerateResponse(response = responseParts.toString(), done = true)
    }

    fun close() {
        client.close()
    }
}
