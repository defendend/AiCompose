package org.example.analytics.agent

import org.example.analytics.model.*

/**
 * Агент для анализа данных с использованием локальной модели.
 */
class DataAnalysisAgent {

    /**
     * Создает системный промпт для аналитики данных.
     */
    fun createAnalyticsPrompt(
        query: AnalyticsQuery,
        dataFiles: List<ParsedData>
    ): String {
        val dataContext = buildDataContext(dataFiles)

        return """
Ты — эксперт по анализу данных. Твоя задача — отвечать на вопросы пользователя о предоставленных данных.

Данные для анализа:
$dataContext

Вопрос пользователя: ${query.question}

Правила ответа:
1. Отвечай точно и конкретно на заданный вопрос
2. Приводи конкретные числа и факты из данных
3. Если данных недостаточно для точного ответа, скажи об этом
4. Предлагай дополнительные инсайты только если они релевантны вопросу
5. Используй эмодзи для лучшей визуализации (📊 📈 📉 ⚠️ ✅)
6. Если находишь интересные паттерны или аномалии, обязательно о них расскажи

Формат ответа:
- Прямой ответ на вопрос
- Ключевые находки (если есть)
- Рекомендации (если уместно)

Ответ:""".trimIndent()
    }

    /**
     * Создает специализированный промпт для разных типов анализа.
     */
    fun createSpecializedPrompt(
        analysisType: AnalysisType,
        dataFiles: List<ParsedData>,
        query: String? = null
    ): String {
        val dataContext = buildDataContext(dataFiles)

        return when (analysisType) {
            AnalysisType.ERROR_ANALYSIS -> """
Проанализируй логи на предмет ошибок и проблем.

Данные:
$dataContext

Найди:
1. 🔴 Все ошибки (ERROR, FATAL, EXCEPTION)
2. ⚠️ Предупреждения (WARN, WARNING)
3. 📊 Статистику по типам ошибок
4. ⏰ Временные паттерны ошибок
5. 🎯 Наиболее частые проблемы

${query?.let { "Дополнительный вопрос: $it" } ?: ""}

Ответ:""".trimIndent()

            AnalysisType.PERFORMANCE_ANALYSIS -> """
Проанализируй данные на предмет производительности.

Данные:
$dataContext

Найди:
1. 📈 Тренды производительности
2. 🐌 Медленные операции
3. ⏱️ Среднее время выполнения
4. 📊 Распределение нагрузки
5. 🚨 Пиковые значения

${query?.let { "Дополнительный вопрос: $it" } ?: ""}

Ответ:""".trimIndent()

            AnalysisType.USER_BEHAVIOR -> """
Проанализируй поведение пользователей.

Данные:
$dataContext

Найди:
1. 👥 Активных пользователей
2. 📱 Популярные функции
3. 🕐 Пиковые часы активности
4. 🛒 Паттерны использования
5. 📉 Места "отвала" пользователей

${query?.let { "Дополнительный вопрос: $it" } ?: ""}

Ответ:""".trimIndent()

            AnalysisType.GENERAL_SUMMARY -> """
Создай общую сводку по данным.

Данные:
$dataContext

Включи:
1. 📊 Основные метрики
2. 🎯 Ключевые инсайты
3. 📈 Тренды
4. ⚠️ Потенциальные проблемы
5. 💡 Рекомендации

${query?.let { "Дополнительный вопрос: $it" } ?: ""}

Ответ:""".trimIndent()

            AnalysisType.CUSTOM -> query?.let {
                createAnalyticsPrompt(
                    AnalyticsQuery(id = "", question = it),
                    dataFiles
                )
            } ?: "Не указан вопрос для анализа"
        }
    }

