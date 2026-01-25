package org.example.analytics.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.example.analytics.agent.*
import org.example.analytics.model.*
import org.example.analytics.parser.DataParserFactory
import org.example.logging.AppLogger
import java.io.File
import java.util.*

/**
 * ViewModel для экрана аналитики данных.
 */
class AnalyticsViewModel {

    private val viewModelScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val dataParserFactory = DataParserFactory()
    private val analyticsService = AnalyticsService(
        ollamaClient = DesktopOllamaAnalyticsClient()
    )

    var state by mutableStateOf(AnalyticsState())
        private set

    private val _streamingResponse = MutableSharedFlow<AnalyticsStreamChunk>()
    val streamingResponse: SharedFlow<AnalyticsStreamChunk> = _streamingResponse.asSharedFlow()

    /**
     * Загружает файл для анализа.
     */
    fun loadDataFile(file: File) {
        viewModelScope.launch {
            try {
                state = state.copy(isLoading = true, error = null)
                AppLogger.info("Analytics", "Загружаем файл: ${file.name}")

                val content = file.readText()
                val parsedData = dataParserFactory.parseFile(file.name, content)

                val updatedFiles = state.loadedFiles + parsedData

                state = state.copy(
                    loadedFiles = updatedFiles,
                    isLoading = false
                )

                AppLogger.info("Analytics", "Файл загружен: ${parsedData.totalRows} строк")

            } catch (e: Exception) {
                AppLogger.error("Analytics", "Ошибка загрузки файла: ${e.message}")
                state = state.copy(
                    isLoading = false,
                    error = "Ошибка загрузки файла: ${e.message}"
                )
            }
        }
    }

    /**
     * Удаляет загруженный файл.
     */
    fun removeDataFile(fileName: String) {
        val updatedFiles = state.loadedFiles.filter { it.fileName != fileName }
        state = state.copy(loadedFiles = updatedFiles)
    }

    /**
     * Очищает все загруженные файлы.
     */
    fun clearAllFiles() {
        state = state.copy(loadedFiles = emptyList())
        analyticsService.clearHistory()
    }

    /**
     * Выполняет анализ данных.
     */
    fun performAnalysis(question: String, useStreaming: Boolean = true) {
        if (question.isBlank()) {
            state = state.copy(error = "Введите вопрос для анализа")
            return
        }

        if (state.loadedFiles.isEmpty()) {
            state = state.copy(error = "Загрузите данные для анализа")
            return
        }

        val query = AnalyticsQuery(
            id = UUID.randomUUID().toString(),
            question = question,
            dataFiles = state.loadedFiles.map { it.fileName }
        )

        state = state.copy(
            isLoading = true,
            currentQuery = query,
            error = null
        )

        if (useStreaming) {
            performStreamingAnalysis(query)
        } else {
            performSyncAnalysis(query)
        }
    }

    /**
     * Выполняет анализ с готовым вопросом.
     */
    fun performSuggestedAnalysis(suggestion: SuggestedQuestion) {
        performAnalysis(suggestion.prompt, useStreaming = true)
    }

    /**
     * Получает предлагаемые вопросы.
     */
    fun getSuggestedQuestions(): List<SuggestedQuestion> {
        return if (state.loadedFiles.isNotEmpty()) {
            analyticsService.getSuggestedQuestions(state.loadedFiles)
        } else {
            emptyList()
        }
    }

    /**
     * Получает историю запросов.
     */
    fun getQueryHistory(): List<AnalyticsResult> {
        return analyticsService.getQueryHistory()
    }

    /**
     * Очищает историю запросов.
     */
    fun clearHistory() {
        analyticsService.clearHistory()
        state = state.copy(queryHistory = emptyList())
    }

    /**
     * Изменяет выбранную модель.
     */
    fun selectModel(modelId: String) {
        state = state.copy(selectedModel = modelId)
    }

    /**
     * Проверяет доступность модели.
     */
    fun checkModelAvailability() {
        viewModelScope.launch {
            try {
                val isAvailable = analyticsService.checkModelAvailability(state.selectedModel)
                if (!isAvailable) {
                    state = state.copy(
                        error = "Модель ${state.selectedModel} недоступна. Проверьте, что Ollama запущен."
                    )
                }
            } catch (e: Exception) {
                state = state.copy(
                    error = "Не удалось проверить доступность модели: ${e.message}"
                )
            }
        }
    }

    /**
     * Сбрасывает ошибку.
     */
    fun clearError() {
        state = state.copy(error = null)
    }

    private fun performStreamingAnalysis(query: AnalyticsQuery) {
        viewModelScope.launch {
            try {
                val responseBuilder = StringBuilder()

                analyticsService.performAnalysisStream(query, state.loadedFiles)
                    .collect { chunk ->
                        _streamingResponse.emit(chunk)

                        responseBuilder.append(chunk.content)

                        if (chunk.isComplete) {
                            val result = AnalyticsResult(
                                queryId = query.id,
                                question = query.question,
                                answer = responseBuilder.toString(),
                                insights = chunk.insights
                            )

                            val updatedHistory = state.queryHistory + result

                            state = state.copy(
                                isLoading = false,
                                currentQuery = null,
                                queryHistory = updatedHistory
                            )

                            AppLogger.info("Analytics", "Анализ завершен (поток)")
                        }
                    }

            } catch (e: Exception) {
                AppLogger.error("Analytics", "Ошибка потокового анализа: ${e.message}")
                state = state.copy(
                    isLoading = false,
                    currentQuery = null,
                    error = "Ошибка анализа: ${e.message}"
                )
            }
        }
    }

    private fun performSyncAnalysis(query: AnalyticsQuery) {
        viewModelScope.launch {
            try {
                val result = analyticsService.performAnalysis(query, state.loadedFiles)
                val updatedHistory = state.queryHistory + result

                state = state.copy(
                    isLoading = false,
                    currentQuery = null,
                    queryHistory = updatedHistory
                )

                AppLogger.info("Analytics", "Анализ завершен за ${result.executionTimeMs}ms")

            } catch (e: Exception) {
                AppLogger.error("Analytics", "Ошибка анализа: ${e.message}")
                state = state.copy(
                    isLoading = false,
                    currentQuery = null,
                    error = "Ошибка анализа: ${e.message}"
                )
            }
        }
    }

    fun onDispose() {
        analyticsService.clearHistory()
        viewModelScope.cancel()
    }
}