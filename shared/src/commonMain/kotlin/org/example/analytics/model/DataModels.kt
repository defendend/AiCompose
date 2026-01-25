package org.example.analytics.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Тип данных для анализа.
 */
enum class DataFileType(val displayName: String, val extensions: List<String>) {
    CSV("CSV файлы", listOf("csv")),
    JSON("JSON файлы", listOf("json")),
    LOG("Лог файлы", listOf("log", "txt"))
}

/**
 * Результат парсинга файла.
 */
@Serializable
data class ParsedData(
    val fileName: String,
    val fileType: DataFileType,
    val headers: List<String> = emptyList(),
    val rows: List<Map<String, String>> = emptyList(),
    val totalRows: Int = 0,
    val summary: DataSummary
)

/**
 * Сводка по данным.
 */
@Serializable
data class DataSummary(
    val fileName: String,
    val fileType: DataFileType,
    val totalRows: Int,
    val columns: List<ColumnInfo>,
    val insights: List<String> = emptyList()
)

/**
 * Информация о колонке.
 */
@Serializable
data class ColumnInfo(
    val name: String,
    val type: ColumnType,
    val uniqueValues: Int,
    val nullValues: Int,
    val sampleValues: List<String> = emptyList()
)

/**
 * Тип колонки.
 */
enum class ColumnType {
    TEXT, NUMBER, DATE, BOOLEAN, UNKNOWN
}

/**
 * Запрос на анализ данных.
 */
@Serializable
data class AnalyticsQuery(
    val id: String,
    val question: String,
    val dataFiles: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Результат анализа данных.
 */
@Serializable
data class AnalyticsResult(
    val queryId: String,
    val question: String,
    val answer: String,
    val insights: List<String> = emptyList(),
    val charts: List<ChartData> = emptyList(),
    val executionTimeMs: Long = 0,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Данные для графика.
 */
@Serializable
data class ChartData(
    val type: ChartType,
    val title: String,
    val labels: List<String>,
    val datasets: List<ChartDataset>
)

/**
 * Тип графика.
 */
enum class ChartType {
    BAR, LINE, PIE, SCATTER
}

/**
 * Данные серии для графика.
 */
@Serializable
data class ChartDataset(
    val label: String,
    val data: List<Double>,
    val backgroundColor: String = "#4CAF50",
    val borderColor: String = "#2E7D32"
)

/**
 * Состояние аналитики.
 */
@Serializable
data class AnalyticsState(
    val isLoading: Boolean = false,
    val currentQuery: AnalyticsQuery? = null,
    val loadedFiles: List<ParsedData> = emptyList(),
    val queryHistory: List<AnalyticsResult> = emptyList(),
    val error: String? = null,
    val selectedModel: String = "qwen2.5:1.5b"
)