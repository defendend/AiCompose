package org.example.demo

import kotlinx.serialization.Serializable
import org.example.data.LLMClient
import org.example.data.LLMApiException
import org.example.model.LLMMessage
import org.example.model.Usage

/**
 * Демонстрация подсчёта токенов и поведения модели с разными размерами запросов.
 *
 * DeepSeek API лимиты:
 * - Максимум контекста: 64K токенов (deepseek-chat)
 * - Максимум выходных токенов: 8K (по умолчанию 4K)
 */
class TokenCounterDemo(
    private val llmClient: LLMClient
) {
    companion object {
        // DeepSeek deepseek-chat лимиты
        const val MAX_CONTEXT_TOKENS = 64000
        const val MAX_OUTPUT_TOKENS = 8192
        const val DEFAULT_MAX_OUTPUT = 4096

        // Примерная оценка: 1 токен ≈ 4 символа для английского, ≈ 1-2 символа для русского
        const val AVG_CHARS_PER_TOKEN_EN = 4
        const val AVG_CHARS_PER_TOKEN_RU = 2
    }

    /**
     * Результат теста токенов.
     */
    @Serializable
    data class TokenTestResult(
        val testName: String,
        val inputLength: Int,
        val estimatedInputTokens: Int,
        val actualPromptTokens: Int?,
        val actualCompletionTokens: Int?,
        val actualTotalTokens: Int?,
        val durationMs: Long,
        val success: Boolean,
        val error: String? = null,
        val response: String? = null,
        val notes: String? = null
    )

    /**
     * Сводка сравнения токенов.
     */
    @Serializable
    data class TokenComparisonSummary(
        val results: List<TokenTestResult>,
        val modelLimits: ModelLimits,
        val insights: List<String>
    )

    @Serializable
    data class ModelLimits(
        val maxContextTokens: Int = MAX_CONTEXT_TOKENS,
        val maxOutputTokens: Int = MAX_OUTPUT_TOKENS,
        val defaultMaxOutput: Int = DEFAULT_MAX_OUTPUT
    )

    /**
     * Оценить количество токенов в тексте.
     * Простая эвристика, реальный подсчёт делает API.
     */
    fun estimateTokens(text: String): Int {
        // Проверяем, преимущественно русский или английский текст
        val cyrillicCount = text.count { it in '\u0400'..'\u04FF' }
        val latinCount = text.count { it in 'A'..'Z' || it in 'a'..'z' }

        val avgCharsPerToken = if (cyrillicCount > latinCount) {
            AVG_CHARS_PER_TOKEN_RU
        } else {
            AVG_CHARS_PER_TOKEN_EN
        }

        return (text.length / avgCharsPerToken).coerceAtLeast(1)
    }

    /**
     * Выполнить тест с заданным промптом.
     */
    suspend fun runTest(testName: String, prompt: String, notes: String? = null): TokenTestResult {
        val startTime = System.currentTimeMillis()
        val estimatedTokens = estimateTokens(prompt)

        return try {
            val response = llmClient.chat(
                messages = listOf(
                    LLMMessage(role = "user", content = prompt)
                ),
                tools = emptyList(),
                temperature = 0.7f,
                conversationId = "token-demo-$testName"
            )

            val duration = System.currentTimeMillis() - startTime
            val usage = response.usage
            val content = response.choices.firstOrNull()?.message?.content

            TokenTestResult(
                testName = testName,
                inputLength = prompt.length,
                estimatedInputTokens = estimatedTokens,
                actualPromptTokens = usage?.prompt_tokens,
                actualCompletionTokens = usage?.completion_tokens,
                actualTotalTokens = usage?.total_tokens,
                durationMs = duration,
                success = true,
                response = content?.take(500),
                notes = notes
            )
        } catch (e: LLMApiException) {
            val duration = System.currentTimeMillis() - startTime
            TokenTestResult(
                testName = testName,
                inputLength = prompt.length,
                estimatedInputTokens = estimatedTokens,
                actualPromptTokens = null,
                actualCompletionTokens = null,
                actualTotalTokens = null,
                durationMs = duration,
                success = false,
                error = "API Error ${e.statusCode}: ${e.message}",
                notes = notes
            )
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            TokenTestResult(
                testName = testName,
                inputLength = prompt.length,
                estimatedInputTokens = estimatedTokens,
                actualPromptTokens = null,
                actualCompletionTokens = null,
                actualTotalTokens = null,
                durationMs = duration,
                success = false,
                error = e.message,
                notes = notes
            )
        }
    }

    /**
     * Запустить сравнение: короткий, средний, длинный и превышающий лимит запросы.
     */
    suspend fun runComparison(): TokenComparisonSummary {
        val results = mutableListOf<TokenTestResult>()

        // 1. Короткий запрос (~10-20 токенов)
        results.add(
            runTest(
                testName = "short",
                prompt = "Привет! Как дела?",
                notes = "Короткий запрос ~10 токенов"
            )
        )

        // 2. Средний запрос (~100-200 токенов)
        results.add(
            runTest(
                testName = "medium",
                prompt = """
                    Расскажи мне про историю создания языка программирования Kotlin.
                    Кто его создал, когда это произошло, какие были основные цели?
                    Почему JetBrains решили создать новый язык вместо использования существующих?
                    Какие преимущества Kotlin имеет перед Java?
                """.trimIndent(),
                notes = "Средний запрос ~100 токенов"
            )
        )

        // 3. Длинный запрос (~1000-2000 токенов)
        val longContext = buildString {
            appendLine("Проанализируй следующий текст и дай краткое резюме:")
            appendLine()
            repeat(20) { i ->
                appendLine("Параграф ${i + 1}: История развития компьютерных технологий показывает, что каждое десятилетие приносит новые революционные изменения. " +
                        "От первых ламповых компьютеров до современных квантовых вычислений — путь был долгим и увлекательным. " +
                        "Развитие программирования прошло через множество этапов: от машинного кода до высокоуровневых языков, " +
                        "от процедурного программирования до объектно-ориентированного и функционального подходов.")
            }
        }
        results.add(
            runTest(
                testName = "long",
                prompt = longContext,
                notes = "Длинный запрос ~2000 токенов"
            )
        )

        // 4. Очень длинный запрос (~10000 токенов)
        val veryLongContext = buildString {
            appendLine("Это тест на очень длинный контекст. Проанализируй весь текст и выдели ключевые темы:")
            appendLine()
            repeat(100) { i ->
                appendLine("Секция ${i + 1}: Искусственный интеллект и машинное обучение становятся неотъемлемой частью современного мира. " +
                        "Нейронные сети способны решать задачи, которые ещё недавно казались невозможными для автоматизации. " +
                        "Обработка естественного языка позволяет компьютерам понимать и генерировать человеческую речь. " +
                        "Компьютерное зрение открывает новые возможности в медицине, автомобилестроении и безопасности. " +
                        "Рекомендательные системы помогают пользователям находить релевантный контент среди миллионов вариантов.")
            }
        }
        results.add(
            runTest(
                testName = "very_long",
                prompt = veryLongContext,
                notes = "Очень длинный запрос ~10000 токенов"
            )
        )

        // 5. Запрос, приближающийся к лимиту (~50000 токенов)
        val nearLimitContext = buildString {
            appendLine("Тест на приближение к лимиту контекста. Ответь одним словом 'ОК' если получил сообщение:")
            appendLine()
            repeat(500) { i ->
                appendLine("Блок $i: " + "Lorem ipsum dolor sit amet, consectetur adipiscing elit. ".repeat(20))
            }
        }
        results.add(
            runTest(
                testName = "near_limit",
                prompt = nearLimitContext,
                notes = "Запрос близкий к лимиту ~50000 токенов"
            )
        )

        // 6. Запрос, превышающий лимит (~100000+ токенов)
        val overLimitContext = buildString {
            appendLine("Тест на превышение лимита. Этот запрос должен вернуть ошибку:")
            appendLine()
            repeat(2000) { i ->
                appendLine("Блок $i: " + "Тестовый текст для проверки превышения лимита токенов модели. ".repeat(30))
            }
        }
        results.add(
            runTest(
                testName = "over_limit",
                prompt = overLimitContext,
                notes = "Запрос превышающий лимит ~100000+ токенов (ожидается ошибка)"
            )
        )

        // Формируем выводы
        val insights = mutableListOf<String>()

        // Анализируем результаты
        val successfulTests = results.filter { it.success }
        val failedTests = results.filter { !it.success }

        if (successfulTests.isNotEmpty()) {
            val avgTokensPerChar = successfulTests
                .filter { it.actualPromptTokens != null && it.actualPromptTokens > 0 }
                .map { it.inputLength.toDouble() / it.actualPromptTokens!! }
                .average()

            insights.add("Среднее соотношение символов к токенам: %.2f символов/токен".format(avgTokensPerChar))

            val avgMsPerToken = successfulTests
                .filter { it.actualTotalTokens != null && it.actualTotalTokens > 0 }
                .map { it.durationMs.toDouble() / it.actualTotalTokens!! }
                .average()

            insights.add("Средняя скорость: %.2f мс/токен".format(avgMsPerToken))
        }

        if (failedTests.isNotEmpty()) {
            insights.add("Неуспешных тестов: ${failedTests.size}")
            failedTests.forEach { test ->
                insights.add("  - ${test.testName}: ${test.error}")
            }
        }

        // Сравнение оценки и реальных токенов
        successfulTests.filter { it.actualPromptTokens != null }.forEach { test ->
            val accuracy = (test.actualPromptTokens!!.toDouble() / test.estimatedInputTokens * 100).toInt()
            insights.add("${test.testName}: оценка ${test.estimatedInputTokens} vs реально ${test.actualPromptTokens} токенов ($accuracy%)")
        }

        return TokenComparisonSummary(
            results = results,
            modelLimits = ModelLimits(),
            insights = insights
        )
    }
}