    /**
     * Предлагает готовые вопросы для анализа на основе типа данных.
     */
    fun suggestQuestions(dataFiles: List<ParsedData>): List<SuggestedQuestion> {
        val suggestions = mutableListOf<SuggestedQuestion>()

        dataFiles.forEach { data ->
            when (data.fileType) {
                DataFileType.CSV -> {
                    val numericColumns = data.summary.columns.filter { it.type == ColumnType.NUMBER }
                    if (numericColumns.isNotEmpty()) {
                        suggestions.add(
                            SuggestedQuestion(
                                "📊 Какие числовые показатели в данных?",
                                AnalysisType.GENERAL_SUMMARY,
                                "Покажи статистику по числовым колонкам: ${numericColumns.joinToString { it.name }}"
                            )
                        )
                    }

                    suggestions.add(
                        SuggestedQuestion(
                            "🔍 Есть ли дубликаты в данных?",
                            AnalysisType.GENERAL_SUMMARY,
                            "Проанализируй данные на наличие дубликатов и аномалий"
                        )
                    )
                }

                DataFileType.JSON -> {
                    suggestions.add(
                        SuggestedQuestion(
                            "🌐 Какая структура у JSON данных?",
                            AnalysisType.GENERAL_SUMMARY,
                            "Опиши структуру JSON данных и основные поля"
                        )
                    )
                }

                DataFileType.LOG -> {
                    val hasErrorLevel = data.summary.columns.any { it.name == "level" }
                    if (hasErrorLevel) {
                        suggestions.add(
                            SuggestedQuestion(
                                "🔴 Какие ошибки чаще всего встречаются?",
                                AnalysisType.ERROR_ANALYSIS,
                                "Найди и подсчитай все ошибки по типам"
                            )
                        )
                    }

                    val hasTimestamp = data.summary.columns.any { it.name == "timestamp" }
                    if (hasTimestamp) {
                        suggestions.add(
                            SuggestedQuestion(
                                "⏰ В какое время больше всего активности?",
                                AnalysisType.PERFORMANCE_ANALYSIS,
                                "Проанализируй временные паттерны в логах"
                            )
                        )
                    }

                    suggestions.add(
                        SuggestedQuestion(
                            "⚠️ Где пользователи чаще всего теряются?",
                            AnalysisType.USER_BEHAVIOR,
                            "Найди паттерны поведения пользователей и места возможного отвала"
                        )
                    )
                }
            }
        }

        // Общие вопросы для всех типов данных
        suggestions.addAll(
            listOf(
                SuggestedQuestion(
                    "📈 Какие основные тренды видны в данных?",
                    AnalysisType.GENERAL_SUMMARY,
                    "Проанализируй основные тренды и паттерны в данных"
                ),
                SuggestedQuestion(
                    "🎯 Какие ключевые инсайты можно извлечь?",
                    AnalysisType.GENERAL_SUMMARY,
                    "Выдели самые важные находки и инсайты из данных"
                ),
                SuggestedQuestion(
                    "💡 Какие есть рекомендации по улучшению?",
                    AnalysisType.GENERAL_SUMMARY,
                    "Дай рекомендации на основе анализа данных"
                )
            )
        )

        return suggestions.distinctBy { it.question }.take(8)
    }

    /**
     * Строит контекст данных для промпта.
     */
    private fun buildDataContext(dataFiles: List<ParsedData>): String {
        if (dataFiles.isEmpty()) {
            return "Данные не загружены."
        }

        return dataFiles.joinToString("\n\n") { data ->
            buildString {
                appendLine("📁 Файл: ${data.fileName} (${data.fileType.displayName})")
                appendLine("📊 Строк: ${data.totalRows}, Колонок: ${data.headers.size}")

                if (data.headers.isNotEmpty()) {
                    appendLine("🏷️ Колонки: ${data.headers.take(10).joinToString()}")
                    if (data.headers.size > 10) {
                        appendLine("   ... и ещё ${data.headers.size - 10} колонок")
                    }
                }

                if (data.summary.insights.isNotEmpty()) {
                    appendLine("💡 Инсайты:")
                    data.summary.insights.take(5).forEach { insight ->
                        appendLine("   • $insight")
                    }
                }

                // Добавляем пример данных (первые несколько строк)
                if (data.rows.isNotEmpty()) {
                    appendLine("\n📋 Пример данных:")
                    val sampleRows = data.rows.take(3)
                    sampleRows.forEachIndexed { index, row ->
                        appendLine("   ${index + 1}. ${row.entries.take(5).joinToString { "${it.key}: ${it.value}" }}")
                    }
                    if (data.rows.size > 3) {
                        appendLine("   ... всего строк: ${data.rows.size}")
                    }
                }
            }
        }
    }
}

/**
 * Типы анализа данных.
 */
enum class AnalysisType(val displayName: String, val icon: String) {
    ERROR_ANALYSIS("Анализ ошибок", "🔴"),
    PERFORMANCE_ANALYSIS("Анализ производительности", "📈"),
    USER_BEHAVIOR("Поведение пользователей", "👥"),
    GENERAL_SUMMARY("Общая сводка", "📊"),
    CUSTOM("Произвольный запрос", "🔍")
}

/**
 * Предлагаемый вопрос для анализа.
 */
data class SuggestedQuestion(
    val question: String,
    val type: AnalysisType,
    val prompt: String
)