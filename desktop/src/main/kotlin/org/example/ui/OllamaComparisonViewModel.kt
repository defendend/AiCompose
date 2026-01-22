package org.example.ui

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.example.logging.AppLogger
import org.example.model.*
import org.example.network.OllamaBenchmarkClient

/**
 * ViewModel для экрана бенчмарка Ollama.
 */
class OllamaComparisonViewModel {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val apiClient = OllamaBenchmarkClient()

    private val _isOllamaRunning = MutableStateFlow<Boolean?>(null)
    val isOllamaRunning: StateFlow<Boolean?> = _isOllamaRunning.asStateFlow()

    private val _installedModels = MutableStateFlow<List<String>>(emptyList())
    val installedModels: StateFlow<List<String>> = _installedModels.asStateFlow()

    private val _selectedModels = MutableStateFlow<Set<OllamaBenchmarkModel>>(emptySet())
    val selectedModels: StateFlow<Set<OllamaBenchmarkModel>> = _selectedModels.asStateFlow()

    private val _prompt = MutableStateFlow("Что такое Kotlin? Ответь кратко.")
    val prompt: StateFlow<String> = _prompt.asStateFlow()

    private val _benchmarkState = MutableStateFlow(OllamaBenchmarkState())
    val benchmarkState: StateFlow<OllamaBenchmarkState> = _benchmarkState.asStateFlow()

    private val _testType = MutableStateFlow(OllamaTestType.MODELS)
    val testType: StateFlow<OllamaTestType> = _testType.asStateFlow()

    init {
        checkOllamaStatus()
    }

    fun checkOllamaStatus() {
        scope.launch {
            _isOllamaRunning.value = null // Loading state
            val isRunning = apiClient.isRunning()
            _isOllamaRunning.value = isRunning

            if (isRunning) {
                _installedModels.value = apiClient.getInstalledModels()
                AppLogger.info("Ollama", "Установленные модели: ${_installedModels.value}")
            }
        }
    }

    fun setPrompt(newPrompt: String) {
        _prompt.value = newPrompt
    }

    fun setTestType(type: OllamaTestType) {
        _testType.value = type
    }

    fun toggleModelSelection(model: OllamaBenchmarkModel) {
        val current = _selectedModels.value.toMutableSet()
        if (current.contains(model)) {
            current.remove(model)
        } else {
            current.add(model)
        }
        _selectedModels.value = current
    }

    fun selectAllInCategory(category: OllamaBenchmarkCategory) {
        val current = _selectedModels.value.toMutableSet()
        val modelsInCategory = AvailableOllamaModels.getByCategory(category)
        current.addAll(modelsInCategory)
        _selectedModels.value = current
    }

    fun clearSelection() {
        _selectedModels.value = emptySet()
    }

    fun isModelInstalled(modelId: String): Boolean {
        return _installedModels.value.any {
            it.startsWith(modelId) || modelId.startsWith(it.substringBefore(":"))
        }
    }

    /**
     * Запустить бенчмарк.
     */
    fun runBenchmark() {
        val modelsToTest = _selectedModels.value.toList()
        val testPrompt = _prompt.value
        val currentTestType = _testType.value

        if (modelsToTest.isEmpty()) {
            AppLogger.warning("OllamaBenchmark", "Не выбрано ни одной модели")
            return
        }

        if (testPrompt.isBlank()) {
            AppLogger.warning("OllamaBenchmark", "Пустой промпт")
            return
        }

        // Определяем конфигурации для тестирования
        val configs = when (currentTestType) {
            OllamaTestType.MODELS -> listOf(OllamaTestConfigs.defaultConfig)
            OllamaTestType.TEMPERATURE -> OllamaTestConfigs.temperatureConfigs
            OllamaTestType.TOKENS -> OllamaTestConfigs.tokenConfigs
            OllamaTestType.CONTEXT -> OllamaTestConfigs.contextConfigs
            OllamaTestType.FULL -> OllamaTestConfigs.temperatureConfigs +
                    OllamaTestConfigs.tokenConfigs +
                    OllamaTestConfigs.contextConfigs
        }

        val totalTests = modelsToTest.size * configs.size

        _benchmarkState.value = OllamaBenchmarkState(
            isRunning = true,
            currentTest = "Запуск...",
            progress = 0,
            totalTests = totalTests,
            results = emptyList()
        )

        scope.launch {
            val results = mutableListOf<OllamaTestResult>()
            var completed = 0

            for (model in modelsToTest) {
                // Проверяем, установлена ли модель
                if (!isModelInstalled(model.id)) {
                    AppLogger.warning("OllamaBenchmark", "Модель ${model.id} не установлена, пропускаем")
                    configs.forEach { config ->
                        results.add(
                            OllamaTestResult(
                                model = model,
                                config = config,
                                prompt = testPrompt,
                                response = "",
                                responseTimeMs = 0,
                                responseLength = 0,
                                estimatedTokens = 0,
                                tokensPerSecond = 0f,
                                error = "Модель не установлена. Выполните: ollama pull ${model.id}"
                            )
                        )
                        completed++
                    }
                    _benchmarkState.value = _benchmarkState.value.copy(
                        progress = completed,
                        results = results.toList()
                    )
                    continue
                }

                for (config in configs) {
                    _benchmarkState.value = _benchmarkState.value.copy(
                        currentTest = "${model.name} (${config.name})"
                    )

                    val result = apiClient.sendRequest(model, testPrompt, config)
                    results.add(result)
                    completed++

                    _benchmarkState.value = _benchmarkState.value.copy(
                        progress = completed,
                        results = results.toList()
                    )

                    AppLogger.info(
                        "OllamaBenchmark",
                        "Тест $completed/$totalTests: ${model.name} (${config.name}) - ${result.responseTimeMs}ms"
                    )
                }
            }

            _benchmarkState.value = _benchmarkState.value.copy(
                isRunning = false,
                currentTest = "Завершено"
            )

            AppLogger.info("OllamaBenchmark", "Бенчмарк завершён. Результатов: ${results.size}")
        }
    }

    fun cancelBenchmark() {
        scope.coroutineContext.cancelChildren()
        _benchmarkState.value = _benchmarkState.value.copy(isRunning = false)
    }

    fun clearResults() {
        _benchmarkState.value = OllamaBenchmarkState()
    }

    fun close() {
        scope.cancel()
        apiClient.close()
    }
}

/**
 * Тип теста Ollama.
 */
enum class OllamaTestType(val label: String, val description: String) {
    MODELS("Сравнение моделей", "Тест всех выбранных моделей с базовыми параметрами"),
    TEMPERATURE("Температура", "Сравнение температуры: 0.1, 0.7, 1.2"),
    TOKENS("Длина ответа", "Сравнение max_tokens: 50, 200, 500"),
    CONTEXT("Размер контекста", "Сравнение num_ctx: 1K, 2K, 4K"),
    FULL("Полный бенчмарк", "Все тесты для каждой модели")
}
