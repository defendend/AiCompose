package org.example.analytics.parser

import org.example.analytics.model.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Интерфейс для парсеров данных.
 */
interface DataParser {
    suspend fun parse(fileName: String, content: String): ParsedData
    fun canParse(fileType: DataFileType): Boolean
}

/**
 * Парсер CSV файлов.
 */
class CsvDataParser : DataParser {

    override suspend fun parse(fileName: String, content: String): ParsedData {
        val lines = content.trim().lines().filter { it.isNotBlank() }

        if (lines.isEmpty()) {
            return ParsedData(
                fileName = fileName,
                fileType = DataFileType.CSV,
                summary = DataSummary(
                    fileName = fileName,
                    fileType = DataFileType.CSV,
                    totalRows = 0,
                    columns = emptyList()
                )
            )
        }

        val headers = parseCsvLine(lines.first())
        val dataRows = lines.drop(1).map { line ->
            val values = parseCsvLine(line)
            headers.zip(values) { header, value -> header to value }.toMap()
        }

        val summary = createSummary(fileName, DataFileType.CSV, headers, dataRows)

        return ParsedData(
            fileName = fileName,
            fileType = DataFileType.CSV,
            headers = headers,
            rows = dataRows,
            totalRows = dataRows.size,
            summary = summary
        )
    }

    override fun canParse(fileType: DataFileType): Boolean = fileType == DataFileType.CSV

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var inQuotes = false
        var current = StringBuilder()

        for (char in line) {
            when {
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    result.add(current.toString().trim())
                    current.clear()
                }
                else -> current.append(char)
            }
        }

        result.add(current.toString().trim())
        return result
    }
}

/**
 * Парсер JSON файлов.
 */
class JsonDataParser : DataParser {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        isLenient = true
    }

    override suspend fun parse(fileName: String, content: String): ParsedData {
        try {
            val jsonElement = json.parseToJsonElement(content)

            val (headers, rows) = when (jsonElement) {
                is JsonArray -> parseJsonArray(jsonElement)
                is JsonObject -> parseJsonObject(jsonElement)
                else -> Pair(listOf("value"), listOf(mapOf("value" to jsonElement.toString())))
            }

            val summary = createSummary(fileName, DataFileType.JSON, headers, rows)

            return ParsedData(
                fileName = fileName,
                fileType = DataFileType.JSON,
                headers = headers,
                rows = rows,
                totalRows = rows.size,
                summary = summary
            )
        } catch (e: Exception) {
            return ParsedData(
                fileName = fileName,
                fileType = DataFileType.JSON,
                summary = DataSummary(
                    fileName = fileName,
                    fileType = DataFileType.JSON,
                    totalRows = 0,
                    columns = emptyList(),
                    insights = listOf("Ошибка парсинга JSON: ${e.message}")
                )
            )
        }
    }

    override fun canParse(fileType: DataFileType): Boolean = fileType == DataFileType.JSON

    private fun parseJsonArray(jsonArray: JsonArray): Pair<List<String>, List<Map<String, String>>> {
        if (jsonArray.isEmpty()) return Pair(emptyList(), emptyList())

        val allKeys = mutableSetOf<String>()
        val rows = jsonArray.mapNotNull { element ->
            if (element is JsonObject) {
                val row = element.jsonObject.mapValues { (_, value) ->
                    value.jsonPrimitive?.content ?: value.toString()
                }
                allKeys.addAll(row.keys)
                row
            } else {
                mapOf("value" to element.toString())
            }
        }

        return Pair(allKeys.toList().sorted(), rows)
    }

    private fun parseJsonObject(jsonObject: JsonObject): Pair<List<String>, List<Map<String, String>>> {
        val headers = listOf("key", "value")
        val rows = jsonObject.map { (key, value) ->
            mapOf(
                "key" to key,
                "value" to (value.jsonPrimitive?.content ?: value.toString())
            )
        }

        return Pair(headers, rows)
    }
}

/**
 * Парсер лог файлов.
 */
class LogDataParser : DataParser {

    private val commonPatterns = listOf(
        // ISO timestamp: 2023-12-01T10:30:00.123Z INFO message
        Regex("""(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z?)\s+(\w+)\s+(.+)"""),
        // Simple timestamp: 2023-12-01 10:30:00 [INFO] message
        Regex("""(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2})\s+\[(\w+)\]\s+(.+)"""),
        // Android logcat: 12-01 10:30:00.123  1234  5678 I/TAG: message
        Regex("""(\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s+(\d+)\s+(\d+)\s+([VDIWEF])/(\w+):\s+(.+)"""),
        // Simple format: [timestamp] level: message
        Regex("""\[([^\]]+)\]\s+(\w+):\s+(.+)"""),
        // Just level and message: ERROR: something went wrong
        Regex("""(\w+):\s+(.+)""")
    )

