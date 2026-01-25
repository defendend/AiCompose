package org.example.analytics.parser

import org.example.analytics.model.DataFileType

/**
 * Фабрика для создания парсеров данных.
 */
class DataParserFactory {

    private val parsers = listOf(
        CsvDataParser(),
        JsonDataParser(),
        LogDataParser()
    )

    /**
     * Получить парсер для типа файла.
     */
    fun getParser(fileType: DataFileType): DataParser? {
        return parsers.firstOrNull { it.canParse(fileType) }
    }

    /**
     * Определить тип файла по расширению.
     */
    fun detectFileType(fileName: String): DataFileType? {
        val extension = fileName.substringAfterLast('.', "").lowercase()

        return DataFileType.values().find { type ->
            extension in type.extensions
        }
    }

    /**
     * Парсит файл, автоматически определяя тип.
     */
    suspend fun parseFile(fileName: String, content: String): org.example.analytics.model.ParsedData {
        val fileType = detectFileType(fileName)
            ?: return createErrorData(fileName, "Неподдерживаемый тип файла")

        val parser = getParser(fileType)
            ?: return createErrorData(fileName, "Не найден парсер для типа $fileType")

        return try {
            parser.parse(fileName, content)
        } catch (e: Exception) {
            createErrorData(fileName, "Ошибка парсинга: ${e.message}")
        }
    }

    private fun createErrorData(fileName: String, error: String): org.example.analytics.model.ParsedData {
        return org.example.analytics.model.ParsedData(
            fileName = fileName,
            fileType = DataFileType.CSV, // Fallback
            summary = org.example.analytics.model.DataSummary(
                fileName = fileName,
                fileType = DataFileType.CSV,
                totalRows = 0,
                columns = emptyList(),
                insights = listOf("❌ $error")
            )
        )
    }
}