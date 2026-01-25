package org.example.analytics.agent

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.example.analytics.model.*

/**
 * Заглушка для JVM версии аналитического клиента.
 * Реальная реализация находится в desktop модуле.
 */
actual class OllamaAnalyticsClientImpl : AnalyticsOllamaClient {

    override suspend fun analyzeData(query: AnalyticsQuery, dataFiles: List<ParsedData>): AnalyticsResult {
        return AnalyticsResult(
            queryId = query.id,
            question = query.question,
            answer = "Заглушка: реализация доступна только в desktop версии",
            insights = listOf("Используйте desktop приложение для полной функциональности"),
            executionTimeMs = 0
        )
    }

    override suspend fun isModelAvailable(modelId: String): Boolean {
        return false
    }

    override suspend fun getAvailableModels(): List<String> {
        return emptyList()
    }

    override fun analyzeDataStream(query: AnalyticsQuery, dataFiles: List<ParsedData>): Flow<AnalyticsStreamChunk> = flow {
        emit(AnalyticsStreamChunk(
            queryId = query.id,
            content = "Заглушка: потоковый анализ доступен только в desktop версии",
            isComplete = true,
            insights = listOf("Используйте desktop приложение")
        ))
    }
}