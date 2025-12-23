package org.example.rag

import java.io.File

/**
 * Разбивает документы на чанки для векторизации
 */
object DocumentChunker {

    /**
     * Параметры чанкинга
     */
    data class ChunkConfig(
        val chunkSize: Int = 500,           // Размер чанка в символах
        val chunkOverlap: Int = 50,         // Перекрытие между чанками
        val minChunkSize: Int = 100         // Минимальный размер чанка
    )

    /**
     * Чанк документа
     */
    data class DocumentChunk(
        val id: String,                     // Уникальный ID чанка
        val source: String,                 // Источник (имя файла)
        val content: String,                // Текст чанка
        val metadata: Map<String, String> = emptyMap()  // Метаданные
    )

    /**
     * Загружает документ из файла
     */
    fun loadDocument(file: File): String {
        return when (file.extension.lowercase()) {
            "md", "txt" -> file.readText()
            "kt", "java", "js", "py", "ts" -> {
                // Для кода добавляем имя файла в начало
                "// File: ${file.name}\n${file.readText()}"
            }
            else -> file.readText()
        }
    }

    /**
     * Разбивает текст на чанки с перекрытием
     */
    fun chunkText(
        text: String,
        source: String,
        config: ChunkConfig = ChunkConfig()
    ): List<DocumentChunk> {
        val chunks = mutableListOf<DocumentChunk>()

        // Разбиваем на параграфы для более осмысленных границ
        val paragraphs = text.split(Regex("\n\n+"))

        var currentChunk = StringBuilder()
        var chunkIndex = 0

        for (paragraph in paragraphs) {
            val trimmedParagraph = paragraph.trim()
            if (trimmedParagraph.isEmpty()) continue

            // Если параграф + текущий чанк превышают размер
            if (currentChunk.length + trimmedParagraph.length > config.chunkSize) {
                // Сохраняем текущий чанк если он достаточно большой
                if (currentChunk.length >= config.minChunkSize) {
                    chunks.add(
                        DocumentChunk(
                            id = "${source}_chunk_${chunkIndex++}",
                            source = source,
                            content = currentChunk.toString().trim()
                        )
                    )
                }

                // Начинаем новый чанк с перекрытием
                val overlapText = getLastWords(currentChunk.toString(), config.chunkOverlap)
                currentChunk = StringBuilder(overlapText)
            }

            currentChunk.append(trimmedParagraph).append("\n\n")
        }

        // Добавляем последний чанк
        if (currentChunk.length >= config.minChunkSize) {
            chunks.add(
                DocumentChunk(
                    id = "${source}_chunk_${chunkIndex}",
                    source = source,
                    content = currentChunk.toString().trim()
                )
            )
        }

        return chunks
    }

    /**
     * Получает последние N символов текста по границам слов
     */
    private fun getLastWords(text: String, maxLength: Int): String {
        if (text.length <= maxLength) return text

        val substring = text.takeLast(maxLength)
        val firstSpace = substring.indexOf(' ')

        return if (firstSpace > 0) {
            substring.substring(firstSpace + 1)
        } else {
            substring
        }
    }

    /**
     * Загружает и чанкует файл
     */
    fun chunkFile(file: File, config: ChunkConfig = ChunkConfig()): List<DocumentChunk> {
        val text = loadDocument(file)
        return chunkText(text, file.name, config)
    }

    /**
     * Загружает и чанкует директорию рекурсивно
     */
    fun chunkDirectory(
        directory: File,
        extensions: Set<String> = setOf("md", "txt", "kt", "java", "js", "py", "ts"),
        config: ChunkConfig = ChunkConfig()
    ): List<DocumentChunk> {
        val chunks = mutableListOf<DocumentChunk>()

        directory.walk()
            .filter { it.isFile }
            .filter { it.extension.lowercase() in extensions }
            .forEach { file ->
                try {
                    chunks.addAll(chunkFile(file, config))
                } catch (e: Exception) {
                    println("⚠️  Ошибка при обработке ${file.name}: ${e.message}")
                }
            }

        return chunks
    }
}
