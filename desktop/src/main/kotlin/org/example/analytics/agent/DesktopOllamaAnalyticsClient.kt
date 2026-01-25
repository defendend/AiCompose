package org.example.analytics.agent

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import org.example.analytics.model.*
import org.example.model.*
import org.example.logging.AppLogger
import java.util.*

/**
 * Имплементация аналитического клиента Ollama для Desktop.
 */
class DesktopOllamaAnalyticsClient(
    private val baseUrl: String = "http://localhost:11434",
    private val defaultModel: String = "qwen2.5:1.5b"
) : AnalyticsOllamaClient {

    private val analysisAgent = DataAnalysisAgent()

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
            requestTimeoutMillis = 180000  // 3 минуты для аналитики
            connectTimeoutMillis = 5000
            socketTimeoutMillis = 180000
        }
    }

    override suspend fun analyzeData(
        query: AnalyticsQuery,
        dataFiles: List<ParsedData>
    ): AnalyticsResult {
        val startTime = System.currentTimeMillis()

        return try {
            AppLogger.info("AnalyticsOllama", "Начинаем анализ: ${query.question}")

            val prompt = analysisAgent.createAnalyticsPrompt(query, dataFiles)
            val response = sendRequestToOllama(prompt)

            val executionTime = System.currentTimeMillis() - startTime

            val insights = extractInsights(response)

            AnalyticsResult(
                queryId = query.id,
                question = query.question,
                answer = response,
                insights = insights,
                executionTimeMs = executionTime,
                timestamp = System.currentTimeMillis()
            )

        } catch (e: Exception) {
            val executionTime = System.currentTimeMillis() - startTime
            AppLogger.error("AnalyticsOllama", "Ошибка анализа: ${e.message}")

            AnalyticsResult(
                queryId = query.id,
                question = query.question,
                answer = "❌ Не удалось выполнить анализ: ${e.message}",
                insights = listOf("Проверьте подключение к Ollama"),
                executionTimeMs = executionTime
            )
        }
    }

    override suspend fun isModelAvailable(modelId: String): Boolean {
        return try {
            val models = getAvailableModels()
            models.any { it.startsWith(modelId) || modelId.startsWith(it.substringBefore(":")) }
        } catch (e: Exception) {
            AppLogger.warning("AnalyticsOllama", "Не удалось проверить модель $modelId: ${e.message}")
            false
        }
    }

    override suspend fun getAvailableModels(): List<String> {
        return try {
            val response = client.get("$baseUrl/api/tags")
            if (response.status.isSuccess()) {
                val tagsResponse = response.body<OllamaBenchmarkTagsResponse>()
                tagsResponse.models.map { it.name }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            AppLogger.error("AnalyticsOllama", "Ошибка получения моделей: ${e.message}")
            emptyList()
        }
    }

    override fun analyzeDataStream(
        query: AnalyticsQuery,
        dataFiles: List<ParsedData>
    ): Flow<AnalyticsStreamChunk> = flow {
        try {
            val prompt = analysisAgent.createAnalyticsPrompt(query, dataFiles)

            val request = OllamaGenerateRequest(
                model = defaultModel,
                prompt = prompt,
                stream = true,
                options = OllamaOptions(
                    temperature = 0.3f,  // Меньше креативности для аналитики
                    numPredict = 2000,
                    numCtx = 4096
                )
            )

            val response = client.post("$baseUrl/api/generate") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            if (response.status.isSuccess()) {
                val responseText = response.bodyAsText()
                val lines = responseText.trim().lines().filter { it.isNotBlank() }

                val contentBuilder = StringBuilder()

                for (line in lines) {
                    try {
                        val parsed = jsonParser.decodeFromString<OllamaGenerateResponse>(line)
                        contentBuilder.append(parsed.response)

                        emit(
                            AnalyticsStreamChunk(
                                queryId = query.id,
                                content = parsed.response,
                                isComplete = parsed.done
                            )
                        )

                        if (parsed.done) {
                            val finalContent = contentBuilder.toString()
                            val insights = extractInsights(finalContent)

                            emit(
                                AnalyticsStreamChunk(
                                    queryId = query.id,
                                    content = "",
                                    isComplete = true,
                                    insights = insights
                                )
                            )
                            break
                        }
                    } catch (e: Exception) {
                        AppLogger.warning("AnalyticsOllama", "Не удалось распарсить строку потока: $line")
                    }
                }
            } else {
                emit(
                    AnalyticsStreamChunk(
                        queryId = query.id,
                        content = "❌ Ошибка запроса: ${response.status}",
                        isComplete = true
                    )
                )
            }

        } catch (e: Exception) {
            AppLogger.error("AnalyticsOllama", "Ошибка потокового анализа: ${e.message}")
            emit(
                AnalyticsStreamChunk(
                    queryId = query.id,
                    content = "❌ Ошибка анализа: ${e.message}",
                    isComplete = true
                )
            )
        }
    }

    private suspend fun sendRequestToOllama(prompt: String): String {
        val request = OllamaGenerateRequest(
            model = defaultModel,
            prompt = prompt,
            stream = false,
            options = OllamaOptions(
                temperature = 0.3f,  // Меньше креативности для точности
                numPredict = 2000,
                numCtx = 4096,
                topK = 40,
                topP = 0.9f,
                repeatPenalty = 1.1f
            )
        )

        val response = client.post("$baseUrl/api/generate") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            throw Exception("HTTP ${response.status.value}: $errorBody")
        }

        val responseText = response.bodyAsText()
        return parseNdjsonResponse(responseText).response
    }

    private fun parseNdjsonResponse(ndjson: String): OllamaGenerateResponse {
        val lines = ndjson.trim().lines().filter { it.isNotBlank() }

        if (lines.isEmpty()) {
            return OllamaGenerateResponse()
        }

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
                AppLogger.warning("AnalyticsOllama", "Не удалось распарсить строку NDJSON: $line")
            }
        }

        return lastResponse?.copy(response = responseParts.toString())
            ?: OllamaGenerateResponse(response = responseParts.toString(), done = true)
    }

    /**
     * Извлекает ключевые инсайты из ответа модели.
     */
    private fun extractInsights(response: String): List<String> {
        val insights = mutableListOf<String>()

        // Ищем числа и проценты
        val numbers = Regex("""(\d+(?:[.,]\d+)?%?)""").findAll(response)
        numbers.forEach { match ->
            val context = response.substring(
                maxOf(0, match.range.first - 30),
                minOf(response.length, match.range.last + 30)
            ).trim()
            if (context.length > 10) {
                insights.add("📊 $context")
            }
        }

        // Ищем предупреждения и проблемы
        val warnings = listOf("ошибк", "проблем", "внимани", "важно", "критическ", "высок")
        warnings.forEach { keyword ->
            val regex = Regex("([^.!?]*$keyword[^.!?]*[.!?])", RegexOption.IGNORE_CASE)
            regex.findAll(response).forEach { match ->
                insights.add("⚠️ ${match.value.trim()}")
            }
        }

        // Ищем рекомендации
        val recommendations = listOf("рекоменд", "следует", "стоит", "лучше", "можно")
        recommendations.forEach { keyword ->
            val regex = Regex("([^.!?]*$keyword[^.!?]*[.!?])", RegexOption.IGNORE_CASE)
            regex.findAll(response).forEach { match ->
                insights.add("💡 ${match.value.trim()}")
            }
        }

        return insights.distinct().take(5)
    }

    fun close() {
        client.close()
    }
}