    override suspend fun parse(fileName: String, content: String): ParsedData {
        val lines = content.trim().lines().filter { it.isNotBlank() }

        if (lines.isEmpty()) {
            return ParsedData(
                fileName = fileName,
                fileType = DataFileType.LOG,
                summary = DataSummary(
                    fileName = fileName,
                    fileType = DataFileType.LOG,
                    totalRows = 0,
                    columns = emptyList()
                )
            )
        }

        val parsedLines = lines.mapIndexedNotNull { index, line ->
            parseLogLine(line, index + 1)
        }

        val headers = if (parsedLines.isNotEmpty()) {
            parsedLines.first().keys.toList()
        } else {
            listOf("line_number", "raw_message")
        }

        val rows = if (parsedLines.isNotEmpty()) {
            parsedLines
        } else {
            lines.mapIndexed { index, line ->
                mapOf(
                    "line_number" to (index + 1).toString(),
                    "raw_message" to line
                )
            }
        }

        val summary = createSummary(fileName, DataFileType.LOG, headers, rows)

        return ParsedData(
            fileName = fileName,
            fileType = DataFileType.LOG,
            headers = headers,
            rows = rows,
            totalRows = rows.size,
            summary = summary
        )
    }

    override fun canParse(fileType: DataFileType): Boolean = fileType == DataFileType.LOG

    private fun parseLogLine(line: String, lineNumber: Int): Map<String, String>? {
        for (pattern in commonPatterns) {
            val match = pattern.find(line)
            if (match != null) {
                return when (match.groupValues.size) {
                    4 -> mapOf(
                        "line_number" to lineNumber.toString(),
                        "timestamp" to match.groupValues[1],
                        "level" to match.groupValues[2],
                        "message" to match.groupValues[3]
                    )
                    3 -> mapOf(
                        "line_number" to lineNumber.toString(),
                        "level" to match.groupValues[1],
                        "message" to match.groupValues[2]
                    )
                    7 -> mapOf(
                        "line_number" to lineNumber.toString(),
                        "timestamp" to match.groupValues[1],
                        "pid" to match.groupValues[2],
                        "tid" to match.groupValues[3],
                        "level" to match.groupValues[4],
                        "tag" to match.groupValues[5],
                        "message" to match.groupValues[6]
                    )
                    else -> null
                }
            }
        }

        // Если ни один паттерн не подошел, возвращаем как plain text
        return mapOf(
            "line_number" to lineNumber.toString(),
            "raw_message" to line
        )
    }
}

/**
 * Создает сводку по данным.
 */
private fun createSummary(
    fileName: String,
    fileType: DataFileType,
    headers: List<String>,
    rows: List<Map<String, String>>
): DataSummary {
    val columns = headers.map { header ->
        val values = rows.mapNotNull { it[header] }.filter { it.isNotBlank() }
        val uniqueValues = values.distinct().size
        val nullValues = rows.size - values.size
        val type = detectColumnType(values)

        ColumnInfo(
            name = header,
            type = type,
            uniqueValues = uniqueValues,
            nullValues = nullValues,
            sampleValues = values.take(3)
        )
    }

    val insights = generateInsights(fileName, fileType, columns, rows)

    return DataSummary(
        fileName = fileName,
        fileType = fileType,
        totalRows = rows.size,
        columns = columns,
        insights = insights
    )
}

/**
 * Определяет тип колонки по значениям.
 */
private fun detectColumnType(values: List<String>): ColumnType {
    if (values.isEmpty()) return ColumnType.UNKNOWN

    val sample = values.take(10)

    // Проверяем на число
    val isNumeric = sample.all {
        it.toDoubleOrNull() != null
    }
    if (isNumeric) return ColumnType.NUMBER

    // Проверяем на булево значение
    val isBooleanLike = sample.all {
        it.lowercase() in listOf("true", "false", "yes", "no", "1", "0")
    }
    if (isBooleanLike) return ColumnType.BOOLEAN

    // Проверяем на дату (базовая проверка)
    val isDateLike = sample.any {
        it.contains("-") && it.matches(Regex("""^\d{4}-\d{2}-\d{2}.*"""))
    }
    if (isDateLike) return ColumnType.DATE

    return ColumnType.TEXT
}

/**
 * Генерирует инсайты по данным.
 */
private fun generateInsights(
    fileName: String,
    fileType: DataFileType,
    columns: List<ColumnInfo>,
    rows: List<Map<String, String>>
): List<String> {
    val insights = mutableListOf<String>()

    insights.add("📊 Файл содержит ${rows.size} строк и ${columns.size} колонок")

    when (fileType) {
        DataFileType.CSV -> {
            val numericColumns = columns.filter { it.type == ColumnType.NUMBER }
            if (numericColumns.isNotEmpty()) {
                insights.add("🔢 Найдено ${numericColumns.size} числовых колонок: ${numericColumns.map { it.name }.take(3).joinToString()}")
            }
        }
        DataFileType.JSON -> {
            insights.add("🌐 JSON структура успешно разобрана")
        }
        DataFileType.LOG -> {
            val levelColumn = columns.find { it.name == "level" }
            if (levelColumn != null) {
                insights.add("📝 Обнаружены уровни логирования в данных")
            }

            val errorCount = rows.count { row ->
                row.values.any { it.contains("ERROR", ignoreCase = true) }
            }
            if (errorCount > 0) {
                insights.add("⚠️ Найдено $errorCount потенциальных ошибок")
            }
        }
    }

    // Проверяем на дубликаты
    val duplicates = rows.size - rows.distinct().size
    if (duplicates > 0) {
        insights.add("🔄 Найдено $duplicates дублирующихся записей")
    }

    return insights
}