package org.example.tools.devassistant.docs

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.example.rag.DocumentChunker
import org.example.rag.DocumentIndex
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Специализированный индекс для документации проекта.
 * Автоматически индексирует README, CLAUDE.md, docs/ и схемы данных.
 * Поддерживает сохранение/загрузку индекса в файл.
 */
class DocsIndex(
    val projectPath: String = "."
) {
    companion object {
        private val logger = LoggerFactory.getLogger(DocsIndex::class.java)

        /** Имя файла для сохранения индекса */
        const val INDEX_FILE_NAME = "docs_index.json"

        /** Расширения файлов документации */
        val DOCS_EXTENSIONS = setOf("md", "txt", "json", "yaml", "yml")

        /** Паттерны путей для поиска документации */
        val DOCS_PATTERNS = listOf(
            "README.md",
            "CHANGELOG.md",
            "CONTRIBUTING.md",
            ".claude/CLAUDE.md",
            "docs"
        )

        /** Конфигурация чанкинга для документации */
        val DOCS_CHUNK_CONFIG = DocumentChunker.ChunkConfig(
            chunkSize = 800,    // Больше для документации
            chunkOverlap = 100,
            minChunkSize = 150
        )
    }

    private val index = DocumentIndex()
    private var lastIndexTime: Long = 0
    private var indexedFiles: List<String> = emptyList()
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    /**
     * Индексирует документацию проекта.
     *
     * @return Результат индексации
     */
    fun indexProjectDocs(): IndexResult {
        val baseDir = File(projectPath).absoluteFile
        if (!baseDir.exists()) {
            return IndexResult(
                success = false,
                error = "Директория не существует: ${baseDir.absolutePath}",
                filesIndexed = 0,
                chunksCreated = 0,
                sources = emptyList()
            )
        }

        val docsFiles = findDocsFiles(baseDir)
        if (docsFiles.isEmpty()) {
            return IndexResult(
                success = false,
                error = "Файлы документации не найдены в ${baseDir.absolutePath}",
                filesIndexed = 0,
                chunksCreated = 0,
                sources = emptyList()
            )
        }

        val chunks = mutableListOf<DocumentChunker.DocumentChunk>()

        docsFiles.forEach { file ->
            try {
                val fileChunks = DocumentChunker.chunkFile(file, DOCS_CHUNK_CONFIG)
                // Используем относительный путь как source для лучшей читаемости
                val relativePath = file.relativeTo(baseDir).path
                val renamedChunks = fileChunks.map { chunk ->
                    chunk.copy(
                        id = "${relativePath}_chunk_${fileChunks.indexOf(chunk)}",
                        source = relativePath
                    )
                }
                chunks.addAll(renamedChunks)
            } catch (e: Exception) {
                println("⚠️ Ошибка при обработке ${file.name}: ${e.message}")
            }
        }

        if (chunks.isEmpty()) {
            return IndexResult(
                success = false,
                error = "Не удалось создать чанки из документации",
                filesIndexed = docsFiles.size,
                chunksCreated = 0,
                sources = emptyList()
            )
        }

        // Очищаем старый индекс и индексируем новые чанки
        index.clear()
        index.indexChunks(chunks)

        lastIndexTime = System.currentTimeMillis()
        indexedFiles = docsFiles.map { it.relativeTo(baseDir).path }

        return IndexResult(
            success = true,
            error = null,
            filesIndexed = docsFiles.size,
            chunksCreated = chunks.size,
            sources = indexedFiles
        )
    }

    /**
     * Поиск по документации.
     *
     * @param query Поисковый запрос
     * @param topK Количество результатов
     * @param minRelevance Минимальный порог релевантности
     * @return Список результатов поиска
     */
    fun search(
        query: String,
        topK: Int = 5,
        minRelevance: Float? = 0.2f
    ): List<DocumentIndex.SearchResult> {
        if (index.size() == 0) {
            // Автоматически индексируем если индекс пуст
            val result = indexProjectDocs()
            if (!result.success) {
                return emptyList()
            }
        }

        return index.search(query, topK, minRelevance)
    }

    /**
     * Получить размер индекса.
     */
    fun size(): Int = index.size()

    /**
     * Информация об индексе.
     */
    fun getInfo(): IndexInfo {
        return IndexInfo(
            projectPath = projectPath,
            size = index.size(),
            lastIndexTime = lastIndexTime,
            indexedFiles = indexedFiles
        )
    }

    /**
     * Очистить индекс.
     */
    fun clear() {
        index.clear()
        lastIndexTime = 0
        indexedFiles = emptyList()
    }

    /**
     * Получить внутренний DocumentIndex для использования с RagQueryService.
     */
    fun getDocumentIndex(): DocumentIndex = index

    /**
     * Сохраняет метаданные индекса в файл.
     * Примечание: сам DocumentIndex сохраняется отдельно.
     */
    fun saveMetadata(file: File = File(projectPath, ".docs_index_meta.json")) {
        try {
            val metadata = DocsIndexMetadata(
                projectPath = projectPath,
                lastIndexTime = lastIndexTime,
                indexedFiles = indexedFiles,
                chunksCount = index.size()
            )
            file.writeText(json.encodeToString(metadata))
            logger.info("📁 Метаданные индекса сохранены: ${file.absolutePath}")
        } catch (e: Exception) {
            logger.error("❌ Ошибка сохранения метаданных: ${e.message}")
        }
    }

    /**
     * Загружает метаданные индекса из файла.
     */
    fun loadMetadata(file: File = File(projectPath, ".docs_index_meta.json")): Boolean {
        return try {
            if (!file.exists()) {
                logger.info("ℹ️ Файл метаданных не найден: ${file.absolutePath}")
                return false
            }

            val metadata = json.decodeFromString<DocsIndexMetadata>(file.readText())
            lastIndexTime = metadata.lastIndexTime
            indexedFiles = metadata.indexedFiles
            logger.info("📂 Метаданные индекса загружены: ${metadata.chunksCount} чанков")
            true
        } catch (e: Exception) {
            logger.error("❌ Ошибка загрузки метаданных: ${e.message}")
            false
        }
    }

    /**
     * Сохраняет полный индекс (DocumentIndex + метаданные).
     */
    fun save(directory: File = File(projectPath)) {
        try {
            val indexFile = File(directory, INDEX_FILE_NAME)
            index.save(indexFile)
            saveMetadata(File(directory, ".docs_index_meta.json"))
            logger.info("✅ Индекс документации сохранён в ${directory.absolutePath}")
        } catch (e: Exception) {
            logger.error("❌ Ошибка сохранения индекса: ${e.message}")
        }
    }

    /**
     * Загружает полный индекс (DocumentIndex + метаданные).
     * @return true если индекс успешно загружен
     */
    fun load(directory: File = File(projectPath)): Boolean {
        return try {
            val indexFile = File(directory, INDEX_FILE_NAME)
            if (!indexFile.exists()) {
                logger.info("ℹ️ Файл индекса не найден: ${indexFile.absolutePath}")
                return false
            }

            index.load(indexFile)
            loadMetadata(File(directory, ".docs_index_meta.json"))
            logger.info("✅ Индекс документации загружен: ${index.size()} чанков")
            true
        } catch (e: Exception) {
            logger.error("❌ Ошибка загрузки индекса: ${e.message}")
            false
        }
    }

    /**
     * Проверяет, нужна ли переиндексация.
     * Возвращает true если файлы изменились с момента последней индексации.
     */
    fun needsReindex(): Boolean {
        if (lastIndexTime == 0L) return true

        val baseDir = File(projectPath).absoluteFile
        val currentFiles = findDocsFiles(baseDir)

        // Проверяем, изменился ли список файлов
        val currentPaths = currentFiles.map { it.relativeTo(baseDir).path }.sorted()
        if (currentPaths != indexedFiles.sorted()) {
            logger.info("📝 Список файлов изменился, нужна переиндексация")
            return true
        }

        // Проверяем время модификации файлов
        val newerFiles = currentFiles.any { it.lastModified() > lastIndexTime }
        if (newerFiles) {
            logger.info("📝 Найдены обновлённые файлы, нужна переиндексация")
            return true
        }

        return false
    }

    /**
     * Метаданные индекса для сериализации.
     */
    @Serializable
    data class DocsIndexMetadata(
        val projectPath: String,
        val lastIndexTime: Long,
        val indexedFiles: List<String>,
        val chunksCount: Int
    )

    /**
     * Находит файлы документации в проекте.
     */
    private fun findDocsFiles(baseDir: File): List<File> {
        val result = mutableListOf<File>()

        // Файлы в корне
        listOf("README.md", "CHANGELOG.md", "CONTRIBUTING.md").forEach { name ->
            val file = File(baseDir, name)
            if (file.exists() && file.isFile) {
                result.add(file)
            }
        }

        // CLAUDE.md
        val claudeMd = File(baseDir, ".claude/CLAUDE.md")
        if (claudeMd.exists()) {
            result.add(claudeMd)
        }

        // Директория docs/
        val docsDir = File(baseDir, "docs")
        if (docsDir.isDirectory) {
            docsDir.walkTopDown()
                .filter { it.isFile && it.extension.lowercase() in DOCS_EXTENSIONS }
                .forEach { result.add(it) }
        }

        // API документация (если есть)
        baseDir.walkTopDown()
            .maxDepth(2)
            .filter { it.isFile }
            .filter { it.name.lowercase().contains("api") && it.extension in DOCS_EXTENSIONS }
            .forEach { result.add(it) }

        return result.distinctBy { it.absolutePath }
    }

    /**
     * Результат индексации.
     */
    data class IndexResult(
        val success: Boolean,
        val error: String?,
        val filesIndexed: Int,
        val chunksCreated: Int,
        val sources: List<String>
    )

    /**
     * Информация об индексе.
     */
    data class IndexInfo(
        val projectPath: String,
        val size: Int,
        val lastIndexTime: Long,
        val indexedFiles: List<String>
    )
}
