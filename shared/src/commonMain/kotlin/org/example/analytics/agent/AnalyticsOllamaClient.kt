package org.example.analytics.agent

import org.example.analytics.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable

/**
 * Интерфейс для работы с локальной моделью аналитики.
 */
interface AnalyticsOllamaClient {
    suspend fun analyzeData(query: AnalyticsQuery, dataFiles: List<ParsedData>): AnalyticsResult
    suspend fun isModelAvailable(modelId: String): Boolean
    suspend fun getAvailableModels(): List<String>
    fun analyzeDataStream(query: AnalyticsQuery, dataFiles: List<ParsedData>): Flow<AnalyticsStreamChunk>
}

/**
 * Реализация клиента для аналитики через Ollama.
 */
expect class OllamaAnalyticsClientImpl() : AnalyticsOllamaClient

/**
 * Чанк для потокового ответа аналитики.
 */
@Serializable
data class AnalyticsStreamChunk(
    val queryId: String,
    val content: String,
    val isComplete: Boolean = false,
    val insights: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Сервис аналитики с поддержкой кэширования и истории.
 */
class AnalyticsService(
    private val ollamaClient: AnalyticsOllamaClient,
    private val analysisAgent: DataAnalysisAgent = DataAnalysisAgent()
) {
    private val queryHistory = mutableListOf<AnalyticsResult>()
    private val resultCache = mutableMapOf<String, AnalyticsResult>()

    /**
     * Выполняет анализ данных.
     */
    suspend fun performAnalysis(
        query: AnalyticsQuery,
        dataFiles: List<ParsedData>
    ): AnalyticsResult {
        val cacheKey = createCacheKey(query, dataFiles)

        // Проверяем кэш
        resultCache[cacheKey]?.let { return it }

        val startTime = System.currentTimeMillis()

        return try {
            val result = ollamaClient.analyzeData(query, dataFiles)
            val executionTime = System.currentTimeMillis() - startTime

            val finalResult = result.copy(
                executionTimeMs = executionTime,
                timestamp = System.currentTimeMillis()
            )

            // Сохраняем в кэш и историю
            resultCache[cacheKey] = finalResult
            queryHistory.add(finalResult)

            finalResult
        } catch (e: Exception) {
            val errorResult = AnalyticsResult(
                queryId = query.id,
                question = query.question,
                answer = "❌ Ошибка анализа: ${e.message}",
                insights = listOf("Проверьте, что Ollama запущен и модель доступна"),
                executionTimeMs = System.currentTimeMillis() - startTime
            )

            queryHistory.add(errorResult)
            errorResult
        }
    }

    /**
     * Выполняет анализ с потоковым ответом.
     */
    fun performAnalysisStream(
        query: AnalyticsQuery,
        dataFiles: List<ParsedData>
    ): Flow<AnalyticsStreamChunk> = flow {
        try {
            ollamaClient.analyzeDataStream(query, dataFiles).collect { chunk ->
                emit(chunk)
            }
        } catch (e: Exception) {
            emit(
                AnalyticsStreamChunk(
                    queryId = query.id,
                    content = "❌ Ошибка анализа: ${e.message}",
                    isComplete = true,
                    insights = listOf("Проверьте, что Ollama запущен и модель доступна")
                )
            )
        }
    }

    /**
     * Получает предлагаемые вопросы.
     */
    fun getSuggestedQuestions(dataFiles: List<ParsedData>): List<SuggestedQuestion> {
        return analysisAgent.suggestQuestions(dataFiles)
    }

    /**
     * Получает историю запросов.
     */
    fun getQueryHistory(): List<AnalyticsResult> = queryHistory.toList()

    /**
     * Очищает историю и кэш.
     */
    fun clearHistory() {
        queryHistory.clear()
        resultCache.clear()
    }

    /**
     * Проверяет доступность модели.
     */
    suspend fun checkModelAvailability(modelId: String): Boolean {
        return try {
            ollamaClient.isModelAvailable(modelId)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Получает список доступных моделей.
     */
    suspend fun getAvailableModels(): List<String> {
        return try {
            ollamaClient.getAvailableModels()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun createCacheKey(query: AnalyticsQuery, dataFiles: List<ParsedData>): String {
        val dataKey = dataFiles.joinToString("|") { "${it.fileName}:${it.totalRows}" }
        return "${query.question}:$dataKey"
    }
}