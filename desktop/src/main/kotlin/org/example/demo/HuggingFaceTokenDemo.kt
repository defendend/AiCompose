package org.example.demo

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.example.logging.AppLogger
import org.example.model.AvailableModels
import org.example.model.HuggingFaceModel
import org.example.model.ModelComparisonResult
import org.example.network.HuggingFaceApiClient

/**
 * Демонстрация подсчёта токенов для HuggingFace моделей.
 *
 * Сравнивает поведение моделей с разными размерами запросов:
 * - Короткий запрос (~20 токенов)
 * - Средний запрос (~200 токенов)
 * - Длинный запрос (~2000 токенов)
 * - Очень длинный запрос (~5000 токенов)
 * - Превышающий лимит (~50000+ токенов)
 *
 * Лимиты контекста моделей (примерные):
 * - Llama 3.x: 8K-128K токенов
 * - Qwen 2.5: 32K-128K токенов
 * - Gemma 2: 8K токенов
 * - DeepSeek V3: 64K токенов
 */
class HuggingFaceTokenDemo(
    private val apiClient: HuggingFaceApiClient
) {
    companion object {
        // Примерные лимиты контекста для разных моделей
        val MODEL_CONTEXT_LIMITS = mapOf(
            "Llama 3.1 8B" to 128_000,
            "Llama 3.2 1B" to 128_000,
            "Llama 3.2 3B" to 128_000,
            "Llama 3.3 70B" to 128_000,
            "Qwen 2.5 7B" to 32_000,
            "Qwen 2.5 72B" to 32_000,
            "Gemma 2 2B" to 8_000,
            "DeepSeek V3" to 64_000
        )

        // Средняя оценка: ~4 символа на токен (смешанный текст)
        // Русский текст ~2 символа/токен, английский ~4
        const val CHARS_PER_TOKEN = 4
    }

    /**
     * Результат теста токенов для одной модели.
     */
    data class TokenTestResult(
        val model: HuggingFaceModel,
        val testType: TestType,
        val inputChars: Int,
        val estimatedTokens: Int,
        val actualPromptTokens: Int?,
        val actualCompletionTokens: Int?,
        val actualTotalTokens: Int?,
        val responseTimeMs: Long,
        val success: Boolean,
        val error: String? = null,
        val response: String? = null,
        val fullResponse: String? = null,  // Полный ответ
        val cost: Double? = null,
        val tokensPerSecond: Double? = null  // Скорость генерации
    )

    enum class TestType(val label: String, val description: String) {
        SHORT("Короткий", "~20 токенов"),
        MEDIUM("Средний", "~200 токенов"),
        LONG("Длинный", "~2000 токенов"),
        VERY_LONG("Очень длинный", "~5000 токенов"),
        OVER_LIMIT("Превышающий лимит", "~50000+ токенов")
    }

    /**
     * Сводка сравнения токенов.
     */
    data class TokenComparisonSummary(
        val modelResults: Map<HuggingFaceModel, List<TokenTestResult>>,
        val insights: List<String>,
        val totalCost: Double
    )

    /**
     * Оценить количество токенов в тексте.
     */
    fun estimateTokens(text: String): Int {
        return (text.length / CHARS_PER_TOKEN).coerceAtLeast(1)
    }

    /**
     * Сгенерировать тестовые промпты разной длины.
     */
    fun generateTestPrompts(): Map<TestType, String> {
        return mapOf(
            TestType.SHORT to "Привет! Как дела?",

            TestType.MEDIUM to """
                Расскажи мне про историю создания языка программирования Kotlin.
                Кто его создал, когда это произошло, какие были основные цели?
                Почему JetBrains решили создать новый язык вместо использования существующих?
                Какие преимущества Kotlin имеет перед Java?
                Как Kotlin стал официальным языком для Android разработки?
            """.trimIndent(),

            TestType.LONG to buildString {
                appendLine("Проанализируй следующий текст и дай краткое резюме в 2-3 предложениях:")
                appendLine()
                repeat(30) { i ->
                    appendLine("Параграф ${i + 1}: История развития компьютерных технологий показывает, что каждое десятилетие приносит новые революционные изменения. " +
                        "От первых ламповых компьютеров до современных квантовых вычислений — путь был долгим и увлекательным. " +
                        "Развитие программирования прошло через множество этапов: от машинного кода до высокоуровневых языков.")
                }
            },

            TestType.VERY_LONG to buildString {
                appendLine("Это тест на очень длинный контекст. Ответь одним предложением о чём этот текст:")
                appendLine()
                repeat(80) { i ->
                    appendLine("Секция ${i + 1}: Искусственный интеллект и машинное обучение становятся неотъемлемой частью современного мира. " +
                        "Нейронные сети способны решать задачи, которые ещё недавно казались невозможными для автоматизации. " +
                        "Обработка естественного языка позволяет компьютерам понимать и генерировать человеческую речь. " +
                        "Компьютерное зрение открывает новые возможности в медицине и автомобилестроении.")
                }
            },

            TestType.OVER_LIMIT to buildString {
                appendLine("Тест на превышение лимита контекста. Ответь 'OK' если получил сообщение:")
                appendLine()
                repeat(800) { i ->
                    appendLine("Блок $i: " + "Тестовый текст для проверки превышения лимита токенов модели. ".repeat(30))
                }
            }
        )
    }

    /**
     * Запустить тест для одной модели с одним промптом.
     */
    suspend fun runSingleTest(
        model: HuggingFaceModel,
        testType: TestType,
        prompt: String
    ): TokenTestResult {
        val estimatedTokens = estimateTokens(prompt)

        AppLogger.info("HFTokenDemo", "Тест ${testType.label} для ${model.name}: ~$estimatedTokens токенов")

        val result = apiClient.sendRequest(
            model = model,
            prompt = prompt,
            maxTokens = 512,  // Больше токенов для полных ответов
            temperature = 0.7
        )

        // Расчёт скорости генерации токенов
        val tokensPerSec = if (result.error == null && result.outputTokens > 0 && result.responseTimeMs > 0) {
            result.outputTokens.toDouble() / (result.responseTimeMs / 1000.0)
        } else null

        return TokenTestResult(
            model = model,
            testType = testType,
            inputChars = prompt.length,
            estimatedTokens = estimatedTokens,
            actualPromptTokens = if (result.error == null) result.inputTokens else null,
            actualCompletionTokens = if (result.error == null) result.outputTokens else null,
            actualTotalTokens = if (result.error == null) result.inputTokens + result.outputTokens else null,
            responseTimeMs = result.responseTimeMs,
            success = result.error == null,
            error = result.error,
            response = result.response.take(200),
            fullResponse = result.response,  // Полный ответ
            cost = result.totalCost,
            tokensPerSecond = tokensPerSec
        )
    }

    /**
     * Запустить полное сравнение для выбранных моделей.
     */
    suspend fun runComparison(
        models: List<HuggingFaceModel> = listOf(
            AvailableModels.middleModels.first(),  // Llama 3.1 8B - быстрая
            AvailableModels.smallModels.first()    // Gemma 2 2B - компактная
        ),
        testTypes: List<TestType> = listOf(
            TestType.SHORT,
            TestType.MEDIUM,
            TestType.LONG
        )
    ): TokenComparisonSummary = coroutineScope {
        val prompts = generateTestPrompts()
        val allResults = mutableMapOf<HuggingFaceModel, MutableList<TokenTestResult>>()
        var totalCost = 0.0

        // Запускаем тесты последовательно по типам, параллельно по моделям
        for (testType in testTypes) {
            val prompt = prompts[testType] ?: continue

            AppLogger.info("HFTokenDemo", "=== Тест: ${testType.label} (${testType.description}) ===")

            val deferredResults = models.map { model ->
                async {
                    runSingleTest(model, testType, prompt)
                }
            }

            val results = deferredResults.awaitAll()

            results.forEach { result ->
                allResults.getOrPut(result.model) { mutableListOf() }.add(result)
                result.cost?.let { totalCost += it }
            }
        }

        // Формируем выводы о поведении моделей
        val insights = mutableListOf<String>()

        insights.add("═══ АНАЛИЗ ПОВЕДЕНИЯ МОДЕЛЕЙ ═══")

        // Анализ по каждой модели
        allResults.forEach { (model, results) ->
            insights.add("")
            insights.add("📊 ${model.name} (${model.parameters}):")

            val successfulTests = results.filter { it.success }
            val failedTests = results.filter { !it.success }

            if (successfulTests.isNotEmpty()) {
                // Как меняется время с увеличением токенов
                val sortedByTokens = successfulTests.sortedBy { it.actualPromptTokens ?: 0 }
                val shortTest = sortedByTokens.firstOrNull()
                val longTest = sortedByTokens.lastOrNull()

                if (shortTest != null && longTest != null && shortTest != longTest) {
                    val timeIncrease = longTest.responseTimeMs.toDouble() / shortTest.responseTimeMs
                    val tokenIncrease = (longTest.actualPromptTokens ?: 1).toDouble() / (shortTest.actualPromptTokens ?: 1)
                    insights.add("  • Время: ${shortTest.responseTimeMs}ms → ${longTest.responseTimeMs}ms (x${"%.1f".format(timeIncrease)})")
                    insights.add("  • Токены: ${shortTest.actualPromptTokens} → ${longTest.actualPromptTokens} (x${"%.1f".format(tokenIncrease)})")

                    // Вывод о линейности
                    if (timeIncrease < tokenIncrease * 0.5) {
                        insights.add("  ✅ Отличная масштабируемость! Время растёт медленнее токенов")
                    } else if (timeIncrease < tokenIncrease * 1.5) {
                        insights.add("  ✅ Хорошая линейная масштабируемость")
                    } else {
                        insights.add("  ⚠️ Время растёт быстрее количества токенов")
                    }
                }

                // Средняя скорость генерации
                val avgSpeed = successfulTests.mapNotNull { it.tokensPerSecond }.average()
                if (!avgSpeed.isNaN()) {
                    insights.add("  • Средняя скорость: ${"%.1f".format(avgSpeed)} токенов/сек")
                }
            }

            // Ошибки
            failedTests.forEach { failed ->
                insights.add("  ❌ ${failed.testType.label}: ${failed.error?.take(60)}")
            }
        }

        // Сравнение моделей
        insights.add("")
        insights.add("═══ СРАВНЕНИЕ МОДЕЛЕЙ ═══")

        val avgSpeedByModel = allResults.mapValues { (_, results) ->
            results.filter { it.success }.map { it.responseTimeMs }.average()
        }.filter { !it.value.isNaN() }

        val fastestModel = avgSpeedByModel.minByOrNull { it.value }
        val slowestModel = avgSpeedByModel.maxByOrNull { it.value }

        fastestModel?.let {
            insights.add("🥇 Быстрейшая: ${it.key.name} (${it.value.toLong()}ms в среднем)")
        }
        slowestModel?.let {
            if (it.key != fastestModel?.key) {
                insights.add("🐢 Медленнейшая: ${it.key.name} (${it.value.toLong()}ms в среднем)")
            }
        }

        // Общая статистика
        insights.add("")
        insights.add("═══ СТАТИСТИКА ═══")
        val totalTests = allResults.values.flatten().size
        val successfulCount = allResults.values.flatten().count { it.success }
        val failedCount = totalTests - successfulCount
        insights.add("• Всего тестов: $totalTests")
        insights.add("• Успешных: $successfulCount")
        if (failedCount > 0) {
            insights.add("• Ошибок: $failedCount (превышение лимита или недоступность)")
        }
        insights.add("• Общая стоимость: $${"%.6f".format(totalCost)}")

        TokenComparisonSummary(
            modelResults = allResults,
            insights = insights,
            totalCost = totalCost
        )
    }

    /**
     * Форматировать результаты для вывода.
     */
    fun formatResults(summary: TokenComparisonSummary): String {
        return buildString {
            appendLine("=" .repeat(60))
            appendLine("  СРАВНЕНИЕ ТОКЕНОВ HUGGINGFACE МОДЕЛЕЙ")
            appendLine("=".repeat(60))
            appendLine()

            summary.modelResults.forEach { (model, results) ->
                appendLine("### ${model.name} (${model.parameters})")
                appendLine("-".repeat(40))

                results.forEach { result ->
                    appendLine("${result.testType.label}:")
                    appendLine("  Символов: ${result.inputChars}")
                    appendLine("  Оценка токенов: ${result.estimatedTokens}")
                    if (result.success) {
                        appendLine("  Реальных токенов: ${result.actualPromptTokens} вход + ${result.actualCompletionTokens} выход")
                        appendLine("  Время: ${result.responseTimeMs}ms")
                        appendLine("  Стоимость: $${"%.6f".format(result.cost ?: 0.0)}")
                    } else {
                        appendLine("  ОШИБКА: ${result.error}")
                    }
                    appendLine()
                }
                appendLine()
            }

            appendLine("=".repeat(60))
            appendLine("  ВЫВОДЫ")
            appendLine("=".repeat(60))
            summary.insights.forEach { insight ->
                appendLine("• $insight")
            }
        }
    }
}
