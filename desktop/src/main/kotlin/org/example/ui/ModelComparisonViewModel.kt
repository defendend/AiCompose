package org.example.ui

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.example.demo.HuggingFaceTokenDemo
import org.example.logging.AppLogger
import org.example.model.*
import org.example.network.HuggingFaceApiClient

/**
 * ViewModel для экрана сравнения моделей
 */
class ModelComparisonViewModel(
    apiToken: String? = System.getenv("HF_TOKEN")
        ?: System.getProperty("hf.token")?.takeIf { it.isNotBlank() }
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val apiClient = HuggingFaceApiClient(apiToken)
    private val tokenDemo = HuggingFaceTokenDemo(apiClient)

    val hasApiToken: Boolean = apiToken != null

    private val _state = MutableStateFlow(ComparisonState())
    val state: StateFlow<ComparisonState> = _state.asStateFlow()

    private val _selectedModels = MutableStateFlow<Set<HuggingFaceModel>>(emptySet())
    val selectedModels: StateFlow<Set<HuggingFaceModel>> = _selectedModels.asStateFlow()

    private val _prompt = MutableStateFlow("Объясни что такое рекурсия простыми словами.")
    val prompt: StateFlow<String> = _prompt.asStateFlow()

    // Состояние для демо токенов
    private val _tokenDemoState = MutableStateFlow(TokenDemoState())
    val tokenDemoState: StateFlow<TokenDemoState> = _tokenDemoState.asStateFlow()

    fun setPrompt(newPrompt: String) {
        _prompt.value = newPrompt
    }

    fun toggleModelSelection(model: HuggingFaceModel) {
        val current = _selectedModels.value.toMutableSet()
        if (current.contains(model)) {
            current.remove(model)
        } else {
            current.add(model)
        }
        _selectedModels.value = current
    }

    fun selectAllInCategory(category: ModelCategory) {
        val current = _selectedModels.value.toMutableSet()
        val modelsInCategory = AvailableModels.getByCategory(category)
        current.addAll(modelsInCategory)
        _selectedModels.value = current
    }

    fun clearSelection() {
        _selectedModels.value = emptySet()
    }

    /**
     * Запустить сравнение выбранных моделей
     */
    fun runComparison() {
        val modelsToTest = _selectedModels.value.toList()
        val testPrompt = _prompt.value

        if (modelsToTest.isEmpty()) {
            AppLogger.warning("ModelComparison", "Не выбрано ни одной модели")
            return
        }

        if (testPrompt.isBlank()) {
            AppLogger.warning("ModelComparison", "Пустой промпт")
            return
        }

        _state.value = _state.value.copy(
            isRunning = true,
            prompt = testPrompt,
            results = emptyList(),
            selectedModels = modelsToTest
        )

        scope.launch {
            val results = mutableListOf<ModelComparisonResult>()

            // Запускаем запросы параллельно
            val deferredResults = modelsToTest.map { model ->
                async {
                    apiClient.sendRequest(
                        model = model,
                        prompt = testPrompt,
                        maxTokens = 512,
                        temperature = 0.7
                    )
                }
            }

            // Собираем результаты по мере готовности
            deferredResults.forEachIndexed { index, deferred ->
                try {
                    val result = deferred.await()
                    results.add(result)

                    // Обновляем состояние после каждого результата
                    _state.value = _state.value.copy(
                        results = results.toList()
                    )

                    AppLogger.info(
                        "ModelComparison",
                        "Получен результат ${index + 1}/${modelsToTest.size}: ${result.model.name}"
                    )
                } catch (e: Exception) {
                    AppLogger.error("ModelComparison", "Ошибка для модели ${modelsToTest[index].name}: ${e.message}")
                    results.add(
                        ModelComparisonResult(
                            model = modelsToTest[index],
                            response = "",
                            responseTimeMs = 0,
                            inputTokens = 0,
                            outputTokens = 0,
                            totalCost = null,
                            error = e.message
                        )
                    )
                    _state.value = _state.value.copy(results = results.toList())
                }
            }

            _state.value = _state.value.copy(isRunning = false)

            AppLogger.info("ModelComparison", "Сравнение завершено. Результатов: ${results.size}")
        }
    }

    fun cancelComparison() {
        scope.coroutineContext.cancelChildren()
        _state.value = _state.value.copy(isRunning = false)
    }

    fun clearResults() {
        _state.value = ComparisonState()
    }

    fun close() {
        scope.cancel()
        apiClient.close()
    }

    // === Демо сравнения токенов ===

    /**
     * Запустить демо сравнения токенов для выбранных моделей.
     */
    fun runTokenDemo(includeOverLimit: Boolean = false) {
        val modelsToTest = _selectedModels.value.toList().ifEmpty {
            // Если ничего не выбрано, берём ВСЕ доступные модели
            AvailableModels.allModels
        }

        val testTypes = buildList {
            add(HuggingFaceTokenDemo.TestType.SHORT)
            add(HuggingFaceTokenDemo.TestType.MEDIUM)
            add(HuggingFaceTokenDemo.TestType.LONG)
            if (includeOverLimit) {
                add(HuggingFaceTokenDemo.TestType.VERY_LONG)
                add(HuggingFaceTokenDemo.TestType.OVER_LIMIT)
            }
        }

        _tokenDemoState.value = TokenDemoState(isRunning = true, progress = "Запуск тестов...")

        scope.launch {
            try {
                AppLogger.info("TokenDemo", "Запуск демо для ${modelsToTest.size} моделей, ${testTypes.size} тестов")

                val summary = tokenDemo.runComparison(modelsToTest, testTypes)
                val formattedResults = tokenDemo.formatResults(summary)

                _tokenDemoState.value = TokenDemoState(
                    isRunning = false,
                    results = summary,
                    formattedOutput = formattedResults
                )

                AppLogger.info("TokenDemo", "Демо завершено. Стоимость: $${summary.totalCost}")

            } catch (e: Exception) {
                AppLogger.error("TokenDemo", "Ошибка демо: ${e.message}")
                _tokenDemoState.value = TokenDemoState(
                    isRunning = false,
                    error = e.message ?: "Неизвестная ошибка"
                )
            }
        }
    }

    fun clearTokenDemo() {
        _tokenDemoState.value = TokenDemoState()
    }
}

/**
 * Состояние демо сравнения токенов.
 */
data class TokenDemoState(
    val isRunning: Boolean = false,
    val progress: String = "",
    val results: HuggingFaceTokenDemo.TokenComparisonSummary? = null,
    val formattedOutput: String = "",
    val error: String? = null
)
